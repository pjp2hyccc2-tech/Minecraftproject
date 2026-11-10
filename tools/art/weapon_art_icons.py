"""Skill icons of the generated weapon arts (16x16): the weapon-skill frame with the art's primary shape drawn in
its colours and a touch of its accent colour. Called by weapon_arts.py; one icon per art."""
from __future__ import annotations

import math
import os

import numpy as np

import skills
from px import Canvas, mix

N = 12  # the symbol area inside the frame (offset 2, 2)


def _grid():
    return np.zeros((N, N), dtype=bool)


def _disc(m, cx, cy, r, inner=-1.0):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if inner < d <= r:
                m[y, x] = True


def _line(m, x0, y0, x1, y1, w=0.6):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for i in range(steps + 1):
        t = i / steps
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for yy in range(N):
            for xx in range(N):
                if math.hypot(xx - x, yy - y) <= w:
                    m[yy, xx] = True


def symbol(shape: str):
    """(main mask, accent mask) of a shape."""
    m, a = _grid(), _grid()
    c = 5.5
    if shape == "RING":
        _disc(m, c, c, 5.2, 3.8)
        _disc(a, c, c, 1.2)
    elif shape == "BURST":
        for k in range(8):
            ang = k * math.pi / 4
            _line(m, c + math.cos(ang) * 2, c + math.sin(ang) * 2, c + math.cos(ang) * 5.4, c + math.sin(ang) * 5.4, 0.55)
        _disc(a, c, c, 1.6)
    elif shape == "CRESCENT":
        _disc(m, c, c, 5.4, -1)
        inner = _grid()
        _disc(inner, c + 2.2, c - 1.2, 4.6, -1)
        m &= ~inner
        _disc(a, 2.5, 8.5, 0.9)
    elif shape == "ARC":
        for k in range(3):
            for i in range(14):
                ang = math.pi * (0.9 + 0.8 * i / 13)
                r = 5.2 - k * 1.6
                (m if k < 2 else a)[min(N - 1, max(0, int(round(c + math.sin(ang) * r + 1)))),
                                    min(N - 1, max(0, int(round(c + math.cos(ang) * r + 2))))] = True
    elif shape == "LINE":
        _line(m, 1, 10, 10, 1, 0.7)
        _disc(a, 10, 1, 1.0)
    elif shape == "HELIX":
        for i in range(40):
            t = i / 39
            x, y = 1 + 9 * t, 10 - 9 * t
            off = math.sin(t * math.pi * 4) * 1.8
            (m if i % 2 else a)[int(round(y + off * 0.7)) % N, int(round(x + off * 0.7)) % N] = True
        _line(m, 1, 10, 10, 1, 0.3)
    elif shape == "CHAIN":
        for k in range(4):
            x, y = 2 + k * 2.5, 9 - k * 2.5
            _disc(m if k % 2 == 0 else a, x, y, 1.4, 0.6)
    elif shape == "SPIKES":
        for k, h in enumerate((6, 9, 7, 10, 5)):
            x = 1 + k * 2.4
            _line(m, x, 11, x + 0.4, 11 - h, 0.6)
        _line(a, 0, 11, 11, 11, 0.5)
    elif shape == "PILLAR":
        _line(m, 5.5, 0, 5.5, 11, 1.4)
        _disc(a, 5.5, 10.5, 2.4, 1.4)
    elif shape == "RAIN":
        for k in range(6):
            x = 1 + (k * 1.9) % 10
            y = (k * 3.7) % 7
            _line(m if k % 2 == 0 else a, x + 1.5, y, x, y + 4, 0.45)
    elif shape == "WAVE":
        for row, col in ((3, m), (6, a), (9, m)):
            for x in range(N):
                y = row + math.sin(x * 0.9) * 1.2
                col[int(round(y)) % N, x] = True
    elif shape == "FAN":
        for k in range(5):
            ang = math.radians(8 + 18.5 * k)
            _line(m if k % 2 == 0 else a, 1, 10, 1 + math.cos(ang) * 9.5, 10 - math.sin(ang) * 9.5, 0.45)
        _disc(a, 1, 10, 1.2)
    elif shape == "SPIRAL":
        for i in range(60):
            t = i / 59
            ang = t * math.pi * 4
            r = 0.5 + t * 5
            (m if t > 0.3 else a)[int(round(c + math.sin(ang) * r)) % N, int(round(c + math.cos(ang) * r)) % N] = True
    elif shape == "VORTEX":
        for arm in range(3):
            for i in range(20):
                t = i / 19
                ang = arm * 2.1 + t * 3.5
                r = 5.3 * (1 - t) + 0.3
                (m if arm < 2 else a)[int(round(c + math.sin(ang) * r)) % N, int(round(c + math.cos(ang) * r)) % N] \
                    = True
    elif shape == "STAR":
        pts = [(c + math.cos(math.pi / 2 + k * 2 * math.pi / 5) * 5.2, c - math.sin(math.pi / 2 + k * 2 * math.pi / 5)
                * 5.2) for k in range(5)]
        for k in range(5):
            x0, y0 = pts[(k * 2) % 5]
            x1, y1 = pts[(k * 2 + 2) % 5]
            _line(m, x0, y0, x1, y1, 0.5)
        _disc(a, c, c, 1.0)
    elif shape == "SIGIL":
        _disc(m, c, c, 5.4, 4.5)
        pts = [(c + math.cos(-math.pi / 2 + k * 2 * math.pi / 3) * 4, c + math.sin(-math.pi / 2 + k * 2 * math.pi / 3)
                * 4) for k in range(3)]
        for k in range(3):
            _line(a, *pts[k], *pts[(k + 1) % 3], 0.45)
    elif shape == "CROSS":
        _line(m, 1, 1, 10, 10, 0.7)
        _line(a, 1, 10, 10, 1, 0.7)
    elif shape == "PETALS":
        for k in range(6):
            ang = k * math.pi / 3
            _disc(m if k % 2 == 0 else a, c + math.cos(ang) * 3.3, c + math.sin(ang) * 3.3, 1.7)
        _disc(a, c, c, 1.0)
    elif shape == "ORBS":
        for k, (x, y, r) in enumerate(((3, 3, 2.2), (8.5, 4, 1.8), (5, 8.5, 2.0))):
            _disc(m, x, y, r)
            _disc(a, x - 0.6, y - 0.6, 0.7)
    elif shape == "DOME":
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - c, y - 10)
                if 3.8 < d <= 5.6 and y <= 10:
                    m[y, x] = True
        _line(a, 0, 10.5, 11, 10.5, 0.5)
    elif shape == "COMET":
        _line(m, 1, 10, 7, 4, 0.5)
        _line(m, 0, 8, 6, 3, 0.35)
        _disc(a, 8.3, 2.8, 2.2)
    elif shape == "SCATTER":
        for k in range(10):
            x, y = (k * 4.3 + 1) % 11, (k * 7.1 + 2) % 11
            (m if k % 3 else a)[int(y), int(x)] = True
            m[int(y), min(N - 1, int(x) + 1)] = k % 2 == 0
    elif shape == "GEYSER":
        for x in (2.5, 5.5, 8.5):
            _line(m, x, 11, x, 3 + (x % 3), 0.7)
            _disc(a, x, 2.5 + (x % 3), 1.1)
    elif shape == "CAGE":
        for x in (1.5, 4.0, 7.0, 9.5):
            _line(m, x, 1, x, 10, 0.4)
        _line(a, 0.5, 1, 10.5, 1, 0.5)
        _line(a, 0.5, 10.5, 10.5, 10.5, 0.5)
    else:
        raise ValueError(shape)
    return m, a & ~m | (a & m)


def ramp_of(rgb: int):
    base = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
    return [mix((0, 0, 0, 255), base, 0.45), mix((0, 0, 0, 255), base, 0.75), base,
            mix(base, (255, 255, 255, 255), 0.35), mix(base, (255, 255, 255, 255), 0.7)]


def icon(primary: str, rgb: int, accent_rgb: int) -> Canvas:
    c = skills.frame("weapon")
    main, accent = symbol(primary)
    ramp, aramp = ramp_of(rgb), ramp_of(accent_rgb)
    shadow = np.zeros_like(main)
    shadow[1:, 1:] = (main | accent)[:-1, :-1]
    for y in range(N):
        for x in range(N):
            px, py = x + 2, y + 2
            if shadow[y, x] and not (main[y, x] or accent[y, x]):
                c.put(px, py, mix(c.get(px, py), (0, 0, 0, 255), 0.5))
            if main[y, x]:
                # light from the upper left
                shade = 3 if (x + (N - y)) > N else 2
                edge = not (0 < x < N - 1 and 0 < y < N - 1 and main[y - 1, x] and main[y, x - 1])
                c.put(px, py, ramp[4] if edge and x + y < 8 else ramp[shade])
            if accent[y, x]:
                c.put(px, py, aramp[3])
    return c


def write(rows, assets_dir: str) -> int:
    count = 0
    for art_id, primary, rgb, accent_rgb in rows:
        path = os.path.join(assets_dir, "textures/gui/skill", f"{art_id}.png")
        icon(primary, rgb, accent_rgb).save(path)
        count += 1
    return count
