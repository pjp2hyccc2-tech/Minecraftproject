"""Block registry, block-state helpers and the validator for the capital build.

Every block state written by the capital generators goes through :func:`canon` (via ``World.idx``), which
parses ``"ns:id[k=v,...]"``, checks the id against the registry below and every property name/value against
the family schema, fills in a few defaults that matter (stairs facing/half/shape, persistent leaves, lit
candles ...) and returns a canonical string with sorted properties. Unknown ids or values raise ``ValueError``.

Connection properties (fence/pane/wall/iron-bar north/east/south/west/up, stair shapes next to other stairs)
are recomputed by the Java loader after placement, so the helpers never emit them.

Only Minecraft Java 1.20.1 ids and properties are listed. Custom ids are the ones the lead registers in Java.
"""
from __future__ import annotations

from functools import lru_cache

H4 = ("north", "south", "west", "east")
F6 = ("north", "south", "west", "east", "up", "down")
BOOL = ("true", "false")
R16 = tuple(str(i) for i in range(16))


def _rng(a, b):
    return tuple(str(i) for i in range(a, b + 1))


# --------------------------------------------------------------------------------------------- schemas
SCHEMAS: dict[str, dict[str, tuple]] = {
    "plain": {},
    "axis": {"axis": ("x", "y", "z")},
    "stairs": {"facing": H4, "half": ("top", "bottom"),
               "shape": ("straight", "inner_left", "inner_right", "outer_left", "outer_right"), "waterlogged": BOOL},
    "slab": {"type": ("top", "bottom", "double"), "waterlogged": BOOL},
    "wall": {"waterlogged": BOOL, "up": BOOL, "north": ("none", "low", "tall"), "south": ("none", "low", "tall"),
             "east": ("none", "low", "tall"), "west": ("none", "low", "tall")},
    "fence": {"waterlogged": BOOL, "north": BOOL, "south": BOOL, "east": BOOL, "west": BOOL},
    "pane": {"waterlogged": BOOL, "north": BOOL, "south": BOOL, "east": BOOL, "west": BOOL},
    "fence_gate": {"facing": H4, "in_wall": BOOL, "open": BOOL, "powered": BOOL},
    "door": {"facing": H4, "half": ("upper", "lower"), "hinge": ("left", "right"), "open": BOOL, "powered": BOOL},
    "trapdoor": {"facing": H4, "half": ("top", "bottom"), "open": BOOL, "powered": BOOL, "waterlogged": BOOL},
    "bed": {"facing": H4, "part": ("head", "foot"), "occupied": BOOL},
    "lantern": {"hanging": BOOL, "waterlogged": BOOL},
    "candle": {"candles": ("1", "2", "3", "4"), "lit": BOOL, "waterlogged": BOOL},
    "candle_cake": {"lit": BOOL},
    "leaves": {"distance": _rng(1, 7), "persistent": BOOL},
    "banner": {"rotation": R16},
    "wall_banner": {"facing": H4},
    "sign": {"rotation": R16, "waterlogged": BOOL},
    "wall_sign": {"facing": H4, "waterlogged": BOOL},
    "hanging_sign": {"attached": BOOL, "rotation": R16, "waterlogged": BOOL},
    "wall_hanging_sign": {"facing": H4, "waterlogged": BOOL},
    "facing4": {"facing": H4},
    "facing6": {"facing": F6},
    "chest": {"facing": H4, "type": ("single", "left", "right"), "waterlogged": BOOL},
    "ender_chest": {"facing": H4, "waterlogged": BOOL},
    "lectern": {"facing": H4, "has_book": BOOL, "powered": BOOL},
    "furnace": {"facing": H4, "lit": BOOL},
    "barrel": {"facing": F6, "open": BOOL},
    "chain": {"axis": ("x", "y", "z"), "waterlogged": BOOL},
    "wall_torch": {"facing": H4},
    "campfire": {"facing": H4, "lit": BOOL, "signal_fire": BOOL, "waterlogged": BOOL},
    "fluid": {"level": _rng(0, 15)},
    "snowy": {"snowy": BOOL},
    "double_plant": {"half": ("upper", "lower")},
    "cake": {"bites": _rng(0, 6)},
    "bell": {"attachment": ("floor", "ceiling", "single_wall", "double_wall"), "facing": H4, "powered": BOOL},
    "grindstone": {"face": ("floor", "wall", "ceiling"), "facing": H4},
    "button": {"face": ("floor", "wall", "ceiling"), "facing": H4, "powered": BOOL},
    "lever": {"face": ("floor", "wall", "ceiling"), "facing": H4, "powered": BOOL},
    "ladder": {"facing": H4, "waterlogged": BOOL},
    "vine": {"north": BOOL, "east": BOOL, "south": BOOL, "west": BOOL, "up": BOOL},
    "glow_lichen": {"north": BOOL, "east": BOOL, "south": BOOL, "west": BOOL, "up": BOOL, "down": BOOL,
                    "waterlogged": BOOL},
    "cauldron_level": {"level": ("1", "2", "3")},
    "brewing_stand": {"has_bottle_0": BOOL, "has_bottle_1": BOOL, "has_bottle_2": BOOL},
    "skull": {"rotation": R16},
    "wall_skull": {"facing": H4},
    "pink_petals": {"facing": H4, "flower_amount": ("1", "2", "3", "4")},
    "amethyst": {"facing": F6, "waterlogged": BOOL},
    "lightning_rod": {"facing": F6, "powered": BOOL, "waterlogged": BOOL},
    "snow": {"layers": _rng(1, 8)},
    "farmland": {"moisture": _rng(0, 7)},
    "sea_pickle": {"pickles": ("1", "2", "3", "4"), "waterlogged": BOOL},
    "light": {"level": _rng(0, 15), "waterlogged": BOOL},
    "lit": {"lit": BOOL},
    "decorated_pot": {"facing": H4, "waterlogged": BOOL},
    "composter": {"level": _rng(0, 8)},
    "target": {"power": _rng(0, 15)},
    "age15": {"age": _rng(0, 15)},
    "age3": {"age": _rng(0, 3)},
    "age7": {"age": _rng(0, 7)},
    "pressure_plate": {"powered": BOOL},
    "weighted_plate": {"power": _rng(0, 15)},
    "chiseled_bookshelf": {"facing": H4, **{f"slot_{i}_occupied": BOOL for i in range(6)}},
    "beehive": {"facing": H4, "honey_level": _rng(0, 5)},
    "jukebox": {"has_record": BOOL},
    "waterlogged": {"waterlogged": BOOL},
    "light_shaft": {"facing": H4},
    "gimmick": {"facing": H4},          # flag, clock_face, windmill_sails, water_wheel (block entity data)
    "smoke_emitter": {"heavy": BOOL},
    "fountain_spray": {"power": ("1", "2", "3"), "facing": ("up", "north", "south", "west", "east")},
    "scaffolding": {"bottom": BOOL, "distance": _rng(0, 7), "waterlogged": BOOL},
    "hopper": {"enabled": BOOL, "facing": ("down", "north", "south", "west", "east")},
}

# Defaults written into the canonical string when absent (only where the game default is not what we want,
# or where a canonical palette benefits from an explicit value).
DEFAULTS: dict[str, dict[str, str]] = {
    "stairs": {"facing": "north", "half": "bottom", "shape": "straight"},
    "slab": {"type": "bottom"},
    "axis": {"axis": "y"},
    "chain": {"axis": "y"},
    "leaves": {"persistent": "true", "distance": "1"},
    "candle": {"candles": "1", "lit": "true"},
    "candle_cake": {"lit": "true"},
    "lantern": {"hanging": "false"},
    "door": {"half": "lower", "hinge": "left", "open": "false"},
    "trapdoor": {"half": "bottom", "open": "false"},
    "bed": {"part": "foot"},
    "double_plant": {"half": "lower"},
    "fluid": {"level": "0"},
    "campfire": {"lit": "true"},
    "furnace": {"lit": "false"},
    "chest": {"type": "single"},
    "banner": {"rotation": "0"},
    "sign": {"rotation": "0"},
    "hanging_sign": {"rotation": "0"},
    "light": {"level": "15"},
    "lit": {"lit": "false"},
    "smoke_emitter": {"heavy": "false"},
    "fountain_spray": {"power": "2", "facing": "up"},
}

# --------------------------------------------------------------------------------------------- registry
REGISTRY: dict[str, str] = {}  # full id -> schema name
CUSTOM_NS = "eternalrealm"
CUSTOM = set()

COLORS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black")
WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")

# base block -> (full, stairs, slab, wall); None if the variant does not exist in 1.20.1
MATERIALS: dict[str, tuple] = {}


def _reg(ids, schema="plain", ns="minecraft"):
    if isinstance(ids, str):
        ids = [ids]
    for i in ids:
        full = i if ":" in i else f"{ns}:{i}"
        REGISTRY[full] = schema
        if full.startswith(CUSTOM_NS + ":"):
            CUSTOM.add(full.split(":", 1)[1])


def _family(base, stairs=None, slab=None, wall=None, ns="minecraft", base_schema="plain"):
    _reg(base, base_schema, ns)
    if stairs:
        _reg(stairs, "stairs", ns)
    if slab:
        _reg(slab, "slab", ns)
    if wall:
        _reg(wall, "wall", ns)
    f = lambda s: (f"{ns}:{s}" if s else None)  # noqa: E731
    MATERIALS[base] = (f(base), f(stairs), f(slab), f(wall))


def _std(base, stem=None, stairs=True, slab=True, wall=True, ns="minecraft"):
    stem = stem or base
    _family(base, f"{stem}_stairs" if stairs else None, f"{stem}_slab" if slab else None,
            f"{stem}_wall" if wall else None, ns)


# stone families
_std("stone", wall=False)
_std("cobblestone")
_std("mossy_cobblestone")
_std("stone_bricks", "stone_brick")
_std("mossy_stone_bricks", "mossy_stone_brick")
_family("smooth_stone", None, "smooth_stone_slab", None)
for _b in ("andesite", "diorite", "granite"):
    _std(_b)
    _std(f"polished_{_b}", wall=False)
_std("cobbled_deepslate")
_std("polished_deepslate")
_std("deepslate_bricks", "deepslate_brick")
_std("deepslate_tiles", "deepslate_tile")
_std("blackstone")
_std("polished_blackstone")
_std("polished_blackstone_bricks", "polished_blackstone_brick")
_family("quartz_block", "quartz_stairs", "quartz_slab", None)
_std("smooth_quartz", wall=False)
_std("sandstone")
_std("smooth_sandstone", wall=False)
_family("cut_sandstone", None, "cut_sandstone_slab", None)
_std("red_sandstone")
_std("bricks", "brick")
_std("mud_bricks", "mud_brick")
_std("end_stone_bricks", "end_stone_brick")
_std("prismarine")
_family("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab", None)
_std("dark_prismarine", wall=False)
_std("nether_bricks", "nether_brick")
_family("purpur_block", "purpur_stairs", "purpur_slab", None)
for _c in ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_oxidized_cut_copper"):
    _std(_c, wall=False)
for _w in WOODS:
    _family(f"{_w}_planks", f"{_w}_stairs", f"{_w}_slab", None)
_family("bamboo_planks", "bamboo_stairs", "bamboo_slab", None)
_family("bamboo_mosaic", "bamboo_mosaic_stairs", "bamboo_mosaic_slab", None)
# custom castle materials
_family("castle_bricks", "castle_brick_stairs", "castle_brick_slab", "castle_brick_wall", ns=CUSTOM_NS)
_family("smooth_castle_stone", "smooth_castle_stone_stairs", "smooth_castle_stone_slab", None, ns=CUSTOM_NS)
_family("royal_roof_tiles", "royal_roof_tile_stairs", "royal_roof_tile_slab", None, ns=CUSTOM_NS)
_family("pale_roof_tiles", "pale_roof_tile_stairs", "pale_roof_tile_slab", None, ns=CUSTOM_NS)
_family("gilded_trim", "gilded_trim_stairs", "gilded_trim_slab", None, ns=CUSTOM_NS)
_family("terracotta_roof_tiles", "terracotta_roof_tile_stairs", "terracotta_roof_tile_slab", None, ns=CUSTOM_NS)
_family("cobbled_paving", "cobbled_paving_stairs", "cobbled_paving_slab", None, ns=CUSTOM_NS)
_family("plaza_flagstones", "plaza_flagstone_stairs", "plaza_flagstone_slab", None, ns=CUSTOM_NS)
_reg(["leaded_glass"], "plain", CUSTOM_NS)
_reg(["leaded_glass_pane"], "pane", CUSTOM_NS)
_reg(["castle_pillar"], "axis", CUSTOM_NS)
_reg(["carved_castle_stone", "castle_crest_stone", "gilded_finial", "white_plaster", "chandelier", "void"],
     "plain", CUSTOM_NS)
_reg(["light_shaft"], "light_shaft", CUSTOM_NS)
_reg(["flag", "clock_face", "windmill_sails", "water_wheel"], "gimmick", CUSTOM_NS)
_reg(["smoke_emitter"], "smoke_emitter", CUSTOM_NS)
_reg(["fountain_spray"], "fountain_spray", CUSTOM_NS)

# plain full blocks and simple things
_reg([
    "air", "cave_air", "cracked_stone_bricks", "chiseled_stone_bricks", "calcite", "tuff", "dripstone_block",
    "cracked_deepslate_bricks", "cracked_deepslate_tiles", "chiseled_deepslate", "chiseled_polished_blackstone",
    "gilded_blackstone", "cracked_polished_blackstone_bricks", "quartz_bricks", "chiseled_quartz_block",
    "chiseled_sandstone", "smooth_red_sandstone", "cut_red_sandstone", "packed_mud", "smooth_basalt", "end_stone",
    "dirt", "coarse_dirt", "rooted_dirt", "dirt_path", "gravel", "sand", "red_sand", "clay", "mud", "moss_block",
    "moss_carpet", "snow_block", "ice", "packed_ice", "blue_ice", "glass", "tinted_glass", "terracotta",
    "sea_lantern", "glowstone", "shroomlight", "bookshelf", "crafting_table", "cartography_table",
    "fletching_table", "smithing_table", "enchanting_table", "gold_block", "iron_block", "lapis_block",
    "diamond_block", "emerald_block", "copper_block", "waxed_copper_block", "amethyst_block", "obsidian",
    "crying_obsidian", "cauldron", "lava_cauldron", "flower_pot", "grass", "fern", "dead_bush", "dandelion",
    "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
    "oxeye_daisy", "cornflower", "lily_of_the_valley", "torchflower", "azalea", "flowering_azalea",
    "spore_blossom", "lily_pad", "torch", "soul_torch", "melon", "pumpkin", "slime_block", "honey_block",
    "honeycomb_block", "dried_kelp_block", "mossy_cobblestone", "cobweb", "sponge", "wet_sponge", "beacon",
    "sculk", "white_carpet", "redstone_block", "coal_block", "raw_iron_block", "raw_gold_block", "raw_copper_block",
    "netherrack", "soul_sand", "soul_soil", "magma_block", "brown_mushroom_block", "red_mushroom_block",
    "mushroom_stem", "nether_wart_block", "warped_wart_block", "potted_dandelion", "potted_poppy",
    "potted_blue_orchid", "potted_allium", "potted_azure_bluet", "potted_red_tulip", "potted_orange_tulip",
    "potted_white_tulip", "potted_pink_tulip", "potted_oxeye_daisy", "potted_cornflower",
    "potted_lily_of_the_valley", "potted_fern", "potted_azalea_bush", "potted_flowering_azalea_bush",
    "potted_oak_sapling", "potted_spruce_sapling", "potted_birch_sapling", "potted_cherry_sapling",
    "potted_torchflower", "potted_dead_bush", "potted_cactus", "potted_bamboo", "potted_red_mushroom",
    "potted_brown_mushroom", "hanging_roots", "big_dripleaf_stem", "cactus", "bedrock", "barrier",
    "stripped_bamboo_block", "smooth_stone", "polished_basalt", "loom", "note_block", "jack_o_lantern",
    "carved_pumpkin",
])
_reg(["hay_block", "bone_block", "deepslate", "basalt", "quartz_pillar", "purpur_pillar", "bamboo_block",
      "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "muddy_mangrove_roots"], "axis")
for _w in WOODS:
    _reg([f"{_w}_log", f"stripped_{_w}_log", f"{_w}_wood", f"stripped_{_w}_wood"], "axis")
    _reg(f"{_w}_leaves", "leaves")
    _reg(f"{_w}_fence", "fence")
    _reg(f"{_w}_fence_gate", "fence_gate")
    _reg(f"{_w}_door", "door")
    _reg(f"{_w}_trapdoor", "trapdoor")
    _reg(f"{_w}_sign", "sign")
    _reg(f"{_w}_wall_sign", "wall_sign")
    _reg(f"{_w}_hanging_sign", "hanging_sign")
    _reg(f"{_w}_wall_hanging_sign", "wall_hanging_sign")
    _reg(f"{_w}_pressure_plate", "pressure_plate")
    _reg(f"{_w}_button", "button")
_reg(["mangrove_roots"], "waterlogged")
_reg(["azalea_leaves", "flowering_azalea_leaves"], "leaves")
_reg(["nether_brick_fence", "bamboo_fence"], "fence")
_reg(["glass_pane", "iron_bars"], "pane")
_reg(["iron_door"], "door")
_reg(["iron_trapdoor"], "trapdoor")
_reg(["oak_sapling"], "plain")
for _c in COLORS:
    _reg([f"{_c}_wool", f"{_c}_carpet", f"{_c}_concrete", f"{_c}_concrete_powder", f"{_c}_terracotta",
          f"{_c}_stained_glass"])
    _reg(f"{_c}_glazed_terracotta", "facing4")
    _reg(f"{_c}_stained_glass_pane", "pane")
    _reg(f"{_c}_banner", "banner")
    _reg(f"{_c}_wall_banner", "wall_banner")
    _reg(f"{_c}_bed", "bed")
    _reg(f"{_c}_candle", "candle")
    _reg(f"{_c}_candle_cake", "candle_cake")
_reg("candle", "candle")
_reg("candle_cake", "candle_cake")
_reg(["lantern", "soul_lantern"], "lantern")
_reg(["wall_torch", "soul_wall_torch"], "wall_torch")
_reg(["campfire", "soul_campfire"], "campfire")
_reg("chain", "chain")
_reg(["water", "lava"], "fluid")
_reg(["grass_block", "podzol", "mycelium"], "snowy")
_reg(["sunflower", "lilac", "rose_bush", "peony", "tall_grass", "large_fern", "pitcher_plant"], "double_plant")
_reg("cake", "cake")
_reg("bell", "bell")
_reg("grindstone", "grindstone")
_reg(["stone_button", "polished_blackstone_button"], "button")
_reg(["stone_pressure_plate", "polished_blackstone_pressure_plate"], "pressure_plate")
_reg(["light_weighted_pressure_plate", "heavy_weighted_pressure_plate"], "weighted_plate")
_reg("lever", "lever")
_reg("ladder", "ladder")
_reg("vine", "vine")
_reg("glow_lichen", "glow_lichen")
_reg(["water_cauldron", "powder_snow_cauldron"], "cauldron_level")
_reg("brewing_stand", "brewing_stand")
_reg(["skeleton_skull", "player_head", "wither_skeleton_skull", "zombie_head", "creeper_head", "dragon_head",
      "piglin_head"], "skull")
_reg(["skeleton_wall_skull", "player_wall_head", "wither_skeleton_wall_skull", "zombie_wall_head",
      "creeper_wall_head", "dragon_wall_head", "piglin_wall_head"], "wall_skull")
_reg("pink_petals", "pink_petals")
_reg(["amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud"], "amethyst")
_reg("lightning_rod", "lightning_rod")
_reg(["end_rod"], "facing6")
_reg(["anvil", "chipped_anvil", "damaged_anvil", "stonecutter", "loom", "carved_pumpkin", "jack_o_lantern"],
     "facing4")
_reg(["barrel"], "barrel")
_reg(["chest", "trapped_chest"], "chest")
_reg("ender_chest", "ender_chest")
_reg("lectern", "lectern")
_reg(["furnace", "blast_furnace", "smoker"], "furnace")
_reg("snow", "snow")
_reg("farmland", "farmland")
_reg("sea_pickle", "sea_pickle")
_reg("light", "light")
_reg("redstone_lamp", "lit")
_reg("decorated_pot", "decorated_pot")
_reg("composter", "composter")
_reg("target", "target")
_reg(["sugar_cane"], "age15")
_reg(["sweet_berry_bush"], "age3")
_reg(["wheat", "carrots", "potatoes"], "age7")
_reg(["beetroots"], "age3")
_reg("chiseled_bookshelf", "chiseled_bookshelf")
_reg(["beehive", "bee_nest"], "beehive")
_reg("jukebox", "jukebox")
_reg("scaffolding", "scaffolding")
_reg("hopper", "hopper")
_reg(["seagrass", "kelp_plant"], "plain")
_reg(["conduit"], "waterlogged")

for _w in WOODS:
    MATERIALS[_w] = MATERIALS[f"{_w}_planks"]

# Some convenient aliases for materials (``stairs("castle")`` etc.)
ALIASES = {
    "polished_blackstone_brick": "polished_blackstone_bricks", "castle_brick": "castle_bricks",
    "royal_roof_tile": "royal_roof_tiles", "pale_roof_tile": "pale_roof_tiles", "nether_brick": "nether_bricks",
    "end_stone_brick": "end_stone_bricks", "prismarine_brick": "prismarine_bricks",
    "castle": "castle_bricks", "smooth_castle": "smooth_castle_stone", "roof": "royal_roof_tiles",
    "royal_roof": "royal_roof_tiles", "pale_roof": "pale_roof_tiles", "gold": "gilded_trim",
    "gilded": "gilded_trim", "quartz": "quartz_block", "stone_brick": "stone_bricks", "brick": "bricks",
    "deepslate_tile": "deepslate_tiles", "deepslate_brick": "deepslate_bricks", "mud_brick": "mud_bricks",
    "terracotta_roof_tile": "terracotta_roof_tiles", "terracotta_roof": "terracotta_roof_tiles",
    "plaza_flagstone": "plaza_flagstones",
}

# Block entities whose SNBT the loader applies (the loader creates the block entity, then loads this data).
BE_FAMILIES = ("banner", "wall_banner", "sign", "wall_sign", "hanging_sign", "wall_hanging_sign", "lectern",
               "chest", "barrel", "light_shaft", "skull", "wall_skull", "decorated_pot", "chiseled_bookshelf",
               "campfire", "furnace", "brewing_stand", "ender_chest", "bell", "jukebox", "beehive", "hopper",
               "gimmick")


# --------------------------------------------------------------------------------------------- parsing
def full_id(name: str) -> str:
    if ":" in name:
        return name
    if name in CUSTOM:
        return f"{CUSTOM_NS}:{name}"
    return f"minecraft:{name}"


def parse(state: str):
    """``'minecraft:oak_stairs[facing=east]'`` -> ``('minecraft:oak_stairs', {'facing': 'east'})``."""
    state = state.strip()
    if "[" in state:
        bid, rest = state.split("[", 1)
        if not rest.endswith("]"):
            raise ValueError(f"bad block state {state!r}")
        props = {}
        body = rest[:-1].strip()
        if body:
            for kv in body.split(","):
                k, _, v = kv.partition("=")
                props[k.strip()] = v.strip()
    else:
        bid, props = state, {}
    return full_id(bid.strip()), props


def fmt(bid: str, props: dict) -> str:
    if not props:
        return bid
    return bid + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


@lru_cache(maxsize=None)
def canon(state: str) -> str:
    """Validate and canonicalise a block state string (raises ValueError when invalid)."""
    bid, props = parse(state)
    schema_name = REGISTRY.get(bid)
    if schema_name is None:
        raise ValueError(f"unknown block id {bid!r} (state {state!r})")
    schema = SCHEMAS[schema_name]
    for k, v in props.items():
        if k not in schema:
            raise ValueError(f"block {bid}: unknown property {k!r} (state {state!r})")
        if v not in schema[k]:
            raise ValueError(f"block {bid}: bad value {k}={v!r} (state {state!r})")
    out = dict(DEFAULTS.get(schema_name, {}))
    out.update(props)
    return fmt(bid, out)


def schema_of(state: str) -> str:
    return REGISTRY[parse(state)[0]]


def block_id(state: str) -> str:
    return parse(state)[0]


def validate(state: str) -> bool:
    try:
        canon(state)
        return True
    except ValueError:
        return False


# --------------------------------------------------------------------------------------------- rotation
_CW = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}
_MX = {"east": "west", "west": "east", "north": "north", "south": "south", "up": "up", "down": "down"}
_SHAPE_MIRROR = {"inner_left": "inner_right", "inner_right": "inner_left", "outer_left": "outer_right",
                 "outer_right": "outer_left", "straight": "straight"}


@lru_cache(maxsize=None)
def transform(state: str, rot: int = 0, mirror_x: bool = False) -> str:
    """Rotate a (canonical) state clockwise by ``rot`` quarter turns (seen from above), optionally after
    mirroring east<->west (mirror applied first, in the building's own frame)."""
    rot %= 4
    if rot == 0 and not mirror_x:
        return state
    bid, props = parse(state)
    p = dict(props)
    if mirror_x:
        if "facing" in p:
            p["facing"] = _MX[p["facing"]]
        if "rotation" in p:
            p["rotation"] = str((16 - int(p["rotation"])) % 16)
        if "shape" in p and p["shape"] in _SHAPE_MIRROR:
            p["shape"] = _SHAPE_MIRROR[p["shape"]]
        if "hinge" in p:
            p["hinge"] = "right" if p["hinge"] == "left" else "left"
        if "east" in p and "west" in p:
            p["east"], p["west"] = p["west"], p["east"]
    for _ in range(rot):
        if "facing" in p:
            p["facing"] = _CW[p["facing"]]
        if "rotation" in p:
            p["rotation"] = str((int(p["rotation"]) + 4) % 16)
        if "axis" in p and p["axis"] in ("x", "z"):
            p["axis"] = "z" if p["axis"] == "x" else "x"
        if all(k in p for k in ("north", "east", "south", "west")):
            p["north"], p["east"], p["south"], p["west"] = p["west"], p["north"], p["east"], p["south"]
    return fmt(bid, p)


# --------------------------------------------------------------------------------------------- helpers
def _mat(material: str) -> tuple:
    m = ALIASES.get(material, material)
    m = m.split(":", 1)[1] if ":" in m else m
    if m not in MATERIALS:
        raise ValueError(f"unknown material {material!r}")
    return MATERIALS[m]


def S(name: str, **props) -> str:
    """Generic state: ``S('oak_door', facing='north', half='upper')``; booleans/ints are converted."""
    p = {k: (str(bool(v)).lower() if type(v).__name__ in ("bool", "bool_") else str(v))
         for k, v in props.items() if v is not None}
    return canon(fmt(full_id(name), p))


def full(material: str) -> str:
    """Full block of a material family (or any plain block id)."""
    m = ALIASES.get(material, material)
    key = m.split(":", 1)[1] if ":" in m else m
    if key in MATERIALS:
        return canon(MATERIALS[key][0])
    return canon(full_id(m))


def stairs(material: str, facing="north", half="bottom", shape="straight") -> str:
    s = _mat(material)[1]
    if not s:
        raise ValueError(f"{material} has no stairs")
    return canon(fmt(s, {"facing": facing, "half": half, "shape": shape}))


def slab(material: str, type="bottom") -> str:  # noqa: A002
    s = _mat(material)[2]
    if not s:
        raise ValueError(f"{material} has no slab")
    return canon(fmt(s, {"type": type}))


def wall(material: str) -> str:
    s = _mat(material)[3]
    if not s:
        raise ValueError(f"{material} has no wall")
    return canon(s)


def has_stairs(material):
    return bool(_mat(material)[1])


def log(wood="oak", axis="y", stripped=False, wood_block=False) -> str:
    kind = "wood" if wood_block else "log"
    return S(f"{'stripped_' if stripped else ''}{wood}_{kind}", axis=axis)


def pillar(axis="y") -> str:
    return S("castle_pillar", axis=axis)


def leaves(kind="oak") -> str:
    return S(f"{kind}_leaves", persistent=True, distance=1)


def fence(wood="oak") -> str:
    return S(f"{wood}_fence")


def gate(wood="oak", facing="north", open=False) -> str:  # noqa: A002
    return S(f"{wood}_fence_gate", facing=facing, open=open)


def pane(color: str | None = None) -> str:
    return S(f"{color}_stained_glass_pane" if color else "glass_pane")


def glass(color: str | None = None) -> str:
    return S(f"{color}_stained_glass" if color else "glass")


def trapdoor(wood="oak", facing="north", half="bottom", open=False) -> str:  # noqa: A002
    return S(f"{wood}_trapdoor", facing=facing, half=half, open=open)


def carpet(color="red") -> str:
    return S(f"{color}_carpet")


def wool(color="red") -> str:
    return S(f"{color}_wool")


def lantern(hanging=False, soul=False) -> str:
    return S("soul_lantern" if soul else "lantern", hanging=hanging)


def candle(n=1, color: str | None = None, lit=True) -> str:
    return S(f"{color}_candle" if color else "candle", candles=n, lit=lit)


def chain(axis="y") -> str:
    return S("chain", axis=axis)


def torch(facing: str | None = None, soul=False) -> str:
    if facing is None:
        return S("soul_torch" if soul else "torch")
    return S("soul_wall_torch" if soul else "wall_torch", facing=facing)


def banner(color="blue", rotation=0) -> str:
    return S(f"{color}_banner", rotation=rotation)


def wall_banner(color="blue", facing="south") -> str:
    return S(f"{color}_wall_banner", facing=facing)


def sign(wood="oak", rotation=0) -> str:
    return S(f"{wood}_sign", rotation=rotation)


def wall_sign(wood="oak", facing="south") -> str:
    return S(f"{wood}_wall_sign", facing=facing)


def hanging_sign(wood="oak", facing="south") -> str:
    return S(f"{wood}_wall_hanging_sign", facing=facing)


def lectern(facing="north", has_book=False) -> str:
    return S("lectern", facing=facing, has_book=has_book)


def chest(facing="north", type="single") -> str:  # noqa: A002
    return S("chest", facing=facing, type=type)


def barrel(facing="up", open=False) -> str:  # noqa: A002
    return S("barrel", facing=facing, open=open)


def potted(plant: str) -> str:
    return S(f"potted_{plant}") if plant else S("flower_pot")


def light_shaft(facing: str) -> str:
    return S("light_shaft", facing=facing)


def door_states(wood="oak", facing="north", hinge="left", open=False):  # noqa: A002
    """(lower, upper) states of a door; ``facing`` is the direction a player walking in faces."""
    n = "iron_door" if wood == "iron" else f"{wood}_door"
    return (S(n, facing=facing, half="lower", hinge=hinge, open=open),
            S(n, facing=facing, half="upper", hinge=hinge, open=open))


def bed_states(color="red", facing="north"):
    """(foot, head) states of a bed; the head lies one block towards ``facing`` from the foot."""
    return S(f"{color}_bed", facing=facing, part="foot"), S(f"{color}_bed", facing=facing, part="head")


def tall_plant_states(name="tall_grass"):
    return S(name, half="lower"), S(name, half="upper")


# --------------------------------------------------------------------------------------------- SNBT
def snbt_str(s: str) -> str:
    """Quote a string for SNBT (double quotes, escaped)."""
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _json_text(text: str, color: str | None = None, bold=False, italic=False) -> str:
    import json
    d = {"text": text}
    if color:
        d["color"] = color
    if bold:
        d["bold"] = True
    if italic:
        d["italic"] = True
    return json.dumps(d, ensure_ascii=False, separators=(",", ":"))


def sign_nbt(lines, color="black", glowing=False, back_lines=None, waxed=True) -> str:
    """1.20.1 sign block-entity SNBT with ``front_text``/``back_text`` (4 lines each)."""
    def side(ls):
        ls = list(ls or [])[:4]
        ls += [""] * (4 - len(ls))
        msgs = ",".join("'" + _json_text(t).replace("\\", "\\\\").replace("'", "\\'") + "'" for t in ls)
        return f"{{messages:[{msgs}],color:{snbt_str(color)},has_glowing_text:{'1b' if glowing else '0b'}}}"
    return f"{{front_text:{side(lines)},back_text:{side(back_lines)},is_waxed:{'1b' if waxed else '0b'}}}"


DYE = {c: i for i, c in enumerate(COLORS)}


def banner_nbt(patterns) -> str:
    """``patterns`` = [(pattern_code, colour_name_or_index), ...] -> ``{Patterns:[{Pattern:"bo",Color:4}]}``."""
    items = []
    for code, col in patterns:
        ci = DYE[col] if isinstance(col, str) else int(col)
        items.append(f'{{Pattern:{snbt_str(code)},Color:{ci}}}')
    return "{Patterns:[" + ",".join(items) + "]}"


KINGDOM_BANNER = banner_nbt([("er_crest", 4), ("bo", 4)])


def light_shaft_nbt(length=14.0, width=2.0, height=4.0, pitch=38.0) -> str:
    return f"{{Length:{float(length):.1f}f,Width:{float(width):.1f}f,Height:{float(height):.1f}f,Pitch:{float(pitch):.1f}f}}"


def item_snbt(item: str, count=1, tag: str | None = None) -> str:
    s = f'{{id:{snbt_str(full_id(item))},Count:{int(count)}b'
    if tag:
        s += f",tag:{tag}"
    return s + "}"


def lectern_nbt(title: str, author: str, pages) -> str:
    """Lectern holding a written book (pages = list of plain strings)."""
    import json
    pg = ",".join("'" + json.dumps({"text": p}, ensure_ascii=False).replace("\\", "\\\\").replace("'", "\\'") + "'"
                  for p in pages)
    tag = f"{{title:{snbt_str(title)},author:{snbt_str(author)},resolved:1b,pages:[{pg}]}}"
    return f"{{Book:{item_snbt('written_book', 1, tag)},Page:0}}"
