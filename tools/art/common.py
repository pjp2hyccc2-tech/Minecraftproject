"""Shared helpers for the MEIKON procedural texture generator.

Everything here is deterministic: every random source takes an explicit seed.
Colours are float32 RGBA arrays in 0..255.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
TEX_ROOT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'meikon', 'textures')
PREVIEW = os.path.join(HERE, 'preview')

FONT_SERIF_JP_BLACK = ('/usr/share/fonts/opentype/noto/NotoSerifCJK-Black.ttc', 0)
FONT_SERIF_JP_BOLD = ('/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc', 0)
FONT_SERIF_JP_REG = ('/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc', 0)
FONT_LABEL = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'

# --------------------------------------------------------------------------
# registry
# --------------------------------------------------------------------------
REGISTRY = {}


def texture(path, size=(16, 16), cutout=False, opaque=False, frames=1, tile=False):
    """Register a generator. path is relative to textures/ without .png."""
    def deco(fn):
        REGISTRY[path] = dict(fn=fn, size=size, cutout=cutout, opaque=opaque,
                              frames=frames, tile=tile, name=path)
        return fn
    return deco


# --------------------------------------------------------------------------
# colour
# --------------------------------------------------------------------------
def C(c, a=None):
    """Anything colour-like -> float32 RGBA array."""
    if isinstance(c, str):
        h = c.lstrip('#')
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        al = int(h[6:8], 16) if len(h) >= 8 else 255
        out = np.array([r, g, b, al], np.float32)
    else:
        arr = np.asarray(c, np.float32)
        if arr.shape[-1] == 3:
            arr = np.concatenate([arr, [255.0]])
        out = arr.astype(np.float32).copy()
    if a is not None:
        out[3] = a
    return out


def _s2l(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _l2s(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, 12.92 * c, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def to_lab(rgb):
    c = _s2l(np.asarray(rgb, np.float64)[..., :3] / 255.0)
    l = 0.4122214708 * c[..., 0] + 0.5363325363 * c[..., 1] + 0.0514459929 * c[..., 2]
    m = 0.2119034982 * c[..., 0] + 0.6806995451 * c[..., 1] + 0.1073969566 * c[..., 2]
    s = 0.0883024619 * c[..., 0] + 0.2817188376 * c[..., 1] + 0.6299787005 * c[..., 2]
    l_, m_, s_ = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return np.stack([0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
                     1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
                     0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_], -1)


def from_lab(lab):
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return np.clip(_l2s(np.stack([r, g, bb], -1)) * 255.0, 0, 255)


def ramp(stops, n):
    """n colours interpolated (OKLab) through the given hex stops, dark->light."""
    labs = to_lab(np.array([C(s)[:3] for s in stops]))
    out = []
    for ti in np.linspace(0, len(stops) - 1, n):
        i = min(int(ti), len(stops) - 2)
        f = ti - i
        lab = labs[i] * (1 - f) + labs[i + 1] * f
        out.append(np.append(from_lab(lab), 255.0))
    return np.array(out, np.float32)


def pal(*hexes):
    return np.array([C(h) for h in hexes], np.float32)


def mix(c1, c2, t):
    c1, c2 = C(c1), C(c2)
    return c1 * (1 - t) + c2 * t


def shade(c, dl, dh=0.0, dc=1.0):
    """Shift a colour in OKLab: dl lightness (+-0..1), dh hue rotation (rad), dc chroma scale."""
    c = C(c)
    lab = to_lab(c[:3])
    L = lab[0] + dl
    a, b = lab[1], lab[2]
    ch = math.hypot(a, b) * dc
    h = math.atan2(b, a) + dh
    rgb = from_lab(np.array([L, ch * math.cos(h), ch * math.sin(h)]))
    return np.array([rgb[0], rgb[1], rgb[2], c[3]], np.float32)


def lighten_arr(arr, dl):
    """Shift lightness of an (...,4) array (alpha kept)."""
    lab = to_lab(arr[..., :3])
    lab[..., 0] += dl
    out = arr.copy()
    out[..., :3] = from_lab(lab)
    return out


def desaturate(arr, amount):
    lab = to_lab(arr[..., :3])
    lab[..., 1:] *= (1 - amount)
    out = arr.copy()
    out[..., :3] = from_lab(lab)
    return out


# --------------------------------------------------------------------------
# noise (all periodic / tileable unless stated)
# --------------------------------------------------------------------------
def rng(seed):
    return np.random.default_rng(seed)


def fnoise(w, h, seed, beta=1.0, sx=1.0, sy=1.0, lo=0.0, hi=None):
    """Tileable 1/f^beta noise via FFT, normalised to mean 0 / std 1.
    sx > sy stretches features horizontally (streaks along x).
    lo/hi: band limits in integer frequency radius."""
    r_ = rng(seed)
    wn = r_.standard_normal((h, w))
    F = np.fft.fft2(wn)
    fy = np.fft.fftfreq(h)[:, None] * h
    fx = np.fft.fftfreq(w)[None, :] * w
    r = np.sqrt((fx * sx) ** 2 + (fy * sy) ** 2)
    r0 = r.copy()
    r[0, 0] = 1.0
    amp = 1.0 / np.power(r, beta)
    amp[0, 0] = 0.0
    if lo:
        amp *= (r0 >= lo)
    if hi:
        amp *= np.exp(-(r0 / hi) ** 4)
    out = np.real(np.fft.ifft2(F * amp))
    out -= out.mean()
    sd = out.std()
    return out / (sd if sd > 1e-9 else 1.0)


def vnoise(w, h, cx, cy, seed, smooth=True):
    """Tileable value noise with a cx*cy lattice (cx | w recommended). Range 0..1."""
    r_ = rng(seed)
    lat = r_.random((cy, cx))
    ys = np.arange(h) * cy / h
    xs = np.arange(w) * cx / w
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    if smooth:
        fy = fy * fy * (3 - 2 * fy)
        fx = fx * fx * (3 - 2 * fx)
    y1 = (y0 + 1) % cy
    x1 = (x0 + 1) % cx
    y0 %= cy
    x0 %= cx
    a = lat[np.ix_(y0, x0)]
    b = lat[np.ix_(y0, x1)]
    c = lat[np.ix_(y1, x0)]
    d = lat[np.ix_(y1, x1)]
    fx = fx[None, :]
    fy = fy[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def white(w, h, seed):
    return rng(seed).random((h, w))


def norm01(a):
    a = np.asarray(a, np.float64)
    mn, mx = a.min(), a.max()
    return (a - mn) / (mx - mn + 1e-12)


def equalize(a):
    """Rank transform -> uniform 0..1 (controls exactly how much of each shade appears)."""
    flat = a.ravel()
    order = np.argsort(flat, kind='stable')
    ranks = np.empty_like(order, dtype=np.float64)
    ranks[order] = np.arange(flat.size)
    return (ranks / max(flat.size - 1, 1)).reshape(a.shape)


BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0 - 0.5


def bayer(w, h):
    return np.tile(BAYER4, (h // 4 + 1, w // 4 + 1))[:h, :w]


def quant(field, palette, dither=0.0):
    """Map a 0..1 field onto a palette (dark->light) with optional ordered dither."""
    palette = np.asarray(palette, np.float32)
    n = len(palette)
    h, w = field.shape
    idx = np.floor(np.clip(field, 0, 0.99999) * n + bayer(w, h) * dither)
    idx = np.clip(idx, 0, n - 1).astype(int)
    return palette[idx]


def quant_idx(field, n, dither=0.0):
    h, w = field.shape
    idx = np.floor(np.clip(field, 0, 0.99999) * n + bayer(w, h) * dither)
    return np.clip(idx, 0, n - 1).astype(int)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# --------------------------------------------------------------------------
# canvas
# --------------------------------------------------------------------------
class Tex:
    """Small RGBA float canvas with wrap-around pixel drawing."""

    def __init__(self, w, h=None, fill=None):
        h = w if h is None else h
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.float32)
        if fill is not None:
            self.a[:] = C(fill)

    @classmethod
    def from_array(cls, arr):
        t = cls(arr.shape[1], arr.shape[0])
        t.a[:] = arr
        return t

    def copy(self):
        return Tex.from_array(self.a.copy())

    # --- pixel ops
    def set(self, x, y, c, wrap=True):
        x, y = int(x), int(y)
        if wrap:
            x %= self.w
            y %= self.h
        elif not (0 <= x < self.w and 0 <= y < self.h):
            return
        self.a[y, x] = C(c)

    def get(self, x, y):
        return self.a[int(y) % self.h, int(x) % self.w].copy()

    def blend(self, x, y, c, t=None, wrap=True):
        """Alpha-composite c over pixel (t overrides c's alpha, 0..1)."""
        x, y = int(x), int(y)
        if wrap:
            x %= self.w
            y %= self.h
        elif not (0 <= x < self.w and 0 <= y < self.h):
            return
        c = C(c)
        al = (c[3] / 255.0) if t is None else t
        dst = self.a[y, x]
        out_a = al + dst[3] / 255.0 * (1 - al)
        if out_a <= 1e-6:
            return
        rgb = (c[:3] * al + dst[:3] * (dst[3] / 255.0) * (1 - al)) / out_a
        self.a[y, x, :3] = rgb
        self.a[y, x, 3] = out_a * 255.0

    def mul(self, x, y, f, wrap=True):
        x, y = int(x) % self.w if wrap else int(x), int(y) % self.h if wrap else int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, :3] *= f

    def dl(self, x, y, d, wrap=True):
        """Lighten/darken one pixel in OKLab."""
        x, y = int(x), int(y)
        if wrap:
            x %= self.w
            y %= self.h
        elif not (0 <= x < self.w and 0 <= y < self.h):
            return
        self.a[y, x] = lighten_arr(self.a[y, x][None], d)[0]

    def clear(self, x, y, wrap=True):
        x, y = int(x), int(y)
        if wrap:
            x %= self.w
            y %= self.h
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, 3] = 0

    # --- shapes (inclusive coordinates)
    def rect(self, x0, y0, x1, y1, c, wrap=False):
        if wrap:
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    self.set(x, y, c)
        else:
            x0c, x1c = max(0, x0), min(self.w - 1, x1)
            y0c, y1c = max(0, y0), min(self.h - 1, y1)
            if x0c <= x1c and y0c <= y1c:
                self.a[y0c:y1c + 1, x0c:x1c + 1] = C(c)

    def hline(self, x0, x1, y, c, wrap=True):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.set(x, y, c, wrap)

    def vline(self, x, y0, y1, c, wrap=True):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.set(x, y, c, wrap)

    def line(self, x0, y0, x1, y1, c, wrap=True, blend=None):
        for (x, y) in bresenham(x0, y0, x1, y1):
            if blend is None:
                self.set(x, y, c, wrap)
            else:
                self.blend(x, y, c, blend, wrap)

    def pts(self, pts, c, wrap=True):
        for x, y in pts:
            self.set(x, y, c, wrap)

    def mask_fill(self, mask, c, t=1.0):
        c = C(c)
        m = mask.astype(np.float32) * t
        self.a[..., :3] = self.a[..., :3] * (1 - m[..., None]) + c[:3] * m[..., None]
        self.a[..., 3] = np.maximum(self.a[..., 3], (c[3] * (mask > 0)).astype(np.float32)) \
            if c[3] >= 255 else self.a[..., 3] * (1 - m) + c[3] * m

    def paste(self, other, x, y, wrap=False):
        o = other.a if isinstance(other, Tex) else other
        for yy in range(o.shape[0]):
            for xx in range(o.shape[1]):
                if o[yy, xx, 3] > 0:
                    self.blend(x + xx, y + yy, o[yy, xx], None, wrap)

    def img(self):
        return Image.fromarray(np.clip(np.round(self.a), 0, 255).astype(np.uint8), 'RGBA')


def bresenham(x0, y0, x1, y1):
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


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w]
    return x.astype(np.float64), y.astype(np.float64)


def wrapdist(a, b, period):
    d = np.abs(a - b) % period
    return np.minimum(d, period - d)


def blob(w, h, cx, cy, rx, ry, seed, rough=0.35, wrap=True, cells=4):
    """Irregular blob field (>0 = inside) centred at cx,cy with radii rx,ry."""
    x, y = grid(w, h)
    if wrap:
        dx = (x - cx + w / 2) % w - w / 2
        dy = (y - cy + h / 2) % h - h / 2
    else:
        dx, dy = x - cx, y - cy
    d = np.sqrt((dx / rx) ** 2 + (dy / ry) ** 2)
    n = vnoise(w, h, cells, cells, seed)
    return 1.0 - d + (n - 0.5) * 2 * rough


def dilate(mask, it=1, wrap=False):
    m = mask.copy()
    for _ in range(it):
        if wrap:
            m = m | np.roll(m, 1, 0) | np.roll(m, -1, 0) | np.roll(m, 1, 1) | np.roll(m, -1, 1)
        else:
            p = np.pad(m, 1)
            m = p[1:-1, 1:-1] | p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:]
    return m


def erode(mask, it=1, wrap=False):
    return ~dilate(~mask, it, wrap)


def edge(mask, wrap=False):
    return mask & ~erode(mask, 1, wrap)


# --------------------------------------------------------------------------
# finishing
# --------------------------------------------------------------------------
def bleed_rgb(arr, iterations=16):
    """Fill RGB of fully transparent pixels from opaque neighbours (avoids dark mip fringes)."""
    a = arr.copy()
    known = a[..., 3] > 0
    if known.all() or not known.any():
        if not known.any():
            a[..., :3] = 40
        return a
    rgb = a[..., :3].copy()
    for _ in range(iterations):
        if known.all():
            break
        acc = np.zeros_like(rgb)
        cnt = np.zeros(known.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            k = np.roll(np.roll(known, dy, 0), dx, 1)
            r = np.roll(np.roll(rgb, dy, 0), dx, 1)
            acc += r * k[..., None]
            cnt += k
        newk = (~known) & (cnt > 0)
        rgb[newk] = acc[newk] / cnt[newk][..., None]
        known = known | newk
    rgb[~known] = rgb[known].mean(0) if known.any() else 40
    a[..., :3] = np.where((a[..., 3] > 0)[..., None], a[..., :3], rgb)
    return a


def finalize(arr, cutout=False, opaque=False):
    a = np.asarray(arr, np.float32).copy()
    if opaque:
        a[..., 3] = 255
    if cutout:
        a[..., 3] = np.where(a[..., 3] >= 128, 255, 0)
    if (a[..., 3] < 255).any():
        a = bleed_rgb(a)
    return np.clip(np.round(a), 0, 255).astype(np.uint8)


def to_array(obj):
    if isinstance(obj, Tex):
        return obj.a
    if isinstance(obj, Image.Image):
        return np.asarray(obj.convert('RGBA'), np.float32)
    return np.asarray(obj, np.float32)


def upscale(arr, k):
    return np.repeat(np.repeat(arr, k, 0), k, 1)


def font(spec, size):
    if isinstance(spec, tuple):
        return ImageFont.truetype(spec[0], size, index=spec[1])
    return ImageFont.truetype(spec, size)
