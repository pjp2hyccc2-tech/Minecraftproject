#!/usr/bin/env python3
"""Item models of the tiered equipment items (item module: <tier>_<type>).

Weapons point at the 32x32 family art of tools/art (models/item/weapon/<family>_<n>.json); armor pieces use the
16x16 tier icon and switch to the job outfit icon via custom_model_data 101..106 (knight, assassin, mage, ranger,
priest, spellblade), which the server sets when a player wears the piece in job-outfit style.
The four Phase 2 iron weapons and the art-generated models are left alone. Run from the repository root.
"""
import json
import pathlib

ROOT = pathlib.Path("src/main/resources/assets/eternalrealm/models/item")
TIERS = ["iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"]
WEAPONS = {"longsword": "longsword", "greatsword": "greatsword", "twin_blades": "twin_blades", "spear": "spear",
           "staff": "staff", "bow": "longbow"}
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
JOBS = ["knight", "assassin", "mage", "ranger", "priest", "spellblade"]
KEEP = {"iron_longsword", "iron_greatsword", "iron_twin_blades", "iron_spear"}


def write(name, model):
    path = ROOT / f"{name}.json"
    path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main():
    count = 0
    for number, tier in enumerate(TIERS, start=1):
        for item_type, family in WEAPONS.items():
            name = f"{tier}_{item_type}"
            if name in KEEP:
                continue
            write(name, {"parent": f"eternalrealm:item/weapon/{family}_{number}"})
            count += 1
        for slot in ARMOR:
            write(f"{tier}_{slot}", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"eternalrealm:item/armor/{slot}_{number}"},
                "overrides": [{"predicate": {"custom_model_data": 101 + index},
                               "model": f"eternalrealm:item/armor/{slot}_job_{job}"}
                              for index, job in enumerate(JOBS)],
            })
            count += 1
    print(f"{count} equipment models")


if __name__ == "__main__":
    main()
