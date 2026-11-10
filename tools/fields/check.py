#!/usr/bin/env python3
"""Validate the exported field set pieces (manifest + piece files).

    python3 tools/fields/check.py            # re-validate src/main/resources/data/eternalrealm/fieldpieces

Checks: canonical and valid block states, the encoding of every tile, block entities on blocks that carry them,
fluid ticks on water, pieces inside the world and outside the capital (24 blocks; the capital's own gate forecourt
8), clear of the dungeon gates (20 blocks, the dark tower 30) and of their approach (the gate's local +z side: only
paths there), out of the chunks the surface-theme GameTest samples, all eleven waystones within 3 blocks of their
points with a walkable ring of two blocks, and the size budget (8 MiB).
"""
from __future__ import annotations

import gzip
import json
import math
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import blocks as B  # noqa: E402

from fields import blocks_ext  # noqa: E402,F401
from fields import geo, probe  # noqa: E402

MAX_BYTES = 8 * 1024 * 1024
CAPITAL_MARGIN = 24
CAPITAL_MARGIN_FORECOURT = 8
GATE_KEEP_OUT = 20
TOWER_KEEP_OUT = 30
APPROACH_LENGTH = 48
APPROACH_HALF = 5
LOW_OK = ("air", "cave_air", "grass", "fern", "tall_grass", "large_fern", "dirt_path", "gravel", "moss_carpet",
          "snow", "dead_bush", "rail", "powered_rail", "pink_petals")


def _varint(data, p):
    v = s = 0
    while True:
        b = data[p]
        p += 1
        v |= (b & 0x7F) << s
        s += 7
        if not b & 0x80:
            return v, p


def decode_piece(raw):
    """[(chunkX, chunkZ, minY, height, [column -> (foundation, [(len, idx)]) or None])]"""
    assert raw[:4] == b"ERP1", "bad magic"
    p = 4
    n, p = _varint(raw, p)
    tiles = []
    for _ in range(n):
        cx, cz, miny = struct.unpack(">iii", raw[p:p + 12])
        p += 12
        h, p = _varint(raw, p)
        cols = []
        for _c in range(256):
            runs, p = _varint(raw, p)
            if runs == 0:
                cols.append(None)
                continue
            f, p = _varint(raw, p)
            rs = []
            tot = 0
            for _r in range(runs):
                ln, p = _varint(raw, p)
                v, p = _varint(raw, p)
                rs.append((ln, v))
                tot += ln
            assert tot == h, f"column covers {tot} of {h}"
            cols.append((f, rs))
        tiles.append((cx, cz, miny, h, cols))
    assert p == len(raw), "trailing bytes"
    return tiles


class Decoded:
    """Block lookup over all decoded pieces (later pieces win), tile by tile in numpy arrays."""

    def __init__(self):
        self.tiles = {}        # (cx, cz) -> [(pid, palette, minY, array[16, 16, h] of palette indices)]
        self.columns = {}      # (x, z) -> set of piece ids writing there
        self.blocks = 0

    def add(self, pid, palette, tiles):
        import numpy as np
        for (cx, cz, miny, h, cols) in tiles:
            arr = np.zeros((16, 16, h), np.uint16)
            for c, col in enumerate(cols):
                if col is None:
                    continue
                lx, lz = c >> 4, c & 15
                y = 0
                writes = False
                for ln, v in col[1]:
                    if v:
                        writes = True
                        arr[lx, lz, y:y + ln] = v
                        if v > 1:
                            self.blocks += ln
                    y += ln
                if writes:
                    self.columns.setdefault((cx * 16 + lx, cz * 16 + lz), set()).add(pid)
            self.tiles.setdefault((cx, cz), []).append((pid, palette, miny, arr))

    def get(self, x, y, z):
        out = None
        for (pid, pal, miny, arr) in self.tiles.get((x >> 4, z >> 4), ()):
            k = y - miny
            if 0 <= k < arr.shape[2]:
                v = int(arr[x & 15, z & 15, k])
                if v:
                    out = pal[v]
        return out


def _solid(state):
    if state is None:
        return None
    name = B.parse(state)[0].split(":", 1)[1]
    sch = B.REGISTRY.get(B.parse(state)[0], "plain")
    if name in LOW_OK or name in ("air", "water", "light", "void") or sch in ("carpet", "pressure_plate"):
        return False
    from capital.colors import material
    m = material(state)
    return m["kind"] != "air" and name not in ("lily_pad",) and not name.endswith("_carpet")


def check(out_dir, pieces=None, verbose=True):
    problems = []
    with open(os.path.join(out_dir, "manifest.json"), encoding="utf-8") as f:
        m = json.load(f)
    if m.get("format") != 1:
        problems.append("manifest format")
    L = probe.layout()
    total = os.path.getsize(os.path.join(out_dir, "manifest.json"))
    D = Decoded()
    be_pos = {}
    by_id = {}
    for e in m["pieces"]:
        pid = e["id"]
        by_id[pid] = e
        pal = e["palette"]
        if pal[:2] != ["eternalrealm:void", "minecraft:air"]:
            problems.append(f"{pid}: palette must start with void, air")
        if len(set(pal)) != len(pal):
            problems.append(f"{pid}: duplicate palette entries")
        for s in pal:
            try:
                if B.canon(s) != s:
                    problems.append(f"{pid}: non-canonical state {s}")
            except ValueError as ex:
                problems.append(f"{pid}: {ex}")
        path = os.path.join(out_dir, e["file"])
        data = open(path, "rb").read()
        total += len(data)
        try:
            tiles = decode_piece(gzip.decompress(data))
        except Exception as ex:  # noqa: BLE001
            problems.append(f"{pid}: undecodable ({ex})")
            continue
        for t in tiles:
            for col in t[4]:
                if col and (col[0] >= len(pal) or any(v >= len(pal) for _, v in col[1])):
                    problems.append(f"{pid}: palette index out of range")
                    break
        D.add(pid, pal, tiles)
        for be in e["blockEntities"]:
            x, y, z = be["pos"]
            key = (x, y, z)
            if key in be_pos:
                problems.append(f"{pid}: block entity at {key} also in {be_pos[key]}")
            be_pos[key] = pid
            if not be["nbt"].startswith("{"):
                problems.append(f"{pid}: bad SNBT at {key}")
        for (x, y, z) in e["fluidTicks"]:
            st = D.get(x, y, z) or ""
            if "water" not in st and "lava" not in st:
                problems.append(f"{pid}: fluid tick on {st} at {(x, y, z)}")
        for pr in e["probes"]:
            if not pr["state"].startswith("minecraft:"):
                problems.append(f"{pid}: probe on a non-vanilla block {pr['state']}")
        if not e["probes"]:
            problems.append(f"{pid}: no GameTest probe")
    for key, pid in be_pos.items():
        st = D.get(*key)
        if st is None or B.schema_of(st) not in B.BE_FAMILIES:
            problems.append(f"{pid}: block entity at {key} sits on {st}")
    # ---- where the pieces may build
    x0, z0, x1, z1 = L["bounds"]
    label_chunks = {(int(math.floor(p[0])) >> 4, int(math.floor(p[1])) >> 4) for p in L["labels"]}
    gates = L["landmarks"]
    for (x, z), pids in D.columns.items():
        if not (x0 <= x <= x1 and z0 <= z <= z1):
            problems.append(f"{sorted(pids)} outside the world at {x}, {z}")
            continue
        cd = geo.capital_distance(x, z)
        limit = CAPITAL_MARGIN_FORECOURT if pids <= {"shrine_capital"} else CAPITAL_MARGIN
        if cd < limit:
            problems.append(f"{sorted(pids)} {cd:.1f} blocks from the capital at {x}, {z}")
        if (x >> 4, z >> 4) in label_chunks:
            problems.append(f"{sorted(pids)} in the surface-test chunk of a field label at {x}, {z}")
        for g in gates:
            d = math.hypot(x - g["x"], z - g["z"])
            keep = TOWER_KEEP_OUT if g["id"] == "d8" else GATE_KEEP_OUT
            if d <= keep:
                problems.append(f"{sorted(pids)} {d:.1f} blocks from the gate {g['id']} at {x}, {z}")
    # approaches: only low things (paths, plants) in front of each gate
    for g in gates:
        fx, fz = g["front"]
        sx, sz = -fz, fx
        keep = TOWER_KEEP_OUT if g["id"] == "d8" else GATE_KEEP_OUT
        for a in range(keep, keep + APPROACH_LENGTH):
            for s in range(-APPROACH_HALF, APPROACH_HALF + 1):
                x, z = g["x"] + fx * a + sx * s, g["z"] + fz * a + sz * s
                if (x, z) not in D.columns:
                    continue
                t = probe.grid(x, z, x, z, 1).top[0, 0] if pieces is None else None
                tops = [P for P in (pieces or []) if P.X0 <= x <= P.X1 and P.Z0 <= z <= P.Z1]
                t = tops[0].top(x, z) if tops else t
                for y in range(int(t) + 1, int(t) + 4):
                    if _solid(D.get(x, y, z)):
                        problems.append(f"{sorted(D.columns[(x, z)])} blocks the approach of {g['id']} at {x}, {y}, {z}")
                        break
    # ---- waystones
    placed = {w["id"]: w for w in m["waystones"]}
    for w in L["waystones"]:
        p = placed.get(w["id"])
        if p is None:
            problems.append(f"waystone {w['id']} missing")
            continue
        x, y, z = p["pos"]
        if math.hypot(x - w["x"], z - w["z"]) > 3.0:
            problems.append(f"waystone {w['id']} {math.hypot(x - w['x'], z - w['z']):.1f} blocks from its point")
        lo, hi = D.get(x, y, z), D.get(x, y + 1, z)
        if not (lo and lo.startswith("eternalrealm:waystone") and "half=lower" in lo and f"facing={p['facing']}" in lo):
            problems.append(f"waystone {w['id']}: lower half is {lo}")
        if not (hi and hi.startswith("eternalrealm:waystone") and "half=upper" in hi):
            problems.append(f"waystone {w['id']}: upper half is {hi}")
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx == 0 and dz == 0:
                    continue
                for yy in (y, y + 1):
                    if _solid(D.get(x + dx, yy, z + dz)):
                        problems.append(f"waystone {w['id']}: ring blocked at {(x + dx, yy, z + dz)} by "
                                        f"{D.get(x + dx, yy, z + dz)}")
                below = D.get(x + dx, y - 1, z + dz)
                if below is not None and not _solid(below) and "water" not in below:
                    problems.append(f"waystone {w['id']}: no floor at {(x + dx, y - 1, z + dz)} ({below})")
    if pieces:
        for P in pieces:
            for (ax, az, bx, bz, fy) in P.keep_clear:
                for xx in range(ax, bx + 1):
                    for zz in range(az, bz + 1):
                        st = P.effective(xx, fy, zz)
                        if st.startswith("eternalrealm:waystone"):
                            continue
                        if _solid(st) or _solid(P.effective(xx, fy + 1, zz)):
                            problems.append(f"{P.id}: keep-clear area blocked at {(xx, fy, zz)}")
    if total > MAX_BYTES:
        problems.append(f"export is {total / 1048576:.2f} MiB > {MAX_BYTES / 1048576:.0f} MiB")
    if verbose:
        nonair = D.blocks
        print(f"check: {len(m['pieces'])} pieces, {total / 1048576:.2f} MiB, {len(D.columns):,} columns, "
              f"{nonair:,} blocks, {len(be_pos)} block entities, {len(m['waystones'])} waystones")
    return problems


if __name__ == "__main__":
    d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.dirname(HERE)), "src", "main",
                                                           "resources", "data", "eternalrealm", "fieldpieces")
    ps = check(d)
    for p in ps[:300]:
        print("PROBLEM:", p)
    print("ok" if not ps else f"{len(ps)} problems")
    sys.exit(1 if ps else 0)
