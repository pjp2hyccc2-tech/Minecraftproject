"""Armor toolkit: humanoid armor base models, face painting helpers, UV packing, preview renderer.

Built on tools/hcmodel (Model/Bone/Cube, materials, software renderer). Coordinates follow Minecraft's
HumanoidModel: +Y down, model faces -Z, entity right = -X. Base parts keep the vanilla armor UV layout
(head 0,0 / body 16,16 / arm 40,16 / leg 0,16) inside a 128x64 texture; decoration cubes are packed into
the free area (x >= 64, or y >= 32).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from hcmodel.model import Model  # noqa: E402
from hcmodel import paint as P  # noqa: E402
from hcmodel import render as R  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
import platepaint as PP  # noqa: E402

TW, TH = 128, 64
HD = 4            # HD texel multiplier of the painted textures (1 = legacy pixel art); set by armor.py
YMIN, YSPAN = -10.0, 34.0
FACES = ('top', 'bottom', 'west', 'north', 'east', 'south')


def hexc(h):
    return P.hexrgb(h)


# ----------------------------------------------------------------------------- base model
class ArmorModel:
    """One armor layer (outer: helm/mail/greaves, inner: coil)."""

    def __init__(self, name, inner):
        self.name = name
        self.inner = inner
        m = Model(name, TW, TH)
        self.m = m
        self.head = m.bone('head', (0, 0, 0))
        self.hat = m.bone('hat', (0, 0, 0))
        self.body = m.bone('body', (0, 0, 0))
        self.rarm = m.bone('right_arm', (-5, 2, 0))
        self.larm = m.bone('left_arm', (5, 2, 0))
        self.rleg = m.bone('right_leg', (-1.9, 12, 0))
        self.lleg = m.bone('left_leg', (1.9, 12, 0))
        self.base = {}
        if not inner:
            self.base['head'] = self.head.cube((-4, -8, -4), (8, 8, 8), 'default', inflate=1.0, uv=(0, 0))
            self.base['body'] = self.body.cube((-4, 0, -2), (8, 12, 4), 'default', inflate=1.0, uv=(16, 16))
            self.base['arm'] = self.rarm.cube((-3, -2, -2), (4, 12, 4), 'default', inflate=1.0, uv=(40, 16))
            self.larm.cube((-1, -2, -2), (4, 12, 4), 'default', inflate=1.0, mirror_of=self.base['arm'])
            self.base['leg'] = self.rleg.cube((-2, 0, -2), (4, 12, 4), 'default', inflate=0.9, uv=(0, 16))
            self.lleg.cube((-2, 0, -2), (4, 12, 4), 'default', inflate=0.9, mirror_of=self.base['leg'])
        else:
            self.base['body'] = self.body.cube((-4, 0, -2), (8, 12, 4), 'default', inflate=0.5, uv=(16, 16))
            self.base['leg'] = self.rleg.cube((-2, 0, -2), (4, 12, 4), 'default', inflate=0.4, uv=(0, 16))
            self.lleg.cube((-2, 0, -2), (4, 12, 4), 'default', inflate=0.4, mirror_of=self.base['leg'])
        self.base_cubes = set(id(c) for c in m.all_cubes())
        self.img = None
        self.glow = None

    def mirror(self, parent_bone, bone):
        """Mirror a decoration bone subtree from the right (-X) side to the left: name *_r -> *_l."""
        return mirror_tree(parent_bone, bone, '_r', '_l')

    # ------------------------------------------------------------------ packing
    def pack(self):
        cubes = [c for c in self.m.all_cubes() if c.uv is None and c.mirror_of is None]
        def foot(c):
            dx, dy, dz = (int(math.ceil(v - 1e-6)) for v in c.size)
            return 2 * (dz + dx), dz + dy
        cubes.sort(key=lambda c: (foot(c)[1], foot(c)[0]), reverse=True)
        sky = [32] * 64 + [0] * (TW - 64)
        for c in cubes:
            fw, fh = foot(c)
            rw, rh = fw + 1, fh + 1
            best_x, best_y = None, None
            for x in range(0, TW - rw + 1):
                y = max(sky[x:x + rw])
                if best_y is None or y < best_y:
                    best_x, best_y = x, y
            if best_y + rh > TH + 1:
                raise ValueError('armor texture overflow in %s (cube %s)' % (self.name, c.size))
            c.uv = (best_x, best_y)
            for x in range(best_x, best_x + rw):
                sky[x] = best_y + rh
        for c in self.m.all_cubes():
            if c.mirror_of is not None:
                c.uv = c.mirror_of.uv

    # ------------------------------------------------------------------ painting
    def paint(self, base_painter):
        """Paints the 1x pixel-art texture.  With HD > 1 (module setting) an HD texture is painted alongside:
        plates are repainted in high detail (platepaint.HDContext) and every other pixel operation (studs,
        lines, cut-outs, recolours...) is replayed onto it with rounded, anti-aliased shapes; the HD images
        are returned instead and the LabPBR maps are kept in self.pbr_maps."""
        self.pack()
        a = np.zeros((TH, TW, 4), float)
        g = np.zeros((TH, TW, 4), float)
        self.hd = PP.HDContext(TW, TH, HD) if HD > 1 else None
        self.img, self.glow = a, g
        self._shadow, self._gshadow = a.copy(), g.copy()
        base_ids = set(id(c) for c in self.base.values())
        for c in self.m.all_cubes():
            if c.mirror_of is None and id(c) not in base_ids:
                PP.paint_cube(a, g, c, hd=self.hd)
        self.sync()
        base_painter(self)
        self.flush()
        self.pbr_maps = None
        if self.hd is not None:
            out, gl, self.pbr_maps = self.hd.images()
            return out, gl
        out = Image.fromarray(np.clip(self.img, 0, 255).astype(np.uint8), 'RGBA')
        gl = None
        if (self.glow[..., 3] > 0).any():
            gg = self.glow.copy()
            gg[gg[..., 3] == 0] = 0
            gl = Image.fromarray(np.clip(gg, 0, 255).astype(np.uint8), 'RGBA')
        return out, gl

    def sync(self):
        """The 1x buffers now match what the HD buffers show."""
        self._shadow, self._gshadow = self.img.copy(), self.glow.copy()

    def flush(self):
        """Replays 1x pixel edits made since the last sync onto the HD buffers (no-op without HD)."""
        if self.hd is None:
            return
        from hcmodel.hdup import apply_changes
        if np.array_equal(self.img, self._shadow) and np.array_equal(self.glow, self._gshadow):
            return
        apply_changes(self.hd.img, self.hd.glow, self._shadow, self.img, self._gshadow, self.glow, HD)
        self.sync()

    def face(self, part, face):
        return FaceCanvas(self, self.base[part], face)

    def json(self):
        return {'texture_width': TW, 'texture_height': TH, 'parts': [r.to_json() for r in self.m.roots]}


# ----------------------------------------------------------------------------- face canvas
class FaceCanvas:
    """Painting helper for one face of a base cube. Local coords: x = column, y = row (0 = top)."""

    def __init__(self, am, cube, face):
        self.am = am
        self.cube = cube
        self.face = face
        g = P.face_grid(cube, face)
        self.u0, self.v0, self.w, self.h, self.I, self.J, pts, n, self.f, self.d, self.r = g
        mrest = cube.bone.rest_matrix()
        self.P = (np.concatenate([pts, np.ones(pts.shape[:2] + (1,))], axis=-1) @ mrest.T)[..., :3]
        N = mrest[:3, :3] @ n
        self.N = N / (np.linalg.norm(N) + 1e-9)
        self.seed = (hash((cube.bone.name, face)) & 0xFFF)

    @property
    def reg(self):
        return self.am.img[self.v0:self.v0 + self.h, self.u0:self.u0 + self.w]

    @property
    def greg(self):
        return self.am.glow[self.v0:self.v0 + self.h, self.u0:self.u0 + self.w]

    def mask(self, x0=0, y0=0, x1=None, y1=None):
        x1 = self.w - 1 if x1 is None else x1
        y1 = self.h - 1 if y1 is None else y1
        m = np.zeros((self.h, self.w), bool)
        m[max(0, y0):max(0, y1 + 1), max(0, x0):max(0, x1 + 1)] = True
        return m

    def all(self):
        return np.ones((self.h, self.w), bool)

    def rows(self, y0, y1):
        return self.mask(0, y0, self.w - 1, y1)

    def shade(self, mat, seed=0):
        m = P.MATERIALS[mat]
        rgb, gl = P._shade(m, self.I, self.J, self.w, self.h, self.P, self.N, self.f, self.d, self.r, YMIN, YSPAN, self.seed + seed, self.cube)
        return rgb, gl

    def fill(self, mat, mask=None, seed=0, glow=False):
        if mask is None:
            mask = self.all()
        rgb, gl = self.shade(mat, seed)
        reg = self.reg
        reg[mask, :3] = rgb[mask]
        reg[mask, 3] = 255
        greg = self.greg
        if glow:
            greg[mask, :3] = rgb[mask]
            greg[mask, 3] = 255
        elif gl is not None:
            gm = mask & (gl[..., 3] > 0)
            greg[gm] = gl[gm]
        return self

    def plate(self, style, mask=None, seed=0, offset=(0, 0)):
        """Beveled plate in a 5-level style (see platepaint). Bevel follows the mask outline."""
        st = PP.STYLES[style]
        self.am.flush()
        PP.paint_region(self.am.img, self.am.glow, self.u0, self.v0, self.w, self.h, st, self.face,
                        self.seed + seed, mask=mask, offset=offset)
        if self.am.hd is not None:
            self.am.hd.plate(self.u0, self.v0, self.w, self.h, st, self.face, self.seed + seed, mask=mask, offset=offset)
            self.am.sync()
        return self

    def ring(self, mask, col, inside_face=True):
        """Colours the 1px ring around mask (4-neighbourhood) - outlines raised plates."""
        m = mask
        r = np.zeros_like(m)
        r[1:, :] |= m[:-1, :]
        r[:-1, :] |= m[1:, :]
        r[:, 1:] |= m[:, :-1]
        r[:, :-1] |= m[:, 1:]
        r &= ~m
        return self.color(col, r)

    def color(self, col, mask, glow=None):
        c = hexc(col) if isinstance(col, str) else np.array(col, float)
        reg = self.reg
        reg[mask, :3] = c
        reg[mask, 3] = 255
        if glow is not None:
            gc = c if glow is True else (hexc(glow) if isinstance(glow, str) else np.array(glow, float))
            self.greg[mask, :3] = gc
            self.greg[mask, 3] = 255
        return self

    def px(self, x, y, col, glow=None):
        if 0 <= x < self.w and 0 <= y < self.h:
            m = np.zeros((self.h, self.w), bool)
            m[y, x] = True
            self.color(col, m, glow)
        return self

    def clear(self, mask=None):
        if mask is None:
            mask = self.all()
        self.reg[mask] = 0
        self.greg[mask] = 0
        return self

    def darken(self, mask, k=0.7):
        reg = self.reg
        sel = mask & (reg[..., 3] > 0)
        reg[sel, :3] *= k
        return self

    def lighten(self, mask, k=1.25, add=10):
        reg = self.reg
        sel = mask & (reg[..., 3] > 0)
        reg[sel, :3] = np.clip(reg[sel, :3] * k + add, 0, 255)
        return self

    def hline(self, y, x0, x1, col, glow=None):
        return self.color(col, self.mask(x0, y, x1, y), glow)

    def vline(self, x, y0, y1, col, glow=None):
        return self.color(col, self.mask(x, y0, x, y1), glow)

    def border(self, col, sides='tblr', y0=0, y1=None):
        y1 = self.h - 1 if y1 is None else y1
        m = np.zeros((self.h, self.w), bool)
        if 't' in sides:
            m[y0, :] = True
        if 'b' in sides:
            m[y1, :] = True
        if 'l' in sides:
            m[y0:y1 + 1, 0] = True
        if 'r' in sides:
            m[y0:y1 + 1, self.w - 1] = True
        return self.color(col, m)

    def outline_mask(self, mask):
        """Pixels of mask that touch a non-mask pixel (4-neighbourhood) or the face edge."""
        m = mask
        e = np.zeros_like(m)
        e[1:, :] |= m[1:, :] & ~m[:-1, :]
        e[:-1, :] |= m[:-1, :] & ~m[1:, :]
        e[:, 1:] |= m[:, 1:] & ~m[:, :-1]
        e[:, :-1] |= m[:, :-1] & ~m[:, 1:]
        return e

    def checker(self, mask, parity=0):
        return mask & (((self.I + self.J) % 2) == parity)


# ----------------------------------------------------------------------------- preview
def skin_model():
    """Plain player model (4px arms) with a simple generated skin, for previews."""
    m = Model('steve', 64, 64)
    head = m.bone('head', (0, 0, 0))
    body = m.bone('body', (0, 0, 0))
    ra = m.bone('right_arm', (-5, 2, 0))
    la = m.bone('left_arm', (5, 2, 0))
    rl = m.bone('right_leg', (-1.9, 12, 0))
    ll = m.bone('left_leg', (1.9, 12, 0))
    head.cube((-4, -8, -4), (8, 8, 8), uv=(0, 0))
    body.cube((-4, 0, -2), (8, 12, 4), uv=(16, 16))
    ra.cube((-3, -2, -2), (4, 12, 4), uv=(40, 16))
    la.cube((-1, -2, -2), (4, 12, 4), uv=(32, 48))
    rl.cube((-2, 0, -2), (4, 12, 4), uv=(0, 16))
    ll.cube((-2, 0, -2), (4, 12, 4), uv=(16, 48))
    img = np.zeros((64, 64, 4), float)
    skin, hair, shirt, pants, shoe = hexc('#c99a74'), hexc('#3b2616'), hexc('#2f8c8c'), hexc('#2f3a7a'), hexc('#3a3a3a')

    def rect(x, y, w, h, c):
        img[y:y + h, x:x + w, :3] = c
        img[y:y + h, x:x + w, 3] = 255

    rect(0, 0, 32, 16, skin)
    rect(8, 0, 8, 8, hair)
    rect(0, 8, 32, 2, hair)
    rect(24, 8, 8, 6, hair)
    rect(9, 12, 2, 1, hexc('#ffffff'))
    rect(13, 12, 2, 1, hexc('#ffffff'))
    rect(10, 12, 1, 1, hexc('#3050a0'))
    rect(13, 12, 1, 1, hexc('#3050a0'))
    rect(11, 14, 2, 1, hexc('#8a5a40'))
    rect(16, 16, 24, 16, shirt)
    rect(40, 16, 16, 16, skin)
    rect(40, 20, 16, 4, shirt)
    rect(32, 48, 16, 16, skin)
    rect(32, 52, 16, 4, shirt)
    rect(0, 16, 16, 16, pants)
    rect(0, 28, 16, 4, shoe)
    rect(16, 48, 16, 16, pants)
    rect(16, 60, 16, 4, shoe)
    return m, Image.fromarray(img.astype(np.uint8), 'RGBA')


def _set_pose(model, pose):
    saved = {}
    for name, rot in pose.items():
        b = model.bones.get(name)
        if b is not None:
            saved[name] = b.rot
            b.rot = tuple(float(v) for v in rot)
    return saved


def _restore(model, saved):
    for name, rot in saved.items():
        model.bones[name].rot = rot


def rasterize_multi(layers, size, yaw, pitch, fit=None, bg=(46, 50, 58)):
    """layers: list of (quads, texture PIL). Shared z-buffer (adapted from hcmodel.render.rasterize)."""
    W = H = size
    img = np.zeros((H, W, 3), float)
    img[:] = bg
    zbuf = np.full((H, W), np.inf)
    V = R.view_matrix(yaw, pitch)
    prepared = []
    allp = []
    for quads, tex in layers:
        t = np.asarray(tex.convert('RGBA'), dtype=float)
        tris = []
        for Pq, uv, n in quads:
            Wp = R.to_world(Pq)
            nw = np.array([n[0], -n[1], -n[2]])
            nl = np.linalg.norm(nw)
            nw = nw / nl if nl > 0 else nw
            bright = min(1.0, 0.45 + 0.55 * (max(0, nw @ R.L0) + max(0, nw @ R.L1)))
            S = Wp @ V.T
            allp.append(S)
            tris.append((S, uv, bright, -n))
        prepared.append((tris, t))
    pts = np.concatenate(allp)
    if fit is None:
        mn, mx = pts[:, :2].min(0), pts[:, :2].max(0)
        span = max(mx - mn) * 1.08 + 1e-6
        fit = ((mn + mx) / 2, span)
    center, span = fit
    sc = W / span
    for tris, tex in prepared:
        TH_, TW_ = tex.shape[:2]
        for S, uv, bright, _ in tris:
            Q = np.stack([(S[:, 0] - center[0]) * sc + W / 2, -(S[:, 1] - center[1]) * sc + H / 2, S[:, 2]], axis=-1)
            for (a, b, c) in ((0, 1, 2), (0, 2, 3)):
                p0, p1, p2 = Q[a], Q[b], Q[c]
                area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p1[1] - p0[1]) * (p2[0] - p0[0])
                if abs(area) < 1e-9:
                    continue
                xmin = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
                xmax = min(W - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
                ymin = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
                ymax = min(H - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
                if xmin > xmax or ymin > ymax:
                    continue
                xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
                w0 = ((p1[0] - xs) * (p2[1] - ys) - (p1[1] - ys) * (p2[0] - xs)) / area
                w1 = ((p2[0] - xs) * (p0[1] - ys) - (p2[1] - ys) * (p0[0] - xs)) / area
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not inside.any():
                    continue
                z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
                u = w0 * uv[a][0] + w1 * uv[b][0] + w2 * uv[c][0]
                v = w0 * uv[a][1] + w1 * uv[b][1] + w2 * uv[c][1]
                tx = np.clip((u * TW_).astype(int), 0, TW_ - 1)
                ty = np.clip((v * TH_).astype(int), 0, TH_ - 1)
                col = tex[ty, tx]
                ok = inside & (col[..., 3] > 25)
                sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
                ok &= z < sub - 1e-4
                if not ok.any():
                    continue
                sub[ok] = z[ok]
                region = img[ymin:ymax + 1, xmin:xmax + 1]
                region[ok] = col[ok][:, :3] * bright
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), fit


POSES = {
    'stand': {'right_arm': (0, 0, 6), 'left_arm': (0, 0, -6)},
    'walk': {'right_arm': (-35, 0, 6), 'left_arm': (35, 0, -6), 'right_leg': (30, 0, 0), 'left_leg': (-30, 0, 0), 'head': (-8, 20, 0)},
}


def preview(armor_layers, out_path, size=330, with_body=True, views=None):
    """armor_layers: list of (ArmorModel, texture PIL). Writes a sheet with several views."""
    sm, stex = skin_model()
    views = views or [('front-left', 35, 14, 'stand'), ('front', 0, 6, 'stand'), ('back-left', 145, 22, 'stand'),
                      ('right', -70, 8, 'walk')]
    tiles = []
    fit = None
    for label, yaw, pitch, pose in views:
        layers = []
        models = ([sm] if with_body else []) + [a.m for a, _ in armor_layers]
        saved = [_set_pose(m, POSES[pose]) for m in models]
        if with_body:
            layers.append((R.model_quads(sm, R.pose_matrices(sm)), stex))
        for a, tex in armor_layers:
            layers.append((R.model_quads(a.m, R.pose_matrices(a.m)), tex))
        im, f = rasterize_multi(layers, size, yaw, pitch, fit)
        if fit is None:
            c, s = f
            fit = (c, s * 1.05)
            im, _ = rasterize_multi(layers, size, yaw, pitch, fit)
        for m, sv in zip(models, saved):
            _restore(m, sv)
        tiles.append(R.label(im, label))
    sheet = Image.new('RGB', (size * len(tiles), size), (20, 22, 26))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * size, 0))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


def overview(entries, out_path, size=250):
    """entries: list of (name, [(ArmorModel, tex), ...]). Grid with front-left + back views per set."""
    tiles = []
    for name, layers in entries:
        for label, yaw, pitch in (('fl', 35, 12), ('back', 160, 14)):
            sm, stex = skin_model()
            ls = [(R.model_quads(sm, R.pose_matrices(sm)), stex)]
            models = [sm] + [a.m for a, _ in layers]
            saved = [_set_pose(m, POSES['stand']) for m in models]
            ls = [(R.model_quads(sm, R.pose_matrices(sm)), stex)] + [(R.model_quads(a.m, R.pose_matrices(a.m)), t) for a, t in layers]
            im, f = rasterize_multi(ls, size, yaw, pitch, None)
            c, s = f
            im, _ = rasterize_multi(ls, size, yaw, pitch, (c, s * 1.04))
            for m, sv in zip(models, saved):
                _restore(m, sv)
            tiles.append(R.label(im, name + ' ' + label))
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * size, rows * size), (20, 22, 26))
    for k, t in enumerate(tiles):
        sheet.paste(t, ((k % cols) * size, (k // cols) * size))
    sheet.save(out_path)
