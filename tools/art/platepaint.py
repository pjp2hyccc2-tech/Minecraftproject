"""Clean pixel-art painter for armor cubes: every face is a beveled "plate" (5-level ramp: outline, shadow,
base, light, highlight) with an optional pixel pattern (scales, fur, rock, plates, cloth, crystal...).

Styles are registered with S(name, ramp, pattern, ...). A cube's `mat` (and `faces` overrides) name a style.
Decals understood: {'type': 'tip', 'axis': 'up|down|front|back|out', 'frac': 0.4, 'mat': style}.
"""
import math

import numpy as np

from hcmodel import paint as P

STYLES = {}


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


class Style:
    def __init__(self, name, ramp, pattern='smooth', glow=False, speckle=0.06, bevel=True, base=2, scale=1.0, glow_levels=None):
        assert len(ramp) == 5, name
        self.name = name
        self.ramp = np.stack([hexrgb(c) for c in ramp])
        self.pattern = pattern
        self.glow = glow
        self.speckle = speckle
        self.bevel = bevel
        self.base = base
        self.scale = scale
        self.glow_levels = glow_levels  # e.g. (3, 4): only these levels glow


def S(name, ramp, pattern='smooth', **kw):
    STYLES[name] = Style(name, ramp, pattern, **kw)
    return STYLES[name]


def h2(i, j, seed):
    return P.hash2(np.asarray(i, dtype=np.int64), np.asarray(j, dtype=np.int64), seed)


# ----------------------------------------------------------------------------- level maps
def level_map(style, w, h, face, seed, mask=None, offset=(0, 0)):
    """Integer level (0..4) per pixel of a w x h face region."""
    I, J = np.meshgrid(np.arange(w), np.arange(h))
    I = I + offset[0]
    J = J + offset[1]
    if mask is None:
        mask = np.ones((h, w), bool)
    lev = np.full((h, w), float(style.base))
    pat = style.pattern
    s = style.scale
    if face == 'top':
        lev += 0.45
    elif face == 'bottom':
        lev -= 0.6
    if pat == 'scales':
        sw, sh = max(3, int(round(4 * s))), max(2, int(round(3 * s)))
        row = J // sh
        off = (row % 2) * (sw // 2)
        lx = (I + off) % sw
        ly = J % sh
        lev = np.where(ly == sh - 1, lev - 1, lev)
        lev = np.where((lx == 0) & (ly > 0), lev - 1, lev)
        lev = np.where((ly == 0) & (lx >= 1) & (lx <= sw - 2), lev + 0.8, lev)
    elif pat == 'plates':
        ph = max(2, int(round(3 * s)))
        ly = J % ph
        lev = np.where(ly == ph - 1, lev - 1, lev)
        lev = np.where(ly == 0, lev + 0.7, lev)
    elif pat == 'vplates':
        pw = max(2, int(round(3 * s)))
        lx = I % pw
        lev = np.where(lx == pw - 1, lev - 1, lev)
        lev = np.where(lx == 0, lev + 0.6, lev)
    elif pat == 'fur':
        col = h2(I // 1, I * 0 + 5, seed)
        lev = np.where(col < 0.3, lev - 0.9, lev)
        lev = np.where(col > 0.78, lev + 0.8, lev)
        tip = ((J + (h2(I, I * 0 + 9, seed) * 4).astype(int)) % 4 == 3)
        lev = np.where(tip, lev - 0.6, lev)
    elif pat == 'rock':
        cell = 3.6 * s
        gx, gy = I / cell, J / cell
        ix, iy = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
        best = np.full(I.shape, 9.0)
        second = np.full(I.shape, 9.0)
        by = np.zeros(I.shape)
        bv = np.zeros(I.shape)
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                cx, cy = ix + ox, iy + oy
                px = cx + 0.2 + 0.6 * h2(cx, cy, seed)
                py = cy + 0.2 + 0.6 * h2(cx, cy, seed + 7)
                dist = np.sqrt((gx - px) ** 2 + (gy - py) ** 2)
                upd = dist < best
                second = np.where(upd, best, np.minimum(second, dist))
                by = np.where(upd, gy - py, by)
                bv = np.where(upd, h2(cx, cy, seed + 13), bv)
                best = np.where(upd, dist, best)
        crack = (second - best) < 0.16
        lev = lev + (bv - 0.5) * 0.9 - np.clip(by, -0.6, 0.6) * 1.1
        lev = np.where(crack, 0.6, lev)
    elif pat == 'cloth':
        lev = np.where(((I + 2 * J) % 4) == 0, lev - 0.6, lev)
        lev = np.where(((I + 2 * J) % 4) == 2, lev + 0.3, lev)
    elif pat == 'metal':
        hl = np.abs(J - (h - 1) * 0.3) < 0.6
        lev = np.where(hl, lev + 1.0, lev)
        lev = lev - (J / max(1, h - 1)) * 0.7
    elif pat == 'crystal':
        lev = np.where(((I + J) % 4) == 0, lev + 1.0, lev)
        lev = np.where(((I - J) % 5) == 0, lev - 0.8, lev)
    elif pat == 'membrane':
        v1 = ((I * 2 + J) % 7) == 0
        lev = np.where(v1, lev - 1.0, lev + 0.2)
    elif pat == 'horn':
        along = J if h >= w else I
        lev = np.where(along % 3 == 2, lev - 0.7, lev)
    elif pat == 'bone':
        pits = h2(I, J, seed + 5) > 0.9
        lev = np.where(pits, lev - 0.8, lev)
    elif pat == 'stripes':
        lev = np.where((I + J) % 4 == 0, lev - 1.0, lev)
    if style.speckle > 0:
        r = h2(I, J, seed + 101)
        lev = np.where(r < style.speckle, lev - 0.7, lev)
        lev = np.where(r > 1 - style.speckle * 0.7, lev + 0.6, lev)
    if style.bevel and w >= 3 and h >= 3 and face not in ('top', 'bottom'):
        up = np.zeros_like(mask)
        up[1:, :] = mask[:-1, :]
        dn = np.zeros_like(mask)
        dn[:-1, :] = mask[1:, :]
        lf = np.zeros_like(mask)
        lf[:, 1:] = mask[:, :-1]
        rt = np.zeros_like(mask)
        rt[:, :-1] = mask[:, 1:]
        lev = np.where(mask & ~up, lev + 1.0, lev)
        lev = np.where(mask & ~dn, lev - 1.0, lev)
        lev = np.where(mask & ~lf & up & dn, lev + 0.5, lev)
        lev = np.where(mask & ~rt & up & dn, lev - 0.5, lev)
    elif style.bevel and w >= 3 and h >= 3:
        edge = np.zeros_like(mask)
        edge[0, :] = edge[-1, :] = True
        edge[:, 0] = edge[:, -1] = True
        lev = np.where(mask & edge, lev - 0.6, lev)
    return np.clip(np.round(lev), 0, 4).astype(int)


def paint_region(img, glow, u0, v0, w, h, style, face, seed, mask=None, offset=(0, 0)):
    if w <= 0 or h <= 0:
        return
    if mask is None:
        mask = np.ones((h, w), bool)
    lev = level_map(style, w, h, face, seed, mask, offset)
    rgb = style.ramp[lev]
    reg = img[v0:v0 + h, u0:u0 + w]
    reg[mask, :3] = rgb[mask]
    reg[mask, 3] = 255
    if glow is not None:
        greg = glow[v0:v0 + h, u0:u0 + w]
        if style.glow:
            gm = mask.copy()
            if style.glow_levels is not None:
                gm &= np.isin(lev, style.glow_levels)
            greg[gm, :3] = rgb[gm]
            greg[gm, 3] = 255
        else:
            greg[mask] = 0


def paint_cube(img, glow, cube, seed=0):
    """Paints all faces of a decoration cube from its style(s) and tip decals."""
    for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
        g = P.face_grid(cube, face)
        if g is None:
            continue
        u0, v0, w, h, I, J, pts, n, f, d, r = g
        st = STYLES.get(cube.faces.get(face, cube.mat)) or STYLES['default']
        sd = (cube.seed + hash(face)) & 0xFFFF
        paint_region(img, glow, u0, v0, w, h, st, face, sd)
        for dec in cube.decals:
            if dec.get('type') != 'tip':
                continue
            ts = STYLES.get(dec.get('mat', 'ivory'))
            if ts is None:
                continue
            frac = dec.get('frac', 0.4)
            axis = dec.get('axis', 'up')
            coord = {'up': 1 - d, 'down': d, 'front': f, 'back': 1 - f, 'right': r, 'left': 1 - r}[axis]
            sel = coord > 1 - frac - 1e-6
            if face in ('top', 'bottom') and axis in ('up', 'down'):
                sel = np.full_like(sel, axis == 'up' and face == 'top' or axis == 'down' and face == 'bottom')
            if sel.any():
                paint_region(img, glow, u0, v0, w, h, ts, face, sd + 3, mask=sel)


S('default', ['#202020', '#505050', '#808080', '#a0a0a0', '#c8c8c8'])
