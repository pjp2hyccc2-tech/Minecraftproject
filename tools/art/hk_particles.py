"""Particle textures (textures/particle/<id>[_<n>].png, 8x8 like vanilla) and particle definitions
(particles/<id>.json) of HITORI (DESIGN §7 Particles).

Frame use (the game's providers): rice_grain 4 (pick a random grain), stuffing 4 (pick), water_drip 3 (age: falling
drop -> splash -> ring), salt_spray 4 (age: dense spray -> fine mist), ember 4 (age: bright -> dim), smoke 4 (age:
puff grows and thins), tv_static_mote 4 (cycle quickly: flickering black / white specks)."""
import math

import numpy as np

from common import C, ramp, rng, grid, texture
from hk_lib import blank, rect, px, hline, vline, line, frame, disc_mask, ring_mask, over, dl, tint, noise

PARTICLES = {}
S = 8


def particle(pid, frames, size=S):
    def deco(fn):
        names = []
        for i in range(frames):
            name = f'{pid}_{i}' if frames > 1 else pid
            texture('particle/' + name, size=(size, size))(lambda _i=i: fn(_i))
            names.append(f'hitori:{name}')
        PARTICLES[pid] = {'textures': names}
        return fn
    return deco


def soft(w, h, cx, cy, r, color, power=1.6, alpha=255):
    x, y = grid(w, h)
    d = np.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
    a = blank(w, h, color)
    a[..., 3] = np.clip(1 - d, 0, 1) ** power * alpha
    return a


@particle('rice_grain', 4)
def rice_grain(i):
    """A single polished grain of rice (white, a little translucent at the tip), in four orientations."""
    a = blank(S, S)
    shapes = [((3, 3), (4, 3), (3, 4), (4, 4), (4, 5)), ((2, 4), (3, 4), (4, 4), (3, 3)),
              ((3, 3), (3, 4), (4, 4), (4, 5), (5, 5)), ((3, 3), (4, 3), (5, 3), (4, 4))]
    for k, (x, y) in enumerate(shapes[i]):
        a[y, x] = C('#f4f2ea' if k else '#ffffff')
    x, y = shapes[i][-1]
    a[y, x] = C('#d8d4c4')
    return a


@particle('stuffing', 4)
def stuffing(i):
    """Tufts of polyester stuffing: white, fluffy, irregular (grey shading)."""
    r = rng(12101 + i)
    a = blank(S, S)
    for _ in range(4 + i):
        cx, cy = r.uniform(2, 6), r.uniform(2, 6)
        rr = r.uniform(1.2, 2.2)
        m = disc_mask(S, S, cx, cy, rr)
        a[m] = C('#ecebe6')
    x, y = grid(S, S)
    edge = (a[..., 3] > 0) & ~((np.roll(a[..., 3], -1, 0) > 0) & (np.roll(a[..., 3], -1, 1) > 0))
    a[edge] = C('#bdbab2')
    px(a, 3, 3, '#ffffff')
    return a


@particle('water_drip', 3)
def water_drip(i):
    """0: a falling drop (bright highlight), 1: the splash crown, 2: the ring spreading."""
    a = blank(S, S)
    if i == 0:
        for (x, y, c) in ((3, 2, '#b8d4e0'), (3, 3, '#9ac0d0'), (4, 3, '#7aa4b8'), (3, 4, '#7aa4b8'),
                          (4, 4, '#5a8498'), (3, 1, '#d8eaf0')):
            a[y, x] = C(c, 220)
        a[2, 3] = C('#ffffff', 240)
    elif i == 1:
        for (x, y) in ((1, 5), (2, 4), (3, 3), (4, 3), (5, 4), (6, 5), (2, 6), (5, 6), (3, 6), (4, 6)):
            a[y, x] = C('#a8cad8', 200)
        a[3, 3] = C('#ffffff', 230)
    else:
        m = ring_mask(S, S, 4, 5, 2.0, 3.0)
        a[m] = C('#9ac0d0', 150)
    return a


@particle('salt_spray', 4)
def salt_spray(i):
    """Salt water spat out: a dense cloudy spray with bright droplets, thinning to a fine mist (age)."""
    r = rng(12301 + i)
    a = soft(S, S, 4, 4, 3.6 - i * 0.4, '#e8ecea', 1.4, 180 - i * 35)
    for _ in range(7 - i):
        x, y = int(r.integers(0, S)), int(r.integers(0, S))
        a[y, x] = C('#ffffff', 230 - i * 30)
    return a


@particle('ember', 4)
def ember(i):
    """A burning flake from the doll: white-yellow core -> orange -> dull red -> grey ash (age)."""
    cols = [('#fff4c0', '#ffb03a'), ('#ffc860', '#e8661a'), ('#e8661a', '#8a2a10'), ('#6a5a50', '#3a3230')]
    core, rim = cols[i]
    a = blank(S, S)
    sz = [2, 2, 2, 1][i]
    rect(a, 3, 3, 3 + sz - 1, 3 + sz - 1, rim)
    a[3, 3] = C(core)
    if i < 2:
        g = soft(S, S, 4, 4, 3.5, rim, 2.0, 90)
        a = over(g, a)
    return a


@particle('smoke', 4)
def smoke(i):
    """Grey smoke puff growing and thinning with age (lit from the top-left)."""
    r = rng(12501 + i)
    rad = 2.0 + i * 0.6
    a = soft(S, S, 4, 4, rad, '#5a5a5c', 1.0, 210 - i * 40)
    x, y = grid(S, S)
    hl = soft(S, S, 3.4, 3.2, rad * 0.6, '#8a8a8c', 1.4, 120 - i * 20)
    a = over(a, hl)
    for _ in range(3):
        xx, yy = int(r.integers(1, 7)), int(r.integers(1, 7))
        if a[yy, xx, 3] > 40:
            a[yy, xx, :3] *= 0.85
    return a


@particle('tv_static_mote', 4)
def tv_static_mote(i):
    """Flickering specks of TV snow floating off the screen: a scatter of single black and white pixels."""
    r = rng(12601 + i)
    a = blank(S, S)
    for _ in range(5):
        x, y = int(r.integers(1, 7)), int(r.integers(1, 7))
        a[y, x] = C('#f0f4f8' if r.random() < 0.6 else '#101214', 230)
    a[int(r.integers(2, 6)), int(r.integers(2, 6))] = C('#a8c8ff', 200)
    return a
