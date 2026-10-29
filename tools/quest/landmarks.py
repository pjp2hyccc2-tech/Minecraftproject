"""Voxel landmark builders shared by the quest-map designs (camp, trees, ruins, skeletons, spires...)."""
import math

import numpy as np

from qmap import HORIZ, DIR_VEC, rotate_state


def _rng(seed):
    return np.random.default_rng(seed)


# ====================================================================== base camp
CAMP_STYLES = {
    'verdant': dict(roof=('minecraft:white_wool', 'minecraft:blue_wool'), pole='minecraft:oak_fence', log='minecraft:stripped_oak_log',
                    floor='minecraft:dirt_path', pad='minecraft:grass_block[snowy=false]', carpet='minecraft:blue_carpet',
                    bed='minecraft:blue_bed', fence='minecraft:oak_fence', lantern='minecraft:lantern'),
    'frost': dict(roof=('minecraft:white_wool', 'minecraft:light_blue_wool'), pole='minecraft:spruce_fence',
                  log='minecraft:stripped_spruce_log', floor='minecraft:coarse_dirt', pad='minecraft:snow_block',
                  carpet='minecraft:light_gray_carpet', bed='minecraft:light_blue_bed', fence='minecraft:spruce_fence',
                  lantern='minecraft:lantern'),
    'ember': dict(roof=('minecraft:red_terracotta', 'minecraft:orange_terracotta'), pole='minecraft:crimson_fence',
                  log='minecraft:stripped_crimson_stem', floor='minecraft:packed_mud', pad='minecraft:smooth_basalt',
                  carpet='minecraft:red_carpet', bed='minecraft:red_bed', fence='minecraft:crimson_fence',
                  lantern='minecraft:soul_lantern'),
}


def build_camp(m, cu, cv, y, facing, style):
    """Base camp around (cu, cv), ground (standing) level y, opening towards `facing`.
    Local frame: +b = forward (towards the map), +a = right hand side. Designed facing south and rotated."""
    st = CAMP_STYLES[style]
    fx, fz = DIR_VEC[facing]
    rx, rz = -fz, fx
    steps = (HORIZ.index(facing) - HORIZ.index('south')) % 4

    def P(a, b):
        return cu + a * rx + b * fx, cv + a * rz + b * fz

    def put(a, dy, b, s):
        u, v = P(a, b)
        m.put(u, y + dy, v, rotate_state(s, steps))

    # pad + floor ring
    for a in range(-9, 10):
        for b in range(-10, 10):
            if a * a * 0.8 + b * b * 0.8 > 100:
                continue
            u, v = P(a, b)
            m.put(u, y - 1, v, st['pad'])
            for dy in range(0, 6):
                m.put(u, y + dy, v, 'minecraft:air', replace=False)
    for a in range(-3, 6):
        for b in range(-2, 8):
            if (a - 1) ** 2 + (b - 2) ** 2 <= 14:
                u, v = P(a, b)
                m.put(u, y - 1, v, st['floor'])
    # tent (A-frame along b), footprint a in [-8, -2], b in [-9, -3]
    r0, r1 = st['roof']
    for b in range(-9, -2):
        stripe = r1 if b % 2 == 0 else r0
        for a in range(-8, -1):
            off = abs(a + 5)
            hgt = 4 - off  # 4 at the ridge (a=-5), 1 at the eaves
            if hgt < 1:
                continue
            put(a, hgt, b, stripe)
            if b == -9:  # closed back wall
                for dy in range(0, hgt):
                    put(a, dy, b, r0)
        put(-8, 0, b, st['pole'])
        put(-2, 0, b, st['pole'])
    for b in range(-8, -2):
        for a in range(-7, -2):
            put(a, -1, b, st['carpet'].replace('_carpet', '_wool') if False else st['floor'])
            put(a, 0, b, st['carpet'])
    # bed: head at the back
    put(-5, 0, -8, f"{st['bed']}[facing=north,part=head,occupied=false]")
    put(-5, 0, -7, f"{st['bed']}[facing=north,part=foot,occupied=false]")
    put(-5, 3, -6, f"{st['lantern']}[hanging=true,waterlogged=false]")
    put(-3, 0, -8, 'minecraft:barrel[facing=up,open=false]')
    put(-7, 0, -8, 'minecraft:chest[facing=south,type=single,waterlogged=false]')
    # supply (blue) + delivery (red) boxes, guild flag
    put(0, 0, -6, 'huntcraft:supply_box[facing=south]')
    put(2, 0, -6, 'huntcraft:delivery_box[facing=south]')
    put(1, 0, -7, 'minecraft:cartography_table')
    put(5, 0, -5, 'huntcraft:camp_flag[facing=south]')
    # campfire + log seats
    put(1, 0, 2, 'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    put(-1, 0, 2, f"{st['log']}[axis=z]")
    put(3, 0, 2, f"{st['log']}[axis=z]")
    put(1, 0, 4, f"{st['log']}[axis=x]")
    # stores at the back right
    for (a, b, s) in [(6, -8, 'minecraft:barrel[facing=up,open=false]'), (7, -8, 'minecraft:hay_block[axis=y]'),
                      (7, -7, 'minecraft:barrel[facing=up,open=false]'), (6, -7, 'minecraft:composter[level=0]')]:
        put(a, 0, b, s)
    put(7, 1, -8, 'minecraft:hay_block[axis=x]')
    put(6, 1, -8, f"{st['lantern']}[hanging=false,waterlogged=false]")
    # fence around the back with lantern posts
    for a in range(-9, 10):
        put(a, 0, -10, st['fence'])
    for b in range(-10, -3):
        put(-9, 0, b, st['fence'])
        put(9, 0, b, st['fence'])
    for (a, b) in [(-9, -10), (9, -10), (-9, -4), (9, -4)]:
        put(a, 1, b, st['fence'])
        put(a, 2, b, f"{st['lantern']}[hanging=false,waterlogged=false]")
    arrivals = []
    for (a, b) in [(-1, 5), (3, 5), (1, 6), (-1, 7), (3, 7), (1, 8)]:
        u, v = P(a, b)
        arrivals.append((u, y, v))
    su, sv = P(0, -6)
    du, dv = P(2, -6)
    gu, gv = P(5, -5)
    bu, bv = P(-5, -7)
    yaw = {'south': 0.0, 'west': 90.0, 'north': 180.0, 'east': -90.0}[facing]
    m.camp = dict(x=int(cu), y=int(y), z=int(cv), yaw=yaw, r=18, supply=[su, y, sv], delivery=[du, y, dv], flag=[gu, y, gv],
                  bed=[bu, y, bv], arrivals=arrivals, fwd=facing[0])


def flatten_pad(m, cu, cv, y, r_in=11, r_out=19):
    """Flattens the terrain to y-1 within r_in, blending out to r_out."""
    d = np.hypot(np.arange(m.h.shape[1])[None, :] - cu, np.arange(m.h.shape[0])[:, None] - cv)
    t = np.clip((d - r_in) / (r_out - r_in), 0, 1)
    t = t * t * (3 - 2 * t)
    m.h = m.h * t + (y - 1) * (1 - t)


# ====================================================================== giant ancient tree
def giant_tree(m, u0, v0, y0, height=52, r0=4.6, seed=1, log='minecraft:dark_oak_log', leaves=None, glow=True):
    rng = _rng(seed)
    leaves = leaves or ['minecraft:oak_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]',
                        'minecraft:azalea_leaves[persistent=true]']
    L = lambda axis: f'{log}[axis={axis}]'
    # buttress roots
    for k in range(9):
        ang = k * 2 * math.pi / 9 + rng.random() * 0.4
        length = 7 + rng.random() * 6
        for s in np.arange(0, length, 0.5):
            rr = max(0.6, (r0 + 1.4) * (1 - s / length) ** 1.3)
            cu = u0 + math.cos(ang) * (r0 + s)
            cv = v0 + math.sin(ang) * (r0 + s)
            top = y0 + 3.5 * (1 - s / length) ** 2 - 1
            for du in range(-2, 3):
                for dv in range(-2, 3):
                    if du * du + dv * dv <= rr * rr:
                        for yy in range(int(y0) - 3, int(top) + 1):
                            m.put(cu + du, yy, cv + dv, L('y'))
    # trunk
    for yy in range(int(y0) - 3, int(y0 + height * 0.82)):
        t = (yy - y0) / height
        rr = r0 * (1 - 0.45 * t) + 0.5 * math.sin(yy * 0.4)
        ir = int(rr) + 1
        for du in range(-ir, ir + 1):
            for dv in range(-ir, ir + 1):
                if du * du + dv * dv <= rr * rr:
                    m.put(u0 + du, yy, v0 + dv, L('y'))
    # branches + canopy blobs
    blobs = [(u0, y0 + height * 0.86, v0, 11.0)]
    nb = 8
    for k in range(nb):
        ang = k * 2 * math.pi / nb + rng.random() * 0.5
        hy = y0 + height * (0.5 + 0.32 * rng.random())
        length = 14 + rng.random() * 8
        rise = 3 + rng.random() * 5
        ex, ez = math.cos(ang), math.sin(ang)
        axis = 'x' if abs(ex) > abs(ez) else 'z'
        for s in np.arange(0, length, 0.5):
            t = s / length
            cu = u0 + ex * (r0 * 0.6 + s)
            cv = v0 + ez * (r0 * 0.6 + s)
            cy = hy + rise * t * t
            th = 1.6 * (1 - t) + 0.6
            for du in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dv in (-1, 0, 1):
                        if du * du + dy * dy + dv * dv <= th * th:
                            m.put(cu + du, cy + dy, cv + dv, L(axis))
        blobs.append((u0 + ex * (r0 + length), hy + rise + 1, v0 + ez * (r0 + length), 7.0 + rng.random() * 2.5))
    for k in range(5):
        ang = rng.random() * 2 * math.pi
        rad = 6 + rng.random() * 6
        blobs.append((u0 + math.cos(ang) * rad, y0 + height * (0.78 + 0.12 * rng.random()), v0 + math.sin(ang) * rad, 6.5))
    for (bu, by, bv, br) in blobs:
        ir = int(br) + 1
        for du in range(-ir, ir + 1):
            for dy in range(-int(br * 0.55) - 1, int(br * 0.55) + 2):
                for dv in range(-ir, ir + 1):
                    d = math.sqrt(du * du + (dy / 0.5) ** 2 + dv * dv)
                    if d <= br + rng.random() * 1.2 - 0.6:
                        pos = (int(round(bu + du)), int(round(by + dy)), int(round(bv + dv)))
                        if m.get(*pos) is None:
                            m.put(*pos, leaves[rng.integers(len(leaves))])
        if glow:
            # glow berry vines hanging from the canopy underside
            for _ in range(5):
                gu = int(bu + rng.integers(-ir + 2, ir - 1))
                gv = int(bv + rng.integers(-ir + 2, ir - 1))
                base = None
                for yy in range(int(by - br), int(by + 2)):
                    if m.get(gu, yy, gv) and 'leaves' in m.get(gu, yy, gv):
                        base = yy
                        break
                if base is None:
                    continue
                n = 2 + rng.integers(4)
                for i in range(1, n + 1):
                    yy = base - i
                    if m.get(gu, yy, gv) is not None:
                        break
                    berries = 'true' if rng.random() < 0.6 else 'false'
                    if i == n:
                        m.put(gu, yy, gv, f'minecraft:cave_vines[age=25,berries={berries}]')
                    else:
                        m.put(gu, yy, gv, f'minecraft:cave_vines_plant[berries={berries}]')


# ====================================================================== ruins
def ruins(m, u0, v0, y0, half=18, seed=2, gate='south'):
    rng = _rng(seed)
    mats = ['minecraft:stone_bricks'] * 5 + ['minecraft:mossy_stone_bricks'] * 3 + ['minecraft:cracked_stone_bricks'] * 2 + \
           ['minecraft:mossy_cobblestone']
    pick = lambda: mats[rng.integers(len(mats))]
    # floor
    for du in range(-half, half + 1):
        for dv in range(-half, half + 1):
            if rng.random() < 0.72:
                m.put(u0 + du, y0 - 1, v0 + dv, pick() if rng.random() < 0.8 else 'minecraft:mossy_cobblestone')
    # walls (2 thick) with broken heights
    gx, gz = DIR_VEC[gate]
    for i in range(-half, half + 1):
        for side in range(4):
            for t in (0, 1):
                if side == 0:
                    du, dv = i, -half + t
                elif side == 1:
                    du, dv = i, half - t
                elif side == 2:
                    du, dv = -half + t, i
                else:
                    du, dv = half - t, i
                # gate gap
                if (gz != 0 and side == (1 if gz > 0 else 0) and abs(du) <= 2) or (gx != 0 and side == (3 if gx > 0 else 2) and abs(dv) <= 2):
                    continue
                hgt = int(2 + 4 * (0.5 + 0.5 * math.sin(i * 0.35 + side * 1.7)) * (rng.random() * 0.6 + 0.6))
                if rng.random() < 0.12:
                    hgt = rng.integers(0, 2)
                for dy in range(hgt):
                    m.put(u0 + du, y0 + dy, v0 + dv, pick())
                if hgt > 0 and rng.random() < 0.3:
                    m.put(u0 + du, y0 + hgt, v0 + dv, 'minecraft:moss_carpet')
                elif hgt > 0 and rng.random() < 0.2:
                    m.put(u0 + du, y0 + hgt, v0 + dv, 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    # corner towers
    for (cx, cz) in [(-half, -half), (half, -half), (-half, half), (half, half)]:
        th = 7 + rng.integers(0, 6)
        for du in range(-2, 3):
            for dv in range(-2, 3):
                edge = abs(du) == 2 or abs(dv) == 2
                if not edge:
                    continue
                top = th - (rng.integers(0, 4) if rng.random() < 0.5 else 0)
                for dy in range(top):
                    m.put(u0 + cx + du, y0 + dy, v0 + cz + dv, pick())
        m.put(u0 + cx, y0 + th - 1, v0 + cz, 'minecraft:chiseled_stone_bricks')
    # gate arch
    ax, az = u0 + gx * half, v0 + gz * half
    px, pz = (1, 0) if gz != 0 else (0, 1)
    for dy in range(0, 7):
        for s in (-3, 3):
            for t in (0, -1):
                m.put(ax + px * s + gx * t, y0 + dy, az + pz * s + gz * t, 'minecraft:chiseled_stone_bricks' if dy == 6 else pick())
    for s in range(-3, 4):
        for t in (0, -1):
            m.put(ax + px * s + gx * t, y0 + 6, az + pz * s + gz * t, pick())
            if abs(s) <= 1:
                m.put(ax + px * s + gx * t, y0 + 7, az + pz * s + gz * t, 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
    # inner pillars (some broken) and a central dais
    for (du, dv) in [(-8, -8), (8, -8), (-8, 8), (8, 8), (0, -10), (-10, 0), (10, 0)]:
        ph = 2 + rng.integers(0, 6)
        for dy in range(ph):
            m.put(u0 + du, y0 + dy, v0 + dv, 'minecraft:stone_bricks' if dy < ph - 1 else 'minecraft:chiseled_stone_bricks')
    for du in range(-3, 4):
        for dv in range(-3, 4):
            m.put(u0 + du, y0, v0 + dv, 'minecraft:polished_andesite' if abs(du) < 3 and abs(dv) < 3 else pick())
    m.put(u0, y0 + 1, v0, 'minecraft:lodestone')
    # rubble
    for _ in range(40):
        du, dv = rng.integers(-half - 4, half + 5), rng.integers(-half - 4, half + 5)
        s = ['minecraft:mossy_cobblestone', 'minecraft:cobblestone', 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]',
             'minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]'][rng.integers(4)]
        m.put(u0 + du, m.ground(u0 + du, v0 + dv), v0 + dv, s, replace=False)


# ====================================================================== skeletons
def skeleton(m, u0, v0, y0, length=34, ang=0.0, seed=3, wings=True, block='minecraft:bone_block'):
    """Giant wyvern skeleton lying on the ground, head at (u0, v0) pointing along ang (radians)."""
    rng = _rng(seed)
    ex, ez = math.cos(ang), math.sin(ang)
    px, pz = -ez, ex
    axis_along = 'x' if abs(ex) > abs(ez) else 'z'
    axis_side = 'z' if axis_along == 'x' else 'x'
    B = lambda a: f'{block}[axis={a}]'
    # skull
    for du in range(-4, 5):
        for dv in range(-4, 5):
            for dy in range(0, 5):
                lx = du * ex + dv * ez
                lz = -du * ez + dv * ex
                if (lx / 4.2) ** 2 + (lz / 3.0) ** 2 + ((dy - 1.5) / 2.4) ** 2 <= 1.0:
                    if dy >= 2 and abs(lz) >= 1.0 and abs(lz) <= 2.2 and -1 <= lx <= 1:
                        continue  # eye sockets
                    m.put(u0 + du, y0 + dy, v0 + dv, B('y'))
    # jaw / teeth forward
    for s in range(4, 9):
        for side in (-1.5, 1.5):
            m.put(u0 + ex * s + px * side, y0, v0 + ez * s + pz * side, B(axis_along))
        if s % 2 == 0:
            m.put(u0 + ex * s + px * 1.5, y0 + 1, v0 + ez * s + pz * 1.5, 'minecraft:bone_block[axis=y]')
            m.put(u0 + ex * s - px * 1.5, y0 + 1, v0 + ez * s - pz * 1.5, 'minecraft:bone_block[axis=y]')
    # horns
    for s in range(1, 7):
        for side in (-1, 1):
            m.put(u0 - ex * (2 + s * 0.7) + px * side * (2 + s * 0.4), y0 + 3 + s * 0.6, v0 - ez * (2 + s * 0.7) + pz * side * (2 + s * 0.4),
                  B('y'))
    # spine + ribs + tail
    for s in range(5, length):
        t = (s - 5) / (length - 5)
        cy = y0 + 1.5 + 2.5 * math.sin(min(1.0, t * 2.2) * math.pi) * (1 - t * 0.5)
        cu, cv = u0 - ex * s, v0 - ez * s
        m.put(cu, cy, cv, B(axis_along))
        if s % 2 == 0:
            m.put(cu, cy + 1, cv, B('y'))
        if 0.1 < t < 0.5 and s % 3 == 0:
            span = 5.5 * math.sin((t - 0.1) / 0.4 * math.pi) + 2
            for side in (-1, 1):
                for k in np.arange(0, math.pi * 0.95, 0.12):
                    ru = cu + px * side * math.sin(k) * span
                    rv = cv + pz * side * math.sin(k) * span
                    ry = cy - (1 - math.cos(k)) * (cy - y0 + 0.5) * 0.5 + math.sin(k) * 1.5
                    m.put(ru, max(y0, ry), rv, B(axis_side))
        if t > 0.6 and rng.random() < 0.15:
            continue
    # legs / wings
    if wings:
        for side in (-1, 1):
            base_s = 12
            for k in range(0, 16):
                wu = u0 - ex * (base_s + k * 0.5) + px * side * (3 + k)
                wv = v0 - ez * (base_s + k * 0.5) + pz * side * (3 + k)
                m.put(wu, y0 + max(0, 3 - k * 0.25), wv, B(axis_side))
            for f in range(3):
                for k in range(0, 9):
                    wu = u0 - ex * (base_s + 8 + f * 3 + k * 0.8) + px * side * (19 - k * 0.6)
                    wv = v0 - ez * (base_s + 8 + f * 3 + k * 0.8) + pz * side * (19 - k * 0.6)
                    m.put(wu, y0, wv, B(axis_along))


# ====================================================================== spires / pillars / rocks
def spire(m, u0, v0, y0, height, r, mats, seed=4, lean=(0.0, 0.0)):
    rng = _rng(seed)
    for dy in range(-2, int(height)):
        t = max(0.0, dy / height)
        rr = r * (1 - t) ** 1.2 + 0.3
        cu, cv = u0 + lean[0] * dy, v0 + lean[1] * dy
        ir = int(rr) + 1
        for du in range(-ir, ir + 1):
            for dv in range(-ir, ir + 1):
                if du * du + dv * dv <= rr * rr:
                    core = du * du + dv * dv <= (rr * 0.45) ** 2
                    m.put(cu + du, y0 + dy, cv + dv, mats[1] if core and len(mats) > 1 else mats[0] if rng.random() < 0.9 else mats[-1])


def boulder(m, u0, v0, y0, r, mats, seed=5):
    rng = _rng(seed)
    ir = int(r) + 1
    for du in range(-ir, ir + 1):
        for dv in range(-ir, ir + 1):
            for dy in range(-1, ir + 1):
                d = math.sqrt(du * du + (dy * 1.3) ** 2 + dv * dv)
                if d <= r + rng.random() * 0.8 - 0.4:
                    m.put(u0 + du, y0 + dy, v0 + dv, mats[rng.integers(len(mats))])


def fallen_log(m, u0, v0, y0, length, ang, log='minecraft:oak_log', moss=True, seed=6):
    rng = _rng(seed)
    ex, ez = math.cos(ang), math.sin(ang)
    axis = 'x' if abs(ex) > abs(ez) else 'z'
    for s in range(length):
        u, v = u0 + ex * s, v0 + ez * s
        m.put(u, y0, v, f'{log}[axis={axis}]')
        if moss and rng.random() < 0.4:
            m.put(u, y0 + 1, v, 'minecraft:moss_carpet')


def tower(m, u0, v0, y0, r=4, height=18, seed=7, mats=None, snow=True):
    """Ruined round watchtower."""
    rng = _rng(seed)
    mats = mats or ['minecraft:stone_bricks'] * 4 + ['minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cobblestone']
    for dy in range(-1, height):
        broken = height - 6 + 6 * (0.5 + 0.5 * np.sin(np.arange(64) * 0.4 + seed))
        for k in range(64):
            ang = k * 2 * math.pi / 64
            for rr in (r, r - 0.8):
                u = int(round(u0 + math.cos(ang) * rr))
                v = int(round(v0 + math.sin(ang) * rr))
                if dy > broken[k]:
                    continue
                window = dy in (5, 6, 11, 12) and k % 16 in (0, 1)
                door = dy in (0, 1, 2) and 30 <= k <= 34
                if window or door:
                    m.put(u, y0 + dy, v, 'minecraft:air')
                    continue
                m.put(u, y0 + dy, v, mats[rng.integers(len(mats))])
        if dy in (-1, 7) or dy == 13:
            for du in range(-r + 1, r):
                for dv in range(-r + 1, r):
                    if du * du + dv * dv < (r - 1) ** 2 and (dy == -1 or rng.random() < 0.8):
                        m.put(u0 + du, y0 + dy, v0 + dv, 'minecraft:spruce_planks' if dy > 0 else 'minecraft:stone_bricks')
    if snow:
        for (u, y, v), i in list(m.vox.items()):
            pass


def lantern_post(m, u, v, y, fence='minecraft:oak_fence', lantern='minecraft:lantern'):
    m.put(u, y, v, fence)
    m.put(u, y + 1, v, fence)
    m.put(u, y + 2, v, f'{lantern}[hanging=false,waterlogged=false]')


def lookout_tower(m, u0, v0, y0, height=13, seed=8, mats=None, ladder_side='south', parapet_dirs=('south', 'west')):
    """Solid stone lookout tower (5x5 core, 7x7 platform) with a ladder and a parapet on the given sides."""
    rng = _rng(seed)
    mats = mats or ['minecraft:stone_bricks'] * 4 + ['minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks']
    for dy in range(-3, height):
        for du in range(-2, 3):
            for dv in range(-2, 3):
                m.put(u0 + du, y0 + dy, v0 + dv, mats[rng.integers(len(mats))])
    top = y0 + height
    for du in range(-3, 4):
        for dv in range(-3, 4):
            m.put(u0 + du, top, v0 + dv, 'minecraft:chiseled_stone_bricks' if du == 0 and dv == 0 else 'minecraft:stone_bricks')
    for du in range(-3, 4):
        for dv in range(-3, 4):
            if max(abs(du), abs(dv)) != 3:
                continue
            side = ('north' if dv == -3 else 'south' if dv == 3 else None, 'west' if du == -3 else 'east' if du == 3 else None)
            if any(sd in parapet_dirs for sd in side if sd) and (du + dv) % 2 == 0:
                m.put(u0 + du, top + 1, v0 + dv, 'minecraft:stone_bricks')
    for (du, dv) in [(-3, -3), (3, 3), (-3, 3), (3, -3)]:
        m.put(u0 + du, top + 1, v0 + dv, 'minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
        m.put(u0 + du, top + 2, v0 + dv, 'minecraft:lantern[hanging=false,waterlogged=false]')
    lx, lz = DIR_VEC[ladder_side]
    for dy in range(0, height):
        m.put(u0 + lx * 3, y0 + dy, v0 + lz * 3, f'minecraft:ladder[facing={ladder_side},waterlogged=false]')
    m.put(u0 + lx * 3, top, v0 + lz * 3, 'minecraft:air')
