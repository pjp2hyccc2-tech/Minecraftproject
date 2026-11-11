"""住宅街 (東) The old town east of the market (2026-11-11 rebuild): a block cut by a jogging lane and a cross lane
into clusters of tall half-timbered houses with rooms bridging the lanes and a well square, and a row against
the east wall."""
from __future__ import annotations

from . import _maze as MZ
from . import _town_kit as TK

DISTRICT = "residential_east"


def build(w, district, terrain):
    MZ.quarter(w, (59, -39, 87, -6), seed=41, a1=68, a2=73, b=-25, bridges=2)
    TK.row(w, 88, -39, 98, -21, "west", seed=45, min_w=6, max_w=8, inset=2)
    TK.crowd(w, "residential", [(72, 1, -24)], start=12, need=10)
