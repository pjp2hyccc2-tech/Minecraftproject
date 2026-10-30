"""Shared building blocks for the world builders: paths, roads, roofs, trees, street furniture, generic buildings."""
import math
import random

from wb import stairs, slab, facing, pane, axis, DIRS, OPP, YAW

AIR = 'minecraft:air'
TB = lambda n: f'tabi:{n}'


# ===================================================================== geometry
def fillet_path(wps, radius, step=1.0):
    """polyline through waypoints with circular fillets of `radius` at the corners; returns dense [(x, z)]"""
    pts = [tuple(map(float, p)) for p in wps]
    out = [pts[0]]
    for i in range(1, len(pts) - 1):
        ax, az = pts[i - 1]
        bx, bz = pts[i]
        cx, cz = pts[i + 1]
        v1 = (ax - bx, az - bz)
        v2 = (cx - bx, cz - bz)
        l1 = math.hypot(*v1)
        l2 = math.hypot(*v2)
        if l1 < 1e-6 or l2 < 1e-6:
            continue
        u1 = (v1[0] / l1, v1[1] / l1)
        u2 = (v2[0] / l2, v2[1] / l2)
        cosang = max(-1.0, min(1.0, u1[0] * u2[0] + u1[1] * u2[1]))
        ang = math.acos(cosang)
        if abs(math.pi - ang) < 1e-3:
            out.append((bx, bz))
            continue
        t = radius / math.tan(ang / 2)
        t = min(t, l1 * 0.49, l2 * 0.49)
        r = t * math.tan(ang / 2)
        p1 = (bx + u1[0] * t, bz + u1[1] * t)
        p2 = (bx + u2[0] * t, bz + u2[1] * t)
        bis = (u1[0] + u2[0], u1[1] + u2[1])
        bl = math.hypot(*bis)
        d = r / math.sin(ang / 2)
        c = (bx + bis[0] / bl * d, bz + bis[1] / bl * d)
        a0 = math.atan2(p1[1] - c[1], p1[0] - c[0])
        a1 = math.atan2(p2[1] - c[1], p2[0] - c[0])
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        out.append(p1)
        n = max(2, int(abs(da) * r / 2))
        for k in range(1, n):
            a = a0 + da * k / n
            out.append((c[0] + r * math.cos(a), c[1] + r * math.sin(a)))
        out.append(p2)
    out.append(pts[-1])
    return resample(out, step)


def resample(pts, step=1.0):
    res = [pts[0]]
    acc = 0.0
    for i in range(1, len(pts)):
        ax, az = pts[i - 1]
        bx, bz = pts[i]
        seg = math.hypot(bx - ax, bz - az)
        if seg < 1e-9:
            continue
        pos = step - acc
        while pos <= seg:
            t = pos / seg
            res.append((ax + (bx - ax) * t, az + (bz - az) * t))
            pos += step
        acc = seg - (pos - step)
    if math.hypot(res[-1][0] - pts[-1][0], res[-1][1] - pts[-1][1]) > 1e-3:
        res.append(pts[-1])
    return res


def path_dirs(pts):
    """unit tangent for each dense point"""
    out = []
    for i in range(len(pts)):
        a = pts[max(0, i - 2)]
        b = pts[min(len(pts) - 1, i + 2)]
        dx, dz = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dx, dz) or 1
        out.append((dx / l, dz / l))
    return out


def dominant_facing(dx, dz):
    """facing whose model 'north-south' axis aligns with the direction: rails/lines run along it"""
    return 'east' if abs(dx) > abs(dz) else 'north'


# ===================================================================== stairs shape fixing (mirrors StairBlock.getStairsShape)
_CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
_CCW = {v: k for k, v in _CW.items()}


def _parse(state):
    if '[' not in state:
        return state, {}
    name, props = state[:-1].split('[', 1)
    d = dict(kv.split('=') for kv in props.split(','))
    return name, d


def _is_stairs(st):
    return 'stairs' in st.split('[')[0]


def fix_stairs(w, x1, y1, z1, x2, y2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                st = w.get(x, y, z)
                if not _is_stairs(st):
                    continue
                name, p = _parse(st)
                f, half = p['facing'], p['half']
                shape = 'straight'
                fx, fz = DIRS[f]
                front = w.get(x + fx, y, z + fz)
                done = False
                if _is_stairs(front):
                    _, fp = _parse(front)
                    d1 = fp['facing']
                    if fp['half'] == half and DIRS[d1][0] * fx + DIRS[d1][1] * fz == 0 and _can_take(w, x, y, z, f, half, OPP[d1]):
                        shape = 'outer_left' if d1 == _CCW[f] else 'outer_right'
                        done = True
                if not done:
                    back = w.get(x - fx, y, z - fz)
                    if _is_stairs(back):
                        _, bp = _parse(back)
                        d2 = bp['facing']
                        if bp['half'] == half and DIRS[d2][0] * fx + DIRS[d2][1] * fz == 0 and _can_take(w, x, y, z, f, half, d2):
                            shape = 'inner_left' if d2 == _CCW[f] else 'inner_right'
                if shape != p.get('shape'):
                    p['shape'] = shape
                    w.set(x, y, z, f'{name}[' + ','.join(f'{k}={v}' for k, v in sorted(p.items())) + ']')


def _can_take(w, x, y, z, f, half, side):
    dx, dz = DIRS[side]
    n = w.get(x + dx, y, z + dz)
    if not _is_stairs(n):
        return True
    _, np_ = _parse(n)
    return np_['facing'] != f or np_['half'] != half


# ===================================================================== roofs
def hip_roof(w, x1, z1, x2, z2, y, block='tabi:kawara', overhang=1, ridge_block=None, max_layers=99):
    """hipped roof (yosemune) over the rectangle; eaves at y"""
    st = block + '_stairs'
    x1, z1, x2, z2 = x1 - overhang, z1 - overhang, x2 + overhang, z2 + overhang
    k = 0
    while x1 + k <= x2 - k and z1 + k <= z2 - k and k < max_layers:
        a, b, c, d = x1 + k, z1 + k, x2 - k, z2 - k
        yy = y + k
        if a == c or b == d:
            w.fill(a, yy, b, c, yy, d, slab(block + '_slab', 'bottom') if not ridge_block else ridge_block)
            break
        for x in range(a, c + 1):
            w.set(x, yy, b, stairs(st, 'south'))
            w.set(x, yy, d, stairs(st, 'north'))
        for z in range(b + 1, d):
            w.set(a, yy, z, stairs(st, 'east'))
            w.set(c, yy, z, stairs(st, 'west'))
        # interior under the roof surface
        if c - a >= 2 and d - b >= 2:
            w.fill(a + 1, yy, b + 1, c - 1, yy, d - 1, block)
        k += 1
    fix_stairs(w, x1, y, z1, x2, y + k + 1, z2)
    return y + k


def gable_roof(w, x1, z1, x2, z2, y, ridge_axis='x', block='tabi:kawara', overhang=1, gable_wall=None):
    """gable roof (kirizuma); ridge along x or z; eaves at y. gable_wall fills the triangular ends."""
    st = block + '_stairs'
    x1o, z1o, x2o, z2o = x1 - overhang, z1 - overhang, x2 + overhang, z2 + overhang
    if ridge_axis == 'x':
        k = 0
        while z1o + k <= z2o - k:
            a, b = z1o + k, z2o - k
            yy = y + k
            if a == b:
                w.fill(x1o, yy, a, x2o, yy, a, slab(block + '_slab'))
                break
            for x in range(x1o, x2o + 1):
                w.set(x, yy, a, stairs(st, 'south'))
                w.set(x, yy, b, stairs(st, 'north'))
            if gable_wall and b - a >= 2:
                for z in range(max(a + 1, z1), min(b - 1, z2) + 1):
                    w.set(x1, yy, z, gable_wall)
                    w.set(x2, yy, z, gable_wall)
            if b - a == 1:
                break
            k += 1
        return y + k
    else:
        k = 0
        while x1o + k <= x2o - k:
            a, b = x1o + k, x2o - k
            yy = y + k
            if a == b:
                w.fill(a, yy, z1o, a, yy, z2o, slab(block + '_slab'))
                break
            for z in range(z1o, z2o + 1):
                w.set(a, yy, z, stairs(st, 'east'))
                w.set(b, yy, z, stairs(st, 'west'))
            if gable_wall and b - a >= 2:
                for x in range(max(a + 1, x1), min(b - 1, x2) + 1):
                    w.set(x, yy, z1, gable_wall)
                    w.set(x, yy, z2, gable_wall)
            if b - a == 1:
                break
            k += 1
        return y + k


def flat_roof(w, x1, z1, x2, z2, y, block='minecraft:smooth_stone', parapet='minecraft:light_gray_concrete'):
    w.fill(x1, y, z1, x2, y, z2, block)
    if parapet:
        for x in range(x1, x2 + 1):
            w.set(x, y + 1, z1, parapet)
            w.set(x, y + 1, z2, parapet)
        for z in range(z1, z2 + 1):
            w.set(x1, y + 1, z, parapet)
            w.set(x2, y + 1, z, parapet)


# ===================================================================== nature
def tree(w, x, y, z, kind='broad', rnd=None, size=1.0):
    r = rnd or random.Random(x * 73856093 ^ z * 19349663)
    if kind == 'pine':  # Japanese black pine: crooked trunk, flat layered canopy
        h = int(6 * size) + r.randrange(3)
        cx, cz = x, z
        for i in range(h):
            if i > 2 and r.random() < 0.35:
                cx += r.choice((-1, 0, 1))
                cz += r.choice((-1, 0, 1))
            w.set(cx, y + i, cz, 'minecraft:spruce_log')
        for layer in range(3):
            ly = y + h - 3 + layer * 2
            rad = 3 - layer
            ox, oz = cx + r.choice((-1, 0, 1)), cz + r.choice((-1, 0, 1))
            for dx in range(-rad, rad + 1):
                for dz in range(-rad, rad + 1):
                    if dx * dx + dz * dz <= rad * rad + 1 and r.random() < 0.92:
                        w.set(ox + dx, ly, oz + dz, 'minecraft:spruce_leaves[persistent=true]')
            w.set(ox, ly, oz, 'minecraft:spruce_log')
    elif kind == 'cedar':  # sugi: tall straight cone
        h = int(10 * size) + r.randrange(4)
        for i in range(h):
            w.set(x, y + i, z, 'minecraft:spruce_log')
        for i in range(3, h + 2):
            rad = max(0, int((h + 2 - i) / 3.2))
            for dx in range(-rad, rad + 1):
                for dz in range(-rad, rad + 1):
                    if abs(dx) + abs(dz) <= rad + (1 if rad > 1 else 0) and (dx or dz or i >= h):
                        w.set(x + dx, y + i, z + dz, 'minecraft:spruce_leaves[persistent=true]')
    elif kind == 'bamboo':
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if r.random() < 0.55:
                    hh = 6 + r.randrange(6)
                    for i in range(hh):
                        leaves = 'large' if i >= hh - 2 else ('small' if i >= hh - 4 else 'none')
                        w.set(x + dx, y + i, z + dz, f'minecraft:bamboo[age=1,leaves={leaves},stage=0]')
    else:  # broadleaf (keyaki / kusu)
        h = int(5 * size) + r.randrange(3)
        for i in range(h):
            w.set(x, y + i, z, 'minecraft:oak_log' if kind == 'broad' else 'minecraft:birch_log')
        leaf = 'minecraft:oak_leaves[persistent=true]' if kind == 'broad' else 'minecraft:jungle_leaves[persistent=true]'
        rad = int(3 * size) + 1
        for dy in range(-2, 3):
            rr = rad - abs(dy) // 2 - (1 if dy == 2 else 0)
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    if dx * dx + dz * dz + (dy * dy) * 0.6 <= rr * rr + 0.5 and r.random() < 0.9:
                        if w.is_air(x + dx, y + h + dy, z + dz):
                            w.set(x + dx, y + h + dy, z + dz, leaf)


def hedge(w, x1, z1, x2, z2, y, height=1, leaf='minecraft:azalea_leaves[persistent=true]'):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            for k in range(height):
                w.set(x, y + k, z, leaf)


# ===================================================================== street furniture
def utility_pole(w, x, y, z, f='north', height=8):
    for i in range(height):
        w.set(x, y + i, z, TB('utility_pole'))
    w.set(x, y + height, z, facing(TB('utility_pole_top'), f))


def street_lamp(w, x, y, z, f='north', height=5, kind='street_lamp'):
    if kind == 'gas_lamp':
        for i in range(height - 1):
            w.set(x, y + i, z, TB('lamp_pole'))
        w.set(x, y + height - 1, z, TB('gas_lamp'))
        return
    for i in range(height):
        w.set(x, y + i, z, TB('lamp_pole'))
    w.set(x, y + height, z, facing(TB('street_lamp'), f))


def vending(w, x, y, z, f):
    w.set(x, y, z, facing(TB('vending_machine'), f))
    w.set(x, y + 1, z, facing(TB('vending_machine_top'), f))


def bench(w, x, y, z, f, kind='park_bench'):
    w.set(x, y, z, facing(TB(kind), f))


# ===================================================================== roads
def _edge_facing(ox, oz):
    """facing that puts the road_line texture's stripe (east side in the north model) on the outer side"""
    if abs(ox) >= abs(oz):
        return 'north' if ox > 0 else 'south'
    return 'east' if oz > 0 else 'west'


def road(w, pts, y, width=6, center='white', edge=True, sidewalk=0, cut=5, base='minecraft:stone', curb='minecraft:smooth_stone',
         walk='tabi:sidewalk', support=True, y_at=None):
    """road surface top block at y (or y_at(i)) along dense path pts"""
    dirs = path_dirs(pts)
    half = width / 2.0
    placed = {}
    rank = {'center': 4, 'edge': 3, 'road': 2, 'walk': 1, 'curb': 0}
    for i, ((px, pz), (dx, dz)) in enumerate(zip(pts, dirs)):
        nx, nz = -dz, dx  # left normal
        yy = y if y_at is None else y_at(i)
        steps = int((half + sidewalk + 1) * 2) + 2
        for k in range(-steps, steps + 1):
            o = k * 0.5
            x = int(math.floor(px + nx * o + 0.5))
            z = int(math.floor(pz + nz * o + 0.5))
            ao = abs(o)
            if ao <= half:
                kind = 'road'
                if edge and half - 1.0 <= ao <= half - 0.5:
                    kind = 'edge'
                if center and ao < 0.5:
                    kind = 'center'
            elif ao <= half + sidewalk:
                kind = 'walk'
            elif sidewalk and ao <= half + sidewalk + 0.5:
                kind = 'curb'
            else:
                continue
            prev = placed.get((x, z))
            if prev is not None and rank[prev[0]] >= rank[kind]:
                continue
            sg = 1 if o >= 0 else -1
            placed[(x, z)] = (kind, dx, dz, nx * sg, nz * sg, yy)
    for (x, z), (kind, dx, dz, ox, oz, yy) in placed.items():
        f = dominant_facing(dx, dz)
        if kind == 'center':
            st = facing(TB('road_center_yellow' if center == 'yellow' else 'road_center'), f)
        elif kind == 'edge':
            st = facing(TB('road_line'), _edge_facing(ox, oz))
        elif kind == 'road':
            st = TB('asphalt')
        elif kind == 'walk':
            st = walk
        else:
            st = curb
        yf = float(yy)
        cell = int(math.floor(yf))
        half_step = (yf - cell) >= 0.5
        if support:
            g = w.ground(x, z)
            if g < cell - 1:
                w.fill(x, g, z, x, cell - 1, z, base)
        if cut:
            w.clear(x, cell + 1, z, x, cell + cut, z)
        if half_step and kind in ('road', 'edge', 'center'):
            w.set(x, cell, z, TB('asphalt'))
            w.set(x, cell + 1, z, slab(TB('asphalt_slab')))
        elif half_step:
            w.set(x, cell, z, st)
            w.set(x, cell + 1, z, slab('minecraft:smooth_stone_slab'))
        else:
            w.set(x, cell, z, st)
    return placed


def crosswalk(w, x1, z1, x2, z2, y, f):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            w.set(x, y, z, facing(TB('crosswalk'), f))


# ===================================================================== generic buildings
def windows_row(w, x1, z1, x2, z2, y, block, every=2, offset=0):
    """punch windows along a wall line (x or z constant)"""
    if x1 == x2:
        for z in range(min(z1, z2), max(z1, z2) + 1):
            if (z + offset) % every == 0:
                w.set(x1, y, z, block)
    else:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            if (x + offset) % every == 0:
                w.set(x, y, z1, block)


def office(w, x1, z1, x2, z2, y, floors, rnd, wall=None, lit_ratio=0.55, roof=True, ground_shop=False):
    """city building with window grid; lit windows glow at night"""
    wall = wall or rnd.choice(['tabi:office_wall', 'minecraft:light_gray_concrete', 'minecraft:white_concrete', 'tabi:tile_white',
                               'minecraft:smooth_sandstone', 'tabi:tile_brown', 'minecraft:gray_concrete'])
    fh = 3
    top = y + floors * fh
    w.fill(x1, y, z1, x2, top, z2, wall)
    w.fill(x1 + 1, y + 1, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR)
    for fl in range(floors):
        fy = y + fl * fh
        w.fill(x1 + 1, fy, z1 + 1, x2 - 1, fy, z2 - 1, 'minecraft:smooth_stone')
        for wy in (fy + 1, fy + 2):
            for x in range(x1 + 1, x2):
                for z in (z1, z2):
                    if (x - x1) % 3 != 0:
                        w.set(x, wy, z, 'tabi:window_lit' if rnd.random() < lit_ratio else 'tabi:window_dark')
            for z in range(z1 + 1, z2):
                for x in (x1, x2):
                    if (z - z1) % 3 != 0:
                        w.set(x, wy, z, 'tabi:window_lit_cool' if rnd.random() < lit_ratio else 'tabi:window_dark')
    if roof:
        w.fill(x1, top, z1, x2, top, z2, 'minecraft:smooth_stone')
        for x in range(x1, x2 + 1):
            w.set(x, top + 1, z1, wall)
            w.set(x, top + 1, z2, wall)
        for z in range(z1, z2 + 1):
            w.set(x1, top + 1, z, wall)
            w.set(x2, top + 1, z, wall)
        if rnd.random() < 0.5 and x2 - x1 > 6 and z2 - z1 > 6:
            w.fill(x1 + 2, top + 1, z1 + 2, x1 + 4, top + 2, z1 + 4, 'minecraft:light_gray_concrete')
        if rnd.random() < 0.3:
            w.set((x1 + x2) // 2, top + 2, (z1 + z2) // 2, 'minecraft:redstone_lamp[lit=true]')
    return top


def jp_house(w, x1, z1, x2, z2, y, rnd, door='south', two_story=None, roof='kawara'):
    """ordinary Japanese house: plaster or siding walls, aluminium windows, tiled hip roof"""
    wall = rnd.choice(['tabi:shikkui', 'minecraft:white_terracotta', 'tabi:clapboard_white', 'minecraft:smooth_sandstone', 'tabi:yakisugi'])
    two = two_story if two_story is not None else (x2 - x1 >= 7 and rnd.random() < 0.6)
    h = 8 if two else 4
    w.fill(x1, y, z1, x2, y, z2, 'minecraft:stone_bricks')
    w.fill(x1, y + 1, z1, x2, y + h, z2, wall)
    w.fill(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h - 1, z2 - 1, AIR)
    w.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, 'tabi:hinoki')
    if two:
        w.fill(x1 + 1, y + 4, z1 + 1, x2 - 1, y + 4, z2 - 1, 'tabi:hinoki')
    for x in (x1, x2):
        w.fill(x, y + 1, z1, x, y + h, z1, 'tabi:hashira[axis=y]')
        w.fill(x, y + 1, z2, x, y + h, z2, 'tabi:hashira[axis=y]')
    for lvl in ([y + 2] + ([y + 6] if two else [])):
        for x in range(x1 + 2, x2 - 1, 3):
            w.set(x, lvl, z1, pane('minecraft:glass_pane', e=True, w=True))
            w.set(x, lvl, z2, pane('minecraft:glass_pane', e=True, w=True))
        for z in range(z1 + 2, z2 - 1, 3):
            w.set(x1, lvl, z, pane('minecraft:glass_pane', n=True, s=True))
            w.set(x2, lvl, z, pane('minecraft:glass_pane', n=True, s=True))
    dx, dz = DIRS[door]
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    if door in ('south', 'north'):
        dzp = z2 if door == 'south' else z1
        w.set(mx, y + 1, dzp, f'minecraft:spruce_door[facing={OPP[door]},half=lower,hinge=left,open=false,powered=false]')
        w.set(mx, y + 2, dzp, f'minecraft:spruce_door[facing={OPP[door]},half=upper,hinge=left,open=false,powered=false]')
        w.set(mx, y + 3, dzp + dz, slab('tabi:kawara_slab'))
    else:
        dxp = x2 if door == 'east' else x1
        w.set(dxp, y + 1, mz, f'minecraft:spruce_door[facing={OPP[door]},half=lower,hinge=left,open=false,powered=false]')
        w.set(dxp, y + 2, mz, f'minecraft:spruce_door[facing={OPP[door]},half=upper,hinge=left,open=false,powered=false]')
    hip_roof(w, x1, z1, x2, z2, y + h + 1, 'tabi:kawara' if roof == 'kawara' else roof, overhang=1)
    return y + h


def fence_line(w, pts, y, block='minecraft:spruce_fence'):
    for (x, z) in pts:
        w.set(x, y, z, block)


# ===================================================================== connection fixing for panes / fences
_PANE_HINTS = ('_pane', 'iron_bars', 'tabi:shoji', 'tabi:fusuma', 'tabi:bamboo_fence', 'tabi:sash_window', 'tabi:love_lock_fence', 'tabi:railing')
_NONSOLID = ('air', 'stairs', 'slab', 'leaves', 'door', 'lantern', 'torch', 'carpet', 'water', 'bamboo', 'grass[', 'short_grass', 'flower',
             'tabi:chochin', 'tabi:noren', 'tabi:sudare', 'tabi:street_lamp', 'tabi:gas_lamp', 'tabi:lamp_pole', 'tabi:sign_pole', 'tabi:utility_pole',
             'tabi:weathercock', 'tabi:furin', 'tabi:jizo', 'tabi:stone_lantern', 'tabi:mailbox', 'tabi:shop_sign', 'tabi:gate_plaque', 'tabi:neon_sign',
             'tabi:station_sign', 'tabi:info_board', 'tabi:bus_stop_sign', 'tabi:track', 'tabi:guardrail', 'rail', 'button', 'sign', 'tabi:kakejiku',
             'tabi:senpuki', 'tabi:phone', 'tabi:zabuton', 'tabi:futon', 'tabi:kakebuton', 'tabi:chabudai', 'tabi:park_bench', 'tabi:station_bench',
             'tabi:kaisatsu', 'tabi:steamer', 'tabi:elevator_button', 'chain', 'flower_pot', 'tabi:rice_plant', 'crop')


def _kind(st):
    name = st.split('[')[0]
    if any(h in name for h in _PANE_HINTS):
        return 'pane'
    if name.endswith('_fence') or name.endswith('fence_gate'):
        return 'fence'
    if name.endswith('_wall') and 'minecraft:' in name:
        return 'wall'
    return None


def _solid(st):
    return not any(h in st for h in _NONSOLID)


def fix_connections(w, x1, y1, z1, x2, y2, z2):
    import numpy as np
    targets = [i for i, s in enumerate(w.pal) if _kind(s)]
    if not targets:
        return
    tset = np.array(targets, dtype=np.uint16)
    for cx in range(min(x1, x2) >> 4, (max(x1, x2) >> 4) + 1):
        for cz in range(min(z1, z2) >> 4, (max(z1, z2) >> 4) + 1):
            if (cx, cz) not in w.chunks:
                continue
            a = w.chunks[(cx, cz)]
            hits = np.argwhere(np.isin(a, tset))
            for lz, lx, yi in hits:
                x, z, y = cx * 16 + int(lx), cz * 16 + int(lz), int(yi) - 64
                if not (min(x1, x2) <= x <= max(x1, x2) and min(z1, z2) <= z <= max(z1, z2) and min(y1, y2) <= y <= max(y1, y2)):
                    continue
                st = w.get(x, y, z)
                k = _kind(st)
                name = st.split('[')[0]
                con = {}
                for d, (dx, dz) in DIRS.items():
                    n = w.get(x + dx, y, z + dz)
                    nk = _kind(n)
                    if k == 'pane':
                        ok = nk == 'pane' or (nk is None and _solid(n))
                    elif k == 'fence':
                        ok = nk == 'fence' or (nk is None and _solid(n))
                    else:
                        ok = nk == 'wall' or (nk is None and _solid(n))
                    con[d] = ok
                if k == 'wall':
                    props = {d: ('low' if con[d] else 'none') for d in con}
                    props['up'] = 'true' if not ((con['north'] and con['south'] and not con['east'] and not con['west']) or
                                                 (con['east'] and con['west'] and not con['north'] and not con['south'])) else 'false'
                    props['waterlogged'] = 'false'
                else:
                    props = {d: ('true' if con[d] else 'false') for d in con}
                    props['waterlogged'] = 'false'
                w.set(x, y, z, name + '[' + ','.join(f'{kk}={vv}' for kk, vv in sorted(props.items())) + ']')
