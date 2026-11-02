"""Block-model surface textures: furniture, ritual objects, props.

These are mapped with uv [0,0,16,16] on every face of simple box models, so each is a
complete 16x16 surface of one material / face.
"""
import math

import numpy as np

from common import (Tex, texture, ramp, pal, C, fnoise, vnoise, white, quant_idx, grid, blob, dilate,
                    erode, smoothstep, rng, lighten_arr, bresenham)
from t_build import (paint_idx, LACQ, PAPER, IRON, RUST, GOLD, BLOOD, VERM, _paper, draw_map)

KEYAKI = ramp(['#140d0a', '#22160f', '#321f15', '#432b1d', '#563826', '#664630'], 6)
GOLDF = ramp(['#2e220f', '#4c3a1a', '#6d5627', '#8f7536', '#ab9049', '#c2a85e', '#d3bd77'], 7)


def wood(seed, P, vertical=False, base=None, amp=0.8, w=16, h=16):
    t = Tex(w, h)
    n = len(P)
    base = (n - 1) * 0.5 if base is None else base
    g = fnoise(w, h, seed, beta=1.3, sx=0.8 if vertical else 6.0, sy=6.0 if vertical else 0.8)
    v = base + g * amp + (white(w, h, seed + 1) - 0.5) * 0.6
    paint_idx(t, np.clip(np.round(v), 0, n - 1).astype(int), P)
    return t


# ---------------------------------------------------------------------------
# butsudan (black lacquer Buddhist altar, gold inside)
# ---------------------------------------------------------------------------
@texture('block/butsudan_front', opaque=True)
def butsudan_front():
    t = Tex(16)
    x, y = grid(16, 16)
    lq = 1.3 + fnoise(16, 16, 700, beta=2.0) * 0.3 + np.exp(-((x - 1.0) ** 2) / 0.8) * 1.2
    paint_idx(t, np.clip(np.round(lq), 0, 5).astype(int), LACQ)
    # recessed gilded shrine, deep in shadow towards the back/top
    inner = (x >= 3) & (x <= 12) & (y >= 3) & (y <= 12)
    depth = 1.6 + (y - 3) * 0.22 - (np.abs(x - 7.5) > 3.5) * 0.8 + (white(16, 16, 701) - 0.5) * 0.8
    gi = np.clip(np.round(depth), 0, 6).astype(int)
    t.a[inner] = GOLDF[gi][inner]
    # canopy fringe (yoraku)
    for xx in range(3, 13):
        t.set(xx, 3, GOLDF[4] if xx % 2 else GOLDF[2])
        if xx % 2:
            t.set(xx, 4, GOLDF[5])
    # small seated Amida silhouette with halo behind the tablet
    halo = (np.sqrt((x - 7.5) ** 2 + (y - 6.0) ** 2) < 2.4) & inner
    t.a[halo] = GOLDF[4]
    # the memorial tablet (ihai) - black lacquer, gold rim
    for yy in range(5, 11):
        t.set(7, yy, LACQ[0])
        t.set(8, yy, LACQ[1])
    t.set(7, 4, GOLDF[6])
    t.set(8, 4, GOLDF[5])
    t.set(7, 7, GOLDF[3])
    for xx in (6, 9):
        t.set(xx, 10, GOLDF[5])
    # stepped altar with offerings
    for xx in range(3, 13):
        t.set(xx, 11, GOLDF[6] if xx % 3 else GOLDF[5])
        t.set(xx, 12, GOLDF[3])
    t.set(4, 10, '#2a221c')       # incense bowl
    t.set(5, 10, '#3a3028')
    t.set(4, 9, '#5a5248')        # ash stick
    t.set(11, 10, '#bfb59c')      # unlit candle
    t.set(11, 9, '#bfb59c')
    # trim lines and cornice
    for i in range(16):
        t.set(i, 0, LACQ[3] if i % 5 else LACQ[4])
        t.set(i, 1, LACQ[2])
        t.set(i, 2, GOLDF[3] if 2 <= i <= 13 else LACQ[1])
        t.set(i, 13, GOLDF[3] if 2 <= i <= 13 else LACQ[1])
    for yy in range(2, 14):
        t.set(2, yy, GOLDF[3])
        t.set(13, yy, GOLDF[2])
    # hinge fittings on the folded doors
    for yy in (4, 10):
        t.set(0, yy, GOLDF[5])
        t.set(15, yy, GOLDF[4])
    # base drawer
    for xx in range(16):
        t.set(xx, 14, LACQ[2])
        t.set(xx, 15, LACQ[0])
    t.set(7, 14, GOLDF[5])
    t.set(8, 14, GOLDF[3])
    for (dx, dy) in ((10, 5), (4, 6), (12, 9)):
        t.a[dy, dx, :3] *= 0.7
    return t


@texture('block/butsudan_side', opaque=True)
def butsudan_side():
    t = Tex(16)
    x, y = grid(16, 16)
    low = fnoise(16, 16, 711, beta=2.0, sx=1.0, sy=3.0)
    v = 1.4 + low * 0.35 + np.exp(-((x - 4.0) ** 2) / 1.5) * 1.8 + (white(16, 16, 712) < 0.05) * 1
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), LACQ)
    for i in range(1, 15):
        t.set(i, 1, GOLDF[3])
        t.set(i, 14, GOLDF[2])
        t.set(1, i, GOLDF[3])
        t.set(14, i, GOLDF[2])
    # corner fittings (kanagu)
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for k in range(3):
            t.set(cx + sx * k, cy, GOLDF[5 - k])
            t.set(cx, cy + sy * k, GOLDF[5 - k])
    # dust settled along the bottom trim and a drip of wax
    for (dx, dy) in ((5, 13), (9, 13), (10, 12), (12, 13)):
        t.set(dx, dy, LACQ[4])
    return t


@texture('block/butsudan_top', opaque=True)
def butsudan_top():
    t = Tex(16)
    v = 1.3 + fnoise(16, 16, 721, beta=2.0) * 0.35
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), LACQ)
    x, y = grid(16, 16)
    # dust layer + a clean ring where an incense bowl used to stand
    dust = white(16, 16, 722) < 0.22
    t.a[dust] = LACQ[3]
    d = np.sqrt((x - 9.5) ** 2 + (y - 8.5) ** 2)
    ring = (d > 2.2) & (d < 3.3)
    t.a[ring] = LACQ[1]
    for i in range(16):
        t.set(i, 0, GOLDF[3])
        t.set(i, 15, GOLDF[2])
        t.set(0, i, GOLDF[3])
        t.set(15, i, GOLDF[2])
    t.set(4, 5, '#6b6058')                 # spilled incense ash
    t.set(5, 5, '#5a514a')
    t.set(4, 6, '#4b443f')
    return t


# ---------------------------------------------------------------------------
# candle stand
# ---------------------------------------------------------------------------
@texture('block/candle_wax', opaque=True)
def candle_wax():
    W = ramp(['#7d7564', '#9a917d', '#b0a790', '#c2b9a1', '#d0c8b0'], 5)
    t = Tex(16)
    x, y = grid(16, 16)
    g = fnoise(16, 16, 731, beta=1.4, sx=0.6, sy=5.0)
    v = 2.6 + g * 0.7 + (white(16, 16, 732) - 0.5) * 0.5
    v += np.where(x == 0, -0.8, 0) + np.where(x == 15, -1.0, 0) + np.where(x == 4, 0.8, 0)
    paint_idx(t, np.clip(np.round(v), 0, 4).astype(int), W)
    # drips running down from the rim
    r = rng(733)
    for dx in (2, 6, 9, 13):
        ln = int(r.integers(4, 11))
        for k in range(ln):
            t.set(dx, k, W[4] if k < ln - 1 else W[3])
            t.set(dx + 1, k, W[2])
        t.set(dx, ln, W[1])
    # soot near the top and a dark wick-stain
    for xx in range(16):
        t.a[0, xx, :3] *= 0.85
    return t


def _metal(seed, P, rings=True):
    t = Tex(16)
    x, y = grid(16, 16)
    n = len(P)
    v = (n - 1) * 0.45 + np.exp(-((x - 4.5) ** 2) / 2.0) * 2.4 - np.exp(-((x - 13.0) ** 2) / 3.0) * 1.0
    v += fnoise(16, 16, seed, beta=1.6, sx=0.6, sy=4.0) * 0.45
    if rings:
        v += np.where(y % 5 == 0, 1.0, 0) + np.where(y % 5 == 1, -1.1, 0)
    paint_idx(t, np.clip(np.round(v), 0, n - 1).astype(int), P)
    return t


@texture('block/candle_gold', opaque=True)
def candle_gold():
    P = ramp(['#20160a', '#3a2a12', '#5a4520', '#7c6230', '#9c8042', '#b89c58', '#cdb574'], 7)
    t = _metal(741, P)
    # green-brown patina in the recesses
    r = rng(742)
    for _ in range(9):
        px_, py_ = int(r.integers(0, 16)), int(r.integers(0, 16))
        if (py_ % 5) in (1, 2) or px_ > 10:
            t.blend(px_, py_, '#3e4a2c', 0.55)
    return t


@texture('block/candle_silver', opaque=True)
def candle_silver():
    P = ramp(['#121315', '#24262a', '#3b3e43', '#565a5f', '#73777c', '#8f9398', '#a8acb0'], 7)
    t = _metal(751, P)
    tarn = blob(16, 16, 11, 10, 4, 5, 752, rough=0.6) > 0
    t.a[tarn] = lighten_arr(t.a[tarn], -0.08)
    t.a[tarn, :3] *= np.array([1.0, 0.97, 0.9])
    return t


@texture('block/candle_black', opaque=True)
def candle_black():
    P = ramp(['#060506', '#0f0d0e', '#1a1719', '#272325', '#363134', '#4a4447'], 6)
    t = _metal(761, P)
    r = rng(762)
    for _ in range(10):
        t.set(int(r.integers(0, 16)), int(r.integers(0, 16)), RUST[int(r.integers(0, 3))])
    return t


@texture('block/candle_flame', cutout=True)
def candle_flame():
    t = Tex(16)
    F = {'o': '#a8481c', 'O': '#d0742a', 'y': '#eaa843', 'Y': '#f6d27c', 'w': '#fbf0cd', 'b': '#33406a',
         'B': '#4b5d8c'}
    flame = [
        ".......o........",
        ".......o........",
        "......oO........",
        "......OO........",
        "......OyO.......",
        ".....oyYO.......",
        ".....OyYyo......",
        ".....OYwYO......",
        ".....yYwYy......",
        ".....OYwYO......",
        ".....oYwYo......",
        "......yYy.......",
        "......bBb.......",
        ".......b........",
    ]
    draw_map(t, flame, 0, 2, colors={k: C(v) for k, v in F.items()}, wrap=False)
    return t


# ---------------------------------------------------------------------------
# andon (paper floor lamp)
# ---------------------------------------------------------------------------
ANDON_FRAME = ramp(['#120d0a', '#21180f', '#322419', '#433225'], 4)


def _andon_side(lit):
    t = Tex(16)
    x, y = grid(16, 16)
    fib = fnoise(16, 16, 771, beta=0.7, sx=2.0, sy=0.7)
    if lit:
        P = ramp(['#5a2a12', '#86411a', '#ad6224', '#cc8a38', '#e3b058', '#f0cf84', '#f6e3ac'], 7)
        d = np.sqrt(((x - 7.5) / 1.0) ** 2 + ((y - 9.5) / 1.25) ** 2)
        v = 0.95 - d * 0.085 + fib * 0.04
    else:
        P = ramp(['#3e3a34', '#4e4942', '#5e5850', '#6d675e', '#7b746a', '#878075'], 6)
        v = 0.62 - np.abs(y - 8) * 0.012 + fib * 0.08 + fnoise(16, 16, 772, beta=1.8) * 0.06
    idx = quant_idx(v, len(P), dither=0.9)
    paint_idx(t, idx, P)
    # paper stains: a water mark and a moth smudge
    st = blob(16, 16, 11.0, 4.0, 2.4, 1.6, 773, rough=0.5, wrap=False) > 0
    t.a[st, :3] *= np.array([0.86, 0.80, 0.72])
    rim = st & ~erode(st, 1)
    t.a[rim, :3] *= 0.9
    t.set(5, 6, '#2b1f17' if lit else '#2a2620')
    t.set(4, 6, '#4a2c1a' if lit else '#3e3a33')
    # thin inner kumiko bar + frame
    for xx in range(16):
        t.set(xx, 5, ANDON_FRAME[2] if xx % 7 else ANDON_FRAME[3])
    for i in range(16):
        t.set(i, 0, ANDON_FRAME[3])
        t.set(i, 15, ANDON_FRAME[0])
        t.set(0, i, ANDON_FRAME[2])
        t.set(15, i, ANDON_FRAME[1])
    t.set(0, 0, ANDON_FRAME[3])
    if lit:
        # light leaking at the frame edges
        for i in range(2, 14):
            t.blend(i, 14, '#e3b058', 0.25)
    return t


@texture('block/andon_side', opaque=True)
def andon_side():
    return _andon_side(True)


@texture('block/andon_side_off', opaque=True)
def andon_side_off():
    return _andon_side(False)


@texture('block/andon_top', opaque=True)
def andon_top():
    t = Tex(16)
    x, y = grid(16, 16)
    t.a[:] = C('#0d0a08')
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    dish = d < 3.2
    t.a[dish] = C('#3a2a1d')
    t.a[(d < 2.2)] = C('#1d1610')          # oil
    t.a[(d >= 2.6) & (d < 3.2) & (y < 7)] = C('#55402b')
    t.set(8, 7, '#6a5030')                  # wick
    t.set(9, 7, '#2a1c12')
    # crossbars and frame
    for i in range(16):
        for (a, b) in ((0, 1), (15, 14)):
            t.set(i, a, ANDON_FRAME[3 if a == 0 else 1])
            t.set(i, b, ANDON_FRAME[2])
            t.set(a, i, ANDON_FRAME[3 if a == 0 else 1])
            t.set(b, i, ANDON_FRAME[2])
    for i in range(2, 14):
        t.set(i, 11, ANDON_FRAME[1])
    return t


# ---------------------------------------------------------------------------
# tansu (chest of drawers)
# ---------------------------------------------------------------------------
def _iron_handle(t, cx, y0):
    """Bow-shaped warabite handle centred at cx (two px wide)."""
    t.set(cx - 2, y0 + 1, IRON[1])
    t.set(cx - 1, y0, IRON[3])
    t.set(cx, y0, IRON[3])
    t.set(cx + 1, y0, IRON[2])
    t.set(cx + 2, y0 + 1, IRON[1])
    t.set(cx - 1, y0 + 1, IRON[0])
    t.set(cx + 1, y0 + 1, IRON[0])


@texture('block/tansu_front', opaque=True)
def tansu_front():
    t = wood(781, KEYAKI, base=3.0, amp=0.7)
    gap = KEYAKI[0]
    for xx in range(16):
        t.set(xx, 0, KEYAKI[4])
        t.set(xx, 5, gap)
        t.set(xx, 10, gap)
        t.set(xx, 15, gap)
        t.set(xx, 6, KEYAKI[4]) if 1 <= xx <= 14 else None
        t.set(xx, 11, KEYAKI[4]) if 1 <= xx <= 14 else None
        t.set(xx, 1, KEYAKI[4]) if 1 <= xx <= 14 else None
    for yy in range(16):
        t.set(0, yy, KEYAKI[2])
        t.set(15, yy, KEYAKI[1])
    for yy in range(1, 5):
        t.set(7, yy, gap)
        t.set(8, yy, KEYAKI[4])
    # iron corner plates on every drawer
    for (x0, x1, y0, y1) in ((1, 6, 1, 4), (8, 14, 1, 4), (1, 14, 6, 9), (1, 14, 11, 14)):
        for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            t.set(cx, cy, IRON[2])
    # handles
    _iron_handle(t, 4, 2)
    _iron_handle(t, 11, 2)
    _iron_handle(t, 5, 7)
    _iron_handle(t, 11, 7)
    _iron_handle(t, 5, 12)
    _iron_handle(t, 11, 12)
    # lock plate with keyhole on the middle drawer
    for (dx, dy) in ((7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)):
        t.set(dx, dy, IRON[2])
    t.set(7, 8, '#030303')
    t.set(8, 7, IRON[4])
    # rust bleeding + a drawer slightly open (shadow line)
    for (rx, ry) in ((4, 4), (11, 9), (5, 14)):
        t.blend(rx, ry, RUST[1], 0.6)
    for xx in range(1, 15):
        t.set(xx, 10, '#050303')
    return t


@texture('block/tansu_side', opaque=True)
def tansu_side():
    t = wood(791, KEYAKI, base=2.8, amp=0.8)
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for k in range(3):
            t.set(cx + sx * k, cy, IRON[2] if k < 2 else IRON[1])
            t.set(cx, cy + sy * k, IRON[2] if k < 2 else IRON[1])
        t.set(cx + sx, cy + sy, IRON[1])
    # carrying-handle fitting
    for (dx, dy) in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (9, 7)):
        t.set(dx, dy, IRON[2])
    t.set(7, 7, IRON[0])
    t.set(8, 7, IRON[0])
    for xx in range(5, 11):
        t.set(xx, 8, IRON[3] if xx in (5, 10) else IRON[1])
    t.blend(7, 9, RUST[1], 0.5)
    t.blend(7, 10, RUST[0], 0.3)
    return t


@texture('block/tansu_top', opaque=True)
def tansu_top():
    t = wood(801, KEYAKI, base=3.2, amp=0.8)
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for k in range(3):
            t.set(cx + sx * k, cy, IRON[2])
            t.set(cx, cy + sy * k, IRON[2])
    dust = white(16, 16, 802) < 0.10
    t.a[dust] = lighten_arr(t.a[dust], 0.06)
    for (px_, py_) in bresenham(4, 9, 9, 7):
        t.set(px_, py_, KEYAKI[5])
    return t


# ---------------------------------------------------------------------------
# kamado (clay stove)
# ---------------------------------------------------------------------------
CLAY = ramp(['#2a221c', '#3e342b', '#52463a', '#665848', '#786856', '#877663'], 6)


def _clay(seed):
    t = Tex(16)
    v = 3.0 + fnoise(16, 16, seed, beta=1.6) * 0.6 + (white(16, 16, seed + 1) - 0.5) * 0.8
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), CLAY)
    return t


def _cracks(t, segs, c):
    for (a, b, cc, d) in segs:
        for (px_, py_) in bresenham(a, b, cc, d):
            t.set(px_, py_, c)


@texture('block/kamado_front', opaque=True)
def kamado_front():
    t = _clay(811)
    x, y = grid(16, 16)
    # soot rising above the fire mouth
    soot = smoothstep(3.5, 0.0, np.abs(x - 7.5) - (13 - y) * 0.2) * smoothstep(1, 9, y)
    t.a[..., :3] *= (1 - 0.55 * soot[..., None])
    # arched fire mouth
    mouth = ((np.abs(x - 7.5) <= 3.5) & (y >= 9)) | ((((x - 7.5) / 3.6) ** 2 + ((y - 9.5) / 2.6) ** 2) <= 1)
    mouth &= y >= 7
    rim = dilate(mouth, 1) & ~mouth & (y >= 6)
    t.a[rim] = C('#7f705f')
    t.a[rim & (x > 7)] = C('#5f5244')
    t.a[mouth] = C('#070505')
    # dying embers
    for (ex, ey, c) in ((5, 14, '#5e1f0e'), (6, 15, '#83300f'), (8, 15, '#6a2610'), (9, 14, '#3e170c'),
                        (10, 15, '#83300f'), (7, 14, '#2c120a')):
        t.set(ex, ey, c)
    # plaster cap
    for xx in range(16):
        t.set(xx, 0, '#8a8274' if xx % 5 else '#797163')
        t.set(xx, 1, CLAY[2])
    _cracks(t, ((1, 3, 3, 6), (3, 6, 2, 9), (13, 2, 14, 5), (12, 11, 14, 13)), CLAY[0])
    return t


@texture('block/kamado_side', opaque=True)
def kamado_side():
    t = _clay(821)
    x, y = grid(16, 16)
    t.a[..., :3] *= (1 - 0.25 * smoothstep(9, 0, y)[..., None] * (x > 9)[..., None])
    for xx in range(16):
        t.set(xx, 0, '#8a8274' if xx % 6 else '#797163')
        t.set(xx, 1, CLAY[2])
    _cracks(t, ((4, 4, 6, 8), (6, 8, 5, 12), (11, 9, 13, 14)), CLAY[0])
    return t


@texture('block/kamado_top', opaque=True)
def kamado_top():
    t = _clay(831)
    x, y = grid(16, 16)
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    t.a[d < 7.2, :3] *= 0.8                                 # soot ring
    pot = d < 6.0
    t.a[pot] = IRON[2]
    t.a[(d >= 5.0) & (d < 6.0) & (y < 8)] = IRON[3]         # lit rim
    t.a[(d >= 5.0) & (d < 6.0) & (y >= 9)] = IRON[1]
    lid = d < 4.6
    lw = ramp(['#22180f', '#33251a', '#463424', '#57432f'], 4)
    g = fnoise(16, 16, 832, beta=1.3, sx=6.0, sy=0.8)
    li = np.clip(np.round(1.8 + g * 0.6 - (y % 3 == 0) * 1.0), 0, 3).astype(int)
    t.a[lid] = lw[li][lid]
    # lid handle bar
    for xx in range(5, 11):
        t.set(xx, 7, lw[3])
        t.set(xx, 8, lw[0])
    return t


# ---------------------------------------------------------------------------
# well
# ---------------------------------------------------------------------------
STONE = ramp(['#1b1e1c', '#2a2e2b', '#3a3f3b', '#4b514c', '#5d635d', '#6e746d'], 6)
MOSS = pal('#1f2a19', '#2d3b23', '#3d4e2e', '#4f623a')


@texture('block/well_side', opaque=True, tile=True)
def well_side():
    t = Tex(16)
    v = 3.0 + fnoise(16, 16, 841, beta=1.3) * 0.6 + (white(16, 16, 842) - 0.5) * 0.9
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), STONE)
    # coursed stones: two courses, staggered joints
    courses = ((0, 7, (5, 13)), (8, 15, (1, 9)))
    for (y0, y1, joints) in courses:
        for xx in range(16):
            t.set(xx, y1, STONE[0])
            t.set(xx, y0, STONE[4] if xx % 4 else STONE[3])
        for jx in joints:
            for yy in range(y0, y1):
                t.set(jx, yy, STONE[0])
                t.set(jx + 1, yy, STONE[4])
    # moss in the joints and damp bottom
    r = rng(843)
    for yy in range(16):
        for xx in range(16):
            near = (yy in (6, 7, 14, 15, 0, 8))
            if near and r.random() < 0.35:
                t.set(xx, yy, MOSS[int(r.integers(0, 4))])
    for (mx, my) in ((5, 3), (6, 4), (13, 12), (1, 11), (9, 10)):
        t.set(mx, my, MOSS[int(r.integers(1, 4))])
    return t


@texture('block/well_top', opaque=True)
def well_top():
    t = Tex(16)
    x, y = grid(16, 16)
    W = pal('#030406', '#06080b', '#0a0d11', '#0f1318', '#151b21', '#1d252c')
    d = np.sqrt((x - 6.5) ** 2 + (y - 9.0) ** 2)
    v = 1.4 + np.sin(d * 1.6) * 0.5 * smoothstep(9, 2, d) + fnoise(16, 16, 851, beta=1.8) * 0.4
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), W)
    # faint reflection of the red moon
    dm = np.sqrt((x - 10.5) ** 2 + (y - 4.5) ** 2)
    t.a[dm < 2.0] = C('#2a0d0c')
    t.a[(dm < 1.2)] = C('#3a1311')
    t.set(10, 3, '#4a1915')
    # something pale just under the surface: a face that is barely there
    face = ((x - 6.0) / 2.0) ** 2 + ((y - 10.0) / 2.6) ** 2 < 1
    t.a[face] = C('#1a2026')
    t.set(5, 9, '#050607')
    t.set(7, 9, '#050607')
    t.set(6, 12, '#0b0e11')
    # floating hair
    for (px_, py_) in ((2, 6), (3, 7), (3, 8), (4, 8), (9, 12), (10, 13), (11, 13), (12, 14), (8, 7), (9, 6)):
        t.set(px_, py_, '#20262c')
    return t


# ---------------------------------------------------------------------------
# bells, salt, felt, doll, sake tray, clock, stone lantern
# ---------------------------------------------------------------------------
@texture('block/suzu_dai', opaque=True)
def suzu_dai():
    t = Tex(16)
    x, y = grid(16, 16)
    R = ramp(['#2a0807', '#430e0b', '#5a1510', '#6e1c15', '#80261b'], 5)
    # red silk cushion in the middle with a soft puff
    d = np.maximum(np.abs(x - 7.5), np.abs(y - 7.5))
    v = 3.2 - d * 0.28 + fnoise(16, 16, 861, beta=1.2) * 0.35 + (white(16, 16, 862) - 0.5) * 0.5
    v += ((x + y) % 4 == 0) * 0.5
    paint_idx(t, np.clip(np.round(v), 0, 4).astype(int), R)
    # black lacquer border with a gold line
    border = d > 5.5
    t.a[border] = LACQ[1]
    t.a[border & ((x == 0) | (y == 0))] = LACQ[3]
    t.a[(d > 5.5) & (d <= 6.5)] = GOLDF[4]
    t.a[(d > 5.5) & (d <= 6.5) & ((x > 12) | (y > 12))] = GOLDF[3]
    # tassels at the cushion corners
    for (cx, cy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        t.set(cx, cy, GOLDF[5])
    return t


@texture('block/suzu_bell', opaque=True)
def suzu_bell():
    t = Tex(16)
    x, y = grid(16, 16)
    P = ramp(['#2a1d0a', '#4a3514', '#6e5220', '#957436', '#b8954c', '#d2b468', '#e6d090'], 7)
    d = np.sqrt((x - 5.0) ** 2 + (y - 4.5) ** 2)
    rim = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    v = 5.6 - d * 0.42 - smoothstep(6.0, 10.5, rim) * 1.5 + (white(16, 16, 871) - 0.5) * 0.4
    paint_idx(t, np.clip(np.round(v), 0, 6).astype(int), P)
    # equator ridge
    for xx in range(16):
        t.set(xx, 7, P[5] if 2 < xx < 9 else P[3])
        t.set(xx, 8, P[1])
    # mouth slit with round ends
    for xx in range(4, 12):
        t.set(xx, 11, '#0c0804')
    t.set(3, 11, '#1c1408')
    t.set(12, 11, '#1c1408')
    for xx in range(5, 11):
        t.set(xx, 12, P[2])
    t.set(7, 13, '#0c0804')
    t.set(7, 14, '#0c0804')
    # specular
    t.set(4, 3, '#f0e2b0')
    t.set(5, 3, P[6])
    t.set(4, 4, P[6])
    # tarnish
    for (tx, ty) in ((12, 4), (13, 9), (10, 14), (2, 13)):
        t.blend(tx, ty, '#3f3a20', 0.5)
    return t


@texture('block/salt_pile', opaque=True)
def salt_pile():
    S = ramp(['#76736c', '#8f8c84', '#a5a298', '#b8b5aa', '#c7c4b8', '#d2cfc3'], 6)
    t = Tex(16)
    x, y = grid(16, 16)
    v = 0.62 + fnoise(16, 16, 881, beta=0.8) * 0.12 - y * 0.006
    paint_idx(t, quant_idx(v, 6, dither=1.0), S)
    r = rng(882)
    for _ in range(10):
        t.set(int(r.integers(0, 16)), int(r.integers(0, 16)), S[5])
    # it has started to grey: something is wrong here
    for (dx, dy) in ((3, 12), (4, 12), (11, 13), (12, 14), (8, 15), (13, 9)):
        t.set(dx, dy, '#5b5853')
    return t


@texture('block/hina_dan', opaque=True)
def hina_dan():
    F = ramp(['#230807', '#360c0a', '#4a120e', '#5c1913', '#6c2118', '#7a2a1e'], 6)
    t = Tex(16)
    v = 0.55 + fnoise(16, 16, 891, beta=0.9) * 0.10 + fnoise(16, 16, 892, beta=2.0) * 0.08
    paint_idx(t, quant_idx(v, 6, dither=1.0), F)
    # felt nap: soft highlight row near the edge, fading
    for xx in range(16):
        t.set(xx, 0, F[1])
        t.dl(xx, 1, 0.03)
        t.set(xx, 15, F[0])
    # dust, faded patch, moth holes
    fade = blob(16, 16, 11, 6, 3.4, 2.4, 893, rough=0.5) > 0
    t.a[fade] = lighten_arr(t.a[fade], 0.04)
    t.a[fade, :3] *= np.array([0.96, 0.98, 1.0])
    for (hx, hy) in ((4, 9), (12, 12)):
        t.set(hx, hy, '#0e0606')
    return t


@texture('block/doll_kimono', opaque=True)
def doll_kimono():
    K = ramp(['#2a0906', '#420f0b', '#58160f', '#6c1d14', '#7c251a'], 5)
    t = Tex(16)
    v = 2.4 + fnoise(16, 16, 901, beta=1.4) * 0.5 + (white(16, 16, 902) - 0.5) * 0.6
    paint_idx(t, np.clip(np.round(v), 0, 4).astype(int), K)
    # small faded cherry blossoms + gold dots
    BL = C('#b7aa94')
    BL2 = C('#8c7f6c')
    for (cx, cy) in ((3, 3), (11, 2), (7, 12), (14, 11), (1, 11)):
        for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            t.set(cx + dx, cy + dy, BL)
        t.set(cx, cy, GOLDF[5])
        t.set(cx + 1, cy + 1, BL2)
    # gold-brocade obi band
    for xx in range(16):
        t.set(xx, 6, GOLDF[4] if xx % 3 else GOLDF[2])
        t.set(xx, 7, GOLDF[3] if xx % 2 else GOLDF[5])
        t.set(xx, 8, GOLDF[2] if xx % 3 else GOLDF[4])
        t.set(xx, 5, K[0])
        t.set(xx, 9, K[0])
    for (sx, sy) in ((5, 7), (12, 7)):
        t.set(sx, sy, '#6e2a1c')
    # stain
    t.a[13, 4, :3] *= 0.7
    t.a[14, 4, :3] *= 0.8
    return t


@texture('block/doll_face', opaque=True)
def doll_face():
    t = Tex(16)
    FACE = ramp(['#8f8676', '#b0a796', '#c8c0ae', '#d8d1c0', '#e2dccc'], 5)
    HAIR = pal('#060506', '#0e0c0e', '#19161a', '#2a2730')
    x, y = grid(16, 16)
    d = np.sqrt(((x - 7.5) / 6.5) ** 2 + ((y - 10.5) / 7.0) ** 2)
    v = 4.2 - d * 2.6 + (white(16, 16, 911) - 0.5) * 0.5
    paint_idx(t, np.clip(np.round(v), 0, 4).astype(int), FACE)
    # hime-cut black hair: straight bangs and side locks
    hair = (y <= 4) | (x <= 1) | (x >= 14) | ((x <= 2) & (y >= 3)) | ((x >= 13) & (y >= 3))
    t.a[hair] = HAIR[1]
    for xx in range(2, 13):
        t.set(xx, 1, HAIR[3] if 4 <= xx <= 9 else HAIR[2])
    for xx in range(16):
        t.set(xx, 4, HAIR[0])
    for yy in range(5, 16, 3):
        t.set(1, yy, HAIR[2])
        t.set(14, yy, HAIR[2])
    # thin painted brows, glossy black eyes, small red lips
    t.set(4, 6, '#5e5850')
    t.set(5, 6, '#5e5850')
    t.set(10, 6, '#5e5850')
    t.set(11, 6, '#5e5850')
    for (ex, ey) in ((4, 8), (5, 8), (10, 8), (11, 8), (4, 9), (5, 9), (10, 9), (11, 9)):
        t.set(ex, ey, '#050405')
    t.set(5, 8, '#5d5c66')
    t.set(11, 8, '#5d5c66')
    t.set(7, 12, '#7a1b16')
    t.set(8, 12, '#7a1b16')
    t.set(6, 12, '#5a1712')
    t.set(9, 12, '#5a1712')
    t.set(7, 11, '#a8a090')
    # hairline crack running from the eye down the cheek
    for (cx, cy) in ((11, 10), (11, 11), (12, 12), (12, 13), (11, 14), (12, 15)):
        t.set(cx, cy, '#5e574c')
    t.set(4, 10, '#8a8274')        # a tear-stain under the left eye
    t.set(4, 11, '#9e9685')
    return t


@texture('block/sakazuki_table', opaque=True)
def sakazuki_table():
    t = Tex(16)
    x, y = grid(16, 16)
    R = ramp(['#250807', '#3d0d0a', '#55140f', '#691b14', '#7a2419', '#8a3021'], 6)
    v = 3.0 + fnoise(16, 16, 921, beta=1.8) * 0.4 + (white(16, 16, 922) - 0.5) * 0.4
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), R)
    for i in range(16):
        t.set(i, 0, R[5])
        t.set(0, i, R[5])
        t.set(i, 15, R[0])
        t.set(15, i, R[0])
        if 0 < i < 15:
            t.set(i, 1, R[1])
            t.set(1, i, R[1])
    # the three cups of san-san-kudo: large, medium, small
    cups = ((5.0, 10.0, 3.6), (10.5, 9.5, 3.0), (8.0, 4.5, 2.4))
    for k, (cx, cy, rr) in enumerate(cups):
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        shadow = (np.sqrt((x - cx - 0.8) ** 2 + (y - cy - 0.8) ** 2) <= rr) & (d > rr)
        t.a[shadow] = R[0]
        ring = (d <= rr) & (d > rr - 1.0)
        t.a[ring] = C('#9a3a26')
        t.a[ring & (y > cy + 0.5)] = R[3]
        t.a[ring & (x < cx - 0.5) & (y < cy)] = C('#b04a30')
        inner = d <= rr - 1.0
        t.a[inner] = GOLDF[4]
        t.a[inner & (y < cy - 0.4)] = GOLDF[3]
        t.set(int(cx - 0.5), int(cy - 0.5), GOLDF[6])
    # the small cup holds something dark
    d = np.sqrt((x - 8.0) ** 2 + (y - 4.5) ** 2)
    t.a[d <= 1.3] = C('#3a0c09')
    t.set(8, 4, '#5a1a12')
    # worn-through lacquer on the tray corners
    for (wx, wy) in ((2, 2), (13, 13), (13, 2), (2, 13), (3, 2)):
        t.set(wx, wy, LACQ[1])
    return t


@texture('block/old_clock_front', opaque=True)
def old_clock_front():
    t = Tex(16)
    CW = ramp(['#0e0907', '#1c130e', '#2b1d15', '#3c291d', '#4d3626'], 5)
    t.a[:] = wood(931, CW, vertical=True, base=2.2, amp=0.6).a
    x, y = grid(16, 16)
    # octagonal dial surround
    cx, cy = 7.5, 5.5
    oct_ = np.maximum(np.maximum(np.abs(x - cx), np.abs(y - cy)), (np.abs(x - cx) + np.abs(y - cy)) / 1.414)
    t.a[(oct_ <= 6.4) & (y <= 11)] = CW[3]
    t.a[(oct_ <= 6.4) & (oct_ > 5.6) & (y <= 11) & ((x < cx) & (y < cy))] = CW[4]
    t.a[(oct_ <= 5.6) & (oct_ > 5.0)] = GOLDF[3]
    dial = np.sqrt((x - 7.0) ** 2 + (y - 5.0) ** 2) <= 4.3
    DIAL = ramp(['#8e8470', '#a89d86', '#bcb199', '#c8bea5'], 4)
    dv = 2.6 - np.sqrt((x - 6) ** 2 + (y - 4) ** 2) * 0.25 + (white(16, 16, 932) - 0.5) * 0.6
    t.a[dial] = DIAL[np.clip(np.round(dv), 0, 3).astype(int)][dial]
    # hour marks
    for k in range(12):
        a = k / 12 * 2 * math.pi
        px_ = int(round(7 + math.sin(a) * 3.6))
        py_ = int(round(5 - math.cos(a) * 3.6))
        t.set(px_, py_, '#5a5244' if k % 3 == 0 else '#857b68')
    # hands stopped at 2:30 (ushimitsu)
    t.set(7, 5, '#100c0a')
    for (hx, hy) in ((8, 5), (9, 4)):
        t.set(hx, hy, '#100c0a')
    for (mx, my) in ((7, 6), (7, 7), (7, 8)):
        t.set(mx, my, '#100c0a')
    # lower case: glass window with the pendulum
    for yy in range(11, 16):
        for xx in range(4, 12):
            t.set(xx, yy, '#0b0d10' if (xx + yy) % 5 else '#12161a')
    for yy in range(11, 14):
        t.set(7, yy, GOLDF[3])
    for (bx, by) in ((6, 13), (7, 13), (8, 13), (6, 14), (7, 14), (8, 14)):
        t.set(bx, by, GOLDF[4])
    t.set(6, 13, GOLDF[6])
    t.set(8, 14, GOLDF[2])
    for k in range(4):
        t.set(10 - k, 11 + k, '#2a3036')          # glass glint
    for xx in range(3, 13):
        t.set(xx, 10, CW[4]) if 3 < xx < 12 else None
    for yy in range(10, 16):
        t.set(3, yy, CW[4])
        t.set(12, yy, CW[1])
    # stains / cracked glass
    t.set(5, 12, '#2a3036')
    t.set(5, 13, '#1a1e22')
    t.a[3, 9, :3] *= 0.8
    return t


@texture('block/old_clock_side', opaque=True)
def old_clock_side():
    CW = ramp(['#0e0907', '#1c130e', '#2b1d15', '#3c291d', '#4d3626'], 5)
    t = wood(941, CW, vertical=True, base=2.2, amp=0.7)
    for xx in range(16):
        t.set(xx, 0, CW[4])
        t.set(xx, 1, CW[1])
        t.set(xx, 10, CW[4])
        t.set(xx, 11, CW[0])
        t.set(xx, 15, CW[0])
    t.set(13, 12, GOLDF[3])
    t.set(13, 13, GOLDF[2])
    return t


@texture('block/stone_lantern', opaque=True, tile=True)
def stone_lantern():
    G = ramp(['#262624', '#353533', '#454542', '#575652', '#6a6863', '#7d7b75'], 6)
    t = Tex(16)
    v = 3.0 + fnoise(16, 16, 951, beta=1.5) * 0.6
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), G)
    r = rng(952)
    sp = white(16, 16, 953)
    t.a[sp < 0.10] = C('#222220')
    t.a[sp > 0.92] = C('#908d86')
    # moss and lichen
    MOSS2 = pal('#26301f', '#34422a', '#465836', '#5a6c44')
    mm = (blob(16, 16, 3.0, 12.0, 3.2, 2.6, 954, rough=0.7) > 0.1) | \
         (blob(16, 16, 12.0, 3.0, 2.0, 1.6, 955, rough=0.7) > 0.1)
    mi = np.clip(np.round(vnoise(16, 16, 8, 8, 956) * 3.4), 0, 3).astype(int)
    t.a[mm] = MOSS2[mi][mm]
    for (lx, ly) in ((9, 9), (10, 9), (9, 10), (13, 13), (6, 4)):
        t.set(lx, ly, '#8a876a')
    # weathering streak
    for k in range(6):
        t.a[(6 + k) % 16, 11, :3] *= 0.85
    return t
