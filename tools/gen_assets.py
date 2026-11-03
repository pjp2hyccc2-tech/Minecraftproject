#!/usr/bin/env python3
"""Generates the blockstates, simple block models and item models of REISATSU (assets/reisatsu/...).

Prop block models and 3D held item models are made by the art pass (tools/art) and are never overwritten here.
Run from anywhere: python3 tools/gen_assets.py
"""
import json
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "reisatsu"))
NS = "reisatsu"

# made by the art pass (tools/art): never written here
ART_BLOCK_MODELS = {"lost_bag", "lost_bag_open", "memo", "memo_wall", "logbook", "notice_board", "signpost", "jizo", "hokora",
                    "hokora_offered", "wara_ningyo", "rope_anchor", "rope_anchor_roped", "climbing_rope", "public_phone", "bus_stop",
                    "tunnel_lamp", "tunnel_lamp_lit", "guardrail", "key_hook", "key_hook_empty", "car_door", "blue_tarp",
                    "fallen_leaves"}
ART_ITEM_MODELS = {"flashlight", "flashlight_on", "flashlight_low", "phone", "phone_light", "phone_camera", "flare", "flare_lit"}
FLAT_ITEMS = ["battery", "power_bank", "bear_bell", "rope", "trail_map", "omamori", "hut_key", "omiki", "memo"]
MONSTERS = ["nozoki", "yobigoe", "ushiro", "koyanushi", "oyamasama", "utsurumono"]
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bs(name, variants):
    write(f"blockstates/{name}.json", {"variants": variants})


def model(name, data):
    if name in ART_BLOCK_MODELS:
        return
    write(f"models/block/{name}.json", data)


def item(name, data):
    if name in ART_ITEM_MODELS:
        return
    write(f"models/item/{name}.json", data)


def t(name):
    return name if ":" in name else f"{NS}:block/{name}"


def m(name):
    return f"{NS}:block/{name}"


def block_item(name, model_name=None):
    item(name, {"parent": m(model_name or name)})


def flat_item(name, texture=None):
    item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": texture or f"{NS}:item/{name}"}})


def facing(name, model_for, extra=None):
    """extra: (property, [values]) -> model_for(value)."""
    variants = {}
    for f, y in FACING_Y.items():
        if extra:
            prop, values = extra
            for v in values:
                variants[f"facing={f},{prop}={v}"] = {"model": model_for(v), "y": y} if y else {"model": model_for(v)}
        else:
            variants[f"facing={f}"] = {"model": model_for(None), "y": y} if y else {"model": model_for(None)}
    return variants


def cube_all(name, texture=None, cutout=False):
    d = {"parent": "minecraft:block/cube_all", "textures": {"all": t(texture or name)}}
    if cutout:
        d["render_type"] = "minecraft:cutout"
    model(name, d)
    bs(name, {"": {"model": m(name)}})
    block_item(name)


def bottom_top(name, top, side, bottom, spin=False):
    model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": t(top), "side": t(side), "bottom": t(bottom)}})
    if spin:
        # ground that covers whole hillsides: a random quarter turn per block hides the tile repeat
        bs(name, {"": [{"model": m(name)}, {"model": m(name), "y": 90}, {"model": m(name), "y": 180},
                       {"model": m(name), "y": 270}]})
    else:
        bs(name, {"": {"model": m(name)}})
    block_item(name)


def pillar(name, side, end):
    model(name, {"parent": "minecraft:block/cube_column", "textures": {"side": t(side), "end": t(end)}})
    model(name + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": t(side), "end": t(end)}})
    bs(name, {"axis=y": {"model": m(name)}, "axis=z": {"model": m(name + "_horizontal"), "x": 90},
              "axis=x": {"model": m(name + "_horizontal"), "x": 90, "y": 90}})
    block_item(name)


def decal(name, texture, height=0.5, cutout=True):
    d = {"parent": "minecraft:block/block", "textures": {"particle": t(texture), "tex": t(texture)},
         "elements": [{"from": [0, 0, 0], "to": [16, height, 16],
                       "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#tex"}, "down": {"uv": [0, 16, 16, 0], "texture": "#tex"}}}]}
    if cutout:
        d["render_type"] = "minecraft:cutout"
    model(name, d)
    bs(name, facing(name, lambda v: m(name)))
    block_item(name)


def stairs_slab(base, texture):
    tx = t(texture)
    model(base + "_stairs", {"parent": "minecraft:block/stairs", "textures": {"bottom": tx, "top": tx, "side": tx}})
    model(base + "_stairs_inner", {"parent": "minecraft:block/inner_stairs", "textures": {"bottom": tx, "top": tx, "side": tx}})
    model(base + "_stairs_outer", {"parent": "minecraft:block/outer_stairs", "textures": {"bottom": tx, "top": tx, "side": tx}})
    variants = {}
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                mdl = m(base + "_stairs") if shape == "straight" else m(base + ("_stairs_inner" if shape.startswith("inner") else "_stairs_outer"))
                y = rot[f]
                if shape in ("inner_left", "outer_left"):
                    y = (y + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape in ("inner_left", "outer_left", "inner_right", "outer_right"):
                        y = (y + 90) % 360
                v = {"model": mdl}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if x or y:
                    v["uvlock"] = True
                variants[f"facing={f},half={half},shape={shape}"] = v
    bs(base + "_stairs", variants)
    block_item(base + "_stairs")
    model(base + "_slab", {"parent": "minecraft:block/slab", "textures": {"bottom": tx, "top": tx, "side": tx}})
    model(base + "_slab_top", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tx, "top": tx, "side": tx}})
    bs(base + "_slab", {"type=bottom": {"model": m(base + "_slab")}, "type=top": {"model": m(base + "_slab_top")},
                        "type=double": {"model": m(base)}})
    block_item(base + "_slab")


def door(name):
    lo, hi = t(name + "_bottom"), t(name + "_top")
    for kind in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right",
                 "top_right_open"):
        model(f"{name}_{kind}", {"parent": f"minecraft:block/door_{kind}", "render_type": "minecraft:cutout",
                                 "textures": {"bottom": lo, "top": hi}})
    variants = {}
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f in ("east", "north", "south", "west"):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    part = ("bottom" if half == "lower" else "top") + "_" + hinge + ("_open" if opened == "true" else "")
                    y = base_y[f]
                    if opened == "true":
                        y = (y + (90 if hinge == "left" else 270)) % 360
                    v = {"model": m(f"{name}_{part}")}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},half={half},hinge={hinge},open={opened}"] = v
    bs(name, variants)
    flat_item(name)


def front_model(name, frm, to, front, side, top=None, cutout=False):
    tx = {"particle": t(side), "front": t(front), "side": t(side), "top": t(top or side)}
    faces = {"north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
             "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}
    d = {"parent": "minecraft:block/block", "textures": tx, "elements": [{"from": frm, "to": to, "faces": faces}]}
    if cutout:
        d["render_type"] = "minecraft:cutout"
    model(name, d)


def main():
    # ---- nature
    pillar("cedar_log", "cedar_log", "cedar_log_top")
    pillar("cedar_log_ribbon", "cedar_log_ribbon", "cedar_log_top")
    pillar("cedar_log_shimenawa", "cedar_log_shimenawa", "cedar_log_top")
    model("cedar_leaves", {"parent": "minecraft:block/leaves", "render_type": "minecraft:cutout_mipped",
                           "textures": {"all": t("cedar_leaves")}})
    bs("cedar_leaves", {"": {"model": m("cedar_leaves")}})
    block_item("cedar_leaves")
    bottom_top("forest_floor", "forest_floor_top", "forest_floor_side", "forest_dirt", spin=True)
    cube_all("forest_dirt")
    bottom_top("moss_floor", "moss_floor", "forest_floor_side", "forest_dirt", spin=True)
    bottom_top("trail", "trail_top", "forest_dirt", "forest_dirt", spin=True)
    for n in ("mountain_rock", "mossy_rock", "river_stones"):
        cube_all(n)
    bs("fallen_leaves", facing("fallen_leaves", lambda v: m("fallen_leaves")))
    block_item("fallen_leaves")

    # ---- buildings
    for n in ("weathered_planks", "dark_planks", "old_plaster", "tin_roof", "concrete_old", "concrete_moss", "asphalt", "old_planks",
              "rotten_planks", "plaster_wall"):
        cube_all(n)
    cube_all("shoji_torn", cutout=True)
    cube_all("steel_lattice", cutout=True)
    stairs_slab("tin_roof", "tin_roof")
    bs("blue_tarp", facing("blue_tarp", lambda v: m("blue_tarp")))
    block_item("blue_tarp")
    bottom_top("tatami_old", "tatami_old_top", "tatami_old_side", "tatami_old_side")
    pillar("dark_beam", "dark_beam_side", "dark_beam_top")
    # futon: a 3 px mattress
    model("futon", {"parent": "minecraft:block/block", "textures": {"particle": t("futon_top"), "top": t("futon_top"), "side": t("futon_side")},
                    "elements": [{"from": [0, 0, 0], "to": [16, 3, 16], "faces": {
                        "up": {"texture": "#top"}, "down": {"texture": "#side"}, "north": {"uv": [0, 13, 16, 16], "texture": "#side"},
                        "south": {"uv": [0, 13, 16, 16], "texture": "#side"}, "east": {"uv": [0, 13, 16, 16], "texture": "#side"},
                        "west": {"uv": [0, 13, 16, 16], "texture": "#side"}}}]})
    bs("futon", facing("futon", lambda v: m("futon")))
    block_item("futon")
    # stone lantern: base, post, fire box, cap
    st = t("stone_lantern")
    def el(a, b):
        return {"from": a, "to": b, "faces": {d: {"texture": "#s"} for d in ("north", "south", "east", "west", "up", "down")}}
    model("stone_lantern", {"parent": "minecraft:block/block", "textures": {"particle": st, "s": st},
                            "elements": [el([4, 0, 4], [12, 2, 12]), el([6, 2, 6], [10, 8, 10]), el([4, 8, 4], [12, 12, 12]),
                                         el([3, 12, 3], [13, 14, 13]), el([6, 14, 6], [10, 16, 10])]})
    bs("stone_lantern", facing("stone_lantern", lambda v: m("stone_lantern")))
    block_item("stone_lantern")
    for n in ("blood_stain", "footprints", "hair_strands"):
        decal(n, n)
    # shimenawa: a hanging plane in the middle of the block
    model("shimenawa", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                        "textures": {"particle": t("shimenawa"), "tex": t("shimenawa")},
                        "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "faces": {
                            "north": {"uv": [0, 0, 16, 16], "texture": "#tex"}, "south": {"uv": [16, 0, 0, 16], "texture": "#tex"}}}]})
    bs("shimenawa", facing("shimenawa", lambda v: m("shimenawa")))
    block_item("shimenawa")

    # ---- doors
    for d in ("hut_door", "house_door", "iron_gate"):
        door(d)

    # ---- props (models by the art pass)
    bs("lost_bag", facing("lost_bag", lambda v: m("lost_bag_open" if v == "true" else "lost_bag"), ("open", ["false", "true"])))
    block_item("lost_bag")
    for n in ("memo", "memo_wall", "logbook", "notice_board", "signpost", "jizo", "wara_ningyo", "public_phone", "bus_stop",
              "guardrail", "car_door"):
        bs(n, facing(n, lambda v, n=n: m(n)))
        if n not in ("memo", "memo_wall"):
            block_item(n)
    bs("hokora", facing("hokora", lambda v: m("hokora_offered" if v == "true" else "hokora"), ("offered", ["false", "true"])))
    block_item("hokora")
    bs("rope_anchor", facing("rope_anchor", lambda v: m("rope_anchor_roped" if v == "true" else "rope_anchor"), ("roped", ["false", "true"])))
    block_item("rope_anchor")
    bs("tunnel_lamp", facing("tunnel_lamp", lambda v: m("tunnel_lamp_lit" if v == "true" else "tunnel_lamp"), ("lit", ["false", "true"])))
    block_item("tunnel_lamp")
    bs("key_hook", facing("key_hook", lambda v: m("key_hook" if v == "true" else "key_hook_empty"), ("has_key", ["true", "false"])))
    block_item("key_hook")
    bs("climbing_rope", {f"facing={f}": ({"model": m("climbing_rope"), "y": y} if y else {"model": m("climbing_rope")})
                         for f, y in FACING_Y.items()})
    flat_item("climbing_rope", t("climbing_rope"))
    # the sake bottle (徳利), vanilla textures
    model("omiki_bottle", {"parent": "minecraft:block/block",
                           "textures": {"particle": "minecraft:block/calcite", "body": "minecraft:block/calcite",
                                        "label": "minecraft:block/white_wool", "cap": "minecraft:block/stripped_oak_log"},
                           "elements": [
                               {"from": [5.5, 0, 5.5], "to": [10.5, 6, 10.5], "faces": {d: {"texture": "#body"} for d in
                                                                                       ("north", "south", "east", "west", "up", "down")}},
                               {"from": [6.5, 6, 6.5], "to": [9.5, 9, 9.5], "faces": {d: {"texture": "#body"} for d in
                                                                                     ("north", "south", "east", "west", "up")}},
                               {"from": [6.2, 2, 5.4], "to": [9.8, 5, 5.5], "faces": {"north": {"texture": "#label"}}},
                               {"from": [7, 9, 7], "to": [9, 10.5, 9], "faces": {d: {"texture": "#cap"} for d in
                                                                                ("north", "south", "east", "west", "up")}}]})
    bs("omiki_bottle", facing("omiki_bottle", lambda v: m("omiki_bottle")))
    block_item("omiki_bottle")

    # ---- items
    for n in FLAT_ITEMS:
        flat_item(n)
    for n in MONSTERS:
        item(f"{n}_spawn_egg", {"parent": "minecraft:item/template_spawn_egg"})
    print("assets written to", ROOT)


if __name__ == "__main__":
    main()
