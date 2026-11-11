"""住宅街 (南西) The canal quarter south-west (2026-11-11 rebuild): canal-front houses on the Grand Canal's south
quay, a block of lanes and bridges behind them down to the wall lane, houses facing the canal's south branch, and
the tavern on the south avenue."""
from __future__ import annotations

from capital import blocks as B

from . import _civic_kit as K
from . import _inns as INN
from . import _maze as MZ
from . import _town_kit as TK

DISTRICT = "south_west"

VIEWS = {
    "tavern": dict(pos=(-9.5, 3.6, 49.5), look=(-20, 2.5, 60), fov=82, sun=(40, -150), fog=900,
                   label="The tavern's common room"),
    "canal_lanes": dict(pos=(-55.5, 2.8, 50.5), look=(-56, 4, 62), fov=80, sun=(40, -150), fog=900,
                        label="Canal quarter: a lane from the quay"),
}


def build(w, district, terrain):
    TK.pave(w, -98, 48, -6, 73, TK.LANE_MIX, seed=501)
    TK.pave(w, -82, 48, -25, 49, TK.QUAY_MIX, seed=502, only_ground=False)
    INN.tavern(w, (-24, 48, -8, 64), "east")
    MZ.quarter(w, (-82, 48, -26, 73), seed=61, a1=-63, a2=-55, b=60, bridges=3,
               inset={"north": 2, "south": 2, "west": 1, "east": 1})
    TK.row(w, -98, 48, -94, 73, "east", seed=512, min_w=5, max_w=7, inset=0, end_gap=(0, 0))
    for x in (-40, -70):
        K.lamp(w, x, 0, 72, height=3)
    TK.crowd(w, "tavern", [(-15, 1, 54), (-19, 1, 52), (-12, 1, 58)], need=14)
    TK.crowd(w, "residential", [(-58, 1, 61), (-36, 1, 71)], need=10)
