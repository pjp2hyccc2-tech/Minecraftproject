#!/usr/bin/env python3
"""遭難 ―SOUNAN― terrain designer.

Bakes the playable mountain (X, Z in -256..255) into
  src/main/resources/data/sounan/terrain/terrain.bin.gz   (read by MountainGenerator / Terrain.java)
  src/main/resources/data/sounan/terrain/pois.json        (POIs, checkpoints, ribbons, memos ...)
and writes previews to tools/terrain/out/.

Binary layout (big endian, gzip): magic "SNTR", u16 size (512), i16 origin (-256),
then five planes of size*size unsigned bytes, row-major [z][x]:
  height   surface Y (top solid block)
  surface  surface type (see SURF_*)
  trees    tree density 0..255
  water    water surface Y (0 = none)
  area     area id (see AREAS)

The design: every column gets the height of the closest point of the route polyline plus a
valley cross profile (the trail always runs along a valley floor), softly blended between
segments by distance.  Hard features are separate fields: the cliff step (a straight line
across the valley, everything downstream is 16 lower), the gorge trench (a band across the
valley under the bridge) and the tunnel ridge (a plateau over the tunnel axis).  Outside the
corridor (distance > W) is area 0: the game loops the player back.
"""
import gzip
import json
import math
import os
import struct
import sys

import numpy as np
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_DATA = os.path.join(ROOT, "src/main/resources/data/sounan/terrain")
OUT_PREV = os.path.join(HERE, "out")

N = 512
OFF = 256
XX, ZZ = np.meshgrid(np.arange(N, dtype=np.float64) - OFF, np.arange(N, dtype=np.float64) - OFF)

SURF_FOREST, SURF_MOSS, SURF_TRAIL, SURF_ROCK, SURF_STONES, SURF_GRAVEL, SURF_ASPHALT, SURF_DIRT, SURF_MOSSROCK = range(9)
AREAS = ["out", "rakuraku", "sawa", "gake", "sugi", "haison", "hokora", "tsuribashi", "koya", "tetto", "tunnel",
         "tozanguchi"]
A = {k: i for i, k in enumerate(AREAS)}

rng = np.random.default_rng(20261102)


# ------------------------------------------------------------------------------------ noise
def smooth_noise(cell, seed):
    """Value noise with cubic interpolation, cell size in blocks, values -1..1 (deterministic)."""
    r = np.random.default_rng(seed)
    n = N // cell + 4
    g = r.uniform(-1, 1, (n, n))
    z = ndimage.zoom(g, cell, order=3, mode="grid-wrap")
    return z[cell:cell + N, cell:cell + N]


def fbm(base, octaves, seed, gain=0.5):
    out = np.zeros((N, N))
    amp, tot, cell = 1.0, 0.0, base
    for o in range(octaves):
        out += amp * smooth_noise(max(2, int(cell)), seed + o * 101)
        tot += amp
        amp *= gain
        cell /= 2
    return out / tot


# ------------------------------------------------------------------------------------ the route
# (x, z, true surface height, corridor half width)
MAIN = [
    (-200, -212, 190, 24),   # 0  start: bottom of the slope you fell down
    (-190, -197, 188, 28),   # 1
    (-178, -183, 185, 32),   # 2
    (-166, -170, 183, 36),   # 3  the bag at the stream
    (-154, -157, 180, 36),   # 4
    (-144, -146, 178, 32),   # 5  cliff top (cliff line crosses before 6)
    (-128, -122, 161, 38),   # 6  below the cliff
    (-112, -104, 158, 44),   # 7
    (-95, -88, 155, 46),     # 8
    (-78, -74, 152, 46),     # 9
    (-60, -60, 149, 46),     # 10 fork: the dead end branches off here
    (-42, -44, 146, 46),     # 11
    (-24, -28, 142, 46),     # 12
    (-6, -12, 137, 46),      # 13
    (14, 0, 133, 46),        # 14 village west
    (30, 8, 132, 50),        # 15 village centre
    (46, 18, 132, 44),       # 16 village east
    (56, 30, 135, 30),       # 17 shrine steps
    (68, 37, 141, 26),       # 18 shrine knoll
    (79, 52, 139, 26),       # 19 fog wall
    (88, 70, 135, 30),       # 20
    (95, 85, 131, 30),       # 21 bridge north end
    (95, 105, 130, 30),      # 22 bridge south end
    (86, 118, 127, 36),      # 23
    (72, 130, 124, 40),      # 24 the hut
    (88, 142, 120, 36),      # 25
    (106, 147, 116, 30),     # 26
    (120, 151, 114, 24),     # 27 tunnel north portal
    (120, 229, 108, 24),     # 28 tunnel south portal
    (132, 236, 106, 30),     # 29
    (150, 240, 104, 36),     # 30
    (168, 240, 102, 36),     # 31 trailhead parking
]
DEAD = [(-60, -60, 149, 34), (-74, -44, 150, 30), (-86, -30, 151, 28), (-93, -21, 151, 24)]
PYLON = [(72, 130, 124, 30), (56, 126, 128, 24), (38, 123, 135, 22), (22, 117, 143, 22), (8, 110, 150, 22),
         (-6, 105, 155, 22)]

MAIN_AREA = {0: "rakuraku", 1: "rakuraku", 2: "sawa", 3: "sawa", 4: "sawa", 5: "sawa", 6: "sugi", 7: "sugi",
             8: "sugi", 9: "sugi", 10: "sugi", 11: "sugi", 12: "sugi", 13: "sugi", 14: "haison", 15: "haison",
             16: "haison", 17: "hokora", 18: "hokora", 19: "hokora", 20: "tsuribashi", 21: "tsuribashi",
             22: "tsuribashi", 23: "koya", 24: "koya", 25: "koya", 26: "koya", 27: "tunnel", 28: "tozanguchi",
             29: "tozanguchi", 30: "tozanguchi"}

# cliff: a straight line across the valley between waypoints 5 and 6
CLIFF_C = np.array([-136.0, -134.0])
_d = np.array([-128.0 + 144.0, -122.0 + 146.0])
CLIFF_N = _d / np.linalg.norm(_d)          # downstream normal
CLIFF_DROP = 16
# gorge under the bridge: band |z - 95| < 6, |x - 95| < 70
GORGE_Z, GORGE_HALF, GORGE_BOTTOM = 95.0, 6.0, 107
# tunnel ridge over the axis x = 120, z 151..229
TUN_X, TUN_Z0, TUN_Z1, RIDGE_H = 120.0, 151.0, 229.0, 30.0
# shrine fog wall: band across the valley beyond the shrine
FOG_C = np.array([80.0, 56.0])
_f = np.array([88.0 - 79.0, 70.0 - 52.0])
FOG_N = _f / np.linalg.norm(_f)
FOG_DEPTH = 9.0


def cliff_sd(x, z):
    return (x - CLIFF_C[0]) * CLIFF_N[0] + (z - CLIFF_C[1]) * CLIFF_N[1]


def fog_sd(x, z):
    return (x - FOG_C[0]) * FOG_N[0] + (z - FOG_C[1]) * FOG_N[1]


def in_gorge_pre(x, z):
    return (np.abs(z - GORGE_Z) < GORGE_HALF + 2) & (np.abs(x - 95) < 78)


def ridge_pre(x, z):
    pz = np.clip(np.minimum(z - TUN_Z0, TUN_Z1 - z) / 2.5, 0, 1)
    px = np.clip(1 - (np.abs(x - TUN_X) - 46) / 40, 0, 1)
    return RIDGE_H * pz * px


def base_heights(route):
    """Route heights in 'base' space: the cliff step is added back for points downstream of it."""
    out = []
    for (x, z, h, w) in route:
        if cliff_sd(x, z) > 0:
            h = h + CLIFF_DROP
        out.append((x, z, h, w))
    return out


def segments(route):
    return [(route[i], route[i + 1]) for i in range(len(route) - 1)]


def seg_fields(a, b):
    ax, az, ah, aw = a
    bx, bz, bh, bw = b
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    t = np.clip(((XX - ax) * dx + (ZZ - az) * dz) / L2, 0.0, 1.0)
    px, pz = ax + t * dx, az + t * dz
    d = np.hypot(XX - px, ZZ - pz)
    return t, d, ah + t * (bh - ah), aw + t * (bw - aw)


def profile(d):
    """Valley cross-section above the trail height at distance d."""
    e = np.maximum(d - 3.0, 0.0)
    return 0.20 * e + 0.0065 * e * e


def build():
    routes = [("main", base_heights(MAIN)), ("dead", base_heights(DEAD)), ("pylon", base_heights(PYLON))]
    acc_w = np.zeros((N, N))
    acc_h = np.zeros((N, N))
    dmin = np.full((N, N), 1e9)
    wmin = np.zeros((N, N))
    seg_id = np.zeros((N, N), np.int32)    # main segment index of the nearest main segment
    seg_route = np.zeros((N, N), np.int32)  # 0 main 1 dead 2 pylon (nearest overall)
    seg_t = np.zeros((N, N))
    fields = []
    for ri, (name, route) in enumerate(routes):
        for si, (a, b) in enumerate(segments(route)):
            t, d, h, w = seg_fields(a, b)
            fields.append((ri, si, t, d, h, w))
            closer = d < dmin
            dmin = np.where(closer, d, dmin)
            wmin = np.where(closer, w, wmin)
            seg_route = np.where(closer, ri, seg_route)
            seg_id = np.where(closer, si, seg_id)
            seg_t = np.where(closer, t, seg_t)
    S = 4.0
    for (ri, si, t, d, h, w) in fields:
        wt = np.exp(-(d - dmin) / S)
        acc_w += wt
        acc_h += wt * (h + profile(d))
    H = acc_h / acc_w

    # natural roughness: grows away from the trail
    n_big = fbm(96, 4, 11)
    n_mid = fbm(24, 3, 23)
    n_small = smooth_noise(6, 37)
    amp = np.clip((dmin - 2.5) / 22.0, 0.0, 1.0)
    H += amp * (7.0 * n_big + 2.6 * n_mid) + np.clip((dmin - 1.5) / 6.0, 0, 1) * 0.8 * n_small
    # the trail itself is gently uneven
    H += 0.55 * n_mid * (1 - amp)

    # the slope you fell down: a steep wall north/west of the start
    sx, sz = MAIN[0][0], MAIN[0][1]
    back = np.maximum(0.0, -((XX - sx) * 0.55 + (ZZ - sz) * 0.83) - 6.0)
    H += back * 1.7

    # ---- cliff step (jagged edge)
    csd = cliff_sd(XX, ZZ) + 1.6 * smooth_noise(5, 41)
    H = np.where(csd > 0, H - CLIFF_DROP, H)

    # ---- POI flats (x, z, radius, height, blend)
    flats = [
        (-162, -172, 5, None, 6),       # bag site (keep local height, smooth)
        (-121, -131, 6, None, 5),       # pool below the cliff
        (30, 6, 30, 132.0, 14),         # village
        (66, 36, 6, 141.0, 5),          # shrine knoll top
        (95, 81, 4, 131.0, 4),          # bridge north abutment
        (95, 109, 4, 130.0, 4),         # bridge south abutment
        (72, 131, 11, 124.0, 8),        # hut clearing
        (-6, 105, 7, 155.0, 6),         # pylon pad
        (120, 147, 6, 114.0, 6),        # north portal apron
        (120, 233, 5, 108.0, 6),        # south portal apron
        (163, 240, 16, 102.0, 10),      # parking
        (-93, -21, 6, None, 5),         # dead end hollow
        (-200, -211, 5, None, 5),       # start
    ]
    for (cx, cz, r, h, bl) in flats:
        dist = np.hypot(XX - cx, ZZ - cz)
        w = np.clip(1 - (dist - r) / bl, 0, 1)
        w = w * w * (3 - 2 * w)
        target = H[int(cz) + OFF, int(cx) + OFF] if h is None else h
        if h is None:
            # smooth towards the local mean instead of a fixed height
            target = ndimage.uniform_filter(H, size=2 * r + 1)
        H = H * (1 - w) + target * w

    # the trail itself must be walkable: smooth the heights along it (steps of at most one block)
    sm = ndimage.uniform_filter(H, size=5)
    wtr = np.clip(1 - (dmin - 2.5) / 3.0, 0, 1)
    keep = np.abs(cliff_sd(XX, ZZ)) < 5.0
    H = np.where(keep, H, H * (1 - wtr) + sm * wtr)

    # ---- tunnel ridge (plateau with sheer portal faces)
    pz = np.clip(np.minimum(ZZ - TUN_Z0, TUN_Z1 - ZZ) / 2.5, 0, 1)
    px = np.clip(1 - (np.abs(XX - TUN_X) - 46) / 40, 0, 1)
    px = px * px * (3 - 2 * px)
    ridge = RIDGE_H * pz * px * (1 + 0.15 * n_big)
    H = np.maximum(H, H + ridge)

    # ---- gorge trench
    gsd = np.abs(ZZ - GORGE_Z) - (GORGE_HALF + 0.9 * smooth_noise(4, 53))
    in_gorge = (gsd < 0) & (np.abs(XX - 95) < 78)
    H = np.where(in_gorge, np.minimum(H, GORGE_BOTTOM + 1.2 * smooth_noise(3, 59)), H)

    H = np.clip(np.round(H), 72, 232).astype(np.int32)

    # ------------------------------------------------------------------ areas
    area = np.zeros((N, N), np.uint8)
    inside = dmin <= wmin
    main_seg = np.full((N, N), -1)
    # nearest main segment (for area naming), distances among main segments only
    dm = np.full((N, N), 1e9)
    for (ri, si, t, d, h, w) in fields:
        if ri != 0:
            continue
        c = d < dm
        dm = np.where(c, d, dm)
        main_seg = np.where(c, si + (t > 0.5), main_seg)  # nearest waypoint index
    for wp, name in MAIN_AREA.items():
        area[(main_seg == wp) & inside] = A[name]
    area[(main_seg == 31) & inside] = A["tozanguchi"]
    area[(seg_route == 1) & inside] = A["sugi"]
    area[(seg_route == 2) & inside] = A["tetto"]
    # the hut spur start belongs to the hut
    area[(seg_route == 2) & (np.hypot(XX - 72, ZZ - 130) < 26) & inside] = A["koya"]
    cl = np.abs(cliff_sd(XX, ZZ)) < 16
    area[cl & inside & (main_seg >= 4) & (main_seg <= 7)] = A["gake"]
    area[(np.hypot(XX - 30, ZZ - 6) < 44) & inside] = A["haison"]
    tunnel = (np.abs(XX - TUN_X) <= 4) & (ZZ > TUN_Z0 - 1) & (ZZ < TUN_Z1 + 1)
    area[(ridge > 6) & ~tunnel] = 0         # nobody walks over the ridge
    area[tunnel] = A["tunnel"]
    area[in_gorge & ~((np.abs(XX - 95) <= 2))] = 0   # falling into the gorge loops you back
    area[in_gorge & (np.abs(XX - 95) <= 2)] = A["tsuribashi"]

    # ------------------------------------------------------------------ water
    water = np.zeros((N, N), np.int32)
    # the gully stream: from above the start down to a pool before the cliff edge
    # the stream runs 5 blocks north-east of the trail (offset (+3.8, -3.2))
    stream = [(-207, -228), (-197, -216), (-187, -202), (-175, -187), (-163, -174), (-151, -161), (-143, -151),
              (-138, -145)]
    sd_stream = np.full((N, N), 1e9)
    for i in range(len(stream) - 1):
        a = (stream[i][0], stream[i][1], 0, 0)
        b = (stream[i + 1][0], stream[i + 1][1], 0, 0)
        t, d, _, _ = seg_fields(a, b)
        sd_stream = np.minimum(sd_stream, d)
    # keep the stream beside the trail: shift it 4 blocks to the trail's left by construction above
    bed = (sd_stream < 1.6 + 0.5 * smooth_noise(4, 61)) & (cliff_sd(XX, ZZ) < -3.5)
    banks = (sd_stream < 3.2) & ~bed & (cliff_sd(XX, ZZ) < -3.5)
    Hf = H.astype(np.float64)
    # carve: the bed is 2 below the local smoothed ground, water one above the bed
    smooth = ndimage.uniform_filter(Hf, size=7)
    bedH = np.floor(smooth - 2)
    H = np.where(bed, np.minimum(H, bedH).astype(np.int32), H)
    H = np.where(banks, np.minimum(H, np.floor(smooth - 0.5)).astype(np.int32), H)
    water = np.where(bed, H + 1, water)
    # pool below the cliff and the gorge river
    pool = np.hypot(XX + 121, ZZ + 131) < 3.5
    H = np.where(pool, H - 2, H)
    water = np.where(pool, H + 2, water)
    gorge_floor = in_gorge
    water = np.where(gorge_floor, GORGE_BOTTOM + 3, water)
    H = np.where(gorge_floor, np.minimum(H, GORGE_BOTTOM), H)

    # ------------------------------------------------------------------ surface + trees
    Hf = H.astype(np.float64)
    gx = np.maximum(np.abs(np.roll(Hf, 1, 1) - Hf), np.abs(np.roll(Hf, -1, 1) - Hf))
    gz = np.maximum(np.abs(np.roll(Hf, 1, 0) - Hf), np.abs(np.roll(Hf, -1, 0) - Hf))
    steep = np.maximum(gx, gz)
    surf = np.full((N, N), SURF_FOREST, np.uint8)
    wet = fbm(20, 3, 71)
    surf[(wet > 0.25)] = SURF_MOSS
    surf[(steep >= 3)] = SURF_ROCK
    surf[(steep >= 2) & (wet > 0.35)] = SURF_MOSSROCK
    trail_w = 1.25 + 0.35 * smooth_noise(5, 73)
    trail = (dmin < trail_w) & (seg_route != 2) & (steep < 3)
    trail_breaks = smooth_noise(9, 79) > 0.55          # the old trail fades in places
    surf[trail & ~trail_breaks] = SURF_TRAIL
    pylon_trail = (dmin < 0.9) & (seg_route == 2) & (smooth_noise(7, 83) < 0.3)
    surf[pylon_trail] = SURF_TRAIL
    surf[bed | pool] = SURF_STONES
    surf[banks & (wet > -0.2)] = SURF_MOSS
    village = np.hypot(XX - 30, ZZ - 6) < 30
    surf[village & (surf == SURF_FOREST)] = SURF_DIRT
    # the old village road (east-west) and the forest road from the tunnel to the parking
    road = np.zeros((N, N), bool)
    road |= (np.abs(ZZ - 6) < 2.0) & (XX > -2) & (XX < 62)
    for (a, b) in [((120, 229), (132, 236)), ((132, 236), (150, 240)), ((150, 240), (176, 240))]:
        t, d, _, _ = seg_fields((a[0], a[1], 0, 0), (b[0], b[1], 0, 0))
        road |= d < 2.6
    surf[road & (smooth_noise(4, 89) > -0.55)] = SURF_ASPHALT
    surf[road & (smooth_noise(4, 89) <= -0.55)] = SURF_GRAVEL
    parking = (np.abs(XX - 163) < 12) & (np.abs(ZZ - 240) < 9)
    surf[parking] = SURF_ASPHALT
    north_apron = (np.abs(XX - 120) < 5) & (ZZ > 140) & (ZZ < TUN_Z0)
    surf[north_apron] = SURF_GRAVEL

    dens = 0.80 + 0.30 * fbm(40, 3, 97)                 # glades and thickets
    dens = np.clip(dens, 0.0, 1.0)
    dens *= np.clip((dmin - 2.0) / 3.5, 0, 1)            # the trail is free
    dens[steep >= 4] *= 0.35
    dens[(bed | banks | pool | gorge_floor)] = 0
    dens[road | parking | north_apron | village] = 0
    for (cx, cz, r) in [(-162, -172, 4), (-121, -131, 5), (66, 36, 6), (72, 131, 9), (-6, 105, 8), (-93, -21, 4),
                        (-200, -211, 4), (95, 81, 4), (95, 109, 4), (120, 233, 6)]:
        dens[np.hypot(XX - cx, ZZ - cz) < r] = 0
    dens[np.abs(cliff_sd(XX, ZZ)) < 2.5] = 0
    dens[ridge > 6] *= 0.5
    dens[area == A["tetto"]] *= 0.45
    trees = np.clip(dens * 255, 0, 255).astype(np.uint8)
    return dict(H=H, surf=surf, trees=trees, water=water.astype(np.int32), area=area, dmin=dmin, wmin=wmin,
                main_seg=main_seg, seg_route=seg_route, ridge=ridge, in_gorge=in_gorge)


# ------------------------------------------------------------------------------------ checks
def walkable(T):
    """Columns a player can stand on and that are inside the corridor."""
    return T["area"] > 0


def check_separation(T):
    """Hard gates must cut the corridor: the cliff (rope), the gorge (bridge), the fog wall (offering),
    the tunnel ridge (gate)."""
    H = T["H"].astype(np.float64)
    ok = walkable(T)
    ok_water = ok & (T["water"] == 0) | (T["area"] > 0) & (T["water"] > 0)
    problems = []

    def comp_of(mask, a, b):
        lab, n = ndimage.label(mask)
        la = lab[a[1] + OFF, a[0] + OFF]
        lb = lab[b[1] + OFF, b[0] + OFF]
        return la, lb

    # 1) cliff: remove the cliff edge band; start and forest must be disconnected
    mask = ok & (np.abs(cliff_sd(XX, ZZ)) > 1.0)
    la, lb = comp_of(mask, (-200, -211), (-100, -92))
    if la != 0 and la == lb:
        problems.append("cliff does not separate start from forest")
    # 2) fog wall
    mask = ok & ~((fog_sd(XX, ZZ) > 0) & (fog_sd(XX, ZZ) < FOG_DEPTH))
    la, lb = comp_of(mask, (30, 6), (95, 115))
    if la != 0 and la == lb:
        problems.append("fog wall does not separate village from bridge side")
    # 3) gorge: without the bridge columns
    mask = ok & ~T["in_gorge"]
    la, lb = comp_of(mask, (88, 70), (86, 118))
    if la != 0 and la == lb:
        problems.append("gorge does not separate the two sides")
    # 4) tunnel: without the tunnel itself
    mask = ok & (T["area"] != A["tunnel"])
    la, lb = comp_of(mask, (100, 145), (160, 240))
    if la != 0 and la == lb:
        problems.append("tunnel ridge does not separate the hut side from the trailhead")
    # every POI must be inside the corridor and connected to the start with the gates open
    return problems


def check_route_slopes(T):
    """The trail itself must be walkable: no step > 1 block between neighbouring trail columns,
    except at the cliff and over the gorge (bridge)."""
    H = T["H"]
    bad = []
    for route in (MAIN, DEAD, PYLON):
        pts = []
        for i in range(len(route) - 1):
            ax, az = route[i][0], route[i][1]
            bx, bz = route[i + 1][0], route[i + 1][1]
            L = max(1, int(math.hypot(bx - ax, bz - az)))
            for k in range(L):
                x = ax + (bx - ax) * k / L
                z = az + (bz - az) * k / L
                pts.append((int(round(x)), int(round(z))))
        prev = None
        for (x, z) in pts:
            h = int(H[z + OFF, x + OFF])
            if prev is not None:
                px, pz, ph = prev
                if abs(h - ph) > 1 and abs(cliff_sd(x, z)) > 3 and not (abs(z - GORGE_Z) < 9 and abs(x - 95) < 5) \
                        and not (abs(x - TUN_X) < 5 and TUN_Z0 - 2 < z < TUN_Z1 + 2):
                    bad.append((x, z, ph, h))
            prev = (x, z, h)
    return bad


# ------------------------------------------------------------------------------------ outputs
def write_bin(T):
    os.makedirs(OUT_DATA, exist_ok=True)
    buf = bytearray(b"SNTR")
    buf += struct.pack(">Hh", N, -OFF)
    for key in ("H", "surf", "trees", "water", "area"):
        a = np.clip(T[key], 0, 255).astype(np.uint8)
        buf += a.tobytes(order="C")
    with gzip.open(os.path.join(OUT_DATA, "terrain.bin.gz"), "wb", compresslevel=9) as f:
        f.write(bytes(buf))


def preview(T, pois):
    from PIL import Image, ImageDraw
    os.makedirs(OUT_PREV, exist_ok=True)
    H = T["H"].astype(np.float64)
    lx, lz = np.gradient(H)
    shade = np.clip(0.55 + 0.35 * (-lz * 0.6 - lx * 0.4) / 2.0, 0, 1)
    hn = (H - H.min()) / (H.max() - H.min())
    rgb = np.zeros((N, N, 3))
    rgb[..., 0] = 0.25 + 0.5 * hn
    rgb[..., 1] = 0.30 + 0.45 * hn
    rgb[..., 2] = 0.25 + 0.35 * hn
    rgb *= shade[..., None]
    tr = T["trees"] / 255.0
    rgb[..., 0] *= 1 - 0.35 * tr
    rgb[..., 2] *= 1 - 0.35 * tr
    s = T["surf"]
    rgb[s == SURF_TRAIL] = [0.75, 0.6, 0.4]
    rgb[s == SURF_ASPHALT] = [0.25, 0.25, 0.27]
    rgb[s == SURF_ROCK] *= 0.8
    rgb[T["water"] > 0] = [0.2, 0.35, 0.6]
    rgb[T["area"] == 0] *= 0.45
    img = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)).resize((N * 2, N * 2), Image.NEAREST)
    d = ImageDraw.Draw(img)
    for p in pois["points"]:
        x, z = (p["x"] + OFF) * 2, (p["z"] + OFF) * 2
        d.rectangle([x - 3, z - 3, x + 3, z + 3], outline=(255, 60, 60))
        d.text((x + 5, z - 5), p["id"], fill=(255, 230, 200))
    for (x, z) in pois["ribbons"]:
        d.point(((x + OFF) * 2, (z + OFF) * 2), fill=(255, 100, 200))
    img.save(os.path.join(OUT_PREV, "map.png"))
    # area map
    pal = np.array([[0, 0, 0], [200, 80, 80], [80, 140, 220], [220, 200, 60], [60, 160, 60], [200, 120, 40],
                    [220, 60, 160], [120, 220, 220], [160, 100, 60], [180, 180, 180], [90, 90, 160],
                    [240, 240, 120]], np.uint8)
    Image.fromarray(pal[T["area"]]).resize((N, N)).save(os.path.join(OUT_PREV, "areas.png"))


def surface_y(T, x, z):
    return int(T["H"][int(z) + OFF, int(x) + OFF])


def build_pois(T):
    pts = []

    def P(pid, x, z, facing="north", **kw):
        d = dict(id=pid, x=int(x), y=surface_y(T, x, z) + 1, z=int(z), facing=facing)
        d.update(kw)
        pts.append(d)

    # player start (facing down the gully)
    P("start", -199, -210, "south")
    P("bag", -162, -172, "east")
    # rope anchor at the cliff edge on the trail (one block upstream of the line)
    cx = CLIFF_C[0] - CLIFF_N[0] * 2.2
    cz = CLIFF_C[1] - CLIFF_N[1] * 2.2
    P("rope_anchor", round(cx), round(cz), "south")
    P("dead_end", -93, -21, "south")
    P("village", 30, 6)
    P("shrine", 68, 36, "west")
    P("fog_wall", FOG_C[0], FOG_C[1])
    P("bridge_n", 95, 87, "south", deck=131)
    P("bridge_s", 95, 103, "south", deck=131)
    P("hut", 72, 131, "east")
    P("pylon", -6, 105)
    P("tunnel_n", 120, 151, "south", road=114)
    P("tunnel_s", 120, 229, "south", road=108)
    P("parking", 163, 240)
    P("car", 168, 241, "west")
    # checkpoints: where you wake up after being caught (x, z, facing)
    cps = [("bag", -164, -168, "south"), ("village", 4, 9, "east"), ("shrine", 64, 36, "east"),
           ("hut", 77, 127, "south"), ("tunnel", 118, 144, "south")]
    checkpoints = [dict(id=c[0], x=c[1], y=surface_y(T, c[1], c[2]) + 1, z=c[2], facing=c[3]) for c in cps]

    # pink ribbons along the trail (and, misleadingly, into the dead end)
    ribbons = []

    def ribbon_walk(route, step, start_i, end_i, offset=3.2):
        side = 1
        acc = 0.0
        for i in range(start_i, end_i):
            ax, az = route[i][0], route[i][1]
            bx, bz = route[i + 1][0], route[i + 1][1]
            L = math.hypot(bx - ax, bz - az)
            ux, uz = (bx - ax) / L, (bz - az) / L
            s = step - acc
            while s < L:
                x = ax + ux * s - uz * offset * side
                z = az + uz * s + ux * offset * side
                ribbons.append((int(round(x)), int(round(z))))
                side = -side
                s += step
            acc = L - (s - step)

    ribbon_walk(MAIN, 15, 0, 5)
    ribbon_walk(MAIN, 11, 6, 14)
    ribbon_walk(DEAD, 9, 0, 3)
    ribbon_walk(MAIN, 13, 19, 24)
    ribbons = [(x, z) for (x, z) in ribbons if T["area"][z + OFF, x + OFF] > 0 and T["water"][z + OFF, x + OFF] == 0
               and abs(cliff_sd(x, z)) > 3]

    # memos (lore): id = note number
    memo_spots = [(1, -195, -203), (2, -158, -162), (3, -124, -117), (4, -70, -66), (5, -92, -19), (6, -18, -24),
                  (9, 72, 41), (11, 2, 110)]
    memos = [dict(note=n, x=x, y=surface_y(T, x, z) + 1, z=z) for (n, x, z) in memo_spots]

    jizo = [dict(x=-57, z=-57, facing="west"), dict(x=77, z=49, facing="north"), dict(x=101, z=145, facing="west")]
    for i in range(6):
        jizo.append(dict(x=5, z=-5 + i, facing="east", row=True))
    for j in jizo:
        j["y"] = surface_y(T, j["x"], j["z"]) + 1
    signs = [dict(x=-57, z=-62, facing="east", text="sign_fork"), dict(x=48, z=24, facing="south", text="sign_shrine"),
             dict(x=80, z=136, facing="west", text="sign_hut"), dict(x=104, z=150, facing="east", text="sign_tunnel"),
             dict(x=-198, z=-206, facing="south", text="sign_start")]
    for s_ in signs:
        s_["y"] = surface_y(T, s_["x"], s_["z"]) + 1
    return dict(points=pts, checkpoints=checkpoints, ribbons=ribbons, memos=memos, jizo=jizo, signs=signs,
                fog=dict(cx=FOG_C[0], cz=FOG_C[1], nx=float(FOG_N[0]), nz=float(FOG_N[1]), depth=FOG_DEPTH),
                cliff=dict(cx=float(CLIFF_C[0]), cz=float(CLIFF_C[1]), nx=float(CLIFF_N[0]), nz=float(CLIFF_N[1])),
                gorge=dict(z=GORGE_Z, half=GORGE_HALF, bottom=GORGE_BOTTOM),
                tunnel=dict(x=TUN_X, z0=TUN_Z0, z1=TUN_Z1),
                areas=AREAS,
                route=[[p[0], p[1]] for p in MAIN])


def main():
    T = build()
    problems = check_separation(T)
    bad = check_route_slopes(T)
    pois = build_pois(T)
    write_bin(T)
    with open(os.path.join(OUT_DATA, "pois.json"), "w", encoding="utf-8") as f:
        json.dump(pois, f, ensure_ascii=False, indent=1)
    preview(T, pois)
    np.savez_compressed(os.path.join(OUT_PREV, "terrain.npz"), **{k: T[k] for k in ("H", "surf", "trees", "water", "area")})
    print("height range", T["H"].min(), T["H"].max())
    print("separation problems:", problems or "none")
    print("steep trail steps:", len(bad), bad[:12])
    for p in pois["points"]:
        print(f"  {p['id']:12s} {p['x']:5d} {p['y']:4d} {p['z']:5d}")
    print("ribbons", len(pois["ribbons"]))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
