#!/usr/bin/env python3
"""The estates' blocks (docs/modules/estates.md): the inn's ornate room door with its brass room plate, the labelled
storage chest of the rooms (one brass plate picture per label) and the sorting desk with its ledger — textures (drawn
pixel by pixel here, original art), block/item models, blockstates, loot tables — and a preview.

    python3 tools/art/estate_blocks.py      # writes the assets and tools/art/estate_blocks_preview.png

Deterministic (seeded per texture name). The door is 3/16 thick like a vanilla door; its models add a brass knob
and the room plate as raised elements. Its ``facing`` is the side it faces (its front, with the plate), unlike a
vanilla door's; the capital's manor sets every door facing its gallery. Faces: 32x32 textures (two pixels per model unit).
"""
from __future__ import annotations

import json
import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from px import seed_of  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
NS = "eternalrealm"


def hexc(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)] + [255], np.float64)


def rng(name):
    return random.Random(seed_of("estate:" + name))


# ----------------------------------------------------------------------------------------------- palette
WOOD = [hexc(c) for c in ("#26140b", "#3c2112", "#55301a", "#6e4024", "#87522e", "#a2683b", "#bb8150")]
BRASS = [hexc(c) for c in ("#3b2a0c", "#6a4b16", "#9b7023", "#c69434", "#e3b955", "#f6dc8c", "#fff4cc")]
IRON = [hexc(c) for c in ("#17171b", "#2a2a31", "#3f3f48", "#5a5a66", "#7a7a88")]
AMBER = [hexc(c) for c in ("#2a1406", "#5e300c", "#a35a16", "#e09a36", "#ffd27a", "#fff0c4")]


def canvas(w, h, color):
    a = np.zeros((h, w, 4), np.float64)
    a[:, :] = color
    return a


def save(a, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8), "RGBA").save(path)
    return a


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def wood_fill(a, x0, y0, x1, y1, r, along="y", base=3, streak=0.09):
    """Wood with grain running ``along`` (y: vertical boards, x: horizontal rails), inclusive box."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            u = x if along == "y" else y          # across the grain
            v = y if along == "y" else x          # along the grain
            g = np.sin(u * 2.3 + np.sin(v * 0.35 + u) * 1.4) * 0.5 + 0.5
            tone = base + (g - 0.5) * 0.9 + (r.random() - 0.5) * 0.35
            if (u * 7 + v // 5) % 11 == 0 and r.random() < 0.55:
                tone -= 0.8                         # a darker grain line
            a[y, x] = mix_ramp(WOOD, tone) * (1.0 + (r.random() - 0.5) * streak)
            a[y, x, 3] = 255


def mix_ramp(ramp, t):
    t = max(0.0, min(len(ramp) - 1.0, t))
    i = int(t)
    f = t - i
    if i >= len(ramp) - 1:
        return ramp[-1].copy()
    return ramp[i] * (1 - f) + ramp[i + 1] * f


def bevel_panel(a, x0, y0, x1, y1, r, depth=2, field=3.1):
    """A raised panel: a lit top/left bevel, a shaded bottom/right bevel, a flat field with fine grain, and a dark
    groove all round."""
    for x in range(x0, x1 + 1):
        a[y0, x, :3] = WOOD[1][:3]
        a[y1, x, :3] = WOOD[1][:3]
    for y in range(y0, y1 + 1):
        a[y, x0, :3] = WOOD[1][:3]
        a[y, x1, :3] = WOOD[1][:3]
    for d in range(1, depth + 1):
        for x in range(x0 + d, x1 - d + 1):
            a[y0 + d, x, :3] = mix_ramp(WOOD, 5.2 - d * 0.4)[:3]
            a[y1 - d, x, :3] = mix_ramp(WOOD, 1.8 + d * 0.3)[:3]
        for y in range(y0 + d, y1 - d + 1):
            a[y, x0 + d, :3] = mix_ramp(WOOD, 4.8 - d * 0.4)[:3]
            a[y, x1 - d, :3] = mix_ramp(WOOD, 2.0 + d * 0.3)[:3]
    wood_fill(a, x0 + depth + 1, y0 + depth + 1, x1 - depth - 1, y1 - depth - 1, r, "y", base=field, streak=0.05)


def stud(a, x, y, ramp=BRASS):
    a[y, x, :3] = ramp[5][:3]
    if x + 1 < a.shape[1]:
        a[y, x + 1, :3] = ramp[3][:3]
    if y + 1 < a.shape[0]:
        a[y + 1, x, :3] = ramp[2][:3]
        if x + 1 < a.shape[1]:
            a[y + 1, x + 1, :3] = ramp[1][:3]


def strap(a, y, x0, x1, r, flip=False):
    """A wrought-iron strap hinge (3 px high) from the hinge edge, ending in a fleur."""
    for x in range(x0, x1 + 1):
        for dy, t in ((0, 3), (1, 2), (2, 1)):
            a[y + dy, x, :3] = IRON[t][:3] * (1.0 + (r.random() - 0.5) * 0.08)
    tip = x1 + 1
    for (dx, dy, t) in ((0, -1, 3), (0, 0, 3), (0, 1, 2), (0, 2, 1), (0, 3, 1), (1, 0, 3), (1, 1, 2), (1, 2, 1),
                        (2, 1, 2)):
        a[y + dy, tip + dx, :3] = IRON[t][:3]
    for x in range(x0 + 1, x1, 4):
        a[y + 1, x, :3] = IRON[4][:3]
    if flip:
        pass


def mirror(a):
    return a[:, ::-1].copy()


# ----------------------------------------------------------------------------------------------- textures
def door_bottom():
    """Lower half, seen from the front with the hinge on the left: two raised panels, an iron strap hinge, the
    brass escutcheon under the knob, a brass kick plate with rivets."""
    r = rng("room_door_bottom")
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 4, 31, r, "y", base=3.2)          # hinge stile
    wood_fill(a, 27, 0, 31, 31, r, "y", base=3.2)        # lock stile
    wood_fill(a, 5, 0, 26, 3, r, "x", base=3.0)          # top rail (continues the middle rail)
    wood_fill(a, 5, 22, 26, 26, r, "x", base=3.0)        # bottom rail
    wood_fill(a, 15, 4, 16, 21, r, "y", base=2.9)        # muntin
    bevel_panel(a, 5, 4, 14, 21, r)
    bevel_panel(a, 17, 4, 26, 21, r)
    # stile edges: a dark joint line and a lit arris
    for y in range(32):
        a[y, 4, :3] = WOOD[1][:3]
        a[y, 27, :3] = WOOD[1][:3]
        a[y, 0, :3] = mix_ramp(WOOD, 2.0)[:3]
        a[y, 31, :3] = mix_ramp(WOOD, 1.6)[:3]
        a[y, 1, :3] = mix_ramp(WOOD, 4.6)[:3]
    # brass kick plate with rivets
    for y in range(27, 32):
        for x in range(0, 32):
            t = (5.0 if y == 27 else 3.6 - (y - 28) * 0.45) + (r.random() - 0.5) * 0.25
            if x in (0, 31):
                t -= 1.2
            a[y, x, :3] = mix_ramp(BRASS, t)[:3]
    for x in range(3, 30, 6):
        stud(a, x, 29)
    # iron strap hinge on the hinge stile
    strap(a, 8, 0, 11, r)
    # brass escutcheon with the keyhole, under the knob (the knob is a raised element at the top right)
    for y in range(4, 13):
        for x in range(27, 31):
            edge = y in (4, 12) or x in (27, 30)
            a[y, x, :3] = mix_ramp(BRASS, 2.3 if edge else 4.2 - (y - 4) * 0.15)[:3]
    a[7, 28, :3] = BRASS[0][:3]
    a[7, 29, :3] = BRASS[0][:3]
    a[8, 28, :3] = BRASS[0][:3]
    a[8, 29, :3] = BRASS[0][:3]
    a[9, 28, :3] = BRASS[0][:3]
    a[9, 29, :3] = BRASS[1][:3]
    a[10, 28, :3] = BRASS[0][:3]
    a[5, 28, :3] = BRASS[6][:3]
    return a


def door_top():
    """Upper half: the arched upper panel with a small leaded window glowing amber (a lamp burns inside), the
    upper strap hinge; the brass room plate is a raised element below the window."""
    r = rng("room_door_top")
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 4, 31, r, "y", base=3.2)
    wood_fill(a, 27, 0, 31, 31, r, "y", base=3.2)
    wood_fill(a, 5, 0, 26, 4, r, "x", base=3.0)
    wood_fill(a, 5, 27, 26, 31, r, "x", base=3.0)
    # arched panel field
    bevel_panel(a, 5, 5, 26, 26, r, depth=2, field=3.0)
    # round the panel's top corners into an arch (fill the corners back with rail wood)
    for (cx, sign) in ((5, 1), (26, -1)):
        for k in range(0, 5):
            for j in range(0, 5 - k):
                x = cx + sign * j
                y = 5 + k
                a[y, x] = mix_ramp(WOOD, 3.0 + (r.random() - 0.5) * 0.4)
        for k in range(0, 5):
            x = cx + sign * (5 - k)
            a[5 + k, x, :3] = WOOD[1][:3]
            if 5 + k + 1 < 32:
                a[5 + k + 1, x, :3] = mix_ramp(WOOD, 4.8 if sign > 0 else 2.2)[:3]
    # the little window: a round-topped light with leading, glowing amber with a warm centre
    win = [(x, y) for y in range(8, 15) for x in range(11, 21)
           if not ((y == 8 and x in (11, 12, 19, 20)) or (y == 9 and x in (11, 20)))]
    for (x, y) in win:
        d = ((x - 15.5) ** 2 + (y - 12.5) ** 2) ** 0.5
        a[y, x, :3] = mix_ramp(AMBER, 4.6 - d * 0.42 + (r.random() - 0.5) * 0.3)[:3]
    for (x, y) in win:
        if x in (15, 16) or y == 11:
            a[y, x, :3] = IRON[1][:3] if (x + y) % 2 else IRON[2][:3]
    for x in range(10, 22):
        a[15, x, :3] = mix_ramp(WOOD, 5.4)[:3]          # the sill catches the light
        a[16, x, :3] = WOOD[1][:3]
    for y in range(7, 16):
        a[y, 10, :3] = WOOD[1][:3]
        a[y, 21, :3] = WOOD[1][:3]
    for x in range(12, 20):
        a[7, x, :3] = WOOD[1][:3]
    a[8, 11, :3] = WOOD[1][:3]
    a[8, 20, :3] = WOOD[1][:3]
    # a carved scroll above the window
    for x in range(13, 19):
        a[3, x, :3] = mix_ramp(WOOD, 5.0)[:3]
        a[4, x, :3] = WOOD[1][:3]
    a[2, 12, :3] = mix_ramp(WOOD, 5.0)[:3]
    a[2, 19, :3] = mix_ramp(WOOD, 5.0)[:3]
    for y in range(32):
        a[y, 4, :3] = WOOD[1][:3]
        a[y, 27, :3] = WOOD[1][:3]
        a[y, 0, :3] = mix_ramp(WOOD, 2.0)[:3]
        a[y, 31, :3] = mix_ramp(WOOD, 1.6)[:3]
        a[y, 1, :3] = mix_ramp(WOOD, 4.6)[:3]
    strap(a, 21, 0, 11, r)
    return a


def plate():
    """The brass room plate (its raised element shows the top 32x20): a bevelled oval-cornered plate, an engraved
    key across a little laurel, and two screw heads."""
    r = rng("room_door_plate")
    a = canvas(32, 32, BRASS[3])
    w, h = 32, 20
    for y in range(h):
        for x in range(w):
            t = 4.1 - y * 0.05 + np.sin(x * 0.9 + y * 0.4) * 0.12 + (r.random() - 0.5) * 0.15
            a[y, x, :3] = mix_ramp(BRASS, t)[:3]
    for x in range(w):
        a[0, x, :3] = BRASS[5][:3]
        a[1, x, :3] = BRASS[4][:3]
        a[h - 1, x, :3] = BRASS[1][:3]
        a[h - 2, x, :3] = BRASS[2][:3]
    for y in range(h):
        a[y, 0, :3] = BRASS[4][:3]
        a[y, 1, :3] = mix_ramp(BRASS, 4.6)[:3]
        a[y, w - 1, :3] = BRASS[1][:3]
        a[y, w - 2, :3] = BRASS[2][:3]
    for (x, y) in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1), (0, h - 1), (0, h - 2), (1, h - 1),
                   (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
        a[y, x, 3] = 0
    # an engraved inner border
    for x in range(4, w - 4):
        a[3, x, :3] = BRASS[2][:3]
        a[h - 4, x, :3] = BRASS[5][:3]
    for y in range(4, h - 4):
        a[y, 3, :3] = BRASS[2][:3]
        a[y, w - 4, :3] = BRASS[5][:3]
    # the key: bow (a ring) on the left, shaft to the right, two bits
    ring = [(8, 7), (9, 6), (10, 6), (11, 7), (11, 8), (11, 9), (10, 10), (9, 10), (8, 9), (8, 8), (7, 8)]
    for (x, y) in ring:
        a[y, x, :3] = BRASS[0][:3]
    for x in range(12, 24):
        a[8, x, :3] = BRASS[0][:3]
        a[9, x, :3] = BRASS[6][:3]
    for (x, y) in ((22, 10), (22, 11), (20, 10), (20, 11), (21, 11), (23, 10)):
        a[y, x, :3] = BRASS[0][:3]
    # laurel sprigs under the key
    for k in range(4):
        a[13, 9 + k * 2, :3] = BRASS[1][:3]
        a[14, 10 + k * 2, :3] = BRASS[1][:3]
        a[13, 22 - k * 2, :3] = BRASS[1][:3]
        a[14, 21 - k * 2, :3] = BRASS[1][:3]
    a[15, 15, :3] = BRASS[1][:3]
    a[15, 16, :3] = BRASS[1][:3]
    # screws
    for (x, y) in ((5, 9), (26, 9)):
        a[y, x, :3] = BRASS[6][:3]
        a[y + 1, x, :3] = BRASS[1][:3]
        a[y, x + 1, :3] = BRASS[2][:3]
    a[h:, :, 3] = 0
    return a


def door_side():
    """The door's edge: end grain with a brass weatherstrip."""
    r = rng("room_door_side")
    a = canvas(16, 16, WOOD[3])
    wood_fill(a, 0, 0, 15, 15, r, "y", base=2.8, streak=0.12)
    for y in range(16):
        a[y, 7, :3] = BRASS[3][:3]
        a[y, 8, :3] = BRASS[2][:3]
    return a


def brass():
    r = rng("room_door_brass")
    a = canvas(16, 16, BRASS[3])
    for y in range(16):
        for x in range(16):
            d = ((x - 5.5) ** 2 + (y - 5.0) ** 2) ** 0.5
            a[y, x, :3] = mix_ramp(BRASS, 5.6 - d * 0.32 + (r.random() - 0.5) * 0.2)[:3]
    return a


def item_icon():
    """16x16 inventory icon: the door from the front, its plate and knob catching the light."""
    a = np.zeros((16, 16, 4), np.float64)
    r = rng("room_door_item")
    for y in range(1, 16):
        for x in range(4, 12):
            a[y, x] = mix_ramp(WOOD, 3.2 + (r.random() - 0.5) * 0.5)
            a[y, x, 3] = 255
    for y in range(1, 16):
        a[y, 3] = WOOD[0]
        a[y, 12] = WOOD[0]
        a[y, 4, :3] = WOOD[5][:3]
    for x in range(3, 13):
        a[0, x] = WOOD[0]
    for x in range(4, 12):
        a[1, x, :3] = WOOD[5][:3]
    for (x, y) in ((6, 2), (7, 2), (8, 2), (9, 2), (6, 3), (9, 3)):
        a[y, x, :3] = AMBER[3][:3]
    a[3, 7, :3] = AMBER[4][:3]
    a[3, 8, :3] = AMBER[4][:3]
    for x in range(5, 11):
        a[5, x, :3] = BRASS[4][:3]
        a[6, x, :3] = BRASS[3][:3]
    a[5, 5, :3] = BRASS[5][:3]
    for y in range(8, 14):
        a[y, 5, :3] = WOOD[1][:3]
        a[y, 10, :3] = WOOD[1][:3]
    for x in range(5, 11):
        a[8, x, :3] = WOOD[1][:3]
        a[13, x, :3] = WOOD[1][:3]
    a[9, 10, :3] = BRASS[5][:3]
    a[9, 11, :3] = BRASS[3][:3]
    for x in range(4, 12):
        a[15, x, :3] = BRASS[3][:3]
    a[11, 3] = IRON[2]
    a[11, 4, :3] = IRON[3][:3]
    a[11, 5, :3] = IRON[2][:3]
    return a


# ----------------------------------------------------------------------------------------------- models
def door_model(half, hinge):
    """Model of the door facing north: panel at z 0..3, its front (plate, knob) on the north face; ``hinge`` is
    seen from the front (left: the hinge on the viewer's left, i.e. east)."""
    front_uv = [0, 0, 16, 16] if hinge == "left" else [16, 0, 0, 16]
    back_uv = [16, 0, 0, 16] if hinge == "left" else [0, 0, 16, 16]
    tex = f"{NS}:block/room_door_{'bottom' if half == 'lower' else 'top'}"
    elements = [{
        "from": [0, 0, 0], "to": [16, 16, 3],
        "faces": {
            "north": {"uv": front_uv, "texture": "#face"},
            "south": {"uv": back_uv, "texture": "#face"},
            "east": {"uv": [6, 0, 9, 16], "texture": "#side"},
            "west": {"uv": [6, 0, 9, 16], "texture": "#side"},
            "up": {"uv": [0, 6, 16, 9], "texture": "#side", "rotation": 90},
            "down": {"uv": [0, 6, 16, 9], "texture": "#side", "rotation": 90},
        },
    }]
    knob_x = (1.5, 3.5) if hinge == "left" else (12.5, 14.5)   # opposite the hinge
    if half == "lower":
        for (z0, z1) in ((-1.0, 0.0), (3.0, 4.0)):
            elements.append({
                "from": [knob_x[0], 12.5, z0], "to": [knob_x[1], 14.5, z1],
                "faces": {f: {"uv": [4, 4, 6, 6], "texture": "#brass"}
                          for f in ("north", "south", "east", "west", "up", "down")},
            })
    else:
        elements.append({
            "from": [4.0, 3.0, -0.5], "to": [12.0, 8.0, 0.0],
            "faces": {
                "north": {"uv": [0, 0, 16, 10], "texture": "#plate"},
                "east": {"uv": [15, 0, 16, 10], "texture": "#plate"},
                "west": {"uv": [0, 0, 1, 10], "texture": "#plate"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#plate"},
                "down": {"uv": [0, 9, 16, 10], "texture": "#plate"},
            },
        })
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": tex, "face": tex, "side": f"{NS}:block/room_door_side",
                     "brass": f"{NS}:block/room_door_brass", "plate": f"{NS}:block/room_door_plate"},
        "elements": elements,
    }


def blockstate():
    """The model faces north; the others turn it (the door's front faces ``facing``)."""
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                model = {"model": f"{NS}:block/room_door_{'bottom' if half == 'lower' else 'top'}_{hinge}"}
                if y:
                    model["y"] = y
                variants[f"facing={facing},half={half},hinge={hinge}"] = model
    return {"variants": variants}



# ----------------------------------------------------------------------------------------------- storage chest
LABELS = ("none", "gear", "potion", "material", "food", "book", "treasure", "misc")
CARD = [hexc(c) for c in ("#6e5a40", "#a8916a", "#d8c8a0", "#efe4c6", "#fbf4e2")]

# 12x8 pictures on the plate's card: '.' card, letters from PIC_COLORS
LABEL_PICTURES = {
    "gear": ([
        "..........ss",
        ".........sSs",
        "........sSs.",
        ".......sSs..",
        "..gg..sSs...",
        "...ggsSs....",
        "....hgg.....",
        "...hh.g.....",
    ], {"s": "#5a6070", "S": "#d8dee8", "g": "#b8862e", "h": "#6a3a1e"}),
    "potion": ([
        ".....cc.....",
        ".....nn.....",
        "....gnng....",
        "...gRRRRg...",
        "..gRrRRRRg..",
        "..gRRRRRRg..",
        "...gRRRRg...",
        "....gggg....",
    ], {"c": "#8a5a30", "n": "#cfe0ea", "g": "#7a8a98", "R": "#c8283c", "r": "#ff8a96"}),
    "material": ([
        "............",
        "....yYYy....",
        "...yYYYYy...",
        "..dyyyyyyd..",
        ".kkKKk..ii..",
        "kKKKKkkiIIi.",
        "kkkkkk.iiii.",
        "............",
    ], {"y": "#c89628", "Y": "#ffe07a", "d": "#7a5414", "k": "#5a5a64", "K": "#9a9aa8", "i": "#6a8a9a",
        "I": "#b8e0f0"}),
    "food": ([
        "......l.....",
        ".....lL.....",
        "...bbbbbb...",
        "..bBBbBBbb..",
        ".bBBBBBBBBb.",
        ".bbbbbbbbbb.",
        "..oooooooo..",
        "............",
    ], {"l": "#4a7a2a", "L": "#8ac050", "b": "#9a5a22", "B": "#e0a050", "o": "#6a3a14"}),
    "book": ([
        "............",
        "..pppppppp..",
        ".rPPPPPPPPr.",
        ".rRRRRRRRRr.",
        ".rRRgRRgRRr.",
        ".rRRRRRRRRr.",
        ".rrrrrrrrrr.",
        "............",
    ], {"p": "#f4ead2", "P": "#ffffff", "r": "#5a1a1e", "R": "#a8323a", "g": "#e8c050"}),
    "treasure": ([
        "....aaaa....",
        "...aAAAAa...",
        "..aAWAAAAa..",
        "..aaaaaaaa..",
        "...aAAAAa...",
        "....aAAa....",
        ".....aa.....",
        "............",
    ], {"a": "#2a8aa8", "A": "#7ae0f8", "W": "#ffffff"}),
    "misc": ([
        ".....tt.....",
        "....tTTt....",
        "...uuuuuu...",
        "..uUUUUUUu..",
        ".uUUUUuUUUu.",
        ".uUUUUUUUUu.",
        "..uuuuuuuu..",
        "............",
    ], {"t": "#8a6a3a", "T": "#d0b070", "u": "#7a5a34", "U": "#b8935a"}),
}


def chest_wood(name, along="x"):
    r = rng(name)
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 31, 31, r, along, base=3.1, streak=0.1)
    # board seams every 7 px across the grain
    for k in range(4, 32, 7):
        for t in range(32):
            if along == "x":
                a[k, t, :3] = WOOD[1][:3]
            else:
                a[t, k, :3] = WOOD[1][:3]
    return a, r


def iron_corner(a, x, y, w, h, flip_x=False, flip_y=False):
    """An L-shaped iron corner cap with a rivet."""
    for dy in range(h):
        for dx in range(w):
            if dx < 3 or dy < 3:
                xx = x + (w - 1 - dx if flip_x else dx)
                yy = y + (h - 1 - dy if flip_y else dy)
                t = 3 if (dx == 0 or dy == 0) else 2
                a[yy, xx, :3] = IRON[t][:3]
    rx = x + (w - 2 if flip_x else 1)
    ry = y + (h - 2 if flip_y else 1)
    a[ry, rx, :3] = IRON[4][:3]


def chest_front():
    """The chest's front (the model's north face uses x 2..29, y 4..31): walnut boards, the lid's edge with an iron
    band, iron corner caps and the brass hasp; the label plate is its own raised element below the hasp."""
    a, r = chest_wood("storage_chest_front", "x")
    # the lid (rows 4..13) a little lighter, its lower edge an iron band
    for y in range(4, 14):
        for x in range(32):
            a[y, x, :3] = np.minimum(255, a[y, x, :3] * 1.08)
    for x in range(32):
        a[13, x, :3] = IRON[1][:3]
        a[14, x, :3] = IRON[3][:3]
        a[15, x, :3] = IRON[2][:3]
    for x in range(4, 30, 6):
        a[14, x, :3] = IRON[4][:3]
    iron_corner(a, 2, 4, 6, 6)
    iron_corner(a, 24, 4, 6, 6, flip_x=True)
    iron_corner(a, 2, 26, 6, 6, flip_y=True)
    iron_corner(a, 24, 26, 6, 6, flip_x=True, flip_y=True)
    # the hasp: a brass tongue from the lid over the band
    for y in range(11, 18):
        for x in range(14, 18):
            a[y, x, :3] = mix_ramp(BRASS, 4.4 - (y - 11) * 0.25 + (0.4 if x == 14 else 0.0) - (0.6 if x == 17 else 0))[:3]
    a[16, 15, :3] = BRASS[0][:3]
    a[16, 16, :3] = BRASS[0][:3]
    for y in range(4, 32):
        a[y, 2, :3] = a[y, 2, :3] * 0.8
        a[y, 29, :3] = a[y, 29, :3] * 0.7
    a[31, :, :3] = a[31, :, :3] * 0.7
    return a


def chest_side():
    a, r = chest_wood("storage_chest_side", "x")
    for y in range(4, 14):
        for x in range(32):
            a[y, x, :3] = np.minimum(255, a[y, x, :3] * 1.08)
    for x in range(32):
        a[13, x, :3] = IRON[1][:3]
        a[14, x, :3] = IRON[3][:3]
        a[15, x, :3] = IRON[2][:3]
    iron_corner(a, 2, 4, 6, 6)
    iron_corner(a, 24, 4, 6, 6, flip_x=True)
    iron_corner(a, 2, 26, 6, 6, flip_y=True)
    iron_corner(a, 24, 26, 6, 6, flip_x=True, flip_y=True)
    # a carrying handle
    for x in range(12, 20):
        a[19, x, :3] = IRON[3][:3]
        a[20, x, :3] = IRON[1][:3]
    for y in range(17, 20):
        a[y, 12, :3] = IRON[2][:3]
        a[y, 19, :3] = IRON[2][:3]
    return a


def chest_top():
    a, r = chest_wood("storage_chest_top", "x")
    for y in range(32):
        for x in (8, 9, 22, 23):
            a[y, x, :3] = IRON[2 if x in (8, 22) else 3][:3]
        for x in (8, 22):
            if y % 6 == 4:
                a[y, x + 1, :3] = IRON[4][:3]
    for x in range(32):
        a[2, x, :3] = a[2, x, :3] * 0.75
        a[29, x, :3] = a[29, x, :3] * 0.75
    return a


def chest_bottom():
    a, r = chest_wood("storage_chest_bottom", "x")
    a[:, :, :3] = a[:, :, :3] * 0.6
    return a


def label_plate(label):
    """The brass plate (its element shows the top-left 16x12): a bevelled brass frame round a cream card with the
    label's picture; the unlabelled plate is plain engraved brass."""
    r = rng("storage_chest_label_" + label)
    a = np.zeros((32, 32, 4), np.float64)
    w, h = 16, 12
    for y in range(h):
        for x in range(w):
            a[y, x] = mix_ramp(BRASS, 4.0 - y * 0.08 + (r.random() - 0.5) * 0.2)
            a[y, x, 3] = 255
    for x in range(w):
        a[0, x, :3] = BRASS[5][:3]
        a[h - 1, x, :3] = BRASS[1][:3]
    for y in range(h):
        a[y, 0, :3] = BRASS[4][:3]
        a[y, w - 1, :3] = BRASS[1][:3]
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        a[y, x, 3] = 0
    if label == "none":
        for x in range(3, w - 3):
            a[5, x, :3] = BRASS[2][:3]
            a[6, x, :3] = BRASS[5][:3]
        for (x, y) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            a[y, x, :3] = BRASS[6][:3]
        return a
    rows, colors = LABEL_PICTURES[label]
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            edge = x == 1 or y == 1 or x == w - 2 or y == h - 2
            a[y, x, :3] = (CARD[1] if edge else mix_ramp(CARD, 3.3 + (r.random() - 0.5) * 0.4))[:3]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                a[y + 2, x + 2, :3] = hexc(colors[ch])[:3]
    return a


def chest_model(label):
    """A 14x14x14 chest facing north (its front at z = 1), with the label plate as a raised element on the front."""
    t = f"{NS}:block/"
    faces = {
        "north": {"uv": [1, 2, 15, 16], "texture": "#front"},
        "south": {"uv": [1, 2, 15, 16], "texture": "#side"},
        "east": {"uv": [1, 2, 15, 16], "texture": "#side"},
        "west": {"uv": [1, 2, 15, 16], "texture": "#side"},
        "up": {"uv": [1, 1, 15, 15], "texture": "#top"},
        "down": {"uv": [1, 1, 15, 15], "texture": "#bottom", "cullface": "down"},
    }
    plate_faces = {
        "north": {"uv": [0, 0, 8, 6], "texture": "#label"},
        "east": {"uv": [7, 0, 8, 6], "texture": "#label"},
        "west": {"uv": [0, 0, 1, 6], "texture": "#label"},
        "up": {"uv": [0, 0, 8, 1], "texture": "#label"},
        "down": {"uv": [0, 5, 8, 6], "texture": "#label"},
    }
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": t + "storage_chest_front",
            "front": t + "storage_chest_front",
            "side": t + "storage_chest_side",
            "top": t + "storage_chest_top",
            "bottom": t + "storage_chest_bottom",
            "label": t + "storage_chest_label_" + label,
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 14, 15], "faces": faces},
            {"from": [4, 2, 0.5], "to": [12, 8, 1], "faces": plate_faces},
        ],
    }


def chest_blockstate():
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for label in LABELS:
            model = {"model": f"{NS}:block/storage_chest_{label}"}
            if y:
                model["y"] = y
            variants[f"facing={facing},label={label}"] = model
    return {"variants": variants}


# ----------------------------------------------------------------------------------------------- sorting desk
def desk_top():
    """The desk's top (32x32): walnut boards, a green baize writing pad with a brass edge."""
    r = rng("sorting_desk_top")
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 31, 31, r, "x", base=3.4, streak=0.08)
    for x in range(32):
        a[0, x, :3] = WOOD[5][:3]
        a[31, x, :3] = WOOD[1][:3]
    for y in range(32):
        a[y, 0, :3] = WOOD[5][:3]
        a[y, 31, :3] = WOOD[1][:3]
    for y in range(5, 27):
        for x in range(4, 28):
            g = 0.82 + (r.random() - 0.5) * 0.08 + ((x + y) % 3 == 0) * 0.03
            a[y, x, :3] = np.array([46, 92, 64]) * g
    for x in range(4, 28):
        a[5, x, :3] = BRASS[4][:3]
        a[26, x, :3] = BRASS[2][:3]
    for y in range(5, 27):
        a[y, 4, :3] = BRASS[4][:3]
        a[y, 27, :3] = BRASS[2][:3]
    return a


def desk_front():
    """The drawer front (the model's drawer face uses x 6..25, y 8..15): a raised panel with two brass pulls and a
    little brass card holder."""
    r = rng("sorting_desk_front")
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 31, 31, r, "x", base=3.0, streak=0.08)
    bevel_panel(a, 6, 8, 25, 15, r, depth=1, field=3.3)
    for (x, y) in ((9, 11), (21, 11)):
        for dx in range(3):
            a[y, x + dx, :3] = BRASS[5 - dx][:3]
            a[y + 1, x + dx, :3] = BRASS[2][:3]
    for x in range(14, 18):
        a[10, x, :3] = BRASS[4][:3]
        a[13, x, :3] = BRASS[2][:3]
    for y in range(10, 14):
        a[y, 14, :3] = BRASS[3][:3]
        a[y, 17, :3] = BRASS[2][:3]
    a[11, 15, :3] = CARD[3][:3]
    a[11, 16, :3] = CARD[3][:3]
    a[12, 15, :3] = CARD[2][:3]
    a[12, 16, :3] = CARD[2][:3]
    return a


def desk_side():
    r = rng("sorting_desk_side")
    a = canvas(32, 32, WOOD[3])
    wood_fill(a, 0, 0, 31, 31, r, "y", base=2.8, streak=0.1)
    return a


def ledger_book():
    """The open ledger lying on the desk (32x32; the book element's top uses x 0..19, y 0..15): two cream pages ruled
    in columns with tallies, a red ribbon, the leather cover's edge showing round them."""
    r = rng("sorting_desk_ledger")
    a = canvas(32, 32, hexc("#5a1e22"))
    for y in range(16):
        for x in range(20):
            a[y, x, :3] = mix_ramp([hexc("#2a0c0e"), hexc("#5a1e22"), hexc("#7a2a30")], 1.2 + (r.random() - 0.5) * 0.4)[:3]
    for y in range(1, 15):
        for x in range(1, 19):
            if x in (9, 10):
                a[y, x, :3] = CARD[1][:3]
                continue
            a[y, x, :3] = mix_ramp(CARD, 3.4 + (r.random() - 0.5) * 0.3 - (0.5 if x in (8, 11) else 0.0))[:3]
    for y in range(3, 14, 2):
        for x in list(range(2, 8)) + list(range(12, 18)):
            if r.random() < 0.8:
                a[y, x, :3] = np.array([92, 78, 70]) if (x % 4) else np.array([150, 60, 50])
    for y in range(1, 15):
        a[y, 5, :3] = a[y, 5, :3] * 0.85
        a[y, 15, :3] = a[y, 15, :3] * 0.85
    for y in range(8, 16):
        a[y, 10, :3] = np.array([196, 40, 50])
    return a


def desk_ink():
    """Inkwell and bell (16x16): dark glass with a quill, a brass bell."""
    a = canvas(16, 16, hexc("#1a1c2a"))
    for y in range(16):
        for x in range(16):
            a[y, x, :3] = mix_ramp([hexc("#0c0c16"), hexc("#1a1c2a"), hexc("#3a3e5a"), hexc("#8a90b8")],
                                   1.2 + (0.9 if (x + y) % 7 == 0 else 0.0))[:3]
    return a


def desk_model():
    t = f"{NS}:block/"

    def box(f, to, tex, uv=None, faces=("north", "south", "east", "west", "up", "down")):
        out = {}
        for face in faces:
            entry = {"texture": tex}
            if uv is not None and face in uv:
                entry["uv"] = uv[face]
            out[face] = entry
        return {"from": f, "to": to, "faces": out}

    elements = [
        # the top, its front edge and the drawer
        {"from": [0, 12, 0], "to": [16, 15, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "north": {"uv": [0, 0, 16, 3], "texture": "#side"},
            "south": {"uv": [0, 0, 16, 3], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 3], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 3], "texture": "#side"}}},
        {"from": [3, 8, 2], "to": [13, 12, 14], "faces": {
            "north": {"uv": [3, 4, 13, 8], "texture": "#front"},
            "south": {"uv": [3, 4, 13, 8], "texture": "#side"},
            "east": {"uv": [2, 4, 14, 8], "texture": "#side"},
            "west": {"uv": [2, 4, 14, 8], "texture": "#side"},
            "down": {"uv": [3, 2, 13, 14], "texture": "#side"}}},
        box([1, 0, 1], [3, 12, 3], "#side"),
        box([13, 0, 1], [15, 12, 3], "#side"),
        box([1, 0, 13], [3, 12, 15], "#side"),
        box([13, 0, 13], [15, 12, 15], "#side"),
        # the open ledger, a little turned towards the sitter
        {"from": [3, 15, 4], "to": [13, 15.5, 12], "rotation": {"angle": 0, "axis": "y", "origin": [8, 15, 8]},
         "faces": {
             "up": {"uv": [0, 0, 10, 8], "texture": "#book"},
             "north": {"uv": [0, 0, 10, 0.5], "texture": "#book"},
             "south": {"uv": [0, 0, 10, 0.5], "texture": "#book"},
             "east": {"uv": [0, 0, 8, 0.5], "texture": "#book"},
             "west": {"uv": [0, 0, 8, 0.5], "texture": "#book"}}},
        # the inkwell with a quill, and the brass bell
        box([12, 15, 12.5], [14, 17, 14.5], "#ink"),
        {"from": [13, 16, 13], "to": [13.2, 21, 13.6], "rotation": {"angle": 22.5, "axis": "z", "origin": [13, 16, 13]},
         "faces": {face: {"texture": "#quill"} for face in ("north", "south", "east", "west", "up")}},
        box([2, 15, 12.5], [4.5, 16.5, 15], "#brass"),
        box([2.75, 16.5, 13.25], [3.75, 17, 14.25], "#brass"),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": t + "sorting_desk_side",
            "top": t + "sorting_desk_top",
            "front": t + "sorting_desk_front",
            "side": t + "sorting_desk_side",
            "book": t + "sorting_desk_ledger",
            "ink": t + "sorting_desk_ink",
            "quill": "minecraft:block/white_wool",
            "brass": t + "room_door_brass",
        },
        "elements": elements,
    }


def desk_blockstate():
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        model = {"model": f"{NS}:block/sorting_desk"}
        if y:
            model["y"] = y
        variants[f"facing={facing}"] = model
    return {"variants": variants}


def loot_table(block):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{NS}:blocks/{block}",
    }

def main():
    textures = {
        "room_door_bottom": door_bottom(),
        "room_door_top": door_top(),
        "room_door_plate": plate(),
        "room_door_side": door_side(),
        "room_door_brass": brass(),
    }
    for name, a in textures.items():
        save(a, "block", name + ".png")
    save(item_icon(), "item", "room_door.png")
    for half in ("lower", "upper"):
        for hinge in ("left", "right"):
            name = f"room_door_{'bottom' if half == 'lower' else 'top'}_{hinge}"
            write_json(os.path.join(ASSETS, "models", "block", name + ".json"), door_model(half, hinge))
    write_json(os.path.join(ASSETS, "models", "item", "room_door.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/room_door"}})
    write_json(os.path.join(ASSETS, "blockstates", "room_door.json"), blockstate())
    # the storage chest and the sorting desk
    chest = {
        "storage_chest_front": chest_front(),
        "storage_chest_side": chest_side(),
        "storage_chest_top": chest_top(),
        "storage_chest_bottom": chest_bottom(),
    }
    for label in LABELS:
        chest["storage_chest_label_" + label] = label_plate(label)
    desk = {
        "sorting_desk_top": desk_top(),
        "sorting_desk_front": desk_front(),
        "sorting_desk_side": desk_side(),
        "sorting_desk_ledger": ledger_book(),
        "sorting_desk_ink": desk_ink(),
    }
    for name, a in list(chest.items()) + list(desk.items()):
        save(a, "block", name + ".png")
    for label in LABELS:
        write_json(os.path.join(ASSETS, "models", "block", f"storage_chest_{label}.json"), chest_model(label))
    write_json(os.path.join(ASSETS, "blockstates", "storage_chest.json"), chest_blockstate())
    write_json(os.path.join(ASSETS, "models", "item", "storage_chest.json"),
               {"parent": f"{NS}:block/storage_chest_gear"})
    write_json(os.path.join(ASSETS, "models", "block", "sorting_desk.json"), desk_model())
    write_json(os.path.join(ASSETS, "blockstates", "sorting_desk.json"), desk_blockstate())
    write_json(os.path.join(ASSETS, "models", "item", "sorting_desk.json"), {"parent": f"{NS}:block/sorting_desk"})
    data = os.path.join(ROOT, "src", "main", "resources", "data", NS, "loot_tables", "blocks")
    for block in ("storage_chest", "sorting_desk"):
        write_json(os.path.join(data, block + ".json"), loot_table(block))
    textures.update(chest)
    textures.update(desk)
    preview(textures)
    return 0


def preview(textures):
    """The door's two halves stacked (front, 8x), its plate, edge and the item icon."""
    scale = 8
    top = textures["room_door_top"]
    bottom = textures["room_door_bottom"]
    door = np.concatenate([top, bottom], axis=0)
    sheet = np.zeros((64 * scale // 2 * 2 + 40 + 160, 32 * scale * 3 + 60 + 340, 4), np.float64)
    sheet[:, :] = (40, 36, 44, 255)

    def blit(img, ox, oy, s):
        big = np.kron(img, np.ones((s, s, 1)))
        h, w = big.shape[:2]
        alpha = big[:, :, 3:4] / 255.0
        region = sheet[oy:oy + h, ox:ox + w]
        region[:, :, :3] = region[:, :, :3] * (1 - alpha) + big[:, :, :3] * alpha
    blit(door, 20, 20, scale)
    blit(mirror(door), 32 * scale + 40, 20, scale)
    blit(textures["room_door_plate"][:20], 64 * scale + 50, 20, 6)
    blit(textures["room_door_side"], 64 * scale + 50, 180, 6)
    blit(textures["room_door_brass"], 64 * scale + 160, 180, 6)
    blit(item_icon(), 64 * scale + 50, 300, 10)
    if "storage_chest_front" in textures:
        blit(textures["storage_chest_front"], 64 * scale + 220, 300, 4)
        blit(textures["storage_chest_side"], 64 * scale + 360, 300, 4)
        blit(textures["sorting_desk_top"], 64 * scale + 220, 440, 4)
        blit(textures["sorting_desk_ledger"], 64 * scale + 360, 440, 4)
        for i, label in enumerate(LABELS):
            blit(textures["storage_chest_label_" + label][:12, :16], 64 * scale + 260 + (i % 4) * 100,
                 20 + (i // 4) * 90, 6)
    Image.fromarray(np.clip(sheet, 0, 255).astype(np.uint8), "RGBA").save(
        os.path.join(HERE, "estate_blocks_preview.png"))


if __name__ == "__main__":
    sys.exit(main())
