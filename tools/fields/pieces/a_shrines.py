"""Waystone shrines (転移碑) at the eleven ``FieldLayout.waystones()`` points.

Each shrine is a paved platform fitted to the slope (foundations and steps where the ground falls away, a cut and a
retaining kerb where it rises, the natural ground eased back around it) with the waystone in the centre: the block
``eternalrealm:waystone`` (lower half on the platform, upper half above it, facing the main approach), a free ring
of two blocks around it, and standing stones, lights and plants themed after the field.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital.noise import hash2, hash3

from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S


def waystones():
    return {w["id"]: w for w in probe.layout()["waystones"]}


def place_waystone(P, wid, x, y, z, facing):
    """The 転移碑 itself: lower half standing on the block at y, upper half above."""
    P.set(x, y + 1, z, B.S("waystone", half="lower", facing=facing))
    P.set(x, y + 2, z, B.S("waystone", half="upper", facing=facing))
    P.fill(x, y + 3, z, x, y + 5, z, AIR)            # head room for the floating crystal
    P.waystone = (x, y + 1, z, facing, wid)


def platform(P, cx, cz, r, y, mix, found, edge=None, shape="octagon", blend=5, seed=0, cut_kerb=None):
    """Paved platform with its top block at y (players stand at y + 1), fitted to the terrain."""
    m = K.disc(P, cx, cz, r, shape)
    for (x, z) in K.cells(m, P):
        t = P.top(x, z)
        if t < y:
            P.fill(x, min(t - 1, y - 1), z, x, y - 1, z, F(found))
        else:
            P.fill(x, y - 2, z, x, y - 1, z, F(found))
            P.fill(x, y + 1, z, x, max(t, y) + 3, z, AIR)
        K.mixed(P, x, y, z, mix, seed)
    if edge:
        for (x, z) in K.cells(K.ring(m), P):
            P.set(x, y, z, F(edge) if isinstance(edge, str) and ":" not in edge and "[" not in edge else edge)
    if cut_kerb:
        # where the natural ground stands higher than the platform, a low kerb holds it back
        for (x, z) in K.cells(K.ring(K.dilate(m, 1)) & ~m, P):
            if P.top(x, z) > y:
                P.set(x, y + 1, z, cut_kerb)
    if blend:
        K.blend(P, m, y, width=blend)
    return m


def steps_down(P, x0, z0, direction, y, width, mat, max_steps=12):
    """A flight of stairs leaving the platform edge cell (x0, z0) towards ``direction`` until it meets the ground:
    the first step is one below the platform top ``y``. ``width`` cells across (centred)."""
    dx, dz = K.DIRS[direction]
    sx, sz = K.DIRS[K.CW[direction]]
    half = width // 2
    for i in range(1, max_steps + 1):
        x, z = x0 + dx * i, z0 + dz * i
        step_y = y - i + 1
        ground = max(P.top(x + sx * s, z + sz * s) for s in range(-half, half + 1))
        if ground >= step_y:
            # the ground has risen to this step: finish with a level tread
            break
        for s in range(-half, half + 1):
            px, pz = x + sx * s, z + sz * s
            P.fill(px, P.top(px, pz) - 1, pz, px, step_y - 1, pz, F(mat))
            P.set(px, step_y, pz, B.stairs(mat, K.OPP[direction]))
            P.fill(px, step_y + 1, pz, px, step_y + 3, pz, AIR)
        for s in (-half - 1, half + 1):
            px, pz = x + sx * s, z + sz * s
            if P.top(px, pz) < step_y:
                P.fill(px, P.top(px, pz) - 1, pz, px, step_y, pz, F(mat))


def keep_ring(P, x, z, y):
    """The two-block ring around the waystone stays walkable (checked)."""
    P.keep_clear.append((x - 2, z - 2, x + 2, z + 2, y + 1))


def pillar_ring(P, cx, cz, y, positions, fn):
    for (dx, dz) in positions:
        fn(cx + dx, y, cz + dz)


DIAG4 = [(-4, -4), (4, -4), (-4, 4), (4, 4)]
DIAG4_5 = [(-3, -4), (3, -4), (-3, 4), (3, 4), (-4, -3), (4, -3), (-4, 3), (4, 3)]


# ====================================================================================== the capital's forecourt
def capital(w):
    """Forecourt outside the south gate: plaza flagstones with a gilded inlay round the waystone, lamp posts,
    flower beds, benches, kingdom flags and the signpost where the south and lakeside roads part."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_capital", "転移碑 王都南門前広場 (capital gate forecourt)", 1, cx - 26, cz - 12, cx + 26, cz + 24,
              below=8, above=24, kind="waystone")
    y = P.top(cx, cz)
    # an apron of flagstones: semicircle-ended rectangle, the roads leave it at the south-west and south-east
    X, Z = K.grid(P)
    dx, dz = X - cx, Z - (cz + 2)
    plaza = (np.abs(dx) <= 15) & (dz >= -9) & (dz <= 10) & ((dx * dx) / 15.5 ** 2 + np.maximum(dz - 2, 0) ** 2 / 9.5 ** 2
                                                             <= 1.0)
    roads = probe.layout()["roads"]
    south = next(r for r in roads if r["id"] == "south")["points"]
    lake = next(r for r in roads if r["id"] == "lakeside")["points"]
    road = K.polyline_mask(P, south, 2.6) | K.polyline_mask(P, lake, 2.6)
    for (x, z) in K.cells(plaza, P):
        K.set_ground(P, x, z, y, clear=4)
        K.mixed(P, x, y, z, K.FLAG_MIX, seed=11)
    # road mouths: cobbles where the roads run out of the apron
    mouth = road & ~plaza & K.dilate(plaza, 7)
    for (x, z) in K.cells(mouth, P):
        K.mixed(P, x, P.top(x, z), z, K.COBBLE_MIX, seed=12)
    # kerb of smooth castle stone slabs round the apron (not across the road mouths, not on the gate side)
    edge = K.ring(plaza) & ~K.dilate(road, 1)
    for (x, z) in K.cells(edge, P):
        if z > cz - 9:
            P.set(x, y, z, F("smooth_castle_stone"))
    # the inlay: a gilded ring and a star of castle stone round the stone
    for (x, z) in K.cells(K.disc(P, cx, cz, 4.6), P):
        d = math.hypot(x - cx, z - cz)
        if 3.6 < d <= 4.6:
            P.set(x, y, z, F("gilded_trim") if (x + z) % 2 == 0 else F("smooth_castle_stone"))
        elif d <= 1.5:
            P.set(x, y, z, F("smooth_castle_stone"))
        elif abs(x - cx) == abs(z - cz) or x == cx or z == cz:
            P.set(x, y, z, F("castle_bricks"))
        else:
            P.set(x, y, z, F("plaza_flagstones"))
    place_waystone(P, "capital", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    # four double lamp posts on the inlay's diagonals
    for (ox, oz) in ((-7, -5), (7, -5), (-7, 7), (7, 7)):
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, F("polished_blackstone"))
        P.fill(x, y + 2, z, x, y + 4, z, B.wall("polished_blackstone"))
        P.set(x, y + 5, z, B.slab("polished_blackstone", "top"))
        P.set(x, y + 6, z, B.lantern())
        for d in ("east", "west"):
            ex, ez = K.DIRS[d]
            P.set(x + ex, y + 5, z + ez, B.slab("polished_blackstone", "top"))
            P.set(x + ex, y + 4, z + ez, B.lantern(hanging=True))
    # flower beds east and west of the stone, raised in castle stone
    for side in (-1, 1):
        x0, x1 = cx + side * 10, cx + side * 13
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(cz - 2, cz + 4):
                edge_c = x in (x0, x1) or z in (cz - 2, cz + 3)
                if edge_c:
                    P.set(x, y + 1, z, B.slab("smooth_castle_stone", "bottom"))
                else:
                    P.set(x, y, z, S("grass_block", snowy=False))
                    k = ("red_tulip", "white_tulip", "cornflower", "allium", "azure_bluet", "oxeye_daisy")
                    P.set(x, y + 1, z, B.S(k[int(hash2(x, z, 5) * len(k))]))
        # a clipped cypress at the bed's outer end
        tx = cx + side * 15
        for (bx, bz) in ((tx, cz - 1), (tx, cz + 2)):
            P.set(bx, y, bz, S("grass_block", snowy=False))
            P.fill(bx, y + 1, bz, bx, y + 3, bz, B.leaves("dark_oak"))
            P.set(bx, y + 4, bz, B.leaves("dark_oak"))
    # benches facing the stone between the beds and the lamps
    for side in (-1, 1):
        K.bench(P, cx + side * 6, y, cz + 10, "north", length=3, wood="dark_oak", axis_dir="east")
    # kingdom flags at the gate side, on stone bases
    for side in (-1, 1):
        x, z = cx + side * 12, cz - 7
        P.set(x, y + 1, z, F("castle_bricks"))
        P.set(x, y + 2, z, B.slab("smooth_castle_stone", "bottom"))
        K.flagpole(P, x, y + 2, z, 7, facing="east", design="kingdom", width=3.0, height=2.0, wood="dark_oak")
    # the signpost where the roads part (south of the court)
    sx, sz = cx + 3, cz + 15
    if P.top(sx, sz) != y:
        K.set_ground(P, sx, sz, y)
    P.set(sx, y + 1, sz, B.log("dark_oak"))
    P.set(sx, y + 2, sz, B.fence("dark_oak"))
    P.set(sx, y + 3, sz, B.fence("dark_oak"))
    P.set(sx, y + 4, sz, B.lantern())
    signpost_arm(P, sx - 1, y + 3, sz, "west", [K.field_name(1), K.field_level(1)])
    signpost_arm(P, sx + 1, y + 3, sz, "east", [K.field_name(9), K.field_level(9)])
    signpost_arm(P, sx, y + 2, sz - 1, "north",
                 ['{"translate":"map.eternalrealm.capital","bold":true}', '{"translate":"sign.eternalrealm.waystone"}'])
    P.probe(cx + 4, y, cz)
    P.probe(cx - 7, y + 1, cz - 5)
    P.view("shrine_capital", (cx + 30, y + 22, cz + 34), (cx, y + 2, cz), fov=52)
    return P


def signpost_arm(P, x, y, z, facing, lines, wood="dark_oak"):
    """A wall sign on the side of a post (the post is behind it, the text faces ``facing``)."""
    K.place_sign(P, x, y, z, lines, wood=wood, facing=facing, wall=True)


# ====================================================================================== field shrines
def meadow(w):
    """A stone circle on the meadow: rural paving, a ring of mossy standing stones, spruce lantern posts at the
    path from the road, flowers all round."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_meadow", "転移碑 はじまりの草原", 1, cx - 14, cz - 16, cx + 14, cz + 13, kind="waystone")
    y = P.top(cx, cz) + 1
    m = platform(P, cx, cz, 5, y, K.RURAL_MIX, "cobblestone", edge="mossy_cobblestone", shape="circle", seed=21)
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("polished_andesite") if (x + z) % 2 else F("stone_bricks"))
    P.set(cx, y, cz, F("chiseled_stone_bricks"))
    place_waystone(P, "meadow", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    # standing stones round the platform
    for k in range(7):
        a = 2 * math.pi * (k + 0.5) / 7 + 0.25
        if abs(math.cos(a + math.pi / 2)) < 0.25 and math.sin(a) < 0:
            continue                                     # leave the north (road side) open
        x, z = cx + round(7.5 * math.cos(a)), cz + round(7.5 * math.sin(a))
        K.standing_stone(P, x, P.top(x, z), z, 2 + int(hash2(x, z, 3) * 3), "mossy", seed=k)
    # gravel path to the road with lantern posts
    for z in range(cz - 15, cz - 5):
        for x in range(cx - 1, cx + 2):
            t = P.top(x, z)
            P.set(x, t, z, S("dirt_path") if (x + z) % 3 else F("gravel"))
    for side in (-1, 1):
        x, z = cx + side * 2, cz - 7
        K.lantern_post(P, x, P.top(x, z), z, height=3, wood="spruce")
    outer = K.disc(P, cx, cz, 12.5) & ~K.dilate(m, 1)
    outer &= ~K.rect(P, cx - 2, cz - 16, cx + 2, cz - 5)
    K.flowers(P, outer, density=0.28, seed=5)
    P.probe(cx + 3, y, cz + 1)
    return P


def moon_forest(w):
    """A moonlit shrine in the wood: mossy octagon, four pillars with soul lanterns, a stone torii at the north
    entrance, moss, ferns, mushrooms and azaleas."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_moon_forest", "転移碑 月影の森", 2, cx - 13, cz - 14, cx + 13, cz + 12, kind="waystone")
    y = int(np.median([P.top(cx + a, cz + b) for a in range(-5, 6) for b in range(-5, 6)])) + 1
    m = platform(P, cx, cz, 5, y, K.MOSS_MIX, "mossy_cobblestone", edge="mossy_stone_bricks", seed=31,
                 cut_kerb=B.slab("mossy_cobblestone", "bottom"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2, "square"), P):
        P.set(x, y, z, F("mossy_stone_bricks") if (x + z) % 2 else F("moss_block"))
    P.set(cx, y, cz, F("chiseled_stone_bricks"))
    place_waystone(P, "moon_forest", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    for (ox, oz) in DIAG4:
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, F("mossy_stone_bricks"))
        P.fill(x, y + 2, z, x, y + 3, z, B.wall("mossy_stone_bricks"))
        P.set(x, y + 4, z, B.lantern(soul=True))
        for (vx, vz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            if hash2(x + vx, z + vz, 41) < 0.4:
                P.set(x + vx, y + 2, z + vz, B.S("vine", **{f: True}))
    # torii-like stone gate on the north side
    gz = cz - 7
    for side in (-2, 2):
        x = cx + side
        t = P.top(x, gz)
        P.fill(x, t - 1, gz, x, y, gz, F("mossy_cobblestone"))
        P.fill(x, y + 1, gz, x, y + 4, gz, B.wall("mossy_cobblestone"))
    for x in range(cx - 3, cx + 4):
        P.set(x, y + 5, gz, F("mossy_stone_bricks") if abs(x - cx) < 3 else B.stairs("mossy_stone_bricks", "east" if x < cx
                                                                                       else "west", "top"))
    P.set(cx - 3, y + 5, gz, B.stairs("mossy_stone_bricks", "east", "bottom"))
    P.set(cx + 3, y + 5, gz, B.stairs("mossy_stone_bricks", "west", "bottom"))
    P.set(cx, y + 4, gz, B.lantern(hanging=True, soul=True))
    for z in range(gz - 6, gz + 1):
        for x in range(cx - 1, cx + 2):
            t = P.top(x, z)
            if z < gz - 1:
                P.set(x, t, z, F("mossy_cobblestone") if hash2(x, z, 9) < 0.5 else S("moss_block"))
    around = K.disc(P, cx, cz, 11) & ~K.dilate(m, 1) & ~K.rect(P, cx - 2, gz - 7, cx + 2, gz + 1)
    for (x, z) in K.cells(around, P):
        t = P.top(x, z)
        r = hash2(x, z, 77)
        if P.get_idx(x, t, z) == 0:
            P.set(x, t, z, S("moss_block") if r < 0.35 else S("grass_block", snowy=False))
        if r < 0.08:
            P.set(x, t + 1, z, S("red_mushroom" if hash2(z, x, 2) < 0.5 else "brown_mushroom"))
        elif r < 0.30:
            P.set(x, t + 1, z, S("fern"))
        elif r < 0.36:
            P.set(x, t + 1, z, S("moss_carpet"))
    for (ox, oz) in ((-8, 3), (8, -2), (6, 8), (-7, -6)):
        x, z = cx + ox, cz + oz
        K.bush(P, x, P.top(x, z), z, r=1.4, kind="flowering_azalea" if ox > 0 else "azalea", seed=ox)
    P.probe(cx + 4, y + 1, cz + 4)
    return P


def iron_mine(w):
    """The miners' shrine: dark stone platform under a timber canopy hung with lanterns, an ore cart and a
    pile of raw iron."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_iron_mine", "転移碑 鉄鉱の鉱山", 3, cx - 11, cz - 12, cx + 11, cz + 11, kind="waystone")
    y = P.top(cx, cz) + 1
    mix = [("polished_andesite", 30), ("cobbled_deepslate", 25), ("andesite", 15), ("polished_deepslate", 15),
           ("gravel", 5), ("cobblestone", 10)]
    m = platform(P, cx, cz, 5, y, mix, "cobbled_deepslate", edge="polished_blackstone_bricks", shape="square",
                 seed=41)
    for (x, z) in K.cells(K.disc(P, cx, cz, 1.6, "square"), P):
        P.set(x, y, z, F("polished_deepslate"))
    P.set(cx, y, cz, F("chiseled_deepslate"))
    place_waystone(P, "iron_mine", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    # timber canopy on four posts (open in the middle for the crystal)
    for (ox, oz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        x, z = cx + ox, cz + oz
        P.fill(x, y + 1, z, x, y + 6, z, B.log("spruce"))
    for z in (cz - 4, cz + 4):
        P.fill(cx - 5, y + 7, z, cx + 5, y + 7, z, B.log("spruce", "x"))
    for x in (cx - 4, cx + 4):
        P.fill(x, y + 7, cz - 3, x, y + 7, cz + 3, B.log("spruce", "z"))
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            if max(abs(x - cx), abs(z - cz)) >= 3:
                d = max(abs(x - cx), abs(z - cz))
                P.set(x, y + 8 + (5 - d) // 2, z, B.slab("spruce", "bottom") if d % 2 else B.slab("dark_oak", "bottom"))
    for (ox, oz) in ((-4, 0), (4, 0), (0, -4), (0, 4)):
        P.set(cx + ox, y + 6, cz + oz, B.chain())
        P.set(cx + ox, y + 5, cz + oz, B.lantern(hanging=True))
    # ore cart, raw iron, anvil, barrels
    K.cart(P, cx + 7, P.top(cx + 7, cz - 2), cz - 2, "south", wood="spruce", load=F("raw_iron_block"))
    for (ox, oz, st) in ((-7, 2, F("raw_iron_block")), (-7, 3, F("iron_block")), (-6, 3, F("raw_iron_block")),
                         (-7, 4, B.barrel("up")), (-6, 2, S("anvil", facing="east"))):
        x, z = cx + ox, cz + oz
        P.set(x, P.top(x, z) + 1, z, st)
    P.set(cx - 7, P.top(cx - 7, cz + 3) + 2, cz + 3, F("raw_iron_block"))
    for side in (-1, 1):
        x, z = cx + side * 2, cz - 8
        K.lantern_post(P, x, P.top(x, z), z, height=3, wood="spruce")
    P.probe(cx - 4, y + 1, cz - 4)
    return P


def sacred_falls(w):
    """White stone shrine on the river bank where the falls road comes over the bridge: calcite and quartz,
    a cherry tree, water basins, sea lanterns, and a balcony on piles over the river."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_sacred_falls", "転移碑 聖なる滝", 4, cx - 14, cz - 13, cx + 14, cz + 14, kind="waystone")
    y = 66
    mix = [("calcite", 30), ("smooth_quartz", 20), ("polished_diorite", 25), ("quartz_bricks", 15),
           ("smooth_stone", 10)]
    m = K.disc(P, cx, cz, 6, "octagon")
    for (x, z) in K.cells(m, P):
        t = P.top(x, z)
        if P.wet(x, z):
            P.fill(x, y - 1, z, x, y - 1, z, F("quartz_bricks"))
            P.fill(x, y, z, x, y, z, F("smooth_quartz"))
        else:
            if t < y:
                P.fill(x, t - 1, z, x, y - 1, z, F("polished_diorite"))
            else:
                P.fill(x, y + 1, z, x, t + 3, z, AIR)
            K.mixed(P, x, y, z, mix, 51)
    # piles under the part over the water
    for (x, z) in K.cells(m, P):
        if P.wet(x, z) and (x + z) % 3 == 0:
            P.fill(x, P.top(x, z), z, x, y - 2, z, B.S("quartz_pillar", axis="y"))
    for (x, z) in K.cells(K.ring(m), P):
        P.set(x, y, z, F("quartz_bricks"))
        if P.wet(x, z) or P.top(x, z) < y - 1:
            P.set(x, y + 1, z, B.slab("smooth_quartz", "bottom") if (x + z) % 2 else B.wall("diorite"))
    K.blend(P, m, y, width=5, skip=None)
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("smooth_quartz") if (x + z) % 2 == 0 else F("calcite"))
    P.set(cx, y, cz, F("chiseled_quartz_block"))
    place_waystone(P, "sacred_falls", cx, y, cz, "east")
    keep_ring(P, cx, cz, y)
    # four white lanterns on calcite plinths
    for (ox, oz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        x, z = cx + ox, cz + oz
        K.stone_lantern(P, x, y, z, stone="diorite", cap=B.slab("smooth_quartz", "bottom"))
    # water basins with lily pads west of the stone, a cherry tree beside them
    for (bx, bz) in ((cx - 4, cz - 1), (cx - 4, cz + 1)):
        P.set(bx, y, bz, S("water", level=0))
    P.set(cx - 4, y, cz, F("sea_lantern"))
    tx, tz = cx - 9, cz - 6
    K.tree(P, tx, P.top(tx, tz), tz, kind="cherry", height=5, crown=3.2, seed=4)
    K.tree(P, cx - 8, P.top(cx - 8, cz + 8), cz + 8, kind="cherry", height=4, crown=2.8, seed=9)
    for (x, z) in K.cells(K.disc(P, cx - 9, cz, 6) & ~K.dilate(m, 1), P):
        if not P.wet(x, z) and hash2(x, z, 3) < 0.3:
            P.set(x, P.top(x, z) + 1, z, S("pink_petals", facing="north", flower_amount=1 + int(hash2(z, x, 4) * 4)))
    P.probe(cx - 4, y, cz)
    return P


def ancient_ruins(w):
    """A ruined sanctuary on the hilltop road: worn sandstone paving, a ring of broken columns, one fallen,
    braziers and dry grass."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_ancient_ruins", "転移碑 古代遺跡", 5, cx - 13, cz - 13, cx + 13, cz + 13, kind="waystone")
    y = P.top(cx, cz) + 1
    mix = [("smooth_sandstone", 30), ("cut_sandstone", 25), ("sandstone", 20), ("chiseled_sandstone", 3),
           ("coarse_dirt", 7), ("gravel", 5), ("cracked_stone_bricks", 10)]
    m = platform(P, cx, cz, 6, y, mix, "sandstone", edge="cut_sandstone", shape="octagon", seed=61)
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("smooth_sandstone") if (x + z) % 2 else F("cut_red_sandstone"))
    P.set(cx, y, cz, F("chiseled_sandstone"))
    place_waystone(P, "ancient_ruins", cx, y, cz, "west")
    keep_ring(P, cx, cz, y)
    for k in range(8):
        a = 2 * math.pi * k / 8 + math.pi / 8
        x, z = cx + round(5 * math.cos(a)), cz + round(5 * math.sin(a))
        h = (5, 2, 4, 1, 5, 3, 0, 4)[k]
        P.set(x, y + 1, z, F("chiseled_sandstone"))
        for i in range(2, h + 1):
            P.set(x, y + i, z, B.S("quartz_pillar", axis="y") if i % 2 else F("cut_sandstone"))
        if h >= 4:
            P.set(x, y + h + 1, z, B.slab("smooth_sandstone", "bottom"))
            P.set(x, y + h + 2, z, S("campfire", facing="north", lit=True, signal_fire=False))
        elif h >= 1:
            P.set(x, y + h + 1, z, B.stairs("sandstone", ("north", "east", "south", "west")[k % 4], "bottom"))
    # a toppled column lying across the edge
    for i in range(5):
        x, z = cx + 8 + i % 2, cz + 4 + i
        if P.top(x, z) <= y + 1:
            P.set(x, P.top(x, z) + 1, z, B.S("quartz_pillar", axis="z") if i % 2 else F("cut_sandstone"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 12) & ~K.dilate(m, 1), P):
        r = hash2(x, z, 6)
        t = P.top(x, z)
        if r < 0.12:
            P.set(x, t + 1, z, S("dead_bush"))
        elif r < 0.30:
            P.set(x, t + 1, z, S("grass"))
    P.probe(cx + 3, y, cz - 1)
    return P


def soaring_peaks(w):
    """A terrace cut into the mountainside: snowy stone bricks, a retaining wall on the valley side with steps
    down to the path, spruce lantern posts, cairns and snow."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_soaring_peaks", "転移碑 天翔の峰", 6, cx - 15, cz - 15, cx + 15, cz + 15, below=16, above=30,
              kind="waystone")
    y = int(np.percentile([P.top(cx + a, cz + b) for a in range(-5, 6) for b in range(-5, 6)], 45))
    mix = [("stone_bricks", 35), ("polished_andesite", 15), ("cracked_stone_bricks", 12), ("cobblestone", 15),
           ("andesite", 13), ("packed_ice", 4), ("snow_block", 6)]
    m = platform(P, cx, cz, 6, y, mix, "stone_bricks", edge="polished_andesite", shape="octagon", seed=71,
                 cut_kerb=F("stone_bricks"), blend=6)
    # retaining wall faces on the low side: crenellated parapet where the drop is high
    for (x, z) in K.cells(K.ring(m), P):
        if P.top(x, z) < y - 2:
            P.set(x, y + 1, z, B.wall("stone_brick"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("polished_andesite") if (x + z) % 2 else F("stone_bricks"))
    P.set(cx, y, cz, F("chiseled_stone_bricks"))
    place_waystone(P, "soaring_peaks", cx, y, cz, "south")
    keep_ring(P, cx, cz, y)
    # steps down/up towards the road on the south side
    steps_down(P, cx, cz + 6, "south", y, 3, "stone_brick")
    steps_down(P, cx, cz - 6, "north", y, 3, "stone_brick")
    for (ox, oz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        x, z = cx + ox, cz + oz
        K.lantern_post(P, x, y, z, height=3, wood="spruce")
    for (ox, oz) in ((-9, 2), (8, -6), (9, 6)):
        x, z = cx + ox, cz + oz
        t = P.top(x, z)
        for i, st in enumerate((F("cobblestone"), F("stone"), F("andesite"), B.slab("cobblestone", "bottom"))):
            P.set(x, t + 1 + i, z, st)
    for (x, z) in K.cells(m & ~K.disc(P, cx, cz, 2.5), P):
        if hash2(x, z, 8) < 0.35 and P.get_idx(x, y + 1, z) <= 1:
            P.set(x, y + 1, z, S("snow", layers=1))
    K.spruce(P, cx - 10, P.top(cx - 10, cz - 8), cz - 8, height=8, snowy=True)
    K.spruce(P, cx + 11, P.top(cx + 11, cz + 1), cz + 1, height=7, snowy=True)
    P.probe(cx + 3, y, cz - 1)
    return P


def end_forest(w):
    """A corrupted shrine: obsidian and blackstone with a purpur inlay, crying obsidian, amethyst growing
    through the cracks, end rods and soul lanterns on blackstone posts."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_end_forest", "転移碑 終焉の森", 7, cx - 13, cz - 13, cx + 13, cz + 13, kind="waystone")
    y = P.top(cx, cz) + 1
    mix = [("polished_blackstone_bricks", 35), ("blackstone", 15), ("cracked_polished_blackstone_bricks", 15),
           ("obsidian", 15), ("cobbled_deepslate", 12), ("crying_obsidian", 3), ("gilded_blackstone", 5)]
    m = platform(P, cx, cz, 6, y, mix, "blackstone", edge="obsidian", shape="octagon", seed=81)
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.6), P):
        d = math.hypot(x - cx, z - cz)
        P.set(x, y, z, F("purpur_block") if 1.6 < d <= 2.6 else F("obsidian"))
    P.set(cx, y, cz, F("crying_obsidian"))
    place_waystone(P, "end_forest", cx, y, cz, "east")
    keep_ring(P, cx, cz, y)
    for (ox, oz) in DIAG4:
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, F("polished_blackstone"))
        P.fill(x, y + 2, z, x, y + 3, z, B.wall("polished_blackstone"))
        P.set(x, y + 4, z, B.lantern(soul=True))
    for (ox, oz) in ((0, -5), (0, 5), (-5, 0)):
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, S("end_rod", facing="up"))
    # amethyst and crying obsidian breaking through around the platform
    for (x, z) in K.cells(K.disc(P, cx, cz, 12) & ~K.dilate(m, 1), P):
        r = hash2(x, z, 12)
        t = P.top(x, z)
        if r < 0.05:
            P.set(x, t, z, F("amethyst_block"))
            P.set(x, t + 1, z, S("amethyst_cluster", facing="up"))
        elif r < 0.09:
            P.set(x, t, z, F("crying_obsidian"))
        elif r < 0.16:
            P.set(x, t, z, F("obsidian") if hash2(z, x, 1) < 0.5 else F("blackstone"))
    for (ox, oz) in ((-8, -6), (7, 8), (-7, 7)):
        crystal_spike(P, cx + ox, cz + oz, 3 + int(hash2(ox, oz, 1) * 3))
    P.probe(cx - 4, y + 1, cz - 4)
    return P


def crystal_spike(P, x, z, h):
    t = P.top(x, z)
    P.fill(x, t - 1, z, x, t + h, z, F("amethyst_block"))
    P.set(x, t + h + 1, z, S("amethyst_cluster", facing="up"))
    for (dx, dz, f) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        if hash2(x + dx, z + dz, h) < 0.6:
            yy = t + 1 + int(hash2(z + dz, x + dx, 2) * max(1, h - 1))
            P.set(x + dx, yy, z + dz, S("amethyst_cluster", facing=f))


def farm(w):
    """The harvest shrine at the farm crossroads: cobbled square on the road, a gabled timber lych roof on
    log posts over the north entrance, hay bales, pumpkins, a scarecrow and flower boxes."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_farm", "転移碑 豊穣の農場", 8, cx - 13, cz - 14, cx + 13, cz + 13, kind="waystone")
    y = P.top(cx, cz)
    mix = [("cobblestone", 25), ("cobbled_paving", 35), ("mossy_cobblestone", 10), ("andesite", 10),
           ("dirt_path", 20)]
    m = platform(P, cx, cz, 6, y, mix, "cobblestone", edge="stripped_spruce_log", shape="square", seed=91)
    for (x, z) in K.cells(K.ring(m), P):
        P.set(x, y, z, B.log("spruce", "x" if abs(z - cz) == 6 else "z"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("polished_andesite") if (x + z) % 2 else F("cobbled_paving"))
    place_waystone(P, "farm", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    # fence round the square with four openings (where the roads come in, and north/south)
    for (x, z) in K.cells(K.ring(m), P):
        ax, az = abs(x - cx), abs(z - cz)
        opening = (ax <= 1) or (az <= 1)
        if not opening:
            P.set(x, y + 1, z, B.fence("spruce"))
    for (ox, oz) in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, B.log("spruce"))
        P.set(x, y + 2, z, B.lantern())
    # hay, pumpkins, flowers in boxes along the inside of the fence
    for (ox, oz, st) in ((-5, -4, B.S("hay_block", axis="x")), (-5, -3, B.S("hay_block", axis="x")),
                         (-4, -4, B.S("hay_block", axis="z")), (5, 4, S("pumpkin")), (4, 5, S("pumpkin")),
                         (5, 5, S("carved_pumpkin", facing="west")), (5, -4, B.barrel("up")),
                         (-5, 4, B.S("composter", level=6))):
        P.set(cx + ox, y + 1, cz + oz, st)
    P.set(cx - 5, y + 2, cz - 4, B.S("hay_block", axis="x"))
    for (ox, oz) in ((-4, 5), (-3, 5), (3, -5), (4, -5)):
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, F("rooted_dirt"))
        P.set(x, y + 2, z, B.S(("sunflower", "rose_bush")[ox % 2], half="lower"))
        P.set(x, y + 3, z, B.S(("sunflower", "rose_bush")[ox % 2], half="upper"))
    K.scarecrow(P, cx + 9, P.top(cx + 9, cz - 8), cz - 8, facing="west")
    K.haystack(P, cx - 10, P.top(cx - 10, cz + 8), cz + 8, r=1.8, seed=3)
    P.probe(cx - 6, y + 1, cz - 6)
    return P


def lakeside(w):
    """The lake shrine: pale stone and birch, a moat ring of water with lily pads round the dais, reeds,
    birch lantern posts and a little stone bridge over the moat on the north side."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_lakeside", "転移碑 湖畔のほとり", 9, cx - 13, cz - 14, cx + 13, cz + 13, kind="waystone")
    y = P.top(cx, cz) + 1
    mix = [("smooth_stone", 25), ("polished_diorite", 20), ("stone_bricks", 25), ("diorite", 10),
           ("mossy_stone_bricks", 10), ("birch_planks", 10)]
    m = platform(P, cx, cz, 6, y, mix, "stone_bricks", edge="polished_andesite", shape="circle", seed=101)
    moat = K.disc(P, cx, cz, 4.5) & ~K.disc(P, cx, cz, 3.4)
    for (x, z) in K.cells(moat, P):
        if abs(x - cx) <= 1 and z < cz:
            continue                                                      # the little bridge
        P.set(x, y - 1, z, F("stone_bricks"))
        P.set(x, y, z, S("water", level=0))
        if hash2(x, z, 4) < 0.35:
            P.set(x, y + 1, z, S("lily_pad"))
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 5, cz - 3):
            P.set(x, y, z, F("birch_planks"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 3.0), P):
        P.set(x, y, z, F("polished_diorite") if (x + z) % 2 else F("smooth_stone"))
    P.set(cx, y, cz, F("chiseled_stone_bricks"))
    place_waystone(P, "lakeside", cx, y, cz, "north")
    P.keep_clear.append((cx - 2, cz - 2, cx + 2, cz + 2, y + 1))
    for (ox, oz) in ((-5, -3), (5, -3), (-5, 3), (5, 3)):
        x, z = cx + ox, cz + oz
        P.set(x, y + 1, z, B.log("birch"))
        P.fill(x, y + 2, z, x, y + 3, z, B.fence("birch"))
        P.set(x, y + 4, z, B.lantern())
    # reeds and grass round the edge, a birch tree
    for (x, z) in K.cells(K.disc(P, cx, cz, 11) & ~K.dilate(m, 1), P):
        r = hash2(x, z, 14)
        t = P.top(x, z)
        if r < 0.06:
            P.set(x, t + 1, z, S("sugar_cane", age=0))
            P.set(x, t + 2, z, S("sugar_cane", age=0))
        elif r < 0.25:
            P.set(x, t + 1, z, S("grass"))
        elif r < 0.32:
            P.set(x, t + 1, z, B.S(("blue_orchid", "azure_bluet", "oxeye_daisy")[int(r * 100) % 3]))
    K.tree(P, cx + 9, P.top(cx + 9, cz - 7), cz - 7, kind="birch", height=6, crown=2.6, seed=2)
    K.tree(P, cx - 9, P.top(cx - 9, cz + 6), cz + 6, kind="birch", height=5, crown=2.4, seed=7)
    P.probe(cx + 5, y + 1, cz + 3)
    return P


def harbour(w):
    """The harbour shrine: a timber deck with rope railings, barrels and crates, an anchor, a ship's bell
    and the harbour flag."""
    cx, cz = w["x"], w["z"]
    P = Piece("shrine_harbour", "転移碑 風の港", 10, cx - 12, cz - 14, cx + 12, cz + 12, kind="waystone")
    y = P.top(cx, cz) + 1
    mix = [("spruce_planks", 60), ("oak_planks", 25), ("dark_oak_planks", 15)]
    m = platform(P, cx, cz, 6, y, mix, "cobblestone", edge="dark_oak_planks", shape="square", seed=111)
    for (x, z) in K.cells(K.ring(m), P):
        P.set(x, y, z, B.log("dark_oak", "x" if abs(z - cz) == 6 else "z"))
    for (x, z) in K.cells(K.disc(P, cx, cz, 2.2), P):
        P.set(x, y, z, F("smooth_stone") if (x + z) % 2 else F("polished_andesite"))
    place_waystone(P, "harbour", cx, y, cz, "north")
    keep_ring(P, cx, cz, y)
    # rope railing: posts with chains between (chains along the edge)
    for (x, z) in K.cells(K.ring(m), P):
        ax, az = abs(x - cx), abs(z - cz)
        if ax <= 1 or az <= 1:
            continue
        if (ax == 6 and az % 2 == 0) or (az == 6 and ax % 2 == 0):
            P.set(x, y + 1, z, B.fence("dark_oak"))
        else:
            P.set(x, y + 1, z, B.chain("x" if az == 6 else "z"))
    # anchor (iron), bell, barrels and crates
    ax_, az_ = cx - 4, cz + 4
    P.set(ax_, y + 1, az_, B.S("iron_bars"))
    P.set(ax_, y + 2, az_, B.S("iron_bars"))
    P.set(ax_, y + 3, az_, B.S("chain", axis="y"))
    P.set(ax_ - 1, y + 1, az_, B.S("iron_trapdoor", facing="west", half="bottom", open=True))
    P.set(ax_ + 1, y + 1, az_, B.S("iron_trapdoor", facing="east", half="bottom", open=True))
    P.set(cx + 4, y + 1, cz - 4, B.log("dark_oak"))
    P.set(cx + 4, y + 2, cz - 4, B.log("dark_oak"))
    P.set(cx + 4, y + 3, cz - 4, B.log("dark_oak", "x"))
    P.set(cx + 3, y + 3, cz - 4, B.fence("dark_oak"))
    P.set(cx + 3, y + 2, cz - 4, B.S("bell", attachment="ceiling", facing="north"))
    for (ox, oz, st) in ((4, 4, B.barrel("up")), (4, 3, B.barrel("up")), (3, 4, B.barrel("east")),
                         (-4, -4, F("spruce_planks")), (-4, -3, B.barrel("up")), (-3, -4, B.barrel("north"))):
        P.set(cx + ox, y + 1, cz + oz, st)
    P.set(cx + 4, y + 2, cz + 4, B.barrel("up"))
    P.set(cx - 4, y + 2, cz - 4, B.lantern())
    # the harbour flag
    K.flagpole(P, cx - 6, y, cz - 6, 8, facing="east", design="harbour", width=3.5, height=2.2, wood="dark_oak")
    for side in (-1, 1):
        x, z = cx + side * 2, cz - 8
        K.lantern_post(P, x, P.top(x, z), z, height=3, wood="dark_oak")
    P.probe(cx + 4, y + 1, cz - 4)
    return P


BUILDERS = {"capital": capital, "meadow": meadow, "moon_forest": moon_forest, "iron_mine": iron_mine,
            "sacred_falls": sacred_falls, "ancient_ruins": ancient_ruins, "soaring_peaks": soaring_peaks,
            "end_forest": end_forest, "farm": farm, "lakeside": lakeside, "harbour": harbour}


def build(only=None):
    ws = waystones()
    out = []
    for wid, fn in BUILDERS.items():
        pid = "shrine_" + wid
        if only and pid not in only:
            continue
        out.append(fn(ws[wid]))
    return out
