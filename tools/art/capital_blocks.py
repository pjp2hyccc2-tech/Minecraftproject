#!/usr/bin/env python3
"""Building blocks of the royal capital (castle stone, blue roof tiles, gold trim, plaster, chandelier, light shaft)
and the kingdom crest banner pattern: textures, block/item models, blockstates, loot tables and tags.

    python3 tools/art/capital_blocks.py            # writes assets + data, and tools/art/capital_blocks_preview.png

Deterministic (seeded per texture name). Run from anywhere.
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
from px import Canvas, hexc, mix, seed_of  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "eternalrealm"


# =========================================================================== helpers
def rng(name: str) -> random.Random:
    return random.Random(seed_of("capital:" + name))


def canvas_from(arr: np.ndarray) -> Canvas:
    c = Canvas(arr.shape[1], arr.shape[0])
    c.a = np.clip(arr, 0, 255).astype(np.uint8)
    return c


def solid(w: int, h: int, color) -> np.ndarray:
    a = np.zeros((h, w, 4), np.float64)
    a[:, :] = color
    return a


def shade(arr: np.ndarray, mask: np.ndarray, factor: float) -> None:
    arr[mask, :3] *= factor


def noise_tone(arr: np.ndarray, r: random.Random, amount: float, mask: np.ndarray | None = None) -> None:
    h, w = arr.shape[:2]
    for y in range(h):
        for x in range(w):
            if mask is not None and not mask[y, x]:
                continue
            arr[y, x, :3] *= 1.0 + (r.random() - 0.5) * 2 * amount


def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def save(arr: np.ndarray, *parts: str) -> np.ndarray:
    canvas_from(arr).save(os.path.join(ASSETS, "textures", *parts))
    return arr


# =========================================================================== textures
LIMESTONE = hexc("#E9E4D8")
MORTAR = hexc("#C9C1B1")


def castle_bricks() -> np.ndarray:
    """Warm white limestone: 8x4 bricks, staggered, soft bevel, faint speckles."""
    r = rng("castle_bricks")
    a = solid(16, 16, LIMESTONE)
    for row in range(4):
        y0 = row * 4
        offset = 0 if row % 2 == 0 else 4
        for b in range(-1, 3):
            x0 = b * 8 + offset
            tone = 1.0 + (r.random() - 0.5) * 0.07
            warm = (r.random() - 0.5) * 6
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 8):
                    if not 0 <= x < 16:
                        continue
                    px = a[y, x]
                    px[:3] = np.array(LIMESTONE[:3], float) * tone + np.array([warm, warm * 0.6, -warm * 0.4])
                    if y == y0 + 3 or x == x0:            # mortar: bottom row and left column of the brick
                        px[:3] = np.array(MORTAR[:3], float) * (0.97 + r.random() * 0.06)
                    elif y == y0:                          # lit top edge
                        px[:3] = np.minimum(255, px[:3] * 1.045)
                    elif y == y0 + 2 or x == x0 + 7:      # shaded lower/right edge
                        px[:3] *= 0.965
    noise_tone(a, r, 0.018)
    for _ in range(7):                                     # tiny pits
        x, y = r.randrange(16), r.randrange(16)
        if a[y, x, 0] > 215:
            a[y, x, :3] *= 0.93
    return a


def smooth_castle_stone() -> np.ndarray:
    r = rng("smooth_castle_stone")
    a = solid(16, 16, hexc("#EEEAE1"))
    noise_tone(a, r, 0.012)
    edge = np.zeros((16, 16), bool)
    edge[0, :] = edge[15, :] = edge[:, 0] = edge[:, 15] = True
    shade(a, edge, 0.95)
    a[1, 1:15, :3] = np.minimum(255, a[1, 1:15, :3] * 1.02)
    return a


def castle_pillar_side() -> np.ndarray:
    """Fluted column: 4 flutes, each a shaded groove between lit ridges, capital/base bands."""
    r = rng("castle_pillar_side")
    a = solid(16, 16, hexc("#E7E2D6"))
    for x in range(16):
        k = x % 4
        f = {0: 1.05, 1: 0.95, 2: 0.9, 3: 1.0}[k]
        a[:, x, :3] *= f
    for y in (0, 15):
        a[y, :, :3] = np.array(hexc("#D8D1C3")[:3], float)
    a[1, :, :3] = np.minimum(255, a[1, :, :3] * 1.06)
    a[14, :, :3] *= 0.94
    noise_tone(a, r, 0.012)
    return a


def castle_pillar_top() -> np.ndarray:
    a = solid(16, 16, hexc("#E4DED2"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.2:
                a[y, x, :3] *= 0.93
            elif 5.2 < d < 6.2:
                a[y, x, :3] *= 0.95
            elif d < 2.0:
                a[y, x, :3] *= 1.03
    noise_tone(a, rng("pillar_top"), 0.012)
    return a


def carved_castle_stone() -> np.ndarray:
    """Gothic quatrefoil tracery carved into smooth stone (shadow lower-right, light upper-left)."""
    a = smooth_castle_stone()
    cut = np.zeros((16, 16), bool)
    for y in range(16):
        for x in range(16):
            cx, cy = x - 7.5, y - 7.5
            lobes = min(math.hypot(cx - 2.6, cy), math.hypot(cx + 2.6, cy), math.hypot(cx, cy - 2.6),
                        math.hypot(cx, cy + 2.6))
            ring = 2.0 <= lobes <= 3.1
            frame = (x in (2, 13) and 2 <= y <= 13) or (y in (2, 13) and 2 <= x <= 13)
            cut[y, x] = ring or frame
    base = a.copy()
    for y in range(16):
        for x in range(16):
            if cut[y, x]:
                a[y, x, :3] = base[y, x, :3] * 0.82
                if x + 1 < 16 and y + 1 < 16 and not cut[y + 1, x + 1]:
                    a[y + 1, x + 1, :3] = base[y + 1, x + 1, :3] * 1.04
                if x - 1 >= 0 and y - 1 >= 0 and not cut[y - 1, x - 1]:
                    a[y - 1, x - 1, :3] = base[y - 1, x - 1, :3] * 0.9
    centre = [(7, 7), (8, 7), (7, 8), (8, 8)]
    for x, y in centre:
        a[y, x, :3] = np.array(hexc("#D9A93B")[:3], float)  # a small gold boss in the middle
    a[7, 7, :3] = np.array(hexc("#F2D27A")[:3], float)
    return a


GOLD = [hexc("#6E4E12"), hexc("#A97A22"), hexc("#D9A93B"), hexc("#F0CC6A"), hexc("#FFF0B8")]


STAR = ["...#...",
        ".#.#.#.",
        "..###..",
        "###@###",
        "..###..",
        ".#.#.#.",
        "...#..."]


def castle_crest_stone() -> np.ndarray:
    """The kingdom crest in relief: a heater shield with a gold eight-pointed star, small wings at its sides."""
    a = smooth_castle_stone()
    base = a.copy()
    shield = np.zeros((16, 16), bool)
    for y in range(2, 15):
        for x in range(2, 14):
            w = 5.5 if y < 9 else 5.5 - (y - 8) * 0.95
            if abs(x - 7.5) <= w:
                shield[y, x] = True
    for y in range(16):
        for x in range(16):
            if not shield[y, x]:
                continue
            inner = all(shield[min(15, max(0, y + dy)), min(15, max(0, x + dx))]
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if inner:
                a[y, x, :3] = base[y, x, :3] * 0.94
            else:
                lit = x <= 7 and y <= 8
                a[y, x, :3] = base[y, x, :3] * (1.06 if lit else 0.76)
    for j, row in enumerate(STAR):
        for i, ch in enumerate(row):
            if ch in "#@":
                x, y = 5 + i, 4 + j
                g = GOLD[4] if ch == "@" else GOLD[3] if (i <= 3 and j <= 3) else GOLD[2]
                a[y, x, :3] = np.array(g[:3], float)
                if x + 1 < 16 and y + 1 < 16 and STAR[j + 1 if j + 1 < 7 else j][min(6, i + 1)] == ".":
                    a[y + 1, x + 1, :3] = np.array(GOLD[1][:3], float) * 0.9
    for (x, y) in [(0, 5), (1, 4), (1, 5), (0, 6), (1, 7), (15, 5), (14, 4), (14, 5), (15, 6), (14, 7)]:
        a[y, x, :3] = base[y, x, :3] * 0.84
    return a


def roof_tiles(name: str, base: str, light: str, dark: str, hi: str) -> np.ndarray:
    """Slate shingles: 4x4 tiles staggered by half a tile per row; each tile has a lit top edge, a shadowed bottom
    lip and a dark seam on its right, with a little tone variation so the roof does not look printed."""
    r = rng(name)
    b, l, d, h = (np.array(hexc(c)[:3], float) for c in (base, light, dark, hi))
    a = solid(16, 16, hexc(base))
    for row in range(4):
        y0 = row * 4
        offset = 0 if row % 2 == 0 else 2
        for t in range(-1, 5):
            x0 = t * 4 + offset
            tone = 1.0 + (r.random() - 0.5) * 0.12
            for yy in range(4):
                for xx in range(4):
                    x = x0 + xx
                    if not 0 <= x < 16:
                        continue
                    y = y0 + yy
                    if xx == 3:
                        col = d * 1.05                       # seam between tiles
                    elif yy == 3:
                        col = d                              # shadowed lip over the row below
                    elif yy == 0:
                        col = (h if xx == 0 else l) * tone   # lit top edge
                    else:
                        col = b * tone * (1.0 - 0.03 * yy)
                    a[y, x, :3] = col
    noise_tone(a, r, 0.015)
    return a


def gilded_trim() -> np.ndarray:
    """Gold moulding: dark rims, bright fillets and a row of round beads between two grooves."""
    a = solid(16, 16, GOLD[2])
    rows = {0: 0, 1: 3, 2: 4, 3: 2, 4: 1, 11: 1, 12: 3, 13: 2, 14: 2, 15: 0}
    for y, g in rows.items():
        a[y, :, :3] = np.array(GOLD[g][:3], float)
    for x in range(16):
        k = x % 4
        for y in range(5, 11):
            yy = y - 5
            if k == 3 or yy in (0, 5):
                c = GOLD[1]                                   # gaps between beads
            elif (k in (0, 2)) and yy in (1, 4):
                c = GOLD[1]                                   # rounded corners of a bead
            elif k == 0 and yy in (2, 3) or (k == 1 and yy == 1):
                c = GOLD[4] if yy <= 2 else GOLD[3]           # lit upper-left of the bead
            elif k == 2 or yy == 4:
                c = GOLD[1] if (k == 2 and yy == 4) else GOLD[2]
            else:
                c = GOLD[3]
            a[y, x, :3] = np.array(c[:3], float)
    return a


def gilded_plain() -> np.ndarray:
    a = solid(16, 16, GOLD[2])
    for y in range(16):
        for x in range(16):
            f = 1.0 + 0.12 * math.cos((x - y) * 0.45)
            a[y, x, :3] = np.minimum(255, a[y, x, :3] * f)
    a[0, :, :3] = np.array(GOLD[3][:3], float)
    a[:, 0, :3] = np.array(GOLD[3][:3], float)
    a[15, :, :3] = np.array(GOLD[1][:3], float)
    a[:, 15, :3] = np.array(GOLD[1][:3], float)
    return a


def white_plaster() -> np.ndarray:
    r = rng("white_plaster")
    a = solid(16, 16, hexc("#EEE7D6"))
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.9 + r.random() * 0.3) * math.sin(y * 0.7 + 1.3) * 0.012
            a[y, x, :3] *= 1.0 + v + (r.random() - 0.5) * 0.03
    return a


def candle_wax() -> np.ndarray:
    a = solid(16, 16, hexc("#F3EBD5"))
    for x in range(16):
        a[:, x, :3] *= 1.0 - 0.06 * abs(x - 7.5) / 7.5
    a[0:2, :, :3] = np.array(hexc("#FFE9A8")[:3], float)
    a[2:4, :, :3] = np.array(hexc("#FFF6D6")[:3], float)
    return a


def flame() -> np.ndarray:
    a = solid(16, 16, hexc("#FFB33A"))
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 8, (y - 10) / 9)
            a[y, x, :3] = np.array(mix(hexc("#FFF7DA"), hexc("#FF9E2C"), min(1.0, d))[:3], float)
    return a


def light_shaft_icon() -> np.ndarray:
    a = np.zeros((16, 16, 4), float)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            along = (x - y)
            if abs(along) <= 4 - t * 2:
                alpha = 220 * (1 - t) * (1 - abs(along) / 5)
                a[y, x] = [255, 244, 200, max(0, alpha)]
    a[1:4, 1:4] = [255, 250, 225, 255]
    return a


def mote() -> np.ndarray:
    """A soft warm speck (8x8) for the dust in sunbeams."""
    a = np.zeros((8, 8, 4), float)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) / 3.5
            if d < 1.0:
                a[y, x] = [255, 246, 222, 255 * (1.0 - d) ** 1.6]
    return a


# ---- banner pattern (64x64 layout; the flag face is 20x40 at (1, 1); the shield face 12x22 at (2, 2))
def crest_mask(w: int, h: int) -> np.ndarray:
    """White mask of the crest on a w x h face: a crown, an eight-pointed star with wings, and a sword below."""
    m = np.zeros((h, w), bool)
    cx = (w - 1) / 2.0
    sy = h * 0.40                       # star centre
    rs = w * 0.26
    for y in range(h):
        for x in range(w):
            dx, dy = x - cx, y - sy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            spike = rs * (0.42 + 0.58 * abs(math.cos(4 * ang)) ** 5)
            if d <= spike:
                m[y, x] = True
            # wings: two sweeping arcs beside the star
            for side in (-1, 1):
                wx = side * dx
                if rs * 0.9 <= wx <= rs * 2.05 and -rs * 0.85 <= dy <= rs * 0.55:
                    feather = (wx - rs * 0.9) / (rs * 1.15)
                    top = -rs * 0.85 + feather * rs * 0.55
                    if top <= dy <= rs * 0.55 - feather * rs * 0.9 and int(wx * 1.6) % 2 == 0:
                        m[y, x] = True
            # crown
            cy0 = sy - rs * 1.95
            if -rs * 0.8 <= dx <= rs * 0.8 and cy0 <= y <= cy0 + rs * 0.55:
                m[y, x] = True
            if -rs * 0.8 <= dx <= rs * 0.8 and cy0 - rs * 0.55 <= y < cy0:
                if int(round((dx + rs * 0.8) / (rs * 0.8))) in (0, 1, 2) and abs(((dx + rs * 0.8) % (rs * 0.8))) < 1.0:
                    m[y, x] = True
            # sword below the star
            if abs(dx) <= max(0.6, w * 0.035) and sy + rs * 1.0 <= y <= h * 0.93:
                m[y, x] = True
            if abs(dx) <= rs * 0.75 and abs(y - (sy + rs * 1.35)) <= 0.6:
                m[y, x] = True
    return m


def banner_pattern() -> np.ndarray:
    a = np.zeros((64, 64, 4), float)
    m = crest_mask(20, 40)
    for y in range(40):
        for x in range(20):
            if m[y, x]:
                a[1 + y, 1 + x] = [255, 255, 255, 255]
    return a


def shield_pattern() -> np.ndarray:
    a = np.zeros((64, 64, 4), float)
    m = crest_mask(12, 22)
    for y in range(22):
        for x in range(12):
            if m[y, x]:
                a[2 + y, 2 + x] = [255, 255, 255, 255]
    return a


# =========================================================================== blocks
FULL = {
    "castle_bricks": "castle_bricks",
    "smooth_castle_stone": "smooth_castle_stone",
    "carved_castle_stone": "carved_castle_stone",
    "royal_roof_tiles": "royal_roof_tiles",
    "pale_roof_tiles": "pale_roof_tiles",
    "gilded_trim": "gilded_trim",
    "white_plaster": "white_plaster",
}
STAIRS = {  # stairs/slab id prefix -> (full block, texture)
    "castle_brick": ("castle_bricks", "castle_bricks"),
    "smooth_castle_stone": ("smooth_castle_stone", "smooth_castle_stone"),
    "royal_roof_tile": ("royal_roof_tiles", "royal_roof_tiles"),
    "pale_roof_tile": ("pale_roof_tiles", "pale_roof_tiles"),
    "gilded_trim": ("gilded_trim", "gilded_trim"),
}
WALLS = {"castle_brick_wall": "castle_bricks"}
PICKAXE = ["castle_bricks", "smooth_castle_stone", "carved_castle_stone", "castle_crest_stone", "castle_pillar",
           "royal_roof_tiles", "pale_roof_tiles", "gilded_trim", "gilded_finial", "white_plaster", "chandelier",
           "castle_brick_wall"] + [f"{p}_stairs" for p in STAIRS] + [f"{p}_slab" for p in STAIRS]


def tex(name: str) -> str:
    return f"{NS}:block/{name}"


def model(name: str, data: dict) -> None:
    write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), data)


def item_model(name: str, parent: str | None = None, data: dict | None = None) -> None:
    write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"), data or {"parent": parent or f"{NS}:block/{name}"})


def blockstate(name: str, data: dict) -> None:
    write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), data)


def loot(name: str, slab: bool = False) -> None:
    entry = {"type": "minecraft:item", "name": f"{NS}:{name}"}
    if slab:
        entry["functions"] = [{"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [
            {"condition": "minecraft:block_state_property", "block": f"{NS}:{name}",
             "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]
    write_json(os.path.join(DATA, NS, "loot_tables", "blocks", f"{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def stairs_state(name: str) -> dict:
    straight, inner, outer = (f"{NS}:block/{name}", f"{NS}:block/{name}_inner", f"{NS}:block/{name}_outer")
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                m = straight if shape == "straight" else inner if shape.startswith("inner") else outer
                y = y0
                if half == "bottom" and shape.endswith("_left"):
                    y -= 90
                if half == "top" and shape.endswith("_right"):
                    y += 90
                v = {"model": m}
                if half == "top":
                    v["x"] = 180
                y %= 360
                if y:
                    v["y"] = y
                if half == "top" or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def gen_stairs_slab(prefix: str, full: str, texture: str) -> None:
    t = tex(texture)
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        model(f"{prefix}_stairs{suffix}", {"parent": f"minecraft:block/{parent}",
                                            "textures": {"bottom": t, "top": t, "side": t}})
    blockstate(f"{prefix}_stairs", stairs_state(f"{prefix}_stairs"))
    item_model(f"{prefix}_stairs")
    loot(f"{prefix}_stairs")
    model(f"{prefix}_slab", {"parent": "minecraft:block/slab", "textures": {"bottom": t, "top": t, "side": t}})
    model(f"{prefix}_slab_top", {"parent": "minecraft:block/slab_top", "textures": {"bottom": t, "top": t, "side": t}})
    blockstate(f"{prefix}_slab", {"variants": {
        "type=bottom": {"model": f"{NS}:block/{prefix}_slab"},
        "type=top": {"model": f"{NS}:block/{prefix}_slab_top"},
        "type=double": {"model": f"{NS}:block/{full}"}}})
    item_model(f"{prefix}_slab")
    loot(f"{prefix}_slab", slab=True)


def gen_wall(name: str, texture: str) -> None:
    t = tex(texture)
    for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                           ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
        model(f"{name}{suffix}", {"parent": f"minecraft:block/{parent}", "textures": {"wall": t}})
    parts = [{"when": {"up": "true"}, "apply": {"model": f"{NS}:block/{name}_post"}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for height, suffix in (("low", "_side"), ("tall", "_side_tall")):
            apply = {"model": f"{NS}:block/{name}{suffix}", "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"when": {side: height}, "apply": apply})
    blockstate(name, {"multipart": parts})
    item_model(name, f"{NS}:block/{name}_inventory")
    loot(name)


def gen_full(name: str, texture: str) -> None:
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(texture)}})
    blockstate(name, {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    item_model(name)
    loot(name)


def gen_pillar() -> None:
    model("castle_pillar", {"parent": "minecraft:block/cube_column",
                            "textures": {"end": tex("castle_pillar_top"), "side": tex("castle_pillar_side")}})
    model("castle_pillar_horizontal", {"parent": "minecraft:block/cube_column_horizontal",
                                       "textures": {"end": tex("castle_pillar_top"), "side": tex("castle_pillar_side")}})
    blockstate("castle_pillar", {"variants": {
        "axis=y": {"model": f"{NS}:block/castle_pillar"},
        "axis=z": {"model": f"{NS}:block/castle_pillar_horizontal", "x": 90},
        "axis=x": {"model": f"{NS}:block/castle_pillar_horizontal", "x": 90, "y": 90}}})
    item_model("castle_pillar")
    loot("castle_pillar")


def gen_crest_stone() -> None:
    model("castle_crest_stone", {"parent": "minecraft:block/orientable",
                                 "textures": {"front": tex("castle_crest_stone"), "side": tex("smooth_castle_stone"),
                                              "top": tex("smooth_castle_stone")}})
    blockstate("castle_crest_stone", {"variants": {
        "facing=north": {"model": f"{NS}:block/castle_crest_stone"},
        "facing=east": {"model": f"{NS}:block/castle_crest_stone", "y": 90},
        "facing=south": {"model": f"{NS}:block/castle_crest_stone", "y": 180},
        "facing=west": {"model": f"{NS}:block/castle_crest_stone", "y": 270}}})
    item_model("castle_crest_stone")
    loot("castle_crest_stone")


def box(frm, to, texture, faces=("north", "east", "south", "west", "up", "down"), rotation=None, uv=None):
    e = {"from": list(frm), "to": list(to),
         "faces": {f: {"texture": texture, **({"uv": uv} if uv else {})} for f in faces}}
    if rotation:
        e["rotation"] = rotation
    return e


def gen_finial() -> None:
    """A small gold spire tip with an orb and a star point, sitting on top of a spire block."""
    g = "#gold"
    elements = [
        box((6, 0, 6), (10, 2, 10), g),
        box((7, 2, 7), (9, 6, 9), g),
        box((6, 6, 6), (10, 9, 10), g),     # orb
        box((7.5, 9, 7.5), (8.5, 16, 8.5), g),
        box((6, 12, 7.5), (10, 13, 8.5), g),  # cross bar
    ]
    model("gilded_finial", {"textures": {"gold": tex("gilded_plain"), "particle": tex("gilded_plain")},
                            "elements": elements})
    blockstate("gilded_finial", {"variants": {"": {"model": f"{NS}:block/gilded_finial"}}})
    item_model("gilded_finial")
    loot("gilded_finial")


def gen_chandelier() -> None:
    """A gold chandelier for the great halls: stem, bowl, 8 arms (4 straight, 4 rotated 45 degrees) with candles."""
    g, w, f = "#gold", "#wax", "#flame"
    elements = [
        box((7, 9, 7), (9, 16, 9), g),            # stem up to the chain
        box((6, 14, 6), (10, 16, 10), g),          # cap
        box((5, 5, 5), (11, 8, 11), g),            # bowl
        box((6, 3, 6), (10, 5, 10), g),
        box((7, 0, 7), (9, 3, 9), g),              # finial
    ]
    arm_sets = [None, {"origin": [8, 7, 8], "axis": "y", "angle": 45}]
    for rot in arm_sets:
        for (frm, to, cup, candle, fl) in (
            ((11, 6.5, 7.5), (19, 7.5, 8.5), ((18, 7.5, 7), (20, 8.5, 9)), ((18.5, 8.5, 7.5), (19.5, 11.5, 8.5)),
             ((18.75, 11.5, 7.75), (19.25, 12.75, 8.25))),
            ((-3, 6.5, 7.5), (5, 7.5, 8.5), ((-4, 7.5, 7), (-2, 8.5, 9)), ((-3.5, 8.5, 7.5), (-2.5, 11.5, 8.5)),
             ((-3.25, 11.5, 7.75), (-2.75, 12.75, 8.25))),
            ((7.5, 6.5, 11), (8.5, 7.5, 19), ((7, 7.5, 18), (9, 8.5, 20)), ((7.5, 8.5, 18.5), (8.5, 11.5, 19.5)),
             ((7.75, 11.5, 18.75), (8.25, 12.75, 19.25))),
            ((7.5, 6.5, -3), (8.5, 7.5, 5), ((7, 7.5, -4), (9, 8.5, -2)), ((7.5, 8.5, -3.5), (8.5, 11.5, -2.5)),
             ((7.75, 11.5, -3.25), (8.25, 12.75, -2.75))),
        ):
            elements.append(box(frm, to, g, rotation=rot))
            elements.append(box(cup[0], cup[1], g, rotation=rot))
            elements.append(box(candle[0], candle[1], w, rotation=rot))
            elements.append(box(fl[0], fl[1], f, rotation=rot))
    model("chandelier", {"textures": {"gold": tex("gilded_plain"), "wax": tex("candle_wax"), "flame": tex("flame"),
                                      "particle": tex("gilded_plain")},
                         "elements": elements})
    blockstate("chandelier", {"variants": {"": {"model": f"{NS}:block/chandelier"}}})
    item_model("chandelier")
    loot("chandelier")


def gen_light_shaft() -> None:
    model("light_shaft", {"textures": {"particle": tex("light_shaft")}})
    blockstate("light_shaft", {"variants": {f"facing={d}": {"model": f"{NS}:block/light_shaft"}
                                            for d in ("north", "east", "south", "west")}})
    item_model("light_shaft", data={"parent": "minecraft:item/generated",
                                    "textures": {"layer0": f"{NS}:block/light_shaft"}})
    loot("light_shaft")


def tags() -> None:
    stairs = [f"{NS}:{p}_stairs" for p in STAIRS]
    slabs = [f"{NS}:{p}_slab" for p in STAIRS]
    walls = [f"{NS}:{w}" for w in WALLS]
    for tag, values in (("mineable/pickaxe", [f"{NS}:{n}" for n in PICKAXE]), ("stairs", stairs), ("slabs", slabs),
                        ("walls", walls)):
        merge_tag(os.path.join(DATA, "minecraft", "tags", "blocks", f"{tag}.json"), values)
    for tag, values in (("stairs", stairs), ("slabs", slabs), ("walls", walls)):
        merge_tag(os.path.join(DATA, "minecraft", "tags", "items", f"{tag}.json"), values)


def merge_tag(path: str, values) -> None:
    """Adds values to a tag file, keeping entries other generators wrote (town_gimmicks.py shares these tags)."""
    existing = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            existing = json.load(f).get("values", [])
    write_json(path, {"replace": False, "values": sorted(set(existing) | set(values))})


def main() -> int:
    textures = {
        "castle_bricks": castle_bricks(),
        "smooth_castle_stone": smooth_castle_stone(),
        "castle_pillar_side": castle_pillar_side(),
        "castle_pillar_top": castle_pillar_top(),
        "carved_castle_stone": carved_castle_stone(),
        "castle_crest_stone": castle_crest_stone(),
        "royal_roof_tiles": roof_tiles("royal_roof_tiles", "#2D4FA0", "#4469BC", "#1B3168", "#6F95DD"),
        "pale_roof_tiles": roof_tiles("pale_roof_tiles", "#5A84CE", "#7FA3E0", "#3B5FA6", "#A9C5F0"),
        "gilded_trim": gilded_trim(),
        "gilded_plain": gilded_plain(),
        "white_plaster": white_plaster(),
        "candle_wax": candle_wax(),
        "flame": flame(),
        "light_shaft": light_shaft_icon(),
    }
    for name, arr in textures.items():
        save(arr, "block", f"{name}.png")
    save(banner_pattern(), "entity", "banner", "er_crest.png")
    save(mote(), "particle", "mote_0.png")
    write_json(os.path.join(ASSETS, "particles", "mote.json"), {"textures": [f"{NS}:mote_0"]})
    save(shield_pattern(), "entity", "shield", "er_crest.png")

    for name, texture in FULL.items():
        gen_full(name, texture)
    for prefix, (full, texture) in STAIRS.items():
        gen_stairs_slab(prefix, full, texture)
    for name, texture in WALLS.items():
        gen_wall(name, texture)
    gen_pillar()
    gen_crest_stone()
    gen_finial()
    gen_chandelier()
    gen_light_shaft()
    tags()

    # preview sheet: every texture at 6x, plus the banner crest on a blue flag
    tiles = list(textures.items())
    cell = 96
    sheet = Image.new("RGBA", (cell * 8, cell * 3), (40, 44, 56, 255))
    for i, (name, arr) in enumerate(tiles):
        im = canvas_from(arr).image().resize((cell - 8, cell - 8), Image.NEAREST)
        sheet.paste(im, ((i % 8) * cell + 4, (i // 8) * cell + 4), im)
    flag = Image.new("RGBA", (20, 40), (44, 72, 160, 255))
    m = crest_mask(20, 40)
    for y in range(40):
        for x in range(20):
            if m[y, x]:
                flag.putpixel((x, y), (232, 190, 70, 255))
    sheet.paste(flag.resize((40, 80), Image.NEAREST), (cell * 7 + 28, cell * 2 + 8))
    sheet.save(os.path.join(HERE, "capital_blocks_preview.png"))
    print(f"{len(textures)} textures, {len(FULL) + 2 * len(STAIRS) + len(WALLS) + 5} blocks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
