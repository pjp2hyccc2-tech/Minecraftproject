"""風の港 (wind harbour, Lv.20-30): the harbour town on its terrace by the bay.

- the canal that carries the harbour river through the town into the sea (the river ended inland), lined with
  quay walls and crossed by a stone arch bridge
- the stone quay along the shore with bollards, ladders, cranes, crates and barrels, warehouses behind it
- the main pier with the large sailing ship moored alongside (hull in the water, three masts with white sails,
  bowsprit and jibs, rigging, the captain's cabin), timber piers with rowing boats
- the lighthouse on its rock at the north end of the bay with a breakwater
- the town: townhouses along the main street from the waystone to the quay, the market square with its fountain
  and flags, fishermen's cottages south of the canal
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2

from .. import build_kit as BK
from .. import geo
from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S
WATER = "minecraft:water[level=0]"
SEA = 63
QUAY_Y = 67
QUAY_MIX = [("cobbled_paving", 55), ("stone_bricks", 15), ("polished_andesite", 10), ("andesite", 8),
            ("mossy_stone_bricks", 6), ("cracked_stone_bricks", 6)]
STREET_MIX = [("cobbled_paving", 60), ("cobblestone", 12), ("stone_bricks", 10), ("andesite", 8), ("gravel", 5),
              ("mossy_cobblestone", 5)]

# geometry of the harbour (world coordinates)
CANAL = [(1104.0, 935.0), (1132.0, 946.0), (1186.0, 983.0)]
CANAL_HALF = 6.0
QUAY = [(1176.0, 966.0), (1222.0, 931.0), (1264.0, 899.0)]
PIER_X = (1220, 1225)
PIER_Z = (928, 978)
SHIP_X = 1233          # keel line
SHIP_Z = (936, 978)    # stern .. bow
LIGHT = (1296, 858)


def water_fill(P, x, z, top=SEA):
    """Make column (x, z) sea: carve to the seabed (keep it below top - 1) and fill with water."""
    t = P.top(x, z)
    if t >= top:
        P.fill(x, top - 2, z, x, top - 2, z, F("sand"))
        P.fill(x, top - 1, z, x, top, z, WATER)
        P.fill(x, top + 1, z, x, t + 2, z, AIR)
    elif P.water(x, z) is None:
        P.fill(x, t + 1, z, x, top, z, WATER)


# ====================================================================================== the canal and quay
def canal_and_quay():
    P = Piece("harbour_quay", "港の岸壁と運河 (quay, canal, stone bridge, piers)", 10, 1100, 880, 1290, 1000,
              below=14, above=26, kind="scenery")
    # canal: water at sea level between quay walls, the walls' tops paved at the town level
    cm = K.polyline_mask(P, CANAL, CANAL_HALF)
    wall = K.polyline_mask(P, CANAL, CANAL_HALF + 1.6) & ~cm
    bank = K.polyline_mask(P, CANAL, CANAL_HALF + 5.5) & ~cm
    for (x, z) in K.cells(cm, P):
        t = P.top(x, z)
        if P.wet(x, z) and t < SEA - 3:
            continue                    # already river or sea
        bed = min(t, SEA - 4)
        P.fill(x, bed - 1, z, x, bed, z, F("gravel") if hash2(x, z, 1) < 0.5 else F("sand"))
        P.fill(x, bed + 1, z, x, SEA, z, WATER)
        P.fill(x, SEA + 1, z, x, max(t, QUAY_Y) + 3, z, AIR)
    for (x, z) in K.cells(wall, P):
        t = P.top(x, z)
        if P.wet(x, z):
            continue                    # the walls open into the river and into the sea
        P.fill(x, min(t, SEA - 5), z, x, QUAY_Y - 1, z, F("stone_bricks"))
        P.set(x, QUAY_Y, z, F("smooth_stone"))
        P.fill(x, QUAY_Y + 1, z, x, max(t, QUAY_Y) + 3, z, AIR)
    for (x, z) in K.cells(bank & ~wall, P):
        K.set_ground(P, x, z, QUAY_Y, under="minecraft:stone_bricks")
        K.mixed(P, x, QUAY_Y, z, QUAY_MIX, 3)
    # the quay: a paved strip inland of the quay line, the quay wall on the line
    qm = K.polyline_mask(P, QUAY, 14.0)
    X, Z = K.grid(P)
    side = np.zeros_like(qm)
    for i in range(len(QUAY) - 1):
        (ax, az), (bx, bz) = QUAY[i], QUAY[i + 1]
        cross = (bx - ax) * (Z - az) - (bz - az) * (X - ax)
        seg = K.segment_mask(P, QUAY[i], QUAY[i + 1], 14.0)
        side |= seg & (cross < 0)              # land side (north-west of the line going north-east)
    land = qm & side
    edge = K.polyline_mask(P, QUAY, 1.0) & ~K.dilate(cm, 1)
    for (x, z) in K.cells(land & ~cm & ~wall, P):
        K.set_ground(P, x, z, QUAY_Y, under="minecraft:stone_bricks")
        K.mixed(P, x, QUAY_Y, z, QUAY_MIX, 5)
    for (x, z) in K.cells(edge & ~wall, P):
        t = P.top(x, z)
        P.fill(x, min(t, SEA - 6), z, x, QUAY_Y, z, F("stone_bricks"))
        P.set(x, QUAY_Y, z, F("smooth_stone"))
    # sea side of the quay line: sea all the way up (no beach left in front of the wall)
    seaside = K.polyline_mask(P, QUAY, 7.0) & ~side & ~edge & ~K.dilate(cm, 2)
    for (x, z) in K.cells(seaside, P):
        water_fill(P, x, z)
    # bollards and ladders along the quay edge
    for k, (x, z) in enumerate(sorted(K.cells(edge, P))):
        if k % 9 == 0:
            P.set(x, QUAY_Y + 1, z, F("polished_andesite"))
            P.set(x, QUAY_Y + 2, z, B.slab("polished_andesite", "bottom"))
    # the main stone pier, running out to sea
    x0, x1 = PIER_X
    z0, z1 = PIER_Z
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t = P.top(x, z)
            edgec = x in (x0, x1) or z == z1
            P.fill(x, min(t, SEA - 6), z, x, QUAY_Y - 1, z, F("stone_bricks"))
            P.set(x, QUAY_Y, z, F("smooth_stone") if edgec else F("polished_andesite") if (x + z) % 4 == 0
                  else F("cobbled_paving"))
            P.fill(x, QUAY_Y + 1, z, x, QUAY_Y + 4, z, AIR)
    for z in range(z0 + 3, z1 + 1, 8):
        for x in (x0, x1):
            P.set(x, QUAY_Y + 1, z, F("polished_andesite"))
            P.set(x, QUAY_Y + 2, z, B.slab("polished_andesite", "bottom"))
    for z in (z0 + 7, z1 - 1):
        P.set(x0, QUAY_Y + 1, z, B.log("dark_oak"))
        P.fill(x0, QUAY_Y + 2, z, x0, QUAY_Y + 3, z, B.fence("dark_oak"))
        P.set(x0, QUAY_Y + 4, z, B.lantern())
    # arches through the pier near its root so the harbour water flows under it
    for z in range(z0 + 2, z0 + 9):
        for x in range(x0 + 1, x1):
            P.fill(x, SEA - 4, z, x, SEA, z, WATER)
            P.fill(x, SEA + 1, z, x, SEA + 2, z, AIR)
    # timber piers west of the main pier with rowing boats
    for (px, pz0, pz1) in ((1196, 952, 974), (1250, 912, 936)):
        for x in range(px, px + 3):
            for z in range(pz0, pz1 + 1):
                t = P.top(x, z)
                P.set(x, QUAY_Y - 1, z, F("spruce_planks"))
                P.fill(x, QUAY_Y, z, x, QUAY_Y + 3, z, AIR)
                if (x in (px, px + 2)) and (z - pz0) % 4 == 0:
                    P.fill(x, min(t, SEA - 6), z, x, QUAY_Y - 2, z, B.log("spruce"))
                    P.set(x, QUAY_Y, z, B.fence("spruce"))
        rowboat(P, px + 4, pz0 + 6, "south")
        rowboat(P, px - 2, pz0 + 12, "south")
    # stone arch bridge over the canal
    mid = (1146.0, 957.0)
    ux, uz = 0.57, -0.82          # across the canal (perpendicular to its line)
    a = (mid[0] - ux * 11, mid[1] - uz * 11)
    b = (mid[0] + ux * 11, mid[1] + uz * 11)
    cells, _, L = BK.bridge_cells(a, b, 2.4)
    for (x, z), (t, s) in cells.items():
        if not (P.X0 <= x <= P.X1 and P.Z0 <= z <= P.Z1):
            continue
        tt = t * L
        c = L / 2
        us = SEA + 4.0 * math.sqrt(max(0.0, 1 - ((tt - c) / (CANAL_HALF + 1.5)) ** 2))
        hump = int(round(1.0 * math.cos((tt - c) / L * math.pi)))
        deck = QUAY_Y + hump
        ub = int(math.floor(us))
        tg = P.top(x, z)
        if cm[x - P.X0, z - P.Z0]:
            P.fill(x, ub, z, x, deck - 1, z, F("stone_bricks"))
            P.fill(x, SEA + 1, z, x, ub - 1, z, AIR)
        else:
            P.fill(x, min(tg, SEA - 4), z, x, deck - 1, z, F("stone_bricks"))
        edgec = abs(s) > 1.9
        P.set(x, deck, z, F("smooth_stone") if edgec else F("cobbled_paving"))
        P.fill(x, deck + 1, z, x, deck + 4, z, AIR)
        if edgec:
            P.set(x, deck + 1, z, B.wall("stone_bricks"))
    for (x, z) in (a, b):
        xi, zi = int(round(x)), int(round(z))
        P.set(xi, QUAY_Y + 1, zi, F("stone_bricks"))
        P.set(xi, QUAY_Y + 2, zi, B.wall("stone_bricks"))
        P.set(xi, QUAY_Y + 3, zi, B.lantern())
    # cranes on the quay, crates and barrels
    for (cx, cz, face) in ((1190, 946, "south"), (1240, 905, "south")):
        crane(P, cx, QUAY_Y, cz, face)
    rng = [(1184, 940), (1186, 941), (1185, 938), (1204, 934), (1205, 933), (1232, 913), (1233, 915),
           (1252, 902), (1253, 900), (1214, 924), (1179, 952)]
    for k, (x, z) in enumerate(rng):
        st = (B.barrel("up"), F("spruce_planks"), B.barrel("north"), B.S("hay_block", axis="x"),
              B.barrel("up"))[k % 5]
        P.set(x, QUAY_Y + 1, z, st)
        if k % 3 == 0:
            P.set(x, QUAY_Y + 2, z, B.barrel("up"))
    # flags along the quay
    for (x, z) in ((1200, 938), (1216, 926), (1246, 902)):
        K.flagpole(P, x, QUAY_Y, z, 7, facing="east", design="harbour", width=3.0, height=2.0, wood="dark_oak",
                   base=F("polished_andesite"))
    P.probe(x0, QUAY_Y, z0 + 20)
    P.view("harbour_quay", (1300, 100, 1040), (1200, 64, 940), fov=55)
    return P


def rowboat(P, x, z, facing):
    """A small rowing boat (5 long) floating at the water surface, bow towards ``facing``."""
    dx, dz = K.DIRS[facing]
    sx, sz = K.DIRS[K.CW[facing]]
    for i in range(5):
        cx_, cz_ = x + dx * i, z + dz * i
        if i in (0, 4):
            P.set(cx_, SEA + 1, cz_, B.stairs("spruce", K.OPP[facing] if i == 4 else facing, "bottom"))
            continue
        for s in (-1, 0, 1):
            px, pz = cx_ + sx * s, cz_ + sz * s
            P.set(px, SEA, pz, F("spruce_planks") if s == 0 else B.stairs("spruce", K.facing_of(sx * s, sz * s),
                                                                           "top"))
            if s != 0:
                P.set(px, SEA + 1, pz, B.trapdoor("spruce", K.facing_of(-sx * s, -sz * s), "bottom", True))
        P.set(cx_, SEA + 1, cz_, B.slab("spruce", "bottom") if i == 2 else AIR)


def crane(P, x, y, z, face):
    """A wooden quay crane: a framed mast, a jib over the water with a hanging hook and a load."""
    dx, dz = K.DIRS[face]
    for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        P.fill(x + a, y + 1, z + b, x + a, y + 9, z + b, B.log("spruce"))
    P.fill(x, y + 4, z, x + 1, y + 4, z + 1, F("spruce_planks"))
    for k in range(1, 8):
        P.set(x + dx * k, y + 10, z + dz * k, B.log("spruce", "x" if dx else "z"))
        if k == 2:
            P.set(x + dx * k, y + 8, z + dz * k, B.fence("spruce"))
            P.set(x + dx * k, y + 9, z + dz * k, B.fence("spruce"))
    P.fill(x, y + 10, z, x + 1, y + 10, z + 1, F("spruce_planks"))
    P.set(x, y + 11, z, B.fence("spruce"))
    for k in range(1, 5):
        P.set(x + dx * 7, y + 10 - k, z + dz * 7, B.chain())
    P.set(x + dx * 7, y + 5, z + dz * 7, B.barrel("up"))
    P.set(x + dx * 7, y + 4, z + dz * 7, B.S("hay_block", axis="y"))


# ====================================================================================== the ship
def ship():
    """A three-masted ship moored alongside the main pier: bow out to sea (south), stern at the quay."""
    zs, zb = SHIP_Z
    cx = SHIP_X
    P = Piece("harbour_ship", "帆船 (sailing ship at the main pier)", 10, cx - 12, zs - 6, cx + 12, zb + 10, below=12,
              above=48, y0=50, y1=112, kind="scenery")
    L = zb - zs + 1
    keel = 58
    deck = QUAY_Y
    # carve the seabed under the hull
    for x in range(cx - 7, cx + 8):
        for z in range(zs - 1, zb + 2):
            t = P.top(x, z)
            if t > keel - 2:
                P.fill(x, keel - 2, z, x, keel - 2, z, F("sand"))
                P.fill(x, keel - 1, z, x, SEA, z, WATER)
                if t > SEA:
                    P.fill(x, SEA + 1, z, x, t + 1, z, AIR)

    def half_width(z, y):
        """Half beam at station z (0 = stern .. 1 = bow) and height y."""
        u = (z - zs) / (L - 1)
        base = 5.2
        if u > 0.72:
            base *= math.sqrt(max(0.0, 1 - ((u - 0.72) / 0.28) ** 2)) * 0.98 + 0.02
        if u < 0.08:
            base *= 0.86 + 0.14 * (u / 0.08)
        v = (y - keel) / (deck - keel)        # 0 at the keel, 1 at the deck
        if v < 0:
            return -1
        shape = 0.38 + 0.62 * math.sqrt(min(1.0, v * 1.15))
        return base * shape

    hull_hi = "dark_oak_planks"
    hull_lo = "spruce_planks"
    for z in range(zs, zb + 1):
        u = (z - zs) / (L - 1)
        top = deck + (3 if u < 0.22 else 2 if u > 0.86 else 0)
        for y in range(keel, top + 2):
            hw = half_width(z, min(y, deck))
            if hw < 0:
                continue
            for x in range(cx - 7, cx + 8):
                d = abs(x - cx)
                if d > hw + 0.35:
                    continue
                inner = d < hw - 0.75
                if y <= top and not inner:
                    st = F(hull_lo) if y < SEA - 1 else (F("birch_planks") if y == deck - 2 else F(hull_hi))
                    P.set(x, y, z, st)
                elif y < deck and inner:
                    P.set(x, y, z, AIR if y > keel + 1 else F(hull_lo))
                elif y == deck and inner:
                    P.set(x, y, z, F("spruce_planks"))
                elif y == top + 1 and not inner:
                    P.set(x, y, z, B.fence("dark_oak"))
            # raised decks: forecastle and quarterdeck
            if top > deck:
                for x in range(cx - 7, cx + 8):
                    hw = half_width(z, deck)
                    if abs(x - cx) < hw - 0.75:
                        P.set(x, top, z, F("spruce_planks"))
                        P.fill(x, deck + 1, z, x, top - 1, z, AIR)
    # the captain's cabin under the quarterdeck: windows in the stern, a door to the main deck
    qz1 = zs + int(L * 0.22)
    for x in range(cx - 3, cx + 4):
        P.set(x, deck + 1, qz1, F("dark_oak_planks"))
        P.set(x, deck + 2, qz1, F("dark_oak_planks") if x != cx else AIR)
        P.set(x, deck + 1, qz1, F("dark_oak_planks") if x != cx else AIR)
    lo, hi = B.door_states("dark_oak", "south", "left")
    P.set(cx, deck + 1, qz1, lo)
    P.set(cx, deck + 2, qz1, hi)
    for x in range(cx - 3, cx + 4, 2):
        P.set(x, deck + 2, zs, B.S("glass_pane"))
    P.set(cx, deck + 1, zs + 2, B.lantern())
    for x in (cx - 4, cx + 4):
        P.set(x, deck + 5, zs, B.lantern())
    # stern lantern and the wheel on the quarterdeck
    P.set(cx, deck + 4, zs + 4, B.fence("dark_oak"))
    P.set(cx, deck + 5, zs + 4, B.S("dark_oak_trapdoor", facing="south", half="bottom", open=True))
    # masts with yards and sails, a bowsprit with jibs
    masts = [(zs + int(L * 0.30), 24), (zs + int(L * 0.52), 29), (zs + int(L * 0.74), 23)]
    for (mz, h) in masts:
        P.fill(cx, keel + 1, mz, cx, deck + h, mz, B.log("dark_oak"))
        top = deck + h
        P.set(cx, top + 1, mz, B.fence("dark_oak"))
        # fighting top
        P.fill(cx - 1, top - 6, mz - 1, cx + 1, top - 6, mz + 1, B.slab("spruce", "top"))
        P.set(cx, top - 6, mz, B.log("dark_oak"))
        for (y_yard, half, drop) in ((deck + 6, 6, 5), (top - 7, 5, 4), (top - 2, 3, 3)):
            for x in range(cx - half, cx + half + 1):
                P.set(x, y_yard, mz, B.log("spruce", "x") if abs(x - cx) < half else B.fence("spruce"))
            for k in range(1, drop + 1):
                bulge = 1 if 1 < k < drop else 0
                for x in range(cx - half + 1, cx + half):
                    if x == cx:
                        P.set(x, y_yard - k, mz + 1 + bulge, B.S("white_wool"))
                        continue
                    P.set(x, y_yard - k, mz + bulge + (1 if abs(x - cx) < half - 1 and bulge else 0), B.S("white_wool"))
        # shrouds: chains from the top down to the bulwarks
        for side in (-1, 1):
            for k in range(0, 8):
                yy = deck + 2 + k * 2
                xx = cx + side * max(1, int(round(4.5 - k * 0.5)))
                if yy < top - 6:
                    P.set(xx, yy, mz - 1, B.fence("dark_oak"))
                    P.set(xx, yy + 1, mz - 1, B.fence("dark_oak"))
    # bowsprit and jibs
    for k in range(0, 9):
        P.set(cx, deck + 3 + k // 3, zb + k - 1, B.log("dark_oak", "z"))
    for k in range(1, 7):
        for j in range(0, 6 - k):
            P.set(cx, deck + 4 + k + j, zb - 3 + k, B.S("white_wool"))
    # flag on the main mast, pennants on the others
    mz, h = masts[1]
    K.flag(P, cx, deck + h + 2, mz, facing="east", design="harbour", width=4.0, height=2.5)
    for (mz2, h2) in (masts[0], masts[2]):
        K.flag(P, cx, deck + h2 + 2, mz2, facing="east", design="pennant", width=5.0, height=1.0)
    # deck clutter: hatches, barrels, coiled rope (chains), the gangplank to the pier
    for (x, z) in ((cx, zs + int(L * 0.40)), (cx, zs + int(L * 0.62))):
        P.fill(x - 1, deck, z - 1, x + 1, deck, z + 1, B.trapdoor("spruce", "north", "top", False))
    for (x, z) in ((cx + 3, zs + 14), (cx + 3, zs + 15), (cx - 3, zs + 26), (cx + 2, zs + 30)):
        P.set(x, deck + 1, z, B.barrel("up"))
    for z in range(zs + 16, zs + 19):
        for x in range(PIER_X[1] + 1, cx - 3):
            P.set(x, deck, z, F("spruce_planks"))
            P.set(x, deck + 1, z, AIR)
    # mooring lines: chains from the pier bollards to the hull
    P.probe(cx, deck, zs + int(L * 0.45))
    P.view("harbour_ship", (cx + 44, deck + 20, zb + 26), (cx, deck + 10, (zs + zb) / 2), fov=55)
    P.view("harbour_ship_side", (cx + 40, deck + 4, (zs + zb) / 2), (cx, deck + 12, (zs + zb) / 2), fov=62)
    return P


# ====================================================================================== the lighthouse
def lighthouse():
    lx, lz = LIGHT
    P = Piece("harbour_lighthouse", "灯台と防波堤 (lighthouse and breakwater)", 10, lx - 22, lz - 22, lx + 22, lz + 30,
              below=14, above=50, y0=48, y1=118, kind="scenery")
    base = QUAY_Y
    # a rocky outcrop: a mound of stone, andesite and mossy cobble around the tower foot
    for (x, z) in K.cells(K.disc(P, lx, lz, 9), P):
        d = math.hypot(x - lx, z - lz)
        h = int(round(base + 2 - max(0.0, d - 4) * 0.9 + hash2(x, z, 3) * 1.5))
        t = P.top(x, z)
        if h > t:
            for yy in range(min(t, SEA - 3), h + 1):
                r = hash2(x, yy, z)
                P.set(x, yy, z, F("stone") if r < 0.4 else F("andesite") if r < 0.7 else F("mossy_cobblestone")
                      if r < 0.85 else F("cobblestone"))
    y0 = base + 2
    r = 4.0
    H = 30
    SH.cylinder(P, lx, lz, r + 1.5, y0 - 1, y0, F("stone_bricks"))
    for k in range(H):
        rr = r + 0.8 * (1 - k / H)
        st = F("white_concrete") if (k // 5) % 2 == 0 else F("red_concrete")
        if k < 3:
            st = F("stone_bricks")
        SH.cylinder(P, lx, lz, rr, y0 + 1 + k, y0 + 1 + k, st, hollow=True, thickness=1.4)
        SH.cylinder(P, lx, lz, rr - 1.3, y0 + 1 + k, y0 + 1 + k, AIR)
    for k in (8, 15, 22):
        P.set(lx + 4, y0 + k, lz, B.S("glass_pane"))
        P.set(lx - 4, y0 + k + 3, lz, B.S("glass_pane"))
    lo, hi = B.door_states("spruce", "west", "left")
    P.set(lx - 5, y0 + 1, lz, lo)
    P.set(lx - 5, y0 + 2, lz, hi)
    top = y0 + H + 1
    # gallery
    SH.cylinder(P, lx, lz, r + 1.5, top, top, F("smooth_stone"))
    SH.cylinder(P, lx, lz, r + 1.5, top + 1, top + 1, B.S("iron_bars"), hollow=True, thickness=1.0)
    # lantern room
    SH.cylinder(P, lx, lz, 2.4, top + 1, top + 4, B.S("glass"), hollow=True, thickness=1.0)
    SH.cylinder(P, lx, lz, 1.4, top + 1, top + 1, F("sea_lantern"))
    P.fill(lx, top + 2, lz, lx, top + 3, lz, F("glowstone"))
    SH.cone(P, lx, lz, 3.4, top + 5, 4, "dark_oak", finial=False)
    P.set(lx, top + 9, lz, B.S("lightning_rod", facing="up"))
    # the breakwater: a stone mole from the rock running south-west to shelter the harbour
    for k in range(0, 34):
        x, z = int(round(lx - 6 - k * 0.55)), int(round(lz + 6 + k * 0.83))
        for w in (-1, 0, 1):
            xx, zz = x + w, z
            t = P.top(xx, zz)
            P.fill(xx, min(t, SEA - 6), zz, xx, base - 1, zz, F("stone_bricks") if w == 0 else F("cobblestone"))
            P.set(xx, base, zz, F("smooth_stone") if w else F("polished_andesite"))
            P.fill(xx, base + 1, zz, xx, base + 3, zz, AIR)
        if k % 8 == 4:
            P.set(x, base + 1, z, B.log("dark_oak"))
            P.set(x, base + 2, z, B.lantern())
    P.probe(lx, top, lz + 4)
    P.view("harbour_lighthouse", (lx - 40, top - 4, lz + 40), (lx, top - 14, lz), fov=58)
    return P


# ====================================================================================== the town
STREET = [(1112.0, 880.0), (1200.0, 880.0), (1200.0, 944.0)]
ROOFS = ("royal_roof_tiles", "terracotta_roof_tiles", "deepslate_tiles", "pale_roof_tiles", "royal_roof_tiles",
         "dark_oak")


def town():
    P = Piece("harbour_town", "港町 (harbour town: houses, warehouses, market square)", 10, 1104, 846, 1262, 944,
              below=8, above=34, kind="scenery")
    y = QUAY_Y
    # streets: the main street from the waystone to the turn, south down to the quay; a lane north
    st = K.polyline_mask(P, STREET, 3.2)
    lane = K.polyline_mask(P, [(1166.0, 880.0), (1166.0, 848.0)], 2.2)
    square = K.disc(P, 1150, 881, 10.5)
    shrine_keep = K.disc(P, 1100, 876, 14)
    canal_keep = K.polyline_mask(P, CANAL, CANAL_HALF + 7)
    quay_keep = K.polyline_mask(P, QUAY, 15.0)
    for (x, z) in K.cells((st | lane | square) & ~shrine_keep & ~canal_keep, P):
        K.set_ground(P, x, z, y, under="minecraft:dirt")
        K.mixed(P, x, y, z, STREET_MIX, 11)
    # the market square: a fountain basin, lamp posts and the town flag
    sx, sz = 1150, 881
    for (x, z) in K.cells(K.disc(P, sx, sz, 3.6), P):
        d = math.hypot(x - sx, z - sz)
        if d > 2.6:
            P.set(x, y + 1, z, F("stone_bricks"))
        else:
            P.set(x, y, z, WATER)
            P.set(x, y - 1, z, F("stone_bricks"))
    P.fill(sx, y, sz, sx, y + 2, sz, F("stone_bricks"))
    P.set(sx, y + 3, sz, B.S("fountain_spray", power=2, facing="up"))
    for (ox, oz) in ((-8, -6), (8, -6), (-8, 7), (8, 7)):
        K.lamp_post(P, sx + ox, y, sz + oz, height=3)
    K.flagpole(P, sx + 6, y, sz + 9, 10, facing="east", design="harbour", width=4.0, height=2.5, wood="dark_oak",
               base=F("polished_andesite"))
    taken = shrine_keep | canal_keep | quay_keep | K.dilate(square, 1) | K.dilate(st, 0)
    n = [0]

    def row(a, b, fixed, side_axis, depth, front, seed, start_gap=0):
        """Houses side by side between a and b along x (side_axis 'z' lots) or along z."""
        pos = a + start_gap
        k = 0
        while pos < b - 5:
            w = 7 + int(hash2(seed, k, 3) * 4)
            if pos + w - 1 > b:
                break
            if side_axis == "z":       # lots along x, depth along z
                x0, x1 = pos, pos + w - 1
                z0, z1 = (fixed, fixed + depth - 1) if front == "north" else (fixed - depth + 1, fixed)
            else:
                z0, z1 = pos, pos + w - 1
                x0, x1 = (fixed, fixed + depth - 1) if front == "west" else (fixed - depth + 1, fixed)
            lot = K.rect(P, x0 - 1, z0 - 1, x1 + 1, z1 + 1)
            if not (lot & taken).any() and P.X0 + 2 <= x0 and x1 <= P.X1 - 2 and P.Z0 + 2 <= z0 and z1 <= P.Z1 - 2:
                _house(P, x0, z0, x1, z1, front, y, seed=seed * 100 + k, roofs=ROOFS)
                taken[K.rect(P, x0, z0, x1, z1)] = True
                n[0] += 1
            pos += w + (1 if hash2(seed, k, 9) < 0.5 else 0)
            k += 1

    row(1114, 1198, 875, "z", 9, "south", 31)        # north side of the main street
    row(1114, 1196, 885, "z", 9, "north", 32)        # south side
    row(1114, 1196, 865, "z", 8, "north", 33)        # back row facing the north lane... (fronts north)
    row(884, 942, 1195, "x", 9, "east", 34)          # west side of the street down to the quay
    row(884, 930, 1205, "x", 9, "west", 35)          # east side
    # warehouses facing the quay, behind the quay strip
    for (a, b, c, d, face, sd) in ((1212, 900, 1226, 912, "south", 1), (1232, 888, 1246, 900, "south", 2)):
        if not (K.rect(P, a, b, c, d) & (shrine_keep | canal_keep)).any():
            warehouse(P, a, b, c, d, face, y, sd)
            taken[K.rect(P, a - 1, b - 1, c + 1, d + 1)] = True
    # the tavern on the square's south side
    _house(P, 1140, 894, 1151, 904, "north", y, seed=500, roofs=("dark_oak",), floors=2)
    K.place_sign(P, 1145, y + 3, 893, ['{"text":"⚓"}', '{"translate":"sign.eternalrealm.harbour_town","bold":true}'],
                 wood="dark_oak", facing="north", wall=True)
    # trees and planters in the gaps
    for (x, z) in ((1136, 868), (1176, 868), (1190, 904)):
        if not taken[x - P.X0, z - P.Z0]:
            K.tree(P, x, K.ground_y(P, x, z), z, kind="oak", height=5, crown=2.8, seed=x)
    P.probe(sx, y + 1, sz - 3)
    P.view("harbour_town", (1240, y + 44, 980), (1160, y + 4, 890), fov=55)
    P.view("harbour_town_street", (1116, y + 8, 880), (1200, y + 5, 880), fov=62)
    return P


def _house(P, a, b, c, d, front, y, seed, roofs, floors=None):
    BK.fit_ground(P, a, b, c, d, y, found="stone_bricks", blend=0, margin=0)
    f, W, D = BK.frame_for(P, a, b, c, d, front, y)
    h = BK.House.pick(seed, roofs=roofs, wall="white_plaster" if hash2(seed, 1, 2) < 0.7 else "birch_planks")
    if floors:
        h.floors = floors
    h.roof_axis = "x"
    h.jetty = False
    BK.house(f, W, D, h)


def warehouse(P, a, b, c, d, front, y, seed):
    """A big warehouse: stone ground floor with wide doors, timber loft, hoist beam, gable roof."""
    BK.fit_ground(P, a, b, c, d, y, found="stone_bricks", blend=0, margin=0)
    f, W, D = BK.frame_for(P, a, b, c, d, front, y)
    h = BK.House(seed=seed, floors=2, fh=5, wall="spruce_planks", ground_wall="stone_bricks", timber="dark_oak",
                 roof="deepslate_tiles", roof_axis="z", shutters=None, chimney=None, flowers=False, interior=False,
                 porch=False, lantern=True)
    BK.house(f, W, D, h)
    mid = W // 2
    for x in range(mid - 1, mid + 2):
        f.fill(x, 1, 0, x, 3, 0, AIR)
    f.fill(mid - 2, 4, 0, mid + 2, 4, 0, B.log("dark_oak", "x"))
    for (x, z) in ((1, 2), (2, 2), (1, 3), (W - 2, 2), (W - 2, 3), (W - 3, 2)):
        f.set(x, 1, z, B.barrel("up"))
    f.set(1, 2, 2, B.barrel("up"))
    f.set(W - 2, 1, D - 2, F("spruce_planks"))
    f.set(mid, h.fh * 2 + 3, -1, B.log("dark_oak", "z"))
    f.set(mid, h.fh * 2 + 2, -1, B.chain())
    f.set(mid, h.fh * 2 + 1, -1, B.barrel("up"))


def fishermen():
    """Fishermen's cottages on the beach south of the canal, boats drawn up, drying racks."""
    P = Piece("harbour_fishermen", "漁師の家 (fishermen's cottages south of the canal)", 10, 1112, 958, 1178, 1012,
              below=8, above=22, kind="scenery")
    lots = [(1128, 976, 1134, 982, "east", 1), (1138, 986, 1144, 992, "east", 2), (1120, 990, 1126, 996, "north", 3)]
    for (a, b, c, d, face, sd) in lots:
        yy = max(P.top(x, z) for x in range(a, c + 1) for z in range(b, d + 1))
        yy = max(yy, 65)
        BK.fit_ground(P, a, b, c, d, yy, found="cobblestone", blend=3, margin=1)
        f, W, D = BK.frame_for(P, a, b, c, d, face, yy)
        BK.house(f, W, D, BK.House(seed=600 + sd, floors=1, wall="spruce_planks", timber="spruce",
                                   roof="dark_oak" if sd % 2 else "spruce", roof_axis="x", shutters=None,
                                   chimney="cobblestone", ground_wall="cobblestone", porch=False))
    # drying racks (fences with cobweb nets) and upturned boats on the sand
    for (x, z) in ((1150, 984), (1152, 988)):
        t = P.top(x, z)
        P.fill(x, t + 1, z, x, t + 2, z, B.fence("spruce"))
        P.fill(x + 3, t + 1, z, x + 3, t + 2, z, B.fence("spruce"))
        for k in range(1, 3):
            P.set(x + k, t + 2, z, S("cobweb"))
        P.fill(x, t + 3, z, x + 3, t + 3, z, B.slab("spruce", "bottom"))
    for (x, z) in ((1156, 996), (1146, 1002)):
        t = P.top(x, z)
        for i in range(4):
            P.set(x + i, t + 1, z, B.stairs("spruce", "north", "top"))
            P.set(x + i, t + 1, z + 1, B.stairs("spruce", "south", "top"))
    P.probe(1131, K.ground_y(P, 1131, 979), 979)
    return P


def build(only=None):
    out = []
    makers = [("harbour_quay", canal_and_quay), ("harbour_town", town), ("harbour_fishermen", fishermen),
              ("harbour_lighthouse", lighthouse), ("harbour_ship", ship)]
    for pid, fn in makers:
        if not only or pid in only:
            out.append(fn())
    return out
