"""ギルド門の広場 Guild Gate Terrace (docs/modules/guildhouse.md).

Twenty gates, one per guild-house design, on a two-tier terrace at the foot of the castle cliff west of the viaduct.
The tiers of the terrace follow the tiers of the houses:

* the street wall (z = -49, facing street_n1): five gates at street level - the four tier-1 houses and the dwarven
  mine - between lamp-lit alcoves; twin cascades fall beside the grand stair (x -59..-49) that climbs on the axis of
  street_w1 to
* the lower court (top block y = 6, level with the passage through the viaduct's west parapet at z -54..-53, whose
  landing at x = -10 opens straight into the court): the broker's
  pavilion (npc.guild_estates), a fountain, cherry trees, benches; the glasshouse gate at its west end; along its
  north side the upper wall (z = -65) with six gates (tier 3 and two of tier 2) set into it, and the second grand
  stair up to
* the upper court (top block y = 12, level with the viaduct's second landing): the eight grandest gates (tiers 4 and
  5) against the cliff, a tall mast with the guild flag on the axis between the palace and the sky temple.
* Under the upper court a vaulted undercroft gives the viaduct's three ground-level arches somewhere to go: a
  gallery of banners in the colours of the twenty gates, with a door to the street.

Gate monuments: ``_gate_kit.py``. Spots: ``guildgate.<theme>`` (from the kit), ``npc.guild_estates``,
``crowd.guild_gates.<n>``.
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3

from . import _gate_kit as GK
from . import _town_kit as TK

DISTRICT = "guild_gates"
TOP = 6          # lower court top block (players stand at 7)
UP = 12          # upper court top block
X0, X1 = -98, -10
Z0, Z1 = -86, -47
WALL_S = -49     # street wall: blocks at z <= -49 (face towards +z)
WALL_U = -65     # upper wall: blocks at z <= -65 (face towards the lower court)
AXIS = -54       # x of the grand stairs (street_w1 is at x = -55)
STAIR = (-59, -49)
AIR = "minecraft:air"
F, ST, SL, WL = B.full, B.stairs, B.slab, B.wall

# gate placements: (theme, centre x or z, facing). Street gates: portal plane z = -51 (two blocks inside the street
# wall), floor y 0. Upper-wall gates: portal plane z = -67, floor y 6. North row: portal plane z = -81, floor y 12.
STREET_GATES = [("northern_longhouse", -93), ("mushroom_grove", -81), ("dwarven_forge", -69),
                ("ancient_ruins", -39), ("pirate_ship", -25)]
UPPER_WALL_GATES = [("great_tree", -90), ("sage_library", -79), ("japanese_mansion", -68),
                    ("jungle_temple", -40), ("desert_palace", -29), ("observatory", -18)]
NORTH_GATES = [("ice_castle", -92), ("arcane_tower", -82), ("volcanic_fortress", -72), ("sky_temple", -61),
               ("marble_palace", -47), ("gothic_cathedral", -36), ("dragon_lair", -26), ("coral_hall", -15)]
WEST_GATE = ("flower_glasshouse", -57)          # lower court west end, facing east, portal plane x = -93
BRIDGE = (-55, -52)                             # z range of the landing from the viaduct passage (z -54..-53)
BROKER = (-30, -59)                             # centre of the broker's pavilion
CASCADES = ((-63, -62), (-46, -45))             # x ranges of the two cascades beside the street stair

VIEWS = {
    "guild_gates": dict(pos=(-6, 24, -40), look=(-58, 9, -70), fov=70, sun=(42, -140), fog=2600,
                        label="Guild Gate Terrace from over street_n1"),
    "guild_gates_street": dict(pos=(-54.5, 4.6, -39), look=(-54, 11, -72), fov=74, sun=(40, -150), fog=2600,
                               label="The grand stair and the cascades on the axis of street_w1"),
    "guild_gates_lower": dict(pos=(-12, 9.6, -54.5), look=(-70, 10, -62), fov=78, sun=(40, -140), fog=2600,
                              label="Lower court from the bridge: the broker's pavilion and the upper wall gates"),
    "guild_gates_upper": dict(pos=(-54, 14.6, -69), look=(-54, 22, -86), fov=90, sun=(40, -150), fog=2600,
                              label="Upper court: the grandest gates against the cliff"),
    "guild_gates_upper_w": dict(pos=(-30, 15.6, -74), look=(-95, 18, -82), fov=80, sun=(40, -110), fog=2600,
                                label="Upper court looking west"),
    "guild_gates_upper_e": dict(pos=(-80, 15.6, -74), look=(-12, 18, -82), fov=80, sun=(40, -110), fog=2600,
                                label="Upper court looking east"),
    "guild_gates_aerial": dict(pos=(-42, 56, -18), look=(-56, 6, -68), fov=66, sun=(42, -140), fog=3000,
                               label="Guild Gate Terrace from above"),
    "guild_gates_undercroft": dict(pos=(-13.5, 2.6, -55), look=(-14, 3, -82), fov=80, sun=(40, -140), fog=900,
                                   label="The undercroft gallery behind the viaduct arches"),
}


def build(w, district, terrain):
    body(w)
    stairs(w)
    cascades(w)
    walls_dress(w)
    undercroft(w)
    gates(w)
    lower_court(w)
    upper_court(w)
    people(w)


# ====================================================================================== terrace body
def body(w):
    w.clear(X0, 1, Z0, X1, 40, Z1)
    w.fill(X0, -2, Z0, X1, 0, Z1, B.S("stone"))
    w.fill(X0, 1, WALL_U + 1, X1, TOP - 1, WALL_S, B.S("stone"))
    w.fill(X0, 1, Z0, X1, UP - 1, WALL_U, B.S("stone"))
    TK.pave(w, X0, WALL_S - 1, X1, WALL_U + 1, PAVE_LOWER, seed=7101, y=TOP, only_ground=False)
    TK.pave(w, X0, WALL_U, X1, Z0, PAVE_UPPER, seed=7102, y=UP, only_ground=False)
    # the street-level strip in front of the wall
    TK.pave(w, X0, WALL_S + 1, X1, Z1, TK.COBBLE_MIX, seed=7103, y=0, only_ground=False)
    # dressed faces: street wall, upper wall, west and east ends, north face
    face(w, "south", X0, X1, WALL_S, 1, TOP)
    face(w, "south", X0, X1, WALL_U, TOP + 1, UP)
    face(w, "west", Z0, Z1 - 2, X0, 1, TOP, upto=UP, split=WALL_U)
    face(w, "east", Z0, Z1 - 2, X1, 1, TOP, upto=UP, split=WALL_U)
    face(w, "north", X0, X1, Z0, 1, UP)
    # balustrades along the edges of both courts
    rail(w, X0 + 1, WALL_S, X1 - 1, WALL_S, TOP + 1)
    rail(w, X0 + 1, WALL_U, X1 - 1, WALL_U, UP + 1)
    rail(w, X0, WALL_S - 1, X0, WALL_U + 1, TOP + 1)
    rail(w, X0, WALL_U, X0, Z0 + 1, UP + 1)
    rail(w, X1, WALL_U, X1, Z0 + 1, UP + 1)
    rail(w, X1, WALL_S - 1, X1, BRIDGE[1] + 1, TOP + 1)
    rail(w, X1, BRIDGE[0] - 1, X1, WALL_U + 1, TOP + 1)


PAVE_LOWER = [("plaza_flagstones", 62), ("smooth_castle_stone", 10), ("polished_diorite", 8), ("calcite", 6),
              ("polished_andesite", 6), ("stone_bricks", 4)]
PAVE_UPPER = [("plaza_flagstones", 50), ("smooth_castle_stone", 18), ("polished_diorite", 10), ("quartz_bricks", 8),
              ("calcite", 8)]


def face(w, out, a0, a1, c, y0, y1, upto=None, split=None):
    """Dress a vertical face of the terrace: plinth, ashlar of castle bricks, a carved band and a cornice under the
    rail. ``out`` is the side the face looks to; it runs along a0..a1 at the plane c (x for east/west, z else).
    With ``upto``/``split`` the face is higher (up to ``upto``) on the far side of ``split`` (end faces)."""
    for a in range(min(a0, a1), max(a0, a1) + 1):
        top = y1
        if upto is not None and a <= split:
            top = upto
        x, z = (a, c) if out in ("south", "north") else (c, a)
        for y in range(y0, top + 1):
            if y == y0:
                st = F("stone_bricks")
            elif y == top:
                st = F("smooth_castle_stone")
            elif y == top - 1:
                st = F("carved_castle_stone") if a % 6 == 0 else F("castle_bricks")
            else:
                st = F("castle_bricks")
            w.set(x, y, z, st)
        dx, dz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[out]
        if w.is_empty(x + dx, top, z + dz):
            w.set(x + dx, top, z + dz, ST("smooth_castle_stone", OPP[out], "top"))


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def rail(w, x0, z0, x1, z1, y):
    """Balustrade: smooth posts every four blocks with a slab cap, castle brick walls between (only where free)."""
    n = max(abs(x1 - x0), abs(z1 - z0))
    for i in range(n + 1):
        x = x0 + (0 if n == 0 else round((x1 - x0) * i / n))
        z = z0 + (0 if n == 0 else round((z1 - z0) * i / n))
        if not w.is_empty(x, y, z):
            continue
        if i % 4 == 0 or i == n:
            w.set(x, y, z, F("smooth_castle_stone"))
            w.set(x, y + 1, z, SL("smooth_castle_stone"), only_empty=True)
        else:
            w.set(x, y, z, WL("castle_bricks"))


# ====================================================================================== stairs
def stairs(w):
    sx0, sx1 = STAIR
    # street_n1 -> lower court: first step at z -47 (y 1), last at z -52 (y 6)
    for k in range(6):
        z = Z1 - k
        w.fill(sx0, k + 1, z, sx1, k + 1, z, ST("smooth_castle_stone", "north"))
        w.fill(sx0, 1, z, sx1, k, z, F("stone"))
        w.clear(sx0, k + 2, z, sx1, k + 6, z)
    w.fill(sx0, 0, Z1, sx1, 0, Z1 - 1, F("stone_bricks"))
    # lower court -> upper court: first step at z -65 (y 7), last at z -70 (y 12)
    for k in range(6):
        z = WALL_U - k
        w.fill(sx0, TOP + 1 + k, z, sx1, TOP + 1 + k, z, ST("smooth_castle_stone", "north"))
        w.fill(sx0, TOP, z, sx1, TOP + k, z, F("stone"))
        w.clear(sx0, TOP + 2 + k, z, sx1, TOP + 7 + k, z)
    # cheek walls with newel posts and lamps (the stair runs between them)
    for x in (sx0 - 1, sx1 + 1):
        for k in range(6):
            z = Z1 - k
            w.fill(x, 1, z, x, k + 1, z, F("castle_bricks"))
            w.set(x, k + 2, z, WL("castle_bricks"))
            z2 = WALL_U - k
            w.fill(x, TOP, z2, x, TOP + k + 1, z2, F("castle_bricks"))
            w.set(x, TOP + k + 2, z2, WL("castle_bricks"))
        for (y, z) in ((1, Z1), (TOP + 1, WALL_U + 1)):
            w.fill(x, y - 1, z, x, y + 1, z, F("smooth_castle_stone"))
            deco.lamp_post(w, x, y + 1, z, height=3)
        w.set(x, TOP + 1, WALL_S - 4, F("smooth_castle_stone"))
        w.set(x, UP + 1, WALL_U - 6, F("smooth_castle_stone"))
        deco.lamp_post(w, x, UP + 1, WALL_U - 6, height=3)
    # carpet runners of red stone down the middle of both stairs' landings
    for z in range(WALL_S - 4, WALL_U + 1):
        w.set(AXIS, TOP, z, F("red_terracotta") if (z % 6) else F("gilded_trim"))


# ====================================================================================== cascades
def cascades(w):
    """Two water curtains beside the street stair: a rill on the lower court spills over the street wall into a
    basin on the strip; a lion's-head jet on each side of the stair's foot."""
    for (xa, xb) in CASCADES:
        # rill on the lower court, edged, two blocks long, sources
        w.fill(xa - 1, TOP - 1, WALL_S - 3, xb + 1, TOP, WALL_S, F("smooth_castle_stone"))
        w.fill(xa, TOP - 1, WALL_S - 2, xb, TOP - 1, WALL_S, F("prismarine_bricks"))
        w.fill(xa, TOP, WALL_S - 2, xb, TOP, WALL_S, B.S("water", level=0))
        w.fill(xa - 1, TOP + 1, WALL_S - 3, xb + 1, TOP + 1, WALL_S - 3, SL("smooth_castle_stone"))
        for x in (xa - 1, xb + 1):
            w.fill(x, TOP + 1, WALL_S - 2, x, TOP + 1, WALL_S, SL("smooth_castle_stone"))
        # the spill (level 1 at the lip) and the falling curtain (level 8) in front of the wall face
        w.fill(xa, TOP, WALL_S + 1, xb, TOP, WALL_S + 1, B.S("water", level=1))
        w.fill(xa, 1, WALL_S + 1, xb, TOP - 1, WALL_S + 1, B.S("water", level=8))
        # the basin on the strip
        w.fill(xa - 1, -1, WALL_S + 1, xb + 1, -1, Z1, F("prismarine_bricks"))
        w.fill(xa, 0, WALL_S + 1, xb, 0, WALL_S + 1, B.S("water", level=0))
        w.fill(xa - 1, 0, Z1, xb + 1, 0, Z1, F("smooth_castle_stone"))
        w.fill(xa - 1, 1, Z1, xb + 1, 1, Z1, SL("smooth_castle_stone"))
        for x in (xa - 1, xb + 1):
            w.fill(x, 0, WALL_S + 1, x, 1, WALL_S + 1, F("smooth_castle_stone"))
        # the wall behind the curtain: dark prismarine so the water reads
        w.fill(xa, 1, WALL_S, xb, TOP - 1, WALL_S, F("dark_prismarine"))
        for x in range(xa, xb + 1):
            w.fluid_tick(x, TOP, WALL_S + 1)
    for x in (STAIR[0] - 2, STAIR[1] + 2):
        pass


# ====================================================================================== wall dressing
def walls_dress(w):
    """Alcoves with benches and lanterns, ivy, banners and buttresses between the gates of both walls."""
    used = []
    for th, cx in STREET_GATES:
        used.append((cx - 5, cx + 5))
    used.append((STAIR[0] - 5, STAIR[1] + 5))
    used.append((X1 - 8, X1))                                   # undercroft door
    free = [x for x in range(X0 + 2, X1 - 1) if not any(a <= x <= b for a, b in used)]
    # buttress-like pilasters every 6 blocks on free stretches, alcoves between
    for x in free:
        if x % 6 == 0:
            w.fill(x, 0, WALL_S + 1, x, TOP - 1, WALL_S + 1, F("castle_bricks"))
            w.set(x, TOP, WALL_S + 1, ST("castle_bricks", "north"))
            w.set(x, 0, WALL_S + 1, F("stone_bricks"))
        elif x % 6 == 3 and all(xx in free for xx in (x - 1, x + 1)):
            # alcove: a round-headed niche with a bench and a lantern
            w.fill(x - 1, 1, WALL_S, x + 1, 3, WALL_S, AIR)
            w.set(x, 4, WALL_S, AIR)
            w.set(x - 1, 4, WALL_S, ST("castle_bricks", "west", "top"))
            w.set(x + 1, 4, WALL_S, ST("castle_bricks", "east", "top"))
            w.fill(x - 1, 1, WALL_S - 1, x + 1, 4, WALL_S - 1, F("smooth_castle_stone"))
            w.fill(x - 1, 1, WALL_S, x + 1, 1, WALL_S, ST("spruce", "north"))
            w.set(x, 3, WALL_S, B.lantern(hanging=True))
            w.set(x, 4, WALL_S, B.chain())
        elif x % 6 in (1, 5) and hash2(x, 3, 71) < 0.55:
            top = 2 + int(hash2(x, 1, 72) * 3)
            for y in range(top, TOP + 1):
                w.set(x, y, WALL_S + 1, B.S("vine", north=True), only_empty=True)
    # banners along the street wall: the kingdom and the guild colours
    for x in free:
        if x % 12 == 6:
            w.set(x, TOP - 1, WALL_S + 2, B.wall_banner("blue", "south"), only_empty=True)
            w.be(x, TOP - 1, WALL_S + 2, B.KINGDOM_BANNER)
    # upper wall: pilasters with lamps between the gates
    used_u = [(cx - 5, cx + 5) for _, cx in UPPER_WALL_GATES] + [(STAIR[0] - 2, STAIR[1] + 2)]
    for x in range(X0 + 2, X1 - 1):
        if any(a <= x <= b for a, b in used_u):
            continue
        if x % 4 == 0:
            w.fill(x, TOP + 1, WALL_U + 1, x, UP - 1, WALL_U + 1, F("castle_bricks"))
            w.set(x, UP, WALL_U + 1, ST("castle_bricks", "north"))
            w.set(x, UP - 2, WALL_U + 2, B.lantern(hanging=False), only_empty=True)
            w.set(x, UP - 3, WALL_U + 2, ST("castle_bricks", "north", "top"), only_empty=True)
        elif hash2(x, 9, 73) < 0.4:
            for y in range(TOP + 3 + int(hash2(x, 2, 74) * 3), UP + 1):
                w.set(x, y, WALL_U + 1, B.S("vine", north=True), only_empty=True)


# ====================================================================================== undercroft
def undercroft(w):
    """A vaulted gallery behind the viaduct's arches (x -17..-11, z -84..-50, floor y 0, vault up to y 4) opening
    on the three arches, with a door to the street at its south end (the lower court above stays whole: its east
    approach from the viaduct passage at z -54..-53 runs straight in). Banners in the colours of the twenty gates
    hang along its walls."""
    gx0, gx1 = -17, -11
    zs, zn = -50, -84
    w.fill(gx0 - 1, 0, zn - 1, gx1 + 1, TOP - 1, zs + 1, F("castle_bricks"))
    for z in range(zn, zs + 1):
        for x in range(gx0, gx1 + 1):
            w.set(x, 0, z, F("polished_andesite") if (x + z) % 2 else F("stone_bricks"))
    w.clear(gx0, 1, zn, gx1, 4, zs)
    for z in range(zn, zs + 1):
        rib = z % 5 == 0
        w.set(gx0, 4, z, F("smooth_castle_stone") if rib else ST("castle_bricks", "west", "top"))
        w.set(gx1, 4, z, F("smooth_castle_stone") if rib else ST("castle_bricks", "east", "top"))
        if rib:
            w.set(gx0 + 1, 4, z, ST("smooth_castle_stone", "west", "top"))
            w.set(gx1 - 1, 4, z, ST("smooth_castle_stone", "east", "top"))
            w.fill(gx0 + 2, TOP - 1, z, gx1 - 2, TOP - 1, z, F("smooth_castle_stone"))
        if z % 5 == 2 and z < -56:
            w.set((gx0 + gx1) // 2, 4, z, B.lantern(hanging=True))
    # openings onto the viaduct's arches (the terrace's east face, x = -10)
    for (za, zb) in ((-62, -57), (-72, -67), (-82, -77)):
        w.fill(X1, 1, za, X1, 3, zb, AIR)
        w.fill(X1, 4, za + 1, X1, 4, zb - 1, AIR)
        w.set(X1, 4, za, ST("castle_bricks", "north", "top"))
        w.set(X1, 4, zb, ST("castle_bricks", "south", "top"))
        for z in range(za, zb + 1):
            w.set(X1, 0, z, F("stone_bricks"))
    # the street door
    w.fill(-14, 1, WALL_S, -12, 3, WALL_S + 1, AIR)
    w.set(-13, 4, WALL_S, AIR)
    w.set(-14, 4, WALL_S, ST("castle_bricks", "west", "top"))
    w.set(-12, 4, WALL_S, ST("castle_bricks", "east", "top"))
    w.fill(-14, 0, WALL_S, -12, 0, WALL_S + 1, F("stone_bricks"))
    for x in (-15, -11):
        w.set(x, 3, WALL_S + 1, B.S("wall_torch", facing="south"))
    # banners of the twenty gates along both walls (east wall between the arches)
    slots = [(gx0, z, "east") for z in range(-57, -84, -2)] + \
            [(gx1, z, "west") for z in (-64, -65, -74, -75, -83, -84)]
    themes = [t for t, _ in STREET_GATES] + [WEST_GATE[0]] + [t for t, _ in UPPER_WALL_GATES] + \
             [t for t, _ in NORTH_GATES]
    for (x, z, f), th in zip(slots, themes):
        col = BANNER_COLOR.get(th, "white")
        w.set(x, 3, z, B.wall_banner(col, f))
        w.be(x, 3, z, B.banner_nbt(BANNER_PATTERN.get(th, [("bo", "yellow")])))


BANNER_PATTERN = {
    "northern_longhouse": [("mc", "white"), ("bo", "white")], "mushroom_grove": [("mc", "white"), ("flo", "red")],
    "ancient_ruins": [("bri", "gray"), ("bo", "lime")], "pirate_ship": [("sku", "white"), ("bo", "white")],
    "flower_glasshouse": [("flo", "lime"), ("bo", "white")], "dwarven_forge": [("cr", "black"), ("bo", "black")],
    "desert_palace": [("mr", "light_blue"), ("bo", "orange")], "observatory": [("mc", "yellow"), ("tts", "yellow")],
    "japanese_mansion": [("mc", "white"), ("bo", "black")], "great_tree": [("glb", "green"), ("bo", "green")],
    "sage_library": [("bri", "white"), ("mc", "yellow")], "jungle_temple": [("mr", "yellow"), ("bo", "yellow")],
    "gothic_cathedral": [("cr", "white"), ("bo", "black")], "coral_hall": [("mr", "cyan"), ("bo", "cyan")],
    "volcanic_fortress": [("gra", "red"), ("bo", "black")], "ice_castle": [("mr", "white"), ("bo", "white")],
    "dragon_lair": [("cre", "black"), ("bo", "yellow")], "arcane_tower": [("mr", "magenta"), ("bo", "light_blue")],
    "sky_temple": [("mc", "yellow"), ("bo", "light_blue")], "marble_palace": [("cr", "yellow"), ("bo", "yellow")],
}


BANNER_COLOR = {
    "northern_longhouse": "cyan", "mushroom_grove": "magenta", "ancient_ruins": "green", "pirate_ship": "black",
    "flower_glasshouse": "pink", "dwarven_forge": "orange", "desert_palace": "yellow", "observatory": "blue",
    "japanese_mansion": "red", "great_tree": "lime", "sage_library": "brown", "jungle_temple": "green",
    "gothic_cathedral": "purple", "coral_hall": "pink", "volcanic_fortress": "orange", "ice_castle": "light_blue",
    "dragon_lair": "red", "arcane_tower": "purple", "sky_temple": "white", "marble_palace": "white",
}


# ====================================================================================== gates
def gates(w):
    for th, cx in STREET_GATES:
        GK.gate(w, th, cx, 0, WALL_S - 2, "south")
    for th, cx in UPPER_WALL_GATES:
        GK.gate(w, th, cx, TOP, WALL_U - 2, "south")
    for th, cx in NORTH_GATES:
        GK.gate(w, th, cx, UP, -81, "south")
    th, cz = WEST_GATE
    GK.gate(w, th, X0 + 5, TOP, cz, "east")


# ====================================================================================== the courts
def lower_court(w):
    # fountain on the axis between the two stairs
    cx, cz = AXIS, -58
    S.cylinder(w, cx, cz, 4.4, TOP, TOP, F("smooth_castle_stone"))
    S.cylinder(w, cx, cz, 3.4, TOP, TOP, B.S("water", level=0))
    S.cylinder(w, cx, cz, 4.4, TOP + 1, TOP + 1, SL("smooth_castle_stone"), hollow=True, thickness=0.9)
    w.fill(cx - 1, TOP - 1, cz - 1, cx + 1, TOP - 1, cz + 1, F("prismarine_bricks"))
    S.cylinder(w, cx, cz, 3.4, TOP - 1, TOP - 1, F("prismarine_bricks"))
    w.fill(cx, TOP, cz, cx, TOP + 2, cz, F("smooth_castle_stone"))
    w.set(cx, TOP + 3, cz, F("gilded_trim"))
    for (dx, dz, f) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        w.set(cx + dx, TOP + 2, cz + dz, ST("smooth_castle_stone", OPP[f], "top"))
    w.set(cx, TOP + 4, cz, B.S("fountain_spray", power=2, facing="up"))
    # the broker's pavilion: an open domed pavilion with a counter, the broker behind it facing the court
    bx, bz = BROKER
    pavilion(w, bx, bz, broker=True)
    pavilion(w, -78, -60, broker=False)
    # cherry trees, benches, lamps, flower beds
    for (x, z) in ((-90, -52), (-70, -55), (-38, -55), (-16, -62), (-66, -62), (-44, -62)):
        if w.is_empty(x, TOP + 1, z):
            w.fill(x - 1, TOP, z - 1, x + 1, TOP, z + 1, F("grass_block"))
            w.set(x, TOP, z, F("grass_block"))
            deco.tree_oak(w, x, TOP, z, seed=int(hash2(x, z, 1) * 99), height=4, crown=2.6, kind="cherry",
                          log_kind="cherry")
            for (dx, dz) in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
                w.set(x + dx, TOP + 1, z + dz, B.S("pink_petals", facing="north", flower_amount=3), only_empty=True)
    for (x, z, f) in ((-61, -55, "east"), (-47, -55, "west"), (-61, -61, "east"), (-47, -61, "west")):
        deco.bench(w, x, TOP + 1, z, facing=f, length=3)
    for (x, z) in ((-86, -58), (-50, -63), (-58, -63), (-22, -58), (-12, -51), (-12, -58)):
        if w.is_empty(x, TOP + 1, z):
            deco.lamp_post(w, x, TOP + 1, z, height=4, double=True)
    for (x0, z0, x1, z1) in ((-66, -52, -62, -51), (-46, -52, -42, -51)):
        deco.flower_bed(w, x0, z0, x1, z1, TOP, seed=x0)
    # the bridge entrance from the viaduct: piers with banners and lamps
    for z in (BRIDGE[0] - 1, BRIDGE[1] + 1):
        w.fill(X1, TOP + 1, z, X1, TOP + 2, z, F("smooth_castle_stone"))
        w.set(X1, TOP + 3, z, F("gilded_trim"))
        deco.lamp_post(w, X1, TOP + 3, z, height=2)
    w.fill(X1 - 1, TOP, BRIDGE[0], X1, TOP, BRIDGE[1], F("smooth_castle_stone"))
    for z in range(BRIDGE[0], BRIDGE[1] + 1):
        w.set(X1, TOP, z, F("polished_diorite"))


def pavilion(w, cx, cz, broker=False):
    """An open octagonal pavilion: eight slender columns, a gilded cornice, a blue dome with a lantern."""
    r = 3
    w.fill(cx - r, TOP, cz - r, cx + r, TOP, cz + r, F("polished_diorite"))
    S.cylinder(w, cx, cz, r + 0.4, TOP, TOP, F("smooth_castle_stone"), hollow=True, thickness=0.9)
    for (dx, dz) in ((-3, -1), (-3, 1), (3, -1), (3, 1), (-1, -3), (1, -3), (-1, 3), (1, 3)):
        w.fill(cx + dx, TOP + 1, cz + dz, cx + dx, TOP + 4, cz + dz, B.S("quartz_pillar", axis="y"))
        w.set(cx + dx, TOP + 5, cz + dz, F("gilded_trim"))
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        w.set(cx + dx, TOP + 5, cz + dz, F("smooth_castle_stone"))
    S.cylinder(w, cx, cz, 3.4, TOP + 6, TOP + 6, F("smooth_castle_stone"))
    S.cylinder(w, cx, cz, 3.6, TOP + 6, TOP + 6, SL("gilded_trim"), hollow=True, thickness=0.6)
    for k, rr in enumerate((3.0, 2.5, 1.8, 1.0)):
        S.cylinder(w, cx, cz, rr, TOP + 7 + k, TOP + 7 + k, F("royal_roof_tiles"))
    w.set(cx, TOP + 11, cz, F("gilded_trim"))
    w.set(cx, TOP + 12, cz, B.S("gilded_finial"))
    w.set(cx, TOP + 5, cz, B.chain())
    w.set(cx, TOP + 4, cz, B.S("chandelier"))
    w.fill(cx - r + 1, TOP, cz - r + 1, cx + r - 1, TOP, cz + r - 1, F("polished_diorite"))
    for (dx, dz) in ((0, 0),):
        w.set(cx + dx, TOP, cz + dz, F("gilded_trim"))
    if broker:
        # counter on the north side, the broker behind it facing south (towards the stairs)
        w.fill(cx - 1, TOP + 1, cz - 1, cx + 1, TOP + 1, cz - 1, ST("dark_oak", "south", "top"))
        w.set(cx - 2, TOP + 1, cz - 1, F("dark_oak_planks"))
        w.set(cx + 2, TOP + 1, cz - 1, F("dark_oak_planks"))
        w.set(cx - 2, TOP + 2, cz - 1, B.S("lectern", facing="south", has_book=True))
        w.set(cx + 2, TOP + 2, cz - 1, B.candle(3, "white"))
        w.set(cx, TOP + 2, cz - 1, B.S("flower_pot"))
        w.spot("npc.guild_estates", (cx, TOP + 1, cz - 2), 0.0)
    else:
        # a stand of the twenty gates' colours: a ring of glazed tiles round a bench
        for (dx, dz, f) in ((-1, 0, "east"), (1, 0, "west")):
            deco.bench(w, cx + dx, TOP + 1, cz - 1, facing=f, length=3)
        w.set(cx, TOP + 1, cz, B.potted("flowering_azalea_bush"))


def upper_court(w):
    # the mast with the guild flag on the axis, between the sky temple and the palace
    mx, mz = AXIS, -84
    w.fill(mx - 1, UP + 1, mz - 1, mx + 1, UP + 2, mz + 1, F("smooth_castle_stone"))
    w.fill(mx - 1, UP + 3, mz - 1, mx + 1, UP + 3, mz + 1, SL("gilded_trim"))
    w.set(mx, UP + 3, mz, F("smooth_castle_stone"))
    TK.flagpole(w, mx, UP + 4, mz, 16, "east", design="guild", width=4.0, height=2.6)
    # fountains of light: braziers on the upper court's edge either side of the stair
    for x in (STAIR[0] - 3, STAIR[1] + 3):
        w.set(x, UP + 1, WALL_U - 2, F("smooth_castle_stone"))
        w.set(x, UP + 2, WALL_U - 2, B.S("campfire", lit=True))
    for (x, z) in ((-87, -76), (-77, -76), (-66, -76), (-42, -76), (-31, -76), (-21, -76)):
        if w.is_empty(x, UP + 1, z):
            deco.lamp_post(w, x, UP + 1, z, height=4)
    for (x, z, f) in ((-60, -74, "east"), (-48, -74, "west")):
        deco.bench(w, x, UP + 1, z, facing=f, length=3)
    for (x, z) in ((-97, -74), (-11, -74)):
        w.fill(x, UP, z - 1, x, UP, z + 1, F("grass_block"))
        deco.tree_oak(w, x, UP, z, seed=5, height=5, crown=2.4, kind="cherry", log_kind="cherry")


def people(w):
    TK.crowd(w, "guild_gates", [(-70, TOP + 1, -58), (-40, TOP + 1, -58), (-54, TOP + 1, -53), (-86, TOP + 1, -55),
                                (-20, TOP + 1, -55), (-54, UP + 1, -76), (-80, UP + 1, -76), (-30, UP + 1, -76)],
             need=20)
