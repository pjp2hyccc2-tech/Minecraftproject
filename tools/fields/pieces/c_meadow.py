"""はじまりの草原 (meadow, Lv.5-15): the windmill on its knoll with the miller's cottage, a shepherd's hut with
its sheep pen, the ring of ancient standing stones, a roadside well, an abandoned cart, flower meadows and
split-rail fences along the roads.
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

AIR = K.AIR
F = K.F
S = K.S


def road(rid):
    return next(r for r in probe.layout()["roads"] if r["id"] == rid)["points"]


def path_to_road(P, x, z, rid, width=1, top="minecraft:dirt_path", stop=3.5):
    """A footpath from (x, z) to the nearest point of a road."""
    n = geo.nearest(road(rid), x, z)
    px, pz = n[0], n[1]
    L = math.hypot(px - x, pz - z)
    steps = int(L)
    for i in range(steps + 1):
        t = i / max(steps, 1)
        cx, cz = x + (px - x) * t, z + (pz - z) * t
        if math.hypot(px - cx, pz - cz) < stop:
            break
        for a in range(-width, width + 1):
            for b in range(-width, width + 1):
                xx, zz = int(round(cx + a)), int(round(cz + b))
                if P.X0 <= xx <= P.X1 and P.Z0 <= zz <= P.Z1 and not P.wet(xx, zz) and abs(a) + abs(b) <= width:
                    if P.get_idx(xx, P.top(xx, zz), zz) == 0:
                        P.set(xx, P.top(xx, zz), zz, top if hash2(xx, zz, 3) < 0.8 else "minecraft:coarse_dirt")


def mound(P, cx, cz, r, h, top_y=None):
    """Raise a rounded knoll: +h at the centre, falling off to 0 at r. Returns the centre ground y."""
    X, Z = K.grid(P)
    d = np.hypot(X - cx, Z - cz)
    m = d <= r
    for (x, z) in K.cells(m, P):
        dd = math.hypot(x - cx, z - cz) / r
        add = h * (1 - dd * dd) ** 1.3
        target = int(round(P.top(x, z) + add)) if top_y is None else int(round(P.top(x, z) + (top_y - P.top(x, z)) *
                                                                                   (1 - dd * dd) ** 1.3))
        if target > P.top(x, z):
            K.set_ground(P, x, z, target)
    return P.top(cx, cz) + h if top_y is None else top_y


# ====================================================================================== the windmill
def windmill():
    cx, cz = -430, 692
    P = Piece("meadow_windmill", "風車の丘 (windmill on its knoll, miller's cottage)", 1, cx - 34, cz - 30, cx + 30,
              cz + 30, below=8, above=40, kind="scenery")
    y = mound(P, cx, cz, 22, 5)
    # level the top of the knoll around the mill
    top = K.disc(P, cx, cz, 7)
    for (x, z) in K.cells(top, P):
        K.set_ground(P, x, z, y)
    K.blend(P, top, y, width=8)
    for (x, z) in K.cells(K.disc(P, cx, cz, 5.5), P):
        K.mixed(P, x, y, z, [("cobblestone", 30), ("gravel", 25), ("coarse_dirt", 25), ("mossy_cobblestone", 20)], 5)
    hub = BK.windmill(P, cx, y, cz, facing="north", r=3.0, h=12, body="white_plaster", base="stone_bricks",
                      roof="dark_oak", sails=7.5)
    # the miller's cottage on the slope south-east, its yard and sacks of flour
    hx0, hz0 = cx + 8, cz + 9
    y2 = P.top(hx0 + 4, hz0 + 3)
    BK.fit_ground(P, hx0, hz0, hx0 + 8, hz0 + 6, y2, found="cobblestone", blend=4)
    f, W, D = BK.frame_for(P, hx0, hz0, hx0 + 8, hz0 + 6, "north", y2)
    BK.house(f, W, D, BK.House(seed=11, floors=1, wall="white_plaster", timber="dark_oak", roof="spruce",
                               roof_axis="x", shutters="spruce", chimney="cobblestone", porch=True))
    for (ox, oz, st) in ((-2, 2, B.S("hay_block", axis="y")), (-3, 2, B.S("white_wool")), (-3, 3, B.S("white_wool")),
                         (-2, 3, B.barrel("up")), (-4, 2, B.S("white_wool"))):
        x, z = cx + ox + 1, cz + oz + 4
        P.set(x, P.top(x, z) + 1 if P.get_idx(x, P.top(x, z) + 1, z) == 0 else K.ground_y(P, x, z) + 1, z, st)
    K.cart(P, cx - 6, K.ground_y(P, cx - 6, cz + 3), cz + 3, "south", wood="spruce", load=B.S("white_wool"))
    # a fence round the miller's garden with a gate to the path
    for x in range(hx0 - 2, hx0 + 11):
        for z in (hz0 + 9,):
            P.set(x, K.ground_y(P, x, z) + 1, z, B.fence("oak"))
    for z in range(hz0 - 1, hz0 + 9):
        P.set(hx0 + 10, K.ground_y(P, hx0 + 10, z) + 1, z, B.fence("oak"))
    for x in range(hx0 - 1, hx0 + 10):
        for z in range(hz0 + 7, hz0 + 9):
            yy = K.ground_y(P, x, z)
            if hash2(x, z, 4) < 0.7:
                P.set(x, yy, z, S("farmland", moisture=7))
                P.set(x, yy + 1, z, S(("carrots", "potatoes", "beetroots")[x % 3], age=7 if x % 3 != 2 else 3))
    path_to_road(P, cx - 4, cz - 6, "south")
    for (x, z) in ((cx - 2, cz - 9), (cx - 9, cz - 15)):
        K.lantern_post(P, x, K.ground_y(P, x, z), z, height=3, wood="spruce")
    K.flowers(P, K.disc(P, cx, cz, 26) & ~K.disc(P, cx, cz, 8), density=0.06, seed=31)
    K.grass_tufts(P, K.disc(P, cx, cz, 26) & ~K.disc(P, cx, cz, 7), density=0.18, seed=32)
    P.probe(cx, y, cz + 3)
    P.view("meadow_windmill", (cx + 40, y + 24, cz + 34), (cx, y + 9, cz), fov=55)
    P.view("meadow_windmill_road", (cx - 30, y + 6, cz - 40), (cx, y + 12, cz), fov=60)
    return P


# ====================================================================================== the standing stones
def stone_ring():
    cx, cz = -270, 604
    P = Piece("meadow_stone_ring", "古の環状列石 (ring of ancient standing stones)", 1, cx - 18, cz - 18, cx + 18,
              cz + 18, below=6, above=16, kind="scenery")
    y = P.top(cx, cz)
    n = 13
    R = 10.5
    stones = []
    for k in range(n):
        a = 2 * math.pi * k / n + 0.1
        x, z = cx + round(R * math.cos(a)), cz + round(R * math.sin(a))
        stones.append((x, z, a))
    for k, (x, z, a) in enumerate(stones):
        t = P.top(x, z)
        fallen = k in (4, 9)
        if fallen:
            # a toppled stone lying on its side, pointing outwards
            for i in range(4):
                xx, zz = x + round(math.cos(a) * i), z + round(math.sin(a) * i)
                P.set(xx, P.top(xx, zz) + 1, zz, F("mossy_cobblestone") if i % 2 else F("stone"))
            continue
        h = 4 + int(hash2(x, z, 2) * 3)
        for i in range(-1, h):
            for (dx, dz) in ((0, 0), (round(-math.sin(a)), round(math.cos(a)))):
                st = ("stone", "andesite", "mossy_cobblestone", "cobblestone", "stone")[int(hash2(x + dx, i, z) * 5)]
                P.set(x + dx, t + 1 + i, z + dz, F(st))
        stones[k] = (x, z, a, t + h)
    # trilithons: lintels over two pairs of neighbouring stones
    for k in (0, 6):
        s1, s2 = stones[k], stones[k + 1]
        if len(s1) == 4 and len(s2) == 4:
            yt = max(s1[3], s2[3]) + 1
            for (x, z, _a, top) in (s1, s2):
                P.fill(x, top + 1, z, x, yt - 1, z, F("stone"))
            for xx, zz in BK.deck_path((s1[0], s1[1]), (s2[0], s2[1])):
                P.set(xx, yt, zz, F("mossy_stone_bricks") if hash2(xx, zz, 1) < 0.5 else F("stone"))
    # the altar stone in the middle, worn paving round it
    for (x, z) in K.cells(K.disc(P, cx, cz, 3.2), P):
        K.mixed(P, x, P.top(x, z), z, [("mossy_cobblestone", 30), ("stone", 20), ("gravel", 15), ("moss_block", 20),
                                       ("cobblestone", 15)], 9)
    P.fill(cx - 1, y + 1, cz, cx + 1, y + 1, cz, F("smooth_stone"))
    P.set(cx - 1, y + 2, cz, B.slab("smooth_stone", "bottom"))
    P.set(cx + 1, y + 2, cz, B.slab("smooth_stone", "bottom"))
    P.set(cx, y + 2, cz, B.candle(3, "white"))
    K.flowers(P, K.disc(P, cx, cz, 16) & ~K.disc(P, cx, cz, 4), density=0.16, seed=12,
              kinds=["oxeye_daisy", "azure_bluet", "cornflower", "lily_of_the_valley", "allium"])
    K.grass_tufts(P, K.disc(P, cx, cz, 16), density=0.2, seed=4)
    P.probe(cx, y + 1, cz)
    P.view("meadow_stone_ring", (cx + 26, y + 16, cz + 24), (cx, y + 3, cz), fov=55)
    return P


# ====================================================================================== shepherd's hut
def shepherd():
    cx, cz = -60, 424
    P = Piece("meadow_shepherd", "羊飼いの小屋と囲い (shepherd's hut and sheep pen)", 1, cx - 24, cz - 18, cx + 22,
              cz + 22, below=6, above=20, kind="scenery")
    y = P.top(cx, cz)
    BK.fit_ground(P, cx - 4, cz - 3, cx + 4, cz + 3, y, found="cobblestone", blend=4)
    f, W, D = BK.frame_for(P, cx - 4, cz - 3, cx + 4, cz + 3, "west", y)
    BK.house(f, W, D, BK.House(seed=21, floors=1, wall="spruce_planks", timber="spruce", roof="dark_oak",
                               roof_axis="x", shutters=None, chimney="cobblestone", ground_wall="cobblestone",
                               porch=False))
    # the pen: a fenced paddock east of the hut, hay and a trough, a gate towards the hut
    px0, pz0, px1, pz1 = cx + 7, cz - 8, cx + 20, cz + 14
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            edge = x in (px0, px1) or z in (pz0, pz1)
            if not edge:
                continue
            yy = K.ground_y(P, x, z)
            if x == px0 and z == (pz0 + pz1) // 2:
                P.set(x, yy + 1, z, B.gate("spruce", "east"))
            else:
                P.set(x, yy + 1, z, B.fence("spruce"))
    for (x, z) in ((px0 + 2, pz0 + 2), (px0 + 3, pz0 + 2), (px0 + 2, pz0 + 3)):
        P.set(x, K.ground_y(P, x, z) + 1, z, B.S("hay_block", axis="y"))
    for x in range(px0 + 4, px0 + 8):
        z = pz1 - 2
        yy = K.ground_y(P, x, z)
        P.set(x, yy, z, B.S("water_cauldron", level=3) if x in (px0 + 5, px0 + 6) else F("spruce_planks"))
    # a shelter roof in the pen corner
    for x in range(px1 - 4, px1):
        for z in range(pz0 + 1, pz0 + 5):
            yy = K.ground_y(P, x, z)
            if (x in (px1 - 4, px1 - 1)) and (z in (pz0 + 1, pz0 + 4)):
                P.fill(x, yy + 1, z, x, yy + 3, z, B.log("spruce"))
    yy = K.ground_y(P, px1 - 2, pz0 + 2) + 4
    P.fill(px1 - 5, yy, pz0, px1, yy, pz0 + 5, B.slab("spruce", "bottom"))
    path_to_road(P, cx - 6, cz, "south")
    K.lantern_post(P, cx - 6, K.ground_y(P, cx - 6, cz - 3), cz - 3, height=3, wood="spruce")
    K.tree(P, cx - 3, K.ground_y(P, cx - 3, cz + 9), cz + 9, kind="oak", height=5, crown=3.0, seed=6)
    K.flowers(P, K.disc(P, cx, cz, 20) & ~K.rect(P, px0, pz0, px1, pz1), density=0.1, seed=41)
    P.probe(cx, y, cz)
    P.view("meadow_shepherd", (cx + 34, y + 20, cz + 38), (cx + 6, y + 2, cz + 3), fov=55)
    return P


# ====================================================================================== roadside well, cart
def well():
    cx, cz = -186, 474
    P = Piece("meadow_well", "街道の井戸 (roadside well and resting place)", 1, cx - 12, cz - 12, cx + 12, cz + 12,
              below=8, above=14, kind="scenery")
    y = P.top(cx, cz)
    for (x, z) in K.cells(K.disc(P, cx, cz, 4.6), P):
        K.mixed(P, x, P.top(x, z), z, K.COBBLE_MIX, 3)
    K.well(P, cx, P.top(cx, cz), cz, stone="cobblestone", roof="spruce")
    K.bench(P, cx - 2, P.top(cx - 2, cz + 5), cz + 5, "north", length=3, wood="spruce", axis_dir="east")
    K.tree(P, cx + 6, P.top(cx + 6, cz + 4), cz + 4, kind="oak", height=6, crown=3.4, seed=2)
    K.lantern_post(P, cx - 5, P.top(cx - 5, cz - 3), cz - 3, height=3, wood="spruce", arm="east")
    for (x, z, st) in ((cx + 3, cz - 4, B.barrel("up")), (cx + 4, cz - 4, B.S("water_cauldron", level=3))):
        P.set(x, P.top(x, z) + 1, z, st)
    K.flowers(P, K.disc(P, cx, cz, 11) & ~K.disc(P, cx, cz, 5), density=0.2, seed=9)
    P.probe(cx + 2, y + 1, cz + 2)
    return P


def cart():
    cx, cz = -556, 792
    P = Piece("meadow_cart", "打ち捨てられた荷車 (abandoned cart)", 1, cx - 8, cz - 8, cx + 8, cz + 8, below=4,
              above=8, kind="scenery")
    y = P.top(cx, cz)
    K.cart(P, cx, y, cz, "east", wood="spruce", broken=True)
    for (ox, oz, st) in ((2, 1, B.barrel("north")), (3, -1, B.barrel("up")), (-1, 2, B.S("hay_block", axis="x")),
                         (2, 2, B.S("hay_block", axis="z")), (-2, -1, S("pumpkin")), (1, -2, B.barrel("east"))):
        x, z = cx + ox, cz + oz
        P.set(x, P.top(x, z) + 1, z, st)
    P.set(cx + 1, y + 1, cz - 1, B.trapdoor("spruce", "north", "bottom", False))
    K.grass_tufts(P, K.disc(P, cx, cz, 7), density=0.35, seed=8)
    P.probe(cx, y + 1, cz)
    return P


# ====================================================================================== flowers and fences
def flower_patch(pid, cx, cz, r, seed, kinds=None):
    P = Piece(pid, "花畑 (flower meadow)", 1, cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2, below=2, above=4,
              kind="flowers")
    X, Z = K.grid(P)
    wob = r * (0.75 + 0.25 * np.sin(np.arctan2(Z - cz, X - cx) * 3 + seed))
    m = np.hypot(X - cx, Z - cz) <= wob
    K.flowers(P, m, density=0.62, seed=seed, kinds=kinds, tall=0.12)
    return P


def fences(pid, rid, s0, s1, seed=0, off=4.0, every=(26, 9)):
    """Split-rail fences along both sides of a road between two arc lengths, in sections with gaps."""
    pts = road(rid)
    xs, zs = [], []
    for s in np.arange(s0, s1, 3.0):
        x, z, _, _ = geo.point_at(pts, s)
        xs.append(x)
        zs.append(z)
    P = Piece(pid, f"街道の柵 ({rid} road)", 1, min(xs) - 8, min(zs) - 8, max(xs) + 8, max(zs) + 8, below=3, above=5,
              kind="road")
    on, gap = every
    placed = set()
    for side in (-1, 1):
        s = s0 + (seed * 7 + (5 if side > 0 else 0)) % (on + gap)
        while s < s1:
            run = on + int(hash2(int(s), side, seed) * 10) - 5
            for d in np.arange(0, run, 0.5):
                x, z, ux, uz = geo.point_at(pts, s + d)
                px, pz = int(round(x - uz * off * side)), int(round(z + ux * off * side))
                if not (P.X0 <= px <= P.X1 and P.Z0 <= pz <= P.Z1):
                    continue
                if (px, pz) in placed or P.wet(px, pz) or geo.capital_distance(px, pz) < 26:
                    continue
                if geo.nearest(pts, px, pz)[5] < off - 1.0:
                    continue
                placed.add((px, pz))
                P.set(px, P.top(px, pz) + 1, pz, B.fence("oak"))
            s += run + gap + int(hash2(int(s), side, seed + 1) * 8)
    first = sorted(placed)[0]
    P.probe(first[0], P.top(first[0], first[1]) + 1, first[1])
    return P


def build(only=None):
    out = []
    makers = [("meadow_windmill", windmill), ("meadow_stone_ring", stone_ring), ("meadow_shepherd", shepherd),
              ("meadow_well", well), ("meadow_cart", cart)]
    for pid, fn in makers:
        if not only or pid in only:
            out.append(fn())
    patches = [("meadow_flowers_1", -150, 560, 9, 1, None), ("meadow_flowers_2", -480, 610, 8, 2, None),
               ("meadow_flowers_3", -330, 720, 10, 3, ["poppy", "red_tulip", "orange_tulip", "dandelion"]),
               ("meadow_flowers_4", -40, 300, 7, 4, ["cornflower", "azure_bluet", "allium", "lily_of_the_valley"]),
               ("meadow_flowers_5", -600, 860, 8, 5, None)]
    for (pid, x, z, r, sd, kinds) in patches:
        if not only or pid in only:
            P = flower_patch(pid, x, z, r, sd, kinds)
            out.append(P)
    runs = [("meadow_fences_south_1", "south", 40, 330, 1), ("meadow_fences_south_2", "south", 330, 640, 2),
            ("meadow_fences_south_3", "south", 640, 940, 3), ("meadow_fences_lakeside", "lakeside", 40, 270, 4)]
    for (pid, rid, a, b, sd) in runs:
        if not only or pid in only:
            out.append(fences(pid, rid, a, b, seed=sd))
    return out
