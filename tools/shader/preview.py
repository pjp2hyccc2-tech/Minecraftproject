#!/usr/bin/env python3
"""MeikonShaders offline harness: Mesa compile/link check + Iris-like preview renders.

  python3 tools/shader/preview.py --check
        compile AND link every program of every dimension folder with Mesa (llvmpipe, the
        compiler the CI run uses) with the default options and with every option flipped.
        Catches what glslang cannot: vertex/fragment interface mismatches, driver quirks.

  python3 tools/shader/preview.py [--out DIR] [--size 960x540] [--shots a,b] [-D NAME=VALUE]
        renders a small voxel test mansion (corridor, andon lamp, candles, shoji, courtyard,
        torii portal, well, the bride) through the real pipeline - shadow pass, gbuffers,
        deferred, composite..composite3, final - emulating what Iris/Oculus do (ping-pong
        buffers, RENDERTARGETS, mipmap directives, celestial / shadow matrices, uniforms).
        Writes one PNG per shot plus contact.png, and prints buffer statistics (NaN / black).

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
#  test scene: a corner of the 隠り世 mansion
# =====================================================================================

TILES = {}


def make_atlas(seed=7):
    """procedural 16x16 pixel-art-ish tiles, 8x8 atlas (128 px). Returns uint8 RGBA."""
    rng = np.random.default_rng(seed)
    atlas = np.zeros((128, 128, 4), np.uint8)
    names = ["plaster", "plaster_stain", "planks", "tatami", "beam", "roof", "shoji", "stone",
             "gravel", "vermilion", "lacquer", "andon", "candle", "blood", "water", "torii",
             "kimono", "hair", "moon", "chochin", "wainscot", "grass"]

    def noise(scale=1.0):
        return rng.random((16, 16)) * scale

    for i, n in enumerate(names):
        tx, ty = i % 8, i // 8
        TILES[n] = (tx, ty)
        y, x = np.mgrid[0:16, 0:16]
        a = np.full((16, 16), 255.0)
        if n == "plaster":
            base = np.array([196, 188, 168]); t = base[None, None] * (0.92 + noise(0.10))[..., None]
        elif n == "plaster_stain":
            base = np.array([190, 180, 158]); t = base[None, None] * (0.90 + noise(0.12))[..., None]
            stain = np.exp(-(((x - 6) ** 2) / 18 + ((y - 9) ** 2) / 40)); t *= (1 - 0.35 * stain)[..., None]
        elif n == "planks":
            base = np.array([84, 62, 46]); g = 0.85 + 0.2 * np.sin(y * 1.7 + rng.random() * 3) * 0.3 + noise(0.15)
            t = base[None, None] * g[..., None]; t[(y % 4) == 0] *= 0.6
        elif n == "tatami":
            base = np.array([150, 140, 92]); g = 0.88 + 0.12 * ((x % 2) == 0) + noise(0.08)
            t = base[None, None] * g[..., None]; t[:, 0] *= 0.55
        elif n == "beam":
            base = np.array([52, 40, 31]); t = base[None, None] * (0.85 + noise(0.2))[..., None]
        elif n == "roof":
            base = np.array([44, 44, 50]); t = base[None, None] * (0.8 + noise(0.25))[..., None]; t[(y % 5) == 0] *= 0.6
        elif n == "shoji":
            base = np.array([214, 208, 190]); t = base[None, None] * (0.95 + noise(0.05))[..., None]
            frame = (x % 8 == 0) | (y % 5 == 0) | (x == 15) | (y == 15)
            t[frame] = np.array([70, 55, 42])
        elif n == "stone":
            base = np.array([112, 112, 108]); t = base[None, None] * (0.75 + noise(0.35))[..., None]
        elif n == "gravel":
            base = np.array([92, 86, 78]); t = base[None, None] * (0.7 + noise(0.45))[..., None]
        elif n == "vermilion":
            base = np.array([186, 54, 30]); t = base[None, None] * (0.88 + noise(0.15))[..., None]
        elif n == "lacquer":
            base = np.array([22, 19, 19]); t = base[None, None] * (0.9 + noise(0.2))[..., None]
        elif n == "andon":
            base = np.array([226, 204, 160]); t = base[None, None] * (0.95 + noise(0.05))[..., None]
            frame = (x <= 1) | (x >= 14) | (y <= 1) | (y >= 14) | (y == 8)
            t[frame] = np.array([40, 30, 24])
        elif n == "candle":
            t = np.zeros((16, 16, 3)) + np.array([230, 220, 200])
            flame = ((x - 7.5) ** 2 / 4 + (y - 4) ** 2 / 9) < 1
            t[flame] = np.array([255, 200, 90]); a[:] = 0; a[flame] = 255
            body = (abs(x - 7.5) < 2) & (y > 7); t[body] = np.array([225, 215, 195]); a[body] = 255
        elif n == "blood":
            base = np.array([84, 62, 46]); t = base[None, None] * (0.85 + noise(0.15))[..., None]
            b = np.exp(-(((x - 8) ** 2) / 20 + ((y - 7) ** 2) / 14)) + 0.5 * np.exp(-(((x - 3) ** 2) / 4 + ((y - 13) ** 2) / 3))
            t = t * (1 - np.clip(b, 0, 1))[..., None] + np.array([78, 12, 10])[None, None] * np.clip(b, 0, 1)[..., None]
        elif n == "water":
            t = np.zeros((16, 16, 3)) + 170 * (0.9 + noise(0.1))[..., None]; a[:] = 190
        elif n == "torii":
            v = 0.5 + 0.5 * np.sin(x * 0.8 + y * 0.5) * np.cos(y * 0.6 - x * 0.3)
            t = np.zeros((16, 16, 3)) + np.array([240, 120, 90])[None, None] * (0.4 + 0.6 * v)[..., None]
            a[:] = 120 + 100 * v
        elif n == "kimono":
            base = np.array([222, 220, 212]); t = base[None, None] * (0.93 + noise(0.07))[..., None]
        elif n == "hair":
            t = np.zeros((16, 16, 3)) + 12 * (0.8 + noise(0.4))[..., None]
        elif n == "moon":
            d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
            v = np.clip(1.0 - d / 7.5, 0, 1) > 0
            t = np.zeros((16, 16, 3)) + (200 * (0.8 + noise(0.2)))[..., None] * v[..., None]
            a[:] = 255
        elif n == "chochin":
            base = np.array([232, 200, 150]); t = base[None, None] * (0.95 + noise(0.05))[..., None]
            t[(y % 4) == 0] *= 0.75; t[(y <= 1) | (y >= 14)] = np.array([30, 24, 20])
        elif n == "wainscot":
            base = np.array([60, 45, 34]); t = base[None, None] * (0.85 + noise(0.15))[..., None]; t[(x % 4) == 0] *= 0.7
        elif n == "grass":
            base = np.array([64, 74, 50]); t = base[None, None] * (0.7 + noise(0.5))[..., None]
        atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16, :3] = np.clip(t, 0, 255).astype(np.uint8)
        atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16, 3] = np.clip(a, 0, 255).astype(np.uint8)
    return atlas


# block type: (tile(s), mc_Entity id, opaque, emission, render layer, box)
#   tile: name or dict(top=..., side=..., bottom=...)  layer: "solid" | "translucent"
BLOCKS = {
    1: ("plaster", 0, True, 0, "solid", None),
    2: (dict(top="planks", side="planks", bottom="planks"), 0, True, 0, "solid", None),
    3: (dict(top="tatami", side="planks", bottom="planks"), 0, True, 0, "solid", None),
    4: ("beam", 0, True, 0, "solid", None),
    5: ("roof", 0, True, 0, "solid", None),
    6: ("shoji", 10032, True, 0, "solid", None),
    7: ("stone", 0, True, 0, "solid", None),
    8: (dict(top="gravel", side="stone", bottom="stone"), 0, True, 0, "solid", None),
    9: ("vermilion", 10031, True, 0, "solid", None),
    10: ("lacquer", 10031, True, 0, "solid", None),
    11: ("andon", 10002, False, 15, "solid", (0.22, 0.0, 0.22, 0.78, 0.9, 0.78)),
    12: ("candle", 10001, False, 12, "solid", (0.35, 0.0, 0.35, 0.65, 0.7, 0.65)),
    13: (dict(top="blood", side="planks", bottom="planks"), 10030, True, 0, "solid", None),
    14: ("water", 10020, False, 0, "translucent", (0.0, 0.0, 0.0, 1.0, 0.875, 1.0)),
    15: ("torii", 10005, False, 11, "translucent", (0.0, 0.0, 0.42, 1.0, 1.0, 0.58)),
    16: ("plaster_stain", 0, True, 0, "solid", None),
    17: ("wainscot", 0, True, 0, "solid", None),
    18: (dict(top="grass", side="stone", bottom="stone"), 0, True, 0, "solid", None),
}


class Scene:
    """voxel scene, world coordinates = index + origin"""

    def __init__(self):
        self.sx, self.sy, self.sz = 48, 16, 44
        self.origin = np.array([0, 58, 0])
        self.b = np.zeros((self.sx, self.sy, self.sz), np.int16)
        self.build()

    def fill(self, x0, y0, z0, x1, y1, z1, t):
        o = self.origin
        self.b[x0 - o[0]:x1 - o[0] + 1, y0 - o[1]:y1 - o[1] + 1, z0 - o[2]:z1 - o[2] + 1] = t

    def build(self):
        F = self.fill
        F(0, 58, 0, 47, 62, 43, 7)                 # ground
        F(0, 63, 0, 47, 63, 43, 2)                 # floor boards everywhere
        # ---- corridor along +x (z 10..12), plaster walls, low ceiling
        F(1, 64, 9, 40, 66, 9, 1)
        F(1, 64, 13, 16, 66, 13, 1)
        F(1, 64, 9, 40, 64, 9, 17)                 # dark wainscot
        F(1, 64, 13, 16, 64, 13, 17)
        F(1, 67, 8, 40, 67, 14, 5)                 # roof / ceiling
        F(1, 64, 10, 1, 66, 12, 1)                 # back wall behind the player
        F(41, 64, 9, 41, 66, 13, 1)                # far end of the corridor
        F(41, 67, 8, 41, 67, 14, 5)
        for x in range(4, 40, 5):                  # dark pillars
            F(x, 64, 9, x, 66, 9, 4)
        F(5, 64, 9, 6, 66, 9, 16)                  # a stained wall section
        F(9, 63, 11, 9, 63, 11, 13)                # blood on the floor
        F(9, 63, 12, 10, 63, 12, 13)
        F(3, 63, 10, 16, 63, 12, 3)                # tatami in the first room part
        F(12, 64, 12, 12, 64, 12, 11)              # andon lamp
        F(30, 64, 10, 30, 64, 10, 12)              # candle stand far down the corridor
        # ---- courtyard (x 17..40, z 14..40): open to the moon, rooms with shoji around
        F(17, 63, 14, 40, 63, 40, 18)
        F(17, 64, 13, 40, 66, 13, 6)               # shoji facing the courtyard (corridor side)
        F(17, 64, 13, 17, 66, 13, 4)
        F(40, 64, 13, 40, 66, 40, 1)
        F(16, 64, 13, 16, 66, 40, 1)
        F(16, 64, 40, 40, 66, 40, 1)
        F(16, 67, 13, 40, 67, 14, 5)               # eaves over the veranda
        F(15, 67, 15, 16, 67, 40, 5)
        F(16, 67, 39, 40, 67, 40, 5)
        F(39, 67, 13, 40, 67, 40, 5)
        F(22, 64, 33, 22, 69, 33, 9)               # torii pillars
        F(29, 64, 33, 29, 69, 33, 9)
        F(21, 70, 33, 30, 70, 33, 10)              # kasagi (black lacquer)
        F(22, 68, 33, 29, 68, 33, 9)               # nuki
        F(23, 64, 33, 28, 67, 33, 15)              # the portal
        F(20, 63, 20, 22, 64, 22, 7)               # the well
        F(21, 63, 21, 21, 63, 21, 14)
        F(21, 64, 21, 21, 64, 21, 0)
        F(34, 64, 24, 34, 66, 24, 7)               # stone lantern
        F(33, 67, 23, 35, 67, 25, 7)

    def inside(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def opaque(self, x, y, z):
        if not self.inside(x, y, z):
            return False
        t = self.b[x, y, z]
        return t != 0 and BLOCKS[t][2]

    def compute_light(self, extra_sources=()):
        sx, sy, sz = self.sx, self.sy, self.sz
        op = np.zeros_like(self.b, dtype=bool)
        for t, d in BLOCKS.items():
            if d[2]:
                op |= self.b == t
        sky = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for x in range(sx):
            for z in range(sz):
                for y in range(sy - 1, -1, -1):
                    if op[x, y, z]:
                        break
                    sky[x, y, z] = 15
                    q.append((x, y, z))
        self._bfs(sky, q, op)
        blk = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for t, d in BLOCKS.items():
            if d[3] > 0:
                for (x, y, z) in zip(*np.nonzero(self.b == t)):
                    blk[x, y, z] = max(blk[x, y, z], d[3])
                    q.append((x, y, z))
        for (wx, wy, wz, lvl) in extra_sources:
            x, y, z = int(wx) - self.origin[0], int(wy) - self.origin[1], int(wz) - self.origin[2]
            if self.inside(x, y, z):
                blk[x, y, z] = max(blk[x, y, z], lvl)
                q.append((x, y, z))
        self._bfs(blk, q, op)
        self.sky, self.blk = sky, blk

    def _bfs(self, L, q, op):
        sx, sy, sz = L.shape
        while q:
            x, y, z = q.popleft()
            nl = L[x, y, z] - 1
            if nl <= 0:
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                X, Y, Z = x + dx, y + dy, z + dz
                if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz and not op[X, Y, Z] and L[X, Y, Z] < nl:
                    L[X, Y, Z] = nl
                    q.append((X, Y, Z))

    def light_at(self, x, y, z):
        if not self.inside(x, y, z):
            return 0, 15
        return int(self.blk[x, y, z]), int(self.sky[x, y, z])

    def mesh(self):
        """-> dict layer -> arrays (world positions etc.)"""
        faces = {"solid": [], "translucent": []}
        dirs = [((1, 0, 0), "side"), ((-1, 0, 0), "side"), ((0, 1, 0), "top"), ((0, -1, 0), "bottom"), ((0, 0, 1), "side"), ((0, 0, -1), "side")]
        for (x, y, z) in zip(*np.nonzero(self.b)):
            t = int(self.b[x, y, z])
            tile, bid, opq, emit, layer, box = BLOCKS[t]
            x0, y0, z0, x1, y1, z1 = box if box else (0, 0, 0, 1, 1, 1)
            for (n, kind) in dirs:
                nx, ny, nz = x + n[0], y + n[1], z + n[2]
                if box is None:
                    if self.opaque(nx, ny, nz):
                        continue
                    if self.inside(nx, ny, nz) and self.b[nx, ny, nz] == t:
                        continue
                elif layer == "translucent" and self.inside(nx, ny, nz) and self.b[nx, ny, nz] == t:
                    if (box[1], box[4]) == (0.0, 1.0) or n[1] == 0:
                        continue
                tname = tile if isinstance(tile, str) else tile[kind]
                faces[layer].append((x, y, z, n, tname, bid, (x0, y0, z0, x1, y1, z1), box is not None))
        out = {}
        for layer, fl in faces.items():
            out[layer] = self._build_arrays(fl)
        return out

    def _build_arrays(self, fl):
        P, N, Cc, U0, U1, E, M = [], [], [], [], [], [], []
        o = self.origin
        for (x, y, z, n, tname, bid, bb, is_box) in fl:
            x0, y0, z0, x1, y1, z1 = bb
            n = np.array(n)
            ax = int(np.argmax(np.abs(n)))
            # face corners in order (u,v) with u,v in {0,1}
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
            u0, v0, u1, v1 = tx / 8, ty / 8, (tx + 1) / 8, (ty + 1) / 8
            mid = ((u0 + u1) / 2, (v0 + v1) / 2)
            fc = (x + n[0], y + n[1], z + n[2])
            verts = []
            for (cx, cy, cz) in corners:
                # uv: side faces have the texture top at the block top
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
                    # smooth lighting + vanilla-style AO over the 4 cells around the vertex
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
            # winding: make the quad counter-clockwise seen from the normal side
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


def box_mesh(scene, wmin, wmax, tiles, light_cell):
    """entity box (the bride). tiles: dict per face kind; returns arrays"""
    (x0, y0, z0), (x1, y1, z1) = wmin, wmax
    bl, sl = scene.light_at(*(np.array(light_cell) - scene.origin))
    lu, lv = (bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0
    P, N, Cc, U0, U1 = [], [], [], [], []
    quads = [
        ((1, 0, 0), [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], "side"),
        ((-1, 0, 0), [(x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0)], "side"),
        ((0, 0, 1), [(x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (x0, y0, z1)], "side"),
        ((0, 0, -1), [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)], "side"),
        ((0, 1, 0), [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], "top"),
    ]
    for n, cs, kind in quads:
        tx, ty = TILES[tiles[kind]]
        uvs = [(tx / 8, (ty + 1) / 8), (tx / 8, ty / 8), ((tx + 1) / 8, ty / 8), ((tx + 1) / 8, (ty + 1) / 8)]
        for i in (0, 1, 2, 0, 2, 3):
            P.append(cs[i]); N.append(n); Cc.append((1, 1, 1, 1)); U0.append(uvs[i]); U1.append((lu, lv))
    f = lambda a, k: np.array(a, np.float32).reshape(-1, k)
    return dict(pos=f(P, 3), nrm=f(N, 3), col=f(Cc, 4), uv0=f(U0, 2), uv1=f(U1, 2))


# =====================================================================================
#  renderer
# =====================================================================================

FORMATS = {0: glctx.GL_RGBA16F, 1: glctx.GL_RGBA8, 2: glctx.GL_RGBA16F, 3: glctx.GL_RGBA16F, 4: glctx.GL_RGBA16F}
UNITS = {"gtexture": 0, "noisetex": 1, "colortex0": 2, "colortex1": 3, "colortex2": 4, "colortex3": 5,
         "colortex4": 6, "depthtex0": 7, "depthtex1": 8, "shadowtex0": 9}


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


class Renderer:
    def __init__(self, folder, w, h, overrides):
        self.folder, self.w, self.h = folder, w, h
        self.progs = {}
        names = [p for p in build_pack.PROGRAMS]
        for n in names:
            try:
                p = Program(folder, n, overrides)
            except FileNotFoundError:
                continue
            if p.errors:
                raise RuntimeError("%s/%s failed:\n%s" % (folder or "root", n, "\n".join(p.errors)))
            self.progs[n] = p
        any_prog = self.progs["final"]
        self.shadow_res = int(any_prog.consts.get("shadowMapResolution", 1536))
        self.shadow_dist = any_prog.consts.get("shadowDistance", 96.0)
        self.sun_path = any_prog.consts.get("sunPathRotation", 0.0)
        # colour buffers (ping-pong) + depth
        self.tex = {i: [tex2d(w, h, FORMATS[i], glctx.GL_RGBA, glctx.GL_FLOAT), tex2d(w, h, FORMATS[i], glctx.GL_RGBA, glctx.GL_FLOAT)] for i in FORMATS}
        self.cur = {i: 0 for i in FORMATS}
        self.depth0 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.depth1 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.out_tex = tex2d(w, h, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE)
        sr = self.shadow_res
        self.shadow_depth = tex2d(sr, sr, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT)
        gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_MODE, glctx.GL_COMPARE_REF_TO_TEXTURE)
        gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_FUNC, glctx.GL_LEQUAL)
        self.shadow_color = tex2d(sr, sr, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE)
        self.fbo = glctx.gen_framebuffer()
        rng = np.random.default_rng(3)
        self.noise = tex2d(256, 256, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, rng.integers(0, 256, (256, 256, 4), dtype=np.uint8), wrap=glctx.GL_REPEAT)
        atlas = make_atlas()
        self.atlas = tex2d(128, 128, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, np.ascontiguousarray(atlas), filt=glctx.GL_NEAREST)
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
        for name, unit in UNITS.items():
            loc = prog.loc(name)
            if loc < 0:
                continue
            gl.glUniform1i(loc, unit)
        binds = {"gtexture": self.atlas, "noisetex": self.noise, "depthtex0": self.depth0, "depthtex1": self.depth1,
                 "shadowtex0": self.shadow_depth}
        for i in FORMATS:
            binds["colortex%d" % i] = self.tex[i][self.cur[i]]
        for name, unit in UNITS.items():
            gl.glActiveTexture(glctx.GL_TEXTURE0 + unit)
            gl.glBindTexture(glctx.GL_TEXTURE_2D, binds[name])
            if name.startswith("colortex"):
                idx = int(name[-1])
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
        quad = dict(pos=np.array([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 0, 0), (1, 1, 0), (0, 1, 0)], np.float32),
                    nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)), col=np.ones((6, 4), np.float32),
                    uv0=np.array([(0, 0), (1, 0), (1, 1), (0, 0), (1, 1), (0, 1)], np.float32), uv1=np.zeros((6, 2), np.float32))
        self.draw_arrays(quad, (0, 0, 0))
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
        quad = dict(pos=np.array([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 0, 0), (1, 1, 0), (0, 1, 0)], np.float32),
                    nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)), col=np.ones((6, 4), np.float32),
                    uv0=np.array([(0, 0), (1, 0), (1, 1), (0, 0), (1, 1), (0, 1)], np.float32), uv1=np.zeros((6, 2), np.float32))
        self.draw_arrays(quad, (0, 0, 0))
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
    def render(self, shot, scene, meshes, bride, moon_quad):
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
        smv = look_rot(-light_w, up=(0, 0, 1)) @ np.array([[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], np.float32)
        smv[:3, 3] = smv[:3, :3] @ (-light_w * 100.0)
        sd = self.shadow_dist
        sproj = ortho(-sd, sd, -sd, sd, 0.05, 256.0)
        ecell = (cam - scene.origin).astype(int)
        bl, sl = scene.light_at(*ecell)
        U = {
            "gbufferModelView": view, "gbufferModelViewInverse": np.linalg.inv(view).astype(np.float32),
            "gbufferProjection": proj, "gbufferProjectionInverse": np.linalg.inv(proj).astype(np.float32),
            "shadowModelView": smv, "shadowModelViewInverse": np.linalg.inv(smv).astype(np.float32), "shadowProjection": sproj,
            "cameraPosition": tuple(cam), "sunPosition": tuple(sun), "moonPosition": tuple(moon), "shadowLightPosition": tuple(light),
            "fogColor": (0.2, 0.03, 0.02), "viewWidth": float(w), "viewHeight": float(h), "aspectRatio": w / h,
            "near": 0.05, "far": 192.0, "frameTimeCounter": shot.get("t", 123.4), "rainStrength": shot.get("fear", 0.0),
            "thunderStrength": shot.get("danger", 0.0), "wetness": shot.get("fear", 0.0) * 0.8, "nightVision": 0.0,
            "blindness": 0.0, "darknessFactor": shot.get("darkness", 0.0), "darknessLightFactor": shot.get("darkness", 0.0) * 0.4,
            "frameCounter": int(shot.get("t", 123.4) * 60), "isEyeInWater": 0, "heldItemId": shot.get("held", 0), "heldItemId2": 0,
            "heldBlockLightValue": shot.get("heldLight", 0), "heldBlockLightValue2": 0, "renderStage": 0, "entityId": 0,
            "currentRenderedItemId": 0, "moonPhase": 0, "eyeBrightnessSmooth": (bl * 16, sl * 16), "entityColor": (0.0, 0.0, 0.0, 0.0),
        }
        # ---- clear ----------------------------------------------------------------------
        gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
        for i in FORMATS:
            for k in (0, 1):
                self.cur[i] = k
                self.bind_targets([i], write_alt=False)
                gl.glClearColor(0, 0, 0, 0)
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
            self.draw_arrays(bride, cam)
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
        # ---- opaque terrain + entities ---------------------------------------------------
        gl.glDepthMask(1)
        gl.glEnable(glctx.GL_DEPTH_TEST)
        gl.glDepthFunc(glctx.GL_LEQUAL)
        gl.glDisable(glctx.GL_BLEND)
        tp = self.progs["gbuffers_terrain"]
        self.bind_targets(tp.targets, write_alt=False, depth=self.depth0)
        self.bind_samplers(tp)
        self.set_uniforms(tp, U)
        self.load_matrices(view, proj)
        self.draw_arrays(meshes["solid"], cam)
        if shot.get("bride", True):
            ep = self.progs["gbuffers_entities"]
            self.bind_targets(ep.targets, write_alt=False, depth=self.depth0)
            self.bind_samplers(ep)
            U3 = dict(U)
            U3["entityId"] = 20001
            self.set_uniforms(ep, U3)
            self.load_matrices(view, proj)
            self.draw_arrays(bride, cam)
        # ---- deferred ---------------------------------------------------------------------
        self.fullscreen("deferred", U)
        # ---- translucents -----------------------------------------------------------------
        wp = self.progs["gbuffers_water"]
        self.bind_targets(wp.targets, write_alt=False, depth=self.depth0)
        gl.glEnable(glctx.GL_DEPTH_TEST)
        gl.glEnablei(glctx.GL_BLEND, 0)
        gl.glDisablei(glctx.GL_BLEND, 1)
        gl.glBlendFuncSeparate(glctx.GL_SRC_ALPHA, glctx.GL_ONE_MINUS_SRC_ALPHA, glctx.GL_ONE, glctx.GL_ONE_MINUS_SRC_ALPHA)
        self.bind_samplers(wp)
        self.set_uniforms(wp, U)
        self.load_matrices(view, proj)
        self.draw_arrays(meshes["translucent"], cam)
        gl.glDisable(glctx.GL_BLEND)
        # ---- hand-held lantern -------------------------------------------------------------
        if shot.get("held", 0) == 10001:
            hp = self.progs["gbuffers_hand"]
            self.bind_targets(hp.targets, write_alt=False, depth=self.depth0)
            gl.glClear(glctx.GL_DEPTH_BUFFER_BIT)
            self.bind_samplers(hp)
            U4 = dict(U)
            U4["currentRenderedItemId"] = 10001
            self.set_uniforms(hp, U4)
            self.load_matrices(np.eye(4, dtype=np.float32), proj)
            tx, ty = TILES["chochin"]
            x0, y0, x1, y1, z = 0.16, -0.42, 0.30, -0.24, -0.5
            hq = dict(pos=np.array([(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y0, z), (x1, y1, z), (x0, y1, z)], np.float32),
                      nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)), col=np.ones((6, 4), np.float32),
                      uv0=np.array([(tx / 8, (ty + 1) / 8), ((tx + 1) / 8, (ty + 1) / 8), ((tx + 1) / 8, ty / 8), (tx / 8, (ty + 1) / 8), ((tx + 1) / 8, ty / 8), (tx / 8, ty / 8)], np.float32),
                      uv1=np.tile(np.array([[(bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0]], np.float32), (6, 1)))
            self.draw_arrays(hq, (0, 0, 0))
        # ---- composite chain + final ------------------------------------------------------
        for name in ("composite", "composite1", "composite2", "composite3"):
            self.fullscreen(name, U)
        stats = {}
        c0 = self.read_float(0)
        stats["nan"] = int(np.isnan(c0).sum() + np.isinf(c0).sum())
        stats["hdr_max"] = float(np.nanmax(c0[..., :3]))
        img = self.final_pass(U)
        gl.glFinish()
        lum = img.astype(np.float32).mean(axis=2)
        stats["mean"] = float(lum.mean())
        stats["black%"] = float((lum < 3).mean() * 100)
        return img, stats


def moon_quad_factory():
    tx, ty = TILES["moon"]

    def build(moon_view):
        m = np.array(moon_view, np.float64)
        d = m / np.linalg.norm(m)
        up = np.array([0, 1, 0.0]) if abs(d[1]) < 0.9 else np.array([1, 0, 0.0])
        s = np.cross(d, up); s /= np.linalg.norm(s)
        u = np.cross(s, d)
        c = d * 100.0
        k = 20.0
        cs = [c - s * k - u * k, c + s * k - u * k, c + s * k + u * k, c - s * k + u * k]
        uvs = [(tx / 8, (ty + 1) / 8), ((tx + 1) / 8, (ty + 1) / 8), ((tx + 1) / 8, ty / 8), (tx / 8, ty / 8)]
        idx = (0, 1, 2, 0, 2, 3)
        return dict(pos=np.array([cs[i] for i in idx], np.float32), nrm=np.tile(np.array([[0, 0, 1]], np.float32), (6, 1)),
                    col=np.ones((6, 4), np.float32), uv0=np.array([uvs[i] for i in idx], np.float32), uv1=np.ones((6, 2), np.float32) * (248 / 256))
    return build


SHOTS = {
    # 隠り世
    "k_corridor": dict(folder="meikon_kakuriyo", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), fear=0.12, held=10001, lantern=14),
    "k_corridor_dark": dict(folder="meikon_kakuriyo", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), fear=0.35, held=0, lantern=0),
    "k_fear": dict(folder="meikon_kakuriyo", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), fear=0.95, held=10001, lantern=14, glitch=True),
    "k_chase": dict(folder="meikon_kakuriyo", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), fear=1.0, danger=1.0, held=10001, lantern=14, t=130.2),
    "k_darkness": dict(folder="meikon_kakuriyo", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), fear=0.8, darkness=0.9, held=10001, lantern=14),
    "k_courtyard": dict(folder="meikon_kakuriyo", eye=(25.5, 65.62, 16.5), look=(0.05, 0.08, 1.0), fear=0.25, held=10001, lantern=14, bride=False),
    "k_torii_back": dict(folder="meikon_kakuriyo", eye=(25.5, 65.62, 38.3), look=(0.0, -0.32, -1.0), fear=0.25, held=0, lantern=0, bride=False),
    "k_shadow_test": dict(folder="meikon_kakuriyo", eye=(36.5, 68.5, 27.0), look=(-1.0, -0.42, 0.28), fear=0.0, held=0, lantern=0, bride=False),
    "k_moon": dict(folder="meikon_kakuriyo", eye=(25.5, 65.62, 16.5), look=None, fear=0.25, held=0, lantern=0, bride=False),
    "k_well": dict(folder="meikon_kakuriyo", eye=(21.5, 66.4, 22.9), look=(0.02, -1.0, -0.42), fear=0.2, held=0, lantern=0, bride=False),
    # overworld (root programs)
    "o_day": dict(folder="", eye=(25.5, 65.62, 16.5), look=(0.05, 0.08, 1.0), time=6000, held=0, lantern=0, bride=False),
    "o_rain": dict(folder="", eye=(25.5, 65.62, 16.5), look=(0.05, 0.08, 1.0), time=8000, fear=1.0, held=0, lantern=0, bride=False),
    "n_nether": dict(folder="world-1", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), held=0, heldLight=14, lantern=14, bride=False),
    "e_end": dict(folder="world1", eye=(25.5, 65.62, 16.5), look=(0.05, 0.08, 1.0), held=0, lantern=0, bride=False),
    "o_night": dict(folder="", eye=(3.5, 65.62, 11.5), look=(1, -0.06, 0.0), time=18000, held=0, heldLight=14, lantern=14, bride=False),
}


def glsl_hash11(p):
    f = np.float32
    p = f(p) * f(0.1031); p = f(p - np.floor(p))
    p = f(p * f(p + f(33.33)))
    p = f(p * f(p + p))
    return float(p - np.floor(p))


def glitch_time(fear, start=125.0):
    """a frame time at which the fear glitch of final.glsl is active (for the preview)"""
    x = min(max((fear - 0.78) / 0.22, 0.0), 1.0)
    g = x * x * (3 - 2 * x)
    t = start
    while t < start + 60:
        slot = math.floor(t * 8.0)
        if glsl_hash11(slot * 1.37 + 0.5) > 1.0 - (0.03 + 0.07 * g) + 0.01:
            return slot / 8.0 + 0.0625
        t += 0.125
    return start


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
    ap.add_argument("--out", default="/tmp/meikon_preview")
    ap.add_argument("--size", default="960x540")
    ap.add_argument("--shots", default=",".join(SHOTS))
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
    scene = Scene()
    renderers = {}
    paths = []
    moon_quad = moon_quad_factory()
    for name in args.shots.split(","):
        shot = dict(SHOTS[name])
        folder = shot["folder"]
        if folder not in renderers:
            renderers[folder] = Renderer(folder, w, h, overrides)
        r = renderers[folder]
        extra = [(*shot["eye"], shot["lantern"])] if shot.get("lantern") else []
        scene.compute_light(extra)
        meshes = scene.mesh()
        bride = box_mesh(scene, (19.65, 64.0, 10.65), (20.35, 66.4, 11.35), {"side": "kimono", "top": "hair"}, (20, 65, 11))
        if shot["look"] is None:
            # look straight at the moon
            sa = sky_angle(shot.get("time", 18000))
            cel = rot("y", -90.0) @ rot("z", r.sun_path) @ rot("x", sa * 360.0)
            mw = (cel @ np.array([0, -100, 0, 0], np.float32))[:3]
            shot["look"] = tuple(mw / np.linalg.norm(mw) * np.array([1, 0.80, 1]))
        if shot.get("glitch"):
            shot["t"] = glitch_time(shot.get("fear", 0.0))
        t0 = time.time()
        img, st = r.render(shot, scene, meshes, bride, moon_quad)
        p = os.path.join(args.out, name + ".png")
        write_png(p, img)
        paths.append(p)
        print("%-18s %5.2fs  mean %6.2f  black %5.1f%%  hdr max %8.3f  nan %d" % (name, time.time() - t0, st["mean"], st["black%"], st["hdr_max"], st["nan"]))
    if len(paths) > 1:
        contact_sheet(paths, os.path.join(args.out, "contact.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
