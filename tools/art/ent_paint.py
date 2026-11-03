"""Painting helpers for the spirits' box-UV textures at S texels per model pixel.

Face-local coordinates (x right, y down, in texels) follow the vanilla box layout seen from outside the cube:
  front  = -z face as seen by someone facing the model (x from the model's right (-x) to its left (+x))
  back   = +z face seen from behind (x from the model's left to its right)
  right  = -x side seen from that side (x from back to front);  left = +x side (x from front to back)
  top    = rows from back (y=0) to front, columns from -x to +x;  bottom = same orientation

Every texel also knows where it sits on the model in the rest pose (model pixels, y down) and its outward
normal, so materials can be written as 3D fields (dirt rising from the floor, wet hems, soot, occlusion near the
joints) that continue seamlessly over cube edges and between parts.
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from common import C, rng, fnoise, lighten_arr, to_lab, from_lab

FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')
NORMALS = {'top': (0, -1, 0), 'bottom': (0, 1, 0), 'right': (-1, 0, 0), 'front': (0, 0, -1), 'left': (1, 0, 0),
           'back': (0, 0, 1)}


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.float32)


def col(c):
    return C(c) if not isinstance(c, np.ndarray) else c.astype(np.float32)


class Face:
    """One face of one cube on the hi-res texture."""

    def __init__(self, img, cube, name, S):
        self.img = img
        self.cube = cube
        self.name = name
        self.S = S
        u0, v0, w, h = cube.regions()[name]
        self.u0, self.v0 = int(round(u0 * S)), int(round(v0 * S))
        self.w, self.h = int(round(w * S)), int(round(h * S))
        self._pos = None

    # ---------------------------------------------------------------- pixel ops (face-local texels)
    def ok(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        if c is None:
            return
        x, y = int(x), int(y)
        if self.ok(x, y):
            self.img.a[self.v0 + y, self.u0 + x] = col(c)

    def get(self, x, y):
        return self.img.a[self.v0 + int(y), self.u0 + int(x)].copy()

    def blend(self, x, y, c, a):
        x, y = int(x), int(y)
        if self.ok(x, y) and a > 0:
            px = self.img.a[self.v0 + y, self.u0 + x]
            cc = col(c)
            if px[3] <= 0:
                px[:] = cc
                px[3] = cc[3] * min(1.0, a) if a < 1 else cc[3]
                return
            px[:3] = px[:3] * (1 - a) + cc[:3] * a

    def mul(self, x, y, f):
        x, y = int(x), int(y)
        if self.ok(x, y):
            self.img.a[self.v0 + y, self.u0 + x, :3] *= f

    def dl(self, x, y, d):
        x, y = int(x), int(y)
        if self.ok(x, y):
            px = self.img.a[self.v0 + y, self.u0 + x]
            if px[3] > 0:
                self.img.a[self.v0 + y, self.u0 + x] = lighten_arr(px[None], d)[0]

    def clear(self, x, y):
        x, y = int(x), int(y)
        if self.ok(x, y):
            self.img.a[self.v0 + y, self.u0 + x] = 0

    def alpha(self, x, y, a):
        x, y = int(x), int(y)
        if self.ok(x, y):
            self.img.a[self.v0 + y, self.u0 + x, 3] = a

    def fill(self, c):
        self.img.a[self.v0:self.v0 + self.h, self.u0:self.u0 + self.w] = col(c)

    def fill_fn(self, fn):
        for y in range(self.h):
            for x in range(self.w):
                c = fn(x, y)
                if c is not None:
                    self.set(x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.set(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(int(min(x0, x1)), int(max(x0, x1)) + 1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(int(min(y0, y1)), int(max(y0, y1)) + 1):
            self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c, a=1.0):
        for (x, y) in bres(x0, y0, x1, y1):
            if a >= 1.0:
                self.set(x, y, c)
            else:
                self.blend(x, y, c, a)

    def rows(self, rows, legend, ox=0, oy=0):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                c = legend.get(ch)
                if c is not None:
                    if isinstance(c, str) and c == 'CLEAR':
                        self.clear(ox + i, oy + j)
                    else:
                        self.set(ox + i, oy + j, c)

    def view(self):
        """numpy view (h, w, 4) of the face region."""
        return self.img.a[self.v0:self.v0 + self.h, self.u0:self.u0 + self.w]

    # ---------------------------------------------------------------- 3D
    def positions(self):
        """(h, w, 3) model-space rest positions (pixels, y down) of the texel centres."""
        if self._pos is None:
            c = self.cube
            ox, oy, oz = c.origin
            w, h, d = c.size
            x0, y0, z0, x1, y1, z1 = ox, oy, oz, ox + w, oy + h, oz + d
            ys, xs = np.mgrid[0:self.h, 0:self.w].astype(np.float64)
            s = (xs + 0.5) / max(self.w, 1)
            t = (ys + 0.5) / max(self.h, 1)
            n = self.name
            if n == 'front':
                P = np.stack([x0 + s * (x1 - x0), y0 + t * (y1 - y0), np.full_like(s, z0)], -1)
            elif n == 'back':
                P = np.stack([x1 - s * (x1 - x0), y0 + t * (y1 - y0), np.full_like(s, z1)], -1)
            elif n == 'right':
                P = np.stack([np.full_like(s, x0), y0 + t * (y1 - y0), z1 - s * (z1 - z0)], -1)
            elif n == 'left':
                P = np.stack([np.full_like(s, x1), y0 + t * (y1 - y0), z0 + s * (z1 - z0)], -1)
            elif n == 'top':
                P = np.stack([x0 + s * (x1 - x0), np.full_like(s, y0), z1 - t * (z1 - z0)], -1)
            else:  # bottom
                P = np.stack([x0 + s * (x1 - x0), np.full_like(s, y1), z1 - t * (z1 - z0)], -1)
            M = c.part.rest_matrix()
            self._pos = P @ M[:3, :3].T + M[:3, 3]
            nrm = np.array(NORMALS[n], float) @ M[:3, :3].T
            self._nrm = nrm / (np.linalg.norm(nrm) + 1e-12)
        return self._pos

    def normal(self):
        self.positions()
        return self._nrm


def bres(x0, y0, x1, y1):
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return pts


def faces(img, cube, S):
    return {n: Face(img, cube, n, S) for n in FACES}


# ------------------------------------------------------------------------------------------------ 3D noise
class Noise3:
    """Smooth 3D value noise (deterministic), evaluated on arrays of model-space points (pixels)."""

    def __init__(self, seed, scale=4.0):
        self.r = rng(seed).random(4096).astype(np.float64)
        self.scale = scale

    def _h(self, ix, iy, iz):
        k = (ix * 73856093 ^ iy * 19349663 ^ iz * 83492791) & 4095
        return self.r[k]

    def __call__(self, P, scale=None):
        s = self.scale if scale is None else scale
        p = P / s
        i = np.floor(p).astype(np.int64)
        f = p - i
        u = f * f * (3 - 2 * f)
        out = 0.0
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    w = (u[..., 0] if dx else 1 - u[..., 0]) * (u[..., 1] if dy else 1 - u[..., 1]) * \
                        (u[..., 2] if dz else 1 - u[..., 2])
                    out = out + w * self._h(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz)
        return out * 2 - 1

    def fbm(self, P, scale=None, octaves=3, gain=0.5):
        s = self.scale if scale is None else scale
        tot, amp, norm = 0.0, 1.0, 0.0
        for o in range(octaves):
            tot = tot + self(P * (2 ** o) + o * 17.3, s) * amp
            norm += amp
            amp *= gain
        return tot / norm


# ------------------------------------------------------------------------------------------------ colour utils
def ramp_pick(P, v):
    """Pick colours from a dark->light palette P (n,4) with a float index array v (rounded)."""
    k = np.clip(np.round(v), 0, len(P) - 1).astype(int)
    return P[k]


def lerp_rgb(a, b, t):
    a, b = col(a), col(b)
    t = np.asarray(t, np.float32)[..., None]
    return a * (1 - t) + b * t


def shade_arr(arr, dl):
    """OKLab lightness shift of an (...,4) array by an array dl."""
    lab = to_lab(arr[..., :3])
    lab[..., 0] += dl
    out = arr.copy()
    out[..., :3] = from_lab(lab)
    return out


def paint_material(img, cube, S, fn, which=FACES):
    """fn(face, P (h,w,3), N (3,), X (h,w), Y (h,w)) -> (h,w,4) colours (alpha used)."""
    fs = faces(img, cube, S)
    for n in which:
        f = fs[n]
        if f.w <= 0 or f.h <= 0:
            continue
        P = f.positions()
        Y, X = np.mgrid[0:f.h, 0:f.w]
        out = fn(f, P, f.normal(), X, Y)
        if out is not None:
            f.view()[:] = out
    return fs


def text_mask(text, font_path, size, w, h, index=0, threshold=0.4, dx=0, dy=0, stroke=0):
    """Rasterises text into a boolean (h, w) mask (for badges, tags)."""
    big = 8
    img = Image.new('L', (w * big, h * big), 0)
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(font_path, size * big, index=index)
    bb = d.textbbox((0, 0), text, font=f, stroke_width=stroke * big)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((w * big - tw) / 2 - bb[0] + dx * big, (h * big - th) / 2 - bb[1] + dy * big), text, fill=255, font=f,
           stroke_width=stroke * big, stroke_fill=255)
    a = np.asarray(img.resize((w, h), Image.BOX), np.float32) / 255.0
    return a


def finalize_eyes(img):
    """Eyes layer: RGB must be zero wherever alpha is zero (RenderType.eyes adds RGB with ONE, ONE)."""
    a = img.a.copy()
    m = a[..., 3] <= 0
    a[m] = 0
    a[~m, 3] = 255
    return np.clip(np.round(a), 0, 255).astype(np.uint8)


def finalize_base(img, cutout=True):
    from common import bleed_rgb
    a = img.a.copy()
    if cutout:
        a[..., 3] = np.where(a[..., 3] >= 128, 255, 0)
    a = bleed_rgb(a)
    return np.clip(np.round(a), 0, 255).astype(np.uint8)
