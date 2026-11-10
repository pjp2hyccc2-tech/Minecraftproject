"""豊穣の農場 (fertile farm, Lv.10-20): two farmsteads (farmhouse, red barn, silo, well, coop, yard), a second
windmill among the fields, a patchwork of crops on irrigated farmland (golden wheat, carrots, potatoes,
beetroots, pumpkins and melons) with water channels, plank footbridges, scarecrows and haystacks, an orchard and
fenced paddocks.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital.noise import hash2

from .. import build_kit as BK
from .. import geo
from .. import kit as K
from .. import probe
from ..world import Piece
from .c_meadow import path_to_road

AIR = K.AIR
F = K.F
S = K.S

CROPS = [("wheat", 7), ("wheat", 7), ("carrots", 7), ("potatoes", 7), ("beetroots", 3), ("wheat", 6),
         ("pumpkin", None), ("melon", None), ("harvest", None), ("wheat", 7)]


def road(rid):
    return next(r for r in probe.layout()["roads"] if r["id"] == rid)["points"]


def road_mask(P, margin=4.5):
    m = np.zeros((P.NX, P.NZ), bool)
    for r in probe.layout()["roads"]:
        m |= K.polyline_mask(P, r["points"], r["halfWidth"] + margin)
    return m


def plot(P, x0, z0, x1, z1, y, kind, seed):
    """One crop plot on farmland at ground height y (crops at y + 1)."""
    crop, age = kind
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if crop == "harvest":
                P.set(x, y, z, S("farmland", moisture=7) if (x + z) % 5 else F("coarse_dirt"))
                continue
            P.set(x, y, z, S("farmland", moisture=7))
            if crop in ("pumpkin", "melon"):
                # rows of stems with the fruit beside them
                if (x - x0) % 3 == 1:
                    if hash2(x, z, seed) < 0.45:
                        P.set(x, y + 1, z, S(f"attached_{crop}_stem", facing="east"))
                        P.set(x + 1, y, z, S("farmland", moisture=7))
                        P.set(x + 1, y + 1, z, S(crop))
                    else:
                        P.set(x, y + 1, z, S(f"{crop}_stem", age=7))
                continue
            a = age if hash2(x, z, seed + 3) < 0.9 else max(0, age - 2)
            P.set(x, y + 1, z, S(crop, age=a))
    if crop == "harvest":
        for k in range(int((x1 - x0) * (z1 - z0) / 30) + 1):
            hx = x0 + 1 + int(hash2(k, seed, 1) * max(1, x1 - x0 - 2))
            hz = z0 + 1 + int(hash2(seed, k, 2) * max(1, z1 - z0 - 2))
            P.set(hx, y + 1, hz, B.S("hay_block", axis="x" if k % 2 else "z"))
            if k % 3 == 0:
                P.set(hx, y + 2, hz, B.S("hay_block", axis="y"))


def patchwork(P, x0, z0, x1, z1, seed, avoid=None, plot_w=(9, 15), plot_d=(12, 22), y=None):
    """Fill a rectangle with crop plots separated by water channels (east-west) and paths (north-south)."""
    if y is None:
        y = P.median_top(x0, z0, x1, z1)
    area = K.rect(P, x0, z0, x1, z1)
    if avoid is not None:
        area &= ~avoid
    # level everything first (one height for the whole patchwork: the water stays put)
    for (x, z) in K.cells(area, P):
        K.set_ground(P, x, z, y, top="minecraft:grass_block[snowy=false]", clear=3)
    K.blend(P, area, y, width=6, skip=avoid)
    plots = []
    x = x0
    col = 0
    while x + plot_w[0] <= x1:
        w = plot_w[0] + int(hash2(col, seed, 5) * (plot_w[1] - plot_w[0]))
        w = min(w, x1 - x)
        z = z0
        row = 0
        while z + plot_d[0] <= z1:
            d = plot_d[0] + int(hash2(col, row, seed) * (plot_d[1] - plot_d[0]))
            d = min(d, z1 - z)
            plots.append((x, z, x + w - 1, z + d - 1, col, row))
            z += d + 1                                        # water channel row
            row += 1
        x += w + 2                                            # path column (2 wide)
        col += 1
    ncol = max(p[4] for p in plots) + 1 if plots else 1
    nrow = {}
    for p in plots:
        nrow[p[4]] = max(nrow.get(p[4], 0), p[5] + 1)
    kept = []
    for (a, b, c, d, col, row) in plots:
        sub = K.rect(P, a, b, c, d) & area
        if sub.sum() < (c - a + 1) * (d - b + 1) * 0.9:
            continue
        # a ragged outline: plots on the outer rows/columns are sometimes left fallow (meadow and flowers)
        outer = col in (0, ncol - 1) or row in (0, nrow[col] - 1)
        if outer and hash2(col, row, seed + 91) < 0.4:
            K.flowers(P, sub, density=0.08, seed=col * 13 + row)
            K.grass_tufts(P, sub, density=0.3, seed=col * 7 + row)
            continue
        kept.append((a, b, c, d, col, row))
        kind = CROPS[int(hash2(col * 7 + row, seed, 11) * len(CROPS))]
        plot(P, a, b, c, d, y, kind, seed + col * 31 + row)
        # the channel south of the plot
        for xx in range(a - 1, c + 2):
            zz = d + 1
            if area[xx - P.X0, zz - P.Z0] if (P.X0 <= xx <= P.X1 and P.Z0 <= zz <= P.Z1) else False:
                P.set(xx, y - 1, zz, F("clay") if hash2(xx, zz, 2) < 0.4 else F("dirt"))
                P.set(xx, y, zz, S("water", level=0))
                P.set(xx, y + 1, zz, AIR)
    plots = kept
    # hedgerows along parts of the outer edge, with a tree now and then
    edge = K.ring(area)
    for (x, z) in K.cells(edge, P):
        if hash2(x // 9, z // 9, seed + 5) < 0.55 and P.get_idx(x, y + 1, z) <= 1 and \
                P.get(x, y, z).startswith("minecraft:grass_block"):
            P.set(x, y + 1, z, B.leaves("oak"))
            if hash2(x, z, seed + 6) < 0.03:
                K.tree(P, x, y, z, kind="oak", height=4, crown=2.6, seed=x)
    # paths between the plot columns, footbridges where they cross the channels
    for (a, b, c, d, col, row) in plots:
        for xx in (c + 1, c + 2):
            for zz in range(b, d + 2):
                if not (P.X0 <= xx <= P.X1 and P.Z0 <= zz <= P.Z1) or not area[xx - P.X0, zz - P.Z0]:
                    continue
                if zz == d + 1:
                    P.set(xx, y, zz, F("spruce_planks"))
                else:
                    P.set(xx, y, zz, S("dirt_path") if hash2(xx, zz, 7) < 0.85 else F("coarse_dirt"))
    return y, plots


def scarecrows(P, plots, y, seed, n=3):
    chosen = [p for p in plots if hash2(p[4], p[5], seed) < 0.35][:n]
    for (a, b, c, d, _, _) in chosen:
        x, z = (a + c) // 2, (b + d) // 2
        P.set(x, y + 1, z, AIR)
        K.scarecrow(P, x, y, z, facing=("south", "west", "north", "east")[int(hash2(x, z, seed) * 4)])


# ====================================================================================== farmsteads
def farmstead(pid, name, cx, cz, front, seed, orchard=None, with_silo=True):
    """A farmstead round a yard at (cx, cz): farmhouse, red barn with a silo, coop, well, cart, fences."""
    P = Piece(pid, name, 8, cx - 40, cz - 36, cx + 40, cz + 36, below=8, above=34, kind="scenery")
    y = P.median_top(cx - 16, cz - 14, cx + 16, cz + 14)
    yard = K.rect(P, cx - 17, cz - 15, cx + 17, cz + 15)
    for (x, z) in K.cells(yard, P):
        K.set_ground(P, x, z, y, top="minecraft:grass_block[snowy=false]", clear=4)
    K.blend(P, yard, y, width=7)
    # yard surface: packed earth and gravel
    for (x, z) in K.cells(K.rect(P, cx - 9, cz - 7, cx + 9, cz + 7), P):
        r = hash2(x, z, seed)
        P.set(x, y, z, S("dirt_path") if r < 0.45 else F("coarse_dirt") if r < 0.7 else F("gravel")
              if r < 0.85 else S("grass_block", snowy=False))
    # farmhouse (2 storeys) on the north side of the yard, barn on the east, coop west
    f, W, D = BK.frame_for(P, cx - 8, cz - 16, cx + 2, cz - 9, "south", y)
    BK.house(f, W, D, BK.House(seed=seed, floors=2, wall="white_plaster", timber="dark_oak",
                               roof="terracotta_roof_tiles", roof_axis="x", shutters="dark_oak",
                               chimney="bricks", porch=True, ground_wall="cobblestone"))
    f2, W2, D2 = BK.frame_for(P, cx + 7, cz - 6, cx + 17, cz + 9, "west", y)
    BK.barn(f2, W2, D2, red=True, roof="dark_oak", seed=seed)
    if with_silo:
        BK.silo(P, cx + 14, y, cz + 13, r=2.6, h=13, wall="bricks", roof="waxed_oxidized_cut_copper")
    # chicken coop: a small shed with a fenced run
    f3, W3, D3 = BK.frame_for(P, cx - 16, cz + 4, cx - 12, cz + 8, "east", y)
    BK.shed(f3, W3, D3, wall="spruce_planks", roof="spruce", open_front=False)
    for x in range(cx - 11, cx - 6):
        for z in (cz + 3, cz + 9):
            P.set(x, y + 1, z, B.fence("spruce"))
    for z in range(cz + 3, cz + 10):
        P.set(cx - 6, y + 1, z, B.fence("spruce") if z != cz + 6 else B.gate("spruce", "east"))
    for x in range(cx - 11, cx - 6):
        for z in range(cz + 4, cz + 9):
            if hash2(x, z, 3) < 0.25:
                P.set(x, y + 1, z, S("grass"))
    K.well(P, cx - 3, y, cz + 2, stone="cobblestone", roof="spruce")
    K.cart(P, cx + 3, y, cz + 6, "north", wood="spruce", load=B.S("hay_block", axis="y"))
    for (ox, oz) in ((5, 2), (5, 3), (4, 3)):
        P.set(cx + ox, y + 1, cz + oz, B.S("hay_block", axis="y"))
    P.set(cx + 5, y + 2, cz + 3, B.S("hay_block", axis="x"))
    for (ox, oz) in ((-9, -6), (-1, -6)):
        P.set(cx + ox, y + 1, cz + oz, B.barrel("up"))
    # yard fence with two gates
    for x in range(cx - 17, cx + 18):
        for z in (cz - 15, cz + 15):
            if abs(x - cx) <= 1:
                if x == cx:
                    P.set(x, y + 1, z, B.gate("oak", "north" if z < cz else "south"))
                continue
            P.set(x, y + 1, z, B.fence("oak"))
    for z in range(cz - 14, cz + 15):
        for x in (cx - 17, cx + 17):
            if abs(z - cz) <= 1:
                if z == cz:
                    P.set(x, y + 1, z, B.gate("oak", "west" if x < cx else "east"))
                continue
            P.set(x, y + 1, z, B.fence("oak"))
    for (ox, oz) in ((-17, -15), (17, -15), (-17, 15), (17, 15)):
        P.set(cx + ox, y + 1, cz + oz, B.log("oak"))
        P.set(cx + ox, y + 2, cz + oz, B.lantern())
    # kitchen garden behind the house
    for x in range(cx - 15, cx - 10):
        for z in range(cz - 13, cz - 8):
            P.set(x, y, z, S("farmland", moisture=7))
            P.set(x, y + 1, z, S(("carrots", "beetroots", "potatoes")[(x + z) % 3],
                                 age=7 if (x + z) % 3 != 1 else 3))
    P.set(cx - 13, y, cz - 11, S("water", level=0))
    P.set(cx - 13, y + 1, cz - 11, S("lily_pad"))
    K.flagpole(P, cx + 12, y, cz - 13, 8, facing="east", design="market", width=3.0, height=1.8, wood="spruce")
    K.tree(P, cx - 14, y, cz - 3, kind="oak", height=5, crown=3.2, seed=seed)
    K.flowers(P, K.rect(P, cx - 16, cz - 14, cx + 16, cz + 14), density=0.05, seed=seed + 5)
    P.probe(cx - 3, y + 1, cz)
    P.view(pid, (cx + 42, y + 30, cz + 44), (cx, y + 4, cz), fov=55)
    return P, y


def orchard(P, x0, z0, x1, z1, y, seed):
    """Fruit trees in rows on grass, a fence round them."""
    for x in range(x0, x1 + 1, 6):
        for z in range(z0, z1 + 1, 6):
            xx = x + int(hash2(x, z, seed) * 2)
            zz = z + int(hash2(z, x, seed) * 2)
            yy = K.ground_y(P, xx, zz)
            K.tree(P, xx, yy, zz, kind="oak", height=3 + int(hash2(xx, zz, 4) * 2), crown=2.2, seed=seed + x)
            # blossom and fruit: flowering azalea leaves mixed in, a few fruit blocks
            for k in range(6):
                bx, by, bz = xx + int(hash2(k, xx, 1) * 5) - 2, yy + 4 + int(hash2(k, zz, 2) * 2), zz + \
                    int(hash2(zz, k, 3) * 5) - 2
                if P.get(bx, by, bz).startswith("minecraft:oak_leaves"):
                    P.set(bx, by, bz, B.leaves("flowering_azalea"))
    for x in range(x0 - 3, x1 + 4):
        for z in (z0 - 3, z1 + 3):
            P.set(x, K.ground_y(P, x, z) + 1, z, B.fence("spruce"))
    for z in range(z0 - 3, z1 + 4):
        for x in (x0 - 3, x1 + 3):
            P.set(x, K.ground_y(P, x, z) + 1, z, B.fence("spruce") if z != (z0 + z1) // 2 else
                  B.gate("spruce", "east"))


def stead_north():
    P, y = farmstead("farm_stead_north", "北の農家 (northern farmstead)", 690, 168, "south", seed=101)
    path_to_road(P, 690, 184, "east")
    return P


def stead_east():
    P, y = farmstead("farm_stead_east", "東の農家と果樹園 (eastern farmstead, orchard)", 940, 470, "west", seed=202)
    path_to_road(P, 922, 470, "farm_harbour")
    return P


def orchard_piece():
    P = Piece("farm_orchard", "果樹園 (orchard)", 8, 892, 506, 990, 560, below=6, above=16, kind="scenery")
    y = P.median_top(900, 512, 980, 552)
    m = K.rect(P, 898, 512, 982, 552)
    for (x, z) in K.cells(m, P):
        K.set_ground(P, x, z, y, clear=3)
    K.blend(P, m, y, width=5)
    orchard(P, 902, 516, 978, 548, y, 9)
    for k in range(8):
        x, z = 905 + k * 9, 530 + (k % 2) * 6
        if hash2(x, z, 1) < 0.6:
            P.set(x + 2, y + 1, z + 2, B.barrel("up"))
    P.probe(898 + 1, y + 1, 512 - 3)
    P.view("farm_orchard", (1000, y + 26, 590), (940, y + 3, 532), fov=55)
    return P


def fields_west():
    """The big patchwork between the east road, the farm-harbour road and the river, with the second windmill."""
    x0, z0, x1, z1 = 560, 270, 772, 420
    P = Piece("farm_fields_west", "西の畑と風車 (west fields and windmill)", 8, x0 - 10, z0 - 10, x1 + 10, z1 + 10,
              below=6, above=40, kind="scenery")
    avoid = road_mask(P, 4.0) | K.disc(P, 752, 248, 20)
    mill = (668, 346)
    avoid |= K.disc(P, mill[0], mill[1], 12)
    y, plots = patchwork(P, x0, z0, x1, z1, seed=1, avoid=avoid)
    # the windmill on its yard in the middle of the fields
    yard = K.disc(P, mill[0], mill[1], 10)
    for (x, z) in K.cells(yard, P):
        K.set_ground(P, x, z, y + 1, top="minecraft:grass_block[snowy=false]")
    for (x, z) in K.cells(K.disc(P, mill[0], mill[1], 6), P):
        K.mixed(P, x, y + 1, z, [("cobblestone", 30), ("gravel", 30), ("coarse_dirt", 40)], 3)
    BK.windmill(P, mill[0], y + 1, mill[1], facing="east", r=3.2, h=13, body="oak_planks", base="cobblestone",
                roof="spruce", sails=8.0, band="spruce_planks")
    for (ox, oz) in ((-5, 4), (-6, 3), (-5, 3)):
        P.set(mill[0] + ox, y + 2, mill[1] + oz, B.S("white_wool") if ox != -5 or oz != 4 else B.barrel("up"))
    scarecrows(P, plots, y, seed=3, n=4)
    P.probe(mill[0], y + 1, mill[1] - 5)
    P.view("farm_fields_west", (x1 + 40, y + 60, z1 + 50), ((x0 + x1) / 2, y, (z0 + z1) / 2), fov=55)
    P.view("farm_fields_west_mill", (mill[0] + 34, y + 12, mill[1] + 20), (mill[0], y + 10, mill[1]), fov=60)
    return P


def fields_east():
    x0, z0, x1, z1 = 862, 362, 980, 452
    P = Piece("farm_fields_east", "東の畑 (east fields)", 8, x0 - 10, z0 - 10, x1 + 10, z1 + 10, below=6, above=12,
              kind="scenery")
    avoid = road_mask(P, 4.0)
    y, plots = patchwork(P, x0, z0, x1, z1, seed=2, avoid=avoid, plot_w=(10, 16), plot_d=(14, 22))
    scarecrows(P, plots, y, seed=5, n=3)
    # haystacks on the harvested edge
    for k, (hx, hz) in enumerate(((x0 + 4, z1 + 5), (x0 + 12, z1 + 6), (x0 + 22, z1 + 5))):
        K.haystack(P, hx, K.ground_y(P, hx, hz), hz, r=1.7, seed=k)
    P.probe(x0 + 1, y, z0 + 1)
    P.view("farm_fields_east", (x1 + 30, y + 40, z1 + 40), ((x0 + x1) / 2, y, (z0 + z1) / 2), fov=55)
    return P


def paddocks():
    """Fenced pastures with a field shelter and a trough, by the east road north of the farmstead."""
    x0, z0, x1, z1 = 612, 112, 660, 150
    P = Piece("farm_paddocks", "放牧地 (paddocks)", 8, x0 - 6, z0 - 6, x1 + 6, z1 + 6, below=4, above=12,
              kind="scenery")
    mid = (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1) or x == mid
            if not edge:
                continue
            yy = K.ground_y(P, x, z)
            if z == z1 and x in (x0 + 6, mid + 6):
                P.set(x, yy + 1, z, B.gate("spruce", "south"))
            elif (x in (x0, x1, mid)) and (z in (z0, z1)):
                P.set(x, yy + 1, z, B.log("spruce"))
            else:
                P.set(x, yy + 1, z, B.fence("spruce"))
    # shelter in each paddock
    for sx in (x0 + 3, mid + 3):
        for x in range(sx, sx + 5):
            for z in range(z0 + 2, z0 + 6):
                yy = K.ground_y(P, x, z)
                if x in (sx, sx + 4) and z in (z0 + 2, z0 + 5):
                    P.fill(x, yy + 1, z, x, yy + 3, z, B.log("spruce"))
        yy = K.ground_y(P, sx + 2, z0 + 3) + 4
        P.fill(sx - 1, yy, z0 + 1, sx + 5, yy, z0 + 6, B.slab("spruce", "bottom"))
        for x in range(sx + 1, sx + 4, 2):
            P.set(x, K.ground_y(P, x, z0 + 3) + 1, z0 + 3, B.S("hay_block", axis="x"))
        tx, tz = sx + 8, z0 + 10
        for k in range(3):
            P.set(tx + k, K.ground_y(P, tx + k, tz) + 1, tz, B.S("water_cauldron", level=3) if k == 1 else
                  F("spruce_planks"))
    K.grass_tufts(P, K.rect(P, x0 + 1, z0 + 1, x1 - 1, z1 - 1), density=0.25, seed=6)
    P.probe(x0, K.ground_y(P, x0, z0), z0)
    return P


def build(only=None):
    out = []
    makers = [("farm_stead_north", stead_north), ("farm_stead_east", stead_east), ("farm_orchard", orchard_piece),
              ("farm_fields_west", fields_west), ("farm_fields_east", fields_east), ("farm_paddocks", paddocks)]
    for pid, fn in makers:
        if not only or pid in only:
            out.append(fn())
    return out
