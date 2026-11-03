"""Surface textures of コーポ白樺 (plain block models made by the world's gen_assets.py), DESIGN §7 "Surfaces", and the
vanilla-style door textures (<id>_bottom / <id>_top, 16x16 like vanilla doors) with their item sprites.

Every surface tiles seamlessly (all noise is periodic, all drawing wraps). Light comes from the top-left.
The apartment is a 1970s wooden 2DK: yellowed tatami, dark varnished flooring, beige walls with stains, cedar ceiling
boards, pale blue bath tiles with black grout; outside grey corrugated siding, concrete, wet asphalt, block walls.
"""
import math

import numpy as np

from common import texture, C, ramp, pal, fnoise, vnoise, rng, grid, lighten_arr, bayer, smoothstep, wrapdist
from hk_lib import (blank, from_idx, noise, dl, dl_field, tint, over, rect, px, hline, vline, line, frame, bevel,
                    wear, specks, grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, bitmap)

T = 16


def tex(name, size=(16, 16), cutout=False, opaque=False):
    return texture('block/' + name, size=size, cutout=cutout, opaque=opaque, tile=True)


def door_tex(name, cutout=False):
    return texture('block/' + name, size=(16, 16), cutout=cutout, tile=False)


def wrapn(seed, beta=1.0, sx=1.0, sy=1.0, w=16, h=16):
    return fnoise(w, h, seed, beta=beta, sx=sx, sy=sy)


def wpx(a, x, y, c, alpha=None):
    """Pixel with wrap-around (tileable drawing)."""
    h, w = a.shape[:2]
    return px(a, x % w, y % h, c, alpha)


def wdl(a, x, y, d):
    h, w = a.shape[:2]
    dl(a[y % h:y % h + 1, x % w:x % w + 1], d)


def xy():
    x, y = grid(16, 16)
    return x.astype(int), y.astype(int)


# ================================================================================================ palettes
# aged igusa: once green, now a dull straw gold
IGUSA = ramp(['#3e3a20', '#524c2a', '#666034', '#79713e', '#8a8149', '#9a9054', '#a99e60', '#b6ab6c'], 8)
IGUSA_SUN = ramp(['#4e4630', '#62593b', '#776d46', '#8a7f52', '#9b905e', '#aa9f6a', '#b8ad77', '#c4ba84'], 8)
HERI = pal('#120f0b', '#1c1812', '#28221a', '#352d22', '#4a4030')
VARNISH = ramp(['#140b06', '#1f1209', '#2a190d', '#362111', '#432a16', '#51331c', '#5f3d22', '#6e4729'], 8)
CUSHION = ramp(['#8a7a5e', '#9e8e70', '#b0a082', '#c0b192', '#cdbf9f', '#d8cbac'], 6)
PLASTER = ramp(['#857a63', '#958a72', '#a3987f', '#b0a58b', '#bbb096', '#c5bba1', '#cec4ab'], 7)
CEDAR = ramp(['#4e3420', '#64432a', '#7a5334', '#8d6340', '#9e734c', '#ad8258', '#bb9166'], 7)
PLYWOOD = ramp(['#7a6040', '#8e7250', '#a1845e', '#b2956c', '#c1a47a', '#ceb288'], 6)
BATH_BLUE = ramp(['#4f6f7c', '#628392', '#7698a6', '#8aabb8', '#9dbcc8', '#afcbd5', '#c0d8e0'], 7)
SIDING = ramp(['#3a3d3f', '#4a4e50', '#5b5f61', '#6c7072', '#7e8183', '#8f9293', '#a0a2a2'], 7)
DOOR_STEEL = ramp(['#3c4238', '#4d5448', '#5f6758', '#717a69', '#828b78', '#939b88', '#a3aa97'], 7)
CONCRETE = ramp(['#4a4a46', '#585854', '#666661', '#74746e', '#81817b', '#8e8e87', '#9b9b93'], 7)
CORRIDOR = ramp(['#3f4440', '#4b514c', '#585e58', '#656b64', '#727870', '#7f857c'], 6)
ASPHALT = ramp(['#101112', '#16171a', '#1c1e21', '#232528', '#2a2c30', '#323438', '#3b3e42'], 7)
BLOCK = ramp(['#55554f', '#64645d', '#73736b', '#82827a', '#909088', '#9d9d94', '#aaaaa0'], 7)
SLATE = ramp(['#1b1d20', '#24272b', '#2e3136', '#383c41', '#43474d', '#4f5359'], 6)
GENKAN = ramp(['#1f1a17', '#29231f', '#342d28', '#3f3731', '#4a413a', '#564c44'], 6)
WAINSCOT = ramp(['#1e120a', '#2a190e', '#372113', '#452a19', '#53331f', '#623d26', '#71482e'], 7)
FLORAL_BG = ramp(['#9d9178', '#b0a48a', '#c0b59b', '#cdc3a9', '#d8cfb6', '#e1d9c2'], 6)


# ================================================================================================ tatami
def _tatami(seed, worn=False):
    """A half mat: the heri (cloth border) on the left and right edges, the rushes running across (horizontal rows),
    faint warp threads along the mat. Seamless along the mat (no end seam), so mats read as strips in the 6-mat plan."""
    r = rng(seed)
    x, y = xy()
    P = IGUSA_SUN if worn else IGUSA
    streak = wrapn(seed + 1, beta=1.1, sx=1.0, sy=7.0)          # rush irregularities run across (along x)
    blot = wrapn(seed + 2, beta=2.0)                            # sun / wear blotches
    row = r.normal(0, 0.28, 16)
    base = 4.25 if not worn else 4.55
    idx = base + row[y] + streak * 0.32 + blot * 0.22
    # rows: rushes in pairs - a lit top row and a shaded lower row (bumpy weave)
    idx += np.where(y % 2 == 0, 0.55, -0.45)
    # warp threads (hemp) every 5 px along the mat: dips in the shaded rows
    warp = np.isin(x, (4, 8, 11))
    idx -= (warp & (y % 2 == 1)) * 0.65
    idx += (warp & (y % 2 == 0)) * 0.15
    a = from_idx(idx, P)
    # the omote folds under the heri: shaded column next to each border
    dl(a, -0.045, (x == 1))
    dl(a, -0.03, (x == 14))
    # heri: dark cloth with a woven thread pattern along it; a stitched seam line (lighter px) every 4 rows
    for xx in (0, 15):
        for yy in range(16):
            k = 1 + ((yy + (xx // 15)) % 2)
            if yy % 4 == 2:
                k = 3
            a[yy, xx] = HERI[k]
    if worn:
        # frayed heri: threads pulled pale, a worn-through spot showing the core
        for (xx, yy, c) in ((0, 5, '#5e5238'), (0, 6, '#40382a'), (15, 11, '#5e5238'), (15, 12, '#6e6044'),
                            (15, 3, '#40382a')):
            a[yy, xx] = C(c)
        # broken rushes poking up (pale fuzz) and a fuzzy worn path where feet go
        for _ in range(7):
            xx, yy = int(r.integers(2, 14)), int(r.integers(0, 16))
            a[yy, xx] = P[7] if r.random() < 0.6 else P[6]
        worn_path = (wrapn(seed + 5, beta=1.6) > 0.4) & (x > 1) & (x < 14)
        tint(a, '#c2b68e', 0.16, worn_path)
        # a faint spill mark (soft, no hard edge so the tiling does not show) and a tiny old cigarette burn
        sp = wrapn(seed + 6, beta=2.2)
        tint(a, '#4a3a20', np.clip((sp - 0.6) * 0.3, 0, 0.18) * ((x > 1) & (x < 14)))
    return a


@tex('tatami')
def tatami():
    return _tatami(2101)


@tex('tatami_worn')
def tatami_worn():
    return _tatami(2101, worn=True)


# ================================================================================================ floors
def _flooring(seed):
    """Dark varnished hardwood strips (4 per block along x), staggered butt joints, the varnish worn pale along the
    walking line, scratches, dust in the gaps."""
    r = rng(seed)
    x, y = xy()
    grain = wrapn(seed + 1, beta=0.8, sx=14, sy=1)
    grain2 = wrapn(seed + 2, beta=1.4, sx=4, sy=1)
    tones = [0.25, -0.35, 0.4, -0.1]
    joints = [5, 13, 9, 2]
    idx = np.zeros((16, 16))
    for k in range(4):
        rows = slice(k * 4, k * 4 + 4)
        right = (x[rows] >= joints[k]).astype(float)
        idx[rows] = 4.0 + tones[k] + (right - 0.5) * (0.45 if k % 2 else -0.4) + grain[rows] * 0.55 + grain2[rows] * 0.3
        # flame grain: darker lines drifting along the strip
        g = np.abs(np.sin(x[rows] * 0.42 + joints[k] * 1.7 + (y[rows] - k * 4) * 1.3)) < 0.16
        idx[rows] -= g * 0.7
    for k in range(4):
        idx[k * 4 + 3, :] = 0.6 + grain[k * 4 + 3] * 0.25      # the gap between strips (dust-dark)
        idx[k * 4, :] += 0.55                                    # lit upper edge of each strip
        jx = joints[k]
        idx[k * 4:k * 4 + 3, jx] = 0.9
        idx[k * 4:k * 4 + 3, (jx + 1) % 16] += 0.4
    a = from_idx(idx, VARNISH)
    # varnish gloss: a few bright pixels in the strip centres
    gl = (r.random((16, 16)) < 0.07) & ((y % 4) == 1)
    dl(a, 0.045, gl)
    # worn: the finish rubbed off in a soft patch (paler, greyer, matte), scratches across
    wp = wrapn(seed + 9, beta=1.8)
    m = (wp > 0.35) & ((y % 4) != 3)
    a[m, :3] = a[m, :3] * 0.82 + np.array([120, 92, 64]) * 0.18
    for _ in range(5):
        sx, sy = int(r.integers(0, 16)), int(r.integers(0, 16))
        if sy % 4 == 3:
            sy -= 1
        ln = int(r.integers(2, 5))
        for kk in range(ln):
            p = (sx + kk) % 16
            a[sy, p, :3] = a[sy, p, :3] * 0.7 + np.array([150, 116, 84]) * 0.3
    grime(a, seed + 11, strength=0.12, color='#120a05', beta=1.5)
    return a


@tex('old_flooring')
def old_flooring():
    return _flooring(2201)


@tex('vinyl_floor')
def vinyl_floor():
    """Showa cushion floor: a beige vinyl printed with a faux tile pattern (8 px squares, a small diamond motif),
    the print worn off in a path, yellowed, a curling seam, a burn ring from a pot."""
    r = rng(2301)
    x, y = xy()
    n = wrapn(2302, beta=1.6)
    fine = wrapn(2303, beta=0.2)
    idx = 3.4 + n * 0.25 + fine * 0.22
    a = from_idx(idx, CUSHION)
    # printed grid lines (brownish, slightly misregistered) and the motif in each square
    lines = ((x % 8) == 0) | ((y % 8) == 0)
    tint(a, '#7a6448', 0.32, lines)
    tint(a, '#9a845e', 0.14, ((x % 8) == 1) & ~lines)       # misregistration shadow of the print
    for (cx, cy) in ((4, 4), (12, 12), (12, 4), (4, 12)):
        alt = ((cx // 8 + cy // 8) % 2) == 0
        col = '#8a6c4a' if alt else '#6f7a5c'
        for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            tint(a, col, 0.38, (x == cx + dx) & (y == cy + dy))
        tint(a, '#d8cab0', 0.35, (x == cx) & (y == cy))
    # textured emboss: tiny dark pits
    pits = (r.random((16, 16)) < 0.08)
    dl(a, -0.03, pits)
    # the print worn through along the walking path (paler, no lines)
    worn = wrapn(2305, beta=1.9) > 0.6
    a[worn, :3] = a[worn, :3] * 0.7 + np.array([200, 188, 160]) * 0.3
    tint(a, '#b49a5a', 0.07)
    grime(a, 2306, strength=0.12, color='#3a2c1a', beta=1.4)
    return a


@tex('genkan_tile')
def genkan_tile():
    """Small dark stoneware tiles (4 px, 3 + grout) in the step-down entrance: each tile its own shade, gritty from
    shoes, a chipped tile, wet footprints' grit near the door."""
    r = rng(2401)
    x, y = xy()
    n = wrapn(2402, beta=1.0)
    tones = r.normal(0, 0.4, (4, 4))
    idx = 3.3 + tones[y // 4, x // 4] + n * 0.25
    a = from_idx(idx, GENKAN)
    # speckled glaze
    sp = r.random((16, 16)) < 0.12
    dl(a, 0.035, sp)
    grout = ((x % 4) == 3) | ((y % 4) == 3)
    a[grout] = from_idx(np.full((16, 16), 1.2) + wrapn(2403)[...] * 0.5,
                        ramp(['#141210', '#1b1815', '#221e1a', '#2a2520', '#322c26', '#3a332c'], 6))[grout]
    # tile edges: lit top-left, shaded bottom-right
    dl(a, 0.04, ((x % 4) == 0) & ~grout)
    dl(a, 0.025, ((y % 4) == 0) & ~grout)
    dl(a, -0.035, ((x % 4) == 2) & ((y % 4) == 2))
    # a chipped corner, grit and a pale dried water mark
    a[6, 6] = C('#5a524a')
    a[5, 6] = C('#3a332d')
    specks(a, 2404, 0.025, '#6a6052', 0.6)
    dry = disc_mask(16, 16, 11.5, 11.5, 3.2, 2.2) & ~grout
    tint(a, '#8a8274', 0.12, dry)
    grime(a, 2405, strength=0.1, color='#0e0c0a', beta=1.3)
    return a


def _bath_tiles(seed, size, floor=False):
    """Small glazed tiles with dark old grout (mould), pale blue; size = tile pitch in px (incl. 1 px grout)."""
    r = rng(seed)
    x, y = xy()
    n = wrapn(seed + 1, beta=1.4)
    k = 16 // size
    tones = r.normal(0, 0.22 if not floor else 0.4, (k, k))
    if floor:
        # the floor mosaic mixes a few deeper blues and off-whites
        pick = r.random((k, k))
        tones[pick < 0.18] -= 1.2
        tones[pick > 0.86] += 0.9
    idx = 4.1 + tones[y // size, x // size] + n * 0.12
    a = from_idx(idx, BATH_BLUE)
    tx, ty = x % size, y % size
    grout = (tx == size - 1) | (ty == size - 1)
    # glaze: a bright corner pixel and a lit top row, shaded bottom-right inside each tile
    dl(a, 0.06, (tx == 0) & (ty == 0) & ~grout)
    dl(a, 0.03, (ty == 0) & ~grout)
    dl(a, -0.04, ((tx == size - 2) | (ty == size - 2)) & ~grout)
    gcol = ramp(['#2a2f2e', '#363c3b', '#444a49', '#535a58', '#646b69'], 5)
    gn = wrapn(seed + 2, beta=1.2)
    gi = np.clip((3.4 if not floor else 2.4) + gn * 0.8, 0, 4)
    a[grout] = from_idx(gi, gcol)[grout]
    # black mould in the grout: scattered short runs where the grout stays wet (fine noise, so a tiled wall shows
    # no diagonal band of it), denser on the floor
    mn = wrapn(seed + 3, beta=0.3) + (r.random((16, 16)) - 0.5) * 0.6
    mould = (mn > (1.25 if not floor else 0.6)) & grout
    a[mould] = C('#1a1e1c')
    dl(a, -0.06, (mn > 0.8) & grout & ~mould)
    # soap scum / limescale haze
    haze = wrapn(seed + 4, beta=2.0) > 0.7
    tint(a, '#dfe6e2', 0.12, haze & ~grout)
    if floor:
        wet = (r.random((16, 16)) < 0.06) & ~grout
        dl(a, 0.1, wet)
        grime(a, seed + 5, strength=0.12, color='#2c3830', beta=1.2)
    # (no cracked tile or rust run here: a wall of these repeats every 16 px, and one-off marks would form a grid)
    return a


@tex('bath_tile_wall')
def bath_tile_wall():
    return _bath_tiles(2501, 4)


@tex('bath_tile_floor')
def bath_tile_floor():
    return _bath_tiles(2511, 4, floor=True)


# ================================================================================================ walls
def _plaster(seed):
    """Old beige sand-textured wall (京壁): a soft, fine granular grain (no big specks: walls are seen large), faint
    mottling, a few darker grains."""
    r = rng(seed)
    x, y = xy()
    n = wrapn(seed, beta=1.9)
    fine = wrapn(seed + 1, beta=0.0)
    idx = 3.9 + n * 0.22 + fine * 0.2
    a = from_idx(idx, PLASTER)
    # sand grains: a lit / shadow pair here and there (relief from the top-left), low contrast
    for _ in range(10):
        gx, gy = int(r.integers(0, 16)), int(r.integers(0, 16))
        wdl(a, gx, gy, 0.02)
        wdl(a, gx + 1, gy + 1, -0.025)
    specks(a, seed + 2, 0.012, '#8a7c62', 0.4)
    tint(a, '#a8905a', 0.05)
    return a


@tex('plaster_wall')
def plaster_wall():
    return _plaster(2601)


@tex('plaster_wall_stained')
def plaster_wall_stained():
    """The same wall soaked by years of leaks: soft brown blooms with darker tide lines (bands of a periodic noise
    field, so a stained wall shows no tile grid), drip runs, a few mildew freckles."""
    a = _plaster(2601)
    r = rng(2611)
    x, y = xy()
    f = wrapn(2612, beta=2.0, sx=1.3, sy=0.8)
    inside = f > 0.15
    tide = (f > 0.15) & (f < 0.32)
    tint(a, '#8f7040', 0.14, inside)
    tint(a, '#73552c', 0.1, f > 0.9)
    tint(a, '#5a3e1e', 0.2, tide)
    # drip runs (vertical, periodic so they continue across blocks)
    runs = wrapn(2613, beta=0.6, sx=1, sy=14)
    tint(a, '#5a3e1e', 0.13, (runs > 1.1))
    for _ in range(5):
        mx, my = int(r.integers(0, 16)), int(r.integers(0, 16))
        a[my, mx, :3] = a[my, mx, :3] * 0.6 + np.array([58, 58, 44]) * 0.4
    return a


@tex('wallpaper_old')
def wallpaper_old():
    """Faded Showa floral wallpaper: a small rose sprig in a half-drop repeat on a yellowed ground, the print faded to
    dusty pink and olive, a roll seam lifting at the left edge."""
    r = rng(2701)
    x, y = xy()
    n = wrapn(2702, beta=1.6)
    fine = wrapn(2703, beta=0.1)
    a = from_idx(3.9 + n * 0.25 + fine * 0.2, FLORAL_BG)
    # fine vertical embossed stripes of the paper
    dl(a, -0.015, (x % 3) == 2)
    PINK = ['#a07a6c', '#b48c7c', '#8a6658']
    LEAF = ['#7f8462', '#6c7052']
    sprig = ['..pp..',
             '.pPpp.',
             '.ppPp.',
             '..pd..',
             'l.s.l.',
             '.lsl..',
             '..s...']
    legend = {'p': PINK[0], 'P': PINK[1], 'd': PINK[2], 'l': LEAF[0], 's': LEAF[1]}
    for (ox, oy) in ((2, 1), (10, 9)):
        for j, row in enumerate(sprig):
            for i, ch in enumerate(row):
                if ch in legend:
                    xx, yy = (ox + i) % 16, (oy + j) % 16
                    a[yy, xx, :3] = a[yy, xx, :3] * 0.45 + C(legend[ch])[:3] * 0.55
    # small scattered buds between sprigs
    for (bx, by) in ((12, 3), (5, 11), (14, 14), (7, 6)):
        a[by, bx, :3] = a[by, bx, :3] * 0.55 + C(PINK[0])[:3] * 0.45
    # roll seam (darker line, a lifting edge lit on its right)
    dl(a, -0.05, x == 0)
    dl(a, 0.03, x == 1)
    tint(a, '#b0944e', 0.07)
    grime(a, 2704, strength=0.1, color='#6a5434', beta=1.6)
    return a


@tex('wood_wainscot')
def wood_wainscot():
    """Dark varnished tongue-and-groove boards (4 px), vertical grain, grooves dark, scuffs low down. Tiles
    vertically, so a wall can carry it one block high."""
    r = rng(2801)
    x, y = xy()
    grain = wrapn(2802, beta=0.8, sx=1, sy=14)
    g2 = wrapn(2803, beta=1.4, sx=1, sy=5)
    tones = r.normal(0, 0.35, 4)
    idx = 3.6 + tones[x // 4] + grain * 0.55 + g2 * 0.25
    lines = np.abs(np.sin(y * 0.38 + (x // 4) * 2.1 + x * 0.9)) < 0.15
    idx -= lines * 0.6
    a = from_idx(idx, WAINSCOT)
    dl(a, -0.1, (x % 4) == 3)
    dl(a, 0.045, (x % 4) == 0)
    wear(a, 2804, amount=0.07, edge_only=False, light=0.05, dark=-0.05)
    return a


# ================================================================================================ ceiling
def _ceiling(seed, stained=False):
    """竿縁天井: wide cedar-veneer boards with flame grain running along x, a dark batten (竿縁) across them at the
    block's west edge, thin board joints."""
    r = rng(seed)
    x, y = xy()
    n1 = wrapn(seed + 1, beta=0.8, sx=12, sy=1)
    n2 = wrapn(seed + 2, beta=1.6, sx=3, sy=1)
    # straight cedar grain (柾目) along x: thin dark lines at irregular spacing, softly wavering
    t = y + 0.8 * np.sin(x * 2 * math.pi / 16 + 0.6) + n2 * 0.5
    lines = np.abs(np.sin(t * 2.2 + 0.4)) < 0.2
    idx = 3.9 + n1 * 0.35 + n2 * 0.25 - lines * 0.75
    # a darker heartwood band in one board, a paler sapwood edge in the other
    idx -= ((y >= 2) & (y <= 4)) * 0.45
    idx += ((y >= 11) & (y <= 12)) * 0.35
    a = from_idx(idx, CEDAR)
    # board joints every 8 px (thin dark line with a lit edge below)
    dl(a, -0.1, (y % 8) == 7)
    dl(a, 0.03, (y % 8) == 0)
    # the batten: 2 px dark wood with a lit left edge
    bat = from_idx(np.full((16, 2), 1.6) + wrapn(seed + 3, sx=1, sy=8)[:, :2] * 0.3, CEDAR)
    a[:, 14:16] = bat
    dl(a[:, 14:15], 0.04)
    dl(a[:, 13:14], -0.05)                                  # its shadow on the boards
    tint(a, '#6a5030', 0.08)
    if stained:
        # rain leak: soft brown blooms with darker tide lines (bands of a periodic field: no tile grid), mould dots
        f = wrapn(seed + 7, beta=2.1)
        inside = f > 0.1
        tint(a, '#3a2410', 0.2, inside)
        tint(a, '#2a1a0a', 0.18, f > 0.85)
        tint(a, '#24160a', 0.38, (f > 0.1) & (f < 0.27))
        for _ in range(4):
            sx, sy = int(r.integers(0, 14)), int(r.integers(0, 16))
            a[sy, sx, :3] *= 0.72
    return a


@tex('ceiling_board')
def ceiling_board():
    return _ceiling(2901)


@tex('ceiling_board_stained')
def ceiling_board_stained():
    return _ceiling(2901, stained=True)


@tex('oshiire_floor')
def oshiire_floor():
    """Bare plywood (rotary-cut veneer: wide swirling grain), a panel joint, mould spots and a pale square where a
    box stood for years."""
    r = rng(3001)
    x, y = xy()
    n = wrapn(3002, beta=1.3, sx=3, sy=1)
    n2 = wrapn(3004, beta=1.8)
    t = y + 1.3 * np.sin(x * 2 * math.pi / 16 + 1.2) + n2 * 1.4
    lines = np.abs(np.sin(t * 0.95)) < 0.13
    idx = 3.1 + n * 0.3 + n2 * 0.3 - lines * 0.6
    a = from_idx(idx, PLYWOOD)
    dl(a, -0.1, y == 15)
    dl(a, 0.03, y == 0)
    # nail heads along the joist line and a couple of mould freckles
    for nx in (3, 11):
        a[15, nx] = C('#3a3430')
    for (mx, my) in ((6, 9), (13, 4)):
        a[my, mx, :3] = a[my, mx, :3] * 0.55 + np.array([60, 62, 46]) * 0.45
    grime(a, 3003, strength=0.14, color='#3a2a18', beta=1.5)
    return a


# ================================================================================================ exterior
@tex('apartment_siding')
def apartment_siding():
    """Grey painted corrugated steel (波板): vertical corrugations with a 4 px pitch (lit crest, mid, shadow, deep
    trough), a row of nail heads, chalky faded paint and rust bleeding down from the nails."""
    r = rng(3101)
    x, y = xy()
    n = wrapn(3102, beta=1.6)
    prof = np.array([1.2, 0.45, -0.45, -1.25])          # crest -> trough across one 4 px corrugation
    idx = 3.2 + prof[x % 4] + n * 0.28
    a = from_idx(idx, SIDING)
    # chalky fading in blotches, darker water-stained streaks running down
    fade = wrapn(3103, beta=1.8) > 0.4
    tint(a, '#b8bcbc', 0.1, fade)
    streak = wrapn(3104, beta=0.7, sx=1, sy=10)
    tint(a, '#2a2e30', 0.16, streak > 0.6)
    # nail heads on the crests at y = 3 (and the rust bleeding down from them)
    for nx in (0, 8):
        a[3, nx] = C('#2c2a28')
        a[2, nx] = C('#9a9a96')
        for k in range(1, 9):
            tint(a, '#7a3e1c', max(0.0, 0.42 - k * 0.045), (x == nx) & (y == 3 + k))
            if k < 5:
                tint(a, '#7a3e1c', 0.12, (x == nx + 1) & (y == 3 + k))
    specks(a, 3105, 0.015, '#6a3818', 0.5)
    return a


@tex('apartment_door_metal')
def apartment_door_metal():
    """The painted steel of the apartment doors (a door leaf seen flat): dusty green-grey paint, faint edge frame on
    the left and right, dents and a scuffed patch, rust pin-spots. Tiles vertically for a two-block leaf."""
    r = rng(3201)
    x, y = xy()
    n = wrapn(3202, beta=1.7)
    a = from_idx(3.6 + n * 0.3, DOOR_STEEL)
    dl(a, -0.08, x == 0)
    dl(a, 0.045, x == 1)
    dl(a, -0.05, x == 15)
    # dents: a lit/dark pair
    for (dx, dy) in ((10, 6), (5, 12)):
        dl(a[dy:dy + 1, dx:dx + 2], -0.05)
        dl(a[dy + 1:dy + 2, dx:dx + 2], 0.035)
    wear(a, 3203, amount=0.06, edge_only=False, light=0.04, dark=-0.05)
    specks(a, 3204, 0.008, '#6a3a1a', 0.6)
    grime(a, 3205, strength=0.1, color='#1e2018', beta=1.4)
    return a


@tex('corridor_concrete')
def corridor_concrete():
    """The outdoor corridor's concrete slab with its old grey-green waterproof coating, worn to bare concrete along
    the walking line, hairline cracks, damp dark patches from the rain blowing in."""
    r = rng(3301)
    x, y = xy()
    n = wrapn(3302, beta=1.3)
    fine = wrapn(3303, beta=0.1)
    a = from_idx(3.0 + n * 0.4 + fine * 0.3, CORRIDOR)
    worn = wrapn(3304, beta=1.8) > 0.45
    a[worn] = from_idx(3.6 + n[worn] * 0.4 + fine[worn] * 0.4, CONCRETE)
    # one faint hairline crack (low contrast, so the tiling does not draw a pattern down the corridor)
    for k in range(4):
        wpx(a, 3 + k, 4 + (k // 2), '#4a504a')
    damp = wrapn(3305, beta=1.9) > 0.55
    tint(a, '#1c201e', 0.25, damp)
    specks(a, 3306, 0.03, '#9a9e96', 0.4)
    return a


@tex('concrete')
def concrete():
    """Cast concrete: fine aggregate, form-board grain faintly horizontal, a form-tie hole, rain streaks."""
    r = rng(3401)
    x, y = xy()
    n = wrapn(3402, beta=1.2)
    board = wrapn(3403, beta=0.7, sx=1, sy=9)
    fine = wrapn(3404, beta=0.0)
    a = from_idx(3.4 + n * 0.3 + board * 0.22 + fine * 0.28, CONCRETE)
    dl(a, -0.035, (y % 8) == 7)
    streak = wrapn(3405, beta=0.6, sx=1, sy=12)
    tint(a, '#30302c', 0.15, streak > 0.7)
    specks(a, 3406, 0.04, '#9a9a92', 0.4)
    specks(a, 3407, 0.03, '#3a3a36', 0.5)
    return a


@tex('asphalt_wet')
def asphalt_wet():
    """Rain-soaked asphalt at night: near-black with pale aggregate, a cold sheen where water films the surface,
    bright pinpoint glints of water in the pits."""
    r = rng(3501)
    x, y = xy()
    n = wrapn(3502, beta=1.0)
    fine = wrapn(3503, beta=0.0)
    a = from_idx(2.4 + n * 0.45 + fine * 0.45, ASPHALT)
    # aggregate: a few pale and dark stones
    st = r.random((16, 16))
    a[st < 0.035] = C('#43464a')
    a[(st > 0.035) & (st < 0.05)] = C('#4f5256')
    a[st > 0.975] = C('#0a0b0c')
    # water film: a smooth cold sheen in the low areas
    film = wrapn(3504, beta=2.2)
    tint(a, '#323c48', np.clip((film - 0.1) * 0.3, 0, 0.26))
    # glints of water in the pits
    g = (r.random((16, 16)) < 0.014) & (film > 0.3)
    a[g] = C('#7a8696')
    return a


@tex('block_wall')
def block_wall():
    """Concrete block garden wall (ブロック塀): 16x8 blocks in running bond, rough faces, mortar joints, black rain
    streaks and moss low down, efflorescence."""
    r = rng(3601)
    x, y = xy()
    n = wrapn(3602, beta=1.0)
    fine = wrapn(3603, beta=0.0)
    row = y // 8
    off = (row % 2) * 8
    bx = (x + off) % 16
    tones = {0: 0.3, 1: -0.2}
    idx = 3.4 + np.vectorize(tones.get)(row) + n * 0.18 + fine * 0.3
    a = from_idx(idx, BLOCK)
    mortar = ((y % 8) == 7) | (bx == 15)
    a[mortar] = from_idx(np.full((16, 16), 2.0) + fine * 0.3, BLOCK)[mortar]
    dl(a, 0.04, ((y % 8) == 0) & ~mortar)
    dl(a, 0.02, (bx == 0) & ~mortar)
    dl(a, -0.04, ((y % 8) == 6) & ~mortar)
    # black rain streaks from the top of each block, moss at the bottom joint
    streak = wrapn(3604, beta=0.6, sx=1, sy=12)
    tint(a, '#24261e', 0.22, (streak > 0.7) & ~mortar)
    moss = (wrapn(3605, beta=1.4) > 0.8) & ((y % 8) >= 5)
    tint(a, '#3a4a24', 0.3, moss)
    eff = (wrapn(3606, beta=1.6) > 0.9) & ~mortar
    tint(a, '#cfcfc4', 0.25, eff)
    return a


@tex('roof_slate')
def roof_slate():
    """Coloured cement slates (カラーベスト): rows 4 px high, slates 8 px wide staggered, each row's lower edge
    casting a dark line, lichen spots, wet sheen."""
    r = rng(3701)
    x, y = xy()
    n = wrapn(3702, beta=1.1)
    row = y // 4
    off = (row % 2) * 4
    sx_ = (x + off) % 8
    tones = r.normal(0, 0.3, (4, 2))
    idx = 3.0 + tones[row, ((x + off) % 16) // 8] + n * 0.3 + (y % 4) * 0.18
    a = from_idx(idx, SLATE)
    dl(a, -0.09, (y % 4) == 3)
    dl(a, -0.05, sx_ == 7)
    dl(a, 0.03, ((y % 4) == 0) & (sx_ != 7))
    lich = (wrapn(3703, beta=1.2) > 0.85)
    tint(a, '#5a6040', 0.4, lich)
    sheen = wrapn(3704, beta=2.0) > 0.5
    dl(a, 0.025, sheen & ((y % 4) == 1))
    return a


# ================================================================================================ doors
# vanilla door textures: the hinge is at the texture's left edge (columns 0..2 also form the door's thin edges), the
# handle belongs on the right. bottom = lower half (y 0 = 1 m above the floor), top = upper half.
def _steel_leaf(seed, top):
    x, y = xy()
    n = wrapn(seed, beta=1.7)
    a = from_idx(3.7 + n * 0.28, DOOR_STEEL)
    vline(a, 0, 0, 15, DOOR_STEEL[1])
    vline(a, 1, 0, 15, DOOR_STEEL[5])
    vline(a, 15, 0, 15, DOOR_STEEL[2])
    if top:
        hline(a, 0, 15, 0, DOOR_STEEL[1])
        hline(a, 1, 14, 1, DOOR_STEEL[5])
    wear(a, seed + 3, amount=0.05, edge_only=False, light=0.035, dark=-0.045)
    return a


@door_tex('front_door_top')
def front_door_top():
    """Upper half: a weathered vertical wooden plaque (表札) with the room number in kanji, 「二〇三」 stacked - each
    of the three characters is left-right symmetric, so it reads the same from both faces of a vanilla door (which
    mirrors its texture on one side); the peephole; a sticker; rust at the top corner."""
    a = _steel_leaf(4101, True)
    rect(a, 5, 1, 9, 15, '#2a1c12')
    plaque = from_idx(np.full((13, 3), 3.6) + wrapn(4102, beta=1.4, w=16, h=16)[:13, :3] * 0.4,
                      ramp(['#6a5236', '#806446', '#967856', '#a88a64', '#b89a72', '#c6a880'], 6))
    a[2:15, 6:9] = plaque
    INK = '#1a120c'
    # 二 (rows 2..4), 〇 (rows 6..8), 三 (rows 10..14), 3 px wide, centred on x 7
    for (x, y) in ((7, 2), (6, 4), (7, 4), (8, 4),
                   (6, 6), (7, 6), (8, 6), (6, 7), (8, 7), (6, 8), (7, 8), (8, 8),
                   (6, 10), (7, 10), (8, 10), (7, 12), (6, 14), (7, 14), (8, 14)):
        px(a, x, y, INK)
    px(a, 7, 7, '#a88a64')
    # the peephole: chrome ring, dark lens with a glint
    px(a, 12, 9, '#1a1c1e')
    px(a, 11, 9, '#a8aeb0')
    px(a, 13, 9, '#7a8084')
    px(a, 12, 8, '#c8cdd0')
    px(a, 12, 10, '#6a7074')
    # a faded sticker (an old inspection seal) and rust at the top corner
    rect(a, 11, 12, 13, 13, '#a89a7a')
    px(a, 12, 12, '#8a3a2a')
    px(a, 14, 1, '#6a3a1a')
    px(a, 13, 1, '#7a4a28')
    return a


@door_tex('front_door_bottom')
def front_door_bottom():
    a = _steel_leaf(4111, False)
    # lever handle + escutcheon and the key cylinder below it (latch side = right)
    rect(a, 12, 1, 13, 6, '#2a2c2c')
    rect(a, 9, 2, 13, 3, '#b8bec0')
    hline(a, 9, 12, 2, '#e0e4e4')
    px(a, 9, 3, '#7a8084')
    px(a, 12, 5, '#c8ccce')
    px(a, 12, 4, '#4a4e50')
    # the newspaper slot: chrome frame, dark slot, a flyer stuck half in
    rect(a, 4, 6, 10, 8, '#9ca2a4')
    hline(a, 4, 10, 6, '#d0d4d4')
    hline(a, 5, 9, 7, '#121314')
    rect(a, 6, 7, 8, 7, '#e8e2cc')
    px(a, 7, 8, '#d0c8b0')
    # kick plate at the bottom: stainless band, scuffed, rust creeping up from the sill
    rect(a, 1, 13, 15, 15, '#7e8486')
    hline(a, 1, 15, 13, '#a6acae')
    wear(a[13:16, 1:16], 4112, amount=0.3, edge_only=False, light=0.05, dark=-0.08)
    for xx in (3, 4, 9, 13):
        px(a, xx, 15, '#5a2e14')
    px(a, 4, 14, '#7a4020')
    return a


# The genkan side of 203's door (the world's two_face_door: #inner on the panel's other face). Same layout rule as the
# outer face - hinge left, handle right, and everything that goes through the leaf (peephole, lever spindle, the
# newspaper slot) on the same texture columns as outside. With 203's hinge on the left the inner face is seen
# mirrored, so nothing here may read as text.
@door_tex('front_door_inner_top')
def front_door_inner_top():
    """Upper half, inside: the peephole's eyepiece, the door chain hooked (sagging from its slide rail to the jamb
    on the latch side - it is 3 a.m. and you are in), a garbage-day sheet taped up long ago (yellowed paper, the
    colour-coded rows of 燃える/燃えない/資源/びん, the tape gone brown) and a coat hook."""
    a = _steel_leaf(4121, True)
    x, y = xy()
    # cleaner than outside, but greasy around the handle side where hands go
    dl(a, -0.03, (x >= 10) & (y >= 9) & (wrapn(4122, beta=1.2) > 0.2))
    # the eyepiece: a wider chrome ring around a dark lens, a glint, the little swing cover's hinge above
    for (cx, cy, c) in ((11, 9, '#9aa0a2'), (13, 9, '#6e7476'), (12, 8, '#c4c9cc'), (12, 10, '#5e6466'),
                        (11, 8, '#b4babc'), (13, 8, '#8a9092'), (11, 10, '#7a8082'), (13, 10, '#4e5456')):
        px(a, cx, cy, c)
    px(a, 12, 9, '#141618')
    px(a, 12, 7, '#7a8084')
    # the chain's slide rail (a chrome plate with a dark slot) and the chain hooked across to the jamb (x 15)
    rect(a, 8, 11, 11, 12, '#a4aaac')
    hline(a, 8, 11, 11, '#d4d8d8')
    hline(a, 9, 10, 12, '#2a2c2e')
    px(a, 10, 12, '#e6eaea')                     # the knob in the slot
    for (cx, cy) in ((11, 13), (12, 13), (13, 14), (14, 13), (15, 12)):
        px(a, cx, cy, '#c2c8ca' if (cx % 2) else '#7c8284')
    # the garbage-day sheet: yellowed paper, four colour rows (a dot and a ruled line each), brown tape corners
    rect(a, 3, 2, 8, 8, '#ddd6bc')
    vline(a, 8, 3, 8, '#c4bca0')
    hline(a, 3, 8, 8, '#c8c0a4')
    for yy, c in zip((3, 4, 5, 7), ('#b8503e', '#4a74b0', '#4a9452', '#c0983a')):
        px(a, 4, yy, c)
        hline(a, 5, 7, yy, '#9a927e')
    hline(a, 5, 6, 6, '#b4ac94')
    for (cx, cy) in ((3, 2), (8, 2)):
        px(a, cx, cy, '#b49a62')
    px(a, 8, 8, '#a89a7c')                       # the lower corner curling off the steel
    # a small coat hook screwed on near the hinge, its shadow
    px(a, 2, 12, '#3a3c3a')
    px(a, 2, 13, '#8e9496')
    px(a, 3, 13, '#6a7072')
    px(a, 3, 14, '#4a5052')
    return a


@door_tex('front_door_inner_bottom')
def front_door_inner_bottom():
    """Lower half, inside: the lever and its tall escutcheon with the deadbolt's thumb-turn turned upright (locked),
    the steel newspaper box behind the slot with the flyer's corner showing, scuffs from shoes, the rubber seal."""
    a = _steel_leaf(4131, False)
    x, y = xy()
    # the escutcheon (same spindle as the outer lever) and the lever pointing toward the hinge
    rect(a, 12, 1, 13, 7, '#2a2c2c')
    vline(a, 13, 1, 7, '#3e4242')
    rect(a, 9, 2, 13, 3, '#b8bec0')
    hline(a, 9, 12, 2, '#e0e4e4')
    px(a, 9, 3, '#7a8084')
    px(a, 13, 3, '#8a9092')
    # the thumb-turn below it, upright = locked
    px(a, 12, 5, '#d4d8d8')
    px(a, 12, 6, '#9aa0a2')
    px(a, 13, 6, '#2a2c2c')
    # the newspaper box behind the slot (x 4..10 y 6..8 outside): a steel pocket, open at its top, a flyer in it
    rect(a, 3, 5, 11, 10, '#59614f')
    hline(a, 3, 11, 5, '#7e8873')
    vline(a, 11, 5, 10, '#454c3e')
    hline(a, 3, 11, 10, '#3a4034')
    hline(a, 4, 10, 6, '#16181a')                # the dark mouth of the box
    rect(a, 6, 4, 7, 6, '#e6dfc8')               # the flyer's corner standing up out of it
    px(a, 7, 4, '#cfc6ac')
    px(a, 6, 6, '#bfb69c')
    dl(a, -0.06, (y == 11) & (x >= 4) & (x <= 11))   # the box's shadow on the leaf
    # scuffs where shoes hit it, the rubber seal along the bottom
    sc = (wrapn(4132, beta=1.0) > 0.45) & (y >= 12) & (y <= 14) & (x >= 2) & (x <= 13)
    dl(a, -0.08, sc)
    hline(a, 1, 15, 15, '#232624')
    px(a, 6, 13, '#3c3f3a')
    px(a, 10, 14, '#41443f')
    return a


WOODPRINT =ramp(['#5a3c22', '#6e4a2b', '#805734', '#91653e', '#a17348', '#ae8052', '#ba8d5e'], 7)


def _wood_leaf(seed, top):
    """Cheap flush door: wood-grain printed hardboard, vertical grain, an edge band."""
    x, y = xy()
    g = wrapn(seed, beta=0.8, sx=1, sy=12)
    g2 = wrapn(seed + 1, beta=1.5, sx=1, sy=4)
    t = x + 1.5 * np.sin(y * 0.4 + x * 0.3)
    lines = (np.abs(np.sin(t * 1.1)) < 0.13)
    a = from_idx(3.6 + g * 0.45 + g2 * 0.3 - lines * 0.7, WOODPRINT)
    vline(a, 0, 0, 15, WOODPRINT[1])
    vline(a, 1, 0, 15, WOODPRINT[5])
    vline(a, 15, 0, 15, WOODPRINT[2])
    if top:
        hline(a, 0, 15, 0, WOODPRINT[1])
        hline(a, 1, 14, 1, WOODPRINT[5])
    return a


def _knob(a, y0):
    """Round brass knob (握り玉) on the latch side."""
    rect(a, 11, y0, 13, y0 + 2, '#8a6a2a')
    px(a, 11, y0, '#e8d08a')
    px(a, 12, y0, '#c8a858')
    px(a, 11, y0 + 1, '#c8a858')
    px(a, 13, y0 + 2, '#5a4214')
    px(a, 12, y0 + 3, '#3a2a14')


@door_tex('interior_door_top')
def interior_door_top():
    a = _wood_leaf(4201, True)
    # two shallow grooves (a cheap "design") and a hand-grime patch around where you push
    hline(a, 3, 13, 5, WOODPRINT[2])
    hline(a, 3, 13, 6, WOODPRINT[5])
    grime(a, 4202, strength=0.1, color='#3a2410', beta=1.6)
    return a


@door_tex('interior_door_bottom')
def interior_door_bottom():
    a = _wood_leaf(4211, False)
    _knob(a, 1)
    hline(a, 3, 13, 11, WOODPRINT[2])
    hline(a, 3, 13, 12, WOODPRINT[5])
    # dark hand grime around the knob, a scuffed bottom
    tint(a, '#2a1a0c', 0.18, disc_mask(16, 16, 12.0, 2.5, 3.5, 3.0))
    wear(a[13:16], 4212, amount=0.35, edge_only=False, light=0.05, dark=-0.07)
    return a


@door_tex('toilet_door_top')
def toilet_door_top():
    a = _wood_leaf(4301, True)
    # a small frosted window (vertical, patterned glass) in the upper half
    rect(a, 5, 3, 10, 12, WOODPRINT[1])
    rect(a, 6, 4, 9, 11, '#c9d0cc')
    x, y = xy()
    pat = ((x + y) % 3 == 0) & (x >= 6) & (x <= 9) & (y >= 4) & (y <= 11)
    dl(a, 0.05, pat)
    hline(a, 6, 9, 4, '#e2e8e4')
    return a


@door_tex('toilet_door_bottom')
def toilet_door_bottom():
    a = _wood_leaf(4311, False)
    _knob(a, 1)
    # the lock indicator (表示錠): red = occupied, here showing blue (vacant)
    rect(a, 11, 5, 13, 6, '#2a2c2c')
    px(a, 12, 5, '#3a6ac8')
    px(a, 12, 6, '#2a4a88')
    # a ventilation grille at the bottom (undercut louvres)
    for yy in (11, 13):
        hline(a, 4, 12, yy, '#2a1a0e')
        hline(a, 4, 12, yy + 1, WOODPRINT[5])
    return a


def _folding_glass(seed, top):
    """Frosted folding bath door (折れ戸): white aluminium frame, two leaves meeting at a fold line, patterned acrylic
    (translucent, a small floral emboss), the grip on the fold."""
    x, y = xy()
    a = blank(16, 16, C('#d8dedc', 178))
    n = wrapn(seed, beta=1.4)
    a[..., :3] += n[..., None] * 4
    # patterned glass: tiny four-petal flowers in a lattice, a bit more opaque
    pat = (((x + (y // 4) * 2) % 4 == 1) & (y % 4 == 1)) | (((x + (y // 4) * 2) % 4 == 1) & (y % 4 == 3)) | \
          (((x + (y // 4) * 2) % 4 == 0) & (y % 4 == 2)) | (((x + (y // 4) * 2) % 4 == 2) & (y % 4 == 2))
    a[pat] = C('#eef2f0', 205)
    # soap scum / mould at the bottom of the panels
    if not top:
        for yy in (12, 13):
            a[yy, 2:14] = C('#b8c2bc', 196)
        a[13, 3:13:3] = C('#3a443e', 230)
    # frame: left/right stiles, the fold line in the middle (two thin white bars), rails
    for xx in (0, 1, 14, 15):
        a[:, xx] = C('#e8ecea')
    a[:, 0] = C('#a8b0ae')
    a[:, 15] = C('#b8c0be')
    a[:, 7] = C('#c8d0ce')
    a[:, 8] = C('#f0f4f2')
    if top:
        a[0:2, :] = C('#e8ecea')
        a[0, :] = C('#a8b0ae')
    else:
        a[14:16, :] = C('#e8ecea')
        a[15, :] = C('#98a09e')
        # the grip (a recessed handle on the fold)
        a[2:6, 7] = C('#7a8482')
        a[2:6, 8] = C('#a8b2b0')
    return a


@door_tex('bath_door_top')
def bath_door_top():
    return _folding_glass(4401, True)


@door_tex('bath_door_bottom')
def bath_door_bottom():
    return _folding_glass(4411, False)


def _alu_glass(seed, top):
    """Aluminium sliding balcony door (掃き出し窓): bronze-anodised frame, clear glass (translucent), rain beads on
    the outside, the crescent lock on the meeting stile."""
    r = rng(seed)
    x, y = xy()
    a = blank(16, 16, C('#7a909a', 62))
    diag = ((x + y * 0.8) % 13) < 1.3
    a[diag] = C('#c8d8de', 88)
    # rain beads: small brighter, more opaque dots with a dark lower rim, and a run streak
    for _ in range(9):
        bx, by = int(r.integers(2, 14)), int(r.integers(1, 15))
        a[by, bx] = C('#dfe8ec', 150)
        if by + 1 < 16:
            a[by + 1, bx] = C('#3a4a52', 120)
    sx = int(r.integers(4, 12))
    for yy in range(0, 16):
        if r.random() < 0.7:
            a[yy, sx] = C('#b8c8d0', 110)
    ALU_B = ['#3a2e24', '#54443a', '#6c5a4c', '#857060']
    for xx, c in ((0, ALU_B[0]), (1, ALU_B[2]), (14, ALU_B[1]), (15, ALU_B[3])):
        a[:, xx] = C(c)
    if top:
        a[0, :] = C(ALU_B[0])
        a[1, :] = C(ALU_B[2])
    else:
        a[14, :] = C(ALU_B[2])
        a[15, :] = C(ALU_B[0])
        # crescent lock on the meeting stile (right), at the top of the lower half
        a[1:4, 13] = C('#c8c4b8')
        a[2, 12] = C('#e8e4d8')
    return a


@door_tex('balcony_door_top')
def balcony_door_top():
    return _alu_glass(4501, True)


@door_tex('balcony_door_bottom')
def balcony_door_bottom():
    return _alu_glass(4511, False)


# ================================================================================================ door item sprites
def item(name):
    return texture('item/' + name, size=(16, 16), cutout=True)


def _icon(rows, legend):
    a = blank(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                a[y, x] = C(legend[ch])
    return a


DOOR_ROWS = ['....KKKKKKKK....',
             '....KfFFFFFfK...',
             '....KfssssssK...',
             '....KfsppppsK...',
             '....KfsppppsK...',
             '....KfsssosK....',
             '....KfssssssK...',
             '....KfssssssK...',
             '....KfssssssK...',
             '....KfsmmmmsK...',
             '....KfssssHhK...',
             '....KfssssssK...',
             '....KfssssssK...',
             '....KfssssssK...',
             '....KfkkkkkkK...',
             '....KKKKKKKKK...']


@item('front_door')
def front_door_item():
    L = {'K': '#1e221c', 'f': '#3c4238', 'F': '#5f6758', 's': '#717a69', 'p': '#717a69', 'o': '#1a1c1e',
         'm': '#121314', 'H': '#d8dcdc', 'h': '#8a9092', 'k': '#8e9496'}
    a = _icon(DOOR_ROWS, L)
    rect(a, 6, 2, 7, 7, '#2a1c12')
    for yy in (2, 4, 6):
        px(a, 6, yy, '#a88a64')
        px(a, 7, yy, '#a88a64')
    px(a, 9, 4, '#1a1c1e')
    return a


@item('interior_door')
def interior_door_item():
    L = {'K': '#2a1a0e', 'f': '#5a3c22', 'F': '#91653e', 's': '#91653e', 'p': '#805734', 'o': '#91653e',
         'm': '#805734', 'H': '#e8d08a', 'h': '#8a6a2a', 'k': '#6e4a2b'}
    a = _icon(DOOR_ROWS, L)
    for yy in range(2, 14, 3):
        px(a, 7, yy, '#a17348')
    return a


@item('toilet_door')
def toilet_door_item():
    L = {'K': '#2a1a0e', 'f': '#5a3c22', 'F': '#91653e', 's': '#91653e', 'p': '#c9d0cc', 'o': '#91653e',
         'm': '#805734', 'H': '#e8d08a', 'h': '#8a6a2a', 'k': '#6e4a2b'}
    a = _icon(DOOR_ROWS, L)
    px(a, 11, 11, '#3a6ac8')
    return a


@item('bath_door')
def bath_door_item():
    L = {'K': '#8a9290', 'f': '#e8ecea', 'F': '#f4f6f5', 's': '#d0d8d6', 'p': '#d0d8d6', 'o': '#d0d8d6',
         'm': '#d0d8d6', 'H': '#7a8482', 'h': '#a8b2b0', 'k': '#e8ecea'}
    a = _icon(DOOR_ROWS, L)
    for yy in range(2, 14):
        px(a, 8, yy, '#f4f6f5')
        if yy % 2 == 0:
            px(a, 6, yy, '#e6ecea')
            px(a, 10, yy, '#e6ecea')
    return a


@item('balcony_door')
def balcony_door_item():
    L = {'K': '#3a2e24', 'f': '#6c5a4c', 'F': '#857060', 's': '#8fb0bc', 'p': '#8fb0bc', 'o': '#8fb0bc',
         'm': '#8fb0bc', 'H': '#e8e4d8', 'h': '#c8c4b8', 'k': '#54443a'}
    a = _icon(DOOR_ROWS, L)
    for (xx, yy) in ((7, 3), (8, 4), (9, 5), (7, 8), (8, 9)):
        px(a, xx, yy, '#d8e8ee')
    return a


FUSUMA_ICON = ['....KKKKKKKK....',
               '....KppppppK....',
               '....KpbpppbK....',
               '....KppbpbpK....',
               '....KppppppK....',
               '....KmmmmmmK....',
               '....KppppppK....',
               '....KgppppcK....',
               '....KhppppcK....',
               '....KppppppK....',
               '....KwwwwwwK....',
               '....KpwwwwpK....',
               '....KwppppwK....',
               '....KppppppK....',
               '....KyyyyyyK....',
               '....KKKKKKKK....']


@item('fusuma')
def fusuma_item():
    """A fusuma panel: black lacquered frame, cream paper with the faint birds, mist and flowing water, the pull."""
    L = {'K': '#151110', 'p': '#e2dcc8', 'b': '#8a8a84', 'm': '#d4c494', 'g': '#b8943e', 'h': '#2a1c0e',
         'c': '#d0c9b3', 'w': '#9aa8aa', 'y': '#c8b88e'}
    return _icon(FUSUMA_ICON, L)


@item('oshiire_fusuma')
def oshiire_fusuma_item():
    """The closet's fusuma: plainer worn paper, a taped repair, the faded rabbit sticker."""
    L = {'K': '#151110', 'p': '#d6cfb8', 'b': '#d6cfb8', 'm': '#d6cfb8', 'g': '#b8943e', 'h': '#2a1c0e',
         'c': '#d6cfb8', 'w': '#d6cfb8', 'y': '#b8a878'}
    a = _icon(FUSUMA_ICON, L)
    for (x, y, c) in ((6, 9, '#ece8e0'), (8, 9, '#ece8e0'), (6, 10, '#ece8e0'), (7, 10, '#ece8e0'),
                      (8, 10, '#ece8e0'), (6, 11, '#4a4846'), (7, 11, '#ece8e0'), (8, 11, '#4a4846')):
        px(a, x, y, c)
    rect(a, 7, 3, 10, 3, '#c8b47a')
    return a
