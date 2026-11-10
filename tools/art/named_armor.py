"""The 30 named armor sets of the armory: item icons (16x16, four pieces) and worn textures (64x32, two layers).

Each set is cut from one of the outfit patterns of armor_icons.py / armor_worn.py (heavy plate, light leather and
cloth, robes) in its own materials, and carries its own emblem (leaf, flame, snowflake, sun, eye, fang, bolt, drop,
mountain, wave...) on the chest of the worn texture and the chestplate icon, plus a matching gem on the helmet.
"""
from __future__ import annotations

import palette as P
import armor_icons
import armor_worn
from icon import render
from px import hexc
import named_weapons as NW

DARK = ("flat", hexc("1a1520"))
DARK_BLUE = ("flat", hexc("101828"))
DARK_GREEN = ("flat", hexc("0e1a10"))
DARK_RED = ("flat", hexc("1e0a08"))
DARK_VIOLET = ("flat", hexc("0c0614"))


def theme(A, B, C, E, G, R=None, S=None, F=None, D=DARK):
    """A main plate or cloth, B trim, C cloth/leather, D dark interior, E fur/scarf/plume, G gem, R glow, S horn or
    spike, F accent (tabard symbols, emblems)."""
    return dict(A=A, B=B, C=C, D=D, E=E, G=("gem", G), R=("glow", R or G), S=S or B, F=F or B)


# set id -> (pattern of armor_icons / armor_worn, materials, emblem)
LOOKS = {
    # 1 アルセリア王国
    "meadow_runner": ("ranger", theme(P.LEATHER, P.BRONZE, P.CLOTH_RED, P.LINEN, P.SKY, F=P.LINEN), "wing"),
    "homestead_guard": (1, theme(P.STEEL, P.BRONZE, P.LEATHER, P.CLOTH_RED, NW.AMBER, F=P.GOLD), "mountain"),
    "wildflower_vestment": ("priest", theme(P.LINEN, P.LEAF, P.LINEN, NW.ROSE, NW.ROSE, F=NW.ROSE, D=DARK_GREEN),
                            "flower"),
    # 2 エルダーフォレスト
    "moonleaf_robe": ("mage", theme(NW.MOONSILVER, P.LEAF, NW.MOONSILVER, P.MOSS, P.SKY), "moon"),
    "silkshadow": ("assassin", theme(P.LINEN, NW.MOONSILVER, P.LINEN, NW.VIOLET, P.VOID_GLOW, S=P.SILVER,
                                     D=DARK_VIOLET), "eye"),
    "heartwood_bulwark": (5, theme(NW.DARKWOOD, P.MOSS, NW.DARKWOOD, P.LEAF, NW.AMBER, S=P.WOOD, F=P.LEAF),
                          "leaf"),
    # 3 サンドリア砂漠
    "ironvein_plate": (2, theme(P.IRON_DARK, NW.COPPER, P.LEATHER_DARK, P.CLOTH_RED, NW.AMBER, R=NW.AMBER,
                                F=NW.AMBER), "mountain"),
    "ember_scholar": ("mage", theme(P.DRAGON_DARK, P.GOLD, P.DRAGON_DARK, P.EMBER, P.FLAME), "flame"),
    "dune_stalker": (3, theme(P.SAND, P.SCARAB, P.LINEN, P.SLIME, P.SCARAB, S=P.SCARAB), "fang"),
    # 4 ノルディア雪原
    "rivermist_garb": ("ranger", theme(P.LEATHER_DARK, P.SILVER, NW.SEA, P.FUR_WHITE, P.ICE, F=P.ICE), "wave"),
    "glacier_warden": ("knight", theme(P.FROST_STEEL, P.ICE, P.ICE_DEEP, P.FUR_WHITE, P.ICE, F=P.FUR_WHITE,
                                       D=DARK_BLUE), "flake"),
    "holy_spring_vestment": ("priest", theme(NW.PEARL, P.HOLY_GOLD, NW.PEARL, NW.SEA, P.SKY, F=NW.SEA), "drop_water"),
    # 5 ドラゴニア火山
    "rune_sage": ("mage", theme(P.STONE, P.BRONZE, P.STONE, P.CYAN_RUNE, P.CYAN_RUNE), "bolt"),
    "cinder_dancer": ("assassin", theme(P.DRAGON_DARK, P.GOLD, P.DRAGON_DARK, P.FLAME, P.FLAME, S=P.OBSIDIAN,
                                        D=DARK_RED), "flame"),
    "ruin_guardian_plate": (6, theme(P.SANDSTONE, P.STONE, P.SANDSTONE, NW.SUNSTONE, NW.AMBER, F=NW.AMBER),
                            "sun"),
    # 6 セレスティア天空大陸
    "thunderpeak_armor": ("spellblade", theme(P.STEEL, P.SILVER, P.SKY, P.SKY, NW.THUNDER, R=NW.THUNDER,
                                              D=DARK_BLUE), "bolt"),
    "cloudweaver": ("priest", theme(P.SKY, P.HOLY_WHITE, P.HOLY_WHITE, P.SKY, P.SKY, F=P.SKY, D=DARK_BLUE), "wing"),
    "skystrider": (3, theme(P.HOLY_WHITE, NW.THUNDER, P.SKY, P.SKY, NW.THUNDER, S=P.SILVER), "bolt"),
    # 7 終焉の塔
    "voidstep": ("assassin", theme(P.ABYSS, P.VOID, NW.INK, P.VOID_GLOW, P.VOID_GLOW, S=P.SHADOW, D=DARK_VIOLET),
                 "eye"),
    "endless_night_plate": (7, theme(NW.NIGHT, P.BLOOD, NW.NIGHT, P.BLOOD, P.BLOOD, R=P.BLOOD, S=NW.NIGHT,
                                     D=DARK_RED), "drop"),
    "oblivion_vestment": ("mage", theme(NW.INK, P.VOID, NW.INK, P.ABYSS, P.VOID_GLOW), "eye"),
    # 8 豊穣の農場
    "farmstead_bulwark": (1, theme(NW.RUST, P.WOOD, P.LEATHER, NW.STRAW, NW.AMBER, F=NW.STRAW), "wheat"),
    "scarecrow_garb": ("ranger", theme(NW.STRAW, P.LEATHER_DARK, P.LEATHER, P.CLOTH_RED, P.SLIME, F=P.CLOTH_RED),
                       "fang"),
    "harvest_rite": ("priest", theme(NW.STRAW, P.GOLD, P.LINEN, P.CLOTH_RED, P.HOLY_GOLD, F=P.CLOTH_RED), "wheat"),
    # 9 湖畔のほとり
    "lotus_vestment": ("priest", theme(NW.ROSE, NW.JADE, NW.ROSE, NW.JADE, NW.JADE, F=NW.JADE), "flower"),
    "lakescale_mail": (5, theme(NW.TEAL_STEEL, NW.PEARL, NW.SEA, NW.SEA, P.ICE, S=NW.PEARL, F=NW.PEARL), "wave"),
    "reedwalker": (3, theme(P.MOSS, NW.JADE, P.LINEN, P.SLIME, P.SLIME, S=P.WOOD), "fang"),
    # 10 風の港
    "privateer_garb": ("assassin", theme(P.LEATHER_DARK, P.GOLD, P.LEATHER_DARK, P.CLOTH_RED, P.GOLD,
                                         S=P.STEEL_DARK), "anchor"),
    "tidecaller_robe": ("mage", theme(NW.SEA, NW.CORAL, NW.SEA, NW.TEAL_STEEL, P.ICE), "wave"),
    "anchorguard_plate": (2, theme(P.IRON_DARK, NW.RUST, NW.SEA, NW.SEA, NW.SEA, F=NW.CORAL), "anchor"),
}

# 4x4 emblems: F accent, G gem, R glow, '.' keeps the pattern underneath
EMBLEMS = {
    "wing": ["F..F", "FF.F", ".FFF", "..F."],
    "mountain": ["....", ".F..", "FGF.", "FFFF"],
    "flower": [".F..", "FGF.", ".F..", "...."],
    "moon": [".GG.", "G...", "G...", ".GG."],
    "eye": ["....", ".FF.", "FGGF", ".FF."],
    "leaf": ["..F.", ".FF.", "FFF.", "F..."],
    "flame": ["..R.", ".RR.", "RRRR", ".RR."],
    "fang": ["F..F", "F..F", ".FF.", "...."],
    "wave": ["....", "F.F.", ".F.F", "...."],
    "flake": ["G.G.", ".G..", "G.G.", "...."],
    "drop_water": [".G..", "GGG.", "GGG.", ".G.."],
    "drop": [".R..", "RRR.", "RRR.", ".R.."],
    "sun": [".F.F", "FGGF", "FGGF", "F.F."],
    "bolt": ["..R.", ".RR.", "RR..", "R..."],
    "wheat": [".F.F", "F.F.", ".F..", ".F.."],
    "anchor": [".F..", "FFF.", ".F..", "FFF."],
}


def _overlay(rows, emblem, top, left):
    out = [list(r) for r in rows]
    for dy, line in enumerate(emblem):
        for dx, ch in enumerate(line):
            y, x = top + dy, left + dx
            if ch != "." and 0 <= y < len(out) and 0 <= x < len(out[y]) and out[y][x] != ".":
                out[y][x] = ch
    return ["".join(r) for r in out]


def pieces(set_id):
    return [(f"{set_id}_{slot}", noun) for slot, noun in (("head", "helmet"), ("chest", "chestplate"),
                                                         ("legs", "leggings"), ("feet", "boots"))]


def icon(set_id: str, noun: str):
    pattern, mats, emblem = LOOKS[set_id]
    rows = armor_icons.SLOTS[noun][pattern]
    if noun == "chestplate":
        rows = _overlay(rows, EMBLEMS[emblem], 4, 6)
    elif noun == "helmet":
        rows = _overlay(rows, ["GG"], 3, 7)
    if noun != "boots":
        return render(rows, mats, local="BSEGRF")
    return _boots(rows, mats)


def _boots(rows, mats):
    pad = ["." * 16] * 16
    back = [list(r) for r in pad]
    front = [list(r) for r in pad]
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                back[y + 1][x + 6] = ch
                front[y + 5][x + 1] = ch
    b = render(["".join(r) for r in back], mats, local="BSEGRF")
    f = render(["".join(r) for r in front], mats, local="BSEGRF")
    b.a[:, :, :3] = (b.a[:, :, :3].astype(int) * 0.86).astype("uint8")
    b.paste(f)
    return b


def worn(set_id: str):
    """(layer_1, layer_2) of the set: the pattern's faces in the set's materials, the emblem on the chest."""
    pattern, mats, emblem = LOOKS[set_id]
    spec = armor_worn.set_spec(pattern)
    body = dict(spec["body"])
    body["front"] = _overlay(body["front"], EMBLEMS[emblem], 2, 2)
    head = dict(spec["head"])
    if head.get("front") and head["front"][1] != "........":
        head["front"] = _overlay(head["front"], ["GG"], 1, 3)
    l1 = armor_worn.Layer(mats, f"named_l1_{set_id}")
    l2 = armor_worn.Layer(mats, f"named_l2_{set_id}")
    for face, rows in head.items():
        l1.put_face("head", face, rows)
    for face, rows in body.items():
        l1.put_face("body", face, rows)
    for face, rows in spec["arm"].items():
        l1.put_face("arm", face, rows)
    for face, rows in spec["boots"].items():
        l1.put_face("leg", face, rows)
    for face, rows in spec["waist"].items():
        l2.put_face("body", face, rows)
    for face, rows in spec["legs"].items():
        l2.put_face("leg", face, rows)
    return l1.c, l2.c


def file_names(set_id: str):
    return f"er_set_{set_id}_layer_1.png", f"er_set_{set_id}_layer_2.png"
