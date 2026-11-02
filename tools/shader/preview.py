#!/usr/bin/env python3
"""SounanShaders offline harness: Mesa compile/link check + Iris-like preview renders.

  python3 tools/shader/preview.py --check
        compile AND link every program of every dimension folder with Mesa (llvmpipe, the
        compiler the CI run uses) with the default options and with every option flipped.
        Catches what glslang cannot: vertex/fragment interface mismatches, driver quirks.

  python3 tools/shader/preview.py [--out DIR] [--size 960x540] [--shots a,b] [-D NAME=VALUE]
        renders a small voxel night forest on Kaerazu-yama (tall cedar trunks under conical
        canopies with gaps, ferns, a fog hollow with a stream, a hut with a tin roof, a
        concrete road tunnel through a hill, a peeking monster with an eyes layer) through
        the real pipeline - shadow pass, sky, gbuffers (terrain, entities, eyes, the solid
        hand), deferred, translucents, composite..composite3, final - emulating what
        Iris/Oculus do (ping-pong buffers, RENDERTARGETS, buffer formats / clear colours from
        the directives, per-program blending, the squeezed hand depth, mipmap directives,
        celestial / shadow matrices, uniforms incl. both held items).
        Writes one PNG per shot plus contact.png, and prints buffer statistics (NaN / black).
        --timing prints the llvmpipe time of every pass.

  python3 tools/shader/preview.py --shots t_forest_deep,t_fear,...
        shots named t_* are taken on the REAL mountain at the CI tour's viewpoints: a 72x72
        piece of the baked terrain (src/main/resources/data/sounan/terrain/terrain.bin.gz or
        $SOUNAN_TERRAIN) decorated with a port of com.sounan.world.Decorator (cedars, boulders,
        fallen logs, ferns; no POI structures), a peeking monster behind a trunk ahead.
        The repo's block textures (or $SOUNAN_TEXTURES) replace the procedural tiles when present.

This is a development aid, not a substitute for the CI client run.
"""
import argparse
import math
import os
import re
import sys
sys.dont_write_bytecode = True
import time
from collections import deque

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import glctx  # noqa: E402
from glctx import gl  # noqa: E402
import lint  # noqa: E402
import build_pack  # noqa: E402

ROOT = lint.ROOT
ATTRIBS = {"mc_Entity": 10, "mc_midTexCoord": 11}
MOUNTAIN = "sounan_kaerazu"

# item / entity ids (item.properties, entity.properties)
FLASHLIGHT, FLASH_LOW, PHONE_LIGHT, CAMERA, FLARE = 10001, 10002, 10003, 10004, 10005
NOZOKI, UTSURU = 20001, 20006

# =====================================================================================
#  pack loading
# =====================================================================================

def eval_expr(expr, macros):
    expr = re.sub(r"defined\s*\(\s*(\w+)\s*\)", lambda m: "1" if m.group(1) in macros else "0", expr)
    expr = re.sub(r"defined\s+(\w+)", lambda m: "1" if m.group(1) in macros else "0", expr)

    def sub(m):
        v = macros.get(m.group(0))
        return (v if v else "1") if v is not None else "0"

    for _ in range(6):
        expr = re.sub(r"\b[A-Za-z_]\w*\b", sub, expr)
    expr = expr.replace("&&", " and ").replace("||", " or ")
    expr = re.sub(r"!(?!=)", " not ", expr)
    return bool(eval(expr, {"__builtins__": {}}, {}))


def preprocess(lines):
    """tiny GLSL preprocessor (enough for this pack): returns the active lines"""
    macros = {}
    out = []
    stack = []
    active = True
    for line in lines:
        s = line.strip()
        if s.startswith("#"):
            m = re.match(r"#\s*(\w+)\s*(.*)", s)
            kw, rest = (m.group(1), re.sub(r"//.*$", "", m.group(2)).strip()) if m else ("", "")
            if kw in ("ifdef", "ifndef", "if"):
                if kw == "ifdef":
                    cond = rest.split()[0] in macros
                elif kw == "ifndef":
                    cond = rest.split()[0] not in macros
                else:
                    cond = eval_expr(rest, macros) if active else False
                stack.append([active, cond])
                active = active and cond
                continue
            if kw == "elif":
                parent, taken = stack[-1]
                cond = (not taken) and parent and eval_expr(rest, macros)
                active = cond
                stack[-1][1] = taken or cond
                continue
            if kw == "else":
                parent, taken = stack[-1]
                active = parent and not taken
                stack[-1][1] = True
                continue
            if kw == "endif":
                active = stack.pop()[0]
                continue
            if not active:
                continue
            if kw == "define":
                mm = re.match(r"(\w+)(?:\s+(.*))?", rest)
                macros[mm.group(1)] = (mm.group(2) or "").strip()
            elif kw == "undef":
                macros.pop(rest.split()[0], None)
            out.append(line)
            continue
        if active:
            out.append(line)
    return out


def apply_options(lines, overrides):
    if not overrides:
        return lines
    out = []
    for line in lines:
        m = lint.VALUE_RE.match(line)
        if m and m.group(1) in overrides:
            out.append(re.sub(r"(#define\s+%s\s+)\S+" % m.group(1), lambda mm: mm.group(1) + overrides[m.group(1)], line, count=1))
            continue
        m = lint.CONST_RE.match(line)
        if m and m.group(1) in overrides:
            out.append(re.sub(r"(=\s*)[^;]+;", lambda mm: mm.group(1) + overrides[m.group(1)] + ";", line, count=1))
            continue
        m = lint.BOOL_RE.match(line)
        if m and m.group(3) in overrides and "[" not in (m.group(4) or ""):
            on = overrides[m.group(3)] not in ("0", "false", "off")
            out.append(("#define %s\n" % m.group(3)) if on else ("//#define %s\n" % m.group(3)))
            continue
        out.append(line)
    return out


def load_source(folder, prog, ext, overrides, flip=False):
    path = os.path.join(ROOT, folder, prog + "." + ext) if folder else os.path.join(ROOT, prog + "." + ext)
    if not os.path.isfile(path):
        return None
    lines = lint.expand(path)
    if flip:
        bools, values = lint.parse_options()
        lines = lint.apply_variant(lines, bools, values, True)
    lines = apply_options(lines, overrides)
    header = ["#define %s %s\n" % (k, v) for k, v in lint.ENV_DEFINES.items()]
    return lines[:1] + header + lines[1:]


def buffer_directives():
    """colortexN formats and clear colours from lib/settings.glsl"""
    fmts, clears = {}, {}
    with open(os.path.join(ROOT, "lib", "settings.glsl"), encoding="utf-8") as fh:
        txt = fh.read()
    for n, f in re.findall(r"const\s+int\s+colortex(\d+)Format\s*=\s*(\w+)\s*;", txt):
        fmts[int(n)] = f
    for n, v in re.findall(r"const\s+vec4\s+colortex(\d+)ClearColor\s*=\s*vec4\(([^)]*)\)", txt):
        clears[int(n)] = tuple(float(x) for x in v.split(","))
    return fmts, clears


class Program:
    def __init__(self, folder, name, overrides, flip=False):
        self.name = name
        self.folder = folder
        vs = load_source(folder, name, "vsh", overrides, flip)
        fs = load_source(folder, name, "fsh", overrides, flip)
        if vs is None or fs is None:
            raise FileNotFoundError(name)
        self.errors = []
        v, ok_v, log_v = glctx.compile_shader("".join(vs), glctx.GL_VERTEX_SHADER)
        f, ok_f, log_f = glctx.compile_shader("".join(fs), glctx.GL_FRAGMENT_SHADER)
        if not ok_v:
            self.errors.append("vertex: " + log_v)
        if not ok_f:
            self.errors.append("fragment: " + log_f)
        self.id = 0
        if ok_v and ok_f:
            self.id, ok, log = glctx.link_program(v, f, ATTRIBS)
            if not ok:
                self.errors.append("link: " + log)
        active = "".join(preprocess(fs))
        rt = re.findall(r"/\*\s*(?:RENDERTARGETS|DRAWBUFFERS)\s*:\s*([0-9,\s]+?)\s*\*/", active)
        if rt:
            txt = rt[-1]
            self.targets = [int(x) for x in txt.split(",")] if "," in txt else [int(c) for c in txt.strip()]
        else:
            self.targets = [0]
        self.mipmaps = [int(x) for x in re.findall(r"const\s+bool\s+colortex(\d+)MipmapEnabled\s*=\s*true", active)]
        self.consts = {}
        for name_, val in re.findall(r"const\s+(?:int|float)\s+(shadowDistance|sunPathRotation|shadowMapResolution)\s*=\s*([-\d.]+)", active):
            self.consts[name_] = float(val)
        self._loc = {}

    def loc(self, name):
        if name not in self._loc:
            self._loc[name] = gl.glGetUniformLocation(self.id, name.encode())
        return self._loc[name]


# =====================================================================================
#  math
# =====================================================================================

def perspective(fovy, aspect, n, f):
    t = 1.0 / math.tan(math.radians(fovy) / 2)
    m = np.zeros((4, 4), np.float32)
    m[0, 0] = t / aspect
    m[1, 1] = t
    m[2, 2] = (f + n) / (n - f)
    m[2, 3] = 2 * f * n / (n - f)
    m[3, 2] = -1
    return m


def ortho(l, r, b, t, n, f):
    m = np.eye(4, dtype=np.float32)
    m[0, 0] = 2 / (r - l)
    m[1, 1] = 2 / (t - b)
    m[2, 2] = -2 / (f - n)
    m[0, 3] = -(r + l) / (r - l)
    m[1, 3] = -(t + b) / (t - b)
    m[2, 3] = -(f + n) / (f - n)
    return m


def look_rot(forward, up=(0, 1, 0)):
    f = np.array(forward, np.float64)
    f /= np.linalg.norm(f)
    u = np.array(up, np.float64)
    if abs(np.dot(f, u / np.linalg.norm(u))) > 0.999:
        u = np.array([0, 0, 1.0])
    s = np.cross(f, u)
    s /= np.linalg.norm(s)
    u = np.cross(s, f)
    m = np.eye(4, dtype=np.float32)
    m[0, :3], m[1, :3], m[2, :3] = s, u, -f
    return m


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4, dtype=np.float32)
    if axis == "x":
        m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
    elif axis == "y":
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    else:
        m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
    return m


def sky_angle(world_time):
    d0 = (world_time / 24000.0 - 0.25) % 1.0
    d1 = 0.5 - math.cos(d0 * math.pi) / 2.0
    return (d0 * 2.0 + d1) / 3.0


# =====================================================================================
#  test scene: a slope of Kaerazu-yama at night
# =====================================================================================

TILES = {}
ATLAS_N = 8          # 8x8 tiles of 16 px

# the real block textures (from the repo, once the art is merged) replace the procedural tiles
TEXTURE_DIR = os.environ.get("SOUNAN_TEXTURES") or os.path.normpath(
    os.path.join(ROOT, "..", "..", "resources", "assets", "sounan", "textures", "block"))
TILE_FILES = {"bark": "cedar_log", "log_top": "cedar_log_top", "leaves": "cedar_leaves", "floor_top": "forest_floor_top",
              "floor_side": "forest_floor_side", "dirt": "forest_dirt", "moss": "moss_floor", "rock": "mountain_rock",
              "stones": "river_stones", "planks": "weathered_planks", "tin": "tin_roof", "concrete": "concrete_old",
              "asphalt": "asphalt", "ribbon": "cedar_log_ribbon", "trail": "trail_top", "lamp": "tunnel_lamp_lit"}


def real_tile(name):
    """16x16 RGBA uint8 of the repo texture for a tile, or None"""
    f = TILE_FILES.get(name)
    if not f:
        return None
    path = os.path.join(TEXTURE_DIR, f + ".png")
    if not os.path.isfile(path) or os.path.getsize(path) == 0:
        return None
    from PIL import Image
    try:
        im = Image.open(path).convert("RGBA")
    except Exception:
        return None
    if im.size != (16, 16):
        im = im.crop((0, 0, im.size[0], im.size[0])).resize((16, 16), Image.NEAREST)
    return np.asarray(im, np.uint8)


def make_atlas(seed=7):
    """procedural 16x16 pixel-art-ish tiles. Returns uint8 RGBA (128x128)."""
    rng = np.random.default_rng(seed)
    atlas = np.zeros((16 * ATLAS_N, 16 * ATLAS_N, 4), np.uint8)
    names = ["bark", "log_top", "leaves", "floor_top", "floor_side", "dirt", "moss", "rock",
             "stones", "water", "planks", "tin", "concrete", "asphalt", "ribbon", "fern",
             "skin", "hair", "eyes", "torch_body", "lens", "moon", "trail", "phone",
             "flare", "lamp"]
    y, x = np.mgrid[0:16, 0:16]

    def noise(scale=1.0):
        return rng.random((16, 16)) * scale

    for i, n in enumerate(names):
        tx, ty = i % ATLAS_N, i // ATLAS_N
        TILES[n] = (tx, ty)
        a = np.full((16, 16), 255.0)
        if n == "bark":          # reddish-brown fibrous vertical bark, dark
            base = np.array([92, 58, 44])
            g = 0.75 + 0.35 * np.sin(x * 1.9 + rng.random((1, 16)) * 6.0) * 0.5 + noise(0.25)
            t = base[None, None] * g[..., None]
        elif n == "log_top":
            r = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
            t = np.array([120, 88, 60])[None, None] * (0.8 + 0.2 * np.sin(r * 1.8))[..., None]
        elif n == "leaves":      # dense dark blue-green needles, cutout
            base = np.array([34, 52, 46])
            t = base[None, None] * (0.6 + noise(0.8))[..., None]
            holes = rng.random((16, 16)) < 0.22
            a[holes] = 0
        elif n == "floor_top":   # dead brown needles, a few leaves, moss specks
            base = np.array([86, 62, 42])
            t = base[None, None] * (0.6 + noise(0.6))[..., None]
            leaf = rng.random((16, 16)) < 0.06
            t[leaf] = np.array([120, 70, 34])
            moss = rng.random((16, 16)) < 0.05
            t[moss] = np.array([60, 66, 36])
        elif n == "floor_side":
            base = np.array([70, 52, 38]); t = base[None, None] * (0.65 + noise(0.5))[..., None]
            t[y < 4] = (np.array([86, 62, 42])[None, None] * (0.6 + noise(0.6))[..., None])[y < 4]
        elif n == "dirt":
            base = np.array([58, 42, 32]); t = base[None, None] * (0.6 + noise(0.6))[..., None]
        elif n == "moss":
            base = np.array([58, 66, 38]); t = base[None, None] * (0.6 + noise(0.6))[..., None]
        elif n == "rock":        # dark grey wet andesite
            base = np.array([84, 86, 88]); t = base[None, None] * (0.65 + noise(0.45))[..., None]
            crack = (np.abs(x - 5 - y * 0.4) < 0.6); t[crack] *= 0.5
        elif n == "stones":      # rounded river stones
            cells = rng.random((4, 4, 3))
            cx, cy = x // 4, y // 4
            t = (np.array([90, 88, 84])[None, None] * (0.6 + 0.6 * cells[cy, cx, :1]))
            edge = ((x % 4) == 0) | ((y % 4) == 0); t[edge] *= 0.45
        elif n == "water":
            t = np.zeros((16, 16, 3)) + 170 * (0.9 + noise(0.1))[..., None]; a[:] = 190
        elif n == "planks":      # sun-bleached grey old wood
            base = np.array([120, 114, 104]); g = 0.8 + noise(0.3)
            t = base[None, None] * g[..., None]; t[(y % 4) == 0] *= 0.55
        elif n == "tin":         # rusted corrugated iron, ribs vertical
            rib = 0.75 + 0.25 * np.sin(x * 1.6)
            t = np.array([118, 76, 52])[None, None] * (rib * (0.7 + noise(0.4)))[..., None]
        elif n == "concrete":
            base = np.array([118, 118, 114]); t = base[None, None] * (0.7 + noise(0.3))[..., None]
            streak = (x % 5 == 2); t[streak] *= 0.75
        elif n == "asphalt":
            base = np.array([46, 46, 48]); t = base[None, None] * (0.7 + noise(0.5))[..., None]
        elif n == "ribbon":      # bark with a faded pink tape band
            base = np.array([92, 58, 44])
            t = base[None, None] * (0.75 + noise(0.3))[..., None]
            band = (y >= 6) & (y <= 9)
            t[band] = np.array([214, 112, 156])[None, None] * (0.85 + noise(0.15))[..., None][band]
        elif n == "fern":        # cutout fronds
            t = np.zeros((16, 16, 3)) + np.array([52, 74, 40])
            t = t * (0.6 + noise(0.6))[..., None]
            frond = (np.abs(x - 7.5) < (16 - y) * 0.45) & ((x + y) % 3 != 0) & (y > 1)
            a[:] = 0; a[frond] = 255
        elif n == "skin":        # grey-white skin
            base = np.array([176, 172, 164]); t = base[None, None] * (0.88 + noise(0.12))[..., None]
        elif n == "hair":
            t = np.zeros((16, 16, 3)) + 14 * (0.8 + noise(0.4))[..., None]
        elif n == "eyes":        # eyes layer: only eye texels opaque
            t = np.zeros((16, 16, 3)) + np.array([30, 30, 30])
            a[:] = 255
        elif n == "torch_body":
            t = np.zeros((16, 16, 3)) + np.array([40, 40, 44]) * (0.8 + noise(0.3))[..., None]
        elif n == "lens":
            t = np.zeros((16, 16, 3)) + np.array([250, 240, 210])
        elif n == "moon":
            d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
            v = np.clip(1.0 - d / 7.5, 0, 1) > 0
            t = np.zeros((16, 16, 3)) + (200 * (0.8 + noise(0.2)))[..., None] * v[..., None]
        elif n == "trail":
            base = np.array([96, 74, 52]); t = base[None, None] * (0.65 + noise(0.5))[..., None]
        elif n == "phone":
            t = np.zeros((16, 16, 3)) + np.array([20, 20, 22])
            scr = (x > 2) & (x < 13) & (y > 2) & (y < 13); t[scr] = np.array([60, 90, 70])
        elif n == "flare":
            t = np.zeros((16, 16, 3)) + np.array([180, 30, 24]) * (0.8 + noise(0.3))[..., None]
            tip = y < 4; t[tip] = np.array([255, 230, 200])
        elif n == "lamp":
            t = np.zeros((16, 16, 3)) + np.array([60, 58, 54])
            glass = (x > 3) & (x < 12) & (y > 4) & (y < 12); t[glass] = np.array([250, 170, 70])
        atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16, :3] = np.clip(t, 0, 255).astype(np.uint8)
        atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16, 3] = np.clip(a, 0, 255).astype(np.uint8)
        real = real_tile(n)
        if real is not None:
            REAL_TILES.add(n)
            atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16] = real
            if n == "leaves":                    # cutout: binary alpha like the alpha test
                atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16, 3] = np.where(real[..., 3] > 25, 255, 0)
    return atlas


REAL_TILES = set()


# block type: (tile(s), mc_Entity id, opaque, emission, layer, shape)
#   tile: name or dict(top=..., side=..., bottom=...)
#   layer: "solid" (incl. cutout) | "translucent"     shape: None (cube) | box tuple | "cross"
AIR, BARK, LEAVES, FLOOR, DIRT, MOSS, ROCK, STONES, WATER, PLANKS, TIN, CONCRETE, ASPHALT, RIBBON, FERN, TRAIL, LAMP = range(17)
BLOCKS = {
    BARK: (dict(top="log_top", side="bark", bottom="log_top"), 0, True, 0, "solid", None),
    LEAVES: ("leaves", 10011, False, 0, "solid", None),
    FLOOR: (dict(top="floor_top", side="floor_side", bottom="dirt"), 0, True, 0, "solid", None),
    DIRT: ("dirt", 0, True, 0, "solid", None),
    MOSS: (dict(top="moss", side="floor_side", bottom="dirt"), 0, True, 0, "solid", None),
    ROCK: ("rock", 10032, True, 0, "solid", None),
    STONES: ("stones", 10032, True, 0, "solid", None),
    WATER: ("water", 10020, False, 0, "translucent", (0.0, 0.0, 0.0, 1.0, 0.875, 1.0)),
    PLANKS: ("planks", 0, True, 0, "solid", None),
    TIN: ("tin", 10031, True, 0, "solid", None),
    CONCRETE: ("concrete", 10032, True, 0, "solid", None),
    ASPHALT: ("asphalt", 10032, True, 0, "solid", None),
    RIBBON: (dict(top="log_top", side="ribbon", bottom="log_top"), 10033, True, 0, "solid", None),
    FERN: ("fern", 10010, False, 0, "solid", "cross"),
    TRAIL: (dict(top="trail", side="dirt", bottom="dirt"), 0, True, 0, "solid", None),
    LAMP: ("lamp", 10002, False, 14, "solid", (0.25, 0.2, 0.7, 0.75, 0.8, 1.0)),
}


def lcg(seed):
    s = [seed & 0xFFFFFFFF]

    def r():
        s[0] = (1103515245 * s[0] + 12345) & 0x7FFFFFFF
        return s[0] / 0x7FFFFFFF
    return r


class Scene:
    """voxel scene, world coordinates = index + origin"""

    def __init__(self, size=(64, 48, 64), origin=(0, 58, 0)):
        self.sx, self.sy, self.sz = size
        self.origin = np.array(origin)
        self.b = np.zeros((self.sx, self.sy, self.sz), np.int16)
        self.ground = np.zeros((self.sx, self.sz), np.int32)
        self.trunks = []
        self.build()

    def set(self, x, y, z, t):
        o = self.origin
        X, Y, Z = x - o[0], y - o[1], z - o[2]
        if 0 <= X < self.sx and 0 <= Y < self.sy and 0 <= Z < self.sz:
            self.b[X, Y, Z] = t

    def get(self, x, y, z):
        o = self.origin
        X, Y, Z = x - o[0], y - o[1], z - o[2]
        if 0 <= X < self.sx and 0 <= Y < self.sy and 0 <= Z < self.sz:
            return int(self.b[X, Y, Z])
        return AIR

    def fill(self, x0, y0, z0, x1, y1, z1, t):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, t)

    def height(self, x, z):
        """surface height (the top solid block) of the slope"""
        h = 74.0 - 0.085 * z + 1.0 * math.sin(x * 0.21 + z * 0.13) + 0.7 * math.sin(z * 0.31 - x * 0.07)
        # the fog hollow with the stream
        d = math.hypot((x - 40) / 11.0, (z - 46) / 8.0)
        h -= 5.5 * max(0.0, 1.0 - d * d) if d < 1 else 0.0
        # the stream gully running along x through the hollow
        if abs(z - 47) <= 1 and x >= 22:
            h = min(h, 74.0 - 0.085 * 47 - 6.5)
        return int(round(h))

    def build(self):
        rnd = lcg(20261102)
        # ---- terrain --------------------------------------------------------------------
        for x in range(self.sx):
            for z in range(self.sz):
                h = self.height(x, z)
                self.ground[x, z] = h
                for y in range(self.origin[1], h):
                    self.set(x, y, z, DIRT)
                top = FLOOR
                if abs(z - 47) <= 2 and x >= 22:
                    top = STONES
                elif rnd() < 0.12:
                    top = MOSS
                self.set(x, h, z, top)
        # the stream: water over the stones in the gully
        for x in range(22, self.sx):
            for z in (46, 47, 48):
                h = self.ground[x, z]
                if z == 47 or h <= self.ground[x, 47] + 0:
                    self.set(x, h + 1, z, WATER)
        # a faded trail across the slope
        for z in range(0, 40):
            x = int(30 + 3 * math.sin(z * 0.15))
            for dx in (0, 1):
                self.set(x + dx, self.ground[x + dx, z], z, TRAIL)
        # ---- the hill with the road tunnel (x 0..13) -------------------------------------
        for x in range(0, 14):
            for z in range(0, 44):
                h = self.ground[x, z]
                top = h + 12 - max(0, z - 34)
                for y in range(h + 1, max(top, h + 1) + 1):
                    self.set(x, y, z, ROCK)
        tunnel_floor = self.ground[6, 30]
        self.tunnel_floor = tunnel_floor
        for z in range(8, 44):
            for x in range(3, 11):
                for y in range(tunnel_floor + 1, tunnel_floor + 6):
                    self.set(x, y, z, AIR)
                self.set(x, tunnel_floor, z, ASPHALT)
                for y in range(self.origin[1], tunnel_floor):
                    self.set(x, y, z, DIRT)
            for y in range(tunnel_floor, tunnel_floor + 7):
                self.set(2, y, z, CONCRETE)
                self.set(11, y, z, CONCRETE)
            for x in range(2, 12):
                self.set(x, tunnel_floor + 6, z, CONCRETE)
        self.fill(2, tunnel_floor, 7, 11, tunnel_floor + 6, 7, CONCRETE)      # the dead end
        self.set(10, tunnel_floor + 4, 18, LAMP)                             # a dead sodium lamp
        # ---- the hut (x 46..51, z 16..21) -------------------------------------------------
        hx0, hx1, hz0, hz1 = 46, 51, 16, 21
        hb = max(self.ground[x, z] for x in range(hx0, hx1 + 1) for z in range(hz0, hz1 + 1))
        self.hut_floor = hb
        for x in range(hx0, hx1 + 1):
            for z in range(hz0, hz1 + 1):
                for y in range(self.ground[x, z], hb + 1):
                    self.set(x, y, z, PLANKS)
                edge = x in (hx0, hx1) or z in (hz0, hz1)
                for y in range(hb + 1, hb + 4):
                    self.set(x, y, z, PLANKS if edge else AIR)
        for y in range(hb + 1, hb + 3):
            self.set(48, y, hz0, AIR)            # the door, facing north (uphill)
            self.set(49, y, hz0, AIR)
        for x in range(hx0 - 1, hx1 + 2):
            for z in range(hz0 - 1, hz1 + 2):
                self.set(x, hb + 4, z, TIN)
        # ---- cedars -------------------------------------------------------------------------
        def free(x, z):
            if x < 15 or x > self.sx - 2 or z < 1 or z > self.sz - 2:
                return False
            if hx0 - 2 <= x <= hx1 + 2 and hz0 - 2 <= z <= hz1 + 2:
                return False
            if abs(z - 47) <= 2 and x >= 21:
                return False
            if (x - 40) ** 2 / 81.0 + (z - 46) ** 2 / 36.0 < 1.0 and (x + z) % 3 != 0:
                return False
            if 29 <= x <= 32 and z < 40 and abs(x - (30 + 3 * math.sin(z * 0.15))) < 1.5:
                return False
            # keep the view from the forest camera (30.5, 9.5) open in front
            dx, dz = x + 0.5 - 30.5, z + 0.5 - 9.5
            if dx * dx + dz * dz < 4.5 * 4.5:
                return False
            if dz > 0 and abs(dx) < 1.2 + dz * 0.12 and dz < 13:
                return False
            return True
        # hand-placed trunks framing the forest view: a field of trunks receding into the fog
        manual = [(27, 15), (34, 14), (25, 20), (36, 19), (33, 24), (28, 27), (23, 26), (38, 29), (31, 33), (26, 36), (35, 38)]
        spots = list(manual)
        for gx in range(15, self.sx, 5):
            for gz in range(0, self.sz, 5):
                x = gx + int(rnd() * 4)
                z = gz + int(rnd() * 4)
                if free(x, z) and all((x - a) ** 2 + (z - b) ** 2 > 9 for a, b in spots):
                    spots.append((x, z))
        self.monster_trunk = (33, 24)
        for i, (x, z) in enumerate(spots):
            g = self.ground[x, z]
            trunk_h = 9 + int(rnd() * 5)
            top = g + trunk_h
            ribbon = (x, z) == (28, 27)
            for y in range(g + 1, top + 1):
                self.set(x, y, z, RIBBON if (ribbon and y == g + 2) else BARK)
            self.trunks.append((x, g, z, top))
            # a narrow dark cone of needles, with gaps
            levels = 8
            for k in range(levels):
                yy = top - 2 + k
                rad = 2.0 * (1.0 - k / levels) + 0.35
                for dx in range(-3, 4):
                    for dz in range(-3, 4):
                        if dx == 0 and dz == 0 and yy <= top:
                            continue
                        if dx * dx + dz * dz <= rad * rad and rnd() > 0.22:
                            if self.get(x + dx, yy, z + dz) == AIR:
                                self.set(x + dx, yy, z + dz, LEAVES)
        # ---- ferns ----------------------------------------------------------------------------
        for x in range(15, self.sx):
            for z in range(0, self.sz):
                if rnd() < 0.10:
                    g = self.ground[x, z]
                    if self.get(x, g, z) in (FLOOR, MOSS) and self.get(x, g + 1, z) == AIR:
                        self.set(x, g + 1, z, FERN)

    def inside(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def opaque(self, x, y, z):
        if not self.inside(x, y, z):
            return False
        t = self.b[x, y, z]
        return t != 0 and BLOCKS[t][2]

    def compute_light(self):
        sx, sy, sz = self.sx, self.sy, self.sz
        op = np.zeros_like(self.b, dtype=bool)
        for t, d in BLOCKS.items():
            if d[2]:
                op |= self.b == t
        leaf = self.b == LEAVES
        sky = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for x in range(sx):
            for z in range(sz):
                lvl = 15
                for y in range(sy - 1, -1, -1):
                    if op[x, y, z]:
                        break
                    if leaf[x, y, z]:
                        lvl -= 1
                    sky[x, y, z] = max(lvl, 0)
                    if lvl > 1:
                        q.append((x, y, z))
        self._bfs(sky, q, op, leaf)
        blk = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for t, d in BLOCKS.items():
            if d[3] > 0:
                for (x, y, z) in zip(*np.nonzero(self.b == t)):
                    blk[x, y, z] = max(blk[x, y, z], d[3])
                    q.append((x, y, z))
        self._bfs(blk, q, op, leaf)
        self.sky, self.blk = sky, blk

    def _bfs(self, L, q, op, leaf):
        sx, sy, sz = L.shape
        while q:
            x, y, z = q.popleft()
            base = L[x, y, z] - 1
            if base <= 0:
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                X, Y, Z = x + dx, y + dy, z + dz
                if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz and not op[X, Y, Z]:
                    nl = base - (1 if leaf[X, Y, Z] else 0)
                    if L[X, Y, Z] < nl:
                        L[X, Y, Z] = nl
                        q.append((X, Y, Z))

    def light_at(self, x, y, z):
        if not self.inside(x, y, z):
            return 0, 15
        return int(self.blk[x, y, z]), int(self.sky[x, y, z])

    def mesh(self):
        """-> dict layer -> arrays (world positions etc.)"""
        faces = {"solid": [], "translucent": []}
        crosses = []
        dirs = [((1, 0, 0), "side"), ((-1, 0, 0), "side"), ((0, 1, 0), "top"), ((0, -1, 0), "bottom"), ((0, 0, 1), "side"), ((0, 0, -1), "side")]
        for (x, y, z) in zip(*np.nonzero(self.b)):
            t = int(self.b[x, y, z])
            tile, bid, opq, emit, layer, shape = BLOCKS[t]
            if shape == "cross":
                crosses.append((x, y, z, tile, bid))
                continue
            box = shape
            x0, y0, z0, x1, y1, z1 = box if box else (0, 0, 0, 1, 1, 1)
            for (n, kind) in dirs:
                nx, ny, nz = x + n[0], y + n[1], z + n[2]
                if box is None:
                    if self.opaque(nx, ny, nz):
                        continue
                    if self.inside(nx, ny, nz) and self.b[nx, ny, nz] == t and t != LEAVES:
                        continue
                elif layer == "translucent" and self.inside(nx, ny, nz) and self.b[nx, ny, nz] == t:
                    if n[1] == 0:
                        continue
                tname = tile if isinstance(tile, str) else tile[kind]
                faces[layer].append((x, y, z, n, tname, bid, (x0, y0, z0, x1, y1, z1), box is not None))
        out = {}
        for layer, fl in faces.items():
            out[layer] = self._build_arrays(fl)
        cr = self._cross_arrays(crosses)
        out["solid"] = {k: np.concatenate([out["solid"][k], cr[k]]) for k in out["solid"]}
        return out

    def _cross_arrays(self, crosses):
        P, N, Cc, U0, U1, E, M = [], [], [], [], [], [], []
        o = self.origin
        for (x, y, z, tname, bid) in crosses:
            tx, ty = TILES[tname]
            u0, v0, u1, v1 = tx / ATLAS_N, ty / ATLAS_N, (tx + 1) / ATLAS_N, (ty + 1) / ATLAS_N
            mid = ((u0 + u1) / 2, (v0 + v1) / 2)
            bl, sl = self.light_at(x, y, z)
            lu, lv = (bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0
            wx, wy, wz = x + o[0], y + o[1], z + o[2]
            for (ax, az, bx, bz, n) in ((0.15, 0.15, 0.85, 0.85, (0.707, 0, -0.707)), (0.15, 0.85, 0.85, 0.15, (0.707, 0, 0.707))):
                cs = [(wx + ax, wy, wz + az), (wx + bx, wy, wz + bz), (wx + bx, wy + 0.9, wz + bz), (wx + ax, wy + 0.9, wz + az)]
                uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
                for i in (0, 1, 2, 0, 2, 3):
                    P.append(cs[i]); N.append(n); Cc.append((1, 1, 1, 1)); U0.append(uvs[i]); U1.append((lu, lv))
                    E.append((bid, 0, 0, 0)); M.append((mid[0], mid[1], 0, 1))
        f = lambda a, k: np.array(a, np.float32).reshape(-1, k)
        return dict(pos=f(P, 3), nrm=f(N, 3), col=f(Cc, 4), uv0=f(U0, 2), uv1=f(U1, 2), ent=f(E, 4), mid=f(M, 4))

    def _build_arrays(self, fl):
        P, N, Cc, U0, U1, E, M = [], [], [], [], [], [], []
        o = self.origin
        for (x, y, z, n, tname, bid, bb, is_box) in fl:
            x0, y0, z0, x1, y1, z1 = bb
            n = np.array(n)
            ax = int(np.argmax(np.abs(n)))
            if ax == 0:
                xf = x1 if n[0] > 0 else x0
                corners = [(xf, y0, z0), (xf, y0, z1), (xf, y1, z1), (xf, y1, z0)]
            elif ax == 1:
                yf = y1 if n[1] > 0 else y0
                corners = [(x0, yf, z0), (x1, yf, z0), (x1, yf, z1), (x0, yf, z1)]
            else:
                zf = z1 if n[2] > 0 else z0
                corners = [(x0, y0, zf), (x1, y0, zf), (x1, y1, zf), (x0, y1, zf)]
            tx, ty = TILES[tname]
            u0, v0, u1, v1 = tx / ATLAS_N, ty / ATLAS_N, (tx + 1) / ATLAS_N, (ty + 1) / ATLAS_N
            mid = ((u0 + u1) / 2, (v0 + v1) / 2)
            fc = (x + n[0], y + n[1], z + n[2])
            verts = []
            for (cx, cy, cz) in corners:
                if ax == 1:
                    uu, vv = (cx - x0) / max(x1 - x0, 1e-6), (cz - z0) / max(z1 - z0, 1e-6)
                elif ax == 0:
                    uu, vv = (cz - z0) / max(z1 - z0, 1e-6), 1 - (cy - y0) / max(y1 - y0, 1e-6)
                else:
                    uu, vv = (cx - x0) / max(x1 - x0, 1e-6), 1 - (cy - y0) / max(y1 - y0, 1e-6)
                uv = (u0 + (u1 - u0) * uu, v0 + (v1 - v0) * vv)
                if is_box:
                    bl, sl = self.light_at(x, y, z)
                    ao = 1.0
                else:
                    s = [1 if c > 0.5 else -1 for c in (cx - x, cy - y, cz - z)]
                    others = [i for i in range(3) if i != ax]
                    a1, a2 = others
                    c1 = list(fc); c1[a1] += s[a1]
                    c2 = list(fc); c2[a2] += s[a2]
                    c3 = list(fc); c3[a1] += s[a1]; c3[a2] += s[a2]
                    cells = [fc, tuple(c1), tuple(c2), tuple(c3)]
                    occ = [self.opaque(*c) for c in cells]
                    if occ[1] and occ[2]:
                        occ[3] = True
                    lights = [self.light_at(*c) for c, oc in zip(cells, occ) if not oc]
                    if not lights:
                        lights = [self.light_at(*fc)]
                    bl = sum(l[0] for l in lights) / len(lights)
                    sl = sum(l[1] for l in lights) / len(lights)
                    ao = sum(0.2 if oc else 1.0 for oc in occ) / 4.0
                wp = (cx + o[0] + x, cy + o[1] + y, cz + o[2] + z)
                verts.append((wp, uv, (bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0, ao))
            p0, p1, p2 = (np.array(verts[i][0]) for i in range(3))
            if np.dot(np.cross(p1 - p0, p2 - p0), n) < 0:
                verts = verts[::-1]
            tint = (0.24, 0.42, 0.70) if bid == 10020 else (1.0, 1.0, 1.0)
            for i in (0, 1, 2, 0, 2, 3):
                wp, uv, lu, lv, ao = verts[i]
                P.append(wp); N.append(n); Cc.append((tint[0], tint[1], tint[2], ao)); U0.append(uv); U1.append((lu, lv))
                E.append((bid, 0, 0, 0)); M.append((mid[0], mid[1], 0, 1))
        f = lambda a, k: np.array(a, np.float32).reshape(-1, k)
        return dict(pos=f(P, 3), nrm=f(N, 3), col=f(Cc, 4), uv0=f(U0, 2), uv1=f(U1, 2), ent=f(E, 4), mid=f(M, 4))



# ---- the real mountain: tools/terrain's baked terrain + a port of the chunk decorator -------
TERRAIN_FILE = os.environ.get("SOUNAN_TERRAIN") or os.path.normpath(
    os.path.join(ROOT, "..", "..", "resources", "data", "sounan", "terrain", "terrain.bin.gz"))
M64 = (1 << 64) - 1


def _mix64(z):
    z &= M64
    z = ((z ^ (z >> 30)) * 0xbf58476d1ce4e5b9) & M64
    z = ((z ^ (z >> 27)) * 0x94d049bb133111eb) & M64
    return z ^ (z >> 31)


def jhash(x, z, salt):
    """com.sounan.world.Decorator.hash (Java int)"""
    v = (x * 0x9E3779B97F4A7C15 + z * 0xC2B2AE3D27D4EB4F + salt * 0x165667B19E3779F9 + 0x2545F4914F6CDD1D) & M64
    r = _mix64(v) & 0xFFFFFFFF
    return r - (1 << 32) if r >= (1 << 31) else r


def jrand(x, z, salt):
    return ((jhash(x, z, salt) & 0xFFFFFFFF) >> 1) / 2147483648.0


class Terrain:
    def __init__(self, path=TERRAIN_FILE):
        import gzip
        import struct
        raw = gzip.open(path).read()
        assert raw[:4] == b"SNTR", "bad terrain magic"
        self.size, self.org = struct.unpack(">Hh", raw[4:8])
        n = self.size
        planes = np.frombuffer(raw[8:8 + 5 * n * n], np.uint8).reshape(5, n, n)
        self.h, self.surf, self.trees, self.water, self.area = (p.astype(np.int32) for p in planes)

    def _get(self, plane, x, z, outside):
        i, j = x - self.org, z - self.org
        if 0 <= i < self.size and 0 <= j < self.size:
            return int(plane[j, i])
        return outside

    def height(self, x, z): return self._get(self.h, x, z, 236)
    def surface(self, x, z): return self._get(self.surf, x, z, 3)
    def tree(self, x, z): return self._get(self.trees, x, z, 0)
    def wat(self, x, z): return self._get(self.water, x, z, 0)


class TerrainScene(Scene):
    """a 72x72 piece of Kaerazu-yama around (cx, cz), decorated like MountainGenerator"""
    SURF = {0: FLOOR, 1: MOSS, 2: TRAIL, 3: ROCK, 4: STONES, 5: STONES, 6: ASPHALT, 7: DIRT, 8: ROCK}

    def __init__(self, terrain, cx, cz, half=36):
        self.t = terrain
        self.x0, self.z0 = int(cx) - half, int(cz) - half
        hs = [terrain.height(x, z) for x in range(self.x0, self.x0 + 2 * half) for z in range(self.z0, self.z0 + 2 * half)]
        y0 = min(hs) - 4
        super().__init__(size=(2 * half, max(hs) + 26 - y0, 2 * half), origin=(self.x0, y0, self.z0))

    def build(self):
        t, o = self.t, self.origin
        X0, Z0, X1, Z1 = self.x0, self.z0, self.x0 + self.sx - 1, self.z0 + self.sz - 1
        for i in range(self.sx):
            for k in range(self.sz):
                x, z = X0 + i, Z0 + k
                h = t.height(x, z)
                self.ground[i, k] = h
                for y in range(o[1], h):
                    self.set(x, y, z, DIRT if y >= h - 3 and t.surface(x, z) != 3 else ROCK)
                self.set(x, h, z, self.SURF.get(t.surface(x, z), FLOOR))
                for y in range(h + 1, t.wat(x, z) + 1):
                    self.set(x, y, z, WATER)

        def soft(x, y, z, b):
            if X0 <= x <= X1 and Z0 <= z <= Z1 and self.get(x, y, z) == AIR:
                self.set(x, y, z, b)
        # cedars (Decorator.collectTrees / makeTree / trunk / crown)
        trees = []
        for gx in range((X0 - 4) // 3, (X1 + 4) // 3 + 1):
            for gz in range((Z0 - 4) // 3, (Z1 + 4) // 3 + 1):
                x = gx * 3 + jhash(gx, gz, 1) % 3
                z = gz * 3 + jhash(gx, gz, 2) % 3
                d = t.tree(x, z) / 255.0
                if d <= 0.0 or jrand(gx, gz, 3) > d * 0.66:
                    continue
                if t.surface(x, z) not in (0, 1, 8, 3, 7) or t.wat(x, z) > 0:
                    continue
                base = t.height(x, z)
                hh = 14 + jhash(x, z, 11) % 8
                crown = base + int(hh * 0.46) + jhash(x, z, 12) % 3
                trees.append((x, z, base, hh, crown, 2.0 + jrand(x, z, 13) * 1.1))
        for (x, z, base, hh, crown, rad) in trees:
            if X0 <= x <= X1 and Z0 <= z <= Z1:
                for y in range(base + 1, base + hh + 1):
                    self.set(x, y, z, BARK)
                self.trunks.append((x - o[0], base, z - o[2], base + hh))
        for (x, z, base, hh, crown, rad) in trees:
            top = base + hh + 1
            span = max(1, top - crown)
            for y in range(crown, top + 1):
                rel = (y - crown) / span
                r = rad * (1.0 - rel) ** 0.85 + 0.35
                if y == crown:
                    r *= 0.7
                ri = int(math.ceil(r))
                for dx in range(-ri, ri + 1):
                    for dz in range(-ri, ri + 1):
                        xx, zz = x + dx, z + dz
                        jit = (jrand(xx * 7 + y, zz * 13 - y, 17) - 0.5) * 0.9
                        if dx * dx + dz * dz > r * r + jit or jrand(xx + y * 31, zz, 19) < 0.05:
                            continue
                        soft(xx, y, zz, LEAVES)
        # boulders, fallen logs
        for gx in range((X0 - 3) // 11, (X1 + 3) // 11 + 1):
            for gz in range((Z0 - 3) // 11, (Z1 + 3) // 11 + 1):
                if jrand(gx, gz, 31) > 0.2:
                    continue
                cx, cz = gx * 11 + jhash(gx, gz, 32) % 11, gz * 11 + jhash(gx, gz, 33) % 11
                if t.tree(cx, cz) < 60 or t.wat(cx, cz) > 0 or t.surface(cx, cz) == 2:
                    continue
                r = 1 + jhash(gx, gz, 34) % 2
                base = t.height(cx, cz)
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        x, z = cx + dx, cz + dz
                        if t.surface(x, z) == 2:
                            continue
                        hh = r + 1 - max(abs(dx), abs(dz)) - (1 if jrand(x, z, 35) < 0.4 else 0)
                        for y in range(t.height(x, z) + 1, base + hh + 1):
                            soft(x, y, z, ROCK)
        for gx in range((X0 - 8) // 14, (X1 + 8) // 14 + 1):
            for gz in range((Z0 - 8) // 14, (Z1 + 8) // 14 + 1):
                if jrand(gx, gz, 41) > 0.24:
                    continue
                sx_, sz_ = gx * 14 + jhash(gx, gz, 42) % 14, gz * 14 + jhash(gx, gz, 43) % 14
                if t.tree(sx_, sz_) < 80:
                    continue
                along_x = (jhash(gx, gz, 44) & 1) == 0
                for kk in range(4 + jhash(gx, gz, 45) % 4):
                    x, z = sx_ + (kk if along_x else 0), sz_ + (0 if along_x else kk)
                    if t.surface(x, z) == 2 or t.wat(x, z) > 0:
                        break
                    soft(x, t.height(x, z) + 1, z, BARK)
        # undergrowth: ferns (large ferns as two), dead bushes
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if t.wat(x, z) > 0 or t.surface(x, z) not in (0, 1, 7):
                    continue
                y = t.height(x, z) + 1
                if self.get(x, y, z) != AIR or (t.tree(x, z) == 0 and t.surface(x, z) == 7):
                    continue
                r = jrand(x, z, 51)
                if r < 0.08:
                    self.set(x, y, z, FERN)
                    if 0.06 <= r < 0.075 and self.get(x, y + 1, z) == AIR:
                        self.set(x, y + 1, z, FERN)


def quad_arrays(quads, light):
    """quads: list of (normal, 4 corners, tile name); light: (lu, lv)"""
    P, N, Cc, U0, U1 = [], [], [], [], []
    for n, cs, tname in quads:
        tx, ty = TILES[tname]
        uvs = [(tx / ATLAS_N, (ty + 1) / ATLAS_N), (tx / ATLAS_N, ty / ATLAS_N), ((tx + 1) / ATLAS_N, ty / ATLAS_N), ((tx + 1) / ATLAS_N, (ty + 1) / ATLAS_N)]
        for i in (0, 1, 2, 0, 2, 3):
            P.append(cs[i]); N.append(n); Cc.append((1, 1, 1, 1)); U0.append(uvs[i]); U1.append(light)
    f = lambda a, k: np.array(a, np.float32).reshape(-1, k)
    return dict(pos=f(P, 3), nrm=f(N, 3), col=f(Cc, 4), uv0=f(U0, 2), uv1=f(U1, 2))


def box_quads(wmin, wmax, tiles):
    (x0, y0, z0), (x1, y1, z1) = wmin, wmax
    return [
        ((1, 0, 0), [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], tiles["side"]),
        ((-1, 0, 0), [(x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0)], tiles["side"]),
        ((0, 0, 1), [(x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (x0, y0, z1)], tiles.get("front", tiles["side"])),
        ((0, 0, -1), [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)], tiles.get("north", tiles["side"])),
        ((0, 1, 0), [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], tiles["top"]),
    ]


def monster_meshes(scene, trunk, far=None):
    """a 覗き peeking from behind a trunk toward the camera (-z): tall thin grey body hidden
    behind the trunk, the head tilted out past its edge, an eyes layer; plus a second pair of
    eyes far off to the side, outside the beam. Returns (body arrays, eyes arrays)."""
    tx, tz = trunk
    bx, bz = tx + 0.5, tz + 1.45
    g = scene.ground[tx - scene.origin[0], tz - scene.origin[2]] + 1
    cell = (tx - scene.origin[0], g - scene.origin[1] + 1, tz + 1 - scene.origin[2])
    bl, sl = scene.light_at(*cell)
    light = ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0)
    quads = box_quads((bx - 0.3, g, bz - 0.3), (bx + 0.3, g + 2.15, bz + 0.3), {"side": "skin", "top": "hair"})
    # PEEK_LEFT: the head leans out past the left edge of the trunk (the camera's side)
    hx, hz = tx - 0.24, tz + 0.55
    quads += box_quads((hx - 0.22, g + 2.0, hz - 0.22), (hx + 0.22, g + 2.46, hz + 0.22), {"side": "hair", "north": "skin", "top": "hair"})
    quads += box_quads((tx - 0.10, g + 1.3, tz + 0.02), (tx + 0.05, g + 1.62, tz + 0.12), {"side": "skin", "top": "skin"})   # fingers on the bark
    body = quad_arrays(quads, light)
    ez = hz - 0.225
    eyes = []
    for ex in (hx - 0.19, hx + 0.03):
        eyes.append(((0, 0, -1), [(ex, g + 2.22, ez), (ex, g + 2.32, ez), (ex + 0.16, g + 2.32, ez), (ex + 0.16, g + 2.22, ez)], "eyes"))
    # a second one far off in the dark: only its eyes
    fx, fz = far if far else (tx - 10.5, tz + 7.6)
    if not (0 <= int(fx) - scene.origin[0] < scene.sx and 0 <= int(fz) - scene.origin[2] < scene.sz):
        fx, fz = tx + 0.5, tz + 0.5
    fg = scene.ground[int(fx) - scene.origin[0], int(fz) - scene.origin[2]] + 1
    for ex in (fx - 0.19, fx + 0.03):
        eyes.append(((0, 0, -1), [(ex, fg + 2.2, fz), (ex, fg + 2.3, fz), (ex + 0.16, fg + 2.3, fz), (ex + 0.16, fg + 2.2, fz)], "eyes"))
    return body, quad_arrays(eyes, light)


def ghost_mesh(scene, x, z):
    """写るもの: a translucent pale hiker only the phone camera sees (entities_translucent)"""
    g = scene.ground[int(x) - scene.origin[0], int(z) - scene.origin[2]] + 1
    cell = (int(x) - scene.origin[0], g - scene.origin[1] + 1, int(z) - scene.origin[2])
    bl, sl = scene.light_at(*cell)
    quads = box_quads((x - 0.3, g, z - 0.25), (x + 0.3, g + 1.8, z + 0.25), {"side": "skin", "top": "skin"})
    arr = quad_arrays(quads, ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0))
    arr["col"][:, 3] = 0.45
    return arr


def hand_mesh(item, side, light):
    """the held item in view space (Iris draws it with its own projection)"""
    sx = 1.0 if side > 0 else -1.0
    if item in (FLASHLIGHT, FLASH_LOW):
        x0, x1, y0, y1, z0, z1 = 0.20, 0.30, -0.36, -0.27, -0.62, -0.30
        tiles = {"side": "torch_body", "top": "torch_body", "front": "torch_body"}
        lens = "lens"
    elif item in (PHONE_LIGHT, CAMERA):
        x0, x1, y0, y1, z0, z1 = 0.10, 0.24, -0.32, -0.12, -0.46, -0.44
        tiles = {"side": "phone", "top": "phone", "front": "phone"}
        lens = None
    elif item == FLARE:
        x0, x1, y0, y1, z0, z1 = 0.26, 0.31, -0.44, -0.24, -0.55, -0.50
        tiles = {"side": "flare", "top": "flare", "front": "flare"}
        lens = None
    else:
        return None
    if sx < 0:
        x0, x1 = -x1, -x0
    quads = box_quads((x0, y0, z0), (x1, y1, z1), tiles)
    if lens:
        quads.append(((0, 0, -1), [(x0, y0, z0 - 0.001), (x0, y1, z0 - 0.001), (x1, y1, z0 - 0.001), (x1, y0, z0 - 0.001)], lens))
    return quad_arrays(quads, light)


# =====================================================================================
#  renderer
# =====================================================================================

GLFMT = {"RGBA16F": glctx.GL_RGBA16F, "RGBA8": glctx.GL_RGBA8, "RGBA16": 0x805B, "RGB16F": 0x881B,
         "R11F_G11F_B10F": 0x8C3A, "RGBA32F": 0x8814}
UNITS = {"gtexture": 0, "noisetex": 1, "depthtex0": 2, "depthtex1": 3, "shadowtex0": 4}


def tex2d(w, h, ifmt, fmt, typ, data=None, filt=glctx.GL_LINEAR, wrap=glctx.GL_CLAMP_TO_EDGE):
    t = glctx.gen_texture()
    gl.glBindTexture(glctx.GL_TEXTURE_2D, t)
    ptr = data.ctypes.data if data is not None else None
    gl.glTexImage2D(glctx.GL_TEXTURE_2D, 0, ifmt, w, h, 0, fmt, typ, ptr)
    gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_MIN_FILTER, filt)
    gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_MAG_FILTER, filt)
    gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_WRAP_S, wrap)
    gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_WRAP_T, wrap)
    return t


FULL_QUAD = None


def full_quad():
    global FULL_QUAD
    if FULL_QUAD is None:
        FULL_QUAD = dict(pos=np.array([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 0, 0), (1, 1, 0), (0, 1, 0)], np.float32),
                         nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)), col=np.ones((6, 4), np.float32),
                         uv0=np.array([(0, 0), (1, 0), (1, 1), (0, 0), (1, 1), (0, 1)], np.float32), uv1=np.zeros((6, 2), np.float32))
    return FULL_QUAD


class Renderer:
    def __init__(self, folder, w, h, overrides):
        self.folder, self.w, self.h = folder, w, h
        self.progs = {}
        for n in build_pack.PROGRAMS:
            try:
                p = Program(folder, n, overrides)
            except FileNotFoundError:
                continue
            if p.errors:
                raise RuntimeError("%s/%s failed:\n%s" % (folder or "root", n, "\n".join(p.errors)))
            self.progs[n] = p
        any_prog = self.progs["final"]
        self.shadow_res = int(any_prog.consts.get("shadowMapResolution", 2048))
        self.shadow_dist = any_prog.consts.get("shadowDistance", 48.0)
        self.sun_path = any_prog.consts.get("sunPathRotation", 0.0)
        fmts, self.clears = buffer_directives()
        self.formats = {i: GLFMT[f] for i, f in fmts.items()}
        self.units = dict(UNITS)
        for i in sorted(self.formats):
            self.units["colortex%d" % i] = 5 + i
        # colour buffers (ping-pong) + depth
        self.tex = {i: [tex2d(w, h, f, glctx.GL_RGBA, glctx.GL_FLOAT), tex2d(w, h, f, glctx.GL_RGBA, glctx.GL_FLOAT)] for i, f in self.formats.items()}
        self.cur = {i: 0 for i in self.formats}
        self.depth0 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.depth1 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.out_tex = tex2d(w, h, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE)
        sr = self.shadow_res
        self.shadow_depth = tex2d(sr, sr, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT)
        gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_MODE, glctx.GL_COMPARE_REF_TO_TEXTURE)
        gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_FUNC, glctx.GL_LEQUAL)
        self.shadow_color = tex2d(sr, sr, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE)
        self.fbo = glctx.gen_framebuffer()
        # the loader's noise texture: random RGB, alpha 255, linear, repeating (like Iris)
        rng = np.random.default_rng(3)
        nz = rng.integers(0, 256, (256, 256, 4), dtype=np.uint8)
        nz[..., 3] = 255
        self.noise = tex2d(256, 256, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, nz, wrap=glctx.GL_REPEAT)
        atlas = make_atlas()
        self.atlas = tex2d(16 * ATLAS_N, 16 * ATLAS_N, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, np.ascontiguousarray(atlas), filt=glctx.GL_NEAREST)
        self._keep = []

    # ---- framebuffer helpers ----------------------------------------------------------
    def bind_targets(self, targets, write_alt, depth=None):
        gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
        for i in range(8):
            gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0 + i, glctx.GL_TEXTURE_2D, 0, 0)
        bufs = []
        for k, t in enumerate(targets):
            tid = self.tex[t][1 - self.cur[t] if write_alt else self.cur[t]]
            gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0 + k, glctx.GL_TEXTURE_2D, tid, 0)
            bufs.append(glctx.GL_COLOR_ATTACHMENT0 + k)
        gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_DEPTH_ATTACHMENT, glctx.GL_TEXTURE_2D, depth or 0, 0)
        arr = (glctx.U * len(bufs))(*bufs)
        gl.glDrawBuffers(len(bufs), arr)
        st = gl.glCheckFramebufferStatus(glctx.GL_FRAMEBUFFER)
        if st != glctx.GL_FRAMEBUFFER_COMPLETE:
            raise RuntimeError("framebuffer incomplete 0x%x" % st)
        gl.glViewport(0, 0, self.w, self.h)

    def bind_samplers(self, prog, mip=()):
        gl.glUseProgram(prog.id)
        for name, unit in self.units.items():
            loc = prog.loc(name)
            if loc < 0:
                continue
            gl.glUniform1i(loc, unit)
        binds = {"gtexture": self.atlas, "noisetex": self.noise, "depthtex0": self.depth0, "depthtex1": self.depth1,
                 "shadowtex0": self.shadow_depth}
        for i in self.formats:
            binds["colortex%d" % i] = self.tex[i][self.cur[i]]
        for name, unit in self.units.items():
            gl.glActiveTexture(glctx.GL_TEXTURE0 + unit)
            gl.glBindTexture(glctx.GL_TEXTURE_2D, binds[name])
            if name.startswith("colortex"):
                idx = int(name[8:])
                f = glctx.GL_LINEAR_MIPMAP_LINEAR if idx in mip else glctx.GL_LINEAR
                if idx in mip:
                    gl.glGenerateMipmap(glctx.GL_TEXTURE_2D)
                gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_MIN_FILTER, f)
        gl.glActiveTexture(glctx.GL_TEXTURE0)

    def set_uniforms(self, prog, U):
        for name, val in U.items():
            loc = prog.loc(name)
            if loc < 0:
                continue
            if isinstance(val, np.ndarray) and val.shape == (4, 4):
                m = np.ascontiguousarray(val, np.float32)
                gl.glUniformMatrix4fv(loc, 1, 1, glctx.fptr(m))
            elif isinstance(val, tuple) and len(val) == 3:
                gl.glUniform3f(loc, *[float(v) for v in val])
            elif isinstance(val, tuple) and len(val) == 4:
                gl.glUniform4f(loc, *[float(v) for v in val])
            elif isinstance(val, tuple) and len(val) == 2:
                gl.glUniform2i(loc, int(val[0]), int(val[1]))
            elif isinstance(val, int):
                gl.glUniform1i(loc, val)
            else:
                gl.glUniform1f(loc, float(val))

    def load_matrices(self, mv, proj):
        gl.glMatrixMode(glctx.GL_PROJECTION)
        gl.glLoadMatrixf(glctx.fptr(np.ascontiguousarray(proj.T, np.float32)))
        gl.glMatrixMode(glctx.GL_MODELVIEW)
        gl.glLoadMatrixf(glctx.fptr(np.ascontiguousarray(mv.T, np.float32)))
        for unit in (0, 1):
            gl.glActiveTexture(glctx.GL_TEXTURE0 + unit)
            gl.glMatrixMode(glctx.GL_TEXTURE)
            gl.glLoadIdentity()
        gl.glActiveTexture(glctx.GL_TEXTURE0)
        gl.glMatrixMode(glctx.GL_MODELVIEW)

    def draw_arrays(self, arrs, cam):
        pos = np.ascontiguousarray(arrs["pos"] - np.array(cam, np.float32))
        keep = [pos]
        gl.glEnableClientState(glctx.GL_VERTEX_ARRAY)
        gl.glVertexPointer(3, glctx.GL_FLOAT, 0, pos.ctypes.data)
        gl.glEnableClientState(glctx.GL_NORMAL_ARRAY)
        gl.glNormalPointer(glctx.GL_FLOAT, 0, arrs["nrm"].ctypes.data)
        gl.glEnableClientState(glctx.GL_COLOR_ARRAY)
        gl.glColorPointer(4, glctx.GL_FLOAT, 0, arrs["col"].ctypes.data)
        for unit, key in ((0, "uv0"), (1, "uv1")):
            gl.glClientActiveTexture(glctx.GL_TEXTURE0 + unit)
            gl.glEnableClientState(glctx.GL_TEXTURE_COORD_ARRAY)
            gl.glTexCoordPointer(2, glctx.GL_FLOAT, 0, arrs[key].ctypes.data)
        gl.glClientActiveTexture(glctx.GL_TEXTURE0)
        for name, loc in ATTRIBS.items():
            key = "ent" if name == "mc_Entity" else "mid"
            if key in arrs:
                gl.glEnableVertexAttribArray(loc)
                gl.glVertexAttribPointer(loc, 4, glctx.GL_FLOAT, 0, 0, arrs[key].ctypes.data)
            else:
                gl.glDisableVertexAttribArray(loc)
                gl.glVertexAttrib4f(loc, 0.0, 0.0, 0.0, 1.0)
        gl.glDrawArrays(glctx.GL_TRIANGLES, 0, len(pos))
        for name, loc in ATTRIBS.items():
            gl.glDisableVertexAttribArray(loc)
        self._keep = keep

    def gbuffer_draw(self, prog_name, U, arrs, mv, proj, cam, blend=None, extra=None):
        """blend: None = off; ('translucent', off_buffers) or ('additive',)"""
        p = self.progs[prog_name]
        self.bind_targets(p.targets, write_alt=False, depth=self.depth0)
        for k in range(len(p.targets)):
            gl.glDisablei(glctx.GL_BLEND, k)
        if blend and blend[0] == "translucent":
            gl.glBlendFuncSeparate(glctx.GL_SRC_ALPHA, glctx.GL_ONE_MINUS_SRC_ALPHA, glctx.GL_ONE, glctx.GL_ONE_MINUS_SRC_ALPHA)
            for k, t in enumerate(p.targets):
                if t not in blend[1]:
                    gl.glEnablei(glctx.GL_BLEND, k)
        elif blend and blend[0] == "additive":
            gl.glBlendFuncSeparate(glctx.GL_SRC_ALPHA, glctx.GL_ONE, glctx.GL_ZERO, glctx.GL_ONE)
            for k in range(len(p.targets)):
                gl.glEnablei(glctx.GL_BLEND, k)
        self.bind_samplers(p)
        U2 = dict(U)
        if extra:
            U2.update(extra)
        self.set_uniforms(p, U2)
        self.load_matrices(mv, proj)
        self.draw_arrays(arrs, cam)
        for k in range(len(p.targets)):
            gl.glDisablei(glctx.GL_BLEND, k)

    def fullscreen(self, prog_name, U):
        p = self.progs.get(prog_name)
        if p is None:
            return
        self.bind_targets(p.targets, write_alt=True)
        gl.glDisable(glctx.GL_DEPTH_TEST)
        gl.glDisable(glctx.GL_BLEND)
        self.bind_samplers(p, p.mipmaps)
        self.set_uniforms(p, U)
        self.load_matrices(np.eye(4, dtype=np.float32), ortho(0, 1, 0, 1, -1, 1))
        self.draw_arrays(full_quad(), (0, 0, 0))
        for t in p.targets:
            self.cur[t] = 1 - self.cur[t]

    def final_pass(self, U):
        p = self.progs["final"]
        gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
        for i in range(8):
            gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0 + i, glctx.GL_TEXTURE_2D, 0, 0)
        gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0, glctx.GL_TEXTURE_2D, self.out_tex, 0)
        gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_DEPTH_ATTACHMENT, glctx.GL_TEXTURE_2D, 0, 0)
        arr = (glctx.U * 1)(glctx.GL_COLOR_ATTACHMENT0)
        gl.glDrawBuffers(1, arr)
        gl.glViewport(0, 0, self.w, self.h)
        gl.glDisable(glctx.GL_DEPTH_TEST)
        gl.glDisable(glctx.GL_BLEND)
        self.bind_samplers(p, p.mipmaps)
        self.set_uniforms(p, U)
        self.load_matrices(np.eye(4, dtype=np.float32), ortho(0, 1, 0, 1, -1, 1))
        self.draw_arrays(full_quad(), (0, 0, 0))
        buf = np.zeros((self.h, self.w, 4), np.uint8)
        gl.glPixelStorei(glctx.GL_PACK_ALIGNMENT, 1)
        gl.glReadPixels(0, 0, self.w, self.h, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, buf.ctypes.data)
        return buf[::-1, :, :3].copy()

    def read_float(self, idx):
        t = self.tex[idx][self.cur[idx]]
        buf = np.zeros((self.h, self.w, 4), np.float32)
        gl.glBindTexture(glctx.GL_TEXTURE_2D, t)
        gl.glGetTexImage(glctx.GL_TEXTURE_2D, 0, glctx.GL_RGBA, glctx.GL_FLOAT, buf.ctypes.data)
        return buf

    # ---- one frame --------------------------------------------------------------------
    def render(self, shot, scene, meshes, monster, moon_quad, timing=None):
        w, h = self.w, self.h
        cam = np.array(shot["eye"], np.float64)
        fwd = np.array(shot["look"], np.float64)
        view = look_rot(fwd)
        proj = perspective(70.0, w / h, 0.05, 192.0 * 4)
        world_time = shot.get("time", 18000)
        sa = sky_angle(world_time)
        celestial = view @ rot("y", -90.0) @ rot("z", self.sun_path) @ rot("x", sa * 360.0)
        sun = (celestial @ np.array([0, 100, 0, 0], np.float32))[:3]
        moon = (celestial @ np.array([0, -100, 0, 0], np.float32))[:3]
        sun_angle = sa + 0.25 if sa < 0.75 else sa - 0.75
        light = sun if sun_angle <= 0.5 else moon
        light_w = np.linalg.inv(view)[:3, :3] @ light
        light_w /= np.linalg.norm(light_w)
        smv = look_rot(-light_w, up=(0, 0, 1))
        smv[:3, 3] = smv[:3, :3] @ (-light_w * 100.0)
        sd = self.shadow_dist
        sproj = ortho(-sd, sd, -sd, sd, 0.05, 256.0)
        ecell = (cam - scene.origin).astype(int)
        bl, sl = scene.light_at(*ecell)
        held, held2 = shot.get("held", 0), shot.get("held2", 0)
        t = shot.get("t", 123.4)
        U = {
            "gbufferModelView": view, "gbufferModelViewInverse": np.linalg.inv(view).astype(np.float32),
            "gbufferProjection": proj, "gbufferProjectionInverse": np.linalg.inv(proj).astype(np.float32),
            "shadowModelView": smv, "shadowModelViewInverse": np.linalg.inv(smv).astype(np.float32), "shadowProjection": sproj,
            "cameraPosition": tuple(cam), "sunPosition": tuple(sun), "moonPosition": tuple(moon), "shadowLightPosition": tuple(light),
            "fogColor": (0.02, 0.025, 0.03), "viewWidth": float(w), "viewHeight": float(h), "aspectRatio": w / h,
            "near": 0.05, "far": 192.0, "frameTimeCounter": t, "rainStrength": shot.get("fear", 0.0),
            "thunderStrength": shot.get("danger", 0.0), "nightVision": 1.0 if CAMERA in (held, held2) else 0.0,
            "blindness": 0.0, "darknessFactor": shot.get("darkness", 0.0), "darknessLightFactor": shot.get("darkness", 0.0) * 0.4,
            "frameCounter": int(t * 60), "isEyeInWater": 0, "heldItemId": held, "heldItemId2": held2,
            "heldBlockLightValue": 0, "heldBlockLightValue2": 0, "renderStage": 0, "entityId": 0,
            "currentRenderedItemId": 0, "moonPhase": 0, "eyeBrightnessSmooth": (bl * 16, sl * 16), "entityColor": (0.0, 0.0, 0.0, 0.0),
        }
        T = timing if timing is not None else {}

        def mark(name, t0):
            gl.glFinish()
            T[name] = T.get(name, 0.0) + time.time() - t0
            return time.time()

        tm = time.time()
        # ---- clear (to the directive clear colours) ----------------------------------------
        gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
        for i in self.formats:
            c = self.clears.get(i, (0.0, 0.0, 0.0, 0.0))
            for k in (0, 1):
                self.cur[i] = k
                self.bind_targets([i], write_alt=False)
                gl.glClearColor(*c)
                gl.glClear(glctx.GL_COLOR_BUFFER_BIT)
            self.cur[i] = 0
        # ---- shadow pass ------------------------------------------------------------------
        sp = self.progs.get("shadow")
        if sp is not None:
            gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
            for i in range(8):
                gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0 + i, glctx.GL_TEXTURE_2D, 0, 0)
            gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_COLOR_ATTACHMENT0, glctx.GL_TEXTURE_2D, self.shadow_color, 0)
            gl.glFramebufferTexture2D(glctx.GL_FRAMEBUFFER, glctx.GL_DEPTH_ATTACHMENT, glctx.GL_TEXTURE_2D, self.shadow_depth, 0)
            gl.glDrawBuffers(1, (glctx.U * 1)(glctx.GL_COLOR_ATTACHMENT0))
            gl.glViewport(0, 0, self.shadow_res, self.shadow_res)
            gl.glEnable(glctx.GL_DEPTH_TEST)
            gl.glDepthFunc(glctx.GL_LESS)
            gl.glDepthMask(1)
            gl.glDisable(glctx.GL_BLEND)
            gl.glClearColor(1, 1, 1, 1)
            gl.glClear(glctx.GL_COLOR_BUFFER_BIT | glctx.GL_DEPTH_BUFFER_BIT)
            gl.glUseProgram(sp.id)
            self.bind_samplers(sp)
            self.set_uniforms(sp, U)
            self.load_matrices(smv, sproj)
            self.draw_arrays(meshes["solid"], cam)
            if shot.get("monster", True):
                self.draw_arrays(monster[0], cam)
        tm = mark("shadow", tm)
        # ---- sky objects ----------------------------------------------------------------
        st = self.progs["gbuffers_skytextured"]
        self.bind_targets(st.targets, write_alt=False, depth=self.depth0)
        gl.glClear(glctx.GL_DEPTH_BUFFER_BIT)
        gl.glDisable(glctx.GL_DEPTH_TEST)
        gl.glDepthMask(0)
        gl.glEnable(glctx.GL_BLEND)
        gl.glBlendFuncSeparate(glctx.GL_SRC_ALPHA, glctx.GL_ONE, glctx.GL_ONE, glctx.GL_ZERO)
        self.bind_samplers(st)
        U2 = dict(U)
        U2["renderStage"] = 5
        self.set_uniforms(st, U2)
        self.load_matrices(np.eye(4, dtype=np.float32), proj)
        self.draw_arrays(moon_quad(moon), (0, 0, 0))
        gl.glDisable(glctx.GL_BLEND)
        # ---- opaque terrain + entities + eyes + the solid hand --------------------------------
        gl.glDepthMask(1)
        gl.glEnable(glctx.GL_DEPTH_TEST)
        gl.glDepthFunc(glctx.GL_LEQUAL)
        self.gbuffer_draw("gbuffers_terrain", U, meshes["solid"], view, proj, cam)
        if shot.get("monster", True):
            self.gbuffer_draw("gbuffers_entities", U, monster[0], view, proj, cam, extra={"entityId": NOZOKI})
            gl.glDepthMask(0)
            self.gbuffer_draw("gbuffers_spidereyes", U, monster[1], view, proj, cam, blend=("additive",), extra={"entityId": NOZOKI})
            gl.glDepthMask(1)
        if shot.get("ghost"):
            self.gbuffer_draw("gbuffers_entities_translucent", U, ghost_mesh(scene, *shot["ghost"]), view, proj, cam,
                              blend=("translucent", ()), extra={"entityId": UTSURU})
        hand_proj = np.diag([1.0, 1.0, 0.125, 1.0]).astype(np.float32) @ perspective(70.0, w / h, 0.05, 192.0 * 4)
        light = ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0)
        for item, side in ((held, 1), (held2, -1)):
            hm = hand_mesh(item, side, light)
            if hm is not None:
                self.gbuffer_draw("gbuffers_hand", U, hm, np.eye(4, dtype=np.float32), hand_proj, (0, 0, 0), extra={"currentRenderedItemId": item})
        tm = mark("gbuffers", tm)
        # ---- deferred ---------------------------------------------------------------------
        self.fullscreen("deferred", U)
        tm = mark("deferred", tm)
        # ---- translucents -----------------------------------------------------------------
        gl.glEnable(glctx.GL_DEPTH_TEST)
        self.gbuffer_draw("gbuffers_water", U, meshes["translucent"], view, proj, cam, blend=("translucent", (1,)))
        tm = mark("translucent", tm)
        # ---- composite chain + final ------------------------------------------------------
        for name in ("composite", "composite1", "composite2", "composite3"):
            self.fullscreen(name, U)
            tm = mark(name, tm)
        stats = {}
        c0 = self.read_float(0)
        stats["nan"] = int(np.isnan(c0).sum() + np.isinf(c0).sum())
        stats["hdr_max"] = float(np.nanmax(c0[..., :3]))
        img = self.final_pass(U)
        gl.glFinish()
        mark("final", tm)
        lum = img.astype(np.float32).mean(axis=2)
        stats["mean"] = float(lum.mean())
        stats["black%"] = float((lum < 3).mean() * 100)
        return img, stats


def moon_quad_factory():
    def build(moon_view):
        tx, ty = TILES["moon"]
        m = np.array(moon_view, np.float64)
        d = m / np.linalg.norm(m)
        up = np.array([0, 1, 0.0]) if abs(d[1]) < 0.9 else np.array([1, 0, 0.0])
        s = np.cross(d, up); s /= np.linalg.norm(s)
        u = np.cross(s, d)
        c = d * 100.0
        k = 20.0
        cs = [c - s * k - u * k, c + s * k - u * k, c + s * k + u * k, c - s * k + u * k]
        uvs = [(tx / ATLAS_N, (ty + 1) / ATLAS_N), ((tx + 1) / ATLAS_N, (ty + 1) / ATLAS_N), ((tx + 1) / ATLAS_N, ty / ATLAS_N), (tx / ATLAS_N, ty / ATLAS_N)]
        idx = (0, 1, 2, 0, 2, 3)
        return dict(pos=np.array([cs[i] for i in idx], np.float32), nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)),
                    col=np.ones((6, 4), np.float32), uv0=np.array([uvs[i] for i in idx], np.float32), uv1=np.ones((6, 2), np.float32) * (248 / 256))
    return build


def eye_at(scene, x, z, dy=1.62):
    return (x, scene.ground[int(x) - scene.origin[0], int(z) - scene.origin[2]] + 1 + dy, z)


def make_shots(scene):
    M = MOUNTAIN
    fz = 9.5
    forest_eye = eye_at(scene, 30.5, fz)
    tunnel_eye = (6.5, scene.tunnel_floor + 1 + 1.62, 30.5)
    rim = eye_at(scene, 39.5, 36.5)
    hut_eye = eye_at(scene, 48.5, 8.5)
    down = (0.0, -0.17, 1.0)
    return {
        # the forest, looking down the mountain (south, toward the moon)
        "m_beam": dict(folder=M, eye=forest_eye, look=down, fear=0.15, held=FLASHLIGHT),
        "m_dark": dict(folder=M, eye=forest_eye, look=down, fear=0.15, held=0),
        "m_shafts": dict(folder=M, eye=forest_eye, look=(0.15, 0.28, 1.0), fear=0.1, held=0, monster=False),
        "m_hollow": dict(folder=M, eye=rim, look=(0.05, -0.30, 1.0), fear=0.2, held=FLASHLIGHT, monster=False),
        "m_hollow_dark": dict(folder=M, eye=rim, look=(0.05, -0.30, 1.0), fear=0.2, held=0, monster=False),
        "m_eyes_dark": dict(folder=M, eye=forest_eye, look=(0.10, -0.06, 1.0), fear=0.45, held=0),
        "m_eyes_beam": dict(folder=M, eye=forest_eye, look=(2.26, -3.4, 15.0), fear=0.45, held=FLASHLIGHT),
        "m_fear": dict(folder=M, eye=forest_eye, look=down, fear=0.95, held=FLASHLIGHT, t=131.7),
        "m_danger": dict(folder=M, eye=forest_eye, look=down, fear=0.8, danger=1.0, held=FLASHLIGHT, t=128.31),
        "m_danger_lit": dict(folder=M, eye=forest_eye, look=down, fear=0.8, danger=0.8, held=FLASHLIGHT, t=126.02),
        "m_camera": dict(folder=M, eye=forest_eye, look=down, fear=0.3, held=CAMERA, held2=FLASHLIGHT, ghost=(30.6, 18.5)),
        "m_ghost_eye": dict(folder=M, eye=forest_eye, look=down, fear=0.3, held=FLASHLIGHT, ghost=(30.6, 18.5)),
        "m_low": dict(folder=M, eye=forest_eye, look=down, fear=0.4, held=FLASH_LOW, t=124.0),
        "m_phone": dict(folder=M, eye=forest_eye, look=down, fear=0.3, held=PHONE_LIGHT),
        "m_flare": dict(folder=M, eye=forest_eye, look=down, fear=0.3, held=FLASHLIGHT, held2=FLARE),
        "m_tunnel": dict(folder=M, eye=tunnel_eye, look=(0.0, -0.05, -1.0), fear=0.5, held=FLASHLIGHT, monster=False),
        "m_hut": dict(folder=M, eye=hut_eye, look=(0.0, -0.12, 1.0), fear=0.3, held2=FLASHLIGHT, monster=False),
        "m_wall": dict(folder=M, eye=eye_at(scene, 48.5, 12.0), look=(0.0, -0.05, 1.0), fear=0.0, held=FLASHLIGHT, monster=False, t=100.0),
        "m_darkness": dict(folder=M, eye=forest_eye, look=down, fear=0.9, darkness=0.9, held=FLASHLIGHT),
        "m_stream": dict(folder=M, eye=eye_at(scene, 36.5, 43.5), look=(0.25, -0.55, 1.0), fear=0.2, held=FLASHLIGHT, monster=False),
        "m_moon": dict(folder=M, eye=rim, look=(0.0, 1.55, 1.0), fear=0.1, held=0, monster=False),
        "m_low2": dict(folder=M, eye=forest_eye, look=down, fear=0.4, held=FLASH_LOW, t=127.5),
        # other dimensions (root programs etc.)
        "o_day": dict(folder="", eye=forest_eye, look=down, time=6000, held=0, monster=False),
        "o_day_up": dict(folder="", eye=forest_eye, look=(0.15, 0.28, 1.0), time=6000, held=0),
        "o_day_hollow": dict(folder="", eye=rim, look=(0.05, -0.30, 1.0), time=6000, held=0),
        "o_day_top": dict(folder="", eye=(40.0, 130.0, 30.0), look=(0.0, -1.0, 0.001), time=6000, held=0),
        "o_day_monster": dict(folder="", eye=forest_eye, look=(2.26, -3.4, 15.0), time=6000, held=0),
        "o_day_monster_close": dict(folder="", eye=eye_at(scene, 31.5, 19.5), look=(1.0, -0.5, 4.5), time=6000, held=0),
        "o_night": dict(folder="", eye=forest_eye, look=down, time=18000, held=FLASHLIGHT, monster=False),
        "n_nether": dict(folder="world-1", eye=forest_eye, look=down, held=FLASHLIGHT, monster=False),
        "e_end": dict(folder="world1", eye=forest_eye, look=down, held=0, monster=False),
    }


def mc_dir(yaw, pitch):
    """Minecraft yaw / pitch (degrees; yaw 0 = south +Z, pitch > 0 = down) -> look vector"""
    y, p = math.radians(yaw), math.radians(pitch)
    return (-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p))


# the CI tour's viewpoints on the real mountain (com.sounan.client.AutoShots)
TERRAIN_SHOTS = {
    "t_start_dark": dict(at=(-198.5, -209.5), yaw=155.0, pitch=4.0, fear=0.3, held=0),
    "t_start_beam": dict(at=(-198.5, -209.5), yaw=155.0, pitch=4.0, fear=0.3, held=FLASHLIGHT),
    "t_ribbons": dict(at=(-95.5, -88.5), yaw=-45.0, pitch=2.0, fear=0.3, held=FLASHLIGHT),
    "t_forest_deep": dict(at=(-42.5, -44.5), yaw=-40.0, pitch=0.0, fear=0.3, held=FLASHLIGHT),
    "t_forest_dark": dict(at=(-42.5, -44.5), yaw=-40.0, pitch=0.0, fear=0.3, held=0),
    "t_fear": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.92, danger=0.15, held=FLASHLIGHT, t=131.7),
    "t_danger": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.95, danger=0.9, held=FLASHLIGHT, t=126.02),
    "t_phone": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.3, held=PHONE_LIGHT),
    "t_flare": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.3, held=FLARE),
    "t_low": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.3, held=FLASH_LOW, t=127.5),
    "t_camera": dict(at=(-60.5, -60.5), yaw=45.0, pitch=0.0, fear=0.3, held=CAMERA),
    "t_shafts": dict(at=(-60.5, -60.5), yaw=0.0, pitch=-18.0, fear=0.1, held=0),
    "t_shafts2": dict(at=(-42.5, -44.5), yaw=10.0, pitch=-8.0, fear=0.1, held=0),
}


def terrain_monster_trunk(scene, eye, look):
    """a trunk 8..16 blocks ahead, within 20 degrees of the view, for the peeking monster"""
    best = None
    f = np.array([look[0], look[2]]) / max(math.hypot(look[0], look[2]), 1e-6)
    for (x, g, z, top) in scene.trunks:
        wx, wz = x + scene.origin[0] + 0.5, z + scene.origin[2] + 0.5
        d = np.array([wx - eye[0], wz - eye[2]])
        dist = float(np.linalg.norm(d))
        if 8.0 <= dist <= 16.0 and float(np.dot(d / dist, f)) > math.cos(math.radians(20)):
            if best is None or abs(dist - 11.0) < best[0]:
                best = (abs(dist - 11.0), (x + scene.origin[0], z + scene.origin[2]))
    return best[1] if best else None


def write_png(path, img):
    from PIL import Image
    Image.fromarray(img).save(path)


def contact_sheet(paths, out, cols=2):
    from PIL import Image, ImageDraw
    ims = [Image.open(p) for p in paths]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, (h + 18) * rows), (40, 40, 40))
    d = ImageDraw.Draw(sheet)
    for i, (im, p) in enumerate(zip(ims, paths)):
        x, y = (i % cols) * w, (i // cols) * (h + 18)
        sheet.paste(im, (x, y + 18))
        d.text((x + 4, y + 3), os.path.basename(p), fill=(230, 230, 230))
    sheet.save(out)


def run_check():
    ver, ren = glctx.init()
    print("GL:", ver, "|", ren)
    fails = 0
    total = 0
    for folder in build_pack.FOLDERS:
        for flip in (False, True):
            for name in build_pack.PROGRAMS:
                try:
                    p = Program(folder, name, {}, flip)
                except FileNotFoundError:
                    continue
                total += 1
                if p.errors:
                    fails += 1
                    print("FAIL %s/%s%s" % (folder or "root", name, " (options flipped)" if flip else ""))
                    for e in p.errors:
                        print("   " + e.strip().replace("\n", "\n   ")[:1500])
    print("mesa: compiled + linked %d programs, %d failed" % (total, fails))
    return 1 if fails else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--out", default="/tmp/sounan_preview")
    ap.add_argument("--size", default="960x540")
    ap.add_argument("--shots", default="")
    ap.add_argument("--timing", action="store_true", help="print the time of every pass (llvmpipe)")
    ap.add_argument("-D", action="append", default=[])
    args = ap.parse_args()
    if args.check:
        return run_check()
    overrides = dict(d.split("=", 1) for d in args.D)
    w, h = (int(v) for v in args.size.split("x"))
    os.makedirs(args.out, exist_ok=True)
    ver, ren = glctx.init()
    print("GL:", ver, "|", ren)
    make_atlas()                                  # fills TILES
    print("textures: %d real (%s), the rest procedural" % (len(REAL_TILES), TEXTURE_DIR))
    names = args.shots.split(",") if args.shots else None
    scenes = {}

    def get_scene(key):
        if key not in scenes:
            t0 = time.time()
            if key == "test":
                sc = Scene()
            else:
                sc = TerrainScene(Terrain(), key[0], key[1])
            sc.compute_light()
            meshes = sc.mesh()
            scenes[key] = (sc, meshes)
            print("scene %s: %d solid / %d translucent vertices (%.1fs)" % (key, len(meshes["solid"]["pos"]), len(meshes["translucent"]["pos"]), time.time() - t0))
        return scenes[key]

    test_scene = get_scene("test")[0] if (names is None or any(not n.startswith("t_") for n in names)) else None
    shots = make_shots(test_scene) if test_scene is not None else {}
    if names is None:
        names = list(shots)
    renderers = {}
    paths = []
    moon_quad = moon_quad_factory()
    for name in names:
        if name in TERRAIN_SHOTS:
            ts = TERRAIN_SHOTS[name]
            sc, meshes = get_scene(ts["at"])
            eye = eye_at(sc, ts["at"][0], ts["at"][1])
            shot = dict(ts, folder=MOUNTAIN, eye=eye, look=mc_dir(ts["yaw"], ts["pitch"]))
            mt = terrain_monster_trunk(sc, eye, shot["look"])
            shot["monster"] = mt is not None
            monster = monster_meshes(sc, mt) if mt else (None, None)
        else:
            shot = dict(shots[name])
            sc, meshes = get_scene("test")
            monster = monster_meshes(sc, sc.monster_trunk, far=(22.5, 31.6))
        folder = shot["folder"]
        if folder not in renderers:
            renderers[folder] = Renderer(folder, w, h, overrides)
        r = renderers[folder]
        timing = {} if args.timing else None
        t0 = time.time()
        img, st = r.render(shot, sc, meshes, monster, moon_quad, timing)
        p = os.path.join(args.out, name + ".png")
        write_png(p, img)
        paths.append(p)
        print("%-14s %5.2fs  mean %6.2f  black %5.1f%%  hdr max %8.3f  nan %d" % (name, time.time() - t0, st["mean"], st["black%"], st["hdr_max"], st["nan"]))
        if timing:
            print("    " + "  ".join("%s %.0fms" % (k, v * 1000) for k, v in timing.items()))
    if len(paths) > 1:
        contact_sheet(paths, os.path.join(args.out, "contact.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
