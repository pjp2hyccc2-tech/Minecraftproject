#!/usr/bin/env python3
"""ReisatsuShaders offline harness: Mesa compile/link check + Iris-like preview renders.

  python3 tools/shader/preview.py --check
        compile AND link every program of every dimension folder with Mesa (llvmpipe, the
        compiler the CI run uses) with the default options and with every option flipped.
        Catches what glslang cannot: vertex/fragment interface mismatches, driver quirks.

  python3 tools/shader/preview.py [--out DIR] [--size 960x540] [--shots a,b] [-D NAME=VALUE]
        renders the restaurant HIDAMARI (tools/shader/hidamari_scene.py: the floor plan of
        docs/DESIGN.md with the art's block models and textures where they exist) through the
        real pipeline - shadow pass (the emitter map), prepare / prepare1 (the light table),
        sky, gbuffers (terrain as indexed quads with mc_Entity / mc_midTexCoord / at_midBlock,
        spirits as translucent entities with their eyes layer, the hand), deferred, translucents,
        composite..composite4, final - emulating what Iris/Oculus do (ping-pong buffers,
        RENDERTARGETS, buffer formats / clear colours from the directives, per-buffer blending,
        the squeezed hand depth, mipmap directives, uniforms incl. both held items and the mod's
        channels: fear, danger, the finder, the flash, caught).
        Writes one PNG per shot plus contact.png, and prints buffer statistics (NaN / black).
        --timing prints the llvmpipe time of every pass. --list shows the shots.
        Assets: $REISATSU_ASSETS (an assets/reisatsu dir, e.g. extracted from the art branch:
        git archive origin/rs-art src/main/resources/assets | tar -x -C /tmp/art) or the repo's.

This is a development aid, not a substitute for the CI client run.
"""
import argparse
import math
import os
import re
import sys
sys.dont_write_bytecode = True
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import glctx  # noqa: E402
from glctx import gl  # noqa: E402
import lint  # noqa: E402
import build_pack  # noqa: E402
import mcscene  # noqa: E402
import hidamari_scene  # noqa: E402

ROOT = lint.ROOT
ATTRIBS = {"mc_Entity": 10, "mc_midTexCoord": 11, "at_midBlock": 12}
HIDAMARI = "reisatsu_hidamari"

# item / entity ids (item.properties, entity.properties)
FLASHLIGHT, FINDER = 10001, 10004
VISITOR, WAITRESS, CHEF, CHILD, MIRROR_WOMAN, MANAGER, MEDIUM = 20001, 20002, 20003, 20004, 20005, 20006, 20010

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
        for name_, val in re.findall(r"const\s+(?:int|float)\s+(shadowDistance|sunPathRotation|shadowMapResolution)\s*=\s*([-\d.]+)", "".join(preprocess(vs))):
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
#  meshes that are not blocks: spirits, the medium, the hand
# =====================================================================================

def box_quads(x0, y0, z0, x1, y1, z1, tex, rect_of, face_tex=None):
    """axis-aligned box -> list of (normal, 4 corners, uv rect)"""
    ft = face_tex or {}
    faces = {
        (1, 0, 0): [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        (-1, 0, 0): [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        (0, 0, 1): [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        (0, 0, -1): [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        (0, 1, 0): [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        (0, -1, 0): [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }
    out = []
    for n, cs in faces.items():
        out.append((n, cs, rect_of(ft.get(n, tex))))
    return out


def quads_to_arrays(quads, light, alpha=1.0, color=(1.0, 1.0, 1.0)):
    P, N, C, U0, U1 = [], [], [], [], []
    for n, cs, r in quads:
        uvs = [(r[0], r[1]), (r[0], r[3]), (r[2], r[3]), (r[2], r[1])]
        for i in (0, 1, 2, 0, 2, 3):
            P.append(cs[i]); N.append(n); C.append((color[0], color[1], color[2], alpha)); U0.append(uvs[i]); U1.append(light)
    f = lambda a, k: np.array(a, np.float32).reshape(-1, k)
    return dict(pos=f(P, 3), nrm=f(N, 3), col=f(C, 4), uv0=f(U0, 2), uv1=f(U1, 2))


# simple box figures for the spirits (the real ones are the spirits worker's models)
SPIRIT_LOOK = {
    VISITOR: dict(body=(14, 14, 16), legs=(30, 34, 52), head=(22, 22, 24), h=1.9),       # leather, jeans, helmet
    WAITRESS: dict(body=(92, 58, 38), legs=(92, 58, 38), head=(176, 174, 168), apron=(214, 120, 40), h=1.8),
    CHEF: dict(body=(220, 216, 204), legs=(60, 56, 50), head=(26, 20, 18), h=2.2),
    CHILD: dict(body=(150, 190, 220), legs=(150, 190, 220), head=(230, 200, 60), h=1.1),
    MIRROR_WOMAN: dict(body=(225, 225, 220), legs=(30, 30, 34), head=(14, 14, 16), h=1.8),
    MANAGER: dict(body=(220, 222, 224), legs=(40, 40, 46), head=(150, 146, 140), h=1.85),
    MEDIUM: dict(body=(60, 36, 70), legs=(40, 30, 46), head=(190, 170, 150), h=1.6),
}


def figure_mesh(atlas, kind, x, y, z, yaw_deg, light, alpha):
    """a standing box figure at feet (x, y, z) facing yaw (0 = south +z)"""
    look = SPIRIT_LOOK[kind]
    h = look["h"]
    s = h / 1.8

    def rect_of(name):
        return atlas.rect(name)

    def solid(name, rgb):
        if name not in atlas.rects:
            tile = np.zeros((16, 16, 4), np.uint8)
            rng = np.random.default_rng(abs(hash(name)) % 1000)
            tile[..., :3] = np.clip(np.array(rgb)[None, None] * (0.85 + rng.random((16, 16, 1)) * 0.25), 0, 255)
            tile[..., 3] = 255
            atlas._load = (lambda orig, nm, tl: (lambda n: tl if n == nm else orig(n)))(atlas._load, name, tile)
        return name
    tb = solid("fig_body_%d" % kind, look["body"])
    tl = solid("fig_legs_%d" % kind, look["legs"])
    th = solid("fig_head_%d" % kind, look["head"])
    q = []
    q += box_quads(-0.12 * s, 0.0, -0.10, 0.12 * s, 0.75 * s, 0.10, tl, rect_of)
    q += box_quads(-0.24 * s, 0.75 * s, -0.13, 0.24 * s, 1.45 * s, 0.13, tb, rect_of)
    if "apron" in look:
        ta = solid("fig_apron_%d" % kind, look["apron"])
        q += box_quads(-0.20 * s, 0.70 * s, 0.13, 0.20 * s, 1.25 * s, 0.15, ta, rect_of)
    q += box_quads(-0.36 * s, 0.80 * s, -0.09, -0.24 * s, 1.42 * s, 0.09, tb, rect_of)
    q += box_quads(0.24 * s, 0.80 * s, -0.09, 0.36 * s, 1.42 * s, 0.09, tb, rect_of)
    q += box_quads(-0.17 * s, 1.45 * s, -0.17 * s, 0.17 * s, 1.80 * s, 0.17 * s, th, rect_of)
    a = math.radians(yaw_deg)
    ca, sa = math.cos(a), math.sin(a)
    out = []
    for n, cs, r in q:
        n2 = (n[0] * ca + n[2] * sa, n[1], -n[0] * sa + n[2] * ca)
        cs2 = [(x + p[0] * ca + p[2] * sa, y + p[1], z - p[0] * sa + p[2] * ca) for p in cs]
        out.append((n2, cs2, r))
    body = quads_to_arrays(out, light, alpha)
    # the eyes layer: two small glints on the face
    ez = 0.175 * s
    eyes = []
    for ex in (-0.09 * s, 0.04 * s):
        cs = [(ex, 1.66 * s, ez), (ex, 1.62 * s, ez), (ex + 0.05 * s, 1.62 * s, ez), (ex + 0.05 * s, 1.66 * s, ez)]
        cs2 = [(x + p[0] * ca + p[2] * sa, y + p[1], z - p[0] * sa + p[2] * ca) for p in cs]
        eyes.append(((sa, 0.0, ca), cs2, rect_of(solid("fig_eye_%d" % kind, (230, 120, 60) if kind == CHEF else (200, 210, 220)))))
    return body, quads_to_arrays(eyes, light, alpha)


def hand_mesh(atlas, item, side, light):
    """the held flashlight in view space (Iris draws it with its own projection)"""
    if item != FLASHLIGHT:
        return None
    sx = 1.0 if side > 0 else -1.0
    x0, x1, y0, y1, z0, z1 = 0.20, 0.30, -0.36, -0.27, -0.62, -0.30
    if sx < 0:
        x0, x1 = -x1, -x0

    def solid(name, rgb):
        if name not in atlas.rects:
            tile = np.zeros((16, 16, 4), np.uint8)
            tile[..., :3] = rgb
            tile[..., 3] = 255
            atlas._load = (lambda orig, nm, tl: (lambda n: tl if n == nm else orig(n)))(atlas._load, name, tile)
        return atlas.rect(name)
    body = solid("hand_torch", (40, 40, 44))
    lens = solid("hand_lens", (250, 240, 210))
    q = box_quads(x0, y0, z0, x1, y1, z1, "hand_torch", lambda n: body)
    q.append(((0, 0, -1), [(x1, y1, z0 - 0.001), (x1, y0, z0 - 0.001), (x0, y0, z0 - 0.001), (x0, y1, z0 - 0.001)], lens))
    return quads_to_arrays(q, light)


# =====================================================================================
#  renderer
# =====================================================================================

GLFMT = {"RGBA16F": glctx.GL_RGBA16F, "RGBA8": glctx.GL_RGBA8, "RGBA16": 0x805B, "RGB16F": 0x881B,
         "R11F_G11F_B10F": 0x8C3A, "RGBA32F": 0x8814}
UNITS = {"gtexture": 0, "noisetex": 1, "depthtex0": 2, "depthtex1": 3, "shadowtex0": 4, "shadowcolor0": 20}


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


def read_blend_overrides():
    """blend.<program>.<buffer>=off from shaders.properties"""
    props = mcscene.read_props(os.path.join(ROOT, "shaders.properties"))
    off = {}
    for k, v in props.items():
        m = re.match(r"blend\.(\w+)\.colortex(\d+)$", k)
        if m and v.strip() == "off":
            off.setdefault(m.group(1), set()).add(int(m.group(2)))
    return off


class Renderer:
    def __init__(self, folder, w, h, overrides, atlas):
        self.folder, self.w, self.h = folder, w, h
        self.progs = {}
        for n in build_pack.programs_of(folder):
            try:
                p = Program(folder, n, overrides)
            except FileNotFoundError:
                continue
            if p.errors:
                raise RuntimeError("%s/%s failed:\n%s" % (folder or "root", n, "\n".join(p.errors)))
            self.progs[n] = p
        sp = self.progs.get("shadow")
        consts = sp.consts if sp else {}
        self.shadow_res = int(consts.get("shadowMapResolution", 2048))
        self.shadow_dist = consts.get("shadowDistance", 64.0)
        self.sun_path = consts.get("sunPathRotation", 0.0)
        fmts, self.clears = buffer_directives()
        self.formats = {i: GLFMT[f] for i, f in fmts.items()}
        self.units = dict(UNITS)
        for i in sorted(self.formats):
            self.units["colortex%d" % i] = 5 + i
        self.blend_off = read_blend_overrides()
        # colour buffers (ping-pong) + depth
        self.tex = {i: [tex2d(w, h, f, glctx.GL_RGBA, glctx.GL_FLOAT), tex2d(w, h, f, glctx.GL_RGBA, glctx.GL_FLOAT)] for i, f in self.formats.items()}
        self.cur = {i: 0 for i in self.formats}
        self.depth0 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.depth1 = tex2d(w, h, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, filt=glctx.GL_NEAREST)
        self.depth_fbo = glctx.gen_framebuffer()
        self.out_tex = tex2d(w, h, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE)
        sr = self.shadow_res
        self.shadow_depth = tex2d(sr, sr, glctx.GL_DEPTH_COMPONENT32F, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT)
        if folder != HIDAMARI:
            gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_MODE, glctx.GL_COMPARE_REF_TO_TEXTURE)
            gl.glTexParameteri(glctx.GL_TEXTURE_2D, glctx.GL_TEXTURE_COMPARE_FUNC, glctx.GL_LEQUAL)
        self.shadow_color = tex2d(sr, sr, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, filt=glctx.GL_NEAREST)
        self.fbo = glctx.gen_framebuffer()
        rng = np.random.default_rng(3)
        nz = rng.integers(0, 256, (256, 256, 4), dtype=np.uint8)
        nz[..., 3] = 255
        self.noise = tex2d(256, 256, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, nz, wrap=glctx.GL_REPEAT)
        self.atlas_obj = atlas
        self.atlas = None
        self.upload_atlas()
        self._keep = []

    def upload_atlas(self):
        a = self.atlas_obj
        if self.atlas is None:
            self.atlas = tex2d(a.SIZE, a.SIZE, glctx.GL_RGBA8, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE,
                               np.ascontiguousarray(a.img), filt=glctx.GL_NEAREST)
        else:
            gl.glBindTexture(glctx.GL_TEXTURE_2D, self.atlas)
            gl.glTexImage2D(glctx.GL_TEXTURE_2D, 0, glctx.GL_RGBA8, a.SIZE, a.SIZE, 0, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE,
                            np.ascontiguousarray(a.img).ctypes.data)

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
            if loc >= 0:
                gl.glUniform1i(loc, unit)
        binds = {"gtexture": self.atlas, "noisetex": self.noise, "depthtex0": self.depth0, "depthtex1": self.depth1,
                 "shadowtex0": self.shadow_depth, "shadowcolor0": self.shadow_color}
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
            key = {"mc_Entity": "ent", "mc_midTexCoord": "mid", "at_midBlock": "midblock"}[name]
            if key in arrs:
                gl.glEnableVertexAttribArray(loc)
                comps = 3 if key == "midblock" else 4
                gl.glVertexAttribPointer(loc, comps, glctx.GL_FLOAT, 0, 0, arrs[key].ctypes.data)
            else:
                gl.glDisableVertexAttribArray(loc)
                gl.glVertexAttrib4f(loc, 0.0, 0.0, 0.0, 1.0)
        if "idx" in arrs:
            idx = arrs["idx"]
            gl.glDrawElements(glctx.GL_TRIANGLES, len(idx), glctx.GL_UNSIGNED_INT, idx.ctypes.data)
        else:
            gl.glDrawArrays(glctx.GL_TRIANGLES, 0, len(pos))
        for name, loc in ATTRIBS.items():
            gl.glDisableVertexAttribArray(loc)
        self._keep = keep

    def gbuffer_draw(self, prog_name, U, arrs, mv, proj, cam, blend=None, extra=None):
        """blend: None = off; 'translucent' or 'additive' (per-buffer 'off' from shaders.properties)"""
        p = self.progs[prog_name]
        self.bind_targets(p.targets, write_alt=False, depth=self.depth0)
        off = self.blend_off.get(prog_name, set())
        for k in range(len(p.targets)):
            gl.glDisablei(glctx.GL_BLEND, k)
        if blend == "translucent":
            gl.glBlendFuncSeparate(glctx.GL_SRC_ALPHA, glctx.GL_ONE_MINUS_SRC_ALPHA, glctx.GL_ONE, glctx.GL_ONE_MINUS_SRC_ALPHA)
            for k, t in enumerate(p.targets):
                if t not in off:
                    gl.glEnablei(glctx.GL_BLEND, k)
        elif blend == "additive":
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
        gl.glDrawBuffers(1, (glctx.U * 1)(glctx.GL_COLOR_ATTACHMENT0))
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

    def copy_depth(self):
        d = np.zeros((self.h, self.w), np.float32)
        gl.glBindTexture(glctx.GL_TEXTURE_2D, self.depth0)
        gl.glGetTexImage(glctx.GL_TEXTURE_2D, 0, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, d.ctypes.data)
        gl.glBindTexture(glctx.GL_TEXTURE_2D, self.depth1)
        gl.glTexImage2D(glctx.GL_TEXTURE_2D, 0, glctx.GL_DEPTH_COMPONENT32F, self.w, self.h, 0, glctx.GL_DEPTH_COMPONENT, glctx.GL_FLOAT, d.ctypes.data)

    def read_float(self, idx):
        t = self.tex[idx][self.cur[idx]]
        buf = np.zeros((self.h, self.w, 4), np.float32)
        gl.glBindTexture(glctx.GL_TEXTURE_2D, t)
        gl.glGetTexImage(glctx.GL_TEXTURE_2D, 0, glctx.GL_RGBA, glctx.GL_FLOAT, buf.ctypes.data)
        return buf

    def read_shadow_color(self):
        sr = self.shadow_res
        buf = np.zeros((sr, sr, 4), np.uint8)
        gl.glBindTexture(glctx.GL_TEXTURE_2D, self.shadow_color)
        gl.glGetTexImage(glctx.GL_TEXTURE_2D, 0, glctx.GL_RGBA, glctx.GL_UNSIGNED_BYTE, buf.ctypes.data)
        return buf

    # ---- one frame --------------------------------------------------------------------
    def render(self, shot, world, meshes, figures, timing=None, history_frames=1):
        w, h = self.w, self.h
        cam = np.array(shot["eye"], np.float64)
        fwd = np.array(shot["look"], np.float64)
        view = look_rot(fwd)
        fov = shot.get("fov", 70.0)
        proj = perspective(fov, w / h, 0.05, 192.0 * 4)
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
        bl, sl = world.light_at(*[int(math.floor(v)) for v in cam])
        held, held2 = shot.get("held", 0), shot.get("held2", 0)
        t = shot.get("t", 123.4)
        finder = FINDER in (held, held2)
        U = {
            "gbufferModelView": view, "gbufferModelViewInverse": np.linalg.inv(view).astype(np.float32),
            "gbufferProjection": proj, "gbufferProjectionInverse": np.linalg.inv(proj).astype(np.float32),
            "shadowModelView": smv, "shadowModelViewInverse": np.linalg.inv(smv).astype(np.float32), "shadowProjection": sproj,
            "cameraPosition": tuple(cam), "sunPosition": tuple(sun), "moonPosition": tuple(moon), "shadowLightPosition": tuple(light),
            "fogColor": (0.02, 0.025, 0.03), "viewWidth": float(w), "viewHeight": float(h), "aspectRatio": w / h,
            "near": 0.05, "far": 192.0, "frameTimeCounter": t, "frameTime": shot.get("frameTime", 1.0 / 30.0), "rainStrength": shot.get("fear", 0.0),
            "thunderStrength": shot.get("danger", 0.0), "nightVision": 1.0 if finder else 0.0,
            "blindness": shot.get("flash", 0.0), "darknessFactor": shot.get("darkness", 0.0),
            "darknessLightFactor": shot.get("darkness", 0.0) * 0.3,
            "frameCounter": int(t * 60), "worldTime": int(world_time), "isEyeInWater": 0, "heldItemId": held, "heldItemId2": held2,
            "heldBlockLightValue": 0, "heldBlockLightValue2": 0, "renderStage": 0, "entityId": 0, "blockEntityId": 0,
            "currentRenderedItemId": 0, "moonPhase": 0, "eyeBrightnessSmooth": (bl * 16, sl * 16), "entityColor": (0.0, 0.0, 0.0, 0.0),
        }
        T = timing if timing is not None else {}

        def mark(name, t0):
            gl.glFinish()
            T[name] = T.get(name, 0.0) + time.time() - t0
            return time.time()

        tm = time.time()
        # ---- clear (to the directive clear colours; colortex9 is kept between frames) -------------
        gl.glBindFramebuffer(glctx.GL_FRAMEBUFFER, self.fbo)
        for i in self.formats:
            if i == 9 and history_frames > 1 and getattr(self, "_hist_ok", False):
                continue
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
            gl.glDepthFunc(glctx.GL_LEQUAL)
            gl.glDepthMask(1)
            gl.glDisable(glctx.GL_BLEND)
            cc = (0.0, 0.0, 0.0, 0.0) if self.folder == HIDAMARI else (1.0, 1.0, 1.0, 1.0)
            gl.glClearColor(*cc)
            gl.glClear(glctx.GL_COLOR_BUFFER_BIT | glctx.GL_DEPTH_BUFFER_BIT)
            gl.glUseProgram(sp.id)
            self.bind_samplers(sp)
            self.set_uniforms(sp, U)
            self.load_matrices(smv, sproj)
            self.draw_arrays(meshes["solid"], cam)
            self.draw_arrays(meshes["translucent"], cam)
        tm = mark("shadow", tm)
        # ---- prepare: the light table ----------------------------------------------------------------
        for name in ("prepare", "prepare1"):
            if name in self.progs:
                self.fullscreen(name, U)
        tm = mark("prepare", tm)
        # ---- opaque terrain + entities + eyes + the solid hand --------------------------------
        self.bind_targets([0], write_alt=False, depth=self.depth0)
        gl.glClear(glctx.GL_DEPTH_BUFFER_BIT)
        gl.glDepthMask(1)
        gl.glEnable(glctx.GL_DEPTH_TEST)
        gl.glDepthFunc(glctx.GL_LEQUAL)
        self.gbuffer_draw("gbuffers_terrain", U, meshes["solid"], view, proj, cam)
        for fig in figures:
            if fig["translucent"]:
                continue
            self.gbuffer_draw("gbuffers_entities", U, fig["body"], view, proj, cam, extra={"entityId": fig["id"]})
        # the spirits: translucent entities (alpha = what the naked eye sees; 1 in the finder)
        for fig in figures:
            if not fig["translucent"]:
                continue
            gl.glDepthMask(1)
            self.gbuffer_draw("gbuffers_entities_translucent", U, fig["body"], view, proj, cam, blend="translucent",
                              extra={"entityId": fig["id"]})
            gl.glDepthMask(0)
            self.gbuffer_draw("gbuffers_spidereyes", U, fig["eyes"], view, proj, cam, blend="additive", extra={"entityId": fig["id"]})
            gl.glDepthMask(1)
        hand_proj = np.diag([1.0, 1.0, 0.125, 1.0]).astype(np.float32) @ perspective(70.0, w / h, 0.05, 192.0 * 4)
        hl = ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0)
        for item, side in ((held, 1), (held2, -1)):
            hm = None if finder else hand_mesh(self.atlas_obj, item, side, hl)   # the mod hides the hand in the finder
            if hm is not None:
                self.upload_atlas()
                self.gbuffer_draw("gbuffers_hand", U, hm, np.eye(4, dtype=np.float32), hand_proj, (0, 0, 0), extra={"currentRenderedItemId": item})
        tm = mark("gbuffers", tm)
        # ---- deferred ---------------------------------------------------------------------
        self.fullscreen("deferred", U)
        tm = mark("deferred", tm)
        # ---- depthtex1: the depth before the translucents (Iris copies it here) ---------------
        self.copy_depth()
        # ---- translucents -----------------------------------------------------------------
        gl.glEnable(glctx.GL_DEPTH_TEST)
        self.gbuffer_draw("gbuffers_water", U, meshes["translucent"], view, proj, cam, blend="translucent")
        tm = mark("translucent", tm)
        # ---- composite chain + final ------------------------------------------------------
        for name in ("composite", "composite1", "composite2", "composite3", "composite4"):
            self.fullscreen(name, U)
            tm = mark(name, tm)
        self._hist_ok = True
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


# =====================================================================================
#  shots
# =====================================================================================

def mc_dir(yaw, pitch):
    """Minecraft yaw / pitch (degrees; yaw 0 = south +Z, pitch > 0 = down) -> look vector"""
    y, p = math.radians(yaw), math.radians(pitch)
    return (-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p))


EYE = 65 + 1.62


def shot(x, z, yaw, pitch, **kw):
    d = dict(eye=(x, kw.pop("y", EYE), z), look=mc_dir(yaw, pitch), folder=HIDAMARI)
    d.update(kw)
    return d


# circuits: the start of the game (dining off, kitchen off, restroom on, sign on, register night lights)
START = {}
SHOTS = {
    # the dark dining room: exit signs, the drink bar, the cake case, sodium through the windows
    "r_dining_dark": shot(21.5, 14.5, -35.0, 8.0, fear=0.2),
    "r_dining_beam": shot(21.5, 14.5, -35.0, 8.0, fear=0.2, held=FLASHLIGHT),
    "r_dining_bar": shot(24.5, 16.5, 132.0, 10.0, fear=0.2),
    "r_register": shot(19.5, 23.5, 95.0, 8.0, fear=0.2),
    # the roof sign outside, falling in through the blinds onto the booths by the windows
    "r_blinds": shot(24.5, 24.5, -15.0, 28.0, fear=0.2),
    "r_dining_lit": shot(21.5, 14.5, -35.0, 8.0, fear=0.0, circuits=dict(dining=True)),
    # the wet lot at night: the pole sign, the vending machines, the sodium lamps, puddles
    "r_lot": shot(28.5, 44.5, 150.0, 4.0, fear=0.1),
    "r_lot_sign": shot(26.5, 38.5, -70.0, -6.0, fear=0.1),
    "r_lot_vending": shot(13.5, 40.5, 155.0, 10.0, fear=0.1),
    # the same through the finder: the lights must keep their faces (no blown-out boxes)
    "r_lot_finder": shot(13.5, 40.5, 155.0, 10.0, fear=0.1, held=FINDER),
    # the kitchen under its fluorescent tubes: steel, the blue flames, the heat lamp
    "r_kitchen": shot(31.5, 7.5, 125.0, 12.0, fear=0.1, circuits=dict(kitchen=True)),
    # the women's restroom: the dark mirror over the sinks, the cavity and the woman behind it
    "r_restroom": shot(10.5, 20.5, 180.0, 6.0, fear=0.35, spirits=[(MIRROR_WOMAN, 10.5, 65, 13.5, 0.0, 0.25)]),
    "r_trail": shot(21.5, 14.5, -20.0, 6.0, fear=0.3, held=FINDER, spirits=[(WAITRESS, 23.2, 65, 18.3, 270.0, 1.0)],
                    sequence=dict(frames=8, move=(-0.22, 0.0, 0.0), dt=1.0 / 30.0)),
    "r_restroom_finder": shot(10.5, 20.5, 180.0, 6.0, fear=0.35, held=FINDER, spirits=[(MIRROR_WOMAN, 10.5, 65, 13.5, 0.0, 1.0)]),
    # the finder (reishi) on the waitress between the tables, the flash frame
    "r_finder": shot(21.5, 14.5, -20.0, 6.0, fear=0.3, held=FINDER, spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 1.0)]),
    "r_finder_lamp": shot(21.5, 14.5, -20.0, 6.0, fear=0.3, held=FINDER, held2=FLASHLIGHT, spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 1.0)]),
    "r_flash": shot(21.5, 14.5, -20.0, 6.0, fear=0.3, held=FINDER, flash=1.0, spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 1.0)]),
    "r_flash_decay": shot(21.5, 14.5, -20.0, 6.0, fear=0.3, held=FINDER, flash=0.35, spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 1.0)]),
    # the naked eye: the waitress faint, the visitor invisible (only the haze)
    "r_spirit_eye": shot(21.5, 14.5, -20.0, 6.0, fear=0.4, held=FLASHLIGHT,
                         spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 0.3), (VISITOR, 20.6, 65, 17.6, 160.0, 0.0)]),
    # fear, danger, caught
    "r_fear": shot(21.5, 14.5, -35.0, 8.0, fear=0.95, danger=0.15, held=FLASHLIGHT, t=131.7),
    "r_danger": shot(21.5, 14.5, -35.0, 8.0, fear=0.8, danger=1.0, held=FLASHLIGHT, t=126.02,
                     spirits=[(WAITRESS, 23.2, 65, 18.3, 200.0, 0.9)]),
    "r_caught": shot(21.5, 14.5, -35.0, 8.0, fear=0.9, darkness=0.65, held=FLASHLIGHT),
    # the visitor's profile through the south window, from the lot (a photo window)
    "r_window_visitor": shot(31.5, 35.5, 160.0, 4.0, fear=0.3, spirits=[(VISITOR, 33.0, 65, 28.5, 90.0, 1.0)], held=FINDER),
    # other dimensions: the root programs on the same place by day / the overworld at night
    "o_day": dict(shot(28.5, 44.5, 150.0, 4.0), folder="", time=6000),
    "o_night": dict(shot(28.5, 44.5, 150.0, 4.0, held=FLASHLIGHT), folder="", time=18000),
}


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
            for name in build_pack.programs_of(folder):
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
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--out", default="/tmp/reisatsu_preview")
    ap.add_argument("--size", default="960x540")
    ap.add_argument("--shots", default="")
    ap.add_argument("--timing", action="store_true", help="print the time of every pass (llvmpipe)")
    ap.add_argument("--dump-emap", action="store_true", help="also write the emitter map of each shot")
    ap.add_argument("--t", type=float, default=None, help="frameTimeCounter for every shot (flicker phases)")
    ap.add_argument("--dump", default="", help="comma separated colortex indices to dump after the frame (rgb, scaled to max)")
    ap.add_argument("-D", action="append", default=[])
    args = ap.parse_args()
    if args.check:
        return run_check()
    if args.list:
        print("\n".join(SHOTS))
        return 0
    overrides = dict(d.split("=", 1) for d in args.D)
    w, h = (int(v) for v in args.size.split("x"))
    os.makedirs(args.out, exist_ok=True)
    ver, ren = glctx.init()
    print("GL:", ver, "|", ren)
    atlas = mcscene.Atlas(os.path.join(mcscene.ASSETS, "textures", "block"))
    models = mcscene.Models(os.path.join(mcscene.ASSETS, "models", "block"), atlas, extra=hidamari_scene.STANDINS)
    ids = mcscene.BlockIds()
    names = args.shots.split(",") if args.shots else list(SHOTS)
    worlds = {}

    def get_world(circuits):
        key = tuple(sorted((circuits or {}).items()))
        if key not in worlds:
            t0 = time.time()
            wd = hidamari_scene.build(circuits)
            wd.compute_light()
            meshes = wd.mesh(models, ids)
            worlds[key] = (wd, meshes)
            print("world %s: %d solid / %d translucent quads (%.1fs); %d real models, %d real textures (%s)" % (
                dict(key), len(meshes["solid"]["idx"]) // 6, len(meshes["translucent"]["idx"]) // 6, time.time() - t0,
                len(models.real), len(atlas.real), mcscene.ASSETS))
        return worlds[key]

    renderers = {}
    paths = []
    for name in names:
        sh = dict(SHOTS[name])
        if args.t is not None:
            sh["t"] = args.t
        wd, meshes = get_world(sh.get("circuits"))
        figures = []
        for (kind, x, y, z, yaw, alpha) in sh.get("spirits", []):
            bl, sl = wd.light_at(int(math.floor(x)), y + 1, int(math.floor(z)))
            body, eyes = figure_mesh(atlas, kind, x, y, z, yaw, ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0), alpha)
            figures.append(dict(id=kind, body=body, eyes=eyes, translucent=kind != MEDIUM))
        folder = sh["folder"]
        if folder not in renderers:
            renderers[folder] = Renderer(folder, w, h, overrides, atlas)
        r = renderers[folder]
        r.upload_atlas()
        timing = {} if args.timing else None
        t0 = time.time()
        seq = sh.get("sequence")
        if seq:
            # frames with the spirits moving: colortex9 (the afterimage) is kept between them
            r._hist_ok = False
            base = list(sh.get("spirits", []))
            for f in range(seq["frames"]):
                figs = []
                for (kind, x, y, z, yaw, alpha) in base:
                    mx, my, mz = (v * (f - seq["frames"] + 1) for v in seq["move"])
                    bl, sl = wd.light_at(int(math.floor(x + mx)), y + 1, int(math.floor(z + mz)))
                    body, eyes = figure_mesh(atlas, kind, x + mx, y + my, z + mz, yaw, ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0), alpha)
                    figs.append(dict(id=kind, body=body, eyes=eyes, translucent=True))
                r.upload_atlas()
                sh2 = dict(sh, t=sh.get("t", 123.4) + f * seq["dt"])
                img, st = r.render(sh2, wd, meshes, figs, timing, history_frames=2 if f > 0 else 1)
        else:
            img, st = r.render(sh, wd, meshes, figures, timing)
        p = os.path.join(args.out, name + ".png")
        write_png(p, img)
        paths.append(p)
        print("%-18s %5.2fs  mean %6.2f  black %5.1f%%  hdr max %8.3f  nan %d" % (name, time.time() - t0, st["mean"], st["black%"], st["hdr_max"], st["nan"]))
        if timing:
            print("    " + "  ".join("%s %.0fms" % (k, v * 1000) for k, v in timing.items()))
        for idx in [int(v) for v in args.dump.split(",") if v.strip()]:
            buf = r.read_float(idx)[::-1, :, :3]
            m = max(float(np.nanmax(buf)), 1e-6)
            write_png(os.path.join(args.out, "%s_ct%d.png" % (name, idx)), np.clip(buf / m * 255.0, 0, 255).astype(np.uint8))
            np.save(os.path.join(args.out, "%s_ct%d.npy" % (name, idx)), buf)
            print("    colortex%d max %.4f" % (idx, m))
        if args.dump_emap and folder == HIDAMARI:
            em = r.read_shadow_color()
            write_png(os.path.join(args.out, name + "_emap.png"), np.ascontiguousarray(em[::-1, :, :3]))
    if len(paths) > 1:
        contact_sheet(paths, os.path.join(args.out, "contact.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
