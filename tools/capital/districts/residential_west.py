"""住宅街 (西) The old town west of the guild (2026-11-11 rebuild, 「入り組んだ街並み」): two blocks either side of
the west avenue, each cut by a jogging lane and a cross lane into four clusters of tall half-timbered houses, with
rooms bridging the lanes, a little well square where the lanes cross, ivy, washing and pennant strings, shop
signs, barrels and flower pots."""
from __future__ import annotations

from . import _maze as MZ
from . import _town_kit as TK

DISTRICT = "residential_west"

VIEWS = {
    "old_town_lane": dict(pos=(-82.5, 2.6, -36.5), look=(-83, 4, -26), fov=80, sun=(40, -150), fog=900,
                          label="Old town: the lane, a room bridging it, washing lines"),
    "old_town_square": dict(pos=(-73.5, 3.0, -16.5), look=(-80, 2, -23), fov=80, sun=(40, -150), fog=900,
                            label="Old town: the well square where the lanes cross"),
}


def build(w, district, terrain):
    MZ.quarter(w, (-98, -39, -59, -6), seed=11, a1=-84, a2=-78, b=-24, bridges=3)
    MZ.quarter(w, (-98, 6, -59, 38), seed=23, a1=-80, a2=-74, b=21, bridges=2)
    TK.crowd(w, "residential", [(-80, 1, -23), (-76, 1, 22)], start=20, need=10)
