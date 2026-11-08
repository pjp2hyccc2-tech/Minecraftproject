"""Helpers shared by the 32x32 diagonal weapon sprites.

Axis coordinates (n = 32): t = x - y runs from the bottom-left corner (-31) to the
top-right corner (+31); s = x + y - 31 runs across the weapon, negative = upper-left
(lit) side. A pixel exists only where t + s is odd. Every sprite is built from layers
(back to front); each layer gets its own dark outline, so parts in front cut a clean
dark line into the parts behind them.
"""
from __future__ import annotations

import math
from typing import Callable

import numpy as np

from px import Canvas, Ramp, axis_mask, flatten, mix, xy, grow, LIGHT

N = 32


class Sprite:
    def __init__(self, n: int = N):
        self.n = n
        self.layers: list[Canvas] = []

    def layer(self) -> Canvas:
        c = Canvas(self.n)
        self.layers.append(c)
        return c

    def done(self) -> Canvas:
        return flatten(self.layers)


def am(fn: Callable[[int, int], bool], n: int = N) -> np.ndarray:
    """Mask from an axis predicate fn(t, s)."""
    return axis_mask(n, fn)


def pos(t: float, s: float, n: int = N) -> tuple[float, float]:
    """Continuous pixel position of axis point (t, s)."""
    return (t + s + n - 1) / 2.0, (s - t + n - 1) / 2.0


def axis_of(x: int, y: int, n: int = N) -> tuple[int, int]:
    return x - y, x + y - (n - 1)


def disc(cx: float, cy: float, r: float, n: int = N) -> np.ndarray:
    m = np.zeros((n, n), bool)
    for y in range(n):
        for x in range(n):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.01:
                m[y, x] = True
    return m


def disc_ts(t: float, s: float, r: float, n: int = N) -> np.ndarray:
    x, y = pos(t, s, n)
    return disc(x, y, r, n)


def neighbours_out(m: np.ndarray, x: int, y: int) -> tuple[int, int]:
    """(# of up/left neighbours outside m, # of down/right neighbours outside m)."""
    h, w = m.shape

    def out(xx, yy):
        return not (0 <= xx < w and 0 <= yy < h) or not m[yy, xx]

    ul = int(out(x - 1, y)) + int(out(x, y - 1))
    dr = int(out(x + 1, y)) + int(out(x, y + 1))
    return ul, dr


def shade_blade(c: Canvas, m: np.ndarray, ramp: Ramp, center: Callable[[int], float] = lambda t: -0.5,
                flat_lit: int = 4, flat_dark: int = 3, edge_hi: int = 5, edge_dark: int = 2,
                extra: Callable[[int, int, int], int | None] | None = None) -> None:
    """Classic diagonal blade shading: bright upper-left edge, lit half, shaded half,
    dark lower-right edge. ``extra(t, s, tone)`` may override the ramp index."""
    n = c.w
    ys, xs = np.nonzero(m)
    for x, y in zip(xs.tolist(), ys.tolist()):
        t, s = x - y, x + y - (n - 1)
        ul, dr = neighbours_out(m, x, y)
        rel = s - center(t)
        if ul == 2 and dr < 2:
            tone = edge_hi
        elif dr == 2 and ul < 2:
            tone = edge_dark
        elif ul == 1 and dr == 0:
            tone = flat_lit if rel < 0 else flat_lit
        elif dr == 1 and ul == 0:
            tone = flat_dark if rel > 0 else flat_lit
        else:
            tone = flat_lit if rel < 0 else flat_dark
        if extra is not None:
            o = extra(t, s, tone)
            if o is not None:
                if isinstance(o, tuple):
                    c.put(x, y, o)
                    continue
                tone = o
        c.put(x, y, ramp[tone])


def shade_bar(c: Canvas, m: np.ndarray, ramp: Ramp, base: int = 3, grad: float = 1.0,
              extra: Callable[[int, int, int], int | tuple | None] | None = None) -> None:
    """Shading for guards / pommels / caps: light from the upper left via the mask
    boundary plus a gentle gradient across s (upper-left end brighter)."""
    n = c.w
    ys, xs = np.nonzero(m)
    if len(xs) == 0:
        return
    ss = xs + ys - (n - 1)
    smin, smax = ss.min(), ss.max()
    span = max(1, smax - smin)
    for x, y in zip(xs.tolist(), ys.tolist()):
        t, s = x - y, x + y - (n - 1)
        ul, dr = neighbours_out(m, x, y)
        tone = base + (ul - dr) * 0.75
        tone += grad * (0.5 - (s - smin) / span)
        tone = int(math.floor(tone + 0.5))
        tone = max(1, min(len(ramp) - 1, tone))
        if extra is not None:
            o = extra(t, s, tone)
            if o is not None:
                if isinstance(o, tuple):
                    c.put(x, y, o)
                    continue
                tone = o
        c.put(x, y, ramp[tone])


def shade_ball(c: Canvas, m: np.ndarray, ramp: Ramp, lo: int = 1, spec: bool = True) -> None:
    ys, xs = np.nonzero(m)
    if len(xs) == 0:
        return
    cx, cy = xs.mean() + 0.5, ys.mean() + 0.5
    r = max(1.0, math.sqrt(len(xs) / math.pi))
    top = len(ramp) - 1
    best, bestv = None, -9
    for x, y in zip(xs.tolist(), ys.tolist()):
        dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
        d2 = min(1.0, dx * dx + dy * dy)
        dz = math.sqrt(1 - d2)
        lam = -(dx * -LIGHT[0] + dy * -LIGHT[1]) + dz * 0.6
        lam = max(0.0, min(1.0, (lam + 0.45) / 1.45))
        idx = lo + lam * (top - lo - (1 if spec else 0))
        c.put(x, y, ramp[int(round(idx))])
        if lam > bestv:
            bestv, best = lam, (x, y)
    if spec and best is not None:
        c.put(best[0], best[1], ramp[top])


def shade_shaft(c: Canvas, m: np.ndarray, ramp: Ramp, lit: int = 4, dark: int = 2, mid: int = 3,
                band: Callable[[int, int], int | tuple | None] | None = None) -> None:
    """Shading for 1-3 line wide diagonal shafts / grips."""
    n = c.w
    ys, xs = np.nonzero(m)
    for x, y in zip(xs.tolist(), ys.tolist()):
        t, s = x - y, x + y - (n - 1)
        ul, dr = neighbours_out(m, x, y)
        if ul > dr:
            tone = lit
        elif dr > ul:
            tone = dark
        else:
            tone = mid
        if band is not None:
            o = band(t, s)
            if o is not None:
                if isinstance(o, tuple):
                    c.put(x, y, o)
                    continue
                tone = tone + o
        c.put(x, y, ramp[tone])


def wrap_band(period: int = 3, offset: int = 0, depth: int = -1):
    """Diagonal grip wrapping: every ``period`` steps along t, darken by ``depth``."""
    def f(t, s):
        k = (t + s + offset) % (period * 2)
        return depth if k < 2 else None
    return f


def gem(c: Canvas, cx: float, cy: float, ramp: Ramp, r: float = 1.2) -> np.ndarray:
    m = disc(cx, cy, r, c.w)
    shade_ball(c, m, ramp, lo=1)
    return m


def sparkle(c: Canvas, x: int, y: int, col, arm: int = 1, core=None) -> None:
    c.put(x, y, core or col)
    for k in range(1, arm + 1):
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            c.put(x + dx, y + dy, col)


def outline(c: Canvas, ramp_or_color) -> Canvas:
    col = ramp_or_color.out if isinstance(ramp_or_color, Ramp) else ramp_or_color
    return c.outline(col)


# --------------------------------------------------------------------------- polygons in axis space
def point_in_poly(px: float, py: float, pts) -> bool:
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > py) != (yj > py):
            xc = xi + (py - yi) * (xj - xi) / (yj - yi)
            if px < xc:
                inside = not inside
        j = i
    return inside


def poly_ts(pts, n: int = N) -> np.ndarray:
    """Mask of pixels whose axis coords (t, s) fall inside polygon ``pts`` = [(t, s), ...].

    Think of the weapon standing upright: t = up (towards the tip), s = right."""
    return am(lambda t, s: point_in_poly(t + 0.013, s + 0.007, pts), n)


def sweep_ts(path, widths, n: int = N) -> np.ndarray:
    """Tapered stroke along ``path`` [(t, s), ...] with half-widths ``widths`` (same length)."""
    left, right = [], []
    for i, (t, s) in enumerate(path):
        a = path[max(0, i - 1)]
        b = path[min(len(path) - 1, i + 1)]
        dt, ds = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dt, ds) or 1.0
        nt, ns = -ds / L, dt / L
        w = widths[i]
        left.append((t + nt * w, s + ns * w))
        right.append((t - nt * w, s - ns * w))
    return poly_ts(left + right[::-1], n)


def curve(p0, p1, p2, steps: int = 12):
    """Quadratic Bezier points from p0 to p2 with control p1."""
    out = []
    for i in range(steps + 1):
        u = i / steps
        a = (1 - u) ** 2
        b = 2 * (1 - u) * u
        c = u * u
        out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return out


def mirror_s(m: np.ndarray, n: int = N) -> np.ndarray:
    """Mirror a mask across the weapon axis (s -> -s, i.e. swap x/y about the anti-diagonal)."""
    out = np.zeros_like(m)
    ys, xs = np.nonzero(m)
    for x, y in zip(xs.tolist(), ys.tolist()):
        # (t, s) -> (t, -s): x' = (t - s + n - 1)/2, y' = (-s - t + n - 1)/2
        t, s = x - y, x + y - (n - 1)
        xx, yy = (t - s + n - 1) // 2, (-s - t + n - 1) // 2
        if 0 <= xx < n and 0 <= yy < n:
            out[yy, xx] = True
    return out
