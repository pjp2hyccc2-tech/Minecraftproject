"""Export of the field set pieces (format 1) for ``com.eternalrealm.world.field.FieldPieceData``.

``data/eternalrealm/fieldpieces/manifest.json``::

    {"format": 1, "build": "<hash>", "terrain": "<probe hash>",
     "pieces": [{"id", "name", "field", "kind", "box": [x0, y0, z0, x1, y1, z1], "file": "pieces/<id>.bin",
                 "palette": ["eternalrealm:void", "minecraft:air", ...],
                 "blockEntities": [{"pos": [x, y, z], "nbt": "{...}"}], "fluidTicks": [[x, y, z]],
                 "probes": [{"pos": [x, y, z], "state": "..."}], "samples": [[x, z, top]]}],
     "waystones": [{"id", "pos": [x, y, z], "facing"}]}

``pieces/<id>.bin`` is gzip of ``ERP1`` + varint tile count + tiles. A tile is one chunk of the piece:
int32 chunkX, int32 chunkZ, int32 minY, varint height, then 256 columns (local x major, then local z), each
``varint runs`` (0 = the piece leaves the column alone) and, if runs > 0, ``varint foundation`` (palette index
the column is extended down with when the terrain lies lower than the piece's lowest block, 0 = never) and ``runs``
pairs of (varint length, varint palette index) from minY up, covering ``height`` blocks.
Palette index 0 keeps the terrain, 1 is air. All positions are absolute world coordinates.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import shutil
import struct

import numpy as np

from capital import blocks as B
from capital.colors import material

from . import probe

FORMAT = 1
MAGIC = b"ERP1"
FOUNDATION_OK = ("plain", "axis", "snowy", "slab")


def _varint(v: int) -> bytes:
    out = bytearray()
    v = int(v)
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def compact(P):
    """Drop unused palette entries (keeps void, air), sorted for stable output; remaps the voxels."""
    counts = np.bincount(P.a.ravel(), minlength=len(P.palette))
    used = [i for i in range(2, len(P.palette)) if counts[i] > 0]
    order = [0, 1] + sorted(used, key=lambda i: P.palette[i])
    remap = np.zeros(len(P.palette), np.uint16)
    for new, old in enumerate(order):
        remap[old] = new
    P.a = remap[P.a]
    P.palette = [P.palette[i] for i in order]
    P._index = {s: i for i, s in enumerate(P.palette)}


def _foundation_state(state: str):
    """The block a column is extended down with below ``state`` (its lowest block), or None."""
    bid, props = B.parse(state)
    name = bid.split(":", 1)[1]
    sch = B.REGISTRY.get(bid, "plain")
    if name in ("grass_block", "dirt_path", "farmland", "podzol", "mycelium", "rooted_dirt", "moss_block"):
        return "minecraft:dirt"
    if name in ("water", "lava", "air", "void") or sch not in FOUNDATION_OK:
        return None
    if sch == "slab":
        return None if props.get("type") != "double" else state
    m = material(state)
    if m["kind"] != "solid" or m["box"] is not None or m["emit"]:
        return None
    if name in ("sand", "red_sand", "gravel", "snow_block", "ice", "packed_ice", "blue_ice", "glass", "hay_block",
                "melon", "pumpkin") or "glass" in name or "wool" in name or "leaves" in name:
        return {"sand": "minecraft:sandstone", "red_sand": "minecraft:red_sandstone", "gravel": "minecraft:stone",
                "snow_block": "minecraft:stone"}.get(name)
    if sch == "axis" and props.get("axis", "y") != "y":
        return None
    return state


def piece_bytes(P):
    """Tiles of one piece: (bytes, box, foundation count)."""
    nonvoid = P.a > 0
    cols = nonvoid.any(axis=2)
    if not cols.any():
        raise ValueError(f"piece {P.id} is empty")
    xs, zs = np.nonzero(cols)
    ys = np.nonzero(nonvoid.any(axis=(0, 1)))[0]
    box = [int(xs.min() + P.X0), int(ys.min() + P.Y0), int(zs.min() + P.Z0), int(xs.max() + P.X0),
           int(ys.max() + P.Y0), int(zs.max() + P.Z0)]
    found_idx = {}
    tiles = []
    n_found = 0
    cx0, cx1 = box[0] >> 4, box[3] >> 4
    cz0, cz1 = box[2] >> 4, box[5] >> 4
    for cx in range(cx0, cx1 + 1):
        for cz in range(cz0, cz1 + 1):
            ax0, az0 = max(cx * 16, P.X0), max(cz * 16, P.Z0)
            ax1, az1 = min(cx * 16 + 15, P.X1), min(cz * 16 + 15, P.Z1)
            if ax0 > ax1 or az0 > az1:
                continue
            sub = P.a[ax0 - P.X0:ax1 - P.X0 + 1, az0 - P.Z0:az1 - P.Z0 + 1]
            nv = sub > 0
            if not nv.any():
                continue
            yy = np.nonzero(nv.any(axis=(0, 1)))[0]
            y_lo, y_hi = int(yy.min()), int(yy.max())
            height = y_hi - y_lo + 1
            out = [struct.pack(">iii", cx, cz, y_lo + P.Y0), _varint(height)]
            for lx in range(16):
                for lz in range(16):
                    x, z = cx * 16 + lx, cz * 16 + lz
                    if not (ax0 <= x <= ax1 and az0 <= z <= az1):
                        out.append(_varint(0))
                        continue
                    col = P.a[x - P.X0, z - P.Z0, y_lo:y_hi + 1]
                    if not (col > 0).any():
                        out.append(_varint(0))
                        continue
                    change = np.nonzero(col[1:] != col[:-1])[0] + 1
                    bounds = np.concatenate(([0], change, [height]))
                    runs = [(int(bounds[r + 1] - bounds[r]), int(col[bounds[r]])) for r in range(len(bounds) - 1)]
                    # foundation: the lowest block of the column, if it stands on (or in) the terrain
                    fidx = 0
                    first = np.nonzero(col > 0)[0][0]
                    low_state = P.palette[int(col[first])]
                    y_low = y_lo + int(first) + P.Y0
                    if int(col[first]) > 1 and y_low <= P.top(x, z) + 1:
                        fs = _foundation_state(low_state)
                        if fs:
                            fidx = P.idx(fs)
                            found_idx[fs] = fidx
                            n_found += 1
                    out.append(_varint(len(runs)))
                    out.append(_varint(fidx))
                    for ln, v in runs:
                        out.append(_varint(ln))
                        out.append(_varint(v))
            tiles.append(b"".join(out))
    raw = MAGIC + _varint(len(tiles)) + b"".join(tiles)
    return raw, box, n_found


def _samples(P, box, n=16):
    """Terrain samples for the drift guard: corners, centre and a few content columns."""
    x0, _, z0, x1, _, z1 = box
    pts = [(x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, (z0 + z1) // 2)]
    cols = np.argwhere((P.a > 1).any(axis=2))
    if cols.size:
        step = max(1, len(cols) // (n - len(pts)))
        for i, j in cols[::step][:n - len(pts)]:
            pts.append((int(i + P.X0), int(j + P.Z0)))
    out = []
    for x, z in pts:
        if P.gx0 <= x < P.gx0 + P.g.nx and P.gz0 <= z < P.gz0 + P.g.nz:
            out.append([int(x), int(z), P.top(x, z)])
    return out


# blocks that decoration cannot replace (no ores, no soil, nothing a plant or a tree grows into)
_PROBE_BAD = ("stone", "andesite", "diorite", "granite", "tuff", "deepslate", "dirt", "gravel", "grass_block", "sand",
              "clay", "coarse_dirt", "farmland", "dirt_path", "snow", "water", "air", "lava")


def _probe_ok(st):
    if not st.startswith("minecraft:"):
        return False
    name = B.parse(st)[0].split(":", 1)[1]
    if name in _PROBE_BAD:
        return False
    sch = B.REGISTRY.get(B.parse(st)[0], "plain")
    if sch == "fence":
        return True
    m = material(st)
    return m["kind"] == "solid" and m["box"] is None and sch in ("plain", "axis")


def _near_probe(P, x, y, z, radius=12):
    """The vanilla full block of the piece nearest to (x, y, z) that makes a reliable GameTest probe."""
    ok = np.array([_probe_ok(s) if i > 1 else False for i, s in enumerate(P.palette)], bool)
    cells = np.argwhere(ok[P.a])
    if cells.size == 0:
        return None
    pos = cells + np.array([P.X0, P.Z0, P.Y0])
    d = (pos[:, 0] - x) ** 2 + (pos[:, 1] - z) ** 2 + (pos[:, 2] - y) ** 2
    k = int(np.argmin(d))
    if d[k] > radius * radius:
        return None
    px, pz, py = (int(v) for v in pos[k])
    return (px, py, pz), P.get(px, py, pz)


def export(pieces, out_dir, log=print):
    pieces_dir = os.path.join(out_dir, "pieces")
    if os.path.isdir(pieces_dir):
        shutil.rmtree(pieces_dir)
    os.makedirs(pieces_dir, exist_ok=True)
    h = hashlib.sha1()
    entries = []
    waystones = []
    total = 0
    stats = []
    for P in pieces:
        compact(P)
        raw, box, n_found = piece_bytes(P)       # may add foundation states to the palette
        data = gzip.compress(raw, compresslevel=9, mtime=0)
        name = f"pieces/{P.id}.bin"
        with open(os.path.join(out_dir, name), "wb") as f:
            f.write(data)
        h.update(name.encode())
        h.update(data)
        total += len(data)
        bes = []
        for (x, y, z), nbt in sorted(P.block_entities.items()):
            st = P.get(x, y, z)
            if B.schema_of(st) not in B.BE_FAMILIES:
                continue
            bes.append({"pos": [x, y, z], "nbt": nbt})
        probes = []
        for (x, y, z) in P.probes:
            st = P.get(x, y, z)
            if not _probe_ok(st):
                found = _near_probe(P, x, y, z)
                if found is None:
                    continue
                (x, y, z), st = found
            probes.append({"pos": [x, y, z], "state": st})
        if not probes and P.kind != "flowers":
            found = _near_probe(P, (P.X0 + P.X1) // 2, (P.Y0 + P.Y1) // 2, (P.Z0 + P.Z1) // 2, radius=10 ** 6)
            if found:
                (x, y, z), st = found
                probes.append({"pos": [x, y, z], "state": st})
        entry = {"id": P.id, "name": P.name, "field": P.field, "kind": P.kind, "box": box, "file": name,
                 "palette": P.palette, "blockEntities": bes,
                 "fluidTicks": [list(p) for p in sorted(P.fluid_ticks)], "probes": probes,
                 "samples": _samples(P, box)}
        entries.append(entry)
        if P.waystone:
            x, y, z, facing, wid = P.waystone
            waystones.append({"id": wid, "pos": [x, y, z], "facing": facing})
        stats.append((P.id, len(raw), len(data), int(np.count_nonzero(P.a > 1)), len(P.palette)))
    manifest = {"format": FORMAT, "build": "", "terrain": probe.source_hash(), "pieces": entries,
                "waystones": sorted(waystones, key=lambda w: w["id"])}
    body = json.dumps(manifest, ensure_ascii=False, separators=(",", ":"))
    h.update(body.encode("utf-8"))
    manifest["build"] = h.hexdigest()[:16]
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
        f.write("\n")
    mbytes = os.path.getsize(os.path.join(out_dir, "manifest.json"))
    log(f"export: {len(pieces)} pieces, {total / 1024:.0f} KiB pieces + {mbytes / 1024:.0f} KiB manifest, "
        f"build {manifest['build']}")
    return {"pieces": len(pieces), "bytes": total + mbytes, "stats": stats, "build": manifest["build"]}
