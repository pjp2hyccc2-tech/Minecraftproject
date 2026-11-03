"""Shared builders and painting tools for the spirit specs."""
import math

import numpy as np

from common import C
from ent_model import Part, Cube
from ent_paint import Img, faces, Noise3


def card(name, pivot, origin, size, mat, tag=None, faces=('front',), rot=(0, 0, 0)):
    """A flat card (depth 0) with a single polygon: mouths, cracks, glasses, hair curtains. One polygon only, so it
    never doubles up over itself when the model is translucent (NO_CULL still shows it from behind)."""
    return Part(name, pivot, rot, cubes=[Cube(origin, size, mat, tag=tag or name, faces=faces)])


def pal_arr(hexes):
    return np.array([C(h) for h in hexes], np.float32)


def P(hexes, i):
    return C(hexes[int(max(0, min(len(hexes) - 1, round(i))))])


class Painter:
    """Hi-res texture + eyes layer for a spec, with shading helpers working on 3D texel positions."""

    def __init__(self, spec, seed):
        self.spec = spec
        self.S = spec.px
        self.img = Img(spec.tex_w * self.S, spec.tex_h * self.S)
        self.eyes = Img(spec.tex_w * self.S, spec.tex_h * self.S)
        self.n3 = Noise3(seed, 3.0)
        self.n3b = Noise3(seed + 1, 1.2)
        self.n3c = Noise3(seed + 2, 6.0)

    def F(self, tag):
        return faces(self.img, self.spec.cube(tag), self.S)

    def EF(self, tag):
        return faces(self.eyes, self.spec.cube(tag), self.S)

    def nz(self, f, x, y, a=0.45, b=0.3):
        pp = f.positions()[int(y), int(x)][None]
        return float(self.n3(pp)[0] * a + self.n3b(pp)[0] * b)

    def base(self, tag, palette, mid, amp=0.6, top=0.5, fine=0.35, fn=None, which=None):
        """Shades every face of a cube from a dark->light palette: 3D noise, up-facing lighter, plus fn(P, nrm)
        (an extra offset array) if given."""
        pa = pal_arr(palette)
        fs = self.F(tag)
        for n, f in fs.items():
            if f.w <= 0 or f.h <= 0 or (which and n not in which):
                continue
            Pp = f.positions()
            nrm = f.normal()
            v = mid + self.n3(Pp) * amp + self.n3b(Pp) * fine - nrm[1] * top
            if fn is not None:
                v = v + fn(Pp, nrm, n)
            k = np.clip(np.round(v), 0, len(pa) - 1).astype(int)
            f.view()[:] = pa[k]
        return fs

    def pattern(self, f, rows, legend, ox=0, oy=0, jitter=True, a=0.45, b=0.3):
        """legend: char -> (palette, index) | colour array | 'CLEAR' | None."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                e = legend.get(ch)
                if e is None:
                    continue
                if isinstance(e, str) and e == 'CLEAR':
                    f.clear(ox + x, oy + y)
                elif isinstance(e, tuple):
                    pal_, i = e
                    v = i + (self.nz(f, ox + x, oy + y, a, b) if jitter else 0.0)
                    f.set(ox + x, oy + y, P(pal_, v))
                else:
                    f.set(ox + x, oy + y, e)

    def each(self, f, fn):
        for y in range(f.h):
            for x in range(f.w):
                fn(x, y)


def lerp(a, b, t):
    return a + (b - a) * t
