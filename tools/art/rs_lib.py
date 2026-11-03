"""REISATSU art library: palettes, material painters, pixel glyphs / text, the HIDAMARI sun logo.

Painters return float32 (h, w, 4) arrays (0..255). Everything is deterministic (explicit seeds).
"""
import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from common import (C, ramp, pal, fnoise, vnoise, rng, grid, lighten_arr, desaturate, font, smoothstep,
                    FONT_SANS_JP_BLACK, FONT_SANS_JP_BOLD, FONT_SANS_JP_MED, FONT_SANS_JP_REG, FONT_SERIF_JP_BLACK,
                    FONT_SERIF_JP_BOLD, FONT_SANS, FONT_SANS_BOLD, bayer)

FONT_IPAG = ('/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf', 0)

# ================================================================================================ palettes
# warm wood-look vinyl / real woods
WOOD_FLOOR = ramp(['#3b2414', '#5a3820', '#74492a', '#8a5a34', '#9d6b3f', '#ad7b4b', '#bb8a58'], 7)
WOOD_DARK = ramp(['#160d08', '#22150d', '#2e1c11', '#3b2516', '#4a2f1c', '#5a3a23', '#6b472b'], 7)
WOOD_TABLE = ramp(['#1c110a', '#2a190f', '#382214', '#472c1a', '#573721', '#684329'], 6)
WOOD_LIGHT = ramp(['#4a2f19', '#6a4424', '#87592f', '#a0703c', '#b5844a', '#c69659', '#d4a76a'], 7)
# brand
ORANGE = ramp(['#5c2208', '#8a350d', '#b44a14', '#d8621c', '#ee7a26', '#f69a3c', '#fbb860'], 7)
BROWN = ramp(['#1e0f08', '#33190d', '#4a2513', '#5f321a', '#744022', '#8a512d'], 6)
CREAM = ramp(['#8d8068', '#a99b80', '#c0b296', '#d3c6a8', '#e2d6b9', '#ece3c9', '#f4edd8'], 7)
SUNY = ramp(['#9a4a0a', '#c8670c', '#e98a14', '#f6a520', '#fcc03a', '#ffd767', '#ffe9a0'], 7)
# materials
VINYL_RED = ramp(['#1e0a07', '#33130d', '#4a1c14', '#5e261a', '#712f20', '#843a28', '#974834'], 7)
STEEL = ramp(['#2f3234', '#44484b', '#5a5f62', '#70767a', '#878d90', '#9ea4a7', '#b5babc', '#cbcfd0'], 8)
ALU = ramp(['#4c5054', '#666b70', '#80868b', '#9aa0a4', '#b3b8bb', '#c9cdcf', '#dcdfe0'], 7)
CHROME = ramp(['#1d2023', '#3a3f44', '#646b71', '#949ba0', '#c4c9cc', '#eef0f1'], 6)
GREYPAINT = ramp(['#2a2d2f', '#3b3f42', '#4d5255', '#5f6467', '#72777a', '#868b8d', '#9a9ea0'], 7)
WHITEPLASTIC = ramp(['#7b7a74', '#97968f', '#b0afa7', '#c6c4bb', '#d7d5cc', '#e5e3da', '#f0eee6'], 7)
BEIGE = ramp(['#6e6352', '#867a66', '#9d907a', '#b2a58d', '#c4b89f', '#d3c8af'], 6)
BLACKPLASTIC = ramp(['#0d0d0f', '#17171a', '#212125', '#2c2c31', '#38383e', '#46464d'], 6)
GRANITE = ramp(['#121213', '#1b1b1d', '#252527', '#303033', '#3c3c40', '#4a4a4f'], 6)
CONCRETE = ramp(['#4a4a47', '#5b5b57', '#6b6b66', '#7b7b75', '#8a8a83', '#999991', '#a8a8a0'], 7)
ASPHALT = ramp(['#141416', '#1b1b1e', '#222226', '#2a2a2e', '#333337', '#3d3d42', '#4a4a4f'], 7)
CARDBOARD = ramp(['#4e3a22', '#664c2d', '#7e5f39', '#957246', '#a98453', '#b99561'], 6)
PAPERW = ramp(['#8f8b80', '#a8a497', '#bdb9ab', '#cfcbbd', '#dddacb', '#e9e6d8', '#f3f1e6'], 7)
TERRACOTTA = ramp(['#3e1a0e', '#5c2714', '#7a351b', '#954323', '#ad532c', '#c26537', '#d27a47'], 7)
BRICK = ramp(['#2a1510', '#3d1d15', '#52271b', '#653222', '#783e2a', '#8a4b33', '#9a5a3f'], 7)
STUCCO = ramp(['#6f6553', '#857a66', '#9a8f79', '#ada28b', '#bdb29a', '#cbc1a8', '#d7cdb5'], 7)
GREEN_LEAF = ramp(['#07100a', '#0c1a10', '#122517', '#19311e', '#213d26', '#2b4a2f', '#375839'], 7)
RUBBER = ramp(['#0b0b0c', '#131314', '#1b1b1d', '#242427', '#2e2e32'], 5)
GLASS_GREEN = ramp(['#304a46', '#3f5c57', '#507069', '#63857d', '#7a9a92'], 5)
RED_LED = pal('#3a0606', '#6e0a08', '#c41810', '#ff3a22', '#ff8a6a', '#ffd0c0')
GREEN_SIGN = ramp(['#03250f', '#063a18', '#0a5222', '#10692d', '#1a8139', '#2b9a49'], 6)

# ================================================================================================ arrays


def blank(w, h, c=(0, 0, 0, 0)):
    a = np.zeros((h, w, 4), np.float32)
    a[:] = C(c)
    return a


def from_idx(idx, palette):
    palette = np.asarray(palette, np.float32)
    idx = np.clip(np.round(idx), 0, len(palette) - 1).astype(int)
    return palette[idx].astype(np.float32)


def noise(w, h, seed, beta=1.0, sx=1.0, sy=1.0, wrap=True):
    """Std-1 noise field of exactly (h, w). Non-wrapping sizes are cut from a larger periodic field."""
    if wrap:
        return fnoise(w, h, seed, beta=beta, sx=sx, sy=sy)
    W, H = max(16, int(2 ** math.ceil(math.log2(max(w, 2) * 2)))), max(16, int(2 ** math.ceil(math.log2(max(h, 2) * 2))))
    n = fnoise(W, H, seed, beta=beta, sx=sx, sy=sy)
    return n[:h, :w]


def dl(a, d, mask=None):
    """Lighten / darken (OKLab L) an array (in place) where mask."""
    if mask is None:
        a[...] = lighten_arr(a, d)
        return a
    if np.isscalar(d) or np.ndim(d) == 0:
        if mask.any():
            a[mask] = lighten_arr(a[mask][None], d)[0]
        return a
    # per-pixel amounts
    lab_in = a[mask]
    out = lab_in.copy()
    for i in range(len(out)):
        out[i] = lighten_arr(lab_in[i][None], float(d[mask][i]))[0]
    a[mask] = out
    return a


def dl_field(a, field):
    """Per-pixel OKLab lightness shift (field shaped (h, w))."""
    from common import to_lab, from_lab
    lab = to_lab(a[..., :3])
    lab[..., 0] += field
    a[..., :3] = from_lab(lab)
    return a


def tint(a, color, t, mask=None):
    c = C(color)[:3]
    scalar = np.isscalar(t) or np.ndim(t) == 0
    if mask is None:
        tt = t if scalar else np.asarray(t)[..., None]
        a[..., :3] = a[..., :3] * (1 - tt) + c * tt
    else:
        if not np.any(mask):
            return a
        tt = t if scalar else np.asarray(t)[mask][:, None]
        a[mask, :3] = a[mask, :3] * (1 - tt) + c * tt
    return a


def over(a, b, x=0, y=0, alpha=1.0):
    """Composite RGBA array b over a at (x, y) (clipped)."""
    h, w = b.shape[:2]
    H, W = a.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x0 >= x1 or y0 >= y1:
        return a
    sb = b[y0 - y:y1 - y, x0 - x:x1 - x]
    sa = a[y0:y1, x0:x1]
    al = sb[..., 3:4] / 255.0 * alpha
    da = sa[..., 3:4] / 255.0
    oa = al + da * (1 - al)
    rgb = (sb[..., :3] * al + sa[..., :3] * da * (1 - al)) / np.maximum(oa, 1e-6)
    sa[..., :3] = np.where(oa > 0, rgb, sa[..., :3])
    sa[..., 3:4] = oa * 255
    return a


def rect(a, x0, y0, x1, y1, c, alpha=None):
    """Inclusive rectangle fill (clipped). alpha: blend factor 0..1 (None = replace)."""
    H, W = a.shape[:2]
    x0, y0, x1, y1 = max(0, int(x0)), max(0, int(y0)), min(W - 1, int(x1)), min(H - 1, int(y1))
    if x0 > x1 or y0 > y1:
        return a
    c = C(c)
    if alpha is None:
        a[y0:y1 + 1, x0:x1 + 1] = c
    else:
        a[y0:y1 + 1, x0:x1 + 1, :3] = a[y0:y1 + 1, x0:x1 + 1, :3] * (1 - alpha) + c[:3] * alpha
        a[y0:y1 + 1, x0:x1 + 1, 3] = np.maximum(a[y0:y1 + 1, x0:x1 + 1, 3], c[3] * alpha)
    return a


def px(a, x, y, c, alpha=None):
    H, W = a.shape[:2]
    x, y = int(x), int(y)
    if 0 <= x < W and 0 <= y < H:
        c = C(c)
        if alpha is None:
            a[y, x] = c
        else:
            a[y, x, :3] = a[y, x, :3] * (1 - alpha) + c[:3] * alpha
            a[y, x, 3] = max(a[y, x, 3], c[3] * alpha)
    return a


def hline(a, x0, x1, y, c, alpha=None):
    return rect(a, min(x0, x1), y, max(x0, x1), y, c, alpha)


def vline(a, x, y0, y1, c, alpha=None):
    return rect(a, x, min(y0, y1), x, max(y0, y1), c, alpha)


def line(a, x0, y0, x1, y1, c, alpha=None):
    from common import bresenham
    for (x, y) in bresenham(x0, y0, x1, y1):
        px(a, x, y, c, alpha)
    return a


def frame(a, x0, y0, x1, y1, c, alpha=None):
    hline(a, x0, x1, y0, c, alpha)
    hline(a, x0, x1, y1, c, alpha)
    vline(a, x0, y0, y1, c, alpha)
    vline(a, x1, y0, y1, c, alpha)
    return a


def disc_mask(w, h, cx, cy, r, ry=None):
    x, y = grid(w, h)
    ry = r if ry is None else ry
    return ((x + 0.5 - cx) / r) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0


def ring_mask(w, h, cx, cy, r0, r1):
    x, y = grid(w, h)
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy)
    return (d >= r0) & (d <= r1)


def bevel(a, top=0.05, bottom=-0.06, left=0.02, right=-0.03, width=1):
    """Pixel-art edge lighting of a face region (light from the top-left)."""
    h, w = a.shape[:2]
    if h > 2 * width and w > 2 * width:
        if left:
            dl(a[:, :width], left)
        if right:
            dl(a[:, w - width:], right)
        if top:
            dl(a[:width, :], top)
        if bottom:
            dl(a[h - width:, :], bottom)
    elif h > 1 and top:
        dl(a[:1, :], top)
    return a


def wear(a, seed, amount=0.06, edge_only=True, light=0.06, dark=-0.08):
    """Chips and scuffs: random pixels lightened / darkened, concentrated on the region border."""
    h, w = a.shape[:2]
    r = rng(seed)
    x, y = grid(w, h)
    ed = np.minimum(np.minimum(x, w - 1 - x), np.minimum(y, h - 1 - y))
    p = amount * (np.exp(-ed / 1.2) if edge_only else 1.0)
    m = r.random((h, w)) < p
    m2 = r.random((h, w)) < 0.5
    dl(a, light, m & m2)
    dl(a, dark, m & ~m2)
    return a


def specks(a, seed, density, color, alpha=1.0, size=1):
    h, w = a.shape[:2]
    r = rng(seed)
    n = int(density * w * h)
    for _ in range(n):
        x, y = int(r.integers(0, w)), int(r.integers(0, h))
        rect(a, x, y, x + size - 1, y + size - 1, color, alpha)
    return a


def grime(a, seed, strength=0.25, color='#2a2016', scale=1.0, bottom=0.0, top=0.0, beta=1.3):
    """Soft dirt: low-frequency noise multiplied in; bottom/top bias (0..1) adds dirt near those edges."""
    h, w = a.shape[:2]
    n = noise(w, h, seed, beta=beta, wrap=False)
    n = (n - n.min()) / (np.ptp(n) + 1e-6)
    y = (np.arange(h)[:, None] + 0.5) / h
    f = np.clip(n * strength + bottom * y ** 2 + top * (1 - y) ** 2, 0, 1) * scale
    tint(a, color, np.clip(f, 0, 0.9))
    return a


def quantize(a, palette, dither=0.35, keep_alpha=True):
    """Snap colours to the nearest palette entry by OKLab lightness ordering (palette dark->light)."""
    from common import to_lab
    p = np.asarray(palette, np.float32)
    pl = to_lab(p[:, :3])[:, 0]
    al = to_lab(a[..., :3])[..., 0]
    h, w = a.shape[:2]
    t = (al - pl.min()) / (pl.max() - pl.min() + 1e-6)
    idx = np.clip(np.floor(t * (len(p) - 1) + 0.5 + bayer(w, h) * dither), 0, len(p) - 1).astype(int)
    out = a.copy()
    out[..., :3] = p[idx, :3]
    return out


# ================================================================================================ materials
def m_flat(w, h, palette, seed, base=None, amp=0.7, beta=1.1, sx=1.0, sy=1.0, dither=0.0):
    """Palette noise fill."""
    n = noise(w, h, seed, beta=beta, sx=sx, sy=sy, wrap=False)
    base = (len(palette) - 1) / 2 if base is None else base
    idx = base + n * amp + bayer(w, h) * dither
    return from_idx(idx, palette)


def m_wood(w, h, palette, seed, along=None, base=None, amp=0.55, lines=0.9, knots=0.0, planks=0, plank_dark=1.0):
    """Wood grain running along the longer side (or along='x'/'y'). planks: gap lines every n px across."""
    if along is None:
        along = 'x' if w >= h else 'y'
    L, S = (w, h) if along == 'x' else (h, w)
    r = rng(seed)
    n1 = noise(L, S, seed, beta=0.9, sx=8.0, sy=1.0, wrap=False)        # streaks along L
    n2 = noise(L, S, seed + 1, beta=1.4, wrap=False)
    base = (len(palette) - 1) * 0.55 if base is None else base
    t = np.arange(S)[None, :].repeat(L, 0).T.astype(np.float64)
    wav = np.sin((t + n2 * 1.8) * 1.7 + r.random() * 6) * 0.5 + 0.5
    idx = base + n1 * amp + (wav - 0.5) * lines * 0.8
    # occasional dark grain lines
    dark = (np.abs(np.sin((t + n2 * 2.2) * 0.9 + r.random() * 6)) < 0.12)
    idx = idx - dark * 0.9
    if planks:
        for k in range(0, S, planks):
            idx[k, :] -= plank_dark
    if knots > 0:
        for _ in range(int(knots * L * S / 64) + (1 if r.random() < knots else 0)):
            kx, ky = r.integers(0, L), r.integers(0, S)
            yy, xx = np.mgrid[0:S, 0:L]
            d = ((xx - kx) / 1.6) ** 2 + ((yy - ky) / 0.9) ** 2
            idx -= np.exp(-d) * 1.6
    out = from_idx(idx, palette)
    if along == 'y':
        out = out.transpose(1, 0, 2).copy()
    return out


def m_brushed(w, h, palette, seed, along='x', base=None, amp=0.45, scratches=0.04, smudge=0.0):
    """Brushed metal: fine streaks along an axis, a few scratches, optional greasy smudges."""
    sx, sy = (14.0, 1.0) if along == 'x' else (1.0, 14.0)
    n = noise(w, h, seed, beta=0.7, sx=sx, sy=sy, wrap=False)
    base = (len(palette) - 1) * 0.55 if base is None else base
    idx = base + n * amp
    out = from_idx(idx, palette)
    r = rng(seed + 7)
    for _ in range(int(scratches * w * h)):
        x0, y0 = r.integers(0, w), r.integers(0, h)
        ln = int(r.integers(2, 6))
        dx, dy = (1, 0) if r.random() < 0.6 else (1, 1 if r.random() < 0.5 else -1)
        for k in range(ln):
            px(out, x0 + dx * k, y0 + dy * k, palette[min(len(palette) - 1, int(base) + 2)], 0.5)
    if smudge:
        grime(out, seed + 3, strength=smudge, color='#4a3a22', beta=1.6)
    return out


def m_vinyl(w, h, palette, seed, base=None, sheen=0.9, cracks=0.0):
    """Leatherette: soft mottling, a broad sheen band, fine grain, optional cracks."""
    n = noise(w, h, seed, beta=2.2, wrap=False)
    g = noise(w, h, seed + 1, beta=0.2, wrap=False)
    base = (len(palette) - 1) * 0.5 if base is None else base
    y = (np.arange(h)[:, None] + 0.5) / max(h, 1)
    band = np.exp(-((y - 0.3) / 0.22) ** 2) * sheen
    idx = base + n * 0.35 + g * 0.12 + band
    out = from_idx(idx, palette)
    if cracks:
        r = rng(seed + 9)
        for _ in range(int(cracks * w * h / 16) + (1 if r.random() < cracks * 4 else 0)):
            x, yy = int(r.integers(1, max(2, w - 1))), int(r.integers(1, max(2, h - 1)))
            for k in range(int(r.integers(2, 5))):
                px(out, x, yy, palette[1])
                x += int(r.integers(-1, 2))
                yy += 1 if r.random() < 0.6 else 0
                px(out, x + 1, yy, palette[min(len(palette) - 1, 5)], 0.35)
    return out


def m_paint(w, h, palette, seed, base=None, amp=0.35, scuffs=0.05, rust=0.0):
    n = noise(w, h, seed, beta=1.3, wrap=False)
    base = (len(palette) - 1) * 0.55 if base is None else base
    out = from_idx(base + n * amp, palette)
    if scuffs:
        wear(out, seed + 2, amount=scuffs, edge_only=False, light=0.05, dark=-0.06)
    if rust:
        r = rng(seed + 5)
        m = r.random((h, w)) < rust
        m = ndimage.binary_dilation(m, iterations=1) & (r.random((h, w)) < 0.6)
        tint(out, '#5a2c14', 0.6, m)
    return out


def m_plastic(w, h, palette, seed, base=None, amp=0.25, yellow=0.0):
    n = noise(w, h, seed, beta=1.8, wrap=False)
    base = (len(palette) - 1) * 0.6 if base is None else base
    out = from_idx(base + n * amp, palette)
    if yellow:
        tint(out, '#c9a75a', yellow)
    return out


def m_granite(w, h, palette, seed, base=None):
    r = rng(seed)
    n = noise(w, h, seed, beta=0.4, wrap=False)
    base = (len(palette) - 1) * 0.45 if base is None else base
    idx = base + n * 0.9
    out = from_idx(idx, palette)
    m = r.random((h, w)) < 0.07
    out[m] = C('#8a8780')
    m2 = r.random((h, w)) < 0.05
    out[m2] = C('#5b4a3e')
    return out


def m_glass(w, h, tint_c='#9fb9bd', alpha=70, seed=0, streaks=0.4, edge=None):
    r = rng(seed)
    out = blank(w, h, C(tint_c, alpha))
    n = noise(w, h, seed, beta=1.2, sx=1, sy=6, wrap=False)
    out[..., 3] = np.clip(alpha + n * 10, 20, 255)
    x, y = grid(w, h)
    diag = ((x + y * 0.9) % 11) < 1.4
    out[diag, :3] = out[diag, :3] * 0.6 + 255 * 0.4
    out[diag, 3] = np.minimum(255, out[diag, 3] + 30)
    if edge:
        frame(out, 0, 0, w - 1, h - 1, edge)
    return out


def m_fabric(w, h, palette, seed, base=None):
    x, y = grid(w, h)
    n = noise(w, h, seed, beta=1.5, wrap=False)
    base = (len(palette) - 1) * 0.5 if base is None else base
    weave = (((x + y) % 2) * 0.35 - 0.17)
    return from_idx(base + n * 0.5 + weave, palette)


def m_cardboard(w, h, seed, base=3.0, flute=True):
    n = noise(w, h, seed, beta=1.5, wrap=False)
    out = from_idx(base + n * 0.6, CARDBOARD)
    if flute:
        x, _ = grid(w, h)
        dl(out, -0.025, (x.astype(int) % 2) == 0)
    return out


def m_concrete(w, h, seed, base=3.5, amp=0.8, palette=CONCRETE):
    n = noise(w, h, seed, beta=1.2, wrap=False)
    n2 = noise(w, h, seed + 1, beta=0.3, wrap=False)
    return from_idx(base + n * amp + n2 * 0.3, palette)


# ================================================================================================ glyphs / text
_GLYPH_CACHE = {}


def text_mask(text, size, font_spec=FONT_SANS_JP_BOLD, binary=True, spacing=0, vertical=False, threshold=0.5,
              ss=1):
    """Render text to a 0..1 mask cropped to its ink. binary: crisp pixel glyphs (fontmode 1).
    ss > 1: supersample and box-downsample (antialiased mask)."""
    key = (text, size, font_spec, binary, spacing, vertical, threshold, ss)
    if key in _GLYPH_CACHE:
        return _GLYPH_CACHE[key].copy()
    S = ss
    f = font(font_spec, size * S)
    W = (size * S + spacing * S) * (len(text) + 2) + 8
    H = size * S * (len(text) + 2 if vertical else 3)
    im = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(im)
    if binary and S == 1:
        d.fontmode = '1'
    if not vertical:
        x = 4
        for ch in text:
            d.text((x, size * S // 2), ch, fill=255, font=f)
            x += int(d.textlength(ch, font=f)) + spacing * S
    else:
        y = size * S // 2
        for ch in text:
            bb = d.textbbox((0, 0), ch, font=f)
            d.text((4 + (size * S - (bb[2] - bb[0])) // 2 - bb[0], y - bb[1]), ch, fill=255, font=f)
            y += size * S + spacing * S
    m = np.asarray(im, np.float32) / 255.0
    if S > 1:
        h2, w2 = m.shape[0] // S, m.shape[1] // S
        m = m[:h2 * S, :w2 * S].reshape(h2, S, w2, S).mean((1, 3))
        if binary:
            m = (m >= threshold).astype(np.float32)
    ys, xs = np.nonzero(m > 0.01)
    if len(xs) == 0:
        return np.zeros((1, 1), np.float32)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    _GLYPH_CACHE[key] = m
    return m.copy()


# 3x5 pixel font (caps, digits, a few signs)
FONT35 = {
    'A': ['.#.', '#.#', '###', '#.#', '#.#'], 'B': ['##.', '#.#', '##.', '#.#', '##.'], 'C': ['.##', '#..', '#..', '#..', '.##'],
    'D': ['##.', '#.#', '#.#', '#.#', '##.'], 'E': ['###', '#..', '##.', '#..', '###'], 'F': ['###', '#..', '##.', '#..', '#..'],
    'G': ['.##', '#..', '#.#', '#.#', '.##'], 'H': ['#.#', '#.#', '###', '#.#', '#.#'], 'I': ['#', '#', '#', '#', '#'],
    'J': ['..#', '..#', '..#', '#.#', '.#.'], 'K': ['#.#', '#.#', '##.', '#.#', '#.#'], 'L': ['#..', '#..', '#..', '#..', '###'],
    'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#'], 'N': ['#..#', '##.#', '#.##', '#..#', '#..#'],
    'O': ['.#.', '#.#', '#.#', '#.#', '.#.'], 'P': ['##.', '#.#', '##.', '#..', '#..'], 'Q': ['.#.', '#.#', '#.#', '##.', '.##'],
    'R': ['##.', '#.#', '##.', '#.#', '#.#'], 'S': ['.##', '#..', '.#.', '..#', '##.'], 'T': ['###', '.#.', '.#.', '.#.', '.#.'],
    'U': ['#.#', '#.#', '#.#', '#.#', '###'], 'V': ['#.#', '#.#', '#.#', '#.#', '.#.'], 'W': ['#...#', '#...#', '#.#.#', '##.##', '#...#'],
    'X': ['#.#', '#.#', '.#.', '#.#', '#.#'], 'Y': ['#.#', '#.#', '.#.', '.#.', '.#.'], 'Z': ['###', '..#', '.#.', '#..', '###'],
    '0': ['###', '#.#', '#.#', '#.#', '###'], '1': ['.#', '##', '.#', '.#', '.#'], '2': ['###', '..#', '###', '#..', '###'],
    '3': ['###', '..#', '.##', '..#', '###'], '4': ['#.#', '#.#', '###', '..#', '..#'], '5': ['###', '#..', '###', '..#', '###'],
    '6': ['###', '#..', '###', '#.#', '###'], '7': ['###', '..#', '.#.', '.#.', '.#.'], '8': ['###', '#.#', '###', '#.#', '###'],
    '9': ['###', '#.#', '###', '..#', '###'], ' ': ['.', '.', '.', '.', '.'], '-': ['...', '...', '###', '...', '...'],
    '.': ['.', '.', '.', '.', '#'], ':': ['.', '#', '.', '#', '.'], '/': ['..#', '..#', '.#.', '#..', '#..'],
    '!': ['#', '#', '#', '.', '#'], '¥': ['#.#', '.#.', '###', '.#.', '.#.'], '+': ['...', '.#.', '###', '.#.', '...'],
    '%': ['#.#', '..#', '.#.', '#..', '#.#'], "'": ['#', '#', '.', '.', '.'],
}
# 5x7 dot-matrix digits (LED displays)
DIGIT57 = {
    '0': ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'],
    '1': ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
    '2': ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'],
    '3': ['#####', '...#.', '..#..', '...#.', '....#', '#...#', '.###.'],
    '4': ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'],
    '5': ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
    '6': ['..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'],
    '7': ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
    '8': ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'],
    '9': ['.###.', '#...#', '#...#', '.####', '....#', '...#.', '.##..'],
}


def bitmap(rows):
    h = len(rows)
    w = max(len(r) for r in rows)
    m = np.zeros((h, w), np.float32)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch not in '. ':
                m[y, x] = 1
    return m


def text35(s, gap=1):
    """Mask of a string in the 3x5 font."""
    glyphs = [bitmap(FONT35.get(ch.upper(), FONT35[' '])) for ch in s]
    w = sum(g.shape[1] for g in glyphs) + gap * (len(glyphs) - 1)
    m = np.zeros((5, max(w, 1)), np.float32)
    x = 0
    for g in glyphs:
        m[:, x:x + g.shape[1]] = g
        x += g.shape[1] + gap
    return m


def stamp_mask(a, mask, x, y, color, alpha=1.0):
    """Paint colour where mask (0..1) at (x, y) (top-left), clipped."""
    h, w = mask.shape
    H, W = a.shape[:2]
    c = C(color)
    for yy in range(h):
        ty = y + yy
        if not 0 <= ty < H:
            continue
        for xx in range(w):
            tx = x + xx
            if not 0 <= tx < W:
                continue
            m = mask[yy, xx] * alpha
            if m <= 0.003:
                continue
            a[ty, tx, :3] = a[ty, tx, :3] * (1 - m) + c[:3] * m
            a[ty, tx, 3] = max(a[ty, tx, 3], c[3] * m)
    return a


def stamp_center(a, mask, cx, cy, color, alpha=1.0):
    h, w = mask.shape
    return stamp_mask(a, mask, int(round(cx - w / 2)), int(round(cy - h / 2)), color, alpha)


# ================================================================================================ sun logo
def sun_logo(size, ss=8, face=True, rays=12, ray_len=0.30, disc=0.62, bg=None, outline=True, colors=None):
    """The HIDAMARI smiling sun as an antialiased RGBA array (size x size)."""
    col = dict(ray='#ef7d22', ray2='#f39a35', disc='#fbb534', disc_hi='#ffd36a', line='#5a2c12', cheek='#f07a52',
               outline='#7a3510')
    if colors:
        col.update(colors)
    S = size * ss
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0) if bg is None else tuple(int(v) for v in C(bg)))
    d = ImageDraw.Draw(im)
    c = S / 2
    R_disc = S / 2 * disc
    R_ray = S / 2 * min(0.98, disc + ray_len)
    for i in range(rays):
        ang = i / rays * 2 * math.pi - math.pi / 2
        long_ = i % 2 == 0
        rr = R_ray if long_ else R_disc + (R_ray - R_disc) * 0.62
        half = math.pi / rays * (0.55 if long_ else 0.5)
        p = [(c + math.cos(ang) * rr, c + math.sin(ang) * rr),
             (c + math.cos(ang + half) * R_disc * 0.92, c + math.sin(ang + half) * R_disc * 0.92),
             (c + math.cos(ang - half) * R_disc * 0.92, c + math.sin(ang - half) * R_disc * 0.92)]
        fill = C(col['ray'] if long_ else col['ray2'])
        d.polygon(p, fill=tuple(int(v) for v in fill))
        # rounded ray tip
        tr = S * 0.018
        d.ellipse([p[0][0] - tr, p[0][1] - tr, p[0][0] + tr, p[0][1] + tr], fill=tuple(int(v) for v in fill))
    if outline:
        o = S * 0.025
        d.ellipse([c - R_disc - o, c - R_disc - o, c + R_disc + o, c + R_disc + o],
                  fill=tuple(int(v) for v in C(col['outline'])))
    d.ellipse([c - R_disc, c - R_disc, c + R_disc, c + R_disc], fill=tuple(int(v) for v in C(col['disc'])))
    # highlight
    hr = R_disc * 0.72
    d.ellipse([c - hr - R_disc * 0.18, c - hr - R_disc * 0.22, c + hr - R_disc * 0.18, c + hr - R_disc * 0.22],
              fill=tuple(int(v) for v in C(col['disc_hi'])))
    d.ellipse([c - R_disc * 0.86, c - R_disc * 0.86, c + R_disc * 0.86, c + R_disc * 0.86],
              outline=None)
    if face:
        lw = max(1, int(S * 0.045))
        lc = tuple(int(v) for v in C(col['line']))
        er = R_disc * 0.2
        for sx in (-1, 1):
            ex, ey = c + sx * R_disc * 0.36, c - R_disc * 0.12
            d.arc([ex - er, ey - er * 0.9, ex + er, ey + er * 0.9], 200, 340, fill=lc, width=lw)
        mr = R_disc * 0.52
        d.arc([c - mr, c - mr * 0.75, c + mr, c + mr * 0.95], 25, 155, fill=lc, width=lw)
        cr = R_disc * 0.12
        for sx in (-1, 1):
            cx_, cy_ = c + sx * R_disc * 0.6, c + R_disc * 0.2
            d.ellipse([cx_ - cr, cy_ - cr * 0.7, cx_ + cr, cy_ + cr * 0.7], fill=tuple(int(v) for v in C(col['cheek'], 170)))
    a = np.asarray(im, np.float32)
    # premultiplied box downsample
    al = a[..., 3:4] / 255.0
    pm = np.concatenate([a[..., :3] * al, al], -1).reshape(size, ss, size, ss, 4).mean((1, 3))
    rgb = np.where(pm[..., 3:4] > 1e-4, pm[..., :3] / np.maximum(pm[..., 3:4], 1e-4), 0)
    return np.concatenate([rgb, pm[..., 3:4] * 255], -1).astype(np.float32)


# hand-pixelled small suns (o = outline/ray dark, r = ray, y = disc, h = highlight, l = face line)
SUN_PIX = {
    7: ['...r...',
        '.r.y.r.',
        '..yhy..',
        'ryl.lyr',
        '..yly..',
        '.r.y.r.',
        '...r...'],
    9: ['....r....',
        '.r.....r.',
        '...yyy...',
        '..yhhyy..',
        'r.yl.ly.r',
        '..yylyy..',
        '...yyy...',
        '.r.....r.',
        '....r....'],
    11: ['.....r.....',
         '..r.....r..',
         '....yyy....',
         '...yhhyy...',
         '..yhl.lyy..',
         'r.yyyyyyy.r',
         '..yylllyy..',
         '...yyyyy...',
         '....yyy....',
         '..r.....r..',
         '.....r.....'],
}
SUN_PIX[12] = ['.....rr.....',
               '.r........r.',
               '....yyyy....',
               '...yhhyyy...',
               '..yhhyyyyy..',
               'r.yylyylyy.r',
               'r.yyyyyyyy.r',
               '..yclyylcy..',
               '...yyllyy...',
               '....yyyy....',
               '.r........r.',
               '.....rr.....']
SUN_PIX[16] = ['.......rr.......',
               '.r.....rr.....r.',
               '..r..........r..',
               '......yyyy......',
               '....yhhyyyyy....',
               '...yhhyyyyyyy...',
               '...yhyyyyyyyy...',
               'rr.yylyyyylyy.rr',
               'rr.ylylyylyly.rr',
               '...ycyyyyyycy...',
               '...yylyyyylyy...',
               '....yyllllyy....',
               '......yyyy......',
               '..r..........r..',
               '.r.....rr.....r.',
               '.......rr.......']
SUN_LEGEND = {'r': '#ee7a22', 'y': '#f9b232', 'h': '#ffd56c', 'l': '#5a2c12', 'o': '#e89a2a', 'c': '#f08060'}


def sun_small(n, legend=None):
    lg = dict(SUN_LEGEND)
    if legend:
        lg.update(legend)
    rows = SUN_PIX[n]
    a = blank(n, n)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:n]):
            if ch in lg:
                a[y, x] = C(lg[ch])
    return a


def downsample(arr, k):
    """Premultiplied box downsample by an integer factor."""
    h, w = arr.shape[:2]
    a = arr[..., 3:4] / 255.0
    pm = np.concatenate([arr[..., :3] * a, a * 255.0], -1)
    pm = pm[:h // k * k, :w // k * k].reshape(h // k, k, w // k, k, 4).mean((1, 3))
    al = pm[..., 3:4] / 255.0
    rgb = np.where(al > 1e-4, pm[..., :3] / np.maximum(al, 1e-4), 0)
    return np.concatenate([rgb, pm[..., 3:4]], -1).astype(np.float32)


def upscale_nn(arr, k):
    return np.repeat(np.repeat(arr, k, 0), k, 1)


def binarize_alpha(arr, thr=110):
    out = arr.copy()
    out[..., 3] = np.where(out[..., 3] >= thr, 255, 0)
    return out
