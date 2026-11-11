"""Art of the relics (金枠の遺宝): weapon sprites, armor icons and worn textures, trinket icons, item models, the
overrides of the tier and trinket base items, and tools/art/relics_preview.png. Called by tools/art/relics.py."""
from __future__ import annotations

import json
import os
from types import SimpleNamespace

from PIL import Image, ImageDraw

import armory_art
import armor_icons
import armor_worn
import named_armor
import named_weapons
import trinket_icons
from icon import render
from relics_data import SLOTS

NS = "eternalrealm"
TIER_IDS = ["iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"]
PIECE_NOUNS = ["helmet", "chestplate", "leggings", "boots"]
HERE = os.path.dirname(os.path.abspath(__file__))
DARKS = {"DARK": named_armor.DARK, "DARK_BLUE": named_armor.DARK_BLUE, "DARK_GREEN": named_armor.DARK_GREEN,
         "DARK_RED": named_armor.DARK_RED, "DARK_VIOLET": named_armor.DARK_VIOLET}
EMBLEMS = dict(named_armor.EMBLEMS)
EMBLEMS.update({
    "crown": ["....", "F.F.F", "FGFGF", "FFFFF"],
    "tree": [".FF.", "FFFF", ".FF.", ".R.."],
    "gear": [".FF.", "FGGF", "FGGF", ".FF."],
    "key": [".GG.", ".GG.", "..F.", "..FF"],
    "star": ["..G.", ".GGG", "..G.", "...."],
    "skull": [".FF.", "FGFG", ".FF.", ".F.F"],
    "web": ["F.F.", ".F..", "F.F.", "...."],
    "cross": [".F..", "FFF.", ".F..", ".F.."],
})


def ramp(name):
    return named_weapons.MATERIALS[name]


def materials(look):
    pattern, a, b, c, e, g, emblem, extra = look
    kwargs = {}
    for key, value in extra.items():
        kwargs[key] = DARKS[value] if key == "D" else ramp(value)
    mats = named_armor.theme(ramp(a), ramp(b), ramp(c), ramp(e), ramp(g), **kwargs)
    return pattern, mats, emblem


def armor_icon(look, noun):
    pattern, mats, emblem = materials(look)
    rows = armor_icons.SLOTS[noun][pattern]
    if noun == "chestplate":
        rows = named_armor._overlay(rows, EMBLEMS[emblem], 4, 6)
    elif noun == "helmet":
        rows = named_armor._overlay(rows, ["GG"], 3, 7)
    if noun != "boots":
        return render(rows, mats, local="BSEGRF")
    return named_armor._boots(rows, mats)


def armor_worn_layers(look, set_id):
    pattern, mats, emblem = materials(look)
    spec = armor_worn.set_spec(pattern)
    body = dict(spec["body"])
    body["front"] = named_armor._overlay(body["front"], EMBLEMS[emblem], 2, 2)
    head = dict(spec["head"])
    if head.get("front") and head["front"][1] != "........":
        head["front"] = named_armor._overlay(head["front"], ["GG"], 1, 3)
    l1 = armor_worn.Layer(mats, f"relic_l1_{set_id}")
    l2 = armor_worn.Layer(mats, f"relic_l2_{set_id}")
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


def trinket_icon(kind, trinket):
    metal, gem, cloth = trinket
    return trinket_icons.icon(kind, ramp(metal), ramp(gem), ramp(cloth))


def overrides(relics):
    """item path -> [(model number, model id)] of the relics made on it (weapons and armor on the tier items,
    trinkets on the trinket base items)."""
    out = {}
    for r in relics:
        tier = TIER_IDS[r.tier - 1]
        family = armory_art.FAMILY[_type_id(r)]
        item = f"{tier}_{'bow' if family == 'longbow' else _type_id(r)}"
        out.setdefault(item, []).append((r.weapon_model, f"{NS}:item/named/{r.weapon_id}"))
        for i, (piece_id, noun) in enumerate(zip(r.piece_ids(), PIECE_NOUNS)):
            out.setdefault(f"{tier}_{noun}", []).append((r.set_model + i, f"{NS}:item/named/{piece_id}"))
        out.setdefault(f"{tier}_accessory", []).append((r.accessory_model, f"{NS}:item/trinket/{r.accessory_id}"))
        out.setdefault(f"{tier}_charm", []).append((r.charm_model, f"{NS}:item/trinket/{r.charm_id}"))
    return out


def _type_id(r):
    return {"SWORD": "longsword", "GREAT": "greatsword", "TWIN": "twin_blades", "SPEAR": "spear", "STAFF": "staff",
            "BOW": "bow"}[r.wtype]


def write_trinket_bases(assets):
    """The 14 trinket base items: a plain icon and the overrides of their relics (written by patch)."""
    acc, charm = trinket_icons.base_icons()
    armory_art.save_png(acc, os.path.join(assets, "textures", "item", "trinket", "base_accessory.png"))
    armory_art.save_png(charm, os.path.join(assets, "textures", "item", "trinket", "base_charm.png"))
    for tier in TIER_IDS:
        for kind in ("accessory", "charm"):
            path = os.path.join(assets, "models", "item", f"{tier}_{kind}.json")
            if not os.path.isfile(path):
                armory_art.write_json(path, {"parent": "minecraft:item/generated",
                                             "textures": {"layer0": f"{NS}:item/trinket/base_{kind}"}})


def generate(relics, assets):
    tex_named = os.path.join(assets, "textures", "item", "named")
    tex_trinket = os.path.join(assets, "textures", "item", "trinket")
    weapons, pieces, worn, trinkets = {}, {}, {}, {}
    for r in relics:
        spec = r.sprite
        canvas = named_weapons.render(spec)
        armory_art.save_png(canvas, os.path.join(tex_named, f"{r.weapon_id}.png"))
        if spec["kind"] == "twin":
            armory_art.save_png(named_weapons.render_held(spec), os.path.join(tex_named, f"{r.weapon_id}_held.png"))
        armory_art.weapon_models(assets, SimpleNamespace(id=r.weapon_id, type=_type_id(r)))
        weapons[r.key] = canvas
        for piece_id, noun in zip(r.piece_ids(), PIECE_NOUNS):
            img = armor_icon(r.look, noun)
            armory_art.save_png(img, os.path.join(tex_named, f"{piece_id}.png"))
            armory_art.armor_models(assets, piece_id)
            pieces[piece_id] = img
        l1, l2 = armor_worn_layers(r.look, r.set_id)
        armory_art.save_png(l1, os.path.join(assets, "textures", "models", "armor", f"er_set_{r.set_id}_layer_1.png"))
        armory_art.save_png(l2, os.path.join(assets, "textures", "models", "armor", f"er_set_{r.set_id}_layer_2.png"))
        worn[r.key] = (l1, l2)
        for trinket_id, kind in ((r.accessory_id, r.acc[0]), (r.charm_id, r.charm[0])):
            img = trinket_icon(kind, r.trinket)
            armory_art.save_png(img, os.path.join(tex_trinket, f"{trinket_id}.png"))
            armory_art.write_json(os.path.join(assets, "models", "item", "trinket", f"{trinket_id}.json"),
                                  {"parent": "minecraft:item/generated",
                                   "textures": {"layer0": f"{NS}:item/trinket/{trinket_id}"}})
            trinkets[trinket_id] = img
    write_trinket_bases(assets)
    patched = armory_art.patch_tier_models(assets)
    print(f"relic art: {len(weapons)} weapons, {len(pieces)} armor pieces, {len(worn)} worn sets, "
          f"{len(trinkets)} trinkets; {patched} item models patched")
    preview(relics, weapons, pieces, worn, trinkets)


def preview(relics, weapons, pieces, worn, trinkets):
    bg = (30, 26, 34, 255)
    cell = 34 * 2
    row_h = cell + 10
    width = 12 + 8 * (cell + 6) + 3 * 52 + 40
    height = 30 + len(relics) * row_h // 2 + 20
    img = Image.new("RGBA", (width * 2, height), bg)
    d = ImageDraw.Draw(img)
    d.text((8, 6), "Relics (gold frame): weapon, four armor pieces, accessory, charm, worn set (front/back/side)",
           fill=(255, 216, 74, 255))
    for i, r in enumerate(relics):
        col, row = divmod(i, (len(relics) + 1) // 2)
        x0 = 8 + col * width
        y0 = 26 + row * row_h
        d.text((x0, y0 + cell // 2 - 6), r.key[:12], fill=(230, 220, 190, 255))
        x = x0 + 96
        tiles = [weapons[r.key]] + [pieces[p] for p in r.piece_ids()] + [trinkets[r.accessory_id],
                                                                           trinkets[r.charm_id]]
        for tile in tiles:
            im = Image.fromarray(tile.a, "RGBA")
            scale = cell // im.size[0]
            im = im.resize((im.size[0] * scale, im.size[1] * scale), Image.NEAREST)
            d.rectangle((x - 2, y0 - 2, x + cell + 1, y0 + cell + 1), outline=(255, 216, 74, 255))
            img.alpha_composite(im, (x, y0))
            x += cell + 6
        l1, l2 = worn[r.key]
        doll = Image.new("RGBA", (46, 32), (0, 0, 0, 0))
        doll.alpha_composite(armor_worn.paperdoll(l1, l2, "front").image(), (0, 0))
        doll.alpha_composite(armor_worn.paperdoll(l1, l2, "back").image(), (18, 0))
        doll.alpha_composite(armor_worn.side_doll(l1, l2).image(), (36, 0))
        img.alpha_composite(doll.resize((46 * 2, 32 * 2), Image.NEAREST), (x + 4, y0))
    out = os.path.join(HERE, "relics_preview.png")
    img.save(out, optimize=True)
    print(f"preview: {os.path.relpath(out, os.path.join(HERE, '..', '..'))} {img.size}")


def check(relics, assets):
    missing = []
    for r in relics:
        paths = [f"textures/item/named/{r.weapon_id}.png", f"models/item/named/{r.weapon_id}.json",
                 f"textures/models/armor/er_set_{r.set_id}_layer_1.png",
                 f"textures/models/armor/er_set_{r.set_id}_layer_2.png",
                 f"textures/item/trinket/{r.accessory_id}.png", f"textures/item/trinket/{r.charm_id}.png"]
        paths += [f"textures/item/named/{p}.png" for p in r.piece_ids()]
        for p in paths:
            if not os.path.isfile(os.path.join(assets, p)):
                missing.append(p)
    return missing
