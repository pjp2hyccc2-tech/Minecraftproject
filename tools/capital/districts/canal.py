"""大運河 The Grand Canal (2026-11-11 rebuild, after the owner's pictures of a canal town).

The canal comes from the river through the east water gate, runs west along z = 43 (water z 41..45) between the
market/shops blocks and the southern quarters, turns south at x = -88 and leaves through a water gate in the south
wall for the lake. The terrain carves the water (layout.CANAL_*); this district builds the masonry: quay walls with
a pale coping, cobbled quays (north z 39..40, south z 46..47 + the southern quarters' pavement at z 48), landing
stairs with mooring posts and moored boats, the avenue bridge, the two street bridges, humped footbridges, a covered
timber bridge to the inn mansion, lamps, planters and a small square where the canal turns.
"""
from __future__ import annotations

import numpy as np

from capital import blocks as B
from capital import deco
from capital import layout as L
from capital.noise import hash2
from capital.world import X0, Y0, Z0

from . import _civic_kit as K
from . import _maze as MZ
from . import _town_kit as TK

DISTRICT = "canal"

AIR = "minecraft:air"
F = K.F
BED = L.CANAL_BED
WY = L.WATER_Y
SM = "smooth_castle_stone"

VIEWS = {
    "canal_east": dict(pos=(30.5, 4.5, 43.5), look=(60, 1, 43), fov=74, sun=(40, -150), fog=2600,
                       label="The Grand Canal: the covered bridge, the market quay and the inn mansion"),
    "canal_bridge": dict(pos=(-16.5, 3.0, 43.5), look=(0, 1, 43), fov=74, sun=(40, -150), fog=2600,
                         label="The avenue bridge over the Grand Canal"),
    "canal_corner": dict(pos=(-83.5, 6.0, 43.5), look=(-90, 0, 56), fov=78, sun=(40, -150), fog=2600,
                         label="Where the canal turns south"),
}


def _water(T, x, z):
    if not (X0 <= x < X0 + T.canal.shape[0] and Z0 <= z < Z0 + T.canal.shape[1]):
        return False
    return bool(T.canal[x - X0, z - Z0])


def masonry(w, x, y, z, seed=0):
    """Quay-wall stone: mostly stone bricks with mossy/cracked ones low down and a little castle brick."""
    u = hash2(x * 7 + y, z, 3301 + seed)
    if y <= WY and u < 0.35:
        return B.S("mossy_stone_bricks")
    if u < 0.12:
        return B.S("cracked_stone_bricks")
    if u < 0.2:
        return F("castle_bricks")
    return B.S("stone_bricks")


def _pick(mix, x, z, seed):
    """One block name from a weighted mix (deterministic per column)."""
    total = sum(m[1] for m in mix)
    u = hash2(x, z, seed) * total
    for name, wt in mix:
        u -= wt
        if u < 0:
            return name
    return mix[-1][0]


def quays(w, district, T):
    """Masonry under every dry column of the district, a pale coping where it meets the water, cobbled quays."""
    m = district.mask() & w.footprint & ~T.canal
    xi, zi = np.nonzero(m)
    for i, j in zip(xi.tolist(), zi.tolist()):
        x, z = i + X0, j + Z0
        for y in range(BED, 0):
            w.set(x, y, z, masonry(w, x, y, z))
        edge = any(_water(T, x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            w.set(x, 0, z, F(SM) if hash2(x, z, 3310) < 0.85 else B.S("polished_andesite"))
        else:
            w.set(x, 0, z, B.S(_pick(TK.QUAY_MIX, x, z, 3311)))
        w.fill(x, 1, z, x, 5, z, AIR)
    # the bed: gravel and stone, a little clay; a few water plants
    ci, cj = np.nonzero(T.canal & w.footprint)
    for i, j in zip(ci.tolist(), cj.tolist()):
        x, z = i + X0, j + Z0
        if not district.contains(x, z):
            continue
        u = hash2(x, z, 3320)
        w.set(x, BED, z, B.S("gravel") if u < 0.5 else B.S("stone") if u < 0.85 else B.S("clay"))
        w.set(x, BED - 1, z, B.S("stone"))
        if u > 0.97:
            w.set(x, BED + 1, z, B.S("seagrass"))


def landing(w, T, x0, side, n=3):
    """Landing stairs from a main-line quay down to a platform just above the water, a mooring post and a
    moored boat. ``side``: 'north' (quay edge z = 40) or 'south' (quay edge z = 46)."""
    s = 1 if side == "north" else -1
    zs, zp = (41, 42) if side == "north" else (45, 44)
    face = "north" if side == "north" else "south"
    for x in range(x0, x0 + n):
        w.set(x, 0, zs, B.stairs("stone_bricks", face))
        for y in range(BED, 0):
            w.set(x, y, zs, masonry(w, x, y, zs, 1))
        w.set(x, -1, zp, F(SM) if (x - x0) % 2 else B.S("polished_andesite"))
        for y in range(BED, -1):
            w.set(x, y, zp, masonry(w, x, y, zp, 2))
        w.fill(x, 0, zp, x, 3, zp, AIR)
    w.set(x0 + n, -1, zp, B.S("stone_bricks"))
    for y in range(BED, -1):
        w.set(x0 + n, y, zp, masonry(w, x0 + n, y, zp, 3))
    w.set(x0 + n, 0, zp, B.fence("dark_oak"))
    w.set(x0 + n, 1, zp, B.fence("dark_oak"))
    bz = zp + s
    wood = ("spruce", "dark_oak", "oak", "birch")[int(hash2(x0, zp, 3330) * 4)]
    w.entity("minecraft:boat", (x0 + 1.5, WY + 0.7, bz + 0.5), 90.0, '{Type:"%s",Invulnerable:1b}' % wood)


def flat_bridge(w, x0, x1, deck="polished_andesite", lamps=True):
    """A level stone bridge carrying a street over the main line: deck y 0, masonry piers between low arches,
    balustraded parapets on x0 - 1 and x1 + 1, lamps at the four corners."""
    for x in range(x0 - 1, x1 + 2):
        parapet = x in (x0 - 1, x1 + 1)
        pier = parapet or (x - x0) % 4 == 3
        for z in range(41, 46):
            w.set(x, 0, z, F(SM) if parapet else B.S(deck) if hash2(x, z, 3340) < 0.7 else F("plaza_flagstones"))
            if pier:
                for y in range(BED, 0):
                    w.set(x, y, z, masonry(w, x, y, z, 4))
            elif (x - x0) % 4 == 1:
                w.set(x, -1, z, AIR)                                   # the crown of a low arch
            else:
                w.set(x, -1, z, B.stairs("stone_bricks", "east" if (x - x0) % 4 == 0 else "west", "top"))
            if parapet:
                w.set(x, 1, z, B.wall("stone_bricks") if z in (41, 45) else B.S("stone_brick_wall"))
                w.set(x, 2, z, B.slab(SM, "bottom") if z not in (41, 45) else F(SM))
    if lamps:
        for x in (x0 - 1, x1 + 1):
            for z in (40, 46):
                K.lamp(w, x, 0, z, height=4)


def hump_bridge(w, x0, width=3, wood_rail=False):
    """A humped stone footbridge over the main line at x0..x0+width-1: steps up from both quays to a crown at
    y 2 over the middle of the water, an arch below, parapets with lanterns on the end posts."""
    prof = {40: (0, None), 41: (1, "south"), 42: (2, "south"), 43: (2, None), 44: (2, "north"), 45: (1, "north"),
            46: (0, None)}
    for x in range(x0 - 1, x0 + width + 1):
        rail = x in (x0 - 1, x0 + width)
        for z, (y, face) in prof.items():
            if z in (40, 46):
                if rail:
                    w.set(x, 1, z, B.wall("stone_bricks"))
                    w.set(x, 2, z, B.lantern())
                continue
            # body: springers on the outer water rows, an arch over the middle
            low = 0 if z in (41, 45) else 1
            for yy in range(BED if z in (41, 45) else low, y):
                w.set(x, yy, z, masonry(w, x, yy, z, 5))
            if z == 43:
                w.set(x, 0, z, B.stairs("stone_bricks", "south", "top"))
                w.set(x, 1, z, F(SM))
            if rail:
                w.set(x, y, z, F(SM) if face is None else B.stairs("stone_bricks", face))
                w.set(x, y + 1, z, B.fence("dark_oak") if wood_rail else B.wall("stone_bricks"))
            else:
                w.set(x, y, z, B.stairs("stone_bricks", face) if face else F(SM))
                w.fill(x, y + 1, z, x, y + 3, z, AIR)


def culvert(w, x0=40, width=3):
    """The mouth of the underground waterway in the north quay wall (x0..x0+width-1): the canal runs on under the
    quay with a hand's breadth of air under the paving (swimmers and boats fit) and pours down a shaft into the
    waterway under the market square (districts/_undercroft.py builds that side). Pale stone over the mouth and
    mossy jambs either side of it."""
    for x in range(x0, x0 + width):
        for z in (39, 40):
            w.set(x, BED, z, B.S("stone"))
            w.fill(x, BED + 1, z, x, WY, z, B.S("water"))
            w.set(x, WY + 1, z, AIR)
            w.set(x, 0, z, F(SM))
        for y in range(BED + 1, WY + 1):
            w.fluid_tick(x, y, 39)                     # the water starts pouring down the shaft when it loads
    for x in (x0 - 1, x0 + width):
        for y in range(BED, 1):
            w.set(x, y, 40, B.S("mossy_stone_bricks") if y <= WY else F(SM))


def festoons(w, xs):
    """Strings of paper lanterns across the canal between tall posts: a lantern post on the north quay (z 39) and
    the lamp opposite on the south quay (z 47), raised for it; the string hangs from hooks on the water side of the
    two posts (z 40 and z 46) at y 6."""
    for x in xs:
        if not all(w.is_empty(x, y, z) for y in range(1, 9) for z in (39, 40)):
            continue
        w.set(x, 1, 39, B.S("polished_blackstone"))
        w.fill(x, 2, 39, x, 7, 39, B.fence("dark_oak"))
        w.set(x, 8, 39, B.lantern())
        deco.lamp_post(w, x, 0, 47, height=6)
        MZ.bunting(w, x, 6, 40, (0, 0, 6), style="lanterns", colors=(1, 4, 14, 0), sag=1.0, wind=0.4, facing="south")


def covered_bridge(w, x0):
    """A covered timber bridge (x0..x0+4: plaster walls on x0 and x0+4 with leaded windows, a gabled roof),
    level with the quays, joining the market side to the inn mansion's quay."""
    x1 = x0 + 4
    for z in range(39, 48):
        over = 41 <= z <= 45
        for x in range(x0, x1 + 1):
            w.set(x, 0, z, B.S("spruce_planks") if 0 < x - x0 < 4 else B.S("stripped_dark_oak_log", axis="z"))
            if over:
                w.set(x, -1, z, B.S("dark_oak_planks") if 0 < x - x0 < 4 else B.S("dark_oak_log", axis="z"))
        if over and z in (41, 45):
            for x in (x0, x1):
                for y in range(BED, -1):
                    w.set(x, y, z, masonry(w, x, y, z, 6))
        if 40 <= z <= 46:
            for x in (x0, x1):
                post = z in (40, 43, 46)
                for y in range(1, 4):
                    if post:
                        w.set(x, y, z, B.log("dark_oak"))
                    elif y == 1:
                        w.set(x, y, z, F("white_plaster"))
                    else:
                        w.set(x, y, z, TK.LEADED)
                w.set(x, 4, z, B.log("dark_oak", "z"))
            w.fill(x0 + 1, 1, z, x1 - 1, 3, z, AIR)
            # gable roof along z
            w.set(x0 - 1, 4, z, B.stairs("royal_roof_tiles", "east"))
            w.set(x1 + 1, 4, z, B.stairs("royal_roof_tiles", "west"))
            w.set(x0, 5, z, B.stairs("royal_roof_tiles", "east"))
            w.set(x1, 5, z, B.stairs("royal_roof_tiles", "west"))
            w.set(x0 + 1, 6, z, B.stairs("royal_roof_tiles", "east"))
            w.set(x1 - 1, 6, z, B.stairs("royal_roof_tiles", "west"))
            w.set(x0 + 2, 6, z, F("royal_roof_tiles"))
            w.set(x0 + 2, 7, z, B.slab("royal_roof_tiles", "bottom"))
            if z in (40, 46):
                w.fill(x0 + 1, 4, z, x1 - 1, 5, z, B.S("dark_oak_planks"))     # the gable ends
            else:
                w.fill(x0 + 1, 4, z, x1 - 1, 4, z, B.S("spruce_planks"))       # the ceiling
        if z in (40, 46):
            # portals: open arches with a lantern and a sign board over them
            w.fill(x0 + 1, 1, z, x1 - 1, 3, z, AIR)
            w.set(x0 + 2, 4, z, B.S("dark_oak_planks"))
    for z in (41, 43, 45):
        deco.hanging_lantern(w, x0 + 2, 4, z, drop=1)


def bend_square(w, T):
    """The little square where the canal turns south (x -98..-91, z 39..47): a wall fountain, a bench, a lamp."""
    TK.pave(w, -98, 39, -91, 47, TK.SQUARE_MIX, seed=3350, only_ground=False)
    K.lamp(w, -94, 0, 46, height=4)
    deco.bench(w, -97, 0, 44, facing="east", length=3)
    K.planter_tree(w, -96, 0, 41, kind="cherry", seed=3351)


def branch(w, T):
    """The south branch inside the walls: quays on both sides, a humped bridge (along x) at z 59..61, lamps."""
    # humped bridge across the branch (water x -90..-86), deck rising from the quays at x -91 / -85
    prof = {-91: (0, None), -90: (1, "east"), -89: (2, "east"), -88: (2, None), -87: (2, "west"),
            -86: (1, "west"), -85: (0, None)}
    for z in range(58, 63):
        rail = z in (58, 62)
        for x, (y, face) in prof.items():
            if x in (-91, -85):
                if rail:
                    w.set(x, 1, z, B.wall("stone_bricks"))
                    w.set(x, 2, z, B.lantern())
                continue
            for yy in range(BED if x in (-90, -86) else (0 if x in (-90, -86) else 1), y):
                w.set(x, yy, z, masonry(w, x, yy, z, 7))
            if x == -88:
                w.set(x, 0, z, B.stairs("stone_bricks", "east", "top"))
                w.set(x, 1, z, F(SM))
            if rail:
                w.set(x, y, z, F(SM) if face is None else B.stairs("stone_bricks", face))
                w.set(x, y + 1, z, B.wall("stone_bricks"))
            else:
                w.set(x, y, z, B.stairs("stone_bricks", face) if face else F(SM))
                w.fill(x, y + 1, z, x, y + 3, z, AIR)
    for z in (50, 70):
        K.lamp(w, -84, 0, z, height=4)
        K.lamp(w, -92, 0, z + 6, height=4)
    # a landing on the east quay with a boat
    for z in range(65, 68):
        w.set(-86, 0, z, B.stairs("stone_bricks", "east"))
        for y in range(BED, 0):
            w.set(-86, y, z, masonry(w, -86, y, z, 8))
        w.set(-87, -1, z, F(SM))
        for y in range(BED, -1):
            w.set(-87, y, z, masonry(w, -87, y, z, 9))
        w.fill(-87, 0, z, -87, 3, z, AIR)
    w.entity("minecraft:boat", (-88.5, WY + 0.6, 66.5), 0.0, '{Type:"spruce",Invulnerable:1b}')


def outside(w, T):
    """Beyond the south wall the branch runs between grassy banks with a stone edging to the lake; a plank
    footbridge carries the field track over it."""
    for z in range(87, 109):
        for x in (-92, -91, -85, -84):
            if T.canal[x - X0, z - Z0]:
                continue
            w.set(x, 0, z, F(SM) if x in (-91, -85) else B.S("grass_block"))
            for y in range(BED, 0):
                w.set(x, y, z, masonry(w, x, y, z, 10))
    for x in range(-91, -84):
        w.set(x, 0, 92, B.S("spruce_planks"))
        w.set(x, 1, 91, B.fence("spruce"))
        w.set(x, 1, 93, B.fence("spruce"))


def east_mouth(w, T):
    """Outside the east water gate: the channel meets the river between masonry walls."""
    for x in range(113, 123):
        for z in (39, 40, 46, 47):
            if T.canal[x - X0, z - Z0] or T.river[x - X0, z - Z0]:
                continue
            for y in range(BED, 0):
                w.set(x, y, z, masonry(w, x, y, z, 11))
            w.set(x, 0, z, F(SM) if z in (40, 46) else B.S("grass_block"))


def build(w, district, terrain):
    T = terrain
    quays(w, district, T)
    bend_square(w, T)
    # bridges: the avenue, the two streets, humped footbridges, the covered bridge to the inn mansion
    flat_bridge(w, -5, 5)
    flat_bridge(w, -58, -52)
    flat_bridge(w, 52, 58)
    for x in (-78, -30, 22, 73):
        hump_bridge(w, x)
    covered_bridge(w, 42)
    culvert(w, 40)
    # landings with boats
    for x in (-70, -16, 64, 88):
        landing(w, T, x, "north")
    for x in (-44, 12, 46, 64):
        landing(w, T, x, "south")
    # lamps and bollards on the south quay, clear of the bridge heads and landings
    busy = [(-80, -75), (-60, -50), (-33, -26), (-7, 7), (19, 26), (41, 47), (50, 60), (70, 77),
            (-45, -40), (11, 16), (45, 50), (63, 68)]

    def free(x):
        return not any(a - 1 <= x <= b + 1 for a, b in busy)
    lamps = [x for x in range(-80, 98, 16) if free(x)]
    for x in lamps:
        deco.lamp_post(w, x, 0, 47, height=4)
    for x in range(-84, 98, 8):
        if free(x) and free(x + 1) and w.is_empty(x, 1, 46):
            w.set(x, 1, 46, B.S("polished_blackstone_wall"))
    festoons(w, [x for x in lamps if not any(a - 1 <= x <= a + 4 for a in (-70, -16, 64, 88, 40))])
    branch(w, T)
    outside(w, T)
    east_mouth(w, T)
    TK.crowd(w, "canal", [(-20, 1, 47), (30, 1, 39), (60, 1, 47), (-60, 1, 39), (-94, 1, 42)], need=10)
