"""The preview's HIDAMARI: an approximation of the restaurant of docs/DESIGN.md section 2 (floor
plan, rooms, the lot and the road) built from the art's block models where they exist and simple
stand-in models where they do not. Circuits: a dict of which lights are on (like the breaker).
"""
import math

from mcscene import Block, World

FEET = 65           # feet level; floor blocks at 64, ceiling at 69


# ---- stand-in models for props the art has not modelled yet (same JSON format) -------------------
STANDINS = {
    "vending_machine_lower": {"textures": {"f": "vending_machine_front", "s": "vending_machine_side"},
                              "elements": [{"from": [0, 0, 1], "to": [16, 16, 16], "faces": {
                                  "north": {"texture": "#f"}, "south": {"texture": "#s"}, "east": {"texture": "#s"},
                                  "west": {"texture": "#s"}, "up": {"texture": "#s"}, "down": {"texture": "#s"}}}]},
    "vending_machine_upper": {"textures": {"f": "vending_machine_front", "s": "vending_machine_side"},
                              "elements": [{"from": [0, 0, 1], "to": [16, 16, 16], "faces": {
                                  "north": {"texture": "#f"}, "south": {"texture": "#s"}, "east": {"texture": "#s"},
                                  "west": {"texture": "#s"}, "up": {"texture": "#s"}, "down": {"texture": "#s"}}}]},
    "pole_sign_pole": {"textures": {"p": "pole_sign_pole"},
                       "elements": [{"from": [5, 0, 5], "to": [11, 16, 11], "faces": {
                           k: {"texture": "#p"} for k in ("north", "south", "east", "west")}}]},
    "pole_sign_tile_lit": {"textures": {"f": "pole_sign_face", "e": "pole_sign_pole"},
                           "elements": [{"from": [0, 0, 4], "to": [16, 16, 12], "faces": {
                               "north": {"texture": "#f"}, "south": {"texture": "#f"},
                               "east": {"texture": "#e"}, "west": {"texture": "#e"}, "up": {"texture": "#e"}, "down": {"texture": "#e"}}}]},
    "street_lamp_pole": {"textures": {"p": "street_lamp_pole"},
                         "elements": [{"from": [6, 0, 6], "to": [10, 16, 10], "faces": {
                             k: {"texture": "#p"} for k in ("north", "south", "east", "west")}}]},
    "street_lamp_head_lit": {"textures": {"h": "street_lamp_head", "p": "street_lamp_pole"},
                             "elements": [{"from": [6, 0, 6], "to": [10, 8, 10], "faces": {
                                 k: {"texture": "#p"} for k in ("north", "south", "east", "west")}},
                                 {"from": [2, 8, 0], "to": [14, 12, 16], "faces": {
                                     "down": {"texture": "#h"}, "up": {"texture": "#p"}, "north": {"texture": "#p"},
                                     "south": {"texture": "#p"}, "east": {"texture": "#p"}, "west": {"texture": "#p"}}}]},
    "traffic_light_lit": {"textures": {"t": "traffic_light"},
                          "elements": [{"from": [3, 0, 4], "to": [13, 16, 12], "faces": {
                              k: {"texture": "#t"} for k in ("north", "south", "east", "west", "up", "down")}}]},
    "puddle": {"render_type": "minecraft:translucent", "textures": {"t": "puddle"},
               "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {"up": {"texture": "#t"}}}]},
    "curb": {"textures": {"t": "curb", "u": "curb_top"},
             "elements": [{"from": [0, 0, 0], "to": [16, 8, 16], "faces": {
                 "up": {"texture": "#u"}, "north": {"texture": "#t"}, "south": {"texture": "#t"},
                 "east": {"texture": "#t"}, "west": {"texture": "#t"}}}]},
    "guardrail": {"render_type": "minecraft:cutout", "textures": {"t": "guardrail"},
                  "elements": [{"from": [0, 4, 7], "to": [16, 12, 9], "faces": {
                      "north": {"texture": "#t"}, "south": {"texture": "#t"}}}]},
}


# ---- block makers ---------------------------------------------------------------------------------
def cube(name, tex=None, opaque=True, light=0, state=None, layer=None):
    return Block(name, state, cube=tex or {"all": name}, opaque=opaque, light=light, layer=layer)


def prop(name, model=None, facing="north", light=0, state=None, opaque=False):
    return Block(name, state, facing=facing, model=model or name, opaque=opaque, light=light)


def build(circuits=None, puddle_seed=7):
    """circuits: dining, kitchen, restroom, back, sign (lot lamps), broken (restroom tube)"""
    c = dict(dining=False, kitchen=False, restroom=True, sign=True, register=True)
    c.update(circuits or {})
    w = World(-12, 58, -8, 76, 30, 80)

    # ---- ground: the lot, the road, fields -----------------------------------------------------
    import random
    rnd = random.Random(puddle_seed)
    for x in range(-12, 64):
        for z in range(-8, 72):
            w.set(x, 63, z, cube("concrete_wall"))
            if 31 <= z <= 48 or 50 <= z <= 58:
                top = cube("asphalt")
                if 31 < z <= 46 and z != 39 and x % 4 == 0 and 8 <= x <= 40:
                    top = Block("parking_line", {"axis": "z"}, cube={"all": "parking_line"}, opaque=True)
                if z == 54:
                    top = Block("road_line", {"axis": "x"}, cube={"all": "road_line"}, opaque=True)
                w.set(x, 64, z, top)
            elif z == 49 or z == 59:
                w.set(x, 64, z, cube("sidewalk"))
                w.set(x, 65, z, prop("curb", "curb"))
            elif z >= 60 or z < -2 or x < -2 or x > 44:
                w.set(x, 64, z, cube("rice_paddy", {"up": "rice_paddy", "all": "rice_paddy_side"}))
            else:
                w.set(x, 64, z, cube("sidewalk"))
    # puddles in the dips of the lot and along the road
    for _ in range(26):
        cx, cz = rnd.randint(2, 46), rnd.randint(32, 57)
        rx, rz = rnd.uniform(1.0, 3.2), rnd.uniform(0.8, 2.2)
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 < 1.0 and w.get(x, 65, z) is None and z not in (49,):
                    b = w.get(x, 64, z)
                    if b is not None and b.name in ("asphalt", "parking_line", "road_line"):
                        w.set(x, 65, z, prop("puddle", "puddle", state={"layers": "1"}))

    # ---- the building shell ------------------------------------------------------------------------
    for x in range(0, 41):
        for z in range(0, 31):
            w.set(x, 64, z, cube("dining_floor"))
            w.set(x, 69, z, cube("ceiling_tile"))
            w.set(x, 70, z, cube("roof_tile"))
            border = x in (0, 40) or z in (0, 30)
            if border:
                for y in range(65, 69):
                    w.set(x, y, z, cube("exterior_wall") if y > 65 else cube("facade_brick"))
    # roof edge: fascia band
    for x in range(-1, 42):
        for z in (-1, 31):
            w.set(x, 69, z, cube("fascia"))
    for z in range(-1, 32):
        for x in (-1, 41):
            w.set(x, 69, z, cube("fascia"))
    # south shop windows x 9..39 (z 30), east windows z 12..29 (x 40); blinds inside, half closed
    for x in range(9, 40):
        for y in range(65, 69):
            w.set(x, y, 30, cube("shop_window", opaque=False, layer="translucent"))
        # the blinds as the world hangs them: the top row closed, open around tables 5..7,
        # lowered further at some windows
        open_ = 27 <= x <= 35
        w.set(x, 68, 29, prop("window_blind", "window_blind" if open_ else "window_blind_closed", facing="north",
                              state={"closed": "false" if open_ else "true"}))
        if not open_ and x >= 15 and x % 7 in (1, 2, 3):
            w.set(x, 67, 29, prop("window_blind", "window_blind_closed", facing="north", state={"closed": "true"}))
    for z in range(12, 30):
        for y in range(65, 69):
            w.set(40, y, z, cube("shop_window", opaque=False, layer="translucent"))
        if z <= 28:
            w.set(39, 68, z, prop("window_blind", "window_blind_closed", facing="west", state={"closed": "true"}))
            if z % 6 in (0, 1):
                w.set(39, 67, z, prop("window_blind", "window_blind_closed", facing="west", state={"closed": "true"}))
    # the entrance: automatic doors (glass) at x 3..4
    for x in (3, 4):
        for y in range(65, 68):
            w.set(x, y, 30, cube("shop_window", opaque=False, layer="translucent"))
            w.set(x, y, 26, cube("shop_window", opaque=False, layer="translucent"))

    def wall(x0, z0, x1, z1, tex="wallpaper", lower="wainscot"):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for y in range(65, 69):
                    w.set(x, y, z, cube(lower if y == 65 else tex))

    # interior walls (the plan: '#' / '=')
    wall(1, 10, 21, 10)                 # corridor / dining
    wall(24, 10, 28, 10)                # (pass window 22..23 and swing doors 29..30 left open)
    wall(31, 10, 39, 10)
    wall(14, 1, 14, 9)                  # back / kitchen
    wall(14, 11, 14, 21)                # restrooms + cavity / dining
    wall(1, 16, 13, 16, "white_tile", "white_tile")      # cavity / restrooms (mirror at 9..11)
    wall(6, 11, 6, 21, "white_tile", "white_tile")
    wall(1, 22, 13, 22)
    wall(1, 26, 2, 26)
    wall(5, 26, 8, 26)
    wall(8, 27, 8, 29)
    wall(1, 7, 13, 7)
    # the women's restroom (7..13 x 17..21): mosaic floor, tiles, the dark mirror over the sinks
    for x in range(7, 14):
        for z in range(17, 22):
            w.set(x, 64, z, cube("restroom_floor"))
        for z in range(11, 16):
            w.set(x, 64, z, cube("restroom_floor_dark"))
    for z in range(17, 22):
        for y in range(65, 69):
            w.set(14, y, z, cube("white_tile"))
    for x in range(9, 12):
        for y in (66, 67):
            w.set(x, y, 16, prop("mirror", facing="south", state={"cracked": "false"}))
        w.set(x, 65, 17, prop("restroom_sink", facing="south"))
    # the cavity behind the mirror: dirty tiles, a dead sink
    for x in range(7, 14):
        for y in range(65, 69):
            w.set(x, y, 10, cube("white_tile_dirty"))
    for z in range(11, 16):
        for y in range(65, 69):
            w.set(6, y, z, cube("white_tile_dirty"))
            w.set(14, y, z, cube("white_tile_dirty"))
    for x in range(9, 12):
        w.set(x, 65, 15, prop("restroom_sink", facing="north"))
    w.set(10, 69, 19, Block("ceiling_light", {"lit": "true" if c["restroom"] else "false", "broken": "true"},
                            cube={"down": "ceiling_light_lit" if c["restroom"] else "ceiling_light", "all": "ceiling_tile"},
                            opaque=True, light=13 if c["restroom"] else 0))
    w.set(12, 68, 21, prop("hand_dryer", facing="north"))
    # the kitchen (15..39 x 1..9): quarry tiles, steel, the cooking line on the north wall
    for x in range(15, 40):
        for z in range(1, 10):
            w.set(x, 64, z, cube("kitchen_floor"))
        for y in range(65, 69):
            w.set(x, y, 0, cube("stainless"))
    for x in range(16, 22):
        w.set(x, 65, 1, prop("gas_range", "gas_range_lit" if c["kitchen"] else "gas_range", facing="south",
                              light=9 if c["kitchen"] else 0, state={"lit": "true" if c["kitchen"] else "false"}))
        w.set(x, 68, 1, prop("exhaust_hood", facing="south"))
    w.set(22, 65, 1, prop("fryer", facing="south"))
    w.set(23, 65, 1, prop("griddle", facing="south"))
    for x in range(24, 30):
        w.set(x, 65, 1, prop("kitchen_counter", facing="south"))
    for x in range(23, 28):
        w.set(x, 65, 5, prop("prep_table", facing="north"))
    for x in (22, 23):
        w.set(x, 65, 9, prop("pass_shelf", "pass_shelf_lit" if c["kitchen"] else "pass_shelf", facing="north",
                             light=8 if c["kitchen"] else 0, state={"lit": "true" if c["kitchen"] else "false"}))
    for x in range(33, 39):
        w.set(x, 65, 1, prop("kitchen_sink", facing="south"))
    for x in range(16, 39, 4):
        for z in (3, 7):
            on = c["kitchen"]
            w.set(x, 69, z, Block("ceiling_light", {"lit": "true" if on else "false", "broken": "false"},
                                  cube={"down": "ceiling_light_lit" if on else "ceiling_light", "all": "ceiling_tile"},
                                  opaque=True, light=13 if on else 0))
    # the dining room (15..39 x 11..29): booths at the south windows, the drink bar on the north wall
    for x in range(15, 21):
        w.set(x, 65, 11, prop("drink_bar", facing="south", light=6))
    w.set(21, 65, 11, prop("coffee_machine", facing="south"))
    w.set(29, 68, 11, prop("exit_sign", facing="south", light=7, state={"hanging": "false"}))
    w.set(39, 68, 20, prop("exit_sign", facing="west", light=7, state={"hanging": "false"}))
    w.set(15, 68, 26, prop("exit_sign", facing="east", light=7, state={"hanging": "false"}))
    for x0 in range(16, 39, 4):
        # a booth: two benches facing each other and a table between, partitions on the backs
        w.set(x0, 65, 28, prop("booth_seat", facing="east"))
        w.set(x0, 65, 29, prop("booth_seat", facing="east"))
        w.set(x0 + 1, 65, 28, prop("restaurant_table"))
        w.set(x0 + 1, 65, 29, prop("restaurant_table"))
        w.set(x0 + 2, 65, 28, prop("booth_seat", facing="west"))
        w.set(x0 + 2, 65, 29, prop("booth_seat", facing="west"))
        w.set(x0 + 1, 66, 28, prop("menu_stand", facing="south"))
        lit_button = x0 == 32            # the visitor's table: its call button rings
        w.set(x0 + 1, 66, 29, prop("call_button", "call_button_lit" if lit_button else "call_button",
                                   state={"lit": "true" if lit_button else "false"}))
        w.set(x0 + 2, 66, 27, prop("booth_partition", facing="east"))
        if c["dining"]:
            w.set(x0 + 1, 68, 28, prop("pendant_lamp", "pendant_lamp_lit", light=12, state={"lit": "true"}))
    # center tables with chairs, the planter divider
    for x0 in (19, 25, 31):
        for z0 in (14, 23):
            w.set(x0, 65, z0, prop("restaurant_table"))
            w.set(x0 + 1, 65, z0, prop("restaurant_table"))
            w.set(x0, 65, z0 - 1, prop("restaurant_chair", facing="south", state={"fallen": "false"}))
            w.set(x0 + 1, 65, z0 + 1, prop("restaurant_chair", facing="north", state={"fallen": "false"}))
            w.set(x0, 66, z0, prop("menu_stand", facing="south"))
    for x in range(18, 37):
        w.set(x, 65, 19, prop("planter", facing="north"))
    w.set(24, 65, 21, prop("menu_scattered", "menu_scattered_1"))
    w.set(25, 65, 21, prop("menu_scattered", "menu_scattered_2"))
    # ceiling lights of the dining room (on the dining circuit), downlights over the register
    for x in range(17, 39, 4):
        for z in range(13, 29, 4):
            on = c["dining"]
            w.set(x, 69, z, Block("ceiling_light", {"lit": "true" if on else "false", "broken": "false"},
                                  cube={"down": "ceiling_light_lit" if on else "ceiling_light", "all": "ceiling_tile"},
                                  opaque=True, light=13 if on else 0))
    # waiting area / register counter with the cake case
    for x in range(9, 14):
        w.set(x, 64, 23, cube("dining_floor"))
    for x in range(9, 13):
        w.set(x, 65, 24, prop("counter", facing="south"))
    w.set(10, 66, 24, prop("register", facing="south"))
    w.set(12, 66, 24, prop("cake_case", facing="south", light=9))
    w.set(13, 65, 24, prop("cake_case", facing="south", light=9))
    for x in (10, 12):
        on = c["register"]
        w.set(x, 69, 25, Block("downlight", {"lit": "true" if on else "false"},
                               cube={"down": "downlight_lit" if on else "downlight", "all": "ceiling_tile"},
                               opaque=True, light=11 if on else 0))
    w.set(14, 68, 25, prop("call_display", "call_display_7", facing="east", light=4, state={"number": "7", "closed": "false"}))
    for x in range(1, 8):
        for z in range(27, 30):
            w.set(x, 64, z, cube("vestibule_floor"))

    # ---- outside: the vending machines, the pole sign, the street lamps, a hedge -----------------------
    # (positions as in the world's ExteriorBuilder / Terrain)
    for x in (7, 8):
        w.set(x, 65, 31, Block("vending_machine", {"half": "lower"}, facing="south", model="vending_machine_lower", light=12))
        w.set(x, 66, 31, Block("vending_machine", {"half": "upper"}, facing="south", model="vending_machine_upper", light=12))
    on = c["sign"]
    # the pole sign by the road: a pole, a 4x4 box sign high up, two faces (road / lot)
    for y in range(65, 73):
        w.set(46, y, 49, Block("pole_sign", {"part": "pole", "tile": "0", "lit": "true" if on else "false"}, facing="south",
                               model="pole_sign_pole_lit" if on else "pole_sign_pole"))
    for i in range(4):
        for j in range(4):
            for z, face in ((49, "south"), (48, "north")):
                t = i + 4 * (3 - j)
                w.set(45 + i if z == 49 else 48 - i, 73 + j, z,
                      Block("pole_sign", {"part": "tile", "tile": str(t), "lit": "true" if on else "false"},
                            facing=face, model="pole_sign_tile_%d%s" % (t, "_lit" if on else ""), light=15 if on else 0))
    # the roof sign over the shop windows (warm letters; its light leaks in through the blinds)
    for t in range(8):
        w.set(16 + t, 72, 31, Block("roof_sign", {"tile": str(t), "lit": "true" if on else "false"}, facing="south",
                                    model="roof_sign_tile_%d%s" % (t, "_lit" if on else ""), light=12 if on else 0))
    # sodium lamps on the lot and along the road (world: ExteriorBuilder.LOT_LAMPS, Terrain.ROAD_LAMPS,
    # heads at Terrain.LAMP_Y = 70)
    for (lx, lz, face) in ((-8, 40, "east"), (4, 49, "north"), (20, 49, "north"), (36, 49, "north"), (48, 40, "west"),
                           (58, 49, "south")):
        for y in range(65, 70):
            w.set(lx, y, lz, Block("street_lamp", {"part": "pole", "lit": "true" if on else "false"}, facing=face,
                                   model="street_lamp_pole_lit" if on else "street_lamp_pole"))
        w.set(lx, 70, lz, Block("street_lamp", {"part": "head", "lit": "true" if on else "false"}, facing=face,
                                model="street_lamp_head_lit" if on else "street_lamp_head", light=14 if on else 0))
    # the medium waits by her car (marker medium -1, 39): an invisible level-11 light over her spot
    w.set(-1, 67, 39, Block("light", light=11, opaque=False))
    for x in range(-2, 5):
        w.set(x, 65, 47, cube("hedge", opaque=False))
        w.set(x, 66, 47, cube("hedge", opaque=False))
    for x in range(46, 62):
        w.set(x, 65, 49, prop("guardrail", facing="south"))
    w.set(-28, 70, 49, Block("traffic_light", {"lit": "true"}, model="traffic_light_lit", light=8))
    return w
