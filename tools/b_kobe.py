"""Kobe: JR Kobe station (1930 building), Harborland, Meriken Park (Port Tower, Maritime Museum, BE KOBE, quake memorial),
Nankinmachi, Motomachi shotengai, Kitano ijinkan (Weathercock House, Moegi House), Venus Bridge on Suwayama, city skyline."""
import math
import random

import numpy as np

from bcommon import (AIR, TB, hip_roof, gable_roof, flat_roof, tree, utility_pole, street_lamp, vending, bench, road, fillet_path, resample,
                     office, jp_house, fix_stairs, fix_connections)
from wb import stairs, slab, facing, pane, axis, smooth

G = 66
RESERVED = []


def reserve(x1, z1, x2, z2):
    RESERVED.append((x1, z1, x2, z2))


def free(x1, z1, x2, z2, margin=2):
    for (a, b, c, d) in RESERVED:
        if x1 - margin <= c and x2 + margin >= a and z1 - margin <= d and z2 + margin >= b:
            return False
    return True


# ===================================================================== terrain
def terrain(w):
    def kobe(X, Z, Hm, Hn):
        wx = smooth(np.minimum((X - 3030) / 120.0, (4170 - X) / 120.0))
        wn = np.where(Z < 470, smooth(1 - (470 - Z) / 100.0), 1.0)
        land = (Z <= 812).astype(np.float64)
        wgt = wx * wn * land
        return Hm + (G - Hm) * wgt
    w.add_mod(kobe)
    # Kitano slope terraces along Kitano-zaka
    w.flatten_path([(3740, 470), (3740, 445), (3740, 420), (3740, 395)], 75, 35, targets=[67, 74, 82, 88])
    # Suwayama hill for Venus Bridge
    w.flatten_ellipse(3548, 398, 44, 30, 104, 32)


def regions(w):
    w.ensure(3060, 300, 4150, 960)


# ===================================================================== helpers
def disc(cx, cz, r):
    out = []
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 <= r * r:
                out.append((x, z))
    return out


def ring(cx, cz, r, width=1.0):
    out = []
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if abs(d - r) <= width / 2 + 0.25:
                out.append((x, z))
    return out


def line3(w, a, b, state, lit=None, every=4):
    ax, ay, az = a
    bx, by, bz = b
    n = int(max(abs(bx - ax), abs(by - ay), abs(bz - az)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x, y, z = ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t
        st = lit if (lit and i % (every * 2) == 0) else state
        w.set(int(math.floor(x + 0.5)), int(math.floor(y + 0.5)), int(math.floor(z + 0.5)), st)


FONT = {  # 3x5 pixel font (rows top->bottom)
    'B': ['110', '101', '110', '101', '110'], 'E': ['111', '100', '110', '100', '111'], 'K': ['101', '110', '100', '110', '101'],
    'O': ['111', '101', '101', '101', '111'], ' ': ['000', '000', '000', '000', '000'],
}


# ===================================================================== roads (also bus route)
ROADS = {}


def roads(w):
    r1 = fillet_path([(3330, 702), (3820, 702)], 10)
    road(w, r1, G, width=10, center='yellow', sidewalk=3)
    reserve(3330, 694, 3820, 710)
    r2 = fillet_path([(3575, 702), (3575, 754)], 6)
    road(w, r2, G, width=7, center='white', sidewalk=2)
    reserve(3568, 702, 3582, 756)
    r3 = fillet_path([(3484, 702), (3484, 590)], 6)
    road(w, r3, G, width=8, center='white', sidewalk=2)
    reserve(3477, 588, 3491, 702)
    r4 = fillet_path([(3360, 590), (3820, 590)], 10)
    road(w, r4, G, width=8, center='white', sidewalk=3)
    reserve(3360, 583, 3820, 597)
    r5 = fillet_path([(3700, 590), (3700, 474)], 6)
    road(w, r5, G, width=8, center='white', sidewalk=2)
    reserve(3693, 470, 3707, 590)
    # side streets for the city grid
    for x in (3400, 3560, 3800, 3900, 4000):
        p = fillet_path([(x, 470), (x, 700)], 6)
        road(w, p, G, width=6, center=None, sidewalk=1)
        reserve(x - 4, 470, x + 4, 700)
    for z in (520,):
        p = fillet_path([(3330, z), (4050, z)], 6)
        road(w, p, G, width=6, center=None, sidewalk=1)
        reserve(3330, z - 4, 4050, z + 4)
    # Suwayama road: hairpin up to Venus Bridge
    hill = fillet_path([(3700, 474), (3700, 462), (3600, 470), (3575, 452), (3640, 440), (3600, 422), (3566, 414)], 9)
    n = len(hill)
    def y_at(i):
        t = i / (n - 1)
        return G + (104 - G) * smooth(t * 1.05)
    road(w, hill, G, width=6, center='yellow', sidewalk=0, y_at=y_at, cut=6)
    ROADS['hill'] = (hill, y_at)


# ===================================================================== JR Kobe station (1930 building)
def kobe_station(w):
    x1, x2, z1, z2 = 3398, 3472, 655, 690
    y0 = G
    top = y0 + 14
    w.fill(x1, y0, z1, x2, y0, z2, 'minecraft:smooth_stone')
    w.fill(x1, y0 + 1, z1, x2, top, z2, TB('tile_cream'))
    w.fill(x1 + 1, y0 + 1, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR)
    w.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, 'minecraft:polished_andesite')
    w.fill(x1 + 1, y0 + 6, z1 + 1, x2 - 1, y0 + 6, z2 - 1, 'minecraft:smooth_stone')
    w.fill(x1 + 1, y0 + 10, z1 + 1, x2 - 1, y0 + 10, z2 - 1, 'minecraft:smooth_stone')
    # concourse opening between floors (double height hall in the middle)
    w.fill(3420, y0 + 6, 662, 3450, y0 + 6, 684, AIR)
    # south facade: pilasters, tall arched windows, central portico
    for x in range(x1, x2 + 1, 4):
        w.fill(x, y0 + 1, z2, x, top, z2, 'minecraft:smooth_sandstone')
    for x in range(x1 + 2, x2 - 1, 4):
        for (ya, yb) in ((y0 + 2, y0 + 4), (y0 + 7, y0 + 9), (y0 + 11, y0 + 12)):
            w.fill(x, ya, z2, x + 1, yb, z2, pane('minecraft:light_blue_stained_glass_pane', e=True, w=True))
    w.fill(x1, top, z1, x2, top + 1, z2, 'minecraft:smooth_sandstone')
    w.fill(x1 + 1, top + 1, z1 + 1, x2 - 1, top + 1, z2 - 1, AIR)
    w.fill(x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, 'minecraft:smooth_stone')
    # central block raised with the station name
    w.fill(3424, top, z2 - 3, 3446, top + 4, z2, TB('tile_cream'))
    w.fill(3425, top + 1, z2 - 2, 3445, top + 3, z2 - 1, AIR)
    w.fill(3424, top + 5, z2 - 3, 3446, top + 5, z2, 'minecraft:smooth_sandstone')
    w.sign(3431, top + 2, z2 + 1, 'tabi:info_board', 'south', ['神戸駅', 'KOBE STATION'], style='info', span=9)
    for k in range(1, 9):
        w.set(3431 + k, top + 2, z2 + 1, facing(TB('info_board'), 'south'))
    # entrance portico
    w.fill(3426, y0 + 1, z2, 3444, y0 + 4, z2, AIR)
    for x in (3426, 3431, 3439, 3444):
        w.fill(x, y0 + 1, z2 + 4, x, y0 + 5, z2 + 4, 'minecraft:smooth_sandstone')
    w.fill(3425, y0 + 6, z2, 3445, y0 + 6, z2 + 5, 'minecraft:smooth_sandstone')
    w.fill(3426, y0 + 5, z2 + 1, 3444, y0 + 5, z2 + 4, 'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
    for x in (3429, 3435, 3441):
        w.set(x, y0 + 5, z2 + 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # concourse: ticket machines, gates toward the platform (north wall z1)
    for x in (3404, 3406, 3408):
        w.set(x, y0 + 1, z1 + 1, facing(TB('kenbaiki'), 'south'))
        w.set(x, y0 + 2, z1 + 1, facing(TB('kenbaiki_top'), 'south'))
    w.fill(3412, y0 + 1, z1, 3428, y0 + 4, z1, AIR)
    for x in range(3412, 3429, 2):
        w.set(x, y0 + 1, z1, facing(TB('kaisatsu'), 'south') if 3412 < x < 3428 else TB('tile_cream'))
    # steps up to the platform (platform surface y 68)
    w.fill(3412, y0, z1 - 1, 3428, y0 + 1, z1 - 1, 'minecraft:smooth_stone')
    w.fill(3412, y0 + 2, z1 - 1, 3428, y0 + 4, z1 - 1, AIR)
    # info counter (観光案内所)
    w.fill(3452, y0 + 1, 668, 3462, y0 + 1, 668, 'minecraft:smooth_quartz')
    w.sign(3455, y0 + 4, 664, 'tabi:info_board', 'south', ['観光案内所', 'Tourist Information'], style='info', span=5)
    for k in range(1, 5):
        w.set(3455 + k, y0 + 4, 664, facing(TB('info_board'), 'south'))
    w.npc(3457, y0 + 1, 666, 'kobe_info', 'info_lady', '観光案内所のお姉さん', '', 'stand', yaw=0)
    w.npc(3420, y0 + 1, 660, 'kobe_ekiin', 'station_staff', '神戸駅の駅員さん', 'kobe_station', 'stand', yaw=0)
    for x in range(3404, 3468, 8):
        w.set(x, y0 + 5, 676, 'minecraft:lantern[hanging=true,waterlogged=false]')
    for x in (3414, 3418, 3448, 3452):
        bench(w, x, y0 + 1, 680, 'south', 'station_bench')
    vending(w, 3466, y0 + 1, 670, 'west')
    # station plaza
    for x in range(3380, 3480):
        for z in range(691, 695):
            w.set(x, G, z, 'tabi:sidewalk')
    reserve(3380, 640, 3480, 696)
    w.area('kobe_station', '神戸駅', '1930年の駅舎', (3396, 60, 640, 3474, 90, 696), 'spot.kobe_station')
    w.check(3404, y0 + 1, z1 + 1, 'kobe ticket machine')


# ===================================================================== Harborland
def harborland(w, rnd):
    # gas light street (ガス燈通り) N-S from the avenue to the waterfront
    for z in range(708, 800):
        for x in range(3434, 3447):
            w.set(x, G, z, 'minecraft:bricks' if 3436 <= x <= 3444 else 'tabi:sidewalk')
    for z in range(710, 798, 6):
        for x in (3435, 3445):
            w.set(x, G + 1, z, TB('lamp_pole'))
            w.set(x, G + 2, z, TB('lamp_pole'))
            w.set(x, G + 3, z, TB('gas_lamp'))
        if z % 12 == 4:
            tree(w, 3433, G + 1, z, 'broad', size=0.7)
            tree(w, 3447, G + 1, z, 'broad', size=0.7)
    w.sign(3437, G + 5, 708, 'tabi:shop_sign', 'north', ['ガス燈通り', 'GAS LIGHT STREET'], style='shop', span=7)
    for k in range(1, 7):
        w.set(3437 + k, G + 5, 708, facing(TB('shop_sign'), 'north'))
    for x in (3436, 3444):
        w.fill(x, G + 1, 708, x, G + 4, 708, TB('lamp_pole'))
    reserve(3432, 706, 3448, 800)
    w.area('gaslight', 'ガス燈通り', 'ハーバーランド', (3432, 60, 706, 3448, 85, 800), 'spot.gaslight_street')
    # mall (fictional) west of the street
    office(w, 3352, 714, 3426, 788, G, 6, rnd, wall='tabi:office_wall', lit_ratio=0.8)
    w.fill(3352, G + 1, 788, 3426, G + 3, 788, pane('minecraft:light_blue_stained_glass_pane', e=True, w=True))
    w.sign(3380, G + 8, 789, 'tabi:neon_sign', 'south', ['ハーバーモール', 'HARBOR MALL'], style='neon', span=12, glow=True)
    for k in range(1, 12):
        w.set(3380 + k, G + 8, 789, facing(TB('neon_sign'), 'south'))
    reserve(3350, 712, 3428, 790)
    # souvenir hall east of the street
    sx1, sx2, sz1, sz2 = 3450, 3470, 712, 732
    w.fill(sx1, G, sz1, sx2, G, sz2, 'minecraft:polished_andesite')
    w.fill(sx1, G + 1, sz1, sx2, G + 6, sz2, 'minecraft:white_concrete')
    w.fill(sx1 + 1, G + 1, sz1 + 1, sx2 - 1, G + 5, sz2 - 1, AIR)
    w.fill(sx1, G + 1, sz1 + 3, sx1, G + 3, sz2 - 3, pane('minecraft:glass_pane', n=True, s=True))
    w.fill(sx1, G + 1, 721, sx1, G + 3, 723, AIR)
    for z in range(sz1 + 2, sz2 - 1, 2):
        w.set(sx2 - 1, G + 1, z, facing(TB('souvenir_shelf'), 'west'))
        w.set(sx2 - 1, G + 2, z, facing(TB('souvenir_shelf'), 'west'))
    for x in range(sx1 + 4, sx2 - 3, 3):
        w.set(x, G + 1, sz1 + 1, facing(TB('souvenir_shelf'), 'south'))
        w.set(x, G + 1, sz2 - 1, facing(TB('souvenir_shelf'), 'north'))
    w.fill(sx1 + 6, G + 1, 720, sx1 + 6, G + 1, 724, 'minecraft:smooth_quartz')
    w.sign(sx1 - 1, G + 5, sz1 + 4, 'tabi:shop_sign', 'west', ['港のおみやげ館', 'KOBE SOUVENIR'], style='shop', span=8)
    for k in range(1, 8):
        w.set(sx1 - 1, G + 5, sz1 + 4 + k, facing(TB('shop_sign'), 'west'))
    w.npc(sx1 + 7, G + 1, 722, 'harbor_souvenir', 'shop_staff', 'おみやげ館の店員さん', '', 'stand', yaw=90)
    reserve(sx1, sz1, sx2, sz2)
    # brick warehouses on the waterfront
    for (x1, x2) in ((3458, 3500), (3506, 3542)):
        z1, z2 = 776, 796
        w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
        w.fill(x1, G + 1, z1, x2, G + 9, z2, TB('renga'))
        w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 8, z2 - 1, AIR)
        w.fill(x1 + 1, G, z1 + 1, x2 - 1, G, z2 - 1, 'minecraft:spruce_planks')
        w.fill(x1 + 1, G + 5, z1 + 1, x2 - 1, G + 5, z2 - 1, 'minecraft:spruce_planks')
        for x in range(x1 + 2, x2 - 1, 4):
            for zz in (z1, z2):
                w.fill(x, G + 1, zz, x + 1, G + 3, zz, pane('minecraft:glass_pane', e=True, w=True))
                w.set(x, G + 4, zz, 'minecraft:brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]')
                w.set(x + 1, G + 4, zz, 'minecraft:brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]')
                w.fill(x, G + 6, zz, x + 1, G + 7, zz, pane('minecraft:glass_pane', e=True, w=True))
        gable_roof(w, x1, z1, x2, z2, G + 10, 'x', 'minecraft:deepslate_tile', overhang=1, gable_wall=TB('renga'))
        for x in range(x1 + 3, x2 - 2, 6):
            w.set(x, G + 4, (z1 + z2) // 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
        reserve(x1, z1, x2, z2)
    # cafe in the first warehouse: counter + tables, terrace on the promenade
    w.fill(3462, G + 1, 779, 3470, G + 1, 779, 'minecraft:dark_oak_planks')
    w.set(3464, G + 1, 778, 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=true,has_bottle_2=false]')
    w.set(3468, G + 1, 778, TB('cake_showcase') + '[facing=south]')
    for (x, z) in ((3464, 786), (3470, 786), (3476, 786), (3482, 790), (3488, 786), (3494, 790)):
        w.set(x, G + 1, z, 'minecraft:dark_oak_fence')
        w.set(x, G + 2, z, 'minecraft:dark_oak_pressure_plate')
        w.set(x - 1, G + 1, z, stairs('minecraft:dark_oak_stairs', 'east'))
        w.set(x + 1, G + 1, z, stairs('minecraft:dark_oak_stairs', 'west'))
    w.fill(3462, G + 1, 796, 3470, G + 3, 796, AIR)
    w.sign(3462, G + 5, 797, 'tabi:shop_sign', 'south', ['ハーバー煉瓦カフェ', 'HARBOR BRICK CAFE'], style='shop', span=9)
    for k in range(1, 9):
        w.set(3462 + k, G + 5, 797, facing(TB('shop_sign'), 'south'))
    w.npc(3466, G + 1, 780, 'harbor_cafe', 'cafe_staff', '煉瓦カフェの店員さん', '', 'stand', yaw=0)
    w.area('brick_warehouse', '煉瓦倉庫', 'ハーバーランド', (3456, 60, 774, 3544, 85, 799), 'spot.brick_warehouse')
    # promenade + quay
    for x in range(3330, 3560):
        for z in range(798, 812):
            w.set(x, G, z, 'tabi:hinoki' if z >= 802 else 'tabi:sidewalk')
        w.set(x, G + 1, 811, 'tabi:railing')
        w.fill(x, 50, 812, x, G, 812, 'minecraft:stone_bricks')
    for x in range(3336, 3556, 10):
        street_lamp(w, x, G + 1, 809, 'south', kind='gas_lamp', height=4)
        if x % 20 == 6:
            bench(w, x + 3, G + 1, 806, 'south')
    # ferris wheel (観覧車)
    fx, fz, fy, R = 3400, 804, G + 24, 20
    w.fill(fx - 8, G + 1, fz - 3, fx + 8, G + 1, fz + 3, 'minecraft:smooth_stone')
    for side in (-2, 2):
        line3(w, (fx - 10, G + 1, fz + side), (fx, fy, fz + side), 'minecraft:white_concrete')
        line3(w, (fx + 10, G + 1, fz + side), (fx, fy, fz + side), 'minecraft:white_concrete')
    for k in range(-2, 3):
        w.set(fx, fy, fz + k, 'minecraft:white_concrete')
    for a in range(0, 360, 2):
        r = math.radians(a)
        for side in (-1, 1):
            x = int(round(fx + R * math.cos(r)))
            y = int(round(fy + R * math.sin(r)))
            w.set(x, y, fz + side, 'minecraft:white_concrete' if a % 12 else 'minecraft:sea_lantern')
    for a in range(0, 360, 20):
        r = math.radians(a)
        line3(w, (fx, fy, fz - 1), (fx + R * math.cos(r), fy + R * math.sin(r), fz - 1), 'minecraft:white_concrete')
        gx, gy = int(round(fx + (R + 1.5) * math.cos(r))), int(round(fy + (R + 1.5) * math.sin(r)))
        col = rnd.choice(['minecraft:red_stained_glass', 'minecraft:yellow_stained_glass', 'minecraft:lime_stained_glass', 'minecraft:light_blue_stained_glass'])
        w.fill(gx - 1, gy - 2, fz - 1, gx + 1, gy - 1, fz + 1, col)
    w.npc(3500, G + 1, 806, 'musician', 'musician', 'ストリートミュージシャン', 'musician', 'stand', yaw=180)
    for (x, z, sk) in ((3445, 760, 'tourist_a'), (3470, 805, 'tourist_b'), (3420, 800, 'tourist_c'), (3438, 740, 'tourist_d')):
        w.npc(x, G + 1, z, 'tourist', sk, '観光客', 'tourists', 'wander', yaw=0, radius=8)
    w.area('harborland', '神戸ハーバーランド', '海辺の街', (3330, 55, 706, 3560, 120, 812), 'spot.harborland')
    w.viewpoint('harborland', 3440, 80, 760, 180, 10, time=12500)


# ===================================================================== Meriken Park
def meriken(w, rnd):
    x1, x2, z1, z2 = 3585, 3722, 760, 905
    # inlet between Harborland and Meriken (water)
    for x in range(3545, 3585):
        for z in range(766, 813):
            w.clear(x, 54, z, x, G + 3, z)
            w.fill(x, 48, z, x, 53, z, 'minecraft:sand')
            w.fill(x, 54, z, x, 62, z, 'minecraft:water')
    for z in range(766, 813):
        w.fill(3544, 54, z, 3544, G, z, 'minecraft:stone_bricks')
        w.fill(3585, 54, z, 3585, G, z, 'minecraft:stone_bricks')
    # footbridge over the inlet
    for x in range(3544, 3586):
        for z in range(768, 772):
            w.set(x, G, z, 'tabi:hinoki')
        w.set(x, G + 1, 767, 'tabi:railing')
        w.set(x, G + 1, 772, 'tabi:railing')
    # the pier (reclaimed land into the harbour)
    for x in range(x1, x2 + 1):
        for z in range(760, z2 + 1):
            w.fill(x, 46, z, x, G - 1, z, 'minecraft:stone')
            w.set(x, G, z, 'minecraft:grass_block')
            w.clear(x, G + 1, z, x, G + 8, z)
    for x in range(x1, x2 + 1):
        w.fill(x, 46, z2 + 1, x, G, z2 + 1, 'minecraft:stone_bricks')
        w.set(x, G + 1, z2 + 1, 'tabi:railing')
    for z in range(812, z2 + 1):
        w.fill(x2 + 1, 46, z, x2 + 1, G, z, 'minecraft:stone_bricks')
        w.set(x2 + 1, G + 1, z, 'tabi:railing')
        w.fill(x1 - 1, 46, z, x1 - 1, G, z, 'minecraft:stone_bricks')
    # paths
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if (z - 770) % 40 < 5 or (x - 3600) % 45 < 4 or z >= z2 - 8:
                w.set(x, G, z, 'tabi:sidewalk' if (x + z) % 7 else 'minecraft:smooth_stone')
    for x in range(x1 + 2, x2, 12):
        for z in range(z1 + 2, z2 - 10, 12):
            if rnd.random() < 0.35 and w.get(x, G, z) == 'minecraft:grass_block':
                tree(w, x, G + 1, z, rnd.choice(['broad', 'pine']), size=0.8)
    for x in range(x1 + 4, x2, 14):
        street_lamp(w, x, G + 1, z2 - 2, 'north')
        bench(w, x + 3, G + 1, z2 - 3, 'south')
    port_tower(w, 3612, 800)
    maritime_museum(w)
    be_kobe(w, 3650, 897)
    quake_memorial(w)
    # crepe truck
    cx, cz = 3628, 880
    w.fill(cx, G + 1, cz, cx + 5, G + 3, cz + 2, 'minecraft:pink_concrete')
    w.fill(cx + 1, G + 2, cz + 2, cx + 4, G + 2, cz + 2, AIR)
    w.fill(cx, G + 4, cz, cx + 5, G + 4, cz + 2, 'minecraft:white_concrete')
    for x in (cx, cx + 5):
        w.set(x, G, cz - 1 + 0, 'minecraft:black_concrete')
    w.sign(cx + 1, G + 5, cz + 2, 'tabi:shop_sign', 'south', ['クレープ', 'CREPE'], style='shop', span=4)
    for k in range(1, 4):
        w.set(cx + 1 + k, G + 5, cz + 2, facing(TB('shop_sign'), 'south'))
    w.npc(cx + 2, G + 1, cz + 3, 'meriken_crepe', 'crepe_girl', 'クレープ屋さん', '', 'stand', yaw=0)
    w.area('meriken', 'メリケンパーク', '神戸港', (x1, 55, 758, x2, 120, z2), 'spot.meriken_park')
    reserve(3540, 758, 3725, 910)
    w.viewpoint('meriken_day', 3655, 74, 930, 160, 5, time=6000, mode='spectator')
    w.viewpoint('port_tower_night', 3650, 90, 860, 160, -25, time=15000, mode='spectator')


def port_tower(w, cx, cz):
    """神戸ポートタワー: hyperboloid lattice of 32 red pipes, 108 m, observation floors on top"""
    # podium (3 storeys)
    for (x, z) in disc(cx, cz, 13.5):
        w.fill(x, G, z, x, G + 8, z, 'minecraft:white_concrete')
    for (x, z) in disc(cx, cz, 12.5):
        w.fill(x, G + 1, z, x, G + 7, z, AIR)
        w.set(x, G, z, 'minecraft:polished_andesite')
        w.set(x, G + 4, z, 'minecraft:smooth_stone')
    for (x, z) in ring(cx, cz, 13.2):
        for y in (G + 2, G + 3, G + 6):
            w.set(x, y, z, 'minecraft:light_blue_stained_glass')
    for (x, z) in disc(cx, cz, 3):
        if (x - cx, z - cz) != (0, 0):
            pass
    # entrance on the north side
    w.fill(cx - 2, G + 1, cz - 14, cx + 2, G + 3, cz - 12, AIR)
    w.sign(cx - 2, G + 5, cz - 14, 'tabi:info_board', 'north', ['神戸ポートタワー', 'KOBE PORT TOWER'], style='info', span=5)
    for k in range(1, 5):
        w.set(cx - 2 + k, G + 5, cz - 14, facing(TB('info_board'), 'north'))
    # core shaft
    for (x, z) in disc(cx, cz, 2.6):
        w.fill(x, G + 1, z, x, G + 104, z, 'minecraft:white_concrete')
    # lattice
    y0, y1, ytop = G + 9, G + 92, G + 104
    R0, R1, phi = 11.5, 7.5, math.radians(100)
    def pt(th, R, y):
        return (cx + 0.5 + R * math.cos(th), y, cz + 0.5 + R * math.sin(th))
    tmax = (ytop - y0) / (y1 - y0)
    for k in range(16):
        th = k * math.tau / 16
        for sgn in (1, -1):
            a = pt(th, R0, y0)
            b = pt(th + sgn * phi, R1, y1)
            end = (a[0] + (b[0] - a[0]) * tmax, a[1] + (b[1] - a[1]) * tmax, a[2] + (b[2] - a[2]) * tmax)
            line3(w, a, end, TB('red_steel'), lit=TB('red_steel_lit'), every=3)
    # horizontal rings (radius of the lattice at height y)
    def radius_at(y):
        t = (y - y0) / (y1 - y0)
        a = pt(0, R0, y0)
        b = pt(phi, R1, y1)
        x = a[0] + (b[0] - a[0]) * t - cx - 0.5
        z = a[2] + (b[2] - a[2]) * t - cz - 0.5
        return math.hypot(x, z)
    for y in range(y0, ytop + 1, 12):
        for (x, z) in ring(cx, cz, radius_at(y), 1.0):
            w.set(x, y, z, TB('red_steel'))
    # observation floors (3) + rooftop deck
    floors = (G + 93, G + 97, G + 101)
    for fy in floors:
        r = radius_at(fy) - 0.8
        for (x, z) in disc(cx, cz, r):
            w.set(x, fy, z, 'minecraft:white_concrete')
            w.clear(x, fy + 1, z, x, fy + 3, z)
        for (x, z) in ring(cx, cz, r, 1.0):
            for y in range(fy + 1, fy + 4):
                w.set(x, y, z, 'minecraft:light_gray_stained_glass')
        for (x, z) in disc(cx, cz, 2.6):
            w.fill(x, fy + 1, z, x, fy + 3, z, 'minecraft:white_concrete')
        for a in range(0, 360, 45):
            x = int(round(cx + (r - 2) * math.cos(math.radians(a))))
            z = int(round(cz + (r - 2) * math.sin(math.radians(a))))
            w.set(x, fy + 3, z, 'minecraft:sea_lantern')
    rt = ytop + 1
    r = radius_at(rt) - 0.5
    for (x, z) in disc(cx, cz, r):
        w.set(x, rt, z, 'minecraft:smooth_stone')
        w.clear(x, rt + 1, z, x, rt + 4, z)
    for (x, z) in ring(cx, cz, r, 1.0):
        w.set(x, rt + 1, z, 'tabi:railing')
    w.set(cx, rt + 1, cz, 'minecraft:red_concrete')
    w.set(cx, rt + 2, cz, 'minecraft:redstone_lamp[lit=true]')
    # elevator buttons + warps
    top_floor = floors[-1]
    w.set(cx + 3, G + 2, cz, facing(TB('elevator_button'), 'east'))
    w.set(cx + 3, top_floor + 2, cz, facing(TB('elevator_button'), 'east'))
    w.set(cx - 3, top_floor + 2, cz, facing(TB('elevator_button'), 'west'))
    w.meta['warps'] += [
        {'pos': [cx + 3, G + 2, cz], 'to': [cx + 4.5, top_floor + 1, cz + 0.5, -90], 'fee': 500, 'label': '展望フロアへ（子ども 500円）', 'journal': 'spot.port_tower_top'},
        {'pos': [cx + 3, top_floor + 2, cz], 'to': [cx + 4.5, G + 1, cz + 0.5, -90], 'fee': 0, 'label': '1階へおります'},
        {'pos': [cx - 3, top_floor + 2, cz], 'to': [cx + 0.5 - 4, rt + 1, cz + 0.5, 90], 'fee': 0, 'label': '屋上デッキへ', 'journal': 'spot.port_tower_top'},
    ]
    w.sign(cx - 4, top_floor + 2, cz + 2, 'tabi:info_board', 'west', ['展望フロア', '地上約100m', '六甲山・神戸港が見えるよ'], style='info')
    w.npc(cx + 5, G + 1, cz - 3, 'tower_staff', 'tower_staff', 'ポートタワーの係員', '', 'stand', yaw=0)
    w.area('port_tower', '神戸ポートタワー', '高さ108m', (cx - 14, 60, cz - 15, cx + 14, G + 90, cz + 14), 'spot.port_tower')
    w.area('port_tower_top', 'ポートタワー展望フロア', '地上約100m', (cx - 12, G + 92, cz - 12, cx + 12, G + 112, cz + 12), 'spot.port_tower_top')
    w.check(cx + 3, G + 2, cz, 'port tower elevator')
    w.check(int(cx + 0.5 + R0 * math.cos(0)), y0, int(cz + 0.5 + R0 * math.sin(0)), 'port tower lattice')
    w.viewpoint('port_tower_view', cx + 4.5, top_floor + 2.6, cz + 0.5, -40, 15, time=9000, mode='spectator')


def maritime_museum(w):
    x1, x2, z1, z2 = 3640, 3700, 816, 846
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:polished_andesite')
    w.fill(x1, G + 1, z1, x2, G + 7, z2, pane('minecraft:light_blue_stained_glass_pane', e=True, w=True))
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 6, z2 - 1, AIR)
    for x in range(x1, x2 + 1, 5):
        w.fill(x, G + 1, z1, x, G + 7, z1, TB('white_steel'))
        w.fill(x, G + 1, z2, x, G + 7, z2, TB('white_steel'))
    w.fill(x1, G + 8, z1, x2, G + 8, z2, TB('white_steel'))
    w.fill(x1 + 20, G + 1, z1, x1 + 24, G + 4, z1, AIR)
    # white space-frame "sails" above the roof
    for k, (sx, h) in enumerate(((x1 + 6, 30), (x1 + 24, 36), (x1 + 44, 26))):
        for zz in range(z1 + 2, z2 - 1, 4):
            for i in range(0, h + 1):
                t = i / h
                x = sx + int(round(14 * t * t))
                y = G + 9 + i
                w.set(x, y, zz, TB('white_steel'))
            line3(w, (sx, G + 9, zz), (sx + 14, G + 9, zz), TB('white_steel'))
        for i in range(0, h + 1, 3):
            t = i / h
            x = sx + int(round(14 * t * t))
            for zz in range(z1 + 2, z2 - 1):
                w.set(x, G + 9 + i, zz, TB('white_steel'))
    w.sign(x1 + 19, G + 5, z1 - 1, 'tabi:info_board', 'north', ['神戸海洋博物館', 'KOBE MARITIME MUSEUM'], style='info', span=7)
    for k in range(1, 7):
        w.set(x1 + 19 + k, G + 5, z1 - 1, facing(TB('info_board'), 'north'))
    # exhibits: a ship model (boat-shaped blocks) inside
    w.fill(x1 + 10, G + 1, z1 + 12, x1 + 30, G + 1, z1 + 16, 'minecraft:white_concrete')
    w.fill(x1 + 12, G + 2, z1 + 13, x1 + 28, G + 2, z1 + 15, 'minecraft:spruce_planks')
    w.fill(x1 + 20, G + 3, z1 + 14, x1 + 20, G + 5, z1 + 14, 'minecraft:spruce_fence')
    w.npc(x1 + 22, G + 1, z1 + 2, 'museum_staff', 'museum_staff', '海洋博物館の係員', 'museum', 'stand', yaw=180)
    w.area('maritime_museum', '神戸海洋博物館', '白いフレームの建物', (x1 - 2, 60, z1 - 3, x2 + 2, G + 50, z2 + 2), 'spot.maritime_museum')


def be_kobe(w, cx, z):
    text = 'BE KOBE'
    scale = 1
    width = len(text) * 4 - 1
    # readable from the north (viewer looks south: their right is -x) -> first letter at the highest x
    x = cx + width // 2
    w.fill(cx - width // 2 - 2, G, z - 2, cx + width // 2 + 2, G, z + 2, 'minecraft:smooth_stone')
    for ch in text:
        glyph = FONT[ch]
        for row, bits in enumerate(glyph):
            for col, bit in enumerate(bits):
                if bit == '1':
                    w.set(x - col, G + 5 - row, z, 'minecraft:white_concrete')
        x -= 4
    w.area('be_kobe', 'BE KOBE', 'メリケンパーク', (cx - 16, 60, z - 8, cx + 16, 80, z + 6), 'spot.be_kobe')
    w.viewpoint('be_kobe', cx, G + 4, z - 14, 0, 5, time=7000, mode='spectator')


def quake_memorial(w):
    x1, x2, z1, z2 = 3586, 3604, 826, 886
    r = random.Random(1995)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            drop = 1 if (x - x1) > 8 and r.random() < 0.7 else (2 if (x - x1) > 13 and r.random() < 0.5 else 0)
            w.clear(x, G - 2, z, x, G + 3, z)
            w.fill(x, 46, z, x, G - 1 - drop, z, 'minecraft:stone')
            w.set(x, G - drop, z, r.choice(['minecraft:cracked_stone_bricks', 'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:andesite']))
    for z in range(z1, z2 + 1, 12):
        x = x1 + 14 + r.randrange(3)
        w.set(x, G - 1, z, TB('lamp_pole'))
        w.set(x, G, z, TB('lamp_pole'))
        w.set(x + 1, G + 1, z, TB('lamp_pole'))
        w.set(x + 1, G + 2, z, facing(TB('street_lamp'), 'east'))
    for z in range(z1, z2 + 1):
        w.set(x1 - 1 + 0, G + 1, z, 'minecraft:iron_bars') if False else None
    for z in range(z1, z2 + 1):
        w.set(x2 + 1, G + 1, z, 'tabi:railing')
    w.sign(x2 + 2, G + 2, 840, 'tabi:info_board', 'east', ['神戸港震災メモリアルパーク', '1995年1月17日 午前5時46分', '阪神・淡路大震災で被災した', 'メリケン波止場の一部を保存'], style='info')
    w.set(x2 + 2, G + 1, 840, TB('sign_pole'))
    w.fill(x2 + 3, G + 1, 852, x2 + 4, G + 2, 853, 'minecraft:polished_granite')
    w.npc(x2 + 3, G + 1, 846, 'memorial_guide', 'guide_old', 'メモリアルパークのガイドさん', '', 'stand', yaw=-90)
    w.area('quake_memorial', '神戸港震災メモリアルパーク', '1995年1月17日', (x1 - 1, 55, z1 - 2, x2 + 6, 80, z2 + 2), 'spot.quake_memorial')


# ===================================================================== Nankinmachi (中華街)
def cn_gate(w, x, z, axis_='z', span=12, h=9, name='長安門', face='east'):
    """Chinese gate across a street: red pillars, gold beams, green tiled double roof, plaque"""
    if axis_ == 'z':      # gate spans along z (street runs east-west)
        za, zb = z - span // 2, z + span // 2
        for zz in (za, za + 3, zb - 3, zb):
            w.fill(x, G + 1, zz, x, G + h, zz, axis(TB('shu_pillar'), 'y'))
            w.set(x, G, zz, 'minecraft:polished_granite')
        w.fill(x, G + h - 2, za, x, G + h - 2, zb, TB('china_gold'))
        w.fill(x, G + h - 1, za, x, G + h - 1, zb, TB('shu_block'))
        w.fill(x, G + h + 2, za + 1, x, G + h + 2, zb - 1, TB('shu_block'))
        for k, (yy, ext) in enumerate(((G + h, 2), (G + h + 3, 1))):
            for zz in range(za - ext, zb + ext + 1):
                w.set(x - 1, yy, zz, stairs(TB('green_kawara_stairs'), 'east'))
                w.set(x + 1, yy, zz, stairs(TB('green_kawara_stairs'), 'west'))
                w.set(x, yy, zz, TB('green_kawara'))
            w.set(x, yy + 1, za - ext, TB('china_gold'))
            w.set(x, yy + 1, zb + ext, TB('china_gold'))
        w.fill(x, G + h + 4, za + 1, x, G + h + 4, zb - 1, slab(TB('green_kawara_slab')))
        w.sign(x + (1 if face == 'east' else -1), G + h - 3, z - 1, 'tabi:gate_plaque', face, [name], style='gate', span=3, glow=True)
        for k in range(1, 3):
            w.set(x + (1 if face == 'east' else -1), G + h - 3, z - 1 + k * (1 if face == 'west' else -1) * -1, facing(TB('gate_plaque'), face))
        for zz in (za + 1, zb - 1):
            w.set(x, G + h - 3, zz, TB('chochin'))
    else:                 # gate spans along x (street runs north-south)
        xa, xb = x - span // 2, x + span // 2
        for xx in (xa, xa + 3, xb - 3, xb):
            w.fill(xx, G + 1, z, xx, G + h, z, axis(TB('shu_pillar'), 'y'))
        w.fill(xa, G + h - 2, z, xb, G + h - 2, z, TB('china_gold'))
        w.fill(xa, G + h - 1, z, xb, G + h - 1, z, TB('shu_block'))
        w.fill(xa + 1, G + h + 2, z, xb - 1, G + h + 2, z, TB('shu_block'))
        for (yy, ext) in ((G + h, 2), (G + h + 3, 1)):
            for xx in range(xa - ext, xb + ext + 1):
                w.set(xx, yy, z - 1, stairs(TB('green_kawara_stairs'), 'south'))
                w.set(xx, yy, z + 1, stairs(TB('green_kawara_stairs'), 'north'))
                w.set(xx, yy, z, TB('green_kawara'))
        w.fill(xa + 1, G + h + 4, z, xb - 1, G + h + 4, z, slab(TB('green_kawara_slab')))
        w.sign(x + 1, G + h - 3, z + (1 if face == 'south' else -1), 'tabi:gate_plaque', face, [name], style='gate', span=3, glow=True)
        for k in (1, 2):
            w.set(x + 1 - k if face == 'south' else x + 1 + k, G + h - 3, z + (1 if face == 'south' else -1), facing(TB('gate_plaque'), face))


def nankinmachi(w, rnd):
    x1, x2, z1, z2 = 3600, 3700, 600, 650
    # streets: E-W main street z 612..618, N-S street x 3647..3653
    for x in range(x1, x2 + 1):
        for z in range(611, 620):
            w.set(x, G, z, 'minecraft:polished_granite' if (x + z) % 2 else 'minecraft:smooth_stone')
            w.clear(x, G + 1, z, x, G + 8, z)
    for z in range(z1, z2 + 6):
        for x in range(3646, 3655):
            w.set(x, G, z, 'minecraft:polished_granite' if (x + z) % 2 else 'minecraft:smooth_stone')
            w.clear(x, G + 1, z, x, G + 8, z)
    # shop buildings along the streets
    colors = ['tabi:shu_block', 'minecraft:red_terracotta', 'minecraft:yellow_terracotta', 'tabi:tile_white', 'minecraft:white_terracotta']
    names = ['中華料理 金龍', '福来 豚まん', '中国茶 青龍', '飯店 鳳凰', '雑貨 熊猫', '点心 花月', '中華菓子 喜来', '飯店 桃園', '中華料理 海龍', '甘栗 天津']
    k = 0
    for side in ('n', 's'):
        for xa in range(x1 + 2, x2 - 6, 12):
            if 3640 <= xa <= 3655:
                continue
            if side == 'n':
                za, zb = 600, 610
                f = 'south'
            else:
                za, zb = 621, 631
                f = 'north'
            xb = xa + 10
            wall = colors[k % len(colors)]
            hgt = 9 + (k % 3) * 3
            w.fill(xa, G, za, xb, G, zb, 'minecraft:smooth_stone')
            w.fill(xa, G + 1, za, xb, G + hgt, zb, wall)
            w.fill(xa + 1, G + 1, za + 1, xb - 1, G + hgt - 1, zb - 1, AIR)
            fz = zb if side == 'n' else za
            w.fill(xa + 1, G + 1, fz, xb - 1, G + 3, fz, AIR)
            w.fill(xa + 1, G + 6, fz, xb - 1, G + 7, fz, pane('minecraft:red_stained_glass_pane', e=True, w=True))
            for xx in range(xa, xb + 1):
                w.set(xx, G + 4, fz + (1 if side == 'n' else -1), stairs(TB('green_kawara_stairs'), 'north' if side == 'n' else 'south'))
            nm = names[k % len(names)]
            w.sign(xa + 2, G + 5, fz + (1 if side == 'n' else -1), 'tabi:neon_sign' if k % 2 else 'tabi:gate_plaque', f, [nm], style='neon' if k % 2 else 'gate', span=7, glow=True)
            for j in range(1, 7):
                w.set(xa + 2 + j, G + 5, fz + (1 if side == 'n' else -1), facing(TB('neon_sign' if k % 2 else 'gate_plaque'), f))
            # stall counter with steamers
            cz = fz + (2 if side == 'n' else -2)
            for xx in range(xa + 2, xb - 1):
                w.set(xx, G + 1, fz, 'minecraft:spruce_planks' if xx % 3 else 'minecraft:spruce_planks')
            w.set(xa + 3, G + 2, fz, TB('steamer'))
            w.set(xa + 5, G + 2, fz, TB('steamer'))
            w.set(xa + 5, G + 3, fz, TB('steamer'))
            flat_roof(w, xa, za, xb, zb, G + hgt, 'minecraft:smooth_stone', wall)
            k += 1
    # lanterns strung across the streets
    for x in range(x1 + 2, x2 - 1, 3):
        for z in range(611, 620):
            w.set(x, G + 7, z, 'minecraft:chain[axis=z,waterlogged=false]' if z not in (612, 615, 618) else TB('chochin'))
    for z in range(z1 + 2, z2 + 4, 3):
        for x in range(3646, 3655):
            w.set(x, G + 7, z, 'minecraft:chain[axis=x,waterlogged=false]' if x not in (3647, 3650, 3653) else TB('chochin'))
    # gates
    cn_gate(w, x2 + 1, 615, 'z', 12, 9, '長安門', 'east')
    cn_gate(w, x1 - 1, 615, 'z', 12, 8, '西安門', 'west')
    cn_gate(w, 3650, z2 + 3, 'x', 10, 8, '海栄門', 'south')
    # plaza pavilion (あずまや) + zodiac pedestals
    px, pz = 3650, 615
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        w.fill(px + dx, G + 1, pz + dz, px + dx, G + 5, pz + dz, axis(TB('shu_pillar'), 'y'))
    w.fill(px - 2, G + 5, pz - 2, px + 2, G + 5, pz + 2, TB('china_gold'))
    hip_roof(w, px - 2, pz - 2, px + 2, pz + 2, G + 6, TB('green_kawara'), overhang=1)
    w.set(px, G + 9, pz, TB('china_gold'))
    for i in range(12):
        a = i * math.tau / 12
        x = int(round(px + 6.5 * math.cos(a)))
        z = int(round(pz + 3.8 * math.sin(a)))
        w.set(x, G + 1, z, 'minecraft:polished_andesite')
        w.set(x, G + 2, z, 'minecraft:stone_button[face=floor,facing=north,powered=false]') if False else w.set(x, G + 2, z, 'minecraft:chiseled_stone_bricks')
    w.sign(px + 4, G + 2, pz + 4, 'tabi:info_board', 'south', ['南京町広場', '十二支の像とあずまや'], style='info')
    w.set(px + 4, G + 1, pz + 4, TB('sign_pole'))
    # shopkeepers
    w.npc(3631, G + 1, 608, 'nankin_butaman', 'chinese_vendor', '豚まん屋の店員さん', '', 'stand', yaw=0)
    for i in range(4):
        w.npc(3627 + i, G + 1, 613, 'tourist', rnd.choice(['tourist_a', 'tourist_b', 'tourist_c', 'tourist_d', 'student']), '並んでいる人', 'tourists', 'stand', yaw=180)
    w.npc(3656, G + 1, 620, 'nankin_dimsum', 'dimsum_cook', '点心屋台の店主', '', 'stand', yaw=180)
    w.fill(3655, G + 1, 621, 3659, G + 1, 621, 'minecraft:spruce_planks')
    w.set(3656, G + 2, 621, TB('steamer'))
    w.set(3658, G + 2, 621, TB('steamer'))
    for (x, z) in ((3620, 615), (3670, 614), (3650, 635), (3685, 616)):
        w.npc(x, G + 1, z, 'nankin_person', rnd.choice(['townsman', 'townswoman', 'tourist_b', 'tourist_c']), '南京町の人', 'nankin', 'wander', yaw=0, radius=7)
    reserve(x1 - 4, z1, x2 + 4, z2 + 6)
    w.area('nankinmachi', '南京町', '神戸の中華街', (x1 - 3, 55, z1 - 1, x2 + 3, 100, z2 + 6), 'spot.nankinmachi')
    w.area('choanmon', '長安門', '南京町の東の門', (x2 - 2, 55, 607, x2 + 6, 100, 623), 'spot.choanmon')
    w.viewpoint('nankinmachi', 3712, 72, 615, 90, 8, time=13000, mode='spectator')
    w.check(x2 + 1, G + 1, 609, 'choanmon pillar')


# ===================================================================== Motomachi shotengai (arcade)
def motomachi(w, rnd):
    x1, x2 = 3500, 3760
    zs1, zs2 = 559, 565          # street
    for x in range(x1, x2 + 1):
        for z in range(zs1, zs2 + 1):
            w.set(x, G, z, 'minecraft:smooth_stone' if (x // 4 + z) % 2 else 'minecraft:polished_diorite')
            w.clear(x, G + 1, z, x, G + 10, z)
    special = {3520: ('motomachi_kissa', '元町珈琲 さざなみ', '自家焙煎珈琲'), 3556: ('motomachi_sweets', '元町洋菓子 アカシア', 'バウムクーヘン・プリン'),
               3592: ('motomachi_bakery', 'パン工房 こむぎ堂', '焼きたてパン'), 3628: ('motomachi_souvenir', '神戸みやげ 港屋', '瓦せんべい・洋菓子'),
               3664: ('sobameshi', 'そばめし 長田屋', '長田名物'), 3700: ('wagyu', '和牛串 ほむら', '炭火焼')}
    others = ['靴 マリン', '帽子 コウベ', '時計 元町堂', '雑貨 ハーバー', '本 海文堂', '洋装 リリー', '眼鏡 光学堂', '紅茶 キタノ', '画材 みなと', '真珠 アコヤ', '帆布 かばん', '楽器 ソナタ']
    skins = {'motomachi_kissa': ('master', '元町珈琲のマスター'), 'motomachi_sweets': ('patissier', '洋菓子店のパティシエ'),
             'motomachi_bakery': ('baker', 'パン屋さん'), 'motomachi_souvenir': ('shopkeeper_old', '港屋の店主'),
             'sobameshi': ('chef', '長田屋の店主'), 'wagyu': ('chef', '和牛串屋の店主')}
    oi = 0
    for xa in range(x1 + 2, x2 - 10, 12):
        for side in ('n', 's'):
            za, zb = (548, 558) if side == 'n' else (566, 576)
            xb = xa + 11
            f = 'south' if side == 'n' else 'north'
            fz = zb if side == 'n' else za
            wall = rnd.choice(['tabi:tile_white', 'tabi:tile_cream', 'tabi:tile_brown', 'minecraft:white_concrete', 'tabi:renga', 'minecraft:smooth_sandstone'])
            hgt = rnd.choice([9, 12, 15])
            w.fill(xa, G, za, xb, G, zb, 'minecraft:smooth_stone')
            w.fill(xa, G + 1, za, xb, G + hgt, zb, wall)
            w.fill(xa + 1, G + 1, za + 1, xb - 1, G + hgt - 1, zb - 1, AIR)
            w.fill(xa + 1, G + 1, fz, xb - 1, G + 3, fz, pane('minecraft:glass_pane', e=True, w=True))
            w.fill(xa + 4, G + 1, fz, xa + 6, G + 2, fz, AIR)
            for fl in range(1, hgt // 3):
                for xx in range(xa + 1, xb, 2):
                    w.set(xx, G + 2 + fl * 3, fz, 'tabi:window_lit' if rnd.random() < 0.5 else 'tabi:window_dark')
            sp = special.get(xa) if side == 'n' else None
            if sp:
                prof, nm, sub = sp
                sk, dn = skins[prof]
                w.npc(xa + 5, G + 1, (za + zb) // 2, prof, sk, dn, '', 'stand', yaw=0 if side == 'n' else 180)
                inside_shop(w, prof, xa, za, xb, zb, side)
            else:
                nm, sub = others[oi % len(others)], ''
                oi += 1
                w.set(xa + 2, G + 1, za + (2 if side == 'n' else 8), facing(TB('souvenir_shelf'), f))
                w.set(xa + 8, G + 1, za + (2 if side == 'n' else 8), facing(TB('souvenir_shelf'), f))
            sz = fz + (1 if side == 'n' else -1)
            w.sign(xa + 1, G + 4, sz, 'tabi:shop_sign', f, [nm] + ([sub] if sub else []), style='shop', span=10)
            for j in range(1, 10):
                w.set(xa + 1 + j, G + 4, sz, facing(TB('shop_sign'), f))
            flat_roof(w, xa, za, xb, zb, G + hgt, 'minecraft:smooth_stone', wall)
    # arcade roof
    for x in range(x1, x2 + 1):
        for z in range(zs1, zs2 + 1):
            w.set(x, G + 10, z, 'minecraft:white_stained_glass' if zs1 < z < zs2 else 'minecraft:smooth_stone')
        if x % 6 == 0:
            for z in range(zs1, zs2 + 1):
                w.set(x, G + 10, z, 'minecraft:smooth_stone')
            w.set(x, G + 9, 562, 'minecraft:lantern[hanging=true,waterlogged=false]')
    for xe in (x1, x2):
        f = 'west' if xe == x1 else 'east'
        w.sign(xe, G + 8, 560, 'tabi:shop_sign', f, ['元町商店街', 'MOTOMACHI'], style='shop', span=5)
        for j in range(1, 5):
            w.set(xe, G + 8, 560 + j, facing(TB('shop_sign'), f))
    for (x, sk) in ((3530, 'townswoman'), (3580, 'salaryman'), (3620, 'tourist_d'), (3680, 'student'), (3720, 'tourist_b')):
        w.npc(x, G + 1, 562, 'shopper', sk, '買い物客', 'motomachi', 'wander', yaw=0, radius=12)
    reserve(x1 - 2, 546, x2 + 2, 578)
    w.area('motomachi', '元町商店街', '1874年からつづく商店街', (x1, 55, 546, x2, 90, 578), 'spot.motomachi')
    w.viewpoint('motomachi', 3505, 70, 562, -90, 5, time=8000, mode='spectator')


def inside_shop(w, prof, xa, za, xb, zb, side):
    if prof == 'motomachi_kissa':
        w.fill(xa + 1, G, za + 1, xb - 1, G, zb - 1, 'minecraft:dark_oak_planks')
        w.fill(xa + 2, G + 1, za + 2, xa + 8, G + 1, za + 2, 'minecraft:dark_oak_planks')
        for x in (xa + 3, xa + 7):
            w.set(x, G + 1, za + 6, 'minecraft:dark_oak_fence')
            w.set(x, G + 2, za + 6, 'minecraft:dark_oak_pressure_plate')
            w.set(x, G + 1, za + 5, stairs('minecraft:dark_oak_stairs', 'south'))
            w.set(x, G + 1, za + 7, stairs('minecraft:dark_oak_stairs', 'north'))
        w.set(xa + 4, G + 2, za + 2, 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=false]')
    elif prof == 'motomachi_sweets':
        for x in range(xa + 2, xb - 1, 2):
            w.set(x, G + 1, za + 3, facing(TB('cake_showcase'), 'south'))
    elif prof == 'motomachi_bakery':
        for x in range(xa + 1, xb):
            w.set(x, G + 1, za + 1, facing(TB('bread_shelf'), 'south'))
            w.set(x, G + 2, za + 1, facing(TB('bread_shelf'), 'south'))
    elif prof == 'motomachi_souvenir':
        for x in range(xa + 1, xb):
            w.set(x, G + 1, za + 1, facing(TB('souvenir_shelf'), 'south'))
            w.set(x, G + 2, za + 1, facing(TB('souvenir_shelf'), 'south'))
        w.fill(xa + 3, G + 1, za + 5, xa + 7, G + 1, za + 5, TB('souvenir_shelf') + '[facing=south]')
    elif prof in ('sobameshi', 'wagyu'):
        w.fill(xa + 2, G + 1, za + 3, xb - 2, G + 1, za + 3, 'minecraft:spruce_planks')
        w.set(xa + 3, G + 1, za + 1, 'minecraft:campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]' if prof == 'wagyu' else 'minecraft:smoker[facing=south,lit=true]')
        for x in range(xa + 2, xb - 1, 2):
            w.set(x, G + 1, za + 4, 'minecraft:red_wool' if prof == 'sobameshi' else 'minecraft:black_wool')
        if prof == 'wagyu':
            w.set(xa + 6, G + 3, zb, TB('chochin'))


# ===================================================================== Kitano ijinkan
def kitano(w, rnd):
    # Kitano-zaka: stepped street from the city up the slope
    pts = fillet_path([(3742, 548), (3742, 400)], 6)
    def y_at(i):
        z = pts[i][1]
        return float(w.ground(3742, int(z)))
    road(w, pts, G, width=6, center=None, sidewalk=1, y_at=y_at, walk='minecraft:bricks')
    for z in range(404, 545, 8):
        y = w.ground(3738, z)
        street_lamp(w, 3738, y + 1, z, 'east', kind='gas_lamp', height=4)
        tree(w, 3747, w.ground(3747, z) + 1, z + 4, 'broad', size=0.7)
    reserve(3735, 400, 3750, 548)
    # 風見鶏の館 (red brick, spire with the weathercock)
    kx1, kx2, kz1, kz2 = 3712, 3732, 408, 424
    gy = max(w.ground(x, z) for x in range(kx1, kx2 + 1) for z in range(kz1, kz2 + 4))
    w.fill(kx1 - 2, gy - 6, kz1 - 2, kx2 + 2, gy, kz2 + 8, 'minecraft:stone_bricks')
    w.fill(kx1 - 2, gy, kz2 + 1, kx2 + 2, gy, kz2 + 8, 'minecraft:bricks')
    w.clear(kx1 - 2, gy + 1, kz1 - 2, kx2 + 2, gy + 30, kz2 + 8)
    w.fill(kx1, gy + 1, kz1, kx2, gy + 10, kz2, TB('renga'))
    w.fill(kx1 + 1, gy + 1, kz1 + 1, kx2 - 1, gy + 9, kz2 - 1, AIR)
    w.fill(kx1 + 1, gy, kz1 + 1, kx2 - 1, gy, kz2 - 1, 'minecraft:dark_oak_planks')
    w.fill(kx1 + 1, gy + 5, kz1 + 1, kx2 - 1, gy + 5, kz2 - 1, 'minecraft:dark_oak_planks')
    for x in range(kx1 + 2, kx2 - 1, 3):
        for y in (gy + 2, gy + 7):
            w.fill(x, y, kz2, x, y + 1, kz2, pane(TB('sash_window'), e=True, w=True))
            w.fill(x, y, kz1, x, y + 1, kz1, pane(TB('sash_window'), e=True, w=True))
    for z in range(kz1 + 2, kz2 - 1, 3):
        for y in (gy + 2, gy + 7):
            w.fill(kx1, y, z, kx1, y + 1, z, pane(TB('sash_window'), n=True, s=True))
            w.fill(kx2, y, z, kx2, y + 1, z, pane(TB('sash_window'), n=True, s=True))
    # half-timbered gable on the upper floor (front)
    for x in range(kx1, kx2 + 1, 2):
        w.set(x, gy + 10, kz2, 'minecraft:dark_oak_log[axis=y]')
    w.fill(3721, gy + 1, kz2, 3723, gy + 3, kz2, AIR)
    w.set(3721, gy + 1, kz2, 'minecraft:dark_oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    w.set(3721, gy + 2, kz2, 'minecraft:dark_oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    hip_roof(w, kx1, kz1, kx2, kz2, gy + 11, 'minecraft:deepslate_tile', overhang=1)
    # spire tower at the front-left corner with the weathercock
    tx1, tz1 = kx1 - 1, kz2 - 3
    w.fill(tx1, gy + 1, tz1, tx1 + 4, gy + 16, tz1 + 4, TB('renga'))
    w.fill(tx1 + 1, gy + 1, tz1 + 1, tx1 + 3, gy + 15, tz1 + 3, AIR)
    for y in (gy + 7, gy + 12):
        w.set(tx1 + 2, y, tz1 + 4, pane(TB('sash_window'), e=True, w=True))
        w.set(tx1, y, tz1 + 2, pane(TB('sash_window'), n=True, s=True))
    for k in range(4):
        w.fill(tx1 + k // 2 + 0, gy + 17 + k * 2, tz1 + k // 2, tx1 + 4 - k // 2, gy + 18 + k * 2, tz1 + 4 - k // 2, 'minecraft:deepslate_tiles')
    w.fill(tx1 + 2, gy + 25, tz1 + 2, tx1 + 2, gy + 27, tz1 + 2, 'minecraft:iron_bars')
    w.set(tx1 + 2, gy + 28, tz1 + 2, TB('weathercock'))
    w.sign(3724, gy + 2, kz2 + 3, 'tabi:info_board', 'south', ['風見鶏の館（旧トーマス住宅）', '1909年ごろ・ドイツ人貿易商の家', '屋根の上に風見鶏'], style='info')
    w.set(3724, gy + 1, kz2 + 3, TB('sign_pole'))
    w.npc(3719, gy + 1, kz2 + 3, 'kitano_guide', 'info_lady', '異人館のガイドさん', 'kitano', 'stand', yaw=0)
    w.npc(3730, gy + 1, kz2 + 6, 'artist', 'artist', '絵描きのおじさん', 'kitano', 'stand', yaw=180, hold='minecraft:paper')
    w.area('kazamidori', '風見鶏の館', '北野異人館街', (kx1 - 3, gy - 5, kz1 - 3, kx2 + 3, gy + 35, kz2 + 9), 'spot.kazamidori')
    w.check(tx1 + 2, gy + 28, tz1 + 2, 'weathercock')
    # 北野町広場: the little square in front of the house
    for x in range(kx1 - 4, kx2 + 6):
        for z in range(kz2 + 9, kz2 + 20):
            g = w.ground(x, z)
            w.fill(x, g, z, x, gy, z, 'minecraft:stone_bricks')
            w.set(x, gy, z, 'minecraft:bricks' if (x + z) % 3 else 'minecraft:polished_granite')
            w.clear(x, gy + 1, z, x, gy + 10, z)
    for x in range(kx1, kx2 + 1, 5):
        bench(w, x, gy + 1, kz2 + 17, 'north')
    tree(w, kx1 - 2, gy + 1, kz2 + 12, 'broad', size=0.8)
    tree(w, kx2 + 3, gy + 1, kz2 + 12, 'broad', size=0.8)
    w.viewpoint('kazamidori', 3722, gy + 4, kz2 + 19, 180, 8, time=5000, mode='spectator')
    reserve(kx1 - 3, kz1 - 3, kx2 + 3, kz2 + 9)
    # 萌黄の館 (pale green clapboard, verandas with bay windows)
    mx1, mx2, mz1, mz2 = 3756, 3776, 414, 430
    gy2 = max(w.ground(x, z) for x in range(mx1, mx2 + 1) for z in range(mz1, mz2 + 4))
    w.fill(mx1 - 2, gy2 - 6, mz1 - 2, mx2 + 2, gy2, mz2 + 6, 'minecraft:stone_bricks')
    w.fill(mx1 - 2, gy2, mz2 + 1, mx2 + 2, gy2, mz2 + 6, 'minecraft:grass_block')
    w.clear(mx1 - 2, gy2 + 1, mz1 - 2, mx2 + 2, gy2 + 26, mz2 + 6)
    w.fill(mx1, gy2 + 1, mz1, mx2, gy2 + 10, mz2, TB('clapboard_green'))
    w.fill(mx1 + 1, gy2 + 1, mz1 + 1, mx2 - 1, gy2 + 9, mz2 - 1, AIR)
    w.fill(mx1 + 1, gy2, mz1 + 1, mx2 - 1, gy2, mz2 - 1, 'minecraft:spruce_planks')
    w.fill(mx1 + 1, gy2 + 5, mz1 + 1, mx2 - 1, gy2 + 5, mz2 - 1, 'minecraft:spruce_planks')
    for (bx1, bx2) in ((mx1 + 2, mx1 + 6), (mx2 - 6, mx2 - 2)):     # bay windows on both floors
        for y0 in (gy2 + 1, gy2 + 6):
            w.fill(bx1, y0, mz2 + 1, bx2, y0 + 3, mz2 + 2, TB('clapboard_green'))
            w.fill(bx1 + 1, y0 + 1, mz2 + 2, bx2 - 1, y0 + 2, mz2 + 2, pane(TB('sash_window'), e=True, w=True))
            w.fill(bx1 + 1, y0, mz2 + 1, bx2 - 1, y0 + 3, mz2 + 1, AIR)
    w.fill(mx1 + 8, gy2 + 6, mz2 + 1, mx2 - 8, gy2 + 6, mz2 + 3, 'minecraft:white_concrete')    # veranda
    w.fill(mx1 + 8, gy2 + 7, mz2 + 3, mx2 - 8, gy2 + 7, mz2 + 3, 'minecraft:white_stained_glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]')
    w.fill(mx1 + 9, gy2 + 1, mz2, mx1 + 11, gy2 + 3, mz2, AIR)
    for x in range(mx1 + 1, mx2, 3):
        w.set(x, gy2 + 8, mz1, pane(TB('sash_window'), e=True, w=True))
        w.set(x, gy2 + 3, mz1, pane(TB('sash_window'), e=True, w=True))
    hip_roof(w, mx1, mz1, mx2, mz2, gy2 + 11, 'minecraft:deepslate_tile', overhang=1)
    for (x, z) in ((mx1 + 3, mz1 + 3), (mx2 - 3, mz1 + 4)):
        w.fill(x, gy2 + 11, z, x, gy2 + 17, z, 'minecraft:bricks')
    w.sign(mx1 + 13, gy2 + 2, mz2 + 5, 'tabi:info_board', 'south', ['萌黄の館（旧シャープ住宅）', '1903年・アメリカ総領事の家', 'うすみどり色の下見板張り'], style='info')
    w.set(mx1 + 13, gy2 + 1, mz2 + 5, TB('sign_pole'))
    w.npc(mx1 + 10, gy2 + 1, mz2 + 4, 'kitano_guide', 'info_lady', '異人館のガイドさん', 'kitano', 'stand', yaw=0)
    w.area('moegi', '萌黄の館', '北野異人館街', (mx1 - 3, gy2 - 5, mz1 - 3, mx2 + 3, gy2 + 30, mz2 + 7), 'spot.moegi')
    w.viewpoint('moegi', 3766, gy2 + 5, mz2 + 20, 180, 10, time=7000, mode='spectator')
    reserve(mx1 - 3, mz1 - 3, mx2 + 3, mz2 + 7)
    # 北野 洋館カフェ (white clapboard house with a garden terrace)
    cx1, cx2, cz1, cz2 = 3754, 3770, 446, 458
    gy3 = max(w.ground(x, z) for x in range(cx1, cx2 + 1) for z in range(cz1, cz2 + 4))
    w.fill(cx1 - 2, gy3 - 6, cz1 - 2, cx2 + 2, gy3, cz2 + 6, 'minecraft:stone_bricks')
    w.fill(cx1 - 2, gy3, cz2 + 1, cx2 + 2, gy3, cz2 + 6, 'minecraft:bricks')
    w.clear(cx1 - 2, gy3 + 1, cz1 - 2, cx2 + 2, gy3 + 20, cz2 + 6)
    w.fill(cx1, gy3 + 1, cz1, cx2, gy3 + 9, cz2, TB('clapboard_white'))
    w.fill(cx1 + 1, gy3 + 1, cz1 + 1, cx2 - 1, gy3 + 8, cz2 - 1, AIR)
    w.fill(cx1 + 1, gy3, cz1 + 1, cx2 - 1, gy3, cz2 - 1, 'minecraft:dark_oak_planks')
    w.fill(cx1 + 1, gy3 + 5, cz1 + 1, cx2 - 1, gy3 + 5, cz2 - 1, 'minecraft:dark_oak_planks')
    for x in range(cx1 + 2, cx2 - 1, 3):
        w.fill(x, gy3 + 2, cz2, x, gy3 + 3, cz2, pane(TB('sash_window'), e=True, w=True))
        w.fill(x, gy3 + 7, cz2, x, gy3 + 8, cz2, pane(TB('sash_window'), e=True, w=True))
    w.fill(cx1 + 7, gy3 + 1, cz2, cx1 + 9, gy3 + 3, cz2, AIR)
    hip_roof(w, cx1, cz1, cx2, cz2, gy3 + 10, 'minecraft:deepslate_tile', overhang=1)
    for (x, z) in ((cx1 + 2, cz2 + 3), (cx1 + 7, cz2 + 4), (cx2 - 2, cz2 + 3)):
        w.set(x, gy3 + 1, z, 'minecraft:white_concrete_powder' if False else 'minecraft:quartz_pillar[axis=y]')
        w.set(x, gy3 + 2, z, 'minecraft:quartz_slab[type=bottom,waterlogged=false]')
        w.set(x - 1, gy3 + 1, z, stairs('minecraft:dark_oak_stairs', 'east'))
    w.fill(cx1 + 2, gy3 + 1, cz1 + 2, cx1 + 6, gy3 + 1, cz1 + 2, 'minecraft:dark_oak_planks')
    w.set(cx1 + 3, gy3 + 1, cz1 + 3, TB('cake_showcase') + '[facing=south]')
    w.sign(cx1 + 5, gy3 + 4, cz2 + 1, 'tabi:shop_sign', 'south', ['北野 洋館カフェ', 'KITANO CAFE'], style='shop', span=6)
    for j in range(1, 6):
        w.set(cx1 + 5 + j, gy3 + 4, cz2 + 1, facing(TB('shop_sign'), 'south'))
    w.npc(cx1 + 4, gy3 + 1, cz1 + 4, 'kitano_cafe', 'cafe_staff', '洋館カフェの店員さん', '', 'stand', yaw=0)
    reserve(cx1 - 3, cz1 - 3, cx2 + 3, cz2 + 7)
    w.area('kitano', '北野異人館街', '坂の上の洋館', (3690, 55, 380, 3800, 140, 480), 'spot.kitano')
    fix_stairs(w, 3700, 60, 380, 3800, 140, 480)


# ===================================================================== Venus Bridge on Suwayama
def venus(w, rnd):
    cx, cz = 3540, 400
    y = 104
    # observation deck facing south over the city (cantilevered over the slope)
    for x in range(cx - 14, cx + 15):
        for z in range(cz + 8, cz + 18):
            w.fill(x, w.ground(x, z), z, x, y - 1, z, 'minecraft:stone_bricks')
            w.set(x, y, z, 'tabi:hinoki' if z > cz + 10 else 'minecraft:smooth_stone')
            w.clear(x, y + 1, z, x, y + 8, z)
    for x in range(cx - 14, cx + 15):
        w.set(x, y + 1, cz + 18, TB('love_lock_fence'))
    for z in range(cz + 8, cz + 19):
        w.set(cx - 15, y + 1, z, 'tabi:railing')
        w.set(cx + 15, y + 1, z, 'tabi:railing')
    # love lock monument
    w.fill(cx - 2, y + 1, cz + 12, cx + 2, y + 1, cz + 12, 'minecraft:polished_andesite')
    for x in range(cx - 2, cx + 3):
        w.set(x, y + 2, cz + 12, TB('love_lock_fence'))
        w.set(x, y + 3, cz + 12, TB('love_lock_fence'))
    w.set(cx, y + 4, cz + 12, 'minecraft:gold_block')
    w.sign(cx + 4, y + 2, cz + 12, 'tabi:info_board', 'south', ['愛の鍵モニュメント', 'ビーナスブリッジ'], style='info')
    w.set(cx + 4, y + 1, cz + 12, TB('sign_pole'))
    for x in range(cx - 12, cx + 13, 6):
        bench(w, x, y + 1, cz + 15, 'south')
        street_lamp(w, x + 2, y + 1, cz + 17, 'north', height=4)
    # the loop bridge: a spiral road circling around and over itself (figure-8 like)
    R = 12
    lcx, lcz = cx - 22, cz - 4
    for i in range(0, 361, 2):
        a = math.radians(i)
        yy = y - 6 + int(6 * i / 360)
        for rr in (R - 2, R - 1, R, R + 1, R + 2):
            x = int(round(lcx + rr * math.cos(a)))
            z = int(round(lcz + rr * math.sin(a)))
            w.set(x, yy, z, 'tabi:asphalt')
            w.clear(x, yy + 1, z, x, yy + 4, z)
            w.fill(x, yy - 3, z, x, yy - 1, z, 'minecraft:light_gray_concrete') if (i % 30 == 0 and rr == R) else None
        for rr in (R - 3, R + 3):
            x = int(round(lcx + rr * math.cos(a)))
            z = int(round(lcz + rr * math.sin(a)))
            w.set(x, yy + 1, z, 'tabi:railing')
            if i % 20 == 0:
                w.set(x, yy + 2, z, 'minecraft:sea_lantern')
    for i in range(0, 360, 45):
        a = math.radians(i)
        x = int(round(lcx + R * math.cos(a)))
        z = int(round(lcz + R * math.sin(a)))
        g = w.ground(x, z)
        w.fill(x, g, z, x, y - 8, z, 'minecraft:light_gray_concrete')
    w.npc(cx - 6, y + 1, cz + 16, 'venus_photographer', 'photographer', 'カメラマン', 'venus', 'stand', yaw=0, hold='minecraft:spyglass')
    w.npc(cx + 6, y + 1, cz + 16, 'couple', 'couple_m', 'カップル', 'venus', 'stand', yaw=0)
    w.npc(cx + 7, y + 1, cz + 16, 'couple', 'couple_f', 'カップル', 'venus', 'stand', yaw=0)
    reserve(cx - 40, cz - 25, cx + 16, cz + 20)
    w.area('venus', 'ビーナスブリッジ', '諏訪山', (cx - 40, 80, cz - 25, cx + 16, 140, cz + 19), 'spot.venus_bridge', night_journal='spot.night_view')
    w.area('love_locks', '愛の鍵モニュメント', 'ビーナスブリッジ', (cx - 5, y - 2, cz + 9, cx + 5, y + 8, cz + 15), 'spot.love_locks')
    w.viewpoint('venus_night', cx, y + 3.6, cz + 17, 0, 24, time=15500, mode='spectator')
    w.viewpoint('venus_dusk', cx - 10, y + 3.6, cz + 17, -20, 20, time=12300, mode='spectator')
    w.check(cx, y + 4, cz + 12, 'love lock monument')


# ===================================================================== city filler (skyline)
def city(w, rnd):
    parcels = []
    for x in range(3150, 4060, 26):
        for z in range(470, 700, 24):
            parcels.append((x, z))
    for x in range(3600, 4060, 26):
        for z in range(712, 800, 24):
            parcels.append((x, z))
    for (x, z) in parcels:
        bw, bd = rnd.randrange(12, 22), rnd.randrange(12, 20)
        x1, z1, x2, z2 = x + 2, z + 2, x + 2 + bw, z + 2 + bd
        if not free(x1, z1, x2, z2, 3):
            continue
        g = min(w.ground(x1, z1), w.ground(x2, z2))
        if g < 64 or g > 72:
            continue
        east = x > 3760
        floors = rnd.randrange(6, 15) if east else rnd.randrange(3, 9)
        if x < 3380:
            floors = rnd.randrange(2, 5)
        w.fill(x1, 40, z1, x2, g, z2, 'minecraft:stone')
        w.clear(x1, g + 1, z1, x2, g + 60, z2)
        office(w, x1, z1, x2, z2, g, floors, rnd, lit_ratio=0.6 if east else 0.45)
        reserve(x1, z1, x2, z2)
    # trees along the avenues
    for x in range(3340, 3820, 12):
        if free(x, 708, x, 709, 0):
            tree(w, x, G + 1, 708, 'broad', size=0.7)


def loop_bus(w):
    from b_rail import route_meta
    hill, y_at = ROADS['hill']
    main = fillet_path([(3575, 752), (3575, 702), (3484, 702), (3484, 590), (3700, 590), (3700, 474)], 9)
    pts = [(x + 0.5, G + 1.0, z + 0.5) for (x, z) in main[::2]]
    for i in range(0, len(hill), 2):
        x, z = hill[i]
        pts.append((x + 0.5, float(y_at(i)) + 1.0, z + 0.5))
    stations = [
        {'id': 'loop_meriken', 'name': 'メリケンパーク', 'kana': 'めりけんぱーく', 'en': 'Meriken Park', 's': 8.0, 'exit': [3579.5, G + 1, 746.5]},
        {'id': 'loop_kobe', 'name': '神戸駅前', 'kana': 'こうべえきまえ', 'en': 'Kobe Sta.', 'x': 3500, 'z': 702, 'exit': [3500.5, G + 1, 697.5]},
        {'id': 'loop_motomachi', 'name': '元町・南京町', 'kana': 'もとまち・なんきんまち', 'en': 'Motomachi', 'x': 3600, 'z': 590, 'exit': [3600.5, G + 1, 595.5]},
        {'id': 'loop_kitano', 'name': '北野異人館', 'kana': 'きたのいじんかん', 'en': 'Kitano', 'x': 3700, 'z': 500, 'exit': [3704.5, G + 1, 500.5]},
    ]
    meta, L = route_meta('kobe_loop', '港めぐりバス', 'bus', 'bus_loop', 1, 11.0, 0.55, 260, 0, '#1E88A8', 'ビーナスブリッジ方面', 'メリケンパーク方面', pts, stations)
    last = {'id': 'loop_venus', 'name': '諏訪山・ビーナスブリッジ', 'kana': 'すわやま', 'en': 'Venus Bridge', 's': round(L - 7.0, 2),
            'exit': [hill[-1][0] + 0.5, 105.0, hill[-1][1] - 2.5]}
    meta['stations'].append(last)
    meta['stations'].sort(key=lambda d: d['s'])
    w.meta['routes'].append(meta)
    # stop signs
    for st in meta['stations']:
        ex, ey, ez = st['exit']
        x, z = int(math.floor(ex)), int(math.floor(ez))
        y = int(ey)
        w.set(x + 1, y, z, TB('sign_pole'))
        w.sign(x + 1, y + 1, z, 'tabi:bus_stop_sign', 'south', [st['name'], '港めぐりバス'], style='white', back=True)


def build(w):
    rnd = random.Random(1868)
    roads(w)
    kobe_station(w)
    harborland(w, rnd)
    meriken(w, rnd)
    nankinmachi(w, rnd)
    motomachi(w, rnd)
    kitano(w, rnd)
    venus(w, rnd)
    city(w, rnd)
    loop_bus(w)
    w.viewpoint('kobe_station', 3436, 72, 730, 180, 5, time=6000, mode='spectator')
    w.viewpoint('kobe_overview_dusk', 3600, 140, 960, 180, 20, time=12000, mode='spectator')
    fix_stairs(w, 3330, 60, 380, 3830, 200, 910)
    fix_connections(w, 3330, 60, 380, 3830, 200, 910)
