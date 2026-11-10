"""住宅街 (西) West quarter: rows of half-timbered houses on both sides of the west avenue, between the
guild/shop blocks (street_w1) and the west wall, a row facing the wall lane, and back gardens with fruit trees,
vegetable patches, wells and washing lines."""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "residential_west"


def yard(w, x0, z0, x1, z1, seed):
    """Back gardens: lawn, vegetable patches, fruit trees, a well, a washing line."""
    w.fill(x0, 0, z0, x1, 0, z1, B.S("grass_block"))
    w.fill(x0, 1, z0, x1, 3, z1, K.AIR)
    for x in range(x0 + 1, x1 - 2, 7):
        for z in range(z0 + 1, z1 - 2, 6):
            u = hash2(x, z, seed)
            if u < 0.45:
                deco.tree_oak(w, x + 2, 0, z + 2, seed=x * 3 + z, height=4, crown=2.0,
                              kind="oak" if u < 0.3 else "birch")
            elif u < 0.7:
                for xx in range(x, min(x + 4, x1)):
                    for zz in range(z, min(z + 3, z1)):
                        w.set(xx, 0, zz, B.S("farmland", moisture=7))
                        w.set(xx, 1, zz, B.S(("carrots", "potatoes", "wheat")[int(hash2(xx, zz, seed) * 3)], age=7))
            elif u < 0.85:
                w.set(x + 1, 1, z + 1, B.S("water_cauldron", level=3))
                for dx in (0, 2):
                    w.fill(x + dx, 1, z + 1, x + dx, 3, z + 1, B.fence("spruce"))
                w.fill(x, 4, z + 1, x + 2, 4, z + 1, B.slab("spruce", "bottom"))
            else:
                w.fill(x, 1, z + 1, x, 3, z + 1, B.fence("spruce"))
                w.fill(x + 4, 1, z + 1, x + 4, 3, z + 1, B.fence("spruce"))
                for xx in range(x + 1, x + 4):
                    w.set(xx, 3, z + 1, B.carpet(("white", "light_blue", "pink")[xx % 3]))


def block(w, x0, z0, x1, z1, seed):
    """A block bounded by streets: rows along the north/south edges, the east edge (street_w1) and the west
    edge (facing the wall lane), gardens in the middle."""
    TK.pave(w, x0, z0, x1, z1, TK.LANE_MIX, seed=seed)
    yard(w, x0 + 11, z0 + 11, x1 - 11, z1 - 11, seed)
    TK.row(w, x0, z0, x1, z0 + 9, "north", seed=seed + 1)
    TK.row(w, x0, z1 - 9, x1, z1, "south", seed=seed + 2)
    TK.row(w, x1 - 9, z0 + 10, x1, z1 - 10, "east", seed=seed + 3, min_w=6, max_w=8)
    TK.row(w, x0, z0 + 10, x0 + 9, z1 - 10, "west", seed=seed + 4, min_w=6, max_w=8, inset=2)


def build(w, district, terrain):
    block(w, -98, -39, -59, -6, 11)
    block(w, -98, 6, -59, 39, 23)
    TK.crowd(w, "residential", [(-80, 1, 22)], start=20, need=12)
