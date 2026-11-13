#!/usr/bin/env python3
"""The 魔導スマホ (ui/phone/PhoneScreen): every texture of the magic phone, original art.

    python3 tools/art/phone.py  ->  src/main/resources/assets/eternalrealm/textures/gui/phone/phone.png (512 x 512)
                                    tools/art/phone_preview.png

The body (ebony with a gold rim, a crystal lens at the top and a rune button at the bottom) leaves the screen
transparent; the wallpaper (a moonlit night over a castle), the app icons and the small pieces of the apps share the
same atlas. Painted with gate_paint.Canvas (shapes drawn four times larger and averaged down, so edges are smooth) and
gate_screen's pixel tools for the tiny icons. ATLAS is the single source of the layout; ui/phone/PhoneArt's constants
must match it. Deterministic.
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from gate_paint import Canvas, rgb, smoothstep  # noqa: E402
from gate_screen import Px, from_map, hexa  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "phone")
SIZE = 512

ATLAS = {
    "body": (0, 0, 168, 280),          # the screen (10..158 x 22..254) is transparent
    "wallpaper": (168, 0, 148, 232),
    "app": (320, 0, 28, 28),           # five in a row: friends, messages, camera, feed, activity
    "icon": (320, 32, 10, 10),         # twelve in a row: back, heart, heart_full, comment, trash, send, photo,
                                       # check, cross, plus, bell, person
    "button": (320, 48, 40, 14),       # three stacked: normal, hover, disabled
    "tab": (364, 48, 40, 12),          # two stacked: selected, not
    "bubble_mine": (408, 48, 12, 12),
    "bubble_theirs": (424, 48, 12, 12),
    "card": (440, 48, 16, 16),
    "input": (456, 48, 16, 12),
    "badge": (472, 48, 9, 9),
    "online": (484, 48, 5, 5),
    "offline": (490, 48, 5, 5),
    "home_glow": (320, 96, 20, 20),
    "no_photo": (344, 96, 24, 18),
}
SCREEN = (10, 22, 158, 254)


# ------------------------------------------------------------------------------------------------ helpers

def rounded_rect(c, x0, y0, x1, y1, r):
    """Mask of a rounded rectangle."""
    pts = []
    for (cx, cy, a0) in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for i in range(10):
            a = math.radians(a0 + i * 10)
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return c.poly(pts)


def rgba(c, alpha):
    img = c.finish(exposure=1.0, saturation=1.0, vignette=0.0, grain=0.0)
    a = Image.fromarray(np.clip(alpha * 255 + 0.5, 0, 255).astype(np.uint8), "L")
    out = img.convert("RGBA")
    out.putalpha(a)
    return out


def plain(c, alpha):
    """Colours as painted (no tone mapping), with alpha."""
    arr = np.clip(c.img * 255.0 + 0.5, 0, 255).astype(np.uint8)
    out = Image.fromarray(arr, "RGB").convert("RGBA")
    out.putalpha(Image.fromarray(np.clip(alpha * 255 + 0.5, 0, 255).astype(np.uint8), "L"))
    return out


GOLD_HI = rgb("#fff0b0")
GOLD_LIGHT = rgb("#f0cc6a")
GOLD_MID = rgb("#c8942e")
GOLD_DARK = rgb("#7a4c12")


# ------------------------------------------------------------------------------------------------ body

def body():
    w, h = 168, 280
    c = Canvas(w, h, 11)
    outer = rounded_rect(c, 0.5, 0.5, w - 0.5, h - 0.5, 16)
    hole = rounded_rect(c, SCREEN[0], SCREEN[1], SCREEN[2], SCREEN[3], 5)
    # ebony with a violet sheen, lit from the upper left
    base = c.vgrad([(0.0, rgb("#2e2342")), (0.55, rgb("#1d1530")), (1.0, rgb("#120c1e"))])
    c.put(np.ones((h, w), np.float32), base)
    sheen = smoothstep(0.0, 1.0, 1.0 - np.abs((c.X - w * 0.28) / (w * 0.22)))
    c.add(sheen * 0.08, rgb("#9a86d0"))
    c.multiply(c.fbm(18, 3, seed=5) * 0.25 + 0.75, rgb("#ffffff"))
    grain = np.abs(np.sin(c.Y * 0.9 + c.fbm(6, 2, seed=8) * 6.0))
    c.add((grain > 0.96) * 0.05, rgb("#b8a0e8"))
    # bevel along the outer edge
    inner = rounded_rect(c, 3.0, 3.0, w - 3.0, h - 3.0, 13.5)
    edge = np.clip(outer - inner, 0, 1)
    lit = np.clip((w - c.X) / w * 0.5 + (h - c.Y) / h * 0.5, 0, 1)
    c.add(edge * lit * 0.35, rgb("#d8c8ff"))
    c.multiply(edge * (1 - lit) * 0.6, rgb("#000000"))
    # the gold rim
    rim_out = rounded_rect(c, 4.0, 4.0, w - 4.0, h - 4.0, 12.5)
    rim_in = rounded_rect(c, 5.6, 5.6, w - 5.6, h - 5.6, 11.0)
    rim = np.clip(rim_out - rim_in, 0, 1)
    gold = c.vgrad([(0.0, GOLD_LIGHT), (0.5, GOLD_MID), (1.0, GOLD_DARK)])
    c.put(rim, gold)
    c.add(rim * np.clip(1.0 - c.Y / h * 1.6, 0, 1) * 0.35, GOLD_HI)
    # corner jewels on the rim
    for (cx, cy) in ((9.5, 9.5), (w - 9.5, 9.5), (9.5, h - 9.5), (w - 9.5, h - 9.5)):
        d = np.abs(c.X - cx) + np.abs(c.Y - cy)
        c.put(smoothstep(4.2, 3.4, d), gold)
        c.put(smoothstep(2.6, 1.8, d), rgb("#2f6fe0"))
        c.add(c.radial(cx - 0.6, cy - 0.8, 1.4) * 0.9, rgb("#cfe2ff"))
    # the screen's bezel and its faint crystal glow
    bezel = np.clip(rounded_rect(c, SCREEN[0] - 2.0, SCREEN[1] - 2.0, SCREEN[2] + 2.0, SCREEN[3] + 2.0, 7) - hole, 0, 1)
    c.put(bezel, rgb("#06040b"))
    ring = np.clip(rounded_rect(c, SCREEN[0] - 3.0, SCREEN[1] - 3.0, SCREEN[2] + 3.0, SCREEN[3] + 3.0, 8)
                   - rounded_rect(c, SCREEN[0] - 2.0, SCREEN[1] - 2.0, SCREEN[2] + 2.0, SCREEN[3] + 2.0, 7), 0, 1)
    c.put(ring, rgb("#8a6a2a"), 0.8)
    # the crystal lens and the two studs at the top
    lx, ly = w / 2, 12.0
    c.put(c.ellipse(lx, ly, 5.2), gold)
    c.put(c.ellipse(lx, ly, 3.8), rgb("#0b1636"))
    c.add(c.radial(lx, ly + 0.8, 3.6) * 0.8, rgb("#3a7cff"))
    c.add(c.radial(lx - 1.2, ly - 1.3, 1.5) * 1.2, rgb("#e8f2ff"))
    for sx in (lx - 13, lx + 13):
        c.put(c.ellipse(sx, ly, 1.8), gold)
        c.put(c.ellipse(sx, ly, 1.0), rgb("#6a2a8a"))
    slot = rounded_rect(c, lx - 30, ly - 1.0, lx - 18, ly + 1.0, 1.0) + rounded_rect(c, lx + 18, ly - 1.0, lx + 30,
                                                                                       ly + 1.0, 1.0)
    c.put(np.clip(slot, 0, 1), rgb("#05030a"))
    # the rune button at the bottom
    bx, by = w / 2, 267.5
    c.put(c.ellipse(bx, by, 8.4), gold)
    c.put(c.ellipse(bx, by, 6.8), rgb("#0d1230"))
    c.add(c.radial(bx, by, 6.5) * 0.45, rgb("#3a4cc0"))
    rune = c.lines([[(bx, by - 4.2), (bx + 3.2, by), (bx, by + 4.2), (bx - 3.2, by), (bx, by - 4.2)],
                    [(bx, by - 2.0), (bx, by + 2.0)], [(bx - 1.6, by), (bx + 1.6, by)]], 0.9)
    c.put(rune, rgb("#8ff0ff"))
    c.add(c.blur_mask(rune, 1.2) * 0.8, rgb("#48c8ff"))
    c.add(c.radial(bx - 2.5, by - 3.0, 3.0) * 0.25, rgb("#ffffff"))
    alpha = np.clip(outer - hole, 0, 1)
    return plain(c, alpha)


# ------------------------------------------------------------------------------------------------ wallpaper

def wallpaper():
    w, h = 148, 232
    c = Canvas(w, h, 23)
    sky = c.vgrad([(0.0, rgb("#070a22")), (0.35, rgb("#141a48")), (0.62, rgb("#3a2266")), (0.78, rgb("#7a3a72")),
                   (0.9, rgb("#c0607a"))])
    c.put(np.ones((h, w), np.float32), sky)
    # aurora
    wave = 70 + 14 * np.sin(c.X * 0.05 + 1.3) + c.fbm(30, 3, seed=4) * 22
    band = np.exp(-((c.Y - wave) / 9.0) ** 2) * (0.55 + 0.45 * c.fbm(10, 3, seed=6))
    c.add(band * smoothstep(0.0, 0.4, c.X / w) * 0.35, rgb("#3cf0b0"))
    c.add(np.exp(-((c.Y - wave + 10) / 14.0) ** 2) * 0.12, rgb("#8a6cff"))
    # stars
    rng = np.random.default_rng(7)
    stars = [(float(rng.uniform(0, w)), float(rng.uniform(0, h * 0.62)), float(rng.uniform(0.25, 1.0)))
             for _ in range(90)]
    c.dots(stars, rgb("#ffffff"), 0.9)
    bright = [(float(rng.uniform(5, w - 5)), float(rng.uniform(5, h * 0.5)), 1.0) for _ in range(7)]
    c.dots(bright, rgb("#fff4d8"), 1.0, glow_r=3.5, glow_k=0.25)
    # the moon
    mx, my, mr = 104.0, 46.0, 15.0
    c.glow(mx, my, mr * 3.2, rgb("#ffe8c0"), 0.28, power=2.2)
    moon = c.ellipse(mx, my, mr)
    c.put(moon, rgb("#fff3d6"))
    c.multiply(moon * (c.fbm(6, 3, seed=9) * 0.25 + 0.0), rgb("#c8b090"), 1.0)
    for (cx, cy, r) in ((mx - 5, my - 3, 3.2), (mx + 4, my + 5, 2.4), (mx + 6, my - 6, 1.6)):
        c.multiply(c.ellipse(cx, cy, r) * 0.35, rgb("#b09878"))
    c.put(c.ellipse(mx + 6, my - 3, mr) * moon * 0.22, rgb("#e8d6b8"))
    # far mountains, near hills, the castle
    far = 150 + 18 * c.fbm(40, 3, seed=12) - 10 * np.exp(-((c.X - 40) / 26) ** 2)
    c.put(smoothstep(-0.8, 0.8, c.Y - far), rgb("#2a1a4a"))
    near = 178 + 12 * c.fbm(30, 3, seed=13)
    c.put(smoothstep(-0.8, 0.8, c.Y - near), rgb("#140c26"))
    castle = c.polys([
        [(60, 178), (60, 150), (63, 146), (66, 150), (66, 158), (74, 158), (74, 136), (77, 128), (80, 136),
         (80, 158), (88, 158), (88, 144), (91, 139), (94, 144), (94, 178)],
        [(52, 178), (52, 162), (55, 158), (58, 162), (58, 178)],
        [(96, 178), (96, 160), (99, 156), (102, 160), (102, 178)],
    ])
    c.put(castle, rgb("#0d0820"))
    for (x, y) in ((77, 142), (77, 148), (63, 156), (91, 150), (99, 166), (55, 168), (70, 166), (84, 166)):
        c.put(c.rect(x - 0.6, y - 0.9, x + 0.6, y + 0.9), rgb("#ffcc66"))
        c.glow(x, y, 2.6, rgb("#ff9a3a"), 0.35)
    c.put(c.line([(77, 128), (77, 122)], 0.6), rgb("#0d0820"))
    c.put(c.poly([(77, 122), (82, 123.5), (77, 125)]), rgb("#c03048"))
    # mist and the ground
    c.add(np.exp(-((c.Y - 182) / 8.0) ** 2) * 0.22, rgb("#c8a0d8"))
    ground = 196 + 6 * c.fbm(24, 2, seed=14)
    c.put(smoothstep(-0.8, 0.8, c.Y - ground), rgb("#08060f"))
    img = c.finish(exposure=1.05, saturation=1.08, vignette=0.25, grain=0.01)
    return img.convert("RGBA")


# ------------------------------------------------------------------------------------------------ app icons

APP_TILES = {
    "friends": ("#2fb3a8", "#0f4c58"),
    "messages": ("#4a86f0", "#1a3584"),
    "camera": ("#a066ea", "#40208a"),
    "feed": ("#f0aa38", "#94500e"),
    "activity": ("#ea5a7c", "#861e3c"),
}


def app_icon(kind):
    s = 28
    c = Canvas(s, s, 31)
    top, bottom = APP_TILES[kind]
    tile = rounded_rect(c, 0.5, 0.5, s - 0.5, s - 0.5, 7)
    inner = rounded_rect(c, 1.6, 1.6, s - 1.6, s - 1.6, 6)
    c.put(np.ones((s, s), np.float32), c.vgrad([(0.0, rgb(top)), (1.0, rgb(bottom))]))
    c.add(c.radial(s * 0.35, s * 0.15, s * 0.7, s * 0.45) * 0.35, rgb("#ffffff"))
    border = np.clip(tile - inner, 0, 1)
    c.put(border, c.vgrad([(0.0, GOLD_LIGHT), (1.0, GOLD_DARK)]))
    white = rgb("#ffffff")
    cx, cy = s / 2, s / 2
    if kind == "friends":
        back = c.ellipse(cx + 4.5, cy - 4.0, 3.4) + c.ellipse(cx + 4.5, cy + 6.5, 6.0, 4.6) * smoothstep(
            cy + 4.0, cy + 2.5, -c.Y + 2 * cy + 1)
        c.put(np.clip(back, 0, 1) * (c.Y < cy + 9.5), rgb("#ffe6a0"))
        front = c.ellipse(cx - 3.0, cy - 2.5, 4.0) + c.ellipse(cx - 3.0, cy + 9.0, 7.2, 5.6)
        c.put(np.clip(front, 0, 1) * (c.Y < cy + 10.5), rgb("#1b4d55"), 0.9)
        c.put(np.clip(c.ellipse(cx - 3.0, cy - 2.5, 3.2) + c.ellipse(cx - 3.0, cy + 9.4, 6.2, 5.0), 0, 1)
              * (c.Y < cy + 10.0), white)
    elif kind == "messages":
        env = c.rect(cx - 8.5, cy - 6.0, cx + 8.5, cy + 6.5)
        c.put(env, white)
        c.put(c.lines([[(cx - 8.5, cy - 6.0), (cx, cy + 1.5), (cx + 8.5, cy - 6.0)]], 1.1), rgb("#9ab0d8"))
        c.put(c.ellipse(cx, cy + 1.0, 3.0), rgb("#c8283a"))
        c.add(c.radial(cx - 0.8, cy, 1.5) * 0.6, rgb("#ff9a9a"))
    elif kind == "camera":
        c.put(c.ellipse(cx, cy, 9.0), c.vgrad([(0.0, GOLD_LIGHT), (1.0, GOLD_DARK)]))
        c.put(c.ellipse(cx, cy, 7.2), rgb("#0b1030"))
        c.add(c.radial(cx, cy + 1.0, 6.8) * 0.9, rgb("#7a5cff"))
        c.add(c.radial(cx, cy, 3.4) * 0.9, rgb("#c8f0ff"))
        c.put(c.ellipse(cx, cy, 1.6), rgb("#0b1030"))
        c.add(c.radial(cx - 2.6, cy - 3.0, 2.2) * 1.2, white)
    elif kind == "feed":
        c.put(c.rect(cx - 7.5, cy - 7.0, cx + 6.0, cy + 7.5), rgb("#fff1d0"))
        c.put(c.ellipse(cx - 7.5, cy - 7.0, 2.2) + c.ellipse(cx + 6.0, cy + 7.5, 2.2), rgb("#e8c890"))
        for i in range(4):
            y = cy - 3.5 + i * 3.0
            c.put(c.line([(cx - 5.0, y), (cx + 3.0 - (i == 3) * 3, y)], 0.8), rgb("#b08850"))
        quill = c.lines([[(cx + 9.0, cy - 10.0), (cx + 1.0, cy + 4.0)]], 1.2)
        c.put(quill, white)
        c.put(c.poly([(cx + 9.5, cy - 10.5), (cx + 4.5, cy - 4.0), (cx + 6.5, cy - 9.0)]), rgb("#ffffff"))
    elif kind == "activity":
        bell = c.poly([(cx - 7.5, cy + 5.0), (cx - 5.5, cy - 2.0), (cx - 4.0, cy - 6.0), (cx, cy - 8.0),
                       (cx + 4.0, cy - 6.0), (cx + 5.5, cy - 2.0), (cx + 7.5, cy + 5.0)])
        c.put(bell, rgb("#ffe08a"))
        c.add(c.radial(cx - 2.5, cy - 3.0, 4.0) * 0.6, white)
        c.put(c.rect(cx - 8.5, cy + 4.5, cx + 8.5, cy + 6.3), rgb("#f0c050"))
        c.put(c.ellipse(cx, cy + 8.0, 2.0), rgb("#ffe08a"))
        c.put(c.ellipse(cx, cy - 9.0, 1.3), rgb("#ffe08a"))
    return plain(c, tile)


# ------------------------------------------------------------------------------------------------ small pixel icons

ICON_MAPS = {
    "back": ["....##....", "...##.....", "..##......", ".#########", "##########", ".#########", "..##......",
             "...##.....", "....##....", ".........."],
    "heart": ["..........", ".##...##..", "#..#.#..#.", "#...#...#.", "#.......#.", ".#.....#..", "..#...#...",
              "...#.#....", "....#.....", ".........."],
    "heart_full": ["..........", ".##...##..", "#rr#.#rr#.", "#rrr#rrr#.", "#rrrrrrr#.", ".#rrrrr#..",
                   "..#rrr#...", "...#r#....", "....#.....", ".........."],
    "comment": [".########.", "##########", "##.##.##.#", "##########", ".########.", "..##......",
                ".##.......", "#.........", "..........", ".........."],
    "trash": ["...####...", ".########.", "..........", ".#.#.#.#..", ".#.#.#.#..", ".#.#.#.#..", ".#.#.#.#..",
              ".#.#.#.#..", "..######..", ".........."],
    "send": ["#.........", "##........", "#.##......", "#...##....", "#.....##..", "#...##....", "#.##......",
             "##........", "#.........", ".........."],
    "photo": ["##########", "#........#", "#.##.....#", "#.##..#..#", "#....###.#", "#...#####.", "#.#######.",
              "##########", "..........", ".........."],
    "check": [".........#", "........##", ".......##.", "#.....##..", "##...##...", ".##.##....", "..###.....",
              "...#......", "..........", ".........."],
    "cross": ["#.......#.", "##.....##.", ".##...##..", "..##.##...", "...###....", "..##.##...", ".##...##..",
              "##.....##.", "#.......#.", ".........."],
    "plus": ["....##....", "....##....", "....##....", "##########", "##########", "....##....", "....##....",
             "....##....", "..........", ".........."],
    "bell": ["....##....", "...####...", "..######..", "..######..", "..######..", ".########.", "##########",
             "..........", "....##....", ".........."],
    "person": ["...###....", "..#####...", "..#####...", "...###....", "..........", ".#######..", "#########.",
               "#########.", "#########.", ".........."],
}
ICON_ORDER = ["back", "heart", "heart_full", "comment", "trash", "send", "photo", "check", "cross", "plus", "bell",
              "person"]


def icons(px, ox, oy):
    pal = {"#": hexa("#ffffff"), "r": hexa("#ff4a6a")}
    for i, name in enumerate(ICON_ORDER):
        rows = [row.ljust(10, ".")[:10] for row in ICON_MAPS[name]]
        while len(rows) < 10:
            rows.append("..........")
        from_map(px, rows, pal, ox + i * 10, oy)


# ------------------------------------------------------------------------------------------------ ui pieces

def piece(w, h, fill_top, fill_bottom, border, radius, alpha=1.0, border_alpha=1.0, highlight=0.25, seed=41):
    c = Canvas(w, h, seed)
    outer = rounded_rect(c, 0.5, 0.5, w - 0.5, h - 0.5, radius)
    inner = rounded_rect(c, 1.5, 1.5, w - 1.5, h - 1.5, max(0.5, radius - 1))
    c.put(np.ones((h, w), np.float32), c.vgrad([(0.0, rgb(fill_top)), (1.0, rgb(fill_bottom))]))
    if highlight > 0:
        c.add(np.clip(inner * smoothstep(h * 0.55, 1.0, c.Y), 0, 1) * highlight, rgb("#ffffff"))
    edge = np.clip(outer - inner, 0, 1)
    if border is not None:
        c.put(edge, rgb(border))
    a = np.clip(inner * alpha + edge * border_alpha, 0, 1) * outer
    return plain(c, a)


def dot(color, glow):
    c = Canvas(5, 5, 51)
    m = c.ellipse(2.5, 2.5, 2.2)
    c.put(np.ones((5, 5), np.float32), rgb(color))
    c.add(c.radial(1.8, 1.7, 1.4) * 0.6, rgb("#ffffff"))
    return plain(c, np.clip(m + (c.radial(2.5, 2.5, 2.5) * glow), 0, 1))


def badge():
    c = Canvas(9, 9, 52)
    outer = c.ellipse(4.5, 4.5, 4.4)
    c.put(np.ones((9, 9), np.float32), rgb("#ffffff"))
    c.put(c.ellipse(4.5, 4.5, 3.5), rgb("#e0243e"))
    c.add(c.radial(3.6, 3.2, 2.4) * 0.35, rgb("#ffffff"))
    return plain(c, outer)


def home_glow():
    c = Canvas(20, 20, 53)
    m = c.radial(10, 10, 10, power=1.6)
    c.put(np.ones((20, 20), np.float32), rgb("#8ff0ff"))
    return plain(c, m * 0.85)


def no_photo():
    c = Canvas(24, 18, 54)
    frame = rounded_rect(c, 0.5, 0.5, 23.5, 17.5, 2)
    c.put(np.ones((18, 24), np.float32), rgb("#3a3354"))
    c.put(c.poly([(3, 15), (9, 7), (13, 12), (16, 9), (21, 15)]), rgb("#6a5f90"))
    c.put(c.ellipse(17, 5, 2.0), rgb("#c8b8f0"))
    return plain(c, frame * 0.9)


# ------------------------------------------------------------------------------------------------ the atlas

def build():
    atlas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    atlas.alpha_composite(body(), ATLAS["body"][:2])
    atlas.alpha_composite(wallpaper(), ATLAS["wallpaper"][:2])
    ax, ay, aw, ah = ATLAS["app"]
    for i, kind in enumerate(["friends", "messages", "camera", "feed", "activity"]):
        atlas.alpha_composite(app_icon(kind), (ax + i * aw, ay))
    px = Px(120, 10)
    icons(px, 0, 0)
    atlas.alpha_composite(px.image(), ATLAS["icon"][:2])
    bx, by, bw, bh = ATLAS["button"]
    atlas.alpha_composite(piece(bw, bh, "#5a48a8", "#2c2266", "#e8c060", 3), (bx, by))
    atlas.alpha_composite(piece(bw, bh, "#7a66d0", "#3c2e88", "#fff0a8", 3, highlight=0.35), (bx, by + bh))
    atlas.alpha_composite(piece(bw, bh, "#4a4658", "#2a2834", "#7a7680", 3, highlight=0.1), (bx, by + 2 * bh))
    tx, ty, tw, th = ATLAS["tab"]
    atlas.alpha_composite(piece(tw, th, "#f6d27a", "#c08a2a", "#fff0b0", 4, highlight=0.3), (tx, ty))
    atlas.alpha_composite(piece(tw, th, "#2a2246", "#1a1530", "#a07c3a", 4, alpha=0.85, highlight=0.1), (tx, ty + th))
    atlas.alpha_composite(piece(12, 12, "#f8e4b0", "#e8c884", "#b08a3a", 4, highlight=0.2),
                          ATLAS["bubble_mine"][:2])
    atlas.alpha_composite(piece(12, 12, "#3a4c84", "#28365e", "#6a80c0", 4, highlight=0.12),
                          ATLAS["bubble_theirs"][:2])
    atlas.alpha_composite(piece(16, 16, "#161228", "#0c0a18", "#8a6a2a", 5, alpha=0.82, border_alpha=0.8,
                                highlight=0.05), ATLAS["card"][:2])
    atlas.alpha_composite(piece(16, 12, "#06050c", "#0c0a16", "#6a58b0", 3, alpha=0.9, highlight=0.0),
                          ATLAS["input"][:2])
    atlas.alpha_composite(badge(), ATLAS["badge"][:2])
    atlas.alpha_composite(dot("#4be36a", 0.35), ATLAS["online"][:2])
    atlas.alpha_composite(dot("#6a6a7a", 0.0), ATLAS["offline"][:2])
    atlas.alpha_composite(home_glow(), ATLAS["home_glow"][:2])
    atlas.alpha_composite(no_photo(), ATLAS["no_photo"][:2])
    return atlas


def preview(atlas):
    """The phone on its home screen over a scene, at 3x."""
    scene = Image.new("RGBA", (260, 300), (60, 84, 56, 255))
    x0, y0 = 46, 10
    wall = atlas.crop((168, 0, 316, 232))
    scene.alpha_composite(wall, (x0 + SCREEN[0], y0 + SCREEN[1]))
    for i in range(5):
        icon = atlas.crop((320 + i * 28, 0, 348 + i * 28, 28))
        col, row = i % 3, i // 3
        scene.alpha_composite(icon, (x0 + SCREEN[0] + 14 + col * 44, y0 + SCREEN[1] + 120 + row * 46))
    scene.alpha_composite(atlas.crop((472, 48, 481, 57)), (x0 + SCREEN[0] + 14 + 44 + 22, y0 + SCREEN[1] + 117))
    scene.alpha_composite(atlas.crop((0, 0, 168, 280)), (x0, y0))
    strip = Image.new("RGBA", (260, 30), (30, 30, 30, 255))
    strip.alpha_composite(atlas.crop((320, 32, 440, 42)), (4, 2))
    for k in range(3):
        strip.alpha_composite(atlas.crop((320, 48 + 14 * k, 360, 62 + 14 * k)), (4 + k * 44, 14))
    strip.alpha_composite(atlas.crop((364, 48, 404, 60)), (140, 14))
    strip.alpha_composite(atlas.crop((364, 60, 404, 72)), (184, 14))
    strip.alpha_composite(atlas.crop((408, 48, 436, 60)), (228, 2))
    out = Image.new("RGBA", (260, 330), (0, 0, 0, 255))
    out.alpha_composite(scene, (0, 0))
    out.alpha_composite(strip, (0, 300))
    return out.resize((260 * 3, 330 * 3), Image.NEAREST)


def main():
    os.makedirs(OUT, exist_ok=True)
    atlas = build()
    atlas.save(os.path.join(OUT, "phone.png"))
    preview(atlas).save(os.path.join(HERE, "phone_preview.png"))
    for name, (x, y, w, h) in ATLAS.items():
        print(f"{name}: u={x} v={y} {w}x{h}")


if __name__ == "__main__":
    main()
