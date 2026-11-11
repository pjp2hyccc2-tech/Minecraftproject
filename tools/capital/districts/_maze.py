"""Intricate quarters (2026-11-11 rebuild, after the owner's canal-town pictures: 「入り組んだ街並み」).

A quarter is a street block cut by two alleys that cross with a jog (the north and south legs of the lane are
offset, so the crossing is a little dog-leg square with a well), four sub-blocks of back-to-back townhouses whose
fronts face the streets and the alleys, timber-framed rooms bridging the alleys overhead, and the life of the lanes:
lanterns, ivy, washing and pennant strings between the upper floors, barrels, flower pots and hanging shop signs.

``bunting()`` and ``shop_sign()`` write the ids and data agreed with the town-gimmick blocks
(``eternalrealm:bunting``: ``{Tx,Ty,Tz,Sag,Style,Colors,Wind}``; ``eternalrealm:shop_sign[facing,attach,icon]``).
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

AIR = "minecraft:air"
F = K.F
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
PENNANTS = ((14, 4, 11, 0, 5), (11, 0, 14, 4), (1, 4, 3, 10, 5), (14, 0, 11, 0), (6, 4, 9, 0, 3))
ICONS = ("sword", "shield", "potion", "bread", "anvil", "book", "gem", "flower", "shirt", "key", "fish", "mug",
         "scroll", "map", "hammer", "ring")


# ------------------------------------------------------------------------------------------------ gimmicks
def bunting(w, x, y, z, to, style="pennant", colors=(14, 4, 11, 0), sag=1.0, wind=1.0, facing="east"):
    """A string of pennants / flags / lanterns / laundry / garland from the anchor (x, y, z) (an air cell in
    front of a wall, an eave or a post top) to the point at block offset ``to``."""
    tx, ty, tz = (int(v) for v in to)
    w.set(x, y, z, B.S("eternalrealm:bunting", facing=facing))
    cols = ",".join(str(int(c)) for c in colors)
    w.be(x, y, z, f'{{Tx:{tx},Ty:{ty},Tz:{tz},Sag:{float(sag):.1f}f,Style:"{style}",Colors:[I;{cols}],'
                  f'Wind:{float(wind):.1f}f}}')


def shop_sign(w, x, y, z, facing, icon, attach="wall"):
    """A hanging shop board with a picture of the trade in the air cell (x, y, z) in front of a wall (``facing``
    = away from the wall) or under a ceiling block."""
    w.set(x, y, z, B.S("eternalrealm:shop_sign", facing=facing, attach=attach, icon=icon))


# ------------------------------------------------------------------------------------------------ helpers
def _solid(w, x, y, z):
    st = w.get(x, y, z)
    return st not in (AIR, "eternalrealm:void") and "water" not in st and not any(
        k in st for k in ("_carpet", "torch", "lantern", "flower", "potted", "vine", "bunting", "sign"))


def _free(w, x, y, z):
    return w.get(x, y, z) == AIR


def alley_bridge(w, x0, z0, x1, z1, y, along, seed=0):
    """A timber-framed room spanning an alley overhead: floor block y over the box (x0..x1, z0..z1), walls y+1..y+3
    with leaded windows on the two faces looking ``along`` the alley, a gable roof, an arched soffit and a
    lantern underneath. ``along``: 'x' or 'z' (the direction people walk under it)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    tim = B.log("dark_oak")
    pl = F("white_plaster")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, y, z, B.S("spruce_planks"))
            for yy in range(y + 1, y + 4):
                w.set(x, yy, z, AIR)
            w.set(x, y - 1, z, AIR)
    # the faces seen from the alley (the box's ends along the alley) get windows; the sides meet the houses
    if along == "z":
        faces = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)]
        sides = [(x0, z) for z in range(z0, z1 + 1)] + [(x1, z) for z in range(z0, z1 + 1)]
    else:
        faces = [(x0, z) for z in range(z0, z1 + 1)] + [(x1, z) for z in range(z0, z1 + 1)]
        sides = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)]
    for (x, z) in faces:
        corner = (x in (x0, x1)) and (z in (z0, z1))
        for yy in range(y + 1, y + 4):
            if corner:
                w.set(x, yy, z, tim)
            elif yy == y + 2 and hash2(x, z, seed) < 0.8:
                w.set(x, yy, z, TK.LEADED)
            else:
                w.set(x, yy, z, pl)
        w.set(x, y, z, B.log("dark_oak", "x" if along == "z" else "z"))
    for (x, z) in sides:
        if not _solid(w, x, y + 1, z):
            for yy in range(y + 1, y + 4):
                w.set(x, yy, z, pl if (x, z) not in faces else w.get(x, yy, z))
    # roof: gable with the ridge along the alley's direction of travel ... across the span
    roof = TK.ROOFS["blue"] if hash2(x0, z0, seed + 1) < 0.6 else TK.ROOFS["red"]
    if along == "z":
        for z in range(z0 - 1, z1 + 2):
            w.set(x0 - 0, y + 4, z, B.stairs(roof, "east"))
            w.set(x1, y + 4, z, B.stairs(roof, "west"))
            for x in range(x0 + 1, x1):
                w.set(x, y + 4, z, F(roof) if x1 - x0 > 2 else B.slab(roof, "bottom"))
                if x1 - x0 > 2:
                    w.set(x, y + 5, z, B.slab(roof, "bottom"))
    else:
        for x in range(x0 - 1, x1 + 2):
            w.set(x, y + 4, z0, B.stairs(roof, "south"))
            w.set(x, y + 4, z1, B.stairs(roof, "north"))
            for z in range(z0 + 1, z1):
                w.set(x, y + 4, z, F(roof) if z1 - z0 > 2 else B.slab(roof, "bottom"))
    # soffit: upside-down stairs along the two edges the alley passes, a lantern in the middle
    if along == "z":
        for z in (z0, z1):
            for x in range(x0, x1 + 1):
                w.set(x, y - 1, z, B.stairs("dark_oak", "south" if z == z0 else "north", "top"))
    else:
        for x in (x0, x1):
            for z in range(z0, z1 + 1):
                w.set(x, y - 1, z, B.stairs("dark_oak", "east" if x == x0 else "west", "top"))
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    w.set(cx, y - 1, cz, B.lantern(hanging=True))


def well(w, x, z, seed=0):
    """A stone well with a little roof, a bucket chain and a lantern."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                w.set(x, 0, z, B.S("water"))
                w.set(x, -1, z, B.S("water"))
                continue
            w.set(x + dx, 1, z + dz, B.wall("cobblestone") if (dx and dz) else B.slab("stone_bricks", "bottom"))
    for (dx, dz) in ((-1, -1), (1, 1)):
        w.fill(x + dx, 2, z + dz, x + dx, 3, z + dz, B.fence("spruce"))
    w.set(x - 1, 4, z - 1, B.stairs("spruce", "south"))
    w.set(x + 1, 4, z + 1, B.stairs("spruce", "north"))
    w.set(x, 4, z, B.slab("spruce", "bottom"))
    w.set(x - 1, 4, z + 1, B.slab("spruce", "bottom"))
    w.set(x + 1, 4, z - 1, B.slab("spruce", "bottom"))
    w.set(x, 3, z, B.S("chain", axis="y"))


def lane_life(w, cells, seed=0, y0=0):
    """Dress alley cells (list of (x, z) on the floor at y0): torches and ivy on the walls beside them, flower
    pots and barrels against the walls (never two in a row, so a 3-wide lane keeps 2 free), now and then a
    hanging shop sign."""
    cellset = set(cells)
    for i, (x, z) in enumerate(cells):
        u = hash2(x, z, seed)
        walls = [d for d, (dx, dz) in STEP.items()
                 if (x + dx, z + dz) not in cellset and _solid(w, x + dx, y0 + 2, z + dz)]
        if not walls:
            continue
        near_door = any("door" in w.get(x + ddx, y0 + 1, z + ddz) for ddx, ddz in STEP.values())
        d = walls[int(u * len(walls)) % len(walls)]
        dx, dz = STEP[d]
        out = OPP[d]
        if u < 0.08 and _free(w, x, y0 + 3, z):
            w.set(x, y0 + 3, z, B.S("wall_torch", facing=out))
        elif u < 0.2:
            # ivy climbing the wall beside the lane
            h = 3 + int(hash2(z, x, seed + 1) * 5)
            for yy in range(y0 + 2, y0 + 2 + h):
                if _free(w, x, yy, z) and _solid(w, x + dx, yy, z + dz):
                    w.set(x, yy, z, B.S("vine", **{d: "true"}))
                else:
                    break
        elif u < 0.27 and not near_door and _free(w, x, y0 + 1, z) and _free(w, x, y0 + 2, z):
            w.set(x, y0 + 1, z, B.potted(("red_tulip", "fern", "azalea_bush", "blue_orchid", "allium",
                                          "flowering_azalea_bush")[int(hash2(x, z, seed + 2) * 6)]))
        elif u < 0.33 and not near_door and _free(w, x, y0 + 1, z):
            w.set(x, y0 + 1, z, B.S("barrel", facing="up"))
            if hash2(x, z, seed + 3) < 0.4:
                w.set(x, y0 + 2, z, B.potted("cactus") if hash2(x, z, seed + 4) < 0.3 else B.candle(2, "white"))
        elif u < 0.37 and _free(w, x, y0 + 3, z):
            shop_sign(w, x, y0 + 3, z, out, ICONS[int(hash2(x, z, seed + 5) * len(ICONS))])


def strings(w, cells, axis_across, seed=0, y0=0, every=9):
    """Washing lines and pennant strings across an alley: from the air cell in front of one wall to the one in
    front of the opposite wall, at the first upper floor."""
    done = 0
    for i, (x, z) in enumerate(sorted(cells)):
        if i % every:
            continue
        dx, dz = (1, 0) if axis_across == "x" else (0, 1)
        # walk to both walls
        a = (x, z)
        while (a[0] - dx, a[1] - dz) in cells:
            a = (a[0] - dx, a[1] - dz)
        b = (x, z)
        while (b[0] + dx, b[1] + dz) in cells:
            b = (b[0] + dx, b[1] + dz)
        span = abs(b[0] - a[0]) + abs(b[1] - a[1])
        if span < 1:
            continue
        y = y0 + 6 + int(hash2(x, z, seed) * 2)
        if not (_free(w, a[0], y, a[1]) and _solid(w, a[0] - dx, y, a[1] - dz)):
            continue
        style = "laundry" if hash2(x, z, seed + 1) < 0.45 else "pennant"
        colors = PENNANTS[int(hash2(z, x, seed + 2) * len(PENNANTS))]
        bunting(w, a[0], y, a[1], (b[0] - a[0], 0, b[1] - a[1]), style=style, colors=colors, sag=0.6 + span * 0.12,
                facing="east" if axis_across == "x" else "south")
        done += 1
    return done


# ------------------------------------------------------------------------------------------------ quarter
def quarter(w, rect, seed, a1, a2, b, aw=3, inset=None, floors=None, posh=False, roofs=None, bridges=2,
            y0=0):
    """Fill the street block ``rect`` = (x0, z0, x1, z1) with an intricate quarter. The north-south lane runs at
    x a1..a1+aw-1 north of the cross lane and at a2..a2+aw-1 south of it; the cross lane runs at z b..b+aw-1.
    ``inset``: pavement width kept on each street side {'north': 2, ...} (default 2 all round)."""
    x0, z0, x1, z1 = rect
    ins = {"north": 2, "south": 2, "east": 2, "west": 2}
    ins.update(inset or {})
    TK.pave(w, x0, z0, x1, z1, TK.LANE_MIX, seed=seed)
    lo, hi = min(a1, a2), max(a1, a2) + aw - 1
    lanes = set()
    for z in range(z0, b):
        for x in range(a1, a1 + aw):
            lanes.add((x, z))
    for z in range(b + aw, z1 + 1):
        for x in range(a2, a2 + aw):
            lanes.add((x, z))
    for z in range(b, b + aw):
        for x in range(x0, x1 + 1):
            lanes.add((x, z))
    # the dog-leg square at the crossing: trim the sub-block corners next to it
    sq = (lo - 2, b - 2, hi + 2, b + aw + 1)
    subs = {
        "nw": (x0 + ins["west"], z0, a1 - 1, b - 1),
        "ne": (a1 + aw, z0, x1 - ins["east"], b - 1),
        "sw": (x0 + ins["west"], b + aw, a2 - 1, z1),
        "se": (a2 + aw, b + aw, x1 - ins["east"], z1),
    }
    built = []
    for k, (sx0, sz0, sx1, sz1) in subs.items():
        if sx1 - sx0 < 4 or sz1 - sz0 < 6:
            continue
        north_street = k in ("nw", "ne")
        depth = sz1 - sz0 + 1
        dn = depth // 2
        # rows: the street-side row keeps its pavement inset, the lane-side row hugs the lane
        top = (sx0, sz0, sx1, sz0 + dn - 1)
        bot = (sx0, sz0 + dn, sx1, sz1)
        # shorten the rows that touch the crossing square so the square opens up
        def trim(r, at_south):
            rx0, rz0, rx1, rz1 = r
            touches = (rz1 >= sq[1] and rz0 <= sq[3]) if at_south else (rz0 <= sq[3] and rz1 >= sq[1])
            if not touches:
                return r
            if rx1 >= sq[0] and rx0 < sq[0]:
                rx1 = min(rx1, sq[0] - 1)
            if rx0 <= sq[2] and rx1 > sq[2]:
                rx0 = max(rx0, sq[2] + 1)
            return (rx0, rz0, rx1, rz1)
        if north_street:
            top_front, top_inset = "north", ins["north"]
            bot_front, bot_inset = "south", 0
            bot = trim(bot, True)
        else:
            top_front, top_inset = "north", 0
            bot_front, bot_inset = "south", ins["south"]
            top = trim(top, False)
        for (r, fr, inset_, sd) in ((top, top_front, top_inset, 1), (bot, bot_front, bot_inset, 2)):
            if r[2] - r[0] < 4 or r[3] - r[1] < 5:
                continue
            out = TK.row(w, r[0], r[1], r[2], r[3], fr, seed=seed * 10 + sd + len(built) * 3, inset=inset_,
                         end_gap=(0, 0), min_w=5, max_w=8, posh=posh, floors=floors, roofs=roofs)
            built.extend(out)
    # the square: well, a cherry tree in a planter, a bench, a lamp
    cx = (sq[0] + sq[2]) // 2
    cz = (sq[1] + sq[3]) // 2
    well(w, cx, cz, seed)
    K.lamp(w, sq[0] + 1, 0, sq[3] - 1, height=3)
    if hash2(cx, cz, seed) < 0.6:
        K.planter_tree(w, sq[2] - 1, 0, sq[1] + 1, kind="cherry", seed=seed)
    # bridges over the lanes (rooms between facing houses), lane life, strings
    nl = sorted((x, z) for (x, z) in lanes if z < b)
    sl = sorted((x, z) for (x, z) in lanes if z >= b + aw)
    if bridges >= 1 and len(nl) > 12:
        bz = z0 + 3 + int(hash2(seed, 1, 77) * max(1, (b - z0 - 9)))
        alley_bridge(w, a1, bz, a1 + aw - 1, bz + 2, 4, "z", seed)
    if bridges >= 2 and len(sl) > 12:
        bz = b + aw + 3 + int(hash2(seed, 2, 77) * max(1, (z1 - b - aw - 9)))
        alley_bridge(w, a2, bz, a2 + aw - 1, bz + 2, 4, "z", seed + 1)
    if bridges >= 3:
        bx = x0 + 6 + int(hash2(seed, 3, 77) * max(1, (lo - x0 - 12)))
        alley_bridge(w, bx, b, bx + 2, b + aw - 1, 4, "x", seed + 2)
    lane_cells = sorted(lanes)
    lane_life(w, lane_cells, seed=seed + 5)
    strings(w, set(nl), "x", seed=seed + 6, every=7)
    strings(w, set(sl), "x", seed=seed + 7, every=7)
    cross = set((x, z) for (x, z) in lanes if b <= z < b + aw)
    strings(w, cross, "z", seed=seed + 8, every=11)
    return built
