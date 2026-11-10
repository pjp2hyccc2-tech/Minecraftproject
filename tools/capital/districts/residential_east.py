"""住宅街 (東) East quarter: half-timbered houses between the market, the cathedral close and the east wall."""
from __future__ import annotations

from capital import blocks as B
from capital import deco

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "residential_east"

VIEWS = {
    "east_street": dict(pos=(70, 3.6, 2), look=(96, 7, -12), fov=72, sun=(40, -150), fog=2600,
                        label="East avenue houses"),
}


def build(w, district, terrain):
    TK.pave(w, 59, -39, 98, -6, TK.LANE_MIX, seed=701)
    w.fill(70, 0, -28, 86, 0, -17, B.S("grass_block"))
    TK.row(w, 59, -15, 87, -6, "south", seed=711)
    TK.row(w, 59, -39, 97, -30, "north", seed=712)
    TK.row(w, 59, -29, 68, -16, "east", seed=713, min_w=6, max_w=8, inset=2) if False else \
        TK.row(w, 59, -29, 68, -16, "west", seed=713, min_w=6, max_w=8)
    # back gardens: fruit trees, a well, washing line
    for (x, z) in ((74, -25), (82, -20), (80, -27)):
        deco.tree_oak(w, x, 0, z, seed=x + z, height=4, crown=2.2, kind="oak" if x % 2 else "birch")
    w.set(77, 1, -21, B.S("water_cauldron", level=3))
    for x in range(88, 98):
        w.set(x, 1, -21, B.fence("spruce"))
    for (x, z) in ((90, -26), (95, -25)):
        deco.tree_oak(w, x, 0, z, seed=x * 3 + z, height=5, crown=2.4)
    deco.bench(w, 92, 0, -28, facing="south", length=3)
    TK.crowd(w, "residential", [(93, 1, -24)], start=7, need=14)
