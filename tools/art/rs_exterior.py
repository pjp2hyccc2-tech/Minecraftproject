"""Exterior, road and door-hardware props of HIDAMARI: vending machines, the pole sign and roof letters (lit / unlit),
the A-frame menu board, lot and back-yard props, street lamps, utility poles, road furniture, the 献花, the auto-door
rail / swing-door frame / key switch, note paper (models face north)."""
import math

import numpy as np
from scipy import ndimage

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, sun_logo, sun_small,
                    bitmap, upscale_nn, downsample, m_brushed, m_plastic, m_paint, m_glass, m_concrete, m_wood,
                    m_fabric, STEEL, ALU, CHROME, WHITEPLASTIC, BEIGE, GREYPAINT, CONCRETE, ORANGE, BROWN, CREAM,
                    BLACKPLASTIC, WOOD_DARK, GREEN_LEAF, SUNY, FONT_IPAG)
from common import FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK, FONT_SANS_JP_MED, FONT_SANS_BOLD
import rs_mats as M
from rs_waiting import solid, lit

GALV = ramp(['#3e4244', '#55595b', '#6c7072', '#83878a', '#9a9ea0', '#b1b5b7'], 6)
GALV_M = M.brushed('galv', GALV, base=3.0)


# ================================================================================================ vending machine
A_VEND = Atlas('vending_machine')
DRINK_COLS = [('#c8281c', '#f2f2ea'), ('#1e4aa8', '#f2f2ea'), ('#f0c020', '#3a2a10'), ('#3aa84a', '#f2f2ea'),
              ('#4a2a1a', '#e8c070'), ('#e8721f', '#f2f2ea'), ('#f2f2ea', '#2a5ab0'), ('#1a1a1a', '#e8c070'),
              ('#8a4ad8', '#f2f2ea'), ('#d8d0b8', '#c8281c')]


def _vend_display(w, h):
    """2x: rows of (fictional) drink samples with price tags and lit buttons."""
    r = rng(12001)
    a = blank(w, h, '#e9eef2')
    rect(a, 0, 0, w - 1, 2, '#1e4aa8')
    for k in range(3):
        y0 = 4 + k * 9
        for i in range(5):
            x0 = 1 + i * 6
            c, band = DRINK_COLS[int(r.integers(0, len(DRINK_COLS)))]
            can = r.random() < 0.6
            if can:
                rect(a, x0 + 1, y0, x0 + 3, y0 + 4, c)
                hline(a, x0 + 1, x0 + 3, y0 + 2, band)
                hline(a, x0 + 1, x0 + 3, y0, '#c9cdd0')
            else:
                rect(a, x0 + 1, y0 + 1, x0 + 3, y0 + 4, c)
                rect(a, x0 + 2, y0 - 1, x0 + 2, y0, '#e8e8e8')
                hline(a, x0 + 1, x0 + 3, y0 + 3, band)
            px(a, x0 + 1, y0 + 1, '#ffffff')
            hline(a, x0 + 1, x0 + 3, y0 + 5, '#ffffff')                 # price tag
            px(a, x0 + 1, y0 + 5, '#3a3a3a')
            px(a, x0 + 3, y0 + 5, '#3a3a3a')
            b = '#2a7aff' if (i + k) % 3 else '#ff3a2a'                   # COLD blue / HOT red buttons
            px(a, x0 + 2, y0 + 6, b)
        hline(a, 0, w - 1, y0 + 7, '#b8c0c8')
    return a


def _vend_panel(w, h):
    """2x lower front: an advert panel (left), the coin / bill slots (right), the take-out port (bottom)."""
    a = blank(w, h, '#f2f4f6')
    rect(a, 1, 1, 19, 15, '#1e4aa8')
    for y in range(2, 15):
        hline(a, 2, 18, y, '#2456b8' if y % 4 else '#1e4aa8')
    rect(a, 7, 4, 12, 13, '#e8e8ec')                  # a giant bottle in the advert
    rect(a, 8, 2, 11, 3, '#c9cdd0')
    rect(a, 7, 7, 12, 9, '#c8281c')
    hline(a, 3, 17, 14, '#f0c020')
    # money column
    rect(a, 21, 2, 28, 15, '#c9cdd0')
    rect(a, 22, 3, 27, 4, '#1a1a1a')                  # display
    stamp_mask(a, bitmap(['#.#', '.#.', '###', '.#.', '.#.']), 22, 6, '#3a3a3a')
    rect(a, 26, 6, 27, 8, '#2a2a2a')                  # coin slot
    rect(a, 22, 10, 27, 10, '#2a2a2a')                # bill slot
    rect(a, 23, 12, 26, 14, '#5a5e60')                # change return
    # take-out port
    rect(a, 3, 18, 26, 26, '#2a2c2e')
    rect(a, 4, 19, 25, 24, '#141516')
    hline(a, 4, 25, 19, '#3a3c3e')
    rect(a, 0, h - 2, w - 1, h - 1, '#5a5e60')
    return a


def _vend_side(w, h, face):
    a = m_plastic(w, h, ramp(['#8a8e92', '#a6aaae', '#c0c4c8', '#d4d8dc', '#e4e8ea'], 5), 12002 + w + h, base=3.4)
    if face != 'h':
        rect(a, 0, 0, w - 1, 1, '#1e4aa8')
        grime(a, 12003, strength=0.12, color='#3a3a30', bottom=0.35)
    return a


def vending_machine(part):
    name = f'vending_machine_{part}'
    if 'display' not in A_VEND.regions:
        A_VEND.add('display', 30, 30, _vend_display)
        A_VEND.add('panel', 30, 30, _vend_panel)
        A_VEND.add('header', 30, 4, lambda w, h: _vend_header(w, h))
    m = Model(name, A_VEND, display=DISP_TALL, ao=part == 'lower')
    SIDE = M.custom('vend_side', _vend_side)
    if part == 'lower':
        m.box([0.5, 0, 3], [15.5, 16, 16], SIDE, faces={'north': 'panel'})
        m.box([0.5, 0, 2.5], [15.5, 0.5, 3], solid('vend_plinth', '#2a2c2e'))
    else:
        m.box([0.5, 0, 3], [15.5, 14, 16], SIDE, faces={'north': 'display'}, glow=('north',))
        m.box([0.5, 14, 2], [15.5, 16, 16], SIDE, faces={'north': 'header'}, glow=('north',))
        m.box([0.5, 13.5, 2], [15.5, 14, 3], solid('vend_lip', '#c9cdd0'))
    return m


def _vend_header(w, h):
    a = blank(w, h, '#1e4aa8')
    stamp_center(a, text35('COLD'), 8, 2, '#e8f0ff')
    stamp_center(a, text35('HOT'), 23, 2, '#ff8a7a')
    return a


vending_machine('lower')
vending_machine('upper')

# ================================================================================================ pole sign
FAMILY = 'FAMILY'
RESTAURANT = 'RESTAURANT'


def _pole_face(lit_):
    """64x64 box-sign face: the smiling sun, 「ひだまり」, FAMILY RESTAURANT."""
    W = H = 64
    a = blank(W, H, '#f6eedc' if lit_ else '#b8ad96')
    # top and bottom brand bands
    rect(a, 0, 0, W - 1, 3, '#e8721f' if lit_ else '#8a4a1c')
    rect(a, 0, 50, W - 1, H - 1, '#5a2e14' if lit_ else '#3a2010')
    logo = sun_logo(28, ss=8, colors=None if lit_ else dict(ray='#a85a20', ray2='#b8702c', disc='#c09040',
                                                              disc_hi='#d0a858', line='#3a1c0c', cheek='#b06048',
                                                              outline='#5a2a10'))
    logo = logo.copy()
    over(a, logo, (W - 28) // 2, 4)
    g = text_mask('ひだまり', 15, FONT_SANS_JP_BLACK, binary=True, ss=4, threshold=0.45)
    col = '#5a2a10' if lit_ else '#3a1c0c'
    stamp_center(a, g, W / 2, 40.5, col)
    for ch_line, y in ((FAMILY, 52), (RESTAURANT, 58)):
        stamp_center(a, text35(ch_line), W / 2, y + 2, '#fff2dc' if lit_ else '#a89a80')
    if lit_:
        # uneven backlight: brighter centre, a failing tube on the left third, dead insects at the bottom edge
        x, y = grid(W, H)
        d = np.hypot((x - 32) / 40, (y - 26) / 40)
        dl(a, -0.03, d > 0.6)
        dl(a, -0.05, x < 18)
        bugs = (y > 45) & (y < 50) & (rng(12101).random((H, W)) < 0.05)
        a[bugs] = C('#5a4a36')
    else:
        grime(a, 12102, strength=0.18, color='#3a3020', bottom=0.2)
        bugs = (grid(W, H)[1] > 45) & (grid(W, H)[1] < 50) & (rng(12101).random((H, W)) < 0.05)
        a[bugs] = C('#2a2016')
    frame(a, 0, 0, W - 1, H - 1, '#3a2010')
    return a


def _roof_face(lit_):
    """128x16 channel letters: the sun + 「ひだまり」 (alpha = letter shapes)."""
    W, H = 128, 16
    a = blank(W, H)
    face = '#ffb84a' if lit_ else '#b8601e'
    ret = '#c86418' if lit_ else '#6a3210'
    logo = sun_small(16) if lit_ else sun_small(16, legend={'r': '#a8501a', 'y': '#b87828', 'h': '#c8903a',
                                                            'l': '#3a1c0c', 'c': '#a85a3a'})
    x = 12
    over(a, logo, x, 0)
    x += 16 + 7
    for ch in 'ひだまり':
        g = text_mask(ch, 16, FONT_SANS_JP_BLACK, binary=True, ss=4, threshold=0.45)
        gh, gw = g.shape
        oy = (H - gh) // 2
        ox = x + (16 - gw) // 2
        inner = ndimage.binary_erosion(g > 0.5)
        for yy in range(gh):
            for xx in range(gw):
                if g[yy, xx] > 0.5:
                    a[oy + yy, ox + xx] = C(face if inner[yy, xx] else ret)
        x += 16 + 7
    return a


A_POLEFACE = Atlas('pole_sign_face', fixed=(64, 64), base=lambda w, h: _pole_face(False))
A_POLEFACE_LIT = Atlas('pole_sign_face_lit', fixed=(64, 64), base=lambda w, h: _pole_face(True))
A_ROOF = Atlas('roof_sign_face', fixed=(128, 16), base=lambda w, h: _roof_face(False), cutout=True)
A_ROOF_LIT = Atlas('roof_sign_face_lit', fixed=(128, 16), base=lambda w, h: _roof_face(True), cutout=True)
for _t in range(16):
    for _A in (A_POLEFACE, A_POLEFACE_LIT):
        _A.add_at(f't{_t}', (_t % 4) * 16, (_t // 4) * 16, 16, 16)
for _t in range(8):
    for _A in (A_ROOF, A_ROOF_LIT):
        _A.add_at(f't{_t}', _t * 16, 0, 16, 16)

A_POLE = Atlas('pole_sign')
FRAME_BROWN = M.paint('sign_frame', ramp(['#140a06', '#22120a', '#301a0e', '#3e2414', '#4c2e1a'], 5), base=2.4,
                      scuffs=0.04, rust=0.02)
POLE_PAINT = M.paint('sign_pole', ramp(['#1e1410', '#2c1e16', '#3a281e', '#4a3426', '#5a4030'], 5), base=2.6,
                     scuffs=0.03, rust=0.0)


def pole_sign_tile(t, lit_):
    name = f'pole_sign_tile_{t}' + ('_lit' if lit_ else '')
    c, r = t % 4, t // 4
    FA = A_POLEFACE_LIT if lit_ else A_POLEFACE
    m = Model(name, A_POLE, display=disp(gui=(0, 180, 0), gui_scale=0.6), ao=not lit_)
    z0, z1 = 3, 13
    faces = {'north': {'r': (FA, f't{t}')}, 'south': {'r': (FA, f't{(3 - c) + r * 4}')}}
    glow = ('north', 'south') if lit_ else ()
    m.box([0, 0, z0], [16, 16, z1], None, faces=faces, glow=glow, shade=not lit_)
    # the box frame where this tile is on the edge of the 4x4 face (col 0 = viewer's left = +x side)
    if c == 0:
        m.box([15, 0, z0 - 0.5], [16.5, 16, z1 + 0.5], FRAME_BROWN, skip=('west',))
    if c == 3:
        m.box([-0.5, 0, z0 - 0.5], [1, 16, z1 + 0.5], FRAME_BROWN, skip=('east',))
    if r == 0:
        m.box([-0.5 if c == 3 else 0, 15, z0 - 0.5], [16.5 if c == 0 else 16, 16.5, z1 + 0.5], FRAME_BROWN, skip=('down',))
    if r == 3:
        m.box([-0.5 if c == 3 else 0, -0.5, z0 - 0.5], [16.5 if c == 0 else 16, 1, z1 + 0.5], FRAME_BROWN, skip=('up',))
    return m


for _t in range(16):
    pole_sign_tile(_t, False)
    pole_sign_tile(_t, True)


def pole_sign_pole():
    m = Model('pole_sign_pole', A_POLE, display=DISP_TALL)
    m.box([4, 0, 4], [12, 16, 12], POLE_PAINT)
    m.box([3.5, 6, 3.5], [12.5, 7, 12.5], POLE_PAINT)                                 # a joint ring
    return m


pole_sign_pole()
child('pole_sign_pole_lit', 'pole_sign_pole')


def roof_sign_tile(t, lit_):
    name = f'roof_sign_tile_{t}' + ('_lit' if lit_ else '')
    FA = A_ROOF_LIT if lit_ else A_ROOF
    m = Model(name, A_POLE, render_type='cutout', display=disp(gui=(0, 180, 0), gui_scale=0.8), ao=not lit_)
    g = ('north', 'south') if lit_ else ()
    for z in (6.5, 7.5, 8.5):
        m.plane([0, 0, z], [16, 16, z], {'north': {'r': (FA, f't{t}')}, 'south': {'r': (FA, f't{t}'), 'flip': 'h'}},
                glow=g, shade=not lit_)
    m.box([0, 0, 9.5], [16, 1.5, 11.5], GALV_M)                                         # mounting rail
    for x0 in (3, 12):
        m.box([x0, 1.5, 9.5], [x0 + 1, 13, 10.5], GALV_M)
    return m


for _t in range(8):
    roof_sign_tile(_t, False)
    roof_sign_tile(_t, True)

# ================================================================================================ A-frame menu board
A_BOARD = Atlas('menu_board')


def chalk(mask, seed, color='#ecebe2', grain=0.28):
    """Chalk strokes: the mask broken up by paper-like grain."""
    r = rng(seed)
    h, w = mask.shape
    n = r.random((h, w))
    m = (mask > 0.35) & (n > grain)
    out = blank(w, h)
    out[m] = C(color)
    soft = (mask > 0.1) & (mask <= 0.35) & (n > 0.6)
    out[soft] = C(color, 150)
    return out


def _board_face(msg):
    """4x density blackboard (48 x 56) with chalk writing."""
    W, H = 48, 56
    r = rng(12301 + msg)
    a = from_idx(1.4 + noise(W, H, 12302, beta=1.0, wrap=False) * 0.5,
                 ramp(['#0e1612', '#141e18', '#1a261e', '#223026'], 4))
    # old chalk smudges (wiped)
    sm = noise(W, H, 12303 + msg, beta=1.8, wrap=False) > 0.6
    tint(a, '#4a5a50', 0.25, sm)
    # painted header: the sun and ひだまり (permanent, orange)
    over(a, sun_small(9), 3, 2)
    hm = text_mask('ひだまり', 9, FONT_SANS_JP_BOLD, binary=True)
    stamp_mask(a, hm, 14, 2, '#e8721f')
    hline(a, 2, W - 3, 12, '#e8721f')
    if msg == 0:
        l1 = text_mask('本日の', 12, FONT_SANS_JP_MED, binary=False, ss=4)
        over(a, chalk(l1, 12304), 4, 15)
        l2 = text_mask('おすすめ', 11, FONT_SANS_JP_MED, binary=False, ss=4)
        over(a, chalk(l2, 12305, '#f0a0b0'), 3, 28)
        # doodle: hamburg steak on a hot plate + price
        dm = np.zeros((10, 20), np.float32)
        yy, xx = np.mgrid[0:10, 0:20]
        dm[(((xx - 10) / 9.0) ** 2 + ((yy - 5) / 4.2) ** 2) < 1.0] = 0.3
        dm[(((xx - 10) / 6.0) ** 2 + ((yy - 4.5) / 2.6) ** 2) < 1.0] = 0.9
        over(a, chalk(dm, 12306, '#e8c070'), 4, 41)
        pm = text_mask('¥780', 8, FONT_SANS_BOLD, binary=False, ss=4)
        over(a, chalk(pm, 12307), 26, 45)
    else:
        # 「まだ　いますか」: big, uneven, as if written by a shaking hand; everything else wiped
        l1 = text_mask('まだ', 16, FONT_SANS_JP_MED, binary=False, ss=4)
        sh = ndimage.shift(l1, (0, 0), order=0)
        over(a, chalk(sh, 12308, '#f2f1ea', grain=0.18), 6, 15)
        l2 = text_mask('いますか', 11, FONT_SANS_JP_MED, binary=False, ss=4)
        # tilt the second line down to the right
        tilt = np.zeros((l2.shape[0] + 4, l2.shape[1]), np.float32)
        for xx in range(l2.shape[1]):
            d = int(xx / l2.shape[1] * 4)
            tilt[d:d + l2.shape[0], xx] = l2[:, xx]
        over(a, chalk(tilt, 12309, '#f2f1ea', grain=0.18), 3, 36)
        # a small chalk hand print in the corner
        hp = np.zeros((7, 6), np.float32)
        hp[3:7, 1:5] = 0.8
        for k in range(4):
            hp[0:3, 1 + k] = 0.7 if k % 2 == 0 else 0.0
        over(a, chalk(hp, 12310, '#c8c8c0'), 38, 47)
    return a


def menu_board(msg):
    name = f'menu_board_{msg}'
    if 'face0' not in A_BOARD.regions:
        A_BOARD.add('face0', 48, 56, lambda w, h: _board_face(0))
        A_BOARD.add('face1', 48, 56, lambda w, h: _board_face(1))
        A_BOARD.add('back', 12, 14, lambda w, h: from_idx(1.4 + noise(w, h, 12311, beta=1.0, wrap=False) * 0.5,
                                                         ramp(['#0e1612', '#141e18', '#1a261e', '#223026'], 4)))
    m = Model(name, A_BOARD, display=DISP_FRONT)
    FR = M.wood('board_frame', WOOD_DARK, base=3.6)
    ang = -22.5
    # front board leaning back, rear board leaning forward (A-frame)
    m.box([2, 1, 6], [14, 15, 6.75], FR, faces={'north': f'face{msg}', 'south': 'back'}, rot=('x', ang, [8, 1, 6.4]))
    m.box([2, 1, 9.25], [14, 15, 10], FR, faces={'south': 'back', 'north': 'back'}, rot=('x', -ang, [8, 1, 9.6]))
    for x0 in (1.25, 14):
        m.box([x0, 0, 5.8], [x0 + 0.75, 15.2, 6.9], FR, rot=('x', ang, [8, 1, 6.4]))
        m.box([x0, 0, 9.1], [x0 + 0.75, 15.2, 10.2], FR, rot=('x', -ang, [8, 1, 9.6]))
    m.box([2, 13.6, 7.4], [14, 14.4, 8.6], FR)                                         # top hinge
    m.box([2.5, 3.5, 7.2], [13.5, 4, 8.8], M.CHROME_M)                                 # spreader chain bar
    return m


menu_board(0)
menu_board(1)

# ================================================================================================ bike rack (with the old staff bicycle)
A_BIKE = Atlas('bike_rack', cutout=True)


def _wheel(w, h):
    a = blank(w, h)
    rm = ring_mask(w, h, w / 2, h / 2, w / 2 - 1.6, w / 2)
    a[rm] = C('#1a1a1a')
    rim = ring_mask(w, h, w / 2, h / 2, w / 2 - 2.2, w / 2 - 1.6)
    a[rim] = C('#9a9ea0')
    x, y = grid(w, h)
    cx, cy = w / 2 - 0.5, h / 2 - 0.5
    for k in range(8):
        ang = k / 8 * math.pi
        sp = np.abs((x - cx) * math.sin(ang) - (y - cy) * math.cos(ang)) < 0.45
        a[sp & (np.hypot(x - cx, y - cy) < w / 2 - 1.6)] = C('#7a7e80')
    a[disc_mask(w, h, w / 2, h / 2, 1.2)] = C('#5a5e60')
    return a


def _basket(w, h, face):
    a = blank(w, h)
    for yy in range(h):
        for xx in range(w):
            if xx % 2 == 0 or yy % 2 == 0 or yy == h - 1:
                a[yy, xx] = C('#6a6e70')
    return a


def bike_rack():
    A_BIKE.add('wheel', 12, 12, _wheel)
    m = Model('bike_rack', A_BIKE, render_type='cutout', display=disp(gui=(30, 225, 0), gui_scale=0.4))
    m.box([0, 0, 6], [16, 1, 10], GALV_M)                                              # rack rail
    for x0 in (3, 11):
        m.box([x0, 1, 6.5], [x0 + 0.5, 6, 7], GALV_M)                                  # wheel slot hoops
        m.box([x0 + 2, 1, 6.5], [x0 + 2.5, 6, 7], GALV_M)
        m.box([x0, 5.5, 6.5], [x0 + 2.5, 6, 7], GALV_M)
    # an old mamachari parked in the left slot, front wheel in the hoop, leaning slightly
    bx = 4.25
    FR = M.paint('bike_frame', ramp(['#2a0e0c', '#4a1612', '#6a1e18', '#8a2a20', '#a83a2c'], 5), base=2.6, rust=0.05)
    m.plane([bx, 0, -2], [bx, 12, 10], {'east': 'wheel', 'west': 'wheel'})                 # front wheel (in the rack)
    m.plane([bx, 0, 14], [bx, 12, 26], {'east': 'wheel', 'west': 'wheel'})                 # rear wheel
    m.box([bx - 0.4, 6, 4], [bx + 0.4, 6.8, 20], FR)                                       # down tube
    m.box([bx - 0.4, 6, 19], [bx + 0.4, 13, 19.8], FR, rot=('x', -22.5, [bx, 6, 19.4]))  # seat tube
    m.box([bx - 0.4, 11, 6], [bx + 0.4, 11.8, 17], FR)                                     # top tube
    m.box([bx - 0.4, 6, 3.6], [bx + 0.4, 14, 4.4], FR, rot=('x', 22.5, [bx, 6, 4]))       # fork / head tube
    m.box([bx - 1.2, 14.6, 18.4], [bx + 1.2, 15.6, 22], solid('saddle', '#1a1614'))      # saddle
    m.box([bx - 4, 15, 4.5], [bx + 4, 15.6, 5.2], M.CHROME_M)                             # handlebar
    m.box([bx - 2.5, 11, 0], [bx + 2.5, 15, 4], M.custom('basket', _basket))               # front basket
    m.box([bx - 1.6, 9.5, 18], [bx + 1.6, 10.2, 25], M.CHROME_M)                           # rear carrier
    return m


bike_rack()

# ================================================================================================ trash station (recycling bins)
A_RECY = Atlas('trash_station')


def _bin_label(text, bg):
    def fn(w, h):
        a = blank(w, h, '#f2f2ea')
        rect(a, 0, 0, w - 1, 2, bg)
        g = text_mask(text, 8, FONT_SANS_JP_BOLD, binary=True)
        stamp_center(a, g, w / 2, (h + 3) / 2, '#1a1a1a')
        return a
    return fn


def trash_station():
    A_RECY.add('lab_can', 12, 12, _bin_label('かん', '#2a5ab0'))
    A_RECY.add('lab_pet', 12, 12, _bin_label('ペット', '#e8b020'))
    m = Model('trash_station', A_RECY)
    BLUE = M.plastic('bin_blue', ramp(['#0a1a3a', '#12285a', '#1c3a7a', '#2a4e9a', '#3a62b0'], 5), base=2.8)
    YEL = M.plastic('bin_yel', ramp(['#5a4208', '#8a6810', '#b88e1a', '#d8aa28', '#e8c040'], 5), base=2.8)
    for x0, mat, lab in ((1, BLUE, 'lab_can'), (8.5, YEL, 'lab_pet')):
        m.box([x0, 0, 4], [x0 + 6.5, 11, 12], mat)
        m.box([x0 + 0.5, 4, 3.9], [x0 + 6, 9.5, 4], None, faces={'north': lab})
        m.box([x0 + 2, 11, 6], [x0 + 4.5, 11.2, 8.5], solid('hole', '#0a0a0a'), only=('up',))
    m.box([2, 0, 13], [7, 3, 15], solid('bag_blue', '#3a4a8a'))                            # a bag of cans left beside
    return m


trash_station()

# ================================================================================================ A/C outdoor unit
A_AC = Atlas('ac_unit')


def _ac_front(w, h, face):
    a = m_plastic(w, h, ramp(['#7e7a6e', '#9a9688', '#b4b0a2', '#c8c4b6', '#d8d4c6'], 5), 12401, base=3.4)
    if face != 'h' and w >= 12:
        cx, cy = w * 0.38, h / 2
        g = disc_mask(w, h, cx, cy, h * 0.42)
        a[g] = C('#2a2c2e')
        x, y = grid(w, h)
        grill = g & ((x.astype(int) % 2 == 0) | (y.astype(int) % 2 == 0))
        a[grill] = C('#4a4c4e')
        a[disc_mask(w, h, cx, cy, 1.2)] = C('#7a7c7e')
        for yy in range(1, h - 1, 2):
            hline(a, int(w * 0.75), w - 2, yy, '#8a8678')
        grime(a, 12402, strength=0.2, color='#4a3a20', bottom=0.4)
    return a


def ac_unit():
    m = Model('ac_unit', A_AC)
    B = M.custom('ac_body', _ac_front)
    m.box([1, 1.5, 4], [15, 12, 12], M.plastic('ac_side', ramp(['#7e7a6e', '#9a9688', '#b4b0a2', '#c8c4b6'], 4), base=2.6),
          faces={'north': B})
    for x0 in (2, 12):
        m.box([x0, 0, 3.5], [x0 + 2, 1.5, 12.5], GALV_M)
    m.box([12, 6, 12], [13, 7, 16], solid('ac_pipe', '#d8d0b8'))                           # insulated pipe to the wall
    return m


ac_unit()

# ================================================================================================ gas cylinders
A_GAS = Atlas('gas_cylinder')


def _cyl_side(w, h, face):
    a = m_paint(w, h, ramp(['#5a5e62', '#74787c', '#8e9296', '#a6aaae', '#bcc0c4'], 5), 12501 + w, base=3.0, scuffs=0.06)
    if face != 'h' and h > 10:
        rect(a, 0, h // 3, w - 1, h // 3 + 1, '#e8c020')
        grime(a, 12502, strength=0.2, color='#4a3020', bottom=0.4)
    return a


def gas_cylinder():
    m = Model('gas_cylinder', A_GAS, display=DISP_TALL)
    S = M.custom('cyl', _cyl_side)
    for cx in (4.5, 11.5):
        for ang in (0, 45):
            r_ = ('y', ang, [cx, 0, 8]) if ang else None
            m.box([cx - 2.5, 0, 5.5], [cx + 2.5, 20, 10.5], S, rot=r_)
            m.box([cx - 2, 20, 6], [cx + 2, 22, 10], S, rot=r_)
        m.box([cx - 0.6, 22, 7.4], [cx + 0.6, 23.5, 8.6], M.CHROME_M)                      # valve
        m.box([cx - 2, 22, 6], [cx + 2, 24, 6.5], S)                                       # cap guard ring
        m.box([cx - 2, 22, 9.5], [cx + 2, 24, 10], S)
    m.box([1, 14, 10.6], [15, 14.6, 11.2], M.custom('chain', lambda w, h, f: _chain(w, h)))
    return m


def _chain(w, h):
    a = blank(w, h, '#5a5e60')
    for x in range(0, w, 2):
        vline(a, x, 0, h - 1, '#8a8e90')
    return a


gas_cylinder()

# ================================================================================================ dumpster
A_DUMP = Atlas('dumpster')


def _dump_side(w, h, face):
    a = m_paint(w, h, ramp(['#0c1a12', '#14281c', '#1c3826', '#264830', '#30583a'], 5), 12601 + w, base=2.8, amp=0.2,
                scuffs=0.04)
    if face not in ('h', 'up', 'down'):
        for x in range(2, w - 1, 4):                       # pressed ribs
            vline(a, x, 1, h - 2, '#30583a')
            vline(a, x + 1, 1, h - 2, '#0e1c14')
        grime(a, 12602, strength=0.12, color='#2a2010', bottom=0.35)
        if face == 'north' and w >= 12 and h >= 8:
            # a weathered collection sticker
            rect(a, w // 2 - 3, 3, w // 2 + 3, 7, '#d8dcd0')
            hline(a, w // 2 - 2, w // 2 + 2, 4, '#3a5a8a')
            hline(a, w // 2 - 2, w // 2 + 1, 6, '#6a6e66')
    return a


def dumpster():
    m = Model('dumpster', A_DUMP)
    S = M.custom('dump', _dump_side, per_face=True)
    m.box([0.5, 1.5, 1.5], [15.5, 13.5, 14.5], S)
    m.box([0.2, 13.5, 1.2], [15.8, 14.5, 14.8], M.paint('dump_lid', ramp(['#0c1a12', '#14281c', '#1c3826', '#264830'], 4),
                                                        base=2.0, scuffs=0.06), rot=('x', -22.5, [8, 13.5, 14.8]))
    for x0, z0 in ((1, 2), (13, 2), (1, 12), (13, 12)):
        m.box([x0, 0, z0], [x0 + 2, 1.5, z0 + 2], M.BLACK_M)
    m.box([-0.5, 9, 6], [0.5, 10, 10], GALV_M)
    m.box([15.5, 9, 6], [16.5, 10, 10], GALV_M)
    return m


dumpster()

# ================================================================================================ wheel stop
A_WSTOP = Atlas('wheel_stop')


def _wstop(w, h, face):
    a = m_concrete(w, h, 12701 + w, base=3.8)
    if w >= 10:
        for x0 in range(1, w, 5):
            rect(a, x0, 0, min(w - 1, x0 + 1), h - 1, '#d8a820')
    grime(a, 12702, strength=0.25, color='#202018', bottom=0.3)
    return bevel(a, 0.06, -0.08)


def wheel_stop():
    m = Model('wheel_stop', A_WSTOP, display=DISP_SMALL)
    m.box([1, 0, 6], [15, 2.5, 10], M.custom('wstop', _wstop))
    for x0 in (3.5, 12):
        m.box([x0, 2.5, 7.5], [x0 + 0.8, 2.7, 8.3], M.CHROME_M)
    return m


wheel_stop()

# ================================================================================================ outdoor planter
A_OPLANT = Atlas('outdoor_planter', cutout=True)


def _azalea(w, h):
    from rs_dining import leaves_tex
    a = leaves_tex(w, h, 12801, dusty=0.0)
    r = rng(12802)
    for _ in range(9):
        x, y = int(r.integers(1, w - 1)), int(r.integers(1, h - 1))
        if a[y, x, 3] > 0:
            a[y, x] = C('#c84a7a')
            if x + 1 < w:
                a[y, x + 1] = C('#e86a9a')
    return a


def outdoor_planter():
    A_OPLANT.add('shrub', 16, 16, _azalea)
    m = Model('outdoor_planter', A_OPLANT, render_type='cutout')
    BOX = M.custom('planter_brick', lambda w, h, f: _pbrick(w, h, f))
    m.box([1, 0, 1], [15, 7, 15], BOX)
    m.box([2, 6.5, 2], [14, 6.6, 14], solid('soil', '#2a1e14'), only=('up',))
    for ang, z in ((45, 8), (-45, 8)):
        m.box([0, 6.5, z], [16, 15, z], None, faces={'north': 'shrub', 'south': 'shrub'}, rot=('y', ang, [8, 6.5, 8]))
    m.box([2, 7, 2], [14, 12, 14], None, faces={'up': 'shrub', 'north': 'shrub', 'south': 'shrub', 'east': 'shrub',
                                                 'west': 'shrub'})
    m.box([9, 6.6, 3], [10.5, 6.9, 3.3], solid('butt', '#e8e2d0'))                        # a cigarette butt
    return m


def _pbrick(w, h, face):
    from rs_lib import BRICK
    a = m_concrete(w, h, 12803 + w, base=3.4)
    if face != 'h':
        for y in range(0, h, 3):
            hline(a, 0, w - 1, y, CONCRETE[2])
        tint(a, '#7a4a30', 0.35)
    return bevel(a, 0.05, -0.06)


outdoor_planter()

# ================================================================================================ street lamp
A_LAMP = Atlas('street_lamp')


def street_lamp(part, lit_):
    name = f'street_lamp_{part}' + ('_lit' if lit_ else '')
    m = Model(name, A_LAMP, display=DISP_TALL, ao=not lit_)
    if part == 'pole':
        m.box([7, 0, 7], [9, 16, 9], GALV_M, skip=('up', 'down'))
        return m
    m.box([7, 0, 7], [9, 10, 9], GALV_M, skip=('down',))
    m.box([7.25, 9, -6], [8.75, 10.5, 9], GALV_M)                                       # arm to the north
    m.box([7.25, 6, 7.5], [8.75, 9.5, 8.5], GALV_M, rot=('x', -45, [8, 9.5, 8]))        # brace
    HOUSING = M.paint('lamp_housing', GALV, base=2.4, scuffs=0.03)
    m.box([5, 8, -15], [11, 10.5, -5], HOUSING)
    m.box([5.5, 10.5, -14], [10.5, 11, -6], HOUSING)
    LENS = lit('sodium', '#ffae4a') if lit_ else solid('sodium_off', '#6a5a3a')
    m.box([5.6, 7.5, -14.4], [10.4, 8, -5.6], LENS, glow='all' if lit_ else (), shade=not lit_)
    return m


for _p in ('pole', 'head'):
    for _l in (False, True):
        street_lamp(_p, _l)

# ================================================================================================ utility pole
A_UPOLE = Atlas('utility_pole')


def _pole_conc(w, h, face):
    a = m_concrete(w, h, 12901 + w, base=3.8)
    if face != 'h':
        st = noise(w, h, 12902, beta=0.8, sx=1, sy=10, wrap=False) > 0.9
        tint(a, '#2a2a22', 0.25, st)
    return a


def _pole_ad_unused(w, h):
    """電柱広告: a vertical yellow advert plate (a clinic) with red/black lettering."""
    a = blank(w, h, '#f0c828')
    rect(a, 0, 0, w - 1, 1, '#1e4aa8')
    g = text_mask('内科', 7, FONT_SANS_JP_BOLD, binary=True, vertical=True)
    stamp_center(a, g, w / 2, 9, '#1a1a1a')
    rect(a, 1, h - 5, w - 2, h - 2, '#c8281c')
    frame(a, 0, 0, w - 1, h - 1, '#3a3a2a')
    return a


def utility_pole(part):
    name = f'utility_pole_{part}'
    m = Model(name, A_UPOLE, display=DISP_TALL)
    P = M.custom('pole_conc', _pole_conc)
    for ang in (0, 45):
        m.box([5.5, 0, 5.5], [10.5, 16, 10.5], P, rot=('y', ang, [8, 0, 8]) if ang else None, skip=('up', 'down'))
    if part == 'pole':
        for y, side in ((4, 1), (10, -1)):
            x0 = 10.5 if side > 0 else 3.5
            m.box([x0, y, 7.5], [x0 + 2, y + 0.6, 8.5], M.CHROME_M)                       # step bolts
        return m
    # top: crossarm, insulators, transformer, wire stubs
    m.box([-12, 9, 7], [28, 10.5, 9], GALV_M)
    for x in (-10, -3, 19, 26):
        m.box([x - 0.75, 10.5, 7.25], [x + 0.75, 12.5, 8.75], M.custom('insulator', lambda w, h, f: _insul(w, h)))
        m.box([x - 0.25, 12.2, -16], [x + 0.25, 12.6, 32], solid('wire', '#141414'))
    TR = M.paint('transformer', ramp(['#5a5e62', '#74787c', '#8e9296', '#a6aaae'], 4), base=2.4, rust=0.03)
    for ang in (0, 45):
        m.box([11.5, 1, 9], [15.5, 7.5, 13], TR, rot=('y', ang, [13.5, 1, 11]) if ang else None)
    m.box([11, 7.5, 8.5], [16, 8, 13.5], TR)
    m.box([13, 8, 10.5], [14, 9, 11.5], M.custom('insulator', lambda w, h, f: _insul(w, h)))
    m.box([10.5, 3, 9.5], [11.5, 6, 12.5], GALV_M)                                         # bracket
    return m


def _insul(w, h):
    a = blank(w, h, '#e8e4dc')
    for y in range(0, h, 2):
        hline(a, 0, w - 1, y, '#b8b4aa')
    return a


utility_pole('pole')
utility_pole('top')

# ================================================================================================ guardrail
A_GUARD = Atlas('guardrail')


def _wbeam(w, h, face):
    a = m_paint(w, h, ramp(['#8a8c88', '#a8aaa6', '#c4c6c2', '#dcded8', '#ecede8'], 5), 13001 + w, base=3.4, scuffs=0.08)
    if face != 'h' and h >= 4:
        hline(a, 0, w - 1, 0, '#f4f5f0')
        hline(a, 0, w - 1, h // 2, '#8a8c88')
        hline(a, 0, w - 1, h - 1, '#6a6c68')
        grime(a, 13002, strength=0.2, color='#4a4030', bottom=0.3)
        tint(a, '#7a5a3a', 0.15, noise(w, h, 13003, beta=1.5, wrap=False) > 1.0)
    return a


def guardrail():
    m = Model('guardrail', A_GUARD)
    W_ = M.custom('wbeam', _wbeam)
    m.box([0, 6, 6], [16, 12, 7.5], W_)
    POST = M.paint('post_white', ramp(['#8a8c88', '#a8aaa6', '#c4c6c2', '#dcded8'], 4), base=2.6, scuffs=0.06)
    m.box([7, 0, 8.5], [9, 12.5, 10.5], POST)
    m.box([7.25, 8, 7.5], [8.75, 10, 8.5], GALV_M)
    m.box([7.5, 12.5, 9], [8.5, 13.5, 10], lit('reflector', '#ff8a1a'))                   # delineator
    return m


guardrail()

# ================================================================================================ chevron sign
A_CHEV = Atlas('chevron_sign')


def _chevrons(w, h):
    a = blank(w, h, '#f2c818')
    x, y = grid(w, h)
    for k in range(3):
        cx = 4 + k * 8
        m = (np.abs((x - cx) - np.abs(y - h / 2 + 0.5) * 0.8) < 1.4)
        a[m] = C('#141414')
    frame(a, 0, 0, w - 1, h - 1, '#141414')
    return a


def chevron_sign():
    A_CHEV.add('face', 24, 12, _chevrons)
    m = Model('chevron_sign', A_CHEV, display=DISP_FRONT)
    m.box([2, 7, 7.5], [14, 13, 8.5], M.GREY_M, faces={'north': 'face'})
    m.box([7.5, 0, 8.5], [8.5, 13, 9.5], GALV_M)
    return m


chevron_sign()

# ================================================================================================ curve mirror
A_CMIR = Atlas('curve_mirror')


def _convex(w, h):
    a = blank(w, h, '#3a4248')
    x, y = grid(w, h)
    cx, cy = w / 2, h / 2
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy) / (w / 2)
    # a fish-eye reflection: dark sky, the road curving, a sodium light glow
    sky = y < cy - 1
    a[sky] = C('#1e2630')
    road = (y >= cy) & (np.abs(x - cx - (y - cy) * 0.6) < (y - cy) * 0.9 + 1)
    a[road] = C('#2a2c30')
    a[road & (np.abs(x - cx - (y - cy) * 0.6) < 0.6)] = C('#c8a020')
    a[disc_mask(w, h, cx + 3, cy - 3, 1.4)] = C('#ffb050')
    dl(a, 0.1, d < 0.35)
    dl(a, -0.1, d > 0.8)
    a[d > 1.0, 3] = 0
    return a


def curve_mirror():
    A_CMIR.add('mirror', 12, 12, _convex)
    m = Model('curve_mirror', A_CMIR, render_type='cutout', display=disp(gui=(15, 200, 0), gui_scale=0.4, gui_t=(0, -4, 0)))
    OR = M.paint('mirror_orange', ramp(['#5a1e06', '#8a320c', '#b84a14', '#e0641c', '#f07a2a'], 5), base=3.0, rust=0.03)
    m.box([7, 0, 8.5], [9, 22, 10.5], OR)                                                 # post
    m.box([2, 18, 7], [14, 30, 8], OR)                                                    # frame back
    m.box([3, 19, 6.9], [13, 29, 7], None, faces={'north': 'mirror'})                     # convex mirror face
    m.box([2, 29.5, 5], [14, 30.5, 8], OR)                                                # hood
    m.box([7, 24, 8], [9, 25, 9], OR)
    return m


curve_mirror()

# ================================================================================================ traffic light
A_TL = Atlas('traffic_light')


def traffic_light(lit_):
    name = 'traffic_light_lit' if lit_ else 'traffic_light'
    m = Model(name, A_TL, display=DISP_FRONT, ao=not lit_)
    H = M.paint('signal_body', ramp(['#3a3c3e', '#4e5052', '#626466', '#76787a'], 4), base=2.0, scuffs=0.03)
    m.box([0, 5, 6], [16, 11, 10], H)
    lamps = [(2.5, '#1e6a5a', '#1e6a5a', False), (7, '#6a5410', '#ffc020', True), (11.5, '#5a1410', '#5a1410', False)]
    for x0, off, on, is_y in lamps:
        c = on if (lit_ and is_y) else off
        mat = lit(f'lamp_{c}', c) if (lit_ and is_y) else solid(f'lens_{c}', c)
        m.box([x0, 6, 5.8], [x0 + 3, 9.5, 6], mat, only=('north',), glow=('north',) if (lit_ and is_y) else ())
        m.box([x0 - 0.25, 9.5, 4], [x0 + 3.25, 10, 6], H)                                # visor
    m.box([7, 11, 7], [9, 16, 9], GALV_M)
    return m


traffic_light(False)
traffic_light(True)

# ================================================================================================ flower offering
A_FLOW = Atlas('flower_offering', cutout=True)


def _bouquet(w, h):
    r = rng(13101)
    a = blank(w, h)
    # cellophane cone (translucent-ish white) with chrysanthemums
    for y in range(h):
        half = int(1 + (h - 1 - y) * 0.45)
        for x in range(w // 2 - half, w // 2 + half + 1):
            if 0 <= x < w:
                a[y, x] = C('#e8eef0')
                if (x + y) % 3 == 0:
                    a[y, x] = C('#c8d4d8')
    for _ in range(14):
        x, y = int(r.integers(1, w - 1)), int(r.integers(0, h // 2))
        c = ['#f4f2e8', '#f0d040', '#f4f2e8', '#d8c8e8'][int(r.integers(0, 4))]
        a[y, x] = C(c)
        if y + 1 < h:
            a[y + 1, x] = C('#3a6a2a')
    return a


def flower_offering():
    A_FLOW.add('bouquet', 10, 16, _bouquet)
    m = Model('flower_offering', A_FLOW, render_type='cutout', display=DISP_SMALL)
    # bouquet leaning against the rail (two crossed planes)
    for ang in (22.5, -22.5):
        m.box([3, 0, 9], [10, 11, 9], None, faces={'north': 'bouquet', 'south': 'bouquet'},
              rot=('y', ang, [6.5, 0, 9]))
    m.box([3.5, 0, 8], [9.5, 1, 11], solid('ribbon_wrap', '#d8d0e8'))
    # a can of coffee
    m.box([10.5, 0, 5], [12, 2.6, 6.5], M.custom('can_coffee', lambda w, h, f: _can(w, h, f)))
    # a small toy motorbike
    m.box([1.5, 0.8, 3], [5.5, 2, 4], solid('toy_bike_body', '#c8281c'))
    m.box([1.2, 0, 3.2], [2.2, 1.4, 3.8], solid('toy_wheel', '#141414'))
    m.box([4.8, 0, 3.2], [5.8, 1.4, 3.8], solid('toy_wheel', '#141414'))
    m.box([4.5, 2, 3.1], [5, 2.8, 3.9], solid('toy_bar', '#2a2a2a'))
    # a candle stub
    m.box([12.5, 0, 9], [13.5, 1.5, 10], solid('candle', '#f2ede0'))
    return m


def _can(w, h, face):
    a = blank(w, h, '#5a3418')
    if face == 'h':
        a[:] = C('#c9cdd0')
    else:
        rect(a, 0, 0, w - 1, 0, '#c9cdd0')
        rect(a, 0, h // 2, w - 1, h // 2, '#e8c070')
    return a


flower_offering()

# ================================================================================================ note paper
A_NOTE = Atlas('note_paper', cutout=True)


def _note(w, h):
    r = rng(13201)
    a = blank(w, h, '#efeadc')
    for y in range(3, h - 1, 2):
        hline(a, 1, w - 2, y, '#c8d0dc')
    for y in range(3, h - 2, 2):
        x = 2
        while x < w - 3:
            if r.random() < 0.65:
                px(a, x, y - 1, '#2a2a3a')
            x += 1 + int(r.integers(0, 2))
    vline(a, w // 2, 0, h - 1, '#d8d2c2')            # fold
    a[0, w - 1, 3] = 0
    a[h - 1, 0, 3] = 0
    return a


def note_paper():
    A_NOTE.add('sheet', 10, 14, _note)
    m = Model('note_paper', A_NOTE, render_type='cutout', ao=False, display=DISP_FLOOR)
    m.box([3, 0.02, 1], [13, 0.27, 15], solid('paper_edge', '#e2ddcf'), faces={'up': 'sheet'},
          rot=('y', -22.5, [8, 0, 8]))
    return m


note_paper()

# ================================================================================================ door hardware
A_DOORSW = Atlas('door_switch')


def _switch_face(on):
    def fn(w, h):
        a = blank(w, h, '#c9cdd0')
        frame(a, 0, 0, w - 1, h - 1, '#7a7e80')
        for i, t in enumerate(('自動', '開放', '閉鎖')):
            g = text_mask(t, 5, FONT_SANS_JP_BOLD, binary=True)
            stamp_mask(a, g[:, :min(g.shape[1], 10)], 1, 2 + i * 4, '#1a1a1a')
        dm = disc_mask(w, h, w - 4, 8, 2.2)
        a[dm] = C('#9a9ea0')
        px(a, w - 4, 7, '#1a1a1a')
        px(a, w - 4, 8, '#1a1a1a')
        rect(a, w - 5, 12, w - 3, 13, '#3aff6a' if on else '#1a4a2a')
        return a
    return fn


def door_switch(on):
    name = 'door_switch_on' if on else 'door_switch'
    A_DOORSW.add('face_on' if on else 'face', 16, 16, _switch_face(on))
    m = Model(name, A_DOORSW, display=DISP_WALL)
    m.box([4, 4, 14], [12, 12, 16], M.STEEL_M, faces={'north': 'face_on' if on else 'face'},
          glow=('north',) if on else ())
    if on:
        m.box([9.6, 7.6, 13], [10.4, 8.4, 14], M.CHROME_M)                                # the shop key, inserted
        m.box([9.2, 7.2, 12.4], [10.8, 8.8, 13], solid('key_tag', '#e8721f'))
    return m


door_switch(False)
door_switch(True)

A_RAIL = Atlas('auto_door_rail')


def auto_door_rail():
    m = Model('auto_door_rail', A_RAIL, display=DISP_FRONT)
    m.box([0, 11, 3], [16, 16, 13], M.ALU_M)
    m.box([0, 10.5, 6.5], [16, 11, 9.5], solid('rail_slot', '#1a1c1e'), only=('down',))
    m.box([6, 10, 2.5], [10, 11, 3.5], solid('sensor_box', '#1e1e20'))
    m.box([7.5, 10.3, 2.4], [8.5, 10.7, 2.5], lit('sensor_led', '#ff2a1a'), only=('north',), glow=('north',))
    return m


auto_door_rail()

A_SWF = Atlas('swing_door_frame')


def swing_door_frame():
    """Lower half of the kitchen swing doors (the leaves are drawn by the world's renderer in the block centre plane):
    a stainless threshold plate and the door header at the top of the 2-high opening (y 30..32 reaches into the upper
    half, whose own model is invisible). No jambs: the same model serves the left and the right leaf."""
    m = Model('swing_door_frame', A_SWF, display=disp(gui=(30, 225, 0), gui_scale=0.4, gui_t=(0, -3, 0)))
    m.box([0, 0, 4], [16, 0.3, 12], M.STEEL_M)
    m.box([0, 30, 5], [16, 32, 11], M.STEEL_M)
    m.box([0, 29.6, 6], [16, 30, 10], M.custom('rubber_strip', lambda w, h, f: blank(w, h, '#1a1a1a')))
    return m


swing_door_frame()
