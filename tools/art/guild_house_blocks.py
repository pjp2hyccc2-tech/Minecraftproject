#!/usr/bin/env python3
"""Art of the guild houses' own blocks and entity (docs/modules/guildhouse.md), all original pixel art:

* six roof-tile families (block, stairs, slab): weathered wooden shingles, blue-grey slate, Japanese kawara,
  straw thatch, verdigris copper sheet with standing seams, gold fish-scale tiles - textures, block/item models,
  blockstates and loot tables;
* the training dummy's three looks (straw, armoured, painted target) for TrainingDummyModel's 64 x 64 layout.

    python3 tools/art/guild_house_blocks.py      # writes the assets and tools/art/guild_house_blocks_preview.png

Deterministic (seeded per texture name).
"""
from __future__ import annotations

import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import capital_blocks as CB  # noqa: E402
from px import hexc, seed_of  # noqa: E402

ROOFS = ("shingle", "slate", "kawara", "verdigris", "gilded")


def rng(name):
    return random.Random(seed_of("guildhouse:" + name))


def rgb(h):
    c = hexc(h)
    return np.array([c[0], c[1], c[2], 255], np.float64)


def blank(w, h, color):
    a = np.zeros((h, w, 4), np.float64)
    a[:, :] = rgb(color)
    return a


def tone(a, r, amount):
    h, w = a.shape[:2]
    for y in range(h):
        for x in range(w):
            if a[y, x, 3] > 0:
                a[y, x, :3] *= 1.0 + (r.random() - 0.5) * 2 * amount


# =========================================================================== roof tiles (16 x 16)
def shingle():
    """Weathered wooden shingles: staggered rows of narrow boards, grain streaks, a shadow under each row."""
    r = rng("shingle")
    a = blank(16, 16, "#6B5440")
    for row in range(4):
        y0 = row * 4
        x = -((row % 2) * 2)
        while x < 16:
            wdt = 3 + r.randint(0, 2)
            base = rgb(r.choice(("#6E573F", "#5F4A36", "#76604A", "#665039", "#7A6650")))
            for yy in range(y0, y0 + 4):
                for xx in range(max(0, x), min(16, x + wdt)):
                    c = base.copy()
                    if yy == y0 + 3:
                        c[:3] *= 0.62                       # shadow of the row above
                    elif yy == y0:
                        c[:3] *= 1.12
                    if (xx - x) % 2 == 0 and r.random() < 0.35:
                        c[:3] *= 0.9                        # grain
                    a[yy, xx] = c
            if 0 <= x + wdt - 1 < 16:
                for yy in range(y0, y0 + 3):
                    a[yy, x + wdt - 1, :3] *= 0.72          # gap between boards
            x += wdt
    tone(a, r, 0.05)
    return a


def slate():
    """Blue-grey slates: staggered rectangles with a pale top edge and a dark lower lip, a little lichen."""
    r = rng("slate")
    a = blank(16, 16, "#424A57")
    for row in range(4):
        y0 = row * 4
        off = (row % 2) * 2
        for k in range(-1, 5):
            x0 = k * 4 + off
            base = rgb(r.choice(("#454E5C", "#3C4451", "#4B5462", "#404856", "#4E5767")))
            for yy in range(y0, y0 + 4):
                for xx in range(x0, x0 + 4):
                    if 0 <= xx < 16:
                        c = base.copy()
                        if yy == y0:
                            c[:3] *= 1.22
                        if yy == y0 + 3:
                            c[:3] *= 0.66
                        if xx == x0 + 3:
                            c[:3] *= 0.78
                        a[yy, xx] = c
    for _ in range(3):
        x, y = r.randint(0, 15), r.randint(0, 15)
        a[y, x, :3] = rgb("#7E8A63")[:3]
    tone(a, r, 0.04)
    return a


def kawara():
    """Japanese kawara: rounded channel tiles in columns (light crown, dark trough), joints every eight pixels with
    a pale lip and a shadow under it."""
    r = rng("kawara")
    a = blank(16, 16, "#3B3E44")
    col = ("#2A2C31", "#43464D", "#5C6069", "#4A4D54")
    for x in range(16):
        for y in range(16):
            c = rgb(col[x % 4])
            if y % 8 == 0:
                c[:3] *= 1.25
            elif y % 8 == 1:
                c[:3] *= 0.7
            a[y, x] = c
    tone(a, r, 0.035)
    return a


def thatch():
    """Straw thatch: layers of golden reed bound every six pixels, single strands lighter and darker."""
    r = rng("thatch")
    a = blank(16, 16, "#C29A4E")
    for x in range(16):
        shade = 0.82 + r.random() * 0.36
        for y in range(16):
            c = rgb("#C29A4E")
            c[:3] *= shade
            if r.random() < 0.18:
                c[:3] = rgb(r.choice(("#E6C77A", "#9A7432", "#D9B464")))[:3]
            if y % 6 == 5:
                c[:3] *= 0.7                                  # the binding of the layer above
            a[y, x] = c
    tone(a, r, 0.03)
    return a


def verdigris():
    """Copper sheet weathered to verdigris: standing seams every four pixels, mottled patina, bronze where it wore."""
    r = rng("verdigris")
    a = blank(16, 16, "#4FA58C")
    for y in range(16):
        for x in range(16):
            c = rgb("#4FA58C")
            n = r.random()
            if n < 0.18:
                c[:3] = rgb("#6CC2A6")[:3]
            elif n < 0.3:
                c[:3] = rgb("#3D8D77")[:3]
            elif n < 0.33:
                c[:3] = rgb("#8C6A3A")[:3]
            if x % 4 == 0:
                c[:3] = rgb("#9AE0C8")[:3]                    # the seam's crest
            elif x % 4 == 1:
                c[:3] *= 0.7                                  # its shadow
            a[y, x] = c
    return a


def gilded():
    """Gold fish-scale tiles: staggered rows of round-bottomed scales with bright rims and darker hearts."""
    r = rng("gilded")
    a = blank(16, 16, "#B8891E")
    for row in range(5):
        y0 = row * 4 - 1
        off = (row % 2) * 2
        for k in range(-1, 5):
            cx = k * 4 + off + 1.5
            for yy in range(y0, y0 + 5):
                for xx in range(int(cx) - 2, int(cx) + 3):
                    if not (0 <= xx < 16 and 0 <= yy < 16):
                        continue
                    dx = (xx - cx) / 2.0
                    dy = (yy - y0) / 4.0
                    if dx * dx + max(0.0, dy - 0.2) ** 2 * 1.6 <= 1.0:
                        c = rgb("#D9A92E")
                        edge = dx * dx + max(0.0, dy - 0.2) ** 2 * 1.6 > 0.55
                        c[:3] = rgb("#F7D667")[:3] if edge and dy > 0.5 else (rgb("#9C7418")[:3] if edge
                                                                              else c[:3])
                        if xx == int(cx) and yy == y0 + 1:
                            c[:3] = rgb("#FFF0A0")[:3]
                        a[yy, xx] = c
    tone(a, r, 0.03)
    return a


ROOF_TEXTURES = {"shingle": shingle, "slate": slate, "kawara": kawara, "verdigris": verdigris, "gilded": gilded,
                 "thatch": thatch}


# =========================================================================== the training dummy (64 x 64)
def _box_regions(u, v, w, h, d):
    """The texture rectangles of a model box (top, bottom, then right, front, left, back)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def _fill(a, region, palette, r, grain=None):
    x0, y0, w, h = region
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = rgb(r.choice(palette))
            if grain == "v" and (x - x0) % 2 == 0:
                c[:3] *= 0.9
            if grain == "h" and (y - y0) % 2 == 0:
                c[:3] *= 0.92
            a[y, x] = c


def dummy(look):
    r = rng("dummy_" + look)
    a = np.zeros((64, 64, 4), np.float64)
    burlap = ("#B89A6A", "#A88B5D", "#C4A877", "#9E8256")
    straw = ("#D9B85C", "#C9A447", "#E6CB78", "#B8913A")
    wood = ("#6E5236", "#5E452C", "#7A5C3E")
    rope = ("#8A6E45", "#7A5F3A", "#9A7C50")
    head = _box_regions(0, 0, 8, 8, 8)
    body = _box_regions(0, 16, 9, 12, 5)
    arms = _box_regions(0, 36, 22, 2, 2)
    hands = [_box_regions(48, 36, 3, 4, 3), _box_regions(48, 43, 3, 4, 3)]
    post = _box_regions(56, 0, 2, 10, 2)
    base = _box_regions(0, 48, 10, 2, 10)
    for reg in head.values():
        _fill(a, reg, burlap, r)
    for reg in body.values():
        _fill(a, reg, burlap, r, grain="h")
    for reg in arms.values():
        _fill(a, reg, wood, r, grain="v")
    for hd in hands:
        for reg in hd.values():
            _fill(a, reg, straw, r, grain="v")
    for reg in post.values():
        _fill(a, reg, wood, r, grain="v")
    for reg in base.values():
        _fill(a, reg, wood, r, grain="h")
    # straw tufts poking out of the head's top and the body's neck
    x0, y0, w, h = head["top"]
    for _ in range(14):
        a[y0 + r.randint(0, h - 1), x0 + r.randint(0, w - 1)] = rgb(r.choice(straw))
    # rope bands round the body (rows 3 and 9 of the side faces)
    for key in ("right", "front", "left", "back"):
        x0, y0, w, h = body[key]
        for yy in (y0 + 2, y0 + 9):
            for xx in range(x0, x0 + w):
                a[yy, xx] = rgb(r.choice(rope))
    # the face: stitched cross eyes and a stitched mouth
    fx, fy, _, _ = head["front"]
    stitch = rgb("#3A2A1C")
    for (dx, dy) in ((1, 2), (3, 2), (2, 3), (1, 4), (3, 4), (4, 2), (6, 2), (5, 3), (4, 4), (6, 4)):
        a[fy + dy, fx + dx] = stitch
    for dx in range(2, 6):
        a[fy + 6, fx + dx] = stitch
    for dx in (2, 4):
        a[fy + 5, fx + dx] = stitch
        a[fy + 7, fx + dx + 1] = stitch
    if look == "armoured":
        iron = ("#9EA4AB", "#8A9097", "#B4B9BF")
        for key in ("front", "back", "right", "left"):
            x0, y0, w, h = body[key]
            for y in range(y0, y0 + 8):
                for x in range(x0, x0 + w):
                    c = rgb(r.choice(iron))
                    if y == y0 + 7:
                        c[:3] *= 0.7
                    a[y, x] = c
            for x in range(x0 + 1, x0 + w, 3):
                a[y0 + 1, x] = rgb("#E4E8EC")             # rivets
        for key in ("top", "right", "left", "back"):      # a steel cap
            x0, y0, w, h = head[key]
            for y in range(y0, y0 + (h if key == "top" else 3)):
                for x in range(x0, x0 + w):
                    a[y, x] = rgb(r.choice(iron))
        x0, y0, w, h = head["front"]
        for x in range(x0, x0 + w):
            a[y0, x] = rgb("#8A9097")
            a[y0 + 1, x] = rgb("#B4B9BF")
    if look == "target":
        x0, y0, w, h = body["front"]
        cx, cy = x0 + w / 2.0 - 0.5, y0 + 5.5
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= 4.6:
                    a[y, x] = rgb("#C8302C") if int(d) % 2 == 0 else rgb("#EFE6D2")
        a[int(cy), int(cx)] = rgb("#FFD040")
        x0, y0, w, h = body["back"]
        for y in range(y0 + 3, y0 + 8):
            for x in range(x0 + 2, x0 + w - 2):
                a[y, x] = rgb("#EFE6D2") if (x + y) % 2 else rgb("#C8302C")
    a[..., 3] = np.where(a[..., 3] > 0, 255, 0)
    return a


# =========================================================================== writing everything
def main() -> int:
    sheet = []
    for name, fn in ROOF_TEXTURES.items():
        arr = fn()
        full = f"{name}_roof_tiles" if name != "thatch" else "thatch_roof"
        stem = f"{name}_roof_tile" if name != "thatch" else "thatch_roof"
        CB.save(arr, "block", f"{full}.png")
        CB.gen_full(full, full)
        CB.gen_stairs_slab(stem, full, full)
        sheet.append(arr)
    looks = []
    for look in ("straw", "armoured", "target"):
        arr = dummy(look)
        CB.save(arr, "entity", f"training_dummy_{look}.png")
        looks.append(arr)
    # preview: the six roof textures tiled 2 x 2 at 4x, then the dummy sheets at 2x
    img = Image.new("RGBA", (6 * 136 + 8, 136 + 136 + 16), (24, 24, 28, 255))
    for i, arr in enumerate(sheet):
        tile = np.tile(np.clip(arr, 0, 255).astype(np.uint8), (2, 2, 1))
        img.paste(Image.fromarray(tile, "RGBA").resize((128, 128), Image.NEAREST), (8 + i * 136, 8))
    for i, arr in enumerate(looks):
        img.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").resize((128, 128), Image.NEAREST),
                  (8 + i * 136, 144))
    img.save(os.path.join(HERE, "guild_house_blocks_preview.png"))
    print("guild house blocks: 6 roof families, 3 dummy looks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
