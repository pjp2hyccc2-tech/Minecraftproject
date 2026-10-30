"""World builder: chunked voxel buffer + palette + meta, exported to japan.bin / meta.json for TabiChunkGenerator."""
import gzip
import json
import math
import struct

import numpy as np

import terrain as T

YMIN, H = -64, 352          # stored y range: -64 .. 287
BASE_Y = 30                 # designed columns are written from here up (Java fills stone below the first run)


def varints(v):
    v = np.asarray(v, dtype=np.int64)
    assert v.size == 0 or (v.min() >= 0 and v.max() < 16384), (v.min(), v.max())
    two = v >= 128
    pos = np.arange(len(v)) + np.concatenate(([0], np.cumsum(two)[:-1])) if len(v) else np.zeros(0, dtype=np.int64)
    out = np.empty(len(v) + int(two.sum()), dtype=np.uint8)
    out[pos] = np.where(two, (v & 0x7F) | 0x80, v)
    out[pos[two] + 1] = v[two] >> 7
    return out.tobytes()


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


class World:
    def __init__(self):
        self.pal = ['minecraft:air']
        self.pidx = {'minecraft:air': 0}
        self.chunks = {}
        self.mods = []          # terrain modifiers f(X, Z, H) -> H
        self.ground_cache = {}
        self.meta = {'signs': [], 'npcs': [], 'areas': [], 'routes': [], 'cars': [], 'warps': [], 'viewpoints': [], 'checks': [], 'spawn': None}

    # ------------------------------------------------------------- palette
    def p(self, state):
        i = self.pidx.get(state)
        if i is None:
            i = len(self.pal)
            self.pal.append(state)
            self.pidx[state] = i
        return i

    # ------------------------------------------------------------- terrain
    def ground_np(self, X, Z):
        """modified terrain height (top solid block) for arrays"""
        Hn = T.height(X, Z).astype(np.float64)
        Hm = Hn.copy()
        for m in self.mods:
            Hm = m(X, Z, Hm, Hn)
        return np.floor(Hm + 0.5).astype(np.int64), Hn

    def ground(self, x, z):
        key = (x, z)
        g = self.ground_cache.get(key)
        if g is None:
            h, _ = self.ground_np(np.array([x]), np.array([z]))
            g = int(h[0])
            if len(self.ground_cache) < 2_000_000:
                self.ground_cache[key] = g
        return g

    def add_mod(self, f):
        assert not self.chunks, 'terrain modifiers must be registered before building'
        self.mods.append(f)

    # flatten helpers -------------------------------------------------------
    def flatten_rect(self, x1, z1, x2, z2, target, falloff):
        def f(X, Z, Hm, Hn):
            dx = np.maximum(np.maximum(x1 - X, 0), X - x2)
            dz = np.maximum(np.maximum(z1 - Z, 0), Z - z2)
            w = smooth(1 - np.sqrt(dx * dx + dz * dz) / falloff)
            return Hm + (target - Hm) * w
        self.add_mod(f)

    def flatten_ellipse(self, cx, cz, rx, rz, target, falloff):
        def f(X, Z, Hm, Hn):
            r = np.sqrt(((X - cx) / rx) ** 2 + ((Z - cz) / rz) ** 2)
            d = np.maximum(0, (r - 1) * min(rx, rz))
            w = smooth(1 - d / falloff)
            return Hm + (target - Hm) * w
        self.add_mod(f)

    def flatten_path(self, pts, half, falloff, targets=None, only_down=False, only_up=False):
        """pts [(x,z)], targets per point (or None = keep natural median); corridor of half-width `half`."""
        P = np.array(pts, dtype=np.float64)
        tg = np.array(targets, dtype=np.float64) if targets is not None else None
        reach = half + falloff + 2
        segbox = [(min(P[i][0], P[i + 1][0]) - reach, max(P[i][0], P[i + 1][0]) + reach,
                   min(P[i][1], P[i + 1][1]) - reach, max(P[i][1], P[i + 1][1]) + reach) for i in range(len(P) - 1)]

        def f(X, Z, Hm, Hn):
            best = np.full(X.shape, 1e18)
            tval = np.zeros(X.shape)
            xmin, xmax, zmin, zmax = X.min(), X.max(), Z.min(), Z.max()
            for i in range(len(P) - 1):
                bx1, bx2, bz1, bz2 = segbox[i]
                if xmax < bx1 or xmin > bx2 or zmax < bz1 or zmin > bz2:
                    continue
                ax, az = P[i]
                bx, bz = P[i + 1]
                vx, vz = bx - ax, bz - az
                L2 = vx * vx + vz * vz or 1e-9
                t = np.clip(((X - ax) * vx + (Z - az) * vz) / L2, 0, 1)
                px, pz = ax + t * vx, az + t * vz
                d2 = (X - px) ** 2 + (Z - pz) ** 2
                m = d2 < best
                best = np.where(m, d2, best)
                if tg is not None:
                    tval = np.where(m, tg[i] + (tg[i + 1] - tg[i]) * t, tval)
            d = np.maximum(0, np.sqrt(best) - half)
            w = smooth(1 - d / falloff)
            target = tval
            new = Hm + (target - Hm) * w
            if only_down:
                new = np.minimum(Hm, new)
            if only_up:
                new = np.maximum(Hm, new)
            return new
        self.add_mod(f)

    # ------------------------------------------------------------- chunks
    def chunk(self, cx, cz):
        a = self.chunks.get((cx, cz))
        if a is None:
            a = np.zeros((16, 16, H), dtype=np.uint16)
            self.chunks[(cx, cz)] = a
            self._fill_terrain(cx, cz, a)
        return a

    def ensure(self, x1, z1, x2, z2):
        for cx in range(x1 >> 4, (x2 >> 4) + 1):
            for cz in range(z1 >> 4, (z2 >> 4) + 1):
                self.chunk(cx, cz)

    def _fill_terrain(self, cx, cz, a):
        xs = np.arange(16) + cx * 16
        zs = np.arange(16) + cz * 16
        X, Z = np.meshgrid(xs, zs)            # [lz, lx]
        Hm, Hn = self.ground_np(X, Z)
        inl = T.inland(X, Z)
        surf = T.surface(X, Z, Hm)
        raised = (Hm > T.WATER_TOP) & (inl < 0)          # reclaimed land: grass, beach near the waterline
        surf = np.where(raised, np.where(Hm <= 63, 1, 0), surf)
        inl = np.where(raised, 1.0, inl)
        stone, dirt, grass = self.p('minecraft:stone'), self.p('minecraft:dirt'), self.p('minecraft:grass_block')
        sand, gravel, water = self.p('minecraft:sand'), self.p('minecraft:gravel'), self.p('minecraft:water')
        ys = np.arange(H) + YMIN
        for lz in range(16):
            for lx in range(16):
                h = int(Hm[lz, lx])
                col = a[lz, lx]
                col[BASE_Y - YMIN: max(BASE_Y, h - 3) - YMIN] = stone
                s = int(surf[lz, lx])
                sea = inl[lz, lx] < 0
                top = grass if s == 0 else sand if s in (1, 2) else gravel
                if sea and h < T.WATER_TOP:
                    top = gravel if s == 3 else sand
                fill = dirt if top == grass else top
                lo = max(BASE_Y, h - 3)
                col[lo - YMIN: h - YMIN] = fill
                col[h - YMIN] = top
                if sea and h < T.WATER_TOP:
                    col[h + 1 - YMIN: T.WATER_TOP + 1 - YMIN] = water

    # ------------------------------------------------------------- voxel access
    def set(self, x, y, z, state):
        if y < BASE_Y - 10 or y >= YMIN + H:
            return
        a = self.chunk(x >> 4, z >> 4)
        a[z & 15, x & 15, y - YMIN] = self.p(state) if isinstance(state, str) else state

    def get(self, x, y, z):
        a = self.chunk(x >> 4, z >> 4)
        return self.pal[a[z & 15, x & 15, y - YMIN]]

    def is_air(self, x, y, z):
        a = self.chunk(x >> 4, z >> 4)
        return a[z & 15, x & 15, y - YMIN] == 0

    def top(self, x, z):
        """y of the highest non-air block"""
        a = self.chunk(x >> 4, z >> 4)
        col = a[z & 15, x & 15]
        nz = np.flatnonzero(col)
        return int(nz[-1]) + YMIN if len(nz) else YMIN

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        x1, x2 = min(x1, x2), max(x1, x2)
        y1, y2 = min(y1, y2), max(y1, y2)
        z1, z2 = min(z1, z2), max(z1, z2)
        v = self.p(state) if isinstance(state, str) else state
        for cx in range(x1 >> 4, (x2 >> 4) + 1):
            for cz in range(z1 >> 4, (z2 >> 4) + 1):
                a = self.chunk(cx, cz)
                lx1, lx2 = max(x1, cx * 16) - cx * 16, min(x2, cx * 16 + 15) - cx * 16
                lz1, lz2 = max(z1, cz * 16) - cz * 16, min(z2, cz * 16 + 15) - cz * 16
                a[lz1:lz2 + 1, lx1:lx2 + 1, y1 - YMIN:y2 - YMIN + 1] = v

    def clear(self, x1, y1, z1, x2, y2, z2):
        self.fill(x1, y1, z1, x2, y2, z2, 0)

    def box(self, x1, y1, z1, x2, y2, z2, wall, floor=None, ceil=None, inside=0):
        """hollow box: walls, optional floor/ceiling, interior cleared"""
        self.fill(x1, y1, z1, x2, y2, z2, wall)
        self.fill(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1, inside)
        if floor:
            self.fill(x1, y1, z1, x2, y1, z2, floor)
        if ceil:
            self.fill(x1, y2, z1, x2, y2, z2, ceil)

    def column_to_ground(self, x, z, y_top, state):
        """fill from the ground (or base) up to y_top"""
        g = self.ground(x, z)
        self.fill(x, min(g, y_top), z, x, y_top, z, state)

    # ------------------------------------------------------------- meta
    def sign(self, x, y, z, block, facing, lines, style=None, span=1, color=None, glow=False, back=False):
        self.set(x, y, z, f'{block}[facing={facing}]')
        o = {'x': x, 'y': y, 'z': z, 'lines': lines, 'span': span}
        if style:
            o['style'] = style
        if color is not None:
            o['color'] = color
        if glow:
            o['glow'] = True
        if back:
            o['back'] = True
        self.meta['signs'].append(o)

    def npc(self, x, y, z, profile, skin, name='', bubbles='', mode='stand', yaw=0, radius=6, hold=None):
        o = {'x': x + 0.5, 'y': y, 'z': z + 0.5, 'yaw': yaw, 'profile': profile, 'skin': skin,
             'name': name, 'bubbles': bubbles, 'mode': mode, 'radius': radius}
        if hold:
            o['hold'] = hold
        self.meta['npcs'].append(o)

    def area(self, id, name, sub, box, journal='', build=False, night_journal=''):
        o = {'id': id, 'name': name, 'sub': sub, 'box': list(box), 'journal': journal}
        if build:
            o['build'] = True
        if night_journal:
            o['night_journal'] = night_journal
        self.meta['areas'].append(o)

    def check(self, x, y, z, what=''):
        self.meta['checks'].append({'pos': [x, y, z], 'block': self.get(x, y, z).split('[')[0], 'what': what})

    def viewpoint(self, name, x, y, z, yaw, pitch=0, time=6000, wait=160, **kw):
        o = {'name': name, 'pos': [x, y, z], 'yaw': yaw, 'pitch': pitch, 'time': time, 'wait': wait}
        o.update(kw)
        self.meta['viewpoints'].append(o)

    # ------------------------------------------------------------- export
    def export(self, bin_path, meta_path):
        keys = sorted(self.chunks)
        min_cx = min(k[0] for k in keys)
        max_cx = max(k[0] for k in keys)
        min_cz = min(k[1] for k in keys)
        max_cz = max(k[1] for k in keys)
        wC, hC = max_cx - min_cx + 1, max_cz - min_cz + 1
        offsets = np.full(wC * hC, -1, dtype=np.int64)
        parts = []
        pos = 0
        for (cx, cz) in keys:
            a = self.chunks[(cx, cz)].reshape(256, H)
            st = np.ones(a.shape, dtype=bool)
            st[:, 1:] = a[:, 1:] != a[:, :-1]
            cols, ys = np.nonzero(st)
            vals = a[cols, ys].astype(np.int64)
            nxt = np.empty_like(ys)
            nxt[:-1] = ys[1:]
            nxt[-1] = H
            same = np.zeros(len(cols), dtype=bool)
            same[:-1] = cols[1:] == cols[:-1]
            lens = np.where(same, nxt - ys, H - ys)
            keep = vals != 0
            cols, ys, lens, vals = cols[keep], ys[keep], lens[keep], vals[keep]
            counts = np.bincount(cols, minlength=256)
            before = np.concatenate(([0], np.cumsum(counts)[:-1]))
            col_off = np.arange(256) + 3 * before
            seq = np.empty(256 + 3 * len(vals), dtype=np.int64)
            seq[col_off] = counts
            k = np.arange(len(vals))
            p = col_off[cols] + 1 + 3 * (k - before[cols])
            seq[p] = ys
            seq[p + 1] = lens
            seq[p + 2] = vals
            b = varints(seq)
            offsets[(cz - min_cz) * wC + (cx - min_cx)] = pos
            parts.append(b)
            pos += len(b)
        data = b''.join(parts)
        out = bytearray()
        out += b'TABI'
        out += struct.pack('>i', 1)
        out += struct.pack('>i', len(self.pal))
        for s in self.pal:
            e = s.encode('utf-8')
            out += struct.pack('>H', len(e)) + e
        out += struct.pack('>iiii', min_cx, min_cz, wC, hC)
        out += offsets.astype('>i4').tobytes()
        out += struct.pack('>i', len(data))
        out += data
        with gzip.open(bin_path, 'wb', compresslevel=9) as f:
            f.write(bytes(out))
        with open(meta_path, 'w', encoding='utf-8') as f:
            json.dump(self.meta, f, ensure_ascii=False, separators=(',', ':'))
        return len(keys), len(data)


# ===================================================================== helpers for builders
DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
YAW = {'south': 0, 'west': 90, 'north': 180, 'east': -90}


def stairs(block, facing, half='bottom', shape='straight'):
    return f'{block}[facing={facing},half={half},shape={shape},waterlogged=false]'


def slab(block, typ='bottom'):
    return f'{block}[type={typ},waterlogged=false]'


def facing(block, f):
    return f'{block}[facing={f}]'


def pane(block, n=False, s=False, e=False, w=False):
    b = lambda v: 'true' if v else 'false'
    return f'{block}[east={b(e)},north={b(n)},south={b(s)},waterlogged=false,west={b(w)}]'


def axis(block, a):
    return f'{block}[axis={a}]'
