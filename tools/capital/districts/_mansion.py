"""星灯りの館 The Starlight Manor: the inn mansion of the player houses (2026-11-11 rebuild).

The owner: 「プレイヤーハウスは宿屋、ファンタジーなマンションみたいにして、主に荷物の管理をする場所にする。」 The rooms
themselves are instances (the estates system); here stands the building every room belongs to: a stone ground
floor with an arcaded portico on the canal, three timber-framed floors around a skylit atrium with galleries, room
doors (``eternalrealm:room_door``: each leads into the user's own room) along every gallery, a reception with the
innkeeper and a wall of keys, lounges with a fireplace, front corner turrets and a clock-and-lantern tower.

Local frame: front (north) wall at z = 0, x 0..35 west to east, z 0..21; floors at y 0 / 6 / 11 / 16, eaves 21.
Spots: ``npc.innkeeper`` behind the desk, ``estate.lobby`` in the atrium.
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2
from capital.world import Frame

from . import _civic_kit as K
from . import _furnish as FU
from . import _town_kit as TK

AIR = "minecraft:air"
F = K.F
W, D = 36, 22
FLOORS = (0, 6, 11, 16)
EAVE = 21
ATRIUM = (11, 5, 24, 15)            # x0, z0, x1, z1 open from the ground to the skylight
RING = (9, 3, 26, 17)               # galleries around the atrium on the upper floors
PLASTER = F("white_plaster")
TIMBER = B.log("dark_oak")
ROOF = "royal_roof_tiles"


def _shell(f):
    """Walls: castle-stone ground floor with pale quoins, timber-framed plaster above, floors and the attic."""
    f.fill(0, 1, 0, W - 1, EAVE + 1, D - 1, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, F("polished_andesite"))
    for x in range(W):
        for z in range(D):
            if 0 < x < W - 1 and 0 < z < D - 1:
                f.set(x, 0, z, B.S("dark_oak_planks") if (x + z) % 7 else B.S("spruce_planks"))
    for y in range(1, FLOORS[1]):
        for x in range(W):
            for z in (0, D - 1):
                f.set(x, y, z, F("smooth_castle_stone") if x in (0, W - 1) or x % 6 == 0 else F("castle_bricks"))
        for z in range(D):
            for x in (0, W - 1):
                f.set(x, y, z, F("smooth_castle_stone") if z in (0, D - 1) or z % 6 == 0 else F("castle_bricks"))
    S.rect_ring(f, 0, 0, W - 1, D - 1, FLOORS[1], F("smooth_castle_stone"))     # string course
    for k, fy in enumerate(FLOORS[1:]):
        top = FLOORS[k + 2] if k + 2 < len(FLOORS) else EAVE
        for y in range(fy + 1, top):
            for x in range(W):
                for z in (0, D - 1):
                    f.set(x, y, z, TIMBER if x % 3 == 0 else PLASTER)
            for z in range(D):
                for x in (0, W - 1):
                    f.set(x, y, z, TIMBER if z % 3 == 0 else PLASTER)
        S.rect_ring(f, 0, 0, W - 1, D - 1, top, B.log("dark_oak", "x"))
        f.fill(1, fy, 1, W - 2, fy, D - 2, B.S("spruce_planks"))
    f.fill(1, EAVE, 1, W - 2, EAVE, D - 2, B.S("spruce_planks"))
    # the atrium: open from the ground floor to the skylight
    x0, z0, x1, z1 = ATRIUM
    for fy in FLOORS[1:] + (EAVE,):
        f.fill(x0, fy, z0, x1, fy, z1, AIR)


def _windows(f):
    """Arched leaded windows on the ground floor, paired leaded casements with flower boxes and shutters above."""
    for x in range(2, W - 2):
        if x % 6 in (2, 3) and not 14 <= x <= 21:
            for z, out in ((0, -1), (D - 1, 1)):
                f.fill(x, 2, z, x, 4, z, TK.LEADED)
                f.set(x, 5, z, B.stairs("smooth_castle_stone", "south" if out < 0 else "north", "top"))
    for z in range(3, D - 3):
        if z % 6 in (2, 3):
            for x in (0, W - 1):
                f.fill(x, 2, z, x, 4, z, TK.LEADED)
    for fy in FLOORS[1:]:
        for x in range(1, W - 1):
            if x % 3 == 0:
                continue
            for z, out in ((0, -1), (D - 1, 1)):
                f.fill(x, fy + 2, z, x, fy + 3, z, TK.LEADED)
                # a flower box under the window: a trapdoor ledge with potted flowers
                f.set(x, fy + 1, z + out, B.S("spruce_trapdoor", facing="north" if out < 0 else "south",
                                              half="top", open=False))
                if hash2(x, fy + z, 5101) < 0.7:
                    flower = ("red_tulip", "blue_orchid", "allium", "oxeye_daisy", "pink_tulip", "cornflower")[
                        int(hash2(x, fy + z, 5102) * 6)]
                    f.set(x, fy + 2, z + out, B.potted(flower))
        for z in range(1, D - 1):
            if z % 3 == 0:
                continue
            for x in (0, W - 1):
                f.fill(x, fy + 2, z, x, fy + 3, z, TK.LEADED)


def _portico(f):
    """The arcaded portico on the canal side: five arches on pale columns carrying a balcony, the double doors,
    a hanging sign with a key, lanterns, banners."""
    cx0, cx1 = 13, 22
    for x in range(cx0, cx1 + 1):
        for z in range(-3, 0):
            f.set(x, 0, z, F("plaza_flagstones") if (x + z) % 3 else F("smooth_castle_stone"))
            f.fill(x, 1, z, x, 5, z, AIR)
    for x in range(cx0, cx1 + 1, 3):
        for y in range(1, 5):
            f.set(x, y, -3, F("castle_pillar"))
        f.set(x, 5, -3, F("carved_castle_stone"))
    for x in range(cx0, cx1 + 1):
        if (x - cx0) % 3:
            f.set(x, 5, -3, F("smooth_castle_stone"))
            f.set(x, 4, -3, B.stairs("smooth_castle_stone", "east" if (x - cx0) % 3 == 1 else "west", "top"))
    f.fill(cx0, 6, -3, cx1, 6, -1, F("smooth_castle_stone"))
    for x in range(cx0, cx1 + 1):
        f.set(x, 7, -3, B.wall("castle_bricks") if (x - cx0) % 3 == 0 else B.S("dark_oak_fence"))
    for z in (-2, -1):
        f.set(cx0, 7, z, B.wall("castle_bricks"))
        f.set(cx1, 7, z, B.wall("castle_bricks"))
    for x in (cx0 + 1, cx1 - 1):
        f.set(x, 7, -2, B.potted("flowering_azalea_bush"))
    # doors (a door onto the balcony too)
    lo, hi = B.door_states("dark_oak", "north", "left")
    f.set(17, 1, 0, lo)
    f.set(17, 2, 0, hi)
    lo, hi = B.door_states("dark_oak", "north", "right")
    f.set(18, 1, 0, lo)
    f.set(18, 2, 0, hi)
    f.fill(16, 3, 0, 19, 4, 0, TK.LEADED)
    f.set(16, 1, 0, F("smooth_castle_stone"))
    f.set(19, 1, 0, F("smooth_castle_stone"))
    f.set(16, 2, 0, F("smooth_castle_stone"))
    f.set(19, 2, 0, F("smooth_castle_stone"))
    lo, hi = B.door_states("spruce", "north", "left")
    f.set(17, 7, 0, lo)
    f.set(17, 8, 0, hi)
    lo, hi = B.door_states("spruce", "north", "right")
    f.set(18, 7, 0, lo)
    f.set(18, 8, 0, hi)
    for x in (cx0 + 1, cx1 - 1):
        deco.hanging_lantern(f, x, 5, -2, drop=1)
    for x in (cx0 - 1, cx1 + 1):
        deco.kingdom_wall_banner(f, x, 9, -1, facing="north")
    # the name board over the portico
    deco.sign_text(f, 17, 10, -1, B.S("dark_oak_wall_sign", facing="north"), ["星灯りの館", "Starlight", "Manor", "宿屋"],
                   color="yellow", glowing=True)
    deco.sign_text(f, 18, 10, -1, B.S("dark_oak_wall_sign", facing="north"), ["お部屋あります", "Rooms to let", "", ""],
                   color="white", glowing=True)


def _turret(f, cx, cz):
    """Round corner turret: castle stone below, plaster with timber above, a tall blue cone with a finial."""
    S.cylinder(f, cx, cz, 3.2, 1, EAVE + 3, F("castle_bricks"), hollow=True)
    for y in range(FLOORS[1] + 1, EAVE + 3):
        S.cylinder(f, cx, cz, 3.2, y, y, PLASTER if y % 5 else TIMBER, hollow=True)
    S.cylinder(f, cx, cz, 2.2, EAVE + 3, EAVE + 3, F("spruce_planks"))
    S.corbel_ring(f, cx, cz, 3.2, EAVE + 3, "smooth_castle_stone")
    S.cone(f, cx, cz, 4.0, EAVE + 4, 12, ROOF, finial=True)
    for y in (FLOORS[1] + 2, FLOORS[2] + 2, FLOORS[3] + 2):
        for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            if f.get(cx + dx, y, cz + dz) != AIR:
                f.set(cx + dx, y, cz + dz, TK.LEADED)
                f.set(cx + dx, y + 1, cz + dz, TK.LEADED)


def _roof(f):
    """A hipped blue roof with dormers, a glazed lantern over the atrium and the clock-and-lantern tower."""
    S.hip_roof(f, 0, 0, W - 1, D - 1, EAVE + 1, ROOF, overhang=1)
    # dormers on the long sides
    for x in range(4, W - 4, 6):
        if 14 <= x <= 21:
            continue
        for z, face in ((1, "north"), (D - 2, "south")):
            zz = z - 1 if face == "north" else z + 1
            for y in range(EAVE + 1, EAVE + 4):
                f.fill(x - 1, y, z, x + 1, y, z, PLASTER)
            f.fill(x, EAVE + 2, zz, x, EAVE + 3, zz, TK.LEADED)
            f.set(x - 1, EAVE + 2, zz, TIMBER)
            f.set(x + 1, EAVE + 2, zz, TIMBER)
            f.set(x - 1, EAVE + 4, zz, B.stairs(ROOF, "east"))
            f.set(x + 1, EAVE + 4, zz, B.stairs(ROOF, "west"))
            f.set(x, EAVE + 5, zz, B.slab(ROOF, "bottom"))
    # glazed lantern over the atrium
    x0, z0, x1, z1 = ATRIUM
    f.fill(x0, EAVE + 1, z0, x1, EAVE + 7, z1, AIR)
    for y in range(EAVE + 1, EAVE + 6):
        for x in range(x0 - 1, x1 + 2):
            for z in (z0 - 1, z1 + 1):
                f.set(x, y, z, TIMBER if (x - x0) % 4 == 0 or y == EAVE + 1 else TK.LEADED)
        for z in range(z0 - 1, z1 + 2):
            for x in (x0 - 1, x1 + 1):
                f.set(x, y, z, TIMBER if (z - z0) % 4 == 0 or y == EAVE + 1 else TK.LEADED)
    S.hip_roof(f, x0 - 1, z0 - 1, x1 + 1, z1 + 1, EAVE + 6, ROOF, overhang=1)
    f.fill(x0 + 2, EAVE + 6, z0 + 2, x1 - 2, EAVE + 9, z1 - 2, B.S("air"))
    f.fill(x0 + 2, EAVE + 6, z0 + 2, x1 - 2, EAVE + 6, z1 - 2, B.S("glass"))
    # the clock-and-lantern tower over the portico
    tx0, tx1, tz0, tz1 = 15, 20, -1, 3
    for y in range(EAVE + 1, EAVE + 12):
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                edge = x in (tx0, tx1) or z in (tz0, tz1)
                if edge:
                    f.set(x, y, z, F("smooth_castle_stone") if (x in (tx0, tx1) and z in (tz0, tz1))
                          else F("castle_bricks"))
                else:
                    f.set(x, y, z, AIR)
    TK.clock(f, 17, EAVE + 7, tz0, "north", size=2.0)
    f.set(18, EAVE + 7, tz0, F("castle_bricks"))
    # belfry with open arches and the lantern of the manor's name
    for y in range(EAVE + 12, EAVE + 16):
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                corner = x in (tx0, tx1) and z in (tz0, tz1)
                f.set(x, y, z, F("castle_pillar") if corner else AIR)
    f.fill(tx0, EAVE + 16, tz0, tx1, EAVE + 16, tz1, F("smooth_castle_stone"))
    for (x, z) in ((17, 1), (18, 1)):
        f.set(x, EAVE + 15, z, B.S("chain", axis="y"))
        f.set(x, EAVE + 14, z, B.lantern(hanging=True))
    S.square_spire(f, 17.5, 1, 3, EAVE + 17, 10, ROOF, finial=True, even=True)


def _atrium(f):
    """Galleries with balustrades round the atrium, the staircases, chandeliers, the reception, the lounges."""
    x0, z0, x1, z1 = ATRIUM
    for fy in FLOORS[1:]:
        S.rect_ring(f, x0 - 1, z0 - 1, x1 + 1, z1 + 1, fy + 1, B.fence("dark_oak"))
        for (x, z) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
            f.set(x, fy + 1, z, B.S("dark_oak_log", axis="y"))
            f.set(x, fy + 2, z, B.lantern())
        # a carved rail edge under the gallery floor
        for x in range(x0, x1 + 1):
            f.set(x, fy - 1, z0 - 1, B.stairs("dark_oak", "south", "top"))
            f.set(x, fy - 1, z1 + 1, B.stairs("dark_oak", "north", "top"))
        for z in range(z0, z1 + 1):
            f.set(x0 - 1, fy - 1, z, B.stairs("dark_oak", "east", "top"))
            f.set(x1 + 1, fy - 1, z, B.stairs("dark_oak", "west", "top"))
    # staircases (build before any dressing)
    FU.stair_up(f, 25, 16, FLOORS[0], FLOORS[1] - FLOORS[0], "north", wood="dark_oak", width=2, side="east")
    FU.stair_up(f, 9, 4, FLOORS[1], FLOORS[2] - FLOORS[1], "south", wood="dark_oak", width=2, side="east")
    FU.stair_up(f, 25, 16, FLOORS[2], FLOORS[3] - FLOORS[2], "north", wood="dark_oak", width=2, side="east")
    # rails along the stairwell openings
    for fy, (sx, sz0, sz1) in ((FLOORS[1], (25, 13, 16)), (FLOORS[3], (25, 14, 16))):
        for z in range(sz0, sz1 + 1):
            if f.is_empty(24, fy + 1, z) and f.get(24, fy, z) != AIR:
                f.set(24, fy + 1, z, B.fence("dark_oak"))
    # chandeliers down the atrium
    for (x, z) in ((14, 8), (21, 8), (14, 12), (21, 12), (17, 10)):
        deco.chandelier(f, x, EAVE + 5, z, drop=7 if (x, z) == (17, 10) else 5)


def _reception(f):
    """The reception desk at the back of the atrium with the wall of keys, the guest book and a bell."""
    for x in range(13, 23):
        f.set(x, 1, 17, B.S("stripped_dark_oak_log", axis="x"))
        f.set(x, 2, 17, B.slab("dark_oak", "top"))
    f.set(12, 1, 18, B.S("stripped_dark_oak_log", axis="z"))
    f.set(23, 1, 18, B.S("stripped_dark_oak_log", axis="z"))
    f.set(12, 2, 18, B.slab("dark_oak", "top"))
    f.set(23, 2, 18, B.slab("dark_oak", "top"))
    f.set(14, 3, 17, B.S("bell", attachment="floor", facing="north"))
    f.set(20, 3, 17, B.candle(3, "white"))
    f.set(21, 3, 17, B.potted("flowering_azalea_bush"))
    # the wall of keys behind the desk
    for x in range(13, 23):
        for y in (2, 3, 4):
            f.set(x, y, 21, B.S("dark_oak_planks"))
    for x in range(13, 23):
        for y in (2, 3):
            deco.item_frame(f, x, y, 20, "north", "minecraft:tripwire_hook", rotation=(x + y) % 8)
    deco.sign_text(f, 17, 4, 20, B.S("dark_oak_wall_sign", facing="north"), ["鍵", "Keys", "", ""], color="yellow")
    deco.sign_text(f, 18, 4, 20, B.S("dark_oak_wall_sign", facing="north"), ["受付", "Reception", "", ""],
                   color="yellow")
    f.spot("npc.innkeeper", (17.5, 1, 19.5), 180.0)


def _lounges(f):
    """West lounge with a fireplace and armchairs, east dining corner, rugs and plants in the lobby."""
    # fireplace on the west wall
    for z in range(9, 13):
        for y in range(1, 6):
            f.set(1, y, z, F("castle_bricks") if z in (9, 12) or y >= 4 else AIR)
    f.set(1, 1, 10, B.S("campfire", lit=True))
    f.set(1, 1, 11, B.S("campfire", lit=True))
    f.set(0, 1, 10, F("castle_bricks"))
    f.set(0, 1, 11, F("castle_bricks"))
    for z in range(9, 13):
        f.set(2, 4, z, B.stairs("smooth_castle_stone", "east", "top"))
    for (x, z, face) in ((4, 9, "west"), (4, 12, "west"), (6, 10, "east"), (6, 11, "east")):
        f.set(x, 1, z, B.stairs("spruce", face))
    f.set(5, 1, 10, B.slab("dark_oak", "top"))
    f.set(5, 1, 11, B.slab("dark_oak", "top"))
    f.set(5, 2, 10, B.candle(2, "yellow"))
    FU.rug(f, 3, 8, 7, 13, 0, field="red", border="yellow")
    # dining corner east
    for z in (8, 12):
        deco.long_table(f, 30, 0, z, 4, axis="x", top="spruce", cloth="white", chair_wood="dark_oak")
    FU.rug(f, 12, 6, 23, 14, 0, field="blue", border="light_blue")
    FU.plants(f, [(11, 5), (24, 5), (11, 15), (24, 15), (2, 2), (33, 2), (2, 20), (33, 20)], 0, seed=5120)
    f.spot("estate.lobby", (17.5, 1, 10.5), 180.0)


def _gallery_walls(f):
    """Panelled walls between the galleries and the rooms behind them on the upper floors: spruce panelling
    between dark oak posts under a moulded beam."""
    x0, z0, x1, z1 = 8, 2, 27, 18
    for k, fy in enumerate(FLOORS[1:]):
        top = FLOORS[k + 2] if k + 2 < len(FLOORS) else EAVE
        for y in range(fy + 1, top):
            for x in range(x0, x1 + 1):
                for z in (z0, z1):
                    f.set(x, y, z, TIMBER if (x - x0) % 3 == 1 or y == top - 1 else B.S("spruce_planks"))
            for z in range(z0, z1 + 1):
                for x in (x0, x1):
                    f.set(x, y, z, TIMBER if (z - z0) % 4 == 0 or y == top - 1 else B.S("spruce_planks"))


def _room_door(f, x, fy, z, face):
    """One room door in a gallery wall (opening onto the gallery towards ``face``) with a sconce beside it; none
    where the gallery floor in front of it is a stairwell. Returns whether a door was set."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[face]
    if f.get(x + dx, fy, z + dz) == AIR or not f.is_empty(x + dx, fy + 1, z + dz):
        return False
    f.set(x, fy + 1, z, B.S("eternalrealm:room_door", facing=face, half="lower", hinge="left"))
    f.set(x, fy + 2, z, B.S("eternalrealm:room_door", facing=face, half="upper", hinge="left"))
    sx, sz = {"north": (1, 0), "south": (-1, 0), "east": (0, 1), "west": (0, -1)}[face]
    lx, lz = x + sx + dx, z + sz + dz
    if f.is_empty(lx, fy + 3, lz) and f.get(x + sx, fy + 3, z + sz) != AIR:
        f.set(lx, fy + 3, lz, B.S("wall_torch", facing=face))
    return True


def _room_doors(f):
    """Room doors along every gallery wall (each leads into the user's own room), with a brass number lamp."""
    n = 0
    for k, fy in enumerate(FLOORS[1:]):
        # doors in the walls that bound the galleries: north wall z 2, south wall z 18, west x 8, east x 27
        for x in range(11, 25, 3):
            for (z, face) in ((2, "south"), (18, "north")):
                n += _room_door(f, x, fy, z, face)
        for z in range(6, 15, 4):
            for (x, face) in ((8, "east"), (27, "west")):
                n += _room_door(f, x, fy, z, face)
    return n


def _band_rooms(f):
    """The rooms behind the gallery walls are never entered (every door leads into one's own room), but their
    windows face the town: beds, tables, lamps and curtains so the façade looks lived in."""
    for fy in FLOORS[1:]:
        for x in range(1, W - 1, 4):
            for (z0, z1, head) in ((1, 1, "east"), (19, 20, "south")):
                if f.is_empty(x, fy + 1, z1):
                    FU.bed_pair(f, x, fy, z1, "east", color=("red", "blue", "green", "purple")[(x // 4) % 4])
                if f.is_empty(x + 2, fy + 1, z0):
                    f.set(x + 2, fy + 1, z0, B.S("barrel", facing="up"))
                    f.set(x + 2, fy + 2, z0, B.lantern())
        for z in range(4, D - 4, 4):
            for x in (2, W - 3):
                if f.is_empty(x, fy + 1, z):
                    FU.bed_pair(f, x, fy, z, "south", color="white")


def inn_mansion(w, x0, z0):
    """Build the Starlight Manor with its front (north) wall on z = z0, west wall on x = x0."""
    f = Frame(w, (x0, 0, z0), 0)
    _shell(f)
    _windows(f)
    _turret(f, 0, 0)
    _turret(f, W - 1, 0)
    _atrium(f)
    _reception(f)
    _lounges(f)
    _gallery_walls(f)
    doors = _room_doors(f)
    _band_rooms(f)
    _portico(f)
    _roof(f)
    return doors
