#!/usr/bin/env python3
"""Writes GameTest structure templates that are easier to describe in code than to build by hand.

  boss_arena.nbt  41x15x41: stone floor, walls and a roof (a closed hall, so nothing from the neighbouring tests can
                  fall in), a 5x5 pillar in the middle and four 2x2 pillars around it, 13 blocks of headroom. Big
                  enough for the giant bosses to walk, turn and swing (EntityGameTests).

Run from the repository root: python3 tools/gen-gametest-structures.py
"""
import gzip
import io
import os
import struct

DATA_VERSION = 3465  # Minecraft 1.20.1
OUT = os.path.join('src', 'main', 'resources', 'data', 'eternalrealm', 'structures')


# ------------------------------------------------------------------------------------------- tiny NBT writer

def _name(out, name):
    data = name.encode('utf-8')
    out.write(struct.pack('>H', len(data)))
    out.write(data)


def _payload(out, tag, value):
    if tag == 3:
        out.write(struct.pack('>i', value))
    elif tag == 8:
        _name(out, value)
    elif tag == 9:
        item_tag, items = value
        out.write(struct.pack('>bi', item_tag if items else 0, len(items)))
        for item in items:
            _payload(out, item_tag, item)
    elif tag == 10:
        for key, (child_tag, child) in value.items():
            out.write(struct.pack('>b', child_tag))
            _name(out, key)
            _payload(out, child_tag, child)
        out.write(b'\x00')
    else:
        raise ValueError(tag)


def nbt_file(path, root):
    out = io.BytesIO()
    out.write(struct.pack('>b', 10))
    _name(out, '')
    _payload(out, 10, root)
    with open(path, 'wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0) as f:
        f.write(out.getvalue())


def compound(**fields):
    return fields


# -------------------------------------------------------------------------------------------- structures

def structure(size, solid):
    """A template of {@code size} where solid(x, y, z) blocks are stone and every other block is air."""
    sx, sy, sz = size
    blocks = []
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                state = 1 if solid(x, y, z) else 0
                blocks.append(compound(pos=(9, (3, [x, y, z])), state=(3, state)))
    return compound(
        DataVersion=(3, DATA_VERSION),
        size=(9, (3, list(size))),
        palette=(9, (10, [compound(Name=(8, 'minecraft:air')), compound(Name=(8, 'minecraft:stone'))])),
        blocks=(9, (10, blocks)),
        entities=(9, (10, [])))


def boss_arena():
    size = 41
    height = 15

    def solid(x, y, z):
        if y == 0 or y == height - 1:
            return True                                      # floor and roof
        if x in (0, size - 1) or z in (0, size - 1):
            return True                                      # walls
        if y > 5:
            return False
        if 18 <= x <= 22 and 18 <= z <= 22:
            return True                                      # pillar in the middle (blocks the diagonal)
        for cx in (10, 29):
            for cz in (10, 29):
                if cx <= x <= cx + 1 and cz <= z <= cz + 1:
                    return True                              # smaller pillars
        return False
    return structure((size, height, size), solid)


def main():
    nbt_file(os.path.join(OUT, 'boss_arena.nbt'), boss_arena())
    print('wrote', os.path.join(OUT, 'boss_arena.nbt'))


if __name__ == '__main__':
    main()
