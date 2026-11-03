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


def F(tex, light15=False, uv=None):
    d = {"texture": tex}
    if light15:
        d["forge_data"] = {"block_light": 15, "sky_light": 15}
    if uv:
        d["uv"] = list(uv)
    return d


def box(frm, to, faces):
    # elements reaching out of their block need explicit UVs (the defaults would leave the texture)
    if any(c < 0 or c > 16 for c in list(frm) + list(to)):
        faces = {k: dict(v, uv=v.get("uv", [0, 0, 16, 16])) for k, v in faces.items()}
    return {"from": frm, "to": to, "faces": faces}


def all6(tex, light15=False, skip=()):
    return {k: F(tex, light15) for k in ("north", "south", "east", "west", "up", "down") if k not in skip}


# ---- stand-in models (facing north at rest, named like the art's: the art's own replace them) ----
def _tv(lit):
    # like the art's crt_tv: the body hangs 10 px down onto its stand in the block below
    el = [box([2, -10, 3], [14, 0, 9], all6("#s")), box([3.5, -9, 9], [12.5, -1, 12], all6("#s")),
          box([2, -10, 2.4], [14, 0, 3], all6("#s", skip=("north",))),
          box([5, -8, 2.3], [13, -2, 2.4], {"north": F("#f" if lit else "#o", lit, uv=[1, 2, 11, 12])})]
    return {"textures": {"f": "crt_tv_front", "o": "crt_tv_off_front", "s": "crt_tv_side"}, "elements": el}


def _fusuma(open_):
    # the closet's panel at the model's front edge (z 0.25..1.75), slid toward +x by 4 px (crack) / a block
    tex = {"p": "oshiire_fusuma"}
    sh = {0: 0, 1: 4, 2: 16}[open_]
    el = [box([0, 0, 0], [16, 0.5, 3.6], all6("#p"))]
    if sh < 16:
        el.append(box([sh, 0.5, 0.25], [16 + sh if sh else 16, 16, 1.75], all6("#p")))
    return {"textures": tex, "elements": el}


def _tub(part, water):
    tex = {"e": "bathtub", "t": "bath_tile_wall", "w": "bath_water"}
    el = [box([0, 0, 0], [1.5, 12, 16], all6("#e")), box([14.5, 0, 0], [16, 12, 16], all6("#e")),
          box([1.5, 0, 1.5], [14.5, 2, 16], {"up": F("#e")})]
    el.append(box([1.5, 0, 0], [14.5, 12, 1.5], all6("#e")) if part == "head" else box([1.5, 0, 14.5], [14.5, 12, 16], all6("#e")))
    if water:
        h = 10.5 if water == 2 else 6.5
        el.append(box([1.5, h - 0.01, 1.5], [14.5, h, 14.5], {"up": F("#w")}))
    return {"render_type": "minecraft:translucent", "textures": tex, "elements": el}


STANDINS = {
    "crt_tv_on_0": _tv(True), "crt_tv_on_1": _tv(True), "crt_tv_on_2": _tv(True), "crt_tv": _tv(False),
    "tv_stand": {"textures": {"w": "tv_stand"}, "elements": [box([0, 0, 2], [16, 6, 14], all6("#w"))]},
    "chabudai": {"textures": {"w": "chabudai"}, "elements": [
        box([1, 5, 1], [15, 6.5, 15], all6("#w")), box([7, 0, 7], [9, 5, 9], all6("#w"))]},
    "zabuton": {"textures": {"z": "zabuton"}, "elements": [box([1, 0, 1], [15, 2, 15], all6("#z"))]},
    "hanging_lamp_lit": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.8, 9.5, 7.8], [8.2, 16, 8.2], all6("#c")),
        box([3, 4, 3], [13, 9, 13], dict(all6("#s", True, skip=("up",)), up=F("#c")))]},
    "hanging_lamp": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.8, 9.5, 7.8], [8.2, 16, 8.2], all6("#c")),
        box([3, 4, 3], [13, 9, 13], dict(all6("#s", skip=("up",)), up=F("#c")))]},
    "hanging_lamp_lit_swing": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.8, 9.5, 7.8], [8.2, 16, 8.2], all6("#c")),
        box([3, 4, 3], [13, 9, 13], dict(all6("#s", True, skip=("up",)), up=F("#c")))]},
    "hanging_lamp_swing": {"textures": {"s": "lamp_shade", "c": "lamp_cord"}, "elements": [
        box([7.8, 9.5, 7.8], [8.2, 16, 8.2], all6("#c")),
        box([3, 4, 3], [13, 9, 13], dict(all6("#s", skip=("up",)), up=F("#c")))]},
    "fluorescent_ring_lit": {"textures": {"r": "ring_fixture"}, "elements": [
        box([2.5, 15, 2.5], [13.5, 16, 13.5], all6("#r")),
        box([2, 13.2, 2], [14, 15, 14], dict(all6("#r", True, skip=("up",)), up=F("#r")))]},
    "fluorescent_ring": {"textures": {"r": "ring_fixture"}, "elements": [box([2, 13.2, 2], [14, 16, 14], all6("#r"))]},
    "oshiire_fusuma_lower_0": _fusuma(0), "oshiire_fusuma_lower_1": _fusuma(1), "oshiire_fusuma_lower_2": _fusuma(2),
    "oshiire_fusuma_upper_0": _fusuma(0), "oshiire_fusuma_upper_1": _fusuma(1), "oshiire_fusuma_upper_2": _fusuma(2),
    "fusuma_0": {"textures": {"p": "fusuma"}, "elements": [box([0, 0, 6.5], [16, 16, 9.5], all6("#p"))]},
    "curtain_closed": {"textures": {"c": "curtain"}, "elements": [
        box([float(i * 2), 0, 13.0 if i % 2 == 0 else 13.8], [float(i * 2 + 2), 16, 14.0 if i % 2 == 0 else 14.8], all6("#c"))
        for i in range(8)]},
    "curtain": {"textures": {"c": "curtain"}, "elements": [box([0, 0, 12], [3, 16, 15], all6("#c"))]},
    "rain_window": {"render_type": "minecraft:translucent", "textures": {"g": "rain_window", "f": "window_frame"}, "elements": [
        box([1, 1, 7.6], [15, 15, 8.4], {"north": F("#g"), "south": F("#g")}),
        box([0, 0, 6], [16, 1, 10], all6("#f")), box([0, 15, 6], [16, 16, 10], all6("#f")),
        box([0, 1, 6], [1, 15, 10], all6("#f")), box([15, 1, 6], [16, 15, 10], all6("#f"))]},
    "balcony_door_lower": {"render_type": "minecraft:translucent", "textures": {"g": "balcony_door", "f": "window_frame"}, "elements": [
        box([1, 0, 7.6], [15, 16, 8.4], {"north": F("#g"), "south": F("#g")}),
        box([0, 0, 6], [1, 16, 10], all6("#f")), box([15, 0, 6], [16, 16, 10], all6("#f")), box([1, 0, 6], [15, 1, 10], all6("#f"))]},
    "balcony_door_upper": {"render_type": "minecraft:translucent", "textures": {"g": "balcony_door", "f": "window_frame"}, "elements": [
        box([1, 0, 7.6], [15, 15, 8.4], {"north": F("#g"), "south": F("#g")}),
        box([0, 0, 6], [1, 16, 10], all6("#f")), box([15, 0, 6], [16, 16, 10], all6("#f")), box([1, 15, 6], [15, 16, 10], all6("#f"))]},
    "bath_door": {"render_type": "minecraft:translucent", "textures": {"g": "bath_door"}, "elements": [
        box([0, 0, 7], [16, 16, 9], all6("#g"))]},
    "bathtub_head_0": _tub("head", 0), "bathtub_head_1": _tub("head", 1), "bathtub_head_2": _tub("head", 2),
    "bathtub_foot_0": _tub("foot", 0), "bathtub_foot_1": _tub("foot", 1), "bathtub_foot_2": _tub("foot", 2),
    "washbasin": {"textures": {"e": "washbasin"}, "elements": [box([2, 8, 0], [14, 13, 10], all6("#e")),
                                                              box([6, 0, 0], [10, 8, 4], all6("#e"))]},
    "toilet_seat": {"textures": {"e": "toilet_seat"}, "elements": [box([3, 0, 2], [13, 7, 14], all6("#e")),
                                                                  box([3, 7, 0], [13, 16, 4], all6("#e"))]},
    "street_lamp_old_lit": {"textures": {"p": "street_lamp_pole", "h": "street_lamp_head"}, "elements": [
        box([3, 9, 0], [13, 10.6, 9], dict(all6("#p", skip=("down",)), down=F("#h", True))),
        box([5.5, 7.6, 2.5], [10.5, 9, 6.5], all6("#h", True, skip=("up",))),
        box([7.3, 9.6, 8.5], [8.7, 11, 21], all6("#p"))]},
    "vending_machine_small": {"textures": {"f": "vending_front", "s": "vending_side"}, "elements": [
        box([1, 0, 3], [15, 28, 15], all6("#s")),
        box([4.5, 13.5, 2.9], [14.5, 25.5, 3.0], {"north": F("#f", True)})]},
    "utility_pole": {"textures": {"p": "utility_pole"}, "elements": [box([5, 0, 5], [11, 16, 11], all6("#p"))]},
    "puddle": {"render_type": "minecraft:translucent", "textures": {"t": "puddle_water"},
               "elements": [box([0, 0, 0], [16, 1, 16], {"up": F("#t")})]},
    "desk": {"textures": {"w": "desk"}, "elements": [box([0, 10, 0], [16, 12, 16], all6("#w")),
                                                    box([0, 0, 0], [2, 10, 2], all6("#w")), box([14, 0, 0], [16, 10, 2], all6("#w")),
                                                    box([0, 0, 14], [2, 10, 16], all6("#w")), box([14, 0, 14], [16, 10, 16], all6("#w"))]},
    "laptop_block_open_on": {"textures": {"b": "laptop_body", "s": "laptop_screen"}, "elements": [
        box([3.5, -4, 4.5], [12.5, -3.4, 11], all6("#b")),
        box([3.5, -3.4, 11], [12.5, 3, 11.6], all6("#b")),
        box([4, -3, 10.95], [12, 2.5, 11], {"north": F("#s", True, uv=[1, 1, 15, 11])})]},
    "bed": {"textures": {"b": "bed"}, "elements": [box([0, 0, 0], [16, 9, 16], all6("#b"))]},
    "futon_folded": {"textures": {"f": "futon", "p": "oshiire_shelf"}, "elements": [
        box([0, 0, 0], [16, 2, 16], all6("#p")), box([1, 2, 1], [15, 10, 15], all6("#f"))]},
    "cardboard_box": {"textures": {"c": "cardboard_box", "p": "oshiire_shelf"}, "elements": [
        box([0, 0, 0], [16, 2, 16], all6("#p")), box([2, 2, 2], [14, 12, 14], all6("#c"))]},
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


DEFAULT = dict(tv=True, tv_channel=0, tv_light=8, fusuma=0, curtains=True, bath=2, street=True,
               lights=(), laptop=True, desk_lamp=False, swing=False)


def build(state=None):
    """state: tv (on), tv_channel, tv_light (its vanilla light level), fusuma (closet: 0 shut, 1 crack,
    2 open), curtains (drawn), bath (water 0..2), street (lamps lit), lights (rooms whose ceiling
    lamp is on), laptop (on), desk_lamp, swing (the hanging lamp swinging).
    Placements as the world builds them (hk-world ApartmentBuilder / Terrain / Layout)."""
    s = dict(DEFAULT)
    s.update(state or {})
    w = World(-14, 58, -20, 72, 30, 54)
    import random
    rnd = random.Random(7)

    # ---- ground: the street (asphalt z 19..24, gutters 18 / 25), concrete round the building ------
    for x in range(-14, 58):
        for z in range(-20, 34):
            w.set(x, 63, z, cube("concrete"))
            w.set(x, 64, z, cube("asphalt_wet") if 19 <= z <= 24 else cube("concrete"))
    for _ in range(16):
        cx, cz = rnd.randint(2, 54), rnd.randint(19, 24)
        rx, rz = rnd.uniform(0.8, 2.6), rnd.uniform(0.6, 1.4)
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 < 1.0 and 19 <= z <= 24:
                    w.set(x, 65, z, prop("puddle", "puddle"))
    # the block wall along the property (the gate at x -7..-3), the dark house across the street
    for x in range(-9, 47):
        if -7 <= x <= -3:
            continue
        for y in (65, 66):
            w.set(x, y, 16, cube("block_wall"))
    for x in range(10, 30):
        for z in range(27, 33):
            for y in range(65, 72):
                if x in (10, 29) or z in (27, 32):
                    w.set(x, y, z, cube("house_wall"))
            w.set(x, 72, z, cube("roof_slate"))
    w.set(20, 67, 27, prop("house_window_lit", "house_window_lit"))

    # ---- the building: X 0..44, Z 0..13, ground floor dark, second floor slab, units, roof ---------
    for x in range(0, 45):
        for z in range(0, 14):
            w.set(x, 64, z, cube("concrete"))
            for y in (68, 69):
                w.set(x, y, z, cube("concrete"))
            w.set(x, 73, z, cube("ceiling_board"))
            w.set(x, 74, z, cube("roof_slate"))
            wall = x in (0, 11, 22, 33, 44) or z in (0, 13)
            if wall:
                for y in list(range(65, 68)) + list(range(70, 73)):
                    w.set(x, y, z, cube("apartment_siding"))
    for x in range(-1, 46):
        for z in list(range(-4, 0)) + list(range(14, 17)):
            w.set(x, 74, z, cube("roof_slate"))
    for x in range(0, 45):
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
            if ch == "#" or (x == 23 and z == 9):          # (23, 9): the pier beside the closet
                # like the world's ApartmentBuilder.wall: a wall between the bathroom and another room
                # is a two-sided wall (the bath's tiles toward the tub, plaster on the other faces)
                nb = {}
                for fac, (dx, dz) in (("north", (0, -1)), ("east", (1, 0)), ("south", (0, 1)), ("west", (-1, 0))):
                    xx, zz = xi + dx, zi + dz
                    nb[fac] = PLAN[zz][xx] if 0 <= zz < len(PLAN) and 0 <= xx < len(PLAN[0]) else "#"
                bath = [f for f, c in nb.items() if c in "bt"]
                other = [f for f, c in nb.items() if c not in "bt#"]
                plain = "plaster_wall_stained" if (x + z) % 7 == 0 else "plaster_wall"
                for y in range(70, 73):
                    if bath and other:
                        w.set(x, y, z, Block("two_sided_wall", {"facing": bath[0], "inner": "bath_tile_wall", "outer": "plaster_wall"}, facing=bath[0],
                                             model="two_sided_wall_bath_tile_wall__plaster_wall", opaque=True))
                    else:
                        w.set(x, y, z, cube("bath_tile_wall" if bath else plain))
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
                for y in (69, 70):
                    w.set(x, y, z, prop("front_door", "front_door_closed", facing="south", state={"open": "false"}))
                w.set(x, 71, z, cube("plaster_wall"))
                w.set(x, 72, z, cube("plaster_wall"))
            elif ch == "d":
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
                w.set(x, 70, z, prop("balcony_door", "balcony_door_lower", facing="north", state={"open": "false"}))
                w.set(x, 71, z, prop("balcony_door", "balcony_door_upper", facing="north", state={"open": "false"}))
                w.set(x, 72, z, prop("rain_window", "rain_window", facing="north"))
    # small (frosted) windows on the corridor side: the toilet's and the bathroom's
    for x in (29, 31, 32):
        w.set(x, 72, 0, prop("rain_window", "rain_window", facing="north"))
    # the bathroom's folding door: frosted glass
    for y in (70, 71):
        w.set(31, y, 3, prop("bath_door", "bath_door", facing="north"))

    # ---- the closet (oshiire): x 23, z 10..12 - the fusuma stand in its own column's east edge ------
    op = s["fusuma"]
    for z in (10, 11, 12):
        w.set(23, 70, z, prop("oshiire_fusuma", "oshiire_fusuma_lower_%d" % op, facing="east", state={"open": str(op), "half": "lower"}))
        w.set(23, 71, z, prop("oshiire_fusuma", "oshiire_fusuma_upper_%d" % op, facing="east", state={"open": str(op), "half": "upper"}))
    w.set(23, 72, 10, prop("futon_folded", "futon_folded", facing="east", state={"shelf": "true"}))
    w.set(23, 72, 11, prop("futon_folded", "futon_folded", facing="east", state={"shelf": "true"}))
    w.set(23, 72, 12, prop("cardboard_box", "cardboard_box", facing="east", state={"shelf": "true"}))

    # ---- the tatami room: the TV on its stand, the low table, cushions, the hanging lamp, curtains ----
    tv_on = s["tv"]
    w.set(27, 70, 9, prop("tv_stand", "tv_stand", facing="west"))
    w.set(27, 71, 9, Block("crt_tv", {"on": "true" if tv_on else "false", "channel": str(s["tv_channel"])}, facing="west",
                           model="crt_tv_on_%d" % s["tv_channel"] if tv_on else "crt_tv", light=s["tv_light"] if tv_on else 0))
    w.set(25, 70, 11, prop("chabudai", "chabudai"))
    w.set(25, 70, 10, prop("zabuton", "zabuton"))
    w.set(26, 70, 12, prop("zabuton", "zabuton"))
    lit = "tatami" in s["lights"]
    sw = s["swing"]
    w.set(25, 72, 11, prop("hanging_lamp", ("hanging_lamp_lit" if lit else "hanging_lamp") + ("_swing" if sw else ""),
                           light=13 if lit else 0, state={"lit": "true" if lit else "false", "swing": "true" if sw else "false"}))
    for x in (24, 25, 26):
        closed = s["curtains"] and x != 26
        for y in (70, 71):
            w.set(x, y, 12, prop("curtain", "curtain_closed" if closed else "curtain", facing="north",
                                 state={"closed": "true" if closed else "false"}))

    # ---- the other rooms' ceiling lamps, the western room, the bathroom, the kitchen ---------------
    for room, xz in dict(western=[(30, 10)], dk=[(24, 5)], hall=[(26, 3), (27, 6)], bath=[(32, 2)], washroom=[(30, 5)],
                         toilet=[(29, 1)], genkan=[(24, 1)]).items():
        on = room in s["lights"]
        for (x, z) in xz:
            w.set(x, 72, z, Block("fluorescent_ring", {"lit": "true" if on else "false"},
                                  model="fluorescent_ring_lit" if on else "fluorescent_ring", light=13 if on else 0))
    for x in (30, 31, 32):
        w.set(x, 70, 7, prop("desk", "desk", facing="south"))
    w.set(32, 70, 8, prop("desk", "desk", facing="west"))
    if s["laptop"]:
        w.set(32, 71, 8, Block("laptop_block", {"open": "true", "on": "true"}, facing="west", model="laptop_block_open_on", light=4))
    if s["desk_lamp"]:
        w.set(32, 71, 7, Block("desk_lamp", {"lit": "true"}, facing="west", model="desk_lamp_lit", light=10))
    for x in (30, 31):
        for y in (70, 71):
            w.set(x, y, 12, prop("curtain", "curtain_closed", facing="north", state={"closed": "true"}))
    for (x, z) in ((29, 11), (29, 12)):
        w.set(x, 70, z, prop("bed", "bed"))
    w.set(32, 70, 1, Block("bathtub", {"water": str(s["bath"]), "lid": "false", "part": "head"}, facing="north",
                           model="bathtub_head_%d" % s["bath"]))
    w.set(32, 70, 2, Block("bathtub", {"water": str(s["bath"]), "lid": "false", "part": "foot"}, facing="north",
                           model="bathtub_foot_%d" % s["bath"]))
    w.set(32, 70, 5, prop("washbasin", "washbasin", facing="west"))
    w.set(29, 70, 1, prop("toilet_seat", "toilet_seat", facing="south"))
    w.set(23, 70, 5, cube("kitchen_sink", {"all": "enamel_white"}))
    w.set(23, 70, 4, cube("fridge_old", {"all": "fridge_old"}))
    w.set(23, 71, 4, cube("fridge_old", {"all": "fridge_old"}))

    # ---- the street: utility poles, the old sodium lamp in front of 203, the vending machine ---------
    on = s["street"]
    for px in (9, 26, 43):
        for y in range(65, 75):
            w.set(px, y, 18, prop("utility_pole", "utility_pole"))
    w.set(26, 71, 17, Block("street_lamp_old", {"lit": "true" if on else "false"}, facing="north",
                            model="street_lamp_old_lit", light=15 if on else 0))
    w.set(0, 65, 18, Block("vending_machine_small", {}, facing="south", model="vending_machine_small", light=11))
    return w
