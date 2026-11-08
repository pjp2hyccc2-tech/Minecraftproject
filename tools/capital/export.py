"""Binary tile export (format 1) + manifest.json for the Java loader.

tiles/<tx>_<tz>.bin: b"ERT1", int32be tx, int32be tz, then 32x32 columns (x-major: for i: for j: x=tx*32+i,
z=tz*32+j); each column: varint runCount, then runCount pairs (varint length, varint paletteIndex) from minY up,
covering exactly maxY-minY+1 blocks. Tiles that are entirely void are omitted.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import struct

import numpy as np

from . import blocks as B
from .world import NY, X0, X1, Y0, Y1, Z0, Z1, VOID

TILE = 32

_VARINT = None


def _varints():
    global _VARINT
    if _VARINT is None:
        out = []
        for v in range(1 << 16):
            b = bytearray()
            x = v
            while True:
                byte = x & 0x7F
                x >>= 7
                if x:
                    b.append(byte | 0x80)
                else:
                    b.append(byte)
                    break
            out.append(bytes(b))
        _VARINT = out
    return _VARINT


def encode_tile(cols: np.ndarray, tx: int, tz: int) -> bytes:
    """cols: (32, 32, NY) uint16 palette indices -> tile bytes."""
    vi = _varints()
    flat = cols.reshape(TILE * TILE, NY)
    change = flat[:, 1:] != flat[:, :-1]
    parts = [b"ERT1", struct.pack(">ii", tx, tz)]
    ci, pos = np.nonzero(change)
    # run starts per column
    counts = np.bincount(ci, minlength=TILE * TILE) + 1
    starts_all = np.split(pos + 1, np.cumsum(counts - 1)[:-1])
    for c in range(TILE * TILE):
        st = starts_all[c]
        bounds = np.concatenate(([0], st, [NY]))
        lens = np.diff(bounds)
        vals = flat[c, bounds[:-1]]
        parts.append(vi[len(lens)])
        for ln, v in zip(lens.tolist(), vals.tolist()):
            parts.append(vi[ln])
            parts.append(vi[v])
    return b"".join(parts)


def decode_tile(data: bytes):
    """Inverse of encode_tile (used by check.py)."""
    assert data[:4] == b"ERT1"
    tx, tz = struct.unpack(">ii", data[4:12])
    p = 12
    out = np.zeros((TILE * TILE, NY), np.uint16)

    def rv():
        nonlocal p
        v = 0
        s = 0
        while True:
            b = data[p]
            p += 1
            v |= (b & 0x7F) << s
            s += 7
            if not b & 0x80:
                return v
    for c in range(TILE * TILE):
        n = rv()
        y = 0
        for _ in range(n):
            ln = rv()
            v = rv()
            out[c, y:y + ln] = v
            y += ln
        assert y == NY, (c, y)
    assert p == len(data)
    return tx, tz, out.reshape(TILE, TILE, NY)


def tile_range():
    tx0, tx1 = X0 // TILE, X1 // TILE
    tz0, tz1 = Z0 // TILE, Z1 // TILE
    return range(tx0, tx1 + 1), range(tz0, tz1 + 1)


def compact_palette(w):
    """Drop unused palette entries (keeps 0=void, 1=air) and remap the array. Returns the new palette."""
    used = np.zeros(len(w.palette), bool)
    used[0] = used[1] = True
    counts = np.bincount(w.a.ravel(), minlength=len(w.palette))
    used |= counts > 0
    order = [0, 1] + sorted([i for i in range(2, len(w.palette)) if used[i]], key=lambda i: w.palette[i])
    remap = np.zeros(len(w.palette), np.uint16)
    for new, old in enumerate(order):
        remap[old] = new
    w.a = remap[w.a]
    w.palette = [w.palette[i] for i in order]
    w._index = {s: i for i, s in enumerate(w.palette)}
    return w.palette


def export(w, out_dir: str, log=print):
    """Write tiles + manifest. Returns a stats dict."""
    compact_palette(w)
    # block entities whose block can't carry them are dropped
    bes = []
    for (x, y, z), nbt in sorted(w.block_entities.items()):
        st = w.get(x, y, z)
        fam = B.schema_of(st)
        if fam not in B.BE_FAMILIES:
            continue
        bes.append({"pos": [x, y, z], "nbt": nbt})
    tiles_dir = os.path.join(out_dir, "tiles")
    if os.path.isdir(tiles_dir):
        shutil.rmtree(tiles_dir)
    os.makedirs(tiles_dir, exist_ok=True)
    txs, tzs = tile_range()
    tiles = []
    h = hashlib.sha1()
    total_bytes = 0
    for tx in txs:
        for tz in tzs:
            x0, z0 = tx * TILE, tz * TILE
            cols = np.zeros((TILE, TILE, NY), np.uint16)
            ax0, ax1 = max(x0, X0), min(x0 + TILE - 1, X1)
            az0, az1 = max(z0, Z0), min(z0 + TILE - 1, Z1)
            if ax0 <= ax1 and az0 <= az1:
                cols[ax0 - x0:ax1 - x0 + 1, az0 - z0:az1 - z0 + 1] = w.a[ax0 - X0:ax1 - X0 + 1, az0 - Z0:az1 - Z0 + 1]
            if not cols.any():
                continue
            data = encode_tile(cols, tx, tz)
            name = f"tiles/{tx}_{tz}.bin"
            with open(os.path.join(out_dir, name), "wb") as f:
                f.write(data)
            h.update(name.encode())
            h.update(data)
            total_bytes += len(data)
            tiles.append({"x": tx, "z": tz, "file": name})
    ents = sorted(w.entities, key=lambda e: (e["pos"][0], e["pos"][1], e["pos"][2], e["type"]))
    manifest = {
        "format": 1,
        "build": "",
        "tileSize": TILE,
        "minY": Y0,
        "maxY": Y1,
        "palette": w.palette,
        "tiles": tiles,
        "blockEntities": bes,
        "entities": ents,
        "fluidTicks": [list(p) for p in sorted(w.fluid_ticks)],
        "spots": {k: w.spots[k] for k in sorted(w.spots)},
        "blend": {"edgeY": 0},
    }
    body = json.dumps(manifest, ensure_ascii=False, sort_keys=False, separators=(",", ":"))
    h.update(body.encode("utf-8"))
    manifest["build"] = h.hexdigest()[:16]
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
        f.write("\n")
    nonair = int(np.count_nonzero(w.a > 1))
    stats = {"tiles": len(tiles), "tile_bytes": total_bytes, "palette": len(w.palette), "nonair": nonair,
             "block_entities": len(bes), "entities": len(ents), "fluid_ticks": len(w.fluid_ticks),
             "spots": len(w.spots), "build": manifest["build"]}
    log(f"export: {stats}")
    return stats
