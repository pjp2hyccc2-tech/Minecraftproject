"""Kawagiri line: alignment, track bed (cuttings, embankments, viaducts, tunnels), stations, Akashi Kaikyo Bridge, rolling stock."""
import math
import random

import numpy as np

import terrain as T
from bcommon import (AIR, TB, fillet_path, path_dirs, dominant_facing, tree, utility_pole, street_lamp, vending, bench, fix_stairs,
                     fix_connections, hip_roof, flat_roof)
from wb import stairs, slab, facing, pane, axis, YAW

RAIL_OFF = 0.8125       # rail top above the track block's y (full track)
RAIL_OFF_LOW = 0.3125   # rail top above the low track block's y

LINE = {}


def coast(x):
    return float(T.coast_z(np.array([x]))[0])


def alignment():
    """returns dense centreline points (x, z) for the Kawagiri line, with fixed straights at stations"""
    # coastal section follows the shore 22 blocks inland, smoothed
    cz = {}
    for x in range(1800, 3340, 10):
        cz[x] = coast(x)
    def shore(x):
        xs = [k for k in cz if abs(k - x) <= 60]
        return sum(cz[k] for k in xs) / len(xs) - 24
    shiomi_z = round(min(shore(x) for x in range(2040, 2181, 10)))
    LINE['shiomi_z'] = shiomi_z
    wps = [(600, 51), (990, 51), (1150, 100), (1340, 100), (1520, 300), (1730, 520), (1880, round(shore(1880)))]
    for x in range(1960, 2040, 40):
        wps.append((x, round((shore(x) + shiomi_z) / 2)))
    wps += [(2040, shiomi_z), (2180, shiomi_z)]
    for x in range(2300, 3240, 120):
        wps.append((x, round(shore(x))))
    wps += [(3250, 676), (3330, 645), (3470, 645)]
    return fillet_path(wps, 110, 1.0)


def profile(pts):
    """desired track-block level (float) per dense point; stations flat"""
    xs = np.array([p[0] for p in pts])
    n = len(pts)
    s = np.arange(n, dtype=np.float64)
    # anchor heights by x position along the line
    def idx_at_x(x, after=0):
        for i in range(after, n):
            if pts[i][0] >= x:
                return i
        return n - 1
    i_okawa_end = idx_at_x(660)
    i_kiri_a = idx_at_x(1180)
    i_kiri_b = idx_at_x(1310)
    i_coast = idx_at_x(1840)
    keys = [(0, 67.0), (i_okawa_end, 67.0), (i_kiri_a, 82.0), (i_kiri_b, 82.0), (i_coast, 67.0), (n - 1, 67.0)]
    ks = np.array([k[0] for k in keys], dtype=np.float64)
    kv = np.array([k[1] for k in keys])
    return np.interp(s, ks, kv)


def quantize(level):
    """track block y and variant for a desired rail level (track-block units)"""
    r = level + RAIL_OFF
    k = int(round((r - RAIL_OFF_LOW) * 2))
    if k % 2 == 0:
        return k // 2, True        # low track at y=k/2
    return (k - 1) // 2, False     # full track at y=(k-1)/2


def runs(mask, min_len, merge_gap):
    out = []
    i, n = 0, len(mask)
    while i < n:
        if mask[i]:
            j = i
            while j < n and mask[j]:
                j += 1
            out.append([i, j])
            i = j
        else:
            i += 1
    merged = []
    for r in out:
        if merged and r[0] - merged[-1][1] < merge_gap:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    return [r for r in merged if r[1] - r[0] >= min_len]


TUNNEL_X = (1000, 1075)          # one deliberate tunnel between Okawa and Kirigaya
GORGE = [(1330, 330), (1430, 215), (1540, 95)]   # river gorge crossed by a viaduct


def terrain(w):
    pts = alignment()
    levels = profile(pts)
    LINE['pre'] = (pts, levels)
    # valley along the line (lowering only), except the deliberate tunnel
    sub, tg = [], []
    for i in range(0, len(pts), 12):
        x, z = pts[i]
        if TUNNEL_X[0] <= x <= TUNNEL_X[1]:
            if len(sub) > 1:
                w.flatten_path(sub, 7, 38, targets=tg, only_down=True)
            sub, tg = [], []
            continue
        sub.append((x, z))
        tg.append(levels[i] - 1.0)
    if len(sub) > 1:
        w.flatten_path(sub, 7, 38, targets=tg, only_down=True)
    # Kirigaya: a small misty valley with the station on its floor
    w.flatten_ellipse(1245, 98, 70, 34, 81, 40)
    # the gorge
    w.flatten_path(GORGE, 5, 34, targets=[59, 59, 59], only_down=True)


def regions(w):
    pts = alignment()
    for i in range(0, len(pts), 16):
        x, z = int(pts[i][0]), int(pts[i][1])
        w.ensure(x - 52, z - 52, x + 52, z + 52)
    for (x, z) in GORGE:
        w.ensure(x - 60, z - 60, x + 60, z + 60)
    # Akashi Kaikyo bridge strip
    w.ensure(2140, 520, 2200, 1460)
    # Kirigaya valley
    w.ensure(1160, 40, 1330, 160)


def gorge_river(w):
    from bcommon import resample
    pts = resample([(float(a), float(b)) for (a, b) in GORGE], 1.0)
    for (px, pz) in pts:
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                x, z = int(px) + dx, int(pz) + dz
                g = w.ground(x, z)
                if g <= 60:
                    w.fill(x, g + 1, z, x, 61, z, 'minecraft:water')
                    w.set(x, g, z, 'minecraft:gravel')


def build_line(w):
    pts = alignment()
    dirs = path_dirs(pts)
    levels = profile(pts)
    n = len(pts)
    q = [quantize(l) for l in levels]
    gs = np.array([w.ground(int(round(x)), int(round(z))) for (x, z) in pts])
    ys = np.array([a for a, _ in q])
    tunnel = runs((gs - ys) > 10, 16, 14)
    bridge = runs((ys - gs) > 7, 8, 10)
    in_tunnel = np.zeros(n, dtype=bool)
    in_bridge = np.zeros(n, dtype=bool)
    for a, b in tunnel:
        in_tunnel[a:b] = True
    for a, b in bridge:
        in_bridge[a:b] = True
    LINE.update(pts=pts, levels=levels, q=q, tunnels=tunnel, bridges=bridge)
    rnd = random.Random(7)
    for i, ((px, pz), (dx, dz)) in enumerate(zip(pts, dirs)):
        Y, low = q[i]
        nx, nz = -dz, dx
        f = dominant_facing(dx, dz)
        cells = {}
        for k in range(-24, 25):
            o = k * 0.5
            x = int(math.floor(px + nx * o + 0.5))
            z = int(math.floor(pz + nz * o + 0.5))
            a = abs(o)
            if (x, z) not in cells or cells[(x, z)] > a:
                cells[(x, z)] = a
        for (x, z), a in cells.items():
            g = w.ground(x, z)
            if in_tunnel[i]:
                if a <= 3.4:
                    w.clear(x, Y, z, x, Y + 6, z)
                    w.set(x, Y - 1, z, 'tabi:ballast')
                    w.set(x, Y + 7, z, 'minecraft:stone_bricks')
                elif a <= 4.6:
                    w.fill(x, Y - 1, z, x, Y + 7, z, 'minecraft:stone_bricks')
                    if i % 14 == 0 and a <= 4.1:
                        w.set(x, Y + 5, z, 'minecraft:sea_lantern')
                continue
            if in_bridge[i]:
                if a <= 2.6:
                    w.set(x, Y - 1, z, 'minecraft:smooth_stone')
                    w.set(x, Y - 2, z, 'minecraft:light_gray_concrete')
                    w.clear(x, Y, z, x, Y + 6, z)
                elif a <= 3.4:
                    w.set(x, Y - 1, z, 'minecraft:smooth_stone')
                    w.set(x, Y - 2, z, 'minecraft:light_gray_concrete')
                    w.set(x, Y, z, 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
                    w.set(x, Y + 1, z, 'tabi:railing')
                continue
            # ---- open line: embankment / cutting
            bed_top = Y - 1
            if a <= 3.4:
                if g < bed_top:
                    w.fill(x, g, z, x, bed_top - 1, z, 'minecraft:dirt' if g >= 62 else 'minecraft:stone')
                w.set(x, bed_top, z, 'tabi:ballast')
                w.clear(x, Y, z, x, max(Y + 6, g + 1), z)
            else:
                edge = a - 3.4
                if g < bed_top:  # embankment slope
                    top = bed_top - int(edge)
                    if top > g:
                        sea = g < 62
                        w.fill(x, g, z, x, top - 1, z, 'minecraft:stone' if sea else 'minecraft:dirt')
                        w.set(x, top, z, 'minecraft:stone_bricks' if sea else 'minecraft:grass_block')
                elif g > Y:   # cutting slope
                    lo = Y + int(edge)
                    if lo <= g:
                        w.clear(x, lo + 1, z, x, g + 1, z)
                        below = w.get(x, lo, z)
                        if 'water' not in below:
                            w.set(x, lo, z, 'minecraft:grass_block' if g < 120 else 'minecraft:stone')
        # track on top
        cx, cz = int(math.floor(px + 0.5)), int(math.floor(pz + 0.5))
        if low:
            w.set(cx, Y, cz, facing(TB('track_low'), f))
            w.set(cx, Y - 1, cz, 'tabi:ballast')
        else:
            w.set(cx, Y, cz, facing(TB('track'), f))
        # shoulders (ballast) next to the track
        for side in (-1, 1):
            for o in (1.0, 1.5):
                sx = int(math.floor(px + nx * o * side + 0.5))
                sz = int(math.floor(pz + nz * o * side + 0.5))
                if (sx, sz) == (cx, cz):
                    continue
                if not low and 'track' not in w.get(sx, Y, sz):
                    w.set(sx, Y, sz, slab('tabi:ballast_slab'))
    # bridge piers and tunnel portals
    for a, b in bridge:
        for i in range(a + 6, b - 3, 22):
            (px, pz), (dx, dz) = pts[i], dirs[i]
            Y, _ = q[i]
            nx, nz = -dz, dx
            for side in (-1.5, 1.5):
                x = int(math.floor(px + nx * side + 0.5))
                z = int(math.floor(pz + nz * side + 0.5))
                g = w.ground(x, z)
                for xx in (x, x + 1):
                    for zz in (z, z + 1):
                        w.fill(xx, min(g, 55), zz, xx, Y - 3, zz, 'minecraft:light_gray_concrete')
    for a, b in tunnel:
        for i in (a, b - 1):
            (px, pz), (dx, dz) = pts[i], dirs[i]
            Y, _ = q[i]
            nx, nz = -dz, dx
            for k in range(-14, 15):
                o = k * 0.5
                x = int(math.floor(px + nx * o + 0.5))
                z = int(math.floor(pz + nz * o + 0.5))
                if abs(o) <= 3.4:
                    w.fill(x, Y + 7, z, x, Y + 9, z, 'minecraft:smooth_stone')
                else:
                    g = w.ground(x, z)
                    w.fill(x, Y - 1, z, x, max(Y + 9, min(g, Y + 9)), z, 'minecraft:smooth_stone')
    return pts


# ===================================================================== stations
def platform(w, x1, x2, z_edge, side, y_top, depth=4, roof=(0, 0), name=None, sign_lines=None, sign_x=None):
    """side platform next to an E-W track. side = 'north' (platform north of track) / 'south'. y_top = walking surface block y."""
    sgn = -1 if side == 'north' else 1
    z_in = z_edge + sgn * (depth - 1)
    za, zb = min(z_edge, z_in), max(z_edge, z_in)
    w.fill(x1, y_top - 3, za, x2, y_top - 1, zb, 'minecraft:stone_bricks')
    w.fill(x1, y_top, za, x2, y_top, zb, 'tabi:platform')
    edge_f = 'south' if side == 'north' else 'north'   # yellow line toward the track
    w.fill(x1, y_top, z_edge, x2, y_top, z_edge, facing(TB('platform_edge'), edge_f))
    w.clear(x1, y_top + 1, za, x2, y_top + 5, zb)
    if roof[1] > roof[0]:
        rz1, rz2 = za, zb
        for x in range(roof[0], roof[1] + 1, 6):
            w.fill(x, y_top + 1, z_in - sgn, x, y_top + 4, z_in - sgn, 'minecraft:light_gray_concrete')
        w.fill(roof[0] - 1, y_top + 5, rz1, roof[1] + 1, y_top + 5, rz2, 'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
        for x in range(roof[0], roof[1] + 1, 4):
            w.set(x, y_top + 5, z_in - sgn, 'minecraft:sea_lantern')
    return za, zb


def ekimeihyo(w, xl, y, z, f, name, kana, en, prev='', nxt=''):
    """station board spanning 3 blocks starting at xl going to the viewer's right"""
    # viewer stands in front (on the `f` side) looking at the board; text runs to the viewer's right
    right = {'north': (-1, 0), 'south': (1, 0), 'east': (0, -1), 'west': (0, 1)}[f]
    for k in range(3):
        x, zz = xl + right[0] * k, z + right[1] * k
        if k == 0:
            w.sign(x, y, zz, 'tabi:station_sign', f, [name, kana + '   ' + en, ('← ' + prev if prev else '') + '            ' + (nxt + ' →' if nxt else '')],
                   style='station', span=3, back=True)
        else:
            w.set(x, y, zz, facing(TB('station_sign'), f))
    for k in (0, 2):
        x, zz = xl + right[0] * k, z + right[1] * k
        w.set(x, y - 1, zz, TB('sign_pole'))
        w.set(x, y - 2, zz, TB('sign_pole'))


def x_at(pts, x):
    for i, p in enumerate(pts):
        if p[0] >= x:
            return i
    return len(pts) - 1


def stations(w, pts):
    """build the four stations; returns station dicts for the route meta (s filled later)"""
    q = LINE['q']
    out = []
    # ---------------------------------------------------------------- 大川 (terminal, platform north of track at z=51)
    tz = 51
    y_top = 68
    platform(w, 603, 652, 49, 'north', y_top, depth=6, roof=(612, 640))
    # buffer stop
    w.fill(599, 67, 50, 599, 68, 52, 'minecraft:red_concrete')
    w.set(599, 69, 51, 'minecraft:redstone_lamp[lit=true]')
    ekimeihyo(w, 626, y_top + 3, 46, 'south', '大川', 'おおかわ', 'Okawa', '', '霧ヶ谷')
    ekimeihyo(w, 646, y_top + 3, 46, 'south', '大川', 'おおかわ', 'Okawa', '', '霧ヶ谷')
    for x in (618, 634, 648):
        bench(w, x, y_top + 1, 44, 'south', 'station_bench')
    w.fill(640, y_top + 1, 44, 643, y_top + 1, 44, 'minecraft:spruce_planks')
    w.set(641, y_top + 2, 44, TB('steamer'))
    w.npc(642, y_top + 1, 45, 'ekiben_okawa', 'okami', '駅弁売りのおばちゃん', 'station', 'stand', yaw=0)
    out.append({'id': 'okawa', 'name': '大川', 'kana': 'おおかわ', 'en': 'Okawa', 'x': 622, 'exit': [622.5, y_top + 1, 47.5]})
    LINE['okawa_platform'] = (603, 652, 44, 49, y_top)
    # ---------------------------------------------------------------- 霧ヶ谷 (intermediate, z=100)
    i0, i1 = x_at(pts, 1180), x_at(pts, 1320)
    Y, low = q[(i0 + i1) // 2]
    rail = Y + (RAIL_OFF_LOW if low else RAIL_OFF)
    y_top = int(math.floor(rail + 1.2)) - 1
    LINE['kiri_ytop'] = y_top
    platform(w, 1222, 1268, 98, 'north', y_top, depth=5, roof=(1238, 1252))
    ekimeihyo(w, 1236, y_top + 3, 96, 'south', '霧ヶ谷', 'きりがや', 'Kirigaya', '大川', '潮見')
    bench(w, 1245, y_top + 1, 95, 'south', 'station_bench')
    bench(w, 1247, y_top + 1, 95, 'south', 'station_bench')
    vending(w, 1230, y_top + 1, 94, 'south')
    w.set(1232, y_top + 1, 94, facing(TB('kenbaiki'), 'south'))
    w.set(1232, y_top + 2, 94, facing(TB('kenbaiki_top'), 'south'))
    w.sign(1234, y_top + 2, 94, 'tabi:info_board', 'south', ['無人駅です', '切符は券売機で', '買ってから乗ろう'], style='info')
    w.set(1234, y_top + 1, 94, TB('sign_pole'))
    out.append({'id': 'kirigaya', 'name': '霧ヶ谷', 'kana': 'きりがや', 'en': 'Kirigaya', 'x': 1245, 'exit': [1245.5, y_top + 1, 96.5]})
    LINE['kiri_platform'] = (1222, 1268, 94, 98, y_top)
    w.npc(1247, y_top + 1, 95, 'kirigaya_obaachan', 'obaachan', '霧ヶ谷のおばあさん', 'kirigaya', 'sit', yaw=0)
    # ---------------------------------------------------------------- 潮見 (intermediate on the coast)
    sz = LINE['shiomi_z']
    i0 = x_at(pts, 2100)
    Y, low = q[i0]
    rail = Y + (RAIL_OFF_LOW if low else RAIL_OFF)
    y_top = int(math.floor(rail + 1.2)) - 1
    LINE['shiomi_ytop'] = y_top
    za, zb = platform(w, 2076, 2124, sz - 2, 'north', y_top, depth=6, roof=(2088, 2112))
    ekimeihyo(w, 2094, y_top + 3, sz - 4, 'south', '潮見', 'しおみ', 'Shiomi', '霧ヶ谷', '神戸')
    bench(w, 2102, y_top + 1, sz - 6, 'south', 'station_bench')
    bench(w, 2104, y_top + 1, sz - 6, 'south', 'station_bench')
    w.set(2082, y_top + 1, sz - 7, facing(TB('kenbaiki'), 'south'))
    w.set(2082, y_top + 2, sz - 7, facing(TB('kenbaiki_top'), 'south'))
    w.set(2084, y_top + 1, sz - 7, facing(TB('kenbaiki'), 'south'))
    w.set(2084, y_top + 2, sz - 7, facing(TB('kenbaiki_top'), 'south'))
    vending(w, 2086, y_top + 1, sz - 7, 'south')
    w.npc(2092, y_top + 1, sz - 5, 'shiomi_ekiin', 'station_staff', '潮見駅の駅員さん', 'station', 'stand', yaw=180)
    w.fill(2108, y_top + 1, sz - 7, 2111, y_top + 1, sz - 7, 'minecraft:spruce_planks')
    w.npc(2110, y_top + 1, sz - 6, 'ekiben_shiomi', 'fisher', '駅弁売りのおじさん', 'station', 'stand', yaw=0)
    w.sign(2116, y_top + 2, sz - 7, 'tabi:info_board', 'south', ['明石海峡大橋', '全長3911m（1998年開通）', '東の空を見てごらん'], style='info')
    w.set(2116, y_top + 1, sz - 7, TB('sign_pole'))
    shiomi_town(w, sz, y_top)
    out.append({'id': 'shiomi', 'name': '潮見', 'kana': 'しおみ', 'en': 'Shiomi', 'x': 2100, 'exit': [2100.5, y_top + 1, sz - 3.5]})
    LINE['shiomi_platform'] = (2076, 2124, sz - 7, sz - 2, y_top)
    # ---------------------------------------------------------------- 神戸 (terminal) - platform south of the track (z=645), station building south
    y_top = 68
    platform(w, 3400, 3466, 647, 'south', y_top, depth=7, roof=(3404, 3460))
    w.fill(3471, 67, 644, 3471, 68, 646, 'minecraft:red_concrete')
    w.set(3471, 69, 645, 'minecraft:redstone_lamp[lit=true]')
    ekimeihyo(w, 3420, y_top + 3, 651, 'north', '神戸', 'こうべ', 'Kōbe', '潮見', '')
    ekimeihyo(w, 3444, y_top + 3, 651, 'north', '神戸', 'こうべ', 'Kōbe', '潮見', '')
    for x in (3412, 3430, 3452):
        bench(w, x, y_top + 1, 652, 'north', 'station_bench')
    w.fill(3436, y_top + 1, 653, 3440, y_top + 1, 653, 'minecraft:spruce_planks')
    w.npc(3438, y_top + 1, 652, 'ekiben_kobe', 'shop_staff', '駅弁屋さん', 'station', 'stand', yaw=180)
    out.append({'id': 'kobe', 'name': '神戸', 'kana': 'こうべ', 'en': 'Kobe', 'x': 3448, 'exit': [3448.5, y_top + 1, 649.5]})
    LINE['kobe_platform'] = (3400, 3466, 647, 653, y_top)
    return out


def shiomi_town(w, sz, y_top):
    """small seaside street behind Shiomi station: 明石焼き shop, steps down to the platform, a fishing jetty"""
    from bcommon import gable_roof
    g0 = y_top
    # station square north of the platform
    for x in range(2060, 2126):
        for z in range(sz - 22, sz - 7):
            g = w.ground(x, z)
            if g < g0:
                w.fill(x, g, z, x, g0 - 1, z, 'minecraft:stone')
            w.set(x, g0, z, 'tabi:sidewalk' if z > sz - 12 else 'minecraft:smooth_stone')
            w.clear(x, g0 + 1, z, x, g0 + 12, z)
    # 明石焼き 潮見屋
    x1, x2, z1, z2 = 2062, 2074, sz - 21, sz - 13
    w.fill(x1, g0 + 1, z1, x2, g0 + 5, z2, 'tabi:shikkui')
    w.fill(x1, g0 + 1, z1, x2, g0 + 2, z2, 'tabi:yakisugi')
    w.fill(x1 + 1, g0 + 1, z1 + 1, x2 - 1, g0 + 4, z2 - 1, AIR)
    w.fill(x1 + 1, g0, z1 + 1, x2 - 1, g0, z2 - 1, 'tabi:hinoki')
    w.fill(x1 + 3, g0 + 1, z2, x2 - 3, g0 + 3, z2, AIR)
    for x in range(x1 + 3, x2 - 2, 2):
        w.set(x, g0 + 3, z2, facing(TB('noren_red'), 'south'))
    w.fill(x1 + 2, g0 + 1, z1 + 3, x2 - 2, g0 + 1, z1 + 3, 'minecraft:spruce_planks')
    w.set(x1 + 3, g0 + 1, z1 + 1, 'minecraft:smoker[facing=south,lit=true]')
    w.set(x1 + 5, g0 + 2, z1 + 3, TB('steamer'))
    gable_roof(w, x1, z1, x2, z2, g0 + 6, 'x', 'tabi:kawara', overhang=1, gable_wall='tabi:shikkui')
    w.sign(x1 + 3, g0 + 4, z2 + 1, 'tabi:shop_sign', 'south', ['明石焼き 潮見屋', 'だしにつけて'], style='shop', span=7)
    for k in range(1, 7):
        w.set(x1 + 3 + k, g0 + 4, z2 + 1, facing(TB('shop_sign'), 'south'))
    w.npc(x1 + 6, g0 + 1, z1 + 2, 'shiomi_akashiyaki', 'okami', '潮見屋のおかみさん', '', 'stand', yaw=0)
    # steps from the square onto the platform
    w.fill(2096, g0, sz - 8, 2104, g0, sz - 7, 'tabi:platform')
    # fishing jetty south of the line
    jx = 2140
    for z in range(sz + 3, sz + 40):
        g = w.ground(jx, z)
        for x in range(jx - 1, jx + 2):
            w.fill(x, min(g, 55), z, x, 64, z, 'minecraft:stone_bricks')
            w.set(x, 65, z, 'minecraft:smooth_stone')
            w.clear(x, 66, z, x, 70, z)
        w.set(jx + 2, 66, z, 'minecraft:spruce_fence')
    w.fill(jx - 1, 66, sz + 6, jx + 1, 66, sz + 6, 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    w.npc(jx, 66, sz + 36, 'shiomi_fisher', 'fisher', '漁師のおじさん', 'sea', 'stand', yaw=0, hold='minecraft:fishing_rod')
    w.area('shiomi', '潮見', '海峡を望む駅', (2040, 50, sz - 40, 2200, 140, sz + 60), 'spot.shiomi')
    w.area('akashi_bridge', '明石海峡大橋', '本州と淡路島をむすぶ橋', (2150, 50, sz + 10, 2190, 260, 1460), 'spot.akashi_bridge')
    w.area('kirigaya', '霧ヶ谷', '山あいの無人駅', (1180, 60, 60, 1320, 140, 140), 'spot.kirigaya')


# ===================================================================== Akashi Kaikyo Bridge (scaled)
def akashi_bridge(w):
    cx = 2170
    z_land, z_awaji = None, None
    for z in range(600, 900):
        if T.inland(np.array([cx]), np.array([z]))[0] < 0:
            z_land = z
            break
    for z in range(1500, 900, -1):
        if T.inland(np.array([cx]), np.array([z]))[0] < 0:
            z_awaji = z
            break
    deck = 110
    t1, t2 = z_land + 110, z_awaji - 120
    top = 182
    half = 7
    LINE['bridge'] = dict(cx=cx, z_land=z_land, z_awaji=z_awaji, t1=t1, t2=t2, deck=deck)
    tower_col = 'minecraft:light_gray_concrete'
    steel = 'minecraft:smooth_stone'
    # ---- towers
    for tz in (t1, t2):
        for sx in (-1, 1):
            x0 = cx + sx * (half + 1)
            for xx in range(x0 - 1, x0 + 2):
                for zz in range(tz - 2, tz + 3):
                    g = w.ground(xx, zz)
                    w.fill(xx, min(g, 40), zz, xx, top, zz, tower_col)
            w.fill(x0 - 2, 40, tz - 4, x0 + 2, 64, tz + 4, 'minecraft:stone_bricks')   # caisson foundation
        for yy in (deck - 6, 140, 162, top):
            w.fill(cx - half, yy - 1, tz - 1, cx + half, yy, tz + 1, tower_col)
        # X bracing between struts
        for (ya, yb) in ((deck - 6, 140), (140, 162), (162, top)):
            hgt = yb - ya
            for k in range(hgt):
                t = k / hgt
                xa = int(round(cx - half + 1 + t * (2 * half - 2)))
                xb = int(round(cx + half - 1 - t * (2 * half - 2)))
                w.set(xa, ya + k, tz, tower_col)
                w.set(xb, ya + k, tz, tower_col)
        w.set(cx - half - 1, top + 1, tz, 'minecraft:redstone_lamp[lit=true]')
        w.set(cx + half + 1, top + 1, tz, 'minecraft:redstone_lamp[lit=true]')
    # ---- deck (road + truss) from the hills on the mainland to Awaji
    z_start, z_end = z_land - 140, z_awaji + 70
    for z in range(z_start, z_end + 1):
        for x in range(cx - half, cx + half + 1):
            w.set(x, deck, z, 'tabi:asphalt')
            w.clear(x, deck - 8, z, x, deck - 1, z)
            w.clear(x, deck + 1, z, x, deck + 4, z)
        w.set(cx - half, deck + 1, z, 'tabi:railing')
        w.set(cx + half, deck + 1, z, 'tabi:railing')
        w.set(cx, deck, z, facing(TB('road_center_yellow'), 'north'))
        if z % 40 == 0:
            for x in (cx - half + 1, cx + half - 1):
                w.set(x, deck + 1, z, TB('lamp_pole'))
                w.set(x, deck + 2, z, TB('lamp_pole'))
                w.set(x, deck + 3, z, facing(TB('street_lamp'), 'east' if x < cx else 'west'))
        # Warren truss on both sides under the deck
        k = z % 12
        diag = k if k <= 6 else 12 - k
        for x in (cx - half, cx + half):
            w.set(x, deck - 1, z, steel)
            w.set(x, deck - 8, z, steel)
            w.set(x, deck - 8 + diag, z, steel)
        if k == 0:
            for x in range(cx - half, cx + half + 1):
                w.set(x, deck - 8, z, steel)
                w.set(x, deck - 1, z, steel)
    # approach piers on land / shallow water (keep clear of the railway)
    rail_z = LINE.get('shiomi_z', 0)
    piers = [z for z in list(range(z_start + 10, t1 - 30, 34)) + list(range(t2 + 40, z_end - 5, 34)) if abs(z - rail_z) > 7]
    for z in piers:
        for sx in (-1, 1):
            x = cx + sx * (half - 2)
            g = w.ground(x, z)
            w.fill(x - 1, min(g, 50), z - 1, x + 1, deck - 9, z + 1, tower_col)
    # mainland end: earthwork ramp into the hillside
    for z in range(z_start - 40, z_start):
        for x in range(cx - half, cx + half + 1):
            g = w.ground(x, z)
            if g < deck:
                w.fill(x, g, z, x, deck - 1, z, 'minecraft:stone')
            w.set(x, deck, z, 'tabi:asphalt')
            w.clear(x, deck + 1, z, x, deck + 8, z)
    # ---- anchorages
    for az in (z_land - 6, z_awaji + 8):
        w.fill(cx - half - 4, 50, az - 8, cx + half + 4, deck + 6, az + 8, 'minecraft:light_gray_concrete')
        w.fill(cx - half - 3, deck + 7, az - 7, cx + half + 3, deck + 7, az + 7, 'minecraft:smooth_stone')
    # ---- main cables + suspenders + lights
    def cable_y(z):
        if z <= t1:
            za, ya, zb, yb = z_land - 6, deck + 6, t1, top
            t = (z - za) / (zb - za)
            return ya + (yb - ya) * t - 18 * 4 * t * (1 - t)
        if z >= t2:
            za, ya, zb, yb = t2, top, z_awaji + 8, deck + 6
            t = (z - za) / (zb - za)
            return ya + (yb - ya) * t - 18 * 4 * t * (1 - t)
        t = (z - t1) / (t2 - t1)
        sag = top - (deck + 12)
        return top - sag * 4 * t * (1 - t)
    for sx in (-1, 1):
        x = cx + sx * (half + 1)
        prev = None
        for z in range(z_land - 6, z_awaji + 9):
            y = int(round(cable_y(z)))
            ys = [y] if prev is None else range(min(prev, y), max(prev, y) + 1)
            for yy in ys:
                w.set(x, yy, z, 'minecraft:light_gray_concrete')
            prev = y
            if z % 6 == 0:
                w.set(x, y + 1, z, 'minecraft:sea_lantern')
            if z % 8 == 0 and y > deck + 2:
                for yy in range(deck + 1, y):
                    w.set(x, yy, z, 'minecraft:chain[axis=y,waterlogged=false]')
    return LINE['bridge']


# ===================================================================== rolling stock (voxel designs)
def car_dmu():
    """Kawagiri line diesel railcar (cabs at both ends), modelled at half-block resolution (scale 0.5).
    Lower body 3 blocks wide; above floor level the body widens to 4 blocks (overhanging the platform edge like real cars),
    giving a 3-block-wide saloon with box seats facing each other beside big windows."""
    pal = []

    def P(s):
        if s not in pal:
            pal.append(s)
        return pal.index(s)
    vox = {}

    def put(x, y, z, st, flag=0):
        vox[(x, round(y, 2), z)] = (P(st), flag)
    CREAM, GREEN, ROOF = 'minecraft:smooth_sandstone', 'minecraft:green_concrete', 'minecraft:light_gray_concrete'
    GLASS, DOOR, DARK = 'tabi:clear_glass', 'minecraft:sandstone', 'minecraft:gray_concrete'
    BLACK, FLOOR, SEAT = 'minecraft:black_concrete', 'minecraft:spruce_planks', 'minecraft:blue_wool'
    LIGHT, HEAD, TAIL = 'minecraft:sea_lantern', 'minecraft:pearlescent_froglight', 'minecraft:red_concrete'
    RACK = 'minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]'
    STRAP = 'minecraft:chain[axis=y,waterlogged=false]'
    L = 40
    Z0, Z1 = -20, 19                    # end walls (cab fronts)
    XL, XR = -4, 3                      # upper side walls (body 8 units = 4 blocks)
    FY = 1.6                            # floor block (top at 2.6 units = 1.3 blocks above rail)
    rows = [2.6 + k for k in range(6)]  # wall rows up to 8.6
    RY = 8.6                            # roof
    doors = (-14, -13, 12, 13)
    # ---------------- running gear and underfloor
    for zc in (-13, 12):
        for z in range(zc - 3, zc + 4):
            for x in (-3, 2):
                put(x, 0.0, z, BLACK if z in (zc - 2, zc - 1, zc + 1, zc + 2) else DARK)
            for x in (-2, -1, 0, 1):
                put(x, 0.6, z, DARK)
    for z in range(-9, 9):
        for x in (-2, -1, 0, 1):
            put(x, 0.6, z, BLACK if -5 <= z <= 2 else DARK)
    # ---------------- floor
    for z in range(Z0, Z1 + 1):
        for x in range(-3, 3):
            put(x, FY, z, FLOOR if Z0 < z < Z1 else DARK)
    # ---------------- side walls
    for z in range(Z0 + 1, Z1):
        for x in (XL, XR):
            for k, y in enumerate(rows):
                if z in doors:
                    put(x, y, z, GLASS if k in (2, 3) else DOOR, 1)
                    continue
                if k == 0:
                    st = GREEN
                elif k == 1:
                    st = CREAM
                elif k in (2, 3, 4):
                    if -10 <= z <= 9:          # saloon: one wide window per seat bay, pillars behind the backrests
                        pillar = (z - (-10)) % 5 in (0, 4)
                    else:
                        pillar = (z % 3 == 0) or z in (-17, -16, 15, 16) or abs(z - (-13.5)) < 2 or abs(z - 12.5) < 2
                    st = CREAM if pillar else GLASS
                else:
                    st = GREEN
                put(x, y, z, st)
    # ---------------- end walls (cab fronts)
    for z in (Z0, Z1):
        for x in range(XL, XR + 1):
            for k, y in enumerate(rows):
                st = CREAM
                if k == 0:
                    st = GREEN
                if k in (3, 4) and x in (-3, -2, 1, 2):
                    st = GLASS
                if k in (2, 3, 4) and x in (-1, 0):
                    st = DARK if k == 2 else GLASS        # gangway door
                if k == 5 and x in (-1, 0):
                    st = HEAD
                if k == 5 and x in (-3, -2):
                    st = 'tabi:train_lcd[facing=' + ('north' if z == Z0 else 'south') + ']'
                if k == 1 and x in (-3, 2):
                    st = TAIL
                put(x, y, z, st)
        for x in range(-3, 3):
            put(x, 0.6, z, BLACK)                          # skirt
    # ---------------- roof (+ ventilators, lights inside)
    for z in range(Z0, Z1 + 1):
        for x in range(XL, XR + 1):
            put(x, RY, z, ROOF)
        if Z0 + 2 < z < Z1 - 2 and z % 4 == 1:
            put(-1, RY, z, LIGHT)
            put(0, RY, z, LIGHT)
        if z % 6 == 0 and Z0 + 3 < z < Z1 - 3:
            put(-1, RY + 1, z, ROOF)
            put(0, RY + 1, z, ROOF)
    put(-1, RY + 1, Z1 - 2, BLACK)                          # exhaust
    # ---------------- cab partitions with doorway
    for z in (-17, 16):
        for x in range(-3, 3):
            for k, y in enumerate(rows):
                if x in (-1, 0) and k < 4:
                    continue
                put(x, y, z, CREAM if k not in (2, 3) or x in (-1, 0) else GLASS)
    for (z, f) in ((Z0 + 1, 'south'), (Z1 - 1, 'north')):
        put(-3, 2.6, z, DARK)                              # driver's console
        put(-2, 2.6, z, DARK)
        put(-3, 3.6, z, 'minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]')
    # ---------------- box seats (4 bays) + long seats near the ends
    seats = []
    for z0 in (-10, -5, 0, 5):
        for xs in ((-3, -2), (1, 2)):
            side = -1 if xs[0] < 0 else 1
            for x in xs:
                for y in (2.6, 3.6, 4.6):
                    put(x, y, z0, SEAT)          # backrest
                    put(x, y, z0 + 4, SEAT)
                put(x, 2.6, z0 + 1, SEAT)        # cushions
                put(x, 2.6, z0 + 3, SEAT)
                seats.append([(x + 0.5) * 0.5, 1.55, (z0 + 1.5) * 0.5, side])
                seats.append([(x + 0.5) * 0.5, 1.55, (z0 + 3.5) * 0.5, side])
            # luggage racks above the windows
            for z in range(z0, z0 + 5):
                for x in xs:
                    put(x, 7.6, z, RACK)
    for z in (-16, -15, 14, 15):
        for x in (-3, 2):
            put(x, 2.6, z, SEAT)
            seats.append([(x + 0.5) * 0.5, 1.55, (z + 0.5) * 0.5, -1 if x < 0 else 1])
    # standing area: straps, displays, fans
    for z in (-12, -11, 10, 11, -15, 14):
        for x in (-1, 0):
            put(x, 7.6, z, STRAP)
    put(-3, 7.6, -13, 'tabi:train_lcd[facing=east]')
    put(2, 7.6, 12, 'tabi:train_lcd[facing=west]')
    for z in (-7, 3):
        put(-0.5, 7.6, z, 'tabi:ceiling_fan')
    blocks = [[x, y, z, p, f] for (x, y, z), (p, f) in vox.items()]
    return {'id': 'dmu', 'size': [4.0, 5.0, 20.0], 'scale': 0.5, 'palette': pal, 'blocks': blocks, 'seats': seats}


def car_bus(cid, body, stripe, roofc='minecraft:white_concrete'):
    pal = []
    def P(s):
        if s not in pal:
            pal.append(s)
        return pal.index(s)
    blocks = []
    W, L = 4, 17
    b, st, rf, glass, black, light = P(body), P(stripe), P(roofc), P('minecraft:glass'), P('minecraft:black_concrete'), P('minecraft:sea_lantern')
    seat, fl = P('minecraft:red_wool'), P('minecraft:gray_concrete')
    head = P('minecraft:pearlescent_froglight')
    for z in range(L):
        zc = z - L / 2
        end = z in (0, L - 1)
        for x in range(W):
            xc = x - W / 2
            side = x in (0, W - 1)
            if z in (2, 3, L - 4, L - 3) and side:
                blocks.append([xc, 0, zc, black, 0])
            blocks.append([xc, 1, zc, fl, 0])
            blocks.append([xc, 5, zc, rf if not (z % 5 == 2 and x in (1, 2)) else light, 0])
            if side or end:
                door = side and x == W - 1 and z in (L - 3, L - 4, 6, 7)   # doors on the left (kerb) side
                for y in range(1, 5):
                    if end:
                        if z == L - 1 and y in (2, 3, 4) and x in (1, 2):
                            mat = glass
                        elif z == L - 1 and y in (3, 4):
                            mat = glass
                        elif y == 1 and x in (0, 3):
                            mat = head if z == L - 1 else P('minecraft:red_concrete')
                        elif y == 4 and z == 0:
                            mat = glass
                        else:
                            mat = b
                        blocks.append([xc, y, zc, mat, 0])
                        continue
                    if door:
                        blocks.append([xc, y, zc, glass if y >= 2 else b, 1])
                        continue
                    if y == 1:
                        mat = st
                    elif y in (3, 4):
                        mat = glass
                    else:
                        mat = b
                    blocks.append([xc, y, zc, mat, 0])
    seats = []
    for z in range(4, L - 5, 2):
        for x in (1, 2):
            blocks.append([x - W / 2, 2, z - L / 2, seat, 0])
    for zc in (-3.5, -1.5, 0.5, 2.5):
        for xs in (-0.5, 0.5):
            seats.append([round(xs * 0.6, 3), 1.55, round(zc * 0.6, 3), 0])
    return {'id': cid, 'size': [2.4, 3.6, 10.2], 'scale': 0.6, 'palette': pal, 'blocks': blocks, 'seats': seats}


def route_meta(rid, name, typ, design, cars, spacing, vmax, fare_base, fare100, color, toward_end, toward_start, pts3, stations):
    """pts3: list of (x, y, z) dense-ish points; stations: dicts with id,name,kana,en,x or s,exit"""
    P = np.array(pts3, dtype=np.float64)
    seg = np.sqrt(((P[1:] - P[:-1]) ** 2).sum(axis=1))
    cum = np.concatenate(([0.0], np.cumsum(seg)))
    out_st = []
    for st in stations:
        if 's' in st:
            s = st['s']
        else:
            i = int(np.argmin(np.abs(P[:, 0] - st['x']) + (np.abs(P[:, 2] - st['z']) if 'z' in st else 0)))
            s = float(cum[i])
        out_st.append({'id': st['id'], 'name': st['name'], 'kana': st['kana'], 'en': st['en'], 's': round(float(s), 2),
                       'exit': [round(v, 2) for v in st['exit']]})
    out_st.sort(key=lambda d: d['s'])
    return {'id': rid, 'name': name, 'type': typ, 'design': design, 'cars': cars, 'car_spacing': spacing, 'max_speed': vmax,
            'fare_base': fare_base, 'fare_per_100': fare100, 'color': color, 'toward_end': toward_end, 'toward_start': toward_start,
            'points': [[round(p[0], 2), round(p[1], 3), round(p[2], 2)] for p in pts3], 'stations': out_st}, float(cum[-1])


def build(w):
    pts = build_line(w)
    sts = stations(w, pts)
    akashi_bridge(w)
    # route points: every 3rd dense point with the smooth rail level
    lv = LINE['levels']
    q = LINE['q']
    pts3 = []
    for i in range(0, len(pts), 3):
        Y, low = q[i]
        pts3.append((pts[i][0] + 0.5, Y + (RAIL_OFF_LOW if low else RAIL_OFF), pts[i][1] + 0.5))
    if (len(pts) - 1) % 3:
        Y, low = q[-1]
        pts3.append((pts[-1][0] + 0.5, Y + (RAIL_OFF_LOW if low else RAIL_OFF), pts[-1][1] + 0.5))
    # rail level per point should be smooth: use the float profile instead of the quantised one
    pts3 = [(x, float(lv[min(len(lv) - 1, i * 3)]) + RAIL_OFF, z) for i, (x, _, z) in enumerate(pts3)]
    meta, L = route_meta('kawagiri', '川霧線', 'train', 'dmu', 2, 20.5, 1.0, 140, 20, '#2FA05A', '神戸方面', '大川方面', pts3, [])
    # station s values: terminals placed so the whole train stands on the platform
    st_meta = []
    P = np.array(pts3)
    seg = np.sqrt(((P[1:] - P[:-1]) ** 2).sum(axis=1))
    cum = np.concatenate(([0.0], np.cumsum(seg)))
    extra = {
        'okawa': {'melody': 'melody_okawa', 'door': 'L', 'transfer': 'ほなみ方面のバスは、駅前広場からお乗りください。'},
        'kirigaya': {'melody': 'melody_kirigaya', 'door': 'L', 'transfer': '霧ヶ谷は無人駅です。切符はホームの券売機でお求めください。'},
        'shiomi': {'melody': 'melody_shiomi', 'door': 'L', 'transfer': '駅前には名物の明石焼きのお店があります。'},
        'kobe': {'melody': 'melody_kobe', 'door': 'R', 'transfer': '港めぐりバスは、駅前の停留所からお乗りください。'},
    }
    for st in sts:
        i = int(np.argmin(np.abs(P[:, 0] - st['x'])))
        s = float(cum[i])
        if st['id'] == 'okawa':
            s = 22.0
        if st['id'] == 'kobe':
            s = float(cum[-1]) - 22.0
        o = {'id': st['id'], 'name': st['name'], 'kana': st['kana'], 'en': st['en'], 's': round(s, 2), 'exit': st['exit']}
        o.update(extra.get(st['id'], {}))
        st_meta.append(o)
    meta['stations'] = st_meta

    def s_at_x(x):
        i = int(np.argmax(P[:, 0] >= x))
        return round(float(cum[i]), 1)
    b0, b1 = LINE['bridges'][0] if LINE['bridges'] else (0, 0)
    bridge_x = pts[(b0 + b1) // 2][0] if b1 else 1440
    meta['events'] = [
        {'s': s_at_x(760), 'type': 'guide', 'text': 'この先、列車は山あいの霧ヶ谷へと上っていきます。右手の山なみもお楽しみください。'},
        {'s': s_at_x(bridge_x - 40), 'type': 'horn'},
        {'s': s_at_x(bridge_x), 'type': 'guide', 'text': 'ただいま霧川橋梁を渡っています。眼下を流れるのは霧川。朝夕には川霧が立ちのぼります。'},
        {'s': s_at_x(1790), 'type': 'guide', 'text': '右手前方に、瀬戸内海が見えてきました。'},
        {'s': s_at_x(1985), 'type': 'guide', 'text': '右手前方に明石海峡大橋が見えてきました。全長3911m、1998年に開通したつり橋です。',
         'journal': 'spot.akashi_bridge'},
        {'s': s_at_x(2260), 'type': 'guide', 'text': '右手に見える大きな島は淡路島です。海峡には大きな船が行き交います。'},
        {'s': s_at_x(2700), 'type': 'guide', 'text': '右手は瀬戸内海。夕方には、海に沈む夕日が見どころです。'},
        {'s': s_at_x(2900), 'type': 'horn'},
        {'s': s_at_x(3180), 'type': 'guide', 'text': '右手前方に神戸港、赤いポートタワーが見えてきます。まもなく終点の神戸です。'},
    ]
    w.meta['routes'].append(meta)
    w.meta['cars'] += [car_dmu(), car_bus('bus_honami', 'minecraft:white_concrete', 'minecraft:blue_concrete'),
                       car_bus('bus_loop', 'minecraft:cyan_terracotta', 'minecraft:yellow_concrete', 'minecraft:white_concrete')]
    LINE['length'] = float(cum[-1])
    return LINE
