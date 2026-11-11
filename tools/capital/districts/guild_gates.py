"""ギルド門の広場 Guild Gate Terrace (placeholder until the guild-house work fills it).

The terrace top block is y = 6 (players stand at y = 7), level with the viaduct's first landing (z -53..-56).
The twenty guild gates, the broker, the stairs to street_n1 and the bridge to the viaduct are built here.
"""
from __future__ import annotations

from capital import blocks as B

from . import _town_kit as TK

DISTRICT = "guild_gates"
TOP = 6


def build(w, district, terrain):
    x0, z0, x1, z1 = district.bbox()
    w.fill(x0, 1, z0, x1, TOP - 1, z1, B.S("stone"))
    TK.pave(w, x0, z0, x1, z1, TK.SQUARE_MIX, seed=7001, y=TOP, only_ground=False)
    w.fill(x0, 1, z1, x1, TOP, z1, B.full("castle_bricks"))
    for x in range(x0 + 1, x1, 2):
        w.set(x, TOP + 1, z1, B.wall("castle_bricks"))
    # a plain stair down to street_n1 in the middle of the front
    cx = (x0 + x1) // 2
    for k in range(TOP):
        z = z1 - (TOP - 1) + k
        w.fill(cx - 2, TOP - k, z, cx + 2, TOP - k, z, B.stairs("stone_bricks", "north"))
        w.fill(cx - 2, TOP - k + 1, z, cx + 2, TOP + 4, z, B.S("air"))
