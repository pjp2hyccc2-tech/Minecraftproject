"""城壁 City walls: curtain walls round the town (west x=-104, east x=104, south z=79; 5 thick, walk at y=10),
round towers with blue cones, the main gate in the south (twin round towers with blue cone roofs, crest
banners, raised portcullis, guard niches) opening onto the lake bridge, west and east gates with square towers,
a postern down to the harbour, and the dungeon warden Gant's office inside the east gate.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import layout as L
from capital import shapes as S
from capital.noise import hash2, hash3

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "walls"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF = K.CB, K.SM, K.GOLD, K.ROOF

WX_W, WX_E, WZ_S, WZ_N = L.WALLS["west"], L.WALLS["east"], L.WALLS["south"], L.WALLS["north"]
WALK = 10                     # walkway top block

VIEWS = {
    "gate_close": dict(pos=(10, 5, 104), look=(0, 14, 80), fov=70, sun=(40, -150), fog=2600,
                       label="The south gate: twin towers, crest banners, raised portcullis"),
    "wall_walk": dict(pos=(-98, 12.6, 60), look=(-104, 12, 20), fov=74, sun=(40, -150), fog=2600,
                      label="On the west wall walk"),
    "east_gate": dict(pos=(86, 4, -3), look=(110, 10, 4), fov=72, sun=(40, -150), fog=2600,
                      label="The east gate and Gant's office"),
    "gant_office": dict(pos=(91.5, 3.6, 17.5), look=(99, 2, 10), fov=80, sun=(40, -150), fog=900,
                        label="Gant's office (dungeon warden)"),
}

WALL_MIX = [("stone_bricks", 56), ("cracked_stone_bricks", 9), ("mossy_stone_bricks", 9), ("andesite", 8),
            ("polished_andesite", 10), ("tuff", 4), ("cobblestone", 4)]


def _mix_state(x, y, z, seed=7):
    wts = np.array([m[1] for m in WALL_MIX], float)
    c = np.cumsum(wts / wts.sum())
    u = hash3(x, y, z, seed)
    k = int(np.searchsorted(c, u, side="right"))
    return B.S(WALL_MIX[min(k, len(WALL_MIX) - 1)][0])


def _stone(w, x, y, z):
    w.set(x, y, z, _mix_state(x, y, z))


# ============================================================================== curtain walls
def curtain(w, axis, c, a0, a1, out, gaps=()):
    """Wall along ``axis`` ('x': runs along x at z = c; 'z': runs along z at x = c) from a0 to a1, outside
    towards ``out`` (+1/-1). ``gaps``: [(g0, g1)] along-ranges left open (gates)."""
    for a in range(a0, a1 + 1):
        if any(g0 <= a <= g1 for g0, g1 in gaps):
            continue
        for d in range(-2, 3):
            x, z = (a, c + d * out) if axis == "x" else (c + d * out, a)
            w.fill(x, 0, z, x, WALK - 1, z, F("stone"))
            if d in (-2, 2):
                for y in range(1, WALK):
                    _stone(w, x, y, z)
            w.set(x, WALK, z, B.S("cobbled_paving") if abs(d) < 2 else F(SM))
            w.fill(x, WALK + 1, z, x, WALK + 4, z, AIR)
        # outer parapet with merlons, corbel course, batter at the foot, inner low wall
        def P(d):
            return (a, c + d * out) if axis == "x" else (c + d * out, a)
        ox, oz = P(2)
        w.set(ox, WALK + 1, oz, F(SM) if False else _mix_state(ox, WALK + 1, oz))
        if a % 2 == 0:
            w.set(ox, WALK + 2, oz, _mix_state(ox, WALK + 2, oz))
            w.set(ox, WALK + 3, oz, B.slab("stone_bricks", "bottom"))
        else:
            w.set(ox, WALK + 2, oz, B.slab("smooth_castle_stone", "bottom"))
        fx, fz = P(3)
        face_out = ("south" if out > 0 else "north") if axis == "x" else ("east" if out > 0 else "west")
        face_in = K.OPP[face_out]
        w.set(fx, WALK, fz, B.stairs("stone_bricks", face_in, "top"))
        w.set(fx, WALK - 1, fz, B.stairs("stone_bricks", face_in, "top")) if a % 4 == 0 else None
        w.set(fx, 1, fz, B.stairs("stone_bricks", face_in, "bottom"))
        w.set(fx, 2, fz, B.stairs("stone_bricks", face_in, "bottom")) if False else None
        w.set(fx, 0, fz, F("stone_bricks"))
        ix, iz = P(-2)
        w.set(ix, WALK + 1, iz, B.wall("stone_bricks"))
        if a % 8 == 4:
            w.set(ix, WALK + 2, iz, B.lantern())
        # string course on the outside at mid-height
        w.set(ox, 6, oz, F(SM)) if a % 1 == 0 else None
        if a % 6 == 0:                                          # arrow slit
            w.set(ox, 8, oz, B.S("iron_bars")) if False else None


def tower(w, cx, cz, r=4.5, top=16, cone_h=13, roof=ROOF, flag=None, door_dir=None, seed=0):
    """Round wall tower with a crenellated top and a cone roof; interior floors at the walk level."""
    S.cylinder(w, cx, cz, r, 0, top - 1, F("stone_bricks"))
    xs, zs = S.ring_positions(cx, cz, r)
    for (x, z) in zip(xs, zs):
        for y in range(1, top):
            _stone(w, int(x), y, int(z))
    S.cylinder(w, cx, cz, r - 1.0, 1, top - 1, AIR)
    S.cylinder(w, cx, cz, r - 1.0, WALK, WALK, F("spruce_planks"))
    S.cylinder(w, cx, cz, r - 1.0, 0, 0, F("cobbled_paving"))
    S.corbel_ring(w, cx, cz, r + 0.6, top - 1, "stone_bricks")
    S.cylinder(w, cx, cz, r + 0.8, top, top, F(SM))
    S.crenellate_ring(w, cx, cz, r + 0.8, top + 1, B.S("stone_bricks"), cap=B.slab("smooth_castle_stone"))
    tip = S.cone(w, cx, cz, r - 0.2, top + 1, cone_h, roof, finial=False)
    S.cylinder(w, cx, cz, r - 0.2, top + 1, top + 1, B.slab(GOLD, "top"), hollow=True) if False else None
    w.set(cx, tip, cz, F(GOLD))
    if flag:
        design, width = flag
        TK.flagpole(w, cx, tip, cz, 3, "east", design, width, 2.0 if design != "pennant" else 1.0, pole="fence")
    else:
        w.set(cx, tip + 1, cz, B.S("gilded_finial"))
    # arrow slits
    for k in range(6):
        a = math.radians(k * 60 + 30)
        x, z = cx + int(round(r * math.cos(a))), cz + int(round(r * math.sin(a)))
        for y in (5, 12):
            if y < top:
                w.set(x, y, z, B.S("iron_bars"))
                w.set(x, y + 1, z, B.S("iron_bars"))
    w.set(cx, WALK - 1, cz, B.lantern(hanging=True))
    return tip


def walk_openings(w, cx, cz, r, dirs):
    """Doors from the wall walk into a tower (dirs = directions of the wall arms)."""
    for d in dirs:
        vx, vz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[d]
        for k in range(int(r) - 1, int(r) + 2):
            x, z = cx + vx * k, cz + vz * k
            w.fill(x, WALK + 1, z, x, WALK + 3, z, AIR)
            if vx:
                w.fill(x, WALK + 1, z - 1, x, WALK + 3, z + 1, AIR)
            else:
                w.fill(x - 1, WALK + 1, z, x + 1, WALK + 3, z, AIR)
            w.set(x, WALK, z, B.S("cobbled_paving"))


def wall_stairs(w, x, z, direction, n=10, width=2):
    """Stone stair against the inner face climbing towards ``direction`` up to the walk."""
    vx, vz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[direction]
    for i in range(n):
        for k in range(width):
            px = x + vx * i + (k if vz else 0)
            pz = z + vz * i + (k if vx else 0)
            w.fill(px, 1, pz, px, i, pz, F("stone_bricks")) if i > 0 else None
            w.set(px, i + 1, pz, B.stairs("stone_bricks", direction))
            w.fill(px, i + 2, pz, px, i + 4, pz, AIR)


# ============================================================================== main gate (south)
def main_gate(w):
    zc = WZ_S
    gx = 12
    top = 22
    # gate block between the towers
    x0, x1, z0, z1 = -7, 7, zc - 4, zc + 5
    w.fill(x0, 0, z0, x1, 0, z1, F("stone_bricks"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(1, top - 3):
                _stone(w, x, y, z)
    # passage (9 wide, round head) right through
    m = S.round_arch_mask(9, 9)
    for z in range(z0, z1 + 1):
        S.carve(w, "x", -4, 1, z, m, AIR)
    w.fill(-4, 0, z0, 4, 0, z1, B.S("cobbled_paving"))
    # vault ribs and lanterns in the passage
    for z in (z0 + 2, z1 - 2):
        for x in range(-4, 5):
            for y in range(1, 10):
                if m[x + 4, y - 1] and not (m[x + 4, min(y, 8)] if y < 9 else False):
                    pass
        w.set(0, 8, z, B.lantern(hanging=True)) if False else None
    w.set(0, 9, (z0 + z1) // 2, B.chain("y"))
    w.set(0, 8, (z0 + z1) // 2, B.lantern(hanging=True))
    # raised portcullis in the outer arch (iron bars across the head + its chains)
    for x in range(-4, 5):
        for y in range(7, 10):
            if m[x + 4, y - 1]:
                w.set(x, y, z1 - 1, B.S("iron_bars"))
    w.set(-4, 6, z1 - 1, B.S("iron_bars"))
    w.set(4, 6, z1 - 1, B.S("iron_bars"))
    # gilded voussoirs and keystone, crest panel, machicolations, crenellations
    ring = K.ring_of(m)
    S.carve(w, "x", -5, 0, z1, ring, F(SM))
    w.set(0, 10, z1, F(GOLD))
    for z in (z1,):
        for x in range(-2, 3):
            w.set(x, 13, z, F(SM))
            w.set(x, 15, z, F(SM))
        w.set(-1, 14, z, F(GOLD))
        w.set(1, 14, z, F(GOLD))
        w.set(0, 14, z, B.S("castle_crest_stone"))
    S.corbel_rect(w, x0, z0, x1, z1, top - 3, "stone_bricks")
    w.fill(x0 - 1, top - 2, z0 - 1, x1 + 1, top - 2, z1 + 1, F(SM))
    w.fill(x0, top - 2, z0, x1, top - 2, z1, B.S("cobbled_paving"))
    S.crenellate(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top - 1, B.S("stone_bricks"), cap=B.slab("smooth_castle_stone"))
    # inner side: arch moulding and the city arms
    S.carve(w, "x", -5, 0, z0, ring, F(SM))
    deco.kingdom_wall_banner(w, 0, 13, z0 - 1, facing="north")
    # twin round towers with blue cones and kingdom flags
    for s in (-1, 1):
        cx = s * gx
        tip = tower(w, cx, zc + 1, r=5.5, top=top, cone_h=17, flag=("kingdom", 4.0), seed=s)
        # big crest banners on the towers' outer faces (two tiers)
        for y in (17, 12):
            deco.kingdom_wall_banner(w, cx, y, zc + 1 + 6, facing="south")
        # guard room doors into the passage, guard niches with armour stands
        w.fill(s * 5, 1, zc, s * 6, 3, zc + 2, AIR)
        lo, hi = B.door_states("spruce", "east" if s < 0 else "west", "left")
        w.set(s * 6, 1, zc + 1, lo)
        w.set(s * 6, 2, zc + 1, hi)
        w.set(s * 5, 1, zc, AIR)
        deco.armor_stand(w, s * 5, 0, z1 - 2, yaw=90.0 * s if False else (90.0 if s > 0 else -90.0), armor="iron",
                         weapon="minecraft:iron_sword", offhand=deco.blue_shield()) if False else None
        # tower stair: ladder up to the walk floor inside
        for y in range(1, WALK + 1):                       # against the tower wall on its north side
            w.set(cx, y, zc + 1 - 4, B.S("ladder", facing="south"))
        w.set(cx, 1, zc + 1, B.S("barrel", facing="up"))
        w.set(cx + s, 1, zc + 3, B.S("chest", facing="north"))
        w.set(cx + s, 1, zc - 1, B.S("grindstone", face="floor", facing="north"))
        deco.armor_stand(w, cx, 0, zc + 3, yaw=0.0, armor="iron", weapon="minecraft:iron_sword",
                         offhand=deco.blue_shield())
    # pennants on the gate block corners
    TK.flagpole(w, 0, top - 1, z1 + 1, 3, "east", "pennant", 4.5, 1.0, pole="fence")
    # forecourt outside the gate towards the bridge: paving, lamps, a horse trough
    TK.pave(w, -24, zc + 6, 24, 92, TK.SQUARE_MIX, seed=401)
    for s in (-1, 1):
        K.lamp(w, s * 9, 0, 88, height=4)
        w.fill(s * 16, 1, 89, s * 19, 1, 89, B.S("water_cauldron", level=3)) if False else None
    for x in (-5, 5):                                        # the terrain's kerb lamp collides with the parapet
        w.fill(x, 1, 86, x, 7, 86, AIR, replace=[B.S("lantern"), B.wall("polished_blackstone"),
                                                 B.full("polished_blackstone")])
    # inside: gate square
    TK.pave(w, -24, 66, 24, zc - 5, TK.SQUARE_MIX, seed=402)
    for s in (-1, 1):
        K.mast(w, s * 9, 0, 70, height=9, sides=("north", "south"))
        TK.flag(w, s * 9, 10, 70, "east", "kingdom", 3.0, 2.0)
        deco.bench(w, s * 15, 0, 68, facing="south", length=3)
    # guards: both sides outside and inside, on the gate top
    TK.guard(w, "south_gate", 1, -6, 1, zc + 7, 0.0)
    TK.guard(w, "south_gate", 2, 6, 1, zc + 7, 0.0)
    TK.guard(w, "south_gate", 3, -6, 1, zc - 6, 180.0)
    TK.guard(w, "south_gate", 4, 6, 1, zc - 6, 180.0)
    TK.guard(w, "south_gate", 5, 0, top - 1, z1, 0.0)
    TK.crowd(w, "street", [(-14, 1, 72), (14, 1, 88)], start=1)


# ============================================================================== side gates
def side_gate(w, xc, out, name):
    """Gate through the west (out=-1) or east (out=+1) wall at z = 0 with two square towers."""
    top = 18
    # gatehouse block across the wall
    x0, x1 = xc - 3, xc + 3
    for x in range(x0, x1 + 1):
        for z in range(-6, 7):
            for y in range(1, top - 3):
                _stone(w, x, y, z)
    m = S.round_arch_mask(9, 8)
    for x in range(x0, x1 + 1):
        S.carve(w, "z", -4, 1, x, m, AIR)
    w.fill(x0, 0, -4, x1, 0, 4, B.S("cobbled_paving"))
    ring = K.ring_of(m)
    for x in (x0, x1) if out < 0 else (x1, x0):
        S.carve(w, "z", -5, 0, x, ring, F(SM))
    xo = x1 if out > 0 else x0
    for z in range(-4, 5):
        for y in (7, 8):
            if m[z + 4, y - 1]:
                w.set(xo, y, z, B.S("iron_bars"))
    w.set(xo, 9, 0, F(GOLD))
    w.set(xo + out, 12, 0, B.S("castle_crest_stone")) if False else None
    S.corbel_rect(w, x0, -6, x1, 6, top - 3, "stone_bricks")
    w.fill(x0 - 1, top - 2, -7, x1 + 1, top - 2, 7, F(SM))
    w.fill(x0, top - 2, -6, x1, top - 2, 6, B.S("cobbled_paving"))
    S.crenellate(w, x0 - 1, -7, x1 + 1, 7, top - 1, B.S("stone_bricks"), cap=B.slab("smooth_castle_stone"))
    w.set(xo, 12, 0, B.S("castle_crest_stone"))
    deco.kingdom_wall_banner(w, xo + out, 12, -2, facing="east" if out > 0 else "west")
    deco.kingdom_wall_banner(w, xo + out, 12, 2, facing="east" if out > 0 else "west")
    # square towers north and south of the gate, with blue pyramid roofs
    for s in (-1, 1):
        tz0, tz1 = (7, 13) if s > 0 else (-13, -7)
        tx0, tx1 = xc - 3, xc + 3
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                edge = x in (tx0, tx1) or z in (tz0, tz1)
                for y in range(1, top + 2):
                    if edge:
                        _stone(w, x, y, z)
                    else:
                        w.set(x, y, z, AIR)
        w.fill(tx0 + 1, WALK, tz0 + 1, tx1 - 1, WALK, tz1 - 1, F("spruce_planks"))
        S.corbel_rect(w, tx0, tz0, tx1, tz1, top + 1, "stone_bricks")
        w.fill(tx0 - 1, top + 2, tz0 - 1, tx1 + 1, top + 2, tz1 + 1, F(SM))
        tip = S.square_spire(w, xc, (tz0 + tz1) // 2, 4, top + 3, 12, ROOF, finial=False)
        w.set(xc, tip, (tz0 + tz1) // 2, F(GOLD))
        TK.flagpole(w, xc, tip, (tz0 + tz1) // 2, 3, "east", "kingdom", 3.0, 2.0, pole="fence")
        for y in (5, 13):
            w.set(xo, y, (tz0 + tz1) // 2, B.S("iron_bars"))
            w.set(xo, y + 1, (tz0 + tz1) // 2, B.S("iron_bars"))
        # door from the inside street into the tower, ladder to the walk
        xi = x0 if out > 0 else x1
        lo, hi = B.door_states("spruce", "east" if out > 0 else "west", "left")
        dz = tz0 + 3
        w.set(xi, 1, dz, lo)
        w.set(xi, 2, dz, hi)
        for y in range(1, WALK + 1):
            w.set(xc, y, tz1 - 1 if s > 0 else tz0 + 1, B.S("ladder", facing="north" if s > 0 else "south"))
        TK.guard(w, name, 1 if s < 0 else 2, xc - out * 5, 1, s * 6, 90.0 if out < 0 else -90.0)
        TK.guard(w, name, 3 if s < 0 else 4, xc + out * 6, 1, s * 6, -90.0 if out < 0 else 90.0)


# ============================================================================== Gant's office
def gant_office(w):
    rect = (90, 8, 100, 19)
    f, W, D = TK.frame_for(w, rect, "north")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="royal_roof_tiles", roof_kind="gable_x", portal_w=2,
                     portal_h=4, trim=None, wall="stone_bricks", pilaster="polished_andesite", plinth="cobblestone",
                     window_every=3, dormers=False, chimney=True,
                     sign=(["", "迷宮管理局", "Dungeon Warden", ""], "white"),
                     banner_nbt=B.banner_nbt([("sku", "white"), ("bo", "white"), ("cr", "gray")]),
                     banner_color="black")
    TK.civic_hall(f, W, D, sp)
    TK.pave(w, 88, 6, 101, 20, TK.COBBLE_MIX, seed=411)
    # interior: Gant's desk facing the door, map table of the labyrinth, notice board, weapon rack, cells key
    w.fill(91, 0, 9, 99, 0, 18, F("spruce_planks"))
    for x in range(93, 98):
        w.set(x, 1, 14, F("dark_oak_planks") if x in (93, 97) else B.S("barrel", facing="north"))
        w.set(x, 2, 14, B.slab("dark_oak", "top"))
    w.set(95, 3, 14, B.lectern("north", True))
    w.be(95, 3, 14, B.lectern_nbt("迷宮入場記録", "Gant", ["Entrance to the first labyrinth: east road.",
                                                          "Parties must register here."]))
    w.spot("npc.gatekeeper", (95, 1, 15), 180.0)
    w.set(92, 1, 11, B.S("cartography_table"))
    w.set(92, 2, 11, B.lantern())
    for z in range(10, 14):
        deco.item_frame(w, 91, 3, z, "east", ("minecraft:map", "minecraft:compass", "minecraft:map",
                                              "minecraft:recovery_compass")[z % 4])
    for x in (98, 99):
        deco.item_frame(w, x, 3, 18, "north", "minecraft:iron_sword", rotation=1)
    deco.armor_stand(w, 99, 0, 16, yaw=90.0, armor="iron", weapon="minecraft:iron_sword", offhand=deco.blue_shield())
    w.set(91, 1, 18, B.S("chest", facing="east"))
    w.set(92, 1, 18, B.S("barrel", facing="up"))
    deco.hanging_lantern(w, 95, 5, 11, drop=1)
    w.set(99, 1, 9, B.S("skeleton_skull", rotation=12)) if False else None
    from . import _furnish as FU
    # stair along the back wall (z = 18) rising west to the archive and the wardens' bunks (floor y = 5)
    FU.stair_up(w, 98, 18, 0, 5, "west", wood="spruce")
    y = 5
    for x in range(91, 100):
        for yy in (y + 1, y + 2):
            if w.is_empty(x, yy, 9) and x % 3:
                w.set(x, yy, 9, B.S("chiseled_bookshelf", facing="south", slot_0_occupied=True,
                                    slot_1_occupied=x % 2 == 0, slot_3_occupied=True, slot_5_occupied=x % 2 == 1))
    FU.bed_pair(w, 91, y, 16, "north", "gray")
    if w.is_empty(92, y + 1, 15):
        FU.nightstand(w, 92, y, 15)
    FU.desk(w, 96, y, 11, "north", wood="spruce", seed=21)
    deco.armor_stand(w, 99, y, 12, yaw=90.0, armor="chainmail", weapon="minecraft:iron_sword")
    for z in (11, 13):
        FU.item_on_wall(w, 99, y + 3, z, "west", "minecraft:map")
    FU.dress(w, 91, 10, 99, 17, y, 10, seed=22, rug_colors=("gray", "black"), paintings=1,
             keep={(92, 18), (93, 18), (92, 17)})
    deco.hanging_lantern(w, 95, 10, 14, drop=1)
    FU.dress(w, 91, 9, 99, 13, 0, 5, seed=23, rug_colors=None, paintings=1, beams_on=False)


# ============================================================================== postern to the harbour
def postern(w, x):
    zc = WZ_S
    for z in range(zc - 2, zc + 3):
        w.fill(x, 1, z, x + 1, 3, z, AIR)
        w.set(x, 0, z, B.S("cobbled_paving"))
        w.set(x + 1, 0, z, B.S("cobbled_paving"))
    for xx in (x - 1, x + 2):
        w.fill(xx, 1, zc + 2, xx, 4, zc + 2, F(SM))
    w.fill(x - 1, 4, zc + 2, x + 2, 4, zc + 2, F(SM))
    lo, hi = B.door_states("spruce", "south", "left")
    w.set(x, 1, zc + 2, lo)
    w.set(x, 2, zc + 2, hi)
    lo, hi = B.door_states("spruce", "south", "right")
    w.set(x + 1, 1, zc + 2, lo)
    w.set(x + 1, 2, zc + 2, hi)
    deco.kingdom_wall_banner(w, x - 1, 6, zc + 3, facing="south") if False else None


def build(w, district, terrain):
    zc = WZ_S
    # curtain walls (gates left open, towers drawn over)
    curtain(w, "z", WX_W, WZ_N, zc, -1, gaps=[(-5, 5)])
    curtain(w, "z", WX_E, WZ_N, zc, 1, gaps=[(-5, 5)])
    curtain(w, "x", zc, WX_W - 2, WX_E + 2, 1, gaps=[(-6, 6)])
    # wall towers
    towers = [(WX_W, -78), (WX_W, -40), (WX_W, 40), (WX_W, zc), (WX_E, -78), (WX_E, -40), (WX_E, 40),
              (WX_E, zc), (-62, zc), (-34, zc), (34, zc), (62, zc)]
    for i, (x, z) in enumerate(towers):
        corner = (x in (WX_W, WX_E) and z == zc)
        flag = ("pennant", 5.0) if (i % 2 == 0 or corner) else None
        tower(w, x, z, r=5.0 if corner else 4.5, top=16 if corner else 15, cone_h=14 if corner else 12, flag=flag)
        dirs = ["north", "south"] if x in (WX_W, WX_E) and not corner else ["east", "west"]
        if corner:
            dirs = ["north", "east" if x < 0 else "west"]
        walk_openings(w, x, z, 5.0 if corner else 4.5, dirs)
    main_gate(w)
    side_gate(w, WX_W, -1, "west_gate")
    side_gate(w, WX_E, 1, "east_gate")
    postern(w, 74)
    gant_office(w)
    # stairs up to the walk near the gates and corners (inner side)
    wall_stairs(w, -26, zc - 3 - 10, "south", n=1) if False else None
    for (x, z, d) in ((-20, zc - 3, "west"), (20, zc - 3, "east")):
        pass
    # wall-top guards and the bridge end
    TK.guard(w, "wall", 1, WX_W, WALK + 1, -20, 90.0)
    TK.guard(w, "wall", 2, WX_E, WALK + 1, 20, -90.0)
    TK.guard(w, "wall", 3, -48, WALK + 1, zc, 0.0)
    TK.guard(w, "wall", 4, 48, WALK + 1, zc, 0.0)
