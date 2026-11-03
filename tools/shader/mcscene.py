"""A small Minecraft-like block world for the HitoriShaders preview (tools/shader/preview.py).
(Stand-in textures for room 203: tools/shader/hitori_textures.py.)

* Assets: the art's block models (assets/hitori/models/block/*.json: elements, uv, face
  rotation, element rotation, forge_data full-bright faces, render_type) and textures
  (assets/hitori/textures/block/*.png, first frame of animated ones), packed into one atlas.
  Blocks without a model are cubes of their texture. Missing textures get a procedural tile.
  Paths: $HITORI_ASSETS (an assets/hitori directory) or the repo's src/main/resources.
* Ids: block.properties of the pack, matched with the placement's block state (so the
  preview exercises the real ids).
* World: a voxel grid of placements (name, state, facing), vanilla-like light propagation
  (block light from each block's level, sky light from above), smooth lighting / AO for cube
  faces, face culling, and meshes as indexed quads (4 vertices each, like Sodium - the pack's
  shadow pass uses gl_VertexID & 3) with mc_Entity, mc_midTexCoord and at_midBlock.
"""
import json
import math
import os
import re
from collections import deque

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
SHADERS = os.path.join(REPO, "src", "main", "shaderpack", "shaders")
ASSETS = os.environ.get("HITORI_ASSETS") or os.path.join(REPO, "src", "main", "resources", "assets", "hitori")

FACES = {
    "north": ((0, 0, -1)), "south": ((0, 0, 1)), "east": ((1, 0, 0)), "west": ((-1, 0, 0)),
    "up": ((0, 1, 0)), "down": ((0, -1, 0)),
}


# =====================================================================================
#  block.properties
# =====================================================================================

def read_props(path):
    out = {}
    if not os.path.isfile(path):
        return out
    with open(path, encoding="utf-8") as fh:
        text = fh.read().replace("\\\n", " ")
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


class BlockIds:
    """block.properties: (namespace:name, state) -> id"""

    def __init__(self, path=os.path.join(SHADERS, "block.properties")):
        self.entries = []      # (name, {prop: value}, id)
        for k, v in read_props(path).items():
            if not k.startswith("block."):
                continue
            bid = int(k.split(".")[1])
            for tok in v.split():
                parts = tok.split(":")
                name = ":".join(parts[:2]) if len(parts) >= 2 else "minecraft:" + parts[0]
                state = dict(p.split("=", 1) for p in parts[2:] if "=" in p)
                self.entries.append((name, state, bid))

    def lookup(self, name, state):
        if ":" not in name:
            name = "hitori:" + name
        for n, st, bid in self.entries:
            if n != name:
                continue
            if all(str(state.get(p, "")) == val for p, val in st.items()):
                return bid
        return 0


# =====================================================================================
#  textures: one atlas
# =====================================================================================

def _noise(rng, shape, scale):
    return rng.random(shape) * scale


def procedural_tile(name, rng):
    """16x16 RGBA uint8 stand-ins for textures the art has not drawn (yet)"""
    try:
        import hitori_textures
        t = hitori_textures.tile(name, rng)
        if t is not None:
            return t
    except ImportError:
        pass
    y, x = np.mgrid[0:16, 0:16]
    a = np.full((16, 16), 255.0)
    t = np.zeros((16, 16, 3))
    n = lambda s: _noise(rng, (16, 16), s)[..., None]
    if name in ("asphalt",):
        t[:] = np.array([44, 44, 46]) * (0.65 + n(0.55))
    elif name == "parking_line":
        t[:] = np.array([44, 44, 46]) * (0.65 + n(0.5))
        t[(x >= 6) & (x <= 9)] = np.array([205, 205, 198]) * (0.85 + n(0.15))[(x >= 6) & (x <= 9)]
    elif name == "road_line":
        t[:] = np.array([44, 44, 46]) * (0.65 + n(0.5))
        t[(x >= 7) & (x <= 8)] = np.array([210, 170, 40]) * (0.85 + n(0.15))[(x >= 7) & (x <= 8)]
    elif name in ("sidewalk", "curb", "curb_top", "wheel_stop"):
        t[:] = np.array([118, 116, 112]) * (0.75 + n(0.35))
        t[(x % 8 == 0) | (y % 8 == 0)] *= 0.6
    elif name == "puddle":
        t[:] = np.array([40, 58, 70]); a[:] = 170
    elif name == "shop_window":
        t[:] = np.array([150, 180, 185]); a[:] = 60
        fr = (x == 0) | (x == 15) | (y == 0) | (y == 15)
        t[fr] = np.array([170, 172, 176]); a[fr] = 255
    elif name.startswith("vending_machine"):
        t[:] = np.array([200, 205, 210]) * (0.9 + n(0.1))
        if name.endswith("front"):
            win = (x > 1) & (x < 14) & (y > 1) & (y < 11)
            t[win] = np.array([235, 240, 248])
            cans = win & ((x % 3) != 0) & ((y % 4) > 1)
            cols = np.array([[200, 40, 40], [40, 90, 200], [230, 200, 40], [40, 160, 80], [220, 120, 30]])
            t[cans] = cols[((x[cans] // 3) + (y[cans] // 4)) % 5]
            t[(y >= 12) & (y <= 13) & (x > 3) & (x < 12)] = np.array([30, 30, 30])
    elif name.startswith("pole_sign_face") or name.startswith("roof_sign_face"):
        t[:] = np.array([240, 150, 50])
        cx, cy = 7.5, 7.5
        r = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        t[r < 5] = np.array([255, 220, 90])
        t[(r > 5.8) & (r < 6.8)] = np.array([255, 245, 220])
        t[(x == 0) | (x == 15) | (y == 0) | (y == 15)] = np.array([120, 70, 30])
    elif name == "street_lamp_head":
        t[:] = np.array([70, 72, 76]) * (0.85 + n(0.2))
        t[(y > 5) & (y < 12) & (x > 3) & (x < 12)] = np.array([255, 200, 120])
    elif name in ("lamp_pole", "pole_sign_pole", "street_lamp_pole", "utility_pole"):
        t[:] = np.array([120, 122, 126]) * (0.8 + n(0.25))
    elif name == "traffic_light":
        t[:] = np.array([40, 42, 40])
        t[((x - 7.5) ** 2 + (y - 7.5) ** 2) < 16] = np.array([255, 190, 30])
    elif name == "guardrail":
        t[:] = np.array([190, 192, 195]) * (0.8 + n(0.2))
        t[(y < 5) | (y > 10)] = 0; a[(y < 5) | (y > 10)] = 0
    elif name == "hedge":
        t[:] = np.array([30, 60, 32]) * (0.5 + n(0.8)); a[rng.random((16, 16)) < 0.18] = 0
    elif name in ("rice_paddy", "rice_paddy_side"):
        t[:] = np.array([62, 48, 34]) * (0.6 + n(0.6))
        t[(x % 4 == 1) & (y % 3 == 0)] = np.array([150, 130, 70])
    elif name == "skin":
        t[:] = np.array([170, 168, 162]) * (0.9 + n(0.1))
    else:
        t[:] = np.array([128, 120, 110]) * (0.8 + n(0.3))
    out = np.zeros((16, 16, 4), np.uint8)
    out[..., :3] = np.clip(t, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    return out


class Atlas:
    SIZE = 1024

    def __init__(self, tex_dir):
        self.tex_dir = tex_dir
        self.img = np.zeros((self.SIZE, self.SIZE, 4), np.uint8)
        self.rects = {}          # name -> (u0, v0, u1, v1) in [0, 1]
        self.real = set()
        self.x = self.y = self.row_h = 0
        self.rng = np.random.default_rng(11)

    def _load(self, name):
        from PIL import Image
        path = os.path.join(self.tex_dir, name + ".png")
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            try:
                im = np.asarray(Image.open(path).convert("RGBA"), np.uint8)
            except Exception:
                im = None
            if im is not None:
                if os.path.isfile(path + ".mcmeta"):
                    w = im.shape[1]
                    im = im[:w]                     # animated: the first frame
                self.real.add(name)
                return im
        return procedural_tile(name, self.rng)

    def rect(self, name):
        if name not in self.rects:
            im = self._load(name)
            h, w = im.shape[:2]
            if self.x + w > self.SIZE:
                self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
            if self.y + h > self.SIZE:
                raise RuntimeError("atlas full")
            self.img[self.y:self.y + h, self.x:self.x + w] = im
            s = float(self.SIZE)
            # half-texel inset against bleeding
            self.rects[name] = ((self.x + 0.02) / s, (self.y + 0.02) / s, (self.x + w - 0.02) / s, (self.y + h - 0.02) / s)
            self.x += w
            self.row_h = max(self.row_h, h)
        return self.rects[name]

    def pixel(self, name, u, v):
        """sRGB rgba of a texture at uv in [0,1] (for shading decisions in the builder)"""
        u0, v0, u1, v1 = self.rect(name)
        X = int((u0 + (u1 - u0) * u) * self.SIZE)
        Y = int((v0 + (v1 - v0) * v) * self.SIZE)
        return self.img[min(Y, self.SIZE - 1), min(X, self.SIZE - 1)]


# =====================================================================================
#  block models
# =====================================================================================

def _default_uv(face, f, t):
    if face == "down":
        return (f[0], 16 - t[2], t[0], 16 - f[2])
    if face == "up":
        return (f[0], f[2], t[0], t[2])
    if face == "north":
        return (16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1])
    if face == "south":
        return (f[0], 16 - t[1], t[0], 16 - f[1])
    if face == "west":
        return (f[2], 16 - t[1], t[2], 16 - f[1])
    return (16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1])          # east


def _face_corners(face, f, t):
    """4 corners (block units 0..16) in the order top-left, bottom-left, bottom-right, top-right
    as seen from outside the face, with their (s, t) position in the face"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == "north":     # seen from -z: left = +x
        c = [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)]
    elif face == "south":   # seen from +z: left = -x
        c = [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)]
    elif face == "west":    # seen from -x: left = -z
        c = [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)]
    elif face == "east":    # seen from +x: left = +z
        c = [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)]
    elif face == "up":      # seen from above: u = x, v = z
        c = [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)]
    else:                   # down: u = x, v = 16 - z
        c = [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]
    st = [(0.0, 0.0), (0.0, 1.0), (1.0, 1.0), (1.0, 0.0)]
    return c, st


def _rot_st(s, t, r):
    r %= 360
    if r == 90:
        return t, 1.0 - s
    if r == 180:
        return 1.0 - s, 1.0 - t
    if r == 270:
        return 1.0 - t, s
    return s, t


def _rotate_point(p, rot):
    if not rot:
        return p
    o = rot.get("origin", [8, 8, 8])
    ang = math.radians(rot.get("angle", 0))
    ax = rot.get("axis", "y")
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(ang), math.sin(ang)
    if ax == "x":
        y, z = y * c - z * s, y * s + z * c
    elif ax == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    if rot.get("rescale"):
        k = 1.0 / max(abs(c), 1e-3)
        if ax == "x":
            y, z = y * k, z * k
        elif ax == "y":
            x, z = x * k, z * k
        else:
            x, y = x * k, y * k
    return (x + o[0], y + o[1], z + o[2])


def _full_faces(tex_of, uv_side=None):
    faces = {}
    for f in FACES:
        faces[f] = {"texture": tex_of(f), "cullface": f}
        if uv_side and f in ("north", "south", "east", "west"):
            faces[f]["uv"] = uv_side
    return faces


# vanilla parent models the world's generated models use (elements with texture variables)
VANILLA_PARENTS = {
    "block/cube": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": _full_faces(lambda f: "#" + f)}]},
    "block/cube_all": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": _full_faces(lambda f: "#all")}]},
    "block/leaves": {"render_type": "minecraft:cutout_mipped",
                     "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": _full_faces(lambda f: "#all")}]},
    "block/cube_bottom_top": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": _full_faces(
        lambda f: "#top" if f == "up" else ("#bottom" if f == "down" else "#side"))}]},
    "block/cube_column": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": _full_faces(
        lambda f: "#end" if f in ("up", "down") else "#side")}]},
    "block/carpet": {"elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": dict(
        _full_faces(lambda f: "#wool", uv_side=[0, 15, 16, 16]), up={"texture": "#wool"})}]},
    "block/slab": {"elements": [{"from": [0, 0, 0], "to": [16, 8, 16], "faces": dict(_full_faces(
        lambda f: "#top" if f == "up" else ("#bottom" if f == "down" else "#side"), uv_side=[0, 8, 16, 16]), up={"texture": "#top"})}]},
    "block/slab_top": {"elements": [{"from": [0, 8, 0], "to": [16, 16, 16], "faces": dict(_full_faces(
        lambda f: "#top" if f == "up" else ("#bottom" if f == "down" else "#side"), uv_side=[0, 0, 16, 8]), down={"texture": "#bottom"})}]},
    "block/stairs": {"elements": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": _full_faces(lambda f: "#top" if f == "up" else ("#bottom" if f == "down" else "#side"))},
        {"from": [8, 8, 0], "to": [16, 16, 16], "faces": _full_faces(lambda f: "#top" if f == "up" else ("#bottom" if f == "down" else "#side"))}]},
    "block/door_bottom_left": {"render_type": "minecraft:cutout",
                               "elements": [{"from": [0, 0, 0], "to": [3, 16, 16], "faces": _full_faces(lambda f: "#bottom")}]},
    "block/door_top_left": {"render_type": "minecraft:cutout",
                            "elements": [{"from": [0, 0, 0], "to": [3, 16, 16], "faces": _full_faces(lambda f: "#top")}]},
}
for _k in ("inner_stairs", "outer_stairs"):
    VANILLA_PARENTS["block/" + _k] = VANILLA_PARENTS["block/stairs"]
for _a, _b in (("door_bottom_right", "door_bottom_left"), ("door_bottom_left_open", "door_bottom_left"),
               ("door_bottom_right_open", "door_bottom_left"), ("door_top_right", "door_top_left"),
               ("door_top_left_open", "door_top_left"), ("door_top_right_open", "door_top_left")):
    VANILLA_PARENTS["block/" + _a] = VANILLA_PARENTS["block/" + _b]


class Models:
    def __init__(self, model_dir, atlas, extra=None):
        self.dir = model_dir
        self.atlas = atlas
        self.cache = {}
        self.extra = extra or {}       # stand-in models (same format) for what the art lacks
        self.real = set()

    def load_json(self, name):
        path = os.path.join(self.dir, name + ".json")
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as fh:
                self.real.add(name)
                m = json.load(fh)
            return self._resolve(m)
        return self.extra.get(name)

    def _resolve(self, m, depth=0):
        """follow the parent chain: elements / render_type from the first parent that has them,
        texture variables merged (the child wins)"""
        if "elements" in m or depth > 6:
            return m
        parent = m.get("parent", "")
        pm = None
        if parent.startswith("hitori:"):
            path = os.path.join(self.dir, parent.split("/")[-1] + ".json")
            if os.path.isfile(path):
                with open(path, encoding="utf-8") as fh:
                    pm = self._resolve(json.load(fh), depth + 1)
        else:
            pm = VANILLA_PARENTS.get(parent.replace("minecraft:", ""))
        if pm is None:
            return m
        out = dict(pm)
        tex = dict(pm.get("textures", {}))
        tex.update(m.get("textures", {}))
        out["textures"] = tex
        if "render_type" in m:
            out["render_type"] = m["render_type"]
        return out

    def quads(self, name, cube_tex=None):
        """-> (list of quads, render layer). quad = dict(pos[4] (0..1 block units), normal,
        uv[4] (atlas), tex (name), cull (face or None), bright (forge_data full-bright), shade)"""
        key = (name, str(cube_tex))
        if key in self.cache:
            return self.cache[key]
        m = self.load_json(name) if cube_tex is None else None
        layer = "solid"
        out = []
        if m is None or "elements" not in m:
            tex = cube_tex or {"all": name}
            for face in FACES:
                tn = tex.get(face) or tex.get("side" if face in ("north", "south", "east", "west") else ("top" if face == "up" else "bottom")) or tex.get("all") or name
                out.append(self._face(face, (0, 0, 0), (16, 16, 16), {"texture": tn}, None, {"#" + tn: tn}, cull=True))
        else:
            rt = m.get("render_type", "")
            layer = "translucent" if rt.endswith("translucent") else ("cutout" if "cutout" in rt else "solid")
            texvars = {}
            for k, v in m.get("textures", {}).items():
                texvars["#" + k] = v.split("/")[-1]
            for _ in range(3):              # resolve #refs to other variables
                for k, v in list(texvars.items()):
                    if v.startswith("#") and v in texvars:
                        texvars[k] = texvars[v]
            for e in m["elements"]:
                for face, fd in e.get("faces", {}).items():
                    out.append(self._face(face, e["from"], e["to"], fd, e.get("rotation"), texvars, cull=False))
        self.cache[key] = (out, layer)
        return out, layer

    def _face(self, face, f, t, fd, rot, texvars, cull):
        tn = fd.get("texture", "")
        tn = texvars.get(tn, tn.lstrip("#"))
        uv = fd.get("uv") or _default_uv(face, f, t)
        corners, st = _face_corners(face, f, t)
        r = fd.get("rotation", 0)
        rect = self.atlas.rect(tn)
        pos, uvs = [], []
        for c, (s_, t_) in zip(corners, st):
            s2, t2 = _rot_st(s_, t_, r)
            u = (uv[0] + (uv[2] - uv[0]) * s2) / 16.0
            v = (uv[1] + (uv[3] - uv[1]) * t2) / 16.0
            uvs.append((rect[0] + (rect[2] - rect[0]) * u, rect[1] + (rect[3] - rect[1]) * v))
            p = _rotate_point(c, rot)
            pos.append((p[0] / 16.0, p[1] / 16.0, p[2] / 16.0))
        n = FACES[face]
        if rot:
            n = _rotate_point((n[0] + 8, n[1] + 8, n[2] + 8), dict(rot, origin=[8, 8, 8], rescale=False))
            n = (n[0] - 8, n[1] - 8, n[2] - 8)
        fdat = fd.get("forge_data", {})
        bright = fdat.get("block_light", 0) >= 15
        on_boundary = (face == "north" and f[2] == 0) or (face == "south" and t[2] == 16) or \
                      (face == "west" and f[0] == 0) or (face == "east" and t[0] == 16) or \
                      (face == "down" and f[1] == 0) or (face == "up" and t[1] == 16)
        mid = (rect[0] + (rect[2] - rect[0]) * (uv[0] + uv[2]) / 32.0, rect[1] + (rect[3] - rect[1]) * (uv[1] + uv[3]) / 32.0)
        return dict(pos=pos, normal=n, uv=uvs, tex=tn, cull=(face if (cull or (fd.get("cullface") and on_boundary)) else None),
                    bright=bright, boundary=on_boundary and not rot, mid=mid, face=face)


# =====================================================================================
#  the world
# =====================================================================================

ROT_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def rotate_y(p, deg):
    """rotate a block-local point (0..1) around the block centre like a blockstate y rotation"""
    x, y, z = p
    for _ in range((deg // 90) % 4):
        x, z = 1.0 - z, x
    return (x, y, z)


def rotate_n(n, deg):
    x, y, z = n
    for _ in range((deg // 90) % 4):
        x, z = -z, x
    return (x, y, z)


class Block:
    __slots__ = ("name", "state", "facing", "model", "cube", "opaque", "light", "layer_override", "tint")

    def __init__(self, name, state=None, facing="north", model=None, cube=None, opaque=None, light=0, layer=None, tint=None):
        self.name = name
        self.state = state or {}
        self.facing = facing
        self.model = model          # model json name (art) or None
        self.cube = cube            # dict of face textures for cubes
        self.opaque = opaque
        self.light = light
        self.layer_override = layer
        self.tint = tint


class World:
    def __init__(self, x0, y0, z0, sx, sy, sz):
        self.o = np.array([x0, y0, z0])
        self.size = (sx, sy, sz)
        self.grid = {}           # (x, y, z) world -> Block
        self.entities = []

    def set(self, x, y, z, b):
        if b is None:
            self.grid.pop((x, y, z), None)
        else:
            self.grid[(x, y, z)] = b

    def get(self, x, y, z):
        return self.grid.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, make):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, make() if callable(make) else make)

    def is_opaque(self, x, y, z):
        b = self.grid.get((x, y, z))
        return b is not None and bool(b.opaque)

    # ---- light ----------------------------------------------------------------------------
    def compute_light(self):
        sx, sy, sz = self.size
        ox, oy, oz = self.o
        op = np.zeros((sx, sy, sz), bool)
        for (x, y, z), b in self.grid.items():
            X, Y, Z = x - ox, y - oy, z - oz
            if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz and b.opaque:
                op[X, Y, Z] = True
        sky = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for X in range(sx):
            for Z in range(sz):
                for Y in range(sy - 1, -1, -1):
                    if op[X, Y, Z]:
                        break
                    b = self.grid.get((X + ox, Y + oy, Z + oz))
                    if b is not None and b.name in ("hedge",):
                        break
                    sky[X, Y, Z] = 15
                    q.append((X, Y, Z))
        self._bfs(sky, q, op)
        blk = np.zeros((sx, sy, sz), np.int8)
        q = deque()
        for (x, y, z), b in self.grid.items():
            if b.light > 0:
                X, Y, Z = x - ox, y - oy, z - oz
                if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz:
                    blk[X, Y, Z] = max(blk[X, Y, Z], b.light)
                    q.append((X, Y, Z))
        self._bfs(blk, q, op)
        self.sky, self.blk, self.op = sky, blk, op

    def _bfs(self, L, q, op):
        sx, sy, sz = L.shape
        while q:
            x, y, z = q.popleft()
            base = L[x, y, z] - 1
            if base <= 0:
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                X, Y, Z = x + dx, y + dy, z + dz
                if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz and not op[X, Y, Z] and L[X, Y, Z] < base:
                    L[X, Y, Z] = base
                    q.append((X, Y, Z))

    def light_at(self, x, y, z):
        X, Y, Z = x - self.o[0], y - self.o[1], z - self.o[2]
        sx, sy, sz = self.size
        if 0 <= X < sx and 0 <= Y < sy and 0 <= Z < sz:
            return int(self.blk[X, Y, Z]), int(self.sky[X, Y, Z])
        return 0, 15

    # ---- meshes -------------------------------------------------------------------------------
    def mesh(self, models, ids):
        """-> {layer: arrays} with indexed quads. layers: solid (incl. cutout), translucent"""
        acc = {"solid": [], "translucent": []}
        for (x, y, z), b in self.grid.items():
            quads, layer = models.quads(b.model or b.name, b.cube)
            if b.layer_override:
                layer = b.layer_override
            bid = ids.lookup(b.name, b.state)
            deg = ROT_Y.get(b.facing, 0)
            dst = acc["translucent" if layer == "translucent" else "solid"]
            for qd in quads:
                n = rotate_n(qd["normal"], deg)
                if qd["cull"] is not None:
                    ni = (int(round(n[0])), int(round(n[1])), int(round(n[2])))
                    nb = self.grid.get((x + ni[0], y + ni[1], z + ni[2]))
                    if nb is not None and (nb.opaque or (nb.name == b.name and b.cube is not None and not b.opaque)):
                        continue
                dst.append((x, y, z, b, bid, qd, deg, n))
        return {k: self._arrays(v) for k, v in acc.items()}

    def _vertex_light(self, x, y, z, b, qd, n, p):
        """(block, sky, ao) of one vertex: smooth light over the 4 cells in front of a cube face"""
        if qd["bright"]:
            return 15.0, 15.0, 1.0
        ni = (int(round(n[0])), int(round(n[1])), int(round(n[2])))
        if not qd["boundary"] or b.cube is None:
            # model faces: the cell in front for boundary faces, the block itself for inner ones
            if qd["boundary"]:
                bl, sl = self.light_at(x + ni[0], y + ni[1], z + ni[2])
            else:
                bl, sl = self.light_at(x, y, z)
                if b.light > 0:
                    bl = max(bl, b.light)
            return float(bl), float(sl), 1.0
        fc = (x + ni[0], y + ni[1], z + ni[2])
        ax = int(np.argmax(np.abs(ni)))
        others = [i for i in range(3) if i != ax]
        s = [1 if c > 0.5 else -1 for c in p]
        c1 = list(fc); c1[others[0]] += s[others[0]]
        c2 = list(fc); c2[others[1]] += s[others[1]]
        c3 = list(fc); c3[others[0]] += s[others[0]]; c3[others[1]] += s[others[1]]
        cells = [fc, tuple(c1), tuple(c2), tuple(c3)]
        occ = [self.is_opaque(*c) for c in cells]
        if occ[1] and occ[2]:
            occ[3] = True
        lights = [self.light_at(*c) for c, o in zip(cells, occ) if not o]
        if not lights:
            lights = [self.light_at(*fc)]
        bl = sum(l[0] for l in lights) / len(lights)
        sl = sum(l[1] for l in lights) / len(lights)
        ao = sum(0.25 if o else 1.0 for o in occ) / 4.0
        return bl, sl, ao

    def _arrays(self, items):
        n = len(items) * 4
        P = np.zeros((n, 3), np.float32)
        N = np.zeros((n, 3), np.float32)
        C = np.ones((n, 4), np.float32)
        U0 = np.zeros((n, 2), np.float32)
        U1 = np.zeros((n, 2), np.float32)
        E = np.zeros((n, 4), np.float32)
        M = np.zeros((n, 4), np.float32)
        B = np.zeros((n, 3), np.float32)
        k = 0
        for (x, y, z, b, bid, qd, deg, nrm) in items:
            for i in range(4):
                p = rotate_y(qd["pos"][i], deg)
                bl, sl, ao = self._vertex_light(x, y, z, b, qd, nrm, p)
                P[k] = (x + p[0], y + p[1], z + p[2])
                N[k] = nrm
                tint = b.tint or (1.0, 1.0, 1.0)
                C[k] = (tint[0], tint[1], tint[2], ao)
                U0[k] = qd["uv"][i]
                U1[k] = ((bl * 16 + 8) / 256.0, (sl * 16 + 8) / 256.0)
                E[k] = (bid, 0, 0, 0)
                M[k] = (qd["mid"][0], qd["mid"][1], 0, 1)
                B[k] = ((0.5 - p[0]) * 64.0, (0.5 - p[1]) * 64.0, (0.5 - p[2]) * 64.0)
                k += 1
        quads = np.arange(len(items), dtype=np.uint32) * 4
        idx = np.stack([quads, quads + 1, quads + 2, quads + 2, quads + 3, quads], axis=1).reshape(-1).astype(np.uint32)
        return dict(pos=P, nrm=N, col=C, uv0=U0, uv1=U1, ent=E, mid=M, midblock=B, idx=idx)
