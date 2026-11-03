"""Kitchen and walk-in fridge props (models face north). Counter-height props are full blocks (top = 16 px);
cutting_board and pass_bell stand on the block below (prep_table / pass_shelf)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center, bitmap, m_brushed,
                    m_wood, m_plastic, m_paint, m_cardboard, STEEL, ALU, CHROME, WHITEPLASTIC, BLACKPLASTIC,
                    GREYPAINT, CARDBOARD, WOOD_LIGHT, ORANGE)
import rs_mats as M
from rs_waiting import solid, lit

GREASE = '#4a3418'


def _seed_s(s):
    import zlib
    return zlib.crc32(s.encode()) % 100000


def dirty_steel(key, smudge=0.35, along=None):
    def fn(w, h, face):
        a = m_brushed(w, h, STEEL, _seed_s(key) % 1000 + w * 31 + h, along=along or ('x' if w >= h else 'y'),
                      base=4.3, amp=0.3, smudge=0.0, scratches=0.02)
        if smudge:
            n = noise(w, h, _seed_s(key) + w, beta=1.8, wrap=False)
            tint(a, GREASE, np.clip((n - 0.6) * smudge * 0.5, 0, 0.3))
        if face != 'h':
            grime(a, w * 7 + h, strength=0.04, color=GREASE, bottom=0.18)
        bevel(a, 0.05, -0.06)
        return a
    return M.custom(key, fn)


STEEL_K = dirty_steel('ksteel')


# ================================================================================================ animated textures
def _flame_frame(w, h, fi):
    r = rng(7301 + fi)
    a = blank(w, h)
    x, y = grid(w, h)
    for cx in range(1, w, 3):
        hgt = 3 + r.random() * 3.5 + math.sin(fi * 1.7 + cx) * 0.8
        m = (np.abs(x - cx) <= 1) & (y >= h - hgt)
        core = (np.abs(x - cx) < 0.6) & (y >= h - hgt * 0.55)
        a[m] = C('#2a5aff', 200)
        a[core] = C('#9ad0ff', 230)
        tip = m & (y < h - hgt + 1.2)
        a[tip] = C('#1a3ae0', 150)
    return a


def _water_frame(w, h, fi):
    a = blank(w, h, C('#9ac0d0', 150))
    for y in range(h):
        k = (y + fi * 2) % 4
        if k == 0:
            a[y, :] = C('#e8f6ff', 200)
        elif k == 1:
            a[y, 0] = C('#c8e4f0', 180)
    return a


def _oil_frame(w, h, fi):
    r = rng(7401 + fi)
    a = from_idx(2.5 + noise(w, h, 7402 + fi, beta=1.5, wrap=False) * 0.6,
                 ramp(['#3a2008', '#5a3410', '#7a4a18', '#9a6424', '#b8803a'], 5))
    for _ in range(int(w * h / 10)):
        bx, by = int(r.integers(0, w)), int(r.integers(0, h))
        a[by, bx] = C('#e8c070')
        if bx + 1 < w:
            a[by, bx + 1] = C('#c89a50')
    return a


A_FLAME = Atlas('gas_flame', frames=6, frametime=2, cutout=True)
A_FLAME.add('flame', 16, 8, _flame_frame)
A_WATER = Atlas('tap_water', frames=4, frametime=1)
A_WATER.add('stream', 4, 16, _water_frame)
A_OIL = Atlas('fryer_oil', frames=4, frametime=3)
A_OIL.add('oil', 16, 16, _oil_frame)


# ================================================================================================ gas range
A_RANGE = Atlas('gas_range')


def _range_front(w, h, face):
    a = m_brushed(w, h, STEEL, 7501, along='x', smudge=0.25)
    # knobs row, oven door with a window and a handle
    for i in range(4):
        x0 = 2 + i * (w - 4) // 4
        rect(a, x0, 1, x0 + 1, 2, '#1a1a1a')
        px(a, x0, 1, '#4a4a4a')
    rect(a, 1, 4, w - 2, 4, '#c9cdd0')
    rect(a, 2, 6, w - 3, h - 4, '#3a3c3e')
    rect(a, 4, 8, w - 5, h - 6, '#121416')
    hline(a, 4, w - 5, 8, '#2a3034')
    rect(a, 0, h - 2, w - 1, h - 1, '#1a1a1a')
    grime(a, 7502, strength=0.15, color=GREASE, bottom=0.4)
    return a


def _range_top(w, h, face):
    a = blank(w, h, '#1c1c1c')
    # 4 burners under cast-iron grates
    for (cx, cy) in ((4, 4), (12, 4), (4, 12), (12, 12)):
        ring = ring_mask(w, h, cx, cy, 1.4, 2.6)
        a[ring] = C('#5a5a5a')
        a[disc_mask(w, h, cx, cy, 1.4)] = C('#2a2a2a')
    for k in (0, 7, 8, 15):
        vline(a, k, 0, h - 1, '#2e2e2e')
        hline(a, 0, w - 1, k, '#2e2e2e')
    for k in (4, 12):
        vline(a, k, 0, h - 1, '#3a3a3a')
        hline(a, 0, w - 1, k, '#3a3a3a')
    specks(a, 7503, 0.06, '#5a3a1a', 0.7)
    return a


def gas_range(lit_):
    name = 'gas_range_lit' if lit_ else 'gas_range'
    m = Model(name, A_RANGE, display=DISP_FRONT, render_type='cutout')
    m.box([0, 0, 0], [16, 15, 16], STEEL_K, faces={'north': M.custom('range_front', _range_front)})
    m.box([0, 15, 0], [16, 16, 16], STEEL_K, faces={'up': M.custom('range_top', _range_top)})
    m.box([0, 16, 14], [16, 19, 16], STEEL_K)                                     # back splash
    if lit_:
        for (cx, cz) in ((4, 4), (12, 4), (4, 12), (12, 12)):
            for ang in (0, 90):
                fr, to = ([cx - 2.5, 16, cz], [cx + 2.5, 18.5, cz]) if ang == 0 else ([cx, 16, cz - 2.5], [cx, 18.5, cz + 2.5])
                fs = ('north', 'south') if ang == 0 else ('east', 'west')
                m.box(fr, to, None, faces={f: {'r': (A_FLAME, 'flame')} for f in fs}, glow='all', shade=False)
    return m


gas_range(False)
gas_range(True)

# ================================================================================================ fryer
A_FRYER = Atlas('fryer')


def _fryer_front(w, h, face):
    a = m_brushed(w, h, STEEL, 7601, along='x', smudge=0.3)
    rect(a, 2, 1, w - 3, 3, '#1a1a1c')
    px(a, 3, 2, '#ff4a2a')
    px(a, 5, 2, '#3aff6a')
    rect(a, 8, 2, w - 4, 2, '#3a3a3a')
    vline(a, w // 2, 5, h - 3, '#5a5e60')
    px(a, w // 2 - 2, 8, '#c9cdd0')
    px(a, w // 2 + 1, 8, '#c9cdd0')
    grime(a, 7602, strength=0.25, color=GREASE, bottom=0.5)
    return a


def fryer(on):
    name = 'fryer_on' if on else 'fryer'
    m = Model(name, A_FRYER, display=DISP_FRONT)
    m.box([1, 0, 0], [15, 15, 16], STEEL_K, faces={'north': M.custom('fryer_front', _fryer_front)}, skip=('up',))
    # top with the oil vat (recessed) and rims
    m.box([1, 15, 0], [15, 16, 2.5], STEEL_K)
    m.box([1, 15, 13.5], [15, 16, 16], STEEL_K)
    m.box([1, 15, 2.5], [2.5, 16, 13.5], STEEL_K)
    m.box([13.5, 15, 2.5], [15, 16, 13.5], STEEL_K)
    m.box([2.5, 13.5, 2.5], [13.5, 13.6, 13.5], None, faces={'up': {'r': (A_OIL, 'oil')}},
          glow=('up',) if on else ())
    m.box([2.5, 13.6, 2.5], [13.5, 15, 13.5], dirty_steel('vat_wall'), only=('north', 'south', 'east', 'west'))
    # baskets hanging on the back rail
    m.box([1, 15, 14], [15, 17.5, 16], STEEL_K)
    W = M.custom('basket', lambda w, h, f: _mesh(w, h))
    for x0 in (3, 9):
        m.box([x0, 14, 9], [x0 + 4, 17.5, 13.5], W)
        m.box([x0 + 1.5, 17.5, 11], [x0 + 2.5, 18, 15], M.BLACK_M)
    return m


def _mesh(w, h):
    a = blank(w, h, '#6a6e70')
    for y in range(h):
        for x in range(w):
            if (x + y) % 2:
                a[y, x] = C('#3a3c3e')
    return a


fryer(False)
fryer(True)

# ================================================================================================ griddle
A_GRID = Atlas('griddle')


def _griddle_top(w, h, face):
    a = from_idx(1.6 + noise(w, h, 7701, beta=1.3, wrap=False) * 0.5, ramp(['#0e0e0e', '#1a1a1a', '#262422', '#34302a'], 4))
    specks(a, 7702, 0.08, '#5a3a18', 0.8)
    rect(a, 0, h - 2, w - 1, h - 1, '#3a3c3e')       # trough
    return a


def griddle():
    m = Model('griddle', A_GRID, display=DISP_FRONT)
    m.box([0, 0, 0], [16, 15, 16], STEEL_K, faces={'north': M.custom('griddle_front', _griddle_front)})
    m.box([0, 15, 0], [16, 15.5, 16], STEEL_K, faces={'up': M.custom('griddle_plate', _griddle_top)})
    m.box([0, 15.5, 14], [16, 18, 16], STEEL_K)
    m.box([0, 15.5, 0], [0.75, 17, 14], STEEL_K)
    m.box([15.25, 15.5, 0], [16, 17, 14], STEEL_K)
    m.box([5, 15.5, 6], [11, 16, 9], M.CHROME_M, rot=('y', 22.5, [8, 15.5, 7.5]))   # a spatula left on it
    return m


def _griddle_front(w, h, face):
    a = m_brushed(w, h, STEEL, 7703, along='x', smudge=0.3)
    for i in range(3):
        x0 = 3 + i * 4
        rect(a, x0, 2, x0 + 1, 3, '#1a1a1a')
    rect(a, 1, 6, w - 2, h - 3, '#4a4e50')
    grime(a, 7704, strength=0.2, color=GREASE, bottom=0.5)
    return a


griddle()

# ================================================================================================ kitchen counter
A_KCOUNT = Atlas('kitchen_counter')


def _kc_front(w, h, face):
    a = m_brushed(w, h, STEEL, 7801, along='y', smudge=0.15)
    vline(a, w // 2, 1, h - 3, '#5a5e60')
    for xx in (w // 2 - 2, w // 2 + 1):
        rect(a, xx, 2, xx, 5, '#d9dcde')
    rect(a, 0, h - 2, w - 1, h - 1, '#2a2a2a')
    grime(a, 7802, strength=0.12, color=GREASE, bottom=0.4)
    return a


def kitchen_counter():
    m = Model('kitchen_counter', A_KCOUNT)
    m.box([0, 0, 1], [16, 15, 16], STEEL_K, faces={'north': M.custom('kc_front', _kc_front)}, skip=('up',))
    m.box([0, 15, 0], [16, 16, 16], STEEL_K)
    return m


kitchen_counter()

# ================================================================================================ exhaust hood
A_HOOD = Atlas('exhaust_hood')


def _filters(w, h, face):
    a = blank(w, h, '#2a2c2e')
    for x in range(w):
        if x % 2 == 0:
            vline(a, x, 0, h - 1, '#4a4c4e')
    for y in (0, h - 1):
        hline(a, 0, w - 1, y, '#6a6e70')
    grime(a, 7901, strength=0.35, color='#3a2410')
    return a


def exhaust_hood():
    m = Model('exhaust_hood', A_HOOD, display=disp(gui=(30, 225, 0), gui_scale=0.6, gui_t=(0, -1, 0)))
    H = dirty_steel('hood_steel', 0.45)
    m.box([0, 12, 0], [16, 16, 16], H)                                            # duct box (to the ceiling)
    m.box([0, 6, -2], [16, 12, 16], H, faces={'down': M.custom('hood_filters', _filters)})
    m.box([0, 5, -2], [16, 6, 0], H)                                              # front lip (grease gutter)
    m.box([2, 5.5, 2], [14, 6, 14], M.custom('hood_light', lambda w, h, f: blank(w, h, '#d8dcd0')), only=('down',))
    return m


exhaust_hood()

# ================================================================================================ pot shelf
A_POTS = Atlas('pot_shelf')


def pot_shelf():
    m = Model('pot_shelf', A_POTS, display=DISP_WALL)
    S = M.STEEL_M
    for y in (4, 10):
        m.box([0, y, 8], [16, y + 0.75, 16], S)
    for x0 in (0.5, 15):
        m.box([x0, 3, 15], [x0 + 0.5, 11.5, 16], S)
    POT = M.brushed('pot_alu', ALU, base=3.2)
    m.box([1.5, 4.75, 9], [7, 9.5, 14.5], POT)                                     # stock pot
    m.box([1.2, 9, 9.5], [1.5, 9.75, 14], M.BLACK_M)
    m.box([2, 9.5, 9.5], [6.5, 9.9, 14], POT)
    m.box([8, 4.75, 9.5], [12.5, 7, 14], POT)                                      # sauce pots
    m.box([12.5, 6, 11.25], [15.5, 6.5, 12.25], M.BLACK_M)
    m.box([2, 10.75, 9], [9, 12.5, 15], POT)                                       # stacked pans
    m.box([2.5, 12.5, 9.5], [8.5, 13.5, 14.5], POT)
    m.box([10, 10.75, 9.5], [14.5, 11.5, 14.5], M.custom('pan_black', lambda w, h, f: bevel(blank(w, h, '#1e1e20'))))
    m.box([14.5, 11, 11.5], [16, 11.4, 12.5], M.BLACK_M)
    return m


pot_shelf()

# ================================================================================================ dish rack
A_DISH = Atlas('dish_rack')


def dish_rack():
    m = Model('dish_rack', A_DISH, display=DISP_SMALL)
    RACK = M.custom('rack_blue', lambda w, h, f: _rack(w, h))
    m.box([1, 0, 2], [15, 0.75, 14], RACK)
    for z0 in (2, 13.25):
        m.box([1, 0.75, z0], [15, 4, z0 + 0.75], RACK)
    for x0 in (1, 14.25):
        m.box([x0, 0.75, 2.75], [x0 + 0.75, 4, 13.25], RACK)
    PL = M.custom('plate', lambda w, h, f: _plate_edge(w, h, f))
    for i in range(6):
        x = 2.5 + i * 2
        m.box([x, 0.75, 4], [x + 0.6, 7.5, 12], PL)
    return m


def _rack(w, h):
    a = blank(w, h, '#2a5a9a')
    for x in range(0, w, 2):
        vline(a, x, 0, h - 1, '#1a3a6a')
    return a


def _plate_edge(w, h, face):
    a = blank(w, h, '#f0efe8')
    if face != 'h' and w > 3:
        ring = ring_mask(w, h, w / 2, h / 2, min(w, h) / 2 - 1.2, min(w, h) / 2 - 0.4)
        a[ring] = C('#2d5a9a')
    return a


dish_rack()

# ================================================================================================ kitchen trash
A_TRASH = Atlas('kitchen_trash')


def kitchen_trash():
    m = Model('kitchen_trash', A_TRASH)
    BIN = M.plastic('bin_grey', GREYPAINT, base=3.2)
    m.box([3, 0, 3], [13, 12, 13], BIN)
    m.box([2.5, 12, 2.5], [13.5, 13, 13.5], BIN)
    m.box([4, 13, 4], [12, 13.5, 12], BIN)
    m.box([3, 11, 2.5], [13, 12.6, 3], M.custom('bag', lambda w, h, f: blank(w, h, '#1a1a1c')))   # liner bag lip
    m.box([6, 0, 2], [10, 1, 3], M.BLACK_M)                                                       # pedal
    return m


kitchen_trash()

# ================================================================================================ prep table
A_PREP = Atlas('prep_table')


def prep_table():
    m = Model('prep_table', A_PREP)
    S = dirty_steel('prep_steel', 0.2)
    m.box([0, 15, 0], [16, 16, 16], S)
    m.box([0, 14, 0], [16, 15, 1], S)
    LEG = M.brushed('leg_steel', STEEL, base=4.0)
    for x0 in (0.5, 14):
        for z0 in (0.5, 14):
            m.box([x0, 0, z0], [x0 + 1.5, 14, z0 + 1.5], LEG, skip=('up',))
    m.box([0.5, 3, 0.5], [15.5, 3.75, 15.5], S)                                         # undershelf
    # containers on the undershelf
    m.box([2, 3.75, 3], [7, 7.5, 9], M.custom('container', lambda w, h, f: blank(w, h, '#e8ece8')))
    m.box([2.2, 7.5, 3.2], [6.8, 8, 8.8], M.custom('lid_red', lambda w, h, f: blank(w, h, '#c8281c')))
    m.box([8.5, 3.75, 4], [14, 6.5, 12], M.CARD_M)
    return m


prep_table()

# ================================================================================================ cutting board
A_BOARD = Atlas('cutting_board')


def _board_top(w, h, face):
    a = m_plastic(w, h, WHITEPLASTIC, 8001, base=4.0)
    r = rng(8002)
    for _ in range(int(w * h / 5)):
        x, y = int(r.integers(0, w)), int(r.integers(0, h))
        L = int(r.integers(1, 4))
        for k in range(L):
            px(a, min(w - 1, x + k), y, '#a8a49a', 0.6)
    # old stains (dark, burnt-looking) on the board
    m = noise(w, h, 8003, beta=1.6, wrap=False) > 1.0
    tint(a, '#5a3a24', 0.35, m)
    return a


def cutting_board():
    m = Model('cutting_board', A_BOARD, display=DISP_SMALL)
    m.box([2, 0, 3.5], [14, 1, 12.5], M.custom('board', _board_top))
    # cleaver stuck in the board (blade + handle)
    BLADE = M.custom('blade', lambda w, h, f: _blade(w, h, f))
    m.box([7.5, 0.5, 5], [7.9, 4.5, 10], BLADE, rot=('z', -22.5, [7.7, 0.5, 7.5]))
    m.box([7.3, 4.5, 8.5], [8.1, 5.3, 13], M.custom('handle_black', lambda w, h, f: bevel(blank(w, h, '#1a1412'))),
          rot=('z', -22.5, [7.7, 0.5, 7.5]))
    # chopped vegetables: carrot dice, leek rings, half an onion
    r = rng(8004)
    for i in range(7):
        x, z = 3 + r.random() * 3.5, 4.5 + r.random() * 6.5
        c = ['#e87a1c', '#f08a2a', '#7ab84a', '#e8e4c8'][i % 4]
        m.box([x, 1, z], [x + 0.8, 1.7, z + 0.8], solid(f'veg{i % 4}', c))
    m.box([10, 1, 4.5], [12.5, 2.4, 7], solid('onion', '#efe8d0'))
    m.box([9.8, 1, 9], [13.5, 1.6, 11.5], solid('leek', '#5a9a3a'))
    return m


def _blade(w, h, face):
    a = m_brushed(w, h, CHROME, 8005, along='x')
    if face != 'h':
        hline(a, 0, w - 1, h - 1, '#e8ecee')
        specks(a, 8006, 0.06, '#5a2a1a', 0.7)
    return a


cutting_board()

# ================================================================================================ kitchen sink
A_KSINK = Atlas('kitchen_sink')


def kitchen_sink(running):
    name = 'kitchen_sink_running' if running else 'kitchen_sink'
    m = Model(name, A_KSINK, render_type='translucent' if running else None, display=DISP_FRONT)
    S = STEEL_K
    m.box([0, 0, 1], [16, 12, 16], S, faces={'north': M.custom('kc_front', _kc_front)})
    # basin rims and the deep bowl
    m.box([0, 12, 0], [16, 16, 2], S)
    m.box([0, 12, 14], [16, 16, 16], S)
    m.box([0, 12, 2], [2, 16, 14], S)
    m.box([14, 12, 2], [16, 16, 14], S)
    m.box([2, 12, 2], [14, 12.5, 14], dirty_steel('bowl', 0.5), only=('up',))
    m.box([7.5, 12.5, 7.5], [8.5, 12.6, 8.5], M.BLACK_M, only=('up',))                  # drain
    # tall gooseneck faucet at the back
    C_ = M.CHROME_M
    m.box([7.5, 16, 14], [8.5, 23, 15], C_)
    m.box([7.5, 22, 9.5], [8.5, 23, 15], C_)
    m.box([7.5, 20.5, 9.5], [8.5, 22, 10.5], C_)
    m.box([5.5, 16, 14.2], [6.5, 17.5, 15.2], M.custom('tap_red', lambda w, h, f: blank(w, h, '#c8281c')))
    m.box([9.5, 16, 14.2], [10.5, 17.5, 15.2], M.custom('tap_blue', lambda w, h, f: blank(w, h, '#2a5ad8')))
    if running:
        m.box([7.6, 12.5, 9.6], [8.4, 20.5, 10.4], None, faces={f: {'r': (A_WATER, 'stream')} for f in
                                                                ('north', 'south', 'east', 'west')}, shade=False)
    return m


kitchen_sink(False)
kitchen_sink(True)

# ================================================================================================ dishwasher
A_DW = Atlas('dishwasher')


def _dw_panel(on):
    def fn(w, h):
        a = m_brushed(w, h, STEEL, 8101, along='x')
        rect(a, 1, 1, w - 2, 3, '#1a1a1c')
        px(a, 2, 2, '#3aff6a' if on else '#1a4a2a')
        px(a, 4, 2, '#ff8a2a' if on else '#4a2a1a')
        rect(a, 7, 2, w - 3, 2, '#2a4a3a' if not on else '#6affaa')
        return a
    return fn


def dishwasher(running):
    name = 'dishwasher_running' if running else 'dishwasher'
    A_DW.add('panel_on' if running else 'panel', 14, 4, _dw_panel(running))
    m = Model(name, A_DW, display=disp(gui=(30, 225, 0), gui_scale=0.45, gui_t=(0, -2, 0)))
    S = STEEL_K
    m.box([1, 0, 1], [15, 13, 15], S)                                                   # base cabinet / tank
    m.box([1, 13, 1], [15, 26, 15], S)                                                  # the hood (raised = closed)
    m.box([1, 22, 0.5], [15, 26, 1], S, faces={'north': 'panel_on' if running else 'panel'},
          glow=('north',) if running else ())
    m.box([3, 18, 0], [13, 19, 1], M.CHROME_M)                                           # hood handle
    m.box([1, 26, 4], [15, 27, 12], S)
    return m


dishwasher(False)
dishwasher(True)

# ================================================================================================ pass shelf
A_PASS = Atlas('pass_shelf')


def pass_shelf(lit_):
    name = 'pass_shelf_lit' if lit_ else 'pass_shelf'
    m = Model(name, A_PASS, display=DISP_FRONT, ao=not lit_)
    S = M.STEEL_M
    m.box([0, 0, 0], [16, 1.5, 16], S)                                                  # shelf
    m.box([0, 13, 3], [16, 15.5, 13], S)                                                # heat lamp housing
    m.box([0, 15.5, 7], [16, 16, 9], S)
    LAMP = lit('heat_on', '#ff6a2a') if lit_ else solid('heat_off', '#4a2a1e')
    m.box([1, 12.6, 4], [15, 13, 12], LAMP, only=('down',), glow=('down',) if lit_ else ())
    for x0 in (3, 7.5, 12):
        m.box([x0, 12.4, 6.5], [x0 + 1, 13, 9.5], M.custom('bulb_red', lambda w, h, f: blank(w, h, '#ff8a4a')) if lit_
              else M.custom('bulb_dead', lambda w, h, f: blank(w, h, '#6a3a2a')), glow='all' if lit_ else ())
    # an order ticket rail with one yellowed ticket
    m.box([0, 11, 2.5], [16, 11.5, 3], M.CHROME_M)
    m.box([9, 8, 2.6], [11, 11, 2.7], M.custom('ticket', lambda w, h, f: _ticket(w, h)), only=('north', 'south'))
    # one plate waiting on the shelf, long cold
    m.box([4, 1.5, 6], [9, 2, 11], M.custom('plate_top', lambda w, h, f: _plate_top(w, h)))
    return m


def _ticket(w, h):
    a = blank(w, h, '#e8e0c0')
    for y in range(1, h, 2):
        hline(a, 0, w - 1, y, '#5a5040')
    return a


def _plate_top(w, h):
    a = blank(w, h, '#efeee8')
    if w >= 4:
        ring = ring_mask(w, h, w / 2, h / 2, w / 2 - 1, w / 2 - 0.3)
        a[ring] = C('#2d5a9a')
        px(a, w // 2, h // 2, '#6a3418')
    return a


pass_shelf(False)
pass_shelf(True)

# ================================================================================================ pass bell
A_PBELL = Atlas('pass_bell')


def pass_bell():
    m = Model('pass_bell', A_PBELL, display=disp(gui=(30, 225, 0), gui_scale=1.2, gui_t=(0, 3, 0)))
    G = M.brushed('brass', ramp(['#3a2a10', '#6a5020', '#9a7a34', '#c8a24c', '#e8c870'], 5), base=3.0)
    m.box([5.5, 0, 5.5], [10.5, 1, 10.5], M.custom('bell_base', lambda w, h, f: bevel(blank(w, h, '#3a2414'))))
    m.box([6, 1, 6], [10, 2.2, 10], G)
    m.box([6.5, 2.2, 6.5], [9.5, 3, 9.5], G)
    m.box([7.75, 3, 7.75], [8.25, 3.8, 8.25], M.CHROME_M)
    return m


pass_bell()

# ================================================================================================ walk-in shelf
A_WALKIN = Atlas('walkin_shelf', cutout=True)


def _wire(w, h, face):
    a = blank(w, h)
    for x in range(0, w, 2):
        vline(a, x, 0, h - 1, '#b9bec1')
    hline(a, 0, w - 1, 0, '#d9dde0')
    hline(a, 0, w - 1, h - 1, '#d9dde0')
    # frost
    r = rng(8201 + w)
    fm = r.random((h, w)) < 0.25
    a[fm] = C('#eef6fa')
    return a


def _frosty(base):
    def fn(w, h, face):
        a = blank(w, h, base)
        n = noise(w, h, 8202 + w * 3 + h, beta=1.2, wrap=False)
        dl(a, 0.0)
        tint(a, '#eef6fa', np.clip(0.15 + n * 0.12 + (0.25 if face == 'h' else 0), 0, 0.6))
        return bevel(a, 0.04, -0.05)
    return fn


def walkin_shelf():
    m = Model('walkin_shelf', A_WALKIN, render_type='cutout', display=DISP_FRONT)
    POST = M.brushed('post', CHROME, base=3.4)
    WIRE = M.custom('wire', _wire)
    for x0 in (0.5, 14.5):
        for z0 in (2, 14):
            m.box([x0, 0, z0], [x0 + 1, 16, z0 + 1], POST)
    for y in (1, 6, 11, 15.5):
        m.box([0.5, y, 2], [15.5, y + 0.5, 15], WIRE)
    BOX = M.custom('frosty_card', lambda w, h, f: _frosty_card(w, h, f))
    m.box([1.5, 1.5, 4], [7.5, 5.5, 13], BOX)
    m.box([8, 1.5, 5], [14, 4.5, 14], M.custom('bag_frozen', _frosty('#c8d8e8')))
    m.box([2, 6.5, 3], [9, 9.5, 12], M.custom('crate_blue', _frosty('#2a5a9a')))
    m.box([10, 6.5, 6], [14, 10.5, 14], BOX)
    m.box([1.5, 11.5, 5], [6, 14, 13], M.custom('tub_white', _frosty('#e8e8e2')))
    m.box([7, 11.5, 4], [14, 13, 13], M.custom('meat', _frosty('#a84a3a')))
    return m


def _frosty_card(w, h, face):
    a = m_cardboard(w, h, 8203 + w * 5 + h, base=3.0)
    if face != 'h' and w >= 5 and h >= 3:
        rect(a, 1, 1, w - 2, 1, '#2a5a9a')
    tint(a, '#eef6fa', 0.25 if face == 'h' else 0.12)
    return a


walkin_shelf()

# ================================================================================================ frost decals
A_FROST = Atlas('frost', cutout=True)


def _frost(w, h):
    r = rng(8301)
    a = blank(w, h)
    n = noise(w, h, 8302, beta=1.4, wrap=False)
    x, y = grid(w, h)
    edge = np.minimum(np.minimum(x, w - 1 - x), np.minimum(y, h - 1 - y))
    n = n + np.exp(-edge / 2.5) * 1.2 - 0.6
    m = n > 0.0
    a[m] = C('#dcecf4')
    a[(n > 0.8)] = C('#f6fbff')
    # crystal spikes
    for _ in range(int(w * h / 12)):
        x, y = int(r.integers(0, w)), int(r.integers(0, h))
        L = int(r.integers(1, 3))
        for k in range(L):
            px(a, x + k, y - k, '#f4fbff')
    holes = ((r.random((h, w)) < 0.35) & (n < 0.5)) | (n < 0.15)
    a[holes, 3] = 0
    return a


def frost():
    A_FROST.add('frost', 16, 16, _frost)
    m = Model('frost', A_FROST, render_type='cutout', ao=False, display=DISP_FLOOR)
    m.plane([0, 0.1, 0], [16, 0.1, 16], {'up': 'frost', 'down': 'frost'})
    w_ = Model('frost_wall', A_FROST, render_type='cutout', ao=False, display=DISP_WALL)
    w_.plane([0, 0, 15.9], [16, 16, 15.9], {'north': 'frost', 'south': 'frost'})


frost()
