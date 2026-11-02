"""Textures of the custom-shaped prop blocks (models: gen_models.py).

Each prop texture is a 16x16 atlas of named regions; REG[texture][region] = (u0, v0, u1, v1) in pixels is shared
with gen_models.py so the model UVs always match what is painted here."""
import math

import numpy as np

from common import Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, rng, blob, lighten_arr, erode, dilate
from px import paint_idx, despeckle, emboss, stamp, curve_pts, line_pts, overlay, outline_alpha, shade_sprite, \
    voronoi, jitter_points
import s_town
from s_town import GREYWOOD, DARKWOOD, RUST, CONC, GALV, IRON
from s_nature import MOSS, ROCK, bark_tex

REG = {}

PAPER = ramp(['#5d574a', '#77705f', '#8f8874', '#a49d88', '#b5ae98', '#c2bba5'], 6)
INK = pal('#1b1a1f', '#2b2a31', '#3d3b44')
STONE = ramp(['#1e1f1f', '#2c2d2c', '#3b3c3a', '#4b4c49', '#5b5c58', '#6b6c67', '#7b7c76'], 7)
REDCLOTH = ramp(['#3a0f0c', '#561711', '#712219', '#8a2f22', '#9e3e2d'], 5)
NYLON = ramp(['#3a120b', '#5a1c10', '#7d2915', '#9c3a1c', '#b44d26', '#c45f32'], 6)   # red-orange pack
WEB = pal('#0d0d0e', '#19191b', '#26262a', '#36363b')
STRAW = ramp(['#2a2213', '#43381f', '#5e4f2d', '#7a673c', '#94804c', '#a8945d'], 6)
GREENPHONE = ramp(['#0c1c12', '#123021', '#1a4430', '#24583e', '#306b4c', '#3f7d5b'], 6)
WHITEPAINT = ramp(['#4a4b4a', '#6b6c69', '#8a8a86', '#a5a59f', '#b9b9b2', '#c8c7bf'], 6)
BRASS = pal('#3b2d12', '#5f4a1e', '#86692c', '#a8873d', '#c2a254')
FLAME = pal('#ffe8a8', '#ffc65c', '#ff9a2e', '#e8641c')


def region(tex_name, name, u0, v0, u1, v1):
    REG.setdefault(tex_name, {})[name] = (u0, v0, u1, v1)


def fill_region(t, rect, fn):
    u0, v0, u1, v1 = [int(round(c)) for c in rect]
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            c = fn(xx - u0, yy - v0, u1 - u0, v1 - v0)
            if c is not None:
                t.set(xx, yy, c, wrap=False)


def noisy(Pal, seed, base, amp=0.6, beta=0.8, sx=1.0, sy=1.0, w=16, h=16):
    n = fnoise(w, h, seed, beta=beta, sx=sx, sy=sy)

    def f(x, y, w_, h_, _n=n):
        return Pal[int(np.clip(round(base + _n[y % h, x % w] * amp), 0, len(Pal) - 1))]
    return f


def woodgrain(Pal, seed, base=3.5, vertical=False):
    n = fnoise(16, 16, seed, beta=0.9, sx=1 if vertical else 6, sy=6 if vertical else 1)

    def f(x, y, w_, h_):
        v = base + n[y, x] * 0.8
        if vertical and x in (0, w_ - 1):
            v -= 0.8
        if not vertical and y in (0, h_ - 1):
            v -= 0.8
        return Pal[int(np.clip(round(v), 0, len(Pal) - 1))]
    return f


def scribble(t, x0, y0, x1, y1, seed, color, density=0.55, line_gap=2, alpha=0.7):
    """Suggestive handwriting: faded vertical stroke clusters in columns right-to-left (Japanese), never legible."""
    r = rng(seed)
    x = x1
    while x >= x0:
        y = y0 + int(r.integers(0, 3))
        while y <= y1:
            ln = int(r.integers(1, 4))
            if r.random() < density:
                for k in range(ln):
                    if y + k <= y1:
                        t.blend(x, y + k, color, alpha * (0.75 + 0.25 * r.random()), wrap=False)
                if r.random() < 0.25 and x - 1 >= x0:
                    t.blend(x - 1, y + ln // 2, color, alpha * 0.6, wrap=False)
            y += ln + 1 + int(r.integers(0, 2))
        x -= line_gap + (1 if r.random() < 0.2 else 0)


# ================================================================================================ lost bag
region('lost_bag', 'body', 0, 0, 10, 6)        # long faces of the main body (10 x 6)
region('lost_bag', 'end', 10, 0, 16, 6)        # ends of the body (6 x 6)
region('lost_bag', 'pocket', 0, 6, 8, 11)      # front pocket face (8 x 5)
region('lost_bag', 'pocket_side', 8, 6, 10, 11)
region('lost_bag', 'lid', 10, 6, 16, 11)       # top lid (6 x 5)
region('lost_bag', 'strap', 0, 11, 2, 16)      # black webbing (2 x 5)
region('lost_bag', 'lining', 10, 11, 16, 16)   # inside (open state)
region('lost_bag', 'bottom', 2, 11, 10, 16)    # muddy base fabric


@texture('block/lost_bag', opaque=True)
def lost_bag():
    t = Tex(16)
    R = REG['lost_bag']
    rip = lambda x, y, w, h: None
    n = fnoise(16, 16, 401, beta=1.0)

    def fabric(base, mud_bottom=False):
        def f(x, y, w, h):
            v = base + n[y % 16, x % 16] * 0.6 + (0.6 if (x % 3 == 0 and y % 3 == 0) else 0)
            if y == 0:
                v += 0.7
            if y == h - 1:
                v -= 0.8
            return NYLON[int(np.clip(round(v), 0, 5))]
        return f
    fill_region(t, R['body'], fabric(3.2))
    fill_region(t, R['end'], fabric(2.6))
    fill_region(t, R['pocket'], fabric(2.8))
    fill_region(t, R['pocket_side'], fabric(2.0))
    fill_region(t, R['lid'], fabric(2.2))
    fill_region(t, R['bottom'], fabric(1.8))
    fill_region(t, R['strap'], lambda x, y, w, h: WEB[2 if x == 0 else 1] if y != 2 else WEB[3])
    fill_region(t, R['lining'], lambda x, y, w, h: ['#0b0a0b', '#141215', '#1c1a1e'][(x + y) % 3])
    # zipper along the body top edge and around the pocket
    for xx in range(0, 10):
        t.set(xx, 1, '#2a2522' if xx % 2 else '#6b655d')
    for xx in range(0, 8):
        t.set(xx, 6, '#2a2522' if xx % 2 else '#6b655d')
    t.set(6, 7, '#9a958c')            # zipper puller
    t.set(6, 8, '#4a4540')
    # reflective logo patch, buckles
    t.set(2, 8, '#8f8a80')
    t.set(3, 8, '#7d786e')
    t.set(0, 13, '#4a4a4f')
    t.set(1, 13, '#3a3a3e')
    for xx in range(10, 16):
        t.set(xx, 6, '#1b1a1c')       # drawstring hem of the lid
    # wet sheen: a few light streaks on the top
    for (sx, sy) in ((3, 3), (4, 3), (8, 4), (12, 2)):
        t.dl(sx, sy, 0.07)
    # mud: splashed from below on every face, caked on the base
    MUD = pal('#2a1d14', '#3b2a1d', '#4d3726')
    wn = white(16, 16, 402)
    for (x0, y0, x1, y1) in (R['body'], R['end'], R['pocket'], R['lid']):
        for yy in range(int(y1) - 2, int(y1)):
            for xx in range(int(x0), int(x1)):
                if wn[yy, xx] < (0.25 if yy == y1 - 2 else 0.6):
                    t.blend(xx, yy, MUD[int(wn[yy, xx] * 7) % 3], 0.75)
    u0, v0, u1, v1 = R['bottom']
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            if wn[yy, xx] < 0.7:
                t.blend(xx, yy, MUD[(xx + yy) % 3], 0.8)
    return t


# ================================================================================================ paper decals
def _paper_sheet(seed, w=16, h=16, damp=0.5):
    t = Tex(w, h)
    n = fnoise(w, h, seed, beta=1.4)
    v = 3.6 + n * 0.7
    q = np.clip(np.round(v), 0, 5).astype(int)
    paint_idx(t, q, PAPER)
    # water stain rings
    st = blob(w, h, w * 0.65, h * 0.7, w * 0.25, h * 0.2, seed + 1, rough=0.4, wrap=False)
    ring = (st > 0) & (st < 0.25)
    t.a[ring, :3] *= np.array([0.86, 0.82, 0.74])
    inside = st >= 0.25
    t.a[inside, :3] *= np.array([0.95, 0.93, 0.88])
    return t


region('memo', 'sheet', 0, 0, 16, 16)


@texture('block/memo', cutout=True)
def memo():
    """A sheet of damp notebook paper lying on the ground: faded pencil lines, torn edge, mud print."""
    t = _paper_sheet(411)
    x, y = grid(16, 16)
    # ruled lines (faint blue) and handwriting
    for yy in (4, 7, 10, 13):
        for xx in range(1, 15):
            t.blend(xx, yy, '#5e6c80', 0.25)
    scribble(t, 3, 2, 13, 14, 412, '#3c3a42', density=0.45, alpha=0.55)
    # torn top edge, a dog-ear, rotten corner
    for xx in range(16):
        cut = [1, 0, 0, 1, 2, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0][xx]
        for yy in range(cut):
            t.clear(xx, yy)
    for (cx, cy) in ((15, 15), (14, 15), (15, 14), (0, 15)):
        t.clear(cx, cy)
    t.set(13, 15, PAPER[1])
    t.set(14, 14, PAPER[1])
    # muddy shoe print across one corner
    for (mx, my) in ((2, 11), (3, 11), (2, 12), (3, 12), (4, 12), (3, 13), (4, 13), (2, 14), (3, 14)):
        t.blend(mx, my, '#3b2a1c', 0.6)
    return t


region('memo_wall', 'sheet', 2, 1, 14, 15)


@texture('block/memo_wall', cutout=True)
def memo_wall():
    """Paper nailed to a trunk: rain-streaked ink running down, a rusty nail, curled corner."""
    t = Tex(16)
    sheet = _paper_sheet(421, 12, 14)
    for yy in range(14):
        for xx in range(12):
            t.a[1 + yy, 2 + xx] = sheet.a[yy, xx]
    scribble(t, 4, 4, 12, 13, 422, '#2a2930', density=0.5, alpha=0.6)
    # ink runs from the rain
    for (rx, ry) in ((11, 8), (9, 10), (6, 7)):
        for k in range(3):
            t.blend(rx, ry + k, INK[2], 0.45 - k * 0.12)
    # nail + rust stain
    t.set(8, 2, '#1a120d')
    t.blend(8, 3, RUST[3], 0.6)
    t.blend(8, 4, RUST[3], 0.3)
    # curled lower-right corner (shows the paper's darker back), torn left edge
    for (cx, cy) in ((13, 14), (13, 13), (12, 14)):
        t.clear(cx, cy)
    t.set(12, 13, PAPER[1])
    t.set(11, 14, PAPER[2])
    for yy in (5, 6, 11):
        t.clear(2, yy)
    return t


# ================================================================================================ logbook
region('logbook', 'page_l', 0, 0, 5, 8)
region('logbook', 'page_r', 5, 0, 10, 8)
region('logbook', 'cover', 10, 0, 16, 8)       # dark green cloth cover
region('logbook', 'edges', 0, 8, 10, 10)       # stacked page edges
region('logbook', 'pencil', 0, 10, 8, 11)
region('logbook', 'cover_edge', 10, 8, 16, 10)


@texture('block/logbook', opaque=True)
def logbook():
    """登山者ノート: an open hikers' logbook, swollen with damp; pencil on a string."""
    t = Tex(16)
    R = REG['logbook']
    fill_region(t, R['page_l'], noisy(PAPER, 431, 3.8, 0.5))
    fill_region(t, R['page_r'], noisy(PAPER, 432, 3.6, 0.5))
    for yy in range(1, 8, 2):
        for xx in range(0, 10):
            t.blend(xx, yy, '#55627a', 0.22)
    scribble(t, 0, 1, 4, 7, 433, '#34333b', density=0.5, alpha=0.6)
    scribble(t, 6, 1, 9, 5, 434, '#2a2930', density=0.55, alpha=0.65)
    # spine shadow
    for yy in range(8):
        t.dl(4, yy, -0.08)
        t.dl(5, yy, -0.1)
    COVER = ramp(['#0e1712', '#16231b', '#1f3024', '#2a3d2f'], 4)
    fill_region(t, R['cover'], noisy(COVER, 435, 1.8, 0.6))
    fill_region(t, R['cover_edge'], lambda x, y, w, h: COVER[1])
    fill_region(t, R['edges'], lambda x, y, w, h: PAPER[3] if (x + y) % 3 else PAPER[1])
    fill_region(t, R['pencil'], lambda x, y, w, h: ['#2a2420', '#c6b36a', '#b49f55', '#b49f55', '#b49f55', '#b49f55', '#9b8a4c', '#d9c9a9'][x])
    # stain of something dark on the right page
    t.blend(8, 6, '#3a2018', 0.5)
    t.blend(7, 6, '#3a2018', 0.3)
    t.blend(8, 7, '#3a2018', 0.4)
    return t


# ================================================================================================ notice board
region('notice_board', 'board', 0, 0, 14, 9)       # front: planks with pinned papers (14 x 9)
region('notice_board', 'post', 14, 0, 16, 8)       # post wood (2 x 8)
region('notice_board', 'roof', 0, 9, 16, 12)       # roof planks
region('notice_board', 'edge', 0, 12, 16, 13)      # board edges
region('notice_board', 'back', 0, 13, 14, 16)      # back of the board (plain planks)


@texture('block/notice_board', opaque=True)
def notice_board():
    """Village notice board (掲示板): weathered planks, three pinned notices (山の掟 among them), a roof."""
    t = Tex(16)
    R = REG['notice_board']
    fill_region(t, R['board'], lambda x, y, w, h: GREYWOOD[3 + (1 if y % 3 == 0 else 0) - (1 if y % 3 == 2 else 0)
                                                                + (1 if (x * 7 + y) % 11 == 0 else 0)])
    fill_region(t, R['back'], lambda x, y, w, h: GREYWOOD[2 + (y % 3 == 0)])
    fill_region(t, R['post'], woodgrain(GREYWOOD, 441, 3.0, vertical=True))
    fill_region(t, R['roof'], lambda x, y, w, h: DARKWOOD[[5, 3, 1][y]] if x % 5 else DARKWOOD[1])
    fill_region(t, R['edge'], lambda x, y, w, h: GREYWOOD[2])
    # papers: the big one is the mountain rules (vertical columns of strokes), two small ones
    for (x0, y0, x1, y1, seed, tone) in ((1, 1, 7, 8, 442, 4), (8, 1, 12, 5, 443, 3), (9, 6, 13, 8, 444, 2)):
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                t.set(xx, yy, PAPER[tone - (1 if (xx + yy) % 5 == 0 else 0)])
        scribble(t, x0 + 1, y0 + 1, x1 - 2, y1 - 1, seed, INK[0], density=0.65, alpha=0.75)
        t.set((x0 + x1) // 2, y0, '#8f2a20')      # red pin
        t.dl(x1, y0 + 1, -0.1)
    # rain streaks, a torn corner hanging
    for (rx, ry) in ((3, 6), (5, 7)):
        t.blend(rx, ry, '#4a4236', 0.5)
    t.set(12, 4, GREYWOOD[3])
    t.set(11, 4, PAPER[1])
    return t


# ================================================================================================ signpost
region('signpost', 'board_l', 0, 0, 14, 4)     # arrow board pointing left
region('signpost', 'board_r', 0, 4, 14, 8)     # arrow board pointing right
region('signpost', 'post', 14, 0, 16, 16)      # post (2 x 16)
region('signpost', 'board_edge', 0, 8, 14, 9)
region('signpost', 'cap', 0, 9, 4, 13)         # little roof cap


@texture('block/signpost', cutout=True)
def signpost():
    """Wooden trail sign: two arrow boards with carved (not readable) strokes on a post."""
    t = Tex(16)
    R = REG['signpost']
    fill_region(t, R['post'], woodgrain(GREYWOOD, 451, 3.2, vertical=True))
    for name, left in (('board_l', True), ('board_r', False)):
        u0, v0, u1, v1 = R[name]
        for yy in range(v0, v1):
            for xx in range(u0, u1):
                lv = 4 + (1 if yy == v0 else 0) - (1 if yy == v1 - 1 else 0) + (1 if (xx * 5 + yy) % 13 == 0 else 0)
                t.set(xx, yy, GREYWOOD[lv])
        # arrow tip: cut the corners on the pointing end
        tipx, step = (u0, 1) if left else (u1 - 1, -1)
        for k, (yy0, yy1) in enumerate(((v0, v0), (v1 - 1, v1 - 1))):
            t.clear(tipx, yy0)
            t.clear(tipx, yy1)
        # carved strokes (dark grooves with a lit lower edge)
        r = rng(452 if left else 453)
        xs = range(u0 + 3, u1 - 2) if left else range(u0 + 2, u1 - 3)
        for xx in xs:
            if r.random() < 0.55:
                t.set(xx, v0 + 1, GREYWOOD[1])
                t.set(xx, v0 + 2, GREYWOOD[5])
            elif r.random() < 0.4:
                t.set(xx, v0 + 1, GREYWOOD[1])
                t.set(xx, v0 + 2, GREYWOOD[1])
    fill_region(t, R['board_edge'], lambda x, y, w, h: GREYWOOD[3])
    fill_region(t, R['cap'], lambda x, y, w, h: DARKWOOD[4 - y])
    # moss on the post foot and the upper board
    for (mx, my) in ((14, 14), (15, 15), (14, 15), (12, 0), (13, 1)):
        t.set(mx, my, MOSS[3] if (mx + my) % 2 else MOSS[2])
    return t


# ================================================================================================ jizo
region('jizo', 'face', 0, 0, 5, 4)             # head front (5 x 4)
region('jizo', 'head', 5, 0, 10, 4)            # head sides / back
region('jizo', 'cap', 10, 0, 16, 3)            # red knitted cap (6 x 3)
region('jizo', 'cap_top', 10, 3, 16, 6)
region('jizo', 'robe_front', 0, 4, 6, 11)      # body front (6 x 7)
region('jizo', 'robe', 6, 4, 10, 11)           # body sides (4 x 7)
region('jizo', 'bib', 10, 6, 16, 11)           # red bib (6 x 5), cutout triangle
region('jizo', 'base_top', 0, 11, 8, 16)       # base stone
region('jizo', 'base_side', 8, 11, 16, 13)


@texture('block/jizo', cutout=True)
def jizo():
    """Small stone jizo: eroded calm face, red knitted cap and bib (faded), moss on the shoulders."""
    t = Tex(16)
    R = REG['jizo']
    sn = fnoise(16, 16, 461, beta=1.0)

    def stone(base):
        def f(x, y, w, h):
            return STONE[int(np.clip(round(base + sn[y % 16, x % 16] * 0.8), 0, 6))]
        return f
    for k in ('face', 'head', 'robe_front', 'robe', 'base_top', 'base_side'):
        fill_region(t, R[k], stone(4.0 if k in ('face', 'robe_front') else 3.3))
    # face: closed eyes (two short dark lines), a soft mouth, eroded nose
    t.set(1, 1, STONE[1])
    t.set(3, 1, STONE[1])
    t.set(2, 2, STONE[5])
    t.set(2, 3, STONE[2])
    # hands together in prayer on the robe, robe folds
    t.set(2, 7, STONE[5])
    t.set(3, 7, STONE[5])
    t.set(2, 8, STONE[4])
    t.set(3, 8, STONE[4])
    for yy in range(5, 11):
        t.set(1, yy, STONE[2])
        t.set(4, yy, STONE[2])
    KNIT = ramp(['#3e1210', '#5b1b16', '#77261d', '#8e3326'], 4)
    fill_region(t, R["cap"], lambda x, y, w, h: KNIT[min(3, [3, 2, 1][y] - (1 if x % 2 else 0) + (1 if y == 0 and x % 2 == 0 else 0))])
    fill_region(t, R['cap_top'], lambda x, y, w, h: KNIT[2 - (1 if (x + y) % 2 else 0)])
    u0, v0, u1, v1 = R['bib']
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            j = yy - v0
            half = (u1 - u0) / 2
            if abs(xx - u0 - half + 0.5) <= half - j * 0.6:
                lv = 3 if j == 0 else (2 if (xx + yy) % 3 else 1)
                t.set(xx, yy, REDCLOTH[lv])
    for (dx, dy) in ((12, 8), (13, 9)):
        t.blend(dx, dy, '#3a2a20', 0.45)
    # moss
    for (mx, my) in ((6, 4), (7, 4), (9, 5), (0, 11), (1, 11), (2, 12), (8, 11), (9, 11), (5, 0), (6, 0)):
        t.set(mx, my, MOSS[3] if (mx + my) % 2 else MOSS[4])
    return t


# ================================================================================================ hokora
region('hokora', 'front', 0, 0, 8, 7)          # shrine front: doors with lattice + paper (8 x 7)
region('hokora', 'side', 8, 0, 14, 7)          # shrine sides (6 x 7)
region('hokora', 'roof', 0, 7, 16, 10)         # roof boards (dark, mossy)
region('hokora', 'roof_edge', 0, 10, 16, 11)
region('hokora', 'base', 0, 11, 12, 14)        # stone base sides
region('hokora', 'base_top', 0, 14, 12, 16)
region('hokora', 'bottle', 12, 11, 14, 16)     # sake bottle (white ceramic tokkuri)
region('hokora', 'candle', 14, 11, 15, 14)
region('hokora', 'flame', 14, 14, 16, 16)
region('hokora', 'wood', 14, 0, 16, 7)


@texture('block/hokora', cutout=True)
def hokora():
    """Tiny wooden shrine (祠): silver-grey weathered wood, lattice doors with torn faded paper, a small
    shimenawa, mossy roof, stone base; offered state adds a sake bottle and a candle (flame)."""
    t = Tex(16)
    R = REG['hokora']
    fill_region(t, R['side'], woodgrain(GREYWOOD, 471, 3.4, vertical=True))
    fill_region(t, R['wood'], woodgrain(GREYWOOD, 472, 3.0, vertical=True))
    u0, v0, u1, v1 = R['front']
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            frame = xx in (u0, u1 - 1) or yy in (v0, v1 - 1) or xx == (u0 + u1) // 2
            lattice = (xx - u0) % 2 == 1 and (yy - v0) % 2 == 1
            if frame:
                t.set(xx, yy, GREYWOOD[3])
            elif lattice:
                t.set(xx, yy, GREYWOOD[2])
            else:
                t.set(xx, yy, PAPER[2] if (xx + yy) % 4 else PAPER[1])
    # torn paper: dark holes into the shrine
    for (hx, hy) in ((2, 3), (2, 4), (6, 2), (5, 5)):
        t.set(hx, hy, '#090807')
    # small rope with two shide above the doors
    for xx in range(u0, u1):
        t.set(xx, v0, STRAW[3] if xx % 2 else STRAW[2])
    t.set(2, 1, '#cfc9b8')
    t.set(5, 1, '#cfc9b8')
    ROOF = ramp(['#0f0d0b', '#1b1714', '#28221d', '#352d26', '#433930'], 5)
    fill_region(t, R['roof'], lambda x, y, w, h: ROOF[[3, 2, 1][y] + (1 if x % 4 == 0 and y < 2 else 0)])
    for (mx, my) in ((2, 7), (3, 7), (9, 8), (10, 7), (13, 7), (6, 8)):
        t.set(mx, my, MOSS[3] if (mx + my) % 2 else MOSS[4])
    fill_region(t, R['roof_edge'], lambda x, y, w, h: ROOF[1])
    sn = fnoise(16, 16, 473, beta=1.0)
    fill_region(t, R['base'], lambda x, y, w, h: STONE[int(np.clip(round(3.2 + sn[y, x] * 0.9 - (y == h - 1)), 0, 6))])
    fill_region(t, R['base_top'], lambda x, y, w, h: STONE[int(np.clip(round(4 + sn[y + 8, x] * 0.8), 0, 6))])
    for (mx, my) in ((0, 13), (1, 13), (10, 13), (11, 12)):
        t.set(mx, my, MOSS[3])
    # offering: white ceramic sake bottle (tokkuri) with a faded label, candle, flame
    BOT = pal('#5e5d58', '#8d8b84', '#b6b3aa', '#d4d0c6')
    stamp(t, ["ab", "dc", "dc", "dc", "dc"], 12, 11, {'a': BOT[3], 'b': BOT[2], 'c': BOT[1], 'd': BOT[2]}, wrap=False)
    t.set(12, 13, '#a0302a')      # red label
    t.set(13, 13, '#7a241f')
    fill_region(t, R['candle'], lambda x, y, w, h: ['#d8d2c2', '#cfc8b6', '#b9b1a0'][y])
    u0, v0, u1, v1 = R['flame']
    t.set(14, 14, FLAME[1])
    t.set(15, 14, FLAME[0])
    t.set(14, 15, FLAME[2])
    t.set(15, 15, FLAME[1])
    return t


# ================================================================================================ wara ningyo
region('wara_ningyo', 'doll', 0, 0, 16, 16)


@texture('block/wara_ningyo', cutout=True)
def wara_ningyo():
    """Straw doll (藁人形) nailed to a trunk through the chest; a scrap of paper with a name."""
    t = Tex(16)
    S = STRAW
    rows = [
        "......aa........",
        ".....abba.......",
        ".....abbc.......",
        "......bc........",
        "......dd........",
        ".aabbbbbbbbccc..",
        "aabbbbbbbbbbcc.",
        "......bbcc......",
        ".....abbbc......",
        ".....abbbc......",
        ".....dddd.......",
        ".....abbbc......",
        "....ab..bc......",
        "....ab...bc.....",
        "...ab.....bc....",
        "...a.......c....",
    ]
    stamp(t, rows, 1, 0, {'a': S[4], 'b': S[3], 'c': S[1], 'd': '#2b2116'}, wrap=False)
    # loose straws sticking out
    for (sx, sy) in ((1, 4), (14, 6), (0, 7), (12, 15), (2, 16 - 1)):
        t.set(sx, sy, S[2], wrap=False)
    # the nail through the chest + a smear of rust, paper slip
    t.set(8, 8, '#141110')
    t.set(9, 8, '#2a2522')
    t.blend(8, 9, RUST[3], 0.6)
    for (px_, py_) in ((10, 9), (11, 9), (10, 10), (11, 10), (10, 11), (11, 11)):
        t.set(px_, py_, PAPER[3] if py_ % 2 else PAPER[2])
    t.set(10, 10, INK[0])
    t.set(11, 11, INK[1])
    return t


# ================================================================================================ rope anchor
region('rope_anchor', 'iron', 0, 0, 4, 8)
region('rope_anchor', 'ring', 4, 0, 12, 2)
region('rope_anchor', 'rope', 0, 8, 16, 10)
region('rope_anchor', 'knot', 0, 10, 4, 14)
region('rope_anchor', 'plate', 4, 2, 12, 8)


@texture('block/rope_anchor', opaque=True)
def rope_anchor():
    """Climber's iron stake (ハーケン/ボルト) with a ring, rusted; the player's orange rope tied to it."""
    t = Tex(16)
    R = REG['rope_anchor']
    fill_region(t, R['iron'], lambda x, y, w, h: RUST[[4, 3, 2, 1][x]] if (x + y) % 5 else RUST[0])
    fill_region(t, R['ring'], lambda x, y, w, h: IRON[3 if y == 0 else 1] if x % 3 else RUST[2])
    fill_region(t, R['plate'], lambda x, y, w, h: RUST[2 + ((x * 3 + y) % 4 == 0)] if (x, y) not in ((1, 1), (6, 1), (1, 4), (6, 4)) else IRON[0])
    ROPE = ramp(['#4a1e0c', '#7a3312', '#a8481a', '#c95f24', '#dd7a3a'], 5)
    fill_region(t, R['rope'], lambda x, y, w, h: ROPE[[3, 1][y] + (1 if (x + y) % 3 == 0 else 0) - (1 if (x + y) % 3 == 2 else 0)])
    fill_region(t, R['knot'], lambda x, y, w, h: ROPE[2 + ((x + y) % 2) - (1 if y == h - 1 else 0)])
    for (dx, dy) in ((3, 8), (9, 9), (13, 8)):
        t.blend(dx, dy, '#2a1c12', 0.4)
    return t


# ================================================================================================ climbing rope
region('climbing_rope', 'rope', 0, 0, 16, 16)


@texture('block/climbing_rope', cutout=True)
def climbing_rope():
    """The player's orange rope hanging down the cliff, with grip knots every 4 px, muddy and frayed."""
    t = Tex(16)
    ROPE = ramp(['#3b180a', '#6a2c10', '#94401a', '#b85422', '#d06c32', '#e08a50'], 6)
    for yy in range(16):
        knot = yy % 8 in (3, 4)
        xs = [6, 7, 8, 9] if knot else [7, 8]
        for i, xx in enumerate(xs):
            twist = (yy + xx) % 3
            lv = [4, 3, 2][twist] if not knot else [3, 4, 3, 1][i]
            if xx == xs[-1]:
                lv = max(0, lv - 2)
            t.set(xx, yy, ROPE[lv])
    for (fx, fy) in ((6, 5), (10, 4), (9, 13), (6, 12)):
        t.set(fx, fy, ROPE[2])
    for yy in (12, 13):
        t.blend(7, yy, '#3a281a', 0.5)
    return t


# ================================================================================================ public phone
region('public_phone', 'front', 0, 0, 8, 9)        # green body front (8 x 9): keypad, display, slots
region('public_phone', 'side', 8, 0, 14, 9)        # body sides / back (6 x 9)
region('public_phone', 'top', 0, 9, 8, 15)         # top (8 x 6)
region('public_phone', 'handset', 14, 0, 16, 7)    # handset (2 x 7)
region('public_phone', 'shelf', 8, 9, 16, 14)      # shelf wood
region('public_phone', 'cord', 14, 7, 16, 9)


@texture('block/public_phone', opaque=True)
def public_phone():
    """Old green public telephone (公衆電話) on a shelf: keypad, dead LCD, coin slot, curly cord."""
    t = Tex(16)
    R = REG['public_phone']
    gn = fnoise(16, 16, 481, beta=1.0)
    green = lambda base: (lambda x, y, w, h: GREENPHONE[int(np.clip(round(base + gn[y % 16, x % 16] * 0.5
                                                                            + (0.8 if y == 0 else 0) - (0.8 if y == h - 1 else 0)), 0, 5))])
    fill_region(t, R['front'], green(3.0))
    fill_region(t, R['side'], green(2.3))
    fill_region(t, R['top'], green(3.4))
    fill_region(t, R['handset'], lambda x, y, w, h: GREENPHONE[[4, 2][x] - (1 if y in (0, h - 1) else 0)])
    fill_region(t, R['cord'], lambda x, y, w, h: GREENPHONE[1] if (x + y) % 2 else GREENPHONE[0])
    fill_region(t, R['shelf'], woodgrain(DARKWOOD, 482, 3.6))
    # front details: LCD (dead, grey-green), 12 keys, coin return
    for xx in range(1, 7):
        t.set(xx, 1, '#2b3530')
    t.set(1, 1, '#1f2723')
    for ky in range(3):
        for kx in range(3):
            t.set(2 + kx * 2 - 1 + 1, 3 + ky * 1 + ky, '#c8c4b6' if (kx + ky) % 2 == 0 else '#b3ae9f')
    t.set(6, 7, '#0d1510')
    t.set(5, 7, '#0d1510')
    t.set(1, 7, '#a33a2a')            # red emergency button
    # top: card slot + coin slot
    t.set(2, 11, '#0a0f0c')
    t.set(3, 11, '#0a0f0c')
    t.set(4, 11, '#0a0f0c')
    t.set(5, 13, '#0a0f0c')
    t.set(5, 12, '#0a0f0c')
    # grime and a cobweb corner
    for (gx, gy) in ((0, 8), (7, 8), (8, 8), (13, 8)):
        t.dl(gx, gy, -0.08)
    t.set(7, 0, '#8a8c86')
    t.set(13, 0, '#7a7c76')
    return t


# ================================================================================================ bus stop
region('bus_stop', 'sign', 0, 0, 10, 10)       # round sign face (cutout disk)
region('bus_stop', 'sign_back', 0, 10, 10, 16) # back of the sign (rust), reused squashed
region('bus_stop', 'pole', 10, 0, 12, 16)      # pole (white paint, rust)
region('bus_stop', 'base', 12, 0, 16, 6)       # concrete base
region('bus_stop', 'plate', 12, 6, 16, 12)     # timetable plate


@texture('block/bus_stop', cutout=True)
def bus_stop():
    """Rusty round bus-stop sign (バス停) on a pole: faded red ring, white face with unreadable strokes."""
    t = Tex(16)
    R = REG['bus_stop']
    x, y = grid(10, 10)
    d = np.hypot(x - 4.5, y - 4.5)
    for yy in range(10):
        for xx in range(10):
            dd = d[yy, xx]
            if dd > 5.0:
                continue
            if dd > 3.9:
                c = ['#5d1d17', '#7a2a20', '#8f3a2c'][(xx + yy) % 3]
            else:
                c = WHITEPAINT[4] if (xx * 3 + yy) % 7 else WHITEPAINT[3]
            t.set(xx, yy, c, wrap=False)
    # strokes on the face (the stop name), a bus pictogram line
    for (sx, sy) in ((3, 3), (4, 3), (5, 3), (6, 3), (4, 4), (4, 5), (6, 5), (3, 6), (5, 6), (6, 6)):
        t.set(sx, sy, '#2a2a30', wrap=False)
    # rust bleeding over the face
    for (rx, ry, k) in ((7, 6, 0.6), (7, 7, 0.5), (2, 7, 0.4), (8, 3, 0.5)):
        t.blend(rx, ry, RUST[3], k)
    fill_region(t, R['sign_back'], lambda x, y, w, h: RUST[2 + ((x + y) % 3 == 0)] if True else None)
    fill_region(t, R['pole'], lambda x, y, w, h: (WHITEPAINT[[4, 2][x]] if (y * 5 + x) % 7 else RUST[3]))
    for yy in range(0, 16, 4):
        t.set(10, yy, RUST[2])
    fill_region(t, R['base'], lambda x, y, w, h: CONC[[5, 4, 4, 3, 3, 2][y] - ((x + y) % 4 == 0)])
    fill_region(t, R['plate'], lambda x, y, w, h: WHITEPAINT[3] if not (y in (1, 3) and 0 < x < 3) else '#3a3a40')
    t.blend(15, 11, RUST[3], 0.6)
    return t


# ================================================================================================ tunnel lamp
region('tunnel_lamp', 'housing', 0, 0, 10, 4)     # housing long faces (10 x 4)
region('tunnel_lamp', 'housing_end', 10, 0, 14, 4)
region('tunnel_lamp', 'glass', 0, 4, 10, 8)       # lens (dead / lit)
region('tunnel_lamp', 'plate', 0, 8, 8, 14)       # wall plate
region('tunnel_lamp', 'conduit', 10, 4, 12, 16)


def _tunnel_lamp(lit):
    t = Tex(16)
    R = REG['tunnel_lamp']
    HOUSE = ramp(['#16181a', '#24272a', '#33373a', '#43474a', '#53575a'], 5)
    fill_region(t, R['housing'], lambda x, y, w, h: HOUSE[[4, 3, 2, 1][y] - ((x * 7 + y) % 9 == 0)])
    fill_region(t, R['housing_end'], lambda x, y, w, h: HOUSE[2])
    fill_region(t, R['plate'], lambda x, y, w, h: CONC[3 + ((x + y) % 5 == 0)] if 0 < x < w - 1 and 0 < y < h - 1 else HOUSE[1])
    fill_region(t, R['conduit'], lambda x, y, w, h: HOUSE[[3, 1][x]])
    u0, v0, u1, v1 = R['glass']
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            edge = xx in (u0, u1 - 1) or yy in (v0, v1 - 1)
            if lit:
                c = '#ffd27a' if not edge else '#d9822e'
                if 3 <= xx <= 6 and yy in (5, 6):
                    c = '#fff2c8'
            else:
                c = ['#3a3428', '#463e30', '#2f2a22'][(xx + yy) % 3] if not edge else '#1c1a17'
                if 3 <= xx <= 6 and yy == 6:
                    c = '#5a5040'           # dead tube
            t.set(xx, yy, c)
    # dirt, dead insects in the lens
    for (dx, dy) in ((2, 5), (8, 6)):
        t.blend(dx, dy, '#0c0b0a', 0.6)
    for (sx, sy) in ((1, 0), (6, 1), (9, 3)):
        t.blend(sx, sy, RUST[2], 0.6)
    return t


@texture('block/tunnel_lamp', opaque=True)
def tunnel_lamp():
    return _tunnel_lamp(False)


@texture('block/tunnel_lamp_lit', opaque=True)
def tunnel_lamp_lit():
    return _tunnel_lamp(True)


# ================================================================================================ guardrail
region('guardrail', 'rail', 0, 0, 16, 6)       # W-beam panel front (16 x 6)
region('guardrail', 'rail_back', 0, 6, 16, 8)  # rail top/bottom/back strip
region('guardrail', 'post', 0, 8, 2, 16)       # post (2 x 8 used for 12 tall)
region('guardrail', 'post_side', 2, 8, 4, 16)
region('guardrail', 'spacer', 4, 8, 6, 11)


@texture('block/guardrail', opaque=True)
def guardrail():
    """Rusty white road guardrail: W-beam profile, chipped paint, rust running from the bolts."""
    t = Tex(16)
    R = REG['guardrail']
    prof = [4, 5, 3, 1, 4, 2]          # two ridges with a groove
    wn = fnoise(16, 16, 491, beta=1.2)
    fill_region(t, R['rail'], lambda x, y, w, h: WHITEPAINT[int(np.clip(prof[y] + round(wn[y, x] * 0.5), 0, 5))])
    fill_region(t, R['rail_back'], lambda x, y, w, h: WHITEPAINT[2 - y])
    fill_region(t, R['post'], lambda x, y, w, h: GALV[[4, 2][x]] if (x + y) % 6 else RUST[3])
    fill_region(t, R['post_side'], lambda x, y, w, h: GALV[[3, 1][x]])
    fill_region(t, R['spacer'], lambda x, y, w, h: GALV[2])
    # rust: bolts at the ends + streaks running down, a big chipped patch
    for bx in (1, 14):
        t.set(bx, 2, '#141210')
        for k in range(1, 4):
            t.blend(bx, 2 + k, RUST[3], 0.6 - k * 0.12)
    rm = (fnoise(16, 16, 492, beta=1.3) > 0.9) & (grid(16, 16)[1] < 6)
    t.a[rm] = RUST[3]
    for xx in range(16):
        if (xx * 5) % 7 == 0:
            t.blend(xx, 5, RUST[2], 0.6)
    return t


# ================================================================================================ key hook
region('key_hook', 'board', 0, 0, 8, 6)
region('key_hook', 'board_edge', 8, 0, 16, 1)
region('key_hook', 'hook', 8, 1, 10, 3)
region('key_hook', 'key', 10, 1, 13, 8)        # key (cutout): bow, blade, a wooden tag on a string
region('key_hook', 'tag', 13, 1, 16, 6)


def _key_hook(with_key):
    t = Tex(16)
    R = REG['key_hook']
    fill_region(t, R['board'], woodgrain(DARKWOOD, 501, 4.6))
    fill_region(t, R['board_edge'], lambda x, y, w, h: DARKWOOD[3])
    fill_region(t, R['hook'], lambda x, y, w, h: BRASS[[3, 1][x]] if y == 0 else BRASS[2])
    # written label strip on the board (the hut's key), screw holes
    t.set(1, 1, '#0a0807')
    t.set(6, 1, '#0a0807')
    for xx in range(2, 6):
        t.set(xx, 4, PAPER[3])
    t.set(3, 4, INK[0])
    t.set(4, 4, INK[1])
    if with_key:
        u0, v0 = R['key'][:2]
        stamp(t, [".a.", "a.a", ".a.", ".b.", ".b.", ".bc", ".bb"], u0, v0,
              {'a': BRASS[3], 'b': BRASS[2], 'c': BRASS[1]}, wrap=False)
        u0, v0 = R['tag'][:2]
        stamp(t, ["d..", ".d.", "eef", "eef", "fff"], u0, v0, {'d': '#3b3b33', 'e': '#a07c45', 'f': '#7a5c30'}, wrap=False)
    else:
        # a clean silhouette in the grime where the key used to hang
        t.dl(4, 3, 0.05)
        t.dl(4, 2, 0.05)
    return t


@texture('block/key_hook', cutout=True)
def key_hook():
    return _key_hook(True)


@texture('block/key_hook_empty', cutout=True)
def key_hook_empty():
    return _key_hook(False)


# ================================================================================================ car door
region('car_door', 'door', 0, 0, 16, 16)


@texture('block/car_door', opaque=True)
def car_door():
    """White car body panel: dark window (a faint handprint on the inside), handle, seam, mud and dew."""
    t = Tex(16)
    wn = fnoise(16, 16, 511, beta=1.3)
    for yy in range(16):
        for xx in range(16):
            if yy < 6:
                # tinted glass, a cold reflection band of the sky
                c = ['#0d1014', '#11151a', '#161b21'][(xx + yy * 2) % 3]
                if 2 <= (xx - yy) <= 3:
                    c = '#2a323a'
                t.set(xx, yy, c)
            else:
                v = 3.8 + wn[yy, xx] * 0.5 + (0.6 if yy == 7 else 0) - (0.9 if yy == 6 else 0)
                t.set(xx, yy, WHITEPAINT[int(np.clip(round(v), 0, 5))])
    # window frame / rubber seal
    for xx in range(16):
        t.set(xx, 6, '#151617')
    # door seams and handle
    for yy in range(6, 16):
        t.set(0, yy, '#4a4b4a')
    for xx in range(10, 14):
        t.set(xx, 9, '#2a2b2c')
        t.set(xx, 10, '#9a9b98')
    t.set(13, 9, '#1a1b1c')
    # a pale handprint pressed against the glass from inside
    for (hx, hy) in ((5, 2), (6, 1), (7, 1), (8, 1), (6, 2), (7, 2), (8, 2), (6, 3), (7, 3), (9, 2)):
        t.blend(hx, hy, '#9aa3ab', 0.35)
    # mud splashed up from the forest road, dew streaks
    for yy in range(12, 16):
        for xx in range(16):
            if white(16, 16, 512)[yy, xx] < (yy - 11) * 0.22:
                t.blend(xx, yy, '#4a3828', 0.7)
    for (dx, dy) in ((3, 8), (3, 9), (11, 12), (8, 7)):
        t.blend(dx, dy, '#d8dcd8', 0.35)
    return t
