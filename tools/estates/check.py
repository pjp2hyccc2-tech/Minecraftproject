#!/usr/bin/env python3
"""Validate an exported estate design: canonical palette, tiles, block entities on blocks that carry them, the
``entry`` and ``exit`` spots (floor, head room, the exit at a room door), storage pieces (unique names, on a
container block or on an armour stand / item frame), paintings and stairs as the capital checks them.

    python3 tools/estates/check.py src/main/resources/data/eternalrealm/estates/room/garret
"""
from __future__ import annotations

import json
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import blocks as B  # noqa: E402
from capital.check import stair_problems  # noqa: E402
from capital.export import TILE, decode_tile  # noqa: E402
from capital.world import Y0, Y1  # noqa: E402

SLOT = re.compile(r'er_slot:"([a-z]+\.[0-9]+)"')
# a design must fit the plot (EstatePlot.HALF_EXTENT = 256) and stay compact
MAX_EXTENT = 176
MAX_NONAIR = 400_000
CONTAINERS = ("chest", "barrel", "chiseled_bookshelf", "furnace", "brewing_stand", "hopper", "jukebox")


def check(out_dir, storage=None, verbose=True) -> list:
    problems = []
    with open(os.path.join(out_dir, "manifest.json"), encoding="utf-8") as f:
        m = json.load(f)
    if m.get("format") != 1 or m.get("tileSize") != TILE or m.get("minY") != Y0 or m.get("maxY") != Y1:
        problems.append("header mismatch")
    pal = m["palette"]
    if pal[:2] != ["eternalrealm:void", "minecraft:air"]:
        problems.append("palette must start with void, air")
    for s in pal:
        try:
            if B.canon(s) != s:
                problems.append(f"non-canonical state {s}")
        except ValueError as e:
            problems.append(str(e))
    grid = {}
    nonair = 0
    for t in m["tiles"]:
        data = open(os.path.join(out_dir, t["file"]), "rb").read()
        tx, tz, cols = decode_tile(data)
        if (tx, tz) != (t["x"], t["z"]):
            problems.append(f"tile id mismatch {t}")
        if max(abs(tx * TILE), abs(tx * TILE + TILE - 1), abs(tz * TILE), abs(tz * TILE + TILE - 1)) > MAX_EXTENT + 32:
            problems.append(f"tile {tx},{tz} is too far from the origin")
        nonair += int(np.count_nonzero(cols > 1))
        grid[(tx, tz)] = cols

    def block_at(x, y, z):
        cols = grid.get((x // TILE, z // TILE))
        if cols is None or not (Y0 <= y <= Y1):
            return "eternalrealm:void"
        return pal[int(cols[x - (x // TILE) * TILE, z - (z // TILE) * TILE, y - Y0])]

    names = []
    for be in m["blockEntities"]:
        x, y, z = be["pos"]
        st = block_at(x, y, z)
        if B.schema_of(st) not in B.BE_FAMILIES:
            problems.append(f"block entity at {be['pos']} on {st}")
        for name in SLOT.findall(be["nbt"]):
            names.append(name)
            if not any(c in st for c in CONTAINERS):
                problems.append(f"storage {name} at {be['pos']} is not a container ({st})")
    for e in m["entities"]:
        for name in SLOT.findall(e["nbt"]):
            names.append(name)
            if not (e["type"].endswith("armor_stand") or e["type"].endswith("item_frame")):
                problems.append(f"storage {name} on {e['type']}")
            if e["type"].endswith("item_frame") and "Fixed:1b" in e["nbt"]:
                problems.append(f"storage frame {name} is fixed (players could not use it)")
            if e["type"].endswith("armor_stand") and "DisabledSlots:0" not in e["nbt"]:
                problems.append(f"storage stand {name} has locked slots")
        if e["type"].endswith("armor_stand"):
            x, y, z = (int(np.floor(v)) for v in e["pos"])
            st = block_at(x, y, z)
            if st not in ("minecraft:air", "eternalrealm:void") and "carpet" not in st:
                problems.append(f"armour stand inside {st} at {e['pos']}")
    if len(set(names)) != len(names):
        problems.append(f"duplicate storage names: {sorted(n for n in set(names) if names.count(n) > 1)}")
    if storage is not None and sorted(names) != sorted(storage.names):
        problems.append(f"storage written {sorted(storage.names)} but exported {sorted(names)}")
    for required in ("entry", "exit"):
        if required not in m["spots"]:
            problems.append(f"missing spot {required}")
    for name, s in m["spots"].items():
        x, y, z = s["pos"]
        feet, head, floor = block_at(x, y, z), block_at(x, y + 1, z), block_at(x, y - 1, z)
        for what, st in (("feet", feet), ("head", head)):
            if st != "minecraft:air" and "carpet" not in st and "light_shaft" not in st:
                problems.append(f"spot {name} {what} blocked by {st}")
        if floor in ("minecraft:air", "eternalrealm:void"):
            problems.append(f"spot {name} has no floor")
    if "exit" in m["spots"]:
        x, y, z = m["spots"]["exit"]["pos"]
        if not any(any(way in block_at(x + dx, y, z + dz) for way in ("room_door", "guild_portal"))
                   for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            problems.append("the exit spot is not in front of a room door or a guild portal")
    problems += stair_problems(pal, grid, block_at)
    if nonair > MAX_NONAIR:
        problems.append(f"non-air blocks {nonair} > {MAX_NONAIR}")
    if verbose:
        print(f"check {os.path.relpath(out_dir)}: {len(m['tiles'])} tiles, palette {len(pal)}, non-air {nonair:,}, "
              f"block entities {len(m['blockEntities'])}, entities {len(m['entities'])}, storage {len(names)}")
    return problems


if __name__ == "__main__":
    found = check(sys.argv[1])
    for p in found:
        print("PROBLEM:", p)
    print("ok" if not found else f"{len(found)} problems")
    sys.exit(1 if found else 0)
