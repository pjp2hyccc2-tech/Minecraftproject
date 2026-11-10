"""古代遺跡 (ancient ruins, Lv.30-40): the ruined temple complex on the levelled hilltop.

The great temple north of the volcano cave gate (d6): a stylobate with its great stair, a broken peristyle of
columns with a few lintels still standing, the collapsed walls of the cella, a toppled statue; a processional way
between broken colonnades from the plaza in front of the gate (whose approach stays open) to the temple stair; a
collapsed triumphal arch where the road arrives; a ruined rotunda south of the gate; moss, vines and rubble.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2, hash3

from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S

L = probe.layout()
RX, RZ, RTOP = L["ruins"]
D6 = next(g for g in L["landmarks"] if g["id"] == "d6")
STONE = [("stone_bricks", 40), ("mossy_stone_bricks", 22), ("cracked_stone_bricks", 18), ("smooth_stone", 8),
         ("chiseled_stone_bricks", 4), ("andesite", 8)]
FLOOR = [("stone_bricks", 40), ("mossy_stone_bricks", 18), ("cracked_stone_bricks", 18), ("smooth_stone", 10),
         ("polished_andesite", 8), ("andesite", 6)]


def stone(P, x, y, z, seed=0, mix=STONE):
    K.mixed(P, x, y, z, mix, seed)


def column(P, x, y, z, h, full_h=10, seed=0, capital=True, fallen_dir=None, base=True):
    """A plus-shaped column standing on the block at y: square base, round-ish shaft, capital if it is whole.
    ``h`` is the standing height (< full_h: broken); the rest lies toppled towards ``fallen_dir``."""
    if base:
        P.fill(x - 1, y + 1, z - 1, x + 1, y + 1, z + 1, F("stone_bricks"))
        for (a, b) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            P.set(x + a, y + 2, z + b, B.slab("stone_bricks", "bottom"))
    for k in range(2, h + 1):
        for (a, b) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            st = F("smooth_stone") if (a or b) else F("stone_bricks")
            if hash3(x + a, y + k, z + b, seed) < 0.2:
                st = F("mossy_stone_bricks")
            if (a or b) and k == h and h < full_h and hash3(x + a, k, z + b, seed + 1) < 0.45:
                continue                                       # a ragged break
            P.set(x + a, y + k, z + b, st)
    if h >= full_h and capital:
        y2 = y + h + 1
        P.fill(x - 1, y2, z - 1, x + 1, y2, z + 1, F("chiseled_stone_bricks"))
        for d in ("north", "south", "east", "west"):
            dx, dz = K.DIRS[d]
            P.set(x + dx * 2, y2, z + dz * 2, B.stairs("stone_bricks", K.OPP[d], "top"))
        return y2
    if fallen_dir and h < full_h - 2:
        # the broken-off drums lie on the ground beyond the base
        dx, dz = K.DIRS[fallen_dir]
        for k in range(full_h - h - 1):
            fx, fz = x + dx * (3 + k), z + dz * (3 + k)
            if not P.inside(fx, y + 1, fz) or P.wet(fx, fz):
                break
            gy = K.ground_y(P, fx, fz)
            if gy > y + 1 or P.get_idx(fx, gy + 1, fz) > 1:
                break
            P.set(fx, gy + 1, fz, F("smooth_stone") if k % 3 else F("chiseled_stone_bricks"))
            if hash2(fx, fz, seed) < 0.5:
                P.set(fx + (dz != 0), gy + 1, fz + (dx != 0), B.slab("smooth_stone", "bottom"))
    return None


def statue_fallen(P, x, z, direction, seed=0):
    """A toppled giant statue: head, torso and an arm lying on the ground, the empty plinth beside them."""
    dx, dz = K.DIRS[direction]
    sx, sz = K.DIRS[K.CW[direction]]
    y = K.ground_y(P, x, z)
    P.fill(x - 1, y + 1, z - 1, x + 1, y + 2, z + 1, F("chiseled_stone_bricks"))
    P.set(x, y + 3, z, F("smooth_stone"))
    for k in range(3, 10):
        px, pz = x + dx * k, z + dz * k
        gy = K.ground_y(P, px, pz)
        for s in (-1, 0, 1):
            if k >= 8 and s:
                continue
            P.set(px + sx * s, gy + 1, pz + sz * s, F("smooth_stone") if k < 8 else F("chiseled_stone_bricks"))
            if k < 7 and s == 0:
                P.set(px, gy + 2, pz, F("smooth_stone"))
    hx, hz = x + dx * 11, z + dz * 11
    gy = K.ground_y(P, hx, hz)
    P.fill(hx - 1, gy + 1, hz - 1, hx + 1, gy + 2, hz + 1, F("smooth_stone"))
    P.set(hx, gy + 3, hz, B.slab("smooth_stone", "bottom"))
    ax, az = x + dx * 5 + sx * 3, z + dz * 5 + sz * 3
    for k in range(4):
        gy = K.ground_y(P, ax + sx * k, az + sz * k)
        P.set(ax + sx * k, gy + 1, az + sz * k, F("smooth_stone"))


def rubble(P, mask, seed=0, density=0.06):
    for (x, z) in K.cells(mask, P):
        r = hash2(x, z, seed)
        if P.wet(x, z):
            continue
        if r < density:
            y = K.ground_y(P, x, z)
            if P.get_idx(x, y + 1, z) > 1:
                continue
            st =(B.slab("cobblestone", "bottom"), F("mossy_cobblestone"), B.slab("stone_bricks", "bottom"),
                  B.stairs("stone_bricks", "north"), F("cracked_stone_bricks"))[int(r / density * 5) % 5]
            P.set(x, y + 1, z, st)
        elif r < density * 2.5:
            y = K.plantable(P, x, z)
            if y is not None:
                P.set(x, y + 1, z, S("grass") if r < density * 2 else S("fern"))


def _vine_host(st):
    if not st.startswith("minecraft:"):
        return False
    name = st.split("[", 1)[0]
    return ("stone" in name or "brick" in name) and not any(k in name for k in ("slab", "stairs", "wall", "button",
                                                                              "pressure"))


def vines(P, x0, y0, z0, x1, y1, z1, seed=0, density=0.25):
    """Vines on the faces of the piece's stonework in a box, some hanging down a few blocks."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y1, y0 - 1, -1):
                if not P.is_empty(x, y, z) or P.get(x, y, z).startswith("minecraft:vine"):
                    continue
                for d, (dx, dz) in K.DIRS.items():
                    if not _vine_host(P.get(x + dx, y, z + dz)) or hash3(x, y, z, seed) >= density:
                        continue
                    v = B.S("vine", **{d: True})
                    P.set(x, y, z, v)
                    k = 1
                    while k < 4 and P.is_empty(x, y - k, z) and hash3(x, y - k, z, seed + 1) < 0.55:
                        P.set(x, y - k, z, v)
                        k += 1
                    break


# ====================================================================================== the great temple
def temple():
    x0, z0, x1, z1 = 1276, -268, 1326, -214         # stylobate
    P = Piece("ruins_temple", "古代神殿跡 (the great temple: stylobate, peristyle, cella, statue)", 5, x0 - 14, z0 - 14,
              x1 + 14, z1 + 16, below=10, above=30, kind="scenery")
    y = int(np.percentile([P.top(x, z) for x in range(x0, x1 + 1, 3) for z in range(z0, z1 + 1, 3)], 60))
    top = y + 3                                         # floor of the temple
    plat = K.rect(P, x0, z0, x1, z1)
    for (x, z) in K.cells(plat, P):
        t = P.top(x, z)
        for yy in range(min(t, y) - 1, top):
            stone(P, x, yy, z, 1)
        K.mixed(P, x, top, z, FLOOR, 2)
        P.fill(x, top + 1, z, x, max(t, top) + 3, z, AIR)
    # cornice of stairs round the stylobate
    for (x, z) in K.cells(K.ring(plat), P):
        if hash2(x, z, 3) < 0.85:
            P.set(x, top, z, F("smooth_stone"))
    K.blend(P, plat | K.dilate(plat, 1), y, width=6)
    # the great stair on the south front, full width of the cella
    cxm = (x0 + x1) // 2
    for i in range(1, 5):
        for x in range(cxm - 9, cxm + 10):
            zz = z1 + i
            yy = top - i + 1
            P.fill(x, min(P.top(x, zz), yy) - 1, zz, x, yy - 1, zz, F("stone_bricks"))
            if hash2(x, i, 5) < 0.12:
                P.set(x, yy, zz, B.slab("cobblestone", "bottom"))
            else:
                P.set(x, yy, zz, B.stairs("stone_bricks", "north"))
            P.fill(x, yy + 1, zz, x, yy + 3, zz, AIR)
    for side in (-10, 10):
        for i in range(1, 5):
            x, zz = cxm + side, z1 + i
            P.fill(x, P.top(x, zz), zz, x, top - i + 2, zz, F("stone_bricks"))
        P.set(cxm + side, top + 1, z1 + 1, F("chiseled_stone_bricks"))
    # the peristyle: columns round the edge, many broken, a few lintels left
    cols = []
    for x in range(x0 + 2, x1 - 1, 6):
        cols += [(x, z0 + 2), (x, z1 - 2)]
    for z in range(z0 + 8, z1 - 5, 6):
        cols += [(x0 + 2, z), (x1 - 2, z)]
    # the south front keeps a portico: whole columns with their entablature and a broken pediment
    front = {1278: 3, 1284: 10, 1290: 10, 1296: 10, 1302: 5, 1308: 10, 1314: 10, 1320: 7}
    standing = {}
    for k, (x, z) in enumerate(cols):
        r = hash2(x, z, 11)
        h = front.get(x, 10) if z == z1 - 2 else (10 if r < 0.35 else (2 + int(r * 7)))
        # drums fall outwards off the stylobate or inwards onto its floor, never down the great stair
        out = "south" if z == z1 - 2 else "north" if z == z0 + 2 else ("west" if x == x0 + 2 else "east")
        side_dir = K.OPP[out] if out == "south" or hash2(x, z, 12) < 0.4 else out
        cap = column(P, x, top, z, h, 10, seed=k, fallen_dir=side_dir)
        if cap:
            standing[(x, z)] = cap
    # lintels between neighbouring whole columns
    for (x, z), cy in standing.items():
        for (dx, dz) in ((6, 0), (0, 6)):
            if (x + dx, z + dz) in standing:
                for k in range(0, 7):
                    px, pz = x + dx * k // 6, z + dz * k // 6
                    P.set(px, cy + 1, pz, F("stone_bricks") if k % 3 else F("chiseled_stone_bricks"))
                    if dz == 0 and z == z1 - 2:
                        continue
                    if hash2(px, pz, 4) < 0.6:
                        P.set(px, cy + 2, pz, B.slab("smooth_stone", "bottom"))
    # the front entablature (frieze and cornice) over the standing runs, and what is left of the pediment
    zf = z1 - 2
    cy = top + 11
    runs = [(1284, 1296), (1308, 1314)]
    for (a, b) in runs:
        for x in range(a - 1, b + 2):
            for zz in (zf - 1, zf, zf + 1):
                P.set(x, cy + 1, zz, F("stone_bricks"))
                P.set(x, cy + 2, zz, F("chiseled_stone_bricks") if (x - a) % 3 == 0 else F("smooth_stone"))
            P.set(x, cy + 3, zf + 2, B.stairs("stone_bricks", "north", "top"))
            P.set(x, cy + 3, zf + 1, F("stone_bricks"))
            P.set(x, cy + 3, zf, F("stone_bricks"))
            P.set(x, cy + 3, zf - 1, F("stone_bricks"))
    # the pediment rose from the west corner; it breaks off above the fourth column
    for x in range(1283, 1301):
        hgt = (x - 1283) // 2
        if x > 1297:
            hgt = min(hgt, int(4 + 4 * hash2(x, 1, 13)))
        for k in range(hgt):
            P.set(x, cy + 4 + k, zf, F("stone_bricks") if hash2(x, k, 14) < 0.8 else F("cracked_stone_bricks"))
            P.set(x, cy + 4 + k, zf - 1, F("stone_bricks"))
        if x <= 1297:
            P.set(x, cy + 4 + hgt, zf, B.stairs("stone_bricks", "east"))
            P.set(x, cy + 4 + hgt, zf + 1, B.slab("smooth_stone", "bottom") if hgt == 0 else
                  B.stairs("stone_bricks", "east"))
    P.set(1290, cy + 5, zf + 1, F("chiseled_stone_bricks"))
    # the tympanum fallen at the foot of the stair corner
    for k in range(5):
        gx, gz = 1316 + k % 3, z1 + 3 + k // 3
        gy = K.ground_y(P, gx, gz)
        P.set(gx, gy + 1, gz, F("chiseled_stone_bricks") if k == 2 else F("stone_bricks"))
    # the cella: walls in ruins (random heights), its door to the south, the inner sanctum
    c0x, c0z, c1x, c1z = x0 + 9, z0 + 10, x1 - 9, z1 - 9
    for x in range(c0x, c1x + 1):
        for z in range(c0z, c1z + 1):
            edge = x in (c0x, c1x) or z in (c0z, c1z)
            if not edge:
                continue
            if z == c1z and abs(x - cxm) <= 2:
                continue                                   # the doorway
            h = int(2 + 9 * max(0.0, math.sin(x * 0.37 + z * 0.21) * 0.5 + hash2(x, z, 6) * 0.6))
            for yy in range(top + 1, top + 1 + h):
                stone(P, x, yy, z, 7)
    for yy in range(top + 1, top + 9):
        for x in (cxm - 3, cxm + 3):
            P.set(x, yy, c1z, F("chiseled_stone_bricks") if yy % 3 == 0 else F("stone_bricks"))
    for x in range(cxm - 3, cxm + 4):
        P.set(x, top + 9, c1z, F("stone_bricks") if hash2(x, 1, 2) < 0.8 else AIR)
    # inside: the altar, broken floor, the fallen statue of the old god, moss
    P.fill(cxm - 2, top + 1, c0z + 4, cxm + 2, top + 1, c0z + 6, F("chiseled_stone_bricks"))
    P.fill(cxm - 1, top + 2, c0z + 5, cxm + 1, top + 2, c0z + 5, B.slab("smooth_stone", "bottom"))
    P.set(cxm, top + 2, c0z + 5, B.candle(3, "orange"))
    statue_fallen(P, cxm - 6, c0z + 6, "south", seed=3)
    rubble(P, K.rect(P, c0x + 1, c0z + 1, c1x - 1, c1z - 1), seed=8, density=0.08)
    for (x, z) in K.cells(K.rect(P, c0x + 1, c0z + 1, c1x - 1, c1z - 1), P):
        if hash2(x, z, 9) < 0.12:
            P.set(x, top, z, S("moss_block"))
            if hash2(z, x, 9) < 0.5:
                P.set(x, top + 1, z, S("moss_carpet"))
    # braziers at the stair head
    for side in (-6, 6):
        P.set(cxm + side, top + 1, z1 - 1, F("chiseled_stone_bricks"))
        P.set(cxm + side, top + 2, z1 - 1, S("campfire", facing="south", lit=True))
    vines(P, x0 - 1, top, z0 - 1, x1 + 1, top + 12, z1 + 1, seed=12, density=0.12)
    rubble(P, K.rect(P, x0 - 10, z0 - 10, x1 + 10, z1 + 12) & ~plat, seed=13, density=0.012)
    P.probe(x0, top - 1, z0)
    P.view("ruins_temple", (x1 + 40, top + 34, z1 + 46), ((x0 + x1) / 2, top + 3, (z0 + z1) / 2), fov=55)
    return P


# ====================================================================================== processional way
def processional():
    """The plaza before the cave gate (paved in broken flagstones; the gate's approach stays open), a processional
    way from it along the north of the gate to the temple stair, a broken colonnade along the way."""
    x0, z0, x1, z1 = 1224, -214, 1334, -140
    P = Piece("ruins_way", "参道と広場の跡 (plaza before the cave gate, processional way, colonnade)", 5, x0, z0,
              x1, z1, below=8, above=24, kind="scenery")
    gate = K.disc(P, D6["x"], D6["z"], 23)
    approach = K.rect(P, D6["x"] - 75, D6["z"] - 7, D6["x"] - 20, D6["z"] + 7)
    shrine = K.disc(P, 1230, -132, 15)
    way = K.rect(P, 1240, -201, 1309, -195) | K.rect(P, 1293, -209, 1309, -195)
    plaza = K.rect(P, 1236, -194, 1278, -146)
    free = ~gate & ~shrine
    for (x, z) in K.cells((way | plaza) & free, P):
        if not way[x - P.X0, z - P.Z0] and (hash2(x // 3, z // 3, 20) < 0.2 or hash2(x, z, 24) < 0.08):
            continue                                     # grass grows through the broken plaza
        K.mixed(P, x, P.top(x, z), z, FLOOR, 21)
    # the colonnade on the north side of the way (its drums fall away from the way)
    caps = {}
    for x in range(1242, 1292, 6):
        z = -204
        h = 10 if hash2(x, z, 1) < 0.4 else 2 + int(hash2(x, z, 2) * 7)
        caps[x] = column(P, x, K.ground_y(P, x, z), z, h, 10, seed=x, fallen_dir="north")
    # lintel fragments on neighbouring whole columns
    for x in range(1242, 1286, 6):
        if caps.get(x) and caps.get(x + 6):
            cy = min(caps[x], caps[x + 6])
            for k in range(7):
                P.set(x + k, cy + 1, -204, F("stone_bricks") if k % 3 else F("chiseled_stone_bricks"))
    # the east side of the way, beside the temple stair
    for z in (-207,):
        for x in (1290, 1312):
            column(P, x, K.ground_y(P, x, z), z, 10 if x == 1312 else 5, 10, seed=z + x, fallen_dir="east" if
                   x == 1312 else "west")
    # low stumps round the plaza (nothing tall near the gate or its approach)
    for x in range(1240, 1276, 9):
        for z in (-150,):
            if (K.rect(P, x - 1, z - 1, x + 1, z + 1) & (gate | approach | shrine)).any():
                continue
            yy = K.ground_y(P, x, z)
            P.fill(x - 1, yy + 1, z - 1, x + 1, yy + 1, z + 1, F("stone_bricks"))
            P.set(x, yy + 2, z, F("smooth_stone"))
            if hash2(x, z, 6) < 0.5:
                P.set(x, yy + 3, z, B.slab("smooth_stone", "bottom"))
    for z in range(-188, -150, 9):
        x = 1238
        if (K.rect(P, x - 1, z - 1, x + 1, z + 1) & (gate | approach | shrine)).any():
            continue
        yy = K.ground_y(P, x, z)
        P.fill(x - 1, yy + 1, z - 1, x + 1, yy + 1, z + 1, F("stone_bricks"))
        if hash2(x, z, 7) < 0.6:
            P.set(x, yy + 2, z, F("mossy_stone_bricks"))
    # braziers marking the head of the plaza (beside, not in, the approach)
    for z in (-180, -160):
        x = 1262
        yy = K.ground_y(P, x, z)
        P.set(x, yy + 1, z, F("chiseled_stone_bricks"))
        P.set(x, yy + 2, z, S("campfire", facing="west", lit=True))
    rubble(P, K.rect(P, x0, z0, x1, z1) & ~approach & ~gate & ~shrine & ~way, seed=22, density=0.02)
    vines(P, 1236, 117, -208, 1314, 130, -200, seed=23, density=0.15)
    K.place_sign(P, 1244, K.ground_y(P, 1244, -146) + 1, -146,
                 ['{"translate":"sign.eternalrealm.temple","bold":true}', K.field_level(5)], wood="dark_oak",
                 rotation=0)
    P.probe(1242, K.ground_y(P, 1242, -204) + 1, -204)
    P.view("ruins_way", (1210, 150, -120), (1280, 117, -190), fov=60)
    return P


def arch():
    """The collapsed triumphal arch where the road climbs onto the temple hill: two great piers, the northern half
    of the vault and its attic still standing, the keystone and the southern half lying beside the road."""
    ax, az = 1195, -108                     # the road runs through it roughly west-east
    P = Piece("ruins_arch", "凱旋門の跡 (collapsed triumphal arch on the road)", 5, ax - 14, az - 14, ax + 14,
              az + 18, below=8, above=24, kind="scenery")
    road = K.polyline_mask(P, next(r for r in L["roads"] if r["id"] == "east")["points"], 3.5)
    yy = min(K.ground_y(P, ax + a, az + s) for s in range(-7, 8) for a in (-1, 0, 1))
    spring = yy + 8
    for zc, h in ((az - 6, 17), (az + 6, 8)):
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                x, z = ax + a, zc + b
                for k in range(min(P.top(x, z), yy) - 1, yy + h + 1):
                    stone(P, x, k, z, 31)
        for a in range(-2, 3):
            for b in range(-2, 3):
                if max(abs(a), abs(b)) != 2:
                    continue
                x, z = ax + a, zc + b
                P.fill(x, min(P.top(x, z), yy) - 1, z, x, yy, z, F("stone_bricks"))
                P.set(x, yy + 1, z, B.stairs("stone_bricks", K.OPP[K.facing_of(a, b)]) if abs(a) != abs(b) else
                      B.slab("stone_bricks", "bottom"))
        for a in (-2, 2):                     # impost mouldings at the springing
            for b in (-1, 0, 1):
                if h > 8:
                    P.set(ax + a, spring, zc + b, B.stairs("stone_bricks", "west" if a > 0 else "east", "top"))
    # the northern half of the vault (the opening is az-4..az+4), the attic above the north pier
    intrados = {4: 10, 3: 11, 2: 12, 1: 12}
    for d, hi in intrados.items():
        z = az - d
        for a in (-1, 0, 1):
            P.set(ax + a, yy + hi, z, B.stairs("stone_bricks", "north", "top"))
            for k in range(yy + hi + 1, yy + 18):
                stone(P, ax + a, k, z, 33)
    for a in (-1, 0, 1):
        for z in range(az - 8, az):
            P.set(ax + a, yy + 18, z, B.slab("smooth_stone", "bottom") if hash2(a, z, 5) < 0.75 else AIR)
        P.set(ax + a, yy + 13, az - 1, F("chiseled_stone_bricks"))
    for z in range(az - 8, az):
        for a in (-2, 2):
            P.set(ax + a, yy + 17, z, B.stairs("stone_bricks", "west" if a > 0 else "east", "top"))
    # the fallen half and the keystone lie south of the road
    pieces = [(ax - 2, az + 11, 3, 2), (ax + 2, az + 13, 2, 2), (ax - 5, az + 14, 2, 1), (ax + 5, az + 10, 1, 1)]
    for (px, pz, w, hh) in pieces:
        for a in range(w):
            for c in range(w):
                x, z = px + a, pz + c
                if road[x - P.X0, z - P.Z0] or P.wet(x, z):
                    continue
                gy = K.ground_y(P, x, z)
                for k in range(hh):
                    stone(P, x, gy + 1 + k, z, 35)
    P.set(ax, K.ground_y(P, ax, az + 16) + 1, az + 16, F("chiseled_stone_bricks"))
    vines(P, ax - 3, yy + 2, az - 9, ax + 3, yy + 18, az + 9, seed=4, density=0.2)
    rubble(P, K.disc(P, ax, az, 12) & ~road, seed=32, density=0.03)
    P.probe(ax, yy + 3, az - 6)
    P.view("ruins_arch", (ax - 30, yy + 13, az + 9), (ax, yy + 9, az - 1), fov=55)
    return P


def rotunda():
    """A ruined round temple south of the gate: a round podium, a ring of slender columns of which a third still
    carry the ring beam and a fragment of the dome, an altar with an old pot in the middle."""
    cx, cz = 1300, -112
    P = Piece("ruins_rotunda", "円堂の跡 (ruined rotunda)", 5, cx - 16, cz - 16, cx + 16, cz + 16, below=8, above=24,
              kind="scenery")
    y = P.median_top(cx - 9, cz - 9, cx + 9, cz + 9)
    podium = K.disc(P, cx, cz, 9.5)
    for (x, z) in K.cells(podium, P):
        t = P.top(x, z)
        for yy in range(min(t, y) - 1, y + 1):
            stone(P, x, yy, z, 41)
        K.mixed(P, x, y + 1, z, FLOOR, 42)
        P.fill(x, y + 2, z, x, max(t, y) + 3, z, AIR)
    for (x, z) in K.cells(K.ring(podium), P):
        P.set(x, y + 1, z, B.slab("smooth_stone", "bottom"))
    K.blend(P, podium, y + 1, width=5)
    n = 12
    whole = {0, 1, 2, 3, 4}
    for k in range(n):
        a = 2 * math.pi * k / n
        x, z = cx + round(7 * math.cos(a)), cz + round(7 * math.sin(a))
        h = 7 if k in whole else int(1 + hash2(k, 3, 1) * 4)
        P.set(x, y + 2, z, F("chiseled_stone_bricks"))
        for yy in range(y + 3, y + 3 + h):
            P.set(x, yy, z, F("smooth_stone") if hash3(x, yy, z, 2) < 0.8 else F("mossy_stone_bricks"))
        if k in whole:
            P.set(x, y + 10, z, F("chiseled_stone_bricks"))
        elif h < 4:
            fx, fz = x + round(2 * math.cos(a)), z + round(2 * math.sin(a))
            gy = K.ground_y(P, fx, fz)
            P.set(fx, gy + 1, fz, F("smooth_stone"))
    # ring beam over the whole columns, dome rings stepping in above it
    a0, a1 = -0.15, 2 * math.pi * 4 / n + 0.15
    for r, yy, st in ((7, 11, F("stone_bricks")), (7, 12, B.slab("smooth_stone", "bottom")),
                      (6, 12, F("stone_bricks")), (5, 13, F("stone_bricks")), (4, 14, B.slab("stone_bricks", "bottom"))):
        sh = 0.18 * (7 - r)
        steps = int(60 * r)
        for i in range(steps + 1):
            a = a0 + sh + (a1 - a0 - 2 * sh) * i / steps
            P.set(cx + round(r * math.cos(a)), y + yy, cz + round(r * math.sin(a)), st)
    P.fill(cx - 1, y + 2, cz - 1, cx + 1, y + 2, cz + 1, F("chiseled_stone_bricks"))
    P.set(cx, y + 3, cz, B.S("decorated_pot", facing="north"))
    vines(P, cx - 9, y + 2, cz - 9, cx + 9, y + 14, cz + 9, seed=7, density=0.15)
    rubble(P, K.disc(P, cx, cz, 15) & ~K.dilate(podium, 1), seed=43, density=0.03)
    P.probe(cx, y + 2, cz)
    P.view("ruins_rotunda", (cx - 26, y + 20, cz + 26), (cx, y + 6, cz), fov=55)
    return P


def build(only=None):
    out = []
    for pid, fn in (("ruins_temple", temple), ("ruins_way", processional), ("ruins_arch", arch),
                    ("ruins_rotunda", rotunda)):
        if not only or pid in only:
            out.append(fn())
    return out
