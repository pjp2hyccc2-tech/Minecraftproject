"""Item model JSON writers (deterministic, 2-space indented, LF)."""
from __future__ import annotations

import copy
import json
import os

NS = "eternalrealm"

# display transforms copied from the hand-written models that existed before the art pass
_HEAVY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 6.0, 0.5], "scale": [1.15, 1.15, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 6.0, 0.5], "scale": [1.15, 1.15, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
}
_POLE = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 6.5, 0.5], "scale": [1.25, 1.25, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 6.5, 0.5], "scale": [1.25, 1.25, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.5, 1.13], "scale": [0.9, 0.9, 0.9]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 4.5, 1.13], "scale": [0.9, 0.9, 0.9]},
}

WEAPON_ITEMS = {
    # family: (item id, parent, display or None)
    "longsword": ("iron_longsword", "minecraft:item/handheld", None),
    "greatsword": ("iron_greatsword", "minecraft:item/handheld", _HEAVY),
    "twin_blades": ("iron_twin_blades", "minecraft:item/handheld", None),
    "spear": ("iron_spear", "minecraft:item/handheld", _POLE),
    "staff": ("staff", "minecraft:item/handheld", _POLE),
    "longbow": ("longbow", "minecraft:item/handheld", None),
}

ARMOR_SLOTS = ["helmet", "chestplate", "leggings", "boots"]
JOBS = ["knight", "assassin", "mage", "ranger", "priest", "spellblade"]


def write_json(path: str, data: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def _model(parent: str, texture: str, display=None, overrides=None) -> dict:
    d = {"parent": parent, "textures": {"layer0": texture}}
    if display:
        d["display"] = copy.deepcopy(display)
    if overrides:
        d["overrides"] = overrides
    return d


# Families drawn as a pair in the icon but held one per hand: the icon (inventory, ground, item frame) shows the pair,
# the hands show the single "<family>_<n>_held" blade (the client draws the second one in the off hand).
PAIRED = {"twin_blades"}
ICON_CONTEXTS = ["gui", "ground", "fixed"]


def weapon_models(assets: str, tiers: int = 7) -> list[str]:
    written = []
    for family, (item, parent, display) in WEAPON_ITEMS.items():
        overrides = [{"predicate": {"custom_model_data": n}, "model": f"{NS}:item/weapon/{family}_{n}"}
                     for n in range(2, tiers + 1)]
        p = os.path.join(assets, "models", "item", f"{item}.json")
        if family in PAIRED:
            write_json(p, {"parent": f"{NS}:item/weapon/{family}_1", "overrides": overrides})
        else:
            write_json(p, _model(parent, f"{NS}:item/{item}", display, overrides))
        written.append(p)
        for n in range(1, tiers + 1):
            p = os.path.join(assets, "models", "item", "weapon", f"{family}_{n}.json")
            if family in PAIRED:
                written += _paired_models(assets, family, n, parent, display)
                write_json(p, {
                    "loader": "forge:separate_transforms",
                    "gui_light": "front",
                    "textures": {"particle": f"{NS}:item/weapon/{family}_{n}"},
                    "base": {"parent": f"{NS}:item/weapon/{family}_{n}_held"},
                    "perspectives": {ctx: {"parent": f"{NS}:item/weapon/{family}_{n}_icon"} for ctx in ICON_CONTEXTS},
                })
            else:
                write_json(p, _model(parent, f"{NS}:item/weapon/{family}_{n}", display))
            written.append(p)
    return written


def _paired_models(assets: str, family: str, n: int, parent: str, display) -> list[str]:
    out = []
    for suffix, tex in (("held", f"{family}_{n}_held"), ("icon", f"{family}_{n}")):
        p = os.path.join(assets, "models", "item", "weapon", f"{family}_{n}_{suffix}.json")
        write_json(p, _model(parent, f"{NS}:item/weapon/{tex}", display))
        out.append(p)
    return out


def armor_models(assets: str, tiers: int = 7) -> list[str]:
    written = []
    for slot in ARMOR_SLOTS:
        overrides = [{"predicate": {"custom_model_data": n}, "model": f"{NS}:item/armor/{slot}_{n}"}
                     for n in range(2, tiers + 1)]
        overrides += [{"predicate": {"custom_model_data": 101 + i}, "model": f"{NS}:item/armor/{slot}_job_{job}"}
                      for i, job in enumerate(JOBS)]
        p = os.path.join(assets, "models", "item", f"armor_{slot}.json")
        write_json(p, _model("minecraft:item/generated", f"{NS}:item/armor/{slot}_1", None, overrides))
        written.append(p)
        variants = [f"{slot}_{n}" for n in range(1, tiers + 1)] + [f"{slot}_job_{job}" for job in JOBS]
        for v in variants:
            p = os.path.join(assets, "models", "item", "armor", f"{v}.json")
            write_json(p, _model("minecraft:item/generated", f"{NS}:item/armor/{v}"))
            written.append(p)
    return written


def generated_item_models(assets: str, ids_to_textures: dict[str, str]) -> list[str]:
    written = []
    for item_id, tex in ids_to_textures.items():
        p = os.path.join(assets, "models", "item", f"{item_id}.json")
        write_json(p, _model("minecraft:item/generated", f"{NS}:{tex}"))
        written.append(p)
    return written


def particle_jsons(assets: str, frames: dict[str, int]) -> list[str]:
    written = []
    for name, n in frames.items():
        p = os.path.join(assets, "particles", f"{name}.json")
        write_json(p, {"textures": [f"{NS}:{name}_{i}" for i in range(n)]})
        written.append(p)
    return written
