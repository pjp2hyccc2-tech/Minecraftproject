"""洋室 / 廊下 / 玄関: the desk with the laptop (closed / open / on: the camera app glowing), the sewing box, the forum
printout, the pipe bed (head / foot), the colour-box shelf with the plush rabbit on top, the camera bag, a poster,
the fluorescent desk lamp; the shoe cabinet, shoes, a wet vinyl umbrella, the breaker panel, the intercom, the
November calendar and the wall clock (its second hand ticks). Desk top = 12 px: things on the desk start at y -4."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, fnoise, texture
from hk_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_brushed, m_plastic, m_wood, m_fabric, text35, text_mask,
                    stamp_mask, stamp_center, bitmap, FONT_IPAG)
import hk_mats as M
from hk_common import (STAINLESS, STEELW, CREAMPL, GREYPL, CHARCOAL, PAPER, PALEWOOD, TEAK, DARKWOOD, solid, lit, mat,
                       painted, woodmat, disc_strips, cord, register_anim, ref)

WOODGRAIN = ramp(['#4e3a24', '#62492e', '#765838', '#896742', '#9b774c', '#ab8656', '#b99461'], 7)   # print veneer
# the student desk's and the shoe cabinet's printed laminate: the same browns in finer steps, so the grain reads as
# grain and the panels do not break into two-tone patches
WOODLAM = ramp(['#5a432a', '#665032', '#725a3a', '#7e6442', '#8a6e4a', '#967852', '#a2825a', '#ae8c62'], 8)


# ================================================================================================ desk, chair
A_DESK = Atlas('desk')


def _desk_drawers(w, h, face):
    a = m_wood(w, h, WOODLAM, 9001 + w, along='x', base=3.0, amp=0.3, lines=0.7)
    bevel(a, 0.05, -0.07)
    if w >= 5 and h >= 9:
        for yy in (3, 7):
            hline(a, 0, w - 1, yy, WOODGRAIN[1])
        for yy in (1, 5, 9):
            if yy < h:
                px(a, w // 2, yy, '#c8ccce')
    return a


def desk():
    m = Model('desk', A_DESK, display=DISP_FRONT)
    TOP = woodmat('desk_top', WOODLAM, base=5.0, amp=0.35, wear_amt=0.05)
    SIDE = woodmat('desk_side', WOODLAM, base=4.0, amp=0.3)
    m.box([0, 11, 1], [16, 12, 15], TOP)
    m.box([0.5, 0, 2], [1.5, 11, 14.5], SIDE)                                  # left panel leg (model west)
    m.box([10, 0, 2], [15.5, 11, 14.5], SIDE, faces={'north': M.custom('desk_drawers', _desk_drawers)})
    m.box([1.5, 6, 13.5], [10, 11, 14.2], SIDE)                                 # modesty panel
    return m


desk()

A_DCH = Atlas('desk_chair')


def desk_chair():
    """Cheap swivel chair: black fabric seat and back, gas lift, five-star base. Faces north (backrest south)."""
    m = Model('desk_chair', A_DCH, display=DISP_FRONT)
    FAB = M.fabric('dch_fab', ramp(['#141416', '#1c1c20', '#25252a', '#2e2e34', '#38383e'], 5), base=2.4)
    BASE = solid('dch_base', '#1a1a1c')
    m.box([3.5, 6.5, 3.5], [12.5, 8, 12.5], FAB)
    m.box([4, 9, 12.4], [12, 16, 13.6], FAB)
    m.box([7.3, 8, 12.6], [8.7, 9.5, 13.4], BASE)
    m.box([7.4, 1.5, 7.4], [8.6, 6.5, 8.6], M.brushed('dch_lift', STEELW, base=4.0))
    for (x0, z0, x1, z1) in ((2.5, 7.5, 13.5, 8.5), (7.5, 2.5, 8.5, 13.5)):
        m.box([x0, 0.8, z0], [x1, 1.5, z1], BASE)
    for (cx, cz) in ((2.5, 8), (13.5, 8), (8, 2.5), (8, 13.5)):
        m.box([cx - 0.5, 0, cz - 0.5], [cx + 0.5, 0.8, cz + 0.5], BASE)
    return m


desk_chair()


# ================================================================================================ laptop (block)
A_LAP = Atlas('laptop_block')


def _lap_lid(w, h, face):
    """The closed lid seen from above: dark grey aluminium, scuffed, covered in stickers - a cat, a band logo, a
    university sticker, a peeling holo star."""
    a = m_brushed(w, h, ramp(['#2a2c30', '#34363a', '#3e4044', '#484a4e', '#525458'], 5), 9101 + w, along='x', base=2.0)
    if face == 'h' and w >= 8 and h >= 6:
        # cat (white, black ears)
        for (xx, yy, c) in ((1, 1, '#1a1a1a'), (3, 1, '#1a1a1a'), (1, 2, '#f0ece4'), (2, 2, '#f0ece4'),
                            (3, 2, '#f0ece4'), (1, 3, '#f0ece4'), (2, 3, '#f0ece4'), (3, 3, '#f0ece4')):
            px(a, xx, yy, c)
        rect(a, 5, 1, 7, 2, '#c83a6a')                      # band logo (pink)
        px(a, 6, 1, '#1a1a1a')
        rect(a, w - 3, h - 3, w - 2, h - 2, '#3a7ac8')      # university sticker
        px(a, 4, h - 2, '#e8d860')                          # holo star
        px(a, 5, h - 3, '#a8e8e0')
        px(a, w // 2, h // 2, '#8a8c90')                    # the dull logo in the middle
    return a


def _lap_keys(w, h, face):
    a = blank(w, h, '#3a3c40')
    if face == 'h':
        x, y = grid(w, h)
        keys = (y < h - 3) & ((x + (y % 2)) % 2 == 0) & (x > 0) & (x < w - 1) & (y > 0)
        a[keys] = C('#1c1c1e')
        rect(a, w // 2 - 1, h - 2, w // 2 + 1, h - 2, '#2a2c30')   # touchpad
    return a


def _lap_screen_frame(i, on=True):
    """The camera app on the laptop screen (2x density, 16 x 10): a grey-green IR feed of the bathtub, the window
    bar, REC blinking."""
    a = blank(16, 16, '#000000')
    if not on:
        a[:10, :16] = C('#141618')
        a[1, 2:6] = C('#2a2e32')
        return a
    r = rng(9201 + i)
    w, h = 16, 10
    x, y = grid(w, h)
    img = np.zeros((h, w, 3)) + np.array([70, 84, 76])
    # the tub seen from the camera: tiles (grid), the dark rectangle of water
    tiles = ((x % 3) == 0) | ((y % 3) == 0)
    img[tiles] = (54, 66, 60)
    img[(x >= 4) & (x <= 12) & (y >= 5)] = (24, 30, 28)
    img[(x >= 4) & (x <= 12) & (y == 5)] = (110, 126, 116)
    img += (r.random((h, w, 1)) - 0.5) * 22
    img[0] = (180, 184, 188)                       # title bar
    img[0, 13:15] = (200, 60, 50)
    img[h - 1] = (40, 44, 48)                       # status bar
    if i % 2 == 0:
        img[2, 1] = (255, 40, 30)                   # REC dot
    img[2, 2:4] = (220, 220, 220)
    a[:h, :w, :3] = np.clip(img, 0, 255)
    a[:h, :w, 3] = 255
    a[h:, :, :3] = 20
    a[h:, :, 3] = 255
    return a


register_anim('block/laptop_screen', (16, 16), 2, lambda i: _lap_screen_frame(i), frametime=16)


def laptop_block(open_, on):
    name = 'laptop_block' + ('_open' if open_ else '') + ('_on' if on else '')
    m = Model(name, A_LAP, display=DISP_SMALL)
    SHELL = M.brushed('lap_shell', ramp(['#2a2c30', '#34363a', '#3e4044', '#484a4e', '#525458'], 5), base=2.0)
    y0 = -4.0
    if not open_:
        m.box([3.5, y0, 4.5], [12.5, y0 + 1.0, 11.0], SHELL, faces={'up': M.custom('lap_lid', _lap_lid)})
        if on:
            m.box([11.5, y0 + 0.3, 4.4], [12.0, y0 + 0.6, 4.5], lit('lap_led', '#7affb0'), glow=('north',))
        return m
    m.box([3.5, y0, 4.5], [12.5, y0 + 0.6, 11.0], SHELL, faces={'up': M.custom('lap_keys', _lap_keys)})
    # the lid opened back ~110 degrees: tilted 22.5 degrees back about the hinge at the rear edge
    rot = ('x', 22.5, [8, y0 + 0.6, 11.0])
    m.box([3.5, y0 + 0.6, 11.0], [12.5, y0 + 7.0, 11.6], SHELL,
          faces={'south': M.custom('lap_lid_back', _lap_lid)}, rot=rot)
    m.extra_tex['screen'] = ref('block/laptop_screen')
    if on:
        m.box([4.0, y0 + 1.0, 10.95], [12.0, y0 + 6.5, 11.0], None,
              faces={'north': {'r': '#screen', 'sub': (0, 0, 16, 10)}}, glow=('north',), rot=rot)
    else:
        m.box([4.0, y0 + 1.0, 10.95], [12.0, y0 + 6.5, 11.0], None, faces={'north': solid('lap_off', '#141618')},
              rot=rot)
    return m


for _o in (False, True):
    for _n in (False, True):
        laptop_block(_o, _n)


# ================================================================================================ sewing box
A_SEW = Atlas('sewing_box')


def _rattan(w, h, face):
    x, y = grid(w, h)
    a = from_idx(3.0 + ((x + y) % 2) * 0.6 + noise(w, h, 9301 + w, beta=1.4, wrap=False) * 0.3,
                 ramp(['#4a3018', '#5e3e20', '#72502a', '#866034', '#98723e', '#a8824a'], 6))
    bevel(a, 0.05, -0.07)
    return a


def _sew_lid(w, h, face):
    """The quilted floral lid (faded pink flowers on cream)."""
    x, y = grid(w, h)
    a = blank(w, h, '#d8ccb4')
    fl = ((x * 2 + y * 3) % 5 == 0)
    a[fl] = C('#c87a80')
    dl(a, -0.05, (x % 3 == 0) & (y % 3 == 0))
    frame(a, 0, 0, w - 1, h - 1, '#7a5030')
    return a


def _sew_inside(w, h, face):
    """Inside seen from above: thread spools (the red one big and bright), the tomato pin cushion, scissors, a
    tape measure."""
    a = blank(w, h, '#3a2a1c')
    if w >= 6 and h >= 4:
        for (sx, sy, c) in ((1, 1, '#c81c1c'), (3, 1, '#e8e4dc'), (5, 1, '#2a2a6a'), (1, 3, '#c8a83a')):
            px(a, sx, sy, c)
            px(a, sx, sy + 1 if sy + 1 < h else sy, c)
        px(a, 2, 1, '#e8323a')
        rect(a, w - 3, h - 3, w - 2, h - 2, '#c83a2a')       # tomato
        px(a, w - 3, h - 3, '#3a8a3a')
        px(a, 3, h - 2, '#1a1a1a')                           # scissors handles
        px(a, 4, h - 2, '#1a1a1a')
    return a


def sewing_box(open_):
    name = 'sewing_box' + ('_open' if open_ else '')
    m = Model(name, A_SEW, display=DISP_SMALL)
    R = mat('rattan', _rattan)
    y0 = -4.0
    if not open_:
        m.box([4, y0, 5], [12, y0 + 3.5, 11], R)
        m.box([3.8, y0 + 3.5, 4.8], [12.2, y0 + 4.5, 11.2], R, faces={'up': M.custom('sew_lid', _sew_lid)})
    else:
        m.box([4, y0, 5], [12, y0 + 3.5, 11], R, faces={'up': M.custom('sew_in', _sew_inside)})
        # the lid propped open against the back
        m.box([3.8, y0 + 3.5, 11.0], [12.2, y0 + 9.5, 12.0], R, faces={'north': M.custom('sew_lid_in', _sew_lid)},
              rot=('x', 22.5, [8, y0 + 3.5, 11.0]))
        # the red thread spool and the scissors lying in front of the box
        m.box([2.0, y0, 6.0], [3.4, y0 + 1.4, 7.4], solid('spool_red', '#c81c1c'))
        m.box([2.3, y0 + 1.4, 6.3], [3.1, y0 + 1.6, 7.1], solid('spool_wood', '#c8a878'))
        m.box([12.5, y0, 7.0], [14.5, y0 + 0.4, 7.6], M.brushed('scis_blade', STEELW, base=4.6))
        m.box([12.5, y0, 7.6], [13.6, y0 + 0.5, 8.6], solid('scis_handle', '#1a1a1a'))
    return m


sewing_box(False)
sewing_box(True)


# ================================================================================================ printout (paper on the desk)
A_PRINT = Atlas('printout')


def _paper_top(w, h, face):
    """The forum printout lying on the desk (A4, 7 x 10): the title line, the numbered steps (grey lines), pencil
    ticks down the margin, a corner staple."""
    a = blank(w, h, '#ecebe4')
    if face == 'h':
        hline(a, 1, w - 2, 1, '#2a2a2a')
        for yy in range(3, h - 1):
            hline(a, 2, w - 2 - (yy % 3 == 0), yy, '#8a8a86')
            if yy % 2 == 1:
                px(a, 0, yy, '#c83a2a')
        px(a, 0, 0, '#a8acae')
    return a


def printout():
    m = Model('printout', A_PRINT, display=DISP_FLOOR)
    P = mat('print_paper', _paper_top)
    y0 = -4.0
    m.box([4.4, y0, 3.4], [11.4, y0 + 0.15, 13.4], solid('print_under', '#d8d6cc'),
          rot=('y', 22.5, [8, y0, 8]))
    m.box([4.5, y0 + 0.15, 3.5], [11.5, y0 + 0.3, 13.5], P)
    m.box([9.8, y0 + 0.3, 5.0], [12.6, y0 + 0.75, 5.5], solid('pencil', '#d8a83a'),
          rot=('y', -22.5, [11, y0, 5]))
    return m


printout()


# ================================================================================================ bed
A_BED = Atlas('bed')
DUVET = ramp(['#2a3442', '#344052', '#3e4c62', '#4a5a72', '#566880', '#62768e'], 6)


def _duvet(w, h, face):
    """Navy plaid duvet cover, rumpled."""
    x, y = grid(w, h)
    a = from_idx(2.8 + noise(w, h, 9501 + w * 3 + h, beta=1.5, wrap=False) * 0.45, DUVET)
    plaid = ((x % 6) == 0) | ((y % 6) == 0)
    tint(a, '#8a6a3a', 0.3, plaid)
    tint(a, '#c8b070', 0.2, ((x % 6) == 0) & ((y % 6) == 0))
    if face != 'h':
        dl(a, 0.04, y == 0)
    return a


def _mattress(w, h, face):
    a = blank(w, h, '#d8d4c8')
    if face != 'h':
        hline(a, 0, w - 1, 0, '#e8e4d8')
        hline(a, 0, w - 1, h - 1, '#a8a49a')
    return a


def bed(part):
    """Black pipe-frame single bed. head: the north block (headboard, pillow); foot: the south block."""
    name = f'bed_{part}'
    m = Model(name, A_BED, display=DISP_FRONT)
    PIPE = solid('bed_pipe', '#1c1c1e')
    DU = mat('duvet', _duvet)
    MA = mat('mattress', _mattress)
    m.box([1, 3, 0], [15, 6, 16], MA)
    m.box([1, 2.4, 0], [15, 3, 16], solid('bed_slats', '#5a4a3a'))
    for x0 in (0.5, 14.5):
        m.box([x0, 2.0, 0], [x0 + 1, 3.0, 16], PIPE)
    if part == 'head':
        for x0 in (0.5, 14.5):
            m.box([x0, 0, 0.5], [x0 + 1, 12, 1.5], PIPE)
        for yy in (6.5, 11):
            m.box([1.5, yy, 0.6], [14.5, yy + 1, 1.4], PIPE)
        for xx in range(4, 13, 3):
            m.box([xx, 7.5, 0.7], [xx + 0.6, 11, 1.3], PIPE)
        # pillow and the duvet pulled up, thrown back at one corner
        m.box([3, 6, 1.6], [13, 8, 5.5], mat('pillow', lambda w, h, f: from_idx(
            3.6 + noise(w, h, 9511, beta=1.5, wrap=False) * 0.3, PAPER)))
        m.box([1.2, 6, 6.5], [14.8, 7.6, 16], DU)
        m.box([8, 7.6, 7], [14.8, 8.4, 11], DU)
    else:
        for x0 in (0.5, 14.5):
            m.box([x0, 0, 14.5], [x0 + 1, 8, 15.5], PIPE)
        m.box([1.5, 7, 14.6], [14.5, 8, 15.4], PIPE)
        m.box([1.2, 6, 0], [14.8, 7.6, 14.0], DU)
        m.box([0.8, 3.5, 2], [1.2, 7.6, 13], DU)                    # the duvet hanging over the side
        m.box([14.8, 4.5, 4], [15.2, 7.6, 12], DU)
    return m


bed('head')
bed('foot')
child('bed', 'bed_head')


# ================================================================================================ shelf + plush
A_SHELF = Atlas('shelf')


def _shelf_front(w, h, face):
    """3-tier colour box (12 x 16): white melamine, manga and books, CDs, a small cactus, a photo frame face down."""
    a = blank(w, h, '#1a1612')
    frame(a, 0, 0, w - 1, h - 1, '#e2ddd0')
    for yy in (5, 10):
        hline(a, 0, w - 1, yy, '#e2ddd0')
    r = rng(9601)
    cols = ['#c83a2a', '#2a5ac8', '#e8d860', '#3a8a4a', '#e8e4dc', '#8a3a8a', '#d86a2a', '#1a1a1a', '#5ac8c8']
    for (y0, y1, fill) in ((1, 4, 0.8), (6, 9, 1.0), (11, 15, 0.6)):
        x = 1
        while x < w - 1:
            if r.random() < fill:
                c = cols[int(r.integers(0, len(cols)))]
                top = y0 + int(r.integers(0, 2))
                vline(a, x, top, y1, c)
                px(a, x, top, '#f0ece4' if c != '#e8e4dc' else '#8a8678')
            x += 1
    # top tier right: a little cactus in a pot
    px(a, w - 3, 3, '#3a7a3a')
    px(a, w - 3, 2, '#4a9a4a')
    px(a, w - 3, 4, '#a85a3a')
    return a


def shelf():
    m = Model('shelf', A_SHELF, display=DISP_FRONT)
    SIDE = painted('shelf_side', ramp(['#a8a498', '#bcb8ac', '#ccc8bc', '#dad6ca', '#e4e0d4'], 5), base=3.0, amp=0.2)
    m.box([2, 0, 4], [14, 16, 12], SIDE, faces={'north': M.custom('shelf_front', _shelf_front)})
    return m


shelf()

A_PLUSH = Atlas('plush_doll')
PLUSHW = ramp(['#8e8a82', '#aaa69e', '#c2beb6', '#d4d0c8', '#e2dfd8', '#ecebe6'], 6)


def _plush(w, h, face):
    r = rng(9701 + w * 7 + h)
    a = from_idx(4.0 + noise(w, h, 9702 + w + h, beta=0.8, wrap=False) * 0.4, PLUSHW)
    dl(a, 0.03, r.random((h, w)) < 0.18)
    return a


def _plush_face(w, h, face):
    a = _plush(w, h, face)
    if w >= 4 and h >= 4:
        # black button eyes with a glint, the stitched pink nose and mouth
        px(a, 1, 1, '#121212')
        px(a, w - 2, 1, '#121212')
        px(a, 1, 0, '#6a6a6a') if h > 4 else None
        px(a, w // 2, 2, '#d88a90')
        px(a, w // 2 - 1, 3, '#a87a80')
        px(a, w // 2, 3, '#a87a80')
    return a


def plush_doll(present):
    name = 'plush_doll' + ('_present' if present else '')
    m = Model(name, A_PLUSH, display=DISP_SMALL)
    if not present:
        # the clean spot in the dust where it sat for years
        m.box([5.5, 0.01, 6.5], [10.5, 0.03, 10.5], None, faces={'up': M.custom('plush_spot', _dust_spot)})
        return m
    W = mat('plush_white', _plush)
    PINK = solid('plush_pink', '#d8a0a4')
    m.box([5.5, 0, 6.5], [10.5, 4.5, 10.5], W)                                   # sitting body
    m.box([5.0, 0, 5.0], [7.0, 1.6, 7.5], W)                                     # legs stuck out in front
    m.box([9.0, 0, 5.0], [11.0, 1.6, 7.5], W)
    m.box([4.5, 1.5, 7.5], [5.5, 4.0, 9.0], W)                                   # arms
    m.box([10.5, 1.5, 7.5], [11.5, 4.0, 9.0], W)
    m.box([5.5, 4.5, 6.0], [10.5, 8.5, 10.0], W, faces={'north': M.custom('plush_face', _plush_face)})
    m.box([6.0, 8.5, 7.5], [7.2, 12.5, 8.5], W, faces={'north': PINK})            # ears up
    m.box([8.8, 8.5, 7.5], [10.0, 12.5, 8.5], W, faces={'north': PINK})
    m.box([7.6, 5.4, 5.85], [8.4, 5.9, 6.0], PINK, only=('north',))
    return m


def _dust_spot(w, h, face):
    """A paler, dust-free shape on the shelf top where the plush sat (translucent pale)."""
    a = blank(w, h, C('#f4f2ec', 0))
    m = disc_mask(w, h, w / 2, h / 2, w * 0.45, h * 0.42)
    a[m] = C('#f0eee6', 70)
    return a


plush_doll(True)
plush_doll(False)
from hk_kit import MODELS as _MODELS  # noqa: E402
_MODELS['plush_doll'].render_type = 'translucent'


# ================================================================================================ camera bag, poster, desk lamp
A_CBAG = Atlas('camera_bag')


def camera_bag():
    """Black nylon camera bag, flap thrown open, two little action cams inside and one lying beside it."""
    m = Model('camera_bag', A_CBAG, display=DISP_SMALL)
    NY = M.fabric('cbag_nylon', ramp(['#0e0e10', '#16161a', '#1e1e22', '#28282c', '#323238'], 5), base=2.4)
    m.box([3.5, 0, 5], [12.5, 6, 11], NY)
    m.box([3.3, 4.5, 11.0], [12.7, 6.2, 13.5], NY, rot=('x', 22.5, [8, 6, 11]))   # flap thrown back
    m.box([4.0, 6, 5.5], [12.0, 6.1, 10.5], solid('cbag_in', '#2a2a30'), only=('up',))
    for x0 in (5.0, 8.4):
        m.box([x0, 5.0, 6.5], [x0 + 2.4, 7.4, 8.4], solid('cam_body', '#1a1a1c'),
              faces={'north': M.custom('cam_lens', _cam_lens)})
    m.box([12.8, 0, 7], [15.2, 2.4, 8.8], solid('cam_body', '#1a1a1c'), faces={'north': M.custom('cam_lens', _cam_lens)})
    # the shoulder strap lying on the floor
    m.box([1.0, 0, 6.0], [3.5, 0.3, 6.8], solid('cbag_strap', '#2a2a30'))
    m.box([1.0, 0, 6.8], [1.8, 0.3, 12.0], solid('cbag_strap', '#2a2a30'))
    return m


def _cam_lens(w, h, face):
    a = blank(w, h, '#1a1a1c')
    if w >= 2 and h >= 2:
        px(a, w // 2, h // 2, '#3a4a5a')
        px(a, 0, 0, '#c8281c')
    return a


camera_bag()

A_POSTER = Atlas('poster')


def _poster_art(w, h):
    """A cheap concert poster (10 x 14): a night-blue print, a moon, the silhouette of a girl with a guitar, a
    title band in red, the edges sun-faded, one corner peeling."""
    x, y = grid(w, h)
    a = blank(w, h, '#1e2a4a')
    g = y / h
    a[..., :3] = a[..., :3] * (1 - g[..., None] * 0.4) + np.array([90, 40, 60]) * g[..., None] * 0.4
    a[disc_mask(w, h, w * 0.7, h * 0.22, 1.8)] = C('#e8dcb0')
    for (xx, yy) in ((4, 6), (4, 7), (4, 8), (3, 9), (5, 9), (4, 5), (3, 10), (5, 10), (4, 11)):
        px(a, xx, yy, '#0a0c14')
    line(a, 5, 8, 7, 10, '#0a0c14')
    rect(a, 1, h - 3, w - 2, h - 2, '#c8281c')
    hline(a, 2, w - 3, h - 2, '#f0d8b0')
    tint(a, '#c8b890', 0.15)
    px(a, 0, 0, '#d8d4c8')
    px(a, 1, 0, '#c8c4b8')
    px(a, 0, 1, '#c8c4b8')
    return a


def poster():
    A_POSTER.add('art', 10, 14, _poster_art)
    m = Model('poster', A_POSTER, display=DISP_WALL)
    m.box([3, 1, 15.8], [13, 15, 16], solid('poster_back', '#d8d4c8'), faces={'north': 'art'})
    m.box([12.0, 13.6, 15.4], [13.0, 15.0, 15.8], solid('poster_curl', '#d8d4c8'), rot=('y', 22.5, [13, 14, 15.8]))
    return m


poster()

A_DLAMP = Atlas('desk_lamp')


def desk_lamp(lit_):
    """Showa fluorescent study lamp (学習スタンド): a weighted base, a bent arm, a long shade over the tube."""
    name = 'desk_lamp' + ('_lit' if lit_ else '')
    m = Model(name, A_DLAMP, ao=not lit_, display=DISP_SMALL)
    MET = painted('dlamp_metal', ramp(['#3a5a4a', '#4a6e5c', '#5a826e', '#6a9680', '#7aa892'], 5), base=2.6)
    y0 = -4.0
    m.box([5, y0, 8], [11, y0 + 1.2, 13], MET)
    m.box([7.5, y0 + 1.2, 11.5], [8.5, y0 + 10, 12.5], MET)
    m.box([7.5, y0 + 9.2, 6.0], [8.5, y0 + 10.2, 12.5], MET)
    m.box([4, y0 + 8.2, 4.0], [12, y0 + 9.6, 8.0], MET, faces={'down': solid('dlamp_shade_in', '#e8e4d8')})
    TUBE = lit('dlamp_tube_on', '#fffcf0') if lit_ else solid('dlamp_tube', '#d8d8d0')
    m.box([4.6, y0 + 7.6, 5.4], [11.4, y0 + 8.2, 6.6], TUBE, glow=('down', 'north', 'south', 'east', 'west') if lit_ else ())
    m.box([9.6, y0 + 1.2, 9.0], [10.6, y0 + 1.6, 10.0], solid('dlamp_switch', '#c8281c'))
    return m


desk_lamp(False)
desk_lamp(True)


# ================================================================================================ genkan: shoe cabinet, shoes, umbrella
A_SHOE = Atlas('shoe_cabinet')


def _shoecab_front(w, h, face):
    a = m_wood(w, h, WOODLAM, 9801 + w, along='y', base=3.0, amp=0.3, lines=0.7)
    bevel(a, 0.05, -0.07)
    if w >= 10 and h >= 10:
        vline(a, w // 2, 1, h - 2, WOODGRAIN[1])
        for xx in (w // 2 - 2, w // 2 + 1):
            vline(a, xx, 4, 6, '#c8ccce')
        tint(a[h - 2:], '#2a1a0c', 0.3)
    return a


def shoe_cabinet():
    """下駄箱: wood-grain shoe cabinet; on top a key tray, a dead plant in a pot, a pile of flyers."""
    m = Model('shoe_cabinet', A_SHOE, display=DISP_FRONT)
    SIDE = woodmat('shoecab_side', WOODLAM, base=3.0, amp=0.3)
    m.box([1, 0.8, 5], [15, 13, 15.5], SIDE, faces={'north': M.custom('shoecab_front', _shoecab_front)})
    m.box([1, 0, 5.5], [15, 0.8, 15], solid('shoecab_kick', '#1a1208'))
    m.box([0.6, 13, 4.6], [15.4, 13.8, 15.8], woodmat('shoecab_top', WOODLAM, base=4.0, amp=0.3))
    m.box([2.5, 13.8, 7], [5.5, 14.3, 9.5], solid('key_tray', '#8a8a86'))
    m.box([3.2, 14.3, 7.6], [4.2, 14.5, 8.4], solid('key', '#c8a858'))
    m.box([10.5, 13.8, 9.5], [13.0, 15.8, 12.0], solid('plant_pot', '#8a4a2a'))
    m.box([11.2, 15.8, 10.2], [12.3, 18.8, 11.3], solid('plant_dead', '#6a5a3a'))
    m.box([6.5, 13.8, 10.0], [9.8, 14.4, 14.0], solid('flyers', '#e8dcc8'))
    m.box([6.7, 14.4, 10.4], [9.6, 14.6, 13.6], solid('flyer_red', '#c84a3a'))
    return m


shoe_cabinet()

A_SHOES = Atlas('shoes')


def shoes():
    """On the genkan tiles: your worn white sneakers (one tipped over) and a pair of brown slip-on sandals."""
    m = Model('shoes', A_SHOES, display=DISP_SMALL)
    SNK = solid('sneaker', '#dcd8cc')
    SOLE = solid('sole', '#8a847a')
    for (x0, z0) in ((2.5, 2.5), (5.5, 3.0)):
        m.box([x0, 0, z0], [x0 + 2.5, 0.6, z0 + 6], SOLE)
        m.box([x0, 0.6, z0 + 0.5], [x0 + 2.5, 2.4, z0 + 5.5], SNK)
        m.box([x0 + 0.5, 2.0, z0 + 3.4], [x0 + 2.0, 2.5, z0 + 5.0], solid('sneaker_in', '#3a3630'))
        m.box([x0, 0.6, z0 + 2.0], [x0 + 2.5, 1.0, z0 + 2.5], solid('sneaker_stripe', '#2a5ac8'))
    SAN = solid('sandal', '#5a3a22')
    for (x0, z0) in ((10.0, 6.0), (12.8, 5.4)):
        m.box([x0, 0, z0], [x0 + 2.4, 0.5, z0 + 6], SAN)
        m.box([x0, 0.5, z0 + 1.0], [x0 + 2.4, 1.6, z0 + 2.6], solid('sandal_band', '#2a1a10'))
    return m


shoes()

A_UMB = Atlas('umbrella')


def umbrella():
    """A clear vinyl umbrella (ビニール傘) leaning against the wall, still wet: rain beads on the folded canopy, a
    puddle forming under its tip."""
    m = Model('umbrella', A_UMB, render_type='translucent', display=DISP_TALL)
    rot = ('x', 22.5, [8, 0, 10])
    m.box([7.2, 1.5, 9.6], [8.8, 13, 11.2], M.custom('umb_canopy', _canopy), rot=rot)
    m.box([7.7, 0, 10.1], [8.3, 15.5, 10.7], solid('umb_shaft', '#d8dcdc'), rot=rot)
    m.box([7.6, 15.5, 10.0], [8.4, 18.0, 10.8], solid('umb_handle', '#f0ece4'), rot=rot)
    m.box([7.6, 17.2, 10.8], [8.4, 18.0, 12.0], solid('umb_handle', '#f0ece4'), rot=rot)
    m.box([5.5, 0.01, 8.5], [10.5, 0.05, 12.0], None, faces={'up': M.custom('umb_puddle', _umb_puddle)})
    return m


def _canopy(w, h, face):
    x, y = grid(w, h)
    a = blank(w, h, C('#d8e4e8', 120))
    a[(x + y) % 3 == 0] = C('#f0f6f8', 160)
    r = rng(9901 + w + h)
    a[r.random((h, w)) < 0.15] = C('#ffffff', 210)
    return a


def _umb_puddle(w, h, face):
    a = blank(w, h, C('#2a3a44', 0))
    a[disc_mask(w, h, w / 2, h / 2, w * 0.45, h * 0.4)] = C('#3a4e5a', 110)
    return a


umbrella()


# ================================================================================================ breaker panel, intercom
A_BRK = Atlas('breaker_panel')


def _breakers(w, h):
    """Inside the panel (10 x 9): the green 30 A main breaker, the leakage breaker with its test button, a row of
    branch breakers with hand-written labels, the little red indicator."""
    a = blank(w, h, '#d8d4c4')
    rect(a, 1, 1, 3, 4, '#3a8a4a')                    # main (アンペア) breaker, green = 30 A
    rect(a, 2, 2, 2, 3, '#1a1a1a')
    rect(a, 5, 1, 8, 4, '#e8e4d8')                    # leakage breaker
    rect(a, 6, 2, 7, 3, '#2a2a2a')
    px(a, 8, 1, '#c8c4b8')
    for xx in range(1, 9, 2):
        rect(a, xx, 6, xx, 7, '#2a2a2a')
        px(a, xx + 1, 6, '#e8e4d8')
        px(a, xx, 8, '#8a8678')                       # pencil labels
    return a


def _brk_lid(w, h):
    a = from_idx(4.2 + noise(w, h, 9951, beta=1.6, wrap=False) * 0.2, CREAMPL)
    bevel(a, 0.05, -0.07)
    hline(a, 3, w - 4, 2, '#8a8676')                  # 分電盤 label (unreadable at this size)
    rect(a, w // 2 - 1, h - 3, w // 2, h - 2, '#a8a490')
    return a


def breaker_panel(open_):
    name = 'breaker_panel' + ('_open' if open_ else '')
    A_BRK.add('inside', 10, 9, _breakers)
    A_BRK.add('lid', 10, 9, _brk_lid)
    m = Model(name, A_BRK, display=DISP_WALL)
    BODY = painted('brk_body', CREAMPL, base=4.0, amp=0.2)
    m.box([3, 3.5, 13.5], [13, 12.5, 16], BODY, faces={'north': 'inside' if open_ else 'lid'})
    # the indicator lamp (always faintly lit while there is power)
    m.box([11.0, 11.0, 13.4], [11.6, 11.6, 13.5], lit('brk_led', '#ff3a2a'), glow=('north',))
    if open_:
        # the lid swung open on its left hinge (as seen from the front: model east), standing out from the wall
        m.box([13.0, 3.5, 4.0], [13.5, 12.5, 13.5], BODY, faces={'east': {'r': 'lid', 'flip': 'h'}})
    return m


breaker_panel(False)
breaker_panel(True)

A_ICOM = Atlas('intercom')


def _icom_led(i):
    a = blank(16, 16, '#3a0a0a' if i % 2 else '#ff4a2a')
    return a


register_anim('block/intercom_led', (16, 16), 2, _icom_led, frametime=6)


def _icom_face(w, h):
    """Showa apartment door-phone (4 x 7): beige, a speaker grille, the talk button."""
    a = from_idx(4.0 + noise(w, h, 9961, beta=1.6, wrap=False) * 0.2, CREAMPL)
    bevel(a, 0.05, -0.07)
    for yy in (1, 3):
        hline(a, 1, w - 2, yy, CREAMPL[1])
    rect(a, 1, 5, w - 2, 5, '#c8c4b4')
    return a


def intercom(ringing):
    name = 'intercom' + ('_ringing' if ringing else '')
    A_ICOM.add('face', 4, 7, _icom_face)
    m = Model(name, A_ICOM, display=DISP_WALL)
    m.box([6, 4, 14.5], [10, 11, 16], painted('icom_body', CREAMPL, base=4.0, amp=0.2), faces={'north': 'face'})
    if ringing:
        m.extra_tex['led'] = ref('block/intercom_led')
        m.box([8.6, 9.8, 14.4], [9.2, 10.4, 14.5], None, faces={'north': {'r': '#led', 'sub': (0, 0, 16, 16)}},
              glow=('north',))
    else:
        m.box([8.6, 9.8, 14.4], [9.2, 10.4, 14.5], None, faces={'north': solid('icom_led_off', '#3a1a14')})
    return m


intercom(False)
intercom(True)


# ================================================================================================ calendar, wall clock
A_CAL = Atlas('calendar')


def _calendar_page(w, h):
    """November 2026 (3x density: 30 x 42 texels on a 10 x 14 face): an autumn photo, the big 11, the date grid with
    the 3rd (文化の日) in red and a hand-drawn circle around the 2nd."""
    a = blank(w, h, '#f2f0e8')
    x, y = grid(w, h)
    # photo: maple leaves on a temple roof
    ph = (y < 16) & (x >= 2) & (x < w - 2) & (y >= 2)
    a[ph] = C('#3a2a22')
    leaves = ph & (((x * 7 + y * 13) % 5) < 2)
    a[leaves] = C('#c84a2a')
    a[ph & (((x * 3 + y * 11) % 7) == 0)] = C('#e8a03a')
    a[ph & (y >= 12)] = C('#2a2420')
    # 2026 and the big 11
    m = text35('2026', gap=1)
    stamp_mask(a, m, 3, 18, '#5a5a5a')
    big = text_mask('11', 11, FONT_IPAG, binary=True)
    stamp_mask(a, big, w - 3 - big.shape[1], 17, '#1a1a1a')
    # date grid: 7 columns (Sun..Sat), Nov 1 is a Sunday
    gx0, gy0 = 2, 30
    cw, ch = 4, 3
    for d in range(1, 31):
        idx = d - 1
        col, row = idx % 7, idx // 7
        cx, cy = gx0 + col * cw, gy0 + row * ch
        c = '#c8281c' if (col == 0 or d == 3) else ('#2a4ac8' if col == 6 else '#3a3a3a')
        rect(a, cx, cy, cx + 1, cy, c)
        if d == 2:
            frame(a, cx - 1, cy - 1, cx + 2, cy + 1, '#c8281c')
    # a small hole for the nail, a fold
    px(a, w // 2, 0, '#8a8678')
    return a


def calendar():
    A_CAL.add('page', 30, 42, _calendar_page)
    m = Model('calendar', A_CAL, display=DISP_WALL)
    m.box([3, 1, 15.6], [13, 15, 16], solid('cal_back', '#e2dfd6'), faces={'north': 'page'})
    m.box([7.6, 14.6, 15.2], [8.4, 15.4, 16], solid('cal_nail', '#5a5a58'))
    return m


calendar()


def _clock_frame(i):
    """Wall clock face 16 x 16 (on a 10 x 10 face, 1.6x): cream dial, hour marks, the hands at 2:58, the thin red
    second hand ticking round once a minute (60 frames x 1 s)."""
    w = h = 16
    a = blank(w, h, '#efe9d6')
    x, y = grid(w, h)
    c = 7.5
    d = np.hypot(x - c, y - c)
    a[d > 7.6] = C('#2a1a10')
    a[(d > 6.9) & (d <= 7.6)] = C('#5a3a22')
    for k in range(12):
        ang = k / 12 * 2 * math.pi
        xx, yy = c + math.sin(ang) * 5.9, c - math.cos(ang) * 5.9
        px(a, int(round(xx)), int(round(yy)), '#2a2a2a')

    def hand(ang, L, col):
        for t in np.linspace(0, L, int(L * 3)):
            px(a, int(round(c + math.sin(ang) * t)), int(round(c - math.cos(ang) * t)), col)
    hand((2 + 58 / 60) / 12 * 2 * math.pi, 3.4, '#1a1a1a')
    hand(58 / 60 * 2 * math.pi, 5.2, '#1a1a1a')
    hand(i / 60 * 2 * math.pi, 5.8, '#c8281c')
    px(a, 7, 7, '#c8281c')
    return a


register_anim('block/wall_clock_face', (16, 16), 60, _clock_frame, frametime=20)
A_CLK = Atlas('wall_clock')


def wall_clock():
    m = Model('wall_clock', A_CLK, display=DISP_WALL)
    m.extra_tex['face'] = ref('block/wall_clock_face')
    WOOD = woodmat('clock_wood', DARKWOOD, base=3.2)
    disc_strips_wall(m, 8, 8, 5.0, 14.6, 16.0, WOOD)
    m.box([3, 3, 14.5], [13, 13, 14.6], None, faces={'north': {'r': '#face', 'sub': (0, 0, 16, 16)}})
    return m


def disc_strips_wall(m, cx, cy, r, z0, z1, side):
    """A wall-mounted disc (a round frame on the wall) from strips along y."""
    x = cx - r
    while x < cx + r - 1e-6:
        xb = min(cx + r, x + 1.0)
        xm = (x + xb) / 2
        half = math.sqrt(max(0.0, r * r - (xm - cx) ** 2))
        half = max(0.5, round(half * 2) / 2)
        m.box([x, cy - half, z0], [xb, cy + half, z1], side)
        x = xb


wall_clock()
