"""Textures added for the story campaign: new blocks, clock items, keepsakes, cast skins and Time Eater variants.

Run from the repository root after gen_textures.py:  python3 tools/gen_textures_story.py
"""
import colorsys
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures as gt  # noqa: E402
from gen_textures import (BRASS, BRASS_DARK, BRASS_DEEP, BRASS_LIGHT, CREAM, INK, RUBY, STEEL, STEEL_DARK, WOOD, WOOD_DARK,  # noqa: E402
                          bevel, disc, gear, hexc, item_canvas, line, mix, new, noise_fill, out, outline, px, rect, rgb, ring,
                          rivet, shaded_disc)

GOLD = hexc(0xF0C860)
NOTE_COLORS = [hexc(0xE04848), hexc(0xF0C040), hexc(0x48C060), hexc(0x4890E8)]


# ===================================================================== blocks

def tuning_bells():
    rnd = random.Random(301)
    for note, c in enumerate(NOTE_COLORS):
        for lit in (False, True):
            img = new(16, 16)
            for y in range(16):
                for x in range(16):
                    shade = 1.25 - abs(x - 7.5) / 12.0 - y * 0.012
                    px(img, x, y, rgb(BRASS_LIGHT if lit else BRASS, shade * (1.15 if lit else 1.0) + rnd.uniform(-0.04, 0.04)))
            for x in range(16):
                px(img, x, 10, rgb(c, 1.5 if lit else 1.0))
                px(img, x, 11, rgb(c, 1.25 if lit else 0.8))
            for x in range(2, 15, 4):
                px(img, x, 3, rgb(BRASS_DARK))
            disc(img, 8, 6, 1.6, rgb(c, 1.6 if lit else 1.1))
            px(img, 7, 5, (255, 255, 255, 255) if lit else rgb(c, 1.6))
            for x in range(16):
                px(img, x, 15, rgb(BRASS_DEEP))
            img.save(out("block/tuning_bell_%d%s.png" % (note, "_lit" if lit else "")))


def dust_pile():
    rnd = random.Random(302)
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            base = (188, 182, 172) if v < 0.55 else (150, 145, 138) if v < 0.85 else (214, 210, 202)
            px(img, x, y, rgb(base, rnd.uniform(0.9, 1.08)))
    for _ in range(10):
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        disc(img, cx, cy, 1.2, (226, 222, 214, 255))
    for _ in range(7):
        px(img, rnd.randrange(16), rnd.randrange(16), (92, 84, 76, 255))
    for i in range(16):
        px(img, i, (i * 7) % 16, (236, 236, 240, 200))
    img.save(out("block/dust_pile.png"))


def memory_cog():
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if gt.gear_mask(7.5, 7.5, 5.0, 7.0, 10, x, y):
                t = d / 7.0
                px(img, x, y, rgb(mix(hexc(0xC8F8FF), hexc(0x40B0D8), t)[:3]))
            if 5.6 < d < 7.4 and gt.gear_mask(7.5, 7.5, 5.0, 7.0, 10, x, y):
                px(img, x, y, rgb(GOLD, 1.1 - (x + y) * 0.01))
    disc(img, 7.5, 7.5, 1.8, (255, 255, 255, 255))
    for (x, y) in [(5, 4), (10, 5), (4, 10)]:
        px(img, x, y, (255, 255, 255, 255))
    img.save(out("block/memory_cog.png"))


def tower_gate():
    rnd = random.Random(303)
    for half in ("lower", "upper"):
        img = new(16, 16)
        noise_fill(img, 0, 0, 16, 16, hexc(0x4A2C16), 0.08, rnd, streak=True)
        for x in (0, 5, 10, 15):
            for y in range(16):
                px(img, x, y, rgb(hexc(0x2A180A)))
        iron = hexc(0x3A3A40)
        bands = (2, 12) if half == "lower" else (5,)
        for by in bands:
            for x in range(16):
                px(img, x, by, rgb(iron, 1.2))
                px(img, x, by + 1, rgb(iron, 0.9))
            for x in range(1, 16, 3):
                px(img, x, by, rgb(STEEL, 1.2))
        if half == "lower":
            disc(img, 12, 7, 1.3, rgb(GOLD))
            px(img, 12, 8, rgb(BRASS_DEEP))
        else:
            # Brass clock emblem in the upper half.
            disc(img, 7.5, 9.5, 4.2, rgb(GOLD))
            disc(img, 7.5, 9.5, 3.2, rgb(CREAM))
            line(img, 7.5, 9.5, 7.5, 7.0, rgb(INK))
            line(img, 7.5, 9.5, 9.5, 10.5, rgb(INK))
            for x in range(16):
                px(img, x, 0, rgb(hexc(0x6A6A70)))
        img.save(out("block/tower_gate_%s.png" % half))


# ===================================================================== items

def clock_items():
    # music box
    img = item_canvas()
    rect(img, 2, 7, 12, 7, rgb(hexc(0x8A4A26)))
    bevel(img, 2, 7, 12, 7, 1.3, 0.6)
    for y in range(2, 7):
        for x in range(3 + (6 - y) // 2, 13 - (6 - y) // 2):
            px(img, x, y, rgb(hexc(0x9AB4C8), 1.0 + (x - 8) * 0.02))
    rect(img, 2, 6, 12, 1, rgb(hexc(0x5A2E18)))
    disc(img, 8, 10.5, 2.0, rgb(CREAM))
    px(img, 8, 10, rgb(INK))
    px(img, 9, 10, rgb(INK))
    px(img, 8, 4, (244, 184, 200, 255))
    px(img, 8, 3, (244, 216, 192, 255))
    px(img, 7, 5, (244, 184, 200, 255))
    px(img, 9, 5, (244, 184, 200, 255))
    rect(img, 14, 9, 1, 2, rgb(GOLD))
    img.save(out("item/clock_music_box.png"))
    # grandfather
    img = item_canvas()
    rect(img, 5, 1, 6, 6, rgb(WOOD, 1.2))
    rect(img, 6, 7, 4, 8, rgb(WOOD))
    rect(img, 5, 14, 6, 2, rgb(WOOD_DARK))
    disc(img, 8, 4, 2.2, rgb(CREAM))
    px(img, 8, 3, rgb(INK))
    px(img, 9, 4, rgb(INK))
    line(img, 8, 8, 8, 12, rgb(GOLD))
    disc(img, 8, 12.5, 1.0, rgb(GOLD, 1.1))
    px(img, 6, 0, rgb(GOLD))
    px(img, 10, 0, rgb(GOLD))
    img.save(out("item/clock_grandfather.png"))
    # great clock
    img = item_canvas()
    rect(img, 1, 1, 14, 14, rgb(hexc(0x6A6466)))
    bevel(img, 1, 1, 14, 14, 1.3, 0.6)
    shaded_disc(img, 8, 8, 6.0, GOLD)
    disc(img, 8, 8, 4.8, rgb(hexc(0xF0E6CE)))
    for a in range(12):
        ang = a / 12 * math.tau
        px(img, int(8 + math.cos(ang) * 4.0), int(8 + math.sin(ang) * 4.0), rgb(INK))
    line(img, 8, 8, 8, 4.5, rgb(INK))
    line(img, 8, 8, 10.5, 9.5, rgb(INK))
    px(img, 8, 8, rgb(GOLD))
    img.save(out("item/clock_great_clock.png"))
    for name in ("music_box", "grandfather", "great_clock"):
        p = out("item/clock_" + name + ".png")
        outline(Image.open(p)).save(p)


def keepsakes():
    """Sixteen keepsake icons (index = StoryQuests.K_*)."""
    icons = []

    def whistle():
        img = item_canvas()
        rect(img, 2, 7, 9, 4, rgb(BRASS, 1.1))
        bevel(img, 2, 7, 9, 4, 1.3, 0.6)
        disc(img, 12, 9, 2.6, rgb(BRASS, 1.2))
        disc(img, 12, 9, 1.3, rgb(BRASS_DEEP))
        rect(img, 5, 6, 2, 1, rgb(BRASS_DARK))
        line(img, 1, 9, 1, 9, rgb(BRASS_DARK))
        return img

    def flower(petal, center):
        img = item_canvas()
        line(img, 8, 9, 8, 15, (70, 140, 60, 255))
        line(img, 8, 12, 11, 10, (70, 140, 60, 255))
        for k in range(5):
            a = k / 5 * math.tau - math.pi / 2
            disc(img, 8 + math.cos(a) * 2.8, 6 + math.sin(a) * 2.8, 1.9, rgb(petal))
        disc(img, 8, 6, 1.4, rgb(center))
        return img

    def star():
        img = item_canvas()
        rect(img, 2, 2, 12, 12, rgb(hexc(0x1B2A55)))
        bevel(img, 2, 2, 12, 12, 1.4, 0.6)
        for (x, y) in [(5, 5), (10, 4), (7, 9), (11, 11), (4, 11)]:
            px(img, x, y, (255, 244, 192, 255))
        line(img, 5, 5, 7, 9, (180, 190, 230, 160))
        line(img, 7, 9, 11, 11, (180, 190, 230, 160))
        disc(img, 10, 4, 1.2, (255, 224, 100, 255))
        return img

    def key(color, big):
        img = item_canvas()
        r = 3.4 if big else 2.6
        ring(img, 4.5, 4.5, r - 1.3, r, rgb(color))
        for i in range(8 if big else 7):
            px(img, 6 + i, 6 + i, rgb(color, 1.1))
            px(img, 7 + i, 6 + i, rgb(color, 0.75))
        rect(img, 11, 12, 2, 2, rgb(color, 0.9))
        if big:
            rect(img, 13, 10, 2, 2, rgb(color, 0.9))
            px(img, 4, 4, rgb(RUBY))
        return img

    def paper(burnt):
        img = item_canvas()
        rect(img, 3, 2, 10, 12, (240, 230, 200, 255))
        for y in range(4, 13, 2):
            line(img, 4, y, 11 - (y % 3), y, (110, 90, 70, 255))
        if burnt:
            for (x, y) in [(3, 2), (4, 2), (12, 2), (12, 3), (3, 13), (12, 13), (11, 13), (3, 12)]:
                px(img, x, y, (60, 40, 30, 255))
            px(img, 13, 8, (0, 0, 0, 0))
        else:
            gear(img, 10, 11, 1.4, 2.0, 6, hexc(0x3A60C0))
        return img

    def feather():
        img = item_canvas()
        for i in range(11):
            x, y = 3 + i, 13 - i
            px(img, x, y, (200, 200, 210, 255))
            for k in range(1, 3 + (i < 8)):
                px(img, x - k, y - k + 1 if i > 1 else y, (30, 26, 34, 255))
                px(img, x + k - 1, y + k, (50, 30, 50, 255))
        return img

    def bell():
        img = item_canvas()
        for y in range(3, 12):
            half = 1.5 + (y - 3) * 0.55
            for x in range(16):
                if abs(x + 0.5 - 8) <= half:
                    px(img, x, y, rgb(BRASS, 1.3 - abs(x - 7.5) * 0.06))
        rect(img, 3, 12, 10, 1, rgb(BRASS_DARK))
        disc(img, 8, 13.5, 1.2, rgb(BRASS_DEEP))
        rect(img, 7, 1, 2, 2, rgb(BRASS_DARK))
        return img

    def gear_icon(color):
        img = item_canvas()
        gear(img, 8, 8, 4.5, 6.6, 9, color, hole=1.6)
        px(img, 6, 5, (255, 255, 255, 220))
        return img

    def lens():
        img = item_canvas()
        disc(img, 8, 8, 5.5, (140, 220, 255, 210))
        ring(img, 8, 8, 4.6, 6.0, rgb(GOLD))
        px(img, 6, 5, (255, 255, 255, 255))
        px(img, 5, 6, (255, 255, 255, 255))
        line(img, 12, 12, 14, 14, rgb(BRASS_DARK))
        return img

    def music():
        img = item_canvas()
        for i in range(6):
            rect(img, 2 + i * 2, 4 + i, 1, 9 - i, rgb(STEEL, 1.25 - i * 0.05))
        rect(img, 2, 13, 12, 2, rgb(BRASS_DARK))
        disc(img, 12, 5, 1.4, rgb(INK))
        line(img, 13, 5, 13, 1, rgb(INK))
        return img

    def glove():
        img = item_canvas()
        rect(img, 4, 7, 8, 7, (240, 240, 245, 255))
        for i, h in enumerate((4, 5, 5, 4)):
            rect(img, 4 + i * 2, 7 - h, 2, h, (236, 236, 242, 255))
        rect(img, 11, 8, 3, 2, (236, 236, 242, 255))
        rect(img, 4, 13, 8, 2, rgb(hexc(0x8A2A3A)))
        return img

    def pendulum():
        img = item_canvas()
        line(img, 8, 1, 8, 9, rgb(BRASS_DARK))
        shaded_disc(img, 8, 11, 3.6, GOLD)
        disc(img, 8, 11, 1.4, rgb(BRASS_LIGHT))
        return img

    def crown():
        img = item_canvas()
        for x in range(2, 14):
            for y in range(8, 13):
                px(img, x, y, rgb(hexc(0x7A3AA0), 1.1 - (y - 8) * 0.06))
        for cx in (3, 8, 12):
            for y in range(4, 8):
                for x in range(cx - (8 - y) // 2, cx + (8 - y) // 2 + 1):
                    px(img, x, y, rgb(hexc(0x7A3AA0), 1.2))
        for cx in (3, 8, 12):
            px(img, cx, 3, rgb(GOLD))
        rect(img, 2, 12, 12, 1, rgb(GOLD))
        px(img, 8, 10, (255, 120, 220, 255))
        line(img, 10, 8, 12, 12, (30, 10, 30, 255))
        return img

    def medal():
        img = item_canvas()
        line(img, 5, 0, 7, 6, (180, 40, 50, 255))
        line(img, 10, 0, 8, 6, (40, 70, 160, 255))
        shaded_disc(img, 8, 10, 4.6, GOLD)
        gear(img, 8, 10, 2.0, 3.0, 8, BRASS_DARK, hole=0.8)
        px(img, 6, 8, (255, 255, 230, 255))
        return img

    icons = [whistle(), flower((236, 140, 170), (250, 220, 90)), star(), key(STEEL, False), paper(False), feather(), bell(),
             gear_icon(BRASS), lens(), music(), glove(), pendulum(), flower((150, 220, 245), (255, 255, 255)), key(GOLD, True),
             crown(), medal()]
    for i, img in enumerate(icons):
        outline(img).save(out("item/keepsake_%d.png" % i))


# ===================================================================== entities

def cast_skins():
    tone = gt.SKIN_TONES
    # 6 Pip: bell-ringer boy, messy brown hair, navy vest with brass buttons
    gt.customer_skin(6, tone[3], hexc(0x8A5A2A), hexc(0x3A6A9A), hexc(0x2A3A6A), hexc(0xF0E8D8), hexc(0x6A4A2A),
                     hexc(0x2A1A10), hexc(0xD9AE55))
    # 7 Viktor: gaunt clockmaker in a long grey coat
    gt.customer_skin(7, tone[1], hexc(0x4A4A52), hexc(0x6A7A8A), hexc(0x6A6A72), hexc(0x2A2A30), hexc(0x3A3A40),
                     hexc(0x1A1A1E), hexc(0x9A9AA2), beard=hexc(0x58585E), cape=hexc(0x5A5A62))
    # 8 Elias: the old master, white beard, goggles, leather apron
    gt.customer_skin(8, tone[0], hexc(0xE8E8E8), hexc(0x3A6A4A), hexc(0x2E4A3A), hexc(0xE8E0D0), hexc(0x4A3A2A),
                     hexc(0x2A1A10), hexc(0xD9AE55), beard=hexc(0xF4F4F4), goggles=hexc(0xD9AE55), apron=hexc(0x6A4A2A))


def recolor(src, dst, body_hue, body_sat, body_val, trim, eye=None):
    img = Image.open(out(src)).convert("RGBA")
    w, h = img.size
    res = new(w, h)
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if eye is not None:
                er, eg, eb = eye
                res.putpixel((x, y), (int(er * v), int(eg * v), int(eb * v), a))
                continue
            gold = s > 0.35 and 0.08 < hh < 0.18 and v > 0.45
            if gold:
                tr, tg, tb = trim
                res.putpixel((x, y), (int(min(255, tr * v * 1.1)), int(min(255, tg * v * 1.1)), int(min(255, tb * v * 1.1)), a))
            else:
                nr, ng, nb = colorsys.hsv_to_rgb(body_hue, min(1.0, s * 0.4 + body_sat), min(1.0, v * body_val))
                res.putpixel((x, y), (int(nr * 255), int(ng * 255), int(nb * 255), a))
    res.save(out(dst))


def time_eater_variants():
    # shadow: soot-black shell, red trim and eyes
    recolor("entity/time_eater.png", "entity/time_eater_shadow.png", 0.0, 0.05, 0.55, (210, 50, 40))
    recolor("entity/time_eater_eyes.png", "entity/time_eater_shadow_eyes.png", 0, 0, 0, (0, 0, 0), eye=(255, 50, 40))
    # shade: violet ghost
    recolor("entity/time_eater.png", "entity/time_eater_shade.png", 0.76, 0.35, 1.05, (200, 150, 255))
    recolor("entity/time_eater_eyes.png", "entity/time_eater_shade_eyes.png", 0, 0, 0, (0, 0, 0), eye=(200, 120, 255))
    # queen: royal purple with bright gold
    recolor("entity/time_eater.png", "entity/time_eater_queen.png", 0.80, 0.55, 1.25, (255, 214, 90))
    recolor("entity/time_eater_eyes.png", "entity/time_eater_queen_eyes.png", 0, 0, 0, (0, 0, 0), eye=(255, 90, 220))


if __name__ == "__main__":
    tuning_bells()
    dust_pile()
    memory_cog()
    tower_gate()
    clock_items()
    keepsakes()
    cast_skins()
    time_eater_variants()
    print("story textures written")
