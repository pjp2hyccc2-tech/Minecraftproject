"""Paintings: mukasari (votive ghost-marriage painting), sayo, family photo, mansion."""
import math

import numpy as np
from PIL import Image, ImageDraw

from common import (Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, dilate, erode, rng,
                    lighten_arr, bresenham, smoothstep, quant_idx, desaturate)
from t_build import paint_idx, BLOOD


def poly(w, h, pts):
    im = Image.new('L', (w, h), 0)
    ImageDraw.Draw(im).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    return np.asarray(im) > 0


def ell(w, h, cx, cy, rx, ry):
    x, y = grid(w, h)
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def shade_fill(t, mask, P, field):
    idx = np.clip(np.round(field), 0, len(P) - 1).astype(int)
    t.a[mask] = np.asarray(P)[idx][mask]


def age(t, seed, crack_n=6, vignette=0.35, soot=0.0, flecks=8, fleck_col='#5a4630'):
    """Darken edges, add cracks in the paint and paint loss."""
    h, w = t.h, t.w
    x, y = grid(w, h)
    dx = (x - (w - 1) / 2) / (w / 2)
    dy = (y - (h - 1) / 2) / (h / 2)
    v = 1 - vignette * smoothstep(0.45, 1.25, np.sqrt(dx ** 2 + dy ** 2))
    if soot:
        v *= 1 - soot * smoothstep(0.6, -1.0, dy)
    t.a[..., :3] *= v[..., None]
    r = rng(seed)
    for _ in range(crack_n):
        x0, y0 = r.integers(0, w), r.integers(0, h)
        ang = r.random() * math.pi
        ln = r.integers(3, 9)
        px_, py_ = float(x0), float(y0)
        for _k in range(ln):
            t.mul(int(px_), int(py_), 0.72, wrap=False)
            ang += (r.random() - 0.5) * 0.9
            px_ += math.cos(ang)
            py_ += math.sin(ang)
    for _ in range(flecks):
        t.set(r.integers(0, w), r.integers(0, h), fleck_col, wrap=False)
    return t


def scratch_out(t, x0, y0, x1, y1, seed, light='#c7b796', dark='#1a120c', n=7, gouge='#8a7656'):
    """Violent scratches over a face: the paint is gouged away, then scored with long strokes."""
    r = rng(seed)
    for yy in range(y0, y1 + 1):
        for xx in range(x0, x1 + 1):
            if r.random() < 0.8:
                t.set(xx, yy, gouge, wrap=False)
    for k in range(n):
        if k % 3 == 2:
            ay, by = r.integers(y0, y1 + 1), r.integers(y0, y1 + 1)
            ax, bx = x0 - 1, x1 + 1
        else:
            ax, bx = r.integers(x0 - 1, x1 + 2), r.integers(x0 - 1, x1 + 2)
            ay, by = y0 - 1 - r.integers(0, 2), y1 + 1 + r.integers(0, 2)
        pts = bresenham(ax, ay, bx, by)
        for i, (px_, py_) in enumerate(pts):
            if 0 <= px_ < t.w and 0 <= py_ < t.h:
                t.set(px_, py_, light, wrap=False)
                if i % 2 == 0 and dark:
                    t.set(px_ + 1, py_, dark, wrap=False) if 0 <= px_ + 1 < t.w else None


# ---------------------------------------------------------------------------
# mukasari ema 32x32
# ---------------------------------------------------------------------------
GROUND = ramp(['#30230f', '#4c391b', '#6a5229', '#836937', '#977d45', '#a68d53'], 6)
WHITE_K = ramp(['#5f5a52', '#7f7a6f', '#9d978a', '#b5af9f', '#c6c0b0', '#d0cab9'], 6)
BLACK_K = pal('#0a090b', '#141216', '#1f1c22', '#2d2930', '#3d3840')
SKIN = pal('#8f8070', '#ad9d86', '#c5b59c', '#d3c5ad')


@texture('painting/mukasari', size=(32, 32), opaque=True)
def mukasari():
    W = H = 32
    t = Tex(W, H)
    x, y = grid(W, H)
    # aged gold ground with leaf squares
    cell = (((x // 6) + (y // 6) * 3) % 5 - 2) * 0.25
    g = 3.4 + fnoise(W, H, 1101, beta=1.6) * 0.5 + cell + (white(W, H, 1102) - 0.5) * 0.6
    shade_fill(t, np.ones((H, W), bool), GROUND, g)
    # floor: red carpet dais
    floor = y >= 27
    FL = ramp(['#2a0b08', '#3e120c', '#521a11'], 3)
    shade_fill(t, floor, FL, 1.4 + (white(W, H, 1103) - 0.5) * 1.2 - (y - 27) * 0.2)
    for xx in range(W):
        t.set(xx, 27, '#64241a' if xx % 3 else '#4e1b13')
    # ---- groom (left) ----
    gx = 10
    haori = poly(W, H, [(gx - 4, 11), (gx + 4, 11), (gx + 6, 18), (gx + 6, 20), (gx - 6, 20), (gx - 6, 18)])
    hak = poly(W, H, [(gx - 5, 19), (gx + 5, 19), (gx + 6, 28), (gx - 6, 28)])
    HAK = pal('#2e2c2b', '#413e3b', '#55514c')
    hv = 1.0 + ((x - gx) % 3 == 0) * 1.0 - ((x - gx) % 3 == 2) * 1.0 + (x > gx + 2) * -0.4
    shade_fill(t, hak, HAK, hv)
    hv2 = 2.0 - (x - gx) * 0.12 + (white(W, H, 1104) - 0.5) * 0.6 - (y < 13) * 0.3
    shade_fill(t, haori, BLACK_K, hv2)
    # sleeves
    for (sx, d) in ((gx - 6, -1), (gx + 6, 1)):
        sl = poly(W, H, [(sx, 13), (sx + d * 2, 14), (sx + d * 2, 21), (sx, 21)])
        shade_fill(t, sl, BLACK_K, np.full((H, W), 1.0 if d < 0 else 0.6))
    # white family crests and the haori tie
    for (cx_, cy_) in ((gx - 3, 13), (gx + 3, 13), (gx - 6, 15), (gx + 6, 15)):
        t.set(cx_, cy_, '#b9b2a2')
    t.set(gx, 16, '#c6c0b0')
    t.set(gx, 17, '#c6c0b0')
    t.set(gx - 1, 17, '#a39d8f')
    t.set(gx + 1, 17, '#a39d8f')
    # collar V
    for k in range(4):
        t.set(gx - 1 - k // 2, 11 + k, '#8f8a7e')
        t.set(gx + k // 2, 11 + k, '#8f8a7e')
    # hand holding a closed fan
    t.set(gx + 3, 19, SKIN[2])
    t.set(gx + 4, 18, '#c6c0b0')
    t.set(gx + 5, 17, '#c6c0b0')
    # tabi feet
    for xx in (gx - 3, gx - 2, gx + 2, gx + 3):
        t.set(xx, 28, '#b8b1a1')
    # head: short black hair, the face is scratched out
    face = ell(W, H, gx, 8.0, 2.6, 3.0)
    shade_fill(t, face, SKIN, 2.4 - (x - gx) * 0.3)
    hair = ell(W, H, gx, 6.0, 3.0, 2.2) & (y <= 6)
    t.a[hair] = BLACK_K[1]
    t.set(gx - 1, 10, SKIN[1])
    t.set(gx, 10, SKIN[1])
    # the face has been scratched out with a blade: dark smudge, pale cross-hatched gouges
    for yy in range(6, 11):
        for xx in range(gx - 2, gx + 3):
            if face[yy, xx] or yy == 6:
                t.set(xx, yy, '#3a2a1c' if (xx + yy) % 2 else '#2a1e14')
    for (a, b, c, d) in ((gx - 2, 5, gx + 2, 10), (gx - 1, 5, gx + 2, 9), (gx + 2, 6, gx - 2, 10),
                         (gx + 1, 5, gx - 2, 8), (gx - 2, 8, gx + 2, 8)):
        for (px_, py_) in bresenham(a, b, c, d):
            t.set(px_, py_, '#d2c3a0' if (px_ + py_) % 3 else '#a8977a')
    # ---- bride (right) ----
    bx = 22
    body = poly(W, H, [(bx - 4, 11), (bx + 4, 11), (bx + 6, 22), (bx + 7, 28), (bx - 7, 28), (bx - 6, 22)])
    bv = 3.6 - (x - bx) * 0.18 + np.sin(y * 0.9 + x * 0.3) * 0.35 + (white(W, H, 1106) - 0.5) * 0.5
    shade_fill(t, body, WHITE_K, bv)
    # embroidered cranes as faint grey lines
    for (px_, py_) in ((bx - 3, 19), (bx - 2, 18), (bx - 1, 19), (bx + 2, 23), (bx + 3, 22), (bx + 4, 23),
                       (bx + 1, 15), (bx + 2, 14)):
        t.set(px_, py_, WHITE_K[2])
    # red lining at the padded hem
    for xx in range(bx - 7, bx + 8):
        t.set(xx, 28, '#6e2219' if xx % 4 else '#5a1a13')
    # hands and hakoseko (red/gold case) at the chest
    t.set(bx - 1, 16, SKIN[2])
    t.set(bx, 16, SKIN[2])
    t.set(bx, 15, '#7a2a1c')
    t.set(bx + 1, 15, '#8a6a30')
    t.set(bx + 1, 14, '#7a2a1c')
    # collar layers (white over red)
    for k in range(4):
        t.set(bx - 1 - k // 2, 11 + k, WHITE_K[5])
        t.set(bx + k // 2, 11 + k, WHITE_K[5])
    t.set(bx - 2, 12, '#5e1d16')
    t.set(bx + 1, 12, '#5e1d16')
    # wataboshi hood
    hood = ell(W, H, bx, 7.0, 4.6, 4.6) & (y <= 11)
    shade_fill(t, hood, WHITE_K, 4.4 - np.sqrt((x - bx + 2) ** 2 + (y - 4) ** 2) * 0.35)
    bface = ell(W, H, bx - 0.5, 9.5, 2.0, 2.3) & (y >= 8)
    shade_fill(t, bface, SKIN, 3.0 - (x - bx) * 0.3)
    t.set(bx - 2, 9, '#2a2220')       # downcast eyes
    t.set(bx, 9, '#2a2220')
    t.set(bx - 1, 11, '#7e2419')       # red lips
    # hood rim shadow
    for xx in range(bx - 3, bx + 3):
        t.set(xx, 8, WHITE_K[2])
    # ---- inscriptions: names and date in faded ink ----
    r = rng(1107)
    for yy in range(2, 24):
        if r.random() < 0.7:
            t.set(1, yy, '#2a1e12')
        if r.random() < 0.6 and yy < 18:
            t.set(30, yy, '#2a1e12')
    t.set(30, 20, '#7a2a1c')
    t.set(30, 21, '#7a2a1c')
    age(t, 1108, crack_n=9, vignette=0.45, soot=0.25, flecks=10)
    return t


# ---------------------------------------------------------------------------
# sayo 16x32
# ---------------------------------------------------------------------------
@texture('painting/sayo', size=(16, 32), opaque=True)
def sayo():
    W, H = 16, 32
    t = Tex(W, H)
    x, y = grid(W, H)
    BG = ramp(['#070506', '#0e0a0a', '#16100e', '#201713', '#2b1f19'], 5)
    bg = 2.2 + fnoise(W, H, 1201, beta=1.8) * 0.6 - np.abs(x - 7.5) * 0.12 + (y > 24) * 0.4
    shade_fill(t, np.ones((H, W), bool), BG, bg)
    # floor line
    for xx in range(W):
        t.set(xx, 29, BG[3])
    cx = 7.5
    body = poly(W, H, [(cx - 3, 9), (cx + 3, 9), (cx + 4.5, 20), (cx + 5.5, 30), (cx - 5.5, 30), (cx - 4.5, 20)])
    bv = 3.7 - (x - cx) * 0.3 + np.sin(y * 0.8 + x * 0.5) * 0.3 + (white(W, H, 1202) - 0.5) * 0.4
    shade_fill(t, body, WHITE_K, bv)
    # long hanging sleeves
    for (sx, d) in ((cx - 4.5, -1), (cx + 4.5, 1)):
        sl = poly(W, H, [(sx, 11), (sx + d * 1.5, 12), (sx + d * 1.5, 23), (sx - d * 0.5, 23)])
        shade_fill(t, sl, WHITE_K, np.full((H, W), 2.6 if d < 0 else 1.8))
    # long pale hands with too-long fingers
    for (hx, d) in ((2, 0), (13, 0)):
        for k in range(4):
            t.set(hx, 23 + k, SKIN[1] if k < 3 else SKIN[0])
        t.set(hx + (1 if hx < 8 else -1), 23, SKIN[2])
        t.set(hx + (1 if hx < 8 else -1), 24, SKIN[1])
    t.set(2, 27, SKIN[0])
    t.set(13, 27, SKIN[0])
    # hem with the red lining
    for xx in range(2, 14):
        t.set(xx, 30, '#5a1a13' if xx % 3 else '#6e2219')
    # obi (white brocade, slightly darker)
    for xx in range(4, 12):
        t.set(xx, 15, WHITE_K[2])
        t.set(xx, 16, WHITE_K[3] if xx % 2 else WHITE_K[2])
        t.set(xx, 17, WHITE_K[2])
    # head: long black hair hanging over the face down to the chest
    HAIR = pal('#040304', '#0b090b', '#151216', '#221f25')
    hair = (ell(W, H, cx, 6.0, 3.0, 3.4) | poly(W, H, [(cx - 3, 6), (cx + 3, 6), (cx + 3.6, 15),
                                                          (cx + 1, 17), (cx - 1, 17), (cx - 3.6, 15)]))
    hv = 1.0 + ((x.astype(int) % 2) == 0) * 0.6 + (white(W, H, 1203) - 0.5) * 0.8
    shade_fill(t, hair, HAIR, hv)
    for yy in range(4, 16, 3):
        t.set(int(cx) - 1, yy, HAIR[3])
    # one eye between the strands
    t.set(8, 8, '#cfc6b2')
    t.set(8, 9, '#a69c88')
    t.set(9, 8, '#050404')
    # the pale shoulder line under the hair
    t.set(4, 10, WHITE_K[4])
    t.set(11, 10, WHITE_K[3])
    age(t, 1204, crack_n=5, vignette=0.4, flecks=4, fleck_col='#2b2219')
    return t


# ---------------------------------------------------------------------------
# family photo 32x16 (sepia)
# ---------------------------------------------------------------------------
SEPIA = ramp(['#1e150e', '#35261a', '#4f3c2a', '#6b543c', '#87704f', '#a28a66', '#b9a27c', '#c9b48e'], 8)


@texture('painting/family', size=(32, 16), opaque=True)
def family():
    W, H = 32, 16
    t = Tex(W, H)
    x, y = grid(W, H)
    # studio backdrop (light, mottled) + darker floor
    v = 5.3 + fnoise(W, H, 1301, beta=1.8) * 0.5 - (y >= 12) * 2.2 - np.abs(x - 15.5) * 0.05
    shade_fill(t, np.ones((H, W), bool), SEPIA, v)
    people = [(5, 5, 'father'), (11, 6, 'mother'), (16, 3, 'sayo'), (21, 6, 'grandmother'), (27, 4, 'son')]
    for (cx, hy, kind) in people:
        if kind == 'father':        # seated, dark western suit, white collar
            body = poly(W, H, [(cx - 2, hy + 2), (cx + 2, hy + 2), (cx + 3, hy + 9), (cx - 3, hy + 9)])
            shade_fill(t, body, SEPIA, np.full((H, W), 1.2) - (x > cx) * 0.7)
            t.set(cx, hy + 2, SEPIA[7])
            t.set(cx, hy + 3, SEPIA[6])
        elif kind == 'mother':      # seated kimono, mid tone
            body = poly(W, H, [(cx - 2, hy + 2), (cx + 2, hy + 2), (cx + 3, hy + 8), (cx - 3, hy + 8)])
            shade_fill(t, body, SEPIA, np.full((H, W), 3.3) - (x > cx) * 0.8)
            t.set(cx - 1, hy + 2, SEPIA[7])
            t.set(cx, hy + 3, SEPIA[7])
            for xx in range(cx - 2, cx + 3):
                t.set(xx, hy + 5, SEPIA[1])
        elif kind == 'sayo':        # standing, pale furisode with long sleeves
            body = poly(W, H, [(cx - 2, hy + 2), (cx + 2, hy + 2), (cx + 3, hy + 11), (cx - 3, hy + 11)])
            shade_fill(t, body, SEPIA, np.full((H, W), 6.4) - (x > cx) * 0.9)
            for k in range(3, 9):
                t.set(cx - 3, hy + k, SEPIA[5])
                t.set(cx + 3, hy + k, SEPIA[4])
            for xx in range(cx - 2, cx + 3):
                t.set(xx, hy + 5, SEPIA[3])
        elif kind == 'grandmother':  # dark kimono
            body = poly(W, H, [(cx - 2, hy + 2), (cx + 2, hy + 2), (cx + 3, hy + 8), (cx - 3, hy + 8)])
            shade_fill(t, body, SEPIA, np.full((H, W), 1.8) - (x > cx) * 0.6)
            t.set(cx, hy + 3, SEPIA[5])
        else:                        # boy in a black school uniform
            body = poly(W, H, [(cx - 2, hy + 2), (cx + 2, hy + 2), (cx + 2, hy + 9), (cx - 2, hy + 9)])
            shade_fill(t, body, SEPIA, np.full((H, W), 0.7) - (x > cx) * 0.4)
            for yy in (hy + 3, hy + 5, hy + 7):
                t.set(cx, yy, SEPIA[5])
        head = ell(W, H, cx, hy, 1.3, 1.6)
        shade_fill(t, head, SEPIA, np.full((H, W), 6.0) - (x > cx) * 1.2)
        hair = ell(W, H, cx, hy - 1.1, 1.5, 0.9)
        t.a[hair] = SEPIA[0]
        if kind in ('mother', 'grandmother', 'sayo'):
            t.set(cx, hy - 2, SEPIA[0])
        if kind == 'sayo':
            t.set(cx - 1, hy - 1, SEPIA[0])
            t.set(cx + 1, hy - 1, SEPIA[0])
            t.set(cx - 1, hy, '#120b08')       # the only face left: she looks straight at you
            t.set(cx + 1, hy, '#120b08')
            t.set(cx, hy + 1, SEPIA[4])
        else:
            # scratched off the emulsion: a pale blot with strokes beyond it
            for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1),
                             (0, -2), (0, 2)):
                t.set(cx + dx, hy + dy, '#ddd0b0' if (dx + dy) % 2 else '#cdbd98', wrap=False)
            r = rng(1310 + cx)
            for _ in range(2):
                a = (cx + r.integers(-2, 3), hy - 2 - r.integers(0, 2))
                b = (cx + r.integers(-2, 3), hy + 2 + r.integers(0, 2))
                for (px_, py_) in bresenham(a[0], a[1], b[0], b[1]):
                    t.set(px_, py_, '#e2d6b8', wrap=False)
    # chair legs, a vase on a stand
    t.set(3, 14, SEPIA[1])
    t.set(8, 14, SEPIA[1])
    for yy in (10, 11, 12, 13):
        t.set(31 - 1, yy, SEPIA[2] if yy > 10 else SEPIA[3])
    # a pale blurred figure standing at the back between the parents
    for (px_, py_, a) in ((8, 1, .40), (8, 2, .35), (9, 2, .25), (8, 3, .3), (8, 4, .25), (9, 3, .2),
                          (8, 5, .18), (7, 3, .15)):
        t.blend(px_, py_, '#f0e2c0', a, wrap=False)
    age(t, 1311, crack_n=3, vignette=0.6, flecks=0)
    r = rng(1312)
    for _ in range(9):
        t.blend(r.integers(0, W), r.integers(0, H), '#5a3a1e', 0.5, wrap=False)
    for xx in range(W):
        t.blend(xx, 0, '#d8c8a2', 0.55, wrap=False)
        t.blend(xx, H - 1, '#d8c8a2', 0.5, wrap=False)
    for yy in range(H):
        t.blend(0, yy, '#d8c8a2', 0.55, wrap=False)
        t.blend(W - 1, yy, '#d8c8a2', 0.5, wrap=False)
    for (px_, py_) in ((31, 0), (30, 0), (31, 1)):
        t.set(px_, py_, '#2a1e14')
    for (px_, py_) in bresenham(19, 0, 23, 15):
        t.blend(px_, py_, '#e0cfa8', 0.3, wrap=False)
    return t


# ---------------------------------------------------------------------------
# mansion under the red moon 32x16
# ---------------------------------------------------------------------------
@texture('painting/mansion', size=(32, 16), opaque=True)
def mansion():
    W, H = 32, 16
    t = Tex(W, H)
    x, y = grid(W, H)
    SKY = ramp(['#080304', '#130507', '#20080a', '#310d0e', '#461412', '#5c1d17', '#6e2619'], 7)
    sv = 0.6 + smoothstep(0, 12, y) * 4.2 + fnoise(W, H, 1401, beta=2.0, sx=2.5, sy=1.0) * 0.4
    dm = np.sqrt((x - 24.5) ** 2 + ((y - 3.5) * 1.1) ** 2)
    sv += smoothstep(8, 2.5, dm) * 1.5
    shade_fill(t, np.ones((H, W), bool), SKY, sv)
    MO = ramp(['#651811', '#842417', '#a0331f', '#b4452a'], 4)
    moon = dm <= 3.0
    shade_fill(t, moon, MO, 3.0 - np.sqrt((x - 23.6) ** 2 + (y - 2.6) ** 2) * 0.5)
    for (px_, py_) in ((25, 4), (24, 5), (25, 3), (23, 4)):
        t.set(px_, py_, MO[1])
    for xx in range(20, 30):
        if xx % 4:
            t.blend(xx, 5, '#1a0708', 0.5, wrap=False)
    SIL = pal('#030202', '#060304', '#0a0506', '#120a0a')
    upper_roof = poly(W, H, [(7.5, 7.6), (10.5, 5.0), (19.5, 5.0), (22.5, 7.6), (21.5, 7.2), (8.5, 7.2)])
    upper_wall = poly(W, H, [(10, 7), (20, 7), (20, 10), (10, 10)])
    lower_roof = poly(W, H, [(2.5, 11.4), (7.0, 9.0), (23.0, 9.0), (27.5, 11.4), (26.5, 11.0), (3.5, 11.0)])
    lower_wall = poly(W, H, [(4, 11), (26, 11), (26, 16), (4, 16)])
    house = upper_roof | upper_wall | lower_roof | lower_wall
    t.a[house] = SIL[1]
    t.a[lower_wall] = SIL[2]
    for (px_, py_) in ((10, 4), (19, 4), (7, 7), (22, 7), (2, 11), (27, 11)):
        t.set(px_, py_, SIL[0])                       # ridge ends and upturned eaves
    for xx in range(11, 19):
        t.set(xx, 5, '#2a0d0c')                       # ridge catching the moon
    for xx in range(7, 23):
        t.set(xx, 9, '#1c0909')
    # storehouse with pale plaster walls
    kura = poly(W, H, [(27, 10), (31, 10), (31, 16), (27, 16)])
    kroof = poly(W, H, [(26, 10.6), (27.5, 8.6), (30.5, 8.6), (32, 10.6)])
    t.a[kura] = C('#241e1d')
    t.a[kura & (x >= 30)] = C('#1a1615')
    t.a[kroof] = SIL[0]
    t.set(29, 12, '#070404')
    # pine silhouette
    pine = np.zeros((H, W), bool)
    for (cx_, cy_, rx_) in ((2.0, 4.0, 2.0), (3.0, 6.5, 3.0), (1.5, 9.0, 2.4)):
        pine |= ell(W, H, cx_, cy_, rx_, 0.9)
    pine |= poly(W, H, [(2, 5), (3, 5), (3.6, 16), (1.8, 16)])
    t.a[pine] = SIL[0]
    # a lit window upstairs with someone standing in it; dim shoji below
    t.set(14, 8, '#7a4a1c')
    t.set(15, 8, '#8f5a22')
    t.set(14, 9, '#6a3e18')
    t.set(15, 9, '#7a4a1c')
    t.set(15, 8, '#0a0505')
    t.set(15, 9, '#0c0606')
    for xx in (8, 9, 20, 21):
        t.set(xx, 13, '#2c160c')
    t.set(13, 13, '#050303')
    t.set(13, 14, '#050303')
    # low mist
    for yy in range(12, 16):
        for xx in range(W):
            a = (yy - 11) * 0.10 * (0.6 + 0.4 * math.sin(xx * 0.7 + yy))
            t.blend(xx, yy, '#3e2522', a, wrap=False)
    age(t, 1402, crack_n=3, vignette=0.3, flecks=0)
    return t
