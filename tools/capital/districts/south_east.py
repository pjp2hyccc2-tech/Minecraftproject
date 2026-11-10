"""宿屋街 (南東) Inn quarter: the Silver Star inn on street_s1, houses along the south avenue and street_s1,
a back lane under the south wall with the passage to the harbour postern."""
from __future__ import annotations

from capital import blocks as B
from capital import deco

from . import _civic_kit as K
from . import _inns as INN
from . import _town_kit as TK

DISTRICT = "south_east"

VIEWS = {
    "inn": dict(pos=(29.5, 3.6, 51.5), look=(46, 2.5, 58), fov=82, sun=(40, -150), fog=900,
                label="The inn: reception and dining hall"),
    "inn_rooms": dict(pos=(29.5, 8.6, 52.5), look=(42, 7, 50), fov=82, sun=(40, -150), fog=900,
                      label="The inn: a guest room"),
}


def build(w, district, terrain):
    TK.pave(w, 6, 47, 98, 73, TK.LANE_MIX, seed=601)
    TK.pave(w, 52, 59, 98, 61, TK.COBBLE_MIX, seed=602, only_ground=False)
    TK.pave(w, 72, 62, 77, 73, TK.COBBLE_MIX, seed=603, only_ground=False)
    INN.inn(w, (26, 49, 50, 64), "north")
    TK.row(w, 6, 47, 17, 65, "west", seed=611, min_w=6, max_w=8)
    TK.row(w, 52, 47, 97, 58, "north", seed=612)
    TK.row(w, 52, 62, 71, 72, "north", seed=613)
    TK.row(w, 78, 62, 97, 72, "north", seed=614)
    # the inn's stable yard behind it
    for (x, z) in ((30, 67), (31, 67), (30, 68)):
        w.set(x, 1, z, B.S("hay_block", axis="y"))
    w.set(31, 2, 67, B.S("hay_block", axis="y"))
    for x in range(34, 48):
        w.set(x, 1, 70, B.fence("spruce"))
    w.set(40, 1, 68, B.S("water_cauldron", level=3))
    for x in (60, 88):
        K.lamp(w, x, 0, 61, height=3)
    TK.crowd(w, "inn", [(36, 1, 52), (44, 1, 55), (30, 1, 52)], need=12)
    TK.crowd(w, "residential", [(64, 1, 60), (74, 1, 68)], start=4, need=12)
