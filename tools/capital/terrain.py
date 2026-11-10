"""Terrain of the capital: meadows, castle plateau and cliff, NE mountains with the great waterfall, NW ridge,
lake, river, roads with kerbs, bridges, plaza paving and the floating islands.

Columns are written from ``FOUND_Y`` (or deeper under water) up to the top of the box; below that the
columns stay void (natural stone is kept by the loader).
"""
from __future__ import annotations

import math

import numpy as np

from . import blocks as B
from . import deco
from . import layout as L
from .noise import fbm2, hash2, hash3, seed_of, value2
from .world import NX, NY, NZ, X0, Y0, Z0, X1, Z1, AIR as AIR_I

FOUND_Y = -6
SNOW_Y = 84


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Terrain:
    """Holds the heightfield and surface classes so other builders can query them."""

    def __init__(self):
        X, Z = np.meshgrid(np.arange(X0, X1 + 1), np.arange(Z0, Z1 + 1), indexing="ij")
        self.X, self.Z = X, Z
        self.H = np.zeros((NX, NZ), np.int32)       # top solid block y
        self.water = np.full((NX, NZ), -999, np.int32)  # top water block y (or -999)
        self.kind = np.zeros((NX, NZ), np.uint8)    # surface kind (see K_*)
        self.free = np.zeros((NX, NZ), bool)        # inside a free district (keep plain)
        self.road = np.zeros((NX, NZ), bool)

    def h(self, x, z):
        return int(self.H[x - X0, z - Z0])


K_GRASS, K_ROCK, K_GRASS_ROCK, K_SAND, K_BED, K_SNOW, K_GRAVEL, K_PLATEAU = range(8)


# ============================================================================== heightfield
def compute(T: Terrain):
    X, Z = T.X.astype(np.float64), T.Z.astype(np.float64)
    edge = L.edge_distance()
    fade = smoothstep(L.EDGE_BAND, L.EDGE_BAND + 18, edge)
    H = np.zeros_like(X)

    # --- city & free districts stay flat at 0
    for d in L.DISTRICTS.values():
        if d.owner == "free":
            T.free |= d.mask()
    city = (np.abs(X) <= 110) & (Z >= -90) & (Z <= 97)
    city_soft = np.zeros_like(X)
    dcity = np.maximum.reduce([np.abs(X) - 110, -90 - Z, Z - 97, np.zeros_like(X)])
    city_soft = 1.0 - smoothstep(0, 14, dcity)

    # --- rural undulation (gentle meadows)
    n1 = fbm2(X, Z, 46.0, 3, seed_of("meadow"))
    meadow = np.clip(n1 * 3.2 + 0.6, 0.0, 4.0) * fade * (1.0 - city_soft)
    H = np.maximum(H, meadow)

    # --- NW ridge (wooded hill behind the cathedral)
    rx = (X + 128.0) / 44.0
    rz = (Z + 152.0) / 52.0
    rr = np.sqrt(rx * rx + rz * rz) + fbm2(X, Z, 30.0, 3, seed_of("ridge")) * 0.18
    ridge = 36.0 * smoothstep(1.0, 0.25, rr) + fbm2(X, Z, 14.0, 2, seed_of("ridge2")) * 3.0 * (rr < 0.9)
    H = np.maximum(H, ridge * fade)

    # --- castle plateau with cliff
    jit = fbm2(X, Z, 22.0, 3, seed_of("plateau-rim")) * 5.0
    dP = L.plateau_distance(X, Z, jit)
    steep = 3.2 + 2.2 * value2(X, Z, 18.0, seed_of("steep"))
    front = (np.abs(X) < 58) & (Z > -110)
    steep = np.where(front, 7.5, steep)
    ledge = fbm2(X, Z, 9.0, 2, seed_of("ledge")) * 2.5
    cliff = L.PLATEAU_Y + dP * steep + ledge * (dP < 0)
    cliff = np.where(cliff > L.PLATEAU_Y - 3, np.minimum(cliff, L.PLATEAU_Y),
                     cliff + fbm2(X, Z, 5.0, 2, seed_of("crag")) * 2.0)
    hP = np.where(dP >= 0, float(L.PLATEAU_Y), np.clip(cliff, 0, L.PLATEAU_Y))
    plateau_top = dP >= 0
    H = np.maximum(H, hP)

    # --- NE mountain massif
    M = L.MOUNTAIN
    mj = fbm2(X, Z, 20.0, 3, seed_of("massif-rim")) * 7.0
    mdx = np.abs(X - M["cx"]) / (38.0 + mj * 0.6)
    mdz = np.abs(Z - M["cz"]) / (58.0 + mj)
    mf = np.power(mdx ** 2.2 + mdz ** 2.2, 1 / 2.2)
    dM = (1.0 - mf) * 44.0
    ridges = 1.0 - np.abs(fbm2(X, Z, 26.0, 4, seed_of("ridges")))
    crag = fbm2(X, Z, 9.0, 3, seed_of("massif-crag"))
    # steep faces towards the castle and the city (west / south), long slopes towards the edges
    facing_city = smoothstep(-10.0, 10.0, (M["cx"] - X) * 0.8 + (Z - M["cz"]) * 0.6)
    ramp = 30.0 - 17.0 * facing_city
    hM = np.where(dM > 0, 62.0 * smoothstep(0.0, ramp, dM + crag * 4.0) +
                  50.0 * ridges * smoothstep(4.0, 34.0, dM) + crag * 5.0 * smoothstep(0.0, 8.0, dM), 0.0)
    step_h = 6.0 + 3.0 * value2(X, Z, 23.0, seed_of("terr"))
    hM = np.where((hM > 4) & (hM < 66), np.floor(hM / step_h) * step_h + step_h * 0.6 *
                  smoothstep(0, step_h, hM % step_h), hM)
    hM = np.where(hM > 92, 92 + (hM - 92) * 0.45, hM)       # round off the summits
    hM *= smoothstep(L.EDGE_BAND - 6, L.EDGE_BAND + 10, edge)
    H = np.maximum(H, hM)

    # --- the waterfall cirque, hanging valley and pool
    WF = L.WATERFALL
    wx0, wx1, lip = WF["x0"], WF["x1"], WF["lip_z"]
    pcx, pcz = WF["pool"]
    pool = ((X - pcx) / 14.5) ** 2 + ((Z - pcz) / 10.5) ** 2 <= 1.0
    wall_zone = (X >= wx0 - 8) & (X <= wx1 + 8) & (Z >= lip - 6) & (Z <= lip)
    H = np.where(wall_zone, np.maximum(H, 66.0), H)
    # open the cirque towards the city (south-west): lower the massif's flank in front of the fall
    front = (Z > lip) & (X < wx1 + 4) & (X > 100) & ~pool
    flank = np.clip((Z - lip) * 2.2 + (wx0 - X) * 0.0, 0, 80)
    H = np.where(front, np.minimum(H, np.maximum(0.0, 64.0 - flank * 1.6)), H)
    valley = ((X - 126) / 8.5) ** 2 + ((Z - (lip - 13)) / 8.0) ** 2 <= 1.0
    valley_rim = ((X - 126) / 12.5) ** 2 + ((Z - (lip - 13)) / 12.0) ** 2 <= 1.0
    H = np.where(valley_rim & ~valley, np.maximum(H, 64.0), H)
    chan = (X >= wx0) & (X <= wx1) & (Z > lip - 8) & (Z <= lip)
    H = np.where(valley | chan, WF["top_y"] - 2.0, H)
    H = np.where(chan & (Z == lip), WF["top_y"] - 1.0, H)
    pdist = np.sqrt(((X - pcx) / 14.5) ** 2 + ((Z - pcz) / 10.5) ** 2)
    H = np.where(pool & (Z > lip), -8.0 + 5.0 * pdist, H)

    # --- lake
    lj = fbm2(X, Z, 16.0, 2, seed_of("lake")) * 2.0
    dL = L.lake_distance(X, Z, lj)
    lake = dL > 0
    bed = -3.0 - np.clip(dL * 0.55, 0, 10.0)
    beach = (dL > -3.5) & ~lake
    H = np.where(lake, bed, H)
    H = np.where(beach, np.minimum(H, np.where(dL > -1.5, -1.0, 0.0)), H)

    # --- river
    rd = _polyline_dist(X, Z, L.RIVER_PTS)
    rj = fbm2(X, Z, 12.0, 2, seed_of("river")) * 1.0
    river = (rd <= L.RIVER_HALF + rj) & (Z > lip + 4)
    rbed = -3.0 - 2.5 * (1.0 - np.clip(rd / (L.RIVER_HALF + 1.0), 0, 1))
    bank = (rd <= L.RIVER_HALF + 2.2 + rj) & ~river & (Z > lip + 4)
    H = np.where(river, np.minimum(H, rbed), H)
    H = np.where(bank, np.minimum(H, -1.0), H)

    # --- flatten roads and free districts
    for r in L.ROADS:
        d = _polyline_dist(X, Z, r.pts)
        corridor = d <= r.width / 2.0 + 3
        H = np.where(corridor & (H > 0) & (H < 8), 0.0, H)
    H = np.where(T.free & (H > 0) & (H < 8), 0.0, H)

    Hi = np.floor(H + 0.5).astype(np.int32)
    T.H = Hi
    T.plateau_top = plateau_top
    T.lake = lake
    T.river = river | (pool & (Z > lip))
    T.pool = pool & (Z > lip)
    T.valley = valley | chan
    water = np.full((NX, NZ), -999, np.int32)
    water[lake | T.river] = L.WATER_Y
    water[valley | chan] = WF["top_y"]
    T.water = water

    # --- surface kinds
    gx = np.abs(np.diff(Hi, axis=0, prepend=Hi[:1]))
    gx2 = np.abs(np.diff(Hi, axis=0, append=Hi[-1:]))
    gz = np.abs(np.diff(Hi, axis=1, prepend=Hi[:, :1]))
    gz2 = np.abs(np.diff(Hi, axis=1, append=Hi[:, -1:]))
    slope = np.maximum.reduce([gx, gx2, gz, gz2])
    kind = np.full((NX, NZ), K_GRASS, np.uint8)
    kind[slope >= 3] = K_ROCK
    kind[(Hi > 2) & (slope == 2) & (hash2(T.X, T.Z, 77) < 0.5)] = K_ROCK
    kind[plateau_top] = K_PLATEAU
    kind[(Hi >= SNOW_Y) & (slope < 4)] = K_SNOW
    kind[beach | bank] = K_SAND
    kind[(beach | bank) & (hash2(T.X, T.Z, 78) < 0.3)] = K_GRAVEL
    kind[lake | T.river | valley | chan] = K_BED
    T.kind = kind
    return T


def _polyline_dist(X, Z, pts):
    d = np.full(X.shape, 1e9)
    for (x0, z0), (x1, z1) in zip(pts[:-1], pts[1:]):
        vx, vz = x1 - x0, z1 - z0
        L2 = vx * vx + vz * vz
        t = np.clip(((X - x0) * vx + (Z - z0) * vz) / max(L2, 1e-9), 0, 1)
        px, pz = x0 + t * vx, z0 + t * vz
        d = np.minimum(d, np.sqrt((X - px) ** 2 + (Z - pz) ** 2))
    return d


# ============================================================================== column writer
ROCK_PALE = ["stone", "calcite", "andesite", "stone", "diorite", "tuff", "calcite", "stone", "andesite",
             "smooth_basalt"]
ROCK_PALE_W = [30, 18, 16, 0, 12, 8, 0, 0, 0, 2]


def write_columns(w, T: Terrain):
    ids = {n: w.idx(B.S(n)) for n in ["stone", "calcite", "andesite", "diorite", "tuff", "smooth_basalt",
                                       "dirt", "grass_block", "sand", "gravel", "clay", "coarse_dirt",
                                       "snow_block", "water", "sandstone", "moss_block", "rooted_dirt",
                                       "packed_ice", "podzol"]}
    rock_names = ["stone", "calcite", "andesite", "diorite", "tuff", "smooth_basalt"]
    rock_ids = np.array([ids[n] for n in rock_names], np.uint16)
    ys = np.arange(Y0, Y0 + NY)
    seed = seed_of("strata")
    for xs in range(0, NX, 16):
        xe = min(NX, xs + 16)
        Hs = T.H[xs:xe][:, :, None]
        Ks = T.kind[xs:xe][:, :, None]
        Ws = T.water[xs:xe][:, :, None]
        X = T.X[xs:xe][:, :, None]
        Z = T.Z[xs:xe][:, :, None]
        Y = ys[None, None, :]
        # rock: irregular 3D blobs (value noise on a squashed lattice) instead of regular strata
        warp = fbm2(T.X[xs:xe], T.Z[xs:xe], 34.0, 2, seed)[:, :, None] * 4.0
        bx = np.floor((X + warp) / 5.0).astype(np.int64)
        by = np.floor((Y + warp * 0.7) / 3.0).astype(np.int64)
        bz = np.floor((Z - warp) / 5.0).astype(np.int64)
        u = hash3(bx, by, bz, seed + 1)
        speck = hash3(X, Y, Z, seed + 2)
        # mostly mid-grey stone/andesite with pale (diorite/calcite) and tuff veins
        ri = np.where(u < 0.46, 0, np.where(u < 0.72, 2, np.where(u < 0.82, 4, np.where(u < 0.93, 3, 1))))
        ri = np.where(speck < 0.07, 2, np.where(speck > 0.96, 0, ri))
        rock = rock_ids[ri]
        out = np.full(Hs.shape[:2] + (NY,), AIR_I, np.uint16)
        below = Y <= Hs
        bottom = np.minimum(FOUND_Y, Hs - 4)
        out = np.where(Y < bottom, np.uint16(0), out)  # void deep down
        solid = below & (Y >= bottom)
        out = np.where(solid, ids["stone"], out)
        rocky = (Ks == K_ROCK) | (Hs > 2) & (Ks != K_GRASS) & (Ks != K_SAND) & (Ks != K_BED)
        out = np.where(solid & (Y > -6) & rocky, rock, out)
        depth = Hs - Y
        grassy = (Ks == K_GRASS) | (Ks == K_PLATEAU)
        out = np.where(solid & grassy & (depth == 0), ids["grass_block"], out)
        out = np.where(solid & grassy & (depth >= 1) & (depth <= 3), ids["dirt"], out)
        # rocky tops that are gentle get a grass skin
        out = np.where(solid & (Ks == K_ROCK) & (depth == 0) & (Hs > 10) & (speck < 0.0), ids["grass_block"], out)
        snow = Ks == K_SNOW
        out = np.where(solid & snow & (depth == 0), ids["snow_block"], out)
        out = np.where(solid & snow & (depth >= 1) & (depth <= 2), ids["packed_ice"], out) if False else out
        sand = Ks == K_SAND
        out = np.where(solid & sand & (depth <= 2), ids["sand"], out)
        grav = Ks == K_GRAVEL
        out = np.where(solid & grav & (depth <= 1), ids["gravel"], out)
        bedk = Ks == K_BED
        bedmat = np.where(hash2(T.X[xs:xe], T.Z[xs:xe], 91)[:, :, None] < 0.55, ids["sand"],
                          np.where(hash2(T.X[xs:xe], T.Z[xs:xe], 92)[:, :, None] < 0.5, ids["gravel"], ids["clay"]))
        out = np.where(solid & bedk & (depth <= 1), bedmat, out)
        out = np.where(solid & bedk & (depth >= 2) & (depth <= 3), ids["sandstone"], out) if False else out
        # water
        wet = (Y > Hs) & (Y <= Ws)
        out = np.where(wet, ids["water"], out)
        # footprint
        fp = w.footprint[xs:xe][:, :, None]
        out = np.where(fp, out, np.uint16(0))
        w.a[xs:xe] = out
    return T


# ============================================================================== cliff dressing
def cliff_dressing(w, T: Terrain):
    """Vines hanging down cliff faces, moss patches and grassy ledges (deterministic patches)."""
    H = T.H
    seed = seed_of("cliff")
    moss_ids = [w.idx(B.S("mossy_cobblestone")), w.idx(B.S("moss_block"))]
    for (dx, dz, side) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        # columns whose neighbour (x+dx, z+dz) is much lower: the face looks towards the neighbour
        Hn = np.roll(np.roll(H, -dx, axis=0), -dz, axis=1)
        drop = H - Hn
        cand = (drop >= 4) & (T.kind == K_ROCK) | (drop >= 4) & (T.H > 10) & (T.kind != K_BED)
        cand &= T.water == -999
        xi, zi = np.nonzero(cand)
        for i, j in zip(xi, zi):
            x, z = int(T.X[i, j]), int(T.Z[i, j])
            nx, nz = x + dx, z + dz
            if not (X0 <= nx <= X1 and Z0 <= nz <= Z1):
                continue
            top, bot = int(H[i, j]), int(Hn[i, j]) + 1
            p = value2(x, z, 7.0, seed)
            if p > 0.62:
                ln = int((p - 0.62) * 40) + 2
                for y in range(top, max(bot, top - ln) - 1, -1):
                    if w.get_idx(nx, y, nz) == AIR_I:
                        w.set(nx, y, nz, B.S("vine", **{side: True}))
            elif p < 0.22:
                for y in range(max(bot, top - 10), top + 1):
                    if hash3(x, y, z, seed + 1) < 0.5:
                        w.set_idx_many([x], [y], [z], moss_ids[int(hash3(x, y, z, seed + 2) * 2)])


# ============================================================================== vegetation
def vegetation(w, T: Terrain):
    """Trees, bushes, grass and flowers outside the free districts, the city and the roads."""
    rng_seed = seed_of("veg")
    X, Z, H, K = T.X, T.Z, T.H, T.kind
    near_road = _road_mask(T, pad=2)
    blocked = T.free | near_road | (T.water > -999)
    edge = L.edge_distance()
    flowers = ["poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "white_tulip",
               "red_tulip", "lily_of_the_valley"]
    grass_id = w.idx(B.S("grass"))
    # ground cover
    u = hash2(X, Z, rng_seed)
    v = hash2(Z, X, rng_seed + 1)
    ok = ((K == K_GRASS) | (K == K_PLATEAU)) & ~blocked & ~T.plateau_top_castle
    gx, gz = np.nonzero(ok & (u < 0.22))
    w.set_idx_many(gx + X0, H[gx, gz] + 1, gz + Z0, grass_id, only_empty=True)
    fl = ok & (u >= 0.22) & (u < 0.26)
    fx, fz = np.nonzero(fl)
    for i, f in enumerate(flowers):
        sel = (v[fx, fz] * len(flowers)).astype(int) == i
        if sel.any():
            w.set_many(fx[sel] + X0, H[fx[sel], fz[sel]] + 1, fz[sel] + Z0, B.S(f), only_empty=True)
    # trees on a jittered grid
    cell = 9
    for cx in range(X0, X1 + 1, cell):
        for cz in range(Z0, Z1 + 1, cell):
            hx = int(hash2(cx, cz, rng_seed + 3) * (cell - 2)) + 1
            hz = int(hash2(cz, cx, rng_seed + 4) * (cell - 2)) + 1
            x, z = cx + hx, cz + hz
            if not (X0 + 2 <= x <= X1 - 2 and Z0 + 2 <= z <= Z1 - 2):
                continue
            i, j = x - X0, z - Z0
            if not w.footprint[i, j] or blocked[i, j] or T.plateau_top_castle[i, j]:
                continue
            k = K[i, j]
            h = int(H[i, j])
            e = edge[i, j]
            p = hash2(x, z, rng_seed + 5)
            if k == K_ROCK and h > 30 and p < 0.25:
                deco.tree_spruce(w, x, h, z, seed=rng_seed)
                continue
            if k not in (K_GRASS, K_PLATEAU):
                continue
            if h >= 60:
                if p < 0.45:
                    deco.tree_spruce(w, x, h, z, seed=rng_seed)
                continue
            density = 0.10
            if T.ridge_zone[i, j]:
                density = 0.55
            elif T.lakeshore[i, j]:
                density = 0.30
            elif T.cliff_foot[i, j]:
                density = 0.35
            elif e < 30:
                density = 0.18
            if p < density:
                r2 = hash2(x, z, rng_seed + 6)
                if T.ridge_zone[i, j] and r2 < 0.45:
                    deco.tree_spruce(w, x, h, z, seed=rng_seed, height=int(7 + r2 * 8))
                elif r2 < 0.2:
                    deco.tree_oak(w, x, h, z, seed=rng_seed, kind="birch")
                elif r2 < 0.3:
                    deco.bush(w, x, h, z, seed=rng_seed, kind="azalea")
                else:
                    deco.tree_oak(w, x, h, z, seed=rng_seed)


def _road_mask(T, pad=0):
    m = np.zeros(T.X.shape, bool)
    for r in L.ROADS:
        m |= _polyline_dist(T.X, T.Z, r.pts) <= r.width / 2.0 + pad
    return m


# ============================================================================== roads & plaza
AVENUE_MIX = (["stone_bricks", "polished_andesite", "andesite", "cobblestone", "cracked_stone_bricks",
               "stone_bricks"], [46, 18, 14, 12, 5, 5])
STREET_MIX = (["cobblestone", "stone_bricks", "andesite", "mossy_cobblestone", "gravel", "polished_andesite"],
              [38, 26, 18, 6, 6, 6])
RURAL_MIX = (["dirt_path", "coarse_dirt", "gravel", "cobblestone", "dirt_path"], [44, 16, 18, 12, 10])


def _mix_ids(w, mix, X, Z, seed):
    names, weights = mix
    c = np.cumsum(np.asarray(weights, float))
    c /= c[-1]
    u = hash2(X, Z, seed)
    k = np.searchsorted(c, u, side="right").clip(0, len(names) - 1)
    ids = np.array([w.idx(B.S(n)) for n in names], np.uint16)
    return ids[k]


def roads(w, T: Terrain):
    X, Z = T.X, T.Z
    kerb_id = w.idx(B.S("smooth_stone"))
    for r in L.ROADS:
        d = _polyline_dist(X, Z, r.pts)
        half = r.width / 2.0
        on = (d <= half) & (T.water == -999) & (np.abs(T.H) <= 1) & w.footprint
        if not on.any():
            continue
        rural = (np.abs(X) > 110) | (Z > 140)
        mix = AVENUE_MIX if r.kind == "avenue" else STREET_MIX
        ids = _mix_ids(w, mix, X, Z, seed_of(r.name))
        ids_r = _mix_ids(w, RURAL_MIX, X, Z, seed_of(r.name) + 1)
        ids = np.where(rural, ids_r, ids)
        if r.kerb:
            kerb = (d > half - 1.0) & ~rural
            ids = np.where(kerb, kerb_id, ids)
        xi, zi = np.nonzero(on)
        w.a[xi, zi, 0 - Y0] = ids[xi, zi]
        w.a[xi, zi, -1 - Y0] = w.idx(B.S("stone"))
        for yy in range(1, 4):
            w.a[xi, zi, yy - Y0] = AIR_I
        T.H[xi, zi] = 0
        T.road[xi, zi] = True


def plaza(w, T: Terrain):
    """Radial paving of the plaza disc (the plaza builder adds the fountain, beds, lamps)."""
    X, Z = T.X, T.Z
    r = np.sqrt(X * X + Z * Z)
    ang = (np.degrees(np.arctan2(Z, X)) + 360.0) % 360.0
    disc = r <= L.PLAZA_RING_R + 0.4
    ring = np.floor(r / 2.0).astype(int)
    rings = [w.idx(B.S(n)) for n in ("polished_diorite", "smooth_stone", "calcite", "smooth_stone")]
    ids = np.array(rings, np.uint16)[ring % 4]
    spoke = (np.abs(((ang + 11.25) % 22.5) - 11.25) * np.pi / 180.0 * np.maximum(r, 0.1) < 0.6) & (r > 11)
    ids = np.where(spoke, w.idx(B.S("stone_bricks")), ids)
    outer = r > L.PLAZA_R
    ids = np.where(outer, _mix_ids(w, AVENUE_MIX, X, Z, seed_of("plaza-ring")), ids)
    ids = np.where((r > L.PLAZA_R - 0.6) & (r <= L.PLAZA_R + 0.4), w.idx(B.S("smooth_stone")), ids)
    ids = np.where(r <= L.FOUNTAIN_R + 0.4, w.idx(B.S("polished_diorite")), ids)
    xi, zi = np.nonzero(disc & w.footprint)
    w.a[xi, zi, 0 - Y0] = ids[xi, zi]
    for yy in range(1, 4):
        w.a[xi, zi, yy - Y0] = AIR_I
    T.H[xi, zi] = 0
    T.road[xi, zi] = True


def avenue_lamps(w, T: Terrain):
    """Lamp posts on the kerbs of the avenues (every 14 blocks) inside the city."""
    for r in L.ROADS:
        if r.kind != "avenue":
            continue
        (x0, z0), (x1, z1) = r.pts[0], r.pts[-1]
        n = int(max(abs(x1 - x0), abs(z1 - z0)))
        ux, uz = (np.sign(x1 - x0), np.sign(z1 - z0))
        half = r.width // 2
        for i in range(4, n, 14):
            x, z = x0 + ux * i, z0 + uz * i
            if abs(x) > 104 or z > 92:
                continue
            for s in (-1, 1):
                px, pz = (x + s * half * abs(uz), z + s * half * abs(ux))
                if T.water[px - X0, pz - Z0] != -999:
                    continue
                deco.lamp_post(w, px, 0, pz, height=4)


# ============================================================================== bridges
def lake_bridge(w, T: Terrain):
    """Long arched stone bridge carrying the south road across the lake."""
    b = L.LAKE_BRIDGE
    x0, x1, za, zb, dy = b["x0"], b["x1"], b["z0"], b["z1"], b["deck_y"]
    # find the shores along x=0
    col = T.water[0 - X0]
    zs = [z for z in range(za - 10, zb + 10) if col[z - Z0] != -999]
    if not zs:
        return
    n_shore, s_shore = min(zs), max(zs)
    deck_z0, deck_z1 = n_shore - 2, s_shore + 2
    stone = B.full("stone_bricks")
    trim = B.full("smooth_castle_stone")
    for z in range(deck_z0 - dy * 2, deck_z1 + dy * 2 + 1):
        # deck height: ramps at both ends (2 blocks run per 1 rise using slabs)
        if z < deck_z0:
            hy = (z - (deck_z0 - dy * 2)) / 2.0
        elif z > deck_z1:
            hy = ((deck_z1 + dy * 2) - z) / 2.0
        else:
            hy = dy
        top = int(math.floor(hy))
        half = hy - top >= 0.5
        for x in range(x0, x1 + 1):
            ground = T.H[x - X0, z - Z0]
            bottom = min(ground + 1, 0)
            if top >= 1 or half:
                w.fill(x, bottom, z, x, top, z, stone) if top >= bottom else None
            if half:
                w.set(x, top + 1, z, B.slab("stone_bricks", "bottom"))
                surf = top + 1
            else:
                w.set(x, top, z, _pave(x, z))
                surf = top
            w.fill(x, surf + 1, z, x, surf + 4, z, B.S("air"))
        # parapets
        for x in (x0 - 1, x1 + 1):
            w.fill(x, min(T.H[x - X0, z - Z0] + 1, 0), z, x, top, z, stone)
            w.set(x, top + 1, z, B.wall("stone_bricks") if (z % 6) else trim)
            if z % 6 == 0:
                w.set(x, top + 2, z, B.slab("smooth_castle_stone", "bottom"))
    # arches over the water: carve openings between piers every 8 blocks
    for z0 in range(deck_z0 + 2, deck_z1 - 2, 9):
        za_, zb_ = z0 + 1, min(z0 + 7, deck_z1 - 2)
        span = zb_ - za_ + 1
        rr = span / 2.0
        cz = (za_ + zb_) / 2.0
        for z in range(za_, zb_ + 1):
            dz = abs(z - cz)
            crown = int(math.floor(dy - 1 - (1 - math.sqrt(max(0.0, 1 - (dz / (rr + 0.2)) ** 2))) * (dy + 1)))
            for x in range(x0 - 1, x1 + 2):
                ground = T.H[x - X0, z - Z0]
                if T.water[x - X0, z - Z0] == -999:
                    continue
                if crown > L.WATER_Y:
                    w.fill(x, ground + 1, z, x, L.WATER_Y, z, B.S("water"))
                    if crown - 1 > L.WATER_Y:
                        w.fill(x, L.WATER_Y + 1, z, x, crown - 1, z, B.S("air"))
        # cutwaters on the piers
        for pz in (z0 - 1, z0, z0 + 1)[1:2]:
            for s in (-1, 1):
                w.set(x0 - 2 if s < 0 else x1 + 2, L.WATER_Y + 1, pz,
                      B.stairs("stone_bricks", "east" if s < 0 else "west"))
    # lamps on the bridge
    for z in range(deck_z0, deck_z1 + 1, 12):
        for x in (x0 - 1, x1 + 1):
            deco.lamp_post(w, x, dy, z, height=3)


def _pave(x, z):
    names = ["stone_bricks", "polished_andesite", "andesite", "cobblestone"]
    return B.S(names[int(hash2(x, z, 4242) * len(names))])


def river_bridge(w, T: Terrain):
    """Humped stone arch bridge carrying the east road over the river."""
    b = L.RIVER_BRIDGE
    z0, z1 = b["z0"], b["z1"]
    row = T.water[:, 0 - Z0]
    xs = [x for x in range(b["x0"], b["x1"] + 1) if row[x - X0] != -999]
    if not xs:
        return
    wx0, wx1 = min(xs), max(xs)
    dy = b["deck_y"]
    xa, xb = wx0 - 2 - dy * 2, wx1 + 2 + dy * 2
    stone = B.full("stone_bricks")
    for x in range(xa, xb + 1):
        if x < wx0 - 2:
            hy = (x - xa) / 2.0
        elif x > wx1 + 2:
            hy = (xb - x) / 2.0
        else:
            hy = dy
        top = int(math.floor(hy))
        half = hy - top >= 0.5
        for z in range(z0, z1 + 1):
            ground = T.H[x - X0, z - Z0]
            bottom = min(ground + 1, 0)
            if top >= bottom:
                w.fill(x, bottom, z, x, top, z, stone)
            if half:
                w.set(x, top + 1, z, B.slab("stone_bricks", "bottom"))
            elif top >= 0:
                w.set(x, top, z, _pave(x, z))
            w.fill(x, top + 2, z, x, top + 5, z, B.S("air"))
        for z in (z0 - 1, z1 + 1):
            ground = T.H[x - X0, z - Z0]
            w.fill(x, min(ground + 1, 0), z, x, top, z, stone)
            w.set(x, top + 1, z, B.wall("stone_bricks"))
    # the arch opening over the water
    cx = (wx0 + wx1) / 2.0
    rr = (wx1 - wx0) / 2.0 + 1.0
    for x in range(wx0 - 1, wx1 + 2):
        dx = abs(x - cx)
        crown = int(math.floor(L.WATER_Y + (dy + 1 - L.WATER_Y) * math.sqrt(max(0.0, 1 - (dx / (rr + 0.5)) ** 2))))
        for z in range(z0 - 1, z1 + 2):
            if T.water[x - X0, z - Z0] == -999:
                continue
            w.fill(x, T.H[x - X0, z - Z0] + 1, z, x, L.WATER_Y, z, B.S("water"))
            if crown > L.WATER_Y + 1:
                w.fill(x, L.WATER_Y + 1, z, x, min(crown, dy - 1), z, B.S("air"))


# ============================================================================== waterfall
def waterfall(w, T: Terrain):
    WF = L.WATERFALL
    top = WF["top_y"]
    lip = WF["lip_z"]
    for x in range(WF["x0"], WF["x1"] + 1):
        z = lip + 1
        # spill block (horizontal flow from the lip sources), then the falling column down to the pool
        w.set(x, top, z, B.S("water", level=1))
        pool_top = int(T.water[x - X0, z - Z0]) if T.water[x - X0, z - Z0] != -999 else L.WATER_Y
        for y in range(pool_top + 1, top):
            w.set(x, y, z, B.S("water", level=8))
        w.fluid_tick(x, top, z)
        # mossy, wet rock around the fall
    for x in range(WF["x0"] - 3, WF["x1"] + 4):
        for y in range(L.WATER_Y + 1, top + 1):
            z = lip
            cur = w.get(x, y, z)
            if "water" in cur or cur.endswith(":air"):
                continue
            if hash3(x, y, z, 515) < 0.35:
                w.set(x, y, z, B.S("mossy_cobblestone") if hash3(x, y, z, 516) < 0.5 else B.S("moss_block"))


# ============================================================================== floating islands
ISLANDS = [
    # (cx, top_y, cz, radius, depth, feature)
    (-122, 92, -150, 21, 36, "watchtower"),
    (84, 112, -180, 17, 30, "ruin"),
    (-62, 118, -192, 7, 14, "grove"),
    (32, 128, -198, 5, 10, "rock"),
    (-152, 74, -108, 7, 12, "grove"),
    (150, 116, -70, 6, 11, "rock"),
    (112, 96, -42, 5, 9, "grove"),
    (-96, 132, -128, 5, 9, "rock"),
    (-20, 140, -206, 4, 8, "rock"),
]


def floating_islands(w, T: Terrain):
    for n, (cx, ty, cz, r, depth, feat) in enumerate(ISLANDS):
        seed = seed_of(f"island{n}")
        R = r + 4
        xs, zs = np.meshgrid(np.arange(cx - R, cx + R + 1), np.arange(cz - R, cz + R + 1), indexing="ij")
        ang = np.arctan2(zs - cz, xs - cx)
        rim = r * (1.0 + 0.18 * np.sin(ang * 3 + n) + 0.12 * fbm2(xs, zs, 6.0, 2, seed))
        d = np.sqrt((xs - cx) ** 2 + (zs - cz) ** 2)
        inside = d <= rim
        f = np.clip(d / np.maximum(rim, 1e-3), 0, 1)
        bottom = ty - depth * (1 - f ** 1.4) - 2 + fbm2(xs, zs, 4.0, 2, seed + 1) * 3 * (1 - f)
        topy = ty + np.round(fbm2(xs, zs, 7.0, 2, seed + 2) * 1.5 * (1 - f)).astype(int)
        for i, j in zip(*np.nonzero(inside)):
            x, z = int(xs[i, j]), int(zs[i, j])
            b = int(math.floor(bottom[i, j]))
            t = int(topy[i, j])
            for y in range(b, t + 1):
                dd = t - y
                if dd == 0:
                    s = "grass_block"
                elif dd <= 2:
                    s = "dirt"
                else:
                    u = hash3(x, y, z, seed + 3)
                    s = "stone" if u < 0.4 else "calcite" if u < 0.6 else "andesite" if u < 0.82 else "tuff"
                w.set(x, y, z, B.S(s))
            if hash2(x, z, seed + 4) < 0.18 and f[i, j] > 0.4:
                ln = int(1 + hash2(z, x, seed + 5) * 4)
                for k in range(1, ln):
                    w.set(x, b - k, z, B.S("rooted_dirt"), only_empty=True)
                w.set(x, b - ln, z, B.S("hanging_roots"), only_empty=True)
            if hash2(x, z, seed + 6) < 0.12:
                w.set(x, t + 1, z, B.S("grass"), only_empty=True)
            elif hash2(x, z, seed + 7) < 0.03:
                w.set(x, t + 1, z, B.S("azure_bluet"), only_empty=True)
        if feat in ("grove", "watchtower", "ruin"):
            for k in range(max(1, r // 5)):
                a = k * 2.3 + n
                tx, tz = int(cx + math.cos(a) * r * 0.55), int(cz + math.sin(a) * r * 0.55)
                deco.tree_oak(w, tx, ty, tz, seed=seed + k, kind="birch" if k % 2 else "oak")
        if feat == "watchtower":
            _sky_watchtower(w, cx + 2, ty, cz - 1)
        if feat == "ruin":
            _sky_ruin(w, cx, ty, cz)


def _sky_watchtower(w, cx, y, cz):
    """Slender white tower of the Sky Folk with a blue spire."""
    from . import shapes as S
    S.cylinder(w, cx, cz, 3.2, y + 1, y + 16, B.full("castle_bricks"))
    S.cylinder(w, cx, cz, 2.2, y + 2, y + 15, B.S("air"))
    S.cylinder(w, cx, cz, 3.2, y + 1, y + 1, B.full("smooth_castle_stone"))
    for yy in (y + 6, y + 11):
        for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            w.set(cx + dx, yy, cz + dz, B.pane("light_blue"))
            w.set(cx + dx, yy + 1, cz + dz, B.pane("light_blue"))
    w.set(cx, y + 2, cz + 3, B.S("air"))
    w.set(cx, y + 3, cz + 3, B.S("air"))
    S.corbel_ring(w, cx, cz, 4.0, y + 16, "smooth_castle_stone")
    S.cylinder(w, cx, cz, 4.0, y + 17, y + 17, B.full("smooth_castle_stone"))
    S.crenellate_ring(w, cx, cz, 4.0, y + 18, B.wall("castle_bricks"))
    S.cylinder(w, cx, cz, 4.0, y + 17, y + 17, B.slab("gilded_trim"), hollow=True)
    S.cone(w, cx, cz, 3.2, y + 18, 16, "royal_roof_tiles")
    w.set(cx, y + 3, cz, B.S("end_rod", facing="up"))
    w.set(cx, y + 14, cz, B.S("chandelier"))


def _sky_ruin(w, cx, y, cz):
    """Broken arches of the Sky Folk (white quartz, gold, amethyst)."""
    for k, (dx, dz) in enumerate(((-6, -2), (-2, -5), (3, -4), (6, 1))):
        hgt = 5 + (k * 3) % 6
        for yy in range(y + 1, y + 1 + hgt):
            w.set(cx + dx, yy, cz + dz, B.S("quartz_pillar", axis="y"))
        w.set(cx + dx, y + 1 + hgt, cz + dz, B.S("chiseled_quartz_block"))
    for xx in range(cx - 6, cx - 1):
        w.set(xx, y + 9, cz - 2 - (xx - cx + 6) // 2, B.slab("smooth_quartz", "bottom"))
    w.set(cx, y + 1, cz, B.S("amethyst_block"))
    w.set(cx, y + 2, cz, B.S("amethyst_cluster", facing="up"))
    w.set(cx + 1, y + 1, cz + 1, B.S("gilded_trim"))


# ============================================================================== entry point
def build(w) -> Terrain:
    T = Terrain()
    w.set_footprint(L.footprint_mask())
    compute(T)
    # zones used for vegetation density
    X, Z = T.X, T.Z
    T.ridge_zone = (((X + 128) / 50.0) ** 2 + ((Z + 152) / 58.0) ** 2 <= 1.0) & (T.H > 4)
    T.lakeshore = (L.lake_distance(X, Z) > -12) & (L.lake_distance(X, Z) <= -2)
    castle_d = L.plateau_distance(X, Z)
    T.cliff_foot = (castle_d < -6) & (castle_d > -26) & (T.H < 8)
    T.plateau_top_castle = castle_d > -2
    write_columns(w, T)
    roads(w, T)
    plaza(w, T)
    lake_bridge(w, T)
    river_bridge(w, T)
    waterfall(w, T)
    cliff_dressing(w, T)
    vegetation(w, T)
    avenue_lamps(w, T)
    floating_islands(w, T)
    return T
