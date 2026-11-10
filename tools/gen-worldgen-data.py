#!/usr/bin/env python3
"""Generates the アストリア dimension data pack JSON (dimension, dimension type, biomes, configured and placed features).

Run from anywhere:  python3 tools/gen-worldgen-data.py
Output: src/main/resources/data/eternalrealm/{dimension,dimension_type,worldgen/...}

Why a generator: every biome must list its features in ONE global order per generation step (Minecraft refuses a
biome source whose biomes order the same features differently: "Feature order cycle found"). Each biome below only
names the set of features it wants; the order always comes from ORDER. WorldgenDataTest re-checks the written files.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "eternalrealm")
NS = "eternalrealm"


def er(name):
    return NS + ":" + name


def mc(name):
    return "minecraft:" + name


# ----------------------------------------------------------------------------------------------- configured features
# name -> (feature type, variant or None)
CONFIGURED = {
    "area_surface": ("area_surface", None),
    "dark_tower": ("dark_tower", None),
    "magma_vent": ("magma_vent", None),
    "void_crack": ("void_crack", None),
    "farm_plot": ("farm_plot", None),
    "mushroom_ring": ("mushroom_ring", None),
    "fallen_log": ("fallen_pillar", "log"),
}
for v in ["oasis", "lava", "frozen"]:
    CONFIGURED["pool_" + v] = ("pool", v)
for v in ["stone", "sandstone", "terracotta", "ice", "basalt", "obsidian", "calcite", "dark"]:
    CONFIGURED["rock_spire_" + v] = ("rock_spire", v)
for v in ["mossy", "sandstone", "snowy", "basalt", "calcite", "deepslate"]:
    CONFIGURED["boulder_" + v] = ("boulder", v)
for v in ["amethyst", "ice", "magma", "quartz", "void"]:
    CONFIGURED["crystal_" + v] = ("crystal_cluster", v)
for v in ["stone", "sandstone", "quartz", "blackstone"]:
    CONFIGURED["fallen_pillar_" + v] = ("fallen_pillar", v)
for v in ["mossy", "sandstone", "frost", "basalt", "quartz", "abyss"]:
    CONFIGURED["ruin_" + v] = ("ruin", v)
for v in ["giant", "palm", "white", "cherry", "charred", "ashen", "bleached"]:
    CONFIGURED["tree_" + v] = ("astoria_tree", v)
for v in ["meadow", "cherry", "crystal"]:
    CONFIGURED["floating_island_" + v] = ("floating_island", v)

# --------------------------------------------------------------------------------------------------- placed features
# name -> (configured feature, frequency) where frequency is None (once per chunk at the chunk corner, no filters),
# ("rarity", n) = on average once every n chunks, or ("count", n) = n times per chunk.
PLACED = {
    "area_surface": ("area_surface", None),
    "dark_tower": ("dark_tower", None),
    "pool_oasis": ("pool_oasis", ("rarity", 5)),
    "pool_oasis_rare": ("pool_oasis", ("rarity", 40)),
    "pool_lava": ("pool_lava", ("rarity", 4)),
    "pool_lava_rare": ("pool_lava", ("rarity", 16)),
    "pool_frozen": ("pool_frozen", ("rarity", 6)),
    "pool_frozen_rare": ("pool_frozen", ("rarity", 18)),
    "void_crack": ("void_crack", ("rarity", 3)),
    "void_crack_rare": ("void_crack", ("rarity", 12)),
    "rock_spire_stone_rare": ("rock_spire_stone", ("rarity", 24)),
    "rock_spire_sandstone": ("rock_spire_sandstone", ("rarity", 5)),
    "rock_spire_terracotta": ("rock_spire_terracotta", ("rarity", 2)),
    "rock_spire_ice": ("rock_spire_ice", ("rarity", 3)),
    "rock_spire_ice_rare": ("rock_spire_ice", ("rarity", 12)),
    "rock_spire_basalt": ("rock_spire_basalt", ("count", 1)),
    "rock_spire_basalt_rare": ("rock_spire_basalt", ("rarity", 5)),
    "rock_spire_obsidian": ("rock_spire_obsidian", ("rarity", 3)),
    "rock_spire_calcite": ("rock_spire_calcite", ("rarity", 4)),
    "rock_spire_calcite_rare": ("rock_spire_calcite", ("rarity", 10)),
    "rock_spire_dark": ("rock_spire_dark", ("rarity", 3)),
    "rock_spire_dark_rare": ("rock_spire_dark", ("rarity", 8)),
    "boulder_mossy": ("boulder_mossy", ("rarity", 6)),
    "boulder_mossy_forest": ("boulder_mossy", ("rarity", 2)),
    "boulder_sandstone": ("boulder_sandstone", ("rarity", 4)),
    "boulder_snowy": ("boulder_snowy", ("rarity", 4)),
    "boulder_basalt": ("boulder_basalt", ("rarity", 2)),
    "boulder_calcite": ("boulder_calcite", ("rarity", 5)),
    "boulder_deepslate": ("boulder_deepslate", ("rarity", 2)),
    "crystal_amethyst": ("crystal_amethyst", ("rarity", 6)),
    "crystal_amethyst_rare": ("crystal_amethyst", ("rarity", 20)),
    "crystal_ice": ("crystal_ice", ("rarity", 3)),
    "crystal_ice_rare": ("crystal_ice", ("rarity", 14)),
    "crystal_magma": ("crystal_magma", ("rarity", 3)),
    "crystal_magma_rare": ("crystal_magma", ("rarity", 10)),
    "crystal_quartz": ("crystal_quartz", ("rarity", 4)),
    "crystal_quartz_rare": ("crystal_quartz", ("rarity", 12)),
    "crystal_void": ("crystal_void", ("rarity", 3)),
    "crystal_void_rare": ("crystal_void", ("rarity", 9)),
    "magma_vent": ("magma_vent", ("rarity", 3)),
    "magma_vent_rare": ("magma_vent", ("rarity", 9)),
    "ruin_mossy": ("ruin_mossy", ("rarity", 26)),
    "ruin_mossy_rare": ("ruin_mossy", ("rarity", 60)),
    "ruin_sandstone": ("ruin_sandstone", ("rarity", 22)),
    "ruin_sandstone_rare": ("ruin_sandstone", ("rarity", 50)),
    "ruin_frost": ("ruin_frost", ("rarity", 28)),
    "ruin_frost_rare": ("ruin_frost", ("rarity", 60)),
    "ruin_basalt": ("ruin_basalt", ("rarity", 26)),
    "ruin_basalt_rare": ("ruin_basalt", ("rarity", 55)),
    "ruin_quartz": ("ruin_quartz", ("rarity", 20)),
    "ruin_quartz_rare": ("ruin_quartz", ("rarity", 45)),
    "ruin_abyss": ("ruin_abyss", ("rarity", 20)),
    "ruin_abyss_rare": ("ruin_abyss", ("rarity", 45)),
    "fallen_pillar_stone": ("fallen_pillar_stone", ("rarity", 28)),
    "fallen_pillar_sandstone": ("fallen_pillar_sandstone", ("rarity", 18)),
    "fallen_pillar_quartz": ("fallen_pillar_quartz", ("rarity", 16)),
    "fallen_pillar_blackstone": ("fallen_pillar_blackstone", ("rarity", 18)),
    "farm_plot": ("farm_plot", ("rarity", 3)),
    "floating_island_meadow": ("floating_island_meadow", ("rarity", 4)),
    "floating_island_cherry": ("floating_island_cherry", ("rarity", 4)),
    "floating_island_crystal": ("floating_island_crystal", ("rarity", 4)),
    "tree_giant": ("tree_giant", ("rarity", 2)),
    "tree_giant_rare": ("tree_giant", ("rarity", 9)),
    "tree_palm": ("tree_palm", ("count", 2)),
    "tree_palm_rare": ("tree_palm", ("rarity", 30)),
    "tree_white": ("tree_white", ("count", 2)),
    "tree_white_rare": ("tree_white", ("rarity", 3)),
    "tree_charred": ("tree_charred", ("count", 1)),
    "tree_charred_rare": ("tree_charred", ("rarity", 4)),
    "tree_ashen": ("tree_ashen", ("count", 3)),
    "tree_ashen_rare": ("tree_ashen", ("rarity", 3)),
    "tree_bleached": ("tree_bleached", ("rarity", 9)),
    "mushroom_ring": ("mushroom_ring", ("rarity", 4)),
    "fallen_log": ("fallen_log", ("rarity", 3)),
}

ORES = [mc(n) for n in [
    "ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower",
    "ore_andesite_upper", "ore_andesite_lower", "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper",
    "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower",
    "ore_diamond", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper",
    "underwater_magma", "disk_sand", "disk_clay", "disk_gravel"]]

# One global order per generation step (GenerationStep.Decoration order). Every biome uses a subsequence.
ORDER = [
    [er("area_surface")],
    [mc("lake_lava_underground"), mc("lake_lava_surface")]
    + [er(n) for n in ["pool_oasis", "pool_oasis_rare", "pool_lava", "pool_lava_rare", "pool_frozen", "pool_frozen_rare"]],
    [mc("amethyst_geode")]
    + [er(n) for n in [
        "void_crack", "void_crack_rare",
        "rock_spire_stone_rare", "rock_spire_sandstone", "rock_spire_terracotta", "rock_spire_ice",
        "rock_spire_ice_rare", "rock_spire_basalt", "rock_spire_basalt_rare", "rock_spire_obsidian",
        "rock_spire_calcite", "rock_spire_calcite_rare", "rock_spire_dark", "rock_spire_dark_rare",
        "boulder_mossy", "boulder_mossy_forest", "boulder_sandstone", "boulder_snowy", "boulder_basalt",
        "boulder_calcite", "boulder_deepslate",
        "crystal_amethyst", "crystal_amethyst_rare", "crystal_ice", "crystal_ice_rare", "crystal_magma",
        "crystal_magma_rare", "crystal_quartz", "crystal_quartz_rare", "crystal_void", "crystal_void_rare",
        "magma_vent", "magma_vent_rare"]],
    [],
    [mc("desert_well"), mc("ice_spike"), mc("ice_patch")]
    + [er(n) for n in [
        "dark_tower",
        "ruin_mossy", "ruin_mossy_rare", "ruin_sandstone", "ruin_sandstone_rare", "ruin_frost", "ruin_frost_rare",
        "ruin_basalt", "ruin_basalt_rare", "ruin_quartz", "ruin_quartz_rare", "ruin_abyss", "ruin_abyss_rare",
        "fallen_pillar_stone", "fallen_pillar_sandstone", "fallen_pillar_quartz", "fallen_pillar_blackstone",
        "farm_plot", "floating_island_meadow", "floating_island_cherry", "floating_island_crystal"]],
    [],
    ORES,
    [],
    [mc("spring_water"), mc("spring_lava")],
    [mc("glow_lichen"), er("tree_giant"), er("tree_giant_rare"), mc("dark_forest_vegetation"),
     mc("trees_birch_and_oak"), mc("birch_tall"), mc("trees_plains"), mc("trees_meadow"), mc("trees_taiga"),
     mc("trees_snowy"), mc("trees_cherry"), mc("trees_windswept_hills"), mc("trees_savanna"),
     er("tree_white"), er("tree_white_rare"), er("tree_palm"), er("tree_palm_rare"), er("tree_charred"),
     er("tree_charred_rare"), er("tree_ashen"), er("tree_ashen_rare"), er("tree_bleached"),
     mc("mushroom_island_vegetation"), er("mushroom_ring"), er("fallen_log"),
     mc("flower_plains"), mc("flower_meadow"), mc("flower_flower_forest"), mc("forest_flowers"),
     mc("flower_default"), mc("flower_warm"), mc("flower_cherry"), mc("patch_sunflower"),
     mc("patch_tall_grass"), mc("patch_tall_grass_2"), mc("patch_large_fern"), mc("patch_grass_plain"),
     mc("patch_grass_forest"), mc("patch_grass_taiga_2"), mc("patch_grass_badlands"), mc("patch_grass_savanna"),
     mc("patch_berry_common"), mc("patch_berry_rare"), mc("patch_dead_bush"), mc("patch_dead_bush_2"),
     mc("patch_dead_bush_badlands"), mc("patch_cactus_desert"), mc("patch_cactus_decorated"),
     mc("brown_mushroom_normal"), mc("red_mushroom_normal"), mc("brown_mushroom_old_growth"),
     mc("red_mushroom_old_growth"), mc("brown_mushroom_taiga"), mc("red_mushroom_taiga"),
     mc("patch_sugar_cane"), mc("patch_sugar_cane_desert"), mc("patch_sugar_cane_badlands"),
     mc("patch_pumpkin"), mc("patch_melon_sparse"), mc("patch_waterlily"), mc("seagrass_normal"),
     mc("seagrass_river"), mc("seagrass_deep"), mc("kelp_cold")],
    [mc("freeze_top_layer")],
]

COMMON = {er("area_surface"), mc("lake_lava_underground"), mc("amethyst_geode"), mc("spring_water"),
          mc("spring_lava"), mc("glow_lichen"), mc("freeze_top_layer")} | set(ORES)


def feats(*names):
    return {n if ":" in n else er(n) for n in names}


# ------------------------------------------------------------------------------------------------------------ biomes
def spawn(entity, weight, lo, hi):
    return {"type": mc(entity), "weight": weight, "minCount": lo, "maxCount": hi}


PASTORAL = [spawn("sheep", 12, 4, 4), spawn("pig", 10, 4, 4), spawn("chicken", 10, 4, 4), spawn("cow", 8, 4, 4)]
CAVE_MOOD = {"sound": mc("ambient.cave"), "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}


def nether_ambience(kind):
    return {
        "ambient_sound": mc("ambient." + kind + ".loop"),
        "mood_sound": {"sound": mc("ambient." + kind + ".mood"), "tick_delay": 6000, "block_search_extent": 8,
                       "offset": 2.0},
        "additions_sound": {"sound": mc("ambient." + kind + ".additions"), "tick_chance": 0.0111},
    }


def music(sound):
    return {"sound": mc(sound), "min_delay": 12000, "max_delay": 24000, "replace_current_music": False}


def biome(area, temperature, downfall, precipitation, sky, fog, water, water_fog, grass, foliage, features,
          creatures=(), water_creatures=True, particle=None, ambience=None, music_sound=None, extra_spawns=None):
    effects = {"sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog,
               "grass_color": grass, "foliage_color": foliage, "mood_sound": CAVE_MOOD}
    if particle:
        effects["particle"] = {"options": {"type": mc(particle[0])}, "probability": particle[1]}
    if ambience:
        effects.update(ambience)
    if music_sound:
        effects["music"] = music(music_sound)
    spawners = {"monster": [], "creature": list(creatures), "ambient": [spawn("bat", 10, 8, 8)], "axolotls": [],
                "underground_water_creature": [spawn("glow_squid", 10, 4, 6)],
                "water_creature": [spawn("squid", 2, 1, 4)] if water_creatures else [],
                "water_ambient": [spawn("salmon", 5, 1, 5)] if water_creatures else [], "misc": []}
    if extra_spawns:
        for category, entries in extra_spawns.items():
            spawners[category] = spawners[category] + entries
    return {"area": area, "has_precipitation": precipitation, "temperature": temperature, "downfall": downfall,
            "effects": effects, "spawners": spawners, "features": features | COMMON}


BIOMES = {
    # ---- area 1 アルセリア王国: bright plains, farms and meadows
    "royal_capital": biome(1, 0.8, 0.4, True, 0x78A7FF, 0xC0D8FF, 0x3F76E4, 0x050533, 0x86C95A, 0x6AB04A,
                           feats(mc("patch_grass_plain"), mc("flower_plains"), mc("flower_meadow")),
                           creatures=[spawn("sheep", 6, 2, 3), spawn("chicken", 6, 2, 4)],
                           music_sound="music.overworld.meadow"),
    "arcelia_plains": biome(1, 0.8, 0.4, True, 0x78A7FF, 0xC0D8FF, 0x3F76E4, 0x050533, 0x7DC45A, 0x62AC3E,
                            feats("rock_spire_stone_rare", "boulder_mossy", "ruin_mossy_rare", "fallen_pillar_stone",
                                  mc("trees_plains"), mc("patch_tall_grass_2"), mc("flower_plains"),
                                  mc("patch_grass_plain"), mc("brown_mushroom_normal"), mc("red_mushroom_normal"),
                                  mc("patch_sugar_cane"), mc("patch_pumpkin"), mc("seagrass_normal")),
                            creatures=PASTORAL + [spawn("horse", 5, 2, 6), spawn("donkey", 1, 1, 3)],
                            music_sound="music.overworld.meadow"),
    "arcelia_flower_meadow": biome(1, 0.7, 0.8, True, 0x7AA9FF, 0xC8DCFF, 0x0E4ECF, 0x050533, 0x83BB6D, 0x5FA83F,
                                   feats("boulder_mossy", "ruin_mossy_rare", mc("trees_meadow"), mc("flower_meadow"),
                                         mc("flower_flower_forest"), mc("patch_sunflower"), mc("patch_tall_grass"),
                                         mc("patch_grass_plain"), mc("seagrass_normal")),
                                   creatures=[spawn("sheep", 10, 2, 4), spawn("rabbit", 4, 2, 6),
                                              spawn("donkey", 1, 1, 2), spawn("cow", 6, 2, 4)],
                                   music_sound="music.overworld.meadow"),
    "arcelia_farmlands": biome(8, 0.8, 0.4, True, 0x78A7FF, 0xC8D8F0, 0x3F76E4, 0x050533, 0x9DC45A, 0x7BA840,
                               feats("boulder_mossy", "farm_plot", mc("trees_plains"), mc("flower_plains"),
                                     mc("patch_grass_plain"), mc("patch_pumpkin"), mc("patch_melon_sparse"),
                                     mc("patch_sugar_cane"), mc("seagrass_normal")),
                               creatures=[spawn("sheep", 12, 4, 4), spawn("pig", 12, 4, 4),
                                          spawn("chicken", 12, 4, 4), spawn("cow", 10, 4, 4)],
                               music_sound="music.overworld.meadow"),
    # ---- area 2 エルダーフォレスト: deep ancient forest
    "elder_forest": biome(2, 0.7, 0.8, True, 0x79A6FF, 0xB5D3C0, 0x3D8E8C, 0x062A2A, 0x5A9A3C, 0x4A8A2C,
                          feats("boulder_mossy_forest", "rock_spire_stone_rare", "ruin_mossy", "fallen_pillar_stone",
                                "tree_giant_rare", mc("trees_birch_and_oak"), "fallen_log", mc("forest_flowers"),
                                mc("flower_default"), mc("patch_grass_forest"), mc("patch_large_fern"),
                                mc("brown_mushroom_normal"), mc("red_mushroom_normal"), mc("patch_berry_common"),
                                mc("patch_sugar_cane"), mc("seagrass_normal")),
                          creatures=PASTORAL + [spawn("wolf", 5, 4, 4), spawn("rabbit", 4, 2, 3)],
                          music_sound="music.overworld.old_growth_taiga"),
    "elder_old_growth": biome(2, 0.7, 0.8, True, 0x6E9BE8, 0x9DBFAA, 0x2F7A6A, 0x062A2A, 0x467F32, 0x3B7029,
                              feats("boulder_mossy_forest", "ruin_mossy", "fallen_pillar_stone", "tree_giant",
                                    mc("dark_forest_vegetation"), "fallen_log", mc("flower_default"),
                                    mc("patch_grass_forest"), mc("patch_large_fern"),
                                    mc("brown_mushroom_old_growth"), mc("red_mushroom_old_growth")),
                              creatures=[spawn("wolf", 8, 4, 4), spawn("rabbit", 4, 2, 3), spawn("fox", 8, 2, 4),
                                         spawn("pig", 6, 2, 4)],
                              particle=("spore_blossom_air", 0.004),
                              music_sound="music.overworld.old_growth_taiga"),
    "elder_mushroom_glade": biome(2, 0.9, 1.0, True, 0x77ADFF, 0xB8C7D8, 0x5A6FB0, 0x0A1A3A, 0x55C93F, 0x2BBB0F,
                                  feats("boulder_mossy_forest", "crystal_amethyst_rare", "ruin_mossy_rare",
                                        mc("mushroom_island_vegetation"), "mushroom_ring", "fallen_log",
                                        mc("brown_mushroom_normal"), mc("red_mushroom_normal"),
                                        mc("patch_grass_forest")),
                                  creatures=[spawn("mooshroom", 8, 4, 8)],
                                  particle=("spore_blossom_air", 0.012),
                                  music_sound="music.overworld.lush_caves"),
    # ---- area 3 サンドリア砂漠: dunes, mesas, oases
    "sandria_dunes": biome(10, 2.0, 0.0, False, 0x6EB1FF, 0xE8D9B0, 0x32A598, 0x041F33, 0xBFB755, 0xAEA42A,
                           feats("pool_oasis_rare", "rock_spire_sandstone", "boulder_sandstone", mc("desert_well"),
                                 "ruin_sandstone", "fallen_pillar_sandstone", "tree_palm_rare", "tree_bleached",
                                 mc("patch_dead_bush_2"), mc("patch_cactus_desert"), mc("patch_sugar_cane_desert")),
                           creatures=[spawn("rabbit", 4, 2, 3), spawn("camel", 1, 1, 1)], water_creatures=False),
    "sandria_mesa": biome(5, 2.0, 0.0, False, 0x6EB1FF, 0xE9B98A, 0x4E7F81, 0x041F33, 0x90814D, 0x9E814D,
                          feats("rock_spire_terracotta", "boulder_sandstone", "ruin_sandstone_rare",
                                "fallen_pillar_sandstone", "tree_bleached", mc("patch_dead_bush_badlands"),
                                mc("patch_cactus_decorated"), mc("patch_grass_badlands"),
                                mc("patch_sugar_cane_badlands")),
                          creatures=[spawn("rabbit", 3, 2, 3)], water_creatures=False),
    "sandria_oasis": biome(0, 1.6, 0.5, False, 0x74B6FF, 0xD8E8C0, 0x2BD4C6, 0x0C5A5A, 0x6EC14B, 0x4FB23A,
                           feats("pool_oasis", "boulder_sandstone", "ruin_sandstone_rare", "tree_palm",
                                 mc("flower_warm"), mc("patch_tall_grass"), mc("patch_sugar_cane"),
                                 mc("patch_waterlily"), mc("seagrass_normal")),
                           creatures=[spawn("camel", 2, 1, 1), spawn("rabbit", 4, 2, 3), spawn("chicken", 4, 2, 3)]),
    # ---- area 4 ノルディア雪原: snowfields, ice spikes, frozen lakes, crystals
    "nordia_snowfield": biome(6, -0.5, 0.5, True, 0x7FA1FF, 0xD6E4F2, 0x3D57D6, 0x050533, 0x80B497, 0x60A17B,
                              feats("pool_frozen_rare", "rock_spire_ice_rare", "boulder_snowy", "crystal_ice_rare",
                                    mc("ice_patch"), "ruin_frost", mc("trees_snowy"), mc("patch_berry_rare")),
                              creatures=[spawn("rabbit", 10, 2, 3), spawn("fox", 8, 2, 4), spawn("wolf", 4, 2, 4)],
                              music_sound="music.overworld.snowy_slopes"),
    "nordia_taiga": biome(6, -0.5, 0.4, True, 0x839EFF, 0xC8D8E8, 0x3D57D6, 0x050533, 0x80B497, 0x60A17B,
                          feats("boulder_snowy", "ruin_frost_rare", mc("trees_taiga"), mc("patch_large_fern"),
                                mc("patch_grass_taiga_2"), mc("patch_berry_common"), mc("brown_mushroom_taiga"),
                                mc("red_mushroom_taiga")),
                          creatures=[spawn("wolf", 8, 4, 4), spawn("rabbit", 4, 2, 3), spawn("fox", 8, 2, 4),
                                     spawn("sheep", 8, 2, 4)],
                          music_sound="music.overworld.grove"),
    "nordia_ice_spikes": biome(6, -0.7, 0.5, True, 0x8DB8FF, 0xE6F2FF, 0x3D57D6, 0x050533, 0x80B497, 0x60A17B,
                               feats("rock_spire_ice", mc("ice_spike"), mc("ice_patch")),
                               creatures=[spawn("rabbit", 10, 2, 3)], particle=("white_ash", 0.01),
                               music_sound="music.overworld.frozen_peaks"),
    "nordia_crystal_hollow": biome(6, -0.5, 0.5, True, 0x8EA9FF, 0xCFE0F5, 0x4F6BE0, 0x050533, 0x7AB3A0, 0x5E9E8A,
                                   feats("pool_frozen", "crystal_ice", "crystal_amethyst", "boulder_snowy",
                                         "ruin_frost_rare", mc("trees_snowy")),
                                   creatures=[spawn("rabbit", 6, 2, 3), spawn("goat", 5, 1, 3)],
                                   music_sound="music.overworld.jagged_peaks"),
    # ---- area 5 ドラゴニア火山: basalt, blackstone, ash, lava
    "dragonia_ashlands": biome(0, 2.0, 0.0, False, 0x9C6A5A, 0x6E4A3E, 0x7A5A3A, 0x2A1A10, 0x8A7A4A, 0x7A6A3A,
                               feats("pool_lava_rare", "rock_spire_basalt_rare", "boulder_basalt", "magma_vent_rare",
                                     "ruin_basalt", "fallen_pillar_blackstone", "tree_charred",
                                     mc("patch_dead_bush")),
                               water_creatures=False, particle=("ash", 0.05),
                               ambience=nether_ambience("basalt_deltas"),
                               music_sound="music.nether.basalt_deltas"),
    "dragonia_basalt_fields": biome(3, 2.0, 0.0, False, 0x8A5A50, 0x5A3A34, 0x7A5A3A, 0x2A1A10, 0x7E6E44, 0x6E5E34,
                                    feats("pool_lava_rare", "rock_spire_basalt", "boulder_basalt",
                                          "crystal_magma_rare", "magma_vent", "ruin_basalt_rare",
                                          "tree_charred_rare"),
                                    water_creatures=False, particle=("white_ash", 0.08),
                                    ambience=nether_ambience("basalt_deltas"),
                                    music_sound="music.nether.basalt_deltas"),
    "dragonia_caldera": biome(0, 2.0, 0.0, False, 0xB04A30, 0x8A3A20, 0x7A5A3A, 0x2A1A10, 0x7E6E44, 0x6E5E34,
                              feats(mc("lake_lava_surface"), "pool_lava", "rock_spire_obsidian", "crystal_magma",
                                    "magma_vent_rare", "fallen_pillar_blackstone"),
                              water_creatures=False, particle=("ash", 0.08),
                              ambience=nether_ambience("crimson_forest"),
                              music_sound="music.nether.nether_wastes"),
    # ---- area 6 セレスティア天空大陸: luminous meadows, cherry and white trees, floating islands
    "celestia_meadow": biome(4, 0.7, 0.8, True, 0x8ED8FF, 0xEAF8FF, 0x5DD9F5, 0x1A6A8A, 0xA8E6A0, 0xC8F0D8,
                             feats("rock_spire_calcite_rare", "boulder_calcite", "crystal_quartz_rare", "ruin_quartz",
                                   "fallen_pillar_quartz", "floating_island_meadow", "tree_white_rare",
                                   mc("trees_meadow"), mc("flower_meadow"), mc("patch_grass_plain"),
                                   mc("patch_tall_grass"), mc("seagrass_normal")),
                             creatures=[spawn("sheep", 12, 2, 4), spawn("rabbit", 6, 2, 3), spawn("horse", 3, 2, 4)],
                             particle=("white_ash", 0.02), music_sound="music.overworld.meadow"),
    "celestia_cherry_grove": biome(4, 0.6, 0.8, True, 0x9ED4FF, 0xF8E8F8, 0x5DB7EF, 0x5DB7EF, 0xB6DB61, 0xB6DB61,
                                   feats("boulder_calcite", "ruin_quartz_rare", "floating_island_cherry",
                                         mc("trees_cherry"), mc("flower_cherry"), mc("patch_grass_plain"),
                                         mc("patch_tall_grass")),
                                   creatures=[spawn("pig", 1, 1, 2), spawn("rabbit", 2, 2, 6), spawn("sheep", 2, 2, 4)],
                                   particle=("cherry_leaves", 0.006), music_sound="music.overworld.cherry_grove"),
    "celestia_white_woods": biome(6, 0.6, 0.8, True, 0xA0DCFF, 0xF0F8FF, 0x7AE0F0, 0x1A6A8A, 0xC8EBD0, 0xE6F5F0,
                                  feats("rock_spire_calcite", "crystal_quartz", "fallen_pillar_quartz",
                                        "floating_island_crystal", "tree_white", mc("birch_tall"),
                                        mc("flower_meadow"), mc("patch_grass_plain")),
                                  creatures=[spawn("rabbit", 6, 2, 3), spawn("sheep", 6, 2, 4)],
                                  particle=("white_ash", 0.025), music_sound="music.overworld.meadow"),
    # ---- area 7 終焉の塔: dark wasteland
    "abyss_wastes": biome(7, 0.5, 0.0, False, 0x2E2438, 0x1C1622, 0x2A1B3D, 0x0A0612, 0x4A4A3A, 0x3E3A30,
                          feats("void_crack_rare", "rock_spire_dark", "boulder_deepslate", "crystal_void_rare",
                                "dark_tower", "ruin_abyss", "fallen_pillar_blackstone", "tree_ashen_rare",
                                mc("patch_dead_bush")),
                          water_creatures=False, particle=("ash", 0.03),
                          ambience=nether_ambience("soul_sand_valley"), music_sound="music.nether.soul_sand_valley"),
    "abyss_dead_forest": biome(7, 0.5, 0.0, False, 0x30283A, 0x221A26, 0x2A1B3D, 0x0A0612, 0x4A4A3A, 0x3E3A30,
                               feats("rock_spire_dark_rare", "boulder_deepslate", "dark_tower", "ruin_abyss_rare",
                                     "tree_ashen", mc("patch_dead_bush")),
                               water_creatures=False, particle=("ash", 0.04),
                               ambience=nether_ambience("soul_sand_valley"),
                               music_sound="music.nether.soul_sand_valley"),
    "abyss_void_rift": biome(7, 0.5, 0.0, False, 0x26183A, 0x1A1028, 0x2A1B3D, 0x0A0612, 0x4A4A3A, 0x3E3A30,
                             feats("void_crack", "crystal_void", "rock_spire_dark_rare", "dark_tower"),
                             water_creatures=False, particle=("reverse_portal", 0.01),
                             ambience=nether_ambience("soul_sand_valley"), music_sound="music.end"),
    "abyss_spire": biome(7, 0.5, 0.0, False, 0x1E1428, 0x140C1C, 0x2A1B3D, 0x0A0612, 0x4A4A3A, 0x3E3A30,
                         feats("void_crack_rare", "rock_spire_dark", "crystal_void", "dark_tower", "ruin_abyss_rare",
                               "tree_ashen_rare"),
                         water_creatures=False, particle=("ash", 0.06),
                         ambience=nether_ambience("soul_sand_valley"), music_sound="music.end"),
    # ---- the field map's new fields and sub-biomes
    # 3 鉄鉱の鉱山: stony hills with wind-bent trees, and the bare quarry
    "iron_hills": biome(3, 0.6, 0.4, True, 0x7FA6E8, 0xC4CCD8, 0x3F76E4, 0x050533, 0x8DA65A, 0x6E8F45,
                        feats("rock_spire_stone_rare", "boulder_mossy", "ruin_mossy_rare", "fallen_pillar_stone",
                              mc("trees_windswept_hills"), mc("flower_default"), mc("patch_grass_plain"),
                              mc("patch_berry_rare")),
                        creatures=[spawn("goat", 6, 1, 3), spawn("sheep", 6, 2, 4), spawn("rabbit", 3, 2, 3)],
                        music_sound="music.overworld.stony_peaks"),
    "iron_quarry": biome(3, 0.6, 0.3, True, 0x86A8E0, 0xC9CED6, 0x3F76E4, 0x050533, 0x92A05E, 0x7A8A4A,
                         feats("rock_spire_stone_rare", "crystal_amethyst_rare", mc("patch_grass_plain")),
                         creatures=[spawn("goat", 2, 1, 2)], music_sound="music.overworld.stony_peaks"),
    # 5 古代遺跡: dry grassy hills full of fallen columns
    "ruin_hills": biome(5, 1.2, 0.2, True, 0x76A8F2, 0xD4D8C6, 0x3F8FC4, 0x050533, 0xA9B060, 0x9AA34A,
                        feats("ruin_mossy", "ruin_quartz_rare", "fallen_pillar_stone", "fallen_pillar_quartz",
                              "boulder_mossy", "rock_spire_stone_rare", mc("trees_savanna"),
                              mc("patch_grass_savanna"), mc("patch_dead_bush")),
                        creatures=[spawn("horse", 4, 2, 4), spawn("rabbit", 4, 2, 3), spawn("sheep", 4, 2, 4)],
                        music_sound="music.overworld.desert"),
    # 6 天翔の峰: flowery alpine meadows below the snow, with floating islands
    "alpine_meadow": biome(6, 0.4, 0.6, True, 0x86B6FF, 0xD8E6F6, 0x3F76E4, 0x050533, 0x88BB66, 0x63A048,
                           feats("boulder_mossy", "rock_spire_stone_rare", "floating_island_meadow",
                                 mc("trees_meadow"), mc("flower_meadow"), mc("patch_grass_plain"),
                                 mc("patch_tall_grass")),
                           creatures=[spawn("goat", 5, 1, 3), spawn("sheep", 8, 2, 4), spawn("rabbit", 4, 2, 3)],
                           music_sound="music.overworld.meadow"),
    # 8 豊穣の農場: golden wheat fields
    "golden_fields": biome(8, 0.9, 0.4, True, 0x78A7FF, 0xD0DCF0, 0x3F76E4, 0x050533, 0xB7C45A, 0x93AE45,
                           feats("farm_plot", mc("trees_plains"), mc("patch_sunflower"), mc("flower_plains"),
                                 mc("patch_grass_plain"), mc("patch_pumpkin")),
                           creatures=PASTORAL, music_sound="music.overworld.meadow"),
    # 9 湖畔のほとり: birch shores, reeds and lilies, and the lake itself
    "lakeside": biome(9, 0.7, 0.8, True, 0x7AA9FF, 0xC8DCF0, 0x3F8FE4, 0x050533, 0x7FBF62, 0x5FA83F,
                      feats("boulder_mossy", "fallen_log", mc("trees_birch_and_oak"), mc("flower_flower_forest"),
                            mc("patch_sugar_cane"), mc("patch_waterlily"), mc("patch_grass_plain"),
                            mc("seagrass_normal")),
                      creatures=[spawn("sheep", 8, 2, 4), spawn("cow", 6, 2, 4), spawn("rabbit", 4, 2, 3)],
                      music_sound="music.overworld.meadow"),
    "astoria_lake": biome(9, 0.7, 0.8, True, 0x7AA9FF, 0xC8DCF0, 0x3F8FE4, 0x050533, 0x7FBF62, 0x5FA83F,
                          feats(mc("seagrass_river"), mc("patch_waterlily")),
                          music_sound="music.overworld.meadow"),
    # 10 風の港: grassy coast and the open sea
    "harbour_coast": biome(10, 0.8, 0.5, True, 0x78A7FF, 0xC8DCF0, 0x3F76E4, 0x050533, 0x8DBF6A, 0x6CA84A,
                           feats("boulder_mossy", "rock_spire_stone_rare", mc("trees_plains"), mc("flower_plains"),
                                 mc("patch_grass_plain"), mc("patch_tall_grass")),
                           creatures=[spawn("sheep", 6, 2, 4), spawn("chicken", 8, 2, 4)],
                           music_sound="music.overworld.meadow"),
    "astoria_sea": biome(10, 0.6, 0.5, True, 0x78A7FF, 0xC0D8FF, 0x2F6FC9, 0x062A4A, 0x8DBF6A, 0x6CA84A,
                         feats(mc("seagrass_deep"), mc("kelp_cold")),
                         extra_spawns={"water_creature": [spawn("dolphin", 1, 1, 2)],
                                       "water_ambient": [spawn("cod", 10, 3, 6)]}),
}

CARVERS = {"air": [mc("cave"), mc("cave_extra_underground"), mc("canyon")]}


def biome_json(spec):
    steps = []
    for step in ORDER:
        steps.append([f for f in step if f in spec["features"]])
    known = {f for step in ORDER for f in step}
    unknown = spec["features"] - known
    if unknown:
        raise SystemExit("features missing from ORDER: %s" % sorted(unknown))
    return {
        "has_precipitation": spec["has_precipitation"],
        "temperature": spec["temperature"],
        "downfall": spec["downfall"],
        "effects": spec["effects"],
        "spawners": spec["spawners"],
        "spawn_costs": {},
        "carvers": CARVERS,
        "features": steps,
    }


def placed_json(name):
    configured, frequency = PLACED[name]
    if frequency is None:
        placement = []
    else:
        kind, n = frequency
        first = {"type": mc("rarity_filter"), "chance": n} if kind == "rarity" else {"type": mc("count"), "count": n}
        placement = [first, {"type": mc("in_square")},
                     {"type": mc("heightmap"), "heightmap": "WORLD_SURFACE_WG"}, {"type": mc("biome")}]
    return {"feature": er(configured), "placement": placement}


def configured_json(name):
    feature_type, variant = CONFIGURED[name]
    return {"type": er(feature_type), "config": {} if variant is None else {"variant": variant}}


DIMENSION_TYPE = {
    "ambient_light": 0.0, "bed_works": True, "coordinate_scale": 1.0, "effects": mc("overworld"),
    "has_ceiling": False, "has_raids": False, "has_skylight": True, "height": 384,
    "infiniburn": "#" + mc("infiniburn_overworld"), "logical_height": 384, "min_y": -64,
    "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "natural": True, "piglin_safe": False,
    "respawn_anchor_works": False, "ultrawarm": False,
}

DIMENSION = {
    "type": er("astoria"),
    "generator": {"type": er("fields"), "biome_source": {"type": er("astoria")}},
}


def write(relative, data):
    path = os.path.join(ROOT, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, indent=2, ensure_ascii=False)
        out.write("\n")


def clean(directory):
    path = os.path.join(ROOT, directory)
    if os.path.isdir(path):
        for name in os.listdir(path):
            if name.endswith(".json"):
                os.remove(os.path.join(path, name))


def main():
    used = set()
    for spec in BIOMES.values():
        used |= {f for f in spec["features"] if f.startswith(NS + ":")}
    for name in PLACED:
        if PLACED[name][0] not in CONFIGURED:
            raise SystemExit("placed feature %s uses unknown configured feature" % name)
    unused = {er(n) for n in PLACED} - used
    if unused:
        raise SystemExit("placed features not used by any biome: %s" % sorted(unused))
    for directory in ["worldgen/biome", "worldgen/configured_feature", "worldgen/placed_feature"]:
        clean(directory)
    write("dimension_type/astoria.json", DIMENSION_TYPE)
    write("dimension/astoria.json", DIMENSION)
    for name in sorted(CONFIGURED):
        write("worldgen/configured_feature/%s.json" % name, configured_json(name))
    for name in sorted(PLACED):
        write("worldgen/placed_feature/%s.json" % name, placed_json(name))
    for name, spec in sorted(BIOMES.items()):
        write("worldgen/biome/%s.json" % name, biome_json(spec))
    print("wrote %d biomes, %d configured and %d placed features" % (len(BIOMES), len(CONFIGURED), len(PLACED)))


if __name__ == "__main__":
    main()
