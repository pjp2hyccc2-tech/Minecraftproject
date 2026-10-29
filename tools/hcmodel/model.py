"""Model / bone / cube / animation definitions, UV packing and JSON export."""
import json
import math
import os

import numpy as np

FACES = ('top', 'bottom', 'west', 'north', 'east', 'south')


# ----------------------------------------------------------------------------- math
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def translate(x, y, z):
    m = np.eye(4)
    m[0, 3], m[1, 3], m[2, 3] = x, y, z
    return m


def scale_m(x, y, z):
    return np.diag([x, y, z, 1.0])


def part_matrix(px, py, pz, rx, ry, rz, sx=1.0, sy=1.0, sz=1.0):
    """ModelPart.translateAndRotate: T(pivot) * Rz * Ry * Rx * S  (angles in radians)."""
    m = translate(px, py, pz)
    if rx or ry or rz:
        m = m @ rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    if sx != 1 or sy != 1 or sz != 1:
        m = m @ scale_m(sx, sy, sz)
    return m


# ----------------------------------------------------------------------------- model classes
class Cube:
    def __init__(self, bone, origin, size, mat='default', inflate=0.0, mirror=False, uv=None,
                 faces=None, decals=None, mirror_of=None, name=None, glow=False, tint=None, seed=None):
        self.bone = bone
        self.origin = tuple(float(v) for v in origin)
        self.size = tuple(float(v) for v in size)
        self.mat = mat
        self.inflate = float(inflate)
        self.mirror = mirror or mirror_of is not None
        self.mirror_of = mirror_of
        self.uv = uv
        self.faces = faces or {}          # face -> material name override
        self.decals = decals or []        # list of dicts (see paint.py)
        self.name = name
        self.glow = glow                  # whole cube emissive
        self.tint = tint                  # optional (r,g,b) multiplier for this cube
        self.seed = seed if seed is not None else (hash((bone.name, self.origin, self.size)) & 0xFFFF)
        if mirror_of is not None:
            assert tuple(mirror_of.size) == tuple(self.size), 'mirror_of cubes must have identical size'

    @property
    def uv_w(self):
        dx, dy, dz = self.size
        return int(math.ceil(2 * (dz + dx)))

    @property
    def uv_h(self):
        dx, dy, dz = self.size
        return int(math.ceil(dz + dy))

    def face_rects(self):
        """face -> (u0, v0, w, h) texel rectangles (integer)."""
        u, v = self.uv
        dx, dy, dz = (int(math.ceil(s)) for s in self.size)
        return {
            'top': (u + dz, v, dx, dz),
            'bottom': (u + dz + dx, v, dx, dz),
            'west': (u, v + dz, dz, dy),
            'north': (u + dz, v + dz, dx, dy),
            'east': (u + dz + dx, v + dz, dz, dy),
            'south': (u + 2 * dz + dx, v + dz, dx, dy),
        }

    def to_json(self):
        d = {'uv': [int(self.uv[0]), int(self.uv[1])], 'origin': list(self.origin), 'size': list(self.size)}
        if self.inflate:
            d['inflate'] = self.inflate
        if self.mirror:
            d['mirror'] = True
        return d


class Bone:
    def __init__(self, model, name, parent, pivot, rot):
        self.model = model
        self.name = name
        self.parent = parent
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.cubes = []
        self.children = []

    def bone(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        b = Bone(self.model, name, self, pivot, rot)
        self.children.append(b)
        self.model._register(b)
        return b

    def cube(self, origin, size, mat='default', **kw):
        c = Cube(self, origin, size, mat, **kw)
        self.cubes.append(c)
        return c

    def rest_matrix(self):
        rx, ry, rz = (math.radians(a) for a in self.rot)
        m = part_matrix(*self.pivot, rx, ry, rz)
        return m if self.parent is None else self.parent.rest_matrix() @ m

    def to_json(self):
        d = {'name': self.name, 'pivot': list(self.pivot)}
        if any(self.rot):
            d['rotation'] = list(self.rot)
        if self.cubes:
            d['cubes'] = [c.to_json() for c in self.cubes]
        if self.children:
            d['children'] = [ch.to_json() for ch in self.children]
        return d

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()


def catmullrom(t, a, b, c, d):
    return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t)


class Animation:
    """Keyframe animation with Minecraft semantics (KeyframeAnimations.animate)."""

    def __init__(self, name, length, loop=False):
        self.name = name
        self.length = float(length)
        self.loop = loop
        self.channels = {}

    def _add(self, bone, chan, keys, interp):
        lst = self.channels.setdefault(bone, {}).setdefault(chan, [])
        for k in keys:
            t, v = k[0], k[1]
            it = k[2] if len(k) > 2 else interp
            lst.append((float(t), tuple(float(x) for x in v), it))
        lst.sort(key=lambda e: e[0])
        return self

    def rot(self, bone, keys, interp='c'):
        return self._add(bone, 'rotation', keys, interp)

    def pos(self, bone, keys, interp='c'):
        return self._add(bone, 'position', keys, interp)

    def scale(self, bone, keys, interp='c'):
        return self._add(bone, 'scale', keys, interp)

    def sample(self, bone, chan, time):
        keys = self.channels.get(bone, {}).get(chan)
        if not keys:
            return None
        t = (time % self.length) if self.loop and self.length > 0 else time
        n = len(keys)
        first = n
        for idx in range(n):
            if t <= keys[idx][0]:
                first = idx
                break
        i = max(0, first - 1)
        j = min(n - 1, i + 1)
        k0, k1 = keys[i], keys[j]
        if j != i:
            f = min(1.0, max(0.0, (t - k0[0]) / max(1e-9, (k1[0] - k0[0]))))
        else:
            f = 0.0
        if k1[2] == 'l':
            return tuple(a + (b - a) * f for a, b in zip(k0[1], k1[1]))
        p0 = keys[max(0, i - 1)][1]
        p3 = keys[min(n - 1, j + 1)][1]
        return tuple(catmullrom(f, p0[q], k0[1][q], k1[1][q], p3[q]) for q in range(3))

    def to_json(self):
        bones = {}
        for b, chans in self.channels.items():
            bones[b] = {}
            for c, keys in chans.items():
                bones[b][c] = [[round(t, 4), [round(x, 3) for x in v]] + (['l'] if it == 'l' else []) for t, v, it in keys]
        return {'length': round(self.length, 4), 'loop': self.loop, 'bones': bones}


class Model:
    def __init__(self, name, tex_w=None, tex_h=None):
        self.name = name
        self.roots = []
        self.bones = {}
        self.animations = {}
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.config = {'head_bones': [], 'walk_speed': 1.0, 'run_speed': 1.6, 'walk_scale': 2.0, 'render_scale': 1.0}
        self.palette_overrides = {}

    def _register(self, b):
        if b.name in self.bones:
            raise ValueError('duplicate bone name ' + b.name)
        self.bones[b.name] = b

    def bone(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        b = Bone(self, name, None, pivot, rot)
        self.roots.append(b)
        self._register(b)
        return b

    def anim(self, name, length, loop=False):
        a = Animation(name, length, loop)
        self.animations[name] = a
        return a

    def all_cubes(self):
        for r in self.roots:
            for b in r.walk():
                for c in b.cubes:
                    yield c

    # ------------------------------------------------------------------ UV packing
    def pack(self):
        cubes = [c for c in self.all_cubes() if c.uv is None and c.mirror_of is None]
        rects = sorted(cubes, key=lambda c: (c.uv_h, c.uv_w), reverse=True)
        area = sum((c.uv_w + 1) * (c.uv_h + 1) for c in rects)
        maxw = max([c.uv_w for c in rects] + [16])
        w = self.tex_w or 1 << max(6, int(math.ceil(math.log2(max(maxw, math.sqrt(area * 1.25))))))
        while True:
            placed = self._skyline(rects, w)
            if placed is not None:
                used_h = max([c.uv[1] + c.uv_h for c in rects] + [16])
                break
            w *= 2
        h = self.tex_h or 1 << max(5, int(math.ceil(math.log2(used_h))))
        if self.tex_h and used_h > self.tex_h:
            raise ValueError('texture too small for %s: need %d' % (self.name, used_h))
        for c in self.all_cubes():
            if c.mirror_of is not None:
                c.uv = c.mirror_of.uv
        self.tex_w, self.tex_h = w, h
        return w, h

    @staticmethod
    def _skyline(rects, width):
        sky = [0] * width
        for c in rects:
            rw, rh = c.uv_w + 1, c.uv_h + 1
            if rw > width:
                return None
            best_x, best_y = None, None
            for x in range(0, width - rw + 1):
                y = max(sky[x:x + rw])
                if best_y is None or y < best_y:
                    best_x, best_y = x, y
            c.uv = (best_x, best_y)
            for x in range(best_x, best_x + rw):
                sky[x] = best_y + rh
        return True

    # ------------------------------------------------------------------ export
    def to_json(self):
        return {
            'format': 'hcmodel-1',
            'texture_width': self.tex_w,
            'texture_height': self.tex_h,
            'parts': [r.to_json() for r in self.roots],
            'animations': {n: a.to_json() for n, a in self.animations.items()},
            'config': self.config,
        }

    def export(self, assets_root, texture_name=None, paint=True, extra_textures=None):
        """Packs UVs, paints the texture and writes JSON + PNG(s) under assets/huntcraft/."""
        from . import paint as painter
        if any(c.uv is None for c in self.all_cubes()):
            self.pack()
        tex_name = texture_name or self.name
        os.makedirs(os.path.join(assets_root, 'hcmodels'), exist_ok=True)
        with open(os.path.join(assets_root, 'hcmodels', self.name + '.json'), 'w') as f:
            json.dump(self.to_json(), f, separators=(',', ':'))
        if paint:
            img, glow = painter.paint_model(self)
            tex_dir = os.path.join(assets_root, 'textures', 'entity')
            os.makedirs(tex_dir, exist_ok=True)
            img.save(os.path.join(tex_dir, tex_name + '.png'))
            glow_path = os.path.join(tex_dir, tex_name + '_glow.png')
            if glow is not None:
                glow.save(glow_path)
            elif os.path.exists(glow_path):
                os.remove(glow_path)
            self._last_texture = img
            return img, glow
        return None, None
