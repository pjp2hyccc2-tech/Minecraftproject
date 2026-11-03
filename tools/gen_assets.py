#!/usr/bin/env python3
"""Generates the blockstates, the plain block models and the block item models of HITORI (docs/DESIGN.md §7.1).

World (this script) writes: every blockstate; the models of the plain shapes (cubes, the ceiling light / downlight
panels, roof stairs and slab, the 1 px mat and puddle, the hedge, the painted lines, the curb and the brick sill, the
shop window glass, the two-sided interior walls, the vanilla-style doors and an invisible model); every block item model.
Art writes every other block model (prop models facing north) under the names referenced here -- never written here.
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


# ------------------------------------------------------------------------------------------------ plain shapes
def cube_all(name, texture=None, render_type=None):
    d = {"parent": "minecraft:block/cube_all", "textures": {"all": t(texture or name)}}
    if render_type:
        d["render_type"] = render_type
    model(name, d)
    bs(name, {"": var(name)})
    item_parent(name, name)


def panel_light(name):
    """A ceiling block whose bottom face is the light panel (lit / unlit textures), ceiling tile elsewhere."""
    for suffix, tex in (("", name), ("_lit", name + "_lit")):
        model(name + suffix, {"parent": "minecraft:block/cube",
                              "textures": {"particle": t("ceiling_tile"), "down": t(tex), "up": t("ceiling_tile"),
                                           "north": t("ceiling_tile"), "south": t("ceiling_tile"), "east": t("ceiling_tile"),
                                           "west": t("ceiling_tile")}})


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


def low_block(name, height, side, top, side_uv_top=True):
    uv = [0, 0, 16, height] if side_uv_top else [0, 16 - height, 16, 16]
    model(name, {"parent": "minecraft:block/block", "textures": {"particle": t(side), "side": t(side), "top": t(top)},
                 "elements": [{"from": [0, 0, 0], "to": [16, height, 16], "faces": {
                     "up": {"texture": "#top", "cullface": "up"} if height == 16 else {"texture": "#top"},
                     "down": {"texture": "#side", "cullface": "down"},
                     "north": {"uv": uv, "texture": "#side", "cullface": "north"},
                     "south": {"uv": uv, "texture": "#side", "cullface": "south"},
                     "east": {"uv": uv, "texture": "#side", "cullface": "east"},
                     "west": {"uv": uv, "texture": "#side", "cullface": "west"}}}]})
    bs(name, {"": var(name)})
    item_parent(name, name)


def line_block(name):
    model(name, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": t(name), "side": t("asphalt"), "bottom": t("asphalt")}})
    bs(name, {"axis=z": var(name), "axis=x": var(name, 90)})
    item_parent(name, name)


def door(name, textures=None, extra=None):
    """Vanilla door models; extra = (property, value) added to every variant key, textures = (bottom, top)."""
    lo, hi = textures or (t(name + "_bottom"), t(name + "_top"))
    prefix = name if not extra else f"{name}_{extra[0]}"
    for kind in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right",
                 "top_right_open"):
        model(f"{prefix}_{kind}", {"parent": f"minecraft:block/door_{kind}", "render_type": "minecraft:cutout",
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
                    key = f"facing={f},half={half},hinge={hinge},open={opened}"
                    if extra:
                        key += f",{extra[1]}"
                    variants[key] = var(f"{prefix}_{part}", y)
    return variants


# ------------------------------------------------------------------------------------------------ art props
def art(name):
    ART_MODELS.add(name)
    return name


def facing_prop(name, extras=(), name_fn=None, item=None, rotate=True, x_fn=None):
    """A facing prop drawn by an art model. extras: [(property, [values])]; name_fn(dict) -> model name;
    x_fn(dict) -> x rotation (a floor decal turned up against the wall behind it: x 90 puts the model's bottom on the south)."""
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
    default = {p: vals[0] if vals[0] not in ("true",) else "false" for p, vals in extras}
    for p, vals in extras:
        if "false" in vals:
            default[p] = "false"
    item_parent(name, item or name_fn(default))


B = ["false", "true"]


def main():
    listing = "--list" in sys.argv
    # ---- surfaces and structure (world models)
    for n in ("dining_floor", "dining_floor_worn", "kitchen_floor", "restroom_floor", "restroom_floor_dark", "vestibule_floor", "wallpaper",
              "wallpaper_stained", "wainscot", "white_tile", "white_tile_dirty", "ceiling_tile", "ceiling_tile_stained", "exterior_wall",
              "facade_brick", "roof_tile", "fascia", "asphalt", "sidewalk", "concrete_wall", "granite_counter", "stainless"):
        cube_all(n)
    model("pillar", {"parent": "minecraft:block/cube_column", "textures": {"side": t("pillar"), "end": t("pillar_top")}})
    bs("pillar", {"": var("pillar")})
    item_parent("pillar", "pillar")
    model("rice_paddy", {"parent": "minecraft:block/cube_bottom_top",
                         "textures": {"top": t("rice_paddy"), "side": t("rice_paddy_side"), "bottom": t("rice_paddy_side")}})
    bs("rice_paddy", {"": var("rice_paddy")})
    item_parent("rice_paddy", "rice_paddy")
    cube_all("shop_window", render_type="minecraft:translucent")
    model("hedge", {"parent": "minecraft:block/leaves", "render_type": "minecraft:cutout_mipped", "textures": {"all": t("hedge")}})
    bs("hedge", {"": var("hedge")})
    item_parent("hedge", "hedge")
    # 1 px layers (carpet shape)
    model("entrance_mat", {"parent": "minecraft:block/carpet", "textures": {"wool": t("entrance_mat"), "particle": t("entrance_mat")}})
    bs("entrance_mat", {f"facing={f}": var("entrance_mat", y) for f, y in FACING_Y.items()})
    item_parent("entrance_mat", "entrance_mat")
    model("puddle", {"parent": "minecraft:block/carpet", "render_type": "minecraft:translucent",
                     "textures": {"wool": t("puddle"), "particle": t("puddle")}})
    bs("puddle", {"": var("puddle")})
    item_parent("puddle", "puddle")
    # lights
    panel_light("ceiling_light")
    bs("ceiling_light", {f"lit={lit},broken={br}": var("ceiling_light_lit" if lit == "true" else "ceiling_light") for lit in B for br in B})
    item_parent("ceiling_light", "ceiling_light_lit")
    panel_light("downlight")
    for suffix, tex in (("", "downlight"), ("_lit", "downlight_lit")):     # set into the eave's fascia band outside
        model("downlight_fascia" + suffix, {"parent": "minecraft:block/cube",
                                            "textures": {"particle": t("fascia"), "down": t(tex), "up": t("fascia"), "north": t("fascia"),
                                                         "south": t("fascia"), "east": t("fascia"), "west": t("fascia")}})
    bs("downlight", {f"lit={lit},fascia={fa}": var(("downlight_fascia" if fa == "true" else "downlight") + ("_lit" if lit == "true" else ""))
                     for lit in B for fa in B})
    item_parent("downlight", "downlight_lit")
    bs("pendant_lamp", {f"lit={lit}": var(art("pendant_lamp_lit" if lit == "true" else "pendant_lamp")) for lit in B})
    item_parent("pendant_lamp", "pendant_lamp_lit")
    facing_prop("exit_sign", [("hanging", B)])
    stairs_slab("roof_tile", "roof_tile")
    low_block("brick_sill", 10, "brick_sill", "brick_sill_top")
    low_block("curb", 8, "curb", "curb_top")
    line_block("parking_line")
    line_block("road_line")
    facing_prop("window_blind", [("closed", B)], item="window_blind_closed")
    # two-sided walls: the face toward facing shows "inner", the others "outer"
    faces = ["wallpaper", "wallpaper_stained", "wainscot", "white_tile", "white_tile_dirty", "stainless", "concrete_wall", "exterior_wall",
             "facade_brick"]
    variants = {}
    for inner in faces:
        for outer in faces:
            name = f"two_sided_wall_{inner}__{outer}"
            model(name, {"parent": "minecraft:block/cube", "textures": {
                "particle": t(outer), "north": t(inner), "south": t(outer), "east": t(outer), "west": t(outer), "up": t(outer), "down": t(outer)}})
            for f, y in FACING_Y.items():
                variants[f"facing={f},inner={inner},outer={outer}"] = var(name, y)
    bs("two_sided_wall", variants)
    item_parent("two_sided_wall", "two_sided_wall_wallpaper__exterior_wall")
    # an invisible model (the panels of the automatic doors and the upper half of the swing doors are drawn by renderers)
    model("world_invisible", {"textures": {"particle": t("auto_door_panel")}, "elements": []})

    # ---- doors
    v = {}
    for f, y in FACING_Y.items():
        for side in ("left", "right"):
            for part in ("bottom", "middle", "top"):
                for op in B:
                    v[f"facing={f},side={side},part={part},open={op}"] = var(art("auto_door_rail") if part == "top" else "world_invisible",
                                                                             y if part == "top" else 0)
    bs("auto_door", v)
    flat_item("auto_door", f"{NS}:item/auto_door")
    facing_prop("door_switch", [("on", B)])
    v = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for side in ("left", "right"):
                for sw in ("0", "1", "2"):
                    v[f"facing={f},half={half},side={side},swing={sw}"] = var(art("swing_door_frame") if half == "lower" else "world_invisible",
                                                                             y if half == "lower" else 0)
    bs("swing_door", v)
    flat_item("swing_door", f"{NS}:item/swing_door")
    for d in ("staff_door", "office_door", "restroom_door_m", "restroom_door_w", "back_door", "fridge_door"):
        bs(d, door(d))
        flat_item(d, f"{NS}:item/{d}")
    sv = door("stall_door", extra=("vacant", "occupied=false"))
    sv.update(door("stall_door", textures=(t("stall_door_occupied_bottom"), t("stall_door_occupied_top")), extra=("occupied", "occupied=true")))
    bs("stall_door", sv)
    flat_item("stall_door", f"{NS}:item/stall_door")
    # the power lines between the utility poles: the same four wires as the art's pole top (texture and UVs), a full block long
    wires = []
    for x0 in (-10.25, -3.25, 18.75, 25.75):
        wires.append({"from": [x0, 12.2, 0], "to": [x0 + 0.5, 12.6, 16], "faces": {
            "north": {"uv": [4.0, 13.25, 4.1667, 13.35], "texture": "#t"}, "south": {"uv": [4.0, 13.25, 4.1667, 13.35], "texture": "#t"},
            "east": {"uv": [0.0, 13.0, 16.0, 13.1], "texture": "#t"}, "west": {"uv": [0.0, 13.0, 16.0, 13.1], "texture": "#t"},
            "up": {"uv": [0.0, 0.0, 0.1667, 12.0], "texture": "#t"}, "down": {"uv": [0.0, 0.0, 0.1667, 12.0], "texture": "#t"}}})
    model("power_wire", {"parent": "minecraft:block/block", "textures": {"t": t("utility_pole"), "particle": t("utility_pole")},
                         "elements": wires})
    bs("power_wire", {f"facing={f}": var("power_wire", y) for f, y in FACING_Y.items()})
    item_parent("power_wire", "power_wire")

    # ---- furniture and fixtures (art models, facing north at rest)
    plain = ["booth_seat", "booth_partition", "restaurant_table", "menu_stand", "broken_plate", "service_bell", "counter", "cake_case",
             "reservation_book", "capsule_machine", "waiting_bench", "magazine_rack", "umbrella_stand", "notice_board", "coffee_machine",
             "ice_machine", "cup_shelf", "soup_warmer", "server_station", "planter", "kids_mat", "toy_shelf", "stuffed_bear", "kids_fence",
             "high_chair", "griddle", "kitchen_counter", "exhaust_hood", "pot_shelf", "dish_rack", "kitchen_trash", "prep_table", "cutting_board",
             "pass_bell", "walkin_shelf", "frost", "time_recorder", "staff_table", "folding_chair", "bulletin_board", "cardboard_boxes",
             "cleaning_cart", "filing_cabinet", "safe", "office_desk", "office_chair", "pc", "restroom_mirror", "toilet", "urinal", "stall_wall",
             "paper_towel", "bike_rack", "trash_station", "ac_unit", "gas_cylinder", "dumpster", "wheel_stop", "outdoor_planter", "guardrail",
             "chevron_sign", "curve_mirror", "flower_offering"]
    for n in plain:
        facing_prop(n)
    # a note lies on a counter, or is taped to the wall behind it (hanging: the decal turned up, x 90)
    facing_prop("note_paper", [("hanging", B)], name_fn=lambda kv: "note_paper", x_fn=lambda kv: 90 if kv["hanging"] == "true" else 0)
    facing_prop("restaurant_chair", [("fallen", B)])
    facing_prop("call_button", [("lit", B)])
    facing_prop("register", [("open", B)])
    facing_prop("business_phone", [("ringing", B)])
    facing_prop("drink_bar", [("pouring", B)])
    facing_prop("gas_range", [("lit", B)])
    facing_prop("fryer", [("on", B)])
    facing_prop("kitchen_sink", [("running", B)])
    facing_prop("dishwasher", [("running", B)])
    facing_prop("pass_shelf", [("lit", B)])
    facing_prop("crt_tv", [("on", B)])
    facing_prop("breaker_panel", [("open", B)])
    facing_prop("restroom_sink", [("running", B)])
    facing_prop("mirror", [("cracked", B)])
    facing_prop("hand_dryer", [("running", B)])
    facing_prop("traffic_light", [("lit", B)], item="traffic_light_lit")
    facing_prop("security_monitors", [("on", B), ("alert", B)],
                name_fn=lambda kv: "security_monitors" if kv["on"] == "false" else ("security_monitors_on_alert" if kv["alert"] == "true" else "security_monitors_on"),
                item="security_monitors_on")
    facing_prop("security_camera", [("on", B), ("seen", B)],
                name_fn=lambda kv: "security_camera" if kv["on"] == "false" else ("security_camera_on_seen" if kv["seen"] == "true" else "security_camera_on"),
                item="security_camera_on")
    facing_prop("menu_scattered", [("variant", ["0", "1", "2", "3"])], name_fn=lambda kv: f"menu_scattered_{kv['variant']}", item="menu_scattered_0")
    facing_prop("water_glass", [("glasses", ["1", "2", "3", "4"])], name_fn=lambda kv: f"water_glass_{kv['glasses']}", item="water_glass_1")
    facing_prop("menu_board", [("message", ["0", "1"])], name_fn=lambda kv: f"menu_board_{kv['message']}", item="menu_board_0")
    facing_prop("call_display", [("number", [str(i) for i in range(18)]), ("closed", B)],
                name_fn=lambda kv: "call_display_closed" if kv["closed"] == "true" else f"call_display_{kv['number']}", item="call_display_0")
    facing_prop("locker", [("half", ["lower", "upper"]), ("open", B)],
                name_fn=lambda kv: f"locker_{kv['half']}" + ("_open" if kv["open"] == "true" else ""), item="locker_lower")
    facing_prop("vending_machine", [("half", ["lower", "upper"])], name_fn=lambda kv: f"vending_machine_{kv['half']}", item="vending_machine_lower")
    facing_prop("street_lamp", [("part", ["pole", "head"]), ("lit", B)],
                name_fn=lambda kv: "street_lamp_pole" if kv["part"] == "pole" else ("street_lamp_head_lit" if kv["lit"] == "true" else "street_lamp_head"),
                item="street_lamp_head_lit")
    facing_prop("utility_pole", [("part", ["pole", "top"])], name_fn=lambda kv: f"utility_pole_{kv['part']}", item="utility_pole_top")
    facing_prop("pole_sign", [("part", ["pole", "tile"]), ("tile", [str(i) for i in range(16)]), ("lit", B)],
                name_fn=lambda kv: "pole_sign_pole" if kv["part"] == "pole" else f"pole_sign_tile_{kv['tile']}" + ("_lit" if kv["lit"] == "true" else ""),
                item="pole_sign_tile_0_lit")
    facing_prop("roof_sign", [("tile", [str(i) for i in range(8)]), ("lit", B)],
                name_fn=lambda kv: f"roof_sign_tile_{kv['tile']}" + ("_lit" if kv["lit"] == "true" else ""), item="roof_sign_tile_0_lit")

    clash = WRITTEN_MODELS & ART_MODELS
    if clash:
        raise SystemExit(f"world would overwrite art models: {sorted(clash)}")
    if listing:
        for n in sorted(ART_MODELS):
            print(n)
    print(f"assets written to {ROOT}: {len(WRITTEN_MODELS)} world models, {len(ART_MODELS)} art model names referenced")


if __name__ == "__main__":
    main()
