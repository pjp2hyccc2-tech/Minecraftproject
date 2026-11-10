"""東の川辺 East riverside: fields east of the river and the watermill on its bank with a mill race."""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2
from capital.world import X0, Z0

from . import _civic_kit as K
from . import _farm as FM
from . import _town_kit as TK

DISTRICT = "east_riverside"
AIR = K.AIR
F = K.F

VIEWS = {}

MILL_Z = 40
WHEEL_R = 4.0


def watermill(w, T):
    zc = MILL_Z
    # the river's east edge at this z
    xs = [x for x in range(113, 150) if T.water[x - X0, zc - Z0] != -999]
    xe = max(xs) if xs else 130
    wall_x = xe + 4                       # west wall of the mill house (the axle bearing sits in it)
    # mill race: water channel from the river to the wall, deep enough for the lower half of the wheel
    for x in range(xe - 1, wall_x):
        for z in range(zc - 5, zc + 6):
            edge = abs(z - zc) == 5
            w.fill(x, -7, z, x, -7, z, F("stone_bricks"))
            if edge:
                w.fill(x, -6, z, x, 0, z, F("stone_bricks"))
                w.set(x, 1, z, B.wall("stone_bricks"))
            else:
                w.fill(x, -6, z, x, -2, z, B.S("water"))
                w.fill(x, -1, z, x, 4, z, AIR)
    # mill house: stone basement into the bank, timbered upper floor, steep roof, chimney with smoke
    x0, x1, z0, z1 = wall_x, wall_x + 9, zc - 6, zc + 6
    w.fill(x0, -7, z0, x0, 0, z1, F("stone_bricks"))
    f, W, D = TK.frame_for(w, (x0, z0, x1, z1), "north")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="terracotta_roof_tiles", roof_kind="gable_x",
                     portal_w=2, portal_h=4, trim=None, wall="stone_bricks", pilaster="polished_andesite",
                     plinth="cobblestone", window_every=3, dormers=False, chimney=True, side_windows=False,
                     sign=(["", "水車小屋", "Watermill", ""], "white"))
    TK.civic_hall(f, W, D, sp)
    # the water wheel's axle bearing in the west wall, half in the race
    TK.water_wheel(w, x0, -1, zc, "west", radius=WHEEL_R, width=2.0)
    # inside: millstones (grindstones), sacks, gears
    w.set(x0 + 2, 1, zc, B.S("grindstone", face="floor", facing="north"))
    w.set(x0 + 3, 1, zc, B.log("spruce", "x"))
    for (dx, dz) in ((5, -3), (6, -3), (5, 3), (6, 4)):
        w.set(x0 + dx, 1, zc + dz, B.barrel("up") if dz < 0 else B.S("hay_block", axis="y"))
    TK.flagpole(w, x1 + 2, 0, z1 + 2, 7, "east", "kingdom", 3.0, 2.0, pole="fence")
    return wall_x


def build(w, district, terrain):
    T = terrain
    wall_x = watermill(w, T)
    # fields east of the river (avoiding the road and the mill)
    k = 0
    for z0 in list(range(-58, -12, 11)) + list(range(10, 84, 11)):
        for x0 in range(136, 158, 11):
            if abs(z0 + 4 - MILL_Z) < 12 and x0 < wall_x + 14:
                continue
            crop = FM.CROPS[int(hash2(x0, z0, 99) * len(FM.CROPS))]
            FM.field(w, T, x0, z0, min(x0 + 9, 158), z0 + 7, crop, seed=k)
            k += 1
    FM.farmhouse(w, (146, -30, 154, -22), "west", 5101)
    for (x, z) in ((140, 64), (150, -40)):
        FM.haystack(w, x, z)
    FM.scarecrow(w, 152, 70)
    TK.crowd(w, "farm", [(wall_x + 5, 1, MILL_Z - 9)], start=5, need=10)
