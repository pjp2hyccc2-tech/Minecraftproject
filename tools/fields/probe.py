"""The real field-map terrain for the set-piece builder.

The terrain of アストリア is computed by the pure Java classes ``com.eternalrealm.world.field.FieldTerrain`` and
``FieldLayout``. Rather than porting them, this module compiles ``TerrainProbe.java`` against ``src/main/java`` with
``javac`` and asks it for the layout geometry and for terrain grids (top block, water top, surface, field, biome).
Results are cached in ``tools/fields/.cache`` keyed by a hash of the Java sources, so rebuilding is fast and a changed
terrain is picked up automatically.
"""
from __future__ import annotations

import hashlib
import json
import os
import struct
import subprocess

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(ROOT, "src", "main", "java")
CACHE = os.path.join(HERE, ".cache")
NO_WATER = -32768

# the Java sources the terrain depends on (anything else in the pure tree is irrelevant for the heights)
_SOURCES = [
    "com/eternalrealm/world/field/FieldLayout.java",
    "com/eternalrealm/world/field/FieldTerrain.java",
    "com/eternalrealm/world/gen/GenNoise.java",
    "com/eternalrealm/world/gen/AstoriaLayout.java",
    "com/eternalrealm/world/gen/AstoriaBiome.java",
    "com/eternalrealm/world/gen/LandmarkSites.java",
    "com/eternalrealm/world/gen/LandmarkSite.java",
]

_layout = None
_classes = None


def source_hash() -> str:
    h = hashlib.sha1()
    for rel in _SOURCES + ["../../../tools/fields/TerrainProbe.java"]:
        p = os.path.normpath(os.path.join(SRC, rel))
        with open(p, "rb") as f:
            h.update(rel.encode())
            h.update(f.read())
    return h.hexdigest()[:12]


def _compile() -> str:
    global _classes
    if _classes:
        return _classes
    out = os.path.join(CACHE, "classes-" + source_hash())
    marker = os.path.join(out, "TerrainProbe.class")
    if not os.path.exists(marker):
        os.makedirs(out, exist_ok=True)
        cmd = ["javac", "--release", "17", "-encoding", "UTF-8", "-nowarn", "-sourcepath", SRC, "-d", out,
               os.path.join(HERE, "TerrainProbe.java")]
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    _classes = out
    return out


def _java(*args):
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    subprocess.run(["java", "-Xss4m", "-cp", _compile(), "TerrainProbe", *args], check=True, env=env,
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def layout() -> dict:
    """FieldLayout geometry: roads, rivers, waystones, labels, landmarks, constants (see TerrainProbe)."""
    global _layout
    if _layout is None:
        os.makedirs(CACHE, exist_ok=True)
        path = os.path.join(CACHE, f"layout-{source_hash()}.json")
        if not os.path.exists(path):
            _java("layout", path)
        with open(path, encoding="utf-8") as f:
            _layout = json.load(f)
    return _layout


class Grid:
    """Terrain samples over an inclusive rectangle with a step: arrays indexed ``[i, j]`` for
    ``x = x0 + i * step``, ``z = z0 + j * step``."""

    def __init__(self, x0, z0, nx, nz, step, top, water, surface, field, biome, flags, raw):
        self.x0, self.z0, self.nx, self.nz, self.step = x0, z0, nx, nz, step
        self.top, self.water, self.surface, self.field, self.biome, self.flags, self.raw = \
            top, water, surface, field, biome, flags, raw

    @property
    def x1(self):
        return self.x0 + (self.nx - 1) * self.step

    @property
    def z1(self):
        return self.z0 + (self.nz - 1) * self.step

    def contains(self, x, z):
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1

    def at(self, arr, x, z):
        return arr[(x - self.x0) // self.step, (z - self.z0) // self.step]


_RECORD = np.dtype([("top", ">i2"), ("water", ">i2"), ("surface", "u1"), ("field", "u1"), ("biome", "u1"),
                    ("flags", "u1"), ("raw", ">f4")])


def grids(requests):
    """Terrain grids for ``[(x0, z0, x1, z1, step), ...]`` (cached on disk)."""
    os.makedirs(CACHE, exist_ok=True)
    sh = source_hash()
    results = [None] * len(requests)
    missing = []
    for k, req in enumerate(requests):
        x0, z0, x1, z1, step = req
        path = os.path.join(CACHE, f"grid-{sh}-{x0}_{z0}_{x1}_{z1}_{step}.npz")
        if os.path.exists(path):
            d = np.load(path)
            results[k] = Grid(x0, z0, int(d["nx"]), int(d["nz"]), step, d["top"], d["water"], d["surface"],
                              d["field"], d["biome"], d["flags"], d["raw"])
        else:
            missing.append((k, req, path))
    if missing:
        req_path = os.path.join(CACHE, "requests.txt")
        out_path = os.path.join(CACHE, "grid.bin")
        with open(req_path, "w") as f:
            for _, (x0, z0, x1, z1, step), _ in missing:
                f.write(f"{x0} {z0} {x1} {z1} {step}\n")
        _java("grid", req_path, out_path)
        data = open(out_path, "rb").read()
        p = 0
        for k, (x0, z0, x1, z1, step), path in missing:
            gx0, gz0, nx, nz, st = struct.unpack(">iiiii", data[p:p + 20])
            p += 20
            n = nx * nz
            rec = np.frombuffer(data, _RECORD, n, p).reshape(nx, nz)
            p += n * _RECORD.itemsize
            arrs = {name: rec[name].astype(np.int32 if name in ("top", "water") else
                                         (np.float32 if name == "raw" else np.uint8)) for name in _RECORD.names}
            np.savez_compressed(path, nx=nx, nz=nz, **arrs)
            results[k] = Grid(x0, z0, nx, nz, step, arrs["top"], arrs["water"], arrs["surface"], arrs["field"],
                              arrs["biome"], arrs["flags"], arrs["raw"])
        os.remove(out_path)
    return results


def grid(x0, z0, x1, z1, step=1) -> Grid:
    return grids([(x0, z0, x1, z1, step)])[0]


_POINT = np.dtype([("field", "u1"), ("top", ">i2"), ("water", ">i2"), ("border", ">f4")])


def points(pts):
    """Field, top, water top and border distance at arbitrary (fractional) points: a structured array."""
    os.makedirs(CACHE, exist_ok=True)
    pts = [(float(x), float(z)) for x, z in pts]
    key = hashlib.sha1(repr(pts).encode()).hexdigest()[:16]
    path = os.path.join(CACHE, f"points-{source_hash()}-{key}.npy")
    if os.path.exists(path):
        return np.load(path)
    req_path = os.path.join(CACHE, "points.txt")
    out_path = os.path.join(CACHE, "points.bin")
    with open(req_path, "w") as f:
        for x, z in pts:
            f.write(f"{x!r} {z!r}\n")
    _java("points", req_path, out_path)
    rec = np.frombuffer(open(out_path, "rb").read(), _POINT).copy()
    os.remove(out_path)
    out = np.zeros(len(pts), [("field", "i4"), ("top", "i4"), ("water", "i4"), ("border", "f4")])
    for name in _POINT.names:
        out[name] = rec[name]
    np.save(path, out)
    return out
