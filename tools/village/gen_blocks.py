#!/usr/bin/env python3
"""Village block / item assets: item box, quest board (3 variants), paper lanterns, mofu gear item icons.

Run from the repository root:  python3 tools/village/gen_blocks.py
Writes blockstates, block + item models and 16x16 textures under assets/huntcraft/, plus tools/preview/village_blocks.png.
"""
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
AS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
RNG = np.random.default_rng(77)


def C(h, a=255):
    h = h.lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], np.uint8)


def ramp(cols):
    return [C(c) for c in cols]


def tex(name, img):
    p = os.path.join(AS, 'textures', name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    Image.fromarray(img, 'RGBA').save(p)


def jdump(rel, obj):
    p = os.path.join(AS, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=1)


def noise(h, w, scale=1.0):
    return RNG.random((h, w)) * scale


# ====================================================================== item box
WOODR = ramp(['#2a1a0e', '#43291a', '#5c3a22', '#76502e', '#8f663c', '#a87c4c'])
IRON = ramp(['#1e2024', '#35383e', '#555a62', '#7a8088', '#a4aab2', '#ccd2d8'])
GOLDR = ramp(['#4a3004', '#7a560c', '#b08418', '#d8aa2a', '#f2cc54', '#fff0a0'])


def planks(h=16, w=16, ramp_=WOODR, seam_every=4, horizontal=True):
    img = np.zeros((h, w, 4), np.uint8)
    n = noise(h, w)
    for y in range(h):
        for x in range(w):
            i = 2 + int(n[y, x] * 2.2)
            band = (y if horizontal else x) % seam_every
            if band == seam_every - 1:
                i = 1
            if band == 0:
                i = min(5, i + 1)
            img[y, x] = ramp_[i]
    # grain streaks
    for k in range(10):
        y = RNG.integers(0, h)
        x0 = RNG.integers(0, w - 4)
        for x in range(x0, min(w, x0 + RNG.integers(3, 7))):
            if horizontal and (y % seam_every) not in (0, seam_every - 1):
                img[y, x] = ramp_[1]
    return img


def band(img, y0, y1, r=IRON, rivets=True):
    for y in range(y0, y1 + 1):
        for x in range(img.shape[1]):
            img[y, x] = r[3 if y == y0 else (1 if y == y1 else 2)]
    if rivets:
        for x in (2, img.shape[1] - 3):
            img[(y0 + y1) // 2, x] = r[5]


def item_box():
    side = planks()
    band(side, 4, 5)                           # lid seam band
    for y in range(16):                        # iron corner straps
        for x in (0, 15):
            side[y, x] = IRON[2 if y % 5 else 4]
    side[6:16, 0] = IRON[1]
    side[6:16, 15] = IRON[1]
    front = side.copy()
    # latch + guild emblem (gold diamond)
    for y in range(3, 9):
        for x in range(6, 10):
            front[y, x] = GOLDR[2 if (x in (6, 9) or y in (3, 8)) else 4]
    front[5, 7] = GOLDR[0]
    front[6, 7] = GOLDR[0]
    front[5, 8] = GOLDR[1]
    for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1), (0, 0)):
        front[12 + dy, 7 + dx] = GOLDR[3]
        front[12 + dy, 8 + dx] = GOLDR[3]
    front[12, 7] = GOLDR[5]
    top = planks(horizontal=False)
    band(top, 0, 1, rivets=False)
    band(top, 14, 15, rivets=False)
    for x in range(16):
        top[7, x] = IRON[2]
        top[8, x] = IRON[1]
    tex('block/item_box_side', side)
    tex('block/item_box_front', front)
    tex('block/item_box_top', top)
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "huntcraft:block/item_box_side", "side": "huntcraft:block/item_box_side",
                     "front": "huntcraft:block/item_box_front", "top": "huntcraft:block/item_box_top"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 10, 15],
             "faces": {"north": {"uv": [1, 6, 15, 16], "texture": "#front"}, "south": {"uv": [1, 6, 15, 16], "texture": "#side"},
                       "east": {"uv": [1, 6, 15, 16], "texture": "#side"}, "west": {"uv": [1, 6, 15, 16], "texture": "#side"},
                       "down": {"uv": [1, 1, 15, 15], "texture": "#top", "cullface": "down"}}},
            {"from": [0.5, 10, 0.5], "to": [15.5, 14, 15.5],
             "faces": {"north": {"uv": [0.5, 2, 15.5, 6], "texture": "#front"}, "south": {"uv": [0.5, 2, 15.5, 6], "texture": "#side"},
                       "east": {"uv": [0.5, 2, 15.5, 6], "texture": "#side"}, "west": {"uv": [0.5, 2, 15.5, 6], "texture": "#side"},
                       "up": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#top"}, "down": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#top"}}},
            {"from": [7, 7, 0.2], "to": [9, 11.5, 1],
             "faces": {"north": {"uv": [6, 3, 8, 7.5], "texture": "#front"}, "east": {"uv": [6, 3, 7, 7.5], "texture": "#front"},
                       "west": {"uv": [6, 3, 7, 7.5], "texture": "#front"}, "up": {"uv": [6, 3, 8, 4], "texture": "#front"},
                       "down": {"uv": [6, 3, 8, 4], "texture": "#front"}}}
        ]
    }
    jdump('models/block/item_box.json', model)
    jdump('blockstates/item_box.json', {"variants": {
        "facing=north": {"model": "huntcraft:block/item_box"}, "facing=east": {"model": "huntcraft:block/item_box", "y": 90},
        "facing=south": {"model": "huntcraft:block/item_box", "y": 180}, "facing=west": {"model": "huntcraft:block/item_box", "y": 270}}})
    jdump('models/item/item_box.json', {"parent": "huntcraft:block/item_box"})
    return side, front, top


# ====================================================================== quest board
PAPER = ramp(['#8a7a5c', '#bba98a', '#d8c9a8', '#ece0c4', '#f6efdc', '#fffaf0'])
CORK = ramp(['#3a2414', '#54341c', '#6e4626', '#885a32', '#a06e40', '#b88452'])


def quest_board(variant):
    img = np.zeros((16, 16, 4), np.uint8)
    n = noise(16, 16)
    for y in range(16):
        for x in range(16):
            img[y, x] = CORK[1 + int(n[y, x] * 2.5)]
    # dark oak frame
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img[y, x] = WOODR[1]
    rng = np.random.default_rng(100 + variant)
    sheets = [((2, 2, 7, 8), '#f6efdc'), ((8, 1, 13, 6), '#f4e4b0'), ((9, 8, 14, 14), '#f6efdc'), ((2, 10, 7, 14), '#e8f0f4')]
    if variant == 1:
        sheets = [((1, 3, 6, 9), '#f4e4b0'), ((7, 2, 13, 8), '#f6efdc'), ((4, 9, 10, 14), '#f6efdc'), ((11, 10, 14, 14), '#f8dcd8')]
    if variant == 2:
        sheets = [((2, 1, 8, 7), '#f6efdc'), ((9, 3, 14, 9), '#e8f0f4'), ((1, 9, 6, 14), '#f4e4b0'), ((7, 10, 12, 14), '#f6efdc')]
    for (x0, y0, x1, y1), col in sheets:
        base = C(col)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                c = base.astype(int) - (12 if (x == x1 or y == y1) else 0)
                img[y, x] = np.clip(c, 0, 255).astype(np.uint8)
        # text lines
        for y in range(y0 + 2, y1, 2):
            for x in range(x0 + 1, x1 - (0 if rng.random() < 0.5 else 1)):
                if rng.random() < 0.85:
                    img[y, x] = C('#5a4a3a')
        # monster seal (red stamp) + pin
        img[y0 + 1, x0 + 1] = C('#c02a22')
        img[y0, (x0 + x1) // 2] = C('#d8aa2a')
    return img


def quest_boards():
    for v in range(3):
        tex('block/village_quest_board_front%s' % ('' if v == 0 else '_%d' % (v + 1)), quest_board(v))
        jdump('models/block/village_quest_board%s.json' % ('' if v == 0 else '_%d' % (v + 1)), {
            "parent": "minecraft:block/orientable",
            "textures": {"front": "huntcraft:block/village_quest_board_front%s" % ('' if v == 0 else '_%d' % (v + 1)),
                         "side": "minecraft:block/dark_oak_planks", "top": "minecraft:block/dark_oak_planks"}})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        lst = []
        for v in range(3):
            m = {"model": "huntcraft:block/village_quest_board%s" % ('' if v == 0 else '_%d' % (v + 1))}
            if y:
                m["y"] = y
            lst.append(m)
        variants["facing=" + facing] = lst
    jdump('blockstates/village_quest_board.json', {"variants": variants})
    jdump('models/item/village_quest_board.json', {"parent": "huntcraft:block/village_quest_board"})


# ====================================================================== paper lanterns
def lantern(red):
    paper = ramp(['#4a0806', '#7a120c', '#b01e16', '#d63422', '#ee5a3c', '#ff8a64']) if red else \
        ramp(['#8a7a62', '#b8aa90', '#ddd2bc', '#efe7d6', '#f9f4ea', '#ffffff'])
    side = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            shade = 3 + (1 if 4 <= x <= 11 else 0) - (1 if x in (0, 1, 14, 15) else 0)
            side[y, x] = paper[shade]
    for y in range(1, 16, 3):                   # bamboo ribs
        for x in range(16):
            side[y, x] = paper[max(0, side_idx(paper, side[y, x]) - 2)]
    # crest: red lantern -> black paw print (the village's mofu mark), white lantern -> red circle crest
    mark = C('#1a0e0a') if red else C('#c02a22')
    if red:
        paw = ['.XX..XX.',
               '.XX..XX.',
               'X......X',
               'X..XX..X',
               '..XXXX..',
               '.XXXXXX.',
               '.XXXXXX.',
               '..X..X..']
        for yy, row in enumerate(paw):
            for xx, ch in enumerate(row):
                if ch == 'X':
                    side[4 + yy, 4 + xx] = mark
    if not red:
        for a in range(0, 360, 20):
            x = int(round(7.5 + 3.6 * math.cos(math.radians(a))))
            y = int(round(8 + 3.6 * math.sin(math.radians(a))))
            side[y, x] = mark
    cap = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            cap[y, x] = C('#1c1410') if (x + y) % 5 else C('#2e241c')
    for y in range(16):
        cap[y, 7] = C('#3a2e24')
    name = 'paper_lantern' if red else 'paper_lantern_white'
    tex('block/%s' % name, side)
    tex('block/%s_cap' % name, cap)
    body = lambda f, t, uv: {"from": f, "to": t, "faces": {d: {"uv": uv, "texture": "#side"} for d in ("north", "south", "east", "west")}}
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "huntcraft:block/%s" % name, "side": "huntcraft:block/%s" % name, "cap": "huntcraft:block/%s_cap" % name},
        "elements": [
            body([4, 2, 4], [12, 14, 12], [4, 2, 12, 14]),
            body([3, 4, 3], [13, 12, 13], [3, 4, 13, 12]),
            {"from": [5, 0, 5], "to": [11, 2, 11], "faces": {d: {"uv": [5, 0, 11, 2], "texture": "#cap"} for d in ("north", "south", "east", "west")}
             | {"down": {"uv": [5, 5, 11, 11], "texture": "#cap"}, "up": {"uv": [5, 5, 11, 11], "texture": "#cap"}}},
            {"from": [5, 14, 5], "to": [11, 16, 11], "faces": {d: {"uv": [5, 0, 11, 2], "texture": "#cap"} for d in ("north", "south", "east", "west")}
             | {"down": {"uv": [5, 5, 11, 11], "texture": "#cap"}, "up": {"uv": [5, 5, 11, 11], "texture": "#cap"}}},
            {"from": [3, 12, 3], "to": [13, 12, 13], "faces": {"up": {"uv": [3, 3, 13, 13], "texture": "#side"}, "down": {"uv": [3, 3, 13, 13], "texture": "#side"}}},
            {"from": [3, 4, 3], "to": [13, 4, 13], "faces": {"up": {"uv": [3, 3, 13, 13], "texture": "#side"}, "down": {"uv": [3, 3, 13, 13], "texture": "#side"}}},
        ]
    }
    jdump('models/block/%s.json' % name, model)
    jdump('blockstates/%s.json' % name, {"variants": {"": {"model": "huntcraft:block/%s" % name}}})
    jdump('models/item/%s.json' % name, {"parent": "huntcraft:block/%s" % name})
    return side


def side_idx(r, c):
    for i, rc in enumerate(r):
        if np.array_equal(rc[:3], c[:3]):
            return i
    return 3


# ====================================================================== mofu gear item icons
TIER_COLORS = {
    'bamboo': (['#2c3a14', '#4a5e22', '#6e8434', '#94a84a', '#b8c868', '#dce694'], ['#3a2a14', '#5a4220', '#7e5e30', '#a07c44', '#c09a5c', '#dcb87a']),
    'bone': (['#4a4033', '#7d6e57', '#a8987c', '#cbbd9f', '#e6dcc4', '#f7f1e2'], ['#2a1c12', '#46301e', '#64462c', '#84603c', '#a47c50', '#c09a6a']),
    'azurite': (['#0c1a36', '#163060', '#22488c', '#3866b4', '#5a8ad6', '#8ab4f0'], ['#2a2410', '#4e4218', '#7a6624', '#a68c36', '#ccb04e', '#ecd476']),
    'ignivald': (['#2a0806', '#561008', '#8a1d0c', '#bd3312', '#e05a20', '#f59a48'], ['#3a2600', '#6b4a00', '#a07400', '#d3a20f', '#f2c94a', '#fff0a8']),
    'frostgarm': (['#2c3f5c', '#4a6a8f', '#7499bd', '#a3c4e0', '#d0e6f5', '#f4fbff'], ['#1b4d6b', '#2e7aa3', '#4fa9d3', '#86d0ef', '#c2ecfb', '#f5fdff']),
    'voltrix': (['#0d1c2a', '#173149', '#24496b', '#336590', '#4a86b5', '#6fadd8'], ['#2c2508', '#5c4c0e', '#9a8016', '#d6b324', '#f2d74a', '#fff08a']),
    'gravadon': (['#1e1a17', '#36302a', '#524840', '#71655a', '#938677', '#b5a898'], ['#14200f', '#22361a', '#355228', '#4d703a', '#6b8f50', '#8fb06c']),
    'azragos': (['#0a0612', '#170d26', '#25153d', '#382057', '#4e2f73', '#6a4593'], ['#4a0c3a', '#86125e', '#c21f7d', '#e9409c', '#ff78c0', '#ffb3df']),
}
OUTLINE = C('#140c08')


def shade_icon(mask_rows, main, trim, legend):
    """mask_rows: 16 strings; legend char -> (palette, index) or None."""
    img = np.zeros((16, 16, 4), np.uint8)
    pal = {'m': ramp(main), 't': ramp(trim)}
    for y, row in enumerate(mask_rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            if ch == 'o':
                img[y, x] = OUTLINE
                continue
            p, i = legend[ch]
            img[y, x] = pal[p][i] if p in pal else C(p)
    return img


WEAPON = [
    "..........oo....",
    ".........oSo....",
    "........oSso....",
    ".......oSso.....",
    "......oSso......",
    ".....oSso.......",
    "...ooSso........",
    "..otTtto........",
    "..oTttTo........",
    "...ottoo........",
    "..oHHo..........",
    ".oHHo...........",
    "oPPo............",
    "oPPPo...........",
    ".oPo............",
    "..o.............",
]
HELM = [
    "................",
    "..oo........oo..",
    ".oMMo......oMMo.",
    ".oMmMo....oMmMo.",
    ".oMmmMooooMmmMo.",
    ".oMMMMMMMMMMMMo.",
    "oMMMMMMMMMMMMMMo",
    "oMMMMMTTTTMMMMMo",
    "oMmmmmTttTmmmmmo",
    "oTTTTTTTTTTTTTTo",
    "otttttttttttttto",
    "oMmo........oMmo",
    "oMmo........oMmo",
    ".oo..........oo.",
    "................",
    "................",
]
ARMOR = [
    "................",
    "...oooo..oooo...",
    "..oMMMMooMMMMo..",
    ".oMMMMMMMMMMMMo.",
    "oMMmMMMTTMMMmMMo",
    "oMMmMMTttTMMmMMo",
    "oMMmMMMTTMMMmMMo",
    ".oMmMMMMMMMMmMo.",
    "..oMMMMMMMMMMo..",
    "..oSSSSSSSSSSo..",
    "..osssGGsssss o.",
    "..oMMMooooMMMo..",
    "..oMmMo..oMmMo..",
    "..oMMo....oMMo..",
    "...oo......oo...",
    "................",
]


def gear_icons():
    legend = {'S': ('m', 4), 's': ('m', 2), 'T': ('t', 4), 't': ('t', 2), 'H': ('#5a3a1e', 0), 'P': ('#f0e0d0', 0),
              'M': ('m', 3), 'm': ('m', 1), 'G': ('t', 5)}
    legend['H'] = ('#5a3a1e', 0)
    out = []
    for tier, (main, trim) in TIER_COLORS.items():
        for slot, rows in (('weapon', WEAPON), ('helm', HELM), ('armor', ARMOR)):
            rows = [r.replace(' ', '.') for r in rows]
            if slot == 'armor':
                lg = dict(legend)
                lg['S'] = ('#4a2e18', 0)
                lg['s'] = ('#6a4628', 0)
            else:
                lg = legend
            img = shade_icon(rows, main, trim, lg)
            name = 'mofu_%s_%s' % (slot, tier)
            tex('item/' + name, img)
            jdump('models/item/%s.json' % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "huntcraft:item/" + name}})
            out.append(img)
    return out


def main():
    previews = list(item_box())
    quest_boards()
    previews.append(np.array(Image.open(os.path.join(AS, 'textures', 'block', 'village_quest_board_front.png'))))
    previews.append(lantern(True))
    previews.append(lantern(False))
    previews += gear_icons()
    cols = 12
    rows = (len(previews) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * 18 * 4, rows * 18 * 4), (60, 62, 68, 255))
    for i, im in enumerate(previews):
        tile = Image.fromarray(im, 'RGBA').resize((64, 64), Image.NEAREST)
        sheet.alpha_composite(tile, ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
    sheet.save(os.path.join(ROOT, 'tools', 'preview', 'village_blocks.png'))
    print('block + item assets written')


if __name__ == '__main__':
    main()
