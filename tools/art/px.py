"""Pixel-art helpers shared by the HITORI texture modules (s_*.py)."""
import math

import numpy as np

from common import (Tex, C, ramp, pal, fnoise, vnoise, white, grid, rng, bresenham, equalize, norm01,
                    bayer, dilate, erode, edge, wrapdist, lighten_arr, smoothstep)


def paint_idx(t, idx, palette, mask=None):
    palette = np.asarray(palette, np.float32)
    idx = np.clip(np.round(idx), 0, len(palette) - 1).astype(int)
    if mask is None:
        t.a[:] = palette[idx]
    else:
        t.a[mask] = palette[idx][mask]
    return t


def from_idx(idx, palette):
    t = Tex(idx.shape[1], idx.shape[0])
    return paint_idx(t, idx, palette)


def levels(field, n, dither=0.0):
    """Equalise a field and cut it into n integer levels (0..n-1)."""
    f = equalize(field)
    h, w = f.shape
    return np.clip(np.floor(f * n + bayer(w, h) * dither), 0, n - 1)


def despeckle(idx, wrap=True, passes=1, keep=None):
    """Replace pixels that differ from all 4 neighbours with the neighbours' median (kills salt & pepper)."""
    idx = idx.copy()
    for _ in range(passes):
        if wrap:
            nb = np.stack([np.roll(idx, 1, 0), np.roll(idx, -1, 0), np.roll(idx, 1, 1), np.roll(idx, -1, 1)])
        else:
            p = np.pad(idx, 1, mode='edge')
            nb = np.stack([p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]])
        lonely = (nb != idx[None]).all(0)
        if keep is not None:
            lonely &= ~keep
        med = np.median(nb, 0)
        idx[lonely] = np.round(med[lonely])
    return idx


def emboss(height, light=(-1.0, -1.0), wrap=True):
    """Directional slope (lit from top-left by default) of a height field: >0 facing the light."""
    lx, ly = light
    if wrap:
        gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5
        gy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5
    else:
        gy, gx = np.gradient(height)
    n = math.hypot(lx, ly)
    return -(gx * lx + gy * ly) / n


def voronoi(w, h, pts, wrap=True, metric='euclid', aniso=(1.0, 1.0)):
    """Returns (cell id, d1, d2) for a set of feature points (x, y)."""
    x, y = grid(w, h)
    pts = np.asarray(pts, np.float64)
    ds = []
    for (px_, py_) in pts:
        if wrap:
            dx = wrapdist(x, px_, w)
            dy = wrapdist(y, py_, h)
        else:
            dx, dy = np.abs(x - px_), np.abs(y - py_)
        dx, dy = dx * aniso[0], dy * aniso[1]
        if metric == 'cheb':
            ds.append(np.maximum(dx, dy))
        elif metric == 'manhattan':
            ds.append(dx + dy)
        else:
            ds.append(np.hypot(dx, dy))
    ds = np.stack(ds)
    order = np.argsort(ds, 0)
    cid = order[0]
    d1 = np.take_along_axis(ds, order[:1], 0)[0]
    d2 = np.take_along_axis(ds, order[1:2], 0)[0] if len(pts) > 1 else d1 + 99
    return cid, d1, d2


def jitter_points(n, w, h, seed, min_d=2.5, tries=4000):
    """Poisson-ish wrapped point set."""
    r = rng(seed)
    pts = []
    for _ in range(tries):
        if len(pts) >= n:
            break
        p = (r.random() * w, r.random() * h)
        ok = True
        for q in pts:
            dx = min(abs(p[0] - q[0]), w - abs(p[0] - q[0]))
            dy = min(abs(p[1] - q[1]), h - abs(p[1] - q[1]))
            if dx * dx + dy * dy < min_d * min_d:
                ok = False
                break
        if ok:
            pts.append(p)
    return pts


def stamp(t, rows, ox, oy, legend, wrap=True, flipx=False):
    """Draw an ASCII sprite: rows of chars, legend char -> colour (None/'.' = skip)."""
    w = max(len(r) for r in rows)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            c = legend.get(ch)
            if c is None:
                continue
            xx = ox + (w - 1 - i if flipx else i)
            t.set(xx, oy + j, c, wrap)


def sprite(rows, legend, w=16, h=16):
    t = Tex(w, h)
    stamp(t, rows, 0, 0, legend, wrap=False)
    return t


def line_pts(x0, y0, x1, y1):
    return bresenham(x0, y0, x1, y1)


def curve_pts(pts, steps=None):
    """Catmull-Rom through control points -> integer pixel path (deduplicated, 8-connected)."""
    pts = np.asarray(pts, np.float64)
    if len(pts) < 3:
        return line_pts(*pts[0], *pts[-1])
    P = np.vstack([pts[0], pts, pts[-1]])
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        n = steps or int(max(2, np.hypot(*(p2 - p1)) * 3))
        for k in range(n):
            s = k / n
            q = 0.5 * ((2 * p1) + (-p0 + p2) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s
                       + (-p0 + 3 * p1 - 3 * p2 + p3) * s ** 3)
            out.append((int(round(q[0])), int(round(q[1]))))
    out.append((int(round(pts[-1][0])), int(round(pts[-1][1]))))
    ded = []
    for p in out:
        if not ded or ded[-1] != p:
            if ded and max(abs(p[0] - ded[-1][0]), abs(p[1] - ded[-1][1])) > 1:
                ded += line_pts(*ded[-1], *p)[1:]
            else:
                ded.append(p)
    return ded


def outline_alpha(t, color, diag=False):
    """Paint `color` on transparent pixels touching opaque ones (sprite outline)."""
    a = t.a[..., 3] > 0
    p = np.pad(a, 1)
    nb = p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:]
    if diag:
        nb |= p[:-2, :-2] | p[:-2, 2:] | p[2:, :-2] | p[2:, 2:]
    m = nb & ~a
    t.a[m] = C(color)
    return t


def shade_sprite(t, light=(-1, -1), amount=0.06):
    """Lighten pixels whose top-left neighbour is transparent, darken those whose bottom-right is."""
    a = t.a[..., 3] > 0
    p = np.pad(a, 1)
    lx, ly = light
    tl = ~p[1 + ly:1 + ly + t.h, 1 + lx:1 + lx + t.w]
    br = ~p[1 - ly:1 - ly + t.h, 1 - lx:1 - lx + t.w]
    out = t.a.copy()
    m1 = a & tl
    m2 = a & br
    if m1.any():
        out[m1] = lighten_arr(t.a[m1][None], amount)[0]
    if m2.any():
        out[m2] = lighten_arr(t.a[m2][None], -amount)[0]
    t.a[:] = out
    return t


def cutout(t, thr=128):
    t.a[..., 3] = np.where(t.a[..., 3] >= thr, 255, 0)
    return t


def overlay(base, top):
    """Alpha-composite Tex top over Tex base (same size), returns new Tex."""
    out = base.copy()
    a = top.a[..., 3:4] / 255.0
    out.a[..., :3] = out.a[..., :3] * (1 - a) + top.a[..., :3] * a
    out.a[..., 3] = np.maximum(out.a[..., 3], top.a[..., 3])
    return out


def tint(t, rgb, amount, mask=None):
    c = C(rgb)[:3]
    m = np.ones(t.a.shape[:2], bool) if mask is None else mask
    t.a[m, :3] = t.a[m, :3] * (1 - amount) + c * amount
    return t


def mul(t, f, mask=None):
    m = np.ones(t.a.shape[:2], bool) if mask is None else mask
    t.a[m, :3] *= np.asarray(f, np.float32)
    return t


def detile(t, keep=0.35, rcut=2.0, chroma=True):
    """Weaken the tile-scale (lowest frequency) light/dark and colour blotches of a tiling texture, keeping all
    the small detail: a 16 px tile with one big dark patch shows as a grid of patches on a long wall or slope.
    keep: fraction of the frequency components below rcut (cycles per tile) that survives."""
    a = t.a
    h, w = a.shape[:2]
    fy = np.fft.fftfreq(h)[:, None] * h
    fx = np.fft.fftfreq(w)[None, :] * w
    r = np.hypot(fx, fy)
    band = (r > 0) & (r < rcut)

    def low(f):
        return np.real(np.fft.ifft2(np.fft.fft2(f) * band))

    rgb = a[..., :3].astype(np.float64)
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722])
    lum_new = lum - (1.0 - keep) * low(lum)
    scale = lum_new / np.maximum(lum, 1.0)
    out = rgb * scale[..., None]
    if chroma:
        # the same for the colour cast (rusty / mossy / grey patches): pull each channel's low band towards luma's
        for c in range(3):
            dev = out[..., c] - lum_new
            out[..., c] = lum_new + dev - (1.0 - keep) * low(dev)
    a[..., :3] = np.clip(out, 0, 255).astype(np.float32)
    return t
