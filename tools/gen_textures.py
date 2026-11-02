"""Generates every texture of Clockwork Diver (pixel art drawn procedurally with PIL).

Run from the repository root:  python3 tools/gen_textures.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "clockworkdiver", "textures")


def out(path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(c, f=1.0, a=255):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), a)


def mix(c0, c1, t):
    return tuple(clamp(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


BRASS = hexc(0xD9AE55)
BRASS_LIGHT = hexc(0xF6D98A)
BRASS_DARK = hexc(0x8C6424)
BRASS_DEEP = hexc(0x5A3C16)
COPPER = hexc(0xC77A45)
COPPER_DARK = hexc(0x7A3E1E)
STEEL = hexc(0xB4BCC6)
STEEL_DARK = hexc(0x5E6670)
IRON = hexc(0x4A474C)
RUST = hexc(0x9A4A22)
RUST_DARK = hexc(0x5A2410)
RUST_LIGHT = hexc(0xD27A3A)
RUBY = hexc(0xE0203A)
WOOD = hexc(0x6B3F1F)
WOOD_DARK = hexc(0x3E2410)
CREAM = hexc(0xF3E8CF)
INK = hexc(0x231A12)


def new(w, h, color=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), color)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


def rect(img, x0, y0, w, h, c):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(img, x, y, c)


def noise_fill(img, x0, y0, w, h, base, var, rnd, streak=False):
    for y in range(y0, y0 + h):
        row = rnd.uniform(-var, var) if streak else 0
        for x in range(x0, x0 + w):
            f = 1.0 + rnd.uniform(-var, var) * (0.4 if streak else 1.0) + row
            px(img, x, y, rgb(base, f))


def bevel(img, x0, y0, w, h, light=1.25, dark=0.7):
    for x in range(x0, x0 + w):
        c = img.getpixel((x, y0))
        px(img, x, y0, rgb(c, light))
        c = img.getpixel((x, y0 + h - 1))
        px(img, x, y0 + h - 1, rgb(c, dark))
    for y in range(y0, y0 + h):
        c = img.getpixel((x0, y))
        px(img, x0, y, rgb(c, light))
        c = img.getpixel((x0 + w - 1, y))
        px(img, x0 + w - 1, y, rgb(c, dark))


def rivet(img, x, y, base=BRASS):
    px(img, x, y, rgb(base, 1.45))
    px(img, x + 1, y, rgb(base, 1.0))
    px(img, x, y + 1, rgb(base, 0.9))
    px(img, x + 1, y + 1, rgb(base, 0.55))


def disc(img, cx, cy, r, c, aa=True):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                px(img, x, y, c)


def ring(img, cx, cy, r0, r1, c):
    for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
        for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                px(img, x, y, c)


def shaded_disc(img, cx, cy, r, base, light=1.4, dark=0.6):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                t = (-(dx + dy) / (2 * r) + 0.5)
                px(img, x, y, rgb(base, dark + (light - dark) * t))


def gear_mask(cx, cy, r_root, r_tip, teeth, x, y, rot=0.0):
    dx, dy = x + 0.5 - cx, y + 0.5 - cy
    d = math.hypot(dx, dy)
    a = (math.atan2(dy, dx) - rot) % (2 * math.pi)
    seg = (a / (2 * math.pi) * teeth) % 1.0
    tooth = 0.25 < seg < 0.75
    return d <= (r_tip if tooth else r_root)


def gear(img, cx, cy, r_root, r_tip, teeth, base, hole=0.0, rot=0.0):
    for y in range(int(cy - r_tip - 1), int(cy + r_tip + 2)):
        for x in range(int(cx - r_tip - 1), int(cx + r_tip + 2)):
            if gear_mask(cx, cy, r_root, r_tip, teeth, x, y, rot):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if math.hypot(dx, dy) < hole:
                    continue
                t = (-(dx + dy) / (2 * r_tip) + 0.5)
                px(img, x, y, rgb(base, 0.65 + 0.75 * t))


def line(img, x0, y0, x1, y1, c):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / max(1, n)
        px(img, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)


# ===================================================================== blocks

def brass_block():
    rnd = random.Random(1)
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            base = mix(BRASS_LIGHT, BRASS_DARK, t * 0.8)
            px(img, x, y, rgb(base, 1.0 + rnd.uniform(-0.04, 0.04)))
    for y in range(1, 15, 3):
        for x in range(1, 15):
            if rnd.random() < 0.5:
                px(img, x, y, rgb(img.getpixel((x, y)), 1.08))
    bevel(img, 0, 0, 16, 16, 1.3, 0.6)
    for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        rivet(img, x, y)
    img.save(out("block/brass_block.png"))


def brass_plate():
    rnd = random.Random(2)
    img = new(16, 16)
    noise_fill(img, 0, 0, 16, 16, mix(BRASS, BRASS_DARK, 0.45), 0.05, rnd, streak=True)
    for x in range(16):
        px(img, x, 7, rgb(BRASS_DEEP, 0.9))
        px(img, x, 8, rgb(BRASS, 1.15))
    bevel(img, 0, 0, 16, 16, 1.25, 0.55)
    for x in range(1, 15, 4):
        rivet(img, x, 1, BRASS)
        rivet(img, x, 13, BRASS)
    img.save(out("block/brass_plate.png"))


def brass_tiles():
    rnd = random.Random(3)
    img = new(16, 16)
    for ty in range(2):
        for tx in range(2):
            x0, y0 = tx * 8, ty * 8
            base = BRASS if (tx + ty) % 2 == 0 else mix(BRASS, BRASS_DARK, 0.35)
            noise_fill(img, x0, y0, 8, 8, base, 0.04, rnd)
            ring(img, x0 + 4, y0 + 4, 1.6, 2.6, rgb(base, 0.72))
            px(img, x0 + 4, y0 + 4, rgb(base, 1.3))
            bevel(img, x0, y0, 8, 8, 1.25, 0.6)
    img.save(out("block/brass_tiles.png"))


def rusty_plate():
    rnd = random.Random(4)
    img = new(16, 16)
    noise_fill(img, 0, 0, 16, 16, mix(BRASS_DARK, RUST, 0.6), 0.08, rnd)
    for _ in range(9):
        cx, cy, r = rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(1.5, 3.5)
        for y in range(16):
            for x in range(16):
                if math.hypot(x - cx, y - cy) < r * rnd.uniform(0.7, 1.1):
                    px(img, x, y, rgb(mix(RUST, RUST_LIGHT, rnd.random() * 0.6), rnd.uniform(0.8, 1.1)))
    for _ in range(14):
        px(img, rnd.randrange(16), rnd.randrange(16), rgb(RUST_DARK))
    bevel(img, 0, 0, 16, 16, 1.15, 0.6)
    for (x, y) in [(2, 2), (12, 12)]:
        rivet(img, x, y, mix(BRASS_DARK, RUST, 0.5)[:3])
    img.save(out("block/rusty_plate.png"))


def copper_pipe():
    rnd = random.Random(5)
    side = new(16, 16)
    for y in range(16):
        for x in range(16):
            t = abs(x - 7.5) / 8.0
            f = 1.35 - t * 0.9
            px(side, x, y, rgb(COPPER, f + rnd.uniform(-0.03, 0.03)))
    for y in (0, 1, 14, 15):
        for x in range(16):
            px(side, x, y, rgb(COPPER_DARK, 1.2 - abs(x - 7.5) / 10.0))
    side.save(out("block/copper_pipe_side.png"))
    end = new(16, 16)
    noise_fill(end, 0, 0, 16, 16, COPPER, 0.05, rnd)
    ring(end, 8, 8, 0, 3.2, rgb((40, 20, 10)))
    ring(end, 8, 8, 3.2, 4.5, rgb(COPPER_DARK))
    ring(end, 8, 8, 6.4, 8.5, rgb(COPPER_DARK, 1.1))
    end.save(out("block/copper_pipe_end.png"))


def jewel_bearing():
    rnd = random.Random(6)
    img = new(16, 16)
    noise_fill(img, 0, 0, 16, 16, BRASS_DEEP, 0.08, rnd)
    ring(img, 8, 8, 5.0, 7.0, rgb(BRASS, 1.0))
    ring(img, 8, 8, 6.2, 7.0, rgb(BRASS_LIGHT, 1.0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 5.0:
                t = d / 5.0
                px(img, x, y, rgb(mix((255, 140, 150), RUBY, t)[:3], 1.0 - 0.35 * t))
    px(img, 6, 5, (255, 255, 255, 255))
    px(img, 6, 6, (255, 220, 225, 255))
    px(img, 5, 6, (255, 220, 225, 255))
    bevel(img, 0, 0, 16, 16, 1.2, 0.5)
    img.save(out("block/jewel_bearing.png"))


def steam_vent():
    rnd = random.Random(7)
    top = new(16, 16)
    noise_fill(top, 0, 0, 16, 16, IRON, 0.06, rnd)
    for y in range(3, 13, 3):
        for x in range(2, 14):
            px(top, x, y, (18, 16, 20, 255))
            px(top, x, y + 1, rgb(IRON, 1.3))
    bevel(top, 0, 0, 16, 16, 1.4, 0.6)
    for (x, y) in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        rivet(top, x, y, STEEL)
    top.save(out("block/steam_vent_top.png"))
    side = new(16, 16)
    noise_fill(side, 0, 0, 16, 16, IRON, 0.06, rnd, streak=True)
    for x in range(16):
        px(side, x, 3, rgb(COPPER, 1.0))
        px(side, x, 4, rgb(COPPER_DARK, 1.0))
    bevel(side, 0, 0, 16, 16, 1.3, 0.6)
    for x in range(1, 15, 5):
        rivet(side, x, 11, STEEL)
    side.save(out("block/steam_vent_side.png"))


def rust_cluster():
    rnd = random.Random(8)
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            c = RUST if v < 0.3 else RUST_LIGHT if v < 0.8 else (238, 140, 60)
            px(img, x, y, rgb(c, rnd.uniform(0.85, 1.2)))
    for _ in range(18):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px(img, x, y, rgb((255, 196, 96)))
    for _ in range(6):
        cx, cy = rnd.randrange(16), rnd.randrange(16)
        disc(img, cx, cy, 1.4, rgb(RUST_DARK, 0.8))
    img.save(out("block/rust_cluster.png"))


def tooth_socket():
    rnd = random.Random(9)
    for filled in (False, True):
        top = new(16, 16)
        noise_fill(top, 0, 0, 16, 16, BRASS, 0.05, rnd)
        bevel(top, 0, 0, 16, 16, 1.3, 0.6)
        # trapezoid slot
        for y in range(4, 13):
            half = 2 + (y - 4) * 0.35
            for x in range(16):
                if abs(x + 0.5 - 8) <= half:
                    if filled:
                        px(top, x, y, rgb(STEEL, 1.3 - (y - 4) * 0.06))
                    else:
                        edge = abs(x + 0.5 - 8) > half - 1 or y in (4, 12)
                        px(top, x, y, (70, 230, 255, 255) if edge else (12, 10, 14, 255))
        top.save(out("block/tooth_socket_top" + ("_filled" if filled else "") + ".png"))
    side = new(16, 16)
    noise_fill(side, 0, 0, 16, 16, mix(BRASS, BRASS_DARK, 0.4), 0.05, rnd, streak=True)
    bevel(side, 0, 0, 16, 16, 1.25, 0.55)
    for x in range(2, 15, 4):
        rivet(side, x, 2)
    side.save(out("block/tooth_socket_side.png"))


def screw_head():
    rnd = random.Random(10)
    top = new(16, 16)
    blued = hexc(0x3550A8)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            px(top, x, y, rgb(mix(hexc(0x6A86D8), blued, t)[:3], 1.0 + rnd.uniform(-0.05, 0.05)))
    for y in range(6, 10):
        for x in range(16):
            px(top, x, y, (14, 14, 26, 255) if y in (7, 8) else rgb(blued, 0.55))
    bevel(top, 0, 0, 16, 16, 1.35, 0.55)
    top.save(out("block/screw_head_top.png"))
    side = new(16, 16)
    for y in range(16):
        for x in range(16):
            stripe = ((x + y * 2) % 6) < 3
            px(side, x, y, rgb(STEEL, 1.15 if stripe else 0.7))
    side.save(out("block/screw_head_side.png"))


def winding_crank():
    rnd = random.Random(11)
    top = new(16, 16)
    noise_fill(top, 0, 0, 16, 16, BRASS, 0.05, rnd)
    gear(top, 8, 8, 6.0, 7.6, 12, BRASS)
    rect(top, 6, 6, 4, 4, rgb(STEEL, 1.1))
    rect(top, 7, 7, 2, 2, rgb(STEEL_DARK))
    top.save(out("block/winding_crank_top.png"))
    side = new(16, 16)
    noise_fill(side, 0, 0, 16, 16, STEEL_DARK, 0.06, rnd, streak=True)
    for x in range(16):
        c = rgb(BRASS, 1.2 if x % 3 == 0 else 0.9)
        for y in range(0, 4):
            px(side, x, y, c)
    bevel(side, 0, 0, 16, 16, 1.3, 0.6)
    side.save(out("block/winding_crank_side.png"))


def repair_bench():
    rnd = random.Random(12)
    top = new(16, 16)
    noise_fill(top, 0, 0, 16, 16, WOOD, 0.08, rnd, streak=True)
    rect(top, 2, 2, 12, 12, rgb(hexc(0x1E4A32)))
    for _ in range(12):
        px(top, rnd.randrange(3, 13), rnd.randrange(3, 13), rgb(hexc(0x2A5E42)))
    gear(top, 6, 7, 2.0, 2.8, 8, BRASS)
    gear(top, 10, 10, 1.4, 2.0, 6, STEEL)
    px(top, 11, 5, rgb(RUBY))
    bevel(top, 0, 0, 16, 16, 1.2, 0.6)
    top.save(out("block/repair_bench_top.png"))
    side = new(16, 16)
    noise_fill(side, 0, 0, 16, 16, WOOD_DARK, 0.08, rnd, streak=True)
    rect(side, 0, 0, 16, 4, rgb(WOOD, 1.1))
    for x in range(16):
        px(side, x, 4, rgb(BRASS))
    rect(side, 2, 6, 5, 4, rgb(WOOD, 0.9))
    rect(side, 9, 6, 5, 4, rgb(WOOD, 0.9))
    px(side, 4, 8, rgb(BRASS_LIGHT))
    px(side, 11, 8, rgb(BRASS_LIGHT))
    side.save(out("block/repair_bench_side.png"))
    front = side.copy()
    rect(front, 5, 11, 6, 4, rgb(STEEL_DARK))
    rect(front, 6, 12, 4, 2, rgb(STEEL))
    front.save(out("block/repair_bench_front.png"))


def mechanism():
    rnd = random.Random(13)
    img = new(16, 16)
    noise_fill(img, 0, 0, 16, 16, BRASS, 0.05, rnd)
    gear(img, 8, 8, 5.5, 7.5, 10, BRASS, hole=1.5)
    disc(img, 8, 8, 1.4, rgb(RUBY))
    img.save(out("block/mechanism.png"))


# ===================================================================== items

def item_canvas():
    return new(16, 16)


def outline(img, color=(74, 50, 26, 255)):
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        img.putpixel((x, y), color)
                        break
    return img


def clock_items():
    gold = hexc(0xF0C860)
    # pocket watch
    img = item_canvas()
    ring(img, 8, 2.5, 1.0, 2.2, rgb(gold))
    rect(img, 7, 3, 2, 2, rgb(gold, 0.8))
    shaded_disc(img, 8, 9.5, 6.0, gold)
    disc(img, 8, 9.5, 4.6, rgb(CREAM))
    for a in range(12):
        ang = a / 12 * math.tau
        px(img, int(8 + math.cos(ang) * 3.8), int(9.5 + math.sin(ang) * 3.8), rgb(INK, 1.5))
    line(img, 8, 9.5, 8, 6.5, rgb(INK))
    line(img, 8, 9.5, 10, 10.5, rgb(INK))
    line(img, 5, 7, 10, 12, (160, 40, 30, 200))
    img.save(out("item/clock_pocket_watch.png"))
    # cuckoo
    img = item_canvas()
    for y in range(1, 6):
        for x in range(8 - y - 1, 8 + y + 1):
            px(img, x, y + 1, rgb(WOOD_DARK, 1.1))
    rect(img, 3, 6, 10, 7, rgb(WOOD))
    disc(img, 8, 9, 2.6, rgb(CREAM))
    px(img, 8, 8, rgb(INK))
    px(img, 8, 9, rgb(INK))
    px(img, 9, 9, rgb(INK))
    disc(img, 8, 4.2, 1.0, (242, 201, 76, 255))
    line(img, 6, 13, 6, 15, (140, 140, 140, 255))
    line(img, 10, 13, 10, 15, (140, 140, 140, 255))
    px(img, 6, 15, rgb(WOOD_DARK))
    px(img, 10, 14, rgb(WOOD_DARK))
    img.save(out("item/clock_cuckoo.png"))
    # astronomical
    img = item_canvas()
    rect(img, 1, 1, 14, 14, rgb(gold, 0.8))
    bevel(img, 1, 1, 14, 14, 1.3, 0.6)
    disc(img, 8, 8, 6.0, rgb(hexc(0x1B2A55)))
    ring(img, 8, 8, 4.3, 5.2, rgb(gold))
    for (x, y) in [(6, 5), (10, 6), (7, 10), (9, 9), (5, 8)]:
        px(img, x, y, (255, 244, 192, 255))
    disc(img, 11, 5, 1.0, (255, 220, 90, 255))
    disc(img, 8, 8, 1.2, rgb(hexc(0x3A8ADA)))
    img.save(out("item/clock_astronomical.png"))
    # birdcage
    img = item_canvas()
    for i in range(-3, 4):
        for y in range(3, 13):
            t = (13 - y) / 10.0
            x = 8 + i * 1.7 * math.sqrt(max(0.0, 1 - t * t))
            px(img, int(round(x)), y, rgb(gold))
    rect(img, 3, 13, 10, 2, rgb(BRASS_DARK, 1.2))
    ring(img, 8, 2, 0.5, 1.5, rgb(gold))
    disc(img, 8, 9, 1.8, rgb(hexc(0x2F8F8A)))
    disc(img, 9.5, 7.5, 1.1, rgb(hexc(0x2F8F8A)))
    px(img, 11, 7, (224, 160, 48, 255))
    img.save(out("item/clock_birdcage.png"))
    # steam tower
    img = item_canvas()
    rect(img, 5, 3, 6, 12, rgb(IRON, 1.1))
    bevel(img, 5, 3, 6, 12, 1.4, 0.6)
    rect(img, 7, 1, 2, 2, rgb(IRON, 0.8))
    disc(img, 8, 6.5, 2.2, rgb(CREAM))
    px(img, 8, 6, rgb(INK))
    px(img, 9, 6, rgb(INK))
    rect(img, 3, 5, 2, 9, rgb(COPPER))
    rect(img, 11, 5, 2, 9, rgb(COPPER))
    disc(img, 7, 11, 0.8, rgb(STEEL))
    disc(img, 9, 11, 0.8, rgb(STEEL))
    px(img, 10, 0, (230, 230, 230, 180))
    px(img, 11, 0, (230, 230, 230, 120))
    img.save(out("item/clock_steam_tower.png"))
    # orrery
    img = item_canvas()
    disc(img, 8, 8, 7.0, rgb(WOOD_DARK, 1.2))
    ring(img, 8, 8, 6.2, 7.0, rgb(gold))
    ring(img, 8, 8, 2.6, 3.1, rgb(gold, 0.7))
    ring(img, 8, 8, 4.6, 5.1, rgb(gold, 0.7))
    disc(img, 8, 8, 1.5, (255, 200, 60, 255))
    disc(img, 10.8, 7, 0.9, rgb(hexc(0x3A8ADA)))
    disc(img, 4, 10, 1.0, rgb(hexc(0xC84A2A)))
    img.save(out("item/clock_orrery.png"))
    for name in ["pocket_watch", "cuckoo", "astronomical", "birdcage", "steam_tower", "orrery"]:
        p = out("item/clock_" + name + ".png")
        outline(Image.open(p)).save(p)


def other_items():
    gold = hexc(0xF0C860)
    # spectacles
    img = item_canvas()
    for cx in (4.5, 11.5):
        disc(img, cx, 9, 3.4, (120, 220, 255, 200))
        ring(img, cx, 9, 2.6, 3.6, rgb(gold))
        px(img, int(cx) - 1, 7, (255, 255, 255, 255))
    line(img, 7, 8, 9, 8, rgb(gold))
    line(img, 1, 8, 0, 5, rgb(gold, 0.8))
    line(img, 15, 8, 15, 5, rgb(gold, 0.8))
    outline(img).save(out("item/chrono_spectacles.png"))
    # wrench
    img = item_canvas()
    for i in range(9):
        x, y = 2 + i, 13 - i
        px(img, x, y, rgb(BRASS, 1.1))
        px(img, x + 1, y, rgb(BRASS, 0.8))
        px(img, x, y - 1, rgb(BRASS, 1.3) if i % 3 else rgb(BRASS_DARK))
    disc(img, 12, 4, 3.2, rgb(STEEL, 1.3))
    ring(img, 12, 4, 2.4, 3.2, rgb(STEEL, 0.95))
    disc(img, 13, 3, 1.5, (0, 0, 0, 0))
    px(img, 14, 2, (0, 0, 0, 0))
    px(img, 11, 3, (255, 255, 255, 255))
    outline(img).save(out("item/tinker_wrench.png"))
    # key
    img = item_canvas()
    gear(img, 4.5, 4.5, 2.8, 3.8, 8, gold, hole=1.2)
    for i in range(8):
        px(img, 6 + i, 6 + i, rgb(gold, 1.1))
        px(img, 7 + i, 6 + i, rgb(gold, 0.75))
    rect(img, 11, 12, 2, 2, rgb(gold, 0.9))
    rect(img, 13, 10, 2, 2, rgb(gold, 0.9))
    px(img, 4, 4, rgb(RUBY))
    outline(img).save(out("item/workshop_key.png"))
    # gear tooth
    img = item_canvas()
    for y in range(3, 14):
        half = 2 + (y - 3) * 0.4
        for x in range(16):
            if abs(x + 0.5 - 8) <= half:
                px(img, x, y, rgb(STEEL, 1.35 - (y - 3) * 0.05 - (0.2 if x > 8 else 0)))
    px(img, 7, 4, (255, 255, 255, 255))
    outline(img).save(out("item/gear_tooth.png"))
    # brass coin
    img = item_canvas()
    shaded_disc(img, 8, 8, 6.5, gold)
    ring(img, 8, 8, 5.2, 5.8, rgb(BRASS_DARK, 1.1))
    gear(img, 8, 8, 2.4, 3.4, 8, BRASS_DARK, hole=1.0)
    px(img, 5, 4, (255, 255, 230, 255))
    outline(img).save(out("item/brass_coin.png"))
    # bolt
    img = item_canvas()
    for y in range(4, 12):
        for x in range(4, 12):
            if abs(x - 7.5) + abs(y - 7.5) * 0.6 < 4.4:
                px(img, x, y, rgb(BRASS, 1.2 - (x + y - 8) * 0.03))
    disc(img, 8, 8, 1.4, rgb(BRASS_DEEP))
    outline(img).save(out("item/bolt.png"))


# ===================================================================== entities

def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(img, u, v, w, h, d, base, rnd, var=0.08, top_f=1.15, side_f=0.92, bottom_f=0.7):
    faces = box_faces(u, v, w, h, d)
    for name, (x0, y0, fw, fh) in faces.items():
        f = top_f if name == "top" else bottom_f if name == "bottom" else (1.0 if name == "front" else side_f)
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                px(img, x, y, rgb(base, f * (1 + rnd.uniform(-var, var))))
    return faces


def mechanism_metal():
    rnd = random.Random(20)
    img = new(64, 64)
    for y in range(64):
        row = rnd.uniform(-0.05, 0.05)
        for x in range(64):
            v = 232 * (1 + row + rnd.uniform(-0.035, 0.035))
            px(img, x, y, (clamp(v), clamp(v), clamp(v), 255))
    for _ in range(40):
        y = rnd.randrange(64)
        x0 = rnd.randrange(64)
        for x in range(x0, min(64, x0 + rnd.randrange(6, 30))):
            c = img.getpixel((x, y))
            px(img, x, y, rgb(c[:3], 1.12))
    img.save(out("entity/mechanism_metal.png"))


def rust_mite():
    rnd = random.Random(21)
    img = new(64, 32)
    body = paint_box(img, 0, 0, 8, 4, 10, RUST, rnd, 0.12)
    tx, ty, tw, td = body["top"]
    for y in range(ty, ty + td):
        for x in range(tx, tx + tw):
            if (y - ty) % 3 == 0:
                px(img, x, y, rgb(RUST_DARK))
    plate = paint_box(img, 0, 14, 6, 2, 6, BRASS_DARK, rnd, 0.1)
    px0, py0, pw, pd = plate["top"]
    for (x, y) in [(px0, py0), (px0 + pw - 1, py0), (px0, py0 + pd - 1), (px0 + pw - 1, py0 + pd - 1)]:
        px(img, x, y, rgb(BRASS_LIGHT))
    rx, ry = px0 + 2, py0 + 2
    rect(img, rx, ry, 2, 2, rgb(RUBY))
    head = paint_box(img, 24, 14, 6, 3, 4, RUST_DARK, rnd, 0.1)
    fx, fy, fw, fh = head["front"]
    px(img, fx + 1, fy + 1, (255, 200, 60, 255))
    px(img, fx + fw - 2, fy + 1, (255, 200, 60, 255))
    paint_box(img, 44, 14, 1, 1, 3, STEEL_DARK, rnd, 0.1)
    paint_box(img, 36, 0, 7, 1, 1, RUST_DARK, rnd, 0.15)
    img.save(out("entity/rust_mite.png"))


def cog_gremlin():
    rnd = random.Random(22)
    img = new(64, 64)
    skin = hexc(0x6E8A3E)
    head = paint_box(img, 0, 0, 8, 7, 7, skin, rnd, 0.08)
    fx, fy, fw, fh = head["front"]
    for (x, y) in [(fx + 1, fy + 2), (fx + 2, fy + 2), (fx + 5, fy + 2), (fx + 6, fy + 2)]:
        px(img, x, y, (255, 230, 80, 255))
    px(img, fx + 2, fy + 3, (20, 20, 10, 255))
    px(img, fx + 5, fy + 3, (20, 20, 10, 255))
    for x in range(fx + 2, fx + 6):
        px(img, x, fy + 5, (40, 20, 20, 255))
    px(img, fx + 3, fy + 6, (240, 240, 230, 255))
    px(img, fx + 5, fy + 6, (240, 240, 230, 255))
    for x in range(fx, fx + fw):
        px(img, x, fy, rgb(BRASS_DARK, 1.2))
        px(img, x, fy + 1, rgb((110, 220, 240)) if x in (fx + 1, fx + 2, fx + 5, fx + 6) else rgb(BRASS_DARK))
    paint_box(img, 30, 0, 2, 2, 2, rgb(skin, 0.85)[:3], rnd, 0.05)
    paint_box(img, 40, 0, 4, 3, 1, rgb(skin, 1.1)[:3], rnd, 0.06)
    body = paint_box(img, 0, 16, 6, 7, 4, hexc(0x5A3A22), rnd, 0.1)
    bx, by, bw, bh = body["front"]
    for y in range(by, by + bh):
        px(img, bx + 2, y, rgb(BRASS))
    for x in range(bx, bx + bw):
        px(img, x, by + 4, rgb(WOOD_DARK))
    gx, gy = 24, 16
    for face_x in (gx, gx + 8):
        for y in range(8):
            for x in range(8):
                if gear_mask(4, 4, 2.6, 3.9, 8, x, y):
                    d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
                    if d < 1.0:
                        continue
                    px(img, face_x + x, gy + y, rgb(BRASS, 1.2 - (x + y) * 0.04))
    paint_box(img, 40, 16, 2, 7, 2, skin, rnd, 0.08)
    paint_box(img, 48, 16, 2, 6, 2, hexc(0x3A2A1A), rnd, 0.08)
    img.save(out("entity/cog_gremlin.png"))


def dust_moth():
    rnd = random.Random(23)
    img = new(64, 32)
    fuzz = hexc(0x9C9283)
    paint_box(img, 0, 0, 4, 3, 8, fuzz, rnd, 0.15)
    head = paint_box(img, 0, 11, 4, 4, 3, rgb(fuzz, 0.85)[:3], rnd, 0.12)
    fx, fy, fw, fh = head["front"]
    rect(img, fx, fy + 1, 1, 2, (220, 60, 40, 255))
    rect(img, fx + fw - 1, fy + 1, 1, 2, (220, 60, 40, 255))
    paint_box(img, 14, 11, 1, 4, 1, hexc(0x5A5148), rnd, 0.1)
    # wings (top at 32..42, bottom at 42..52, rows 0..8)
    for wx in (32, 42):
        for y in range(8):
            for x in range(10):
                cx, cy = 3.5, 4.0
                inside = ((x - 0.0) / 10.0) ** 2 * 1.2 + ((y - 4.0) / 4.2) ** 2 < 1.0 or x < 3
                if not inside:
                    continue
                c = mix(hexc(0xB8AE9C), hexc(0x6E6456), x / 10.0)
                if math.hypot(x - 6, y - 4) < 1.8:
                    c = (230, 210, 120, 255) if math.hypot(x - 6, y - 4) > 0.9 else (40, 30, 30, 255)
                if (x + y) % 5 == 0:
                    c = rgb(c[:3], 0.85)
                px(img, wx + x, y, c)
    img.save(out("entity/dust_moth.png"))


def time_eater():
    rnd = random.Random(24)
    img = new(128, 128)
    shell_c = hexc(0x2B1D3A)
    body = paint_box(img, 0, 0, 16, 10, 22, hexc(0x34303A), rnd, 0.08)
    for name in ("left", "right"):
        x0, y0, w, h = body[name]
        for x in range(x0, x0 + w, 4):
            for y in range(y0 + 2, y0 + h - 2, 3):
                rivet(img, x + 1, y, STEEL)
    shell = paint_box(img, 0, 32, 18, 3, 20, shell_c, rnd, 0.1)
    sx, sy, sw, sd = shell["top"]
    for y in range(sy, sy + sd):
        for x in range(sx, sx + sw):
            if x in (sx, sx + sw - 1) or y in (sy, sy + sd - 1):
                px(img, x, y, rgb(hexc(0xE0C060)))
            elif (x - sx) == sw // 2:
                px(img, x, y, rgb(hexc(0xA08030)))
    head = paint_box(img, 0, 56, 10, 8, 8, hexc(0x241A2E), rnd, 0.1)
    fx, fy, fw, fh = head["front"]
    for x in range(fx, fx + fw):
        px(img, x, fy + fh - 2, rgb(STEEL, 1.1) if x % 2 == 0 else (20, 10, 20, 255))
    paint_box(img, 36, 56, 2, 2, 7, STEEL, rnd, 0.1)
    paint_box(img, 56, 56, 6, 5, 10, hexc(0x2E2438), rnd, 0.1)
    paint_box(img, 80, 0, 3, 12, 3, hexc(0x3E3A42), rnd, 0.1)
    gear_faces = box_faces(80, 20, 1, 8, 8)
    for name in ("right", "left"):
        x0, y0, w, h = gear_faces[name]
        for y in range(h):
            for x in range(w):
                if gear_mask(w / 2, h / 2, 2.8, 3.9, 8, x, y):
                    px(img, x0 + x, y0 + y, rgb(BRASS, 1.15 - (x + y) * 0.03))
    for name in ("front", "back", "top", "bottom"):
        x0, y0, w, h = gear_faces[name]
        rect(img, x0, y0, w, h, rgb(BRASS_DARK))
    img.save(out("entity/time_eater.png"))
    eyes = new(128, 128)
    for (x, y) in [(fx + 2, fy + 2), (fx + 3, fy + 2), (fx + 6, fy + 2), (fx + 7, fy + 2),
                   (fx + 2, fy + 3), (fx + 7, fy + 3)]:
        px(eyes, x, y, (255, 230, 60, 255))
    for (x, y) in [(fx + 3, fy + 3), (fx + 6, fy + 3)]:
        px(eyes, x, y, (255, 120, 30, 255))
    eyes.save(out("entity/time_eater_eyes.png"))


# ------------------------------------------------------------------ customers

SKIN_TONES = [hexc(0xE8C3A0), hexc(0xF2D2B6), hexc(0xC8936A), hexc(0xF0CDB0), hexc(0xA8724E), hexc(0xE6BE9A)]


def skin_face(img, x0, y0, tone, hair, eyes, beard=None, glasses=None, mask=None, lips=None):
    rect(img, x0, y0, 8, 8, rgb(tone))
    rect(img, x0, y0, 8, 2, rgb(hair))
    px(img, x0, y0 + 2, rgb(hair))
    px(img, x0 + 7, y0 + 2, rgb(hair))
    for (x, y) in [(1, 4), (5, 4)]:
        px(img, x0 + x, y0 + y, (255, 255, 255, 255))
        px(img, x0 + x + 1, y0 + y, rgb(eyes))
    px(img, x0 + 3, y0 + 5, rgb(tone, 0.85))
    px(img, x0 + 4, y0 + 5, rgb(tone, 0.85))
    if lips:
        px(img, x0 + 3, y0 + 6, rgb(lips))
        px(img, x0 + 4, y0 + 6, rgb(lips))
    else:
        px(img, x0 + 3, y0 + 6, rgb(tone, 0.7))
        px(img, x0 + 4, y0 + 6, rgb(tone, 0.7))
    if beard:
        for x in range(1, 7):
            px(img, x0 + x, y0 + 7, rgb(beard))
        px(img, x0 + 2, y0 + 6, rgb(beard))
        px(img, x0 + 5, y0 + 6, rgb(beard))
        px(img, x0 + 1, y0 + 6, rgb(beard))
        px(img, x0 + 6, y0 + 6, rgb(beard))
    if glasses:
        for (x, y) in [(1, 3), (2, 3), (1, 5), (2, 5), (5, 3), (6, 3), (5, 5), (6, 5), (0, 4), (3, 4), (4, 4), (7, 4)]:
            px(img, x0 + x, y0 + y, rgb(glasses))
        px(img, x0 + 1, y0 + 4, (140, 220, 255, 255))
        px(img, x0 + 6, y0 + 4, (140, 220, 255, 255))
    if mask:
        for x in range(8):
            px(img, x0 + x, y0 + 4, rgb(mask))
            px(img, x0 + x, y0 + 3, rgb(mask, 0.85))
        px(img, x0 + 2, y0 + 4, (255, 255, 255, 255))
        px(img, x0 + 5, y0 + 4, (255, 255, 255, 255))


def customer_skin(idx, tone, hair, eyes, coat, shirt, trousers, shoes, accent, beard=None, glasses=None, mask=None, lips=None,
                  goggles=None, apron=None, cape=None, long_hair=False):
    rnd = random.Random(100 + idx)
    img = new(64, 64)
    # head
    faces = box_faces(0, 0, 8, 8, 8)
    for name, (x0, y0, w, h) in faces.items():
        rect(img, x0, y0, w, h, rgb(hair if name in ("top", "back") else tone))
    for name in ("right", "left"):
        x0, y0, w, h = faces[name]
        rect(img, x0, y0, w, 3, rgb(hair))
        if long_hair:
            rect(img, x0, y0, w, h, rgb(hair))
            rect(img, x0 + (0 if name == "left" else 6), y0 + 3, 2, 4, rgb(tone))
    bx, by, bw, bh = faces["back"]
    if long_hair:
        rect(img, bx, by, bw, bh, rgb(hair))
    fx, fy, _, _ = faces["front"]
    skin_face(img, fx, fy, tone, hair, eyes, beard, glasses, mask, lips)
    if long_hair:
        px(img, fx, fy + 3, rgb(hair))
        px(img, fx + 7, fy + 3, rgb(hair))
    # hat layer: goggles strap
    if goggles:
        hf = box_faces(32, 0, 8, 8, 8)
        for name in ("front", "right", "left", "back"):
            x0, y0, w, h = hf[name]
            for x in range(x0, x0 + w):
                px(img, x, y0 + 1, rgb(hexc(0x3A2410)))
        x0, y0, w, h = hf["front"]
        for cx in (x0 + 2, x0 + 5):
            for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)]:
                px(img, cx + dx, y0 + dy, rgb(goggles))
            px(img, cx, y0, (150, 230, 255, 255))
    # body
    bfaces = box_faces(16, 16, 8, 12, 4)
    for name, (x0, y0, w, h) in bfaces.items():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px(img, x, y, rgb(coat, (1.0 if name == "front" else 0.9) * (1 + rnd.uniform(-0.04, 0.04))))
    x0, y0, w, h = bfaces["front"]
    rect(img, x0 + 2, y0, 4, h - 3, rgb(shirt))
    rect(img, x0 + 3, y0, 2, 2, rgb(accent))
    for y in range(y0 + 3, y0 + 9, 2):
        px(img, x0 + 1, y, rgb(BRASS_LIGHT))
        px(img, x0 + 6, y, rgb(BRASS_LIGHT))
    for x in range(x0, x0 + w):
        px(img, x, y0 + 8, rgb(hexc(0x3A2410)))
    px(img, x0 + 3, y0 + 8, rgb(BRASS_LIGHT))
    if apron:
        rect(img, x0 + 1, y0 + 5, 6, 7, rgb(apron))
    # pocket watch chain
    line(img, x0 + 2, y0 + 6, x0 + 5, y0 + 7, rgb(BRASS_LIGHT))
    if cape:
        bx, by, bw, bh = bfaces["back"]
        rect(img, bx, by, bw, bh, rgb(cape))
    # arms
    for (u, v) in [(40, 16), (32, 48)]:
        afaces = box_faces(u, v, 4, 12, 4)
        for name, (ax, ay, aw, ah) in afaces.items():
            for y in range(ay, ay + ah):
                for x in range(ax, ax + aw):
                    c = coat
                    if name in ("right", "front", "left", "back") and y >= ay + ah - 2:
                        c = tone
                    elif name in ("right", "front", "left", "back") and y == ay + ah - 3:
                        c = shirt
                    if name == "bottom":
                        c = tone
                    px(img, x, y, rgb(c, (0.95 if name != "front" else 1.0) * (1 + rnd.uniform(-0.04, 0.04))))
    # legs
    for (u, v) in [(0, 16), (16, 48)]:
        lfaces = box_faces(u, v, 4, 12, 4)
        for name, (lx, ly, lw, lh) in lfaces.items():
            for y in range(ly, ly + lh):
                for x in range(lx, lx + lw):
                    c = trousers
                    if y >= ly + lh - 3 or name == "bottom":
                        c = shoes
                    px(img, x, y, rgb(c, (0.92 if name != "front" else 1.0) * (1 + rnd.uniform(-0.04, 0.04))))
    img.save(out("entity/customer/customer_%d.png" % idx))


def customers():
    # 0 Gregory: old gentleman, grey beard, monocle-ish glasses, tweed coat
    customer_skin(0, SKIN_TONES[0], hexc(0xB8B8B8), hexc(0x3A5A8A), hexc(0x5A4632), hexc(0xE8E0D0), hexc(0x3A3430),
                  hexc(0x1E1612), hexc(0x8A1E1E), beard=hexc(0xD0D0D0), glasses=hexc(0xD9AE55))
    # 1 Ada: inventor with goggles, auburn hair, teal coat
    customer_skin(1, SKIN_TONES[1], hexc(0x8A3A1E), hexc(0x2E7A4A), hexc(0x2E6A6A), hexc(0xF0E6D2), hexc(0x4A3422),
                  hexc(0x2A1A10), hexc(0xD9AE55), goggles=hexc(0xC07A3A), lips=hexc(0xB04040), long_hair=True)
    # 2 Bolt: engineer, sooty overalls, flat dark hair
    customer_skin(2, SKIN_TONES[2], hexc(0x2A1E16), hexc(0x4A3A2A), hexc(0x3A4A5A), hexc(0x8A5A3A), hexc(0x3A4A5A),
                  hexc(0x1A1410), hexc(0xC77A45), beard=hexc(0x2A1E16), apron=hexc(0x6A4A2A), goggles=hexc(0x8A8A8A))
    # 3 Lily: flower seller, blonde, green dress
    customer_skin(3, SKIN_TONES[3], hexc(0xE8C860), hexc(0x4A8A3A), hexc(0x4A7A3A), hexc(0xF4E8F0), hexc(0x4A7A3A),
                  hexc(0x5A3A22), hexc(0xE05A7A), lips=hexc(0xD06070), long_hair=True)
    # 4 Celes: astronomer, navy robe with stars, white hair
    customer_skin(4, SKIN_TONES[4], hexc(0xE8E8F0), hexc(0x6A4AA8), hexc(0x24305A), hexc(0xD8D0F0), hexc(0x24305A),
                  hexc(0x1A1A2A), hexc(0xF0C860), glasses=hexc(0xC0C0D0), cape=hexc(0x1E2850))
    # 5 Phantom: masked gentleman thief, black and wine
    customer_skin(5, SKIN_TONES[5], hexc(0x1A1A1E), hexc(0x8A2A3A), hexc(0x1E1A22), hexc(0xF0F0F0), hexc(0x1E1A22),
                  hexc(0x0E0C10), hexc(0x8A2A3A), mask=hexc(0x2A0E18), cape=hexc(0x6A1A2A))
    # top hat (white base, tinted in game) with a red band and a brass gear pin
    rnd = random.Random(150)
    hat = new(64, 32)
    paint_box(hat, 0, 0, 10, 1, 10, (230, 230, 235), rnd, 0.04)
    paint_box(hat, 0, 11, 7, 7, 7, (235, 235, 240), rnd, 0.05)
    band = box_faces(28, 11, 8, 2, 8)
    for name, (x0, y0, w, h) in band.items():
        rect(hat, x0, y0, w, h, (210, 60, 60, 255))
    paint_box(hat, 44, 0, 2, 2, 1, BRASS, rnd, 0.05)
    hat.save(out("entity/customer/top_hat.png"))


def spectacles_armor():
    img = new(64, 32)
    faces = box_faces(0, 0, 8, 8, 8)
    fx, fy, fw, fh = faces["front"]
    gold = hexc(0xF0C860)
    for cx in (fx + 2, fx + 5):
        for (dx, dy) in [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]:
            px(img, cx + dx, fy + 4 + dy, rgb(gold))
        px(img, cx, fy + 4, (140, 220, 255, 200))
    px(img, fx + 3, fy + 3, rgb(gold))
    px(img, fx + 4, fy + 3, rgb(gold))
    for name in ("right", "left"):
        x0, y0, w, h = faces[name]
        for x in range(x0, x0 + w):
            px(img, x, y0 + 3, rgb(gold, 0.85))
    img.save(out("models/armor/chrono_spectacles_layer_1.png"))


if __name__ == "__main__":
    brass_block()
    brass_plate()
    brass_tiles()
    rusty_plate()
    copper_pipe()
    jewel_bearing()
    steam_vent()
    rust_cluster()
    tooth_socket()
    screw_head()
    winding_crank()
    repair_bench()
    mechanism()
    clock_items()
    other_items()
    mechanism_metal()
    rust_mite()
    cog_gremlin()
    dust_moth()
    time_eater()
    customers()
    spectacles_armor()
    print("textures written to", os.path.normpath(ROOT))
