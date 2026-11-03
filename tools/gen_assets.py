#!/usr/bin/env python3
"""Generates the blockstates, the world's block models and the block item models of HITORI (docs/DESIGN.md §7).

World (this script) writes: every blockstate; the models of the plain shapes (the surface cubes, the tatami, the roof's
stairs and slab, the two-sided walls, the genkan's wooden half step, the shelf board under the closet's futons, the
power wires), the vanilla-style doors (front_door, interior_door, toilet_door, bath_door, unit_door), the sliding glass
balcony_door, and every block item model.
Art writes every other block model (prop models facing north; wall-mounted ones have the wall on their south side)
under the names referenced here -- never written here. Naming: boolean p -> <id> / <id>_<p>; int -> <id>_<n>;
half/part first (fusuma_<half>_<open>, bathtub_<part>_<water>, bed_<part>); several booleans in the order of the
blockstate (laptop_block_open_on, hanging_lamp_lit_swing); crt_tv / crt_tv_on_<channel>; bathtub_<part>_lid.
Internal properties (world-only, mapped onto the same art models): fridge_old.on (powered), breaker_panel.lit (the
breaker is on), futon_folded.shelf / cardboard_box.shelf (the world adds its shelf board under them).
Run from anywhere: python3 tools/gen_assets.py   (add --list to print the art model names the blockstates use)
"""
import itertools
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "hitori"))
NS = "hitori"
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
WRITTEN_MODELS = set()
ART_MODELS = set()
B = ["false", "true"]


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def t(name):
    return name if ":" in name else f"{NS}:block/{name}"


def m(name):
    return f"{NS}:block/{name}"


def bs(name, variants):
    write(f"blockstates/{name}.json", {"variants": variants})


def model(name, data):
    WRITTEN_MODELS.add(name)
    write(f"models/block/{name}.json", data)


def item_parent(name, parent_model):
    write(f"models/item/{name}.json", {"parent": m(parent_model)})


def flat_item(name, texture):
    write(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def var(model_name, y=0, x=0, uvlock=False):
    v = {"model": m(model_name)}
    if x:
        v["x"] = x
    if y:
        v["y"] = y
    if uvlock:
        v["uvlock"] = True
    return v


def art(name):
    ART_MODELS.add(name)
    return name


# ------------------------------------------------------------------------------------------------ plain shapes
def cube_all(name, texture=None, render_type=None):
    d = {"parent": "minecraft:block/cube_all", "textures": {"all": t(texture or name)}}
    if render_type:
        d["render_type"] = render_type
    model(name, d)
    bs(name, {"": var(name)})
    item_parent(name, name)


def tatami(name):
    model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": t(name), "side": t(name), "bottom": t("oshiire_floor")}})
    bs(name, {"axis=z": var(name), "axis=x": var(name, 90)})
    item_parent(name, name)


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
                mdl = base + "_stairs" if shape == "straight" else base + ("_stairs_inner" if shape.startswith("inner") else "_stairs_outer")
                y = rot[f]
                if shape in ("inner_left", "outer_left"):
                    y = (y + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape != "straight":
                        y = (y + 90) % 360
                variants[f"facing={f},half={half},shape={shape}"] = var(mdl, y, x, uvlock=bool(x or y))
    bs(base + "_stairs", variants)
    item_parent(base + "_stairs", base + "_stairs")
    model(base + "_slab", {"parent": "minecraft:block/slab", "textures": {"bottom": tx, "top": tx, "side": tx}})
    model(base + "_slab_top", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tx, "top": tx, "side": tx}})
    bs(base + "_slab", {"type=bottom": var(base + "_slab"), "type=top": var(base + "_slab_top"), "type=double": var(base)})
    item_parent(base + "_slab", base + "_slab")


def low_block(name, height, side, top):
    uv = [0, 16 - height, 16, 16]
    model(name, {"parent": "minecraft:block/block", "textures": {"particle": t(top), "side": t(side), "top": t(top)},
                 "elements": [{"from": [0, 0, 0], "to": [16, height, 16], "faces": {
                     "up": {"texture": "#top"},
                     "down": {"texture": "#top", "cullface": "down"},
                     "north": {"uv": uv, "texture": "#side", "cullface": "north"},
                     "south": {"uv": uv, "texture": "#side", "cullface": "south"},
                     "east": {"uv": uv, "texture": "#side", "cullface": "east"},
                     "west": {"uv": uv, "texture": "#side", "cullface": "west"}}}]})
    bs(name, {"": var(name)})
    item_parent(name, name)


def door(name, textures=None, render_type="minecraft:cutout"):
    """Vanilla door models and blockstate (textures = (bottom, top))."""
    lo, hi = textures or (t(name + "_bottom"), t(name + "_top"))
    for kind in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right",
                 "top_right_open"):
        model(f"{name}_{kind}", {"parent": f"minecraft:block/door_{kind}", "render_type": render_type,
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
                    variants[f"facing={f},half={half},hinge={hinge},open={opened}"] = var(f"{name}_{part}", y)
    bs(name, variants)


def balcony_door():
    """The aluminium sliding glass door: closed on the outer track, open slid 12 px toward +x on the inner track."""
    for half, tex in (("lower", "balcony_door_bottom"), ("upper", "balcony_door_top")):
        for opened in (False, True):
            frm, to = ([12, 0, 9], [28, 16, 10]) if opened else ([0, 0, 7.5], [16, 16, 8.5])
            el = {"from": frm, "to": to, "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#pane"}, "south": {"uv": [0, 0, 16, 16], "texture": "#pane"},
                "east": {"uv": [15, 0, 16, 16], "texture": "#pane"}, "west": {"uv": [0, 0, 1, 16], "texture": "#pane"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#pane"}, "down": {"uv": [0, 15, 16, 16], "texture": "#pane"}}}
            # the track (rail) in the sill / the lintel stays in the wall
            rail = {"from": [0, 0 if half == "lower" else 15.5, 7], "to": [16, 0.5 if half == "lower" else 16, 10.5], "faces": {
                f: {"uv": [0, 0, 16, 1], "texture": "#pane"} for f in ("north", "south", "east", "west", "up", "down")}}
            name = f"balcony_door_{half}" + ("_open" if opened else "")
            model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                         "textures": {"pane": t(tex), "particle": t(tex)}, "elements": [el, rail]})
    v = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for op in B:
                v[f"facing={f},half={half},open={op}"] = var(f"balcony_door_{half}" + ("_open" if op == "true" else ""), y)
    bs("balcony_door", v)
    flat_item("balcony_door", f"{NS}:item/balcony_door")


# ------------------------------------------------------------------------------------------------ art props
def facing_prop(name, extras=(), name_fn=None, item=None, rotate=True, x_fn=None):
    """A facing prop drawn by an art model. extras: [(property, [values])]; name_fn(dict) -> model name."""
    if name_fn is None:
        def name_fn(kv):
            n = name
            for p, _ in extras:
                if kv[p] == "true":
                    n += "_" + p
            return n
    variants = {}
    props = [p for p, _ in extras]
    for f, y in FACING_Y.items():
        for combo in itertools.product(*[vals for _, vals in extras]):
            kv = dict(zip(props, combo))
            key = ",".join([f"facing={f}"] + [f"{p}={v}" for p, v in kv.items()])
            variants[key] = var(art(name_fn(kv)), y if rotate else 0, x_fn(kv) if x_fn else 0)
    bs(name, variants)
    default = {p: vals[0] for p, vals in extras}
    for p, vals in extras:
        if "false" in vals:
            default[p] = "false"
    item_parent(name, item or name_fn(default))


def shelf_prop(name):
    """futon_folded / cardboard_box: the art model; shelf=true adds the world's board under it (the closet's middle shelf)."""
    parts = []
    for f, y in FACING_Y.items():
        parts.append({"when": {"facing": f}, "apply": var(art(name), y)})
    parts.append({"when": {"shelf": "true"}, "apply": var("oshiire_shelf_board")})
    write(f"blockstates/{name}.json", {"multipart": parts})
    item_parent(name, name)


def main():
    listing = "--list" in sys.argv
    # ---- surfaces (world models)
    for n in ("old_flooring", "vinyl_floor", "genkan_tile", "bath_tile_floor", "bath_tile_wall", "plaster_wall", "plaster_wall_stained",
              "wallpaper_old", "wood_wainscot", "ceiling_board", "ceiling_board_stained", "oshiire_floor", "apartment_siding",
              "apartment_door_metal", "corridor_concrete", "concrete", "asphalt_wet", "block_wall", "roof_slate"):
        cube_all(n)
    tatami("tatami")
    tatami("tatami_worn")
    stairs_slab("roof_slate", "roof_slate")
    low_block("genkan_step", 8, "wood_wainscot", "old_flooring")
    # two-sided walls: the face toward facing shows "inner", the others "outer"
    faces = ["plaster_wall", "plaster_wall_stained", "wallpaper_old", "wood_wainscot", "bath_tile_wall", "oshiire_floor", "apartment_siding",
             "concrete"]
    variants = {}
    for inner in faces:
        for outer in faces:
            name = f"two_sided_wall_{inner}__{outer}"
            model(name, {"parent": "minecraft:block/cube", "textures": {
                "particle": t(outer), "north": t(inner), "south": t(outer), "east": t(outer), "west": t(outer), "up": t(outer), "down": t(outer)}})
            for f, y in FACING_Y.items():
                variants[f"facing={f},inner={inner},outer={outer}"] = var(name, y)
    bs("two_sided_wall", variants)
    item_parent("two_sided_wall", "two_sided_wall_plaster_wall__apartment_siding")
    # the closet's middle shelf: a plywood board under the futons (1 px into the block below, so it never z-fights them)
    model("oshiire_shelf_board", {"parent": "minecraft:block/block", "textures": {"t": t("oshiire_floor"), "particle": t("oshiire_floor")},
                                  "elements": [{"from": [0, -1, 0], "to": [16, 1, 16], "faces": {
                                      f: {"texture": "#t", "uv": [0, 0, 16, 2] if f not in ("up", "down") else [0, 0, 16, 16]}
                                      for f in ("north", "south", "east", "west", "up", "down")}}]})
    # the wires between the utility poles: four thin black lines along the block (north-south at rest), on the
    # insulators of the art's utility_pole_top (its crossarm at model x -10, -3, 19, 26, insulator tops at y 13.2);
    # each span reaches half a block into its neighbours so the last one meets the pole
    wires = []
    for x0 in (-10.15, -3.15, 18.85, 25.85):
        wires.append({"from": [x0, 13.0, -8], "to": [x0 + 0.3, 13.3, 24], "shade": False, "faces": {
            f: {"uv": [0, 0, 1, 1], "texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")}})
    model("power_wire", {"parent": "minecraft:block/block", "textures": {"t": "minecraft:block/black_concrete", "particle": "minecraft:block/black_concrete"},
                         "elements": wires})
    bs("power_wire", {f"facing={f}": var("power_wire", y) for f, y in FACING_Y.items()})
    item_parent("power_wire", "power_wire")
    # clutter the art list does not have (world models from vanilla textures; art may replace them under the same names)
    def box(frm, to, tex, uv=None):
        return {"from": frm, "to": to, "faces": {f: ({"texture": tex, "uv": uv} if uv else {"texture": tex})
                                                 for f in ("north", "south", "east", "west", "up", "down")}}
    model("laundry_hanging", {"parent": "minecraft:block/block", "textures": {
        "bar": "minecraft:block/iron_block", "towel": "minecraft:block/white_wool", "shirt": "minecraft:block/light_blue_wool",
        "sock": "minecraft:block/gray_wool", "particle": "minecraft:block/white_wool"},
        "elements": [box([2, 15, 7.6], [14, 15.6, 8.4], "#bar"), box([7.6, 15.6, 7.8], [8.4, 16, 8.2], "#bar"),
                     box([2.5, 4, 7.85], [7.2, 15, 8.15], "#towel"), box([8.6, 6, 7.65], [13.6, 15, 7.95], "#shirt"),
                     box([7.8, 11.5, 7.65], [14.4, 15, 7.95], "#shirt"), box([5, 1, 8.2], [6.2, 6.5, 8.6], "#sock")]})
    bs("laundry_hanging", {f"facing={f}": var("laundry_hanging", y) for f, y in FACING_Y.items()})
    item_parent("laundry_hanging", "laundry_hanging")
    model("trash_bag", {"parent": "minecraft:block/block", "textures": {
        "bag": "minecraft:block/white_concrete_powder", "tie": "minecraft:block/yellow_concrete_powder", "particle": "minecraft:block/white_concrete_powder"},
        "elements": [box([3, 0, 3], [13, 7, 13], "#bag"), box([4, 7, 4], [12, 9, 12], "#bag"), box([6.5, 9, 6.5], [9.5, 10.5, 9.5], "#tie"),
                     box([5.5, 10.5, 7], [7, 12, 8.5], "#tie"), box([9, 10.5, 7.5], [10.5, 12, 9], "#tie")]})
    bs("trash_bag", {f"facing={f}": var("trash_bag", y) for f, y in FACING_Y.items()})
    item_parent("trash_bag", "trash_bag")
    # art-modelled surfaces
    bs("puddle", {"": var(art("puddle"))})
    item_parent("puddle", "puddle")
    facing_prop("rain_window")

    # ---- doors
    door("front_door")  # the art's plaque 「二〇三」 reads from both faces of the vanilla model
    door("interior_door")
    door("toilet_door")
    door("bath_door", render_type="minecraft:translucent")
    door("unit_door", textures=(t("apartment_door_metal"), t("apartment_door_metal")))
    for d in ("front_door", "interior_door", "toilet_door", "bath_door", "fusuma", "oshiire_fusuma"):
        flat_item(d, f"{NS}:item/{d}")
    flat_item("unit_door", f"{NS}:block/apartment_door_metal")
    for d in ("fusuma", "oshiire_fusuma"):
        v = {}
        for f, y in FACING_Y.items():
            for half in ("lower", "upper"):
                for op in ("0", "1", "2"):
                    v[f"facing={f},half={half},open={op}"] = var(art(f"{d}_{half}_{op}"), y)
        bs(d, v)
    balcony_door()

    # ---- props (art models, facing north at rest)
    plain = ["chabudai", "zabuton", "tv_stand", "pull_switch", "altar_shelf", "shower", "bath_stool", "bucket", "mirror_small", "toilet_seat",
             "rice_bag", "rice_cooker", "salt_shaker", "cup_glass", "microwave", "dining_table", "dining_chair", "desk", "desk_chair", "printout",
             "shelf", "camera_bag", "poster", "shoe_cabinet", "shoes", "umbrella", "calendar", "wall_clock", "laundry_pole", "ac_outdoor_unit",
             "vending_machine_small", "mailboxes", "bicycle", "stairs_rail", "vacancy_sign", "swing_set"]
    for n in plain:
        facing_prop(n)
    shelf_prop("futon_folded")
    shelf_prop("cardboard_box")
    facing_prop("crt_tv", [("on", B), ("channel", ["0", "1", "2"])],
                name_fn=lambda kv: f"crt_tv_on_{kv['channel']}" if kv["on"] == "true" else "crt_tv", item="crt_tv_on_0")
    facing_prop("hanging_lamp", [("lit", B), ("swing", B)], item="hanging_lamp_lit")
    facing_prop("fluorescent_ring", [("lit", B)], item="fluorescent_ring_lit")
    facing_prop("light_switch", [("on", B)])
    facing_prop("curtain", [("closed", B)], item="curtain_closed")
    facing_prop("bathtub", [("part", ["head", "foot"]), ("water", ["0", "1", "2"]), ("lid", B)],
                name_fn=lambda kv: f"bathtub_{kv['part']}_lid" if kv["lid"] == "true" else f"bathtub_{kv['part']}_{kv['water']}", item="bathtub_head_2")
    facing_prop("bath_tap", [("running", B)])
    facing_prop("washing_machine", [("running", B)])
    facing_prop("washbasin", [("running", B)])
    facing_prop("kitchen_sink", [("running", B)])
    facing_prop("gas_stove", [("lit", B)])
    facing_prop("fridge_old", [("open", B), ("on", B)], name_fn=lambda kv: "fridge_old_open" if kv["open"] == "true" else "fridge_old")
    facing_prop("knife_rack", [("has_knife", B)], item="knife_rack_has_knife")
    facing_prop("drawer", [("open", B)])
    facing_prop("laptop_block", [("open", B), ("on", B)], item="laptop_block_open_on")
    facing_prop("sewing_box", [("open", B)])
    facing_prop("bed", [("part", ["head", "foot"])], name_fn=lambda kv: f"bed_{kv['part']}", item="bed_head")
    facing_prop("plush_doll", [("present", B)], item="plush_doll_present")
    facing_prop("desk_lamp", [("lit", B)], item="desk_lamp_lit")
    facing_prop("breaker_panel", [("open", B), ("lit", B)], name_fn=lambda kv: "breaker_panel_open" if kv["open"] == "true" else "breaker_panel")
    facing_prop("intercom", [("ringing", B)])
    facing_prop("street_lamp_old", [("lit", B)], item="street_lamp_old_lit")
    facing_prop("utility_pole", [("top", B)], item="utility_pole_top")
    facing_prop("nameplate", [("unit", [str(i) for i in range(8)])], name_fn=lambda kv: f"nameplate_{kv['unit']}", item="nameplate_6")

    clash = WRITTEN_MODELS & ART_MODELS
    if clash:
        raise SystemExit(f"world would overwrite art models: {sorted(clash)}")
    if listing:
        for n in sorted(ART_MODELS):
            print(n)
    print(f"assets written to {ROOT}: {len(WRITTEN_MODELS)} world models, {len(ART_MODELS)} art model names referenced")


if __name__ == "__main__":
    main()
