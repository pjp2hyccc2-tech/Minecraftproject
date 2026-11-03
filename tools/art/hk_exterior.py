"""Outside コーポ白樺: the balcony's laundry pole and AC unit, the small drink vending machine (lit), the old sodium
street lamp on its arm, the concrete utility pole (+ a top with crossarm and transformer), the mailbox row, a
mamachari, the rusty stair railing, the door nameplates 101-204, the 「入居者募集」 sign, the swing set in the park,
puddles with rain ripples and the rain-streaked window glass (both animated)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, fnoise, texture, FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK
from hk_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_brushed, m_plastic, m_paint, m_concrete, text35, text_mask,
                    stamp_mask, stamp_center, bitmap, FONT_IPAG)
import hk_mats as M
from hk_common import (STAINLESS, STEELW, CREAMPL, GREYPL, CHARCOAL, PAPER, RUST, solid, lit, mat, painted, woodmat,
                       disc_strips, cord, register_anim, ref)

RUSTPAINT = ramp(['#1e0e0a', '#2e1610', '#401f16', '#52291c', '#643424', '#76402c'], 6)     # oxide-red paint
CONC = ramp(['#4e4e4a', '#5c5c57', '#6a6a64', '#787871', '#86867e', '#93938b', '#a0a097'], 7)


def rusty(key, palette=RUSTPAINT, base=3.0, rust=0.12):
    def fn(w, h, face):
        a = m_paint(w, h, palette, M._seed(key, w, h, face), base=base, scuffs=0.05, rust=0.0)
        r = rng(M._seed(key, w, h, face) + 3)
        m = r.random((h, w)) < rust
        tint(a, '#8a4a1e', 0.55, m)
        tint(a, '#5a2a10', 0.4, np.roll(m, 1, 0) & (r.random((h, w)) < 0.5))
        return M._finish(a, w, h, face, True)
    return M.Mat(key, fn)


# ================================================================================================ balcony
A_LAUN = Atlas('laundry_pole')


def _towel(w, h, face):
    x, y = grid(w, h)
    a = from_idx(3.4 + noise(w, h, 13001 + w, beta=1.4, wrap=False) * 0.3,
                 ramp(['#5a7a8a', '#6e8e9e', '#84a2b0', '#98b4c0', '#acc4ce'], 5))
    if face != 'h':
        dl(a, -0.04, (x % 2) == 0)
        hline(a, 0, w - 1, h - 2, '#e8e4dc')
    return a


def laundry_pole():
    """An aluminium laundry pole across the balcony (blocks join along x), with plastic clothespins clipped on and,
    off-centre, one forgotten grey sock - little enough that a row of these does not read as a pattern."""
    m = Model('laundry_pole', A_LAUN, display=DISP_FRONT)
    POLE = M.brushed('laun_pole', STEELW, base=4.6)
    m.box([0, 13, 7.6], [16, 13.8, 8.4], POLE)
    for (xx, c) in ((3.2, '#c8a0b0'), (12.6, '#9ac0d0')):
        m.box([xx - 0.3, 12.2, 7.5], [xx + 0.3, 14.2, 8.5], solid('laun_clip' + c[1:], c))
    m.box([12.3, 10.0, 7.65], [12.9, 12.2, 8.35], solid('sock_grey', '#5a5a5e'))
    m.box([12.3, 9.4, 7.65], [13.6, 10.0, 8.35], solid('sock_grey', '#5a5a5e'))
    return m


laundry_pole()

A_AC = Atlas('ac_outdoor_unit')


def _ac_front(w, h):
    """AC outdoor unit front (14 x 10): beige-grey steel, the round fan grille (as seen, on the left), louvres on the
    right, a faded spec sticker, rust bleeding from the bottom edge."""
    a = from_idx(3.8 + noise(w, h, 13101, beta=1.6, wrap=False) * 0.2,
                 ramp(['#7a786e', '#8e8c82', '#a2a096', '#b4b2a8', '#c4c2b8', '#d2d0c6'], 6))
    bevel(a, 0.05, -0.08)
    x, y = grid(w, h)
    cx, cy = 4.5, 5.0
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy)
    a[d < 4.0] = C('#1c1c1c')
    spiral = (d < 4.0) & ((np.round(d * 1.3).astype(int) % 2) == 0)
    a[spiral] = C('#5a5a58')
    a[d < 0.9] = C('#3a3a3a')
    for yy in range(2, h - 2, 2):
        hline(a, 10, w - 2, yy, '#6a685e')
    rect(a, 10, 1, 12, 1, '#e8e4d8')
    tint(a[h - 2:], '#6a3a1a', 0.35)
    return a


def ac_outdoor_unit():
    A_AC.add('front', 14, 10, _ac_front)
    m = Model('ac_outdoor_unit', A_AC, display=DISP_FRONT)
    BODY = painted('ac_body', ramp(['#7a786e', '#8e8c82', '#a2a096', '#b4b2a8', '#c4c2b8'], 5), base=3.2,
                   wear_amt=0.05)
    m.box([1, 1.2, 6], [15, 11.2, 12], BODY, faces={'north': 'front'})
    for x0 in (1.5, 13.5):
        m.box([x0, 0, 6.5], [x0 + 1, 1.2, 11.5], rusty('ac_foot'))
    # insulated pipes (taped, grubby white) leaving the side and going into the wall
    TAPE = solid('ac_tape', '#c8c4b4')
    m.box([0.0, 3, 9], [1.0, 4.2, 10.2], TAPE)
    m.box([-1.2, 3, 9], [0.0, 4.2, 16], TAPE)
    m.box([0.0, 5, 10.4], [1.0, 5.8, 11.2], solid('ac_drain', '#4a4a4a'))
    return m


ac_outdoor_unit()


# ================================================================================================ vending machine
A_VEND = Atlas('vending_machine_small')


def _vend_display(w, h):
    """The lit display (10 x 12): three shelves of drink dummies (cans and bottles in bright colours) with their
    price tags, the blue 'つめた～い' and red 'あったか～い' bands under the rows."""
    a = blank(w, h, '#e8f0f4')
    r = rng(13201)
    cols = ['#c8281c', '#2a5ac8', '#e8c83a', '#3a9a4a', '#f0f0ec', '#8a3a1a', '#1a1a1a', '#e87a2a', '#6ac8e8']
    for row, band in enumerate(('#3a7ad8', '#3a7ad8', '#d83a2a')):
        y0 = row * 4
        for k in range(5):
            c = cols[int(r.integers(0, len(cols)))]
            x0 = k * 2
            rect(a, x0, y0, x0, y0 + 2, c)
            px(a, x0, y0, '#ffffff' if c != '#f0f0ec' else '#c8c8c8')
            px(a, x0 + 1, y0 + 2, '#f8f8f0')
        hline(a, 0, w - 1, y0 + 3, band)
    return a


def _vend_front(w, h):
    """Body front (14 x 28): white, the display window (covered by the lit element), the button row, the coin panel
    on the right (as seen), the dispenser flap at the bottom, a 'recycle' box shadow, stickers."""
    a = from_idx(4.0 + noise(w, h, 13211, beta=1.6, wrap=False) * 0.2,
                 ramp(['#8a8e92', '#a2a6aa', '#b8bcc0', '#cccfd2', '#dcdfe0', '#e8eaea'], 6))
    bevel(a, 0.05, -0.08)
    # brand-less blue panel at the top
    rect(a, 0, 0, w - 1, 1, '#2a4a9a')
    # button row under the display
    for xx in range(1, 11, 2):
        px(a, xx, 15, '#e83a2a')
    # coin panel (texture right columns = model west)
    rect(a, 11, 9, 13, 18, '#8a8e92')
    px(a, 12, 11, '#1a1a1a')
    rect(a, 12, 13, 12, 14, '#1a1a1a')
    px(a, 12, 16, '#3aff6a')
    # the dispenser opening
    rect(a, 1, 22, 10, 25, '#1a1a1a')
    hline(a, 1, 10, 22, '#4a4e52')
    # dirt and a peeling sticker at the bottom
    tint(a[h - 3:], '#4a4038', 0.3)
    rect(a, 11, 21, 12, 23, '#e8d860')
    return a


def vending_machine_small():
    """A single drink machine, 1 block wide but 28 px tall (keep the block above clear); the display and the button
    lamps are emissive."""
    A_VEND.add('front', 14, 28, _vend_front)
    A_VEND.add('display', 10, 12, _vend_display)
    m = Model('vending_machine_small', A_VEND, display=disp(gui=(15, 200, 0), gui_scale=0.4, gui_t=(0, -3, 0)))
    BODY = painted('vend_body', ramp(['#2a4a9a', '#3458aa', '#3e66ba', '#4a74c8'], 4), base=2.0, amp=0.22, wear_amt=0.03)
    m.box([1, 0, 3], [15, 28, 15], BODY, faces={'north': 'front'})
    # the display window under its glass, glowing (texture cols 0..9 = model x 15..5)
    m.box([4.5, 13.5, 2.9], [14.5, 25.5, 3.0], None, faces={'north': 'display'}, glow=('north',))
    m.box([1, 27.0, 2.4], [15, 28.0, 3.0], BODY)
    return m


vending_machine_small()


# ================================================================================================ street lamp
A_LAMP = Atlas('street_lamp_old')


def street_lamp_old(lit_):
    """An old residential street light: a shallow white reflector over a sodium lamp on a pipe arm, clamped to the
    utility pole in the block to the south."""
    name = 'street_lamp_old' + ('_lit' if lit_ else '')
    m = Model(name, A_LAMP, ao=not lit_, display=disp(gui=(20, 135, 0), gui_scale=0.45, gui_t=(0, 0, 0)))
    REF = painted('sl_reflector', ramp(['#6a6c6a', '#8a8c88', '#a8aaa6', '#c4c6c0', '#d8dad4'], 5), base=3.0)
    UNDER = lit('sl_under_on', '#f0c890') if lit_ else solid('sl_under', '#8a8a84')
    BULB = lit('sl_bulb_on', '#ffd890') if lit_ else solid('sl_bulb', '#b8a888')
    ARM = rusty('sl_arm', ramp(['#3a3c3a', '#4a4c4a', '#5a5c5a', '#6a6c6a', '#7a7c7a'], 5), base=2.4, rust=0.08)
    m.box([3, 9, 0], [13, 10.6, 9], REF, faces={'down': UNDER}, glow=('down',) if lit_ else ())
    m.box([4, 10.6, 1], [12, 11.4, 8], REF)
    m.box([5.5, 7.6, 2.5], [10.5, 9, 6.5], BULB, glow=('down', 'north', 'south', 'east', 'west') if lit_ else ())
    m.box([7.3, 9.6, 8.5], [8.7, 11.0, 21.0], ARM)                                 # the arm
    m.box([7.0, 11.4, 3.5], [9.0, 12.4, 5.5], solid('sl_sensor', '#2a2a2a'))       # photocell
    # the clamp around the pole (pole centred at z 24, 6 px wide)
    for (x0, z0, x1, z1) in ((4.1, 20.1, 11.9, 20.5), (4.1, 27.5, 11.9, 27.9), (4.1, 20.5, 4.5, 27.5),
                             (11.5, 20.5, 11.9, 27.5)):
        m.box([x0, 8.5, z0], [x1, 11.5, z1], ARM)
    return m


street_lamp_old(False)
street_lamp_old(True)


# ================================================================================================ utility pole
A_POLE = Atlas('utility_pole')


def _pole_conc(w, h, face):
    a = m_concrete(w, h, 13401 + w, base=3.6, palette=CONC)
    if face != 'h':
        x, y = grid(w, h)
        st = noise(w, h, 13402 + w, beta=0.6, sx=1, sy=10, wrap=False)
        tint(a, '#30302c', 0.18, st > 0.6)
        dl(a, 0.04, x == 0)
        dl(a, -0.05, x == w - 1)
    return a


def _pole_body(m):
    C_ = mat('pole_conc', _pole_conc)
    m.box([5.5, 0, 4.5], [10.5, 16, 11.5], C_)
    m.box([4.5, 0, 5.5], [11.5, 16, 10.5], C_)
    STEP = M.brushed('pole_step', STEELW, base=3.4)
    m.box([11.5, 4, 7.6], [14.0, 4.8, 8.4], STEP)                                 # step bolts (足場ボルト)
    m.box([2.0, 12, 7.6], [4.5, 12.8, 8.4], STEP)


def utility_pole():
    m = Model('utility_pole', A_POLE, display=DISP_TALL)
    _pole_body(m)
    # the pole's number plate (tag) and a rusty band
    m.box([7.0, 7, 4.2], [9.0, 10, 4.5], mat('pole_tag', _pole_tag))
    return m


def _pole_tag(w, h, face):
    a = blank(w, h, '#e8e4d8')
    if face != 'h':
        hline(a, 0, w - 1, 0, '#2a5ac8')
        px(a, 0, h - 1, '#1a1a1a')
        px(a, 1, h - 1, '#1a1a1a')
    return a


def utility_pole_top():
    """The pole's top: a steel crossarm with white insulators where the world's power wires (x 5.5..10.5 at rest,
    y 10.6..12.7) meet it, a grey pole transformer hanging on the south side."""
    m = Model('utility_pole_top', A_POLE, display=DISP_TALL)
    _pole_body(m)
    ARM = M.brushed('pole_arm', ramp(['#3a3c3e', '#4a4c4e', '#5a5c5e', '#6a6c6e'], 4), base=2.4)
    INS = M.plastic('pole_ins', ramp(['#9a9890', '#b8b6ae', '#d4d2ca', '#ecebe4'], 4), base=2.8)
    m.box([-2, 11.0, 7.0], [18, 12.0, 9.0], ARM)
    m.box([-2, 9.2, 7.3], [18, 9.9, 8.7], ARM)
    for xc in (5.65, 10.35):
        m.box([xc - 0.7, 12.0, 7.3], [xc + 0.7, 13.6, 8.7], INS)
    for xc in (6.95, 9.15):
        m.box([xc - 0.55, 9.9, 7.4], [xc + 0.55, 10.6, 8.6], INS)
    for xc in (-1.2, 17.2):
        m.box([xc - 0.6, 12.0, 7.4], [xc + 0.6, 13.4, 8.6], INS)
    # the transformer drum hanging on the south side
    TR = painted('pole_trans', ramp(['#6a6e70', '#7c8082', '#8e9294', '#a0a4a6', '#b0b4b6'], 5), base=2.8)
    m.box([5.0, 1.5, 11.8], [11.0, 8.5, 16.0], TR)
    m.box([4.6, 8.5, 11.5], [11.4, 9.2, 16.4], TR)
    for xx in (6.0, 9.4):
        m.box([xx, 9.2, 13.0], [xx + 0.6, 10.4, 13.6], INS)
    return m


utility_pole()
utility_pole_top()


# ================================================================================================ mailboxes
A_MAIL = Atlas('mailboxes')


def _mailbox_front(kind):
    def fn(w, h):
        """One box door (6 x 5): grey-green steel, the mail flap at the top, a name card slot, a dial lock."""
        a = from_idx(3.0 + noise(w, h, 13501 + kind, beta=1.6, wrap=False) * 0.2,
                     ramp(['#4e5850', '#5e6860', '#6e7870', '#7e8880', '#8e9890'], 5))
        bevel(a, 0.06, -0.08)
        hline(a, 1, w - 2, 1, '#1e2420')                      # mail slot
        rect(a, 1, 3, 3, 3, '#e8e4d8' if kind != 2 else '#c8c4b8')
        px(a, w - 2, 3, '#1a1a1a')
        px(a, w - 2, 2, '#c8ccce')
        if kind == 2:
            # vacant: taped shut
            line(a, 0, 0, w - 1, h - 1, '#d8c070')
            line(a, 0, h - 1, w - 1, 0, '#d8c070')
        tint(a[h - 1:], '#6a3a1a', 0.3)
        return a
    return fn


def mailboxes():
    """A row of apartment mailboxes on the wall: 2 x 2 boxes per block, flyers stuffed in one, one taped shut."""
    for k in range(3):
        A_MAIL.add(f'door{k}', 6, 5, _mailbox_front(k))
    m = Model('mailboxes', A_MAIL, display=DISP_WALL)
    BODY = painted('mail_body', ramp(['#3e4840', '#4e5850', '#5e6860', '#6e7870'], 4), base=1.8)
    m.box([1, 3, 10], [15, 15, 16], BODY)
    kinds = [0, 1, 2, 1]
    for i, (x0, y0) in enumerate(((1.5, 9.5), (8.5, 9.5), (1.5, 3.5), (8.5, 3.5))):
        m.box([x0, y0, 9.6], [x0 + 6, y0 + 5, 10.0], BODY, faces={'north': f'door{kinds[i]}'})
    # flyers sticking out of the top-left box (as seen: model east = box at x 8.5)
    m.box([9.6, 13.6, 8.4], [13.4, 14.2, 9.6], solid('flyer_w', '#ece8dc'))
    m.box([10.2, 13.8, 8.0], [12.8, 14.4, 9.2], solid('flyer_r', '#d84a3a'))
    m.box([1, 15, 9.4], [15, 15.6, 16], BODY)                                     # rain hood
    return m


mailboxes()


# ================================================================================================ bicycle (mamachari)
A_BIKE = Atlas('bicycle')


def _octagon(m, cx, cy, cz, R, mat_, thick=0.7, width=0.8):
    """A wheel rim / tyre in the y-z plane (bike runs along z): 8 segments, the diagonal ones rotated 45 degrees."""
    ap = R * math.cos(math.pi / 8)
    s = 2 * R * math.sin(math.pi / 8)
    hw = width / 2
    for k in range(8):
        ang = k * math.pi / 4
        py, pz = cy + ap * math.cos(ang), cz + ap * math.sin(ang)
        if k in (0, 4):         # top / bottom: along z
            m.box([cx - hw, py - thick / 2, pz - s / 2], [cx + hw, py + thick / 2, pz + s / 2], mat_)
        elif k in (2, 6):       # front / back: along y
            m.box([cx - hw, py - s / 2, pz - thick / 2], [cx + hw, py + s / 2, pz + thick / 2], mat_)
        else:                   # diagonals: a z-segment turned 45 degrees about x
            rot = 45.0 if k in (1, 5) else -45.0
            m.box([cx - hw, py - thick / 2, pz - s / 2], [cx + hw, py + thick / 2, pz + s / 2], mat_,
                  rot=('x', rot, [cx, py, pz]))


def bicycle():
    """A silver mamachari with a wire basket, chain case and rear carrier, on its stand. 1 block wide, it overflows
    the block along z (z -6..22)."""
    m = Model('bicycle', A_BIKE, render_type='cutout', display=disp(gui=(15, 90, 0), gui_scale=0.4))
    TYRE = solid('bike_tyre', '#1a1a1a')
    RIM = solid('bike_rim', '#b8bcbe')
    FRAME = M.brushed('bike_frame', ramp(['#6a7072', '#8a9092', '#a8aeb0', '#c4c8ca', '#dadddf'], 5), base=2.6)
    for cz in (-1.0, 17.0):
        _octagon(m, 8, 5.0, cz, 5.0, TYRE, thick=0.9, width=0.9)
        _octagon(m, 8, 5.0, cz, 4.2, RIM, thick=0.4, width=0.5)
        m.box([7.4, 4.4, cz - 0.6], [8.6, 5.6, cz + 0.6], RIM)                     # hub
        m.box([7.8, 1.0, cz - 0.2], [8.2, 9.0, cz + 0.2], solid('bike_spoke', '#8a8e90'))
        m.box([7.8, 4.8, cz - 4.0], [8.2, 5.2, cz + 4.0], solid('bike_spoke', '#8a8e90'))
    # frame: head tube + fork, the low step-through down tube, seat tube, chain stays, seat stays
    m.box([7.5, 9.0, 0.0], [8.5, 13.5, 1.0], FRAME, rot=('x', -22.5, [8, 11, 0.5]))       # head tube (raked)
    m.box([7.6, 5.0, -1.2], [8.4, 10.0, -0.4], FRAME, rot=('x', -22.5, [8, 7.5, -0.8]))    # fork
    m.box([7.5, 6.6, 0.6], [8.5, 7.6, 7.4], FRAME, rot=('x', 45.0, [8, 7.1, 4.0]))         # down tube (steep)
    m.box([7.5, 4.0, 5.6], [8.5, 5.0, 10.0], FRAME)                                         # low tube to the crank
    m.box([7.5, 4.5, 9.4], [8.5, 12.6, 10.4], FRAME, rot=('x', 22.5, [8, 4.5, 9.9]))       # seat tube
    m.box([7.6, 4.6, 10.0], [8.4, 5.4, 17.0], FRAME)                                        # chain stay
    m.box([7.6, 8.0, 11.4], [8.4, 8.8, 18.2], FRAME, rot=('x', 45.0, [8, 8.4, 14.8]))      # seat stay
    # mudguards, chain case, stand
    for cz in (-1.0, 17.0):
        m.box([7.3, 10.2, cz - 3.4], [8.7, 10.7, cz + 3.4], FRAME)
    m.box([8.6, 2.8, 8.0], [9.4, 6.2, 17.5], solid('bike_case', '#2a2a2c'))
    m.box([7.7, 0, 15.5], [8.3, 4.6, 16.1], solid('bike_stand', '#4a4a4a'))
    # saddle on its post, the swept-back handlebar on its stem, basket over the front wheel, rear carrier
    m.box([7.6, 12.2, 12.5], [8.4, 13.2, 13.3], FRAME)
    m.box([7.0, 13.2, 11.6], [9.0, 14.2, 15.0], solid('bike_saddle', '#3a2216'))
    m.box([7.6, 13.2, 1.6], [8.4, 15.0, 2.4], FRAME)
    m.box([3.5, 14.6, 2.6], [12.5, 15.3, 3.3], FRAME)
    for gx in (3.0, 12.0):
        m.box([gx, 14.5, 2.8], [gx + 1.0, 15.4, 5.0], solid('bike_grip', '#1a1a1a'))
    BASK = M.custom('bike_basket', _basket)
    m.box([5.0, 10.6, -5.6], [11.0, 14.2, -1.6], BASK)
    m.box([6.5, 10.8, 13.8], [9.5, 11.4, 20.6], solid('bike_carrier', '#9aa0a2'))
    m.box([7.7, 5.2, 19.8], [8.3, 10.8, 20.4], solid('bike_carrier', '#9aa0a2'))
    m.box([7.3, 8.6, -2.6], [8.7, 9.8, -1.4], solid('bike_light', '#e8e4d8'))      # dynamo lamp
    return m


def _basket(w, h, face):
    """Wire basket: a grid of silver wires with see-through holes (cutout)."""
    x, y = grid(w, h)
    a = blank(w, h)
    wire = ((x % 2) == 0) | ((y % 2) == 0) | (x == w - 1) | (y == h - 1)
    a[wire] = C('#a8aeb0')
    if face == 'h':
        a[:] = C('#a8aeb0')
        a[((x % 2) == 1) & ((y % 2) == 1)] = C('#7a8082')
    return a


bicycle()


# ================================================================================================ stair railing
A_RAIL = Atlas('stairs_rail')


def stairs_rail():
    """A rusty steel balustrade segment along the facing edge of its block (at rest: the north edge, running along x):
    a handrail at y 7..8.4, a mid rail, and balusters reaching down to y -8, so a rail block placed one block above a
    stair step (beside the stairs) stands on the step; on a landing (placed at floor level) the balusters disappear
    into the floor. Blocks next to each other join into one rail."""
    m = Model('stairs_rail', A_RAIL, display=disp(gui=(20, 160, 0), gui_scale=0.6, gui_t=(0, 2, 0)))
    PAINT = rusty('rail_paint', base=3.0, rust=0.15)
    z0, z1 = 1.0, 2.2
    m.box([0, 7.0, z0 - 0.2], [16, 8.4, z1 + 0.2], PAINT)                       # handrail (a flat steel bar)
    m.box([0, 1.5, z0 + 0.2], [16, 2.1, z1 - 0.2], PAINT)                       # mid rail
    for x in (1.5, 5.5, 9.5, 13.5):
        m.box([x - 0.35, -8.0, z0 + 0.25], [x + 0.35, 7.0, z1 - 0.25], PAINT)  # balusters
    return m


stairs_rail()


# ================================================================================================ nameplates, vacancy sign
A_NAME = Atlas('nameplate')
UNITS = ['101', '102', '103', '104', '201', '202', '203', '204']
NAMES = {0: '田中', 2: '鈴木', 4: '山本', 7: '佐々木'}


def _nameplate(i):
    def fn(w, h):
        """2x density (16 x 10 on an 8 x 5 plate): brushed aluminium number plate with the room number, a cream name
        card below (a surname or left blank; 202 vacant)."""
        a = m_brushed(w, h, ramp(['#6a6e70', '#8a8e90', '#a8acae', '#c4c8c9', '#dadddd'], 5), 13601 + i, along='x',
                      base=3.0)
        frame(a, 0, 0, w - 1, h - 1, '#4a4e50')
        num = text35(UNITS[i], gap=1)
        stamp_mask(a, num, (w - num.shape[1]) // 2, 1, '#1a1c1e')
        rect(a, 1, 7, w - 2, h - 2, '#e8e2cc')
        if i in NAMES:
            # the surname, hand-written: dark strokes one character wide each (illegible at this size, but a name)
            for k in range(len(NAMES[i])):
                xx = 4 + k * 3
                px(a, xx, 7, '#2a2620')
                px(a, xx + 1, 7, '#5a564e')
                px(a, xx, 8, '#5a564e')
                px(a, xx + 1, 8, '#2a2620')
        elif i == 5:
            hline(a, 2, w - 3, 7, '#c8281c')
        return a
    return fn


def nameplate(i):
    key = f'plate{i}'
    A_NAME.add(key, 16, 10, _nameplate(i))
    m = Model(f'nameplate_{i}', A_NAME, display=DISP_WALL)
    m.box([4, 9, 15.6], [12, 14, 16], solid('np_edge', '#5a5e60'), faces={'north': key})
    return m


for _i in range(8):
    nameplate(_i)

A_VAC = Atlas('vacancy_sign')


def _vacancy(w, h):
    """「入居者募集」 sign (3x density: 42 x 42 texels on a 14 x 14 plate): white plate, red border, the words in red,
    the agent's phone number."""
    a = blank(w, h, '#f4f2ea')
    frame(a, 0, 0, w - 1, h - 1, '#c8281c')
    frame(a, 1, 1, w - 2, h - 2, '#c8281c')
    l1 = text_mask('入居者', 12, FONT_SANS_JP_BOLD, binary=True, ss=4, threshold=0.45)
    l2 = text_mask('募集中', 12, FONT_SANS_JP_BOLD, binary=True, ss=4, threshold=0.45)
    stamp_mask(a, l1, (w - l1.shape[1]) // 2, 4, '#c8281c')
    stamp_mask(a, l2, (w - l2.shape[1]) // 2, 18, '#c8281c')
    hline(a, 3, w - 4, 31, '#c8281c')
    tel = text35('TEL 87-4410', gap=1)
    stamp_mask(a, tel, (w - tel.shape[1]) // 2, 34, '#1a1a1a')
    # weathering: rain streaks of dirt from the top, a rusty screw in each corner
    for (cx, cy) in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):
        px(a, cx, cy, '#7a4a2a')
    st = noise(w, h, 13701, beta=0.6, sx=1, sy=8, wrap=False)
    tint(a, '#6a604a', np.clip(st - 0.5, 0, 0.25))
    return a


def vacancy_sign():
    A_VAC.add('face', 42, 42, _vacancy)
    m = Model('vacancy_sign', A_VAC, display=DISP_WALL)
    m.box([1, 1, 15.4], [15, 15, 16], solid('vac_edge', '#d8d6ce'), faces={'north': 'face'})
    return m


vacancy_sign()


# ================================================================================================ swing set
A_SWING = Atlas('swing_set')


def swing_set():
    """The park's swing: a faded yellow A-frame (overflowing the block, x -6..22, up to y 28), two seats on chains."""
    m = Model('swing_set', A_SWING, display=disp(gui=(20, 160, 0), gui_scale=0.3, gui_t=(0, -2, 0)))
    FR = rusty('swing_frame', ramp(['#6a5a1a', '#867224', '#a28a2e', '#b89e3a', '#ccb24a', '#dcc25a'], 6), base=3.0,
               rust=0.1)
    for x0 in (-6.0, 21.0):
        for ang in (22.5, -22.5):
            m.box([x0, 0, 7.4], [x0 + 1.0, 27.0, 8.6], FR, rot=('x', ang, [x0 + 0.5, 27.0, 8.0]))
    m.box([-6.5, 26.0, 7.3], [22.5, 27.2, 8.7], FR)
    CH = solid('swing_chain', '#8a8e90')
    SEAT = rusty('swing_seat', ramp(['#3a120e', '#521a14', '#6a241c', '#7e2e24', '#92382c'], 5), base=3.0, rust=0.05)
    for (sx0, sx1) in ((0.5, 6.5), (9.5, 15.5)):
        for cx in (sx0 + 0.3, sx1 - 0.8):
            m.box([cx, 6.0, 7.8], [cx + 0.5, 26.0, 8.2], CH)
        m.box([sx0, 5.0, 6.6], [sx1, 6.0, 9.4], SEAT)
    return m


swing_set()


# ================================================================================================ puddle, rain window
def _puddle_frame(i):
    """Puddle on the asphalt: dark reflective water with an irregular edge, raindrop rings appearing and spreading
    (16 frames, each drop's ring lives 4 frames)."""
    w = h = 16
    x, y = grid(w, h)
    n = fnoise(w, h, 13801, beta=1.6)
    shape = (np.hypot((x + 0.5 - 8) / 7.2, (y + 0.5 - 8) / 6.4) + n * 0.16) < 1.0
    a = blank(w, h, C('#1a2228', 0))
    a[shape] = C('#1c262c', 196)
    # sky / street-lamp sheen
    sheen = shape & (np.abs((x - y) * 0.7 + n * 2) < 1.2)
    a[sheen] = C('#3a4a54', 205)
    edge = shape & ~(np.roll(shape, 1, 0) & np.roll(shape, -1, 0) & np.roll(shape, 1, 1) & np.roll(shape, -1, 1))
    a[edge] = C('#2a3238', 150)
    r = rng(13802)
    drops = [(int(r.integers(3, 13)), int(r.integers(3, 13)), int(r.integers(0, 16))) for _ in range(10)]
    RINGS = {0: [(0, 0)], 1: [(0, -1), (-1, 0), (1, 0), (0, 1)],
             2: [(0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2)],
             3: [(-1, -3), (0, -3), (1, -3), (-3, -1), (3, -1), (-3, 0), (3, 0), (-3, 1), (3, 1), (-1, 3), (0, 3),
                 (1, 3), (-2, -2), (2, -2), (-2, 2), (2, 2)]}
    for (cx, cy, t0) in drops:
        age = (i - t0) % 16
        if age in RINGS:
            al = (240, 200, 150, 90)[age]
            for (dx, dy) in RINGS[age]:
                xx, yy = cx + dx, cy + dy
                if 0 <= xx < w and 0 <= yy < h and shape[yy, xx]:
                    a[yy, xx] = C('#9aaab6', al)
    return a


register_anim('block/puddle_water', (16, 16), 16, _puddle_frame, frametime=2)
A_PUD = Atlas('puddle')


def puddle():
    m = Model('puddle', A_PUD, render_type='translucent', ao=False, display=DISP_FLOOR)
    m.extra_tex['water'] = ref('block/puddle_water')
    m.extra_tex['particle'] = ref('block/puddle_water')
    m.box([0, 0, 0], [16, 0.1, 16], None, faces={'up': {'r': '#water', 'sub': (0, 0, 16, 16)}})
    return m


puddle()


def _rain_frame(i):
    """Rain on window glass (16 frames, seamless vertically): faint cold glass, fixed beads, drops running down with
    wet trails (1 or 2 px per frame so the loop closes)."""
    w = h = 16
    r = rng(13901)
    x, y = grid(w, h)
    a = blank(w, h, C('#6a8090', 48))
    for _ in range(14):
        bx, by = int(r.integers(0, w)), int(r.integers(0, h))
        a[by, bx] = C('#c8d8e0', 120)
        a[(by + 1) % h, bx] = C('#2a3640', 90)
    for k in range(6):
        cx = int(r.integers(0, w))
        speed = 1 if k % 2 == 0 else 2
        y0 = int(r.integers(0, h))
        head = (y0 + i * speed) % h
        trail = 5 if speed == 2 else 3
        for t in range(1, trail + 1):
            a[(head - t) % h, cx] = C('#a8bcc8', 70 + (trail - t) * 8)
        a[head, cx] = C('#e4eef2', 190)
        a[(head + 1) % h, cx] = C('#24303a', 130)
    return a


register_anim('block/rain_glass', (16, 16), 16, _rain_frame, frametime=2)
A_RWIN = Atlas('rain_window')


def rain_window():
    """An aluminium sash window pane with rain running down the glass (animated, translucent). Each block has its
    own frame, so a 2-wide window shows the meeting stile in the middle."""
    m = Model('rain_window', A_RWIN, render_type='translucent', display=DISP_WALL)
    m.extra_tex['glass'] = ref('block/rain_glass')
    ALU = M.brushed('win_alu', ramp(['#5a5e60', '#7a7e80', '#9a9ea0', '#b8bcbe', '#d0d4d4'], 5), base=3.0)
    m.box([1, 1, 7.6], [15, 15, 8.4], None, faces={'north': {'r': '#glass', 'sub': (1, 1, 15, 15)},
                                                   'south': {'r': '#glass', 'sub': (1, 1, 15, 15)}})
    for (x0, y0, x1, y1) in ((0, 0, 16, 1), (0, 15, 16, 16), (0, 1, 1, 15), (15, 1, 16, 15)):
        m.box([x0, y0, 6.0], [x1, y1, 10.0], ALU)
    m.box([1, 1, 9.0], [15, 1.5, 10.0], solid('win_sill_dirt', '#3a3c3a'))
    return m


rain_window()
