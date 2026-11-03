"""The tatami room (和室) of 203 and its fixtures: the ちゃぶ台, 座布団, the CRT TV (animated static / dark room / face
channels) on its low stand, the hanging lamp with its pull cord, the ring light, pull switch and wall switches, the
curtains, the futons and box of the 押し入れ, the empty altar shelf, and the sliding doors (fusuma, oshiire_fusuma).
Models face north (front = -z); wall-mounted things have the wall on their south side."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, fnoise, texture, REGISTRY
from hk_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL, DISP_TALL
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_wood, m_plastic, m_fabric, m_brushed, text35, stamp_mask, bitmap)
import hk_mats as M
from hk_common import (TEAK, LACQUER, KEYAKI, PALEWOOD, DARKWOOD, CREAMPL, GREYPL, CHARCOAL, STEELW, PAPER,
                       FUSUMA_PAPER, solid, lit, mat, painted, woodmat, disc_strips, cord, register_anim, ref)


# ================================================================================================ ちゃぶ台
A_CHAB = Atlas('chabudai')


CHAB = ramp(['#20100a', '#2e170f', '#3e1f14', '#4f281a', '#603220', '#723d27', '#84492f', '#965637'], 8)


def _chab_top(w, h):
    """The round lacquered top seen from above: reddish keyaki grain under worn lacquer, a sheen across it, a lit rim,
    pale cup rings and scratches from years of meals."""
    r = rng(5101)
    x, y = grid(w, h)
    cx, cy = w / 2, h / 2
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy)
    n = noise(w, h, 5102, beta=0.8, sx=8, sy=1, wrap=False)
    t = y + 1.6 * np.sin(x * 0.5 + 0.8) + noise(w, h, 5103, beta=1.8, wrap=False) * 1.4
    lines = np.abs(np.sin(t * 0.9)) < 0.2
    a = from_idx(3.8 + n * 0.45 - lines * 1.1, CHAB)
    # lacquer sheen: a diagonal soft highlight band
    band = np.exp(-(((x - y) + 3.0) / 3.0) ** 2)
    dl(a, 0.0)
    a[..., :3] = a[..., :3] * (1 + band[..., None] * 0.28)
    # the rim: lit top-left half, dark bottom-right half
    rim = (d > cx - 1.1) & (d <= cx + 0.2)
    dl(a, 0.06, rim & ((x + y) < w))
    dl(a, -0.08, rim & ((x + y) >= w))
    for (rx, ry, rr) in ((4.5, 5.0, 1.8), (9.5, 9.5, 1.5)):
        ring = (np.abs(np.hypot(x + 0.5 - rx, y + 0.5 - ry) - rr) < 0.45)
        tint(a, '#c09068', 0.3, ring)
    for _ in range(4):
        sx, sy = int(r.integers(3, w - 4)), int(r.integers(3, h - 4))
        for k in range(3):
            px(a, sx + k, sy + (k // 2), '#a0603c', 0.5)
    return a


def chabudai():
    r = 7.0
    A_CHAB.add('top', 14, 14, _chab_top)
    EDGE = woodmat('chab_edge', CHAB, base=2.0, amp=0.3)
    UNDER = woodmat('chab_under', PALEWOOD, base=2.0, amp=0.4)
    LEG = woodmat('chab_leg', CHAB, base=1.6, amp=0.3)
    m = Model('chabudai', A_CHAB, display=disp(gui=(30, 225, 0), gui_scale=0.8, gui_t=(0, 3, 0)))
    disc_strips(m, 8, 8, r, 4.0, 5.0, EDGE, top_key='top', bottom=UNDER, step=2.0)
    # apron ring under the top (a smaller octagon of boards) and the four folding legs with their brackets
    disc_strips(m, 8, 8, 5.5, 3.2, 4.0, EDGE, top_key=None, bottom=UNDER, step=11.0 / 4)
    for (lx, lz) in ((4.0, 4.0), (11.0, 4.0), (4.0, 11.0), (11.0, 11.0)):
        m.box([lx, 0, lz], [lx + 1, 3.2, lz + 1], LEG)
    return m


chabudai()


# ================================================================================================ 座布団
A_ZAB = Atlas('zabuton')
ZAB = ramp(['#2a0f10', '#3c1617', '#4f1e1e', '#622826', '#73322e', '#843e38'], 6)     # faded えんじ


def _zab_cloth(w, h, face):
    r = rng(5201 + w * 7 + h)
    x, y = grid(w, h)
    n = noise(w, h, 5202 + w, beta=1.4, wrap=False)
    weave = ((x + y) % 2) * 0.3
    a = from_idx(3.0 + n * 0.4 + weave, ZAB)
    if face == 'h' and w >= 9:
        # woven stripe pattern, faded in the middle where people sat, the tuft threads (綴じ糸) at the centre
        dl(a, -0.04, (y % 4) == 0)
        sat = disc_mask(w, h, w / 2, h / 2, w * 0.36, h * 0.36)
        tint(a, '#8a5a50', 0.18, sat)
        cxi, cyi = w // 2, h // 2
        for (dx, dy) in ((0, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            px(a, cxi + dx, cyi + dy, '#d8ccb0')
        px(a, cxi, cyi, '#f0e6cc')
    else:
        dl(a, 0.04, y == 0)
    return a


def zabuton():
    CL = mat('zab_cloth', _zab_cloth)
    m = Model('zabuton', A_ZAB, display=DISP_FLOOR)
    m.box([2.5, 0, 2.5], [13.5, 1.5, 13.5], CL)
    m.box([3.5, 1.5, 3.5], [12.5, 2.0, 12.5], CL)
    # the four corner tassels (房)
    for (tx, tz) in ((2.0, 2.0), (13.0, 2.0), (2.0, 13.0), (13.0, 13.0)):
        m.box([tx, 0.5, tz], [tx + 1, 1.2, tz + 1], solid('zab_tassel', '#6a2a22'))
    return m


zabuton()


# ================================================================================================ the CRT TV
SCREEN_FRAMES = {'static': 16, 'dark_room': 12, 'face': 16}


def _crt_mask(w, h):
    """Rounded CRT corners + edge darkening factor."""
    x, y = grid(w, h)
    ex = np.minimum(x, w - 1 - x)
    ey = np.minimum(y, h - 1 - y)
    corner = (ex + ey) < 1.5
    vig = np.clip(np.minimum(ex, ey) / 2.5, 0, 1)
    return corner, vig


def _static_frame(i):
    """砂嵐: 16x12 screen at 2x density in a 16x16 frame. Grainy white noise, rolling bright and dark bands,
    a faint blue cast; a fresh pattern every frame."""
    r = rng(5301 + i * 31)
    w, h = 16, 12
    a = blank(16, 16, '#000000')
    x, y = grid(w, h)
    nz = r.random((h, w))
    roll = 0.5 + 0.5 * np.sin((y + i * 2.3) * 0.7)
    roll2 = (np.abs(((y + i * 5) % 12) - 6) < 1).astype(float)
    v = np.clip(nz * 0.85 + roll * 0.18 + roll2 * 0.2 - 0.12, 0, 1)
    v = np.round(v * 5) / 5
    corner, vig = _crt_mask(w, h)
    lum = (40 + v * 205) * (0.55 + 0.45 * vig)
    img = np.stack([lum * 0.92, lum * 0.96, lum * 1.05], -1)
    img[corner] = (14, 16, 18)
    a[:h, :w, :3] = np.clip(img, 0, 255)
    a[:h, :w, 3] = 255
    a[h:, :, :3] = a[:4, :, :3]
    a[h:, :, 3] = 255
    return a


def _dark_room_frame(i):
    """Channel 1: a grainy, cold image of a dark tatami room seen from up in a corner: the closet's pale fusuma, the
    low table, the bright rectangle of a TV - your room. A figure stands in the doorway in some frames."""
    r = rng(5401 + i * 17)
    w, h = 16, 12
    a = blank(16, 16, '#000000')
    x, y = grid(w, h)
    img = np.zeros((h, w, 3)) + np.array([18, 24, 26])
    # floor (tatami lines in perspective) in the lower half
    floor = y >= 5
    img[floor] = (34, 38, 30)
    for k in range(3):
        xx = 3 + k * 5 + (y - 5) * (k - 1) * 0.5
        img[floor & (np.abs(x - xx) < 0.6)] = (20, 22, 18)
    # back wall with the closet fusuma (two pale panels) on the left
    img[(y < 5) & (x < 7)] = (52, 56, 52)
    img[(y < 5) & (x == 3)] = (26, 28, 26)
    img[(y < 5) & (x >= 7)] = (28, 32, 34)
    # the low table: a dark ellipse
    tab = ((x + 0.5 - 9.0) / 3.0) ** 2 + ((y + 0.5 - 8.0) / 1.4) ** 2 < 1
    img[tab] = (10, 10, 10)
    # the TV's own glow in the right corner (a bright square = the screen you are looking at)
    img[(x >= 13) & (x <= 14) & (y >= 3) & (y <= 4)] = (150, 160, 170)
    # a figure in the doorway on frames 5..8: a thin pale-faced silhouette
    if 5 <= i <= 8:
        img[(x == 6) & (y >= 2) & (y <= 7)] = (8, 8, 8)
        img[(x == 6) & (y == 2)] = (90, 90, 86)
    nz = r.random((h, w))
    img += (nz[..., None] - 0.5) * 34
    # a slow rolling bar
    bar = np.abs(((y + i) % 12) - 3) < 1
    img[bar] *= 1.25
    corner, vig = _crt_mask(w, h)
    img *= (0.5 + 0.5 * vig)[..., None]
    img[corner] = (8, 9, 10)
    a[:h, :w, :3] = np.clip(img, 0, 255)
    a[:h, :w, 3] = 255
    a[h:, :, :3] = a[:4, :, :3]
    a[h:, :, 3] = 255
    return a


def _face_frame(i):
    """Channel 2: a pale face pressing out of the static: black eye hollows, an open mouth; it fades in, holds,
    breaks up into noise and comes back closer."""
    r = rng(5501 + i * 13)
    w, h = 16, 12
    a = blank(16, 16, '#000000')
    x, y = grid(w, h)
    nz = r.random((h, w))
    base = 30 + nz * 120
    strength = [0.15, 0.35, 0.6, 0.85, 1.0, 1.0, 0.9, 0.3, 0.05, 0.2, 0.55, 0.9, 1.0, 1.0, 0.7, 0.25][i % 16]
    scale = 1.0 if i < 8 else 1.25
    fx, fy = 8.0, 6.2
    face = ((x + 0.5 - fx) / (3.4 * scale)) ** 2 + ((y + 0.5 - fy) / (4.6 * scale)) ** 2 < 1
    eyes = (((x + 0.5 - (fx - 1.4 * scale)) / (0.9 * scale)) ** 2 + ((y + 0.5 - (fy - 0.8 * scale)) / (0.8 * scale)) ** 2 < 1) | \
           (((x + 0.5 - (fx + 1.4 * scale)) / (0.9 * scale)) ** 2 + ((y + 0.5 - (fy - 0.8 * scale)) / (0.8 * scale)) ** 2 < 1)
    mouth = ((x + 0.5 - fx) / (0.9 * scale)) ** 2 + ((y + 0.5 - (fy + 2.2 * scale)) / (1.1 * scale)) ** 2 < 1
    hair = (((x + 0.5 - fx) / (4.4 * scale)) ** 2 + ((y + 0.5 - (fy - 0.6)) / (5.6 * scale)) ** 2 < 1) & ~face
    img = base.copy()
    img = np.where(face, img * (1 - strength) + 200 * strength, img)
    img = np.where(hair, img * (1 - strength) + 12 * strength, img)
    img = np.where(eyes | mouth, img * (1 - strength) + 6 * strength, img)
    corner, vig = _crt_mask(w, h)
    img = img * (0.6 + 0.4 * vig)
    col = np.stack([img * 0.95, img * 0.98, img * 1.04], -1)
    col[corner] = (10, 10, 12)
    a[:h, :w, :3] = np.clip(col, 0, 255)
    a[:h, :w, 3] = 255
    a[h:, :, :3] = a[:4, :, :3]
    a[h:, :, 3] = 255
    return a


register_anim('block/crt_tv_static', (16, 16), SCREEN_FRAMES['static'], _static_frame, frametime=1)
register_anim('block/crt_tv_dark_room', (16, 16), SCREEN_FRAMES['dark_room'], _dark_room_frame, frametime=3)
register_anim('block/crt_tv_face', (16, 16), SCREEN_FRAMES['face'], _face_frame, frametime=2)
SCREENS = {0: 'block/crt_tv_static', 1: 'block/crt_tv_dark_room', 2: 'block/crt_tv_face'}

A_TV = Atlas('crt_tv')


def _tv_front(w, h):
    """Silver-grey front bezel (12 x 10): the screen hole is covered by the screen element; the right column holds
    the speaker grille, the two rotary knobs and the power button with its tiny red standby lamp."""
    a = from_idx(4.0 + noise(w, h, 5601, beta=1.6, wrap=False) * 0.25, GREYPL)
    bevel(a, 0.06, -0.08, 0.03, -0.04)
    # recessed screen surround (texture cols 0..9, rows 1..8; the screen element covers cols 1..8, rows 2..7)
    frame(a, 0, 1, 9, 8, GREYPL[1])
    frame(a, 0, 0, w - 1, h - 1, GREYPL[3])
    # control column
    for yy in range(2, 5):
        hline(a, 10, 11, yy, GREYPL[1] if yy % 2 == 0 else GREYPL[3])
    px(a, 10, 6, '#1a1a1c')
    px(a, 11, 6, '#6a6c70')
    px(a, 10, 7, '#1a1a1c')
    px(a, 11, 7, '#6a6c70')
    px(a, 10, 9, '#3a1010')
    # brand-less badge strip under the screen
    hline(a, 3, 6, 9, GREYPL[5])
    return a


def _tv_side(w, h, face):
    a = from_idx(2.2 + noise(w, h, 5602 + w, beta=1.6, wrap=False) * 0.3, CHARCOAL)
    bevel(a, 0.05, -0.06)
    if face != 'h' and w >= 6:
        # ventilation slots near the back
        for xx in range(w - 5, w - 1, 2):
            vline(a, xx, 2, h - 3, CHARCOAL[0])
    return a


def _screen_off(w, h):
    """Dark grey-green glass with a dull reflection of the room: a pale window shape and a curved highlight."""
    a = blank(w, h, '#1c2422')
    x, y = grid(w, h)
    corner, vig = _crt_mask(w, h)
    a[..., :3] = a[..., :3] * (0.7 + 0.3 * vig[..., None])
    hl = (np.abs((x - 1.5) ** 2 / 30 + (y - 1.2) ** 2 / 12 - 0.35) < 0.07)
    a[hl] = C('#4a5652')
    a[2:4, 11:13] = C('#2e3a38')
    a[corner] = C('#121414')
    return a


def crt_tv(on, channel=0):
    name = 'crt_tv' if not on else f'crt_tv_on_{channel}'
    A_TV.add('front', 12, 10, _tv_front)
    A_TV.add('screen_off', 16, 12, _screen_off)
    m = Model(name, A_TV, display=disp(gui=(15, 200, 0), gui_scale=0.75, gui_t=(0, -1.5, 0)))
    SIDE = mat('tv_side', _tv_side)
    # sits on the tv_stand (its top is 6 px up in the block below): the model starts at y -10
    y0 = -10.0
    # front box (bezel + body) and the tapering back for the tube
    m.box([2, y0, 3], [14, y0 + 10, 9], SIDE, faces={'north': 'front'})
    m.box([3.5, y0 + 1, 9], [12.5, y0 + 9, 12], SIDE)
    m.box([5, y0 + 2.5, 12], [11, y0 + 7.5, 14], SIDE)
    # the screen (8 x 6 px), slightly proud of the bezel; on: emissive animated texture at 2x density
    if on:
        m.extra_tex['screen'] = ref(SCREENS[channel])
        m.box([5, y0 + 2, 2.6], [13, y0 + 8, 3], None, faces={'north': {'r': '#screen', 'sub': (0, 0, 16, 12)}},
              glow=('north',))
    else:
        m.box([5, y0 + 2, 2.6], [13, y0 + 8, 3], None, faces={'north': {'r': 'screen_off', 'sub': (0, 0, 16, 12)}})
    # knobs stick out a little (the control column is on the viewer's right = model west)
    for ky in (y0 + 6.2, y0 + 3.6):
        m.box([2.6, ky, 2.4], [3.8, ky + 1.2, 3], solid('tv_knob', '#1a1a1c'))
    # rabbit-ear antenna: a base on top and two rods tilted 22.5 degrees outwards
    m.box([6.5, y0 + 10, 7], [9.5, y0 + 10.6, 9], solid('tv_ant_base', '#2a2a2e'))
    ROD = solid('tv_rod', '#b8bcc0')
    m.box([7.3, y0 + 10.6, 7.8], [7.7, y0 + 18.5, 8.2], ROD, rot=('z', 22.5, [7.5, y0 + 10.6, 8]))
    m.box([8.3, y0 + 10.6, 7.8], [8.7, y0 + 18.5, 8.2], ROD, rot=('z', -22.5, [8.5, y0 + 10.6, 8]))
    return m


crt_tv(False)
for _ch in (0, 1, 2):
    crt_tv(True, _ch)
# aliases (if the blockstate names the channel without "on", or "on" without the channel)
child('crt_tv_on', 'crt_tv_on_0')
for _ch in (0, 1, 2):
    child(f'crt_tv_{_ch}', f'crt_tv_on_{_ch}')


# ================================================================================================ TV stand
A_TVS = Atlas('tv_stand')


def _tvs_front(w, h):
    """Low teak-veneer cabinet front (14 x 5): an open shelf with a VCR on the left, a sliding glass door on the
    right with a few tapes behind it."""
    a = m_wood(w, h, TEAK, 5701, along='x', base=3.6, amp=0.4)
    bevel(a, 0.05, -0.07)
    # open shelf with a VCR (black, a dim green clock window, a tape slot)
    rect(a, 1, 1, 7, 3, '#0e0c0a')
    rect(a, 1, 2, 7, 3, '#1c1c1e')
    hline(a, 2, 4, 2, '#0a0a0a')
    px(a, 6, 2, '#2a4a2a')
    # glass door: dark glass with tape spines behind and a highlight
    rect(a, 8, 1, 12, 3, '#1a1612')
    for xx, c in zip(range(8, 12), ('#5a3a2a', '#2a3a5a', '#d8d0b8', '#5a2a2a')):
        vline(a, xx, 2, 3, c)
    px(a, 12, 1, '#4a4a48')
    vline(a, 8, 1, 3, '#3a3430')
    return a


def tv_stand():
    A_TVS.add('front', 14, 5, _tvs_front)
    BODY = woodmat('tvs_body', TEAK, base=3.4, amp=0.4)
    m = Model('tv_stand', A_TVS, display=DISP_FRONT)
    m.box([1, 1, 2], [15, 6, 14], BODY, faces={'north': 'front'})
    for (lx, lz) in ((1.5, 2.5), (13.5, 2.5), (1.5, 12.5), (13.5, 12.5)):
        m.box([lx, 0, lz], [lx + 1, 1, lz + 1], solid('tvs_foot', '#1a1008'))
    return m


tv_stand()


# ================================================================================================ hanging lamp (和風ペンダント)
A_LAMP = Atlas('hanging_lamp')


def _shade_panel(lit_):
    def fn(w, h, face):
        """Paper-look acrylic panel between dark wooden rims, two thin kumiko bars; lit = glowing warm."""
        x, y = grid(w, h)
        if face == 'h':
            return blank(w, h, '#efe2c4' if lit_ else '#cfc6b0')
        a = blank(w, h, '#f6e8c8' if lit_ else '#d8cfb8')
        fib = noise(w, h, 5801 + w, beta=0.4, wrap=False)
        a[..., :3] += fib[..., None] * (4 if lit_ else 3)
        if lit_:
            tint(a, '#ffd890', 0.12 * (y / max(h - 1, 1)))
        else:
            # dead insects collected at the bottom of the shade (dark specks seen through the paper)
            if w >= 6:
                px(a, 2, h - 2, '#6a6250')
                px(a, w - 3, h - 2, '#7a7260')
        for k in (w // 3, (2 * w) // 3):
            vline(a, k, 0, h - 1, '#b89a74' if lit_ else '#8a7458')
        hline(a, 0, w - 1, 0, '#2a1a10')
        hline(a, 0, w - 1, h - 1, '#2a1a10')
        return a
    return fn


def _lamp_under(lit_):
    def fn(w, h, face):
        """The open bottom of the shade: the circle fluorescent tube inside (glowing when lit)."""
        a = blank(w, h, '#3a2a1c' if not lit_ else '#e8dcc0')
        ring = ring_mask(w, h, w / 2, h / 2, w * 0.26, w * 0.4)
        a[ring] = C('#f8f4ea' if lit_ else '#bab6aa')
        a[disc_mask(w, h, w / 2, h / 2, w * 0.2)] = C('#6a6a62' if not lit_ else '#fff8e8')
        frame(a, 0, 0, w - 1, h - 1, '#2a1a10')
        return a
    return fn


def hanging_lamp(lit_, swing=False):
    name = 'hanging_lamp' + ('_lit' if lit_ else '') + ('_swing' if swing else '')
    m = Model(name, A_LAMP, render_type='cutout', ao=False,
              display=disp(gui=(20, 225, 0), gui_scale=0.55, gui_t=(0, -2, 0)))
    rot = ('z', 22.5, [8, 16, 8]) if swing else None
    PANEL = mat('lamp_panel' + ('_lit' if lit_ else ''), _shade_panel(lit_))
    UNDER = mat('lamp_under' + ('_lit' if lit_ else ''), _lamp_under(lit_))
    WOOD = woodmat('lamp_wood', DARKWOOD, base=3.0, amp=0.3)
    CORD = solid('lamp_cord', '#e8e4d8')
    g = ('north', 'south', 'east', 'west', 'down') if lit_ else ()
    # ceiling rose (never swings)
    m.box([6.5, 15.5, 6.5], [9.5, 16, 9.5], solid('lamp_rose', '#ddd8c8'))
    # the cord with its adjuster, the shade (10 x 5 x 10) with a wooden top cap
    m.box([7.8, 9.5, 7.8], [8.2, 15.5, 8.2], CORD, rot=rot)
    m.box([7.3, 12, 7.3], [8.7, 13.5, 8.7], solid('lamp_adjuster', '#c8c2b0'), rot=rot)
    m.box([6.5, 9, 6.5], [9.5, 9.6, 9.5], WOOD, rot=rot)
    m.box([3, 4, 3], [13, 9, 13], PANEL, faces={'down': UNDER, 'up': WOOD}, glow=g, rot=rot)
    # frame corner posts standing proud
    for (cx, cz) in ((3, 3), (12.4, 3), (3, 12.4), (12.4, 12.4)):
        m.box([cx - 0.2, 3.6, cz - 0.2], [cx + 0.8, 9.2, cz + 0.8], WOOD, rot=rot)
    # the pull cord (紐) from the tube down to the bottom of the block (the pull_switch block below continues it)
    m.box([7.9, 0, 7.9], [8.1, 4, 8.1], solid('lamp_pull', '#d8d0bc'), rot=rot)
    return m


for _l in (False, True):
    for _s in (False, True):
        hanging_lamp(_l, _s)


# ================================================================================================ ring light, pull switch, wall switch
A_RING = Atlas('fluorescent_ring')


def _ring_diffuser(lit_):
    def fn(w, h, face):
        x, y = grid(w, h)
        if face != 'h':
            a = blank(w, h, '#e4e0d4' if lit_ else '#bdb8aa')
            dl(a, 0.03, y == 0)
            return a
        a = blank(w, h, '#c8c4b6' if not lit_ else '#f2eee2')
        d = np.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2)
        ring = (d > w * 0.24) & (d < w * 0.38)
        a[ring] = C('#d6d2c4' if not lit_ else '#fffcf2')
        dl(a, -0.04 if not lit_ else 0.0, d > w * 0.46)
        # dead flies in the dish
        if not lit_:
            px(a, 3, 7, '#3a3830')
            px(a, 8, 4, '#4a4840')
        return a
    return fn


def fluorescent_ring(lit_):
    m = Model('fluorescent_ring' + ('_lit' if lit_ else ''), A_RING, ao=False,
              display=disp(gui=(-60, 0, 0), gui_scale=0.7))
    D = mat('ring_diff' + ('_lit' if lit_ else ''), _ring_diffuser(lit_))
    m.box([2.5, 15, 2.5], [13.5, 16, 13.5], solid('ring_plate', '#d8d4c8'))
    disc_strips(m, 8, 8, 6.0, 13.2, 15.0, D, top_spec=None, bottom=D, step=2.0,
                glow=('down', 'north', 'south', 'east', 'west') if lit_ else ())
    return m


fluorescent_ring(False)
fluorescent_ring(True)

A_PULL = Atlas('pull_switch')


def pull_switch():
    """The light's pull cord (紐スイッチ) in the block below the hanging lamp: the cord coming down from the lamp
    above and, at reach height, its little plastic ornament (a faded pink bear's head from the previous tenant)."""
    m = Model('pull_switch', A_PULL, display=disp(gui=(20, 225, 0), gui_scale=0.6, gui_t=(0, -1, 0)))
    m.box([7.9, 3.4, 7.9], [8.1, 16, 8.1], solid('pull_cord', '#d8d0bc'))
    m.box([7.6, 3.0, 7.6], [8.4, 3.4, 8.4], solid('pull_knot', '#a89a80'))
    m.box([7.2, 1.2, 7.2], [8.8, 3.0, 8.8], solid('pull_orn', '#c88a8a'))
    m.box([7.1, 2.6, 7.6], [7.5, 3.2, 8.4], solid('pull_orn', '#c88a8a'))
    m.box([8.5, 2.6, 7.6], [8.9, 3.2, 8.4], solid('pull_orn', '#c88a8a'))
    m.box([7.5, 1.9, 7.15], [7.8, 2.2, 7.2], solid('pull_eye', '#2a2020'), only=('north',))
    m.box([8.2, 1.9, 7.15], [8.5, 2.2, 7.2], solid('pull_eye', '#2a2020'), only=('north',))
    return m


pull_switch()

A_SW = Atlas('light_switch')


def _switch_face(on):
    def fn(w, h):
        """Wide-rocker double switch plate (4 x 6), cream; the rockers' pressed halves shaded. Off = the ほたる
        locator lamp glows green so you can find it in the dark."""
        a = blank(w, h, '#dcd4bc')
        frame(a, 0, 0, w - 1, h - 1, '#b8b098')
        for x0 in (1, 2):
            if on:
                px(a, x0, 1, '#f0ead6')
                vline(a, x0, 2, 4, '#c8c0a8')
            else:
                vline(a, x0, 1, 3, '#c8c0a8')
                px(a, x0, 4, '#f0ead6')
        if not on:
            px(a, 1, 2, '#7aff9a')
            px(a, 2, 2, '#7aff9a')
        return a
    return fn


def light_switch(on):
    name = 'light_switch' + ('_on' if on else '')
    key = 'face_on' if on else 'face'
    A_SW.add(key, 4, 6, _switch_face(on))
    m = Model(name, A_SW, display=DISP_WALL)
    m.box([6, 3, 15.4], [10, 9, 16], solid('sw_plate', '#c8c0a8'), faces={'north': key},
          glow=('north',) if not on else ())
    return m


light_switch(False)
light_switch(True)


# ================================================================================================ curtain
A_CUR = Atlas('curtain')
CURT = ramp(['#3a1c0c', '#562a12', '#6e3a1a', '#844a24', '#97582e', '#a86838'], 6)


def _curtain_cloth(w, h, face):
    """Faded rust-orange Showa curtain print: round flowers in a lattice, sun-bleached, the pleats shading."""
    x, y = grid(w, h)
    n = noise(w, h, 5901 + w, beta=1.5, wrap=False)
    a = from_idx(3.2 + n * 0.25, CURT)
    if face != 'h':
        flower = ((x % 6 == 2) & (y % 6 == 2)) | ((x % 6 == 5) & (y % 6 == 5))
        petal = (((x % 6 == 1) | (x % 6 == 3)) & (y % 6 == 2)) | ((x % 6 == 2) & ((y % 6 == 1) | (y % 6 == 3)))
        tint(a, '#c8945a', 0.35, petal)
        tint(a, '#e0b070', 0.45, flower)
        tint(a, '#d8b080', 0.12)            # sun-faded
    return a


def curtain(closed):
    name = 'curtain' + ('_closed' if closed else '')
    CL = mat('cur_cloth', _curtain_cloth)
    m = Model(name, A_CUR, display=DISP_WALL)
    if closed:
        # four pleats across the window: alternating deep / shallow folds
        for i in range(4):
            x0 = i * 4
            m.box([x0, 0, 13.0], [x0 + 2, 16, 14.0], CL, skip=('up', 'down'))
            m.box([x0 + 2, 0, 13.8], [x0 + 4, 16, 14.8], CL, skip=('up', 'down'))
    else:
        # gathered at the west side of the window
        m.box([0, 0, 12.0], [2, 16, 14.0], CL, skip=('up', 'down'))
        m.box([2, 0, 12.8], [4, 16, 14.8], CL, skip=('up', 'down'))
        m.box([4, 0, 13.4], [5, 16, 14.6], CL, skip=('up', 'down'))
    return m


curtain(False)
curtain(True)


# ================================================================================================ futons, box
A_FUTON = Atlas('futon_folded')
FUT_BLUE = ramp(['#4a5a6e', '#5c6e84', '#70849a', '#8498ac', '#98abbd', '#aabccc'], 6)
FUT_PINK = ramp(['#7a4e52', '#8e6064', '#a27478', '#b4888a', '#c49c9c', '#d2aeac'], 6)


def _futon(pal_, seed, flowers='#e8e0d0'):
    def fn(w, h, face):
        x, y = grid(w, h)
        n = noise(w, h, seed + w * 3 + h, beta=1.6, wrap=False)
        a = from_idx(3.0 + n * 0.3, pal_)
        if face == 'h' or w >= 8:
            fl = ((x * 3 + y * 5) % 11 == 0)
            tint(a, flowers, 0.45, fl)
        if face != 'h':
            # the fold: a rounded edge, lit on top, a crease in the middle
            dl(a, 0.05, y == 0)
            dl(a, -0.06, y == h - 1)
            if h >= 3:
                dl(a, -0.04, y == h // 2)
        return a
    return fn


def futon_folded():
    """A stack of folded futons, a blanket, a sheet and a buckwheat pillow, starting 1 px up so they rest on the
    world's oshiire_shelf_board (top at y 1) on the closet shelf."""
    m = Model('futon_folded', A_FUTON, display=DISP_FRONT)
    # (no board: on the closet shelf the world adds its oshiire_shelf_board (y -1..1) under it)
    BLUE = mat('futon_blue', _futon(FUT_BLUE, 6001))
    PINK = mat('futon_pink', _futon(FUT_PINK, 6011, '#f0e4dc'))
    BLANKET = mat('futon_blanket', _futon(ramp(['#3a2c22', '#4a382c', '#5a4636', '#6a5442', '#7a624e'], 5), 6021,
                                          '#8a7058'))
    SHEET = mat('futon_sheet', _futon(PAPER, 6031, '#d4cfc1'))
    m.box([1, 1, 2.6], [15, 4.5, 14.5], BLUE)
    m.box([0.5, 1.2, 2.0], [15.5, 3.8, 2.6], BLUE)                          # the front fold bulging out
    m.box([1.5, 4.5, 3], [14.5, 7.5, 14.5], PINK)
    m.box([2, 7.5, 3.5], [14, 9.5, 14], BLANKET)
    m.box([2.5, 9.5, 4], [13.5, 10.5, 13.5], SHEET)
    # a buckwheat pillow on top
    m.box([4, 10.5, 5], [11, 12.5, 9.5], mat('futon_pillow', _futon(ramp(['#8a8270', '#a09884', '#b4ac98',
                                                                       '#c4bca8'], 4), 6041, '#7a7262')))
    return m


futon_folded()

A_BOX = Atlas('cardboard_box')


def _box_side(w, h, face):
    from hk_lib import m_cardboard
    a = m_cardboard(w, h, 6101 + w * 5 + h, base=3.0)
    if face == 'h':
        rect(a, 0, h // 2, w - 1, h // 2, '#b8a070')               # packing tape across the flaps
        dl(a, -0.05, (grid(w, h)[1] == h // 2 - 1))
    else:
        bevel(a, 0.04, -0.06)
        if w >= 10 and h >= 8:
            # a marker scribble (冬物 - winter clothes) and a rain-damp lower edge
            for (xx, yy) in ((3, 3), (4, 3), (3, 4), (5, 4), (4, 5), (7, 3), (7, 4), (8, 4), (7, 5), (8, 5)):
                px(a, xx, yy, '#1a1410')
            tint(a[h - 2:h], '#3a2a18', 0.25)
    return a


def cardboard_box():
    B = mat('box_side', _box_side)
    m = Model('cardboard_box', A_BOX, display=DISP_FRONT)
    m.box([2, 0, 3], [14, 9, 13], B)
    m.box([1.8, 8.6, 2.8], [14.2, 9.2, 13.2], B)                 # the lid flaps, slightly bulging
    return m


cardboard_box()


# ================================================================================================ altar shelf (empty)
A_ALT = Atlas('altar_shelf')


def _alt_inside(w, h, face):
    a = blank(w, h, '#0e0a08')
    x, y = grid(w, h)
    dl(a, 0.03, y == 0)
    # gilt trim lines of the tiers, faded
    if face != 'h':
        for yy in range(2, h, 3):
            hline(a, 0, w - 1, yy, '#3a2e18')
    return a


def altar_shelf():
    """A small black-lacquered household altar cabinet on its low base, both doors open, nothing inside: no photo,
    no Buddha. Dust, a burnt-down candle stub and an empty offering cup."""
    m = Model('altar_shelf', A_ALT, display=DISP_FRONT)
    LAQ = painted('alt_laq', ramp(['#0a0706', '#120d0a', '#1a130e', '#231a13', '#2c2118'], 5), base=2.4, amp=0.25)
    GILT = solid('alt_gilt', '#8a6e34')
    IN = mat('alt_inside', _alt_inside)
    m.box([3, 0, 5], [13, 3, 13], woodmat('alt_base', DARKWOOD, base=3.0))             # low base
    m.box([3.5, 3, 6], [12.5, 13, 13], LAQ, faces={'north': IN})                         # the cabinet (open front)
    m.box([3.2, 13, 5.7], [12.8, 14, 13.3], LAQ)                                          # roof cornice
    m.box([3.5, 3, 5.9], [12.5, 3.4, 6.1], GILT)
    m.box([3.5, 12.6, 5.9], [12.5, 13, 6.1], GILT)
    # the two doors swung open (perpendicular to the front)
    for x0 in (3.0, 12.5):
        m.box([x0, 3.4, 1.6], [x0 + 0.5, 12.6, 6.0], LAQ, faces={'east' if x0 < 8 else 'west': GILT})
    # inside: steps, a candle stub and an empty cup
    m.box([4, 3, 9], [12, 5, 12.8], LAQ)
    m.box([5, 5, 10.5], [11, 7, 12.8], LAQ)
    m.box([5.0, 5, 8.0], [5.8, 6.2, 8.8], solid('alt_candle', '#d8d0bc'))
    m.box([9.6, 5, 8.0], [10.8, 5.8, 9.2], solid('alt_cup', '#c8c4b8'))
    return m


altar_shelf()


# ================================================================================================ fusuma / oshiire_fusuma
A_FUS = Atlas('fusuma')
A_OSH = Atlas('oshiire_fusuma')


def _seigaiha(w, h, ox=0, oy=0):
    """Mask of a 青海波 wave pattern: rows of overlapping half-circle arcs (radius 3, two rings), offset each row."""
    x, y = grid(w, h)
    x = x + 0.5 + ox
    y = y + 0.5 + oy
    m = np.zeros((h, w), bool)
    covered = np.zeros((h, w), bool)
    R = 3.0
    for row in range(int(h / 1.5) + 3, -2, -1):          # later (lower) rows overlap the ones above
        cy = row * 1.5 + 1.0
        off = (row % 2) * R
        for col in range(-1, int(w / (2 * R)) + 3):
            cx = col * 2 * R + off
            d = np.hypot(x - cx, y - cy)
            inside = (d < R + 0.5) & (y <= cy + 0.01) & ~covered
            ring = inside & ((np.abs(d - R) < 0.5) | (np.abs(d - R * 0.45) < 0.45))
            m |= ring
            covered |= inside
    return m


def _fusuma_picture(oshiire=False):
    """The whole 14 x 30 paper of a 2-block panel (inside the 1 px frame): pale waves (青海波) along the bottom, mist
    bands and two flying cranes above, everything faded; finger grime around the pull, a tear, yellowing at the
    foot. The closet's paper is plainer: a small diamond lattice, a taped repair and an old rabbit sticker."""
    W, H = 14, 30
    r = rng(6201 if not oshiire else 6301)
    x, y = grid(W, H)
    n = noise(W, H, 6202, beta=1.6, wrap=False)
    fib = noise(W, H, 6203, beta=0.2, wrap=False)
    a = from_idx(4.6 + n * 0.3 + fib * 0.25, FUSUMA_PAPER)
    if not oshiire:
        # 流水: three flowing water lines across the foot of the panel, pale blue-grey, the lowest strongest
        for k, (by, amp, ph, al) in enumerate(((H - 8, 1.0, 0.3, 0.28), (H - 5, 1.2, 2.1, 0.34), (H - 2, 0.9, 4.0, 0.4))):
            for xx in range(W):
                yy = int(round(by + amp * math.sin(xx * 2 * math.pi / 7.0 + ph)))
                if 0 <= yy < H:
                    px(a, xx, yy, '#6f8288', al)
                    if yy + 1 < H:
                        px(a, xx, yy + 1, '#a8b6b6', al * 0.5)
        # mist bands (霞): very pale gold, soft-edged
        for (cy, cx, rx) in ((6.0, 5.0, 6.5), (13.5, 10.0, 6.0), (19.0, 3.0, 5.0)):
            band = disc_mask(W, H, cx, cy, rx, 1.3)
            tint(a, '#cdb57a', 0.22, band)
            tint(a, '#b0955a', 0.12, band & ~np.roll(band, -1, 0))
        # two small birds (cranes far off) in flight
        for (bx, by) in ((3, 2), (9, 4)):
            for (dx, dy) in ((0, 0), (4, 0), (1, 1), (3, 1), (2, 2)):
                px(a, bx + dx, by + dy, '#7a7a74', 0.75)
        # finger grime around the pull, a small tear showing the white lining
        tint(a, '#6a5a40', 0.16, disc_mask(W, H, 2.5, H - 12.5, 2.2, 2.6))
        px(a, 10, 21, '#8a7e64')
        px(a, 11, 22, '#6a5e48')
        px(a, 10, 22, '#ece8da')
    else:
        lat = ((x + y) % 4 == 0) | ((x - y) % 4 == 0)
        tint(a, '#9a8e70', 0.16, lat)
        # a strip of yellowed tape repairing a tear
        tint(a[12:14, 8:13], '#c8b47a', 0.35)
        # an old sticker of a white rabbit (from someone before you), half peeled
        sticker = ['#.#', '#.#', '###', 'k#k', '#p#']
        for j, row in enumerate(sticker):
            for i, ch in enumerate(row):
                c = {'#': '#e2ded4', 'k': '#4a4846', 'p': '#c8989a'}.get(ch)
                if c:
                    px(a, 4 + i, 19 + j, c, 0.55)
        px(a, 7, 23, '#c8c0b0')
    # yellowed / dirty toward the foot of the panel, and the hand grime band at pull height
    yel = np.clip((y - H * 0.7) / (H * 0.3), 0, 1)
    tint(a, '#a08a58', yel * 0.18)
    return a


_PICTURES = {}


def _picture(oshiire):
    if oshiire not in _PICTURES:
        _PICTURES[oshiire] = _fusuma_picture(oshiire)
    return _PICTURES[oshiire]


def _panel_face(oshiire, half, back=False):
    """16 x 16 face of one half panel: the black lacquer frame (縁) on the sides and the outer end, the paper
    picture's matching half, the round recessed pull (引手) on the lower half."""
    def fn(w, h):
        a = blank(w, h, '#151110')
        pic = _picture(oshiire)
        if back and oshiire:
            # the inside of the closet doors: plain brown lining paper with old newspaper patches
            b = from_idx(2.6 + noise(14, 30, 6401, beta=1.4, wrap=False) * 0.3, PAPER)
            tint(b, '#8a7a5a', 0.35)
            patch = np.zeros((30, 14), bool)
            patch[9:13, 2:6] = True
            tint(b, '#b8b098', 0.22, patch)
            for yy in range(10, 12):
                hline(b, 3, 4, yy, '#6a6456')
            pic = b
        if half == 'upper':
            a[1:16, 1:15] = pic[0:15]
            hline(a, 0, 15, 0, '#2a2220')
        else:
            a[0:15, 1:15] = pic[15:30]
            hline(a, 0, 15, 15, '#2a2220')
        vline(a, 0, 0, 15, '#2a2220')
        vline(a, 15, 0, 15, '#0e0a08')
        if half == 'lower':
            # 引手 (pull): a small round recess with a brass rim near the left edge, at the top of the lower half
            for (dx, dy, c) in ((0, 0, '#b8943e'), (1, 0, '#7a5c26'), (0, 1, '#2a1c0e'), (1, 1, '#140e08'),
                                (0, 2, '#5a4420'), (1, 2, '#4a361a')):
                px(a, 2 + dx, 3 + dy, c)
        return a
    return fn


def _tenbukuro(w, h):
    """The small upper cupboard's fusuma (16 x 16 face): plain worn paper in its black frame, a little round pull."""
    a = blank(w, h, '#151110')
    b = from_idx(4.2 + noise(14, 15, 6501, beta=1.6, wrap=False) * 0.3, FUSUMA_PAPER)
    tint(b, '#a08a58', 0.12)
    a[1:16, 1:15] = b
    hline(a, 0, 15, 0, '#2a2220')
    vline(a, 0, 0, 15, '#2a2220')
    vline(a, 15, 0, 15, '#0e0a08')
    vline(a, 8, 1, 15, '#2a2220')
    for (dx, dy, c) in ((0, 0, '#b8943e'), (0, 1, '#2a1c0e')):
        px(a, 3 + dx, 8 + dy, c)
        px(a, 12 + dx, 8 + dy, c)
    return a


A_OSH.add('tenbukuro', 16, 16, _tenbukuro)


def _fusuma_models(oshiire):
    A = A_OSH if oshiire else A_FUS
    pref = 'oshiire_fusuma' if oshiire else 'fusuma'
    # panel plane and the step-back plane when fully open (slid behind its neighbour)
    z0, z1 = (0.25, 1.75) if oshiire else (7.0, 8.5)
    zb0, zb1 = (1.9, 3.4) if oshiire else (8.6, 10.1)
    slide = {0: 0.0, 1: 4.0 if oshiire else 8.0, 2: 15.0}
    FRAME = solid('fus_frame', '#151110')
    SILL = woodmat('fus_sill', ramp(['#3a2414', '#4a2e1a', '#5a3a22', '#6a462a', '#7a5232'], 5), base=2.6)
    for half in ('lower', 'upper'):
        fk = f'{half}_front'
        bk = f'{half}_back'
        A.add(fk, 16, 16, _panel_face(oshiire, half))
        A.add(bk, 16, 16, _panel_face(oshiire, half, back=True))
        for op in (0, 1, 2):
            m = Model(f'{pref}_{half}_{op}', A, display=DISP_TALL if half == 'lower' else DISP_TALL)
            dx = slide[op]
            za, zb = (zb0, zb1) if op == 2 else (z0, z1)
            if half == 'lower':
                m.box([dx, 0.5, za], [16 + dx, 16, zb], FRAME,
                      faces={'north': {'r': fk, 'sub': (0, 0, 16, 15.5)},
                             'south': {'r': bk, 'sub': (0, 0, 16, 15.5), 'flip': 'h'}})
            else:
                m.box([dx, 0, za], [16 + dx, 15.2, zb], FRAME,
                      faces={'north': {'r': fk, 'sub': (0, 0.8, 16, 16)},
                             'south': {'r': bk, 'sub': (0, 0.8, 16, 16), 'flip': 'h'}})
            if half == 'lower':
                # the sill (敷居) with its two grooves stays put
                sz0, sz1 = (0, 3.6) if oshiire else (6.5, 10.5)
                m.box([0, 0, sz0], [16, 0.5, sz1], SILL)
            else:
                lz0, lz1 = (0, 3.6) if oshiire else (6.5, 10.5)
                m.box([0, 15.2, lz0], [16, 16, lz1], SILL)
                if oshiire:
                    # the 天袋 above: a small fixed fusuma closing the futon shelf (overflows into the block above)
                    m.box([0, 16, 0.25], [16, 31.2, 1.75], FRAME,
                          faces={'north': {'r': 'tenbukuro', 'sub': (0, 0.8, 16, 16)},
                                 'south': {'r': 'upper_back', 'sub': (0, 0.8, 16, 16), 'flip': 'h'}})
                    m.box([0, 31.2, 0], [16, 32, 3.6], SILL)
    return


_fusuma_models(False)
_fusuma_models(True)
