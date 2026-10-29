"""Reusable building pieces for the Kazami village generator (roofs, timber walls, trees, lanterns...)."""
import math

from vworld import h2, fbm, banner_nbt, sign_nbt, DYE

STAIR_MATS = {'deepslate_tile', 'deepslate_brick', 'cobbled_deepslate', 'polished_deepslate', 'stone_brick', 'mossy_stone_brick',
              'cobblestone', 'mossy_cobblestone', 'andesite', 'polished_andesite', 'stone', 'brick', 'mud_brick', 'spruce',
              'dark_oak', 'oak', 'birch', 'cherry', 'bamboo', 'bamboo_mosaic', 'mangrove', 'crimson', 'blackstone',
              'polished_blackstone_brick', 'granite', 'quartz', 'smooth_quartz', 'acacia', 'jungle'}
FULL_OF = {'deepslate_tile': 'deepslate_tiles', 'deepslate_brick': 'deepslate_bricks', 'stone_brick': 'stone_bricks',
           'mossy_stone_brick': 'mossy_stone_bricks', 'brick': 'bricks', 'mud_brick': 'mud_bricks', 'spruce': 'spruce_planks',
           'dark_oak': 'dark_oak_planks', 'oak': 'oak_planks', 'birch': 'birch_planks', 'cherry': 'cherry_planks',
           'bamboo': 'bamboo_planks', 'mangrove': 'mangrove_planks', 'crimson': 'crimson_planks', 'acacia': 'acacia_planks',
           'jungle': 'jungle_planks', 'polished_blackstone_brick': 'polished_blackstone_bricks', 'quartz': 'quartz_block'}


def full(mat):
    return FULL_OF.get(mat, mat)


class Kit:
    def __init__(self, world):
        self.w = world

    # ------------------------------------------------------------------ primitive block helpers
    def s(self, x, y, z, name, props=None, nbt=None):
        self.w.set(x, y, z, name, props, nbt)

    def st(self, x, y, z, mat, facing, half='bottom'):
        self.w.set(x, y, z, mat + '_stairs', {'facing': facing, 'half': half, 'shape': 'straight', 'waterlogged': 'false'})

    def slab(self, x, y, z, mat, t='bottom'):
        self.w.set(x, y, z, mat + '_slab', {'type': t, 'waterlogged': 'false'})

    def log(self, x, y, z, mat, axis='y'):
        self.w.set(x, y, z, mat, {'axis': axis})

    def leaf(self, x, y, z, mat='cherry_leaves', force=False):
        if force or self.w.is_empty(x, y, z):
            self.w.set(x, y, z, mat, {'distance': '1', 'persistent': 'true', 'waterlogged': 'false'})

    def lantern(self, x, y, z, hanging=False, soul=False):
        self.w.set(x, y, z, 'soul_lantern' if soul else 'lantern', {'hanging': 'true' if hanging else 'false', 'waterlogged': 'false'})

    def paper_lantern(self, x, y, z, color='red'):
        self.w.set(x, y, z, 'huntcraft:paper_lantern' if color == 'red' else 'huntcraft:paper_lantern_white')

    def fence(self, x, y, z, mat='spruce'):
        self.w.set(x, y, z, mat + '_fence')

    def wallb(self, x, y, z, mat='stone_brick'):
        self.w.set(x, y, z, mat + '_wall')

    def pane(self, x, y, z, mat='white_stained_glass_pane'):
        self.w.set(x, y, z, mat)

    def trapdoor(self, x, y, z, mat, facing, half='bottom', open_=False):
        self.w.set(x, y, z, mat + '_trapdoor', {'facing': facing, 'half': half, 'open': 'true' if open_ else 'false',
                                                'powered': 'false', 'waterlogged': 'false'})

    def door(self, x, y, z, facing, mat='spruce', hinge='left', open_=False):
        p = {'facing': facing, 'hinge': hinge, 'open': 'true' if open_ else 'false', 'powered': 'false'}
        self.w.set(x, y, z, mat + '_door', dict(p, half='lower'))
        self.w.set(x, y + 1, z, mat + '_door', dict(p, half='upper'))

    def bed(self, x, y, z, facing, color='red'):
        """Foot at (x,y,z); head one block toward `facing`."""
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        self.w.set(x, y, z, color + '_bed', {'facing': facing, 'occupied': 'false', 'part': 'foot'})
        self.w.set(x + dx, y, z + dz, color + '_bed', {'facing': facing, 'occupied': 'false', 'part': 'head'})

    def wall_banner(self, x, y, z, facing, color, patterns=()):
        self.w.set(x, y, z, color + '_wall_banner', {'facing': facing}, banner_nbt(patterns) if patterns else None)

    def banner(self, x, y, z, rotation, color, patterns=()):
        self.w.set(x, y, z, color + '_banner', {'rotation': str(rotation)}, banner_nbt(patterns) if patterns else None)

    def hanging_sign(self, x, y, z, rotation, lines, mat='spruce', glow=False, color='black'):
        self.w.set(x, y, z, mat + '_hanging_sign', {'rotation': str(rotation), 'attached': 'false', 'waterlogged': 'false'},
                   sign_nbt(lines, color=color, glow=glow))

    def wall_sign(self, x, y, z, facing, lines, mat='spruce', glow=False, color='black'):
        self.w.set(x, y, z, mat + '_wall_sign', {'facing': facing, 'waterlogged': 'false'}, sign_nbt(lines, color=color, glow=glow))

    def campfire(self, x, y, z, signal=False, lit=True, facing='north'):
        self.w.set(x, y, z, 'campfire', {'facing': facing, 'lit': 'true' if lit else 'false',
                                          'signal_fire': 'true' if signal else 'false', 'waterlogged': 'false'})

    def chain(self, x, y, z, axis='y'):
        self.w.set(x, y, z, 'chain', {'axis': axis, 'waterlogged': 'false'})

    def candle(self, x, y, z, n=1, color=None, lit=True):
        self.w.set(x, y, z, (color + '_candle') if color else 'candle', {'candles': str(n), 'lit': 'true' if lit else 'false', 'waterlogged': 'false'})

    def water(self, x, y, z, level=0):
        self.w.set(x, y, z, 'water', {'level': str(level)})

    def flower(self, x, y, z, name):
        tall = name in ('rose_bush', 'lilac', 'peony', 'tall_grass', 'large_fern', 'sunflower')
        if tall:
            self.w.set(x, y, z, name, {'half': 'lower'})
            self.w.set(x, y + 1, z, name, {'half': 'upper'})
        else:
            self.w.set(x, y, z, name)

    def petals(self, x, y, z, n=None, seed=0):
        n = n or 1 + int(h2(x, z, seed + 3) * 4) % 4
        f = ('north', 'east', 'south', 'west')[int(h2(x, z, seed + 9) * 4) % 4]
        self.w.set(x, y, z, 'pink_petals', {'facing': f, 'flower_amount': str(max(1, min(4, n)))})

    # ------------------------------------------------------------------ structure helpers
    def roof(self, x0, z0, x1, z1, y, mat, axis, hip=0, cap='slab', gable=None, gable_inset=1, soffit=None, ridge_mat=None):
        """Stepped (45 deg) roof over the eave rectangle [x0..x1]x[z0..z1]. axis = ridge direction.
        hip = number of hipped layers at the gable ends (irimoya), gable = fill material of the gable triangles.
        Returns the y of the ridge cap."""
        k = 0
        top = y
        while True:
            yy = y + k
            if axis == 'x':
                a0, a1 = z0 + k, z1 - k
                hx = min(k, hip)
                b0, b1 = x0 + hx, x1 - hx
            else:
                a0, a1 = x0 + k, x1 - k
                hz = min(k, hip)
                b0, b1 = z0 + hz, z1 - hz
            if a0 > a1 or b0 > b1:
                break

            def P(b, a):
                return (b, a) if axis == 'x' else (a, b)

            lo_face, hi_face = ('south', 'north') if axis == 'x' else ('east', 'west')
            end_lo, end_hi = ('east', 'west') if axis == 'x' else ('south', 'north')
            if a0 == a1:
                for b in range(b0, b1 + 1):
                    px, pz = P(b, a0)
                    self.s(px, yy, pz, full(ridge_mat or mat))
                    if cap == 'slab':
                        self.slab(px, yy + 1, pz, ridge_mat or mat)
                top = yy + 1
                break
            for b in range(b0, b1 + 1):
                px, pz = P(b, a0)
                self.st(px, yy, pz, mat, lo_face)
                px, pz = P(b, a1)
                self.st(px, yy, pz, mat, hi_face)
            if k < hip:
                for a in range(a0 + 1, a1):
                    px, pz = P(b0, a)
                    self.st(px, yy, pz, mat, end_lo)
                    px, pz = P(b1, a)
                    self.st(px, yy, pz, mat, end_hi)
            elif gable:
                for a in range(a0 + 1, a1):
                    for bb in (b0 + gable_inset, b1 - gable_inset):
                        px, pz = P(bb, a)
                        if self.w.is_empty(px, yy, pz):
                            self.s(px, yy, pz, gable)
            if k == 0 and soffit:
                for b in range(b0, b1 + 1):
                    px, pz = P(b, a0)
                    if self.w.is_empty(px, yy - 1, pz):
                        self.st(px, yy - 1, pz, soffit, hi_face, 'top')
                    px, pz = P(b, a1)
                    if self.w.is_empty(px, yy - 1, pz):
                        self.st(px, yy - 1, pz, soffit, lo_face, 'top')
                if hip > 0:
                    for a in range(a0, a1 + 1):
                        px, pz = P(b0, a)
                        if self.w.is_empty(px, yy - 1, pz):
                            self.st(px, yy - 1, pz, soffit, end_hi, 'top')
                        px, pz = P(b1, a)
                        if self.w.is_empty(px, yy - 1, pz):
                            self.st(px, yy - 1, pz, soffit, end_lo, 'top')
            if a0 + 1 == a1:
                if cap == 'slab':
                    for b in range(b0, b1 + 1):
                        for a in (a0, a1):
                            px, pz = P(b, a)
                            self.slab(px, yy + 1, pz, ridge_mat or mat)
                top = yy + 1
                break
            k += 1
        return top

    def timber_walls(self, x0, z0, x1, z1, y0, y1, fill='white_concrete', post='stripped_dark_oak_log', base='spruce_planks',
                     beam='stripped_dark_oak_log', step=4, skip=None):
        """Japanese timber frame walls on the rectangle outline (inclusive)."""
        skip = skip or set()

        def col(x, z):
            corner = x in (x0, x1) and z in (z0, z1)
            ispost = corner or (z in (z0, z1) and (x - x0) % step == 0) or (x in (x0, x1) and (z - z0) % step == 0)
            for y in range(y0, y1 + 1):
                if (x, y, z) in skip:
                    continue
                if ispost:
                    self.log(x, y, z, post, 'y')
                elif y == y1 and beam:
                    self.log(x, y, z, beam, 'x' if z in (z0, z1) else 'z')
                elif y == y0 and base:
                    self.s(x, y, z, base)
                else:
                    self.s(x, y, z, fill)

        for x in range(x0, x1 + 1):
            col(x, z0)
            col(x, z1)
        for z in range(z0 + 1, z1):
            col(x0, z)
            col(x1, z)

    def seal(self, x0, z0, x1, z1, y_start, mat='white_concrete', max_h=24):
        """Closes gaps between the top of the walls (rectangle outline) and the roof above."""
        cols = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)]
        cols += [(x0, z) for z in range(z0 + 1, z1)] + [(x1, z) for z in range(z0 + 1, z1)]
        for (x, z) in cols:
            # only seal if there is a roof somewhere above
            roof_y = None
            for y in range(y_start, y_start + max_h):
                if not self.w.is_empty(x, y, z):
                    roof_y = y
                    break
            if roof_y is None:
                continue
            for y in range(y_start, roof_y):
                self.s(x, y, z, mat)

    def floor(self, x0, z0, x1, z1, y, mat, props=None):
        self.w.fill(x0, y, z0, x1, y, z1, mat, props)

    def window(self, x0, y0, z0, x1, y1, z1, mat='white_stained_glass_pane'):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.s(x, y, z, mat)

    def opening(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.w.air(x, y, z)

    # ------------------------------------------------------------------ props
    def toro(self, x, y, z):
        """Stone lantern (ishi-doro)."""
        self.s(x, y, z, 'polished_andesite')
        self.wallb(x, y + 1, z, 'stone_brick')
        self.s(x, y + 2, z, 'chiseled_stone_bricks')
        self.lantern(x, y + 3, z)
        self.slab(x, y + 4, z, 'stone_brick')

    def big_toro(self, x, y, z):
        """3x3 based stone lantern for plazas."""
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                self.slab(x + dx, y, z + dz, 'stone_brick')
        self.s(x, y, z, 'polished_andesite')
        self.wallb(x, y + 1, z, 'andesite')
        self.wallb(x, y + 2, z, 'andesite')
        self.s(x, y + 3, z, 'chiseled_stone_bricks')
        for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
            self.st(x + dx, y + 3, z + dz, 'stone_brick', f, 'top')
        self.lantern(x, y + 4, z)
        for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
            self.st(x + dx, y + 5, z + dz, 'stone_brick', f)
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            self.slab(x + dx, y + 5, z + dz, 'stone_brick')
        self.s(x, y + 5, z, 'stone_bricks')
        self.slab(x, y + 6, z, 'stone_brick')

    def lamp_post(self, x, y, z, arm='east', h=3, mat='spruce'):
        for i in range(h):
            self.fence(x, y + i, z, mat)
        self.slab(x, y + h, z, mat)
        dx, dz = {'east': (1, 0), 'west': (-1, 0), 'north': (0, -1), 'south': (0, 1)}[arm]
        self.fence(x + dx, y + h - 1, z + dz, mat)
        self.lantern(x + dx, y + h - 2, z + dz, hanging=True)

    def table(self, x, y, z, mat='dark_oak'):
        self.fence(x, y, z, 'spruce')
        self.trapdoor(x, y + 1, z, mat, 'north', 'bottom')

    def stool(self, x, y, z, facing, mat='spruce'):
        self.st(x, y, z, mat, facing)

    def barrel(self, x, y, z, facing='up', open_=False):
        self.s(x, y, z, 'barrel', {'facing': facing, 'open': 'true' if open_ else 'false'})

    def pot(self, x, y, z, plant):
        self.s(x, y, z, 'potted_' + plant)

    # ------------------------------------------------------------------ nature
    def cherry_tree(self, x, y, z, size=1.0, seed=0, petals=True):
        hgt = int(3 + 3 * size + h2(x, z, seed) * 2)
        lean = h2(x, z, seed + 1) * math.tau
        lx, lz = math.cos(lean) * 0.18, math.sin(lean) * 0.18
        tx, tz = float(x), float(z)
        for i in range(hgt):
            self.log(int(round(tx)), y + i, int(round(tz)), 'cherry_log', 'y')
            if i > 1:
                tx += lx
                tz += lz
        top = (int(round(tx)), y + hgt, int(round(tz)))
        nb = 3 + int(h2(x, z, seed + 2) * 2)
        crowns = [top]
        for b in range(nb):
            ang = lean + b * math.tau / nb + (h2(x, z, seed + 10 + b) - 0.5)
            ln = 1.6 + 1.6 * size * (0.6 + 0.4 * h2(x, z, seed + 20 + b))
            sy = y + int(hgt * (0.55 + 0.35 * h2(x, z, seed + 30 + b)))
            bx, bz = float(top[0]), float(top[2])
            px = top[0] + (x - top[0]) * 0.3
            pz = top[2] + (z - top[2]) * 0.3
            bx, bz = px, pz
            steps = int(ln * 2)
            yy = float(sy)
            for s_ in range(steps):
                bx += math.cos(ang) * 0.5
                bz += math.sin(ang) * 0.5
                yy += 0.28
                axis = 'x' if abs(math.cos(ang)) > abs(math.sin(ang)) else 'z'
                self.log(int(round(bx)), int(round(yy)), int(round(bz)), 'cherry_log', axis)
            crowns.append((int(round(bx)), int(round(yy)) + 1, int(round(bz))))
        rad = 1.7 + 1.2 * size
        for (cx, cy, cz) in crowns:
            r = rad * (0.85 + 0.3 * h2(cx, cz, seed + 5))
            ri = int(math.ceil(r)) + 1
            for dx in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    for dy in range(-2, 3):
                        d = math.sqrt(dx * dx + dz * dz + (dy * 1.7 + (0.6 if dy < 0 else 0)) ** 2)
                        n = h2(cx + dx, cz + dz + dy * 31, seed + 7)
                        if d < r - 0.3 + n * 0.8:
                            self.leaf(cx + dx, cy + dy, cz + dz)
                    # hanging fringe
                    d2 = math.sqrt(dx * dx + dz * dz)
                    if r - 1.5 < d2 < r - 0.2 and h2(cx + dx, cz + dz, seed + 8) > 0.72:
                        self.leaf(cx + dx, cy - 3, cz + dz)
        if petals:
            pr = int(rad + 2)
            for dx in range(-pr, pr + 1):
                for dz in range(-pr, pr + 1):
                    gx, gz = x + dx, z + dz
                    if dx * dx + dz * dz <= pr * pr and h2(gx, gz, seed + 40) > 0.55:
                        if self.w.is_empty(gx, y, gz) and self.w.get(gx, y - 1, gz) in (None, 'minecraft:grass_block'):
                            self.petals(gx, y, gz, seed=seed)

    def spruce_tree(self, x, y, z, hgt=9, seed=0):
        for i in range(hgt):
            self.log(x, y + i, z, 'spruce_log', 'y')
        top = y + hgt
        self.leaf(x, top, z, 'spruce_leaves')
        self.leaf(x, top + 1, z, 'spruce_leaves')
        r = 1
        for i in range(hgt - 2, 1, -1):
            yy = y + i
            layer = hgt - i
            r = 1 + (layer // 2) % 3 if layer > 1 else 1
            r = min(r + layer // 5, 3)
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (1 if r >= 2 else 0) and not (dx == 0 and dz == 0):
                        if abs(dx) == r and abs(dz) == r:
                            continue
                        self.leaf(x + dx, yy, z + dz, 'spruce_leaves')

    def pine_bonsai(self, x, y, z, seed=0):
        """Small garden pine (niwaki)."""
        self.log(x, y, z, 'spruce_log', 'y')
        self.log(x, y + 1, z, 'spruce_log', 'y')
        self.log(x + 1, y + 2, z, 'spruce_log', 'x')
        for dx, dz, dy in ((0, 0, 2), (1, 0, 3), (2, 0, 3), (-1, 0, 2), (0, 1, 2), (0, -1, 2), (1, 1, 3), (1, -1, 3), (2, 1, 3),
                           (-1, 1, 3), (-1, -1, 3), (0, 0, 3), (2, -1, 3), (1, 0, 4), (0, 0, 4)):
            self.leaf(x + dx, y + dy, z + dz, 'spruce_leaves')
        for dx, dz in ((-2, 0), (-2, 1), (-1, 0), (-1, 1)):
            self.leaf(x + dx, y + 1, z + dz, 'azalea_leaves')

    def bamboo(self, x, y, z, hgt=8):
        for i in range(hgt):
            lv = 'large' if i >= hgt - 2 else ('small' if i >= hgt - 4 else 'none')
            self.s(x, y + i, z, 'bamboo', {'age': '1', 'leaves': lv, 'stage': '0'})

    def bush(self, x, y, z, mat='azalea_leaves', r=1):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r:
                    self.leaf(x + dx, y, z + dz, mat)
        self.leaf(x, y + 1, z, mat)
