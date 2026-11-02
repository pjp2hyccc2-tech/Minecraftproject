"""Man-made materials of the abandoned hamlet, the hut, the road and the tunnel; doors; decals.
Everything is decades old: bleached, rusted, mouldy, cracked (art direction §9)."""
import math

import numpy as np

from common import Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, rng, blob, lighten_arr, \
    dilate, erode, edge, equalize
from px import paint_idx, despeckle, emboss, voronoi, jitter_points, stamp, curve_pts, line_pts, overlay, \
    outline_alpha, shade_sprite, tint, cutout

GREYWOOD = ramp(['#1f1c19', '#2e2a26', '#403a35', '#534c45', '#665e56', '#787067', '#8a8278', '#9a9288'], 8)
DARKWOOD = ramp(['#0c0a08', '#15110e', '#1e1814', '#28201b', '#322822', '#3d312a', '#483a31'], 7)
PLASTER = ramp(['#3f3b35', '#524d45', '#656057', '#777168', '#878177', '#958f84', '#a29c90'], 7)
MOULD = pal('#1b1f14', '#252a1b', '#323823')
RUST = ramp(['#2a160c', '#432212', '#5e3019', '#7a4022', '#93522d', '#a6653a'], 6)
TINPAINT = ramp(['#1e100d', '#311913', '#43221a', '#552c21', '#653629'], 5)
CONC = ramp(['#232425', '#2e2f30', '#3a3b3b', '#464747', '#525352', '#5e5f5d', '#6b6c69', '#787975'], 8)
ASPH = ramp(['#121314', '#18191a', '#1f2021', '#262728', '#2e2f30', '#373839', '#414243'], 7)
GALV = ramp(['#2b2e30', '#41464a', '#5a5f63', '#71777a', '#8a9093', '#a3a8aa'], 6)
TARP = ramp(['#14253a', '#1c3350', '#254466', '#2f557b', '#3b6690', '#4c78a0', '#6189ab'], 7)
GLASS = ramp(['#3d4447', '#4f575a', '#61696b', '#737b7c', '#848b8b', '#959b9a'], 6)
IRON = ramp(['#110f0e', '#1d1a18', '#2a2623', '#38332f', '#47413b'], 5)


# ------------------------------------------------------------------------------------------------ boards
def boards(seed, P, rows=4, base=4.0, tone=None, joints=None, streak=0.7, gap_lv=0, knots=(), lmin=3, lmax=9):
    """Horizontal boards (3 px wood + 1 px gap each), grain made of coherent streaks."""
    W = H = 16
    r = rng(seed)
    v = np.zeros((H, W))
    tone = tone or [0] * rows
    for b in range(rows):
        y0 = b * 4
        for k, off in enumerate((0.6, 0.0, -0.7)):
            yy = y0 + k
            v[yy, :] = base + tone[b] + off
            for _ in range(3):
                xs, ln = int(r.integers(0, W)), int(r.integers(lmin, lmax))
                d = -streak if r.random() < 0.65 else streak
                for q in range(ln):
                    v[yy, (xs + q) % W] += d
        v[y0 + 3, :] = gap_lv + (r.random(W) < 0.2) * 0.8
    joints = joints or []
    for b, jx in enumerate(joints):
        if jx is None:
            continue
        for yy in range(b * 4, b * 4 + 3):
            v[yy, jx % W] = gap_lv + 0.5
            v[yy, (jx + 1) % W] += 0.6
    for (kx, ky) in knots:
        v[ky, kx] = base - 2
        v[ky, (kx + 1) % W] = base - 2.5
        v[ky, (kx - 1) % W] = base + 0.8
    return v


@texture('block/weathered_planks', opaque=True, tile=True)
def weathered_planks():
    """Sun-bleached grey old boards (hut walls, village houses): cracks, nail holes bleeding rust."""
    v = boards(201, GREYWOOD, base=4.6, tone=[0.3, -0.4, 0.5, -0.2], joints=[6, 13, 2, 10], knots=((11, 9),))
    q = np.clip(np.round(v), 0, 7).astype(int)
    t = Tex(16)
    paint_idx(t, q, GREYWOOD)
    # a warm brown cast deeper in the grain (old cedar under the grey)
    warm = vnoise(16, 16, 2, 4, 202) > 0.62
    t.a[warm, :3] *= np.array([1.06, 0.98, 0.9])
    # splits along the grain
    for (x0, y0, ln) in ((2, 1, 4), (9, 6, 3), (12, 13, 3)):
        for k in range(ln):
            t.set(x0 + k, y0, GREYWOOD[1])
    # nails with rust runs
    for (nx, ny) in ((5, 1), (5, 9), (12, 5), (1, 13)):
        t.set(nx, ny, '#1a120d')
        t.set(nx, ny + 1, RUST[2])
        t.blend(nx, ny + 2, RUST[3], 0.5)
    return t


@texture('block/dark_planks', opaque=True, tile=True)
def dark_planks():
    """Dark rotten interior floor boards: soft black rot, pale fungus specks, a broken gap."""
    v = boards(211, DARKWOOD, base=3.8, tone=[0.2, -0.3, 0.4, 0], joints=[4, 11, 14, 7], streak=0.6)
    q = np.clip(np.round(v), 0, 6).astype(int)
    t = Tex(16)
    paint_idx(t, q, DARKWOOD)
    rot = blob(16, 16, 10.0, 9.5, 3.4, 1.6, 212, rough=0.5) > 0
    t.a[rot, :3] *= 0.62
    for (fx, fy) in ((9, 9), (12, 10), (11, 8), (3, 2)):
        t.set(fx, fy, '#6d665a')
    for xx in range(7, 10):
        t.set(xx, 7, '#050404')
    t.set(8, 6, DARKWOOD[0])
    for (nx, ny) in ((2, 1), (13, 5), (6, 13)):
        t.set(nx, ny, '#0a0807')
    return t


@texture('block/old_plaster', opaque=True, tile=True)
def old_plaster():
    """Village wall: dirty cracked plaster, mould creeping up from below, water streaks, a chipped spot."""
    W = H = 16
    n = fnoise(W, H, 221, beta=1.2) * 0.6 + fnoise(W, H, 222, beta=0.3) * 0.4
    x, y = grid(W, H)
    v = 4.2 + n
    streak = fnoise(W, H, 223, beta=1.0, sx=1, sy=8)
    v -= np.clip(streak, 0, None) * 0.8
    q = np.clip(np.round(v), 0, 6).astype(int)
    q = despeckle(q)
    t = Tex(16)
    paint_idx(t, q, PLASTER)
    # mould: dark green-black blotches, denser low down (the wall's foot is always damp)
    mo = fnoise(W, H, 224, beta=1.6) * 0.8 + (y / 15.0) * 1.0 - 0.5
    m = mo > 0.8
    t.a[m, :3] = t.a[m, :3] * 0.62 + MOULD[2][:3] * 0.38
    m2 = mo > 1.15
    t.a[m2, :3] = t.a[m2, :3] * 0.7 + MOULD[0][:3] * 0.3
    # cracks
    for pts in (((3, 1), (4, 3), (3, 6)), ((12, 7), (13, 9), (12, 11))):
        for (px_, py_) in curve_pts(pts):
            t.set(px_, py_, PLASTER[1])
            t.dl(px_ + 1, py_, 0.03)
    # a chip where the plaster fell away, showing the dark mud layer (small, so long walls do not repeat a pattern)
    for (cx, cy) in ((9, 11), (10, 11), (9, 12)):
        t.set(cx, cy, '#3b2f22')
    t.dl(9, 10, -0.06)
    t.dl(10, 10, -0.06)
    return t


@texture('block/tin_roof', opaque=True, tile=True)
def tin_roof():
    """Rusted corrugated iron (トタン), ribs vertical: faded red-brown paint flaking off into rust."""
    W = H = 16
    x, y = grid(W, H)
    prof = np.array([3, 4, 3, 2, 1, 1, 2, 2])          # 8 px corrugation: crest lit, trough dark
    pv = prof[x.astype(int) % 8].astype(float)
    t = Tex(16)
    paint = np.clip(np.round(pv * 0.9 + fnoise(W, H, 231, beta=0.8, sx=1, sy=4) * 0.4), 0, 4).astype(int)
    paint_idx(t, paint, TINPAINT)
    rustm = (fnoise(W, H, 232, beta=1.3) + fnoise(W, H, 233, beta=0.8, sx=1, sy=6) * 0.6) > 0.7
    rv = np.clip(np.round(pv * 0.9 + fnoise(W, H, 234, beta=0.4) * 0.5 - 0.3), 0, 5).astype(int)
    t.a[rustm] = RUST[rv][rustm]
    # rust runs down from the nails
    for (nx, ny) in ((1, 2), (9, 10)):
        t.set(nx, ny, '#0f0b09')
        for k in range(1, 5):
            t.blend(nx, ny + k, RUST[3], 0.5 - k * 0.1)
    # dark overlap seam where one sheet lies over the next
    for xx in range(16):
        t.dl(xx, 15, -0.08)
    return t


@texture('block/blue_tarp', opaque=True, tile=True)
def blue_tarp():
    """Faded dirty blue plastic sheet (ブルーシート): woven grid, creases, mud and needles on it."""
    W = H = 16
    x, y = grid(W, H)
    weave = ((x % 2 == 0) & (y % 2 == 0)) * 0.6 - ((x % 2 == 1) & (y % 2 == 1)) * 0.4
    fade = vnoise(W, H, 2, 2, 241)
    v = 3.6 + weave + fade * 1.2
    # creases: a lit ridge with shadow below
    for (a, b, c_, d) in ((0, 4, 16, 9), (3, 15, 12, 0)):
        for (px_, py_) in line_pts(a, b, c_, d):
            v[py_ % 16, px_ % 16] += 1.4
            v[(py_ + 1) % 16, px_ % 16] -= 1.3
    q = np.clip(np.round(v), 0, 6).astype(int)
    t = Tex(16)
    paint_idx(t, q, TARP)
    # sun-bleached greyish areas
    bleach = fade > 0.7
    t.a[bleach, :3] = t.a[bleach, :3] * 0.8 + np.array([120, 125, 128]) * 0.2
    # mud smears and stuck needles
    mud = blob(16, 16, 11.0, 12.0, 3.0, 1.8, 242, rough=0.5) > 0
    t.a[mud, :3] = t.a[mud, :3] * 0.45 + np.array([70, 52, 36]) * 0.55
    for (a, b, c_, d) in ((3, 6, 5, 7), (13, 3, 14, 5), (6, 12, 7, 12)):
        for (px_, py_) in line_pts(a, b, c_, d):
            t.set(px_, py_, '#5e4430')
    return t


def concrete(seed=251):
    W = H = 16
    x, y = grid(W, H)
    v = 4.0 + fnoise(W, H, seed, beta=1.3) * 0.5 + fnoise(W, H, seed + 1, beta=0.1) * 0.45
    # vertical water streaks
    st = fnoise(W, H, seed + 2, beta=1.2, sx=1, sy=10)
    v -= np.clip(st, 0, None) * 1.1
    # formwork joint
    v[7, :] -= 1.0
    v[8, :] += 0.4
    q = np.clip(np.round(v), 0, 7).astype(int)
    q = despeckle(q)
    t = Tex(16)
    paint_idx(t, q, CONC)
    return t


@texture('block/concrete_old', opaque=True, tile=True)
def concrete_old():
    """Tunnel lining: stained grey concrete, a crack weeping white efflorescence, tie-rod holes."""
    t = concrete()
    EFF = pal('#7d7e78', '#94958e', '#aaaba3')
    path = curve_pts([(5, 4), (6, 6), (5, 8), (6, 10)])
    for (px_, py_) in path:
        t.set(px_, py_, CONC[1])
    for i, (px_, py_) in enumerate(path):
        if i % 3 == 1:
            t.blend(px_ + 1, py_, EFF[1], 0.6)
    for k in range(3):
        t.blend(6, 11 + k, EFF[2], 0.55 - k * 0.15)
    for (hx, hy) in ((12, 3), (12, 12)):
        t.set(hx, hy, CONC[0])
        t.set(hx + 1, hy, CONC[1])
        t.set(hx, hy + 1, CONC[1])
        t.blend(hx, hy + 2, '#5a3a24', 0.5)
    return t


@texture('block/concrete_moss', opaque=True, tile=True)
def concrete_moss():
    """Tunnel portal: concrete with moss in the joints and dead ivy hanging over it."""
    from s_nature import moss_tex
    t = concrete(seed=261)
    moss, _, _ = moss_tex(seed=262, n=22, dark=0.3)
    W = H = 16
    x, y = grid(W, H)
    cover = vnoise(W, H, 4, 4, 263) * 0.7 + (np.abs(y - 7.5) < 1.5) * 0.35 + white(W, H, 264) * 0.1
    m = cover > 0.62
    m = despeckle(m.astype(int)).astype(bool)
    t.a[m] = moss.a[m]
    VINE = pal('#1f150d', '#3a2717', '#563a22')
    DRYLEAF = pal('#3b2614', '#5a3c1f', '#73502a')
    for pts in (((3, 0), (2, 4), (4, 8), (3, 12), (4, 16)), ((12, 0), (13, 3), (11, 6), (12, 9))):
        path = curve_pts(pts)
        for (px_, py_) in path:
            t.set(px_, py_, VINE[1])
            t.set(px_ + 1, py_, VINE[0])
        for i, (px_, py_) in enumerate(path):
            if i % 4 == 2:
                t.set(px_ - 1, py_, DRYLEAF[2])
                t.set(px_ - 2, py_, DRYLEAF[1])
                t.set(px_ - 1, py_ + 1, DRYLEAF[0])
    return t


@texture('block/asphalt', opaque=True, tile=True)
def asphalt():
    """Cracked dark asphalt of the forest road; weeds in the cracks."""
    W = H = 16
    v = 3.0 + fnoise(W, H, 271, beta=1.2) * 0.5 + fnoise(W, H, 272, beta=0.0) * 0.6
    q = np.clip(np.round(v), 0, 6).astype(int)
    t = Tex(16)
    paint_idx(t, q, ASPH)
    sp = white(W, H, 273)
    t.a[sp > 0.95] = C('#4e4f50')
    t.a[sp < 0.03] = C('#5a5754')
    for pts in (((0, 9), (3, 8), (6, 10), (9, 9)), ((6, 10), (7, 13), (6, 16)), ((11, 0), (12, 3), (14, 4))):
        for (px_, py_) in curve_pts(pts):
            t.set(px_, py_, ASPH[0])
            t.dl(px_ + 1, py_ + 1, 0.03)
    WEED = pal('#2a3216', '#3d4720', '#56602d')
    for (wx, wy) in ((4, 8), (12, 3)):
        t.set(wx, wy, WEED[1])
        t.set(wx + 1, wy - 1, WEED[2])
        t.set(wx - 1, wy - 1, WEED[0])
        t.set(wx, wy - 2, WEED[1])
    return t


@texture('block/steel_lattice', cutout=True, tile=True)
def steel_lattice():
    """Galvanised pylon lattice: angle-iron frame + X bracing, bolts, rust; transparent gaps."""
    t = Tex(16)
    for i in range(16):
        # frame (L-angle: lit flange + dark web)
        t.set(i, 0, GALV[4])
        t.set(i, 1, GALV[2])
        t.set(i, 15, GALV[1])
        t.set(i, 14, GALV[3])
        t.set(0, i, GALV[4])
        t.set(1, i, GALV[2])
        t.set(15, i, GALV[1])
        t.set(14, i, GALV[3])
    for i in range(2, 14):
        t.set(i, i, GALV[4])
        t.set(i + 1, i, GALV[2])
        t.set(15 - i, i, GALV[3])
        t.set(14 - i, i, GALV[1])
    for (bx, by) in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 7), (8, 8)):
        t.set(bx, by, GALV[0])
    t.set(7, 8, GALV[5])
    for (rx, ry) in ((0, 6), (1, 7), (15, 11), (9, 15), (4, 4), (11, 4), (12, 3)):
        t.set(rx, ry, RUST[2] if (rx + ry) % 2 else RUST[3])
    t.blend(15, 12, RUST[1], 0.7)
    t.blend(15, 13, RUST[2], 0.5)
    return t


# ------------------------------------------------------------------------------------------------ doors
def _door_planks(seed):
    t = Tex(16, 32)
    r = rng(seed)
    for xx in range(16):
        plank = xx // 4
        for yy in range(32):
            lv = 4.2 + [0.3, -0.4, 0.4, -0.2][plank] + (0.8 if xx % 4 == 0 else 0) - (1.0 if xx % 4 == 3 else 0)
            t.a[yy, xx] = GREYWOOD[int(np.clip(round(lv + r.normal(0, 0.3)), 0, 7))]
    streak = fnoise(16, 32, seed + 1, beta=0.8, sx=1, sy=6)
    t.a[..., :3] *= (1 + streak[..., None] * 0.05)
    return t


def _hut_door():
    """Old plank door: vertical boards, two battens, rusty strap hinges, a pull ring."""
    t = _door_planks(301)
    for (y0, y1) in ((5, 7), (24, 26)):
        for yy in range(y0, y1 + 1):
            for xx in range(16):
                lv = 5 if yy == y0 else (3 if yy == y1 else 4)
                t.set(xx, yy, GREYWOOD[lv] if xx % 4 else GREYWOOD[lv - 1])
        for xx in range(16):
            t.dl(xx, y1 + 1, -0.08)
    for (y0,) in ((5,), (24,)):
        for xx in range(0, 7):
            t.set(xx, y0 + 1, RUST[3] if xx % 3 else RUST[1])
            t.set(xx, y0, RUST[4] if xx < 6 else RUST[2])
        t.set(1, y0 + 1, IRON[0])
        t.set(4, y0 + 1, IRON[0])
    # pull ring + latch
    t.set(13, 15, IRON[3])
    t.set(12, 16, IRON[2])
    t.set(14, 16, IRON[2])
    t.set(13, 17, IRON[1])
    t.set(13, 16, GREYWOOD[1])
    # a knot hole you can see through, dirt splashed at the bottom
    t.clear(9, 11)
    for xx in range(16):
        for yy in range(28, 32):
            if (xx * 3 + yy) % 4 == 0 or yy == 31:
                t.a[yy, xx, :3] = t.a[yy, xx, :3] * 0.7 + np.array([50, 38, 28]) * 0.3
    return t


def _house_door():
    """Sliding house door (引き戸): dark wood frame, old patterned frosted glass, two panes broken."""
    t = Tex(16, 32)
    FR = DARKWOOD
    for yy in range(32):
        for xx in range(16):
            t.a[yy, xx] = GLASS[3]
    # patterned glass: soft diagonal dimples
    for yy in range(32):
        for xx in range(16):
            if (xx + yy * 2) % 5 == 0:
                t.a[yy, xx] = GLASS[4]
            elif (xx * 2 + yy) % 7 == 0:
                t.a[yy, xx] = GLASS[2]
    grime = fnoise(16, 32, 311, beta=1.2)
    t.a[..., :3] *= (0.85 + 0.12 * grime[..., None])
    # frame + muntins
    rows = [0, 1, 8, 15, 16, 22, 23]
    for yy in range(32):
        for xx in range(16):
            frame = xx in (0, 1, 14, 15) or yy in (0, 1, 30, 31) or yy in (8, 15, 16, 22) or xx == 8 or yy >= 24
            if frame:
                lv = 4 if xx in (0, 14) or yy in (0, 8, 15, 22) else 2
                if yy >= 24 and not (xx in (0, 1, 14, 15) or yy in (24, 30, 31)):
                    lv = 3 if (xx + yy) % 5 else 2          # wooden kick panel (腰板)
                t.a[yy, xx] = FR[lv]
    # broken panes: jagged holes with shards left in the corners
    for (x0, y0, x1, y1, seed) in ((9, 2, 13, 7, 312), (2, 17, 7, 21, 313)):
        r = rng(seed)
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                dx = min(xx - x0, x1 - xx)
                dy = min(yy - y0, y1 - yy)
                if dx + dy >= 1 + int(r.integers(0, 2)):
                    t.clear(xx, yy)
                elif dx + dy == 0:
                    t.a[yy, xx] = GLASS[5]
    # dark mould along the bottom rail
    for xx in range(16):
        t.dl(xx, 29, -0.08)
    return t


def _iron_gate():
    """Tunnel gate: rusty round vertical bars in a pipe frame, a heavy chain and padlock across the middle."""
    t = Tex(16, 32)
    BAR = ramp(['#100d0c', '#1d1714', '#2d231d', '#422e22', '#573b29', '#6b4a33'], 6)
    for yy in range(32):
        for xx in range(16):
            frame_v = xx in (0, 1, 14, 15)
            frame_h = yy in (1, 2, 29, 30) or yy in (15, 16)
            bar = (xx in (4, 5, 8, 9, 11, 12)) and 2 < yy < 29
            if frame_v or frame_h or bar:
                # round bars: lit left column, dark right column
                left = xx in (0, 4, 8, 11, 14) or (frame_h and yy in (1, 15, 29))
                t.a[yy, xx] = BAR[4 if left else 2]
    # rust runs: vertical streaks along the bars, darker crust in spots
    st = fnoise(16, 32, 321, beta=1.0, sx=1, sy=6)
    m = (t.a[..., 3] > 0)
    t.a[m, :3] *= (1 + st[m][:, None] * 0.12)
    for (rx, ry) in ((4, 9), (9, 20), (12, 6), (0, 24), (15, 10), (5, 26)):
        t.set(rx, ry, BAR[5])
        t.set(rx, ry + 1, BAR[1])
    # chain sagging across the bars + padlock
    CH = pal('#15120f', '#36302a', '#57504a', '#78716a')
    for xx in range(16):
        yy = 18 + int(round(1.5 * math.sin(math.pi * xx / 15)))
        if xx % 3 == 0:
            t.set(xx, yy - 1, CH[3])
            t.set(xx, yy + 1, CH[1])
            t.clear(xx, yy)
        else:
            t.set(xx, yy, CH[3] if xx % 3 == 1 else CH[2])
            t.set(xx, yy + 1, CH[0])
    PL = pal('#241b11', '#46361f', '#66502e', '#85693f')
    stamp(t, [".aa.", "a..a", "bbbb", "bccb", "bcdb", "bbbb"], 6, 19,
          {'a': CH[3], 'b': PL[1], 'c': PL[2], 'd': PL[0]})
    t.set(7, 23, PL[3])
    return t


@texture('block/hut_door_top', cutout=True)
def hut_door_top():
    return Tex.from_array(_hut_door().a[:16])


@texture('block/hut_door_bottom', cutout=True)
def hut_door_bottom():
    return Tex.from_array(_hut_door().a[16:])


@texture('block/house_door_top', cutout=True)
def house_door_top():
    return Tex.from_array(_house_door().a[:16])


@texture('block/house_door_bottom', cutout=True)
def house_door_bottom():
    return Tex.from_array(_house_door().a[16:])


@texture('block/iron_gate_top', cutout=True)
def iron_gate_top():
    return Tex.from_array(_iron_gate().a[:16])


@texture('block/iron_gate_bottom', cutout=True)
def iron_gate_bottom():
    return Tex.from_array(_iron_gate().a[16:])


def door_icon(full, outline='#0b0908'):
    """16x16 item icon: the 16x32 door squeezed to 8x16 (every other column / row pair) with an outline."""
    t = Tex(16)
    a = full.a
    for yy in range(15):
        for xx in range(8):
            sy = int(yy * 32 / 15)
            px_ = a[min(sy, 31), xx * 2]
            if px_[3] > 0:
                t.a[yy, 4 + xx] = px_
            else:
                t.a[yy, 4 + xx] = 0
    outline_alpha(t, outline)
    return t


@texture('item/hut_door', cutout=True)
def hut_door_item():
    return door_icon(_hut_door())


@texture('item/house_door', cutout=True)
def house_door_item():
    return door_icon(_house_door())


@texture('item/iron_gate', cutout=True)
def iron_gate_item():
    return door_icon(_iron_gate())


# ------------------------------------------------------------------------------------------------ decals
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
    """Muddy bare footprints (retouched from MEIKON's wet ones): something walked in from the forest."""
    t = Tex(16)
    MUD = {'#': '#2d2117', 'o': '#4a3a2a'}
    for (ox, oy, flip) in ((9, 0, False), (1, 4, True)):
        w = len(FOOT_R[0])
        for j, row in enumerate(FOOT_R):
            for i, ch in enumerate(row):
                if ch in MUD:
                    xx = ox + (w - 1 - i if flip else i)
                    t.set(xx, oy + j, MUD[ch], wrap=False)
    for (dx, dy, c) in ((15, 14, '#34271b'), (7, 1, '#2a1f16'), (12, 15, '#3a2c1f'), (0, 0, '#2a1f16'),
                        (6, 10, '#3e2f21'), (14, 12, '#2d2117'), (5, 3, '#33261b')):
        t.set(dx, dy, c)
    # a few cedar needles stuck in the mud
    t.set(11, 5, '#6a4b33')
    t.set(12, 6, '#6a4b33')
    t.set(3, 12, '#5a3f2b')
    return t
