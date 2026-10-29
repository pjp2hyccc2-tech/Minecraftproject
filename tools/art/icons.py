#!/usr/bin/env python3
"""Generates every 16x16 item icon of HuntCraft core items + armor module items, plus item models.

    python3 tools/art/icons.py            # textures + models + contact sheets (tools/preview/icons_*.png)
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pix import Canvas, ramp, hx, mix, sheet, to_hex, lighten_ramp  # noqa: E402
import icons_lib as L  # noqa: E402
from icons_lib import GOLD, IVORY, IVORY_DK, BONE, WOOD, LEATHER, IRON, GLASS, WHITE  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
TEX = os.path.join(ASSETS, 'textures', 'item')
MODELS = os.path.join(ASSETS, 'models', 'item')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')

ICONS = {}       # id -> Canvas
ORDER = []


def icon(name):
    def deco(fn):
        ORDER.append((name, fn))
        return fn
    return deco


def reg(name, c):
    ICONS[name] = c


# ----------------------------------------------------------------------------- monster palettes
MON = {
    'ignivald': {'main': ramp('#c23a16'), 'dark': ramp('#6e1a0e'), 'acc': ramp('#8c221a'), 'tip': IVORY,
                 'gem': ['#3a0505', '#9a1010', '#d42a1e', '#ff6a50', '#ffd0c0'], 'glow': '#ffb347', 'belly': ramp('#c8954e')},
    'frostgarm': {'main': ramp('#8fb3d6'), 'dark': ramp('#465772'), 'acc': ramp('#cde2ef'), 'tip': IVORY,
                  'gem': ['#123a56', '#2e86b8', '#62c4ec', '#b0ecff', '#ffffff'], 'glow': '#bff4ff'},
    'voltrix': {'main': ramp('#2c587c'), 'dark': ramp('#18222f'), 'acc': ramp('#e8c020'), 'tip': ramp('#d8d6bd'),
                'gem': ['#4a3c00', '#c49a00', '#f2d100', '#fff27a', '#ffffff'], 'glow': '#ffe55a'},
    'gravadon': {'main': ramp('#7d6e5f'), 'dark': ramp('#4a382c'), 'acc': ramp('#4d8030'), 'tip': IVORY,
                 'gem': ['#2a1806', '#8a5a14', '#c88a24', '#f0c45a', '#fff0b8'], 'glow': '#f0c45a'},
    'azragos': {'main': ramp('#3e2563'), 'dark': ramp('#1d1130'), 'acc': ramp('#5e3880'), 'tip': ramp('#4f4656'),
                'gem': ['#2a0626', '#8a1466', '#d02a92', '#ff7ac8', '#ffe0f4'], 'glow': '#ff4fc0'},
}


def plus_palette(P):
    Q = dict(P)
    for k in ('main', 'dark', 'acc', 'tip', 'gem'):
        if k in P:
            Q[k] = lighten_ramp(P[k], 0.16)
    return Q


def upgrade(c, P):
    L.plus_mark(c)
    return c


def monster_mats():
    spec = {
        'ignivald': [('scale', 'scale'), ('shell', 'shell'), ('webbing', 'membrane'), ('claw', 'claw'), ('ruby', 'gem_faceted')],
        'frostgarm': [('fur', 'fur'), ('hide', 'hide'), ('fang', 'fang'), ('claw', 'claw'), ('frostgem', 'crystal')],
        'voltrix': [('scale', 'scale'), ('shell', 'shell'), ('electrode', 'electrode'), ('fang', 'fang'), ('thunderorb', 'orb')],
        'gravadon': [('plate', 'rock_plate'), ('shell', 'shell'), ('skull', 'skull'), ('tusk', 'tusk'), ('earthgem', 'gem_faceted')],
        'azragos': [('scale', 'scale'), ('shell', 'shell'), ('horn', 'horn'), ('claw', 'claw'), ('orb', 'orb')],
    }
    tails = {'ignivald': 'spike', 'frostgarm': 'fluff', 'voltrix': 'fin', 'gravadon': 'club', 'azragos': 'blade'}

    def draw(kind, P, mid):
        if kind == 'scale':
            return L.scale(P)
        if kind == 'fur':
            return L.fur(P)
        if kind == 'rock_plate':
            return L.rock_plate(P)
        if kind == 'shell':
            return L.shell(P)
        if kind == 'hide':
            return L.hide(P, furry=True)
        if kind == 'membrane':
            return L.membrane(P)
        if kind == 'claw':
            return L.claw(P)
        if kind == 'fang':
            return L.fang(P, ice=P['gem'] if mid == 'frostgarm' else None)
        if kind == 'tusk':
            return L.tusk(P)
        if kind == 'horn':
            return L.horn(P, glow=P['gem'])
        if kind == 'gem_faceted':
            return L.gem_faceted(P)
        if kind == 'crystal':
            return L.crystal_cluster(P)
        if kind == 'orb':
            return L.gem_orb(P, motif='bolt' if mid == 'voltrix' else 'flame')
        if kind == 'electrode':
            return L.electrode(P)
        if kind == 'skull':
            return L.skull_plate(P)
        raise ValueError(kind)

    for mid, slots in spec.items():
        P = MON[mid]
        Q = plus_palette(P)
        for sid, kind in slots:
            reg('%s_%s' % (mid, sid), draw(kind, P, mid))
            reg('%s_%s_plus' % (mid, sid), upgrade(draw(kind, Q, mid), Q))
        reg('%s_tail' % mid, L.tail(P, tails[mid]))


def pincer(rp, rock):
    c = Canvas()
    arm = c.stroke([(2.0, 14.0), (6.0, 9.6)], 4.6, 4.0)
    c.layer(arm, rock, shade='cyl')
    top = L.crescent(c, (4.6, 10.4), (4.0, 1.0), (14.6, 2.8), (8.6, 4.6), (8.4, 9.8))
    c.layer(top, rp, shade='cyl', bulge=1.1)
    bot = L.crescent(c, (6.6, 12.2), (12.0, 13.6), (15.0, 7.4), (11.0, 10.0), (7.0, 10.0))
    c.layer(bot, rp, shade='cyl', bulge=1.1)
    for (x, y) in ((6, 5), (7, 4)):
        c.px_on(x, y, rp[4])
    return c


def small_mats():
    blue = ramp('#2e5f94')
    orange = ramp('#d0661e')
    white = ['#3c4a59', '#8ba0b4', '#c4d4e2', '#e4eef6', '#ffffff']
    icy = ramp('#3a8fc4')
    brown = ramp('#7a5434')
    tan = ramp('#a8875e')
    reg('jagraptor_hide', L.hide({'main': blue}, stripes=orange))
    reg('jagraptor_fang', L.fang({'tip': IVORY, 'dark': ramp('#2e3e5a')}))
    reg('blizraptor_hide', L.hide({'main': white}, stripes=icy))
    reg('blizraptor_crest', L.feather_crest(['#2a3a4a', '#7fa8c8', '#bcd8ee', '#e2f0fa', '#ffffff'], '#3a8fc4'))
    reg('boarfang_hide', L.hide({'main': brown}, furry=True))
    reg('boarfang_tusk', L.tusk({'tip': IVORY, 'dark': ramp('#5a3a22')}))
    reg('grasica_antler', L.antler(tan))
    reg('gamenos_shell', L.turtle_shell(ramp('#4a7a52'), ramp('#3a3a24')))
    reg('buzzling_stinger', L.stinger(ramp('#3a3418'), '#b04cff'))
    reg('buzzling_wing', L.insect_wing(['#3a4a52', '#9ab8c4', '#c8e0e8', '#e8f6fa', '#ffffff']))
    reg('yukimammo_fur', L.fur({'main': ramp('#9a7c62'), 'dark': ramp('#5a463a'), 'acc': ramp('#c2a690')}))
    reg('yukimammo_tusk', L.tusk({'tip': ['#4a4436', '#b8b09a', '#e0dac8', '#f4f0e4', '#ffffff'], 'dark': ramp('#6a5a4a')}))
    gecko = L.scale({'main': ramp('#d0601a')})
    for (x, y) in ((7, 6), (9, 9), (6, 10)):
        gecko.px_on(x, y, '#ffd060')
    reg('heatgecko_scale', gecko)
    reg('ganseki_shell', L.shell({'main': ramp('#99492c'), 'tip': ramp('#736a61')}))
    reg('ganseki_claw', pincer(ramp('#b0553a'), ramp('#736a61')))
    reg('mofu_coin', L.coin(GOLD))


def generic_parts():
    reg('monster_fluid', L.honey_drop(ramp('#8a3a9a')))
    reg('flame_sac', L.sac({'main': ramp('#b8441e'), 'dark': ramp('#5a1a0c')}, ramp('#ffb030')))
    reg('frost_sac', L.sac({'main': ramp('#6a9ac8'), 'dark': ramp('#2a4a6a')}, ramp('#d8f6ff')))
    reg('thunder_sac', L.sac({'main': ramp('#c8a020'), 'dark': ramp('#5a4a10')}, ramp('#fff6a0')))
    reg('aqua_sac', L.sac({'main': ramp('#2a6ab8'), 'dark': ramp('#12305a')}, ramp('#8ad0ff')))
    reg('poison_sac', L.sac({'main': ramp('#7a3aa8'), 'dark': ramp('#3a1a5a')}, ramp('#c890ff')))
    reg('monster_bone_s', L.bone_icon('s'))
    reg('monster_bone_m', L.bone_icon('m'))
    reg('monster_bone_l', L.bone_icon('l'))
    reg('raw_meat', L.meat(ramp('#c03a30'), ramp('#f0d0c0')))


def gathering():
    green = ramp('#4a9a2e')
    reg('herb', L.leaves(green))
    reg('antidote_herb', L.leaves(ramp('#2e9a8a'), broad=0.85, tint=('#d8fff4', [(3, 6), (12, 6), (7, 3)])))
    reg('fire_herb', L.leaves(ramp('#d0502a'), stem=ramp('#5a3a1a'), tint=('#ffd060', [(3, 6), (12, 6), (8, 2), (7, 3)])))
    reg('blue_mushroom', L.mushroom(ramp('#2e6ad0'), spots='#d8f0ff'))
    reg('numb_mushroom', L.mushroom(ramp('#e0b020'), spots='#7a4a10'))
    reg('honey', L.honey_drop(ramp('#e8a020')))
    reg('might_seed', L.seed(ramp('#c83a2a'), spot='#ffd0a0'))
    reg('adamant_seed', L.seed(ramp('#c8a030'), spot='#fff0b0'))
    reg('sticky_grass', L.vine(ramp('#5aa02a')))
    reg('spider_web', L.web())
    reg('flashbug', L.bug(ramp('#4a4030'), wing=['#3a4a52', '#9ab8c4', '#c8e0e8', '#e8f6fa', '#ffffff'],
                          glow=['#8a8a50', '#e8e8a0', '#fafad0', '#ffffff', '#ffffff']))
    reg('thunderbug', L.bug(ramp('#3a4a20'), wing=['#3a4a52', '#9ab8c4', '#c8e0e8', '#e8f6fa', '#ffffff'],
                            glow=['#5a6a00', '#c8d820', '#eef060', '#ffffa0', '#ffffff']))
    reg('bitterbug', L.bug(ramp('#5a6a2a')))
    reg('sleep_herb', L.flower(ramp('#9a6ad8')))
    reg('blast_nut', L.nut(ramp('#8a5a30')))
    reg('ice_crystal', L.crystal_cluster({'gem': ['#174a6a', '#3c93c4', '#7cd0f2', '#c0f0ff', '#ffffff']}))
    reg('hot_pepper', L.pepper(ramp('#d42a1e')))
    reg('earth_crystal', L.crystal_cluster({'gem': ['#5a5a6a', '#b0b4c4', '#dcdee8', '#f2f4fa', '#ffffff']}))
    reg('azurite_ore', L.ore(ramp('#6a6260'), ['#0a2a5a', '#2a6ad0', '#4a9af0', '#9ad0ff', '#ffffff']))
    reg('crimsonite_ore', L.ore(ramp('#5a4a48'), ['#4a0808', '#a81a1a', '#e03a2a', '#ff8a6a', '#ffe0d0']))
    nova = L.crystal_cluster({'gem': ['#6a5a20', '#e8d070', '#fff4b0', '#ffffe0', '#ffffff']})
    nova.sparkle(13, 3, '#ffffff', '#fff4b0')
    nova.sparkle(3, 4, '#ffffff', '#fff4b0')
    reg('novacrystal', nova)
    reg('lightcrystal', L.crystal_cluster({'gem': ['#5a6a30', '#d8e8a0', '#f4fad0', '#ffffff', '#ffffff']}, n=2))
    reg('armor_sphere', L.sphere(['#2a2c30', '#6a707a', '#959ca6', '#c0c6ce', '#e6eaee']))
    reg('armor_sphere_plus', L.sphere(['#14284a', '#3a6aa8', '#6a9ad0', '#a8ccf0', '#e8f4ff'], band=GOLD))
    hard = L.sphere(['#100c14', '#3a3248', '#5a4e6e', '#8a7aa0', '#c8b8e0'], band=GOLD)
    hard.sparkle(12, 4, '#ffffff', '#f6dc8a')
    reg('hard_armor_sphere', hard)


def consumables():
    green = ['#0a3a14', '#2a8a30', '#4ac04a', '#8ae878', '#d0ffc0']
    reg('potion', L.flask(green, 'round'))
    reg('mega_potion', L.flask(['#0a3a2a', '#20a070', '#40d8a0', '#90f8d0', '#e0fff4'], 'tall', label=GOLD))
    mp = L.flask(['#5a3a00', '#d09010', '#f8c830', '#fff080', '#ffffd0'], 'vial')
    mp.sparkle(12, 3, '#ffffff', '#fff4b0')
    reg('max_potion', mp)
    fa = L.flask(['#0a3a14', '#2a8a30', '#4ac04a', '#8ae878', '#d0ffc0'], 'vial')
    fa.px(7, 10, '#ffffff')
    fa.px(8, 10, '#ffffff')
    fa.px(7, 9, '#e02020')
    fa.px(8, 11, '#e02020')
    reg('first_aid_med', fa)
    reg('antidote', L.flask(['#0a2a4a', '#2a6ad0', '#4a9af0', '#9ad0ff', '#e8f6ff'], 'round'))
    reg('energy_drink', L.flask(['#4a3a00', '#c8a010', '#f0d030', '#fff080', '#ffffd0'], 'bottle'))
    reg('hot_drink', L.flask(['#4a0a0a', '#c02a1a', '#f04a2a', '#ff9a6a', '#ffe0d0'], 'bottle'))
    reg('cool_drink', L.flask(['#0a2a4a', '#2a8ad0', '#5ac8f0', '#a8ecff', '#f0ffff'], 'bottle'))
    reg('demondrug', L.flask(['#3a0606', '#8a1414', '#c82020', '#f05a4a', '#ffc0b0'], 'gourd'))
    reg('armorskin', L.flask(['#4a2a00', '#b86a10', '#e89a20', '#ffc860', '#fff0c0'], 'gourd'))
    reg('ration', L.ration(ramp('#c8a46a'), ramp('#6a4a2a')))
    reg('well_done_steak', L.meat(ramp('#9a5424'), ramp('#e8b878')))
    reg('rare_steak', L.meat(ramp('#c8564a'), ramp('#f0c0a8')))
    reg('burnt_meat', L.meat(ramp('#3a2a22'), ramp('#5a4a40'), char=True))
    reg('bbq_spit', L.bbq_spit())
    reg('paintball', L.ball(ramp('#e050a0'), mark=('#ffb0e0', [(9, 10), (10, 9), (10, 11)])))
    reg('flash_bomb', L.ball(['#5a5a3a', '#d8d890', '#f4f4c8', '#ffffee', '#ffffff'], rays='#fff4a0'))
    reg('dung_bomb', L.ball(ramp('#6a4a2a'), flies=True))
    reg('tranq_bomb', L.ball(ramp('#9a7ad0'), mark=('#f0e0ff', [(9, 8), (10, 8), (9, 10), (10, 10), (10, 9)])))
    reg('pitfall_trap', L.trap_pitfall())
    reg('shock_trap', L.trap_shock())
    reg('farcaster', L.smoke_ball(ramp('#3a9a4a'), '#c8f0d0'))
    reg('guild_pass', L.badge())
    reg('hunter_notes', L.book(ramp('#7a4a2a'), strap=ramp('#4a2a14')))
    skull = [(x, y, '#e8e0c8') for (x, y) in ((7, 8), (8, 8), (9, 8), (6, 9), (8, 9), (10, 9), (7, 10), (9, 10))] + \
            [(7, 9, '#1a0a06'), (9, 9, '#1a0a06'), (8, 10, '#1a0a06')]
    reg('barrel_bomb', L.barrel_icon(ramp('#b8301c'), IRON, skull))
    flame = [(8, 7, '#ffd860'), (7, 8, '#ff8a24'), (8, 8, '#fff0a0'), (9, 8, '#ff8a24'), (7, 9, '#ee5a10'), (8, 9, '#ffd860'),
             (9, 9, '#ee5a10'), (8, 10, '#c42a08')]
    reg('barrel_bomb_plus', L.barrel_icon(ramp('#7a1414'), GOLD, flame))


# ----------------------------------------------------------------------------- armor icons
def armor_icons():
    A = {
        'hunter': {'main': ramp('#8a5a32'), 'dark': ramp('#4a3020'), 'brow': ['#1b1e23', '#4a525c', '#6f7884', '#98a2ad', '#d0d8e0'],
                   'pad': ramp('#7a4e2c'), 'belt': ramp('#3a2414'), 'legs': ramp('#3a4252'), 'visor': 'open',
                   'helm': [L.deco_layer(lambda c: c.stroke([(12.6, 6.6), (15.2, 0.6)], 2.2, 0.8), ramp('#c4321f'), 'cyl')],
                   'mail': [L.deco_px_on([(x, y, '#2a5275') for x in (7, 8) for y in range(3, 11)] + [(7, 6, '#d9aa4c'), (8, 6, '#f5d88a')])]},
        'bone': {'main': BONE, 'dark': ramp('#5a4632'), 'visor': 'mask', 'pad': BONE, 'belt': ramp('#7a2a1f'), 'legs': ramp('#4a3626'),
                 'cuff': ramp('#6a5848'),
                 'helm': [L.deco_horns(IVORY, 1.0)],
                 'mail': [L.deco_px_on([(x, y, '#5a4632') for y in (6, 9) for x in range(4, 12) if x not in (7, 8)])]},
        'jagraptor': {'main': ramp('#2e5f94'), 'dark': ramp('#1a2232'), 'pad': ramp('#2e5f94'), 'visor': 'open',
                      'belt': ramp('#1a2232'), 'legs': ramp('#2e5f94'), 'cuff': ramp('#d0661e'),
                      'helm': [L.deco_crest(ramp('#d0661e'), 4.4)],
                      'mail': [L.deco_px_on([(x, y, '#cfc9ad') for x in (6, 7, 8, 9) for y in range(5, 14)] +
                                            [(4, 6, '#de7c34'), (4, 9, '#de7c34'), (11, 6, '#de7c34'), (11, 9, '#de7c34')])]},
        'ignivald': {'main': ramp('#c23a16'), 'dark': ramp('#5a140b'), 'brow': ramp('#4f2716'), 'visor': 'slit', 'eye': '#ffb347',
                     'pad': ramp('#d65a26'), 'belt': ramp('#4f2716'), 'legs': ramp('#a52a12'),
                     'helm': [L.deco_horns(IVORY, 1.1)],
                     'mail': [L.deco_px_on([(x, y, '#c8954e') for x in (6, 7, 8, 9) for y in range(7, 13)] +
                                           [(7, 5, '#ff3b2a'), (8, 5, '#ffc0b0'), (7, 6, '#8a0e0a'), (8, 6, '#ff3b2a')])]},
        'frostgarm': {'main': ramp('#8fb3d6'), 'dark': ramp('#33415a'), 'visor': 'open', 'pad': ramp('#cde2ef'),
                      'belt': ramp('#28354a'), 'legs': ramp('#465772'), 'cuff': ramp('#cde2ef'),
                      'helm': [L.deco_ears(ramp('#8fb3d6')), L.deco_px([(7, 3, '#bff4ff'), (8, 3, '#ecfdff'), (7, 4, '#4fa9d3'), (8, 4, '#bff4ff')])],
                      'mail': [L.deco_px_on([(7, 6, '#bff4ff'), (8, 6, '#ecfdff'), (7, 7, '#4fa9d3'), (8, 7, '#bff4ff'), (7, 8, '#2e7aa3')])]},
        'voltrix': {'main': ramp('#2c587c'), 'dark': ramp('#18222f'), 'brow': ramp('#2f3f55'), 'visor': 'slit', 'eye': '#ffe55a',
                    'pad': ramp('#2c587c'), 'belt': ramp('#18222f'), 'legs': ramp('#2c587c'), 'cuff': ramp('#2f3f55'),
                    'helm': [L.deco_crest(ramp('#33698e'), 5.0), L.deco_px([(8, 0, '#ffe55a'), (8, 1, '#ffe55a')])],
                    'mail': [L.deco_px_on([(4, y, '#ffe55a') for y in range(4, 12)] + [(11, y, '#ffe55a') for y in range(4, 12)])],
                    'greaves': [L.deco_px_on([(x, 7, '#ffe55a') for x in list(range(2, 7)) + list(range(9, 14))])]},
        'gravadon': {'main': ramp('#7d6e5f'), 'dark': ramp('#4a382c'), 'brow': ramp('#4d8030'), 'visor': 'open', 'pad': ramp('#7d6e5f'),
                     'belt': ramp('#3a332c'), 'legs': ramp('#5f4a3b'), 'cuff': ramp('#4d8030'),
                     'helm': [L.deco_horns(IVORY, 0.8)],
                     'mail': [L.deco_px_on([(3, 2, '#548039'), (4, 2, '#73a04e'), (12, 2, '#548039'), (13, 2, '#73a04e'), (6, 7, '#4a4038'), (9, 9, '#4a4038')])]},
        'azragos': {'main': ramp('#3e2563'), 'dark': ramp('#1d1130'), 'brow': ramp('#a87a22'), 'visor': 'slit', 'eye': '#ff4fc0',
                    'pad': ramp('#3a245c'), 'belt': ramp('#a87a22'), 'legs': ramp('#2c1a47'), 'cuff': ramp('#a87a22'),
                    'helm': [L.deco_horns(ramp('#4f4656'), 1.2), L.deco_px([(7, 6, '#ff4fc0'), (8, 6, '#ffc2ec')])],
                    'mail': [L.deco_px_on([(7, 5, '#ff4fc0'), (8, 5, '#ffc2ec'), (7, 6, '#ff4fc0'), (8, 6, '#ff4fc0')] +
                                          [(7, y, '#ff4fc0') for y in range(7, 11)] + [(8, y, '#c21f7d') for y in range(7, 11)])]},
    }
    for sid, a in A.items():
        reg(sid + '_helm', L.helm_icon(a))
        reg(sid + '_mail', L.mail_icon(a))
        reg(sid + '_coil', L.coil_icon(a))
        reg(sid + '_greaves', L.greaves_icon(a))


def charms():
    reg('charm', L.charm_icon(ramp('#7a8a6a'), ramp('#6a4a2a'), '#3a4430'))
    reg('charm_t2', L.charm_icon(ramp('#3a7ad0'), ramp('#8a6a3a'), '#e8f6ff', frame=GOLD))
    reg('charm_t3', L.charm_icon(ramp('#7a2ab0'), ramp('#5a1a2a'), '#ff9ad8', glow=True, frame=GOLD))


# ----------------------------------------------------------------------------- build
def build_all():
    monster_mats()
    small_mats()
    generic_parts()
    gathering()
    consumables()
    armor_icons()
    charms()


def write_models(names):
    os.makedirs(MODELS, exist_ok=True)
    for n in names:
        if n in ('charm_t2', 'charm_t3'):
            model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'huntcraft:item/' + n}}
        elif n == 'charm':
            model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'huntcraft:item/charm'},
                     'overrides': [{'predicate': {'huntcraft:tier': 0.5}, 'model': 'huntcraft:item/charm_t2'},
                                   {'predicate': {'huntcraft:tier': 0.75}, 'model': 'huntcraft:item/charm_t3'}]}
        else:
            model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'huntcraft:item/' + n}}
        with open(os.path.join(MODELS, n + '.json'), 'w') as f:
            json.dump(model, f, indent=1)


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    build_all()
    names = sorted(ICONS)
    for n in names:
        ICONS[n].save(os.path.join(TEX, n + '.png'))
    write_models(names)
    imgs = [ICONS[n].image() for n in names]
    per = 60
    for k in range(0, len(names), per):
        sheet(imgs[k:k + per], names[k:k + per], os.path.join(PREVIEW, 'icons_%d.png' % (k // per)), scale=5, cols=10)
    print('icons:', len(names))


if __name__ == '__main__':
    main()
