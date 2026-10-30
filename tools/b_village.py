"""ほなみ村: the family farmhouse, vegetable field, rice paddies, shrine, よろず屋, 直売所, bus stop and the road to town."""
import math
import random

from bcommon import (AIR, TB, hip_roof, gable_roof, tree, hedge, utility_pole, street_lamp, vending, bench, road, fillet_path,
                     jp_house, fix_stairs, fix_connections)
from wb import stairs, slab, facing, pane, axis, YAW

G = 66   # village ground (grass top)


def terrain(w):
    w.flatten_ellipse(20, 30, 200, 160, G, 70)
    w.flatten_path([(150, 24), (300, 28), (450, 22), (560, 24)], 34, 60, targets=[G, G, G, G])
    w.flatten_ellipse(-30, -112, 13, 11, 74, 18)


def regions(w):
    w.ensure(-290, -260, 300, 260)
    w.ensure(250, -120, 560, 150)


# ===================================================================== farmhouse (わが家)
def farmhouse(w):
    x1, x2, z1, z2 = -12, 12, -10, 6
    F = G + 1          # floor block y (67)
    top = F + 5        # beam line (72)
    # foundation & floor
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    w.fill(x1 + 1, F, z1 + 1, x2 - 1, F, z2 - 1, 'tabi:hinoki')
    # walls: yakisugi wainscot + white plaster, posts every 3 blocks
    for y in range(F, top + 1):
        for x in range(x1, x2 + 1):
            for z in (z1, z2):
                w.set(x, y, z, 'tabi:yakisugi' if y <= F + 1 else 'tabi:shikkui')
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                w.set(x, y, z, 'tabi:yakisugi' if y <= F + 1 else 'tabi:shikkui')
    w.fill(x1 + 1, F + 1, z1 + 1, x2 - 1, top, z2 - 1, AIR)
    for x in range(x1, x2 + 1, 3):
        for z in (z1, z2):
            w.fill(x, F, z, x, top, z, axis('tabi:hashira', 'y'))
    for z in range(z1, z2 + 1, 4):
        for x in (x1, x2):
            w.fill(x, F, z, x, top, z, axis('tabi:hashira', 'y'))
    w.fill(x1, top, z1, x2, top, z1, axis('tabi:hashira', 'x'))
    w.fill(x1, top, z2, x2, top, z2, axis('tabi:hashira', 'x'))
    w.fill(x1, top, z1, x1, top, z2, axis('tabi:hashira', 'z'))
    w.fill(x2, top, z1, x2, top, z2, axis('tabi:hashira', 'z'))
    # ceiling
    w.fill(x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, 'minecraft:spruce_planks')
    # ---- rooms
    # doma (earthen kitchen) east part x 4..11
    w.fill(4, F, z1 + 1, x2 - 1, F, z2 - 1, 'minecraft:packed_mud')
    w.fill(4, G, z1 + 1, x2 - 1, G, z2 - 1, 'minecraft:packed_mud')
    w.fill(3, F + 1, z1 + 1, 3, top - 1, z2 - 1, 'tabi:shikkui')
    w.fill(3, F + 1, -1, 3, F + 3, 1, AIR)                        # opening doma <-> hall
    w.set(3, F, 0, slab('tabi:hinoki_slab'))
    # entrance (genkan) south wall at x 7..8
    for x in (7, 8):
        w.fill(x, F, z2, x, F + 2, z2, AIR)
        w.set(x, G, z2, 'minecraft:stone_bricks')
    w.set(7, F, z2, 'minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]')
    w.set(7, F + 1, z2, 'minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]')
    w.set(8, F, z2, 'minecraft:spruce_door[facing=north,half=lower,hinge=right,open=false,powered=false]')
    w.set(8, F + 1, z2, 'minecraft:spruce_door[facing=north,half=upper,hinge=right,open=false,powered=false]')
    w.sign(7, F + 3, z2 + 1, 'tabi:shop_sign', 'south', ['山田'], style='shop')
    # kitchen along the north wall: stove, sink, shelves, rice cooker
    w.set(5, F, z1 + 1, 'minecraft:smoker[facing=south,lit=false]')
    w.set(6, F, z1 + 1, 'minecraft:cauldron')
    w.set(7, F, z1 + 1, 'minecraft:water_cauldron[level=3]')
    w.set(8, F, z1 + 1, 'minecraft:barrel[facing=up,open=false]')
    w.set(9, F, z1 + 1, 'minecraft:furnace[facing=south,lit=false]')
    w.set(10, F, z1 + 1, 'minecraft:barrel[facing=up,open=false]')
    w.set(9, F + 1, z1 + 1, 'minecraft:flower_pot')
    w.fill(5, F + 2, z1 + 1, 10, F + 2, z1 + 1, stairs('minecraft:spruce_stairs', 'south', 'top'))
    w.set(10, F, z1 + 4, facing(TB('crate_vegetables'), 'south') if False else TB('crate_vegetables'))
    w.set(10, F, z1 + 5, TB('crate_empty'))
    # dining table in the doma
    w.set(7, F, -3, 'minecraft:spruce_fence')
    w.set(7, F + 1, -3, 'minecraft:spruce_pressure_plate')
    w.set(6, F, -3, stairs('minecraft:spruce_stairs', 'east'))
    w.set(8, F, -3, stairs('minecraft:spruce_stairs', 'west'))
    w.set(5, top - 1, -3, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # west part: tatami rooms
    for (rx1, rz1, rx2, rz2) in ((-11, -9, -5, -2), (-4, -9, 2, -2), (-11, -1, -5, 5), (-4, -1, 2, 5)):
        for x in range(rx1, rx2 + 1):
            for z in range(rz1, rz2 + 1):
                f = 'north' if (z - rz1) % 2 == 0 else 'south'
                if (x - rx1) % 4 >= 2:
                    f = 'north' if (z - rz1) % 2 == 1 else 'south'
                w.set(x, F, z, facing(TB('tatami'), f))
    # partitions: fusuma between rooms, shoji toward the engawa
    w.fill(-11, F + 1, -1 + 0, -5, F + 3, -1 + 0, AIR)
    for x in range(-11, 3):
        w.set(x, F + 1, -1, pane('tabi:fusuma', e=True, w=True))
        w.set(x, F + 2, -1, pane('tabi:fusuma', e=True, w=True))
        w.set(x, F + 3, -1, 'tabi:shikkui')
    w.set(-1, F + 1, -1, AIR)
    w.set(-1, F + 2, -1, AIR)
    for z in range(-9, 6):
        if z == -1:
            continue
        w.set(-4, F + 1, z, pane('tabi:fusuma', n=True, s=True))
        w.set(-4, F + 2, z, pane('tabi:fusuma', n=True, s=True))
        w.set(-4, F + 3, z, 'tabi:shikkui')
    w.set(-4, F + 1, 2, AIR)
    w.set(-4, F + 2, 2, AIR)
    # south wall of the tatami rooms: shoji, opening to the engawa
    for x in range(x1 + 1, 3):
        w.set(x, F + 1, z2, pane('tabi:shoji', e=True, w=True))
        w.set(x, F + 2, z2, pane('tabi:shoji', e=True, w=True))
        w.set(x, F + 3, z2, 'tabi:shikkui')
    w.set(-8, F + 1, z2, AIR)
    w.set(-8, F + 2, z2, AIR)
    w.set(-1, F + 1, z2, AIR)
    w.set(-1, F + 2, z2, AIR)
    # west/north windows (shoji)
    for z in (-7, -4, 1, 3):
        w.set(x1, F + 2, z, pane('tabi:shoji', n=True, s=True))
    for x in (-9, -7, -2, 0):
        w.set(x, F + 2, z1, pane('tabi:shoji', e=True, w=True))
    # ---- living room (居間) -4..2, -9..-2: chabudai, zabuton, phone, fan, scroll
    w.set(-1, F + 1, -6, TB('chabudai'))
    for (x, z, f) in ((-1, -7, 'south'), (-1, -5, 'north'), (-2, -6, 'east'), (0, -6, 'west')):
        w.set(x, F + 1, z, facing(TB('zabuton'), f))
    w.set(1, F + 1, -9 + 0, facing(TB('tansu'), 'south'))
    w.set(-3, F + 1, -9, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    w.set(-3, F + 1, -8, facing(TB('phone'), 'south'))
    w.set(2, F + 1, -3, facing(TB('senpuki'), 'west'))
    w.set(-1, top - 1, -6, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # ---- grandma's room (座敷) -11..-5, -9..-2: tokonoma with scroll, butsudan
    w.fill(-11, F + 1, -9, -9, F + 1, -9, 'minecraft:spruce_slab[type=bottom,waterlogged=false]')
    w.set(-10, F + 2, -9, facing(TB('kakejiku'), 'south'))
    w.set(-10, F + 2, -9 + 0, facing(TB('kakejiku'), 'south'))
    w.set(-7, F + 1, -9, facing(TB('tansu'), 'south'))
    w.set(-6, F + 1, -9, facing(TB('tansu'), 'south'))
    w.set(-6, F + 2, -9, 'minecraft:candle[candles=2,lit=false,waterlogged=false]')
    w.set(-8, F + 1, -4, facing(TB('zabuton'), 'south'))
    w.set(-8, top - 1, -6, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # ---- player's room (自室) -11..-5, -1..5: futon, desk, fan, bookshelf
    w.set(-9, F + 1, 1, facing(TB('futon'), 'south'))
    w.set(-9, F + 1, 2, facing(TB('kakebuton'), 'south'))
    w.set(-11, F + 1, 4, 'minecraft:spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    w.set(-10, F + 1, 4, 'minecraft:spruce_slab[type=top,waterlogged=false]')
    w.set(-10, F + 2, 4, 'minecraft:flower_pot')
    w.set(-11, F + 1, 0, 'minecraft:bookshelf')
    w.set(-11, F + 2, 0, 'minecraft:bookshelf')
    w.set(-6, F + 1, 0, facing(TB('senpuki'), 'west'))
    w.set(-11, F + 1, 3, 'minecraft:chest[facing=east,type=single,waterlogged=false]')
    w.set(-8, top - 1, 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # guest room -4..2, -1..5
    w.set(-1, F + 1, 2, TB('chabudai'))
    w.set(2, F + 1, 4, facing(TB('tansu'), 'west'))
    w.set(-1, top - 1, 2, 'minecraft:lantern[hanging=true,waterlogged=false]')
    # ---- engawa along the south side (x -11..2), deep eaves
    w.fill(x1, F, z2 + 1, 2, F, z2 + 1, 'tabi:hinoki')
    w.fill(x1, F, z2 + 2, 2, F, z2 + 2, slab('tabi:hinoki_slab'))
    w.fill(x1, G, z2 + 1, 2, G, z2 + 2, 'minecraft:smooth_stone')
    for x in (x1, -6, 2):
        w.fill(x, F + 1, z2 + 2, x, top, z2 + 2, axis('tabi:hashira', 'y'))
    w.fill(x1, top, z2 + 2, 2, top, z2 + 2, axis('tabi:hashira', 'x'))
    w.set(-2, top - 1, z2 + 2, TB('furin'))
    w.set(-8, top - 1, z2 + 2, TB('furin'))
    w.set(-5, F + 1, z2 + 2, TB('sudare')) if False else None
    for x in (-10, -4):
        w.set(x, top - 1, z2 + 2, facing(TB('sudare'), 'north'))
    w.set(-6, G + 0, z2 + 3, 'minecraft:stone_slab[type=bottom,waterlogged=false]')   # kutsunugi-ishi
    # ---- big hip roof (deep eaves)
    hip_roof(w, x1, z1, x2, z2 + 2, top + 1, 'tabi:kawara', overhang=2)
    # gable vents
    w.set(0, top + 6, z1 + 3, 'tabi:shikkui')
    fix_stairs(w, x1 - 3, top, z1 - 3, x2 + 3, top + 14, z2 + 5)
    return F


def garden(w):
    F = G + 1
    # stepping stones from the engawa to the gate
    for (x, z) in ((-6, 10), (-5, 12), (-6, 14), (-5, 16), (-4, 18), (-3, 20)):
        w.set(x, G, z, 'minecraft:smooth_stone')
    # pond
    for x in range(-2, 5):
        for z in range(12, 18):
            if ((x - 1) / 3.5) ** 2 + ((z - 14.5) / 3) ** 2 <= 1:
                w.set(x, G, z, 'minecraft:water')
                w.set(x, G - 1, z, 'minecraft:water')
                w.set(x, G - 2, z, 'minecraft:mud')
    for (x, z) in ((-3, 13), (5, 15), (2, 18), (-2, 17), (4, 11)):
        w.set(x, G + 1, z, 'minecraft:mossy_cobblestone_slab[type=bottom,waterlogged=false]')
    w.set(1, G, 14, 'minecraft:lily_pad') if False else None
    w.set(1, G + 1, 14, 'minecraft:lily_pad')
    w.set(-9, G + 1, 12, TB('stone_lantern'))
    tree(w, -10, G + 1, 17, 'pine', size=1.0)
    tree(w, 8, G + 1, 17, 'pine', size=0.8)
    tree(w, 14, G + 1, 4, 'broad', size=0.8)
    hedge(w, -14, 21, 12, 21, G + 1, 1)
    w.fill(-4, G + 1, 21, -2, G + 1, 21, AIR)   # garden opening to the road
    for x in (-5, -1):
        w.fill(x, G + 1, 21, x, G + 3, 21, axis('tabi:hashira', 'y'))
    w.fill(-5, G + 4, 21, -1, G + 4, 21, slab('tabi:kawara_slab'))
    # flower bed
    for x in range(-13, -9):
        w.set(x, G + 1, 8, 'minecraft:red_tulip' if x % 2 else 'minecraft:oxeye_daisy')
    # well by the doma
    w.fill(13, G, 8, 15, G + 1, 10, 'minecraft:cobblestone_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=low]')
    w.set(14, G + 1, 9, AIR)
    w.set(14, G, 9, 'minecraft:water')
    w.fill(13, G + 2, 9, 13, G + 3, 9, 'minecraft:spruce_fence')
    w.fill(15, G + 2, 9, 15, G + 3, 9, 'minecraft:spruce_fence')
    w.fill(13, G + 4, 8, 15, G + 4, 10, slab('tabi:kawara_slab'))
    # back: bamboo grove and cedar trees
    tree(w, -18, G + 1, -22, 'bamboo')
    tree(w, -10, G + 1, -24, 'bamboo')
    tree(w, -2, G + 1, -26, 'bamboo')
    for (x, z) in ((-30, -30), (-26, -40), (10, -36), (20, -44), (-40, -20)):
        tree(w, x, G + 1, z, 'cedar')


def shed(w):
    x1, x2, z1, z2 = 15, 23, -21, -13
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:packed_mud')
    w.fill(x1, G + 1, z1, x2, G + 4, z2, 'tabi:yakisugi')
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 4, z2 - 1, AIR)
    w.fill(18, G + 1, z2, 20, G + 3, z2, AIR)
    for x in (x1, x2):
        for z in (z1, z2):
            w.fill(x, G + 1, z, x, G + 4, z, axis('tabi:hashira', 'y'))
    w.set(x1 + 1, G + 1, z1 + 1, 'minecraft:hay_block[axis=y]')
    w.set(x1 + 2, G + 1, z1 + 1, 'minecraft:hay_block[axis=y]')
    w.set(x1 + 1, G + 2, z1 + 1, 'minecraft:hay_block[axis=x]')
    w.set(x2 - 1, G + 1, z1 + 1, 'minecraft:barrel[facing=up,open=false]')
    w.set(x2 - 1, G + 1, z1 + 2, 'minecraft:composter[level=4]')
    w.set(x2 - 2, G + 1, z1 + 1, TB('crate_empty'))
    w.set(x2 - 2, G + 2, z1 + 1, TB('crate_empty'))
    gable_roof(w, x1, z1, x2, z2, G + 5, 'x', 'tabi:kawara', overhang=1, gable_wall='tabi:yakisugi')


def field(w, rnd):
    """kitchen garden east of the house: rows of tomato, cucumber, eggplant with water channels"""
    x1, x2, z1, z2 = 17, 40, -9, 12
    kinds = ['tomato', 'cucumber', 'eggplant']
    col = 0
    for x in range(x1, x2 + 1):
        r = (x - x1) % 3
        if r == 2:
            w.fill(x, G - 1, z1, x, G, z2, 'minecraft:water')
            w.fill(x, G - 2, z1, x, G - 2, z2, 'minecraft:mud')
            continue
        kind = kinds[((x - x1) // 3) % 3]
        for z in range(z1, z2 + 1):
            w.set(x, G, z, 'minecraft:farmland[moisture=7]')
            age = 7 if rnd.random() < 0.8 else rnd.choice([3, 4, 5])
            w.set(x, G + 1, z, f'tabi:{kind}_crop[age={age}]')
        col += 1
    # border path + small sign
    for x in range(x1 - 1, x2 + 2):
        w.set(x, G, z1 - 1, 'minecraft:dirt_path')
        w.set(x, G, z2 + 1, 'minecraft:dirt_path')
    w.sign(x1 - 2, G + 2, z2 + 1, 'tabi:shop_sign', 'south', ['うちの畑'], style='shop')
    w.set(x1 - 2, G + 1, z2 + 1, TB('sign_pole'))
    # scarecrow (kakashi)
    w.fill(30, G + 1, 0, 30, G + 2, 0, 'minecraft:spruce_fence')
    w.set(30, G + 3, 0, 'minecraft:hay_block[axis=y]')
    w.set(29, G + 2, 0, 'minecraft:spruce_fence')
    w.set(31, G + 2, 0, 'minecraft:spruce_fence')
    w.set(30, G + 4, 0, 'minecraft:yellow_carpet')


def paddies(w, rnd):
    """rice paddies south of the road: mud, shallow water with rice, aze ridges between plots"""
    z0, z1 = 31, 150
    x0, x1 = -150, 170
    # irrigation canal along the road
    w.fill(x0, G - 2, 29, x1, G, 30, 'minecraft:smooth_stone')
    w.fill(x0, G - 1, 29, x1, G, 29, 'minecraft:water') if False else None
    w.fill(x0, G - 1, 29, x1, G - 1, 30, 'minecraft:water')
    w.fill(x0, G, 29, x1, G, 30, AIR)
    w.fill(x0, G, 29, x1, G, 30, 'minecraft:water')
    pw, pd = 22, 14
    for px in range(x0, x1, pw + 1):
        for pz in range(z0, z1, pd + 1):
            gx, gz = px + pw, pz + pd
            if gx > x1 or gz > z1:
                continue
            if abs(px - 0) < 1 and False:
                continue
            stage = rnd.choice([1, 1, 2, 2, 2])
            for x in range(px, gx):
                for z in range(pz, gz):
                    if math.hypot(x - 10, z - 15) < 26 and z < 30:
                        continue
                    w.set(x, G - 1, z, TB('paddy_mud'))
                    if rnd.random() < 0.04:
                        w.set(x, G, z, 'minecraft:water')
                    else:
                        w.set(x, G, z, f'tabi:rice_plant[age={stage},waterlogged=true]')
            # aze ridges (grass) around the plot
            for x in range(px - 1, gx + 1):
                w.set(x, G, pz - 1, 'minecraft:grass_block')
                w.set(x, G, gz, 'minecraft:grass_block')
            for z in range(pz - 1, gz + 1):
                w.set(px - 1, G, z, 'minecraft:grass_block')
                w.set(gx, G, z, 'minecraft:grass_block')
    # a farm road through the paddies (gravel)
    for z in range(31, 150):
        w.set(45, G, z, 'minecraft:dirt_path')
        w.set(46, G, z, 'minecraft:dirt_path')
        w.set(-60, G, z, 'minecraft:dirt_path')


def bus_stop(w):
    """ほなみ バス停: wooden shelter, bench, sign, vending machine"""
    x, z = 60, 19
    w.fill(x - 3, G, z - 3, x + 3, G, z + 1, 'minecraft:smooth_stone')
    for (px, pz) in ((x - 3, z - 3), (x + 3, z - 3), (x - 3, z + 1), (x + 3, z + 1)):
        w.fill(px, G + 1, pz, px, G + 3, pz, axis('tabi:hashira', 'y'))
    w.fill(x - 3, G + 1, z - 3, x + 3, G + 2, z - 3, 'minecraft:spruce_planks')
    w.fill(x - 3, G + 1, z - 3, x - 3, G + 2, z + 1, 'minecraft:spruce_planks')
    w.fill(x + 3, G + 1, z - 3, x + 3, G + 2, z + 1, 'minecraft:spruce_planks')
    gable_roof(w, x - 3, z - 3, x + 3, z + 1, G + 4, 'x', 'tabi:kawara', overhang=1)
    for bx in (x - 1, x, x + 1):
        w.set(bx, G + 1, z - 2, facing(TB('park_bench'), 'south'))
    w.set(x + 5, G + 1, z + 1, TB('sign_pole'))
    w.sign(x + 5, G + 2, z + 1, 'tabi:bus_stop_sign', 'south', ['ほなみ', 'ほなみ線 大川駅前ゆき'], style='white', back=True)
    w.sign(x - 5, G + 2, z + 1, 'tabi:info_board', 'south', ['時刻表', '大川駅前ゆき', 'バスは いつでも来ます', '運賃 200円'], style='info')
    w.set(x - 5, G + 1, z + 1, TB('sign_pole'))
    vending(w, x + 4, G + 1, z - 2, 'south')
    w.npc(x, G + 1, z - 2, 'busstop_oldman', 'old_man', 'バス停のおじいさん', 'busstop', 'sit', yaw=0)


def yorozuya(w):
    x1, x2, z1, z2 = 70, 80, 6, 16
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    w.fill(x1, G + 1, z1, x2, G + 7, z2, 'tabi:yakisugi')
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 6, z2 - 1, AIR)
    w.fill(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 4, z2 - 1, 'minecraft:spruce_planks')
    w.fill(x1, G + 4, z1, x2, G + 7, z2, 'tabi:shikkui')
    w.fill(x1 + 1, G + 5, z1 + 1, x2 - 1, G + 6, z2 - 1, AIR)
    for x in (x1, x2):
        for z in (z1, z2):
            w.fill(x, G + 1, z, x, G + 7, z, axis('tabi:hashira', 'y'))
    # open shop front to the south
    w.fill(x1 + 1, G + 1, z2, x2 - 1, G + 3, z2, AIR)
    for x in range(x1 + 2, x2 - 1, 2):
        w.set(x, G + 3, z2, facing(TB('noren_indigo'), 'south'))
    w.fill(x1, G + 4, z2 + 1, x2, G + 4, z2 + 1, slab('tabi:kawara_slab'))
    w.sign(x1 + 3, G + 5, z2 + 1, 'tabi:shop_sign', 'south', ['よろず屋 たなか', '駄菓子・日用品・たね'], style='shop', span=5)
    for k in range(1, 5):
        w.set(x1 + 3 + k, G + 5, z2 + 1, facing(TB('shop_sign'), 'south'))
    for z in range(z1 + 1, z2 - 2):
        w.set(x1 + 1, G + 1, z, facing(TB('souvenir_shelf'), 'east'))
        w.set(x1 + 1, G + 2, z, facing(TB('souvenir_shelf'), 'east'))
        w.set(x2 - 1, G + 1, z, facing(TB('souvenir_shelf'), 'west'))
    w.fill(x1 + 3, G + 1, z1 + 3, x2 - 3, G + 1, z1 + 3, 'minecraft:spruce_planks')
    w.set(x1 + 5, G + 2, z1 + 3, 'minecraft:bell[attachment=floor,facing=south,powered=false]') if False else None
    w.set(x1 + 4, G + 1, z1 + 5, 'minecraft:white_concrete')    # ice chest
    w.set(x1 + 5, G + 1, z1 + 5, 'minecraft:white_concrete')
    w.set(x1 + 6, G + 1, z1 + 5, 'minecraft:light_blue_stained_glass')
    w.set(x1 + 5, G + 5, z1 + 5, 'minecraft:lantern[hanging=false,waterlogged=false]')
    hip_roof(w, x1, z1, x2, z2, G + 8, 'tabi:kawara', overhang=1)
    vending(w, x2 + 1, G + 1, z2, 'south')
    w.set(x1 - 1, G + 1, z2 + 1, facing(TB('mailbox'), 'south'))
    w.set(x1 + 2, G + 1, z2 + 2, facing(TB('park_bench'), 'south'))
    w.npc(x1 + 5, G + 1, z1 + 2, 'yorozuya', 'yorozuya_lady', 'よろず屋の田中さん', 'yorozuya', 'stand', yaw=0)
    w.npc(x1 + 2, G + 1, z2 + 3, 'village_kid', 'kid_boy', 'ケンタ', 'village_kids', 'stand', yaw=180)
    w.npc(x1 + 4, G + 1, z2 + 3, 'village_kid', 'kid_girl', 'ミホ', 'village_kids', 'stand', yaw=180)


def market(w):
    x1, x2, z1, z2 = 95, 117, 5, 17
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    for x in range(x1, x2 + 1, 4):
        for z in (z1, z2):
            w.fill(x, G + 1, z, x, G + 4, z, axis('tabi:hashira', 'y'))
    w.fill(x1, G + 1, z1, x2, G + 4, z1, 'tabi:yakisugi')
    w.fill(x1, G + 1, z1, x1, G + 4, z2, 'tabi:yakisugi')
    w.fill(x2, G + 1, z1, x2, G + 4, z2, 'tabi:yakisugi')
    w.fill(x1, G + 5, z1, x2, G + 5, z2, 'minecraft:spruce_planks')
    gable_roof(w, x1, z1, x2, z2, G + 6, 'x', 'tabi:kawara', overhang=1, gable_wall='tabi:yakisugi')
    # display tables with crates
    for x in range(x1 + 2, x2 - 1):
        for z in (z1 + 3, z1 + 4, z1 + 8, z1 + 9):
            w.set(x, G + 1, z, TB('crate_vegetables') if (x + z) % 3 else TB('crate_empty'))
    w.fill(x1 + 8, G + 1, z2 - 2, x1 + 12, G + 1, z2 - 2, 'minecraft:spruce_planks')
    w.set(x1 + 10, G + 2, z2 - 2, 'minecraft:lantern[hanging=false,waterlogged=false]')
    w.sign(x1 + 6, G + 5, z2 + 1, 'tabi:shop_sign', 'south', ['ほなみ農産物直売所', 'とれたて野菜 買い取りします'], style='shop', span=10)
    for k in range(1, 10):
        w.set(x1 + 6 + k, G + 5, z2 + 1, facing(TB('shop_sign'), 'south'))
    w.npc(x1 + 10, G + 1, z2 - 3, 'market', 'market_man', '直売所の佐藤さん', 'market', 'stand', yaw=0)
    # parking
    for x in range(x1, x2 + 1):
        for z in range(z2 + 1, z2 + 4):
            w.set(x, G, z, 'tabi:asphalt')


def shrine(w):
    sx, sz, sy = -30, -112, 74
    # stone stairs from the basin up to the terrace
    for i, z in enumerate(range(-78, -100, -1)):
        y = G + 1 + int(i * (sy - G) / 22)
        w.fill(sx - 1, w.ground(sx, z), z, sx + 1, y - 1, z, 'minecraft:stone_bricks')
        w.fill(sx - 1, y, z, sx + 1, y, z, 'minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        w.clear(sx - 1, y + 1, z, sx + 1, y + 6, z)
    w.fill(sx - 7, sy - 3, sz - 9, sx + 7, sy, sz + 12, 'minecraft:stone_bricks')
    w.fill(sx - 6, sy, sz - 8, sx + 6, sy, sz + 11, 'minecraft:gravel')
    w.fill(sx - 1, sy, sz - 8, sx + 1, sy, sz + 11, 'minecraft:smooth_stone')
    w.clear(sx - 7, sy + 1, sz - 9, sx + 7, sy + 14, sz + 12)

    def torii(x, z, y, h=6):
        for dx in (-3, 3):
            w.fill(x + dx, y, z, x + dx, y + h, z, axis(TB('shu_pillar'), 'y'))
        w.fill(x - 4, y + h - 1, z, x + 4, y + h - 1, z, TB('shu_block'))
        w.fill(x - 5, y + h + 1, z, x + 5, y + h + 1, z, 'minecraft:black_concrete')
        w.fill(x - 4, y + h, z, x + 4, y + h, z, TB('shu_block'))
        w.sign(x, y + h, z + 1, 'tabi:gate_plaque', 'south', ['ほなみ神社'], style='gate')
    torii(sx, -78, G + 1, 6)
    torii(sx, sz + 10, sy + 1, 5)
    # haiden
    hx1, hx2, hz1, hz2 = sx - 4, sx + 4, sz - 7, sz - 1
    w.fill(hx1, sy + 1, hz1, hx2, sy + 1, hz2, 'tabi:hinoki')
    for x in (hx1, hx2):
        for z in (hz1, hz2):
            w.fill(x, sy + 2, z, x, sy + 5, z, axis(TB('shu_pillar'), 'y'))
    w.fill(hx1 + 1, sy + 2, hz1, hx2 - 1, sy + 5, hz1, 'tabi:hinoki')
    w.fill(hx1, sy + 2, hz1 + 1, hx1, sy + 5, hz2 - 1, 'tabi:hinoki')
    w.fill(hx2, sy + 2, hz1 + 1, hx2, sy + 5, hz2 - 1, 'tabi:hinoki')
    w.fill(hx1 + 1, sy + 5, hz2, hx2 - 1, sy + 5, hz2, 'tabi:hinoki')
    w.set(sx, sy + 2, hz2 + 1, 'minecraft:barrel[facing=up,open=false]')       # offertory box
    w.set(sx, sy + 5, hz2 + 1, 'minecraft:bell[attachment=ceiling,facing=south,powered=false]')
    gable_roof(w, hx1, hz1, hx2, hz2 + 1, sy + 6, 'z', 'tabi:kawara', overhang=1, gable_wall='tabi:hinoki')
    for dx in (-4, 4):
        w.set(sx + dx, sy + 1, sz + 3, TB('stone_lantern'))
    for (x, z) in ((sx - 9, sz - 8), (sx + 9, sz - 6), (sx - 10, sz + 4), (sx + 10, sz + 6), (sx - 8, sz + 12), (sx + 8, sz + 13)):
        tree(w, x, w.ground(x, z) + 1, z, 'cedar', size=1.4)
    w.sign(sx + 3, sy + 2, sz + 8, 'tabi:info_board', 'south', ['ほなみ神社', '村の田んぼと人びとを', '見守ってきた鎮守さま'], style='info')
    w.set(sx + 3, sy + 1, sz + 8, TB('sign_pole'))


def jizo(w, x, z):
    w.set(x, G + 1, z, facing(TB('jizo'), 'south'))
    w.set(x - 1, G + 1, z, 'minecraft:flower_pot')
    for px in (x - 1, x + 1):
        w.fill(px, G + 1, z - 1, px, G + 2, z - 1, 'minecraft:spruce_fence')
    w.fill(x - 1, G + 3, z - 1, x + 1, G + 3, z, slab('tabi:kawara_slab'))


def build(w):
    rnd = random.Random(42)
    F = farmhouse(w)
    garden(w)
    shed(w)
    field(w, rnd)
    paddies(w, rnd)
    # main road: village -> town
    pts = fillet_path([(-160, 24), (535, 24)], 20, 1.0)
    road(w, pts, G, width=7, center='white')
    # gravel lane from the house gate to the road
    for z in range(21, 22):
        for x in range(-4, -1):
            w.set(x, G, z, 'minecraft:gravel')
    for x in range(-150, 530, 32):
        utility_pole(w, x, G + 1, 19, 'north')
    bus_stop(w)
    yorozuya(w)
    market(w)
    shrine(w)
    jizo(w, -40, 19)
    jizo(w, 140, 19)
    # neighbours' houses
    for (x, z, d) in ((-70, -34, 'south'), (-95, -10, 'east'), (45, -38, 'south'), (130, -30, 'south'), (160, 2, 'west'), (-110, 10, 'south')):
        jp_house(w, x - 5, z - 4, x + 5, z + 4, G, rnd, door=d)
    w.npc(30, G + 1, 33, 'neighbor', 'farmer_old', '山本のおじいちゃん', 'farmer', 'wander', yaw=0, radius=8)
    # family
    w.npc(-6, F + 1, 7, 'grandma', 'grandma', 'おばあちゃん', 'grandma', 'sit', yaw=0)
    w.npc(8, F + 1, -6, 'mom', 'mom', 'お母さん', 'mom', 'stand', yaw=0)
    # trees around the basin edge
    for i in range(70):
        a = rnd.random() * math.tau
        r = 170 + rnd.random() * 40
        x, z = int(20 + math.cos(a) * r * 1.1), int(30 + math.sin(a) * r * 0.85)
        if 20 < z < 160 and -160 < x < 180:
            continue
        g = w.ground(x, z)
        if g > 64:
            tree(w, x, g + 1, z, rnd.choice(['cedar', 'cedar', 'broad', 'pine']))
    # areas
    w.area('honami', 'ほなみ村', '田んぼと山にかこまれた村', (-220, 40, -200, 240, 140, 200), 'spot.honami')
    w.area('home', 'わが家', 'おばあちゃんの家', (-15, 60, -12, 15, 85, 10))
    w.area('honami_market', 'ほなみ農産物直売所', 'とれたて野菜の店', (93, 60, 3, 119, 80, 21))
    w.area('honami_shrine', 'ほなみ神社', '村の鎮守さま', (-45, 60, -125, -15, 100, -76), 'spot.honami_shrine')
    w.area('akichi', '', '', (130, 60, -70, 175, 120, -40), build=True)
    w.sign(150, G + 2, -38, 'tabi:info_board', 'south', ['空き地', 'ここは自由に', '建物を建ててもいいよ'], style='info')
    w.set(150, G + 1, -38, TB('sign_pole'))
    # spawn: on the futon in the player's room, facing the engawa
    w.meta['spawn'] = {'x': -8.5, 'y': F + 1.2, 'z': 2.5, 'yaw': 0}
    w.check(-9, F + 1, 1, 'futon')
    w.check(-3, F + 1, -8, 'phone')
    w.check(20, G + 1, 0, 'field crop')
    w.viewpoint('village_morning', 30, 80, 70, 160, 15, time=1000)
    w.viewpoint('farmhouse', 2, 72, 26, 180, 10, time=4000)
    w.viewpoint('home_room', -8.5, F + 2.6, 4.5, 180, 15, time=4000, mode='spectator')
    fix_connections(w, -20, 60, -30, 180, 100, 40)
    return F
