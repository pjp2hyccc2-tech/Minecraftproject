"""Restroom props (models face north; wall-mounted fixtures have the wall on their south side)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Atlas, Model, child, disp, DISP_FRONT, DISP_WALL, DISP_FLOOR, DISP_SMALL
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, bitmap, m_brushed, m_plastic, m_paint, m_glass, STEEL, CHROME,
                    WHITEPLASTIC, BEIGE, GREYPAINT)
import rs_mats as M
from rs_waiting import solid, lit
from rs_kitchen import A_WATER

CERAMIC = ramp(['#8e8e86', '#a8a8a0', '#c0c0b8', '#d4d4cc', '#e2e2da', '#ecece6', '#f4f4ee'], 7)
CER = M.plastic('ceramic', CERAMIC, base=4.6)


def _basin_top(w, h, face):
    a = m_plastic(w, h, ramp(['#6a6a64', '#86867e', '#a2a29a', '#b8b8b0', '#cacac2'], 5), 11001, base=3.0)
    if w >= 10 and h >= 8:
        cx, cy = w / 2, h / 2 - 0.5
        outer = disc_mask(w, h, cx, cy, w * 0.36, h * 0.36)
        inner = disc_mask(w, h, cx, cy, w * 0.30, h * 0.28)
        a[outer] = C('#f0f0ea')
        a[inner] = C('#d8d8d2')
        x, y = grid(w, h)
        dl(a, -0.06, inner & (y < cy))
        px(a, int(cx), int(cy + 0.5), '#3a3a3a')
        # rust stain under the tap and limescale
        tint(a, '#9a6a3a', 0.35, inner & (np.abs(x - cx) < 0.8) & (y < cy) & (y > cy - 3))
    return a


def restroom_sink(running):
    name = 'restroom_sink_running' if running else 'restroom_sink'
    m = Model(name, A_SINK, render_type='translucent' if running else None, display=DISP_FRONT)
    TOP = M.custom('sink_counter', _basin_top)
    m.box([0, 12, 3], [16, 13.5, 16], M.plastic('counter_edge', ramp(['#6a6a64', '#86867e', '#a2a29a', '#b8b8b0'], 4)),
          faces={'up': TOP})
    m.box([0, 9, 3.5], [16, 12, 4.5], M.paint('apron', ramp(['#2a1a10', '#3a2416', '#4a2e1c', '#5a3a24'], 4), base=2.2))
    # plumbing under the counter
    m.box([7.5, 6, 8], [8.5, 12, 9], M.CHROME_M)
    m.box([7.5, 6, 9], [8.5, 7, 15], M.CHROME_M)
    # faucet at the back
    m.box([7.25, 13.5, 12.5], [8.75, 16, 14], M.CHROME_M)
    m.box([7.5, 15, 9.5], [8.5, 16, 12.5], M.CHROME_M)
    m.box([7.5, 16, 12.8], [8.5, 16.5, 13.8], M.CHROME_M)
    if running:
        m.box([7.7, 12.5, 9.7], [8.3, 15, 10.3], None, faces={f: {'r': (A_WATER, 'stream')} for f in
                                                              ('north', 'south', 'east', 'west')}, shade=False)
    return m


A_SINK = Atlas('restroom_sink')
restroom_sink(False)
restroom_sink(True)

# ================================================================================================ the dark mirror
A_MIRROR = Atlas('mirror')


def _smoked(cracked):
    def fn(w, h):
        r = rng(11101)
        a = blank(w, h, C('#1c2428', 128))
        n = noise(w, h, 11102, beta=1.5, wrap=False)
        a[..., :3] += n[..., None] * 5
        x, y = grid(w, h)
        # smudges and wiped streaks, water spots
        st = (np.abs((x * 0.8 + y) % 13 - 6) < 0.6)
        a[st, :3] += 18
        a[st, 3] = 140
        spots = r.random((h, w)) < 0.03
        a[spots] = C('#56646a', 150)
        # two old palm prints, faint
        for (cx, cy) in ((4.5, 7.5), (11.5, 6.5)):
            palm = disc_mask(w, h, cx, cy + 1.2, 1.5, 1.8)
            a[palm, :3] += 14
            a[palm, 3] = 150
            for k in range(4):
                fx = cx - 1.5 + k
                for yy in range(int(cy - 2.5), int(cy - 0.3)):
                    if 0 <= yy < h and 0 <= int(fx) < w:
                        a[yy, int(fx), :3] += 12
                        a[yy, int(fx), 3] = 146
        if cracked:
            ox, oy = 6.5, 7.5
            for k in range(9):
                ang = k / 9 * 2 * math.pi + r.uniform(-0.25, 0.25)
                L = r.uniform(5, 11)
                px_, py_ = ox, oy
                for t in range(int(L * 2)):
                    px_ += math.cos(ang) * 0.5
                    py_ += math.sin(ang) * 0.5
                    ang += r.uniform(-0.15, 0.15)
                    ix, iy = int(px_), int(py_)
                    if 0 <= ix < w and 0 <= iy < h:
                        a[iy, ix] = C('#c8d4d8', 210)
            for rr in (2.0, 4.0):
                ring = ring_mask(w, h, ox, oy, rr - 0.4, rr + 0.4) & (r.random((h, w)) < 0.6)
                a[ring] = C('#b4c2c6', 190)
            a[int(oy), int(ox)] = C('#f0f6f8', 230)
        return a
    return fn


def mirror(cracked):
    name = 'mirror_cracked' if cracked else 'mirror'
    reg = 'glass_cracked' if cracked else 'glass'
    A_MIRROR.add(reg, 16, 16, _smoked(cracked))
    A_MIRROR.add('frame', 16, 1, lambda w, h: blank(w, h, '#9ea4a8'))
    m = Model(name, A_MIRROR, render_type='translucent', display=DISP_WALL, ao=False)
    m.box([0, 0.5, 0], [16, 15.5, 1], None, faces={'north': reg, 'south': {'r': reg, 'flip': 'h'}})
    for y0 in (0, 15.5):
        m.box([0, y0, 0], [16, y0 + 0.5, 1.2], None, faces={f: 'frame' for f in ('north', 'south', 'up', 'down')})
    return m


mirror(False)
mirror(True)

# ================================================================================================ ordinary mirror
A_RMIR = Atlas('restroom_mirror')


def _reflection(w, h):
    a = blank(w, h, '#9fb0b8')
    x, y = grid(w, h)
    g = (y / h) * 0.25
    a[..., :3] = a[..., :3] * (1 - g[..., None]) + np.array([60, 72, 80]) * g[..., None]
    # a reflected tile wall: faint grid, a reflected light
    dl(a, -0.04, ((x.astype(int) % 4) == 0) | ((y.astype(int) % 4) == 0))
    st = (np.abs((x + y * 0.7) % 11 - 5) < 0.7)
    dl(a, 0.08, st)
    specks(a, 11201, 0.03, '#d8e2e6', 0.7)
    grime(a, 11202, strength=0.12, color='#5a5a48', bottom=0.2)
    return a


def restroom_mirror():
    A_RMIR.add('reflection', 14, 13, _reflection)
    m = Model('restroom_mirror', A_RMIR, display=DISP_WALL)
    m.box([1, 2, 15], [15, 15, 16], M.CHROME_M)
    m.box([1.5, 2.5, 14.9], [14.5, 14.5, 15], None, faces={'north': 'reflection'})
    return m


restroom_mirror()

# ================================================================================================ toilet
A_TOILET = Atlas('toilet')


def _washlet_panel(w, h, face):
    a = m_plastic(w, h, BEIGE, 11301, base=3.6)
    if face == 'h' and w >= 2:
        for i, c in enumerate(('#2a6ad8', '#e8721f', '#c8281c', '#3aa84a')):
            yy = 1 + i
            if yy < h - 1:
                px(a, w // 2, yy, c)
    return a


def toilet():
    m = Model('toilet', A_TOILET)
    m.box([5.5, 0, 4.5], [10.5, 4.5, 12], CER)                                      # pedestal
    m.box([4, 4.5, 2], [12, 7, 12.5], CER)                                          # bowl
    SEAT = M.plastic('seat', ramp(['#a8a49a', '#c0bcb2', '#d4d0c6', '#e2ded4'], 4), base=2.4)
    m.box([3.8, 7, 1.8], [12.2, 7.6, 12], SEAT)                                     # seat
    m.box([4, 7.6, 2], [12, 8.3, 11.5], SEAT)                                       # lid (closed)
    m.box([4.5, 7, 11.5], [11.5, 9.5, 13], M.plastic('washlet', BEIGE, base=3.8))   # washlet body
    m.box([12.2, 7, 6], [14, 8.8, 10], M.custom('washlet_panel', _washlet_panel))
    m.box([4, 7, 12.5], [12, 14.5, 16], CER)                                        # tank
    m.box([3.8, 14.5, 12.3], [12.2, 15.2, 16], CER)
    m.box([4.5, 12.5, 12.2], [6, 13, 12.5], M.CHROME_M)                             # flush lever
    return m


toilet()

# ================================================================================================ urinal
A_URI = Atlas('urinal')


def _urinal_front(w, h, face):
    a = m_plastic(w, h, CERAMIC, 11401, base=4.6)
    if face != 'h' and w >= 6 and h >= 8:
        rect(a, 2, 2, w - 3, h - 2, '#cfcfc7')
        rect(a, 3, 3, w - 4, h - 3, '#babab2')
        dl(a[h - 4:h - 2, 3:w - 3], -0.05)
        tint(a, '#a8904a', 0.15)
    return a


def urinal():
    m = Model('urinal', A_URI, display=DISP_WALL)
    m.box([4, 2, 10], [12, 13, 16], CER, faces={'north': M.custom('urinal_front', _urinal_front)})
    m.box([4.5, 3.5, 9.5], [11.5, 4.2, 10.5], CER)                                  # lip
    m.box([6.5, 3.6, 11], [9.5, 4.2, 13], solid('deodorizer', '#3a8ad8'))           # blue cake inside
    m.box([7.5, 13, 14], [8.5, 16, 15], M.CHROME_M)                                 # flush valve pipe
    m.box([6, 14, 13.5], [10, 15.5, 15.5], M.CHROME_M)
    m.box([7, 14.5, 13.3], [9, 15, 13.5], lit('sensor', '#3a0a0a'), only=('north',))
    return m


urinal()

# ================================================================================================ stall wall
A_STALL = Atlas('stall_wall')
LAMINATE = ramp(['#6d604c', '#857760', '#9c8d74', '#b0a186', '#c1b296', '#cfc1a4'], 6)


def stall_wall():
    m = Model('stall_wall', A_STALL)
    P = M.custom('stall_panel', lambda w, h, f: _stall_panel(w, h, f))
    m.box([0, 0, 7], [16, 16, 9], P)
    m.box([0, 15.5, 6.75], [16, 16, 9.25], M.CHROME_M)
    return m


def _stall_panel(w, h, face):
    a = m_plastic(w, h, LAMINATE, 11501 + w, base=3.6)
    if face != 'h':
        grime(a, 11502, strength=0.1, color='#4a3c26', bottom=0.3)
        # a scratched-in graffiti and an old sticker
        if w >= 12:
            for (x, y) in ((4, 5), (5, 6), (6, 5), (7, 6), (8, 5)):
                px(a, x, y, LAMINATE[1])
            rect(a, 11, 9, 13, 10, '#e8e4dc')
    return bevel(a, 0.03, -0.04)


stall_wall()

# ================================================================================================ hand dryer
A_DRYER = Atlas('hand_dryer')


def _dryer_front(on):
    def fn(w, h):
        a = m_plastic(w, h, ramp(['#7a7e80', '#96999b', '#b0b3b4', '#c6c8c8', '#d8d9d8'], 5), 11601, base=3.4)
        rect(a, 1, 1, w - 2, 2, '#3a5a8a')
        px(a, w - 3, 4, '#3aa8ff' if on else '#1a3a5a')
        return a
    return fn


def hand_dryer(running):
    name = 'hand_dryer_running' if running else 'hand_dryer'
    reg = 'front_on' if running else 'front'
    A_DRYER.add(reg, 10, 6, _dryer_front(running))
    m = Model(name, A_DRYER, display=DISP_WALL, ao=not running)
    BODY = M.plastic('dryer_body', ramp(['#7a7e80', '#96999b', '#b0b3b4', '#c6c8c8', '#d8d9d8'], 5), base=3.2)
    m.box([3, 10, 9], [13, 16, 16], BODY, faces={'north': reg}, glow=('north',) if running else ())
    m.box([3, 2, 9], [13, 5, 16], BODY)                                           # lower part / drain tank
    m.box([3, 5, 14], [13, 10, 16], BODY)                                         # back of the hand slot
    m.box([3, 5, 9], [3.75, 10, 14], BODY)
    m.box([12.25, 5, 9], [13, 10, 14], BODY)
    SLOT = lit('slot_on', '#3a7ad8') if running else solid('slot', '#1a1c1e')
    m.box([3.75, 9.6, 9.5], [12.25, 10, 14], SLOT, only=('down',), glow=('down',) if running else ())
    m.box([3.75, 5, 9.5], [12.25, 5.4, 14], SLOT, only=('up',), glow=('up',) if running else ())
    m.box([4, 2, 8.5], [12, 4.5, 9], M.custom('tank_window', lambda w, h, f: blank(w, h, '#5a6a72')))
    return m


hand_dryer(False)
hand_dryer(True)

# ================================================================================================ paper towel
A_TOWEL = Atlas('paper_towel')


def paper_towel():
    m = Model('paper_towel', A_TOWEL, display=DISP_WALL)
    W_ = M.plastic('towel_box', WHITEPLASTIC, base=4.2)
    m.box([4, 7, 12], [12, 15, 16], W_, faces={'north': M.custom('towel_front', lambda w, h, f: _towel_front(w, h, f))})
    m.box([5, 6, 12.5], [11, 7.5, 13], solid('towel', '#f4f2ea'), rot=('x', -22.5, [8, 7, 12.75]))
    return m


def _towel_front(w, h, face):
    a = m_plastic(w, h, WHITEPLASTIC, 11701, base=4.2)
    if face != 'h' and w >= 6:
        rect(a, 1, 1, w - 2, h - 3, '#6a7478')
        for y in range(2, h - 3):
            hline(a, 2, w - 3, y, '#e8e6dc')
        rect(a, 2, h - 2, w - 3, h - 2, '#3a3a3a')
    return a


paper_towel()
