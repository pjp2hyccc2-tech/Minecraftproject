"""ASCII-art icon renderer with automatic lighting and outline (for 16x16 / 18x18 icons).

Each icon is a list of equal-length strings. '.' (or space) is transparent; every
other character names a material in ``mats``:

* ``Ramp``                -> lit with the shared silhouette lighting
* ``("flat", color)``     -> fixed colour
* ``("gem", Ramp)``       -> each connected blob shaded as a small ball
* ``("glow", Ramp)``      -> bright, only lightly shaded (runes, lights)
* ``("tone", Ramp, k)``   -> lit, but shifted k ramp steps (e.g. -2 = engraved)

Lower-case letters default to their upper-case material shifted two steps darker
(engraving lines, rivets, seams) unless they are defined explicitly.
"""
from __future__ import annotations

import math

import numpy as np

from px import Canvas, Ramp, LIGHT, dark_of, facing, luminance


def _components(m: np.ndarray):
    h, w = m.shape
    seen = np.zeros_like(m)
    comps = []
    for y in range(h):
        for x in range(w):
            if m[y, x] and not seen[y, x]:
                stack = [(x, y)]
                seen[y, x] = True
                pts = []
                while stack:
                    cx, cy = stack.pop()
                    pts.append((cx, cy))
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and m[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((nx, ny))
                comps.append(pts)
    return comps


def render(rows, mats: dict, outline=None, grad: float = 0.9, edge: float = 1.4, base: float = 3.0,
           local: str = "", local_k: float = 0.9, shape_mask: np.ndarray | None = None,
           size: int | None = None, offset=(0, 0)) -> Canvas:
    h = len(rows)
    w = max(len(r) for r in rows)
    W = size or w
    H = size or h
    ox, oy = offset
    c = Canvas(W, H)
    grid = [[(r[x] if x < len(r) else ".") for x in range(w)] for r in rows]
    sil = np.zeros((H, W), bool)
    for y in range(h):
        for x in range(w):
            if grid[y][x] not in ". ":
                sil[y + oy, x + ox] = True
    lightm = shape_mask if shape_mask is not None else sil
    ys, xs = np.nonzero(sil)
    if len(xs) == 0:
        return c
    cx, cy = xs.mean(), ys.mean()
    span = max(2.0, max(xs.max() - xs.min(), ys.max() - ys.min()) / 2.0)

    # per-letter masks for local bevel
    letters = {}
    for y in range(h):
        for x in range(w):
            ch = grid[y][x]
            if ch not in ". ":
                letters.setdefault(ch, np.zeros((H, W), bool))[y + oy, x + ox] = True

    def spec_of(ch):
        if ch in mats:
            return mats[ch], 0
        if ch.islower() and ch.upper() in mats:
            return mats[ch.upper()], -2
        raise KeyError(f"no material for {ch!r}")

    gem_done = set()
    for y in range(h):
        for x in range(w):
            ch = grid[y][x]
            if ch in ". ":
                continue
            X, Y = x + ox, y + oy
            spec, shift_k = spec_of(ch)
            tone = base + facing(lightm, X, Y, 1) * edge
            tone += ((X - cx) * LIGHT[0] + (Y - cy) * LIGHT[1]) / span * grad
            if ch in local:
                tone += facing(letters[ch], X, Y, 1) * local_k
            if isinstance(spec, Ramp):
                k = int(math.floor(tone + 0.5)) + shift_k
                c.put(X, Y, spec[max(1, min(len(spec) - 1, k))])
            elif spec[0] == "flat":
                c.put(X, Y, spec[1])
            elif spec[0] == "tone":
                k = int(math.floor(tone + 0.5)) + spec[2] + shift_k
                c.put(X, Y, spec[1][max(1, min(len(spec[1]) - 1, k))])
            elif spec[0] == "glow":
                r = spec[1]
                k = 4 if tone < 3.4 else 5
                c.put(X, Y, r[k + shift_k // 2])
            elif spec[0] == "gem":
                if ch in gem_done:
                    continue
                gem_done.add(ch)
                for comp in _components(letters[ch]):
                    shade_blob(c, comp, spec[1])
    if outline is not False:
        if outline is None:
            c.outline(None, auto=1.0)
        else:
            c.outline(outline)
    return c


def shade_blob(c: Canvas, pts, ramp: Ramp, spec: bool = True) -> None:
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = sum(xs) / len(xs) + 0.5, sum(ys) / len(ys) + 0.5
    r = max(0.8, math.sqrt(len(pts) / math.pi))
    top = len(ramp) - 1
    best, bv = None, -9
    for x, y in pts:
        dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
        d2 = min(1.0, dx * dx + dy * dy)
        lam = (dx * LIGHT[0] + dy * LIGHT[1]) + math.sqrt(1 - d2) * 0.6
        lam = max(0.0, min(1.0, (lam + 0.45) / 1.45))
        idx = 2 + lam * (top - 3)
        c.put(x, y, ramp[int(round(idx))])
        if lam > bv:
            bv, best = lam, (x, y)
    if spec and best is not None and len(pts) >= 2:
        c.put(best[0], best[1], ramp[top])


def overlay(base: Canvas, rows, mats: dict, ox: int = 0, oy: int = 0) -> Canvas:
    """Draw flat-coloured ASCII details on top of a rendered icon (no lighting)."""
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in mats and mats[ch] is not None:
                base.put(ox + x, oy + y, mats[ch])
    return base
