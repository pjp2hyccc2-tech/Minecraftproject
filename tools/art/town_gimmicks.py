#!/usr/bin/env python3
"""Art for the capital's moving decorations: flag cloths, the tower clock dial and hands, windmill sails, the water
wheel, item icons, block models/blockstates/loot/tags and the spray/smoke particle definitions.

    python3 tools/art/town_gimmicks.py      # writes assets + data, and tools/art/town_gimmicks_preview.png

Everything is drawn procedurally (supersampled vector shapes, seeded noise); no fonts or external images.
"""
from __future__ import annotations

import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from px import seed_of  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "eternalrealm"
SS = 6  # supersampling factor for the vector artwork


# =========================================================================== helpers
def rng(name: str) -> random.Random:
    return random.Random(seed_of("gimmick:" + name))


def hexrgb(value: str, alpha: int = 255) -> tuple:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha


def mixc(a, b, f):
    return tuple(int(round(a[i] + (b[i] - a[i]) * f)) for i in range(len(a)))


def big(w: int, h: int, fill=(0, 0, 0, 0)) -> Image.Image:
    return Image.new("RGBA", (w * SS, h * SS), fill)


def shrink(img: Image.Image, w: int, h: int) -> Image.Image:
    """Downsample with premultiplied alpha so transparent edges keep their colour."""
    return img.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")


def poly(draw: ImageDraw.ImageDraw, pts, fill, scale: float = SS):
    draw.polygon([(x * scale, y * scale) for x, y in pts], fill=fill)


def ellipse(draw: ImageDraw.ImageDraw, cx, cy, rx, ry, fill=None, outline=None, width=0, scale: float = SS):
    draw.ellipse([(cx - rx) * scale, (cy - ry) * scale, (cx + rx) * scale, (cy + ry) * scale], fill=fill,
                 outline=outline, width=int(width * scale))


def rect(draw: ImageDraw.ImageDraw, x0, y0, x1, y1, fill, scale: float = SS):
    draw.rectangle([x0 * scale, y0 * scale, x1 * scale - 1, y1 * scale - 1], fill=fill)


def line(draw: ImageDraw.ImageDraw, pts, fill, width, scale: float = SS):
    draw.line([(x * scale, y * scale) for x, y in pts], fill=fill, width=max(1, int(round(width * scale))),
              joint="curve")


def weave(img: Image.Image, name: str, amount: float = 0.05, warp: float = 0.03) -> Image.Image:
    """Cloth: a fine weave pattern and slow dye unevenness multiplied into the colours (alpha untouched)."""
    a = np.asarray(img).astype(np.float64)
    h, w = a.shape[:2]
    r = rng(name)
    yy, xx = np.mgrid[0:h, 0:w]
    pattern = 1.0 + amount * (((xx + yy) % 2) * 2 - 1) * 0.5
    phase = r.random() * 6.28
    blotch = 1.0 + warp * np.sin(xx * 0.09 + phase) * np.sin(yy * 0.13 + phase * 0.7)
    noise = 1.0 + (np.array([[r.random() for _ in range(w)] for _ in range(h)]) - 0.5) * amount
    a[..., :3] *= (pattern * blotch * noise)[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def save(img: Image.Image, *parts: str) -> Image.Image:
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return img


GOLD = [hexrgb("#5E420F"), hexrgb("#9C6F1E"), hexrgb("#D6A63A"), hexrgb("#F2CF6B"), hexrgb("#FFF1BE")]


# =========================================================================== heraldry
def star_points(cx, cy, r_out, r_in, points=8, rot=-math.pi / 2):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + math.pi * i / points
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def draw_crest(d: ImageDraw.ImageDraw, cx, cy, size, gold, dark):
    """The kingdom crest: an eight-pointed star between two wings, a crown above and a sword below."""
    s = size
    # wings: rows of long feathers fanning up and out from the star, darker quills between them
    for side in (-1, 1):
        root = (cx + side * s * 0.2, cy - s * 0.02)
        for k in range(7):
            a = math.radians(-62 + k * 13.5)                     # fan from steep to shallow
            length = s * (0.62 - 0.045 * k)
            width = s * 0.07
            dx, dy = math.cos(a) * side, math.sin(a)
            tip = (root[0] + dx * length, root[1] + dy * length)
            nx, ny = -dy * side, dx * side
            base_l = (root[0] + nx * width * 0.4, root[1] + ny * width * 0.4)
            base_r = (root[0] - nx * width * 0.4, root[1] - ny * width * 0.4)
            mid_l = (root[0] + dx * length * 0.6 + nx * width, root[1] + dy * length * 0.6 + ny * width)
            mid_r = (root[0] + dx * length * 0.6 - nx * width, root[1] + dy * length * 0.6 - ny * width)
            poly(d, [base_l, mid_l, tip, mid_r, base_r], gold)
            line(d, [(root[0] + dx * length * 0.15, root[1] + dy * length * 0.15),
                     (root[0] + dx * length * 0.85, root[1] + dy * length * 0.85)], dark, s * 0.012)
        # covert feathers over the roots
        for k in range(4):
            a = math.radians(-40 + k * 16)
            length = s * 0.28
            dx, dy = math.cos(a) * side, math.sin(a)
            tip = (root[0] + dx * length, root[1] + dy * length)
            nx, ny = -dy * side, dx * side
            poly(d, [(root[0] + nx * s * 0.05, root[1] + ny * s * 0.05), tip,
                     (root[0] - nx * s * 0.05, root[1] - ny * s * 0.05)], mixc(gold, dark, 0.25))
    # star with a darker inner star for depth
    poly(d, star_points(cx, cy, s * 0.36, s * 0.15), gold)
    poly(d, star_points(cx, cy, s * 0.18, s * 0.08, rot=-math.pi / 2 + math.pi / 8), dark)
    ellipse(d, cx, cy, s * 0.06, s * 0.06, fill=gold)
    # crown
    cy0 = cy - s * 0.58
    poly(d, [(cx - s * 0.22, cy0 + s * 0.14), (cx - s * 0.25, cy0 - s * 0.06), (cx - s * 0.12, cy0 + s * 0.04),
             (cx, cy0 - s * 0.12), (cx + s * 0.12, cy0 + s * 0.04), (cx + s * 0.25, cy0 - s * 0.06),
             (cx + s * 0.22, cy0 + s * 0.14)], gold)
    for x in (-0.25, 0.0, 0.25):
        ellipse(d, cx + x * s, cy0 - s * (0.08 if x else 0.14), s * 0.035, s * 0.035, fill=gold)
    rect(d, cx - s * 0.22, cy0 + s * 0.15, cx + s * 0.22, cy0 + s * 0.2, dark)
    # sword below
    poly(d, [(cx - s * 0.035, cy + s * 0.38), (cx + s * 0.035, cy + s * 0.38), (cx + s * 0.02, cy + s * 0.78),
             (cx, cy + s * 0.84), (cx - s * 0.02, cy + s * 0.78)], gold)
    rect(d, cx - s * 0.16, cy + s * 0.34, cx + s * 0.16, cy + s * 0.39, gold)


def fringe(img: Image.Image, color, edge: str, depth: int, name: str):
    """Gold fringe: strands with gaps along an edge (cutout transparency makes them real tassels)."""
    r = rng(name)
    a = np.asarray(img).copy()
    h, w = a.shape[:2]
    shade = [mixc(color, (60, 40, 10, 255), 0.45), color, mixc(color, (255, 250, 220, 255), 0.35)]
    if edge == "right":
        for y in range(h):
            strand = y % 3 != 2
            length = depth - (1 if r.random() < 0.3 else 0)
            for x in range(w - depth, w):
                k = x - (w - depth)
                if strand and k < length:
                    a[y, x] = shade[(y + k) % 3] if k > 0 else shade[0]
                elif k > 0:
                    a[y, x] = (0, 0, 0, 0)
    elif edge == "bottom":
        for x in range(w):
            strand = x % 3 != 2
            length = depth - (1 if r.random() < 0.3 else 0)
            for y in range(h - depth, h):
                k = y - (h - depth)
                if strand and k < length:
                    a[y, x] = shade[(x + k) % 3] if k > 0 else shade[0]
                elif k > 0:
                    a[y, x] = (0, 0, 0, 0)
    return Image.fromarray(a, "RGBA")


# =========================================================================== flags (120 x 80, pennant 192 x 32)
FW, FH = 120, 80


def flag_kingdom() -> Image.Image:
    blue, deep = hexrgb("#2348A8"), hexrgb("#132C6E")
    gold, dark = GOLD[3], GOLD[1]
    img = big(FW, FH, blue)
    d = ImageDraw.Draw(img)
    # deeper blue towards the fly end, a gold border line inside the edges
    for i in range(24):
        f = i / 24
        rect(d, FW * (0.55 + 0.45 * f), 0, FW, FH, mixc(blue, deep, f * 0.6))
    for (x0, y0, x1, y1) in ((3, 3, FW - 8, 4.4), (3, FH - 4.4, FW - 8, FH - 3), (3, 3, 4.4, FH - 3),
                             (FW - 9.4, 3, FW - 8, FH - 3)):
        rect(d, x0, y0, x1, y1, GOLD[2])
    draw_crest(d, FW * 0.47, FH * 0.47, FH * 0.62, gold, dark)
    out = weave(shrink(img, FW, FH), "kingdom")
    return fringe(out, GOLD[3], "right", 6, "kingdom")


def flag_guild() -> Image.Image:
    red, deep = hexrgb("#A3202F"), hexrgb("#5E0F19")
    img = big(FW, FH, red)
    d = ImageDraw.Draw(img)
    for i in range(24):
        f = i / 24
        rect(d, FW * (0.5 + 0.5 * f), 0, FW, FH, mixc(red, deep, f * 0.55))
    rect(d, 0, 0, FW, 9, GOLD[2])                               # gold chief band
    rect(d, 0, 9, FW, 10.5, GOLD[1])
    for i in range(7):                                           # small studs on the band
        ellipse(d, 10 + i * 15, 4.5, 1.6, 1.6, fill=GOLD[4])
    cx, cy = FW * 0.42, FH * 0.56
    # shield behind
    poly(d, [(cx - 15, cy - 18), (cx + 15, cy - 18), (cx + 15, cy + 2), (cx, cy + 20), (cx - 15, cy + 2)],
         hexrgb("#E8E1CF"))
    poly(d, [(cx - 12.5, cy - 15.5), (cx + 12.5, cy - 15.5), (cx + 12.5, cy + 1), (cx, cy + 16.5),
             (cx - 12.5, cy + 1)], hexrgb("#1F3F99"))
    # crossed swords
    for side in (-1, 1):
        tip = (cx + side * 26, cy - 26)
        pommel = (cx - side * 22, cy + 22)
        line(d, [pommel, tip], hexrgb("#D9DEE6"), 3.4)
        line(d, [pommel, tip], hexrgb("#9AA3B0"), 1.2)
        bx, by = tip[0] - pommel[0], tip[1] - pommel[1]
        blen = math.hypot(bx, by)
        ux, uy = bx / blen, by / blen
        gx, gy = pommel[0] + ux * 10, pommel[1] + uy * 10
        line(d, [(gx - uy * 6, gy + ux * 6), (gx + uy * 6, gy - ux * 6)], GOLD[3], 2.6)
        line(d, [pommel, (gx, gy)], hexrgb("#5A3A1E"), 2.4)
        ellipse(d, pommel[0], pommel[1], 2.6, 2.6, fill=GOLD[3])
    ellipse(d, cx, cy - 2, 3.2, 3.2, fill=GOLD[4])
    out = weave(shrink(img, FW, FH), "guild")
    # swallowtail: a V cut out of the fly end
    a = np.asarray(out).copy()
    for y in range(FH):
        cut = int(round(26 * (1.0 - abs(y - FH / 2) / (FH / 2))))
        if cut > 0:
            a[y, FW - cut:] = (0, 0, 0, 0)
    return Image.fromarray(a, "RGBA")


def flag_cathedral() -> Image.Image:
    white = hexrgb("#F3EFE6")
    img = big(FW, FH, white)
    d = ImageDraw.Draw(img)
    for (x0, y0, x1, y1) in ((0, 0, FW, 5), (0, FH - 5, FW, FH)):
        rect(d, x0, y0, x1, y1, hexrgb("#4C6FC4"))
    rect(d, 0, 5, FW, 6.4, GOLD[2])
    rect(d, 0, FH - 6.4, FW, FH - 5, GOLD[2])
    cx, cy = FW * 0.45, FH * 0.5
    # radiant rays behind the star
    for i in range(24):
        a = math.pi * 2 * i / 24
        length = 30 if i % 2 == 0 else 22
        poly(d, [(cx + math.cos(a - 0.05) * 6, cy + math.sin(a - 0.05) * 6),
                 (cx + math.cos(a) * length, cy + math.sin(a) * length),
                 (cx + math.cos(a + 0.05) * 6, cy + math.sin(a + 0.05) * 6)], mixc(GOLD[3], white, 0.35))
    poly(d, star_points(cx, cy, 20, 8), GOLD[2])
    poly(d, star_points(cx, cy, 16, 6.5), GOLD[3])
    ellipse(d, cx, cy, 4.2, 4.2, fill=GOLD[4])
    out = weave(shrink(img, FW, FH), "cathedral", amount=0.035)
    return fringe(out, GOLD[3], "right", 5, "cathedral")


def flag_market() -> Image.Image:
    red, white = hexrgb("#C7362E"), hexrgb("#F4EEDF")
    img = big(FW, FH, white)
    d = ImageDraw.Draw(img)
    stripes = 7
    for i in range(stripes):
        if i % 2 == 0:
            rect(d, 0, FH * i / stripes, FW, FH * (i + 1) / stripes, red)
    # a gold coin with scales on a white roundel near the hoist
    cx, cy = FW * 0.28, FH * 0.5
    ellipse(d, cx, cy, 17, 17, fill=white)
    ellipse(d, cx, cy, 15, 15, fill=GOLD[2])
    ellipse(d, cx, cy, 12.5, 12.5, fill=GOLD[3])
    line(d, [(cx, cy - 8), (cx, cy + 7)], GOLD[0], 1.4)
    line(d, [(cx - 8, cy - 5), (cx + 8, cy - 5)], GOLD[0], 1.4)
    for side in (-1, 1):
        line(d, [(cx + side * 8, cy - 5), (cx + side * 10, cy + 2)], GOLD[0], 0.8)
        line(d, [(cx + side * 8, cy - 5), (cx + side * 6, cy + 2)], GOLD[0], 0.8)
        d.chord([(cx + side * 8 - 4) * SS, (cy - 1) * SS, (cx + side * 8 + 4) * SS, (cy + 5) * SS], 0, 180,
                fill=GOLD[0])
    rect(d, cx - 5, cy + 7, cx + 5, cy + 8.5, GOLD[0])
    out = weave(shrink(img, FW, FH), "market")
    return fringe(out, GOLD[3], "bottom", 4, "market")


def flag_harbour() -> Image.Image:
    sky, deep = hexrgb("#5BA3DB"), hexrgb("#2F6FAE")
    img = big(FW, FH, sky)
    d = ImageDraw.Draw(img)
    for i in range(20):
        f = i / 20
        rect(d, 0, FH * (0.6 + 0.4 * f), FW, FH, mixc(sky, deep, f))
    white = hexrgb("#FBFBF6")
    cx, cy = FW * 0.46, FH * 0.5
    # hull
    poly(d, [(cx - 26, cy + 9), (cx + 28, cy + 9), (cx + 20, cy + 18), (cx - 20, cy + 18)], white)
    # masts and sails
    for mx, h, w in ((cx - 10, 30, 13), (cx + 8, 34, 15)):
        line(d, [(mx, cy + 9), (mx, cy + 9 - h)], white, 1.4)
        poly(d, [(mx + 1.2, cy + 6 - h), (mx + 1.2 + w, cy + 2 - h * 0.45), (mx + 1.2, cy + 5)], white)
        poly(d, [(mx - 1.2, cy + 10 - h), (mx - 1.2 - w * 0.7, cy + 5 - h * 0.3), (mx - 1.2, cy + 5)],
             mixc(white, sky, 0.18))
    poly(d, [(cx + 22, cy - 2), (cx + 36, cy + 7), (cx + 22, cy + 7)], white)   # jib
    # waves
    for row, y in enumerate((FH * 0.8, FH * 0.9)):
        pts = []
        for i in range(0, FW + 1, 2):
            pts.append((i, y + math.sin(i * 0.35 + row * 1.7) * 1.6))
        line(d, pts, white, 1.3)
    out = weave(shrink(img, FW, FH), "harbour")
    return fringe(out, white, "right", 4, "harbour")


def flag_pennant() -> Image.Image:
    w, h = 192, 32
    blue, gold = hexrgb("#2348A8"), GOLD[3]
    img = big(w, h, blue)
    d = ImageDraw.Draw(img)
    rect(d, 0, h * 0.42, w, h * 0.58, gold)
    rect(d, 0, h * 0.46, w, h * 0.54, GOLD[4])
    rect(d, 0, 0, w, 2, GOLD[2])
    rect(d, 0, h - 2, w, h, GOLD[2])
    ellipse(d, 13, h / 2, 9, 9, fill=GOLD[2])
    poly(d, star_points(13, h / 2, 7.5, 3.0), GOLD[4])
    return weave(shrink(img, w, h), "pennant")


# =========================================================================== clock
ROMAN = ["XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"]


def numeral_glyph(ch: str, size: float) -> list:
    """Strokes of a serif Roman letter in a unit box (x right, y down), as polygons."""
    w = 0.16  # stroke weight
    if ch == "I":
        return [[(0.5 - w / 2, 0.08), (0.5 + w / 2, 0.08), (0.5 + w / 2, 0.92), (0.5 - w / 2, 0.92)],
                [(0.22, 0.0), (0.78, 0.0), (0.78, 0.09), (0.22, 0.09)],
                [(0.22, 0.91), (0.78, 0.91), (0.78, 1.0), (0.22, 1.0)]]
    if ch == "V":
        return [[(0.0, 0.0), (0.2, 0.0), (0.56, 0.86), (0.46, 1.0)],
                [(0.8, 0.0), (1.0, 0.0), (0.56, 1.0), (0.48, 0.86)]]
    if ch == "X":
        return [[(0.0, 0.0), (0.2, 0.0), (1.0, 1.0), (0.8, 1.0)],
                [(0.8, 0.0), (1.0, 0.0), (0.2, 1.0), (0.0, 1.0)]]
    raise ValueError(ch)


def draw_numeral(img: Image.Image, text: str, cx: float, cy: float, height: float, angle: float, color):
    """Draws a numeral centred at (cx, cy) (supersampled coordinates), rotated so its foot points at the centre."""
    widths = {"I": 0.42, "V": 0.85, "X": 0.85}
    gap = 0.08
    total = sum(widths[c] for c in text) + gap * (len(text) - 1)
    layer = Image.new("RGBA", (int(height * (total + 0.4)), int(height * 1.4)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x = height * 0.2
    for c in text:
        cw = widths[c] * height
        for p in numeral_glyph(c, height):
            d.polygon([(x + px_ * cw, height * 0.2 + py * height) for px_, py in p], fill=color)
        x += cw + gap * height
    rotated = layer.rotate(-math.degrees(angle), resample=Image.BICUBIC, expand=True)
    img.alpha_composite(rotated, (int(cx - rotated.width / 2), int(cy - rotated.height / 2)))


def clock_dial() -> Image.Image:
    n = 256
    S = 4
    c = n * S / 2
    img = Image.new("RGBA", (n * S, n * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def circ(r, fill=None, outline=None, width=0):
        d.ellipse([c - r, c - r, c + r, c + r], fill=fill, outline=outline, width=width)

    R = c - 1
    ivory, ivory_dark = hexrgb("#F4EDDA"), hexrgb("#E2D6B8")
    blue = hexrgb("#1C2D63")
    circ(R, fill=blue)                                           # chapter ring enamel
    circ(R * 0.985, outline=GOLD[2], width=int(R * 0.012))
    circ(R * 0.72, fill=GOLD[2])                                 # gold line inside the ring
    circ(R * 0.70, fill=ivory)
    # soft shading of the enamel: darker towards the rim
    for i in range(30):
        f = i / 30
        circ(R * 0.70 * (1 - f * 0.5), fill=mixc(ivory_dark, ivory, min(1.0, f * 1.6)))
    # minute track on the outer edge of the ring
    for m in range(60):
        a = math.pi * 2 * m / 60 - math.pi / 2
        r0 = R * (0.93 if m % 5 else 0.9)
        r1 = R * 0.975
        wdt = R * (0.012 if m % 5 else 0.02)
        p0 = (c + math.cos(a) * r0, c + math.sin(a) * r0)
        p1 = (c + math.cos(a) * r1, c + math.sin(a) * r1)
        d.line([p0, p1], fill=GOLD[3], width=int(wdt))
    # numerals on the blue ring, feet towards the centre
    for i, text in enumerate(ROMAN):
        a = math.pi * 2 * i / 12
        rr = R * 0.81
        x = c + math.sin(a) * rr
        y = c - math.cos(a) * rr
        draw_numeral(img, text, x, y, R * 0.135, a, GOLD[3])
    # filigree: a ring of small lobes and a sunburst in the centre
    for i in range(24):
        a = math.pi * 2 * i / 24
        x = c + math.cos(a) * R * 0.6
        y = c + math.sin(a) * R * 0.6
        circ_r = R * 0.035
        d.ellipse([x - circ_r, y - circ_r, x + circ_r, y + circ_r], outline=GOLD[1], width=int(R * 0.008))
    circ(R * 0.62, outline=GOLD[1], width=int(R * 0.006))
    circ(R * 0.58, outline=GOLD[1], width=int(R * 0.006))
    for i in range(32):
        a = math.pi * 2 * i / 32
        length = R * (0.36 if i % 2 == 0 else 0.26)
        w = 0.035
        pts = [(c + math.cos(a - w) * R * 0.08, c + math.sin(a - w) * R * 0.08),
               (c + math.cos(a) * length, c + math.sin(a) * length),
               (c + math.cos(a + w) * R * 0.08, c + math.sin(a + w) * R * 0.08)]
        d.polygon(pts, fill=mixc(GOLD[3], ivory, 0.45))
    circ(R * 0.1, fill=mixc(GOLD[2], ivory, 0.3))
    out = img.convert("RGBa").resize((n, n), Image.LANCZOS).convert("RGBA")
    # crisp transparent outside the circle (the cutout shader needs alpha 0 there)
    a = np.asarray(out).copy()
    yy, xx = np.mgrid[0:n, 0:n]
    outside = np.hypot(xx - (n - 1) / 2, yy - (n - 1) / 2) > n / 2 - 0.5
    a[outside] = (0, 0, 0, 0)
    a[~outside, 3] = 255
    return Image.fromarray(a, "RGBA")


def hand_sprite(length: int, pivot: int, kind: str) -> Image.Image:
    """A blued-steel hand with gold edging, pointing up; pivot row `pivot` (tip at row 0). 16 px wide."""
    w = 16
    S = 8
    img = Image.new("RGBA", (w * S, length * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    steel, edge = hexrgb("#1A2140"), GOLD[3]
    cx = w * S / 2
    p = pivot * S

    def P(x, y):
        return cx + x * S, y * S

    if kind == "hour":
        # spade hand: shaft, a pierced spade near the tip, a counterweight tail
        shaft = [P(-1.2, 22), P(1.2, 22), P(1.4, pivot), P(-1.4, pivot)]
        spade = [P(0, 2), P(5.5, 12), P(4, 19), P(1.5, 22), P(0, 20), P(-1.5, 22), P(-4, 19), P(-5.5, 12)]
        tail = [P(-1.3, pivot), P(1.3, pivot), P(3.0, length - 3), P(0, length - 1), P(-3.0, length - 3)]
        for shape in (shaft, spade, tail):
            d.polygon(shape, fill=edge)
        shrink_by = 0.9
        d.polygon([(cx + (x - cx) * 0.7, y) for x, y in spade], fill=steel)
        d.polygon([(cx + (x - cx) * 0.5, y) for x, y in shaft], fill=steel)
        d.ellipse([cx - 1.6 * S, 12 * S - 1.6 * S, cx + 1.6 * S, 12 * S + 1.6 * S], fill=(0, 0, 0, 0))
        del shrink_by
    else:
        # minute hand: long tapering shaft with a lozenge near the tip and a ring counterweight
        shaft = [P(-0.6, 4), P(0.6, 4), P(1.4, pivot), P(-1.4, pivot)]
        tip = [P(0, 0), P(2.4, 9), P(0, 16), P(-2.4, 9)]
        tail = [P(-1.3, pivot), P(1.3, pivot), P(1.0, length - 6), P(-1.0, length - 6)]
        for shape in (shaft, tip, tail):
            d.polygon(shape, fill=edge)
        d.polygon([(cx + (x - cx) * 0.55, y) for x, y in tip], fill=steel)
        d.polygon([(cx + (x - cx) * 0.4, y) for x, y in shaft], fill=steel)
        ry = (length - 4) * S
        d.ellipse([cx - 3.4 * S, ry - 3.4 * S, cx + 3.4 * S, ry + 3.4 * S], fill=edge)
        d.ellipse([cx - 1.8 * S, ry - 1.8 * S, cx + 1.8 * S, ry + 1.8 * S], fill=(0, 0, 0, 0))
    # the pivot boss
    d.ellipse([cx - 2.6 * S, p - 2.6 * S, cx + 2.6 * S, p + 2.6 * S], fill=edge)
    return img.convert("RGBa").resize((w, length), Image.LANCZOS).convert("RGBA")


def clock_parts() -> Image.Image:
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    img.alpha_composite(hand_sprite(64, 52, "hour"), (0, 0))
    img.alpha_composite(hand_sprite(96, 84, "minute"), (16, 0))
    # gold boss (32..48, 0..16)
    boss = Image.new("RGBA", (16 * 8, 16 * 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(boss)
    for i in range(12):
        f = i / 12
        r = 62 * (1 - f * 0.85)
        col = mixc(GOLD[1], GOLD[4], f)
        d.ellipse([64 - r - f * 10, 64 - r - f * 10, 64 + r - f * 10, 64 + r - f * 10], fill=col)
    img.alpha_composite(boss.convert("RGBa").resize((16, 16), Image.LANCZOS).convert("RGBA"), (32, 0))
    # bezel strip (48..64, 0..16): beads (0..8), outer rim (8..12), inner lip (12..16)
    strip = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            if y < 8:
                bx = (x % 8) - 3.5
                by = y - 3.5
                dist = math.hypot(bx, by) / 4.2
                if dist < 1.0:
                    light = max(0.0, 1.0 - dist) * 0.6 + (0.4 if bx + by < 0 else 0.0)
                    strip[y, x] = mixc(GOLD[2], GOLD[4], min(1.0, light))
                else:
                    strip[y, x] = GOLD[1]
            elif y < 12:
                strip[y, x] = mixc(GOLD[1], GOLD[3], (y - 8) / 4)
            else:
                strip[y, x] = mixc(GOLD[2], GOLD[0], (y - 12) / 4)
    img.alpha_composite(Image.fromarray(strip, "RGBA"), (48, 0))
    return img


# =========================================================================== windmill and water wheel
def wood_strip(w, h, base, dark, light, name, vertical=False, knots=2):
    r = rng(name)
    a = np.zeros((h, w, 4), np.float64)
    along, across = (h, w) if vertical else (w, h)
    for i in range(along):
        for j in range(across):
            grain = 0.5 + 0.5 * math.sin(j * 1.7 + math.sin(i * 0.08 + j) * 1.3 + r.random() * 0.4)
            col = mixc(dark, light, 0.25 + 0.5 * grain)
            col = mixc(col, base, 0.45)
            y, x = (i, j) if vertical else (j, i)
            a[y, x] = col
    for _ in range(knots):
        kx, ky = r.randrange(w), r.randrange(h)
        for y in range(h):
            for x in range(w):
                dd = math.hypot((x - kx) / 1.6, (y - ky))
                if dd < 1.6:
                    a[y, x, :3] *= 0.72
    a[..., 3] = 255
    return a


def windmill_texture() -> Image.Image:
    img = np.zeros((128, 128, 4), np.float64)
    r = rng("windmill")
    canvas = hexrgb("#EADFC4")
    frame = hexrgb("#6B4A2E")
    frame_hi = hexrgb("#8F6A44")
    # the sail: lattice (bars every 10 px across, three rails along) with canvas over most of it
    for y in range(128):
        for x in range(64):
            rail = x < 4 or 30 <= x < 33 or x >= 60
            bar = (y % 11) < 2
            near_hub = y > 112
            if rail or bar:
                img[y, x] = frame_hi if (x % 4 == 0 or y % 11 == 0) else frame
            elif not near_hub and 5 <= x <= 58:
                fold = 0.93 + 0.07 * math.sin(x * 0.45) + (r.random() - 0.5) * 0.03
                edge = 0.92 if (x < 7 or x > 56) else 1.0
                c = tuple(int(min(255, v * fold * edge)) for v in canvas[:3]) + (255,)
                img[y, x] = c
            else:
                img[y, x] = (0, 0, 0, 0)
    # reef lines (cords) across the canvas
    for y in range(6, 112, 22):
        for x in range(5, 59):
            if x % 3 != 0:
                img[y, x] = hexrgb("#B9A57E")
    # hub end (64..96, 0..32): end grain rings + iron cross straps and bolts
    for y in range(32):
        for x in range(32):
            dd = math.hypot(x - 15.5, y - 15.5)
            ring = 0.5 + 0.5 * math.sin(dd * 1.4)
            col = mixc(hexrgb("#4A321D"), hexrgb("#7A5534"), ring * 0.7)
            if abs(x - 15.5) < 3 or abs(y - 15.5) < 3:
                col = hexrgb("#3B3E44") if (x + y) % 5 else hexrgb("#5A5F67")
            if dd > 14.5:
                col = hexrgb("#2E3035")
            img[y, 64 + x] = col
    for (bx, by) in ((15, 4), (15, 27), (4, 15), (27, 15), (15, 15)):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                img[by + dy, 64 + bx + dx] = hexrgb("#8C939C") if dx + dy < 0 else hexrgb("#24262A")
    # axle strip (64..128, 32..48) and stock strip (64..128, 64..72)
    img[32:48, 64:128] = wood_strip(64, 16, hexrgb("#5B3F26"), hexrgb("#3A2716"), hexrgb("#7E5A38"), "axle")
    for x in (70, 92, 114):
        img[32:48, x:x + 3] = hexrgb("#33363B")
    img[64:72, 64:128] = wood_strip(64, 8, hexrgb("#6E4E30"), hexrgb("#4A321D"), hexrgb("#8F6A44"), "stock")
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def wheel_texture() -> Image.Image:
    img = np.zeros((64, 64, 4), np.float64)
    wet = wood_strip(64, 8, hexrgb("#4D3A26"), hexrgb("#2F2418"), hexrgb("#6B5236"), "paddle", knots=3)
    r = rng("wheel_moss")
    for y in range(8):
        for x in range(64):
            if (y >= 6 and r.random() < 0.35) or r.random() < 0.04:
                wet[y, x, :3] = (wet[y, x, :3] * 0.6 + np.array([62, 88, 40]) * 0.4)
    img[0:8] = wet
    for y in range(8, 12):
        for x in range(64):
            img[y, x] = hexrgb("#4A4E55") if y in (8, 11) else hexrgb("#5E636B")
            if x % 8 == 3 and y in (9, 10):
                img[y, x] = hexrgb("#9AA1AA") if y == 9 else hexrgb("#2A2C30")
    img[16:24] = wood_strip(64, 8, hexrgb("#5B4128"), hexrgb("#3A2716"), hexrgb("#7C5A37"), "wheel_axle")
    img[24:28] = wood_strip(64, 4, hexrgb("#634830"), hexrgb("#3E2B1A"), hexrgb("#83603C"), "spoke", knots=1)
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x - 7.5, y - 7.5)
            col = mixc(hexrgb("#3F2C1A"), hexrgb("#6E4F31"), 0.5 + 0.5 * math.sin(dd * 1.5))
            if dd > 6.2:
                col = hexrgb("#3E4249")
            if 2.0 < dd < 3.2:
                col = hexrgb("#4A4E55")
            img[32 + y, x] = col
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


# =========================================================================== block + item textures (16 x 16)
def pixels(rows, pal) -> Image.Image:
    a = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                a[y, x] = pal[ch]
    return Image.fromarray(a, "RGBA")


def flag_pole_texture() -> Image.Image:
    a = np.zeros((16, 16, 4), np.uint8)
    cols = [hexrgb("#1E2236"), hexrgb("#2E3452"), hexrgb("#47507A"), hexrgb("#2E3452")]
    for y in range(16):
        for x in range(16):
            a[y, x] = cols[x % 4]
            if y % 8 == 0:
                a[y, x] = GOLD[2]
    return Image.fromarray(a, "RGBA")


def mill_hub_front() -> Image.Image:
    base = wood_strip(16, 16, hexrgb("#5B3F26"), hexrgb("#3A2716"), hexrgb("#7E5A38"), "mill_front", vertical=True)
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x - 7.5, y - 7.5)
            if dd < 5.5:
                base[y, x] = hexrgb("#3B3E44") if dd > 2.5 else hexrgb("#24262A")
            if 4.6 < dd < 5.5:
                base[y, x] = hexrgb("#5A5F67")
    for (bx, by) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        base[by, bx] = hexrgb("#8C939C")
    return Image.fromarray(base.astype(np.uint8), "RGBA")


def wheel_bearing_front() -> Image.Image:
    base = wood_strip(16, 16, hexrgb("#6A4C30"), hexrgb("#44301C"), hexrgb("#8A6844"), "bearing", vertical=False)
    for y in range(4, 12):
        for x in range(4, 12):
            base[y, x] = hexrgb("#4A4E55") if (x in (4, 11) or y in (4, 11)) else hexrgb("#5E636B")
    for y in range(6, 10):
        for x in range(6, 10):
            base[y, x] = hexrgb("#2A2C30")
    for (bx, by) in ((5, 5), (10, 5), (5, 10), (10, 10)):
        base[by, bx] = hexrgb("#9AA1AA")
    return Image.fromarray(base.astype(np.uint8), "RGBA")


ICON_PAL = {
    "k": hexrgb("#1B1E2B"), "p": hexrgb("#3A4166"), "b": hexrgb("#2348A8"), "B": hexrgb("#132C6E"),
    "g": GOLD[2], "G": GOLD[3], "y": GOLD[4], "w": hexrgb("#F4EDDA"), "W": hexrgb("#FFFFFF"),
    "s": hexrgb("#9AA1AA"), "S": hexrgb("#5E636B"), "o": hexrgb("#6B4A2E"), "O": hexrgb("#8F6A44"),
    "c": hexrgb("#EADFC4"), "C": hexrgb("#C9BB98"), "l": hexrgb("#7FB8E8"), "L": hexrgb("#3F76E4"),
    "a": hexrgb("#B8BCC4"), "A": hexrgb("#D8DCE2"), "d": hexrgb("#7A7F88"), "r": hexrgb("#1C2D63"),
}

ICONS = {
    "flag": [
        "..y.............",
        ".yGg............",
        "..k.............",
        "..pbbbbbbbbbg...",
        "..pbbbbbbbbbbg..",
        "..pbbbbGbbbbbbg.",
        "..pbbbGyGbbbbbg.",
        "..pbbbbGbbbbbBg.",
        "..pbbbbbbbbbBBg.",
        "..pbbbbbbbbBBg..",
        "..pbbbbbbbBBg...",
        "..p.............",
        "..p.............",
        "..p.............",
        "..p.............",
        ".kpk............",
    ],
    "clock_face": [
        ".....gGGGg......",
        "...gGrrrrrGg....",
        "..GrrGrrrGrrG...",
        ".grrwwwwwwwrrg..",
        ".GrwwwwkwwwwrG..",
        "GrGwwwwkwwwwGrG.",
        "GrwwwwwkwwwwwrG.",
        "GrwwwwwkkkkwwrG.",
        "GrwwwwwwwwwwwrG.",
        "GrGwwwwwwwwwGrG.",
        ".GrwwwwwwwwwrG..",
        ".grrwwwwwwwrrg..",
        "..GrrGrrrGrrG...",
        "...gGrrrrrGg....",
        ".....gGGGg......",
        "................",
    ],
    "windmill_sails": [
        "c..............c",
        "Cc............cC",
        ".Cco........ocC.",
        "..CcO......OcC..",
        "...cco....occ...",
        "....ccO..Occ....",
        ".....cOooOc.....",
        "......oSSo......",
        "......oSSo......",
        ".....cOooOc.....",
        "....ccO..Occ....",
        "...cco....occ...",
        "..CcO......OcC..",
        ".Cco........ocC.",
        "Cc............cC",
        "c..............c",
    ],
    "water_wheel": [
        ".....oOOOo......",
        "...oOo.O.oOo....",
        "..oo...O...oo...",
        ".oO.O..O..O.Oo..",
        ".O...O.O.O...O..",
        "oO....OSO....Oo.",
        "OOOOOOSSSOOOOOO.",
        "oO....OSO....Oo.",
        ".O...O.O.O...O..",
        ".oO.O..O..O.Oo..",
        "..oo...O...oo...",
        "LLLoOoLOLoOoLLL.",
        "LlLLLLLLLLLLLlL.",
        "LLlLLLlLLLlLLLL.",
        "................",
        "................",
    ],
    "smoke_emitter": [
        "......aAa.......",
        "....aAAAAa......",
        "...aAAaaAAa.....",
        "....aaAAAa......",
        ".....aAAa.......",
        "......aa........",
        ".....aAa........",
        "......a.........",
        "....SSSSSSS.....",
        "....SdddddS.....",
        "....SdkkkdS.....",
        "....SdkkkdS.....",
        "....SdddddS.....",
        "....SSSSSSS.....",
        "................",
        "................",
    ],
    "fountain_spray": [
        ".......W........",
        "...l..WlW..l....",
        "..l..WlLlW..l...",
        ".l..l.lLl.l..l..",
        ".l.l..lLl..l.l..",
        "l..l..lLl..l..l.",
        "l.....lLl.....l.",
        "......lLl.......",
        "......lLl.......",
        "......lLl.......",
        "l..l.lLLLl.l..l.",
        ".LlLLLLLLLLLLlL.",
        ".sssssssssssss..",
        ".SsssssssssssS..",
        "..SSSSSSSSSSS...",
        "................",
    ],
}


# =========================================================================== particles
def spray_sprite(i: int) -> Image.Image:
    n = 8
    a = np.zeros((n, n, 4), np.float64)
    stretch = 1.0 + i * 0.25
    for y in range(n):
        for x in range(n):
            dd = math.hypot((x - 3.5) / 3.2, (y - 3.5) / (3.2 * stretch))
            if dd < 1.0:
                hl = 1.0 if (x - 2.5) ** 2 + (y - 2.5) ** 2 < 1.5 else 0.0
                c = mixc((178, 214, 255), (255, 255, 255), hl)
                a[y, x] = (c[0], c[1], c[2], 255 * (1 - dd) ** 0.6)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# =========================================================================== models, states, loot, tags
def model(name, data):
    write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), data)


def item_icon(name):
    write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


def blockstate(name, data):
    write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), data)


def loot(name):
    write_json(os.path.join(DATA, NS, "loot_tables", "blocks", f"{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def facing_states(name, model_ref):
    return {"variants": {f"facing={f}": ({"model": model_ref, "y": y} if y else {"model": model_ref})
                         for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}


def merge_tag(path, values):
    existing = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            existing = json.load(f)
    merged = sorted(set(existing.get("values", [])) | set(values))
    write_json(path, {"replace": False, "values": merged})


def gen_blocks():
    t = lambda n: f"{NS}:block/{n}"  # noqa: E731
    # flag: a slim lacquered pole with a gold ball on top
    model("flag", {"textures": {"pole": t("flag_pole"), "gold": t("gilded_plain"), "particle": t("flag_pole")},
                   "elements": [
                       {"from": [7, 0, 7], "to": [9, 13, 9],
                        "faces": {f: {"texture": "#pole", "uv": [7, 3, 9, 16]} for f in
                                  ("north", "east", "south", "west")} | {"down": {"texture": "#pole"}}},
                       {"from": [6, 12, 6], "to": [10, 16, 10],
                        "faces": {f: {"texture": "#gold"} for f in
                                  ("north", "east", "south", "west", "up", "down")}},
                       {"from": [6.5, 11.5, 6.5], "to": [9.5, 12, 9.5],
                        "faces": {f: {"texture": "#gold"} for f in
                                  ("north", "east", "south", "west", "up", "down")}},
                   ]})
    blockstate("flag", facing_states("flag", f"{NS}:block/flag"))
    # facade mechanisms: orientable cubes (front = north in the model)
    model("clock_face", {"parent": "minecraft:block/cube_all", "textures": {"all": t("smooth_castle_stone")}})
    blockstate("clock_face", facing_states("clock_face", f"{NS}:block/clock_face"))
    model("windmill_sails", {"parent": "minecraft:block/orientable",
                             "textures": {"front": t("mill_hub_front"), "side": "minecraft:block/dark_oak_planks",
                                          "top": "minecraft:block/dark_oak_planks"}})
    blockstate("windmill_sails", facing_states("windmill_sails", f"{NS}:block/windmill_sails"))
    model("water_wheel", {"parent": "minecraft:block/orientable",
                          "textures": {"front": t("wheel_bearing_front"), "side": "minecraft:block/spruce_planks",
                                       "top": "minecraft:block/spruce_planks"}})
    blockstate("water_wheel", facing_states("water_wheel", f"{NS}:block/water_wheel"))
    # invisible emitters: particle texture only
    model("smoke_emitter", {"textures": {"particle": f"{NS}:item/smoke_emitter"}})
    blockstate("smoke_emitter", {"variants": {f"heavy={h}": {"model": f"{NS}:block/smoke_emitter"}
                                              for h in ("false", "true")}})
    model("fountain_spray", {"textures": {"particle": f"{NS}:item/fountain_spray"}})
    blockstate("fountain_spray", {"variants": {f"facing={f},power={p}": {"model": f"{NS}:block/fountain_spray"}
                                               for f in ("up", "north", "east", "south", "west")
                                               for p in (1, 2, 3)}})
    for name in ("flag", "clock_face", "windmill_sails", "water_wheel", "smoke_emitter", "fountain_spray"):
        item_icon(name)
        loot(name)
    tags = os.path.join(DATA, "minecraft", "tags", "blocks")
    merge_tag(os.path.join(tags, "mineable", "pickaxe.json"), [f"{NS}:clock_face"])
    merge_tag(os.path.join(tags, "mineable", "axe.json"),
              [f"{NS}:flag", f"{NS}:windmill_sails", f"{NS}:water_wheel"])


def main() -> int:
    flags = {
        "kingdom": flag_kingdom(), "guild": flag_guild(), "pennant": flag_pennant(),
        "cathedral": flag_cathedral(), "market": flag_market(), "harbour": flag_harbour(),
    }
    for name, img in flags.items():
        save(img, "entity", "flag", f"{name}.png")
    dial = save(clock_dial(), "entity", "clock", "dial.png")
    parts = save(clock_parts(), "entity", "clock", "parts.png")
    sails = save(windmill_texture(), "entity", "windmill", "sails.png")
    wheel = save(wheel_texture(), "entity", "water_wheel", "wheel.png")
    save(flag_pole_texture(), "block", "flag_pole.png")
    save(mill_hub_front(), "block", "mill_hub_front.png")
    save(wheel_bearing_front(), "block", "wheel_bearing_front.png")
    icons = {}
    for name, rows in ICONS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        icons[name] = save(pixels(rows, ICON_PAL), "item", f"{name}.png")
    for i in range(3):
        save(spray_sprite(i), "particle", f"spray_{i}.png")
    write_json(os.path.join(ASSETS, "particles", "spray.json"), {"textures": [f"{NS}:spray_{i}" for i in range(3)]})
    smoke = [f"minecraft:big_smoke_{i}" for i in range(12)]
    write_json(os.path.join(ASSETS, "particles", "chimney_smoke.json"), {"textures": smoke})
    write_json(os.path.join(ASSETS, "particles", "forge_smoke.json"), {"textures": smoke})
    gen_blocks()

    # preview sheet: flags at 2x in a grid, then the clock, sails, wheel and icons
    sheet = Image.new("RGBA", (1120, 700), (38, 42, 54, 255))
    cells = [n for n in flags if n != "pennant"]
    for i, name in enumerate(cells):
        img = flags[name].resize((FW * 3, FH * 3), Image.NEAREST)
        sheet.alpha_composite(img, (10 + (i % 3) * (FW * 3 + 10), 10 + (i // 3) * (FH * 3 + 10)))
    sheet.alpha_composite(flags["pennant"].resize((384, 64), Image.NEAREST), (10, 520))
    sheet.alpha_composite(dial.resize((180, 180), Image.LANCZOS), (410, 505))
    sheet.alpha_composite(parts.resize((192, 192), Image.NEAREST), (600, 500))
    sheet.alpha_composite(sails.resize((128, 128), Image.NEAREST), (800, 500))
    sheet.alpha_composite(wheel.resize((128, 128), Image.NEAREST), (940, 500))
    for i, img in enumerate(icons.values()):
        sheet.alpha_composite(img.resize((48, 48), Image.NEAREST), (10 + i * 58, 600))
    sheet.save(os.path.join(HERE, "town_gimmicks_preview.png"))
    print(f"{len(flags)} flags, dial, hands, sails, wheel, {len(icons)} icons")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
