"""Deterministic integer-hash noise (no global random state)."""
from __future__ import annotations

import zlib

import numpy as np

M32 = np.uint64(0xFFFFFFFF)


def seed_of(name: str) -> int:
    return zlib.crc32(name.encode("utf-8")) & 0x7FFFFFFF


def hash2(x, z, seed: int = 0):
    """uint32-ish hash of integer lattice coordinates -> float in [0, 1)."""
    with np.errstate(over="ignore"):
        x = np.asarray(x, np.int64).astype(np.uint64)
        z = np.asarray(z, np.int64).astype(np.uint64)
        sd = np.uint64(int(seed) & 0xFFFFFFFF)
        h = (x * np.uint64(374761393) + z * np.uint64(668265263) + sd * np.uint64(2246822519)) & M32
        h = ((h ^ (h >> np.uint64(13))) * np.uint64(1274126177)) & M32
        h = h ^ (h >> np.uint64(16))
        return (h & M32).astype(np.float64) / 4294967296.0


def hash3(x, y, z, seed: int = 0):
    return hash2(np.asarray(x, np.int64) * 31 + np.asarray(y, np.int64) * 1009, z, seed)


def value2(x, z, scale: float, seed: int = 0):
    """Smooth value noise in [0, 1] at float coordinates."""
    fx = np.asarray(x, np.float64) / scale
    fz = np.asarray(z, np.float64) / scale
    x0 = np.floor(fx).astype(np.int64)
    z0 = np.floor(fz).astype(np.int64)
    tx = fx - x0
    tz = fz - z0
    tx = tx * tx * (3 - 2 * tx)
    tz = tz * tz * (3 - 2 * tz)
    a = hash2(x0, z0, seed)
    b = hash2(x0 + 1, z0, seed)
    c = hash2(x0, z0 + 1, seed)
    d = hash2(x0 + 1, z0 + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def fbm2(x, z, scale: float, octaves: int = 4, seed: int = 0, gain=0.5):
    """Fractal value noise in roughly [-1, 1]."""
    total = 0.0
    amp = 1.0
    norm = 0.0
    s = scale
    for o in range(octaves):
        total = total + (value2(x, z, s, seed + o * 7919) * 2 - 1) * amp
        norm += amp
        amp *= gain
        s /= 2.0
    return total / norm


def grid(x0, z0, x1, z1):
    """Meshgrid of integer coordinates (inclusive) with indexing [x, z]."""
    return np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1), indexing="ij")


def pick(weights, u):
    """Choose indices from cumulative ``weights`` for uniform samples ``u`` (array)."""
    w = np.asarray(weights, np.float64)
    c = np.cumsum(w / w.sum())
    return np.searchsorted(c, np.asarray(u), side="right").clip(0, len(w) - 1)
