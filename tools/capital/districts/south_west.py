"""住宅街 (南西) South-west quarter: the tavern on the south avenue and rows of houses along street_s1 and a
back lane under the south wall."""
from __future__ import annotations

from capital import blocks as B
from capital import deco

from . import _civic_kit as K
from . import _inns as INN
from . import _town_kit as TK

DISTRICT = "south_west"

VIEWS = {
    "tavern": dict(pos=(-9.5, 3.6, 49.5), look=(-20, 2.5, 60), fov=82, sun=(40, -150), fog=900,
                   label="The tavern's common room"),
}


def build(w, district, terrain):
    TK.pave(w, -98, 47, -6, 73, TK.LANE_MIX, seed=501)
    TK.pave(w, -98, 59, -25, 61, TK.COBBLE_MIX, seed=502, only_ground=False)
    INN.tavern(w, (-24, 48, -8, 64), "east")
    TK.row(w, -98, 47, -27, 58, "north", seed=511)
    TK.row(w, -98, 62, -27, 72, "north", seed=512, alley_every=4)
    # wells and little squares on the lane
    for x in (-80, -46):
        w.set(x, 1, 60, B.S("water_cauldron", level=3))
        for (dx, dz) in ((-1, 0), (1, 0)):
            w.fill(x + dx, 1, 60, x + dx, 3, 60, B.fence("spruce"))
        w.fill(x - 1, 4, 60, x + 1, 4, 60, B.slab("spruce", "bottom"))
    for x in (-64, -32):
        K.lamp(w, x, 0, 61, height=3)
    TK.crowd(w, "tavern", [(-15, 1, 54), (-19, 1, 52), (-12, 1, 58)], need=14)
    TK.crowd(w, "residential", [(-70, 1, 60), (-40, 1, 60), (-90, 1, 60)], need=14)
