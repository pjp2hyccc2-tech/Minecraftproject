"""Farm kit: crop fields with irrigation, hedges, haystacks, scarecrows, barns and tower windmills."""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2
from capital.world import X0, Z0

from . import _civic_kit as K
from . import _town_kit as TK

AIR = K.AIR
F = K.F

CROPS = ("wheat", "wheat", "carrots", "potatoes", "beetroots", "wheat", "flowers", "pumpkin")


def free_ground(w, T, x, z):
    i, j = x - X0, z - Z0
    if not w.footprint[i, j] or T.road[i, j] or T.water[i, j] != -999:
        return False
    return T.H[i, j] == 0


def field(w, T, x0, z0, x1, z1, crop, seed=0):
    """A crop field on the ground at y = 0 with a water channel every 5 rows (along z)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not free_ground(w, T, x, z) or not w.get(x, 0, z).startswith("minecraft:grass_block"):
                continue
            w.fill(x, 1, z, x, 3, z, AIR)
            if (z - z0) % 5 == 2 and crop != "flowers":
                w.set(x, 0, z, B.S("water"))
                continue
            if crop == "flowers":
                w.set(x, 1, z, B.S(("sunflower", "lilac", "peony", "rose_bush")[(x // 3 + z // 4) % 4], half="lower"))
                w.set(x, 2, z, B.S(("sunflower", "lilac", "peony", "rose_bush")[(x // 3 + z // 4) % 4], half="upper"))
                continue
            if crop == "pumpkin":
                w.set(x, 0, z, B.S("farmland", moisture=7))
                if hash2(x, z, seed) < 0.3:
                    w.set(x, 1, z, B.S("pumpkin") if hash2(z, x, seed) < 0.7 else B.S("melon"))
                continue
            w.set(x, 0, z, B.S("farmland", moisture=7))
            age = 7 if crop != "beetroots" else 3
            if hash2(x, z, seed + 5) < 0.12 and crop == "wheat":
                age = 5
            w.set(x, 1, z, B.S(crop, age=age))


def hedge(w, T, x0, z0, x1, z1, seed=0, gaps=()):
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z1, x1, z1), (x0, z0, x0, z1), (x1, z0, x1, z1)):
        n = max(abs(c - a), abs(d - b))
        for i in range(n + 1):
            x = a + (0 if c == a else i)
            z = b + (0 if d == b else i)
            if any(abs(x - gx) <= 1 and abs(z - gz) <= 1 for gx, gz in gaps):
                continue
            if free_ground(w, T, x, z):
                w.set(x, 1, z, B.leaves("oak") if hash2(x, z, seed) < 0.75 else B.fence("spruce"))


def scarecrow(w, x, z):
    w.set(x, 1, z, B.fence("spruce"))
    w.set(x, 2, z, B.S("hay_block", axis="y"))
    w.set(x, 3, z, B.S("carved_pumpkin", facing="south"))
    w.set(x - 1, 2, z, B.fence("spruce"))
    w.set(x + 1, 2, z, B.fence("spruce"))


def haystack(w, x, z):
    w.fill(x, 1, z, x + 1, 1, z + 1, B.S("hay_block", axis="y"))
    w.set(x, 2, z, B.S("hay_block", axis="y"))


def windmill(w, cx, cz, facing="south", radius=7.0, seed=0):
    """Tower mill: tapering stone tower, timber cap with the sail hub on its ``facing`` side, gallery, door."""
    vx, vz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[facing]
    for y in range(0, 15):
        r = 4.3 - y * 0.09
        S.cylinder(w, cx, cz, r, y, y, B.S("stone_bricks") if y < 2 else (F("white_plaster") if y > 3 else
                                                                         B.S("cobblestone")), hollow=y > 0)
        if y > 0:
            S.cylinder(w, cx, cz, r - 1.0, y, y, AIR)
    S.cylinder(w, cx, cz, 3.0, 0, 0, F("spruce_planks"))
    # timbering on the plaster
    for k in range(8):
        a = math.radians(k * 45)
        for y in range(4, 15):
            r = 4.3 - y * 0.09
            x, z = cx + int(round(r * math.cos(a))), cz + int(round(r * math.sin(a)))
            w.set(x, y, z, B.log("dark_oak", "y"))
    # gallery ring at y 9 (stage), windows
    S.cylinder(w, cx, cz, 4.6, 9, 9, B.slab("spruce", "top"), hollow=True)
    for k in range(4):
        a = math.radians(k * 90 + 45)
        x, z = cx + int(round(3.9 * math.cos(a))), cz + int(round(3.9 * math.sin(a)))
        w.set(x, 6, z, TK.LEADED)
        w.set(x, 12, z, TK.LEADED)
    # cap: gabled timber box projecting towards ``facing`` (the hub sits in its front wall)
    cap_y = 15
    hub_y = cap_y + 2
    f = TK.frame_for(w, (cx - 3, cz - 3, cx + 3, cz + 3), facing)[0]
    # local frame: front (towards ``facing``) is local z = 0; the cap is 7 x 7, front projects to z = -1
    f.fill(0, 0 + cap_y, -1, 6, 3 + cap_y, 6, F("spruce_planks"))
    f.fill(1, 1 + cap_y, 0, 5, 3 + cap_y, 5, AIR)
    S.gable_roof(f, 0, -1, 6, 6, cap_y + 4, "dark_oak", axis="z", overhang=1, gable_state=F("spruce_planks"),
                 end_overhang=0)
    # hub bearing in the front wall (local (3, hub, -1)) facing out
    f.set(3, hub_y, -1, B.S("windmill_sails", facing="north"))
    f.be(3, hub_y, -1, f"{{Radius:{float(radius):.1f}f}}")
    # door at the foot (opposite the sails) and a ladder up the inside
    bx, bz = cx - vx * 4, cz - vz * 4
    w.fill(bx, 1, bz, bx, 2, bz, AIR)
    w.set(bx - vx, 0, bz - vz, B.stairs("cobblestone", facing))
    for y in range(1, cap_y + 1):
        w.set(cx + vx * 2, y, cz + vz * 2, B.S("ladder", facing=facing if False else {"south": "north",
              "north": "south", "east": "west", "west": "east"}[facing]))
    # millstones and sacks
    w.set(cx, 1, cz, B.S("grindstone", face="floor", facing="north"))
    w.set(cx - 1, 1, cz + 1, B.barrel("up"))
    w.set(cx + 1, 1, cz - 1, B.S("hay_block", axis="y"))
    TK.flagpole(w, cx, cap_y + 9, cz, 2, "east", "pennant", 3.0, 0.8, pole="fence")


def farmhouse(w, rect, front, seed, roof="terracotta_roof_tiles"):
    f, Wd, Dd = TK.frame_for(w, rect, front)
    sp = TK.HouseSpec.random(seed, Wd, Dd, floors=2)
    sp.roof = roof
    sp.jetty = False
    sp.dormers = False
    sp.ground_plaster = True
    TK.house(f, Wd, Dd, sp, nb_left=False, nb_right=False)


def barn(w, rect, front, seed=0):
    f, W, D = TK.frame_for(w, rect, front)
    f.fill(0, 0, 0, W - 1, 0, D - 1, B.S("coarse_dirt"))
    S.walls(f, 0, 0, W - 1, D - 1, 1, 6, F("spruce_planks"))
    for x in range(0, W, 3):
        f.fill(x, 1, 0, x, 6, 0, B.log("dark_oak"))
        f.fill(x, 1, D - 1, x, 6, D - 1, B.log("dark_oak"))
    f.fill(0, 1, 0, 0, 6, D - 1, B.log("dark_oak"))
    f.fill(W - 1, 1, 0, W - 1, 6, D - 1, B.log("dark_oak"))
    f.fill(1, 1, 1, W - 2, 6, D - 2, AIR)
    S.gable_roof(f, 0, 0, W - 1, D - 1, 7, "dark_oak", axis="z", overhang=1, gable_state=F("spruce_planks"),
                 end_overhang=1)
    mx = (W - 1) // 2
    f.fill(mx - 1, 1, 0, mx + 1, 4, 0, AIR)
    f.set(mx - 2, 1, -1, B.S("spruce_fence_gate", facing="north", open=True))
    f.set(mx + 2, 1, -1, B.S("spruce_fence_gate", facing="north", open=True))
    for x in range(1, W - 1):
        for z in (D - 2, D - 3):
            if hash2(x, z, seed) < 0.6:
                f.set(x, 1, z, B.S("hay_block", axis="y"))
                if hash2(z, x, seed) < 0.4:
                    f.set(x, 2, z, B.S("hay_block", axis="y"))
    f.set(1, 1, 1, B.S("composter", level=6))
    f.set(W - 2, 1, 1, B.barrel("up"))
    deco.hanging_lantern(f, mx, 7, D // 2, drop=1)
