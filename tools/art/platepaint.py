"""Clean pixel-art painter for armor cubes: every face is a beveled "plate" (5-level ramp: outline, shadow,
base, light, highlight) with an optional pixel pattern (scales, fur, rock, plates, cloth, crystal...).

Styles are registered with S(name, ramp, pattern, ...). A cube's `mat` (and `faces` overrides) name a style.
Decals understood: {'type': 'tip', 'axis': 'up|down|front|back|out', 'frac': 0.4, 'mat': style}.
"""
import math

import numpy as np
from PIL import Image

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
    lev = lev + bevel_levels(style, w, h, face, mask)
    return np.clip(np.round(lev), 0, 4).astype(int)


def bevel_levels(style, w, h, face, mask):
    """Level offsets of the plate bevel (lit top edge, dark bottom edge, half-strength sides; a dark rim on
    top / bottom faces) along the mask outline, at 1x."""
    lev = np.zeros((h, w), float)
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
    return lev


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


def paint_cube(img, glow, cube, seed=0, hd=None):
    """Paints all faces of a decoration cube from its style(s) and tip decals.  hd = HDContext or None:
    the same plates are also painted in high detail into the HD buffers."""
    for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
        g = P.face_grid(cube, face)
        if g is None:
            continue
        u0, v0, w, h, I, J, pts, n, f, d, r = g
        st = STYLES.get(cube.faces.get(face, cube.mat)) or STYLES['default']
        sd = (cube.seed + hash(face)) & 0xFFFF
        paint_region(img, glow, u0, v0, w, h, st, face, sd)
        if hd is not None:
            hd.plate(u0, v0, w, h, st, face, sd)
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
                if hd is not None:
                    hd.plate(u0, v0, w, h, ts, face, sd + 3, mask=sel)


# ----------------------------------------------------------------------------- HD painting
# A style is painted in high detail with the hcmodel HD patterns: continuous level field (base + face light +
# HD pattern + relief lighting + soft bevel along the plate outline), looked up continuously along the 5-colour
# ramp, anti-aliased rounded plate edges, and LabPBR height / ao / smoothness per style.  The mean level of
# every plate is calibrated to its 1x pixel-art level map so the colour identity (box-filtered) is kept.
_HD_PATTERN = {'vplates': 'plates', 'stripes': 'smooth'}
_HD_PBR = {'smooth': dict(smooth=120, f0=14, por=60), 'metal': dict(smooth=210, f0=230, por=0), 'cloth': dict(smooth=40, f0=6, por=100)}


class HDContext:
    """HD buffers of one armor texture (TH*K x TW*K): colour, glow and LabPBR channels."""

    def __init__(self, tw, th, K):
        self.K = K
        self.img = np.zeros((th * K, tw * K, 4), float)
        self.glow = np.zeros((th * K, tw * K, 4), float)
        self.pbr = {'height': np.full((th * K, tw * K), 0.5), 'ao': np.zeros((th * K, tw * K)), 'smooth': np.zeros((th * K, tw * K)),
                    'f0': np.zeros((th * K, tw * K)), 'por': np.zeros((th * K, tw * K)), 'face': np.zeros((th * K, tw * K), np.int32)}
        self._face_id = 0
        self._mats = {}

    def material(self, style):
        m = self._mats.get(style.name)
        if m is None:
            pat = _HD_PATTERN.get(style.pattern, style.pattern)
            if pat not in P._PATTERNS_HD:
                pat = 'smooth'
            if pat == 'smooth' and style.speckle >= 0.05:
                pat = 'skin'                                   # speckled plain styles are leather / hide
            ramp = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in style.ramp]
            m = P.Material('armor_' + style.name, ramp, pat, scale=style.scale, pbr=_HD_PBR.get(pat, {}))
            self._mats[style.name] = m
        return m

    def plate(self, u0, v0, w, h, style, face, seed, mask=None, offset=(0, 0)):
        from hcmodel import hdup
        if w <= 0 or h <= 0:
            return
        K = self.K
        if mask is None:
            mask = np.ones((h, w), bool)
        if not mask.any():
            return
        mat = self.material(style)
        P._CUR_K, P._CUR_DETAIL = K, 1.0
        I, J = np.meshgrid(np.arange(w * K), np.arange(h * K))
        X, Y = (I + 0.5) / K + offset[0], (J + 0.5) / K + offset[1]
        if style.pattern == 'vplates':
            pt = P.pattern_hd(mat, Y, X, h, w, seed)            # plate rows run vertically
        else:
            pt = P.pattern_hd(mat, X, Y, w, h, seed, face)
        lev = np.full((h * K, w * K), float(style.base))
        if face == 'top':
            lev += 0.45
        elif face == 'bottom':
            lev -= 0.6
        lev += pt['delta'] * 3.2
        if style.pattern == 'stripes':
            lev -= P.smoothstep(0.3, 0.7, 0.5 + 0.5 * np.sin((X + Y) * 2 * math.pi / 4.0 + 1.2)) * 0.9
        # relief lighting from the pattern height field (same light as the monster painter)
        hgt = pt['height'] * mat.hd('relief') * K * 0.8
        gx = np.zeros_like(hgt)
        gy = np.zeros_like(hgt)
        gx[:, 1:-1] = (hgt[:, 2:] - hgt[:, :-2]) * 0.5
        gy[1:-1, :] = (hgt[2:, :] - hgt[:-2, :]) * 0.5
        nx, ny, nz = -gx, -gy, np.ones_like(gx)
        nl = np.sqrt(nx * nx + ny * ny + nz * nz)
        nx, ny, nz = nx / nl, ny / nl, nz / nl
        diff = nx * P._L[0] + ny * P._L[1] + nz * P._L[2]
        lev += (diff - P._L[2]) * 2.2
        gloss = mat.hd('gloss')
        if gloss > 0:
            ndh = np.clip(nx * P._HV[0] + ny * P._HV[1] + nz * P._HV[2], 0, 1)
            lev += ndh ** mat.hd('shine') * 0.9 * gloss
        # soft bevel along the plate outline (the 1x bevel offsets, blurred over ~0.7 px)
        bev = bevel_levels(style, w, h, face, mask)
        bev_hd = np.kron(bev, np.ones((K, K)))
        try:
            from scipy import ndimage
            bev_hd = ndimage.gaussian_filter(bev_hd, 0.35 * K)
        except Exception:
            pass
        lev += bev_hd * 1.1
        # tone calibration against the 1x level map
        lev1 = level_map(style, w, h, face, seed, mask, offset)
        m1 = float(lev1[mask].mean())
        cov = hdup.coverage(mask, K, radius=0.16, aa=0.14)
        sel = cov > 0.5
        if sel.any():
            lev += float(np.clip(m1 - lev[sel].mean(), -1.5, 1.5))
        rgb = P.ramp_lookup(list(style.ramp), lev / 4.0)
        if pt.get('tint') is not None:
            rgb = rgb * pt['tint']
        rgb = np.clip(rgb, 0, 255)
        reg = self.img[v0 * K:(v0 + h) * K, u0 * K:(u0 + w) * K]
        c = cov[..., None]
        reg[..., :3] = reg[..., :3] * (1 - c) + rgb * c
        reg[..., 3] = np.maximum(reg[..., 3], cov * 255)
        greg = self.glow[v0 * K:(v0 + h) * K, u0 * K:(u0 + w) * K]
        if style.glow:
            gm = cov
            if style.glow_levels is not None:
                lo, hi = min(style.glow_levels), max(style.glow_levels)
                gm = cov * P.smoothstep(lo - 0.6, lo - 0.1, lev) * (1 - P.smoothstep(hi + 0.1, hi + 0.6, lev))
            greg[..., :3] = greg[..., :3] * (1 - gm[..., None]) + rgb * gm[..., None]
            greg[..., 3] = np.maximum(greg[..., 3], gm * 255)
        else:
            greg[..., :3] *= (1 - c)
            greg[..., 3] *= (1 - cov)
        # PBR channels
        self._face_id += 1
        pb = self.pbr
        rows, cols = slice(v0 * K, (v0 + h) * K), slice(u0 * K, (u0 + w) * K)
        ao = np.clip((pt['ao'] if pt.get('ao') is not None else 0) + np.clip(-bev_hd, 0, 1) * 0.4, 0, 1)
        hgt01 = np.clip(pt['height'] * 0.8 + 0.1 + bev_hd * 0.08, 0, 1)
        for key, val in (('height', hgt01), ('ao', ao), ('smooth', float(mat.pbr_byte('smooth')) + (40.0 if style.glow else 0.0)),
                         ('f0', float(mat.pbr_byte('f0'))), ('por', float(mat.pbr_byte('por')))):
            pb[key][rows, cols] = np.where(sel, val, pb[key][rows, cols])
        pb['face'][rows, cols] = np.where(sel, self._face_id, pb['face'][rows, cols])

    def images(self):
        """(colour, glow or None, (normal, specular)) PIL images; colour slightly blurred for anti-aliasing."""
        img, glow, K = self.img, self.glow, self.K
        P._finish_hd(img, glow, K)
        img[..., :3] = np.round(img[..., :3] / 2) * 2
        has_glow = (glow[..., 3] > 0).any()
        out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
        gl = None
        if has_glow:
            gg = glow.copy()
            gg[gg[..., 3] == 0] = 0
            gl = Image.fromarray(np.clip(gg, 0, 255).astype(np.uint8), 'RGBA')
        return out, gl, P._pbr_images(self.pbr, img, glow, K)


S('default', ['#202020', '#505050', '#808080', '#a0a0a0', '#c8c8c8'])
