#!/usr/bin/env python3
"""HuntCraft quest-map designer framework.

A quest map is a 512x512 block tile (u = east, v = south; region centre = (256, 256)) designed here with numpy and
exported as gzipped NBT to src/main/resources/data/huntcraft/quest_maps/<id>.nbt. The Java QuestChunkGenerator
repeats the tile every 2048 blocks (one copy per running quest) and fills each column from these planes:

  height  top solid block y            zone   area number (0 = boundary wall)
  biome   index into the map biome list top/sub/rock/ctop  surface palette indices
  fluid   0 none, 1 water, 2 lava, 3 water frozen at its surface      level  fluid surface y
  clo/chi cave carve range (air from clo to chi, 0 = none)

plus sparse voxels (camp, landmarks, decoration, gathering points) and area / camp metadata.
Previews (top-down shaded map) go to tools/preview/, the in-game map texture to assets/.../gui/quest_map/.
"""
import gzip
import math
import os
import struct
from collections import deque

import numpy as np
from scipy import ndimage

T = 512
LO, HI = 32, 480                     # playable range (QuestRegions.HALF = 224 around 256)
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'huntcraft', 'quest_maps')
OUT_TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft', 'textures', 'gui', 'quest_map')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')

V, U = np.mgrid[0:T, 0:T].astype(np.float64)
FLUID_NONE, FLUID_WATER, FLUID_LAVA, FLUID_FROZEN = 0, 1, 2, 3
GATHER_KINDS = {'herb': 0, 'mushroom': 1, 'bug': 2, 'bone': 3, 'mining': 4}
CLIMATE = {'none': 0, 'cold': 1, 'hot': 2, 'safe': 3}


# ====================================================================== noise
def fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


class Perlin:
    def __init__(self, seed):
        rng = np.random.default_rng(seed)
        perm = rng.permutation(256)
        self.p = np.concatenate([perm, perm])
        a = rng.random(256) * 2 * np.pi
        self.g = np.stack([np.cos(a), np.sin(a)], -1)

    def __call__(self, x, z):
        xi = np.floor(x).astype(np.int64)
        zi = np.floor(z).astype(np.int64)
        xf = x - xi
        zf = z - zi
        xi &= 255
        zi &= 255
        p = self.p

        def gr(ix, iz, dx, dz):
            h = p[p[ix] + iz] & 255
            g = self.g[h]
            return g[..., 0] * dx + g[..., 1] * dz

        n00 = gr(xi, zi, xf, zf)
        n10 = gr(xi + 1, zi, xf - 1, zf)
        n01 = gr(xi, zi + 1, xf, zf - 1)
        n11 = gr(xi + 1, zi + 1, xf - 1, zf - 1)
        u = fade(xf)
        w = fade(zf)
        a = n00 + u * (n10 - n00)
        b = n01 + u * (n11 - n01)
        return (a + w * (b - a)) * 1.4142


def fbm(seed, scale, octaves=4, pers=0.5, lac=2.0, x=None, z=None):
    """Fractal noise, roughly in [-1, 1] (std ~0.3)."""
    x = U if x is None else x
    z = V if z is None else z
    total = np.zeros(np.shape(x))
    amp, norm, f = 1.0, 0.0, 1.0 / scale
    for o in range(octaves):
        n = Perlin(seed * 7919 + o * 104729)
        total += amp * n(x * f + o * 17.31, z * f + o * 9.17)
        norm += amp
        amp *= pers
        f *= lac
    return total / norm


def ridged(seed, scale, octaves=4):
    return 1.0 - np.abs(fbm(seed, scale, octaves)) * 1.6


# ====================================================================== geometry helpers
def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def dist(cx, cz):
    return np.hypot(U - cx, V - cz)


def ellipse(cx, cz, rx, rz, ang=0.0):
    """Normalised elliptical distance (1.0 on the rim)."""
    c, s = math.cos(ang), math.sin(ang)
    du, dv = U - cx, V - cz
    a = (du * c + dv * s) / rx
    b = (-du * s + dv * c) / rz
    return np.hypot(a, b)


def polyline(pts):
    """Distance to a polyline and arclength parameter of the closest point. Returns (d, s, total_length)."""
    best = np.full(U.shape, 1e9)
    bs = np.zeros(U.shape)
    acc = 0.0
    for (ax, az), (bx, bz) in zip(pts[:-1], pts[1:]):
        dx, dz = bx - ax, bz - az
        L2 = dx * dx + dz * dz
        L = math.sqrt(L2)
        t = np.clip(((U - ax) * dx + (V - az) * dz) / max(L2, 1e-9), 0.0, 1.0)
        d = np.hypot(U - (ax + t * dx), V - (az + t * dz))
        m = d < best
        best = np.where(m, d, best)
        bs = np.where(m, acc + t * L, bs)
        acc += L
    return best, bs, acc


def smooth_path(pts, n=8):
    """Catmull-Rom resampling of a control polyline (n points per segment)."""
    if len(pts) < 3:
        return pts
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)))
    out.append(pts[-1])
    return out


def edge_dist():
    """Distance (blocks) from the playable boundary, negative outside."""
    return np.minimum(np.minimum(U - LO, HI - U), np.minimum(V - LO, HI - V))


# ====================================================================== block states
def parse_state(s):
    """'minecraft:oak_log[axis=y]' -> (name, {props})"""
    if '[' in s:
        name, rest = s.split('[', 1)
        props = {}
        for kv in rest.rstrip(']').split(','):
            if kv:
                k, v = kv.split('=')
                props[k.strip()] = v.strip()
        return name, props
    return s, {}


def state_str(name, props):
    if not props:
        return name
    return name + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'


class Palette:
    def __init__(self):
        self.entries = []
        self.index = {}

    def idx(self, s):
        name, props = parse_state(s)
        key = state_str(name, props)
        if key not in self.index:
            self.index[key] = len(self.entries)
            self.entries.append((name, props))
        return self.index[key]

    def name(self, i):
        return self.entries[i][0]

    def nbt(self):
        out = []
        for name, props in self.entries:
            e = {'Name': name}
            if props:
                e['Properties'] = dict(props)
            out.append(e)
        return out


HORIZ = ['north', 'east', 'south', 'west']
DIR_VEC = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}


def rotate_dir(d, steps):
    if d not in HORIZ:
        return d
    return HORIZ[(HORIZ.index(d) + steps) % 4]


def rotate_state(s, steps):
    """Rotates facing / axis / connection properties by steps * 90 degrees clockwise (seen from above)."""
    if steps % 4 == 0:
        return s
    name, props = parse_state(s)
    p = dict(props)
    if 'facing' in p:
        p['facing'] = rotate_dir(p['facing'], steps)
    if 'axis' in p and p['axis'] in ('x', 'z') and steps % 2 == 1:
        p['axis'] = 'z' if p['axis'] == 'x' else 'x'
    if any(k in p for k in HORIZ):
        old = {k: p.get(k) for k in HORIZ}
        for k in HORIZ:
            if old[k] is not None:
                p[rotate_dir(k, steps)] = old[k]
    return state_str(name, p)


# ====================================================================== NBT writer (byte/int arrays supported)
class ByteArray(bytes):
    pass


class IntArray(list):
    pass


class Byte(int):
    pass


def _tag_type(v):
    if isinstance(v, ByteArray):
        return 7
    if isinstance(v, IntArray):
        return 11
    if isinstance(v, (Byte, bool)):
        return 1
    if isinstance(v, (int, np.integer)):
        return 3
    if isinstance(v, float):
        return 6
    if isinstance(v, str):
        return 8
    if isinstance(v, list):
        return 9
    if isinstance(v, dict):
        return 10
    raise TypeError(type(v))


def _payload(out, t, v):
    if t == 1:
        out += struct.pack('>b', int(v))
    elif t == 3:
        out += struct.pack('>i', int(v))
    elif t == 6:
        out += struct.pack('>d', float(v))
    elif t == 7:
        out += struct.pack('>i', len(v)) + bytes(v)
    elif t == 8:
        b = v.encode('utf-8')
        out += struct.pack('>H', len(b)) + b
    elif t == 9:
        et = _tag_type(v[0]) if v else 0
        out += struct.pack('>bi', et, len(v))
        for e in v:
            _payload(out, et, e)
    elif t == 10:
        for k, e in v.items():
            et = _tag_type(e)
            kb = k.encode('utf-8')
            out += struct.pack('>bH', et, len(kb)) + kb
            _payload(out, et, e)
        out += b'\x00'
    elif t == 11:
        out += struct.pack('>i', len(v))
        out += np.asarray(v, dtype='>i4').tobytes()


def write_nbt(path, root):
    out = bytearray()
    out += struct.pack('>bH', 10, 0)
    _payload(out, 10, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(gzip.compress(bytes(out), 9))


# ====================================================================== preview colours (approximate top colours)
COLORS = {
    'grass_block': (96, 156, 64), 'dirt': (134, 96, 67), 'coarse_dirt': (119, 85, 59), 'podzol': (106, 76, 40),
    'dirt_path': (148, 122, 65), 'rooted_dirt': (144, 103, 76), 'mud': (60, 57, 60), 'moss_block': (89, 109, 45),
    'stone': (125, 125, 125), 'andesite': (136, 136, 137), 'cobblestone': (122, 121, 122), 'mossy_cobblestone': (110, 118, 94),
    'gravel': (131, 127, 126), 'sand': (219, 207, 163), 'clay': (160, 166, 179), 'snow_block': (249, 254, 254),
    'snow': (249, 254, 254), 'powder_snow': (248, 253, 253), 'ice': (145, 183, 253), 'packed_ice': (141, 180, 250),
    'blue_ice': (116, 167, 253), 'calcite': (223, 224, 220), 'tuff': (108, 109, 102), 'basalt': (80, 81, 86),
    'smooth_basalt': (72, 72, 78), 'blackstone': (42, 36, 41), 'magma_block': (142, 63, 31), 'obsidian': (15, 10, 24),
    'netherrack': (97, 38, 38), 'red_sand': (190, 102, 33), 'terracotta': (152, 94, 67), 'water': (63, 118, 228),
    'lava': (207, 92, 20), 'oak_leaves': (60, 120, 40), 'birch_leaves': (110, 150, 70), 'spruce_leaves': (40, 80, 50),
    'dark_oak_leaves': (40, 90, 30), 'jungle_leaves': (50, 125, 30), 'oak_log': (109, 85, 50), 'spruce_log': (58, 37, 16),
    'stone_bricks': (122, 121, 122), 'mossy_stone_bricks': (115, 121, 105), 'cracked_stone_bricks': (118, 117, 118),
    'bone_block': (229, 225, 207), 'deepslate': (80, 80, 82), 'white_wool': (234, 236, 236), 'blue_wool': (53, 57, 157),
    'red_wool': (161, 39, 34), 'gilded_blackstone': (60, 44, 36), 'polished_blackstone_bricks': (48, 42, 50),
    'mycelium': (111, 99, 105), 'grass': (96, 156, 64), 'tall_grass': (96, 156, 64), 'crying_obsidian': (32, 10, 60),
}


def color_of(name):
    n = name.split(':', 1)[-1]
    if n in COLORS:
        return COLORS[n]
    for k, c in COLORS.items():
        if k in n:
            return c
    if 'huntcraft:' in name:
        return (255, 60, 220)
    return (150, 150, 150)


# ====================================================================== the map
class QMap:
    def __init__(self, mid, seed, base):
        self.id = mid
        self.seed = seed
        self.h = np.full((T, T), float(base))
        self.zone = np.zeros((T, T), np.uint8)
        self.biome = np.zeros((T, T), np.uint8)
        self.top = np.zeros((T, T), np.int32)
        self.sub = np.zeros((T, T), np.int32)
        self.rock = np.zeros((T, T), np.int32)
        self.ctop = np.zeros((T, T), np.int32)
        self.fluid = np.zeros((T, T), np.uint8)
        self.level = np.zeros((T, T), np.int32)
        self.clo = np.zeros((T, T), np.int32)
        self.chi = np.zeros((T, T), np.int32)
        self.spal = Palette()
        self.vpal = Palette()
        self.vox = {}
        self.gather = []
        self.areas = {}
        self.biomes = []
        self.camp = None
        self.outside = None
        self.sub_depth = 3
        self.sea = 62
        self.cave_biome = -1
        self.edge_biome = 0
        self.sites = []
        self.hi = None

    # ------------------------------------------------------------------ setup
    def set_biomes(self, ids):
        self.biomes = list(ids)

    def b(self, bid):
        return self.biomes.index(bid)

    def S(self, s):
        return self.spal.idx(s)

    def area(self, n, key, x, z, r=40, large=False, climate='none', smalls=()):
        self.areas[n] = dict(n=n, key=key, x=int(x), z=int(z), r=int(r), large=large, climate=CLIMATE[climate], smalls=list(smalls))

    def assign_zones(self, sites, jitter=14.0, seed=0):
        """Weighted, noise-perturbed Voronoi over (area, x, z, weight) sites inside the playable square."""
        nx = fbm(self.seed + 900 + seed, 60, 3) * jitter
        nz = fbm(self.seed + 901 + seed, 60, 3) * jitter
        best = np.full((T, T), 1e9)
        zone = np.zeros((T, T), np.uint8)
        for (n, x, z, w) in sites:
            d = np.hypot(U + nx - x, V + nz - z) * w
            m = d < best
            best = np.where(m, d, best)
            zone = np.where(m, n, zone)
        inside = (U >= LO) & (U <= HI) & (V >= LO) & (V <= HI)
        self.zone = np.where(inside, zone, 0).astype(np.uint8)
        self.sites = sites
        # strongly jittered copy for biomes / materials (organic boundaries)
        nx = fbm(self.seed + 910 + seed, 48, 4) * 64 + fbm(self.seed + 912, 12, 2) * 9
        nz = fbm(self.seed + 911 + seed, 48, 4) * 64 + fbm(self.seed + 913, 12, 2) * 9
        best = np.full((T, T), 1e9)
        zj = np.zeros((T, T), np.uint8)
        for (n, x, z, w) in sites:
            d = np.hypot(U + nx - x, V + nz - z) * w
            m = d < best
            best = np.where(m, d, best)
            zj = np.where(m, n, zj)
        self.zj = zj

    # ------------------------------------------------------------------ voxels
    def put(self, u, y, v, s, replace=True):
        u, y, v = int(round(u)), int(round(y)), int(round(v))
        if not (0 <= u < T and 0 <= v < T and 1 <= y < 320):
            return
        if not replace and (u, y, v) in self.vox:
            return
        self.vox[(u, y, v)] = self.vpal.idx(s)

    def get(self, u, y, v):
        i = self.vox.get((int(u), int(y), int(v)))
        return None if i is None else self.vpal.name(i)

    def fill(self, u0, y0, v0, u1, y1, v1, s, replace=True):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for v in range(min(v0, v1), max(v0, v1) + 1):
                    self.put(u, y, v, s, replace)

    def ground(self, u, v):
        """Standing y (first air block) at u, v, ignoring voxels."""
        return int(self.hi[int(v), int(u)]) + 1 if self.hi is not None else int(round(self.h[int(v), int(u)])) + 1

    def solid_at(self, u, y, v):
        """Is there a solid block (terrain or voxel) at the position?"""
        n = self.get(u, y, v)
        if n is not None:
            return n != 'minecraft:air' and not any(k in n for k in ('water', 'lava', 'grass', 'flower', 'lantern', 'torch'))
        u, v = int(u), int(v)
        if not (0 <= u < T and 0 <= v < T):
            return False
        h = self.hi[v, u]
        if self.clo[v, u] > 0 and self.clo[v, u] <= y <= self.chi[v, u]:
            return False
        return y <= h

    def gather_point(self, u, v, kind, y=None):
        u, v = int(u), int(v)
        if y is None:
            y = self.ground(u, v)
        self.gather.append((u, int(y), v, GATHER_KINDS[kind]))
        self.put(u, y, v, f'huntcraft:gather_{kind}[charges=3]')

    def fix_fences(self):
        """Sets fence connection properties (worldgen does not run neighbour updates)."""
        fences = [k for k, i in self.vox.items() if self.vpal.name(i).endswith('_fence')]
        fence_set = set(fences)
        for (u, y, v) in fences:
            name, props = self.vpal.entries[self.vox[(u, y, v)]]
            p = dict(props)
            for d, (du, dv) in DIR_VEC.items():
                n = (u + du, y, v + dv)
                p[d] = 'true' if n in fence_set or (self.get(*n) not in (None, 'minecraft:air') and 'fence' not in (self.get(*n) or '')
                                                     and self.solid_at(*n) and 'lantern' not in (self.get(*n) or '')) else 'false'
            p['waterlogged'] = 'false'
            self.vox[(u, y, v)] = self.vpal.idx(state_str(name, p))

    # ------------------------------------------------------------------ finishing
    def finalize_heights(self, lo=2, hi=250):
        self.hi = np.clip(np.round(self.h), lo, hi).astype(np.int32)

    def slope(self):
        h = self.hi
        s = np.zeros_like(h)
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s = np.maximum(s, np.abs(h - np.roll(np.roll(h, du, 1), dv, 0)))
        return s

    def fill_water(self, mask, level, kind=FLUID_WATER):
        m = mask & (self.hi < level)
        self.fluid = np.where(m, kind, self.fluid).astype(np.uint8)
        self.level = np.where(m, level, self.level)

    # ------------------------------------------------------------------ validation
    def check_fluids(self):
        """Every fluid column must be contained: 4-neighbours are fluid at the same level or solid up to it."""
        bad = 0
        fl = self.fluid > 0
        for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nf = np.roll(np.roll(fl, -du, 1), -dv, 0)
            nl = np.roll(np.roll(self.level, -du, 1), -dv, 0)
            nh = np.roll(np.roll(self.hi, -du, 1), -dv, 0)
            ok = (nf & (nl == self.level)) | (nh >= self.level)
            bad += int(np.sum(fl & ~ok))
        return bad

    def reachable(self, start, max_step=1):
        """BFS over walkable columns (step <= max_step, no lava) from start (u, v). Returns bool mask."""
        h = self.hi.copy()
        walk_h = np.where(self.fluid == FLUID_WATER, np.maximum(h, self.level - 1), np.where(self.fluid == FLUID_FROZEN, self.level, h))
        lava = self.fluid == FLUID_LAVA
        seen = np.zeros((T, T), bool)
        q = deque([start])
        seen[start[1], start[0]] = True
        while q:
            u, v = q.popleft()
            for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nu, nv = u + du, v + dv
                if nu < LO or nu > HI or nv < LO or nv > HI or seen[nv, nu] or lava[nv, nu]:
                    continue
                if abs(int(walk_h[nv, nu]) - int(walk_h[v, u])) <= max_step:
                    seen[nv, nu] = True
                    q.append((nu, nv))
        return seen

    def report(self):
        bad = self.check_fluids()
        au, ay, av = self.camp['arrivals'][0]
        reach = self.reachable((int(au), int(av)))
        print(f"[{self.id}] h {self.hi.min()}..{self.hi.max()}  fluid cols {int((self.fluid > 0).sum())}  uncontained {bad}  "
              f"voxels {len(self.vox)} (vpal {len(self.vpal.entries)})  spal {len(self.spal.entries)}  gather {len(self.gather)}  "
              f"reachable {100.0 * reach[LO:HI + 1, LO:HI + 1].mean():.0f}%")
        for n, a in sorted(self.areas.items()):
            ok = reach[a['z'], a['x']]
            cnt = int((self.zone == n).sum())
            kinds = {}
            for (gu, gy, gv, k) in self.gather:
                if self.zone[gv, gu] == n:
                    kinds[k] = kinds.get(k, 0) + 1
            print(f"   area {n} {a['key']:<22} anchor({a['x']},{a['z']}) h={self.hi[a['z'], a['x']]} reach={'ok' if ok else 'NO!'} "
                  f"cells={cnt} gather={kinds}")
        return reach

    # ------------------------------------------------------------------ export
    def export(self):
        assert len(self.spal.entries) < 256, 'surface palette too large'
        assert self.camp is not None and self.outside is not None
        b8 = lambda a: ByteArray(np.clip(a, 0, 255).astype(np.uint8).tobytes())
        keys = sorted(self.vox.keys(), key=lambda k: (k[0] >> 4, k[2] >> 4, k[1], k[0], k[2]))
        vpos = IntArray([(u << 18) | (v << 9) | y for (u, y, v) in keys])
        vst = IntArray([self.vox[k] for k in keys])
        gpos = IntArray([(u << 18) | (v << 9) | y for (u, y, v, k) in self.gather])
        gk = ByteArray(bytes([k for (_, _, _, k) in self.gather]))
        areas = []
        for n, a in sorted(self.areas.items()):
            areas.append({'n': n, 'key': a['key'], 'x': a['x'], 'z': a['z'], 'r': a['r'], 'large': Byte(1 if a['large'] else 0),
                          'climate': a['climate'], 'smalls': list(a['smalls']) if a['smalls'] else []})
        camp = dict(self.camp)
        camp_n = {k: v for k, v in camp.items() if k not in ('arrivals', 'fwd')}
        camp_n['arrivals'] = IntArray([(u << 18) | (v << 9) | y for (u, y, v) in camp['arrivals']])
        for k in ('supply', 'delivery', 'flag', 'bed'):
            if k in camp_n:
                camp_n[k] = IntArray(list(camp_n[k]))
        root = {
            'version': 1, 'id': self.id, 'size': T, 'sea': self.sea, 'subDepth': self.sub_depth,
            'height': b8(self.hi), 'zone': b8(self.zone), 'biome': b8(self.biome),
            'top': b8(self.top), 'sub': b8(self.sub), 'rock': b8(self.rock), 'ctop': b8(self.ctop),
            'fluid': b8(self.fluid), 'level': b8(self.level), 'clo': b8(self.clo), 'chi': b8(self.chi),
            'spal': self.spal.nbt(), 'vpal': self.vpal.nbt(), 'vpos': vpos, 'vstate': vst,
            'gpos': gpos if self.gather else IntArray([]), 'gkind': gk,
            'areas': areas, 'biomes': list(self.biomes), 'caveBiome': self.cave_biome, 'edgeBiome': self.edge_biome,
            'camp': camp_n, 'outside': self.outside,
        }
        path = os.path.join(OUT_DATA, self.id + '.nbt')
        write_nbt(path, root)
        print(f"   wrote {os.path.relpath(path, ROOT)} ({os.path.getsize(path) // 1024} KB)")

    # ------------------------------------------------------------------ previews
    def render(self, scale=1, labels=True):
        from PIL import Image, ImageDraw
        img = np.zeros((T, T, 3), np.float64)
        tops = [color_of(self.spal.name(i)) for i in range(len(self.spal.entries))]
        tl = np.array(tops, np.float64)
        img[:] = tl[self.top]
        # voxel tops above terrain
        vtop = {}
        for (u, y, v), i in self.vox.items():
            n = self.vpal.name(i)
            if n == 'minecraft:air':
                continue
            if y > self.hi[v, u] and (u, v) not in vtop or (u, v) in vtop and y > vtop[(u, v)][0]:
                vtop[(u, v)] = (y, n)
        hv = self.hi.astype(np.float64).copy()
        for (u, v), (y, n) in vtop.items():
            img[v, u] = color_of(n)
            hv[v, u] = max(hv[v, u], y)
        water = self.fluid == FLUID_WATER
        img[water] = img[water] * 0.25 + np.array(COLORS['water']) * 0.75
        img[self.fluid == FLUID_FROZEN] = np.array(COLORS['ice'])
        img[self.fluid == FLUID_LAVA] = np.array(COLORS['lava'])
        # hillshade
        gx = np.gradient(hv, axis=1)
        gz = np.gradient(hv, axis=0)
        shade = np.clip(1.0 + (-gx - gz) * 0.12, 0.5, 1.4)
        img *= shade[..., None]
        img *= (0.8 + 0.2 * np.clip((hv - 40) / 160, 0, 1))[..., None]
        cave = self.clo > 0
        img[cave] = img[cave] * 0.7 + np.array([90, 60, 120]) * 0.3
        img = np.clip(img, 0, 255).astype(np.uint8)
        # zone borders
        z = self.zone
        border = (z != np.roll(z, 1, 0)) | (z != np.roll(z, 1, 1))
        out = img.copy()
        if labels:
            out[border & (z > 0)] = (out[border & (z > 0)] * 0.5 + 128).astype(np.uint8)
        im = Image.fromarray(out)
        if scale != 1:
            im = im.resize((T * scale, T * scale), Image.NEAREST)
        if labels:
            d = ImageDraw.Draw(im)
            gcol = {0: (60, 255, 60), 1: (80, 200, 255), 2: (255, 240, 60), 3: (255, 255, 255), 4: (255, 120, 40)}
            for (u, y, v, k) in self.gather:
                d.rectangle([u * scale - 1, v * scale - 1, u * scale + 1, v * scale + 1], fill=gcol[k])
            for n, a in self.areas.items():
                d.text((a['x'] * scale - 4, a['z'] * scale - 6), str(n), fill=(255, 255, 255))
            c = self.camp
            d.rectangle([c['x'] * scale - 3, c['z'] * scale - 3, c['x'] * scale + 3, c['z'] * scale + 3], outline=(255, 0, 0))
        return im

    def save_previews(self):
        os.makedirs(PREVIEW, exist_ok=True)
        self.render(2).save(os.path.join(PREVIEW, f'qmap_{self.id}.png'))
        os.makedirs(OUT_TEX, exist_ok=True)
        # in-game map texture: playable square (+8 margin) scaled to 256x256, no labels (drawn in-game)
        from PIL import Image
        im = self.render(1, labels=False).crop((LO - 8, LO - 8, HI + 9, HI + 9)).resize((256, 256), Image.LANCZOS)
        im.save(os.path.join(OUT_TEX, f'{self.id}.png'))
