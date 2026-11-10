"""聖なる滝 (sacred falls, Lv.20-30): the stairway up the cliff beside the falls (a white stone tower with a spiral
stair, its top bridged to the plateau), the prayer platform at the lip with its gate, lanterns and cherry trees,
white stone arches along the riverside path from the waystone, and the observation bridge across the river below
the plunge pool, from which a path leads on to the barred gate in the cliff (d4).
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

AIR = K.AIR
F = K.F
S = K.S

L = probe.layout()
LIP = L["falls"]["lip"]
NX_, NZ_ = L["falls"]["normal"]           # onto the plateau
TX_, TZ_ = -NZ_, NX_                      # along the cliff (t > 0: south-east of the lip)
PLATEAU = L["falls"]["plateauY"]
D4 = next(g for g in L["landmarks"] if g["id"] == "d4")
WHITE_MIX = [("calcite", 30), ("polished_diorite", 25), ("smooth_quartz", 15), ("quartz_bricks", 15),
             ("smooth_stone", 15)]


def cliff_point(t, s):
    """World point at distance t along the cliff (negative: north-west of the falls) and s off it (positive: up)."""
    return LIP[0] + TX_ * t + NX_ * s, LIP[1] + TZ_ * t + NZ_ * s


# ====================================================================================== the stair tower
def stair_tower():
    cxf, czf = cliff_point(-22, -7.5)
    cx, cz = int(round(cxf)), int(round(czf))
    P = Piece("falls_stair", "滝の大階段 (stairway tower up the cliff beside the falls)", 4, cx - 16, cz - 18, cx + 16,
              cz + 14, below=8, above=70, y0=60, y1=154, kind="scenery")
    half = 5                                    # 11 x 11 tower, walls 1 thick, 9 x 9 inside
    base = min(P.top(cx + a, cz + b) for a in (-half, 0, half) for b in (-half, 0, half))
    top = PLATEAU + 1                            # the walk-out level onto the plateau bridge
    # footing and shell
    for x in range(cx - half, cx + half + 1):
        for z in range(cz - half, cz + half + 1):
            t = P.top(x, z)
            P.fill(x, min(t, base) - 2, z, x, base, z, F("stone_bricks"))
            edge = abs(x - cx) == half or abs(z - cz) == half
            for y in range(base + 1, top + 8):
                if edge:
                    corner = abs(x - cx) == half and abs(z - cz) == half
                    st = F("quartz_bricks") if corner else (F("calcite") if (y // 4) % 2 else F("polished_diorite"))
                    P.set(x, y, z, st)
                else:
                    P.set(x, y, z, AIR)
    # the spiral: a two-wide ring of steps round a 3x3 core, one block up per step, twenty steps a turn
    core = 1
    y = base + 1
    k = 0
    ring = []
    for x in range(cx - half + 1, cx + half):
        for z in range(cz - half + 1, cz + half):
            if max(abs(x - cx), abs(z - cz)) <= core:
                continue
            ring.append((x, z, math.atan2(z - cz, x - cx)))
    turns = (top - base) / 20.0 + 0.2
    for x in range(cx - core, cx + core + 1):
        for z in range(cz - core, cz + core + 1):
            P.fill(x, base + 1, z, x, top + 4, z, F("smooth_stone") if (x + z) % 2 else F("polished_diorite"))
    for (x, z, ang) in ring:
        a = (ang + math.pi) / (2 * math.pi)                     # 0..1 round the ring
        tx, tz = -math.sin(ang), math.cos(ang)                  # direction of travel (increasing angle)
        facing = K.facing_of(tx, tz)
        for turn in range(int(math.ceil(turns)) + 1):
            h = base + 1 + int(math.floor((turn + a) * 20.0))
            if h > top:
                continue
            P.set(x, h, z, B.stairs("smooth_quartz", facing))
            P.set(x, h - 1, z, F("smooth_quartz") if h - 1 > base else F("stone_bricks"))
    # the floor at the top and the landing at the bottom
    for x in range(cx - half + 1, cx + half):
        for z in range(cz - half + 1, cz + half):
            if max(abs(x - cx), abs(z - cz)) > core:
                if P.get(x, base + 1, z) == "minecraft:air":
                    P.set(x, base, z, F("polished_diorite"))
    # light in the core, slit windows
    for yy in range(base + 4, top, 6):
        for (x, z) in ((cx - core - 0, cz), (cx + core, cz), (cx, cz - core), (cx, cz + core)):
            P.set(x, yy, z, F("sea_lantern"))
        for (x, z) in ((cx - half, cz), (cx + half, cz), (cx, cz - half), (cx, cz + half)):
            P.set(x, yy + 2, z, B.S("glass_pane"))
    # door at the foot (on the side away from the cliff) with steps
    dfx, dfz = K.DIRS[K.facing_of(-NX_, -NZ_)]
    dx_, dz_ = cx + dfx * half, cz + dfz * half
    lo, hi = B.door_states("dark_oak", K.facing_of(-NX_, -NZ_), "left")
    P.fill(dx_, base + 1, dz_, dx_, base + 3, dz_, AIR)
    P.set(dx_, base + 1, dz_, lo)
    P.set(dx_, base + 2, dz_, hi)
    P.set(dx_ + dfx, base, dz_ + dfz, F("polished_diorite"))
    # the walk-out at the top towards the plateau and the bridge across to it
    ofx, ofz = K.DIRS[K.facing_of(NX_, NZ_)]
    ex, ez = cx + ofx * half, cz + ofz * half
    sx, sz = K.DIRS[K.CW[K.facing_of(NX_, NZ_)]]
    for s in (-1, 0, 1):
        P.fill(ex + sx * s, top + 1, ez + sz * s, ex + sx * s, top + 3, ez + sz * s, AIR)
        P.set(ex + sx * s, top, ez + sz * s, F("smooth_quartz"))
    for i in range(1, 14):
        bx, bz = ex + ofx * i, ez + ofz * i
        if P.top(bx, bz) >= top:
            break
        for s in (-1, 0, 1):
            P.set(bx + sx * s, top, bz + sz * s, F("smooth_quartz") if s == 0 else F("calcite"))
            P.fill(bx + sx * s, top + 1, bz + sz * s, bx + sx * s, top + 3, bz + sz * s, AIR)
        for s in (-2, 2):
            P.set(bx + sx * s, top, bz + sz * s, F("calcite"))
            P.set(bx + sx * s, top + 1, bz + sz * s, B.wall("diorite"))
        if i % 4 == 0:
            P.fill(bx, P.top(bx, bz) + 1, bz, bx, top - 1, bz, F("polished_diorite"))
    # the roof: a lookout under a blue pyramid
    SH.crenellate(P, cx - half, cz - half, cx + half, cz + half, top + 8, F("calcite"))
    P.fill(cx - half, top + 8, cz - half, cx + half, top + 8, cz + half, F("smooth_quartz"))
    P.fill(cx - half + 1, top + 8, cz - half + 1, cx + half - 1, top + 8, cz + half - 1, AIR)
    for (a, b) in ((-half, -half), (half, -half), (-half, half), (half, half)):
        P.fill(cx + a, top + 8, cz + b, cx + a, top + 11, cz + b, F("quartz_bricks"))
    SH.hip_roof(P, cx - half, cz - half, cx + half, cz + half, top + 12, "royal_roof_tiles", overhang=1, pitch=1)
    P.set(cx, top + 18, cz, B.S("gilded_finial"))
    K.place_sign(P, dx_ + dfx, base + 2, dz_ + dfz + 0, ['{"translate":"sign.eternalrealm.falls_stair","bold":true}',
                                                         K.field_level(4)], wood="dark_oak",
                 rotation=K.rotation_of(-NX_, -NZ_))
    P.probe(cx - half, base + 2, cz - half)
    P.view("falls_stair", (cx - 50, base + 40, cz + 46), (cx, base + 28, cz), fov=58)
    return P


# ====================================================================================== the prayer platform
def prayer_platform():
    cxf, czf = cliff_point(-17, 8)
    cx, cz = int(round(cxf)), int(round(czf))
    P = Piece("falls_prayer", "滝口の祈りの壇 (prayer platform at the lip of the falls)", 4, cx - 16, cz - 16, cx + 16,
              cz + 16, below=10, above=24, kind="scenery")
    y = PLATEAU + 1
    m = K.disc(P, cx, cz, 8, "octagon")
    # the terrace reaches out over the cliff edge on corbels
    for (x, z) in K.cells(m, P):
        t = P.top(x, z)
        if t < y:
            P.fill(x, max(t - 1, y - 4), z, x, y - 1, z, F("quartz_bricks"))
            if t < y - 4:
                P.set(x, y - 4, z, B.stairs("quartz", K.facing_of(cx - x, cz - z), "top"))
        else:
            P.fill(x, y + 1, z, x, t + 2, z, AIR)
        K.mixed(P, x, y, z, WHITE_MIX, 41)
    edge = K.ring(m)
    for (x, z) in K.cells(edge, P):
        P.set(x, y, z, F("quartz_bricks"))
        s = (x - LIP[0]) * NX_ + (z - LIP[1]) * NZ_
        if s < 4:                                   # the balustrade on the falls side
            P.set(x, y + 1, z, B.wall("diorite") if (x + z) % 3 else F("quartz_bricks"))
    K.blend(P, m, y, width=4)
    # the gate (a white torii) facing the falls, an altar with candles, stone lanterns, cherries
    gx, gz = int(round(cx - NX_ * 4)), int(round(cz - NZ_ * 4))
    ax_ = "x" if abs(TX_) > abs(TZ_) else "z"
    for side in (-2, 2):
        px = gx + (side if ax_ == "x" else 0)
        pz = gz + (side if ax_ == "z" else 0)
        P.fill(px, y + 1, pz, px, y + 5, pz, B.S("quartz_pillar", axis="y"))
    for k in range(-3, 4):
        px = gx + (k if ax_ == "x" else 0)
        pz = gz + (k if ax_ == "z" else 0)
        P.set(px, y + 6, pz, F("smooth_quartz") if abs(k) < 3 else B.slab("smooth_quartz", "bottom"))
        if abs(k) <= 2:
            P.set(px, y + 5, pz, F("gilded_trim") if k == 0 else AIR if abs(k) == 1 else P.get(px, y + 5, pz))
    P.set(gx, y + 4, gz, B.lantern(hanging=True))
    P.set(cx, y + 1, cz, F("chiseled_quartz_block"))
    P.set(cx, y + 2, cz, B.candle(4, "white"))
    for (a, b) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        P.set(cx + a, y + 1, cz + b, B.slab("smooth_quartz", "bottom"))
    for (a, b) in ((5, 3), (-5, 3), (5, -3), (-5, -3)):
        K.stone_lantern(P, cx + a, y, cz + b, stone="diorite", cap=B.slab("smooth_quartz", "bottom"))
    for (a, b) in ((10, 5), (-9, 9), (8, -10)):
        x, z = cx + a, cz + b
        if P.top(x, z) >= PLATEAU - 1 and not P.wet(x, z):
            K.tree(P, x, K.ground_y(P, x, z), z, kind="cherry", height=5, crown=3.0, seed=a)
    for (x, z) in K.cells(K.disc(P, cx, cz, 15) & ~K.dilate(m, 1), P):
        if hash2(x, z, 5) < 0.18 and not P.wet(x, z) and P.top(x, z) >= PLATEAU - 1:
            P.set(x, K.ground_y(P, x, z) + 1, z, S("pink_petals", facing="north", flower_amount=2))
    P.probe(cx, y + 1, cz)
    P.view("falls_prayer", (cx - 30, y + 18, cz + 26), (cx, y + 3, cz), fov=58)
    return P


# ====================================================================================== arches, path, bridge
PATH = [(532.0, -296.0), (544.0, -314.0), (554.0, -332.0), (562.0, -349.0), (568.0, -364.0)]


def riverside():
    """The riverside path from the waystone to the observation bridge under white arches, the bridge itself and the
    path on the far bank towards the barred gate in the cliff."""
    P = Piece("falls_riverside", "滝見の参道と観瀑橋 (riverside arches and the observation bridge)", 4, 520, -392, 632,
              -284, below=10, above=22, kind="scenery")
    gate_keep = K.disc(P, D4["x"], D4["z"], 22)
    # the path: white gravel and flagstones following the bank
    pm = K.polyline_mask(P, PATH, 1.6)
    for (x, z) in K.cells(pm & ~gate_keep, P):
        if P.wet(x, z):
            continue
        t = P.top(x, z)
        P.set(x, t, z, F("calcite") if hash2(x, z, 3) < 0.35 else F("polished_diorite") if hash2(x, z, 4) < 0.5
              else S("dirt_path"))
    # arches over the path
    for k in range(1, len(PATH) - 1):
        (ax, az), (bx, bz) = PATH[k - 1], PATH[k + 1]
        ux, uz = bx - ax, bz - az
        n = math.hypot(ux, uz)
        ux, uz = ux / n, uz / n
        cx, cz = PATH[k]
        y = max(P.top(int(round(cx + -uz * s)), int(round(cz + ux * s))) for s in (-3, 0, 3))
        across = "x" if abs(ux) < abs(uz) else "z"
        for s in (-3, 3):
            px = int(round(cx)) + (s if across == "x" else 0)
            pz = int(round(cz)) + (s if across == "z" else 0)
            P.fill(px, P.top(px, pz) - 1, pz, px, y, pz, F("calcite"))
            P.fill(px, y + 1, pz, px, y + 5, pz, B.S("quartz_pillar", axis="y"))
            P.set(px, y + 6, pz, F("chiseled_quartz_block"))
        for s in range(-4, 5):
            px = int(round(cx)) + (s if across == "x" else 0)
            pz = int(round(cz)) + (s if across == "z" else 0)
            P.set(px, y + 7, pz, F("smooth_quartz") if abs(s) < 4 else B.slab("smooth_quartz", "bottom"))
            if abs(s) <= 2:
                P.set(px, y + 6, pz, B.stairs("quartz", ("east" if s < 0 else "west") if across == "x" else
                                              ("south" if s < 0 else "north"), "top") if abs(s) == 2 else AIR)
        P.set(int(round(cx)), y + 6, int(round(cz)), B.lantern(hanging=True))
    # the observation bridge across the river below the plunge pool
    a, b = (563.0, -361.0), (588.0, -347.0)       # downstream of the cave gate's approach
    cells, (ux, uz), Lb = BK.bridge_cells(a, b, 2.4)
    deck = 67
    for (x, z), (t, s) in cells.items():
        if not (P.X0 <= x <= P.X1 and P.Z0 <= z <= P.Z1):
            continue
        tt = t * Lb
        c = Lb / 2
        us = 63 + 3.0 * math.sqrt(max(0.0, 1 - ((tt - c) / 7.0) ** 2))
        ub = int(math.floor(us))
        tg = P.top(x, z)
        if P.wet(x, z):
            P.fill(x, ub, z, x, deck - 1, z, F("calcite"))
            P.fill(x, 64, z, x, ub - 1, z, AIR)
        else:
            P.fill(x, min(tg, 62), z, x, deck - 1, z, F("calcite"))
        edgec = abs(s) > 1.9
        P.set(x, deck, z, F("quartz_bricks") if edgec else F("polished_diorite"))
        P.fill(x, deck + 1, z, x, deck + 4, z, AIR)
        if edgec:
            P.set(x, deck + 1, z, B.wall("diorite"))
    # the balcony in the middle, facing the falls (upstream, north-east)
    mx, mz = int(round((a[0] + b[0]) / 2)), int(round((a[1] + b[1]) / 2))
    for x in range(mx - 2, mx + 3):
        for z in range(mz - 5, mz - 1):
            P.set(x, deck, z, F("polished_diorite"))
            P.fill(x, deck + 1, z, x, deck + 3, z, AIR)
            if P.wet(x, z):
                P.set(x, deck - 1, z, F("calcite"))
            else:
                P.fill(x, 64, z, x, deck - 1, z, F("calcite"))
            if z == mz - 5 or abs(x - mx) == 2:
                P.set(x, deck + 1, z, B.wall("diorite"))
    K.bench(P, mx - 1, deck, mz - 3, "north", length=3, wood="cherry", axis_dir="east")
    for (x, z) in (a, b):
        xi, zi = int(round(x)), int(round(z))
        K.stone_lantern(P, xi, P.top(xi, zi), zi, stone="diorite", cap=B.slab("smooth_quartz", "bottom"))
    # the path on the east bank towards the gate in the cliff (stops short of its keep-out)
    east = [(590.0, -348.0), (608.0, -354.0), (626.0, -361.0)]
    for (x, z) in K.cells(K.polyline_mask(P, east, 1.5) & ~gate_keep, P):
        if not P.wet(x, z):
            P.set(x, P.top(x, z), z, F("polished_diorite") if hash2(x, z, 6) < 0.5 else S("dirt_path"))
    K.place_sign(P, 596, P.top(596, -350) + 1, -350, ['{"translate":"landmark.eternalrealm.d4","bold":true}',
                                                      '{"text":"→"}'], wood="cherry", rotation=12)
    P.probe(int(round(a[0])) + 2, deck, int(round(a[1])) + 1)
    P.view("falls_riverside", (600, 100, -300), (575, 68, -345), fov=60)
    return P


def build(only=None):
    out = []
    for pid, fn in (("falls_stair", stair_tower), ("falls_prayer", prayer_platform), ("falls_riverside", riverside)):
        if not only or pid in only:
            out.append(fn())
    return out
