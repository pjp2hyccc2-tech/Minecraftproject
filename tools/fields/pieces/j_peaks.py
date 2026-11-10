"""天翔の峰 (soaring peaks, Lv.40+): the mountain road and its works.

The road from the waystone up to the cave gate d7 climbs steeply over a high ridge. It is stepped where it climbs
(a stair on every rise, cut into the slope where it must, walled where it runs on a fill), with cairns and lantern
poles marking it in the snow. The Skyward Pass gate stands in the notch where the road crosses the crest; a mountain
hut offers shelter lower down; beside the last hump before the gate a watchtower looks over a gully, and a rope
bridge crosses the gully to a lookout on the flank of the eastern peak.
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
ROAD = next(r for r in L["roads"] if r["id"] == "peaks")
D7 = next(g for g in L["landmarks"] if g["id"] == "d7")
# north of the plateau the road runs steadily northwards: its centre is a function of z
_V = [p for p in ROAD["points"] if p[1] <= -600][::-1]
_VZ = np.array([p[1] for p in _V], float)
_VX = np.array([p[0] for p in _V], float)

Z_START, Z_END = -842, -1160       # the stepped stretch (south end just past the waystone, north end before the
                                   # approach of the cave gate, which must stay as it is)
CREST_Z = -1114                    # where the road crosses the ridge
GATE_ZN, GATE_ZS = -1108, -1103    # the pass gate stands on a level landing just below the crest


def x_of(z):
    return float(np.interp(z, _VZ, _VX))


def road_cells(z):
    xr = int(round(x_of(z)))
    return [xr - 1, xr, xr + 1]


def _profile(h, cut=1.0, fill=1.7, fixed=None):
    """Heights p (integers) close to h with |p[i+1] - p[i]| <= 1, both ends on the terrain and the rows of
    ``fixed`` ({index: height}) at the given heights."""
    h = np.asarray(h, float)
    n = len(h)
    H = np.arange(int(h.min()) - 14, int(h.max()) + 6)
    d = H[None, :] - h[:, None]
    unit = np.where(d < 0, -d * cut, d * fill) + np.where(np.abs(d) > 8, 50.0, 0.0)
    INF = 1e18
    for i, v in (fixed or {}).items():
        unit[i] = np.where(H == v, unit[i], INF)
    cost = np.full((n, len(H)), INF)
    back = np.zeros((n, len(H)), int)
    cost[0] = np.where(H == int(round(h[0])), 0.0, INF)
    for i in range(1, n):
        best = cost[i - 1].copy()
        arg = np.zeros(len(H), int)
        for sh in (-1, 1):
            prev = np.full(len(H), INF)
            if sh == 1:
                prev[1:] = cost[i - 1][:-1]
            else:
                prev[:-1] = cost[i - 1][1:]
            better = prev < best
            best = np.where(better, prev, best)
            arg = np.where(better, sh, arg)
        cost[i] = best + unit[i]
        back[i] = arg
    k = int(np.where(H == int(round(h[-1])))[0][0])
    p = np.zeros(n, int)
    for i in range(n - 1, -1, -1):
        p[i] = H[k]
        k -= back[i, k]
    return p


_PROFILE = None


def profile():
    """{z: road height} over the whole stepped stretch (computed once, so the pieces meet exactly)."""
    global _PROFILE
    if _PROFILE is None:
        rows = list(range(Z_START, Z_END - 1, -1))
        xs = [x_of(z) for z in rows]
        g = probe.grid(int(min(xs)) - 3, Z_END, int(max(xs)) + 3, Z_START, 1)
        hs = [np.mean([g.top[x - g.x0, z - g.z0] for x in road_cells(z)]) for z in rows]
        gate = [rows.index(z) for z in range(GATE_ZN, GATE_ZS + 1)]
        gy = int(round(np.mean([hs[i] for i in gate])))
        _PROFILE = dict(zip(rows, (int(v) for v in _profile(hs, fixed={i: gy for i in gate}))))
    return _PROFILE


STAIR_MIX = [("stone_bricks", 50), ("cobblestone", 35), ("mossy_cobblestone", 15)]


def _mat(x, z, seed=0, mix=STAIR_MIX):
    tot = sum(w for _, w in mix)
    r = hash2(x, z, 900 + seed) * tot
    for m, w in mix:
        r -= w
        if r < 0:
            return m
    return mix[-1][0]


def cairn(P, x, z, seed=0):
    y = K.ground_y(P, x, z)
    h = 2 + int(hash2(x, z, seed) * 2)
    for k in range(h):
        P.set(x, y + 1 + k, z, F("cobblestone") if hash3(x, k, z, seed) < 0.6 else F("mossy_cobblestone"))
    P.set(x, y + 1 + h, z, B.slab("stone", "bottom") if hash2(z, x, seed) < 0.5 else B.wall("cobblestone"))
    for (dx, dz) in ((1, 0), (0, 1), (-1, 0), (0, -1)):
        if hash2(x + dx, z + dz, seed + 3) < 0.45:
            yy = K.ground_y(P, x + dx, z + dz)
            if yy <= y + 1 and P.get_idx(x + dx, yy + 1, z + dz) <= 1:
                P.set(x + dx, yy + 1, z + dz, B.slab("cobblestone", "bottom"))


def lantern_pole(P, x, z):
    y = K.ground_y(P, x, z)
    P.fill(x, y + 1, z, x, y + 2, z, B.fence("spruce"))
    P.set(x, y + 3, z, B.S("lantern", hanging=False))


def stepped_road(P, z_a, z_b, seed=0):
    """Rows z_a (south) .. z_b (north) of the stepped road into piece P. Returns {z: road height}."""
    prof = profile()
    rows = [z for z in range(z_a, z_b - 1, -1) if z in prof]
    for z in rows:
        y = prof[z]
        xs = road_cells(z)
        north, south = prof.get(z - 1), prof.get(z + 1)
        rise = "north" if north == y + 1 else ("south" if south == y + 1 else None)
        mat = _mat(z, 0, seed)
        for x in xs:
            t = P.top(x, z)
            if t > y:
                P.fill(x, y + 1, z, x, t + 1, z, AIR)
            elif t < y:
                P.fill(x, t, z, x, y - 1, z, F("cobblestone"))
            P.set(x, y, z, F(mat) if rise else S("dirt_path"))
            if rise:
                P.set(x, y + 1, z, B.stairs(mat, rise))
            P.fill(x, y + (2 if rise else 1), z, x, y + 4, z, AIR)
        # the banks: cut back at 2:1 where the road is sunk, a retaining wall and parapet where it is raised
        for side in (-1, 1):
            edge = xs[0] if side < 0 else xs[-1]
            for k in range(1, 5):
                x = edge + side * k
                if not (P.X0 <= x <= P.X1):
                    continue
                t = P.top(x, z)
                allow = y + 1 + 2 * (k - 1)
                if t > allow:
                    K.set_ground(P, x, z, allow, clear=t - allow + 2)
                elif k == 1 and t < y - 1:
                    for yy in range(t, y + 1):
                        P.set(x, yy, z, F("cobblestone") if hash3(x, yy, z, 5) < 0.7 else F("mossy_cobblestone"))
                    P.set(x, y + 1, z, B.wall("cobblestone"))
                elif k == 1 and t == y - 1:
                    P.set(x, y, z, F(_mat(x, z, seed + 1)))
        # markers: cairns every 30 rows, lantern poles every 14 rows above the snow line
        i = Z_START - z
        side = -1 if (i // 30) % 2 else 1
        if i % 30 == 15:
            x = (xs[0] - 3) if side < 0 else (xs[-1] + 3)
            if P.X0 <= x <= P.X1 and not P.wet(x, z):
                cairn(P, x, z, seed=i)
        if i % 14 == 7 and y >= 150:
            x = (xs[0] - 1) if (i // 14) % 2 else (xs[-1] + 1)
            if P.X0 <= x <= P.X1 and P.get(x, y + 1, z).split("[")[0] != "minecraft:cobblestone_wall":
                lantern_pole(P, x, z)
    return {z: prof[z] for z in rows}


def road_piece(pid, name, z_a, z_b, seed):
    xs = [x_of(z) for z in range(z_a, z_b - 1, -1)]
    P = Piece(pid, name, 6, int(min(xs)) - 8, z_b, int(max(xs)) + 8, z_a, below=14, above=12, kind="road")
    prof = stepped_road(P, z_a, z_b, seed)
    zm = (z_a + z_b) // 2
    P.probe(road_cells(zm)[1], prof[zm] - 1, zm)
    return P, prof


def road_low():
    P, prof = road_piece("peaks_road_low", "天翔の峰 山道 下 (stepped mountain road, waystone to the high slopes)",
                         Z_START, -990, 1)
    P.view("peaks_road_low", (x_of(-930) + 34, prof[-930] + 26, -905), (x_of(-950), prof[-950], -950), fov=60)
    return P


def road_high():
    P, prof = road_piece("peaks_road_high", "天翔の峰 山道 上 (stepped mountain road up the ridge)", -991, -1097, 2)
    P.view("peaks_road_high", (x_of(-1020) + 32, prof[-1020] + 26, -1005), (x_of(-1060), prof[-1060], -1060),
           fov=60)
    return P


# ====================================================================================== the pass gate
def tower(P, x0, z0, x1, z1, y, h, door=None, seed=0, roof=None):
    """A hollow stone tower on the floor y (walls y+1 .. y+h), a ladder inside up to a hatch in the top floor
    (y+h+1); a crenellated top, or a pyramid roof of ``roof`` stairs."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t = P.top(x, z)
            for yy in range(min(t, y) - 2, y + 1):
                P.set(x, yy, z, F("stone_bricks") if hash3(x, yy, z, seed) < 0.75 else F("mossy_stone_bricks"))
            edge = x in (x0, x1) or z in (z0, z1)
            for yy in range(y + 1, y + h + 1):
                if edge:
                    r = hash3(x, yy, z, seed + 1)
                    P.set(x, yy, z, F("stone_bricks") if r < 0.7 else F("cracked_stone_bricks") if r < 0.82 else
                          F("mossy_stone_bricks"))
                else:
                    P.set(x, yy, z, AIR)
            P.set(x, y + h + 1, z, F("spruce_planks") if not edge else F("stone_bricks"))
            if roof is None and edge and (x + z) % 2 == 0:
                P.set(x, y + h + 2, z, F("stone_bricks"))
                P.set(x, y + h + 3, z, B.slab("stone_bricks", "bottom"))
    if roof is None:
        for x in (x0, x1):
            for z in (z0, z1):
                P.set(x, y + h + 2, z, F("stone_bricks"))
                P.set(x, y + h + 3, z, B.slab("stone_bricks", "bottom"))
    else:
        k = 0
        while True:
            a0, a1, b0, b1 = x0 - 1 + k, x1 + 1 - k, z0 - 1 + k, z1 + 1 - k
            if a0 > a1 or b0 > b1:
                break
            yy = y + h + 1 + k
            for x in range(a0, a1 + 1):
                for z in range(b0, b1 + 1):
                    if x not in (a0, a1) and z not in (b0, b1):
                        continue
                    face = "south" if z == b0 else "north" if z == b1 else "east" if x == a0 else "west"
                    P.set(x, yy, z, B.stairs(roof, face))
            k += 1
        P.fill(x0 + 1, y + h + 2, z0 + 1, x1 - 1, y + h + 2, z1 - 1, AIR)
    # arrow slits
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for yy in (y + 5, y + 9):
        if yy < y + h:
            for (x, z) in ((cx, z0), (cx, z1), (x0, cz), (x1, cz)):
                P.set(x, yy, z, S("iron_bars"))
    # ladder in the north-west inner corner up to a hatch
    lx, lz = x0 + 1, z0 + 1
    for yy in range(y + 1, y + h + 1):
        P.set(lx, yy, lz, S("ladder", facing="south"))
    P.set(lx, y + h + 1, lz, B.trapdoor("spruce", "south", "top"))
    P.set(x1 - 1, y + 1, z1 - 1, B.lantern())
    if door:
        dx, dz = door
        P.set(dx, y + 1, dz, AIR)
        P.set(dx, y + 2, dz, AIR)
        P.set(dx, y + 3, dz, B.stairs("stone_bricks", "east" if dx > cx else "west", "top") if dx in (x0, x1) else
              B.stairs("stone_bricks", "south" if dz > cz else "north", "top"))


def pass_gate():
    """The stepped road over the crest, and the gate in its notch: two towers on the banks, the gatehouse over the
    road with a raised portcullis and a timber-roofed wall walk, the kingdom's flags and the field's banners."""
    xs = [x_of(z) for z in range(-1098, Z_END - 1, -1)]
    P = Piece("peaks_pass", "天翔の関 (the Skyward Pass gate in the notch of the crest)", 6, int(min(xs)) - 14, Z_END,
              int(max(xs)) + 14, -1098, below=16, above=26, kind="scenery")
    prof = stepped_road(P, -1098, Z_END, 3)
    zn, zs = GATE_ZN, GATE_ZS                     # the gate's north and south faces
    zc = (zn + zs + 1) // 2
    xr = int(round(x_of(zc)))
    y = prof[zc]
    h = 13
    tower(P, xr - 8, zn - 1, xr - 3, zs + 1, y, h, door=(xr - 3, zc), seed=1, roof="spruce")
    tower(P, xr + 3, zn - 1, xr + 8, zs + 1, y, h, door=(xr + 3, zc - 1), seed=2, roof="spruce")
    # the gatehouse: solid masonry over the passage, a corbelled opening, the wall walk and its roof
    for x in range(xr - 2, xr + 3):
        for z in range(zn + 1, zs):
            for yy in range(y + 7, y + 11):
                P.set(x, yy, z, F("stone_bricks") if hash3(x, yy, z, 4) < 0.8 else F("mossy_stone_bricks"))
            P.set(x, y + 11, z, F("spruce_planks"))
        for z in (zn + 1, zs - 1):
            P.set(x, y + 6, z, B.stairs("stone_bricks", "west" if x < xr else "east", "top") if abs(x - xr) == 2
                  else AIR)
        for z in (zn, zs):
            for yy in range(y + 7, y + 11):
                P.set(x, yy, z, F("stone_bricks") if hash3(x, yy, z, 6) < 0.85 else F("cracked_stone_bricks"))
            P.set(x, y + 6, z, B.stairs("stone_bricks", "west" if x < xr else "east", "top") if abs(x - xr) == 2
                  else AIR)
            P.set(x, y + 11, z, B.wall("stone_bricks") if x % 2 == 0 else AIR)
    for x in range(xr - 2, xr + 3):
        P.set(x, y + 6, zc, S("iron_bars"))
        for z in range(zn + 1, zs):
            for yy in range(prof.get(z, y) + 2, y + 6):
                if P.get_idx(x, yy, z) > 1 and not P.get(x, yy, z).startswith("minecraft:iron_bars"):
                    P.set(x, yy, z, AIR)
    for x in (xr - 2, xr + 2):
        for z in (zn + 1, zs - 1):
            P.fill(x, y + 12, z, x, y + 13, z, B.log("spruce"))
    for x in range(xr - 2, xr + 3):
        P.set(x, y + 14, zn + 1, B.stairs("spruce", "south"))
        P.set(x, y + 14, zs - 1, B.stairs("spruce", "north"))
        for z in range(zn + 2, zs - 1):
            P.set(x, y + 15, z, B.slab("spruce", "bottom"))
            P.set(x, y + 14, z, F("spruce_planks"))
    for z in (zn + 1, zs - 1):
        for x in (xr - 1, xr, xr + 1):
            P.set(x, y + 12, z, B.fence("spruce"))
    P.set(xr, y + 13, zc - 1, B.lantern(hanging=True))
    # lanterns under the arch, banners of the field on both faces, the kingdom's flags on the towers
    for z in (zn + 1, zs - 1):
        P.set(xr, y + 6, z, B.lantern(hanging=True))
    for (tx0, tx1) in ((xr - 8, xr - 3), (xr + 3, xr + 8)):
        cx = (tx0 + tx1) // 2
        for (z, face) in ((zs + 2, "south"), (zn - 2, "north")):
            for bx in (cx, cx + 1):
                K.wall_banner(P, bx, y + 9, z, K.FIELD_COLORS[6][0], face, K.banner_nbt_field(6))
        K.flagpole(P, cx, y + h + 4, (zn + zs) // 2, 4, facing="east", design="kingdom", width=3.5, height=2.2)
    # the sign of the pass on the south side, by the road
    sx = xr + 3
    sz = zs + 4
    sy = K.ground_y(P, sx, sz)
    K.place_sign(P, sx, sy + 1, sz, ['{"translate":"sign.eternalrealm.pass","bold":true}', K.field_name(6),
                                     K.field_level(6)], back=['{"translate":"sign.eternalrealm.pass","bold":true}'],
                 wood="spruce", rotation=0)
    P.probe(xr - 5, y + 2, zn)
    P.view("peaks_pass", (xr + 14, y + 12, zs + 36), (xr, y + 5, zc), fov=50)
    P.view("peaks_pass_north", (xr - 20, y + 14, zn - 28), (xr, y + 6, zc), fov=55)
    return P


# ====================================================================================== the mountain hut
def hut():
    """A stone-and-timber mountain hut in the bowl east of the road: woodpile, bench, trough, a path and a sign."""
    x0, z0, x1, z1 = 280, -881, 287, -873
    P = Piece("peaks_hut", "山小屋 (mountain hut)", 6, 270, -891, 296, -864, below=10, above=20, kind="scenery")
    y = P.median_top(x0, z0, x1, z1)
    BK.fit_ground(P, x0, z0, x1, z1, y, found="cobblestone", blend=3)
    f, W, D = BK.frame_for(P, x0, z0, x1, z1, "west", y)
    BK.house(f, W, D, BK.House(seed=61, floors=1, wall="spruce_planks", ground_wall="cobblestone", timber="spruce",
                                 roof="deepslate_tiles", roof_axis="x", door="spruce", shutters="spruce",
                                 chimney="cobblestone", smoke=True, porch=True, lantern=True, flowers=False))
    # woodpile along the south wall, a chopping block, a trough, a bench by the door
    for x in range(x0 + 1, x1):
        for yy in (y + 1, y + 2):
            if hash2(x, yy, 3) < 0.85:
                P.set(x, yy, z1 + 2, B.log("spruce", axis="z"))
    P.set(x0 + 1, y + 3, z1 + 2, B.slab("spruce", "bottom"))
    P.set(x0 - 3, y + 1, z1, B.log("oak", "y", stripped=False))
    P.set(x0 - 3, y + 1, z0, S("cauldron"))
    P.set(x0 - 3, y + 1, z0 + 1, S("cauldron"))
    K.bench(P, x0 - 2, y + 1, z0 + 2, "west", length=2, wood="spruce")
    # path from the road (the road's banks end 4 blocks east of it) to the door
    door_z = (z0 + z1) // 2
    for x in range(276, x0 - 1):
        for z in (door_z - 1, door_z, door_z + 1):
            t = P.top(x, z)
            if abs(t - y) <= 1 and P.get_idx(x, t + 1, z) <= 1:
                P.set(x, t, z, S("dirt_path") if z == door_z or hash2(x, z, 7) < 0.5 else S("coarse_dirt"))
    K.lantern_post(P, 277, K.ground_y(P, 277, door_z - 2) + 1, door_z - 2, height=3, wood="spruce")
    K.place_sign(P, 276, K.ground_y(P, 276, door_z + 2) + 1, door_z + 2,
                 ['{"translate":"sign.eternalrealm.mountain_hut","bold":true}'], wood="spruce", rotation=4)
    for (x, z) in ((x1 + 3, z0 - 2), (x1 + 4, z1 + 3), (x0 - 4, z0 - 4)):
        K.spruce(P, x, K.ground_y(P, x, z) + 1, z, height=9 + int(hash2(x, z, 5) * 4), seed=x + z, snowy=y >= 158)
    P.probe(x0, y, z0)
    P.view("peaks_hut", (x0 - 18, y + 12, z1 + 16), ((x0 + x1) / 2, y + 3, (z0 + z1) / 2), fov=55)
    return P


# ====================================================================================== watchtower and rope bridge
def watchtower():
    """A stone watchtower beside the road's last hump before the cave gate, a rope bridge from its foot east over
    the gully, and a small lookout platform on the flank of the eastern peak at the far end."""
    tx0, tz0, tx1, tz1 = 207, -1178, 213, -1172
    zb = (tz0 + tz1) // 2                         # the bridge's middle row
    P = Piece("peaks_watchtower", "物見櫓と吊り橋 (watchtower, rope bridge over the gully, lookout)", 6, 206,
              tz0 - 8, 252, tz1 + 8, below=26, above=28, kind="scenery")
    y = P.top(206, zb)                            # floor at the level of the road beside it
    tower(P, tx0, tz0, tx1, tz1, y, 16, door=None, seed=7)
    for z in (zb - 1, zb, zb + 1):                # doors: west to the road, east to the bridge
        for x in (tx0, tx1):
            if x == tx0 and z != zb:
                continue
            P.set(x, y + 1, z, AIR)
            P.set(x, y + 2, z, AIR)
    P.set(tx0, y + 3, zb, B.stairs("stone_bricks", "east", "top"))
    K.lantern_post(P, tx0 - 1, y + 1, zb + 2, height=2, wood="spruce")
    # top: a brazier and the kingdom's flag
    cx = (tx0 + tx1) // 2
    P.set(cx, y + 18, zb, S("campfire", facing="west", lit=True))
    K.flagpole(P, tx1 - 1, y + 17, tz1 - 1, 5, facing="east", design="kingdom", width=3.5, height=2.2)
    # the rope bridge: a sagging deck of slabs (half-block steps), fence rails, taller posts every few blocks
    xa, xb = tx1 + 1, 240                         # deck from the tower's east door to the lookout
    span = xb - xa + 1
    for i, x in enumerate(range(xa, xb + 1)):
        s = (y + 1) - 3.0 * math.sin(math.pi * (i + 0.5) / span)
        s2 = round(s * 2) / 2.0                   # walking surface in half blocks
        n = int(math.floor(s2))
        top_slab = abs(s2 - n) < 1e-6             # surface on a whole block: a top slab below it
        for z in range(zb - 1, zb + 2):
            if top_slab:
                P.set(x, n - 1, z, B.slab("spruce", "top"))
            else:
                P.set(x, n, z, B.slab("spruce", "bottom"))
            P.fill(x, n + (0 if top_slab else 1), z, x, n + 3, z, AIR)
        rail = n if top_slab else n + 1
        for z in (zb - 2, zb + 2):
            P.set(x, rail - 1, z, B.slab("spruce", "top") if top_slab else B.slab("spruce", "bottom"))
            P.set(x, rail, z, B.fence("spruce"))
            if i % 5 == 0:
                P.set(x, rail + 1, z, B.fence("spruce"))
        if i % 5 == 0:
            for z in (zb - 2, zb + 2):
                P.set(x, rail - 2, z, S("chain", axis="y"))
    # the lookout at the far end, cut into the slope, held up by stone where it overhangs
    lx0, lx1 = xb + 1, xb + 6
    for x in range(lx0, lx1 + 1):
        for z in range(zb - 3, zb + 4):
            t = P.top(x, z)
            if t < y:
                for yy in range(t - 1, y):
                    P.set(x, yy, z, F("cobblestone") if hash3(x, yy, z, 9) < 0.75 else F("mossy_cobblestone"))
            P.set(x, y, z, F("spruce_planks"))
            P.fill(x, y + 1, z, x, max(t, y) + 2, z, AIR)
            edge = z in (zb - 3, zb + 3) or x == lx1
            if edge:
                P.set(x, y + 1, z, B.fence("spruce"))
    for z in range(zb - 3, zb + 4):               # the slope behind is cut back in steps
        for k in range(1, 5):
            x = lx1 + k
            t = P.top(x, z)
            allow = y + 1 + 2 * k
            if t > allow and x <= P.X1:
                K.set_ground(P, x, z, allow, clear=t - allow + 2)
    K.bench(P, lx1 - 1, y + 1, zb - 1, "west", length=3, wood="spruce")
    K.lantern_post(P, lx0 + 1, y + 1, zb + 3, height=3, wood="spruce")
    K.place_sign(P, lx0, y + 1, zb - 3, ['{"translate":"sign.eternalrealm.lookout","bold":true}'], wood="spruce",
                 rotation=4)
    cairn(P, lx1 - 1, zb + 2, seed=4)
    P.probe(tx0, y, tz0)
    P.view("peaks_watchtower", (214, y + 16, zb + 44), (228, y - 2, zb), fov=58)
    return P


def build(only=None):
    out = []
    for pid, fn in (("peaks_road_low", road_low), ("peaks_road_high", road_high), ("peaks_pass", pass_gate),
                    ("peaks_hut", hut), ("peaks_watchtower", watchtower)):
        if not only or pid in only:
            out.append(fn())
    return out
