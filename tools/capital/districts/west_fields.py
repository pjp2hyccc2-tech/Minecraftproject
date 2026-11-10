"""農場 Farms south-west of the walls: patchwork crop fields with irrigation and hedges, three white tower
windmills with turning sails, farmhouses, barns, orchards, haystacks and scarecrows."""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2

from . import _farm as FM
from . import _town_kit as TK

DISTRICT = "west_fields"

VIEWS = {
    "windmill": dict(pos=(-114, 8, 56), look=(-136, 15, 34), fov=70, sun=(40, -150), fog=2600,
                     label="A windmill in the west fields"),
}

MILLS = ((-136, 32), (-128, 100), (-104, 142))


def build(w, district, terrain):
    T = terrain
    # field grid: plots 12 x 9 with 3-wide paths, crops chosen per plot
    k = 0
    for z0 in range(8, 148, 12):
        for x0 in range(-158, -116, 14):
            if any(abs(x0 + 6 - mx) < 11 and abs(z0 + 4 - mz) < 11 for mx, mz in MILLS):
                continue
            crop = FM.CROPS[int(hash2(x0, z0, 77) * len(FM.CROPS))]
            FM.field(w, T, x0, z0, x0 + 11, z0 + 8, crop, seed=k)
            if hash2(x0, z0, 78) < 0.5:
                FM.hedge(w, T, x0 - 1, z0 - 1, x0 + 12, z0 + 9, seed=k, gaps=((x0 + 5, z0 - 1),))
            k += 1
    # strip south of the walls between the wall and the lake shore (west of the bridge)
    for x0 in range(-98, -40, 14):
        FM.field(w, T, x0, 88, x0 + 11, 92, "wheat" if x0 % 28 else "carrots", seed=x0)
    for z0 in range(98, 156, 12):
        FM.field(w, T, -111, z0, -100, z0 + 8, FM.CROPS[(z0 // 12) % len(FM.CROPS)], seed=z0)
    for (mx, mz) in MILLS:
        FM.windmill(w, mx, mz, facing="south", radius=7.0, seed=mx)
    # farmhouses and barns
    FM.farmhouse(w, (-130, 60, -121, 68), "east", 4101)
    FM.barn(w, (-130, 72, -119, 81), "east", seed=4102)
    FM.farmhouse(w, (-150, 120, -141, 128), "east", 4103, roof="royal_roof_tiles")
    FM.barn(w, (-118, 118, -108, 127), "west", seed=4104)
    # orchard
    for x in range(-156, -132, 5):
        for z in range(82, 96, 5):
            deco.tree_oak(w, x, 0, z, seed=x * 7 + z, height=4, crown=2.2,
                          kind=("oak", "birch", "flowering_azalea")[int(hash2(x, z, 5) * 3)] if False else
                          ("oak" if hash2(x, z, 5) < 0.6 else "birch"))
    for (x, z) in ((-145, 50), (-120, 140)):
        FM.scarecrow(w, x, z)
    for (x, z) in ((-124, 86), (-127, 88), (-139, 112)):
        FM.haystack(w, x, z)
    TK.crowd(w, "farm", [(-123, 1, 70), (-136, 1, 44), (-145, 1, 132), (-110, 1, 116)], need=10)
