#!/usr/bin/env python3
"""The estates' blocks (docs/modules/estates.md): the inn's ornate room door with its brass room plate — textures
(drawn pixel by pixel here, original art), block/item models, the blockstate — and a preview.

    python3 tools/art/estate_blocks.py      # writes the assets and tools/art/estate_blocks_preview.png

Deterministic (seeded per texture name). The door is 3/16 thick like a vanilla door; its models add a brass knob
and the room plate as raised elements. Faces: 32x32 textures (two pixels per model unit).
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
    """Model of the door facing east (panel at x 0..3, front face west), hinge on the left (north) or right."""
    front_uv = [0, 0, 16, 16] if hinge == "left" else [16, 0, 0, 16]
    back_uv = [16, 0, 0, 16] if hinge == "left" else [0, 0, 16, 16]
    tex = f"{NS}:block/room_door_{'bottom' if half == 'lower' else 'top'}"
    elements = [{
        "from": [0, 0, 0], "to": [3, 16, 16],
        "faces": {
            "west": {"uv": front_uv, "texture": "#face"},
            "east": {"uv": back_uv, "texture": "#face"},
            "north": {"uv": [6, 0, 9, 16], "texture": "#side"},
            "south": {"uv": [6, 0, 9, 16], "texture": "#side"},
            "up": {"uv": [6, 0, 9, 16], "texture": "#side"},
            "down": {"uv": [6, 0, 9, 16], "texture": "#side"},
        },
    }]
    knob_z = (12.5, 14.5) if hinge == "left" else (1.5, 3.5)
    if half == "lower":
        for (x0, x1) in ((-1.0, 0.0), (3.0, 4.0)):
            elements.append({
                "from": [x0, 12.5, knob_z[0]], "to": [x1, 14.5, knob_z[1]],
                "faces": {f: {"uv": [4, 4, 6, 6], "texture": "#brass"}
                          for f in ("north", "south", "east", "west", "up", "down")},
            })
    else:
        elements.append({
            "from": [-0.5, 3.0, 4.0], "to": [0.0, 8.0, 12.0],
            "faces": {
                "west": {"uv": [0, 0, 16, 10], "texture": "#plate"},
                "north": {"uv": [0, 0, 1, 10], "texture": "#plate"},
                "south": {"uv": [15, 0, 16, 10], "texture": "#plate"},
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
    variants = {}
    for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                model = {"model": f"{NS}:block/room_door_{'bottom' if half == 'lower' else 'top'}_{hinge}"}
                if y:
                    model["y"] = y
                variants[f"facing={facing},half={half},hinge={hinge}"] = model
    return {"variants": variants}


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
    preview(textures)
    return 0


def preview(textures):
    """The door's two halves stacked (front, 8x), its plate, edge and the item icon."""
    scale = 8
    top = textures["room_door_top"]
    bottom = textures["room_door_bottom"]
    door = np.concatenate([top, bottom], axis=0)
    sheet = np.zeros((64 * scale // 2 * 2 + 40, 32 * scale * 3 + 60, 4), np.float64)
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
    Image.fromarray(np.clip(sheet, 0, 255).astype(np.uint8), "RGBA").save(
        os.path.join(HERE, "estate_blocks_preview.png"))


if __name__ == "__main__":
    sys.exit(main())
