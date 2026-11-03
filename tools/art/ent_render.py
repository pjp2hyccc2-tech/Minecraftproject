"""Preview rendering of the spirit models (offline, not shipped).

`mc` mode reproduces how the game draws a translucent entity: parts in ModelPart order (children in Java HashMap
order), every polygon in Cube order, no sorting, depth test LEQUAL with depth writes, colour = texel * alpha,
fragments with alpha < 0.1 discarded (rendertype_entity_translucent.fsh), then the eyes layer added (ONE, ONE,
no depth write) at full strength. Seeing the previews this way shows exactly which internal faces leak through
the translucent skin.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

import render3d as R
from ent_model import render_children
from common import font, FONT_LABEL

FACE_ORDER = ('down', 'up', 'west', 'north', 'east', 'south')      # ModelPart.Cube polygon order
DIR_TO_FACE = {'down': 'top', 'up': 'bottom', 'west': 'right', 'north': 'front', 'east': 'left', 'south': 'back'}


class Canvas(R.Canvas):
    """render3d.Canvas plus an immediate 'mc' translucent mode and an optional exposure/fog post."""

    def quad(self, world_pts, uvs, tex, shade=1.0, mode='cutout', tint=None, alpha=1.0, double=True):
        if mode == 'mc':
            pc = R.apply(self.V, world_pts)
            if (-pc[:, 2] < self.near).all():
                return
            self._raster((pc, np.asarray(uvs, np.float64), tex, shade, tint, alpha), 'mc')
            return
        super().quad(world_pts, uvs, tex, shade, mode, tint, alpha, double)

    def _tri(self, s, pc, uv, tex, shade, tint, alpha, mode):
        if mode != 'mc':
            return super()._tri(s, pc, uv, tex, shade, tint, alpha, mode)
        x0, x1 = int(max(0, math.floor(s[:, 0].min()))), int(min(self.w - 1, math.ceil(s[:, 0].max())))
        y0, y1 = int(max(0, math.floor(s[:, 1].min()))), int(min(self.h - 1, math.ceil(s[:, 1].max())))
        if x0 > x1 or y0 > y1:
            return
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xs + 0.5, ys + 0.5
        (ax, ay), (bx, by), (cx, cy) = s[0, :2], s[1, :2], s[2, :2]
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        l0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
        l1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
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
            iz = 1.0 / z
            w_ = l0 * iz[0] + l1 * iz[1] + l2 * iz[2]
            depth = 1 / w_
            u = (l0 * uv[0, 0] * iz[0] + l1 * uv[1, 0] * iz[1] + l2 * uv[2, 0] * iz[2]) / w_
            v = (l0 * uv[0, 1] * iz[0] + l1 * uv[1, 1] * iz[1] + l2 * uv[2, 1] * iz[2]) / w_
        th, tw = tex.shape[:2]
        tu = np.clip(np.floor(u * tw).astype(int), 0, tw - 1)
        tv = np.clip(np.floor(v * th).astype(int), 0, th - 1)
        texel = tex[tv, tu]
        a = texel[..., 3] / 255.0 * alpha
        zb = self.depth[y0:y1 + 1, x0:x1 + 1]
        cb = self.color[y0:y1 + 1, x0:x1 + 1]
        rgb = texel[..., :3] * shade
        if tint is not None:
            rgb = rgb * np.asarray(tint)[None, None, :]
        m = inside & (a >= 0.1) & (depth <= zb + 1e-7)
        cb[m] = cb[m] * (1 - a[m, None]) + rgb[m] * a[m, None]
        zb[m] = depth[m]


def cube_quads(cube, Mp, tex_w, tex_h):
    """Polygons of a model cube in game order, skipping hidden faces: (face, world pts, uvs)."""
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    polys = R.box_polys(ox, oy, oz, w, h, d, cube.uv[0], cube.uv[1], cube.mirror, cube.grow)
    out = []
    by = {p[0]: p for p in polys}
    for dname in FACE_ORDER:
        _, verts, uvs = by[dname]
        if DIR_TO_FACE[dname] not in cube.faces:
            continue
        pts = R.apply(Mp, np.array(verts, float) / 16.0)
        uv = np.array(uvs, float) / np.array([tex_w, tex_h])
        out.append((DIR_TO_FACE[dname], pts, uv))
    return out


def collect_quads(spec, M_world):
    """[(pts, uv)] of the posed model in ModelPart render order (hidden parts skipped)."""
    quads = []

    def rec(p, M):
        Mp = M @ p.matrix()
        if not p.visible:
            return
        if not p.skipDraw:
            for c in p.cubes:
                for (fname, pts, uv) in cube_quads(c, Mp, spec.tex_w, spec.tex_h):
                    quads.append((pts, uv))
        for ch in render_children(p):
            rec(ch, Mp)
    rec(spec.root, M_world)
    return quads


def draw_entity(cv, spec, tex, eyes, M_world, eye_pos, alpha=1.0, eyes_gain=1.0, light=1.0, tint=None, mode='mc',
                sky=None):
    """Draws the posed spec (ModelPart order). light scales the diffuse (lightmap); eyes (if given) are added."""
    quads = collect_quads(spec, M_world)
    for pts, uv in quads:
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        if np.linalg.norm(n) < 1e-12:
            n = np.cross(pts[2] - pts[1], pts[3] - pts[1])
        if np.linalg.norm(n) < 1e-12:
            continue
        # NO_CULL: the visible side of every polygon is lit
        nn = n if np.dot(n, eye_pos - pts.mean(0)) > 0 else -n
        sh = R.entity_shade(nn) * light
        if sky is not None:
            sh = sh * sky(nn / np.linalg.norm(nn), pts.mean(0))
        if mode == 'mc':
            cv.quad(pts, uv, tex, shade=sh, mode='mc', alpha=alpha, tint=tint)
        else:
            cv.quad(pts, uv, tex, shade=sh, mode='cutout', tint=tint)
    if eyes is not None and eyes_gain > 0:
        for pts, uv in quads:
            cv.quad(pts, uv, eyes, shade=1.0, mode='additive', alpha=eyes_gain)
        cv.finish()


def draw_entity_eyes(cv, spec, eyes, M_world, gain=1.0):
    if gain <= 0:
        return
    for pts, uv in collect_quads(spec, M_world):
        cv.quad(pts, uv, eyes, shade=1.0, mode='additive', alpha=gain)
    cv.finish()


# ------------------------------------------------------------------------------------------------ scene props
def flat_tex(rgb, noise=0.0, seed=1, size=16):
    r = np.random.default_rng(seed)
    t = np.zeros((size, size, 4), np.float64)
    t[..., :3] = rgb
    if noise:
        t[..., :3] *= (1 + r.normal(0, noise, (size, size, 1)))
    t[..., 3] = 255
    return t


def plank_tex(seed=3):
    """Dark wood-look vinyl floor (preview only)."""
    r = np.random.default_rng(seed)
    t = np.zeros((32, 32, 4), np.float64)
    base = np.array([92, 64, 44], float)
    for row in range(4):
        tone = 1 + r.normal(0, 0.06)
        for y in range(row * 8, row * 8 + 8):
            for x in range(32):
                t[y, x, :3] = base * tone * (1 + 0.05 * math.sin(x * 0.7 + row) + r.normal(0, 0.02))
        t[row * 8, :, :3] *= 0.7
    t[..., 3] = 255
    return t


def box(cv, a, b, tex, shade=1.0, mode='cutout', light=1.0):
    """An axis-aligned box from a to b (blocks), every face textured with tex (stretched)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    F = {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }
    uv = np.array([(0, 0), (1, 0), (1, 1), (0, 1)], float)
    for f, pts in F.items():
        cv.quad(np.array(pts, float), uv, tex, shade=R.FACE_SHADE[f] * shade * light, mode=mode)


def floor(cv, size=3, y=0.0, light=1.0, tex=None):
    tex = plank_tex() if tex is None else tex
    for gx in range(-size, size):
        for gz in range(-size, size):
            pts = np.array([(gx, y, gz), (gx + 1, y, gz), (gx + 1, y, gz + 1), (gx, y, gz + 1)], float)
            cv.quad(pts, R._uv_corners([0, 0, 16, 16]), tex, shade=light)


# ------------------------------------------------------------------------------------------------ post
def night_post(cv, amb=0.13, flash=True, cone=0.45, strength=1.25, fog=(14, 16, 22), fog_dist=16.0, cool=True):
    h, w = cv.h, cv.w
    ys, xs = np.mgrid[0:h, 0:w]
    r = np.hypot((xs - w / 2) / (h / 2), (ys - h / 2) / (h / 2))
    d = np.where(np.isfinite(cv.depth), cv.depth, 1e3)
    lit = np.full((h, w), amb)
    if flash:
        spot = np.exp(-(r / cone) ** 2) * 1.4 + np.exp(-(r / (cone * 2.2)) ** 2) * 0.45
        lit = lit + spot * strength / (1.0 + (d / 7.0) ** 2)
    tintc = np.array([0.86, 0.92, 1.08]) if cool else np.array([1.0, 0.92, 0.8])
    c = cv.color * lit[..., None] * tintc
    fk = 1 - np.exp(-(d / fog_dist) ** 2)
    fk = np.where(np.isfinite(cv.depth), fk, 1.0)
    cv.color = c * (1 - fk[..., None]) + np.asarray(fog) * fk[..., None]


def finder_post(img, seed=5):
    """Camera finder look: cool, desaturated, vignette, grain (rough stand-in for the shader)."""
    a = img.astype(np.float64) / 255.0
    lum = a @ np.array([0.3, 0.59, 0.11])
    a = a * 0.55 + lum[..., None] * 0.45
    a = a * np.array([0.86, 0.97, 1.1])
    h, w = a.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w]
    r = np.hypot((xs - w / 2) / (w / 2), (ys - h / 2) / (h / 2))
    a *= np.clip(1.15 - r ** 2 * 0.55, 0, 1)[..., None]
    g = np.random.default_rng(seed).normal(0, 0.045, (h, w, 1))
    a = a + g
    return np.clip(a * 255, 0, 255)


# ------------------------------------------------------------------------------------------------ sheets
def sheet(rows, out, bg=(18, 16, 18)):
    f = font(FONT_LABEL, 12)
    W = max(sum(im.shape[1] + 4 for _, im in r[1]) for r in rows) + 8
    H = sum(max(im.shape[0] for _, im in r[1]) + 34 for r in rows) + 8
    img = Image.new('RGB', (W, H), bg)
    d = ImageDraw.Draw(img)
    y = 6
    for label, ims in rows:
        d.text((6, y), label, fill=(230, 220, 205), font=f)
        y += 16
        x = 6
        rh = 0
        for sub, im in ims:
            img.paste(Image.fromarray(np.clip(im, 0, 255).astype(np.uint8)), (x, y))
            d.text((x + 2, y + im.shape[0] + 1), sub, fill=(170, 165, 155), font=f)
            x += im.shape[1] + 4
            rh = max(rh, im.shape[0])
        y += rh + 18
    img.save(out)
    return out
