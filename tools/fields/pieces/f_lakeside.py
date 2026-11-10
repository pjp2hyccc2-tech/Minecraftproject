"""湖畔のほとり (lakeside, Lv.15-25): the fishing village on the north shore (cottages, boathouses on stilts,
piers with rowing boats, drying racks), the long wooden bridge out to the island with a resting pavilion halfway,
the white gazebo shrine on the island among birches and cherries, reeds and lily pads along the shores.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2

from .. import build_kit as BK
from .. import kit as K
from .. import probe
from ..world import Piece
from .c_meadow import path_to_road
from .e_harbour import rowboat

AIR = K.AIR
F = K.F
S = K.S
WATER = "minecraft:water[level=0]"
SEA = 63
DECK = 65
BRIDGE_X = 318          # the bridge runs south along x = 316..320
ROOFS = ("deepslate_tiles", "pale_roof_tiles", "spruce", "royal_roof_tiles", "dark_oak")


def shoreline_z(P, x, z_from, z_to):
    """First wet z going south from z_from at column x (or None)."""
    for z in range(z_from, z_to):
        if P.wet(x, z):
            return z
    return None


# ====================================================================================== the fishing village
def village():
    P = Piece("lake_village", "漁師の村 (fishing village on the north shore)", 9, 286, 662, 356, 736, below=12,
              above=28, kind="scenery")
    roads = probe.layout()["roads"]
    road_keep = K.polyline_mask(P, next(r for r in roads if r["id"] == "lakeside")["points"], 6.0)
    shrine_keep = K.disc(P, 282, 676, 15)
    river_keep = K.disc(P, 360, 700, 1) | K.polyline_mask(P, [(360.0, 610.0), (360.0, 720.0)], 13.0)
    taken = road_keep | shrine_keep | river_keep
    # cottages on the slope above the shore, fronts towards the lake (south)
    lots = [(298, 684, 305, 691), (308, 686, 315, 693), (325, 690, 332, 697), (334, 686, 340, 693),
            (300, 696, 306, 702), (316, 698, 322, 704)]
    built = []
    for i, (a, b, c, d) in enumerate(lots):
        if (K.rect(P, a - 1, b - 1, c + 1, d + 1) & taken).any():
            continue
        yy = max(P.median_top(a, b, c, d), 65)
        if any(P.wet(x, z) for x in range(a, c + 1) for z in range(b, d + 1)):
            continue
        BK.fit_ground(P, a, b, c, d, yy, found="cobblestone", blend=3, margin=1)
        f, W, D = BK.frame_for(P, a, b, c, d, "south", yy)
        BK.house(f, W, D, BK.House.pick(700 + i, roofs=ROOFS, floors=1, wall="white_plaster" if i % 2 else
                                        "spruce_planks", roof_axis="x", jetty=False))
        taken[K.rect(P, a - 1, b - 1, c + 1, d + 2)] = True
        built.append((a, b, c, d, yy))
    # the shore path along the water with lantern posts
    for x in range(P.X0 + 2, P.X1 - 2):
        zs = shoreline_z(P, x, 690, 730)
        if zs is None:
            continue
        for z in range(zs - 4, zs - 1):
            if not taken[x - P.X0, z - P.Z0] and not P.wet(x, z):
                K.set_ground(P, x, z, max(P.top(x, z), 64), top="minecraft:dirt_path" if hash2(x, z, 4) < 0.8
                             else "minecraft:gravel")
        if x % 12 == 0 and not taken[x - P.X0, zs - 5 - P.Z0]:
            K.lantern_post(P, x, K.ground_y(P, x, zs - 5), zs - 5, height=3, wood="spruce")
    # piers with boats, a boathouse on stilts
    for (px, length) in ((302, 16), (336, 13)):
        zs = shoreline_z(P, px, 690, 730)
        if zs is None:
            continue
        for x in range(px, px + 3):
            for z in range(zs - 2, zs + length):
                t = P.top(x, z)
                P.set(x, DECK - 1, z, F("spruce_planks"))
                P.fill(x, DECK, z, x, DECK + 3, z, AIR)
                if x in (px, px + 2) and (z - zs) % 4 == 0:
                    P.fill(x, t, z, x, DECK - 2, z, B.log("spruce"))
                    P.set(x, DECK, z, B.fence("spruce"))
        rowboat(P, px + 4, zs + 3, "south")
        rowboat(P, px - 2, zs + 6, "south")
        P.set(px + 1, DECK, zs + length - 1, B.barrel("up"))
    # boathouse: a shed on stilts over the water with an open water door
    bx = 318
    zs = shoreline_z(P, bx, 690, 730) or 712
    for x in range(bx - 4, bx + 5):
        for z in range(zs - 2, zs + 8):
            t = P.top(x, z)
            edge = x in (bx - 4, bx + 4) or z in (zs - 2, zs + 7)
            P.set(x, DECK - 1, z, F("spruce_planks"))
            if (x in (bx - 4, bx + 4)) and (z - zs) % 3 == 0:
                P.fill(x, t, z, x, DECK - 2, z, B.log("spruce"))
            if edge:
                h = 4
                if z == zs + 7 and abs(x - bx) <= 1:
                    continue                                 # the water door
                P.fill(x, DECK, z, x, DECK + h - 1, z, F("spruce_planks") if not (x in (bx - 4, bx + 4) and
                                                                                   z in (zs - 2, zs + 7))
                       else B.log("spruce"))
            else:
                P.fill(x, DECK, z, x, DECK + 4, z, AIR)
    # the boat slip inside: water under an opening in the floor
    for x in range(bx - 1, bx + 2):
        for z in range(zs + 1, zs + 7):
            if P.wet(x, z):
                P.set(x, DECK - 1, z, AIR)
    rowboat(P, bx, zs + 1, "south")
    SH.gable_roof(P, bx - 4, zs - 2, bx + 4, zs + 7, DECK + 4, "spruce", axis="z", overhang=1, pitch=1,
                  gable_state=F("spruce_planks"))
    # drying racks with nets, barrels of fish
    for (x, z) in ((292, 706), (346, 702)):
        if taken[x - P.X0, z - P.Z0] or P.wet(x, z):
            continue
        t = P.top(x, z)
        P.fill(x, t + 1, z, x, t + 2, z, B.fence("spruce"))
        P.fill(x + 3, t + 1, z, x + 3, t + 2, z, B.fence("spruce"))
        for k in (1, 2):
            P.set(x + k, t + 2, z, S("cobweb"))
        P.fill(x, t + 3, z, x + 3, t + 3, z, B.slab("spruce", "bottom"))
        P.set(x + 1, t + 1, z + 1, B.barrel("up"))
    reeds(P, K.rect(P, P.X0, 700, P.X1, P.Z1), seed=3)
    P.probe(bx - 4, DECK, zs - 2)
    P.view("lake_village", (366, 92, 752), (318, 66, 700), fov=55)
    return P


def reeds(P, mask, seed=0, lily=0.08, cane=0.18):
    """Sugar cane on the sand next to water, lily pads on the water near the shore."""
    for (x, z) in K.cells(mask, P):
        if P.wet(x, z):
            w = P.water(x, z)
            if w == SEA and P.top(x, z) >= SEA - 3 and hash2(x, z, seed) < lily and P.get_idx(x, w + 1, z) <= 1:
                P.set(x, w + 1, z, S("lily_pad"))
            continue
        t = P.top(x, z)
        if P.get_idx(x, t + 1, z) != 0 or P.get_idx(x, t, z) > 1:
            continue
        wet_nb = any(P.wet(x + a, z + b) for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1))
                     if P.X0 <= x + a <= P.X1 and P.Z0 <= z + b <= P.Z1)
        if wet_nb and t <= SEA + 1 and hash2(x, z, seed + 1) < cane:
            h = 2 + int(hash2(z, x, seed) * 2)
            for k in range(1, h + 1):
                P.set(x, t + k, z, S("sugar_cane", age=0))


# ====================================================================================== the bridge to the island
def island_bridge():
    """A long timber bridge from the village shore to the island, on piles, with a covered resting pavilion."""
    x0, x1 = BRIDGE_X - 2, BRIDGE_X + 2
    P = Piece("lake_bridge", "湖の小島への橋 (long bridge to the island)", 9, x0 - 8, 700, x1 + 8, 860, below=14,
              above=20, kind="bridge")
    zs = shoreline_z(P, BRIDGE_X, 700, 760) or 712
    ze = None
    for z in range(830, 860):
        if not P.wet(BRIDGE_X, z):
            ze = z
            break
    ze = ze or 848
    z0, z1 = zs - 4, ze + 3
    mid = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t = P.top(x, z)
            edge = x in (x0, x1)
            P.set(x, DECK, z, F("spruce_planks") if not edge else B.log("spruce", "z"))
            P.fill(x, DECK + 1, z, x, DECK + 4, z, AIR)
            if t < DECK - 1 and not P.wet(x, z):
                P.fill(x, t + 1, z, x, DECK - 1, z, F("dirt") if abs(z - z0) < 3 or abs(z - z1) < 3 else AIR)
            if edge:
                P.set(x, DECK + 1, z, B.fence("spruce"))
            if edge and (z - z0) % 6 == 0:
                P.fill(x, min(t, SEA) - 1, z, x, DECK - 1, z, B.log("spruce"))
                P.set(x, DECK + 1, z, B.log("spruce"))
                P.set(x, DECK + 2, z, B.lantern() if (z - z0) % 18 == 0 else B.fence("spruce"))
            if (z - z0) % 6 == 0 and not edge:
                P.set(x, DECK - 1, z, B.log("spruce", "x"))
    # the resting pavilion halfway: wider deck, roof on posts, benches
    for x in range(x0 - 4, x1 + 5):
        for z in range(mid - 4, mid + 5):
            t = P.top(x, z)
            edge = x in (x0 - 4, x1 + 4) or z in (mid - 4, mid + 4)
            P.set(x, DECK, z, F("spruce_planks"))
            P.fill(x, DECK + 1, z, x, DECK + 6, z, AIR)
            if edge and not (x0 <= x <= x1):
                P.set(x, DECK + 1, z, B.fence("spruce"))
            if x in (x0 - 4, x1 + 4) and z in (mid - 4, mid + 4):
                P.fill(x, min(t, SEA) - 1, z, x, DECK - 1, z, B.log("spruce"))
                P.fill(x, DECK + 1, z, x, DECK + 4, z, B.log("spruce"))
    SH.hip_roof(P, x0 - 4, mid - 4, x1 + 4, mid + 4, DECK + 5, "dark_oak", overhang=1, pitch=1)
    P.set(BRIDGE_X, DECK + 4, mid, B.lantern(hanging=True))
    K.bench(P, x0 - 3, DECK, mid - 2, "east", length=4, wood="spruce", axis_dir="south")
    K.bench(P, x1 + 3, DECK, mid - 2, "west", length=4, wood="spruce", axis_dir="south")
    # lily pads beside the bridge
    for (x, z) in K.cells(K.rect(P, P.X0, z0 + 6, P.X1, z1 - 6), P):
        if P.wet(x, z) and not (x0 - 4 <= x <= x1 + 4) and hash2(x, z, 9) < 0.06:
            P.set(x, SEA + 1, z, S("lily_pad"))
    P.probe(x0, DECK, z0 + 12)
    P.view("lake_bridge", (BRIDGE_X + 50, 92, mid + 40), (BRIDGE_X, 66, mid), fov=58)
    return P


# ====================================================================================== the island
def island():
    L = probe.layout()
    ix, iz, ir = L["island"]
    ix, iz = int(ix), int(iz)
    P = Piece("lake_island", "湖の小島の四阿 (gazebo shrine on the island)", 9, ix - 50, iz - 64, ix + 50, iz + 52,
              below=8, above=26, kind="scenery")
    y = P.top(ix, iz)
    # the gazebo: eight white pillars, a dome, a raised round floor with steps
    for (x, z) in K.cells(K.disc(P, ix, iz, 7.5), P):
        t = P.top(x, z)
        if t < y + 1:
            P.fill(x, t - 1, z, x, y, z, F("stone_bricks"))
        P.set(x, y + 1, z, F("smooth_quartz") if math.hypot(x - ix, z - iz) < 6.6 else F("quartz_bricks"))
        P.fill(x, y + 2, z, x, y + 12, z, AIR)
    for k in range(8):
        a = k * math.pi / 4
        px, pz = ix + round(5.5 * math.cos(a)), iz + round(5.5 * math.sin(a))
        P.fill(px, y + 2, pz, px, y + 6, pz, B.S("quartz_pillar", axis="y"))
        P.set(px, y + 7, pz, F("chiseled_quartz_block"))
    SH.cylinder(P, ix, iz, 6.4, y + 8, y + 8, F("smooth_quartz"))
    SH.dome(P, ix, y + 8, iz, 6, F("pale_roof_tiles"), ry=5, hollow=True, thickness=1.2)
    P.set(ix, y + 14, iz, B.S("gilded_finial"))
    P.set(ix, y + 12, iz, B.chain())
    P.set(ix, y + 11, iz, B.chain())
    P.set(ix, y + 10, iz, B.lantern(hanging=True))
    # a basin and a statue stone in the middle
    P.set(ix, y + 2, iz, F("chiseled_quartz_block"))
    P.set(ix, y + 3, iz, B.candle(4, "light_blue"))
    for (x, z) in K.cells(K.disc(P, ix, iz, 2.4) & ~K.disc(P, ix, iz, 1.2), P):
        P.set(x, y + 1, z, WATER)
    # steps on the four sides
    for d in ("north", "south", "east", "west"):
        dx, dz = K.DIRS[d]
        sx, sz = K.DIRS[K.CW[d]]
        for i in range(1, 4):
            for s in (-1, 0, 1):
                x, z = ix + dx * (7 + i) + sx * s, iz + dz * (7 + i) + sz * s
                t = P.top(x, z)
                if t < y + 1 - i + 1:
                    P.fill(x, t, z, x, y - i + 1, z, F("stone_bricks"))
                    P.set(x, y - i + 2, z, B.stairs("quartz", K.OPP[d]))
    # the path from the bridge landing to the gazebo
    for z in range(iz - 62, iz - 7):
        for x in range(BRIDGE_X - 1, BRIDGE_X + 2):
            if P.wet(x, z):
                continue
            t = P.top(x, z)
            P.set(x, t, z, S("dirt_path") if hash2(x, z, 2) < 0.8 else F("gravel"))
        if z % 10 == 0 and not P.wet(BRIDGE_X + 3, z):
            K.lantern_post(P, BRIDGE_X + 3, P.top(BRIDGE_X + 3, z), z, height=2, wood="birch")
    # trees, flowers and reeds
    for k in range(18):
        a = k * 2.4
        r = 14 + (k * 7) % 26
        x, z = ix + round(r * math.cos(a)), iz + round(r * math.sin(a))
        if P.wet(x, z) or abs(x - BRIDGE_X) < 4:
            continue
        kind = ("birch", "cherry", "birch", "oak")[k % 4]
        K.tree(P, x, P.top(x, z), z, kind=kind, height=5 + k % 3, crown=2.6 + (k % 3) * 0.4, seed=k)
    K.flowers(P, K.disc(P, ix, iz, 40) & ~K.disc(P, ix, iz, 11), density=0.1, seed=17,
              kinds=["azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "allium", "blue_orchid"])
    reeds(P, ~K.disc(P, ix, iz, ir - 6), seed=5, lily=0.05, cane=0.25)
    P.probe(ix + 5, y + 1, iz)
    P.view("lake_island", (ix + 36, y + 22, iz + 34), (ix, y + 5, iz), fov=55)
    return P


def build(only=None):
    out = []
    for pid, fn in (("lake_village", village), ("lake_bridge", island_bridge), ("lake_island", island)):
        if not only or pid in only:
            out.append(fn())
    return out
