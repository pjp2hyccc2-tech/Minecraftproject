"""Item sprites (16x16, transparent, binary alpha) + door item icons."""
import math

import numpy as np

from common import (Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, dilate, erode, rng,
                    lighten_arr, bresenham, smoothstep)
from t_build import (LACQ, PAPER, IRON, RUST, BLOOD, LAT, FG, draw_map, _door, MOTIF_COLORS)
from t_props import GOLDF, KEYAKI


def outline(t, dark=0.22, color=None, diag=False):
    """Add a 1px outline outside the opaque shape, coloured as a darkened neighbour."""
    m = t.a[..., 3] > 0
    p = np.pad(m, 1)
    ring = (p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:])
    if diag:
        ring |= p[:-2, :-2] | p[:-2, 2:] | p[2:, :-2] | p[2:, 2:]
    ring &= ~m
    src = t.a.copy()
    for (yy, xx) in zip(*np.nonzero(ring)):
        if color is not None:
            t.a[yy, xx] = C(color)
            continue
        acc, n = np.zeros(3), 0
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            y2, x2 = yy + dy, xx + dx
            if 0 <= y2 < 16 and 0 <= x2 < 16 and m[y2, x2]:
                acc += src[y2, x2, :3]
                n += 1
        if n == 0:
            continue
        c = np.append(acc / n * dark, 255)
        t.a[yy, xx] = c
    return t


def sprite(rows, legend):
    t = Tex(16)
    draw_map(t, rows, 0, 0, colors={k: C(v) for k, v in legend.items()}, wrap=False)
    return t


# ---------------------------------------------------------------------------
# lantern
# ---------------------------------------------------------------------------
CHOCHIN = [
    ".......hh.......",
    "......h..h......",
    ".......hh.......",
    "......KkkK......",
    ".....KkkkkK.....",
    ".....LPPPPp.....",
    "....rrrrrrrr....",
    "....LPPMMPPp....",
    "...LPPMPPMPPp...",
    "...LPPMPPMPPp...",
    "...rrrrMMrrrr...",
    "....LPPPPPPp....",
    "....rrrrrrrr....",
    ".....LPPPPp.....",
    ".....KkkkkK.....",
    "......KKKK......",
]


def _chochin(lit):
    if lit:
        lg = {'L': '#fbe9b8', 'P': '#f2c470', 'p': '#d0873a', 'r': '#a8561f', 'M': '#6e2412',
              'K': '#151112', 'k': '#3b3236', 'h': '#3a2a1e'}
    else:
        lg = {'L': '#c4bba3', 'P': '#a59c85', 'p': '#82796a', 'r': '#655d50', 'M': '#5e2019',
              'K': '#141012', 'k': '#3a3135', 'h': '#3a2a1e'}
    t = sprite(CHOCHIN, lg)
    if lit:
        t.set(5, 9, '#fff3d0')
        t.set(5, 8, '#fde9b8')
    else:
        t.set(9, 11, '#6e665a')       # stain on the unlit paper
        t.set(9, 13, '#8a8272')
    return outline(t, 0.3)


@texture('item/chochin', cutout=True)
def chochin():
    return _chochin(False)


@texture('item/chochin_lit', cutout=True)
def chochin_lit():
    return _chochin(True)


# ---------------------------------------------------------------------------
# simple sprites
# ---------------------------------------------------------------------------
@texture('item/lamp_oil', cutout=True)
def lamp_oil():
    rows = [
        "................",
        "......cccc......",
        "......cCCc......",
        ".......gg.......",
        ".......gG.......",
        "......gGGg......",
        ".....gHYYYg.....",
        "....gHYYYYYg....",
        "....gHwwwwYg....",
        "....gHwRRwYg....",
        "....gHwwwwYg....",
        "....gHYYYYYg....",
        "....gyyyyyyg....",
        ".....gyyyyg.....",
        "................",
        "................",
    ]
    lg = {'c': '#4a3322', 'C': '#6b4c33', 'g': '#5c6a6c', 'G': '#8fa0a2', 'H': '#c9d4cf',
          'Y': '#9c7f2a', 'y': '#6e5718', 'w': '#c8bfa6', 'R': '#6a2219'}
    t = sprite(rows, lg)
    t.set(7, 6, '#c2a24a')
    t.set(6, 7, '#b6963e')
    return outline(t, 0.3)


def bell(t, cx, cy, r, P, slit='#120c05', cord='#7a1f17', cord2='#541510', with_cord=True):
    x, y = grid(16, 16)
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    m = d <= r
    hl = np.sqrt((x - (cx - r * 0.4)) ** 2 + (y - (cy - r * 0.45)) ** 2)
    v = (len(P) - 1) - hl * (len(P) - 1) / (r * 1.75)
    idx = np.clip(np.round(v), 0, len(P) - 1).astype(int)
    t.a[m] = np.asarray(P)[idx][m]
    ey = int(round(cy))
    for xx in range(16):
        if m[ey, xx]:
            t.set(xx, ey, P[1])
    for xx in range(int(cx - r * 0.55), int(cx + r * 0.55) + 1):
        t.set(xx, int(cy + r * 0.5), slit)
    t.set(int(round(cx)), int(cy + r * 0.5) + 1, slit)
    if with_cord:
        top = int(round(cy - r))
        t.set(int(round(cx)), top - 1, P[1])
        for (dx, dy) in ((-1, -2), (1, -2), (-1, -3), (1, -3), (0, -4)):
            t.set(int(round(cx)) + dx, top + dy, cord if dy > -4 else cord2)
    return t


@texture('item/suzu', cutout=True)
def suzu():
    P = ramp(['#4a3412', '#6f5220', '#977532', '#ba9849', '#d6b864', '#ecd896'], 6)
    t = bell(Tex(16), 7.5, 9.0, 4.6, P)
    # tassel of the red cord trailing to the side
    for (px_, py_) in ((10, 4), (11, 5), (12, 6), (12, 7), (13, 8)):
        t.set(px_, py_, '#7a1f17')
    t.set(13, 9, '#541510')
    t.set(12, 9, '#7a1f17')
    t.set(5, 6, '#f6ebc4')
    return outline(t, 0.32)


@texture('item/shio', cutout=True)
def shio():
    rows = [
        "................",
        "................",
        "................",
        "....tttttttt....",
        "....WWWWWWWW....",
        "....PPPPPPPp....",
        "....PRRPPPPp....",
        "....PRRPPKKp....",
        "....PPPPPKKp....",
        "....PRRPPPPp....",
        "....PRRPPKKp....",
        "....PPPPPKKp....",
        "....PPPPPPp.s...",
        "....pppppp...s..",
        "..........s.....",
        "................",
    ]
    lg = {'t': '#9b927e', 'W': '#d6cfbd', 'P': '#c3bba7', 'p': '#9d9581', 'R': '#7a2a1e', 'K': '#2a2420',
          's': '#d8d4c8'}
    return outline(sprite(rows, lg), 0.3)


@texture('item/ofuda', cutout=True)
def ofuda():
    rows = [
        "................",
        "......LPPPp.....",
        "......RRRRp.....",
        "......RrRRp.....",
        "......RRRRp.....",
        "......LPPPp.....",
        "......LKKPp.....",
        "......LPKPp.....",
        "......LKKKp.....",
        "......LPPPp.....",
        "......LKPKp.....",
        "......LPKPp.....",
        "......LKKPp.....",
        "......LPPKp.....",
        "......LPPPp.....",
        "......dpdpd.....",
    ]
    lg = {'L': '#d4ccb6', 'P': '#c2b9a2', 'p': '#9f9781', 'R': '#7d2419', 'r': '#a5442c', 'K': '#1e1a17',
          'd': '#8a816c'}
    t = sprite(rows, lg)
    t.set(9, 9, '#a49a82')           # foxing
    return outline(t, 0.3)


@texture('item/ohajiki', cutout=True)
def ohajiki():
    t = Tex(16)
    x, y = grid(16, 16)
    discs = ((5.0, 6.0, 3.1, ('#5e3a46', '#8a5a68', '#b48a94', '#d8b8bc')),
             (10.5, 8.0, 3.1, ('#2f4a52', '#4f7480', '#7aa3ac', '#b6d2d4')),
             (6.0, 11.5, 2.6, ('#5a4314', '#8a6a24', '#b8943e', '#dcc27a')))
    for (cx, cy, r, cols) in discs:
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        m = d <= r
        t.a[m] = C(cols[1])
        t.a[m & (d > r - 1.0) & (y > cy)] = C(cols[0])
        t.a[m & (d < r - 1.2)] = C(cols[2])
        t.set(int(cx - 1), int(cy - 1), cols[3])
        t.set(int(cx), int(cy - 1), cols[2])
    return outline(t, 0.35)


@texture('item/kura_key', cutout=True)
def kura_key():
    rows = [
        "................",
        "..iIIi..........",
        ".iI..Ii.........",
        ".I....d.........",
        ".I....d.........",
        ".iI..ddi........",
        "..iddiIIi.......",
        "......iIIi......",
        ".......iIIi.....",
        "........iIIi....",
        ".........iIIi...",
        "..........iIId..",
        "......dIi..iIId.",
        "......iIIiiIdd..",
        ".......ddi.dd...",
        "................",
    ]
    lg = {'I': '#6f675d', 'i': '#45403a', 'd': '#26231f'}
    t = sprite(rows, lg)
    t.set(8, 8, RUST[1])
    t.set(2, 4, RUST[2])
    for (px_, py_) in ((1, 6), (1, 7), (2, 8), (2, 9)):
        t.set(px_, py_, '#7a1f17')
    for (px_, py_) in ((1, 10), (2, 10), (3, 10), (1, 11), (2, 11), (3, 11), (2, 12)):
        t.set(px_, py_, '#5e1611' if (px_ + py_) % 2 else '#8e2a1d')
    return outline(t, 0.4)


@texture('item/doll_head', cutout=True)
def doll_head():
    t = Tex(16)
    x, y = grid(16, 16)
    F = ramp(['#9a9180', '#b9b09e', '#d1c9b7', '#e0d9c8'], 4)
    head = ((x - 7.5) / 5.6) ** 2 + ((y - 7.2) / 5.8) ** 2 <= 1
    face = ((x - 7.5) / 3.8) ** 2 + ((y - 8.6) / 3.8) ** 2 <= 1
    t.a[head] = C('#0d0b0d')
    v = 3.2 - np.sqrt((x - 6.5) ** 2 + (y - 7.5) ** 2) * 0.45
    fi = np.clip(np.round(v), 0, 3).astype(int)
    t.a[face] = F[fi][face]
    for xx in range(3, 13):
        t.set(xx, 5, '#0d0b0d')           # straight bangs
    for xx in range(5, 10):
        t.set(xx, 2, '#2c2a33')           # hair shine
    t.set(6, 7, '#050405')
    t.set(9, 7, '#050405')
    t.set(6, 8, '#1a1818')
    t.set(9, 8, '#1a1818')
    t.set(7, 10, '#7a1b16')
    t.set(8, 10, '#7a1b16')
    # crack from the eye
    for (cx, cy) in ((10, 8), (10, 9), (11, 10)):
        t.set(cx, cy, '#6e665a')
    # neck peg
    for (px_, py_) in ((7, 13), (8, 13), (7, 14), (8, 14)):
        t.set(px_, py_, '#6b5038' if py_ == 13 else '#4a3626')
    return outline(t, 0.3)


@texture('item/matches', cutout=True)
def matches():
    rows = [
        "................",
        "................",
        "..........h.....",
        ".........hs.h...",
        "........hs.hs...",
        ".......hs.hs....",
        "......s..hs.....",
        "..yyyyyyyys.....",
        "..yRRRRRRRRy....",
        "..yRYkkYYYRy....",
        "..yRYkkYccRy....",
        "..yRRRRRRRRy....",
        "..yyyyyyyyyy....",
        "..bbbbbbbbbb....",
        "................",
        "................",
    ]
    lg = {'h': '#7a2416', 's': '#b59a6c', 'y': '#a88f52', 'R': '#7c261b', 'Y': '#c2a85e', 'k': '#1f1a16',
          'c': '#3a5a6a', 'b': '#4a3524'}
    return outline(sprite(rows, lg), 0.3)


@texture('item/note', cutout=True)
def note():
    rows = [
        "................",
        "...LPPPPPPPd....",
        "...LPKPKPPPdd...",
        "...LPKPKPKPPdd..",
        "...LPKPPPKPPPp..",
        "...LPKPKPKPKPp..",
        "...LPPPKPKPKPp..",
        "...LPKPKPPPKPp..",
        "...LPKPKPKPPPp..",
        "...LPKPPPKPKPp..",
        "...LPPPKPKPKPp..",
        "...LPKPKPPPPPp..",
        "...LPPPPPPPPPp..",
        "...LPPPPPPPpp...",
        "...pppppppp.....",
        "................",
    ]
    lg = {'L': '#cfc6ae', 'P': '#bab199', 'p': '#958c77', 'd': '#a39a84', 'K': '#3c3530'}
    t = sprite(rows, lg)
    for (sx, sy) in ((9, 9), (10, 10), (9, 10), (10, 11)):   # a dried brown-red stain
        t.set(sx, sy, '#5a2a1e')
    t.set(11, 10, '#7a4a38')
    return outline(t, 0.3)


@texture('item/red_envelope', cutout=True)
def red_envelope():
    rows = [
        "................",
        "....RRRRRRRR....",
        "....RrrrrrrRd...",
        "....RrrWWrrRd...",
        "....RrrWKrrRd...",
        "....RrrWWrrRd...",
        "....RrrWKrrRd...",
        "....RrrWKrrRd...",
        "....RrrWWrrRd...",
        "....RrrWKrrRd...",
        "....RrrWWrrRd...",
        "....RrrrrrgRd...",
        "....RrrrrggRd...",
        "....RRRRRRRRd...",
        ".....ddddddd....",
        "................",
    ]
    lg = {'R': '#6a1611', 'r': '#7e1d15', 'W': '#cfc5aa', 'K': '#1a1412', 'g': '#a8893e', 'd': '#3e0c09'}
    t = sprite(rows, lg)
    t.set(5, 2, '#952a1d')
    t.set(6, 2, '#8c2619')
    return outline(t, 0.35)


@texture('item/omamori', cutout=True)
def omamori():
    rows = [
        "......w..w......",
        ".......ww.......",
        "......wwww......",
        ".....PPPPPP.....",
        "....PPgPPgPPp...",
        "....PPGGGGPPp...",
        "....PgGkkGgPp...",
        "....PPGkkGPPp...",
        "....PPGkkGPPp...",
        "....PgGGGGgPp...",
        "....PPPPPPPPp...",
        "....PgPPPPgPp...",
        "....PPPPPPPPp...",
        "....pppppppp....",
        "................",
        "................",
    ]
    lg = {'w': '#c9bfa9', 'P': '#4a2a52', 'p': '#321b38', 'g': '#a8893e', 'G': '#8a7034', 'k': '#24122a'}
    t = sprite(rows, lg)
    t.set(5, 4, '#5e3a68')
    t.set(5, 5, '#5e3a68')
    return outline(t, 0.35)


@texture('item/ash', cutout=True)
def ash():
    t = Tex(16)
    x, y = grid(16, 16)
    A = ramp(['#3a3836', '#4f4c49', '#67635f', '#7f7b75', '#95918a'], 5)
    m = (((x - 7.5) / 6.2) ** 2 + ((y - 13.0) / 4.2) ** 2 <= 1) & (y <= 13)
    v = 3.2 - (13 - y) * -0.15 - np.abs(x - 6.5) * 0.25 + (white(16, 16, 1001) - 0.5) * 1.6
    v = 1.8 + (white(16, 16, 1001) - 0.5) * 1.8 - (x - 7.5) * 0.12 - (y - 11) * 0.25
    t.a[m] = A[np.clip(np.round(v), 0, 4).astype(int)][m]
    # charred flakes + a scrap of the red envelope + one dying ember
    for (fx, fy) in ((5, 11), (9, 10), (11, 12), (3, 12)):
        t.set(fx, fy, '#1c1a19')
    for (fx, fy) in ((8, 9), (9, 9), (8, 8)):
        t.set(fx, fy, '#5e1813')
    t.set(9, 8, '#7a2318')
    t.set(6, 12, '#a0461e')
    # flakes drifting above
    t.set(4, 6, A[3])
    t.set(11, 5, A[2])
    return outline(t, 0.45)


# ---------------------------------------------------------------------------
# door items: whole door as a narrow 16x16 sprite
# ---------------------------------------------------------------------------
def _door_icon(rows, legend):
    return outline(sprite(rows, legend), 0.4)


@texture('item/fusuma_door', cutout=True)
def fusuma_door_item():
    rows = [
        "....KKKKKKKK....",
        "....KbgGgffK....",
        "....KfbgGgfK....",
        "....KffbfffK....",
        "....KfffffrK....",
        "....KfffffwK....",
        "....KffffkfK....",
        "....KfffwwfK....",
        "....KffwwwhK....",
        "....KfkwwwfK....",
        "....KffkfkfK....",
        "....KffkfkfK....",
        "....KffffffK....",
        "....KssssssK....",
        "....KssssssK....",
        "....KKKKKKKK....",
    ]
    lg = {'K': '#16121a', 'b': '#2a2018', 'g': '#2b3a2b', 'G': '#42553c', 'f': '#8c7a50', 'w': '#bdb5a0',
          'h': '#0b0908', 'o': '#6d5832', 's': '#6c5c3c', 'k': '#141212', 'r': '#6e2219'}
    t = sprite(rows, lg)
    for (px_, py_) in ((6, 5), (9, 12), (6, 3)):
        t.set(px_, py_, '#9a8758')
    return _door_icon_finish(t)


def _door_icon_finish(t):
    return outline(t, 0.45)


@texture('item/shoji_door', cutout=True)
def shoji_door_item():
    rows = [
        "....FFFFFFFF....",
        "....FPPLPPPF....",
        "....FLLLLLLF....",
        "....FPPLPPPF....",
        "....FLLLLLLF....",
        "....FPPLPPPF....",
        "....FLLLLLLF....",
        "....FPPLPPPF....",
        "....FLLLLLLF....",
        "....FPPLPPPF....",
        "....FLLLLLLF....",
        "....FwwwwwwF....",
        "....FWWWWWWF....",
        "....FwwwwwwF....",
        "....FWWWWWWF....",
        "....FFFFFFFF....",
    ]
    lg = {'F': '#3a2e26', 'L': '#2a211b', 'P': '#c8bfa6', 'w': '#4a3a2e', 'W': '#3b2f26'}
    t = sprite(rows, lg)
    t.set(6, 3, '#b1a890')
    t.set(9, 7, '#b6ad95')
    return _door_icon_finish(t)


@texture('item/oshiire_door', cutout=True)
def oshiire_door_item():
    rows = [
        "....FFFFFFFF....",
        "....FhhhhhhF....",
        "....FssssssF....",
        "....F......F....",
        "....FhhhhhhF....",
        "....FssssssF....",
        "....F......F....",
        "....FFFFFFFF....",
        "....FhhhhhhF....",
        "....FssssssF....",
        "....F......F....",
        "....FhhhhhhF....",
        "....FssssssF....",
        "....F......F....",
        "....FhhhhhhF....",
        "....FFFFFFFF....",
    ]
    lg = {'F': '#1b1511', 'h': '#45372d', 's': '#28201a'}
    t = sprite(rows, lg)
    t.set(10, 8, '#0e0b09')
    return _door_icon_finish(t)


@texture('item/kura_door', cutout=True)
def kura_door_item():
    rows = [
        "....kkkkkkkk....",
        "....kbbcbbck....",
        "....iIiIiIii....",
        "....kbbcbbck....",
        "....kbbcbbck....",
        "....kbbcbbck....",
        "....kbbcbbck....",
        "....kbLLLLck....",
        "....kbL00Lck....",
        "....kbL0LLck....",
        "....kbLLLLck....",
        "....kbbcbbck....",
        "....kbbcbbck....",
        "....iIiIiIii....",
        "....kbbcbbck....",
        "....kkkkkkkk....",
    ]
    lg = {'k': '#0c0a09', 'b': '#231c18', 'c': '#120f0d', 'i': '#3b3631', 'I': '#6b6255',
          'L': '#5d554c', '0': '#020202'}
    t = sprite(rows, lg)
    t.blend(6, 11, RUST[2], 0.6)
    t.blend(9, 12, RUST[1], 0.5)
    return _door_icon_finish(t)
