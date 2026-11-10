"""港 The harbour outside the south-east walls on the lake: a stone quay built out into the water, warehouses
with hoist beams, a fish market on the little square below the postern, timber piers, treadwheel cranes, a
breakwater with the lighthouse, two moored sailing ships and rowing boats, harbour flags along the quay."""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import layout as L
from capital import shapes as S
from capital.noise import hash2
from capital.world import X0, Z0

from . import _civic_kit as K
from . import _ships as SH
from . import _town_kit as TK

DISTRICT = "harbour"
AIR = K.AIR
F = K.F
QZ = 100                      # quay edge
WATER = L.WATER_Y

VIEWS = {
    "harbour_quay": dict(pos=(40, 4, 96), look=(80, 8, 112), fov=74, sun=(40, -150), fog=2600,
                         label="Harbour quay, cranes and ships"),
    "lighthouse": dict(pos=(92, 10, 140), look=(112, 18, 120), fov=66, sun=(40, -150), fog=2600,
                       label="The lighthouse on the breakwater"),
}


def quay(w, T):
    x0, x1 = 26, 116
    for x in range(x0, x1 + 1):
        for z in range(87, QZ + 1):
            bed = T.H[x - X0, z - Z0]
            w.fill(x, min(bed, -1), z, x, -1, z, B.S("stone"))
            w.fill(x, 1, z, x, 4, z, AIR)
            if z == QZ:
                for y in range(min(bed, -1), 1):
                    w.set(x, y, z, F("stone_bricks") if hash2(x, y, 3) < 0.7 else F("mossy_stone_bricks"))
    TK.pave(w, x0, 87, x1, QZ, TK.QUAY_MIX, seed=901, only_ground=False)
    w.fill(x0, 0, QZ, x1, 0, QZ, F("smooth_stone"))
    for x in range(x0 + 2, x1, 6):                            # bollards
        w.set(x, 1, QZ, B.wall("stone_bricks"))
        w.set(x, 2, QZ, B.slab("stone_bricks", "bottom")) if False else None
    # steps down to the water at the square
    for i, z in enumerate(range(QZ - 2, QZ + 1)):
        pass
    # the quay face towards the gate forecourt (x = 25 side)
    for z in range(87, QZ + 1):
        w.set(x0 - 1, 1, z, B.wall("stone_bricks")) if False else None


def warehouse(w, rect, seed):
    f, W, D = TK.frame_for(w, rect, "south")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="terracotta_roof_tiles" if seed % 2 else "royal_roof_tiles",
                     roof_kind="gable_z", portal_w=3, portal_h=4, trim=None, wall="stone_bricks",
                     pilaster="polished_andesite", plinth="cobblestone", window_every=4, dormers=False,
                     banner_nbt=None, sign=None, floor="spruce_planks")
    a = TK.civic_hall(f, W, D, sp)
    ridge = a["ridge"]
    # hoist beam and a hanging crate at the gable
    hx = (W - 1) // 2
    f.set(hx, ridge - 3, -1, B.log("spruce", "z"))
    f.set(hx, ridge - 3, -2, B.log("spruce", "z"))
    f.fill(hx, ridge - 5, -2, hx, ridge - 4, -2, B.chain("y"))
    f.set(hx, ridge - 6, -2, B.barrel("up"))
    f.set(hx, sp.sh + 1, 0, AIR)
    f.set(hx, sp.sh + 2, 0, AIR)
    # goods inside and stacked outside
    for x in range(1, W - 1):
        for z in range(D - 3, D - 1):
            if hash2(x, z, seed) < 0.7:
                f.set(x, 1, z, B.barrel("up") if hash2(z, x, seed) < 0.5 else B.S("hay_block", axis="y"))
                if hash2(x, z, seed + 1) < 0.4:
                    f.set(x, 2, z, B.barrel("up"))
    for x in (1, W - 2):
        f.set(x, 1, -1, B.barrel("up"))
        f.set(x, 1, -2, B.S("chest", facing="south")) if hash2(x, seed, 2) < 0.5 else None


def pier(w, T, x0, x1, z0, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, 0, z, B.slab("spruce", "top") if (x + z) % 5 else F("spruce_planks"))
            w.fill(x, 1, z, x, 3, z, AIR)
    for z in range(z0, z1 + 1, 4):
        for x in (x0, x1):
            bed = T.H[x - X0, z - Z0]
            w.fill(x, bed + 1, z, x, -1, z, B.log("spruce", "y"))
            w.set(x, 1, z, B.fence("spruce"))
            if (z - z0) % 8 == 0:
                w.set(x, 2, z, B.lantern())
    for x in range(x0, x1 + 1):
        bed = T.H[x - X0, z1 - Z0]
        w.fill(x, bed + 1, z1, x, -1, z1, B.log("spruce", "y")) if x in (x0, x1) else None


def crane(w, x, z, jib=5, height=11):
    """Timber quay crane: a post with braces, a jib over the water, a rope with a crate; a treadwheel."""
    w.fill(x, 1, z, x, height, z, B.log("spruce", "y"))
    for (dx, dz) in ((1, 0), (-1, 0), (0, -1)):
        w.set(x + dx, 1, z + dz, B.stairs("spruce", {(1, 0): "west", (-1, 0): "east", (0, -1): "south"}[(dx, dz)]))
    for i in range(1, jib + 1):
        w.set(x, height, z + i, B.log("spruce", "z"))
    for i in range(1, 4):
        w.set(x, height - i, z + i, B.fence("spruce"))
    w.fill(x, height - 4, z + jib, x, height - 1, z + jib, B.chain("y"))
    w.set(x, height - 5, z + jib, B.barrel("up"))
    # treadwheel beside the post (a ring of planks)
    for k in range(12):
        a = math.radians(k * 30)
        yy = 4 + int(round(2.6 * math.sin(a)))
        zz = z - 2 + int(round(2.6 * math.cos(a)))
        w.set(x - 1, yy, zz, F("spruce_planks"))
    w.set(x - 1, 4, z - 2, B.log("spruce", "x"))


def lighthouse(w, T, cx, cz):
    # breakwater from the quay
    for z in range(QZ + 1, cz + 5):
        for x in range(cx - 2, cx + 3):
            bed = T.H[x - X0, z - Z0]
            w.fill(x, bed + 1, z, x, 0, z, F("stone_bricks") if abs(x - cx) == 2 else B.S("stone"))
            w.set(x, 0, z, B.S("cobbled_paving") if abs(x - cx) < 2 else F("smooth_stone"))
            w.fill(x, 1, z, x, 3, z, AIR)
        if z % 6 == 0:
            K.lamp(w, cx - 2, 0, z, height=3)
    S.cylinder(w, cx, cz, 5.0, -8, 0, F("stone_bricks"))
    S.cylinder(w, cx, cz, 5.0, 0, 0, B.S("cobbled_paving"))
    top = 26
    for y in range(1, top + 1):
        band = (y // 4) % 2 == 1
        S.cylinder(w, cx, cz, 3.2, y, y, B.S("red_terracotta") if band else F("castle_bricks"), hollow=True)
    S.cylinder(w, cx, cz, 2.2, 1, top, AIR)
    for y in range(1, top):                                   # ladder inside
        w.set(cx, y, cz - 2, B.S("ladder", facing="south"))
    w.set(cx, 1, cz + 3, AIR)
    w.set(cx, 2, cz + 3, AIR)
    lo, hi = B.door_states("spruce", "north", "left")
    w.set(cx, 1, cz + 3, lo)
    w.set(cx, 2, cz + 3, hi)
    for y in (8, 14, 20):
        w.set(cx + 3, y, cz, B.pane())
        w.set(cx - 3, y, cz, B.pane())
    S.corbel_ring(w, cx, cz, 4.0, top, "stone_bricks")
    S.cylinder(w, cx, cz, 4.2, top + 1, top + 1, F("smooth_castle_stone"))
    S.crenellate_ring(w, cx, cz, 4.2, top + 2, B.S("iron_bars"), period=1)
    S.cylinder(w, cx, cz, 2.2, top + 2, top + 4, B.S("glass"), hollow=True)
    w.set(cx, top + 2, cz, B.S("sea_lantern"))
    w.set(cx, top + 3, cz, B.S("glowstone"))
    S.cylinder(w, cx, cz, 2.6, top + 5, top + 5, F("waxed_oxidized_cut_copper"))
    S.cone(w, cx, cz, 2.6, top + 6, 4, "waxed_oxidized_cut_copper", finial=False)
    TK.flagpole(w, cx, top + 9, cz, 3, "east", "harbour", 3.0, 2.0, pole="fence")


def fish_market(w):
    # little square below the postern (x 70..79): fish stalls, a crane, nets
    TK.pave(w, 70, 87, 80, QZ, TK.SQUARE_MIX, seed=903, only_ground=False)
    for (x0, front) in ((70, "east"), (78, "west")):
        rect = (x0, 89, x0 + 3, 93) if front == "east" else (x0 - 1, 89, x0 + 2, 93)
        f, Wd, Dd = TK.frame_for(w, (x0 if front == "east" else 77, 89, x0 + 2 if front == "east" else 80, 93),
                                 front)
        TK.market_stall(f, 0, 1, W=Wd, D=Dd - 1, colors=("light_blue", "white"), goods="fish", seed=x0)
    crane(w, 75, QZ - 1, jib=4, height=10)


def build(w, district, terrain):
    T = terrain
    quay(w, T)
    # warehouses on the quay (fronts face the water)
    for i, (xa, xb) in enumerate(((27, 40), (43, 55), (58, 68), (83, 95), (98, 110))):
        warehouse(w, (xa, 88, xb, 94), seed=i)
    fish_market(w)
    # piers
    pier(w, T, 44, 47, QZ + 1, 126)
    pier(w, T, 86, 89, QZ + 1, 124)
    pier(w, T, 64, 67, QZ + 1, 114)
    # ships moored alongside the piers
    SH.ship(w, 54, 102, length=30, beam=9, masts=3, seed=1, flags=("harbour", "kingdom", "pennant"))
    SH.ship(w, 96, 103, length=24, beam=7, masts=2, seed=2, hull="spruce", upper="oak", sail="white",
            flags=("harbour", "pennant"))
    for (x, z) in ((62, 118), (70, 104), (40, 104), (79, 110)):
        SH.rowboat(w, x, z, length=5)
    # cranes on the quay edge
    for x in (36, 60, 82):
        crane(w, x, QZ - 1, jib=5, height=11)
    lighthouse(w, T, 112, 128)
    # harbour flags along the quay, ropes and crates
    for x in (30, 52, 92, 106):
        TK.flagpole(w, x, 0, QZ - 2, 8, "east", "harbour", 3.0, 2.0, pole="fence")
    for (x, z) in ((41, 97), (57, 98), (85, 97), (99, 98)):
        w.set(x, 1, z, B.barrel("up"))
        w.set(x + 1, 1, z, B.S("hay_block", axis="y"))
    # ramp from the postern down to the quay
    for z in range(87, 89):
        w.fill(73, 0, z, 76, 0, z, B.S("cobbled_paving"))
    TK.crowd(w, "harbour", [(34, 1, 97), (50, 1, 97), (66, 1, 98), (75, 1, 95), (90, 1, 97), (104, 1, 97),
                            (45, 1, 112), (88, 1, 110)], need=12)
    # bridge-end guards on the far shore (outside the walls, on the road)
    for (x, n) in ((-3, 1), (3, 2)):
        for z in range(170, 150, -1):
            if not w.is_empty(x, 0, z) and w.is_empty(x, 1, z) and w.is_empty(x, 2, z):
                w.spot(f"guard.bridge.{n}", (x, 1, z), 180.0)
                break
