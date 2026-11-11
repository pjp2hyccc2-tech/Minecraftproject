#!/usr/bin/env python3
"""Art for the canal town gimmicks: hanging shop signs (16 painted trade pictures on iron-framed boards, the
wrought-iron bracket), bunting (pennants, flags, paper lanterns, laundry, garland leaves and flowers, cord, pegs, iron
eyes, the lantern halo), the floating candle lantern (paper side, wooden float), the item icons, and the block
models, blockstates, loot tables and tags.

    python3 tools/art/canal_gimmicks.py      # writes assets + data, and tools/art/canal_gimmicks_preview.png

Everything is drawn here, pixel by pixel or from small vector shapes (no fonts, no external images, no vanilla
textures). Cloth sprites are greyscale: the game tints them with the string's dye colours. Sprites live in the block
atlas (sizes are multiples of 16 so the atlas keeps its mipmaps).
"""
from __future__ import annotations

import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from px import Canvas, Ramp, bevel, ellipse_mask, hexc, line_mask, mix, poly_mask, seed_of, shift  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "eternalrealm"

OUTLINE = hexc("#1b130d")
CLEAR = (0, 0, 0, 0)


def rng(name: str) -> random.Random:
    return random.Random(seed_of("canal:" + name))


# =========================================================================== ramps (dark -> light)
STEEL = Ramp("#262a33", "#3f4552", "#5e6676", "#848d9e", "#adb6c5", "#d6dde8", "#f4f8fc")
IRON = Ramp("#17181c", "#25272d", "#363940", "#4b4f58", "#666b75", "#8a909a")
GOLD = Ramp("#4a2c08", "#7a4f12", "#a8741f", "#d29e33", "#efc55a", "#fde58f", "#fff6cf")
WOOD = Ramp("#2b170a", "#47280f", "#663b18", "#855225", "#a36a35", "#c18749", "#d9a868")
DARKWOOD = Ramp("#1c0f07", "#2f1a0c", "#432612", "#58331a", "#6f4223")
LEATHER = Ramp("#2a0b0b", "#4a1212", "#6e1d1b", "#922b25", "#b33f33", "#cf5e4c")
BROWN_LEATHER = Ramp("#24130a", "#3d2212", "#5a331b", "#774626", "#955c33")
PARCH = Ramp("#5a4426", "#86693d", "#b09160", "#d0b585", "#e8d6aa", "#f7ecd2")
CRUST = Ramp("#3b1c08", "#64330f", "#8f4f19", "#b56f27", "#d4913b", "#ecb562", "#f7d69a")
GLASS = Ramp("#203a48", "#35617a", "#5591ad", "#88c3d9", "#bfe6f2", "#effbff")
POTION = Ramp("#3a0627", "#5f0c3f", "#8c1858", "#b82a72", "#de4f93", "#f68dbd")
CORK = Ramp("#3c2611", "#5e3d1d", "#80582d", "#a37643", "#c3975f")
CLOTH = Ramp("#40505e", "#6d8396", "#9db3c6", "#c9d9e6", "#eaf2f8", "#ffffff")
LEAF = Ramp("#16310f", "#244d17", "#356b20", "#4b8a2b", "#67a83a", "#8cc651")
FISH_BACK = Ramp("#10303a", "#1c4b57", "#2b6b77", "#3f8d96")
FISH_SIDE = Ramp("#4a5866", "#6f8190", "#97a9b6", "#c3d1da", "#e6eef2", "#ffffff")
GEM = Ramp("#06282e", "#0b4a52", "#11717a", "#1c9ea3", "#37c9c4", "#7ae9df", "#d2fff8")
RUBY = Ramp("#3a0410", "#62081b", "#8f1027", "#c01d36", "#e9465a", "#ff8f9a")
FOAM = Ramp("#a69d86", "#cfc7b0", "#ebe5d2", "#fbf8ee")
ALE = Ramp("#5a2a05", "#8a4709", "#b86a12", "#de9123")
SKY = Ramp("#1f4e78", "#2f6fa3", "#4f93c6")
SEA = Ramp("#1d4f6e", "#2f6f8f", "#4b8fae", "#78b3cb")
GRASS = Ramp("#2f5a24", "#467a31", "#5f9a3f", "#83b85a")

# a deep painted ground for each trade's board (the picture is bright on it, so it reads from across the street)
PANEL = {
    "sword": "#6a1d1c", "shield": "#5a1f2e", "potion": "#1f4b4a", "bread": "#4a2140", "anvil": "#1f3a4c",
    "book": "#233a5c", "gem": "#3a2156", "flower": "#24452a", "shirt": "#5a2b1c", "key": "#1b2b44",
    "fish": "#16304a", "mug": "#24452f", "scroll": "#5c2026", "map": "#244a43", "hammer": "#2e3946",
    "ring": "#41204f",
}


# =========================================================================== small drawing helpers
def poly(w, h, pts):
    return poly_mask(w, h, pts)


def thick_line(w, h, x0, y0, x1, y1, half):
    """Mask of a straight bar of half-width ``half`` (pixels) between two points (pixel centres)."""
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / length * half, dx / length * half
    return poly_mask(w, h, [(x0 + nx + 0.5, y0 + ny + 0.5), (x1 + nx + 0.5, y1 + ny + 0.5),
                            (x1 - nx + 0.5, y1 - ny + 0.5), (x0 - nx + 0.5, y0 - ny + 0.5)])


def disc(w, h, cx, cy, r, ry=None):
    return ellipse_mask(w, h, cx, cy, r, ry)


def fill(c: Canvas, m, color):
    c.a[m] = color


def put(c: Canvas, x, y, color):
    c.put(int(x), int(y), color)


def shade_rows(c: Canvas, m, ramp: Ramp, top: int, bottom: int):
    """Vertical gradient inside a mask from ramp[top] (first row) to ramp[bottom] (last row)."""
    ys, xs = np.nonzero(m)
    if len(ys) == 0:
        return
    y0, y1 = ys.min(), ys.max()
    for x, y in zip(xs.tolist(), ys.tolist()):
        f = 0.0 if y1 == y0 else (y - y0) / (y1 - y0)
        c.put(x, y, ramp[int(round(top + (bottom - top) * f))])


def edge(m, dx, dy):
    """Pixels of m whose neighbour at (dx, dy) is outside m."""
    return m & ~shift(m, -dx, -dy)


def outlined(c: Canvas, color=OUTLINE) -> Canvas:
    return c.outline(color=color)


def drop_shadow(icon: Canvas, alpha=110, dx=1, dy=1) -> Canvas:
    """The icon over a soft dark copy of itself (a painted shadow)."""
    out = Canvas(icon.w, icon.h)
    m = icon.mask()
    sm = shift(m, dx, dy) & ~m
    out.a[sm] = (12, 8, 6, alpha)
    out.paste(icon)
    return out


# =========================================================================== shop sign pictures (32 x 32)
N = 32


def icon_sword() -> Canvas:
    c = Canvas(N)
    # blade from the guard (lower left) to the tip (upper right), a fuller down the middle
    blade = thick_line(N, N, 9.5, 21.5, 25.5, 5.5, 1.9) | poly(N, N, [(25.0, 4.0), (29.5, 1.5), (27.0, 7.0)])
    bevel(c, blade, STEEL, base=4, grad=0.6, strength=1.6)
    fuller = line_mask(N, N, 11, 20, 24, 7)
    for y, x in zip(*np.nonzero(fuller)):
        c.put(x, y, STEEL[2])
    for y, x in zip(*np.nonzero(line_mask(N, N, 11, 19, 24, 6) & blade & ~fuller)):
        c.put(x, y, STEEL[6])
    # guard: a gilded bar across the blade with curled ends
    guard = thick_line(N, N, 5.0, 17.0, 13.5, 25.5, 1.4)
    guard |= disc(N, N, 5.0, 16.5, 1.6) | disc(N, N, 13.8, 25.8, 1.6)
    bevel(c, guard, GOLD, base=4, strength=1.4)
    c.put(9, 21, GOLD[6])
    c.put(10, 22, GOLD[5])
    # grip wound with leather, a gold pommel
    grip = thick_line(N, N, 8.0, 23.0, 3.6, 27.4, 1.25)
    bevel(c, grip, BROWN_LEATHER, base=3, strength=1.2)
    for k in range(4):
        x, y = 7 - k, 24 + k
        c.put(x, y, BROWN_LEATHER[1])
    pommel = disc(N, N, 2.6, 28.6, 2.0)
    bevel(c, pommel, GOLD, base=4, strength=1.6)
    c.put(2, 28, GOLD[6])
    # the glint at the tip
    c.put(27, 3, STEEL[6])
    c.put(28, 2, (255, 255, 255, 255))
    return outlined(c)


def icon_shield() -> Canvas:
    c = Canvas(N)
    outer = poly(N, N, [(5, 3), (16, 1.5), (27, 3), (27, 13), (24.5, 21), (16, 30), (7.5, 21), (5, 13)])
    inner = poly(N, N, [(7.5, 5.2), (16, 4.0), (24.5, 5.2), (24.5, 13), (22.3, 19.8), (16, 27), (9.7, 19.8),
                        (7.5, 13)])
    bevel(c, outer & ~inner, GOLD, base=4, strength=1.4)
    blue = Ramp("#0d1f4f", "#173279", "#2348a6", "#3866c9", "#5a8ae0")
    bevel(c, inner, blue, base=2, grad=0.8, strength=1.0, hi_edge=False)
    # quartered: the lower right and upper left quarters a lighter blue
    for y in range(N):
        for x in range(N):
            if inner[y, x] and ((x >= 16) == (y >= 14)):
                r, g, b, a = c.get(x, y)
                c.put(x, y, (min(255, r + 16), min(255, g + 24), min(255, b + 34), a))
    # a silver cross with a gold boss
    cross = (thick_line(N, N, 16, 6, 16, 24.5, 1.4) | thick_line(N, N, 9.5, 13, 22.5, 13, 1.4)) & inner
    bevel(c, cross, STEEL, base=5, strength=1.0)
    boss = disc(N, N, 16.0, 13.0, 2.4)
    bevel(c, boss, GOLD, base=4, strength=1.6)
    c.put(15, 12, GOLD[6])
    for x, y in ((6, 4), (25, 4), (16, 2)):
        c.put(x, y, GOLD[6])
    return outlined(c)


def icon_potion() -> Canvas:
    c = Canvas(N)
    body = disc(N, N, 16.0, 20.0, 9.0, 8.6)
    neck = poly(N, N, [(13.5, 5.5), (18.5, 5.5), (18.5, 13.0), (13.5, 13.0)])
    lip = poly(N, N, [(12.5, 4.5), (19.5, 4.5), (19.5, 6.5), (12.5, 6.5)])
    glass = body | neck | lip
    bevel(c, glass, GLASS, base=3, grad=0.5, strength=1.3)
    # the potion: liquid in the lower part of the bulb with a lighter surface line
    liquid = body & poly(N, N, [(0, 16.5), (32, 16.0), (32, 32), (0, 32)])
    shade_rows(c, liquid, POTION, 4, 1)
    for y, x in zip(*np.nonzero(edge(liquid, 0, -1))):
        c.put(x, y, POTION[5])
    for y, x in zip(*np.nonzero(liquid & edge(body, 1, 0))):
        c.put(x, y, POTION[1])
    # bubbles
    for (x, y, r) in ((13, 23, 1), (18, 20, 1), (16, 26, 0), (20, 24, 0), (12, 19, 0)):
        if r:
            c.put(x, y, POTION[5])
            c.put(x + 1, y, POTION[4])
        else:
            c.put(x, y, POTION[5])
    # glass highlights
    for (x, y) in ((10, 16), (10, 17), (11, 15), (9, 19), (9, 20), (14, 7), (14, 8), (14, 9)):
        c.put(x, y, GLASS[5])
    # cork
    cork = poly(N, N, [(13.5, 1.0), (18.5, 1.0), (18.0, 4.5), (14.0, 4.5)])
    bevel(c, cork, CORK, base=2, strength=1.2)
    c.put(15, 2, CORK[4])
    return outlined(c)


def icon_bread() -> Canvas:
    c = Canvas(N)
    # a wheat ear behind, then a crusty loaf scored three times, flour dusted on top
    for y, x in zip(*np.nonzero(thick_line(N, N, 25.5, 2.5, 18.0, 12.0, 0.5))):
        c.put(x, y, GOLD[2])
    for k, (x, y) in enumerate(((25.0, 3.0), (23.3, 5.2), (21.6, 7.4))):
        grain = disc(N, N, x + 1.5, y + 0.4, 1.3, 0.8) | disc(N, N, x - 0.6, y - 1.0, 0.8, 1.3)
        bevel(c, grain, GOLD, base=4, strength=1.2)
    loaf = disc(N, N, 15.5, 19.5, 13.0, 7.6) & poly(N, N, [(0, 0), (32, 0), (32, 25.5), (0, 25.5)])
    loaf |= poly(N, N, [(4.5, 22), (26.5, 22), (25.5, 26.0), (5.5, 26.0)])
    bevel(c, loaf, CRUST, base=4, grad=0.9, strength=1.5)
    # a darker baked bottom
    for y, x in zip(*np.nonzero(loaf & poly(N, N, [(0, 24.5), (32, 24.5), (32, 32), (0, 32)]))):
        c.put(x, y, CRUST[2])
    # three slashes, each with the pale open crumb and a dark lip below
    for k in range(3):
        x0 = 7.5 + k * 6.0
        slash = thick_line(N, N, x0, 20.0, x0 + 4.5, 14.5, 0.55) & loaf
        for y, x in zip(*np.nonzero(slash)):
            c.put(x, y, hexc("#f3d9a4"))
        for y, x in zip(*np.nonzero(shift(slash, 1, 1) & loaf & ~slash)):
            c.put(x, y, CRUST[1])
    for (x, y) in ((10, 14), (16, 13), (21, 14), (13, 16), (24, 17), (7, 18)):
        c.put(x, y, hexc("#fbf1dc"))
    return outlined(c)


def icon_anvil() -> Canvas:
    c = Canvas(N)
    top = poly(N, N, [(2, 10), (8, 9.5), (27, 9.5), (28.5, 10.5), (28.5, 13.5), (11, 13.5), (6, 13.0), (2.5, 11.5)])
    waist = poly(N, N, [(11, 13.5), (24, 13.5), (21.5, 20.5), (13.5, 20.5)])
    base = poly(N, N, [(9, 20.5), (26, 20.5), (28, 24.5), (7, 24.5)])
    feet = poly(N, N, [(6, 24.5), (29, 24.5), (29, 26.5), (6, 26.5)])
    body = top | waist | base | feet
    bevel(c, body, STEEL, base=2, grad=0.6, strength=1.3, hi_edge=False)
    # the working face catches the light
    face = top & ~shift(top, 0, -1) & ~edge(top, 0, 1)
    for y, x in zip(*np.nonzero(edge(top, 0, -1))):
        c.put(x, y, STEEL[6])
    for x in range(9, 28):
        c.put(x, 11, STEEL[4])
    del face
    # the hardy hole and a pritchel hole
    c.put(23, 10, STEEL[0])
    c.put(25, 10, STEEL[0])
    # sparks flying off the face
    spark = hexc("#ffd36a")
    hot = hexc("#ff8a2a")
    for (x, y, col) in ((14, 5, spark), (12, 3, hot), (17, 4, spark), (19, 2, hot), (10, 6, spark), (21, 6, hot),
                        (15, 1, spark), (8, 4, hot)):
        c.put(x, y, col)
    c = outlined(c)
    # glowing sparks get no outline: put them back on top bright
    for (x, y) in ((14, 5), (17, 4), (10, 6), (15, 1)):
        c.put(x, y, hexc("#fff3c4"))
    return c


def icon_book() -> Canvas:
    c = Canvas(N)
    # an open book seen from a little above: two page blocks fanning from the spine, the red cover behind
    cover = poly(N, N, [(1.5, 9), (16, 12), (30.5, 9), (30.5, 25), (16, 28.5), (1.5, 25)])
    bevel(c, cover, LEATHER, base=2, strength=1.0, hi_edge=False)
    left = poly(N, N, [(3, 7.5), (15.2, 10.5), (15.2, 26), (3, 23)])
    right = poly(N, N, [(16.8, 10.5), (29, 7.5), (29, 23), (16.8, 26)])
    bevel(c, left, PARCH, base=4, strength=0.8, hi_edge=False)
    bevel(c, right, PARCH, base=4, strength=0.8, hi_edge=False)
    # text lines follow the page curve
    ink = hexc("#5b4a33")
    for k in range(6):
        y = 11 + k * 2.3
        for x in range(5, 14):
            yy = int(round(y + (x - 5) * 0.25))
            if (x + k) % 7 != 3 and left[yy, x]:
                c.put(x, yy, ink)
        for x in range(18, 28):
            yy = int(round(y + (27 - x) * 0.25))
            if (x + k) % 6 != 1 and right[yy, x]:
                c.put(x, yy, ink)
    # a red initial and a gilt edge on the cover corners
    c.put(5, 10, hexc("#b0261e"))
    c.put(6, 10, hexc("#b0261e"))
    c.put(5, 11, hexc("#b0261e"))
    for y in range(11, 27):
        c.put(16, y, PARCH[0])
    for (x, y) in ((2, 24), (29, 24), (2, 10), (29, 10)):
        c.put(x, y, GOLD[5])
    # a ribbon marker hanging out
    for y in range(26, 31):
        c.put(18, y, hexc("#d23b2c"))
        c.put(19, y, hexc("#9c1f1a"))
    return outlined(c)


def icon_gem() -> Canvas:
    c = Canvas(N)
    # a brilliant-cut gem: table, crown facets and the pavilion running down to the point
    crown = poly(N, N, [(4, 12), (10, 5), (22, 5), (28, 12)])
    pavilion = poly(N, N, [(4, 12), (28, 12), (16, 29)])
    gem = crown | pavilion
    fill(c, gem, GEM[3])
    facets = [
        (poly(N, N, [(10, 5), (22, 5), (19, 9), (13, 9)]), GEM[6]),     # table
        (poly(N, N, [(4, 12), (10, 5), (13, 9), (9, 12)]), GEM[5]),
        (poly(N, N, [(22, 5), (28, 12), (23, 12), (19, 9)]), GEM[3]),
        (poly(N, N, [(13, 9), (19, 9), (23, 12), (9, 12)]), GEM[4]),
        (poly(N, N, [(4, 12), (9, 12), (16, 29)]), GEM[4]),
        (poly(N, N, [(9, 12), (16, 12), (16, 29)]), GEM[3]),
        (poly(N, N, [(16, 12), (23, 12), (16, 29)]), GEM[2]),
        (poly(N, N, [(23, 12), (28, 12), (16, 29)]), GEM[1]),
    ]
    for m, col in facets:
        fill(c, m & gem, col)
    for y, x in zip(*np.nonzero(line_mask(N, N, 4, 12, 28, 12) & gem)):
        c.put(x, y, GEM[6])
    for (x, y) in ((12, 6), (13, 6), (11, 7)):
        c.put(x, y, (255, 255, 255, 255))
    c = outlined(c)
    # sparkles
    star = hexc("#f2fffb")
    for (x, y) in ((26, 3), (27, 2), (28, 3), (27, 4), (27, 3), (5, 21), (4, 20), (6, 20), (5, 19), (5, 20)):
        c.put(x, y, star)
    return c


def icon_flower() -> Canvas:
    c = Canvas(N)
    stem = Ramp("#1c3a12", "#2c5a1c", "#3f7a28", "#57993a")
    for (x0, y0, x1, y1) in ((16, 29, 16, 12), (16, 27, 8.5, 12), (16, 27, 23.5, 11)):
        for y, x in zip(*np.nonzero(thick_line(N, N, x0, y0, x1, y1, 0.55))):
            c.put(x, y, stem[2])
    for leaf in (poly(N, N, [(15, 22), (8, 18.5), (5, 20.5), (11, 24)]),
                 poly(N, N, [(17, 21), (24, 17.5), (27, 19.5), (21, 23)])):
        bevel(c, leaf, LEAF, base=3, strength=1.3)
    # three roses: a round bloom, the petals curling in a spiral
    blooms = ((8.5, 10.5, 4.6, Ramp("#4f0c2c", "#7f1748", "#b52c6c", "#df5594", "#f79ac3", "#ffd2e6")),
              (23.5, 9.5, 4.6, Ramp("#4e0909", "#801414", "#b52622", "#e04a39", "#fb8a72", "#ffc4b4")),
              (16.0, 6.5, 5.0, Ramp("#5c3604", "#8e5a09", "#c48b13", "#eebc33", "#ffe079", "#fff4c8")))
    for (x, y, r, ramp) in blooms:
        bloom = disc(N, N, x, y, r, r * 0.92)
        bevel(c, bloom, ramp, base=3, grad=0.6, strength=1.4)
        # spiral petal lines
        for k in range(14):
            a = k * 0.62
            rr = 0.6 + k * 0.24
            px_, py_ = x + math.cos(a) * rr, y + math.sin(a) * rr * 0.92
            if bloom[int(py_), int(px_)]:
                c.put(int(px_), int(py_), ramp[1] if k % 2 == 0 else ramp[2])
        c.put(int(x - r * 0.45), int(y - r * 0.5), ramp[5])
    # a ribbon bow where the stems meet
    bow = hexc("#e9d27a")
    bow_dark = hexc("#a8862c")
    for (x, y, col) in ((14, 24, bow), (13, 23, bow), (12, 24, bow_dark), (18, 24, bow), (19, 23, bow),
                        (20, 24, bow_dark), (16, 24, bow_dark), (15, 25, bow), (17, 25, bow), (14, 27, bow),
                        (18, 27, bow), (13, 28, bow_dark), (19, 28, bow_dark)):
        c.put(x, y, col)
    return outlined(c)


def icon_shirt() -> Canvas:
    c = Canvas(N)
    shirt = poly(N, N, [(11, 3), (14, 2.5), (16, 5), (18, 2.5), (21, 3), (29, 8), (26.5, 14), (23, 12), (23, 29.5),
                        (9, 29.5), (9, 12), (5.5, 14), (3, 8)])
    linen = Ramp("#5a6b7c", "#8ea3b6", "#b9cde0", "#dbe8f3", "#f3f8fc", "#ffffff")
    bevel(c, shirt, linen, base=4, grad=0.6, strength=1.2, hi_edge=False)
    # folds and seams
    fold = linen[2]
    for (x0, y0, x1, y1) in ((9, 12, 10, 28), (23, 12, 22, 28), (12, 16, 13, 22)):
        for y, x in zip(*np.nonzero(line_mask(N, N, x0, y0, x1, y1) & shirt)):
            c.put(x, y, fold)
    # the collar and a placket with buttons
    collar = poly(N, N, [(13.5, 2.5), (16, 6.5), (18.5, 2.5), (19.5, 4.5), (16, 8.5), (12.5, 4.5)])
    bevel(c, collar, linen, base=3, strength=1.3)
    for y in range(8, 29):
        c.put(16, y, linen[2])
    for y in (11, 15, 19, 23):
        c.put(15, y, hexc("#30425a"))
        c.put(17, y, linen[3])
    # a needle and red thread across the hem
    for y, x in zip(*np.nonzero(line_mask(N, N, 24, 22, 30, 31))):
        c.put(x, y, STEEL[5])
    thread = hexc("#c62f2a")
    for (x, y) in ((25, 25), (26, 27), (27, 28), (24, 28), (23, 29)):
        c.put(x, y, thread)
    return outlined(c)


def icon_key() -> Canvas:
    c = Canvas(N)
    # an ornate key lying diagonally: a clover bow, a long shaft, a two-toothed bit
    bow = disc(N, N, 8.0, 8.0, 5.6) & ~disc(N, N, 8.0, 8.0, 2.6)
    for (ox, oy) in ((-4.0, -4.0), (-5.2, 1.0), (1.0, -5.2)):
        bow |= disc(N, N, 8.0 + ox, 8.0 + oy, 2.2) & ~disc(N, N, 8.0 + ox, 8.0 + oy, 0.9)
    shaft = thick_line(N, N, 11.5, 11.5, 27.0, 27.0, 1.4)
    collar = thick_line(N, N, 12.0, 15.5, 15.5, 12.0, 1.2)
    bit = poly(N, N, [(22.0, 26.5), (26.0, 22.5), (29.5, 26.0), (27.5, 28.0), (26.0, 26.5), (24.5, 28.0)])
    key = bow | shaft | collar | bit
    bevel(c, key, GOLD, base=3, grad=0.6, strength=1.5)
    for y, x in zip(*np.nonzero(line_mask(N, N, 13, 12, 26, 25) & shaft)):
        c.put(x, y, GOLD[5])
    c.put(5, 4, GOLD[6])
    c.put(4, 5, GOLD[6])
    return outlined(c)


def icon_fish() -> Canvas:
    c = Canvas(N)
    body = disc(N, N, 14.0, 16.0, 10.8, 6.4)
    tail = poly(N, N, [(22.5, 16), (29.5, 9.5), (28.0, 16), (29.5, 22.5)])
    fish = body | tail
    shade_rows(c, fish, FISH_SIDE, 2, 5)
    back = fish & poly(N, N, [(0, 0), (32, 0), (32, 14.5), (0, 13.0)])
    shade_rows(c, back, FISH_BACK, 3, 1)
    # fins
    dorsal = poly(N, N, [(9.5, 10.5), (14.5, 6.0), (18.5, 10.5)])
    belly = poly(N, N, [(11.5, 21.5), (13.5, 25.0), (17.0, 21.5)])
    for m in (dorsal, belly):
        bevel(c, m & ~body, FISH_BACK, base=2, strength=1.0)
    for y, x in zip(*np.nonzero(line_mask(N, N, 23, 16, 28, 11) | line_mask(N, N, 23, 16, 28, 21))):
        if tail[y, x]:
            c.put(x, y, FISH_BACK[2])
    # scales: a staggered dot pattern on the flank
    for y in range(13, 21):
        for x in range(8, 23):
            if body[y, x] and (x + (y % 4) // 2 * 2) % 4 == 0 and y % 2 == 0:
                c.put(x, y, FISH_SIDE[2])
    # gill line, eye, mouth
    for y in range(12, 21):
        if body[y, 7]:
            c.put(7, y, FISH_BACK[1])
    eye = disc(N, N, 5.5, 14.0, 1.5)
    fill(c, eye, (250, 250, 250, 255))
    c.put(5, 14, (20, 20, 24, 255))
    c.put(3, 17, FISH_BACK[0])
    for (x, y) in ((10, 12), (11, 12), (12, 12)):
        c.put(x, y, FISH_SIDE[5])
    return outlined(c)


def icon_mug() -> Canvas:
    c = Canvas(N)
    body = poly(N, N, [(5.5, 9), (21.5, 9), (21.0, 29), (6.0, 29)])
    handle = (disc(N, N, 22.5, 18.5, 6.0, 7.0) & ~disc(N, N, 22.5, 18.5, 3.2, 4.2)
              & poly(N, N, [(21.0, 0), (32, 0), (32, 32), (21.0, 32)]))
    bevel(c, handle, WOOD, base=3, strength=1.4)
    # staves with dark gaps, two iron bands
    bevel(c, body, WOOD, base=4, grad=0.8, strength=1.0, hi_edge=False)
    for x in (9, 13, 17):
        for y in range(10, 29):
            c.put(x, y, WOOD[1])
    for yb in (12, 24):
        band = poly(N, N, [(5.5, yb), (21.5, yb), (21.5, yb + 2), (5.5, yb + 2)]) & body
        bevel(c, band, IRON, base=3, strength=1.2)
        for x in (7, 15, 20):
            c.put(x, yb, IRON[5])
    # foam overflowing the rim and running down
    foam = (disc(N, N, 8.0, 7.5, 3.6, 3.0) | disc(N, N, 13.0, 6.0, 4.4, 3.6) | disc(N, N, 18.5, 7.0, 3.8, 3.2)
            | disc(N, N, 6.5, 11.0, 1.6, 2.6))
    bevel(c, foam, FOAM, base=2, strength=1.2)
    c.put(12, 4, FOAM[3])
    c.put(13, 4, FOAM[3])
    # a peek of ale at the rim
    for x in range(9, 12):
        c.put(x, 9, ALE[3])
    return outlined(c)


def icon_scroll() -> Canvas:
    c = Canvas(N)
    sheet = poly(N, N, [(6, 6), (26, 6), (26, 25), (6, 25)])
    bevel(c, sheet, PARCH, base=4, grad=0.6, strength=0.8, hi_edge=False)
    # rolled ends top and bottom
    for y0 in (3.5, 24.0):
        roll = disc(N, N, 16.0, y0 + 2.2, 12.5, 2.6)
        shade_rows(c, roll, PARCH, 5, 1)
        for (x, yy) in ((4, int(y0 + 2)), (28, int(y0 + 2))):
            c.put(x, yy, PARCH[0])
    ink = hexc("#4f3d26")
    for k in range(5):
        y = 10 + k * 2.6
        x1 = 24 if k < 4 else 17
        for x in range(9, x1):
            if (x * 3 + k) % 9 != 4:
                c.put(x, int(y), ink)
    # a red wax seal with a ribbon
    ribbon = hexc("#8e1b1b")
    for (x, y) in ((20, 22), (21, 24), (22, 26), (23, 27), (19, 24), (18, 26), (18, 27)):
        c.put(x, y, ribbon)
    seal = disc(N, N, 21.0, 20.5, 2.8)
    bevel(c, seal, LEATHER, base=4, strength=1.5)
    c.put(21, 20, LEATHER[1])
    return outlined(c)


def icon_map() -> Canvas:
    c = Canvas(N)
    # an unfolded map in three panels with folds, a coast, a road and an X
    sheet = poly(N, N, [(3, 6), (11, 4), (21, 6), (29, 4), (29, 26), (21, 28), (11, 26), (3, 28)])
    bevel(c, sheet, PARCH, base=4, strength=0.6, hi_edge=False)
    panels = (poly(N, N, [(3, 6), (11, 4), (11, 26), (3, 28)]), poly(N, N, [(21, 6), (29, 4), (29, 26), (21, 28)]))
    for m in panels:
        for y, x in zip(*np.nonzero(m & sheet)):
            r, g, b, a = c.get(x, y)
            c.put(x, y, (int(r * 0.88), int(g * 0.88), int(b * 0.86), a))
    # sea on the right with a wavy coast, a green island
    sea = sheet & poly(N, N, [(20, 3), (32, 3), (32, 30), (17, 30), (19, 22), (16, 16), (19, 10)])
    for y, x in zip(*np.nonzero(sea)):
        c.put(x, y, SEA[2] if (x + y) % 5 else SEA[3])
    island = disc(N, N, 25.0, 12.0, 2.4, 1.8)
    for y, x in zip(*np.nonzero(island & sheet)):
        c.put(x, y, GRASS[2])
    for (x, y) in ((22, 20), (24, 22), (26, 20)):
        c.put(x, y, SEA[0])
    # forest dots and a dotted road to the X
    for (x, y) in ((6, 10), (8, 9), (7, 12), (13, 20), (14, 22)):
        c.put(x, y, GRASS[1])
    road = hexc("#7a3b1c")
    for (x, y) in ((5, 23), (7, 22), (9, 20), (10, 18), (12, 16), (13, 14)):
        c.put(x, y, road)
    red = hexc("#c4221c")
    for d in range(-2, 3):
        c.put(15 + d, 9 + d, red)
        c.put(15 + d, 9 - d, red)
    return outlined(c)


def icon_hammer() -> Canvas:
    c = Canvas(N)
    handle = thick_line(N, N, 5.0, 28.0, 19.5, 12.0, 1.35)
    bevel(c, handle, WOOD, base=4, strength=1.3)
    for k in range(4):
        c.put(7 + k, 25 - k, BROWN_LEATHER[1])
        c.put(7 + k, 26 - k, BROWN_LEATHER[2])
    # a heavy smith's hammer head square to the handle: a broad face below right, a wedge peen above left
    head = poly(N, N, [(13.0, 7.5), (19.5, 1.5), (28.5, 10.5), (22.5, 16.5)])
    face = poly(N, N, [(22.0, 16.0), (28.5, 10.0), (30.0, 12.0), (24.0, 18.0)])
    peen = poly(N, N, [(13.5, 7.0), (19.0, 2.0), (16.5, 1.0), (11.0, 5.5)])
    metal = head | face | peen
    bevel(c, metal, STEEL, base=3, grad=0.7, strength=1.5)
    for y, x in zip(*np.nonzero(edge(head, -1, -1) | edge(peen, -1, -1))):
        c.put(x, y, STEEL[6])
    for y, x in zip(*np.nonzero(face)):
        c.put(x, y, STEEL[5] if (x + y) % 3 else STEEL[4])
    # the eye with the wedged handle end
    c.put(19, 9, WOOD[1])
    c.put(20, 10, WOOD[2])
    c.put(19, 10, WOOD[3])
    return outlined(c)


def icon_ring() -> Canvas:
    c = Canvas(N)
    # a gold band seen at an angle with a ruby set in a claw mount
    band = disc(N, N, 16.0, 19.0, 11.0, 8.0) & ~disc(N, N, 16.0, 19.5, 7.6, 5.2)
    bevel(c, band, GOLD, base=3, grad=0.5, strength=1.6)
    for y, x in zip(*np.nonzero(edge(band, 0, -1) & poly(N, N, [(0, 0), (32, 0), (32, 20), (0, 20)]))):
        c.put(x, y, GOLD[6])
    mount = poly(N, N, [(11.5, 11), (20.5, 11), (19, 14), (13, 14)])
    bevel(c, mount, GOLD, base=4, strength=1.2)
    stone = disc(N, N, 16.0, 7.5, 5.0, 4.4)
    fill(c, stone, RUBY[3])
    for (m, col) in ((poly(N, N, [(12, 5), (16, 3), (20, 5), (16, 7)]), RUBY[5]),
                     (poly(N, N, [(11, 8), (16, 7), (16, 12), (12, 10)]), RUBY[2]),
                     (poly(N, N, [(16, 7), (21, 8), (20, 10), (16, 12)]), RUBY[1])):
        fill(c, m & stone, col)
    c.put(14, 4, (255, 236, 240, 255))
    for (x, y) in ((11, 5), (21, 5), (12, 10), (20, 10)):
        c.put(x, y, GOLD[5])
    c = outlined(c)
    for (x, y) in ((25, 4), (26, 3), (27, 4), (26, 5), (26, 4), (6, 9), (5, 9), (7, 9), (6, 8), (6, 10)):
        c.put(x, y, hexc("#fff6e0"))
    return c


ICON_FUNCS = {
    "sword": icon_sword, "shield": icon_shield, "potion": icon_potion, "bread": icon_bread, "anvil": icon_anvil,
    "book": icon_book, "gem": icon_gem, "flower": icon_flower, "shirt": icon_shirt, "key": icon_key,
    "fish": icon_fish, "mug": icon_mug, "scroll": icon_scroll, "map": icon_map, "hammer": icon_hammer,
    "ring": icon_ring,
}
ICONS = tuple(ICON_FUNCS)  # the order of ShopSignBlock.Icon


# =========================================================================== shop sign boards (64 x 48)
BW, BH = 64, 48
PANEL_BOX = (7, 6, 56, 41)          # painted panel (inclusive)


def grain_field(w, h, r: random.Random, scale=1.0):
    """0..1 wood grain intensity: wavy horizontal lines with slow drift."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    p1, p2, p3 = r.random() * 6.28, r.random() * 6.28, r.random() * 6.28
    warp = 1.8 * np.sin(xx * 0.11 * scale + p1) + 0.9 * np.sin(xx * 0.27 * scale + p2)
    g = np.sin((yy + warp) * 1.45 / scale + p3)
    fine = np.sin((yy * 3.1 + warp * 2.0) / scale + xx * 0.05)
    return np.clip(0.55 * (g > 0.72) + 0.25 * (fine > 0.86) + 0.12 * (g > 0.2), 0, 1)


def wood_board(w, h, name, base="#86552c", planks=3) -> np.ndarray:
    r = rng("wood:" + name)
    out = np.zeros((h, w, 4), float)
    col = np.array(hexc(base)[:3], float)
    bounds = [round(i * h / planks) for i in range(planks + 1)]
    for p in range(planks):
        y0, y1 = bounds[p], bounds[p + 1]
        tone = 1.0 + (r.random() - 0.5) * 0.14
        g = grain_field(w, y1 - y0, r)
        for y in range(y0, y1):
            for x in range(w):
                f = tone * (1.0 - 0.22 * g[y - y0, x]) * (1.0 + (r.random() - 0.5) * 0.05)
                out[y, x, :3] = col * f
                out[y, x, 3] = 255
        # a knot now and then
        if r.random() < 0.7:
            kx, ky = r.randint(6, w - 7), r.randint(y0 + 2, max(y0 + 2, y1 - 3))
            for y in range(max(y0, ky - 2), min(y1, ky + 3)):
                for x in range(kx - 3, kx + 4):
                    d = math.hypot((x - kx) / 2.6, (y - ky) / 1.4)
                    if d < 1.0:
                        out[y, x, :3] *= 0.72 + 0.18 * d
        # the gap between planks and a lit upper edge
        if p > 0:
            out[y0, :, :3] *= 0.45
            if y0 + 1 < h:
                out[y0 + 1, :, :3] = np.minimum(255, out[y0 + 1, :, :3] * 1.12)
    return out


def iron_frame(a: np.ndarray, r: random.Random):
    h, w = a.shape[:2]
    band = np.zeros((h, w), bool)
    band[:2, :] = band[-2:, :] = True
    band[:, :2] = band[:, -2:] = True
    for y in range(h):
        for x in range(w):
            if not band[y, x]:
                continue
            inner = x in (1, w - 2) or y in (1, h - 2)
            v = IRON[3] if inner else IRON[2]
            if y == 0 or x == 0:
                v = IRON[4]
            if y == h - 1 or x == w - 1:
                v = IRON[1]
            n = 1.0 + (r.random() - 0.5) * 0.12
            a[y, x] = (v[0] * n, v[1] * n, v[2] * n, 255)
    # corner brackets: L-shaped straps with rivets
    for (cx, cy, sx, sy) in ((2, 2, 1, 1), (w - 3, 2, -1, 1), (2, h - 3, 1, -1), (w - 3, h - 3, -1, -1)):
        for k in range(9):
            for t in range(3):
                for (x, y) in ((cx + sx * k, cy + sy * t), (cx + sx * t, cy + sy * k)):
                    v = IRON[3] if t == 1 else IRON[2]
                    if t == 0 and (sx > 0 if (x, y) == (cx + sx * k, cy + sy * t) else sy > 0):
                        v = IRON[4]
                    a[y, x] = (*v[:3], 255)
        for (x, y) in ((cx + sx * 1, cy + sy * 1), (cx + sx * 6, cy + sy * 1), (cx + sx * 1, cy + sy * 6)):
            rivet(a, x, y)
    # rivets along the band
    for x in range(12, w - 10, 10):
        rivet(a, x, 0, small=True)
        rivet(a, x, h - 1, small=True)
    for y in (h // 2,):
        rivet(a, 0, y, small=True)
        rivet(a, w - 1, y, small=True)


def rivet(a, x, y, small=False):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        a[y, x] = (*IRON[5][:3], 255)
    if not small:
        for (dx, dy, v) in ((1, 0, IRON[2]), (0, 1, IRON[2]), (1, 1, IRON[1])):
            if 0 <= x + dx < w and 0 <= y + dy < h:
                a[y + dy, x + dx] = (*v[:3], 255)


def rounded_rect(w, h, box, radius):
    x0, y0, x1, y1 = box
    m = np.zeros((h, w), bool)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dx = max(x0 + radius - x, 0, x - (x1 - radius))
            dy = max(y0 + radius - y, 0, y - (y1 - radius))
            if dx * dx + dy * dy <= radius * radius + 0.5:
                m[y, x] = True
    return m


def board(name: str) -> Image.Image:
    r = rng("board:" + name)
    a = wood_board(BW, BH, name)
    # the painted panel: the trade's colour, brushed, the grain faintly showing through, worn at the rim
    panel = rounded_rect(BW, BH, PANEL_BOX, 4)
    col = np.array(hexc(PANEL[name])[:3], float)
    g = grain_field(BW, BH, rng("paint:" + name), scale=1.3)
    for y, x in zip(*np.nonzero(panel)):
        f = (1.0 - 0.07 * g[y, x]) * (1.0 + (r.random() - 0.5) * 0.06) * (1.0 + 0.05 * math.sin(x * 0.9 + y * 0.2))
        a[y, x, :3] = col * f
    rim = panel & ~(shift(panel, 1, 0) & shift(panel, -1, 0) & shift(panel, 0, 1) & shift(panel, 0, -1))
    for y, x in zip(*np.nonzero(rim)):
        a[y, x, :3] = col * 0.62
    # a cream pinstripe two pixels inside the rim
    inner = rounded_rect(BW, BH, (PANEL_BOX[0] + 2, PANEL_BOX[1] + 2, PANEL_BOX[2] - 2, PANEL_BOX[3] - 2), 3)
    stripe = inner & ~(shift(inner, 1, 0) & shift(inner, -1, 0) & shift(inner, 0, 1) & shift(inner, 0, -1))
    cream = np.array(hexc("#e7d49e")[:3], float)
    for y, x in zip(*np.nonzero(stripe)):
        a[y, x, :3] = a[y, x, :3] * 0.3 + cream * 0.7
    # worn paint: a few chips where the wood shows
    for _ in range(9):
        x, y = r.randint(PANEL_BOX[0], PANEL_BOX[2]), r.randint(PANEL_BOX[1], PANEL_BOX[3])
        if panel[y, x] and not stripe[y, x]:
            near = rim[max(0, y - 2):y + 3, max(0, x - 2):x + 3].any()
            if near or r.random() < 0.3:
                a[y, x, :3] = np.array(hexc("#9a6a3d")[:3], float)
    # the picture, centred, with a painted shadow
    icon = drop_shadow(ICON_FUNCS[name](), alpha=130)
    ia = icon.a.astype(float)
    ox, oy = (BW - N) // 2, (PANEL_BOX[1] + PANEL_BOX[3] + 1 - N) // 2
    for y in range(N):
        for x in range(N):
            al = ia[y, x, 3] / 255.0
            if al <= 0:
                continue
            a[oy + y, ox + x, :3] = a[oy + y, ox + x, :3] * (1 - al) + ia[y, x, :3] * al
    iron_frame(a, r)
    a[..., 3] = 255
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def sign_edge() -> Image.Image:
    """The iron band round the board's edges."""
    r = rng("edge")
    a = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            v = IRON[3] if 3 <= y <= 12 else IRON[2]
            if y in (0, 15):
                v = IRON[1]
            n = 1.0 + (r.random() - 0.5) * 0.14
            a[y, x] = (int(v[0] * n), int(v[1] * n), int(v[2] * n), 255)
        if y == 7:
            for x in range(1, 16, 5):
                a[y, x] = IRON[5]
    return Image.fromarray(a, "RGBA")


def chain_sprite() -> Image.Image:
    """Two and a half oval links hanging straight down (cutout); seen edge-on the other links show as bars."""
    c = Canvas(16, 16)
    for k, y0 in enumerate((-3, 3, 9, 15)):
        if k % 2 == 0:
            ring = ellipse_mask(16, 16, 7.5, y0 + 2.5, 3.0, 4.2) & ~ellipse_mask(16, 16, 7.5, y0 + 2.5, 1.4, 2.6)
        else:
            ring = poly_mask(16, 16, [(6.5, y0), (8.5, y0), (8.5, y0 + 5.5), (6.5, y0 + 5.5)])
        bevel(c, ring, IRON, base=3, strength=1.6)
    return c.image()


def ring_sprite(size=16, rx=5.0, ry=5.0, thick=1.8, ramp=IRON) -> Image.Image:
    c = Canvas(size, size)
    cx = cy = (size - 1) / 2.0
    ring = ellipse_mask(size, size, cx, cy, rx, ry) & ~ellipse_mask(size, size, cx, cy, rx - thick, ry - thick)
    bevel(c, ring, ramp, base=3, strength=1.8)
    return c.image()


def wrought_iron() -> Image.Image:
    """Hammered black iron for the bracket."""
    r = rng("wrought")
    a = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            hammer = 0.5 + 0.5 * math.sin(x * 1.3 + math.sin(y * 0.9) * 2.0) * math.sin(y * 1.1 + x * 0.3)
            v = mix(IRON[1], IRON[3], 0.35 + 0.4 * hammer)
            n = 1.0 + (r.random() - 0.5) * 0.16
            a[y, x] = (int(v[0] * n), int(v[1] * n), int(v[2] * n), 255)
    for (x, y) in ((3, 4), (11, 2), (7, 9), (13, 12), (2, 13)):
        a[y, x] = IRON[5]
    return Image.fromarray(a, "RGBA")


# =========================================================================== bunting (16 x 16, cloth in greyscale)
def grey(v: float, alpha=255):
    g = int(round(max(0.0, min(1.0, v)) * 255))
    return (g, g, g, alpha)


def weave(v: float, x: int, y: int, r: random.Random, amount=0.03) -> float:
    return v * (1.0 + amount * (1 if (x + y) % 2 else -1) * 0.6 + (r.random() - 0.5) * amount)


def pennant() -> Image.Image:
    r = rng("pennant")
    c = Canvas(16, 16)
    for y in range(16):
        half = 8.0 * (1.0 - (y - 2.5) / 13.0) if y > 2 else 8.0
        for x in range(16):
            dx = abs(x + 0.5 - 8.0)
            if dx > half:
                continue
            v = 0.98 - 0.06 * (y / 15.0)
            if y <= 1:
                v = 0.8 if y == 0 else 0.9           # the hem folded over the cord
            elif y == 2 and x % 2 == 0:
                v = 0.7                              # its stitching
            elif dx > half - 1.0:
                v *= 0.82                            # a rolled edge
            elif abs(x + 0.5 - 8.0) < 0.6 and y > 4:
                v *= 0.95                            # a soft centre fold
            c.put(x, y, grey(weave(v, x, y, r)))
    return c.image()


def flag_sprite() -> Image.Image:
    r = rng("flag")
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if y >= 14 and (x % 2 == 1 or y == 15 and x % 4 != 0):
                continue                             # a little fringe
            v = 0.86
            if y <= 1:
                v = 0.78 if y == 0 else 0.9
            elif x in (0, 15) or y == 13:
                v = 0.74
            else:
                d = abs(x + 0.5 - 8.0) / 5.5 + abs(y + 0.5 - 7.5) / 5.0
                if d <= 1.0:
                    v = 1.0 if d > 0.25 else 0.72     # a bright lozenge with a dark eye
            c.put(x, y, grey(weave(v, x, y, r)))
    return c.image()


def lantern_sprite() -> Image.Image:
    """The paper body unrolled: u round the lantern, v top to bottom; bamboo ribs every two rows."""
    r = rng("lantern")
    c = Canvas(16, 16)
    for y in range(16):
        belly = 1.0 - 0.16 * abs(y - 7.5) / 7.5
        for x in range(16):
            if y in (0, 15):
                v = 0.32                             # lacquered rims
            elif y % 3 == 0:
                v = 0.8 * belly                      # bamboo ribs
            else:
                v = 1.0 * belly
                if x % 8 == 0:
                    v *= 0.92                        # paper seams
            c.put(x, y, grey(v * (1.0 + (r.random() - 0.5) * 0.04)))
    return c.image()


def lantern_cap() -> Image.Image:
    c = Canvas(16, 16)
    lacquer = Ramp("#170806", "#2c110b", "#461d13", "#62291b", "#86402a")
    m = ellipse_mask(16, 16, 7.5, 7.5, 7.6)
    bevel(c, m, lacquer, base=2, strength=1.4)
    for y, x in zip(*np.nonzero(ellipse_mask(16, 16, 7.5, 7.5, 2.0))):
        c.put(x, y, GOLD[3])
    return c.image()


def leaves_sprite() -> Image.Image:
    c = Canvas(16, 16)
    for (ang, length, width, tone) in ((-35, 7.5, 2.6, 3), (210, 7.0, 2.4, 2), (35, 6.5, 2.3, 4), (150, 6.8, 2.5, 3),
                                       (-95, 5.0, 2.0, 4), (95, 5.2, 2.0, 2)):
        a = math.radians(ang)
        dx, dy = math.cos(a), math.sin(a)
        nx, ny = -dy, dx
        tip = (7.5 + dx * length, 7.5 + dy * length)
        mid = (7.5 + dx * length * 0.5, 7.5 + dy * length * 0.5)
        pts = [(7.5 + nx * 0.4, 7.5 + ny * 0.4), (mid[0] + nx * width, mid[1] + ny * width), tip,
               (mid[0] - nx * width, mid[1] - ny * width), (7.5 - nx * 0.4, 7.5 - ny * 0.4)]
        leaf = poly_mask(16, 16, pts)
        bevel(c, leaf, LEAF, base=tone, strength=1.2)
        for y, x in zip(*np.nonzero(line_mask(16, 16, 7.5, 7.5, tip[0], tip[1]) & leaf)):
            c.put(x, y, LEAF[max(0, tone - 2)])
    return c.image()


def flower_sprite() -> Image.Image:
    c = Canvas(16, 16)
    petals = np.zeros((16, 16), bool)
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        petals |= ellipse_mask(16, 16, 7.5 + math.cos(a) * 4.0, 7.5 + math.sin(a) * 4.0, 3.4)
    for y, x in zip(*np.nonzero(petals)):
        d = math.hypot(x - 7.5, y - 7.5)
        c.put(x, y, grey(0.98 - 0.12 * max(0.0, d - 4.5) / 3.0))
    for y, x in zip(*np.nonzero(petals & ~(shift(petals, 1, 0) & shift(petals, -1, 0) & shift(petals, 0, 1)
                                          & shift(petals, 0, -1)))):
        c.put(x, y, grey(0.72))
    for k in range(5):
        a = -math.pi / 2 + (k + 0.5) * 2 * math.pi / 5
        for t in (2.5, 3.5, 4.5):
            c.put(int(7.5 + math.cos(a) * t), int(7.5 + math.sin(a) * t), grey(0.8))
    centre = ellipse_mask(16, 16, 7.5, 7.5, 2.2)
    for y, x in zip(*np.nonzero(centre)):
        c.put(x, y, hexc("#c99a2e") if (x + y) % 3 else hexc("#8f6a1c"))
    return c.image()


def cloth_from(rows, name, values) -> Image.Image:
    """Greyscale cloth from ASCII art: each character maps to a brightness (cutout where '.')."""
    r = rng(name)
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in values:
                c.put(x, y, grey(weave(values[ch], x, y, r, 0.04)))
    return c.image()


SHIRT = [
    "..##ccVVVVcc##..",
    ".###cccVVccc###.",
    "################",
    "####s#######s###",
    "#####s##b##s####",
    "###.##########.#",
    "...############.",
    "...#####b######.",
    "...#f##########.",
    "...#f###b###f##.",
    "...#f#######f##.",
    "...#####b###f##.",
    "...############.",
    "...############.",
    "...hhhhhhhhhhhh.",
    "................",
]
TOWEL = [
    "hhhhhhhhhhhhhh..",
    "##############..",
    "##############..",
    "##############..",
    "##f###########..",
    "##f###########..",
    "##############..",
    "######f#######..",
    "##############..",
    "llllllllllllll..",
    "##############..",
    "llllllllllllll..",
    "##############..",
    "#.#.#.#.#.#.#.#.",
    "#...#...#...#...",
    "................",
]
SOCKS = [
    "rrrrr...rrrrr...",
    "RRRRR...RRRRR...",
    "rrrrr...rrrrr...",
    "#####...#####...",
    "#####...#####...",
    "#####...#####...",
    "#####...#####...",
    "##f##...##f##...",
    "#####...#####...",
    "#####...#####...",
    "hh######hh######",
    "hh######hh######",
    ".h#####tt.######",
    "..####tt..####tt",
    "................",
    "................",
]
TROUSERS = [
    "wwwwwwwwwwwwww..",
    "wlwwwwwlwwwwlw..",
    "##############..",
    "##############..",
    "######bb######..",
    "##f###bb###f##..",
    "##f###..###f##..",
    "##f###..###f##..",
    "##f###..###f##..",
    "#######..######.",
    "##f####..###f##.",
    "##f###....##f###",
    "##f###....##f###",
    "######....######",
    "hhhhhh....hhhhhh",
    "................",
]
DRESS = [
    "...ss......ss...",
    "...ss......ss...",
    "...##########...",
    "..############..",
    "..###p####p###..",
    "..############..",
    "..wwwwwwwwwwww..",
    ".##############.",
    ".#f###f##f###f#.",
    ".#f###f##f###f#.",
    "##f###f##f###f##",
    "##f###f##f###f##",
    "##f###f##f###f##",
    "################",
    "hhhhhhhhhhhhhhhh",
    "................",
]


def peg_sprite() -> Image.Image:
    c = Canvas(16, 16)
    wood = Ramp("#7a5530", "#a37b4d", "#c79e6b", "#e2bf8e", "#f4dcb4")
    m = poly_mask(16, 16, [(5, 0), (10, 0), (10, 15), (5, 15)])
    bevel(c, m, wood, base=2, strength=1.3)
    for y in range(7, 16):
        c.put(7, y, wood[0])
    for y in (5, 6):
        for x in range(4, 12):
            c.put(x, y, STEEL[3] if y == 5 else STEEL[2])
    return c.image()


def cord_sprite() -> Image.Image:
    """Twisted hemp: diagonal strands, rounded (dark at both sides). u across, v along."""
    c = Canvas(16, 16)
    hemp = Ramp("#4f3a1f", "#76592f", "#9c7a45", "#bf9c62", "#dcc08a")
    for y in range(16):
        for x in range(16):
            across = abs(x - 7.5) / 8.0
            strand = 0.5 + 0.5 * math.sin((x * 0.8 + y * 1.6) * 0.78)
            tone = 1.0 + 3.2 * strand * (1.0 - across ** 2) - 0.6 * across
            c.put(x, y, hemp[int(round(tone))])
    return c.image()


def glow_texture() -> Image.Image:
    a = np.zeros((32, 32, 4), np.uint8)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 15.5
            v = max(0.0, 1.0 - d) ** 2.3
            g = int(round(255 * v))
            a[y, x] = (g, g, g, g)
    return Image.fromarray(a, "RGBA")


# =========================================================================== floating lantern (16 x 16)
def float_paper() -> Image.Image:
    """A side of the floating lantern: a thin wooden frame round paper painted with a blossom (tinted warm)."""
    r = rng("float_paper")
    c = Canvas(16, 16)
    wood = Ramp("#2a160b", "#40230f", "#5a3317", "#744421")
    for y in range(16):
        for x in range(16):
            if y <= 1 or y >= 14:
                c.put(x, y, wood[1 if y in (0, 15) else 2])
            elif x in (0, 15):
                c.put(x, y, wood[1])
            else:
                fibre = 0.03 * math.sin(x * 2.1 + y * 0.7) + (r.random() - 0.5) * 0.04
                c.put(x, y, grey(0.95 + fibre))
    petals = np.zeros((16, 16), bool)
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        petals |= ellipse_mask(16, 16, 7.5 + math.cos(a) * 2.6, 7.6 + math.sin(a) * 2.6, 1.9)
    for y, x in zip(*np.nonzero(petals)):
        if 2 <= y <= 13 and 1 <= x <= 14:
            c.put(x, y, grey(0.8))
    for y, x in zip(*np.nonzero(petals & ~(shift(petals, 1, 0) & shift(petals, -1, 0) & shift(petals, 0, 1)
                                          & shift(petals, 0, -1)))):
        if 2 <= y <= 13 and 1 <= x <= 14:
            c.put(x, y, grey(0.64))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.put(x, y, grey(0.5))
    for x in range(2, 14, 3):
        c.put(x, 12, grey(0.72))                   # a painted wave along the bottom
        c.put(x + 1, 11, grey(0.72))
    return c.image()


def float_raft() -> Image.Image:
    a = wood_board(16, 16, "float_raft", base="#6a4424", planks=3)
    for x in (3, 12):
        for y in range(16):
            a[y, x, :3] = np.array(hexc("#b49a6a")[:3], float) * (0.85 if y % 2 else 1.0)   # rope binding
    a[..., 3] = 255
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def item_floating_lantern() -> Image.Image:
    c = Canvas(16, 16)
    water = Ramp("#123a5a", "#1d5580", "#2c74a6", "#4a96c4")
    for y in range(11, 16):
        for x in range(16):
            c.put(x, y, water[1 + ((x + y * 3) % 5 == 0) + (y == 11)])
    for x in range(4, 12):
        c.put(x, 11, hexc("#e8a04a") if 6 <= x <= 9 else water[3])
    for y in (9, 10):
        for x in range(3, 13):
            c.put(x, y, hexc("#5a3317") if y == 10 else hexc("#744421"))
    paper = Ramp("#a0602a", "#d28a3e", "#f2b85a", "#ffdc8c", "#fff3c8")
    for y in range(3, 9):
        for x in range(5, 11):
            c.put(x, y, paper[4 if (x in (7, 8) and 4 <= y <= 7) else (3 if 5 <= y <= 7 else 2)])
    for x in range(5, 11):
        c.put(x, 2, hexc("#40230f"))
        c.put(x, 8, hexc("#40230f"))
    c.put(7, 1, hexc("#ffe9a8"))
    return c.image()


# =========================================================================== item icons (16 x 16)
def item_bunting() -> Image.Image:
    c = Canvas(16, 16)
    cord = hexc("#cdb07a")
    sag = [int(round(1.5 + 2.2 * (1.0 - ((x - 7.5) / 7.5) ** 2))) for x in range(16)]
    for x in range(16):
        c.put(x, sag[x], cord)
    colors = (hexc("#d43a30"), hexc("#f6cd3b"), hexc("#3559c4"), hexc("#4f8d3b"))
    for k, x0 in enumerate((2, 6, 10)):
        col = colors[k]
        dark = (int(col[0] * 0.72), int(col[1] * 0.72), int(col[2] * 0.72), 255)
        top = max(sag[x0], sag[x0 + 2]) + 1
        rows = ((x0, x0 + 1, x0 + 2), (x0, x0 + 1, x0 + 2), (x0 + 1,), (x0 + 1,))
        for dy, xs in enumerate(rows):
            for x in xs:
                c.put(x, top + dy, col if x <= x0 + 1 else dark)
    c.put(0, 1, IRON[4])
    c.put(15, 1, IRON[4])
    c.put(0, 2, IRON[2])
    c.put(15, 2, IRON[2])
    # a lantern hanging at the end
    for (x, y, v) in ((13, 5, 2), (14, 5, 2), (13, 6, 4), (14, 6, 3), (13, 7, 4), (14, 7, 3), (13, 8, 2), (14, 8, 2)):
        lantern = Ramp("#5a1d0e", "#8a2c12", "#c4531f", "#ee8a3a", "#ffc46a")
        c.put(x, y, lantern[v])
    c.put(13, 4, cord)
    return c.image()


def item_secret_door() -> Image.Image:
    c = Canvas(16, 16)
    stone = Ramp("#3b3b3b", "#5a5a5a", "#787878", "#929292", "#ababab")
    for y in range(16):
        row = y // 4
        for x in range(16):
            off = 0 if row % 2 == 0 else 4
            mortar = y % 4 == 3 or (x + off) % 8 == 7
            c.put(x, y, stone[0] if mortar else stone[2 + ((x * 7 + y * 3) % 5 == 0)])
    # the doorway: dark passage with a glimmer of torchlight
    for y in range(3, 16):
        for x in range(4, 9):
            c.put(x, y, hexc("#140f0c") if x > 4 else hexc("#241a12"))
    c.put(6, 7, hexc("#f2a33a"))
    c.put(6, 6, hexc("#ffd27a"))
    # the panel slid aside, a shadowed edge
    for y in range(3, 16):
        c.put(9, y, hexc("#1f1f1f"))
        for x in range(10, 15):
            if (y % 4 == 3) or (x + (0 if (y // 4) % 2 == 0 else 4)) % 8 == 7:
                c.put(x, y, stone[1])
            else:
                c.put(x, y, stone[3])
        c.put(15, y, stone[1])
    for x in range(4, 15):
        c.put(x, 2, stone[4])
    return c.image()


def item_shop_sign() -> Image.Image:
    c = Canvas(16, 16)
    for x in range(1, 15):
        c.put(x, 1, IRON[3])
    c.put(14, 2, IRON[3])
    c.put(1, 0, IRON[4])
    c.put(1, 2, IRON[2])
    for x in (4, 11):
        c.put(x, 2, IRON[4])
    board_m = poly_mask(16, 16, [(2, 3), (14, 3), (14, 13), (2, 13)])
    bevel(c, board_m, WOOD, base=3, strength=1.0, hi_edge=False)
    for y, x in zip(*np.nonzero(board_m & ~(shift(board_m, 1, 0) & shift(board_m, -1, 0) & shift(board_m, 0, 1)
                                            & shift(board_m, 0, -1)))):
        c.put(x, y, IRON[2])
    for y in range(5, 12):
        for x in range(4, 13):
            c.put(x, y, hexc(PANEL["sword"]))
    for k in range(5):
        c.put(6 + k, 10 - k, STEEL[5])
    c.put(5, 9, GOLD[4])
    c.put(7, 11, GOLD[4])
    c.put(5, 11, BROWN_LEATHER[3])
    return c.image()


# =========================================================================== models, states, loot, tags
def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def save_png(img: Image.Image, *parts: str) -> Image.Image:
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return img


def model(name, data):
    write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), data)


def item_model(name):
    write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


def loot(name):
    write_json(os.path.join(DATA, NS, "loot_tables", "blocks", f"{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def merge_tag(path, values):
    existing = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            existing = json.load(f)
    merged = sorted(set(existing.get("values", [])) | set(values))
    write_json(path, {"replace": False, "values": merged})


FACING_Y = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
LOOKS = ("bookshelf", "stone_bricks", "castle_bricks", "white_plaster", "mossy_stone_bricks", "dark_oak_planks",
         "spruce_planks")
OWN_LOOKS = ("castle_bricks", "white_plaster")      # the mod's weathered blocks: copy their weighted variants


def face_uv(face, frm, to):
    """A patch of the (uniform) iron texture the size of the face, kept inside the sprite (parts of the bracket
    reach outside the block, where automatic UVs would sample the neighbouring atlas sprites)."""
    dx, dy, dz = (abs(to[i] - frm[i]) for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz),
            "down": (dx, dz)}[face]
    w, h = max(0.5, min(12.0, w)), max(0.5, min(12.0, h))
    return [2, 2, round(2 + w, 3), round(2 + h, 3)]


def box(frm, to, tex="#iron", faces=("north", "east", "south", "west", "up", "down"), rotation=None):
    e = {"from": list(frm), "to": list(to),
         "faces": {f: {"uv": face_uv(f, frm, to), "texture": tex} for f in faces}}
    if rotation:
        e["rotation"] = rotation
    return e


def gen_models():
    t = lambda n: f"{NS}:block/{n}"  # noqa: E731
    # bunting: the anchor is drawn by its renderer (the eye turns to the string, the stem finds the wall)
    model("bunting", {"textures": {"particle": t("bunting/iron")}})
    write_json(os.path.join(ASSETS, "blockstates", "bunting.json"),
               {"variants": {f"facing={f}": {"model": f"{NS}:block/bunting"} for f, _ in FACING_Y}})
    # shop sign, facing north (the wall is to the south): plate with two bolts, the arm along the top of the cell
    # (pivot of the board at y 12.5, see ShopSignRenderer), a brace slanting down from the wall to the arm's middle,
    # a collar where they meet and a knob at the tip
    model("shop_sign_wall", {
        "parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:cutout",
        "textures": {"iron": t("shop_sign/bracket"), "particle": t("shop_sign/bracket")},
        "elements": [
            box((6.5, 11, 15), (9.5, 16, 16)),
            box((7.5, 12, -1.5), (8.5, 13, 15)),
            box((7, 11.5, -2.75), (9, 13.5, -1.25)),
            box((7.5, 14.5, 7.5), (8.5, 15.5, 15.5), rotation={"angle": -22.5, "axis": "x", "origin": [8, 15.5, 15.5]}),
            box((7.2, 11.6, 7.4), (8.8, 13.4, 9.2)),
            box((7.3, 11.2, 14.6), (8.7, 11.9, 15)),
            box((7.3, 14.4, 14.6), (8.7, 15.1, 15)),
        ]})
    # hanging from the ceiling: two plates with eyes where the chains hang (the board spans x when facing north)
    model("shop_sign_ceiling", {
        "parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:cutout",
        "textures": {"iron": t("shop_sign/bracket"), "particle": t("shop_sign/bracket")},
        "elements": [
            box((0.25, 15.5, 6.75), (2.75, 16, 9.25)),
            box((1.0, 14.6, 7.6), (2.0, 15.5, 8.4)),
            box((13.25, 15.5, 6.75), (15.75, 16, 9.25)),
            box((14.0, 14.6, 7.6), (15.0, 15.5, 8.4)),
        ]})
    icons = ICONS
    variants = {}
    for attach in ("ceiling", "wall"):
        for f, y in FACING_Y:
            for icon in icons:
                v = {"model": f"{NS}:block/shop_sign_{attach}"}
                if y:
                    v["y"] = y
                variants[f"attach={attach},facing={f},icon={icon}"] = v
    write_json(os.path.join(ASSETS, "blockstates", "shop_sign.json"), {"variants": variants})
    # secret door: the wall's own models, unrotated, so it matches its neighbours exactly (weighted variants too)
    look_models = {}
    for look in LOOKS:
        if look in OWN_LOOKS:
            with open(os.path.join(ASSETS, "blockstates", f"{look}.json"), encoding="utf-8") as fh:
                look_models[look] = json.load(fh)["variants"][""]
        else:
            look_models[look] = {"model": f"minecraft:block/{look}"}
    door = {}
    for f, _ in FACING_Y:
        for half in ("lower", "upper"):
            for look in LOOKS:
                for open_ in ("false", "true"):
                    door[f"facing={f},half={half},look={look},open={open_}"] = look_models[look]
    write_json(os.path.join(ASSETS, "blockstates", "secret_door.json"), {"variants": door})
    # floating lantern: drawn by its renderer (float, paper, flame, halo, light on the water)
    model("floating_lantern", {"textures": {"particle": t("floating_lantern/raft")}})
    write_json(os.path.join(ASSETS, "blockstates", "floating_lantern.json"),
               {"variants": {"": {"model": f"{NS}:block/floating_lantern"}}})
    for name in ("bunting", "secret_door", "shop_sign", "floating_lantern"):
        item_model(name)
    loot("bunting")
    loot("shop_sign")
    loot("floating_lantern")
    tags = os.path.join(DATA, "minecraft", "tags", "blocks")
    merge_tag(os.path.join(tags, "mineable", "pickaxe.json"), [f"{NS}:bunting"])
    merge_tag(os.path.join(tags, "mineable", "axe.json"), [f"{NS}:shop_sign", f"{NS}:floating_lantern"])


# =========================================================================== preview
def tint(img: Image.Image, rgb) -> Image.Image:
    a = np.asarray(img).astype(float)
    a[..., :3] *= np.array(rgb, float)[None, None, :] / 255.0
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


DYES = {"white": (244, 239, 226), "orange": (236, 132, 43), "magenta": (194, 75, 180), "light_blue": (99, 181, 232),
        "yellow": (246, 205, 59), "lime": (141, 198, 63), "pink": (240, 146, 182), "red": (212, 58, 48),
        "blue": (53, 89, 196), "green": (79, 141, 59), "cyan": (44, 158, 168), "purple": (125, 76, 185)}


def catenary_points(span, sag, n=200):
    """Level catenary through (0, 0) and (span, 0) sagging ``sag`` at the middle (for the preview only)."""
    lo, hi = math.log(span * 1e-3), math.log(span * 1e6)
    for _ in range(80):
        mid = 0.5 * (lo + hi)
        a = math.exp(mid)
        if a * (math.cosh(span / (2 * a)) - 1) > sag:
            lo = mid
        else:
            hi = mid
    a = math.exp(0.5 * (lo + hi))
    return [(span * i / n, a * math.cosh((span * i / n - span / 2) / a) - a * math.cosh(span / (2 * a)))
            for i in range(n + 1)]


def string_scene(width, height, sprites, colors, spacing, size, sag, night=False, label=""):
    """A level string seen from the side with its pieces hanging (proportions as in the game, 40 px a block)."""
    S = 40.0
    bg = (24, 30, 54, 255) if night else (206, 222, 236, 255)
    img = Image.new("RGBA", (width, height), bg)
    span = (width - 80) / S
    pts = catenary_points(span, sag)
    ox, oy = 40, 40
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.line([(ox + x * S, oy - y * S) for x, y in pts], fill=(150, 118, 70, 255), width=2)
    for x in (ox, ox + span * S):
        d.ellipse([x - 4, oy - 4, x + 4, oy + 4], outline=(40, 40, 46, 255), width=2)
    k = 0
    s = 0.32 + spacing / 2
    length = sum(math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1))
    while s < length - 0.32 - spacing / 2 + 1e-6:
        acc = 0.0
        for i in range(len(pts) - 1):
            seg = math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1])
            if acc + seg >= s:
                f = (s - acc) / seg
                x = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * f
                y = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * f
                break
            acc += seg
        spr = sprites[k % len(sprites)]
        col = colors[k % len(colors)]
        w, h = size if isinstance(size, tuple) else size[k % len(size)]
        piece = tint(spr, col).resize((max(1, int(w * S)), max(1, int(h * S))), Image.NEAREST)
        if night:
            glow = glow_texture().resize((int(1.0 * S), int(1.0 * S)), Image.BILINEAR)
            g = np.asarray(glow).astype(float)
            g[..., :3] *= np.array(col, float)[None, None, :] / 255.0
            g[..., 3] *= 0.7
            img.alpha_composite(Image.fromarray(g.astype(np.uint8), "RGBA"),
                                (int(ox + x * S - S / 2), int(oy - y * S + 0.08 * S + h * S / 2 - S / 2)))
        img.alpha_composite(piece, (int(ox + x * S - w * S / 2), int(oy - y * S + (0.08 * S if night else 0))))
        s += spacing
        k += 1
    if label:
        d.text((8, height - 14), label, fill=(30, 30, 30, 255) if not night else (220, 220, 230, 255))
    return img


def preview(boards, sprites, items):
    W = 1180
    sheet = Image.new("RGBA", (W, 1460), (38, 42, 54, 255))
    # the 16 boards at 3x, with their look from 40 and 80 blocks away underneath
    for i, name in enumerate(ICONS):
        bx, by = 12 + (i % 4) * 292, 12 + (i // 4) * 196
        sheet.alpha_composite(boards[name].resize((192, 144), Image.NEAREST), (bx, by))
        far = boards[name].resize((32, 24), Image.BOX).resize((64, 48), Image.NEAREST)
        farther = boards[name].resize((16, 12), Image.BOX).resize((64, 48), Image.NEAREST)
        sheet.alpha_composite(far, (bx + 200, by))
        sheet.alpha_composite(farther, (bx + 200, by + 56))
    y0 = 12 + 4 * 196
    scenes = [
        string_scene(W - 24, 110, [sprites["pennant"]], [DYES[c] for c in ("red", "yellow", "blue", "white", "green")],
                     0.56, (0.46, 0.58), 1.2, label="pennant"),
        string_scene(W - 24, 100, [sprites["flag"]], [DYES[c] for c in ("blue", "white", "red")], 0.52, (0.4, 0.36),
                     1.0, label="flags"),
        string_scene(W - 24, 120, [sprites["lantern"]], [DYES[c] for c in ("orange", "red", "yellow", "pink")], 1.3,
                     (0.33, 0.4), 1.2, night=True, label="lanterns at night"),
        string_scene(W - 24, 120, [sprites[k] for k in ("shirt", "towel", "socks", "trousers", "dress")],
                     [DYES[c] for c in ("white", "light_blue", "white", "blue", "pink")], 0.75,
                     [(0.62, 0.6), (0.48, 0.62), (0.34, 0.36), (0.42, 0.76), (0.5, 0.72)], 0.8, label="laundry"),
    ]
    for sc in scenes:
        sheet.alpha_composite(sc, (12, y0))
        y0 += sc.height + 8
    # sprites at 6x
    x = 12
    for name in ("pennant", "flag", "lantern", "lantern_cap", "leaves", "flower", "shirt", "towel", "socks",
                 "trousers", "dress", "peg", "cord", "ring", "iron", "float_paper", "float_raft"):
        img = sprites[name]
        if name == "float_paper":
            img = tint(img, (255, 214, 160))
        if name in ("pennant", "flag", "lantern", "flower", "shirt", "towel", "socks", "trousers", "dress"):
            img = tint(img, DYES["red" if name != "shirt" else "light_blue"])
        sheet.alpha_composite(img.resize((60, 60), Image.NEAREST), (x, y0))
        x += 66
    y0 += 72
    x = 12
    for img in items:
        sheet.alpha_composite(img.resize((96, 96), Image.NEAREST), (x, y0))
        x += 104
    sheet.save(os.path.join(HERE, "canal_gimmicks_preview.png"))


# =========================================================================== main
def main() -> int:
    boards = {}
    for name in ICONS:
        boards[name] = save_png(board(name), "block", "shop_sign", f"{name}.png")
    save_png(sign_edge(), "block", "shop_sign", "edge.png")
    save_png(chain_sprite(), "block", "shop_sign", "chain.png")
    save_png(ring_sprite(16, 4.6, 6.6, 1.6), "block", "shop_sign", "ring.png")
    save_png(wrought_iron(), "block", "shop_sign", "bracket.png")
    sprites = {
        "pennant": pennant(), "flag": flag_sprite(), "lantern": lantern_sprite(), "lantern_cap": lantern_cap(),
        "leaves": leaves_sprite(), "flower": flower_sprite(),
        "shirt": cloth_from(SHIRT, "shirt", {"#": 0.97, "c": 0.86, "V": 0.6, "s": 0.84, "b": 0.5, "f": 0.88,
                                              "h": 0.82}),
        "towel": cloth_from(TOWEL, "towel", {"#": 0.9, "h": 0.78, "f": 0.82, "l": 0.66}),
        "socks": cloth_from(SOCKS, "socks", {"#": 0.95, "r": 0.74, "R": 0.88, "f": 0.86, "h": 0.72, "t": 0.72}),
        "trousers": cloth_from(TROUSERS, "trousers", {"#": 0.86, "w": 0.66, "l": 0.5, "f": 0.76, "b": 0.6,
                                                      "h": 0.72}),
        "dress": cloth_from(DRESS, "dress", {"#": 0.95, "s": 0.8, "p": 0.6, "w": 0.7, "f": 0.86, "h": 0.8}),
        "peg": peg_sprite(), "cord": cord_sprite(), "ring": ring_sprite(), "iron": wrought_iron(),
    }
    for name, img in list(sprites.items()):
        save_png(img, "block", "bunting", f"{name}.png")
    save_png(glow_texture(), "entity", "bunting", "glow.png")
    sprites["float_paper"] = save_png(float_paper(), "block", "floating_lantern", "paper.png")
    sprites["float_raft"] = save_png(float_raft(), "block", "floating_lantern", "raft.png")
    items = [save_png(item_bunting(), "item", "bunting.png"), save_png(item_secret_door(), "item", "secret_door.png"),
             save_png(item_shop_sign(), "item", "shop_sign.png"),
             save_png(item_floating_lantern(), "item", "floating_lantern.png")]
    gen_models()
    preview(boards, sprites, items)
    print(f"{len(boards)} shop sign boards, {len(sprites)} cloth/float sprites, {len(items)} item icons, models, states")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
