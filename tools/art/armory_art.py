#!/usr/bin/env python3
"""Art of the armory (named designs): weapon sprites, armor icons, worn set textures, weapon art icons, item models,
the design overrides of the tier items and tools/art/armory_preview.png.

    python3 tools/art/armory_art.py              # writes everything (deterministic)
    python3 tools/art/armory_art.py --check      # only checks that every design has its art and models

A design is drawn on the item of its base (``<tier>_<type>``): the stack's CustomModelData (the design's model number,
3000+ weapons, 4000+ armor) selects ``eternalrealm:item/named/<id>``. The tier item models keep their other overrides
(legacy tiers 2..7, job outfits 101..106); gen-equipment-models.py and generate_all.py call patch_tier_models() again
after rewriting them.
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

import armory_data  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
NS = "eternalrealm"
FAMILY = {"longsword": "longsword", "greatsword": "greatsword", "twin_blades": "twin_blades", "spear": "spear",
          "staff": "staff", "bow": "longbow"}
ICON_CONTEXTS = ["gui", "ground", "fixed"]

# weapon art icons: kind, object, effects, badge, theme, flags (tools/art/skill_tree.py composer, weapon frame)
ART_ICONS = {
    "art_shadow_legion": ("u", "daggers", "x+speed", "bleed", "SHADOW", "ghost"),
    "art_thornroot_bloom": ("u", "flower", "zone", "slow", "LIFE", ""),
    "art_sand_maelstrom": ("u", "spear", "spiral", "pull", "EARTH", ""),
    "art_doom_guillotine": ("u", "greatsword", "smite", "freeze", "FROST", ""),
    "art_frozen_prison": ("u", "crystal", "zone+hail", "freeze", "FROST", ""),
    "art_meteor_slash": ("u", "meteor", "arc", "burn", "FIRE", ""),
    "art_heavens_chain": ("u", "arrows3fan", "beam", "stun", "THUNDER", ""),
    "art_gate_of_end": ("u", "portal", "spiral", "weaken", "SHADOW", ""),
    "art_black_sword_rain": ("u", "knives_rain", "-", "vuln", "BLOOD", ""),
}


def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
        f.write("\n")


def save_png(canvas, path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(canvas.a, "RGBA").save(path, optimize=True)


def _display(family: str):
    import models
    return models.WEAPON_ITEMS[family][2]


def _model(parent, texture, display=None):
    d = {"parent": parent, "textures": {"layer0": texture}}
    if display:
        d["display"] = display
    return d


def weapon_models(assets: str, weapon) -> None:
    family = FAMILY[weapon.type]
    display = _display(family)
    base = os.path.join(assets, "models", "item", "named")
    tex = f"{NS}:item/named/{weapon.id}"
    if weapon.type == "twin_blades":
        write_json(os.path.join(base, f"{weapon.id}_held.json"),
                   _model("minecraft:item/handheld", tex + "_held", display))
        write_json(os.path.join(base, f"{weapon.id}_icon.json"), _model("minecraft:item/handheld", tex, display))
        write_json(os.path.join(base, f"{weapon.id}.json"), {
            "loader": "forge:separate_transforms",
            "gui_light": "front",
            "textures": {"particle": tex},
            "base": {"parent": f"{NS}:item/named/{weapon.id}_held"},
            "perspectives": {ctx: {"parent": f"{NS}:item/named/{weapon.id}_icon"} for ctx in ICON_CONTEXTS},
        })
    else:
        write_json(os.path.join(base, f"{weapon.id}.json"), _model("minecraft:item/handheld", tex, display))


def armor_models(assets: str, piece_id: str) -> None:
    write_json(os.path.join(assets, "models", "item", "named", f"{piece_id}.json"),
               _model("minecraft:item/generated", f"{NS}:item/named/{piece_id}"))


def design_overrides():
    """item path of the base -> [(model number, model id)] of every design made on it (the relics, the catalogue
    trinkets and the named trinkets included)."""
    weapons, sets = armory_data.load()
    by_item: dict[str, list] = {}
    for w in weapons:
        by_item.setdefault(w.item, []).append((w.model, f"{NS}:item/named/{w.id}"))
    for s in sets:
        tier = armory_data.TIER_IDS[s.tier - 1]
        for piece_id, noun, model in s.pieces():
            by_item.setdefault(f"{tier}_{noun}", []).append((model, f"{NS}:item/named/{piece_id}"))
    import relics_data
    import relic_art
    for item, entries in relic_art.overrides(relics_data.load()).items():
        by_item.setdefault(item, []).extend(entries)
    import trinkets
    for item, entries in trinkets.overrides().items():
        by_item.setdefault(item, []).extend(entries)
    import named_trinkets
    for item, entries in named_trinkets.overrides().items():
        by_item.setdefault(item, []).extend(entries)
    return by_item


def patch_tier_models(assets: str = ASSETS) -> int:
    """Adds the design overrides to the tier item models (idempotent; other overrides are kept, all sorted)."""
    patched = 0
    for item, entries in sorted(design_overrides().items()):
        path = os.path.join(assets, "models", "item", f"{item}.json")
        with open(path, encoding="utf-8") as f:
            model = json.load(f)
        keep = [o for o in model.get("overrides", [])
                if o.get("predicate", {}).get("custom_model_data", 0) < 3000]
        ours = [{"predicate": {"custom_model_data": n}, "model": m} for n, m in sorted(entries)]
        model["overrides"] = sorted(keep, key=lambda o: o["predicate"]["custom_model_data"]) + ours
        write_json(path, model)
        patched += 1
    return patched


def art_icon(art_id: str):
    import skill_tree
    kind, obj, fx, badge, theme, flags = ART_ICONS[art_id]
    return skill_tree.compose(art_id, "weapon", kind, obj, fx, badge, theme, flags)


def generate(assets: str = ASSETS, preview: bool = True) -> list[str]:
    import named_armor
    import named_weapons
    from named_weapon_specs import SPECS
    weapons, sets = armory_data.load()
    written = []
    tex = os.path.join(assets, "textures", "item", "named")
    weapon_imgs = {}
    for w in weapons:
        spec = SPECS[w.id]
        canvas = named_weapons.render(spec)
        save_png(canvas, os.path.join(tex, f"{w.id}.png"))
        weapon_imgs[w.id] = canvas
        if w.type == "twin_blades":
            save_png(named_weapons.render_held(spec), os.path.join(tex, f"{w.id}_held.png"))
        weapon_models(assets, w)
        written.append(w.id)
    armor_imgs = {}
    worn = {}
    for s in sets:
        for piece_id, noun, _ in s.pieces():
            canvas = named_armor.icon(s.id, noun)
            save_png(canvas, os.path.join(tex, f"{piece_id}.png"))
            armor_models(assets, piece_id)
            armor_imgs[piece_id] = canvas
            written.append(piece_id)
        l1, l2 = named_armor.worn(s.id)
        n1, n2 = named_armor.file_names(s.id)
        save_png(l1, os.path.join(assets, "textures", "models", "armor", n1))
        save_png(l2, os.path.join(assets, "textures", "models", "armor", n2))
        worn[s.id] = (l1, l2)
    arts = {}
    for art_id in ART_ICONS:
        arts[art_id] = art_icon(art_id)
        save_png(arts[art_id], os.path.join(assets, "textures", "gui", "skill", f"{art_id}.png"))
    patched = patch_tier_models(assets)
    print(f"armory art: {len(weapons)} weapons, {len(sets) * 4} armor pieces, {len(sets)} worn sets, "
          f"{len(ART_ICONS)} art icons, {patched} tier models patched")
    if preview:
        write_preview(weapons, sets, weapon_imgs, armor_imgs, worn, arts)
    return written


def write_preview(weapons, sets, weapon_imgs, armor_imgs, worn, arts) -> None:
    import armor_worn
    bg = (36, 36, 44, 255)
    scale = 3
    cell = 32 * scale + 6
    label_w = 26
    w_cols = 18
    width = label_w + w_cols * cell + 12
    rows_h = 11 * cell
    armor_cell = 16 * 4 + 4
    doll_w = 46 * 3
    set_w = 4 * armor_cell + doll_w + 18
    set_h = 32 * 3 + 8
    armor_h = 10 * set_h
    art_h = 16 * 6 + 24
    height = 24 + rows_h + 24 + armor_h + 24 + art_h + 12
    width = max(width, 12 + 3 * set_w)
    img = Image.new("RGBA", (width, height), bg)
    d = ImageDraw.Draw(img)
    d.text((8, 6), "Named weapons (x3): rows = fields 1..10 and the boss weapons; columns = longsword, greatsword, "
           "twin blades, spear, staff, bow (three each, lowest level first)", fill=(230, 220, 190, 255))
    y0 = 24
    boss_col = 0
    for w in weapons:
        if w.art:
            r, c = 10, boss_col
            boss_col += 1
        else:
            r = w.field - 1
            c = armory_data.TYPE_ORDER.index(w.type) * 3 + (w.model - 3000) % 3
        im = Image.fromarray(weapon_imgs[w.id].a, "RGBA").resize((32 * scale, 32 * scale), Image.NEAREST)
        img.alpha_composite(im, (label_w + c * cell, y0 + r * cell))
    for r in range(11):
        d.text((6, y0 + r * cell + cell // 2 - 6), "B" if r == 10 else str(r + 1), fill=(200, 200, 210, 255))
    y1 = y0 + rows_h + 4
    d.text((8, y1), "Armor sets: icons (x4) and worn (front, back, side x3); three per field", fill=(230, 220, 190, 255))
    y1 += 20
    for s in sets:
        r = s.field - 1
        c = [x.id for x in sets if x.field == s.field].index(s.id)
        x0 = 12 + c * set_w
        yy = y1 + r * set_h
        for k, (piece_id, _, _) in enumerate(s.pieces()):
            im = Image.fromarray(armor_imgs[piece_id].a, "RGBA").resize((64, 64), Image.NEAREST)
            img.alpha_composite(im, (x0 + k * armor_cell, yy + 8))
        l1, l2 = worn[s.id]
        doll = Image.new("RGBA", (46, 32), (0, 0, 0, 0))
        doll.alpha_composite(armor_worn.paperdoll(l1, l2, "front").image(), (0, 0))
        doll.alpha_composite(armor_worn.paperdoll(l1, l2, "back").image(), (18, 0))
        doll.alpha_composite(armor_worn.side_doll(l1, l2).image(), (36, 0))
        img.alpha_composite(doll.resize((46 * 3, 32 * 3), Image.NEAREST), (x0 + 4 * armor_cell + 6, yy))
    y2 = y1 + armor_h + 4
    d.text((8, y2), "Weapon art icons (x6)", fill=(230, 220, 190, 255))
    for i, art_id in enumerate(ART_ICONS):
        im = Image.fromarray(arts[art_id].a, "RGBA").resize((96, 96), Image.NEAREST)
        img.alpha_composite(im, (12 + i * 104, y2 + 20))
    out = os.path.join(HERE, "armory_preview.png")
    img.save(out, optimize=True)
    print(f"preview: {os.path.relpath(out, ROOT)} {img.size}")


def check(assets: str = ASSETS) -> list[str]:
    problems = []
    weapons, sets = armory_data.load()
    for w in weapons:
        for p in [f"textures/item/named/{w.id}.png", f"models/item/named/{w.id}.json"]:
            if not os.path.isfile(os.path.join(assets, p)):
                problems.append(p)
    for s in sets:
        for piece_id, _, _ in s.pieces():
            if not os.path.isfile(os.path.join(assets, f"textures/item/named/{piece_id}.png")):
                problems.append(piece_id)
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    if args.check:
        problems = check()
        for p in problems:
            print("missing", p)
        return 1 if problems else 0
    generate(preview=not args.no_preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
