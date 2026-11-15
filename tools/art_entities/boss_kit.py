"""Shared helpers of the dedicated dungeon-boss rigs (b1..b8, final; 2026-11 remake from the concept sheets).

Painting: decals drawn in fine pixels (the boss textures are painted at 2 pixels per model unit) on top of the
procedural materials of erpaint: ragged cloth edges, colour ramps, glowing glyphs and runes, faceted gems, rivets,
chain links, cracks. Every helper is deterministic (no randomness beyond seeded hashes).

Motion: small pieces shared by the clips of the rigs (planted stances, eased scalar keys, trembles), on top of eranim.
"""
import math

from erpaint import clamp, lighten, mix, shade


def h(*keys):
    """Deterministic 0..1 hash of the keys (for ragged edges and scattered details)."""
    v = 2166136261
    for k in keys:
        for ch in str(k):
            v = ((v ^ ord(ch)) * 16777619) & 0xffffffff
        v = ((v ^ 0x9e) * 16777619) & 0xffffffff
    return (v % 10007) / 10007.0


# ------------------------------------------------------------------------------------------------- pixels

def line(fc, x0, y0, x1, y1, color, gfc=None, glow=None):
    """A line in fine pixels (Bresenham), on the body and (optionally) the glow layer."""
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        fc.fpx(x0, y0, color)
        if gfc is not None:
            gfc.fpx(x0, y0, glow or color)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def frect(fc, x0, y0, x1, y1, color, gfc=None, glow=None):
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            fc.fpx(x, y, color)
            if gfc is not None:
                gfc.fpx(x, y, glow or color)


def ramp(fc, top, bottom, keep=0.55, y0=None, y1=None):
    """Recolours a face into a vertical two-colour ramp, keeping {@code keep} of the painted light and shade."""
    y0 = 0 if y0 is None else y0
    y1 = fc.fh - 1 if y1 is None else y1
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        col = mix(top, bottom, t)
        for x in range(fc.fw):
            c = fc.fget(x, y)
            lum = (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 140.0
            f = 1.0 + (lum - 1.0) * keep
            fc.fpx(x, y, tuple(clamp(col[i] * f) for i in range(3)))


def ragged(fc, depth, seed, side='bottom', every=1, min_depth=0):
    """Tears the edge of a cloth face: runs of transparent pixels of varying depth (fine pixels), with a darker
    frayed rim left above every tear."""
    n = fc.fw if side in ('bottom', 'top') else fc.fh
    run = 0
    d = 0
    for i in range(n):
        if run <= 0:
            run = 1 + int(h(seed, i, 'run') * 3) * max(1, every)
            d = min_depth + int(h(seed, i, 'depth') ** 1.4 * (depth - min_depth + 1))
        run -= 1
        for k in range(d):
            if side == 'bottom':
                fc.fclear(i, fc.fh - 1 - k)
            elif side == 'top':
                fc.fclear(i, k)
            elif side == 'left':
                fc.fclear(k, i)
            else:
                fc.fclear(fc.fw - 1 - k, i)
        rim = (i, fc.fh - 1 - d) if side == 'bottom' else (i, d) if side == 'top' else (d, i) if side == 'left' \
            else (fc.fw - 1 - d, i)
        if 0 <= rim[0] < fc.fw and 0 <= rim[1] < fc.fh:
            fc.fpx(rim[0], rim[1], shade(fc.fget(rim[0], rim[1]), 0.72))


def trim(fc, color, rows=(0,), cols=(), shine=True):
    """Metal trim along rows / columns (fine pixels; negative indices count from the far edge)."""
    for r in rows:
        y = r if r >= 0 else fc.fh + r
        for x in range(fc.fw):
            fc.fpx(x, y, lighten(color, 0.25) if shine and (x + y) % 5 == 0 else color)
    for cidx in cols:
        x = cidx if cidx >= 0 else fc.fw + cidx
        for y in range(fc.fh):
            fc.fpx(x, y, lighten(color, 0.25) if shine and (x + y) % 5 == 0 else color)


def rivets(fc, color, step, inset=2, rows=None):
    rows = rows if rows is not None else (inset, fc.fh - 1 - inset)
    for y in rows:
        for x in range(inset, fc.fw - inset, step):
            fc.fpx(x, y, lighten(color, 0.55))
            fc.fpx(x + 1, y + 1, shade(color, 0.55))


def gem(fc, x0, y0, w, hh, base, gfc=None, glow=None):
    """A faceted gem (fine pixels): light upper-left facet, dark lower-right, a white glint."""
    for y in range(hh):
        for x in range(w):
            u = (x + 0.5) / w + (y + 0.5) / hh
            col = lighten(base, 0.35) if u < 0.8 else (base if u < 1.25 else shade(base, 0.62))
            if x == 0 or y == 0 or x == w - 1 or y == hh - 1:
                col = shade(col, 0.82)
            fc.fpx(x0 + x, y0 + y, col)
            if gfc is not None:
                gfc.fpx(x0 + x, y0 + y, glow or lighten(base, 0.3))
    fc.fpx(x0 + max(0, w // 3), y0 + max(0, hh // 3), (255, 255, 255))


def diamond(fc, cx, cy, r, base, gfc=None, glow=None):
    """A lozenge gem centred on (cx, cy) with radius r (fine pixels)."""
    for y in range(-r, r + 1):
        for x in range(-r, r + 1):
            if abs(x) + abs(y) <= r:
                u = (x + y) / max(1, r)
                col = lighten(base, 0.4) if u < -0.4 else (base if u < 0.5 else shade(base, 0.65))
                if abs(x) + abs(y) == r:
                    col = shade(col, 0.8)
                fc.fpx(cx + x, cy + y, col)
                if gfc is not None:
                    gfc.fpx(cx + x, cy + y, glow or lighten(base, 0.35))
    fc.fpx(cx - max(0, r // 3), cy - max(0, r // 3), (255, 255, 255))


def square_spiral(fc, x0, y0, size, color, gfc=None, glow=None, dark=None, scale=1):
    """A carved square spiral glyph of {@code size} cells (the sand titan's runes), each cell {@code scale} x
    {@code scale} fine pixels: the groove is dark, its bottom glows."""
    if scale > 1:
        out = []

        class _Cells:
            def __init__(self, base):
                self.base, self.fw, self.fh = base, base.fw // scale, base.fh // scale

            def fget(self, x, y):
                return self.base.fget(min(self.base.fw - 1, x * scale), min(self.base.fh - 1, y * scale))

            def fpx(self, x, y, c):
                for dy in range(scale):
                    for dx in range(scale):
                        self.base.fpx(x0 + (x - x0) * scale + dx, y0 + (y - y0) * scale + dy, c)
                out.append((x, y))
        cells = _Cells(fc)
        gcells = _Cells(gfc) if gfc is not None else None
        square_spiral(cells, x0, y0, size, color, gcells, glow, dark or shade(fc.fget(x0, y0), 0.55))
        return out
    dark = dark or shade(fc.fget(min(fc.fw - 1, x0), min(fc.fh - 1, y0)), 0.55)
    pts = []
    x, y = x0, y0
    n = size
    direction = 0
    steps = [(1, 0), (0, 1), (-1, 0), (0, -1)]
    length = n - 1
    first = True
    while length > 0:
        for _ in range(2 if not first else 3):
            dx, dy = steps[direction % 4]
            for _ in range(length):
                pts.append((x, y))
                x += dx
                y += dy
            direction += 1
            if not first:
                pass
        first = False
        length -= 2
    pts.append((x, y))
    for (px, py) in pts:
        fc.fpx(px, py, color)
        if gfc is not None:
            gfc.fpx(px, py, glow or color)
    # carved rim one pixel outside the outer square
    for i in range(-1, n + 1):
        for (px, py) in ((x0 + i, y0 - 1), (x0 + i, y0 + n), (x0 - 1, y0 + i), (x0 + n, y0 + i)):
            if 0 <= px < fc.fw and 0 <= py < fc.fh:
                fc.fpx(px, py, dark)
    return pts


def knot(fc, cx, cy, s, color, gfc=None, glow=None, bold=False):
    """An interlaced square knot rune (the tree guardian's runes): two offset hooked squares around a centre, an
    inner square at the heart; {@code bold} doubles the stroke."""
    pts = []
    a = s // 2
    # outer hook 1: from top-left going right, down, left
    pts += [(cx - a + i, cy - a) for i in range(s - 1)]
    pts += [(cx + a - 1, cy - a + i) for i in range(a + 1)]
    pts += [(cx + a - 1 - i, cy) for i in range(a)]
    # outer hook 2 (rotated 180 degrees)
    pts += [(cx + a - 1 - i, cy + a) for i in range(s - 1)]
    pts += [(cx - a + 1, cy + a - i) for i in range(a + 1)]
    pts += [(cx - a + 1 + i, cy) for i in range(a)]
    # short spurs that make it read as interlaced
    pts += [(cx - a, cy - a + i) for i in range(a // 2 + 1)]
    pts += [(cx + a, cy + a - i) for i in range(a // 2 + 1)]
    if s >= 11:                                   # inner square ring around the heart
        q = max(1, s // 6)
        for i in range(-q, q + 1):
            pts += [(cx + i, cy - q - 1), (cx + i, cy + q + 1), (cx - q - 1, cy + i), (cx + q + 1, cy + i)]
    if bold:
        pts = pts + [(x + 1, y) for (x, y) in pts] + [(x, y + 1) for (x, y) in pts]
    for (px, py) in pts:
        fc.fpx(px, py, color)
        if gfc is not None:
            gfc.fpx(px, py, glow or color)
    return pts


def chain_links(fc, x0, y0, x1, y1, color, step=3):
    """Chain links (alternating light / dark beads) along a line in fine pixels."""
    n = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) / step))
    for i in range(n + 1):
        t = i / n
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        c = lighten(color, 0.35) if i % 2 == 0 else shade(color, 0.7)
        fc.fpx(x, y, c)
        fc.fpx(x + 1, y, shade(c, 0.85))
        if i % 2 == 0:
            fc.fpx(x, y + 1, shade(color, 0.6))


def crackle(fc, seed, count, length, color, gfc=None, glow=None, branch=0.35):
    """Branching cracks (fine pixels) that wander downwards: lava seams, ice fractures, glowing cracks."""
    for k in range(count):
        x = int(h(seed, k, 'x') * fc.fw)
        y = int(h(seed, k, 'y') * fc.fh)
        stack = [(x, y, length)]
        while stack:
            x, y, n = stack.pop()
            for i in range(n):
                if 0 <= x < fc.fw and 0 <= y < fc.fh:
                    fc.fpx(x, y, color)
                    if gfc is not None:
                        gfc.fpx(x, y, glow or color)
                r = h(seed, k, x, y, i)
                x += -1 if r < 0.33 else (1 if r > 0.66 else 0)
                y += 1 if h(seed, k, i, 'dy') < 0.75 else 0
                if n > 3 and h(seed, k, i, 'br') < branch * 0.25:
                    stack.append((x, y, n // 2))


def speckle(fc, seed, density, colors):
    """Scattered single pixels (moss flecks, frost, sparkle)."""
    for y in range(fc.fh):
        for x in range(fc.fw):
            r = h(seed, x, y)
            if r < density:
                fc.fpx(x, y, colors[int(h(seed, y, x, 'c') * len(colors)) % len(colors)])


def bricks(fc, seed, rows, mortar, light, every=None):
    """Sandstone / stone brick courses in fine pixels: horizontal mortar every {@code rows} pixels, staggered joints,
    each brick slightly lit at its top edge."""
    every = every or rows * 2
    y = 0
    course = 0
    while y < fc.fh:
        off = (every // 2) * (course % 2) + int(h(seed, course) * 3)
        for x in range(fc.fw):
            fc.fpx(x, y, mortar)
            if y + 1 < fc.fh:
                fc.fpx(x, y + 1, mix(fc.fget(x, y + 1), light, 0.35))
        x = -off
        while x < fc.fw:
            for yy in range(y, min(fc.fh, y + rows)):
                if 0 <= x < fc.fw:
                    fc.fpx(x, yy, mortar)
            x += every + int(h(seed, course, x) * 3) - 1
        y += rows
        course += 1


def planks(fc, seed, width, dark, light):
    """Vertical bark planks with dark grooves and lit edges (the guardian's block body)."""
    x = int(h(seed, 'p0') * width)
    k = 0
    while x < fc.fw:
        for y in range(fc.fh):
            wob = 1 if h(seed, k, y // 3) > 0.8 else 0
            if 0 <= x + wob < fc.fw:
                fc.fpx(x + wob, y, dark)
            if 0 <= x + wob + 1 < fc.fw:
                fc.fpx(x + wob + 1, y, mix(fc.fget(x + wob + 1, y), light, 0.4))
        # a few cross cuts
        for _ in range(2):
            yy = int(h(seed, k, 'cut') * fc.fh)
            for xx in range(x, min(fc.fw, x + width)):
                fc.fpx(xx, yy, dark)
        x += width + int(h(seed, k) * 3)
        k += 1


def moss(fc, seed, depth, base, light, dark, side='top'):
    """Moss growing over the top of a face, hanging in drips (fine pixels)."""
    for x in range(fc.fw):
        d = int(depth * (0.45 + 0.55 * h(seed, x // 2))) + (3 if h(seed, x, 'drip') < 0.12 else 0)
        for y in range(min(fc.fh, d)):
            r = h(seed, x, y)
            c = light if r < 0.25 else (dark if r > 0.8 else base)
            fc.fpx(x, y if side == 'top' else fc.fh - 1 - y, c)


def frost(fc, seed, depth, white, blue):
    """Snow and rime on the top edge of a face with icicle drips."""
    for x in range(fc.fw):
        d = int(depth * (0.5 + 0.5 * h(seed, x // 2)))
        if h(seed, x, 'icicle') < 0.18:
            d += 2 + int(h(seed, x, 'len') * 5)
        for y in range(min(fc.fh, d)):
            c = white if y < depth * 0.6 else mix(white, blue, min(1.0, (y - depth * 0.6) / 5.0))
            fc.fpx(x, y, c)


# --------------------------------------------------------------------------------------------- face helpers

def faces(p, part, tag=None, index=0, glow=False, sides=True):
    """Body and glow canvases of every side face of a cube: [(name, face, glow face)]."""
    names = ('front', 'right', 'back', 'left') if sides else ('front', 'right', 'back', 'left', 'top', 'bottom')
    return [(n, p.face(part, n, index=index, tag=tag), p.face(part, n, index=index, tag=tag, glow=True))
            for n in names]


def each_face(p, part, fn, tag=None, index=0, sides=True):
    for name, fc, gfc in faces(p, part, tag, index, sides=sides):
        fn(name, fc, gfc)


# -------------------------------------------------------------------------------------------------- motion

def ev(keys, t):
    """Eased scalar through (t, value, ease) keys."""
    import eranim
    return eranim.eased_value(keys, t)


def wave(t, period, phase=0.0):
    return math.sin(2.0 * math.pi * (t / period + phase))
