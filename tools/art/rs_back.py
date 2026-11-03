"""Back-of-house props: staff room, office, corridor, storage (models face north). The pc sits on an office_desk
(desk top = 12 px: its elements start at y = -4)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, bitmap, m_brushed,
                    m_wood, m_plastic, m_paint, m_cardboard, m_fabric, STEEL, ALU, CHROME, WHITEPLASTIC, BLACKPLASTIC,
                    GREYPAINT, CARDBOARD, BEIGE, WOOD_LIGHT, WOOD_DARK, ORANGE, PAPERW, DIGIT57)
from common import FONT_SANS_JP_BOLD
import rs_mats as M
from rs_waiting import solid, lit

LOCKER = ramp(['#2c3134', '#3d4347', '#4f565a', '#62696d', '#757c80', '#888f92'], 6)


# ================================================================================================ animated screens
def _static(w, h, fi):
    r = rng(9001 + fi)
    v = r.random((h, w))
    a = blank(w, h)
    g = (v * 160 + 50)[..., None]
    a[..., :3] = g
    a[..., 3] = 255
    bar = (fi * 3) % h
    a[bar:bar + 2, :, :3] *= 1.25
    for y in range(0, h, 2):
        a[y, :, :3] *= 0.85
    a[..., 2] *= 1.05
    return a


A_STATIC = Atlas('crt_static', frames=8, frametime=1)
A_STATIC.add('static', 16, 16, _static)


def _cam_view(kind, fi, alert=False):
    """14x12 grey-green security camera view of a room of HIDAMARI (2x density), with noise and a time stamp."""
    r = rng(9101 + fi * 7 + kind)
    W, H = 14, 12
    a = blank(W, H, '#0c120e')
    G = ramp(['#0c120e', '#1a261e', '#2a3a2e', '#3c5040', '#526a56', '#6c8670'], 6)
    if kind == 0:     # entrance: two door frames, the mat
        rect(a, 2, 2, 5, 10, G[2])
        rect(a, 8, 2, 11, 10, G[2])
        rect(a, 3, 3, 4, 10, G[1])
        rect(a, 9, 3, 10, 10, G[1])
        rect(a, 1, 10, 12, 11, G[3])
    elif kind == 1:   # dining aisle: booths receding
        for k in range(4):
            x0 = 1 + k * 2
            rect(a, x0, 4 + k, x0 + 1, 9, G[3 - k // 2])
        rect(a, 0, 9, 13, 11, G[2])
        rect(a, 9, 3, 12, 6, G[4])                    # the lit drink bar
    elif kind == 2:   # kitchen line: hood and the range
        rect(a, 1, 2, 12, 4, G[3])
        rect(a, 2, 6, 11, 9, G[2])
        for x in (3, 6, 9):
            px(a, x, 6, G[5])
    else:             # corridor
        for k in range(5):
            rect(a, k, k, W - 1 - k, H - 1 - k, G[1 + (k % 2)])
        rect(a, 6, 3, 7, 8, G[0])
    # noise, scanlines, a glitch line
    n = r.random((H, W)) < 0.12
    a[n] = G[int(r.integers(1, 5))]
    for y in range(0, H, 2):
        a[y, :, :3] *= 0.86
    gy = int(r.integers(0, H))
    if fi % 2 == 0:
        a[gy, :, :3] = a[gy, :, :3] * 0.5 + 90
    # time stamp 02:00 bottom-left (tiny dots) and camera number
    for k, x in enumerate((1, 2, 4, 5)):
        px(a, x, H - 2, '#d8e8d8')
    px(a, W - 2, 1, '#d8e8d8')
    if alert:
        tint(a, '#5a0a08', 0.35)
        frame(a, 0, 0, W - 1, H - 1, '#e81c14')
        if fi % 2 == 0:
            px(a, 1, 1, '#ff2a1a')
            px(a, 2, 1, '#ff2a1a')
    return a


A_FEED = Atlas('monitor_feed', frames=4, frametime=4)
for _k in range(4):
    A_FEED.add(f'view{_k}', 14, 12, lambda w, h, fi, _k=_k: _cam_view(_k, fi))
    A_FEED.add(f'alert{_k}', 14, 12, lambda w, h, fi, _k=_k: _cam_view(_k, fi, alert=True))


# ================================================================================================ lockers
A_LOCK = Atlas('locker')


def _locker_door(upper):
    def fn(w, h, face):
        a = m_paint(w, h, LOCKER, 9201 + (1 if upper else 0), base=3.2, scuffs=0.06)
        frame(a, 0, 0, w - 1, h - 1, LOCKER[1])
        dl(a[:, 1:2], 0.04)
        if upper:
            for y in (2, 4, 6):                      # vent slits
                hline(a, 3, w - 4, y, '#15181a')
                hline(a, 3, w - 4, y + 1, LOCKER[4])
            rect(a, w // 2 - 2, 9, w // 2 + 1, 10, '#e8e6dc')    # name card
            px(a, w // 2 - 1, 9, '#5a5a6a')
            rect(a, w - 4, 12, w - 3, 15, '#b8bcbe')             # handle
            px(a, w - 4, 13, '#7a7e80')
        else:
            rect(a, 0, h - 1, w - 1, h - 1, '#121416')
            for y in (h - 4, h - 3):
                hline(a, 3, w - 4, y, '#15181a')
            wear(a, 9203, amount=0.08, edge_only=False)
        return a
    return M.custom('locker_door_up' if upper else 'locker_door_lo', fn)


def _locker_inside(w, h, face):
    a = m_paint(w, h, LOCKER, 9204, base=1.4, scuffs=0.02)
    return a


def locker(part, open_):
    name = f'locker_{part}' + ('_open' if open_ else '')
    m = Model(name, A_LOCK, display=disp(gui=(30, 225, 0), gui_scale=0.55 if part == 'lower' else 0.55))
    BODY = M.paint('locker_body', LOCKER, base=3.0, scuffs=0.04)
    IN = M.custom('locker_in', _locker_inside)
    DOOR = _locker_door(part == 'upper')
    top = 16 if part == 'lower' else 15.5
    # body shell (open front)
    m.box([1, 0, 2], [15, top, 16], BODY, skip=('north',) if open_ else (), faces={'north': DOOR} if not open_ else None)
    if open_:
        # interior faces seen through the opening
        m.box([1.6, 0, 15.4], [14.4, top, 16], IN, only=('north',))
        m.box([1, 0, 2], [1.6, top, 15.4], IN, only=('east',))
        m.box([14.4, 0, 2], [15, top, 15.4], IN, only=('west',))
        m.box([1.6, 0, 2], [14.4, 0.4, 15.4], IN, only=('up',))
        # the door swung open on its left hinge (x = 1), sticking out to the north
        m.box([0, 0 if part == 'lower' else 0, -12], [1, top, 2], BODY, faces={'west': DOOR, 'east': IN})
        if part == 'upper':
            m.box([1.6, 11, 2.5], [14.4, 11.6, 15.4], BODY)                              # shelf
            m.box([7.5, 9, 14], [8.5, 10, 15.4], M.CHROME_M)                              # hook
            APRON = M.paint('apron', ORANGE, base=3.6, scuffs=0.02)
            m.box([5, 1, 13.5], [11, 9.5, 14.2], APRON)                                   # an old orange apron
            m.box([7.5, 9.2, 13.5], [8.5, 10, 14.2], APRON)
        else:
            m.box([3, 0, 6], [7, 1.5, 12], solid('shoe', '#1a1612'))                      # a pair of shoes
            m.box([8, 0, 6.5], [12, 1.5, 12.5], solid('shoe', '#1a1612'))
            m.box([2, 14.5, 3], [14, 15.5, 14], solid('locker_bag', '#3a3a52'))
    if part == 'upper':
        m.box([0.5, 15.5, 1.5], [15.5, 16, 16], BODY)
    else:
        m.box([1, 0, 1.6], [15, 1, 2], solid('kick', '#15181a'), only=('north',)) if not open_ else None
    return m


for _p in ('lower', 'upper'):
    for _o in (False, True):
        locker(_p, _o)

# ================================================================================================ time recorder
A_TIME = Atlas('time_recorder')


def _tr_face(w, h):
    a = m_plastic(w, h, BEIGE, 9301, base=3.8)
    rect(a, 2, 2, w - 3, 6, '#0c1a0e')
    # "2:00" in tiny segments
    x = 3
    for ch in '200':
        stamp_mask(a, bitmap(['###', '#.#', '#.#', '#.#', '###'] if ch == '0' else ['###', '..#', '###', '#..', '###']),
                   x, 2, '#5aff7a')
        x += 4 if ch != '2' else 5
    px(a, 7, 3, '#5aff7a')
    px(a, 7, 5, '#5aff7a')
    rect(a, 2, h - 4, w - 3, h - 3, '#3a3a36')         # card slot
    for i in range(3):
        rect(a, 3 + i * 4, 9, 4 + i * 4, 10, ['#d83a2a', '#3a8a3a', '#2a5ab0'][i])
    return a


def _cards(w, h, face):
    a = blank(w, h, '#7e8286')
    if face != 'h':
        for y in range(1, h - 1, 3):
            rect(a, 1, y, w - 2, y + 1, '#ece8dc')
            px(a, 1, y, '#c8281c' if y % 2 else '#2a5ab0')
    return a


def time_recorder():
    A_TIME.add('face', 16, 14, _tr_face)
    m = Model('time_recorder', A_TIME, display=DISP_WALL)
    B = M.BEIGE_M
    m.box([1.5, 4, 11.5], [9.5, 11, 16], B, faces={'north': 'face'})
    m.box([3, 11, 13], [8, 11.5, 14], M.BLACK_M)                                       # card slot top
    m.box([10.5, 2, 14.5], [15, 14, 16], M.custom('card_rack', _cards))
    m.box([11, 11, 14], [12.5, 14.5, 14.5], solid('card', '#ece8dc'))                    # cards sticking out
    m.box([13, 11.5, 14], [14.5, 14.8, 14.5], solid('card', '#ece8dc'))
    return m


time_recorder()

# ================================================================================================ staff table
A_STAFFT = Atlas('staff_table')


def staff_table():
    m = Model('staff_table', A_STAFFT)
    TOP = M.plastic('laminate', ramp(['#6a6a62', '#8a8a80', '#a8a89c', '#c0bfb2', '#d0cfc2'], 5), base=2.8)
    m.box([0, 11, 0], [16, 12, 16], TOP)
    m.box([0, 10.5, 0.5], [16, 11, 15.5], M.GREY_DARK)
    for x0 in (1, 13.5):
        for z0 in (1, 13.5):
            m.box([x0, 0, z0], [x0 + 1.5, 10.5, z0 + 1.5], M.CHROME_M, skip=('up',))
    # an ashtray full of butts, a mug, the TV remote, a folded newspaper
    m.box([2, 12, 2.5], [5, 12.8, 5.5], M.custom('ashtray', lambda w, h, f: _ashtray(w, h, f)))
    m.box([10, 12, 3], [12, 14.2, 5], M.custom('mug', lambda w, h, f: _mug(w, h, f)))
    m.box([12, 13, 3.5], [12.6, 13.8, 4.5], solid('mug_h', '#e8e4dc'))
    m.box([5.5, 12, 9], [7, 12.6, 13], solid('remote', '#1e1e20'))
    m.box([8, 12, 8], [14, 12.3, 13], M.custom('newspaper', lambda w, h, f: _newspaper(w, h)), rot=('y', 22.5, [11, 12, 10.5]))
    return m


def _ashtray(w, h, face):
    a = blank(w, h, '#8a8e90')
    if face == 'h':
        rect(a, 1, 1, w - 2, h - 2, '#3a3a38')
        px(a, 1, 1, '#e8e4d8')
        px(a, 2, 1, '#c8a050')
        px(a, 1, 2, '#e8e4d8')
    return a


def _mug(w, h, face):
    a = blank(w, h, '#e8e4dc')
    if face == 'h':
        a[:] = C('#2a1a10')
    else:
        rect(a, 0, 1, w - 1, 1, '#e8721f')
    return a


def _newspaper(w, h):
    a = blank(w, h, '#d8d4c8')
    for y in range(1, h, 1):
        for x in range(0, w, 2):
            if (x + y) % 3:
                px(a, x, y, '#8a8880')
    rect(a, 0, 0, w - 1, 0, '#3a3a3a')
    return a


staff_table()

# ================================================================================================ folding chair
A_FOLD = Atlas('folding_chair')


def folding_chair():
    m = Model('folding_chair', A_FOLD)
    T = M.brushed('tube', STEEL, base=4.0)
    SEAT = M.vinyl('fold_vinyl', ramp(['#1a1c1e', '#2a2c30', '#3a3e42', '#4a4e54', '#5a6066'], 5))
    m.box([3.5, 7, 3], [12.5, 8, 12], SEAT)
    m.box([3.5, 10, 12.5], [12.5, 14.5, 13.5], SEAT)
    for x0 in (3, 12.25):
        m.box([x0, 0, 3], [x0 + 0.75, 8, 3.75], T, rot=('x', 22.5, [x0, 4, 3.4]))
        m.box([x0, 0, 11.5], [x0 + 0.75, 15, 12.25], T)
        m.box([x0, 0, 7], [x0 + 0.75, 8, 7.75], T, rot=('x', -22.5, [x0, 4, 7.4]))
    m.box([3.75, 1, 11.5], [12.25, 1.6, 12.25], T)
    return m


folding_chair()

# ================================================================================================ bulletin board
A_BULL = Atlas('bulletin_board', cutout=True)


def _bulletin_face(w, h):
    r = rng(9401)
    cork = ramp(['#5a3c22', '#6e4a2a', '#825a34', '#946a3e'], 4)
    a = from_idx(1.8 + noise(w, h, 9402, beta=0.3, wrap=False) * 0.8, cork)
    frame(a, 0, 0, w - 1, h - 1, '#5a3a20')
    frame(a, 1, 1, w - 2, h - 2, '#3a2412')
    # shift schedule (grid)
    sh = blank(13, 10, '#f4f2ea')
    for y in range(2, 10, 2):
        hline(sh, 0, 12, y, '#9a9a9a')
    for x in range(3, 13, 2):
        vline(sh, x, 1, 9, '#c0c0c0')
    rect(sh, 0, 0, 12, 1, '#2a5ab0')
    for (x, y) in ((4, 3), (6, 5), (8, 3), (10, 7), (4, 7)):
        px(sh, x, y, '#c8281c')
    over(a, sh, 3, 3)
    # newspaper clipping, 2009 (the crash): grey columns, a dark photo
    cl = blank(9, 12, '#d6d2c4')
    rect(cl, 0, 0, 8, 1, '#2a2a2a')
    rect(cl, 1, 3, 4, 6, '#4a4a48')
    for y in range(3, 12):
        for x in (6, 7):
            if (x + y) % 2:
                px(cl, x, y, '#7a7a72')
    for y in range(8, 12):
        hline(cl, 1, 4, y, '#8a8a82')
    tint(cl, '#b89a5a', 0.15)
    over(a, cl, 17, 2)
    px(a, 21, 2, '#c8281c')
    # memos 「連絡事項」 and a photo of the staff (faces scratched out)
    mm = blank(8, 7, '#f8e878')
    for y in (2, 4, 6):
        hline(mm, 1, 6, y, '#6a5a2a')
    over(a, mm, 4, 15)
    ph = blank(8, 6, '#e8e8e0')
    rect(ph, 1, 1, 6, 4, '#6a7a8a')
    for x in (2, 4):
        px(ph, x, 2, '#e8c0a0')
        px(ph, x, 3, '#3a3a5a')
    px(ph, 4, 2, '#1a1a1a')
    over(a, ph, 15, 16)
    return a


def bulletin_board():
    A_BULL.add('face', 28, 24, _bulletin_face)
    m = Model('bulletin_board', A_BULL, render_type='cutout', display=DISP_WALL)
    m.box([1, 2, 15], [15, 14, 16], M.DARKWOOD, faces={'north': 'face'})
    return m


bulletin_board()

# ================================================================================================ cardboard boxes
A_BOX = Atlas('cardboard_boxes')


def _box_side(label):
    def fn(w, h, face):
        a = m_cardboard(w, h, 9501 + w + h * 3, base=3.0)
        if face != 'h' and w >= 6 and h >= 4:
            rect(a, 1, 1, min(w - 2, 6), 2, label)
            hline(a, 1, w - 2, h - 2, '#5a4428')
        if face == 'h':
            rect(a, 0, h // 2, w - 1, h // 2, '#b8a070')
        return bevel(a, 0.04, -0.05)
    return fn


def cardboard_boxes():
    m = Model('cardboard_boxes', A_BOX)
    m.box([1, 0, 2], [9, 8, 12], M.custom('box_red', _box_side('#c8281c')))
    m.box([9.5, 0, 4], [15, 6, 13], M.custom('box_blue', _box_side('#2a5ab0')))
    m.box([2, 8, 3], [8, 13, 10], M.custom('box_green', _box_side('#3a8a3a')), rot=('y', 22.5, [5, 8, 6.5]))
    return m


cardboard_boxes()

# ================================================================================================ cleaning cart
A_CART = Atlas('cleaning_cart')


def cleaning_cart():
    m = Model('cleaning_cart', A_CART)
    Y = M.plastic('cart_yellow', ramp(['#5a4208', '#8a6a10', '#b88e1a', '#d8aa28', '#e8c040', '#f0d060'], 6), base=3.4)
    G = M.plastic('cart_grey', GREYPAINT, base=3.0)
    m.box([1, 0, 2], [15, 1, 14], G)
    m.box([1, 8, 2], [9, 9, 14], G)
    for x0 in (1, 8):
        for z0 in (2, 13):
            m.box([x0, 1, z0], [x0 + 1, 13, z0 + 1], G)
    m.box([1, 12.5, 2], [9, 13.5, 14], G)                                                 # top shelf
    m.box([9.5, 1, 3], [15, 12, 13], M.custom('cart_bag', lambda w, h, f: _bag(w, h, f)))  # yellow bag
    m.box([2, 1, 3], [8, 6, 11], Y)                                                       # mop bucket
    m.box([2.5, 6, 3.5], [7.5, 8, 7], G)                                                  # wringer
    m.box([4.5, 6, 8], [5.5, 22, 9], solid('mop_handle', '#3a5a9a'))                      # mop handle
    for i, x in enumerate((2, 4.5, 6.5)):
        m.box([x, 13.5, 4 + i * 2.5], [x + 1.5, 17, 5.5 + i * 2.5], solid(f'spray{i}', ['#2a8ad8', '#e8e4dc', '#3aa84a'][i]))
    return m


def _bag(w, h, face):
    a = m_plastic(w, h, ramp(['#6a4a08', '#9a7212', '#c89a20', '#e0b830'], 4), 9601, base=2.4)
    if face != 'h':
        for x in range(1, w, 3):
            vline(a, x, 1, h - 2, '#8a6a10')
    return a


cleaning_cart()

# ================================================================================================ filing cabinet
A_FILE = Atlas('filing_cabinet')


def _drawers(w, h, face):
    a = m_paint(w, h, GREYPAINT, 9701, base=4.0, scuffs=0.04)
    k = 3
    dh = h // k
    for i in range(k):
        y0 = i * dh
        hline(a, 0, w - 1, y0, GREYPAINT[1])
        rect(a, w // 2 - 2, y0 + 2, w // 2 + 1, y0 + 2, '#b8bcbe')
        rect(a, w // 2 - 1, y0 + 4, w // 2, y0 + 4, '#e8e6dc')
    return a


def filing_cabinet():
    m = Model('filing_cabinet', A_FILE)
    m.box([2, 0, 1], [14, 16, 15], M.paint('file_grey', GREYPAINT, base=3.6), faces={'north': M.custom('drawers', _drawers)})
    m.box([3, 16, 3], [9, 17, 11], solid('files_stack', '#d8d4c4'))
    return m


filing_cabinet()

# ================================================================================================ safe
A_SAFE = Atlas('safe')


def _safe_front(w, h, face):
    a = m_paint(w, h, ramp(['#0e1610', '#16221a', '#1e2e24', '#283a2e', '#324638'], 5), 9801, base=2.4, scuffs=0.04)
    frame(a, 1, 1, w - 2, h - 2, '#b8963a')
    dm = disc_mask(w, h, w / 2 - 1, h / 2 - 0.5, 1.8)
    a[dm] = C('#c9cdd0')
    px(a, int(w / 2 - 1), int(h / 2 - 1), '#3a3a3a')
    rect(a, w - 4, h // 2 - 1, w - 3, h // 2 + 1, '#b8bcbe')
    px(a, w // 2 - 1, h - 3, '#1a1a1a')
    return a


def safe():
    m = Model('safe', A_SAFE)
    BODY = M.paint('safe_body', ramp(['#0e1610', '#16221a', '#1e2e24', '#283a2e', '#324638'], 5), base=2.4)
    m.box([3, 0.5, 3], [13, 10.5, 13], BODY, faces={'north': M.custom('safe_front', _safe_front)})
    for x0 in (3.5, 11.5):
        for z0 in (3.5, 11.5):
            m.box([x0, 0, z0], [x0 + 1, 0.5, z0 + 1], M.BLACK_M)
    m.box([7, 4.5, 2.4], [8, 6.5, 3], M.CHROME_M)                                     # dial knob
    return m


safe()

# ================================================================================================ CRT TV
A_TV = Atlas('crt_tv')


def _tv_front(w, h, face):
    a = m_plastic(w, h, ramp(['#1a1a1c', '#26262a', '#323236', '#3e3e44'], 4), 9901, base=2.0)
    for y in range(2, h - 2, 2):
        hline(a, w - 3, w - 2, y, '#141416')
    px(a, w - 3, h - 2, '#c83a2a')
    return a


def _screen_off(w, h):
    a = blank(w, h, '#1c2428')
    x, y = grid(w, h)
    d = np.hypot(x - w * 0.35, y - h * 0.3)
    dl(a, 0.05, d < 2.5)
    frame(a, 0, 0, w - 1, h - 1, '#121618')
    return a


def crt_tv(on):
    name = 'crt_tv_on' if on else 'crt_tv'
    A_TV.add('screen_off', 9, 7, _screen_off)
    m = Model(name, A_TV, display=DISP_FRONT, ao=not on)
    CASE = M.plastic('tv_case', ramp(['#1a1a1c', '#26262a', '#323236', '#3e3e44'], 4), base=2.2)
    m.box([2, 5, 4], [14, 14, 13], CASE, faces={'north': M.custom('tv_front', _tv_front)})
    m.box([3.5, 6, 5], [12.5, 13, 15], CASE)                                               # tube back
    if on:
        m.box([2.6, 6, 3.9], [11.6, 13, 4], None, faces={'north': {'r': (A_STATIC, 'static'), 'sub': (0, 0, 16, 12)}},
              glow=('north',))
    else:
        m.box([2.6, 6, 3.9], [11.6, 13, 4], None, faces={'north': 'screen_off'})
    # rabbit-ear antenna
    m.box([7.5, 14, 8], [8.5, 14.5, 9], M.BLACK_M)
    m.box([7.8, 14.5, 8.3], [8.2, 20, 8.7], M.CHROME_M, rot=('z', 22.5, [8, 14.5, 8.5]))
    m.box([7.8, 14.5, 8.3], [8.2, 19, 8.7], M.CHROME_M, rot=('z', -22.5, [8, 14.5, 8.5]))
    # stand with a VCR
    ST = M.wood('tv_stand', WOOD_DARK, base=2.6)
    m.box([1, 0, 3], [15, 1, 14], ST)
    m.box([1, 4, 3], [15, 5, 14], ST)
    m.box([1, 1, 13], [15, 4, 14], ST)
    for x0 in (1, 14):
        m.box([x0, 1, 3], [x0 + 1, 4, 13], ST)
    m.box([3, 1, 4.5], [13, 2.6, 12], M.custom('vcr', lambda w, h, f: _vcr(w, h, f, on)))
    return m


def _vcr(w, h, face, on):
    a = blank(w, h, '#2a2a2e')
    if face != 'h':
        rect(a, 1, 0, w // 2, 0, '#141416')
        px(a, w - 2, 0, '#3aff6a' if on else '#1a3a20')
    return a


crt_tv(False)
crt_tv(True)

# ================================================================================================ office desk / chair / pc
A_DESK = Atlas('office_desk')


def _desk_front(w, h, face):
    a = m_paint(w, h, GREYPAINT, 10001, base=3.8, scuffs=0.05)
    for y in (3, 7):
        hline(a, 0, w - 1, y, GREYPAINT[1])
        rect(a, w // 2 - 1, y + 2, w // 2, y + 2, '#c9cdd0')
    return a


def office_desk():
    m = Model('office_desk', A_DESK)
    TOP = M.plastic('desk_top', ramp(['#5a5a54', '#76766e', '#929288', '#aaaaa0', '#bebeb2'], 5), base=2.6)
    BODY = M.paint('desk_body', GREYPAINT, base=3.6, scuffs=0.05)
    m.box([0, 11, 0], [16, 12, 16], TOP)
    m.box([9, 0, 1], [16, 11, 15], BODY, faces={'north': M.custom('desk_drawers', _desk_front)})
    m.box([0, 0, 1], [1, 11, 15], BODY)
    m.box([1, 4, 14], [9, 11, 15], BODY)                                                       # modesty panel
    # papers, a mug, a pen stand, a stack of files
    PAPER = M.custom('paper_sheet', lambda w, h, f: _paper(w, h, f))
    m.box([1, 12, 1], [7, 12.1, 9], PAPER, rot=('y', -22.5, [4, 12, 5]))
    m.box([2, 12.1, 3], [8, 12.2, 11], PAPER, rot=('y', 22.5, [5, 12.1, 7]))
    m.box([12, 12, 2], [13.8, 14.5, 3.8], M.custom('mug2', lambda w, h, f: _mug(w, h, f)))
    m.box([9, 12, 11], [15, 14.5, 15], M.custom('files', lambda w, h, f: _files(w, h, f)))
    m.box([13, 12, 6], [14.5, 14, 7.5], solid('penstand', '#2a2a2a'))
    m.box([13.3, 14, 6.3], [13.6, 15.5, 6.6], solid('pen_red', '#c8281c'))
    return m


def _paper(w, h, face):
    a = blank(w, h, '#efede4')
    if face == 'h':
        for y in range(1, h, 2):
            hline(a, 1, w - 2, y, '#a8a69e')
    return a


def _files(w, h, face):
    a = blank(w, h, '#d8d4c4')
    if face != 'h':
        for y in range(0, h, 2):
            hline(a, 0, w - 1, y, ['#2a5ab0', '#d8d4c4', '#c8281c', '#d8d4c4'][(y // 2) % 4])
    return a


office_desk()

A_OCHAIR = Atlas('office_chair')


def office_chair():
    m = Model('office_chair', A_OCHAIR)
    FAB = M.fabric('chair_fabric', ramp(['#0e0e10', '#18181c', '#222228', '#2c2c34', '#36363e'], 5))
    BK = M.BLACK_M
    for ang in (0, 45, -45):
        m.box([2, 1, 7.5], [14, 2, 8.5], BK, rot=('y', ang, [8, 1.5, 8]) if ang else None)
    m.box([7.5, 1, 2], [8.5, 2, 14], BK)
    for (x, z) in ((2, 7.5), (13, 7.5), (7.5, 2), (7.5, 13)):
        m.box([x, 0, z], [x + 1, 1, z + 1], BK)
    m.box([7.25, 2, 7.25], [8.75, 7, 8.75], M.CHROME_M)
    m.box([3, 7, 3], [13, 9, 13], FAB)
    m.box([3.5, 10, 12], [12.5, 20, 13.5], FAB)
    m.box([7, 8, 12.5], [9, 10, 13.5], BK)
    for x0 in (2, 13):
        m.box([x0, 9, 6], [x0 + 1, 12, 7], BK)
        m.box([x0, 12, 5], [x0 + 1, 12.75, 10], BK)
    return m


office_chair()

A_PC = Atlas('pc')


def _pc_screen(w, h):
    a = blank(w, h, '#0c1e3a')
    rect(a, 0, 0, w - 1, 1, '#1e3c7a')
    for y in range(3, h - 1, 2):
        for x in range(1, w - 1, 3):
            hline(a, x, x + 1, y, '#3a5aa0')
    rect(a, 1, 3, 2, 4, '#e8e8e8')
    px(a, w - 3, h - 2, '#e8e8e8')
    return a


def pc():
    A_PC.add('screen', 14, 11, _pc_screen)
    m = Model('pc', A_PC, display=disp(gui=(30, 225, 0), gui_scale=0.9, gui_t=(0, 3, 0)))
    y0 = -4
    CASE = M.plastic('pc_beige', ramp(['#7a7466', '#958e7e', '#aea795', '#c2bba8', '#d0c9b6'], 5), base=3.0, yellow=0.08)
    m.box([4, y0 + 1.5, 5], [12, y0 + 8.5, 13], CASE)                                       # CRT monitor
    m.box([5, y0 + 2.5, 13], [11, y0 + 7.5, 15], CASE)
    m.box([6, y0, 7], [10, y0 + 1.5, 11], CASE)                                             # stand
    m.box([4.6, y0 + 2.2, 4.9], [11.4, y0 + 7.8, 5], None, faces={'north': 'screen'}, glow=('north',))
    m.box([3, y0, 1], [13, y0 + 0.7, 4.5], M.custom('keyboard', lambda w, h, f: _kb(w, h, f)))
    m.box([13.5, y0, 2], [14.7, y0 + 0.6, 3.8], CASE)                                       # mouse
    return m


def _kb(w, h, face):
    a = blank(w, h, '#b8b1a0')
    if face == 'h':
        for y in range(0, h, 1):
            for x in range(0, w, 1):
                if (x + y) % 2 == 0:
                    a[y, x] = C('#8a8476')
    return a


pc()

# ================================================================================================ security monitors
A_MON = Atlas('security_monitors')


def _mon_off(w, h):
    a = blank(w, h, '#1a2220')
    x, y = grid(w, h)
    dl(a, 0.05, (x + y) < 4)
    frame(a, 0, 0, w - 1, h - 1, '#101412')
    return a


def security_monitors(on, alert):
    name = 'security_monitors' + ('_on' if on else '') + ('_alert' if alert else '')
    A_MON.add('off', 14, 12, _mon_off)
    m = Model(name, A_MON, display=DISP_FRONT, ao=not on)
    RACK = M.paint('mon_rack', GREYPAINT, base=2.2, scuffs=0.03)
    CASE = M.plastic('mon_case', ramp(['#2a2a2c', '#38383c', '#46464a', '#54545a'], 4), base=1.6)
    m.box([0, 0, 8], [16, 16, 16], RACK, skip=('north',))
    m.box([0, 0, 7.5], [16, 0.5, 16], RACK)
    m.box([0, 8, 6], [16, 8.5, 16], RACK)
    k = 0
    for row, y0 in enumerate((8.5, 0.5)):
        for col, x0 in enumerate((0.5, 8.25)):
            m.box([x0, y0, 5], [x0 + 7.25, y0 + 7, 15], CASE)
            face = f'alert{k}' if (on and alert) else (f'view{k}' if on else 'off')
            spec = {'r': (A_FEED, face)} if on else 'off'
            m.box([x0 + 0.6, y0 + 0.6, 4.9], [x0 + 6.65, y0 + 6.2, 5], None, faces={'north': spec},
                  glow=('north',) if on else ())
            k += 1
    if on:
        m.box([13, 0.6, 4.9], [14, 1.2, 5], lit('mon_led', '#ff3a22' if alert else '#3aff6a'), only=('north',),
              glow=('north',))
    return m


security_monitors(False, False)
security_monitors(True, False)
security_monitors(True, True)
child('security_monitors_alert', 'security_monitors')

# ================================================================================================ security camera
A_CAM = Atlas('security_camera')


def security_camera(on, seen):
    name = 'security_camera' + ('_on' if on else '') + ('_seen' if seen else '')
    m = Model(name, A_CAM, display=disp(gui=(-30, 225, 0), gui_scale=1.0, gui_t=(0, -4, 0)), ao=False)
    W_ = M.plastic('cam_white', WHITEPLASTIC, base=4.4)
    DOME = M.glass('dome', '#1a1e22', 215)
    m.box([4.5, 15, 4.5], [11.5, 16, 11.5], W_)
    m.box([5, 13.5, 5], [11, 15, 11], DOME)
    m.box([5.75, 12.5, 5.75], [10.25, 13.5, 10.25], DOME)
    # the lens looks north and down: visible as a darker disc with a glint on the north side
    m.box([7, 12.75, 5.4], [9, 14.25, 5.6], M.custom('lens', lambda w, h, f: _lens(w, h)), only=('north',))
    if seen:
        LED = lit('cam_red', '#ff2a1a')
    elif on:
        LED = lit('cam_green', '#3aff6a')
    else:
        LED = solid('cam_dark', '#2a2a2a')
    m.box([9.5, 14, 4.9], [10.3, 14.6, 5.1], LED, only=('north', 'down'), glow=('north', 'down') if (on or seen) else ())
    return m


def _lens(w, h):
    a = blank(w, h, '#05070a')
    px(a, 0, 0, '#6a7a8a')
    return a


security_camera(False, False)
security_camera(True, False)
security_camera(True, True)
security_camera(False, True)

# ================================================================================================ breaker panel
A_BRK = Atlas('breaker_panel')
CIRCUITS = ['客席', '厨房', 'トイレ', '事務所', '看板', 'カメラ', 'ドア']


def _breakers(w, h):
    a = blank(w, h, '#c8cbcc')
    frame(a, 0, 0, w - 1, h - 1, '#7a7e80')
    rect(a, 2, 2, w - 3, 5, '#2a2a2c')                         # main breaker
    rect(a, w // 2 - 2, 3, w // 2 + 1, 4, '#e8e8e2')
    for i in range(7):
        y = 8 + i * 3
        if y + 2 >= h:
            break
        rect(a, 3, y, 8, y + 1, '#1a1a1c')
        rect(a, 3, y, 5, y + 1, '#f2f2ea')                     # lever up = on
        rect(a, 10, y, w - 3, y + 1, '#f2f2ea')                 # label strip
        hline(a, 11, w - 4, y, '#3a3a3a')
    return a


def _panel_door(w, h, face):
    a = m_paint(w, h, ramp(['#6a6e70', '#868a8c', '#9ea2a4', '#b2b6b8', '#c4c8ca'], 5), 10101, base=2.8, scuffs=0.05)
    frame(a, 0, 0, w - 1, h - 1, '#5a5e60')
    if face != 'h' and w >= 8:
        # warning sticker (lightning), latch
        rect(a, w // 2 - 2, 2, w // 2 + 1, 5, '#f0c828')
        line(a, w // 2, 2, w // 2 - 1, 4, '#1a1a1a')
        px(a, w // 2, 4, '#1a1a1a')
        rect(a, w - 3, h // 2 - 1, w - 2, h // 2 + 1, '#3a3a3a')
    return a


def breaker_panel(open_):
    name = 'breaker_panel_open' if open_ else 'breaker_panel'
    A_BRK.add('inside', 24, 26, _breakers)
    m = Model(name, A_BRK, display=DISP_WALL)
    DOOR = M.custom('panel_door', _panel_door)
    BOX = M.paint('panel_box', ramp(['#6a6e70', '#868a8c', '#9ea2a4', '#b2b6b8', '#c4c8ca'], 5), base=2.4)
    if not open_:
        m.box([2, 2, 13], [14, 15, 16], BOX, faces={'north': DOOR})
    else:
        m.box([2, 2, 13], [14, 15, 16], BOX, faces={'north': 'inside'})
        m.box([1, 2, 1], [2, 15, 13], BOX, faces={'west': DOOR, 'east': DOOR})
    m.box([7, 15, 14], [9, 16, 16], M.GREY_DARK)                                       # conduit
    return m


breaker_panel(False)
breaker_panel(True)
