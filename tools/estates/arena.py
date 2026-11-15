"""The guild battle arena (docs/modules/guild_battles.md): ``arena/colosseum``, an open colosseum on a floating island
in the estates dimension, three of which the server keeps for the guild battles (ギルド対抗戦).

The fighting floor is an oval of sand (about 36 x 26) ringed by a wall with tiered stands; the red side's gate is in the
west, the blue side's in the east, each behind it a small yard under its colours where the fighters stand ready
(spots ``spawn.red`` and ``spawn.blue``, facing the floor). Broken columns and two low barricades give cover. The
north gate holds the way out (a room door: leaving forfeits) with the ``entry`` and ``exit`` spots in front of it.
Local y = 0 is the floor (players stand at y = 1).
"""
from __future__ import annotations

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import blocks as B  # noqa: E402
from capital.noise import fbm2, hash2, hash3  # noqa: E402

from estates import kit as EK  # noqa: E402
from estates.guild import hkit as K  # noqa: E402

F, ST, SL, WL = B.full, B.stairs, B.slab, B.wall
AIR = "minecraft:air"
RX, RZ = 18.0, 13.0          # the fighting floor's half axes
WALL_H = 4


def _d(x, z, rx=RX, rz=RZ):
    return math.hypot(x / rx, z / rz)


def _inward(x, z):
    """The horizontal facing that points from (x, z) back to the centre, for stairs of the stands."""
    if abs(x) / RX >= abs(z) / RZ:
        return "west" if x > 0 else "east"
    return "north" if z > 0 else "south"


def colosseum(w):
    s = EK.Storage()
    # ------------------------------------------------------------------ the island and the foundation
    K.island(w, 0, 0, 37.0, 29.0, top=-1, depth=18, surface=("stone",), soil="stone",
             rock=("stone", "andesite", "tuff", "cobblestone", "stone"), seed=903, rough=0.18)
    for x in range(-36, 37):
        for z in range(-28, 29):
            d = _d(x, z, 33.0, 25.0)
            if d <= 1.0:
                w.set(x, 0, z, F("polished_andesite") if (x + z) % 7 else F("andesite"))
    # ------------------------------------------------------------------ the fighting floor
    for x in range(-int(RX) - 1, int(RX) + 2):
        for z in range(-int(RZ) - 1, int(RZ) + 2):
            d = _d(x, z)
            if d <= 1.0:
                n = fbm2(x, z, 5.0, 2, 11)
                st = "sand" if n < 0.25 else ("smooth_sandstone" if n < 0.5 else "packed_mud")
                if d > 0.93:
                    st = "polished_andesite"
                w.set(x, 0, z, F(st))
                for y in range(1, 14):
                    w.set(x, y, z, AIR)
    # the centre mark: a rose of smooth stone and gold
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) + abs(z) <= 2:
                w.set(x, 0, z, F("chiseled_sandstone") if (x, z) == (0, 0) else F("smooth_stone"))
    # ------------------------------------------------------------------ the ring wall and the stands
    for x in range(-34, 35):
        for z in range(-27, 28):
            d = _d(x, z)
            if 1.0 < d <= 1.12:                         # the ring wall
                for y in range(1, WALL_H + 1):
                    w.set(x, y, z, F("stone_bricks") if hash3(x, y, z, 5) > 0.2 else F("cracked_stone_bricks"))
                w.set(x, WALL_H + 1, z, SL("smooth_stone", "bottom"))
            elif 1.12 < d <= 1.62:                      # three tiers of stands, rising outward
                tier = min(3, int((d - 1.12) / 0.167) + 1)
                top = WALL_H + tier
                for y in range(1, top):
                    w.set(x, y, z, F("stone_bricks"))
                w.set(x, top, z, ST("stone_brick", _inward(x, z)))
            elif 1.62 < d <= 1.75:                      # the outer colonnade
                pillar = hash2(x // 3, z // 3, 7) > 0.55
                for y in range(1, WALL_H + 9):
                    if y <= WALL_H + 3 or pillar:
                        w.set(x, y, z, F("smooth_stone") if y > WALL_H + 3 else F("stone_bricks"))
                if pillar:
                    w.set(x, WALL_H + 9, z, SL("smooth_stone", "bottom"))
    # ------------------------------------------------------------------ the three gates: red (west), blue (east), north
    _side_gate(w, -1, "red", "west")
    _side_gate(w, 1, "blue", "east")
    _north_gate(w)
    # ------------------------------------------------------------------ cover on the floor
    for (cx, cz, h) in ((-8, -5, 3), (8, 5, 3), (-8, 5, 2), (8, -5, 2), (0, -8, 2), (0, 8, 2)):
        _column(w, cx, cz, h)
    for (cx, cz) in ((-12, 0), (12, 0)):
        for dz in (-2, -1, 1, 2):
            w.set(cx, 1, cz + dz, B.S("hay_block", axis="y"))
        w.set(cx, 2, cz - 2, B.S("hay_block", axis="z"))
        w.set(cx, 2, cz + 2, B.S("hay_block", axis="z"))
    # ------------------------------------------------------------------ lights and colours
    for k in range(16):
        a = 2.0 * math.pi * k / 16.0
        x = int(round(math.cos(a) * RX * 1.06))
        z = int(round(math.sin(a) * RZ * 1.06))
        if abs(z) <= 2 and abs(x) > 10 or abs(x) <= 2 and z < -8:
            continue                                     # the gates
        w.set(x, WALL_H + 2, z, B.lantern(hanging=False))
    step_out = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}
    for k in range(24):
        a = 2.0 * math.pi * (k + 0.5) / 24.0
        x = int(round(math.cos(a) * RX * 0.985))
        z = int(round(math.sin(a) * RZ * 0.985))
        if abs(z) <= 3 and abs(x) > 12 or abs(x) <= 3 and z < -8:
            continue                                     # the gates
        facing = _inward(x, z)
        ox, oz = step_out[facing]
        if w.is_empty(x, 3, z) and w.get(x + ox, 3, z + oz).startswith("minecraft:"):
            if "brick" in w.get(x + ox, 3, z + oz):
                w.set(x, 3, z, B.wall_banner("red" if x < 0 else "blue", facing))
    return s


def _side_gate(w, sign, color, side):
    """A side's gate in the ring wall at x = sign * RX and the yard behind it under the side's colours, with the
    side's spawn spot facing the floor."""
    facing_in = "east" if sign < 0 else "west"
    x_wall = int(sign * (RX - 1))          # through the whole ring (it bulges in beside the axis)
    # the passage through the wall and the stands
    for x in range(min(x_wall, sign * 31), max(x_wall, sign * 31) + 1):
        for z in range(-2, 3):
            w.set(x, 0, z, F("polished_andesite"))
            for y in range(1, 5):
                w.set(x, y, z, AIR)
        for z in (-3, 3):
            for y in range(1, 6):
                w.set(x, y, z, F("stone_bricks"))
        for z in range(-3, 4):
            w.set(x, 5, z, F("stone_bricks"))
    # the arch at the floor
    for z in (-2, 2):
        w.set(int(sign * (RX + 1)), 4, z, ST("stone_brick", "south" if z < 0 else "north", "top"))
    # the yard: a walled court in the side's colour
    x0, x1 = sorted((sign * 24, sign * 31))
    for x in range(x0, x1 + 1):
        for z in range(-6, 7):
            edge = x in (x0, x1) or abs(z) == 6
            w.set(x, 0, z, F(f"{color}_terracotta") if (x + z) % 2 else F("polished_andesite"))
            for y in range(1, 7):
                w.set(x, y, z, F("stone_bricks") if edge and not (abs(z) <= 2 and x == (x1 if sign < 0 else x0))
                      else AIR)
            w.set(x, 6, z, F("stone_bricks") if edge else SL("smooth_stone", "top"))
    for x in range(x0 + 1, x1):
        for z in range(-5, 6):
            if abs(z) <= 1:
                w.set(x, 1, z, B.S(f"{color}_carpet"))
    # the side's colours on the yard's back wall and lanterns
    back = x0 if sign < 0 else x1
    out = "east" if sign < 0 else "west"
    for z in (-4, -2, 0, 2, 4):
        if w.get(back, 3, z).startswith("minecraft:stone_bricks"):
            ox = 1 if sign < 0 else -1
            w.set(back + ox, 3, z, B.wall_banner(color, out))
    for z in (-4, 4):
        w.set(back + (1 if sign < 0 else -1), 5, z, B.lantern(hanging=True))
    spot_x = sign * 27
    w.spot(f"spawn.{color}", (spot_x, 1, 0), {"east": -90.0, "west": 90.0}[facing_in])


def _north_gate(w):
    """The way out in the north: a short passage through the wall and the stands to a door (leaving forfeits)."""
    z_wall = -int(RZ - 1)
    for z in range(-24, z_wall + 1):
        for x in range(-2, 3):
            w.set(x, 0, z, F("polished_andesite"))
            for y in range(1, 5):
                w.set(x, y, z, AIR)
        for x in (-3, 3):
            for y in range(1, 6):
                w.set(x, y, z, F("stone_bricks"))
        for x in range(-3, 4):
            w.set(x, 5, z, F("stone_bricks"))
    for x in range(-3, 4):
        for y in range(1, 6):
            w.set(x, y, -25, F("stone_bricks"))
    EK.exit_door(w, 0, 1, -25, "south")
    EK.entry(w, 0, 1, -21, 0.0)
    w.set(-2, 3, -23, B.lantern(hanging=False))
    w.set(2, 3, -23, B.lantern(hanging=False))
    w.set(-2, 2, -23, F("polished_andesite"))
    w.set(2, 2, -23, F("polished_andesite"))


def _column(w, cx, cz, h):
    """A broken column of 2 x 2 to hide behind."""
    for dx in (0, 1):
        for dz in (0, 1):
            top = h - (1 if (dx + dz) == 2 and h > 2 else 0)
            for y in range(1, top + 1):
                w.set(cx + dx, y, cz + dz, F("quartz_pillar") if y < top else F("chiseled_quartz_block"))


DESIGNS = {("arena", "colosseum"): colosseum}

VIEWS = {
    ("arena", "colosseum"): {
        "colosseum_aerial": dict(pos=(-40.0, 34.0, 36.0), look=(0.0, 0.0, 0.0), fov=62,
                                 label="The guild battle arena from the air"),
        "colosseum_floor": dict(pos=(-24.0, 2.6, 0.5), look=(10.0, 2.0, 0.5), fov=80,
                                label="The arena from the red gate"),
    },
}


def _register_views():
    from estates import views
    views.VIEWS.update(VIEWS)


_register_views()
