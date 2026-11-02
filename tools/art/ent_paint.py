"""Painting helpers for entity box-UV textures.

Face-local coordinates (x right, y down) follow the vanilla box layout seen from outside the cube:
  front  = -z face, x runs from the model's right (-x) to its left (+x), y from top to bottom
  back   = +z face seen from behind (x from the model's left to its right)
  right  = -x side seen from the right (x from back to front);  left = +x side (x from front to back)
  top    = rows from back (y=0) to front, columns from -x to +x;   bottom = same orientation
"""
import math

import numpy as np

from common import C, ramp, pal, fnoise, rng, lighten_arr

FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.float32)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[int(y), int(x)] = C(c)

    def get(self, x, y):
        return self.a[int(y), int(x)].copy()


class Face:
    def __init__(self, img, rect, name, cube):
        self.img = img
        self.u0, self.v0, self.w, self.h = rect
        self.name = name
        self.cube = cube

    def set(self, x, y, c):
        if c is None:
            return
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.set(self.u0 + x, self.v0 + y, c)

    def blend(self, x, y, c, a):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            px = self.img.a[self.v0 + y, self.u0 + x]
            cc = C(c)
            px[:3] = px[:3] * (1 - a) + cc[:3] * a
            px[3] = max(px[3], cc[3] * a if px[3] == 0 else px[3])

    def dl(self, x, y, d):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            px = self.img.a[self.v0 + y, self.u0 + x]
            if px[3] > 0:
                self.img.a[self.v0 + y, self.u0 + x] = lighten_arr(px[None], d)[0]

    def clear(self, x, y):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.a[self.v0 + y, self.u0 + x] = 0

    def fill(self, fn):
        for y in range(self.h):
            for x in range(self.w):
                c = fn(x, y, self)
                if c is not None:
                    self.set(x, y, c)

    def rows(self, rows, legend, ox=0, oy=0):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                c = legend.get(ch)
                if c is not None:
                    self.set(ox + i, oy + j, c)


def faces(img, cube):
    return {n: Face(img, r, n, cube) for n, r in cube.regions().items()}


def fill_cube(img, cube, fn, which=FACES):
    fs = faces(img, cube)
    for n in which:
        if fs[n].w > 0 and fs[n].h > 0:
            fs[n].fill(lambda x, y, f, n=n: fn(n, x, y, f))
    return fs


# ------------------------------------------------------------------------------------------------ materials
def material(P, seed, base=None, amp=0.7, beta=1.0, grad=0.9, top_lift=0.6, streak=False, alpha=255, jitter=0.0):
    """Generic shaded material over a palette P (dark -> light): noise, darker towards the bottom of the cube,
    lighter top faces, darker bottom faces. Returns fn(face, x, y, f)."""
    n = len(P)
    base = (n - 1) * 0.55 if base is None else base
    N = fnoise(64, 64, seed, beta=beta, sx=1.0, sy=(6.0 if streak else 1.0))
    r = rng(seed + 7)
    J = r.normal(0, 1, (64, 64))

    def fn(face, x, y, f):
        cu, cv = f.u0, f.v0
        v = base + N[(cv + y) % 64, (cu + x) % 64] * amp + J[(cv + y) % 64, (cu + x) % 64] * jitter
        if face in ('front', 'back', 'left', 'right') and f.h > 1:
            v -= grad * (y / (f.h - 1) - 0.4)
        if face == 'top':
            v += top_lift
        elif face == 'bottom':
            v -= top_lift * 1.4
        k = int(np.clip(round(v), 0, n - 1))
        c = P[k].copy()
        c[3] = alpha
        return c
    return fn


def hair_material(P, seed, alpha=255, gaps=None):
    """Vertical strands: per column tone, occasional lighter wet strand, ragged ends handled by caller."""
    r = rng(seed)
    col = r.normal(0, 0.6, 256)
    wet = r.random(256) < 0.12

    def fn(face, x, y, f):
        gx = f.u0 + x
        v = 1.4 + col[gx % 256] + (0.9 if wet[gx % 256] and y % 5 != 0 else 0) - (0.6 if (y + gx) % 7 == 0 else 0)
        if face == 'top':
            v = 1.2 + col[(gx + y) % 256] * 0.5
        k = int(np.clip(round(v), 0, len(P) - 1))
        c = P[k].copy()
        c[3] = alpha
        return c
    return fn


def ragged_bottom(face, seed, maxcut=3, every=1):
    r = rng(seed)
    for x in range(face.w):
        cut = int(r.integers(0, maxcut + 1))
        for y in range(face.h - cut, face.h):
            face.clear(x, y)


def finalize_eyes(img):
    """Eyes layer: RGB must be zero wherever alpha is zero (the layer is drawn additively)."""
    a = img.a.copy()
    m = a[..., 3] <= 0
    a[m] = 0
    return np.clip(np.round(a), 0, 255).astype(np.uint8)


def finalize_base(img, cutout=True):
    from common import bleed_rgb
    a = img.a.copy()
    if cutout:
        a[..., 3] = np.where(a[..., 3] >= 128, 255, 0)
    a = bleed_rgb(a)
    return np.clip(np.round(a), 0, 255).astype(np.uint8)
