"""Generates blockstates, models, particles, loot tables, recipes, tags, damage types, advancements and the
GameTest arena structure for 殺人喜."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from nbt import Structure  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources'))
A = os.path.join(ROOT, 'assets', 'satsujinki')
D = os.path.join(ROOT, 'data')
NS = 'satsujinki'


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def bs(name, variants):
    w(os.path.join(A, 'blockstates', name + '.json'), {'variants': variants})


def bm(name, obj):
    w(os.path.join(A, 'models', 'block', name + '.json'), obj)


def im(name, obj):
    w(os.path.join(A, 'models', 'item', name + '.json'), obj)


def t(path):
    return f'{NS}:{path}'


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def blockstates_and_models():
    # simple cubes
    bm('mixer_casing', {'parent': 'minecraft:block/cube_bottom_top',
                        'textures': {'top': t('block/mixer_casing_top'), 'bottom': t('block/mixer_casing_top'), 'side': t('block/mixer_casing')}})
    bs('mixer_casing', {'': {'model': t('block/mixer_casing')}})
    bm('mixer_core', {'parent': 'minecraft:block/cube_bottom_top',
                      'textures': {'top': t('block/mixer_core_top'), 'bottom': t('block/mixer_casing_top'), 'side': t('block/mixer_core_side')}})
    bs('mixer_core', {'powered=false': {'model': t('block/mixer_core')}, 'powered=true': {'model': t('block/mixer_core')}})
    for lvl in range(4):
        name = 'mixer_glass' if lvl == 0 else f'mixer_glass_blood_{lvl}'
        bm(name, {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:cutout', 'textures': {'all': t('block/' + name)}})
    bs('mixer_glass', {f'blood={lvl}': {'model': t('block/' + ('mixer_glass' if lvl == 0 else f'mixer_glass_blood_{lvl}'))} for lvl in range(4)})
    bm('press_head', {'parent': 'minecraft:block/cube_bottom_top',
                      'textures': {'top': t('block/press_head_top'), 'bottom': t('block/press_head_bottom'), 'side': t('block/press_head_side')}})
    bs('press_head', {'powered=false': {'model': t('block/press_head')}, 'powered=true': {'model': t('block/press_head')}})
    for name in ('execution_tiles', 'bloody_tiles', 'rusted_plate'):
        bm(name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': t('block/' + name)}})
        bs(name, {'': {'model': t('block/' + name)}})

    # spike bed: base plate + 4x4 spikes with tips
    for lvl in range(3):
        sfx = '' if lvl == 0 else f'_blood_{lvl}'
        elements = [{'from': [0, 0, 0], 'to': [16, 3, 16], 'faces': {
            'down': {'texture': '#base', 'cullface': 'down'}, 'up': {'texture': '#base'},
            'north': {'texture': '#base', 'cullface': 'north'}, 'south': {'texture': '#base', 'cullface': 'south'},
            'west': {'texture': '#base', 'cullface': 'west'}, 'east': {'texture': '#base', 'cullface': 'east'}}}]
        for cx in (2, 6, 10, 14):
            for cz in (2, 6, 10, 14):
                hgt = 10 if (cx + cz) % 8 == 0 else 9
                shaft = {'from': [cx - 0.75, 3, cz - 0.75], 'to': [cx + 0.75, hgt, cz + 0.75],
                         'faces': {f: {'texture': '#spike'} for f in ('north', 'south', 'west', 'east', 'up')}}
                tip = {'from': [cx - 0.3, hgt, cz - 0.3], 'to': [cx + 0.3, hgt + 2.5, cz + 0.3],
                       'faces': {f: {'texture': '#spike', 'uv': [6, 0, 7, 3]} for f in ('north', 'south', 'west', 'east', 'up')}}
                elements += [shaft, tip]
        bm('spike_bed' + sfx, {'ambientocclusion': False, 'textures': {'particle': t('block/spike_bed_base' + sfx),
                                                                         'base': t('block/spike_bed_base' + sfx),
                                                                         'spike': t('block/spike_bed_spike' + sfx)},
                               'elements': elements})
    bs('spike_bed', {f'blood={lvl}': {'model': t('block/spike_bed' + ('' if lvl == 0 else f'_blood_{lvl}'))} for lvl in range(3)})

    # saw table (slot runs along Z for facing=north)
    bm('saw', {'parent': 'minecraft:block/cube', 'textures': {
        'particle': t('block/saw_side'), 'north': t('block/saw_side'), 'south': t('block/saw_side'), 'east': t('block/saw_side'),
        'west': t('block/saw_side'), 'up': t('block/saw_top'), 'down': t('block/mixer_casing_top')}})
    bs('saw', {f'facing={f},powered={p}': {'model': t('block/saw'), 'y': y} for f, y in FACING_Y.items() for p in ('false', 'true')})

    # guillotine platform (the frame and blade are block-entity rendered)
    bm('guillotine', {'textures': {'particle': t('block/guillotine_base'), 'side': t('block/guillotine_base'), 'top': t('block/guillotine_base_top')},
                      'elements': [{'from': [0, 0, 0], 'to': [16, 3, 16], 'faces': {
                          'down': {'texture': '#side', 'cullface': 'down'}, 'up': {'texture': '#top'},
                          'north': {'texture': '#side', 'uv': [0, 13, 16, 16]}, 'south': {'texture': '#side', 'uv': [0, 13, 16, 16]},
                          'west': {'texture': '#side', 'uv': [0, 13, 16, 16]}, 'east': {'texture': '#side', 'uv': [0, 13, 16, 16]}}}]})
    bs('guillotine', {f'facing={f},powered={p}': {'model': t('block/guillotine'), 'y': y} for f, y in FACING_Y.items() for p in ('false', 'true')})

    # host terminal console
    bm('host_terminal', {'textures': {'particle': t('block/host_terminal_side'), 'front': t('block/host_terminal_front'),
                                      'side': t('block/host_terminal_side'), 'top': t('block/host_terminal_top')},
                         'elements': [
                             {'from': [1, 0, 1], 'to': [15, 15, 15], 'faces': {
                                 'north': {'texture': '#front'}, 'south': {'texture': '#side'}, 'west': {'texture': '#side'},
                                 'east': {'texture': '#side'}, 'up': {'texture': '#top'}, 'down': {'texture': '#side', 'cullface': 'down'}}},
                             {'from': [0, 0, 0], 'to': [16, 2, 16], 'faces': {
                                 'north': {'texture': '#side', 'uv': [0, 14, 16, 16]}, 'south': {'texture': '#side', 'uv': [0, 14, 16, 16]},
                                 'west': {'texture': '#side', 'uv': [0, 14, 16, 16]}, 'east': {'texture': '#side', 'uv': [0, 14, 16, 16]},
                                 'up': {'texture': '#top'}, 'down': {'texture': '#side', 'cullface': 'down'}}}]})
    bs('host_terminal', {f'facing={f}': {'model': t('block/host_terminal'), 'y': y} for f, y in FACING_Y.items()})

    # blood splatter: flat decal, random rotation
    variants = {}
    for v in range(4):
        for dried in (False, True):
            name = f'blood_splatter_{v}' + ('_dried' if dried else '')
            bm(name, {'ambientocclusion': False, 'render_type': 'minecraft:cutout',
                      'textures': {'particle': t('block/' + name), 'splat': t('block/' + name)},
                      'elements': [{'from': [0, 0.1, 0], 'to': [16, 0.1, 16],
                                    'faces': {'up': {'texture': '#splat', 'uv': [0, 0, 16, 16]}, 'down': {'texture': '#splat', 'uv': [0, 16, 16, 0]}}}]})
            variants[f'dried={str(dried).lower()},variant={v}'] = [{'model': t('block/' + name), 'y': y} for y in (0, 90, 180, 270)]
    bs('blood_splatter', variants)

    # item models
    for name in ('mixer_core', 'mixer_casing', 'mixer_glass', 'press_head', 'spike_bed', 'host_terminal', 'execution_tiles', 'bloody_tiles', 'rusted_plate'):
        im(name, {'parent': t('block/' + name)})
    for name in ('host_remote', 'contestant_ticket', 'arena_kit', 'mixer_kit', 'press_kit', 'saw', 'guillotine'):
        im(name, {'parent': 'minecraft:item/generated', 'textures': {'layer0': t('item/' + name)}})
    for name in ('host_hand', 'mop'):
        im(name, {'parent': 'minecraft:item/handheld', 'textures': {'layer0': t('item/' + name)}})


def particles():
    for name in ('blood_drop', 'blood_splat', 'blood_mist', 'gore_bit'):
        w(os.path.join(A, 'particles', name + '.json'), {'textures': [t(f'{name}_{i}') for i in range(4)]})


def loot():
    for name in ('mixer_core', 'mixer_casing', 'mixer_glass', 'press_head', 'spike_bed', 'saw', 'guillotine', 'host_terminal',
                 'execution_tiles', 'bloody_tiles', 'rusted_plate'):
        w(os.path.join(D, NS, 'loot_tables', 'blocks', name + '.json'), {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t(name)}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def recipes():
    def shaped(name, pattern, key, result, count=1):
        k = {}
        for ch, v in key.items():
            k[ch] = {'tag': v[1:]} if v.startswith('#') else {'item': v}
        w(os.path.join(D, NS, 'recipes', name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                                                          'key': k, 'result': {'item': result, 'count': count}})

    def shapeless(name, ingredients, result, count=1):
        ing = [{'tag': v[1:]} if v.startswith('#') else {'item': v} for v in ingredients]
        w(os.path.join(D, NS, 'recipes', name + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                          'ingredients': ing, 'result': {'item': result, 'count': count}})

    shaped('host_terminal', ['IGI', 'RCR', 'III'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass', 'R': 'minecraft:redstone', 'C': 'minecraft:gold_ingot'}, t('host_terminal'))
    shaped('host_hand', ['LLL', 'LIL', ' L '], {'L': 'minecraft:leather', 'I': 'minecraft:iron_ingot'}, t('host_hand'))
    shaped('host_remote', [' T ', 'IBI', 'III'], {'T': 'minecraft:redstone_torch', 'I': 'minecraft:iron_ingot', 'B': 'minecraft:stone_button'}, t('host_remote'))
    shapeless('contestant_ticket', ['minecraft:paper', 'minecraft:gold_nugget', 'minecraft:ink_sac'], t('contestant_ticket'), 4)
    shaped('spike_bed', ['D D', 'DID', 'III'], {'D': 'minecraft:pointed_dripstone', 'I': 'minecraft:iron_ingot'}, t('spike_bed'), 4)
    shaped('saw', ['IXI', 'IRI', 'SSS'], {'I': 'minecraft:iron_ingot', 'X': 'minecraft:stonecutter', 'R': 'minecraft:redstone', 'S': 'minecraft:stone'}, t('saw'))
    shaped('guillotine', ['PAP', 'PSP', 'LLL'], {'P': '#minecraft:planks', 'A': 'minecraft:iron_axe', 'S': 'minecraft:string', 'L': '#minecraft:logs'}, t('guillotine'))
    shaped('press_head', ['IPI', 'IRI', 'III'], {'I': 'minecraft:iron_ingot', 'P': 'minecraft:piston', 'R': 'minecraft:redstone_block'}, t('press_head'))
    shaped('mixer_core', ['ISI', 'IRI', 'III'], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:iron_sword', 'R': 'minecraft:redstone_block'}, t('mixer_core'))
    shaped('mixer_casing', ['SIS', 'I I', 'SIS'], {'S': 'minecraft:stone', 'I': 'minecraft:iron_ingot'}, t('mixer_casing'), 8)
    shaped('mixer_glass', ['GIG', 'I I', 'GIG'], {'G': 'minecraft:glass', 'I': 'minecraft:iron_ingot'}, t('mixer_glass'), 4)
    shaped('execution_tiles', ['CC', 'CC'], {'C': 'minecraft:white_concrete'}, t('execution_tiles'), 4)
    shapeless('bloody_tiles', [t('execution_tiles'), 'minecraft:red_dye'], t('bloody_tiles'))
    shaped('rusted_plate', ['IC', 'CI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot'}, t('rusted_plate'), 4)
    shapeless('mixer_kit', [t('mixer_core'), t('mixer_casing'), t('mixer_casing'), t('mixer_glass'), t('mixer_glass'), t('mixer_glass')], t('mixer_kit'))
    shapeless('press_kit', [t('press_head'), t('spike_bed'), t('spike_bed'), t('rusted_plate'), t('rusted_plate')], t('press_kit'))
    shapeless('arena_kit', [t('host_terminal'), t('execution_tiles'), t('execution_tiles'), t('rusted_plate'), t('rusted_plate')], t('arena_kit'))
    shaped('mop', ['  W', ' S ', 'S  '], {'W': 'minecraft:white_wool', 'S': 'minecraft:stick'}, t('mop'))


def tags():
    w(os.path.join(D, 'minecraft', 'tags', 'blocks', 'mineable', 'pickaxe.json'), {'replace': False, 'values': [
        t(n) for n in ('mixer_core', 'mixer_casing', 'mixer_glass', 'press_head', 'spike_bed', 'saw', 'host_terminal',
                       'execution_tiles', 'bloody_tiles', 'rusted_plate')]})
    w(os.path.join(D, 'minecraft', 'tags', 'blocks', 'mineable', 'axe.json'), {'replace': False, 'values': [t('guillotine')]})
    types = [t(n) for n in ('mixer', 'press', 'saw', 'guillotine', 'spikes')]
    w(os.path.join(D, 'minecraft', 'tags', 'damage_type', 'no_knockback.json'), {'replace': False, 'values': types})
    w(os.path.join(D, 'minecraft', 'tags', 'damage_type', 'bypasses_armor.json'), {'replace': False, 'values': types[:4]})


def damage_types():
    for n in ('mixer', 'press', 'saw', 'guillotine', 'spikes'):
        w(os.path.join(D, NS, 'damage_type', n + '.json'), {'message_id': f'{NS}.{n}', 'exhaustion': 0.1, 'scaling': 'never'})


def advancements():
    def adv(name, icon, frame='task', parent='root', hidden=False):
        obj = {'display': {'icon': {'item': icon}, 'title': {'translate': f'advancements.{NS}.{name}.title'},
                           'description': {'translate': f'advancements.{NS}.{name}.description'}, 'frame': frame,
                           'show_toast': True, 'announce_to_chat': False, 'hidden': hidden},
               'criteria': {'done': {'trigger': 'minecraft:impossible'}}}
        if parent:
            obj['parent'] = t(parent)
        else:
            obj['display']['background'] = 'satsujinki:textures/block/bloody_tiles.png'
        w(os.path.join(D, NS, 'advancements', name + '.json'), obj)

    adv('root', t('host_hand'), parent=None)
    adv('first_blood', t('contestant_ticket'))
    adv('mixer', t('mixer_kit'), parent='first_blood')
    adv('press', t('press_kit'), parent='first_blood')
    adv('saw', t('saw'), parent='first_blood')
    adv('guillotine', t('guillotine'), parent='first_blood')
    adv('spikes', t('spike_bed'), parent='first_blood')
    adv('throw', t('host_hand'), parent='first_blood')
    adv('combo5', 'minecraft:redstone', 'goal', 'first_blood')
    adv('combo10', 'minecraft:redstone_block', 'challenge', 'combo5')
    adv('rank_s', 'minecraft:gold_ingot', 'goal', 'first_blood')
    adv('rank_sss', 'minecraft:nether_star', 'challenge', 'rank_s')
    adv('massacre', 'minecraft:wither_skeleton_skull', 'challenge', 'first_blood')
    adv('round', t('host_terminal'), parent='root')
    adv('perfect_game', 'minecraft:totem_of_undying', 'challenge', 'round')


def arena_structure():
    s = Structure(13, 10, 13)
    s.fill(0, 0, 0, 12, 0, 12, 'minecraft:smooth_stone')
    for y in range(1, 10):
        for x in range(13):
            for z in range(13):
                s.set(x, y, z, 'minecraft:air')
    s.save(os.path.join(D, NS, 'structures', 'arena.nbt'))


def main():
    blockstates_and_models()
    particles()
    loot()
    recipes()
    tags()
    damage_types()
    advancements()
    arena_structure()
    print('data done')


if __name__ == '__main__':
    main()
