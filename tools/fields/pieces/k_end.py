"""終焉の森 (end forest, Lv.50+): the land around the dark tower.

A ruined chapel of end stone and purpur beside the road, with its graveyard and a giant dead tree; outcrops of
crystal shards; groves of giant blackened trees; and glowing fissures in the ground that converge on the tower from
the south and east, stopping well short of its site (and of the approach to its gate).
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital.noise import hash2, hash3

from .. import build_kit as BK
from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S

L = probe.layout()
ROAD = next(r for r in L["roads"] if r["id"] == "end")
TX, TZ = int(L["tower"][0]), int(L["tower"][1])
D8 = next(g for g in L["landmarks"] if g["id"] == "d8")
LABELS = [(int(math.floor(p[0])) >> 4, int(math.floor(p[1])) >> 4) for p in L["labels"]]


def free_mask(P, road_half=3.5):
    """Columns a piece may change: off the road, out of the tower's site, its approach and the label chunks."""
    X, Z = K.grid(P)
    m = np.hypot(X - TX, Z - TZ) > 44
    m &= ~K.polyline_mask(P, ROAD["points"], road_half)
    m &= ~((X >= TX + 28) & (X <= TX + 80) & (np.abs(Z - TZ) <= 8))
    for (cx, cz) in LABELS:
        m &= ~(((X >> 4) == cx) & ((Z >> 4) == cz))
    return m


# ====================================================================================== crystals
def shard(P, x, z, h, lean, seed=0, r0=1.3):
    """A crystal shard rising from the ground at (x, z): amethyst core, purple glass skin, an end rod at the tip."""
    y = K.ground_y(P, x, z)
    tip = None
    for k in range(h):
        f = 1.0 - k / float(h)
        r = r0 * f + 0.35
        cx, cz = x + lean[0] * k, z + lean[1] * k
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                px, pz = int(round(cx)) + dx, int(round(cz)) + dz
                d = math.hypot(px - cx, pz - cz)
                if d > r:
                    continue
                st = F("amethyst_block") if d < r - 0.6 or hash3(px, k, pz, seed) < 0.55 else \
                    S("purple_stained_glass")
                P.set(px, y + 1 + k, pz, st)
                tip = (px, y + 1 + k, pz)
    if tip:
        P.set(tip[0], tip[1] + 1, tip[2], S("end_rod", facing="up"))
    for dx in range(-1, 2):                         # the shard's foot sunk in calcite and basalt
        for dz in range(-1, 2):
            if hash2(x + dx, z + dz, seed + 5) < 0.7:
                P.set(x + dx, K.ground_y(P, x + dx, z + dz), z + dz,
                      F("calcite") if (dx + dz) % 2 else F("smooth_basalt"))


def outcrop(pid, name, cx, cz, n=6, seed=0):
    P = Piece(pid, name, 7, cx - 14, cz - 14, cx + 14, cz + 14, below=6, above=24, kind="scenery")
    free = free_mask(P)
    placed = 0
    for i in range(n * 3):
        a = 2 * math.pi * hash2(i, seed, 1)
        r = 1.5 + 6.5 * hash2(i, seed, 2) ** 0.7
        x, z = int(round(cx + r * math.cos(a))), int(round(cz + r * math.sin(a)))
        if not free[x - P.X0, z - P.Z0] or P.wet(x, z):
            continue
        h = int(16 - 1.4 * r + 4 * hash2(i, seed, 3))
        lean = (0.09 * math.cos(a) * (1 + r / 4), 0.09 * math.sin(a) * (1 + r / 4))
        shard(P, x, z, max(4, h), lean, seed=seed + i, r0=1.5 if r < 3 else 1.1)
        placed += 1
        if placed >= n:
            break
    # small clusters and buds scattered round the outcrop
    for (x, z) in K.cells(K.disc(P, cx, cz, 12) & free, P):
        r = hash2(x, z, seed + 9)
        if r < 0.05:
            y = K.ground_y(P, x, z)
            if P.get_idx(x, y + 1, z) <= 1 and not P.wet(x, z):
                P.set(x, y + 1, z, S("amethyst_cluster", facing="up") if r < 0.025 else
                      S("large_amethyst_bud", facing="up"))
                P.set(x, y, z, F("calcite") if r < 0.015 else F("smooth_basalt"))
    P.probe(cx, K.ground_y(P, cx, cz), cz)
    P.view(pid, (cx + 26, K.ground_y(P, cx, cz) + 16, cz + 24), (cx, K.ground_y(P, cx, cz) + 6, cz), fov=55)
    return P


# ====================================================================================== giant dead trees
def _log_axis(dx, dy, dz):
    a = max((abs(dx), "x"), (abs(dy), "y"), (abs(dz), "z"))
    return a[1]


def dead_tree(P, x, z, h, seed=0, lanterns=True):
    """A giant blackened tree: a 2x2 trunk broken at the top, roots, crooked bare branches with twigs, a soul
    lantern or two hanging from them."""
    y = min(K.ground_y(P, x + a, z + b) for a in (0, 1) for b in (0, 1))
    wood = lambda k: B.log("dark_oak", "y") if hash2(k, seed, 3) < 0.7 else \
        B.log("dark_oak", "y", stripped=True)  # noqa: E731
    for a in (0, 1):
        for b in (0, 1):
            top = h - (0 if (a, b) == (0, 0) else int(hash2(a, b + seed, 4) * 3))
            for k in range(-1, top):
                P.set(x + a, y + 1 + k, z + b, wood(k + 3 * a + 7 * b))
    # roots
    for i, (ox, oz) in enumerate(((-1, 0), (1, 0), (0, -1), (0, 1), (1, 1), (-1, -1))):
        if hash2(i, seed, 5) < 0.3:
            continue
        rx, rz = x + (1 if ox > 0 else 0) + ox, z + (1 if oz > 0 else 0) + oz
        L_ = 2 + int(hash2(i, seed, 6) * 3)
        for k in range(L_):
            px, pz = rx + ox * k, rz + oz * k
            gy = K.ground_y(P, px, pz)
            P.set(px, gy + (1 if k < L_ - 1 else 0), pz, B.log("dark_oak", "x" if ox else "z"))
    # branches
    hanging = 0
    for i in range(4 + int(hash2(seed, 1, 7) * 3)):
        a = 2 * math.pi * (i / 5.0 + hash2(i, seed, 8) * 0.3)
        by = y + int(h * (0.45 + 0.4 * hash2(i, seed, 9)))
        dx, dz, dy = math.cos(a), math.sin(a), 0.35 + 0.3 * hash2(i, seed, 10)
        L_ = 4 + int(hash2(i, seed, 11) * 5)
        px, py, pz = x + 0.5 + dx * 1.2, by + 0.0, z + 0.5 + dz * 1.2
        last = None
        for k in range(L_):
            px += dx
            pz += dz
            py += dy
            cell = (int(math.floor(px)), int(math.floor(py)), int(math.floor(pz)))
            if cell != last:
                P.set(*cell, B.log("dark_oak", _log_axis(dx, dy, dz)))
                last = cell
        # twigs
        tx, ty, tz = last
        P.set(tx, ty + 1, tz, B.fence("dark_oak"))
        if hash2(i, seed, 12) < 0.6:
            P.set(tx + (1 if dx > 0 else -1), ty + 1, tz, B.fence("dark_oak"))
            P.set(tx + (1 if dx > 0 else -1), ty + 2, tz, B.fence("dark_oak"))
        if lanterns and hanging < 2 and hash2(i, seed, 13) < 0.5:
            mx, my, mz = cell
            P.set(mx, my - 1, mz, S("chain", axis="y"))
            P.set(mx, my - 2, mz, B.lantern(hanging=True, soul=True))
            hanging += 1


def deadwood():
    """Giant dead trees on both sides of the road south of the waystone (the road itself stays clear)."""
    x0, z0, x1, z1 = -1000, -580, -930, -490
    P = Piece("end_deadwood", "枯れた巨木の林 (grove of giant dead trees by the road)", 7, x0, z0, x1, z1, below=6,
              above=30, kind="scenery")
    free = free_mask(P, road_half=6.0)
    spots = [(-985, -560), (-962, -572), (-947, -540), (-990, -520), (-952, -505), (-975, -497), (-940, -566)]
    for i, (x, z) in enumerate(spots):
        if all(free[x + a - P.X0, z + b - P.Z0] for a in range(-3, 5) for b in range(-3, 5)):
            dead_tree(P, x, z, 13 + int(hash2(i, 4, 1) * 8), seed=40 + i)
    for (x, z) in K.cells(free, P):
        r = hash2(x, z, 77)
        if r < 0.012:
            y = K.ground_y(P, x, z)
            if P.get_idx(x, y + 1, z) <= 1 and not P.wet(x, z):
                P.set(x, y + 1, z, B.fence("dark_oak") if r < 0.006 else S("dead_bush"))
    P.probe(spots[0][0], K.ground_y(P, spots[0][0], spots[0][1]) + 2, spots[0][1])
    P.view("end_deadwood", (-920, 100, -470), (-965, 76, -535), fov=58)
    return P


# ====================================================================================== the ruined chapel
END_WALL = [("end_stone_bricks", 70), ("end_stone", 12), ("purpur_block", 6), ("polished_blackstone_bricks", 8),
            ("cracked_polished_blackstone_bricks", 4)]
FLOOR = [("polished_deepslate", 30), ("deepslate_tiles", 25), ("end_stone_bricks", 25), ("cracked_deepslate_tiles", 10),
         ("polished_blackstone", 10)]


def chapel():
    """The ruined chapel east of the road: nave with buttresses and pointed windows, the west front with its rose
    window and door facing the road, the apse with the altar, a broken bell tower, the graveyard to the south."""
    wx, ex = -1016, -996                 # the nave's west front and east end (walls)
    nz, sz = -712, -704                  # its north and south walls
    P = Piece("end_chapel", "崩れた礼拝堂と墓地 (ruined chapel and graveyard)", 7, -1042, -726, -982, -680, below=8,
              above=30, kind="scenery")
    free = free_mask(P)
    y = P.median_top(wx, nz, ex + 5, sz)
    BK.fit_ground(P, wx - 1, nz - 1, ex + 5, sz + 1, y, found="deepslate_bricks", blend=4)
    for x in range(wx, ex + 1):
        for z in range(nz, sz + 1):
            K.mixed(P, x, y, z, FLOOR, 1)
    cz = (nz + sz) // 2
    # walls: ruined to varying heights
    def wall_h(x, z):
        n = math.sin(x * 0.45 + z * 0.3) * 0.5 + hash2(x, z, 3) * 0.7
        return max(2, min(10, int(4 + 7 * n)))
    for x in range(wx, ex + 1):
        for z in range(nz, sz + 1):
            if x not in (wx, ex) and z not in (nz, sz):
                continue
            h = 13 if x == wx else wall_h(x, z)
            for yy in range(y + 1, y + 1 + h):
                K.mixed(P, x, yy, z, END_WALL, 2)
    # the west front: a gable, the rose window, the door (towards the road)
    for z in range(nz, sz + 1):
        gh = 13 + (4 - abs(z - cz))
        for yy in range(y + 1, y + gh + 1):
            K.mixed(P, wx, yy, z, END_WALL, 4)
        P.set(wx, y + gh + 1, z, B.slab("purpur_block", "bottom"))
    for z in range(cz - 1, cz + 2):
        for yy in range(y + 1, y + 5):
            P.set(wx, yy, z, AIR)
    P.set(wx, y + 5, cz - 1, B.stairs("purpur_block", "south", "top"))
    P.set(wx, y + 5, cz + 1, B.stairs("purpur_block", "north", "top"))
    P.set(wx, y + 5, cz, AIR)
    for (dz, dy) in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1), (-2, 0), (2, 0), (0, 2), (0, -2), (-1, 1), (1, 1),
                     (-1, -1), (1, -1)):
        P.set(wx, y + 9 + dy, cz + dz, S("purple_stained_glass_pane") if hash2(dz, dy, 6) < 0.7 else AIR)
    P.set(wx, y + 9, cz, S("magenta_stained_glass_pane"))
    # buttresses and pointed windows along the long walls
    for x in range(wx + 3, ex, 4):
        for (z, out) in ((nz, -1), (sz, 1)):
            for yy in range(y + 1, y + 1 + min(8, max(3, wall_h(x, z) + 1))):
                P.set(x, yy, z + out, S("purpur_pillar", axis="y"))
            P.set(x, y + 1 + min(8, max(3, wall_h(x, z) + 1)), z + out, B.slab("purpur_block", "bottom"))
        for (z, out) in ((nz, -1), (sz, 1)):
            for wx2 in (x + 1, x + 2):
                if wx2 >= ex:
                    continue
                top = wall_h(wx2, z)
                for yy in range(y + 3, min(y + 7, y + top)):
                    P.set(wx2, yy, z, S("purple_stained_glass_pane") if hash3(wx2, yy, z, 7) < 0.45 else AIR)
                if top >= 8:
                    P.set(wx2, y + 7, z, B.stairs("end_stone_bricks", "east" if wx2 == x + 1 else "west", "top"))
    # the apse: a half ring east of the nave, the altar inside
    ax = ex
    for (x, z) in K.cells(K.disc(P, ax, cz, 4.6) & ~K.disc(P, ax, cz, 3.5), P):
        if x <= ex:
            continue
        for yy in range(y + 1, y + 1 + max(3, wall_h(x, z) - 1)):
            K.mixed(P, x, yy, z, END_WALL, 5)
    for (x, z) in K.cells(K.disc(P, ax, cz, 3.5), P):
        if x >= ex:
            K.mixed(P, x, y, z, FLOOR, 6)
            P.fill(x, y + 1, z, x, y + 6, z, AIR)
    for z in range(cz - 2, cz + 3):
        if abs(z - cz) == 2:
            continue
        P.set(ex, y + 1, z, AIR)
        P.set(ex, y + 2, z, AIR)
        P.set(ex, y + 3, z, AIR)
    P.fill(ax + 1, y + 1, cz - 1, ax + 1, y + 1, cz + 1, F("obsidian"))
    P.set(ax + 1, y + 1, cz, F("crying_obsidian"))
    P.set(ax + 1, y + 2, cz, S("amethyst_cluster", facing="up"))
    P.set(ax + 1, y + 2, cz - 1, B.candle(3, "purple"))
    P.set(ax + 1, y + 2, cz + 1, B.candle(2, "black"))
    # rafters left of the roof, a fragment of its slope, hanging soul lanterns
    for x in range(wx + 2, ex, 4):
        if wall_h(x, nz) >= 8 and wall_h(x, sz) >= 8:
            for z in range(nz, sz + 1):
                P.set(x, y + 9, z, B.log("dark_oak", "z"))
            P.set(x, y + 8, cz, S("chain", axis="y"))
            P.set(x, y + 7, cz, B.lantern(hanging=True, soul=True))
    for x in range(wx + 1, wx + 6):
        for i, z in enumerate(range(nz - 1, cz)):
            P.set(x, y + 9 + i, z, B.stairs("deepslate_tiles", "south"))
    # pews, mostly broken; cobwebs; rubble
    for x in range(wx + 3, ex - 2, 2):
        for z in (nz + 1, nz + 2, sz - 2, sz - 1):
            if hash2(x, z, 8) < 0.6:
                P.set(x, y + 1, z, B.stairs("dark_oak", "west"))
    for (x, z) in K.cells(K.rect(P, wx + 1, nz + 1, ex - 1, sz - 1), P):
        r = hash2(x, z, 9)
        if P.get_idx(x, y + 1, z) <= 1:
            if r < 0.05:
                P.set(x, y + 1, z, B.slab("end_stone_bricks", "bottom"))
            elif r < 0.08:
                P.set(x, y + 1, z, F("end_stone_bricks"))
    for (x, yy, z) in ((wx + 1, y + 6, nz + 1), (ex - 1, y + 5, sz - 1), (wx + 1, y + 4, sz - 1)):
        P.set(x, yy, z, S("cobweb"))
    # the bell tower on the north-west corner, its top broken, the bell still hanging
    bx0, bz0 = wx - 2, nz - 4
    for x in range(bx0, bx0 + 5):
        for z in range(bz0, bz0 + 5):
            edge = x in (bx0, bx0 + 4) or z in (bz0, bz0 + 4)
            t = P.top(x, z)
            for yy in range(min(t, y) - 1, y + 1):
                P.set(x, yy, z, F("deepslate_bricks"))
            hmax = 19 if (x + z) % 3 else 16
            for yy in range(y + 1, y + 1 + hmax):
                if edge:
                    win = 13 <= yy - y <= 15 and (x == bx0 + 2 or z == bz0 + 2)
                    P.set(x, yy, z, AIR if win else (F("end_stone_bricks") if hash3(x, yy, z, 3) < 0.8 else
                                                      F("purpur_block")))
                else:
                    P.set(x, yy, z, AIR)
    P.fill(bx0 + 1, y + 12, bz0 + 1, bx0 + 3, y + 12, bz0 + 3, F("dark_oak_planks"))
    P.set(bx0 + 2, y + 15, bz0 + 2, B.log("dark_oak", "x"))
    P.set(bx0 + 1, y + 15, bz0 + 2, B.log("dark_oak", "x"))
    P.set(bx0 + 3, y + 15, bz0 + 2, B.log("dark_oak", "x"))
    P.set(bx0 + 2, y + 14, bz0 + 2, S("bell", attachment="ceiling", facing="north"))
    # the graveyard south of the chapel: iron railings, rows of stones, soul lanterns, the dead tree
    g0x, g0z, g1x, g1z = wx - 2, sz + 4, ex + 3, sz + 18
    for (x, z) in K.cells(K.rect(P, g0x, g0z, g1x, g1z), P):
        edge = x in (g0x, g1x) or z in (g0z, g1z)
        yy = K.ground_y(P, x, z)
        if edge and not (z == g0z and abs(x - (g0x + 4)) <= 1):
            P.set(x, yy + 1, z, S("iron_bars") if (x + z) % 5 else F("polished_blackstone_bricks"))
            if (x + z) % 5 == 0:
                P.set(x, yy + 2, z, B.lantern(soul=True) if (x + z) % 10 == 0 else B.slab("polished_blackstone",
                                                                                            "bottom"))
    for gx in range(g0x + 3, g1x - 1, 3):
        for gz in range(g0z + 3, g1z - 2, 4):
            if hash2(gx, gz, 11) < 0.25:
                continue
            yy = K.ground_y(P, gx, gz)
            kind = int(hash2(gx, gz, 12) * 3)
            if kind == 0:
                P.set(gx, yy + 1, gz, B.wall("polished_blackstone"))
                P.set(gx, yy + 2, gz, B.slab("polished_blackstone", "bottom"))
            elif kind == 1:
                P.set(gx, yy + 1, gz, F("chiseled_polished_blackstone"))
            else:
                P.set(gx, yy + 1, gz, B.wall("end_stone_bricks"))
                P.set(gx, yy + 2, gz, B.wall("end_stone_bricks"))
            P.set(gx, yy, gz + 1, S("soul_soil"))
            if hash2(gx, gz, 13) < 0.4:
                P.set(gx, yy + 1, gz + 1, B.candle(1, "white"))
    dead_tree(P, g1x - 3, g1z - 4, 15, seed=7)
    # the path from the road to the west door, a sign
    for x in range(-1042, wx):
        for z in (cz - 1, cz, cz + 1):
            if free[x - P.X0, z - P.Z0]:
                t = P.top(x, z)
                P.set(x, t, z, F("polished_blackstone") if hash2(x, z, 14) < 0.5 else F("deepslate_tiles"))
    K.place_sign(P, -1037, K.ground_y(P, -1037, cz - 2) + 1, cz - 2,
                 ['{"translate":"sign.eternalrealm.chapel","bold":true}', K.field_level(7)], wood="dark_oak",
                 rotation=4, glowing=True, color="light_purple")
    for (x, z) in ((wx - 2, cz - 3), (wx - 2, cz + 3)):
        K.lamp_post(P, x, K.ground_y(P, x, z) + 1, z, height=3, soul=True)
    P.probe(wx, y + 2, nz)
    P.view("end_chapel", (-1038, y + 13, -676), (-1004, y + 5, -704), fov=58)
    return P


# ====================================================================================== glowing fissures
def fissure(pid, angle, r0=118.0, r1=48.0, seed=0):
    """A jagged crack in the ground from r0 to r1 blocks from the tower, along ``angle`` (degrees, 0 = east,
    90 = south): crying obsidian walls, a glowing floor, shallow at both ends so nobody is trapped in it."""
    pts = []
    n = int((r0 - r1) / 2)
    for i in range(n + 1):
        t = i / n
        r = r0 + (r1 - r0) * t
        a = math.radians(angle + 9 * math.sin(t * 7.0 + seed) + 4 * math.sin(t * 17.0 + 2 * seed))
        pts.append((TX + r * math.cos(a), TZ + r * math.sin(a), t))
    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
    P = Piece(pid, f"光る裂け目 {pid[-1]} (glowing fissure towards the tower)", 7, int(min(xs)) - 5, int(min(zs)) - 5,
              int(max(xs)) + 5, int(max(zs)) + 5, below=8, above=6, kind="scenery")
    free = free_mask(P)
    depth = {}
    for (px, pz, t) in pts:
        w = 0.8 + 1.0 * math.sin(math.pi * t) * (0.7 + 0.3 * math.sin(t * 23 + seed))
        dmax = 1 + min(3.0, 8.0 * min(t, 1 - t))
        for x in range(int(px - w) - 1, int(px + w) + 2):
            for z in range(int(pz - w) - 1, int(pz + w) + 2):
                d = math.hypot(x + 0.5 - px, z + 0.5 - pz)
                if d <= w:
                    dd = int(round(dmax * (1 - 0.5 * (d / max(w, 0.1)) ** 2)))
                    depth[(x, z)] = max(depth.get((x, z), 0), dd)
    cells = {c: d for c, d in depth.items() if P.X0 <= c[0] <= P.X1 and P.Z0 <= c[1] <= P.Z1
             and free[c[0] - P.X0, c[1] - P.Z0] and not P.wet(*c) and d >= 1}
    for (x, z), d in cells.items():
        t = P.top(x, z)
        P.fill(x, t - d + 1, z, x, t + 1, z, AIR)
        bottom = t - d
        r = hash2(x, z, seed + 1)
        P.set(x, bottom, z, S("pearlescent_froglight", axis="y") if r < 0.45 else F("crying_obsidian") if r < 0.8
              else F("obsidian"))
        P.set(x, bottom - 1, z, F("obsidian"))
        if r > 0.92 and d >= 2:
            P.set(x, bottom + 1, z, S("amethyst_cluster", facing="up"))
    # line the walls: neighbours of the crack show crying obsidian and blackstone where they are exposed
    for (x, z), d in cells.items():
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in cells or not (P.X0 <= nx <= P.X1 and P.Z0 <= nz <= P.Z1):
                continue
            if not free[nx - P.X0, nz - P.Z0]:
                continue
            tn = P.top(nx, nz)
            for yy in range(P.top(x, z) - d, tn + 1):
                r = hash3(nx, yy, nz, seed + 2)
                P.set(nx, yy, nz, F("crying_obsidian") if r < 0.3 else F("blackstone") if r < 0.75 else
                      F("obsidian"))
            if hash2(nx, nz, seed + 3) < 0.15 and P.get_idx(nx, tn + 1, nz) <= 1:
                P.set(nx, tn + 1, nz, S("amethyst_cluster", facing="up") if hash2(nz, nx, 4) < 0.5 else
                      B.slab("blackstone", "bottom"))
    mid = pts[len(pts) // 2]
    mx, mz = int(mid[0]), int(mid[1])
    P.probe(mx, P.top(mx, mz) - 6, mz)
    P.view(pid, (mx + 30 * math.cos(math.radians(angle + 70)), P.top(mx, mz) + 22,
                 mz + 30 * math.sin(math.radians(angle + 70))), (mx, P.top(mx, mz) - 2, mz), fov=60)
    return P


def build(only=None):
    jobs = (("end_chapel", chapel),
            ("end_deadwood", deadwood),
            ("end_crystals_north", lambda: outcrop("end_crystals_north", "水晶の露頭 北 (crystal outcrop)", -965, -655,
                                                   n=7, seed=3)),
            ("end_crystals_south", lambda: outcrop("end_crystals_south", "水晶の露頭 南 (crystal outcrop)", -1072,
                                                   -880, n=6, seed=8)),
            ("end_fissure_1", lambda: fissure("end_fissure_1", 25.0, seed=1)),
            ("end_fissure_2", lambda: fissure("end_fissure_2", 62.0, seed=2)),
            ("end_fissure_3", lambda: fissure("end_fissure_3", 100.0, seed=3)))
    out = []
    for pid, fn in jobs:
        if not only or pid in only:
            out.append(fn())
    return out
