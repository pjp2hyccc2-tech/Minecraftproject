"""The armory catalogue as the art tools see it, read from the Java source (single source of truth).

Parses the rows of com.eternalrealm.item.Armory (w(...), boss(...), set(...)) and derives what the art needs: the
model numbers (same formulas as the Java code; ArmoryResourcesTest checks every model file against the catalogue),
the tier of the base item each design is made on, its feel and element.
"""
from __future__ import annotations

import os
import re
from dataclasses import dataclass

HERE = os.path.dirname(os.path.abspath(__file__))
ARMORY_JAVA = os.path.join(HERE, "..", "..", "src", "main", "java", "com", "eternalrealm", "item", "Armory.java")

LEVELS = [(5, 10, 15), (10, 15, 20), (15, 20, 25), (20, 25, 30), (30, 35, 40),
          (40, 45, 50), (50, 50, 50), (10, 15, 20), (15, 20, 25), (20, 25, 30)]
TIER_LEVELS = [1, 8, 15, 22, 30, 37, 44]
TIER_IDS = ["iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"]
TYPES = {"SWORD": "longsword", "GREAT": "greatsword", "TWIN": "twin_blades", "SPEAR": "spear", "STAFF": "staff",
         "BOW": "bow"}
TYPE_ORDER = ["longsword", "greatsword", "twin_blades", "spear", "staff", "bow"]
SLOTS = [("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots")]


def tier_of(level: int) -> int:
    best = 1
    for i, lv in enumerate(TIER_LEVELS, start=1):
        if lv <= level:
            best = i
    return best


@dataclass(frozen=True)
class Weapon:
    id: str
    type: str          # longsword, greatsword, twin_blades, spear, staff, bow
    field: int
    level: int
    sources: str       # F, C, CF, FM, D, DM, LORD, CAL, BOSS
    origin: str
    feel: str          # BALANCED, SWIFT, HEAVY, KEEN, LONG, ELEMENTAL
    element: str       # NONE, FIRE, FROST, THUNDER, VENOM, HOLY, SHADOW, BLOOD, EARTH
    model: int
    art: str | None = None

    @property
    def tier(self) -> int:
        return tier_of(self.level)

    @property
    def item(self) -> str:
        return f"{TIER_IDS[self.tier - 1]}_{self.type}"


@dataclass(frozen=True)
class ArmorSet:
    id: str
    style: str         # HEAVY, LIGHT, ROBE
    field: int
    level: int
    sources: str
    origin: str
    theme: str
    model: int

    @property
    def tier(self) -> int:
        return tier_of(self.level)

    def pieces(self):
        """(piece id, slot noun of the base item, model) head to feet."""
        return [(f"{self.id}_{slot}", noun, self.model + i) for i, (slot, noun) in enumerate(SLOTS)]


_W = re.compile(r'w\(out, "([a-z0-9_]+)", (SWORD|GREAT|TWIN|SPEAR|STAFF|BOW), (\d+), (\d), (\w+), "([a-z0-9_]*)", '
                r'Feel\.(\w+), Element\.(\w+)\);')
_B = re.compile(r'boss\(out, (\d+), "([a-z0-9_]+)", (SWORD|GREAT|TWIN|SPEAR|STAFF|BOW), (\d+), (\d+), "([a-z0-9_]+)", '
                r'Feel\.(\w+), Element\.(\w+),\s+.*?WeaponArts\.(\w+),', re.S)
_S = re.compile(r'set\(out, "([a-z0-9_]+)", ArmorStyle\.(\w+), (\d+), (\d), (.*?),\s*"([a-z0-9_]*)",\s*'
                r'Element\.(\w+)\);', re.S)
ART_IDS = {"SHADOW_LEGION": "art_shadow_legion", "THORNROOT_BLOOM": "art_thornroot_bloom",
           "SAND_MAELSTROM": "art_sand_maelstrom", "DOOM_GUILLOTINE": "art_doom_guillotine",
           "FROZEN_PRISON": "art_frozen_prison", "METEOR_SLASH": "art_meteor_slash",
           "HEAVENS_CHAIN": "art_heavens_chain", "GATE_OF_END": "art_gate_of_end",
           "BLACK_SWORD_RAIN": "art_black_sword_rain"}


def _source(text: str) -> str:
    text = text.strip()
    if text.startswith("EnumSet.of"):
        return "".join(part.split(".")[-1][0] for part in re.findall(r"DesignSource\.\w+", text))
    return text


def load():
    with open(ARMORY_JAVA, encoding="utf-8") as f:
        src = f.read()
    weapons = []
    for m in _W.finditer(src):
        wid, typ, field, step, sources, origin, feel, element = m.groups()
        field, step = int(field), int(step)
        type_id = TYPES[typ]
        model = 3000 + (field - 1) * 18 + TYPE_ORDER.index(type_id) * 3 + step
        weapons.append(Weapon(wid, type_id, field, LEVELS[field - 1][step], sources, origin, feel, element, model))
    for m in _B.finditer(src):
        index, wid, typ, field, level, origin, feel, element, art = m.groups()
        weapons.append(Weapon(wid, TYPES[typ], int(field), int(level), "BOSS", origin, feel, element,
                              3180 + int(index), ART_IDS[art]))
    sets = []
    for i, m in enumerate(_S.finditer(src)):
        sid, style, field, step, sources, origin, theme = m.groups()
        field, step = int(field), int(step)
        sets.append(ArmorSet(sid, style, field, LEVELS[field - 1][step], _source(sources), origin, theme,
                             4000 + i * 4))
    if len(weapons) != 189 or len(sets) != 30:
        raise SystemExit(f"armory_data: parsed {len(weapons)} weapons and {len(sets)} sets from Armory.java "
                         "(expected 189 and 30) - update the patterns")
    return weapons, sets


if __name__ == "__main__":
    w, s = load()
    print(len(w), "weapons,", len(s), "sets")
    for x in w[:3] + w[-2:]:
        print(x)
    print(s[0], s[0].pieces())
