"""Minimal NBT writer/reader + Minecraft structure template (.nbt) helper.

Usage:
    from nbt import Structure
    s = Structure(16, 8, 16)
    s.set(0, 0, 0, 'minecraft:stone')
    s.set(1, 1, 1, 'minecraft:oak_stairs', {'facing': 'north', 'half': 'bottom'})
    s.save('src/main/resources/data/huntcraft/structures/thing.nbt')
"""
import gzip
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY = range(12)


class Int(int):
    pass


class Byte(int):
    pass


class Float(float):
    pass


class Double(float):
    pass


class Short(int):
    pass


class Long(int):
    pass


def _tag_type(v):
    if isinstance(v, Byte) or isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, Short):
        return TAG_SHORT
    if isinstance(v, Long):
        return TAG_LONG
    if isinstance(v, Float):
        return TAG_FLOAT
    if isinstance(v, Double):
        return TAG_DOUBLE
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, float):
        return TAG_DOUBLE
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, dict):
        return TAG_COMPOUND
    raise TypeError(type(v))


def _write_payload(out, t, v):
    if t == TAG_BYTE:
        out += struct.pack('>b', int(v))
    elif t == TAG_SHORT:
        out += struct.pack('>h', v)
    elif t == TAG_INT:
        out += struct.pack('>i', v)
    elif t == TAG_LONG:
        out += struct.pack('>q', v)
    elif t == TAG_FLOAT:
        out += struct.pack('>f', v)
    elif t == TAG_DOUBLE:
        out += struct.pack('>d', v)
    elif t == TAG_STRING:
        b = v.encode('utf-8')
        out += struct.pack('>H', len(b)) + b
    elif t == TAG_LIST:
        et = _tag_type(v[0]) if v else TAG_END
        out += struct.pack('>bi', et, len(v))
        for e in v:
            _write_payload(out, et, e)
    elif t == TAG_COMPOUND:
        for k, e in v.items():
            et = _tag_type(e)
            kb = k.encode('utf-8')
            out += struct.pack('>bH', et, len(kb)) + kb
            _write_payload(out, et, e)
        out += struct.pack('>b', TAG_END)


def dumps(root):
    out = bytearray()
    out += struct.pack('>bH', TAG_COMPOUND, 0)
    _write_payload(out, TAG_COMPOUND, root)
    return gzip.compress(bytes(out))


class Structure:
    DATA_VERSION = 3465  # Minecraft 1.20.1

    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.palette = []
        self.palette_index = {}
        self.entities = []
        self.block_nbt = {}

    def _state(self, name, props=None):
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.palette_index:
            entry = {'Name': name}
            if props:
                entry['Properties'] = {k: str(v) for k, v in props.items()}
            self.palette_index[key] = len(self.palette)
            self.palette.append(entry)
        return self.palette_index[key]

    def set(self, x, y, z, name, props=None, nbt=None):
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return
        self.blocks[(x, y, z)] = self._state(name, props)
        if nbt is not None:
            self.block_nbt[(x, y, z)] = nbt
        elif (x, y, z) in self.block_nbt:
            del self.block_nbt[(x, y, z)]

    def get(self, x, y, z):
        i = self.blocks.get((x, y, z))
        return None if i is None else self.palette[i]['Name']

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def add_entity(self, x, y, z, nbt):
        self.entities.append({'pos': [Double(x), Double(y), Double(z)], 'blockPos': [Int(int(x)), Int(int(y)), Int(int(z))], 'nbt': nbt})

    def to_nbt(self):
        blocks = []
        for (x, y, z), st in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            b = {'pos': [Int(x), Int(y), Int(z)], 'state': Int(st)}
            if (x, y, z) in self.block_nbt:
                b['nbt'] = self.block_nbt[(x, y, z)]
            blocks.append(b)
        return {
            'DataVersion': Int(self.DATA_VERSION),
            'size': [Int(v) for v in self.size],
            'palette': self.palette,
            'blocks': blocks,
            'entities': self.entities,
        }

    def save(self, path):
        import os
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'wb') as f:
            f.write(dumps(self.to_nbt()))
