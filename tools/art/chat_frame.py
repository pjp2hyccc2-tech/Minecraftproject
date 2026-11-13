#!/usr/bin/env python3
"""The small framed chat's pieces (ui/chat/FantasyChat): pixel art at one texel per GUI unit.

    python3 tools/art/chat_frame.py  ->  src/main/resources/assets/eternalrealm/textures/gui/chat/frame.png
                                         tools/art/chat_frame_preview.png

ATLAS below is the single source of the layout; ui/chat/ChatArt's constants must match it. Deterministic, original.
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from gate_screen import GOLD, Px, emboss, hexa  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "chat")

SIZE = 64
ATLAS = {
    "panel": (0, 0, 32, 32),     # nine-slice, 8 px corners: a gold band 4 px wide, a sapphire at each corner
    "crest": (32, 0, 32, 10),    # sits on the middle of the panel's top edge
    "input": (0, 32, 32, 16),    # three-slice, 4 px caps: the frame of the line you type in
}

OUTLINE = hexa("#1a0e04")
INNER = hexa("#0a0612", 200)
SAPPHIRE = {"light": hexa("#bcd6ff"), "mid": hexa("#3a72f0"), "dark": hexa("#12307a")}


def band(px, ox, oy, w, h):
    """The gold band around a w x h rectangle: outline, light, mid (dark on the lower and right sides), inner line."""
    for x in range(w):
        for y in range(h):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d >= 4:
                continue
            lower = (h - 1 - y) <= d or (w - 1 - x) <= d
            if d == 0:
                c = OUTLINE
            elif d == 1:
                c = GOLD["d"] if lower else GOLD["l"]
            elif d == 2:
                c = GOLD["m"] if not lower else GOLD["d"]
            else:
                c = INNER
            px.set(ox + x, oy + y, c)


def corner_gem(px, cx, cy):
    """A small diamond setting with a sapphire, centred on (cx, cy)."""
    setting = np.zeros((9, 9), bool)
    for y in range(9):
        for x in range(9):
            if abs(x - 4) + abs(y - 4) <= 3:
                setting[y, x] = True
    emboss(px, setting, GOLD, cx - 4, cy - 4)
    for (dx, dy, k) in ((0, -1, "light"), (-1, 0, "light"), (0, 0, "mid"), (1, 0, "dark"), (0, 1, "dark")):
        px.set(cx + dx, cy + dy, SAPPHIRE[k])


def panel(px, ox, oy):
    band(px, ox, oy, 32, 32)
    for (cx, cy) in ((2, 2), (29, 2), (2, 29), (29, 29)):
        corner_gem(px, ox + cx, oy + cy)


def crest(px, ox, oy):
    """A sapphire in a gold diamond with a scroll of filigree curling out to each side (32 x 10)."""
    ss = 8
    im = Image.new("L", (32 * ss, 10 * ss), 0)
    draw = ImageDraw.Draw(im)
    draw.polygon([(16 * ss, 0.3 * ss), (20.9 * ss, 4.9 * ss), (16 * ss, 9.6 * ss), (11.1 * ss, 4.9 * ss)], fill=255)
    right = [(19.5, 5.6), (22.0, 6.4), (24.6, 6.3), (26.8, 5.4), (28.3, 4.0), (28.6, 2.6), (27.9, 1.6),
             (26.8, 1.5), (26.1, 2.3), (26.4, 3.2)]
    for side in (1, -1):
        pts = [((16 + side * (x - 16)) * ss, y * ss) for x, y in right]
        draw.line(pts, fill=255, width=int(1.35 * ss), joint="curve")
        tip = pts[-1]
        r = 0.9 * ss
        draw.ellipse([tip[0] - r, tip[1] - r, tip[0] + r, tip[1] + r], fill=255)
    m = np.asarray(im.resize((32, 10), Image.BOX), np.float32) / 255.0 > 0.42
    emboss(px, m, GOLD, ox, oy)
    for (dx, dy, k) in ((15, 3, "light"), (16, 3, "light"), (14, 4, "light"), (15, 4, "mid"), (16, 4, "mid"),
                        (17, 4, "dark"), (15, 5, "mid"), (16, 5, "dark"), (15, 6, "dark"), (16, 6, "dark")):
        px.set(ox + dx, oy + dy, SAPPHIRE[k])
    px.set(ox + 15, oy + 3, hexa("#ffffff"))


def input_frame(px, ox, oy):
    """Caps of gold with a sapphire stud, and a thin gold band along the top and the bottom (32 x 16)."""
    w, h = 32, 16
    for x in range(w):
        for y in range(h):
            cap = x < 4 or x >= w - 4
            edge = y < 2 or y >= h - 2
            if not cap and not edge:
                continue
            if cap:
                local = x if x < 4 else w - 1 - x
                if local == 0 or y == 0 or y == h - 1:
                    c = OUTLINE
                elif local == 1:
                    c = GOLD["l"] if x < 4 else GOLD["d"]
                elif local == 2:
                    c = GOLD["m"]
                else:
                    c = GOLD["d"] if x < 4 else GOLD["m"]
            else:
                if y == 0 or y == h - 1:
                    c = OUTLINE
                else:
                    c = GOLD["l"] if y < 2 else GOLD["d"]
            px.set(ox + x, oy + y, c)
    for side in (1, w - 3):
        px.set(ox + side, oy + 7, SAPPHIRE["light"])
        px.set(ox + side + 1, oy + 7, SAPPHIRE["mid"])
        px.set(ox + side, oy + 8, SAPPHIRE["mid"])
        px.set(ox + side + 1, oy + 8, SAPPHIRE["dark"])


def build():
    px = Px(SIZE, SIZE)
    panel(px, *ATLAS["panel"][:2])
    crest(px, *ATLAS["crest"][:2])
    input_frame(px, *ATLAS["input"][:2])
    return px.image()


def preview(atlas):
    """The panel over a dark scene with three lines, and the input bar, scaled up 4x."""
    w, h = 220, 90
    scene = Image.new("RGBA", (w, h), (70, 90, 60, 255))
    bg = Image.new("RGBA", (180, 50), (14, 18, 38, 190))
    scene.alpha_composite(bg, (6, 14))
    piece = atlas.crop((0, 0, 32, 32))

    def nine(img, x, y, ww, hh, s=8):
        src = piece
        parts = [((0, 0, s, s), (x, y, s, s)), ((32 - s, 0, 32, s), (x + ww - s, y, s, s)),
                 ((0, 32 - s, s, 32), (x, y + hh - s, s, s)), ((32 - s, 32 - s, 32, 32), (x + ww - s, y + hh - s, s, s)),
                 ((s, 0, 32 - s, s), (x + s, y, ww - 2 * s, s)), ((s, 32 - s, 32 - s, 32), (x + s, y + hh - s, ww - 2 * s, s)),
                 ((0, s, s, 32 - s), (x, y + s, s, hh - 2 * s)), ((32 - s, s, 32, 32 - s), (x + ww - s, y + s, s, hh - 2 * s))]
        for (box, (tx, ty, tw, th)) in parts:
            if tw > 0 and th > 0:
                img.alpha_composite(src.crop(box).resize((tw, th), Image.NEAREST), (tx, ty))

    nine(scene, 6, 14, 180, 50)
    scene.alpha_composite(atlas.crop((32, 0, 64, 10)), (6 + 90 - 16, 14 - 5))
    bar = atlas.crop((0, 32, 32, 48))
    left = bar.crop((0, 0, 4, 16))
    mid = bar.crop((4, 0, 28, 16)).resize((200, 16), Image.NEAREST)
    right = bar.crop((28, 0, 32, 16))
    scene.alpha_composite(Image.new("RGBA", (208, 12), (0, 0, 0, 128)), (6, 72))
    scene.alpha_composite(left, (4, 70))
    scene.alpha_composite(mid, (8, 70))
    scene.alpha_composite(right, (208, 70))
    return scene.resize((w * 4, h * 4), Image.NEAREST)


def main():
    os.makedirs(OUT, exist_ok=True)
    atlas = build()
    atlas.save(os.path.join(OUT, "frame.png"))
    preview(atlas).save(os.path.join(HERE, "chat_frame_preview.png"))
    for name, (x, y, w, h) in ATLAS.items():
        print(f"{name}: u={x} v={y} {w}x{h}")


if __name__ == "__main__":
    main()
