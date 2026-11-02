"""16x16 item sprites (GUI / hotbar) and the skins used by the 3D held models (s_held.py).

Sprites follow vanilla item conventions: 1 px darker outline, light from the top-left, readable silhouettes that
stay distinct in the hotbar (the three phone states even have different silhouettes: front / back / landscape)."""
import math

import numpy as np

from common import Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, rng, lighten_arr
from px import stamp, outline_alpha, shade_sprite, curve_pts, line_pts

OUT = '#0d0c0e'
METAL = ramp(['#141518', '#1f2125', '#2c2f34', '#3b3f45', '#4e535a', '#666c73', '#868c92'], 7)
REDTUBE = ramp(['#3a0b0b', '#5c1210', '#7e1a15', '#a1261d', '#be3a2a', '#d4573e'], 6)
BRASS = ramp(['#2e220c', '#4f3c16', '#735a22', '#967830', '#b39443', '#c9ad5c', '#ddc781'], 7)
PAPER = ramp(['#5d574a', '#77705f', '#8f8874', '#a49d88', '#b8b19b', '#c9c2ac'], 6)
ROPEC = ramp(['#3b180a', '#6a2c10', '#94401a', '#b85422', '#d06c32', '#e08a50'], 6)
LENS_OFF = pal('#1b2026', '#2c343c', '#4a5560', '#7d8a96')
LENS_ON = pal('#fff6d8', '#ffe7a1', '#ffd36a', '#ffffff')
LENS_LOW = pal('#a8622a', '#c97d38', '#e09a52', '#f0c080')


def capsule(t, p0, p1, radius, shade, inside_fn=None):
    """Draw a cylinder seen side-on from p0 to p1 (pixel coords). radius(s) gives the half-width at axis
    position s in 0..1; shade(s, u) returns a colour, u in -1 (lit edge) .. 1 (shadow edge)."""
    x, y = grid(t.w, t.h)
    px_, py_ = x + 0.5, y + 0.5
    d = np.array(p1, float) - np.array(p0, float)
    L = np.hypot(*d)
    d /= L
    n = np.array([d[1], -d[0]])          # perpendicular, pointing up-left for a bottom-left -> top-right axis
    rel = np.stack([px_ - p0[0], py_ - p0[1]], -1)
    s = (rel @ d) / L
    u = rel @ n
    for yy in range(t.h):
        for xx in range(t.w):
            ss = s[yy, xx]
            if ss < 0 or ss > 1:
                continue
            r = radius(ss)
            if abs(u[yy, xx]) <= r:
                c = shade(ss, -u[yy, xx] / max(r, 1e-6))
                if c is not None:
                    t.set(xx, yy, c, wrap=False)
    return t


def finish(t, outline=OUT, light=True):
    if light:
        shade_sprite(t, amount=0.04)
    outline_alpha(t, outline)
    return t


# ------------------------------------------------------------------------------------------------ flashlight
def _flashlight(state):
    t = Tex(16)
    p0, p1 = (2.6, 13.4), (13.0, 3.0)

    def radius(s):
        if s > 0.78:
            return 2.45            # head / bezel
        if s > 0.70:
            return 2.0
        return 1.55

    def shade(s, u):
        lv = 4 if u < -0.35 else (3 if u < 0.35 else 1)
        if s > 0.80:
            lv = 5 if u < -0.3 else (4 if u < 0.4 else 2)
        if 0.12 < s < 0.62 and int(s * 30) % 2 == 0:     # knurled grip
            lv = max(0, lv - 2)
        if s < 0.06:
            lv = 2
        return METAL[lv]
    capsule(t, p0, p1, radius, shade)
    # switch button (orange rubber) on the lit side of the body
    t.set(9, 8, '#c4621f')
    t.set(10, 8, '#8f4515')
    # lens: the end face at the top-right corner
    L = {'off': LENS_OFF, 'on': LENS_ON, 'low': LENS_LOW}[state]
    for (lx, ly, k) in ((13, 1, 1), (14, 2, 1), (14, 1, 3), (12, 1, 2), (14, 3, 2), (13, 2, 0), (12, 2, 0), (13, 3, 0)):
        t.set(lx, ly, L[k])
    finish(t)
    if state == 'on':
        for (gx, gy, a) in ((15, 0, 0.9), (15, 1, 0.6), (14, 0, 0.6), (15, 4, 0.35), (11, 0, 0.35)):
            t.blend(gx, gy, '#fff2c0', a)
        t.set(15, 0, '#fff7da')
    elif state == 'low':
        t.blend(15, 0, '#d98a40', 0.6)
    return t


@texture('item/flashlight', cutout=True)
def flashlight():
    return _flashlight('off')


@texture('item/flashlight_on', cutout=True)
def flashlight_on():
    return _flashlight('on')


@texture('item/flashlight_low', cutout=True)
def flashlight_low():
    return _flashlight('low')


# ------------------------------------------------------------------------------------------------ phone
PHONE_FRONT = [
    "....oooooooo....",
    "...oaaaaaaaao...",
    "...oabbmbbbco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oassssssco...",
    "...oabbbbbbco...",
    "...occccccccoo..",
    "....oooooooo....",
]


@texture('item/phone', cutout=True)
def phone():
    """Smartphone, screen off: black glass with a cold reflection streak."""
    t = Tex(16)
    stamp(t, PHONE_FRONT, 0, 0, {'o': OUT, 'a': '#3d4148', 'b': '#202226', 'c': '#15161a', 'm': '#4a5058',
                                'n': '#000000', 's': '#0e1116'}, wrap=False)
    for (rx, ry) in ((5, 4), (6, 4), (5, 5), (8, 9), (9, 8), (10, 7), (7, 10)):
        t.set(rx, ry, '#1f2731')
    t.set(5, 3, '#2a3440')
    t.set(7, 13, '#3a3e45')
    t.set(8, 13, '#3a3e45')
    return t


@texture('item/phone_light', cutout=True)
def phone_light():
    """Phone seen from the back with its torch LED blazing."""
    t = Tex(16)
    stamp(t, PHONE_FRONT, 0, 0, {'o': OUT, 'a': '#3a3d44', 'b': '#25272c', 'c': '#17181c', 'm': '#25272c',
                                's': '#1d1f24'}, wrap=False)
    # camera module top-left
    stamp(t, ["ooo.", "oko.", "oko.", "ooo."], 4, 2, {'o': '#0a0b0d', 'k': '#2e3a48'}, wrap=False)
    t.set(5, 3, '#4f6a86')
    # LED and its glare
    t.set(8, 3, '#ffffff')
    t.set(9, 3, '#fff3c8')
    t.set(8, 4, '#fff3c8')
    for (gx, gy, a) in ((9, 2, 0.7), (10, 3, 0.55), (8, 2, 0.6), (10, 1, 0.4), (11, 2, 0.35), (7, 4, 0.4), (9, 4, 0.5)):
        t.blend(gx, gy, '#fff7da', a)
    # brand dimple + a scratch
    t.set(7, 9, '#2c2f35')
    t.set(8, 9, '#2c2f35')
    t.set(9, 11, '#34373e')
    return t


@texture('item/phone_camera', cutout=True)
def phone_camera():
    """Phone held sideways in camera mode: green night-vision viewfinder, REC dot."""
    t = Tex(16)
    rows = [
        "................",
        "................",
        "................",
        ".oooooooooooooo.",
        "oaaaaaaaaaaaaaao",
        "oabggggggggggbco",
        "omggggggggggggco",
        "oaggggggggggggco",
        "oaggggggggggggco",
        "oaggggggggggggco",
        "oabggggggggggbco",
        "occcccccccccccco",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
    ]
    stamp(t, rows, 0, 0, {'o': OUT, 'a': '#3d4148', 'b': '#202226', 'c': '#15161a', 'm': '#4a5058',
                          'g': '#2f4a35'}, wrap=False)
    NV = pal('#1c2e21', '#2a4430', '#3d5c42', '#5a7d5c', '#86a684')
    n = fnoise(16, 16, 601, beta=1.0)
    for yy in range(5, 11):
        for xx in range(2, 14):
            if t.a[yy, xx, 1] > 60 and t.a[yy, xx, 0] < 70:
                lv = int(np.clip(round(1.6 + n[yy, xx] * 0.9 + (1 if yy > 8 else 0) * -0.5), 0, 4))
                t.set(xx, yy, NV[lv])
    # silhouettes of trunks in the viewfinder + a pale figure (it is there)
    for yy in range(5, 11):
        t.set(5, yy, NV[0])
        t.set(11, yy, NV[0])
    t.set(8, 7, NV[4])
    t.set(8, 8, NV[3])
    t.set(8, 9, NV[3])
    t.set(3, 6, '#e0302a')          # REC
    t.set(12, 6, NV[4])
    return t


# ------------------------------------------------------------------------------------------------ battery
@texture('item/battery', cutout=True)
def battery():
    """Two spare AA batteries."""
    t = Tex(16)
    for (p0, p1, band) in (((1.5, 12.5), (10.5, 3.5), '#b8862c'), ((5.5, 14.5), (14.5, 5.5), '#3a7ac0')):
        def shade(s, u, band=band):
            lv = 4 if u < -0.3 else (2 if u < 0.4 else 1)
            if s > 0.9:
                return ['#9a9da2', '#b9bcc0', '#6f7277', '#55585c', '#cfd2d6', '#e0e2e5'][lv] if True else None
            if 0.55 < s < 0.9:
                c = C(band)
                return lighten_arr(c[None], [0, -0.12, -0.05, 0.0, 0.06][lv] if lv < 5 else 0.08)[0]
            return METAL[lv + 1]
        capsule(t, p0, p1, lambda s: 1.5 if s < 0.95 else 0.8, shade)
    finish(t)
    t.set(10, 3, '#e8eaec')
    t.set(14, 5, '#e8eaec')
    return t


@texture('item/power_bank', cutout=True)
def power_bank():
    """Mobile battery (モバイルバッテリー): white slab, charge LEDs (one left), a short cable."""
    t = Tex(16)
    rows = [
        "................",
        "................",
        "..oooooooooo....",
        ".oaaaaaaaaabo...",
        ".oaaaaaaaaabo...",
        ".obbbbbbbbbco...",
        ".obddddddbbco...",
        ".obbbbbbbbbco...",
        ".obblLLLbbbco...",
        ".obbbbbbbbbco...",
        ".obbbbbbbbbco...",
        ".obbbbbbbbbco...",
        ".occcccpcccco...",
        "..ooooooooooo...",
        "................",
        "................",
    ]
    stamp(t, rows, 0, 0, {'o': OUT, 'a': '#d6d6d0', 'b': '#bdbdb6', 'c': '#8e8e88', 'd': '#a9a9a2',
                          'l': '#5ab0ff', 'L': '#3c4a58', 'p': '#1a1a1c'}, wrap=False)
    # cable
    for (cx, cy) in curve_pts([(7, 13), (8, 14), (11, 14), (13, 12), (13, 9), (14, 7)]):
        t.set(cx, cy, '#2a2a2e', wrap=False)
    t.set(14, 6, '#8a8c90')
    t.set(14, 5, '#5a5c60')
    outline_alpha(t, OUT)
    t.blend(3, 10, '#6a5a48', 0.5)       # mud thumbprint
    return t


@texture('item/bear_bell', cutout=True)
def bear_bell():
    """Brass bear bell (熊鈴) on a leather strap, clapper peeking out of the slit."""
    t = Tex(16)
    x, y = grid(16, 16)
    cx, cy = 7.5, 9.5
    d = np.hypot((x + 0.5 - cx - 0.5) / 4.6, (y + 0.5 - cy - 0.5) / 4.4)
    for yy in range(16):
        for xx in range(16):
            if d[yy, xx] <= 1.0 and yy >= 5:
                lit = -(xx - cx) - (yy - cy) * 0.8
                lv = int(np.clip(round(3.2 + lit * 0.35 - d[yy, xx] * 1.3), 0, 6))
                t.set(xx, yy, BRASS[lv])
    # slit with the clapper
    for xx in range(4, 12):
        t.set(xx, 11, '#140e06')
    t.set(7, 12, BRASS[1])
    t.set(8, 12, BRASS[0])
    t.set(7, 13, BRASS[2])
    # highlight + crown loop
    t.set(5, 7, BRASS[6])
    t.set(6, 6, BRASS[6])
    stamp(t, [".aa.", "a..a"], 6, 3, {'a': BRASS[3]}, wrap=False)
    # leather strap
    STRAP = pal('#2a160c', '#4a2a16', '#6a3e22')
    for (sx, sy, k) in ((8, 2, 2), (9, 1, 2), (10, 0, 1), (10, 1, 0), (11, 0, 1), (7, 2, 1)):
        t.set(sx, sy, STRAP[k])
    finish(t)
    return t


@texture('item/rope', cutout=True)
def rope():
    """Coiled orange rope with a loose end."""
    t = Tex(16)
    x, y = grid(16, 16)
    cx, cy = 7.5, 8.0
    dx, dy = x + 0.5 - cx - 0.5, (y + 0.5 - cy - 0.5) * 1.15
    r = np.hypot(dx, dy)
    a = np.arctan2(dy, dx)
    for yy in range(16):
        for xx in range(16):
            rr = r[yy, xx]
            if 2.4 <= rr <= 6.6:
                coil = int((rr - 2.4) / 1.4)
                twist = (a[yy, xx] * 3 + rr * 2.2) % (2 * math.pi) < math.pi
                lit = -(dx[yy, xx] + dy[yy, xx]) / 6
                lv = int(np.clip(round(3 + lit * 1.2 + (0.8 if twist else -0.6) - (0.6 if coil % 2 else 0)), 0, 5))
                t.set(xx, yy, ROPEC[lv])
    for (px_, py_) in curve_pts([(11, 12), (12, 13), (13, 14), (14, 14)]):
        t.set(px_, py_, ROPEC[3], wrap=False)
        t.set(px_, py_ + 1, ROPEC[1], wrap=False)
    t.set(15, 14, ROPEC[5])
    finish(t)
    return t


@texture('item/trail_map', cutout=True)
def trail_map():
    """Folded paper trail map (登山地図): contours, green forest, a red trail line."""
    t = Tex(16)
    for yy in range(2, 14):
        for xx in range(1, 15):
            panel = (xx - 1) // 5
            lv = 4 - (1 if panel == 1 else 0) + (1 if (xx - 1) % 5 == 0 else 0)
            t.set(xx, yy, PAPER[lv])
    for yy in range(2, 14):
        t.set(6, yy, PAPER[1])
        t.set(11, yy, PAPER[2])
    for (x0, y0, x1, y1) in ((2, 4, 13, 6), (2, 9, 13, 8), (3, 12, 12, 11)):
        for (px_, py_) in curve_pts([(x0, y0), ((x0 + x1) // 2, y0 + 2), (x1, y1)]):
            t.blend(px_, py_, '#8a6a4a', 0.6)
    for (gx, gy) in ((3, 6), (4, 7), (3, 7), (12, 10), (13, 11), (12, 11)):
        t.set(gx, gy, '#6d8a5c')
    for (px_, py_) in curve_pts([(2, 12), (5, 10), (8, 9), (10, 6), (13, 4)]):
        t.set(px_, py_, '#b8342a')
    t.set(13, 3, '#2a2a30')
    finish(t, light=False)
    return t


def _flare(lit):
    t = Tex(16)
    p0, p1 = (3.0, 13.5), (12.4, 4.1)

    def shade(s, u):
        lv = 4 if u < -0.3 else (3 if u < 0.4 else 1)
        if s < 0.18:
            return ['#8a8780', '#a8a59c', '#6e6b65', '#57544f', '#c4c1b8', '#d8d5cc'][lv]   # white cap
        if 0.42 < s < 0.62:
            return ['#7a6a20', '#a08a2a', '#5e521a', '#4a4014', '#c0a838', '#d4bc4c'][lv]    # yellow label
        if s > 0.93:
            return '#1c1a18' if not lit else None
        return REDTUBE[lv]
    capsule(t, p0, p1, lambda s: 1.6, shade)
    t.set(7, 9, '#3a3020')
    t.set(8, 8, '#3a3020')
    finish(t)
    if lit:
        core = [(12, 4), (13, 3), (12, 3), (13, 4)]
        for (fx, fy) in core:
            t.set(fx, fy, '#fff0f0')
        for (fx, fy, c) in ((14, 2, '#ff6a6a'), (13, 2, '#ffb0b0'), (14, 3, '#ff8080'), (11, 3, '#ff5050'),
                            (12, 2, '#ff9a9a'), (14, 1, '#ff3a3a'), (15, 1, '#d02020'), (13, 1, '#ff5a5a'),
                            (15, 3, '#ff4a4a'), (12, 5, '#e83a3a')):
            t.set(fx, fy, c)
        for (sx, sy) in ((15, 0), (11, 1), (10, 2)):
            t.set(sx, sy, '#ffd27a')
        for (sx, sy, c) in ((14, 0, '#7a7472'), (13, 0, '#5e5a58')):
            t.set(sx, sy, c)
    return t


@texture('item/flare', cutout=True)
def flare():
    """Emergency flare (発煙筒): red tube, white cap, yellow label, striker tip."""
    return _flare(False)


@texture('item/flare_lit', cutout=True)
def flare_lit():
    return _flare(True)


@texture('item/omamori', cutout=True)
def omamori():
    """Charm pouch (お守り): faded red brocade, gold pattern, knotted cord, a white label."""
    t = Tex(16)
    rows = [
        "......aaaa......",
        ".....a.aa.a.....",
        "......aaaa......",
        "....oooooooo....",
        "...obbbbbbbbo...",
        "...obcbbbbcbo...",
        "...obbwwwwbbo...",
        "...obbwkkwbbo...",
        "...obbwwwwbbo...",
        "...obbwkkwbbo...",
        "...obbwwwwbbo...",
        "...obcbbbbcbo...",
        "...obbbbbbbbo...",
        "...odddddddddo..",
        "....oooooooo....",
        "................",
    ]
    stamp(t, rows, 0, 0, {'o': '#2a0a08', 'a': '#c9a24a', 'b': '#8a2a22', 'c': '#c9a24a', 'w': '#d8d0bc',
                          'k': '#2a2420', 'd': '#5e1a14'}, wrap=False)
    for (px_, py_) in ((4, 4), (5, 4), (4, 5), (4, 6)):
        t.dl(px_, py_, 0.06)
    for (px_, py_) in ((11, 11), (11, 12), (10, 12)):
        t.dl(px_, py_, -0.06)
    return t


@texture('item/hut_key', cutout=True)
def hut_key():
    """Old iron hut key on a string with a wooden tag (山小屋)."""
    t = Tex(16)
    K = ramp(['#1a1714', '#2e2925', '#4a433c', '#6a6157', '#8a8075'], 5)
    for (px_, py_) in curve_pts([(5, 10), (8, 7), (11, 4)]):
        t.set(px_, py_, K[3])
        t.set(px_ + 1, py_ + 1, K[1])
    stamp(t, [".aaa.", "a...a", "a...a", "a...a", ".aaa."], 1, 9, {'a': K[3]}, wrap=False)
    t.set(2, 10, K[4])
    t.set(4, 13, K[1])
    t.set(12, 4, K[2])
    t.set(12, 5, K[2])
    t.set(13, 5, K[1])
    t.set(10, 4, K[2])
    t.set(11, 6, K[1])
    # string and tag
    for (px_, py_) in ((5, 12), (6, 13), (7, 13)):
        t.set(px_, py_, '#6a6250')
    stamp(t, ["aab", "aab", "abb", "bbb"], 8, 12, {'a': '#a07c45', 'b': '#7a5c30'}, wrap=False)
    t.set(9, 13, '#2a2420')
    finish(t)
    return t


@texture('item/omiki', cutout=True)
def omiki():
    """御神酒: white porcelain sake flask (tokkuri) wrapped in a red-and-white paper band."""
    t = Tex(16)
    rows = [
        "......oooo......",
        ".....oabbco.....",
        "......obco......",
        "......obco......",
        ".....oabbco.....",
        "....oabbbbco....",
        "...oabbbbbbco...",
        "...orrrrrrrro...",
        "...owwwwwwwwo...",
        "...owwwkkwwwo...",
        "...orrrrrrrro...",
        "...oabbbbbbco...",
        "...oabbbbbbco...",
        "....odddddco....",
        ".....oooooo.....",
        "................",
    ]
    stamp(t, rows, 0, 0, {'o': '#2a2824', 'a': '#f0ece2', 'b': '#d6d1c4', 'c': '#a8a294', 'd': '#8e897d',
                          'r': '#b02a22', 'w': '#ece6d8', 'k': '#2a2420'}, wrap=False)
    t.set(6, 6, '#ffffff')
    t.set(5, 12, '#ffffff')
    return t


@texture('item/memo', cutout=True)
def memo_item():
    """A damp folded note with handwriting (the lost hiker's)."""
    t = Tex(16)
    for yy in range(2, 15):
        for xx in range(3, 13):
            lv = 4 + (1 if xx < 8 else 0) - (1 if (xx + yy) % 7 == 0 else 0)
            t.set(xx, yy, PAPER[lv])
    for yy in range(2, 15):
        t.set(8, yy, PAPER[2])
    for (cx, cy) in ((12, 2), (12, 3), (11, 2)):
        t.clear(cx, cy)
    t.set(11, 3, PAPER[1])
    r = rng(611)
    for xx in (11, 10, 6, 5):
        for yy in range(4, 13):
            if r.random() < 0.55:
                t.set(xx, yy, '#3a3940')
    t.blend(9, 12, '#5a3a28', 0.5)
    t.blend(10, 13, '#5a3a28', 0.4)
    finish(t, light=False)
    return t


# ------------------------------------------------------------------------------------------------ held model skins
HELD_REG = {
    'flashlight': {'body': (0, 0, 12, 3), 'body_end': (12, 0, 15, 3), 'head': (0, 3, 12, 7), 'bezel': (12, 3, 16, 7),
                   'lens_off': (0, 7, 4, 11), 'lens_on': (4, 7, 8, 11), 'lens_low': (8, 7, 12, 11),
                   'tail': (12, 7, 15, 10), 'switch': (0, 11, 2, 13), 'grip': (0, 13, 12, 16)},
    'phone': {'frame': (0, 0, 2, 16), 'frame_h': (2, 0, 16, 2), 'back': (16, 0, 32, 16),
              'screen_off': (0, 16, 8, 32), 'screen_light': (8, 16, 16, 32), 'screen_cam': (16, 16, 24, 32),
              'cam_module': (24, 16, 28, 20), 'led_off': (28, 16, 30, 18), 'led_on': (30, 16, 32, 18),
              'edge': (2, 2, 16, 4)},
    'flare': {'tube': (0, 0, 12, 2), 'label': (0, 2, 12, 4), 'cap': (12, 0, 16, 4), 'cap_side': (0, 4, 4, 6),
              'tip': (4, 4, 6, 6), 'tip_lit': (6, 4, 8, 6), 'flame': (0, 8, 8, 16), 'smoke': (8, 8, 16, 16),
              'end': (8, 4, 10, 6)},
}


@texture('item/held/flashlight', size=(16, 16))
def held_flashlight():
    t = Tex(16)
    R = HELD_REG['flashlight']

    def band(rect, P, rows, ripple=False):
        u0, v0, u1, v1 = rect
        for yy in range(v0, v1):
            for xx in range(u0, u1):
                lv = rows[(yy - v0) % len(rows)]
                if ripple and (xx - u0) % 2 == 0:
                    lv = max(0, lv - 2)
                t.set(xx, yy, P[lv])
    band(R['body'], METAL, [4, 3, 2])
    band(R['grip'], METAL, [4, 3, 2], ripple=True)
    band(R['body_end'], METAL, [3, 2, 1])
    band(R['head'], METAL, [5, 4, 3, 2])
    band(R['bezel'], METAL, [6, 5, 4, 3])
    band(R['tail'], METAL, [2, 1, 1])
    for name, L in (('lens_off', LENS_OFF), ('lens_on', LENS_ON), ('lens_low', LENS_LOW)):
        u0, v0, u1, v1 = R[name]
        for yy in range(v0, v1):
            for xx in range(u0, u1):
                edge = xx in (u0, u1 - 1) or yy in (v0, v1 - 1)
                k = 1 if edge else (3 if (xx - u0, yy - v0) == (1, 1) else 2)
                t.set(xx, yy, L[k] if name != 'lens_on' else L[0 if not edge else 2])
    u0, v0, u1, v1 = R['switch']
    for yy in range(v0, v1):
        for xx in range(u0, u1):
            t.set(xx, yy, '#c4621f' if yy == v0 else '#8f4515')
    return t


@texture('item/held/phone', size=(32, 32))
def held_phone():
    t = Tex(32)
    R = HELD_REG['phone']

    def rect(name, fn):
        u0, v0, u1, v1 = R[name]
        for yy in range(v0, v1):
            for xx in range(u0, u1):
                t.set(xx, yy, fn(xx - u0, yy - v0, u1 - u0, v1 - v0), wrap=False)
    rect('frame', lambda x, y, w, h: ['#4a4f56', '#2a2d32'][x])
    rect('frame_h', lambda x, y, w, h: ['#4a4f56', '#2a2d32'][y])
    rect('edge', lambda x, y, w, h: ['#3a3e44', '#26292e'][y])
    rect('back', lambda x, y, w, h: '#1c1e22' if (x + y) % 7 else '#22252a')
    rect('cam_module', lambda x, y, w, h: '#0b0c0e' if x in (0, 3) or y in (0, 3) else '#2c3a4a')
    rect('led_off', lambda x, y, w, h: '#8a8678')
    rect('led_on', lambda x, y, w, h: '#ffffff')

    def screen_off(x, y, w, h):
        c = '#0c0f13'
        if 0 <= (x + y * 0.6) - 3 <= 1.2:
            c = '#18202a'
        if y == 0 or y == h - 1:
            c = '#08090b'
        return c
    rect('screen_off', screen_off)

    def screen_light(x, y, w, h):
        # torch app: dark UI, a big lit button
        cx, cy = 3.5, 9.5
        d = math.hypot(x - cx, (y - cy) * 0.9)
        if y in (0, h - 1):
            return '#08090b'
        if d < 2.4:
            return '#f6f4ec'
        if d < 3.2:
            return '#9ab0c8'
        if y == 2 and 1 <= x <= 6:
            return '#c8ccd2'
        return '#1a2230'
    rect('screen_light', screen_light)
    n = fnoise(8, 16, 621, beta=0.6)

    def screen_cam(x, y, w, h):
        if y in (0, h - 1):
            return '#08090b'
        if (x, y) == (1, 2):
            return '#ff3a30'
        lv = 1.7 + n[y, x] * 0.8 - (0.8 if x in (2, 6) else 0) + (1.5 if (x, y) in ((4, 8), (4, 9), (4, 10)) else 0)
        return ['#14231a', '#22382a', '#36553e', '#5a7d5c', '#9ec09a'][int(np.clip(round(lv), 0, 4))]
    rect('screen_cam', screen_cam)
    return t


@texture('item/held/flare', size=(16, 16), cutout=True)
def held_flare():
    t = Tex(16)
    R = HELD_REG['flare']

    def rect(name, fn):
        u0, v0, u1, v1 = R[name]
        for yy in range(v0, v1):
            for xx in range(u0, u1):
                c = fn(xx - u0, yy - v0, u1 - u0, v1 - v0)
                if c is not None:
                    t.set(xx, yy, c, wrap=False)
    rect('tube', lambda x, y, w, h: REDTUBE[[4, 2][y]])
    rect('label', lambda x, y, w, h: ['#c0a838', '#8a7a26'][y] if (x % 4) else '#3a3020')
    rect('cap', lambda x, y, w, h: ['#d8d5cc', '#c4c1b8', '#a8a59c', '#8a8780'][y])
    rect('cap_side', lambda x, y, w, h: ['#c4c1b8', '#a8a59c'][y])
    rect('tip', lambda x, y, w, h: '#1c1a18')
    rect('tip_lit', lambda x, y, w, h: '#fff0f0' if (x + y) % 2 == 0 else '#ffb0b0')
    rect('end', lambda x, y, w, h: REDTUBE[1])
    x, y = grid(8, 8)
    for yy in range(8):
        for xx in range(8):
            # flame: a teardrop pointing up (v=0 is the top)
            dx, dy = (xx - 3.5) / 3.6, (yy - 5.0) / 4.5
            r = math.hypot(dx, dy * (1.4 if yy < 5 else 1.0)) - (0.25 * max(0, 4 - yy) / 4)
            if r < 1.0:
                c = '#fff4f0' if r < 0.35 else ('#ff9a9a' if r < 0.6 else ('#ff4a3a' if r < 0.85 else '#c81e1e'))
                t.set(R['flame'][0] + xx, R['flame'][1] + yy, c, wrap=False)
            sm = math.hypot((xx - 3.5) / 3.8, (yy - 3.5) / 3.8)
            if sm < 1.0 and (xx * 3 + yy * 5) % 4:
                t.set(R['smoke'][0] + xx, R['smoke'][1] + yy, ['#8a8684', '#6e6a68', '#5a5654'][(xx + yy) % 3], wrap=False)
    return t
