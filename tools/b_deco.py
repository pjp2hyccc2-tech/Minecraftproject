"""Decorative detail pass (runs after every other builder).

Street furniture (power lines, curve mirrors, stop signs, traffic signals, crosswalks, manholes), shrine fittings,
summer things at the farmhouse, a namako-wall storehouse (kura), station fittings (clocks, LED departure boards,
kiosks, posters, railway signals), a seaside level crossing at Shiomi, tetrapods, and harbour details in Kobe
(bollards, life rings, palms, aviation lights, Kitano iron fences and planters).
Things are placed only into free cells (or replace a known block), so the builders' work is never broken."""
import math
import random

import numpy as np

import b_kobe
import b_town
import b_village
from bcommon import TB, AIR, gable_roof, fix_connections
from wb import facing, axis, slab, stairs, YMIN

VG, TG, KG = b_village.G, b_town.G, b_kobe.G
ROAD = ('tabi:asphalt', 'tabi:road_line', 'tabi:road_center', 'tabi:road_center_yellow')
STATS = {}


def _count(k):
    STATS[k] = STATS.get(k, 0) + 1


def base(st):
    return st.split('[')[0]


def is_road(st):
    return base(st) in ROAD


def put(w, x, y, z, st, need_air=True):
    if need_air and not w.is_air(x, y, z):
        return False
    w.set(x, y, z, st)
    _count(base(st))
    return True


def free_col(w, x, z, y0, h):
    return all(w.is_air(x, y, z) for y in range(y0, y0 + h))


def ring_spots(x, z, r):
    out = [(x, z)]
    for k in range(1, r + 1):
        for dx in range(-k, k + 1):
            for dz in range(-k, k + 1):
                if max(abs(dx), abs(dz)) == k:
                    out.append((x + dx, z + dz))
    return out


def pole_with(w, x, z, y0, height, top, pole=TB('lamp_pole'), search=2, top2=None):
    """`height` pole blocks from y0 with `top` (and optionally `top2` above it); finds a free, supported column near (x, z)"""
    need = height + 1 + (1 if top2 else 0)
    for (px, pz) in ring_spots(x, z, search):
        if free_col(w, px, pz, y0, need) and not w.is_air(px, y0 - 1, pz):
            for y in range(y0, y0 + height):
                w.set(px, y, pz, pole)
            put(w, px, y0 + height, pz, top, need_air=False)
            if top2:
                put(w, px, y0 + height + 1, pz, top2, need_air=False)
            return px, pz
    return None


def sign_on_pole(w, x, z, y0, block, f, lines, style, search=2, pole=TB('sign_pole'), height=1, **kw):
    for (px, pz) in ring_spots(x, z, search):
        if free_col(w, px, pz, y0, height + 1) and not w.is_air(px, y0 - 1, pz):
            for y in range(y0, y0 + height):
                w.set(px, y, pz, pole)
            w.sign(px, y0 + height, pz, block, f, lines, style=style, **kw)
            _count(block)
            return px, pz
    return None


def ridge_onigawara(w, x1, z1, x2, z2, ridge_axis):
    """鬼瓦 on both ends of the ridge of the roof over x1..x2 / z1..z2"""
    if ridge_axis == 'x':
        c = (z1 + z2) // 2
        tops = {x: w.top(x, c) for x in range(x1 - 3, x2 + 4)}
    else:
        c = (x1 + x2) // 2
        tops = {z: w.top(c, z) for z in range(z1 - 3, z2 + 4)}
    ymax = max(tops.values())
    ridge = sorted(k for k, v in tops.items() if v == ymax)
    a, b = ridge[0], ridge[-1]
    if ridge_axis == 'x':
        put(w, a, ymax + 1, c, facing(TB('onigawara'), 'west'))
        put(w, b, ymax + 1, c, facing(TB('onigawara'), 'east'))
    else:
        put(w, c, ymax + 1, a, facing(TB('onigawara'), 'north'))
        put(w, c, ymax + 1, b, facing(TB('onigawara'), 'south'))


def power_lines(w, xs, z, y):
    """電線: three wires between consecutive utility poles along an E-W road, sagging in the middle"""
    for xa, xb in zip(xs, xs[1:]):
        span = xb - xa
        sag = 1 if span <= 34 else 2
        for x in range(xa + 1, xb):
            t = (x - xa) / span
            yy = y - int(round(sag * math.sin(math.pi * t)))
            put(w, x, yy, z, facing(TB('power_line'), 'east'))


# ===================================================================== roads: manholes, gutters, crosswalks, signals
def road_cells(w, y, along, c, a, span):
    """cells across a straight road at position `a` along it: list of (x, z) whose block at y is road"""
    out = []
    for o in range(-span, span + 1):
        x, z = (a, c + o) if along == 'x' else (c + o, a)
        if is_road(w.get(x, y, z)):
            out.append((x, z))
    return out


def road_details(w, y, along, c, a0, a1, span=8, manhole_every=36, grate_every=9, phase=0):
    """manhole covers in the lane and gutter grates at the outer edge of a straight road"""
    for a in range(a0, a1 + 1):
        cells = road_cells(w, y, along, c, a, span)
        if len(cells) < 4:
            continue
        if (a + phase) % manhole_every == 0:
            # inside the lane: 2 cells off the centre
            mid = cells[len(cells) // 2 + 2] if len(cells) > 6 else cells[len(cells) // 2]
            if base(w.get(mid[0], y, mid[1])) == 'tabi:asphalt':
                put(w, mid[0], y, mid[1], TB('manhole'), need_air=False)
        if (a + phase) % grate_every == 0:
            for (x, z) in (cells[0], cells[-1]):
                if base(w.get(x, y, z)) == 'tabi:asphalt':
                    put(w, x, y, z, TB('gutter_grate'), need_air=False)


def zebra(w, y, along, c, a_from, a_to, span=8):
    """横断歩道 across a road that runs along `along` (stripes parallel to the traffic)"""
    for a in range(a_from, a_to + 1):
        for (x, z) in road_cells(w, y, along, c, a, span):
            o = (z - c) if along == 'x' else (x - c)
            if o % 2 == 0:
                put(w, x, y, z, 'minecraft:white_concrete', need_air=False)


def junction(w, y, cx, cz, ew_half, ns_half, arms=('w', 'e', 'n', 's'), signals=True, peds=True):
    """signalised junction of an E-W road (half width incl. sidewalk ew_half) and a N-S road (ns_half).
    Japan drives on the left: each approach is controlled by a head on the far-left corner."""
    ax, az = ns_half + 1, ew_half + 1
    # crosswalks just outside the junction box
    if 'w' in arms:
        zebra(w, y, 'x', cz, cx - ax - 3, cx - ax - 1)
    if 'e' in arms:
        zebra(w, y, 'x', cz, cx + ax + 1, cx + ax + 3)
    if 'n' in arms:
        zebra(w, y, 'z', cx, cz - az - 3, cz - az - 1)
    if 's' in arms:
        zebra(w, y, 'z', cx, cz + az + 1, cz + az + 3)
    if not signals:
        return
    heads = []
    if 'w' in arms:   # eastbound traffic comes from the west -> NE corner, facing west
        heads.append((cx + ax, cz - az, 'west'))
    if 'e' in arms:   # westbound -> SW corner, facing east
        heads.append((cx - ax, cz + az, 'east'))
    if 'n' in arms:   # southbound -> SE corner, facing north
        heads.append((cx + ax, cz + az, 'north'))
    if 's' in arms:   # northbound -> NW corner, facing south
        heads.append((cx - ax, cz - az, 'south'))
    for (x, z, f) in heads:
        pole_with(w, x, z, y + 1, 4, facing(TB('traffic_light'), f))
    if peds:
        for (x, z, f) in ((cx - ax, cz - az + 1, 'east'), (cx + ax, cz + az - 1, 'west')):
            pole_with(w, x, z, y + 1, 2, facing(TB('ped_signal'), f), search=1)


# ===================================================================== ほなみ村
def kura(w, x1, z1, x2, z2, G):
    """土蔵: namako walls below, white plaster above, heavy plastered doors (amado blocks), tiled gable roof"""
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            if not free_col(w, x, z, G + 1, 8):
                return False
    w.fill(x1, G - 1, z1, x2, G, z2, 'minecraft:stone_bricks')
    w.fill(x1, G + 1, z1, x2, G + 2, z2, TB('namako_wall'))
    w.fill(x1, G + 3, z1, x2, G + 6, z2, TB('shikkui'))
    w.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 5, z2 - 1, AIR)
    w.fill(x1 + 1, G, z1 + 1, x2 - 1, G, z2 - 1, 'minecraft:spruce_planks')
    zm = (z1 + z2) // 2
    for z in (zm, zm + 1):          # double door facing east (toward the house)
        w.set(x2, G + 1, z, TB('amado'))
        w.set(x2, G + 2, z, TB('amado'))
        w.set(x2 + 1, G, z, 'minecraft:stone_brick_slab[type=top,waterlogged=false]')
    xm = (x1 + x2) // 2
    for zz, f in ((z1, 'north'), (z2, 'south')):   # small windows: lattice + shutter
        w.set(xm, G + 5, zz, f'{TB("koshi")}[east=true,north=false,south=false,waterlogged=false,west=true]')
        w.set(xm + 1, G + 5, zz, TB('amado'))
    w.set(x2 - 1, G + 1, z1 + 1, 'minecraft:barrel[facing=up,open=false]')
    w.set(x2 - 2, G + 1, z1 + 1, TB('crate_empty'))
    w.set(x1 + 1, G + 1, z2 - 1, 'minecraft:barrel[facing=up,open=false]')
    gable_roof(w, x1, z1, x2, z2, G + 7, 'x', 'tabi:kawara', overhang=1, gable_wall=TB('shikkui'))
    ridge_onigawara(w, x1, z1, x2, z2, 'x')
    _count('kura')
    return True


def village(w):
    G = VG
    F = G + 1
    # ---- 電線 along the road: village poles every 32 blocks, town poles every 30
    xs = list(range(-150, 530, 32)) + list(range(568, 670, 30))
    power_lines(w, xs, 19, G + 9)
    # ---- farmhouse: summer evening on the engawa, kitchen, living room
    z2 = 6
    put(w, -7, F + 1, z2 + 1, facing(TB('suika_plate'), 'south'))
    put(w, -9, F + 1, z2 + 1, TB('katori_senko'))
    put(w, -4, F + 1, z2 + 1, facing(TB('uchiwa'), 'south'))
    put(w, 2, F + 1, -6, facing(TB('retro_tv'), 'west'))
    put(w, 8, F + 1, -9, facing(TB('rice_cooker'), 'south'))
    if base(w.get(5, F, -9)) == 'minecraft:smoker':
        put(w, 5, F, -9, facing(TB('kamado'), 'south'), need_air=False)
    # genkan: asagao pots and a reed screen against the doma wall, geta by the door
    put(w, 6, G + 1, 7, TB('asagao'))
    put(w, 9, G + 1, 7, TB('asagao'))
    for x in (10, 11):
        put(w, x, G + 1, 7, facing(TB('yoshizu'), 'south'))
        put(w, x, G + 2, 7, facing(TB('yoshizu'), 'south'))
    put(w, 7, G + 1, 7, facing(TB('geta'), 'north'))
    # garden: tsukubai by the pond, laundry pole, bicycle
    for (x, z) in ((-6, 12), (-6, 14), (-7, 15), (4, 12)):
        if put(w, x, G + 1, z, facing(TB('tsukubai'), 'east')):
            break
    if all(free_col(w, x, 12, G + 1, 2) for x in range(5, 12)):
        for x in (5, 11):
            w.fill(x, G + 1, 12, x, G + 2, 12, axis(TB('hashira'), 'y'))
        for x in range(6, 11):
            put(w, x, G + 2, 12, facing(TB('monohoshi'), 'north'))
    for (x, z) in ((16, 6), (16, 4), (17, 6)):
        if put(w, x, G + 1, z, facing(TB('bicycle'), 'south')):
            break
    # roof ornaments
    ridge_onigawara(w, -12, -10, 12, 8, 'x')
    # 土蔵 west of the house
    for (x1, z1) in ((-25, -7), (-26, -12), (-27, -3)):
        if kura(w, x1, z1, x1 + 7, z1 + 6, G):
            break
    # ---- road junctions: curve mirrors and 止まれ
    for (x, z) in ((-3, 28), (-3, 29), (-1, 29)):
        if not is_road(w.get(x, G, z)) and pole_with(w, x, z, G + 1, 2, facing(TB('curve_mirror'), 'north'), pole=TB('pole_orange'), search=0):
            break
    pole_with(w, 45, 20, G + 1, 2, facing(TB('curve_mirror'), 'south'), pole=TB('pole_orange'), search=1)
    sign_on_pole(w, 47, 31, G + 1, 'tabi:stop_sign', 'south', ['止まれ'], 'stop', search=1)
    sign_on_pole(w, -60 + 2, 31, G + 1, 'tabi:stop_sign', 'south', ['止まれ'], 'stop', search=1)
    # ---- bus stop + よろず屋: litter bin, red vending machine, public phone, bicycle
    put(w, 65, G + 1, 17, facing(TB('trash_bin'), 'south'))
    if free_col(w, 82, 15, G + 1, 2):
        put(w, 82, G + 1, 15, facing(TB('vending_machine_red'), 'south'))
        put(w, 82, G + 2, 15, facing(TB('vending_machine_red_top'), 'south'))
    put(w, 68, G + 1, 16, facing(TB('public_phone'), 'south'))
    put(w, 84, G + 1, 15, facing(TB('bicycle'), 'south'))
    put(w, 83, G + 1, 17, facing(TB('trash_bin'), 'south'))
    # manholes / gutters along the village road
    road_details(w, G, 'x', 24, -150, 528, span=6, manhole_every=48, grate_every=12)
    # ---- shrine
    sx, sz, sy = -30, -112, 74
    hz1, hz2 = sz - 7, sz - 1
    if base(w.get(sx, sy + 2, hz2 + 1)) == 'minecraft:barrel':
        w.set(sx, sy + 2, hz2 + 1, AIR)
    put(w, sx, sy + 1, hz2 + 1, facing(TB('saisen_bako'), 'south'))
    for x in range(sx - 3, sx + 4):
        put(w, x, sy + 4, hz2, facing(TB('shimenawa'), 'south'))
    for x in range(sx - 2, sx + 3):          # under the upper torii's lower beam
        put(w, x, sy + 4, sz + 10, facing(TB('shimenawa'), 'south'))
    for dx in (-3, 3):
        if free_col(w, sx + dx, sz + 6, sy + 1, 2):
            w.set(sx + dx, sy + 1, sz + 6, 'minecraft:chiseled_stone_bricks')
            put(w, sx + dx, sy + 2, sz + 6, facing(TB('komainu'), 'south'))
    put(w, sx + 6, sy + 1, sz - 2, facing(TB('ema_rack'), 'west'))
    put(w, sx + 6, sy + 1, sz - 4, facing(TB('ema_rack'), 'west'))
    ridge_onigawara(w, sx - 4, hz1, sx + 4, hz2 + 1, 'z')


# ===================================================================== 大川町
def town(w):
    G = TG
    # barber pole at 理容 ひかり (west side, z -26..-16) and a clock at 時計 タナベ (east side, z -15..-5)
    put(w, 598, G + 1, -24, TB('barber_pole'))
    put(w, 598, G + 1, -18, TB('barber_pole'))
    put(w, 604, G + 8, -10, facing(TB('station_clock'), 'north'))
    # station-front junction (shotengai mouth x 598..604 meets the main road z=24)
    junction(w, G, 601, 24, 4, 3, arms=('w', 'e', 'n'))
    road_details(w, G, 'x', 24, 530, 668, span=6, manhole_every=40, grate_every=10, phase=7)
    # station plaza: red vending machine, public phone, litter bin
    for (x, z) in ((610, 32), (609, 32), (611, 31)):
        if free_col(w, x, z, G + 1, 2) and not w.is_air(x, G, z):
            put(w, x, G + 1, z, facing(TB('vending_machine_red'), 'north'))
            put(w, x, G + 1 + 1, z, facing(TB('vending_machine_red_top'), 'north'))
            break
    for (x, z) in ((643, 32), (644, 32), (642, 31)):
        if put(w, x, G + 1, z, facing(TB('public_phone'), 'north')):
            break
    put(w, 645, G + 1, 32, facing(TB('trash_bin'), 'north'))
    for (x, z) in ((646, 30), (647, 30), (648, 30)):
        put(w, x, G + 1, z, facing(TB('bicycle'), 'north'))
    for x in (618, 634, 626):
        if put(w, x, G + 5, 33, facing(TB('station_clock'), 'north')):
            break


# ===================================================================== stations along 川霧線
PLATFORMS = {   # key -> (roof x0, x1, side of the track, board lines)
    'okawa': ('okawa_platform', (612, 640), 'north', ['普通　神戸 ゆき', '霧ヶ谷・潮見 方面']),
    'kirigaya': ('kiri_platform', (1238, 1252), 'north', ['普通　神戸／大川', 'まもなく到着します']),
    'shiomi': ('shiomi_platform', (2088, 2112), 'north', ['普通　神戸／大川', '海峡の見える駅 潮見']),
    'kobe': ('kobe_platform', (3404, 3460), 'south', ['普通　大川 ゆき', '潮見・霧ヶ谷 方面']),
}


def track_at(line, x):
    pts = line['pts']
    i = int(np.argmin([abs(p[0] - x) for p in pts]))
    Y, low = line['q'][i]
    return int(math.floor(pts[i][1] + 0.5)), Y


def route_s_at(w, rid, x):
    for r in w.meta['routes']:
        if r['id'] == rid:
            P = np.array(r['points'], dtype=np.float64)
            seg = np.sqrt(((P[1:] - P[:-1]) ** 2).sum(axis=1))
            cum = np.concatenate(([0.0], np.cumsum(seg)))
            return round(float(cum[int(np.argmin(np.abs(P[:, 0] - x)))]), 1)
    return 0.0


def rail_signal(w, line, x, side, f):
    """出発信号機 beside the track (side +1 = south of it), head 4 blocks above the rails"""
    cz, Y = track_at(line, x)
    z = cz + 3 * side
    top = w.top(x, z)
    if top > Y + 1:
        return
    y0 = top + 1
    h = (Y + 3) - y0
    if h < 1 or h > 8:
        return
    pole_with(w, x, z, y0, h, facing(TB('rail_signal'), f), search=0)


def stations(w, line):
    for sid, (key, (r0, r1), side, lines) in PLATFORMS.items():
        x1, x2, za, zb, yt = line[key]
        zc = (za + zb) // 2
        # hanging from the platform roof: clock and LED departure board (both double-sided)
        put(w, r0 + (r1 - r0) * 2 // 3, yt + 4, zc, facing(TB('station_clock'), 'east'))
        bx = r0 + 3
        if put(w, bx, yt + 4, zc, facing(TB('departure_board'), 'east')):
            w.sign(bx, yt + 4, zc, 'tabi:departure_board', 'east', lines, style='led', back=True)
        # litter bins along the back of the platform
        back_z = za if side == 'north' else zb
        f_track = 'south' if side == 'north' else 'north'
        for x in (r0 + 1, r1 - 1):
            put(w, x, yt + 1, back_z, facing(TB('trash_bin'), f_track))
    # Okawa: kiosk next to the ekiben stall, travel posters on the station building wall
    _, _, za, zb, yt = line['okawa_platform']
    for x in (644, 645):
        put(w, x, yt + 1, za, facing(TB('kiosk_shelf'), 'south'))
    for x, p in ((615, 'poster_kobe'), (622, 'poster_sea'), (631, 'poster_kobe'), (637, 'poster_sea')):
        if not w.is_air(x, yt + 2, za - 1):
            put(w, x, yt + 2, za, facing(TB(p), 'south'))
    # Kobe: kiosk, posters on the station building side
    _, _, za, zb, yt = line['kobe_platform']
    for x in (3442, 3443):
        put(w, x, yt + 1, zb, facing(TB('kiosk_shelf'), 'north'))
    for x, p in ((3408, 'poster_sea'), (3416, 'poster_kobe'), (3426, 'poster_sea'), (3450, 'poster_kobe')):
        if not w.is_air(x, yt + 2, zb + 1):
            put(w, x, yt + 2, zb, facing(TB(p), 'north'))
    # departure signals at the platform ends (on the far side of the track)
    rail_signal(w, line, 657, +1, 'west')        # Okawa -> east
    rail_signal(w, line, 1272, +1, 'west')       # Kirigaya
    rail_signal(w, line, 1218, +1, 'east')
    rail_signal(w, line, 2128, +1, 'west')       # Shiomi
    rail_signal(w, line, 2070, +1, 'east')
    rail_signal(w, line, 3391, -1, 'east')       # Kobe -> west


# ===================================================================== 潮見: seaside level crossing + tetrapods
def shiomi(w, line):
    sz, g0 = line['shiomi_z'], line['shiomi_ytop']
    X0, X1 = 2136, 2140                      # 5-wide lane from the station square down to the fishing jetty
    cz, Y = track_at(line, (X0 + X1) // 2)
    # extend the station square east to the lane
    for x in range(2126, X1 + 2):
        for z in range(sz - 14, sz - 7):
            g = w.top(x, z)
            if g < g0:
                w.fill(x, g, z, x, g0 - 1, z, 'minecraft:stone')
            if g <= g0 + 1:
                w.set(x, g0, z, 'tabi:sidewalk')
                w.clear(x, g0 + 1, z, x, g0 + 8, z)
    deck = Y            # walking level over the rails: top of blocks at Y-1, slabs at Y beside the rails
    # north approach: from the square (g0) down to the deck level
    zN = cz - 2
    for z in range(sz - 7, zN + 1):
        t = (z - (sz - 7)) / max(1, zN - (sz - 7))
        h = g0 - t * (g0 - (deck - 1))
        for x in range(X0, X1 + 1):
            _surface(w, x, z, h)
    # south approach: from the deck to the jetty (top 65)
    zS = cz + 2
    zJ = sz + 4
    for z in range(zS, zJ + 1):
        t = (z - zS) / max(1, zJ - zS)
        h = (deck - 1) + t * (65 - (deck - 1))
        for x in range(X0, X1 + 1):
            _surface(w, x, z, h)
    for x in range(X0, X1 + 1):          # jetty connection
        for z in range(zJ, sz + 4):
            _surface(w, x, z, 65)
    # deck across the track: rubber/asphalt panels flush with the rails
    for x in range(X0, X1 + 1):
        for z in range(cz - 1, cz + 2):
            st = w.get(x, Y, z)
            if 'track' in st:
                continue
            w.set(x, Y - 1, z, 'tabi:asphalt')
            w.set(x, Y, z, slab(TB('asphalt_slab')))
            w.clear(x, Y + 1, z, x, Y + 5, z)
    # signals (crossbuck + flashing lamps) on striped poles, gate bars that drop when a train comes
    sigs, bars = [], []
    for (x, z, f) in ((X0 - 1, cz - 3, 'north'), (X1 + 1, cz + 3, 'south')):
        y0 = w.top(x, z) + 1
        if not free_col(w, x, z, y0, 3):
            w.clear(x, y0, z, x, y0 + 2, z)
        w.set(x, y0, z, TB('crossing_pole'))
        w.set(x, y0 + 1, z, TB('crossing_pole'))
        w.set(x, y0 + 2, z, f'{TB("crossing_signal")}[facing={f},flash=0]')
        sigs.append([x, y0 + 2, z])
    for z in (cz - 3, cz + 3):
        for x in range(X0, X1 + 1):
            y = w.top(x, z) + 1
            w.clear(x, y, z, x, y + 3, z)
            bars.append([x, y, z, 'north'])
    w.meta.setdefault('crossings', []).append({'route': 'kawagiri', 's': route_s_at(w, 'kawagiri', (X0 + X1) / 2),
                                               'pos': [(X0 + X1) // 2, Y + 1, cz], 'signals': sigs, 'bars': bars})
    sign_on_pole(w, X1 + 2, cz - 5, w.top(X1 + 2, cz - 5) + 1, 'tabi:info_board', 'north',
                 ['踏切注意', '警報機が鳴ったら', 'わたらないでね'], 'info', search=1)
    _count('crossing')
    # tetrapods along the water's edge in front of the embankment
    rnd = random.Random(31)
    for x in range(2040, 2200):
        if X0 - 3 <= x <= X1 + 4 or 2137 <= x <= 2143:
            continue
        cz2, _ = track_at(line, x)
        for z in range(cz2 + 3, cz2 + 40):
            if w.get(x, 62, z) == 'minecraft:water':
                for dz in range(0, 2 + (x % 3 == 0)):
                    if rnd.random() < 0.8 and w.get(x, 62, z + dz) == 'minecraft:water':
                        w.set(x, 61, z + dz, 'minecraft:stone')
                        w.set(x, 62, z + dz, TB('tetrapod'))
                        _count('tabi:tetrapod')
                        if rnd.random() < 0.3:
                            put(w, x, 63, z + dz, TB('tetrapod'))
                break


def _surface(w, x, z, h):
    """walkable surface at height h (float): full block top at floor(h), plus a slab for the half step"""
    yi = int(math.floor(h))
    half = (h - yi) >= 0.5
    g = w.top(x, z)
    if g < yi:
        w.fill(x, g, z, x, yi - 1, z, 'minecraft:stone')
    w.set(x, yi, z, 'tabi:asphalt')
    w.clear(x, yi + 1, z, x, yi + 6, z)
    if half:
        w.set(x, yi + 1, z, slab(TB('asphalt_slab')))


# ===================================================================== 神戸
def palm(w, x, y, z, h):
    if not free_col(w, x, z, y, h + 2):
        return False
    for i in range(h):
        w.set(x, y + i, z, 'minecraft:jungle_log[axis=y]')
    t = y + h
    w.set(x, t, z, 'minecraft:jungle_leaves[distance=1,persistent=true,waterlogged=false]')
    put(w, x, t + 1, z, TB('palm_fronds'))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(w, x + dx, t, z + dz, TB('palm_fronds'))
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        put(w, x + dx, t - 1, z + dz, TB('palm_fronds'))
    _count('palm')
    return True


def aviation_lights(w, x1, z1, x2, z2, min_h, n_max=30, spacing=14):
    """航空障害灯 on the tallest roofs: red lights that blink over the night skyline"""
    cand = []
    for cx in range(x1 >> 4, (x2 >> 4) + 1):
        for cz in range(z1 >> 4, (z2 >> 4) + 1):
            a = w.chunks.get((cx, cz))
            if a is None:
                continue
            nz = a != 0
            H = nz.shape[2]
            top = H - 1 - np.argmax(nz[:, :, ::-1], axis=2)
            for lz in range(0, 16, 2):
                for lx in range(0, 16, 2):
                    y = int(top[lz, lx]) + YMIN
                    if y >= min_h:
                        cand.append((y, (cx << 4) + lx, (cz << 4) + lz))
    cand.sort(reverse=True)
    chosen = []
    for (y, x, z) in cand:
        if len(chosen) >= n_max:
            break
        if any(abs(x - cx) < spacing and abs(z - cz) < spacing for (_, cx, cz) in chosen):
            continue
        if 'leaves' in w.get(x, y, z) or 'log' in w.get(x, y, z):
            continue
        if put(w, x, y + 1, z, TB('aviation_light')):
            chosen.append((y, x, z))
    return chosen


def kobe(w):
    G = KG
    # ---- junctions on the avenues (R1 z=702 half 8, R4 z=590 half 7; N-S: R2 x=3575, R3 x=3484, R5 x=3700, side streets)
    junction(w, G, 3484, 702, 8, 6, arms=('w', 'e', 'n'))
    junction(w, G, 3575, 702, 8, 6, arms=('w', 'e', 's'))
    junction(w, G, 3800, 702, 8, 4, arms=('w', 'n'))
    junction(w, G, 3484, 590, 7, 6, arms=('w', 'e', 's'))
    junction(w, G, 3700, 590, 7, 6, arms=('w', 'e', 'n'))
    junction(w, G, 3560, 590, 7, 4, arms=('w', 'e', 'n', 's'))
    junction(w, G, 3800, 590, 7, 4, arms=('w', 'e', 'n', 's'))
    junction(w, G, 3400, 590, 7, 4, arms=('e', 'n', 's'), signals=False)
    for x in (3400, 3560):          # side streets meeting the harbour avenue: 止まれ
        sign_on_pole(w, x + 4, 692, G + 1, 'tabi:stop_sign', 'north', ['止まれ'], 'stop', search=1)
    for x in (3900, 4000):
        junction(w, G, x, 520, 4, 4, signals=False)
    # ---- covers and gutters along the roads
    road_details(w, G, 'x', 702, 3330, 3820, span=9, manhole_every=40, grate_every=8)
    road_details(w, G, 'x', 590, 3360, 3820, span=8, manhole_every=40, grate_every=8, phase=13)
    road_details(w, G, 'x', 520, 3330, 4050, span=5, manhole_every=44, grate_every=10, phase=5)
    road_details(w, G, 'z', 3575, 702, 754, span=6, manhole_every=30, grate_every=8)
    road_details(w, G, 'z', 3484, 590, 702, span=6, manhole_every=36, grate_every=8)
    road_details(w, G, 'z', 3700, 474, 590, span=6, manhole_every=36, grate_every=8)
    for x in (3400, 3560, 3800, 3900, 4000):
        road_details(w, G, 'z', x, 470, 700, span=5, manhole_every=44, grate_every=10, phase=x % 7)
    # ---- harbour: bollards + life rings on the Harborland promenade and the Meriken pier (ferry side)
    for x in range(3341, 3556, 20):
        put(w, x, G + 1, 810, TB('bollard'))
    for x in range(3351, 3556, 40):
        put(w, x, G + 1, 810, facing(TB('life_ring'), 'north'))
    for z in range(846, 874, 9):
        put(w, 3722, G + 1, z, TB('bollard'))
    for z in (830, 890):
        put(w, 3722, G + 1, z, facing(TB('life_ring'), 'west'))
    for x in range(3600, 3716, 24):
        put(w, x, G + 1, 905, facing(TB('life_ring'), 'north'))
    # ---- palms: Meriken Park waterfront, the harbour road (R2)
    for x in range(3596, 3720, 14):
        palm(w, x, G + 1, 899, 7 + (x // 14) % 3)
    for z in range(716, 752, 10):
        palm(w, 3569, G + 1, z, 6 + (z // 10) % 2)
        palm(w, 3581, G + 1, z + 5, 6 + (z // 10 + 1) % 2)
    for x in range(3345, 3550, 30):
        palm(w, x, G + 1, 800, 6)
    # ---- Port Tower top + tallest roofs: aviation lights
    pt = max(((w.top(x, z), x, z) for x in range(3608, 3617) for z in range(796, 805)))
    put(w, pt[1], pt[0] + 1, pt[2], TB('aviation_light'))
    aviation_lights(w, 3150, 470, 4060, 800, G + 34)
    # ---- Kitano: wrought-iron fences and flower planters in front of the ijinkan
    kx1, kx2, kz1, kz2 = 3712, 3732, 408, 424
    gy = max(w.ground(x, z) for x in range(kx1, kx2 + 1) for z in range(kz1, kz2 + 4))
    _fence_line(w, kx1 - 2, kx2 + 2, kz2 + 8, gy + 1, gap=(3720, 3724))
    for x in (kx1 - 3, kx2 + 3, 3719, 3725):
        put(w, x, gy + 1, kz2 + 10, TB('flower_planter'))
    mx1, mx2, mz1, mz2 = 3756, 3776, 414, 430
    gy2 = max(w.ground(x, z) for x in range(mx1, mx2 + 1) for z in range(mz1, mz2 + 4))
    _fence_line(w, mx1 - 2, mx2 + 2, mz2 + 5, gy2 + 1, gap=(3764, 3768))
    for x in (mx1 - 1, mx2 + 1):
        put(w, x, gy2 + 1, mz2 + 4, TB('flower_planter'))
    for z in range(406, 544, 12):           # Kitano-zaka: planters along the brick sidewalk
        y = w.ground(3746, z)
        put(w, 3746, y + 1, z, TB('flower_planter'))
    fix_connections(w, 3700, gy - 3, 400, 3790, gy2 + 4, 450)
    # ---- Kobe station plaza: car-stop posts at the pedestrian area edge
    for x in range(3420, 3462, 3):
        put(w, x, G + 1, 694, TB('car_stop_post'))


def _fence_line(w, xa, xb, z, y, gap):
    for x in range(xa, xb + 1):
        if gap[0] <= x <= gap[1]:
            continue
        if w.is_air(x, y, z) and not w.is_air(x, y - 1, z):
            w.set(x, y, z, f'{TB("iron_fence")}[east=true,north=false,south=false,waterlogged=false,west=true]')
            _count('tabi:iron_fence')


def build(w, line):
    village(w)
    town(w)
    stations(w, line)
    shiomi(w, line)
    kobe(w)
    fix_connections(w, -40, 60, -20, 30, 90, 10)
    # screenshot viewpoints for the new details
    sz, g0 = line['shiomi_z'], line['shiomi_ytop']
    w.viewpoint('shiomi_crossing', 2131.5, g0 + 2.6, sz - 12.5, -30, 8, time=11800, mode='spectator')
    w.viewpoint('kobe_junction_night', 3560.5, KG + 3.6, 716.5, -133, 6, time=14500, mode='spectator')
    w.viewpoint('village_kura', -12.5, VG + 3.6, 9.5, 150, 6, time=1500, mode='spectator')
    w.viewpoint('engawa_evening', -6.5, VG + 3.2, 12.5, 180, 14, time=12300, mode='spectator')
    print('deco:', ', '.join(f'{k.replace("tabi:", "")} {v}' for k, v in sorted(STATS.items())))
