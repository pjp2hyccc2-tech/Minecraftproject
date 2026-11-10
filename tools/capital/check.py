#!/usr/bin/env python3
"""Validate an exported capital (manifest + tiles): palette states, tile encoding, footprint, block entities,
entities, fluid ticks, spots and budgets.

    python3 tools/capital/check.py [export_dir]
"""
from __future__ import annotations

import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import blocks as B  # noqa: E402
from capital import layout as L  # noqa: E402
from capital.export import TILE, decode_tile  # noqa: E402
from capital.world import NY, X0, X1, Y0, Y1, Z0, Z1  # noqa: E402

MAX_NONAIR = 4_000_000
MAX_BYTES = 6 * 1024 * 1024


def check(out_dir: str, verbose=True) -> list:
    problems = []
    with open(os.path.join(out_dir, "manifest.json"), encoding="utf-8") as f:
        m = json.load(f)
    if m.get("format") != 1 or m.get("tileSize") != TILE or m.get("minY") != Y0 or m.get("maxY") != Y1:
        problems.append("header mismatch")
    pal = m["palette"]
    if pal[:2] != ["eternalrealm:void", "minecraft:air"]:
        problems.append("palette must start with void, air")
    if len(set(pal)) != len(pal):
        problems.append("duplicate palette entries")
    for s in pal:
        try:
            if B.canon(s) != s:
                problems.append(f"non-canonical state {s}")
        except ValueError as e:
            problems.append(str(e))
    fp = L.footprint_mask()
    counts = np.zeros(len(pal), np.int64)
    total_bytes = 0
    seen = set()
    grid = {}
    for t in m["tiles"]:
        p = os.path.join(out_dir, t["file"])
        data = open(p, "rb").read()
        total_bytes += len(data)
        tx, tz, cols = decode_tile(data)
        if (tx, tz) != (t["x"], t["z"]) or (tx, tz) in seen:
            problems.append(f"tile id mismatch {t}")
        seen.add((tx, tz))
        if cols.max() >= len(pal):
            problems.append(f"tile {tx},{tz}: palette index out of range")
        counts += np.bincount(cols.ravel(), minlength=len(pal))[:len(pal)]
        for i in range(TILE):
            for j in range(TILE):
                x, z = tx * TILE + i, tz * TILE + j
                inside = X0 <= x <= X1 and Z0 <= z <= Z1 and fp[x - X0, z - Z0]
                if not inside and cols[i, j].any():
                    problems.append(f"column {x},{z} outside the footprint is not void")
                    break
        grid[(tx, tz)] = cols
    nonair = int(counts[2:].sum())

    def block_at(x, y, z):
        tx, tz = x // TILE, z // TILE
        cols = grid.get((tx, tz))
        if cols is None or not (Y0 <= y <= Y1):
            return "eternalrealm:void"
        return pal[int(cols[x - tx * TILE, z - tz * TILE, y - Y0])]

    for be in m["blockEntities"]:
        x, y, z = be["pos"]
        st = block_at(x, y, z)
        if B.schema_of(st) not in B.BE_FAMILIES:
            problems.append(f"block entity at {be['pos']} on {st}")
        if not be["nbt"].startswith("{"):
            problems.append(f"bad SNBT at {be['pos']}")
    for e in m["entities"]:
        x, y, z = e["pos"]
        if not (X0 <= x <= X1 + 1 and Z0 <= z <= Z1 + 1 and Y0 <= y <= Y1 + 1):
            problems.append(f"entity outside box {e['pos']}")
        st = block_at(int(np.floor(x)), int(np.floor(y)), int(np.floor(z)))
        mat = st.split("[")[0]
        if e["type"].endswith("armor_stand") and mat not in ("minecraft:air", "eternalrealm:void") and \
                B.schema_of(st) not in ("slab", "stairs") and "carpet" not in mat and "pressure_plate" not in mat:
            problems.append(f"armor stand inside {st} at {e['pos']}")
    for (x, y, z) in m["fluidTicks"]:
        if "water" not in block_at(x, y, z) and "lava" not in block_at(x, y, z):
            problems.append(f"fluid tick on non-fluid at {(x, y, z)}")
    for name in L.REQUIRED_SPOTS:
        if name not in m["spots"]:
            problems.append(f"missing spot {name}")
    for name, s in m["spots"].items():
        x, y, z = s["pos"]
        feet, head, floor = block_at(x, y, z), block_at(x, y + 1, z), block_at(x, y - 1, z)
        for what, st in (("feet", feet), ("head", head)):
            sch = B.schema_of(st)
            if st not in ("minecraft:air",) and sch not in ("carpet", "pressure_plate", "candle", "light_shaft") \
                    and "carpet" not in st and "grass" != st.split(":")[1].split("[")[0] and "slab" not in st:
                problems.append(f"spot {name} {what} blocked by {st}")
        if floor in ("minecraft:air", "eternalrealm:void"):
            problems.append(f"spot {name} has no floor")
    if nonair > MAX_NONAIR:
        problems.append(f"non-air blocks {nonair} > {MAX_NONAIR} (budget)")
    if total_bytes > MAX_BYTES:
        problems.append(f"tile bytes {total_bytes} > {MAX_BYTES}")
    if verbose:
        top = np.argsort(-counts)[:12]
        print(f"check: {len(m['tiles'])} tiles, {total_bytes / 1048576:.2f} MB, palette {len(pal)}, "
              f"non-air {nonair:,}, block entities {len(m['blockEntities'])}, entities {len(m['entities'])}, "
              f"fluid ticks {len(m['fluidTicks'])}, spots {len(m['spots'])}")
        print("  most used: " + ", ".join(f"{pal[i].split(':')[1].split('[')[0]} {counts[i]:,}" for i in top if i > 1))
    return problems


if __name__ == "__main__":
    d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.dirname(HERE)), "src", "main",
                                                           "resources", "data", "eternalrealm", "capital")
    ps = check(d)
    for p in ps[:200]:
        print("PROBLEM:", p)
    print("ok" if not ps else f"{len(ps)} problems")
    sys.exit(1 if ps else 0)
