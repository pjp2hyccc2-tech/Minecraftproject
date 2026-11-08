#!/usr/bin/env python3
"""Regenerates every ETERNAL REALM texture, item model and particle definition.

    python3 tools/art/generate_all.py            # write assets + tools/art/preview.png
    python3 tools/art/generate_all.py --sections /tmp/preview_sections   # also dump each preview section

Deterministic: no wall-clock or unseeded randomness, so re-running produces identical files.
"""
from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

import armor_icons  # noqa: E402
import armor_worn  # noqa: E402
import effects  # noqa: E402
import items  # noqa: E402
import models  # noqa: E402
import preview  # noqa: E402
import skills  # noqa: E402
import weapons  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
TEX = os.path.join(ASSETS, "textures")

FAMILIES = ["longsword", "greatsword", "twin_blades", "spear", "staff", "longbow"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sections", help="also write each preview section to this directory")
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    written = []

    def save(canvas, *parts):
        p = os.path.join(TEX, *parts)
        canvas.save(p)
        written.append(p)
        return canvas

    # 1. weapons --------------------------------------------------------------------------
    weapon_imgs = {}
    for fam in FAMILIES:
        fn = weapons.FAMILIES[fam]
        for n in range(1, weapons.TIERS + 1):
            c = fn(n)
            assert c.w == 32 and c.h == 32
            save(c, "item", "weapon", f"{fam}_{n}.png")
            weapon_imgs[(fam, n)] = c
        # tier 1 also replaces the original item texture (same file name as before)
        save(weapon_imgs[(fam, 1)], "item", f"{models.WEAPON_ITEMS[fam][0]}.png")
    written += models.weapon_models(ASSETS, weapons.TIERS)

    # 2. armor ----------------------------------------------------------------------------
    armor_imgs = {}
    for slot in armor_icons.SLOTS:
        for v in armor_icons.VARIANTS:
            c = armor_icons.icon(slot, v)
            nm = armor_icons.name(slot, v)
            save(c, "item", "armor", f"{nm}.png")
            armor_imgs[(slot, v)] = c
    written += models.armor_models(ASSETS, 7)
    worn = {}
    for key in armor_worn.SETS:
        l1, l2 = armor_worn.build(key)
        f1, f2 = armor_worn.file_names(key)
        save(l1, "models", "armor", f1)
        save(l2, "models", "armor", f2)
        worn[key] = (l1, l2)

    # 3. materials, consumables, coin -----------------------------------------------------
    item_models = {}
    mat_imgs = []
    for mid in items.MATERIALS:
        c = items.material(mid)
        save(c, "item", "material", f"{mid}.png")
        item_models[mid] = f"item/material/{mid}"
        mat_imgs.append((mid, c))
    con_imgs = []
    for cid in items.CONSUMABLES:
        c = items.consumable(cid)
        save(c, "item", "consumable", f"{cid}.png")
        item_models[cid] = f"item/consumable/{cid}"
        con_imgs.append((cid, c))
    coin = items.gold_coin()
    save(coin, "item", "gold_coin.png")
    item_models["gold_coin"] = "item/gold_coin"
    con_imgs.append(("gold_coin", coin))
    written += models.generated_item_models(ASSETS, item_models)

    # 4. skill icons + job emblems --------------------------------------------------------
    skill_imgs = []
    for sid in skills.ALL_SKILLS:
        c = skills.skill_icon(sid)
        save(c, "gui", "skill", f"{sid}.png")
        skill_imgs.append((sid, c))
    emblem_imgs = []
    for job in skills.JOBS:
        c = skills.emblem(job)
        save(c, "gui", "job", f"{job}.png")
        emblem_imgs.append((job, c))

    # 5. particles ------------------------------------------------------------------------
    frames = {}
    particle_imgs = []
    for name in effects.PARTICLES:
        fr = effects.particle_frames(name)
        for i, c in enumerate(fr):
            save(c, "particle", f"{name}_{i}.png")
            particle_imgs.append((f"{name}_{i}", c))
        frames[name] = len(fr)
    written += models.particle_jsons(ASSETS, frames)

    # 6. mob effects ----------------------------------------------------------------------
    effect_imgs = []
    for eid in effects.EFFECTS:
        c = effects.effect(eid)
        save(c, "mob_effect", f"{eid}.png")
        effect_imgs.append((eid, c))

    print(f"wrote {len(written)} files under {os.path.relpath(ASSETS, ROOT)}")

    if args.no_preview:
        return 0

    # preview -----------------------------------------------------------------------------
    W = 1180
    secs = []
    rows = [[weapon_imgs[(f, n)].image() for n in range(1, 8)] for f in FAMILIES]
    secs.append(preview.grid_section("Weapons - 32x32 (x4), custom_model_data 1..7", W, rows,
                                     preview.TIER_NAMES, FAMILIES, 4, 128, label_w=120, gap=8))
    vlabels = [str(v) if isinstance(v, int) else v for v in armor_icons.VARIANTS]
    rows = [[armor_imgs[(s, v)].image() for v in armor_icons.VARIANTS] for s in armor_icons.SLOTS]
    secs.append(preview.grid_section("Armor icons - 16x16 (x4), cmd 1..7 = tiers, 101..106 = jobs", W, rows,
                                     vlabels, list(armor_icons.SLOTS), 4, 72, label_w=100, gap=10))
    dolls = []
    for key in armor_worn.SETS:
        l1, l2 = worn[key]
        f = armor_worn.paperdoll(l1, l2, "front").image()
        b = armor_worn.paperdoll(l1, l2, "back").image()
        s = armor_worn.side_doll(l1, l2).image()
        im = Image.new("RGBA", (16 + 16 + 8 + 4, 32), (0, 0, 0, 0))
        im.alpha_composite(f, (0, 0))
        im.alpha_composite(b, (18, 0))
        im.alpha_composite(s, (36, 0))
        dolls.append((("tier " + str(key)) if isinstance(key, int) else key, im))
    secs.append(preview.flow_section("Worn armor (front / back / right side on a mannequin, x3)", W, dolls, 3, 136,
                                     gap=12))
    layouts = []
    for key in armor_worn.SETS:
        l1, l2 = worn[key]
        both = Image.new("RGBA", (64, 66), (0, 0, 0, 0))
        both.alpha_composite(l1.image(), (0, 0))
        both.alpha_composite(l2.image(), (0, 34))
        nm = armor_worn.file_names(key)[0].replace("_layer_1.png", "")
        layouts.append((nm + " (L1 / L2)", both))
    secs.append(preview.flow_section("Worn armor raw textures (64x32 layer_1 above layer_2, x2)", W, layouts, 2, 136,
                                     gap=12))
    secs.append(preview.flow_section("Materials (x4)", W, [(n, c.image()) for n, c in mat_imgs], 4, 64, gap=8))
    secs.append(preview.flow_section("Consumables + gold coin (x4)", W, [(n, c.image()) for n, c in con_imgs], 4, 64,
                                     gap=8))
    secs.append(preview.flow_section("Skill icons (x4) - knight / assassin / mage / ranger / priest / spellblade / weapon",
                                     W, [(n, c.image()) for n, c in skill_imgs], 4, 64, gap=8))
    secs.append(preview.flow_section("Job emblems (x4)", W, [(n, c.image()) for n, c in emblem_imgs], 4, 64, gap=8))
    secs.append(preview.flow_section("Particles (frames in order, x6)", W, [(n, c.image()) for n, c in particle_imgs],
                                     6, 96, gap=8))
    old = []
    for eid in ("attack_up", "defense_up", "flame_weapon", "bleed"):
        p = os.path.join(TEX, "mob_effect", f"{eid}.png")
        if os.path.exists(p):
            old.append((eid + " (existing)", Image.open(p).convert("RGBA")))
    secs.append(preview.flow_section("Mob effects 18x18 (x4)", W, [(n, c.image()) for n, c in effect_imgs] + old, 4, 72,
                                     gap=8))
    out = os.path.join(HERE, "preview.png")
    preview.stack(secs, out, args.sections)
    print(f"preview: {os.path.relpath(out, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
