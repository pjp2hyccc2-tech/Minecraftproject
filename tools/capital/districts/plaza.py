"""中央広場 Central plaza: the angel fountain, the compass-star paving, raised flower beds with trees, benches,
ornate lamp posts and crest-banner masts flanking the four avenues. Players spawn just south of the fountain.

Fountain (r <= 10): a wide lower pool (sea lanterns in its floor), a corbelled middle bowl whose rim is notched
all round so a curtain of water falls into the pool, and a central pedestal with four spouts. On top stands the
white angel of Arseria: robe, raised right hand holding a shining star, left hand held out in welcome, wings
spread wide against the castle behind her.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "plaza"
AIR = K.AIR

POOL_R = 8.5        # water of the lower pool
RIM_R = 9.45        # outer edge of the pool rim
STEP_R = 10.35      # low step around the rim
BOWL_R = 4.5        # water of the middle bowl
BOWL_RIM = 5.45
PED_R = 2.4         # central pedestal
BOWL_Y = 4          # water level of the middle bowl
SPOUT_Y = 6
STATUE_Y = 9        # first block of the statue


def _disc(cx, cz, r0, r1, R=12):
    """Integer offsets with r0 < dist <= r1 (+0.35 tolerance like shapes.cylinder)."""
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    d = np.sqrt(dx * dx + dz * dz)
    m = (d > r0 + 0.35 if r0 >= 0 else d >= 0) & (d <= r1 + 0.35)
    return cx + dx[m], cz + dz[m], d[m]


def _set_ring(w, r0, r1, y, state):
    xs, zs, _ = _disc(0, 0, r0, r1)
    w.set_many(xs, np.full(xs.size, y), zs, state)


# ============================================================================== paving
def paving(w):
    """Compass star inlaid around the fountain: four long blue-and-gold rays on the avenue axes, four short
    rays on the diagonals, a bright ring around the fountain step and a gilded ring further out."""
    R = 22
    X, Z = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    r = np.sqrt(X * X + Z * Z)
    ang = np.degrees(np.arctan2(Z, X)) % 360.0
    lapis, gold, bright = B.S("lapis_block"), K.F(K.GOLD), K.F(K.SM)
    out = {}
    # rays
    for k in range(8):
        a = k * 45.0
        long_ = k % 2 == 0
        r_end = 18.6 if long_ else 13.6
        half0 = 2.3 if long_ else 1.5
        da = np.radians(((ang - a + 180) % 360) - 180)
        along = r * np.cos(da)
        perp = np.abs(r * np.sin(da))
        f = np.clip((along - 10.4) / (r_end - 10.4), 0, 1)
        width = half0 * (1 - f)
        inside = (along >= 10.4) & (along <= r_end) & (perp <= width + 0.15) & (np.cos(da) > 0)
        edge = inside & (perp > width - 0.85)
        out.setdefault("lapis", np.zeros_like(inside))
        out.setdefault("gold", np.zeros_like(inside))
        out["lapis"] |= inside & ~edge
        out["gold"] |= edge
    ring = (r > 10.45) & (r <= 11.45)
    ring2 = (np.abs(r - 19.0) < 0.5)
    for name, m, st in (("ring", ring & ~out["gold"] & ~out["lapis"], bright),
                        ("ring2", ring2, K.F("polished_diorite")),
                        ("lapis", out["lapis"], lapis), ("gold", out["gold"], gold)):
        w.set_many(X[m], np.zeros(int(m.sum()), int), Z[m], st)
    # gilded studs on the outer ring at the 16 spoke ends
    for k in range(16):
        a = math.radians(k * 22.5 + 11.25)
        w.set(int(round(19 * math.cos(a))), 0, int(round(19 * math.sin(a))), gold)


# ============================================================================== fountain
def fountain(w):
    cb, sm, carved = K.F(K.CB), K.F(K.SM), K.F(K.CARVED)
    water = B.S("water")
    # dig the pool and lay its floor
    xs, zs, d = _disc(0, 0, -1, RIM_R)
    for y in range(-1, 6):
        w.set_many(xs, np.full(xs.size, y), zs, AIR)
    w.set_many(xs, np.full(xs.size, -2), zs, B.S("dark_prismarine"))
    pool_x, pool_z, pd = _disc(0, 0, -1, POOL_R)
    # pool floor: dark prismarine with a ring of prismarine bricks and sea lanterns
    w.set_many(pool_x, np.full(pool_x.size, -2), pool_z, B.S("dark_prismarine"))
    sel = np.abs(pd - 6.6) < 0.55
    w.set_many(pool_x[sel], np.full(int(sel.sum()), -2), pool_z[sel], B.S("prismarine_bricks"))
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        w.set(int(round(6.6 * math.cos(a))), -2, int(round(6.6 * math.sin(a))), B.S("sea_lantern"))
    w.set_many(pool_x, np.full(pool_x.size, -1), pool_z, water)
    w.set_many(pool_x, np.full(pool_x.size, 0), pool_z, water)
    w.set_many(pool_x, np.full(pool_x.size, 1), pool_z, water)
    # rim: two courses, a moulded cap, and a low step outside
    rx, rz, rd = _disc(0, 0, POOL_R, RIM_R)
    for y in (-1, 0):
        w.set_many(rx, np.full(rx.size, y), rz, cb)
    w.set_many(rx, np.full(rx.size, 1), rz, sm)
    sx, sz, sd = _disc(0, 0, RIM_R, STEP_R)
    w.set_many(sx, np.zeros(sx.size, int), sz, B.S("polished_diorite"))
    for x, z in zip(sx, sz):
        # outward-facing low step: stairs whose tall back leans on the rim
        dx, dz = x, z
        fac = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
        w.set(int(x), 1, int(z), B.stairs(K.SM, fac))
    # eight rim posts with gilded urns
    for k in range(8):
        a = math.radians(k * 45)
        px, pz = int(round(9.0 * math.cos(a))), int(round(9.0 * math.sin(a)))
        w.set(px, 2, pz, carved)
        w.set(px, 3, pz, B.S("gilded_finial"))
    # ---- middle bowl on a drum
    dx_, dz_, dd = _disc(0, 0, -1, BOWL_R - 0.1)
    for y in range(-1, BOWL_Y - 1):
        w.set_many(dx_, np.full(dx_.size, y), dz_, cb)
    _set_ring(w, BOWL_R - 1.1, BOWL_R - 0.1, 0, carved)
    _set_ring(w, BOWL_R - 1.1, BOWL_R - 0.1, 1, K.F(K.GOLD))
    # corbel ring (upside-down stairs) carrying the bowl
    bx, bz, bd = _disc(0, 0, -1, BOWL_RIM)
    w.set_many(bx, np.full(bx.size, BOWL_Y - 1), bz, sm)
    cx_, cz_ = S.ring_positions(0, 0, BOWL_RIM - 0.35)
    S.corbel_ring(w, 0, 0, BOWL_RIM - 0.35, BOWL_Y - 2, K.SM)
    # bowl water + rim with notches
    wx, wz, wd = _disc(0, 0, -1, BOWL_R)
    w.set_many(wx, np.full(wx.size, BOWL_Y), wz, water)
    rimx, rimz, rimd = _disc(0, 0, BOWL_R, BOWL_RIM)
    ang = np.arctan2(rimz, rimx)
    order = np.argsort(ang)
    notches = []
    for i, k in enumerate(order):
        x, z = int(rimx[k]), int(rimz[k])
        if i % 2 == 0:
            w.set(x, BOWL_Y, z, sm)
        else:
            notches.append((x, z))
    for (x, z) in notches:
        w.set(x, BOWL_Y, z, B.S("water", level=1))
        w.fluid_tick(x, BOWL_Y, z)
        for (ox, oz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + ox, z + oz
            if math.hypot(nx, nz) <= BOWL_RIM + 0.35:
                continue
            if not w.is_empty(nx, BOWL_Y, nz) and "water" not in w.get(nx, BOWL_Y, nz):
                continue
            w.set(nx, BOWL_Y, nz, B.S("water", level=2))
            for y in range(BOWL_Y - 1, 1, -1):
                if w.is_empty(nx, y, nz) or "water" in w.get(nx, y, nz):
                    w.set(nx, y, nz, B.S("water", level=8))
    # ---- central pedestal with four spouts
    px, pz, _ = _disc(0, 0, -1, PED_R)
    for y in range(BOWL_Y, STATUE_Y):
        w.set_many(px, np.full(px.size, y), pz, cb)
    _set_ring(w, PED_R - 1.0, PED_R, STATUE_Y - 2, K.F(K.GOLD))
    _set_ring(w, PED_R - 1.0, PED_R, STATUE_Y - 1, carved)
    _set_ring(w, PED_R - 1.0, PED_R, BOWL_Y, carved)
    for (ox, oz, fac) in ((2, 0, "east"), (-2, 0, "west"), (0, 2, "south"), (0, -2, "north")):
        sx, sz = ox + int(np.sign(ox)), oz + int(np.sign(oz))
        # spout mouth: a gilded block in the pedestal under a lion-mask lintel; the jet arcs into the bowl
        w.set(ox, SPOUT_Y, oz, K.F(K.GOLD))
        w.set(sx, SPOUT_Y + 1, sz, B.stairs(K.SM, K.OPP[fac], "top"))
        w.set(ox, SPOUT_Y + 1, oz, carved)
        TK.spray(w, sx, SPOUT_Y, sz, power=1, facing=fac)


# ============================================================================== angel statue
def _capsule(P, a, b, r):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    ab = b - a
    t = np.clip(((P - a) @ ab) / max(ab @ ab, 1e-9), 0, 1)
    q = a + t[..., None] * ab
    return np.linalg.norm(P - q, axis=-1) - r


def _ellipsoid(P, c, rad):
    q = (P - np.asarray(c, float)) / np.asarray(rad, float)
    return (np.linalg.norm(q, axis=-1) - 1.0) * min(rad)


def angel(w, cx, y0, cz, scale=1.0):
    """White angel statue standing on the block row y0 (feet), facing south."""
    R = 10
    xs = np.arange(-R, R + 1)
    ys = np.arange(0, 22)
    zs = np.arange(-6, 5)
    X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
    P = np.stack([X.astype(float), Y + 0.5, Z.astype(float)], axis=-1) / scale
    px, py, pz = P[..., 0], P[..., 1], P[..., 2]

    # robe: elliptic frustum, slightly flared, with a short train behind
    rxr = np.clip(2.2 - 0.105 * py, 1.3, 2.5)
    rzr = 0.72 * rxr + 0.12
    robe = ((px / rxr) ** 2 + ((pz + 0.15) / rzr) ** 2 <= 1.0) & (py >= 0) & (py < 7.6)
    train = _ellipsoid(P, (0, 0.4, -1.6), (1.4, 1.2, 1.3)) < 0
    robe |= train & (py >= 0)
    belt = ((px / 1.45) ** 2 + (pz / 1.05) ** 2 <= 1.0) & (py >= 7.0) & (py < 8.0)
    torso = _ellipsoid(P, (0, 9.2, -0.05), (1.4, 1.9, 0.95)) < 0
    shoulders = _capsule(P, (-1.5, 10.5, -0.1), (1.5, 10.5, -0.1), 0.62) < 0
    neck = _capsule(P, (0, 10.8, 0), (0, 11.8, 0.05), 0.42) < 0
    head = _ellipsoid(P, (0, 12.55, 0.12), (0.92, 1.08, 0.95)) < 0
    hair = (_ellipsoid(P, (0, 12.5, -0.4), (1.02, 1.25, 0.85)) < 0) | (
        _capsule(P, (0, 12.4, -0.65), (0, 10.0, -1.0), 0.72) < 0)
    arm_r = (_capsule(P, (1.6, 10.5, 0), (2.45, 12.5, 0.15), 0.55) < 0) | (
        _capsule(P, (2.45, 12.5, 0.15), (2.65, 14.6, 0.25), 0.5) < 0)
    hand_r = _ellipsoid(P, (2.7, 15.15, 0.3), (0.5, 0.6, 0.5)) < 0
    arm_l = (_capsule(P, (-1.6, 10.5, 0), (-2.3, 8.9, 0.55), 0.55) < 0) | (
        _capsule(P, (-2.3, 8.9, 0.55), (-2.7, 8.3, 1.8), 0.48) < 0)
    sleeve_l = _ellipsoid(P, (-2.35, 8.6, 0.8), (0.75, 0.6, 0.7)) < 0

    # wings: swept surfaces from the shoulder blades, up and out, with scalloped primaries
    wing = np.zeros(X.shape, bool)
    wing_edge = np.zeros(X.shape, bool)
    wing_tip = np.zeros(X.shape, bool)
    for s in (1, -1):
        p0 = np.array([s * 0.9, 10.4, -1.0])
        p1 = np.array([s * 3.2, 16.0, -1.9])
        p2 = np.array([s * 7.6, 18.4, -2.9])
        for i in range(161):
            a = i / 160.0
            L = (1 - a) ** 2 * p0 + 2 * (1 - a) * a * p1 + a * a * p2
            chord = 3.2 + 4.6 * math.sin(math.pi * min(1.0, a * 1.15)) * (1.0 - 0.45 * a)
            chord *= 1.0 - 0.17 * abs(math.sin(4.5 * math.pi * a)) * min(1.0, a * 3)
            chord = max(chord * (1.0 - a ** 6), 0.4)
            dvec = np.array([s * (0.25 + 0.35 * a), -1.0, -0.12])
            dvec /= np.linalg.norm(dvec)
            for j in range(41):
                b = j / 40.0
                Q = (L + dvec * chord * b) * scale
                for off in (0.0, 0.45):
                    qx, qy, qz = Q[0], Q[1] - 0.5, Q[2] - off
                    ix, iy, iz = int(round(qx)) + R, int(round(qy)), int(round(qz)) + 6
                    if 0 <= ix < X.shape[0] and 0 <= iy < X.shape[1] and 0 <= iz < X.shape[2]:
                        wing[ix, iy, iz] = True
                        if b < 0.12:
                            wing_edge[ix, iy, iz] = True
                        if b > 0.8 and a > 0.25:
                            wing_tip[ix, iy, iz] = True
    body = robe | belt | torso | shoulders | neck | head | hair | arm_r | hand_r | arm_l | sleeve_l
    mats = [
        (wing & ~body & ~wing_edge & ~wing_tip, B.S("quartz_bricks")),
        (wing_tip & ~body, B.S("calcite")),
        (wing_edge & ~body, B.S("smooth_quartz")),
        (robe, B.S("quartz_pillar", axis="y")),
        (torso | shoulders | sleeve_l, B.S("quartz_block")),
        (arm_r | arm_l | hand_r | neck | head, B.S("smooth_quartz")),
        (hair & ~head, B.S("calcite")),
        (belt, K.F(K.GOLD)),
    ]
    for m, st in mats:
        ii, jj, kk = np.nonzero(m)
        w.set_many(cx + xs[ii], y0 + ys[jj], cz + zs[kk], st)
    # diadem and the star
    hy = y0 + int(round(13.4 * scale))
    w.set(cx, hy, cz, B.slab(K.GOLD, "bottom"))
    sx, sy, sz = cx + int(round(2.7 * scale)), y0 + int(round(16.4 * scale)), cz + int(round(0.3 * scale))
    w.set(sx, sy, sz, B.S("ochre_froglight", axis="y"))
    w.set(sx, sy + 1, sz, B.S("end_rod", facing="up"))
    w.set(sx, sy - 1, sz, K.F(K.GOLD))
    for (dx, dz, fac) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        w.set(sx + dx, sy, sz + dz, B.S("end_rod", facing=fac))
    return sy


# ============================================================================== beds, benches, lamps, masts
def flower_beds(w):
    """Four raised flower beds on the diagonals between the avenue axes, each with a flowering tree."""
    R = 18
    X, Z = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    r = np.sqrt(X * X + Z * Z)
    ang = np.degrees(np.arctan2(Z, X)) % 360.0
    flowers = ["poppy", "red_tulip", "pink_tulip", "white_tulip", "oxeye_daisy", "cornflower", "allium",
               "azure_bluet", "lily_of_the_valley", "dandelion", "blue_orchid"]
    for k in range(4):
        a = 45.0 + 90.0 * k
        da = np.abs(((ang - a + 180) % 360) - 180)
        bed = (r >= 12.4) & (r <= 17.4) & (da <= 22.0)
        inner = (r >= 13.4) & (r <= 16.5) & (da <= 18.5)
        border = bed & ~inner
        bx, bz = X[border], Z[border]
        w.set_many(bx, np.ones(bx.size, int), bz, K.F(K.CB))
        w.set_many(bx, np.full(bx.size, 2), bz, B.slab(K.SM, "bottom"))
        ix, iz = X[inner], Z[inner]
        w.set_many(ix, np.zeros(ix.size, int), iz, B.S("dirt"))
        w.set_many(ix, np.ones(ix.size, int), iz, B.S("grass_block"))
        for x, z in zip(ix, iz):
            u = hash2(int(x), int(z), 51 + k)
            if u < 0.08:
                deco.bush(w, int(x), 1, int(z), seed=int(x * 7 + z), r=1.1, kind="flowering_azalea")
            elif u < 0.9:
                # colour drifts: neighbouring blocks share a flower
                c = int(hash2(int(x) // 2, int(z) // 2, 61 + k) * len(flowers))
                w.set(int(x), 2, int(z), B.S(flowers[c]), only_empty=True)
        ca = math.radians(a)
        tx, tz = int(round(15.0 * math.cos(ca))), int(round(15.0 * math.sin(ca)))
        w.set(tx, 2, tz, AIR)
        deco.tree_oak(w, tx, 1, tz, seed=7 + k, height=5, crown=2.6, kind="birch" if k % 2 else "oak")


def benches(w):
    """Benches flanking each avenue path, facing the path, and a lamp at the inner end of each pair."""
    for k, (ax, az) in enumerate(((0, 1), (0, -1), (1, 0), (-1, 0))):
        for s in (-1, 1):
            if az:
                x, z0 = 5 * s, 14 * az
                facing = "west" if s > 0 else "east"
                # bench runs along z (3 long) towards the outside
                zs = (z0, z0 + az, z0 + 2 * az)
                start = min(zs)
                deco.bench(w, x, 0, start, facing=facing, length=3)
            else:
                x0, z = 14 * ax, 5 * s
                facing = "north" if s > 0 else "south"
                xs = (x0, x0 + ax, x0 + 2 * ax)
                deco.bench(w, min(xs), 0, z, facing=facing, length=3)


def lamps_and_masts(w):
    # inner lamps flanking the four paths into the fountain ring
    for (ax, az) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        for s in (-1, 1):
            x = 3 * s if az else 11 * ax
            z = 11 * az if az else 3 * s
            K.lamp(w, x, 0, z, height=3)
    # outer lamps on the diagonals near the kerb
    for (sx, sz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        K.lamp(w, 14 * sx, 0, 14 * sz, height=4)
    # banner masts flanking the four avenue mouths
    for (ax, az) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        for s in (-1, 1):
            if az:
                x, z = 8 * s, 19 * az
                sides = ("east", "west")
            else:
                x, z = 19 * ax, 8 * s
                sides = ("north", "south")
            K.mast(w, x, 0, z, height=10, sides=sides)


def jets(w):
    """Animated jets: arcs from the four cardinal rim posts into the pool, tall jets at the diagonals and a
    ring of small bubblers between them."""
    for (dx, dz, fac) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        TK.spray(w, 8 * dx, 2, 8 * dz, power=2, facing=fac)
        w.set(9 * dx, 2, 9 * dz, K.F(K.GOLD))                  # gilded mask on the post
    for k in range(4):
        a = math.radians(45 + 90 * k)
        TK.spray(w, int(round(7.0 * math.cos(a))), 1, int(round(7.0 * math.sin(a))), power=3)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        TK.spray(w, int(round(7.4 * math.cos(a))), 1, int(round(7.4 * math.sin(a))), power=1)


def flags(w):
    """Kingdom flags on the eight banner masts (the town's wind blows from the west: cloth streams east)."""
    for (ax, az) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        for s in (-1, 1):
            x, z = (8 * s, 19 * az) if az else (19 * ax, 8 * s)
            TK.flag(w, x, 11, z, "east", "kingdom", 3.0, 2.0)


def flagstones(w):
    """Re-lay the plaza field in large flagstones with smooth castle stone rings; the ring road in cobbles."""
    R = 26
    X, Z = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    r = np.sqrt(X * X + Z * Z)
    ids_old = [w.idx(B.S(n)) for n in ("smooth_stone", "calcite", "polished_diorite")]
    road_old = [w.idx(B.S(n)) for n in ("stone_bricks", "polished_andesite", "andesite", "cobblestone",
                                        "cracked_stone_bricks")]
    from capital.world import X0 as WX0, Z0 as WZ0, Y0 as WY0
    cur = w.a[X - WX0, Z - WZ0, 0 - WY0]
    field = (r <= 21.6) & np.isin(cur, ids_old) & (r > 10.5)
    ring = field & (np.abs(r - 15.5) < 0.5)
    road = (r > 22.4) & (r <= 26.4) & np.isin(cur, road_old)
    w.a[X[field] - WX0, Z[field] - WZ0, 0 - WY0] = w.idx(B.S("plaza_flagstones"))
    w.a[X[ring] - WX0, Z[ring] - WZ0, 0 - WY0] = w.idx(K.F(K.SM))
    u = hash2(X[road], Z[road], 2301)
    w.a[X[road] - WX0, Z[road] - WZ0, 0 - WY0] = np.where(u < 0.85, w.idx(B.S("cobbled_paving")),
                                                         w.idx(B.S("stone_bricks"))).astype(np.uint16)
    edge = (r > 21.6) & (r <= 22.4)
    w.a[X[edge] - WX0, Z[edge] - WZ0, 0 - WY0] = w.idx(K.F(K.SM))


def build(w, district, terrain):
    flagstones(w)
    paving(w)
    fountain(w)
    angel(w, 0, STATUE_Y, 0, scale=1.0)
    flower_beds(w)
    benches(w)
    lamps_and_masts(w)
    jets(w)
    flags(w)
    w.spot("spawn", (0, 1, 14), 180.0)
    TK.crowd(w, "plaza", [(-6, 1, 18), (6, 1, 18), (18, 1, 6), (18, 1, -6), (-18, 1, 6), (-18, 1, -6),
                          (6, 1, -18), (-6, 1, -18), (12, 1, 12), (-12, 1, 12), (12, 1, -12), (-12, 1, -12),
                          (0, 1, 22)], need=22)
