"""Small pixel-art toolkit used by every ETERNAL REALM art generator.

Everything works on numpy RGBA arrays (height, width, 4) of uint8. The helpers are
deliberately simple and fully deterministic: no randomness here, callers that want
noise pass a seeded ``random.Random``.

Conventions
-----------
* Light comes from the upper left (slightly more from above).
* Every opaque shape gets a dark 4-connected outline (``outline``).
* Diagonal weapons use axis coordinates: ``t = x - y`` runs along the
  bottom-left -> top-right axis and ``s = x + y - (n - 1)`` runs across it
  (negative ``s`` = the upper-left, lit side).
"""
from __future__ import annotations

import math
import os
import zlib
from typing import Callable, Iterable, Sequence

import numpy as np
from PIL import Image, ImageDraw

Color = tuple  # (r, g, b, a)
CLEAR: Color = (0, 0, 0, 0)

# light direction (towards the light), screen space, normalised
LIGHT = (-0.6, -0.8)


# --------------------------------------------------------------------------- colours
def hexc(value: str, alpha: int = 255) -> Color:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def mix(a: Color, b: Color, f: float) -> Color:
    """Linear blend from a (f=0) to b (f=1); alpha taken from a unless both differ."""
    return tuple(int(round(a[i] + (b[i] - a[i]) * f)) for i in range(4))


def scale(c: Color, f: float) -> Color:
    return (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), c[3])


def with_alpha(c: Color, a: int) -> Color:
    return (c[0], c[1], c[2], a)


def luminance(c: Sequence[int]) -> float:
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


class Ramp:
    """Ordered shades of one material, index 0 = outline/darkest, last = highlight.

    Indexing clamps, so ``ramp[99]`` is always the highlight and ``ramp[-5]`` the outline.
    """

    def __init__(self, *colors: str | Color):
        self.c = [hexc(c) if isinstance(c, str) else tuple(c) for c in colors]

    def __getitem__(self, i: int) -> Color:
        return self.c[max(0, min(len(self.c) - 1, int(i)))]

    def __len__(self) -> int:
        return len(self.c)

    @property
    def out(self) -> Color:
        return self.c[0]

    @property
    def hi(self) -> Color:
        return self.c[-1]


def seed_of(name: str) -> int:
    """Stable seed from a name (never use hash(): it is salted per process)."""
    return zlib.crc32(name.encode("utf-8"))


# --------------------------------------------------------------------------- canvas
class Canvas:
    def __init__(self, w: int, h: int | None = None):
        h = w if h is None else h
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.uint8)

    # ---- basic access
    def inside(self, x: int, y: int) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x: int, y: int, c: Color | None) -> None:
        if c is None:
            return
        x, y = int(x), int(y)
        if self.inside(x, y):
            self.a[y, x] = c

    def get(self, x: int, y: int) -> Color:
        if self.inside(x, y):
            return tuple(int(v) for v in self.a[y, x])
        return CLEAR

    def filled(self, x: int, y: int) -> bool:
        return self.inside(x, y) and self.a[y, x, 3] > 0

    def mask(self) -> np.ndarray:
        return self.a[:, :, 3] > 0

    def copy(self) -> "Canvas":
        c = Canvas(self.w, self.h)
        c.a = self.a.copy()
        return c

    # ---- composition
    def paste(self, other: "Canvas", ox: int = 0, oy: int = 0) -> "Canvas":
        """Opaque-over composite (alpha > 0 pixels of ``other`` replace ours)."""
        for y in range(other.h):
            ty = y + oy
            if not 0 <= ty < self.h:
                continue
            for x in range(other.w):
                tx = x + ox
                if not 0 <= tx < self.w:
                    continue
                p = other.a[y, x]
                if p[3] == 0:
                    continue
                if p[3] == 255 or self.a[ty, tx, 3] == 0:
                    self.a[ty, tx] = p
                else:
                    f = p[3] / 255.0
                    base = self.a[ty, tx].astype(float)
                    self.a[ty, tx, :3] = np.round(base[:3] * (1 - f) + p[:3] * f)
                    self.a[ty, tx, 3] = max(int(base[3]), int(p[3]))
        return self

    def fill_mask(self, mask: np.ndarray, c: Color) -> None:
        self.a[mask] = c

    def paint(self, mask: np.ndarray, fn: Callable[[int, int], Color | None]) -> None:
        ys, xs = np.nonzero(mask)
        for x, y in zip(xs.tolist(), ys.tolist()):
            col = fn(x, y)
            if col is not None:
                self.a[y, x] = col

    # ---- outline
    def outline(self, color: Color | None = None, auto: float = 0.0, diag: bool = False,
                only_outer: bool = False) -> "Canvas":
        """Add a 1px ring on transparent pixels touching opaque ones.

        color: fixed outline colour. If None the colour is derived from the darkest
        touching pixel (``dark_of``). ``auto`` blends the derived colour into ``color``.
        """
        m = self.mask()
        ring = np.zeros_like(m)
        ring[1:, :] |= m[:-1, :]
        ring[:-1, :] |= m[1:, :]
        ring[:, 1:] |= m[:, :-1]
        ring[:, :-1] |= m[:, 1:]
        if diag:
            ring[1:, 1:] |= m[:-1, :-1]
            ring[1:, :-1] |= m[:-1, 1:]
            ring[:-1, 1:] |= m[1:, :-1]
            ring[:-1, :-1] |= m[1:, 1:]
        ring &= ~m
        ys, xs = np.nonzero(ring)
        for x, y in zip(xs.tolist(), ys.tolist()):
            if color is not None and auto <= 0:
                self.a[y, x] = color
                continue
            nb = []
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                if self.filled(x + dx, y + dy):
                    nb.append(self.get(x + dx, y + dy))
            if not nb:
                for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                    if self.filled(x + dx, y + dy):
                        nb.append(self.get(x + dx, y + dy))
            src = min(nb, key=luminance)
            d = dark_of(src)
            if color is not None:
                d = mix(color, d, auto)
            self.a[y, x] = d
        return self

    # ---- output
    def image(self) -> Image.Image:
        return Image.fromarray(self.a, "RGBA")

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.image().save(path, optimize=True)

    @staticmethod
    def load(path: str) -> "Canvas":
        im = Image.open(path).convert("RGBA")
        c = Canvas(im.width, im.height)
        c.a = np.array(im)
        return c


def dark_of(c: Color) -> Color:
    """Outline colour derived from a fill colour: very dark, keeps a hint of hue."""
    r, g, b = c[0], c[1], c[2]
    return (int(r * 0.22 + 14), int(g * 0.20 + 10), int(b * 0.24 + 20), 255)


# --------------------------------------------------------------------------- masks
def empty(w: int, h: int | None = None) -> np.ndarray:
    return np.zeros((w if h is None else h, w), bool)


def mask_fn(w: int, h: int, fn: Callable[[int, int], bool]) -> np.ndarray:
    m = empty(w, h)
    for y in range(h):
        for x in range(w):
            if fn(x, y):
                m[y, x] = True
    return m


def poly_mask(w: int, h: int, pts: Iterable[tuple[float, float]]) -> np.ndarray:
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).polygon([tuple(p) for p in pts], fill=255, outline=255)
    return np.array(im) > 0


def ellipse_mask(w: int, h: int, cx: float, cy: float, rx: float, ry: float | None = None) -> np.ndarray:
    ry = rx if ry is None else ry
    m = empty(w, h)
    for y in range(h):
        for x in range(w):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                m[y, x] = True
    return m


def line_pts(x0: int, y0: int, x1: int, y1: int) -> list[tuple[int, int]]:
    """Bresenham line, inclusive."""
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
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


def line_mask(w: int, h: int, x0, y0, x1, y1) -> np.ndarray:
    m = empty(w, h)
    for x, y in line_pts(int(x0), int(y0), int(x1), int(y1)):
        if 0 <= x < w and 0 <= y < h:
            m[y, x] = True
    return m


def ascii_mask(rows: Sequence[str], chars: str = "#") -> np.ndarray:
    h, w = len(rows), max(len(r) for r in rows)
    m = empty(w, h)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in chars:
                m[y, x] = True
    return m


def stamp(canvas: Canvas, rows: Sequence[str], pal: dict, ox: int = 0, oy: int = 0) -> None:
    """Draw ASCII art; characters missing from ``pal`` (and '.', ' ') are skipped."""
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in pal and pal[ch] is not None:
                canvas.put(ox + x, oy + y, pal[ch])


def shift(m: np.ndarray, dx: int, dy: int) -> np.ndarray:
    out = np.zeros_like(m)
    h, w = m.shape
    xs0, xs1 = max(0, dx), min(w, w + dx)
    ys0, ys1 = max(0, dy), min(h, h + dy)
    out[ys0:ys1, xs0:xs1] = m[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


def edge_in(m: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """Pixels of m whose neighbour at (dx, dy) is outside m."""
    return m & ~shift(m, -dx, -dy)


def grow(m: np.ndarray, diag: bool = False) -> np.ndarray:
    out = m.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        out |= shift(m, dx, dy)
    if diag:
        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            out |= shift(m, dx, dy)
    return out


def shrink(m: np.ndarray) -> np.ndarray:
    out = m.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        out &= shift(m, dx, dy)
    return out


# --------------------------------------------------------------------------- shading
def facing(m: np.ndarray, x: int, y: int, radius: int = 1) -> float:
    """-1..1: how much the boundary near (x, y) faces the light. 0 for interior pixels."""
    h, w = m.shape
    nx = ny = 0.0
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx == 0 and dy == 0:
                continue
            xx, yy = x + dx, y + dy
            outside = not (0 <= xx < w and 0 <= yy < h) or not m[yy, xx]
            if outside:
                d = math.hypot(dx, dy)
                nx += dx / d / d
                ny += dy / d / d
    n = math.hypot(nx, ny)
    if n < 1e-6:
        return 0.0
    return (nx * LIGHT[0] + ny * LIGHT[1]) / n


def bevel(canvas: Canvas, m: np.ndarray, ramp: Ramp, base: int = 2, grad: float = 0.0,
          radius: int = 1, strength: float = 1.6, hi_edge: bool = True) -> None:
    """Shade a region: lit edges facing the light, dark edges away from it.

    ``grad`` adds a large-scale top-left -> bottom-right gradient (in ramp steps).
    """
    ys, xs = np.nonzero(m)
    if len(xs) == 0:
        return
    cx, cy = xs.mean(), ys.mean()
    span = max(1.0, max(xs.max() - xs.min(), ys.max() - ys.min()) / 2)
    for x, y in zip(xs.tolist(), ys.tolist()):
        f = facing(m, x, y, radius)
        tone = base + f * strength
        if grad:
            g = ((x - cx) * LIGHT[0] + (y - cy) * LIGHT[1]) / span
            tone += g * grad
        t = int(math.floor(tone + 0.5))
        if not hi_edge:
            t = min(t, len(ramp) - 2)
        canvas.put(x, y, ramp[t])


def sphere(canvas: Canvas, cx: float, cy: float, r: float, ramp: Ramp, m: np.ndarray | None = None,
           spec: bool = True, lo: int = 1) -> None:
    """Ball shading inside a circle (or inside mask m) using the ramp from ``lo`` up."""
    top = len(ramp) - 1
    for y in range(canvas.h):
        for x in range(canvas.w):
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            inside = d2 <= 1.0 if m is None else m[y, x]
            if not inside:
                continue
            dz = math.sqrt(max(0.0, 1 - min(1.0, d2)))
            lam = -(dx * LIGHT[0] + dy * LIGHT[1]) * -1  # dot(normal, light)
            lam = dx * LIGHT[0] + dy * LIGHT[1] + dz * 0.55
            lam = max(0.0, min(1.0, (lam + 0.35) / 1.25))
            idx = lo + lam * (top - lo - (1 if spec else 0))
            canvas.put(x, y, ramp[int(round(idx))])
    if spec:
        sx = cx + LIGHT[0] * r * 0.45
        sy = cy + LIGHT[1] * r * 0.45
        canvas.put(int(math.floor(sx)), int(math.floor(sy)), ramp[top])


# --------------------------------------------------------------------------- diagonal axis helpers
def ts(x: int, y: int, n: int) -> tuple[int, int]:
    return x - y, x + y - (n - 1)


def xy(t: int, s: int, n: int) -> tuple[int, int] | None:
    if (t + s + n - 1) % 2:
        return None
    return (t + s + n - 1) // 2, (s - t + n - 1) // 2


def axis_mask(n: int, fn: Callable[[int, int], bool]) -> np.ndarray:
    m = empty(n)
    for y in range(n):
        for x in range(n):
            t, s = x - y, x + y - (n - 1)
            if fn(t, s):
                m[y, x] = True
    return m


def paint_ts(canvas: Canvas, m: np.ndarray, fn: Callable[[int, int], Color | None]) -> None:
    n = canvas.w
    ys, xs = np.nonzero(m)
    for x, y in zip(xs.tolist(), ys.tolist()):
        col = fn(x - y, x + y - (n - 1))
        if col is not None:
            canvas.a[y, x] = col


def put_ts(canvas: Canvas, t: int, s: int, c: Color) -> None:
    p = xy(t, s, canvas.w)
    if p is not None:
        canvas.put(p[0], p[1], c)


def mirror_x(c: Canvas) -> Canvas:
    out = Canvas(c.w, c.h)
    out.a = c.a[:, ::-1].copy()
    return out


def flatten(layers: Sequence[Canvas]) -> Canvas:
    out = Canvas(layers[0].w, layers[0].h)
    for l in layers:
        out.paste(l)
    return out


# --------------------------------------------------------------------------- upscale helpers for previews
def upscale(img: Image.Image, k: int) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.NEAREST)
