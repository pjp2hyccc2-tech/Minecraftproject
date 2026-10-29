"""Tiny pixel-art toolkit for 16x16 item icons (and other small sprites).

Shapes are built as high-resolution masks (vector polygons, ellipses, tapered strokes), shaded with a
height field lit from the top-left, quantised to a 5-level ramp (outline, shadow, base, light, highlight)
and outlined selectively (dark outline against transparency, soft shadow line against lower layers).
"""
import colorsys
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')))

SS = 8  # supersampling per pixel
HD = 2  # HD output scale: the icon is also painted at n*HD texels (continuous shading, rounded outlines and
        # pixel ops replayed with anti-aliased shapes); 1 = legacy pixel art only


def hx(c):
    if isinstance(c, (tuple, list, np.ndarray)):
        return np.array(c[:3], float)
    c = c.lstrip('#')
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float)


def to_hex(rgb):
    return '#%02x%02x%02x' % tuple(int(max(0, min(255, round(v)))) for v in rgb)


def ramp(base, sat=1.0, light=1.0, shift=1.0, out=None):
    """5-level ramp from a base colour: outline, shadow, base, light, highlight (hue-shifted)."""
    r, g, b = hx(base) / 255.0
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    s = min(1.0, s * sat)
    l = min(0.95, l * light)
    def mk(dl, dh, ds):
        hh = (h + dh * shift) % 1.0
        ll = max(0.02, min(0.97, l + dl))
        ss = max(0.0, min(1.0, s * ds))
        return to_hex(np.array(colorsys.hls_to_rgb(hh, ll, ss)) * 255)
    warm = 0.03 if (h < 0.17 or h > 0.85) else -0.03
    lv = [mk(-l * 0.78, -warm * 1.2, 1.1), mk(-l * 0.38, -warm * 0.6, 1.05), mk(0, 0, 1.0),
          mk((1 - l) * 0.32, warm * 0.6, 0.95), mk((1 - l) * 0.62, warm * 1.0, 0.8)]
    if out:
        lv[0] = out
    return lv


def mix(a, b, t):
    return to_hex(hx(a) * (1 - t) + hx(b) * t)


def lighten_ramp(rp, k=0.25, sat=1.1):
    return [to_hex(np.clip(hx(c) + (255 - hx(c)) * k * (i / 4.0 + 0.3), 0, 255)) for i, c in enumerate(rp)]


class Canvas:
    def __init__(self, n=16):
        self.n = n
        self.H = n * SS
        self.img = np.zeros((n, n, 4), float)
        self.owner = np.full((n, n), -1, int)
        self.nlayers = 0
        yy, xx = np.mgrid[0:self.H, 0:self.H]
        self.X = (xx + 0.5) / SS
        self.Y = (yy + 0.5) / SS
        # HD twin of img: layers are painted in high detail, every other pixel edit is replayed (see image())
        self.hd = np.zeros((n * HD, n * HD, 4), float) if HD > 1 else None
        self.owner_hd = np.full((n * HD, n * HD), -1, int)
        self._shadow = self.img.copy()

    def _flush(self):
        """Replays the pixel-level edits made on img since the last layer onto the HD twin."""
        if self.hd is None or np.array_equal(self.img, self._shadow):
            return
        from hcmodel.hdup import apply_changes
        z = np.zeros_like(self.img)
        apply_changes(self.hd, None, self._shadow, self.img, z, z, HD)
        self._shadow = self.img.copy()

    # ------------------------------------------------------------------ hi-res masks
    def poly(self, pts):
        from matplotlib.path import Path
        path = Path(np.array(pts, float))
        inside = path.contains_points(np.stack([self.X.ravel(), self.Y.ravel()], -1))
        return inside.reshape(self.H, self.H)

    def ellipse(self, cx, cy, rx, ry, ang=0.0):
        a = math.radians(ang)
        dx, dy = self.X - cx, self.Y - cy
        u = dx * math.cos(a) + dy * math.sin(a)
        v = -dx * math.sin(a) + dy * math.cos(a)
        return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0

    def circle(self, cx, cy, r):
        return self.ellipse(cx, cy, r, r)

    def rect(self, x0, y0, x1, y1, rad=0.0):
        if rad <= 0:
            return (self.X >= x0) & (self.X <= x1) & (self.Y >= y0) & (self.Y <= y1)
        cx = np.clip(self.X, x0 + rad, x1 - rad)
        cy = np.clip(self.Y, y0 + rad, y1 - rad)
        return ((self.X - cx) ** 2 + (self.Y - cy) ** 2 <= rad * rad) & (self.X >= x0) & (self.X <= x1) & (self.Y >= y0) & (self.Y <= y1)

    def stroke(self, pts, w0, w1=None, cap=True):
        """Tapered thick polyline (width w0 at the start to w1 at the end)."""
        w1 = w0 if w1 is None else w1
        pts = np.array(pts, float)
        segs = len(pts) - 1
        lens = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        total = lens.sum() + 1e-9
        m = np.zeros((self.H, self.H), bool)
        acc = 0.0
        for k in range(segs):
            a, b = pts[k], pts[k + 1]
            ab = b - a
            L2 = max(1e-9, ab @ ab)
            t = np.clip(((self.X - a[0]) * ab[0] + (self.Y - a[1]) * ab[1]) / L2, 0, 1)
            px = a[0] + t * ab[0]
            py = a[1] + t * ab[1]
            d = np.sqrt((self.X - px) ** 2 + (self.Y - py) ** 2)
            frac = (acc + t * lens[k]) / total
            wid = (w0 + (w1 - w0) * frac) / 2.0
            m |= d <= wid
            acc += lens[k]
        return m

    def curve(self, p0, p1, p2, w0, w1=None, steps=16):
        """Quadratic bezier stroke."""
        ts = np.linspace(0, 1, steps)
        pts = [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in ts]
        return self.stroke(pts, w0, w1)

    # ------------------------------------------------------------------ layers
    def cells(self, hm, thresh=0.5):
        cov = hm.reshape(self.n, SS, self.n, SS).mean(axis=(1, 3))
        return cov >= thresh

    def layer(self, hm, rp, shade='dome', light=(-0.7, -0.75), outline='auto', amb=0.0, bulge=1.0, thresh=0.5,
              mask=None, levels=None, glow=None, flat=0.0, hl=True, contrast=1.0):
        """Shades a hi-res mask with ramp rp (5 colours) and composites it. Returns the cell mask."""
        n = self.n
        M = self.cells(hm, thresh)
        if mask is not None:
            M &= mask
        if not M.any():
            return M
        f = hm.astype(float)
        if shade == 'dome':
            hgt = ndimage.distance_transform_edt(hm) / SS
            hgt = np.sqrt(np.clip(hgt / max(1e-6, hgt.max()), 0, 1)) * bulge
            hgt = ndimage.gaussian_filter(hgt, SS * 0.6)
        elif shade == 'flat':
            hgt = np.clip(ndimage.distance_transform_edt(hm) / (SS * 1.2), 0, 1) * bulge
        elif shade == 'cyl':
            hgt = ndimage.distance_transform_edt(hm) / SS
            hgt = np.clip(hgt / 2.2, 0, 1) ** 0.6 * bulge
            hgt = ndimage.gaussian_filter(hgt, SS * 0.4)
        else:
            hgt = f * 0
        gy, gx = np.gradient(hgt * SS)
        nz = np.full_like(gx, 1.0)
        nx, ny = -gx, -gy
        nl = np.sqrt(nx * nx + ny * ny + nz * nz)
        L = np.array([light[0], light[1], 0.75])
        L = L / np.linalg.norm(L)
        b = (nx * L[0] + ny * L[1] + nz * L[2]) / nl
        b = np.where(hm, b, np.nan)
        with np.errstate(invalid='ignore'):
            bc = np.nanmean(b.reshape(n, SS, n, SS).transpose(0, 2, 1, 3).reshape(n, n, SS * SS), axis=2)
        bc = np.nan_to_num(bc, nan=0.6)
        lo, hi = np.nanpercentile(bc[M], 5), np.nanpercentile(bc[M], 98)
        t = (bc - lo) / max(1e-6, hi - lo)
        t = np.clip((t - 0.5) * contrast + 0.5 + amb, 0, 1)
        t = t * (1 - flat) + 0.55 * flat
        lev = np.clip(np.round(1 + t * 2.95), 1, 4).astype(int)
        if not hl:
            lev = np.minimum(lev, 3)
        if levels is not None:
            lev = np.clip(lev, levels[0], levels[1])
        # outline
        if outline:
            pad = np.pad(M, 1, constant_values=False)
            nb = [pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]]
            edge = M & ~(nb[0] & nb[1] & nb[2] & nb[3])
            ownpad = np.pad(self.owner, 1, constant_values=-1)
            onb = [ownpad[:-2, 1:-1], ownpad[2:, 1:-1], ownpad[1:-1, :-2], ownpad[1:-1, 2:]]
            empty = np.zeros_like(M)
            for k in range(4):
                empty |= (~nb[k]) & (onb[k] == -1)
            if outline == 'auto':
                lev = np.where(edge & empty, 0, lev)
                lev = np.where(edge & ~empty, np.minimum(lev, 1), lev)
            elif outline == 'full':
                lev = np.where(edge, 0, lev)
            elif outline == 'soft':
                lev = np.where(edge & empty, 1, lev)
        cols = np.stack([hx(c) for c in rp])
        if self.hd is not None:
            self._flush()
            self._layer_hd(hm, b, M, lev, lo, hi, amb, flat, hl, levels, outline, contrast, cols)
        self.img[M, :3] = cols[lev[M]]
        self.img[M, 3] = 255
        self.owner[M] = self.nlayers
        self.nlayers += 1
        self.last_lev = lev
        if self.hd is not None:
            self._shadow = self.img.copy()
        return M

    def _layer_hd(self, hm, b, M, lev, lo, hi, amb, flat, hl, levels, outline, contrast, cols):
        """HD version of a layer: the same shading evaluated per HD cell and looked up continuously along the
        ramp (levels 1..4), the outline / shadow line ~1 design px wide with rounded corners."""
        n, K = self.n, HD
        k = SS // K
        Mh = np.kron(M, np.ones((K, K), bool)) & (hm.reshape(n * K, k, n * K, k).mean(axis=(1, 3)) >= 0.35)
        if not Mh.any():
            return
        with np.errstate(invalid='ignore'):
            bh = np.nanmean(b.reshape(n * K, k, n * K, k).transpose(0, 2, 1, 3).reshape(n * K, n * K, k * k), axis=2)
        bh = np.nan_to_num(bh, nan=0.6)
        t = (bh - lo) / max(1e-6, hi - lo)
        t = np.clip((t - 0.5) * contrast + 0.5 + amb, 0, 1)
        t = t * (1 - flat) + 0.55 * flat
        v = np.clip(t * 2.95, 0, 3.0)                       # continuous level 1..4
        if not hl:
            v = np.minimum(v, 2.0)
        if levels is not None:
            v = np.clip(v, levels[0] - 1, levels[1] - 1)
        i0 = np.clip(np.floor(v).astype(int), 0, 2)
        f = (v - i0)[..., None]
        rgb = cols[1 + i0] * (1 - f) + cols[2 + i0] * f
        if outline:
            r = K
            yy, xx = np.mgrid[-r:r + 1, -r:r + 1]
            disc = (xx * xx + yy * yy) <= r * r + 0.5
            inner = ndimage.binary_erosion(Mh, structure=disc, border_value=0)
            edge = Mh & ~inner
            empty = ndimage.binary_dilation(~Mh & (self.owner_hd < 0), structure=disc) & Mh
            if outline == 'auto':
                rgb = np.where((edge & empty)[..., None], cols[0], rgb)
                rgb = np.where((edge & ~empty)[..., None], np.minimum(rgb, cols[1]), rgb)
            elif outline == 'full':
                rgb = np.where(edge[..., None], cols[0], rgb)
            elif outline == 'soft':
                rgb = np.where((edge & empty)[..., None], cols[1], rgb)
        self.hd[Mh, :3] = rgb[Mh]
        self.hd[Mh, 3] = 255
        self.owner_hd[Mh] = self.nlayers

    # ------------------------------------------------------------------ pixel ops
    def px(self, x, y, col, a=255):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.img[y, x, :3] = hx(col)
            self.img[y, x, 3] = a
            if self.owner[y, x] < 0:
                self.owner[y, x] = self.nlayers
            return True
        return False

    def pxs(self, pts, col):
        for x, y in pts:
            self.px(x, y, col)

    def opaque(self, x, y):
        return 0 <= x < self.n and 0 <= y < self.n and self.img[y, x, 3] > 0

    def px_on(self, x, y, col):
        """Only paints if the pixel is already opaque."""
        if self.opaque(x, y):
            self.px(x, y, col)

    def line(self, x0, y0, x1, y1, col, only_on=False):
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            (self.px_on if only_on else self.px)(x0, y0, col)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def get(self, x, y):
        return self.img[y, x].copy()

    def recolor(self, mask, col):
        sel = mask & (self.img[..., 3] > 0)
        self.img[sel, :3] = hx(col)

    def shift_level(self, mask, rp, delta):
        """Moves pixels of a ramp-coloured region by delta levels (matching nearest ramp colour)."""
        cols = np.stack([hx(c) for c in rp])
        for y, x in zip(*np.nonzero(mask & (self.img[..., 3] > 0))):
            d = ((cols - self.img[y, x, :3]) ** 2).sum(1)
            k = int(np.argmin(d))
            self.img[y, x, :3] = cols[int(np.clip(k + delta, 0, 4))]

    def sparkle(self, x, y, col='#ffffff', arm='#fff6c0', size=1):
        self.px(x, y, col)
        for k in range(1, size + 1):
            for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                self.px(x + dx, y + dy, arm)

    def outline_all(self, col, only_empty=True):
        """Outer 1px outline around everything opaque (drawn outside the silhouette)."""
        a = self.img[..., 3] > 0
        pad = np.pad(a, 1)
        ring = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~a
        self.img[ring, :3] = hx(col)
        self.img[ring, 3] = 255

    def drop_shadow(self, col='#00000000'):
        pass

    def image(self, hd=None):
        """The icon: HD (n*HD texels) when HD painting is on, else the 1x pixel art."""
        if self.hd is not None and hd is not False:
            self._flush()
            out = self.hd.copy()
            out[out[..., 3] < 128] = 0
            out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
            return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')
        return Image.fromarray(np.clip(self.img, 0, 255).astype(np.uint8), 'RGBA')

    def save(self, path):
        self.image().save(path)


def sheet(images, labels, out, scale=6, cols=10, bg=(58, 62, 70)):
    """Contact sheet of icons (scaled with nearest neighbour, labelled)."""
    from PIL import ImageDraw
    n = images[0].size[0]
    cell = n * scale + 4
    rows = (len(images) + cols - 1) // cols
    W, H = cols * cell, rows * (cell + 12)
    im = Image.new('RGB', (W, H), bg)
    d = ImageDraw.Draw(im)
    for k, (img, lab) in enumerate(zip(images, labels)):
        x, y = (k % cols) * cell, (k // cols) * (cell + 12)
        checker = Image.new('RGB', (n * scale, n * scale), (78, 82, 92))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, n * scale, scale * 2):
            for cx in range(0, n * scale, scale * 2):
                cd.rectangle([cx, cy, cx + scale - 1, cy + scale - 1], fill=(70, 74, 84))
                cd.rectangle([cx + scale, cy + scale, cx + 2 * scale - 1, cy + 2 * scale - 1], fill=(70, 74, 84))
        big = img.resize((n * scale, n * scale), Image.NEAREST)
        checker.paste(big, (0, 0), big)
        im.paste(checker, (x + 2, y + 2))
        d.text((x + 2, y + cell - 1), lab[:16], fill=(230, 230, 230))
    im.save(out)
