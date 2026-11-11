"""Decals for the mount breeds (rig_horse_breeds.py, rig_griffin_breeds.py): leaves and blossoms, flames, sea foam and
fins, fish scales, lava veins, gold filigree, ice crystals, star fields, lightning, feather barring and mottling.

Every helper draws on a FaceCanvas in fine pixels (fc.fw x fc.fh, x to the right, y down as seen from outside the box)
and, where it shines, on the matching glow canvas (the emissive layer). Randomness is seeded from the arguments: the
output is deterministic. Own designs throughout.
"""
import math

import numpy as np

from erpaint import mix, rgb, seed_of, shade

WHITE = (255, 255, 255)


def col(c):
    return rgb(c) if isinstance(c, str) else tuple(c[:3])


def rng_of(*keys):
    return np.random.default_rng(seed_of('breed', *keys))


def each(fc):
    for y in range(fc.fh):
        for x in range(fc.fw):
            yield x, y


def opaque(fc, x, y):
    return fc.img[fc.v * fc.k + y, fc.u * fc.k + x, 3] > 0


def fill(fc, c):
    c = col(c)
    for x, y in each(fc):
        fc.fpx(x, y, c)


def vgrad(fc, top, bottom, glow=None, glow_top=None, glow_bottom=None, keep_alpha=True):
    """A vertical gradient over the face (and its glow), keeping cut-out pixels cut."""
    top, bottom = col(top), col(bottom)
    for x, y in each(fc):
        if keep_alpha and not opaque(fc, x, y):
            continue
        t = y / max(1, fc.fh - 1)
        fc.fpx(x, y, mix(top, bottom, t))
        if glow is not None:
            glow.fpx(x, y, mix(col(glow_top), col(glow_bottom), t))


def clear_all(fc):
    for x, y in each(fc):
        fc.fclear(x, y)


def glow_copy(fc, glow, factor=0.9, only=None):
    """The face's own colours on the glow layer (a shining part), optionally only where {@code only(x, y)}."""
    for x, y in each(fc):
        if not opaque(fc, x, y) or (only is not None and not only(x, y)):
            continue
        glow.fpx(x, y, shade(fc.fget(x, y), factor))


# ============================================================================================ leaves and flowers

def leaf(fc, base, light, dark, vein, keys, glow=None, glow_col=None, serrate=True):
    """A leaf on a blade face (fine y 0 is the tip): a pointed outline widest two fifths from the base, a midrib, side
    veins slanting to the tip, a serrated edge, a lit left half and a shadowed right half."""
    base, light, dark, vein = col(base), col(light), col(dark), col(vein)
    w, h = fc.fw, fc.fh
    r = rng_of('leaf', *keys)
    cx = (w - 1) / 2.0
    for y in range(h):
        t = (y + 0.5) / h                     # 0 at the tip, 1 at the stalk
        half = (w / 2.0) * (math.sin(math.pi * min(1.0, t * 0.92 + 0.04)) ** 0.75)
        if t > 0.9:
            half = min(half, max(1.0, w * 0.18))
        for x in range(w):
            d = abs(x - cx)
            if d > half + 0.25:
                fc.fclear(x, y)
                continue
            edge = d > half - 1.0
            c = mix(light, base, min(1.0, (x / max(1, w - 1)) * 1.3)) if x < cx else mix(base, dark, (d / max(1.0, half)) * 0.8)
            # side veins: slanting toward the tip
            if not edge and abs(((y + d * 0.9) % 4.0) - 0.0) < 0.6 and d > 0.8:
                c = mix(c, vein, 0.55)
            if abs(x - cx) < 0.6:
                c = vein
            if edge:
                c = shade(c, 0.78)
                if serrate and (y + int(d)) % 3 == 0 and r.random() < 0.7:
                    fc.fclear(x, y)
                    continue
            fc.fpx(x, y, c)
            if glow is not None and glow_col is not None and (abs(x - cx) < 0.6 or edge):
                glow.fpx(x, y, col(glow_col))
    for x in range(w):
        if 0 <= int(cx) < w:
            fc.fpx(int(cx), h - 1, shade(vein, 0.8))


def blossom(fc, cx, cy, petal, heart, glow=None, glow_col=None, size=2):
    """A five-petal flower centred at fine (cx, cy): petals of {@code size} around a bright heart."""
    petal, heart = col(petal), col(heart)
    pts = []
    for i in range(5):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        for rr in range(1, size + 1):
            pts.append((int(round(cx + math.cos(a) * rr)), int(round(cy + math.sin(a) * rr)), rr))
    for x, y, rr in pts:
        fc.fpx(x, y, shade(petal, 1.08 if rr == size else 0.95))
        if glow is not None and glow_col is not None:
            glow.fpx(x, y, shade(col(glow_col), 0.7))
    fc.fpx(cx, cy, heart)
    if glow is not None and glow_col is not None:
        glow.fpx(cx, cy, col(glow_col))


def petal_face(fc, petal, heart, keys, glow=None, glow_col=None):
    """A face that is all flower: overlapping petals with darker creases and a bright heart."""
    petal, heart = col(petal), col(heart)
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for x, y in each(fc):
        a = math.atan2(y - cy, x - cx)
        d = math.hypot(x - cx, y - cy) / max(1.0, min(cx, cy) + 0.5)
        lobe = 0.75 + 0.25 * math.cos(a * 5)
        if d > lobe * 1.15 and (x in (0, w - 1) or y in (0, h - 1)):
            fc.fclear(x, y)
            continue
        c = mix(heart, petal, min(1.0, d * 1.6))
        if 0.35 < d and abs(math.cos(a * 5 + math.pi)) > 0.97:
            c = shade(c, 0.82)
        fc.fpx(x, y, c)
        if glow is not None and glow_col is not None:
            glow.fpx(x, y, shade(col(glow_col), 1.0 - 0.5 * min(1.0, d)))


def spots(fc, color, keys, density=0.06, radius=(0.9, 1.7), region=None, glow=None, glow_col=None):
    """Soft round spots (a fawn's dapples, an owl's flecks), only inside {@code region(x, y)} if given."""
    color = col(color)
    r = rng_of('spots', *keys)
    w, h = fc.fw, fc.fh
    count = max(1, int(w * h * density / 4))
    for _ in range(count):
        ox, oy = r.uniform(0, w), r.uniform(0, h)
        if region is not None and not region(int(ox), int(oy)):
            continue
        rad = r.uniform(*radius)
        for y in range(int(oy - rad - 1), int(oy + rad + 2)):
            for x in range(int(ox - rad - 1), int(ox + rad + 2)):
                if 0 <= x < w and 0 <= y < h and opaque(fc, x, y):
                    d = math.hypot(x + 0.5 - ox, y + 0.5 - oy)
                    if d <= rad:
                        fc.fpx(x, y, mix(fc.fget(x, y), color, 0.85 if d < rad - 0.6 else 0.45))
                        if glow is not None and glow_col is not None:
                            glow.fpx(x, y, shade(col(glow_col), 1.0 if d < rad - 0.6 else 0.55))


# ============================================================================================ fire

FIRE = [(255, 250, 215), (255, 222, 110), (255, 160, 40), (230, 84, 22), (150, 34, 18)]
SOUL_FIRE = [(225, 255, 255), (140, 240, 255), (60, 190, 230), (30, 110, 190), (24, 50, 120)]
GOLD_FIRE = [(255, 255, 240), (255, 244, 170), (255, 214, 90), (240, 160, 40), (170, 90, 30)]


def flame(fc, keys, ramp=FIRE, glow=None, tip_up=True, tongues=True, dim=1.0):
    """Fire on a face: the hottest colours at the root, darker red at the tips, licking tongues cut out at the tip
    end; all of it on the glow layer (fire lights itself). {@code tip_up}: the tip is fine y 0 (a rising flame)."""
    r = rng_of('flame', *keys)
    w, h = fc.fw, fc.fh
    phase = r.uniform(0, 6.28)
    cut = [0] * w
    if tongues:
        for x in range(w):
            cut[x] = int(max(0.0, (h * 0.3) * (0.5 + 0.5 * math.sin(x * 1.7 + phase)) + r.uniform(-1, 1)))
    for x, y in each(fc):
        yy = y if tip_up else h - 1 - y
        if yy < cut[x]:
            fc.fclear(x, y)
            continue
        t = 1.0 - (yy - cut[x]) / max(1, h - cut[x])        # 1 at the tip, 0 at the root
        t = min(1.0, max(0.0, t + 0.12 * math.sin(x * 2.3 + y * 0.9 + phase)))
        k = t * (len(ramp) - 1)
        i = min(len(ramp) - 2, int(k))
        c = mix(ramp[i], ramp[i + 1], k - i)
        if (x + y * 3) % 7 == 0:
            c = mix(c, ramp[0], 0.35)
        fc.fpx(x, y, c)
        if glow is not None:
            glow.fpx(x, y, shade(c, dim))


def lava_veins(fc, keys, glow=None, hot=(255, 150, 40), core=(255, 236, 160), density=1.0, dim=1.0):
    """Cracks of glowing lava branching over a dark face (the coat of a fire breed): bright cores, orange edges."""
    r = rng_of('veins', *keys)
    w, h = fc.fw, fc.fh
    count = max(1, int((w + h) / 9 * density))
    hot, core = col(hot), col(core)
    for _ in range(count):
        x, y = r.uniform(0, w), r.uniform(0, h)
        a = r.uniform(0, 6.28)
        for _ in range(int(r.integers(6, 16))):
            ix, iy = int(x), int(y)
            if 0 <= ix < w and 0 <= iy < h and opaque(fc, ix, iy):
                c = core if r.random() < 0.35 else hot
                fc.fpx(ix, iy, c)
                if glow is not None:
                    glow.fpx(ix, iy, shade(c, dim))
                for nx, ny in ((ix + 1, iy), (ix, iy + 1)):
                    if 0 <= nx < w and 0 <= ny < h and opaque(fc, nx, ny) and r.random() < 0.3:
                        fc.fpx(nx, ny, mix(fc.fget(nx, ny), hot, 0.5))
            a += r.uniform(-0.9, 0.9)
            x += math.cos(a)
            y += math.sin(a)


# ============================================================================================ the sea

def foam(fc, keys, deep, body, crest, glow=None, glow_col=None, tip_at_top=True):
    """A wave on a blade face: deep water at the root, clear blue, a curling white crest at the tip with spray dots,
    streaks of light along it."""
    deep, body, crest = col(deep), col(body), col(crest)
    r = rng_of('foam', *keys)
    w, h = fc.fw, fc.fh
    ph = r.uniform(0, 7)
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        yy = y if tip_at_top else h - 1 - y
        t = 1.0 - yy / max(1, h - 1)                         # 1 at the tip
        c = mix(deep, body, min(1.0, t * 1.6))
        curl = 0.72 + 0.12 * math.sin(x * 1.3 + ph)
        if t > curl:
            c = mix(body, crest, min(1.0, (t - curl) / 0.12))
        if (x * 2 + yy) % 9 == 0 and t < curl:
            c = mix(c, crest, 0.35)
        fc.fpx(x, y, c)
        if glow is not None and glow_col is not None and t > curl + 0.06:
            glow.fpx(x, y, col(glow_col))
    for _ in range(max(1, w // 3)):
        x, y = int(r.integers(0, w)), int(r.integers(0, max(1, h // 4)))
        yy = y if tip_at_top else h - 1 - y
        if opaque(fc, x, yy):
            fc.fpx(x, yy, crest)


def fin(fc, membrane, rib, edge, keys, ribs=4, glow=None, glow_col=None, root_at='bottom', scallop=True):
    """A fin: a membrane shading lighter to its free edge, bony rays fanning from the root, a scalloped free edge,
    glowing dots along the rays (a deep-sea shimmer) when {@code glow_col} is given."""
    membrane, rib, edge = col(membrane), col(rib), col(edge)
    w, h = fc.fw, fc.fh
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        t = (h - 1 - y) / max(1, h - 1) if root_at == 'bottom' else y / max(1, h - 1)   # 0 root, 1 free edge
        c = mix(membrane, edge, t ** 1.5)
        fc.fpx(x, y, c)
    for i in range(ribs):
        fx = (i + 0.5) / ribs
        for y in range(h):
            t = (h - 1 - y) / max(1, h - 1) if root_at == 'bottom' else y / max(1, h - 1)
            x = int(round((w - 1) * (0.5 + (fx - 0.5) * (0.55 + 0.45 * t))))
            if 0 <= x < w and opaque(fc, x, y):
                fc.fpx(x, y, rib)
                if glow is not None and glow_col is not None and y % 3 == 1:
                    glow.fpx(x, y, col(glow_col))
    if scallop:
        k = max(1, w // max(1, ribs))
        for x in range(w):
            depth = int(round(1.5 * (1 - math.cos((x % k) / k * 2 * math.pi)) / 2 * 2))
            for d in range(depth):
                y = d if root_at == 'bottom' else h - 1 - d
                fc.fclear(x, y)


def fish_scales(fc, base, light, dark, size=4, region=None):
    """Overlapping fish scales: rows of arcs, each scale lit at its top and shadowed at its rim."""
    base, light, dark = col(base), col(light), col(dark)
    w, h = fc.fw, fc.fh
    for x, y in each(fc):
        if not opaque(fc, x, y) or (region is not None and not region(x, y)):
            continue
        row = y // (size - 1)
        off = (size // 2) if row % 2 else 0
        sx = (x + off) % size
        sy = y % (size - 1)
        d = math.hypot(sx - (size - 1) / 2.0, sy - 0.2)
        if d > (size - 1) / 2.0 + 0.3:
            c = dark
        elif sy == 0:
            c = light
        else:
            c = mix(base, dark, sy / max(1, size - 1) * 0.6)
        fc.fpx(x, y, c)


def pearl(fc, glow=None, glow_col=(230, 240, 255)):
    """A round pearl filling the face: a soft sheen, transparent corners."""
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    rr = min(w, h) / 2.0
    for x, y in each(fc):
        d = math.hypot(x - cx, y - cy)
        if d > rr:
            fc.fclear(x, y)
            continue
        c = mix((255, 255, 255), (196, 206, 226), d / rr)
        if x < cx and y < cy and d < rr * 0.5:
            c = (255, 255, 255)
        fc.fpx(x, y, c)
        if glow is not None:
            glow.fpx(x, y, shade(glow_col, 0.75 - 0.3 * d / rr))


# ============================================================================================ gold, light, crystal

def filigree(fc, gold, dark, keys, step=6):
    """Gold scrollwork on a white plate: S-curls on a grid, each a ring with a tail, darker under-lines."""
    gold, dark = col(gold), col(dark)
    w, h = fc.fw, fc.fh
    for oy in range(step // 2, h, step):
        for ox in range(step // 2 + ((oy // step) % 2) * (step // 2), w, step):
            for a in range(0, 360, 30):
                x = int(round(ox + math.cos(math.radians(a)) * 1.6))
                y = int(round(oy + math.sin(math.radians(a)) * 1.6))
                if 0 <= x < w and 0 <= y < h and opaque(fc, x, y) and a not in (0, 30):
                    fc.fpx(x, y, gold)
                    if 0 <= y + 1 < h and opaque(fc, x, y + 1) and a > 180:
                        fc.fpx(x, y + 1, dark)
            for t in range(1, 3):
                x, y = ox + 1 + t, oy - t
                if 0 <= x < w and 0 <= y < h and opaque(fc, x, y):
                    fc.fpx(x, y, gold)


def light_strands(fc, colors, keys, glow=None, dim=0.95, tip_at_top=True):
    """Hair of light: strands in a pastel ramp running along the blade, a white sheen down the middle; all of it on
    the glow layer."""
    ramp = [col(c) for c in colors]
    r = rng_of('light', *keys)
    w, h = fc.fw, fc.fh
    offsets = [r.uniform(0, 1) for _ in range(w)]
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        yy = y if tip_at_top else h - 1 - y
        t = (1.0 - yy / max(1, h - 1)) * 0.7 + offsets[x] * 0.3
        k = min(len(ramp) - 1.001, t * (len(ramp) - 1))
        i = int(k)
        c = mix(ramp[i], ramp[i + 1], k - i)
        if x == w // 2 or (x + yy) % 5 == 0:
            c = mix(c, WHITE, 0.45)
        if x in (0, w - 1):
            c = shade(c, 0.9)
        fc.fpx(x, y, c)
        if glow is not None:
            glow.fpx(x, y, shade(c, dim))


def crystal(fc, base, light, dark, keys, glow=None, glow_col=None, facets=3):
    """An ice crystal face: diagonal facets, a bright edge, inner cracks, a glint; a cold glow inside if asked."""
    base, light, dark = col(base), col(light), col(dark)
    r = rng_of('crystal', *keys)
    w, h = fc.fw, fc.fh
    cuts = sorted(r.uniform(0.15, 0.85, facets))
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        s = (x / max(1, w - 1) + y / max(1, h - 1)) / 2.0
        band = sum(1 for c in cuts if s > c)
        c = mix(light, base, band / facets)
        c = mix(c, dark, 0.35 * (y / max(1, h - 1)))
        if any(abs(s - cc) < 0.03 for cc in cuts):
            c = mix(c, WHITE, 0.6)
        fc.fpx(x, y, c)
        if glow is not None and glow_col is not None and band >= facets - 1:
            glow.fpx(x, y, shade(col(glow_col), 0.55 + 0.15 * band / facets))
    for _ in range(max(1, (w * h) // 30)):
        x, y = int(r.integers(0, w)), int(r.integers(0, h))
        if opaque(fc, x, y):
            fc.fpx(x, y, WHITE)
            if glow is not None and glow_col is not None:
                glow.fpx(x, y, WHITE)


def frost(fc, color, keys, density=1.0):
    """Feathery frost: short branching white strokes."""
    color = col(color)
    r = rng_of('frost', *keys)
    w, h = fc.fw, fc.fh
    for _ in range(max(1, int((w * h) / 40 * density))):
        x, y = r.uniform(0, w), r.uniform(0, h)
        a = r.uniform(0, 6.28)
        for step in range(int(r.integers(3, 7))):
            ix, iy = int(x), int(y)
            if 0 <= ix < w and 0 <= iy < h and opaque(fc, ix, iy):
                fc.fpx(ix, iy, mix(fc.fget(ix, iy), color, 0.7))
                if step % 2 == 1:
                    for bx, by in ((ix + 1, iy - 1), (ix - 1, iy - 1)):
                        if 0 <= bx < w and 0 <= by < h and opaque(fc, bx, by):
                            fc.fpx(bx, by, mix(fc.fget(bx, by), color, 0.45))
            x += math.cos(a)
            y += math.sin(a)


def stars(fc, keys, colors=((255, 255, 255), (200, 210, 255), (255, 220, 250)), density=0.03, glow=None,
          crosses=True):
    """A field of stars: single bright pixels, a few with a four-point glint; every star on the glow layer."""
    r = rng_of('stars', *keys)
    w, h = fc.fw, fc.fh
    for x, y in each(fc):
        if not opaque(fc, x, y) or r.random() >= density:
            continue
        c = col(colors[int(r.integers(0, len(colors)))])
        big = crosses and r.random() < 0.18
        fc.fpx(x, y, c)
        if glow is not None:
            glow.fpx(x, y, c)
        if big:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < w and 0 <= y + dy < h and opaque(fc, x + dx, y + dy):
                    fc.fpx(x + dx, y + dy, shade(c, 0.75))
                    if glow is not None:
                        glow.fpx(x + dx, y + dy, shade(c, 0.6))


def runes(fc, color, keys, glow=None, glow_col=None, spacing=7):
    """Angular glowing runes in a row: short strokes on a 3x4 grid per rune."""
    color = col(color)
    r = rng_of('runes', *keys)
    w, h = fc.fw, fc.fh
    y0 = max(0, h // 2 - 2)
    for x0 in range(1, w - 3, spacing):
        strokes = r.integers(0, 2, (4, 3))
        strokes[0][1] = 1
        for gy in range(4):
            for gx in range(3):
                if strokes[gy][gx]:
                    x, y = x0 + gx, y0 + gy
                    if 0 <= x < w and 0 <= y < h and opaque(fc, x, y):
                        fc.fpx(x, y, color)
                        if glow is not None:
                            glow.fpx(x, y, col(glow_col or color))


def bolt(fc, color, keys, glow=None, glow_col=None, branches=1, along='y'):
    """A zig-zag lightning bolt along the face (top to bottom), with a forked branch; it glows."""
    color = col(color)
    r = rng_of('bolt', *keys)
    w, h = fc.fw, fc.fh
    length, span = (h, w) if along == 'y' else (w, h)
    for b in range(branches):
        pos = r.uniform(span * 0.25, span * 0.75)
        for t in range(length):
            if t % 3 == 0:
                pos += r.choice((-1.5, 1.5))
                pos = max(1, min(span - 2, pos))
            for thick in (0, 1) if b == 0 else (0,):
                p = int(pos) + thick
                x, y = (p, t) if along == 'y' else (t, p)
                if 0 <= x < w and 0 <= y < h and opaque(fc, x, y):
                    c = color if thick == 0 else shade(color, 0.8)
                    fc.fpx(x, y, c)
                    if glow is not None:
                        glow.fpx(x, y, col(glow_col or color))


# ============================================================================================ feathers and fur

def barring(fc, dark, period=4, phase=0, axis='y', strength=0.55):
    """Dark bars across a feather (a hawk's barring)."""
    dark = col(dark)
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        p = y if axis == 'y' else x
        if (p + phase) % period == 0:
            fc.fpx(x, y, mix(fc.fget(x, y), dark, strength))


def mottle(fc, light, dark, keys, density=0.08):
    """An owl's mottling: soft light and dark flecks in short horizontal dashes."""
    light, dark = col(light), col(dark)
    r = rng_of('mottle', *keys)
    w, h = fc.fw, fc.fh
    for _ in range(max(1, int(w * h * density / 2))):
        x, y = int(r.integers(0, w)), int(r.integers(0, h))
        c = light if r.random() < 0.55 else dark
        for dx in range(int(r.integers(1, 3))):
            if 0 <= x + dx < w and opaque(fc, x + dx, y):
                fc.fpx(x + dx, y, mix(fc.fget(x + dx, y), c, 0.75))


def fur_tufts(fc, base, light, dark, keys, tip_at_top=False):
    """Shaggy fur: wavy locks of light and dark running along the face, ragged at the free end."""
    base, light, dark = col(base), col(light), col(dark)
    r = rng_of('tufts', *keys)
    w, h = fc.fw, fc.fh
    phase = r.uniform(0, 6.28)
    for x, y in each(fc):
        if not opaque(fc, x, y):
            continue
        s = math.sin(x * 1.1 + math.sin(y * 0.35 + phase) * 1.5)
        c = mix(base, light if s > 0 else dark, abs(s) * 0.55)
        fc.fpx(x, y, c)
    for x in range(w):
        depth = int(r.integers(0, 3))
        for d in range(depth):
            y = d if tip_at_top else h - 1 - d
            fc.fclear(x, y)


def trim(fc, color, width=1, sides='tblr'):
    color = col(color)
    w, h = fc.fw, fc.fh
    for i in range(width):
        for x in range(w):
            if 't' in sides:
                fc.fpx(x, i, color)
            if 'b' in sides:
                fc.fpx(x, h - 1 - i, color)
        for y in range(h):
            if 'l' in sides:
                fc.fpx(i, y, color)
            if 'r' in sides:
                fc.fpx(w - 1 - i, y, color)


def gem(fc, glow, base, glow_col):
    """A faceted gem filling the face (on the glow layer too)."""
    base = col(base)
    w, h = fc.fw, fc.fh
    for x, y in each(fc):
        t = (x + y) / max(1, w + h - 2)
        c = shade(base, 1.3 - 0.55 * t)
        fc.fpx(x, y, c)
        glow.fpx(x, y, shade(col(glow_col), 1.0 - 0.3 * t))
    fc.fpx(0, 0, WHITE)
    glow.fpx(0, 0, WHITE)


def eye(fc, glow, x, y, color, w=3, h=2, glint=True):
    """A glowing eye of w x h fine pixels at (x, y) with a white glint and a dark lid line above."""
    color = col(color)
    for dy in range(h):
        for dx in range(w):
            c = color if dy < h - 1 or h == 1 else shade(color, 0.7)
            fc.fpx(x + dx, y + dy, c)
            glow.fpx(x + dx, y + dy, c)
    if glint:
        fc.fpx(x, y, WHITE)
        glow.fpx(x, y, WHITE)
    for dx in range(-1, w + 1):
        fc.fpx(x + dx, y - 1, shade(color, 0.15))
