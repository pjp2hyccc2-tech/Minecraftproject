"""Particle textures (textures/particle/<id>_<n>.png) and particle definitions (particles/<id>.json) of REISATSU.

Frame counts (the game's providers use the SpriteSet: setSpriteFromAge for animations, pickSprite for variants):
  menu_flutter 4 (tumbling, cycle them), water_drop 2 (falling, splash), wet_print 2 (left / right foot: pick),
  ember 4 (age: bright -> dim), spirit_mote 4 (age), frost_breath 4 (age: puff grows and thins), paper_ash 4 (age)."""
import math

import numpy as np

from common import C, ramp, rng, grid, texture
from rs_lib import blank, rect, px, hline, vline, line, frame, disc_mask, ring_mask, over, dl, tint, noise, sun_small

PARTICLES = {}


def particle(pid, frames, size=16):
    def deco(fn):
        names = []
        for i in range(frames):
            name = f'{pid}_{i}' if frames > 1 else pid
            texture('particle/' + name, size=(size, size))(lambda _i=i: fn(_i))
            names.append(f'reisatsu:{name}')
        PARTICLES[pid] = {'textures': names}
        return fn
    return deco


def soft_dot(w, h, cx, cy, r, color, power=2.0):
    x, y = grid(w, h)
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
    a = blank(w, h, color)
    a[..., 3] = np.clip(1 - d, 0, 1) ** power * 255
    return a


@particle('menu_flutter', 4, 16)
def menu_flutter(i):
    a = blank(16, 16)
    # a laminated menu seen flat, tilted, edge-on, tilted back (tumbling)
    if i == 0:
        rect(a, 3, 1, 12, 14, '#e8721f')
        rect(a, 3, 1, 12, 2, '#5a2e1a')
        over(a, sun_small(7), 5, 4)
        rect(a, 4, 12, 11, 12, '#fff4e0')
        frame(a, 3, 1, 12, 14, '#3a2010')
    elif i == 1:
        for y in range(3, 13):
            x0 = 3 + (y - 3) // 3
            x1 = 12 - (12 - y) // 4
            hline(a, x0, x1, y, '#e8721f' if y > 4 else '#5a2e1a')
        line(a, 3, 3, 6, 12, '#3a2010')
        line(a, 12, 3, 12, 12, '#3a2010')
        px(a, 8, 7, '#fbc03a')
    elif i == 2:
        rect(a, 2, 7, 13, 8, '#f4ead8')
        hline(a, 2, 13, 7, '#e8721f')
    else:
        for y in range(4, 12):
            x0 = 4 - (y - 4) // 4
            x1 = 11 + (y - 4) // 4
            hline(a, x0, x1, y, '#f4ead8')
        rect(a, 5, 6, 10, 7, '#2a1a12')
        rect(a, 6, 6, 9, 6, '#6a3418')
        hline(a, 5, 9, 9, '#8a7a60')
        frame(a, 3, 4, 12, 11, '#5a2e1a')
    return a


@particle('water_drop', 2, 8)
def water_drop(i):
    a = blank(8, 8)
    if i == 0:
        a[disc_mask(8, 8, 4, 5, 1.8, 2.3)] = C('#b8d8e8', 220)
        px(a, 4, 2, '#d8ecf4')
        px(a, 3, 4, '#ffffff')
    else:
        for (x, y) in ((1, 5), (6, 5), (2, 3), (5, 3), (3, 6), (4, 6)):
            px(a, x, y, C('#c8e4f0', 210))
    return a


@particle('wet_print', 2, 16)
def wet_print(i):
    """A wet shoe print (flat on the ground, fades): dark translucent, tread marks. The game draws it on a 0.6 : 1
    quad, so the print fills the sprite's width (it is squeezed to a shoe's proportions)."""
    a = blank(16, 16)
    x, y = grid(16, 16)
    sole = ((x - 7.5) / 6.6) ** 2 + ((y - 5.2) / 5.0) ** 2 < 1.0
    heel = ((x - 7.5) / 5.4) ** 2 + ((y - 12.4) / 2.8) ** 2 < 1.0
    m = sole | heel
    a[m] = C('#0e1418', 150)
    tread = m & ((y.astype(int) % 2) == 0)
    a[tread, 3] = 185
    hl = m & (np.abs(x - 6) < 0.6) & (y < 6)
    a[hl] = C('#3a4a52', 170)
    if i == 1:
        a = a[:, ::-1].copy()
    return a


@particle('ember', 4, 8)
def ember(i):
    r_ = [2.2, 1.8, 1.4, 1.0][i]
    cols = ['#ffd890', '#ffb050', '#ff7a2a', '#c8401a']
    a = soft_dot(8, 8, 4, 4, r_ + 1.2, cols[i], power=1.4)
    a[disc_mask(8, 8, 4, 4, max(0.6, r_ * 0.6))] = C('#fff2c8' if i < 2 else cols[i])
    return a


@particle('spirit_mote', 4, 8)
def spirit_mote(i):
    r_ = [3.0, 2.6, 2.0, 1.4][i]
    a = soft_dot(8, 8, 4, 4, r_ + 0.8, '#dce8ff', power=1.6)
    a[disc_mask(8, 8, 4, 4, 0.9)] = C('#ffffff', 240)
    return a


@particle('frost_breath', 4, 16)
def frost_breath(i):
    r = rng(17001 + i)
    a = blank(16, 16, '#e8f0f4')
    x, y = grid(16, 16)
    n = noise(16, 16, 17002 + i, beta=1.4, wrap=False)
    rad = [3.5, 5.0, 6.3, 7.4][i]
    d = np.hypot(x - 7.5, y - 7.5) / rad + n * 0.18
    a[..., 3] = np.clip(1 - d, 0, 1) ** 1.2 * [200, 160, 110, 60][i]
    return a


@particle('paper_ash', 4, 8)
def paper_ash(i):
    r = rng(17101 + i)
    a = blank(8, 8)
    pts = [(2, 3), (3, 3), (4, 2), (5, 3), (3, 4), (4, 4), (5, 4), (4, 5), (2, 5)]
    shrink = i
    for k, (x, y) in enumerate(pts[:len(pts) - shrink * 2]):
        a[y, x] = C('#1a1614' if k % 3 else '#3a3430')
    if i < 3:
        edge = pts[len(pts) - shrink * 2 - 1]
        a[edge[1], edge[0]] = C('#ff8a2a' if i < 2 else '#c84a1a')
    if i == 0:
        px(a, 5, 2, '#e8dcc0')
    return a
