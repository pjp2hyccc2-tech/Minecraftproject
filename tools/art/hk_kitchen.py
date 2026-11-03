"""DK (dining kitchen): the stainless sink unit, the 2-burner gas table on its stand (blue flames), the old 2-door
fridge with magnets (open: lit inside), rice bag and flowered rice cooker, the salt, a glass, the knife holder, the
microwave on the fridge, the small dining table with its checked vinyl cloth and chairs, the drawer cabinet (the
lighter inside). Counter tops are 14 px high; things on a counter start at y -2."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, fnoise
from hk_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_brushed, m_plastic, m_wood)
import hk_mats as M
from hk_common import (STAINLESS, STEELW, CREAMPL, GREYPL, CHARCOAL, PAPER, PALEWOOD, TEAK, solid, lit, mat, painted,
                       woodmat, disc_strips, cord, register_anim, ref)
from hk_bath import stream, CHROME

CABINET = ramp(['#7a6a52', '#8e7e64', '#a29276', '#b4a488', '#c4b498', '#d2c4a8'], 6)    # yellowed cream doors


def _cab_doors(w, h, face):
    """Cream cabinet front: two doors with a gap, small chrome pulls, grime along the bottom and around the pulls."""
    a = from_idx(4.0 + noise(w, h, 8001 + w, beta=1.6, wrap=False) * 0.22, CABINET)
    bevel(a, 0.05, -0.07)
    if w >= 8 and h >= 6:
        vline(a, w // 2, 1, h - 2, CABINET[1])
        for x0 in (w // 2 - 2, w // 2 + 1):
            px(a, x0 + (1 if x0 < w // 2 else 0), 2, '#d8dcdc')
        tint(a[h - 2:], '#4a3a24', 0.3)
        tint(a, '#5a4a30', 0.12, disc_mask(w, h, w / 2, 2.5, 3, 2))
    return a


CAB = mat('cab_doors', _cab_doors)
CAB_SIDE = painted('cab_side', CABINET, base=3.0, amp=0.2)
COUNTER_ST = M.brushed('counter_st', STAINLESS, base=4.2)


# ================================================================================================ sink
A_SINK = Atlas('kitchen_sink')


def _sink_top(w, h, face):
    """Stainless sink top seen from above (16 x 15): the basin on the left (as seen from the front: model east), the
    ribbed drainboard on the right where the glass stands, water spots, the drain basket."""
    a = m_brushed(w, h, STAINLESS, 8101, along='x', base=4.4)
    if w >= 12:
        x, y = grid(w, h)
        basin = (x >= 1) & (x <= 9) & (y >= 2) & (y <= h - 3)
        a[basin] = m_brushed(w, h, STAINLESS, 8102, along='x', base=2.6)[basin]
        dl(a, -0.06, basin & (y == 2))
        dl(a, 0.04, basin & (y == h - 3))
        rect(a, 4, h // 2 - 1, 5, h // 2, '#3a3e40')
        px(a, 4, h // 2 - 1, '#6a6e70')
        ribs = (x >= 11) & (x <= w - 2) & (y >= 2) & (y <= h - 3) & ((y % 2) == 0)
        dl(a, -0.04, ribs)
        specks(a, 8103, 0.04, '#c8cccc', 0.8)
    return a


def kitchen_sink(running):
    name = 'kitchen_sink' + ('_running' if running else '')
    m = Model(name, A_SINK, render_type='translucent' if running else None, display=DISP_FRONT)
    m.box([0, 0, 2], [16, 13, 15.5], CAB_SIDE, faces={'north': CAB})
    m.box([0, 0, 1.5], [16, 0.8, 2.2], solid('kick', '#2a2620'))
    m.box([0, 13, 1.2], [16, 14, 16], COUNTER_ST, faces={'up': M.custom('sink_top', _sink_top)})
    # wall-mounted faucet: body on the wall, a swivel spout over the basin, two handles
    # wall-mounted faucet over the basin (basin: model x 1..10; the ribbed drainboard: x 11..15)
    m.box([3.0, 15, 15.2], [8.0, 16.2, 16], CHROME)
    m.box([5.0, 15.2, 9.0], [6.0, 16.0, 15.2], CHROME)
    m.box([5.1, 14.4, 9.0], [5.9, 15.2, 9.8], CHROME)
    for hx in (2.5, 7.5):
        m.box([hx, 15.8, 14.6], [hx + 1, 16.8, 15.6], solid('sink_handle', '#d8dcde'))
    # a sponge and a detergent bottle at the back of the drainboard
    m.box([11.2, 14, 12.6], [13.6, 15.0, 14.2], solid('sponge', '#e8c83a'))
    m.box([11.2, 14.6, 12.6], [13.6, 15.0, 14.2], solid('sponge_green', '#3a8a4a'))
    m.box([14.0, 14, 13.2], [15.5, 18.0, 14.8], solid('deterg', '#7ab8d8'))
    m.box([14.4, 18, 13.6], [15.1, 18.8, 14.4], solid('deterg_cap', '#e8e4dc'))
    if running:
        stream(m, 5.5, 9.4, 14.4, 14.0 - 2.4)
    return m


kitchen_sink(False)
kitchen_sink(True)


# ================================================================================================ gas stove
A_GAS = Atlas('gas_stove')


def _flame_frame(i):
    """Blue gas flame ring seen at an angle: a ring of tongues flickering (animated)."""
    w = h = 16
    r = rng(8201 + i)
    a = blank(w, h)
    x, y = grid(w, h)
    for k in range(16):
        if r.random() < 0.85:
            hh = 3 + int(r.integers(0, 4))
            for yy in range(h - hh, h):
                f = (yy - (h - hh)) / hh
                c = C('#a8d0ff') if f < 0.35 else (C('#4a7aff') if f < 0.75 else C('#2a3ad8'))
                a[yy, k] = c
                a[yy, k, 3] = 210 if f > 0.2 else 150
    return a


register_anim('block/gas_flame', (16, 16), 8, _flame_frame, frametime=1, cutout=False)


def _stove_top(w, h, face):
    """Enamel top (13 x 8): black grate trivets over two burner caps."""
    a = blank(w, h, '#2a2a2c')
    x, y = grid(w, h)
    for cx in (3.5, 9.5):
        ring = (np.hypot(x + 0.5 - cx, y + 0.5 - h / 2) < 2.4)
        a[ring] = C('#4a4a4c')
        a[np.hypot(x + 0.5 - cx, y + 0.5 - h / 2) < 1.2] = C('#7a6a5a')
    specks(a, 8211, 0.05, '#5a4a3a', 0.7)
    return a


def _stove_front(w, h, face):
    """The stove body front (13 x 3): two knobs, the fish-grill drawer with its little window."""
    a = blank(w, h, '#d8d4c8')
    bevel(a, 0.05, -0.07)
    rect(a, 4, 0, 8, 2, '#2a2a2c')
    rect(a, 5, 1, 7, 1, '#5a4a3a')
    for kx in (1, 10):
        rect(a, kx, 1, kx + 1, 2, '#1a1a1a')
        px(a, kx, 1, '#8a8a8a')
    return a


def gas_stove(lit_):
    name = 'gas_stove' + ('_lit' if lit_ else '')
    m = Model(name, A_GAS, render_type='translucent' if lit_ else None, display=DISP_FRONT)
    # the gas stand (ガス台): an open steel shelf frame with a few pots underneath
    STAND = painted('gas_stand', CREAMPL, base=4.0, amp=0.2)
    for (lx, lz) in ((1, 2), (14, 2), (1, 14), (14, 14)):
        m.box([lx, 0, lz], [lx + 1, 10, lz + 1], STAND)
    m.box([1, 9, 2], [15, 10, 15], STAND)
    m.box([1, 2, 2], [15, 2.6, 15], STAND)
    m.box([3, 2.6, 5], [8, 6.0, 10], M.brushed('pot', STEELW, base=3.8))           # a big pot below
    m.box([9.5, 2.6, 6], [13.5, 4.6, 11], solid('pan', '#2a2a2c'))                   # a frying pan
    # the stove itself
    m.box([1.5, 10, 3], [14.5, 13, 13], painted('stove_body', ramp(['#a8a498', '#bcb8ac', '#ccc8bc', '#d8d4c8',
                                                                     '#e4e0d4'], 5), base=3.0),
          faces={'north': M.custom('stove_front', _stove_front), 'up': M.custom('stove_top', _stove_top)})
    # grates (trivets) standing on top
    GR = solid('grate', '#141414')
    for cx in (5.0, 11.0):
        m.box([cx - 2.6, 13, 7.4], [cx + 2.6, 13.6, 8.6], GR)
        m.box([cx - 0.6, 13, 5.4], [cx + 0.6, 13.6, 10.6], GR)
    # a dented aluminium kettle on the left burner (as seen from the front: model east)
    KET = M.brushed('kettle', ramp(['#6a6a62', '#8a8a80', '#a8a89e', '#c4c4ba', '#dcdcd2'], 5), base=3.2)
    m.box([9.0, 13.6, 5.8], [13.0, 16.4, 10.2], KET)
    m.box([9.6, 16.4, 6.4], [12.4, 17.0, 9.6], KET)
    m.box([10.6, 17.0, 7.6], [11.4, 17.6, 8.4], solid('ket_knob', '#1a1a1a'))
    m.box([7.4, 14.6, 7.6], [9.0, 15.4, 8.4], KET)                                    # spout
    m.box([10.8, 16.4, 5.6], [11.2, 18.6, 10.4], solid('ket_handle', '#2a2a2a'))
    if lit_:
        m.extra_tex['flame'] = ref('block/gas_flame')
        cx = 5.0
        for (x0, z0, x1, z1, f) in ((cx - 2, 6, cx + 2, 6, 'north'), (cx - 2, 10, cx + 2, 10, 'south'),
                                    (cx - 2, 6, cx - 2, 10, 'west'), (cx + 2, 6, cx + 2, 10, 'east')):
            m.box([x0, 13.0, z0], [x1 if x1 != x0 else x0 + 0.01, 14.8, z1 if z1 != z0 else z0 + 0.01], None,
                  faces={f: {'r': '#flame', 'sub': (0, 10, 16, 16)},
                         {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[f]:
                             {'r': '#flame', 'sub': (0, 10, 16, 16)}},
                  glow=('north', 'south', 'east', 'west'), shade=False)
    return m


gas_stove(False)
gas_stove(True)


# ================================================================================================ fridge
A_FRIDGE = Atlas('fridge_old')
FRIDGE = ramp(['#8a8670', '#a29e86', '#b6b29a', '#c8c4ac', '#d6d2ba', '#e2dec6'], 6)    # cream, yellowed


def _fridge_door(kind):
    def fn(w, h):
        a = from_idx(4.0 + noise(w, h, 8301 + h, beta=1.6, wrap=False) * 0.2, FRIDGE)
        bevel(a, 0.06, -0.08, 0.03, -0.04)
        if kind == 'main':
            # magnets: a red ladybug, a yellow takeout flyer, a photo (two people, faded), a blue round one
            px(a, 3, 2, '#c8281c')
            px(a, 4, 2, '#1a1a1a')
            rect(a, 6, 3, 9, 7, '#e8d860')
            for yy in (4, 5, 6):
                hline(a, 6, 8, yy, '#a88a20')
            rect(a, 2, 6, 4, 8, '#e8e4dc')
            rect(a, 2, 6, 4, 7, '#7a8a9a')
            px(a, 9, 9, '#3a6ac8')
            tint(a[h - 2:], '#6a5a3a', 0.25)
        else:
            hline(a, 2, w - 3, h - 2, FRIDGE[2])
        return a
    return fn


def _fridge_inside(w, h, face):
    """The lit inside (open door): white plastic, wire shelves, a few sad things."""
    a = blank(w, h, '#f2eed8')
    x, y = grid(w, h)
    if face != 'h' and h >= 8:
        for yy in range(3, h, 4):
            hline(a, 0, w - 1, yy, '#b8b4a0')
        rect(a, 2, h - 3, 4, h - 1, '#f8f8f4')              # milk carton
        rect(a, 2, h - 3, 4, h - 3, '#3a6ac8')
        rect(a, 6, h - 2, 9, h - 1, '#e8d8b8')               # eggs
        rect(a, 3, 4, 6, 6, '#c86a3a')                      # leftover in wrap
        rect(a, 8, 1, 9, 2, '#3a8a3a')                      # a bottle
    return a


def fridge_old(open_):
    name = 'fridge_old' + ('_open' if open_ else '')
    A_FRIDGE.add('door_main', 12, 10, _fridge_door('main'))
    A_FRIDGE.add('door_freezer', 12, 5, _fridge_door('freezer'))
    m = Model(name, A_FRIDGE, display=DISP_FRONT)
    BODY = painted('fridge_body', FRIDGE, base=4.0, amp=0.2, wear_amt=0.03)
    HANDLE = M.brushed('fridge_handle', STEELW, base=4.6)
    m.box([2, 0.6, 3], [14, 16, 15.5], BODY)
    m.box([2.5, 0, 3.5], [13.5, 0.6, 15], solid('fridge_kick', '#2a2a28'))
    # freezer door (top), always closed
    m.box([2, 11, 2.3], [14, 16, 3], BODY, faces={'north': 'door_freezer'})
    m.box([3.0, 11.5, 1.6], [3.8, 15, 2.3], HANDLE)
    if not open_:
        m.box([2, 0.8, 2.3], [14, 10.8, 3], BODY, faces={'north': {'r': 'door_main', 'sub': (0, 0, 12, 10)}})
        m.box([3.0, 5, 1.6], [3.8, 10, 2.3], HANDLE)
    else:
        IN = mat('fridge_in', _fridge_inside)
        # the open cavity front (lit inside) and the door standing open toward the north on its west hinge
        m.box([2.5, 1.0, 2.95], [13.5, 10.6, 3.0], None, faces={'north': IN}, glow=('north',))
        m.box([12.6, 0.8, -9.4], [13.4, 10.8, 2.6], BODY, faces={'east': {'r': 'door_main', 'sub': (0, 0, 12, 10)},
                                                                  'west': IN}, glow=('west',))
        m.box([13.4, 5, -8.2], [14.1, 10, -7.4], HANDLE)
    return m


fridge_old(False)
fridge_old(True)


# ================================================================================================ rice, salt, cup
A_RICE = Atlas('rice_bag')


def _rice_bag_face(w, h, face):
    """A 5 kg paper rice bag: kraft paper, a green rice-plant print and a red brand band (no brand), folded top."""
    a = from_idx(4.0 + noise(w, h, 8401 + w, beta=1.6, wrap=False) * 0.25,
                 ramp(['#8a7656', '#a08a68', '#b49e7a', '#c4ae8a', '#d2bc98', '#dcc8a6'], 6))
    if face != 'h' and w >= 6 and h >= 8:
        rect(a, 0, 2, w - 1, 3, '#b8302a')
        hline(a, 1, w - 2, 2, '#d8483a')
        x, y = grid(w, h)
        for (sx, sy) in ((w // 2, 5), (w // 2 - 1, 6), (w // 2 + 1, 6), (w // 2, 7), (w // 2, 8)):
            px(a, sx, sy, '#4a7a3a')
        px(a, w // 2 - 1, 5, '#d8b84a')
        px(a, w // 2 + 1, 5, '#d8b84a')
        tint(a[h - 2:], '#6a5a3a', 0.2)
    return a


def rice_bag():
    m = Model('rice_bag', A_RICE, display=DISP_SMALL)
    B = mat('rice_bag_face', _rice_bag_face)
    m.box([4, 0, 6], [12, 9, 11], B)
    m.box([4.3, 9, 6.5], [11.7, 10.5, 10.5], B)                       # rolled-down top
    m.box([3.6, 0, 5.6], [12.4, 3, 11.4], B)                          # slumped bottom
    return m


rice_bag()

A_COOK = Atlas('rice_cooker')


def _cooker_side(w, h, face):
    """Showa rice cooker: white body with an orange flower print band, chrome lid."""
    a = from_idx(5.0 + noise(w, h, 8501 + w, beta=1.6, wrap=False) * 0.2, CREAMPL)
    if face != 'h' and h >= 4:
        hline(a, 0, w - 1, 1, '#e8a03a')
        for xx in range(0, w, 3):
            px(a, xx, 2, '#d86a2a')
            px(a, (xx + 1) % w, 2, '#4a8a3a')
        bevel(a, 0.04, -0.06)
        if w >= 5:
            px(a, w // 2, h - 2, '#e83a2a')             # the "keep warm" lamp (off)
    return a


def rice_cooker():
    m = Model('rice_cooker', A_COOK, display=DISP_SMALL)
    S = mat('cooker_side', _cooker_side)
    LID = M.brushed('cooker_lid', STEELW, base=4.4)
    y0 = -2.0
    m.box([4.5, y0, 4.5], [11.5, y0 + 5, 11.5], S)
    m.box([5.0, y0 + 5, 5.0], [11.0, y0 + 6, 11.0], LID)
    m.box([7.0, y0 + 6, 7.5], [9.0, y0 + 6.8, 8.5], solid('cooker_knob', '#2a2a2a'))
    m.box([3.8, y0 + 3.5, 7.4], [4.5, y0 + 4.2, 8.6], solid('cooker_handle', '#2a2a2a'))
    m.box([11.5, y0 + 3.5, 7.4], [12.2, y0 + 4.2, 8.6], solid('cooker_handle', '#2a2a2a'))
    return m


rice_cooker()

A_SALT = Atlas('salt_shaker')


def salt_shaker():
    """The table-salt bottle (red cap, white label) and the 1 kg bag of salt lying next to it."""
    m = Model('salt_shaker', A_SALT, display=DISP_SMALL)
    y0 = -2.0
    m.box([6.5, y0, 7], [8.5, y0 + 3.4, 9], mat('salt_bottle', _salt_bottle))
    m.box([6.6, y0 + 3.4, 7.1], [8.4, y0 + 4.2, 8.9], solid('salt_cap', '#c8281c'))
    m.box([9.2, y0, 6], [12.6, y0 + 1.4, 10.6], mat('salt_bag', _salt_bag))
    return m


def _salt_bottle(w, h, face):
    a = blank(w, h, '#e8e8e4')
    if face != 'h':
        hline(a, 0, w - 1, h // 2, '#2a5ac8')
    return a


def _salt_bag(w, h, face):
    a = blank(w, h, '#f0f0ec')
    if face == 'h':
        rect(a, 0, 1, w - 1, 2, '#2a6ac8')
        px(a, 1, h - 2, '#c8281c')
    return a


salt_shaker()

A_CUP = Atlas('cup_glass')


def cup_glass():
    """A plain drinking glass (コップ) standing on a counter / the table (top 14 px: the model starts at y -2), off to
    the east side so on the sink it stands on the drainboard, not over the basin."""
    m = Model('cup_glass', A_CUP, render_type='translucent', display=DISP_SMALL)
    GL = M.glass('cup_gl', '#c8dcd8', 86)
    y0 = -2.0
    m.box([11.6, y0, 7.8], [14.1, y0 + 3.6, 10.3], GL, skip=('up',))
    m.box([11.8, y0 + 0.01, 8.0], [13.9, y0 + 0.4, 10.1], M.glass('cup_base', '#d8ece8', 150), only=('up',))
    return m


cup_glass()


# ================================================================================================ knife holder
A_KNIFE = Atlas('knife_rack')


def knife_rack(has):
    """Wall-mounted wooden knife holder with utensil hooks; has_knife: the 三徳 knife's handle in the slot."""
    name = 'knife_rack' + ('_has_knife' if has else '')
    m = Model(name, A_KNIFE, display=DISP_WALL)
    WOOD = woodmat('krack_wood', PALEWOOD, base=3.2)
    m.box([5, 2, 13.5], [11, 8, 16], WOOD)
    m.box([5.5, 8, 14.0], [10.5, 8.2, 15.5], solid('krack_slot', '#1a120a'))
    if has:
        m.box([6.4, 8.2, 14.4], [7.6, 12.6, 15.1], solid('knife_handle', '#2a1a10'))
        m.box([6.3, 8.0, 14.3], [7.7, 8.6, 15.2], M.brushed('knife_bolster', STEELW, base=4.6))
    # hooks with a ladle and a turner hanging beside it
    m.box([1.5, 10, 15.4], [14.5, 10.6, 16], M.brushed('krack_bar', STEELW, base=4.0))
    m.box([2.2, 3.5, 15.0], [2.8, 10.0, 15.4], solid('ladle', '#b8bcbe'))
    m.box([1.6, 2.5, 14.4], [3.4, 3.5, 15.6], solid('ladle', '#b8bcbe'))
    m.box([12.8, 4.0, 15.0], [13.4, 10.0, 15.4], solid('turner', '#1a1a1a'))
    m.box([12.2, 2.0, 15.0], [14.0, 4.0, 15.3], solid('turner_head', '#2a2a2a'))
    return m


knife_rack(False)
knife_rack(True)


# ================================================================================================ microwave
A_MW = Atlas('microwave')


def _mw_front(w, h):
    """Old microwave (11 x 7): the dark mesh window on the left, the two dials on the right, the door handle."""
    a = from_idx(4.0 + noise(w, h, 8701, beta=1.6, wrap=False) * 0.2, CREAMPL)
    bevel(a, 0.05, -0.07)
    rect(a, 1, 1, 6, 5, '#1a1a1a')
    x, y = grid(w, h)
    mesh = ((x + y) % 2 == 0) & (x >= 1) & (x <= 6) & (y >= 1) & (y <= 5)
    a[mesh] = C('#2a2a2a')
    px(a, 2, 2, '#4a4a48')
    vline(a, 7, 1, 5, '#8a8676')
    for cy in (2, 4):
        rect(a, 8, cy, 9, cy, '#e8e4d8')
        px(a, 9, cy, '#3a3a3a')
    return a


def microwave():
    A_MW.add('front', 11, 7, _mw_front)
    m = Model('microwave', A_MW, display=DISP_FRONT)
    m.box([2.5, 0.4, 4], [13.5, 7.4, 12.5], painted('mw_body', CREAMPL, base=4.0, amp=0.2), faces={'north': 'front'})
    for (fx, fz) in ((3, 4.5), (12.5, 4.5), (3, 11.5), (12.5, 11.5)):
        m.box([fx, 0, fz], [fx + 0.5, 0.4, fz + 0.5], solid('mw_foot', '#1a1a1a'))
    return m


microwave()


# ================================================================================================ dining table, chair
A_TABLE = Atlas('dining_table')


def _check_cloth(w, h, face):
    """Checked vinyl tablecloth (red / white gingham), faded, a few cigarette burns and a ring."""
    x, y = grid(w, h)
    a = blank(w, h, '#e8e0d4')
    cx = (x // 2) % 2 == 0
    cy = (y // 2) % 2 == 0
    a[cx ^ cy] = C('#c88a80')
    a[cx & cy] = C('#a84a40')
    if face == 'h' and w >= 10:
        ring = np.abs(np.hypot(x + 0.5 - 5, y + 0.5 - 4) - 1.6) < 0.45
        tint(a, '#7a5a3a', 0.35, ring)
        px(a, 10, 7, '#3a2a1a')
        tint(a, '#d8c8a0', 0.12)
    return a


def dining_table():
    """The small Showa dining table under a red gingham vinyl cloth, 14 px high like the counters (a glass on it
    stands at y -2 in the block above)."""
    m = Model('dining_table', A_TABLE, display=DISP_FRONT)
    CL = mat('check_cloth', _check_cloth)
    LEG = M.brushed('table_leg', STEELW, base=4.2)
    m.box([1, 13, 2], [15, 14, 14], CL)
    m.box([0.6, 12, 1.6], [15.4, 13, 2.0], CL)                      # the cloth hanging over the edges
    m.box([0.6, 12, 14.0], [15.4, 13, 14.4], CL)
    m.box([0.6, 12, 2.0], [1.0, 13, 14.0], CL)
    m.box([15.0, 12, 2.0], [15.4, 13, 14.0], CL)
    for (lx, lz) in ((2, 3), (13, 3), (2, 12), (13, 12)):
        m.box([lx, 0, lz], [lx + 1, 12, lz + 1], LEG)
    return m


dining_table()

A_CHAIR = Atlas('dining_chair')
VINYLR = ramp(['#3a120e', '#521a14', '#6a241c', '#7e2e24', '#92382c', '#a44434'], 6)


def dining_chair():
    """Chrome tube chair with a red vinyl seat and back (the seat cracked, stuffing showing). Faces north = the
    sitter looks north; the backrest is on the south."""
    m = Model('dining_chair', A_CHAIR, display=DISP_FRONT)
    TUBE = M.brushed('chair_tube', STEELW, base=4.4)
    SEAT = M.vinyl('chair_seat', VINYLR, base=3.0, cracks=0.4, piping=True)
    m.box([3, 6.5, 3], [13, 8, 13], SEAT)
    m.box([3.2, 9.5, 12.0], [12.8, 14.5, 13.2], SEAT)
    for (lx, lz) in ((3.2, 3.2), (11.8, 3.2), (3.2, 11.8), (11.8, 11.8)):
        m.box([lx, 0, lz], [lx + 1, 6.5, lz + 1], TUBE)
    for lx in (3.4, 11.6):
        m.box([lx, 8, 12.3], [lx + 1, 9.5, 13.0], TUBE)
    m.box([7.2, 7.95, 6.0], [8.6, 8.05, 7.6], solid('chair_stuffing', '#e8dcc0'))
    return m


dining_chair()


# ================================================================================================ drawer cabinet
A_DRAW = Atlas('drawer')


def _drawer_front(w, h):
    a = from_idx(3.6 + noise(w, h, 8801 + w, beta=1.6, wrap=False) * 0.22, CABINET)
    bevel(a, 0.05, -0.07)
    hline(a, 3, w - 4, 1, '#d8dcdc')
    return a


def _drawer_inside(w, h, face):
    """The open drawer seen from above: chopsticks, rubber bands, a box of matches, and the red disposable lighter."""
    a = from_idx(2.4 + noise(w, h, 8811, beta=1.4, wrap=False) * 0.25, PALEWOOD)
    if w >= 10 and h >= 4:
        hline(a, 1, 9, 1, '#c8a878')
        hline(a, 1, 9, 2, '#b89868')
        px(a, 11, 1, '#c87a3a')
        px(a, 12, 1, '#a85a2a')
        rect(a, 4, h - 2, 6, h - 2, '#c8281c')            # the lighter
        px(a, 6, h - 2, '#d8dcdc')
        px(a, 9, h - 2, '#3a6ac8')
    return a


def drawer(open_):
    name = 'drawer' + ('_open' if open_ else '')
    A_DRAW.add('front', 14, 3, _drawer_front)
    m = Model(name, A_DRAW, display=DISP_FRONT)
    m.box([0, 0, 2], [16, 13, 15.5], CAB_SIDE, faces={'north': {'r': M.custom('drawer_doors', _cab_doors)}})
    m.box([0, 0, 1.5], [16, 0.8, 2.2], solid('kick', '#2a2620'))
    m.box([0, 13, 1.2], [16, 14, 16], COUNTER_ST)        # the 流し台 set's one stainless top, as over the sink
    dz = -7.0 if open_ else 0.0
    m.box([1, 9.5, 1.6 + dz], [15, 12.5, 2.2 + dz], CAB_SIDE, faces={'north': 'front'})
    if open_:
        IN = mat('drawer_in', _drawer_inside)
        m.box([1.5, 9.6, -5.4], [14.5, 12.0, 2.0], CAB_SIDE, faces={'up': IN}, skip=('north',))
    return m


drawer(False)
drawer(True)
