#!/usr/bin/env python3
"""装身具の工房 (the smithy's trinket catalogue, com.eternalrealm.item.Trinkets): icons, item models, names and lore of
the 156 trinkets (13 grades × 6 focuses × an accessory and a charm), and tools/art/trinkets_preview.png.

    python3 tools/art/trinkets.py            # textures, models, docs/i18n/trinkets.json, preview, base item overrides
    python3 tools/art/trinkets.py --check    # only checks that every trinket has its art

The shapes are trinket_icons.py's: the grade picks the metal and the cloth (iron and linen for the novice up to gold
and void light for the eternal grade, which also sparkle from the starlit grade on), the focus picks the gem and, with
the slot, the kind (STR bracelet and ward stone, VIT amulet and talisman, AGI earring and ward bell, INT ring and
talisman, DEX earring and ward bell, LUK ring and charm pouch). The trinkets sit on the trinket base items
(<tier>_accessory, <tier>_charm; the grades past the seventh on abyss) at CustomModelData 5200 + grade × 12 +
focus × 2 + (charm ? 1 : 0); armory_art.patch_tier_models writes the overrides.

Keep GRADES and FOCUSES in step with Trinkets.java: TrinketCatalogTest checks every Java trinket's art, models,
overrides and names.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import palette as P  # noqa: E402
import trinket_icons  # noqa: E402
from icon import render  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/eternalrealm")
I18N = os.path.join(ROOT, "docs/i18n/trinkets.json")
NS = "eternalrealm"
MODELS = 5200
TIER_IDS = ["iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"]

# id, level, metal, cloth, sparkle, ja name, en name, ja lore, en lore
GRADES = [
    ("novice", 1, P.IRON, P.LINEN, False, "見習い", "Novice",
     "鍛冶屋の徒弟が最初に打つ、素朴な装身具。", "The plain trinket every smith's apprentice forges first."),
    ("wayfarer", 8, P.BRONZE, P.LEATHER, False, "旅人", "Wayfarer",
     "街道を行く者のお守りとして、昔から好まれてきた品。", "A long-time favourite of those who walk the high roads."),
    ("warrior", 15, P.STEEL, P.CLOTH_RED, False, "戦士", "Warrior",
     "鍛え抜いた鋼に、戦う者の祈りを込めた装身具。", "Tempered steel, worked with a fighter's prayer."),
    ("veteran", 22, P.SILVER, P.LEATHER_DARK, False, "古強者", "Veteran",
     "幾多の戦場を越えた者だけが、その重みを知る。", "Only those who have outlived many battles feel its weight."),
    ("valiant", 30, P.GOLD_PALE, P.SAND, False, "勇士", "Valiant",
     "勇士の証として、王都の工房で丁寧に仕上げられた逸品。", "Finished with care in the capital's workshops, a mark of the valiant."),
    ("elite", 37, P.GOLD, P.CLOTH_RED, False, "精鋭", "Elite",
     "選ばれた者のために、熟練の職人が一つずつ打ち上げる。", "Master smiths forge each one by hand for the chosen few."),
    ("heroic", 44, P.HOLY_GOLD, P.HOLY_WHITE, False, "英雄", "Heroic",
     "英雄の名とともに語り継がれる、輝く装身具。", "A shining trinket whose tale is told with its bearer's name."),
    ("champion", 50, P.DRAGON_DARK, P.OBSIDIAN, False, "覇者", "Champion",
     "深淵の縁で鍛えられ、覇者の手にだけ馴染むという。", "Forged at the abyss's rim, it is said to settle only in a champion's hand."),
    ("starlit", 60, P.SKY, P.ICE_DEEP, True, "星詠み", "Starlit",
     "星の巡りを読む者が、夜空の欠片を封じて仕立てた品。", "A reader of the stars sealed a shard of the night sky within."),
    ("celestial", 70, P.HOLY_WHITE, P.SKY, True, "天穹", "Celestial",
     "天穹の果てから降りた光を、白銀の枠に留めている。", "White silver holds light that fell from the edge of the sky."),
    ("divine", 80, P.FROST_STEEL, P.HOLY_GOLD, True, "神域", "Divine",
     "神域に触れた者だけが打てる、澄んだ響きの装身具。", "Its clear ring can only be forged by one who has touched the divine."),
    ("aeon", 90, P.VOID, P.ABYSS, True, "永劫", "Aeon",
     "永劫の時を経ても曇らぬ、紫の輝きを宿す。", "Its violet gleam has not dimmed through an aeon of ages."),
    ("eternal", 100, P.HOLY_GOLD, P.VOID_GLOW, True, "永遠", "Eternal",
     "この世界の果てで鍛えられた、永遠の名を冠する装身具。", "Forged at the end of this world, it bears the name of eternity."),
]

# id, accessory kind, charm kind, gem, ja focus, en focus
FOCUSES = [
    ("str", "BRACELET", "STONE", P.DRAGON, "剛力", "Might"),
    ("vit", "AMULET", "TALISMAN", P.HEAL_GREEN, "堅守", "Fortitude"),
    ("agi", "EARRING", "BELL", P.SKY, "疾風", "the Gale"),
    ("int", "RING", "TALISMAN", P.POTION_VIOLET, "叡智", "Wisdom"),
    ("dex", "EARRING", "BELL", P.FLAME, "鷹眼", "the Hawk"),
    ("luk", "RING", "POUCH", P.TURQUOISE, "幸運", "Fortune"),
]

KIND_NOUNS = {
    "RING": ("指輪", "Ring"), "AMULET": ("首飾り", "Amulet"), "EARRING": ("耳飾り", "Earring"),
    "BRACELET": ("腕輪", "Bracelet"), "TALISMAN": ("護符", "Talisman"), "BELL": ("守り鈴", "Ward Bell"),
    "STONE": ("守り石", "Ward Stone"), "POUCH": ("お守り袋", "Charm Pouch"),
}

STAT_LORE = {
    "str": ("身に着けた者の膂力を", "the wearer's might"),
    "vit": ("身に着けた者の体力を", "the wearer's vitality"),
    "agi": ("身に着けた者の素早さを", "the wearer's swiftness"),
    "int": ("身に着けた者の知力を", "the wearer's mind"),
    "dex": ("身に着けた者の器用さを", "the wearer's aim"),
    "luk": ("身に着けた者の運を", "the wearer's fortune"),
}


def trinkets():
    """Every trinket as a dict, in Trinkets.all() order (grade, focus, accessory before charm)."""
    out = []
    for g, grade in enumerate(GRADES):
        gid, level, metal, cloth, sparkle, ja_grade, en_grade, ja_lore, en_lore = grade
        tier = _tier_of(level)
        for f, focus in enumerate(FOCUSES):
            fid, acc_kind, charm_kind, gem, ja_focus, en_focus = focus
            for charm in (False, True):
                kind = charm_kind if charm else acc_kind
                tid = f"trinket_{gid}_{fid}_{kind.lower()}"
                ja_noun, en_noun = KIND_NOUNS[kind]
                ja_stat, en_stat = STAT_LORE[fid]
                out.append({
                    "id": tid, "grade": g, "focus": f, "charm": charm, "kind": kind,
                    "model": MODELS + g * 12 + f * 2 + (1 if charm else 0),
                    "item": f"{TIER_IDS[tier - 1]}_{'charm' if charm else 'accessory'}",
                    "metal": metal, "cloth": cloth, "gem": gem, "sparkle": sparkle,
                    "ja": f"{ja_grade}の{ja_focus}の{ja_noun}",
                    "en": f"{en_grade} {en_noun} of {en_focus}",
                    "ja_lore": f"{ja_lore}{ja_stat}底上げする。",
                    "en_lore": f"{en_lore} It raises {en_stat}.",
                })
    return out


def _tier_of(level: int) -> int:
    tier = 1
    for i, lv in enumerate([1, 8, 15, 22, 30, 37, 44]):
        if level >= lv:
            tier = i + 1
    return tier


# the catalogue's bracelet wears its focus gem on top (the relics keep trinket_icons' slim band)
BRACELET = [
    "................",
    "................",
    ".......GG.......",
    "......GGGG......",
    "....AAGGGGAA....",
    "...AAAaGGaAAA...",
    "..AA........AA..",
    ".AA..........AA.",
    ".A............A.",
    ".AA..........AA.",
    "..AA........AA..",
    "...aAGaaaaGAa...",
    ".....aaaaaa.....",
    "................",
    "................",
    "................",
]


def icon(t):
    kind, metal, gem, cloth = t["kind"], t["metal"], t["gem"], t["cloth"]
    rows = BRACELET if kind == "BRACELET" else trinket_icons.PATTERNS[kind]
    mats = {"A": metal, "G": ("gem", gem), "R": ("glow", gem), "C": cloth, "S": P.STRING if kind != "AMULET" else metal}
    if kind == "TALISMAN":
        mats["F"] = cloth  # the ward paper in the grade's colour, its seal glowing in the focus's
    elif kind == "POUCH":
        mats["F"] = metal
    if kind == "STONE":
        mats["A"] = cloth
    canvas = render(rows, mats, local="AGRF")
    if t["sparkle"]:
        # the high grades glint: a few light pixels around the piece in the gem's brightest tones
        light = t["gem"].c[5]
        mid = t["gem"].c[4]
        for x, y, c in ((1, 1, light), (2, 2, mid), (14, 3, light), (13, 13, mid), (1, 12, light)):
            if canvas.a[y, x, 3] == 0:
                canvas.a[y, x] = (c[0], c[1], c[2], 255)
    return canvas


def overrides():
    """item path -> [(model number, model id)] of the catalogue trinkets (for armory_art.design_overrides)."""
    out = {}
    for t in trinkets():
        out.setdefault(t["item"], []).append((t["model"], f"{NS}:item/trinket/{t['id']}"))
    return out


def i18n():
    data = {}
    for t in trinkets():
        data[f"design.{NS}.{t['id']}"] = {"ja": t["ja"], "en": t["en"]}
        data[f"design.{NS}.{t['id']}.lore"] = {"ja": t["ja_lore"], "en": t["en_lore"]}
    for gid, level, _m, _c, _s, ja_grade, en_grade, _jl, _el in GRADES:
        data[f"smith.{NS}.trinket_grade.{gid}"] = {"ja": ja_grade, "en": en_grade}
    return data


def generate(assets=ASSETS):
    import armory_art
    tiles = {}
    for t in trinkets():
        img = icon(t)
        armory_art.save_png(img, os.path.join(assets, "textures", "item", "trinket", f"{t['id']}.png"))
        armory_art.write_json(os.path.join(assets, "models", "item", "trinket", f"{t['id']}.json"),
                              {"parent": "minecraft:item/generated",
                               "textures": {"layer0": f"{NS}:item/trinket/{t['id']}"}})
        tiles[t["id"]] = img
    with open(I18N, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(i18n(), ensure_ascii=False, indent=2))
        f.write("\n")
    patched = armory_art.patch_tier_models(assets)
    print(f"trinkets: {len(tiles)} icons and models, {len(i18n())} texts; {patched} item models patched")
    preview(tiles)


def preview(tiles):
    bg = (30, 26, 34, 255)
    cell = 16 * 3
    left = 120
    width = left + 12 * (cell + 6) + 10
    height = 28 + len(GRADES) * (cell + 8) + 10
    img = Image.new("RGBA", (width, height), bg)
    d = ImageDraw.Draw(img)
    d.text((8, 6), "Trinket catalogue: 13 grades x (STR, VIT, AGI, INT, DEX, LUK) x (accessory, charm)",
           fill=(232, 198, 106, 255))
    by_grade = {}
    for t in trinkets():
        by_grade.setdefault(t["grade"], []).append(t)
    for g, (gid, level, *_rest) in enumerate(GRADES):
        y0 = 26 + g * (cell + 8)
        d.text((8, y0 + cell // 2 - 6), f"{gid} Lv{level}", fill=(230, 220, 190, 255))
        for i, t in enumerate(by_grade[g]):
            x = left + i * (cell + 6) + (4 if i % 2 else 0)
            im = Image.fromarray(tiles[t["id"]].a, "RGBA").resize((cell, cell), Image.NEAREST)
            d.rectangle((x - 2, y0 - 2, x + cell + 1, y0 + cell + 1),
                        outline=(120, 200, 220, 255) if t["charm"] else (232, 198, 106, 255))
            img.alpha_composite(im, (x, y0))
    out = os.path.join(HERE, "trinkets_preview.png")
    img.save(out, optimize=True)
    print(f"preview: {os.path.relpath(out, ROOT)} {img.size}")


def check(assets=ASSETS):
    missing = []
    for t in trinkets():
        for path in (f"textures/item/trinket/{t['id']}.png", f"models/item/trinket/{t['id']}.json"):
            if not os.path.isfile(os.path.join(assets, path)):
                missing.append(path)
    return missing


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        missing = check()
        if missing:
            print("missing:", *missing[:20], sep="\n  ")
            sys.exit(1)
        print(f"trinkets: all {len(trinkets())} have their art")
        return
    generate()


if __name__ == "__main__":
    main()
