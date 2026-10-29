#!/usr/bin/env python3
"""Generates the quest-map datapack JSON (Minecraft 1.20.1 formats):
  data/huntcraft/dimension/{verdant_hills,frostpeak_range,ember_caldera}.json   (generator huntcraft:quest_map)
  data/huntcraft/dimension_type/quest_map*.json
  data/huntcraft/worldgen/biome/*.json
The terrain itself is designed by design_maps.py (data/huntcraft/quest_maps/*.nbt); biome order here must match
the biome list of each map (checked against the exported NBT). Run: python3 tools/quest/gen_worldgen.py"""
import gzip
import json
import os
import shutil
import struct

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'huntcraft')
NS = 'huntcraft'
TOP_Y = 320

# map id -> (dimension type, biome list in data order)
MAPS = {
    'verdant_hills': ('quest_map', ['verdant_meadow', 'verdant_forest', 'verdant_highlands', 'verdant_riverside', 'verdant_clearing', 'verdant_cave']),
    'frostpeak_range': ('quest_map_frost', ['frostpeak_taiga', 'frostpeak_snowfield', 'frostpeak_glacier', 'frostpeak_summit',
                                            'frostpeak_hot_spring', 'frostpeak_ice_cave']),
    'ember_caldera': ('quest_map_ember', ['ember_outer_slopes', 'ember_ash_wastes', 'ember_basalt_deltas', 'ember_lava_lake',
                                          'ember_fumaroles', 'ember_lava_tube']),
}


def dimension_type(fixed_time, ultrawarm, ambient):
    return {
        "ultrawarm": ultrawarm, "natural": True, "coordinate_scale": 1.0,
        "has_skylight": True, "has_ceiling": False, "ambient_light": ambient,
        "fixed_time": fixed_time,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": TOP_Y, "min_y": 0, "height": TOP_Y,
        "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld",
    }


DIM_TYPES = {
    'quest_map': dimension_type(6000, False, 0.0),
    'quest_map_frost': dimension_type(5000, False, 0.0),
    'quest_map_ember': dimension_type(6000, True, 0.1),
}

# vegetation: vanilla placed features, all in step 9 (vegetal decoration) except freeze_top_layer (step 10).
# Per map the features keep one global order (no feature order cycles).
ORDER = {
    'verdant_hills': ['minecraft:trees_birch_and_oak', 'minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:forest_flowers',
                      'minecraft:flower_default', 'minecraft:patch_grass_plain', 'minecraft:patch_grass_forest', 'minecraft:patch_tall_grass_2',
                      'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_sugar_cane'],
    'frostpeak_range': ['minecraft:trees_taiga', 'minecraft:trees_grove', 'minecraft:patch_large_fern', 'minecraft:patch_grass_taiga',
                        'minecraft:brown_mushroom_taiga'],
    'ember_caldera': ['minecraft:patch_dead_bush'],
}
TOP_LAYER = {'minecraft:freeze_top_layer'}

BIOMES = {
    # ---------------------------------------------------------------- Verdant Hills
    'verdant_meadow': dict(t=0.8, d=0.5, p=True, sky='78A7FF', fog='C0D8FF', water='3F76E4', wfog='050533', grass='79C05A', foliage='59AE30',
                           f=['minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:flower_default', 'minecraft:patch_grass_plain',
                              'minecraft:patch_tall_grass_2']),
    'verdant_forest': dict(t=0.7, d=0.8, p=True, sky='79A6FF', fog='C0D8FF', water='3F76E4', wfog='050533', grass='5FA842', foliage='4A9A2C',
                           f=['minecraft:trees_birch_and_oak', 'minecraft:forest_flowers', 'minecraft:flower_default', 'minecraft:patch_grass_forest',
                              'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal']),
    'verdant_highlands': dict(t=0.6, d=0.4, p=True, sky='7BA4FF', fog='C8DCFF', water='3F76E4', wfog='050533', grass='8DB360', foliage='71A74D',
                              f=['minecraft:flower_default', 'minecraft:patch_grass_plain']),
    'verdant_riverside': dict(t=0.8, d=0.9, p=True, sky='78A7FF', fog='C0D8FF', water='3F9BE4', wfog='052A40', grass='6CC04E', foliage='52A82E',
                              f=['minecraft:flower_default', 'minecraft:patch_grass_plain', 'minecraft:patch_tall_grass_2', 'minecraft:patch_sugar_cane']),
    'verdant_clearing': dict(t=0.8, d=0.5, p=True, sky='78A7FF', fog='C0D8FF', water='3F76E4', wfog='050533', grass='7CC45C', foliage='59AE30',
                             f=['minecraft:flower_plains', 'minecraft:flower_default', 'minecraft:patch_grass_plain']),
    'verdant_cave': dict(t=0.7, d=0.8, p=False, sky='78A7FF', fog='8090A0', water='3F76E4', wfog='050533', grass='5FA842', foliage='4A9A2C', f=[],
                         particle=('minecraft:spore_blossom_air', 0.004)),
    # ---------------------------------------------------------------- Frostpeak Range
    'frostpeak_taiga': dict(t=-0.5, d=0.6, p=True, sky='8FB4E8', fog='DCE8F5', water='3D57D6', wfog='050533', grass='80B497', foliage='60A17B',
                            f=['minecraft:trees_taiga', 'minecraft:patch_large_fern', 'minecraft:patch_grass_taiga', 'minecraft:brown_mushroom_taiga',
                               'minecraft:freeze_top_layer'], particle=('minecraft:snowflake', 0.01)),
    'frostpeak_snowfield': dict(t=-0.7, d=0.6, p=True, sky='94B8EC', fog='E2ECF8', water='3D57D6', wfog='050533', grass='80B497', foliage='60A17B',
                                f=['minecraft:trees_grove', 'minecraft:freeze_top_layer'], particle=('minecraft:snowflake', 0.015)),
    'frostpeak_glacier': dict(t=-0.9, d=0.7, p=True, sky='A0C0F0', fog='EAF2FC', water='3938C9', wfog='050533', grass='80B497', foliage='60A17B',
                              f=['minecraft:freeze_top_layer'], particle=('minecraft:snowflake', 0.02)),
    'frostpeak_summit': dict(t=-1.0, d=0.8, p=True, sky='B0C8F0', fog='F0F5FF', water='3938C9', wfog='050533', grass='80B497', foliage='60A17B',
                             f=['minecraft:freeze_top_layer'], particle=('minecraft:snowflake', 0.04)),
    'frostpeak_hot_spring': dict(t=0.5, d=0.9, p=True, sky='9FB8E0', fog='E8EEF4', water='4CB8C8', wfog='0A3038', grass='7FB070', foliage='5E9A55',
                                 f=['minecraft:patch_large_fern', 'minecraft:patch_grass_taiga', 'minecraft:brown_mushroom_taiga'],
                                 particle=('minecraft:white_ash', 0.03)),
    'frostpeak_ice_cave': dict(t=-0.8, d=0.5, p=False, sky='A0C0F0', fog='9AB4D0', water='3938C9', wfog='050533', grass='80B497', foliage='60A17B', f=[]),
    # ---------------------------------------------------------------- Ember Caldera
    'ember_outer_slopes': dict(t=1.6, d=0.0, p=False, sky='C0603A', fog='8A4A30', water='3F76E4', wfog='050533', grass='9A8A50', foliage='7A6A3A',
                               f=['minecraft:patch_dead_bush'], particle=('minecraft:ash', 0.008)),
    'ember_ash_wastes': dict(t=2.0, d=0.0, p=False, sky='8A4A3A', fog='5A3A32', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                             f=[], particle=('minecraft:white_ash', 0.06), ambient='minecraft:ambient.basalt_deltas.loop',
                             additions='minecraft:ambient.basalt_deltas.additions'),
    'ember_basalt_deltas': dict(t=2.0, d=0.0, p=False, sky='9C3A1E', fog='6E2614', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                                f=[], particle=('minecraft:ash', 0.03), ambient='minecraft:ambient.basalt_deltas.loop',
                                additions='minecraft:ambient.basalt_deltas.additions'),
    'ember_lava_lake': dict(t=2.0, d=0.0, p=False, sky='B0401E', fog='7A2A10', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                            f=[], particle=('minecraft:ash', 0.03), ambient='minecraft:ambient.basalt_deltas.loop',
                            additions='minecraft:ambient.basalt_deltas.additions'),
    'ember_fumaroles': dict(t=2.0, d=0.0, p=False, sky='A8502A', fog='7A4020', water='3F76E4', wfog='050533', grass='8A7A40', foliage='6E5A2A',
                            f=[], particle=('minecraft:white_ash', 0.04), ambient='minecraft:ambient.basalt_deltas.loop',
                            additions='minecraft:ambient.basalt_deltas.additions'),
    'ember_lava_tube': dict(t=2.0, d=0.0, p=False, sky='9C3A1E', fog='3A1408', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                            f=[], particle=('minecraft:ash', 0.02), ambient='minecraft:ambient.basalt_deltas.loop'),
}
MOOD = {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
SPAWN_CATS = ["monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc"]


def rgb(h):
    return int(h, 16)


def biome_json(bid, dim):
    L = BIOMES[bid]
    effects = {"sky_color": rgb(L['sky']), "fog_color": rgb(L['fog']), "water_color": rgb(L['water']),
               "water_fog_color": rgb(L['wfog']), "grass_color": rgb(L['grass']), "foliage_color": rgb(L['foliage'])}
    if 'particle' in L:
        effects["particle"] = {"options": {"type": L['particle'][0]}, "probability": L['particle'][1]}
    if 'ambient' in L:
        effects["ambient_sound"] = L['ambient']
    if 'additions' in L:
        effects["additions_sound"] = {"sound": L['additions'], "tick_chance": 0.0111}
    effects["mood_sound"] = MOOD
    steps = [[] for _ in range(11)]
    order = ORDER[dim]
    for f in L['f']:
        if f in TOP_LAYER:
            steps[10].append(f)
        else:
            steps[9].append(f)
    idx = [order.index(f) for f in steps[9]]
    assert idx == sorted(idx), f"feature order violated in {bid}"
    return {"has_precipitation": L['p'], "temperature": L['t'], "downfall": L['d'], "effects": effects,
            "spawners": {c: [] for c in SPAWN_CATS}, "spawn_costs": {}, "carvers": {}, "features": steps}


def write(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def nbt_biomes(path):
    """Reads the 'biomes' string list of an exported quest map (minimal NBT reader)."""
    raw = gzip.decompress(open(path, 'rb').read())
    pos = [3]

    def rd(fmt):
        v = struct.unpack_from(fmt, raw, pos[0])
        pos[0] += struct.calcsize(fmt)
        return v[0]

    def string():
        n = rd('>H')
        s = raw[pos[0]:pos[0] + n].decode('utf-8')
        pos[0] += n
        return s

    def payload(t):
        if t == 1: return rd('>b')
        if t == 2: return rd('>h')
        if t == 3: return rd('>i')
        if t == 4: return rd('>q')
        if t == 5: return rd('>f')
        if t == 6: return rd('>d')
        if t == 7:
            n = rd('>i'); pos[0] += n; return None
        if t == 8: return string()
        if t == 9:
            et = rd('>b'); n = rd('>i'); return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                et = rd('>b')
                if et == 0: return out
                k = string(); out[k] = payload(et)
        if t == 11:
            n = rd('>i'); pos[0] += 4 * n; return None
        if t == 12:
            n = rd('>i'); pos[0] += 8 * n; return None
        raise ValueError(t)
    root = payload(10)
    return root.get('biomes', [])


def main():
    wg = os.path.join(DATA, 'worldgen')
    for sub in ('noise', 'noise_settings', 'biome', 'configured_feature', 'placed_feature'):
        shutil.rmtree(os.path.join(wg, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA, 'dimension'), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA, 'dimension_type'), ignore_errors=True)
    for name, v in DIM_TYPES.items():
        write(f"dimension_type/{name}.json", v)
    for mid, (dtype, biomes) in MAPS.items():
        nbt = os.path.join(DATA, 'quest_maps', mid + '.nbt')
        if os.path.exists(nbt):
            got = nbt_biomes(nbt)
            want = [f'{NS}:{b}' for b in biomes]
            assert got == want, f"{mid}: biome order mismatch\n data {got}\n json {want}"
        for b in biomes:
            write(f"worldgen/biome/{b}.json", biome_json(b, mid))
        write(f"dimension/{mid}.json", {
            "type": f"{NS}:{dtype}",
            "generator": {
                "type": f"{NS}:quest_map",
                "map": mid,
                "biome_source": {"type": f"{NS}:quest_map", "map": mid, "biomes": [f'{NS}:{b}' for b in biomes]},
            },
        })
    print("quest-map worldgen json written (biome order verified against map data)")


if __name__ == '__main__':
    main()
