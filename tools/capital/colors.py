"""Approximate block colours and render properties for the preview renderer.

``material(state)`` -> dict(top=(r,g,b), side=(r,g,b), kind, alpha, box, emit)
kind: "air" (skipped), "solid" (full cube), "glass" (transparent tint), "box" (partial box), "water".
box: (x0, y0, z0, x1, y1, z1) in [0,1] for partial blocks.
"""
from __future__ import annotations

from functools import lru_cache

from . import blocks as B

DYE_RGB = {
    "white": (234, 236, 237), "orange": (241, 118, 20), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
    "yellow": (248, 198, 40), "lime": (112, 185, 26), "pink": (237, 141, 172), "gray": (62, 68, 72),
    "light_gray": (142, 142, 135), "cyan": (21, 137, 145), "purple": (122, 42, 172), "blue": (53, 57, 157),
    "brown": (114, 72, 41), "green": (84, 109, 27), "red": (161, 39, 35), "black": (21, 21, 26),
}
TERRACOTTA = {
    "white": (210, 178, 161), "orange": (162, 84, 38), "magenta": (150, 88, 109), "light_blue": (113, 109, 138),
    "yellow": (186, 133, 36), "lime": (104, 118, 53), "pink": (162, 78, 79), "gray": (58, 42, 36),
    "light_gray": (135, 107, 98), "cyan": (87, 91, 91), "purple": (118, 70, 86), "blue": (74, 60, 91),
    "brown": (77, 51, 36), "green": (76, 83, 42), "red": (143, 61, 47), "black": (37, 23, 16),
}
BASE = {
    "stone": (125, 125, 125), "cobblestone": (122, 122, 122), "mossy_cobblestone": (106, 118, 92),
    "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (113, 121, 104), "cracked_stone_bricks": (118, 117, 118),
    "chiseled_stone_bricks": (119, 118, 119), "smooth_stone": (160, 160, 160), "andesite": (136, 136, 137),
    "polished_andesite": (132, 135, 134), "diorite": (189, 188, 189), "polished_diorite": (193, 193, 195),
    "granite": (149, 103, 85), "polished_granite": (154, 106, 89), "calcite": (223, 224, 220), "tuff": (108, 109, 102),
    "deepslate": (80, 80, 82), "cobbled_deepslate": (77, 77, 80), "polished_deepslate": (72, 72, 73),
    "deepslate_bricks": (70, 70, 71), "deepslate_tiles": (54, 54, 55), "chiseled_deepslate": (54, 54, 55),
    "cracked_deepslate_tiles": (52, 52, 53), "blackstone": (42, 36, 41), "polished_blackstone": (53, 48, 56),
    "polished_blackstone_bricks": (48, 42, 49), "gilded_blackstone": (70, 50, 40),
    "chiseled_polished_blackstone": (53, 48, 56), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59),
    "rooted_dirt": (144, 103, 76), "dirt_path": (148, 122, 65), "grass_block": (98, 160, 62),
    "podzol": (92, 63, 24), "gravel": (131, 127, 126), "sand": (219, 207, 163), "red_sand": (190, 102, 33),
    "clay": (160, 166, 179), "mud": (60, 57, 61), "packed_mud": (142, 106, 79), "mud_bricks": (137, 103, 79),
    "moss_block": (89, 109, 45), "moss_carpet": (89, 109, 45), "snow_block": (249, 254, 254), "snow": (249, 254, 254),
    "ice": (145, 183, 253), "packed_ice": (141, 180, 250), "blue_ice": (116, 167, 253),
    "quartz_block": (236, 230, 223), "smooth_quartz": (235, 229, 222), "quartz_bricks": (234, 229, 221),
    "chiseled_quartz_block": (231, 226, 218), "quartz_pillar": (235, 230, 224), "sandstone": (216, 203, 155),
    "smooth_sandstone": (223, 214, 170), "cut_sandstone": (217, 206, 159), "chiseled_sandstone": (216, 203, 155),
    "red_sandstone": (186, 99, 29), "bricks": (150, 97, 83), "end_stone_bricks": (218, 224, 162),
    "prismarine": (99, 156, 151), "prismarine_bricks": (99, 171, 158), "dark_prismarine": (51, 91, 75),
    "nether_bricks": (44, 21, 26), "purpur_block": (169, 125, 169), "smooth_basalt": (72, 72, 78),
    "basalt": (80, 81, 86), "polished_basalt": (99, 98, 100), "obsidian": (15, 11, 25), "crying_obsidian": (32, 10, 60),
    "gold_block": (246, 208, 61), "iron_block": (220, 220, 220), "lapis_block": (31, 67, 140),
    "diamond_block": (98, 237, 228), "emerald_block": (42, 203, 87), "copper_block": (192, 107, 79),
    "waxed_copper_block": (192, 107, 79), "waxed_cut_copper": (191, 106, 80),
    "waxed_exposed_cut_copper": (154, 121, 101), "waxed_weathered_cut_copper": (109, 145, 107),
    "waxed_oxidized_cut_copper": (79, 153, 126), "amethyst_block": (133, 97, 191), "terracotta": (152, 94, 68),
    "oak_planks": (162, 130, 78), "spruce_planks": (114, 84, 48), "birch_planks": (192, 175, 121),
    "jungle_planks": (160, 115, 80), "acacia_planks": (168, 90, 50), "dark_oak_planks": (66, 43, 20),
    "mangrove_planks": (117, 54, 48), "cherry_planks": (226, 178, 172), "bamboo_planks": (193, 173, 80),
    "bamboo_mosaic": (190, 170, 78), "oak_log": (109, 85, 50), "spruce_log": (58, 37, 16), "birch_log": (216, 215, 210),
    "jungle_log": (85, 67, 25), "acacia_log": (103, 96, 86), "dark_oak_log": (60, 46, 26),
    "mangrove_log": (84, 66, 41), "cherry_log": (54, 33, 44), "stripped_oak_log": (177, 144, 86),
    "stripped_spruce_log": (115, 89, 52), "stripped_birch_log": (196, 176, 118), "stripped_dark_oak_log": (96, 76, 49),
    "stripped_jungle_log": (171, 132, 84), "stripped_acacia_log": (174, 92, 59), "stripped_cherry_log": (215, 145, 148),
    "stripped_mangrove_log": (119, 54, 47),
    "oak_leaves": (66, 122, 40), "spruce_leaves": (52, 88, 52), "birch_leaves": (110, 150, 70),
    "jungle_leaves": (48, 130, 30), "acacia_leaves": (80, 120, 30), "dark_oak_leaves": (50, 92, 26),
    "mangrove_leaves": (70, 120, 40), "cherry_leaves": (229, 172, 195), "azalea_leaves": (90, 115, 44),
    "flowering_azalea_leaves": (110, 112, 70), "glass": (200, 222, 232), "tinted_glass": (44, 38, 46),
    "sea_lantern": (172, 199, 190), "glowstone": (171, 131, 84), "shroomlight": (240, 146, 70),
    "ochre_froglight": (250, 240, 180), "verdant_froglight": (220, 245, 200), "pearlescent_froglight": (245, 230, 240),
    "bookshelf": (117, 94, 60), "chiseled_bookshelf": (120, 92, 58), "crafting_table": (130, 100, 60),
    "cartography_table": (90, 70, 50), "fletching_table": (180, 160, 110), "smithing_table": (60, 60, 70),
    "enchanting_table": (90, 30, 40), "lectern": (150, 115, 70), "loom": (150, 120, 90), "barrel": (120, 90, 50),
    "chest": (160, 110, 40), "trapped_chest": (160, 110, 40), "ender_chest": (30, 40, 40),
    "furnace": (110, 110, 110), "blast_furnace": (90, 90, 95), "smoker": (100, 90, 80), "anvil": (68, 68, 68),
    "chipped_anvil": (68, 68, 68), "damaged_anvil": (68, 68, 68), "grindstone": (140, 140, 140),
    "stonecutter": (120, 115, 110), "cauldron": (60, 60, 64), "water_cauldron": (60, 60, 64), "brewing_stand": (110, 100, 90),
    "hay_block": (166, 139, 12), "target": (226, 170, 157), "bone_block": (229, 225, 207), "melon": (111, 145, 30),
    "pumpkin": (198, 118, 24), "carved_pumpkin": (198, 118, 24), "jack_o_lantern": (214, 152, 52),
    "lantern": (210, 160, 80), "soul_lantern": (90, 180, 190), "chain": (60, 64, 75), "iron_bars": (140, 140, 140),
    "iron_door": (190, 190, 190), "iron_trapdoor": (190, 190, 190), "bell": (230, 190, 70), "end_rod": (240, 235, 225),
    "lightning_rod": (200, 120, 90), "redstone_lamp": (200, 140, 80), "beehive": (180, 140, 80),
    "cake": (240, 230, 220), "candle_cake": (240, 230, 220), "flower_pot": (124, 68, 52), "decorated_pot": (140, 70, 50),
    "composter": (110, 80, 40), "jukebox": (90, 60, 40), "note_block": (90, 60, 40), "scaffolding": (170, 135, 80),
    "ladder": (125, 98, 55), "skeleton_skull": (200, 200, 200), "player_head": (90, 60, 40),
    "amethyst_cluster": (170, 130, 220), "lava": (207, 92, 20), "water": (48, 92, 196), "light": (0, 0, 0),
    "hopper": (70, 70, 72), "cactus": (88, 130, 40), "big_dripleaf_stem": (90, 120, 40), "netherrack": (110, 50, 50),
    # custom
    "castle_bricks": (232, 228, 218), "smooth_castle_stone": (239, 235, 227), "castle_pillar": (230, 225, 214),
    "carved_castle_stone": (221, 215, 202), "castle_crest_stone": (214, 200, 168), "royal_roof_tiles": (43, 76, 155),
    "pale_roof_tiles": (92, 134, 208), "gilded_trim": (217, 169, 59), "gilded_finial": (232, 188, 72),
    "white_plaster": (238, 231, 214), "chandelier": (236, 196, 96), "light_shaft": (0, 0, 0),
}
PLANTS = {
    "grass": (90, 150, 55), "fern": (80, 135, 50), "tall_grass": (90, 150, 55), "large_fern": (80, 135, 50),
    "dandelion": (240, 220, 40), "poppy": (200, 40, 30), "blue_orchid": (40, 160, 220), "allium": (180, 120, 220),
    "azure_bluet": (220, 230, 240), "red_tulip": (210, 50, 40), "orange_tulip": (230, 120, 40),
    "white_tulip": (235, 240, 240), "pink_tulip": (240, 160, 200), "oxeye_daisy": (240, 240, 220),
    "cornflower": (70, 100, 220), "lily_of_the_valley": (240, 240, 240), "torchflower": (230, 120, 40),
    "sunflower": (240, 200, 40), "lilac": (200, 140, 200), "rose_bush": (190, 40, 50), "peony": (230, 170, 210),
    "azalea": (90, 120, 50), "flowering_azalea": (150, 110, 150), "sweet_berry_bush": (60, 100, 50),
    "sugar_cane": (140, 190, 90), "pink_petals": (240, 170, 200), "dead_bush": (140, 110, 60),
    "hanging_roots": (160, 110, 90), "vine": (60, 110, 40), "glow_lichen": (110, 140, 120), "lily_pad": (40, 120, 40),
    "wheat": (200, 180, 80), "carrots": (90, 160, 50), "potatoes": (90, 160, 50), "beetroots": (90, 140, 50),
    "seagrass": (40, 100, 40), "kelp_plant": (50, 110, 40), "spore_blossom": (220, 120, 180),
    "pitcher_plant": (100, 120, 200),
}
EMIT = {
    "chandelier": 15, "lantern": 15, "soul_lantern": 10, "torch": 14, "wall_torch": 14, "soul_torch": 10,
    "soul_wall_torch": 10, "sea_lantern": 15, "glowstone": 15, "shroomlight": 15, "end_rod": 14, "campfire": 15,
    "soul_campfire": 10, "jack_o_lantern": 15, "lava": 15, "ochre_froglight": 15, "verdant_froglight": 15,
    "pearlescent_froglight": 15, "amethyst_cluster": 5, "candle_cake": 3, "redstone_lamp": 15,
    "gilded_finial": 0, "lava_cauldron": 15, "beacon": 15, "conduit": 15,
}
STRIP = ["_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_wall_banner", "_banner", "_stairs",
         "_slab", "_wall", "_fence_gate", "_fence", "_door", "_trapdoor", "_pressure_plate", "_button",
         "_stained_glass_pane", "_stained_glass", "_carpet", "_bed", "_candle_cake", "_candle", "_wool", "_concrete_powder",
         "_concrete", "_glazed_terracotta", "_terracotta"]
PLURAL = {"brick": "bricks", "stone_brick": "stone_bricks", "mossy_stone_brick": "mossy_stone_bricks",
          "deepslate_brick": "deepslate_bricks", "deepslate_tile": "deepslate_tiles", "mud_brick": "mud_bricks",
          "polished_blackstone_brick": "polished_blackstone_bricks", "end_stone_brick": "end_stone_bricks",
          "nether_brick": "nether_bricks", "prismarine_brick": "prismarine_bricks", "castle_brick": "castle_bricks",
          "royal_roof_tile": "royal_roof_tiles", "pale_roof_tile": "pale_roof_tiles", "quartz": "quartz_block",
          "purpur": "purpur_block", "oak": "oak_planks", "spruce": "spruce_planks", "birch": "birch_planks",
          "jungle": "jungle_planks", "acacia": "acacia_planks", "dark_oak": "dark_oak_planks",
          "mangrove": "mangrove_planks", "cherry": "cherry_planks", "bamboo": "bamboo_planks",
          "bamboo_mosaic": "bamboo_mosaic", "polished_blackstone": "polished_blackstone", "iron": "iron_door",
          "stone": "stone", "smooth_stone": "smooth_stone", "gilded_trim": "gilded_trim"}


def _shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


@lru_cache(maxsize=None)
def material(state: str) -> dict:
    bid, props = B.parse(state)
    name = bid.split(":", 1)[1]
    sch = B.REGISTRY.get(bid, "plain")
    out = {"kind": "solid", "alpha": 1.0, "box": None, "emit": 0, "name": name}
    if name in ("air", "cave_air", "void", "light_shaft", "light", "barrier"):
        out["kind"] = "air"
        out["emit"] = int(props.get("level", 0)) if name == "light" else 0
        out["top"] = out["side"] = (0, 0, 0)
        return out
    emit = EMIT.get(name, 0)
    if sch == "candle":
        emit = 3 * int(props.get("candles", 1)) if props.get("lit") == "true" else 0
    if name in ("campfire", "soul_campfire", "furnace", "blast_furnace", "smoker", "redstone_lamp"):
        emit = EMIT.get(name, 13) if props.get("lit", "true") == "true" else 0
    out["emit"] = emit
    # colour lookup
    base = None
    color = None
    for c in DYE_RGB:
        if name.startswith(c + "_"):
            color = c
    rest = name
    for suf in STRIP:
        if rest.endswith(suf):
            rest = rest[: -len(suf)]
            break
    if name in BASE:
        base = BASE[name]
    elif name in PLANTS:
        base = PLANTS[name]
    elif name.startswith("potted_"):
        base = (124, 68, 52)
    elif color and ("_terracotta" in name and "glazed" not in name):
        base = TERRACOTTA[color]
    elif color and rest == color:
        base = DYE_RGB[color]
        if name.endswith("_concrete"):
            base = _shade(base, 0.86)
    elif rest in BASE:
        base = BASE[rest]
    elif rest in PLURAL and PLURAL[rest] in BASE:
        base = BASE[PLURAL[rest]]
    elif rest + "s" in BASE:
        base = BASE[rest + "s"]
    elif rest + "_planks" in BASE:
        base = BASE[rest + "_planks"]
    elif rest + "_block" in BASE:
        base = BASE[rest + "_block"]
    elif name.startswith("stripped_") or name.endswith("_wood"):
        k = name.replace("_wood", "_log")
        base = BASE.get(k, (120, 100, 70))
    elif name in ("candle",):
        base = (230, 220, 190)
    else:
        base = (255, 0, 255)
    top = side = base
    if name == "grass_block":
        side = BASE["dirt"]
    if name.endswith("_log") and props.get("axis", "y") == "y":
        top = _shade(base, 1.25) if not name.startswith("stripped") else base
    if name == "bookshelf":
        top = BASE["oak_planks"]
    out["top"], out["side"] = top, side
    # geometry / kind
    if name == "water" or name == "lava":
        out["kind"] = "water" if name == "water" else "solid"
        out["alpha"] = 0.62
        return out
    if "glass" in name or name in ("iron_bars",):
        out["kind"] = "glass"
        out["alpha"] = 0.28 if name in ("glass", "glass_pane") else (0.55 if name == "iron_bars" else 0.62)
        if name == "iron_bars":
            out["alpha"] = 0.45
        return out
    if sch == "slab":
        t = props.get("type", "bottom")
        out["box"] = None if t == "double" else ((0, 0, 0, 1, 0.5, 1) if t == "bottom" else (0, 0.5, 0, 1, 1, 1))
        out["kind"] = "solid" if t == "double" else "box"
        return out
    if name.endswith("_carpet") or name == "moss_carpet":
        out["kind"], out["box"] = "box", (0, 0, 0, 1, 0.0625, 1)
        return out
    if sch in ("wall", "fence"):
        out["kind"], out["box"] = "box", (0.3, 0, 0.3, 0.7, 1, 0.7)
        return out
    if sch == "trapdoor":
        if props.get("open") == "true":
            f = props.get("facing", "north")
            boxes = {"north": (0, 0, 0.8125, 1, 1, 1), "south": (0, 0, 0, 1, 1, 0.1875),
                     "west": (0.8125, 0, 0, 1, 1, 1), "east": (0, 0, 0, 0.1875, 1, 1)}
            out["kind"], out["box"] = "box", boxes[f]
        else:
            out["kind"] = "box"
            out["box"] = (0, 0, 0, 1, 0.1875, 1) if props.get("half") == "bottom" else (0, 0.8125, 0, 1, 1, 1)
        return out
    if sch == "door":
        f = props.get("facing", "north")
        boxes = {"north": (0, 0, 0.8125, 1, 1, 1), "south": (0, 0, 0, 1, 1, 0.1875),
                 "west": (0.8125, 0, 0, 1, 1, 1), "east": (0, 0, 0, 0.1875, 1, 1)}
        out["kind"], out["box"] = "box", boxes[f]
        return out
    if sch == "bed":
        out["kind"], out["box"] = "box", (0, 0, 0, 1, 0.5625, 1)
        return out
    if sch == "wall_banner":
        f = props.get("facing", "south")
        boxes = {"south": (0.05, -1.0, 0, 0.95, 0.9, 0.1), "north": (0.05, -1.0, 0.9, 0.95, 0.9, 1),
                 "east": (0, -1.0, 0.05, 0.1, 0.9, 0.95), "west": (0.9, -1.0, 0.05, 1, 0.9, 0.95)}
        out["kind"], out["box"] = "banner", boxes[f]
        return out
    if sch == "banner":
        out["kind"], out["box"] = "box", (0.45, 0, 0.45, 0.55, 1, 0.55)
        return out
    if name in ("lantern", "soul_lantern"):
        out["kind"], out["box"] = "box", (0.3, 0.0, 0.3, 0.7, 0.55, 0.7) if props.get("hanging") != "true" else \
            (0.3, 0.1, 0.3, 0.7, 0.65, 0.7)
        return out
    if name == "chandelier":
        out["kind"], out["box"] = "box", (0.1, 0.15, 0.1, 0.9, 0.85, 0.9)
        return out
    if name == "gilded_finial":
        out["kind"], out["box"] = "box", (0.3, 0, 0.3, 0.7, 0.9, 0.7)
        return out
    if name == "chain":
        out["kind"], out["box"] = "box", (0.42, 0, 0.42, 0.58, 1, 0.58)
        return out
    if sch in ("candle",) or name in ("torch", "soul_torch", "wall_torch", "soul_wall_torch", "end_rod",
                                       "lightning_rod", "lever"):
        out["kind"], out["box"] = "box", (0.4, 0, 0.4, 0.6, 0.6, 0.6)
        return out
    if name in PLANTS or name.startswith("potted_") or name == "flower_pot":
        if name in ("lily_pad", "pink_petals"):
            out["kind"], out["box"] = "box", (0, 0, 0, 1, 0.06, 1)
        elif name.startswith("potted_") or name == "flower_pot":
            out["kind"], out["box"] = "box", (0.31, 0, 0.31, 0.69, 0.5, 0.69)
        elif name == "vine":
            out["kind"] = "air"
        else:
            out["kind"], out["box"] = "box", (0.25, 0, 0.25, 0.75, 0.7, 0.75)
        return out
    if sch in ("pressure_plate", "weighted_plate", "button", "sign", "wall_sign", "hanging_sign",
               "wall_hanging_sign", "ladder", "glow_lichen", "skull", "wall_skull"):
        if sch in ("wall_sign", "wall_hanging_sign", "ladder"):
            f = props.get("facing", "north")
            boxes = {"north": (0, 0.25, 0.88, 1, 0.75, 1), "south": (0, 0.25, 0, 1, 0.75, 0.12),
                     "west": (0.88, 0.25, 0, 1, 0.75, 1), "east": (0, 0.25, 0, 0.12, 0.75, 1)}
            out["kind"], out["box"] = "box", boxes[f]
        elif sch == "sign":
            out["kind"], out["box"] = "box", (0.1, 0.4, 0.45, 0.9, 1.0, 0.55)
        else:
            out["kind"] = "air"
        return out
    if name in ("cake", "candle_cake") or sch == "candle_cake":
        out["kind"], out["box"] = "box", (0.06, 0, 0.06, 0.94, 0.5, 0.94)
        return out
    if sch == "chest" or name == "ender_chest":
        out["kind"], out["box"] = "box", (0.06, 0, 0.06, 0.94, 0.88, 0.94)
        return out
    if name in ("snow",):
        out["kind"], out["box"] = "box", (0, 0, 0, 1, int(props.get("layers", 1)) / 8.0, 1)
        return out
    if name in ("lectern", "brewing_stand", "grindstone", "bell", "anvil", "chipped_anvil", "damaged_anvil",
                "decorated_pot", "hopper", "cauldron", "water_cauldron", "amethyst_cluster", "stonecutter",
                "big_dripleaf_stem", "scaffolding", "composter"):
        out["kind"], out["box"] = "box", (0.12, 0, 0.12, 0.88, 0.9, 0.88)
        return out
    if name == "dirt_path" or name == "farmland":
        out["kind"], out["box"] = "box", (0, 0, 0, 1, 0.9375, 1)
        return out
    if sch == "stairs":
        out["kind"] = "stairs"
        return out
    return out
