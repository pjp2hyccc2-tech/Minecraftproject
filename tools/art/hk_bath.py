"""浴室 / 脱衣所 / トイレ: the deep square bathtub (head/foot halves, water 0/1/2, lid) with its tiled apron, the bath
tap and shower, stool and basin bucket, the twin-tub washing machine, the washbasin, the small mirror and the old
western toilet. Running water uses the animated textures block/water_stream and block/bath_water."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, fnoise
from hk_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_brushed, m_plastic)
import hk_mats as M
from hk_common import (STAINLESS, STEELW, CREAMPL, GREYPL, CHARCOAL, PAPER, solid, lit, mat, painted, woodmat,
                       disc_strips, cord, register_anim, ref, water_frame, stream_frame)

# ================================================================================================ water textures
register_anim('block/bath_water', (16, 16), 16,
              lambda i: water_frame(16, 16, i, 16, 7001, base='#16242a', alpha=205, bright='#4a6a76'),
              frametime=4, interpolate=True)
register_anim('block/water_stream', (16, 16), 8, lambda i: stream_frame(16, 16, i, 8), frametime=1)
WATER = ref('block/bath_water')
STREAM = ref('block/water_stream')
TILE = 'hitori:block/bath_tile_wall'


def stream(m, x, z, y_top, y_bot, w=0.6):
    """A falling water stream (translucent, animated) - only use in translucent models."""
    m.extra_tex['stream'] = STREAM
    h = w / 2
    m.box([x - h, y_bot, z - h], [x + h, y_top, z + h], None,
          faces={f: {'r': '#stream', 'sub': (0, 0, 4, 16)} for f in ('north', 'south', 'east', 'west')}, shade=False)


CHROME = M.brushed('bath_chrome', ramp(['#3a3f44', '#646b71', '#949ba0', '#c4c9cc', '#eef0f1'], 5), base=2.8)


# ================================================================================================ bathtub
A_TUB = Atlas('bathtub')
TUB_IN = ramp(['#3e4446', '#52585a', '#666c6e', '#7b8183', '#90969a', '#a5abae'], 6)


def _tub_inner(w, h, face):
    """Stainless inner wall: brushed, darker toward the bottom, a scum line where the water stood for years."""
    a = m_brushed(w, h, TUB_IN, 7101 + w * 3 + h, along='x', base=3.2)
    if face != 'h':
        x, y = grid(w, h)
        g = y / max(h - 1, 1)
        dl(a, 0.0)
        a[..., :3] *= (1.0 - g[..., None] * 0.25)
        if h >= 8:
            hline(a, 0, w - 1, 2, '#8a8270')            # the old water line
            hline(a, 0, w - 1, 3, '#5a5448')
    else:
        # the bottom: darker, a hair, rust around the drain
        dl(a, -0.06)
        if w >= 8 and h >= 8:
            px(a, w // 2, h // 2, '#1a1c1c')
            px(a, w // 2 + 1, h // 2, '#5a3a22')
            line(a, 2, 3, 6, 4, '#1c1a18', 0.6)
    return a


def _tub_rim(w, h, face):
    a = m_brushed(w, h, STAINLESS, 7111 + w + h, along='x' if w >= h else 'y', base=4.6)
    bevel(a, 0.06, -0.06)
    return a


def _lid_slats(w, h, face):
    """Roll-up bath lid (風呂ふた): pale blue plastic slats, ribbed; lighter ridges, scum in the grooves."""
    x, y = grid(w, h)
    a = blank(w, h, '#a8bcc4')
    if face == 'h':
        dl(a, 0.06, (y % 2) == 0)
        dl(a, -0.08, (y % 4) == 3)
        tint(a, '#7a8a80', 0.15, (y % 4) == 3)
    else:
        dl(a, 0.04, y == 0)
    return a


def bathtub(part, water, lid=False):
    """part head (closed end at the north) / foot (closed end at the south); the rim at y 12, inside 2..12."""
    name = f'bathtub_{part}_lid' if lid else f'bathtub_{part}_{water}'
    rt = 'translucent' if (water and not lid) else None
    m = Model(name, A_TUB, render_type=rt, display=DISP_FRONT)
    m.extra_tex['tile'] = TILE
    INNER = mat('tub_inner', _tub_inner)
    RIM = mat('tub_rim', _tub_rim)

    def T(fw, fh, u0=0.0):
        """the bath wall tiles on an outside face of size fw x fh (bottom-aligned, like the tiles of the room)."""
        return {'r': '#tile', 'sub': (u0, 16 - fh, u0 + fw, 16)}
    end_z = (0.0, 1.5) if part == 'head' else (14.5, 16.0)
    # tiled apron walls (outside) with stainless inner faces, the rim on top
    m.box([0, 0, 0], [1.5, 12, 16], INNER, faces={'west': T(16, 12), 'north': T(1.5, 12, 14.5), 'south': T(1.5, 12),
                                                    'up': RIM, 'east': INNER}, skip=('down',))
    m.box([14.5, 0, 0], [16, 12, 16], INNER, faces={'east': T(16, 12), 'north': T(1.5, 12), 'south': T(1.5, 12, 14.5),
                                                      'up': RIM, 'west': INNER}, skip=('down',))
    zs = {'north': T(13, 12, 1.5), 'up': RIM, 'south': INNER} if part == 'head' else \
        {'south': T(13, 12, 1.5), 'up': RIM, 'north': INNER}
    m.box([1.5, 0, end_z[0]], [14.5, 12, end_z[1]], INNER, faces=zs, skip=('down', 'east', 'west'))
    # the rolled rim lip standing a little proud on the outside
    m.box([-0.3, 11.4, 0], [0, 12, 16], RIM, skip=('down',))
    m.box([16, 11.4, 0], [16.3, 12, 16], RIM, skip=('down',))
    # floor of the tub
    iz0, iz1 = (1.5, 16.0) if part == 'head' else (0.0, 14.5)
    m.box([1.5, 0, iz0], [14.5, 2, iz1], INNER, only=('up',))
    if part == 'foot' and not lid:
        # the rubber plug on its chain, lying at the drain, the chain over the rim
        m.box([7.2, 2, 6.8], [8.8, 2.6, 8.4], solid('tub_plug', '#1a1a1a'))
        for k in range(5):
            m.box([8.6 + k * 1.1, 2.0 + k * 2.0, 7.4], [9.2 + k * 1.1, 2.4 + k * 2.0, 7.8], solid('tub_chain', '#a8acae'))
    if lid:
        # the roll-up lid lying on the rim (one half per block), a rolled end at the head
        LID = mat('tub_lid', _lid_slats)
        m.box([1.0, 12, iz0 if part == 'head' else 0.0], [15.0, 12.6, iz1 if part == 'foot' else 16.0], LID)
        if part == 'head':
            m.box([1.0, 12, 1.2], [15.0, 14.2, 3.4], LID)
    elif water:
        level = {1: 6.5, 2: 10.5}[water]
        m.box([1.5, level - 0.01, iz0], [14.5, level, iz1], None, faces={'up': {'r': '#water', 'sub': (0, 0, 13, 14.5)}},
              shade=False)
        m.extra_tex['water'] = WATER
    return m


for _p in ('head', 'foot'):
    for _w in (0, 1, 2):
        bathtub(_p, _w)
    bathtub(_p, 0, lid=True)
    for _w in (0, 1, 2):
        child(f'bathtub_{_p}_lid_{_w}', f'bathtub_{_p}_lid')


# ================================================================================================ bath tap, shower
A_TAP = Atlas('bath_tap')


def bath_tap(running):
    """Wall-mounted two-handle mixer at the bottom of its block (place it in the block above the tub, on the wall);
    the spout reaches north over the tub, running water falls into the block below."""
    name = 'bath_tap' + ('_running' if running else '')
    m = Model(name, A_TAP, render_type='translucent' if running else None, display=DISP_WALL)
    m.box([4, 1.5, 15], [12, 3.5, 16], CHROME)                                     # wall plate / mixer body
    m.box([4.5, 2, 13.5], [11.5, 3, 15], CHROME)
    for (hx, cap) in ((3.0, '#c8281c'), (11.5, '#2a5ac8')):
        m.box([hx, 1.5, 13.6], [hx + 1.5, 3.5, 14.9], CHROME)                      # handle stems
        m.box([hx - 0.3, 3.5, 13.3], [hx + 1.8, 4.3, 15.2], solid('tap_cross', '#d8dcde'))
        m.box([hx + 0.4, 4.3, 14.0], [hx + 1.1, 4.6, 14.6], solid('tap_cap' + cap[1:], cap))
    m.box([7.4, 1.6, 6.5], [8.6, 2.6, 13.5], CHROME)                               # the long spout
    m.box([7.5, 0.6, 6.5], [8.5, 1.6, 7.5], CHROME)
    if running:
        stream(m, 8.0, 7.0, 0.6, -6.0)
    return m


bath_tap(False)
bath_tap(True)

A_SHOW = Atlas('shower')


def shower():
    """Hand shower resting in its wall hook, the metal hose looping down to the mixer below."""
    m = Model('shower', A_SHOW, display=DISP_WALL)
    HOSE = M.brushed('shower_hose', ramp(['#5a5e60', '#83878a', '#adb1b2', '#d4d6d7'], 4), base=2.4)
    HEAD = M.plastic('shower_head', ramp(['#9a9890', '#bab8b0', '#d4d2ca', '#e8e6de'], 4), base=2.6)
    m.box([7, 9, 15], [9, 10.5, 16], CHROME)                                       # the hook bracket
    m.box([7.2, 9.5, 13.8], [8.8, 10.2, 15], CHROME)
    m.box([7.0, 8.5, 12.5], [9.0, 14.5, 13.8], HEAD)                               # head (handle down)
    m.box([6.5, 13.0, 11.5], [9.5, 15.5, 12.8], HEAD, faces={'north': M.custom('shower_face', _shower_face)})
    # hose: down from the handle, a loop, into the floor of the block (to the mixer)
    for (x0, y0, z0, x1, y1, z1) in ((7.6, 2, 13.2, 8.4, 8.5, 14.0), (8.4, 2, 13.2, 11.0, 2.8, 14.0),
                                     (10.2, -4, 13.4, 11.0, 2.0, 14.2)):
        m.box([x0, y0, z0], [x1, y1, z1], HOSE)
    return m


def _shower_face(w, h, face):
    a = blank(w, h, '#c8c6be')
    x, y = grid(w, h)
    holes = ((x + y) % 2 == 0) & (x > 0) & (x < w - 1) & (y > 0) & (y < h - 1)
    a[holes] = C('#6a6a66')
    return a


shower()

# ================================================================================================ stool, bucket
A_STOOL = Atlas('bath_stool')
PINKPL = ramp(['#8a5a62', '#a46e76', '#bc848a', '#d09a9e', '#e0b0b2', '#ecc4c4'], 6)


def bath_stool():
    m = Model('bath_stool', A_STOOL, display=DISP_SMALL)
    P = painted('stool_pl', PINKPL, base=3.4, amp=0.2)
    m.box([4, 5, 4], [12, 6.2, 11], P, faces={'up': M.custom('stool_top', _stool_top)})
    for (lx, lz) in ((4.3, 4.3), (10.7, 4.3), (4.3, 9.7), (10.7, 9.7)):
        m.box([lx, 0, lz], [lx + 1.0, 5, lz + 1.0], P)
    m.box([4.6, 2.5, 4.6], [11.4, 3.2, 10.4], P, skip=('up', 'down'))             # the brace ring
    return m


def _stool_top(w, h, face):
    a = from_idx(3.6 + noise(w, h, 7301, beta=1.6, wrap=False) * 0.2, PINKPL)
    rect(a, w // 2 - 1, h // 2 - 2, w // 2, h // 2 + 1, PINKPL[1])                # the grip slot
    bevel(a, 0.05, -0.05)
    tint(a, '#8a8070', 0.1)
    return a


bath_stool()

A_BUCK = Atlas('bucket')
YELLOWPL = ramp(['#8a6a1a', '#a88222', '#c49a2c', '#d8b23a', '#e6c656', '#f0d878'], 6)


def bucket():
    """The yellow plastic bath basin (湯桶)."""
    m = Model('bucket', A_BUCK, display=DISP_SMALL)
    P = painted('buck_pl', YELLOWPL, base=3.4, amp=0.2)
    IN = painted('buck_in', YELLOWPL, base=2.2, amp=0.2)
    disc_strips(m, 8, 8, 3.6, 0, 3.6, P, bottom=P, top_spec=IN, step=1.2)
    disc_strips(m, 8, 8, 4.0, 3.2, 3.8, P, step=1.6)
    return m


bucket()


# ================================================================================================ washing machine (二槽式)
A_WASH = Atlas('washing_machine')
WASHBODY = ramp(['#8a9a9c', '#a2b2b4', '#b8c8ca', '#cad8d8', '#d8e4e2', '#e4ecea'], 6)


def _swirl_frame(i):
    w = h = 16
    x, y = grid(w, h)
    t = i / 8 * 2 * math.pi
    ang = np.arctan2(y + 0.5 - 8, x + 0.5 - 8)
    d = np.hypot(x + 0.5 - 8, y + 0.5 - 8)
    v = np.sin(ang * 3 + d * 0.8 - t)
    a = blank(w, h, C('#4a6a7a', 255))
    a[v > 0.3] = C('#7a9aa8')
    a[v > 0.8] = C('#c8dce4')
    return a


register_anim('block/washing_swirl', (16, 16), 8, _swirl_frame, frametime=2)


def _wash_panel(running):
    def fn(w, h):
        """Back control panel (13 x 4): two timer dials and the selector, a little label strip."""
        a = from_idx(3.6 + noise(w, h, 7401, beta=1.6, wrap=False) * 0.2, CREAMPL)
        bevel(a, 0.05, -0.06)
        for cx in (2, 9):
            rect(a, cx, 1, cx + 2, 2, '#e8e4d8')
            px(a, cx + 1, 1, '#2a2a2a' if not running else '#c8281c')
            px(a, cx, 2, '#9a968a')
        hline(a, 5, 7, 2, '#3a6aa8')
        return a
    return fn


def washing_machine(running):
    name = 'washing_machine' + ('_running' if running else '')
    key = 'panel_on' if running else 'panel'
    A_WASH.add(key, 13, 4, _wash_panel(running))
    m = Model(name, A_WASH, display=DISP_FRONT)
    BODY = painted('wash_body', WASHBODY, base=3.6, amp=0.2, wear_amt=0.04)
    LID = painted('wash_lid', CREAMPL, base=4.2, amp=0.2)
    m.box([1.5, 0, 4], [14.5, 12.5, 15], BODY, faces={'north': M.custom('wash_front', _wash_front)})
    m.box([1.5, 12.5, 13], [14.5, 16.5, 15], BODY, faces={'north': key})          # raised panel at the back
    # the washing tub lid (left, as seen from the front = model east) and the spinner lid
    m.box([7.5, 12.5, 4.5], [14.0, 13.2, 12.5], LID)
    m.box([2.0, 12.5, 4.5], [7.0, 13.2, 12.5], LID)
    if running:
        m.extra_tex['swirl'] = ref('block/washing_swirl')
        m.box([8.5, 13.21, 5.5], [13.0, 13.25, 11.5], None, faces={'up': {'r': '#swirl', 'sub': (0, 0, 16, 16)}})
    else:
        m.box([8.5, 13.21, 5.5], [13.0, 13.25, 11.5], None, faces={'up': M.custom('wash_win', _wash_win)})
    m.box([2.0, 0, 4.0], [14.0, 1.0, 14.5], solid('wash_base', '#3a3c3c'))
    # the drain hose coming out at the side
    m.box([14.5, 0.5, 12], [15.5, 1.5, 13], solid('wash_hose', '#c8ccc8'))
    m.box([14.5, 0, 6], [15.5, 0.6, 13], solid('wash_hose', '#c8ccc8'))
    return m


def _wash_front(w, h, face):
    a = from_idx(3.6 + noise(w, h, 7411, beta=1.6, wrap=False) * 0.2, WASHBODY)
    bevel(a, 0.05, -0.06)
    if w >= 10:
        # a faded badge, a rust bloom at the foot, a sticker with the instructions
        hline(a, 2, 5, 2, '#5a7aa8')
        rect(a, w - 5, 3, w - 2, 6, '#e8e4d8')
        for yy in (4, 5):
            hline(a, w - 5, w - 3, yy, '#8a8678')
        tint(a[h - 2:], '#6a3a1a', 0.3)
    return a


def _wash_win(w, h, face):
    a = blank(w, h, '#2a3a40')
    dl(a, 0.08, grid(w, h)[1] == 0)
    return a


washing_machine(False)
washing_machine(True)


# ================================================================================================ washbasin, mirror
A_BASIN = Atlas('washbasin')
CERAMIC = ramp(['#8e8e86', '#a8a8a0', '#c0c0b8', '#d4d4cc', '#e2e2da', '#ecece6', '#f4f4ee'], 7)


def _basin_top(w, h, face):
    a = m_plastic(w, h, CERAMIC, 7501, base=4.8)
    if w >= 10 and h >= 7:
        cx, cy = w / 2, h / 2 + 0.5
        inner = disc_mask(w, h, cx, cy, w * 0.36, h * 0.34)
        a[inner] = C('#c8c8c0')
        x, y = grid(w, h)
        dl(a, -0.07, inner & (y < cy))
        px(a, int(cx), int(cy), '#3a3a38')
        tint(a, '#9a7a4a', 0.3, inner & (np.abs(x - cx) < 0.8) & (y < cy) & (y > cy - 3))     # rust drip from the tap
    return a


def _vanity_front(w, h, face):
    a = from_idx(4.0 + noise(w, h, 7511, beta=1.6, wrap=False) * 0.2, CREAMPL)
    bevel(a, 0.05, -0.06)
    if w >= 10 and h >= 8:
        vline(a, w // 2, 1, h - 2, CREAMPL[1])
        px(a, w // 2 - 1, 3, '#8a8a84')
        px(a, w // 2 + 1, 3, '#8a8a84')
        tint(a[h - 2:], '#6a5a40', 0.25)
    return a


def washbasin(running):
    name = 'washbasin' + ('_running' if running else '')
    m = Model(name, A_BASIN, render_type='translucent' if running else None, display=DISP_FRONT)
    m.box([2, 0, 6], [14, 11, 15.5], painted('vanity', CREAMPL, base=3.8), faces={'north': M.custom('vanity_front',
                                                                                                     _vanity_front)})
    m.box([1.5, 11, 5], [14.5, 13, 16], M.plastic('basin_cer', CERAMIC, base=4.6),
          faces={'up': M.custom('basin_top', _basin_top)})
    # tap (single spout, two little handles) at the back
    m.box([7.3, 13, 13.5], [8.7, 15.5, 15], CHROME)
    m.box([7.5, 14.5, 10.5], [8.5, 15.3, 13.5], CHROME)
    for hx in (5.0, 10.0):
        m.box([hx, 13, 13.6], [hx + 1, 14.2, 14.6], solid('basin_handle', '#d8dcde'))
    # a plastic cup with one toothbrush, a bar of soap
    m.box([11.6, 13, 12.0], [13.2, 15.2, 13.6], solid('cup_pl', '#7aa8c8'))
    m.box([12.2, 15.2, 12.6], [12.6, 17.2, 13.0], solid('brush', '#e8e4dc'))
    m.box([3.0, 13, 12.5], [5.0, 13.6, 13.8], solid('soap', '#e8d8a8'))
    if running:
        stream(m, 8.0, 11.0, 14.5, 11.6)
    return m


washbasin(False)
washbasin(True)

A_MIR = Atlas('mirror_small')


def _mirror_glass(w, h):
    a = blank(w, h, '#8a9aa2')
    x, y = grid(w, h)
    g = y / h
    a[..., :3] = a[..., :3] * (1 - g[..., None] * 0.3) + np.array([40, 50, 56]) * g[..., None] * 0.3
    st = (np.abs((x * 0.9 + y) % 9 - 4) < 0.6)
    dl(a, 0.08, st)
    # toothpaste flecks and a darkened silvering at the bottom corners (desilvering creeping in)
    specks(a, 7601, 0.04, '#d8dcd8', 0.8)
    for (cx, cy) in ((0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1)):
        px(a, cx, cy, '#2a2e2c')
    return a


def mirror_small():
    A_MIR.add('glass', 8, 10, _mirror_glass)
    m = Model('mirror_small', A_MIR, display=DISP_WALL)
    m.box([3.5, 2.5, 15.2], [12.5, 13.5, 16], solid('mir_frame', '#c8c4b8'), faces={'north': None})
    m.box([4, 3, 15.0], [12, 13, 15.2], None, faces={'north': 'glass'})
    m.box([3.5, 2.5, 15.0], [12.5, 3, 15.2], solid('mir_frame', '#c8c4b8'))
    m.box([3.5, 13, 15.0], [12.5, 13.5, 15.2], solid('mir_frame', '#c8c4b8'))
    m.box([3.5, 3, 15.0], [4, 13, 15.2], solid('mir_frame', '#c8c4b8'))
    m.box([12, 3, 15.0], [12.5, 13, 15.2], solid('mir_frame', '#c8c4b8'))
    return m


mirror_small()


# ================================================================================================ toilet
A_TOI = Atlas('toilet_seat')
TOICER = ramp(['#8a8478', '#a29c90', '#b8b2a6', '#ccc6ba', '#dcd6ca', '#e8e2d6'], 6)   # old ivory ceramic
FLUFF = ramp(['#7a5a62', '#906e76', '#a6848a', '#ba9a9e', '#ccaeb0'], 5)              # faded pink pile


def _fluffy(w, h, face):
    r = rng(7701 + w * 3 + h)
    a = from_idx(2.6 + noise(w, h, 7702 + w, beta=0.6, wrap=False) * 0.5, FLUFF)
    dl(a, 0.04, r.random((h, w)) < 0.2)
    return a


def toilet_seat():
    """The old western toilet in ivory ceramic: a fluffy pink seat and lid cover, a matching mat, the low tank with
    its lever and a spare roll on top."""
    m = Model('toilet_seat', A_TOI, display=DISP_FRONT)
    CER = M.plastic('toi_cer', TOICER, base=4.0)
    FL = mat('toi_fluff', _fluffy)
    m.box([1.5, 0, 0.5], [14.5, 0.4, 12.5], FL)                               # the mat
    m.box([5.5, 0.4, 4.5], [10.5, 5, 12], CER)                                # pedestal
    m.box([4, 5, 2], [12, 7.5, 12.5], CER)                                    # bowl
    m.box([3.8, 7.5, 1.8], [12.2, 8.3, 12.0], FL)                             # covered seat
    m.box([4.0, 8.3, 2.0], [12.0, 9.2, 11.5], FL)                             # covered lid (closed)
    m.box([4, 7, 12.5], [12, 15, 16], CER)                                    # tank
    m.box([3.8, 15, 12.3], [12.2, 15.7, 16], CER)
    m.box([4.4, 12.5, 12.2], [6.2, 13.1, 12.5], CHROME)                       # flush lever
    m.box([8.0, 15.7, 13.0], [11.0, 18.2, 15.6], solid('toi_roll', '#f0ece2'))   # spare roll standing on the tank
    m.box([9.0, 15.7, 13.9], [10.0, 18.25, 14.7], solid('toi_roll_core', '#a08a6a'))
    return m


toilet_seat()
