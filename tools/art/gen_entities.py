#!/usr/bin/env python3
"""Paints the entity textures to match the box-UV layouts of the Java models.

  hanayome.png (128x64) + hanayome_eyes.png   <- client/render/HanayomeModel.java
  ichimatsu.png (64x32)                       <- client/render/IchimatsuModel.java
  kageboshi.png (64x64) + kageboshi_eyes.png  <- vanilla humanoid (zombie) layout

Run: python3 tools/art/gen_entities.py [--preview]
"""
import os
import random
import sys

from PIL import Image

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                                    "assets", "meikon", "textures", "entity"))
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")
R = random.Random(20261102)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def jitter(c, amt):
    if c[3] == 0:
        return c
    d = R.randint(-amt, amt)
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


class Box:
    """Texture regions of a model cube at (u, v) with size (w, h, d)."""

    def __init__(self, u, v, w, h, d):
        self.u, self.v, self.w, self.h, self.d = u, v, w, h, d

    def regions(self):
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }


def fill(img, rect, color_fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            img.putpixel((x0 + x, y0 + y), color_fn(x, y, w, h))


def put(img, rect, x, y, c):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), c)


# ======================================================================= the bride
SILK = [hexc("d9d4ca"), hexc("cfc9be"), hexc("e4dfd6"), hexc("c2bcb1")]
SILK_SHADOW = hexc("a8a196")
SKIN = hexc("d6d2cb")
SKIN_DARK = hexc("aaa59c")
HAIR = [hexc("0b0a0c"), hexc("141216"), hexc("1d1a1f"), hexc("070608")]
BLOOD = [hexc("4a0b0b"), hexc("5e1010"), hexc("3a0707")]
RED = hexc("7a1414")
GOLD = hexc("8f7a4a")
DIRT = hexc("6d6458")
CLEAR = (0, 0, 0, 0)


def silk(x, y, w, h):
    c = SILK[(x * 7 + y * 3 + R.randint(0, 3)) % len(SILK)]
    # vertical folds
    if (x + (y // 5)) % 4 == 0:
        c = SILK_SHADOW if R.random() < 0.5 else c
    return jitter(c, 5)


def hair(x, y, w, h):
    c = HAIR[(x * 3 + R.randint(0, 2)) % len(HAIR)]
    if R.random() < 0.05:
        c = hexc("2a2730")
    return jitter(c, 3)


def blood_splatter(img, rect, n, drip=True):
    x0, y0, w, h = rect
    for _ in range(n):
        cx, cy = R.randint(0, w - 1), R.randint(0, max(0, h - 4))
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if R.random() < 0.55:
                    put(img, rect, cx + dx, cy + dy, jitter(R.choice(BLOOD), 6))
        if drip:
            length = R.randint(2, 6)
            for k in range(length):
                put(img, rect, cx, cy + 1 + k, jitter(BLOOD[k % 3], 4))


def paint_bride():
    img = Image.new("RGBA", (128, 64), CLEAR)
    eyes = Image.new("RGBA", (128, 64), CLEAR)

    # head 7x8x7 at (0,0)
    head = Box(0, 0, 7, 8, 7).regions()
    for k in ("top", "bottom", "right", "left", "back"):
        fill(img, head[k], hair)
    face = head["front"]

    def facepix(x, y, w, h):
        c = jitter(SKIN, 5)
        if y >= 6:
            c = jitter(SKIN_DARK, 4)
        return c

    fill(img, face, facepix)
    # hair parting over the brow
    for x in range(7):
        put(img, face, x, 0, jitter(HAIR[0], 2))
        if x in (0, 1, 5, 6):
            put(img, face, x, 1, jitter(HAIR[1], 2))
    # sunken eyes: black voids with a pin-prick
    for ex in (1, 4):
        for dx in range(2):
            put(img, face, ex + dx, 3, hexc("050404"))
            put(img, face, ex + dx, 4, hexc("0b0808"))
        put(img, face, ex + dx - 1, 2, hexc("8d877e"))
        put(img, face, ex, 5, hexc("5a5048"))
    put(img, face, 2, 3, hexc("9b1a1a"))
    put(img, face, 4, 3, hexc("9b1a1a"))
    # a mouth stretched too wide
    for x in range(1, 6):
        put(img, face, x, 6, hexc("120808"))
    put(img, face, 0, 5, hexc("120808"))
    put(img, face, 6, 5, hexc("120808"))
    for x in (2, 4):
        put(img, face, x, 7, hexc("d8d2c0"))
    put(img, face, 3, 7, hexc("2a0606"))
    # glowing eyes (emissive layer)
    for ex in (2, 4):
        put(eyes, face, ex, 3, hexc("ff2a2a"))
        put(eyes, face, ex, 4, hexc("7a0a0a"))

    # hood (wataboshi) 9x10x9 at (28,0): silk with an opening for the face
    hood = Box(28, 0, 9, 10, 9).regions()
    for k in ("top", "right", "left", "back"):
        fill(img, hood[k], silk)
    fill(img, hood["front"], lambda x, y, w, h: silk(x, y, w, h) if (y < 2 or x == 0 or x == 8 or y == 9) else CLEAR)
    fill(img, hood["bottom"], lambda x, y, w, h: silk(x, y, w, h) if (x == 0 or x == 8 or y == 0 or y == 8) else CLEAR)
    # rim shading
    hx0, hy0, hw, hh = hood["front"]
    for x in range(1, 8):
        put(img, hood["front"], x, 1, SILK_SHADOW)

    # back hair 8x20x1 at (64,0)
    hb = Box(64, 0, 8, 20, 1).regions()
    for k in hb:
        fill(img, hb[k], hair)
    # ragged ends
    x0, y0, w, h = hb["back"]
    for x in range(w):
        cut = R.randint(0, 3)
        for y in range(h - cut, h):
            put(img, hb["back"], x, y, CLEAR)
            put(img, hb["front"], x, y, CLEAR)

    # hair hanging over the face 7x14x1 at (82,0): strands with gaps
    hf = Box(82, 0, 7, 14, 1).regions()
    strands = {0: 14, 1: 11, 2: 6, 4: 9, 5: 13, 6: 12}
    for k in ("front", "back"):
        x0, y0, w, h = hf[k]
        for x in range(w):
            length = strands.get(x, 0)
            for y in range(h):
                if y < 2 or y < length:
                    img.putpixel((x0 + x, y0 + y), hair(x, y, w, h))
    for k in ("left", "right", "top", "bottom"):
        fill(img, hf[k], hair)

    # torso 10x16x7 at (0,19): white kimono, crossed collar with a red under-collar, obi, blood
    torso = Box(0, 19, 10, 16, 7).regions()
    for k in torso:
        fill(img, torso[k], silk)
    tf = torso["front"]
    for y in range(0, 7):
        for x in (4 - y // 2, 5 + y // 2):
            put(img, tf, x, y, RED if y < 6 else SILK_SHADOW)
    for y in range(0, 3):
        put(img, tf, 4, y, hexc("e8e2d8"))
        put(img, tf, 5, y, hexc("e8e2d8"))
    for k in ("front", "back", "left", "right"):
        x0, y0, w, h = torso[k]
        for x in range(w):
            for y in range(8, 12):
                c = GOLD if (x + y) % 3 == 0 else hexc("cfc6b0")
                put(img, torso[k], x, y, jitter(c, 6))
    blood_splatter(img, tf, 3)
    blood_splatter(img, torso["left"], 1)

    # skirt 12x14x8 at (34,21): hem trailing on the floor, dirt and blood at the bottom
    skirt = Box(34, 21, 12, 14, 8).regions()
    for k in skirt:
        fill(img, skirt[k], silk)
    for k in ("front", "back", "left", "right"):
        x0, y0, w, h = skirt[k]
        for x in range(w):
            for y in range(h - 3, h):
                if R.random() < 0.4 + (y - (h - 3)) * 0.2:
                    put(img, skirt[k], x, y, jitter(DIRT, 10))
        # the front overlap line of the kimono
    sf = skirt["front"]
    for y in range(14):
        put(img, sf, 7, y, SILK_SHADOW)
    blood_splatter(img, sf, 4)
    blood_splatter(img, skirt["right"], 1)

    # obi bow 6x5x3 at (36,43)
    obi = Box(36, 43, 6, 5, 3).regions()
    for k in obi:
        fill(img, obi[k], lambda x, y, w, h: jitter(GOLD if (x * 2 + y) % 4 == 0 else hexc("d2c8b0"), 6))

    # sleeves 4x12x5 at (74,21) and (92,21): long furisode sleeves, red lining at the cuff
    for u in (74, 92):
        sl = Box(u, 21, 4, 12, 5).regions()
        for k in sl:
            fill(img, sl[k], silk)
        for k in ("front", "back", "left", "right"):
            x0, y0, w, h = sl[k]
            for x in range(w):
                put(img, sl[k], x, h - 1, RED)
                if R.random() < 0.3:
                    put(img, sl[k], x, h - 2, SILK_SHADOW)
        fill(img, sl["bottom"], lambda x, y, w, h: RED if (x in (0, w - 1) or y in (0, h - 1)) else hexc("1a1214"))

    # forearms 2x10x2 at (0,42),(8,42) and hands 3x5x2 at (16,42),(26,42)
    for u in (0, 8):
        fa = Box(u, 42, 2, 10, 2).regions()
        for k in fa:
            fill(img, fa[k], lambda x, y, w, h: jitter(SKIN if (y % 4) else SKIN_DARK, 5))
    for u in (16, 26):
        hd = Box(u, 42, 3, 5, 2).regions()
        for k in hd:
            def handpix(x, y, w, h):
                if y >= 2 and w >= 2 and x % 2 == 1:
                    return hexc("4a4540")
                if y == h - 1:
                    return hexc("1a0f0f")
                return jitter(SKIN, 5)
            fill(img, hd[k], handpix)
    return img, eyes


# ======================================================================= the doll
def paint_doll():
    img = Image.new("RGBA", (64, 32), CLEAR)
    porcelain = hexc("ece6dc")
    head = Box(0, 0, 6, 6, 6).regions()
    for k in head:
        fill(img, head[k], lambda x, y, w, h: jitter(porcelain, 4))
    f = head["front"]
    put(img, f, 1, 2, hexc("0a0808"))
    put(img, f, 4, 2, hexc("0a0808"))
    put(img, f, 1, 3, hexc("2a2626"))
    put(img, f, 4, 3, hexc("2a2626"))
    put(img, f, 2, 4, hexc("9a1c1c"))
    put(img, f, 3, 4, hexc("b02222"))
    put(img, f, 0, 4, hexc("e6c4c0"))
    put(img, f, 5, 4, hexc("e6c4c0"))
    # a hairline crack
    for y, x in ((0, 4), (1, 4), (2, 5), (3, 5)):
        put(img, f, x, y, hexc("8a8478"))
    hair_box = Box(24, 0, 7, 6, 7).regions()
    gloss = lambda x, y, w, h: jitter(hexc("0d0c10") if (x + y) % 5 else hexc("2c2a34"), 3)
    for k in ("top", "left", "right", "back"):
        fill(img, hair_box[k], gloss)
    fill(img, hair_box["front"], lambda x, y, w, h: gloss(x, y, w, h) if (y < 2 or x == 0 or x == w - 1) else CLEAR)
    fill(img, hair_box["bottom"], lambda x, y, w, h: gloss(x, y, w, h) if (x == 0 or x == w - 1 or y == 0 or y == h - 1) else CLEAR)
    # body: kimono with the ichimatsu checker pattern and an obi
    body = Box(0, 13, 6, 8, 4).regions()
    for k in body:
        def kimono(x, y, w, h):
            if 3 <= y <= 4:
                return jitter(hexc("c8a24a") if x % 2 else hexc("8e6e2a"), 5)
            return jitter(hexc("8e1616") if (x // 1 + y // 1) % 2 else hexc("4a0a0a"), 5)
        fill(img, body[k], kimono)
    bf = body["front"]
    for y in range(0, 3):
        put(img, bf, 2, y, hexc("e8e0d0"))
        put(img, bf, 3, y, hexc("e8e0d0"))
    for u in (20, 28):
        arm = Box(u, 13, 2, 5, 2).regions()
        for k in arm:
            fill(img, arm[k], lambda x, y, w, h: jitter(porcelain if y >= 4 else hexc("8e1616"), 4))
    return img


# ======================================================================= the shadow
def paint_shadow():
    img = Image.new("RGBA", (64, 64), CLEAR)
    eyes = Image.new("RGBA", (64, 64), CLEAR)
    regions = [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]
    for (x0, y0, w, h) in regions:
        for y in range(h):
            for x in range(w):
                v = R.randint(0, 10)
                img.putpixel((x0 + x, y0 + y), (v, v, v + 2, 238 if R.random() > 0.04 else 200))
    # face: two pale eyes
    for ex in (10, 13):
        img.putpixel((ex, 12), (200, 200, 210, 255))
        eyes.putpixel((ex, 12), (230, 230, 255, 255))
        eyes.putpixel((ex, 13), (90, 90, 120, 255))
    return img, eyes


def main():
    os.makedirs(OUT, exist_ok=True)
    bride, bride_eyes = paint_bride()
    bride.save(os.path.join(OUT, "hanayome.png"))
    bride_eyes.save(os.path.join(OUT, "hanayome_eyes.png"))
    paint_doll().save(os.path.join(OUT, "ichimatsu.png"))
    shadow, shadow_eyes = paint_shadow()
    shadow.save(os.path.join(OUT, "kageboshi.png"))
    shadow_eyes.save(os.path.join(OUT, "kageboshi_eyes.png"))
    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (128 * 6, 64 * 6 + 32 * 6 + 64 * 6), (40, 40, 48, 255))
        sheet.alpha_composite(bride.resize((128 * 6, 64 * 6), Image.NEAREST), (0, 0))
        sheet.alpha_composite(paint_doll().resize((64 * 6, 32 * 6), Image.NEAREST), (0, 64 * 6))
        sheet.alpha_composite(shadow.resize((64 * 6, 64 * 6), Image.NEAREST), (0, 64 * 6 + 32 * 6))
        sheet.save(os.path.join(PREVIEW, "entities.png"))
    print("entity textures written to", OUT)


if __name__ == "__main__":
    main()
