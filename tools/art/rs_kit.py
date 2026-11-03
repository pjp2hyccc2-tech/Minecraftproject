"""HITORI prop kit: block models built from boxes whose faces are painted into an automatically packed texture atlas.

    A = Atlas('gas_range')                       # -> textures/block/gas_range.png (size chosen by the packer)
    m = Model('gas_range', A)
    m.box([0, 0, 1], [16, 14, 16], STEEL_MAT, faces={'north': front_painter})
    m.box(..., glow=('up',))                     # emissive faces (forge_data block/sky light 15)

Every face of a box gets an atlas region sized exactly like the face (1 texel per model pixel), painted by the
face's material (Mat) or painter; identical (material, size, orientation) faces share a region. UVs are resolved
when the model is written, after the atlas has been packed, so textures and models always agree.
All atlas sizes are multiples of 16 (the block atlas keeps its mip levels).
"""
import json
import math
import os

import numpy as np

from common import REGISTRY, MODEL_ROOT, C

NS = 'hitori'
ATLASES = {}      # texture path ('block/x') -> Atlas
MODELS = {}       # model name -> Model | dict
ITEM_MODELS = {}  # item model name -> dict
SIDES = ('north', 'south', 'east', 'west')
ALL = ('north', 'south', 'east', 'west', 'up', 'down')


class Mat:
    """Material painter: fn(w, h, face=<'v'|'h'|face name>, **kw) -> (h, w, 4) float array."""

    def __init__(self, key, fn, per_face=False, **kw):
        self.key, self.fn, self.kw, self.per_face = key, fn, kw, per_face

    def paint(self, w, h, face):
        return np.asarray(self.fn(w, h, face=face, **self.kw), np.float32)

    def variant(self, key_suffix, **kw):
        k = dict(self.kw)
        k.update(kw)
        return Mat(self.key + key_suffix, self.fn, self.per_face, **k)


class Region:
    __slots__ = ('key', 'w', 'h', 'painter', 'x', 'y')

    def __init__(self, key, w, h, painter):
        self.key, self.w, self.h, self.painter = key, int(w), int(h), painter
        self.x = self.y = None


def _ceil(v):
    return max(1, int(math.ceil(v - 1e-6)))


class Atlas:
    def __init__(self, name, kind='block', cutout=False, opaque=False, fixed=None, base=None, pad=0, frames=1,
                 frametime=2, interpolate=False):
        """fixed=(W, H): explicit layout (regions placed with add_at, base painter for the whole texture)."""
        self.name, self.kind = name, kind
        self.path = f'{kind}/{name}'
        self.ref = f'{NS}:{self.path}'
        self.regions = {}
        self.fixed = fixed
        self.base = base
        self.pad = pad
        self.W = self.H = None
        if fixed:
            self.W, self.H = fixed
        self.cutout, self.opaque = cutout, opaque
        self.frames = frames
        ATLASES[self.path] = self
        REGISTRY[self.path] = dict(fn=self.render, size=None, cutout=cutout, opaque=opaque, frames=frames, tile=False,
                                   name=self.path)
        if frames > 1:
            REGISTRY[self.path]['mcmeta'] = {'animation': {'frametime': frametime, 'interpolate': interpolate}}

    # ---------------------------------------------------------------- regions
    def add(self, key, w, h, painter):
        if key in self.regions:
            return key
        if self.fixed:
            raise ValueError(f'{self.name}: fixed atlas, use add_at')
        if self.W is not None:
            raise RuntimeError(f'{self.name}: region {key} added after the atlas was packed')
        self.regions[key] = Region(key, w, h, painter)
        return key

    def add_at(self, key, x, y, w, h, painter=None):
        r = Region(key, w, h, painter)
        r.x, r.y = x, y
        self.regions[key] = r
        return key

    def face_region(self, mat, fw, fh, face, uniq=None):
        w, h = _ceil(fw), _ceil(fh)
        orient = face if mat.per_face else ('v' if face in SIDES else 'h')
        key = f'{mat.key}|{orient}|{w}x{h}' + (f'|{uniq}' if uniq else '')
        if key not in self.regions:
            self.add(key, w, h, lambda w_, h_, _m=mat, _o=orient: _m.paint(w_, h_, _o))
        return key

    # ---------------------------------------------------------------- packing
    def pack(self):
        if self.W is not None and all(r.x is not None for r in self.regions.values()):
            return
        regs = sorted(self.regions.values(), key=lambda r: (-r.h, -r.w, r.key))
        if not regs:
            self.W = self.H = 16
            return
        p = self.pad
        maxw = max(r.w for r in regs) + p
        best = None
        for W in (16, 32, 48, 64, 80, 96, 112, 128, 160, 192, 256):
            if W < maxw:
                continue
            x = y = rowh = 0
            pos = {}
            for r in regs:
                if x + r.w + p > W:
                    y += rowh
                    x, rowh = 0, 0
                pos[r.key] = (x, y)
                x += r.w + p
                rowh = max(rowh, r.h + p)
            H = y + rowh
            H = max(16, int(math.ceil(H / 16.0)) * 16)
            score = W * H * (1.0 + 0.15 * abs(math.log2(W / H)))
            if best is None or score < best[0]:
                best = (score, W, H, pos)
        _, self.W, self.H, pos = best
        for r in regs:
            r.x, r.y = pos[r.key]
        self._meta()

    def _meta(self):
        """Animated atlases: frames are W x H (not square), so the mcmeta must say so."""
        if self.frames > 1 and self.W is not None:
            REGISTRY[self.path]['mcmeta']['animation']['width'] = self.W
            REGISTRY[self.path]['mcmeta']['animation']['height'] = self.H

    def uv(self, key, sub=None, size=None):
        """UV rect (model units 0..16) of a region; sub = texel sub-rect (a, b, c, d) inside it;
        size = (fw, fh) actual face size in texels (fractional faces use a partial span)."""
        self.pack()
        r = self.regions[key]
        if sub:
            a, b, c, d = sub
            x0, y0, x1, y1 = r.x + a, r.y + b, r.x + c, r.y + d
        elif size:
            x0, y0 = r.x, r.y
            x1, y1 = r.x + size[0], r.y + size[1]
        else:
            x0, y0, x1, y1 = r.x, r.y, r.x + r.w, r.y + r.h
        ku, kv = 16.0 / self.W, 16.0 / self.H
        return [round(x0 * ku, 4), round(y0 * kv, 4), round(x1 * ku, 4), round(y1 * kv, 4)]

    # ---------------------------------------------------------------- painting
    def render(self):
        self.pack()
        self._meta()
        if self.frames > 1:
            return [self._render_frame(i) for i in range(self.frames)]
        return self._render_frame(0)

    def _render_frame(self, fi):
        out = np.zeros((self.H, self.W, 4), np.float32)
        if self.base is not None:
            b = np.asarray(self.base(self.W, self.H) if self.frames == 1 else self.base(self.W, self.H, fi), np.float32)
            out[:b.shape[0], :b.shape[1]] = b
        for r in self.regions.values():
            if r.painter is None:
                continue
            a = np.asarray(r.painter(r.w, r.h) if self.frames == 1 else r.painter(r.w, r.h, fi), np.float32)
            if a.shape[:2] != (r.h, r.w):
                raise ValueError(f'{self.name}:{r.key} painter returned {a.shape[:2]}, expected {(r.h, r.w)}')
            out[r.y:r.y + r.h, r.x:r.x + r.w] = a
        return out


# ------------------------------------------------------------------------------------------------ geometry helpers
def face_dims(fr, to, face):
    dx, dy, dz = to[0] - fr[0], to[1] - fr[1], to[2] - fr[2]
    if face in ('north', 'south'):
        return dx, dy
    if face in ('east', 'west'):
        return dz, dy
    return dx, dz


def glow_data():
    return {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


class Model:
    def __init__(self, name, atlas=None, render_type=None, ao=True, parent='minecraft:block/block', display=None,
                 register=True):
        self.name = name
        self.atlas = atlas
        self.render_type = render_type
        self.ao = ao
        self.parent = parent
        self.display = display
        self.els = []          # (fr, to, faces(list of (face, spec)), rot, shade, name)
        self.extra_tex = {}    # var -> texture ref (external, e.g. a surface texture)
        if register:
            MODELS[name] = self

    def box(self, fr, to, mat=None, faces=None, rot=None, shade=True, only=None, skip=(), glow=(), cull=None,
            uniq=None, name=None):
        fr = [float(v) for v in fr]
        to = [float(v) for v in to]
        faces = dict(faces or {})
        use = only if only is not None else ALL
        out = []
        for f in use:
            if f in skip:
                continue
            spec = faces.get(f, mat)
            if spec is None:
                continue
            fw, fh = face_dims(fr, to, f)
            if fw <= 1e-6 or fh <= 1e-6:
                continue
            c = cull.get(f) if isinstance(cull, dict) else None
            out.append(self._resolve(f, spec, fw, fh, f in glow or glow == 'all', c, uniq))
        self.els.append((fr, to, out, rot, shade, name, uniq))
        return self

    def plane(self, fr, to, spec, both=True, **kw):
        """A zero-thickness plane (one of the extents must be 0); faces on both sides."""
        fr = [float(v) for v in fr]
        to = [float(v) for v in to]
        if abs(to[0] - fr[0]) < 1e-6:
            fs = ('east', 'west')
        elif abs(to[2] - fr[2]) < 1e-6:
            fs = ('north', 'south')
        else:
            fs = ('up', 'down')
        faces = {}
        if isinstance(spec, dict) and set(spec) & set(ALL):
            faces = spec
        else:
            faces = {fs[0]: spec, fs[1]: spec if both else None}
        # planes: face dims use the two non-zero extents
        out = []
        for f in fs:
            s = faces.get(f)
            if s is None:
                continue
            if f in ('north', 'south'):
                fw, fh = to[0] - fr[0], to[1] - fr[1]
            elif f in ('east', 'west'):
                fw, fh = to[2] - fr[2], to[1] - fr[1]
            else:
                fw, fh = to[0] - fr[0], to[2] - fr[2]
            glow = kw.get('glow', ())
            out.append(self._resolve(f, s, abs(fw), abs(fh), f in glow or glow == 'all', None, kw.get('uniq')))
        self.els.append((fr, to, out, kw.get('rot'), kw.get('shade', True), kw.get('name'), kw.get('uniq')))
        return self

    # ---------------------------------------------------------------- face specs
    def _resolve(self, f, spec, fw, fh, glow, cull, uniq):
        """Normalise a face spec now (atlas regions are registered at definition time, never while writing)."""
        d = {'face': f, 'sub': None, 'flip': None, 'rot': 0, 'glow': glow, 'cull': cull}
        if isinstance(spec, dict):
            d['sub'] = spec.get('sub')
            d['flip'] = spec.get('flip')
            d['rot'] = spec.get('rot', 0)
            d['glow'] = glow or spec.get('glow', False)
            d['cull'] = spec.get('cull', cull)
            spec = spec['r']
        if isinstance(spec, tuple) and len(spec) == 2 and isinstance(spec[0], Atlas):
            d['atlas'], d['key'] = spec
        elif isinstance(spec, Mat):
            dims = (fh, fw) if d['rot'] in (90, 270) else (fw, fh)
            d['atlas'] = self.atlas
            d['key'] = self.atlas.face_region(spec, dims[0], dims[1], f, uniq)
            if d['sub'] is None:
                d['sub'] = (0, 0, dims[0], dims[1])
        elif isinstance(spec, str) and spec.startswith('#'):
            d['ref'] = spec
        else:
            d['atlas'], d['key'] = self.atlas, spec
            if spec not in self.atlas.regions:
                raise KeyError(f'{self.name}: region {spec} not in atlas {self.atlas.name}')
        return d

    def _face_json(self, d, tex_vars):
        if 'ref' in d:
            j = {'texture': d['ref'], 'uv': list(d['sub']) if d['sub'] else [0, 0, 16, 16]}
        else:
            atlas = d['atlas']
            if atlas.path not in tex_vars:
                tex_vars[atlas.path] = f't{len(tex_vars)}' if tex_vars else 't'
            uv = atlas.uv(d['key'], sub=d['sub'])
            flip = d['flip']
            if flip in ('h', True):
                uv = [uv[2], uv[1], uv[0], uv[3]]
            elif flip == 'v':
                uv = [uv[0], uv[3], uv[2], uv[1]]
            elif flip == 'hv':
                uv = [uv[2], uv[3], uv[0], uv[1]]
            j = {'texture': '#' + tex_vars[atlas.path], 'uv': uv}
        if d['rot']:
            j['rotation'] = d['rot']
        if d['glow']:
            j['forge_data'] = glow_data()
        if d['cull']:
            j['cullface'] = d['cull']
        return j

    def to_json(self):
        tex_vars = {}
        if self.atlas is not None:
            tex_vars[self.atlas.path] = 't'
        elements = []
        for fr, to, faces, rot, shade, name, uniq in self.els:
            fj = {}
            for d in faces:
                fj[d['face']] = self._face_json(d, tex_vars)
            if not fj:
                continue
            e = {'from': [round(v, 4) for v in fr], 'to': [round(v, 4) for v in to], 'faces': fj}
            if rot:
                axis, angle, origin = rot
                e['rotation'] = {'origin': [round(v, 4) for v in origin], 'axis': axis, 'angle': angle}
            if not shade:
                e['shade'] = False
            if name:
                e['name'] = name
            elements.append(e)
        m = {}
        if self.parent:
            m['parent'] = self.parent
        if self.render_type:
            m['render_type'] = f'minecraft:{self.render_type}'
        if not self.ao:
            m['ambientocclusion'] = False
        texs = {}
        main = self.atlas.ref if self.atlas is not None else None
        for path, var in tex_vars.items():
            texs[var] = f'{NS}:{path}'
        texs.update(self.extra_tex)
        texs['particle'] = self.extra_tex.get('particle', main or next(iter(texs.values())))
        m['textures'] = texs
        if self.display:
            m['display'] = self.display
        m['elements'] = elements
        return m


def child(name, parent, textures=None):
    """A model that only re-parents (identical state variants) or swaps textures."""
    d = {'parent': f'{NS}:block/{parent}'}
    if textures:
        d['textures'] = textures
    MODELS[name] = d
    return d


# ------------------------------------------------------------------------------------------------ transforms
FACE_ROT_X = {'up': 'south', 'south': 'down', 'down': 'north', 'north': 'up', 'east': 'east', 'west': 'west'}   # +90 about x
FACE_ROT_Y = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}     # +90 about y (clockwise from above)
FACE_ROT_Z = {'up': 'west', 'west': 'down', 'down': 'east', 'east': 'up', 'north': 'north', 'south': 'south'}   # +90 about z


def rot90_box(fr, to, faces, axis, origin=(8, 8, 8), turns=1):
    """Rotate an axis-aligned box (and its per-face specs) by turns x 90 degrees about axis through origin.
    x: (x, y, z) -> (x, oy - (z - oz), oz + (y - oy))   [+y goes to +z]
    y: (x, y, z) -> (ox - (z - oz), y, oz + (x - ox))   [+x goes to +z, i.e. north -> east]
    z: (x, y, z) -> (ox - (y - oy), oy + (x - ox), z)   [+x goes to +y]"""
    ox, oy, oz = origin
    pts = [np.array(fr, float), np.array(to, float)]
    fmap = {f: f for f in ALL}
    for _ in range(turns % 4):
        new = []
        for p in pts:
            x, y, z = p
            if axis == 'x':
                new.append(np.array([x, oy - (z - oz), oz + (y - oy)]))
            elif axis == 'y':
                new.append(np.array([ox - (z - oz), y, oz + (x - ox)]))
            else:
                new.append(np.array([ox - (y - oy), oy + (x - ox), z]))
        pts = new
        m = {'x': FACE_ROT_X, 'y': FACE_ROT_Y, 'z': FACE_ROT_Z}[axis]
        fmap = {f: m[g] for f, g in fmap.items()}
    lo = np.minimum(pts[0], pts[1])
    hi = np.maximum(pts[0], pts[1])
    nf = {}
    if faces:
        for f, s in faces.items():
            nf[fmap[f]] = s
    return list(lo), list(hi), nf, fmap


# ------------------------------------------------------------------------------------------------ display presets
def disp(gui=(30, 225, 0), gui_scale=0.625, gui_t=(0, 0, 0), ground_scale=0.25, fixed_scale=0.5, fixed_rot=(0, 0, 0),
         tp_scale=0.375, fp_scale=0.4, head=None):
    d = {
        'gui': {'rotation': list(gui), 'translation': list(gui_t), 'scale': [gui_scale] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [ground_scale] * 3},
        'fixed': {'rotation': list(fixed_rot), 'translation': [0, 0, 0], 'scale': [fixed_scale] * 3},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [tp_scale] * 3},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [fp_scale] * 3},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [fp_scale] * 3},
    }
    if head:
        d['head'] = head
    return d


DISP_FRONT = disp(gui=(15, 200, 0))          # mostly the front (north) face in the inventory
DISP_WALL = disp(gui=(10, 180, 0), gui_scale=0.7, gui_t=(0, 0, 0))     # wall decals / panels: straight on
DISP_FLOOR = disp(gui=(75, 180, 0), gui_scale=0.75)                     # floor decals: from above
DISP_SMALL = disp(gui=(30, 225, 0), gui_scale=0.9, gui_t=(0, 2, 0))     # small table-top things: bigger in the GUI
DISP_TALL = disp(gui=(30, 225, 0), gui_scale=0.5, gui_t=(0, -1.5, 0))   # parts of tall props


# ------------------------------------------------------------------------------------------------ writing
def write_particles(root=MODEL_ROOT):
    import rs_particles
    proot = os.path.join(os.path.dirname(root), 'particles')
    os.makedirs(proot, exist_ok=True)
    out = []
    for pid, d in sorted(rs_particles.PARTICLES.items()):
        p = os.path.join(proot, pid + '.json')
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(d, f, indent=2)
            f.write('\n')
        out.append(p)
    return out


def write_models(root=MODEL_ROOT):
    out = write_particles(root)
    for name in sorted(MODELS):
        m = MODELS[name]
        data = m.to_json() if isinstance(m, Model) else m
        p = os.path.join(root, 'block', name + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=1, ensure_ascii=False)
            f.write('\n')
        out.append(p)
    for name in sorted(ITEM_MODELS):
        m = ITEM_MODELS[name]
        data = m() if callable(m) else m
        p = os.path.join(root, 'item', name + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=1, ensure_ascii=False)
            f.write('\n')
        out.append(p)
    return out
