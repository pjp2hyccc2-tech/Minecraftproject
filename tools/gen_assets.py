#!/usr/bin/env python3
"""Generates every blockstate, block model and item model of MEIKON (assets/meikon/...).

Run from anywhere: python3 tools/gen_assets.py
"""
import json
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "meikon"))
NS = "meikon"


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bs(name, variants):
    write(f"blockstates/{name}.json", {"variants": variants})


def model(name, data):
    write(f"models/block/{name}.json", data)


def item(name, data):
    write(f"models/item/{name}.json", data)


def tex(t):
    return t if ":" in t else f"{NS}:block/{t}"


def face(t, uv=None, cull=None):
    f = {"texture": t}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces, shade=True):
    e = {"from": frm, "to": to, "faces": faces}
    if not shade:
        e["shade"] = False
    return e


def all_faces(t, uvs=None):
    return {d: face(t, (uvs or {}).get(d)) for d in ("north", "south", "east", "west", "up", "down")}


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_variants(model_for, extra_props=None):
    """model_for(props dict) -> (model name). extra_props: list of (name, values)."""
    variants = {}
    combos = [{}]
    for pname, values in (extra_props or []):
        combos = [dict(c, **{pname: v}) for c in combos for v in values]
    for f, y in FACING_Y.items():
        for c in combos:
            key = ",".join([f"facing={f}"] + [f"{k}={v}" for k, v in c.items()])
            v = {"model": f"{NS}:block/{model_for(c)}"}
            if y:
                v["y"] = y
            variants[key] = v
    return variants


def block_item(name, model_name=None):
    item(name, {"parent": f"{NS}:block/{model_name or name}"})


def flat_item(name, texture):
    item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


# ------------------------------------------------------------------ simple cubes
for name in ["old_planks", "engawa", "rotten_planks", "plaster_wall", "plaster_wall_stained", "wainscot", "kura_wall",
             "shoji", "shoji_bloody", "fusuma", "black_lacquer", "hina_dan"]:
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
    bs(name, {"": {"model": f"{NS}:block/{name}"}})
    block_item(name)

for name in ["shoji_torn", "ranma"]:
    model(name, {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": tex(name)}})
    bs(name, {"": {"model": f"{NS}:block/{name}"}})
    block_item(name)

# ------------------------------------------------------------------ tatami (horizontal axis)
model("tatami", {"parent": "minecraft:block/cube", "textures": {
    "up": tex("tatami_top"), "down": tex("tatami_side"), "north": tex("tatami_side"), "south": tex("tatami_side"),
    "east": tex("tatami_side"), "west": tex("tatami_side"), "particle": tex("tatami_top")}})
bs("tatami", {"axis=z": {"model": f"{NS}:block/tatami"}, "axis=x": {"model": f"{NS}:block/tatami", "y": 90}})
block_item("tatami")

# ------------------------------------------------------------------ pillars
for name in ["dark_beam", "vermilion"]:
    model(name, {"parent": "minecraft:block/cube_column", "textures": {"end": tex(name + "_top"), "side": tex(name + "_side" if name == "dark_beam" else name)}})
    model(name + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal",
                                 "textures": {"end": tex(name + "_top"), "side": tex(name + "_side" if name == "dark_beam" else name)}})
    bs(name, {"axis=y": {"model": f"{NS}:block/{name}"},
              "axis=z": {"model": f"{NS}:block/{name}_horizontal", "x": 90},
              "axis=x": {"model": f"{NS}:block/{name}_horizontal", "x": 90, "y": 90}})
    block_item(name)

# ------------------------------------------------------------------ doors
DOORS = {"fusuma_door": "fusuma_door", "shoji_door": "shoji_door", "sealed_door": "shoji_door",
         "oshiire_door": "oshiire_door", "kura_door": "kura_door"}
for name, t in DOORS.items():
    for part in ["bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"]:
        model(f"{name}_{part}", {"parent": f"minecraft:block/door_{part}", "render_type": "minecraft:cutout",
                                 "textures": {"bottom": tex(t + "_bottom"), "top": tex(t + "_top")}})
    variants = {}
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f, rot in base.items():
        for half, hname in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    m = f"{NS}:block/{name}_{hname}_{hinge}" + ("_open" if opened == "true" else "")
                    y = rot
                    if opened == "true":
                        y = (rot + (90 if hinge == "left" else 270)) % 360
                    v = {"model": m}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},half={half},hinge={hinge},open={opened}"] = v
    bs(name, variants)
    flat_item(name, f"{NS}:item/{t}")

# ------------------------------------------------------------------ torii veil (translucent, animated)
for name in ["torii_gate", "torii_gate_active"]:
    t = tex(name)
    model(name, {"render_type": "minecraft:translucent", "textures": {"particle": t, "veil": t}, "elements": [
        box([0, 0, 7], [16, 16, 9], {"north": face("#veil"), "south": face("#veil")}, shade=False)]})
bs("torii_gate", {"active=false": {"model": f"{NS}:block/torii_gate"}, "active=true": {"model": f"{NS}:block/torii_gate_active"}})
flat_item("torii_gate", f"{NS}:block/torii_gate")

# ------------------------------------------------------------------ thin facing decorations (model built facing NORTH, back at z=16)
model("shimenawa", {"render_type": "minecraft:cutout", "ambientocclusion": False,
                    "textures": {"particle": tex("shimenawa"), "t": tex("shimenawa")}, "elements": [
    box([0, 0, 7.5], [16, 16, 8.5], {"north": face("#t"), "south": face("#t", [16, 0, 0, 16])}, shade=False)]})
bs("shimenawa", facing_variants(lambda c: "shimenawa"))
flat_item("shimenawa", f"{NS}:block/shimenawa")

for name in ["blood_stain", "hair_strands", "footprints"]:
    model(name, {"render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": {"particle": tex(name), "t": tex(name)}, "elements": [
        box([0, 0.05, 0], [16, 0.3, 16], {"up": face("#t"), "down": face("#t")})]})
    bs(name, facing_variants(lambda c, n=name: n))
    flat_item(name, f"{NS}:block/{name}")

model("letter", {"render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": {"particle": f"{NS}:item/note", "t": f"{NS}:item/note"}, "elements": [
    box([0, 0.05, 0], [16, 0.3, 16], {"up": face("#t"), "down": face("#t")})]})
bs("letter", facing_variants(lambda c: "letter"))
flat_item("letter", f"{NS}:item/note")

for name in ["kakejiku", "kakejiku_face"]:
    model(name, {"render_type": "minecraft:cutout", "textures": {"particle": tex(name), "t": tex(name)}, "elements": [
        box([0, 0, 15.4], [16, 16, 16], {"north": face("#t"), "south": face("#t", [16, 0, 0, 16]),
                                         "up": face("#t", [0, 0, 16, 1]), "down": face("#t", [0, 15, 16, 16])})]})
bs("kakejiku", facing_variants(lambda c: "kakejiku_face" if c["face"] == "true" else "kakejiku", [("face", ["false", "true"])]))
flat_item("kakejiku", f"{NS}:block/kakejiku")

model("futon", {"textures": {"particle": tex("futon_top"), "top": tex("futon_top"), "side": tex("futon_side")}, "elements": [
    box([0, 0, 0], [16, 3, 16], {"up": face("#top"), "down": face("#side"), "north": face("#side", [0, 13, 16, 16]),
                                 "south": face("#side", [0, 13, 16, 16]), "east": face("#side", [0, 13, 16, 16]),
                                 "west": face("#side", [0, 13, 16, 16])})]})
bs("futon", facing_variants(lambda c: "futon"))
block_item("futon")

# ------------------------------------------------------------------ furniture (facing north)
def front_box(name, frm, to, front, side, top, cutout=False):
    d = {"textures": {"particle": tex(side), "front": tex(front), "side": tex(side), "top": tex(top)}, "elements": [
        box(frm, to, {"north": face("#front"), "south": face("#side"), "east": face("#side"), "west": face("#side"),
                      "up": face("#top"), "down": face("#top")})]}
    if cutout:
        d["render_type"] = "minecraft:cutout"
    return d


model("butsudan", front_box("butsudan", [1, 0, 4], [15, 16, 16], "butsudan_front", "butsudan_side", "butsudan_top"))
bs("butsudan", facing_variants(lambda c: "butsudan"))
block_item("butsudan")

model("kamado", {"parent": "minecraft:block/orientable", "textures": {"front": tex("kamado_front"), "side": tex("kamado_side"), "top": tex("kamado_top")}})
bs("kamado", facing_variants(lambda c: "kamado"))
block_item("kamado")

model("old_clock", front_box("old_clock", [3, 0, 11], [13, 16, 16], "old_clock_front", "old_clock_side", "old_clock_side"))
bs("old_clock", facing_variants(lambda c: "old_clock"))
block_item("old_clock")

tansu = front_box("tansu", [1, 0, 3], [15, 16, 16], "tansu_front", "tansu_side", "tansu_top")
model("tansu", tansu)
tansu_open = json.loads(json.dumps(tansu))
tansu_open["elements"].append(box([2.5, 7, 0], [13.5, 10, 3], {"north": face("#front", [2, 6, 14, 9]), "east": face("#side"), "west": face("#side"),
                                                                 "up": face("#top"), "down": face("#side")}))
model("tansu_opened", tansu_open)
bs("tansu", facing_variants(lambda c: "tansu_opened" if c["opened"] == "true" else "tansu", [("opened", ["false", "true"])]))
block_item("tansu")

model("well", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tex("well_top"), "bottom": tex("well_side"), "side": tex("well_side")}})
bs("well", facing_variants(lambda c: "well"))
block_item("well")

model("sakazuki_table", {"textures": {"particle": tex("sakazuki_table"), "t": tex("sakazuki_table")}, "elements": [
    box([2, 0, 2], [14, 4, 14], {"up": face("#t"), "down": face("#t"), "north": face("#t", [0, 12, 16, 16]),
                                 "south": face("#t", [0, 12, 16, 16]), "east": face("#t", [0, 12, 16, 16]), "west": face("#t", [0, 12, 16, 16])})]})
bs("sakazuki_table", facing_variants(lambda c: "sakazuki_table"))
block_item("sakazuki_table")

st = "stone_lantern"
model(st, {"textures": {"particle": tex(st), "t": tex(st)}, "elements": [
    box([4, 0, 4], [12, 2, 12], all_faces("#t")),
    box([6, 2, 6], [10, 8, 10], all_faces("#t")),
    box([4, 8, 4], [12, 12, 12], all_faces("#t")),
    box([3, 12, 3], [13, 14, 13], all_faces("#t")),
    box([6, 14, 6], [10, 16, 10], all_faces("#t"))]})
bs(st, facing_variants(lambda c: st))
block_item(st)

# headless doll: body + (optional) head
doll_body = box([5, 0, 6], [11, 8, 10], all_faces("#k"))
doll_head = box([5.5, 8, 6], [10.5, 13, 10], {"north": face("#f"), "south": face("#f", [0, 0, 4, 16]), "east": face("#f", [0, 0, 4, 16]),
                                              "west": face("#f", [12, 0, 16, 16]), "up": face("#f", [0, 0, 16, 3]), "down": face("#f")})
model("headless_doll", {"textures": {"particle": tex("doll_kimono"), "k": tex("doll_kimono"), "f": tex("doll_face")}, "elements": [doll_body]})
model("headless_doll_head", {"textures": {"particle": tex("doll_kimono"), "k": tex("doll_kimono"), "f": tex("doll_face")}, "elements": [doll_body, doll_head]})
bs("headless_doll", facing_variants(lambda c: "headless_doll_head" if c["head"] == "true" else "headless_doll", [("head", ["false", "true"])]))
block_item("headless_doll")

# ------------------------------------------------------------------ lamps and candles
for lit in (True, False):
    n = "andon" if lit else "andon_off"
    side = "andon_side" if lit else "andon_side_off"
    model(n, {"textures": {"particle": tex(side), "s": tex(side), "t": tex("andon_top")}, "elements": [
        box([3, 0, 3], [13, 15, 13], {"north": face("#s"), "south": face("#s"), "east": face("#s"), "west": face("#s"),
                                      "up": face("#t"), "down": face("#t")}, shade=not lit)]})
bs("andon", {"lit=true": {"model": f"{NS}:block/andon"}, "lit=false": {"model": f"{NS}:block/andon_off"}})
block_item("andon")

COLORS = ["gold", "silver", "black"]
for ci, c in enumerate(COLORS):
    for lit in (False, True):
        n = f"candle_stand_{c}" + ("_lit" if lit else "")
        holder = "#h"
        els = [
            box([5, 0, 5], [11, 1, 11], all_faces(holder)),
            box([7, 1, 7], [9, 8, 9], all_faces(holder)),
            box([5.5, 8, 5.5], [10.5, 9, 10.5], all_faces(holder)),
            box([7, 9, 7], [9, 13, 9], all_faces("#w")),
        ]
        if lit:
            els.append({"from": [8, 13, 6.5], "to": [8, 16, 9.5], "shade": False,
                        "faces": {"east": face("#f", [5, 7, 11, 16]), "west": face("#f", [5, 7, 11, 16])}})
            els.append({"from": [6.5, 13, 8], "to": [9.5, 16, 8], "shade": False,
                        "faces": {"north": face("#f", [5, 7, 11, 16]), "south": face("#f", [5, 7, 11, 16])}})
        model(n, {"render_type": "minecraft:cutout", "textures": {"particle": tex(f"candle_{c}"), "h": tex(f"candle_{c}"),
                                                                 "w": tex("candle_wax"), "f": tex("candle_flame")}, "elements": els})
variants = {}
for ci, c in enumerate(COLORS):
    for lit in ("false", "true"):
        variants[f"color={ci},lit={lit}"] = {"model": f"{NS}:block/candle_stand_{c}" + ("_lit" if lit == "true" else "")}
bs("candle_stand", variants)
block_item("candle_stand", "candle_stand_gold_lit")

# bell pedestal
ped = [box([4, 0, 4], [12, 3, 12], {"up": face("#d", [2, 2, 14, 14]), "down": face("#d"), "north": face("#d", [0, 13, 16, 16]),
                                    "south": face("#d", [0, 13, 16, 16]), "east": face("#d", [0, 13, 16, 16]), "west": face("#d", [0, 13, 16, 16])}),
       box([5, 3, 5], [11, 5, 11], {"up": face("#d", [4, 4, 12, 12]), "north": face("#d", [4, 6, 12, 8]), "south": face("#d", [4, 6, 12, 8]),
                                    "east": face("#d", [4, 6, 12, 8]), "west": face("#d", [4, 6, 12, 8])})]
bell = [box([6.5, 5, 6.5], [9.5, 8.5, 9.5], all_faces("#b")), box([7.5, 8.5, 7.5], [8.5, 9.5, 8.5], all_faces("#b"))]
model("suzu_dai", {"textures": {"particle": tex("suzu_dai"), "d": tex("suzu_dai"), "b": tex("suzu_bell")}, "elements": ped + bell})
model("suzu_dai_empty", {"textures": {"particle": tex("suzu_dai"), "d": tex("suzu_dai"), "b": tex("suzu_bell")}, "elements": ped})
bs("suzu_dai", {"has_bell=true": {"model": f"{NS}:block/suzu_dai"}, "has_bell=false": {"model": f"{NS}:block/suzu_dai_empty"}})
block_item("suzu_dai")

model("salt_pile", {"textures": {"particle": tex("salt_pile"), "t": tex("salt_pile")}, "elements": [
    box([4, 0, 4], [12, 3, 12], all_faces("#t")), box([5.5, 3, 5.5], [10.5, 5, 10.5], all_faces("#t")),
    box([7, 5, 7], [9, 6, 9], all_faces("#t"))]})
bs("salt_pile", {"": {"model": f"{NS}:block/salt_pile"}})
block_item("salt_pile")

# ------------------------------------------------------------------ items
for name in ["chochin", "chochin_lit", "lamp_oil", "suzu", "shio", "ofuda", "ohajiki", "kura_key", "doll_head", "matches",
             "note", "red_envelope", "omamori", "ash"]:
    flat_item(name, f"{NS}:item/{name}")
for egg in ["hanayome_spawn_egg", "ichimatsu_spawn_egg", "kageboshi_spawn_egg"]:
    item(egg, {"parent": "minecraft:item/template_spawn_egg"})

print("assets written to", ROOT)
