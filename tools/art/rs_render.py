"""Offline preview renderer for REISATSU props and scenes (not shipped).

LitCanvas extends render3d.Canvas with a normal buffer, an emissive mask and a deferred point-light pass, so props can
be judged both in neutral light and at night lit only by the restaurant's light sources (emissive faces stay bright).
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

import render3d as R
from common import TEX_ROOT, PREVIEW, font, FONT_LABEL

_TEX = {}


def tex(ref):
    """Texture array by reference ('reisatsu:block/x', 'block/x' or 'minecraft:...'). Rendered fresh from the
    generators when registered, else read from disk."""
    name = ref.split(':', 1)[1] if ':' in ref else ref
    if name not in _TEX:
        import gen_textures as G
        if name in G.REGISTRY:
            arr = G.render(name).astype(np.float64)
            spec = G.REGISTRY[name]
            if spec['frames'] > 1:
                fh = arr.shape[0] // spec['frames']
                arr = arr[:fh]                    # first animation frame
        else:
            p = os.path.join(TEX_ROOT, name + '.png')
            if os.path.isfile(p):
                arr = np.asarray(Image.open(p).convert('RGBA'), np.float64)
            else:
                arr = np.full((16, 16, 4), 255.0)
                arr[..., :3] = (255, 0, 255)
        _TEX[name] = arr
    return _TEX[name]


def model_json(name):
    import rs_kit
    m = rs_kit.MODELS.get(name)
    if m is None:
        from common import MODEL_ROOT
        with open(os.path.join(MODEL_ROOT, 'block', name + '.json'), encoding='utf-8') as f:
            return json.load(f)
    if isinstance(m, dict):
        if 'parent' in m and m['parent'].startswith('reisatsu:block/') and 'elements' not in m:
            base = dict(model_json(m['parent'].split('/', 1)[1]))
            if 'textures' in m:
                t = dict(base.get('textures', {}))
                t.update(m['textures'])
                base['textures'] = t
            return base
        return m
    return m.to_json()


class LitCanvas(R.Canvas):
    def __init__(self, *a, **k):
        super().__init__(*a, **k)
        self.normal = np.zeros((self.h, self.w, 3))
        self.emit = np.zeros((self.h, self.w))
        self.albedo = self.color.copy()

    def quad(self, world_pts, uvs, tex_, shade=1.0, mode='cutout', tint=None, alpha=1.0, double=True, emissive=False):
        pc = R.apply(self.V, world_pts)
        if (-pc[:, 2] < self.near).all():
            return
        n = np.cross(world_pts[1] - world_pts[0], world_pts[2] - world_pts[0])
        nn = np.linalg.norm(n)
        n = n / nn if nn > 1e-12 else np.array([0, 1.0, 0])
        item = (pc, np.asarray(uvs, np.float64), tex_, shade, tint, alpha, n, emissive)
        if mode == 'translucent':
            self.trans.append(item)
        elif mode == 'additive':
            self.adds.append(item)
        else:
            self._raster(item, 'cutout')

    def _raster(self, item, mode):
        pc, uvs, tex_, shade, tint, alpha, n, emissive = item
        if not self.ortho and (-pc[:, 2] < self.near).any():
            pc, uvs = self._clip_near(pc, uvs)
            if len(pc) < 3:
                return
        sp = self.project(pc)
        for k in range(1, len(pc) - 1):
            tri = [0, k, k + 1]
            self._tri2(sp[tri], uvs[tri], tex_, shade, tint, alpha, mode, n, emissive)

    def _tri2(self, s, uv, tex_, shade, tint, alpha, mode, n, emissive):
        x0, x1 = int(max(0, math.floor(s[:, 0].min()))), int(min(self.w - 1, math.ceil(s[:, 0].max())))
        y0, y1 = int(max(0, math.floor(s[:, 1].min()))), int(min(self.h - 1, math.ceil(s[:, 1].max())))
        if x0 > x1 or y0 > y1:
            return
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px_, py_ = xs + 0.5, ys + 0.5
        (ax, ay), (bx, by), (cx, cy) = s[0, :2], s[1, :2], s[2, :2]
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        l0 = ((by - cy) * (px_ - cx) + (cx - bx) * (py_ - cy)) / den
        l1 = ((cy - ay) * (px_ - cx) + (ax - cx) * (py_ - cy)) / den
        l2 = 1 - l0 - l1
        inside = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
        if not inside.any():
            return
        z = s[:, 2]
        if self.ortho:
            depth = l0 * z[0] + l1 * z[1] + l2 * z[2]
            u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]
            v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
        else:
            iz = 1 / z
            w_ = l0 * iz[0] + l1 * iz[1] + l2 * iz[2]
            depth = 1 / w_
            u = (l0 * uv[0, 0] * iz[0] + l1 * uv[1, 0] * iz[1] + l2 * uv[2, 0] * iz[2]) / w_
            v = (l0 * uv[0, 1] * iz[0] + l1 * uv[1, 1] * iz[1] + l2 * uv[2, 1] * iz[2]) / w_
        th, tw = tex_.shape[:2]
        tu = np.clip((u * tw).astype(int), 0, tw - 1)
        tv = np.clip((v * th).astype(int), 0, th - 1)
        texel = tex_[tv, tu]
        a = texel[..., 3] / 255.0 * alpha
        zb = self.depth[y0:y1 + 1, x0:x1 + 1]
        cb = self.color[y0:y1 + 1, x0:x1 + 1]
        ab = self.albedo[y0:y1 + 1, x0:x1 + 1]
        nb = self.normal[y0:y1 + 1, x0:x1 + 1]
        eb = self.emit[y0:y1 + 1, x0:x1 + 1]
        rgb = texel[..., :3]
        if tint is not None:
            rgb = rgb * np.asarray(tint)[None, None, :]
        if mode == 'cutout':
            m = inside & (a >= 0.1) & (depth < zb)
            cb[m] = (rgb * (1.0 if emissive else shade))[m]
            ab[m] = rgb[m]
            nb[m] = n
            eb[m] = 1.0 if emissive else 0.0
            zb[m] = depth[m]
        elif mode == 'translucent':
            m = inside & (a > 0.003) & (depth < zb)
            aa = a[m, None]
            cb[m] = cb[m] * (1 - aa) + (rgb * (1.0 if emissive else shade))[m] * aa
            ab[m] = ab[m] * (1 - aa) + rgb[m] * aa
            if emissive:
                eb[m] = np.maximum(eb[m], a[m])
        else:
            m = inside & (depth < zb + 1e-4)
            cb[m] = cb[m] + texel[..., :3][m] * alpha

    # ---------------------------------------------------------------- lighting
    def world_positions(self):
        h, w = self.h, self.w
        ys, xs = np.mgrid[0:h, 0:w]
        d = np.where(np.isfinite(self.depth), self.depth, 1e4)
        if self.ortho:
            s = self.h / 2 / self.ortho
            cx = (xs + 0.5 - w / 2) / s
            cy = -(ys + 0.5 - h / 2) / s
            cz = -d
        else:
            f = (h / 2) / math.tan(math.radians(self.fov) / 2)
            cx = (xs + 0.5 - w / 2) * d / f
            cy = -(ys + 0.5 - h / 2) * d / f
            cz = -d
        pc = np.stack([cx, cy, cz], -1)
        Vi = np.linalg.inv(self.V)
        return pc @ Vi[:3, :3].T + Vi[:3, 3]

    def light(self, lights, ambient=(0.035, 0.04, 0.055), fog=None, exposure=1.0):
        """lights: list of (pos(xyz), color(rgb 0..1), intensity, radius). Emissive pixels keep their albedo."""
        P = self.world_positions()
        N = self.normal
        acc = np.zeros_like(self.color) + np.asarray(ambient) * 255
        acc = np.broadcast_to(np.asarray(ambient), self.color.shape).copy()
        for pos, col, inten, rad in lights:
            L = np.asarray(pos)[None, None, :] - P
            d = np.linalg.norm(L, axis=-1) + 1e-6
            l = L / d[..., None]
            ndl = np.clip((N * l).sum(-1), 0, 1) * 0.85 + 0.15
            att = inten / (1 + (d / rad) ** 2) * np.clip(1 - d / (rad * 4), 0, 1)
            acc += np.asarray(col)[None, None, :] * (ndl * att)[..., None]
        lit = self.albedo / 255.0 * acc
        e = self.emit[..., None]
        out = lit * (1 - e) + (self.albedo / 255.0) * e * 1.05
        out = out * exposure
        out = out / (1 + out * 0.35) * 1.35
        if fog is not None:
            d = np.where(np.isfinite(self.depth), self.depth, 1e3)
            fk = 1 - np.exp(-(d / fog[1]) ** 2)
            out = out * (1 - fk[..., None]) + np.asarray(fog[0]) * fk[..., None]
        bg = ~np.isfinite(self.depth)
        res = np.clip(out * 255, 0, 255)
        res[bg] = self.color[bg] * 0.25
        self.color = res
        return self


def draw_model(cv, model, M=None, emissive_ok=True):
    """Draw a block model JSON dict with a world matrix M (block units)."""
    for pts, uvs, t, face, shade, fd in R.element_quads(model, tex, M):
        s = R.FACE_SHADE.get(face, 1.0) if shade else 1.0
        glow = 'forge_data' in fd and emissive_ok
        rt = model.get('render_type', '')
        mode = 'translucent' if 'translucent' in rt else 'cutout'
        n_alpha_mode = mode
        if mode == 'cutout' and t[..., 3].min() < 250 and 'cutout' not in rt:
            n_alpha_mode = 'cutout'          # solid models ignore alpha in game; keep it visible here
        cv.quad(pts, uvs, t, shade=1.0 if glow else s, mode=n_alpha_mode, emissive=glow)


def place(x, y, z, facing='north'):
    """Matrix for a block model at block (x, y, z) rotated like a blockstate with y rotation for `facing`."""
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}[facing]
    # blockstate y rotation is clockwise seen from above around the block centre
    a = -math.radians(rot)
    Rm = R.rot_y(a)
    c = np.array([0.5, 0.5, 0.5])
    M = R.affine(None, [x, y, z]) @ R.affine(None, c) @ R.affine(Rm) @ R.affine(None, -c)
    return M


def cube(cv, pos, faces, light_shade=True):
    """faces: dict face -> texture ref or array, or a single ref for all."""
    x, y, z = pos
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        t = faces if isinstance(faces, (str, np.ndarray)) else faces.get(f, faces.get('side'))
        if t is None:
            continue
        arr = tex(t) if isinstance(t, str) else t
        pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), f), float) / 16 + [x, y, z]
        uv = R._uv_corners(R._default_uv((0, 0, 0), (16, 16, 16), f))
        cv.quad(pts, uv, arr, shade=R.FACE_SHADE[f], double=False)


def slab_quad(cv, pos, t, y0=0.0, face='up', size=(1, 1)):
    x, y, z = pos
    sx, sz = size
    pts = np.array([(0, y0, 0), (sx, y0, 0), (sx, y0, sz), (0, y0, sz)], float) + [x, y, z]
    cv.quad(pts, R._uv_corners([0, 0, 16, 16]), tex(t) if isinstance(t, str) else t, shade=1.0)


def orbit_eye(target, yaw, pitch, dist):
    t = np.asarray(target, float)
    return t + dist * np.array([math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)),
                                math.sin(math.radians(pitch)),
                                -math.cos(math.radians(yaw)) * math.cos(math.radians(pitch))])


SUPPORT = {}     # prop name -> model drawn in the block below (table / counter) in previews


def render_prop(name, yaw=-35.0, pitch=26.0, size=224, floor='reisatsu:block/dining_floor', zoom=1.0, extent=None,
                bg=(46, 48, 54)):
    m = model_json(name)
    lo = np.array([0, 0, 0.0])
    hi = np.array([1, 1, 1.0])
    for el in m.get('elements', []):
        lo = np.minimum(lo, np.array(el['from']) / 16)
        hi = np.maximum(hi, np.array(el['to']) / 16)
    sup = SUPPORT.get(name)
    if sup is None and lo[1] < -0.01:
        sup = 'restaurant_table'
    if sup:
        lo = np.array([lo[0], -1.0, lo[2]])
        hi = np.array([hi[0], max(hi[1], 0.5), hi[2]])
    c = (lo + hi) / 2
    ext = extent or max(0.95, float(np.max(hi - lo)) * 0.62)
    eye = orbit_eye(c, yaw, pitch, 4.0)
    cv = LitCanvas(size, size, R.look_at(eye, c), ortho=ext / zoom, bg=bg)
    fy = -1.0 if sup else 0.0
    if floor:
        ft = tex(floor) * np.array([0.6, 0.6, 0.6, 1.0])
        for gx in range(int(math.floor(lo[0])) - 1, int(math.ceil(hi[0])) + 1):
            for gz in range(int(math.floor(lo[2])) - 1, int(math.ceil(hi[2])) + 1):
                pts = np.array([(gx, fy, gz), (gx + 1, fy, gz), (gx + 1, fy, gz + 1), (gx, fy, gz + 1)], float)
                cv.quad(pts, R._uv_corners([0, 0, 16, 16]), ft, shade=1.0)
    if sup:
        draw_model(cv, model_json(sup), place(0, -1, 0))
    draw_model(cv, m)
    cv.finish()
    return np.clip(cv.color, 0, 255)


def sheet(items, out, cols=6, cell=224, title=None):
    f = font(FONT_LABEL, 11)
    rows = (len(items) + cols - 1) // cols
    top = 18 if title else 0
    W, H = cols * (cell + 6) + 6, top + rows * (cell + 18) + 6
    img = Image.new('RGB', (W, H), (18, 16, 18))
    d = ImageDraw.Draw(img)
    if title:
        d.text((6, 2), title, fill=(220, 210, 200), font=f)
    for i, (label, arr) in enumerate(items):
        r, c = divmod(i, cols)
        x, y = 6 + c * (cell + 6), top + 6 + r * (cell + 18)
        img.paste(Image.fromarray(arr.astype(np.uint8)), (x, y))
        d.text((x, y + cell + 2), label[:34], fill=(220, 210, 200), font=f)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    return out


def props_sheet(names, out, views=((-35, 26),), cols=6, cell=224, title=None):
    items = []
    for n in names:
        for (yaw, pitch) in views:
            items.append((n if len(views) == 1 else f'{n} {yaw}', render_prop(n, yaw, pitch, size=cell)))
    return sheet(items, out, cols=cols, cell=cell, title=title)
