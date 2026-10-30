"""Small pixel-art helpers for 16x16 Minecraft textures (PIL)."""
import math
import random
from PIL import Image, ImageDraw


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + ((int(a[3] + (b[3] - a[3]) * t) if len(a) > 3 and len(b) > 3 else 255),)


class Tex:
    def __init__(self, w=16, h=16, color=(0, 0, 0, 0), seed=1):
        if isinstance(color, str):
            color = hexc(color)
        self.im = Image.new('RGBA', (w, h), color)
        self.w, self.h = w, h
        self.px = self.im.load()
        self.r = random.Random(seed)

    def get(self, x, y):
        return self.px[x % self.w, y % self.h]

    def set(self, x, y, c):
        if isinstance(c, str):
            c = hexc(c)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        """inclusive-exclusive rectangle"""
        if isinstance(c, str):
            c = hexc(c)
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px[x, y] = c

    def frame(self, x0, y0, x1, y1, c):
        self.rect(x0, y0, x1, y0 + 1, c)
        self.rect(x0, y1 - 1, x1, y1, c)
        self.rect(x0, y0, x0 + 1, y1, c)
        self.rect(x1 - 1, y0, x1, y1, c)

    def hline(self, y, x0, x1, c):
        self.rect(x0, y, x1, y + 1, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x + 1, y1, c)

    def noise(self, amt=0.08, x0=0, y0=0, x1=None, y1=None, mask_alpha=True):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = self.px[x, y]
                if mask_alpha and c[3] == 0:
                    continue
                self.px[x, y] = mul(c, 1 + (self.r.random() * 2 - 1) * amt)

    def speckle(self, n, colors, x0=0, y0=0, x1=None, y1=None):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        for _ in range(n):
            c = self.r.choice(colors)
            self.set(self.r.randrange(x0, x1), self.r.randrange(y0, y1), c)

    def fill(self, c):
        self.rect(0, 0, self.w, self.h, c)

    def map(self, fn):
        for y in range(self.h):
            for x in range(self.w):
                self.px[x, y] = fn(x, y, self.px[x, y])

    def paste(self, other, x, y):
        self.im.alpha_composite(other.im, (x, y))
        self.px = self.im.load()

    def save(self, path):
        self.im.save(path)

    def draw(self):
        return ImageDraw.Draw(self.im)


# ---------------------------------------------------------------- patterns

def solid(c, amt=0.05, seed=1):
    t = Tex(color=c, seed=seed)
    t.noise(amt)
    return t


def planks(base, seed=1, vertical=False, board=4, grain=0.10, seam=0.72, w=16, h=16):
    t = Tex(w, h, base, seed)
    b = hexc(base) if isinstance(base, str) else base
    n = (w if vertical else h) // board
    for i in range(n):
        f = 1 + (t.r.random() - 0.5) * 0.16
        for a in range(board):
            for s in range(w if not vertical else h):
                x, y = (i * board + a, s) if vertical else (s, i * board + a)
                g = 1 + math.sin((s * 0.9 + t.r.random() * 0.8 + i * 3)) * grain * 0.5 + (t.r.random() - 0.5) * grain
                c = mul(b, f * g)
                if a == board - 1:
                    c = mul(c, seam)
                t.set(x, y, c)
        # board ends
        cut = t.r.randrange(2, (h if vertical else w) - 2)
        for a in range(board):
            x, y = (i * board + a, cut) if vertical else (cut, i * board + a)
            if i % 2 == 0:
                t.set(x, y, mul(b, seam * 0.95))
    return t


def bricks(base, mortar, seed=1, bw=8, bh=4, w=16, h=16, var=0.12, offset=None):
    t = Tex(w, h, mortar, seed)
    b = hexc(base) if isinstance(base, str) else base
    offset = bw // 2 if offset is None else offset
    rows = h // bh
    for r in range(rows):
        off = (r % 2) * offset
        for cx in range(-1, w // bw + 1):
            f = 1 + (t.r.random() - 0.5) * var * 2
            x0 = cx * bw + off
            for y in range(r * bh, r * bh + bh - 1):
                for x in range(x0, x0 + bw - 1):
                    if 0 <= x < w:
                        t.set(x, y, mul(b, f * (1 + (t.r.random() - 0.5) * 0.08)))
    return t


def tiles(base, grout, size=4, seed=1, var=0.06, w=16, h=16):
    t = Tex(w, h, grout, seed)
    b = hexc(base) if isinstance(base, str) else base
    for ty in range(0, h, size):
        for tx in range(0, w, size):
            f = 1 + (t.r.random() - 0.5) * var * 2
            t.rect(tx, ty, tx + size - 1, ty + size - 1, mul(b, f))
    t.noise(0.03)
    return t


def stones(base, mortar, seed=1, n=7, w=16, h=16, var=0.18):
    """irregular stone wall (voronoi cells with mortar borders)"""
    r = random.Random(seed)
    pts = [(r.uniform(0, w), r.uniform(0, h), 1 + (r.random() - 0.5) * var * 2) for _ in range(n)]
    b = hexc(base) if isinstance(base, str) else base
    m = hexc(mortar) if isinstance(mortar, str) else mortar
    t = Tex(w, h, base, seed)

    def near(x, y):
        best = []
        for i, (px, py, f) in enumerate(pts):
            for ox in (-w, 0, w):
                for oy in (-h, 0, h):
                    d = (x - px - ox) ** 2 + (y - py - oy) ** 2
                    best.append((d, i))
        best.sort()
        return best[0], best[1]

    for y in range(h):
        for x in range(w):
            (d0, i0), (d1, i1) = near(x + 0.5, y + 0.5)
            if math.sqrt(d1) - math.sqrt(d0) < 0.9:
                t.set(x, y, mul(m, 1 + (r.random() - 0.5) * 0.1))
            else:
                shade = 1.0 + (0.12 if math.sqrt(d0) < 1.5 else 0) - 0.04 * math.sqrt(d0) / 3
                t.set(x, y, mul(b, pts[i0][2] * shade * (1 + (r.random() - 0.5) * 0.1)))
    return t


def gravel(colors, seed=1, w=16, h=16):
    t = Tex(w, h, colors[0], seed)
    for y in range(h):
        for x in range(w):
            t.set(x, y, mul(hexc(t.r.choice(colors)), 1 + (t.r.random() - 0.5) * 0.25))
    # pebble highlights
    for _ in range(14):
        x, y = t.r.randrange(w), t.r.randrange(h)
        c = hexc(t.r.choice(colors))
        t.set(x, y, mul(c, 1.25))
        t.set(x + 1, y, mul(c, 1.1))
        t.set(x, y + 1, mul(c, 0.75))
    return t


def window_grid(frame, glass, lit=None, seed=1, cols=2, rows=2, w=16, h=16, mullion=1):
    t = Tex(w, h, frame, seed)
    cw, ch = w // cols, h // rows
    r = random.Random(seed)
    for j in range(rows):
        for i in range(cols):
            x0, y0 = i * cw + mullion, j * ch + mullion
            x1, y1 = (i + 1) * cw - mullion, (j + 1) * ch - mullion
            g = glass
            if lit:
                g = r.choice(lit) if isinstance(lit, list) else lit
            gc = hexc(g) if isinstance(g, str) else g
            for y in range(y0, y1):
                for x in range(x0, x1):
                    f = 1 + 0.12 * (1 - (y - y0) / max(1, (y1 - y0))) + (r.random() - 0.5) * 0.04
                    t.set(x, y, mul(gc, f))
            # reflection streak
            if not lit:
                for k in range(min(x1 - x0, y1 - y0) - 1):
                    t.set(x0 + k + 1, y1 - 2 - k, mul(gc, 1.3))
    return t


def stripes(c1, c2, width=2, seed=1, vertical=True, w=16, h=16):
    t = Tex(w, h, c1, seed)
    for y in range(h):
        for x in range(w):
            k = (x if vertical else y) // width
            if k % 2:
                t.set(x, y, c2)
    t.noise(0.03)
    return t


def round_mask(t, cx=7.5, cy=7.5, r=7.5):
    for y in range(t.h):
        for x in range(t.w):
            if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
                t.set(x, y, (0, 0, 0, 0))
    return t


def outline(t, color, alpha_threshold=1):
    """add a 1px outline around opaque pixels (item sprites)"""
    c = hexc(color) if isinstance(color, str) else color
    src = t.im.copy()
    sp = src.load()
    for y in range(t.h):
        for x in range(t.w):
            if sp[x, y][3] >= alpha_threshold:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < t.w and 0 <= yy < t.h and sp[xx, yy][3] >= 200:
                    t.set(x, y, c)
                    break
    return t


def ellipse(t, x0, y0, x1, y1, c, fill=True):
    d = t.draw()
    if isinstance(c, str):
        c = hexc(c)
    if fill:
        d.ellipse([x0, y0, x1, y1], fill=c)
    else:
        d.ellipse([x0, y0, x1, y1], outline=c)
    t.px = t.im.load()


def poly(t, pts, c):
    d = t.draw()
    if isinstance(c, str):
        c = hexc(c)
    d.polygon(pts, fill=c)
    t.px = t.im.load()


def shade_sphere(t, cx, cy, r, light=(-0.6, -0.6)):
    """add simple lighting to opaque pixels of a roughly round sprite"""
    for y in range(t.h):
        for x in range(t.w):
            c = t.px[x, y]
            if c[3] == 0:
                continue
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            l = -(dx * light[0] + dy * light[1]) * 0.25
            t.px[x, y] = mul(c, 1 + l)
