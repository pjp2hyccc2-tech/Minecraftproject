"""Procedural pixel-art materials and decal helpers for the ETERNAL REALM entity textures.

Every face of every box is painted from its material (style + palette entry of the variant), with light coming
from above: top faces brighter, bottoms darker, vertical gradients and bevelled edges on the sides. Decals
(faces, emblems, belts, runes) are drawn afterwards through FaceCanvas in face-local coordinates
(x to the right, y down, as seen from outside the box). All randomness is seeded: output is deterministic.
"""
import hashlib
import math

import numpy as np


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def seed_of(*keys):
    return int(hashlib.sha256('/'.join(str(k) for k in keys).encode()).hexdigest()[:8], 16)


class FaceCanvas:
    """Drawing on one face rectangle of the texture, in face-local coordinates (x to the right, y down).

    Coordinates are model units (one per box unit); a texture painted at a texel density {@code k} > 1 (the bosses)
    has k x k pixels per unit: {@link #px} fills the whole unit, {@link #fpx} and the {@code f*} helpers address
    single pixels for fine detail ({@code fw} x {@code fh} of them).
    """

    def __init__(self, img, rect, face, k=1):
        self.img = img
        self.u, self.v, self.w, self.h = rect
        self.face = face
        self.k = k
        self.fw, self.fh = self.w * k, self.h * k

    def px(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            k = self.k
            y0, x0 = (self.v + y) * k, (self.u + x) * k
            self.img[y0:y0 + k, x0:x0 + k, :3] = c[:3]
            self.img[y0:y0 + k, x0:x0 + k, 3] = a if len(c) < 4 else c[3]

    def get(self, x, y):
        k = self.k
        return tuple(int(v) for v in self.img[(self.v + y) * k + k // 2, (self.u + x) * k + k // 2, :3])

    def rect(self, x0, y0, x1, y1, c, a=255):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c, a)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            k = self.k
            self.img[(self.v + y) * k:(self.v + y + 1) * k, (self.u + x) * k:(self.u + x + 1) * k, 3] = 0

    def tint(self, x, y, f):
        if 0 <= x < self.w and 0 <= y < self.h:
            k = self.k
            block = self.img[(self.v + y) * k:(self.v + y + 1) * k, (self.u + x) * k:(self.u + x + 1) * k, :3]
            block[...] = np.clip(block.astype(np.float64) * f, 0, 255).astype(block.dtype)

    def cx(self):
        return self.w // 2

    # -------------------------------------------------------------- single pixels (fine detail)

    def fpx(self, x, y, c, a=255):
        if 0 <= x < self.fw and 0 <= y < self.fh:
            self.img[self.v * self.k + y, self.u * self.k + x, :3] = c[:3]
            self.img[self.v * self.k + y, self.u * self.k + x, 3] = a if len(c) < 4 else c[3]

    def fget(self, x, y):
        return tuple(int(v) for v in self.img[self.v * self.k + y, self.u * self.k + x, :3])

    def fclear(self, x, y):
        if 0 <= x < self.fw and 0 <= y < self.fh:
            self.img[self.v * self.k + y, self.u * self.k + x, 3] = 0

    def frect(self, x0, y0, x1, y1, c, a=255):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.fpx(x, y, c, a)

    def ftint(self, x, y, f):
        if 0 <= x < self.fw and 0 <= y < self.fh:
            self.fpx(x, y, shade(self.fget(x, y), f))


# ---------------------------------------------------------------------------------------------- materials

def palette_entry(pal, key):
    e = pal.get(key)
    if e is None:
        raise KeyError(key)
    if isinstance(e, str):
        e = rgb(e)
    if isinstance(e, tuple):
        e = {'base': e}
    out = dict(e)
    for k, v in list(out.items()):
        if isinstance(v, str) and v.startswith('#'):
            out[k] = rgb(v)
    b = out['base']
    out.setdefault('dark', shade(b, 0.68))
    out.setdefault('light', lighten(b, 0.22))
    out.setdefault('accent', out['light'])
    return out


def paint_face(img, rect, style, pe, face, rng, cube, k=1):
    u, v, w, h = rect
    if w <= 0 or h <= 0:
        return
    fn = MATERIALS.get(style)
    if fn is None:
        raise KeyError('unknown material style ' + style)
    tile = np.zeros((h * k, w * k, 4), dtype=np.float64)
    tile[..., 3] = 255
    fn(tile, dict(pe, _k=k) if k != 1 else pe, face, rng, cube)
    if style not in UNSHADED:
        apply_light(tile, face, style)
    region = img[v * k:(v + h) * k, u * k:(u + w) * k]
    region[...] = np.clip(tile, 0, 255)


def kof(pe):
    """Texel density the face is painted at (feature sizes such as trims and rivets scale with it)."""
    return pe.get('_k', 1)


UNSHADED = {'flame', 'gem', 'glowcore', 'void', 'eye'}


def apply_light(t, face, style):
    h, w = t.shape[:2]
    if face == 'top':
        t[..., :3] *= 1.08
    elif face == 'bottom':
        t[..., :3] *= 0.74
    else:
        for y in range(h):
            f = 1.06 - 0.16 * (y / max(1, h - 1))
            t[y, :, :3] *= f
        if h >= 3:
            t[h - 1, :, :3] *= 0.86
            t[0, :, :3] = t[0, :, :3] * 1.07
        if w >= 4 and style not in ('leaf', 'cloud', 'ghost', 'fur'):
            t[:, 0, :3] *= 0.93
            t[:, w - 1, :3] *= 0.93


def noise(t, rng, amount):
    h, w = t.shape[:2]
    n = 1.0 + rng.uniform(-amount, amount, (h, w, 1))
    t[..., :3] *= n


def fill(t, c):
    t[..., 0], t[..., 1], t[..., 2] = c[0], c[1], c[2]


def m_plain(t, pe, face, rng, cube):
    fill(t, pe['base'])
    noise(t, rng, 0.03)


def m_skin(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    noise(t, rng, 0.035)
    if face not in ('top', 'bottom') and w >= 3:
        for y in range(h):
            if rng.random() < 0.15:
                x = rng.integers(0, w)
                t[y, x, :3] *= 0.94


def m_cloth(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    if face not in ('top', 'bottom'):
        x = int(rng.integers(0, 3))
        while x < w:
            y0 = int(rng.integers(0, max(1, h // 3)))
            for y in range(y0, h):
                t[y, x, :3] *= 0.84
                if x + 1 < w:
                    t[y, x + 1, :3] *= 1.07
            x += int(rng.integers(3, 5))
        trim = pe.get('trim')
        k = kof(pe)
        if trim is not None and h >= 4 * k:
            t[h - k:h, :, :3] = shade(trim, 0.9)
            t[h - 2 * k:h - k, :, :3] = trim
            if k > 1:
                t[h - 2 * k, :, :3] = lighten(trim, 0.25)
            if pe.get('trim_top'):
                t[0:k, :, :3] = trim
    noise(t, rng, 0.04)


def m_leather(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    for _ in range(max(1, w * h // 10)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] *= rng.uniform(0.82, 1.1)
    k = kof(pe)
    if face in ('front', 'back') and w >= 5 * k and h >= 5 * k:
        st = lighten(pe['base'], 0.35)
        for x in range(k, w - k, 2 * k):
            t[k, x:x + k, :3] = st
            t[h - 1 - k, x:x + k, :3] = st
    noise(t, rng, 0.03)


def m_metal(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        f = 1.16 - 0.32 * (y / max(1, h - 1)) if face not in ('top', 'bottom') else 1.0
        t[y, :, :3] = shade(b, f)
    if w >= 3 and h >= 3:
        streak = lighten(b, 0.45)
        for i in range(-h, w):
            for k in range(2):
                x, y = i + k + (h // 3), (h // 3) + (i if i >= 0 else 0)
                if 0 <= x < w and 0 <= y < h and x == y + k + (h // 3) - (h // 3):
                    pass
        for d in range(min(w, h)):
            x, y = d + max(0, w // 3), d
            if x < w and y < h:
                t[y, x, :3] = streak
                if x + 1 < w:
                    t[y, x + 1, :3] = lighten(b, 0.25)
        dark = shade(b, 0.55)
        t[h - 1, :, :3] = dark
        t[:, w - 1, :3] = shade(b, 0.7)
        t[0, :, :3] = lighten(b, 0.3)
        t[:, 0, :3] = lighten(b, 0.12)
    k = kof(pe)
    if w >= 6 * k and h >= 6 * k and face not in ('top', 'bottom') and pe.get('rivets', True):
        for (x, y) in ((k, k), (w - 2 * k, k), (k, h - 2 * k), (w - 2 * k, h - 2 * k)):
            t[y:y + k, x:x + k, :3] = lighten(b, 0.6)
            if k > 1:
                t[y, x, :3] = lighten(b, 0.85)
            if y + k < h and x + k < w:
                t[y + k, x + k, :3] = shade(b, 0.5)
    noise(t, rng, 0.02)


def m_fur(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    dark, light = pe['dark'], pe['light']
    for x in range(w):
        y = int(rng.integers(-2, 2))
        while y < h:
            ln = int(rng.integers(2, 4))
            col = dark if rng.random() < 0.45 else light
            for k in range(ln):
                if 0 <= y + k < h:
                    t[y + k, x, :3] = mix(tuple(t[y + k, x, :3]), col, 0.55)
            y += ln + int(rng.integers(1, 3))
    noise(t, rng, 0.04)


def m_scale(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    for y in range(0, h, 2):
        off = (y // 2) % 2
        for x in range(-1, w, 3):
            xs = x + off
            for dx in range(3):
                if 0 <= xs + dx < w:
                    t[y, xs + dx, :3] = pe['light'] if dx == 1 else pe['base']
                    if y + 1 < h:
                        t[y + 1, xs + dx, :3] = pe['dark'] if dx != 1 else pe['base']
    noise(t, rng, 0.03)


def m_bone(t, pe, face, rng, cube):
    fill(t, pe['base'])
    h, w = t.shape[:2]
    crack(t, rng, shade(pe['base'], 0.7), max(1, (w * h) // 40), 3)
    noise(t, rng, 0.03)


def crack(t, rng, col, count, length):
    h, w = t.shape[:2]
    out = []
    for _ in range(count):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for _ in range(length + int(rng.integers(0, 3))):
            if 0 <= x < w and 0 <= y < h:
                t[y, x, :3] = col
                out.append((x, y))
            x += int(rng.integers(-1, 2))
            y += int(rng.integers(0, 2))
    return out


def m_wood(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    phase = rng.uniform(0, 6.28)
    for y in range(h):
        for x in range(w):
            g = math.sin((x + 1.3 * math.sin(y * 0.5 + phase)) * 1.9)
            t[y, x, :3] = shade(b, 1.0 + 0.09 * g)
    if face in ('top', 'bottom'):
        cx, cy = w / 2.0, h / 2.0
        for y in range(h):
            for x in range(w):
                r = math.hypot(x - cx + 0.5, y - cy + 0.5)
                t[y, x, :3] = shade(pe.get('ring', lighten(b, 0.2)), 0.92 + 0.12 * math.sin(r * 2.2))
    noise(t, rng, 0.03)


def m_bark(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    if face in ('top', 'bottom'):
        m_wood(t, {'base': pe.get('inner', lighten(b, 0.35)), 'ring': pe.get('inner', lighten(b, 0.35))}, face, rng, cube)
        for x in range(w):
            t[0, x, :3] = b
            t[h - 1, x, :3] = b
        for y in range(h):
            t[y, 0, :3] = b
            t[y, w - 1, :3] = b
        return
    x = int(rng.integers(0, 3))
    while x < w:
        wob = 0
        for y in range(h):
            if rng.random() < 0.3:
                wob = max(-1, min(1, wob + int(rng.integers(-1, 2))))
            xx = x + wob
            if 0 <= xx < w:
                t[y, xx, :3] = shade(b, 0.55)
                if xx + 1 < w:
                    t[y, xx + 1, :3] = shade(b, 1.18)
        x += int(rng.integers(3, 5))
    moss = pe.get('moss')
    if moss is not None:
        for _ in range(w * h // 9):
            x, y = int(rng.integers(0, w)), int(rng.integers(0, max(1, h // 2)))
            t[y, x, :3] = shade(moss, rng.uniform(0.85, 1.15))
    noise(t, rng, 0.04)


def m_leaf(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    shades = [shade(pe['base'], 0.7), pe['base'], shade(pe['base'], 1.15), lighten(pe['base'], 0.25)]
    for y in range(h):
        for x in range(w):
            t[y, x, :3] = shades[int(rng.integers(0, 3))]
    for _ in range(max(1, w * h // 6)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        c = shades[3] if rng.random() < 0.5 else shades[0]
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            if x + dx < w and y + dy < h:
                t[y + dy, x + dx, :3] = c
    flowers = pe.get('flowers')
    if flowers is not None:
        for _ in range(max(1, w * h // 40)):
            x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
            t[y, x, :3] = flowers
    if face != 'top' and pe.get('ragged', True):
        for x in range(w):
            if rng.random() < 0.45:
                t[h - 1, x, 3] = 0
            if rng.random() < 0.25 and h > 2:
                t[h - 2, x, 3] = 0 if t[h - 1, x, 3] == 0 else 255


def m_stone(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    y = 0
    row = 0
    k = kof(pe)
    while y < h:
        bh = int(rng.integers(3, 5)) * k
        x = -int(rng.integers(0, 3)) * k if row % 2 else 0
        while x < w:
            bw = int(rng.integers(3, 7)) * k
            f = rng.uniform(0.9, 1.1)
            for yy in range(y, min(h, y + bh)):
                for xx in range(max(0, x), min(w, x + bw)):
                    t[yy, xx, :3] = shade(b, f)
            for yy in range(y, min(h, y + bh)):
                if 0 <= x < w:
                    t[yy, x, :3] = shade(b, 0.66)
            x += bw
        if y < h:
            t[y, :, :3] = shade(b, 0.7)
        y += bh
        row += 1
    noise(t, rng, 0.05)
    if pe.get('cracks', True):
        crack(t, rng, shade(b, 0.5), max(1, (w * h) // 60), 3)


def m_sandstone(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        band = (y // 3) % 3
        t[y, :, :3] = shade(b, (1.06, 0.97, 1.0)[band])
        if y % 3 == 0:
            t[y, :, :3] = shade(b, 1.12)
    for _ in range(w * h // 14):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] = shade(b, 0.8)
    noise(t, rng, 0.03)
    if pe.get('cracks', True):
        crack(t, rng, shade(b, 0.6), max(1, (w * h) // 80), 3)


def m_ice(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            t[y, x, :3] = shade(b, 1.0 - 0.15 * (y / max(1, h - 1)))
    for k in range(max(1, (w + h) // 6)):
        x0 = int(rng.integers(-h, w))
        for d in range(h):
            x = x0 + d
            if 0 <= x < w:
                t[d, x, :3] = lighten(b, 0.5)
                if x + 1 < w:
                    t[d, x + 1, :3] = lighten(b, 0.25)
    for _ in range(max(1, w * h // 25)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] = shade(b, 0.75)
    t[0, :, :3] = lighten(b, 0.55)
    noise(t, rng, 0.02)


def m_crystal(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            upper = (x / max(1, w - 1)) + (y / max(1, h - 1)) < 1.0
            t[y, x, :3] = lighten(b, 0.3) if upper else shade(b, 0.82)
    for d in range(min(w, h)):
        x, y = w - 1 - d, d
        if 0 <= x < w:
            t[y, x, :3] = lighten(b, 0.6)
    for _ in range(max(1, w * h // 18)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] = (255, 255, 255)


def m_magma(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    noise(t, rng, 0.08)
    hot = pe.get('hot', (255, 140, 30))
    pts = crack(t, rng, hot, max(2, (w * h) // 22), 4)
    for (x, y) in pts:
        if rng.random() < 0.3:
            t[y, x, :3] = (255, 220, 90)
    cube.__dict__.setdefault('_hot', {})[(face, w, h)] = pts


def m_flame(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    cols = [(255, 245, 160), (255, 200, 60), (255, 130, 30), (220, 60, 20), (150, 30, 20)]
    if pe.get('cold'):
        cols = [(230, 255, 255), (150, 230, 255), (80, 170, 255), (50, 100, 220), (40, 50, 160)]
    for y in range(h):
        for x in range(w):
            k = (h - 1 - y) / max(1, h - 1) * 4 + rng.uniform(-0.6, 0.6)
            k = max(0, min(4, int(round(k))))
            t[y, x, :3] = cols[k]
    if face != 'bottom':
        for x in range(w):
            depth = int(rng.integers(0, max(1, h // 3)))
            for y in range(depth):
                t[y, x, 3] = 0


def m_ghost(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            g = math.sin(x * 0.9 + y * 0.45 + rng.uniform(0, 0.4))
            t[y, x, :3] = shade(b, 0.95 + 0.1 * g)
    for _ in range(max(1, w // 3)):
        x = int(rng.integers(0, w))
        for y in range(h):
            xx = x + int(round(math.sin(y * 0.6)))
            if 0 <= xx < w:
                t[y, xx, :3] = shade(b, 0.8)
    if face not in ('top', 'bottom') and pe.get('ragged', True):
        for x in range(w):
            depth = int(rng.integers(0, 3))
            for k in range(depth):
                t[h - 1 - k, x, 3] = 0


def m_feather(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b, dark, light = pe['base'], pe['dark'], pe['light']
    fill(t, b)
    row_h = 4
    for r, y0 in enumerate(range(0, h, row_h - 1)):
        off = (r % 2) * 2
        for x0 in range(-off, w, 3):
            for y in range(y0, min(h, y0 + row_h)):
                for dx in range(3):
                    x = x0 + dx
                    if 0 <= x < w:
                        if dx == 1:
                            t[y, x, :3] = shade(b, 0.78)
                        else:
                            t[y, x, :3] = light if (y - y0) < 1 else shade(b, 1.0 - 0.05 * (y - y0))
    tip = pe.get('tip')
    if tip is not None:
        for x in range(w):
            for k in range(min(2, h)):
                t[h - 1 - k, x, :3] = tip
    if face in ('front', 'back') and pe.get('notched', False):
        for x in range(w):
            if x % 3 == 2:
                t[h - 1, x, 3] = 0
    noise(t, rng, 0.03)


def m_membrane(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            t[y, x, :3] = shade(b, 1.0 - 0.25 * (y / max(1, h - 1)))
    vein = pe.get('vein', lighten(b, 0.25))
    for k in range(1, 5):
        x0 = int(w * k / 5)
        for y in range(h):
            x = x0 + int((y / max(1, h)) * (k - 2.5) * 2)
            if 0 <= x < w:
                t[y, x, :3] = vein
    if pe.get('scallop', True):
        kk = kof(pe)
        for x in range(w):
            depth = int(round((1.5 + 1.5 * math.cos((x % (6 * kk)) / (6.0 * kk) * 2 * math.pi)) * kk))
            for k in range(depth):
                if h - 1 - k >= 0:
                    t[h - 1 - k, x, 3] = 0
    noise(t, rng, 0.03)


def m_chain(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, shade(b, 0.5))
    for y in range(h):
        for x in range(w):
            if (x + (y // 2)) % 2 == 0:
                t[y, x, :3] = lighten(b, 0.2) if y % 2 == 0 else b
    noise(t, rng, 0.03)


def m_bandage(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    y = int(rng.integers(0, 2))
    while y < h:
        t[y, :, :3] = shade(b, 0.7)
        slope = int(rng.integers(0, 2))
        for x in range(w):
            if slope and x % 4 == 0 and y + 1 < h:
                t[y + 1, x, :3] = shade(b, 0.8)
        y += int(rng.integers(2, 4))
    stain = pe.get('stain', (150, 125, 80))
    for _ in range(max(1, w * h // 20)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] = stain
    noise(t, rng, 0.04)


def m_cloud(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    for _ in range(max(2, w * h // 8)):
        cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(1.0, 2.5)
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                if d < r:
                    f = 1.12 if (x - cx) + (y - cy) < 0 else 0.86
                    t[y, x, :3] = shade(b, f)
    if face not in ('top',):
        for x in range(w):
            if rng.random() < 0.3:
                t[h - 1, x, 3] = 0


def m_void(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            g = math.sin(x * 0.7 + y * 0.9) * math.cos(y * 0.4 - x * 0.3)
            t[y, x, :3] = shade(b, 1.0 + 0.25 * g)
    for _ in range(max(1, w * h // 14)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        t[y, x, :3] = pe.get('star', (220, 190, 255))


def m_chitin(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        seg = y % 4
        t[y, :, :3] = shade(b, (1.2, 1.05, 0.95, 0.7)[seg])
    if w >= 4:
        for y in range(0, h, 4):
            x = int(rng.integers(1, max(2, w - 1)))
            t[y, x, :3] = lighten(b, 0.6)
    noise(t, rng, 0.03)


def m_hair(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for x in range(w):
        f = rng.uniform(0.85, 1.15)
        for y in range(h):
            t[y, x, :3] = shade(b, f * (1.0 - 0.1 * (y / max(1, h))))
    for _ in range(max(1, w // 3)):
        x = int(rng.integers(0, w))
        for y in range(h):
            t[y, x, :3] = lighten(b, 0.25)
    noise(t, rng, 0.02)


def m_mech(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        t[y, :, :3] = shade(b, 1.1 - 0.2 * (y / max(1, h - 1)))
    step = pe.get('panel', 5) * kof(pe)
    seam = shade(b, 0.55)
    for x in range(step, w, step):
        t[:, x, :3] = seam
    for y in range(step, h, step):
        t[y, :, :3] = seam
    for x in range(step, w, step):
        for y in range(step, h, step):
            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                if 0 <= x + dx < w and 0 <= y + dy < h:
                    t[y + dy, x + dx, :3] = lighten(b, 0.45)
    if w >= 2 and h >= 2:
        t[0, :, :3] = lighten(b, 0.35)
        t[h - 1, :, :3] = shade(b, 0.6)
    noise(t, rng, 0.02)


def m_gem(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    fill(t, b)
    for y in range(h):
        for x in range(w):
            t[y, x, :3] = shade(b, 1.15 - 0.4 * ((x + y) / max(1, w + h - 2)))
    t[0, 0, :3] = (255, 255, 255)
    if w > 2 and h > 2:
        t[1, 1, :3] = lighten(b, 0.6)


def m_glowcore(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / max(1.0, max(cx, cy))
            t[y, x, :3] = mix(lighten(b, 0.7), shade(b, 0.7), min(1.0, d))


def m_eye(t, pe, face, rng, cube):
    fill(t, pe['base'])


def m_gold(t, pe, face, rng, cube):
    m_metal(t, dict(pe, rivets=False), face, rng, cube)


def m_rope(t, pe, face, rng, cube):
    h, w = t.shape[:2]
    b = pe['base']
    for y in range(h):
        for x in range(w):
            t[y, x, :3] = shade(b, 1.12 if (x + y) % 3 == 0 else (0.8 if (x + y) % 3 == 2 else 1.0))


MATERIALS = {
    'plain': m_plain, 'skin': m_skin, 'cloth': m_cloth, 'leather': m_leather, 'metal': m_metal, 'fur': m_fur,
    'scale': m_scale, 'bone': m_bone, 'wood': m_wood, 'bark': m_bark, 'leaf': m_leaf, 'stone': m_stone,
    'sandstone': m_sandstone, 'ice': m_ice, 'crystal': m_crystal, 'magma': m_magma, 'flame': m_flame,
    'ghost': m_ghost, 'feather': m_feather, 'membrane': m_membrane, 'chain': m_chain, 'bandage': m_bandage,
    'cloud': m_cloud, 'void': m_void, 'chitin': m_chitin, 'hair': m_hair, 'mech': m_mech, 'gem': m_gem,
    'glowcore': m_glowcore, 'eye': m_eye, 'gold': m_gold, 'rope': m_rope,
}


# ---------------------------------------------------------------------------------------- texture assembly

class Painter:
    """Paints one variant of a rig; exposes faces of parts for decals."""

    def __init__(self, rig, variant, visible):
        self.rig = rig
        self.variant = variant
        self.k = getattr(rig, 'texel', 1)
        self.img = np.zeros((rig.tex_h * self.k, rig.tex_w * self.k, 4), dtype=np.uint8)
        self.glow = np.zeros((rig.tex_h * self.k, rig.tex_w * self.k, 4), dtype=np.uint8)
        self.visible = visible

    def paint_all(self):
        pal = self.variant.palette
        for c in self.rig.cubes:
            if c.share is not None:
                continue
            if not self.visible(c.part) and not any(self.visible(o.part) for o in self.rig.cubes if o.share == c.tag and c.tag):
                continue
            style, _, key = c.mat.partition(':')
            key = key or style
            pe = palette_entry(pal, key) if key in pal else palette_entry(pal, 'default') if 'default' in pal else {'base': (200, 0, 200), 'dark': (100, 0, 100), 'light': (255, 100, 255), 'accent': (255, 100, 255)}
            st = pe.get('style', style)
            for fname, rect in c.faces().items():
                rng = np.random.default_rng(seed_of(self.rig.name, self.variant.id, c.part.name, c.x, c.y, c.z, fname))
                paint_face(self.img, rect, st, pe, fname, rng, c, self.k)

    def cube(self, part, index=0, tag=None):
        p = self.rig.parts[part]
        if tag is not None:
            for c in p.cubes:
                if c.tag == tag:
                    return c
            raise KeyError(tag)
        return p.cubes[index]

    def face(self, part, face, index=0, tag=None, glow=False):
        c = self.cube(part, index, tag)
        return FaceCanvas(self.glow if glow else self.img, c.faces()[face], face, self.k)

    def all_faces(self, part, index=0, tag=None, glow=False, sides_only=False):
        c = self.cube(part, index, tag)
        names = ('front', 'right', 'back', 'left') if sides_only else ('front', 'right', 'back', 'left', 'top', 'bottom')
        return [FaceCanvas(self.glow if glow else self.img, c.faces()[n], n, self.k) for n in names]

    def has_glow(self):
        return bool(self.glow[..., :3].any())


# ------------------------------------------------------------------------------------------------ decals

def eyes(fc, y, color, spacing=None, width=2, pupil=None, white=None, x_center=None, glow_fc=None, glow_color=None):
    """Two eyes centered on the face. Returns the eye pixel coordinates."""
    w = fc.w
    cx = fc.w / 2.0 if x_center is None else x_center
    sp = spacing if spacing is not None else max(1, w // 4)
    pts = []
    for side in (-1, 1):
        x0 = int(round(cx + side * sp - (width if side < 0 else 0)))
        for k in range(width):
            x = x0 + k
            c = color
            if white is not None:
                c = white
            if pupil is not None and ((side < 0 and k == width - 1) or (side > 0 and k == 0)):
                c = pupil
            fc.px(x, y, c)
            pts.append((x, y))
            if glow_fc is not None:
                glow_fc.px(x, y, glow_color or color)
    return pts


def human_face(fc, skin, eye_col=(40, 30, 30), mouth=True, brows=None, beard=None, nose=True, white=(235, 235, 230),
               eye_row=None, glow_fc=None, glow_eyes=None):
    w, h = fc.w, fc.h
    ey = eye_row if eye_row is not None else h // 2 - (0 if h <= 8 else 1)
    cx = w // 2
    sp = max(1, w // 4)
    for side in (-1, 1):
        xa = cx - sp - 1 if side < 0 else cx + sp - 1
        fc.px(xa, ey, white)
        fc.px(xa + 1, ey, eye_col)
        if side > 0:
            fc.px(xa, ey, eye_col)
            fc.px(xa + 1, ey, white)
        if glow_fc is not None and glow_eyes is not None:
            glow_fc.px(xa, ey, glow_eyes)
            glow_fc.px(xa + 1, ey, glow_eyes)
        if brows is not None:
            fc.px(xa, ey - 1, brows)
            fc.px(xa + 1, ey - 1, brows)
    if nose:
        fc.px(cx - 1, ey + 1, shade(skin, 0.85))
        fc.px(cx, ey + 1, shade(skin, 0.85))
        fc.px(cx - 1, ey + 2, shade(skin, 0.75))
        fc.px(cx, ey + 2, shade(skin, 0.75))
    if mouth:
        my = min(h - 2, ey + 3)
        fc.hline(cx - 2, cx + 1, my, shade(skin, 0.55))
    if beard is not None:
        for y in range(min(h - 1, ey + 2), h):
            for x in range(1, w - 1):
                if y > ey + 2 or x < cx - 2 or x > cx + 1:
                    fc.px(x, y, shade(beard, 0.9 + 0.2 * ((x + y) % 2)))
        fc.hline(cx - 2, cx + 1, min(h - 2, ey + 3), shade(beard, 0.5))
