"""闘技場 The arena outside the west gate: an oval amphitheatre of white stone with two tiers of arches,
tiered seating, a sand floor, gates east and west, the champions' box with banners, pennants all round and the
arena master Zain at the east gate (``npc.arena_master``)."""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "arena"
AIR = K.AIR
F = K.F
CX, CZ = -136, -44
A, BZ = 21.0, 17.0           # outer half-axes
WALL_H = 14

VIEWS = {
    "arena_in": dict(pos=(-121.5, 8, -44.5), look=(-150, 2, -44), fov=80, sun=(40, -150), fog=2600,
                     label="Inside the arena"),
}


def ell(x, z):
    return math.sqrt(((x - CX) / A) ** 2 + ((z - CZ) / BZ) ** 2)


def build(w, district, terrain):
    TK.pave(w, -160, -80, -113, -7, TK.LANE_MIX, seed=1401)
    R = int(A) + 2
    for x in range(CX - R, CX + R + 1):
        for z in range(CZ - int(BZ) - 2, CZ + int(BZ) + 3):
            e = ell(x, z)
            if e > 1.0:
                continue
            w.fill(x, 1, z, x, WALL_H + 3, z, AIR)
            # radial distance in "rows" from the outer wall inwards
            k = (1.0 - e) * min(A, BZ)
            if k < 1.2:                                       # outer wall with arches
                for y in range(0, WALL_H + 1):
                    w.set(x, y, z, F("castle_bricks") if hash2(x, y * 7 + z, 3) < 0.85 else F("smooth_castle_stone"))
            elif k < 7.0:                                     # tiered seating rising outwards
                row = int(k - 1.2)
                top = max(1, WALL_H - 2 - row * 1)
                w.fill(x, 0, z, x, top - 1, z, F("stone_bricks"))
                w.set(x, top, z, B.slab("smooth_castle_stone", "bottom") if row % 2 else F("polished_andesite"))
            elif k < 7.8:                                     # barrier wall around the floor
                w.fill(x, 0, z, x, 3, z, F("smooth_castle_stone"))
                w.set(x, 4, z, B.wall("castle_bricks"))
            else:                                             # sand floor
                w.set(x, 0, z, B.S("sand") if hash2(x, z, 9) < 0.8 else B.S("coarse_dirt"))
    # arches on the outer wall (two tiers) every ~8 degrees
    for k in range(40):
        a = 2 * math.pi * k / 40
        x = CX + int(round(A * math.cos(a)))
        z = CZ + int(round(BZ * math.sin(a)))
        for y in (2, 3, 4, 8, 9):
            w.set(x, y, z, AIR if k % 2 == 0 else F("smooth_castle_stone"))
        w.set(x, WALL_H + 1, z, B.wall("castle_bricks") if k % 2 else F("castle_bricks"))
        if k % 4 == 0:
            TK.flagpole(w, x, WALL_H + 1, z, 3, "east", "pennant", 4.0, 1.0, pole="fence")
    # cornice at the top
    for x in range(CX - R, CX + R + 1):
        for z in range(CZ - int(BZ) - 2, CZ + int(BZ) + 3):
            e = ell(x, z)
            if 0.94 < e <= 1.0:
                w.set(x, WALL_H, z, F("smooth_castle_stone"))
    # gates east (towards the town) and west: tunnels through the stands to the floor
    for (sx, name) in ((1, "east"), (-1, "west")):
        for d in range(0, 12):
            x = CX + sx * (int(A) - d)
            for z in range(CZ - 2, CZ + 3):
                w.fill(x, 1, z, x, 5, z, AIR)
                w.set(x, 0, z, B.S("cobbled_paving"))
            w.set(x, 6, CZ - 3, F("stone_bricks")) if False else None
        x = CX + sx * int(A)
        for z in (CZ - 3, CZ + 3):
            w.fill(x, 1, z, x, 7, z, F("smooth_castle_stone"))
            deco.kingdom_wall_banner(w, x + sx, 6, z, facing="east" if sx > 0 else "west", color="red",
                                     nbt=B.banner_nbt([("cr", "yellow"), ("bo", "yellow")]))
        for z in range(CZ - 2, CZ + 3):
            w.set(x, 6, z, B.S("iron_bars"))
        w.set(x, 7, CZ, B.S("castle_crest_stone"))
    # champions' box on the south side with a canopy and banners
    bz = CZ + int(BZ) - 6
    for x in range(CX - 4, CX + 5):
        w.fill(x, 1, bz, x, 7, bz + 3, AIR)
        w.fill(x, 0, bz, x, 6, bz + 3, F("stone_bricks"))
        w.set(x, 7, bz, B.slab("smooth_castle_stone", "bottom"))
        w.set(x, 7, bz + 1, B.stairs("dark_oak", "south"))
        w.set(x, 11, bz + 1, B.wool("red" if x % 2 else "yellow"))
        w.set(x, 11, bz, B.wool("red" if x % 2 else "yellow"))
    for x in (CX - 4, CX + 4):
        w.fill(x, 8, bz, x, 10, bz, B.fence("dark_oak"))
    w.set(CX, 8, bz + 1, B.stairs("dark_oak", "south"))
    w.set(CX, 9, bz + 2, B.S("gilded_finial")) if False else None
    deco.kingdom_wall_banner(w, CX, 6, bz - 1, facing="north")
    # weapon racks and training dummies on the floor's edge
    for (x, z) in ((CX - 5, CZ - 3), (CX + 5, CZ + 3)):
        deco.armor_stand(w, x, 0, z, yaw=0.0, armor="leather", weapon=None)
    # Zain at the east gate, the town-side forecourt with a notice board
    w.spot("npc.arena_master", (CX + int(A) - 2, 1, CZ + 2), -90.0)
    deco.sign_text(w, CX + int(A) + 2, 2, CZ - 4, B.S("spruce_sign", rotation=4),
                   ["闘技場", "Arena", "受付: Zain", ""], color="black")
    w.set(CX + int(A) + 2, 1, CZ - 4, B.fence("spruce"))
    for z in (CZ - 7, CZ + 7):
        deco.lamp_post(w, CX + int(A) + 2, 0, z, height=4)
    TK.crowd(w, "street", [(CX + int(A) + 6, 1, CZ), (CX, 1, CZ - 4)], start=20, need=14)
