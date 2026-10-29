#!/usr/bin/env python3
"""Generates the quest-map worldgen datapack JSON (Minecraft 1.20.1 formats):
  data/huntcraft/dimension/{verdant_hills,frostpeak_range,ember_caldera}.json
  data/huntcraft/dimension_type/quest_map*.json
  data/huntcraft/worldgen/{noise,noise_settings,biome,configured_feature,placed_feature}/...
Run from anywhere: python3 tools/quest/gen_worldgen.py   (also validates references + feature order)."""
import json, os, shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'huntcraft')
NS = 'huntcraft'


# ------------------------------------------------------------------ density function helpers (all inline)
def noise(name, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": f"{NS}:{name}", "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def absf(x):
    return {"type": "minecraft:abs", "argument": x}


def square(x):
    return {"type": "minecraft:square", "argument": x}


def ygrad(fy, ty, fv, tv):
    return {"type": "minecraft:y_clamped_gradient", "from_y": fy, "to_y": ty, "from_value": fv, "to_value": tv}


def interp(x):
    return {"type": "minecraft:interpolated", "argument": x}


# ------------------------------------------------------------------ noises (per map, so maps never share layouts)
NOISES = {
    # Verdant Hills
    f"{NS}:vh_hills": {"firstOctave": -8, "amplitudes": [1.0, 0.5, 0.25]},
    f"{NS}:vh_detail": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:vh_cliff": {"firstOctave": -7, "amplitudes": [1.0, 0.25]},
    f"{NS}:vh_strength": {"firstOctave": -8, "amplitudes": [1.0]},
    f"{NS}:vh_humidity": {"firstOctave": -7, "amplitudes": [1.0, 0.5]},
    f"{NS}:vh_lake": {"firstOctave": -7, "amplitudes": [1.0, 0.3]},
    f"{NS}:vh_3d": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:vh_surface": {"firstOctave": -5, "amplitudes": [1.0, 1.0, 0.5]},
    # Frostpeak Range
    f"{NS}:fp_mountain": {"firstOctave": -9, "amplitudes": [1.0, 0.6, 0.3, 0.15]},
    f"{NS}:fp_hills": {"firstOctave": -7, "amplitudes": [1.0, 0.5]},
    f"{NS}:fp_lake": {"firstOctave": -7, "amplitudes": [1.0, 0.3]},
    f"{NS}:fp_ridge": {"firstOctave": -8, "amplitudes": [1.0, 0.4]},
    f"{NS}:fp_detail": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:fp_3d": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:fp_surface": {"firstOctave": -5, "amplitudes": [1.0, 1.0, 0.5]},
    # Ember Caldera
    f"{NS}:ec_hills": {"firstOctave": -8, "amplitudes": [1.0, 0.5, 0.25]},
    f"{NS}:ec_cone": {"firstOctave": -7, "amplitudes": [1.0, 0.3]},
    f"{NS}:ec_basin": {"firstOctave": -7, "amplitudes": [1.0, 0.3]},
    f"{NS}:ec_detail": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:ec_3d": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    f"{NS}:ec_surface": {"firstOctave": -5, "amplitudes": [1.0, 1.0, 0.5]},
}

TOP_Y = 320  # dimension height (min_y 0)


def terrain(B, S, h, d3, d3amp):
    """density = (B - y)/S + h  (+ small 3D roughness) -> surface at y = B + S*h."""
    grad = ygrad(0, TOP_Y, B / S, (B - TOP_Y) / S)
    initial = add(grad, h)
    final = interp(add(initial, mul(d3amp, noise(d3, 1.0, 1.0))))
    return initial, final


# Verdant: rolling hills + plateaus with cliffs that fade into ramps + lakes in hollows
vh_step = mul(0.5, add(clamp(mul(18.0, noise('vh_cliff')), -1.0, 1.0), 1.0))
vh_strength = clamp(add(mul(2.5, noise('vh_strength')), 0.1), 0.0, 1.0)
vh_lake = square(clamp(add(mul(2.5, noise('vh_lake')), -1.0), 0.0, 1.0))
VH_H = add(add(add(mul(0.7, noise('vh_hills')), mul(0.12, noise('vh_detail'))), mul(0.45, mul(vh_step, vh_strength))),
           mul(-0.75, vh_lake))

# Frostpeak: big mountain bodies + sharp ridges in mountainous zones
fp_ridge = square(clamp(add(1.0, mul(-2.0, absf(noise('fp_ridge')))), 0.0, 1.0))
fp_weight = clamp(add(mul(2.0, noise('fp_mountain')), 0.6), 0.0, 1.0)
fp_lake = square(clamp(add(mul(2.5, noise('fp_lake')), -1.1), 0.0, 1.0))
FP_H = add(add(add(mul(0.8, noise('fp_mountain')), mul(0.35, noise('fp_hills'))), mul(0.6, mul(fp_ridge, fp_weight))),
           add(mul(0.1, noise('fp_detail')), mul(-0.6, fp_lake)))

# Ember: basalt plains, lava basins (sea level filled with lava) and volcanic cones
ec_cones = mul(0.9, square(clamp(add(mul(3.0, noise('ec_cone')), -1.2), 0.0, 1.0)))
ec_basin = square(clamp(add(mul(2.5, noise('ec_basin')), -0.7), 0.0, 1.0))
EC_H = add(add(add(mul(0.6, noise('ec_hills')), ec_cones), mul(0.12, noise('ec_detail'))), mul(-1.0, ec_basin))

V = {'meadow': f'{NS}:verdant_meadow', 'forest': f'{NS}:verdant_forest', 'high': f'{NS}:verdant_highlands',
     'taiga': f'{NS}:frostpeak_taiga', 'slopes': f'{NS}:frostpeak_slopes', 'glacier': f'{NS}:frostpeak_glacier',
     'basalt': f'{NS}:ember_basalt_fields', 'lava': f'{NS}:ember_lava_flats', 'ash': f'{NS}:ember_ash_wastes'}


# ------------------------------------------------------------------ surface rules
def block(name, props=None):
    d = {"Name": name}
    if props:
        d["Properties"] = props
    return {"type": "minecraft:block", "result_state": d}


def cond(c, r):
    return {"type": "minecraft:condition", "if_true": c, "then_run": r}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def stone_depth(offset=0, add_surface=False):
    return {"type": "minecraft:stone_depth", "offset": offset, "add_surface_depth": add_surface,
            "secondary_depth_range": 0, "surface_type": "floor"}


def water(offset=-1, mult=0):
    return {"type": "minecraft:water", "offset": offset, "surface_depth_multiplier": mult, "add_stone_depth": False}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": list(ids)}


def nthr(noise_id, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": noise_id, "min_threshold": lo, "max_threshold": hi}


STEEP = {"type": "minecraft:steep"}
BEDROCK = cond({"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
               block("minecraft:bedrock"))


def verdant_surface():
    s = f'{NS}:vh_surface'
    top = seq(
        cond(STEEP, seq(cond(nthr(s, 0.25, 4.0), block("minecraft:andesite")), block("minecraft:stone"))),
        cond(water(-1, 0), seq(
            cond(biome_is(V['high']), seq(
                cond(nthr(s, 0.35, 4.0), block("minecraft:coarse_dirt")),
                cond(nthr(s, -4.0, -0.5), block("minecraft:stone")))),
            cond(biome_is(V['forest']), cond(nthr(s, 0.45, 4.0), block("minecraft:podzol"))),
            block("minecraft:grass_block", {"snowy": "false"}))),
        cond(nthr(s, -0.15, 0.15), block("minecraft:clay")),
        block("minecraft:gravel"))
    under = seq(cond(STEEP, block("minecraft:stone")), cond(water(-6, -1), block("minecraft:dirt")), block("minecraft:gravel"))
    return seq(BEDROCK, cond(stone_depth(0, False), top), cond(stone_depth(0, True), under))


def frost_surface():
    s = f'{NS}:fp_surface'
    top = seq(
        cond(STEEP, seq(cond(biome_is(V['glacier']), block("minecraft:packed_ice")), block("minecraft:stone"))),
        cond(biome_is(V['glacier']), seq(cond(nthr(s, 0.4, 4.0), block("minecraft:packed_ice")), block("minecraft:snow_block"))),
        cond(biome_is(V['slopes']), seq(cond(nthr(s, -4.0, -0.55), block("minecraft:stone")), block("minecraft:snow_block"))),
        cond(water(-1, 0), seq(cond(nthr(s, 0.5, 4.0), block("minecraft:podzol", {"snowy": "false"})),
                               block("minecraft:grass_block", {"snowy": "false"}))),
        block("minecraft:gravel"))
    under = seq(cond(STEEP, block("minecraft:stone")),
                cond(biome_is(V['taiga']), cond(water(-6, -1), block("minecraft:dirt"))),
                block("minecraft:snow_block"))
    return seq(BEDROCK, cond(stone_depth(0, False), top), cond(stone_depth(0, True), under))


def ember_surface():
    s = f'{NS}:ec_surface'
    basalt = block("minecraft:basalt", {"axis": "y"})
    top = seq(
        cond(STEEP, basalt),
        cond(water(-1, 0), seq(
            cond(biome_is(V['lava']), seq(cond(nthr(s, 0.6, 4.0), block("minecraft:magma_block")), block("minecraft:smooth_basalt"))),
            cond(biome_is(V['ash']), seq(cond(nthr(s, -0.25, 0.25), block("minecraft:gravel")),
                                         cond(nthr(s, 0.25, 4.0), block("minecraft:tuff")))),
            cond(nthr(s, 0.3, 4.0), basalt),
            cond(nthr(s, -4.0, -0.45), block("minecraft:smooth_basalt")),
            block("minecraft:blackstone"))),
        block("minecraft:magma_block"))
    return seq(BEDROCK, cond(stone_depth(0, False), top))


# ------------------------------------------------------------------ maps
def router(temperature=0.0, vegetation=0.0, depth=0.0, initial=0.0, final=0.0):
    return {"barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": temperature, "vegetation": vegetation, "continents": 0.0, "erosion": 0.0,
            "depth": depth, "ridges": 0.0, "initial_density_without_jaggedness": initial, "final_density": final,
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0}


def params(temp=0.0, hum=0.0, depth=0.0):
    return {"temperature": temp, "humidity": hum, "continentalness": 0.0, "erosion": 0.0,
            "weirdness": 0.0, "depth": depth, "offset": 0.0}


vh_i, vh_f = terrain(78, 40, VH_H, 'vh_3d', 0.1)
fp_i, fp_f = terrain(104, 70, FP_H, 'fp_3d', 0.08)
ec_i, ec_f = terrain(66, 40, EC_H, 'ec_3d', 0.1)

MAPS = [
    dict(id='verdant_hills', dim_type='quest_map', B=78, S=40, h=VH_H, sea=62,
         default_block={"Name": "minecraft:stone"}, default_fluid={"Name": "minecraft:water", "Properties": {"level": "0"}},
         router=router(temperature=noise('vh_hills'), vegetation=noise('vh_humidity'), initial=vh_i, final=vh_f),
         surface=verdant_surface(),
         biomes=[(V['meadow'], params(temp=[-1.0, 0.2], hum=[-1.0, 0.08])),
                 (V['high'], params(temp=[0.2, 1.0], hum=[-1.0, 0.08])),
                 (V['forest'], params(temp=[-1.0, 1.0], hum=[0.08, 1.0]))],
         colors=[(70, 140, 60), (200, 220, 160)], fluid=(60, 110, 220)),
    dict(id='frostpeak_range', dim_type='quest_map_frost', B=104, S=70, h=FP_H, sea=80,
         default_block={"Name": "minecraft:stone"}, default_fluid={"Name": "minecraft:water", "Properties": {"level": "0"}},
         router=router(depth=ygrad(64, 224, 1.0, -1.0), initial=fp_i, final=fp_f),
         surface=frost_surface(),
         biomes=[(V['taiga'], params(depth=[0.45, 1.0])),
                 (V['slopes'], params(depth=[-0.25, 0.45])),
                 (V['glacier'], params(depth=[-1.0, -0.25]))],
         colors=[(60, 90, 70), (240, 245, 255)], fluid=(150, 190, 230)),
    dict(id='ember_caldera', dim_type='quest_map_ember', B=66, S=40, h=EC_H, sea=56,
         default_block={"Name": "minecraft:blackstone"}, default_fluid={"Name": "minecraft:lava", "Properties": {"level": "0"}},
         router=router(temperature=noise('ec_hills'), vegetation=noise('ec_cone'), initial=ec_i, final=ec_f),
         surface=ember_surface(),
         biomes=[(V['lava'], params(temp=[-1.0, -0.3])),
                 (V['ash'], params(temp=[-0.3, 1.0], hum=[0.2, 1.0])),
                 (V['basalt'], params(temp=[-0.3, 1.0], hum=[-1.0, 0.2]))],
         colors=[(40, 30, 30), (120, 110, 110)], fluid=(240, 110, 20)),
]


def noise_settings(m):
    return {
        "sea_level": m['sea'],
        "disable_mob_generation": True,
        "aquifers_enabled": False,
        "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": m['default_block'],
        "default_fluid": m['default_fluid'],
        "noise": {"min_y": 0, "height": TOP_Y, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": m['router'],
        "spawn_target": [],
        "surface_rule": m['surface'],
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
    'quest_map_frost': dimension_type(6500, False, 0.0),
    'quest_map_ember': dimension_type(6000, True, 0.08),
}

# ------------------------------------------------------------------ features
GATHER_KINDS = ['herb', 'mushroom', 'bug', 'bone', 'mining']
CONFIGURED = {f'{NS}:gather_{k}': {"type": f"{NS}:{k}_point", "config": {}} for k in GATHER_KINDS}


def placed(feature, rarity=None, count=None, heightmap="MOTION_BLOCKING_NO_LEAVES"):
    p = []
    if count is not None:
        p.append({"type": "minecraft:count", "count": count})
    if rarity is not None:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    p += [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": heightmap}, {"type": "minecraft:biome"}]
    return {"feature": feature, "placement": p}


# gathering point densities (rarity = 1 per N chunks)
GATHER_RARITY = {
    'verdant': {'herb': 4, 'mushroom': 3, 'bug': 6, 'bone': 9, 'mining': 5},
    'frostpeak': {'herb': 5, 'mushroom': 5, 'bug': 9, 'bone': 7, 'mining': 4},
    'ember': {'herb': 6, 'bug': 9, 'bone': 6, 'mining': 3},
}
PLACED = {}
for mp, kinds in GATHER_RARITY.items():
    for k, r in kinds.items():
        PLACED[f'{NS}:{mp}_{k}_points'] = placed(f'{NS}:gather_{k}', rarity=r)
PLACED[f'{NS}:verdant_boulders'] = placed("minecraft:forest_rock", rarity=7, heightmap="MOTION_BLOCKING")
PLACED[f'{NS}:frostpeak_ice_spikes'] = placed("minecraft:ice_spike", rarity=9, heightmap="MOTION_BLOCKING")
PLACED[f'{NS}:ember_basalt_columns'] = placed("minecraft:small_basalt_columns", rarity=2, heightmap="WORLD_SURFACE_WG")
PLACED[f'{NS}:ember_basalt_pillars'] = placed("minecraft:large_basalt_columns", rarity=5, heightmap="WORLD_SURFACE_WG")

# vanilla ids used (must exist in 1.20.1)
VANILLA_PLACED = {"minecraft:trees_birch_and_oak", "minecraft:trees_plains", "minecraft:flower_plains", "minecraft:forest_flowers",
                  "minecraft:flower_default", "minecraft:patch_grass_plain", "minecraft:patch_grass_forest",
                  "minecraft:patch_tall_grass_2", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal",
                  "minecraft:trees_taiga", "minecraft:trees_grove", "minecraft:brown_mushroom_taiga", "minecraft:freeze_top_layer"}
VANILLA_CONFIGURED = {"minecraft:forest_rock", "minecraft:ice_spike", "minecraft:small_basalt_columns", "minecraft:large_basalt_columns"}

# global (step, feature) order per dimension; each biome uses a subsequence -> no feature order cycles
ORDER = {
    'verdant_hills': [
        (2, f'{NS}:verdant_boulders'),
        (9, 'minecraft:trees_birch_and_oak'), (9, 'minecraft:trees_plains'),
        (9, 'minecraft:flower_plains'), (9, 'minecraft:forest_flowers'), (9, 'minecraft:flower_default'),
        (9, 'minecraft:patch_grass_plain'), (9, 'minecraft:patch_grass_forest'), (9, 'minecraft:patch_tall_grass_2'),
        (9, 'minecraft:brown_mushroom_normal'), (9, 'minecraft:red_mushroom_normal'),
        (9, f'{NS}:verdant_herb_points'), (9, f'{NS}:verdant_mushroom_points'), (9, f'{NS}:verdant_bug_points'),
        (9, f'{NS}:verdant_bone_points'), (9, f'{NS}:verdant_mining_points')],
    'frostpeak_range': [
        (4, f'{NS}:frostpeak_ice_spikes'),
        (9, 'minecraft:trees_taiga'), (9, 'minecraft:trees_grove'), (9, 'minecraft:brown_mushroom_taiga'),
        (9, f'{NS}:frostpeak_herb_points'), (9, f'{NS}:frostpeak_mushroom_points'), (9, f'{NS}:frostpeak_bug_points'),
        (9, f'{NS}:frostpeak_bone_points'), (9, f'{NS}:frostpeak_mining_points'),
        (10, 'minecraft:freeze_top_layer')],
    'ember_caldera': [
        (7, f'{NS}:ember_basalt_columns'), (7, f'{NS}:ember_basalt_pillars'),
        (9, f'{NS}:ember_herb_points'), (9, f'{NS}:ember_bug_points'), (9, f'{NS}:ember_bone_points'),
        (9, f'{NS}:ember_mining_points')],
}

BIOME_FEATURES = {
    V['meadow']: ['minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:flower_default', 'minecraft:patch_grass_plain',
                  'minecraft:patch_tall_grass_2', f'{NS}:verdant_herb_points', f'{NS}:verdant_bug_points',
                  f'{NS}:verdant_bone_points', f'{NS}:verdant_mining_points'],
    V['forest']: ['minecraft:trees_birch_and_oak', 'minecraft:forest_flowers', 'minecraft:flower_default',
                  'minecraft:patch_grass_forest', 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal',
                  f'{NS}:verdant_herb_points', f'{NS}:verdant_mushroom_points', f'{NS}:verdant_bug_points', f'{NS}:verdant_bone_points'],
    V['high']: [f'{NS}:verdant_boulders', 'minecraft:trees_plains', 'minecraft:flower_default', 'minecraft:patch_grass_plain',
                f'{NS}:verdant_herb_points', f'{NS}:verdant_bone_points', f'{NS}:verdant_mining_points'],
    V['taiga']: ['minecraft:trees_taiga', 'minecraft:brown_mushroom_taiga', f'{NS}:frostpeak_herb_points',
                 f'{NS}:frostpeak_mushroom_points', f'{NS}:frostpeak_bug_points', f'{NS}:frostpeak_bone_points',
                 'minecraft:freeze_top_layer'],
    V['slopes']: ['minecraft:trees_grove', f'{NS}:frostpeak_herb_points', f'{NS}:frostpeak_bone_points',
                  f'{NS}:frostpeak_mining_points', 'minecraft:freeze_top_layer'],
    V['glacier']: [f'{NS}:frostpeak_ice_spikes', f'{NS}:frostpeak_bone_points', f'{NS}:frostpeak_mining_points',
                   'minecraft:freeze_top_layer'],
    V['basalt']: [f'{NS}:ember_basalt_columns', f'{NS}:ember_herb_points', f'{NS}:ember_bug_points',
                  f'{NS}:ember_bone_points', f'{NS}:ember_mining_points'],
    V['lava']: [f'{NS}:ember_basalt_columns', f'{NS}:ember_bone_points', f'{NS}:ember_mining_points'],
    V['ash']: [f'{NS}:ember_basalt_columns', f'{NS}:ember_basalt_pillars', f'{NS}:ember_herb_points',
               f'{NS}:ember_bone_points', f'{NS}:ember_mining_points'],
}
BIOME_DIM = {V['meadow']: 'verdant_hills', V['forest']: 'verdant_hills', V['high']: 'verdant_hills',
             V['taiga']: 'frostpeak_range', V['slopes']: 'frostpeak_range', V['glacier']: 'frostpeak_range',
             V['basalt']: 'ember_caldera', V['lava']: 'ember_caldera', V['ash']: 'ember_caldera'}

MOOD = {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
SPAWN_CATS = ["monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc"]


def rgb(h):
    return int(h, 16)


BIOME_LOOK = {
    V['meadow']: dict(t=0.8, d=0.5, p=True, sky='78A7FF', fog='C0D8FF', water='3F76E4', wfog='050533', grass='79C05A', foliage='59AE30'),
    V['forest']: dict(t=0.7, d=0.8, p=True, sky='79A6FF', fog='C0D8FF', water='3F76E4', wfog='050533', grass='5FA842', foliage='4A9A2C'),
    V['high']: dict(t=0.6, d=0.4, p=True, sky='7BA4FF', fog='C8DCFF', water='3F76E4', wfog='050533', grass='8DB360', foliage='71A74D'),
    V['taiga']: dict(t=-0.5, d=0.6, p=True, sky='8FB4E8', fog='DCE8F5', water='3D57D6', wfog='050533', grass='80B497', foliage='60A17B',
                     particle=("minecraft:snowflake", 0.012)),
    V['slopes']: dict(t=-0.7, d=0.6, p=True, sky='94B8EC', fog='E2ECF8', water='3D57D6', wfog='050533', grass='80B497', foliage='60A17B',
                      particle=("minecraft:snowflake", 0.016)),
    V['glacier']: dict(t=-0.9, d=0.7, p=True, sky='A0C0F0', fog='EAF2FC', water='3938C9', wfog='050533', grass='80B497', foliage='60A17B',
                       particle=("minecraft:snowflake", 0.022)),
    V['basalt']: dict(t=2.0, d=0.0, p=False, sky='9C3A1E', fog='6E2614', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                      particle=("minecraft:ash", 0.02), ambient="minecraft:ambient.basalt_deltas.loop",
                      additions="minecraft:ambient.basalt_deltas.additions"),
    V['lava']: dict(t=2.0, d=0.0, p=False, sky='B0401E', fog='7A2A10', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                    particle=("minecraft:ash", 0.03), ambient="minecraft:ambient.basalt_deltas.loop",
                    additions="minecraft:ambient.basalt_deltas.additions"),
    V['ash']: dict(t=2.0, d=0.0, p=False, sky='7E3020', fog='4A2018', water='3F76E4', wfog='050533', grass='7A6A40', foliage='5E4A2A',
                   particle=("minecraft:white_ash", 0.06), ambient="minecraft:ambient.basalt_deltas.loop",
                   additions="minecraft:ambient.basalt_deltas.additions"),
}


def biome_json(bid):
    L = BIOME_LOOK[bid]
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
    order = ORDER[BIOME_DIM[bid]]
    step_of = {f: s for s, f in order}
    for f in BIOME_FEATURES[bid]:
        steps[step_of[f]].append(f)
    return {"has_precipitation": L['p'], "temperature": L['t'], "downfall": L['d'], "effects": effects,
            "spawners": {c: [] for c in SPAWN_CATS}, "spawn_costs": {}, "carvers": {}, "features": steps}


# ------------------------------------------------------------------ output + validation
def write(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def walk(o, fn):
    fn(o)
    if isinstance(o, dict):
        for v in o.values():
            walk(v, fn)
    elif isinstance(o, list):
        for v in o:
            walk(v, fn)


def validate():
    # feature order: biome lists must be subsequences of the dimension order
    for bid, feats in BIOME_FEATURES.items():
        order = [f for _, f in ORDER[BIOME_DIM[bid]]]
        idx = [order.index(f) for f in feats]
        assert idx == sorted(idx), f"feature order violated in {bid}: {feats}"
        assert len(set(feats)) == len(feats), bid
    for dim, order in ORDER.items():
        fs = [f for _, f in order]
        assert len(set(fs)) == len(fs), dim
        for f in fs:
            assert f in PLACED or f in VANILLA_PLACED, f"unknown placed feature {f}"
    for k, p in PLACED.items():
        assert p['feature'] in CONFIGURED or p['feature'] in VANILLA_CONFIGURED, p['feature']
    # noise references
    def chk(o):
        if isinstance(o, dict) and o.get('type') == 'minecraft:noise':
            assert o['noise'] in NOISES, o['noise']
        if isinstance(o, dict) and o.get('type') == 'minecraft:noise_threshold':
            assert o['noise'] in NOISES, o['noise']
        if isinstance(o, dict) and o.get('type') == 'minecraft:biome' and 'biome_is' in o:
            for b in o['biome_is']:
                assert b in BIOME_LOOK, b
    for m in MAPS:
        walk(m['router'], chk)
        walk(m['surface'], chk)
        for b, _ in m['biomes']:
            assert b in BIOME_LOOK and BIOME_DIM[b] == m['id'], b
        assert m['dim_type'] in DIM_TYPES
    # every placed feature in PLACED is used somewhere
    used = {f for o in ORDER.values() for _, f in o}
    for k in PLACED:
        assert k in used, f"unused placed feature {k}"


def main():
    validate()
    wg = os.path.join(DATA, 'worldgen')
    for sub in ('noise', 'noise_settings', 'biome', 'configured_feature', 'placed_feature'):
        shutil.rmtree(os.path.join(wg, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA, 'dimension'), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA, 'dimension_type'), ignore_errors=True)
    for k, v in NOISES.items():
        write(f"worldgen/noise/{k.split(':')[1]}.json", v)
    for k, v in CONFIGURED.items():
        write(f"worldgen/configured_feature/{k.split(':')[1]}.json", v)
    for k, v in PLACED.items():
        write(f"worldgen/placed_feature/{k.split(':')[1]}.json", v)
    for bid in BIOME_LOOK:
        write(f"worldgen/biome/{bid.split(':')[1]}.json", biome_json(bid))
    for name, v in DIM_TYPES.items():
        write(f"dimension_type/{name}.json", v)
    for m in MAPS:
        write(f"worldgen/noise_settings/{m['id']}.json", noise_settings(m))
        write(f"dimension/{m['id']}.json", {
            "type": f"{NS}:{m['dim_type']}",
            "generator": {
                "type": "minecraft:noise",
                "settings": f"{NS}:{m['id']}",
                "biome_source": {"type": "minecraft:multi_noise",
                                 "biomes": [{"biome": b, "parameters": p} for b, p in m['biomes']]},
            },
        })
    print("worldgen written + validated")


if __name__ == '__main__':
    main()
