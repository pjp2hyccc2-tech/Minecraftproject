"""Numpy re-implementation of Minecraft's ImprovedNoise / PerlinNoise / NormalNoise (statistically equivalent,
not bit-exact) used to calibrate the quest-map density functions before shipping them."""
import numpy as np

GRAD = np.array([[1, 1, 0], [-1, 1, 0], [1, -1, 0], [-1, -1, 0], [1, 0, 1], [-1, 0, 1], [1, 0, -1], [-1, 0, -1],
                 [0, 1, 1], [0, -1, 1], [0, 1, -1], [0, -1, -1], [1, 1, 0], [0, -1, 1], [-1, 1, 0], [0, -1, -1]], dtype=np.float64)


class Improved:
    def __init__(self, rng):
        self.xo, self.yo, self.zo = rng.random() * 256, rng.random() * 256, rng.random() * 256
        p = np.arange(256)
        rng.shuffle(p)
        self.p = np.concatenate([p, p])

    def noise(self, x, y, z):
        x = x + self.xo; y = y + self.yo; z = z + self.zo
        xi = np.floor(x).astype(np.int64); yi = np.floor(y).astype(np.int64); zi = np.floor(z).astype(np.int64)
        xf = x - xi; yf = y - yi; zf = z - zi
        xi &= 255; yi &= 255; zi &= 255
        p = self.p

        def g(h, dx, dy, dz):
            gv = GRAD[h & 15]
            return gv[..., 0] * dx + gv[..., 1] * dy + gv[..., 2] * dz

        a = p[xi] + yi; aa = p[a] + zi; ab = p[a + 1] + zi
        b = p[xi + 1] + yi; ba = p[b] + zi; bb = p[b + 1] + zi
        u = xf ** 3 * (xf * (xf * 6 - 15) + 10); v = yf ** 3 * (yf * (yf * 6 - 15) + 10); w = zf ** 3 * (zf * (zf * 6 - 15) + 10)
        x1 = g(p[aa], xf, yf, zf) + u * (g(p[ba], xf - 1, yf, zf) - g(p[aa], xf, yf, zf))
        x2 = g(p[ab], xf, yf - 1, zf) + u * (g(p[bb], xf - 1, yf - 1, zf) - g(p[ab], xf, yf - 1, zf))
        y1 = x1 + v * (x2 - x1)
        x3 = g(p[aa + 1], xf, yf, zf - 1) + u * (g(p[ba + 1], xf - 1, yf, zf - 1) - g(p[aa + 1], xf, yf, zf - 1))
        x4 = g(p[ab + 1], xf, yf - 1, zf - 1) + u * (g(p[bb + 1], xf - 1, yf - 1, zf - 1) - g(p[ab + 1], xf, yf - 1, zf - 1))
        y2 = x3 + v * (x4 - x3)
        return y1 + w * (y2 - y1)


class Perlin:
    def __init__(self, rng, first_octave, amps):
        self.levels = [Improved(rng) if a != 0 else None for a in amps]
        self.amps = amps
        n = len(amps)
        self.freq0 = 2.0 ** first_octave
        self.val0 = 2.0 ** (n - 1) / (2.0 ** n - 1)

    def value(self, x, y, z):
        out = 0.0; f = self.freq0; v = self.val0
        for lvl, a in zip(self.levels, self.amps):
            if lvl is not None:
                out = out + a * lvl.noise(x * f, y * f, z * f) * v
            f *= 2; v /= 2
        return out


class Normal:
    def __init__(self, seed, first_octave, amps):
        rng = np.random.default_rng(seed)
        self.a = Perlin(rng, first_octave, amps)
        self.b = Perlin(rng, first_octave, amps)
        nz = [i for i, a in enumerate(amps) if a != 0]
        spread = nz[-1] - nz[0]
        self.factor = (1 / 6) / (0.1 * (1 + 1 / (spread + 1)))

    def value(self, x, y, z):
        k = 1.0181268882175227
        return (self.a.value(x, y, z) + self.b.value(x * k, y * k, z * k)) * self.factor
