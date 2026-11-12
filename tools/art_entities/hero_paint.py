"""Painting helpers for the hero rig (rig_hero.py), which is painted at two texels per unit: every FaceCanvas of a
hero texture is addressed in fine pixels (fpx, fw x fh) for faces, hair strands, embroidery and engraving.
Deterministic: no randomness except erpaint's seeded material noise and hashes of the coordinates.

Face canvases are seen from outside the box: on 'right' (the entity's right, model -X) x = fw - 1 is the front edge,
on 'left' x = 0 is the front edge; on 'top' y = fh - 1 is the front edge.

The 16 x 16 head front (fine pixels): forehead rows 0-6 (the bangs part hangs in front of rows 0-8), brows on
rows 6-7, eyes on rows 8-11 (right eye x 3-6, left eye x 9-12, seen from the front: the right eye is on the viewer's
left), nose rows 11-12 at x 7-8, mouth rows 13-14, chin row 15.
"""
import hashlib

from erpaint import lighten, mix, rgb, shade

LASH = (34, 22, 30)


def col(c):
    if isinstance(c, str):
        return rgb(c)
    return tuple(int(v) for v in c[:3])


def h01(*keys):
    """Deterministic 0..1 from keys."""
    return int(hashlib.md5('/'.join(str(k) for k in keys).encode()).hexdigest()[:6], 16) / float(0xFFFFFF)


# ------------------------------------------------------------------------------------------- canvas access

def faces(p, part, index=0, sides=False, glow=False):
    return p.all_faces(part, index=index, sides_only=sides, glow=glow)


def face(p, part, name, index=0, glow=False):
    return p.face(part, name, index=index, glow=glow)


def put(fc, x, y, c, a=255):
    fc.fpx(x, y, col(c), a)


def get(fc, x, y):
    return fc.fget(x, y)


def alpha(fc, x, y):
    if 0 <= x < fc.fw and 0 <= y < fc.fh:
        return int(fc.img[fc.v * fc.k + y, fc.u * fc.k + x, 3])
    return 0


def clear(fc, x, y):
    fc.fclear(x, y)


def blend(fc, x, y, c, t):
    if 0 <= x < fc.fw and 0 <= y < fc.fh and alpha(fc, x, y) > 0:
        fc.fpx(x, y, mix(get(fc, x, y), col(c), t))


def tint(fc, x, y, f):
    if 0 <= x < fc.fw and 0 <= y < fc.fh and alpha(fc, x, y) > 0:
        fc.fpx(x, y, shade(get(fc, x, y), f))


def hline(fc, x0, x1, y, c):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        put(fc, x, y, c)


def vline(fc, x, y0, y1, c):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        put(fc, x, y, c)


def rect(fc, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(fc, x, y, c)


def line(fc, x0, y0, x1, y1, c, t=1.0):
    """A pixel line (Bresenham); t < 1 blends into what is there."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if t >= 1.0:
            put(fc, x0, y0, c)
        else:
            blend(fc, x0, y0, c, t)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def fill(fc, c, top=1.05, bottom=0.88, edge=0.94, noise=0.03, key=''):
    """A vertical gradient with slightly darker side columns and a little deterministic noise."""
    c = col(c)
    for y in range(fc.fh):
        f = top + (bottom - top) * (y / max(1, fc.fh - 1))
        for x in range(fc.fw):
            g = f * (edge if x in (0, fc.fw - 1) else 1.0)
            if noise:
                g *= 1.0 + noise * (h01(key, fc.u, fc.v, fc.face, x, y) - 0.5)
            put(fc, x, y, shade(c, g))


def fill_part(p, part, c, index=0, sides_only=False, **kw):
    for fc in faces(p, part, index, sides=sides_only):
        fill(fc, c, **kw)


def clear_face(fc):
    for y in range(fc.fh):
        for x in range(fc.fw):
            clear(fc, x, y)


def clear_part(p, part, index=0):
    for fc in faces(p, part, index):
        clear_face(fc)


def outline_alpha(fc, c, f=0.6):
    """Darkens opaque pixels next to transparent ones (gives cut-out shapes a crisp edge)."""
    edge = []
    for y in range(fc.fh):
        for x in range(fc.fw):
            if alpha(fc, x, y) == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < fc.fw and 0 <= ny < fc.fh and alpha(fc, nx, ny) == 0:
                    edge.append((x, y))
                    break
    for x, y in edge:
        put(fc, x, y, mix(shade(get(fc, x, y), f), col(c), 0.3))


# ------------------------------------------------------------------------------------------------ glow

def glow(p, part, name, x, y, c, index=0, body=None):
    """A pixel that shines: the glow texture, and (optionally) the body texture too."""
    g = face(p, part, name, index=index, glow=True)
    put(g, x, y, c)
    if body is not None:
        put(face(p, part, name, index=index), x, y, body)


def glow_face(p, part, name, c, index=0, mask=None):
    g = face(p, part, name, index=index, glow=True)
    b = face(p, part, name, index=index)
    for y in range(g.fh):
        for x in range(g.fw):
            if (mask is None or mask(x, y)) and alpha(b, x, y) > 0:
                put(g, x, y, mix(get(b, x, y), col(c), 0.7))


def glow_part(p, part, c, index=0, mask=None):
    for name in ('front', 'right', 'back', 'left', 'top', 'bottom'):
        glow_face(p, part, name, c, index=index, mask=mask)


# ------------------------------------------------------------------------------------------------ faces

class HeroFace:
    """A face on the 16 x 16 head front plus the matching closed eyes (eyelids plane) and open mouth (mouth plane).

    kind 'female' (lashes, lips, larger irises) or 'male' (narrower eyes, heavier brows); eyes 'soft' (drooping outer
    corners), 'sharp' (upturned) or 'calm' (half-lidded, heavy lashes) or 'plain'.
    """

    def __init__(self, skin, iris_r, iris_l=None, kind='female', eyes='plain', brow=None, lips=None, smile=True,
                 blush=None, beauty_mark=None, scar=None, stubble=None, lash=LASH, white=(250, 246, 242)):
        self.skin = col(skin)
        self.iris_r = col(iris_r)
        self.iris_l = col(iris_l if iris_l is not None else iris_r)
        self.kind = kind
        self.eyes = eyes
        self.brow = col(brow) if brow is not None else shade(self.skin, 0.45)
        self.lips = col(lips) if lips is not None else None
        self.smile = smile
        self.blush = col(blush) if blush is not None else None
        self.beauty_mark = beauty_mark
        self.scar = scar
        self.stubble = col(stubble) if stubble is not None else None
        self.lash = col(lash)
        self.white = col(white)

    # the outer corner of the right eye is x 3, of the left eye x 12; inner corners x 6 and 9
    def _eye(self, f, side, iris):
        outer, inner, step = (3, 6, 1) if side == 'r' else (12, 9, -1)
        xs = [outer + step * i for i in range(4)]
        lash = self.lash
        if self.kind == 'female':
            # a grown woman's eye: an iris of two columns between whites, three rows tall, lashes that sweep out;
            # 'calm' lowers the lid over the iris (the witch's knowing look)
            top = 8
            rows = (9, 10, 11)
            for y in rows:
                k = (y - rows[0]) / max(1, len(rows) - 1)
                put(f, xs[0], y, self.white if y < 11 else mix(self.white, self.skin, 0.55))
                put(f, xs[3], y, self.white if y == 10 else mix(self.white, self.skin, 0.6))
                for i in (1, 2):
                    c = shade(iris, 0.55 + 0.6 * k)
                    if i == 2 and y == 10:
                        c = shade(iris, 0.3)            # the pupil
                    put(f, xs[i], y, c)
            put(f, xs[1], rows[0], (255, 255, 255))     # the catch light
            put(f, xs[2], 11, lighten(iris, 0.45))      # light in the iris' lower edge
            for x in xs:
                put(f, x, top, lash)
            put(f, outer - step, top, lash)
            if self.eyes == 'calm':
                put(f, outer - step, top - 1, lash)     # a winged line
                put(f, outer - 2 * step, top - 1, mix(lash, self.skin, 0.35))
                for x in xs[1:3]:
                    put(f, x, 9, mix(get(f, x, 9), lash, 0.45))     # the lid's shadow over the iris
                for x in xs:
                    put(f, x, top - 1, mix(lash, self.skin, 0.8))   # the crease of the lid
            elif self.eyes == 'soft':
                put(f, outer - step, top + 1, mix(lash, self.skin, 0.2))   # drooping outer corners
            elif self.eyes == 'sharp':
                put(f, outer - step, top - 1, lash)
            put(f, xs[0], 12, mix(lash, self.skin, 0.55))
            put(f, xs[1], 12, mix(lash, self.skin, 0.8))
        else:
            # a smaller iris with white on both sides, under a strong lid line
            rows = (10, 11)
            for y in rows:
                put(f, xs[0], y, self.white if y == 10 else shade(self.skin, 0.82))
                put(f, xs[3], y, self.white if y == 10 else shade(self.skin, 0.82))
                for i in (1, 2):
                    c = shade(iris, 0.55 if y == 10 else 0.95)
                    put(f, xs[i], y, c)
            put(f, xs[2], 10, shade(iris, 0.28))       # the pupil
            put(f, xs[1], 10, lighten(iris, 0.55))      # the catch light
            for x in xs:
                put(f, x, 9, lash)
            if self.eyes == 'sharp':
                put(f, outer - step, 8, lash)           # flicked up at the outer corner
                put(f, xs[3], 10, mix(self.white, self.skin, 0.4))
            else:
                put(f, outer - step, 9, mix(lash, self.skin, 0.4))
            for x in xs[1:3]:
                put(f, x, 12, shade(self.skin, 0.9))        # the lower lid's shade
        return xs

    def _brows(self, f):
        b = self.brow
        if self.kind == 'female':
            for (x, y) in ((2, 6), (3, 5), (4, 5), (5, 5), (13, 6), (12, 5), (11, 5), (10, 5)):
                put(f, x, y, b)
            put(f, 6, 6, mix(b, self.skin, 0.5))
            put(f, 9, 6, mix(b, self.skin, 0.5))
        elif self.eyes == 'sharp':
            # slanting down towards the nose: a determined look
            for (x, y) in ((2, 6), (3, 6), (4, 7), (5, 7), (6, 7), (13, 6), (12, 6), (11, 7), (10, 7), (9, 7)):
                put(f, x, y, b)
            for (x, y) in ((3, 7), (12, 7), (2, 7), (13, 7)):
                put(f, x, y, shade(b, 1.15))
        else:
            for x in range(2, 7):
                put(f, x, 7, b)
                put(f, 15 - x, 7, b)
                put(f, x, 6, shade(b, 1.12))
                put(f, 15 - x, 6, shade(b, 1.12))

    def paint(self, p):
        f = face(p, 'head', 'front')
        s = self.skin
        # skin: light on the forehead and the bridge, soft shade at the jaw and the temples
        for y in range(16):
            for x in range(16):
                g = 1.03 - 0.06 * (y / 15.0)
                if x in (0, 15):
                    g *= 0.9
                elif x in (1, 14):
                    g *= 0.96
                if y >= 14 and (x <= 2 or x >= 13):
                    g *= 0.92
                put(f, x, y, shade(s, g))
        put(f, 8, 10, lighten(s, 0.12))
        put(f, 8, 11, lighten(s, 0.1))
        put(f, 7, 12, shade(s, 0.86))                  # the nose's shadow
        if self.blush is not None:
            for (x, y) in ((2, 12), (3, 12), (4, 12), (3, 13), (11, 12), (12, 12), (13, 12), (12, 13)):
                blend(f, x, y, self.blush, 0.32)
        self._brows(f)
        self._eye(f, 'r', self.iris_r)
        self._eye(f, 'l', self.iris_l)
        # the mouth: closed; a tint of the lips and a soft smile
        if self.lips is not None:
            lip = self.lips
            put(f, 7, 13, mix(shade(lip, 0.8), s, 0.15))
            put(f, 8, 13, mix(shade(lip, 0.8), s, 0.15))
            put(f, 6, 13, mix(shade(lip, 0.8), s, 0.6))
            put(f, 9, 13, mix(shade(lip, 0.8), s, 0.6))
            put(f, 7, 14, mix(lip, s, 0.55))
            put(f, 8, 14, mix(lip, s, 0.45))
            if self.smile:
                put(f, 10, 12, mix(shade(lip, 0.7), s, 0.7))
                put(f, 5, 12, mix(shade(lip, 0.7), s, 0.7))
        else:
            m = shade(s, 0.62)
            hline(f, 6, 9, 13, m)
            put(f, 6, 13, shade(s, 0.78))
            if self.smile:
                put(f, 10, 12, shade(s, 0.74))
                put(f, 9, 13, shade(s, 0.7))
            put(f, 7, 14, shade(s, 0.93))
            put(f, 8, 14, shade(s, 0.93))
        if self.beauty_mark is not None:
            put(f, self.beauty_mark[0], self.beauty_mark[1], (70, 36, 40))
        if self.stubble is not None:
            for y in range(12, 16):
                for x in range(2, 14):
                    if y == 13 and 5 <= x <= 10:
                        continue
                    if h01('stubble', x, y) < (0.35 if y > 13 else 0.18):
                        blend(f, x, y, self.stubble, 0.35)
        if self.scar is not None:
            (x0, y0), (x1, y1) = self.scar
            scar = mix(lighten(s, 0.15), (210, 120, 120), 0.35)
            for (x, y) in _line_points(x0, y0, x1, y1):
                if 8 <= y <= 11 and (2 <= x <= 6 or 9 <= x <= 13):
                    continue                            # it crosses the brow and the cheek, not the eye
                put(f, x, y, scar)
        # the sides of the head: skin, the ear, the jaw's shade
        for name in ('right', 'left'):
            sf = face(p, 'head', name)
            front_x = 15 if name == 'right' else 0
            for y in range(16):
                for x in range(16):
                    g = 0.96 - 0.05 * (y / 15.0)
                    if abs(x - front_x) > 10:
                        g *= 0.95
                    put(sf, x, y, shade(s, g))
            ear_x = 9 if name == 'right' else 6
            for y in range(8, 12):
                put(sf, ear_x, y, shade(s, 0.86))
                put(sf, ear_x + (-1 if name == 'right' else 1), y, shade(s, 0.93))
        for name in ('back', 'top', 'bottom'):
            fill(face(p, 'head', name), shade(s, 0.9), noise=0.0)
        self.paint_lids(p)
        self.paint_mouth(p)

    def paint_lids(self, p):
        """The closed eyes: skin over the eyes and a curved lash line."""
        f = face(p, 'eyelids', 'front')
        clear_face(f)
        female = self.kind == 'female'
        rows = (8, 9, 10, 11) if female else (9, 10, 11)
        for side, xs in (('r', (2, 3, 4, 5, 6)), ('l', (13, 12, 11, 10, 9))):
            for y in rows:
                for x in xs:
                    put(f, x, y, shade(self.skin, 1.0 - 0.03 * (y - rows[0])))
            low = rows[-1] if self.eyes != 'sharp' else rows[-2]
            for i, x in enumerate(xs):
                y = low - (1 if i in (0, 4) else 0) if self.eyes != 'soft' else low - (1 if i == 4 else 0)
                put(f, x, y, self.lash)
            if female:
                put(f, xs[0], rows[-1], mix(self.lash, self.skin, 0.4))
        f2 = face(p, 'eyelids', 'back')
        clear_face(f2)

    def paint_mouth(self, p):
        """The open mouth while talking (the mouth plane's 16 x 8 covers face rows 8-15: its row r is face row r + 8)."""
        f = face(p, 'mouth', 'front')
        clear_face(f)
        inside = (110, 40, 52)
        if self.lips is not None:
            hline(f, 6, 9, 4, shade(self.lips, 0.85))
            hline(f, 6, 9, 5, inside)
            hline(f, 7, 8, 6, inside)
            put(f, 7, 5, (240, 236, 232))
            hline(f, 7, 8, 7, self.lips)
            put(f, 6, 6, mix(shade(self.lips, 0.8), self.skin, 0.3))
            put(f, 9, 6, mix(shade(self.lips, 0.8), self.skin, 0.3))
        else:
            hline(f, 6, 9, 5, shade(self.skin, 0.55))
            hline(f, 6, 9, 6, inside)
            put(f, 7, 6, (236, 232, 228))
            hline(f, 7, 8, 7, shade(self.skin, 0.8))
        clear_face(face(p, 'mouth', 'back'))


def _line_points(x0, y0, x1, y1):
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


# ------------------------------------------------------------------------------------------------ hair

def strand(c, x, y, h, key='', ring=None, sheen=0.0):
    """The colour of a strand pixel: per-column strands, darker towards the scalp's shadow, a highlight band
    (the 'ring' row range) and a little variation."""
    c = col(c)
    k = (0.9, 1.0, 1.08, 0.96, 1.04, 0.88, 1.02, 0.94)[(x * 3 + int(h01(key, x) * 8)) % 8]
    g = k * (1.04 - 0.18 * (y / max(1, h - 1)))
    out = shade(c, g)
    if ring is not None and ring[0] <= y <= ring[1]:
        t = 0.55 if (x + y) % 3 else 0.32
        out = mix(out, lighten(c, 0.5 + sheen), t)
    return out


def hair_cap(p, c, front_rows=3, side_rows=16, back_rows=16, frame_cols=2, frame_rows=12, ring=(4, 6),
             notch_ear=False, top_part=None, sheen=0.0, part='hair_cap'):
    """The hair shell over the head: top full; the front only along the hairline (rows < front_rows) and the side
    columns framing the face (frame_cols wide, down to frame_rows); sides and back down to side_rows/back_rows
    (with a notch for the ear if notch_ear). The head underneath gets the hair's shadow where the shell covers it."""
    c = col(c)
    for name in ('top', 'front', 'right', 'left', 'back', 'bottom'):
        fc = face(p, part, name)
        head = face(p, 'head', name) if name != 'bottom' else None
        for y in range(fc.fh):
            for x in range(fc.fw):
                on = False
                if name == 'top':
                    on = True
                elif name == 'front':
                    on = y < front_rows or ((x < frame_cols or x >= fc.fw - frame_cols) and y < frame_rows)
                elif name in ('right', 'left'):
                    fx = x if name == 'left' else fc.fw - 1 - x       # 0 at the front edge
                    depth = side_rows
                    if fx < 3:
                        depth = min(side_rows, frame_rows)
                    on = y < depth
                    if notch_ear and 6 <= fx <= 10 and 7 <= y <= 11:
                        on = False
                elif name == 'back':
                    on = y < back_rows
                if not on:
                    clear(fc, x, y)
                    continue
                if name == 'top':
                    fy = fc.fh - 1 - y
                    v = strand(c, x + (3 if top_part is not None and x < top_part else 0), y, fc.fh, 'top')
                    if fy <= 2:
                        v = mix(v, lighten(c, 0.4 + sheen), 0.35)
                    if top_part is not None and x == top_part and fy < 10:
                        v = shade(c, 0.7)
                else:
                    v = strand(c, x, y, max(fc.fh, 16), name, ring=ring, sheen=sheen)
                put(fc, x, y, v)
                if head is not None:
                    put(head, x, y, shade(c, 0.62))
        if name in ('front', 'right', 'left', 'back'):
            # the tips: a darker rim where the hair ends
            for x in range(fc.fw):
                for y in range(fc.fh - 1, -1, -1):
                    if alpha(fc, x, y) > 0:
                        put(fc, x, y, shade(get(fc, x, y), 0.78))
                        break


def bangs(p, c, tips, part='bangs', ring_row=2, sheen=0.0, cover_face=True):
    """The fringe in front of the forehead: tips[x] is the length of the strand at fine column x (0..fw-1, from the
    viewer's left); 0 leaves a gap. The sides and top of the part follow the front's outline."""
    c = col(c)
    f = face(p, part, 'front')
    for y in range(f.fh):
        for x in range(f.fw):
            if y < tips[x]:
                v = strand(c, x, y, f.fh, 'bangs', ring=(ring_row, ring_row + 1), sheen=sheen)
                if y >= tips[x] - 2:
                    v = shade(v, 0.86)
                put(f, x, y, v)
            else:
                clear(f, x, y)
    outline_alpha(f, shade(c, 0.5), 0.8)
    for name in ('right', 'left', 'back', 'top', 'bottom'):
        fc = face(p, part, name)
        edge = tips[0] if name == 'right' else tips[-1]
        for y in range(fc.fh):
            for x in range(fc.fw):
                if name in ('right', 'left') and y >= edge:
                    clear(fc, x, y)
                elif name == 'back':
                    if y < tips[fc.fw - 1 - x]:
                        put(fc, x, y, shade(c, 0.6))
                    else:
                        clear(fc, x, y)
                else:
                    put(fc, x, y, strand(c, x, y, fc.fh, name + 'b'))
        if name == 'bottom':
            clear_face(fc)


def hair_strip(p, part, c, ragged=True, wave=0, ring=None, sheen=0.0, index=0, taper=False, tips=None):
    """A lock or a fall of hair: strands down every face, optional waves (the strands shift sideways every `wave`
    rows), a highlight band and ragged tips."""
    c = col(c)
    for fc in faces(p, part, index):
        for y in range(fc.fh):
            for x in range(fc.fw):
                sx = x + ((y // wave) % 2 if wave else 0)
                v = strand(c, sx, y, fc.fh, part + fc.face, ring=ring if fc.face not in ('top', 'bottom') else None,
                           sheen=sheen)
                put(fc, x, y, v)
        if fc.face in ('front', 'back', 'right', 'left'):
            if ragged:
                for x in range(fc.fw):
                    cut = tips[x % len(tips)] if tips else int(h01(part, fc.face, x) * 3)
                    for d in range(cut):
                        clear(fc, x, fc.fh - 1 - d)
            if taper:
                for y in range(fc.fh * 2 // 3, fc.fh):
                    k = (y - fc.fh * 2 // 3) // 2 + 1
                    for d in range(min(k, fc.fw // 2)):
                        clear(fc, d, y)
                        clear(fc, fc.fw - 1 - d, y)


# ------------------------------------------------------------------------------------------------ cloth

def trim_row(fc, y, c, shine=True):
    c = col(c)
    for x in range(fc.fw):
        put(fc, x, y, lighten(c, 0.25) if shine and x % 4 == 1 else c)


def trim_col(fc, x, c, y0=0, y1=None):
    c = col(c)
    y1 = fc.fh - 1 if y1 is None else y1
    for y in range(y0, y1 + 1):
        put(fc, x, y, lighten(c, 0.2) if y % 4 == 1 else c)


def border(p, part, y, c, faces_=('front', 'right', 'back', 'left'), index=0, rows=1):
    """A band all around a part at fine row y (negative counts from the bottom)."""
    for fc in faces(p, part, index):
        if fc.face not in faces_:
            continue
        yy = y if y >= 0 else fc.fh + y
        for r in range(rows):
            trim_row(fc, yy + r, c)


def folds(fc, every=4, f=0.88, y0=0, phase=0):
    """Soft vertical folds of cloth: a darker column every few pixels, lighter next to it."""
    for x in range(fc.fw):
        k = (x + phase) % every
        g = f if k == 0 else (1.06 if k == 1 else 1.0)
        if g != 1.0:
            for y in range(y0, fc.fh):
                if alpha(fc, x, y) > 0:
                    tint(fc, x, y, g)


def stars(fc, c, density=0.06, key='', bright=None, glow_fc=None):
    """Tiny embroidered stars scattered on cloth (crosses of light on the darkest fabric)."""
    c = col(c)
    for y in range(1, fc.fh - 1):
        for x in range(1, fc.fw - 1):
            r = h01('stars', key, fc.face, x, y)
            if r < density:
                put(fc, x, y, c)
                if r < density * 0.35:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        blend(fc, x + dx, y + dy, c, 0.5)
                if glow_fc is not None:
                    put(glow_fc, x, y, bright or c)


def lacing(fc, x0, x1, y0, y1, c, every=2):
    """Criss-cross lacing between two columns."""
    c = col(c)
    for y in range(y0, y1 + 1, every):
        put(fc, x0, y, c)
        put(fc, x1, y, c)
        if y + every // 2 <= y1:
            put(fc, (x0 + x1) // 2, y + every // 2, lighten(c, 0.2))


def buttons(fc, x, ys, c):
    c = col(c)
    for y in ys:
        put(fc, x, y, lighten(c, 0.3))
        put(fc, x, y + 1, shade(c, 0.7))


def skin_arms(p, skin, upper, fore, rows_from=0, sides=('right', 'left')):
    for side in sides:
        for part in (upper, fore):
            name = part.replace('r_', 'r_' if side == 'right' else 'l_', 1) if side == 'right' else \
                part.replace('r_', 'l_', 1)
            for fc in faces(p, name):
                for y in range(rows_from, fc.fh):
                    for x in range(fc.fw):
                        put(fc, x, y, shade(col(skin), 0.98 - 0.06 * y / fc.fh))


def hands(p, c, fore_r, fore_l, rows=3, gloves=False, nails=None):
    """The hands at the end of the forearms (the last `rows` fine rows)."""
    c = col(c)
    for part in (fore_r, fore_l):
        for fc in faces(p, part):
            if fc.face == 'top':
                continue
            if fc.face == 'bottom':
                for y in range(fc.fh):
                    for x in range(fc.fw):
                        put(fc, x, y, shade(c, 0.92))
                continue
            for y in range(fc.fh - rows, fc.fh):
                for x in range(fc.fw):
                    put(fc, x, y, shade(c, 1.0 - 0.06 * (y - fc.fh + rows)))
            if not gloves:
                for x in range(1, fc.fw - 1, 2):
                    put(fc, x, fc.fh - 1, shade(c, 0.88))   # fingers


# ------------------------------------------------------------------------------------------------ metal

def plate(fc, c, rim=None, rivets=False, scratches=0, dents=0, key='', engrave=None):
    """Polished plate: a light top bevel, a dark lower rim, a soft vertical sheen; scratches and dents for
    battle-worn steel; rivets in the corners."""
    c = col(c)
    for y in range(fc.fh):
        for x in range(fc.fw):
            g = 1.1 if y == 0 else (0.72 if y == fc.fh - 1 else 1.0 - 0.12 * (y / max(1, fc.fh - 1)))
            if x == 0 or x == fc.fw - 1:
                g *= 0.85
            sheen = 1.0 + 0.12 * max(0.0, 1.0 - abs(x - fc.fw * 0.35) / max(1.0, fc.fw * 0.18))
            put(fc, x, y, shade(c, g * sheen))
    if rim is not None:
        for x in range(fc.fw):
            put(fc, x, 0, col(rim))
    for i in range(scratches):
        x0 = int(h01('scr', key, fc.face, i) * fc.fw)
        y0 = int(h01('scr2', key, fc.face, i) * fc.fh)
        ln = 2 + int(h01('scr3', key, i) * 3)
        for j in range(ln):
            blend(fc, x0 + j, y0 + (j // 2), lighten(c, 0.45), 0.6)
    for i in range(dents):
        x0 = int(h01('dent', key, fc.face, i) * (fc.fw - 2)) + 1
        y0 = int(h01('dent2', key, fc.face, i) * (fc.fh - 2)) + 1
        tint(fc, x0, y0, 0.78)
        tint(fc, x0 + 1, y0, 0.86)
        tint(fc, x0, y0 + 1, 1.12)
    if rivets and fc.fw >= 6 and fc.fh >= 4:
        for (x, y) in ((1, 1), (fc.fw - 2, 1)):
            put(fc, x, y, lighten(c, 0.4))
            put(fc, x, y + 1, shade(c, 0.6))
    if engrave is not None:
        for (x, y) in engrave:
            put(fc, x, y, shade(c, 0.6))
            blend(fc, x + 1, y + 1, lighten(c, 0.3), 0.5)


def plate_part(p, part, c, index=0, **kw):
    for fc in faces(p, part, index):
        plate(fc, c, key=part, **kw)
