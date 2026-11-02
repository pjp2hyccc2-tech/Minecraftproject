"""Architectural block textures: floors, walls, panels, doors, torii, decals."""
import math

import numpy as np

from common import (Tex, texture, ramp, pal, C, mix, shade, fnoise, vnoise, white, equalize, norm01,
                    quant, quant_idx, bayer, grid, blob, dilate, erode, edge, smoothstep, rng,
                    lighten_arr, bresenham)

# ---------------------------------------------------------------------------
# shared palettes (dark -> light)
# ---------------------------------------------------------------------------
WOOD_DARK = ramp(['#0f0b0a', '#241c18', '#3b2f27', '#56463a', '#6a5747'], 7)
WOOD_BEAM = ramp(['#0b0908', '#1b1513', '#2c231e', '#3f322a', '#4f4034'], 6)
LACQ = ramp(['#070607', '#141114', '#221d21', '#342c31', '#4e444a'], 6)
PAPER = ramp(['#8f8673', '#aaa18b', '#bdb49c', '#ccc3aa', '#d6ceb5'], 6)
PLASTER = ramp(['#6c665c', '#8d867a', '#a29b8d', '#b2ab9b', '#c1baa8', '#cac3b1'], 6)
BLOOD = ramp(['#1a0806', '#2f0e0a', '#45160f', '#592016', '#69291d'], 5)
IRON = ramp(['#0e0d0d', '#1f1d1c', '#33302d', '#4a4541', '#625b54'], 5)
RUST = ramp(['#2e1a10', '#4f2c18', '#6f4123', '#8a5630'], 4)
GOLD = ramp(['#3a2c14', '#5e4a22', '#806733', '#9f8546', '#b89d5c', '#c9b276'], 6)


def paint_idx(t, idx, palette, mask=None):
    palette = np.asarray(palette, np.float32)
    idx = np.clip(idx, 0, len(palette) - 1).astype(int)
    if mask is None:
        t.a[:] = palette[idx]
    else:
        t.a[mask] = palette[idx][mask]


# ---------------------------------------------------------------------------
# tatami
# ---------------------------------------------------------------------------
@texture('block/tatami_top', opaque=True, tile=True)
def tatami_top():
    R = ramp(['#3d3c25', '#545234', '#6b6841', '#807b4c', '#948d57', '#a49c62', '#b0a86c'], 7)
    HERI = pal('#0d0d12', '#16151c', '#1f1d26', '#2a2731', '#3b3743')
    t = Tex(16)
    x, y = grid(16, 16)
    fade = vnoise(16, 16, 2, 2, 11) - 0.5
    sp = white(16, 16, 13)
    idx = np.where(y % 2 == 0, 4, 2).astype(float)
    # warp binding points on the light rows, twill-offset per row pair
    idx -= (((x + (y // 2) * 2) % 4 == 0) & (y % 2 == 0)) * 1
    idx += np.round(fade * 1.7)
    idx += (sp < 0.04) * 1 - (sp > 0.96) * 1
    # faint old stain (spilled tea / worse) and a pale worn patch
    st = blob(16, 16, 9.5, 11.0, 3.0, 2.0, 14, rough=0.5) > 0
    idx -= st * 1
    worn = blob(16, 16, 5.5, 3.5, 2.6, 1.6, 15, rough=0.6) > 0
    idx += worn * (y % 2 == 1)
    idx = np.clip(idx, 0, 6).astype(int)
    paint_idx(t, idx, R)
    t.a[st, :3] *= np.array([0.98, 0.93, 0.86])
    # frayed stalks
    for (fx, fy) in ((7, 6), (11, 2), (4, 13)):
        t.set(fx, fy, R[6])
        t.set(fx + 1, fy, R[5])
    for yy in range(16):
        t.dl(2, yy, -0.045)
        t.dl(13, yy, -0.035)
        t.set(0, yy, HERI[1] if yy % 2 else HERI[0])
        t.set(1, yy, HERI[3] if yy % 4 == 1 else HERI[2])
        t.set(14, yy, HERI[3] if yy % 4 == 3 else HERI[2])
        t.set(15, yy, HERI[0] if yy % 2 else HERI[1])
    for (hx, hy) in ((1, 5), (14, 10), (1, 13)):
        t.set(hx, hy, HERI[4])
    return t


@texture('block/tatami_side', opaque=True, tile=True)
def tatami_side():
    S = ramp(['#272017', '#3f3524', '#574a33', '#6d5e41', '#80704e', '#918059'], 6)
    R = ramp(['#46442c', '#6b6841', '#8f8957'], 3)
    t = Tex(16)
    r = rng(31)
    idx = np.zeros((16, 16))
    for yy in range(3, 15):
        xx = r.integers(0, 16)
        filled = 0
        while filled < 16:
            ln = int(r.integers(2, 7))
            tone = r.choice([2, 3, 3, 4, 4, 5]) - (1 if yy % 3 == 0 else 0)
            for k in range(ln):
                idx[yy, (xx + k) % 16] = tone
            xx += ln
            filled += ln
            if r.random() < 0.35:
                idx[yy, xx % 16] = 1
                xx += 1
                filled += 1
    idx[13:15] -= 1
    paint_idx(t, idx, S)
    # binding stitches
    for xx in range(1, 16, 4):
        t.set(xx, 8, S[0])
        t.set(xx, 9, S[1])
    for xx in range(16):
        t.set(xx, 0, R[2] if xx % 4 else R[1])
        t.set(xx, 1, R[1] if (xx + 2) % 4 else R[0])
        t.set(xx, 2, S[1])
        t.set(xx, 15, S[0] if xx % 5 else S[1])
    return t


# ---------------------------------------------------------------------------
# floorboards
# ---------------------------------------------------------------------------
def _boards(seed, P, base, tone, joints, knots=(), nails=(), streaks=3, speck=0.06, gap=0,
            lmin=3, lmax=10, pdark=0.7):
    """4 horizontal boards (rows y0..y0+2 wood, y0+3 gap) built from coherent grain streaks."""
    t = Tex(16)
    r = rng(seed)
    n = len(P)
    idx = np.zeros((16, 16))
    for b in range(4):
        y0 = b * 4
        for k, off in enumerate((1, 0, -1)):
            yy = y0 + k
            idx[yy, :] = base + tone[b] + off
            for _ in range(streaks):
                xs = int(r.integers(0, 16))
                ln = int(r.integers(lmin, lmax))
                d = -1 if r.random() < pdark else 1
                for q in range(ln):
                    idx[yy, (xs + q) % 16] += d
            sp = r.random(16)
            idx[yy, sp < speck] += 1
            idx[yy, sp > 1 - speck] -= 1
        idx[y0 + 3, :] = gap + (r.random(16) < 0.15)
    for b, jx in enumerate(joints):
        if jx is None:
            continue
        y0 = b * 4
        for yy in range(y0, y0 + 3):
            idx[yy, jx % 16] = gap
            idx[yy, (jx + 1) % 16] += 1 if yy == y0 else 0
    idx = np.clip(idx, 0, n - 1).astype(int)
    paint_idx(t, idx, P)
    for (kx, ky) in knots:
        t.set(kx, ky, P[max(0, base - 2)])
        t.set(kx + 1, ky, P[max(0, base - 3)])
        t.set(kx - 1, ky, P[min(n - 1, base + 1)])
        t.set(kx + 2, ky, P[min(n - 1, base + 1)])
    for (nx, ny, c) in nails:
        t.set(nx, ny, c)
    return t


@texture('block/old_planks', opaque=True, tile=True)
def old_planks():
    P = ramp(['#100c0b', '#1d1714', '#2a221d', '#372c25', '#45372e', '#544438', '#625143'], 7)
    nail = '#191412'
    t = _boards(21, P, base=4, tone=[0, -1, 1, 0], joints=[5, 12, 1, 9], knots=((10, 9),), streaks=2,
                nails=((3, 1, nail), (7, 1, nail), (10, 5, nail), (14, 5, nail),
                       (3, 9, nail), (11, 13, nail), (7, 13, nail)))
    # dust in the corners of the gaps, a pale scuff
    for (sx, sy) in ((12, 2), (13, 2), (14, 2), (6, 10)):
        t.dl(sx, sy, 0.03)
    return t


@texture('block/engawa', opaque=True, tile=True)
def engawa():
    P = ramp(['#24201d', '#36312c', '#48413b', '#5a524a', '#6b6258', '#7b7266', '#898073'], 7)
    t = _boards(41, P, base=4, tone=[0, -1, 1, 0], joints=[None, 10, None, 3], knots=((6, 5),),
                streaks=3, speck=0.03, lmin=6, lmax=15, pdark=0.55,
                nails=((8, 1, '#3e271b'), (12, 5, '#3e271b'), (14, 9, '#3e271b'), (1, 13, '#3e271b')))
    # weathering checks
    for (cx, cy, ln) in ((1, 9, 5), (12, 1, 3), (8, 13, 4)):
        for k in range(ln):
            t.set(cx + k, cy, P[1])
    return t


@texture('block/rotten_planks', opaque=True, tile=True)
def rotten_planks():
    P = ramp(['#0b0908', '#1a1512', '#271f1a', '#342a22', '#41352b', '#4e4033'], 6)
    MOULD = pal('#232a1f', '#323b2a', '#434d36', '#566043', '#6e7559')
    t = _boards(61, P, base=3, tone=[0, -1, 1, -1], joints=[9, 3, 13, 6], streaks=4)
    x, y = grid(16, 16)
    hole = blob(16, 16, 7.5, 7.5, 3.4, 2.3, 62, rough=0.5, wrap=True) > 0
    hole &= (y >= 5) & (y <= 10)
    rim = dilate(hole, 1, wrap=True) & ~hole
    t.a[hole] = C('#060505')
    t.a[erode(hole, 1, wrap=True)] = C('#020202')
    r = rng(63)
    for (yy, xx) in zip(*np.nonzero(rim)):
        if r.random() < 0.5:
            t.a[yy, xx] = C('#57463a') if yy <= 7 else C('#231c17')
    # splinters poking into the hole
    for (sx, sy) in ((5, 6), (10, 9), (9, 5), (6, 9)):
        if hole[sy, sx]:
            t.set(sx, sy, '#3b3027')
    # mould colonies (organic clumps, not checker)
    mm = (blob(16, 16, 2.5, 13.0, 2.6, 2.2, 64, rough=0.7) > 0.15) | \
         (blob(16, 16, 13.5, 2.0, 2.2, 1.8, 65, rough=0.7) > 0.15)
    mm |= rim & (white(16, 16, 66) < 0.35)
    mf = vnoise(16, 16, 8, 8, 67)
    mi = np.clip(np.round(mf * 4.0), 0, 4).astype(int)
    mm &= ~hole
    t.a[mm] = MOULD[mi][mm]
    for (fx, fy) in ((1, 12), (14, 1), (3, 14)):
        t.set(fx, fy, MOULD[4])
    wet = blob(16, 16, 12.0, 12.5, 3.0, 2.0, 68, rough=0.5) > 0
    t.a[wet & ~hole & ~mm] = lighten_arr(t.a[wet & ~hole & ~mm], -0.05)
    return t


# ---------------------------------------------------------------------------
# beams
# ---------------------------------------------------------------------------
@texture('block/dark_beam_side', opaque=True, tile=True)
def dark_beam_side():
    P = WOOD_BEAM
    t = Tex(16)
    x, y = grid(16, 16)
    g = fnoise(16, 16, 71, beta=1.4, sx=0.7, sy=7.0)
    fine = white(16, 16, 72) - 0.5
    v = 2.6 + g * 0.9 + fine * 0.6
    v += np.where(x == 0, 1.2, 0) + np.where(x == 1, 0.4, 0) + np.where(x == 15, -1.1, 0) + \
        np.where(x == 14, -0.4, 0)
    # cathedral grain chevrons
    for (cx, cy) in ((6, 3), (10, 11)):
        for k in range(4):
            v[(cy + k) % 16, cx - k:cx - k + 1] -= 0.9
            v[(cy + k) % 16, cx + k:cx + k + 1] -= 0.9
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, P)
    # seasoning check (crack) running the full length
    cxs = [9, 9, 9, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 10, 10, 9]
    for yy, cx in enumerate(cxs):
        if yy in (5, 6):
            continue
        t.set(cx, yy, P[0])
        t.dl(cx + 1, yy, 0.03)
    # old nail hole + smoke stain
    t.set(4, 7, '#060505')
    t.blend(4, 8, '#060505', 0.4)
    return t


@texture('block/dark_beam_top', opaque=True)
def dark_beam_top():
    P = ramp(['#110d0b', '#211a16', '#33281f', '#463729', '#574535', '#64513f'], 6)
    t = Tex(16)
    x, y = grid(16, 16)
    cx, cy = 6.7, 8.6
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    ang = np.arctan2(y - cy, x - cx)
    d = d + (vnoise(16, 16, 4, 4, 81) - 0.5) * 1.4 + 0.25 * np.sin(ang * 3)
    rings = np.sin(d * 2.0) * 0.9
    fine = white(16, 16, 82) - 0.5
    v = 3.0 + rings + fine * 0.6 - smoothstep(6, 11, d) * 0.5
    edgem = (x == 0) | (y == 0) | (x == 15) | (y == 15)
    v[edgem] -= 0.9
    v[(x == 0) | (y == 0)] += 0.5
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, P)
    # radial crack from the pith
    for (px, py) in bresenham(7, 8, 15, 4):
        t.set(px, py, P[0])
    t.set(7, 9, P[0])
    t.set(6, 8, P[1])
    return t


# ---------------------------------------------------------------------------
# plaster
# ---------------------------------------------------------------------------
def _plaster_base(seed, P=None):
    P = PLASTER if P is None else P
    t = Tex(16)
    low = fnoise(16, 16, seed, beta=1.8)
    mid = fnoise(16, 16, seed + 1, beta=1.0)
    trowel = fnoise(16, 16, seed + 2, beta=1.4, sx=2.5, sy=0.8)
    v = 0.58 + low * 0.10 + mid * 0.07 + trowel * 0.06
    idx = quant_idx(v, 6, dither=0.9)
    paint_idx(t, idx, P)
    return t


@texture('block/plaster_wall', opaque=True, tile=True)
def plaster_wall():
    t = _plaster_base(101)
    x, y = grid(16, 16)
    grime = fnoise(16, 16, 105, beta=2.0)
    m = grime > 1.0
    t.a[m, :3] *= np.array([0.95, 0.94, 0.92])
    for (sx, sy) in ((3, 4), (12, 9), (7, 14)):
        t.set(sx, sy, PLASTER[2])
    for (cx, cy) in ((11, 2), (11, 3), (12, 4), (12, 5)):
        t.set(cx, cy, PLASTER[2])
    t.set(13, 6, PLASTER[3])
    return t


@texture('block/plaster_wall_stained', opaque=True, tile=True)
def plaster_wall_stained():
    t = _plaster_base(111)
    x, y = grid(16, 16)
    # water from a leaking roof: two vertical stain runs (continuous when stacked)
    wob = vnoise(16, 16, 1, 4, 112) - 0.5
    wob2 = vnoise(16, 16, 1, 4, 118) - 0.5
    run1 = np.abs(x - (4.5 + wob[:, :1] * 3.0)) < (2.2 + wob2[:, :1] * 1.6)
    run2 = np.abs(x - (11.0 + wob2[:, :1] * 2.0)) < (0.9 + wob[:, :1] * 0.8)
    m = run1 | run2
    t.a[m, :3] *= np.array([0.89, 0.86, 0.80])
    core = erode(run1, 1, wrap=True)
    t.a[core, :3] *= np.array([0.97, 0.96, 0.94])
    rim = m & ~erode(m, 1, wrap=True)
    t.a[rim, :3] *= np.array([0.88, 0.82, 0.72])
    # tide-line blooms
    bl = blob(16, 16, 4.5, 3.0, 3.6, 2.4, 119, rough=0.4) > 0
    edge_b = bl & ~erode(bl, 1, wrap=True) & ~core
    t.a[edge_b, :3] *= np.array([0.86, 0.80, 0.70])
    # mould clumps along the narrow run
    MOULD = pal('#3a3d34', '#4b4e43', '#5f6255', '#757769')
    mm = (blob(16, 16, 11.5, 12.5, 1.4, 2.2, 115, rough=0.8) > 0.25) & (white(16, 16, 117) < 0.7)
    mf = vnoise(16, 16, 8, 8, 116)
    mi = np.clip(np.round((1 - mf) * 3.4 - 0.4), 0, 3).astype(int)
    t.a[mm] = MOULD[mi][mm]
    for (px_, py_) in ((13, 9), (10, 15), (14, 14), (9, 11)):
        t.set(px_, py_, MOULD[2])
    # a faint smear, as if a hand slid down the wall
    for (sx, sy, a) in ((6, 8, .16), (7, 8, .20), (8, 8, .18), (9, 8, .12), (6, 9, .18), (7, 9, .22),
                        (8, 9, .2), (9, 9, .14), (7, 10, .17), (8, 10, .18), (7, 11, .13),
                        (8, 11, .15), (8, 12, .11), (7, 12, .08), (8, 13, .08), (8, 14, .05)):
        t.blend(sx, sy, '#4f1d14', a)
    return t


@texture('block/wainscot', opaque=True, tile=True)
def wainscot():
    P = ramp(['#0d0a09', '#1f1814', '#30251e', '#423328', '#544233', '#62503f'], 6)
    t = Tex(16)
    x, y = grid(16, 16)
    g = fnoise(16, 16, 121, beta=1.3, sx=0.7, sy=6.0)
    fine = white(16, 16, 122) - 0.5
    v = 2.6 + g * 0.8 + fine * 0.6
    tone = np.array([0.2, -0.3, 0.35, -0.1])[(x // 4).astype(int)]
    v += tone
    v += np.where(x % 4 == 0, 0.6, 0) + np.where(x % 4 == 3, -2.5, 0)
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, P)
    # top rail
    for xx in range(16):
        t.set(xx, 0, P[5] if xx % 7 else P[4])
        t.set(xx, 1, P[3])
        t.set(xx, 2, P[2] if xx % 5 else P[3])
        t.set(xx, 3, P[0])
    # baseboard
    for xx in range(16):
        t.set(xx, 13, P[0])
        t.set(xx, 14, P[3] if xx % 6 else P[2])
        t.set(xx, 15, P[1])
    # scuffs and scratches near the bottom
    for (sx, sy) in ((5, 11), (6, 10), (7, 9)):
        t.dl(sx, sy, 0.06)
    return t


@texture('block/kura_wall', opaque=True, tile=True)
def kura_wall():
    TILE = ramp(['#141417', '#1f2024', '#2a2b30', '#35363b', '#404146'], 5)
    J = ramp(['#5f5a52', '#7f796e', '#9f988a', '#b5ae9d', '#c3bcaa'], 5)
    t = Tex(16)
    x, y = grid(16, 16)
    u = (x + y) % 8          # '/' ridges
    w = (x - y) % 8          # '\' ridges
    fine = white(16, 16, 131)
    low = vnoise(16, 16, 4, 4, 132) - 0.5
    v = np.full((16, 16), 2.0) + np.round(low * 1.6)
    v += (fine < 0.12) * 1 - (fine > 0.9) * 1
    v -= ((u == 2) | (w == 7)) * 1          # shadow cast by the ridge on the lower side
    v += ((u == 7) & (w != 0)) * 1
    idx = np.clip(v, 0, 4).astype(int)
    paint_idx(t, idx, TILE)
    ru = (u == 0) | (u == 1)
    rw = (w == 0) | (w == 1)
    t.a[ru] = J[3]
    t.a[ru & (u == 1)] = J[2]
    t.a[rw] = J[3]
    t.a[rw & (w == 0)] = J[2]
    t.a[ru & rw] = J[4]
    # grime streaks below the crossings, plaster discoloured by rain
    dirt = white(16, 16, 134)
    m = (ru | rw) & (dirt < 0.18)
    t.a[m] = J[1]
    for (gx, gy, ln) in ((3, 6, 3), (11, 2, 3), (7, 10, 2)):
        for k in range(ln):
            t.a[(gy + k) % 16, gx, :3] *= 0.84
    # chipped tile + crack
    t.set(12, 12, TILE[0])
    t.set(4, 3, TILE[0])
    t.set(5, 3, TILE[1])
    return t


# ---------------------------------------------------------------------------
# shoji / fusuma / ranma
# ---------------------------------------------------------------------------
LAT = ramp(['#1f1814', '#2f251f', '#40332a', '#524236'], 4)


def _paper(seed, w=16, h=16, P=None, base=0.62, amp=1.0):
    P = PAPER if P is None else P
    t = Tex(w, h)
    low = fnoise(w, h, seed, beta=1.8)
    fib = fnoise(w, h, seed + 1, beta=0.6, sx=2.0, sy=0.6)
    v = base + low * 0.07 * amp + fib * 0.05 * amp
    idx = quant_idx(v, len(P), dither=0.8)
    paint_idx(t, idx, P)
    return t


def _shoji_lattice(t, xs, ys, y_range=(0, 15), x_range=(0, 15), L=LAT, seed=5):
    r = rng(seed)
    for yy in ys:
        for xx in range(x_range[0], x_range[1] + 1):
            t.set(xx, yy, L[1] if r.random() > 0.15 else L[2])
        # soft grime shadow under each rail
        for xx in range(x_range[0], x_range[1] + 1):
            if (yy + 1) not in ys and y_range[0] <= yy + 1 <= y_range[1]:
                t.a[(yy + 1) % t.h, xx % t.w, :3] *= 0.94
    for xx in xs:
        for yy in range(y_range[0], y_range[1] + 1):
            c = L[1] if r.random() > 0.15 else L[0]
            if yy in ys:
                c = L[0]
            t.set(xx, yy, c)


def shoji_base(seed=201):
    t = _paper(seed)
    # paper strip seams and dirt specks
    for xx in range(16):
        if xx % 3:
            t.a[6, xx, :3] *= 0.965
    r = rng(seed + 1)
    for _ in range(4):
        t.set(int(r.integers(1, 16)), int(r.integers(1, 16)), PAPER[1])
    _shoji_lattice(t, xs=(0, 8), ys=(0, 4, 8, 12))
    return t


@texture('block/shoji', opaque=True, tile=True)
def shoji():
    t = shoji_base()
    # yellowed patch, a dead moth speck
    m = blob(16, 16, 12, 10, 3, 2, 207, rough=0.5) > 0
    t.a[m, :3] *= np.array([0.97, 0.95, 0.88])
    return t


@texture('block/shoji_torn', cutout=True, tile=True)
def shoji_torn():
    t = shoji_base(211)
    x, y = grid(16, 16)
    holes = (blob(16, 16, 4.0, 10.5, 3.0, 2.6, 212, rough=0.55, wrap=False) > 0)
    holes |= (blob(16, 16, 12.0, 2.5, 2.2, 1.5, 213, rough=0.5, wrap=False) > 0)
    holes |= (blob(16, 16, 12.5, 13.5, 1.2, 1.0, 214, rough=0.3, wrap=False) > 0)
    lattice = np.zeros((16, 16), bool)
    lattice[:, [0, 8]] = True
    lattice[[0, 4, 8, 12], :] = True
    holes &= ~lattice
    # finger-poke holes
    for (hx, hy) in ((5, 2), (14, 6), (10, 9)):
        holes[hy, hx] = True
    # torn flaps: paper pixels that stay hanging into the big hole
    flap = np.zeros((16, 16), bool)
    for (fx, fy) in ((3, 9), (2, 11), (6, 11), (13, 2)):
        flap[fy, fx] = True
    holes &= ~flap
    rim = dilate(holes, 1) & ~holes & ~lattice
    t.a[rim, :3] *= np.array([0.86, 0.80, 0.70])
    t.a[flap, :3] *= np.array([0.80, 0.74, 0.64])
    # broken kumiko piece hanging
    t.set(3, 12, LAT[2])
    t.a[holes, 3] = 0
    return t


HAND = ["....#....",
        "..#.#.#..",
        "..#.#.#.#",
        "..#.#.#.#",
        "#.#####.#",
        "########.",
        ".#######.",
        "..######.",
        "..#####..",
        "...####.."]


def _blood_mask_hand(ox, oy, w=16, h=16, smear=True, seed=222):
    mask = np.zeros((h, w), bool)
    for j, row in enumerate(HAND):
        for i, ch in enumerate(row):
            if ch == '#' and 0 <= oy + j < h and 0 <= ox + i < w:
                mask[oy + j, ox + i] = True
    if smear:
        r = rng(seed)
        top = oy + len(HAND)
        for yy in range(top, h):
            k = yy - top
            for xx in range(ox + 3, ox + 7):
                p = 0.95 - k * 0.16 - (0.5 if xx == ox + 5 and k > 0 else 0)
                if r.random() < p:
                    mask[yy, xx] = True
    return mask


@texture('block/shoji_bloody', opaque=True, tile=True)
def shoji_bloody():
    t = shoji_base(221)
    B = BLOOD
    ox, oy = 2, 1
    mask = _blood_mask_hand(ox, oy)
    for (dx, dy, ln) in ((ox + 8, oy + 4, 6), (ox + 2, oy + 4, 3), (ox + 6, oy + 9, 5)):
        for k in range(ln):
            if dy + k < 16:
                mask[dy + k, dx] = True
    r = rng(223)
    tone = np.where(erode(mask, 1), 1, 2) + (white(16, 16, 224) < 0.22) * 1
    tone[oy + 10:, :] += 1                       # smear thins out
    t.a[mask] = B[np.clip(tone, 0, 4)][mask]
    for (px_, py_) in ((ox + 4, oy + 5), (ox + 3, oy + 6), (ox + 5, oy + 7)):
        t.set(px_, py_, B[0])
    lat = np.zeros((16, 16), bool)
    lat[:, [0, 8]] = True
    lat[[0, 4, 8, 12], :] = True
    t.a[mask & lat] = C('#220806')
    # thin soak halo only beside the palm
    halo = dilate(mask, 1) & ~mask & ~lat & (white(16, 16, 225) < 0.35)
    t.a[halo, :3] = t.a[halo, :3] * 0.88 + np.array([70, 30, 22]) * 0.12
    for (sx, sy) in ((13, 2), (14, 5), (12, 1), (14, 10), (13, 13)):
        t.set(sx, sy, B[2])
    t.set(13, 3, B[3])
    return t


FUSUMA_PINE = [
    "....b.........",
    "....bb........",
    ".....b..gGgGg.",
    ".....b.gGGGGGg",
    ".....bBkGGGGGk",
    "......bbkkkkk.",
    "......b.......",
    ".gGgGg.b......",
    "gGGGGGGb......",
    "kGGGGGkb......",
    ".kkkkkbb......",
    "......b.......",
    ".....bbbbgGg..",
    "....bB..gGGGg.",
    "....b...kkkkk.",
    "....b.........",
]
MOTIF_COLORS = {
    'b': '#241b14', 'B': '#3a2d21',
    'g': '#46593f', 'G': '#2b3a2b', 'k': '#121812',
    'w': '#c2baa5', 'W': '#a39b88', 'K': '#141212', 'r': '#6e2219',
}


def draw_map(t, rows, ox, oy, colors=None, wrap=True):
    colors = MOTIF_COLORS if colors is None else colors
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in colors:
                t.set(ox + i, oy + j, colors[ch], wrap)


@texture('block/fusuma', opaque=True, tile=True)
def fusuma():
    t = fusuma_ground(231, 16, 16)
    draw_map(t, FUSUMA_PINE, 1, 0)
    # needle texture: a few lighter strokes inside the fans
    for (px_, py_) in ((10, 3), (12, 3), (3, 8), (5, 8), (11, 13)):
        t.set(px_, py_, '#3a4c37')
    # three fingernail scratches through the gold
    for k in range(3):
        for (px_, py_) in bresenham(9 + 2 * k, 6, 10 + 2 * k, 11):
            if px_ < 15:
                t.set(px_, py_, '#c9be a4'.replace(' ', '') if (py_ % 3) else '#a59a83')
    # foxing + a small tear
    for (fx, fy) in ((2, 3), (13, 14), (7, 13)):
        t.a[fy, fx, :3] *= np.array([0.85, 0.78, 0.68])
    for (px_, py_) in ((2, 13), (2, 14), (3, 14)):
        t.set(px_, py_, '#16110d')
    t.a[12, 2, :3] *= 0.7
    t.a[14, 1, :3] *= 0.8
    for yy in range(16):
        t.set(0, yy, LACQ[2] if yy % 5 else LACQ[3])
        t.set(15, yy, LACQ[1] if yy % 7 else LACQ[2])
    return t


def _pine_fan(t, cx, cy, small=False):
    """Kano-style pine needle cluster: flat fan, lit top edge, black outline below."""
    PN = pal('#0f150f', '#18221a', '#223024', '#2f4030', '#3c5039')
    rows = [".#.#.#.", "#######", ".#####."] if not small else [".#.#.", "#####"]
    w = len(rows[0])
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '#':
                c = PN[4] if j == 0 else (PN[2] if j == 1 else PN[1])
                if j == 1 and i % 2 == 0:
                    c = PN[3]
                t.set(cx - w // 2 + i, cy - len(rows) + 1 + j, c)
    for i in range(1, w - 1):
        t.set(cx - w // 2 + i, cy + 1, PN[0]) if not small else None


FG = ramp(['#3e331e', '#5c4e30', '#776641', '#8c7a50', '#9d8a5c', '#ab9868'], 6)


def fusuma_ground(seed, w, h):
    """Faded gold-leaf ground: mosaic of slightly different leaf squares."""
    t = Tex(w, h)
    r = rng(seed)
    x, y = grid(w, h)
    cell = np.zeros((h, w))
    for cy in range(0, h, 4):
        for cx in range(0, w, 4):
            cell[cy:cy + 4, cx:cx + 4] = r.choice([-1, 0, 0, 0, 1])
    low = fnoise(w, h, seed + 1, beta=1.6)
    v = 3.0 + cell * 0.8 + low * 0.7 + (white(w, h, seed + 2) - 0.5) * 0.9
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, FG)
    # faint seams of the leaf squares
    for yy in range(0, h, 4):
        for xx in range(w):
            if r.random() < 0.5:
                t.a[yy, xx, :3] *= 0.93
    return t


PINE = pal('#121812', '#1b241a', '#263123', '#33402d')


def _pine(t, clusters, seed):
    """Stylised pine needle clusters (flattened fans) + a branch."""
    r = rng(seed)
    for (cx, cy) in clusters:
        for dx in range(-3, 4):
            hgt = 2 if abs(dx) <= 1 else 1
            for dy in range(-hgt + 1, 1):
                if r.random() < 0.9:
                    c = PINE[1] if dy == 0 else PINE[2]
                    if abs(dx) == 3:
                        c = PINE[0]
                    t.set(cx + dx, cy + dy, c)
        t.set(cx, cy - 2, PINE[3])
        t.set(cx - 1, cy + 1, PINE[0])
        t.set(cx + 1, cy + 1, PINE[0])
    # branch linking the clusters
    BR = C('#2c2219')
    for (a, b) in zip(clusters[:-1], clusters[1:]):
        if abs(a[1] - b[1]) < 6:
            for (px_, py_) in bresenham(a[0], a[1] + 1, b[0], b[1] + 1):
                t.set(px_, py_, BR)


def _crane(t, x0, y0):
    """A small standing crane: off-white body, black neck/tail, dull red crown."""
    W1, W2 = C('#bfb7a3'), C('#a39b88')
    K = C('#151313')
    RED = C('#6e2219')
    pts_body = [(1, 3), (2, 3), (3, 3), (0, 4), (1, 4), (2, 4), (3, 4), (1, 5), (2, 5)]
    for (dx, dy) in pts_body:
        t.set(x0 + dx, y0 + dy, W1)
    t.set(x0 + 1, y0 + 5, W2)
    t.set(x0 + 2, y0 + 5, W2)
    # tail (black)
    t.set(x0 - 1, y0 + 4, K)
    t.set(x0 - 1, y0 + 5, K)
    # neck and head
    t.set(x0 + 4, y0 + 3, K)
    t.set(x0 + 4, y0 + 2, K)
    t.set(x0 + 4, y0 + 1, W1)
    t.set(x0 + 5, y0 + 1, W2)  # beak
    t.set(x0 + 4, y0 + 0, RED)
    # legs
    t.set(x0 + 1, y0 + 6, K)
    t.set(x0 + 2, y0 + 6, K)
    t.set(x0 + 1, y0 + 7, K)
    t.set(x0 + 2, y0 + 7, K)


def _fusuma_damage(t, seed, claws=False, tear=(11, 12)):
    # water stain along the bottom
    r = rng(seed)
    for xx in range(t.w):
        hh = 1 + int(r.integers(0, 3))
        for k in range(hh):
            t.a[t.h - 1 - k, xx, :3] *= np.array([0.80, 0.74, 0.66])
    # tear revealing the dark lattice core
    if tear:
        tx, ty = tear
        for (dx, dy) in ((0, 0), (1, 0), (1, 1), (2, 1), (0, -1), (2, 2)):
            t.set(tx + dx, ty + dy, '#14100c')
        for (dx, dy) in ((-1, 0), (3, 1), (1, -1), (3, 2), (1, 2)):
            t.a[(ty + dy) % t.h, (tx + dx) % t.w, :3] *= 0.75
    if claws:
        # three fingernail scratches exposing pale under-paper
        for k in range(3):
            for (px_, py_) in bresenham(4 + 2 * k, 7, 6 + 2 * k, 13):
                t.set(px_, py_, '#c4b99f' if (px_ + py_) % 3 else '#a0967f')


@texture('block/ranma', cutout=True, tile=True)
def ranma():
    P = ramp(['#120d0b', '#231b16', '#352a22', '#46382d', '#56463a'], 5)
    t = Tex(16)
    x, y = grid(16, 16)
    u = (x + y) % 8
    w = (x - y) % 8
    bars = ((u == 0) | (u == 1) | (w == 0) | (w == 7))
    inner = (y >= 2) & (y <= 13)
    t.a[:] = P[2]
    # diamond lattice bars with a lit upper edge
    lit = ((u == 0) | (w == 7))
    t.a[bars & lit] = P[3]
    t.a[bars & ~lit] = P[1]
    # small carved flower at each crossing
    cross = ((u == 0) | (u == 1)) & ((w == 0) | (w == 7))
    t.a[cross] = P[4]
    for yy in (0, 1, 14, 15):
        for xx in range(16):
            t.set(xx, yy, [P[4], P[2], P[3], P[0]][[0, 1, 14, 15].index(yy)])
    for xx in range(0, 16, 5):
        t.set(xx, 1, P[1])
        t.set(xx + 2, 14, P[2])
    t.a[inner & ~bars, 3] = 0
    return t


# ---------------------------------------------------------------------------
# doors (drawn as one 16x32 panel, then split)
# ---------------------------------------------------------------------------
_DOOR_CACHE = {}


def _door(name):
    if name not in _DOOR_CACHE:
        _DOOR_CACHE[name] = globals()['_door_' + name]()
    return _DOOR_CACHE[name]


def _top(t):
    return Tex.from_array(t.a[:16].copy())


def _bottom(t):
    return Tex.from_array(t.a[16:].copy())


CRANE_BIG = [
    "....r..",
    "...KwW.",
    "...K...",
    "...K...",
    "..KK...",
    ".wwww..",
    "wwwwwW.",
    "KwwwwW.",
    "KK.WW..",
    "...K.K.",
    "...K.K.",
    "...K.K.",
]


def _door_fusuma():
    t = fusuma_ground(301, 16, 32)
    pine = [
        "bb..........",
        ".bb.........",
        "..bBgGgGg...",
        "..bgGGGGGg..",
        "...kGGGGGk..",
        "...bkkkkk...",
        "....b.......",
        "....bb.gGg..",
        ".....bgGGGg.",
        "......kkkkk.",
    ]
    draw_map(t, pine, 1, 1, wrap=False)
    draw_map(t, CRANE_BIG, 3, 15, wrap=False)
    for (px_, py_) in ((4, 4), (6, 4), (9, 10)):
        t.set(px_, py_, '#3a4c37')
    # water damage creeping up from the floor
    r = rng(302)
    for xx in range(16):
        hh = 2 + int(r.integers(0, 3))
        for k in range(hh):
            t.a[30 - k, xx, :3] *= np.array([0.80, 0.74, 0.64])
        t.a[30 - hh, xx, :3] *= np.array([0.86, 0.80, 0.70])
    # finger-poked holes and a tear
    for (hx, hy) in ((6, 9), (12, 24), (3, 27), (4, 27)):
        t.set(hx, hy, '#16110d')
    t.a[28, 3, :3] *= 0.7
    # hikite (round recessed pull)
    HK = pal('#0b0908', '#2b2116', '#4d3c24', '#6d5832')
    hik = [".ab.", "a00b", "a00c", ".bc."]
    cmap = {'0': HK[0], 'a': HK[3], 'b': HK[2], 'c': HK[1]}
    for j, row in enumerate(hik):
        for i, ch in enumerate(row):
            if ch in cmap:
                t.set(10 + i, 17 + j, cmap[ch])
    t.a[21, 11, :3] *= 0.85       # grubby fingers below the pull
    t.a[16, 11, :3] *= 0.9
    # black lacquer frame
    for yy in range(32):
        t.set(0, yy, LACQ[2] if yy % 6 else LACQ[3])
        t.set(15, yy, LACQ[1] if yy % 9 else LACQ[2])
    for xx in range(16):
        t.set(xx, 0, LACQ[3] if xx % 5 else LACQ[2])
        t.set(xx, 31, LACQ[1])
    return t


def _door_shoji():
    t = Tex(16, 32)
    t.a[:] = _paper(311, 16, 32).a
    L = LAT
    FR = ramp(['#1f1814', '#30261f', '#43362c', '#574638'], 4)
    xs = (5, 10)
    ys = (6, 11, 16, 21)
    _shoji_lattice(t, xs=xs, ys=ys, y_range=(2, 24), x_range=(2, 13), seed=312)
    # paper strip seam
    for xx in range(2, 14):
        if xx % 4:
            t.a[13, xx, :3] *= 0.96
    # kick panel (koshi-ita)
    G = fnoise(16, 32, 313, beta=1.3, sx=6.0, sy=0.8)
    for yy in range(25, 30):
        for xx in range(2, 14):
            v = 2 + G[yy, xx] * 0.8 + (0.8 if yy == 26 else 0) - (0.8 if yy == 29 else 0)
            t.set(xx, yy, FR[int(np.clip(round(v), 0, 3))])
    for xx in range(2, 14):
        t.set(xx, 25, FR[0])
    # stiles and rails
    for yy in range(32):
        for xx, c in ((0, FR[2]), (1, FR[1]), (14, FR[2]), (15, FR[0])):
            t.set(xx, yy, c if (yy * 7 + xx) % 11 else FR[3])
    for xx in range(16):
        t.set(xx, 0, FR[3])
        t.set(xx, 1, FR[1])
        t.set(xx, 30, FR[2])
        t.set(xx, 31, FR[0])
    # grime: dirty paper near the handle, a poked hole, a stain
    for (hx, hy) in ((3, 18), (4, 19), (3, 20)):
        t.a[hy, hx, :3] *= 0.9
    t.set(8, 8, '#1b1512')
    m = blob(16, 32, 8.0, 22.0, 3.0, 2.0, 314, rough=0.4, wrap=False) > 0
    m[:, :2] = False
    m[:, 14:] = False
    t.a[m, :3] *= np.array([0.94, 0.9, 0.82])
    return t


def _door_oshiire():
    P = ramp(['#0e0b09', '#1b1511', '#28201a', '#362b23', '#45372d'], 5)
    t = Tex(16, 32)
    t.a[:] = P[2]
    G = fnoise(16, 32, 321, beta=1.3, sx=5.0, sy=0.8)
    Gv = fnoise(16, 32, 322, beta=1.3, sx=0.8, sy=5.0)
    slit_rows = [4, 7, 10, 13, 19, 22, 25, 28]
    rails = {0, 1, 15, 16, 30, 31}
    for yy in range(32):
        for xx in range(16):
            if xx in (0, 1, 14, 15):
                v = 2 + Gv[yy, xx] * 0.7 + (0.8 if xx == 0 else 0) - (0.9 if xx == 15 else 0)
                t.set(xx, yy, P[int(np.clip(round(v), 0, 4))])
            elif yy in rails:
                v = 2 + G[yy, xx] * 0.6 + (1 if yy in (0, 15) else 0) - (1 if yy in (16, 31) else 0)
                t.set(xx, yy, P[int(np.clip(round(v), 0, 4))])
            elif yy in slit_rows:
                t.clear(xx, yy)
            else:
                above_slit = (yy - 1) in slit_rows or (yy - 1) in rails
                v = 2 + G[yy, xx] * 0.6 + (1.0 if above_slit else -0.6)
                t.set(xx, yy, P[int(np.clip(round(v), 0, 4))])
    # inner shadow where slats meet the stiles
    for yy in range(2, 30):
        if yy not in slit_rows and yy not in rails:
            t.dl(2, yy, -0.04)
            t.dl(13, yy, -0.03)
    # small recessed pull on the stile
    t.set(14, 17, P[0])
    t.set(14, 18, P[0])
    t.set(14, 19, P[1])
    # scratches from the inside
    for (sx, sy) in ((6, 20), (7, 21), (8, 23), (9, 24)):
        if sy not in slit_rows:
            t.set(sx, sy, P[4])
    return t


def _door_kura():
    KW = ramp(['#090807', '#120f0d', '#1b1613', '#251e1a', '#2f2621'], 5)
    t = Tex(16, 32)
    G = fnoise(16, 32, 331, beta=1.3, sx=0.8, sy=5.0)
    for yy in range(32):
        for xx in range(16):
            v = 2.3 + G[yy, xx] * 0.8
            if xx in (5, 10):
                v = 0
            if xx in (6, 11, 0):
                v += 0.7
            t.set(xx, yy, KW[int(np.clip(round(v), 0, 4))])
    STUD = pal('#121110', '#3b3631', '#5d554c', '#7a6f62')
    # iron straps with studs
    for (y0, y1) in ((3, 5), (26, 28)):
        for yy in range(y0, y1 + 1):
            for xx in range(16):
                c = IRON[2] if yy == y0 else (IRON[1] if yy == y1 else IRON[1])
                if yy == y0 and xx % 4 == 1:
                    c = IRON[3]
                t.set(xx, yy, c)
        for xx in (1, 5, 10, 14):
            t.set(xx, y0 + 1, STUD[3])
            t.set(xx + 1, y0 + 1, STUD[1])
            t.set(xx, y0 + 2, STUD[1])
            t.set(xx + 1, y0 + 2, STUD[0])
    # rows of studs on the boards
    for yy in (10, 15, 21):
        for xx in (2, 7, 13):
            t.set(xx, yy, STUD[2])
            t.set(xx + 1, yy, STUD[0])
            t.set(xx, yy + 1, STUD[0])
    # big lock plate (bottom half, upper part)
    plate = [
        ".aaaaaa.",
        "abbbbbbc",
        "abd..dbc",
        "ab.00.bc",
        "ab.00.bc",
        "ab..0.bc",
        "abd..dbc",
        "abbbbbbc",
        ".cccccc.",
    ]
    LP = {'a': '#6b6255', 'b': '#3d3730', 'c': '#1c1916', 'd': '#7d7262', '0': '#020202',
          '.': None}
    for j, row in enumerate(plate):
        for i, ch in enumerate(row):
            col = LP.get(ch)
            if col is None and ch == '.' and 1 <= i <= 6 and 1 <= j <= 7:
                col = '#2c2722'
            if col:
                t.set(4 + i, 16 + j, col)
    # hasp / bar across the gap
    for xx in range(2, 4):
        t.set(xx, 19, IRON[3])
        t.set(xx, 20, IRON[1])
    # rust bleeding down from the iron
    r = rng(332)
    for (rx, ry, ln) in ((5, 25, 4), (9, 25, 3), (2, 6, 4), (12, 6, 5), (7, 29, 2)):
        for k in range(ln):
            t.blend(rx, ry + k, RUST[1 + (k == 0)], 0.6 - k * 0.1, wrap=False)
    # pull ring on the top half
    ring = [(11, 9), (12, 9), (10, 10), (13, 10), (10, 11), (13, 11), (11, 12), (12, 12)]
    for (px_, py_) in ring:
        t.set(px_, py_, STUD[2])
    t.set(11, 8, STUD[3])
    t.set(12, 8, STUD[1])
    return t


@texture('block/fusuma_door_top', opaque=True)
def fusuma_door_top():
    return _top(_door('fusuma'))


@texture('block/fusuma_door_bottom', opaque=True)
def fusuma_door_bottom():
    return _bottom(_door('fusuma'))


@texture('block/shoji_door_top', opaque=True)
def shoji_door_top():
    return _top(_door('shoji'))


@texture('block/shoji_door_bottom', opaque=True)
def shoji_door_bottom():
    return _bottom(_door('shoji'))


@texture('block/oshiire_door_top', cutout=True)
def oshiire_door_top():
    return _top(_door('oshiire'))


@texture('block/oshiire_door_bottom', cutout=True)
def oshiire_door_bottom():
    return _bottom(_door('oshiire'))


@texture('block/kura_door_top', opaque=True)
def kura_door_top():
    return _top(_door('kura'))


@texture('block/kura_door_bottom', opaque=True)
def kura_door_bottom():
    return _bottom(_door('kura'))


# ---------------------------------------------------------------------------
# torii
# ---------------------------------------------------------------------------
VERM = ramp(['#1f0a07', '#34100b', '#4a170f', '#5e2014', '#6f2819', '#7e3220'], 6)


@texture('block/vermilion', opaque=True, tile=True)
def vermilion():
    t = Tex(16)
    x, y = grid(16, 16)
    g = fnoise(16, 16, 401, beta=1.3, sx=0.7, sy=6.0)
    low = fnoise(16, 16, 402, beta=2.0)
    v = 3.2 + g * 0.7 + low * 0.5 + (white(16, 16, 403) - 0.5) * 0.8
    v += np.where(x == 0, 0.8, 0) + np.where(x == 15, -0.8, 0)
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, VERM)
    # flaking lacquer exposing the black undercoat, lifted edge lighter
    flakes = np.zeros((16, 16), bool)
    for (fx, fy, shape) in ((4, 4, ((0, 0), (1, 0), (1, 1))),):
        for (dx, dy) in shape:
            flakes[(fy + dy) % 16, (fx + dx) % 16] = True
    t.a[flakes] = C('#140d0b')
    lip = np.roll(flakes, -1, 0) & ~flakes
    t.a[lip] = VERM[5]
    # rain streak grime and fine crazing
    for (sx, sy, ln) in ((3, 8, 6), (8, 1, 7), (14, 9, 5)):
        for k in range(ln):
            t.a[(sy + k) % 16, sx, :3] *= 0.8
    for (cx, cy) in ((7, 6), (8, 6), (9, 7), (2, 13), (3, 13)):
        t.set(cx, cy, VERM[1])
    return t


@texture('block/vermilion_top', opaque=True)
def vermilion_top():
    t = Tex(16)
    x, y = grid(16, 16)
    W = ramp(['#1a110d', '#2b1d15', '#3d2a1e', '#4e3828'], 4)
    cx, cy = 7.5, 7.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) + (vnoise(16, 16, 4, 4, 411) - 0.5) * 1.2
    v = 1.6 + np.sin(d * 2.1) * 0.9 + (white(16, 16, 412) - 0.5) * 0.6
    idx = np.clip(np.round(v), 0, 3).astype(int)
    paint_idx(t, idx, W)
    ring = (np.maximum(np.abs(x - 7.5), np.abs(y - 7.5)) > 5.6)
    t.a[ring] = VERM[3]
    outer = (np.maximum(np.abs(x - 7.5), np.abs(y - 7.5)) > 6.6)
    t.a[outer] = VERM[2]
    t.a[outer & ((x == 0) | (y == 0))] = VERM[4]
    for (px_, py_) in bresenham(8, 8, 13, 3):
        t.set(px_, py_, '#0d0807')
    t.set(7, 8, '#0d0807')
    return t


@texture('block/black_lacquer', opaque=True, tile=True)
def black_lacquer():
    t = Tex(16)
    x, y = grid(16, 16)
    low = fnoise(16, 16, 421, beta=2.4, sx=4.0, sy=1.0)
    v = 1.0 + (low > 1.1) * 1.0
    band = ((y == 3) | (y == 4))
    v = np.where(band, 3.0 + (np.sin(x * 0.55 + 0.7) > 0.55) * 1.0, v)
    v = np.where((y == 2) | (y == 5), 2.0, v)
    v = np.where((y == 12), 1.6 + (x % 5 == 1) * 0.6, v)
    paint_idx(t, np.clip(np.round(v), 0, 5).astype(int), LACQ)
    for (sx, sy) in ((5, 3), (12, 4)):
        t.set(sx, sy, '#6a6068')
    # worn-through spots showing the red undercoat (negoro), dust, scratches
    for (wx, wy) in ((11, 9), (12, 9), (12, 10)):
        t.set(wx, wy, '#3e130e')
    t.set(12, 10, '#4a1610')
    for (px_, py_) in bresenham(2, 7, 7, 8):
        t.set(px_, py_, LACQ[3])
    for (dx, dy) in ((13, 13), (6, 15), (9, 12), (1, 9)):
        t.set(dx, dy, LACQ[3])
    return t


def _torii_frames(active):
    N, S, K = 16, 64, 4
    sd = 431 + active * 10
    n1 = fnoise(S, S, sd, beta=2.2, hi=4.5)
    n2 = fnoise(S, S, sd + 1, beta=2.2, hi=4.5)
    th = fnoise(S, S, sd + 2, beta=2.5, hi=3.0) * math.pi
    tint = fnoise(S, S, sd + 3, beta=2.5, hi=3.0)

    def samp(f, xs, ys):
        x0 = np.floor(xs).astype(int)
        y0 = np.floor(ys).astype(int)
        fx = xs - x0
        fy = ys - y0
        x0 %= S
        y0 %= S
        x1 = (x0 + 1) % S
        y1 = (y0 + 1) % S
        return (f[y0, x0] * (1 - fx) * (1 - fy) + f[y0, x1] * fx * (1 - fy) +
                f[y1, x0] * (1 - fx) * fy + f[y1, x1] * fx * fy)

    x, y = grid(S, S)
    if not active:
        P = pal('#060405', '#0d070a', '#170a11', '#290c16', '#41111f', '#5b1823', '#762322')
        A = np.array([122, 135, 150, 165, 180, 192, 200])
    else:
        P = pal('#661510', '#952617', '#b9512a', '#d2914a', '#e2bb70', '#eedaa8', '#f5ecd6')
        A = np.array([196, 198, 200, 204, 208, 212, 216])
    frames = []
    for i in range(N):
        tt = i / N
        a = 2 * math.pi * tt
        wx = 7.0 * np.cos(th + a)
        wy = 7.0 * np.sin(th + a)
        f1 = samp(n1, x + wx + S * tt, y + wy)
        f2 = samp(n2, x - wy, y + wx - S * tt)
        f = 0.7 * f1 + 0.6 * f2
        ridge = 1.0 - np.abs(np.tanh(f * 1.1))
        if not active:
            v = ridge ** 2.3 * 1.0 + 0.04 * samp(tint, x - S * tt, y)
        else:
            v = 0.22 + ridge ** 1.15 * 0.84
        v = v.reshape(16, K, 16, K).mean((1, 3))
        idx = np.clip((v * len(P)).astype(int), 0, len(P) - 1)
        fr = Tex(16, 16)
        fr.a[:] = P[idx]
        fr.a[..., 3] = A[idx]
        if not active:
            tv = samp(tint, x + S * tt * 0, y).reshape(16, K, 16, K).mean((1, 3))
            m = (tv > 0.25) & (idx >= 3)
            fr.a[m, :3] = fr.a[m, :3] * 0.55 + np.array([62, 24, 86]) * 0.45
        frames.append(fr)
    return frames


@texture('block/torii_gate', frames=16)
def torii_gate():
    return _torii_frames(0)


@texture('block/torii_gate_active', frames=16)
def torii_gate_active():
    return _torii_frames(1)


# ---------------------------------------------------------------------------
# shimenawa + decals
# ---------------------------------------------------------------------------
STRAW = ramp(['#2f2617', '#4d3f25', '#6c5a36', '#8a7547', '#a38c57', '#b49d66'], 6)
SHIDE = pal('#8f8775', '#aaa28e', '#c2baa5', '#d2cab4')


@texture('block/shimenawa', cutout=True, tile=True)
def shimenawa():
    t = Tex(16)
    # twisted rope y=1..5: strands as diagonal bands, period 4
    for xx in range(16):
        for yy in range(1, 6):
            ph = (xx + yy) % 4
            lv = [4, 3, 2, 1][ph]
            if yy == 1:
                lv = min(5, lv + 1)
            if yy == 5:
                lv = max(0, lv - 2)
            t.set(xx, yy, STRAW[lv])
        t.set(xx, 0, STRAW[1] if (xx % 4) in (1, 2) else (0, 0, 0, 0))
        if (xx % 4) == 3:
            t.set(xx, 6, STRAW[0])
    # straw tassels
    for (fx, ln) in ((1, 4), (2, 3), (14, 4), (15, 3)):
        for k in range(ln):
            t.set(fx, 6 + k, STRAW[3 - (k % 2)])
    # two zigzag shide
    zig = ["##..", "###.", ".##.", ".###", "..##", ".###", ".##.", "###.", "##..", "#..."]
    for ox in (4, 9):
        for j, row in enumerate(zig):
            first = row.index('#')
            for i, ch in enumerate(row):
                if ch == '#':
                    c = SHIDE[3] if i == first else SHIDE[2]
                    if i == len(row.rstrip('.')) - 1 and i != first:
                        c = SHIDE[1]
                    t.set(ox + i, 6 + j, c)
    # aged: a damp brown stain on one shide
    t.set(11, 11, '#8a7b60')
    t.set(12, 12, '#7e6f55')
    return t


@texture('block/blood_stain', cutout=True)
def blood_stain():
    t = Tex(16)
    f = np.maximum.reduce([blob(16, 16, 7.0, 8.0, 4.6, 3.4, 501, rough=0.4, wrap=False, cells=8),
                           blob(16, 16, 11.5, 11.5, 2.4, 1.6, 502, rough=0.3, wrap=False) * 0.8,
                           blob(16, 16, 3.5, 4.5, 1.6, 1.3, 503, rough=0.3, wrap=False) * 0.7])
    m = f > 0
    for (dx, dy) in ((13, 4), (14, 6), (2, 12), (12, 14), (1, 9), (10, 2), (5, 14), (14, 13), (15, 9)):
        m[dy, dx] = True
    B = BLOOD
    idx = np.where(f > 0.55, 0, np.where(f > 0.25, 1, 2))
    idx = np.where(m & (f <= 0), 2, idx)
    t.a[m] = B[idx][m]
    edge_px = m & ~erode(m, 1) & (white(16, 16, 504) < 0.35)
    t.a[edge_px] = B[3]
    for (sx, sy) in ((6, 6), (7, 6), (10, 10)):
        if m[sy, sx]:
            t.set(sx, sy, '#6e3026')
    return t


def _wavy(t, x0, y0, x1, slope, amp, lam, ph, colors, seed, vertical=False):
    """1px wavy strand traced along its main axis (continuous, gap-filled)."""
    r = rng(seed)
    last = None
    step = 1 if x1 >= x0 else -1
    for i, a in enumerate(range(x0, x1 + step, step)):
        b = y0 + slope * (a - x0) + amp * math.sin(2 * math.pi * (a - x0) / lam + ph)
        b = int(round(b))
        pts = [(a, b)]
        if last is not None and abs(b - last) > 1:
            s = 1 if b > last else -1
            pts = [(a - step if False else a, q) for q in range(last + s, b + s, s)]
        for (pa, pb) in pts:
            px_, py_ = (pb, pa) if vertical else (pa, pb)
            if 0 <= px_ < 16 and 0 <= py_ < 16:
                c = colors[0] if r.random() > 0.18 else colors[1]
                t.set(px_, py_, c, wrap=False)
        last = b


@texture('block/hair_strands', cutout=True)
def hair_strands():
    t = Tex(16)
    HC = pal('#070606', '#141113', '#1f1c21', '#363541')
    # a tangled lock: strands leave a clump at the lower left and snake across the floor
    strands = [  # (x0, y0, x1, slope, amp, lam, phase, vertical)
        (1, 14, 15, -0.55, 1.0, 7.0, 0.0, False),
        (2, 15, 15, -0.25, 1.1, 8.0, 1.7, False),
        (1, 13, 15, -0.85, 0.9, 6.0, 3.0, False),
        (2, 15, 15, 0.0, 0.8, 9.0, 4.4, False),
        (14, 1, 0, -0.30, 1.0, 7.0, 0.8, True),
        (15, 3, 2, -0.65, 0.9, 6.5, 2.2, True),
    ]
    for k, (x0, y0, x1, sl, amp, lam, ph, vert) in enumerate(strands):
        _wavy(t, x0, y0, x1, sl, amp, lam, ph, (HC[0], HC[2]), 530 + k, vertical=vert)
    for (gx, gy) in ((6, 11), (9, 8), (12, 12), (11, 4)):
        if t.a[gy, gx, 3] > 0:
            t.set(gx, gy, HC[3])
    return t


FOOT_R = [
    "##.#..",
    "##..#.",
    ".....#",
    ".###..",
    "#####.",
    "#o###.",
    ".####.",
    "..###.",
    "..###.",
    ".####.",
    ".#o##.",
    "..##..",
]


@texture('block/footprints', cutout=True)
def footprints():
    t = Tex(16)
    WET = {'#': '#191715', 'o': '#47433d'}
    for (ox, oy, flip) in ((9, 0, False), (1, 4, True)):
        w = len(FOOT_R[0])
        for j, row in enumerate(FOOT_R):
            for i, ch in enumerate(row):
                if ch in WET:
                    xx = ox + (w - 1 - i if flip else i)
                    t.set(xx, oy + j, WET[ch], wrap=False)
    for (dx, dy) in ((15, 14), (7, 1), (12, 15), (0, 0)):
        t.set(dx, dy, '#24211e')
    return t


# ---------------------------------------------------------------------------
# futon
# ---------------------------------------------------------------------------
INDIGO = ramp(['#14171f', '#1e222c', '#292e39', '#353b47', '#424956', '#525a66'], 6)


def _futon_fabric(seed):
    t = Tex(16)
    low = fnoise(16, 16, seed, beta=1.8)
    v = 2.8 + low * 0.6 + (white(16, 16, seed + 1) - 0.5) * 0.7
    idx = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, idx, INDIGO)
    # faded igeta (井) kasuri pattern
    PAT = C('#6f7279')
    PAT2 = C('#5b5f68')
    for (cx, cy) in ((3, 3), (11, 11), (11, 3), (3, 11)):
        if (cx, cy) in ((11, 3), (3, 11)):
            continue
        for d in (-1, 1):
            for k in range(-2, 3):
                t.set(cx + d, cy + k, PAT if k % 2 else PAT2)
                t.set(cx + k, cy + d, PAT if k % 2 else PAT2)
    for (cx, cy) in ((11, 3), (3, 11)):
        t.set(cx, cy, PAT2)
        t.set(cx + 1, cy, PAT2)
        t.set(cx, cy + 1, PAT2)
    return t


@texture('block/futon_top', opaque=True)
def futon_top():
    t = Tex(16)
    x, y = grid(16, 16)
    # quilted puffs between the ties: each 8x8 cell bulges (lit from the upper left)
    u = (x % 8) - 3.5
    v = (y % 8) - 3.5
    puff = 1 - (u ** 2 + v ** 2) / 24.5
    lightdir = -(u + v) * 0.10
    val = 2.4 + puff * 1.6 + lightdir + fnoise(16, 16, 601, beta=1.8) * 0.3 + (white(16, 16, 602) - 0.5) * 0.5
    paint_idx(t, np.clip(np.round(val), 0, 5).astype(int), INDIGO)
    # faded white kasuri crosses scattered on the cover
    PAT = C('#7d8088')
    PAT2 = C('#60646d')
    for (cx, cy) in ((3, 2), (11, 5), (6, 10), (13, 13), (1, 13), (9, 1)):
        t.set(cx, cy, PAT)
        t.set(cx - 1, cy, PAT2)
        t.set(cx + 1, cy, PAT2)
        t.set(cx, cy - 1, PAT2)
        t.set(cx, cy + 1, PAT2)
    # quilting ties (white thread tufts) at the cell corners
    for (qx, qy) in ((0, 0), (8, 0), (0, 8), (8, 8)):
        t.set(qx, qy, '#9a968a')
        t.set(qx + 1, qy, '#6e6b63')
        t.a[(qy + 1) % 16, qx, :3] *= 0.6
    # an old stain soaked through: yellow-brown tide ring, rust-dark core
    st = blob(16, 16, 9.0, 9.5, 4.6, 3.6, 603, rough=0.45, wrap=False)
    m = st > 0
    t.a[m, :3] = t.a[m, :3] * 0.6 + np.array([92, 76, 48]) * 0.4
    core = st > 0.5
    t.a[core, :3] = t.a[core, :3] * 0.62 + np.array([64, 28, 20]) * 0.38
    rim = m & ~erode(m, 1)
    t.a[rim, :3] = t.a[rim, :3] * 0.7 + np.array([70, 54, 32]) * 0.3
    return t


@texture('block/futon_side', opaque=True)
def futon_side():
    t = _futon_fabric(611)
    for xx in range(16):
        for yy in (4, 10, 14):
            t.a[yy, xx, :3] = t.a[yy, xx, :3] * 0.55 + 85 * 0.45     # piping / seam highlight
        for yy in (5, 11, 15):
            t.a[yy, xx, :3] *= 0.6
    st = blob(16, 16, 10.0, 12.0, 4.0, 3.0, 612, rough=0.5, wrap=False) > 0
    t.a[st, :3] = t.a[st, :3] * 0.65 + np.array([88, 70, 44]) * 0.35
    return t


# ---------------------------------------------------------------------------
# kakejiku (hanging scroll)
# ---------------------------------------------------------------------------
SCROLL_BACK = [
    "..KKKK..",
    ".KKKKKK.",
    "KKKKKKKK",
    ".KKKKKK.",
    "..KppK..",
    ".gkppkg.",
    "ggkkkkgg",
    "gglllggg",
]
SCROLL_FACE = [
    "..KKKK..",
    ".KKKKKK.",
    "KKppppKK",
    ".KepepK.",
    "..pmmp..",
    ".gkppkg.",
    "ggkpkkgg",
    "gglllggg",
]


def _kakejiku(face):
    t = Tex(16)
    IND = pal('#141725', '#1d2133', '#272c42')
    BRN = pal('#2e2219', '#3f2f22')
    GLD = C('#7c6a3a')
    ROD = pal('#1a1310', '#2c211a', '#3e3024')
    # cord
    t.set(7, 0, '#3a2a1e')
    t.set(8, 0, '#3a2a1e')
    for xx in range(3, 13):
        t.set(xx, 1, ROD[1] if xx % 3 else ROD[2])
        for yy in (2, 3):
            t.set(xx, yy, IND[1] if (xx + yy) % 3 else IND[2])
        t.set(xx, 4, GLD if 4 <= xx <= 11 else BRN[1])
        t.set(xx, 13, GLD if 4 <= xx <= 11 else BRN[1])
        t.set(xx, 14, IND[1] if xx % 4 else IND[2])
        t.set(xx, 15, ROD[1])
    for yy in range(5, 13):
        t.set(3, yy, BRN[0])
        t.set(12, yy, BRN[1])
    # futai strips
    for yy in (2, 3, 4):
        t.set(5, yy, '#5a4a36')
        t.set(10, yy, '#5a4a36')
    t.set(5, 5, '#4a3c2c')
    t.set(10, 5, '#4a3c2c')
    # roller knobs
    t.set(2, 15, ROD[2])
    t.set(13, 15, ROD[2])
    t.set(2, 14, (0, 0, 0, 0))
    # paper 8x8 at x=4..11, y=5..12
    paper = _paper(621 + face, 8, 8, P=ramp(['#9d9480', '#b6ad95', '#c8bfa6', '#d3cab0'], 4),
                   base=0.66, amp=0.8)
    for yy in range(8):
        for xx in range(8):
            if not (xx in (1, 6) and yy < 2):
                t.a[5 + yy, 4 + xx] = paper.a[yy, xx]
    for yy in range(5, 6):
        t.set(5, yy, '#4a3c2c')
        t.set(10, yy, '#4a3c2c')
    INK = {'K': '#141212', 'k': '#3a3732', 'g': '#6c675d', 'l': '#8f897c', 'p': '#ddd6c4',
           'e': '#030303', 'm': '#c9c0ac'}
    if face:
        INK['m'] = '#d8d0bd'
    rows = SCROLL_FACE if face else SCROLL_BACK
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in INK:
                t.set(4 + i, 5 + j, INK[ch])
    # hair ornament (kanzashi) pin
    t.set(10, 4, '#8a8170')
    t.set(9, 5, '#a69c86')
    # ink inscription + seal
    t.set(11, 6, '#5f5a50')
    t.set(11, 7, '#4a463f')
    t.set(11, 9, '#7d2a1e')
    t.set(11, 10, '#6a2219')
    if face:
        # ink has run from the hollow eyes
        t.set(5, 9, '#5d584f')
        t.set(8, 9, '#6c675d')
        t.set(8, 10, '#8a8478')
    # stains on the mounting / paper
    t.a[12, 5, :3] *= 0.85
    t.a[11, 6, :3] *= 0.9
    for xx in (0, 1, 14, 15):
        for yy in range(16):
            t.clear(xx, yy)
    for yy in range(16):
        if yy not in (14, 15):
            t.clear(2, yy)
            t.clear(13, yy)
    for xx in range(16):
        if xx not in (7, 8):
            t.clear(xx, 0)
    t.set(2, 15, ROD[2])
    t.set(13, 15, ROD[2])
    return t


@texture('block/kakejiku', cutout=True)
def kakejiku():
    return _kakejiku(0)


@texture('block/kakejiku_face', cutout=True)
def kakejiku_face():
    return _kakejiku(1)
