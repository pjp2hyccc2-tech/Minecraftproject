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
import skill_tree  # noqa: E402
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
            if fam in models.PAIRED:
                held = weapons.held(fam, n)
                assert held.w == 32 and held.h == 32
                save(held, "item", "weapon", f"{fam}_{n}_held.png")
        # tier 1 also replaces the original item texture (same file name as before)
        save(weapon_imgs[(fam, 1)], "item", f"{models.WEAPON_ITEMS[fam][0]}.png")
    written += models.weapon_models(ASSETS, weapons.TIERS)
    import armory_art  # noqa: E402  (named designs: their overrides on the tier items)
    armory_art.patch_tier_models(ASSETS)

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
    tree_icons = {}
    for sid in skill_tree.new_skills():
        tree_icons[sid] = save(skill_tree.icon(sid), "gui", "skill", f"{sid}.png")
    # the second classes' icons and emblems (their own tool: recipes come from tools/jobs/class_trees.py)
    import class_skill_icons  # noqa: E402
    if class_skill_icons.generate(not args.no_preview):
        return 1
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
    problems = check()
    for p in problems:
        print("CHECK FAILED:", p)
    if problems:
        return 1
    print("self-check ok")

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
        nm = armor_worn.file_names(key)[0].replace("_layer_1.png", "").replace("er_", "")
        layouts.append((nm, both))
    secs.append(preview.flow_section("Worn armor raw textures (64x32 layer_1 above layer_2, x2)", W, layouts, 2, 136,
                                     gap=12))
    secs.append(preview.flow_section("Materials (x4)", W, [(n, c.image()) for n, c in mat_imgs], 4, 100, gap=12))
    secs.append(preview.flow_section("Consumables + gold coin (x4)", W, [(n, c.image()) for n, c in con_imgs], 4,
                                     120, gap=12))
    short = [(n.split("_", 1)[1] if n.split("_", 1)[0] in skills.JOBS else n, c.image()) for n, c in skill_imgs]
    secs.append(preview.flow_section("Skill icons (x4), per job in order knight / assassin / mage / ranger / priest / "
                                     "spellblade, then the 6 weapon skills", W, short, 4, 100, gap=12))
    secs.append(preview.flow_section("Job emblems (x4)", W, [(n, c.image()) for n, c in emblem_imgs], 4, 64, gap=8))
    secs.append(preview.flow_section("Particles (frames in order, x6)", W, [(n, c.image()) for n, c in particle_imgs],
                                     6, 96, gap=8))
    old = []
    for eid in ("attack_up", "defense_up", "flame_weapon", "bleed"):
        p = os.path.join(TEX, "mob_effect", f"{eid}.png")
        if os.path.exists(p):
            old.append((eid + "*", Image.open(p).convert("RGBA")))
    secs.append(preview.flow_section("Mob effects 18x18 (x4), * = existing icons for comparison", W,
                                     [(n, c.image()) for n, c in effect_imgs] + old, 4, 90, gap=12))
    out = os.path.join(HERE, "preview.png")
    preview.stack(secs, out, args.sections)
    print(f"preview: {os.path.relpath(out, ROOT)}")
    tree = os.path.join(HERE, "skill_tree_preview.png")
    skill_tree.preview(tree, {**{sid: c for sid, c in skill_imgs}, **tree_icons})
    print(f"skill trees: {os.path.relpath(tree, ROOT)}")
    return 0


def check() -> list[str]:
    """Verifies that every promised file exists with the right size and that overrides are ordered."""
    import json
    bad = []

    def png(rel, size):
        p = os.path.join(ASSETS, rel)
        if not os.path.exists(p):
            bad.append(f"missing {rel}")
            return
        with Image.open(p) as im:
            if im.size != size or im.mode != "RGBA":
                bad.append(f"{rel}: {im.size} {im.mode}, expected {size} RGBA")

    def model(rel, texture=None):
        p = os.path.join(ASSETS, rel)
        if not os.path.exists(p):
            bad.append(f"missing {rel}")
            return None
        with open(p, encoding="utf-8") as f:
            d = json.load(f)
        if texture and d.get("textures", {}).get("layer0") != texture:
            bad.append(f"{rel}: layer0 {d.get('textures')} != {texture}")
        ov = [o["predicate"]["custom_model_data"] for o in d.get("overrides", [])]
        if ov != sorted(ov):
            bad.append(f"{rel}: overrides not ascending {ov}")
        for o in d.get("overrides", []):
            ref = o["model"].split(":", 1)[1]
            if not os.path.exists(os.path.join(ASSETS, "models", ref + ".json")):
                bad.append(f"{rel}: override target {o['model']} missing")
        return d

    for fam in FAMILIES:
        item = models.WEAPON_ITEMS[fam][0]
        paired = fam in models.PAIRED
        png(f"textures/item/{item}.png", (32, 32))
        d = model(f"models/item/{item}.json", None if paired else f"eternalrealm:item/{item}")
        if d and [o["predicate"]["custom_model_data"] for o in d["overrides"]] != list(range(2, 8)):
            bad.append(f"{item}: overrides must be 2..7")
        for n in range(1, 8):
            png(f"textures/item/weapon/{fam}_{n}.png", (32, 32))
            if not paired:
                model(f"models/item/weapon/{fam}_{n}.json", f"eternalrealm:item/weapon/{fam}_{n}")
                continue
            # icon = the pair, hands = one blade (forge:separate_transforms)
            png(f"textures/item/weapon/{fam}_{n}_held.png", (32, 32))
            model(f"models/item/weapon/{fam}_{n}_icon.json", f"eternalrealm:item/weapon/{fam}_{n}")
            model(f"models/item/weapon/{fam}_{n}_held.json", f"eternalrealm:item/weapon/{fam}_{n}_held")
            d = model(f"models/item/weapon/{fam}_{n}.json")
            if d and (d.get("loader") != "forge:separate_transforms"
                      or d["base"]["parent"] != f"eternalrealm:item/weapon/{fam}_{n}_held"
                      or any(v["parent"] != f"eternalrealm:item/weapon/{fam}_{n}_icon"
                             for v in d["perspectives"].values())
                      or set(d["perspectives"]) != set(models.ICON_CONTEXTS)):
                bad.append(f"{fam}_{n}: separate_transforms model malformed")
    for slot in armor_icons.SLOTS:
        d = model(f"models/item/armor_{slot}.json", f"eternalrealm:item/armor/{slot}_1")
        if d and [o["predicate"]["custom_model_data"] for o in d["overrides"]] != list(range(2, 8)) + list(range(101, 107)):
            bad.append(f"armor_{slot}: overrides must be 2..7, 101..106")
        for v in armor_icons.VARIANTS:
            nm = armor_icons.name(slot, v)
            png(f"textures/item/armor/{nm}.png", (16, 16))
            model(f"models/item/armor/{nm}.json", f"eternalrealm:item/armor/{nm}")
    for key in armor_worn.SETS:
        for f in armor_worn.file_names(key):
            png(f"textures/models/armor/{f}", (64, 32))
    for mid in items.MATERIALS:
        png(f"textures/item/material/{mid}.png", (16, 16))
        model(f"models/item/{mid}.json", f"eternalrealm:item/material/{mid}")
    for cid in items.CONSUMABLES:
        png(f"textures/item/consumable/{cid}.png", (16, 16))
        model(f"models/item/{cid}.json", f"eternalrealm:item/consumable/{cid}")
    png("textures/item/gold_coin.png", (16, 16))
    model("models/item/gold_coin.json", "eternalrealm:item/gold_coin")
    for sid in skills.ALL_SKILLS + skill_tree.new_skills():
        png(f"textures/gui/skill/{sid}.png", (16, 16))
    bad += skill_tree.check()
    for job in skills.JOBS:
        png(f"textures/gui/job/{job}.png", (16, 16))
    for name, (fn, n) in effects.PARTICLES.items():
        p = os.path.join(ASSETS, "particles", f"{name}.json")
        with open(p, encoding="utf-8") as f:
            d = json.load(f)
        if d != {"textures": [f"eternalrealm:{name}_{i}" for i in range(n)]}:
            bad.append(f"particles/{name}.json unexpected {d}")
        for i in range(n):
            p = os.path.join(ASSETS, "textures", "particle", f"{name}_{i}.png")
            with Image.open(p) as im:
                if im.size not in ((8, 8), (16, 16)):
                    bad.append(f"particle {name}_{i} size {im.size}")
    for eid in effects.EFFECTS:
        png(f"textures/mob_effect/{eid}.png", (18, 18))
    # skill ids must match Jobs.java / WeaponProfiles.java when the sources are present
    import re
    src = os.path.join(ROOT, "src", "main", "java", "com", "eternalrealm")
    ids = set()
    for rel in (("job", "Jobs.java"), ("combat", "WeaponProfiles.java")):
        p = os.path.join(src, *rel)
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                ids |= set(re.findall(r'(?:skill|SkillDefinition)\("([a-z_]+)"', f.read()))
    # the job skill trees (KnightSkills.java ...): every node must have a recipe in skill_tree.py, and vice versa
    tree = set()
    for job in skill_tree.JOBS:
        p = os.path.join(src, "job", job.capitalize() + "Skills.java")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as f:
                tree |= {f"{job}_{n}" for n in re.findall(r'\.(?:active|passive|ultimate)\("([a-z_]+)"', f.read())}
    if tree:
        drawn = {n[0] for job in skill_tree.JOBS for n in skill_tree.nodes(job)}
        if tree != drawn:
            bad.append(f"skill tree nodes without icons: {sorted(tree - drawn)}, icons without nodes: "
                       f"{sorted(drawn - tree)}")
    missing = sorted(i for i in ids if i not in skills.ALL_SKILLS + skill_tree.new_skills())
    if missing:
        bad.append(f"skills in Java without an icon: {missing}")
    # the second classes' trees (GuardianSkills.java ...): every node has its icon, every class its emblem
    import class_skill_icons  # noqa: E402
    for cls in class_skill_icons.CLASSES:
        java = os.path.join(src, "job", "".join(w.capitalize() for w in cls.split("_")) + "Skills.java")
        if os.path.exists(java):
            with open(java, encoding="utf-8") as f:
                for n in re.findall(r'\.(?:active|passive|ultimate)\("([a-z_]+)"', f.read()):
                    png(f"textures/gui/skill/{cls}_{n}.png", (16, 16))
        png(f"textures/gui/job/{cls}.png", (16, 16))
    return bad


if __name__ == "__main__":
    sys.exit(main())
