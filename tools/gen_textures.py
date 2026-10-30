#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_textures.py -- deterministic texture generator for "Minecraft Besiege" (mod id: mcbesiege).

Art direction: medieval machinery x steampunk, rugged and heavy, rendered as genuine
Minecraft-style pixel art (wood grain, riveted iron, stone, brass, rope, rubber).

How it works
------------
Every texture is painted on a `Tex` canvas that stores, per pixel, a *material* (an
index into MATERIALS, each of which is a dark->light colour ramp) and a floating
*shade value* (a position on that ramp).  Drawing operations write material + shade;
lighting operations (bevels, noise, cylinder / sphere shading) only move the shade.
At render time each pixel snaps to the nearest ramp entry, which keeps the palette
small and consistent across the whole set, exactly like hand-made pixel art.
Light always comes from the top-left.

Run:   python3 tools/gen_textures.py
Needs: Python 3, Pillow, numpy.  Output is byte-identical on every run: each texture
draws from its own RNG seeded with the CRC32 of its path.
"""
import math
import os
import shutil
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TOOLS = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS)
RES = os.path.join(PROJECT, 'src', 'main', 'resources')
TEX_ROOT = os.path.join(RES, 'assets', 'mcbesiege', 'textures')
PREVIEW = os.path.join(TOOLS, 'preview')

# =====================================================================================
# Palette -- material ramps, darkest -> lightest.  Base tones were sampled from the
# concept sheet (warm wood #8B5A3A/#795540, dark wood #4A3022, iron #6B6A6C, light
# steel #8A8D99, dark steel #3B3D49, banner red #7E251E-#A3362A, brass #A07A40/#C8962C,
# moss #3E4D21 ...) and extended with extra shades for bevels and highlights.
# =====================================================================================
RAMPS_HEX = {
    'wood':      ['#2B1A10', '#3E2717', '#523320', '#66412A', '#795036', '#8B5D3D', '#9E6C47', '#B27D53', '#C69062'],
    'darkwood':  ['#170E09', '#22150D', '#2E1D13', '#3B2619', '#4A3022', '#59392A', '#6A4533', '#7C523D', '#8E6048'],
    'lightwood': ['#3C2918', '#553B23', '#6E4E30', '#87633F', '#9E774D', '#B28A5C', '#C49C6C', '#D4AE7E', '#E2C092'],
    'weathered': ['#261D17', '#372A21', '#4A392C', '#5D4837', '#705843', '#84694F', '#977B5D', '#A98D6D', '#BAA07F'],
    'iron':      ['#1E1E22', '#2E2E33', '#404045', '#545458', '#6B6A6E', '#828286', '#98989C', '#AEAEB2', '#C6C6CA'],
    'steel':     ['#15161C', '#1F2129', '#2A2D37', '#363A46', '#444957', '#535968', '#646B7B', '#78808F', '#8E94A3'],
    'blackiron': ['#0A0B0D', '#121418', '#1B1E23', '#25292F', '#30353C', '#3D434B', '#4D545D', '#616972', '#7A828B'],
    'stone':     ['#252325', '#353234', '#474345', '#585456', '#6A6567', '#7B7677', '#8D8887', '#A09A97', '#B4AEA9'],
    'brass':     ['#2E1E0C', '#4A3212', '#684818', '#8A6224', '#A87C30', '#C8962C', '#DDB043', '#EECB62', '#F8E494'],
    'gold':      ['#3A2808', '#5C3E0A', '#80580E', '#A67414', '#C8921C', '#E0AE2C', '#F0C84A', '#FAE07A', '#FFF2B0'],
    'copper':    ['#2A120A', '#451D10', '#632B17', '#833B1F', '#A14E2A', '#BC6438', '#D07C4A', '#E2985E', '#F0B47C'],
    'red':       ['#2A0A08', '#45110D', '#5E1A15', '#7A221B', '#962C22', '#AE382A', '#C44A34', '#D66244', '#E67E5C'],
    'blue':      ['#0C1226', '#141E3C', '#1D2C56', '#263B72', '#304B8E', '#3D5DA8', '#5072BE', '#6A8BD0', '#8AA8E0'],
    'white':     ['#4E4A46', '#6A6560', '#86807A', '#A09A92', '#B8B1A8', '#CCC6BC', '#DCD6CC', '#E8E4DA', '#F4F1EA'],
    'canvas':    ['#4A4030', '#625642', '#7A6C54', '#928468', '#A89A7C', '#BCAE8E', '#CEC0A0', '#DED2B4', '#ECE2C8'],
    'rope':      ['#2A1C0E', '#3E2A14', '#56401E', '#6E5428', '#866A34', '#9E8042', '#B49652', '#C8AC66', '#DAC27E'],
    'leather':   ['#22120A', '#341C10', '#482816', '#5C341C', '#714224', '#87522C', '#9C6236', '#B07444', '#C28856'],
    'rubber':    ['#08080A', '#0F0F12', '#17171B', '#1F1F24', '#28282E', '#323238', '#3D3D44', '#4A4A52', '#5A5A63'],
    'moss':      ['#141C10', '#1E2A14', '#2A3A18', '#36481C', '#42571F', '#506826', '#5F7A2C', '#708C34', '#84A040'],
    'ice':       ['#3E5E86', '#4E73A0', '#6088B6', '#749DCA', '#8AB2DA', '#A2C6E6', '#BAD8F0', '#D2E8F8', '#EAF6FF'],
    'glow':      ['#2A0A04', '#521406', '#82240A', '#B03A0C', '#D85A12', '#F07E1E', '#FAA832', '#FFD05A', '#FFF0A8'],
    'redstone':  ['#2E0000', '#4A0202', '#6A0404', '#8C0808', '#B00E0A', '#D41C12', '#F03A22', '#FF6A44', '#FFA080'],
    'thatch':    ['#33250E', '#4A3714', '#62491B', '#7B5F24', '#94762F', '#AB8C3C', '#BFA04D', '#D1B462', '#E0C77B'],
    'parchment': ['#5A4428', '#725836', '#8C7046', '#A48858', '#BAA06C', '#CCB480', '#DAC494', '#E6D4A8', '#F0E2BE'],
    'blueprint': ['#0A1838', '#10224C', '#162E62', '#1C3A78', '#24488E', '#2E58A4', '#3C6CBA', '#5486CC', '#C8DCF4'],
    'yellow':    ['#3A2C04', '#5E4806', '#846608', '#A8840C', '#C8A010', '#E0BA1C', '#F0D02E', '#F8E04C', '#FFF08A'],
}


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


MATERIALS = list(RAMPS_HEX)
MID = {name: i for i, name in enumerate(MATERIALS)}
RAMP = [np.array([hexc(c) for c in RAMPS_HEX[n]], np.uint8) for n in MATERIALS]
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16.0
TAU = 2.0 * math.pi
LIGHT_ANGLE = math.atan2(-1.0, -1.0)  # direction *towards* the light (top-left)


# =====================================================================================
# Mask helpers
# =====================================================================================
def shift(a, dx, dy, fill=False, wrap=False):
    """out[y, x] = a[y - dy, x - dx] (pixels shifted right by dx and down by dy)."""
    if wrap:
        return np.roll(np.roll(a, dy, axis=0), dx, axis=1)
    out = np.empty_like(a)
    out[...] = fill
    h, w = a.shape[:2]
    if abs(dx) >= w or abs(dy) >= h:
        return out
    yd = slice(max(dy, 0), h + min(dy, 0))
    ys = slice(max(-dy, 0), h + min(-dy, 0))
    xd = slice(max(dx, 0), w + min(dx, 0))
    xs = slice(max(-dx, 0), w + min(-dx, 0))
    out[yd, xd] = a[ys, xs]
    return out


def edges(m, border_out=True, wrap=False):
    """Top/left edge pixels and bottom/right edge pixels of a mask."""
    f = not border_out
    up, left = shift(m, 0, 1, f, wrap), shift(m, 1, 0, f, wrap)
    down, right = shift(m, 0, -1, f, wrap), shift(m, -1, 0, f, wrap)
    return m & ~(up & left), m & ~(down & right)


def erode(m, border_out=True):
    f = not border_out
    return m & shift(m, 0, 1, f) & shift(m, 1, 0, f) & shift(m, 0, -1, f) & shift(m, -1, 0, f)


def dilate(m, diag=False):
    r = m | shift(m, 0, 1) | shift(m, 1, 0) | shift(m, 0, -1) | shift(m, -1, 0)
    if diag:
        r = r | shift(m, 1, 1) | shift(m, -1, 1) | shift(m, 1, -1) | shift(m, -1, -1)
    return r


def ring_of(m, diag=False):
    """Pixels just outside a mask."""
    return dilate(m, diag) & ~m


def line_pts(x0, y0, x1, y1):
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return pts


def star_pts(cx, cy, ro, ri, n, rot=0.0):
    pts = []
    for i in range(2 * n):
        a = rot + i * math.pi / n - math.pi / 2
        r = ro if i % 2 == 0 else ri
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def ngon_pts(cx, cy, r, n, rot=0.0):
    return [(cx + r * math.cos(rot + i * TAU / n), cy + r * math.sin(rot + i * TAU / n)) for i in range(n)]


# =====================================================================================
# Noise / shading helpers
# =====================================================================================
def vnoise(rng, w, h, cell, tile=True):
    """Smooth value noise in [0,1]; tiles seamlessly over (w, h)."""
    gw, gh = max(1, int(round(w / cell))), max(1, int(round(h / cell)))
    g = rng.random((gh, gw))
    xs = (np.arange(w) + 0.5) * gw / w - 0.5
    ys = (np.arange(h) + 0.5) * gh / h - 0.5
    x0, y0 = np.floor(xs).astype(int), np.floor(ys).astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
    x0, y0 = x0 % gw, y0 % gh
    a, b = g[np.ix_(y0, x0)], g[np.ix_(y0, x1)]
    c, d = g[np.ix_(y1, x0)], g[np.ix_(y1, x1)]
    FX, FY = fx[None, :], fy[:, None]
    return ((a * (1 - FX) + b * FX) * (1 - FY) + (c * (1 - FX) + d * FX) * FY).astype(np.float32)


def fbm(rng, w, h, cells=(8, 4, 2), weights=(0.5, 0.3, 0.2)):
    f = np.zeros((h, w), np.float32)
    for c, wt in zip(cells, weights):
        f += wt * vnoise(rng, w, h, c)
    return f / sum(weights)


def grain_row(rng, n, amt=1.0):
    """1-D wood-grain streaks along a plank (wraps so planks tile along their length)."""
    r = np.zeros(n, np.float32)
    for _ in range(max(1, int(round(n / 4 * amt)))):
        s = int(rng.integers(0, n))
        ln = int(rng.integers(2, 6))
        d = float(rng.choice([-1.0, -1.0, -0.7, 0.7]))
        r[(s + np.arange(ln)) % n] += d
    return r


def cyl_profile(n, lo=-1.5, hi=1.0):
    """Per-column shade offsets for a cylinder lit from the left."""
    u = (np.arange(n) + 0.5) / n * 2 - 1
    nz = np.sqrt(np.clip(1 - u * u, 0, 1))
    lam = np.clip(-0.6 * u + 0.8 * nz, 0, 1) * (0.4 + 0.6 * nz)
    lam = lam / lam.max()
    return (lo + (hi - lo) * lam).astype(np.float32)


def angle_diff(a, b):
    return np.abs((a - b + math.pi) % TAU - math.pi)


# =====================================================================================
# Canvas
# =====================================================================================
class Tex:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.mat = np.full((h, w), -1, np.int16)
        self.val = np.zeros((h, w), np.float32)
        self.dith = np.zeros((h, w), np.float32)
        yy, xx = np.mgrid[0:h, 0:w]
        self.X, self.Y = xx, yy
        self.PX, self.PY = xx + 0.5, yy + 0.5

    # ---- masks ------------------------------------------------------------------
    def none(self):
        return np.zeros((self.h, self.w), bool)

    def all(self):
        return np.ones((self.h, self.w), bool)

    def R(self, x0, y0, x1, y1):
        """Inclusive rectangle."""
        return (self.X >= x0) & (self.X <= x1) & (self.Y >= y0) & (self.Y <= y1)

    def D(self, cx, cy, r):
        """Disc tested at pixel centres."""
        return (self.PX - cx) ** 2 + (self.PY - cy) ** 2 < r * r

    def ring(self, cx, cy, r0, r1):
        d2 = (self.PX - cx) ** 2 + (self.PY - cy) ** 2
        return (d2 >= r0 * r0) & (d2 < r1 * r1)

    def ellipse(self, cx, cy, rx, ry):
        return ((self.PX - cx) / rx) ** 2 + ((self.PY - cy) / ry) ** 2 < 1.0

    def polar(self, cx, cy):
        dx, dy = self.PX - cx, self.PY - cy
        return np.hypot(dx, dy), np.arctan2(dy, dx)

    def seg(self, x0, y0, x1, y1, w):
        """Capsule of width w around a segment."""
        px, py = self.PX - x0, self.PY - y0
        dx, dy = x1 - x0, y1 - y0
        l2 = dx * dx + dy * dy
        t = np.clip((px * dx + py * dy) / l2, 0, 1) if l2 > 0 else 0.0
        return np.hypot(px - t * dx, py - t * dy) <= w / 2.0

    def seg_t(self, x0, y0, x1, y1):
        """Parameter along a segment (0..1, unclamped) for every pixel."""
        dx, dy = x1 - x0, y1 - y0
        return ((self.PX - x0) * dx + (self.PY - y0) * dy) / (dx * dx + dy * dy)

    def poly(self, pts):
        x, y = self.PX, self.PY
        inside = self.none()
        n = len(pts)
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            cond = (y0 > y) != (y1 > y)
            dy = (y1 - y0) if y1 != y0 else 1e-9
            xint = (x1 - x0) * (y - y0) / dy + x0
            inside ^= cond & (x < xint)
        return inside

    def spr(self, x, y, rows, chars='#'):
        """Mask from a list of strings (any char in `chars` is set)."""
        m = self.none()
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in chars and 0 <= x + i < self.w and 0 <= y + j < self.h:
                    m[y + j, x + i] = True
        return m

    def opaque(self):
        return self.mat >= 0

    def ismat(self, name):
        return self.mat == MID[name]

    # ---- drawing ----------------------------------------------------------------
    def put(self, mask, mat, val, dith=None):
        mask = np.asarray(mask, bool)
        if mat is None:
            self.mat[mask] = -1
            return self
        self.mat[mask] = MID[mat]
        if np.ndim(val) == 2:
            self.val[mask] = np.broadcast_to(val, (self.h, self.w))[mask]
        else:
            self.val[mask] = val
        self.dith[mask] = 0.0 if dith is None else dith
        return self

    def rect(self, x0, y0, x1, y1, mat, val):
        return self.put(self.R(x0, y0, x1, y1), mat, val)

    def px(self, x, y, mat, val, wrap=False):
        if wrap:
            x, y = x % self.w, y % self.h
        if 0 <= x < self.w and 0 <= y < self.h:
            if mat is not None:
                self.mat[y, x] = MID[mat]
            self.val[y, x] = val
            self.dith[y, x] = 0.0

    def addpx(self, x, y, d, wrap=False):
        if wrap:
            x, y = x % self.w, y % self.h
        if 0 <= x < self.w and 0 <= y < self.h and self.mat[y, x] >= 0:
            self.val[y, x] += d

    def add(self, mask, d):
        mask = np.asarray(mask, bool) & self.opaque()
        if np.ndim(d) == 2:
            self.val[mask] += np.broadcast_to(d, (self.h, self.w))[mask]
        else:
            self.val[mask] += d
        return self

    def noise(self, rng, amp, mask=None, steps=2):
        n = rng.uniform(-amp, amp, (self.h, self.w)).astype(np.float32)
        if steps:
            n = np.round(n * steps) / steps
        return self.add(self.all() if mask is None else mask, n)

    def bevel(self, mask, hi=1.0, lo=-1.0, border_out=True, wrap=False):
        tl, br = edges(np.asarray(mask, bool), border_out, wrap)
        self.add(tl, hi)
        self.add(br, lo)
        return self

    def shadow(self, mask, d=-1.0, dx=1, dy=1):
        """Darken what lies just below/right of a mask (a cast shadow)."""
        self.add(shift(mask, dx, dy) & ~mask, d)

    def line(self, x0, y0, x1, y1, mat, val):
        for (x, y) in line_pts(x0, y0, x1, y1):
            self.px(x, y, mat, val)

    def addline(self, x0, y0, x1, y1, d):
        for (x, y) in line_pts(x0, y0, x1, y1):
            self.addpx(x, y, d)

    def sprite(self, x, y, rows, legend):
        """Paint a string sprite. legend: char -> (mat, val) or ('+', delta)."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                e = legend.get(ch)
                if e is None:
                    continue
                if e[0] == '+':
                    self.addpx(x + i, y + j, e[1])
                else:
                    self.px(x + i, y + j, e[0], e[1])

    def outline(self, mat=None, val=0.0, diag=False):
        """Grow a 1px outline around everything opaque. mat=None -> darkest shade of
        the neighbouring material (vanilla-item style)."""
        o = self.opaque()
        ring = ring_of(o, diag)
        if mat is not None:
            self.put(ring, mat, val)
            return ring
        orig = self.mat.copy()
        order = [(0, -1), (-1, 0), (1, 0), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)]
        for dx, dy in order:
            src = shift(orig, dx, dy, -1)
            sel = ring & (src >= 0) & (self.mat < 0)
            self.mat[sel] = src[sel]
            self.val[sel] = val
            self.dith[sel] = 0.0
        return ring

    def render(self):
        out = np.zeros((self.h, self.w, 4), np.uint8)
        thr = BAYER[self.Y % 4, self.X % 4] - 0.5
        q = np.floor(self.val + 0.5 + thr * self.dith).astype(int)
        for m in np.unique(self.mat):
            if m < 0:
                continue
            sel = self.mat == m
            ramp = RAMP[m]
            out[sel, :3] = ramp[np.clip(q[sel], 0, len(ramp) - 1)]
            out[sel, 3] = 255
        return out


# =====================================================================================
# Reusable material painters
# =====================================================================================
def plank_vals(rng, A, B, n, base, var, seam, grain, joints, hi, bounds):
    """Planks running along axis A, stacked along axis B. Returns (B, A) shades."""
    v = np.zeros((B, A), np.float32)
    if bounds is None:
        bounds = [int(round(i * B / n)) for i in range(n + 1)]
    for i in range(len(bounds) - 1):
        b0, b1 = bounds[i], bounds[i + 1] - 1
        v[b0:b1 + 1, :] = base + rng.uniform(-var, var)
        for b in range(b0, b1):
            v[b] += grain_row(rng, A, grain)
        if hi:
            v[b0] += hi
        if b1 - 1 > b0:
            v[b1 - 1] -= 0.35
        v[b1, :] = seam
        for _ in range(joints):
            p = int(rng.integers(0, A))
            v[b0:b1, p] = seam + 0.6
            v[b0:b1, (p + 1) % A] += 0.6
    return v


def planks(t, rng, x0, y0, x1, y1, mat='wood', n=4, vertical=False, base=4.5, var=0.5,
           seam=0.6, grain=1.0, joints=1, hi=0.8, bounds=None):
    W, H = x1 - x0 + 1, y1 - y0 + 1
    if vertical:
        v = plank_vals(rng, H, W, n, base, var, seam, grain, joints, hi, bounds).T
    else:
        v = plank_vals(rng, W, H, n, base, var, seam, grain, joints, hi, bounds)
    full = np.zeros((t.h, t.w), np.float32)
    full[y0:y1 + 1, x0:x1 + 1] = v
    t.put(t.R(x0, y0, x1, y1), mat, full)


def vgrain(rng, w, h, base, amt=1.0, colvar=0.4):
    """Shade field with grain running vertically (tiles vertically)."""
    v = np.full((h, w), base, np.float32)
    v += rng.uniform(-colvar, colvar, w).astype(np.float32)[None, :]
    for x in range(w):
        v[:, x] += grain_row(rng, h, amt) * 0.8
    return v


def hgrain(rng, w, h, base, amt=1.0, rowvar=0.4):
    return vgrain(rng, h, w, base, amt, rowvar).T.copy()


def rivet(t, x, y, mat='iron', hi=7.5, sh=-1.8):
    t.px(x, y, mat, hi)
    t.addpx(x + 1, y + 1, sh)


def big_rivet(t, x, y, mat='iron', base=5.0, sh=-1.6):
    t.px(x, y, mat, base + 2.6)
    t.px(x + 1, y, mat, base + 1.0)
    t.px(x, y + 1, mat, base + 0.6)
    t.px(x + 1, y + 1, mat, base - 1.4)
    for dx, dy in ((2, 1), (1, 2), (2, 2)):
        t.addpx(x + dx, y + dy, sh)


def gear_mask(t, cx, cy, r_root, r_tip, teeth, phase=0.25, duty=0.5):
    r, th = t.polar(cx, cy)
    f = ((th / TAU) * teeth + phase) % 1.0
    return r < np.where(f < duty, r_tip, r_root)


def sphere(t, cx, cy, r, mat, lo, hi, dith=0.6, mask=None):
    dx, dy = (t.PX - cx) / r, (t.PY - cy) / r
    d2 = dx * dx + dy * dy
    m = (d2 < 1.0) if mask is None else mask
    nz = np.sqrt(np.clip(1 - d2, 0, 1))
    L = np.array([-0.45, -0.55, 0.70])
    L = L / np.linalg.norm(L)
    lam = np.clip(dx * L[0] + dy * L[1] + nz * L[2], 0, 1)
    t.put(m, mat, (lo + (hi - lo) * lam).astype(np.float32), dith=dith)
    return m


def coil(t, cx, ys, rx, ry, thick=1.0, tilt=0.12, wrap_h=None, back=('steel', 2.4), front=('steel', 5.6)):
    """Side view of a coil spring drawn as stacked, slightly tilted ellipse rings:
    all back arcs first (dark), then the front arcs on top (lit from the top-left)."""
    ca, sa = math.cos(tilt), math.sin(tilt)
    rings = []
    for yk in ys:
        dx, dy = t.PX - cx, t.PY - yk
        if wrap_h:
            dy = (dy + wrap_h / 2.0) % wrap_h - wrap_h / 2.0
        xr, yr = dx * ca + dy * sa, -dx * sa + dy * ca
        e_out = (xr / rx) ** 2 + (yr / ry) ** 2
        e_in = (xr / max(rx - thick, 0.1)) ** 2 + (yr / max(ry - thick, 0.1)) ** 2
        rings.append(((e_out < 1.0) & (e_in >= 1.0), xr, yr))
    for ring, xr, yr in rings:
        b = ring & (yr <= 0)
        t.put(b, back[0], (back[1] - 0.8 * xr / rx).astype(np.float32))
    allf = t.none()
    for ring, xr, yr in rings:
        f = ring & (yr > 0)
        t.put(f, front[0], (front[1] - 1.1 * xr / rx).astype(np.float32))
        allf |= f
    up = shift(allf, 0, 1)
    t.add(allf & ~up, 1.1)
    t.add(allf & ~shift(allf, 0, -1), -1.0)
    return allf


def lit(th, amt=1.0):
    """Shade offset for a surface facing angle th (light from top-left)."""
    return amt * np.cos(th - LIGHT_ANGLE)


def metal_fill(t, rng, mask, mat, base, noise=0.35, mottle=0.0, cell=4):
    t.put(mask, mat, base)
    if mottle:
        t.add(mask, (vnoise(rng, t.w, t.h, cell) - 0.5) * 2 * mottle)
    t.noise(rng, noise, mask, 2)


def stones_from_map(t, rng, cmap, mat='stone', base=4.5, var=0.7, mortar=0.7, noise=0.5,
                    rough=0.8, hi=1.2, lo=-1.2, cell=4, pits=0.06, mortar_mat=None):
    """Paint stones described by a char map ('.' = mortar, letters = stone ids)."""
    chars = sorted(set(''.join(cmap)) - {'.'})
    grid = np.array([list(r) for r in cmap])
    mort = grid == '.'
    t.put(mort, mortar_mat or mat, mortar)
    t.noise(rng, 0.3, mort, 2)
    field = vnoise(rng, t.w, t.h, cell)
    for ch in chars:
        m = grid == ch
        t.put(m, mat, base + rng.uniform(-var, var) + (field - 0.5) * 2 * rough)
        t.noise(rng, noise, m, 2)
        t.bevel(m, hi, lo, wrap=True)
        pm = m & (rng.random((t.h, t.w)) < pits) & erode(m)
        t.add(pm, -1.5)
    return grid


def brick_map(rows, w=16):
    """rows: [(height, joints)]; each band ends in a mortar row, joints are mortar columns."""
    letters = iter('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789')
    grid = []
    for h, joints in rows:
        js = sorted(j % w for j in joints)
        line = ['.'] * w
        for k, j in enumerate(js):
            ch = next(letters)
            j2 = js[(k + 1) % len(js)]
            n = (j2 - j - 1) % w if len(js) > 1 else w - 1
            for i in range(n):
                line[(j + 1 + i) % w] = ch
        grid += [''.join(line)] * (h - 1) + ['.' * w]
    return grid


# =====================================================================================
# Registry
# =====================================================================================
REG = []
CACHE = {}


def tex(path, w=16, h=16):
    def deco(fn):
        REG.append((path, w, h, fn))
        return fn
    return deco


def seed_for(path):
    return zlib.crc32(path.encode('utf-8')) & 0xFFFFFFFF


# =====================================================================================
# BLOCK TEXTURES -- machine parts
# =====================================================================================
# ---- machine core (starting block) -------------------------------------------------
def crate_base(t, rng):
    """Sturdy crate: recessed horizontal planks inside a raised 2px wooden frame."""
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, base=3.9, var=0.4, joints=1)
    frame = t.all() & ~t.R(2, 2, 13, 13)
    v = np.full((16, 16), 5.2, np.float32)
    for y in (0, 1, 14, 15):
        v[y, :] += grain_row(rng, 16, 0.8) * 0.7
    for x in (0, 1, 14, 15):
        v[2:14, x] += grain_row(rng, 12, 0.8) * 0.7
    t.put(frame, 'wood', v)
    t.bevel(frame, 1.1, -1.3)
    t.add(t.R(2, 2, 13, 2), -1.2)
    t.add(t.R(2, 3, 2, 13), -1.0)


def corner_brackets(t, rng, mat='iron', arm=5, base=4.4):
    for sx, sy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        m = t.none()
        for i in range(arm):
            for j in range(2):
                for (xx, yy) in ((i, j), (j, i)):
                    m[15 - yy if sy else yy, 15 - xx if sx else xx] = True
        t.put(m, mat, base)
        t.noise(rng, 0.3, m, 2)
        t.bevel(m, 1.4, -1.4)
        t.add(shift(m, 1, 1) & ~m & ~t.ismat(mat), -1.0)
        rivet(t, 14 if sx else 1, 14 if sy else 1, mat, 7.6)


ARROW_UP = [
    "...##...",
    "..####..",
    ".######.",
    "########",
    "..####..",
    "..####..",
    "..####..",
    "..####..",
    "..####..",
    "..####..",
]


@tex('block/starting_block_side')
def starting_block_side(t, rng):
    crate_base(t, rng)
    corner_brackets(t, rng)
    plate = t.D(8, 8, 5.8)
    metal_fill(t, rng, plate, 'steel', 1.9, 0.3)
    t.bevel(plate, -0.9, 0.9)
    g = gear_mask(t, 8, 8, 3.8, 5.1, 8)
    metal_fill(t, rng, g, 'brass', 4.9, 0.3)
    t.bevel(g, 1.8, -1.6)
    t.add(shift(g, 1, 1) & ~g & plate, -0.8)
    hub = t.D(8, 8, 2.2)
    t.add(ring_of(hub) & g, -0.7)
    t.put(t.D(8, 8, 1.2), 'steel', 0.6)


@tex('block/starting_block_top')
def starting_block_top(t, rng):
    crate_base(t, rng)
    corner_brackets(t, rng)
    arrow = t.spr(4, 3, ARROW_UP)
    ring = ring_of(arrow, diag=True)
    t.add(shift(ring | arrow, 1, 1) & ~ring & ~arrow, -1.2)
    t.put(ring, 'darkwood', 0.8)
    metal_fill(t, rng, arrow, 'brass', 5.0, 0.3)
    t.bevel(arrow, 1.7, -1.6)


@tex('block/starting_block_bottom')
def starting_block_bottom(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, base=4.0, joints=1)
    frame = t.all() & ~t.R(2, 2, 13, 13)
    metal_fill(t, rng, frame, 'iron', 4.0, 0.35)
    t.bevel(frame, 1.3, -1.3)
    t.add(t.R(2, 2, 13, 2), -1.2)
    t.add(t.R(2, 3, 2, 13), -1.0)
    for (x, y) in ((1, 1), (7, 1), (14, 1), (1, 7), (14, 7), (1, 14), (7, 14), (14, 14)):
        rivet(t, x, y, 'iron', 7.4, -1.6)


# ---- basic wood ------------------------------------------------------------------
@tex('block/wooden_block')
def wooden_block(t, rng):
    # 2x2 grid of plank panels; deep seams between panels, soft seams between boards
    for qy in (0, 8):
        for qx in (0, 8):
            planks(t, rng, qx, qy, qx + 7, qy + 7, 'wood', n=2, vertical=True, base=4.9,
                   var=0.45, joints=0, grain=1.2, hi=0.8)
            t.add(t.R(qx + 3, qy, qx + 3, qy + 6), 1.2)
            t.put(t.R(qx + 7, qy, qx + 7, qy + 7), 'wood', 0.2)
            t.put(t.R(qx, qy + 7, qx + 7, qy + 7), 'wood', 0.2)
            t.add(t.R(qx, qy, qx + 6, qy), 0.9)
            t.add(t.R(qx, qy + 6, qx + 6, qy + 6), -0.6)
            t.add(t.R(qx + 6, qy, qx + 6, qy + 6), -0.4)


@tex('block/reinforced_wood')
def reinforced_wood(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, vertical=True, base=4.1, var=0.5, joints=0, grain=1.2)
    for y0 in (2, 11):
        band = t.R(0, y0, 15, y0 + 2)
        metal_fill(t, rng, band, 'iron', 3.3, 0.3)
        t.add(t.R(0, y0, 15, y0), 1.5)
        t.add(t.R(0, y0 + 2, 15, y0 + 2), -1.2)
        t.add(t.R(0, y0 + 3, 15, y0 + 3), -1.3)
        for x in (1, 5, 9, 13):
            rivet(t, x, y0 + 1, 'iron', 7.2, -1.2)


@tex('block/wooden_pole')
def wooden_pole(t, rng):
    v = vgrain(rng, 16, 16, 4.6, 1.1, 0.35) + cyl_profile(16, -1.8, 1.0)[None, :]
    t.put(t.all(), 'wood', v)


@tex('block/wooden_pole_end')
def wooden_pole_end(t, rng):
    dx, dy = t.PX - 8, t.PY - 8
    r = (np.abs(dx) ** 3 + np.abs(dy) ** 3) ** (1 / 3)
    th = np.arctan2(dy, dx)
    rings = 5.6 + 1.0 * np.cos(r * TAU / 2.3 + 0.5 * np.sin(th * 3))
    t.put(t.all(), 'wood', np.round(rings))
    t.noise(rng, 0.3, None, 2)
    t.put(t.D(8, 8, 1.0), 'wood', 3.0)
    rim = r >= 6.9
    t.put(rim, 'wood', 3.6)
    t.noise(rng, 0.6, rim, 2)
    t.bevel(t.all(), 0.8, -1.0)
    t.bevel(t.all() & ~rim, 0.5, -0.5)
    # radial drying crack
    for i in range(2, 7):
        t.px(8 + i // 2, 8 + i, 'wood', 2.0)


@tex('block/brace')
def brace(t, rng):
    v = vgrain(rng, 16, 16, 4.6, 1.3, 0.5)
    v[:, 0] += 1.1
    v[:, 1] += 0.4
    v[:, 14] -= 0.5
    v[:, 15] -= 1.3
    t.put(t.all(), 'weathered', v)
    for x0 in (4, 10):
        y = int(rng.integers(0, 16))
        for i in range(int(rng.integers(4, 7))):
            t.px(x0, (y + i) % 16, 'weathered', 1.2)
            t.addpx(x0 - 1, (y + i) % 16, 0.6)
    kx, ky = 7, int(rng.integers(3, 12))
    t.sprite(kx - 1, ky - 1, [".a.", "aba", ".a."], {'a': ('weathered', 3.0), 'b': ('weathered', 1.4)})


# ---- metals ----------------------------------------------------------------------
@tex('block/iron_block')
def iron_block(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 4.7, 0.3, 0.2)
    for qy in (0, 8):
        for qx in (0, 8):
            p = t.R(qx, qy, qx + 7, qy + 7)
            t.add(p, (0.45 - ((t.X - qx) + (t.Y - qy)) / 14.0 * 0.9).astype(np.float32))
            t.bevel(p, 1.7, -2.0)
            for (x, y) in ((qx + 1, qy + 1), (qx + 5, qy + 1), (qx + 1, qy + 5), (qx + 5, qy + 5)):
                rivet(t, x, y, 'iron', 7.8, -2.0)


@tex('block/steel_block')
def steel_block(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 4.0, 0.35, 0.3)
    t.bevel(t.all(), 1.6, -1.6)
    t.bevel(t.R(1, 1, 14, 14), 0.7, -0.7)
    c = t.R(3, 3, 12, 12)
    t.add(c, -0.5)
    t.bevel(c, -1.1, 1.0)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        big_rivet(t, x, y, 'steel', 5.0)
    # wear: bright scratches and chipped edges
    for _ in range(3):
        x, y = int(rng.integers(4, 11)), int(rng.integers(4, 11))
        ln = int(rng.integers(2, 4))
        t.addline(x, y, x + ln, y - ln + 1, 1.3)
    border = t.all() & ~t.R(1, 1, 14, 14)
    t.add(border & (rng.random((16, 16)) < 0.18), 1.4)


@tex('block/iron_plate')
def iron_plate(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 4.0, 0.3, 0.55, cell=8)
    t.add(t.all(), (0.5 - (t.X + t.Y) / 30.0).astype(np.float32))
    t.bevel(t.all(), 1.4, -1.6)
    border = t.all() & ~t.R(1, 1, 14, 14)
    t.add(border & (rng.random((16, 16)) < 0.18), 1.6)
    t.add(border & (rng.random((16, 16)) < 0.12), -1.2)
    for (x, y) in ((2, 2), (7, 2), (12, 2), (2, 7), (12, 7), (2, 12), (7, 12), (12, 12)):
        rivet(t, x, y, 'iron', 7.6, -1.9)
    # a dent and a long scratch
    t.addline(5, 10, 8, 7, -1.0)
    t.addline(6, 10, 9, 7, 1.0)
    t.addline(9, 4, 11, 4, 1.2)


@tex('block/stone_block')
def stone_block(t, rng):
    cmap = brick_map([(5, [0, 9]), (4, [4, 12]), (3, [1, 7, 12]), (4, [5, 13])])
    stones_from_map(t, rng, cmap, 'stone', base=4.8, var=0.6, mortar=1.0, noise=0.5,
                    rough=0.7, hi=1.3, lo=-1.2)


@tex('block/wooden_panel')
def wooden_panel(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'lightwood', n=4, base=4.6, var=0.4, seam=1.4, grain=0.8, joints=1, hi=0.7)


# ---- wheels ----------------------------------------------------------------------
def spoke_mask(t, cx, cy, ang, r0, r1, w0, w1):
    ux, uy = math.cos(ang), math.sin(ang)
    dx, dy = t.PX - cx, t.PY - cy
    along = dx * ux + dy * uy
    perp = np.abs(-dx * uy + dy * ux)
    k = np.clip((along - r0) / max(r1 - r0, 1e-6), 0, 1)
    return (along > r0) & (along < r1) & (perp < (w0 + (w1 - w0) * k) / 2)


@tex('block/wheel_face', 32, 32)
def wheel_face(t, rng):
    c = 16.0
    r, th = t.polar(c, c)
    disc = r < 15.95
    t.put(disc, 'darkwood', 0.7)
    t.add(disc & (r < 10.6), (rng.random((32, 32)) < 0.15) * 0.5)
    fel = (r >= 10.4) & (r < 13.4)
    t.put(fel, 'wood', 4.8 + lit(th, 0.5))
    t.noise(rng, 0.7, fel, 2)
    t.bevel(fel, 1.2, -1.3)
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 8
        t.put(fel & (angle_diff(th, a) < 0.07), 'wood', 1.5)
    spokes = t.none()
    for k in range(8):
        spokes |= spoke_mask(t, c, c, k * math.pi / 4, 3.0, 10.9, 3.0, 2.4)
    t.put(spokes, 'wood', 5.0)
    t.noise(rng, 0.5, spokes, 2)
    t.bevel(spokes, 1.1, -1.2)
    tyre = (r >= 13.4) & disc
    metal_fill(t, rng, tyre, 'iron', 4.0, 0.35)
    t.add(tyre, lit(th, 0.7))
    t.bevel(tyre, 1.4, -1.5)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        rivet(t, int(c + 14.6 * math.cos(a)), int(c + 14.6 * math.sin(a)), 'iron', 7.4, -1.4)
    hub = r < 4.4
    metal_fill(t, rng, hub, 'iron', 4.2, 0.3)
    t.bevel(hub, 1.4, -1.5)
    t.add(t.ring(c, c, 2.6, 3.4), -0.8)
    big_rivet(t, 15, 15, 'iron', 5.2)


@tex('block/wheel_tread')
def wheel_tread(t, rng):
    t.put(t.all(), 'wood', vgrain(rng, 16, 16, 4.0, 1.0, 0.3))
    band = t.R(2, 0, 13, 15)
    metal_fill(t, rng, band, 'iron', 4.0, 0.25, 0.2)
    t.add(t.R(2, 0, 2, 15), 1.4)
    t.add(t.R(3, 0, 3, 15), 0.5)
    t.add(t.R(13, 0, 13, 15), -1.4)
    t.add(t.R(1, 0, 1, 15) | t.R(14, 0, 14, 15), -1.0)
    for (x, y) in ((5, 1), (10, 5), (5, 9), (10, 13)):
        rivet(t, x, y, 'iron', 7.4, -1.5)
    t.add(t.R(2, 7, 13, 7), -0.8)


@tex('block/iron_wheel_face', 32, 32)
def iron_wheel_face(t, rng):
    c = 16.0
    r, th = t.polar(c, c)
    disc = r < 15.95
    t.put(disc, 'rubber', 1.0)
    rim = (r >= 12.2) & disc
    metal_fill(t, rng, rim, 'steel', 4.2, 0.4)
    t.add(rim, lit(th, 0.8))
    t.bevel(rim, 1.5, -1.6)
    lip = (r >= 11.0) & (r < 12.2)
    t.put(lip, 'steel', 2.6 - lit(th, 0.6))
    for k in range(12):
        a = k * TAU / 12 + TAU / 24
        rivet(t, int(c + 14.0 * math.cos(a)), int(c + 14.0 * math.sin(a)), 'steel', 7.4, -1.6)
    spokes = t.none()
    for k in range(6):
        spokes |= spoke_mask(t, c, c, k * TAU / 6 + TAU / 12, 3.0, 11.2, 3.6, 2.8)
    metal_fill(t, rng, spokes, 'steel', 4.6, 0.3)
    t.bevel(spokes, 1.3, -1.4)
    hub = r < 5.6
    metal_fill(t, rng, hub, 'steel', 4.3, 0.3)
    t.bevel(hub, 1.5, -1.6)
    for k in range(6):
        a = k * TAU / 6
        rivet(t, int(c + 3.9 * math.cos(a)), int(c + 3.9 * math.sin(a)), 'steel', 7.0, -1.4)
    big_rivet(t, 15, 15, 'steel', 5.4)


@tex('block/iron_wheel_tread')
def iron_wheel_tread(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.7, 0.35, 0.3)
    t.add(t.R(0, 0, 0, 15), -1.3)
    t.add(t.R(1, 0, 1, 15), -0.4)
    t.add(t.R(15, 0, 15, 15), -1.5)
    t.add(t.R(14, 0, 14, 15), -0.5)
    for (x, y) in ((3, 1), (11, 1), (7, 5), (3, 9), (11, 9), (7, 13)):
        big_rivet(t, x, y, 'steel', 5.2, -1.4)


@tex('block/tire_face', 32, 32)
def tire_face(t, rng):
    c = 16.0
    r, th = t.polar(c, c)
    disc = r < 15.95
    rub = (r >= 9.6) & disc
    side = np.where(r > 12.8, 1.0, -0.7)
    t.put(rub, 'rubber', 3.4 + lit(th, 1.1) * side)
    t.noise(rng, 0.3, rub, 2)
    t.add(rub & (r > 14.8) & ((((th / TAU) * 28) % 1.0) < 0.5), -1.4)
    t.add(t.ring(c, c, 12.3, 12.9), -0.9)
    t.bevel(rub, 0.8, -0.8)
    lip = (r >= 8.7) & (r < 9.6)
    t.put(lip, 'steel', 6.0 + lit(th, 0.8))
    dish = (r >= 3.2) & (r < 8.7)
    metal_fill(t, rng, dish, 'steel', 3.8, 0.3)
    t.add(dish, -lit(th, 0.5) * (r < 6))
    for k in range(5):
        a = -math.pi / 2 + k * TAU / 5
        big_rivet(t, int(c + 6.2 * math.cos(a)) - 1, int(c + 6.2 * math.sin(a)) - 1, 'steel', 5.0, -1.2)
    cap = r < 3.2
    metal_fill(t, rng, cap, 'steel', 5.2, 0.2)
    t.bevel(cap, 1.3, -1.4)
    t.put(t.R(15, 15, 16, 16), 'steel', 2.4)


@tex('block/tire_tread')
def tire_tread(t, rng):
    t.put(t.all(), 'rubber', 4.4)
    t.add(t.all(), cyl_profile(16, -1.8, 1.0)[None, :])
    s = (t.Y + np.abs(t.X - 7.5) * 0.7) % 8.0
    t.put(s < 2.2, 'rubber', 0.6)
    t.add((s >= 2.2) & (s < 3.2), 1.8)
    t.add(s >= 7.0, -0.9)
    t.put(t.R(0, 0, 0, 15) | t.R(15, 0, 15, 15), 'rubber', 1.6)
    t.noise(rng, 0.3, None, 2)


@tex('block/small_wheel_face')
def small_wheel_face(t, rng):
    r, th = t.polar(8, 8)
    disc = r < 7.95
    rub = (r >= 5.0) & disc
    t.put(rub, 'rubber', 3.2 + lit(th, 1.0) * np.where(r > 6.5, 1.0, -0.6))
    t.bevel(rub, 0.6, -0.8)
    hub = r < 5.0
    metal_fill(t, rng, hub, 'steel', 4.8, 0.25)
    t.bevel(hub, 1.4, -1.5)
    t.add(t.ring(8, 8, 3.3, 4.0), -1.0)
    t.put(t.R(7, 7, 8, 8), 'steel', 6.6)
    t.px(8, 8, 'steel', 3.0)


@tex('block/axle')
def axle(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.6, 0.4, 0.3)
    t.bevel(t.all(), 1.0, -1.0)
    fl = t.D(8, 8, 6.7)
    metal_fill(t, rng, fl, 'steel', 4.3, 0.3)
    t.bevel(fl, 1.4, -1.5)
    t.shadow(fl, -1.0)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        rivet(t, int(8 + 4.8 * math.cos(a)), int(8 + 4.8 * math.sin(a)), 'steel', 7.2, -1.6)
    nut = t.poly(ngon_pts(8, 8, 3.5, 6, 0.0))
    t.shadow(nut, -1.2)
    metal_fill(t, rng, nut, 'iron', 5.2, 0.2)
    t.bevel(nut, 1.8, -2.0)
    t.put(t.D(8, 8, 1.2), 'iron', 2.6)
    t.px(7, 7, 'iron', 1.8)


@tex('block/grip_pad')
def grip_pad(t, rng):
    metal_fill(t, rng, t.all(), 'rubber', 2.2, 0.5)
    for yy in (1, 5, 9, 13):
        for xx in (1, 5, 9, 13):
            x = xx + (2 if (yy // 4) % 2 else 0)
            for (dx, dy, v) in ((0, 0, 7.4), (1, 0, 6.0), (0, 1, 5.4), (1, 1, 4.2)):
                t.px(x + dx, yy + dy, 'rubber', v, wrap=True)
            for (dx, dy) in ((2, 1), (1, 2), (2, 2)):
                t.addpx(x + dx, yy + dy, -1.4, wrap=True)


@tex('block/ice_pad')
def ice_pad(t, rng):
    frame = t.all() & ~t.R(2, 2, 13, 13)
    metal_fill(t, rng, frame, 'iron', 4.0, 0.35)
    t.bevel(frame, 1.3, -1.3)
    ice = t.R(2, 2, 13, 13)
    t.put(ice, 'ice', 5.0 + (vnoise(rng, 16, 16, 4) - 0.5) * 1.8)
    t.noise(rng, 0.3, ice, 2)
    s = t.X + t.Y
    t.add(ice & ((s == 9) | (s == 10)), 2.2)
    t.add(ice & (s == 17), 1.6)
    t.add(ice & (s == 12), 0.8)
    for (a, b) in (((4, 11), (6, 9)), ((6, 9), (6, 7)), ((6, 9), (9, 9)), ((10, 4), (12, 6))):
        t.line(a[0], a[1], b[0], b[1], 'ice', 2.6)
    t.add(t.R(2, 2, 13, 2), -1.2)
    t.add(t.R(2, 3, 2, 13), -1.2)
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rivet(t, x, y, 'iron', 7.4, -1.4)


# ---- seat -------------------------------------------------------------------------
@tex('block/seat_top')
def seat_top(t, rng):
    metal_fill(t, rng, t.all(), 'darkwood', 4.2, 0.4)
    t.bevel(t.all(), 1.0, -1.0)
    cush = t.R(1, 1, 14, 14) & ~(t.R(1, 1, 1, 1) | t.R(14, 1, 14, 1) | t.R(1, 14, 1, 14) | t.R(14, 14, 14, 14))
    t.shadow(cush, -1.2)
    t.put(cush, 'leather', 4.9)
    t.noise(rng, 0.3, cush, 2)
    # three padded rolls separated by stitched seams
    for (y0, y1) in ((1, 4), (6, 9), (11, 14)):
        n = y1 - y0 + 1
        for i, y in enumerate(range(y0, y1 + 1)):
            t.add(cush & (t.Y == y), 1.3 - 2.4 * i / (n - 1))
    for y in (5, 10):
        t.put(cush & (t.Y == y), 'leather', 2.2)
        t.put(cush & (t.Y == y) & (t.X % 2 == 1) & (t.X > 1) & (t.X < 14), 'canvas', 5.4)
    t.add(cush & (t.X == 1), 0.7)
    t.add(cush & (t.X == 14), -1.2)
    t.add(cush & (t.X == 13), -0.4)


@tex('block/seat_side')
def seat_side(t, rng):
    planks(t, rng, 0, 5, 15, 15, 'darkwood', n=2, base=4.8, var=0.4, joints=1)
    lea = t.R(0, 0, 15, 4)
    t.put(lea, 'leather', 4.6)
    t.noise(rng, 0.3, lea, 2)
    for y, d in enumerate((0.9, 1.3, 0.3, -0.6, -1.6)):
        t.add(t.R(0, y, 15, y), d)
    t.put(t.R(0, 2, 15, 2) & (t.X % 2 == 0), 'canvas', 5.8)
    t.add(t.R(0, 5, 15, 5), -1.2)
    for x in (1, 5, 9, 13):
        t.px(x, 4, 'brass', 6.6)
        t.addpx(x + 1, 5, -0.8)


# ---- hinges & steering ----------------------------------------------------------
@tex('block/hinge')
def hinge(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, vertical=True, base=4.1, var=0.4, joints=0)
    leaves = t.R(1, 3, 6, 12) | t.R(9, 3, 14, 12)
    t.shadow(leaves, -1.3)
    metal_fill(t, rng, leaves, 'iron', 4.3, 0.35)
    t.bevel(t.R(1, 3, 6, 12), 1.3, -1.3)
    t.bevel(t.R(9, 3, 14, 12), 1.3, -1.3)
    for (x, y) in ((3, 5), (3, 10), (11, 5), (11, 10)):
        t.px(x, y, 'iron', 1.4)
        t.px(x + 1, y, 'iron', 6.8)
    kn = t.R(6, 0, 9, 15)
    prof = np.array([2.6, 6.2, 5.0, 2.2], np.float32)
    v = np.zeros((16, 16), np.float32)
    v[:, 6:10] = prof[None, :]
    t.put(kn, 'iron', v)
    for y in (3, 7, 11, 15):
        t.put(t.R(6, y, 9, y), 'iron', 0.8)
    for y0 in (0, 8):
        t.add(t.R(6, y0, 9, y0 + 2), 0.3)
    t.put(t.R(6, 0, 9, 0), 'brass', 4.6)
    t.px(7, 0, 'brass', 6.8)


DOUBLE_ARROW = [
    "..#....#..",
    ".##....##.",
    "##########",
    ".##....##.",
    "..#....#..",
]


@tex('block/steering_hinge')
def steering_hinge(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, base=4.5, joints=1)
    for x0 in (0, 13):
        p = t.R(x0, 0, x0 + 2, 15)
        metal_fill(t, rng, p, 'iron', 4.0, 0.35)
        t.bevel(p, 1.3, -1.3)
        rivet(t, x0 + 1, 2, 'iron', 7.4)
        rivet(t, x0 + 1, 12, 'iron', 7.4)
    t.add(t.R(3, 0, 3, 15), -1.1)
    arr = t.spr(3, 6, DOUBLE_ARROW)
    ring = ring_of(arr, diag=True)
    t.put(ring & ~t.ismat('iron'), 'darkwood', 0.9)
    metal_fill(t, rng, arr, 'brass', 5.0, 0.25)
    t.bevel(arr, 1.6, -1.6)


@tex('block/steering_hinge_top')
def steering_hinge_top(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.5, 0.35)
    t.bevel(t.all(), 1.0, -1.0)
    disc = t.D(8, 8, 7.3)
    t.shadow(disc, -1.0)
    metal_fill(t, rng, disc, 'iron', 4.6, 0.35)
    t.bevel(disc, 1.5, -1.5)
    t.add(t.ring(8, 8, 5.5, 6.2), -1.0)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        rivet(t, int(8 + 6.6 * math.cos(a)), int(8 + 6.6 * math.sin(a)), 'iron', 7.5, -1.5)
    arr = t.spr(4, 3, ["...##...", "..####..", ".######.", "########", "...##...", "...##...", "...##...",
                       "...##..."])
    t.put(ring_of(arr) & disc, 'iron', 2.0)
    metal_fill(t, rng, arr, 'brass', 5.0, 0.2)
    t.bevel(arr, 1.6, -1.6)


@tex('block/steering_block')
def steering_block(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.9, 0.35, 0.3)
    t.bevel(t.all(), 1.2, -1.2)
    g = gear_mask(t, 8, 8, 6.0, 7.7, 8)
    t.shadow(g, -1.1)
    r, th = t.polar(8, 8)
    spokes = t.none()
    for k in range(4):
        spokes |= spoke_mask(t, 8, 8, math.pi / 4 + k * math.pi / 2, 0.0, 5.0, 2.5, 2.5)
    holes = t.ring(8, 8, 2.5, 4.2) & ~spokes
    body = g & ~holes
    t.put(holes, 'steel', 0.9)
    metal_fill(t, rng, body, 'brass', 4.7, 0.3)
    t.bevel(body, 1.6, -1.6)
    t.put(t.D(8, 8, 1.2), 'steel', 0.8)


@tex('block/steering_block_top')
def steering_block_top(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.6, 0.35)
    t.bevel(t.all(), 1.0, -1.0)
    disc = t.D(8, 8, 7.3)
    t.shadow(disc, -1.0)
    metal_fill(t, rng, disc, 'brass', 4.5, 0.3)
    t.bevel(disc, 1.6, -1.6)
    t.add(t.ring(8, 8, 4.6, 5.3), -1.4)
    notch = t.R(7, 0, 8, 2)
    t.put(notch, 'steel', 1.0)
    t.px(7, 2, 'steel', 0.5)
    t.add(t.R(9, 0, 9, 2) & disc, 0.8)
    t.add(t.R(6, 0, 6, 2) & disc, -0.8)
    hub = t.D(8, 8, 1.7)
    t.put(hub, 'brass', 6.0)
    t.bevel(hub, 1.2, -1.6)


# ---- power ------------------------------------------------------------------------
@tex('block/spinning_block')
def spinning_block(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.4, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    t.bevel(t.R(1, 1, 14, 14), 0.5, -0.5)
    for (x0, y0) in ((0, 0), (13, 0), (0, 13), (13, 13)):
        m = t.R(x0, y0, x0 + 2, y0 + 2)
        metal_fill(t, rng, m, 'brass', 4.7, 0.2)
        t.bevel(m, 1.4, -1.4)
        t.px(x0 + 1, y0 + 1, 'brass', 7.0)
    ring = t.ring(8, 8.5, 4.2, 5.7)
    t.shadow(ring, -1.0)
    metal_fill(t, rng, ring, 'brass', 4.6, 0.25)
    t.bevel(ring, 1.4, -1.4)
    r, th = t.polar(8, 8.5)
    core = r < 4.2
    t.put(core, 'glow', 8.2 - r * 0.95, dith=0.7)
    for x in (6, 9):
        bar = core & (t.X == x)
        t.put(bar, 'steel', 2.0)
        t.add(bar & (t.Y < 8), 0.8)
    t.put(core & (t.Y == 12), 'steel', 1.6)
    for (x, y) in ((5, 1), (10, 1), (5, 14), (10, 14)):
        rivet(t, x, y, 'steel', 7.0, -1.3)


@tex('block/spinning_block_top')
def spinning_block_top(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.0, 0.3)
    t.bevel(t.all(), 1.0, -1.0)
    disc = t.D(8, 8, 7.4)
    t.shadow(disc, -0.8)
    metal_fill(t, rng, disc, 'iron', 4.4, 0.35)
    t.bevel(disc, 1.5, -1.5)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        rivet(t, int(8 + 5.4 * math.cos(a)), int(8 + 5.4 * math.sin(a)), 'iron', 7.5, -1.6)
    hub = t.D(8, 8, 2.7)
    metal_fill(t, rng, hub, 'steel', 3.8, 0.2)
    t.bevel(hub, 1.4, -1.4)
    t.put(t.R(7, 7, 8, 8), 'steel', 6.2)
    t.put(t.R(7, 1, 8, 1), 'brass', 6.4)


@tex('block/ball_joint')
def ball_joint(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.4, 0.35)
    t.bevel(t.all(), 1.3, -1.3)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(t, x, y, 'steel', 7.0, -1.2)
    sock = t.ring(8, 8, 5.0, 7.0)
    t.shadow(sock, -1.0)
    metal_fill(t, rng, sock, 'iron', 4.2, 0.3)
    t.bevel(sock, 1.4, -1.4)
    t.put(t.D(8, 8, 5.0), 'rubber', 0.8)
    sphere(t, 8, 8, 4.6, 'brass', 1.6, 7.6, dith=0.7)
    t.px(6, 6, 'brass', 8.0)


@tex('block/suspension')
def suspension(t, rng):
    bg = t.R(0, 3, 15, 12)
    metal_fill(t, rng, bg, 'steel', 1.0, 0.2)
    t.add(t.R(0, 3, 15, 3), -0.6)
    t.put(bg, 'rubber', 1.0)
    coil(t, 8.0, (4.6, 7.9, 11.2), 4.6, 2.0, thick=1.3, tilt=0.32, back=('steel', 2.4), front=('steel', 5.8))
    for (y0p, y1p) in ((0, 2), (13, 15)):
        p = t.R(0, y0p, 15, y1p)
        metal_fill(t, rng, p, 'iron', 4.4, 0.35)
        t.bevel(p, 1.3, -1.3)
        rivet(t, 2, y0p + 1, 'iron', 7.4, -1.2)
        rivet(t, 13, y0p + 1, 'iron', 7.4, -1.2)
    t.add(t.R(0, 3, 15, 3) & t.ismat('steel'), -0.6)


@tex('block/suspension_end')
def suspension_end(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 4.0, 0.25, 0.2)
    t.bevel(t.all(), 1.3, -1.3)
    boss = t.D(8, 8, 4.7)
    t.shadow(boss, -1.0)
    metal_fill(t, rng, boss, 'iron', 4.9, 0.3)
    t.bevel(boss, 1.5, -1.5)
    nut = t.poly(ngon_pts(8, 8, 2.5, 6, math.pi / 6))
    metal_fill(t, rng, nut, 'steel', 4.6, 0.2)
    t.bevel(nut, 1.5, -1.6)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rivet(t, x, y, 'iron', 7.4, -1.5)


# ---- piston ---------------------------------------------------------------------
@tex('block/piston_side')
def piston_side(t, rng):
    planks(t, rng, 0, 0, 15, 3, 'wood', n=1, base=5.0, seam=1.2, joints=0)
    t.put(t.R(0, 0, 15, 0), 'iron', 5.0)
    t.add(t.R(0, 0, 0, 0), 1.2)
    body = t.R(0, 4, 15, 15)
    metal_fill(t, rng, body, 'steel', 3.7, 0.35, 0.3)
    t.bevel(body, 1.3, -1.3)
    rod = t.R(6, 4, 9, 9)
    t.shadow(rod, -1.2, 0, 1)
    v = np.zeros((16, 16), np.float32)
    v[:, 6:10] = np.array([3.6, 6.4, 5.4, 3.2], np.float32)[None, :]
    t.put(rod, 'brass', v)
    t.add(t.R(6, 4, 9, 4), -0.8)
    for (x, y) in ((2, 6), (13, 6), (2, 13), (13, 13)):
        rivet(t, x, y, 'steel', 7.2, -1.3)


@tex('block/piston_top')
def piston_top(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, base=4.7, joints=1)
    border = t.all() & ~t.R(1, 1, 14, 14)
    metal_fill(t, rng, border, 'iron', 4.4, 0.3)
    t.bevel(border, 1.3, -1.3)
    for x0 in (3, 11):
        b = t.R(x0, 1, x0 + 1, 14)
        t.shadow(b, -1.0, 1, 0)
        metal_fill(t, rng, b, 'iron', 3.8, 0.3)
        t.add(t.R(x0, 1, x0, 14), 1.3)
        t.add(t.R(x0 + 1, 1, x0 + 1, 14), -0.7)
        rivet(t, x0, 3, 'iron', 7.4, -1.0)
        rivet(t, x0, 11, 'iron', 7.4, -1.0)


@tex('block/piston_bottom')
def piston_bottom(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.5, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    rec = t.R(2, 2, 13, 13)
    t.bevel(rec, -0.9, 0.8)
    for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
        big_rivet(t, x, y, 'steel', 4.8, -1.2)
    boss = t.D(8, 8, 2.3)
    t.shadow(boss, -1.0)
    metal_fill(t, rng, boss, 'steel', 4.6, 0.2)
    t.bevel(boss, 1.4, -1.4)


@tex('block/piston_inner')
def piston_inner(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.3, 0.35, 0.3)
    t.bevel(t.all(), 0.9, -0.9)
    t.bevel(t.R(1, 1, 14, 14), -1.0, 0.8)
    hole = t.D(8, 8, 3.6)
    t.put(hole, 'rubber', 0.6)
    rod = t.D(8, 8, 2.4)
    metal_fill(t, rng, rod, 'brass', 4.6, 0.2)
    t.bevel(rod, 1.6, -1.6)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rivet(t, x, y, 'steel', 5.6, -1.0)


@tex('block/decoupler')
def decoupler(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 4.3, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    band = t.R(0, 5, 15, 10)
    t.put(band, 'yellow', 5.4)
    stripe = band & (((t.X + t.Y) // 2) % 2 == 1)
    t.put(stripe, 'rubber', 2.2)
    t.add(band & t.ismat('yellow') & (t.Y == 5), 1.0)
    t.add(band & t.ismat('yellow') & (t.Y == 10), -1.2)
    t.noise(rng, 0.25, band, 2)
    t.put(t.R(0, 4, 15, 4), 'iron', 6.2)
    t.put(t.R(0, 11, 15, 11), 'iron', 2.3)
    for (x, y) in ((2, 1), (12, 1), (2, 13), (12, 13)):
        t.add(t.R(x - 1, y - 1, x + 2, y + 2) & ~t.R(x - 1, y - 1, x - 1, y - 1) & ~t.R(x + 2, y + 2, x + 2, y + 2), -0.9)
        t.px(x, y, 'red', 7.4)
        t.px(x + 1, y, 'red', 5.6)
        t.px(x, y + 1, 'red', 5.0)
        t.px(x + 1, y + 1, 'red', 3.2)


@tex('block/grabber')
def grabber(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.5, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    cav = t.R(2, 2, 13, 13)
    t.put(cav, 'rubber', 1.0)
    t.add(t.R(2, 2, 13, 2) | t.R(2, 2, 2, 13), -0.6)
    top = t.R(3, 3, 12, 5)
    bot = t.R(3, 10, 12, 12)
    teeth_t = t.R(3, 6, 12, 6) & (t.X % 2 == 1)
    teeth_b = t.R(3, 9, 12, 9) & (t.X % 2 == 0)
    for jaw, teeth in ((top, teeth_t), (bot, teeth_b)):
        m = jaw | teeth
        t.shadow(m, -0.6)
        metal_fill(t, rng, m, 'iron', 4.6, 0.3)
        t.bevel(m, 1.5, -1.5)
    t.add(teeth_t, 1.0)
    t.add(teeth_b, -0.4)
    for y in (7, 8):
        t.px(1, y, 'brass', 6.0 if y == 7 else 4.0)
        t.px(14, y, 'brass', 6.0 if y == 7 else 4.0)


@tex('block/grabber_side')
def grabber_side(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.7, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    t.put(t.R(1, 7, 14, 7), 'steel', 1.4)
    t.add(t.R(1, 8, 14, 8), 1.0)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rivet(t, x, y, 'steel', 7.2, -1.4)
    for y in (3, 5):
        t.put(t.R(8, y, 12, y), 'rubber', 1.0)
        t.add(t.R(8, y + 1, 12, y + 1), 0.8)
    big_rivet(t, 3, 10, 'brass', 4.8, -1.2)


@tex('block/spring_coil')
def spring_coil(t, rng):
    t.put(t.all(), 'rubber', 0.8)
    coil(t, 8.0, (3.0, 11.0), 7.4, 3.4, thick=1.7, tilt=0.30, wrap_h=16,
         back=('steel', 2.6), front=('steel', 5.8))


@tex('block/rope')
def rope(t, rng):
    s = (t.Y + t.X * 0.6) % 8.0
    v = np.where(s < 1.0, 1.2, np.where(s < 3.0, 6.2, np.where(s < 6.0, 4.7, 3.4))).astype(np.float32)
    v += cyl_profile(16, -2.0, 0.6)[None, :]
    t.put(t.all(), 'rope', v)
    t.noise(rng, 0.6, None, 2)


# ---- cannon -----------------------------------------------------------------------
@tex('block/cannon_barrel')
def cannon_barrel(t, rng):
    v = np.full((16, 16), 3.6, np.float32) + cyl_profile(16, -2.3, 1.6)[None, :]
    t.put(t.all(), 'blackiron', v)
    t.noise(rng, 0.3, None, 2)
    for y, d in ((1, 1.8), (2, 1.0), (3, -0.2), (4, -1.4), (10, 1.6), (11, 0.2), (12, -1.2)):
        t.add(t.R(0, y, 15, y), d)
    t.add(t.R(4, 0, 4, 15), 0.6)


@tex('block/cannon_muzzle')
def cannon_muzzle(t, rng):
    metal_fill(t, rng, t.all(), 'blackiron', 2.6, 0.3)
    t.bevel(t.all(), 0.8, -0.8)
    r, th = t.polar(8, 8)
    outer = r < 7.9
    metal_fill(t, rng, outer, 'blackiron', 4.2, 0.3)
    t.add(outer, lit(th, 0.6))
    t.bevel(outer, 1.5, -1.5)
    t.add(t.ring(8, 8, 6.0, 6.8), -1.8)
    lip = t.ring(8, 8, 4.4, 6.0)
    t.add(lip, 1.0)
    t.bevel(lip, 1.0, -1.0)
    bore = r < 4.4
    t.put(bore, 'rubber', 0.4)
    wall = bore & (r > 3.1)
    t.put(wall, 'rubber', 1.6 - lit(th, 1.3))
    t.put(t.D(8, 8, 1.8), 'rubber', 0.0)


@tex('block/cannon_base')
def cannon_base(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'darkwood', n=4, base=5.1, var=0.4, joints=1)
    for x0 in (2, 12):
        s = t.R(x0, 0, x0 + 1, 15)
        t.shadow(s, -1.0, 1, 0)
        metal_fill(t, rng, s, 'iron', 3.7, 0.3)
        t.add(t.R(x0, 0, x0, 15), 1.3)
        t.add(t.R(x0 + 1, 0, x0 + 1, 15), -0.7)
        for y in (1, 5, 9, 13):
            rivet(t, x0, y, 'iron', 7.2, -1.2)


# ---- crossbow ---------------------------------------------------------------------
@tex('block/crossbow_stock')
def crossbow_stock(t, rng):
    v = vgrain(rng, 16, 16, 5.0, 1.2, 0.4)
    v[:, 0] += 0.9
    v[:, 15] -= 1.3
    t.put(t.all(), 'darkwood', v)
    t.put(t.R(7, 0, 7, 15), 'darkwood', 1.2)
    t.put(t.R(8, 0, 8, 15), 'darkwood', 2.6)
    t.add(t.R(9, 0, 9, 15), 0.7)
    t.add(t.R(6, 0, 6, 15), -0.5)
    for y0 in (3, 11):
        b = t.R(0, y0, 15, y0 + 1)
        t.shadow(b, -1.0, 0, 1)
        metal_fill(t, rng, b, 'iron', 4.2, 0.3)
        t.add(t.R(0, y0, 15, y0), 1.3)
        t.add(t.R(0, y0 + 1, 15, y0 + 1), -0.8)
        rivet(t, 2, y0, 'iron', 7.4, -0.9)
        rivet(t, 12, y0, 'iron', 7.4, -0.9)


@tex('block/crossbow_limb')
def crossbow_limb(t, rng):
    t.put(t.all(), 'lightwood', hgrain(rng, 16, 16, 4.8, 1.0, 0.3))
    t.put(t.R(0, 5, 15, 5) | t.R(0, 10, 15, 10), 'lightwood', 2.4)
    t.add(t.R(0, 6, 15, 6) | t.R(0, 11, 15, 11), 0.6)
    t.put(t.R(0, 0, 15, 0), 'iron', 6.0)
    t.put(t.R(0, 1, 15, 1), 'iron', 4.2)
    t.put(t.R(0, 14, 15, 14), 'iron', 4.4)
    t.put(t.R(0, 15, 15, 15), 'iron', 2.4)
    sl = t.R(6, 0, 9, 15)
    metal_fill(t, rng, sl, 'iron', 4.0, 0.3)
    t.add(t.R(6, 0, 6, 15), 1.3)
    t.add(t.R(9, 0, 9, 15), -1.2)
    t.shadow(sl, -1.0, 1, 0)
    rivet(t, 7, 4, 'iron', 7.5, -1.0)
    rivet(t, 7, 11, 'iron', 7.5, -1.0)


@tex('block/crossbow_string')
def crossbow_string(t, rng):
    for x in range(16):
        t.px(x, 7, 'canvas', 7.0 if (x // 2) % 2 == 0 else 5.6)


# ---- flamethrower -----------------------------------------------------------------
@tex('block/flamethrower')
def flamethrower(t, rng):
    prof = cyl_profile(16, -2.0, 1.3)[None, :]
    t.put(t.all(), 'steel', 3.9 + prof)
    t.noise(rng, 0.3, None, 2)
    for y0 in (2, 12):
        b = t.R(0, y0, 15, y0 + 1)
        t.shadow(b, -1.0, 0, 1)
        t.put(b, 'brass', 4.7 + prof * 0.8)
        t.add(t.R(0, y0, 15, y0), 1.0)
        t.add(t.R(0, y0 + 1, 15, y0 + 1), -0.8)
        rivet(t, 4, y0, 'brass', 7.6, -0.8)
        rivet(t, 11, y0, 'brass', 7.6, -0.8)
    # brass valve hand-wheel on the tank
    wheel = t.ring(8, 8, 3.0, 4.2)
    spokes = (t.R(7, 4, 8, 11) | t.R(4, 7, 11, 8)) & t.D(8, 8, 4.2)
    vw = wheel | spokes
    t.shadow(vw, -1.2)
    t.put(t.D(8, 8, 3.0) & ~spokes, 'steel', 1.4)
    t.put(vw, 'brass', 5.0)
    t.bevel(vw, 1.5, -1.6)
    t.put(t.R(7, 7, 8, 8), 'brass', 6.4)
    t.px(8, 8, 'brass', 3.6)


@tex('block/flamethrower_nozzle')
def flamethrower_nozzle(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.1, 0.35, 0.3)
    t.bevel(t.all(), 1.2, -1.2)
    ring = t.ring(8, 8, 5.6, 7.4)
    t.shadow(ring, -1.0)
    metal_fill(t, rng, ring, 'brass', 4.6, 0.3)
    t.bevel(ring, 1.4, -1.4)
    t.put(t.ring(8, 8, 4.4, 5.6), 'steel', 1.0)
    r, th = t.polar(8, 8)
    core = r < 4.4
    t.put(core, 'glow', 8.0 - r * 0.95 + lit(th, -0.3), dith=0.7)


# ---- explosives -------------------------------------------------------------------
TNT_GLYPHS = [
    "###.#..#.###",
    ".#..##.#..#.",
    ".#..#.##..#.",
    ".#..#..#..#.",
    ".#..#..#..#.",
]


@tex('block/bomb_side')
def bomb_side(t, rng):
    prof = np.tile(np.array([-1.4, 0.9, 0.3, -1.0], np.float32), 4)[None, :]
    t.put(t.all(), 'red', 5.1 + prof)
    t.noise(rng, 0.3, None, 2)
    for y0 in (1, 13):
        rp = t.R(0, y0, 15, y0 + 1)
        tw = ((t.X + t.Y) % 3) == 0
        t.put(rp, 'rope', np.where(tw, 2.8, 5.4) - (t.Y == y0 + 1) * 0.9 + prof * 0.4)
        t.add(t.R(0, y0 + 2, 15, y0 + 2), -1.0)
    band = t.R(0, 4, 15, 10)
    t.put(band, 'white', 6.6 + prof * 0.7)
    t.add(t.R(0, 4, 15, 4), 0.5)
    t.add(t.R(0, 10, 15, 10), -0.7)
    t.add(t.R(0, 11, 15, 11), -0.8)
    letters = t.spr(2, 5, TNT_GLYPHS)
    t.put(letters, 'rubber', 1.4)


def bomb_ends(t, rng, fuse):
    cell = [".rr.", "rppr", "rppr", ".rr."]
    for cy in (0, 4, 8, 12):
        for cx in (0, 4, 8, 12):
            for j, row in enumerate(cell):
                for i, ch in enumerate(row):
                    x, y = cx + i, cy + j
                    if ch == '.':
                        t.px(x, y, 'red', 2.0)
                    elif ch == 'r':
                        t.px(x, y, 'red', 4.6 if (i + j) <= 2 else 3.6)
                    else:
                        t.px(x, y, 'red', {(1, 1): 6.4, (2, 2): 4.6}.get((i, j), 5.4))
    t.noise(rng, 0.3, None, 2)
    r, th = t.polar(8, 8)
    t.add(t.all(), (-np.clip((r / 11.0) ** 2, 0, 1.2)).astype(np.float32))
    if fuse:
        t.put(t.R(7, 7, 8, 8), 'rubber', 1.0)
        for (x, y, v) in ((8, 7, 3.4), (9, 6, 4.2), (10, 5, 5.0), (10, 4, 5.8)):
            t.px(x, y, 'canvas', v)
        t.px(11, 4, 'glow', 7.0)
        t.px(11, 3, 'glow', 5.4)
        t.px(12, 4, 'glow', 5.0)


@tex('block/bomb_top')
def bomb_top(t, rng):
    bomb_ends(t, rng, True)


@tex('block/bomb_bottom')
def bomb_bottom(t, rng):
    bomb_ends(t, rng, False)
    t.add(t.all(), -0.4)


@tex('block/powder_barrel_side')
def powder_barrel_side(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, vertical=True, base=4.5, var=0.5, joints=0, grain=1.1)
    bulge = np.array([-1.0, -0.6, -0.2, 0, 0.2, 0.3, 0.4, 0.4, 0.4, 0.4, 0.3, 0.2, 0, -0.2, -0.6, -1.0], np.float32)
    t.add(t.all(), np.repeat(bulge[:, None], 16, axis=1))
    stencil = t.D(7.5, 9.5, 2.6) | t.spr(8, 5, ["..#.#", "...#.", "..#.#", ".#..."])
    t.put(stencil, 'darkwood', 1.6)
    t.add(stencil & t.D(6.8, 8.8, 1.0), 0.8)
    for y0 in (2, 12):
        h = t.R(0, y0, 15, y0 + 1)
        metal_fill(t, rng, h, 'iron', 3.0, 0.3)
        t.add(t.R(0, y0, 15, y0), 1.8)
        t.add(t.R(0, y0 + 2, 15, y0 + 2), -1.0)
        rivet(t, 3, y0, 'iron', 7.2, -0.8)
        rivet(t, 11, y0, 'iron', 7.2, -0.8)


@tex('block/powder_barrel_top')
def powder_barrel_top(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 3.6, 0.3)
    t.bevel(t.all(), 1.2, -1.2)
    planks(t, rng, 1, 1, 14, 14, 'wood', n=3, base=4.7, var=0.4, joints=0, bounds=[0, 5, 10, 14])
    t.add(t.R(1, 1, 14, 1) | t.R(1, 2, 1, 14), -1.0)
    rim = t.ring(8, 8.5, 3.0, 4.0)
    t.put(rim, 'darkwood', 1.4)
    hole = t.D(8, 8.5, 3.0)
    t.put(hole, 'blackiron', 2.4)
    t.noise(rng, 1.0, hole, 1)
    t.add(t.D(7.2, 7.8, 1.5) & hole, 1.0)
    for (x, y, v) in ((9, 7, 3.2), (10, 6, 4.2), (11, 5, 5.0), (12, 4, 5.6), (12, 3, 6.6)):
        t.px(x, y, 'rope', v)


@tex('block/boulder')
def boulder(t, rng):
    # faceted rock: Voronoi facets, each a flat plane lit from the top-left
    pts = rng.uniform(0, 16, (11, 2))
    d = np.stack([(t.PX - px) ** 2 + (t.PY - py) ** 2 for px, py in pts])
    cell = np.argmin(d, axis=0)
    L = np.array([-0.5, -0.6, 0.62])
    L = L / np.linalg.norm(L)
    v = np.zeros((16, 16), np.float32)
    for k in range(len(pts)):
        n = np.array([rng.uniform(-0.8, 0.8), rng.uniform(-0.8, 0.8), 1.0])
        n = n / np.linalg.norm(n)
        v[cell == k] = 2.4 + 5.0 * max(0.0, float(n @ L))
    t.put(t.all(), 'stone', v)
    t.noise(rng, 0.4, None, 2)
    crack_br = (cell != shift(cell, -1, 0, -1)) | (cell != shift(cell, 0, -1, -1))
    t.add(crack_br & t.R(0, 0, 14, 14), -1.4)
    crack_tl = (cell != shift(cell, 1, 0, -1)) | (cell != shift(cell, 0, 1, -1))
    t.add(crack_tl & t.R(1, 1, 15, 15) & ~crack_br, 0.6)
    r, th = t.polar(8, 8)
    t.add(t.all(), (-np.clip((r / 10.5) ** 2, 0, 1.6) + lit(th, 0.5) * (r / 10.5)).astype(np.float32))


@tex('block/rocket_side')
def rocket_side(t, rng):
    s = (t.Y + t.X * 0.5) % 8.0
    red = s < 4.0
    prof = cyl_profile(16, -1.8, 0.9)[None, :]
    t.put(red, 'red', 5.2 + prof)
    t.put(~red, 'white', 6.4 + prof)
    t.add((s >= 3.5) & (s < 4.0), -1.0)
    t.add((s >= 7.5), -0.8)
    t.noise(rng, 0.25, None, 2)


@tex('block/rocket_top')
def rocket_top(t, rng):
    t.put(t.all(), 'red', 2.4)
    t.noise(rng, 0.3, None, 2)
    r, th = t.polar(8, 8)
    cone = r < 7.6
    t.put(cone, 'red', 5.0 + lit(th, 1.4) * np.clip(r / 7.6, 0.3, 1.0))
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        t.add(cone & (r > 2.4) & (angle_diff(th, a) < 0.12), -1.0)
    tip = r < 2.4
    t.put(tip, 'white', 6.6 + lit(th, 0.8))
    t.put(t.R(7, 7, 8, 8), 'white', 7.6)
    t.px(7, 7, 'white', 8.0)


@tex('block/rocket_bottom')
def rocket_bottom(t, rng):
    r, th = t.polar(8, 8)
    t.put(t.all(), 'red', 3.6 - np.clip((7.5 - r) / 4.0, 0, 1.6))
    t.noise(rng, 0.4, None, 2)
    noz = t.ring(8, 8, 3.0, 5.4)
    metal_fill(t, rng, noz, 'stone', 4.0, 0.3)
    t.bevel(noz, 1.2, -1.2)
    hole = r < 3.0
    t.put(hole, 'rubber', 0.4 + (r / 3.0) * 1.4 * (lit(th, -1.0) > 0))
    soot = (r < 7.2) & ~noz & ~hole & (rng.random((16, 16)) < 0.25)
    t.put(soot, 'blackiron', 1.6)


# ---- blades / tools ---------------------------------------------------------------
@tex('block/saw_blade', 32, 32)
def saw_blade(t, rng):
    c = 16.0
    r, th = t.polar(c, c)
    N, a, r0 = 16, 0.8, 12.6           # hooked teeth: slow rise, sharp drop
    f = ((th / TAU) * N) % 1.0
    prof = np.where(f < a, f / a, 1.0 - (f - a) / (1 - a))
    blade = r < np.minimum(r0 + (15.95 - r0) * prof, 15.95)
    t.put(blade, 'iron', 5.0 + lit(th, 0.6) * np.clip(r / 12.0, 0, 1))
    t.add(t.ring(c, c, 9.6, 10.4), -0.7)
    teeth = blade & (r > r0 - 0.2)
    t.add(teeth, 1.0)
    t.add(teeth & (f > a), -1.8)
    t.bevel(blade, 1.4, -1.8)
    shine = blade & (np.abs(t.PX - t.PY) < 1.6) & (r > 5.6) & (r < 9.4)
    t.add(shine, 1.4)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        sm = spoke_mask(t, c, c, a, 8.4, 11.6, 1.2, 1.2)
        t.put(sm, 'steel', 1.0)
        hx, hy = c + 8.2 * math.cos(a), c + 8.2 * math.sin(a)
        t.put(t.D(hx, hy, 1.0), 'steel', 0.8)
    hub = r < 5.2
    t.shadow(hub, -1.0)
    metal_fill(t, rng, hub, 'steel', 3.8, 0.25)
    t.bevel(hub, 1.4, -1.5)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        rivet(t, int(c + 3.6 * math.cos(a)), int(c + 3.6 * math.sin(a)), 'steel', 6.8, -1.4)
    t.put(t.D(c, c, 1.5), 'rubber', 0.4)


@tex('block/saw_hub')
def saw_hub(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.0, 0.35, 0.3)
    t.bevel(t.all(), 1.1, -1.1)
    fl = t.D(8, 8, 6.9)
    t.shadow(fl, -1.0)
    metal_fill(t, rng, fl, 'iron', 4.8, 0.3)
    t.bevel(fl, 1.5, -1.5)
    for k in range(6):
        a = k * TAU / 6 + TAU / 12
        rivet(t, int(8 + 4.9 * math.cos(a)), int(8 + 4.9 * math.sin(a)), 'steel', 6.8, -1.5)
    nut = t.poly(ngon_pts(8, 8, 2.5, 6, math.pi / 6))
    metal_fill(t, rng, nut, 'steel', 5.0, 0.2)
    t.bevel(nut, 1.4, -1.6)
    t.put(t.D(8, 8, 0.95), 'rubber', 0.8)


@tex('block/drill_side')
def drill_side(t, rng):
    s = (t.Y + t.X * 0.5) % 8.0
    v = np.where(s < 2.5, 1.8 + s * 0.3, 7.0 - (s - 2.5) * 0.5).astype(np.float32)
    v += cyl_profile(16, -2.4, 1.0)[None, :]
    t.put(t.all(), 'iron', v)
    t.noise(rng, 0.2, None, 2)


@tex('block/drill_tip')
def drill_tip(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.0, 0.3)
    r, th = t.polar(8, 8)
    disc = r < 7.8
    arms = ((th + r * 0.42) / TAU * 3) % 1.0
    groove = disc & (arms < 0.30)
    land = disc & ~groove
    t.put(land, 'iron', 4.2 + lit(th, 1.2) + (1 - r / 8.0) * 1.6)
    t.put(groove, 'steel', 1.4 + (arms * 4.0))
    t.add(land & (arms > 0.30) & (arms < 0.40), 1.2)
    t.bevel(disc, 0.8, -1.0)
    t.put(t.R(7, 7, 8, 8), 'iron', 7.6)
    t.px(8, 8, 'iron', 6.0)


@tex('block/hammer_head')
def hammer_head(t, rng):
    metal_fill(t, rng, t.all(), 'blackiron', 4.0, 0.4, 0.5)
    t.bevel(t.all(), 1.7, -1.7)
    t.bevel(t.R(1, 1, 14, 14), 0.6, -0.6)
    for x0 in (3, 11):
        b = t.R(x0, 0, x0 + 1, 15)
        t.put(b, 'steel', 4.4)
        t.add(t.R(x0, 0, x0, 15), 1.2)
        t.add(t.R(x0 + 1, 0, x0 + 1, 15), -0.7)
        t.add(t.R(x0 + 2, 0, x0 + 2, 15), -0.8)
        rivet(t, x0, 3, 'steel', 7.4, -0.8)
        rivet(t, x0, 12, 'steel', 7.4, -0.8)
    t.add(t.R(0, 0, 15, 0) | t.R(0, 0, 0, 15), 0.6)


@tex('block/hammer_handle')
def hammer_handle(t, rng):
    prof = cyl_profile(16, -1.8, 1.0)[None, :]
    t.put(t.all(), 'wood', vgrain(rng, 16, 16, 4.6, 1.0, 0.3) + prof)
    wrap = t.R(0, 4, 15, 11)
    s = (t.Y + t.X * 0.25) % 3.0
    t.put(wrap, 'leather', np.where(s < 2.0, 4.8 - s * 0.6, 2.0) + prof)
    t.put(t.R(0, 3, 15, 3) | t.R(0, 12, 15, 12), 'leather', 2.8 + prof)
    t.add(t.R(0, 13, 15, 13), -0.8)


@tex('block/spike')
def spike(t, rng):
    # sharpened, ground steel: a bright central ridge, left facet lit, right facet in shade
    v = np.full((16, 16), 5.0, np.float32)
    v += np.linspace(1.2, -1.4, 16, dtype=np.float32)[:, None]
    v += rng.uniform(-0.35, 0.35, 16).astype(np.float32)[None, :]
    t.put(t.all(), 'iron', v)
    t.add(t.R(0, 0, 6, 15), 0.6)
    t.add(t.R(9, 0, 15, 15), -0.9)
    t.add(t.R(7, 0, 7, 15), 2.2)
    t.add(t.R(8, 0, 8, 15), 0.8)
    for x in (3, 12):
        t.add(t.R(x, 0, x, 15), 0.6 if x < 8 else -0.5)
    t.add(t.R(0, 0, 15, 0), 1.0)
    t.bevel(t.all(), 0.6, -0.8)


@tex('block/spike_base')
def spike_base(t, rng):
    metal_fill(t, rng, t.all(), 'iron', 4.0, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    sock = t.R(4, 4, 11, 11)
    t.add(sock, -1.5)
    t.bevel(sock, -1.0, 1.0)
    stub = t.R(6, 6, 9, 9)
    metal_fill(t, rng, stub, 'iron', 5.2, 0.2)
    t.bevel(stub, 1.4, -1.4)
    t.shadow(stub, -0.8)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        big_rivet(t, x, y, 'iron', 4.8, -1.4)


# ---- flight -----------------------------------------------------------------------
@tex('block/propeller_blade')
def propeller_blade(t, rng):
    # laminated blade: alternating pale ash and darker walnut strips, twisted sheen
    v = vgrain(rng, 16, 16, 0.0, 0.7, 0.15)
    v += ((1.0 - (t.X + t.Y) / 15.0) * 1.0).astype(np.float32)
    v += ((np.abs(t.X + t.Y * 0.5 - 9.0) < 1.4) * 1.2).astype(np.float32)
    pale = ((t.X // 2) % 2) == 0
    t.put(pale, 'lightwood', v + 5.6)
    t.put(~pale, 'wood', v + 4.0)
    t.add(pale & (t.X % 2 == 0), 0.4)
    for y, d in ((0, 6.0), (1, 4.8), (2, 3.6)):
        t.put(t.R(0, y, 15, y), 'brass', d + cyl_profile(16, -0.6, 0.6))
    t.add(t.R(0, 3, 15, 3), -0.9)
    for x in (3, 8, 12):
        t.px(x, 1, 'brass', 7.6)


@tex('block/propeller_hub')
def propeller_hub(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 2.4, 0.3)
    t.bevel(t.all(), 1.0, -1.0)
    r, th = t.polar(8, 8)
    hub = r < 7.1
    t.shadow(hub, -1.0)
    t.put(hub, 'brass', 4.4 + lit(th, 0.7) * np.clip(r / 7.0, 0, 1))
    t.noise(rng, 0.25, hub, 2)
    t.bevel(hub, 1.4, -1.6)
    t.add(t.ring(8, 8, 4.0, 4.8), -1.5)
    sphere(t, 8, 8, 4.0, 'brass', 3.0, 7.6, dith=0.6)
    for k in range(6):
        a = k * TAU / 6 + TAU / 12
        rivet(t, int(8 + 5.9 * math.cos(a)), int(8 + 5.9 * math.sin(a)), 'steel', 6.6, -1.4)


@tex('block/wing')
def wing(t, rng):
    v = 5.0 + ((t.X + t.Y) % 2) * 0.4 - 0.2
    t.put(t.all(), 'canvas', v.astype(np.float32))
    t.noise(rng, 0.25, None, 2)
    for x in (1, 6, 9, 14):
        t.add(t.R(x, 0, x, 15), -0.6)
    t.add(t.R(3, 0, 4, 15) | t.R(11, 0, 12, 15), 0.4)
    t.put(t.R(0, 0, 0, 15), 'lightwood', 5.4)
    t.put(t.R(15, 0, 15, 15), 'lightwood', 3.4)
    t.put(t.R(7, 0, 7, 15), 'lightwood', 5.6)
    t.put(t.R(8, 0, 8, 15), 'lightwood', 3.6)
    for x in (1, 6, 9, 14):
        t.put(t.R(x, 0, x, 15) & (t.Y % 3 == 0), 'canvas', 2.6)


@tex('block/flying_block')
def flying_block(t, rng):
    metal_fill(t, rng, t.all(), 'copper', 4.4, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    rec = t.R(3, 3, 12, 12)
    t.put(rec, 'copper', 2.8)
    t.bevel(rec, -0.9, 0.9)
    for y in (4, 6, 8, 10):
        t.put(t.R(4, y, 11, y), 'rubber', 1.0)
        t.put(t.R(4, y + 1, 11, y + 1), 'copper', 5.0)
        t.add(t.R(4, y + 1, 4, y + 1), 0.6)
        t.add(t.R(11, y + 1, 11, y + 1), -0.8)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(t, x, y, 'brass', 7.2, -1.2)


@tex('block/flying_block_nozzle')
def flying_block_nozzle(t, rng):
    metal_fill(t, rng, t.all(), 'copper', 4.0, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    ring = t.ring(8, 8, 5.8, 7.6)
    t.shadow(ring, -1.0)
    metal_fill(t, rng, ring, 'brass', 4.6, 0.3)
    t.bevel(ring, 1.4, -1.4)
    t.put(t.ring(8, 8, 4.4, 5.8), 'steel', 1.6)
    r, th = t.polar(8, 8)
    core = r < 4.4
    t.put(core, 'glow', 8.4 - r * 0.9, dith=0.6)
    star = core & ((t.X == 7) | (t.X == 8) | (t.Y == 7) | (t.Y == 8))
    t.add(star, 0.8)
    sp = core & (rng.random((16, 16)) < 0.12)
    t.put(sp, 'glow', 8.0)


@tex('block/balloon')
def balloon(t, rng):
    prof = np.array([-0.9, 0.6, 0.5, -0.5], np.float32)
    red = ((t.X // 4) % 2) == 0
    pv = prof[t.X % 4]
    t.put(red, 'red', 5.0 + pv)
    t.put(~red, 'white', 6.4 + pv)
    t.noise(rng, 0.25, None, 2)


WEIGHT_STAMP = [
    "...##...",
    "..#..#..",
    "..####..",
    ".######.",
    ".######.",
    "########",
    "########",
]


@tex('block/ballast')
def ballast(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.4, 0.35)
    t.bevel(t.all(), 1.3, -1.3)
    ing = t.R(2, 2, 13, 13)
    metal_fill(t, rng, ing, 'gold', 4.9, 0.35)
    t.bevel(ing, 1.6, -1.6)
    t.shadow(ing, -1.0)
    st = t.spr(4, 5, WEIGHT_STAMP)
    t.add(st, -1.8)
    tl, br = edges(st)
    t.add(tl, -0.8)
    t.add(br, 0.9)
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rivet(t, x, y, 'steel', 7.0, -1.2)


# ---- automation -----------------------------------------------------------------
def smooth_stone(t, rng, base=5.0):
    metal_fill(t, rng, t.all(), 'stone', base, 0.3, 0.15, cell=8)
    t.bevel(t.all(), 1.2, -1.2)


@tex('block/timer_top')
def timer_top(t, rng):
    smooth_stone(t, rng)
    bez = t.ring(8, 8, 5.6, 7.4)
    t.shadow(bez, -1.0)
    metal_fill(t, rng, bez, 'brass', 4.6, 0.3)
    t.bevel(bez, 1.4, -1.4)
    face = t.D(8, 8, 5.6)
    t.put(face, 'parchment', 6.4)
    t.bevel(face, -0.7, 0.6)
    for (x, y) in ((7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        t.px(x, y, 'rubber', 2.4)
    for y in (4, 5, 6):
        t.px(8, y, 'rubber', 1.2)
    for x in (9, 10):
        t.px(x, 8, 'rubber', 1.2)
    t.put(t.R(7, 7, 8, 8), 'brass', 5.4)
    t.px(7, 7, 'brass', 7.0)


@tex('block/timer_side')
def timer_side(t, rng):
    smooth_stone(t, rng)
    t.add(t.R(1, 5, 14, 5), -0.8)
    t.add(t.R(1, 6, 14, 6), 0.5)
    t.put(t.R(1, 11, 14, 11), 'redstone', 3.6)
    for x in (2, 6, 10, 14):
        t.px(x, 11, 'redstone', 6.6)
    t.add(t.R(1, 12, 14, 12), -0.9)


@tex('block/sensor_front')
def sensor_front(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.4, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(t, x, y, 'steel', 7.0, -1.2)
    ring = t.ring(8, 8, 5.2, 7.0)
    t.shadow(ring, -1.0)
    metal_fill(t, rng, ring, 'brass', 4.6, 0.3)
    t.bevel(ring, 1.4, -1.4)
    r, th = t.polar(8, 8)
    lens = r < 5.2
    t.put(lens, 'steel', 1.4 - lit(th, 0.6) * (r / 5.2))
    iris = t.ring(8, 8, 1.6, 3.4)
    t.put(iris, 'redstone', 6.2 - r * 0.5, dith=0.5)
    t.put(t.D(8, 8, 1.6), 'rubber', 0.3)
    t.px(5, 5, 'white', 8.0)
    t.px(6, 5, 'white', 6.0)
    t.px(5, 6, 'white', 5.0)


@tex('block/sensor_side')
def sensor_side(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.6, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    for y in (4, 7, 10):
        t.put(t.R(3, y, 12, y), 'rubber', 1.0)
        t.add(t.R(3, y + 1, 12, y + 1), 0.8)
    for (x, y) in ((1, 1), (14, 1), (1, 14)):
        rivet(t, x, y, 'steel', 7.0, -1.2)
    t.px(12, 13, 'redstone', 7.0)
    t.px(13, 13, 'redstone', 5.0)


@tex('block/altimeter_top')
def altimeter_top(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.2, 0.35, 0.3)
    t.bevel(t.all(), 1.2, -1.2)
    bez = t.ring(8, 8, 5.7, 7.5)
    t.shadow(bez, -1.0)
    metal_fill(t, rng, bez, 'brass', 4.6, 0.3)
    t.bevel(bez, 1.4, -1.4)
    dial = t.D(8, 8, 5.7)
    t.put(dial, 'white', 6.8)
    t.bevel(dial, -0.8, 0.6)
    for k in range(8):
        a = k * TAU / 8
        t.px(int(8 + 4.6 * math.cos(a)), int(8 + 4.6 * math.sin(a)), 'rubber', 2.4)
    for (x, y) in ((8, 7), (9, 6), (10, 5), (11, 4)):
        t.px(x, y, 'red', 5.6)
    t.px(7, 8, 'red', 3.6)
    t.px(8, 8, 'brass', 6.0)


@tex('block/altimeter_side')
def altimeter_side(t, rng):
    metal_fill(t, rng, t.all(), 'steel', 3.6, 0.35, 0.3)
    t.bevel(t.all(), 1.3, -1.3)
    for y0 in (1, 13):
        t.put(t.R(0, y0, 15, y0), 'brass', 5.6)
        t.put(t.R(0, y0 + 1, 15, y0 + 1), 'brass', 3.8)
    win = t.R(5, 4, 10, 11)
    t.put(win, 'white', 6.4)
    t.bevel(win, -0.9, 0.7)
    for y in range(4, 12):
        n = 3 if y % 2 == 0 else 1
        t.put(t.R(5, y, 5 + n - 1, y), 'rubber', 2.4)
    for (x, y) in ((9, 7), (10, 6), (10, 7), (10, 8)):
        t.px(x, y, 'red', 5.6)


@tex('block/logic_gate_top')
def logic_gate_top(t, rng):
    smooth_stone(t, rng)
    t.line(4, 6, 4, 10, 'redstone', 3.2)
    t.line(11, 6, 11, 10, 'redstone', 3.2)
    t.line(4, 10, 11, 10, 'redstone', 3.2)
    t.line(8, 10, 8, 14, 'redstone', 3.2)
    t.line(1, 13, 5, 13, 'redstone', 3.2)
    t.line(5, 13, 5, 12, 'redstone', 3.2)
    for (cx, cy) in ((4, 4), (11, 4)):
        t.sprite(cx - 1, cy - 1, [".a.", "abA", ".A."],
                 {'a': ('redstone', 5.0), 'b': ('redstone', 8.0), 'A': ('redstone', 4.0)})
        t.addpx(cx + 1, cy + 1, -0.8)
    t.px(8, 14, 'redstone', 5.0)


@tex('block/logic_gate_side')
def logic_gate_side(t, rng):
    smooth_stone(t, rng, 5.0)
    t.add(t.R(1, 1, 14, 1), 0.8)
    t.add(t.R(1, 2, 14, 2), -1.0)
    t.put(t.R(1, 9, 14, 9), 'redstone', 3.4)
    for x in (3, 12):
        t.px(x, 9, 'copper', 6.4)
        t.px(x, 10, 'copper', 3.4)


@tex('block/automation_base')
def automation_base(t, rng):
    smooth_stone(t, rng, 5.2)
    segs = [((0, 4), (9, 4)), ((9, 4), (9, 12)), ((4, 12), (15, 12)), ((4, 8), (4, 12)), ((9, 8), (15, 8))]
    for (a, b) in segs:
        t.line(a[0], a[1], b[0], b[1], 'redstone', 3.3)
    for (x, y) in ((9, 4), (4, 12), (9, 8), (9, 12)):
        t.put(t.R(x, y, x + 1, y + 1) & t.R(0, 0, 15, 15), 'redstone', 5.6)
        t.px(x, y, 'redstone', 7.2)
    t.add(shift(t.ismat('redstone'), 0, 1) & ~t.ismat('redstone'), -0.7)


# =====================================================================================
# BLOCK TEXTURES -- fortress & mission blocks
# =====================================================================================
CASTLE_STONE_MAP = [
    "aaaa.bbbbbbbb.aa",
    "aaaa.bbbbbbbb.aa",
    "aaaa.bbbbbbbb.aa",
    "aaaa.bbbbbbbb.aa",
    "aaaa.bbbbbbbb.aa",
    "aaaa.bbbbbbbb...",
    ".....bbbbbbbb.dd",
    ".cccc........ddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    ".ccccccc.ddddddd",
    "................",
]


@tex('block/castle_stone')
def castle_stone(t, rng):
    stones_from_map(t, rng, CASTLE_STONE_MAP, 'stone', base=4.3, var=0.7, mortar=0.5, noise=0.7,
                    rough=1.0, hi=1.4, lo=-1.3, cell=3, pits=0.08)


@tex('block/castle_bricks')
def castle_bricks(t, rng):
    cmap = brick_map([(4, [0, 8]), (4, [4, 12]), (4, [0, 8]), (4, [4, 12])])
    stones_from_map(t, rng, cmap, 'stone', base=4.9, var=0.5, mortar=1.2, noise=0.35,
                    rough=0.4, hi=1.2, lo=-1.1, pits=0.03)
    # a few moss specks creeping along the mortar joints
    for (x, y) in ((2, 3), (3, 3), (11, 7), (12, 7), (13, 6), (6, 11), (7, 15), (14, 15)):
        t.px(x, y, 'moss', 4.0 + (x % 2) * 1.5)


def plaster(t, rng):
    t.put(t.all(), 'white', 5.6 + (vnoise(rng, 16, 16, 4) - 0.5) * 1.2)
    t.noise(rng, 0.35, None, 2)
    for _ in range(5):
        x, y = rng.integers(0, 16, 2)
        t.addpx(int(x), int(y), -1.5)


def timber(t, rng, beams):
    t.shadow(beams, -1.0)
    t.put(beams, 'darkwood', 4.3)
    t.noise(rng, 0.6, beams, 2)
    t.bevel(beams, 0.8, -0.8)


@tex('block/timber_frame')
def timber_frame(t, rng):
    plaster(t, rng)
    d1 = t.X - t.Y
    d2 = t.X + t.Y
    beams = (t.all() & ~t.R(1, 1, 14, 14)) | (np.abs(d1) <= 1) | (np.abs(d2 - 15) <= 1)
    timber(t, rng, beams)


@tex('block/timber_frame_plain')
def timber_frame_plain(t, rng):
    plaster(t, rng)
    beams = (t.all() & ~t.R(1, 1, 14, 14)) | t.R(7, 0, 8, 15) | t.R(0, 7, 15, 8)
    timber(t, rng, beams)


@tex('block/thatch')
def thatch(t, rng):
    # overlapping courses of straw bundles: dark under each overhang, sunlit ragged tips
    v = np.zeros((16, 16), np.float32)
    cols = []
    for k in range(4):
        col = 4.4 + rng.uniform(-1.0, 1.0, 16).astype(np.float32)
        col += np.where(np.arange(16) % 2 == 0, 0.45, -0.45).astype(np.float32)
        col = np.roll(col, 2 * k + (k % 2))
        cols.append(col)
        for dy, sh in enumerate((-2.6, -1.0, 0.3, 1.3)):
            v[k * 4 + dy, :] = col + sh
    for k in range(4):
        yn = (k * 4 + 4) % 16
        tips = rng.random(16) < 0.5
        v[yn, tips] = cols[k][tips] + 0.9
    t.put(t.all(), 'thatch', v)
    t.noise(rng, 0.3, None, 2)


@tex('block/castle_gate')
def castle_gate(t, rng):
    # heavy dark-stained oak gate: black-iron straps with square studs, nail rows between
    planks(t, rng, 0, 0, 15, 15, 'darkwood', n=4, vertical=True, base=5.2, var=0.5, joints=0, grain=1.3)
    for y0 in (2, 11):
        for y, d in ((y0, 5.6), (y0 + 1, 4.0), (y0 + 2, 2.6)):
            t.put(t.R(0, y, 15, y), 'blackiron', d)
        t.noise(rng, 0.3, t.R(0, y0, 15, y0 + 2), 2)
        t.add(t.R(0, y0 + 3, 15, y0 + 3), -1.4)
        for x in (1, 5, 9, 13):
            t.sprite(x, y0, ["ab", "bc"], {'a': ('iron', 7.4), 'b': ('iron', 5.2), 'c': ('iron', 3.0)})
            t.addpx(x + 2, y0 + 1, -1.0)
    for x in (1, 5, 9, 13):
        for y in (6, 8):
            rivet(t, x + (1 if y == 8 else 0), y, 'iron', 6.4, -1.4)


# ---- banners ----------------------------------------------------------------------
LION = [  # heraldic lion rampant, facing dexter (viewer's left), 12 x 16
    "....##......",
    "...####...#.",
    "..######.#.#",
    ".##.####.#..",
    "#######..#..",
    "##.####..#..",
    "#.######.#..",
    "..#######...",
    "#..######...",
    "#.#######...",
    "..##.#####..",
    "..#...####..",
    ".##....###..",
    "##.....##...",
    "#......###..",
    "##......###.",
]


def banner(t, rng, field, fv, trim, tv, charge, cv, bottom, side_trim):
    t.rect(0, 0, 15, 1, 'darkwood', 4.0)
    t.add(t.R(0, 0, 15, 0), 1.3)
    t.add(t.R(0, 1, 15, 1), -0.6)
    for x in (0, 15):
        t.px(x, 0, 'brass', 6.6)
        t.px(x, 1, 'brass', 4.4)
    cloth = t.R(1, 2, 14, 29)
    for y in range(23, 30):
        k = y - 22
        if bottom == 'point':
            cloth[y] &= np.abs(t.PX[y] - 8.0) < 7.0 - k * 1.0
        else:
            cloth[y] &= np.abs(t.PX[y] - 8.0) >= k * 1.0
    t.put(cloth, field, fv)
    edge = cloth & ~erode(cloth, border_out=True)
    low_edge = cloth & ~shift(cloth, 0, -1) & (t.Y >= 22)
    trim_m = cloth & ((t.Y == 3) | (t.Y == 4))
    if side_trim:
        trim_m |= edge & (t.Y >= 2)
    else:
        trim_m |= low_edge
    t.put(trim_m, trim, tv)
    t.add(cloth, (0.5 * np.sin((t.X - 1) * TAU / 7.0)).astype(np.float32))
    t.noise(rng, 0.25, cloth, 2)
    t.add(cloth & (t.Y == 2), -1.2)
    lion = t.spr(2, 6, LION) & cloth
    t.put(lion, charge, cv)
    t.add(lion, (0.4 * np.sin((t.X - 1) * TAU / 7.0)).astype(np.float32))
    _, lion_br = edges(lion)
    t.add(lion_br, -1.2)
    t.bevel(cloth, 0.5, -0.8)
    for x in range(1, 15):
        ys = np.where(cloth[:, x])[0]
        if len(ys) == 0:
            continue
        yb = ys.max()
        t.px(x, yb + 1, 'gold', 6.2 if x % 2 else 4.4)
        if x % 2:
            t.px(x, yb + 2, 'gold', 4.0)


@tex('block/banner_blue', 16, 32)
def banner_blue(t, rng):
    banner(t, rng, 'blue', 4.4, 'white', 6.8, 'white', 7.6, 'point', side_trim=False)


@tex('block/banner_red', 16, 32)
def banner_red(t, rng):
    banner(t, rng, 'red', 5.0, 'rubber', 2.4, 'gold', 6.0, 'swallow', side_trim=True)


# ---- objective & mission ----------------------------------------------------------
@tex('block/siege_target')
def siege_target(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, vertical=True, base=4.0, var=0.4, joints=0)
    sh = t.R(2, 1, 13, 14)
    for y in range(9, 15):
        hw = 6.0 * math.sqrt(max(0.0, 1 - ((y - 8.5) / 6.6) ** 2))
        sh[y] &= np.abs(t.PX[y] - 8.0) < hw
    t.shadow(sh, -1.4)
    t.put(sh, 'brass', 4.8)
    t.bevel(sh, 1.4, -1.4)
    inner = erode(sh)
    t.put(inner, 'white', 6.6)
    r, th = t.polar(8, 7.6)
    t.put(inner & (r < 5.1), 'red', 5.2)
    t.put(inner & (r < 3.6), 'white', 7.0)
    t.put(inner & (r < 2.1), 'red', 5.6)
    t.add(inner & t.ismat('red'), lit(th, 0.5))
    t.bevel(inner, 0.5, -0.6)


@tex('block/siege_target_side')
def siege_target_side(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=3, base=4.4, var=0.5, joints=0, bounds=[0, 5, 10, 16])
    br = t.R(6, 0, 9, 15)
    t.shadow(br, -1.0, 1, 0)
    metal_fill(t, rng, br, 'iron', 3.9, 0.3)
    t.add(t.R(6, 0, 6, 15), 1.3)
    t.add(t.R(9, 0, 9, 15), -1.2)
    rivet(t, 7, 2, 'iron', 7.4, -1.0)
    rivet(t, 7, 12, 'iron', 7.4, -1.0)


@tex('block/mission_table_top')
def mission_table_top(t, rng):
    planks(t, rng, 0, 0, 15, 15, 'wood', n=4, base=4.6, joints=1)
    rim = t.all() & ~t.R(1, 1, 14, 14)
    t.add(rim, 0.8)
    t.bevel(t.all(), 1.0, -1.0)
    pm = t.R(2, 2, 13, 13)
    t.shadow(pm, -1.2)
    t.put(pm, 'parchment', 5.6 + (vnoise(rng, 16, 16, 4) - 0.5) * 1.2)
    t.noise(rng, 0.3, pm, 2)
    for (x, y) in ((2, 2), (13, 13), (2, 13)):
        t.px(x, y, 'parchment', 3.8)
    t.px(13, 2, 'parchment', 7.6)
    t.px(12, 2, 'parchment', 4.0)
    t.px(13, 3, 'parchment', 4.0)
    for (a, b) in (((2, 6), (5, 7)), ((5, 7), (7, 10)), ((7, 10), (10, 11)), ((10, 11), (13, 12))):
        t.line(a[0], a[1], b[0], b[1], 'blue', 4.6)
    for (x, y) in line_pts(4, 4, 10, 8)[::2]:
        t.px(x, y, 'parchment', 2.2)
    t.sprite(10, 3, ["#.#", "###", "###"], {'#': ('stone', 3.0)})
    t.px(11, 3, 'red', 5.0)
    for (x, y) in ((4, 10), (6, 12)):
        t.px(x, y, 'red', 6.6)
        t.addpx(x + 1, y + 1, -1.2)
    for (x, y) in ((9, 6), (11, 9)):
        t.px(x, y, 'blue', 7.0)
        t.addpx(x + 1, y + 1, -1.2)


@tex('block/mission_table_side')
def mission_table_side(t, rng):
    t.put(t.all(), 'darkwood', 0.8)
    t.put(t.R(0, 0, 15, 2), 'wood', hgrain(rng, 16, 16, 5.0, 0.8, 0.2))
    t.add(t.R(0, 0, 15, 0), 1.2)
    t.add(t.R(0, 2, 15, 2), -1.8)
    ap = t.R(0, 3, 15, 7)
    t.put(ap, 'wood', hgrain(rng, 16, 16, 4.3, 0.8, 0.2))
    t.add(t.R(0, 7, 15, 7), -1.2)
    dr = t.R(5, 4, 10, 6)
    t.add(dr, -0.6)
    t.bevel(dr, -0.9, 0.8)
    t.px(7, 5, 'brass', 6.6)
    t.px(8, 5, 'brass', 4.4)
    for x0 in (0, 13):
        leg = t.R(x0, 3, x0 + 2, 15)
        t.put(leg, 'wood', vgrain(rng, 16, 16, 4.6, 0.8, 0.2))
        t.add(t.R(x0, 3, x0, 15), 1.0)
        t.add(t.R(x0 + 2, 3, x0 + 2, 15), -1.0)
    st = t.R(3, 12, 12, 12)
    t.put(st, 'wood', 3.8)
    t.add(t.R(3, 13, 12, 13), -0.4)


# =====================================================================================
# ITEMS
# =====================================================================================
def diag_wrench(t, sh=2, hv=(14, 16), slot=(14, 16), prong_u=8, back_u=(0, 2), handle_u0=-12, grip_len=6):
    """Open-end wrench on the 45-degree diagonal (head top-right), built in diagonal
    coordinates u = x - y (along the handle) and v = x + y (across it) so that the
    prongs and slot stay crisp. Returns (shape, grip) masks."""
    u = t.X - t.Y - sh
    v = t.X + t.Y
    handle = (v >= hv[0]) & (v <= hv[1]) & (u >= handle_u0) & (u <= back_u[0])
    back = (v >= slot[0] - 3) & (v <= slot[1] + 3) & (u >= back_u[0]) & (u <= back_u[1])
    side = ((v >= slot[0] - 3) & (v < slot[0])) | ((v > slot[1]) & (v <= slot[1] + 3))
    prong = side & (u > back_u[1]) & (u <= prong_u)
    return handle | back | prong, handle & (u <= handle_u0 + grip_len)


@tex('item/wrench')
def item_wrench(t, rng):
    shape, grip = diag_wrench(t)
    t.put(shape, 'iron', 5.6)
    t.put(grip, 'brass', 4.8)
    u = t.X - t.Y
    t.add(grip & (u % 3 == 0), -1.2)
    t.bevel(shape, 1.8, -1.9)
    t.px(9, 6, 'brass', 6.6)
    t.px(10, 7, 'brass', 3.6)
    t.outline()


@tex('item/brace_tool')
def item_brace_tool(t, rng):
    beam = t.seg(2.6, 13.4, 13.4, 2.6, 3.6)
    t.put(beam, 'wood', 5.0)
    d = t.X + t.Y
    t.add(beam & (d % 3 == 0), -0.9)
    t.noise(rng, 0.3, beam, 2)
    t.bevel(beam, 1.4, -1.6)
    for (x, y) in ((4, 10), (10, 4)):
        t.sprite(x, y, ["ab", "bc"], {'a': ('iron', 7.6), 'b': ('iron', 5.4), 'c': ('iron', 3.0)})
    t.outline()


@tex('item/spring_tool')
def item_spring_tool(t, rng):
    coil(t, 8.0, [3.0 + i * 3.0 for i in range(4)], 4.9, 2.2, thick=1.25, tilt=0.36,
         back=('steel', 3.0), front=('iron', 6.2))
    t.outline('steel', 0.0)


@tex('item/rope_tool')
def item_rope_tool(t, rng):
    # a flat spiral coil of hemp rope with the loose end trailing off
    cx, cy, pitch = 7.5, 7.0, 2.7
    r, th = t.polar(cx, cy)
    s = (r - 0.4) / pitch - th / TAU
    fr = s % 1.0
    arm = (fr < 0.64) & (r < 6.9) & (r > 0.6)
    tw = (((th / TAU) * 16 + fr * 1.2) % 1.0) < 0.5
    t.put(arm, 'rope', (np.where(tw, 5.4, 4.6) + (0.32 - fr) * 3.0).astype(np.float32))
    tail = t.seg(12.6, 9.0, 13.4, 14.2, 2.0) & ~arm
    t.put(tail, 'rope', np.where(((t.Y % 2) == 0), 5.6, 4.2).astype(np.float32))
    t.px(13, 14, 'rope', 7.2)
    t.px(14, 14, 'rope', 6.4)
    t.outline()


@tex('item/blueprint')
def item_blueprint(t, rng):
    roll = t.seg(3.2, 12.8, 11.6, 4.4, 5.0)
    d = (t.PX + t.PY - 16.0) / 2.5
    t.put(roll, 'blueprint', 4.8 - d * 1.2)
    cap = t.ellipse(12.4, 3.6, 2.6, 2.6)
    t.put(cap, 'blueprint', 6.4)
    r, th = t.polar(12.4, 3.6)
    t.put(cap & (np.abs(r - 1.4) < 0.5), 'blueprint', 3.6)
    t.put(t.D(12.4, 3.6, 0.6), 'blueprint', 2.4)
    tie = roll & (np.abs(t.PX - t.PY) < 1.0)
    t.put(tie, 'red', 5.4 - d)
    t.outline('blueprint', 0.0)


@tex('item/blueprint_filled')
def item_blueprint_filled(t, rng):
    sheet = t.R(1, 1, 14, 14) & ~t.poly([(11.0, 0.0), (16.0, 0.0), (16.0, 5.0)])
    t.put(sheet, 'blueprint', 4.4)
    t.add(sheet & ((t.X % 3 == 1) | (t.Y % 3 == 1)), 0.6)
    fold = t.poly([(11.0, 1.0), (15.0, 5.0), (11.0, 5.0)]) & t.R(1, 1, 14, 14)
    t.put(fold, 'blueprint', 6.6)
    t.add(fold & (t.X + t.Y == 16), -0.6)
    W = ('blueprint', 8.0)
    for (a, b) in (((3, 8), (10, 8)), ((3, 8), (3, 10)), ((10, 8), (10, 10)), ((3, 10), (10, 10)),
                   ((6, 8), (9, 4)), ((9, 4), (10, 4)), ((2, 13), (11, 13))):
        t.line(a[0], a[1], b[0], b[1], *W)
    for cx in (4.5, 9.5):
        t.put(t.ring(cx, 11.5, 0.9, 1.75), *W)
    t.px(2, 12, *W)
    t.px(11, 12, *W)
    t.outline('blueprint', 0.0)


@tex('item/cannonball')
def item_cannonball(t, rng):
    sphere(t, 8, 8.4, 6.5, 'blackiron', 1.0, 6.4, dith=0.6)
    t.px(5, 5, 'blackiron', 8.0)
    t.px(6, 5, 'blackiron', 7.0)
    t.px(5, 6, 'blackiron', 7.0)
    t.outline('blackiron', 0.0)


# =====================================================================================
# GUI ICONS
# =====================================================================================
SILVER = 'iron'


def silver(t, mask, base=6.2):
    t.put(mask, SILVER, base)
    t.bevel(mask, 1.6, -1.8)


@tex('gui/icon_settings')
def icon_settings(t, rng):
    g = gear_mask(t, 8, 8, 5.3, 7.2, 8) & ~t.D(8, 8, 2.3)
    silver(t, g)
    t.add(t.ring(8, 8, 2.3, 3.3) & g, -0.8)
    t.outline(SILVER, 0.0)


@tex('gui/icon_play')
def icon_play(t, rng):
    tri = t.poly([(4.0, 2.0), (4.0, 14.0), (13.6, 8.0)])
    silver(t, tri)
    t.outline(SILVER, 0.0)


@tex('gui/icon_stop')
def icon_stop(t, rng):
    sq = t.R(3, 3, 12, 12)
    silver(t, sq)
    t.outline(SILVER, 0.0)


@tex('gui/icon_trash')
def icon_trash(t, rng):
    lid = t.R(2, 3, 13, 4)
    handle = t.R(6, 1, 9, 2) & ~t.R(7, 2, 8, 2)
    body = t.poly([(3.0, 5.0), (13.0, 5.0), (12.0, 14.0), (4.0, 14.0)])
    silver(t, body, 5.8)
    for x in (5, 8, 11):
        t.put(body & t.R(x - (1 if x == 8 else 0), 7, x - (1 if x == 8 else 0), 12), SILVER, 3.4)
    silver(t, lid, 6.4)
    silver(t, handle, 6.0)
    t.outline(SILVER, 0.0)


@tex('gui/icon_mirror')
def icon_mirror(t, rng):
    left = t.spr(1, 4, [
        "...#.",
        "..##.",
        ".###.",
        "#####",
        "#####",
        ".###.",
        "..##.",
        "...#.",
    ])
    right = left[:, ::-1].copy()
    silver(t, left | right)
    axis = t.R(7, 1, 8, 14) & (((t.Y - 1) % 4) < 2)
    t.put(axis, 'brass', 5.0)
    t.add(axis & (t.X == 7), 1.2)
    t.outline(SILVER, 0.0)


def iso_cube(t, top, left, right, shade=(0.7, -0.4, -1.5)):
    T, Lv, Rv = np.array([8.0, 1.0]), np.array([1.0, 4.5]), np.array([15.0, 4.5])
    C, B, LB = np.array([8.0, 8.0]), np.array([8.0, 15.0]), np.array([1.0, 11.5])
    faces = [(top, Lv, T - Lv, C - Lv, shade[0]),
             (left, Lv, C - Lv, LB - Lv, shade[1]),
             (right, C, Rv - C, B - C, shade[2])]
    for src, o, e1, e2, sh in faces:
        Minv = np.linalg.inv(np.array([[e1[0], e2[0]], [e1[1], e2[1]]]))
        px, py = t.PX - o[0], t.PY - o[1]
        a = Minv[0, 0] * px + Minv[0, 1] * py
        b = Minv[1, 0] * px + Minv[1, 1] * py
        m = (a >= 0) & (a < 1) & (b >= 0) & (b < 1) & (t.mat < 0)
        u = np.clip((a * src.w).astype(int), 0, src.w - 1)
        v = np.clip((b * src.h).astype(int), 0, src.h - 1)
        t.mat[m] = src.mat[v[m], u[m]]
        t.val[m] = src.val[v[m], u[m]] + sh


def mini_crate():
    """8x8 crate face for the isometric category icon: dark frame, two plank seams."""
    s = Tex(8, 8)
    s.put(s.all(), 'lightwood', 5.2)
    s.put(s.R(1, 3, 6, 3) | s.R(1, 5, 6, 5), 'lightwood', 3.2)
    s.add(s.R(1, 1, 6, 1) | s.R(1, 4, 6, 4) | s.R(1, 6, 6, 6), 0.8)
    s.put(s.all() & ~s.R(1, 1, 6, 6), 'wood', 4.0)
    s.add(s.R(0, 0, 7, 0) | s.R(0, 0, 0, 7), 1.2)
    return s


def mini_iron():
    s = Tex(8, 8)
    s.put(s.all(), 'iron', 5.2)
    s.bevel(s.all(), 1.8, -1.8)
    for (x, y) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        s.px(x, y, 'iron', 7.8)
        s.px(x + 1, y + 1, 'iron', 3.4)
    return s


@tex('gui/icon_cat_basic')
def icon_cat_basic(t, rng):
    c = mini_crate()
    iso_cube(t, c, c, c)
    t.outline('darkwood', 0.6)


@tex('gui/icon_cat_armor')
def icon_cat_armor(t, rng):
    c = mini_iron()
    iso_cube(t, c, c, c)
    t.outline('iron', 0.0)


@tex('gui/icon_cat_locomotion')
def icon_cat_locomotion(t, rng):
    r, th = t.polar(8, 8)
    disc = r < 7.2
    t.put(disc, 'darkwood', 0.8)
    tyre = disc & (r >= 5.9)
    t.put(tyre, 'iron', 4.6 + lit(th, 1.0))
    fel = (r >= 4.6) & (r < 5.9)
    t.put(fel, 'wood', 5.0 + lit(th, 0.6))
    sp = t.none()
    for k in range(6):
        sp |= spoke_mask(t, 8, 8, k * TAU / 6, 1.0, 4.8, 1.5, 1.3)
    t.put(sp, 'wood', 5.4)
    t.bevel(sp, 0.8, -0.8)
    t.put(t.R(7, 7, 8, 8), 'iron', 5.6)
    t.px(7, 7, 'iron', 7.6)
    t.outline('rubber', 0.6)


@tex('gui/icon_cat_mechanical')
def icon_cat_mechanical(t, rng):
    g = gear_mask(t, 10.5, 10.5, 3.7, 5.3, 8) & ~t.D(10.5, 10.5, 1.5)
    t.put(g, 'brass', 4.8)
    t.bevel(g, 1.4, -1.4)
    shape, grip = diag_wrench(t, sh=2, hv=(15, 16), slot=(15, 16), prong_u=8, back_u=(1, 2), handle_u0=-12)
    t.put(ring_of(shape) & g, 'brass', 1.2)
    t.put(shape, 'iron', 6.0)
    t.bevel(shape, 1.6, -1.8)
    t.outline()


@tex('gui/icon_cat_weapons')
def icon_cat_weapons(t, rng):
    s1 = t.poly(star_pts(8, 8, 7.4, 3.9, 8, math.pi / 8))
    t.put(s1, 'red', 5.6)
    s2 = t.poly(star_pts(8, 8, 5.6, 2.9, 8, 0.0))
    t.put(s2, 'glow', 5.2)
    t.put(t.D(8, 8, 2.5), 'glow', 7.6)
    t.px(7, 7, 'glow', 8.0)
    t.outline('red', 1.0)


@tex('gui/icon_cat_flight')
def icon_cat_flight(t, rng):
    # three-blade wooden propeller with brass-sheathed tips and a steel hub
    blades = t.none()
    L, wmax, skew = 7.4, 2.2, 0.8
    for k in range(3):
        ang = -math.pi / 2 + k * TAU / 3
        ux, uy = math.cos(ang), math.sin(ang)
        dx, dy = t.PX - 8.0, t.PY - 8.0
        s = dx * ux + dy * uy
        w = -dx * uy + dy * ux
        kk = np.clip((s - 1.0) / (L - 1.0), 0, 1)
        hw = 0.6 + wmax * np.sin(kk * math.pi * 0.85) ** 0.7
        b = (s > 0.5) & (s < L) & (np.abs(w - skew * kk) < hw)
        t.put(b, 'lightwood', 5.6)
        t.add(b & (w - skew * kk > 0.2), -1.8)
        t.put(b & (s > L - 1.6), 'brass', 5.6)
        blades |= b
    t.bevel(blades, 0.8, -0.8)
    hub = t.D(8, 8, 2.2)
    t.put(hub, 'steel', 4.8)
    t.bevel(hub, 1.6, -1.6)
    t.px(7, 7, 'steel', 7.0)
    t.outline()


@tex('gui/icon_cat_automation')
def icon_cat_automation(t, rng):
    stick = t.R(7, 7, 8, 14)
    t.put(stick, 'wood', 4.6)
    t.add(t.R(7, 7, 7, 14), 1.0)
    t.add(t.R(8, 7, 8, 14), -0.6)
    t.put(t.R(7, 5, 8, 6), 'redstone', 6.2)
    t.px(7, 5, 'redstone', 8.0)
    t.put(t.R(7, 4, 8, 4), 'redstone', 7.2)
    for (x, y) in ((5, 4), (10, 4), (6, 2), (9, 2), (7, 1)):
        t.px(x, y, 'redstone', 5.0)
    t.outline('redstone', 0.6)


# ---- logo -------------------------------------------------------------------------
BIG_FONT = {
    'B': ["#########.", "##########", "##########", "###....###", "###....###", "#########.",
          "#########.", "###....###", "###....###", "##########", "##########", "#########."],
    'E': ["##########", "##########", "##########", "###.......", "###.......", "########..",
          "########..", "###.......", "###.......", "##########", "##########", "##########"],
    'S': [".#########", "##########", "##########", "###.......", "###.......", "#########.",
          ".#########", ".......###", ".......###", "##########", "##########", "#########."],
    'I': ["####"] * 12,
    'G': [".#########", "##########", "##########", "###.......", "###.......", "###..#####",
          "###..#####", "###....###", "###....###", "##########", "##########", ".#########"],
}
SMALL_FONT = {
    'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    'I': ["###", ".#.", ".#.", ".#.", "###"],
    'N': ["#...#", "##..#", "#.#.#", "#..##", "#...#"],
    'E': ["####", "#...", "###.", "#...", "####"],
    'C': [".###", "#...", "#...", "#...", ".###"],
    'R': ["###.", "#..#", "###.", "#.#.", "#..#"],
    'A': [".##.", "#..#", "####", "#..#", "#..#"],
    'F': ["####", "#...", "###.", "#...", "#..."],
    'T': ["#####", "..#..", "..#..", "..#..", "..#.."],
}


def text_mask(t, text, font, unit, x0, y0, gap):
    m = t.none()
    boxes = []
    x = x0
    for ch in text:
        g = font[ch]
        gw, gh = len(g[0]), len(g)
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == '#':
                    m[y0 + j * unit:y0 + (j + 1) * unit, x + i * unit:x + (i + 1) * unit] = True
        boxes.append((x, y0, gw * unit, gh * unit))
        x += gw * unit + gap
    return m, boxes


def text_width(text, font, unit, gap):
    return sum(len(font[c][0]) * unit for c in text) + gap * (len(text) - 1)


@tex('gui/logo', 256, 64)
def logo(t, rng):
    # --- BESIEGE: heavy riveted iron block letters, extruded down-right ---
    unit, gap = 3, 4
    word = 'BESIEGE'
    x0 = (256 - text_width(word, BIG_FONT, unit, gap)) // 2 - 1
    face, boxes = text_mask(t, word, BIG_FONT, unit, x0, 18, gap)
    ext = t.none()
    for d, (dx, dy) in enumerate(((0, 1), (1, 2), (1, 3), (2, 4))):
        ext |= shift(face, dx, dy)
    ext &= ~face
    t.put(ext, 'iron', 2.8)
    t.add(ext & ~shift(face, 0, 1), -0.6)
    metal_fill(t, rng, face, 'iron', 6.7, 0.35, 0.4, cell=6)
    t.add(face, (0.9 - (t.Y - 18) / 36.0 * 1.8).astype(np.float32))
    t.bevel(face, 1.8, -1.6)
    t.bevel(erode(face), 0.6, -0.5)
    # cracks / scratches
    for _ in range(26):
        x, y = int(rng.integers(0, 256)), int(rng.integers(18, 54))
        if face[y, x] and erode(face)[y, x]:
            ln = int(rng.integers(2, 5))
            dx = int(rng.choice([-1, 1]))
            for i in range(ln):
                xx, yy = x + i * dx, y + i
                if 0 <= xx < 256 and yy < 64 and erode(face)[yy, xx]:
                    t.px(xx, yy, 'iron', 4.0)
    # rivets at letter corners
    for (bx, by, bw, bh) in boxes:
        for (rx, ry) in ((bx + 3, by + 3), (bx + bw - 5, by + 3), (bx + 3, by + bh - 5), (bx + bw - 5, by + bh - 5)):
            if face[ry:ry + 2, rx:rx + 2].all():
                big_rivet(t, rx, ry, 'iron', 5.2, -2.0)
    # --- MINECRAFT: small stone letters ---
    u2, g2 = 2, 2
    w2 = 'MINECRAFT'
    x2 = (256 - text_width(w2, SMALL_FONT, u2, g2)) // 2 - 1
    f2, _ = text_mask(t, w2, SMALL_FONT, u2, x2, 3, g2)
    e2 = (shift(f2, 0, 1) | shift(f2, 1, 2)) & ~f2
    t.put(e2, 'stone', 2.4)
    metal_fill(t, rng, f2, 'stone', 7.0, 0.4)
    t.bevel(f2, 1.2, -1.4)
    # flanking iron bars with rivets
    for (a, b) in ((x2 - 26, x2 - 6), (x2 + text_width(w2, SMALL_FONT, u2, g2) + 6,
                                       x2 + text_width(w2, SMALL_FONT, u2, g2) + 26)):
        bar = t.R(a, 7, b, 8)
        t.put(bar, 'iron', 5.4)
        t.add(t.R(a, 7, b, 7), 1.2)
        t.put(t.R(a, 9, b, 9), 'iron', 2.6)
        for x in (a + 1, b - 2):
            big_rivet(t, x, 7, 'iron', 5.0, 0.0)
    t.outline('rubber', 0.3, diag=True)


# =====================================================================================
# Output, validation, contact sheet
# =====================================================================================
def bleed(rgba):
    """Copy nearest opaque colours into fully transparent pixels (alpha stays 0) so
    mipmapping never pulls in black fringes."""
    a = rgba[..., 3] > 0
    if a.all() or not a.any():
        return rgba
    rgb = rgba[..., :3].astype(np.int32)
    known = a.copy()
    for _ in range(max(rgba.shape[:2])):
        if known.all():
            break
        acc = np.zeros_like(rgb)
        cnt = np.zeros(known.shape, np.int32)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = shift(known, dx, dy)
            acc += shift(rgb, dx, dy, 0) * k[..., None]
            cnt += k
        new = (~known) & (cnt > 0)
        rgb[new] = acc[new] // cnt[new][:, None]
        known |= new
    out = rgba.copy()
    out[..., :3] = rgb.astype(np.uint8)
    return out


def validate(img, w, h, path):
    assert img.shape == (h, w, 4), (path, img.shape)
    alpha = np.unique(img[..., 3])
    assert set(alpha.tolist()) <= {0, 255}, (path, alpha)


def load_font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def contact_sheet(entries, out_path, scale=4, max_w=1640):
    font = load_font(12)
    hfont = load_font(18)
    pad, lab_h = 8, 16
    bg = (128, 128, 128)
    sections = []
    for path, img in entries:
        sec = path.split('/')[0]
        if not sections or sections[-1][0] != sec:
            sections.append((sec, []))
        sections[-1][1].append((path, img))
    placements, y = [], 6
    probe = ImageDraw.Draw(Image.new('RGB', (4, 4)))
    for sec, items in sections:
        placements.append(('header', sec + '/', 10, y))
        y += 28
        x, row_h = pad, 0
        for path, img in items:
            sw, sh = img.width * scale, img.height * scale
            label = os.path.basename(path) + '.png'
            lw = probe.textlength(label, font=font)
            cw = int(max(sw, lw)) + pad * 2
            ch = sh + lab_h + pad * 2
            if x + cw > max_w and x > pad:
                x, y = pad, y + row_h
                row_h = 0
            placements.append(('tex', (path, img, label, cw), x, y))
            x += cw
            row_h = max(row_h, ch)
        y += row_h + 10
    sheet = Image.new('RGB', (max_w, y + 6), bg)
    d = ImageDraw.Draw(sheet)
    for kind, data, x, yy in placements:
        if kind == 'header':
            d.text((x, yy), data, fill=(255, 255, 230), font=hfont)
            d.line((x, yy + 24, max_w - 10, yy + 24), fill=(160, 160, 160))
            continue
        path, img, label, cw = data
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        ox = x + (cw - big.width) // 2
        sheet.paste(big, (ox, yy + pad), big)
        lw = d.textlength(label, font=font)
        d.text((x + (cw - lw) / 2, yy + pad + big.height + 3), label, fill=(20, 20, 20), font=font)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


def main():
    entries = []
    for path, w, h, fn in REG:
        t = Tex(w, h)
        fn(t, np.random.default_rng(seed_for(path)))
        CACHE[path] = t
        img = t.render()
        validate(img, w, h, path)
        out = os.path.join(TEX_ROOT, path + '.png')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        Image.fromarray(bleed(img), 'RGBA').save(out)
        entries.append((path, Image.fromarray(img, 'RGBA')))
    shutil.copyfile(os.path.join(TEX_ROOT, 'gui', 'logo.png'), os.path.join(RES, 'logo.png'))
    contact_sheet(entries, os.path.join(PREVIEW, 'textures_sheet.png'))
    print('generated %d textures (+ resources/logo.png copy)' % len(entries))


if __name__ == '__main__':
    main()
