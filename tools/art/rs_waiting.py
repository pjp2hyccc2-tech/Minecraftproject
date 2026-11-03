"""Register counter, waiting area, vestibule, drink bar, server station, call display and kids' corner props.

Counter-top props (register, service_bell, business_phone, reservation_book) and the drink-bar machines
(drink_bar, coffee_machine, ice_machine, soup_warmer, cup_shelf) stand on a counter-height block (y = 0 here = the
counter top)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, sun_logo, sun_small,
                    bitmap, m_vinyl, m_wood, m_glass, m_plastic, m_paint, m_brushed, m_fabric, m_granite,
                    VINYL_RED, WOOD_DARK, WOOD_TABLE, WOOD_LIGHT, ORANGE, BROWN, CREAM, STEEL, ALU, CHROME,
                    WHITEPLASTIC, BEIGE, BLACKPLASTIC, GREYPAINT, PAPERW, GRANITE, RED_LED, FONT_IPAG, DIGIT57)
from common import FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK
import rs_mats as M
from rs_dining import DISP_COUNTERTOP, menu_cover

RNG_COLORS = ['#d8402c', '#2c7ad8', '#e8c02c', '#3aa84a', '#e86aa8', '#f08a2c', '#8a5ad8', '#f2f2ea']


def solid(key, c):
    return M.custom(key, lambda w, h, f, _c=c: blank(w, h, _c))


def lit(key, c):
    return M.custom(key, lambda w, h, f, _c=c: blank(w, h, _c))


# ================================================================================================ counter
A_COUNTER = Atlas('counter')


def _counter_front(w, h, face):
    a = m_wood(w, h, WOOD_DARK, 6101, along='y', base=3.3, amp=0.45)
    for x in range(3, w, 4):
        dl(a[:, x:x + 1], -0.08)
    # orange brand stripe with the sun, kick
    rect(a, 0, 3, w - 1, 4, ORANGE[4])
    hline(a, 0, w - 1, 3, ORANGE[5])
    hline(a, 0, w - 1, 5, WOOD_DARK[1])
    rect(a, 0, h - 1, w - 1, h - 1, '#120a06')
    wear(a, 6102, amount=0.06, edge_only=False)
    return a


def _counter_back(w, h, face):
    a = blank(w, h, '#1a120c')
    # staff side: an open shelf with papers, a cash bag, a box of receipt rolls
    rect(a, 0, 0, w - 1, 1, WOOD_DARK[3])
    rect(a, 0, 7, w - 1, 7, WOOD_DARK[4])
    rect(a, 1, 3, 5, 6, '#e8e4d8')
    hline(a, 1, 5, 3, '#ffffff')
    rect(a, 7, 4, 9, 6, '#3a5a8a')
    rect(a, 11, 2, 14, 6, '#c8b088')
    hline(a, 11, 14, 2, '#d8c098')
    rect(a, 2, 9, 6, 13, '#6a6458')
    rect(a, 9, 10, 13, 13, '#e8e4d8')
    rect(a, 0, h - 1, w - 1, h - 1, '#0a0604')
    return a


def counter():
    m = Model('counter', A_COUNTER)
    FRONT = M.custom('counter_front', _counter_front)
    BACK = M.custom('counter_back', _counter_back)
    m.box([0, 0, 1], [16, 15, 16], M.DARKWOOD, faces={'north': FRONT, 'south': BACK}, skip=('up',))
    m.box([0, 15, 0], [16, 16, 16], M.GRANITE_M)
    return m


counter()

# ================================================================================================ register
A_REG = Atlas('register')


def _reg_screen(w, h):
    a = blank(w, h, '#0e1a1c')
    rect(a, 1, 1, w - 2, 2, '#1e4a52')
    for y in range(4, h - 1, 2):
        hline(a, 1, w - 3, y, '#163a40')
    rect(a, w - 4, h - 3, w - 2, h - 2, '#2a6a4a')
    return a


def _reg_cust(w, h):
    a = blank(w, h, '#061008')
    stamp_mask(a, text35('0'), w - 5, 1, '#3aff6a')
    stamp_mask(a, bitmap(['#.#', '.#.', '###', '.#.', '.#.']), 1, 1, '#3aff6a')
    return a


def _keys(w, h, face):
    a = blank(w, h, '#2a2c2e')
    for y in range(0, h):
        for x in range(0, w):
            if (x % 2 == 0) and (y % 2 == 0):
                a[y, x] = C('#c9cbc8' if (x + y) % 6 else '#e88a3a')
    return a


def _drawer_top(w, h, face):
    a = blank(w, h, '#1a1a1c')
    # bill and coin compartments
    for i in range(4):
        x0 = 1 + i * (w - 2) // 4
        rect(a, x0, 1, x0 + (w - 2) // 4 - 2, h // 2 - 1, ['#7a8a6a', '#8a7a6a', '#6a7a8a', '#8a8a7a'][i])
    for i in range(5):
        x0 = 1 + i * (w - 2) // 5
        rect(a, x0, h // 2 + 1, x0 + (w - 2) // 5 - 2, h - 2, '#5a5a58')
        px(a, x0, h // 2 + 1, '#c8b060')
    return a


def register(open_):
    name = 'register_open' if open_ else 'register'
    m = Model(name, A_REG, display=DISP_COUNTERTOP)
    if 'screen' not in A_REG.regions:
        A_REG.add('screen', 12, 8, _reg_screen)
        A_REG.add('cust', 8, 4, _reg_cust)
    BODY = M.paint('reg_body', BLACKPLASTIC, base=2.4, scuffs=0.03)
    m.box([3, 0, 3], [13, 3, 12.5], BODY)                                              # drawer box
    m.box([4, 3, 4], [12, 4, 9], BODY, faces={'up': M.custom('keys', _keys)})          # keypad
    m.box([7.5, 3, 9.5], [8.5, 6.5, 10.5], BODY)                                       # pole
    m.box([4, 6.5, 9], [12, 11.5, 10.5], BODY, faces={'south': 'screen', 'north': BODY})  # screen faces the cashier
    m.box([5, 11.5, 9.5], [11, 13.5, 10.5], BODY, faces={'north': 'cust'}, glow=('north',))
    m.box([9.5, 3, 10], [12.5, 5, 12.5], M.BEIGE_M)                                    # receipt printer
    m.box([10, 5, 11], [12, 5.1, 13.5], solid('receipt', '#f4f2ea'), only=('up', 'down'))
    # screen glow (dim)
    m.box([4.5, 7, 10.51], [11.5, 11, 10.52], None, faces={'south': {'r': 'screen', 'sub': (0, 0, 12, 8)}},
          glow=('south',))
    if open_:
        DR = M.custom('drawer_top', _drawer_top)
        m.box([3.5, 0.2, 12.5], [12.5, 2.6, 18.5], BODY, faces={'up': DR})
    else:
        m.box([4, 0.5, 12.5], [12, 2.5, 12.7], M.custom('drawer_front', lambda w, h, f: bevel(blank(w, h, '#242426'))))
        m.box([7, 1.2, 12.7], [9, 1.6, 13], M.CHROME_M)
    return m


register(False)
register(True)

# ================================================================================================ service bell
A_BELL = Atlas('service_bell')


def service_bell():
    m = Model('service_bell', A_BELL, display=disp(gui=(30, 225, 0), gui_scale=1.2, gui_t=(0, 3, 0)))
    m.box([5.5, 0, 5.5], [10.5, 1, 10.5], M.BLACK_M)
    m.box([6, 1, 6], [10, 2, 10], M.CHROME_M)
    m.box([6.5, 2, 6.5], [9.5, 2.75, 9.5], M.CHROME_M)
    m.box([7.25, 2.75, 7.25], [8.75, 3.1, 8.75], M.CHROME_M)
    m.box([7.75, 3.1, 7.75], [8.25, 3.6, 8.25], M.CHROME_M)
    return m


service_bell()

# ================================================================================================ cake case
A_CAKE = Atlas('cake_case')


def _cake_case_base(w, h, face):
    a = m_plastic(w, h, WHITEPLASTIC, 6201, base=4.4)
    if face != 'h' and h >= 4:
        for y in range(1, h - 1, 2):
            hline(a, 2, w - 3, y, WHITEPLASTIC[1])
        rect(a, 0, h - 1, w - 1, h - 1, '#4a4a46')
    return a


def _case_glass(w, h, face):
    a = blank(w, h, C('#cfe0e4', 46))
    x, y = grid(w, h)
    st = ((x + y * 0.6) % 9) < 0.9
    a[st] = C('#f0f8fa', 90)
    return a


def cake_case():
    m = Model('cake_case', A_CAKE, render_type='translucent')
    BASE = M.custom('case_base', _cake_case_base)
    GL = M.custom('case_glass', _case_glass)
    FR = M.CHROME_M
    m.box([0.5, 0, 1], [15.5, 6, 15], BASE)
    m.box([1, 6, 1.5], [15, 6.3, 14.5], solid('case_floor', '#e8eef0'), only=('up',), glow=('up',))
    m.box([0.5, 6, 1], [15.5, 15, 15], GL, skip=('up', 'down'))
    m.box([0.5, 15, 1], [15.5, 16, 15], M.WHITE_M)
    m.box([1, 14.6, 1.5], [15, 15, 14.5], lit('case_light', '#f4fbff'), only=('down',), glow=('down',))
    for (x0, z0) in ((0.5, 1), (14.5, 1), (0.5, 14), (14.5, 14)):
        m.box([x0, 6, z0], [x0 + 1, 15, z0 + 1], FR)
    m.box([1, 10.5, 1.5], [15, 10.8, 14.5], solid('glass_shelf', C('#dfeef2', 120)), only=('up', 'down'))
    # cakes (lit by the case: emissive so the case glows in the dark dining room)
    CREAM_ = solid('cake_cream', '#f6f1e6')
    CHOC = solid('cake_choc', '#4a2414')
    CHEESE = solid('cake_cheese', '#f0d690')
    MONT = solid('cake_mont', '#c8a060')
    STRAW = solid('strawberry', '#d8202a')
    g = 'all'
    m.box([2, 6.3, 3.5], [6.5, 8.8, 8], M.custom('shortcake', lambda w, h, f: _cake_side(w, h, f)), glow=g)
    for (x, z) in ((2.6, 4.2), (4.6, 4.4), (3.4, 6.6), (5.4, 6.8)):
        m.box([x, 8.8, z], [x + 0.8, 9.5, z + 0.8], STRAW, glow=g)
    m.box([8, 6.3, 4], [10, 8.5, 7], CHOC, glow=g)
    m.box([8, 8.5, 4], [10, 8.8, 7], solid('choc_top', '#2a1208'), glow=g)
    m.box([11, 6.3, 4.5], [14, 7.8, 7.5], CHEESE, glow=g)
    m.box([11, 7.8, 4.5], [14, 8.0, 7.5], solid('cheese_top', '#c88a3a'), glow=g)
    for i, x in enumerate((2.5, 5.5, 8.5, 11.5)):
        mat = (MONT, CREAM_, CHOC, solid('pudding', '#e8b84a'))[i]
        m.box([x, 10.8, 5], [x + 2, 12.6, 7], mat, glow=g)
    # price tags
    for x in (3, 8.5, 12):
        m.box([x, 6.3, 2.2], [x + 1.6, 7.3, 2.3], solid('tag', '#ffffff'), only=('north', 'south'), glow=g)
    return m


def _cake_side(w, h, face):
    a = blank(w, h, '#f6f1e6')
    if face != 'h' and h >= 3:
        hline(a, 0, w - 1, h // 2, '#e8c8a0')
        hline(a, 0, w - 1, h // 2 + 1, '#d23a3a')
    return a


cake_case()

# ================================================================================================ business phone
A_PHONE = Atlas('business_phone')


def _phone_top(w, h, face):
    a = m_plastic(w, h, BEIGE, 6301, base=3.6)
    if face == 'h' and w >= 6:
        rect(a, 1, 1, w - 2, 2, '#2c3a2a')            # LCD
        hline(a, 2, 4, 1, '#5a7a50')
        for y in range(4, h - 1, 2):
            for x in range(1, w - 1, 2):
                a[y, x] = C('#ece6d4')
    return a


def business_phone(ringing):
    name = 'business_phone_ringing' if ringing else 'business_phone'
    m = Model(name, A_PHONE, display=DISP_COUNTERTOP)
    B = M.BEIGE_M
    m.box([4, 0, 4], [12, 2, 12], B, faces={'up': M.custom('phone_top', _phone_top)})
    m.box([4, 2, 9.5], [12, 2.5, 12], B)
    hs = []
    hs.append(([3.5, 2.5, 9.6], [12.5, 3.6, 11.6], B))
    hs.append(([3.5, 2.5, 9.4], [5.5, 4.1, 11.8], B))
    hs.append(([10.5, 2.5, 9.4], [12.5, 4.1, 11.8], B))
    rot = ('z', 22.5, [12.5, 2.5, 10.6]) if ringing else None
    for fr, to, mat in hs:
        m.box(fr, to, mat, rot=rot)
    m.box([2.5, 0, 6], [4, 1, 11], M.custom('cord', lambda w, h, f: blank(w, h, '#a89a80')))
    LED = lit('phone_led_on', '#ff3a22') if ringing else solid('phone_led_off', '#4a1a14')
    m.box([10.5, 2, 4.5], [11.3, 2.3, 5.3], LED, glow=('up', 'north') if ringing else ())
    return m


business_phone(False)
business_phone(True)

# ================================================================================================ reservation book
A_RES = Atlas('reservation_book')


def _ledger_page(seed, right=False):
    def fn(w, h):
        r = rng(seed)
        a = blank(w, h, '#efeadb')
        for y in range(2, h, 2):
            hline(a, 0, w - 1, y, '#c9c3b0')
        # handwriting: the same entry every night (2:00 1名 ミウラ様) in the same hand, rows of marks
        for y in range(1, h - 1, 2):
            x = 1
            while x < w - 2:
                if r.random() < 0.7:
                    px(a, x, y, '#2a2a40')
                x += 1 + int(r.integers(0, 2))
        if right:
            # one entry circled in red
            frame(a, 1, 8, w - 2, 12, '#c0281c')
        return a
    return fn


def reservation_book():
    m = Model('reservation_book', A_RES, display=disp(gui=(60, 200, 0), gui_scale=0.9, gui_t=(0, 2, 0)))
    A_RES.add('page_l', 12, 20, _ledger_page(6401))
    A_RES.add('page_r', 12, 20, _ledger_page(6402, right=True))
    COVER = M.custom('ledger_cover', lambda w, h, f: bevel(m_fabric(w, h, ramp(['#0c1a12', '#14281c', '#1e3626', '#284430'], 4), 6403)))
    m.box([2, 0, 3], [14, 0.5, 13], COVER)
    m.box([2.5, 0.5, 3.5], [7.9, 1.2, 12.5], solid('page_edge', '#e2dccb'), faces={'up': {'r': 'page_l'}})
    m.box([8.1, 0.5, 3.5], [13.5, 1.0, 12.5], solid('page_edge', '#e2dccb'), faces={'up': {'r': 'page_r'}})
    m.box([3, 0.5, 13.5], [12, 1.0, 14], solid('pen', '#1a1a2a'), rot=('y', -22.5, [7.5, 0.5, 13.75]))
    return m


reservation_book()

# ================================================================================================ capsule machines
A_GACHA = Atlas('capsule_machine', cutout=True)


def _gacha_front(seed, body):
    def fn(w, h):
        r = rng(seed)
        a = blank(w, h, body)
        # header picture (toys on yellow), capsule window, coin plate, outlet
        rect(a, 1, 1, w - 2, 3, '#f8d838')
        for k in range(4):
            px(a, 2 + k * 3, 2, RNG_COLORS[int(r.integers(0, 7))])
            px(a, 3 + k * 3, 2, RNG_COLORS[int(r.integers(0, 7))])
        rect(a, 1, 4, w - 2, 7, '#1a2a30')
        for y in range(4, 8):
            for x in range(1, w - 1):
                if r.random() < 0.75:
                    a[y, x] = C(RNG_COLORS[int(r.integers(0, len(RNG_COLORS)))])
                    if r.random() < 0.3:
                        dl(a[y:y + 1, x:x + 1], 0.12)
        rect(a, 2, 8, w - 3, 10, '#b8bcbe')
        px(a, 3, 9, '#2a2a2a')
        rect(a, w // 2 - 1, h - 1, w // 2 + 1, h - 1, '#101010')
        frame(a, 0, 0, w - 1, h - 1, '#3a3a3a')
        return a
    return fn


def capsule_machine():
    A_GACHA.add('front_a', 12, 11, _gacha_front(6501, '#c8281c'))
    A_GACHA.add('front_b', 12, 11, _gacha_front(6502, '#2a5ab0'))
    m = Model('capsule_machine', A_GACHA, render_type='cutout', display=disp(gui=(30, 225, 0), gui_scale=0.42, gui_t=(0, -2, 0)))
    RED = M.paint('gacha_red', ramp(['#3a0806', '#6a0e0a', '#9a1a12', '#c8281c', '#e04a32'], 5), base=3.0, scuffs=0.04)
    BLUE = M.paint('gacha_blue', ramp(['#0a1430', '#122a5a', '#1e408a', '#2a5ab0', '#4a7ad0'], 5), base=3.0, scuffs=0.04)
    m.box([1.5, 0, 3], [14.5, 1, 13], M.GREY_DARK)                                     # stand
    m.box([2, 1, 3.5], [14, 12, 12.5], RED, faces={'north': 'front_a'})
    m.box([2, 12, 3.5], [14, 23, 12.5], BLUE, faces={'north': 'front_b'})
    for y in (8.6, 19.6):
        m.box([7, y, 2.6], [9, y + 2, 3.5], M.CHROME_M)                                 # handles
        m.box([6, y + 0.6, 2.2], [10, y + 1.4, 2.6], M.CHROME_M)
    return m


capsule_machine()

# ================================================================================================ waiting bench
A_BENCH = Atlas('waiting_bench')


def waiting_bench():
    m = Model('waiting_bench', A_BENCH)
    V = M.vinyl('bench_vinyl', ramp(['#1a100a', '#2a1a10', '#3a2416', '#4a301e', '#5a3a26', '#6a462e'], 6), piping=True)
    m.box([0, 5, 3], [16, 7.5, 13], V)
    m.box([0, 4, 3.5], [16, 5, 12.5], M.DARKWOOD)
    for x0 in (1.5, 13):
        for z0 in (4, 10.5):
            m.box([x0, 0, z0], [x0 + 1.5, 4, z0 + 1.5], M.CHROME_M, skip=('up', 'down'))
    m.box([1.5, 1, 5.5], [14.5, 1.75, 10.5], M.CHROME_M, only=('up', 'north', 'south'))
    return m


waiting_bench()

# ================================================================================================ magazine rack
A_MAG = Atlas('magazine_rack')


def _mag_cover(seed):
    def fn(w, h):
        r = rng(seed)
        a = blank(w, h, RNG_COLORS[seed % len(RNG_COLORS)])
        rect(a, 0, 0, w - 1, 1, '#f4f4ec' if seed % 2 else '#1a1a1a')
        rect(a, 1, 3, w - 2, h - 2, '#d8b89a' if seed % 3 else '#8ab0c8')
        px(a, w // 2, h // 2, '#3a2a20')
        hline(a, 1, w - 3, h - 2, '#f4f4ec')
        return a
    return fn


def magazine_rack():
    for i in range(4):
        A_MAG.add(f'mag{i}', 5, 6, _mag_cover(6601 + i))
    m = Model('magazine_rack', A_MAG)
    W = M.DARKWOOD
    m.box([2, 0, 7], [3, 14, 13], W)
    m.box([13, 0, 7], [14, 14, 13], W)
    m.box([3, 0, 12], [13, 13, 13], W, only=('north', 'south', 'up'))
    for k, (y, z) in enumerate(((1, 8.5), (5.5, 9.5), (10, 10.5))):
        m.box([3, y, z - 1.5], [13, y + 1, z + 2.5], W, only=('up', 'north', 'down'))
        m.box([3, y + 1, z - 1.5], [13, y + 2.5, z - 1], W)
        m.box([3.5, y + 1, z], [8, y + 6, z + 0.4], M.CREAM_M, faces={'north': f'mag{k}'},
              rot=('x', -22.5, [5.75, y + 1, z]))
        m.box([8.2, y + 1, z + 0.2], [12.7, y + 5.5, z + 0.6], M.CREAM_M, faces={'north': f'mag{(k + 1) % 4}'},
              rot=('x', -22.5, [10.4, y + 1, z + 0.4]))
    return m


magazine_rack()

# ================================================================================================ umbrella stand
A_UMB = Atlas('umbrella_stand')


def _lock_front(w, h, face):
    a = m_brushed(w, h, STEEL, 6701, along='x')
    if face != 'h':
        for i in range(4):
            x0 = 1 + i * 4
            rect(a, x0, 2, x0 + 1, 3, '#e8721f')
            px(a, x0, 2, '#ffd0a0')
            px(a, x0 + 2, 3, '#2a2a2a')
    return a


def umbrella_stand():
    m = Model('umbrella_stand', A_UMB)
    m.box([0.5, 0, 5], [15.5, 8, 11], M.STEEL_M, faces={'north': M.custom('lockfront', _lock_front)})
    m.box([0.5, 8, 5], [15.5, 8.5, 11], M.GREY_DARK)
    m.box([0.5, 0.5, 4.5], [15.5, 1.5, 11.5], M.custom('tray', lambda w, h, f: blank(w, h, '#2a2a2c')))
    NAVY = M.fabric('umb_navy', ramp(['#0a0e1a', '#121a2c', '#1a2440', '#243052'], 4))
    CLEAR = M.glass('umb_clear', '#e8f0f2', 120)
    YEL = M.fabric('umb_yellow', ramp(['#7a5a08', '#a87e10', '#d8a818', '#f0c830'], 4))
    for (x, mat, top, hook) in ((2.5, NAVY, 17, True), (6.5, CLEAR, 18, True), (10.5, YEL, 14, False)):
        m.box([x, 8, 7], [x + 2, top, 9], mat)
        m.box([x + 0.75, top, 7.75], [x + 1.25, top + 1.5, 8.25], M.custom('shaft', lambda w, h, f: blank(w, h, '#3a3a3a')))
        if hook:
            m.box([x + 0.5, top + 1.5, 7.5], [x + 2.5, top + 2.2, 8.5], solid('handle', '#5a3a1e'))
            m.box([x + 1.9, top + 0.8, 7.5], [x + 2.5, top + 1.5, 8.5], solid('handle', '#5a3a1e'))
    return m


umbrella_stand()


# ================================================================================================ notice board
A_NOTICE = Atlas('notice_board', cutout=True)


def _notice_face(w, h):
    """2x-density cork board with flyers: the 2001 lost-child flyer, part-time job ad, a faded festival poster."""
    r = rng(6801)
    cork = ramp(['#5a3c22', '#6e4a2a', '#825a34', '#946a3e'], 4)
    a = from_idx(1.8 + noise(w, h, 6802, beta=0.3, wrap=False) * 0.8, cork)
    frame(a, 0, 0, w - 1, h - 1, '#a8acae')
    frame(a, 1, 1, w - 2, h - 2, '#7a7e80')
    # lost-child flyer (yellowed): 「さがしています」, a photo of a girl in a yellow hat
    fl = blank(11, 15, '#e8e0c4')
    stamp_center(fl, text_mask('迷子', 5, FONT_SANS_JP_BOLD, binary=True), 5.5, 2.5, '#b8241a')
    rect(fl, 2, 5, 8, 11, '#c8c4b0')
    rect(fl, 3, 6, 7, 7, '#f0c828')          # yellow hat
    rect(fl, 4, 8, 6, 9, '#e8c0a0')          # face
    rect(fl, 3, 10, 7, 11, '#8ab0d8')        # blue smock
    for y in (12, 13):
        hline(fl, 1, 9, y, '#6a6458')
    tint(fl, '#b89a5a', 0.18)
    over(a, fl, 3, 4)
    px(a, 8, 4, '#d8281c')
    # part-time job ad (orange)
    jb = blank(10, 9, '#f6efe0')
    rect(jb, 0, 0, 9, 2, '#e8721f')
    for y in (4, 6, 8):
        hline(jb, 1, 8, y, '#5a5048')
    over(a, jb, 16, 3)
    px(a, 20, 3, '#2a6ad8')
    # festival poster (faded blue), a calendar sheet
    fp = blank(9, 10, '#7a9ac0')
    disc = disc_mask(9, 10, 4.5, 4, 2.6)
    fp[disc] = C('#e8d090')
    hline(fp, 1, 7, 8, '#f4f0e0')
    tint(fp, '#c8c0b0', 0.3)
    over(a, fp, 16, 13)
    cal = blank(8, 8, '#f8f8f4')
    for y in range(2, 8, 2):
        for x in range(1, 8, 2):
            px(cal, x, y, '#8a8a8a')
    rect(cal, 0, 0, 7, 1, '#c8281c')
    over(a, cal, 5, 20) if h > 27 else None
    return a


def notice_board():
    A_NOTICE.add('face', 28, 24, _notice_face)
    m = Model('notice_board', A_NOTICE, render_type='cutout', display=DISP_WALL)
    m.box([1, 2, 15], [15, 14, 16], M.ALU_M, faces={'north': 'face'})
    return m


notice_board()


# ================================================================================================ call display
A_CALLD = Atlas('call_display')


def led_face(content, W=28, H=16):
    """2x-density red LED dot-matrix display: content = int (0 = blank) or 'closed'."""
    a = blank(W, H, '#141414')
    frame(a, 0, 0, W - 1, H - 1, '#2a2a2a')
    # label strip
    rect(a, 2, 1, W - 3, 2, '#2c2c2c')
    win = blank(W - 4, H - 6, '#1c0606')
    ww, wh = win.shape[1], win.shape[0]
    for y in range(wh):
        for x in range(ww):
            if (x + y) % 2 == 0:
                win[y, x] = C('#2c0a08')
    on = '#ff3a22'
    if content == 'closed':
        g = text_mask('閉店', 8, FONT_SANS_JP_BOLD, binary=True)
        stamp_center(win, g, ww / 2, wh / 2, on)
    elif content:
        s = str(content)
        x = (ww - (len(s) * 6 - 1)) // 2
        for ch in s:
            stamp_mask(win, bitmap(DIGIT57[ch]), x, (wh - 7) // 2, on)
            x += 6
    # bloom: lit dots light their neighbours a little
    lm = (win[..., 0] > 200)
    glow = np.zeros(lm.shape, bool)
    glow[1:, :] |= lm[:-1, :]
    glow[:-1, :] |= lm[1:, :]
    glow[:, 1:] |= lm[:, :-1]
    glow[:, :-1] |= lm[:, 1:]
    glow &= ~lm
    win[glow] = C('#6a1008')
    over(a, win, 2, 4)
    # small speaker grille / brand dot
    px(a, W - 3, 1, '#e8721f')
    return a


def call_display():
    for n in range(18):
        A_CALLD.add(f'n{n}', 28, 16, lambda w, h, _n=n: led_face(_n))
    A_CALLD.add('closed', 28, 16, lambda w, h: led_face('closed'))
    BODY = M.paint('calld_body', BLACKPLASTIC, base=2.2, scuffs=0.02)
    for n in list(range(18)) + ['closed']:
        name = f'call_display_{n}' if n != 'closed' else 'call_display_closed'
        reg = f'n{n}' if n != 'closed' else 'closed'
        m = Model(name, A_CALLD, display=DISP_WALL, ao=False)
        m.box([1, 4, 13], [15, 12, 16], BODY, faces={'north': reg}, glow=('north',) if n != 0 else ())
        m.box([6, 12, 15], [10, 13, 16], BODY)          # bracket
    return None


call_display()


# ================================================================================================ drink bar
A_DRINK = Atlas('drink_bar')
DRINKS = [('#e8302a', 'ｺｰﾗ'), ('#f0c020', 'ﾚﾓﾝ'), ('#f08a1c', 'ｵﾚﾝｼﾞ'), ('#3aa84a', 'ﾒﾛﾝ'), ('#8a4ad8', 'ｸﾞﾚｰﾌﾟ'),
          ('#e8e8e0', 'ｶﾙﾋﾟ')]


def _drink_panel(lit_):
    def fn(w, h):
        a = blank(w, h, '#1a1a1e')
        n = len(DRINKS)
        cw = (w - 2) // n
        for i, (c, _) in enumerate(DRINKS):
            x0 = 1 + i * cw
            col = C(c)
            if not lit_:
                col[:3] *= 0.45
            rect(a, x0, 1, x0 + cw - 2, h - 3, col)
            # a glass of the drink with bubbles, logo band
            rect(a, x0 + 1, 3, x0 + cw - 3, h - 5, '#f4f8f8' if lit_ else '#6a6e70')
            rect(a, x0 + 1, 5, x0 + cw - 3, h - 5, col if lit_ else col)
            px(a, x0 + 2, 6, '#ffffff' if lit_ else '#9a9a9a')
            rect(a, x0, h - 2, x0 + cw - 2, h - 2, '#f2f2ea' if lit_ else '#5a5a58')
        return a
    return fn


def drink_bar(pouring):
    name = 'drink_bar_pouring' if pouring else 'drink_bar'
    if 'panel' not in A_DRINK.regions:
        A_DRINK.add('panel', 36, 12, _drink_panel(True))
        A_DRINK.add('stream', 1, 6, lambda w, h: blank(w, h, '#e8302a'))
    m = Model(name, A_DRINK, display=DISP_FRONT)
    BODY = M.paint('drink_body', ramp(['#2a2c2e', '#3c3e42', '#4e5256', '#62666a', '#767a7e'], 5), base=2.6, scuffs=0.03)
    m.box([1, 9, 3], [15, 15, 16], BODY, faces={'north': 'panel'}, glow=('north',))         # lit panel
    m.box([1, 15, 3], [15, 16, 16], M.STEEL_M)
    m.box([1, 6.5, 3], [15, 9, 16], M.STEEL_M)                                              # nozzle block
    m.box([1, 1, 9], [15, 6.5, 16], M.STEEL_DIRTY)                                          # splash panel (recess)
    m.box([1, 1, 3], [1.8, 6.5, 9], BODY)
    m.box([14.2, 1, 3], [15, 6.5, 9], BODY)
    m.box([1, 0, 3], [15, 1, 16], BODY)
    for i in range(6):
        x = 2.2 + i * 2.1
        m.box([x, 5.6, 5], [x + 1, 6.5, 6], M.CHROME_M)
        m.box([x + 0.2, 6.5, 3.4], [x + 0.8, 8.2, 4], M.custom('lever', lambda w, h, f: blank(w, h, '#1a1a1a')))
    m.box([1.8, 1, 3.5], [14.2, 1.8, 9], M.custom('drip', lambda w, h, f: _drip(w, h, f)))  # drip tray
    if pouring:
        m.box([2.5, 1.8, 5.3], [3.0, 5.6, 5.8], M.custom('cola_stream', lambda w, h, f: blank(w, h, '#7a1a10')),
              glow='all', shade=False)
    return m


def _drip(w, h, face):
    a = blank(w, h, '#2a2a2a')
    if face == 'h':
        for x in range(0, w, 2):
            vline(a, x, 0, h - 1, '#4a4a4a')
    return a


drink_bar(False)
drink_bar(True)

# ---------------------------------------------------------------------------------------------- coffee machine
A_COFFEE = Atlas('coffee_machine')


def _coffee_front(w, h):
    a = blank(w, h, '#1c1c1e')
    rect(a, 1, 1, w - 2, 4, '#3a2a20')
    hline(a, 2, w - 3, 2, '#c8a070')
    rect(a, 2, 6, w - 3, 7, '#0e2a1e')
    for k in range(4):
        px(a, 3 + k * 2, 6, '#3aff8a' if k == 0 else '#1a5a3a')
    for k in range(3):
        rect(a, 2 + k * 4, 9, 3 + k * 4, 10, '#c8c4bc')
    return a


def coffee_machine():
    A_COFFEE.add('front', 14, 12, _coffee_front)
    m = Model('coffee_machine', A_COFFEE, display=DISP_FRONT)
    B = M.paint('coffee_body', BLACKPLASTIC, base=2.2, scuffs=0.03)
    m.box([1, 4, 3], [15, 16, 15], B, faces={'north': 'front'})
    m.box([1, 0, 3], [15, 1, 15], B)
    m.box([1, 1, 12], [15, 4, 15], B)
    m.box([1, 1, 3], [2, 4, 12], B)
    m.box([14, 1, 3], [15, 4, 12], B)
    m.box([3, 1, 4], [13, 1.5, 11], M.custom('drip', _drip))
    m.box([7, 3, 6], [9, 4, 8], M.CHROME_M)
    m.box([6.5, 1.5, 6], [9.5, 3.5, 9], M.WHITE_M, skip=('up',))                         # a cup left behind
    return m


coffee_machine()

# ---------------------------------------------------------------------------------------------- ice machine
A_ICE = Atlas('ice_machine')


def ice_machine():
    m = Model('ice_machine', A_ICE, display=DISP_FRONT)
    S = M.STEEL_M
    m.box([1, 0, 2], [15, 16, 16], S)
    m.box([3, 7, 1.5], [13, 13, 2], M.custom('ice_label', lambda w, h, f: _ice_label(w, h)))
    m.box([6, 4, 0.5], [10, 7, 2], M.CHROME_M)                                           # chute
    m.box([3, 0.5, 1], [13, 1.5, 6], M.custom('drip', _drip))
    return m


def _ice_label(w, h):
    a = blank(w, h, '#2a6ab8')
    stamp_center(a, text35('ICE'), w / 2, h / 2, '#f2f8ff')
    return a


ice_machine()

# ---------------------------------------------------------------------------------------------- cup shelf
A_CUPS = Atlas('cup_shelf', cutout=False)


def cup_shelf():
    m = Model('cup_shelf', A_CUPS, render_type='translucent', display=DISP_FRONT)
    S = M.STEEL_M
    m.box([0.5, 0, 4], [15.5, 0.5, 15], S)
    m.box([0.5, 7, 4], [15.5, 7.5, 15], S)
    m.box([0.5, 0, 15], [15.5, 13, 16], S, only=('north', 'up', 'east', 'west'))
    for x0 in (0.5, 15):
        m.box([x0, 0.5, 4], [x0 + 0.5, 13, 15], S)
    GL = M.glass('cup_glass', '#dcecf0', 110)
    # stacked glasses (upside down) and stacks of white cups
    for i, x in enumerate((2, 5, 8)):
        for k in range(3):
            m.box([x, 0.5 + k * 2.1, 6], [x + 2, 2.5 + k * 2.1, 8], GL)
    for x in (11.5,):
        m.box([x, 0.5, 6], [x + 2.5, 6, 8.5], M.WHITE_M)
    for i, x in enumerate((2, 6, 10)):
        m.box([x, 7.5, 7], [x + 3, 9, 10], M.WHITE_M)
        m.box([x + 0.3, 9, 7.3], [x + 2.7, 10.5, 9.7], M.WHITE_M)
    return m


cup_shelf()

# ---------------------------------------------------------------------------------------------- soup warmer
A_SOUP = Atlas('soup_warmer')


def soup_warmer():
    m = Model('soup_warmer', A_SOUP, display=DISP_SMALL)
    POT = M.paint('soup_pot', ramp(['#1a1a1c', '#2a2a2e', '#3a3a40', '#4a4a52'], 4), base=1.8)
    m.box([2.5, 0, 2.5], [13.5, 2, 13.5], M.STEEL_M)
    m.box([3, 2, 3], [13, 10, 13], POT, faces={'north': M.custom('soup_label', lambda w, h, f: _soup_front(w, h))})
    m.box([3.5, 10, 3.5], [12.5, 11, 12.5], M.STEEL_M)
    m.box([7, 11, 7], [9, 12, 9], M.BLACK_M)
    m.box([12.5, 9, 7], [14, 9.5, 8.5], M.CHROME_M)                                  # ladle
    m.box([13.5, 9.5, 7.25], [14, 13, 8.25], M.CHROME_M)
    return m


def _soup_front(w, h):
    a = blank(w, h, '#2a2a2e')
    rect(a, 1, 2, w - 2, 4, '#e8721f')
    # bowl pictogram
    hline(a, 3, w - 4, 3, '#fff4e0')
    hline(a, 4, w - 5, 4, '#fff4e0')
    px(a, w - 2, h - 2, '#ff6a2a')
    return a


soup_warmer()

# ================================================================================================ server station
A_SERVER = Atlas('server_station', cutout=False)


def _cutlery(w, h, face):
    a = blank(w, h, '#3a3a3c')
    if face == 'h':
        for i in range(3):
            x0 = 1 + i * (w - 2) // 3
            rect(a, x0, 1, x0 + (w - 2) // 3 - 2, h - 2, '#c9cdd0')
            for y in range(1, h - 1, 2):
                hline(a, x0, x0 + (w - 2) // 3 - 2, y, '#9aa0a4')
    return a


def _cabinet_front(w, h, face):
    a = m_wood(w, h, WOOD_DARK, 6901, along='y', base=3.2)
    vline(a, w // 2, 1, h - 2, WOOD_DARK[0])
    frame(a, 0, 0, w - 1, h - 1, WOOD_DARK[1])
    px(a, w // 2 - 2, h // 3, '#c8b070')
    px(a, w // 2 + 1, h // 3, '#c8b070')
    rect(a, 0, h - 1, w - 1, h - 1, '#0e0806')
    return a


def server_station():
    m = Model('server_station', A_SERVER, render_type='translucent')
    m.box([0, 0, 1], [16, 15, 16], M.DARKWOOD, faces={'north': M.custom('cab_front', _cabinet_front)}, skip=('up',))
    m.box([0, 15, 0], [16, 16, 16], M.GRANITE_M)
    m.box([1, 16, 2], [8, 17.5, 7], M.custom('cutlery', _cutlery))                     # cutlery bins
    # water pitchers
    PIT = M.glass('pitcher', '#cfe4ea', 100)
    for x in (9.5, 12.5):
        m.box([x, 16, 2.5], [x + 2.5, 21, 5], PIT)
        m.box([x + 0.3, 16.2, 2.8], [x + 2.2, 19.5, 4.7], M.glass('pitcher_water', '#a8ccd6', 120))
        m.box([x + 2.5, 18.5, 3.5], [x + 3, 20.5, 4], PIT)
    # stacked trays (brown, non-slip)
    m.box([1, 16, 9], [12, 18, 15], M.paint('tray', ramp(['#1a0f08', '#2a180c', '#3a2414', '#4a2f1c'], 4), base=2.0))
    # napkins
    m.box([12.5, 16, 9.5], [15, 17.5, 13], M.WHITE_M)
    return m


server_station()


# ================================================================================================ kids' corner
A_KIDS = Atlas('kids_mat')


def _puzzle(w, h):
    cols = ['#d84a3a', '#3a7ad8', '#e8c03a', '#4aa85a']
    a = blank(w, h)
    for ty in range(2):
        for tx in range(2):
            c = cols[(tx + ty * 2) % 4]
            rect(a, tx * 8, ty * 8, tx * 8 + 7, ty * 8 + 7, c)
            # jigsaw tabs
            px(a, tx * 8 + 7, ty * 8 + 3, cols[(tx + 1 + ty * 2) % 4])
            px(a, tx * 8 + 7, ty * 8 + 4, cols[(tx + 1 + ty * 2) % 4])
            px(a, tx * 8 + 3, ty * 8 + 7, cols[(tx + (ty + 1) * 2) % 4])
            px(a, tx * 8 + 4, ty * 8 + 7, cols[(tx + (ty + 1) * 2) % 4])
    n = noise(w, h, 7001, beta=1.2, wrap=False)
    tint(a, '#6a6050', np.clip(0.18 + n * 0.06, 0, 0.3))
    # a crayon drawing left on the mat
    for (x, y) in ((3, 10), (4, 11), (5, 11), (6, 10)):
        px(a, x, y, '#2a2a2a')
    return a


def kids_mat():
    A_KIDS.add('mat', 16, 16, _puzzle)
    m = Model('kids_mat', A_KIDS, display=DISP_FLOOR)
    m.box([0, 0, 0], [16, 0.5, 16], solid('mat_edge', '#8a8070'), faces={'up': 'mat'})
    return m


kids_mat()

A_TOYS = Atlas('toy_shelf')


def toy_shelf():
    m = Model('toy_shelf', A_TOYS)
    WH = M.plastic('shelf_white', WHITEPLASTIC, base=4.2)
    m.box([1, 0, 6], [15, 11, 13], WH, skip=('north',))
    m.box([1, 0, 6], [2, 11, 13], WH)
    m.box([14, 0, 6], [15, 11, 13], WH)
    m.box([1, 5, 6], [15, 6, 13], WH)
    m.box([1, 10, 6], [15, 11, 13], WH)
    m.box([2, 0, 12], [14, 10, 13], M.custom('shelf_back', lambda w, h, f: blank(w, h, '#b8b4a8')))
    # picture books on the top shelf
    r = rng(7101)
    x = 2.2
    for i in range(6):
        wdt = 0.9 + r.random() * 0.6
        hh = 3 + r.random() * 1.2
        c = RNG_COLORS[i % len(RNG_COLORS)]
        m.box([x, 6, 7.5], [x + wdt, 6 + hh, 11.5], solid(f'book{i}', c))
        x += wdt + 0.1
    # building blocks, a ball and a toy car on the lower shelf
    for i, (bx, bz, by) in enumerate(((2.5, 7, 0), (4.2, 7.5, 0), (3.3, 7.2, 1.6))):
        m.box([bx, by, bz], [bx + 1.6, by + 1.6, bz + 1.6], solid(f'block{i}', RNG_COLORS[i]))
    m.box([7, 0, 7.5], [10, 3, 10.5], M.custom('ball', lambda w, h, f: _ball(w, h)))
    m.box([10.8, 0, 8], [13.6, 1.4, 10], solid('car', '#2a6ad8'))
    m.box([11.4, 1.4, 8.2], [13, 2.2, 9.8], solid('car_top', '#9ac8f0'))
    # a book fallen out on top
    m.box([3, 11, 7], [7.5, 11.5, 10], solid('book_top', '#e8721f'), rot=('y', 22.5, [5, 11, 8.5]))
    return m


def _ball(w, h):
    a = blank(w, h, '#d8302a')
    hline(a, 0, w - 1, h // 2, '#f0f0e8')
    px(a, 0, 0, '#f06a5a')
    return a


toy_shelf()

A_BEAR = Atlas('stuffed_bear')


def _fur(w, h, face):
    a = m_fabric(w, h, ramp(['#2a1a0e', '#3e2614', '#54341c', '#6a4426', '#7e5430'], 5), 7201, base=2.6)
    return bevel(a, 0.04, -0.06)


def _bear_face(w, h):
    a = _fur(w, h, 'v')
    # button eyes (one hanging lower, the stitching came loose)
    px(a, 1, 1, '#0a0a0a')
    px(a, w - 2, 2, '#0a0a0a')
    px(a, w - 2, 1, '#6a5a4a')
    return a


def stuffed_bear():
    A_BEAR.add('face', 5, 4, _bear_face)
    A_BEAR.add('snout', 2, 2, lambda w, h: (lambda a: (px(a, 0, 0, '#1a0e08'), px(a, 1, 0, '#1a0e08'), a)[2])(
        blank(w, h, '#c8a478')))
    m = Model('stuffed_bear', A_BEAR, display=disp(gui=(15, 200, 0), gui_scale=1.1, gui_t=(0, 2, 0)))
    F = M.custom('fur', _fur)
    m.box([5, 0, 7], [11, 6, 11], F)                                     # body
    m.box([5.5, 6, 6.5], [10.5, 10.5, 10.5], F, faces={'north': {'r': 'face', 'sub': (0, 0, 5, 4)}})
    m.box([7, 6.8, 5.6], [9, 8.6, 6.5], F, faces={'north': 'snout'})
    m.box([5.3, 10, 7.5], [6.8, 11.5, 9], F)                             # ears
    m.box([9.2, 10, 7.5], [10.7, 11.5, 9], F)
    m.box([3.5, 2, 7.5], [5, 5.5, 9.5], F)                               # arms
    m.box([11, 2, 7.5], [12.5, 5.5, 9.5], F)
    m.box([5.5, 0, 4], [7.5, 2, 7.5], F, faces={'north': solid('paw', '#c8a478')})
    m.box([8.5, 0, 4], [10.5, 2, 7.5], F, faces={'north': solid('paw', '#c8a478')})
    m.box([6.5, 5.6, 6.6], [9.5, 6.4, 7.2], solid('ribbon', '#c82838'))  # red ribbon
    m.box([7.4, 5.2, 6.4], [8.6, 6.8, 6.8], solid('ribbon', '#c82838'))
    return m


stuffed_bear()

A_FENCE = Atlas('kids_fence')


def kids_fence():
    m = Model('kids_fence', A_FENCE)
    cols = ['#d84a3a', '#e8c03a', '#3a7ad8', '#4aa85a']
    WH = M.plastic('fence_white', WHITEPLASTIC, base=4.4)
    m.box([0, 8.5, 7], [16, 10, 9], WH)
    m.box([0, 1, 7.25], [16, 2, 8.75], WH)
    for i in range(4):
        x = 0.75 + i * 4
        m.box([x, 0, 7.4], [x + 2, 10.5, 8.6], solid(f'picket{i}', cols[i]))
        m.box([x + 0.5, 10.5, 7.4], [x + 1.5, 11, 8.6], solid(f'picket{i}', cols[i]))
    return m


kids_fence()

A_HIGH = Atlas('high_chair')


def high_chair():
    m = Model('high_chair', A_HIGH, display=disp(gui=(30, 225, 0), gui_scale=0.45, gui_t=(0, -2, 0)))
    L = M.LIGHTWOOD
    for x0 in (3.5, 11):
        for z0 in (3.5, 11):
            m.box([x0, 0, z0], [x0 + 1.5, 11, z0 + 1.5], L)
    m.box([4, 3, 4], [12, 3.75, 4.75], L)
    m.box([4, 3, 11.25], [12, 3.75, 12], L)
    m.box([3.5, 11, 3.5], [12.5, 12, 12.5], L)                                       # seat
    m.box([3.5, 12, 11], [12.5, 20, 12.5], L)                                        # back
    m.box([3.5, 12, 3.5], [5, 15, 11], L)                                            # arms
    m.box([11, 12, 3.5], [12.5, 15, 11], L)
    m.box([2.5, 15, 1.5], [13.5, 15.75, 6], L)                                       # tray
    m.box([4.5, 12, 4.5], [11.5, 13, 10.5], M.vinyl('hc_pad', ramp(['#2a1a10', '#4a2e1a', '#6a4226', '#8a5a34'], 4)))
    return m


high_chair()
