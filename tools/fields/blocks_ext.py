"""Extends the capital block registry (``tools/capital/blocks.py``) and preview colours with the blocks the field
set pieces use that the capital registry on this branch does not list yet: the town gimmicks (flag, clock face,
windmill sails, water wheel, smoke emitter, fountain spray), the town materials (terracotta roof tiles, cobbled
paving, plaza flagstones, leaded glass) and the waystone (転移碑) ``eternalrealm:waystone[half,facing]``.

Registration is idempotent (the same schemas as the town branch), so this module keeps working once the capital
registry gains them. Import it before building anything: ``from fields import blocks_ext as BX``.

Also: SNBT helpers for the gimmicks and translatable (multilingual) sign text.
"""
from __future__ import annotations

import json

from capital import blocks as B
from capital import colors as C

H4 = B.H4
BOOL = B.BOOL

# ------------------------------------------------------------------------------------------------ schemas
B.SCHEMAS.setdefault("gimmick", {"facing": H4})
B.SCHEMAS.setdefault("smoke_emitter", {"heavy": BOOL})
B.SCHEMAS.setdefault("fountain_spray", {"power": ("1", "2", "3"), "facing": ("up", "north", "south", "west", "east")})
B.SCHEMAS.setdefault("waystone", {"half": ("lower", "upper"), "facing": H4})
B.DEFAULTS.setdefault("smoke_emitter", {"heavy": "false"})
B.DEFAULTS.setdefault("fountain_spray", {"power": "2", "facing": "up"})
B.DEFAULTS.setdefault("waystone", {"half": "lower", "facing": "south"})

# farmland/cauldrons etc. are in the base registry; a few vanilla blocks the countryside needs are not
B._reg(["rail"], "rail_shape")
B.SCHEMAS.setdefault("rail_shape", {"shape": ("north_south", "east_west", "ascending_east", "ascending_west",
                                              "ascending_north", "ascending_south", "south_east", "south_west",
                                              "north_west", "north_east"), "waterlogged": BOOL})
B._reg(["powered_rail", "detector_rail", "activator_rail"], "rail_straight")
B.SCHEMAS.setdefault("rail_straight", {"shape": ("north_south", "east_west", "ascending_east", "ascending_west",
                                                 "ascending_north", "ascending_south"), "powered": BOOL,
                                       "waterlogged": BOOL})
B._reg(["pointed_dripstone"], "dripstone")
B.SCHEMAS.setdefault("dripstone", {"thickness": ("tip_merge", "tip", "frustum", "middle", "base"),
                                   "vertical_direction": ("up", "down"), "waterlogged": BOOL})
B._reg(["melon_stem", "pumpkin_stem"], "age7")
B._reg(["attached_melon_stem", "attached_pumpkin_stem"], "facing4")
B.SCHEMAS.setdefault("cocoa", {"age": ("0", "1", "2"), "facing": H4})
B._reg(["cocoa"], "cocoa")
B.SCHEMAS.setdefault("small_dripleaf", {"facing": H4, "half": ("upper", "lower"), "waterlogged": BOOL})
B._reg(["small_dripleaf"], "small_dripleaf")
B.SCHEMAS.setdefault("big_dripleaf", {"facing": H4, "tilt": ("none", "unstable", "partial", "full"),
                                      "waterlogged": BOOL})
B._reg(["big_dripleaf"], "big_dripleaf")
B.SCHEMAS.setdefault("tripwire_hook", {"attached": BOOL, "facing": H4, "powered": BOOL})
B._reg(["tripwire_hook"], "tripwire_hook")
B._reg(["red_mushroom", "brown_mushroom", "crimson_roots", "warped_roots", "nether_sprouts", "crimson_fungus",
        "warped_fungus", "wither_rose", "lodestone", "dead_horn_coral_block", "dead_brain_coral_block",
        "dead_tube_coral_block", "sculk_catalyst", "powder_snow"])
B._reg(["tall_seagrass"], "double_plant")
B._reg(["sculk_vein"], "glow_lichen")

# custom blocks of the town branch (identical schemas) and the lead's waystone
_TOWN = [("terracotta_roof_tiles", "terracotta_roof_tile_stairs", "terracotta_roof_tile_slab"),
         ("cobbled_paving", "cobbled_paving_stairs", "cobbled_paving_slab"),
         ("plaza_flagstones", "plaza_flagstone_stairs", "plaza_flagstone_slab")]
for _base, _st, _sl in _TOWN:
    if f"{B.CUSTOM_NS}:{_base}" not in B.REGISTRY:
        B._family(_base, _st, _sl, None, ns=B.CUSTOM_NS)
B._reg(["leaded_glass"], "plain", B.CUSTOM_NS)
B._reg(["leaded_glass_pane"], "pane", B.CUSTOM_NS)
B._reg(["flag", "clock_face", "windmill_sails", "water_wheel"], "gimmick", B.CUSTOM_NS)
B._reg(["smoke_emitter"], "smoke_emitter", B.CUSTOM_NS)
B._reg(["fountain_spray"], "fountain_spray", B.CUSTOM_NS)
B._reg(["waystone"], "waystone", B.CUSTOM_NS)
for _k, _v in {"terracotta_roof_tile": "terracotta_roof_tiles", "terracotta_roof": "terracotta_roof_tiles",
               "plaza_flagstone": "plaza_flagstones"}.items():
    B.ALIASES.setdefault(_k, _v)
if "gimmick" not in B.BE_FAMILIES:
    B.BE_FAMILIES = tuple(B.BE_FAMILIES) + ("gimmick",)
B.canon.cache_clear()

# ------------------------------------------------------------------------------------------------ colours
C.BASE.update({
    "flag": (232, 188, 72), "clock_face": (226, 218, 196), "windmill_sails": (110, 82, 50),
    "water_wheel": (96, 70, 42), "terracotta_roof_tiles": (178, 86, 58), "cobbled_paving": (124, 121, 117),
    "plaza_flagstones": (214, 208, 196), "leaded_glass": (200, 214, 222), "leaded_glass_pane": (200, 214, 222),
    "waystone": (90, 96, 120), "rail": (120, 110, 100), "powered_rail": (150, 120, 70), "crying_obsidian": (32, 10, 60),
    "purpur_pillar": (171, 129, 171), "packed_ice": (141, 180, 250), "coarse_dirt": (119, 85, 59),
    "pointed_dripstone": (134, 107, 92), "red_mushroom": (200, 40, 40), "brown_mushroom": (150, 110, 80),
    "melon_stem": (110, 140, 60), "pumpkin_stem": (110, 140, 60), "attached_melon_stem": (110, 140, 60),
    "attached_pumpkin_stem": (110, 140, 60), "lodestone": (140, 140, 150), "wither_rose": (40, 30, 30),
    "crimson_roots": (130, 20, 40), "warped_roots": (20, 140, 130), "small_dripleaf": (90, 140, 40),
    "big_dripleaf": (90, 140, 40), "cut_copper": (191, 106, 80), "exposed_cut_copper": (154, 121, 101),
    "weathered_cut_copper": (109, 145, 107), "oxidized_cut_copper": (79, 153, 126), "sculk_vein": (20, 50, 60),
    "sculk_catalyst": (30, 60, 70), "tall_seagrass": (40, 100, 40), "end_stone": (219, 222, 158),
    "powder_snow": (248, 253, 253), "farmland": (100, 70, 45), "dried_kelp_block": (50, 60, 40),
    "mud_bricks": (137, 103, 79), "honeycomb_block": (229, 148, 29), "sea_lantern": (172, 199, 190),
    "white_wool": (234, 236, 237), "lily_pad": (40, 120, 40), "cobweb": (220, 220, 225),
})
C.PLANTS.update({"red_mushroom": (200, 40, 40), "brown_mushroom": (150, 110, 80), "wither_rose": (40, 30, 30),
                 "crimson_roots": (130, 20, 40), "warped_roots": (20, 140, 130), "melon_stem": (110, 140, 60),
                 "pumpkin_stem": (110, 140, 60), "attached_melon_stem": (110, 140, 60),
                 "attached_pumpkin_stem": (110, 140, 60), "small_dripleaf": (90, 140, 40),
                 "tall_seagrass": (40, 100, 40)})
C.EMIT.update({"sea_lantern": 15, "end_rod": 14, "crying_obsidian": 10, "amethyst_cluster": 5,
               "large_amethyst_bud": 4, "medium_amethyst_bud": 2, "magma_block": 3})
_orig_material = C.material.__wrapped__ if hasattr(C.material, "__wrapped__") else None


def _material_ext(state):
    bid, props = B.parse(state)
    name = bid.split(":", 1)[1]
    if name in ("smoke_emitter", "fountain_spray", "void"):
        return {"kind": "air", "alpha": 1.0, "box": None, "emit": 0, "name": name, "top": (0, 0, 0),
                "side": (0, 0, 0)}
    if name == "flag":
        return {"kind": "box", "alpha": 1.0, "box": (0.3, 0, 0.3, 0.7, 0.9, 0.7), "emit": 0, "name": name,
                "top": (232, 188, 72), "side": (232, 188, 72)}
    if name == "waystone":
        return {"kind": "box", "alpha": 1.0, "box": (0.12, 0, 0.12, 0.88, 1.0, 0.88), "emit": 8, "name": name,
                "top": (120, 170, 230) if props.get("half") == "upper" else (90, 96, 120),
                "side": (120, 170, 230) if props.get("half") == "upper" else (90, 96, 120)}
    if name in ("rail", "powered_rail", "detector_rail", "activator_rail"):
        return {"kind": "box", "alpha": 1.0, "box": (0, 0, 0, 1, 0.1, 1), "emit": 0, "name": name,
                "top": C.BASE.get(name, (120, 110, 100)), "side": C.BASE.get(name, (120, 110, 100))}
    if name in ("large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud", "pointed_dripstone"):
        col = C.BASE.get(name, (170, 130, 220)) if name == "pointed_dripstone" else (170, 130, 220)
        return {"kind": "box", "alpha": 1.0, "box": (0.25, 0, 0.25, 0.75, 0.6, 0.75), "emit": C.EMIT.get(name, 0),
                "name": name, "top": col, "side": col}
    if name == "farmland":
        return {"kind": "box", "alpha": 1.0, "box": (0, 0, 0, 1, 0.9375, 1), "emit": 0, "name": name,
                "top": (100, 70, 45), "side": (134, 96, 67)}
    sch = B.REGISTRY.get(bid, "plain")
    if sch in ("wall", "fence", "pane") and any(props.get(d) not in (None, "false", "none")
                                                for d in ("north", "south", "east", "west")):
        base = _base_material(state)
        out = dict(base)
        lo = {"wall": 0.25, "fence": 0.375, "pane": 0.4375}[sch]
        hi = 1.0 - lo
        conn = {d: props.get(d) not in (None, "false", "none") for d in ("north", "south", "east", "west")}
        x0 = 0.0 if conn["west"] else lo
        x1 = 1.0 if conn["east"] else hi
        z0 = 0.0 if conn["north"] else lo
        z1 = 1.0 if conn["south"] else hi
        if sch == "wall":
            h = 1.0 if (props.get("up") == "true" or "tall" in props.values()) else 0.875
            if not (conn["east"] or conn["west"]):
                x0, x1 = 0.3125, 0.6875
            if not (conn["north"] or conn["south"]):
                z0, z1 = 0.3125, 0.6875
        else:
            h = 1.0
        if (conn["east"] or conn["west"]) and (conn["north"] or conn["south"]):
            # corners/crossings: an L or + shape; draw the longer arm only (one box per block)
            if conn["east"] and conn["west"]:
                z0, z1 = (lo, hi) if sch != "wall" else (0.3125, 0.6875)
            else:
                x0, x1 = (lo, hi) if sch != "wall" else (0.3125, 0.6875)
        out["kind"], out["box"] = "box", (x0, 0.0, z0, x1, h, z1)
        return out
    if name == "cobweb":
        return {"kind": "glass", "alpha": 0.35, "box": None, "emit": 0, "name": name, "top": (220, 220, 225),
                "side": (220, 220, 225)}
    if name in ("melon_stem", "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem", "small_dripleaf",
                "big_dripleaf", "red_mushroom", "brown_mushroom", "wither_rose", "crimson_roots", "warped_roots",
                "tall_seagrass"):
        col = C.PLANTS.get(name, (90, 140, 40))
        return {"kind": "box", "alpha": 1.0, "box": (0.25, 0, 0.25, 0.75, 0.6, 0.75), "emit": 0, "name": name,
                "top": col, "side": col}
    return _base_material(state)


_base_material = C.material
C.material = _material_ext
try:  # the capital preview imported ``material`` by name
    from capital import preview as _P
    _P.material = _material_ext
except Exception:  # pragma: no cover - preview needs PIL only
    pass


# ------------------------------------------------------------------------------------------------ SNBT helpers
def flag_nbt(design="kingdom", width=3.0, height=2.0, wind=None) -> str:
    nbt = f'{{Width:{float(width):.1f}f,Height:{float(height):.1f}f,Design:"{design}"'
    if wind is not None:
        nbt += f",Wind:{float(wind):.1f}f"
    return nbt + "}"


def sails_nbt(radius=7.0) -> str:
    return f"{{Radius:{float(radius):.1f}f}}"


def wheel_nbt(radius=3.0, width=2.0) -> str:
    return f"{{Radius:{float(radius):.1f}f,Width:{float(width):.1f}f}}"


def text_component(text=None, translate=None, args=None, color=None, bold=False) -> str:
    """A JSON text component: plain ``text`` or a ``translate`` key with ``args`` (strings or components)."""
    d = {}
    if translate:
        d["translate"] = translate
        if args:
            d["with"] = [a if isinstance(a, dict) else str(a) for a in args]
    else:
        d["text"] = text or ""
    if color:
        d["color"] = color
    if bold:
        d["bold"] = True
    return json.dumps(d, ensure_ascii=False, separators=(",", ":"))


def sign_nbt(front, back=None, color="black", glowing=False) -> str:
    """1.20.1 sign data from text components (JSON strings from :func:`text_component`, or plain strings)."""
    def comp(c):
        if c is None or c == "":
            return '{"text":""}'
        if isinstance(c, str) and c.startswith("{"):
            return c
        return text_component(str(c))

    def side(lines):
        lines = list(lines or [])[:4]
        lines += [""] * (4 - len(lines))
        msgs = ",".join("'" + comp(t).replace("\\", "\\\\").replace("'", "\\'") + "'" for t in lines)
        return f"{{messages:[{msgs}],color:{B.snbt_str(color)},has_glowing_text:{'1b' if glowing else '0b'}}}"

    return f"{{front_text:{side(front)},back_text:{side(back)},is_waxed:1b}}"
