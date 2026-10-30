"""大川町: river and bridge, station (terminus of the Kawagiri line), shotengai with a kissaten, river-fish restaurant."""
import math
import random

from bcommon import (AIR, TB, hip_roof, gable_roof, flat_roof, tree, utility_pole, street_lamp, vending, bench, road, fillet_path,
                     jp_house, fix_stairs, fix_connections)
from wb import stairs, slab, facing, pane, axis

G = 66
RX1, RX2 = 545, 557       # river channel (water) x range


def terrain(w):
    w.flatten_ellipse(600, 0, 175, 145, G, 60)


def regions(w):
    w.ensure(410, -210, 800, 210)


def river(w):
    for z in range(-128, 129):
        for x in range(RX1 - 1, RX2 + 2):
            w.clear(x, 60, z, x, G + 6, z)
            if x in (RX1 - 1, RX2 + 1):
                w.fill(x, 58, z, x, G, z, TB('ishigaki'))
            else:
                w.fill(x, 56, z, x, 59, z, 'minecraft:gravel')
                w.fill(x, 60, z, x, 63, z, 'minecraft:water')
        # riverside promenade on the east bank
        for x in (RX2 + 2, RX2 + 3):
            w.set(x, G, z, 'minecraft:gravel' if x == RX2 + 3 else 'minecraft:dirt_path')
    for z in (-129, 129):
        w.fill(RX1 - 1, 56, z, RX2 + 1, G + 1, z, 'minecraft:stone_bricks')
    # weir in the north
    for x in range(RX1, RX2 + 1):
        w.set(x, 63, -100, 'minecraft:stone_brick_slab[type=bottom,waterlogged=true]')
    # road bridge (z 20..28)
    for z in range(20, 29):
        for x in range(RX1 - 1, RX2 + 2):
            w.set(x, G, z, 'tabi:asphalt' if 21 <= z <= 27 else 'minecraft:smooth_stone')
            w.set(x, G - 1, z, 'minecraft:light_gray_concrete')
    for x in range(RX1 - 1, RX2 + 2):
        w.set(x, G + 1, 20, 'tabi:railing')
        w.set(x, G + 1, 28, 'tabi:railing')
    w.set(RX1 - 2, G + 1, 20, 'minecraft:stone_bricks')
    w.set(RX2 + 2, G + 1, 28, 'minecraft:stone_bricks')
    w.sign(RX1 - 2, G + 2, 20, 'tabi:shop_sign', 'north', ['おおかわばし'], style='shop')
    # footbridge further north
    for z in (-60, -59):
        for x in range(RX1 - 1, RX2 + 2):
            w.set(x, G, z, 'tabi:hinoki')
    for x in range(RX1 - 1, RX2 + 2):
        w.set(x, G + 1, -61, 'minecraft:spruce_fence')
        w.set(x, G + 1, -58, 'minecraft:spruce_fence')
    for z in range(-120, 121, 14):
        tree(w, RX2 + 5, G + 1, z, 'broad', size=0.9)


def station(w):
    """木造駅舎 north of the platform (platform z 44..49), entrance facing the plaza to the north"""
    x1, x2, z1, z2 = 612, 640, 34, 43
    F = G
    w.fill(x1, F, z1, x2, F, z2, 'minecraft:smooth_stone')
    w.fill(x1, F + 1, z1, x2, F + 5, z2, 'tabi:shikkui')
    w.fill(x1, F + 1, z1, x2, F + 1, z2, 'tabi:yakisugi')
    w.fill(x1 + 1, F + 1, z1 + 1, x2 - 1, F + 4, z2 - 1, AIR)
    w.fill(x1 + 1, F, z1 + 1, x2 - 1, F, z2 - 1, TB('tile_cream'))
    for x in range(x1, x2 + 1, 4):
        w.fill(x, F + 1, z1, x, F + 5, z1, axis('tabi:hashira', 'y'))
        w.fill(x, F + 1, z2, x, F + 5, z2, axis('tabi:hashira', 'y'))
    # windows
    for x in range(x1 + 2, x2 - 1, 4):
        w.set(x, F + 3, z1, pane('minecraft:glass_pane', e=True, w=True))
        w.set(x + 1, F + 3, z1, pane('minecraft:glass_pane', e=True, w=True))
    # entrance (north) x 624..628
    w.fill(624, F + 1, z1, 628, F + 3, z1, AIR)
    w.fill(622, F + 4, z1 - 3, 630, F + 4, z1 - 1, slab('tabi:kawara_slab'))
    for x in (622, 630):
        w.fill(x, F + 1, z1 - 3, x, F + 3, z1 - 3, axis('tabi:hashira', 'y'))
    w.sign(624, F + 5, z1 - 1, 'tabi:shop_sign', 'north', ['大川駅', 'OKAWA STATION'], style='shop', span=5)
    for k in range(1, 5):
        w.set(624 + k, F + 5, z1 - 1, facing(TB('shop_sign'), 'north'))
    # waiting room: benches, ticket machines, fare chart
    for x in (615, 616, 617, 618):
        w.set(x, F + 1, 39, facing(TB('station_bench'), 'east'))
    for x in (632, 634):
        w.set(x, F + 1, z1 + 1, facing(TB('kenbaiki'), 'south'))
        w.set(x, F + 2, z1 + 1, facing(TB('kenbaiki_top'), 'south'))
    w.sign(636, F + 2, z1 + 1, 'tabi:info_board', 'south', ['きっぷのかいかた', '券売機をクリック', '行き先をえらぼう'], style='info')
    w.set(636, F + 1, z1 + 1, TB('sign_pole'))
    # gates toward the platform (south wall z2)
    w.fill(620, F + 1, z2, 632, F + 3, z2, AIR)
    for x in (620, 622, 624, 626, 628, 630, 632):
        if x in (620, 632):
            w.set(x, F + 1, z2, 'tabi:shikkui')
        else:
            w.set(x, F + 1, z2, facing(TB('kaisatsu'), 'north'))
    w.fill(620, F + 4, z2, 632, F + 4, z2, 'tabi:shikkui')
    # station office with window
    w.fill(x2 - 5, F + 1, z1 + 4, x2 - 5, F + 4, z2 - 1, 'tabi:shikkui')
    w.set(x2 - 5, F + 2, z1 + 6, pane('minecraft:glass_pane', n=True, s=True))
    w.set(x2 - 3, F + 1, z1 + 6, 'minecraft:lectern[facing=west,has_book=false,powered=false]')
    for x in (618, 626, 634):
        w.set(x, F + 4, 38, 'minecraft:lantern[hanging=true,waterlogged=false]')
    hip_roof(w, x1, z1, x2, z2, F + 6, 'tabi:kawara', overhang=1)
    # steps from the building to the platform (platform top y=68 -> floor 67)
    w.fill(620, F, z2 + 1, 632, F + 1, z2 + 1, 'minecraft:smooth_stone')
    w.fill(620, F + 2, z2 + 1, 632, F + 2, z2 + 1, AIR)
    w.npc(626, F + 1, 40, 'okawa_ekiin', 'station_staff', '大川駅の駅員さん', 'station', 'stand', yaw=180)
    vending(w, x1 - 1, F + 1, z1 - 1, 'north')
    # plaza
    for x in range(600, 660):
        for z in range(28, 34):
            w.set(x, G, z, 'minecraft:smooth_stone' if (x + z) % 2 else 'tabi:sidewalk')
    w.set(632, G + 1, 28, TB('sign_pole'))
    w.sign(632, G + 2, 28, 'tabi:bus_stop_sign', 'north', ['大川駅前', 'ほなみ線 ほなみゆき'], style='white', back=True)
    bench(w, 629, G + 1, 30, 'north')
    bench(w, 630, G + 1, 30, 'north')
    w.set(615, G + 1, 30, facing(TB('mailbox'), 'north'))
    tree(w, 606, G + 1, 31, 'broad', size=0.8)
    tree(w, 652, G + 1, 31, 'broad', size=0.8)
    street_lamp(w, 610, G + 1, 28, 'north')
    street_lamp(w, 646, G + 1, 28, 'north')


def shop_front(w, x1, z1, x2, z2, side, name, sub, rnd, wall=None, noren=None, interior=None, two=True):
    """small shop facing the shotengai street. side: which side the street is on ('east'/'west')"""
    wall = wall or rnd.choice(['tabi:shikkui', 'tabi:tile_brown', 'tabi:tile_cream', 'minecraft:white_terracotta', 'tabi:clapboard_white'])
    top = G + (8 if two else 4)
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    w.fill(x1, G + 1, z1, x2, top, z2, wall)
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR)
    if two:
        w.fill(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 4, z2 - 1, 'minecraft:spruce_planks')
    fx = x2 if side == 'east' else x1
    w.fill(fx, G + 1, z1 + 1, fx, G + 3, z2 - 1, AIR)
    for z in range(z1 + 1, z2):
        w.set(fx, G + 3, z, pane('minecraft:glass_pane', n=True, s=True))
    if noren:
        for z in range(z1 + 2, z2 - 1, 2):
            w.set(fx, G + 3, z, facing(TB(noren), side))
    if two:
        for z in range(z1 + 1, z2, 2):
            w.set(fx, G + 6, z, pane('minecraft:glass_pane', n=True, s=True))
    # awning
    ax = fx + (1 if side == 'east' else -1)
    w.fill(ax, G + 4, z1, ax, G + 4, z2, slab(rnd.choice(['minecraft:red_concrete', 'minecraft:green_concrete', 'minecraft:blue_concrete', 'tabi:kawara_slab']).replace('_concrete', '_concrete') if False else 'tabi:kawara_slab'))
    w.sign(ax, G + 5, z1 + 1, 'tabi:shop_sign', side, [name] + ([sub] if sub else []), style='shop', span=z2 - z1 - 1)
    for z in range(z1 + 2, z2):
        w.set(ax, G + 5, z, facing(TB('shop_sign'), side))
    if interior:
        interior(fx, side)
    gable_roof(w, x1, z1, x2, z2, top + 1, 'z' if False else 'x', 'tabi:kawara', overhang=0, gable_wall=wall)


def shotengai(w, rnd):
    """N-S shopping street x 599..603 from z -70 to 20, covered arcade"""
    for z in range(-72, 21):
        for x in range(598, 605):
            w.set(x, G, z, 'tabi:sidewalk' if x in (598, 604) else ('minecraft:smooth_stone' if z % 6 else 'minecraft:polished_andesite'))
    shops_w = [('八百屋 みどり', '新鮮やさい'), ('大川書店', '本・文具'), ('和菓子 つきみ堂', 'わらび餅・水ようかん'), ('金物 まつだ', ''),
               ('理容 ひかり', ''), ('おおかわ薬局', ''), ('写真館 かもめ', ''), ('とうふ 豆福', '')]
    shops_e = [('喫茶 かわせみ', '珈琲・クリームソーダ'), ('呉服 さくら屋', ''), ('おもちゃ ゆめや', ''), ('パン ほのか', ''),
               ('花 ことり', ''), ('時計 タナベ', ''), ('茶 あおば', '宇治・八女'), ('酒 大川屋', '')]
    z = -70
    for i in range(8):
        z1, z2 = z + i * 11, z + i * 11 + 10
        nm, sub = shops_w[i]
        shop_front(w, 588, z1, 597, z2, 'east', nm, sub, rnd, noren='noren_indigo' if i % 2 else None)
        nm, sub = shops_e[i]
        if i == 0:
            kissa(w, 605, z1, 614, z2)
        else:
            shop_front(w, 605, z1, 614, z2, 'west', nm, sub, rnd, noren='noren_red' if i % 3 == 0 else None)
    # arcade roof
    for z in range(-72, 21):
        for x in range(598, 605):
            w.set(x, G + 9, z, 'minecraft:light_blue_stained_glass' if 599 <= x <= 603 else 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
        if z % 8 == 0:
            for x in range(598, 605):
                w.set(x, G + 9, z, 'minecraft:smooth_stone')
            w.set(601, G + 8, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
    w.sign(599, G + 8, 21, 'tabi:shop_sign', 'south', ['おおかわ駅前商店街'], style='shop', span=5)
    for x in range(600, 604):
        w.set(x, G + 8, 21, facing(TB('shop_sign'), 'south'))
    for (x, z, skin, nm) in ((600, -40, 'townsman', '町の人'), (602, -10, 'townswoman', '町の人'), (601, -60, 'obaachan', '町の人'), (603, 5, 'student', '町の人')):
        w.npc(x, G + 1, z, 'townsfolk', skin, nm, 'town', 'wander', yaw=0, radius=10)


def kissa(w, x1, z1, x2, z2):
    """喫茶 かわせみ: retro coffee shop with counter, red seats, cream soda"""
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    w.fill(x1, G + 1, z1, x2, G + 8, z2, TB('tile_brown'))
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 7, z2 - 1, AIR)
    w.fill(x1 + 1, G, z1 + 1, x2 - 1, G, z2 - 1, 'minecraft:red_terracotta')
    w.fill(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 4, z2 - 1, 'minecraft:dark_oak_planks')
    w.fill(x1, G + 1, z1 + 2, x1, G + 3, z2 - 2, pane('minecraft:brown_stained_glass_pane', n=True, s=True))
    w.set(x1, G + 1, z1 + 5, 'minecraft:dark_oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]')
    w.set(x1, G + 2, z1 + 5, 'minecraft:dark_oak_door[facing=east,half=upper,hinge=left,open=false,powered=false]')
    w.set(x1, G + 3, z1 + 5, 'tabi:tile_brown')
    # counter along the east wall
    w.fill(x2 - 2, G + 1, z1 + 2, x2 - 2, G + 1, z2 - 2, 'minecraft:dark_oak_planks')
    w.set(x2 - 1, G + 1, z1 + 3, 'minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=false]')
    w.set(x2 - 1, G + 1, z1 + 5, 'minecraft:barrel[facing=up,open=false]')
    for z in range(z1 + 2, z2 - 1, 2):
        w.set(x2 - 3, G + 1, z, facing(TB('zabuton'), 'east')) if False else w.set(x2 - 3, G + 1, z, 'minecraft:red_wool')
    # booth seats
    for z in (z1 + 2, z1 + 6):
        w.set(x1 + 2, G + 1, z, stairs('minecraft:red_terracotta' if False else 'minecraft:dark_oak_stairs', 'west'))
        w.set(x1 + 3, G + 1, z, 'minecraft:dark_oak_fence')
        w.set(x1 + 3, G + 2, z, 'minecraft:dark_oak_pressure_plate')
        w.set(x1 + 4, G + 1, z, stairs('minecraft:dark_oak_stairs', 'east'))
    for z in (z1 + 3, z1 + 7):
        w.set(x1 + 3, G + 3, z, 'minecraft:lantern[hanging=true,waterlogged=false]')
    w.sign(x1 - 1, G + 5, z1 + 1, 'tabi:neon_sign', 'west', ['喫茶 かわせみ', 'COFFEE'], style='neon', span=z2 - z1 - 1, glow=True)
    for z in range(z1 + 2, z2):
        w.set(x1 - 1, G + 5, z, facing(TB('neon_sign'), 'west'))
    w.fill(x1 - 1, G + 4, z1, x1 - 1, G + 4, z2, 'minecraft:red_concrete')
    w.set(x1 + 2, G + 1, z2 - 1, TB('cake_showcase') + '[facing=west]')
    gable_roof(w, x1, z1, x2, z2, G + 9, 'x', 'tabi:kawara', overhang=0, gable_wall=TB('tile_brown'))
    w.npc(x2 - 1, G + 1, z1 + 4, 'kawasemi', 'master', '喫茶かわせみのマスター', '', 'stand', yaw=90)


def ayutei(w):
    x1, x2, z1, z2 = 562, 576, -10, 4
    w.fill(x1, G, z1, x2, G, z2, 'minecraft:smooth_stone')
    w.fill(x1, G + 1, z1, x2, G + 5, z2, 'tabi:shikkui')
    w.fill(x1, G + 1, z1, x2, G + 2, z2, 'tabi:yakisugi')
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 4, z2 - 1, AIR)
    w.fill(x1 + 1, G, z1 + 1, x2 - 1, G, z2 - 1, 'tabi:hinoki')
    for x in range(x1, x2 + 1, 3):
        w.fill(x, G + 1, z1, x, G + 5, z1, axis('tabi:hashira', 'y'))
        w.fill(x, G + 1, z2, x, G + 5, z2, axis('tabi:hashira', 'y'))
    # entrance south with noren
    w.fill(568, G + 1, z2, 570, G + 3, z2, AIR)
    for x in (568, 570):
        w.set(x, G + 3, z2, facing(TB('noren_indigo'), 'south'))
    # open west side onto a riverside terrace (kawadoko style)
    w.fill(x1, G + 1, z1 + 2, x1, G + 3, z2 - 2, AIR)
    for z in range(z1 + 2, z2 - 1):
        w.set(x1, G + 3, z, facing(TB('sudare'), 'west'))
    w.fill(RX2 + 2, G, z1, x1 - 1, G, z2, 'tabi:hinoki')
    for z in range(z1, z2 + 1):
        w.set(RX2 + 2, G + 1, z, 'minecraft:spruce_fence')
    for z in (z1 + 2, z1 + 7, z1 + 12):
        if z <= z2:
            w.set(RX2 + 4, G + 1, z, TB('chabudai'))
            w.set(RX2 + 3, G + 1, z, facing(TB('zabuton'), 'east'))
            w.set(RX2 + 5, G + 1, z, facing(TB('zabuton'), 'west'))
    w.set(RX2 + 2, G + 3, z1 + 4, TB('chochin'))
    w.set(RX2 + 2, G + 3, z1 + 10, TB('chochin'))
    # inside: grill and counter
    w.set(x2 - 1, G + 1, z1 + 1, 'minecraft:campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]')
    w.set(x2 - 2, G + 1, z1 + 1, 'minecraft:smoker[facing=south,lit=true]')
    w.fill(x2 - 4, G + 1, z1 + 3, x2 - 1, G + 1, z1 + 3, 'minecraft:spruce_planks')
    for x in (x1 + 3, x1 + 7):
        w.set(x, G + 1, z1 + 6, TB('chabudai'))
        w.set(x, G + 1, z1 + 5, facing(TB('zabuton'), 'south'))
        w.set(x, G + 1, z1 + 7, facing(TB('zabuton'), 'north'))
    w.set(x1 + 5, G + 4, z1 + 6, 'minecraft:lantern[hanging=true,waterlogged=false]')
    w.sign(566, G + 6, z2 + 1, 'tabi:shop_sign', 'south', ['川魚料理 あゆ亭', '鮎の塩焼き'], style='shop', span=7)
    for k in range(1, 7):
        w.set(566 + k, G + 6, z2 + 1, facing(TB('shop_sign'), 'south'))
    hip_roof(w, x1, z1, x2, z2, G + 6 + 0, 'tabi:kawara', overhang=1)
    w.npc(x2 - 3, G + 1, z1 + 2, 'ayutei', 'chef', 'あゆ亭の大将', '', 'stand', yaw=0)
    w.npc(RX2 + 2, G + 1, -24, 'okawa_fisher', 'fisher', '釣り人のおじさん', 'river', 'stand', yaw=90, hold='minecraft:fishing_rod')


def build(w, rnd=None):
    rnd = rnd or random.Random(77)
    river(w)
    # main road continues over the bridge to the station plaza
    pts = fillet_path([(530, 24), (668, 24)], 10, 1.0)
    road(w, pts, G, width=7, center='white', sidewalk=1)
    station(w)
    shotengai(w, rnd)
    ayutei(w)
    for x in range(568, 670, 30):
        utility_pole(w, x, G + 1, 19, 'north')
    # houses around the town
    spots = [(470, -60), (480, -20), (470, 60), (500, 90), (640, -40), (660, -10), (690, -60), (700, 0), (520, -90), (650, -90),
             (690, 80), (720, 40), (580, 90), (620, 100), (470, 110)]
    for (x, z) in spots:
        if 580 <= x <= 620 and -75 <= z <= 25:
            continue
        jp_house(w, x - 5, z - 4, x + 5, z + 4, G, rnd, door=rnd.choice(['south', 'north', 'east', 'west']))
    for i in range(40):
        x, z = rnd.randrange(430, 780), rnd.randrange(-150, 150)
        if 540 <= x <= 680 and -80 <= z <= 60:
            continue
        g = w.ground(x, z)
        if g >= G and w.is_air(x, g + 1, z):
            tree(w, x, g + 1, z, rnd.choice(['broad', 'cedar', 'broad']))
    w.npc(560, G + 1, 30, 'village_kid', 'kid_boy', '町の子ども', 'village_kids', 'stand', yaw=0)
    w.area('okawa', '大川町', '川と駅のある町', (420, 40, -200, 790, 140, 200), 'spot.okawa')
    w.area('okawa_station', '大川駅', '川霧線 始発駅', (600, 60, 30, 655, 85, 50))
    w.check(632, G + 1, 35, 'ticket machine')
    w.viewpoint('okawa_station', 626, 74, 18, 0, 20, time=6000)
    w.viewpoint('shotengai', 601, 70, 18, 180, 5, time=6000)
    w.viewpoint('okawa_river', 551, 72, -40, 0, 15, time=11000)
    fix_stairs(w, 540, 60, -80, 680, 90, 50)
    fix_connections(w, 540, 60, -130, 680, 90, 60)
