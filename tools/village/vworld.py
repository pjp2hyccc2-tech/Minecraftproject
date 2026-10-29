"""Voxel world used to author the hunter village, exported as Minecraft structure tiles (tools/nbt.py).

Coordinates: x = east, y = up, z = south (Minecraft axes). Blocks are stored sparsely; positions never set stay
"structure void" in the exported templates (the flat world below / air above is kept).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from nbt import Structure, Byte  # noqa: E402

# blocks that do not hide neighbouring faces / are not "solid ground"
NON_SOLID_HINTS = ('air', 'water', 'lava', 'glass', 'pane', 'fence', 'wall', 'door', 'trapdoor', 'leaves', 'lantern',
                   'torch', 'carpet', 'slab', 'stairs', 'sign', 'banner', 'flower', 'grass', 'fern', 'petals', 'bamboo',
                   'chain', 'bars', 'rail', 'button', 'plate', 'candle', 'potted', 'rose', 'lilac', 'peony', 'poppy',
                   'dandelion', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'cornflower', 'lily', 'sapling', 'azalea',
                   'campfire', 'cauldron', 'anvil', 'bed', 'snow', 'ladder', 'scaffolding', 'lectern', 'pot', 'cake',
                   'bush', 'vine', 'kelp', 'seagrass', 'wheat', 'carrots', 'potatoes', 'beetroots', 'grindstone',
                   'end_rod', 'lightning_rod', 'path', 'farmland', 'paper_lantern', 'cobweb', 'head', 'skull', 'dripleaf')


class World:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.markers = {}

    # ------------------------------------------------------------------ basic access
    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, name, props=None, nbt=None):
        x, y, z = int(x), int(y), int(z)
        if not self.inside(x, y, z):
            return
        if ':' not in name:
            name = 'minecraft:' + name
        self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def get(self, x, y, z):
        b = self.blocks.get((int(x), int(y), int(z)))
        return None if b is None else b[0]

    def props(self, x, y, z):
        b = self.blocks.get((int(x), int(y), int(z)))
        return {} if b is None else dict(b[1])

    def clear(self, x, y, z):
        self.blocks.pop((int(x), int(y), int(z)), None)

    def air(self, x, y, z):
        self.set(x, y, z, 'air')

    def is_empty(self, x, y, z):
        n = self.get(x, y, z)
        return n is None or n == 'minecraft:air'

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def fill_if_empty(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if self.is_empty(x, y, z):
                        self.set(x, y, z, name, props)

    def replace(self, x0, y0, z0, x1, y1, z1, frm, to, props=None):
        frm = frm if ':' in frm else 'minecraft:' + frm
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if self.get(x, y, z) == frm:
                        self.set(x, y, z, to, props)

    def mark(self, name, x, y, z, yaw=0.0):
        self.markers.setdefault(name, []).append((x, y, z, yaw))

    # ------------------------------------------------------------------ export
    def save_tiles(self, out_dir, prefix, tile, sy=None):
        sx, wy, sz = self.size
        sy = sy or wy
        nx, nz = math.ceil(sx / tile), math.ceil(sz / tile)
        os.makedirs(out_dir, exist_ok=True)
        for f in os.listdir(out_dir):
            if f.startswith(prefix + '_') and f.endswith('.nbt'):
                os.remove(os.path.join(out_dir, f))
        written = []
        counts = {}
        for (x, y, z), b in self.blocks.items():
            k = (x // tile, z // tile)
            counts[k] = counts.get(k, 0) + 1
        for i in range(nx):
            for j in range(nz):
                if not counts.get((i, j)):
                    continue
                w = min(tile, sx - i * tile)
                d = min(tile, sz - j * tile)
                s = Structure(w, sy, d)
                for (x, y, z), (name, props, nbt) in self.blocks.items():
                    if x // tile == i and z // tile == j:
                        s.set(x - i * tile, y, z - j * tile, name, dict(props) if props else None, nbt)
                path = os.path.join(out_dir, '%s_%d_%d.nbt' % (prefix, i, j))
                s.save(path)
                written.append((i, j, len(s.blocks)))
        return nx, nz, written


def sign_nbt(lines, color='black', glow=False):
    msgs = []
    for i in range(4):
        t = lines[i] if i < len(lines) else ''
        msgs.append(json.dumps({'text': t}, ensure_ascii=False))
    side = {'messages': msgs, 'color': color, 'has_glowing_text': Byte(1 if glow else 0)}
    back = {'messages': [json.dumps({'text': ''})] * 4, 'color': 'black', 'has_glowing_text': Byte(0)}
    return {'front_text': side, 'back_text': back, 'is_waxed': Byte(1)}


def banner_nbt(patterns):
    """patterns: list of (code, colour id)."""
    from nbt import Int
    return {'Patterns': [{'Pattern': p, 'Color': Int(c)} for p, c in patterns]}


DYE = {'white': 0, 'orange': 1, 'magenta': 2, 'light_blue': 3, 'yellow': 4, 'lime': 5, 'pink': 6, 'gray': 7,
       'light_gray': 8, 'cyan': 9, 'purple': 10, 'blue': 11, 'brown': 12, 'green': 13, 'red': 14, 'black': 15}


# ------------------------------------------------------------------ deterministic noise
def h2(x, z, seed=0):
    n = (int(x) * 374761393 + int(z) * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, z, seed=0):
    x0, z0 = math.floor(x), math.floor(z)
    fx, fz = x - x0, z - z0
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a = h2(x0, z0, seed)
    b = h2(x0 + 1, z0, seed)
    c = h2(x0, z0 + 1, seed)
    d = h2(x0 + 1, z0 + 1, seed)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz


def fbm(x, z, seed=0, octaves=4):
    v, a, f, t = 0.0, 1.0, 1.0, 0.0
    for o in range(octaves):
        v += vnoise(x * f, z * f, seed + o * 31) * a
        t += a
        a *= 0.5
        f *= 2.0
    return v / t
