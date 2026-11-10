"""月影の森 (moon forest / elder forest, Lv.10-20): set pieces in the old woods.

The moon-viewing shrine on piers in the forest pond (reached by a bridge from the east shore, lit by soul lanterns),
the hunters' lodge west of the north road with its smokehouse and archery butts, a fairy ring of mushrooms under
giant glowing toadstools in the mushroom glade, and a giant fallen tree lying across the brook's ravine as a natural
bridge, glow berries hanging from it over the water.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital.noise import hash2, hash3

from .. import build_kit as BK
from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S

L = probe.layout()
PX, PZ, PR = L["pond"]
WATER = 69                                   # the pond's surface


# ====================================================================================== the pond shrine
def pond_shrine():
    cx, cz = -764, 200                       # the platform in the pond
    P = Piece("moon_pond_shrine", "月見の祠 (moon-viewing shrine on the forest pond)", 2, cx - 10, cz - 10, -728, cz + 10,
              below=10, above=16, kind="scenery")
    deck = WATER + 1
    # the platform on stone piers, a raised floor of dark oak with a stone rim
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            rim = abs(x - cx) == 4 or abs(z - cz) == 4
            if rim and abs(x - cx) == 4 and abs(z - cz) == 4 or (x - cx) % 4 == 0 and (z - cz) % 4 == 0:
                P.fill(x, P.top(x, z) - 1, z, x, deck - 1, z, F("stone_bricks") if hash3(x, 0, z, 1) < 0.6 else
                       F("mossy_stone_bricks"))
            P.set(x, deck, z, F("polished_andesite") if rim else F("dark_oak_planks"))
            P.fill(x, deck + 1, z, x, deck + 6, z, AIR)
    # the pavilion: four posts and a hipped roof over the middle 5 x 5, the shrine under it
    for (a, b) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        P.fill(cx + a, deck + 1, cz + b, cx + a, deck + 4, cz + b, B.log("dark_oak"))
    k = 0
    while True:
        a0, a1, b0, b1 = cx - 3 + k, cx + 3 - k, cz - 3 + k, cz + 3 - k
        if a0 > a1:
            break
        yy = deck + 5 + k
        for x in range(a0, a1 + 1):
            for z in range(b0, b1 + 1):
                if x not in (a0, a1) and z not in (b0, b1):
                    continue
                face = "south" if z == b0 else "north" if z == b1 else "east" if x == a0 else "west"
                P.set(x, yy, z, B.stairs("deepslate_tiles", face) if (a0, b0) != (a1, b1) else
                      B.slab("deepslate_tiles", "bottom"))
        k += 1
    P.fill(cx - 2, deck + 5, cz - 2, cx + 2, deck + 5, cz + 2, F("dark_oak_planks"), only_empty=True)
    P.set(cx, deck + 4, cz, B.lantern(hanging=True, soul=True))
    # the little shrine (hokora) on the west side, facing east towards the bridge
    hx = cx - 1
    P.fill(hx - 1, deck + 1, cz - 1, hx - 1, deck + 1, cz + 1, F("stone_bricks"))
    P.set(hx - 1, deck + 2, cz, F("chiseled_stone_bricks"))
    P.set(hx - 1, deck + 3, cz, S("sea_lantern"))
    P.set(hx - 1, deck + 2, cz - 1, B.candle(2, "white"))
    P.set(hx - 1, deck + 2, cz + 1, B.candle(2, "light_blue"))
    P.set(hx - 1, deck + 4, cz, B.slab("deepslate_tiles", "bottom"))
    for z in (cz - 1, cz + 1):
        P.set(hx - 1, deck + 3, z, B.stairs("dark_oak", "north" if z < cz else "south", "top"))
    P.set(hx, deck + 1, cz, S("white_carpet"))
    # railings round the platform except where the bridge lands; stone lanterns on the corners
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            if abs(x - cx) == 4 and abs(z - cz) == 4:
                K.stone_lantern(P, x, deck, z, stone="stone_bricks", soul=True, cap=B.slab("deepslate_tiles",
                                                                                           "bottom"))
            elif (abs(x - cx) == 4 or abs(z - cz) == 4) and not (x == cx + 4 and abs(z - cz) <= 1):
                P.set(x, deck + 1, z, B.fence("dark_oak"))
    # the bridge to the east shore: a gentle arch of dark oak with red railings, a torii at the shore
    x0b, x1b = cx + 5, -737
    span = x1b - x0b
    for i, x in enumerate(range(x0b, x1b + 1)):
        rise = 2.0 * math.sin(math.pi * i / span)
        s2 = round((deck + 1 + rise) * 2) / 2.0
        n = int(math.floor(s2))
        whole = abs(s2 - n) < 1e-6
        for z in (cz - 1, cz, cz + 1):
            if whole:
                P.set(x, n - 1, z, B.slab("dark_oak", "top"))
            else:
                P.set(x, n, z, B.slab("dark_oak", "bottom"))
            P.fill(x, n + (0 if whole else 1), z, x, n + 3, z, AIR)
        for z in (cz - 2, cz + 2):
            P.set(x, (n - 1) if whole else n, z, B.slab("dark_oak", "top" if whole else "bottom"))
            P.set(x, n if whole else n + 1, z, B.fence("mangrove"))
        if i in (0, span):
            for z in (cz - 2, cz + 2):
                P.set(x, (n if whole else n + 1) + 1, z, B.fence("mangrove"))
                P.set(x, (n if whole else n + 1) + 2, z, B.lantern(soul=True))
        if P.wet(x, cz) and i % 4 == 2:
            for z in (cz - 2, cz + 2):
                P.fill(x, P.top(x, z), z, x, (n - 2) if whole else (n - 1), z, B.log("dark_oak"))
    # the torii on the shore
    tx = x1b + 2
    ty = K.ground_y(P, tx, cz)
    for z in (cz - 2, cz + 2):
        P.fill(tx, ty + 1, z, tx, ty + 5, z, B.log("mangrove"))
    for z in range(cz - 3, cz + 4):
        P.set(tx, ty + 6, z, B.slab("mangrove", "bottom") if abs(z - cz) == 3 else F("mangrove_planks"))
    for z in range(cz - 2, cz + 3):
        P.set(tx, ty + 4, z, F("mangrove_planks") if abs(z - cz) < 2 else B.log("mangrove", "z"))
    P.set(tx, ty + 5, cz, F("dark_oak_planks"))
    for z in range(cz - 1, cz + 2):
        P.fill(tx, ty + 1, z, tx, ty + 3, z, AIR)
    # a gravel path from the torii into the woods, lanterns either side of it
    for x in range(tx - 1, tx + 9):
        for z in (cz - 1, cz, cz + 1):
            if P.X0 <= x <= P.X1 and not P.wet(x, z):
                t = P.top(x, z)
                P.set(x, t, z, S("gravel") if hash2(x, z, 3) < 0.6 else F("cobblestone"))
    for z in (cz - 3, cz + 3):
        K.stone_lantern(P, tx + 3, K.ground_y(P, tx + 3, z), z, soul=True)
    K.place_sign(P, tx + 6, K.ground_y(P, tx + 6, cz - 3) + 1, cz - 3,
                 ['{"translate":"sign.eternalrealm.moon_shrine","bold":true}', K.field_name(2)], wood="dark_oak", rotation=12)
    # lily pads and reeds round the platform
    for (x, z) in K.cells(K.disc(P, cx, cz, 9) & ~K.disc(P, cx, cz, 5.5), P):
        if P.wet(x, z) and abs(z - cz) > 2 and hash2(x, z, 9) < 0.18:
            P.set(x, WATER + 1, z, S("lily_pad"))
    P.probe(cx - 4, deck, cz - 4)
    P.view("moon_pond_shrine", (cx - 22, deck + 12, cz + 24), (cx + 6, deck + 2, cz), fov=55)
    return P


# ====================================================================================== the hunters' lodge
def log_cabin(P, x0, z0, x1, z1, y, wall_h=4, door=None, seed=0):
    """A cabin of horizontal spruce logs with corner posts, a steep spruce roof along x, a stone chimney with smoke
    on the east gable, windows with shutters. ``door``: (x, z) in the south or north wall."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge_x = x in (x0, x1)
            edge_z = z in (z0, z1)
            P.set(x, y, z, F("spruce_planks"))
            for yy in range(y + 1, y + wall_h + 1):
                if edge_x and edge_z:
                    P.set(x, yy, z, B.log("dark_oak"))
                elif edge_z:
                    P.set(x, yy, z, B.log("spruce", "x", stripped=hash3(x, yy, z, seed) < 0.2))
                elif edge_x:
                    P.set(x, yy, z, B.log("spruce", "z", stripped=hash3(x, yy, z, seed) < 0.2))
                else:
                    P.set(x, yy, z, AIR)
    # gables and roof (ridge along x)
    W = z1 - z0
    half = W // 2 + 1
    for k in range(half + 1):
        yy = y + wall_h + 1 + k
        for x in range(x0 - 1, x1 + 2):
            zn, zs = z0 - 1 + k, z1 + 1 - k
            if zn > zs:
                break
            if zn == zs:
                P.set(x, yy, zn, B.slab("spruce", "bottom"))
                continue
            P.set(x, yy, zn, B.stairs("spruce", "south"))
            P.set(x, yy, zs, B.stairs("spruce", "north"))
            if x in (x0, x1):
                for z in range(zn + 1, zs):
                    P.set(x, yy, z, B.log("spruce", "x"))
    # windows with shutters on the long walls, the door
    for x in range(x0 + 2, x1 - 1, 3):
        for (z, out) in ((z0, "north"), (z1, "south")):
            if door and (x == door[0] or abs(x - door[0]) == 1) and z == door[1]:
                continue
            P.set(x, y + 2, z, S("glass_pane"))
            dz = K.DIRS[out][1]
            for sx in (x - 1, x + 1):            # open shutters either side of the window
                P.set(sx, y + 2, z + dz, B.trapdoor("spruce", out, "bottom", open=True))
    if door:
        dx_, dz_ = door
        out = "south" if dz_ == z1 else "north"
        lower, upper = B.door_states("spruce", out)
        P.set(dx_, y + 1, dz_, lower)
        P.set(dx_, y + 2, dz_, upper)
    # chimney on the east gable
    cxh = x1 + 1
    czh = (z0 + z1) // 2
    top = y + wall_h + half + 2
    for yy in range(y - 1, top + 1):
        P.set(cxh, yy, czh, F("cobblestone") if hash3(cxh, yy, czh, 4) < 0.7 else F("mossy_cobblestone"))
    P.set(cxh, top + 1, czh, B.S("smoke_emitter", heavy=False))
    # inside: a hearth, a table, barrels, a rug
    P.set(x1 - 1, y + 1, czh, S("campfire", facing="west", lit=True, signal_fire=False))
    P.set(x0 + 1, y + 1, z0 + 1, B.barrel("up"))
    P.set(x0 + 1, y + 1, z1 - 1, B.barrel("up"))
    P.set(x0 + 3, y + 1, czh, B.fence("spruce"))
    P.set(x0 + 3, y + 2, czh, S("spruce_pressure_plate"))
    for x in range(x0 + 2, x1 - 2):
        P.set(x, y + 1, czh + 1, S("brown_carpet"))
    P.set(x1 - 2, y + 3, z0 + 1, B.lantern())


def lodge():
    x0, z0, x1, z1 = -754, 8, -743, 15
    P = Piece("moon_lodge", "狩人の小屋 (hunters' lodge, smokehouse, archery butts)", 2, -778, -6, -722, 34, below=8,
              above=18, kind="scenery")
    y = P.median_top(x0, z0, x1, z1) + 1
    BK.fit_ground(P, x0 - 1, z0 - 1, x1 + 2, z1 + 4, y, found="mossy_cobblestone", blend=4)
    door = ((x0 + x1) // 2, z1)
    log_cabin(P, x0, z0, x1, z1, y, wall_h=4, door=door, seed=3)
    # the porch: a deck in front of the door under a lean-to, antlers (skull) over the door, a bench
    for x in range(x0 + 2, x1 - 1):
        for z in (z1 + 1, z1 + 2):
            P.set(x, y, z, F("spruce_planks"))
            P.fill(x, y + 1, z, x, y + 3, z, AIR)
        P.set(x, y + 4, z1 + 1, B.slab("spruce", "top"))
        P.set(x, y + 4, z1 + 2, B.stairs("spruce", "north"))
    for x in (x0 + 2, x1 - 2):
        P.fill(x, y + 1, z1 + 2, x, y + 3, z1 + 2, B.fence("spruce"))
    P.set(door[0], y + 3, z1 + 1, S("skeleton_wall_skull", facing="south"))
    K.bench(P, door[0] + 2, y + 1, z1 + 1, "south", length=2, wood="spruce")
    P.set(door[0] - 2, y + 3, z1 + 1, B.lantern(hanging=True))
    # the smokehouse: a small stone hut east of the cabin, smoke from its vent
    sx0, sz0 = x1 + 4, z0 + 1
    for x in range(sx0, sx0 + 4):
        for z in range(sz0, sz0 + 4):
            t = P.top(x, z)
            P.fill(x, min(t, y) - 1, z, x, y, z, F("cobblestone"))
            P.fill(x, y + 1, z, x, max(t, y) + 3, z, AIR)
            edge = x in (sx0, sx0 + 3) or z in (sz0, sz0 + 3)
            for yy in range(y + 1, y + 4):
                if edge:
                    P.set(x, yy, z, F("mossy_cobblestone") if hash3(x, yy, z, 6) < 0.4 else F("cobblestone"))
            P.set(x, y + 4, z, B.slab("cobblestone", "bottom") if edge else F("cobblestone"))
    P.set(sx0 + 1, y + 1, sz0 + 3, AIR)
    P.set(sx0 + 1, y + 2, sz0 + 3, AIR)
    P.set(sx0 + 1, y + 1, sz0 + 3, S("spruce_trapdoor", facing="south", half="bottom", open=True))
    P.set(sx0 + 2, y + 1, sz0 + 1, S("campfire", facing="south", lit=True, signal_fire=False))
    P.set(sx0 + 2, y + 5, sz0 + 1, B.S("smoke_emitter", heavy=False))
    for x in (sx0 + 1, sx0 + 2):
        P.set(x, y + 3, sz0 + 2, S("chain", axis="y"))
    # drying racks with hides, the woodpile, barrels
    for i, x in enumerate(range(x0 - 4, x0 + 3, 3)):
        z = z1 + 5
        gy = K.ground_y(P, x, z)
        P.fill(x, gy + 1, z, x, gy + 2, z, B.fence("spruce"))
        P.fill(x + 1, gy + 1, z, x + 1, gy + 2, z, B.fence("spruce"))
        for xx in (x, x + 1):                    # a hide laid over the rack
            P.set(xx, gy + 3, z, B.slab("spruce", "top"))
            P.set(xx, gy + 4, z, S("brown_carpet") if i % 2 == 0 else S("light_gray_carpet"))
    for z in range(z0, z1 + 1):
        for yy in (y + 1, y + 2):
            if hash2(z, yy, 7) < 0.85:
                P.set(x0 - 2, yy, z, B.log("spruce", "x"))
    K.crate(P, x1 + 2, y + 1, z1 + 2, seed=4)
    P.set(x1 + 3, y + 1, z1 + 2, B.barrel("up"))
    # the archery butts west of the lodge: hay bales with targets, a shooting line of fence posts
    for i, z in enumerate((z0 - 2, z0 + 2, z0 + 6)):
        x = -772
        gy = K.ground_y(P, x, z)
        P.set(x, gy + 1, z, S("hay_block", axis="y"))
        P.set(x, gy + 2, z, S("target"))
        P.set(x - 1, gy + 1, z, S("hay_block", axis="y"))
    for z in range(z0 - 3, z0 + 8, 2):
        x = -758
        P.set(x, K.ground_y(P, x, z) + 1, z, B.fence("spruce"))
    # path to the road and the sign
    for x in range(-742, -722):
        for z in (z1 + 2, z1 + 3):
            t = P.top(x, z)
            if not P.wet(x, z) and abs(t - y) <= 3:
                P.set(x, t, z, S("dirt_path") if hash2(x, z, 8) < 0.7 else S("coarse_dirt"))
    K.place_sign(P, -725, K.ground_y(P, -725, z1 + 5) + 1, z1 + 5,
                 ['{"translate":"sign.eternalrealm.hunters_lodge","bold":true}'], wood="spruce", rotation=12)
    K.lantern_post(P, -727, K.ground_y(P, -727, z1 + 5), z1 + 5, height=3, wood="dark_oak", soul=True)
    P.probe(x0, y, z0)
    P.view("moon_lodge", (x1 + 20, y + 12, z1 + 22), ((x0 + x1) / 2, y + 2, (z0 + z1) / 2), fov=58)
    return P


# ====================================================================================== the fairy ring
def giant_mushroom(P, x, z, h, r, red=True, seed=0):
    y = K.ground_y(P, x, z)
    for k in range(1, h + 1):
        P.set(x, y + k, z, S("mushroom_stem"))
        if k > 2 and hash3(x, k, z, seed) < 0.25:
            P.set(x + 1, y + k, z, S("glow_lichen", west=True))
    cap = S("red_mushroom_block") if red else S("brown_mushroom_block")
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d > r + 0.3:
                continue
            if red:
                yy = y + h + (1 if d < r - 0.8 else 0)
                P.set(x + dx, yy, z + dz, cap)
                if d >= r - 0.8:
                    P.set(x + dx, yy - 1, z + dz, cap)
            else:
                P.set(x + dx, y + h + 1, z + dz, cap)
            if d < r - 1.2 and (dx or dz) and hash2(dx, dz, seed) < 0.35:
                P.set(x + dx, y + h, z + dz, S("shroomlight"))      # glowing gills under the cap
    if red:
        P.set(x, y + h + 2, z, cap)


def fairy_ring():
    cx, cz = -672, 132
    P = Piece("moon_fairy_ring", "妖精の輪 (fairy ring of mushrooms under giant toadstools)", 2, cx - 16, cz - 16,
              cx + 16, cz + 16, below=6, above=16, kind="scenery")
    r = 7.0
    for (x, z) in K.cells(K.disc(P, cx, cz, r + 0.8) & ~K.disc(P, cx, cz, r - 0.8), P):
        y = K.ground_y(P, x, z)
        if P.wet(x, z):
            continue
        P.set(x, y, z, S("podzol"))
        if hash2(x, z, 1) < 0.75:
            P.set(x, y + 1, z, S("red_mushroom") if hash2(z, x, 2) < 0.5 else S("brown_mushroom"))
    inner = K.disc(P, cx, cz, r - 1.2)
    for (x, z) in K.cells(inner, P):
        y = K.plantable(P, x, z)
        if y is None:
            continue
        q = hash2(x, z, 3)
        if q < 0.25:
            P.set(x, y + 1, z, S("moss_carpet"))
        elif q < 0.33:
            P.set(x, y + 1, z, S("lily_of_the_valley"))
        elif q < 0.38:
            P.set(x, y + 1, z, S("azure_bluet"))
    # the fairy stone in the middle: a mossy boulder with lichen and an amethyst
    y = K.ground_y(P, cx, cz)
    for (dx, dz, dy) in ((0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 2), (-1, 0, 1)):
        P.set(cx + dx, y + dy, cz + dz, F("mossy_cobblestone") if dy == 1 else F("moss_block"))
    P.set(cx, y + 3, cz, S("amethyst_cluster", facing="up"))
    P.set(cx, y + 2, cz - 1, S("glow_lichen", south=True))
    P.set(cx + 1, y + 2, cz, S("glow_lichen", west=True))
    # three giant toadstools round the ring
    for i, (a, h, rr, red) in enumerate(((0.3, 9, 4, True), (2.4, 7, 3, False), (4.3, 11, 4, True))):
        x = int(round(cx + 11 * math.cos(a)))
        z = int(round(cz + 11 * math.sin(a)))
        giant_mushroom(P, x, z, h, rr, red=red, seed=i)
    P.probe(cx, y + 1, cz)
    P.view("moon_fairy_ring", (cx + 20, y + 16, cz + 22), (cx, y + 3, cz), fov=58)
    return P


# ====================================================================================== the fallen giant over the brook
def fallen_tree():
    a = np.array([-676.0, 70.6, 256.0])      # the root end on the north-east bank
    b = np.array([-701.0, 70.6, 287.0])      # the broken crown end on the south-west bank
    P = Piece("moon_fallen_tree", "倒れた巨木の橋 (giant fallen tree across the brook)", 2, -712, 244, -664, 298,
              below=12, above=14, kind="scenery")
    axis = (b - a) / np.linalg.norm(b - a)
    Lt = float(np.linalg.norm(b - a))
    for x in range(P.X0, P.X1 + 1):
        for z in range(P.Z0, P.Z1 + 1):
            for y in range(66, 76):
                p = np.array([x + 0.5, y + 0.5, z + 0.5]) - a
                t = float(p @ axis)
                if t < -0.5 or t > Lt + 0.5:
                    continue
                d = float(np.linalg.norm(p - t * axis))
                rad = 2.0 - 0.7 * (t / Lt)
                if d <= rad:
                    end = t < 0.6 or t > Lt - 0.6
                    P.set(x, y, z, B.log("dark_oak", "y", stripped=True) if end else F("dark_oak_wood"))
    # moss on top, bracket fungi on the flanks, glow berries and a spore blossom hanging over the water
    for x in range(P.X0, P.X1 + 1):
        for z in range(P.Z0, P.Z1 + 1):
            col = [y for y in range(66, 76) if P.get(x, y, z).startswith("minecraft:dark_oak")]
            if not col:
                continue
            top, bot = max(col), min(col)
            if hash2(x, z, 1) < 0.6:
                P.set(x, top + 1, z, S("moss_carpet"))
            if P.wet(x, z) or P.top(x, z) < bot - 2:
                r = hash2(x, z, 2)
                if r < 0.18:
                    n = 2 + int(hash2(z, x, 3) * 4)
                    for k in range(1, n + 1):
                        if bot - k <= P.top(x, z) or P.get_idx(x, bot - k, z) > 1:
                            break
                        tip = k == n or bot - k - 1 <= P.top(x, z)
                        P.set(x, bot - k, z, S("cave_vines", berries=True, age=20) if tip else
                              S("cave_vines_plant", berries=hash2(x, k, 4) < 0.5))
                        if tip:
                            break
                elif r < 0.21:
                    P.set(x, bot - 1, z, S("spore_blossom"))
    for k in range(6, int(Lt) - 4, 5):
        p = a + axis * k
        side = np.array([axis[2], 0.0, -axis[0]])
        for sgn in (-1, 1):
            q = p + side * sgn * 2.1
            x, y, z = int(math.floor(q[0])), int(math.floor(q[1])), int(math.floor(q[2]))
            if P.get_idx(x, y, z) <= 1 and hash2(k, sgn, 5) < 0.6:
                P.set(x, y, z, S("brown_mushroom_block"))
    # the upturned root plate at the north-east end, the pit it left behind
    ra = a - axis * 1.0
    side = np.array([axis[2], 0.0, -axis[0]])
    for u in np.arange(-4.5, 4.6, 0.5):
        for v in np.arange(-3.5, 5.0, 0.5):
            if u * u / 20.0 + (v - 0.5) ** 2 / 16.0 > 1.0:
                continue
            q = ra + side * u + np.array([0.0, v, 0.0])
            x, y, z = int(math.floor(q[0])), int(math.floor(q[1])), int(math.floor(q[2]))
            r = hash3(x, y, z, 6)
            P.set(x, y, z, S("rooted_dirt") if r < 0.45 else S("mud") if r < 0.6 else S("mangrove_roots") if r < 0.85
                  else F("dirt"))
            if r > 0.9 and P.get_idx(x, y - 1, z) <= 1:
                P.set(x, y - 1, z, S("hanging_roots"))
    pit = ra - axis * 3.0
    for (x, z) in K.cells(K.disc(P, pit[0], pit[2], 3.2), P):
        t = P.top(x, z)
        d = math.hypot(x + 0.5 - pit[0], z + 0.5 - pit[2])
        dep = 2 if d < 2 else 1
        P.fill(x, t - dep + 1, z, x, t + 1, z, AIR)
        P.set(x, t - dep, z, S("rooted_dirt") if hash2(x, z, 7) < 0.5 else S("coarse_dirt"))
    # broken branches at the crown end
    for i, (dx, dy, dz, n) in enumerate(((1, 1, 0, 4), (-1, 1, 0, 3), (0, 1, 1, 3), (1, 0, 1, 3))):
        x, y, z = int(b[0]), int(b[1]) + 1, int(b[2])
        for k in range(1, n + 1):
            P.set(x + dx * k, y + dy * k, z + dz * k, B.log("dark_oak", "y" if dy and not (dx or dz) else
                                                                 ("x" if dx else "z")))
        P.set(x + dx * n, y + dy * n + 1, z + dz * n, B.leaves("dark_oak"))
    # steps up onto the trunk from the side near both ends
    side = np.array([axis[2], 0.0, -axis[0]])
    for (t, sgn) in ((3.0, 1), (Lt - 3.0, -1)):
        q = a + axis * t
        face = K.facing_of(-side[0] * sgn, -side[2] * sgn)
        for (off, dy) in ((2.7, 0), (1.8, 1)):
            p = q + side * sgn * off
            x, z = int(math.floor(p[0])), int(math.floor(p[2]))
            yy = 71 + dy
            if P.get_idx(x, yy, z) <= 1:
                P.fill(x, P.top(x, z) + 1, z, x, yy - 1, z, F("mossy_cobblestone"))
                P.set(x, yy, z, B.stairs("mossy_cobblestone", face))
    P.probe(int(a[0] + axis[0] * 10), int(a[1]), int(a[2] + axis[2] * 10))
    P.view("moon_fallen_tree", (-662, 84, 296), (-688, 68, 271), fov=58)
    return P


def build(only=None):
    out = []
    for pid, fn in (("moon_pond_shrine", pond_shrine), ("moon_lodge", lodge), ("moon_fairy_ring", fairy_ring),
                    ("moon_fallen_tree", fallen_tree)):
        if not only or pid in only:
            out.append(fn())
    return out
