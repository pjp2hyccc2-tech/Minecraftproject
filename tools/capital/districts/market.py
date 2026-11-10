"""市場 The market, east and south-east of the plaza.

- Market square (SE quadrant x 6..51, z 6..39): rows of stalls with striped awnings (fruit, vegetables, flowers,
  cloth, fish, pottery, bread, potions, arms, books) along three aisles, a market cross with a fountain basin,
  pennant masts with market flags, crates, carts and benches.
- Covered market hall (x 62..84, z 10..30): open stone arcade on the ground floor full of counters, a timber-
  framed upper hall, a big terracotta roof with a cupola carrying a market flag.
- More stalls along the east avenue and a storehouse yard behind (x 88..101, z 21..39).
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "market"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF = K.CB, K.SM, K.GOLD, K.ROOF

VIEWS = {
    "market_square": dict(pos=(14, 7, 6), look=(44, 2, 30), fov=74, sun=(40, -160), fog=2600,
                          label="The market square"),
    "market_hall": dict(pos=(56, 6, 3), look=(76, 6, 22), fov=72, sun=(40, -160), fog=2600,
                        label="The covered market hall"),
    "market_hall_in": dict(pos=(64.5, 3.5, 12.5), look=(80, 2, 26), fov=80, sun=(40, -160), fog=900,
                           label="Inside the market hall"),
}

GOODS_CYCLE = ["fruit", "veg", "flowers", "cloth", "fish", "pots", "bread", "potions", "arms", "books"]


def stall_at(w, x0, z0, front, W=5, D=4, seed=0):
    """Stall whose counter faces ``front``; (x0, z0) = the corner of its footprint (W along the front, D+1 deep
    including the valance line on the front edge)."""
    if front in ("north", "south"):
        rect = (x0, z0, x0 + W - 1, z0 + D)
    else:
        rect = (x0, z0, x0 + D, z0 + W - 1)
    f, Wd, Dd = TK.frame_for(w, rect, front)
    colors = TK.AWNINGS[int(hash2(x0, z0, seed) * len(TK.AWNINGS))]
    goods = GOODS_CYCLE[int(hash2(z0, x0, seed + 1) * len(GOODS_CYCLE))]
    TK.market_stall(f, 0, 1, W=Wd, D=Dd - 1, colors=colors, goods=goods, seed=seed + x0 * 7 + z0)


# ============================================================================== market square
def square(w):
    TK.pave(w, 6, 6, 51, 39, TK.SQUARE_MIX, seed=101)
    # stall rows (counters face the aisles): A faces the east avenue, B/C share the aisle z 16..18,
    # D/E share the aisle z 29..31; the cross aisle x 33..37 holds the market cross
    rows = [(6, "north", (27, 33, 39, 45)), (11, "south", (27, 33, 39, 45)), (19, "north", (21, 27, 39, 45)),
            (24, "south", (12, 18, 24, 39, 45)), (32, "north", (8, 14, 20, 26, 32, 38, 44))]
    for (z0, front, xs) in rows:
        for i, x0 in enumerate(xs):
            stall_at(w, x0, z0, front, W=5, D=4, seed=11 + i * 3 + z0)
    # market cross with a fountain basin at the centre of the cross aisle
    cx, cz = 35, 24
    S.cylinder(w, cx, cz, 2.4, 1, 1, F(SM))
    S.cylinder(w, cx, cz, 1.4, 1, 1, B.S("water"))
    w.fill(cx, 1, cz, cx, 6, cz, B.pillar("y"))
    w.set(cx, 7, cz, F(CARVED))
    for (dx, dz, fac) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        w.set(cx + dx, 7, cz + dz, B.stairs(SM, K.OPP[fac], "top"))
        TK.spray(w, cx + dx, 5, cz + dz, power=1, facing=fac)
        w.set(cx + dx, 5, cz + dz, B.S("fountain_spray", power=1, facing=fac))
        w.set(cx + dx * 1, 6, cz + dz * 1, B.S("lightning_rod", facing=fac)) if False else None
    w.set(cx, 8, cz, F(GOLD))
    w.set(cx, 9, cz, B.S("gilded_finial"))
    w.set(cx, 4, cz, F(GOLD))
    # pennant masts with market flags at the aisle ends
    for (x, z) in ((51, 17), (51, 30), (8, 30), (33, 28), (37, 20)):
        TK.flagpole(w, x, 0, z, 8, "east", "market", 3.0, 2.0, pole="fence")
        w.set(x, 1, z, F(SM))
    # carts, crates and benches in the aisles
    for (x, z) in ((22, 17), (14, 38)):
        _cart(w, x, z, seed=x + z)
    for (x, z) in ((33, 21), (36, 27)):
        deco.bench(w, x, 0, z, facing="east" if x < 35 else "west", length=1) if False else None
    for (x, z) in ((26, 30), (50, 30), (38, 17), (20, 30)):
        K.lamp(w, x, 0, z, height=4)


def _cart(w, x, z, seed=0):
    """Small hand cart with goods: two wheels (dark oak trapdoors), planks bed, goods on top."""
    w.set(x, 1, z, B.S("spruce_slab", type="top") if False else B.slab("spruce", "top"))
    w.set(x + 1, 1, z, B.slab("spruce", "top"))
    w.set(x, 2, z, B.S("pumpkin") if hash2(x, z, seed) < 0.5 else B.S("hay_block", axis="y"))
    w.set(x + 1, 2, z, B.barrel("up"))
    w.set(x, 1, z - 1, B.trapdoor("dark_oak", "north", "bottom", True))
    w.set(x, 1, z + 1, B.trapdoor("dark_oak", "south", "bottom", True))
    w.set(x + 2, 1, z, B.fence("spruce"))


# ============================================================================== covered market hall
def market_hall(w):
    x0, x1, z0, z1 = 62, 84, 10, 30
    TK.pave(w, 59, 6, 87, 39, TK.SQUARE_MIX, seed=102)
    TK.pave(w, 88, 21, 98, 39, TK.LANE_MIX, seed=103)
    w.fill(x0, 0, z0, x1, 0, z1, F("stone_bricks"))
    gy = 7                                         # upper floor level
    # piers every 4 with round arches between them (open arcade)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            w.fill(x, 1, z, x, gy - 1, z, F(CB))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            w.fill(x, 1, z, x, gy - 1, z, F(CB))
    for (axis, c, out, a0, a1) in (("x", z0, -1, x0, x1), ("x", z1, 1, x0, x1), ("z", x0, -1, z0, z1),
                                   ("z", x1, 1, z0, z1)):
        f = K.Face(w, axis, c, out)
        u = a0 + 1
        while u + 2 < a1:
            span = min(3, a1 - u)
            f.mask(u, 1, 0, S.round_arch_mask(span, 5), AIR)
            u += 4
        for uu in range(a0, a1 + 1, 4):
            f.set(uu, 0, 1, F(SM)) if False else None
    # inner piers carrying the floor
    for x in range(x0 + 4, x1, 4):
        for z in range(z0 + 5, z1, 5):
            w.fill(x, 1, z, x, gy - 1, z, B.pillar("y"))
    w.fill(x0, gy, z0, x1, gy, z1, F("spruce_planks"))
    S.walls(w, x0, z0, x1, z1, gy, gy, F(SM))
    for (a, b, c, d, fac) in ((x0, z0 - 1, x1, z0 - 1, "south"), (x0, z1 + 1, x1, z1 + 1, "north"),
                              (x0 - 1, z0, x0 - 1, z1, "east"), (x1 + 1, z0, x1 + 1, z1, "west")):
        w.fill(a, gy, b, c, gy, d, B.stairs(SM, fac, "top"))
    # timber-framed upper hall
    top = gy + 6
    S.walls(w, x0, z0, x1, z1, gy + 1, top - 1, F("white_plaster"))
    for x in range(x0, x1 + 1):
        if (x - x0) % 3 == 0 or x == x1:
            for z in (z0, z1):
                w.fill(x, gy + 1, z, x, top - 1, z, B.log("dark_oak"))
    for z in range(z0, z1 + 1):
        if (z - z0) % 3 == 0 or z == z1:
            for x in (x0, x1):
                w.fill(x, gy + 1, z, x, top - 1, z, B.log("dark_oak"))
    S.rect_ring(w, x0, z0, x1, z1, top, B.log("dark_oak", "x"))
    w.fill(x0, top, z0, x0, top, z1, B.log("dark_oak", "z"))
    w.fill(x1, top, z0, x1, top, z1, B.log("dark_oak", "z"))
    w.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, F("spruce_planks"))
    for (axis, c, out, a0, a1) in (("x", z0, -1, x0, x1), ("x", z1, 1, x0, x1), ("z", x0, -1, z0, z1),
                                   ("z", x1, 1, z0, z1)):
        f = K.Face(w, axis, c, out)
        for u in range(a0 + 1, a1, 3):
            if u + 1 < a1:
                f.fill(u, gy + 2, 0, u + 1, gy + 4, 0, TK.LEADED)
                f.set(u, gy + 1, 1, B.leaves("flowering_azalea")) if hash2(u, c, 4) < 0.5 else None
                f.set(u + 1, gy + 1, 1, B.leaves("azalea")) if hash2(u, c, 5) < 0.4 else None
    # roof: big terracotta gable (ridge along x) with a cupola and the market flag
    r = K.Roof(w, x0, z0, x1, z1, top + 1, "terracotta_roof_tiles", axis="x", overhang=1,
               gable=F("white_plaster"), trim=None, end_overhang=1)
    ridge = r.ridge_y
    for x in (x0, x1):
        for z in range(z0, z1 + 1):
            for y in range(top + 1, ridge + 1):
                if w.get(x, y, z) == B.canon(F("white_plaster")) and (z - z0) % 3 == 0:
                    w.set(x, y, z, B.log("dark_oak"))
    for a in (67, 73, 79):
        K.dormer(w, r, -1, a, width=3, wall="white_plaster", frame="dark_oak", glass=TK.LEADED, mat=
                 "terracotta_roof_tiles")
        K.dormer(w, r, 1, a, width=3, wall="white_plaster", frame="dark_oak", glass=TK.LEADED, mat=
                 "terracotta_roof_tiles")
    ccx, ccz = (x0 + x1) // 2, (z0 + z1) // 2
    w.fill(ccx - 2, ridge - 1, ccz - 2, ccx + 2, ridge + 3, ccz + 2, B.log("dark_oak"))
    w.fill(ccx - 1, ridge, ccz - 2, ccx + 1, ridge + 2, ccz + 2, TK.LEADED)
    w.fill(ccx - 2, ridge, ccz - 1, ccx + 2, ridge + 2, ccz + 1, TK.LEADED)
    w.fill(ccx - 1, ridge, ccz - 1, ccx + 1, ridge + 2, ccz + 1, AIR)
    w.set(ccx, ridge + 2, ccz, B.S("bell", attachment="ceiling", facing="north"))
    S.square_spire(w, ccx, ccz, 3, ridge + 4, 7, ROOF, finial=False)
    w.set(ccx, ridge + 11, ccz, F(GOLD))
    TK.flagpole(w, ccx, ridge + 11, ccz, 3, "east", "market", 3.0, 2.0, pole="fence")
    # inside the arcade: counters and goods, lanterns
    for i, x in enumerate(range(x0 + 2, x1 - 1, 4)):
        for (z, fac) in ((z0 + 3, "south"), (z1 - 3, "north")):
            for dx in range(3):
                w.set(x + dx, 1, z, B.barrel("up") if dx == 1 else F("spruce_planks"))
                g = TK.GOODS[GOODS_CYCLE[(i * 2 + (z > 20)) % len(GOODS_CYCLE)]]
                w.set(x + dx, 2, z, TK._goods_state(g[int(hash2(x + dx, z, 7) * len(g))], fac))
        deco.hanging_lantern(w, x + 1, gy - 1, (z0 + z1) // 2, drop=1)
    # stair to the upper hall (merchants' guild room): long table and benches
    for i in range(6):
        w.set(x1 - 2, 1 + i, z0 + 2 + i, B.stairs("spruce", "south"))
    w.fill(x1 - 2, gy, z0 + 2, x1 - 2, gy, z0 + 8, AIR)
    deco.long_table(w, ccx - 6, gy, ccz, 12, axis="x", top="spruce", cloth="green", chairs=True)
    for x in (ccx - 4, ccx + 4):
        deco.chandelier(w, x, top, ccz, drop=2)


def avenue_stalls(w):
    """Stalls along the east avenue (south side) between the square and the hall, facing the avenue."""
    for i, x0 in enumerate(range(60, 84, 6)):
        stall_at(w, x0, 6, "north", W=5, D=3, seed=301 + i)
    # storehouse yard: stacked crates and a wagon shed
    for (x, z) in ((92, 24), (93, 24), (92, 25), (96, 30), (97, 30), (96, 31)):
        w.set(x, 1, z, B.barrel("up") if (x + z) % 3 else B.S("hay_block", axis="y"))
    w.set(92, 2, 24, B.barrel("up"))
    rect = (89, 32, 97, 38)
    f, W, D = TK.frame_for(w, rect, "north")
    sp = TK.HouseSpec.random(777, W, D)
    sp.floors = 2
    sp.roof = "terracotta_roof_tiles"
    TK.house(f, W, D, sp, nb_left=False, nb_right=False)


def crowd(w):
    pts = [(30, 17), (43, 17), (25, 30), (14, 30), (42, 30), (35, 20), (35, 28), (30, 38), (44, 38),
           (18, 38), (73, 20), (57, 26)]
    TK.crowd(w, "market", pts)


def build(w, district, terrain):
    square(w)
    market_hall(w)
    avenue_stalls(w)
    crowd(w)


CARVED = "carved_castle_stone"
