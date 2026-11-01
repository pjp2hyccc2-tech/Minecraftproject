#!/usr/bin/env python3
"""FROSTBOUND texture / model generator.

Procedurally draws every PNG of the mod (block textures, citizen skins, GUI icons)
and writes the block/item model JSON and blockstates. Pure PIL + numpy, fully
deterministic (fixed seeds, no clock/system randomness), so re-running it always
produces byte-identical files.

    python3 tools/gen_textures.py                 # write assets
    python3 tools/gen_textures.py --preview DIR   # also write upscaled preview sheets to DIR

Art direction: gritty industrial steampunk in deep cold. Dark iron, soot, tarnished
brass/copper, frost-white highlights; warm orange heat glow is the only saturated
accent. Crisp pixels, small fixed palettes, ordered dithering instead of blur.
"""
from __future__ import annotations

import argparse
import json
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "frostbound"
NS = "frostbound"
SEED = 0x0F205B0D  # project-wide base seed


def rng_for(name: str) -> np.random.Generator:
    """Independent, stable random stream per asset (never Python's salted hash())."""
    return np.random.default_rng([SEED, zlib.crc32(name.encode("utf-8"))])


# --------------------------------------------------------------------------------------
# Palettes (RGB). Each ramp runs dark -> light.
# --------------------------------------------------------------------------------------
IRON = [(13, 14, 17), (22, 24, 28), (33, 35, 40), (46, 49, 55), (61, 65, 72),
        (80, 85, 93), (104, 110, 119), (134, 141, 150)]
SOOT = [(9, 8, 8), (16, 15, 15), (24, 22, 22)]
FROST = [(118, 136, 154), (158, 178, 196), (200, 216, 230), (236, 244, 250)]
EMBER = [(44, 12, 6), (82, 22, 8), (132, 38, 10), (186, 68, 14), (228, 108, 24),
         (250, 152, 40), (255, 200, 84), (255, 236, 156), (255, 252, 222)]
WARM_IRON = [(24, 15, 12), (42, 24, 16), (64, 34, 19), (94, 48, 22), (128, 66, 26)]
BRASS = [(38, 28, 17), (62, 46, 26), (92, 70, 38), (124, 96, 52), (158, 126, 70),
         (194, 162, 100), (226, 202, 146)]
VERDI = [(36, 60, 54), (52, 84, 74), (72, 110, 94), (96, 134, 112)]
COPPER = [(62, 31, 21), (96, 49, 30), (132, 71, 42), (164, 97, 60)]
BRICK = [(29, 16, 13), (45, 23, 17), (61, 31, 22), (78, 39, 28), (96, 49, 34),
         (114, 60, 41), (132, 73, 51)]
MORTAR = [(22, 21, 21), (32, 31, 30), (44, 42, 40), (58, 55, 52)]
ASH = [(40, 38, 37), (58, 55, 52), (80, 76, 72)]
WOOD = [(33, 21, 14), (49, 31, 20), (66, 43, 27), (86, 57, 35), (106, 72, 44),
        (128, 89, 55), (150, 107, 68)]
CREAM = [(118, 110, 92), (166, 158, 134), (204, 196, 170), (232, 226, 204)]
NEEDLE = (156, 34, 22)

# 4x4 ordered-dither thresholds in (0, 1)
BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0


def clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v


def ramp_at(ramp, v: float, x: int, y: int, dither: float = 1.0):
    """Pick ramp[v] for a fractional index v using ordered dithering at pixel (x, y)."""
    base = int(np.floor(v))
    frac = v - base
    thr = 0.5 + (BAYER4[y % 4, x % 4] - 0.5) * dither
    if frac > thr:
        base += 1
    return ramp[clamp(base, 0, len(ramp) - 1)]


def periodic_noise(rng, size: int, cells: int) -> np.ndarray:
    """Smooth value noise that tiles seamlessly on a size x size canvas."""
    g = rng.random((cells, cells))
    c = np.arange(size) * cells / size
    i0 = np.floor(c).astype(int) % cells
    i1 = (i0 + 1) % cells
    f = c - np.floor(c)
    f = f * f * (3 - 2 * f)
    fx, fy = f[None, :], f[:, None]
    top = g[np.ix_(i0, i0)] * (1 - fx) + g[np.ix_(i0, i1)] * fx
    bot = g[np.ix_(i1, i0)] * (1 - fx) + g[np.ix_(i1, i1)] * fx
    return top * (1 - fy) + bot * fy


def fbm(rng, size=16, cells=(2, 4, 8), weights=(0.5, 0.3, 0.2)) -> np.ndarray:
    acc = sum(w * periodic_noise(rng, size, c) for c, w in zip(cells, weights))
    acc -= acc.min()
    return acc / max(acc.max(), 1e-9)


class Canvas:
    def __init__(self, w: int, h: int, fill=None):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 4), np.uint8)
        if fill is not None:
            self.px[:, :, :3] = fill
            self.px[:, :, 3] = 255

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y, x] = (c[0], c[1], c[2], a)

    def get(self, x, y):
        return tuple(int(v) for v in self.px[y, x, :3])

    def alpha(self, x, y):
        return int(self.px[y, x, 3])

    def image(self) -> Image.Image:
        return Image.fromarray(self.px, "RGBA")


def from_index_map(idx: np.ndarray, ramp, dither=1.0) -> Canvas:
    h, w = idx.shape
    cv = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            cv.set(x, y, ramp_at(ramp, idx[y, x], x, y, dither))
    return cv


def rivet(cv: Canvas, x, y, ramp=IRON, hi=7, mid=5, lo=1):
    """2x2 dome rivet, lit from the top-left."""
    cv.set(x, y, ramp[hi])
    cv.set(x + 1, y, ramp[mid])
    cv.set(x, y + 1, ramp[mid])
    cv.set(x + 1, y + 1, ramp[lo])


# --------------------------------------------------------------------------------------
# Block textures (16x16, opaque)
# --------------------------------------------------------------------------------------

def plate_base(rng, base=2.8, spread=1.5, grain=0.8) -> np.ndarray:
    n = fbm(rng)
    w = rng.random((16, 16))
    return base + (n - 0.5) * spread + (w - 0.5) * grain


def bevel(idx: np.ndarray, amount=1.6, inset=0):
    a, b = inset, 15 - inset
    idx[a, a:b] += amount
    idx[a:b, a] += amount
    idx[b, a + 1:b + 1] -= amount
    idx[a + 1:b + 1, b] -= amount


def tex_generator_casing() -> Canvas:
    rng = rng_for("generator_casing")
    idx = 3.1 + (fbm(rng) - 0.5) * 0.6 + (rng.random((16, 16)) - 0.5) * 0.3
    idx += np.linspace(0.2, -0.4, 16)[:, None]  # grime collects toward the bottom
    bevel(idx, 1.8)
    rivets = [(2, 2), (7, 2), (12, 2), (2, 7), (12, 7), (2, 12), (7, 12), (12, 12)]
    # soot streaks: a handful of 1px drips running down from rivets and the top seam
    drips = [(4, 1, 4), (10, 1, 3), (8, 4, 6), (3, 9, 4), (13, 9, 3), (5, 6, 5)]
    for x, y0, length in drips:
        for k in range(length):
            y = y0 + k
            if 1 <= y <= 14:
                idx[y, x] = min(idx[y, x], 1.6 + k * 0.35)
    cv = from_index_map(idx, IRON, dither=0.6)
    for rx, ry in rivets:
        cv.set(rx + 2, ry + 2, IRON[2])  # contact shadow
        rivet(cv, rx, ry, hi=7, mid=5, lo=2)
    # frost: rime on top of a few rivet heads and on the lower rim
    for x, y, f in [(2, 1, 2), (12, 1, 1), (7, 11, 1), (2, 15, 2), (3, 15, 1), (11, 15, 1)]:
        cv.set(x, y, FROST[f])
    return cv


def tex_generator_core() -> Canvas:
    rng = rng_for("generator_core")
    idx = plate_base(rng, base=2.2, spread=1.2, grain=0.7)
    bevel(idx, 1.8)
    cv = from_index_map(idx, IRON)
    for rx, ry in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        rivet(cv, rx, ry)
    # warm spill of the core onto the plate around the bezel
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 5.0 < d < 6.6 and not (x in (0, 15) or y in (0, 15)):
                if BAYER4[y % 4, x % 4] < (6.6 - d) * 0.55:
                    cv.set(x, y, WARM_IRON[1 + (1 if d < 5.8 else 0)])
    # raised bezel ring (x/y = 3 and 12)
    for k in range(3, 13):
        cv.set(k, 3, IRON[5])
        cv.set(3, k, IRON[5])
        cv.set(k, 12, IRON[2])
        cv.set(12, k, IRON[2])
    cv.set(3, 3, IRON[6])
    cv.set(12, 3, IRON[4])
    cv.set(3, 12, IRON[4])
    cv.set(12, 12, IRON[1])
    # glowing 3x3 grille, hottest in the middle
    for y in range(4, 12):
        for x in range(4, 12):
            bar_x, bar_y = x in (6, 9), y in (6, 9)
            if bar_x or bar_y:
                cv.set(x, y, WARM_IRON[4] if (bar_x and bar_y) else WARM_IRON[2])
                continue
            d = np.hypot(x - 7.5, y - 7.5) / 5.0
            v = 8.4 - d * 5.2 + (rng.random() - 0.5) * 0.6
            cv.set(x, y, ramp_at(EMBER, v, x, y))
    # the upper-left lip of the bezel shades the glow a little
    for k in range(4, 12):
        if k not in (6, 9):
            cv.set(k, 4, ramp_at(EMBER, 3.6 + (0.8 if 6 < k < 9 else 0), k, 4))
            cv.set(4, k, ramp_at(EMBER, 3.6 + (0.8 if 6 < k < 9 else 0), 4, k))
    return cv


def tex_generator_pipe() -> Canvas:
    rng = rng_for("generator_pipe")
    # dark recess behind the pipe
    idx = 1.3 + (fbm(rng) - 0.5) * 1.0 + (rng.random((16, 16)) - 0.5) * 0.7
    cv = from_index_map(idx, IRON)
    for y in range(16):
        cv.set(13, y, IRON[0])  # cast shadow right of the pipe
        cv.set(14, y, ramp_at(IRON, 0.6 + (rng.random() * 0.8), 14, y))
    # cylinder shading across the pipe (x = 3..12), light from upper-left-front
    lx, lz = -0.55, 0.83

    def cyl(x, x0, x1, boost=0.0):
        c, r = (x0 + x1) / 2.0, (x1 - x0 + 1) / 2.0
        nx = (x - c) / r
        nz = np.sqrt(max(0.0, 1 - nx * nx))
        lum = max(0.0, nx * lx + nz * lz)
        return 0.5 + lum * 2.0 + lum ** 10 * 1.9 - abs(nx) ** 4 * 2.0 + boost

    tarnish = periodic_noise(rng, 16, 4) * 0.45 + periodic_noise(rng, 16, 8) * 0.55
    for y in range(16):
        for x in range(3, 13):
            v = cyl(x, 3, 12) + (rng.random() - 0.5) * 0.8
            if x == 5 and rng.random() < 0.55:
                v += 1.2  # broken specular streak
            near = 1.0 - min(abs(y - 7.5), 8) / 8.0  # verdigris gathers around the coupling
            t = tarnish[y, x] + near * 0.25
            if t > 0.66 and 4 <= x <= 11:
                cv.set(x, y, ramp_at(VERDI, v * 0.7 - 0.3, x, y))
            elif t < 0.25:
                cv.set(x, y, ramp_at(COPPER, v * 0.62, x, y))
            else:
                cv.set(x, y, ramp_at(BRASS, v, x, y))
    # coupling flange y = 6..9, one pixel wider on each side
    for y in range(6, 10):
        for x in range(2, 14):
            v = cyl(x, 2, 13, boost=0.6) + {6: 1.2, 7: 0.2, 8: -0.3, 9: -1.4}[y]
            v += (rng.random() - 0.5) * 0.5
            cv.set(x, y, ramp_at(BRASS, v, x, y))
    for bx in (4, 10):
        cv.set(bx, 7, BRASS[6])
        cv.set(bx, 8, BRASS[1])
    cv.set(7, 7, BRASS[5])
    cv.set(7, 8, BRASS[1])
    # green drips under the flange, frost on its top ledge
    for x, length in [(5, 3), (9, 2), (11, 4)]:
        for k in range(length):
            cv.set(x, 10 + k, VERDI[2 - min(k, 2)])
    for x, f in [(3, 2), (4, 1), (9, 1), (12, 1)]:
        cv.set(x, 6, FROST[f])
    return cv


GRATE_BARS_X = (0, 3, 6, 9, 12, 15)
GRATE_BARS_Y = (0, 5, 10, 15)


def grate_cell(x, y):
    return x not in GRATE_BARS_X and y not in GRATE_BARS_Y


def tex_generator_grate(lit: bool) -> Canvas:
    rng = rng_for("generator_grate")  # same stream for both -> identical metal noise
    metal = plate_base(rng, base=3.2, spread=1.2, grain=0.8)
    flame = periodic_noise(rng, 16, 4)
    flick = rng.random((16, 16))
    ash = rng.random((16, 16))
    cv = Canvas(16, 16, fill=(0, 0, 0))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if grate_cell(x, y):
                if lit:
                    heat = 0.15 + (y / 15.0) ** 1.4 * 0.75 + (flame[y, x] - 0.5) * 0.45
                    heat += (flick[y, x] - 0.5) * 0.18
                    cv.set(x, y, ramp_at(EMBER, 1.0 + heat * 7.6, x, y))
                else:
                    v = 0.6 + (flick[y, x] - 0.5) * 1.2
                    col = ramp_at(SOOT, v, x, y)
                    if y >= 12 and ash[y, x] < 0.25 + (y - 12) * 0.2:
                        col = ASH[0] if ash[y, x] < 0.25 else ASH[1] if ash[y, x] < 0.5 else ASH[2]
                    cv.set(x, y, col)
            else:
                v = metal[y, x]
                if edge:
                    v += 1.2 if (y == 0 or x == 0) else -1.0
                if lit:
                    cv.set(x, y, ramp_at(WARM_IRON, v * 0.55 - 0.2 + (0.5 if y > 7 else 0.0), x, y))
                else:
                    cv.set(x, y, ramp_at(IRON, v + 0.6, x, y))
    # bar crossings are riveted
    for bx in GRATE_BARS_X[1:-1]:
        for by in GRATE_BARS_Y[1:-1]:
            cv.set(bx, by, WARM_IRON[4] if lit else IRON[7])
    if lit:
        # glow licks the lower edge of the frame
        for k in range(1, 15):
            if grate_cell(k, 14):
                cv.set(k, 15, WARM_IRON[3])
    else:
        # cold: drop shadows inside the cells, rime on the bars
        for y in range(16):
            for x in range(16):
                if grate_cell(x, y) and (not grate_cell(x - 1, y) or not grate_cell(x, y - 1)):
                    if cv.get(x, y) not in [tuple(c) for c in ASH]:
                        cv.set(x, y, SOOT[0])
        for x, y, f in [(1, 5, 2), (2, 5, 1), (7, 10, 2), (13, 5, 1), (4, 0, 1), (11, 10, 1), (9, 3, 0)]:
            cv.set(x, y, FROST[f])
    return cv


def tex_chimney_brick() -> Canvas:
    rng = rng_for("chimney_brick")
    soot = fbm(rng, cells=(2, 4, 8), weights=(0.45, 0.35, 0.2))
    grain = rng.random((16, 16))
    brick_shade = rng.random((4, 2))
    brick_soot = rng.random((4, 2))
    cv = Canvas(16, 16, fill=(0, 0, 0))
    for y in range(16):
        band, ly = y // 4, y % 4
        shift = 0 if band % 2 == 0 else 4
        for x in range(16):
            lx = (x + 1 + shift) % 8
            if ly == 3 or lx == 0:
                v = 1.4 + (grain[y, x] - 0.5) * 1.2 - soot[y, x] * 0.9
                cv.set(x, y, ramp_at(MORTAR, v, x, y))
                continue
            k = ((x + 1 + shift) // 8) % 2
            v = 2.6 + brick_shade[band, k] * 1.6
            v += {0: 0.7, 1: 0.0, 2: -0.6}[ly]
            v += 0.4 if lx == 1 else (-0.4 if lx == 7 else 0.0)
            v += (grain[y, x] - 0.5) * 1.1
            v -= max(0.0, soot[y, x] - 0.35) * 4.0
            if brick_soot[band, k] < 0.3:
                v -= 1.4
            if v < 0.9:
                cv.set(x, y, ramp_at(SOOT, v + 1.6, x, y))
            else:
                cv.set(x, y, ramp_at(BRICK, v, x, y))
    return cv


def tex_building_core() -> Canvas:
    rng = rng_for("building_core")
    frame = plate_base(rng, base=2.9, spread=1.0, grain=0.5)
    bevel(frame, 1.6)
    cv = from_index_map(frame, IRON, dither=0.6)
    # wooden board with horizontal grain
    phase = rng.random(4) * 6.0
    grain = rng.random((16, 16))
    for y in range(2, 14):
        for x in range(2, 14):
            v = 3.2 + np.sin(y * 1.7 + phase[x // 4]) * 0.65 + (grain[y, x] - 0.5) * 0.9
            if y == 2 or x == 2:
                v -= 1.5  # the iron frame shades the board
            if y == 13 or x == 13:
                v += 0.6
            cv.set(x, y, ramp_at(WOOD, v, x, y))
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        cv.set(x, y, IRON[7])
    # brass pressure gauge, 8x8, needle swinging toward the red zone
    gauge = [
        "..BBBB..",
        ".BctrrB.",
        "BccccnrB",
        "BtccncrB",
        "BcckcccB",
        "BcccccrB",
        ".BccccB.",
        "..BBBB..",
    ]
    gx, gy = 4, 2
    for j, row in enumerate(gauge):
        for i, ch in enumerate(row):
            x, y = gx + i, gy + j
            if ch == "B":
                s_ = i + j
                cv.set(x, y, BRASS[5] if s_ <= 4 else BRASS[2] if s_ >= 10 else BRASS[3] if s_ >= 7 else BRASS[4])
            elif ch == "c":
                cv.set(x, y, CREAM[2] if i + j <= 4 else CREAM[1])
            elif ch == "r":
                cv.set(x, y, (138, 58, 44))
            elif ch == "t":
                cv.set(x, y, CREAM[0])
            elif ch == "n":
                cv.set(x, y, NEEDLE if (i, j) == (5, 2) else IRON[0])
            elif ch == "k":
                cv.set(x, y, IRON[0])
    for x in range(5, 12):  # shadow under the gauge
        if cv.get(x, 10) != (0, 0, 0):
            cv.set(x, 10, ramp_at(WOOD, 1.3, x, 10))
    # lit indicator lamp, two brass knobs and a toggle lever
    cv.set(3, 11, EMBER[7])
    cv.set(4, 11, EMBER[6])
    cv.set(3, 12, EMBER[5])
    cv.set(4, 12, EMBER[4])
    cv.set(5, 12, WOOD[1])
    cv.set(4, 13, WOOD[1])
    cv.set(5, 13, WOOD[1])
    for kx in (7, 9):
        cv.set(kx, 11, BRASS[5])
        cv.set(kx, 12, BRASS[2])
        cv.set(kx + 1, 12, WOOD[1])
    cv.set(12, 10, BRASS[6])
    cv.set(12, 11, BRASS[4])
    cv.set(11, 12, IRON[5])
    cv.set(12, 12, IRON[3])
    cv.set(13, 12, IRON[2])
    cv.set(12, 13, WOOD[1])
    return cv


def tex_frost_lamp() -> Canvas:
    rng = rng_for("frost_lamp")
    frame = plate_base(rng, base=3.0, spread=1.0, grain=0.7)
    bevel(frame, 1.6)
    cv = from_index_map(frame, IRON)
    for y in range(2, 14):
        for x in range(2, 14):
            d = np.hypot(x - 7.5, y - 7.5) / 7.2
            v = 8.6 - d ** 1.25 * 4.6 + (rng.random() - 0.5) * 0.5
            cv.set(x, y, ramp_at(EMBER, v, x, y))
    # cage bars over the glass
    for k in range(1, 15):
        for b in (5, 10):
            cv.set(b, k, WARM_IRON[2])
            cv.set(k, b, WARM_IRON[2])
    for k in range(1, 15):
        cv.set(k, 1, WARM_IRON[3] if k not in (1, 14) else IRON[3])
        cv.set(1, k, WARM_IRON[3] if k not in (1, 14) else IRON[3])
        cv.set(k, 14, WARM_IRON[1])
        cv.set(14, k, WARM_IRON[1])
    for b in (5, 10):
        for c in (5, 10):
            cv.set(b, c, WARM_IRON[4])
    # brass corner caps
    for cx, cy in [(0, 0), (14, 0), (0, 14), (14, 14)]:
        cv.set(cx, cy, BRASS[5])
        cv.set(cx + 1, cy, BRASS[4])
        cv.set(cx, cy + 1, BRASS[4])
        cv.set(cx + 1, cy + 1, BRASS[2])
    for x, y, f in [(3, 0, 2), (12, 0, 1), (0, 6, 1), (15, 9, 0), (8, 15, 1)]:
        cv.set(x, y, FROST[f])
    return cv


BLOCK_TEXTURES = {
    "generator_core": tex_generator_core,
    "generator_casing": tex_generator_casing,
    "generator_pipe": tex_generator_pipe,
    "generator_grate": lambda: tex_generator_grate(True),
    "generator_grate_off": lambda: tex_generator_grate(False),
    "chimney_brick": tex_chimney_brick,
    "building_core": tex_building_core,
    "frost_lamp": tex_frost_lamp,
}


# --------------------------------------------------------------------------------------
# Citizen skins: 64x64, modern player layout (separate left/right limbs, classic 4px arms)
# --------------------------------------------------------------------------------------

# name: (u, v, width, height, depth) -- vanilla PlayerModel box UVs
SKIN_BOXES = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "right_arm": (40, 16, 4, 12, 4), "right_sleeve": (40, 32, 4, 12, 4),
    "left_arm": (32, 48, 4, 12, 4), "left_sleeve": (48, 48, 4, 12, 4),
    "right_leg": (0, 16, 4, 12, 4), "right_pants": (0, 32, 4, 12, 4),
    "left_leg": (16, 48, 4, 12, 4), "left_pants": (0, 48, 4, 12, 4),
}
SKIN_OVERLAY = {"head": "hat", "body": "jacket", "right_arm": "right_sleeve",
                "left_arm": "left_sleeve", "right_leg": "right_pants", "left_leg": "left_pants"}
FACES = ("top", "bottom", "right", "front", "left", "back")


def box_faces(u, v, w, h, d):
    """Texture rectangles (x, y, w, h) of a box-UV cube.

    'right' is the model's right side (-X); its last column touches the front face.
    'left' is the model's left side (+X); its first column touches the front face.
    'top' shares its last row with the front face's top edge.
    """
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


COAT_CHARCOAL = [(29, 30, 34), (41, 43, 48), (55, 57, 63), (72, 75, 82)]
COAT_RUST = [(56, 29, 19), (80, 42, 26), (104, 57, 35), (128, 75, 47)]
COAT_NAVY = [(21, 26, 43), (30, 38, 61), (41, 52, 81), (57, 70, 103)]
COAT_OLIVE = [(43, 45, 25), (59, 62, 35), (77, 81, 47), (97, 102, 61)]
FUR_GREY = [(118, 114, 108), (156, 152, 144), (192, 188, 180), (224, 222, 216)]
FUR_CREAM = [(132, 116, 90), (172, 156, 124), (204, 190, 158), (230, 220, 194)]
FUR_TAN = [(98, 78, 56), (130, 104, 76), (160, 134, 100), (188, 164, 128)]
SCARF_RUST = [(82, 29, 23), (110, 41, 31), (138, 57, 41), (162, 77, 57)]
SCARF_GREY = [(52, 54, 58), (72, 74, 78), (94, 96, 100), (118, 120, 124)]
SCARF_MUSTARD = [(98, 75, 33), (126, 98, 45), (152, 122, 60), (176, 146, 82)]
SCARF_WINE = [(68, 24, 24), (92, 33, 31), (116, 45, 41), (138, 61, 55)]
SKIN_LIGHT = [(176, 128, 100), (212, 164, 132), (232, 190, 158)]
SKIN_MEDIUM = [(148, 102, 72), (182, 134, 100), (204, 158, 122)]
SKIN_DARK = [(78, 51, 37), (106, 71, 52), (132, 92, 68)]
SKIN_TAN = [(134, 90, 62), (166, 118, 84), (190, 142, 104)]
TROUSERS_BROWN = [(46, 37, 29), (62, 49, 37), (78, 62, 47)]
TROUSERS_GREY = [(37, 39, 43), (51, 53, 57), (65, 67, 71)]
TROUSERS_SLATE = [(44, 48, 56), (58, 62, 72), (74, 78, 88)]
BOOTS = [(24, 19, 15), (38, 29, 21), (56, 42, 31), (74, 56, 41)]
GLOVE_BROWN = [(50, 35, 23), (70, 49, 31), (90, 65, 41)]
GLOVE_BLACK = [(27, 27, 29), (39, 39, 43), (53, 53, 57)]
GLOVE_GREY = [(68, 68, 70), (90, 90, 92), (112, 112, 114)]
BELT = [(34, 23, 15), (50, 35, 23), (68, 49, 33)]
PATCH = [(70, 52, 34), (92, 70, 46), (114, 88, 60)]  # leather mending patches
EYE = (22, 19, 23)

CITIZENS = [
    dict(name="citizen_0", coat=COAT_CHARCOAL, fur=FUR_GREY, scarf=SCARF_RUST, skin=SKIN_LIGHT,
         cheek=(212, 132, 112), hair=(74, 50, 32), fringe=(1, 2, 3), trousers=TROUSERS_BROWN,
         gloves=GLOVE_BROWN, front="buttons", extra="scarf_tail"),
    dict(name="citizen_1", coat=COAT_RUST, fur=FUR_CREAM, scarf=SCARF_GREY, skin=SKIN_MEDIUM,
         cheek=(184, 108, 84), hair=None, fringe=(), trousers=TROUSERS_GREY,
         gloves=GLOVE_BLACK, front="fur", extra="goggles"),
    dict(name="citizen_2", coat=COAT_NAVY, fur=FUR_GREY, scarf=SCARF_MUSTARD, skin=SKIN_DARK,
         cheek=(122, 70, 56), hair=(24, 20, 20), fringe=(4, 5, 6), trousers=TROUSERS_SLATE,
         gloves=GLOVE_GREY, front="toggles", extra="satchel"),
    dict(name="citizen_3", coat=COAT_OLIVE, fur=FUR_TAN, scarf=SCARF_WINE, skin=SKIN_TAN,
         cheek=(172, 100, 76), hair=(132, 70, 34), fringe=(1, 2, 5, 6), trousers=TROUSERS_BROWN,
         gloves=GLOVE_BROWN, front="buttons", extra="patches"),
]


class SkinPainter:
    def __init__(self, cfg):
        self.c = cfg
        self.rng = rng_for(cfg["name"])
        self.cv = Canvas(64, 64)

    # -- materials -------------------------------------------------------------------
    def cloth(self, ramp, base, x, y, amt=0.45):
        return ramp_at(ramp, base + (self.rng.random() - 0.5) * 2 * amt, x, y, dither=0.0)

    def fur(self, x, y, base=2.0):
        return ramp_at(self.c["fur"], base + (self.rng.random() - 0.5) * 2.2, x, y, dither=0.0)

    def knit(self, x, y, base=2.0):
        rib = 0.45 if (x % 2 == 0) else -0.45
        return ramp_at(self.c["scarf"], base + rib + (self.rng.random() - 0.5) * 0.5, x, y, dither=0.0)

    # -- parts -----------------------------------------------------------------------
    def head(self, face, x, y, w, h, over):
        c, coat = self.c, self.c["coat"]
        if face == "bottom":
            return self.knit(x, y, 1.4)
        if face == "top":
            if y == h - 1:
                return self.fur(x, y, 2.2)
            return self.cloth(coat, 2.1 if over else 1.9, x, y)
        if y >= 6:  # scarf wrapped around the lower head
            if face == "back" and x in (5, 6):
                return self.knit(x, y, 0.9)  # knot
            return self.knit(x, y, 2.0 if y == 6 else 1.6)
        rim = (face == "front" and (y == 1 or x in (0, 7)) and y >= 1) or \
              (face == "right" and x == w - 1 and y >= 1) or (face == "left" and x == 0 and y >= 1)
        if c["extra"] == "goggles" and over and y in (0, 1):
            if face == "front":
                if y == 0:
                    return BRASS[5] if x in (1, 2, 5, 6) else BRASS[3] if x in (3, 4) else BELT[1]
                return (150, 172, 178) if x in (1, 5) else (66, 84, 92) if x in (2, 6) else \
                    BRASS[2] if x in (3, 4) else BELT[1]
            if y == 1:
                return BELT[1] if face != "back" or x not in (3, 4) else BRASS[3]
        if rim:
            return self.fur(x, y, 2.4 if over else 2.0)
        if face == "front":
            if y == 0:
                return self.cloth(coat, 2.2, x, y)
            if over:
                return None  # face opening
            return self.face_px(x, y)
        if face == "back" and x == 3:
            return self.cloth(coat, 0.9, x, y, 0.2)  # hood seam
        return self.cloth(coat, 1.9 if face == "back" else 2.0, x, y)

    def face_px(self, x, y):
        c, skin = self.c, self.c["skin"]
        if y == 2:
            if c["hair"] is not None and x in c["fringe"]:
                return c["hair"]
            return skin[0]  # in the hood's shadow
        if y == 4 and x in (2, 5):
            return EYE
        if y == 5 and x in (1, 6):
            return c["cheek"]
        if y == 3 and x in (2, 5) and c["hair"] is not None:
            return tuple(int(a * 0.55 + b * 0.45) for a, b in zip(skin[0], c["hair"]))  # brows
        return skin[2] if (y == 5 and x in (3, 4)) else skin[1]

    def body(self, face, x, y, w, h, over):
        c, coat = self.c, self.c["coat"]
        if face == "top":
            return self.knit(x, y, 1.8) if 2 <= x <= 5 else self.cloth(coat, 2.0, x, y)
        if face == "bottom":
            return self.cloth(coat, 0.6, x, y, 0.3)
        if y <= 1:
            if face == "back" and x in (5, 6):
                return self.knit(x, y, 1.0)
            return self.knit(x, y, 2.1 if y == 0 else 1.7)
        if y == 6:
            if face == "front" and x in (3, 4):
                return BRASS[5] if x == 3 else BRASS[3]
            return self.cloth(BELT, 1.2, x, y, 0.3)
        if over and not (y >= 10 or (face == "front" and c["front"] == "fur" and x in (3, 4))):
            return None  # jacket layer: collar, belt and hem only
        # -- details drawn on top of plain cloth --
        if face == "front":
            px = self.body_front(x, y)
            if px is not None:
                return px
        if face == "back":
            if c["extra"] == "scarf_tail" and x in (5, 6) and y <= 4:
                return self.knit(x, y, 1.3) if y < 4 else (self.c["scarf"][1] if x == 5 else self.c["scarf"][2])
            if c["extra"] == "satchel" and (x + y) in (8, 9) and y <= 7:
                return self.cloth(BELT, 1.6 if (x + y) == 8 else 0.9, x, y, 0.2)
            if c["extra"] == "patches" and 1 <= x <= 3 and 3 <= y <= 4:
                return self.cloth(PATCH, 1.6, x, y, 0.3) if (x, y) != (1, 3) else PATCH[2]
            if x == 4 and y >= 8:
                return coat[0]  # rear vent
            if y == 2:
                return self.cloth(coat, 1.2, x, y, 0.2)  # yoke seam
        if face == "left" and c["extra"] == "satchel" and 7 <= y <= 10:
            if y == 7:
                return BELT[2]
            return BRASS[4] if (x, y) == (1, 8) else self.cloth(BELT, 1.6, x, y, 0.4)
        edge = face in ("right", "left") or (face in ("front", "back") and x in (0, w - 1))
        return self.cloth(coat, 1.6 if edge else 2.0, x, y)

    def body_front(self, x, y):
        c, coat = self.c, self.c["coat"]
        if c["extra"] == "scarf_tail" and x in (1, 2) and 2 <= y <= 5:
            if y == 5:
                return c["scarf"][1] if x == 1 else c["scarf"][2]
            return self.knit(x, y, 1.8)
        if c["extra"] == "satchel" and (x - y) in (-1, 0) and 1 <= y <= 9:
            return self.cloth(BELT, 1.6 if (x - y) == 0 else 0.9, x, y, 0.2)
        if c["front"] == "fur" and x in (3, 4):
            return self.fur(x, y, 1.9)
        if c["front"] == "buttons":
            if x == 4:
                return coat[0]
            if x in (2, 5) and y in (3, 5, 8):
                return BRASS[5] if x == 2 else BRASS[4]
        if c["front"] == "toggles":
            if x == 4 and y >= 2:
                return coat[0]
            if x in (3, 4) and y in (3, 5, 8):
                return (206, 194, 162) if x == 3 else (164, 152, 124)
        if c["extra"] == "patches" and x in (5, 6) and y in (8, 9):
            return self.cloth(PATCH, 1.5, x, y, 0.3) if (x, y) != (5, 8) else PATCH[2]
        if y in (8,) and x in (1, 2, 6) and c["front"] != "buttons":
            return self.cloth(coat, 1.0, x, y, 0.2)  # pocket flap
        return None

    def arm(self, face, x, y, w, h, over, side):
        c, coat = self.c, self.c["coat"]
        inner = (face == "left") if side == "right" else (face == "right")
        if face == "top":
            return self.cloth(coat, 2.2 if over else 2.0, x, y)
        if face == "bottom":
            return self.cloth(c["gloves"], 0.6, x, y, 0.3)
        if 7 <= y <= 8:
            return self.fur(x, y, 2.3 if y == 7 else 1.8)
        if y >= 9:
            base = 1.6 if not inner else 1.1
            if y == 11:
                base -= 0.6
            return self.cloth(c["gloves"], base, x, y, 0.35)
        if over and y >= 2:
            return None
        if c["extra"] == "patches" and face == "back" and 4 <= y <= 5 and 1 <= x <= 2:
            return self.cloth(PATCH, 1.4, x, y, 0.3)
        if y == 0:
            return self.cloth(coat, 1.4, x, y, 0.2)
        return self.cloth(coat, 1.4 if inner else (1.8 if face == "back" else 2.1), x, y)

    def leg(self, face, x, y, w, h, over, side):
        c, coat = self.c, self.c["coat"]
        inner = (face == "left") if side == "right" else (face == "right")
        if face == "top":
            return self.cloth(coat, 1.6, x, y)
        if face == "bottom":
            return BOOTS[0]
        if y <= 3:  # skirt of the long coat
            if y == 3:
                return self.cloth(coat, 1.0, x, y, 0.2)
            return self.cloth(coat, 1.4 if inner else 2.0, x, y)
        if y <= 6:
            if over:
                return None
            return self.cloth(c["trousers"], 0.9 if inner else 1.4, x, y, 0.4)
        if y == 7:
            return self.fur(x, y, 1.9)
        if y == 11:
            return BOOTS[0] if (x + y) % 3 else BOOTS[1]
        if face == "front" and y in (8, 9) and x in (1, 2):
            return BOOTS[3] if (x + y) % 2 == 0 else BOOTS[1]  # laces
        return self.cloth(BOOTS, 1.8 if not inner else 1.4, x, y, 0.4)

    # -- driver ----------------------------------------------------------------------
    def paint(self) -> Canvas:
        for base, over in SKIN_OVERLAY.items():
            for part, is_over in ((base, False), (over, True)):
                u, v, w, h, d = SKIN_BOXES[part]
                for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
                    for y in range(fh):
                        for x in range(fw):
                            col = self.part_px(base, face, x, y, fw, fh, is_over)
                            if col is not None:
                                self.cv.set(fx + x, fy + y, col)
        return self.cv

    def part_px(self, base, face, x, y, w, h, over):
        if base == "head":
            return self.head(face, x, y, w, h, over)
        if base == "body":
            return self.body(face, x, y, w, h, over)
        if base.endswith("arm"):
            return self.arm(face, x, y, w, h, over, base.split("_")[0])
        return self.leg(face, x, y, w, h, over, base.split("_")[0])


def check_skin(cv: Canvas, name: str):
    """Every face of every base box must be fully opaque; overlays must not be empty."""
    for base, over in SKIN_OVERLAY.items():
        for part, need_full in ((base, True), (over, False)):
            for face, (fx, fy, fw, fh) in box_faces(*SKIN_BOXES[part]).items():
                a = cv.px[fy:fy + fh, fx:fx + fw, 3]
                if need_full and not (a == 255).all():
                    raise SystemExit(f"{name}: {part}.{face} has transparent pixels")
                if not need_full and not (a == 255).any():
                    raise SystemExit(f"{name}: overlay {part}.{face} is empty")


# --------------------------------------------------------------------------------------
# GUI icons (16x16, transparent background, 1px dark outline, light from the top-left)
# --------------------------------------------------------------------------------------
OUTLINE = (14, 12, 16)
I_IRON = [(46, 50, 58), (78, 84, 94), (118, 126, 138), (164, 172, 184), (212, 218, 226)]
I_COAL = [(28, 28, 33), (46, 47, 55), (70, 72, 83), (106, 111, 126), (160, 168, 186)]
I_BRASS = [(96, 68, 32), (142, 106, 52), (190, 150, 80), (228, 196, 128)]
I_EMBER = [(150, 46, 14), (214, 92, 22), (248, 150, 40), (255, 206, 96), (255, 246, 200)]
I_FROST = [(98, 116, 138), (146, 166, 188), (198, 214, 228), (240, 246, 252)]
I_WOOD = [(70, 43, 26), (106, 68, 38), (146, 98, 56), (188, 140, 88), (222, 186, 130)]
I_RED = [(112, 28, 24), (166, 44, 34), (210, 76, 58), (238, 128, 104)]
I_SICK = [(88, 112, 56), (128, 156, 84), (168, 194, 118), (206, 224, 160)]
I_ICE = [(40, 70, 122), (70, 112, 172), (124, 170, 220), (190, 224, 248)]
I_TEAL = [(30, 104, 116), (52, 150, 160), (110, 200, 200), (190, 240, 238)]
I_PAPER = [(150, 128, 88), (198, 180, 136), (232, 220, 186), (248, 242, 222)]
I_MEAT = [(96, 44, 26), (140, 70, 38), (184, 104, 58), (222, 146, 92)]
I_BONE = [(170, 160, 140), (214, 206, 188), (246, 242, 230)]
I_AMBER = [(92, 48, 18), (138, 76, 28), (186, 112, 44), (230, 170, 96)]
I_GREY = [(72, 76, 86), (108, 114, 126), (150, 158, 170), (196, 202, 212)]


def mask_empty():
    return np.zeros((16, 16), bool)


def mask_disc(cx, cy, r):
    yy, xx = np.mgrid[0:16, 0:16]
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def mask_poly(pts):
    """Pixel-centre point-in-polygon test (even-odd)."""
    m = mask_empty()
    n = len(pts)
    for y in range(16):
        for x in range(16):
            px, py, inside = x + 0.5, y + 0.5, False
            for i in range(n):
                x1, y1 = pts[i]
                x2, y2 = pts[(i + 1) % n]
                if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                    inside = not inside
            m[y, x] = inside
    return m


def mask_rows(rows, ch=None):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    m = mask_empty()
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            m[y, x] = (c != ".") if ch is None else (c in ch)
    return m


def light_t(mask):
    """0..1 per pixel: 1 at edges facing the top-left light, 0 at edges facing away."""
    t = np.zeros((16, 16))
    for y, x in zip(*np.nonzero(mask)):
        acc = 0.0
        for dx, dy in ((-1, -1), (0, -1), (-1, 0)):
            a = b = 0
            xx, yy = x, y
            while 0 <= xx < 16 and 0 <= yy < 16 and mask[yy, xx]:
                a += 1
                xx += dx
                yy += dy
            xx, yy = x, y
            while 0 <= xx < 16 and 0 <= yy < 16 and mask[yy, xx]:
                b += 1
                xx -= dx
                yy -= dy
            acc += b / (a + b)
        t[y, x] = acc / 3.0
    return t


def shade_fill(cv, mask, ramp, bias=0.0, scale=1.0):
    t = light_t(mask)
    n = len(ramp)
    for y, x in zip(*np.nonzero(mask)):
        cv.set(x, y, ramp[clamp(int((t[y, x] - 0.5) * scale * n + n / 2 + bias), 0, n - 1)])


def flat_fill(cv, mask, col):
    for y, x in zip(*np.nonzero(mask)):
        cv.set(x, y, col)


def stamp(cv, rows, legend, x0=0, y0=0):
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch != ".":
                cv.set(x0 + i, y0 + j, legend[ch])


def grow(mask, diag=False):
    p = np.pad(mask, 1)
    out = mask.copy()
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
    for dx, dy in dirs:
        out |= p[1 + dy:17 + dy, 1 + dx:17 + dx]
    return out


def cut_outline(cv, mask):
    """Dark separation line around `mask` wherever it overlaps already-painted pixels."""
    ring = grow(mask) & ~mask & (cv.px[:, :, 3] > 0)
    for y, x in zip(*np.nonzero(ring)):
        cv.set(x, y, OUTLINE)


def outline(cv, diag=False):
    a = cv.px[:, :, 3] > 0
    ring = grow(a, diag) & ~a
    for y, x in zip(*np.nonzero(ring)):
        cv.set(x, y, OUTLINE)
    return cv


def line_px(x0, y0, x1, y1):
    pts, dx, dy = [], abs(x1 - x0), -abs(y1 - y0)
    sx, sy, err = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1), abs(x1 - x0) - abs(y1 - y0)
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def icon_coal():
    cv = Canvas(16, 16)
    lumps = [
        [(1.5, 7), (3.5, 3.5), (6.5, 2.5), (8.5, 5), (8, 9.5), (2.5, 10)],
        [(7.5, 5), (10, 2), (13.5, 3.5), (14.5, 7.5), (13, 10), (8, 9.5)],
        [(2.5, 11.5), (5, 7.5), (9.5, 6.5), (13, 9), (13.5, 12.5), (10.5, 14.5), (4.5, 14.5)],
    ]
    for pts in lumps:
        m = mask_poly(pts)
        cut_outline(cv, m)
        shade_fill(cv, m, I_COAL, bias=-0.3, scale=1.3)
    for x, y in [(4, 4), (5, 4), (10, 3), (6, 9), (7, 9), (5, 10)]:
        cv.set(x, y, I_COAL[4])
    for x, y in [(3, 5), (11, 4), (8, 8)]:
        cv.set(x, y, I_COAL[3])
    return outline(cv)


def icon_wood():
    cv = Canvas(16, 16)
    for cx, cy in [(4.5, 10.5), (10.5, 10.5), (7.5, 5.5)]:
        disc = mask_disc(cx, cy, 3.3)
        cut_outline(cv, disc)
        inner = mask_disc(cx, cy, 2.3)
        ring = mask_disc(cx, cy, 1.3)
        shade_fill(cv, disc & ~inner, I_WOOD[:3], scale=1.2)
        shade_fill(cv, inner, I_WOOD[3:], scale=1.4)
        for y, x in zip(*np.nonzero(ring & ~mask_disc(cx, cy, 0.6))):
            cv.set(x, y, I_WOOD[2])
        cv.set(int(cx), int(cy), I_WOOD[2])
    return outline(cv)


def icon_iron():
    cv = Canvas(16, 16)
    bar = [".3333.",
           "344443",
           "222221",
           "222221",
           "111110"]
    leg = {str(i): c for i, c in enumerate(I_IRON)}
    for x0, y0 in [(1, 9), (9, 9), (5, 4)]:
        m = mask_empty()
        m[y0:y0 + 5, x0:x0 + 6] = True
        m[y0, x0] = m[y0, x0 + 5] = False
        cut_outline(cv, m)
        stamp(cv, bar, leg, x0, y0)
        cv.set(x0 + 1, y0 + 1, I_IRON[4])
        cv.set(x0 + 2, y0 + 1, I_IRON[4])
    return outline(cv)


def icon_food():
    cv = Canvas(16, 16)
    bone = mask_empty()
    for x, y in line_px(8, 8, 11, 11):
        bone[y, x] = bone[y + 1, x] = True
    bone |= mask_disc(13.4, 11.4, 1.25) | mask_disc(11.4, 13.4, 1.25) | mask_disc(12.2, 12.2, 1.0)
    shade_fill(cv, bone, I_BONE, scale=1.2)
    meat = mask_disc(6.3, 6.2, 4.6) | mask_disc(8.6, 8.6, 2.4)
    cut_outline(cv, meat)
    shade_fill(cv, meat, I_MEAT, scale=1.3)
    for x, y in [(4, 4), (5, 3), (3, 5)]:
        cv.set(x, y, I_MEAT[3])
    for x, y in [(6, 7), (8, 6), (5, 9)]:
        cv.set(x, y, I_MEAT[1])  # roast marks
    return outline(cv)


def icon_medicine():
    cv = Canvas(16, 16)
    rows = [
        "................",
        "......cCCc......",
        "......cccc......",
        ".......gg.......",
        "......gGGg......",
        ".....gGHGGg.....",
        "....gHqqqqGg....",
        "....gHqRRqGg....",
        "....gqRRRRqg....",
        "....gqRRRRqg....",
        "....gHqRRqGg....",
        "....gHqqqqGg....",
        "....gHGGGGGg....",
        ".....gggggg.....",
        "................",
        "................",
    ]
    stamp(cv, rows, {"c": I_WOOD[1], "C": I_WOOD[3], "g": I_AMBER[0], "G": I_AMBER[2],
                     "H": I_AMBER[3], "q": I_PAPER[3], "R": I_RED[1]})
    return outline(cv)


def icon_steam_core():
    cv = Canvas(16, 16)
    pts = [(7.5, 0.6), (12, 4.5), (12, 10.5), (7.5, 15.0), (3, 10.5), (3, 4.5)]
    m = mask_poly(pts)
    for y, x in zip(*np.nonzero(m)):
        top = y < 5
        bot = y > 10
        if x <= 5:
            k = 3 if top else (1 if bot else 2)
        elif x <= 9:
            k = 4 if top else (2 if bot else 3)
        else:
            k = 1 if top else (0 if bot else 1)
        cv.set(x, y, I_EMBER[k])
    for y in range(3, 12):
        cv.set(7, y, I_EMBER[4])  # white-hot core seam
    for x in range(3, 13):  # brass collar
        cv.set(x, 7, I_BRASS[3] if x < 7 else I_BRASS[2])
        cv.set(x, 8, I_BRASS[1] if x < 10 else I_BRASS[0])
    cv.set(5, 7, I_BRASS[3])
    return outline(cv)


def icon_population():
    cv = Canvas(16, 16)
    back = [
        "................",
        "................",
        "..bbb......bbb..",
        ".bbbbb....bbbbb.",
        ".bbbbb....bbbbb.",
        "..bbb......bbb..",
        "................",
        "..bbb......bbb..",
        ".bbbbb....bbbbb.",
        ".bbbbb....bbbbb.",
        ".bbbbb....bbbbb.",
        ".bbbbb....bbbbb.",
        ".bbbbb....bbbbb.",
        "................",
        "................",
        "................",
    ]
    front = [
        "................",
        "................",
        "................",
        "................",
        "......aaaa......",
        ".....aaaaaa.....",
        ".....aaaaaa.....",
        ".....aaaaaa.....",
        "......aaaa......",
        "................",
        ".....aaaaaa.....",
        "....aaaaaaaa....",
        "....aaaaaaaa....",
        "....aaaaaaaa....",
        "....aaaaaaaa....",
        "................",
    ]
    mb = mask_rows(back)
    shade_fill(cv, mb, I_GREY[:3], scale=1.2)
    mf = mask_rows(front)
    cut_outline(cv, mf)
    shade_fill(cv, mf, I_FROST[1:], scale=1.2)
    return outline(cv)


def icon_sick():
    cv = Canvas(16, 16)
    face = mask_disc(6.8, 7.6, 5.7)
    shade_fill(cv, face, I_SICK, scale=1.2)
    stamp(cv, [
        "....kk..kk......",
        "................",
        "...kkk..kkk.....",
    ], {"k": OUTLINE}, 0, 5)
    therm = mask_empty()
    therm[10, 8:15] = True
    therm[11, 7:14] = True
    cut_outline(cv, therm)
    for x in range(8, 15):
        cv.set(x, 10, I_FROST[3] if x < 14 else I_FROST[2])
    for x in range(7, 14):
        cv.set(x, 11, I_RED[1] if x < 10 else I_FROST[2])
    cv.set(7, 11, I_RED[2])
    for x, y in [(11, 3), (11, 4), (12, 4)]:
        cv.set(x, y, I_ICE[3] if y == 3 else I_ICE[2])  # cold sweat
    return outline(cv)


def icon_hope():
    cv = Canvas(16, 16)
    heart = mask_rows([
        "................",
        "................",
        "...oooo..oooo...",
        "..oooooooooooo..",
        ".oooooooooooooo.",
        ".oooooooooooooo.",
        ".oooooooooooooo.",
        ".oooooooooooooo.",
        "..oooooooooooo..",
        "...oooooooooo...",
        "....oooooooo....",
        ".....oooooo.....",
        "......oooo......",
        ".......oo.......",
        "................",
        "................",
    ])
    shade_fill(cv, heart, I_EMBER[:4], scale=1.25)
    for x, y in [(3, 4), (4, 4), (3, 5)]:
        cv.set(x, y, I_EMBER[4])
    return outline(cv)


def icon_discontent():
    cv = Canvas(16, 16)
    face = mask_disc(7.5, 7.8, 6.0)
    shade_fill(cv, face, I_RED, scale=1.2)
    stamp(cv, [
        "...kk......kk...",
        ".....k....k.....",
        "................",
        ".....k....k.....",
        ".....k....k.....",
        "................",
        "......kkkk......",
        ".....k....k.....",
    ], {"k": OUTLINE}, 0, 4)
    return outline(cv)


def icon_temperature():
    cv = Canvas(16, 16)
    rows = [
        "................",
        ".......ww.......",
        "......wnnw..t...",
        "......wnnw......",
        "......wnnw..t...",
        "......wrrw......",
        "......wrrw..t...",
        "......wrrw......",
        "......wrrw..t...",
        "......wrrw......",
        ".....wrrrrw.....",
        "....wrRrrrrw....",
        "....wRRrrrrw....",
        "....wrrrrrrw....",
        ".....wwwwww.....",
        "................",
    ]
    stamp(cv, rows, {"w": I_FROST[2], "n": I_FROST[0], "r": I_RED[1], "R": I_RED[3], "t": I_FROST[3]})
    for y in range(2, 10):
        cv.set(6, y, I_FROST[3])
    return outline(cv)


def icon_generator():
    cv = Canvas(16, 16)
    body = mask_empty()
    body[5:14, 3:13] = True
    body[4, 4:12] = True
    lx = -0.6

    def cyl(x):
        nx = (x - 7.5) / 5.0
        return max(0.0, nx * lx + np.sqrt(max(0.0, 1 - nx * nx)) * 0.8)

    for y, x in zip(*np.nonzero(body)):
        cv.set(x, y, I_IRON[clamp(int(cyl(x) * 4.2), 0, 4)])
    for x in range(3, 13):
        for y in (5, 12):
            cv.set(x, y, I_BRASS[clamp(int(cyl(x) * 3.6), 0, 3)])
    for x in range(4, 12):
        cv.set(x, 4, I_IRON[clamp(int(cyl(x) * 4.2) + 1, 0, 4)])
    chim = mask_empty()
    chim[1:4, 7:9] = True
    chim[1, 6:10] = True
    for y, x in zip(*np.nonzero(chim)):
        cv.set(x, y, I_IRON[3] if x <= 7 else I_IRON[1])
    for y in range(7, 11):
        for x in range(6, 10):
            cv.set(x, y, I_EMBER[3 if 7 <= x <= 8 and y >= 8 else 2 if y >= 8 else 1])
    cv.set(7, 9, I_EMBER[4])
    cv.set(8, 9, I_EMBER[4])
    for x in range(6, 10):
        cv.set(x, 6, OUTLINE)
        cv.set(x, 11, OUTLINE)
    for y in range(7, 11):
        cv.set(5, y, OUTLINE)
        cv.set(10, y, OUTLINE)
    for y in (8, 9):  # steam pipes feeding the sides
        cv.set(1, y, I_BRASS[2] if y == 8 else I_BRASS[1])
        cv.set(14, y, I_BRASS[1] if y == 8 else I_BRASS[0])
    cv.set(1, 10, I_BRASS[1])
    cv.set(1, 11, I_BRASS[1])
    cv.set(14, 10, I_BRASS[0])
    cv.set(14, 11, I_BRASS[0])
    cv.set(2, 8, OUTLINE)
    cv.set(13, 8, OUTLINE)
    return outline(cv)


def icon_research():
    cv = Canvas(16, 16)
    rows = [
        "................",
        "......wWWw......",
        "......wggw......",
        "......wggw......",
        "......wggw......",
        ".....wggggw.....",
        ".....wgguggw....",
        "....wggggggw....",
        "....wTTTTTTw....",
        "...wttuttttw....",
        "...wttttttutw...",
        "..wtttuttttttw..",
        "..wttttttutttw..",
        "..wwwwwwwwwwww..",
        "................",
        "................",
    ]
    stamp(cv, rows, {"w": I_FROST[2], "W": I_FROST[3], "g": (70, 84, 100), "T": I_TEAL[3],
                     "t": I_TEAL[1], "u": I_TEAL[3]})
    return outline(cv)


def icon_expedition():
    cv = Canvas(16, 16)
    mtn = mask_poly([(0.8, 14.6), (6.5, 4.6), (8.5, 6.5), (10.5, 5.6), (15.2, 14.6)])
    for y, x in zip(*np.nonzero(mtn)):
        left = (x + 0.5) < 6.5 + (y - 4.6) * 0.12
        snow = y <= 6 + (1 if x % 3 == 0 else 0) or (y <= 7 and 9 <= x <= 11)
        if snow:
            cv.set(x, y, I_FROST[3] if left else I_FROST[2])
        else:
            cv.set(x, y, I_GREY[2] if left else I_GREY[1] if y < 12 else I_GREY[0])
    for y in range(1, 5):
        cv.set(6, y, I_WOOD[1])
    stamp(cv, ["OOOo", "OOo.", "Oo.."], {"O": I_EMBER[2], "o": I_EMBER[1]}, 7, 1)
    return outline(cv)


def icon_law():
    cv = Canvas(16, 16)
    rows = [
        "................",
        "................",
        "..dCCCCCCCCCCd..",
        "..dcccccccccdd..",
        "...qqqqqqqqqq...",
        "...qkkkkkkkqq...",
        "...qqqqqqqqqq...",
        "...qkkkkkkqqq...",
        "...qqqqqqqqqq...",
        "...qkkkkkqqqq...",
        "...qqqqqqrrqq...",
        "...qqqqqrRRrq...",
        "..dCCCCCrrrrCd..",
        "..dcccccccrcdd..",
        "................",
        "................",
    ]
    stamp(cv, rows, {"d": I_PAPER[0], "C": I_PAPER[2], "c": I_PAPER[1], "q": I_PAPER[3],
                     "k": I_PAPER[0], "r": I_RED[1], "R": I_RED[3]})
    return outline(cv)


def icon_build():
    """Claw hammer at 45 degrees, built on diagonal coordinates s = x + y, d = x - y."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            sd, dd = x + y, x - y
            if dd in (0, 1) and 15 <= sd <= 26:  # handle
                col = I_BRASS[2 if dd == 0 else 1] if sd <= 16 else I_WOOD[3 if dd == 0 else 1]
                cv.set(x, y, col)
    for y in range(16):
        for x in range(16):
            sd, dd = x + y, x - y
            if 9 <= sd <= 14 and -5 <= dd <= 6:  # head
                if dd <= -4 and sd in (11, 12):
                    continue  # claw notch
                k = {9: 4, 10: 3, 11: 3, 12: 2, 13: 2, 14: 1}[sd]
                if dd >= 5:
                    k = max(k - 1, 1)  # flat striking face
                cv.set(x, y, I_IRON[k])
    return outline(cv)


def icon_warning():
    cv = Canvas(16, 16)
    tri = mask_poly([(7.0, 0.9), (9.0, 0.9), (15.0, 14.2), (1.0, 14.2)])
    shade_fill(cv, tri, I_EMBER[1:4], scale=1.2)
    for y in range(5, 10):
        cv.set(7, y, OUTLINE)
        cv.set(8, y, OUTLINE)
    for y in (11, 12):
        cv.set(7, y, OUTLINE)
        cv.set(8, y, OUTLINE)
    return outline(cv)


def icon_weather_clear():
    cv = Canvas(16, 16)
    sun = [(170, 120, 52), (226, 174, 82), (248, 214, 128), (255, 240, 190)]
    shade_fill(cv, mask_disc(7.5, 7.5, 3.7), sun, scale=1.2)
    rays = [(7, 1), (8, 1), (7, 2), (8, 2), (7, 13), (8, 13), (7, 14), (8, 14),
            (1, 7), (1, 8), (2, 7), (2, 8), (13, 7), (13, 8), (14, 7), (14, 8),
            (3, 3), (2, 2), (12, 3), (13, 2), (3, 12), (2, 13), (12, 12), (13, 13)]
    for x, y in rays:
        cv.set(x, y, sun[2])
    return outline(cv)


def cloud_mask(dy=0):
    m = mask_disc(4.8, 6.6 + dy, 2.5) | mask_disc(8.3, 5.0 + dy, 3.3) | mask_disc(11.4, 6.8 + dy, 2.4)
    m[6 + dy:9 + dy, 3:13] = True
    m[9 + dy:, :] = False
    return m


def icon_weather_snow():
    cv = Canvas(16, 16)
    shade_fill(cv, cloud_mask(), I_FROST, scale=1.2)
    for cx, cy in [(4, 11), (8, 13), (12, 11)]:
        for x, y in [(cx, cy - 1), (cx - 1, cy), (cx, cy), (cx + 1, cy), (cx, cy + 1)]:
            cv.set(x, y, I_FROST[3] if (x, y) == (cx, cy) else I_FROST[2])
    return outline(cv)


def icon_weather_wind():
    cv = Canvas(16, 16)
    rows = [
        "................",
        "................",
        "..........ww....",
        "............w...",
        ".WWWWWWWWWWw....",
        "................",
        "................",
        "...WWWWWWWWWWw..",
        "..............w.",
        "............ww..",
        "................",
        ".........w......",
        ".WWWWWWWw.......",
        "................",
        "................",
        "................",
    ]
    stamp(cv, rows, {"W": I_FROST[3], "w": I_FROST[2]})
    return outline(cv)


def icon_weather_blizzard():
    cv = Canvas(16, 16)
    shade_fill(cv, cloud_mask(-1), I_GREY, scale=1.2)
    rows = [
        "......W.....W...",
        ".....W.....W....",
        "....W..W..W.....",
        "......WWW.......",
        ".WWWWWWW........",
        "...........W....",
    ]
    stamp(cv, rows, {"W": I_FROST[3]}, 0, 9)
    for x in (2, 4, 6):
        cv.set(x, 13, I_FROST[2])
    return outline(cv)


def icon_weather_extreme():
    cv = Canvas(16, 16)
    half = [
        "................",
        ".....j.JJ.j.....",
        "..J...jJJj...J..",
        "...J...JJ...J...",
        "....J..JJ..J....",
        ".j...J.JJ.J...j.",
        "..j...JJJJ...j..",
        ".JJJJJJWWJJJJJJ.",
    ]
    rows = half + half[::-1]
    stamp(cv, rows, {"J": I_ICE[3], "j": I_ICE[2], "W": (255, 255, 255)})
    return outline(cv, diag=True)


def icon_weather_great_wave():
    cv = Canvas(16, 16)
    body = mask_disc(8.0, 8.4, 6.6) & ~mask_disc(6.6, 9.0, 2.7)
    body &= ~((np.mgrid[0:16, 0:16][0] > 9) & (np.mgrid[0:16, 0:16][1] < 7))
    base = mask_empty()
    base[12:15, 1:15] = True
    m = body | base
    t = light_t(m)
    yy, xx = np.mgrid[0:16, 0:16]
    for y, x in zip(*np.nonzero(m)):
        r = np.hypot(x - 8.0, y - 8.4)
        if y <= 8 and r >= 5.6:
            col = I_FROST[3]  # foam along the crest
        elif y <= 8 and r >= 4.6:
            col = I_ICE[3]
        else:
            col = I_ICE[clamp(int(t[y, x] * 3.2), 0, 2)]
        cv.set(x, y, col)
    for x, y in [(2, 6), (1, 8), (3, 9)]:
        cv.set(x, y, I_FROST[3])  # spray
    return outline(cv)


ICONS = {
    "coal": icon_coal, "wood": icon_wood, "iron": icon_iron, "food": icon_food,
    "medicine": icon_medicine, "steam_core": icon_steam_core, "population": icon_population,
    "sick": icon_sick, "hope": icon_hope, "discontent": icon_discontent,
    "temperature": icon_temperature, "generator": icon_generator, "research": icon_research,
    "expedition": icon_expedition, "law": icon_law, "build": icon_build, "warning": icon_warning,
    "weather_clear": icon_weather_clear, "weather_snow": icon_weather_snow,
    "weather_wind": icon_weather_wind, "weather_blizzard": icon_weather_blizzard,
    "weather_extreme": icon_weather_extreme, "weather_great_wave": icon_weather_great_wave,
}


# --------------------------------------------------------------------------------------
# Models / blockstates
# --------------------------------------------------------------------------------------

def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def write_models():
    cube_all = ["generator_core", "generator_casing", "chimney_brick", "building_core", "frost_lamp"]
    for bid in cube_all:
        write_json(ASSETS / "models" / "block" / f"{bid}.json",
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{bid}"}})
        write_json(ASSETS / "blockstates" / f"{bid}.json",
                   {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
    write_json(ASSETS / "models" / "block" / "generator_pipe.json",
               {"parent": "minecraft:block/cube_column",
                "textures": {"end": f"{NS}:block/generator_casing", "side": f"{NS}:block/generator_pipe"}})
    write_json(ASSETS / "blockstates" / "generator_pipe.json",
               {"variants": {"": {"model": f"{NS}:block/generator_pipe"}}})
    for model in ("generator_grate", "generator_grate_off"):
        write_json(ASSETS / "models" / "block" / f"{model}.json",
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{model}"}})
    write_json(ASSETS / "blockstates" / "generator_grate.json",
               {"variants": {"lit=false": {"model": f"{NS}:block/generator_grate_off"},
                             "lit=true": {"model": f"{NS}:block/generator_grate"}}})
    for bid in cube_all + ["generator_pipe", "generator_grate"]:
        write_json(ASSETS / "models" / "item" / f"{bid}.json", {"parent": f"{NS}:block/{bid}"})


# --------------------------------------------------------------------------------------
# Previews (written only with --preview, outside the asset tree)
# --------------------------------------------------------------------------------------

def upscale(img: Image.Image, s: int) -> Image.Image:
    return img.resize((img.width * s, img.height * s), Image.NEAREST)


def preview_blocks(blocks: dict, out: Path):
    names = list(blocks)
    tile, s = 16, 6
    sheet = Image.new("RGBA", (len(names) * (tile * 3 * s // 2 + 8), tile * 3 * s // 2 * 2 + 16), (30, 30, 34, 255))
    x = 0
    for n in names:
        img = blocks[n].image()
        single = upscale(img, s * 3 // 2)
        sheet.paste(single, (x, 0))
        grid = Image.new("RGBA", (tile * 3, tile * 3))
        for gy in range(3):
            for gx in range(3):
                grid.paste(img, (gx * tile, gy * tile))
        sheet.paste(upscale(grid, s // 2), (x, tile * 3 * s // 2 + 8))
        x += tile * 3 * s // 2 + 8
    sheet.save(out / "blocks.png")


def skin_views(img: Image.Image) -> Image.Image:
    """Front / back / right / left paper-doll views with overlays composited (preview only)."""
    def face(part, f):
        x, y, w, h = box_faces(*SKIN_BOXES[part])[f]
        return img.crop((x, y, x + w, y + h))

    def layer(dst, part, f, pos):
        base = face(part, f)
        dst.alpha_composite(base, pos)
        dst.alpha_composite(face(SKIN_OVERLAY[part], f), pos)

    out = Image.new("RGBA", (16 * 2 + 8 * 2 + 30, 32), (0, 0, 0, 0))
    fr = Image.new("RGBA", (16, 32))
    layer(fr, "head", "front", (4, 0)); layer(fr, "body", "front", (4, 8))
    layer(fr, "right_arm", "front", (0, 8)); layer(fr, "left_arm", "front", (12, 8))
    layer(fr, "right_leg", "front", (4, 20)); layer(fr, "left_leg", "front", (8, 20))
    bk = Image.new("RGBA", (16, 32))
    layer(bk, "head", "back", (4, 0)); layer(bk, "body", "back", (4, 8))
    layer(bk, "left_arm", "back", (0, 8)); layer(bk, "right_arm", "back", (12, 8))
    layer(bk, "left_leg", "back", (4, 20)); layer(bk, "right_leg", "back", (8, 20))
    rs = Image.new("RGBA", (8, 32))
    layer(rs, "head", "right", (0, 0)); layer(rs, "body", "right", (2, 8))
    layer(rs, "right_leg", "right", (2, 20)); layer(rs, "right_arm", "right", (2, 8))
    ls = Image.new("RGBA", (8, 32))
    layer(ls, "head", "left", (0, 0)); layer(ls, "body", "left", (2, 8))
    layer(ls, "left_leg", "left", (2, 20)); layer(ls, "left_arm", "left", (2, 8))
    out.paste(fr, (0, 0)); out.paste(bk, (26, 0)); out.paste(rs, (52, 0)); out.paste(ls, (70, 0))
    return out


def preview_skins(skins: dict, out: Path):
    s = 6
    rows = []
    for name, cv in skins.items():
        img = cv.image()
        views = skin_views(img)
        row = Image.new("RGBA", (64 * s + 20 + views.width * s, 64 * s), (58, 60, 66, 255))
        flat = Image.new("RGBA", (64, 64), (90, 30, 90, 255))  # magenta shows unpainted texels
        flat.alpha_composite(img)
        row.paste(upscale(flat, s), (0, 0))
        row.alpha_composite(upscale(views, s), (64 * s + 20, 0))
        rows.append(row)
    sheet = Image.new("RGBA", (rows[0].width, sum(r.height + 10 for r in rows)), (30, 30, 34, 255))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height + 10
    sheet.save(out / "skins.png")


def preview_icons(icons: dict, out: Path, bg=(48, 48, 52, 255)):
    names = list(icons)
    cols = 8
    for s, fname in ((3, "icons_3x.png"), (6, "icons_6x.png")):
        cell = 16 * s + 12
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell + 12, rows * cell + 12), bg)
        for i, n in enumerate(names):
            img = upscale(icons[n].image(), s)
            sheet.alpha_composite(img, (12 + (i % cols) * cell, 12 + (i // cols) * cell))
        sheet.save(out / fname)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--preview", type=Path, help="write upscaled preview sheets to this directory")
    args = ap.parse_args()

    out_block = ASSETS / "textures" / "block"
    out_block.mkdir(parents=True, exist_ok=True)
    blocks = {name: fn() for name, fn in BLOCK_TEXTURES.items()}
    for name, cv in blocks.items():
        cv.image().save(out_block / f"{name}.png")
    write_models()

    out_entity = ASSETS / "textures" / "entity"
    out_entity.mkdir(parents=True, exist_ok=True)
    skins = {}
    for cfg in CITIZENS:
        cv = SkinPainter(cfg).paint()
        check_skin(cv, cfg["name"])
        cv.image().save(out_entity / f"{cfg['name']}.png")
        skins[cfg["name"]] = cv

    out_icon = ASSETS / "textures" / "gui" / "icon"
    out_icon.mkdir(parents=True, exist_ok=True)
    icons = {name: fn() for name, fn in ICONS.items()}
    for name, cv in icons.items():
        cv.image().save(out_icon / f"{name}.png")

    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        preview_blocks(blocks, args.preview)
        preview_skins(skins, args.preview)
        preview_icons(icons, args.preview)
    print(f"wrote {len(blocks)} block textures, {len(skins)} skins, {len(icons)} icons and models under {ASSETS}")


if __name__ == "__main__":
    main()
