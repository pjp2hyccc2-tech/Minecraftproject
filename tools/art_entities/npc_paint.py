"""Painting helpers for the NPC rig (rig_npc.py): faces with matching eyelid/mouth planes, hair shells, clothing
decals and prop details. All drawing goes through erpaint.FaceCanvas in face-local pixels (x right, y down, seen from
outside the box). Deterministic: no randomness except erpaint's seeded material noise.

Face layout on the 8x8 head front (x from the viewer's left, which is the character's right):
  row 0-2 forehead (bangs), brows one row above the eyes, eyes on `row` (default 4; 'tall' eyes use row-1 and row),
  nose on row+1, mouth on `mouth` (default row+2), chin on row 7.
"""
from erpaint import FaceCanvas, lighten, mix, rgb, shade

LASH = (40, 28, 30)
# per-column brightness of hair strands
STRAND = (1.0, 0.9, 1.06, 0.95, 1.1, 0.88, 1.02, 0.97, 1.08, 0.92, 1.04)


def col(c):
    """'#rrggbb' or (r, g, b) -> (r, g, b)."""
    if isinstance(c, str):
        return rgb(c)
    return tuple(int(v) for v in c[:3])


def faces_of(p, part, index=0, sides_only=False, glow=False):
    return p.all_faces(part, index=index, sides_only=sides_only, glow=glow)


def clear_part(p, part, index=0):
    for fc in faces_of(p, part, index):
        for y in range(fc.h):
            for x in range(fc.w):
                fc.clear(x, y)


def fill_face(fc, c, shade_rows=True):
    c = col(c)
    for y in range(fc.h):
        f = 1.04 - 0.12 * (y / max(1, fc.h - 1)) if shade_rows else 1.0
        for x in range(fc.w):
            fc.px(x, y, shade(c, f))


def tint_face(fc, c, t, mask=None):
    c = col(c)
    for y in range(fc.h):
        for x in range(fc.w):
            if mask is None or mask(x, y):
                if fc.img[fc.v + y, fc.u + x, 3] > 0:
                    fc.px(x, y, mix(fc.get(x, y), c, t))


# --------------------------------------------------------------------------------------------------- faces

class Face:
    """Describes a face; paint() draws it on the head and the matching closed eyes / open mouth on the planes."""

    def __init__(self, skin, eye='#3a2a20', style='normal', row=4, brows=None, brow_style='flat', nose='small',
                 mouth='line', mouth_row=None, lips=None, blush=False, freckles=False, stubble=None, wrinkles=False,
                 white=(240, 238, 232), lash=None, glow_eyes=None, mouth_w=2, scar=None, beauty_mark=False,
                 cheek=None, teeth=False, brow_dy=0):
        self.skin = col(skin)
        self.eye = col(eye)
        self.style = style
        self.row = row
        self.brows = col(brows) if brows is not None else None
        self.brow_style = brow_style
        self.nose = nose
        self.mouth = mouth
        self.mouth_row = mouth_row if mouth_row is not None else row + 2
        self.lips = col(lips) if lips is not None else None
        self.blush = blush
        self.freckles = freckles
        self.stubble = col(stubble) if stubble is not None else None
        self.wrinkles = wrinkles
        self.white = col(white)
        self.lash = col(lash) if lash is not None else shade(self.skin, 0.38)
        self.glow_eyes = col(glow_eyes) if glow_eyes is not None else None
        self.mouth_w = mouth_w
        self.scar = scar
        self.beauty_mark = beauty_mark
        self.cheek = cheek
        self.teeth = teeth
        self.brow_dy = brow_dy      # moves the brows up (negative), e.g. above glasses
        self.eye_px = []      # (x, y, kind) painted eye pixels, for the eyelid plane

    # ---------------------------------------------------------------- eyes
    def _eyes(self, f, g):
        r = self.row
        iris = self.eye
        pupil = shade(iris, 0.55)
        st = self.style
        pts = []
        if st in ('normal', 'stern'):
            # white outside, iris inside (towards the nose)
            pts += [(1, r, self.white), (2, r, iris), (5, r, iris), (6, r, self.white)]
            if st == 'stern':
                pts = [(x, y, shade(c, 0.85)) for (x, y, c) in pts]
        elif st == 'tall':
            pts += [(1, r - 1, self.lash), (2, r - 1, pupil), (1, r, self.white), (2, r, iris),
                    (5, r - 1, pupil), (6, r - 1, self.lash), (5, r, iris), (6, r, self.white)]
        elif st == 'round':
            # big round eyes (children): highlight in the top outer pixel
            pts += [(1, r - 1, lighten(iris, 0.75)), (2, r - 1, pupil), (1, r, iris), (2, r, shade(iris, 0.8)),
                    (5, r - 1, pupil), (6, r - 1, lighten(iris, 0.75)), (5, r, shade(iris, 0.8)), (6, r, iris)]
        elif st == 'sleepy':
            # half-lidded: heavy lid line above a narrow iris
            pts += [(1, r - 1, self.lash), (2, r - 1, self.lash), (5, r - 1, self.lash), (6, r - 1, self.lash),
                    (1, r, mix(self.white, iris, 0.35)), (2, r, iris), (5, r, iris), (6, r, mix(self.white, iris, 0.35))]
        elif st == 'squint':
            # smiling closed eyes (an arch)
            d = shade(self.skin, 0.42)
            pts += [(1, r, d), (2, r - 1, d), (5, r - 1, d), (6, r, d)]
        elif st == 'narrow':
            pts += [(1, r, shade(iris, 0.7)), (2, r, iris), (5, r, iris), (6, r, shade(iris, 0.7))]
        elif st == 'glow':
            pts += [(1, r, iris), (2, r, lighten(iris, 0.5)), (5, r, lighten(iris, 0.5)), (6, r, iris)]
        for (x, y, c) in pts:
            f.px(x, y, c)
            self.eye_px.append((x, y))
            if g is not None and self.glow_eyes is not None:
                g.px(x, y, self.glow_eyes)

    def _brows(self, f):
        if self.brows is None:
            return
        r = self.row - (2 if self.style in ('tall', 'round', 'sleepy', 'squint') else 1) + self.brow_dy
        b = self.brows
        bs = self.brow_style
        if bs == 'flat':
            for x in (1, 2, 5, 6):
                f.px(x, r, b)
        elif bs == 'angry':
            f.px(1, r - 1, b)
            f.px(2, r, b)
            f.px(5, r, b)
            f.px(6, r - 1, b)
            f.px(1, r, shade(b, 1.25))
            f.px(6, r, shade(b, 1.25))
        elif bs == 'raised':
            f.px(1, r, b)
            f.px(2, r - 1, b)
            f.px(5, r - 1, b)
            f.px(6, r, b)
        elif bs == 'thick':
            for x in (0, 1, 2, 5, 6, 7):
                f.px(x, r, b)
            f.px(2, r + 0, shade(b, 0.8))
            f.px(5, r + 0, shade(b, 0.8))
        elif bs == 'worried':
            f.px(1, r, b)
            f.px(2, r - 1, b)
            f.px(5, r - 1, b)
            f.px(6, r, b)
            f.px(3, r - 1, shade(self.skin, 0.88))
            f.px(4, r - 1, shade(self.skin, 0.88))

    def _nose(self, f):
        r = self.row + 1
        s = self.skin
        if self.nose == 'small':
            f.px(3, r, shade(s, 0.95))
            f.px(4, r, shade(s, 0.86))
        elif self.nose == 'dot':
            f.px(4, r, shade(s, 0.84))
        elif self.nose == 'big':
            f.px(3, r - 1, shade(s, 0.92))
            f.px(4, r - 1, shade(s, 0.86))
            f.px(3, r, mix(s, (200, 90, 80), 0.25))
            f.px(4, r, mix(shade(s, 0.85), (200, 90, 80), 0.25))
            f.px(2, r, shade(s, 0.86))
            f.px(5, r, shade(s, 0.8))
        elif self.nose == 'long':
            f.px(3, r - 1, shade(s, 0.93))
            f.px(4, r - 1, shade(s, 0.85))
            f.px(4, r, shade(s, 0.78))
            f.px(3, r, shade(s, 0.9))

    def _mouth(self, f):
        m = self.mouth_row
        dark = mix(shade(self.skin, 0.62), (130, 50, 55), 0.35) if self.lips is None else self.lips
        cx0 = 4 - self.mouth_w // 2
        cx1 = cx0 + self.mouth_w - 1
        md = self.mouth
        if md == 'none':
            return
        if md in ('line', 'flat'):
            f.hline(cx0, cx1, m, dark)
        elif md == 'smile':
            f.hline(cx0, cx1, m, dark)
            f.px(cx0 - 1, m - 1, shade(dark, 1.15))
            f.px(cx1 + 1, m - 1, shade(dark, 1.15))
        elif md == 'grin':
            f.hline(cx0 - 1, cx1 + 1, m, dark)
            f.px(cx0 - 1, m - 1, shade(dark, 1.1))
            f.px(cx1 + 1, m - 1, shade(dark, 1.1))
            if self.teeth:
                f.hline(cx0, cx1, m, (236, 230, 220))
                f.hline(cx0, cx1, m + 1, shade(dark, 0.9)) if m + 1 < f.h else None
        elif md == 'smirk':
            f.hline(cx0, cx1, m, dark)
            f.px(cx1 + 1, m - 1, dark)
        elif md == 'frown':
            f.hline(cx0, cx1, m, dark)
            f.px(cx0 - 1, m + 1, shade(dark, 1.1)) if m + 1 < f.h else None
            f.px(cx1 + 1, m + 1, shade(dark, 1.1)) if m + 1 < f.h else None
        elif md == 'lips':
            f.hline(cx0, cx1, m, dark)
            f.hline(cx0, cx1, m + 1, lighten(dark, 0.18)) if m + 1 < f.h else None
        elif md == 'small':
            f.px(4, m, dark)
        elif md == 'o':
            f.px(3, m, dark)
            f.px(4, m, dark)

    def _extras(self, f):
        r = self.row
        s = self.skin
        if self.blush:
            pink = mix(s, (240, 110, 120), 0.42)
            f.px(1, r + 1, pink)
            f.px(6, r + 1, pink)
        if self.cheek is not None:
            c = col(self.cheek)
            f.px(1, r + 1, mix(s, c, 0.5))
            f.px(6, r + 1, mix(s, c, 0.5))
        if self.freckles:
            fr = shade(s, 0.78)
            for (x, y) in ((1, r + 1), (2, r + 2), (6, r + 1), (5, r + 2), (0, r + 2), (7, r + 2)):
                if y < f.h:
                    f.px(x, y, mix(f.get(x, y), fr, 0.7))
        if self.wrinkles:
            w = shade(s, 0.8)
            for (x, y) in ((0, r), (7, r), (1, r + 1), (6, r + 1), (2, r + 3), (5, r + 3)):
                if y < f.h:
                    f.px(x, y, mix(f.get(x, y), w, 0.8))
        if self.stubble is not None:
            st = self.stubble
            m = self.mouth_row
            for y in range(m - 1, f.h):
                for x in range(f.w):
                    if (y == m - 1 and x not in (2, 3, 4, 5)) or y > m - 1:
                        if (x + y) % 2 == 0 or y >= m + 1:
                            if not (y == m and 3 <= x <= 4):
                                f.px(x, y, mix(f.get(x, y), st, 0.45))
        if self.scar:
            sc = mix(s, (210, 120, 110), 0.5)
            for (x, y) in self.scar:
                f.px(x, y, sc)
        if self.beauty_mark:
            f.px(6, r + 2, shade(s, 0.5))

    def paint(self, p):
        f = p.face('head', 'front')
        g = p.face('head', 'front', glow=True) if self.glow_eyes is not None else None
        self.eye_px = []
        self._extras(f)
        self._eyes(f, g)
        self._brows(f)
        self._nose(f)
        self._mouth(f)
        # side of the head: a hint of an ear
        for side in ('right', 'left'):
            fc = p.face('head', side)
            ex = 3 if side == 'right' else 4
            fc.px(ex, self.row, shade(self.skin, 0.86))
            fc.px(ex, self.row + 1, shade(self.skin, 0.8))
        self.paint_lids(p)
        self.paint_open_mouth(p)

    def paint_lids(self, p):
        """Closed eyes on the eyelid plane (shown during a blink)."""
        clear_part(p, 'eyelids')
        fc = p.face('eyelids', 'front')
        rows = sorted({y for (_, y) in self.eye_px})
        if not rows:
            return
        bottom = rows[-1]
        for (x, y) in self.eye_px:
            if self.style == 'glow':
                fc.px(x, y, shade(self.skin, 0.55))
            elif y == bottom:
                fc.px(x, y, mix(shade(self.skin, 0.62), self.lash, 0.55))
            else:
                fc.px(x, y, shade(self.skin, 0.93))

    def paint_open_mouth(self, p):
        """An open mouth on the mouth plane (rows 4..7 of the face; shown in flaps while talking)."""
        clear_part(p, 'mouth')
        fc = p.face('mouth', 'front')
        if self.mouth == 'none':
            return
        m = self.mouth_row - 4
        dark = (70, 26, 30)
        cx0 = 4 - max(2, self.mouth_w) // 2
        cx1 = cx0 + max(2, self.mouth_w) - 1
        fc.hline(cx0, cx1, m, dark)
        if m + 1 < fc.h:
            fc.hline(cx0, cx1, m + 1, (150, 60, 66))
            fc.px(cx0, m + 1, dark)
        if self.teeth or self.mouth in ('grin',):
            fc.hline(cx0, cx1, m, (232, 228, 220))


# ---------------------------------------------------------------------------------------------------- hair

def _hair_masks(style, bangs, back_len, side_front, long_len, part_x):
    """Boolean masks (8x8) per face of the hair cap: {face: set((x, y))}."""
    M = {k: set() for k in ('top', 'front', 'back', 'right', 'left', 'bottom')}
    if style == 'bald':
        return M
    for x in range(8):
        for y in range(8):
            M['top'].add((x, y))
    # front: bangs
    for x in range(8):
        depth = bangs
        if isinstance(bangs, (list, tuple)):
            depth = bangs[x]
        for y in range(depth):
            M['front'].add((x, y))
    # temples / face framing columns
    for y in range(min(8, long_len if style == 'long' else side_front)):
        M['front'].add((0, y))
        M['front'].add((7, y))
    # sides: x=0 is the back on the right face, x=7 the back on the left face
    for x in range(8):
        t = x / 7.0  # 0 at back (right face)
        depth_back = long_len if style == 'long' else back_len
        depth = int(round(depth_back + (side_front - depth_back) * t))
        for y in range(min(8, depth)):
            M['right'].add((x, y))
            M['left'].add((7 - x, y))
    for x in range(8):
        for y in range(min(8, long_len if style == 'long' else back_len)):
            M['back'].add((x, y))
    if part_x is not None:
        M['front'].discard((part_x, 0))
    return M


def hair(p, color, style='short', bangs=2, back_len=6, side_front=3, long_len=8, part_x=None, highlight=None,
         shell='hair_cap', inner=True, clear_top=False, extra=None, remove=None):
    """Paints the hair cap (and the head underneath) from masks. bangs: rows of fringe (int or per-column list)."""
    c = col(color)
    hl = col(highlight) if highlight is not None else lighten(c, 0.28)
    M = _hair_masks(style, bangs, back_len, side_front, long_len, part_x)
    if extra:
        for face, pts in extra.items():
            M[face] |= set(pts)
    if remove:
        for face, pts in remove.items():
            M[face] -= set(pts)
    if clear_top:
        M['top'] = set()
    for fname in ('top', 'front', 'back', 'right', 'left', 'bottom'):
        fc = p.face(shell, fname)
        for y in range(fc.h):
            for x in range(fc.w):
                if (x, y) in M[fname]:
                    if fname == 'top':
                        k = STRAND[(x * 3 + 1) % len(STRAND)]
                        f = 1.06 * k * (0.97 + 0.03 * (y % 2))
                    else:
                        k = STRAND[(x * 5 + (2 if fname in ('right', 'left') else 0)) % len(STRAND)]
                        f = k * (1.04 - 0.1 * (y / 7.0))
                    cc = shade(c, f)
                    if fname != 'top' and (x, y + 1) not in M[fname] and y + 1 < fc.h:
                        cc = shade(cc, 0.8)    # dark rim at the lower edge
                    if fname in ('front', 'right', 'left', 'back') and y == 0 and x % 3 == 1:
                        cc = mix(cc, hl, 0.6)
                    fc.px(x, y, cc)
                else:
                    fc.clear(x, y)
        if inner and fname != 'bottom':
            hc = p.face('head', fname)
            for (x, y) in M[fname]:
                if fname == 'front' and y >= 3:
                    continue
                hc.px(x, y, shade(c, 0.72 + 0.06 * ((x + y) % 2)))
    # a highlight band across the top front
    top = p.face(shell, 'top')
    for x in range(1, 7):
        if (x, 6) in M['top']:
            top.px(x, 6, mix(top.get(x, 6), hl, 0.45))
    return M


def bald_head(p, skin, fringe=None, fringe_rows=(4, 6), shine=True):
    """Bald head: shine on top; optional short fringe on the sides and back of the head box."""
    s = col(skin)
    top = p.face('head', 'top')
    if shine:
        top.px(3, 5, lighten(s, 0.35))
        top.px(4, 5, lighten(s, 0.25))
        top.px(3, 4, lighten(s, 0.2))
    if fringe is not None:
        fcol = col(fringe)
        y0, y1 = fringe_rows
        for side in ('right', 'left', 'back'):
            fc = p.face('head', side)
            for y in range(y0, y1 + 1):
                for x in range(fc.w):
                    if side == 'right' and x > 5:
                        continue
                    if side == 'left' and x < 2:
                        continue
                    fc.px(x, y, shade(fcol, 0.9 + 0.1 * ((x + y) % 2)))


def strand_texture(fc, color, dark_every=3, ragged=False, braid=False):
    c = col(color)
    for y in range(fc.h):
        for x in range(fc.w):
            f = 1.0 + 0.08 * (((x * 5 + y) % 3) - 1)
            if braid:
                f = 1.12 if (x + y) % 3 == 0 else (0.82 if (x + y) % 3 == 2 else 1.0)
            elif x % dark_every == 0:
                f *= 0.86
            fc.px(x, y, shade(c, f * (1.04 - 0.1 * y / max(1, fc.h - 1))))
    if ragged:
        for x in range(fc.w):
            if (x * 3) % 4 == 1:
                fc.clear(x, fc.h - 1)


def hair_piece(p, part, color, braid=False, index=0, ragged=False):
    for fc in faces_of(p, part, index):
        strand_texture(fc, color, braid=braid, ragged=ragged and fc.face not in ('top', 'bottom'))


# ------------------------------------------------------------------------------------------------- clothing

def torso_part(p):
    return 'torso_broad' if p.visible(p.rig.parts['torso_broad']) else 'torso'


def arm_parts(p, side):
    """(upper, fore) part names of the visible arm build."""
    s = 'r' if side == 'right' else 'l'
    slim = p.visible(p.rig.parts[s + '_upper_slim'])
    return (s + '_upper_slim', s + '_fore_slim') if slim else (s + '_upper', s + '_fore')


def rows_color(p, part, y0, y1, c, faces=('front', 'right', 'back', 'left'), index=0, shade_rows=True):
    c = col(c)
    for fc in faces_of(p, part, index):
        if fc.face not in faces:
            continue
        for y in range(max(0, y0), min(fc.h, y1 + 1)):
            f = (1.04 - 0.1 * (y / max(1, fc.h - 1))) if shade_rows else 1.0
            for x in range(fc.w):
                fc.px(x, y, shade(c, f * (0.95 if x in (0, fc.w - 1) else 1.0)))


def bottom_color(p, part, c, index=0):
    fc = p.face(part, 'bottom', index=index)
    fill_face(fc, shade(col(c), 0.85), shade_rows=False)


def top_color(p, part, c, index=0):
    fc = p.face(part, 'top', index=index)
    fill_face(fc, col(c), shade_rows=False)


def hands(p, c, rows=2, sides=('right', 'left'), gloves=False):
    """Bottom rows of the forearms (and their bottom face) in skin or glove colour."""
    c = col(c)
    for side in sides:
        _, fore = arm_parts(p, side)
        rows_color(p, fore, 6 - rows, 5, c)
        bottom_color(p, fore, c)
        if gloves:
            rows_color(p, fore, 6 - rows - 1, 6 - rows - 1, lighten(c, 0.15))
        else:
            # a thumb hint on the front
            fc = p.face(fore, 'front')
            fc.px(0 if side == 'right' else fc.w - 1, 4, shade(c, 0.85))


def bare_arms(p, skin, sleeve_rows=0, sides=('right', 'left'), cuff=None):
    """Skin on the arms below `sleeve_rows` rows of the upper arm (0 = sleeveless)."""
    s = col(skin)
    for side in sides:
        upper, fore = arm_parts(p, side)
        rows_color(p, upper, sleeve_rows, 5, s)
        rows_color(p, fore, 0, 5, s)
        bottom_color(p, fore, s)
        if sleeve_rows == 0:
            top_color(p, upper, s)
        if cuff is not None and sleeve_rows > 0:
            rows_color(p, upper, sleeve_rows - 1, sleeve_rows - 1, cuff)
        # muscle shading
        fc = p.face(upper, 'front')
        for y in range(sleeve_rows, 6):
            fc.px(fc.w - 1 if side == 'right' else 0, y, shade(s, 0.88))


def boots(p, c, rows=3, top_trim=None, cuff_row=True, toe=True):
    c = col(c)
    for part in ('right_shin', 'left_shin'):
        rows_color(p, part, 6 - rows, 5, c)
        bottom_color(p, part, shade(c, 0.7))
        if cuff_row and rows < 6:
            rows_color(p, part, 6 - rows, 6 - rows, top_trim if top_trim is not None else lighten(c, 0.18))
        if toe:
            fc = p.face(part, 'front')
            fc.hline(0, fc.w - 1, 5, shade(c, 0.75))
            fc.hline(1, fc.w - 2, 4, lighten(c, 0.1))


def legs(p, c, style='cloth'):
    """Repaint both thighs and shins as trousers/stockings of colour c (keeps boots for a later call)."""
    c = col(c)
    for part in ('right_leg', 'left_leg', 'right_shin', 'left_shin'):
        rows_color(p, part, 0, 5, c)
    for part in ('right_leg', 'left_leg'):
        fc = p.face(part, 'front')
        fc.vline(fc.w - 1 if part == 'right_leg' else 0, 0, fc.h - 1, shade(c, 0.85))


def belt(p, y=0, c=(58, 38, 24), buckle=(208, 176, 80), part='hips', buckle_w=2):
    c = col(c)
    for fc in faces_of(p, part, sides_only=True):
        fc.hline(0, fc.w - 1, y, c)
        if y + 1 < fc.h:
            fc.hline(0, fc.w - 1, y + 1, shade(c, 0.78))
    if buckle is not None:
        f = p.face(part, 'front')
        x0 = f.w // 2 - buckle_w // 2
        f.rect(x0, y, x0 + buckle_w - 1, min(f.h - 1, y + 1), col(buckle))
        f.px(x0, y, lighten(col(buckle), 0.4))


def buttons(fc, x, ys, c=(210, 180, 90)):
    for y in ys:
        fc.px(x, y, col(c))


def vneck(fc, inner, depth=3, cx=None, skin_edge=None):
    """A V neckline at the top centre of a torso front face."""
    cx = fc.w // 2 if cx is None else cx
    inner = col(inner)
    for y in range(depth):
        half = depth - y
        for x in range(cx - half, cx + half):
            if 0 <= x < fc.w:
                fc.px(x, y, inner)
    if skin_edge is not None:
        fc.px(cx - 1, depth, col(skin_edge))
        fc.px(cx, depth, col(skin_edge))


def open_front(fc, inner, width=2, lapel=None, rows=None):
    """Open coat/vest: the centre columns show the shirt (inner)."""
    inner = col(inner)
    x0 = fc.w // 2 - width // 2
    y1 = fc.h - 1 if rows is None else rows
    for y in range(0, y1 + 1):
        for x in range(x0, x0 + width):
            fc.px(x, y, shade(inner, 1.0 - 0.05 * y / max(1, y1)))
    if lapel is not None:
        lp = col(lapel)
        for y in range(min(3, y1 + 1)):
            fc.px(x0 - 1, y, lp)
            fc.px(x0 + width, y, lp)


def sash(fc, c, slope=1, width=2, offset=0):
    """Diagonal band across a face (slope 1: from top-left to bottom-right)."""
    c = col(c)
    for y in range(fc.h):
        for k in range(width):
            x = (y + offset + k) if slope > 0 else (fc.w - 1 - y - offset - k)
            if 0 <= x < fc.w:
                fc.px(x, y, shade(c, 1.08 if k == 0 else 0.92))


def trim_row(p, part, y, c, faces=('front', 'right', 'back', 'left'), index=0):
    c = col(c)
    for fc in faces_of(p, part, index):
        if fc.face in faces and 0 <= y < fc.h:
            for x in range(fc.w):
                fc.px(x, y, shade(c, 1.0 + 0.08 * ((x % 2) * 2 - 1) * 0.5))


def trim_col(fc, x, c, y0=0, y1=None):
    y1 = fc.h - 1 if y1 is None else y1
    fc.vline(x, y0, y1, col(c))


def emblem(fc, pattern, c, cx=None, cy=None, glow=None):
    """pattern: list of (dx, dy) offsets around (cx, cy)."""
    cx = fc.w // 2 if cx is None else cx
    cy = fc.h // 2 if cy is None else cy
    for (dx, dy) in pattern:
        fc.px(cx + dx, cy + dy, col(c))
        if glow is not None:
            glow.px(cx + dx, cy + dy, col(c))


STAR = [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]
DIAMOND = [(0, -2), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]
SHIELD = [(-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]
CROSS = [(0, -2), (0, -1), (-1, 0), (0, 0), (1, 0), (0, 1), (0, 2)]


def mask_alpha(fc, keep):
    """Clears every pixel where keep(x, y) is false."""
    for y in range(fc.h):
        for x in range(fc.w):
            if not keep(x, y):
                fc.clear(x, y)


def circle_mask(w, h, inset=0.0):
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    rx, ry = w / 2.0 - inset, h / 2.0 - inset

    def keep(x, y):
        return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0
    return keep


def glow_fill(p, part, c, index=0, faces=None):
    for fc in faces_of(p, part, index, glow=True):
        if faces is not None and fc.face not in faces:
            continue
        for y in range(fc.h):
            for x in range(fc.w):
                fc.px(x, y, col(c))


def shell_face_opening(p, part='shell', left=1, right=1, top=2, bottom=0, rim=None):
    """Cuts the front of a head shell so the face shows (hoods, open helmets)."""
    fc = p.face(part, 'front')
    for y in range(top, fc.h - bottom):
        for x in range(left, fc.w - right):
            fc.clear(x, y)
    if rim is not None:
        r = col(rim)
        for y in range(top, fc.h - bottom):
            if left > 0:
                fc.px(left - 1, y, r)
            if right > 0:
                fc.px(fc.w - right, y, r)
        if top > 0:
            for x in range(max(0, left - 1), min(fc.w, fc.w - right + 1)):
                fc.px(x, top - 1, r)


def shell_rows(p, part, rows_front, rows_side, rows_back, top=True):
    """Keeps only the upper rows of a head shell (caps, bands, wraps); the rest becomes transparent."""
    for fc in faces_of(p, part):
        if fc.face == 'top':
            if not top:
                mask_alpha(fc, lambda x, y: False)
            continue
        if fc.face == 'bottom':
            mask_alpha(fc, lambda x, y: False)
            continue
        n = {'front': rows_front, 'back': rows_back}.get(fc.face, rows_side)
        if callable(n):
            mask_alpha(fc, n)
        else:
            mask_alpha(fc, lambda x, y, n=n: y < n)


def band_rows(p, rows, c, part='band', pattern=None):
    """A headband on the band shell: only `rows` are opaque on the four sides; pattern(fc, x, y) may recolour."""
    c = col(c)
    for fc in faces_of(p, part):
        if fc.face in ('top', 'bottom'):
            mask_alpha(fc, lambda x, y: False)
            continue
        for y in range(fc.h):
            for x in range(fc.w):
                if y in rows:
                    fc.px(x, y, shade(c, 1.06 if y == rows[0] else 0.9))
                    if pattern is not None:
                        pattern(fc, x, y)
                else:
                    fc.clear(x, y)


def page_lines(fc, ink=(70, 60, 60), paper=(236, 228, 206), margin=1, gap=2):
    fill_face(fc, paper, shade_rows=False)
    for y in range(1, fc.h - 1, gap):
        for x in range(margin, fc.w - margin):
            if (x * 7 + y * 3) % 5 != 0:
                fc.px(x, y, mix(paper, col(ink), 0.55))


def wood_grain(fc, c):
    c = col(c)
    for y in range(fc.h):
        for x in range(fc.w):
            fc.px(x, y, shade(c, 1.0 + 0.08 * (((x + 2 * y) % 4) - 1.5) / 1.5))
