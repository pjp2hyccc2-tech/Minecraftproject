#!/usr/bin/env python3
"""Kazami Village (風見の村) - the HuntCraft hunter village hub, generated as structure tiles.

Run from the repository root or tools/:  python3 tools/village/gen_village.py [--preview]
Outputs:
  src/main/resources/data/huntcraft/structures/village_<i>_<j>.nbt   (48x48 tiles)
  src/main/java/com/huntcraft/village/world/VillageLayout.java      (origin, spawn, NPC spots...)
  tools/preview/village_*.png                                        (--preview)
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from vworld import World, h2, fbm, DYE  # noqa: E402
from vparts import Kit  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
SX, SY, SZ = 176, 60, 176
G = 3          # grass layer (world y 63)
F = 4          # ground floor (first air layer, world y 64)
ORIGIN = (-88, 60, -88)   # world position of template (0,0,0)
TILE = 48
LAYOUT_VERSION = 1

W = World(SX, SY, SZ)
K = Kit(W)
S = W.set

# village interior (rounded rectangle); north side is closed by cliffs, the rest by a palisade
VX0, VX1, VZ0, VZ1, VR = 32, 144, 26, 148, 18
ROAD_X = 88


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / float(e1 - e0)))
    return t * t * (3 - 2 * t)


def sd_village(x, z):
    cx = min(max(x, VX0 + VR), VX1 - VR)
    cz = min(max(z, VZ0 + VR), VZ1 - VR)
    return math.hypot(x - cx, z - cz) - VR


def in_village(x, z, margin=0):
    return sd_village(x, z) < -margin


# ====================================================================== terrain
WF_X = (127, 129)      # waterfall columns
WF_TOP = G + 20        # waterfall lip (y)


def mountain_h(x, z):
    d = sd_village(x, z)
    # waterfall cove at the foot of the north cliff
    if WF_X[0] - 3 <= x <= WF_X[1] + 3 and 23 < z < 27:
        return 0
    if d <= 0:
        return 0
    north = 1 - smooth(40, 115, z)
    south = smooth(115, 165, z)
    hmax = 13 + 17 * north - 5 * south
    rise = 8.0 + 5.0 * (1 - north)
    e = min(x, z, SX - 1 - x, SZ - 1 - z)
    fall = smooth(-3, 22, e)
    n = fbm(x / 24.0, z / 24.0, 7)
    h = hmax * smooth(0, rise, d) * fall * (0.55 + 0.7 * n)
    # natural terraces (ledges every ~3 blocks)
    step = 3.0
    base = math.floor(h / step) * step
    h = base + (h - base) * 0.3 + 2.2 * (fbm(x / 6.0, z / 6.0, 3) - 0.5) * smooth(0, 5, d)
    if z > VZ1 - 6:   # road gap through the southern hills
        g = 1 - smooth(5, 17, abs(x - ROAD_X))
        h *= 1 - g * smooth(VZ1 - 6, VZ1 + 3, z)
    # plateau feeding the waterfall
    if WF_X[0] - 5 <= x <= WF_X[1] + 5 and 8 <= z <= 23:
        h = max(h, WF_TOP - G + 1 + (1 if abs(x - 128) > 3 else 0))
    return max(0, int(round(h)))


def rock(x, y, z):
    v = h2(x * 3 + y, z * 5 - y, 77)
    band = fbm(x / 9.0, (y * 3 + z) / 9.0, 5)
    if band > 0.66:
        return 'andesite'
    if band < 0.30:
        return 'tuff' if v > 0.5 else 'stone'
    if y < G + 5 and v > 0.8:
        return 'mossy_cobblestone'
    if v > 0.93:
        return 'cobblestone'
    return 'stone'


MH = {}


def build_mountains():
    for x in range(SX):
        for z in range(SZ):
            h = mountain_h(x, z)
            if h > 0:
                MH[(x, z)] = h
    for (x, z), h in MH.items():
        top = G + h
        nbs = [MH.get((x + 1, z), 0), MH.get((x - 1, z), 0), MH.get((x, z + 1), 0), MH.get((x, z - 1), 0)]
        lo = max(F, G + min(nbs) - 1)
        slope = max(abs(h - n) for n in nbs)
        for y in range(lo, top + 1):
            depth = top - y
            if depth == 0 and slope < 6:
                m = 'grass_block' if h2(x, z, 12) > 0.08 else ('moss_block' if h2(x, z, 13) > 0.5 else 'coarse_dirt')
            elif slope >= 4 and depth > 0:
                m = rock(x, y, z)
            elif depth == 0:
                m = rock(x, y, z)
            elif depth < 2 and slope < 2:
                m = 'dirt'
            elif depth < 2:
                m = 'dirt' if h2(x * 3, z + y, 19) > 0.6 else rock(x, y, z)
            else:
                m = rock(x, y, z)
            S(x, y, z, m)

    # vegetation on the slopes
    for (x, z), h in MH.items():
        top = G + h
        if W.get(x, top, z) != 'minecraft:grass_block' or not W.is_empty(x, top + 1, z):
            continue
        r = h2(x, z, 21)
        if h < 27 and r > 0.94 and all(W.is_empty(x + dx, top + 2, z + dz) for dx in (-2, 0, 2) for dz in (-2, 0, 2)):
            K.spruce_tree(x, top + 1, z, 6 + int(h2(x, z, 22) * 7), seed=x * 7 + z)
        elif h < 14 and r > 0.925 and sd_village(x, z) < 10:
            K.bush(x, top + 1, z, 'azalea_leaves' if h2(x, z, 24) > 0.4 else 'flowering_azalea_leaves', 1)
        elif r > 0.80:
            K.flower(x, top + 1, z, 'fern' if r > 0.9 else 'grass')
        elif r > 0.78 and h < 20:
            K.flower(x, top + 1, z, ('cornflower', 'oxeye_daisy', 'allium')[int(h2(x, z, 23) * 3) % 3])


def build_waterfall():
    x0, x1 = WF_X
    # channel on the plateau
    for x in range(x0, x1 + 1):
        for z in range(12, 24):
            S(x, WF_TOP, z, 'water', {'level': '0'})
            S(x, WF_TOP - 1, z, 'stone')
            for y in range(WF_TOP + 1, WF_TOP + 4):
                W.clear(x, y, z)
        # lip + falling column
        S(x, WF_TOP, 24, 'water', {'level': '1'})
        for y in range(F, WF_TOP):
            S(x, y, 24, 'water', {'level': '8'})
    for z in range(10, 24):
        for x in (x0 - 1, x1 + 1):
            for y in range(WF_TOP - 1, WF_TOP + 1):
                S(x, y, z, 'mossy_cobblestone' if h2(x, z + y, 3) > 0.5 else 'stone')
    for x in range(x0 - 1, x1 + 2):
        S(x, WF_TOP - 1, 11, 'stone')
        S(x, WF_TOP, 11, 'mossy_cobblestone')


POND = set()


def build_pond():
    for x in range(108, 146):
        for z in range(20, 54):
            dx, dz = (x - 129) / 13.0, (z - 35) / 10.5
            r = math.hypot(dx, dz) + 0.35 * (fbm(x / 6.0, z / 6.0, 11) - 0.5)
            if r < 1.0 and MH.get((x, z), 0) == 0:
                depth = 3 if r < 0.55 else 2
                for y in range(G - depth + 1, G + 1):
                    S(x, y, z, 'water', {'level': '0'})
                S(x, G - depth, z, 'clay' if h2(x, z, 2) > 0.6 else ('sand' if h2(x, z, 5) > 0.5 else 'gravel'))
                POND.add((x, z))
            elif r < 1.18 and MH.get((x, z), 0) == 0:
                S(x, G, z, 'sand' if h2(x, z, 6) > 0.45 else 'gravel')
                if h2(x, z, 8) > 0.86:
                    S(x, F, z, 'mossy_cobblestone' if h2(x, z, 9) > 0.5 else 'cobblestone')
                elif h2(x, z, 8) > 0.74:
                    K.flower(x, F, z, 'fern')
    for (x, z) in list(POND):
        if h2(x, z, 31) > 0.93:
            S(x, F, z, 'lily_pad')


STREAM = [(121, 47), (120, 53), (119, 59), (115, 63.5), (108, 64.5), (104.5, 67), (104.5, 97), (101, 103), (98.5, 108),
          (98.5, 175)]
STREAM_CELLS = set()


def build_stream():
    pts = []
    for (ax, az), (bx, bz) in zip(STREAM, STREAM[1:]):
        n = int(max(abs(bx - ax), abs(bz - az)) * 3) + 1
        for i in range(n):
            t = i / n
            pts.append((ax + (bx - ax) * t, az + (bz - az) * t))
    for (px, pz) in pts:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = int(round(px + dx * 0.6)), int(round(pz + dz * 0.6))
                if (x, z) in STREAM_CELLS or (x, z) in POND or MH.get((x, z), 0) > 0 and z < 150:
                    continue
                STREAM_CELLS.add((x, z))
    for (x, z) in STREAM_CELLS:
        S(x, G, z, 'water', {'level': '0'})
        S(x, G - 1, z, 'water', {'level': '0'})
        S(x, G - 2, z, 'gravel' if h2(x, z, 1) > 0.4 else 'sand')
        for y in range(F, F + 6):
            if W.get(x, y, z) is not None and 'water' not in W.get(x, y, z):
                W.clear(x, y, z)
    # banks
    for (x, z) in list(STREAM_CELLS):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            bx, bz = x + dx, z + dz
            if (bx, bz) in STREAM_CELLS or (bx, bz) in POND:
                continue
            if W.get(bx, G, bz) is None and h2(bx, bz, 44) > 0.55:
                S(bx, G, bz, 'mossy_cobblestone' if h2(bx, bz, 45) > 0.6 else 'cobblestone')
            elif W.get(bx, G, bz) is None and h2(bx, bz, 46) > 0.9 and W.is_empty(bx, F, bz):
                K.flower(bx, F, bz, 'fern')


def bridge(x0, x1, z0, z1, axis, y=G + 1, rail='red'):
    """Small arched wooden bridge over the stream. axis = walking direction."""
    if axis == 'x':
        length = x1 - x0
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, length)
            lift = 1 if 0.25 <= t <= 0.75 else 0
            for z in range(z0, z1 + 1):
                if lift:
                    S(x, y, z, 'spruce_planks')
                    S(x, y - 1, z, 'spruce_slab', {'type': 'top', 'waterlogged': 'false'}) if W.is_empty(x, y - 1, z) else None
                else:
                    S(x, y, z, 'spruce_slab', {'type': 'bottom', 'waterlogged': 'false'})
            for z in (z0 - 1, z1 + 1):
                yy = y + lift + (1 if lift else 0)
                S(x, y + lift, z, 'red_concrete' if rail == 'red' else 'stripped_spruce_log', {} if rail == 'red' else {'axis': 'x'})
                S(x, y + lift + 1, z, 'dark_oak_fence' if rail != 'red' else 'crimson_fence')
    else:
        length = z1 - z0
        for z in range(z0, z1 + 1):
            t = (z - z0) / max(1, length)
            lift = 1 if 0.25 <= t <= 0.75 else 0
            for x in range(x0, x1 + 1):
                if lift:
                    S(x, y, z, 'spruce_planks')
                else:
                    S(x, y, z, 'spruce_slab', {'type': 'bottom', 'waterlogged': 'false'})
            for x in (x0 - 1, x1 + 1):
                S(x, y + lift, z, 'red_concrete' if rail == 'red' else 'stripped_spruce_log', {} if rail == 'red' else {'axis': 'z'})
                S(x, y + lift + 1, z, 'dark_oak_fence' if rail != 'red' else 'crimson_fence')


# ====================================================================== paths
PAVED = set()


def path_line(points, width=3, style='gravel', seed=0):
    pts = []
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        n = int(max(abs(bx - ax), abs(bz - az)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            pts.append((ax + (bx - ax) * t, az + (bz - az) * t))
    r = width / 2.0
    for (px, pz) in pts:
        for x in range(int(px - r - 1), int(px + r + 2)):
            for z in range(int(pz - r - 1), int(pz + r + 2)):
                if (x - px) ** 2 + (z - pz) ** 2 > r * r:
                    continue
                if (x, z) in STREAM_CELLS or (x, z) in POND or MH.get((x, z), 0) > 0 or (x, z) in PAVED:
                    continue
                if W.get(x, G, z) not in (None, 'minecraft:grass_block'):
                    continue
                PAVED.add((x, z))
                v = h2(x, z, seed + 3)
                if style == 'gravel':
                    m = 'dirt_path' if v < 0.45 else ('gravel' if v < 0.8 else ('coarse_dirt' if v < 0.93 else 'cobblestone'))
                elif style == 'stone':
                    m = 'stone_bricks' if v < 0.55 else ('cracked_stone_bricks' if v < 0.7 else ('mossy_stone_bricks' if v < 0.85 else 'polished_andesite'))
                elif style == 'dirt':
                    m = 'dirt_path' if v < 0.7 else 'coarse_dirt'
                else:
                    m = style
                S(x, G, z, m)


def stepping_stones(points, seed=0):
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        n = int(max(abs(bx - ax), abs(bz - az)) / 1.6) + 1
        for i in range(n + 1):
            t = i / n
            x, z = int(round(ax + (bx - ax) * t)), int(round(az + (bz - az) * t))
            S(x, G, z, 'polished_andesite' if h2(x, z, seed) > 0.4 else 'smooth_stone')
            PAVED.add((x, z))


# ====================================================================== palisade & gate
def palisade():
    cells = set()
    for x in range(SX):
        for z in range(SZ):
            d = sd_village(x, z)
            if -0.5 <= d < 0.7 and MH.get((x, z), 0) < 4 and z > 30:
                cells.add((x, z))
    gate = set((x, z) for (x, z) in cells if abs(x - ROAD_X) <= 6 and z > VZ1 - 4)
    stream_gap = set((x, z) for (x, z) in cells if (x, z) in STREAM_CELLS or any((x + dx, z + dz) in STREAM_CELLS for dx in (-1, 0, 1) for dz in (-1, 0, 1)))
    for (x, z) in cells:
        if (x, z) in gate:
            continue
        if (x, z) in stream_gap:
            # water gate: bars above the stream
            for y in range(F + 2, F + 6):
                S(x, y, z, 'spruce_log', {'axis': 'y'}) if (x + z) % 3 == 0 else S(x, y, z, 'iron_bars')
            S(x, F + 6, z, 'dark_oak_planks')
            continue
        S(x, G, z, 'cobblestone')
        S(x, F, z, 'mossy_cobblestone' if h2(x, z, 13) > 0.6 else 'cobblestone')
        hgt = 5 + (1 if h2(x, z, 14) > 0.5 else 0)
        for y in range(F + 1, F + 1 + hgt):
            S(x, y, z, 'spruce_log', {'axis': 'y'})
        S(x, F + 1 + hgt, z, 'spruce_fence')
    # inner walkway posts / buttresses every few blocks
    for (x, z) in cells:
        if (x, z) in gate or (x, z) in stream_gap:
            continue
        if (x * 7 + z * 13) % 11 == 0:
            # find inward direction
            best = None
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if sd_village(x + dx, z + dz) < sd_village(x, z) - 0.5:
                    best = (dx, dz)
            if best and W.is_empty(x + best[0], F, z + best[1]):
                dx, dz = best
                face = {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}[best]
                K.st(x + dx, F, z + dz, 'cobblestone', face)
                if h2(x, z, 15) > 0.6:
                    K.lantern(x + dx, F + 1, z + dz) if W.is_empty(x + dx, F + 1, z + dz) else None
    return cells


def watchtower(cx, cz, y=F):
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            S(x, G, z, 'stone_bricks')
            S(x, y, z, 'stone_bricks')
            corner = abs(x - cx) == 2 and abs(z - cz) == 2
            if corner:
                for yy in range(y + 1, y + 9):
                    K.log(x, yy, z, 'spruce_log')
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            S(x, y + 6, z, 'spruce_planks')
            edge = abs(x - cx) == 2 or abs(z - cz) == 2
            if edge and not (abs(x - cx) == 2 and abs(z - cz) == 2):
                K.fence(x, y + 7, z)
    for yy in range(y + 1, y + 6):
        S(cx, yy, cz + 2, 'ladder', {'facing': 'north', 'waterlogged': 'false'}) if False else None
    # ladder inside on the inner post column
    for yy in range(y + 1, y + 7):
        S(cx, yy, cz, 'spruce_log', {'axis': 'y'}) if yy < y + 6 else None
    K.roof(cx - 3, cz - 3, cx + 3, cz + 3, y + 9, 'spruce', 'x', hip=3, cap='slab')
    K.lantern(cx, y + 8, cz, hanging=True)
    S(cx, y + 9, cz, 'spruce_planks')


def main_gate():
    z = VZ1   # palisade line at the gate
    gx0, gx1 = ROAD_X - 6, ROAD_X + 6
    # massive posts
    for px in (ROAD_X - 5, ROAD_X - 4, ROAD_X + 4, ROAD_X + 5):
        for pz in (z - 1, z):
            S(px, G, pz, 'stone_bricks')
            S(px, F, pz, 'stone_bricks')
            for y in range(F + 1, F + 10):
                K.log(px, y, pz, 'dark_oak_log')
    # crossbeams (nuki + kasagi)
    for x in range(gx0 - 1, gx1 + 2):
        for pz in (z - 1, z):
            K.log(x, F + 7, pz, 'stripped_dark_oak_log', 'x')
            S(x, F + 10, pz, 'dark_oak_planks')
    for x in range(ROAD_X - 3, ROAD_X + 4):
        for pz in (z - 1, z):
            S(x, F + 8, pz, 'red_concrete')
            S(x, F + 9, pz, 'red_concrete')
    # plaque
    K.wall_sign(ROAD_X, F + 8, z + 1, 'south', ['', '風見の村', 'Kazami Village', ''], mat='dark_oak', glow=True, color='yellow')
    K.wall_sign(ROAD_X, F + 8, z - 2, 'north', ['', '風見の村', '狩人の里', ''], mat='dark_oak', glow=True, color='yellow')
    K.roof(gx0 - 2, z - 3, gx1 + 2, z + 2, F + 11, 'deepslate_tile', 'x', hip=1, cap='slab', soffit='dark_oak')
    # open gate doors (swung inward against the posts)
    for (x, f) in ((ROAD_X - 3, 'east'), (ROAD_X + 3, 'west')):
        for y in range(F, F + 6):
            S(x, y, z - 3, 'spruce_planks')
            S(x, y, z - 4, 'spruce_planks') if y < F + 5 else None
    # banners on the gate
    for x in (ROAD_X - 5, ROAD_X + 5):
        K.wall_banner(x, F + 6, z + 1, 'south', 'red', [('mc', DYE['white']), ('flo', DYE['red']), ('bo', DYE['black'])])
        K.wall_banner(x, F + 6, z - 2, 'north', 'red', [('mc', DYE['white']), ('flo', DYE['red']), ('bo', DYE['black'])])
    for x in (ROAD_X - 4, ROAD_X + 4):
        K.lantern(x, F + 6, z + 1, hanging=True) if False else None
    K.paper_lantern(ROAD_X - 3, F + 6, z + 1)
    K.paper_lantern(ROAD_X + 3, F + 6, z + 1)
    K.chain(ROAD_X - 3, F + 7, z + 1)
    K.chain(ROAD_X + 3, F + 7, z + 1)
    S(ROAD_X - 3, F + 8, z + 1, 'dark_oak_planks') if W.is_empty(ROAD_X - 3, F + 8, z + 1) else None
    S(ROAD_X + 3, F + 8, z + 1, 'dark_oak_planks') if W.is_empty(ROAD_X + 3, F + 8, z + 1) else None


# ====================================================================== terrace + gathering hall
PX0, PX1, PZ0, PZ1, PH = 58, 118, 24, 62, 3   # raised terrace (top block y = G + PH)
PY = G + PH + 1                                # walking level on the terrace


def terrace():
    for x in range(PX0, PX1 + 1):
        for z in range(PZ0, PZ1 + 1):
            if MH.get((x, z), 0) > PH + 1 or (x, z) in POND:
                continue
            edge = x in (PX0, PX1) or z == PZ1
            for y in range(F, G + PH + 1):
                if edge:
                    v = h2(x, z + y * 17, 30)
                    S(x, y, z, 'mossy_stone_bricks' if v > 0.7 else ('cracked_stone_bricks' if v > 0.55 else 'stone_bricks'))
                elif y == G + PH:
                    S(x, y, z, 'grass_block')
                else:
                    S(x, y, z, 'dirt')
            # remove any mountain clutter above
    # railing along the front & sides
    for x in range(PX0, PX1 + 1):
        if not (ROAD_X - 6 <= x <= ROAD_X + 6) and not (110 <= x <= 113) and MH.get((x, PZ1), 0) == 0:
            K.wallb(x, PY, PZ1, 'stone_brick')
    for z in range(PZ0, PZ1 + 1):
        for x in (PX0, PX1):
            if MH.get((x, z), 0) <= PH and not (x == PX1 and 40 <= z <= 44) and not (x == PX0 and 44 <= z <= 46):
                K.wallb(x, PY, z, 'stone_brick')
    for x in range(PX0, PX1 + 1, 6):
        if W.get(x, PY, PZ1) == 'minecraft:stone_brick_wall':
            K.lantern(x, PY + 1, PZ1)
    # grand stairs to the plaza
    for i in range(PH):
        z = PZ1 + 1 + i
        y = G + PH - i
        for x in range(ROAD_X - 6, ROAD_X + 7):
            K.st(x, y, z, 'stone_brick', 'north')
            for yy in range(F, y):
                S(x, yy, z, 'stone_bricks')
        for x in (ROAD_X - 7, ROAD_X + 7):
            for yy in range(F, y + 1):
                S(x, yy, z, 'stone_bricks')
            K.wallb(x, y + 1, z, 'stone_brick')
    for x in (ROAD_X - 7, ROAD_X + 7):
        K.toro(x, PY, PZ1 - 1) if False else None
    # side stairs (east, toward the pond) and west steps
    for i in range(PH):
        z = PZ1 + 1 + i
        y = G + PH - i
        for x in range(110, 114):
            K.st(x, y, z, 'cobblestone', 'north')
            for yy in range(F, y):
                S(x, yy, z, 'cobblestone')
    for i in range(PH):
        x = PX0 - 1 - i
        y = G + PH - i
        for z in range(44, 47):
            K.st(x, y, z, 'cobblestone', 'east')
            for yy in range(F, y):
                S(x, yy, z, 'cobblestone')
    for i in range(PH):
        x = PX1 + 1 + i
        y = G + PH - i
        for z in range(40, 45):
            if (x, z) in POND:
                continue
            K.st(x, y, z, 'cobblestone', 'west')
            for yy in range(F, y):
                S(x, yy, z, 'cobblestone')


HALL = (70, 34, 106, 54)     # outer walls x0, z0, x1, z1


def gathering_hall():
    x0, z0, x1, z1 = HALL
    y0 = PY
    wall_top = y0 + 6
    # plinth + floor
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            S(x, y0 - 1, z, 'stone_bricks' if (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)) else 'spruce_planks')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x + z) % 2 == 0 and x0 + 3 < x < x1 - 3 and z0 + 8 < z < z1 - 2:
                S(x, y0 - 1, z, 'dark_oak_planks')
    # front engawa (wooden deck) and steps
    for x in range(x0 - 2, x1 + 3):
        for z in range(z1 + 1, z1 + 4):
            S(x, y0 - 1, z, 'spruce_planks' if z < z1 + 3 else 'spruce_slab', None if z < z1 + 3 else {'type': 'top', 'waterlogged': 'false'})
    K.timber_walls(x0, z0, x1, z1, y0, wall_top, fill='white_concrete', post='stripped_dark_oak_log', base='dark_oak_planks',
                   beam='stripped_dark_oak_log', step=4)
    # big entrance
    ex0, ex1 = ROAD_X - 3, ROAD_X + 3
    K.opening(ex0, y0, z1, ex1, y0 + 3, z1)
    for x in range(ex0, ex1 + 1):
        K.wall_banner(x, y0 + 3, z1 + 1, 'south', 'black' if x in (ex0, ex1) else 'red', [('ts', DYE['black'])] if x not in (ex0, ex1) else [])
    K.wall_sign(ROAD_X, y0 + 5, z1 + 1, 'south', ['', '集会所', 'Gathering Hall', ''], mat='dark_oak', glow=True, color='yellow')
    # side entrances
    K.opening(x0, y0, 44, x0, y0 + 2, 45)
    K.opening(x1, y0, 42, x1, y0 + 2, 43)
    K.wall_banner(x0 - 1, y0 + 2, 44, 'west', 'blue')
    K.wall_banner(x0 - 1, y0 + 2, 45, 'west', 'blue')
    K.wall_banner(x1 + 1, y0 + 2, 42, 'east', 'blue')
    K.wall_banner(x1 + 1, y0 + 2, 43, 'east', 'blue')
    # windows (lattice) on all sides
    for x in range(x0 + 2, x1 - 1, 4):
        if not (ex0 - 1 <= x <= ex1 + 1):
            K.window(x, y0 + 2, z1, x + 1, y0 + 4, z1, 'dark_oak_fence')
        K.window(x, y0 + 3, z0, x + 1, y0 + 4, z0, 'dark_oak_fence')
    for z in range(z0 + 2, z1 - 1, 4):
        if not (41 <= z <= 46):
            K.window(x0, y0 + 2, z, x0, y0 + 4, z + 1, 'dark_oak_fence')
            K.window(x1, y0 + 2, z, x1, y0 + 4, z + 1, 'dark_oak_fence')
    # ceiling + interior pillars
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            S(x, wall_top + 1, z, 'dark_oak_planks' if (x - x0) % 4 else 'stripped_dark_oak_log', None if (x - x0) % 4 else {'axis': 'z'})
    for x in range(x0 + 8, x1 - 7, 8):
        for z in (z0 + 6, z1 - 5):
            for y in range(y0, wall_top + 1):
                K.log(x, y, z, 'stripped_dark_oak_log')
    # lower roof skirt (mokoshi) all around, then the upper storey
    ux0, uz0, ux1, uz1 = 78, 39, 98, 49
    K.roof(x0 - 2, z0 - 2, x1 + 2, z1 + 2, wall_top + 1, 'deepslate_tile', 'x', hip=99, cap=None, soffit='dark_oak')
    # clear the skirt inside the upper storey footprint and build the upper walls
    for x in range(ux0, ux1 + 1):
        for z in range(uz0, uz1 + 1):
            for y in range(wall_top + 2, wall_top + 20):
                W.clear(x, y, z)
    uy0 = wall_top + 2
    uy1 = wall_top + 9
    K.timber_walls(ux0, uz0, ux1, uz1, uy0, uy1, fill='white_concrete', post='stripped_dark_oak_log', base='dark_oak_planks',
                   beam='stripped_dark_oak_log', step=4)
    for x in range(ux0 + 2, ux1 - 1, 4):
        K.window(x, uy1 - 3, uz1, x + 1, uy1 - 2, uz1, 'dark_oak_fence')
        K.window(x, uy1 - 3, uz0, x + 1, uy1 - 2, uz0, 'dark_oak_fence')
    for z in range(uz0 + 2, uz1 - 1, 4):
        K.window(ux0, uy1 - 3, z, ux0, uy1 - 2, z + 1, 'dark_oak_fence')
        K.window(ux1, uy1 - 3, z, ux1, uy1 - 2, z + 1, 'dark_oak_fence')
    for x in range(ux0 + 1, ux1):
        for z in range(uz0 + 1, uz1):
            S(x, uy0 - 1, z, 'dark_oak_planks')
    top = K.roof(ux0 - 3, uz0 - 3, ux1 + 3, uz1 + 3, uy1 + 1, 'deepslate_tile', 'x', hip=3, cap='slab',
                 gable='white_concrete', gable_inset=1, soffit='dark_oak')
    K.seal(ux0, uz0, ux1, uz1, uy1 + 1, 'dark_oak_planks')
    # ridge ornaments (golden fish-like finials)
    for x in (ux0, ux1):
        S(x, top, (uz0 + uz1) // 2, 'gold_block')
        S(x, top + 1, (uz0 + uz1) // 2, 'deepslate_tile_wall')
    # gable crest
    for x in (ux0, ux1):
        K.wall_banner(x - 1 if x == ux0 else x + 1, uy1 + 5, (uz0 + uz1) // 2, 'west' if x == ux0 else 'east', 'red',
                      [('mc', DYE['yellow']), ('cr', DYE['red']), ('bo', DYE['yellow'])])
    # entrance porch (karahafu-like gable facing south)
    for px in (ex0 - 1, ex1 + 1):
        for y in range(y0, y0 + 5):
            S(px, y, z1 + 3, 'red_concrete')
        S(px, y0 - 1, z1 + 3, 'stone_bricks')
    for x in range(ex0 - 1, ex1 + 2):
        K.log(x, y0 + 5, z1 + 3, 'stripped_dark_oak_log', 'x')
    K.roof(ex0 - 3, z1 + 1, ex1 + 3, z1 + 5, y0 + 6, 'deepslate_tile', 'z', hip=0, cap='slab', gable='dark_oak_planks',
           gable_inset=0, soffit='dark_oak')
    K.paper_lantern(ex0 - 1, y0 + 4, z1 + 4)
    K.paper_lantern(ex1 + 1, y0 + 4, z1 + 4)
    K.chain(ex0 - 1, y0 + 5, z1 + 4) if W.is_empty(ex0 - 1, y0 + 5, z1 + 4) else None
    K.chain(ex1 + 1, y0 + 5, z1 + 4) if W.is_empty(ex1 + 1, y0 + 5, z1 + 4) else None
    hall_interior(x0, z0, x1, z1, y0, wall_top)


def hall_interior(x0, z0, x1, z1, y0, wall_top):
    cx = ROAD_X
    # reception counter (receptionist stands behind it at z0+3)
    cz = z0 + 5
    for x in range(cx - 6, cx + 7):
        S(x, y0, cz, 'dark_oak_planks' if abs(x - cx) != 6 else 'stripped_dark_oak_log', None if abs(x - cx) != 6 else {'axis': 'y'})
        K.slab(x, y0 + 1, cz, 'spruce', 'bottom') if x in (cx - 5, cx + 5) else None
    for x in (cx - 6, cx + 6):
        for z in range(z0 + 1, cz):
            S(x, y0, z, 'dark_oak_planks')
    K.candle(cx - 5, y0 + 1, cz, 3, 'white') if False else None
    S(cx - 4, y0 + 1, cz, 'lectern', {'facing': 'south', 'has_book': 'true', 'powered': 'false'}) if False else None
    S(cx + 3, y0 + 1, cz, 'flower_pot') if False else None
    K.pot(cx + 4, y0 + 1, cz, 'red_tulip')
    K.pot(cx - 4, y0 + 1, cz, 'azure_bluet')
    K.candle(cx - 2, y0 + 1, cz, 2, 'yellow')
    K.candle(cx + 2, y0 + 1, cz, 2, 'yellow')
    W.mark('receptionist', cx + 0.5, y0, z0 + 3.5, 0.0)
    # quest boards: behind the receptionist (wall) and accessible ones on both side walls
    for x in range(cx - 2, cx + 3):
        for y in (y0 + 2, y0 + 3):
            S(x, y, z0, 'huntcraft:village_quest_board', {'facing': 'south'})
    for z in (z0 + 8, z0 + 9, z0 + 10):
        for y in (y0 + 1, y0 + 2):
            S(x0, y, z, 'huntcraft:village_quest_board', {'facing': 'east'})
            S(x1, y, z, 'huntcraft:village_quest_board', {'facing': 'west'})
    # guild banners behind the counter
    for x in (cx - 4, cx + 4):
        K.wall_banner(x, y0 + 4, z0 + 1, 'south', 'red', [('mc', DYE['yellow']), ('cr', DYE['red']), ('bo', DYE['yellow'])])
    K.wall_banner(cx, y0 + 5, z0 + 1, 'south', 'black', [('mc', DYE['white']), ('flo', DYE['black'])]) if False else None
    # shelves & barrels behind counter
    for x in (cx - 5, cx - 3, cx + 3, cx + 5):
        S(x, y0, z0 + 1, 'bookshelf')
        S(x, y0 + 1, z0 + 1, 'bookshelf') if x in (cx - 5, cx + 5) else K.barrel(x, y0 + 1, z0 + 1)
    # tables with stools
    for (tx, tz) in ((x0 + 5, z0 + 11), (x0 + 5, z1 - 4), (x1 - 5, z0 + 11), (x1 - 5, z1 - 4), (cx - 9, z1 - 6), (cx + 9, z1 - 6)):
        K.table(tx, y0, tz)
        K.stool(tx - 1, y0, tz, 'east')
        K.stool(tx + 1, y0, tz, 'west')
        K.stool(tx, y0, tz - 1, 'south')
        K.stool(tx, y0, tz + 1, 'north')
    # long bar counter on the east side with barrels (drinks)
    for z in range(z0 + 13, z1 - 1):
        S(x1 - 3, y0, z, 'spruce_planks')
        K.slab(x1 - 3, y0 + 1, z, 'dark_oak', 'bottom') if False else None
        K.barrel(x1 - 1, y0, z, 'west')
        K.barrel(x1 - 1, y0 + 1, z, 'west') if z % 2 == 0 else None
    # irori hearth in the middle
    hx, hz = cx, z0 + 12
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            S(hx + dx, y0 - 1, hz + dz, 'stone_bricks')
    K.campfire(hx, y0 - 1, hz) if False else None
    S(hx, y0 - 1, hz, 'campfire', {'facing': 'north', 'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false'})
    for dx, dz, f in ((-2, 0, 'east'), (2, 0, 'west'), (0, -2, 'south'), (0, 2, 'north')):
        for k in (-1, 0, 1):
            px, pz = (hx + dx, hz + k) if dx else (hx + k, hz + dz)
            K.slab(px, y0, pz, 'spruce', 'bottom')
    K.chain(hx, wall_top, hz)
    K.chain(hx, wall_top - 1, hz)
    K.lantern(hx, wall_top - 2, hz, hanging=True)
    # hanging lanterns
    for x in range(x0 + 4, x1 - 3, 6):
        for z in (z0 + 8, z1 - 3):
            K.chain(x, wall_top, z)
            K.lantern(x, wall_top - 1, z, hanging=True)
    # red carpet from the entrance to the counter
    for z in range(cz + 1, z1):
        for x in (cx - 1, cx, cx + 1):
            if W.is_empty(x, y0, z):
                S(x, y0, z, 'red_carpet')
    # plants
    for (px, pz) in ((x0 + 1, z1 - 1), (x1 - 1, z1 - 1), (x0 + 1, z0 + 1), (x1 - 1, z0 + 1)):
        K.pot(px, y0, pz, 'bamboo')


# ====================================================================== plaza
PLAZA = (ROAD_X, 81, 13)


def plaza():
    cx, cz, r = PLAZA
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if d > r + 0.5 or (x, z) in STREAM_CELLS:
                continue
            ring = abs(d - r) < 0.9 or abs(d - 6) < 0.6
            v = h2(x, z, 50)
            if ring:
                m = 'polished_andesite'
            elif d < 5.5:
                m = 'stone_bricks' if v > 0.25 else 'mossy_stone_bricks'
            else:
                m = ('stone_bricks', 'cracked_stone_bricks', 'andesite', 'polished_andesite', 'cobblestone')[int(v * 5) % 5]
            S(x, G, z, m)
            PAVED.add((x, z))
    # central great cherry tree on a stone planter
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = math.hypot(x - cx, z - cz)
            if d <= 3.2:
                S(x, G, z, 'grass_block')
                if 2.4 <= d:
                    S(x, F, z, 'stone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'}) if W.is_empty(x, F, z) else None
    big_sakura(cx, F, cz)
    for (dx, dz) in ((-10, -6), (10, -6), (-10, 6), (10, 6)):
        K.big_toro(cx + dx, F, cz + dz)
    # benches around
    for ang in range(0, 360, 45):
        if ang in (90, 270):
            continue
        a = math.radians(ang)
        bx, bz = int(round(cx + math.cos(a) * 8.2)), int(round(cz + math.sin(a) * 8.2))
        face = 'west' if math.cos(a) > 0.5 else ('east' if math.cos(a) < -0.5 else ('north' if math.sin(a) > 0 else 'south'))
        K.st(bx, F, bz, 'spruce', face) if W.is_empty(bx, F, bz) else None
    # notice board (announcements) by the road
    for x in range(cx + 7, cx + 10):
        S(x, F, cz + 12, 'spruce_fence') if x in (cx + 7, cx + 9) else None
    for x in range(cx + 7, cx + 10):
        for y in (F + 1, F + 2):
            S(x, y, cz + 12, 'spruce_planks')
    K.wall_sign(cx + 8, F + 2, cz + 11, 'north', ['狩猟心得', '一、準備を怠るな', '二、仲間を信じよ', '三、命を大切に'])
    K.wall_sign(cx + 8, F + 1, cz + 11, 'north', ['- 風見の村 -', '村長より', '', ''])
    for x in range(cx + 7, cx + 10):
        K.slab(x, F + 3, cz + 12, 'deepslate_tile')


def big_sakura(x, y, z):
    for i in range(9):
        for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)) if i < 5 else ((0, 0),):
            K.log(x + dx, y + i, z + dz, 'cherry_log')
    branches = [(1, 0, 6), (-1, 0, 5), (0, 1, 6), (0, -1, 7), (1, 1, 5), (-1, -1, 6), (1, -1, 7), (-1, 1, 5)]
    tips = [(x, y + 10, z)]
    for (bx, bz, ln) in branches:
        px, py, pz = float(x), float(y + 5 + (ln % 3)), float(z)
        n = math.hypot(bx, bz)
        for s in range(ln * 2):
            px += bx / n * 0.5
            pz += bz / n * 0.5
            py += 0.25
            K.log(int(round(px)), int(round(py)), int(round(pz)), 'cherry_log', 'x' if abs(bx) >= abs(bz) else 'z')
        tips.append((int(round(px)), int(round(py)) + 1, int(round(pz))))
    for (cx, cy, cz) in tips:
        r = 3.6
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                for dy in range(-3, 3):
                    d = math.sqrt(dx * dx + dz * dz + (dy * 1.6 + (0.8 if dy < 0 else 0)) ** 2)
                    if d < r + h2(cx + dx, cz + dz + dy * 7, 61) * 1.0:
                        K.leaf(cx + dx, cy + dy, cz + dz)
                dd = math.hypot(dx, dz)
                if r - 1.5 < dd < r + 0.5 and h2(cx + dx, cz + dz, 62) > 0.7:
                    K.leaf(cx + dx, cy - 4, cz + dz)
                    if h2(cx + dx, cz + dz, 63) > 0.5:
                        K.leaf(cx + dx, cy - 5, cz + dz)
    for dx in range(-9, 10):
        for dz in range(-9, 10):
            if dx * dx + dz * dz <= 81 and h2(x + dx, z + dz, 64) > 0.45:
                gx, gz = x + dx, z + dz
                if W.is_empty(gx, F, gz) and (gx, gz) not in STREAM_CELLS:
                    K.petals(gx, F, gz, seed=5)


# ====================================================================== smithy
SMITHY = (34, 62, 54, 78)


def smithy():
    x0, z0, x1, z1 = SMITHY
    y0 = F
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, G, z, 'stone_bricks' if h2(x, z, 70) > 0.3 else 'cobblestone')
            PAVED.add((x, z))
    wt = y0 + 5
    skip = set()
    # open east side (workshop front) except posts
    for z in range(z0 + 1, z1):
        for y in range(y0, wt):
            skip.add((x1, y, z))
    K.timber_walls(x0, z0, x1, z1, y0, wt, fill='white_concrete', post='stripped_dark_oak_log', base='cobblestone',
                   beam='stripped_dark_oak_log', step=4, skip=skip)
    for z in range(z0, z1 + 1, 4):
        for y in range(y0, wt):
            K.log(x1, y, z, 'stripped_dark_oak_log')
    for z in range(z0 + 1, z1):
        S(x1, wt, z, 'stripped_dark_oak_log', {'axis': 'z'})
    # lower stone walls (fire-proof)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if W.get(x, y0 + 1, z) == 'minecraft:white_concrete':
                S(x, y0 + 1, z, 'stone_bricks')
    for z in range(z0, z1 + 1):
        if W.get(x0, y0 + 1, z) == 'minecraft:white_concrete':
            S(x0, y0 + 1, z, 'stone_bricks')
    K.window(x0, y0 + 2, z0 + 6, x0, y0 + 3, z0 + 9, 'iron_bars')
    K.window(x0 + 6, y0 + 2, z1, x0 + 9, y0 + 3, z1, 'iron_bars')
    # roof (gable, ridge along x) with deep eaves over the open front
    K.roof(x0 - 1, z0 - 2, x1 + 2, z1 + 2, wt + 1, 'deepslate_tile', 'x', hip=1, cap='slab', gable='white_concrete', soffit='dark_oak')
    K.seal(x0, z0, x1, z1, wt + 1, 'white_concrete')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            pass
    # forge hearth + chimney (north-west)
    fx0, fz0 = x0 + 1, z0 + 1
    for x in range(fx0, fx0 + 5):
        for z in range(fz0, fz0 + 4):
            for y in range(y0, y0 + 2):
                S(x, y, z, 'bricks')
    S(fx0 + 2, y0 + 1, fz0 + 2, 'lava_cauldron')
    S(fx0 + 2, y0 + 1, fz0 + 3, 'air')
    S(fx0 + 1, y0 + 1, fz0 + 3, 'iron_bars')
    S(fx0 + 3, y0 + 1, fz0 + 3, 'iron_bars')
    for x in range(fx0, fx0 + 5):
        for z in range(fz0, fz0 + 4):
            if not (fx0 < x < fx0 + 4 and fz0 < z < fz0 + 3):
                S(x, y0 + 2, z, 'bricks')
    for x in range(fx0 + 1, fx0 + 4):
        for z in range(fz0, fz0 + 3):
            S(x, y0 + 3, z, 'bricks')
    S(fx0 + 2, y0 + 2, fz0 + 2, 'air') if False else None
    # chimney stack
    for y in range(y0 + 4, wt + 12):
        for x in range(fx0 + 1, fx0 + 4):
            for z in range(fz0, fz0 + 3):
                if x == fx0 + 2 and z == fz0 + 1 and y > y0 + 4:
                    W.clear(x, y, z)
                    continue
                S(x, y, z, 'bricks' if y < wt + 10 else 'mud_bricks')
    K.campfire(fx0 + 2, wt + 10, fz0 + 1, signal=True)
    S(fx0 + 2, wt + 9, fz0 + 1, 'hay_block', {'axis': 'y'})
    for y in range(y0 + 4, wt + 9):
        S(fx0 + 2, y, fz0 + 1, 'bricks')
    # bellows (piston) beside the hearth
    S(fx0 + 5, y0, fz0 + 1, 'piston', {'facing': 'west', 'extended': 'false'})
    S(fx0 + 5, y0, fz0 + 2, 'piston', {'facing': 'west', 'extended': 'false'})
    # anvils, grindstone, smithing table, quenching trough
    S(x0 + 9, y0, z0 + 7, 'anvil', {'facing': 'east'})
    S(x0 + 12, y0, z0 + 4, 'anvil', {'facing': 'north'})
    S(x0 + 7, y0, z0 + 11, 'grindstone', {'face': 'floor', 'facing': 'east'})
    S(x0 + 4, y0, z0 + 13, 'smithing_table')
    S(x0 + 3, y0, z0 + 13, 'blast_furnace', {'facing': 'north', 'lit': 'false'})
    S(x0 + 2, y0, z0 + 13, 'blast_furnace', {'facing': 'north', 'lit': 'false'})
    S(x0 + 11, y0, z0 + 10, 'water_cauldron', {'level': '3'})
    S(x0 + 12, y0, z0 + 10, 'water_cauldron', {'level': '3'})
    for (x, z) in ((x0 + 1, z0 + 8), (x0 + 1, z0 + 9), (x0 + 1, z0 + 10)):
        K.barrel(x, y0, z, 'east')
    S(x0 + 1, y0 + 1, z0 + 9, 'coal_block')
    S(x0 + 1, y0, z0 + 12, 'coal_block')
    S(x0 + 1, y0, z0 + 11, 'iron_block')
    S(x0 + 2, y0, z0 + 12, 'raw_iron_block')
    # weapon racks (fences + trapdoors on the south wall)
    for x in range(x0 + 13, x0 + 18):
        K.trapdoor(x, y0 + 2, z1 - 1, 'spruce', 'north', 'top', open_=False)
        S(x, y0, z1 - 1, 'spruce_fence') if x % 2 == 0 else S(x, y0, z1 - 1, 'chain', {'axis': 'y', 'waterlogged': 'false'})
    for x in (x0 + 5, x0 + 11, x0 + 16):
        K.chain(x, wt, z0 + 7)
        K.lantern(x, wt - 1, z0 + 7, hanging=True)
    W.mark('blacksmith', x0 + 10.5, y0, z0 + 7.5, 90.0)
    # sign
    K.hanging_sign(x1 + 1, wt - 1, z0 + 7, 12, ['', '鍛冶屋', 'Smithy', ''], mat='dark_oak')
    K.wall_banner(x1 + 1, wt - 1, z0 + 2, 'east', 'black', [('flo', DYE['orange']), ('bo', DYE['orange'])])
    K.wall_banner(x1 + 1, wt - 1, z1 - 2, 'east', 'black', [('flo', DYE['orange']), ('bo', DYE['orange'])])
    # ore & timber pile outside
    for (x, z) in ((x1 + 2, z1 - 1), (x1 + 2, z1)):
        K.log(x, y0, z, 'spruce_log', 'z')
    K.log(x1 + 3, y0, z1, 'spruce_log', 'z')
    S(x1 + 2, y0 + 1, z1, 'spruce_log', {'axis': 'z'})


# ====================================================================== market
MARKET = (58, 86, 70, 98)


def market():
    x0, z0, x1, z1 = MARKET
    y0 = F
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, G, z, 'spruce_planks' if x >= x1 - 4 else 'coarse_dirt')
            PAVED.add((x, z))
    # posts
    for (x, z) in ((x1, z0), (x1, z1), (x0 + 6, z0), (x0 + 6, z1), (x1, (z0 + z1) // 2)):
        for y in range(y0, y0 + 4):
            K.log(x, y, z, 'stripped_spruce_log')
    # back storehouse
    sx1 = x0 + 6
    K.timber_walls(x0, z0, sx1, z1, y0, y0 + 4, fill='white_concrete', post='stripped_spruce_log', base='spruce_planks',
                   beam='stripped_spruce_log', step=3)
    K.door(sx1, y0, (z0 + z1) // 2, 'east', 'spruce')
    K.roof(x0 - 1, z0 - 1, sx1 + 1, z1 + 1, y0 + 5, 'spruce', 'z', hip=0, cap='slab', gable='white_concrete', soffit='spruce')
    K.seal(x0, z0, sx1, z1, y0 + 5, 'white_concrete')
    # front awning: striped cloth (carpet on top of a fence frame)
    for z in range(z0, z1 + 1):
        for x in range(sx1 + 1, x1 + 2):
            col = 'red_carpet' if (z // 2) % 2 == 0 else 'white_carpet'
            yy = y0 + 4 if x <= x1 else y0 + 3
            if x <= x1:
                S(x, yy - 0, z, 'spruce_slab', {'type': 'top', 'waterlogged': 'false'})
                S(x, yy + 1, z, col)
            else:
                S(x, yy, z, 'spruce_trapdoor', {'facing': 'east', 'half': 'top', 'open': 'false', 'powered': 'false', 'waterlogged': 'false'})
    # counter with goods
    for z in range(z0 + 1, z1):
        if z == (z0 + z1) // 2:
            continue
        S(x1, y0, z, 'spruce_planks')
        goods = ('melon', 'pumpkin', 'hay_block', 'barrel', 'potted_red_tulip', 'decorated_pot', 'barrel', 'potted_fern', 'composter')
        g = goods[(z * 5) % len(goods)]
        if g in ('barrel',):
            K.barrel(x1, y0 + 1, z)
        elif g == 'hay_block':
            S(x1, y0 + 1, z, 'hay_block', {'axis': 'y'})
        elif g == 'decorated_pot':
            S(x1, y0 + 1, z, 'decorated_pot', {'facing': 'east', 'waterlogged': 'false'})
        elif g == 'composter':
            S(x1, y0 + 1, z, 'composter', {'level': '7'})
        else:
            S(x1, y0 + 1, z, g)
    # crates & shelves inside the stall
    for z in range(z0 + 1, z1):
        if z % 3 != 0:
            K.barrel(sx1 + 1, y0, z, 'east')
            if z % 3 == 1:
                K.barrel(sx1 + 1, y0 + 1, z, 'up')
    W.mark('merchant', x1 - 1.5, y0, (z0 + z1) / 2.0 + 0.5, -90.0)
    K.hanging_sign(x1 + 1, y0 + 2, (z0 + z1) // 2 - 2, 4, ['', '萬屋', 'General Store', ''], mat='spruce') if False else None
    S(x1 + 1, y0 + 3, z0 + 3, 'spruce_planks') if False else None
    K.wall_sign(x1 + 1, y0 + 3, (z0 + z1) // 2, 'east', ['', '萬屋', 'General Store', ''], mat='spruce')
    for z in (z0 + 2, z1 - 2):
        K.paper_lantern(x1 + 1, y0 + 2, z, 'white')
        K.chain(x1 + 1, y0 + 3, z) if W.is_empty(x1 + 1, y0 + 3, z) else None


# ====================================================================== kitchen (モフ亭)
KITCHEN = (108, 66, 126, 80)


def kitchen():
    x0, z0, x1, z1 = KITCHEN
    y0 = F
    for x in range(x0 - 4, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in STREAM_CELLS:
                continue
            S(x, G, z, 'spruce_planks' if x >= x0 else 'stone_bricks')
            PAVED.add((x, z))
    wt = y0 + 5
    skip = set()
    for z in range(z0 + 1, z1):
        for y in range(y0, wt):
            skip.add((x0, y, z))
    K.timber_walls(x0, z0, x1, z1, y0, wt, fill='white_terracotta', post='stripped_spruce_log', base='spruce_planks',
                   beam='stripped_spruce_log', step=3, skip=skip)
    for z in range(z0, z1 + 1, 3):
        for y in range(y0, wt):
            K.log(x0, y, z, 'stripped_spruce_log')
    for z in range(z0 + 1, z1):
        S(x0, wt, z, 'stripped_spruce_log', {'axis': 'z'})
    K.window(x1, y0 + 2, z0 + 3, x1, y0 + 3, z0 + 5, 'spruce_fence')
    K.window(x1, y0 + 2, z1 - 5, x1, y0 + 3, z1 - 3, 'spruce_fence')
    # thatch-like roof (ridge along z)
    K.roof(x0 - 3, z0 - 1, x1 + 1, z1 + 1, wt + 1, 'spruce', 'z', hip=1, cap='slab', gable='white_terracotta', soffit='spruce',
           ridge_mat='dark_oak')
    K.seal(x0, z0, x1, z1, wt + 1, 'white_terracotta')
    # cooking area at the back: counter + stove + pot
    cxl = x0 + 10
    for z in range(z0 + 1, z1):
        if z == z0 + 7:
            continue
        S(cxl, y0, z, 'spruce_planks')
    for (z, item) in ((z0 + 2, 'potted_fern'), (z0 + 4, 'cake'), (z1 - 3, 'potted_bamboo')):
        S(cxl, y0 + 1, z, item)
    for z in range(z0 + 1, z1):
        S(x1 - 1, y0, z, 'smoker' if z % 3 == 0 else 'barrel', {'facing': 'west', 'lit': 'false'} if z % 3 == 0 else {'facing': 'west', 'open': 'false'})
    # big pot on the hearth
    S(x1 - 4, y0, z0 + 7, 'campfire', {'facing': 'north', 'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false'})
    S(x1 - 4, y0 + 1, z0 + 7, 'water_cauldron', {'level': '3'})
    for dz in (-1, 1):
        S(x1 - 4, y0, z0 + 7 + dz, 'bricks')
        S(x1 - 4, y0 + 1, z0 + 7 + dz, 'brick_wall')
    S(x1 - 4, y0 + 2, z0 + 6, 'brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
    S(x1 - 4, y0 + 2, z0 + 8, 'brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
    # chimney through the roof
    for y in range(wt + 1, wt + 9):
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    W.clear(x1 - 4, y, z0 + 7)
                    continue
                if abs(dx) + abs(dz) <= 2:
                    S(x1 - 4 + dx, y, z0 + 7 + dz, 'bricks')
    K.campfire(x1 - 4, wt + 8, z0 + 7, signal=True)
    S(x1 - 4, wt + 7, z0 + 7, 'hay_block', {'axis': 'y'})
    for y in range(wt + 1, wt + 7):
        S(x1 - 4, y, z0 + 7, 'bricks')
    W.mark('chef', cxl + 2.5, y0, z0 + 7.5, 90.0)
    # dining: low tables with cushions inside + terrace benches outside
    for (tx, tz) in ((x0 + 3, z0 + 3), (x0 + 3, z1 - 3), (x0 + 7, z0 + 3), (x0 + 7, z1 - 3), (x0 + 5, z0 + 7)):
        K.table(tx, y0, tz, 'spruce')
        for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west')):
            S(tx + dx, y0, tz + dz, ('red_carpet' if (tx + tz) % 2 else 'orange_carpet'))
    for z in range(z0 + 1, z1, 2):
        K.st(x0 - 3, y0, z, 'spruce', 'east')
    for (x, z) in ((x0 - 2, z0 + 2), (x0 - 2, z1 - 2)):
        K.table(x, y0, z, 'spruce')
    # noren + lanterns + sign
    for z in range(z0 + 1, z1):
        if (z - z0) % 3 != 0:
            K.wall_banner(x0 - 1, wt - 1, z, 'west', 'blue' if (z - z0) % 3 == 1 else 'blue', [('bs', DYE['white']), ('flo', DYE['white'])] if z == z0 + 7 else [('bs', DYE['white'])])
    for z in (z0, z1):
        K.paper_lantern(x0 - 1, wt - 1, z)
        K.chain(x0 - 1, wt, z) if W.is_empty(x0 - 1, wt, z) else None
    K.hanging_sign(x0 - 2, wt + 1, z0 + 7, 4, ['', 'モフ亭', 'Mofu Diner', ''], mat='bamboo') if False else None
    K.wall_sign(x0 - 1, wt, z0 + 4, 'west', ['', 'モフ亭', 'Mofu Diner', ''], mat='bamboo')
    for x in (x0 + 4, x1 - 6):
        K.chain(x, wt, z0 + 7)
        K.lantern(x, wt - 1, z0 + 7, hanging=True)


# ====================================================================== companion lodge
LODGE = (124, 86, 140, 100)


def lodge():
    x0, z0, x1, z1 = LODGE
    y0 = F + 1
    # stilts + raised floor
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, y0 - 1, z, 'spruce_planks')
            if x in (x0, x1) and z in (z0, z1):
                S(x, F - 1, z, 'cobblestone')
    for x in range(x0 - 2, x1 + 1):
        for z in range(z1 + 1, z1 + 3):
            S(x, y0 - 1, z, 'spruce_slab', {'type': 'top', 'waterlogged': 'false'})
    for z in range(z0, z1 + 3):
        S(x0 - 2, y0 - 1, z, 'spruce_slab', {'type': 'top', 'waterlogged': 'false'})
        S(x0 - 1, y0 - 1, z, 'spruce_slab', {'type': 'top', 'waterlogged': 'false'})
    wt = y0 + 4
    K.timber_walls(x0, z0, x1, z1, y0, wt, fill='white_concrete', post='stripped_spruce_log', base='bamboo_planks',
                   beam='stripped_spruce_log', step=4)
    # round window and doors
    for (dy, dz) in ((1, 0), (2, -1), (2, 0), (2, 1), (3, 0)):
        S(x1, y0 + dy, (z0 + z1) // 2 + dz, 'orange_stained_glass_pane')
    K.opening((x0 + x1) // 2 - 1, y0, z1, (x0 + x1) // 2 + 1, y0 + 2, z1)
    K.opening(x0, y0, (z0 + z1) // 2, x0, y0 + 2, (z0 + z1) // 2 + 1)
    for x in ((x0 + x1) // 2 - 1, (x0 + x1) // 2, (x0 + x1) // 2 + 1):
        K.wall_banner(x, y0 + 2, z1 + 1, 'south', 'orange', [('bs', DYE['white'])])
    K.window(x0 + 2, y0 + 1, z0, x0 + 3, y0 + 2, z0, 'bamboo_fence')
    K.window(x1 - 3, y0 + 1, z0, x1 - 2, y0 + 2, z0, 'bamboo_fence')
    # thatched roof: hay with spruce edges
    k = 0
    rx0, rz0, rx1, rz1 = x0 - 2, z0 - 2, x1 + 2, z1 + 2
    while rz0 + k <= rz1 - k:
        yy = wt + 1 + k
        for x in range(rx0 + min(k, 2), rx1 - min(k, 2) + 1):
            for z in (rz0 + k, rz1 - k):
                S(x, yy, z, 'hay_block', {'axis': 'x'})
        if k < 2:
            for z in range(rz0 + k, rz1 - k + 1):
                for x in (rx0 + k, rx1 - k):
                    S(x, yy, z, 'hay_block', {'axis': 'z'})
        else:
            for z in range(rz0 + k + 1, rz1 - k):
                for x in (rx0 + 3, rx1 - 3):
                    if W.is_empty(x, yy, z):
                        S(x, yy, z, 'white_concrete' if k > 3 else 'stripped_spruce_log', None if k > 3 else {'axis': 'x'})
        k += 1
    ridge_y = wt + 1 + k
    K.seal(x0, z0, x1, z1, wt + 1, 'white_concrete')
    for x in range(rx0 + 2, rx1 - 1):
        K.log(x, ridge_y, (rz0 + rz1) // 2, 'stripped_spruce_log', 'x')
    for x in range(rx0 + 2, rx1 - 1, 3):
        S(x, ridge_y, (rz0 + rz1) // 2 - 1, 'spruce_fence') if False else None
    # interior: cushions, scratching posts, the elder's dais
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x + z) % 2 == 0:
                S(x, y0, z, ('orange_carpet', 'yellow_carpet', 'red_carpet')[(x * 3 + z) % 3]) if h2(x, z, 80) > 0.7 else None
    dx_, dz_ = (x0 + x1) // 2, z0 + 2
    for x in range(dx_ - 2, dx_ + 3):
        S(x, y0, dz_ - 1, 'spruce_slab', {'type': 'bottom', 'waterlogged': 'false'})
        S(x, y0, dz_, 'red_carpet')
    S(dx_, y0, dz_, 'red_wool')
    W.mark('elder', dx_ + 0.5, y0 + 1, dz_ + 0.5, 0.0)
    for (x, z) in ((x0 + 2, z1 - 2), (x1 - 2, z1 - 2), (x0 + 2, z0 + 5)):
        S(x, y0, z, 'white_wool')
        K.fence(x, y0 + 1, z, 'bamboo')
        S(x, y0 + 2, z, 'brown_wool')
    for (x, z) in ((x1 - 1, z0 + 1), (x1 - 1, z0 + 2), (x0 + 1, z0 + 1)):
        S(x, y0, z, 'bookshelf') if (x + z) % 2 else K.barrel(x, y0, z)
    K.chain(dx_, wt, (z0 + z1) // 2)
    K.lantern(dx_, wt - 1, (z0 + z1) // 2, hanging=True)
    K.wall_banner(dx_, y0 + 3, z0 + 1, 'south', 'orange', [('mc', DYE['white']), ('ts', DYE['brown'])])
    K.wall_sign(x0 - 1, y0 + 3, (z0 + z1) // 2 - 1, 'west', ['', 'モフの寄合所', 'Mofu Lodge', ''], mat='bamboo')
    # little mofu huts outside
    for (hx, hz) in ((x0 - 6, z1 + 3), (x0 - 4, z1 + 6)):
        S(hx, F, hz, 'barrel', {'facing': 'south', 'open': 'true'})
        K.slab(hx, F + 1, hz, 'spruce')
    # fishing rack
    for x in (x1 - 4, x1 - 1):
        K.fence(x, F, z1 + 5)
        K.fence(x, F + 1, z1 + 5)
    for x in range(x1 - 4, x1):
        K.fence(x, F + 2, z1 + 5)
    S(x1 - 3, F + 1, z1 + 5, 'dried_kelp_block') if False else None


# ====================================================================== player house
HOUSE = (38, 106, 54, 118)


def player_house():
    x0, z0, x1, z1 = HOUSE
    y0 = F + 1
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 1, z1 + 3):
            S(x, F, z, 'stone_bricks' if x in (x0 - 2, x1 + 2) or z in (z0 - 1, z1 + 2) else 'spruce_planks')
    for x in range(x0 - 2, x1 + 3):
        for z in range(z1 + 1, z1 + 3):
            S(x, F, z, 'spruce_planks')
    wt = y0 + 4
    K.timber_walls(x0, z0, x1, z1, y0, wt, fill='white_concrete', post='stripped_dark_oak_log', base='dark_oak_planks',
                   beam='stripped_dark_oak_log', step=4)
    # sliding doors (front) and shoji windows
    K.door(x0 + 7, y0, z1, 'south', 'spruce', hinge='left')
    K.door(x0 + 8, y0, z1, 'south', 'spruce', hinge='right')
    for x in (x0 + 2, x0 + 3, x1 - 3, x1 - 2):
        K.window(x, y0 + 1, z1, x, y0 + 2, z1)
    for z in (z0 + 4, z0 + 5, z0 + 7, z0 + 8):
        K.window(x0, y0 + 1, z, x0, y0 + 2, z)
        K.window(x1, y0 + 1, z, x1, y0 + 2, z)
    K.window(x0 + 6, y0 + 2, z0, x0 + 9, y0 + 2, z0)
    # tatami floor
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            S(x, y0 - 1, z, 'stripped_bamboo_block', {'axis': 'x' if ((x - x0 - 1) // 2 + (z - z0 - 1) // 4) % 2 == 0 else 'z'})
    # roof: irimoya
    K.roof(x0 - 2, z0 - 2, x1 + 2, z1 + 3, wt + 1, 'deepslate_tile', 'x', hip=2, cap='slab', gable='white_concrete',
           soffit='dark_oak')
    K.seal(x0, z0, x1, z1, wt + 1, 'white_concrete')
    # interior: futon, item box, kotatsu, tokonoma
    K.bed(x0 + 2, y0, z0 + 3, 'north', 'white')
    K.bed(x0 + 3, y0, z0 + 3, 'north', 'white') if False else None
    S(x0 + 4, y0, z0 + 1, 'huntcraft:item_box', {'facing': 'south'})
    W.mark('item_box', x0 + 4, y0, z0 + 1, 0)
    S(x0 + 5, y0, z0 + 1, 'barrel', {'facing': 'south', 'open': 'false'})
    S(x0 + 6, y0, z0 + 1, 'bookshelf')
    K.pot(x0 + 6, y0 + 1, z0 + 1, 'azalea_bush') if False else None
    # tokonoma alcove with hanging scroll
    S(x1 - 3, y0, z0 + 1, 'spruce_slab', {'type': 'bottom', 'waterlogged': 'false'})
    S(x1 - 2, y0, z0 + 1, 'spruce_slab', {'type': 'bottom', 'waterlogged': 'false'})
    K.pot(x1 - 2, y0 + 1, z0 + 1, 'flowering_azalea') if False else None
    S(x1 - 2, y0, z0 + 1, 'potted_flowering_azalea_bush')
    K.wall_banner(x1 - 3, y0 + 3, z0 + 1, 'south', 'white', [('mr', DYE['black']), ('ms', DYE['black']), ('ts', DYE['red'])])
    # kotatsu (low table with blanket)
    tx, tz = x0 + 8, z0 + 6
    for dx in (0, 1):
        for dz in (0, 1):
            S(tx + dx, y0, tz + dz, 'spruce_trapdoor', {'facing': 'north', 'half': 'top', 'open': 'false', 'powered': 'false', 'waterlogged': 'false'})
    for (cx, cz) in ((tx - 1, tz), (tx - 1, tz + 1), (tx + 2, tz), (tx + 2, tz + 1)):
        S(cx, y0, cz, 'red_carpet')
    K.chain(x0 + 8, wt, z0 + 6)
    K.paper_lantern(x0 + 8, wt - 1, z0 + 6, 'white')
    S(x1 - 1, y0, z1 - 1, 'lantern', {'hanging': 'false', 'waterlogged': 'false'})
    S(x0 + 1, y0, z1 - 1, 'potted_bamboo')
    K.wall_sign(x0 + 6, y0 + 2, z1 + 1, 'south', ['', 'マイハウス', 'My House', ''], mat='spruce')
    W.mark('house_door', x0 + 7.5, y0, z1 + 2.5, 180.0)


def house_garden():
    x0, z0, x1, z1 = HOUSE
    # bamboo fence enclosure
    gx0, gz0, gx1, gz1 = x0 - 5, z0 - 5, x1 + 8, z1 + 9
    for x in range(gx0, gx1 + 1):
        for z in (gz0, gz1):
            if not (x0 + 6 <= x <= x0 + 9 and z == gz1):
                K.fence(x, F, z, 'bamboo')
                K.fence(x, F + 1, z, 'bamboo') if (x % 4) else S(x, F + 1, z, 'bamboo_block', {'axis': 'y'})
    for z in range(gz0, gz1 + 1):
        for x in (gx0, gx1):
            if not (z1 + 3 <= z <= z1 + 5 and x == gx1):
                K.fence(x, F, z, 'bamboo')
                K.fence(x, F + 1, z, 'bamboo') if (z % 4) else S(x, F + 1, z, 'bamboo_block', {'axis': 'y'})
    # koi pond
    for x in range(x1 + 3, x1 + 8):
        for z in range(z0 + 1, z0 + 8):
            if ((x - (x1 + 5)) ** 2) / 6.5 + ((z - (z0 + 4)) ** 2) / 10.0 <= 1.0:
                S(x, G, z, 'water', {'level': '0'})
                S(x, G - 1, z, 'water', {'level': '0'})
                S(x, G - 2, z, 'clay')
                if h2(x, z, 90) > 0.8:
                    S(x, F, z, 'lily_pad')
    K.toro(x1 + 3, F, z0 - 2)
    K.pine_bonsai(x1 + 6, F, z0 - 3, seed=1)
    for i in range(6):
        K.bamboo(gx0 + 1 + (i % 3), F, gz0 + 1 + i // 3 + (i % 2), 7 + i % 4)
    for z in range(z0 + 3, z1 - 1, 2):
        K.bamboo(gx0 + 1, F, z, 6 + (z % 3))
    # raked gravel garden + rocks
    for x in range(x1 + 3, gx1):
        for z in range(z0 + 9, z1 + 3):
            S(x, G, z, 'gravel')
    for (x, z) in ((x1 + 5, z0 + 11), (x1 + 6, z1)):
        S(x, F, z, 'mossy_cobblestone')
        S(x, F + 1, z, 'cobblestone_wall') if False else None
    stepping_stones([(x0 + 7.5, z1 + 3), (x0 + 7.5, gz1)], seed=3)
    stepping_stones([(x1 + 3, z1 + 4), (gx1, z1 + 4)], seed=4)
    for (x, z, fl) in ((x0 - 3, z1 + 5, 'peony'), (x0 - 2, z1 + 7, 'lilac'), (x0 + 3, z1 + 7, 'rose_bush'), (x1 + 1, z1 + 7, 'azure_bluet'),
                       (x0 - 3, z0 - 3, 'lily_of_the_valley'), (x0 + 12, z1 + 6, 'oxeye_daisy'), (x0 + 13, z1 + 7, 'cornflower')):
        K.flower(x, F, z, fl) if W.is_empty(x, F, z) else None
    K.cherry_tree(x0 - 2, F, z1 + 6, size=0.8, seed=11)


# ====================================================================== training ground
TRAIN = (104, 110, 140, 138)


def training_ground():
    x0, z0, x1, z1 = TRAIN
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, G, z, 'sand' if h2(x, z, 100) > 0.35 else ('coarse_dirt' if h2(x, z, 101) > 0.4 else 'gravel'))
            PAVED.add((x, z))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not (x0 <= x <= x0 + 0 and False):
                K.fence(x, F, z)
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if not (x == x0 and 120 <= z <= 122):
                K.fence(x, F, z)
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, 119), (x0, 123)):
        K.log(x, F, z, 'spruce_log')
        K.log(x, F + 1, z, 'spruce_log')
        K.lantern(x, F + 2, z)
    # entrance arch
    for z in (119, 123):
        for y in range(F, F + 5):
            K.log(x0, y, z, 'spruce_log')
    for z in range(118, 125):
        K.log(x0, F + 5, z, 'stripped_spruce_log', 'z')
    K.wall_sign(x0 - 1, F + 4, 121, 'west', ['', '訓練場', 'Training Ground', ''], mat='spruce')
    K.wall_banner(x0 - 1, F + 4, 120, 'west', 'blue', [('bs', DYE['white']), ('mc', DYE['white'])])
    K.wall_banner(x0 - 1, F + 4, 122, 'west', 'blue', [('bs', DYE['white']), ('mc', DYE['white'])])
    # dummy spots
    for i, dx in enumerate((10, 18, 26)):
        W.mark('dummy', x0 + dx + 0.5, F, 127.5, 180.0)
        for ddx in (-1, 0, 1):
            for ddz in (-1, 0, 1):
                S(x0 + dx + ddx, G, 127 + ddz, 'stone_bricks' if (ddx, ddz) != (0, 0) else 'polished_andesite')
    # archery range on the east side
    for z in (114, 117, 120):
        S(x1 - 2, F, z, 'hay_block', {'axis': 'y'})
        S(x1 - 2, F + 1, z, 'target', {'power': '0'})
        S(x1 - 2, F + 2, z, 'hay_block', {'axis': 'y'})
    # weapon rack + barrels on the north side
    for x in range(x0 + 4, x0 + 12):
        S(x, F, z0 + 1, 'spruce_fence') if x % 2 == 0 else K.barrel(x, F, z0 + 1, 'south')
    for x in range(x0 + 4, x0 + 12):
        K.trapdoor(x, F + 1, z0 + 1, 'spruce', 'south', 'top')
    # viewing stand (roofed bench) on the south side
    sx0, sx1 = x0 + 10, x0 + 24
    for x in range(sx0, sx1 + 1):
        K.st(x, F, z1 - 1, 'spruce', 'south')
        K.st(x, F + 1, z1 - 0, 'spruce', 'south') if False else None
    for x in (sx0, sx1):
        for y in range(F, F + 3):
            K.log(x, y, z1 - 2, 'stripped_spruce_log')
            K.log(x, y, z1, 'stripped_spruce_log')
    K.roof(sx0 - 1, z1 - 3, sx1 + 1, z1 + 1, F + 3, 'spruce', 'x', hip=0, cap='slab')
    for (x, z) in ((x0 + 2, z1 - 3), (x0 + 3, z1 - 3), (x0 + 2, z1 - 4)):
        S(x, F, z, 'hay_block', {'axis': 'x'})


# ====================================================================== residential houses (atmosphere)
def minka(x0, z0, x1, z1, facing='south', roof_mat='deepslate_tile', seed=0, thatch=False):
    y0 = F
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            S(x, G, z, 'cobblestone' if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1) else 'spruce_planks')
    wt = y0 + 4
    K.timber_walls(x0, z0, x1, z1, y0, wt, fill='white_concrete' if seed % 2 == 0 else 'white_terracotta',
                   post='stripped_spruce_log' if seed % 2 else 'stripped_dark_oak_log', base='spruce_planks',
                   beam='stripped_spruce_log' if seed % 2 else 'stripped_dark_oak_log', step=3)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    if facing == 'south':
        K.door(mx, y0, z1, 'south')
        K.window(x0 + 1, y0 + 1, z1, x0 + 1, y0 + 2, z1)
        K.window(x1 - 1, y0 + 1, z1, x1 - 1, y0 + 2, z1)
        K.paper_lantern(mx + 1, y0 + 2, z1 + 1)
    elif facing == 'north':
        K.door(mx, y0, z0, 'north')
        K.window(x0 + 1, y0 + 1, z0, x0 + 1, y0 + 2, z0)
        K.window(x1 - 1, y0 + 1, z0, x1 - 1, y0 + 2, z0)
        K.paper_lantern(mx + 1, y0 + 2, z0 - 1)
    elif facing == 'east':
        K.door(x1, y0, mz, 'east')
        K.window(x1, y0 + 1, z0 + 1, x1, y0 + 2, z0 + 1)
        K.window(x1, y0 + 1, z1 - 1, x1, y0 + 2, z1 - 1)
        K.paper_lantern(x1 + 1, y0 + 2, mz + 1)
    else:
        K.door(x0, y0, mz, 'west')
        K.window(x0, y0 + 1, z0 + 1, x0, y0 + 2, z0 + 1)
        K.window(x0, y0 + 1, z1 - 1, x0, y0 + 2, z1 - 1)
        K.paper_lantern(x0 - 1, y0 + 2, mz + 1)
    axis = 'x' if (x1 - x0) >= (z1 - z0) else 'z'
    K.roof(x0 - 1, z0 - 1, x1 + 1, z1 + 1, wt + 1, roof_mat, axis, hip=1 if seed % 3 == 0 else 0, cap='slab',
           gable='white_concrete', soffit='spruce' if roof_mat == 'spruce' else 'dark_oak')
    K.seal(x0, z0, x1, z1, wt + 1, 'white_concrete')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            S(x, y0 - 1, z, 'spruce_planks')
    K.lantern(mx, wt - 0, mz, hanging=True) if False else None
    S(x0 + 1, y0, z0 + 1, 'barrel', {'facing': 'up', 'open': 'false'})
    K.bed(x1 - 1, y0, z0 + 1, 'north' if z1 - z0 > 2 else 'east', ('blue', 'green', 'yellow', 'cyan')[seed % 4]) if False else None
    S(x1 - 1, y0, z0 + 1, 'crafting_table')
    S(mx, y0, mz, 'white_carpet') if W.is_empty(mx, y0, mz) else None


def well(x, z):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                S(x, G, z, 'water', {'level': '0'})
                S(x, G - 1, z, 'water', {'level': '0'})
                S(x, G - 2, z, 'water', {'level': '0'})
            else:
                S(x + dx, F, z + dz, 'mossy_stone_bricks' if h2(x + dx, z + dz, 3) > 0.6 else 'stone_bricks')
    for (dx, dz) in ((-1, -1), (1, 1)):
        K.fence(x + dx, F + 1, z + dz)
        K.fence(x + dx, F + 2, z + dz)
    K.roof(x - 2, z - 2, x + 2, z + 2, F + 3, 'spruce', 'x', hip=0, cap='slab')
    K.chain(x, F + 3, z) if False else None


def torii(x0, x1, z, y=F, color='red_concrete'):
    for x in (x0, x1):
        S(x, G, z, 'stone_bricks')
        for yy in range(y, y + 6):
            S(x, yy, z, color)
        S(x, y, z, 'black_concrete')
    for x in range(x0 - 1, x1 + 2):
        S(x, y + 6, z, 'black_concrete' if x in (x0 - 1, x1 + 1) else 'dark_oak_planks') if False else None
    for x in range(x0, x1 + 1):
        S(x, y + 4, z, color)
    for x in range(x0 - 1, x1 + 2):
        S(x, y + 6, z, color)
    for x in range(x0 - 2, x1 + 3):
        S(x, y + 7, z, 'black_concrete') if x0 - 1 <= x <= x1 + 1 else K.slab(x, y + 7, z, 'blackstone')
    S((x0 + x1) // 2, y + 5, z, 'dark_oak_planks')


def shrine(x, z):
    """Small hillside shrine with a torii, reached by steps from the village."""
    y = F
    for dx in range(-3, 4):
        for dz in range(-3, 3):
            S(x + dx, G, z + dz, 'gravel')
    for dx in range(-2, 3):
        for dz in range(-2, 2):
            S(x + dx, F, z + dz, 'stone_bricks')
    for dx in range(-1, 2):
        for dz in range(-1, 1):
            for yy in range(F + 1, F + 4):
                S(x + dx, yy, z + dz, 'cherry_planks' if dz == -1 or abs(dx) == 1 else 'air')
    S(x, F + 1, z, 'candle', {'candles': '3', 'lit': 'true', 'waterlogged': 'false'})
    S(x, F + 2, z - 1, 'gold_block') if False else None
    K.roof(x - 2, z - 3, x + 2, z + 2, F + 4, 'deepslate_tile', 'z', hip=0, cap='slab', gable='cherry_planks', soffit='dark_oak')
    K.wall_banner(x, F + 3, z + 1, 'south', 'white', [('bs', DYE['red']), ('mc', DYE['red'])])
    torii(x - 2, x + 2, z + 5)
    K.toro(x - 3, F, z + 3)
    K.toro(x + 3, F, z + 3)


def rice_paddies(x0, z0, x1, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in STREAM_CELLS or MH.get((x, z), 0) > 0:
                continue
            bx = (x - x0) % 7
            bz = (z - z0) % 6
            if bx == 0 or bz == 0:
                S(x, G, z, 'dirt_path' if h2(x, z, 120) > 0.3 else 'grass_block')
                continue
            if bx == 3 and bz == 3:
                S(x, G, z, 'water', {'level': '0'})
                continue
            S(x, G, z, 'farmland', {'moisture': '7'})
            S(x, F, z, 'wheat', {'age': str(5 + int(h2(x, z, 121) * 3) % 3)})


# ====================================================================== decoration passes
def scatter_nature():
    trees = [(50, 84, 0.8), (78, 101, 0.7), (99, 102, 0.6), (77, 124, 0.8), (100, 140, 0.6), (77, 141, 0.7),
             (62, 104, 0.6), (124, 61, 0.5), (66, 58, 0.5), (138, 76, 0.7), (40, 146, 0.6), (116, 145, 0.6)]
    for i, (x, z, s) in enumerate(trees):
        if (x, z) in PAVED or (x, z) in STREAM_CELLS or not W.is_empty(x, F, z):
            continue
        K.cherry_tree(x, F, z, size=s, seed=i * 13 + 5)
    for i, (x, z) in enumerate(((36, 100), (70, 146), (142, 108), (142, 126), (36, 60), (40, 76), (106, 146))):
        if (x, z) in PAVED or (x, z) in STREAM_CELLS or not W.is_empty(x, F, z):
            continue
        K.spruce_tree(x, F, z, 7 + i % 4, seed=i)
    # lanterns along the main road
    for z in range(97, VZ1 - 3, 8):
        K.lamp_post(ROAD_X - 5, F, z, 'east')
        K.lamp_post(ROAD_X + 5, F, z, 'west')
    # flowers & grass in open grass areas
    for x in range(VX0, VX1):
        for z in range(VZ0, VZ1):
            if not in_village(x, z, 1) or (x, z) in PAVED or (x, z) in STREAM_CELLS or (x, z) in POND:
                continue
            if W.get(x, G, z) not in (None, 'minecraft:grass_block') or not W.is_empty(x, F, z):
                continue
            if W.get(x, F + 1, z) is not None:
                continue
            r = h2(x, z, 130)
            if r > 0.93:
                K.flower(x, F, z, 'grass')
            elif r > 0.915:
                K.flower(x, F, z, ('poppy', 'dandelion', 'oxeye_daisy', 'cornflower', 'azure_bluet', 'allium', 'lily_of_the_valley')[int(h2(x, z, 131) * 7) % 7])
            elif r > 0.912:
                K.flower(x, F, z, 'tall_grass')


def build_paths():
    cx, cz, r = PLAZA
    # main road plaza -> gate
    path_line([(ROAD_X, cz + r - 1), (ROAD_X, VZ1 + 8)], width=7, style='gravel', seed=1)
    for z in range(cz + r, VZ1 + 2):
        S(ROAD_X, G, z, 'polished_andesite') if (ROAD_X, z) in PAVED else None
    # plaza <-> terrace stairs
    path_line([(ROAD_X, PZ1 + 3), (ROAD_X, cz - r + 1)], width=13, style='stone', seed=2)
    # west: plaza -> smithy, market, houses
    path_line([(cx - r + 1, cz - 2), (62, 74), (SMITHY[2] + 1, 70)], width=4, style='gravel', seed=3)
    path_line([(cx - r + 1, cz + 4), (MARKET[2] + 2, 92)], width=4, style='gravel', seed=4)
    path_line([(66, 99), (60, 104), (50, 124), (46, 125)], width=3, style='dirt', seed=5)
    path_line([(ROAD_X - 3, 110), (64, 112), (58, 124), (46, 125)], width=3, style='dirt', seed=6)
    path_line([(PX0 - 3, 45), (44, 52), (44, 60)], width=3, style='dirt', seed=7)
    # east: plaza -> kitchen (bridge) -> lodge, training
    path_line([(cx + r - 1, cz - 8), (KITCHEN[0] - 4, 73)], width=4, style='gravel', seed=8)
    path_line([(cx + r - 1, cz + 3), (115, 85), (LODGE[0] - 2, 96)], width=3, style='gravel', seed=9)
    path_line([(ROAD_X + 3, 121), (TRAIN[0], 121)], width=4, style='gravel', seed=10)
    path_line([(113, 62), (113, 66)], width=3, style='gravel', seed=11)
    path_line([(119, 50), (124, 56), (128, 84)], width=3, style='dirt', seed=12)


def bridges():
    # kitchen bridge (over the stream at x~104)
    bridge(102, 107, 72, 74, 'x')
    # lodge path bridge
    bridge(102, 107, 86, 88, 'x', rail='wood')
    # training ground bridge
    bridge(96, 101, 120, 122, 'x')
    # pond-side path bridge (stream outlet)
    bridge(117, 121, 54, 56, 'x', rail='wood')
    # terrace east stairs path crossing
    bridge(111, 113, 62, 66, 'z', rail='wood') if False else None


def markers_spawn():
    W.mark('spawn', ROAD_X + 0.5, F, 72.5, 180.0)


# ====================================================================== java layout export
def export_java():
    def wp(x, y, z):
        return (x + ORIGIN[0], y + ORIGIN[1], z + ORIGIN[2])

    roles = ['receptionist', 'blacksmith', 'merchant', 'chef', 'elder']
    lines = []
    lines.append('package com.huntcraft.village.world;')
    lines.append('')
    lines.append('/** GENERATED by tools/village/gen_village.py - do not edit. World positions of the Kazami village template. */')
    lines.append('public final class VillageLayout {')
    lines.append('    public static final int VERSION = %d;' % LAYOUT_VERSION)
    lines.append('    public static final int ORIGIN_X = %d, ORIGIN_Y = %d, ORIGIN_Z = %d;' % ORIGIN)
    lines.append('    public static final int SIZE_X = %d, SIZE_Y = %d, SIZE_Z = %d;' % (SX, SY, SZ))
    nx, nz = math.ceil(SX / TILE), math.ceil(SZ / TILE)
    lines.append('    public static final int TILE = %d, TILES_X = %d, TILES_Z = %d;' % (TILE, nx, nz))
    sp = W.markers['spawn'][0]
    x, y, z = wp(*sp[:3])
    lines.append('    public static final double SPAWN_X = %.1f, SPAWN_Y = %.1f, SPAWN_Z = %.1f;' % (x, y, z))
    lines.append('    public static final float SPAWN_YAW = %.1fF;' % sp[3])
    lines.append('    /** Protected area (village + surrounding mountains): min x, min z, max x, max z. */')
    lines.append('    public static final int[] BOUNDS = {%d, %d, %d, %d};' % (ORIGIN[0], ORIGIN[2], ORIGIN[0] + SX - 1, ORIGIN[2] + SZ - 1))
    lines.append('    /** NPC spots: role name, x, y, z, yaw. */')
    lines.append('    public static final String[] NPC_ROLES = {%s};' % ', '.join('"%s"' % r for r in roles))
    lines.append('    public static final double[][] NPC_POS = {')
    for r in roles:
        m = W.markers[r][0]
        x, y, z = wp(*m[:3])
        lines.append('            {%.1f, %.1f, %.1f, %.1f}, // %s' % (x, y, z, m[3], r))
    lines.append('    };')
    lines.append('    public static final double[][] DUMMY_POS = {')
    for m in W.markers['dummy']:
        x, y, z = wp(*m[:3])
        lines.append('            {%.1f, %.1f, %.1f, %.1f},' % (x, y, z, m[3]))
    lines.append('    };')
    ib = W.markers['item_box'][0]
    x, y, z = wp(*ib[:3])
    lines.append('    public static final int[] ITEM_BOX = {%d, %d, %d};' % (int(x), int(y), int(z)))
    hd = W.markers['house_door'][0]
    x, y, z = wp(*hd[:3])
    lines.append('    public static final double[] HOUSE_DOOR = {%.1f, %.1f, %.1f, %.1f};' % (x, y, z, hd[3]))
    lines.append('')
    lines.append('    private VillageLayout() {')
    lines.append('    }')
    lines.append('}')
    path = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'huntcraft', 'village', 'world', 'VillageLayout.java')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    return path


def validate():
    """Sanity checks: NPC spots are free with a floor, no floating hanging blocks."""
    for name, lst in W.markers.items():
        for (x, y, z, yaw) in lst:
            ix, iy, iz = int(math.floor(x)), int(math.floor(y)), int(math.floor(z))
            if name in ('item_box',):
                continue
            for dy in (0, 1):
                b = W.get(ix, iy + dy, iz)
                if b is not None and b != 'minecraft:air' and 'carpet' not in b:
                    print('WARN marker %s at %s blocked by %s' % (name, (ix, iy + dy, iz), b))


def build():
    build_mountains()
    build_waterfall()
    build_pond()
    terrace()
    build_stream()
    gathering_hall()
    plaza()
    smithy()
    market()
    kitchen()
    lodge()
    player_house()
    house_garden()
    training_ground()
    # residential houses
    minka(36, 86, 44, 94, 'east', seed=0)
    minka(36, 36, 46, 44, 'south', seed=1, roof_mat='spruce')
    minka(48, 30, 54, 38, 'south', seed=2)
    minka(126, 58, 136, 64, 'west', seed=3)
    minka(60, 128, 68, 134, 'east', seed=4, roof_mat='spruce')
    minka(112, 92, 118, 98, 'west', seed=5)
    well(64, 80)
    shrine(48, 56) if False else None
    rice_paddies(34, 132, 56, 145)
    watchtower(VX0 + 6, VZ1 - 6)
    watchtower(VX1 - 6, VZ1 - 6)
    main_gate()
    torii(ROAD_X - 5, ROAD_X + 5, PZ1 + 6)
    build_paths()
    palisade()
    bridges()
    scatter_nature()
    markers_spawn()


if __name__ == '__main__':
    build()
    validate()
    out = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'huntcraft', 'structures')
    nx, nz, written = W.save_tiles(out, 'village', TILE, SY)
    jp = export_java()
    print('blocks', len(W.blocks), 'tiles', nx, nz, 'written', len(written), '->', jp)
    if '--preview' in sys.argv:
        import vrender
        pv = os.path.join(ROOT, 'tools', 'preview')
        os.makedirs(pv, exist_ok=True)
        vrender.top(W, os.path.join(pv, 'village_top.png'), scale=4)
        vrender.iso(W, os.path.join(pv, 'village_iso.png'), rot=0, scale=4)
        vrender.iso(W, os.path.join(pv, 'village_iso2.png'), rot=1, scale=4)
        print('previews written')
