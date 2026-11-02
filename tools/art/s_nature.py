"""Nature / ground textures: cedar bark and needles, forest floor, soil, moss, trail, rock, river stones,
fallen leaves. These carry the whole world (an endless cedar forest at night) -> everything tiles
seamlessly, has low-contrast large-scale variation and no single feature that reveals the grid
(nothing runs straight across a tile edge to edge)."""
import math

import numpy as np

from common import Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, rng, equalize, norm01, blob, \
    lighten_arr, wrapdist, dilate, erode, bayer
from px import paint_idx, levels, despeckle, emboss, voronoi, jitter_points, stamp, curve_pts, line_pts, \
    shade_sprite, overlay, tint

# ------------------------------------------------------------------------------------------------ palettes
# cedar bark: warm red-brown in the furrows and fresh fibres, weathering to grey-brown on the outer fibres
BARK_R = ramp(['#100a07', '#1d130e', '#2c1d15', '#3c271d', '#4c3226', '#5b3e30', '#6a4b3c', '#795949'], 8)
BARK_G = ramp(['#120f0d', '#201a17', '#302823', '#413731', '#52473f', '#63584e', '#73685e', '#82776d'], 8)
# dead cedar needles on the ground (reddish brown, slightly desaturated)
LITTER = ramp(['#140e0b', '#221813', '#32231a', '#432f22', '#553c2b', '#674a35', '#785940', '#87674c'], 8)
SOIL = ramp(['#0e0a08', '#17110d', '#211812', '#2c2018', '#38291e', '#453326'], 6)
MOSS = ramp(['#10150a', '#18200d', '#212b11', '#2b3615', '#35411a', '#404d20', '#4d5a28', '#5c6932'], 8)
ROCK = ramp(['#121315', '#1b1d20', '#25282b', '#2f3236', '#3a3d41', '#45494d', '#52565a', '#616569'], 8)
LICHEN = pal('#4d5549', '#636d5d', '#7a8573', '#8e9886')
NEEDLE = ramp(['#060c0a', '#0a1310', '#0f1b16', '#15241d', '#1b2e25', '#22392e', '#2b4538', '#365244'], 8)
TRAIL = ramp(['#1b140f', '#271d16', '#34271d', '#413125', '#4f3c2d', '#5d4836', '#6b543f'], 7)
ROOT = pal('#1a110c', '#342117', '#4d3122', '#62412e')
LEAF_RUST = pal('#3e1c10', '#5a2a17', '#753a20', '#8c4a2a', '#9e5c37')
LEAF_OCHRE = pal('#4a3714', '#67501d', '#816828', '#987d34', '#a98e44')
LEAF_BROWN = pal('#2c1d0e', '#402a16', '#54381f', '#674829', '#775735')


def _wrap_put(v, pts, val, mode='max'):
    H, W = v.shape
    for (x, y) in pts:
        if mode == 'max':
            v[y % H, x % W] = max(v[y % H, x % W], val)
        elif mode == 'min':
            v[y % H, x % W] = min(v[y % H, x % W], val)
        else:
            v[y % H, x % W] = val


# ------------------------------------------------------------------------------------------------ cedar bark
def bark_tex(seed=11, grey=(1, 3)):
    """Sugi bark: long stringy vertical fibres in strips split by meandering dark furrows; some strips
    weathered grey. Every feature has period 16 vertically, so 14-block trunks show no seams."""
    W = H = 16
    r = rng(seed)
    yy = np.arange(H)
    furrows = [(0.0, 0.6, 0.3), (3.5, 0.5, 2.0), (7.0, 0.8, 4.1), (10.3, 0.4, 1.0), (13.1, 0.7, 5.2)]
    fx = np.array([[b + a * math.sin(2 * math.pi * y_ / H + ph) for y_ in yy] for (b, a, ph) in furrows])
    x, y = grid(W, H)
    d = np.full((H, W), 99.0)
    side = np.zeros((H, W))
    sid = np.zeros((H, W), int)
    for i in range(len(furrows)):
        dd = (x - fx[i][:, None] + W / 2) % W - W / 2
        a = np.abs(dd)
        closer = a < d
        d = np.where(closer, a, d)
        side = np.where(closer, np.sign(dd), side)
        sid = np.where(closer & (dd >= 0), i, np.where(closer & (dd < 0), (i - 1) % len(furrows), sid))
    # per-column fibre tone varying slowly along y (long strings), plus fine grain
    col_tone = r.normal(0, 0.55, W)
    v = 3.9 + col_tone[None, :].repeat(H, 0)
    for xx in range(W):
        ph = r.random() * 6.28
        k = r.choice([1, 1, 2])
        v[:, xx] += 0.55 * np.sin(2 * math.pi * k * yy / H + ph)
    v += fnoise(W, H, seed + 2, beta=0.5, sx=1.0, sy=5.0) * 0.3
    strip_tone = r.normal(0, 0.35, len(furrows))
    v += strip_tone[sid]
    v += np.where((side > 0) & (d < 1.5), 0.8, 0)       # lit edge right of a furrow (light from the left)
    v -= np.where((side < 0) & (d < 1.3), 1.0, 0)       # shadowed edge before the next furrow
    furrow = d < 0.55
    v = np.where(furrow, 0.6 + (white(W, H, seed + 3) > 0.8) * 0.7, v)
    # lifted fibre strings: long bright streaks with a dark shadow line to their right
    for (sx, sy, ln) in ((5, 1, 7), (12, 7, 8), (2, 10, 6), (9, 12, 5)):
        for k in range(ln):
            py_ = (sy + k) % H
            if not furrow[py_, sx % W]:
                v[py_, sx % W] = max(v[py_, sx % W], 5.6 if 0 < k < ln - 1 else 4.8)
            if not furrow[py_, (sx + 1) % W]:
                v[py_, (sx + 1) % W] = min(v[py_, (sx + 1) % W], 2.2)
    # one split across a strip
    v[5, 8] = 1.0
    v[5, 9] = 1.3
    v[4, 8] += 0.7
    q = np.clip(np.round(v), 0, 7).astype(int)
    t = Tex(16)
    paint_idx(t, q, BARK_R)
    gfade = 0.5 + 0.5 * np.sin(2 * math.pi * (y + seed * 3) / H) ** 2
    for g in grey:
        m = (sid == g) & ~furrow
        k = (gfade * 0.8)[m][:, None]
        t.a[m, :3] = BARK_R[q[m]][:, :3] * (1 - k) + BARK_G[q[m]][:, :3] * k
    for (fi, my) in ((0, 5), (2, 12)):
        px_ = int(round(fx[fi][my])) % W
        t.set(px_, my, '#1e2311')
        t.set(px_, my + 1, '#181c0e')
    return t


@texture('block/cedar_log', opaque=True, tile=True)
def cedar_log():
    return bark_tex()


@texture('block/cedar_log_top', opaque=True)
def cedar_log_top():
    """Cut cedar end: reddish heartwood, paler sapwood, concentric rings, one drying crack, bark rim."""
    W = H = 16
    x, y = grid(W, H)
    cx, cy = 7.4, 7.9
    ang = np.arctan2(y - cy, x - cx)
    d = np.hypot(x - cx, (y - cy) * 1.04) + 0.25 * np.sin(ang * 2 + 0.7) + 0.15 * np.sin(ang * 5 + 2.0)
    HEART = ramp(['#2e170e', '#3e1f13', '#4f2818', '#5f321f', '#6c3c27'], 5)
    SAP = ramp(['#4a3725', '#5b4631', '#6b553c', '#7a6347', '#887052'], 5)
    ring = (d % 1.5) < 0.7                      # dark latewood bands
    v = np.where(ring, 1, 3) + (white(W, H, 5) > 0.92) * 1
    t = Tex(16)
    t.a[:] = SAP[np.clip(v, 0, 4).astype(int)]
    heart = d < 4.4
    t.a[heart] = HEART[np.clip(v, 0, 4).astype(int)][heart]
    t.set(7, 8, '#1c0e08')
    for rr in np.linspace(3.2, 6.6, 8):
        a = 0.55 + rr * 0.04
        px_, py_ = int(round(cx + math.cos(a) * rr)), int(round(cy + math.sin(a) * rr))
        t.set(px_, py_, '#1a0d07', wrap=False)
    bark = bark_tex()
    e = np.minimum.reduce([x, y, 15 - x, 15 - y])
    t.a[e == 0] = bark.a[e == 0]
    inner = (e == 1)
    t.a[inner, :3] = bark.a[inner, :3] * 0.5
    for (a, b) in ((1, 1), (14, 1), (1, 14), (14, 14), (2, 1), (1, 2), (13, 1), (14, 2), (1, 13), (2, 14), (13, 14), (14, 13)):
        t.a[b, a] = bark.a[b, a] * np.array([0.8, 0.8, 0.8, 1.0])
    return t


def _tape_band(t, y0, P, seed=0):
    """A 2px plastic tape wrapped around the trunk (sags slightly, twisted once)."""
    for xx in range(16):
        sag = 1 if 6 <= xx <= 11 else 0
        twist = xx in (2, 3)
        top, bot = y0 + sag, y0 + 1 + sag
        if twist:
            t.set(xx, top, P[3] if xx == 2 else P[2])
            t.set(xx, bot, P[1] if xx == 2 else P[3])
        else:
            t.set(xx, top, P[3] if xx in (8, 9, 13) else P[2])
            t.set(xx, bot, P[1])
        t.dl(xx, bot + 1, -0.07)


@texture('block/cedar_log_ribbon', opaque=True, tile=True)
def cedar_log_ribbon():
    """Bark with a faded fluorescent pink marking tape (目印テープ) tied around the trunk."""
    t = bark_tex()
    PINK = pal('#4a2630', '#83495a', '#aa6978', '#c78a96', '#dcaab2')
    _tape_band(t, 6, PINK)
    # knot with two short loose ends fluttering down
    for (kx, ky, lv) in ((12, 5, 3), (13, 5, 4), (12, 6, 2), (13, 6, 3), (12, 7, 1), (13, 7, 2),
                         (11, 8, 2), (11, 9, 1), (10, 10, 2), (14, 8, 3), (14, 9, 2), (15, 10, 2), (15, 11, 1)):
        t.set(kx, ky, PINK[lv])
    for (sx, sy) in ((12, 8), (10, 11), (14, 10), (15, 12)):
        t.dl(sx, sy, -0.06)
    # grime on the old tape
    for (dx, dy) in ((1, 7), (10, 8), (5, 6)):
        c = t.get(dx, dy)
        t.set(dx, dy, (c[:3] * 0.75 + np.array([62, 50, 42]) * 0.25).tolist() + [255])
    return t


@texture('block/cedar_log_shimenawa', opaque=True, tile=True)
def cedar_log_shimenawa():
    """Sacred cedar: bark with a thick twisted straw rope band and one white zigzag shide."""
    t = bark_tex(seed=17, grey=(0, 2, 4))
    STRAW = ramp(['#2a2213', '#43381f', '#5e4f2d', '#7a673c', '#94804c', '#a8945d'], 6)
    SHIDE = pal('#6f695b', '#958f80', '#b8b2a2', '#d0cbbb')
    for xx in range(16):
        for yy in range(2, 8):
            ph = (xx - yy * 2) % 6
            lv = [5, 4, 4, 3, 2, 1][ph]
            if yy == 2:
                lv = max(1, lv - 1)
            if yy == 7:
                lv = max(0, lv - 2)
            t.set(xx, yy, STRAW[lv])
        t.dl(xx, 8, -0.09)
        t.dl(xx, 1, -0.06)
    for (fx, fy, ln) in ((3, 8, 2), (11, 8, 3), (15, 8, 1)):
        for k in range(ln):
            t.set(fx, fy + k, STRAW[2 + (k % 2)])
    zig = ["##.", "##.", ".##", ".##", "##.", "##.", ".##", ".#."]
    ox, oy = 7, 8
    for j, row in enumerate(zig):
        first = row.index('#')
        for i, ch in enumerate(row):
            if ch == '#':
                lv = 3 if i == first else 2
                if j in (2, 4, 6):          # fold edges catch less light
                    lv -= 1
                t.set(ox + i, oy + j, SHIDE[max(lv, 0)])
        t.dl(ox + len(row.rstrip('.')), oy + j, -0.12)
    t.set(8, 12, '#857d6b')
    return t


# ------------------------------------------------------------------------------------------------ needles
@texture('block/cedar_leaves', cutout=True, tile=True)
def cedar_leaves():
    """Dense dark blue-green sugi foliage: clumps of short awl needles (diagonal dashes) with sky holes."""
    W = H = 16
    r = rng(31)
    clump = fnoise(W, H, 36, beta=1.5) * 0.8 + fnoise(W, H, 37, beta=0.6) * 0.4
    clump = equalize(clump)
    lit = emboss(fnoise(W, H, 36, beta=1.5), light=(-1.0, -1.2))
    v = 1.2 + clump * 2.6 + lit * 1.4
    needle = np.zeros((H, W), bool)
    dirs = [(1, 1), (1, -1), (1, 1), (0, 1), (1, -1)]
    for i in range(46):
        x0, y0 = int(r.integers(0, W)), int(r.integers(0, H))
        dx, dy = dirs[i % len(dirs)]
        if r.random() < 0.5:
            dx = -dx
        c = clump[y0, x0]
        val = 3.2 + c * 3.4 + (0.6 if dx < 0 and dy > 0 else 0)
        for k in range(2):
            px_, py_ = (x0 + dx * k) % W, (y0 + dy * k) % H
            v[py_, px_] = max(v[py_, px_], val - k * 0.6)
            needle[py_, px_] = True
        sx, sy = (x0 + 1) % W, (y0 + 2) % H
        if not needle[sy, sx]:
            v[sy, sx] = min(v[sy, sx], 1.0)
    q = np.clip(np.round(v), 0, 7).astype(int)
    t = Tex(16)
    paint_idx(t, q, NEEDLE)
    hole = (clump < 0.17) & ~needle
    hole |= (clump < 0.30) & ~needle & (white(W, H, 38) < 0.35)
    t.a[hole, 3] = 0
    return t


# ------------------------------------------------------------------------------------------------ forest floor
SPRAYS = [  # small dead needle-spray / twig shapes (x, y offsets), drawn light with a dark shadow
    [(0, 0), (1, 0), (2, 1)],
    [(0, 0), (1, 1), (2, 1), (3, 2)],
    [(0, 0), (0, 1), (1, 2)],
    [(1, 0), (0, 1), (1, 1), (2, 1)],
    [(0, 1), (1, 0), (2, 0)],
    [(0, 0), (1, 1)],
    [(0, 2), (1, 1), (2, 1), (3, 0)],
    [(0, 0), (1, 0)],
]


def litter_field(seed=41, n=26, hi=(5, 6), base=3.1):
    W = H = 16
    v = base + fnoise(W, H, seed, beta=1.0) * 0.55 + fnoise(W, H, seed + 1, beta=0.25) * 0.5
    r = rng(seed + 2)
    lit = np.zeros((H, W), bool)
    for i in range(n):
        shp = SPRAYS[int(r.integers(0, len(SPRAYS)))]
        if r.random() < 0.5:
            shp = [(-a, b) for (a, b) in shp]
        if r.random() < 0.5:
            shp = [(b, a) for (a, b) in shp]
        x0, y0 = int(r.integers(0, W)), int(r.integers(0, H))
        val = hi[0] if r.random() < 0.7 else hi[1]
        pts = [(x0 + a, y0 + b) for (a, b) in shp]
        for (px_, py_) in pts:
            v[py_ % H, px_ % W] = val + r.normal(0, 0.25)
            lit[py_ % H, px_ % W] = True
        for (px_, py_) in pts:
            if not lit[(py_ + 1) % H, (px_ + 1) % W]:
                v[(py_ + 1) % H, (px_ + 1) % W] = min(v[(py_ + 1) % H, (px_ + 1) % W], 1.4)
    holes = (white(W, H, seed + 3) > 0.94) & ~lit
    v = np.where(holes, 0.7, v)
    return v, lit


@texture('block/forest_floor_top', opaque=True, tile=True)
def forest_floor_top():
    W = H = 16
    v, lit = litter_field()
    q = np.clip(np.round(v), 0, 7).astype(int)
    q = despeckle(q, keep=lit)
    t = Tex(16)
    paint_idx(t, q, LITTER)
    # older greyer litter in places, fresher rust in others (very low contrast, breaks the grid)
    g = vnoise(W, H, 2, 2, 44)
    grey = np.array([0.96, 0.97, 1.0])
    rust = np.array([1.04, 0.98, 0.93])
    k = g[..., None]
    t.a[..., :3] *= grey * k + rust * (1 - k)
    # a twig lying across (greyish) and one dark sodden leaf
    for (px_, py_) in line_pts(9, 12, 13, 10):
        t.set(px_, py_, '#5b5045')
        t.set(px_ + 1, py_ + 1, LITTER[1])
    stamp(t, [".ab", "abb", "bc."], 2, 3, {'a': LEAF_BROWN[2], 'b': LEAF_BROWN[1], 'c': LEAF_BROWN[0]})
    t.set(12, 4, MOSS[3])
    t.set(13, 4, MOSS[2])
    t.set(5, 13, MOSS[2])
    return t


@texture('block/forest_dirt', opaque=True, tile=True)
def forest_dirt():
    """Dark humus: crumbly soil, a few pebbles and pale rootlets, nothing that lines up when tiled."""
    W = H = 16
    t = Tex(16)
    n = fnoise(W, H, 51, beta=1.1) * 0.7 + fnoise(W, H, 52, beta=0.15) * 0.6
    q = np.clip(np.round(2.4 + n), 0, 5).astype(int)
    q = despeckle(q)
    paint_idx(t, q, SOIL)
    # crumbs: lit top-left pixel + dark bottom-right
    r = rng(53)
    for i in range(9):
        cx, cy = int(r.integers(0, W)), int(r.integers(0, H))
        t.set(cx, cy, SOIL[4] if i % 2 else SOIL[5])
        t.set(cx + 1, cy + 1, SOIL[0])
    for (px_, py_, c) in ((3, 4, '#38352f'), (12, 11, '#403b35'), (7, 14, '#312e2a')):
        t.set(px_, py_, c)
        t.set(px_ + 1, py_, lighten_arr(C(c)[None], -0.06)[0])
        t.set(px_ + 1, py_ + 1, SOIL[0])
    # rootlets: short, thin, low contrast, different orientations
    for pts in (((1, 9), (3, 9), (5, 10)), ((10, 2), (11, 4), (11, 6)), ((12, 14), (14, 13))):
        path = curve_pts(pts)
        for (px_, py_) in path:
            t.set(px_, py_, ROOT[1])
        t.set(*path[0], ROOT[2])
    return t


@texture('block/forest_floor_side', opaque=True, tile=True)
def forest_floor_side():
    t = forest_dirt()
    top = forest_floor_top()
    r = rng(61)
    depth = [3, 4, 3, 2, 3, 4, 5, 4, 3, 3, 2, 3, 4, 4, 2, 3]
    for xx in range(16):
        dd = depth[xx]
        for yy in range(dd):
            t.a[yy, xx] = top.a[(yy + 5) % 16, xx]
        if r.random() < 0.3:
            t.set(xx, dd, LITTER[4])
            t.set(xx, dd + 1, SOIL[0])
        else:
            t.set(xx, dd, SOIL[0])
    return t


def moss_tex(seed=71, n=26, dark=0.0):
    W = H = 16
    pts = jitter_points(n, W, H, seed, min_d=2.4)
    cid, d1, d2 = voronoi(W, H, pts)
    x, y = grid(W, H)
    cx = np.array([p[0] for p in pts])[cid]
    cy = np.array([p[1] for p in pts])[cid]
    dx = (x - cx + W / 2) % W - W / 2
    dy = (y - cy + H / 2) % H - H / 2
    r = rng(seed + 1)
    dome = 1 - np.hypot(dx, dy) / 2.4
    lit = -(dx + dy) / 3.2
    tone = r.normal(0, 0.5, len(pts))[cid]
    v = 3.2 + dome * 1.3 + lit * 1.2 + tone + fnoise(W, H, seed + 2, beta=0.3) * 0.45 - dark
    v = np.where((d2 - d1) < 0.7, v - 1.9, v)
    q = np.clip(np.round(v), 0, 7).astype(int)
    q = despeckle(q)
    t = Tex(16)
    paint_idx(t, q, MOSS)
    return t, d1, d2


@texture('block/moss_floor', opaque=True, tile=True)
def moss_floor():
    t, d1, d2 = moss_tex()
    for (sx, sy) in ((4, 2), (11, 7), (6, 12), (14, 14)):
        t.set(sx, sy, '#6a7432')
    for (a, b, c_, d) in ((9, 3, 11, 2), (2, 14, 4, 15)):
        for (px_, py_) in line_pts(a, b, c_, d):
            t.set(px_, py_, LITTER[4])
    return t


@texture('block/trail_top', opaque=True, tile=True)
def trail_top():
    """Packed path: trodden brown dirt, a short exposed root, pebbles, a few needles kicked onto it."""
    W = H = 16
    t = Tex(16)
    n = fnoise(W, H, 81, beta=1.5) * 0.5 + fnoise(W, H, 82, beta=0.2) * 0.4
    q = np.clip(np.round(3.2 + n), 0, 6).astype(int)
    q = despeckle(q, passes=2)
    paint_idx(t, q, TRAIL)
    # a root barely breaking the surface, and trodden ruts (slightly darker smooth streaks)
    for (px_, py_) in curve_pts([(5, 11), (7, 10), (9, 10)]):
        t.set(px_, py_, ROOT[2])
        t.set(px_, py_ + 1, TRAIL[1])
    t.set(7, 9, ROOT[3])
    for (a, b, c_, d) in ((1, 4, 4, 5), (11, 12, 14, 13)):
        for (px_, py_) in line_pts(a, b, c_, d):
            t.dl(px_, py_, -0.03)
    for (px_, py_, c) in ((3, 3, '#58534d'), (12, 3, '#635e57'), (13, 13, '#4f4b46'), (6, 14, '#47443f')):
        t.set(px_, py_, c)
        t.set(px_ + 1, py_ + 1, TRAIL[0])
        t.set(px_ + 1, py_, lighten_arr(C(c)[None], -0.06)[0])
    for (a, b, c_, d) in ((8, 2, 10, 3), (1, 7, 1, 8), (14, 7, 15, 8), (9, 14, 10, 14)):
        for (px_, py_) in line_pts(a, b, c_, d):
            t.set(px_, py_, LITTER[5])
    return t


# ------------------------------------------------------------------------------------------------ rock
def rock_base(seed=91):
    W = H = 16
    hgt = fnoise(W, H, seed, beta=1.8) * 1.0 + fnoise(W, H, seed + 1, beta=0.9) * 0.3
    lit = emboss(hgt)
    v = 3.4 + hgt * 0.7 + lit * 1.9 + fnoise(W, H, seed + 2, beta=0.1) * 0.3
    sp = white(W, H, seed + 3)
    v = np.where(sp > 0.955, v + 1.4, v)
    v = np.where(sp < 0.035, v - 1.1, v)
    q = np.clip(np.round(v), 0, 7).astype(int)
    q = despeckle(q, keep=(sp > 0.955) | (sp < 0.035))
    t = Tex(16)
    paint_idx(t, q, ROCK)
    # short cracks (dark line, lit lower lip)
    for pts in (((2, 3), (4, 4), (6, 4)), ((11, 9), (12, 11), (13, 12)), ((4, 12), (5, 14))):
        path = curve_pts(pts)
        for (px_, py_) in path:
            t.set(px_, py_, ROCK[1])
            t.dl(px_, py_ + 1, 0.035)
    return t


@texture('block/mountain_rock', opaque=True, tile=True)
def mountain_rock():
    t = rock_base()
    # crusty lichen: small irregular pale spots
    r = rng(95)
    for (cx, cy, n) in ((4, 9, 6), (13, 2, 4), (10, 14, 3)):
        for k in range(n):
            px_, py_ = cx + int(r.integers(-2, 3)), cy + int(r.integers(-1, 2))
            t.set(px_, py_, LICHEN[int(r.integers(0, 4))])
    for (gx, gy) in ((7, 2), (1, 6), (14, 8)):
        t.dl(gx, gy, 0.07)
    return t


@texture('block/mossy_rock', opaque=True, tile=True)
def mossy_rock():
    t = rock_base(seed=93)
    moss, d1, d2 = moss_tex(seed=105, n=24, dark=0.4)
    W = H = 16
    cover = vnoise(W, H, 4, 4, 101) * 0.55 + vnoise(W, H, 8, 8, 102) * 0.3 + white(W, H, 103) * 0.15
    m = cover > 0.60
    m = despeckle(m.astype(int), passes=2).astype(bool)
    t.a[m] = moss.a[m]
    # shadow where the moss cushion overhangs the rock (below / right of the moss edge)
    sh = ~m & (np.roll(m, 1, 0) | np.roll(m, 1, 1))
    t.a[sh, :3] *= 0.72
    for (fx, fy) in ((5, 3), (12, 12), (2, 9)):
        if not m[fy, fx]:
            t.set(fx, fy, MOSS[3])
    return t


@texture('block/river_stones', opaque=True, tile=True)
def river_stones():
    """Rounded wet pebbles (grey, blue-grey, brown) packed in dark silt."""
    W = H = 16
    pts = jitter_points(11, W, H, 111, min_d=3.4)
    cid, d1, d2 = voronoi(W, H, pts, aniso=(1.0, 1.12))
    x, y = grid(W, H)
    cx = np.array([p[0] for p in pts])[cid]
    cy = np.array([p[1] for p in pts])[cid]
    dx = (x - cx + W / 2) % W - W / 2
    dy = (y - cy + H / 2) % H - H / 2
    ramps = [ramp(['#1c1e21', '#2e3236', '#42474c', '#575d62', '#6c7277'], 5),
             ramp(['#1b1f24', '#2b323a', '#3d4650', '#515b66', '#65707b'], 5),
             ramp(['#1f1b17', '#342d27', '#4a4038', '#5f544a', '#73675c'], 5),
             ramp(['#211e1b', '#36302c', '#4c4540', '#625a54', '#776e67'], 5),
             ramp(['#261b16', '#3c2b23', '#533d32', '#694f42', '#7d6152'], 5)]
    r = rng(112)
    which = r.integers(0, len(ramps), len(pts))
    t = Tex(16)
    gap = (d2 - d1) < 1.0
    dome = 1 - np.hypot(dx, dy) / 3.2
    lit = -(dx * 0.8 + dy) / 3.0
    v = 1.6 + dome * 1.3 + lit * 1.2 + fnoise(W, H, 113, beta=0.2) * 0.3
    q = np.clip(np.round(v), 0, 4).astype(int)
    for k in range(len(pts)):
        m = (cid == k) & ~gap
        t.a[m] = ramps[which[k]][q][m]
    SILT = pal('#0d0c0b', '#151311', '#1e1b18', '#2a2622')
    sm = white(W, H, 114)
    t.a[gap] = SILT[np.where(sm > 0.8, 3, np.where(sm > 0.45, 2, np.where(sm > 0.15, 1, 0)))][gap]
    for k, (px_, py_) in enumerate(pts):
        if k % 3 == 0:
            gx, gy = int(round(px_ - 1)) % W, int(round(py_ - 1)) % H
            if not gap[gy, gx]:
                t.set(gx, gy, '#949ca2')
    return t


def leaf_mask(cx, cy, ang, ln, wd, w=16, h=16, lobes=0):
    x, y = grid(w, h)
    dx, dy = x - cx, y - cy
    u = dx * math.cos(ang) + dy * math.sin(ang)
    v = -dx * math.sin(ang) + dy * math.cos(ang)
    s = np.clip((u / ln + 1) / 2, 0, 1)                    # 0 at stem end, 1 at tip
    prof = np.sin(np.pi * s) ** 0.8 * wd
    if lobes:
        prof = prof * (0.75 + 0.25 * np.abs(np.cos(np.pi * s * lobes)))
    inside = (np.abs(u) <= ln) & (np.abs(v) <= prof + 0.25)
    return inside, u, v


@texture('block/fallen_leaves', cutout=True)
def fallen_leaves():
    """Transparent decal of dull autumn leaves (rust, ochre, brown) lying at all angles, some curled."""
    t = Tex(16)
    leaves = [  # cx, cy, angle, half length, half width, palette, lobes
        (4.0, 3.8, 0.5, 2.9, 2.3, LEAF_RUST, 2), (11.6, 3.0, 2.2, 2.6, 1.6, LEAF_OCHRE, 0),
        (12.0, 10.8, 1.2, 2.9, 2.4, LEAF_OCHRE, 2), (4.0, 11.6, -0.6, 2.5, 1.5, LEAF_BROWN, 0),
        (8.4, 7.2, 2.9, 1.9, 1.2, LEAF_RUST, 0), (14.2, 14.6, 0.2, 1.6, 1.1, LEAF_BROWN, 0),
        (1.4, 7.6, 1.7, 1.6, 1.0, LEAF_OCHRE, 0), (8.2, 14.2, 0.9, 1.8, 1.2, LEAF_RUST, 0),
    ]
    for (cx, cy, ang, ln, wd, P, lobes) in leaves:
        m, u, v = leaf_mask(cx, cy, ang, ln, wd, lobes=lobes)
        # two-tone halves (light falls on one side of the midrib), darker towards the tip, midrib light
        lv = np.where(v < 0, 3, 2) - (u > ln * 0.55) * 1
        lv = np.where(np.abs(v) < 0.5, 4, lv)
        edge_m = m & ~erode(m, 1)
        lv = np.where(edge_m & (v > 0), 1, lv)
        q = np.clip(lv, 0, 4).astype(int)
        t.a[m] = P[q][m]
        # stem
        sx, sy = cx - math.cos(ang) * (ln + 0.8), cy - math.sin(ang) * (ln + 0.8)
        t.set(int(round(sx)), int(round(sy)), P[1], wrap=False)
    # a few holes eaten into the leaves
    for (hx, hy) in ((4, 3), (12, 11)):
        t.clear(hx, hy)
    return t
