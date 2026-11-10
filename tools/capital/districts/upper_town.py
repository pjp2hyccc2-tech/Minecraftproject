"""城下 Upper town: noble houses (castle-stone ground floors, gilded blue roofs) with front gardens along
street_n1 and street_w1, and formal gardens with fountains and cypresses at the foot of the castle cliff."""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "upper_town"

VIEWS = {
    "upper_gardens": dict(pos=(-36, 6, -52), look=(-46, 4, -80), fov=74, sun=(40, -150), fog=2600,
                          label="Upper town: noble houses and gardens below the castle"),
}


def front_garden(w, x0, z0, x1, z1, seed):
    """Low hedge with a gate gap and flowers between the street and the noble houses."""
    w.fill(x0, 0, z0, x1, 0, z1, B.S("grass_block"))
    for x in range(x0, x1 + 1):
        if hash2(x, z0, seed) < 0.85:
            w.set(x, 1, z1, B.leaves("oak") if (x // 3) % 4 else B.fence("dark_oak"))
        if hash2(x, z0 + 1, seed) < 0.6 and z1 - z0 >= 2:
            w.set(x, 1, z0 + 1, B.S(("allium", "white_tulip", "cornflower", "azure_bluet", "pink_tulip")[
                int(hash2(x, seed, 3) * 5)]))


def formal_garden(w, x0, z0, x1, z1, seed):
    w.fill(x0, 0, z0, x1, 0, z1, B.S("grass_block"))
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # gravel cross paths
    w.fill(x0, 0, cz - 1, x1, 0, cz + 1, B.S("gravel"))
    w.fill(cx - 1, 0, z0, cx + 1, 0, z1, B.S("gravel"))
    # box hedges around four parterres with flowers
    for (a0, a1, b0, b1) in ((x0 + 1, cx - 3, z0 + 1, cz - 3), (cx + 3, x1 - 1, z0 + 1, cz - 3),
                             (x0 + 1, cx - 3, cz + 3, z1 - 1), (cx + 3, x1 - 1, cz + 3, z1 - 1)):
        if a1 - a0 < 2 or b1 - b0 < 2:
            continue
        S.rect_ring(w, a0, b0, a1, b1, 1, B.leaves("oak"))
        for x in range(a0 + 1, a1):
            for z in range(b0 + 1, b1):
                if hash2(x, z, seed) < 0.75:
                    w.set(x, 1, z, B.S(("red_tulip", "white_tulip", "allium", "cornflower", "oxeye_daisy",
                                        "pink_tulip", "lily_of_the_valley")[int(hash2(z, x, seed + 1) * 7)]))
    # fountain in the middle
    S.cylinder(w, cx, cz, 2.4, 1, 1, K.F(K.SM))
    S.cylinder(w, cx, cz, 1.5, 1, 1, B.S("water"))
    w.fill(cx, 1, cz, cx, 3, cz, K.F(K.CARVED))
    TK.spray(w, cx, 4, cz, power=2)
    w.set(cx, 4, cz, B.S("fountain_spray", power=2, facing="up"))
    # cypresses on the corners and benches
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        deco.tree_cypress(w, x, 0, z, seed=x + z + seed)
    deco.bench(w, cx - 4, 0, cz - 2, facing="north", length=2) if False else None


def build(w, district, terrain):
    TK.pave(w, -98, -86, -12, -47, TK.COBBLE_MIX, seed=801)
    # west part: houses on street_n1 (front south) and street_w1 (front east), gardens behind
    front_garden(w, -97, -50, -60, -49, 811)
    TK.row(w, -97, -61, -60, -51, "south", seed=812, posh=True, min_w=9, max_w=12, inset=2)
    TK.row(w, -71, -78, -60, -62, "east", seed=813, posh=True, min_w=8, max_w=9, inset=2)
    formal_garden(w, -96, -84, -74, -63, 814)
    # east part: houses on street_n1 and street_w1 (front west), gardens below the cliff
    front_garden(w, -50, -50, -13, -49, 821)
    TK.row(w, -50, -61, -13, -51, "south", seed=822, posh=True, min_w=9, max_w=12, inset=2)
    TK.row(w, -51, -80, -40, -62, "west", seed=823, posh=True, min_w=8, max_w=10, inset=2)
    formal_garden(w, -38, -84, -14, -63, 824)
    # a gazebo in the east garden
    gx, gz = -20, -80
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        w.fill(gx + dx, 1, gz + dz, gx + dx, 4, gz + dz, B.S("quartz_pillar", axis="y"))
    S.dome(w, gx, 5, gz, 3, K.F(K.PALE), ry=3)
    w.fill(gx - 2, 5, gz - 2, gx + 2, 5, gz + 2, K.F(K.SM))
    w.set(gx, 9, gz, B.S("gilded_finial"))
    TK.crowd(w, "residential", [(-26, 1, -73)], start=10, need=14)
