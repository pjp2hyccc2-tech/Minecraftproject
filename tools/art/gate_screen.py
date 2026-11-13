#!/usr/bin/env python3
"""The gatekeeper's screen (転移門): every texture of ui/gate/GatekeeperScreen.

    python3 tools/art/gate_screen.py   ->  src/main/resources/assets/eternalrealm/textures/gui/gate/
                                            atlas.png      the frame pieces, emblems, gems, plaques and icons (pixel art)
                                            circle.png     the slowly turning magic circle (white, tinted in game)
                                            sealed.png     chains and a padlock laid over a sealed dungeon's picture
                                            d1..d8.png, final.png   the pictures (gate_scenes.py)
                                       and tools/art/gate_screen_preview.png

The pictures, the circle and the seal are drawn at two texels per GUI unit and filtered smoothly (their .mcmeta asks
for blur); the atlas is crisp pixel art at one texel per unit. ATLAS below is the single source of the atlas layout;
GatekeeperScreen's constants must match it (the script prints them). Deterministic.
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import gate_scenes  # noqa: E402
from gate_paint import Canvas, rgb, smoothstep  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "gate")


def hexa(value, a=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), a)


# ------------------------------------------------------------------------------------------------ atlas layout

ATLAS = {
    "corner": (0, 0, 24, 24),            # four: top-left, top-right, bottom-left, bottom-right side by side
    "gem": (96, 0, 20, 20),              # six: normal dim/lit, hard dim/lit, ex dim/lit
    "halo": (216, 0, 32, 32),
    "emblem": (0, 24, 16, 16),           # nine: d1..d8, final
    "lock": (144, 24, 12, 14),
    "laurel": (156, 24, 16, 16),
    "pointer": (172, 24, 8, 10),
    "icon": (180, 24, 9, 9),             # four: boss, party, time, rooms
    "medal": (0, 40, 20, 20),            # four: normal, selected, sealed, raid
    "badge": (80, 40, 34, 14),
    "stud": (114, 40, 7, 7),
    "divider": (121, 40, 128, 9),
    "ribbon": (0, 60, 200, 30),
    "button": (0, 90, 96, 24),           # three, stacked: normal, hover, disabled
    "pill": (96, 90, 74, 20),            # three, stacked: normal, hover, selected
    "row": (0, 162, 152, 20),            # two, stacked: selected, hover
}

GOLD = {"o": hexa("#2a1606"), "d": hexa("#6e4410"), "m": hexa("#b07c24"), "l": hexa("#e6bc58"),
        "h": hexa("#fff1b8")}


class Px:
    """A small RGBA pixel canvas with hard edges."""

    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.a = np.zeros((h, w, 4), np.uint8)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.a[y, x]
        return np.zeros(4, np.uint8)

    def image(self):
        return Image.fromarray(self.a, "RGBA")


def mask_from(w, h, draw_fn):
    im = Image.new("1", (w, h), 0)
    draw_fn(ImageDraw.Draw(im))
    return np.asarray(im, bool)


def emboss(px, mask, pal, ox=0, oy=0, outline=True):
    """Fills mask with metal: lit on the upper-left edges, dark on the lower-right ones, with a dark outline."""
    h, w = mask.shape

    def inside(x, y):
        return 0 <= x < w and 0 <= y < h and mask[y, x]

    for y in range(h):
        for x in range(w):
            if mask[y, x]:
                up = not inside(x, y - 1) or not inside(x - 1, y)
                down = not inside(x, y + 1) or not inside(x + 1, y)
                if up and not down:
                    c = pal["l"]
                elif down and not up:
                    c = pal["d"]
                else:
                    c = pal["m"]
                if not inside(x - 1, y - 1) and not inside(x, y - 1) and not inside(x - 1, y):
                    c = pal["h"]
                px.set(ox + x, oy + y, c)
            elif outline:
                near = any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near:
                    px.set(ox + x, oy + y, pal["o"])


def from_map(px, rows, palette, ox, oy, outline=None):
    """Paints a character map; with outline, every empty pixel next to a painted one gets that colour."""
    h = len(rows)
    w = len(rows[0])
    for y, row in enumerate(rows):
        assert len(row) == w, (row, w)
        for x, ch in enumerate(row):
            if ch != ".":
                px.set(ox + x, oy + y, palette[ch])
    if outline is not None:
        for y in range(-1, h + 1):
            for x in range(-1, w + 1):
                filled = 0 <= y < h and 0 <= x < w and rows[y][x] != "."
                if filled:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= ny < h and 0 <= nx < w and rows[ny][nx] != ".":
                        px.set(ox + x, oy + y, outline)
                        break


# ------------------------------------------------------------------------------------------------ atlas pieces

def corner(px, ox, oy):
    """The top-left corner ornament (the other three are mirrors): a jewelled diamond and two scrolls."""
    size = 24
    m = np.zeros((size, size), bool)
    for y in range(size):
        for x in range(size):
            if abs(x - 6) + abs(y - 6) <= 6:
                m[y, x] = True
            if (y in (1, 2) and 6 <= x <= 19) or (x in (1, 2) and 6 <= y <= 19):
                m[y, x] = True
            for (cx, cy) in ((20, 4), (4, 20)):
                d = math.hypot(x - cx, y - cy)
                if 1.2 <= d <= 2.6:
                    m[y, x] = True
            if (x == 13 and 4 <= y <= 6) or (y == 13 and 4 <= x <= 6):
                m[y, x] = True
            if 9 <= x <= 11 and 9 <= y <= 11 and (x + y) <= 21:
                m[y, x] = True
    emboss(px, m, GOLD, ox, oy)
    gem = {"r": hexa("#6a0a1a"), "R": hexa("#c41c36"), "p": hexa("#ff9ab0")}
    for (dx, dy, c) in ((6, 4, "R"), (5, 5, "R"), (6, 5, "p"), (7, 5, "R"), (4, 6, "r"), (5, 6, "R"), (6, 6, "R"),
                        (7, 6, "R"), (8, 6, "r"), (5, 7, "r"), (6, 7, "R"), (7, 7, "r"), (6, 8, "r")):
        px.set(ox + dx, oy + dy, gem[c])


def gem_sprite(px, ox, oy, base, lit):
    """A cut gem in a gold setting (20x20)."""
    pal = {k: hexa(v) for k, v in base.items()}
    setting = np.zeros((20, 20), bool)
    stone = np.zeros((20, 20), bool)
    for y in range(20):
        for x in range(20):
            dx, dy = abs(x - 9.5), abs(y - 9.5)
            if max(dx, dy) <= 9 and dx + dy <= 12.5:
                setting[y, x] = True
            if max(dx, dy) <= 6.6 and dx + dy <= 9.2:
                stone[y, x] = True
    emboss(px, setting & ~stone, GOLD, ox, oy)
    k = 1.0 if lit else 0.62
    for y in range(20):
        for x in range(20):
            if not stone[y, x]:
                continue
            dx, dy = x - 9.5, y - 9.5
            if abs(dx) <= 2.6 and abs(dy) <= 2.6:
                c = pal["table"]
            elif dx + dy < -3:
                c = pal["light"]
            elif dx + dy > 3:
                c = pal["dark"]
            elif dx < dy:
                c = pal["mid"]
            else:
                c = pal["mid2"]
            r, g, b, a = c
            px.set(ox + x, oy + y, (int(r * k), int(g * k), int(b * k), 255))
    # edge of the stone
    for y in range(20):
        for x in range(20):
            if stone[y, x] and not all(stone[y + dy, x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                                       if 0 <= y + dy < 20 and 0 <= x + dx < 20):
                r, g, b, a = pal["edge"]
                px.set(ox + x, oy + y, (int(r * k), int(g * k), int(b * k), 255))
    if lit:
        for (dx, dy) in ((6, 5), (6, 4), (6, 6), (5, 5), (7, 5), (6, 3), (6, 7)):
            px.set(ox + dx, oy + dy, hexa("#ffffff"))
        px.set(ox + 12, oy + 13, hexa("#ffffff", 200))


GEMS = [
    {"light": "#a8ffb8", "mid": "#3ccf6a", "mid2": "#2aa856", "dark": "#0e6a30", "table": "#6ae890",
     "edge": "#0a4a22"},
    {"light": "#ffb0a0", "mid": "#e8402e", "mid2": "#c42a1e", "dark": "#7a0e0a", "table": "#ff7058",
     "edge": "#5a0806"},
    {"light": "#e8c0ff", "mid": "#a050f0", "mid2": "#8238d0", "dark": "#40107a", "table": "#c486ff",
     "edge": "#2e0a5a"},
]


def halo(px, ox, oy):
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16
            a = max(0.0, 1.0 - d) ** 2.2
            px.set(ox + x, oy + y, (255, 255, 255, int(255 * a)))


EMBLEM_OUT = hexa("#1a0e06")

EMBLEMS = {
    "d1": (["..............",
            "......dd......",
            ".....dddd.....",
            "....dddddd....",
            "...YyyyyyyY...",
            "..YyYYYYYYyY..",
            "..yyyyyyyyyy..",
            "..YYYYYYYYYY..",
            "..yyyyyyyyyy..",
            "..yYYYYYYYYy..",
            "..YyyyyyyyyY..",
            "...YyyyyyyY...",
            "....dddddd....",
            ".....dddd.....",
            "......dd......"],
           {"d": hexa("#4a2410"), "Y": hexa("#ffd870"), "y": hexa("#ff8a2a")}),
    "d2": (["....GGGGGG....",
            "..GGgGGGGgGG..",
            ".GGgGGLGGGGgG.",
            ".GLGGGGGGgGGG.",
            "GGGGgGGGLGGGgG",
            "GgGGGGgGGGGGGG",
            "GGGLGGGGGGgGLG",
            ".GGGGGgGGGGGG.",
            "..GgGGbbGGgG..",
            "....GGbbGG....",
            "......bb......",
            "......bb......",
            ".....bbbb.....",
            "...bbb..bbb...",
            "..bb......bb.."],
           {"G": hexa("#3c9a40"), "g": hexa("#26702c"), "L": hexa("#8ae070"), "b": hexa("#7a4a26")}),
    "d3": ([".......SS.....",
            "......SssS....",
            ".......SS.....",
            "..............",
            "......LL......",
            ".....LLLd.....",
            "....LLLLdd....",
            "...LLlLLddd...",
            "..LLLLLLdddd..",
            ".LLLlLLLLdddd.",
            "LLLLLLLLLddddd",
            "..............",
            "..............",
            "..............",
            ".............."],
           {"S": hexa("#ffd060"), "s": hexa("#fff4c0"), "L": hexa("#e8b468"), "l": hexa("#b07a3a"),
            "d": hexa("#9a5a2a")}),
    "d4": (["..............",
            "..............",
            "..BBBB........",
            ".BLLLLB.......",
            "BL....LB......",
            "BL....LBBBBBBB",
            "BL....LLLLLLLL",
            ".BLLLLBBBBBLBL",
            "..BBBB.....LBL",
            "...........BB.",
            "..............",
            "..............",
            "..............",
            "..............",
            ".............."],
           {"B": hexa("#3a6a9a"), "L": hexa("#bfe6ff")}),
    "d5": (["......W.......",
            "....W.W.W.....",
            ".....WWW......",
            ".W....W....W..",
            "..W...W...W...",
            "...W.cWc.W....",
            "....WWWWW.....",
            "WWWWWWcWWWWWWW",
            "....WWWWW.....",
            "...W.cWc.W....",
            "..W...W...W...",
            ".W....W....W..",
            ".....WWW......",
            "....W.W.W.....",
            "......W......."],
           {"W": hexa("#d8f6ff"), "c": hexa("#5ac8ff")}),
    "d6": (["......o.......",
            ".....oYo......",
            ".....oYo...o..",
            "....oYyYo.oYo.",
            "...oYyyyo.oyo.",
            "..oYyyRyyooyo.",
            "..oyyRRRyyyyo.",
            ".oYyRRRRRyyyo.",
            ".oyyRRwRRRyyo.",
            ".oyRRwwwRRyyo.",
            ".oyRRwwwwRRyo.",
            "..oyRwwwwRyo..",
            "...ooyyyyoo...",
            ".....oooo.....",
            ".............."],
           {"o": hexa("#c42010"), "Y": hexa("#ff5a1a"), "y": hexa("#ff8a2a"), "R": hexa("#ffc040"),
            "w": hexa("#fff6c0")}),
    "d7": ([".........WWW..",
            ".......WWWWW..",
            "......WWWWWg..",
            ".....WWWWWg...",
            "....WWWWWgW...",
            "...WWWWWgWW...",
            "...WWWWgWW....",
            "..WWWWgWWW....",
            "..WWWgWWW.....",
            ".WWWgWWW......",
            ".WWgWWW.......",
            ".WgWW.........",
            ".gW...........",
            "g.............",
            ".............."],
           {"W": hexa("#f4f0e4"), "g": hexa("#d8a84a")}),
    "d8": (["........MM....",
            ".......MMm....",
            ".......Mm.....",
            ".......MMm....",
            "..T.T.t.MM....",
            "..TTTtt.......",
            "..TTPtt.......",
            "..TTTtt.......",
            "..TTTtt.......",
            "..TTPtt.......",
            "..TTTtt.......",
            ".TTTTttt......",
            ".TTTPttt......",
            ".TTTTttt......",
            "TTTTTtttt....."],
           {"T": hexa("#6e4ca0"), "t": hexa("#42286a"), "P": hexa("#ff6ae8"), "M": hexa("#ece0ff"),
            "m": hexa("#a890d0")}),
    "final": (["..............",
               "..............",
               "G.....G.....G.",
               "GG...GGG...GG.",
               "GGG.GGPGG.GGG.",
               "GGGGGGGGGGGGG.",
               "GGGGGGGGGGGGG.",
               "GPGGGGPGGGGPG.",
               "GGGGGGGGGGGGG.",
               "ddddddddddddd.",
               "..............",
               "..............",
               "..............",
               "..............",
               ".............."],
              {"G": hexa("#f0c048"), "P": hexa("#c050ff"), "d": hexa("#a06a18")}),
}


def emblem(px, name, ox, oy):
    rows, pal = EMBLEMS[name]
    w = len(rows[0])
    h = len(rows)
    dx = ox + (16 - w) // 2
    dy = oy + (16 - h) // 2
    if name == "d4":
        dy += 2
    if name == "final":
        dy += 3
    from_map(px, rows, pal, dx, dy, outline=EMBLEM_OUT)


LOCK = ["..dddd..",
        ".d....d.",
        "d......d",
        "d......d",
        "llllllll",
        "lmmmmmml",
        "lmmoommm",
        "lmmoommm",
        "lmmmommm",
        "lmmmommm",
        "lmmmmmmm",
        "dddddddd"]

LAUREL = ["................",
          "...L........L...",
          "..Ll........lL..",
          ".L.l........l.L.",
          ".Ll..........lL.",
          "L.l..........l.L",
          "Ll............lL",
          ".L.l........l.L.",
          ".Ll..........lL.",
          "..L.l......l.L..",
          "..Lll......llL..",
          "....Ll....lL....",
          ".....llllll.....",
          ".......ll.......",
          "................",
          "................"]

POINTER = ["l.......",
           "ll......",
           "lml.....",
           "lmml....",
           "lmmml...",
           "lmmmd...",
           "lmmd....",
           "lmd.....",
           "ld......",
           "d......."]

ICONS = {
    "boss": [".wwwww.",
             "wwwwwww",
             "w.ww.ww",
             "w..w..w",
             "wwwwwww",
             ".w.w.w.",
             ".wwwww."],
    "party": [".w...w.",
              "www.www",
              ".w...w.",
              "www.www",
              "wwwwwww",
              "www.www",
              "......."],
    "time": ["wwwwwww",
             ".w...w.",
             "..w.w..",
             "...w...",
             "..w.w..",
             ".wwwww.",
             "wwwwwww"],
    "rooms": ["www.www",
              "w.w.w.w",
              "www.www",
              "...w...",
              "www.www",
              "w.w.w.w",
              "www.www"],
}


def plaque(px, ox, oy, w, h, top, bottom, rim, inner_hi, studs=True):
    """A plaque with cut corners, a gradient face, a metal rim and a highlight line."""
    m = np.zeros((h, w), bool)
    face = np.zeros((h, w), bool)
    for y in range(h):
        for x in range(w):
            cut = min(x, w - 1 - x) + min(y, h - 1 - y)
            if cut >= 2:
                m[y, x] = True
            if cut >= 4 and 2 <= x < w - 2 and 2 <= y < h - 2:
                face[y, x] = True
    emboss(px, m & ~face, rim, ox, oy)
    t0 = np.array(top, np.float32)
    t1 = np.array(bottom, np.float32)
    for y in range(h):
        t = y / max(1, h - 1)
        col = t0 + (t1 - t0) * t
        for x in range(w):
            if face[y, x]:
                px.set(ox + x, oy + y, (int(col[0]), int(col[1]), int(col[2]), 255))
    for x in range(4, w - 4):
        px.set(ox + x, oy + 2, inner_hi)
    if studs:
        for (sx, sy) in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):
            px.set(ox + sx, oy + sy, rim["l"])


def medal(px, ox, oy, ring, inner, glow=None):
    m = np.zeros((20, 20), bool)
    hole = np.zeros((20, 20), bool)
    for y in range(20):
        for x in range(20):
            d = math.hypot(x - 9.5, y - 9.5)
            if d <= 9.6:
                m[y, x] = True
            if d <= 7.4:
                hole[y, x] = True
    emboss(px, m & ~hole, ring, ox, oy)
    c0 = np.array(inner[0], np.float32)
    c1 = np.array(inner[1], np.float32)
    for y in range(20):
        for x in range(20):
            if hole[y, x]:
                t = y / 19
                col = c0 + (c1 - c0) * t
                px.set(ox + x, oy + y, (int(col[0]), int(col[1]), int(col[2]), 255))
    if glow is not None:
        for y in range(20):
            for x in range(20):
                if hole[y, x] and math.hypot(x - 9.5, y - 9.5) > 5.8:
                    px.set(ox + x, oy + y, glow)


def ribbon(px, ox, oy):
    w, h = 200, 30
    red_t = np.array((178, 34, 56), np.float32)
    red_b = np.array((92, 10, 24), np.float32)
    dark_t = np.array((110, 14, 30), np.float32)
    dark_b = np.array((58, 6, 16), np.float32)
    # tails
    for side in (0, 1):
        for y in range(10, 29):
            for x in range(0, 30):
                xx = x if side == 0 else w - 1 - x
                notch = abs(y - 19) <= (9 - x) if x < 9 else False
                if notch:
                    continue
                t = (y - 10) / 18
                col = dark_t + (dark_b - dark_t) * t
                px.set(ox + xx, oy + y, (int(col[0]), int(col[1]), int(col[2]), 255))
            xx0 = 0 if side == 0 else w - 30
        for y in (10, 28):
            for x in range(0, 30):
                xx = x if side == 0 else w - 1 - x
                if x >= 9 or y in (10, 28) and x >= 0:
                    if px.get(ox + xx, oy + y)[3]:
                        px.set(ox + xx, oy + y, GOLD["d"])
        # fold shadow
        for y in range(24, 29):
            for x in range(22, 30):
                xx = x if side == 0 else w - 1 - x
                if (y - 24) <= (x - 22):
                    px.set(ox + xx, oy + y, hexa("#2a0408"))
    # band
    for y in range(4, 25):
        t = (y - 4) / 20
        col = red_t + (red_b - red_t) * t
        for x in range(24, w - 24):
            px.set(ox + x, oy + y, (int(col[0]), int(col[1]), int(col[2]), 255))
    for x in range(24, w - 24):
        px.set(ox + x, oy + 3, GOLD["o"])
        px.set(ox + x, oy + 4, GOLD["l"])
        px.set(ox + x, oy + 5, GOLD["d"])
        px.set(ox + x, oy + 23, GOLD["l"])
        px.set(ox + x, oy + 24, GOLD["d"])
        px.set(ox + x, oy + 25, GOLD["o"])
        if x % 6 == 0:
            px.set(ox + x, oy + 7, hexa("#d04a5a", 120))
    for y in range(3, 26):
        px.set(ox + 23, oy + y, GOLD["o"])
        px.set(ox + w - 24, oy + y, GOLD["o"])
    for cx in (30, w - 31):
        for (dx, dy, c) in ((0, -2, "l"), (-1, -1, "l"), (0, -1, "h"), (1, -1, "m"), (-2, 0, "l"), (-1, 0, "m"),
                            (0, 0, "m"), (1, 0, "m"), (2, 0, "d"), (-1, 1, "m"), (0, 1, "d"), (1, 1, "d"),
                            (0, 2, "d")):
            px.set(ox + cx + dx, oy + 14 + dy, GOLD[c])


def row_overlay(px, ox, oy, w, h, color, strength):
    r, g, b = color
    for x in range(w):
        t = x / (w - 1)
        a = strength * (1.0 - t) ** 1.2 + strength * 0.08
        for y in range(h):
            edge = 1.0 if y in (0, h - 1) else 0.45
            px.set(ox + x, oy + y, (r, g, b, int(255 * min(1.0, a * edge * (1.8 if y in (0, h - 1) else 1.0)))))


def divider(px, ox, oy):
    w, h = 128, 9
    cy = 4
    for x in range(w):
        fade = min(1.0, min(x, w - 1 - x) / 40.0)
        if fade <= 0:
            continue
        px.set(ox + x, oy + cy, GOLD["l"][:3] + (int(255 * fade),))
        px.set(ox + x, oy + cy + 1, GOLD["d"][:3] + (int(200 * fade),))
    cx = w // 2
    for y in range(h):
        for x in range(cx - 5, cx + 6):
            if abs(x - cx) + abs(y - cy) <= 4:
                c = "l" if (x - cx) + (y - cy) < 0 else "d" if (x - cx) + (y - cy) > 1 else "m"
                px.set(ox + x, oy + y, GOLD[c])
    px.set(ox + cx, oy + cy, hexa("#c41c36"))
    for d in (12, 20):
        for sx in (-1, 1):
            px.set(ox + cx + sx * d, oy + cy, GOLD["h"])
            px.set(ox + cx + sx * d, oy + cy - 1, GOLD["l"])
            px.set(ox + cx + sx * d, oy + cy + 1, GOLD["d"])


def badge(px, ox, oy):
    w, h = 34, 14
    m = np.zeros((h, w), bool)
    face = np.zeros((h, w), bool)
    for y in range(h):
        for x in range(w):
            k = min(x, w - 1 - x)
            if k + 6 >= abs(y - 6.5) * 1.0 + 0.5 and k >= 0:
                if k >= abs(y - 6.5) - 6:
                    pass
            if min(x, w - 1 - x) >= abs(y - 6.5) - 0.5 - 5:
                m[y, x] = True
            if min(x, w - 1 - x) >= abs(y - 6.5) - 0.5 - 3 and 1 < y < h - 2 and 1 < x < w - 2:
                face[y, x] = True
    emboss(px, m & ~face, GOLD, ox, oy)
    for y in range(h):
        for x in range(w):
            if face[y, x]:
                t = y / (h - 1)
                px.set(ox + x, oy + y, (int(30 - 12 * t), int(26 - 12 * t), int(58 - 22 * t), 255))


def stud(px, ox, oy):
    for y in range(7):
        for x in range(7):
            d = abs(x - 3) + abs(y - 3)
            if d <= 3:
                c = "h" if d <= 1 and x <= 3 and y <= 3 else "l" if x + y < 6 else "d" if x + y > 6 else "m"
                px.set(ox + x, oy + y, GOLD[c])


def build_atlas():
    px = Px(256, 256)
    # corners: draw the top-left once and mirror it
    tl = Px(24, 24)
    corner(tl, 0, 0)
    tl_img = tl.image()
    pieces = [tl_img, tl_img.transpose(Image.FLIP_LEFT_RIGHT), tl_img.transpose(Image.FLIP_TOP_BOTTOM),
              tl_img.transpose(Image.ROTATE_180)]
    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for i, img in enumerate(pieces):
        atlas.paste(img, (i * 24, 0))
    px.a = np.asarray(atlas).copy()
    gx, gy, gw, gh = ATLAS["gem"]
    for kind, base in enumerate(GEMS):
        for lit in (0, 1):
            gem_sprite(px, gx + (kind * 2 + lit) * gw, gy, base, bool(lit))
    halo(px, *ATLAS["halo"][:2])
    ex, ey, ew, eh = ATLAS["emblem"]
    for i, name in enumerate(["d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "final"]):
        emblem(px, name, ex + i * ew, ey)
    lx, ly = ATLAS["lock"][:2]
    from_map(px, LOCK, {"d": hexa("#8a8a9a"), "l": hexa("#e8c060"), "m": hexa("#b07c24"), "o": hexa("#2a1606")},
             lx + 2, ly + 1, outline=EMBLEM_OUT)
    lax, lay = ATLAS["laurel"][:2]
    from_map(px, LAUREL, {"l": hexa("#f0c858"), "L": hexa("#c89a30")}, lax, lay, outline=hexa("#5a3a0a"))
    pox, poy = ATLAS["pointer"][:2]
    from_map(px, POINTER, {"l": GOLD["h"], "m": GOLD["l"], "d": GOLD["d"]}, pox, poy)
    ix, iy, iw, ih = ATLAS["icon"]
    for i, name in enumerate(["boss", "party", "time", "rooms"]):
        from_map(px, ICONS[name], {"w": hexa("#e6d6b0")}, ix + i * iw + 1, iy + 1, outline=hexa("#20140a"))
    mx, my, mw, mh = ATLAS["medal"]
    medal(px, mx, my, GOLD, ((26, 30, 56), (12, 14, 30)))
    medal(px, mx + mw, my, {"o": hexa("#3a2006"), "d": hexa("#a06a14"), "m": hexa("#e0aa3a"),
                            "l": hexa("#ffe08a"), "h": hexa("#ffffff")}, ((70, 52, 24), (34, 24, 12)),
          glow=hexa("#ffd870", 90))
    medal(px, mx + 2 * mw, my, {"o": hexa("#18181c"), "d": hexa("#3a3a44"), "m": hexa("#5a5a66"),
                                "l": hexa("#8a8a96"), "h": hexa("#c0c0cc")}, ((22, 22, 28), (10, 10, 14)))
    medal(px, mx + 3 * mw, my, GOLD, ((70, 20, 110), (30, 6, 52)), glow=hexa("#c060ff", 70))
    badge(px, *ATLAS["badge"][:2])
    stud(px, *ATLAS["stud"][:2])
    divider(px, *ATLAS["divider"][:2])
    ribbon(px, *ATLAS["ribbon"][:2])
    bx, by, bw, bh = ATLAS["button"]
    plaque(px, bx, by, bw, bh, (150, 26, 44), (84, 10, 24), GOLD, hexa("#e0606e", 160))
    plaque(px, bx, by + bh, bw, bh, (196, 44, 62), (118, 18, 34), {"o": hexa("#3a2006"), "d": hexa("#a06a14"),
                                                                     "m": hexa("#e0aa3a"), "l": hexa("#ffe08a"),
                                                                     "h": hexa("#ffffff")}, hexa("#ff9aa4", 190))
    plaque(px, bx, by + 2 * bh, bw, bh, (58, 56, 62), (34, 32, 38), {"o": hexa("#141416"), "d": hexa("#3a3a40"),
                                                                        "m": hexa("#5e5e66"), "l": hexa("#8a8a92"),
                                                                        "h": hexa("#b0b0b8")}, hexa("#7a7a82", 120))
    qx, qy, qw, qh = ATLAS["pill"]
    plaque(px, qx, qy, qw, qh, (30, 34, 62), (16, 18, 36), GOLD, hexa("#4a5488", 140), studs=False)
    plaque(px, qx, qy + qh, qw, qh, (44, 50, 88), (24, 28, 52), GOLD, hexa("#6a76b0", 160), studs=False)
    plaque(px, qx, qy + 2 * qh, qw, qh, (70, 60, 40), (36, 28, 16), {"o": hexa("#3a2006"), "d": hexa("#a06a14"),
                                                                       "m": hexa("#e0aa3a"), "l": hexa("#ffe08a"),
                                                                       "h": hexa("#ffffff")}, hexa("#ffe8a0", 170),
           studs=False)
    rx, ry, rw, rh = ATLAS["row"]
    row_overlay(px, rx, ry, rw, rh, (255, 208, 96), 0.42)
    row_overlay(px, rx, ry + rh, rw, rh, (190, 200, 255), 0.16)
    return px.image()


# ------------------------------------------------------------------------------------------------ circle and seal

def build_circle():
    size = 256
    c = Canvas(size, size, 4242)
    cx = cy = size / 2
    alpha = np.zeros((size, size), np.float32)

    def ring(r, w):
        d = np.sqrt((c.X - cx) ** 2 + (c.Y - cy) ** 2)
        return np.clip(1.0 - np.abs(d - r) / (w / 2), 0, 1)

    alpha = np.maximum(alpha, ring(124, 2.2))
    alpha = np.maximum(alpha, ring(117, 1.2))
    alpha = np.maximum(alpha, ring(96, 1.6))
    alpha = np.maximum(alpha, ring(90, 0.9))
    alpha = np.maximum(alpha, ring(40, 1.4))
    alpha = np.maximum(alpha, ring(34, 0.9))
    rng = np.random.default_rng(77)
    strokes = []
    for k in range(48):
        a = k * 2 * math.pi / 48
        ca, sa = math.cos(a), math.sin(a)
        px, py = cx + ca * 120.5, cy + sa * 120.5
        kind = rng.integers(0, 4)
        tx, ty = -sa, ca
        if kind == 0:
            strokes.append([(px - tx * 2, py - ty * 2), (px + tx * 2, py + ty * 2)])
        elif kind == 1:
            strokes.append([(px - ca * 2, py - sa * 2), (px + ca * 2, py + sa * 2)])
        elif kind == 2:
            strokes.append([(px - tx * 2 - ca * 2, py - ty * 2 - sa * 2), (px + ca * 2, py + sa * 2),
                            (px + tx * 2 - ca * 2, py + ty * 2 - sa * 2)])
        else:
            strokes.append([(px - tx * 2, py - ty * 2), (px - ca * 2, py - sa * 2), (px + tx * 2, py + ty * 2)])
    alpha = np.maximum(alpha, c.lines(strokes, 1.0))
    tri = []
    for start in (-math.pi / 2, math.pi / 2):
        pts = [(cx + math.cos(start + j * 2 * math.pi / 3) * 90, cy + math.sin(start + j * 2 * math.pi / 3) * 90)
               for j in range(3)]
        tri.append(pts + [pts[0]])
    alpha = np.maximum(alpha, c.lines(tri, 1.4))
    for j in range(6):
        a = -math.pi / 2 + j * math.pi / 3
        px, py = cx + math.cos(a) * 90, cy + math.sin(a) * 90
        alpha = np.maximum(alpha, ring_at(c, px, py, 7, 1.2))
        alpha = np.maximum(alpha, c.ellipse(px, py, 2.0) * 0.9)
        qx, qy = cx + math.cos(a + math.pi / 6) * 64, cy + math.sin(a + math.pi / 6) * 64
        alpha = np.maximum(alpha, c.ellipse(qx, qy, 1.6) * 0.8)
    star = []
    for j in range(8):
        a = j * math.pi / 4
        star.append([(cx, cy), (cx + math.cos(a) * 30, cy + math.sin(a) * 30)])
    alpha = np.maximum(alpha, c.lines(star, 0.8) * 0.7)
    alpha = np.maximum(alpha, c.ellipse(cx, cy, 3.0))
    rgba = np.zeros((size, size, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def ring_at(c, x, y, r, w):
    d = np.sqrt((c.X - x) ** 2 + (c.Y - y) ** 2)
    return np.clip(1.0 - np.abs(d - r) / (w / 2), 0, 1)


def build_seal():
    """Two chains crossing the picture and a padlock where they meet (transparent elsewhere)."""
    w, h = gate_scenes.W, gate_scenes.H
    c = Canvas(w, h, 31337)
    alpha = np.zeros((h, w), np.float32)
    steel_l = rgb("#c8ccd8")
    steel_d = rgb("#3a3e4a")
    for (x0, y0, x1, y1) in ((-10, 14, w + 10, h - 14), (-10, h - 14, w + 10, 14)):
        length = math.hypot(x1 - x0, y1 - y0)
        ux, uy = (x1 - x0) / length, (y1 - y0) / length
        n = int(length / 13)
        for i in range(n + 1):
            px, py = x0 + ux * i * 13, y0 + uy * i * 13
            if i % 2 == 0:
                outer = rotated_ellipse(c, px, py, 9.5, 5.4, ux, uy)
                inner = rotated_ellipse(c, px, py, 6.0, 2.0, ux, uy)
            else:
                outer = rotated_ellipse(c, px, py, 9.5, 2.6, ux, uy)
                inner = np.zeros_like(outer)
            link = np.clip(outer - inner, 0, 1)
            shade = np.clip(0.5 + (c.Y - py) * -0.12 + (c.X - px) * -0.02, 0, 1)[..., None]
            c.put(link, steel_d + (steel_l - steel_d) * shade)
            alpha = np.maximum(alpha, link)
    # the padlock
    cx, cy = w / 2, h / 2 + 8
    shackle = np.clip(rotated_ellipse(c, cx, cy - 22, 18, 22, 1, 0) - rotated_ellipse(c, cx, cy - 22, 12, 16, 1, 0),
                      0, 1) * (c.Y < cy - 6)
    c.put(shackle, c.hgrad([(0, rgb("#4a4e5a")), (0.4, rgb("#d8dce6")), (1, rgb("#5a5e6a"))], cx - 18, cx + 18))
    alpha = np.maximum(alpha, shackle)
    body = c.poly([(cx - 26, cy - 10), (cx + 26, cy - 10), (cx + 28, cy - 6), (cx + 28, cy + 26),
                   (cx + 24, cy + 30), (cx - 24, cy + 30), (cx - 28, cy + 26), (cx - 28, cy - 6)])
    c.put(body, c.vgrad([(0, rgb("#f0c860")), (0.5, rgb("#b8862a")), (1, rgb("#6e4410"))], cy - 10, cy + 30))
    edge = np.clip(body - c.poly([(cx - 24, cy - 8), (cx + 24, cy - 8), (cx + 26, cy - 4), (cx + 26, cy + 24),
                                  (cx + 22, cy + 28), (cx - 22, cy + 28), (cx - 26, cy + 24), (cx - 26, cy - 4)]),
                   0, 1)
    c.put(edge, rgb("#4a2a08"))
    hole = c.ellipse(cx, cy + 6, 5, 5) + c.poly([(cx - 2.5, cy + 8), (cx + 2.5, cy + 8), (cx + 3.5, cy + 20),
                                                (cx - 3.5, cy + 20)])
    c.put(np.clip(hole, 0, 1), rgb("#1a0e04"))
    for (sx, sy) in ((cx - 21, cy - 4), (cx + 21, cy - 4), (cx - 21, cy + 23), (cx + 21, cy + 23)):
        c.put(c.ellipse(sx, sy, 1.6), rgb("#fff0b0"))
    alpha = np.maximum(alpha, body)
    img = np.clip(c.img, 0, 1)
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., :3] = (img * 255).astype(np.uint8)
    rgba[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def rotated_ellipse(c, cx, cy, rx, ry, ux, uy):
    dx = c.X - cx
    dy = c.Y - cy
    a = dx * ux + dy * uy
    b = -dx * uy + dy * ux
    d = np.sqrt((a / rx) ** 2 + (b / ry) ** 2)
    edge = 1.0 / max(1e-6, min(rx, ry))
    return np.clip((1.0 - d) / edge + 0.5, 0, 1)


# ------------------------------------------------------------------------------------------------ output

BLUR = {"texture": {"blur": True, "clamp": True}}


def save(img, name, blur=False):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    meta = path + ".mcmeta"
    if blur:
        with open(meta, "w", encoding="utf-8") as fh:
            json.dump(BLUR, fh, indent=2)
            fh.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)
    return path


def preview(atlas, circle, seal, pictures):
    """A sheet of everything at 2x (the atlas pieces on a dark ground, the pictures, the seal on d4)."""
    sheet = Image.new("RGBA", (1000, 1040), (24, 26, 36, 255))
    big = atlas.resize((512, 512), Image.NEAREST)
    sheet.alpha_composite(big, (8, 8))
    circ = Image.new("RGBA", circle.size, (0, 0, 0, 0))
    circ.paste((240, 200, 110, 255), (0, 0), circle)
    sheet.alpha_composite(circ, (560, 8))
    names = list(pictures)
    for i, name in enumerate(names):
        img = pictures[name].convert("RGBA")
        x = 8 + (i % 2) * 490
        y = 530 + (i // 2) * 102
        sheet.alpha_composite(img.resize((236, 100), Image.LANCZOS), (x, y))
    sealed = pictures["d8"].convert("RGBA").resize((236, 100), Image.LANCZOS)
    dark = Image.new("RGBA", sealed.size, (8, 6, 16, 150))
    sealed.alpha_composite(dark)
    sealed.alpha_composite(seal.resize((236, 100), Image.LANCZOS))
    sheet.alpha_composite(sealed, (8 + 490, 530 + 4 * 102))
    return sheet


def main():
    atlas = build_atlas()
    save(atlas, "atlas.png")
    circle = build_circle()
    save(circle, "circle.png", blur=True)
    seal = build_seal()
    save(seal, "sealed.png", blur=True)
    pictures = {}
    for name in gate_scenes.SCENES:
        pictures[name] = gate_scenes.paint(name)
        save(pictures[name], name + ".png", blur=True)
    preview(atlas, circle, seal, pictures).save(os.path.join(HERE, "gate_screen_preview.png"))
    for key, (x, y, w, h) in ATLAS.items():
        print(f"{key:8s} u={x:3d} v={y:3d} {w}x{h}")


if __name__ == "__main__":
    main()
