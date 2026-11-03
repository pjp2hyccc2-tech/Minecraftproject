"""Dining room, waiting / register area, vestibule and kids' corner props of HIDAMARI (models face north).

Heights: restaurant_table top = 12 px; counters = 16 px. Table-top props (menu_stand, call_button, water_glass) sit on a
restaurant_table placed in the block below (their elements start at y = -4); counter-top props (register,
service_bell, business_phone, reservation_book) sit on a counter below (y = 0)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, MODELS, rot90_box, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, sun_logo, sun_small,
                    bitmap, upscale_nn, m_vinyl, m_wood, m_glass, m_plastic, m_paint, m_brushed, m_fabric,
                    VINYL_RED, WOOD_DARK, WOOD_TABLE, WOOD_LIGHT, ORANGE, BROWN, CREAM, STEEL, ALU, CHROME,
                    WHITEPLASTIC, BEIGE, BLACKPLASTIC, GREYPAINT, PAPERW, GREEN_LEAF, GREEN_SIGN, RED_LED, RUBBER,
                    FONT_IPAG)
from common import FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK, FONT_SANS_JP_MED
import rs_mats as M

TABLE_TOP = 12          # restaurant_table top height (px)
T_OFF = TABLE_TOP - 16  # table-top props start here (-4)
DISP_TABLETOP = disp(gui=(30, 225, 0), gui_scale=0.95, gui_t=(0, 4, 0), ground_scale=0.4, fixed_scale=0.8)
DISP_COUNTERTOP = disp(gui=(30, 225, 0), gui_scale=0.85, gui_t=(0, 1.5, 0), ground_scale=0.4, fixed_scale=0.8)


def _deco(key, base_fn, draw):
    """Material from base painter fn(w, h, face) + draw(a, w, h, face) on top."""
    def fn(w, h, face):
        a = base_fn(w, h, face)
        draw(a, w, h, face)
        return a
    return M.custom(key, fn)


# ================================================================================================ booth seat
def _booth_back_front(w, h, face):
    a = m_vinyl(w, h, VINYL_RED, 5101, sheen=0.7)
    # vertical channel tufting every 4 px
    for x in range(0, w):
        if x % 4 == 3:
            dl(a[:, x:x + 1], -0.09)
        if x % 4 == 0:
            dl(a[:, x:x + 1], 0.035)
    dl(a[:1], 0.08)
    dl(a[h - 1:], -0.08)
    return a


def _booth_seat_top(w, h, face):
    a = m_vinyl(w, h, VINYL_RED, 5102, sheen=0.3, cracks=0.4)
    dl(a[:1], 0.06)                 # front roll
    dl(a[h - 2:], -0.06)            # crease at the back
    # a taped-over split (well used)
    if w >= 12:
        rect(a, 9, 4, 11, 5, '#3a1610')
        hline(a, 9, 11, 4, '#4e2016')
    return a


A_BOOTH = Atlas('booth_seat')
BOOTH_BACK = M.custom('booth_back', _booth_back_front)
BOOTH_TOP = M.custom('booth_top', _booth_seat_top)


def booth_seat():
    m = Model('booth_seat', A_BOOTH)
    m.box([0, 0, 2], [16, 4, 15], M.DARKWOOD)                                         # plinth
    m.box([0, 0, 1.5], [16, 1, 2], M.BLACK_M, only=('north', 'up'))                  # kick
    m.box([0, 4, 1], [16, 7.5, 13], M.VINYL_SEAT, faces={'up': BOOTH_TOP})           # seat cushion
    m.box([0, 7.5, 12], [16, 14.5, 15], M.VINYL, faces={'north': BOOTH_BACK})        # back cushion
    m.box([0, 14.5, 11.5], [16, 15.5, 15], M.VINYL)                                  # top roll
    m.box([0, 15, 15], [16, 16, 16], M.DARKWOOD)                                     # cap
    m.box([0, 15.5, 11], [16, 16, 15], M.DARKWOOD, skip=('down',))
    m.box([0, 0, 15], [16, 15, 16], M.DARKWOOD, skip=('north',))                     # rear panel
    return m


booth_seat()


# ================================================================================================ booth partition
def _frosted(w, h, face):
    a = blank(w, h, C('#dfe6e4', 196))
    n = noise(w, h, 5201, beta=1.4, wrap=False)
    a[..., :3] += n[..., None] * 4
    x, y = grid(w, h)
    # clear etched pattern: thin horizontal lines and a wave band
    band = (np.abs(y - (h * 0.32 + np.sin(x * 0.7) * 0.8)) < 0.5)
    a[band, 3] = 120
    a[(y.astype(int) == h - 3), 3] = 130
    a[(y.astype(int) == 2), 3] = 130
    dl(a, 0.03, (x + y) % 9 == 0)
    return a


A_PART = Atlas('booth_partition')
FROSTED = M.custom('frosted', _frosted)


def booth_partition():
    m = Model('booth_partition', A_PART, render_type='translucent')
    m.box([0, 0, 12], [16, 1.5, 15.5], M.DARKWOOD)
    m.box([0, 14.5, 12], [16, 16, 15.5], M.DARKWOOD)
    m.box([0, 1.5, 12], [1, 14.5, 15.5], M.DARKWOOD)
    m.box([15, 1.5, 12], [16, 14.5, 15.5], M.DARKWOOD)
    m.box([1, 1.5, 13.5], [15, 14.5, 14], FROSTED, only=('north', 'south'))
    return m


booth_partition()


# ================================================================================================ table
A_TABLE = Atlas('restaurant_table')


def _table_top(w, h, face):
    a = m_wood(w, h, WOOD_TABLE, 5301, along='x', base=3.2, amp=0.45)
    n = noise(w, h, 5302, beta=1.8, wrap=False)
    # laminate sheen + rings left by glasses
    for (cx, cy, r) in ((4.5, 5.5, 1.6), (11.5, 10.5, 1.6)):
        ring = ring_mask(w, h, cx, cy, r - 0.5, r + 0.4)
        dl(a, 0.04, ring)
    dl(a, 0.02, n > 0.8)
    return a


def _table_edge(w, h, face):
    a = blank(w, h, '#1d120b')
    a[:1] = C('#4a3020')
    return a


TOP = M.custom('table_top', _table_top)
EDGE = M.custom('table_edge', _table_edge)
METAL_BLACK = M.paint('metal_black', BLACKPLASTIC, base=2.6, scuffs=0.08)


def restaurant_table():
    m = Model('restaurant_table', A_TABLE)
    m.box([0, 10.5, 0], [16, 12, 16], EDGE, faces={'up': TOP, 'down': M.DARKWOOD})
    m.box([4, 9.5, 4], [12, 10.5, 12], METAL_BLACK, skip=('up',))
    m.box([7, 1, 7], [9, 9.5, 9], METAL_BLACK, only=('north', 'south', 'east', 'west'))
    m.box([2.5, 0, 7], [13.5, 1, 9], METAL_BLACK, skip=('down',))
    m.box([7, 0, 2.5], [9, 1, 13.5], METAL_BLACK, skip=('down',))
    return m


restaurant_table()

# ================================================================================================ chair
A_CHAIR = Atlas('restaurant_chair')


def chair_boxes():
    W = M.DARKWOOD
    out = []
    for x0 in (3, 11.5):
        out.append(([x0, 0, 3], [x0 + 1.5, 7, 4.5], W, None))
        out.append(([x0, 0, 11.5], [x0 + 1.5, 15, 13], W, None))
    out.append(([3, 6.5, 2.5], [13, 7.5, 13], W, None))                 # seat frame
    out.append(([3.5, 7.5, 3], [12.5, 9, 12.5], M.VINYL_SEAT, None))    # cushion
    out.append(([4.5, 10, 12], [11.5, 14, 13], M.VINYL, None))          # back pad
    out.append(([3, 14, 11.5], [13, 15.5, 13], W, None))                # top rail
    out.append(([4.5, 3, 3.25], [11.5, 4, 4.25], W, None))              # stretchers
    out.append(([4.5, 3, 11.75], [11.5, 4, 12.75], W, None))
    return out


def restaurant_chair(fallen=False):
    name = 'restaurant_chair_fallen' if fallen else 'restaurant_chair'
    m = Model(name, A_CHAIR)
    boxes = chair_boxes()
    if fallen:
        tb = []
        for fr, to, mat, faces in boxes:
            f2, t2, nf, _ = rot90_box(fr, to, faces, 'x', origin=(8, 8, 8))
            tb.append((f2, t2, mat, nf))
        miny = min(b[0][1] for b in tb)
        minz = min(b[0][2] for b in tb)
        maxz = max(b[1][2] for b in tb)
        dz = -minz + (16 - (maxz - minz)) / 2
        boxes = [([f[0], f[1] - miny, f[2] + dz], [t[0], t[1] - miny, t[2] + dz], mat, nf) for f, t, mat, nf in tb]
    for fr, to, mat, faces in boxes:
        m.box(fr, to, mat, faces=faces)
    return m


restaurant_chair(False)
restaurant_chair(True)


# ================================================================================================ call button
A_CALL = Atlas('call_button')


def _call_top(w, h, face):
    a = m_plastic(w, h, WHITEPLASTIC, 5401, base=5.0)
    return a


def _call_face(lit):
    def fn(w, h, face):
        a = m_plastic(w, h, WHITEPLASTIC, 5402, base=4.6)
        rect(a, 0, h - 1, w - 1, h - 1, WHITEPLASTIC[2])
        return a
    return fn


def call_button(lit):
    name = 'call_button_lit' if lit else 'call_button'
    m = Model(name, A_CALL, display=DISP_TABLETOP, ao=False)
    y0 = T_OFF
    m.box([5.5, y0, 9.5], [10.5, y0 + 0.5, 13], M.BLACK_M)                       # base plate
    m.box([6, y0 + 0.5, 10], [10, y0 + 2, 12.5], M.custom('call_body', _call_top))
    # big "呼出" button (orange) and the small cancel button
    m.box([6.5, y0 + 2, 10.4], [8.5, y0 + 2.5, 12.1], M.ORANGE_M)
    m.box([8.9, y0 + 2, 10.8], [9.6, y0 + 2.3, 11.6], M.custom('btn_grey', lambda w, h, f: blank(w, h, '#9a9a96')))
    led = M.custom('led_on', lambda w, h, f: blank(w, h, '#ff3a26')) if lit else \
        M.custom('led_off', lambda w, h, f: blank(w, h, '#5a1612'))
    m.box([8.9, y0 + 1.1, 9.9], [9.6, y0 + 1.6, 10], led, only=('north',), glow=('north',) if lit else ())
    m.box([8.9, y0 + 2, 11.9], [9.6, y0 + 2.2, 12.3], led, only=('up',), glow=('up',) if lit else ())
    return m


call_button(False)
call_button(True)


# ================================================================================================ menu stand
A_MENU = Atlas('menu_stand')


def menu_cover(kind, W=14, H=16):
    """2x-density laminated menu cover."""
    a = blank(W, H, '#e8721f' if kind == 0 else ('#5a2e1a' if kind == 1 else '#f2e6d0'))
    if kind == 0:
        rect(a, 0, 0, W - 1, 2, '#5a2e1a')
        logo = sun_small(9)
        over(a, logo, (W - 9) // 2, 3)
        stamp_center(a, text35('MENU'), W / 2, H - 4, '#fff4e0')
        dl(a[H - 2:], -0.06)
    elif kind == 1:
        # grand menu: food photo (hamburg steak on a hot plate)
        rect(a, 1, 2, W - 2, 10, '#2a1a12')
        for (x, y) in ((4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6),
                       (5, 7), (6, 7), (7, 7), (8, 7)):
            px(a, x, y, '#6a3418')
        for (x, y) in ((5, 5), (6, 6)):
            px(a, x, y, '#8a4a22')
        for (x, y) in ((10, 4), (11, 5), (10, 8)):
            px(a, x, y, '#e8a030')
        for (x, y) in ((3, 8), (4, 9), (9, 9)):
            px(a, x, y, '#3c8a2c')
        rect(a, 2, 12, W - 3, 12, '#e8721f')
        rect(a, 3, 14, W - 4, 14, '#c8a070')
    else:
        # dessert menu: parfait
        rect(a, 4, 3, 9, 4, '#f5f2ea')
        rect(a, 5, 5, 8, 11, '#f0e0c8')
        rect(a, 5, 6, 8, 7, '#d23a4a')
        rect(a, 5, 9, 8, 9, '#6a3418')
        px(a, 6, 2, '#d23a4a')
        rect(a, 6, 12, 7, 13, '#c9c9c0')
        stamp_center(a, text35('SWEETS'), W / 2, H - 2, '#c2584a')
    frame(a, 0, 0, W - 1, H - 1, '#3a2010' if kind != 1 else '#1a0e08')
    return a


def _soy(w, h, face):
    a = blank(w, h, '#1c0c08')
    a[:max(1, h // 4)] = C('#c8281c')
    if h > 3:
        px(a, w // 2, h // 2, '#4a2a1a')
    return a


def _napkin_box(w, h, face):
    a = blank(w, h, '#efeee6')
    if face == 'h':
        rect(a, 1, h // 2 - 1, w - 2, h // 2, '#fbfbf6')
        return a
    rect(a, 0, h - 2, w - 1, h - 2, '#e8721f')
    return a


def menu_stand():
    m = Model('menu_stand', A_MENU, display=DISP_TABLETOP)
    y0 = T_OFF
    A_MENU.add('cover0', 14, 16, lambda w, h: menu_cover(0))
    A_MENU.add('cover1', 14, 16, lambda w, h: menu_cover(1))
    A_MENU.add('cover2', 12, 12, lambda w, h: menu_cover(2, 12, 12))
    A_MENU.add('back', 14, 16, lambda w, h: (lambda a: (frame(a, 0, 0, w - 1, h - 1, '#3a2010'), a)[1])(
        blank(w, h, '#e9dcc4')))
    m.box([3.5, y0, 11.5], [12.5, y0 + 1, 14.5], M.DARKWOOD)                               # stand base
    m.box([3.5, y0 + 1, 14], [12.5, y0 + 4, 14.5], M.DARKWOOD)                             # back board
    # menus standing in the stand (laminated, 7 x 8 px, covers at 2x)
    m.box([4.5, y0 + 1, 12.2], [11.5, y0 + 9, 12.6], M.CREAM_M, faces={'north': 'cover0', 'south': 'back'})
    m.box([4, y0 + 1, 12.9], [11, y0 + 9, 13.3], M.CREAM_M, faces={'north': 'cover1', 'south': 'back'})
    m.box([7, y0 + 1, 13.5], [13, y0 + 7, 13.9], M.CREAM_M, faces={'north': 'cover2', 'south': 'back'})
    # condiments in front
    SOY = M.custom('soy', _soy)
    m.box([4, y0, 9], [5.2, y0 + 2.8, 10.2], SOY)
    m.box([4.35, y0 + 2.8, 9.35], [4.85, y0 + 3.3, 9.85], M.custom('cap_red', lambda w, h, f: blank(w, h, '#c8281c')))
    m.box([5.8, y0, 9.2], [6.6, y0 + 1.6, 10], M.WHITE_M)                                 # salt
    m.box([5.8, y0 + 1.6, 9.2], [6.6, y0 + 2, 10], M.custom('cap_steel', lambda w, h, f: blank(w, h, '#b8bcbe')))
    m.box([7, y0, 9.2], [7.8, y0 + 1.6, 10], M.custom('pepper', lambda w, h, f: blank(w, h, '#2a2422')))
    m.box([7, y0 + 1.6, 9.2], [7.8, y0 + 2, 10], M.custom('cap_steel', lambda w, h, f: blank(w, h, '#b8bcbe')))
    m.box([8.4, y0, 9.3], [9.2, y0 + 1.4, 10.1], M.custom('pick', lambda w, h, f: blank(w, h, '#d8c8a0')))
    m.box([9.8, y0, 8.4], [12.4, y0 + 1.6, 10.6], M.custom('napkin', _napkin_box))
    m.box([10.6, y0 + 1.6, 9.2], [11.6, y0 + 2.2, 9.8], M.custom('tissue', lambda w, h, f: blank(w, h, '#fbfbf7')))
    return m


menu_stand()


# ================================================================================================ scattered menus
A_SCAT = Atlas('menu_scattered', cutout=False)


def open_menu(W=24, H=16, seed=0):
    """An open grand menu (two pages of food photos) at 2x."""
    r = rng(5501 + seed)
    a = blank(W, H, '#f4ead8')
    vline(a, W // 2, 0, H - 1, '#c8b898')
    frame(a, 0, 0, W - 1, H - 1, '#5a2e1a')
    foods = [('#6a3418', '#8a4a22'), ('#e8c070', '#f0d898'), ('#c83a1c', '#e86a3a'), ('#d8d0b8', '#f4f0e0'),
             ('#3c6a2c', '#5a8a3c'), ('#6a2a10', '#a85a2a')]
    for page in range(2):
        for k in range(2):
            x0 = 2 + page * (W // 2)
            y0 = 2 + k * 7
            c1, c2 = foods[int(r.integers(0, len(foods)))]
            rect(a, x0, y0, x0 + W // 2 - 5, y0 + 3, '#2a1a12')
            rect(a, x0 + 1, y0 + 1, x0 + W // 2 - 6, y0 + 2, c1)
            px(a, x0 + 2, y0 + 1, c2)
            hline(a, x0, x0 + 5, y0 + 5, '#8a7a60')
            px(a, x0 + W // 2 - 6, y0 + 5, '#c8281c')
    return a


def scattered(variant):
    name = f'menu_scattered_{variant}'
    m = Model(name, A_SCAT, render_type='cutout', ao=False, display=DISP_FLOOR)
    if 'cover0' not in A_SCAT.regions:
        A_SCAT.add('cover0', 14, 16, lambda w, h: menu_cover(0))
        A_SCAT.add('cover1', 14, 16, lambda w, h: menu_cover(1))
        A_SCAT.add('cover2', 12, 12, lambda w, h: menu_cover(2, 12, 12))
        A_SCAT.add('open0', 24, 16, lambda w, h: open_menu(24, 16, 0))
        A_SCAT.add('open1', 24, 16, lambda w, h: open_menu(24, 16, 1))
        A_SCAT.add('backc', 14, 16, lambda w, h: (lambda a: (frame(a, 0, 0, w - 1, h - 1, '#3a2010'), a)[1])(
            blank(w, h, '#e9dcc4')))
        A_SCAT.add('edge', 4, 2, lambda w, h: blank(w, h, '#3a2010'))
    layouts = {
        0: [('cover0', (2, 3), 7, 8, 22.5), ('open0', (6, 7), 12, 8, -22.5)],
        1: [('cover1', (1, 1), 7, 8, 45), ('cover0', (8, 2), 7, 8, -22.5), ('cover2', (5, 9), 6, 6, 0)],
        2: [('open1', (2, 5), 12, 8, 22.5), ('cover1', (9, 1), 7, 8, 0)],
        3: [('cover0', (1, 8), 7, 8, -45), ('cover1', (8, 7), 7, 8, 22.5), ('cover2', (3, 1), 6, 6, 45),
            ('open0', (7, 1), 12, 8, -22.5)],
    }
    y = 0.02
    for i, (reg, (x0, z0), w, d, ang) in enumerate(layouts[variant]):
        th = 0.25
        yy = y + i * 0.26
        fr = [x0, yy, z0]
        to = [x0 + w, yy + th, z0 + d]
        cx, cz = x0 + w / 2, z0 + d / 2
        faces = {'up': reg, 'down': 'backc' if reg.startswith('cover') else reg,
                 'north': 'edge', 'south': 'edge', 'east': 'edge', 'west': 'edge'}
        m.box(fr, to, None, faces=faces, rot=('y', ang, [cx, yy, cz]) if ang else None)
    return m


for _v in range(4):
    scattered(_v)


# ================================================================================================ water glass
A_GLASS = Atlas('water_glass')


def _glass_side(w, h, face):
    a = blank(w, h, C('#cfe2e6', 92))
    if h >= 3:
        a[h - 1:, :, 3] = 150                       # thick base
        a[: max(1, h // 4), :, 3] = 70              # empty top part
        a[:, :1, :3] = 240
        a[:, :1, 3] = 140
    return a


def _water(w, h, face):
    a = blank(w, h, C('#9fc4d0', 120))
    if face == 'h':
        px(a, 0, 0, C('#f4fbff', 220))
        if w > 1 and h > 1:
            px(a, w - 1, h - 1, C('#e8f4f8', 210))
    return a


def water_glass(n):
    m = Model(f'water_glass_{n}', A_GLASS, render_type='translucent', ao=False, display=DISP_TABLETOP)
    spots = [(7, 6), (4, 9.5), (10.5, 9), (8, 12)][:n]
    G = M.custom('glass_side', _glass_side)
    Wt = M.custom('water', _water)
    ICE = M.custom('ice', lambda w, h, f: blank(w, h, C('#f2fbff', 215)))
    for (x, z) in spots:
        y0 = T_OFF
        m.box([x, y0, z], [x + 2, y0 + 3.2, z + 2], G, skip=('up',))
        m.box([x + 0.15, y0 + 0.3, z + 0.15], [x + 1.85, y0 + 2.5, z + 1.85], Wt, skip=('down',))
        m.box([x + 0.4, y0 + 1.9, z + 0.4], [x + 1.1, y0 + 2.6, z + 1.1], ICE)
        m.box([x + 1.0, y0 + 1.6, z + 0.9], [x + 1.6, y0 + 2.4, z + 1.5], ICE)
    return m


for _n in range(1, 5):
    water_glass(_n)


# ================================================================================================ broken plate
A_PLATE = Atlas('broken_plate', cutout=True)


def _shards(w, h):
    r = rng(5601)
    a = blank(w, h)
    # big pieces as wedges of a plate centred at (8, 8), plus small splinters
    x, y = grid(w, h)
    ang = np.arctan2(y - 8, x - 8)
    d = np.hypot(x - 8, y - 8)
    wedges = [(0.2, 1.1, 0.0, 0.0), (1.4, 2.3, 1.6, 0.9), (2.6, 3.4, -1.2, 1.4), (-2.2, -1.2, 0.8, -1.6),
              (-0.9, -0.1, -1.8, -1.0)]
    for (a0, a1, ox, oy) in wedges:
        dd = np.hypot(x - 8 - ox, y - 8 - oy)
        aa = np.arctan2(y - 8 - oy, x - 8 - ox)
        m = (aa > a0) & (aa < a1) & (dd < 6.2) & (dd > 0.8)
        a[m] = C('#ecebe4')
        rim = m & (dd > 5.0)
        a[rim] = C('#2d5a9a')          # blue rim line
        a[m & (dd > 5.6)] = C('#dcdbd2')
    for _ in range(14):
        sx, sy = int(r.integers(1, 15)), int(r.integers(1, 15))
        if a[sy, sx, 3] == 0:
            a[sy, sx] = C('#e2e1da')
    # shading on the pieces
    dl(a, -0.06, (a[..., 3] > 0) & (((x + y) % 5) == 0))
    return a


def broken_plate():
    A_PLATE.add('shards', 16, 16, _shards)
    m = Model('broken_plate', A_PLATE, render_type='cutout', ao=False, display=DISP_FLOOR)
    m.plane([0, 0.05, 0], [16, 0.05, 16], {'up': 'shards', 'down': 'shards'})
    m.box([10, 0, 3], [12.5, 0.6, 5], M.custom('shard_big', lambda w, h, f: blank(w, h, '#e6e5de')),
          rot=('y', 22.5, [11, 0, 4]))
    return m


broken_plate()


# ================================================================================================ pendant lamp
A_PEND = Atlas('pendant_lamp')


def _amber(lit):
    def fn(w, h, face):
        if lit:
            a = blank(w, h, '#ffb35a')
            x, y = grid(w, h)
            dl(a, 0.08, y > h * 0.6)
            dl(a, -0.05, y < 1)
        else:
            a = blank(w, h, '#7a3c14')
            x, y = grid(w, h)
            dl(a, 0.05, (x + y) % 4 == 0)
            dl(a, -0.04, y < 1)
        return a
    return M.custom('amber_lit' if lit else 'amber', fn)


def _shade_inside(lit):
    def fn(w, h, face):
        a = blank(w, h, '#fff1c8' if lit else '#3a2010')
        if w >= 5:
            ring = ring_mask(w, h, w / 2, h / 2, w / 2 - 1.2, w / 2)
            a[ring] = C('#ffd27a' if lit else '#5a3216')
        return a
    return M.custom('shade_in_lit' if lit else 'shade_in', fn)


def pendant_lamp(lit):
    name = 'pendant_lamp_lit' if lit else 'pendant_lamp'
    m = Model(name, A_PEND, display=disp(gui=(30, 225, 0), gui_scale=0.9, gui_t=(0, -3, 0)), ao=not lit)
    AMB = _amber(lit)
    BLK = M.BLACK_M
    m.box([6.5, 15.5, 6.5], [9.5, 16, 9.5], M.WHITE_M)                     # ceiling canopy
    m.box([7.75, 12.5, 7.75], [8.25, 15.5, 8.25], BLK)                   # cord
    m.box([7, 12, 7], [9, 12.5, 9], BLK)                                 # socket cap
    g = ('north', 'south', 'east', 'west', 'up') if lit else ()
    m.box([6.5, 11.5, 6.5], [9.5, 12.25, 9.5], AMB, glow=g, shade=not lit)
    m.box([5, 10.5, 5], [11, 11.5, 11], AMB, glow=g, shade=not lit)
    m.box([4, 9.25, 4], [12, 10.5, 12], AMB, glow=g, shade=not lit)
    m.box([3.5, 8.5, 3.5], [12.5, 9.25, 12.5], AMB, faces={'down': _shade_inside(lit)}, glow=g + ('down',) if lit else (),
          shade=not lit)
    m.box([7.25, 8.25, 7.25], [8.75, 9.75, 8.75], M.custom('bulb_lit' if lit else 'bulb',
                                                       lambda w, h, f: blank(w, h, '#fff8e8' if lit else '#b8b0a0')),
          glow='all' if lit else (), shade=not lit)
    return m


pendant_lamp(False)
pendant_lamp(True)


# ================================================================================================ exit sign
A_EXIT = Atlas('exit_sign')


def exit_face(W=24, H=12, flip=False):
    a = blank(W, H, '#14873a')
    frame(a, 0, 0, W - 1, H - 1, '#0d5a26')
    man = ['....##....',
           '....##....',
           '..#####...',
           '.##.###.#.',
           '#...###..#',
           '....####..',
           '...##..#..',
           '..##...##.',
           '.##.....#.',
           '##.......#']
    door = ['######', '#....#', '#....#', '#....#', '#....#', '#....#', '#....#', '#....#', '#....#']
    mm = bitmap(man)
    dm = bitmap(door)
    stamp_mask(a, dm, 15, 2, '#f4fff6')
    rect(a, 16, 3, 19, 10, '#0f7030')
    stamp_mask(a, mm, 4, 1, '#f4fff6')
    # arrow under the man pointing to the door
    hline(a, 3, 12, 11 if H > 11 else H - 1, '#14873a')
    if flip:
        a = a[:, ::-1].copy()
    return a


def exit_sign(hanging):
    name = 'exit_sign_hanging' if hanging else 'exit_sign'
    m = Model(name, A_EXIT, display=DISP_WALL, ao=False)
    A_EXIT.add('face', 24, 12, lambda w, h: exit_face(24, 12))
    A_EXIT.add('face_back', 24, 12, lambda w, h: exit_face(24, 12, flip=True))
    HOUSING = M.custom('exit_housing', lambda w, h, f: bevel(blank(w, h, '#d9dad4'), 0.04, -0.06))
    if not hanging:
        m.box([2, 8, 14], [14, 14, 16], HOUSING, faces={'north': 'face'}, glow=('north',))
    else:
        m.box([2, 6, 7], [14, 12, 9], HOUSING, faces={'north': 'face', 'south': 'face_back'},
              glow=('north', 'south'))
        for x0 in (4, 11):
            m.box([x0, 12, 7.5], [x0 + 1, 16, 8.5], M.CHROME_M, skip=('up', 'down'))
    return m


exit_sign(False)
exit_sign(True)


# ================================================================================================ window blind
A_BLIND = Atlas('window_blind', cutout=True)


def _slats(closed):
    def fn(w, h):
        a = blank(w, h)
        for y in range(h):
            if closed or y % 2 == 0:
                a[y, :] = C('#d6cfbc' if y % 2 == 0 else '#bdb5a0')
                if y % 2 == 0:
                    dl(a[y:y + 1], 0.03)
            else:
                for x in (3, 12):
                    a[y, x] = C('#cfc8b4')
        n = noise(w, h, 5701, beta=1.4, wrap=False)
        m = a[..., 3] > 0
        tint(a, '#8a7a50', np.clip(n * 0.06 + 0.06, 0, 0.2), m)
        # a bent slat
        if not closed:
            a[6, 9:13] = C('#9d9580')
        return a
    return fn


def window_blind(closed):
    name = 'window_blind_closed' if closed else 'window_blind'
    A_BLIND.add('slats_closed' if closed else 'slats', 16, 16, _slats(closed))
    m = Model(name, A_BLIND, render_type='cutout', display=DISP_WALL, ao=False)
    reg = 'slats_closed' if closed else 'slats'
    A_BLIND.add('edge', 2, 16, lambda w, h: blank(w, h, '#c9c2ae'))
    A_BLIND.add('top', 16, 2, lambda w, h: blank(w, h, '#c9c2ae'))
    m.box([0, 0, 14], [16, 16, 15.5], None, faces={'north': reg, 'south': reg})
    return m


window_blind(False)
window_blind(True)


# ================================================================================================ planter divider
A_PLANT = Atlas('planter', cutout=True)


def leaves_tex(w, h, seed, dark=False, dusty=0.15):
    r = rng(seed)
    a = blank(w, h)
    pal_ = ramp(['#0f2414', '#163420', '#1f4529', '#2a5733', '#376a3e', '#4a7d4a'], 6)
    for _ in range(int(w * h / 5)):
        cx, cy = r.uniform(0, w), r.uniform(0, h)
        ang = r.uniform(0, math.pi)
        L = r.uniform(2.0, 3.6)
        for t in np.linspace(-L / 2, L / 2, 7):
            for s_ in (-0.6, 0, 0.6):
                xx = int(cx + math.cos(ang) * t - math.sin(ang) * s_ * (1 - abs(t) / L * 1.6))
                yy = int(cy + math.sin(ang) * t + math.cos(ang) * s_ * (1 - abs(t) / L * 1.6))
                if 0 <= xx < w and 0 <= yy < h:
                    k = 2 + int(r.integers(0, 3)) + (1 if s_ < 0 else 0)
                    a[yy, xx] = pal_[min(5, k)]
    m = a[..., 3] > 0
    if dusty:
        tint(a, '#8a8a70', dusty, m & (r.random((h, w)) < 0.5))
    # a few yellowed fake leaves
    ym = m & (r.random((h, w)) < 0.03)
    a[ym] = C('#8a8a3a')
    return a


def _planter_box(w, h, face):
    a = m_wood(w, h, WOOD_DARK, 5801, along='x', base=3.0)
    if face != 'h':
        if h > 4:
            frame(a, 1, 1, w - 2, h - 2, WOOD_DARK[1])
        dl(a[:1], 0.06)
    return a


def planter():
    A_PLANT.add('leaves_a', 16, 16, lambda w, h: leaves_tex(w, h, 5811))
    A_PLANT.add('leaves_b', 16, 16, lambda w, h: leaves_tex(w, h, 5812))
    A_PLANT.add('leaves_top', 16, 16, lambda w, h: leaves_tex(w, h, 5813))
    m = Model('planter', A_PLANT, render_type='cutout', display=disp(gui=(30, 225, 0), gui_scale=0.45, gui_t=(0, -2, 0)))
    BOX = M.custom('planter_box', _planter_box)
    m.box([0, 0, 3], [16, 9, 13], BOX)
    m.box([0, 9, 2.5], [16, 10, 13.5], M.DARKWOOD, skip=('down',))
    m.box([0, 9.5, 3.5], [16, 9.6, 12.5], M.custom('pebbles', lambda w, h, f: from_idx(
        2.5 + noise(w, h, 5821, beta=0.2, wrap=False) * 1.2, ramp(['#2a2420', '#4a4238', '#6a6052', '#8a8070'], 4))),
          only=('up',))
    # foliage: crossed planes of fake leaves at a few heights (a bushy, dusty divider)
    for ang, reg, x0, y0, y1 in ((45, 'leaves_a', 0, 9.5, 24), (-45, 'leaves_b', 0, 9.5, 23),
                                 (22.5, 'leaves_top', 0, 9.5, 18), (-22.5, 'leaves_a', 0, 9.5, 19)):
        m.box([x0, y0, 8], [x0 + 16, y1, 8], None, faces={'north': reg, 'south': reg}, rot=('y', ang, [8, 9.5, 8]))
    m.box([0, 9.5, 5], [16, 18, 5], None, faces={'north': 'leaves_b', 'south': 'leaves_b'})
    m.box([0, 9.5, 11], [16, 17, 11], None, faces={'north': 'leaves_top', 'south': 'leaves_top'})
    return m


planter()
