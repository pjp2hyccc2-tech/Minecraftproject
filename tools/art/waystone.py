#!/usr/bin/env python3
"""Art for the waystones (転移碑) of the field map: the rune monolith's stone textures (plinth, two shaft sections,
cap), the matching glow overlays of its carved runes, the floating crystal, the glyph circle on the ground, the item
icon, and the block models / blockstates.

    python3 tools/art/waystone.py      # writes assets, and tools/art/waystone_preview.png

The carvings of the stone and the glow the client draws over them come from the same glyph strokes, drawn in the
face's own proportions and then fitted to the square texture, so they line up exactly on the block.
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from px import seed_of  # noqa: E402
from town_gimmicks import ASSETS, NS, blockstate, hexrgb, model, save, shrink, write_json  # noqa: E402,F401

SS = 8
STONE = [hexrgb("#2B3039"), hexrgb("#3F4651"), hexrgb("#57606C"), hexrgb("#717B88"), hexrgb("#8F99A6")]
GROOVE = hexrgb("#14181E")
GROOVE_LIGHT = hexrgb("#8A95A3")
GLOW_CORE = (190, 250, 255)
GLOW = (70, 214, 255)
GOLD = [hexrgb("#6B4A12"), hexrgb("#A87A22"), hexrgb("#D9AC45"), hexrgb("#F5D77E")]

# Rune glyphs on a 4 x 6 grid: lists of strokes (polylines). Original shapes in the spirit of old runes.
GLYPHS = [
    [[(1, 0), (1, 6)], [(1, 1), (3, 0)], [(1, 3), (3, 2)]],
    [[(0, 6), (0, 1), (2, 0), (3, 1), (3, 6)]],
    [[(1, 0), (1, 6)], [(1, 1), (3, 3), (1, 5)]],
    [[(2, 0), (2, 6)], [(0, 2), (2, 0), (4, 2)]],
    [[(2, 0), (2, 6)], [(0, 0), (2, 2), (4, 0)]],
    [[(0, 6), (2, 3), (4, 6)], [(2, 3), (0, 1.5), (2, 0), (4, 1.5), (2, 3)]],
    [[(0, 0), (4, 6)], [(4, 0), (0, 6)], [(0, 0), (0, 6)], [(4, 0), (4, 6)]],
    [[(3, 0), (1, 2), (3, 4), (1, 6)]],
    [[(2, 1), (2, 6)], [(0, 3), (2, 1), (4, 3)], [(0, 6), (4, 6)]],
    [[(1, 0), (1, 6)], [(1, 0), (3, 1.5), (1, 3), (3, 4.5), (1, 6)]],
    [[(0, 3), (2, 0), (4, 3), (2, 6), (0, 3)], [(2, 0), (2, 6)]],
    [[(0, 0), (4, 0)], [(2, 0), (2, 6)], [(0, 6), (2, 4), (4, 6)]],
]


def rng(name: str) -> random.Random:
    return random.Random(seed_of("waystone:" + name))


def value_noise(w: int, h: int, cell: float, name: str) -> np.ndarray:
    r = np.random.default_rng(seed_of("waystone-noise:" + name))
    gw, gh = int(w / cell) + 2, int(h / cell) + 2
    grid = r.random((gh, gw))
    yy, xx = np.mgrid[0:h, 0:w] / cell
    x0, y0 = np.floor(xx).astype(int), np.floor(yy).astype(int)
    fx, fy = xx - x0, yy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = grid[y0, x0], grid[y0, x0 + 1]
    c, d = grid[y0 + 1, x0], grid[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def palette_pick(pal, t: np.ndarray) -> np.ndarray:
    t = np.clip(t, 0.0, 0.999) * (len(pal) - 1)
    i = np.floor(t).astype(int)
    f = (t - i)[..., None]
    p = np.array([c[:3] for c in pal], dtype=np.float64)
    return p[i] * (1 - f) + p[np.minimum(i + 1, len(pal) - 1)] * f


def stone_face(w: int, h: int, name: str, chamfer: float = 0.12) -> Image.Image:
    """Dressed stone in face proportions (w x h, supersampled): noise, chipped edges darker, a faint vertical tool mark."""
    n = 0.55 * value_noise(w, h, w / 3.0, name) + 0.3 * value_noise(w, h, w / 9.0, name + "f") \
        + 0.15 * value_noise(w, h, w / 26.0, name + "g")
    yy, xx = np.mgrid[0:h, 0:w]
    edge = np.minimum(np.minimum(xx, w - 1 - xx) / (w * chamfer), 1.0)
    tool = 0.04 * np.sin(xx / w * 40.0 + value_noise(w, h, w / 4.0, name + "t") * 3.0)
    speck = (value_noise(w, h, max(2.0, w / 60.0), name + "s") > 0.82) * 0.12
    t = 0.10 + 0.62 * n + tool - 0.28 * (1.0 - edge) ** 2 - speck
    rgb = palette_pick(STONE, t)
    img = np.dstack([rgb, np.full((h, w), 255.0)]).astype(np.uint8)
    return Image.fromarray(img, "RGBA")


def glyph_strokes(index: int, cx: float, top: float, cell_w: float, cell_h: float):
    """Polylines of glyph ``index`` with its 4x6 grid placed at (cx - 2 cell_w, top)."""
    out = []
    for stroke in GLYPHS[index % len(GLYPHS)]:
        out.append([(cx + (gx - 2) * cell_w, top + gy * cell_h) for gx, gy in stroke])
    return out


def rune_column(w: int, h: int, count: int, name: str, y0f: float, y1f: float):
    """The glyph strokes of one shaft face (face proportions, supersampled canvas w x h)."""
    r = rng(name)
    order = list(range(len(GLYPHS)))
    r.shuffle(order)
    strokes = []
    span = (y1f - y0f) * h
    slot = span / count
    cell_h = slot * 0.62 / 6.0
    cell_w = min(w * 0.42 / 4.0, cell_h * 1.05)
    for i in range(count):
        top = y0f * h + i * slot + slot * 0.19
        strokes.append(glyph_strokes(order[i], w / 2.0, top, cell_w, cell_h))
    return strokes, max(2.0, cell_w * 0.8)


def carve(img: Image.Image, strokes, width: float) -> Image.Image:
    """Grooves: a light bevel below-right, the dark cut on top."""
    d = ImageDraw.Draw(img)
    off = width * 0.35
    for glyph in strokes:
        for line in glyph:
            d.line([(x + off, y + off) for x, y in line], fill=GROOVE_LIGHT, width=int(width), joint="curve")
    for glyph in strokes:
        for line in glyph:
            d.line(line, fill=GROOVE, width=int(width), joint="curve")
    return img


def glow(w: int, h: int, strokes, width: float) -> Image.Image:
    """Additive glow (black = nothing): a wide soft halo and a bright core along the strokes."""
    halo = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(halo)
    for glyph in strokes:
        for line in glyph:
            d.line(line, fill=GLOW, width=int(width * 2.6), joint="curve")
    halo = halo.filter(ImageFilter.GaussianBlur(width * 1.2))
    core = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(core)
    for glyph in strokes:
        for line in glyph:
            d.line(line, fill=GLOW_CORE, width=int(width * 0.8), joint="curve")
    a = np.asarray(halo).astype(np.float64) * 0.85 + np.asarray(core).astype(np.float64)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def band(img: Image.Image, y0: float, y1: float):
    """A carved horizontal band (two grooves) across the face."""
    w, h = img.size
    d = ImageDraw.Draw(img)
    for y in (y0 * h, y1 * h):
        d.line([(0, y + SS * 0.6), (w, y + SS * 0.6)], fill=GROOVE_LIGHT, width=int(SS * 0.9))
        d.line([(0, y), (w, y)], fill=GROOVE, width=int(SS * 0.9))


def shaft_textures(section: str, face_w: float, face_h: float, glyphs: int, y0f: float, y1f: float,
                   bands: tuple):
    w, h = int(face_w * SS * 4), int(face_h * SS * 4)
    stone = stone_face(w, h, "shaft_" + section)
    for y in bands:
        band(stone, y - 0.025, y + 0.025)
    strokes, width = rune_column(w, h, glyphs, "runes_" + section, y0f, y1f)
    carve(stone, strokes, width)
    lit = glow(w, h, strokes, width)
    return shrink(stone, 32, 32), lit.resize((32, 32), Image.LANCZOS)


def base_texture() -> Image.Image:
    w = h = 32 * SS
    img = stone_face(w, h, "base", chamfer=0.06)
    d = ImageDraw.Draw(img)
    m = 2.2 * SS
    d.rectangle([m, m, w - m, h - m], outline=GROOVE, width=int(SS * 0.9))
    d.rectangle([m + SS, m + SS, w - m - SS, h - m - SS], outline=GROOVE_LIGHT, width=int(SS * 0.5))
    for cx, cy in ((m, m), (w - m, m), (m, h - m), (w - m, h - m)):
        d.ellipse([cx - SS * 1.3, cy - SS * 1.3, cx + SS * 1.3, cy + SS * 1.3], fill=STONE[3][:3] + (255,))
    return shrink(img, 32, 32)


def cap_texture() -> Image.Image:
    w = h = 32 * SS
    img = stone_face(w, h, "cap", chamfer=0.05)
    a = np.asarray(img).astype(np.float64)
    a[..., :3] *= 0.82
    img = Image.fromarray(a.astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    for i, c in enumerate(GOLD):  # gilded rim
        inset = SS * (0.4 + i * 0.5)
        d.rectangle([inset, inset, w - inset, h - inset], outline=c, width=int(SS * 0.6))
    return shrink(img, 32, 32)


def crystal_texture() -> Image.Image:
    w = h = 32 * SS
    yy, xx = np.mgrid[0:h, 0:w] / w
    t = 0.35 + 0.5 * (1.0 - yy) + 0.15 * np.sin(xx * 9.0 + yy * 5.0)
    pal = [(30, 90, 150), (60, 170, 230), (130, 230, 255), (220, 255, 255)]
    rgb = palette_pick([c + (255,) for c in pal], t)
    alpha = np.full((h, w), 215.0)
    img = Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    d.line([(w * 0.18, h * 0.85), (w * 0.5, h * 0.08)], fill=(255, 255, 255, 235), width=int(SS * 1.4))
    d.line([(w * 0.62, h * 0.9), (w * 0.82, h * 0.35)], fill=(210, 250, 255, 180), width=int(SS * 0.8))
    return shrink(img, 32, 32)


def circle_texture() -> Image.Image:
    size = 128
    w = size * 4
    img = Image.new("RGB", (w, w), (0, 0, 0))
    d = ImageDraw.Draw(img)
    c = w / 2.0
    for r, width in ((0.48, 5), (0.43, 3), (0.27, 3)):
        d.ellipse([c - r * w, c - r * w, c + r * w, c + r * w], outline=GLOW, width=width)
    # a six-pointed star inside the inner ring
    pts = [(c + 0.26 * w * math.cos(a), c + 0.26 * w * math.sin(a))
           for a in (i * math.pi / 3 - math.pi / 2 for i in range(6))]
    d.polygon([pts[0], pts[2], pts[4]], outline=GLOW)
    d.polygon([pts[1], pts[3], pts[5]], outline=GLOW)
    for i in range(6):
        d.line([pts[i], pts[(i + 2) % 6]], fill=GLOW, width=3)
    # glyphs between the rings, upright towards the centre
    r = rng("circle")
    for i in range(16):
        a = i / 16 * 2 * math.pi
        gx, gy = c + 0.355 * w * math.cos(a), c + 0.355 * w * math.sin(a)
        glyph = GLYPHS[r.randrange(len(GLYPHS))]
        ca, sa = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)
        for stroke in glyph:
            line = []
            for px_, py_ in stroke:
                lx, ly = (px_ - 2) * 4.2, (py_ - 3) * 4.2
                line.append((gx + lx * ca - ly * sa, gy + lx * sa + ly * ca))
            d.line(line, fill=GLOW_CORE, width=3)
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    a = np.asarray(img).astype(np.float64)
    a = np.clip(a * 1.6, 0, 255)
    img = Image.fromarray(a.astype(np.uint8), "RGB").resize((size, size), Image.LANCZOS)
    return img.convert("RGBA")


def item_icon() -> Image.Image:
    w = h = 16 * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = SS
    d.rectangle([3 * s, 13 * s, 13 * s, 16 * s - 1], fill=STONE[1])
    d.polygon([(5 * s, 13 * s), (5.6 * s, 5 * s), (10.4 * s, 5 * s), (11 * s, 13 * s)], fill=STONE[2])
    d.polygon([(5 * s, 13 * s), (5.6 * s, 5 * s), (6.6 * s, 5 * s), (6.4 * s, 13 * s)], fill=STONE[3])
    d.polygon([(6.2 * s, 5 * s), (8 * s, 3.6 * s), (9.8 * s, 5 * s)], fill=GOLD[2])
    for y in (6.5, 8.5, 10.5):
        d.line([(8 * s, y * s), (8 * s, (y + 1.2) * s)], fill=GLOW + (255,), width=int(s * 0.9))
    d.polygon([(8 * s, 0.2 * s), (9.4 * s, 1.6 * s), (8 * s, 3.0 * s), (6.6 * s, 1.6 * s)], fill=(140, 240, 255, 255))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.alpha_composite(img)
    edge = img.split()[3].filter(ImageFilter.MaxFilter(int(SS * 1.6) | 1))
    outline = Image.new("RGBA", (w, h), (24, 26, 32, 255))
    outline.putalpha(edge)
    outline.alpha_composite(img)
    return shrink(outline, 16, 16)


def models():
    t = lambda n: f"{NS}:block/{n}"  # noqa: E731
    sides = ("north", "east", "south", "west")
    model("waystone_lower", {
        "parent": "minecraft:block/block",
        "textures": {"base": t("waystone_base"), "shaft": t("waystone_shaft_lower"), "particle": t("waystone_base")},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15],
             "faces": {"down": {"texture": "#base", "cullface": "down"}, "up": {"texture": "#base"}}
             | {f: {"texture": "#base", "uv": [1, 0, 15, 3]} for f in sides}},
            {"from": [3, 3, 3], "to": [13, 16, 13],
             "faces": {f: {"texture": "#shaft", "uv": [0, 0, 16, 16]} for f in sides}
             | {"up": {"texture": "#base", "uv": [3, 3, 13, 13]}}},
        ]})
    model("waystone_upper", {
        "parent": "minecraft:block/block",
        "textures": {"shaft": t("waystone_shaft_upper"), "cap": t("waystone_cap"), "particle": t("waystone_base")},
        "elements": [
            {"from": [3.5, 0, 3.5], "to": [12.5, 10, 12.5],
             "faces": {f: {"texture": "#shaft", "uv": [0, 0, 16, 16]} for f in sides}},
            {"from": [3, 10, 3], "to": [13, 11.5, 13],
             "faces": {f: {"texture": "#cap", "uv": [0, 0, 16, 2.4]} for f in sides}
             | {"up": {"texture": "#cap"}, "down": {"texture": "#cap"}}},
            {"from": [4.5, 11.5, 4.5], "to": [11.5, 13, 11.5],
             "faces": {f: {"texture": "#cap", "uv": [2, 4, 14, 6.4]} for f in sides} | {"up": {"texture": "#cap"}}},
            {"from": [6, 13, 6], "to": [10, 14.2, 10],
             "faces": {f: {"texture": "#cap", "uv": [4, 8, 12, 10]} for f in sides} | {"up": {"texture": "#cap"}}},
        ]})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    blockstate("waystone", {"variants": {
        f"facing={f},half={h}": ({"model": f"{NS}:block/waystone_{h}", "y": y} if y else
                                 {"model": f"{NS}:block/waystone_{h}"})
        for f, y in rot.items() for h in ("lower", "upper")}})
    write_json(os.path.join(ASSETS, "models", "item", "waystone.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/waystone"}})


def main() -> int:
    lower, lower_glow = shaft_textures("lower", 10, 13, 4, 0.04, 0.88, (0.95,))
    upper, upper_glow = shaft_textures("upper", 9, 10, 3, 0.10, 0.95, ())
    save(lower, "block", "waystone_shaft_lower.png")
    save(upper, "block", "waystone_shaft_upper.png")
    base = save(base_texture(), "block", "waystone_base.png")
    cap = save(cap_texture(), "block", "waystone_cap.png")
    runes = Image.new("RGBA", (64, 32), (0, 0, 0, 255))
    runes.paste(lower_glow, (0, 0))
    runes.paste(upper_glow, (32, 0))
    save(runes, "entity", "waystone", "runes.png")
    crystal = save(crystal_texture(), "entity", "waystone", "crystal.png")
    circle = save(circle_texture(), "entity", "waystone", "circle.png")
    icon = save(item_icon(), "item", "waystone.png")
    models()

    sheet = Image.new("RGBA", (900, 330), (38, 42, 54, 255))
    x = 10
    for img, scale in ((lower, 6), (upper, 6), (runes, 3), (base, 4), (cap, 4), (crystal, 4)):
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x, 10))
        x += big.width + 10
    sheet.alpha_composite(circle.resize((200, 200), Image.LANCZOS), (560, 110))
    sheet.alpha_composite(icon.resize((96, 96), Image.NEAREST), (780, 200))
    # a mock front view: the stone with its glow composited
    mock = Image.new("RGBA", (10 * 12, 23 * 12), (20, 22, 30, 255))
    lo = lower.resize((10 * 12, 13 * 12), Image.NEAREST)
    up = upper.resize((9 * 12, 10 * 12), Image.NEAREST)
    mock.alpha_composite(up, (6, 0))
    mock.alpha_composite(lo, (0, 10 * 12))
    glow_lo = np.asarray(lower_glow.resize((10 * 12, 13 * 12), Image.BILINEAR)).astype(np.float64)
    glow_up = np.asarray(upper_glow.resize((9 * 12, 10 * 12), Image.BILINEAR)).astype(np.float64)
    m = np.asarray(mock).astype(np.float64)
    m[10 * 12:, :, :3] += glow_lo[..., :3] * 0.8
    m[:10 * 12, 6:6 + 9 * 12, :3] += glow_up[..., :3] * 0.8
    mock = Image.fromarray(np.clip(m, 0, 255).astype(np.uint8), "RGBA")
    sheet.alpha_composite(mock, (10, 210 - 160 + 0) if False else (420, 40))
    sheet.save(os.path.join(HERE, "waystone_preview.png"))
    print("waystone art written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
