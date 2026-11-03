"""The preview's Kopo Shirakaba: room 203 and its street after docs/DESIGN.md section 2 (the floor
plan, the rooms, the closet, the bathroom with its deep tub, the balcony doors with curtains, the
outdoor corridor, the rainy street with its sodium lamp, the vending machine, puddles), built from
the art's block models where they exist and simple stand-in models (and hitori_textures.py
stand-in textures) where they do not. Circuits / state: a dict of what is on, open, filled.
"""
from mcscene import Block, World

FEET = 70           # room 203: feet level; floor blocks at 69, ceiling at 73
GROUND = 65

# the floor plan of docs/DESIGN.md section 2: rows z 0..13, columns x 22..33
PLAN = [
    "##D#########",
    "#ggghh#t#bb#",
    "#ggghhdt#bb#",
    "####hh###d##",
    "#kkkhh#wwww#",
    "#kkkhhdwwww#",
    "#kkkhh######",
    "#kkkhhdyyyy#",
    "##ff#f#yyyy#",
    "#jjjjV#yyyy#",
    "#Ojjjj#yyyc#",
    "#Ojjjj#yyyc#",
    "#Ojjjj#yyyc#",
    "##WWW###WW##",
]
X0 = 22

FLOOR_OF = {"g": "genkan_tile", "h": "old_flooring", "t": "bath_tile_floor", "b": "bath_tile_floor",
            "k": "vinyl_floor", "w": "vinyl_floor", "y": "old_flooring", "j": "tatami", "V": "tatami",
            "O": "oshiire_floor", "c": "old_flooring", "d": "old_flooring", "f": "tatami", "D": "genkan_tile",
            "W": "tatami"}


def F(tex, light15=False):
    d = {"texture": tex}
    if light15:
        d["forge_data"] = {"block_light": 15, "sky_light": 15}
    return d


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all6(tex, light15=False, skip=()):
    return {k: F(tex, light15) for k in ("north", "south", "east", "west", "up", "down") if k not in skip}


# ---- stand-in models (facing north at rest, like the art's) ------------------------------------
def _tv(front, lit):
    return {"textures": {"f": front, "s": "crt_tv_side", "w": "wood_wainscot"},
            "elements": [box([0, 0, 1], [16, 6, 15], all6("#w")),
                         box([1, 6, 3], [15, 16, 15], all6("#s")),
                         box([1, 6, 2], [15, 16, 3], dict(all6("#s", skip=("north",)), north=F("#f", lit)))]}


def _fusuma(open_):
    tex = {"p": "oshiire_fusuma"}
    if open_ == 0:
        return {"textures": tex, "elements": [box([0, 0, 0], [16, 16, 2], all6("#p"))]}
    if open_ == 1:
        # a crack: 4 px in the middle of the block
        return {"textures": tex, "elements": [box([0, 0, 0], [6, 16, 2], all6("#p")), box([10, 0, 0], [16, 16, 2], all6("#p"))]}
    return {"textures": tex, "elements": [box([0, 0, 0], [1, 16, 2], all6("#p"))]}


def _tub(water):
    tex = {"e": "bathtub", "w": "bath_water"}
    el = [box([0, 0, 0], [16, 2, 16], all6("#e")),
          box([0, 2, 0], [16, 16, 2], all6("#e")), box([0, 2, 14], [16, 16, 16], all6("#e")),
          box([0, 2, 2], [2, 16, 14], all6("#e")), box([14, 2, 2], [16, 16, 14], all6("#e"))]
    if water:
        h = 13 if water == 2 else 8
        el.append(box([2, 2, 2], [14, h, 14], {"up": F("#w")}))
    return {"textures": tex, "elements": el}


STANDINS = {
    "crt_tv_on": _tv("crt_tv_front", True),
    "crt_tv_off": _tv("crt_tv_off_front", False),
    "chabudai": {"textures": {"w": "chabudai"}, "elements": [
        box([2, 5, 2], [14, 7, 14], all6("#w")), box([7, 0, 7], [9, 5, 9], all6("#w"))]},
    "zabuton": {"textures": {"z": "zabuton"}, "elements": [box([2, 0, 2], [14, 2, 14], all6("#z"))]},
    "hanging_lamp_lit": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.5, 10, 7.5], [8.5, 16, 8.5], all6("#c")),
        box([3, 3, 3], [13, 10, 13], dict(all6("#s", True, skip=("up",)), up=F("#c")))]},
    "hanging_lamp": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.5, 10, 7.5], [8.5, 16, 8.5], all6("#c")),
        box([3, 3, 3], [13, 10, 13], dict(all6("#s", skip=("up",)), up=F("#c")))]},
    "fluorescent_ring_lit": {"textures": {"r": "ring_fixture"}, "elements": [
        box([3, 14, 3], [13, 16, 13], dict(all6("#r", skip=("down",)), down=F("#r", True)))]},
    "fluorescent_ring": {"textures": {"r": "ring_fixture"}, "elements": [box([3, 14, 3], [13, 16, 13], all6("#r"))]},
    "oshiire_fusuma_0": _fusuma(0),
    "oshiire_fusuma_1": _fusuma(1),
    "oshiire_fusuma_2": _fusuma(2),
    "fusuma_0": {"textures": {"p": "fusuma"}, "elements": [box([0, 0, 7], [16, 16, 9], all6("#p"))]},
    "curtain_closed": {"textures": {"c": "curtain"}, "elements": [box([0, 0, 0], [16, 16, 2], all6("#c"))]},
    "curtain_open": {"textures": {"c": "curtain"}, "elements": [box([0, 0, 0], [3, 16, 4], all6("#c"))]},
    "rain_window": {"render_type": "minecraft:translucent", "textures": {"g": "rain_window"}, "elements": [
        box([0, 0, 7], [16, 16, 9], all6("#g"))]},
    "balcony_door": {"render_type": "minecraft:translucent", "textures": {"g": "balcony_door"}, "elements": [
        box([0, 0, 7], [16, 16, 9], all6("#g"))]},
    "bath_door": {"render_type": "minecraft:translucent", "textures": {"g": "bath_door"}, "elements": [
        box([0, 0, 7], [16, 16, 9], all6("#g"))]},
    "frosted_window": {"render_type": "minecraft:translucent", "textures": {"g": "frosted_glass"}, "elements": [
        box([0, 0, 7], [16, 16, 9], all6("#g"))]},
    "bathtub_0": _tub(0), "bathtub_1": _tub(1), "bathtub_2": _tub(2),
    "washbasin": {"textures": {"e": "washbasin"}, "elements": [box([2, 8, 0], [14, 13, 10], all6("#e")),
                                                              box([6, 0, 0], [10, 8, 4], all6("#e"))]},
    "toilet_seat": {"textures": {"e": "toilet_seat"}, "elements": [box([3, 0, 2], [13, 7, 14], all6("#e")),
                                                                  box([3, 7, 0], [13, 16, 4], all6("#e"))]},
    "street_lamp_old_lit": {"textures": {"p": "street_lamp_pole", "h": "street_lamp_head"}, "elements": [
        box([6, 0, 6], [10, 16, 10], all6("#p")),
        box([7, 12, 0], [9, 14, 6], all6("#p")),
        box([3, 9, -4], [13, 12, 4], dict(all6("#p", skip=("down",)), down=F("#h", True)))]},
    "vending_lower": {"textures": {"f": "vending_front", "s": "vending_side"}, "elements": [
        box([0, 0, 1], [16, 16, 16], dict(all6("#s", skip=("north",)), north=F("#f", True)))]},
    "vending_upper": {"textures": {"f": "vending_front", "s": "vending_side"}, "elements": [
        box([0, 0, 1], [16, 12, 16], dict(all6("#s", skip=("north",)), north=F("#f", True)))]},
    "utility_pole": {"textures": {"p": "utility_pole"}, "elements": [box([6, 0, 6], [10, 16, 10], all6("#p"))]},
    "puddle": {"render_type": "minecraft:translucent", "textures": {"t": "puddle"},
               "elements": [box([0, 0, 0], [16, 1, 16], {"up": F("#t")})]},
    "desk": {"textures": {"w": "desk"}, "elements": [box([0, 13, 0], [16, 16, 16], all6("#w")),
                                                    box([0, 0, 0], [2, 13, 2], all6("#w")), box([14, 0, 0], [16, 13, 2], all6("#w")),
                                                    box([0, 0, 14], [2, 13, 16], all6("#w")), box([14, 0, 14], [16, 13, 16], all6("#w"))]},
    "laptop_on": {"textures": {"b": "laptop_body", "s": "laptop_screen"}, "elements": [
        box([3, 0, 4], [13, 1, 12], all6("#b")),
        box([3, 1, 11], [13, 9, 12], dict(all6("#b", skip=("north",)), north=F("#s", True)))]},
    "bed": {"textures": {"b": "bed"}, "elements": [box([0, 0, 0], [16, 8, 16], all6("#b"))]},
    "futon_folded": {"textures": {"f": "futon"}, "elements": [box([1, 2, 1], [15, 10, 15], all6("#f"))]},
    "oshiire_shelf": {"textures": {"p": "oshiire_shelf"}, "elements": [box([0, 0, 0], [16, 2, 16], all6("#p"))]},
    "cardboard_box": {"textures": {"c": "cardboard_box"}, "elements": [box([2, 0, 2], [14, 10, 14], all6("#c"))]},
    "door_closed": {"textures": {"d": "interior_door"}, "elements": [box([0, 0, 0], [16, 16, 3], all6("#d"))]},
    "front_door_closed": {"textures": {"d": "front_door"}, "elements": [box([0, 0, 0], [16, 16, 3], all6("#d"))]},
    "rail": {"textures": {"p": "utility_pole"}, "elements": [box([0, 12, 7], [16, 14, 9], all6("#p")),
                                                            box([7, 0, 7], [9, 12, 9], all6("#p"))]},
    "house_window_lit": {"textures": {"w": "house_window_lit"}, "elements": [box([0, 0, 0], [16, 16, 16], all6("#w", True))]},
}


def cube(name, tex=None, opaque=True, light=0, state=None, layer=None):
    return Block(name, state, cube=tex or {"all": name}, opaque=opaque, light=light, layer=layer)


def prop(name, model=None, facing="north", light=0, state=None, opaque=False):
    return Block(name, state, facing=facing, model=model or name, opaque=opaque, light=light)


DEFAULT = dict(tv=True, tv_channel=0, tv_light=9, fusuma=0, curtains=True, bath=2, street=True,
               lights=(), laptop=True, desk_lamp=False)


def build(state=None):
    """state: tv (on), tv_channel, tv_light (its vanilla light level), fusuma (closet: 0 shut, 1 crack,
    2 open), curtains (drawn), bath (water 0..2), street (lamps lit), lights (rooms whose ceiling
    lamp is on), laptop (on), desk_lamp"""
    s = dict(DEFAULT)
    s.update(state or {})
    w = World(-14, 58, -20, 72, 30, 54)
    import random
    rnd = random.Random(7)

    # ---- ground: the street south of the building, concrete round it -------------------------
    for x in range(-14, 58):
        for z in range(-20, 34):
            w.set(x, 63, z, cube("concrete"))
            if 18 <= z <= 22:
                w.set(x, 64, z, cube("asphalt_wet"))
            else:
                w.set(x, 64, z, cube("concrete"))
    for _ in range(14):
        cx, cz = rnd.randint(4, 50), rnd.randint(18, 22)
        rx, rz = rnd.uniform(0.8, 2.6), rnd.uniform(0.6, 1.4)
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 < 1.0 and 18 <= z <= 22:
                    w.set(x, 65, z, prop("puddle", "puddle"))
    # the block wall along the street (a gap at the entrance), the house opposite
    for x in range(-2, 46):
        if 6 <= x <= 8:
            continue
        for y in (65, 66):
            w.set(x, y, 17, cube("block_wall"))
    for x in range(10, 30):
        for z in range(25, 31):
            for y in range(65, 72):
                if x in (10, 29) or z in (25, 30):
                    w.set(x, y, z, cube("house_wall"))
            w.set(x, 72, z, cube("roof_slate"))
    w.set(20, 67, 25, prop("house_window_lit", "house_window_lit"))

    # ---- the building: ground floor (dark units), second floor slab, units, roof ------------------
    for x in range(0, 44):
        for z in range(0, 14):
            w.set(x, 64, z, cube("concrete"))
            for y in (68, 69):
                w.set(x, y, z, cube("concrete"))
            w.set(x, 73, z, cube("ceiling_board"))
            w.set(x, 74, z, cube("roof_slate"))
            border = x in (0, 43) or z in (0, 13)
            for y in range(65, 68):
                if border or x in (11, 22, 33):
                    w.set(x, y, z, cube("apartment_siding"))
            if border or x in (11, 22, 33):
                for y in range(70, 73):
                    w.set(x, y, z, cube("apartment_siding"))
    # the eaves over the corridor and the balconies
    for x in range(-1, 45):
        for z in list(range(-4, 0)) + list(range(14, 17)):
            w.set(x, 74, z, cube("roof_slate"))
    # outdoor corridor (north, z -3..-1) and balconies (south, z 14..15)
    for x in range(0, 44):
        for z in (-3, -2, -1):
            w.set(x, 69, z, cube("corridor_concrete"))
        w.set(x, 70, -3, prop("rail", "rail", facing="north"))
        for z in (14, 15):
            w.set(x, 69, z, cube("concrete"))
        w.set(x, 70, 15, prop("rail", "rail", facing="north"))

    # ---- room 203 ----------------------------------------------------------------------------------
    for zi, row in enumerate(PLAN):
        z = zi
        for xi, ch in enumerate(row):
            x = X0 + xi
            if ch == "#":
                bath_side = any(PLAN[zz][xx] in "bt" for zz in range(max(zi - 1, 0), min(zi + 2, 14))
                                for xx in range(max(xi - 1, 0), min(xi + 2, 12)))
                tex = "bath_tile_wall" if bath_side else ("plaster_wall_stained" if (x + z) % 7 == 0 else "plaster_wall")
                for y in range(70, 73):
                    w.set(x, y, z, cube(tex))
                continue
            fl = FLOOR_OF.get(ch, "old_flooring")
            w.set(x, 69, z, cube(fl))
            w.set(x, 73, z, cube("ceiling_board_stained" if (x * 3 + z) % 11 == 0 else "ceiling_board"))
            for y in range(70, 73):
                w.set(x, y, z, None)
            if ch in "gD":
                w.set(x, 69, z, None)
                w.set(x, 68, z, cube("genkan_tile"))
            if ch == "D":
                w.set(x, 69, z, prop("front_door", "front_door_closed", facing="south", state={"open": "false"}))
                w.set(x, 70, z, prop("front_door", "front_door_closed", facing="south", state={"open": "false"}))
                w.set(x, 71, z, cube("plaster_wall"))
                w.set(x, 72, z, cube("plaster_wall"))
            elif ch == "d":
                fac = "west" if row[xi - 1] in "#" or row[xi + 1] in "#" else "north"
                vertical = PLAN[zi - 1][xi] == "#" and PLAN[zi + 1][xi] == "#"
                fac = "east" if vertical else "north"
                for y in (70, 71):
                    w.set(x, y, z, prop("interior_door", "door_closed", facing=fac, state={"open": "false"}))
                w.set(x, 72, z, cube("plaster_wall"))
            elif ch == "f":
                for y in (70, 71):
                    w.set(x, y, z, prop("fusuma", "fusuma_0", facing="north", state={"open": "0"}))
                w.set(x, 72, z, cube("plaster_wall"))
            elif ch == "W":
                for y in (70, 71):
                    w.set(x, y, z, prop("balcony_door", "balcony_door", facing="north", state={"open": "false"}))
                w.set(x, 72, z, cube("plaster_wall"))
    # frosted windows: the bathroom's and the toilet's, in the north wall
    for x in (31, 32):
        w.set(x, 71, 0, prop("bath_door", "frosted_window", facing="north"))
    w.set(29, 71, 0, prop("bath_door", "frosted_window", facing="north"))
    # the bathroom's folding door: frosted glass
    for y in (70, 71):
        w.set(31, y, 3, prop("bath_door", "bath_door", facing="north"))
    # a window in the DK (west wall) and the western room (east wall): rain on them
    for z in (5, 6):
        w.set(22, 71, z, prop("rain_window", "rain_window", facing="east"))
    for z in (9, 10):
        w.set(33, 71, z, prop("rain_window", "rain_window", facing="east"))

    # ---- the closet (oshiire): x 23, z 10..12 ---------------------------------------------------------
    for z in (10, 11, 12):
        w.set(23, 72, z, prop("oshiire_shelf", "oshiire_shelf"))
        for y in (70, 71, 72):
            op = s["fusuma"] if (z == s.get("crack_z", 10) and y < 72) else 0
            w.set(24, y, z, prop("oshiire_fusuma", "oshiire_fusuma_%d" % op, facing="west", state={"open": str(op)}))
    w.set(23, 70, 12, prop("cardboard_box", "cardboard_box"))
    # the closet's north end (the plan leaves (23, 9) to the tatami: the oshiire needs its side wall)
    for y in range(70, 73):
        w.set(23, y, 9, cube("plaster_wall"))
    # (the futons lie on the shelf: the shelf is a slab at the bottom of y 72)

    # ---- the tatami room: the TV, the low table, cushions, the hanging lamp, curtains ----------------
    tv_on = s["tv"]
    w.set(27, 70, 9, Block("crt_tv", {"on": "true" if tv_on else "false", "channel": str(s["tv_channel"])},
                           facing="west", model="crt_tv_on" if tv_on else "crt_tv_off", light=s["tv_light"] if tv_on else 0))
    w.set(25, 70, 11, prop("chabudai", "chabudai"))
    w.set(25, 70, 10, prop("zabuton", "zabuton"))
    w.set(26, 70, 12, prop("zabuton", "zabuton"))
    lit = "tatami" in s["lights"]
    w.set(25, 72, 10, prop("hanging_lamp", "hanging_lamp_lit" if lit else "hanging_lamp", light=13 if lit else 0,
                           state={"lit": "true" if lit else "false"}))
    for x in (25, 26):                  # (x 24 is the closet's fusuma column)
        for y in (70, 71):
            closed = s["curtains"]
            w.set(x, y, 12, prop("curtain", "curtain_closed" if closed else "curtain_open", facing="south",
                                 state={"closed": "true" if closed else "false"}))

    # ---- the other rooms' ceiling lamps, the western room, the bathroom, the kitchen --------------
    for room, (x, z) in dict(western=(30, 9), dk=(24, 5), hall=(26, 4), bath=(31, 1), washroom=(30, 4),
                             toilet=(29, 1), genkan=(24, 1)).items():
        on = room in s["lights"]
        w.set(x, 72, z, Block("fluorescent_ring", {"lit": "true" if on else "false"},
                              model="fluorescent_ring_lit" if on else "fluorescent_ring", light=14 if on else 0))
    for z in (8, 9):
        w.set(32, 70, z, prop("desk", "desk"))
    if s["laptop"]:
        w.set(32, 71, 8, Block("laptop_block", {"open": "true", "on": "true"}, facing="west", model="laptop_on", light=5))
    if s["desk_lamp"]:
        w.set(32, 71, 9, Block("desk_lamp", {"lit": "true"}, model="hanging_lamp_lit", light=10))
    for x in (29, 30):
        for z in (11, 12):
            w.set(x, 70, z, prop("bed", "bed"))
    for z in (1, 2):
        w.set(32, 70, z, Block("bathtub", {"water": str(s["bath"]), "lid": "false"}, facing="west", model="bathtub_%d" % s["bath"]))
    w.set(32, 70, 5, prop("washbasin", "washbasin", facing="west"))
    w.set(29, 70, 1, prop("toilet_seat", "toilet_seat", facing="south"))
    w.set(23, 70, 5, cube("kitchen_sink", {"all": "enamel_white"}))
    w.set(23, 70, 7, cube("fridge_old", {"all": "fridge_old"}))
    w.set(23, 71, 7, cube("fridge_old", {"all": "fridge_old"}))

    # ---- the street lamps (sodium) on their poles, the vending machine --------------------------------
    on = s["street"]
    for (lx, lz, fac) in ((27, 17, "north"), (30, -9, "south")):
        for y in range(65, 71):
            w.set(lx, y, lz, prop("utility_pole", "utility_pole"))
        w.set(lx, 71, lz, Block("street_lamp_old", {"lit": "true" if on else "false"}, facing=fac,
                                model="street_lamp_old_lit", light=15 if on else 0))
    w.set(37, 65, 21, Block("vending_machine_small", {}, facing="north", model="vending_lower", light=12))
    w.set(37, 66, 21, Block("vending_machine_small", {}, facing="north", model="vending_upper", light=12))
    return w
