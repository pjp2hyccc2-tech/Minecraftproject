"""住宅街 (西) West quarter: rows of half-timbered houses on both sides of the west avenue, between the
guild/shop blocks (street_w1) and the west wall, with back gardens, a well and a small square."""
from __future__ import annotations

from capital import blocks as B
from capital import deco

from . import _town_kit as TK

DISTRICT = "residential_west"


def block(w, x0, z0, x1, z1, street_side, seed):
    """A block bounded by streets: rows along the north/south edges and the east edge (street_w1)."""
    TK.pave(w, x0, z0, x1, z1, TK.LANE_MIX, seed=seed)
    # yards first (grass), houses on the perimeter
    w.fill(x0 + 1, 0, z0 + 11, x1 - 11, 0, z1 - 11, B.S("grass_block"))
    TK.row(w, x0, z0, x1, z0 + 9, "north", seed=seed + 1)
    TK.row(w, x0, z1 - 9, x1, z1, "south", seed=seed + 2)
    TK.row(w, x1 - 9, z0 + 10, x1, z1 - 10, "east", seed=seed + 3, min_w=6, max_w=8)


def build(w, district, terrain):
    block(w, -101, -39, -59, -6, "south", 11)
    block(w, -101, 6, -59, 39, "north", 23)
