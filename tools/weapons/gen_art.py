#!/usr/bin/env python3
"""
Procedural pixel art for the HuntCraft weapon module.

Writes 32x32 icons for every weapon (8 classes x 7 lines, + drawn bow variants), 16x16 icons for the whetstone and
the bow coatings, the arrow entity texture, and the item model JSONs (handheld, display transforms computed so that
big weapons look big while the grip stays in the hand).

  python3 tools/weapons/gen_art.py [--preview]

Previews (8x contact sheets) go to tools/preview/weapons_*.png.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
TEX_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft', 'textures', 'item', 'weapon')
MODEL_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft', 'models', 'item')
PREVIEW_DIR = os.path.join(ROOT, 'tools', 'preview')

CLASSES = ['great_sword', 'long_sword', 'sword_and_shield', 'dual_blades', 'hammer', 'lance', 'hunting_horn', 'bow']
LINES = ['iron', 'bone', 'ignivald', 'frostgarm', 'voltrix', 'gravadon', 'azragos']


def hx(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def ramp(*cols):
    return [hx(c) for c in cols]


# ------------------------------------------------------------------------------------------------ palettes
PAL = {
    'iron': dict(
        blade=ramp('#2f3238', '#4b5058', '#6c727c', '#959ca6', '#c4cad2', '#eef2f6'),
        metal=ramp('#2a2420', '#4a4038', '#6e6252', '#968a74', '#bcb09a', '#e0d6c4'),
        grip=ramp('#24160c', '#3c2614', '#5a3a1e', '#7a522c', '#9a6c3c', '#b88a52'),
        accent=ramp('#5a5e66', '#8a909a', '#c0c6ce', '#f0f4f8'),
        gem=ramp('#3a3e46', '#6a707a', '#a0a6ae', '#e0e4ea'),
        outline=hx('#15161a'),
    ),
    'bone': dict(
        blade=ramp('#5a4a32', '#8a7650', '#b4a078', '#d4c49c', '#ece0be', '#fff8e6'),
        metal=ramp('#3a2a1a', '#5c4428', '#7e6038', '#a07e4c', '#c09c64', '#dcbc84'),
        grip=ramp('#3a2410', '#5e3a1a', '#7e5226', '#9c6a34', '#b88446', '#d4a05c'),
        accent=ramp('#4a1a10', '#7a2a18', '#a84228', '#d06a40'),
        gem=ramp('#5a4a32', '#b4a078', '#ece0be', '#fff8e6'),
        outline=hx('#24180c'),
    ),
    'ignivald': dict(
        blade=ramp('#3a0a06', '#6e140a', '#a3220e', '#d8401a', '#f5762e', '#ffb060'),
        metal=ramp('#220806', '#46140c', '#702414', '#9c381c', '#c85428', '#ec8040'),
        grip=ramp('#1c0c08', '#301610', '#48201a', '#602c22', '#7a3a2c', '#944a38'),
        accent=ramp('#b02408', '#ec5410', '#ff961e', '#ffd040', '#fff6a0'),
        gem=ramp('#4a0010', '#900824', '#d8183c', '#ff6080', '#ffd0dc'),
        outline=hx('#1c0402'),
    ),
    'frostgarm': dict(
        blade=ramp('#123a5a', '#1e5a84', '#3a86b4', '#6cb4dc', '#a8dcf4', '#ecfcff'),
        metal=ramp('#4a5664', '#727e8c', '#9ca8b6', '#c4ced8', '#e2e9f0', '#ffffff'),
        grip=ramp('#0e1626', '#182640', '#24385a', '#344e74', '#46668e', '#5e80aa'),
        accent=ramp('#2a6a9a', '#4aa4d4', '#86d8f6', '#c8f4ff', '#ffffff'),
        gem=ramp('#08345e', '#1462a0', '#38a0e4', '#8adcff', '#e0ffff'),
        outline=hx('#081624'),
    ),
    'voltrix': dict(
        blade=ramp('#100c24', '#1e1840', '#2e2660', '#463a86', '#6454a8', '#8e7ece'),
        metal=ramp('#0e0c18', '#1c1830', '#2c2648', '#3e3660', '#544a7c', '#6c5e98'),
        grip=ramp('#121018', '#201c2c', '#302a40', '#423a56', '#544a6c', '#665a82'),
        accent=ramp('#8a6a00', '#c8a000', '#f2d21b', '#ffee60', '#fffcc8'),
        gem=ramp('#5a4a00', '#a08a00', '#f2d21b', '#ffee80', '#ffffe8'),
        outline=hx('#07060f'),
    ),
    'gravadon': dict(
        blade=ramp('#2a2018', '#463a2c', '#64563f', '#857659', '#a89a7c', '#cbbfa2'),
        metal=ramp('#1e1a16', '#36302a', '#4e463c', '#685e50', '#847866', '#a09480'),
        grip=ramp('#1c140c', '#2e2214', '#42321e', '#58442a', '#6e5636', '#866a44'),
        accent=ramp('#0c3a6a', '#1a60aa', '#3d8bff', '#80bcff', '#d0e8ff'),
        gem=ramp('#0a3a4a', '#106a7a', '#20a0b0', '#60d8e0', '#c8fcff'),
        outline=hx('#120e0a'),
    ),
    'azragos': dict(
        blade=ramp('#08060c', '#141020', '#221a34', '#32284a', '#463a62', '#5e4e80'),
        metal=ramp('#0a0608', '#1a0e14', '#2c1620', '#42202e', '#5c2c40', '#7a3a56'),
        grip=ramp('#0c0a10', '#18141e', '#241e2c', '#30283a', '#3c3248', '#4a3e58'),
        accent=ramp('#4a0a6a', '#8a1aaa', '#b04cff', '#d890ff', '#f4dcff'),
        gem=ramp('#3a0a5a', '#6a1a9a', '#a040e0', '#d090ff', '#f8e4ff'),
        crimson=ramp('#5a0814', '#a0102a', '#ff3a6a', '#ff9cb4'),
        outline=hx('#040306'),
    ),
}


def pick(r, t):
    """Discrete ramp lookup: t in [0,1] -> colour (arrays supported)."""
    n = len(r)
    idx = np.clip(np.floor(np.asarray(t) * n), 0, n - 1).astype(int)
    arr = np.stack(r)
    return arr[idx]


def hash01(x, y, seed=0):
    v = np.sin(x * 12.9898 + y * 78.233 + seed * 37.719) * 43758.5453
    return v - np.floor(v)


# ------------------------------------------------------------------------------------------------ canvas
class Canvas:
    def __init__(self, n=32):
        self.n = n
        yy, xx = np.mgrid[0:n, 0:n]
        self.px = xx + 0.5
        self.py = yy + 0.5
        self.ix = xx
        self.iy = yy
        self.rgb = np.zeros((n, n, 3))
        self.alpha = np.zeros((n, n), bool)
        self.solid = np.zeros((n, n), bool)  # pixels that get an outline

    def frame(self, start, end):
        sx, sy = start
        ex, ey = end
        dx, dy = ex - sx, ey - sy
        length = math.hypot(dx, dy)
        dx, dy = dx / length, dy / length
        nx, ny = dy, -dx  # left normal (up-left for a bottom-left -> top-right axis)
        rx, ry = self.px - sx, self.py - sy
        u = rx * dx + ry * dy
        v = rx * nx + ry * ny
        return u, v, length

    def point(self, start, end, u, v):
        sx, sy = start
        ex, ey = end
        dx, dy = ex - sx, ey - sy
        length = math.hypot(dx, dy)
        dx, dy = dx / length, dy / length
        nx, ny = dy, -dx
        return sx + dx * u + nx * v, sy + dy * u + ny * v

    def paint(self, mask, colors, outline=True):
        mask = np.asarray(mask, bool)
        if np.ndim(colors) == 1:
            colors = np.broadcast_to(colors, (self.n, self.n, 3))
        self.rgb[mask] = colors[mask]
        self.alpha |= mask
        if outline:
            self.solid |= mask

    def dot(self, x, y, col, outline=False):
        x, y = int(round(x - 0.5)), int(round(y - 0.5))
        if 0 <= x < self.n and 0 <= y < self.n:
            self.rgb[y, x] = col
            self.alpha[y, x] = True
            if outline:
                self.solid[y, x] = True

    def disc(self, cx, cy, r):
        return (self.px - cx) ** 2 + (self.py - cy) ** 2 <= r * r

    def outline(self, col, inner_dark=None):
        s = self.solid
        nb = np.zeros_like(s)
        nb[1:, :] |= s[:-1, :]
        nb[:-1, :] |= s[1:, :]
        nb[:, 1:] |= s[:, :-1]
        nb[:, :-1] |= s[:, 1:]
        edge = nb & ~self.alpha
        self.rgb[edge] = col
        self.alpha |= edge

    def image(self):
        out = np.zeros((self.n, self.n, 4), np.uint8)
        out[..., :3] = np.clip(self.rgb, 0, 255).astype(np.uint8)
        out[..., 3] = np.where(self.alpha, 255, 0)
        return Image.fromarray(out, 'RGBA')


def erode(m):
    e = m.copy()
    e[1:, :] &= m[:-1, :]
    e[:-1, :] &= m[1:, :]
    e[:, 1:] &= m[:, :-1]
    e[:, :-1] &= m[:, 1:]
    return e


def border(m):
    return m & ~erode(m)


# ------------------------------------------------------------------------------------------------ shading helpers
def shade_band(t_v, base=0.5, slope=0.38):
    """t_v in [-1,1] (positive = up-left side) -> brightness 0..1 (light from the top-left)."""
    return np.clip(base + slope * t_v, 0, 0.999)


def blade_colors(pal, t_v, u, fuller=None):
    b = shade_band(t_v, 0.52, 0.30) + 0.05 * np.sin(u * 0.45)
    cols = pick(pal, np.clip(b, 0.18, 0.80))
    hi = t_v > 0.62
    cols = np.where(hi[..., None], pal[-1], cols)
    lo = t_v < -0.66
    cols = np.where(lo[..., None], pal[1], cols)
    if fuller is not None:
        cols = np.where(fuller[..., None], pal[1], cols)
    return cols


def wrap_colors(pal, t_v, u, period=2.0):
    band = (np.floor(u / period) % 2) == 0
    b = shade_band(t_v, 0.45, 0.35)
    cols = pick(pal, np.where(band, b, b * 0.6))
    return cols


# ------------------------------------------------------------------------------------------------ shapes
def stroke(c, start, end, u0, u1, hw, curve=None):
    """Mask of a straight (or curved) bar along the axis with half-width hw (float or function of u)."""
    u, v, _ = c.frame(start, end)
    cv = curve(u) if curve else 0.0
    w = hw(u) if callable(hw) else hw
    t = (v - cv) / np.maximum(w, 1e-3)
    m = (u >= u0) & (u <= u1) & (np.abs(v - cv) <= w)
    return m, u, v, t


def grip(c, pal, start, end, u0, u1, hw=1.15, period=2.0):
    m, u, v, t = stroke(c, start, end, u0, u1, hw)
    c.paint(m, wrap_colors(pal['grip'], t, u, period))
    return m


def pommel(c, pal, start, end, u, r=1.6, ramp_key='metal'):
    x, y = c.point(start, end, u, 0)
    m = c.disc(x, y, r)
    tv = ((c.px - x) * -0.7 + (c.py - y) * -0.7) / max(r, 0.5)
    c.paint(m, pick(pal[ramp_key], shade_band(tv, 0.5, 0.4)))
    return m


def gem(c, pal, x, y, r=1.4):
    m = c.disc(x, y, r)
    tv = ((c.px - x) * -0.7 + (c.py - y) * -0.7) / max(r, 0.5)
    c.paint(m, pick(pal['gem'], shade_band(tv, 0.45, 0.45)))
    c.dot(x - 0.4, y - 0.4, pal['gem'][-1])
    return m


def guard_bar(c, pal, start, end, u0, u1, half, ramp_key='metal', rounded=True):
    u, v, _ = c.frame(start, end)
    m = (u >= u0) & (u <= u1) & (np.abs(v) <= half)
    if rounded:
        m &= ~(((u < u0 + 0.8) | (u > u1 - 0.8)) & (np.abs(v) > half - 0.8))
    tv = v / max(half, 1) * 0.5 + (u - (u0 + u1) / 2) / max(u1 - u0, 1) * 0.8
    c.paint(m, pick(pal[ramp_key], shade_band(tv, 0.55, 0.45)))
    c.paint(border(m), pal[ramp_key][1])
    return m


# ------------------------------------------------------------------------------------------------ ornaments
def ornament_blade(c, line, pal, start, end, u0, u1, hw, curve=None, seed=0, back_only=False):
    """Monster-line decoration on a blade-like part (u0..u1, half width hw(u))."""
    u, v, _ = c.frame(start, end)
    cv = curve(u) if curve else 0.0
    w = hw(u) if callable(hw) else hw + 0 * u
    rel = v - cv
    inside = (u >= u0) & (u <= u1) & (np.abs(rel) <= w)
    if line == 'iron':
        # rivets on the blade base
        for k in range(2):
            x, y = c.point(start, end, u0 + 1.5 + k * 2.2, 0)
            c.dot(x, y, pal['accent'][3])
    elif line == 'bone':
        # knobs along the back edge and cracks
        for k, uu in enumerate(np.arange(u0 + 2, u1 - 2, 3.4)):
            wk = float(hw(uu) if callable(hw) else hw)
            x, y = c.point(start, end, uu, (curve(uu) if curve else 0) + wk + 0.4)
            m = c.disc(x, y, 1.15)
            c.paint(m & ~c.alpha | m & inside, pick(pal['blade'], np.full(m.shape, 0.72)))
        crack = inside & (np.abs(rel + 0.3 * np.sin(u * 1.3 + seed)) < 0.35) & (hash01(c.ix, c.iy, seed) > 0.35) & (u > u0 + 3)
        c.paint(crack, pal['blade'][1])
        spots = inside & (hash01(c.ix, c.iy, seed + 5) > 0.88)
        c.paint(spots, pal['blade'][2])
    elif line == 'ignivald':
        # scale crescents along the spine and flame tongues on the back edge
        scale = inside & (rel > 0) & (rel < w * 0.65) & (((u * 0.9 + rel * 1.1) % 3.0) < 0.9) & (u > u0 + 1)
        c.paint(scale, pal['metal'][1])
        for k, uu in enumerate(np.arange(u0 + 2.5, u1 - 1.5, 4.0)):
            wk = float(hw(uu) if callable(hw) else hw)
            base = (curve(uu) if curve else 0) + wk
            h = 2.6 + 0.8 * ((k + seed) % 2)
            uu2 = u - uu
            vv = v - base
            flame = (vv >= -0.6) & (vv <= h) & (np.abs(uu2 + vv * 0.45) <= (1.4 * (1 - np.clip(vv, 0, h) / h) + 0.25))
            tt = np.clip(vv / h, 0, 1)
            c.paint(flame & ~inside | flame & (vv > 0), pick(pal['accent'], 1 - tt * 0.95), outline=False)
        edge = inside & (rel < -w + 0.9)
        c.paint(edge, pal['accent'][2])
    elif line == 'frostgarm':
        # ice crystal shards along the back edge
        for k, uu in enumerate(np.arange(u0 + 2.0, u1 - 1.0, 3.6)):
            wk = float(hw(uu) if callable(hw) else hw)
            base = (curve(uu) if curve else 0) + wk - 0.3
            h = 2.2 + 1.2 * ((k + seed) % 2)
            uu2 = u - uu
            vv = v - base
            shard = (vv >= 0) & (vv <= h) & (np.abs(uu2 - vv * 0.35) <= 0.9 * (1 - vv / h) + 0.15)
            c.paint(shard, pick(pal['accent'], 0.45 + 0.55 * np.clip(vv / h, 0, 1)), outline=True)
        glint = inside & (np.abs(rel - w * 0.3) < 0.4) & (((u + seed) % 5) < 1.4)
        c.paint(glint, pal['blade'][-1])
        frost = inside & (hash01(c.ix, c.iy, seed + 3) > 0.9)
        c.paint(frost, pal['accent'][3])
    elif line == 'voltrix':
        # lightning zigzag through the blade + serrated shell plates
        zz = rel - (np.abs(((u * 0.36 + seed) % 2.0) - 1.0) - 0.5) * w * 1.1
        bolt = inside & (np.abs(zz) < 0.75) & (u > u0 + 1) & (u < u1 - 0.5)
        c.paint(bolt, pal['accent'][2])
        core = inside & (np.abs(zz) < 0.32) & (u > u0 + 1) & (u < u1 - 0.5)
        c.paint(core, pal['accent'][4])
        for k, uu in enumerate(np.arange(u0 + 2.0, u1 - 1.0, 3.0)):
            wk = float(hw(uu) if callable(hw) else hw)
            base = (curve(uu) if curve else 0) + wk
            uu2 = u - uu
            vv = v - base
            tooth = (vv >= -0.3) & (vv <= 1.6) & (uu2 >= -1.2) & (uu2 <= 1.2 - vv * 1.1)
            c.paint(tooth, pick(pal['metal'], np.full(tooth.shape, 0.7)))
    elif line == 'gravadon':
        # rocky cracks glowing with water
        crack = inside & (np.abs(rel - 0.45 * w * np.sin(u * 0.8 + seed)) < 0.45) & (u > u0 + 1.5)
        c.paint(crack, pal['accent'][2])
        crack2 = inside & (np.abs(rel + 0.35 * w * np.cos(u * 1.1 + seed)) < 0.35) & (hash01(np.floor(u), 0, seed) > 0.5)
        c.paint(crack2, pal['accent'][1])
        pebble = inside & (hash01(c.ix, c.iy, seed + 9) > 0.86)
        c.paint(pebble, pal['blade'][1])
        for k, uu in enumerate(np.arange(u0 + 3.0, u1 - 2.0, 5.0)):
            wk = float(hw(uu) if callable(hw) else hw)
            x, y = c.point(start, end, uu, (curve(uu) if curve else 0) + wk + 0.3)
            m = c.disc(x, y, 1.3 + 0.3 * (k % 2))
            c.paint(m, pick(pal['metal'], np.full(m.shape, 0.6)))
    elif line == 'azragos':
        # horn spikes on the back, glowing runes along the center
        for k, uu in enumerate(np.arange(u0 + 3.0, u1 - 1.0, 4.2)):
            wk = float(hw(uu) if callable(hw) else hw)
            base = (curve(uu) if curve else 0) + wk - 0.2
            h = 2.8
            uu2 = u - uu
            vv = v - base
            horn = (vv >= 0) & (vv <= h) & (np.abs(uu2 + (vv / h) ** 2 * 1.6) <= 1.1 * (1 - vv / h) + 0.1)
            c.paint(horn, pick(pal['metal'], 0.35 + 0.5 * np.clip(vv / h, 0, 1)))
        glyph = inside & (np.abs(rel) < 0.5) & (((u + seed) % 3.0) < 1.6) & (u > u0 + 2) & (u < u1 - 1)
        c.paint(glyph, pal['accent'][2])
        glyph2 = inside & (np.abs(rel) < 0.2) & (((u + seed) % 3.0) < 0.8) & (u > u0 + 2) & (u < u1 - 1)
        c.paint(glyph2, pal['accent'][4])
        edge = inside & (rel < -w + 0.8)
        c.paint(edge, pal['crimson'][2])


def ornament_head(c, line, pal, cx, cy, size, seed=0):
    """Decoration for compact parts (hammer heads, horn bells, shields)."""
    if line == 'iron':
        for dx, dy in ((-1, -1), (1, 1), (1, -1), (-1, 1)):
            c.dot(cx + dx * size * 0.45, cy + dy * size * 0.45, pal['accent'][3])
    elif line == 'bone':
        m = c.alpha & c.disc(cx, cy, size * 0.9) & (hash01(c.ix, c.iy, seed + 1) > 0.82)
        c.paint(m, pal['blade'][1], outline=False)
    elif line == 'ignivald':
        for k in range(3):
            ang = -2.4 + k * 0.55
            x = cx + math.cos(ang) * size * 0.95
            y = cy + math.sin(ang) * size * 0.95
            m = c.disc(x, y, 1.2) | c.disc(x + math.cos(ang) * 1.2, y + math.sin(ang) * 1.2, 0.7)
            c.paint(m, pal['accent'][2 + k % 2], outline=False)
        gem(c, pal, cx, cy, 1.5)
    elif line == 'frostgarm':
        for k in range(3):
            ang = -2.2 + k * 0.6
            x0, y0 = cx + math.cos(ang) * size * 0.6, cy + math.sin(ang) * size * 0.6
            for s in range(3):
                c.dot(x0 + math.cos(ang) * s, y0 + math.sin(ang) * s, pal['accent'][2 + min(2, s)], outline=True)
        gem(c, pal, cx, cy, 1.4)
    elif line == 'voltrix':
        x, y = cx - size * 0.4, cy - size * 0.2
        for s in range(5):
            c.dot(x + s * 0.9, y + (1 if s % 2 else -1) * 0.8 + s * 0.4, pal['accent'][3])
        gem(c, pal, cx + size * 0.2, cy + size * 0.1, 1.3)
    elif line == 'gravadon':
        m = c.alpha & c.disc(cx, cy, size) & (np.abs((c.px - cx) * 0.6 + (c.py - cy) * 0.8 - 0.5 * np.sin(c.px)) < 0.45)
        c.paint(m, pal['accent'][2], outline=False)
        gem(c, pal, cx + 0.5, cy + 0.5, 1.2)
    elif line == 'azragos':
        m = c.alpha & c.disc(cx, cy, size * 0.9) & (np.abs(((c.px + c.py + seed) % 4.0) - 2.0) < 0.35)
        c.paint(m, pal['accent'][2], outline=False)
        gem(c, pal, cx, cy, 1.4)


# ------------------------------------------------------------------------------------------------ weapon types
DIAG = ((2.5, 29.5), (29.5, 2.5))


def draw_great_sword(c, line, pal):
    s, e = DIAG
    grip(c, pal, s, e, 1.0, 8.8, 1.15)
    pommel(c, pal, s, e, 1.2, 1.8)
    u, v, _ = c.frame(s, e)
    b0, tip0, tip1 = 10.2, 31.0, 37.8
    hw = 3.9
    wob = 0.0
    if line == 'gravadon':
        wob = 0.5 * np.sin(u * 1.7) * (u > 12)
    elif line == 'bone':
        wob = 0.35 * np.sin(u * 0.9)
    lower = -hw - wob * 0.6
    upper = np.where(u < tip0, hw + wob, hw - (u - tip0) * (2 * hw - 0.8) / (tip1 - tip0))
    m = (u >= b0) & (u <= tip1) & (v >= lower) & (v <= upper)
    if line == 'bone':
        m &= ~((v < lower + 1.1) & ((u % 2.6) < 1.0) & (u > b0 + 2))
    if line in ('voltrix', 'azragos'):
        m &= ~((v < lower + 0.9) & ((u % 3.0) < 0.9) & (u > b0 + 2) & (u < tip0))
    t = (v - (upper + lower) / 2) / np.maximum((upper - lower) / 2, 0.5)
    fuller = (np.abs(t + 0.1) < 0.14) & (u > b0 + 2.5) & (u < tip0 - 2)
    cols = blade_colors(pal['blade'], np.clip(t, -1, 1), u, fuller)
    ricasso = (u < b0 + 2.0)
    cols = np.where(ricasso[..., None], pick(pal['blade'], shade_band(t, 0.38, 0.3)), cols)
    c.paint(m, cols)
    if line == 'bone':
        for uu in (13.5, 21.0):
            strap = m & (np.abs(u - uu) < 0.9)
            c.paint(strap, pick(pal['grip'], shade_band(t, 0.45, 0.35)))
    if line == 'iron':
        shine = m & (np.abs(t - 0.45) < 0.12) & (u > b0 + 3) & (u < tip0)
        c.paint(shine, pal['blade'][5])
    guard_bar(c, pal, s, e, 8.4, 11.0, 5.9)
    hwf = lambda uu: np.where(uu < tip0, hw, np.maximum(0.6, hw - (uu - tip0) * 1.1))
    ornament_blade(c, line, pal, s, e, 11.5, 35.5, hwf, seed=1)
    x, y = c.point(s, e, 9.7, 0)
    if line in ('ignivald', 'frostgarm', 'voltrix', 'gravadon', 'azragos'):
        gem(c, pal, x, y, 1.3)
    else:
        c.dot(x, y, pal['accent'][-1] if line == 'iron' else pal['metal'][4])
    if line == 'frostgarm':
        fur(c, pal, s, e, 7.6, 3.4)


def fur(c, pal, s, e, u_at, r):
    x, y = c.point(s, e, u_at, 0)
    m = c.disc(x, y, r) & (hash01(c.ix, c.iy, 4) > 0.15)
    tv = ((c.px - x) * -0.7 + (c.py - y) * -0.7) / r
    c.paint(m, pick(pal['metal'], shade_band(tv + 0.3 * (hash01(c.ix, c.iy, 7) - 0.5), 0.6, 0.4)))


def draw_long_sword(c, line, pal):
    s, e = (3.0, 29.0), (29.6, 2.4)
    curve = lambda u: -0.0045 * (u - 10.0) ** 2 * (u > 10) + 0.0
    grip(c, pal, s, e, 0.5, 9.5, 1.0, period=1.5)
    pommel(c, pal, s, e, 0.6, 1.2)
    tip0 = 33.5
    hw = lambda u: np.where(u < tip0, 1.55, 1.55 * np.clip((37.2 - u) / (37.2 - tip0), 0, 1))
    m, u, v, t = stroke(c, s, e, 10.8, 37.2, hw, curve)
    # hamon: the cutting edge (down-right side) is lighter, the spine darker
    cols = blade_colors(pal['blade'], t, u)
    hamon = (t < -0.25) & (t > -0.75 + 0.2 * np.sin(u * 1.4))
    cols = np.where(hamon[..., None], pal['blade'][4], cols)
    c.paint(m, cols)
    # round tsuba
    x, y = c.point(s, e, 10.0, 0)
    tsuba = c.disc(x, y, 2.9)
    tv = ((c.px - x) * -0.7 + (c.py - y) * -0.7) / 2.9
    c.paint(tsuba, pick(pal['metal'], shade_band(tv, 0.32, 0.3)))
    c.paint(border(tsuba), pick(pal['metal'], shade_band(tv, 0.62, 0.35)))
    habaki = (np.abs(stroke(c, s, e, 10.6, 12.0, 1.7)[0]))
    c.paint(habaki, pal['accent'][-2] if line != 'iron' else pal['metal'][4])
    ornament_blade(c, line, pal, s, e, 12.0, 35.5, hw, curve, seed=2)


def draw_sword_and_shield(c, line, pal):
    # shield behind (lower right)
    cx, cy = 21.0, 21.5
    r = 8.2
    if line in ('bone', 'gravadon'):
        sm = c.disc(cx, cy, r)
    else:
        sm = (np.abs(c.px - cx) / 7.6 + np.maximum(0, c.py - cy) / 9.0 <= 1.0) & (c.py >= cy - 7.4) & (np.abs(c.px - cx) <= 7.6)
    tv = ((c.px - cx) * -0.6 + (c.py - cy) * -0.8) / r
    c.paint(sm, pick(pal['metal'], shade_band(tv, 0.5, 0.4)))
    rim = sm & ~(c.disc(cx, cy, r - 1.4) if line in ('bone', 'gravadon') else
                 ((np.abs(c.px - cx) / 6.2 + np.maximum(0, c.py - cy) / 7.6 <= 1.0) & (c.py >= cy - 6.0) & (np.abs(c.px - cx) <= 6.2)))
    c.paint(rim, pick(pal['blade'], shade_band(tv, 0.55, 0.4)))
    ornament_head(c, line, pal, cx, cy - 0.5, 4.5, seed=3)
    # sword on top
    s, e = (2.5, 26.5), (25.5, 3.5)
    grip(c, pal, s, e, 0.8, 6.5, 1.0)
    pommel(c, pal, s, e, 0.9, 1.4)
    tip0 = 27.5
    hw = lambda u: np.where(u < tip0, 2.05 - 0.02 * (u - 8), 1.9 * np.clip((31.6 - u) / (31.6 - tip0), 0, 1))
    m, u, v, t = stroke(c, s, e, 7.6, 31.6, hw)
    c.paint(m, blade_colors(pal['blade'], t, u, (np.abs(t) < 0.2) & (u > 9) & (u < 25)))
    guard_bar(c, pal, s, e, 6.2, 8.0, 3.6)
    ornament_blade(c, line, pal, s, e, 8.6, 29.5, hw, seed=4)


def draw_dual_blades(c, line, pal):
    # back blade (mirrored: bottom-right -> top-left)
    for idx, (s, e) in enumerate((((28.5, 28.0), (5.0, 4.5)), ((3.5, 28.0), (27.0, 4.5)))):
        grip(c, pal, s, e, 0.6, 5.8, 1.0, period=1.5)
        pommel(c, pal, s, e, 0.6, 1.2)
        tip0 = 24.0
        if line in ('ignivald', 'azragos', 'voltrix'):
            hw = lambda u: np.where(u < tip0, 1.7 + 0.5 * np.clip((u - 8) / 14.0, 0, 1), 2.2 * np.clip((30.5 - u) / (30.5 - tip0), 0, 1))
        else:
            hw = lambda u: np.where(u < tip0, 1.75, 1.75 * np.clip((30.5 - u) / (30.5 - tip0), 0, 1))
        m, u, v, t = stroke(c, s, e, 6.8, 30.5, hw)
        cols = blade_colors(pal['blade'], t, u)
        if idx == 0:
            cols = cols * 0.82
        c.paint(m, cols)
        guard_bar(c, pal, s, e, 5.4, 7.0, 2.8)
        ornament_blade(c, line, pal, s, e, 7.5, 28.5, hw, seed=5 + idx)


def draw_hammer(c, line, pal):
    s, e = (3.0, 29.0), (27.5, 4.5)
    grip(c, pal, s, e, 0.5, 26.0, 1.05, period=2.5)
    pommel(c, pal, s, e, 0.8, 1.5)
    # head: block perpendicular to the shaft
    u, v, _ = c.frame(s, e)
    hu0, hu1 = 23.5, 33.5
    hv = 7.2
    if line in ('bone', 'gravadon'):
        head = (u >= hu0) & (u <= hu1) & (np.abs(v) <= hv + 0.9 * np.sin(u * 2.1 + v * 1.3))
    else:
        head = (u >= hu0) & (u <= hu1) & (np.abs(v) <= hv)
        head &= ~(((u < hu0 + 1.2) | (u > hu1 - 1.2)) & (np.abs(v) > hv - 1.2))
    tv = (u - (hu0 + hu1) / 2) / (hu1 - hu0) * 1.1 + v / hv * 0.45
    cols = pick(pal['blade'], shade_band(tv, 0.48, 0.42))
    face = (np.abs(v) > hv - 1.6) & head
    cols = np.where(face[..., None], pick(pal['metal'], shade_band(tv, 0.5, 0.4)), cols)
    c.paint(head, cols)
    band = head & (np.abs(u - (hu0 + hu1) / 2) < 1.0)
    c.paint(band, pick(pal['metal'], shade_band(tv, 0.4, 0.4)))
    cx, cy = c.point(s, e, (hu0 + hu1) / 2, 0)
    ornament_head(c, line, pal, cx, cy, 4.2, seed=6)
    if line == 'ignivald':
        for side in (-1, 1):
            for k in range(3):
                x, y = c.point(s, e, hu0 + 2 + k * 3, side * (hv + 1.0))
                c.paint(c.disc(x, y, 0.9), pal['accent'][3], outline=False)
    if line == 'frostgarm':
        for side in (-1, 1):
            x, y = c.point(s, e, hu1 + 0.5, side * (hv - 1.0))
            m = c.disc(x, y, 1.2)
            c.paint(m, pal['accent'][3])
    if line == 'azragos':
        for side in (-1, 1):
            for k in range(2):
                x, y = c.point(s, e, hu0 + 2.5 + k * 4.5, side * (hv + 1.2))
                c.paint(c.disc(x, y, 1.0), pal['metal'][3])
    if line == 'voltrix':
        for k in range(4):
            x, y = c.point(s, e, hu1 + 1.0, -hv + 1.5 + k * 3.8)
            c.dot(x, y, pal['accent'][3 + k % 2])


def draw_lance(c, line, pal):
    # tower shield behind (lower right corner)
    sx0, sy0, sx1, sy1 = 16.0, 17.0, 29.0, 31.0
    shield = (c.px >= sx0) & (c.px <= sx1) & (c.py >= sy0) & (c.py <= sy1 - np.maximum(0, (np.abs(c.px - 22.5) - 3) * 0.6))
    tv = ((c.px - 22.5) * -0.5 + (c.py - 24) * -0.5) / 8
    c.paint(shield, pick(pal['metal'], shade_band(tv, 0.48, 0.38)))
    rim = shield & ~((c.px >= sx0 + 1.2) & (c.px <= sx1 - 1.2) & (c.py >= sy0 + 1.2) & (c.py <= sy1 - 1.4 - np.maximum(0, (np.abs(c.px - 22.5) - 3) * 0.6)))
    c.paint(rim, pick(pal['blade'], shade_band(tv, 0.6, 0.35)))
    ornament_head(c, line, pal, 22.5, 23.5, 4.0, seed=7)
    # lance: long cone
    s, e = (3.0, 29.0), (30.0, 2.0)
    grip(c, pal, s, e, 0.5, 6.5, 1.05)
    pommel(c, pal, s, e, 0.6, 1.3)
    # vamplate (hand guard cone)
    u, v, _ = c.frame(s, e)
    vamp = (u >= 5.6) & (u <= 11.0) & (np.abs(v) <= 1.2 + (u - 5.6) * 0.95)
    tvv = v / 5.5 * 0.6 + (u - 8) / 5 * 0.6
    c.paint(vamp, pick(pal['metal'], shade_band(tvv, 0.55, 0.45)))
    c.paint(border(vamp), pal['metal'][1])
    hw = lambda u: np.clip(2.6 * (38.0 - u) / (38.0 - 10.5), 0, 2.6)
    m, u2, v2, t = stroke(c, s, e, 10.5, 38.0, hw)
    c.paint(m, blade_colors(pal['blade'], t, u2))
    ornament_blade(c, line, pal, s, e, 11.0, 33.0, hw, seed=8)


def draw_hunting_horn(c, line, pal):
    s, e = (3.0, 29.0), (28.5, 3.5)
    grip(c, pal, s, e, 0.5, 15.0, 1.1, period=2.0)
    pommel(c, pal, s, e, 0.8, 1.6)
    u, v, L = c.frame(s, e)
    u0, u1 = 13.5, 34.0
    k = np.clip((u - u0) / (u1 - u0), 0, 1)
    cen = 2.0 * np.sin(k * np.pi) - 0.3
    w = 1.7 + 1.1 * np.sin(k * np.pi * 0.85) + 6.2 * k ** 5.0
    body = (u >= u0) & (u <= u1) & (np.abs(v - cen) <= w)
    t = (v - cen) / np.maximum(w, 0.5)
    cols = pick(pal['blade'], shade_band(t * 0.8 + (k - 0.5) * 0.4, 0.5, 0.4))
    c.paint(body, cols)
    # bell opening: ellipse cap at the end, bright rim and dark throat
    wend = 1.7 + 1.1 * math.sin(math.pi * 0.85) + 6.2
    cend = 2.0 * math.sin(math.pi) - 0.3
    ex = (u - (u1 - 0.2)) / 2.2
    ey = (v - cend) / wend
    cap = (ex ** 2 + ey ** 2 <= 1.0)
    c.paint(cap, pick(pal['metal'], shade_band(-ey * 0.6 + 0.2, 0.55, 0.4)))
    throat = ((u - (u1 + 0.1)) / 1.4) ** 2 + ((v - cend) / (wend - 1.5)) ** 2 <= 1.0
    c.paint(throat, pick([pal['blade'][0], pal['blade'][1]], np.clip(0.5 - ey * 0.6, 0, 0.99)))
    # rings
    for uu in (17.0, 21.0, 25.0):
        ring = body & (np.abs(u - uu) < 0.65)
        c.paint(ring, pick(pal['metal'], shade_band(t, 0.5, 0.4)))
        c.paint(ring & (t > 0.4), pal['metal'][5])
    # valves / keys on the lower side
    for uu in (19.0, 23.5):
        kk = (uu - u0) / (u1 - u0)
        cc = 2.0 * math.sin(kk * math.pi) - 0.3
        ww = 1.7 + 1.1 * math.sin(kk * math.pi * 0.85) + 6.2 * kk ** 5.0
        key = (np.abs(u - uu) <= 0.8) & (v <= cc - ww + 0.2) & (v >= cc - ww - 1.8)
        c.paint(key, pick(pal['metal'], np.full(key.shape, 0.65)))
    # mouthpiece pipe curling over the top side
    pipe = (u >= 14.0) & (u <= 24.0) & (np.abs(v - (cen + w + 0.9 + 0.6 * np.sin((u - 14) * 0.5))) <= 0.7)
    c.paint(pipe, pick(pal['metal'], np.full(pipe.shape, 0.75)))
    cx, cy = c.point(s, e, 24.5, 1.0)
    ornament_head(c, line, pal, cx, cy, 3.0, seed=9)
    ornament_blade(c, line, pal, s, e, 15.0, 28.0, lambda uu: 1.7 + 1.1 * np.sin(np.clip((uu - u0) / (u1 - u0), 0, 1) * np.pi * 0.85),
                   lambda uu: 2.0 * np.sin(np.clip((uu - u0) / (u1 - u0), 0, 1) * np.pi) - 0.3, seed=12, back_only=True)


def draw_bow(c, line, pal, drawn=False):
    # string from top-left to bottom-right, limbs bulging toward the top-right (forward)
    s, e = (4.0, 4.0), (28.0, 28.0)
    u, v, L = c.frame(s, e)
    L = float(L)
    vb = -2.0
    amp = 8.6 if not drawn else 9.6
    cen = lambda uu: vb + amp * np.sin(np.pi * np.clip(uu / L, 0, 1))
    thick = lambda uu: 0.85 + 0.85 * np.sin(np.pi * np.clip(uu / L, 0, 1))
    if line in ('ignivald', 'voltrix', 'azragos'):
        thick = lambda uu: 0.95 + 1.0 * np.sin(np.pi * np.clip(uu / L, 0, 1)) ** 2
    limb = (u >= 0.0) & (u <= L) & (np.abs(v - cen(u)) <= thick(u))
    t = (v - cen(u)) / np.maximum(thick(u), 0.3)
    c.paint(limb, pick(pal['blade'], shade_band(t, 0.5, 0.35)))
    # grip in the middle
    gripm = limb & (np.abs(u - L / 2) < 2.6)
    c.paint(gripm, wrap_colors(pal['grip'], t, u, 1.3))
    # limb tips
    for uu in (0.6, L - 0.6):
        x, y = c.point(s, e, uu, cen(np.array(uu)) + 0.2)
        c.paint(c.disc(x, y, 1.1), pal['metal'][3])
    # ornaments along the limbs
    if line != 'iron':
        ornament_blade(c, line, pal, s, e, 2.0, L / 2 - 3.0, thick, cen, seed=10)
        ornament_blade(c, line, pal, s, e, L / 2 + 3.0, L - 2.0, thick, cen, seed=11)
    # string
    if not drawn:
        st = (u >= 0.4) & (u <= L - 0.4) & (np.abs(v - vb) <= 0.42)
        c.paint(st, hx('#e8e4d8'), outline=False)
    else:
        pull = -5.2
        sv = vb + (pull - vb) * (1 - np.abs(u - L / 2) / (L / 2))
        st = (u >= 0.4) & (u <= L - 0.4) & (np.abs(v - sv) <= 0.48)
        c.paint(st, hx('#e8e4d8'), outline=False)
        # arrow pointing forward (top-right)
        shaft = (np.abs(u - L / 2) <= 0.5) & (v >= pull - 0.5) & (v <= vb + amp + 4.5)
        c.paint(shaft, hx('#a07a4a'))
        head = (np.abs(u - L / 2) <= 1.6 * np.clip((vb + amp + 7.0 - v) / 2.6, 0, 1)) & (v >= vb + amp + 4.0) & (v <= vb + amp + 7.0)
        c.paint(head, pick(pal['accent'] if line != 'iron' else pal['blade'], np.full(head.shape, 0.75)))
        fl = (np.abs(u - L / 2) <= 1.4) & (v >= pull - 0.3) & (v <= pull + 1.8) & (np.abs(u - L / 2) >= 0.5)
        c.paint(fl, hx('#d8d0c0'))


DRAW = {
    'great_sword': draw_great_sword, 'long_sword': draw_long_sword, 'sword_and_shield': draw_sword_and_shield,
    'dual_blades': draw_dual_blades, 'hammer': draw_hammer, 'lance': draw_lance, 'hunting_horn': draw_hunting_horn,
    'bow': draw_bow,
}


def make_icon(cls, line, **kw):
    c = Canvas(32)
    pal = PAL[line]
    DRAW[cls](c, line, pal, **kw)
    c.outline(pal['outline'])
    return c.image()


# ------------------------------------------------------------------------------------------------ small items
def make_whetstone():
    c = Canvas(16)
    # rounded rectangular stone, diagonal
    s, e = (3.0, 12.5), (13.0, 3.5)
    u, v, L = c.frame(s, e)
    m = (u >= 0) & (u <= L) & (np.abs(v) <= 3.0) & ~(((u < 1.2) | (u > L - 1.2)) & (np.abs(v) > 1.8))
    t = v / 3.0
    pal = ramp('#3a3a40', '#565862', '#747886', '#9498a6', '#b8bcc8', '#dce0e8')
    cols = pick(pal, shade_band(t, 0.5, 0.42))
    grain = m & (hash01(c.ix, c.iy, 3) > 0.8)
    c.paint(m, cols)
    c.paint(grain, pal[1])
    top = m & (t > 0.55)
    c.paint(top, pal[4])
    # leather wrap band
    band = m & (np.abs(u - L * 0.3) < 1.0)
    c.paint(band, hx('#7a4a24'))
    c.outline(hx('#16161a'))
    return c.image()


def make_coating(rgb_light, rgb_dark):
    c = Canvas(16)
    # small vial: round body + neck + cork
    body = c.disc(8.0, 10.0, 4.6)
    neck = (c.px >= 6.5) & (c.px <= 9.5) & (c.py >= 3.5) & (c.py <= 6.5)
    cork = (c.px >= 6.0) & (c.px <= 10.0) & (c.py >= 1.5) & (c.py <= 3.6)
    glass = ramp('#5a6a7a', '#8aa0b0', '#c0d4e0', '#eef8ff')
    c.paint(neck, pick(glass, np.full(neck.shape, 0.6)))
    c.paint(cork, pick(ramp('#4a2c14', '#6e4422', '#8e5a30', '#ae7444'), shade_band((8 - c.px) / 2, 0.5, 0.4)))
    tv = ((c.px - 8) * -0.6 + (c.py - 10) * -0.8) / 4.6
    liquid = body & (c.py >= 7.6)
    c.paint(body, pick(glass, shade_band(tv, 0.55, 0.35)))
    lc = [hx(rgb_dark), (hx(rgb_dark) + hx(rgb_light)) / 2, hx(rgb_light), np.minimum(255, hx(rgb_light) + 60)]
    c.paint(liquid, pick(lc, shade_band(tv, 0.5, 0.45)))
    c.dot(6.2, 8.4, hx('#ffffff'))
    c.dot(6.0, 9.6, glass[3])
    c.outline(hx('#14161a'))
    return c.image()


def make_arrow_entity():
    """32x32 texture in the vanilla arrow entity layout (shaft side 0..16 x 0..5, back 0..5 x 5..10)."""
    img = np.zeros((32, 32, 4), np.uint8)
    shaft = hx('#8a6a42')
    shaft_d = hx('#5e4428')
    feather = hx('#e8e0d0')
    feather_d = hx('#b8ac98')
    head = hx('#c8ccd4')
    head_d = hx('#7a808a')

    def put(x, y, col):
        img[y, x, :3] = col
        img[y, x, 3] = 255

    # side view row 0..4 (16 px long): feathers at x 0..4, shaft, head at x 12..15
    for x in range(1, 15):
        put(x, 2, shaft if x % 3 else shaft_d)
    for x in range(0, 5):
        put(x, 1, feather)
        put(x, 3, feather_d)
        if x < 3:
            put(x, 0, feather)
            put(x, 4, feather_d)
    for x, ys in ((12, (1, 2, 3)), (13, (1, 2, 3)), (14, (2,)), (15, (2,))):
        for y in ys:
            put(x, y, head if y <= 2 else head_d)
    # back (5x5 at 0,5)
    for y in range(5, 10):
        for x in range(0, 5):
            if x == 2 or y == 7:
                put(x, y, feather if (x + y) % 2 else feather_d)
    put(2, 7, shaft_d)
    return Image.fromarray(img, 'RGBA')


# ------------------------------------------------------------------------------------------------ models
def rot_matrix(rx, ry, rz):
    """JOML rotationXYZ(rx, ry, rz) in degrees -> 3x3 matrix applied to column vectors."""
    a, b, g = (math.radians(x) for x in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Rz = np.array([[math.cos(g), -math.sin(g), 0], [math.sin(g), math.cos(g), 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def keep_grip(rot, t0, s0, g0, s1, g1):
    """Translation (pixels) that keeps the grip point g1 (scaled s1) where vanilla's g0 (scaled s0) is."""
    R = rot_matrix(*rot)
    t = np.array(t0) / 16.0 + R @ (s0 * np.array(g0) - s1 * np.array(g1))
    t16 = np.clip(t * 16.0, -80, 80)
    return [round(float(x), 2) for x in t16]


# vanilla handheld transforms
TP_ROT, TP_T, TP_S = (0, -90, 55), (0, 4.0, 0.5), 0.85
FP_ROT, FP_T, FP_S = (0, -90, 25), (1.13, 3.2, 1.13), 0.68
VANILLA_GRIP = (3.5 / 16 - 0.5, 0.5 - 12.5 / 16, 0.0)

# per class: (grip pixel in the 32px texture, third person scale, first person scale)
GRIP = {
    'great_sword': ((6.0, 26.0), 1.9, 1.25),
    'long_sword': ((6.0, 26.0), 1.7, 1.15),
    'sword_and_shield': ((5.0, 24.5), 1.15, 0.95),
    'dual_blades': ((5.5, 25.5), 1.1, 0.95),
    'hammer': ((7.0, 25.0), 1.7, 1.2),
    'lance': ((5.5, 26.5), 2.1, 1.3),
    'hunting_horn': ((7.0, 25.0), 1.6, 1.15),
}


def handheld_model(tex, cls):
    (gx, gy), s_tp, s_fp = GRIP[cls]
    g = (gx / 32 - 0.5, 0.5 - gy / 32, 0.0)
    tp_t = keep_grip(TP_ROT, TP_T, TP_S, VANILLA_GRIP, s_tp, g)
    fp_t = keep_grip(FP_ROT, FP_T, FP_S, VANILLA_GRIP, s_fp, g)
    tp_t[1] = float(np.clip(tp_t[1], -80, 80))
    disp = {
        'thirdperson_righthand': {'rotation': list(TP_ROT), 'translation': tp_t, 'scale': [s_tp] * 3},
        'thirdperson_lefthand': {'rotation': [TP_ROT[0], -TP_ROT[1], -TP_ROT[2]], 'translation': tp_t, 'scale': [s_tp] * 3},
        'firstperson_righthand': {'rotation': list(FP_ROT), 'translation': fp_t, 'scale': [s_fp] * 3},
        'firstperson_lefthand': {'rotation': [FP_ROT[0], -FP_ROT[1], -FP_ROT[2]], 'translation': fp_t, 'scale': [s_fp] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.7, 0.7, 0.7]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    }
    return {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'huntcraft:item/weapon/{tex}'}, 'display': disp}


def bow_model(tex, drawn_model=None):
    s = 1.15
    disp = {
        'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9 * s] * 3},
        'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9 * s] * 3},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68 * s] * 3},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68 * s] * 3},
    }
    m = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'huntcraft:item/weapon/{tex}'}, 'display': disp}
    if drawn_model:
        m['overrides'] = [{'predicate': {'huntcraft:drawn': 1}, 'model': f'huntcraft:item/{drawn_model}'}]
    return m


def write_json(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ------------------------------------------------------------------------------------------------ main
def main():
    preview = '--preview' in sys.argv
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    icons = {}
    for cls in CLASSES:
        for line in LINES:
            iid = f'{line}_{cls}'
            img = make_icon(cls, line)
            img.save(os.path.join(TEX_DIR, iid + '.png'))
            icons[iid] = img
            if cls == 'bow':
                dimg = make_icon(cls, line, drawn=True)
                dimg.save(os.path.join(TEX_DIR, iid + '_drawn.png'))
                icons[iid + '_drawn'] = dimg
                write_json(os.path.join(MODEL_DIR, iid + '.json'), bow_model(iid, iid + '_drawn'))
                write_json(os.path.join(MODEL_DIR, iid + '_drawn.json'), bow_model(iid + '_drawn'))
            else:
                write_json(os.path.join(MODEL_DIR, iid + '.json'), handheld_model(iid, cls))
    small = {
        'whetstone': make_whetstone(),
        'coating_power': make_coating('#ff5a3a', '#8a1408'),
        'coating_poison': make_coating('#b070f0', '#4a1478'),
        'coating_para': make_coating('#f8e040', '#8a6a00'),
    }
    for iid, img in small.items():
        img.save(os.path.join(TEX_DIR, iid + '.png'))
        write_json(os.path.join(MODEL_DIR, iid + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'huntcraft:item/weapon/{iid}'}})
    make_arrow_entity().save(os.path.join(TEX_DIR, 'hunter_arrow_entity.png'))
    print(f'wrote {len(icons) + len(small)} icons to {TEX_DIR}')
    if preview:
        os.makedirs(PREVIEW_DIR, exist_ok=True)
        scale = 6
        bg = (58, 58, 64, 255)
        for cls in CLASSES:
            names = [f'{l}_{cls}' for l in LINES]
            if cls == 'bow':
                names += [f'{l}_{cls}_drawn' for l in LINES]
            cols = 7
            rows = (len(names) + cols - 1) // cols
            sheet = Image.new('RGBA', (cols * (32 * scale + 8) + 8, rows * (32 * scale + 8) + 8), bg)
            for i, n in enumerate(names):
                im = icons[n].resize((32 * scale, 32 * scale), Image.NEAREST)
                x = 8 + (i % cols) * (32 * scale + 8)
                y = 8 + (i // cols) * (32 * scale + 8)
                sheet.alpha_composite(im, (x, y))
            sheet.save(os.path.join(PREVIEW_DIR, f'weapons_{cls}.png'))
        # small items + an inventory-scale strip (16px, as seen in slots)
        sheet = Image.new('RGBA', (4 * (16 * 8 + 8) + 8, 16 * 8 + 16), bg)
        for i, (n, im) in enumerate(small.items()):
            sheet.alpha_composite(im.resize((16 * 8, 16 * 8), Image.NEAREST), (8 + i * (16 * 8 + 8), 8))
        sheet.save(os.path.join(PREVIEW_DIR, 'weapons_small.png'))
        strip = Image.new('RGBA', (len(LINES) * 36 + 4, len(CLASSES) * 36 + 4), (139, 139, 139, 255))
        for r, cls in enumerate(CLASSES):
            for k, l in enumerate(LINES):
                im = icons[f'{l}_{cls}'].resize((32, 32), Image.LANCZOS)
                strip.alpha_composite(im, (4 + k * 36, 4 + r * 36))
        strip = strip.resize((strip.width * 2, strip.height * 2), Image.NEAREST)
        strip.save(os.path.join(PREVIEW_DIR, 'weapons_inventory.png'))
        print('previews in', PREVIEW_DIR)


if __name__ == '__main__':
    main()
