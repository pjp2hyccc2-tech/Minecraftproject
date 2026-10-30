"""Exact mirror of src/main/java/.../world/Terrain.java (numpy-vectorised). Keep both in sync!"""
import numpy as np

WATER_TOP = 62
M32 = 0xFFFFFFFF


def hash_np(ix, iz, s):
    ix = np.asarray(ix, dtype=np.int64)
    iz = np.asarray(iz, dtype=np.int64)
    h = (ix * 374761393 + iz * 668265263 + s * 1442695041) & M32
    h = ((h ^ (h >> 13)) * 1274126177) & M32
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / 16777216.0


def noise(x, z, scale, s):
    fx = np.asarray(x, dtype=np.float64) / scale
    fz = np.asarray(z, dtype=np.float64) / scale
    ix = np.floor(fx).astype(np.int64)
    iz = np.floor(fz).astype(np.int64)
    tx = fx - ix
    tz = fz - iz
    ux = tx * tx * (3.0 - 2.0 * tx)
    uz = tz * tz * (3.0 - 2.0 * tz)
    a = hash_np(ix, iz, s)
    b = hash_np(ix + 1, iz, s)
    c = hash_np(ix, iz + 1, s)
    d = hash_np(ix + 1, iz + 1, s)
    ab = a + (b - a) * ux
    cd = c + (d - c) * ux
    return ab + (cd - ab) * uz


def fbm(x, z):
    return 0.55 * noise(x, z, 220.0, 11) + 0.30 * noise(x, z, 80.0, 23) + 0.15 * noise(x, z, 28.0, 37)


def coast_z(x):
    x = np.asarray(x, dtype=np.int64)
    ramp = np.minimum(1.0, (x - 1500) / 300.0)
    base = 700.0 + 50.0 * (noise(x, np.zeros_like(x, dtype=np.float64), 300.0, 51) - 0.5) * 2.0
    out = base + (1.0 - ramp) * 2000.0
    return np.where(x < 1500, 1.0e9, out)


def awaji_z(x):
    x = np.asarray(x, dtype=np.int64)
    edge = np.minimum(x - 1500, 2700 - x)
    bulge = np.minimum(1.0, edge / 250.0)
    out = 1350.0 + 40.0 * (noise(x, np.full(x.shape, 5.0), 200.0, 61) - 0.5) * 2.0 + (1.0 - bulge) * 600.0
    return np.where((x < 1500) | (x > 2700), 1.0e9, out)


def inland(x, z):
    x = np.asarray(x, dtype=np.int64)
    z = np.asarray(z, dtype=np.int64)
    c = coast_z(x)
    a = awaji_z(x)
    zf = z.astype(np.float64)
    return np.where(zf <= c, c - zf, np.where(zf >= a, zf - a, -np.minimum(zf - c, a - zf)))


def height(x, z):
    x = np.asarray(x, dtype=np.int64)
    z = np.asarray(z, dtype=np.int64)
    d = inland(x, z)
    depth = np.minimum(18.0, -d * 0.12)
    sea_h = np.floor(58.0 - depth)
    n = fbm(x, z)
    h = 68.0 + n * 44.0
    ridge = np.maximum(0.0, np.minimum(1.0, (420.0 - z) / 260.0))
    h = np.where(x > 3000, h + ridge * (40.0 + 50.0 * noise(x, z, 160.0, 71)), h)
    h = np.where(d < 60.0, 63.0 + (h - 63.0) * (d / 60.0), h)
    land_h = np.floor(h)
    return np.where(d < 0, sea_h, land_h).astype(np.int64)


def surface(x, z, h):
    d = inland(x, z)
    sea = np.where(hash_np(x, z, 91) < 0.3, 3, 2)
    land = np.where((h <= 64) & (d < 14), 1, 0)
    return np.where(d < 0, sea, land)


def forest(x, z):
    return noise(x, z, 140.0, 83)
