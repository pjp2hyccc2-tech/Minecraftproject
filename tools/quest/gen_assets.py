#!/usr/bin/env python3
"""Textures, models and blockstates of the quest module blocks / items (16x16 pixel art).
    python3 tools/quest/gen_assets.py"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
TEX = os.path.join(ASSETS, 'textures')
NS = 'huntcraft'


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def from_ascii(rows, pal):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch])
    return img


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noise(seed, amp=1.0):
    r = np.random.default_rng(seed)
    return r.random((16, 16)) * amp


def ramp_img(values, colors):
    """values 0..1 -> palette ramp."""
    cols = np.array([hexc(c) for c in colors], np.uint8)
    idx = np.clip((values * len(colors)).astype(int), 0, len(colors) - 1)
    return Image.fromarray(cols[idx], 'RGBA').copy()


# ====================================================================== plants (cross models)
HERB = [
    "................",
    "......w..w......",
    ".....wyw.yw.....",
    "......w..w..w...",
    "..L...g..g.wyw..",
    "..LL..g.Lg..w...",
    "...LL.gLLg..g...",
    ".l..LLgLgg.gg...",
    ".ll..LgLg.gG....",
    "..llLGGgGgGG.ll.",
    "...lLGGgGGG.ll..",
    "....dGGgGGdll...",
    ".....dGGGGd.....",
    "......dGGd......",
    "......dssd......",
    ".......ss.......",
]
HERB_E = [
    "................", "................", "................", "................", "................",
    "................", "................", "................", "................", "................",
    "................",
    "......s...s.....",
    "....s.s.s.s.s...",
    "....ssdsdsds....",
    ".....dddddd.....",
    "................",
]
MUSH = [
    "................",
    "................",
    "....bbbb........",
    "...bBBcBb.......",
    "..bBcBBBBb......",
    "..bBBBBcBb...bb.",
    "..ddddddddd.bBcb",
    "......ss...bBBBb",
    "......ss...dddd.",
    ".bbb..ss....ss..",
    "bBcBb.sS....ss..",
    "bBBBb.sS....sS..",
    "ddddd.sS....sS..",
    "..ss..sS....sS..",
    "..sS..sS...ggsg.",
    ".gsSggsSgggg.g..",
]
MUSH_E = [
    "................", "................", "................", "................", "................",
    "................", "................", "................", "................", "................",
    "................", "................",
    "......ss....s...",
    "..s...sS....sS..",
    "..sS..sS...ggsg.",
    ".gsSggsSgggg.g..",
]
BUG = [
    "................",
    "....Y.......Y...",
    "...YyY.....YyY..",
    "....Y...t...Y...",
    ".......tt.......",
    "..t..ttLt..t....",
    "..tt.tLLtttt.Y..",
    "...ttLLtLtt.YyY.",
    ".Y..tLtLLt...Y..",
    "YyY.ttLtLtt.....",
    ".Y.ttLtttLtt....",
    "...tLttLttLt....",
    "....ttLLLtt.....",
    ".....tbbbt......",
    "......bb........",
    "......bb........",
]
BUG_E = [
    "................", "................", "................", "................", "................",
    "................", "................",
    "......b...b.....",
    ".......b.b......",
    "....b...b...b...",
    ".....b..b..b....",
    "......b.b.b.....",
    ".......bbb......",
    "........b.......",
    "........b.......",
    "........b.......",
]


def plants():
    pal_h = {'w': hexc('#FFFFFF'), 'y': hexc('#F5D04A'), 'L': hexc('#79C85A'), 'l': hexc('#4C9A3A'), 'g': hexc('#5DB043'),
             'G': hexc('#3E8A30'), 'd': hexc('#2E6B2A'), 's': hexc('#6A5A2A')}
    save(from_ascii(HERB, pal_h), 'block/gather_herb.png')
    save(from_ascii(HERB_E, pal_h), 'block/gather_herb_empty.png')
    pal_m = {'b': hexc('#2A55B8'), 'B': hexc('#4A80E8'), 'c': hexc('#C8E0FF'), 'd': hexc('#1E3C88'), 's': hexc('#E8E0C8'),
             'S': hexc('#B8AE90'), 'g': hexc('#4C9A3A')}
    save(from_ascii(MUSH, pal_m), 'block/gather_mushroom.png')
    save(from_ascii(MUSH_E, pal_m), 'block/gather_mushroom_empty.png')
    pal_b = {'Y': hexc('#B0E040'), 'y': hexc('#F4FF8A'), 't': hexc('#3A7A34'), 'L': hexc('#5AA048'), 'b': hexc('#6A4A2A')}
    save(from_ascii(BUG, pal_b), 'block/gather_bug.png')
    save(from_ascii(BUG_E, pal_b), 'block/gather_bug_empty.png')


# ====================================================================== materials
def bone():
    n = noise(5) * 0.35 + 0.55
    v = np.tile(np.linspace(0.15, 0.0, 16)[:, None], (1, 16))
    img = ramp_img(np.clip(n + v, 0, 0.999), ['#8A8068', '#B8AE90', '#D0C8AE', '#E8E2CC', '#F6F2E4'])
    px = img.load()
    for (x, y) in [(3, 4), (4, 5), (10, 9), (11, 10), (7, 13)]:
        px[x, y] = hexc('#7A7058')
    save(img, 'block/gather_bone.png')
    dim = Image.eval(img, lambda c: int(c * 0.82))
    save(dim, 'block/gather_bone_empty.png')


def rock(ore):
    n = noise(11 if ore else 12) * 0.6 + 0.2
    img = ramp_img(n, ['#4E4E52', '#646468', '#7A7A7E', '#8E8E92', '#A0A0A4'])
    px = img.load()
    if ore:
        for (x, y) in [(3, 3), (4, 3), (4, 4), (5, 5), (11, 4), (12, 5), (12, 6), (8, 9), (9, 9), (9, 10), (10, 11), (3, 11), (4, 12),
                       (13, 12), (6, 7)]:
            px[x, y] = hexc('#3A8AE8')
        for (x, y) in [(4, 3), (12, 5), (9, 9), (4, 12), (13, 12)]:
            px[x, y] = hexc('#9AD8FF')
        for (x, y) in [(5, 4), (10, 10), (12, 4)]:
            px[x, y] = hexc('#FFFFFF')
    save(img, 'block/gather_mining.png' if ore else 'block/gather_mining_empty.png')


def planks(c_dark, c_mid, c_light, seed):
    n = noise(seed) * 0.25
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        band = y // 4
        for x in range(16):
            v = 0.55 + n[y, x] - (0.18 if y % 4 == 3 else 0) + (0.06 if band % 2 else 0)
            c = c_light if v > 0.72 else c_mid if v > 0.45 else c_dark
            px[x, y] = hexc(c)
    for y in range(16):
        x = (5 + (y // 4) * 7) % 16
        if y % 4 != 3:
            pass
        else:
            continue
    return img


def box(prefix, dark, mid, light, emblem):
    side = planks(dark, mid, light, 21)
    px = side.load()
    metal, metal_d = hexc('#D0D0D4'), hexc('#7A7A80')
    for i in range(16):
        for (x, y) in [(0, i), (15, i), (i, 0), (i, 15)]:
            px[x, y] = metal_d
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14), (2, 1), (1, 2), (13, 1), (14, 2), (1, 13), (2, 14), (14, 13), (13, 14)]:
        px[x, y] = metal
    save(side, f'block/{prefix}_side.png')
    front = side.copy()
    fp = front.load()
    for (x, y) in emblem:
        fp[x, y] = hexc('#F2D27A')
    for (x, y) in emblem:
        if (x + 1, y + 1) not in emblem and 0 < x + 1 < 15 and 0 < y + 1 < 15:
            fp[x + 1, y + 1] = hexc('#6A4A1A') if fp[x + 1, y + 1] != hexc('#F2D27A') else fp[x + 1, y + 1]
    save(front, f'block/{prefix}_front.png')
    top = planks(dark, mid, light, 22).rotate(90)
    tp = top.load()
    for i in range(16):
        for (x, y) in [(0, i), (15, i), (i, 0), (i, 15)]:
            tp[x, y] = metal_d
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        tp[x, y] = metal
    save(top, f'block/{prefix}_top.png')


def emblem_claw():
    # three-claw guild crest
    pts = set()
    for (x0, y0) in [(5, 4), (8, 3), (11, 4)]:
        for k in range(6):
            pts.add((x0 - k // 3, y0 + k))
    for x in range(5, 11):
        pts.add((x, 10))
        pts.add((x, 11))
    return pts


def emblem_arrow():
    pts = set()
    for y in range(3, 9):
        pts.add((7, y))
        pts.add((8, y))
    for k in range(4):
        for x in range(4 + k, 12 - k):
            pts.add((x, 9 + k))
    return pts


def quest_board():
    board = planks('#5A3C22', '#7A5432', '#9A6E44', 31)
    px = board.load()
    paper, line, pin = hexc('#F2E8CC'), hexc('#A8987A'), hexc('#D03A2A')
    notes = [(1, 2, 6, 7), (8, 1, 14, 6), (2, 9, 7, 14), (9, 8, 14, 14)]
    for (x0, y0, x1, y1) in notes:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = paper
        for y in range(y0 + 2, y1, 2):
            for x in range(x0 + 1, x1):
                px[x, y] = line
        px[(x0 + x1) // 2, y0] = pin
    for x in range(16):
        px[x, 0] = hexc('#3E2814')
        px[x, 15] = hexc('#3E2814')
    save(board, 'block/quest_board.png')
    save(planks('#5A3C22', '#7A5432', '#9A6E44', 32), 'block/quest_board_wood.png')


def flag():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    blue, blue_d, gold = hexc('#2A4CA8'), hexc('#1A3278'), hexc('#E8C25A')
    for y in range(16):
        for x in range(16):
            px[x, y] = blue if (x + y) % 7 else blue_d
    for x in range(16):
        px[x, 0] = gold
        px[x, 1] = gold
    for y in range(16):
        px[0, y] = gold
        px[15, y] = gold
    # ragged bottom
    for x in range(16):
        if x % 4 in (1, 2):
            px[x, 15] = (0, 0, 0, 0)
            px[x, 14] = blue_d
    for (x, y) in emblem_claw():
        if 1 <= x <= 14 and 2 <= y <= 13:
            px[x, y + 1] = gold
    save(img, 'block/camp_flag.png')
    save(planks('#4A3020', '#6A4A2E', '#8A6440', 41), 'block/camp_flag_pole.png')
    # item icon
    icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ip = icon.load()
    for y in range(1, 16):
        ip[3, y] = hexc('#6A4A2E')
        ip[4, y] = hexc('#4A3020')
    for x in range(2, 14):
        ip[x, 1] = hexc('#8A6440')
    for y in range(2, 12):
        for x in range(5, 13):
            ip[x, y] = blue if (x + y) % 5 else blue_d
    for x in range(5, 13):
        ip[x, 2] = gold
    for y in range(9, 13):
        for x in range(5, 13):
            if y >= 11 and x % 3 == 0:
                ip[x, y] = (0, 0, 0, 0)
    for (x, y) in [(8, 4), (9, 4), (7, 5), (8, 6), (9, 6), (10, 5), (8, 8), (9, 8)]:
        ip[x, y] = gold
    save(icon, 'item/camp_flag.png')


MAP_ITEM = [
    "................",
    ".PPPPPPPPPPPPP..",
    ".PpGGpppbbpppPd.",
    ".PpGGGppbbppppd.",
    ".PppGpppbpprRpd.",
    ".PpppppbbppRrpd.",
    ".PppcppbpprRrPd.",
    ".PpcccpbbppRppd.",
    ".PppcppbbppppPd.",
    ".PpppppbppGGppd.",
    ".PpbbbbbppGGGpd.",
    ".PppppppppGGppd.",
    ".PPPPPPPPPPPPPd.",
    "..ddddddddddddd.",
    "................",
    "................",
]


def map_item():
    pal = {'P': hexc('#8A6A3A'), 'p': hexc('#EAD9A8'), 'G': hexc('#6AA84A'), 'b': hexc('#4A88D8'), 'R': hexc('#D03A2A'),
           'r': hexc('#F06A4A'), 'c': hexc('#A08A5A'), 'd': hexc('#5A4424')}
    save(from_ascii(MAP_ITEM, pal), 'item/quest_map.png')


# ====================================================================== models / blockstates
def write_json(rel, obj):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def face(tex, uv=None, cull=None):
    d = {"texture": tex}
    if uv:
        d["uv"] = uv
    if cull:
        d["cullface"] = cull
    return d


def cuboid(fr, to, tex, uv_scale=True, rotation=None, faces=None):
    e = {"from": fr, "to": to, "faces": {}}
    for fname in ['north', 'south', 'east', 'west', 'up', 'down']:
        if faces and fname not in faces:
            continue
        if fname in ('north', 'south'):
            uv = [fr[0], 16 - to[1], to[0], 16 - fr[1]]
        elif fname in ('east', 'west'):
            uv = [fr[2], 16 - to[1], to[2], 16 - fr[1]]
        else:
            uv = [fr[0], fr[2], to[0], to[2]]
        uv = [max(0, min(16, v)) for v in uv]
        t = tex[fname] if isinstance(tex, dict) else tex
        e["faces"][fname] = face(t, uv)
    if rotation:
        e["rotation"] = rotation
    return e


def models():
    # plants (cross)
    for k in ('herb', 'mushroom', 'bug'):
        for suffix in ('', '_empty'):
            write_json(f'models/block/gather_{k}{suffix}.json',
                       {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"{NS}:block/gather_{k}{suffix}"}})
        write_json(f'blockstates/gather_{k}.json', {"variants": {
            "charges=0": {"model": f"{NS}:block/gather_{k}_empty"},
            "charges=1": {"model": f"{NS}:block/gather_{k}"},
            "charges=2": {"model": f"{NS}:block/gather_{k}"},
            "charges=3": {"model": f"{NS}:block/gather_{k}"}}})
        write_json(f'models/item/gather_{k}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/gather_{k}"}})
    # bone pile
    for suffix, count in (('', 5), ('_empty', 2)):
        t = f"#bone"
        els = [
            cuboid([2, 0, 6], [14, 2, 8], t, rotation={"origin": [8, 1, 7], "axis": "y", "angle": 22.5}),
            cuboid([3, 0, 2], [5, 2, 13], t, rotation={"origin": [4, 1, 8], "axis": "y", "angle": -22.5}),
            cuboid([9, 0, 9], [15, 3, 14], t),
            cuboid([6, 2, 5], [11, 4, 9], t, rotation={"origin": [8, 3, 7], "axis": "y", "angle": 45}),
            cuboid([1, 0, 11], [9, 2, 13], t),
        ][:count]
        write_json(f'models/block/gather_bone{suffix}.json',
                   {"parent": "minecraft:block/block", "textures": {"bone": f"{NS}:block/gather_bone{suffix}", "particle": f"{NS}:block/gather_bone{suffix}"},
                    "elements": els})
    write_json('blockstates/gather_bone.json', {"variants": {
        "charges=0": {"model": f"{NS}:block/gather_bone_empty"}, "charges=1": {"model": f"{NS}:block/gather_bone"},
        "charges=2": {"model": f"{NS}:block/gather_bone"}, "charges=3": {"model": f"{NS}:block/gather_bone"}}})
    write_json('models/item/gather_bone.json', {"parent": f"{NS}:block/gather_bone"})
    # mining rock
    for suffix in ('', '_empty'):
        t = "#rock"
        write_json(f'models/block/gather_mining{suffix}.json',
                   {"parent": "minecraft:block/block", "textures": {"rock": f"{NS}:block/gather_mining{suffix}", "particle": f"{NS}:block/gather_mining{suffix}"},
                    "elements": [cuboid([1, 0, 2], [15, 8, 14], t), cuboid([4, 8, 4], [12, 13, 12], t),
                                 cuboid([2, 0, 11], [6, 5, 15], t)]})
    write_json('blockstates/gather_mining.json', {"variants": {
        "charges=0": {"model": f"{NS}:block/gather_mining_empty"}, "charges=1": {"model": f"{NS}:block/gather_mining"},
        "charges=2": {"model": f"{NS}:block/gather_mining"}, "charges=3": {"model": f"{NS}:block/gather_mining"}}})
    write_json('models/item/gather_mining.json', {"parent": f"{NS}:block/gather_mining"})
    # boxes (front = north in the model)
    for b in ('supply_box', 'delivery_box'):
        tex = {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"}
        write_json(f'models/block/{b}.json', {"parent": "minecraft:block/block",
                                               "textures": {"front": f"{NS}:block/{b}_front", "side": f"{NS}:block/{b}_side", "top": f"{NS}:block/{b}_top",
                                                            "particle": f"{NS}:block/{b}_side"},
                                               "elements": [cuboid([1, 0, 1], [15, 12, 15], tex)]})
        write_json(f'models/item/{b}.json', {"parent": f"{NS}:block/{b}"})
        facing(b)
    # quest board
    wood = "#wood"
    board_tex = {"north": "#board", "south": "#wood", "east": "#wood", "west": "#wood", "up": "#wood", "down": "#wood"}
    write_json('models/block/quest_board.json', {"parent": "minecraft:block/block",
                                                 "textures": {"board": f"{NS}:block/quest_board", "wood": f"{NS}:block/quest_board_wood",
                                                              "particle": f"{NS}:block/quest_board_wood"},
                                                 "elements": [cuboid([0, 4, 7], [16, 16, 9], board_tex), cuboid([1, 0, 7], [3, 4, 9], wood),
                                                              cuboid([13, 0, 7], [15, 4, 9], wood), cuboid([0, 15, 6], [16, 16, 10], wood)]})
    write_json('models/item/quest_board.json', {"parent": f"{NS}:block/quest_board"})
    facing('quest_board')
    # camp flag (tall banner on a pole, cloth faces north)
    pole = "#pole"
    write_json('models/block/camp_flag.json', {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                               "textures": {"cloth": f"{NS}:block/camp_flag", "pole": f"{NS}:block/camp_flag_pole",
                                                            "particle": f"{NS}:block/camp_flag_pole"},
                                               "elements": [
                                                   cuboid([7, 0, 7], [9, 16, 9], pole), cuboid([7, 16, 7], [9, 30, 9], pole),
                                                   cuboid([1, 28, 7.5], [15, 29, 8.5], pole),
                                                   {"from": [2, 13, 8], "to": [14, 28, 8],
                                                    "faces": {"north": face("#cloth", [0, 0, 16, 16]), "south": face("#cloth", [16, 0, 0, 16])}}]})
    write_json('models/item/camp_flag.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/camp_flag"}})
    facing('camp_flag')
    write_json('models/item/quest_map.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/quest_map"}})


def facing(b):
    write_json(f'blockstates/{b}.json', {"variants": {
        "facing=north": {"model": f"{NS}:block/{b}"}, "facing=east": {"model": f"{NS}:block/{b}", "y": 90},
        "facing=south": {"model": f"{NS}:block/{b}", "y": 180}, "facing=west": {"model": f"{NS}:block/{b}", "y": 270}}})


def main():
    plants()
    bone()
    rock(True)
    rock(False)
    box('supply_box', '#1E3A78', '#2E56A8', '#4A7AD0', emblem_claw())
    box('delivery_box', '#6A1A1A', '#A02E2E', '#C84A4A', emblem_arrow())
    quest_board()
    flag()
    map_item()
    models()
    # contact sheet
    files = ['block/gather_herb', 'block/gather_herb_empty', 'block/gather_mushroom', 'block/gather_mushroom_empty', 'block/gather_bug',
             'block/gather_bug_empty', 'block/gather_bone', 'block/gather_mining', 'block/gather_mining_empty', 'block/supply_box_front',
             'block/supply_box_side', 'block/supply_box_top', 'block/delivery_box_front', 'block/quest_board', 'block/camp_flag',
             'item/camp_flag', 'item/quest_map']
    sheet = Image.new('RGBA', (len(files) * 68, 68), (40, 40, 48, 255))
    for i, f in enumerate(files):
        im = Image.open(os.path.join(TEX, f + '.png')).resize((64, 64), Image.NEAREST)
        sheet.alpha_composite(im, (i * 68 + 2, 2))
    os.makedirs(os.path.join(ROOT, 'tools', 'preview'), exist_ok=True)
    sheet.save(os.path.join(ROOT, 'tools', 'preview', 'quest_assets.png'))
    print('quest assets written')


if __name__ == '__main__':
    main()
