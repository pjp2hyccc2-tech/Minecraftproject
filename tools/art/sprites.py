#!/usr/bin/env python3
"""Non-item art for core features (armor/art module):

* monster projectile sprites  textures/entity/projectile/<kind>.png  (32x32, camera-facing, full-bright, translucent)
* placed trap decals          textures/entity/trap_pitfall.png, trap_shock.png  (32x32, ground quad 1.8 blocks)
* mob effect icons            textures/mob_effect/<effect>.png  (18x18)
* barrel bomb blocks          blockstates + block models + textures/block/barrel_bomb*_{side,top,bottom}.png

    python3 tools/art/sprites.py [--1x] # writes everything + tools/preview/sprites.png (HD by default:
                                        # blocks 64x64, projectiles / traps 64x64, effect icons 36x36)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pix import Canvas, ramp, hx  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')
OUT = {}  # relative texture path -> RGBA float array


# ----------------------------------------------------------------------------- raw RGBA helpers
def blank(n):
    return np.zeros((n, n, 4), float)


def over(dst, src):
    """Alpha-composites src over dst (both float RGBA 0..255), in place on dst."""
    sa = src[..., 3:4] / 255.0
    da = dst[..., 3:4] / 255.0
    oa = sa + da * (1 - sa)
    rgb = np.where(oa > 0, (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6), 0)
    dst[..., :3] = rgb
    dst[..., 3:4] = oa * 255.0
    return dst


def polar(n):
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    dx, dy = xx - n / 2.0, yy - n / 2.0
    return np.hypot(dx, dy), np.arctan2(dy, dx), dx, dy


def wobble(th, seed, amp, freqs=(3, 5, 8)):
    rng = np.random.default_rng(seed)
    w = np.zeros_like(th)
    for k, f in enumerate(freqs):
        w += amp / (k + 1) * np.sin(f * th + rng.uniform(0, 2 * math.pi))
    return w


def bands(t, pal, img=None):
    """Quantises intensity t (0 outside .. 1 centre) into palette pal = [(hex, alpha) outer -> inner]."""
    n = t.shape[0]
    img = blank(n) if img is None else img
    lev = np.floor(np.clip(t, 0, 0.9999) * len(pal)).astype(int)
    on = t > 0
    for k, (col, a) in enumerate(pal):
        m = on & (lev == k)
        img[m, :3] = hx(col)
        img[m, 3] = a
    return img


def put(img, x, y, col, a=255):
    n = img.shape[0]
    if 0 <= x < n and 0 <= y < n:
        img[y, x, :3] = hx(col)
        img[y, x, 3] = a


def canvas_rgba(c):
    return c.img.copy()


def halo(n, radius, col, a0, seed=0, amp=0.0):
    """Soft translucent disc (quantised to 3 alpha steps) - glow behind opaque projectiles."""
    r, th, _, _ = polar(n)
    R = radius * (1 + wobble(th, seed, amp))
    t = np.clip(1 - r / R, 0, 1)
    img = blank(n)
    m = t > 0
    img[m, :3] = hx(col)
    img[m, 3] = np.where(t[m] > 0.5, a0, np.where(t[m] > 0.25, a0 * 0.6, a0 * 0.3))
    return img


def sparks(img, pts, col, a=255):
    for x, y in pts:
        put(img, x, y, col, a)


# ----------------------------------------------------------------------------- projectiles (32x32)
def fireball():
    n = 32
    r, th, dx, dy = polar(n)
    R = 13.5 * (1 + wobble(th, 11, 0.16, (5, 7, 11)))
    t = np.clip(1 - r / R, 0, 1) ** 0.85
    img = bands(t, [('#7a1404', 70), ('#b8260a', 150), ('#e2480e', 225), ('#ff7a1a', 255), ('#ffae2e', 255),
                    ('#ffd860', 255), ('#fff2b0', 255), ('#ffffff', 255)])
    sparks(img, [(3, 9), (27, 6), (29, 20), (6, 27), (24, 28), (1, 17)], '#ffb030', 220)
    sparks(img, [(4, 8), (28, 21)], '#fff0a0', 255)
    return img


def lava_blob():
    n = 32
    img = halo(n, 15.5, '#ff5a10', 110, seed=4, amp=0.08)
    c = Canvas(n)
    hm = c.poly([(6, 12), (10, 6), (17, 5), (24, 8), (27, 15), (25, 23), (18, 27), (10, 26), (5, 20)])
    c.layer(hm, ['#140806', '#2e130c', '#4a2216', '#6a3420', '#8a4a2c'], shade='dome', bulge=1.2)
    rock = canvas_rgba(c)
    cracks = [((9, 13), (14, 15), (16, 21), (13, 25)), ((14, 15), (20, 12), (24, 9)), ((16, 21), (22, 21), (25, 18)),
              ((20, 12), (19, 7))]
    for path in cracks:
        for a, b in zip(path, path[1:]):
            steps = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
            for k in range(steps + 1):
                x = int(round(a[0] + (b[0] - a[0]) * k / steps))
                y = int(round(a[1] + (b[1] - a[1]) * k / steps))
                if rock[y, x, 3] > 0:
                    rock[y, x, :3] = hx('#ffb030')
                    for ox, oy in ((1, 0), (0, 1)):
                        if rock[y + oy, x + ox, 3] > 0 and (rock[y + oy, x + ox, :3] != hx('#ffb030')).any():
                            rock[y + oy, x + ox, :3] = hx('#e2480e')
    for x, y in ((14, 15), (16, 21), (20, 12)):
        rock[y, x, :3] = hx('#fff2b0')
    over(img, rock)
    sparks(img, [(3, 5), (28, 4), (29, 26), (2, 25)], '#ffae2e', 220)
    return img


def thunder_orb():
    n = 32
    r, th, dx, dy = polar(n)
    t = np.clip(1 - r / 12.5, 0, 1) ** 0.8
    img = bands(t, [('#8a6a00', 70), ('#d8b000', 140), ('#ffe030', 220), ('#fff27a', 255), ('#ffffc8', 255), ('#ffffff', 255)])
    rng = np.random.default_rng(7)
    for k in range(6):
        a = k / 6 * 2 * math.pi + rng.uniform(-0.3, 0.3)
        x, y = 16.0, 16.0
        rr = 7.0
        pts = []
        while rr < 15.5:
            rr += 2.2
            a += rng.uniform(-0.5, 0.5)
            pts.append((16 + math.cos(a) * rr, 16 + math.sin(a) * rr))
        px, py = 16 + math.cos(a) * 7, 16 + math.sin(a) * 7
        for qx, qy in pts:
            steps = int(max(abs(qx - px), abs(qy - py))) + 1
            for s in range(steps + 1):
                ix = int(round(px + (qx - px) * s / steps))
                iy = int(round(py + (qy - py) * s / steps))
                put(img, ix, iy, '#fffbe0', 255)
            px, py = qx, qy
        put(img, int(round(px)), int(round(py)), '#ffe55a', 255)
    return img


def frost_orb():
    n = 32
    r, th, dx, dy = polar(n)
    R = 13.0 * (1 + 0.1 * np.cos(6 * th))
    t = np.clip(1 - r / R, 0, 1) ** 0.8
    img = bands(t, [('#2a6a9a', 70), ('#4fa9d3', 140), ('#8ad6f2', 215), ('#c4f0ff', 255), ('#eafcff', 255), ('#ffffff', 255)])
    # six-armed snowflake
    for k in range(6):
        a = k / 6 * 2 * math.pi + math.pi / 6
        for s in range(3, 12):
            x, y = int(round(15.5 + math.cos(a) * s)), int(round(15.5 + math.sin(a) * s))
            put(img, x, y, '#ffffff' if s < 10 else '#dff8ff', 255)
        for s, ln in ((6, 2), (9, 2)):
            bx, by = 15.5 + math.cos(a) * s, 15.5 + math.sin(a) * s
            for side in (-1, 1):
                b = a + side * math.radians(55)
                for q in range(1, ln + 1):
                    put(img, int(round(bx + math.cos(b) * q)), int(round(by + math.sin(b) * q)), '#ffffff', 255)
    sparks(img, [(4, 6), (27, 7), (28, 25), (5, 26)], '#dff8ff', 200)
    return img


def ice_shard():
    n = 32
    img = halo(n, 13.5, '#4fa9d3', 70)
    c = Canvas(n)
    G = ['#0e3a5a', '#2a7ab0', '#5ab8e8', '#a6e4fa', '#ffffff']
    hm = c.poly([(16, 1.5), (23.5, 12), (21, 28.5), (16, 31), (11, 28.5), (8.5, 12)])
    c.layer(hm, G, shade='flat', bulge=0.6)
    side = c.poly([(16, 1.5), (23.5, 12), (21, 28.5), (16, 31)])
    m = c.cells(side) & (c.img[..., 3] > 0) & (c.last_lev > 0)
    c.shift_level(m, G, -1)
    for y in range(4, 29):
        c.px_on(15, y, G[4] if y < 15 else G[3])
        if 8 < y < 24:
            c.px_on(12, y, G[3])
    for (x, y) in ((19, 9), (20, 14), (18, 20)):
        c.px_on(x, y, G[1])
    over(img, canvas_rgba(c))
    sparks(img, [(7, 4), (25, 25), (26, 6)], '#ffffff', 230)
    return img


def spike():
    n = 32
    img = blank(n)
    c = Canvas(n)
    IV = ['#4a3f2e', '#a8977a', '#d2c4a4', '#ece2c8', '#fffaf0']
    hm = c.poly([(24.0, 3.2), (28.8, 8.0), (10.5, 25.0), (3.2, 28.8), (7.0, 21.5)])
    c.layer(hm, IV, shade='cyl', bulge=1.2)
    base = c.poly([(24.0, 3.2), (28.8, 8.0), (25.6, 11.2), (20.8, 6.4)])
    c.layer(base, ['#2a1c14', '#4a3424', '#6a4a34', '#8a6448', '#a8805e'], shade='cyl')
    for k in range(3):
        x, y = 22 - k * 4, 10 + k * 4
        c.px_on(x, y, IV[1])
        c.px_on(x + 1, y + 1, IV[1])
    over(img, halo(n, 15, '#fff6d8', 36))
    over(img, canvas_rgba(c))
    return img


def rock():
    n = 32
    c = Canvas(n)
    R = ['#1c1814', '#4a4038', '#6e6254', '#928474', '#b8aa98']
    hm = c.poly([(5, 13), (9, 6), (17, 4), (25, 7), (28, 14), (26, 23), (19, 28), (10, 27), (5, 21)])
    c.layer(hm, R, shade='dome', bulge=1.3)
    for path in (((10, 10), (14, 15), (13, 21)), ((14, 15), (21, 14), (24, 18)), ((19, 7), (21, 14))):
        for a, b in zip(path, path[1:]):
            c.line(a[0], a[1], b[0], b[1], R[1], only_on=True)
    for (x, y) in ((9, 9), (10, 8), (17, 6), (18, 6), (12, 12), (22, 10)):
        c.px_on(x, y, R[4])
    for (x, y) in ((22, 23), (16, 24), (8, 20)):
        c.px_on(x, y, '#5a7a3a')
    img = blank(n)
    over(img, canvas_rgba(c))
    sparks(img, [(3, 8), (29, 11), (27, 27), (3, 25)], '#6e6254', 255)
    return img


def blob(core, rim, alpha_rim, drops, hl, bubbles=None, seed=0):
    n = 32
    r, th, dx, dy = polar(n)
    R = 11.5 * (1 + wobble(th, seed, 0.12, (3, 5, 7)))
    t = np.clip(1 - r / R, 0, 1)
    pal = [(rim[0], alpha_rim), (rim[1], 255), (core[0], 255), (core[1], 255), (core[2], 255)]
    img = bands(t ** 0.7, pal)
    # light: shift top-left pixels lighter
    for (x, y) in ((11, 10), (12, 10), (11, 11), (10, 12), (13, 9)):
        put(img, x, y, hl, 255)
    put(img, 11, 9, '#ffffff', 255)
    for (x, y, s) in drops:
        for ox in range(s):
            for oy in range(s):
                put(img, x + ox, y + oy, core[1], 255)
        put(img, x, y, hl, 255)
    if bubbles:
        for (x, y) in bubbles:
            put(img, x, y, hl, 255)
            put(img, x + 1, y + 1, core[0], 255)
    return img


def water_jet():
    img = blob(['#1a5ab0', '#3a8ae0', '#7cc4ff'], ['#0e3a7a', '#1a5ab0'], 150,
               [(3, 6, 2), (27, 9, 2), (26, 25, 2), (5, 26, 1), (28, 17, 1), (15, 1, 1)], '#d8f0ff', seed=21)
    r, th, dx, dy = polar(32)
    ring = (np.abs(r - 13.5) < 0.8) & (np.sin(4 * th) > 0.2)
    img[ring, :3] = hx('#bfe6ff')
    img[ring, 3] = 140
    return img


def poison_spit():
    return blob(['#5a1a8a', '#8a3ac8', '#b878f0'], ['#2a0a4a', '#4a1a6a'], 160,
                [(3, 7, 2), (27, 5, 2), (28, 24, 2), (6, 27, 2), (16, 29, 1)], '#d8b8ff',
                bubbles=[(17, 18), (20, 13), (13, 20)], seed=33)


def mud():
    img = blob(['#4a3018', '#6a4626', '#8a6238'], ['#2a1a0c', '#3a2614'], 255,
               [(2, 9, 2), (27, 4, 3), (29, 22, 2), (7, 28, 2), (21, 28, 1)], '#a88058', seed=45)
    for (x, y) in ((18, 18), (21, 15), (14, 21), (20, 21)):
        put(img, x, y, '#2a1a0c')
    return img


def dragon_orb():
    n = 32
    r, th, dx, dy = polar(n)
    sw = np.sin(3 * th + r * 0.55)
    R = 13.0 * (1 + 0.07 * sw)
    t = np.clip(1 - r / R, 0, 1) ** 0.8
    img = bands(t, [('#3a0410', 90), ('#6a0a1a', 170), ('#a8122a', 235), ('#d82a40', 255), ('#1a0208', 255), ('#2c0410', 255)])
    core = (r < 4.6)
    img[core & (sw > 0.2), :3] = hx('#ff5a6a')
    img[core & (sw <= 0.2), :3] = hx('#c01a30')
    img[r < 1.8, :3] = hx('#ffd0d8')
    arms = (r > 5) & (r < 11.5) & (np.abs(sw) < 0.18)
    img[arms, :3] = hx('#ff4a5a')
    img[arms, 3] = 255
    sparks(img, [(4, 5), (27, 4), (29, 26), (3, 27)], '#ff4a5a', 220)
    return img


PROJECTILES = {'fireball': fireball, 'ice_shard': ice_shard, 'thunder_orb': thunder_orb, 'water_jet': water_jet,
               'dragon_orb': dragon_orb, 'rock': rock, 'poison_spit': poison_spit, 'mud': mud, 'spike': spike,
               'frost_orb': frost_orb, 'lava_blob': lava_blob}


# ----------------------------------------------------------------------------- trap decals (32x32 ground quads)
def trap_pitfall():
    n = 32
    c = Canvas(n)
    rim = c.circle(16, 16, 15.3)
    c.layer(rim, ['#1e140a', '#4a3418', '#664a26', '#7e6234', '#987a46'], shade='flat', bulge=0.5)
    img = canvas_rgba(c)
    r, th, dx, dy = polar(n)
    pit = r < 11.6
    img[pit, :3] = hx('#1a120a')
    img[r < 9.6, :3] = hx('#0e0905')
    img[r < 6.5, :3] = hx('#070403')
    edge = (r >= 11.6) & (r < 12.6)
    img[edge & (img[..., 3] > 0), :3] = hx('#2e2010')
    # rope net: two diagonal families of ropes, knots where they cross
    u, v = dx + dy, dx - dy
    rope = (r < 12.4) & ((np.abs(((u + 3) % 7) - 3.5) < 0.6) | (np.abs(((v + 3) % 7) - 3.5) < 0.6))
    img[rope, :3] = hx('#b49a62')
    img[rope & (dy < -2), :3] = hx('#ccb47a')
    knot = (r < 12.4) & (np.abs(((u + 3) % 7) - 3.5) < 0.8) & (np.abs(((v + 3) % 7) - 3.5) < 0.8)
    img[knot, :3] = hx('#e2cc94')
    # grass tufts + leaves scattered over the rim and the net
    rng = np.random.default_rng(5)
    for k in range(34):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(11.0, 15.0) if k < 28 else rng.uniform(3, 10)
        x, y = int(15.5 + math.cos(a) * rr), int(15.5 + math.sin(a) * rr)
        col = ['#2e5a18', '#447a22', '#5e9a30', '#7ab83e'][k % 4]
        put(img, x, y, col)
        if k % 3 == 0:
            put(img, x, y - 1, '#7ab83e')
    # wooden stakes holding the net
    for a in (45, 135, 225, 315):
        x = int(round(15.5 + math.cos(math.radians(a)) * 12.6)) - 1
        y = int(round(15.5 + math.sin(math.radians(a)) * 12.6)) - 1
        for ox, oy, col in ((0, 0, '#c08a50'), (1, 0, '#8a5a30'), (0, 1, '#8a5a30'), (1, 1, '#5a3a1c')):
            put(img, x + ox, y + oy, col)
    return img


def trap_shock():
    n = 32
    c = Canvas(n)
    base = c.circle(16, 16, 14.2)
    c.layer(base, ['#14171b', '#3a4048', '#5a626c', '#7a848e', '#a8b2bc'], shade='dome', bulge=0.8)
    img = canvas_rgba(c)
    r, th, dx, dy = polar(n)
    img[(r < 10.0) & (img[..., 3] > 0), :3] = hx('#2a2e34')
    img[(r < 9.0) & (img[..., 3] > 0), :3] = hx('#1e2226')
    coil = (np.abs(r - 6.2) < 1.0)
    img[coil, :3] = hx('#c87a2a')
    img[coil & (np.sin(12 * th) > 0.35), :3] = hx('#6a3a10')
    img[coil & (dy < -3) & (np.sin(12 * th) <= 0.35), :3] = hx('#e89a40')
    rivets = (np.abs(r - 12.4) < 0.6) & (np.cos(8 * th) > 0.92)
    img[rivets, :3] = hx('#dce3ea')
    # zigzag arcs between the electrodes
    rng = np.random.default_rng(9)
    for k in range(4):
        a0 = math.radians(45 + k * 90)
        prev = None
        for s_ in range(0, 9):
            a = a0 + math.radians(90) * s_ / 8
            rr = 10.0 + (1.2 if s_ % 2 else -0.4) + rng.uniform(-0.3, 0.3)
            pt = (15.5 + math.cos(a) * rr, 15.5 + math.sin(a) * rr)
            if prev is not None:
                steps = int(max(abs(pt[0] - prev[0]), abs(pt[1] - prev[1]))) + 1
                for q in range(steps + 1):
                    put(img, int(round(prev[0] + (pt[0] - prev[0]) * q / steps)), int(round(prev[1] + (pt[1] - prev[1]) * q / steps)), '#ffe55a')
            prev = pt
    # electrodes (3x3 posts)
    for a in (45, 135, 225, 315):
        x = int(round(15.5 + math.cos(math.radians(a)) * 10.4)) - 1
        y = int(round(15.5 + math.sin(math.radians(a)) * 10.4)) - 1
        for ox in range(3):
            for oy in range(3):
                col = '#dce3ea' if ox + oy == 0 else '#4f5761' if ox + oy >= 3 else '#a2acb7'
                put(img, x + ox, y + oy, col)
        put(img, x + 1, y + 1, '#fff6a0')
    for (x, y, col) in ((15, 15, '#fff6a0'), (16, 15, '#ffffff'), (15, 16, '#ffd22a'), (16, 16, '#fff6a0'), (14, 15, '#ffd22a'),
                        (17, 16, '#ffd22a'), (16, 13, '#ffe55a'), (15, 18, '#ffe55a'), (13, 14, '#ffe55a'), (18, 17, '#ffe55a')):
        put(img, x, y, col)
    return img


# ----------------------------------------------------------------------------- mob effect icons (18x18)
def eff_canvas():
    return Canvas(18)


def e_fire():
    c = eff_canvas()
    outer = c.poly([(9, 1.0), (12.4, 5.6), (14.6, 9.4), (14.2, 13.6), (11.4, 16.6), (6.6, 16.6), (3.8, 13.6), (3.6, 9.6),
                    (5.4, 6.4), (6.6, 9.2), (7.4, 5.0)])
    c.layer(outer, ['#5a0a00', '#c42a08', '#ee5a10', '#ff8a24', '#ffb84a'], shade='dome')
    inner = c.poly([(9.4, 6.4), (11.6, 10.4), (11.6, 13.6), (9.0, 15.4), (6.6, 13.8), (6.8, 11.0), (8.0, 11.8)])
    c.layer(inner, ['#c45a08', '#ffb030', '#ffd860', '#fff0a0', '#ffffff'], shade='dome', outline='soft')
    return c


def e_ice():
    c = eff_canvas()
    G = ['#174a6a', '#3c93c4', '#7cd0f2', '#c0f0ff', '#ffffff']
    hm = np.zeros_like(c.X, bool)
    for k in range(3):
        a = math.radians(90 + k * 60)
        hm |= c.stroke([(9 + math.cos(a) * 7.6, 9 + math.sin(a) * 7.6), (9 - math.cos(a) * 7.6, 9 - math.sin(a) * 7.6)], 2.0)
    hm |= c.circle(9, 9, 2.6)
    c.layer(hm, G, shade='flat', outline='auto')
    for k in range(6):
        a = math.radians(90 + k * 60)
        c.px_on(int(round(8.5 + math.cos(a) * 3.5)), int(round(8.5 + math.sin(a) * 3.5)), G[4])
    return c


def e_thunder():
    c = eff_canvas()
    hm = c.poly([(10.8, 0.8), (4.2, 10.0), (8.4, 10.0), (6.0, 17.2), (13.8, 7.0), (9.6, 7.0), (12.6, 0.8)])
    c.layer(hm, ['#5a4200', '#d8a800', '#ffe030', '#fff27a', '#ffffd0'], shade='flat')
    return c


def e_water():
    c = eff_canvas()
    hm = c.poly([(9, 1.0), (13.6, 8.6), (14.2, 12.0), (12.4, 15.6), (9, 16.8), (5.6, 15.6), (3.8, 12.0), (4.4, 8.6)])
    c.layer(hm, ['#0a2a5a', '#1a5ab0', '#3a8ae0', '#7cc4ff', '#d8f0ff'], shade='dome')
    c.px(7, 7, '#ffffff')
    c.px(6, 8, '#d8f0ff')
    return c


def e_dragon():
    from icons_lib import shield_pts
    c = eff_canvas()
    hm = c.poly(shield_pts(9, 9.2, 15.0, 17.0, 0, point=1.0))
    c.layer(hm, ['#1a0208', '#6a0a1a', '#a8122a', '#d82a40', '#ff7a88'], shade='dome', bulge=1.2)
    for (x0, y0, x1, y1) in ((5, 4, 8, 13), (8, 3, 11, 13), (11, 4, 13, 11)):
        c.line(x0, y0, x1, y1, '#14020a', only_on=True)
        c.line(x0 + 1, y0, x1 + 1, y1, '#4a0612', only_on=True)
    c.px_on(4, 3, '#ffb0b8')
    return c


def e_poison():
    c = eff_canvas()
    hm = c.ellipse(9, 7.4, 6.2, 5.6) | c.rect(5.6, 9.0, 12.4, 14.6, rad=1.2)
    c.layer(hm, ['#2a0a4a', '#6a2aa8', '#9a4ad8', '#c88af0', '#ecd8ff'], shade='dome')
    for (x, y) in ((6, 6), (7, 6), (6, 7), (7, 7), (10, 6), (11, 6), (10, 7), (11, 7)):
        c.px_on(x, y, '#1a0630')
    c.px_on(8, 9, '#1a0630')
    c.px_on(9, 9, '#1a0630')
    for x in (6, 8, 10, 12):
        c.px_on(x, 13, '#1a0630')
    c.px(15, 2, '#c88af0')
    c.px(2, 15, '#c88af0')
    return c


def e_paralysis():
    c = eff_canvas()
    hm = c.circle(9, 9, 5.4)
    c.layer(hm, ['#4a3a00', '#c8a010', '#f0d030', '#fff080', '#ffffd0'], shade='dome')
    for pts in (((1, 4), (3, 3), (2, 6), (4, 5)), ((14, 1), (16, 3), (14, 4), (16, 6)), ((13, 13), (16, 14), (14, 15), (17, 16)),
                ((1, 12), (3, 13), (1, 15), (3, 16))):
        for a, b in zip(pts, pts[1:]):
            c.line(a[0], a[1], b[0], b[1], '#ffe55a')
    for (x, y) in ((7, 7), (11, 7)):
        c.px_on(x, y, '#3a2a00')
    c.line(6, 11, 12, 11, '#3a2a00', only_on=True)
    c.px_on(7, 12, '#3a2a00')
    c.px_on(11, 10, '#3a2a00')
    return c


def e_sleep():
    c = eff_canvas()
    moon = c.circle(7.0, 10.6, 6.2) & ~c.circle(9.8, 8.4, 5.2)
    c.layer(moon, ['#1a2a5a', '#4a6ac0', '#7a9ae8', '#b0c8ff', '#e8f0ff'], shade='dome')
    for (x0, y0, s_) in ((11, 1, 5), (14, 8, 3)):
        c.line(x0, y0, x0 + s_ - 1, y0, '#ffffff')
        for q in range(1, s_ - 1):
            c.px(x0 + s_ - 1 - q, y0 + q, '#ffffff')
        c.line(x0, y0 + s_ - 1, x0 + s_ - 1, y0 + s_ - 1, '#ffffff')
    c.outline_all('#1a2a5a')
    return c


def e_stun():
    from icons_lib import star_pts
    c = eff_canvas()
    for (cx, cy, rr) in ((9, 4.6, 4.2), (4.2, 12.6, 3.6), (13.8, 12.6, 3.6)):
        c.layer(c.poly(star_pts(cx, cy, rr, rr * 0.45)), ['#5a4200', '#d8a800', '#ffe030', '#fff27a', '#ffffd0'], shade='flat')
    return c


def e_roar():
    c = eff_canvas()
    rp = ['#2a2a2a', '#8a8a8a', '#c8c8c8', '#e8e8e8', '#ffffff']
    mouth = c.poly([(1.5, 6), (6.5, 7.5), (6.5, 10.5), (1.5, 12)])
    c.layer(mouth, rp, shade='flat')
    for k, rr in enumerate((5.5, 9.0, 12.5)):
        arc = (np.abs(np.hypot(c.X - 3.0, c.Y - 9.0) - rr) < 0.8) & (np.abs(np.arctan2(c.Y - 9.0, c.X - 3.0)) < 0.75)
        c.layer(arc, rp, shade='flat')
    return c


def bottle_icon(liquid, mark):
    c = eff_canvas()
    body = c.circle(9, 11.2, 5.6) | c.rect(7.0, 2.4, 11.0, 7.4)
    c.layer(body, ['#2a3640', '#7f98a8', '#b8d0dc', '#e2f0f6', '#ffffff'], shade='dome', levels=(1, 3))
    liq = c.cells(c.circle(9, 11.6, 4.4), 0.5)
    c.recolor(liq, liquid[2])
    c.recolor(liq & c.cells(c.rect(0, 13.0, 18, 18)), liquid[1])
    c.layer(c.rect(6.6, 0.8, 11.4, 3.0, rad=0.6), ['#2a1a0c', '#6a4626', '#8a6238', '#a88058', '#c8a078'], shade='flat')
    for (x, y, col) in mark:
        c.px_on(x, y, col)
    c.px_on(6, 9, '#ffffff')
    return c


def e_cool():
    return bottle_icon(['#0a2a4a', '#2a8ad0', '#5ac8f0'], [(9, 10, '#ffffff'), (8, 11, '#ffffff'), (10, 11, '#ffffff'),
                                                          (9, 12, '#ffffff'), (9, 11, '#c0f0ff')])


def e_hot():
    return bottle_icon(['#4a0a0a', '#c02a1a', '#f04a2a'], [(9, 9, '#ffd860'), (8, 11, '#ffd860'), (9, 10, '#ffb030'),
                                                          (10, 10, '#ffb030'), (9, 11, '#fff0a0'), (10, 12, '#ffb030')])


def e_demon():
    c = eff_canvas()
    hm = c.circle(9, 12, 5.0) | c.circle(9, 6.0, 3.0) | c.rect(7.8, 1.6, 10.2, 4.0)
    c.layer(hm, ['#3a0606', '#8a1414', '#c82020', '#f05a4a', '#ffc0b0'], shade='dome')
    c.layer(c.rect(6.4, 7.6, 11.6, 8.8), ['#2a1a0c', '#6a4626', '#8a6238', '#a88058', '#c8a078'], shade='flat')
    for (x, y) in ((7, 11), (8, 10), (9, 11), (10, 10), (11, 11)):
        c.px_on(x, y, '#ffd0a0')
    return c


def e_armor():
    from icons_lib import shield_pts
    c = eff_canvas()
    hm = c.poly(shield_pts(9, 9.4, 13.4, 16.4, 0, point=1.0))
    c.layer(hm, ['#4a2a00', '#b86a10', '#e89a20', '#ffc860', '#fff0c0'], shade='dome', bulge=1.2)
    inner = c.cells(c.poly(shield_pts(9, 9.6, 8.0, 10.4, 0, point=0.95)), 0.6)
    c.shift_level(inner & (c.img[..., 3] > 0), ['#4a2a00', '#b86a10', '#e89a20', '#ffc860', '#fff0c0'], -1)
    for y in range(5, 14):
        c.px_on(9, y, '#fff0c0')
    return c


EFFECTS = {'fireblight': e_fire, 'iceblight': e_ice, 'thunderblight': e_thunder, 'waterblight': e_water,
           'dragonblight': e_dragon, 'deadly_poison': e_poison, 'paralysis': e_paralysis, 'sleep': e_sleep, 'stun': e_stun,
           'roar': e_roar, 'cool_drink': e_cool, 'hot_drink': e_hot, 'demondrug': e_demon, 'armorskin': e_armor}


# ----------------------------------------------------------------------------- barrel bomb block textures (16x16)
def barrel_textures(body, hoop, mark):
    """Side / top / bottom of a barrel occupying x 2..13 (12 px) and y 1..15 (15 px) of the side texture."""
    side = blank(16)
    staves = [body[1], body[2], body[3], body[2], body[2], body[3], body[2], body[1]]
    for x in range(2, 14):
        k = x - 2
        col = body[2]
        if k in (0, 11):
            col = body[0]
        elif k in (1, 10):
            col = body[1]
        elif k in (4, 5):
            col = body[3]
        for y in range(1, 16):
            put(side, x, y, col)
        if k % 3 == 2 and 0 < k < 11:
            for y in range(1, 16):
                put(side, x, y, body[1])
    for y in (2, 3, 13, 14):
        for x in range(2, 14):
            c = hoop[2] if y in (2, 13) else hoop[1]
            if x in (2, 13):
                c = hoop[0]
            put(side, x, y, c)
    for x in (4, 9):
        put(side, x, 2, hoop[3])
        put(side, x, 13, hoop[3])
    for (x, y, col) in mark:
        put(side, x, y, col)
    top = blank(16)
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            col = body[2] if (x + (y // 4)) % 3 else body[1]
            if d > 5.2:
                col = hoop[1] if d < 6.3 else hoop[0]
            put(top, x, y, col)
    for y in range(3, 13):
        put(top, 7, y, body[1])
    for (x, y, col) in ((7, 7, '#3a3a3a'), (8, 7, '#5a5a5a'), (7, 8, '#2a2a2a'), (8, 8, '#3a3a3a'), (9, 6, '#d8c8a0'),
                        (10, 5, '#d8c8a0'), (11, 4, '#ffb030'), (11, 3, '#fff0a0')):
        put(top, x, y, col)
    bottom = blank(16)
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            col = body[1] if (x // 3) % 2 else body[0]
            if d > 5.2:
                col = hoop[0]
            put(bottom, x, y, col)
    return side, top, bottom


def barrel_blocks():
    red = ['#4a0e08', '#8a1e12', '#b8301c', '#d8502c']
    iron = ['#1e2126', '#4f5761', '#76808b', '#a2acb7']
    skull = [(6, 6, '#e8e0c8'), (7, 6, '#e8e0c8'), (8, 6, '#e8e0c8'), (9, 6, '#e8e0c8'), (5, 7, '#e8e0c8'), (6, 7, '#1a0a06'),
             (7, 7, '#e8e0c8'), (8, 7, '#e8e0c8'), (9, 7, '#1a0a06'), (10, 7, '#e8e0c8'), (5, 8, '#e8e0c8'), (6, 8, '#e8e0c8'),
             (7, 8, '#e8e0c8'), (8, 8, '#e8e0c8'), (9, 8, '#e8e0c8'), (10, 8, '#e8e0c8'), (6, 9, '#e8e0c8'), (7, 9, '#1a0a06'),
             (8, 9, '#1a0a06'), (9, 9, '#e8e0c8'), (6, 10, '#c8c0a8'), (7, 10, '#e8e0c8'), (8, 10, '#e8e0c8'), (9, 10, '#c8c0a8')]
    s, t, b = barrel_textures(red, iron, skull)
    OUT['block/barrel_bomb_side'], OUT['block/barrel_bomb_top'], OUT['block/barrel_bomb_bottom'] = s, t, b
    dark = ['#2a0606', '#5a0c0c', '#7a1414', '#a02a1c']
    gold = ['#3a2508', '#8a5f1e', '#d9aa4c', '#f5d88a']
    flame = [(7, 5, '#ffd860'), (8, 6, '#ffd860'), (6, 7, '#ff8a24'), (7, 7, '#ffd860'), (8, 7, '#fff0a0'), (9, 7, '#ff8a24'),
             (6, 8, '#ff8a24'), (7, 8, '#ffd860'), (8, 8, '#ffd860'), (9, 8, '#ff8a24'), (5, 9, '#ee5a10'), (6, 9, '#ff8a24'),
             (7, 9, '#ffd860'), (8, 9, '#ffd860'), (9, 9, '#ff8a24'), (10, 9, '#ee5a10'), (6, 10, '#ee5a10'), (7, 10, '#ff8a24'),
             (8, 10, '#ff8a24'), (9, 10, '#ee5a10'), (7, 11, '#c42a08'), (8, 11, '#c42a08')]
    s, t, b = barrel_textures(dark, gold, flame)
    OUT['block/barrel_bomb_plus_side'], OUT['block/barrel_bomb_plus_top'], OUT['block/barrel_bomb_plus_bottom'] = s, t, b


def write_block_json():
    for bid in ('barrel_bomb', 'barrel_bomb_plus'):
        bs = {'variants': {'': {'model': 'huntcraft:block/' + bid}}}
        model = {
            'parent': 'minecraft:block/block',
            'ambientocclusion': False,
            'textures': {'particle': 'huntcraft:block/%s_side' % bid, 'side': 'huntcraft:block/%s_side' % bid,
                         'top': 'huntcraft:block/%s_top' % bid, 'bottom': 'huntcraft:block/%s_bottom' % bid},
            'elements': [{
                'from': [2, 0, 2], 'to': [14, 15, 14],
                'faces': {
                    'down': {'uv': [2, 2, 14, 14], 'texture': '#bottom', 'cullface': 'down'},
                    'up': {'uv': [2, 2, 14, 14], 'texture': '#top'},
                    'north': {'uv': [2, 1, 14, 16], 'texture': '#side'},
                    'south': {'uv': [2, 1, 14, 16], 'texture': '#side'},
                    'west': {'uv': [2, 1, 14, 16], 'texture': '#side'},
                    'east': {'uv': [2, 1, 14, 16], 'texture': '#side'},
                }}],
        }
        for rel, data in (('blockstates/%s.json' % bid, bs), ('models/block/%s.json' % bid, model)):
            p = os.path.join(ASSETS, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=1)
                f.write('\n')


# ----------------------------------------------------------------------------- output
def preview(path):
    groups = [('entity/projectile/' + k, 8) for k in PROJECTILES] + [('entity/trap_pitfall', 8), ('entity/trap_shock', 8)] + \
             [('mob_effect/' + k, 12) for k in EFFECTS] + [(k, 12) for k in sorted(OUT) if k.startswith('block/')]
    cell = 280
    cols = 6
    rows = (len(groups) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 14)), (40, 44, 52))
    d = ImageDraw.Draw(sheet)
    for k, (name, sc) in enumerate(groups):
        a = OUT[name]
        im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
        big = im.resize((im.width * sc, im.height * sc), Image.NEAREST)
        x, y = (k % cols) * cell, (k // cols) * (cell + 14)
        bg = Image.new('RGB', big.size, (88, 110, 70) if 'trap' in name else (24, 26, 30) if 'projectile' in name else (70, 74, 84))
        bg.paste(big, (0, 0), big)
        sheet.paste(bg, (x + (cell - big.width) // 2, y + (cell - big.height) // 2))
        d.text((x + 4, y + cell), name.split('/')[-1], fill=(230, 230, 230))
    sheet.save(path)


def main():
    for k, fn in PROJECTILES.items():
        OUT['entity/projectile/' + k] = fn()
    OUT['entity/trap_pitfall'] = trap_pitfall()
    OUT['entity/trap_shock'] = trap_shock()
    for k, fn in EFFECTS.items():
        OUT['mob_effect/' + k] = canvas_rgba(fn())
    barrel_blocks()
    hd = '--1x' not in sys.argv
    for rel, a in OUT.items():
        p = os.path.join(ASSETS, 'textures', rel + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        if hd:
            # HD: blocks x4 (64x64), projectiles / traps / effect icons x2 (edge-aware upscale, see hdpost)
            from hdpost import hd_upscale_array
            K = 4 if rel.startswith('block/') else 2
            translucent = rel.startswith('entity/')
            a = hd_upscale_array(a, K, round_alpha=not translucent, grain_amp=0.035 if rel.startswith('block/') else 0.02,
                                 seed=abs(hash(rel)) % 1000)
        Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8), 'RGBA').save(p)
    write_block_json()
    os.makedirs(PREVIEW, exist_ok=True)
    preview(os.path.join(PREVIEW, 'sprites.png'))
    print('sprites:', len(OUT))


if __name__ == '__main__':
    main()
