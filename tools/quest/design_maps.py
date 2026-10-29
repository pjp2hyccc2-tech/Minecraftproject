#!/usr/bin/env python3
"""Designs the three HuntCraft quest maps and exports them (see qmap.py for the format).

    nice -n 5 python3 tools/quest/design_maps.py [verdant|frost|ember ...]

Each map: 448x448 playable blocks inside a natural boundary wall, 9 numbered areas with distinct terrain,
landmarks, a base camp (tent + bed, supply box, delivery box, guild flag, campfire) and ~70-90 gathering points.
"""
import math
import os
import sys

import numpy as np
from scipy import ndimage

sys.path.insert(0, os.path.dirname(__file__))
from qmap import (QMap, T, LO, HI, U, V, fbm, ridged, smoothstep, dist, ellipse, polyline, smooth_path, edge_dist,
                  FLUID_WATER, FLUID_LAVA, FLUID_FROZEN)
import landmarks as L

NS = 'huntcraft'


def scatter(m, rng, mask, n, spacing=9, avoid=()):
    """Picks up to n points (u, v) from a boolean mask with a minimum spacing."""
    cand = np.argwhere(mask)
    if len(cand) == 0:
        return []
    rng.shuffle(cand)
    pts = list(avoid)
    out = []
    for (v, u) in cand:
        if all((u - pu) ** 2 + (v - pv) ** 2 >= spacing * spacing for (pu, pv) in pts):
            pts.append((u, v))
            out.append((int(u), int(v)))
            if len(out) >= n:
                break
    return out


def boundary_wall(m, rim_base, rim_amp, inner=10.0, steep=6.0, seed=0):
    """Raises a natural wall around the playable square: steep inner face, high noisy crest outside."""
    e = edge_dist() + fbm(m.seed + 50 + seed, 28, 3) * 4.5
    rim = rim_base + fbm(m.seed + 51 + seed, 45, 4) * rim_amp + ridged(m.seed + 52 + seed, 30, 3) * rim_amp * 0.5
    t = smoothstep(inner, inner - steep, e)
    m.h = m.h * (1 - t) + np.maximum(m.h, rim) * t
    return e


def carve_path_heights(m, pts, width=3.0, strength=1.0):
    """Smooths terrain along a path so it stays walkable (heights low-passed along the corridor)."""
    d, s, _ = polyline(pts)
    smooth = ndimage.gaussian_filter(m.h, 3.0)
    w = smoothstep(width + 3, width, d) * strength
    m.h = m.h * (1 - w) + smooth * w
    return d


# ====================================================================== VERDANT HILLS
def verdant():
    m = QMap('verdant_hills', seed=1101, base=68)
    rng = np.random.default_rng(11)
    m.set_biomes([f'{NS}:verdant_meadow', f'{NS}:verdant_forest', f'{NS}:verdant_highlands', f'{NS}:verdant_riverside',
                  f'{NS}:verdant_clearing', f'{NS}:verdant_cave'])
    m.sea = 62
    W = 62
    # ---------------------------------------------------------------- macro terrain
    m.h = 68 + 6.0 * fbm(m.seed + 1, 95, 4) + 2.5 * fbm(m.seed + 2, 28, 3)
    # forest (W): gentle rolls
    forest = smoothstep(1.15, 0.75, ellipse(125, 262, 85, 95))
    m.h += forest * 2.0 * fbm(m.seed + 3, 22, 2)
    # meadow hills (S)
    meadow = smoothstep(1.2, 0.7, ellipse(225, 385, 95, 60))
    m.h += meadow * (4.5 * np.sin(U / 23.0) * np.cos(V / 19.0) + 3.0 * fbm(m.seed + 16, 26, 3))
    # ruins hill (centre)
    rd = dist(255, 245)
    m.h = np.maximum(m.h, 70 + 17 * (1 - smoothstep(18, 56, rd)) + 1.5 * fbm(m.seed + 4, 15, 2))
    top = smoothstep(26, 22, rd)
    m.h = m.h * (1 - top) + 86 * top
    # wyvern plateau (NW) with cliff edges
    pe = ellipse(128, 112, 72, 56, 0.2) + fbm(m.seed + 5, 25, 3) * 0.10
    plat = smoothstep(1.0, 0.955, pe)
    m.h = m.h * (1 - plat) + (108 + 2.0 * fbm(m.seed + 6, 20, 2)) * plat
    # cave hills (N)
    ce = ellipse(282, 104, 78, 44, -0.1)
    m.h = np.maximum(m.h, 70 + 34 * (1 - smoothstep(0.35, 1.05, ce)) + 4 * fbm(m.seed + 7, 18, 3) * (ce < 1))
    # east cliffs plateau
    ee = ellipse(448, 330, 58, 74, 0.0) + fbm(m.seed + 8, 25, 3) * 0.08
    eplat = smoothstep(1.0, 0.95, ee)
    m.h = m.h * (1 - eplat) + (98 + 1.5 * fbm(m.seed + 9, 20, 2)) * eplat
    # ---------------------------------------------------------------- river + lake (single level W)
    river = smooth_path([(392, 150), (370, 192), (352, 232), (338, 270), (318, 306), (302, 342), (296, 382), (306, 424),
                         (316, 470), (318, 505)], 10)
    rdist, rs, rlen = polyline(river)
    rw = 7.0 + 2.5 * fbm(m.seed + 10, 30, 2, x=rs, z=np.zeros_like(rs))
    bank = np.minimum(m.h, 63.0 + np.maximum(0, rdist - rw) * 1.3)
    near = rdist < rw + 30
    m.h = np.where(near, np.minimum(m.h, bank), m.h)
    bed = W - 4.5 * (1 - (rdist / rw) ** 2)
    # fords (shallow crossings)
    ford = np.zeros_like(rs)
    for f in (0.30, 0.55, 0.83):
        ford = np.maximum(ford, smoothstep(14, 6, np.abs(rs - f * rlen)))
    bed = bed * (1 - ford) + (W - 1) * ford
    inriver = rdist < rw
    m.h = np.where(inriver, np.minimum(m.h, bed), m.h)
    # lake
    le = ellipse(398, 132, 58, 42, 0.15) + fbm(m.seed + 11, 22, 2) * 0.07
    lake = le < 1.0
    m.h = np.where(le < 1.35, np.minimum(m.h, 63 + np.maximum(0, (le - 1.0)) * 30), m.h)
    m.h = np.where(lake, np.minimum(m.h, W - 1 - 7 * (1 - le ** 2) ** 0.7), m.h)
    isl = dist(414, 126)
    m.h = np.where(isl < 11, np.maximum(m.h, 63 + 3 * (1 - isl / 11)), m.h)
    # plunge pool under the waterfall (east cliff) + stream to the river
    pool = dist(392, 322) < 9
    stream = smooth_path([(392, 322), (360, 328), (330, 318)], 6)
    sd, _, _ = polyline(stream)
    m.h = np.where(sd < 9, np.minimum(m.h, 63 + np.maximum(0, sd - 4) * 1.5), m.h)
    m.h = np.where((sd < 4) | pool, np.minimum(m.h, W - 2.5), m.h)
    # ---------------------------------------------------------------- boundary wall
    boundary_wall(m, 132, 14)
    # river leaves through a gorge in the south wall
    gorge = (rdist < rw + 2) & (V > 440)
    m.h = np.where(gorge, np.minimum(m.h, np.where(rdist < rw, bed, 64 + (rdist - rw) * 8)), m.h)
    m.h = np.where((rdist >= rw) & (rdist < rw + 9) & (V > 440), np.minimum(m.h, 64 + (rdist - rw) ** 2 * 1.2), m.h)
    # ---------------------------------------------------------------- ramps + paths (walkable routes)
    cu, cv, cy = 96, 412, 73
    ramp_pl = [(206, 214), (184, 190), (166, 166), (152, 150)]
    rd2, rs2, rl2 = polyline(ramp_pl)
    prof = 74 + (108 - 74) * np.clip(rs2 / rl2, 0, 1)
    m.h = np.where(rd2 < 5, np.maximum(np.minimum(m.h, prof + 0.5), prof), m.h)
    m.h = np.where((rd2 >= 5) & (rd2 < 9), np.maximum(m.h, prof - (rd2 - 5) * 2.5), m.h)
    eramp = [(440, 452), (444, 420), (447, 395)]
    rd3, rs3, rl3 = polyline(eramp)
    prof3 = 70 + (98 - 70) * np.clip(rs3 / rl3, 0, 1)
    m.h = np.where(rd3 < 5, np.maximum(np.minimum(m.h, prof3 + 0.5), prof3), m.h)
    m.h = np.where((rd3 >= 5) & (rd3 < 9), np.maximum(m.h, prof3 - (rd3 - 5) * 2.5), m.h)
    paths = [
        smooth_path([(cu + 6, cv - 2), (140, 392), (190, 386), (232, 384), (270, 378), (296, 376), (330, 380), (380, 410), (430, 452), (440, 452)], 8),
        smooth_path([(cu, cv - 10), (100, 360), (112, 316), (124, 280), (138, 262)], 8),
        smooth_path([(138, 262), (170, 248), (205, 246), (229, 246)], 8),
        smooth_path([(138, 262), (165, 238), (196, 222), (206, 214)], 8),
        smooth_path([(255, 222), (250, 190), (240, 162), (236, 150)], 8),
        smooth_path([(280, 245), (310, 250), (336, 262), (350, 246), (360, 205), (366, 182)], 8),
        smooth_path([(232, 384), (240, 330), (250, 290), (255, 268)], 8),
    ]
    pmask = np.zeros((T, T), bool)
    for p in paths:
        d = carve_path_heights(m, p, 2.5)
        pmask |= d < 1.8
    # ---------------------------------------------------------------- camp pad
    L.flatten_pad(m, cu, cv, cy, 12, 22)
    m.h = np.maximum(m.h, np.where(dist(cu, cv) < 12, cy - 1, 0))
    # ---------------------------------------------------------------- cave through the north hills
    m.finalize_heights()
    h = m.hi
    tunnel = smooth_path([(234, 152), (250, 130), (272, 113), (296, 105), (318, 112), (334, 124)], 10)
    td, ts, tl = polyline(tunnel)
    tt = np.clip(ts / tl, 0, 1)
    h0, h1 = float(h[152, 234]), float(h[124, 334])
    floor = h0 + (h1 - h0) * tt + 5.0 * np.sin(np.pi * tt)
    chamber = dist(286, 107)
    tw = 3.0 + 1.4 * fbm(m.seed + 12, 20, 2, x=ts, z=np.zeros_like(ts))
    in_t = td < tw
    in_c = chamber < 15
    cf = float(h0 + (h1 - h0) * 0.5 + 5.0)
    clo = np.where(in_c, cf - 1.5 * (1 - chamber / 15), floor)
    chi = np.where(in_t, floor + 4.5 + 1.5 * (1 - td / np.maximum(tw, 0.1)), 0)
    chi = np.where(in_c, np.maximum(chi, cf + 6 + 7 * (1 - (chamber / 15) ** 2)), chi)
    cavemask = in_t | in_c
    m.clo = np.where(cavemask, np.round(clo), 0).astype(np.int32)
    m.chi = np.where(cavemask, np.round(chi), 0).astype(np.int32)
    # keep a solid roof of 3+ blocks, open where the hill is too low (entrance trench at floor level)
    opening = cavemask & (m.chi >= h - 2)
    h = np.where(opening, np.minimum(h, m.clo - 1), h)
    m.clo = np.where(opening, 0, m.clo)
    m.chi = np.where(opening, 0, m.chi)
    m.hi = h
    # ---------------------------------------------------------------- water
    m.fill_water(inriver | lake | (sd < 4) | pool | gorge | (V > 470) & (rdist < rw + 1), W)
    # ---------------------------------------------------------------- zones + areas
    sites = [(1, 96, 412, 1.35), (2, 220, 388, 1.0), (3, 312, 340, 1.05), (4, 452, 332, 0.95), (5, 255, 248, 1.0),
             (6, 120, 262, 0.9), (7, 126, 110, 0.95), (8, 284, 102, 1.0), (9, 392, 160, 0.95)]
    m.assign_zones(sites)
    m.area(1, 'verdant.camp', 96, 404, 14, climate='safe')
    m.area(2, 'verdant.meadow', 222, 388, 40, large=True, smalls=['grasica', 'boarfang', 'buzzling'])
    m.area(3, 'verdant.river', 318, 352, 30, large=True, smalls=['jagraptor', 'gamenos'])
    m.area(4, 'verdant.cliffs', 452, 326, 34, large=True, smalls=['boarfang', 'jagraptor'])
    m.area(5, 'verdant.ruins', 255, 236, 26, large=True, smalls=['jagraptor', 'nussutto_mofu'])
    m.area(6, 'verdant.forest', 150, 300, 26, smalls=['buzzling', 'grasica', 'nussutto_mofu'])
    m.area(7, 'verdant.plateau', 120, 108, 40, large=True, smalls=['jagraptor'])
    m.area(8, 'verdant.cave', 286, 106, 10, smalls=['gamenos'])
    m.area(9, 'verdant.lake', 352, 172, 26, large=True, smalls=['grasica', 'buzzling', 'gamenos'])
    # ---------------------------------------------------------------- biomes
    B = m.b
    z = m.zj
    wet = ndimage.binary_dilation(m.fluid > 0, iterations=10)
    bio = np.full((T, T), B(f'{NS}:verdant_meadow'), np.uint8)
    bio[z == 6] = B(f'{NS}:verdant_forest')
    bio[(z == 8) & (fbm(m.seed + 13, 30, 2) > -0.1)] = B(f'{NS}:verdant_forest')
    bio[(z == 7) | (z == 4) | (z == 0)] = B(f'{NS}:verdant_highlands')
    bio[(z == 5) & (rd < 34)] = B(f'{NS}:verdant_clearing')
    bio[wet & ((z == 3) | (z == 9) | (z == 2) | (z == 4))] = B(f'{NS}:verdant_riverside')
    bio[dist(cu, cv) < 26] = B(f'{NS}:verdant_clearing')
    bio[dist(128, 112) < 40] = B(f'{NS}:verdant_highlands')
    tree_free = np.zeros((T, T), bool)
    tree_free |= dist(126, 252) < 24          # giant tree clearing
    bio[tree_free] = B(f'{NS}:verdant_clearing')
    m.biome = bio
    m.cave_biome = B(f'{NS}:verdant_cave')
    m.edge_biome = B(f'{NS}:verdant_highlands')
    # ---------------------------------------------------------------- materials
    S = m.S
    sl = m.slope()
    n1 = fbm(m.seed + 14, 9, 2)
    top = np.full((T, T), S('minecraft:grass_block[snowy=false]'), np.int32)
    sub = np.full((T, T), S('minecraft:dirt'), np.int32)
    rock = np.where(fbm(m.seed + 15, 24, 2) > 0.25, S('minecraft:andesite'), S('minecraft:stone')).astype(np.int32)
    forestfloor = (bio == B(f'{NS}:verdant_forest'))
    top[forestfloor & (n1 > 0.25)] = S('minecraft:podzol[snowy=false]')
    top[forestfloor & (n1 < -0.35)] = S('minecraft:coarse_dirt')
    hl = bio == B(f'{NS}:verdant_highlands')
    top[hl & (n1 > 0.3)] = S('minecraft:coarse_dirt')
    top[hl & (n1 < -0.4)] = S('minecraft:stone')
    near_w = ndimage.binary_dilation(m.fluid > 0, iterations=2) & (m.hi <= 64)
    top[near_w] = S('minecraft:sand')
    sub[near_w] = S('minecraft:sand')
    inw = m.fluid > 0
    top[inw] = np.where(n1[inw] > 0.2, S('minecraft:clay'), np.where(n1[inw] < -0.2, S('minecraft:sand'), S('minecraft:gravel')))
    sub[inw] = S('minecraft:gravel')
    steep = sl >= 3
    top[steep] = np.where(n1[steep] > 0, S('minecraft:stone'), S('minecraft:andesite'))
    sub[steep] = S('minecraft:stone')
    top[pmask & ~inw & ~steep] = S('minecraft:dirt_path')
    m.top, m.sub, m.rock = top, sub, rock
    m.ctop = np.where(m.clo > 0, np.where(n1 > 0, S('minecraft:moss_block'), S('minecraft:gravel')), S('minecraft:stone')).astype(np.int32)
    m.outside = {'floor': 50, 'top': S('minecraft:sand'), 'rock': S('minecraft:stone'), 'fluid': FLUID_WATER, 'level': W, 'biome': m.edge_biome}
    # ---------------------------------------------------------------- landmarks
    L.build_camp(m, cu, cv, cy, 'north', 'verdant')
    gt = (126, 252)
    L.giant_tree(m, gt[0], gt[1], m.hi[gt[1], gt[0]] + 1, height=54, seed=31)
    L.ruins(m, 255, 245, 87, half=18, seed=32, gate='south')
    L.lookout_tower(m, 264, 266, m.ground(264, 266), height=13, seed=34, ladder_side='south', parapet_dirs=('south', 'west'))
    # wyvern nest + skeleton on the plateau
    nu, nv = 112, 96
    ny = int(m.hi[nv, nu]) + 1
    for k in range(80):
        ang = k * 2 * math.pi / 80
        for rr in (7, 8):
            u, v = nu + math.cos(ang) * rr, nv + math.sin(ang) * rr
            m.put(u, ny, v, 'minecraft:stripped_dark_oak_log[axis=y]' if k % 3 else 'minecraft:hay_block[axis=y]')
            if k % 4 == 0:
                m.put(u, ny + 1, v, 'minecraft:dark_oak_leaves[persistent=true]')
    for du in range(-6, 7):
        for dv in range(-6, 7):
            if du * du + dv * dv < 36:
                m.put(nu + du, ny - 1, nv + dv, 'minecraft:coarse_dirt' if (du + dv) % 3 else 'minecraft:rooted_dirt')
    L.skeleton(m, 150, 128, int(m.hi[128, 150]) + 1, length=30, ang=2.4, seed=33)
    # waterfall from the east plateau into the plunge pool
    fv = 322
    c = next(u for u in range(392, 430) if m.hi[fv, u] > 80)
    fu = c - 1
    topy = int(m.hi[fv, c])
    for dv in (-1, 0, 1):
        for u in range(c, c + 12):
            m.put(u, topy, fv + dv, 'minecraft:water')
            m.put(u, topy - 1, fv + dv, 'minecraft:mossy_cobblestone')
            for yy in range(topy + 1, topy + 4):
                m.put(u, yy, fv + dv, 'minecraft:air')
        for y in range(W + 1, topy):
            m.put(fu, y, fv + dv, 'minecraft:water[level=8]')
        m.put(fu, topy, fv + dv, 'minecraft:water')
    for dv in (-2, 2):
        for u in range(c, c + 12):
            m.put(u, topy, fv + dv, 'minecraft:mossy_cobblestone')
    for u in (c + 12,):
        for dv in (-1, 0, 1):
            m.put(u, topy, fv + dv, 'minecraft:mossy_cobblestone')
    # stone arch near the meadow
    for k in np.arange(0, math.pi, 0.05):
        au, av = 176 + math.cos(k) * 9, 352
        ay = m.hi[352, 176] + math.sin(k) * 9
        for d in range(-1, 2):
            for t in (0, 1):
                m.put(au, ay + t, av + d, 'minecraft:mossy_cobblestone' if (k * 10) % 3 < 1 else 'minecraft:andesite')
    # boulders + fallen logs
    for (u, v) in scatter(m, rng, (z == 2) & (sl < 2) & ~pmask & (m.fluid == 0), 10, 24):
        L.boulder(m, u, v, m.ground(u, v), 1.6 + rng.random() * 1.6, ['minecraft:mossy_cobblestone', 'minecraft:andesite', 'minecraft:stone'], seed=u * 7 + v)
    for (u, v) in scatter(m, rng, (z == 6) & (sl < 2) & ~pmask & ~tree_free, 8, 26):
        L.fallen_log(m, u, v, m.ground(u, v), 5 + rng.integers(4), rng.random() * math.pi, seed=u + v)
    # cave decoration: glow lichen, mushrooms, underground pond
    for (u, v) in scatter(m, rng, m.clo > 0, 70, 3):
        y = m.clo[v, u]
        r = rng.random()
        if r < 0.45:
            m.put(u, y, v, 'minecraft:glow_lichen[down=true,east=false,north=false,south=false,up=false,waterlogged=false,west=false]')
        elif r < 0.7:
            m.put(u, y, v, 'minecraft:brown_mushroom' if rng.random() < 0.5 else 'minecraft:red_mushroom')
        else:
            m.put(u, m.chi[v, u] + 1, v, 'minecraft:shroomlight')
    # lily pads / reeds
    for (u, v) in scatter(m, rng, (m.fluid == FLUID_WATER) & lake & (m.hi < W - 1), 25, 5):
        m.put(u, W + 1, v, 'minecraft:lily_pad')
    for (u, v) in scatter(m, rng, near_w & (m.fluid == 0) & (m.hi == W) & ~pmask, 30, 4):
        for k in range(1 + rng.integers(3)):
            m.put(u, W + 1 + k, v, 'minecraft:sugar_cane[age=0]')
    # ---------------------------------------------------------------- gathering points
    z = m.zone
    G = m.gather_point
    dry = (m.fluid == 0) & (sl <= 1) & ~pmask & (m.clo == 0)
    taken = [(cu, cv)]
    def pts(mask, n, sp=10):
        r = scatter(m, rng, mask & dry, n, sp, taken)
        taken.extend(r)
        return r
    for (u, v) in pts((z == 2), 8): G(u, v, 'herb')
    for (u, v) in pts((z == 6) & ~tree_free, 6): G(u, v, 'herb')
    for (u, v) in pts((z == 1) & (dist(cu, cv) > 20), 2): G(u, v, 'herb')
    for (u, v) in pts((z == 7), 3): G(u, v, 'herb')
    for (u, v) in pts((z == 9) | (z == 3), 4): G(u, v, 'herb')
    for (u, v) in pts((z == 6), 6, 12): G(u, v, 'mushroom')
    for (u, v) in pts(near_w & ((z == 3) | (z == 9)), 6, 12): G(u, v, 'bug')
    for (u, v) in pts((z == 6) | (z == 2), 4, 14): G(u, v, 'bug')
    for (u, v) in pts((z == 7) & (dist(nu, nv) < 20), 4, 6): G(u, v, 'bone')
    for (u, v) in pts((z == 4), 2): G(u, v, 'bone')
    for (u, v) in pts((z == 5) & (rd > 22), 1): G(u, v, 'bone')
    rocky = ndimage.binary_dilation(steep, iterations=2) & ~steep
    for (u, v) in pts(rocky & ((z == 4) | (z == 7) | (z == 8) | (z == 5)), 6, 16): G(u, v, 'mining')
    for (u, v) in pts((z == 5) & (rd > 20) & (rd < 30), 2, 10): G(u, v, 'mining')
    # cave points on the cave floor
    cave_pts = scatter(m, rng, (m.clo > 0) & (ndimage.binary_erosion(m.clo > 0, iterations=1)), 9, 8)
    for i, (u, v) in enumerate(cave_pts):
        G(u, v, 'mining' if i % 2 == 0 else 'mushroom', y=int(m.clo[v, u]))
    m.fix_fences()
    return m


# ====================================================================== FROSTPEAK RANGE
def frost():
    m = QMap('frostpeak_range', seed=2202, base=78)
    rng = np.random.default_rng(22)
    m.set_biomes([f'{NS}:frostpeak_taiga', f'{NS}:frostpeak_snowfield', f'{NS}:frostpeak_glacier', f'{NS}:frostpeak_summit',
                  f'{NS}:frostpeak_hot_spring', f'{NS}:frostpeak_ice_cave'])
    W = 72
    m.sea = W
    m.h = 78 + 5.0 * fbm(m.seed + 1, 90, 4) + 2.0 * fbm(m.seed + 2, 24, 3)
    # northern mountain mass (rises towards the north wall), ridged
    north = smoothstep(215, 70, V)
    m.h += north * (50 + 38 * ridged(m.seed + 3, 70, 4) + 12 * fbm(m.seed + 4, 30, 3))
    # snowfield (centre) flattened
    sf = smoothstep(1.1, 0.6, ellipse(258, 288, 100, 62))
    m.h = m.h * (1 - sf) + (83 + 2.5 * fbm(m.seed + 5, 40, 3)) * sf
    # taiga valley (SW)
    tv = smoothstep(1.2, 0.7, ellipse(128, 372, 80, 72))
    m.h = m.h * (1 - tv) + (77 + 3.5 * fbm(m.seed + 6, 30, 3)) * tv
    # west pass valley with a knoll for the watchtower
    wp = smoothstep(1.1, 0.6, ellipse(108, 232, 60, 70))
    m.h = m.h * (1 - wp) + (90 + 3 * fbm(m.seed + 7, 25, 3)) * wp
    kn = dist(96, 236)
    m.h = np.maximum(m.h, 90 + 8 * (1 - smoothstep(6, 20, kn)))
    vp = dist(264, 266)
    m.h = np.maximum(m.h, 97 - np.maximum(0, vp - 4) * 0.75)
    # glacier (E): raised ice sheet with crevasses
    ge = ellipse(412, 236, 62, 82, 0.1) + fbm(m.seed + 8, 25, 3) * 0.08
    gl = smoothstep(1.0, 0.9, ge)
    m.h = m.h * (1 - gl) + (100 + 2 * fbm(m.seed + 9, 18, 2)) * gl
    # frozen lake (SE)
    le = ellipse(385, 372, 64, 46, -0.2) + fbm(m.seed + 10, 22, 2) * 0.07
    lake = le < 1.0
    m.h = np.where(le < 1.4, np.minimum(m.h, 73 + np.maximum(0, le - 1.0) * 25), m.h)
    m.h = np.where(lake, np.minimum(m.h, W - 1 - 6 * np.sqrt(np.clip(1 - le ** 2, 0, 1))), m.h)
    # summit plateau (N)
    su = dist(282, 92)
    sumtop = smoothstep(34, 28, su)
    m.h = np.maximum(m.h, 166 - np.maximum(0, su - 30) * 1.25 + 6 * fbm(m.seed + 19, 20, 3))
    m.h = m.h * (1 - sumtop) + (166 + 1.5 * fbm(m.seed + 11, 15, 2)) * sumtop
    # NW ice-cave mountain
    cm = dist(162, 112)
    m.h = np.maximum(m.h, 158 - np.maximum(0, cm - 22) * 1.15 + 8 * ridged(m.seed + 12, 25, 3) * smoothstep(90, 40, cm))
    # NE hot spring hollow: basin carved into the mountains
    hs = ellipse(404, 104, 42, 34, 0.3) + fbm(m.seed + 13, 18, 2) * 0.08
    hol = smoothstep(1.0, 0.8, hs)
    m.h = m.h * (1 - hol) + (101 + 2 * fbm(m.seed + 14, 12, 2)) * hol
    # ---------------------------------------------------------------- boundary wall (towering peaks)
    boundary_wall(m, 196, 16, inner=11, steep=7)
    # ---------------------------------------------------------------- routes
    cu, cv, cy = 256, 434, 78
    paths = [
        smooth_path([(cu, cv - 10), (256, 400), (258, 360), (258, 320), (258, 296)], 8),            # camp -> snowfield
        smooth_path([(cu - 10, cv - 2), (210, 420), (170, 404), (132, 384)], 8),                      # camp -> taiga
        smooth_path([(cu + 10, cv - 2), (300, 420), (330, 410)], 8),                                  # camp -> lake shore
        smooth_path([(132, 384), (116, 330), (104, 290), (100, 256)], 8),                             # taiga -> west pass
        smooth_path([(200, 292), (150, 268), (112, 250)], 8),                                         # snowfield -> pass
        smooth_path([(330, 300), (350, 296), (366, 290)], 8),                                         # snowfield -> glacier ramp
    ]
    pmask = np.zeros((T, T), bool)
    for p in paths:
        d = carve_path_heights(m, p, 2.5)
        pmask |= d < 1.8
    # switchback trail to the summit (terrace cut into the mountain)
    trail = [(230, 268), (222, 242), (300, 214), (240, 190), (306, 166), (252, 146), (290, 126), (284, 112)]
    td, ts, tl = polyline(trail)
    prof = 84 + (166 - 84) * np.clip(ts / tl, 0, 1)
    m.h = np.where(td < 3.5, prof, m.h)
    m.h = np.where((td >= 3.5) & (td < 7), np.maximum(np.minimum(m.h, prof + (td - 3.5) * 3), prof - (td - 3.5) * 3), m.h)
    pmask |= td < 1.8
    # glacier ramp from the snowfield
    gr = [(366, 290), (378, 282), (390, 274)]
    gd, gs, gl2 = polyline(gr)
    gprof = 84 + (100 - 84) * np.clip(gs / gl2, 0, 1)
    m.h = np.where(gd < 4, gprof, m.h)
    m.h = np.where((gd >= 4) & (gd < 8), np.maximum(np.minimum(m.h, gprof + (gd - 4) * 3), gprof - (gd - 4) * 3), m.h)
    # trail from the glacier up to the hot spring hollow
    hr = [(420, 170), (430, 150), (420, 134), (410, 120)]
    hd, hs2, hl = polyline(hr)
    hprof = 100 + (101 - 100) * np.clip(hs2 / hl, 0, 1)
    m.h = np.where(hd < 4, hprof, m.h)
    m.h = np.where((hd >= 4) & (hd < 8), np.maximum(np.minimum(m.h, hprof + (hd - 4) * 4), hprof - (hd - 4) * 3), m.h)
    pmask |= (gd < 1.8) | (hd < 1.8)
    # camp pad
    L.flatten_pad(m, cu, cv, cy, 12, 22)
    m.finalize_heights()
    h = m.hi
    # glacier crevasses (narrow trenches)
    cre = np.zeros((T, T), bool)
    for k, (a, b, c2, d2) in enumerate([(392, 196, 428, 208), (404, 240, 446, 254), (410, 290, 448, 300), (372, 214, 388, 246)]):
        cd, _, _ = polyline([(a, b), ((a + c2) / 2 + 6, (b + d2) / 2 - 5), (c2, d2)])
        cre |= (cd < 2.2) & (gl > 0.9)
    h = np.where(cre, h - 11, h)
    # hot spring pools
    pools = np.zeros((T, T), bool)
    for (pu, pv, pr) in [(398, 100, 7), (414, 110, 5), (392, 116, 4)]:
        pd = dist(pu, pv)
        pools |= pd < pr
        h = np.where(pd < pr, np.minimum(h, 98 - 2 * (1 - pd / pr)), h)
    hot_level = 100
    # ice cave: entrance in the west pass, tunnel into the NW mountain, big chamber
    tunnel = smooth_path([(118, 186), (128, 166), (142, 146), (156, 128), (162, 114)], 10)
    tdd, tss, tll = polyline(tunnel)
    tt = np.clip(tss / tll, 0, 1)
    f0 = float(h[186, 118])
    floor = f0 + 6 * tt
    ch = dist(164, 108)
    in_t = tdd < 3.4
    in_c = ch < 24
    clo = np.where(in_c, f0 + 6 - 2 * (1 - ch / 24), floor)
    chi = np.where(in_t, floor + 5 + 1.5 * (1 - tdd / 3.4), 0)
    chi = np.where(in_c, np.maximum(chi, f0 + 12 + 14 * np.sqrt(np.clip(1 - (ch / 24) ** 2, 0, 1))), chi)
    cavemask = in_t | in_c
    m.clo = np.where(cavemask, np.round(clo), 0).astype(np.int32)
    m.chi = np.where(cavemask, np.round(chi), 0).astype(np.int32)
    opening = cavemask & (m.chi >= h - 2)
    h = np.where(opening, np.minimum(h, m.clo - 1), h)
    m.clo = np.where(opening, 0, m.clo)
    m.chi = np.where(opening, 0, m.chi)
    m.hi = np.round(h).astype(np.int32)
    m.fill_water(lake, W, FLUID_FROZEN)
    m.fill_water(pools, hot_level, FLUID_WATER)
    # ---------------------------------------------------------------- zones / areas
    sites = [(1, 256, 436, 1.35), (2, 126, 372, 0.95), (3, 384, 372, 0.95), (4, 258, 290, 1.0), (5, 414, 236, 0.95),
             (6, 104, 236, 1.0), (7, 160, 112, 1.0), (8, 282, 96, 0.95), (9, 404, 104, 1.05)]
    m.assign_zones(sites)
    m.area(1, 'frost.camp', 256, 426, 14, climate='safe')
    m.area(2, 'frost.taiga', 128, 372, 30, smalls=['boarfang', 'blizraptor', 'nussutto_mofu'])
    m.area(3, 'frost.lake', 380, 372, 36, large=True, smalls=['blizraptor', 'yukimammo'])
    m.area(4, 'frost.snowfield', 258, 296, 44, large=True, smalls=['yukimammo', 'blizraptor'])
    m.area(5, 'frost.glacier', 420, 228, 30, large=True, smalls=['blizraptor'])
    m.area(6, 'frost.pass', 112, 250, 26, large=True, smalls=['blizraptor', 'boarfang'], climate='cold')
    m.area(7, 'frost.icecave', 164, 108, 12, smalls=['blizraptor'])
    m.area(8, 'frost.summit', 282, 92, 26, large=True, smalls=['blizraptor'])
    m.area(9, 'frost.hotspring', 404, 106, 20, climate='safe', smalls=['nussutto_mofu', 'yukimammo'])
    for n in (2, 3, 4, 5, 7, 8):
        m.areas[n]['climate'] = 1
    m.areas[9]['climate'] = 0
    # ---------------------------------------------------------------- biomes
    B = m.b
    z = m.zj
    bio = np.full((T, T), B(f'{NS}:frostpeak_snowfield'), np.uint8)
    bio[(z == 2) | ((z == 1) & (dist(cu, cv) > 26)) | ((z == 6) & (fbm(m.seed + 15, 30, 2) > 0.0))] = B(f'{NS}:frostpeak_taiga')
    bio[(z == 5) | (z == 7)] = B(f'{NS}:frostpeak_glacier')
    bio[(z == 8) | (m.hi > 140)] = B(f'{NS}:frostpeak_summit')
    bio[(z == 9) & (hol > 0.2)] = B(f'{NS}:frostpeak_hot_spring')
    bio[dist(cu, cv) < 24] = B(f'{NS}:frostpeak_snowfield')
    m.biome = bio
    m.cave_biome = B(f'{NS}:frostpeak_ice_cave')
    m.edge_biome = B(f'{NS}:frostpeak_summit')
    # ---------------------------------------------------------------- materials
    S = m.S
    sl = m.slope()
    n1 = fbm(m.seed + 16, 9, 2)
    snow = S('minecraft:snow_block')
    top = np.full((T, T), snow, np.int32)
    sub = np.full((T, T), snow, np.int32)
    rock = np.where(fbm(m.seed + 17, 30, 2) > 0.3, S('minecraft:andesite'), S('minecraft:stone')).astype(np.int32)
    taiga = bio == B(f'{NS}:frostpeak_taiga')
    top[taiga] = np.where(n1[taiga] > 0.3, S('minecraft:podzol[snowy=false]'), S('minecraft:grass_block[snowy=false]'))
    sub[taiga] = S('minecraft:dirt')
    glac = bio == B(f'{NS}:frostpeak_glacier')
    top[glac] = np.where(n1[glac] > 0.35, S('minecraft:blue_ice'), np.where(n1[glac] > -0.1, S('minecraft:packed_ice'), snow))
    sub[glac] = S('minecraft:packed_ice')
    rock[(gl > 0.5) | (z == 7)] = S('minecraft:packed_ice')
    rock[((gl > 0.5) | (z == 7)) & (fbm(m.seed + 18, 8, 2) > 0.3)] = S('minecraft:blue_ice')
    hsb = bio == B(f'{NS}:frostpeak_hot_spring')
    top[hsb] = np.where(n1[hsb] > 0.2, S('minecraft:coarse_dirt'), np.where(n1[hsb] < -0.3, S('minecraft:gravel'), S('minecraft:grass_block[snowy=false]')))
    sub[hsb] = S('minecraft:dirt')
    steep = sl >= 3
    top[steep & ~glac] = np.where(n1[steep & ~glac] > 0.0, S('minecraft:stone'), S('minecraft:andesite'))
    sub[steep & ~glac] = S('minecraft:stone')
    top[steep & glac] = S('minecraft:packed_ice')
    inw = m.fluid > 0
    top[inw] = S('minecraft:gravel')
    sub[inw] = S('minecraft:gravel')
    top[pools] = S('minecraft:soul_sand')
    top[pmask & ~steep & ~inw] = S('minecraft:coarse_dirt')
    top[(td < 1.8) & ~inw] = S('minecraft:gravel')
    m.top, m.sub, m.rock = top, sub, rock
    m.ctop = np.where(m.clo > 0, np.where(n1 > 0.1, S('minecraft:packed_ice'), np.where(n1 < -0.3, S('minecraft:blue_ice'), snow)), snow).astype(np.int32)
    m.outside = {'floor': 150, 'top': snow, 'rock': S('minecraft:stone'), 'fluid': 0, 'level': 0, 'biome': m.edge_biome}
    # ---------------------------------------------------------------- landmarks
    L.build_camp(m, cu, cv, cy, 'north', 'frost')
    # ice spikes around the frozen lake + on the summit
    for k, (u, v) in enumerate(scatter(m, rng, (le > 1.05) & (le < 1.5) & (sl <= 2) & ~pmask, 9, 14)):
        L.spire(m, u, v, m.ground(u, v) - 1, 12 + rng.integers(18), 2.0 + rng.random() * 1.5,
                ['minecraft:packed_ice', 'minecraft:blue_ice', 'minecraft:ice'], seed=k + 100)
    for k, (u, v) in enumerate(scatter(m, rng, (su > 24) & (su < 34) & (sl <= 2), 7, 10)):
        L.spire(m, u, v, m.ground(u, v) - 1, 10 + rng.integers(14), 1.8 + rng.random(), ['minecraft:packed_ice', 'minecraft:blue_ice'], seed=k + 200)
    L.tower(m, 96, 236, m.ground(96, 236), r=5, height=20, seed=71)
    L.skeleton(m, 270, 84, m.ground(270, 84), length=36, ang=0.6, seed=72, block='minecraft:bone_block')
    L.skeleton(m, 228, 330, m.ground(228, 330), length=22, ang=-2.0, seed=73, wings=False)
    # hot spring: rocks, bubbles
    for (u, v) in scatter(m, rng, pools & (m.hi <= 97), 14, 3):
        for y in range(int(m.hi[v, u]) + 1, hot_level + 1):
            m.put(u, y, v, 'minecraft:bubble_column[drag=false]')
    for (u, v) in scatter(m, rng, hsb & ~pools & (sl <= 2) & ndimage.binary_dilation(pools, iterations=3), 10, 5):
        L.boulder(m, u, v, m.ground(u, v), 1.2 + rng.random(), ['minecraft:mossy_cobblestone', 'minecraft:tuff', 'minecraft:andesite'], seed=u * 3 + v)
    # ice cave decoration: blue ice pillars + froglights
    cave_in = (m.clo > 0)
    for k, (u, v) in enumerate(scatter(m, rng, cave_in & (ch < 20), 10, 7)):
        for y in range(int(m.clo[v, u]), int(m.chi[v, u]) + 1):
            for du in (0, 1):
                for dv in (0, 1):
                    m.put(u + du, y, v + dv, 'minecraft:blue_ice' if (y + k) % 5 else 'minecraft:packed_ice')
    for (u, v) in scatter(m, rng, cave_in, 30, 5):
        m.put(u, int(m.chi[v, u]) + 1, v, 'minecraft:pearlescent_froglight[axis=y]')
    for (u, v) in scatter(m, rng, cave_in & (ch < 22), 20, 4):
        m.put(u, int(m.clo[v, u]) - 1, v, 'minecraft:ice')
    z = m.zone
    # boulders on the snowfield
    for (u, v) in scatter(m, rng, (z == 4) & (sl <= 1) & ~pmask & (dist(264, 266) > 30), 10, 22):
        L.boulder(m, u, v, m.ground(u, v), 1.5 + rng.random() * 1.5, ['minecraft:stone', 'minecraft:andesite', 'minecraft:snow_block'], seed=u + 7 * v)
    for k, (du, dv) in enumerate([(-1, -1), (1, -1), (-1, 1), (1, 1), (0, 0)]):
        y = m.ground(264 + du, 266 + dv)
        m.put(264 + du, y, 266 + dv, 'minecraft:cobblestone' if k < 4 else 'minecraft:stone_bricks')
    m.put(264, m.ground(264, 266) + 1, 266, 'minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    # ---------------------------------------------------------------- gathering points
    G = m.gather_point
    dry = (m.fluid == 0) & (sl <= 1) & ~pmask & (m.clo == 0) & ~cre
    taken = [(cu, cv)]
    def pts(mask, n, sp=10):
        r = scatter(m, rng, mask & dry, n, sp, taken)
        taken.extend(r)
        return r
    for (u, v) in pts(z == 2, 7): G(u, v, 'herb')
    for (u, v) in pts(z == 6, 3): G(u, v, 'herb')
    for (u, v) in pts(z == 9, 3): G(u, v, 'herb')
    for (u, v) in pts((z == 1) & (dist(cu, cv) > 20), 2): G(u, v, 'herb')
    for (u, v) in pts(z == 2, 4, 14): G(u, v, 'mushroom')
    for (u, v) in pts(z == 9, 3): G(u, v, 'mushroom')
    for (u, v) in pts(z == 9, 3): G(u, v, 'bug')
    for (u, v) in pts((z == 2) | (z == 3), 3, 16): G(u, v, 'bug')
    for (u, v) in pts(z == 8, 4, 8): G(u, v, 'bone')
    for (u, v) in pts((z == 4) & (dist(228, 330) < 30), 2, 8): G(u, v, 'bone')
    for (u, v) in pts(z == 5, 2): G(u, v, 'bone')
    rocky = ndimage.binary_dilation(steep, iterations=2) & ~steep
    for (u, v) in pts(rocky & ((z == 6) | (z == 8) | (z == 4) | (z == 3)), 7, 16): G(u, v, 'mining')
    for (u, v) in pts(z == 5, 4, 14): G(u, v, 'mining')
    cave_pts = scatter(m, rng, cave_in & ndimage.binary_erosion(cave_in, iterations=1), 10, 7)
    for i, (u, v) in enumerate(cave_pts):
        G(u, v, 'mining' if i % 3 else 'mushroom', y=int(m.clo[v, u]))
    m.fix_fences()
    return m


# ====================================================================== EMBER CALDERA
def ember():
    m = QMap('ember_caldera', seed=3303, base=64)
    rng = np.random.default_rng(33)
    m.set_biomes([f'{NS}:ember_outer_slopes', f'{NS}:ember_ash_wastes', f'{NS}:ember_basalt_deltas', f'{NS}:ember_lava_lake',
                  f'{NS}:ember_fumaroles', f'{NS}:ember_lava_tube'])
    LV = 52
    m.sea = LV
    m.h = 64 + 5.0 * fbm(m.seed + 1, 90, 4) + 2.0 * fbm(m.seed + 2, 24, 3)
    # the caldera floor slopes up towards the rim
    e0 = edge_dist()
    m.h += smoothstep(90, 10, e0) * (14 + 6 * fbm(m.seed + 3, 40, 3))
    # ash dunes (S)
    ad = smoothstep(1.2, 0.6, ellipse(240, 408, 95, 52))
    m.h = m.h * (1 - ad) + (67 + 3.0 * np.sin(U / 11.0 + 2.0 * np.sin(V / 17.0)) + 2.0 * fbm(m.seed + 4, 20, 2)) * ad
    # dragon graveyard basin (NW)
    gd = smoothstep(1.1, 0.6, ellipse(126, 112, 66, 54))
    m.h = m.h * (1 - gd) + (74 + 1.5 * fbm(m.seed + 5, 25, 2)) * gd
    # fumarole plateau (N) with cliffs
    fe = ellipse(270, 94, 64, 40, 0.0) + fbm(m.seed + 6, 22, 3) * 0.1
    fp = smoothstep(1.0, 0.93, fe)
    m.h = m.h * (1 - fp) + (92 + 2.0 * fbm(m.seed + 7, 15, 2)) * fp
    # altar spur (NE)
    ae = ellipse(404, 104, 46, 40, 0.5) + fbm(m.seed + 8, 22, 3) * 0.08
    ap = smoothstep(1.0, 0.92, ae)
    m.h = m.h * (1 - ap) + (110 + 1.0 * fbm(m.seed + 9, 15, 2)) * ap
    alt = smoothstep(20, 17, dist(408, 100))
    m.h = m.h * (1 - alt) + 111 * alt
    # lava lake (centre)
    le = ellipse(252, 252, 74, 58, 0.1) + fbm(m.seed + 10, 26, 3) * 0.08
    lake = le < 1.0
    m.h = np.where(le < 1.5, np.minimum(m.h, 54 + np.maximum(0, le - 1.0) * 24), m.h)
    m.h = np.where(lake, np.minimum(m.h, LV - 2 - 6 * np.sqrt(np.clip(1 - le ** 2, 0, 1))), m.h)
    isl = dist(264, 264)
    m.h = np.where(isl < 9, np.maximum(m.h, 56 + 3 * (1 - isl / 9)), m.h)
    # lava river (E) joining the lake
    river = smooth_path([(448, 128), (436, 168), (414, 210), (384, 238), (348, 250), (318, 254)], 10)
    rdist, rs, rlen = polyline(river)
    rw = 4.0 + 1.0 * fbm(m.seed + 11, 25, 2, x=rs, z=np.zeros_like(rs))
    m.h = np.where(rdist < rw + 14, np.minimum(m.h, 55 + np.maximum(0, rdist - rw) * 1.6), m.h)
    inriver = rdist < rw
    m.h = np.where(inriver, np.minimum(m.h, LV - 3), m.h)
    # basalt pillar forest (SE)
    bf = smoothstep(1.1, 0.7, ellipse(392, 384, 70, 62))
    m.h = m.h * (1 - bf * 0.6) + 66 * bf * 0.6
    pillars = np.zeros((T, T))
    cand = [(int(u), int(v)) for (u, v) in zip(rng.integers(330, 460, 160), rng.integers(320, 450, 160))]
    for (pu, pv) in cand:
        if ellipse(392, 384, 70, 62)[pv, pu] > 0.95:
            continue
        r = 1.5 + rng.random() * 2.2
        ph = 6 + rng.random() * 20
        pillars = np.maximum(pillars, np.where(dist(pu, pv) < r, ph, 0))
    # ---------------------------------------------------------------- boundary wall (caldera rim)
    boundary_wall(m, 160, 16, inner=12, steep=8)
    # ---------------------------------------------------------------- routes
    cu, cv, cy = 102, 418, 81
    paths = [
        smooth_path([(cu + 10, cv), (150, 414), (200, 410), (240, 406), (290, 404), (330, 396)], 8),       # camp -> ash -> basalt
        smooth_path([(cu, cv - 10), (110, 376), (128, 344), (146, 330)], 8),                               # camp -> tube entrance
        smooth_path([(240, 406), (244, 360), (248, 322), (250, 312)], 8),                                  # ash -> lake south shore
        smooth_path([(330, 396), (360, 340), (392, 300), (420, 270), (436, 232)], 8),                      # basalt -> river east bank
        smooth_path([(250, 312), (200, 304), (170, 290), (150, 260)], 8),                                  # lake W shore
        smooth_path([(126, 196), (140, 160), (130, 130)], 8),                                              # tube exit -> graveyard
        smooth_path([(186, 196), (222, 176), (246, 164), (258, 156)], 8),                                  # lake NW -> plateau ramp
        smooth_path([(436, 232), (446, 200), (440, 170), (426, 146)], 8),                                  # river bank -> altar ramp
    ]
    pmask = np.zeros((T, T), bool)
    for p in paths:
        d = carve_path_heights(m, p, 2.5)
        pmask |= d < 1.8
    for (pts_, h0, h1) in [([(258, 156), (262, 138), (266, 122), (270, 110)], None, 92), ([(426, 146), (416, 130), (410, 118)], None, 110)]:
        rd, rsl, rl = polyline(pts_)
        a = float(m.h[int(pts_[0][1]), int(pts_[0][0])])
        prof = a + (h1 - a) * np.clip(rsl / rl, 0, 1)
        m.h = np.where(rd < 4, prof, m.h)
        m.h = np.where((rd >= 4) & (rd < 8), np.maximum(np.minimum(m.h, prof + (rd - 4) * 3), prof - (rd - 4) * 3), m.h)
        pmask |= rd < 1.8
    tunnel = smooth_path([(146, 330), (132, 300), (116, 268), (112, 240), (118, 214), (126, 196)], 10)
    tdd0, tss0, tll0 = polyline(tunnel)
    mid = smoothstep(0.0, 0.18, tss0 / tll0) * smoothstep(1.0, 0.82, tss0 / tll0)
    ridge = (82 + 8 * fbm(m.seed + 14, 20, 3)) * smoothstep(26, 8, tdd0) * mid
    m.h = np.maximum(m.h, ridge)
    L.flatten_pad(m, cu, cv, cy, 12, 22)
    m.h = np.where(pmask, m.h, m.h + pillars)
    m.finalize_heights()
    h = m.hi
    # lava tube (W)
    tdd, tss, tll = polyline(tunnel)
    tt = np.clip(tss / tll, 0, 1)
    f0, f1 = float(h[330, 146]), float(h[196, 126])
    floor = f0 + (f1 - f0) * tt + 4 * np.sin(np.pi * tt)
    ch = dist(114, 256)
    in_t = tdd < 3.6
    in_c = ch < 16
    cf = float(f0 + (f1 - f0) * 0.45 + 4)
    clo = np.where(in_c, cf - 1, floor)
    chi = np.where(in_t, floor + 5 + 1.5 * (1 - tdd / 3.6), 0)
    chi = np.where(in_c, np.maximum(chi, cf + 7 + 6 * np.sqrt(np.clip(1 - (ch / 16) ** 2, 0, 1))), chi)
    cavemask = in_t | in_c
    m.clo = np.where(cavemask, np.round(clo), 0).astype(np.int32)
    m.chi = np.where(cavemask, np.round(chi), 0).astype(np.int32)
    opening = cavemask & (m.chi >= h - 2)
    h = np.where(opening, np.minimum(h, m.clo - 1), h)
    m.clo = np.where(opening, 0, m.clo)
    m.chi = np.where(opening, 0, m.chi)
    m.hi = np.round(h).astype(np.int32)
    m.fill_water(lake | inriver, LV, FLUID_LAVA)
    # ---------------------------------------------------------------- zones / areas
    sites = [(1, 102, 418, 1.35), (2, 236, 410, 1.0), (3, 392, 386, 0.95), (4, 420, 238, 1.0), (5, 252, 252, 0.85),
             (6, 112, 262, 1.0), (7, 126, 112, 0.95), (8, 270, 94, 1.0), (9, 404, 104, 1.0)]
    m.assign_zones(sites)
    m.area(1, 'ember.camp', 102, 410, 14, climate='safe')
    m.area(2, 'ember.ash', 236, 404, 40, large=True, smalls=['heatgecko', 'gamenos'])
    m.area(3, 'ember.basalt', 380, 372, 24, smalls=['gansekigani', 'heatgecko'])
    m.area(4, 'ember.river', 440, 236, 18, large=True, smalls=['heatgecko', 'gansekigani'])
    m.area(5, 'ember.lake', 250, 318, 26, large=True, smalls=['heatgecko'])
    m.area(6, 'ember.tube', 114, 256, 10, smalls=['gansekigani'])
    m.area(7, 'ember.graveyard', 126, 112, 36, large=True, smalls=['heatgecko', 'nussutto_mofu'])
    m.area(8, 'ember.fumaroles', 270, 94, 30, large=True, smalls=['gansekigani', 'heatgecko'])
    m.area(9, 'ember.altar', 408, 100, 16, large=True, smalls=['heatgecko'])
    for n in (3, 4, 5, 6, 8, 9):
        m.areas[n]['climate'] = 2
    # ---------------------------------------------------------------- biomes
    B = m.b
    z = m.zj
    bio = np.full((T, T), B(f'{NS}:ember_outer_slopes'), np.uint8)
    bio[(z == 2) | (z == 7)] = B(f'{NS}:ember_ash_wastes')
    bio[(z == 3) | (z == 6)] = B(f'{NS}:ember_basalt_deltas')
    near_lava = ndimage.binary_dilation(m.fluid == FLUID_LAVA, iterations=14)
    bio[near_lava | (z == 5) | (z == 4)] = B(f'{NS}:ember_lava_lake')
    bio[(z == 8) | (z == 9)] = B(f'{NS}:ember_fumaroles')
    bio[dist(cu, cv) < 26] = B(f'{NS}:ember_outer_slopes')
    m.biome = bio
    m.cave_biome = B(f'{NS}:ember_lava_tube')
    m.edge_biome = B(f'{NS}:ember_basalt_deltas')
    # ---------------------------------------------------------------- materials
    S = m.S
    sl = m.slope()
    n1 = fbm(m.seed + 12, 9, 2)
    n2 = fbm(m.seed + 13, 30, 2)
    top = np.full((T, T), S('minecraft:blackstone'), np.int32)
    sub = np.full((T, T), S('minecraft:blackstone'), np.int32)
    rock = np.where(n2 > 0.2, S('minecraft:basalt[axis=y]'), S('minecraft:blackstone')).astype(np.int32)
    outer = bio == B(f'{NS}:ember_outer_slopes')
    top[outer] = np.where(n1[outer] > 0.25, S('minecraft:coarse_dirt'), np.where(n1[outer] < -0.25, S('minecraft:tuff'), S('minecraft:packed_mud')))
    sub[outer] = S('minecraft:tuff')
    ash = bio == B(f'{NS}:ember_ash_wastes')
    top[ash] = np.where(n1[ash] > 0.3, S('minecraft:gravel'), np.where(n1[ash] < -0.2, S('minecraft:tuff'), S('minecraft:calcite')))
    sub[ash] = S('minecraft:tuff')
    dl = bio == B(f'{NS}:ember_basalt_deltas')
    top[dl] = np.where(n1[dl] > 0.2, S('minecraft:basalt[axis=y]'), np.where(n1[dl] < -0.3, S('minecraft:magma_block'), S('minecraft:smooth_basalt')))
    sub[dl] = S('minecraft:basalt[axis=y]')
    lk = bio == B(f'{NS}:ember_lava_lake')
    top[lk] = np.where(n1[lk] > 0.35, S('minecraft:magma_block'), np.where(n1[lk] < -0.2, S('minecraft:smooth_basalt'), S('minecraft:blackstone')))
    fu = bio == B(f'{NS}:ember_fumaroles')
    top[fu] = np.where(n1[fu] > 0.3, S('minecraft:yellow_terracotta'), np.where(n1[fu] < -0.25, S('minecraft:magma_block'), S('minecraft:terracotta')))
    sub[fu] = S('minecraft:terracotta')
    steep = sl >= 3
    top[steep] = S('minecraft:basalt[axis=y]')
    sub[steep] = S('minecraft:basalt[axis=y]')
    pil = (pillars > 0) & ~pmask
    top[pil] = S('minecraft:basalt[axis=y]')
    sub[pil] = S('minecraft:basalt[axis=y]')
    rock[pil] = S('minecraft:basalt[axis=y]')
    inl = m.fluid == FLUID_LAVA
    top[inl] = S('minecraft:magma_block')
    sub[inl] = S('minecraft:blackstone')
    top[pmask & ~steep & ~inl] = S('minecraft:smooth_basalt')
    tube_wall = ndimage.binary_dilation(m.clo > 0, iterations=3)
    rock[tube_wall] = np.where(n1[tube_wall] > 0.4, S('minecraft:crying_obsidian'), S('minecraft:obsidian'))
    m.top, m.sub, m.rock = top, sub, rock
    m.ctop = np.where(m.clo > 0, np.where(n1 > 0.2, S('minecraft:magma_block'), S('minecraft:smooth_basalt')), S('minecraft:blackstone')).astype(np.int32)
    m.outside = {'floor': 40, 'top': S('minecraft:basalt[axis=y]'), 'rock': S('minecraft:blackstone'), 'fluid': FLUID_LAVA, 'level': LV, 'biome': m.edge_biome}
    z = m.zone
    # ---------------------------------------------------------------- landmarks
    L.build_camp(m, cu, cv, cy, 'east', 'ember')
    # obsidian spire on the lake island
    L.spire(m, 264, 264, int(m.hi[264, 264]), 26, 4.0, ['minecraft:obsidian', 'minecraft:crying_obsidian', 'minecraft:obsidian'], seed=301)
    # giant dragon skeleton in the graveyard
    L.skeleton(m, 140, 96, m.ground(140, 96), length=48, ang=2.6, seed=302)
    for k, (u, v) in enumerate(scatter(m, rng, (z == 7) & (sl <= 1) & ~pmask, 12, 12)):
        L.boulder(m, u, v, m.ground(u, v), 1.0 + rng.random(), ['minecraft:bone_block[axis=y]', 'minecraft:bone_block[axis=x]'], seed=k + 330)
    # natural bridges over the lava river
    for (bu, bv) in [(430, 180), (398, 228)]:
        k = int(np.argmin(np.abs(np.array([p[0] for p in river]) - bu) + np.abs(np.array([p[1] for p in river]) - bv)))
        (ax, az), (bx, bz) = river[max(0, k - 1)], river[min(len(river) - 1, k + 1)]
        tx, tz = bx - ax, bz - az
        ln = math.hypot(tx, tz)
        px, pz = -tz / ln, tx / ln
        y0 = max(int(m.hi[int(bv + pz * 12), int(bu + px * 12)]), int(m.hi[int(bv - pz * 12), int(bu - px * 12)]))
        for s in np.arange(-13, 13.5, 0.5):
            cy0 = y0 + 3 * math.cos(s / 13 * math.pi / 2)
            for w in (-2, -1, 0, 1, 2):
                uu, vv = bu + px * s + tx / ln * w, bv + pz * s + tz / ln * w
                m.put(uu, cy0, vv, 'minecraft:smooth_basalt')
                m.put(uu, cy0 - 1, vv, 'minecraft:basalt[axis=y]')
                if abs(w) == 2:
                    m.put(uu, cy0 - 2, vv, 'minecraft:blackstone')
    # fumarole vents: campfire pits with hay below (tall smoke columns)
    vents = scatter(m, rng, (z == 8) & (fp > 0.9) & (sl <= 1) & ~pmask, 8, 12)
    for (u, v) in vents:
        y = int(m.hi[v, u])
        m.put(u, y - 1, v, 'minecraft:hay_block[axis=y]')
        m.put(u, y, v, 'minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
        for du in (-1, 0, 1):
            for dv in (-1, 0, 1):
                if du or dv:
                    m.put(u + du, y, v + dv, 'minecraft:magma_block' if rng.random() < 0.5 else 'minecraft:basalt[axis=y]')
                    m.put(u + du, y + 1, v + dv, 'minecraft:air')
    # rim altar
    au, av = 408, 100
    ay = m.ground(au, av)
    for du in range(-12, 13):
        for dv in range(-12, 13):
            r2 = du * du + dv * dv
            if r2 <= 144:
                m.put(au + du, ay - 1, av + dv, 'minecraft:polished_blackstone_bricks' if r2 > 30 else 'minecraft:gilded_blackstone' if (du + dv) % 4 == 0 else 'minecraft:chiseled_polished_blackstone')
    for k in range(8):
        ang = k * math.pi / 4
        pu, pv = au + math.cos(ang) * 10, av + math.sin(ang) * 10
        ph = 6 if k % 2 == 0 else 3 + rng.integers(2)
        for y in range(ay, ay + ph):
            m.put(pu, y, pv, 'minecraft:polished_blackstone_bricks' if y < ay + ph - 1 else 'minecraft:chiseled_polished_blackstone')
        if k % 2 == 0:
            m.put(pu, ay + ph, pv, 'minecraft:netherrack')
            m.put(pu, ay + ph + 1, pv, 'minecraft:fire[age=0,east=false,north=false,south=false,up=false,west=false]')
    m.put(au, ay, av, 'minecraft:lodestone')
    # lava pools inside the tube chamber (contained: floor lowered by one)
    for (u, v) in scatter(m, rng, (m.clo > 0) & (ch < 11), 3, 6):
        for du in range(-2, 3):
            for dv in range(-2, 3):
                if du * du + dv * dv <= 4 and m.clo[v + dv, u + du] > 0:
                    y = int(m.clo[v + dv, u + du]) - 1
                    m.put(u + du, y, v + dv, 'minecraft:lava')
    for (u, v) in scatter(m, rng, m.clo > 0, 24, 5):
        m.put(u, int(m.chi[v, u]) + 1, v, 'minecraft:shroomlight' if rng.random() < 0.3 else 'minecraft:magma_block')
    # obsidian shards along the lake shore
    for k, (u, v) in enumerate(scatter(m, rng, (le > 1.08) & (le < 1.4) & (sl <= 2) & ~pmask, 8, 18)):
        L.spire(m, u, v, m.ground(u, v) - 1, 5 + rng.integers(8), 1.4 + rng.random(), ['minecraft:obsidian', 'minecraft:crying_obsidian'], seed=k + 340)
    # ---------------------------------------------------------------- gathering points
    G = m.gather_point
    dry = (m.fluid == 0) & (sl <= 1) & ~pmask & (m.clo == 0) & (pillars == 0)
    taken = [(cu, cv)]
    def pts(mask, n, sp=10):
        r = scatter(m, rng, mask & dry & ~ndimage.binary_dilation(m.fluid == FLUID_LAVA, iterations=2), n, sp, taken)
        taken.extend(r)
        return r
    for (u, v) in pts((z == 1) & (dist(cu, cv) > 20), 3): G(u, v, 'herb')
    for (u, v) in pts(z == 2, 6): G(u, v, 'herb')
    for (u, v) in pts(z == 7, 3): G(u, v, 'herb')
    for (u, v) in pts(z == 8, 2): G(u, v, 'herb')
    for (u, v) in pts((z == 3) | (z == 8), 6, 14): G(u, v, 'bug')
    for (u, v) in pts(z == 7, 7, 8): G(u, v, 'bone')
    for (u, v) in pts(z == 2, 2): G(u, v, 'bone')
    for (u, v) in pts(z == 3, 5, 12): G(u, v, 'mining')
    for (u, v) in pts((z == 5) | (z == 4), 6, 14): G(u, v, 'mining')
    for (u, v) in pts(z == 9, 3, 10): G(u, v, 'mining')
    cave_pts = scatter(m, rng, (m.clo > 0) & ndimage.binary_erosion(m.clo > 0, iterations=1), 10, 7)
    for i, (u, v) in enumerate(cave_pts):
        y = int(m.clo[v, u])
        if m.get(u, y - 1, v) == 'minecraft:lava':
            continue
        G(u, v, 'mining' if i % 3 else 'mushroom', y=y)
    m.fix_fences()
    return m


MAPS = {'verdant': verdant, 'frost': frost, 'ember': ember}


def main():
    names = sys.argv[1:] or list(MAPS.keys())
    for n in names:
        m = MAPS[n]()
        m.report()
        m.export()
        m.save_previews()


if __name__ == '__main__':
    main()
