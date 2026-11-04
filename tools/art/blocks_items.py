"""Block, item, trap-part (block entity) and particle textures."""
import math
import random

import numpy as np

from common import (box_uv, canvas, fbm, fill, hexc, mix, px, save, shade, splat, textured)

STEEL = hexc('4a5058')
DARK_STEEL = hexc('2c3036')
RUST = hexc('8a4a26')
HAZ_Y = hexc('e8b820')
HAZ_K = hexc('1c1c1c')


def steel(a, x, y, w, h, seed, base=STEEL, amt=0.16, brushed=True):
    n = fbm(w, h, seed, base=2.0, octaves=2)
    r = random.Random(seed)
    for j in range(h):
        line = (r.random() - 0.5) * 0.08 if brushed else 0.0
        for i in range(w):
            k = 1.0 + (n[j, i] - 0.5) * 2 * amt + line
            px(a, x + i, y + j, shade(base, k))


def rivet(a, x, y):
    px(a, x, y, (170, 176, 184, 255))
    px(a, x + 1, y + 1, (24, 26, 30, 255))


def hazard(a, x, y, w, h, phase=0):
    for j in range(h):
        for i in range(w):
            px(a, x + i, y + j, HAZ_Y if ((i + j + phase) // 2) % 2 == 0 else HAZ_K)


def border(a, x, y, w, h, c):
    for i in range(w):
        px(a, x + i, y, c)
        px(a, x + i, y + h - 1, c)
    for j in range(h):
        px(a, x, y + j, c)
        px(a, x + w - 1, y + j, c)


# ------------------------------------------------------------------------------------------------ blocks

def blocks():
    # mixer casing
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 11)
    hazard(a, 0, 0, 16, 3)
    border(a, 0, 3, 16, 13, shade(DARK_STEEL, 0.9))
    for (x, y) in ((1, 4), (13, 4), (1, 13), (13, 13)):
        rivet(a, x, y)
    for i in range(1, 15):
        px(a, i, 9, shade(STEEL, 0.75))
    save(a, 'block/mixer_casing.png')

    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 12, base=hexc('565c64'), brushed=False)
    for j in range(16):
        for i in range(16):
            if (i + j * 2) % 6 == 0 and (i - j * 2) % 6 == 0:
                px(a, i, j, (150, 156, 164, 255))
                px(a, i + 1, j, (40, 42, 48, 255))
    border(a, 0, 0, 16, 16, DARK_STEEL)
    save(a, 'block/mixer_casing_top.png')

    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 13, base=hexc('3c4148'))
    border(a, 0, 0, 16, 16, DARK_STEEL)
    for j in (4, 6, 8, 10):
        for i in range(3, 13):
            px(a, i, j, (18, 18, 22, 255))
            px(a, i, j + 1, (90, 96, 104, 255))
    for (i, j) in ((2, 2), (13, 2)):
        px(a, i, j, (255, 40, 30, 255))
        px(a, i, j + 1, (150, 10, 10, 255))
    # warning triangle
    for j in range(4):
        for i in range(-j, j + 1):
            px(a, 8 + i, 12 + j - 1, HAZ_Y)
    px(a, 8, 12, (20, 20, 20, 255))
    save(a, 'block/mixer_core_side.png')

    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 14, base=hexc('30343a'), brushed=False)
    for j in range(16):
        for i in range(16):
            d = math.hypot(i - 7.5, j - 7.5)
            ang = math.atan2(j - 7.5, i - 7.5)
            if d < 7.5:
                c = shade(hexc('5a6068'), 0.8 + 0.25 * math.cos(ang * 4))
                if 2.2 < d < 6.8 and abs(math.sin(ang * 2)) < 0.22:
                    c = (16, 16, 18, 255)
                if d < 2.0:
                    c = (190, 196, 204, 255) if d < 1.0 else (90, 96, 104, 255)
                px(a, i, j, c)
    save(a, 'block/mixer_core_top.png')

    # mixer glass (cutout): steel frame, transparent centre, glints; bloody variants
    def glass(level, seed):
        a = canvas(16, 16, (0, 0, 0, 0))
        border(a, 0, 0, 16, 16, hexc('3a4048'))
        for (i, j) in ((3, 2), (4, 3), (5, 4), (3, 3), (11, 9), (12, 10), (10, 8)):
            px(a, i, j, (210, 236, 250, 255))
        px(a, 1, 1, (120, 128, 136, 255))
        px(a, 14, 14, (120, 128, 136, 255))
        r = random.Random(seed)
        mask = np.ones((16, 16), dtype=bool)
        for n in range(level * 3):
            splat(a, 1 + r.random() * 14, r.random() * 10, 1.0 + r.random() * (0.6 + level * 0.5), r, True, mask, drips=r.randint(1, 2 + level))
        if level >= 2:
            # hand print smear
            hx, hy = r.randint(3, 10), r.randint(3, 8)
            for (i, j) in ((0, 1), (1, 0), (2, 0), (3, 1), (0, 2), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3), (1, 4), (2, 4), (1, 5), (2, 6)):
                px(a, hx + i, hy + j, (150, 12, 14, 255))
        return a

    save(glass(0, 1), 'block/mixer_glass.png')
    for lvl in (1, 2, 3):
        save(glass(lvl, 30 + lvl), f'block/mixer_glass_blood_{lvl}.png')

    # press head
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 21, base=hexc('3f454d'))
    hazard(a, 0, 13, 16, 3, 1)
    for x0 in (2, 10):
        for j in range(1, 12):
            for i in range(4):
                k = [0.7, 1.25, 1.05, 0.75][i]
                px(a, x0 + i, j, shade(hexc('9aa4ae'), k))
        px(a, x0, 1, (30, 30, 34, 255))
        px(a, x0 + 3, 11, (30, 30, 34, 255))
    for j in range(2, 12):
        px(a, 7, j, (150, 20, 20, 255) if j % 3 else (70, 10, 10, 255))
    border(a, 0, 0, 16, 16, DARK_STEEL)
    save(a, 'block/press_head_side.png')
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 22, base=hexc('4a5058'))
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13), (7, 7)):
        rivet(a, x, y)
    border(a, 0, 0, 16, 16, DARK_STEEL)
    save(a, 'block/press_head_top.png')
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 23, base=hexc('2a2e34'))
    for j in range(16):
        for i in range(16):
            d = math.hypot(i - 7.5, j - 7.5)
            if 3.0 < d < 5.0:
                px(a, i, j, (120, 126, 134, 255))
            elif d <= 3.0:
                px(a, i, j, (12, 12, 14, 255))
    save(a, 'block/press_head_bottom.png')

    # spike bed: base + spike gradients (+ bloody)
    for lvl in range(3):
        r = random.Random(40 + lvl)
        a = canvas(16, 16)
        steel(a, 0, 0, 16, 16, 41, base=hexc('25282d'), brushed=False)
        for cy in (2, 6, 10, 14):
            for cx in (2, 6, 10, 14):
                px(a, cx, cy, (8, 8, 10, 255))
                px(a, cx - 1, cy - 1, (70, 74, 80, 255))
        mask = np.ones((16, 16), dtype=bool)
        for n in range(lvl * 4):
            splat(a, r.random() * 16, r.random() * 16, 1.2 + r.random() * 1.8, r, True, mask)
        save(a, 'block/spike_bed_base.png' if lvl == 0 else f'block/spike_bed_base_blood_{lvl}.png')
        a = canvas(16, 16)
        for j in range(16):
            for i in range(16):
                t = j / 15.0
                base = mix(hexc('e8eef4'), hexc('4a5058'), min(1.0, t * 1.3))
                k = 1.0 + (0.25 if i % 4 == 1 else (-0.15 if i % 4 == 3 else 0.0))
                c = shade(base, k)
                if lvl >= 1 and j <= 6 + lvl * 3 and r.random() < (0.85 if j < 5 else 0.45):
                    c = (130 + r.randint(0, 50), 8, 12, 255)
                px(a, i, j, c)
        save(a, 'block/spike_bed_spike.png' if lvl == 0 else f'block/spike_bed_spike_blood_{lvl}.png')

    # saw table
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 51, base=hexc('5a6068'))
    for j in range(16):
        px(a, 7, j, (12, 12, 14, 255))
        px(a, 8, j, (12, 12, 14, 255))
        px(a, 6, j, (40, 42, 46, 255))
        px(a, 9, j, (110, 116, 124, 255))
    for j in range(0, 16, 3):
        px(a, 2, j, (200, 200, 60, 255))
        px(a, 13, j, (200, 200, 60, 255))
    r = random.Random(52)
    for n in range(3):
        splat(a, 6 + r.random() * 4, r.random() * 16, 1.2, r, True, None)
    border(a, 0, 0, 16, 16, DARK_STEEL)
    save(a, 'block/saw_top.png')
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 53, base=hexc('7a1a1a'), amt=0.12)
    hazard(a, 0, 0, 16, 2)
    for j in (6, 8, 10):
        for i in range(4, 12):
            px(a, i, j, (20, 8, 8, 255))
    px(a, 13, 5, (255, 60, 40, 255))
    border(a, 0, 0, 16, 16, hexc('2a0808'))
    save(a, 'block/saw_side.png')

    # guillotine base (dark stained wood)
    for name, top in (('guillotine_base', False), ('guillotine_base_top', True)):
        a = canvas(16, 16)
        n = fbm(16, 16, 61, base=1.5, octaves=2)
        for j in range(16):
            for i in range(16):
                plank = (j // 4)
                base = shade(hexc('4a2c1c'), 0.85 + 0.15 * ((plank * 37) % 3) / 2)
                k = 1.0 + (n[j, i] - 0.5) * 0.4 + (0.12 if (i * 3 + plank) % 7 == 0 else 0)
                c = shade(base, k)
                if j % 4 == 3:
                    c = shade(c, 0.6)
                px(a, i, j, c)
        r = random.Random(62)
        for k_ in range(4 if top else 2):
            splat(a, r.random() * 16, r.random() * 16, 1.5 + r.random() * 1.5, r, fresh=False)
        if top:
            for i in range(16):
                px(a, i, 7, (20, 12, 8, 255))
                px(a, i, 8, (30, 18, 12, 255))
        save(a, f'block/{name}.png')

    # blood splatters (RGBA)
    for v in range(4):
        for dried in (False, True):
            a = canvas(16, 16, (0, 0, 0, 0))
            r = random.Random(70 + v)
            cx, cy = 7.5 + r.uniform(-1.5, 1.5), 7.5 + r.uniform(-1.5, 1.5)
            splat(a, cx, cy, 3.0 + v * 0.6 + r.random(), r, fresh=not dried)
            for s in range(2 + v):
                splat(a, r.uniform(2, 14), r.uniform(2, 14), 0.8 + r.random() * 1.3, r, fresh=not dried)
            if not dried:
                # glossy highlights
                for s in range(3):
                    hx, hy = int(cx + r.uniform(-2, 2)), int(cy + r.uniform(-2, 2))
                    if a[hy, hx, 3] > 0:
                        px(a, hx, hy, (230, 80, 80, 255))
            save(a, f'block/blood_splatter_{v}{"_dried" if dried else ""}.png')

    # host terminal
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 81, base=hexc('1e2024'))
    for j in range(2, 11):
        for i in range(2, 14):
            scan = 0.75 if j % 2 else 1.0
            px(a, i, j, shade((40, 6, 8, 255), scan))
    face = ["..XXXX..", ".X....X.", "X.X..X.X", "X......X", "X.XXXX.X", "X..XX..X", ".X....X.", "..XXXX.."]
    for j, row in enumerate(face):
        for i, ch in enumerate(row):
            if ch == 'X':
                px(a, 4 + i, 2 + j, (255, 40, 40, 255) if j != 4 else (255, 120, 120, 255))
    border(a, 1, 1, 14, 11, hexc('5a6068'))
    for i, c in enumerate(((220, 30, 30, 255), (240, 200, 40, 255), (60, 200, 80, 255))):
        px(a, 3 + i * 2, 13, c)
        px(a, 3 + i * 2, 14, shade(c, 0.5))
    px(a, 12, 13, (200, 200, 200, 255))
    px(a, 13, 13, (200, 200, 200, 255))
    save(a, 'block/host_terminal_front.png')
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 82, base=hexc('25282d'))
    for j in range(0, 16, 4):
        for i in range(16):
            px(a, i, j, (120, 14, 14, 255))
    border(a, 0, 0, 16, 16, hexc('101113'))
    save(a, 'block/host_terminal_side.png')
    a = canvas(16, 16)
    steel(a, 0, 0, 16, 16, 83, base=hexc('2a2d33'))
    for j in range(5, 11):
        for i in range(5, 11):
            px(a, i, j, (160, 16, 16, 255) if (i + j) % 2 == 0 else (110, 10, 10, 255))
    border(a, 0, 0, 16, 16, hexc('101113'))
    save(a, 'block/host_terminal_top.png')

    # tiles
    for name, bloody in (('execution_tiles', False), ('bloody_tiles', True)):
        a = canvas(16, 16)
        n = fbm(16, 16, 91, base=2.0, octaves=2)
        for j in range(16):
            for i in range(16):
                if i % 8 == 0 or j % 8 == 0:
                    c = (120, 118, 112, 255)
                else:
                    k = 0.93 + n[j, i] * 0.1
                    c = shade(hexc('e6e4dc'), k)
                    if (i % 8 == 1 and j % 8 == 1):
                        c = (250, 250, 248, 255)
                px(a, i, j, c)
        if bloody:
            r = random.Random(92)
            splat(a, 5, 6, 3.2, r, True, drips=2)
            splat(a, 12, 11, 2.2, r, False)
            for j in range(8, 16):
                px(a, 4, j, (120, 6, 8, 255))
            splat(a, 13, 3, 1.3, r, True)
        save(a, f'block/{name}.png')
    a = canvas(16, 16)
    n = fbm(16, 16, 101, base=3.0, octaves=3)
    r = random.Random(102)
    for j in range(16):
        for i in range(16):
            k = n[j, i]
            c = mix(hexc('5a3a26'), hexc('a85a26'), k)
            if r.random() < 0.08:
                c = hexc('c87830')
            px(a, i, j, c)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(a, x, y)
    border(a, 0, 0, 16, 16, hexc('3a2414'))
    save(a, 'block/rusted_plate.png')


# ------------------------------------------------------------------------------------------------ items

def draw_rows(a, rows, palette, ox=0, oy=0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in palette:
                px(a, ox + i, oy + j, palette[ch])


def items():
    # host hand: black glove reaching, red cuff
    a = canvas(16, 16, (0, 0, 0, 0))
    rows = [
        "......k.k.......",
        "....k.kgkgk.....",
        "....kgkgkgk.k...",
        "....kgkgkgkkgk..",
        "....kgggggkkgk..",
        "...kkgggggggk...",
        "..kgkgggggggk...",
        "..kggkgggggk....",
        "...kgggggggk....",
        "....kgggggk.....",
        "....kgggggk.....",
        ".....rrrrr......",
        ".....rRRRr......",
        ".....rrrrr......",
        "......kkk.......",
        "................",
    ]
    draw_rows(a, rows, {'k': (10, 10, 12, 255), 'g': (40, 40, 46, 255), 'r': (150, 10, 14, 255), 'R': (230, 50, 50, 255)})
    for (i, j) in ((6, 1), (8, 1), (10, 3)):
        px(a, i, j, (90, 90, 100, 255))
    save(a, 'item/host_hand.png')

    a = canvas(16, 16, (0, 0, 0, 0))
    rows = [
        "...........k....",
        "..........k.....",
        ".........k......",
        "....kkkkkkk.....",
        "....kgggggk.....",
        "....kgRRRgk.....",
        "....kgRrRgk.....",
        "....kgRRRgk.....",
        "....kgggggk.....",
        "....kgygbgk.....",
        "....kgggggk.....",
        "....kgwgwgk.....",
        "....kgggggk.....",
        "....kgwgwgk.....",
        "....kkkkkkk.....",
        "................",
    ]
    draw_rows(a, rows, {'k': (12, 12, 14, 255), 'g': (48, 50, 56, 255), 'R': (220, 20, 24, 255), 'r': (255, 120, 120, 255),
                        'y': (240, 200, 40, 255), 'b': (60, 200, 90, 255), 'w': (150, 150, 160, 255)})
    save(a, 'item/host_remote.png')

    a = canvas(16, 16, (0, 0, 0, 0))
    for j in range(4, 13):
        for i in range(1, 15):
            px(a, i, j, (24, 22, 26, 255) if 1 < i < 14 and 4 < j < 12 else (60, 56, 62, 255))
    for i in range(1, 15):
        jj = 4 + int(abs(i - 7.5) * 0.55)
        px(a, i, 12 - (jj - 4), (70, 66, 72, 255))
    for j in range(6, 11):
        for i in range(6, 11):
            if math.hypot(i - 8, j - 8) < 2.6:
                px(a, i, j, (180, 14, 18, 255) if math.hypot(i - 8, j - 8) < 1.8 else (110, 8, 10, 255))
    px(a, 8, 8, (250, 220, 120, 255))
    save(a, 'item/contestant_ticket.png')

    # kits: crate with icon
    def crate(seed):
        a = canvas(16, 16, (0, 0, 0, 0))
        for j in range(8, 16):
            for i in range(1, 15):
                c = shade(hexc('6a4a2a'), 0.85 + 0.2 * ((i * 7 + j * 3 + seed) % 5) / 4)
                if i in (1, 14) or j in (8, 15):
                    c = hexc('3a2414')
                if (i - j) % 6 == 0:
                    c = hexc('4a301a')
                px(a, i, j, c)
        hazard(a, 2, 9, 12, 1)
        return a

    a = crate(1)
    for j in range(1, 9):
        for i in range(4, 12):
            edge = i in (4, 11)
            px(a, i, j, (60, 66, 72, 255) if edge else ((170, 14, 18, 255) if j >= 5 else (190, 225, 240, 120 if False else 255)) if j >= 5 or (i + j) % 5 == 0 else (0, 0, 0, 0))
    for i in range(5, 11):
        px(a, i, 7, (200, 200, 210, 255) if i % 2 else (120, 10, 10, 255))
    save(a, 'item/mixer_kit.png')

    a = crate(2)
    for i in range(2, 14):
        px(a, i, 1, (90, 96, 104, 255))
        px(a, i, 2, (60, 64, 70, 255))
    for j in range(3, 5):
        px(a, 7, j, (200, 206, 214, 255))
        px(a, 8, j, (150, 156, 164, 255))
    for i in range(3, 13, 2):
        px(a, i, 7, (220, 226, 234, 255))
        px(a, i, 6, (240, 246, 250, 255))
        px(a, i, 5, (180, 20, 24, 255) if i % 4 == 1 else (230, 236, 240, 255))
    save(a, 'item/press_kit.png')

    a = crate(3)
    for j in range(1, 8):
        for i in range(2, 14):
            wall = i in (2, 13) or j in (1, 7)
            if wall:
                px(a, i, j, (150, 80, 40, 255))
            else:
                px(a, i, j, (230, 228, 220, 255) if (i + j) % 2 else (200, 30, 30, 255) if (i * j) % 7 == 0 else (210, 208, 200, 255))
    save(a, 'item/arena_kit.png')

    a = canvas(16, 16, (0, 0, 0, 0))
    for t in range(11):
        px(a, 4 + t, 11 - t, (130, 90, 50, 255))
        px(a, 5 + t, 11 - t, (90, 60, 32, 255))
    rows = ["..wwww..", ".wwrwww.", "wwwwrww.", "wrwwwwr.", "w.w.w.w.", "w.r.w.r."]
    draw_rows(a, rows, {'w': (200, 196, 186, 255), 'r': (140, 12, 16, 255)}, 0, 10)
    save(a, 'item/mop.png')

    # saw icon
    a = canvas(16, 16, (0, 0, 0, 0))
    for j in range(16):
        for i in range(16):
            d = math.hypot(i - 7.5, j - 7.5)
            ang = math.atan2(j - 7.5, i - 7.5)
            tooth = 6.2 + 1.2 * ((ang * 12 / math.tau) % 1.0)
            if d <= tooth:
                c = shade(hexc('c0c8d0'), 0.75 + 0.3 * math.cos(ang - 0.8))
                if d > 6.0:
                    c = (200, 20, 24, 255) if int(ang * 12 / math.tau) % 3 == 0 else (230, 236, 240, 255)
                if d < 1.6:
                    c = (40, 40, 44, 255)
                px(a, i, j, c)
    save(a, 'item/saw.png')

    a = canvas(16, 16, (0, 0, 0, 0))
    wood = hexc('5a3420')
    for j in range(1, 16):
        px(a, 3, j, wood)
        px(a, 12, j, wood)
        px(a, 4, j, shade(wood, 0.7))
    for i in range(2, 14):
        px(a, i, 1, shade(wood, 1.15))
        px(a, i, 15, shade(wood, 0.9))
    for i in range(5, 12):
        for j in range(3, 6):
            if j - 3 <= (i - 5) * 0.45 + 0.5:
                px(a, i, j + 0, (210, 218, 226, 255) if j < 5 else (240, 246, 250, 255))
    for i in range(5, 12):
        px(a, i, 10, shade(wood, 1.1))
        px(a, i, 12, shade(wood, 1.1))
    px(a, 8, 11, (12, 8, 8, 255))
    px(a, 7, 11, (12, 8, 8, 255))
    px(a, 9, 11, (12, 8, 8, 255))
    px(a, 8, 13, (160, 10, 14, 255))
    px(a, 8, 14, (130, 8, 10, 255))
    save(a, 'item/guillotine.png')


# ------------------------------------------------------------------------------------------------ trap parts (block entities)

def entity_parts():
    # mixer blades 64x32: hub (0,0,6,5,6), cap (24,0,4,2,4), blade (0,12,19,1,4)
    for bloody in (False, True):
        a = canvas(64, 32, (0, 0, 0, 0))
        r = random.Random(5 + bloody)
        for f, (x, y, w, h) in box_uv(0, 0, 6, 5, 6).items():
            steel(a, x, y, w, h, 3, base=hexc('3a3e44'))
        for f, (x, y, w, h) in box_uv(24, 0, 4, 2, 4).items():
            steel(a, x, y, w, h, 4, base=hexc('8a929c'))
        for f, (x, y, w, h) in box_uv(0, 12, 19, 1, 4).items():
            for j in range(h):
                for i in range(w):
                    t = i / max(1, w - 1)
                    c = shade(hexc('c8d0d8'), 0.85 + 0.25 * t)
                    if f in ('up', 'down') and (j == 0 or j == h - 1):
                        c = (245, 250, 255, 255)
                    if f in ('up', 'down') and i % 3 == 0 and (j == 0 or j == h - 1):
                        c = (120, 126, 134, 255)
                    px(a, x + i, y + j, c)
        if bloody:
            mask = a[..., 3] > 0
            for n in range(26):
                splat(a, r.random() * 46, 12 + r.random() * 5 if r.random() < 0.8 else r.random() * 11, 0.8 + r.random() * 1.4, r, True, mask)
        save(a, f'entity/mixer_blades{"_bloody" if bloody else ""}.png')

    # mixer soup 16x64: 4 animation frames of swirling blood
    a = canvas(16, 64)
    for fr in range(4):
        for j in range(16):
            for i in range(16):
                ang = math.atan2(j - 7.5, i - 7.5)
                d = math.hypot(i - 7.5, j - 7.5)
                s = math.sin(ang * 3 + d * 0.9 - fr * math.pi / 2)
                k = 0.75 + 0.25 * s
                c = shade((140, 8, 12, 255), k)
                if s > 0.85:
                    c = (200, 40, 40, 255)
                if (i * 7 + j * 13 + fr * 5) % 29 == 0:
                    c = (230, 120, 110, 255)
                if (i * 11 + j * 5 + fr * 3) % 37 == 0:
                    c = (240, 230, 210, 255)
                px(a, i, fr * 16 + j, c)
    save(a, 'entity/mixer_soup.png')

    # press plate 256x64: box (0,0,48,4,48). 'down' = bottom (contact) face for block entities.
    for bloody in (False, True):
        a = canvas(256, 64, (0, 0, 0, 0))
        uv = box_uv(0, 0, 48, 4, 48)
        r = random.Random(11 + bloody)
        for f, (x, y, w, h) in uv.items():
            if f == 'down':
                steel(a, x, y, w, h, 31, base=hexc('5a6068'), amt=0.2)
                for j in range(0, h, 8):
                    for i in range(w):
                        px(a, x + i, y + j, (36, 38, 42, 255))
                for i in range(0, w, 8):
                    for j in range(h):
                        px(a, x + i, y + j, (36, 38, 42, 255))
            elif f == 'up':
                steel(a, x, y, w, h, 32, base=hexc('3c4148'))
                for (i, j) in ((4, 4), (43, 4), (4, 43), (43, 43), (23, 23)):
                    rivet(a, x + i, y + j)
                hazard(a, x, y, w, 3)
                hazard(a, x, y + h - 3, w, 3)
            else:
                hazard(a, x, y, w, h, 0)
        if bloody:
            x, y, w, h = uv['down']
            mask = np.zeros((64, 256), dtype=bool)
            mask[y:y + h, x:x + w] = True
            for n in range(70):
                splat(a, x + r.random() * w, y + r.random() * h, 1.0 + r.random() * 3.6, r, r.random() < 0.8, mask)
            for f in ('north', 'south', 'east', 'west'):
                fx, fy, fw, fh = uv[f]
                m2 = np.zeros((64, 256), dtype=bool)
                m2[fy:fy + fh, fx:fx + fw] = True
                for n in range(8):
                    splat(a, fx + r.random() * fw, fy + fh - 1, 1.0, r, True, m2)
        save(a, f'entity/press_plate{"_bloody" if bloody else ""}.png')

    # press rod 32x32: chrome (0,0,6,16,6)
    a = canvas(32, 32, (0, 0, 0, 0))
    for f, (x, y, w, h) in box_uv(0, 0, 6, 16, 6).items():
        for j in range(h):
            for i in range(w):
                k = [0.55, 0.9, 1.3, 1.15, 0.85, 0.6][i % 6] if f not in ('up', 'down') else 0.9
                px(a, x + i, y + j, shade(hexc('b8c2cc'), k))
    save(a, 'entity/press_rod.png')

    # saw blade 32x32 (alpha disc with teeth)
    for bloody in (False, True):
        a = canvas(32, 32, (0, 0, 0, 0))
        r = random.Random(77)
        for j in range(32):
            for i in range(32):
                d = math.hypot(i - 15.5, j - 15.5)
                ang = math.atan2(j - 15.5, i - 15.5)
                frac = (ang * 24 / math.tau) % 1.0
                rim = 13.0 + 2.6 * frac
                if d <= rim:
                    c = shade(hexc('aeb8c2'), 0.8 + 0.3 * math.cos(ang * 2 - 0.6) * (d / 16.0))
                    if 9.0 < d < 9.8:
                        c = (90, 96, 104, 255)
                    if d > 12.6:
                        c = (235, 240, 246, 255)
                        if bloody and r.random() < 0.65:
                            c = (150 + r.randint(0, 60), 8, 12, 255)
                    if d < 3.2:
                        c = (40, 42, 46, 255) if d > 1.4 else (150, 156, 164, 255)
                    if 4 < d < 8 and abs(math.sin(ang * 3)) < 0.18:
                        c = (60, 64, 70, 255)
                    if bloody and 6 < d < 12.6 and r.random() < 0.12:
                        c = (120, 6, 10, 255)
                    px(a, i, j, c)
        save(a, f'entity/saw_blade{"_bloody" if bloody else ""}.png')

    # guillotine 64x64: frame boxes + blade (alpha cut) + weight
    for bloody in (False, True):
        a = canvas(64, 64, (0, 0, 0, 0))
        r = random.Random(90 + bloody)

        def woodbox(u, v, w, h, d, seed):
            for f, (x, y, fw, fh) in box_uv(u, v, w, h, d).items():
                n = fbm(max(fw, 1), max(fh, 1), seed, base=1.5, octaves=2)
                for j in range(fh):
                    for i in range(fw):
                        k = 0.8 + 0.4 * n[j, i]
                        c = shade(hexc('4e2e1c'), k)
                        if (i + j * 3) % 11 == 0:
                            c = shade(c, 0.7)
                        px(a, x + i, y + j, c)

        woodbox(0, 0, 2, 42, 2, 1)
        woodbox(8, 0, 2, 42, 2, 2)
        woodbox(16, 0, 17, 3, 3, 3)
        woodbox(16, 8, 4, 4, 2, 4)
        woodbox(28, 8, 4, 4, 2, 5)
        woodbox(16, 26, 12, 2, 2, 6)
        # iron fittings on crossbar
        for f, (x, y, w, h) in box_uv(16, 0, 17, 3, 3).items():
            if f in ('north', 'south'):
                for j in range(h):
                    px(a, x + 1, y + j, (90, 90, 96, 255))
                    px(a, x + w - 2, y + j, (90, 90, 96, 255))
        # blade 12x7x1 at (16,16): front/back faces get the slanted edge
        for f, (x, y, w, h) in box_uv(16, 16, 12, 7, 1).items():
            for j in range(h):
                for i in range(w):
                    if f in ('north', 'south'):
                        edge_y = 2 + (i if f == 'north' else (w - 1 - i)) * 0.42
                        if j > edge_y + 0.5:
                            px(a, x + i, y + j, (0, 0, 0, 0))
                            continue
                        c = shade(hexc('b0bac4'), 0.8 + 0.06 * j)
                        if j >= edge_y - 1.0:
                            c = (240, 246, 252, 255)
                            if bloody and r.random() < 0.7:
                                c = (150 + r.randint(0, 60), 8, 12, 255)
                        if j == 0:
                            c = (70, 74, 80, 255)
                        px(a, x + i, y + j, c)
                    else:
                        px(a, x + i, y + j, (150, 156, 164, 255))
        # weight 8x3x2 at (44,16)
        for f, (x, y, w, h) in box_uv(44, 16, 8, 3, 2).items():
            steel(a, x, y, w, h, 9, base=hexc('2a2c30'))
        if bloody:
            # stains on the lunette boards
            for (u, v) in ((16, 8), (28, 8)):
                for f, (x, y, w, h) in box_uv(u, v, 4, 4, 2).items():
                    if f in ('up', 'north', 'south'):
                        m = np.zeros((64, 64), dtype=bool)
                        m[y:y + h, x:x + w] = True
                        splat(a, x + w / 2, y + 1, 1.6, r, True, m, drips=2)
        save(a, f'entity/guillotine{"_bloody" if bloody else ""}.png')


# ------------------------------------------------------------------------------------------------ particles

def particles():
    for v in range(4):
        a = canvas(8, 8, (0, 0, 0, 0))
        rad = 1.4 + v * 0.45
        for j in range(8):
            for i in range(8):
                d = math.hypot(i - 3.5, (j - 3.5) * (1.0 if v < 2 else 0.8))
                if d <= rad:
                    k = 0.75 + 0.35 * (1 - d / (rad + 0.01))
                    px(a, i, j, shade((255, 255, 255, 255), k))
        px(a, 3, 3, (255, 255, 255, 255))
        save(a, f'particle/blood_drop_{v}.png')
    for v in range(4):
        a = canvas(16, 16, (0, 0, 0, 0))
        r = random.Random(300 + v)
        splat(a, 7.5, 7.5, 3.4 + v * 0.5, r, True)
        for s in range(4):
            splat(a, r.uniform(2, 14), r.uniform(2, 14), 0.7 + r.random(), r, True)
        g = np.clip(a[..., 0] / 185.0, 0.6, 1.0)
        out = canvas(16, 16, (0, 0, 0, 0))
        out[..., 0] = out[..., 1] = out[..., 2] = 255 * g
        out[..., 3] = a[..., 3]
        save(out, f'particle/blood_splat_{v}.png')
    for v in range(4):
        a = canvas(16, 16, (0, 0, 0, 0))
        r = random.Random(500 + v)
        rad = 3.0 + v * 1.4
        for j in range(16):
            for i in range(16):
                d = math.hypot(i - 7.5, j - 7.5)
                n = 0.6 + 0.4 * r.random()
                al = max(0.0, 1.0 - d / rad) * n * (1.0 - v * 0.15)
                if al > 0.02:
                    px(a, i, j, (255, 255, 255, int(255 * min(1.0, al * 1.3))))
        save(a, f'particle/blood_mist_{v}.png')
    cols = [((170, 18, 24), (230, 190, 170)), ((120, 10, 40), (170, 60, 90)), ((236, 228, 205), (190, 180, 160)), ((150, 12, 16), (90, 6, 8))]
    for v in range(4):
        a = canvas(8, 8, (0, 0, 0, 0))
        r = random.Random(700 + v)
        c1, c2 = cols[v]
        for j in range(1, 7):
            for i in range(1, 7):
                if math.hypot(i - 3.5, j - 3.5) < 2.6 + r.random() * 0.5:
                    c = c1 if r.random() < 0.7 else c2
                    px(a, i, j, c + (255,))
        save(a, f'particle/gore_bit_{v}.png')


def main():
    blocks()
    items()
    entity_parts()
    particles()
    print('blocks/items/parts/particles done')


if __name__ == '__main__':
    main()
