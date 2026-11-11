"""市場 The market, east and south-east of the plaza.

- Market square (SE quadrant x 6..51, z 6..38): rows of stalls with striped awnings (fruit, vegetables, flowers,
  cloth, fish, pottery, bread, potions, arms, books) along three aisles, a market cross with a fountain basin,
  pennant masts with market flags, crates and carts; the old well (x 8, z 10) and the cellar hatch (x 29, z 17)
  lead down into the undercroft under the square (_undercroft.py: the underground waterway fed by the canal
  culvert, a smugglers' dock, a warehouse and the black market behind a secret door).
- The Galleria 商店回廊 (x 62..86, z 10..35, _galleria.py): an indoor shopping street of ten partitioned shops
  under a leaded-glass vault, arches at both ends (the east avenue to the north, the canal quay to the south).
- Two stalls along the east avenue either side of the Galleria forecourt, a storehouse yard behind
  (x 88..98, z 21..38).
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _galleria as GA
from . import _maze as MZ
from . import _town_kit as TK
from . import _undercroft as UC

DISTRICT = "market"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF = K.CB, K.SM, K.GOLD, K.ROOF

VIEWS = {
    "market_square": dict(pos=(14, 7, 6), look=(44, 2, 30), fov=74, sun=(40, -160), fog=2600,
                          label="The market square"),
    "galleria": dict(pos=(74.5, 4.5, 1.5), look=(74.5, 9, 12), fov=75, sun=(40, -140), fog=2600,
                     label="The Galleria from the east avenue"),
    "galleria_in": dict(pos=(72.5, 2.6, 12.0), look=(74.5, 5, 34), fov=80, sun=(40, -160), fog=900,
                        label="Inside the Galleria"),
    "galleria_shops": dict(pos=(76.5, 2.6, 17.5), look=(69, 2.8, 19.5), fov=85, sun=(40, -160), fog=900,
                           label="Shop fronts in the Galleria"),
    "undercroft_waterway": dict(pos=(38.6, -8.6, 36.5), look=(41, -8, 14), fov=80, sun=(70, -150), fog=900,
                                ambient_boost=2.5, exposure=1.6, label="The underground waterway under the market"),
    "undercroft_cistern": dict(pos=(35.5, -8.4, 18.5), look=(26, -10, 12), fov=85, sun=(70, -150), fog=900,
                               ambient_boost=2.5, exposure=1.6, label="The old cistern"),
    "undercroft_black_market": dict(pos=(22.5, -8.4, 33.5), look=(9, -9.5, 30), fov=85, sun=(70, -150), fog=900,
                                    ambient_boost=2.5, exposure=1.6, label="The black market behind the secret door"),
    "undercroft_cutaway": dict(pos=(10, 26, 52), look=(28, -10, 22), fov=62, sun=(70, -150), fog=2600,
                               clip=[(1, -5.5, True)], ambient_boost=1.8, exposure=1.3,
                               label="The undercroft, cut away at the ceilings"),
}

GOODS_CYCLE = ["fruit", "veg", "flowers", "cloth", "fish", "pots", "bread", "potions", "arms", "books"]
STALL_ICON = {"flowers": "flower", "cloth": "shirt", "fish": "fish", "pots": "mug", "bread": "bread",
              "potions": "potion", "arms": "sword", "books": "book"}      # shop_sign pictures of the stall trades


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
    if goods in STALL_ICON:                     # a board with the picture of the trade under the awning's front,
        mid = Wd // 2                           # over a spot of the counter where nothing tall stands
        for x in sorted(range(1, Wd - 1), key=lambda u: abs(u - mid)):
            if MZ.shop_sign(f, x, 3, 1, "north", STALL_ICON[goods], attach="ceiling"):
                break


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
    S.cylinder(w, cx, cz, 3.4, 1, 1, F(SM))
    S.cylinder(w, cx, cz, 3.4, 2, 2, B.slab(SM, "bottom"), hollow=True)
    S.cylinder(w, cx, cz, 2.5, 1, 1, B.S("water"))
    S.cylinder(w, cx, cz, 2.5, 2, 2, AIR)
    w.fill(cx, 1, cz, cx, 6, cz, B.pillar("y"))
    w.set(cx, 7, cz, F(CARVED))
    for (dx, dz, fac) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        w.set(cx + dx, 7, cz + dz, B.stairs(SM, K.OPP[fac], "top"))
        TK.spray(w, cx + dx, 5, cz + dz, power=1, facing=fac)
    w.set(cx, 8, cz, F(GOLD))
    w.set(cx, 9, cz, B.S("gilded_finial"))
    w.set(cx, 4, cz, F(GOLD))
    # pennant masts with market flags at the aisle ends
    for (x, z) in ((51, 17), (51, 30), (8, 30), (31, 29), (39, 19)):
        TK.flagpole(w, x, 0, z, 8, "east", "market", 3.0, 2.0, pole="fence")
        w.set(x, 1, z, F(SM))
    # carts, crates and benches in the aisles
    for (x, z) in ((22, 17), (14, 38)):
        _cart(w, x, z, seed=x + z)
    for (x, z) in ((26, 30), (50, 30), (38, 17), (20, 30)):
        K.lamp(w, x, 0, z, height=4)
    # pennant strings from the market cross (in front of its capital) to beside the finials of three lamps, low-sagging
    # over the stall awnings and clear of the masts' flags (which stream east)
    for (a, b, colors) in (((37, 7, 24), (49, 7, 30), (14, 4, 11, 0, 5)), ((33, 7, 24), (21, 7, 30), (1, 4, 3, 10, 5)),
                           ((35, 7, 22), (38, 7, 18), (6, 4, 9, 0, 3))):
        MZ.bunting(w, *a, (b[0] - a[0], b[1] - a[1], b[2] - a[2]), style="pennant", colors=colors, sag=0.4,
                   wind=1.0, facing="east" if b[0] > a[0] else "west" if b[0] < a[0] else "north")


def _cart(w, x, z, seed=0):
    """Small hand cart with goods: two wheels (dark oak trapdoors), planks bed, goods on top."""
    w.set(x, 1, z, B.slab("spruce", "top"))
    w.set(x + 1, 1, z, B.slab("spruce", "top"))
    w.set(x, 2, z, B.S("pumpkin") if hash2(x, z, seed) < 0.5 else B.S("hay_block", axis="y"))
    w.set(x + 1, 2, z, B.barrel("up"))
    w.set(x, 1, z - 1, B.trapdoor("dark_oak", "north", "bottom", True))
    w.set(x, 1, z + 1, B.trapdoor("dark_oak", "south", "bottom", True))
    w.set(x + 2, 1, z, B.fence("spruce"))


# ============================================================================== the Galleria
GALLERIA = (62, 10)          # north-west corner of the Galleria (x 62..86, z 10..35; 2026-11-11 rebuild)


def galleria_site(w):
    TK.pave(w, 59, 6, 87, 39, TK.SQUARE_MIX, seed=102)
    TK.pave(w, 88, 21, 98, 39, TK.LANE_MIX, seed=103)
    GA.galleria(w, *GALLERIA)


def avenue_stalls(w):
    """A stall on the east avenue (south side) west of the Galleria forecourt, a hand cart east of it."""
    stall_at(w, 59, 6, "north", W=5, D=3, seed=301)
    _cart(w, 83, 7, seed=302)
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
    pts = [(30, 17), (43, 17), (25, 30), (14, 30), (42, 30), (35, 20), (30, 38), (44, 38), (73, 8), (57, 26),
           (71, 14), (77, 18), (71, 28), (77, 31), (74, 37)]
    TK.crowd(w, "market", pts)


def build(w, district, terrain):
    square(w)
    UC.build(w)
    galleria_site(w)
    avenue_stalls(w)
    crowd(w)


CARVED = "carved_castle_stone"
