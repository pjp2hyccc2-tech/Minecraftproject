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
STEP4 = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def passable(st: str) -> bool:
    """Blocks a player walks through or over: air, decoration without a collision box (torches, signs, banners,
    pressure plates, buttons, vines, open doors) and carpets (1/16 high). Lanterns, chains and candles collide."""
    n = st.split(":", 1)[1].split("[")[0]
    if n in ("air", "cave_air", "void", "light_shaft", "light", "smoke_emitter", "fountain_spray", "vine",
             "guild_portal"):
        return True
    if n.endswith(("torch", "_sign", "_banner", "pressure_plate", "_button", "carpet")):
        return True
    return n.endswith("_door") and "open=true" in st


def stair_problems(pal, grid, block_at) -> list:
    """Staircases a player cannot use. A staircase is a run of >= 3 bottom stairs (roof tiles aside) rising one
    block per step towards their facing. It needs a floor (not a lone block) in front of or beside the first
    step; two free blocks over every step and, but for the last, a third (a player, 1.8 tall, stepping onto the
    next stair is still partly over this one); and a floor at the level of the last step ahead of it or beside it.
    Runs side by side (a wide stair) are judged together. Runs rising against their facing are reported too."""
    ids = np.array([i for i, st in enumerate(pal) if B.schema_of(st) == "stairs" and "half=bottom" in st
                    and "roof" not in st], dtype=np.int64)
    cells = {}
    for (tx, tz), cols in grid.items():
        xs, zs, ys = np.nonzero(np.isin(cols, ids))
        for i, j, k in zip(xs.tolist(), zs.tolist(), ys.tolist()):
            st = pal[int(cols[i, j, k])]
            cells[(tx * TILE + i, k + Y0, tz * TILE + j)] = st.split("facing=")[1].split(",")[0].split("]")[0]

    def free(x, y, z, n):
        return all(passable(block_at(x, y + d, z)) for d in range(n))

    def standable(x, y, z, stairs_ok=True):            # feet at y; roofs are not floors
        below = block_at(x, y - 1, z)
        return free(x, y, z, 2) and not passable(below) and "roof" not in below and (
            stairs_ok or (x, y - 1, z) not in cells)

    def floor_size(x, y, z, size=3):
        seen, todo = {(x, z)}, [(x, z)]
        while todo and len(seen) < size:
            cx, cz = todo.pop()
            for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (cx + ax, cz + az)
                if n not in seen and standable(n[0], y, n[1]):
                    seen.add(n)
                    todo.append(n)
        return len(seen)

    def stair_next_to(x, y, z):
        """A step of another flight right beside (x, y, z) at that level or one below (a landing between
        flights, as on a spiral stair)."""
        return any((x + ax, yy, z + az) in cells for (ax, az) in ((1, 0), (-1, 0), (0, 1), (0, -1)) for yy in (y - 1, y))

    def on_floor(x, y, z):
        """(x, y, z) is a place to stand that belongs to a floor (not a lone block) or to a landing."""
        return standable(x, y, z, stairs_ok=False) and (floor_size(x, y, z) >= 3 or stair_next_to(x, y, z))

    def run_from(start, sign=1):
        f = cells[start]
        dx, dz = STEP4[f]
        run = [start]
        while (nxt := (run[-1][0] + sign * dx, run[-1][1] + 1, run[-1][2] + sign * dz)) in cells and cells[nxt] == f:
            run.append(nxt)
        return run

    def lanes(c):
        """The tops (or bottoms) side by side with ``c`` at its level and facing: the width of the stair."""
        f = cells[c]
        dx, dz = STEP4[f]
        group = [c]
        for (ax, az) in ((dz, dx), (-dz, -dx)):
            p = (c[0] + ax, c[1], c[2] + az)
            while p in cells and cells[p] == f:
                group.append(p)
                p = (p[0] + ax, p[1], p[2] + az)
        return group, (dz, dx)

    out = []
    starts = [c for c, f in cells.items()
              if not ((c[0] - STEP4[f][0], c[1] - 1, c[2] - STEP4[f][1]) in cells
                      and cells[(c[0] - STEP4[f][0], c[1] - 1, c[2] - STEP4[f][1])] == f)]
    judged = set()
    for c in starts:
        f = cells[c]
        dx, dz = STEP4[f]
        run = run_from(c)
        x0, y0, z0 = c
        if len(run) < 3 or not free(x0, y0 + 1, z0, 2) or c in judged:
            continue
        bottoms, (px, pz) = lanes(c)
        bottoms = [b for b in bottoms if b in starts and len(run_from(b)) == len(run)]
        judged.update(bottoms)
        runs = [run_from(b) for b in bottoms]
        ends = (min(bottoms), max(bottoms))

        def approach_ok(b):
            x, y, z = b
            cand = [(x - dx, y, z - dz)] + [(x + sx, y, z + sz) for (sx, sz) in ((px, pz), (-px, -pz))
                                            if (x + sx, y, z + sz) not in cells]
            return any(on_floor(*a) for a in cand)

        if not any(approach_ok(b) for b in bottoms):
            # no floor to start from: not a staircase meant to be walked (a roof slope, a gable trim), unless the
            # first step sits on a lone block one above a floor (it would need a jump)
            if any(standable(b[0] - dx, b[1], b[2] - dz, stairs_ok=False) and
                   any(standable(b[0] - dx + ax, b[1] - 1, b[2] - dz + az) for (ax, az) in
                       ((1, 0), (-1, 0), (0, 1), (0, -1))) for b in bottoms):
                out.append(f"staircase from {c} ({len(run)} steps rising {f}) starts on a lone block")
            continue
        clear = [all(free(x, y + 1, z, 2 if k == len(r) - 1 else 3) for k, (x, y, z) in enumerate(r)) for r in runs]
        if not any(clear):
            out.append(f"staircase from {c} ({len(run)} steps rising {f}) has no head room")
            continue
        tops = [r[-1] for r, ok in zip(runs, clear) if ok]
        exits = []
        for (x, y, z) in tops:
            exits.append((x + dx, y + 1, z + dz))
            exits += [(x + sx, y + 1, z + sz) for (sx, sz) in ((px, pz), (-px, -pz)) if (x + sx, y, z + sz) not in cells]
        if not any(on_floor(*e) for e in exits):
            out.append(f"staircase from {c} ({len(run)} steps rising {f}) leads nowhere (no floor at the top)")
    # steps facing the wrong way: a run rising away from the side of the stairs' tall backs (the Star Gate stair
    # was built like that; stair `facing` must be the direction the steps rise)
    for c, f in cells.items():
        dx, dz = STEP4[f]
        if (c[0] + dx, c[1] - 1, c[2] + dz) in cells and cells[(c[0] + dx, c[1] - 1, c[2] + dz)] == f:
            continue
        run = run_from(c, sign=-1)
        if len(run) >= 3 and free(c[0], c[1] + 1, c[2], 2):
            out.append(f"staircase from {c} ({len(run)} steps) rises against its facing {f}")
    return out

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
    # paintings: the game takes one down when a block behind it is not solid or a block stands in its place
    sizes = {"kebab": (1, 1), "aztec": (1, 1), "alban": (1, 1), "aztec2": (1, 1), "bomb": (1, 1), "plant": (1, 1),
             "wasteland": (1, 1), "pool": (2, 1), "courbet": (2, 1), "sea": (2, 1), "sunset": (2, 1),
             "creebet": (2, 1), "wanderer": (1, 2), "graham": (1, 2), "match": (2, 2), "bust": (2, 2),
             "stage": (2, 2), "void": (2, 2), "skull_and_roses": (2, 2)}
    ccw = {0: (1, 0), 1: (0, 1), 2: (-1, 0), 3: (0, -1)}       # facing south/west/north/east -> counter-clockwise
    step = {0: (0, 1), 1: (-1, 0), 2: (0, -1), 3: (1, 0)}
    for e in m["entities"]:
        if not e["type"].endswith("painting"):
            continue
        nbt = e["nbt"]
        variant = nbt.split('variant:"minecraft:')[1].split('"')[0]
        facing = int(nbt.split("facing:")[1].split("b")[0])
        w_, h_ = sizes.get(variant, (0, 0))
        if not w_:
            problems.append(f"painting {variant} of unknown size at {e['pos']}")
            continue
        x, y, z = (int(np.floor(v)) for v in e["pos"])
        sx, sz = ccw[facing]
        bx, bz = step[facing]
        for k in range(w_):
            for l in range(h_):
                cx, cy, cz = x + sx * k, y + l, z + sz * k
                if block_at(cx, cy, cz) not in ("minecraft:air", "eternalrealm:light_shaft"):
                    problems.append(f"painting at {e['pos']} covers {block_at(cx, cy, cz)}")
                wall = block_at(cx - bx, cy, cz - bz)
                if wall in ("minecraft:air", "eternalrealm:void") or B.schema_of(wall) in (
                        "stairs", "slab", "pane", "fence", "wall", "door", "trapdoor", "carpet", "leaves"):
                    problems.append(f"painting at {e['pos']} hangs on {wall}")
    hanging = {}
    for e in m["entities"]:
        if e["type"].endswith("painting") or e["type"].endswith("item_frame"):
            x, y, z = (int(np.floor(v)) for v in e["pos"])
            cells = [(x, y, z)]
            if e["type"].endswith("painting"):
                nbt = e["nbt"]
                w_, h_ = sizes.get(nbt.split('variant:"minecraft:')[1].split('"')[0], (1, 1))
                sx, sz = ccw[int(nbt.split("facing:")[1].split("b")[0])]
                cells = [(x + sx * k, y + l, z + sz * k) for k in range(w_) for l in range(h_)]
            for cell in cells:
                if cell in hanging:
                    problems.append(f"{e['type']} at {e['pos']} overlaps {hanging[cell]}")
                hanging[cell] = e["type"]
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
            if st not in ("minecraft:air",) and sch not in ("carpet", "pressure_plate", "candle", "light_shaft",
                                                             "guild_portal") \
                    and "carpet" not in st and "grass" != st.split(":")[1].split("[")[0] and "slab" not in st:
                problems.append(f"spot {name} {what} blocked by {st}")
        if floor in ("minecraft:air", "eternalrealm:void"):
            problems.append(f"spot {name} has no floor")
    problems += stair_problems(pal, grid, block_at)
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
