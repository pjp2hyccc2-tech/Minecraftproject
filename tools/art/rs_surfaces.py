"""Surface / structure textures of HIDAMARI (plain block models made by the world's gen_assets.py) and the door
textures (vanilla-style doors: <id>_bottom / <id>_top at 32x32 = 16 px art doubled + 2x signage; the auto door
panel 16x48 and the kitchen swing door 16x32 drawn by the world's block entity renderers)."""
import math

import numpy as np
from scipy import ndimage

from common import texture, C, ramp, pal, fnoise, vnoise, rng, grid, lighten_arr, bayer, smoothstep
from rs_lib import (blank, from_idx, noise, dl, dl_field, tint, over, rect, px, hline, vline, line, frame, bevel,
                    wear, specks, grime, disc_mask, ring_mask, text_mask, text35, stamp_mask, stamp_center,
                    sun_logo, sun_small, upscale_nn, downsample, bitmap, m_wood, m_brushed, m_granite, m_concrete,
                    WOOD_FLOOR, WOOD_DARK, WOOD_LIGHT, ORANGE, BROWN, CREAM, STEEL, ALU, CHROME, GREYPAINT,
                    WHITEPLASTIC, BEIGE, GRANITE, CONCRETE, ASPHALT, PAPERW, TERRACOTTA, BRICK, STUCCO, GREEN_LEAF,
                    RUBBER, FONT_IPAG, FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK)

T = 16


def tex(name, size=(16, 16), cutout=False, opaque=False):
    return texture('block/' + name, size=size, cutout=cutout, opaque=opaque, tile=True)


def wrapn(seed, beta=1.0, sx=1.0, sy=1.0, w=16, h=16):
    return fnoise(w, h, seed, beta=beta, sx=sx, sy=sy)


# ================================================================================================ floors
def _floor_planks(seed, worn=False):
    r = rng(seed)
    a = np.zeros((16, 16, 4), np.float32)
    grain = wrapn(seed + 1, beta=0.8, sx=10, sy=1)
    fine = wrapn(seed + 2, beta=0.3)
    x, y = grid(16, 16)
    tones = [0.25, -0.35, 0.1, -0.1]
    joints = [3, 11, 7, 14]
    idx = np.zeros((16, 16))
    for k in range(4):
        rows = slice(k * 4, k * 4 + 4)
        base = 3.2 + tones[k]
        # a printed "board" on each side of the joint gets its own tone
        side = (x[rows] >= joints[k]).astype(float)
        idx[rows] = base + grain[rows] * 0.75 + fine[rows] * 0.18 + (side - 0.5) * (0.35 if k % 2 else -0.3)
        # printed grain arcs
        arc = np.sin((x[rows] + joints[k] * 3) * 0.55 + (y[rows] - k * 4) * 1.4) * 0.22
        idx[rows] += arc
    # bevelled plank edges: groove at the bottom row of each plank, light top row
    for k in range(4):
        idx[k * 4 + 3, :] -= 1.25
        idx[k * 4, :] += 0.45
        jx = joints[k]
        idx[k * 4:k * 4 + 3, jx] -= 1.15
        idx[k * 4:k * 4 + 3, (jx + 1) % 16] += 0.3
    a[:] = from_idx(idx, WOOD_FLOOR)
    # sheen (glossy vinyl): a soft diagonal band of slightly lighter pixels
    band = (np.abs(((x - y * 0.6) % 16) - 8) < 1.2) & (r.random((16, 16)) < 0.5)
    dl(a, 0.025, band)
    # scuffs: short grey streaks and dark heel marks
    for _ in range(3 if not worn else 7):
        sx, sy = int(r.integers(0, 16)), int(r.integers(0, 16))
        ln = int(r.integers(2, 4))
        for k in range(ln):
            p = (sx + k) % 16, sy
            a[p[1], p[0], :3] = a[p[1], p[0], :3] * 0.7 + np.array([150, 132, 112]) * 0.3
    for _ in range(2 if not worn else 5):
        sx, sy = int(r.integers(0, 16)), int(r.integers(0, 16))
        a[sy, sx, :3] *= 0.72
    if worn:
        # finish worn off along a walking path: dull, lighter, grey-ish patch
        wp = wrapn(seed + 9, beta=1.6)
        m = (wp > 0.3) & ((y.astype(int) % 4) != 3)
        a[m, :3] = a[m, :3] * 0.78 + np.array([156, 128, 98]) * 0.22
        grime(a, seed + 11, strength=0.18, color='#3a2a1c', beta=1.5)
    return a


@tex('dining_floor')
def dining_floor():
    return _floor_planks(1101)


@tex('dining_floor_worn')
def dining_floor_worn():
    return _floor_planks(1101, worn=True)


QUARRY = ramp(['#2e130b', '#45200f', '#5c2b15', '#71361b', '#833f20', '#934a27', '#a2582f'], 7)


@tex('kitchen_floor')
def kitchen_floor():
    r = rng(1201)
    n = wrapn(1202, beta=1.1)
    fine = wrapn(1203, beta=0.2)
    x, y = grid(16, 16)
    tile_tone = {(0, 0): 0.3, (1, 0): -0.35, (0, 1): -0.15, (1, 1): 0.4}
    idx = np.zeros((16, 16))
    for (tx, ty), t in tile_tone.items():
        sl = (slice(ty * 8, ty * 8 + 8), slice(tx * 8, tx * 8 + 8))
        idx[sl] = 3.3 + t + n[sl] * 0.45 + fine[sl] * 0.35
    a = from_idx(idx, QUARRY)
    # tile edges: grout at x/y = 7, 15; bevel highlights
    grout = ((x.astype(int) % 8) == 7) | ((y.astype(int) % 8) == 7)
    a[grout] = C('#21160f')
    dl(a, 0.04, ((x.astype(int) % 8) == 0) & ~grout)
    dl(a, 0.03, ((y.astype(int) % 8) == 0) & ~grout)
    dl(a, -0.04, ((x.astype(int) % 8) == 6) & ~grout)
    dl(a, -0.05, ((y.astype(int) % 8) == 6) & ~grout)
    # grease: darker glossy blotch + a few bright specular pixels
    g = wrapn(1204, beta=1.8)
    gm = (g > 0.9) & ~grout
    tint(a, '#1d0f08', 0.35, gm)
    spec = gm & (r.random((16, 16)) < 0.18)
    dl(a, 0.08, spec)
    # chipped corner on one tile, speckles
    a[8, 8] = C('#3a2014')
    a[8, 9] = C('#4b2a18')
    specks(a, 1205, 0.03, '#2a140b', 0.6)
    # grout greasy-dark in places, lighter grit in others
    gd = grout & (wrapn(1206, beta=1.0) > 0.6)
    a[gd] = C('#140c08')
    return a


MOSAIC = pal('#7e807b', '#9da09a', '#b5b7b1', '#c6c8c2', '#d4d5cf', '#e0e1db')


def _mosaic(seed, dark=False):
    r = rng(seed)
    a = np.zeros((16, 16, 4), np.float32)
    grout = C('#77796f') if not dark else C('#0e0d0b')
    a[:] = grout
    choice = [5, 4, 4, 3, 5, 4, 2, 5, 4, 3, 5, 4, 1, 4, 5, 3]
    for ty in range(4):
        for tx in range(4):
            k = choice[(ty * 4 + tx * 3 + int(r.integers(0, 3))) % 16]
            col = MOSAIC[k].copy()
            x0, y0 = tx * 4, ty * 4
            for yy in range(3):
                for xx in range(3):
                    c = col.copy()
                    if xx == 0 and yy == 0:
                        c = lighten_arr(c[None], 0.05)[0]
                    if xx == 2 or yy == 2:
                        c = lighten_arr(c[None], -0.03)[0]
                    a[y0 + yy, x0 + xx] = c
    n = wrapn(seed + 1, beta=1.4)
    if not dark:
        tint(a, '#8a7a5a', np.clip(n * 0.08 + 0.05, 0, 0.2)[..., None][..., 0] if False else 0.06)
        grime(a, seed + 2, strength=0.12, color='#5a5040', beta=1.6)
        return a
    # the cavity: stained black, mould, wet sheen
    tint(a, '#2d2a22', 0.55)
    st = np.clip((n + 0.2) * 0.45, 0, 0.75)
    for yy in range(16):
        for xx in range(16):
            a[yy, xx, :3] = a[yy, xx, :3] * (1 - st[yy, xx]) + np.array([12, 11, 9]) * st[yy, xx]
    mould = wrapn(seed + 3, beta=0.9) > 1.1
    a[mould] = C('#0b0d08')
    wet = (r.random((16, 16)) < 0.08) & ~mould
    dl(a, 0.12, wet)
    return a


@tex('restroom_floor')
def restroom_floor():
    return _mosaic(1301)


@tex('restroom_floor_dark')
def restroom_floor_dark():
    return _mosaic(1301, dark=True)


@tex('vestibule_floor')
def vestibule_floor():
    a = m_granite(16, 16, ramp(['#18181a', '#222225', '#2c2c30', '#36363a', '#414146', '#4d4d52'], 6), 1401)
    x, y = grid(16, 16)
    # 2x2 polished granite tiles with a thin light grout
    g = ((x.astype(int) % 8) == 7) | ((y.astype(int) % 8) == 7)
    a[g] = C('#5c5c5e')
    dl(a, 0.04, ((x.astype(int) % 8) == 0) & ~g)
    dl(a, 0.03, ((y.astype(int) % 8) == 0) & ~g)
    # polish: a soft highlight streak per tile
    hl = (np.abs((x % 8) - (y % 8) - 1) < 0.6) & ~g & ((x % 8) < 4)
    dl(a, 0.05, hl)
    grime(a, 1402, strength=0.12, color='#0a0a0a', beta=1.4)
    return a


@tex('entrance_mat', cutout=False)
def entrance_mat():
    a = np.zeros((16, 16, 4), np.float32)
    n = wrapn(1501, beta=1.0)
    a[:] = from_idx(2.0 + n * 0.4, RUBBER)
    x, y = grid(16, 16)
    # raised border, ribbed field
    border = (x < 1) | (x > 14) | (y < 1) | (y > 14)
    a[border] = from_idx(3.3 + n[border] * 0.3, RUBBER)
    rib = ((y.astype(int) % 2) == 0) & ~border
    dl(a, 0.03, rib)
    # the sun logo, printed, worn by feet
    logo = sun_logo(12, ss=8, colors=dict(ray='#c8621c', ray2='#d27a2a', disc='#d99a2c', disc_hi='#e6b24e',
                                          line='#2a160b', outline='#5a2a10'))
    logo = logo.copy()
    logo[..., 3] = np.where(logo[..., 3] > 110, 235, 0)
    worn_m = (wrapn(1502, beta=0.6)[2:14, 2:14] > 0.9)
    logo[worn_m, 3] = 0
    over(a, logo, 2, 2)
    dl(a, -0.05, (y > 11) & ~border)
    grime(a, 1503, strength=0.18, color='#3a3328', beta=1.2)
    return a


# ================================================================================================ walls
@tex('wallpaper')
def wallpaper():
    x, y = grid(16, 16)
    n = wrapn(1601, beta=1.5)
    fine = wrapn(1602, beta=0.1)
    idx = 4.0 + n * 0.25 + fine * 0.22
    stripe = ((x.astype(int) // 2) % 2 == 0)
    idx = idx + stripe * 0.35
    a = from_idx(idx, CREAM)
    # embossed thin lines at the stripe edges
    dl(a, -0.02, (x.astype(int) % 4) == 3)
    # roll seam
    dl(a, -0.035, x.astype(int) == 15)
    tint(a, '#b49a62', 0.08)
    return a


@tex('wallpaper_stained')
def wallpaper_stained():
    a = wallpaper()
    r = rng(1611)
    x, y = grid(16, 16)
    n = fnoise(16, 16, 1612, beta=1.6)
    # water stain from the top: blotch + tide line, drips running down
    d = (y / 16.0) * 1.6 + n * 0.28 - 0.35 + np.abs(x - 8.5) / 16.0 * 0.6
    inside = d < 0.55
    tide = (d >= 0.55) & (d < 0.68)
    tint(a, '#8a6a3a', 0.30, inside)
    tint(a, '#5a3e1e', 0.55, tide)
    for dx in (4, 9, 12):
        top = int(np.argmax(~inside[:, dx]))
        ln = int(r.integers(4, 11))
        for k in range(ln):
            yy = top + k
            if yy < 16:
                tint(a, '#6a4a26', 0.35 * (1 - k / ln) + 0.1, (x == dx) & (y == yy))
    # mould dots
    for (mx, my) in ((2, 2), (3, 3), (13, 1), (12, 2), (6, 1), (14, 4)):
        tint(a, '#1c2418', 0.65, (x == mx) & (y == my))
    for (mx, my) in ((3, 2), (13, 2), (5, 1)):
        tint(a, '#34402c', 0.4, (x == mx) & (y == my))
    return a


@tex('wainscot')
def wainscot():
    x, y = grid(16, 16)
    a = m_wood(16, 16, WOOD_DARK, 1701, along='y', base=3.4, amp=0.5)
    # tongue-and-groove boards (4 px), grooves dark, board edge highlights
    xi = x.astype(int)
    dl(a, -0.09, (xi % 4) == 3)
    dl(a, 0.03, (xi % 4) == 0)
    # chair rail on top: rows 0..2 moulding
    rail = from_idx(np.array([[4.6], [5.4], [3.0]]).repeat(16, 1) + wrapn(1702, sx=8)[:3] * 0.3, WOOD_DARK)
    a[0:3] = rail
    a[3] = from_idx(np.full(16, 1.0) + wrapn(1703)[3] * 0.2, WOOD_DARK)
    # baseboard: rows 14..15
    a[14:16] = from_idx(np.full((2, 16), 1.6) + wrapn(1704, sx=8)[14:16] * 0.3, WOOD_DARK)
    dl(a[14:15], 0.05)
    wear(a[14:16], 1705, amount=0.25, edge_only=False, light=0.05, dark=-0.06)
    wear(a[0:3], 1706, amount=0.15, edge_only=False, light=0.06, dark=-0.04)
    return a


def _wall_tiles(seed, dirty=False):
    r = rng(seed)
    x, y = grid(16, 16)
    xi, yi = x.astype(int), y.astype(int)
    a = blank(16, 16, '#e6e6df')
    n = wrapn(seed + 1, beta=1.2)
    a[:] = from_idx(4.3 + n * 0.3, WHITEPLASTIC)
    grout = (xi % 4 == 3) | (yi % 4 == 3)
    a[grout] = C('#b3b3aa') if not dirty else C('#3d362c')
    # glossy: highlight at tile top-left, shade bottom-right
    dl(a, 0.05, (xi % 4 == 0) & (yi % 4 == 0))
    dl(a, -0.025, ((xi % 4 == 2) | (yi % 4 == 2)) & ~grout)
    if dirty:
        tint(a, '#b39a62', 0.18, ~grout)
        # grease running down
        streak = wrapn(seed + 2, beta=0.8, sx=1, sy=9)
        sm = (streak > 0.6) & ~grout
        tint(a, '#6e5530', 0.32, sm)
        # crack across one tile, chipped corner
        for (cx, cy) in ((5, 4), (6, 5), (6, 6)):
            a[cy, cx] = C('#2c261e')
        a[8, 12] = C('#8a8478')
        grime(a, seed + 3, strength=0.25, color='#4a3b25', bottom=0.25, beta=1.4)
        specks(a, seed + 4, 0.02, '#3a2c18', 0.6)
    return a


@tex('white_tile')
def white_tile():
    return _wall_tiles(1801)


@tex('white_tile_dirty')
def white_tile_dirty():
    return _wall_tiles(1801, dirty=True)


# ================================================================================================ ceiling
CEIL = ramp(['#8d8672', '#a39c86', '#b6af98', '#c5bea6', '#d1cab2', '#dad4bd'], 6)


def _ceiling_base(seed, grid_lines=True):
    r = rng(seed)
    x, y = grid(16, 16)
    n = wrapn(seed, beta=1.2)
    a = from_idx(3.6 + n * 0.3, CEIL)
    # fissured acoustic pattern: short dark worms + pinholes
    for _ in range(26):
        sx, sy = int(r.integers(1, 15)), int(r.integers(1, 15))
        a[sy, sx] = CEIL[1]
        if r.random() < 0.5:
            a[sy, min(14, sx + 1)] = CEIL[2]
    for yy in range(2, 15, 3):
        for xx in range(2 + (yy // 3) % 2, 15, 3):
            if r.random() < 0.7:
                a[yy, xx] = CEIL[2]
    if grid_lines:
        # T-bar grid on the top and left edges (adjacent blocks complete it)
        a[0, :] = C('#a9a7a0')
        a[:, 0] = C('#a9a7a0')
        a[1, 1:] = lighten_arr(a[1, 1:][None], -0.04)[0]
        a[1:, 1] = lighten_arr(a[1:, 1][None], -0.03)[0]
    tint(a, '#b8a46a', 0.06)
    return a


@tex('ceiling_tile')
def ceiling_tile():
    return _ceiling_base(1901)


@tex('ceiling_tile_stained')
def ceiling_tile_stained():
    a = _ceiling_base(1901)
    x, y = grid(16, 16)
    n = fnoise(16, 16, 1911, beta=1.7)
    d = np.hypot(x - 9.5, y - 8.5) / 6.5 + n * 0.25
    tint(a, '#9a7a42', 0.28, d < 1.0)
    tint(a, '#6a4a22', 0.45, (d >= 0.82) & (d < 1.0))
    tint(a, '#7e5f30', 0.30, (d >= 0.45) & (d < 0.55))
    tint(a, '#4a3216', 0.35, d < 0.22)
    return a


def _light_panel(lit):
    a = _ceiling_base(1901)
    x, y = grid(16, 16)
    xi, yi = x.astype(int), y.astype(int)
    # frame 2..14, diffuser 3..13
    rect(a, 2, 2, 14, 14, '#d4d3cc' if not lit else '#e8e9e4')
    frame(a, 2, 2, 14, 14, '#9d9c95')
    inner = (xi >= 3) & (xi <= 13) & (yi >= 3) & (yi <= 13)
    prism = ((xi + yi) % 2 == 0)
    if not lit:
        a[inner] = C('#c9c9c2')
        dl(a, -0.03, inner & prism)
        # tubes as faint darker bands behind the diffuser
        for ty in (5, 6, 10, 11):
            dl(a, -0.04, inner & (yi == ty))
        # dead insects
        for (bx, by) in ((5, 12), (6, 12), (11, 9), (4, 4)):
            a[by, bx] = C('#3c3628')
        dl(a, -0.06, inner & (yi >= 12))
    else:
        a[inner] = C('#f1f8f3')
        dl(a, -0.015, inner & prism)
        for ty in (5, 6, 10, 11):
            a[inner & (yi == ty)] = C('#ffffff')
        for (bx, by) in ((5, 12), (6, 12), (11, 9)):
            a[by, bx] = C('#8a8c80')
        dl(a, -0.03, inner & (yi >= 12))
    frame(a, 3, 3, 13, 13, '#b2b1aa' if not lit else '#dfe6e1')
    return a


@tex('ceiling_light')
def ceiling_light():
    return _light_panel(False)


@tex('ceiling_light_lit')
def ceiling_light_lit():
    return _light_panel(True)


def _downlight(lit):
    a = _ceiling_base(1902)
    cx, cy = 8.5, 8.5
    trim = ring_mask(16, 16, cx, cy, 4.6, 5.6)
    refl = ring_mask(16, 16, cx, cy, 2.4, 4.6)
    lens = disc_mask(16, 16, cx, cy, 2.4)
    a[trim] = C('#e3e1da')
    dl(a, -0.06, trim & (grid(16, 16)[1] > 9))
    if not lit:
        a[refl] = C('#7e7d78')
        dl(a, 0.05, refl & (grid(16, 16)[1] < 8))
        a[lens] = C('#4e4c46')
        a[7, 7] = C('#8e8b80')
    else:
        a[refl] = C('#ffe2a8')
        dl(a, -0.04, refl & (grid(16, 16)[1] > 9))
        a[lens] = C('#fff6e2')
        a[8, 8] = C('#ffffff')
    return a


@tex('downlight')
def downlight():
    return _downlight(False)


@tex('downlight_lit')
def downlight_lit():
    return _downlight(True)


# ================================================================================================ exterior
@tex('exterior_wall')
def exterior_wall():
    r = rng(2001)
    n = wrapn(2001, beta=1.3)
    sp = wrapn(2002, beta=0.0)
    a = from_idx(3.9 + n * 0.35 + sp * 0.4, STUCCO)
    x, y = grid(16, 16)
    # sprayed stucco: pits and bumps
    pits = sp > 1.5
    dl(a, -0.05, pits)
    bumps = sp < -1.6
    dl(a, 0.03, bumps)
    # faint rain streaks
    st = wrapn(2003, beta=0.9, sx=1, sy=12)
    tint(a, '#5e5444', 0.1, st > 0.8)
    return a


@tex('facade_brick')
def facade_brick():
    r = rng(2101)
    x, y = grid(16, 16)
    xi, yi = x.astype(int), y.astype(int)
    n = wrapn(2102, beta=0.9)
    a = blank(16, 16, '#3a2c24')
    tones = r.normal(0, 0.55, 16)
    for row in range(4):
        off = 4 if row % 2 else 0
        for b in range(2):
            x0 = (b * 8 + off) % 16
            t = 3.0 + tones[row * 2 + b]
            for yy in range(row * 4, row * 4 + 3):
                for k in range(7):
                    xx = (x0 + k) % 16
                    v = t + n[yy, xx] * 0.45
                    if yy == row * 4:
                        v += 0.5
                    if yy == row * 4 + 2:
                        v -= 0.4
                    if k == 0:
                        v += 0.25
                    if k == 6:
                        v -= 0.35
                    a[yy, xx] = from_idx(np.array([v]), BRICK)[0]
            # burnt end
            if r.random() < 0.4:
                xx = (x0 + (6 if r.random() < 0.5 else 0)) % 16
                a[row * 4 + 1, xx] = BRICK[1]
    mortar = (yi % 4 == 3)
    for row in range(4):
        off = 4 if row % 2 else 0
        for b in range(2):
            xx = (b * 8 + off + 7) % 16
            a[row * 4:row * 4 + 3, xx] = C('#4a3c32')
    a[mortar] = C('#433730')
    dl(a, -0.04, mortar & (xi % 3 == 0))
    return a


@tex('brick_sill')
def brick_sill():
    a = facade_brick()
    n = wrapn(2201, beta=1.2, sx=6)
    conc = from_idx(3.6 + n * 0.4, CONCRETE)
    a[0:9] = conc[0:9]
    a[6] = from_idx(np.full(16, 5.4) + n[6] * 0.3, CONCRETE)    # sill top edge (seen on a 10 px slab)
    a[7:9] = from_idx(np.full((2, 16), 3.4) + n[7:9] * 0.3, CONCRETE)
    a[9] = from_idx(np.full(16, 1.2), CONCRETE)                 # drip shadow under the sill
    tint(a[7:10], '#3a3020', 0.15)
    a[0] = from_idx(np.full(16, 5.0) + n[0] * 0.3, CONCRETE)
    return a


@tex('brick_sill_top')
def brick_sill_top():
    n = wrapn(2202, beta=1.1)
    a = from_idx(4.2 + n * 0.45, CONCRETE)
    grime(a, 2203, strength=0.22, color='#2c2a24', beta=1.3)
    specks(a, 2204, 0.03, '#4a4a46', 0.8)
    return a


@tex('roof_tile')
def roof_tile():
    r = rng(2301)
    x, y = grid(16, 16)
    xi, yi = x.astype(int), y.astype(int)
    n = wrapn(2302, beta=1.0)
    # S-tiles: 4 px columns (2 cap + 2 pan), 8 px courses; course bottom shadowed
    col = xi % 4
    prof = np.array([4.6, 4.1, 2.4, 2.9])[col]
    course = yi % 8
    shade_ = np.where(course == 7, -1.6, np.where(course == 0, 0.5, -course * 0.07))
    tone = np.zeros((16, 16))
    for c in range(4):
        for k in range(2):
            tone[k * 8:(k + 1) * 8, c * 4:(c + 1) * 4] = r.normal(0, 0.35)
    idx = prof + shade_ + tone + n * 0.3
    a = from_idx(idx, TERRACOTTA)
    # weathering: dark streaks down the pans, a few lichen dots
    st = wrapn(2303, beta=0.8, sx=1, sy=10)
    tint(a, '#2a1a12', 0.25, (st > 0.7) & (col >= 2))
    for _ in range(4):
        px(a, int(r.integers(0, 16)), int(r.integers(0, 16)), '#7d7a5a', 0.6)
    return a


@tex('fascia')
def fascia():
    x, y = grid(16, 16)
    n = wrapn(2401, beta=1.2, sx=4)
    a = from_idx(4.2 + n * 0.3, ORANGE)
    a[0] = C('#3a1f10')
    a[1] = from_idx(np.full(16, 5.5), ORANGE)
    a[10:12] = from_idx(np.full((2, 16), 5.0) + n[10:12] * 0.2, CREAM)
    dl(a[11:12], -0.03)
    a[15] = from_idx(np.full(16, 2.0), ORANGE)
    # dirt drips from the roof edge, faded paint
    st = wrapn(2402, beta=0.8, sx=1, sy=8)
    tint(a, '#4a2a18', 0.22, (st > 0.75) & (y > 1) & (y < 10))
    tint(a, '#c9a080', 0.08)
    return a


@tex('shop_window')
def shop_window():
    r = rng(2501)
    x, y = grid(16, 16)
    a = blank(16, 16, C('#6f8a8e', 64))
    n = wrapn(2502, beta=1.2, sx=1, sy=5)
    a[..., 3] = np.clip(60 + n * 8, 40, 90)
    a[..., :3] = a[..., :3] + n[..., None] * 4
    # mullions (left/right): aluminium
    for xx, v in ((0, '#9aa0a3'), (15, '#7c8285')):
        a[:, xx] = C(v)
    a[:, 1, :3] = a[:, 1, :3] * 0.8
    a[:, 1, 3] = 120
    # raindrops: bright head + darker body, some with a trail
    for _ in range(9):
        dx, dy = int(r.integers(2, 14)), int(r.integers(0, 16))
        a[dy, dx] = C('#dfe8ea', 200)
        if dy + 1 < 16:
            a[dy + 1, dx] = C('#40565a', 150)
        if r.random() < 0.4:
            for k in range(2, int(r.integers(3, 7))):
                if dy - k >= 0:
                    a[dy - k, dx] = C('#b5c6c9', 110)
    # condensation haze near the bottom
    a[13:16, 2:15, 3] = np.minimum(255, a[13:16, 2:15, 3] + np.array([[18], [26], [34]]))
    a[13:16, 2:15, :3] = a[13:16, 2:15, :3] * 0.8 + 255 * 0.2
    return a


@tex('asphalt')
def asphalt():
    r = rng(2601)
    n = wrapn(2602, beta=0.9)
    fine = wrapn(2603, beta=0.0)
    a = from_idx(2.4 + n * 0.45 + fine * 0.5, ASPHALT)
    agg = r.random((16, 16)) < 0.08
    a[agg] = C('#56565b')
    agg2 = r.random((16, 16)) < 0.05
    a[agg2] = C('#0f0f11')
    # wet sheen: slightly cooler, a few specular glints
    tint(a, '#1c2228', 0.15)
    gl = r.random((16, 16)) < 0.02
    a[gl] = C('#6c7278')
    return a


def _lined(color, seed, width=2, worn=0.18):
    a = asphalt()
    r = rng(seed)
    x, y = grid(16, 16)
    c = C(color)
    n = wrapn(seed, beta=0.6)
    x0 = 8 - width // 2
    for xx in range(x0, x0 + width):
        for yy in range(16):
            if r.random() < worn or n[yy, xx] > 1.4:
                a[yy, xx, :3] = a[yy, xx, :3] * 0.5 + c[:3] * 0.5
            else:
                a[yy, xx, :3] = c[:3] * (0.9 + 0.1 * n[yy, xx])
    # aggregate shows through the paint
    for _ in range(4):
        yy = int(r.integers(0, 16))
        a[yy, x0 + int(r.integers(0, width)), :3] *= 0.72
    return a


@tex('parking_line')
def parking_line():
    return _lined('#d9d9d2', 2701, 2)


@tex('road_line')
def road_line():
    return _lined('#d6a524', 2702, 2, worn=0.12)


@tex('curb')
def curb():
    n = wrapn(2801, beta=1.1, sx=4)
    a = from_idx(4.1 + n * 0.4, CONCRETE)
    for k in (0, 8):
        a[k] = from_idx(np.full(16, 5.6) + n[k] * 0.2, CONCRETE)
        a[k + 1] = from_idx(np.full(16, 4.8) + n[k + 1] * 0.2, CONCRETE)
        a[k + 7] = from_idx(np.full(16, 2.4) + n[k + 7] * 0.2, CONCRETE)
    # joint every block (left edge)
    a[:, 0] = from_idx(np.full(16, 2.0), CONCRETE)
    grime(a, 2802, strength=0.2, color='#262420', beta=1.2)
    tint(a, '#1f252a', 0.12)
    return a


@tex('curb_top')
def curb_top():
    n = wrapn(2803, beta=1.1)
    a = from_idx(4.4 + n * 0.4, CONCRETE)
    a[:, 0] = from_idx(np.full(16, 2.2), CONCRETE)
    a[0] = from_idx(np.full(16, 5.2), CONCRETE)
    a[15] = from_idx(np.full(16, 5.2), CONCRETE)
    grime(a, 2804, strength=0.2, color='#262420', beta=1.3)
    tint(a, '#1f252a', 0.12)
    return a


PAVER = ramp(['#2f302e', '#3d3e3b', '#4b4c48', '#595a55', '#676862', '#75766f'], 6)


@tex('sidewalk')
def sidewalk():
    r = rng(2901)
    x, y = grid(16, 16)
    xi, yi = x.astype(int), y.astype(int)
    n = wrapn(2902, beta=1.0)
    a = blank(16, 16, '#262624')
    # running-bond rectangular pavers 8x4 (3 rows of brick + 1 joint)
    for row in range(4):
        off = 4 if row % 2 else 0
        for b in range(2):
            x0 = (b * 8 + off) % 16
            t = 3.0 + r.normal(0, 0.45)
            tan = r.random() < 0.2
            for yy in range(row * 4, row * 4 + 3):
                for k in range(7):
                    xx = (x0 + k) % 16
                    v = t + n[yy, xx] * 0.4 + (0.45 if yy == row * 4 else 0) + (-0.4 if yy == row * 4 + 2 else 0)
                    c = from_idx(np.array([v]), PAVER)[0]
                    if tan:
                        c[:3] = c[:3] * 0.85 + np.array([110, 92, 70]) * 0.15
                    a[yy, xx] = c
    tint(a, '#1d2226', 0.18)      # wet
    gl = (r.random((16, 16)) < 0.03)
    dl(a, 0.08, gl)
    return a


@tex('puddle')
def puddle():
    r = rng(3001)
    x, y = grid(16, 16)
    n = wrapn(3002, beta=1.5)
    a = blank(16, 16, C('#1b2329', 168))
    a[..., :3] += n[..., None] * 5
    # ripples: two faint rings
    for (cx, cy, rr) in ((5.5, 6.5, 3.0), (11.5, 11.5, 2.2)):
        ring = ring_mask(16, 16, cx, cy, rr - 0.5, rr + 0.5)
        a[ring, :3] = a[ring, :3] * 0.6 + np.array([90, 104, 112]) * 0.4
        a[ring, 3] = 190
    gl = r.random((16, 16)) < 0.04
    a[gl] = C('#7d8c94', 200)
    return a


@tex('rice_paddy')
def rice_paddy():
    r = rng(3101)
    x, y = grid(16, 16)
    n = wrapn(3102, beta=1.2)
    mud = ramp(['#14100c', '#1d1711', '#271f17', '#31281e', '#3c3226'], 5)
    a = from_idx(2.2 + n * 0.6, mud)
    # standing water in the low parts
    w = wrapn(3103, beta=1.6)
    wm = w > 0.7
    a[wm] = C('#1a2026')
    dl(a, 0.05, wm & (r.random((16, 16)) < 0.15))
    # stubble hills in rows (4 px spacing), each a tuft of 2-3 pale stalks
    stub = ramp(['#4a3c22', '#66532f', '#83703e', '#9c8a50'], 4)
    for gy in range(1, 16, 4):
        for gx in range(1 + (gy // 4) % 2, 16, 4):
            pts = [(gx, gy), (gx + 1, gy), (gx, gy - 1), (gx + 1, gy + 1)]
            for i, (sx, sy) in enumerate(pts):
                if r.random() < 0.85:
                    a[sy % 16, sx % 16] = stub[min(3, 3 - i if i < 3 else 1)]
            a[(gy + 1) % 16, gx % 16] = stub[0]
    return a


@tex('rice_paddy_side')
def rice_paddy_side():
    n = wrapn(3104, beta=1.1, sx=4)
    mud = ramp(['#120e0a', '#1b1510', '#241c15', '#2e251c', '#3a2f23'], 5)
    a = from_idx(2.0 + n * 0.6, mud)
    a[0:2] = from_idx(np.full((2, 16), 3.2) + n[0:2] * 0.4, mud)
    specks(a, 3105, 0.04, '#4a3c26', 0.7)
    return a


@tex('hedge', cutout=True)
def hedge():
    r = rng(3201)
    x, y = grid(16, 16)
    n = wrapn(3202, beta=0.6)
    a = from_idx(3.0 + n * 0.9, GREEN_LEAF)
    # leaf clusters: lighter tips, dark gaps; ~8% holes
    tips = r.random((16, 16)) < 0.16
    dl(a, 0.06, tips)
    a[..., 3] = 255
    holes = (n < -1.25) & (r.random((16, 16)) < 0.8)
    a[holes, 3] = 0
    dark = (n < -0.6) & ~holes
    a[dark] = from_idx(np.full(int(dark.sum()), 0.6), GREEN_LEAF)
    # wet sheen on some leaves
    dl(a, 0.04, (r.random((16, 16)) < 0.05) & ~holes)
    return a


@tex('concrete_wall')
def concrete_wall():
    r = rng(3301)
    x, y = grid(16, 16)
    n = wrapn(3302, beta=1.3)
    fine = wrapn(3303, beta=0.2)
    a = from_idx(3.8 + n * 0.45 + fine * 0.22, CONCRETE)
    # form-tie (P-con) holes
    for (cx, cy) in ((3.5, 4.5), (11.5, 12.5)):
        ring = ring_mask(16, 16, cx, cy, 0.9, 1.6)
        hole = disc_mask(16, 16, cx, cy, 0.95)
        dl(a, 0.04, ring)
        a[hole] = C('#3b3b38')
    # formwork seam
    dl(a, -0.03, y.astype(int) == 8)
    st = wrapn(3304, beta=0.9, sx=1, sy=10)
    tint(a, '#2e2b26', 0.18, st > 0.8)
    return a


@tex('granite_counter')
def granite_counter():
    a = m_granite(16, 16, GRANITE, 3401)
    # wrap-safe: rebuild with tileable noise
    r = rng(3401)
    n = wrapn(3402, beta=0.5)
    a = from_idx(2.4 + n * 0.9, GRANITE)
    a[r.random((16, 16)) < 0.07] = C('#8a8780')
    a[r.random((16, 16)) < 0.04] = C('#5d4a3c')
    a[r.random((16, 16)) < 0.03] = C('#b5b2a8')
    return a


@tex('stainless')
def stainless():
    r = rng(3501)
    x, y = grid(16, 16)
    n = wrapn(3502, beta=0.7, sx=14, sy=1)
    a = from_idx(4.2 + n * 0.5, STEEL)
    a[:, 0] = from_idx(np.full(16, 2.2), STEEL)     # panel seam
    a[:, 1] = from_idx(np.full(16, 5.6), STEEL)
    for _ in range(6):
        sx, sy = int(r.integers(2, 14)), int(r.integers(0, 16))
        for k in range(int(r.integers(2, 5))):
            px(a, (sx + k) % 16, sy, STEEL[6], 0.6)
    g = wrapn(3503, beta=1.6)
    tint(a, '#5a4a30', 0.18, g > 0.6)
    specks(a, 3504, 0.03, '#4c3a20', 0.5)
    return a


@tex('pillar')
def pillar():
    a = m_wood(16, 16, WOOD_DARK, 3601, along='y', base=3.3, amp=0.5)
    x, _ = grid(16, 16)
    xi = x.astype(int)
    # corner trims
    a[:, 0] = from_idx(np.full(16, 5.2), WOOD_DARK)
    a[:, 1] = from_idx(np.full(16, 2.0), WOOD_DARK)
    a[:, 15] = from_idx(np.full(16, 1.4), WOOD_DARK)
    a[:, 14] = from_idx(np.full(16, 4.3), WOOD_DARK)
    dl(a, -0.07, xi == 7)
    wear(a, 3602, amount=0.08, edge_only=False, light=0.05, dark=-0.05)
    return a


@tex('pillar_top')
def pillar_top():
    x, y = grid(16, 16)
    a = m_wood(16, 16, WOOD_DARK, 3603, along='x', base=2.6, amp=0.4)
    frame(a, 0, 0, 15, 15, WOOD_DARK[5])
    frame(a, 1, 1, 14, 14, WOOD_DARK[1])
    return a


# ================================================================================================ doors (32x32)
def door_tex(name):
    return texture('block/' + name, size=(32, 32))


def x2(a):
    return upscale_nn(a, 2)


def plate(w, h, bg='#ecebe4', border='#3a3a3a'):
    p = blank(w, h, bg)
    frame(p, 0, 0, w - 1, h - 1, border)
    dl(p[1:2, 1:w - 1], 0.04)
    dl(p[h - 2:h - 1, 1:w - 1], -0.04)
    return p


def jp_text(s, size=9, spec=FONT_IPAG):
    return text_mask(s, size, spec, binary=True)


def _steel_door(seed, base, kick=True):
    """16x16 art of a painted steel door half."""
    n = noise(16, 16, seed, beta=1.3, wrap=False)
    a = from_idx(base + n * 0.3, GREYPAINT)
    frame(a, 0, 0, 15, 15, GREYPAINT[1])
    dl(a[:, 1:2], 0.04)
    return a


@door_tex('staff_door_top')
def staff_door_top():
    a = _steel_door(3701, 4.0)
    a[15, :] = GREYPAINT[4]                       # continuous with the bottom half
    wear(a, 3702, amount=0.05, edge_only=False)
    big = x2(a)
    # 「STAFF ONLY」 plate (32-px resolution signage)
    p = plate(24, 15, bg='#e9e7dc', border='#2c2c2c')
    stamp_mask(p, text35('STAFF'), 2, 2, '#1d1d1d')
    stamp_mask(p, text35('ONLY'), 4, 8, '#1d1d1d')
    rect(p, 1, 13, 22, 13, '#c9a227')
    over(big, p, 4, 9)
    # screws
    for (sx, sy) in ((5, 10), (26, 10), (5, 22), (26, 22)):
        px(big, sx, sy, '#8c8c86')
    return big


@door_tex('staff_door_bottom')
def staff_door_bottom():
    a = _steel_door(3711, 4.0)
    a[0, :] = GREYPAINT[4]
    # kick plate
    k = m_brushed(14, 4, STEEL, 3712, along='x')
    a[11:15, 1:15] = k
    a[11, 1:15] = STEEL[6]
    wear(a, 3713, amount=0.08, edge_only=False)
    grime(a, 3714, strength=0.15, color='#2a2620', bottom=0.35)
    big = x2(a)
    # lever handle + key cylinder (left), at the top of the bottom half
    rect(big, 2, 2, 9, 3, '#c4c8ca')
    rect(big, 2, 4, 9, 4, '#7e8386')
    rect(big, 2, 1, 4, 5, '#a9aeb1')
    px(big, 3, 7, '#3a3a38')
    px(big, 3, 8, '#5a5a56')
    return big


def _wood_door_16(seed, palette=WOOD_LIGHT, base=3.4):
    a = m_wood(16, 16, palette, seed, along='y', base=base, amp=0.45)
    frame(a, 0, 0, 15, 15, palette[1])
    return a


OFFICE_WOOD = ramp(['#2a180d', '#3b2414', '#4d301b', '#5e3c23', '#70492b', '#82573a'], 6)


@door_tex('office_door_top')
def office_door_top():
    a = _wood_door_16(3801, OFFICE_WOOD, base=3.2)
    a[15, :] = a[14, :]
    big = x2(a)
    # small window (opaque dark glass, reflection), with a steel frame
    rect(big, 10, 3, 21, 14, '#7a7f82')
    rect(big, 11, 4, 20, 13, '#1a2024')
    for k in range(4):
        px(big, 13 + k, 11 - k, '#3f4b52')
        px(big, 14 + k, 11 - k, '#2c353a')
    # 「事務所」 plate
    p = plate(30, 12, bg='#f0eee6', border='#4a3a2a')
    stamp_center(p, jp_text('事務所', 9), 15, 6, '#151515')
    over(big, p, 1, 17)
    return big


@door_tex('office_door_bottom')
def office_door_bottom():
    a = _wood_door_16(3811, OFFICE_WOOD, base=3.2)
    a[0, :] = a[1, :]
    wear(a, 3812, amount=0.08, edge_only=False)
    grime(a, 3813, strength=0.12, color='#1a120a', bottom=0.3)
    big = x2(a)
    rect(big, 2, 2, 8, 3, '#b8b2a2')     # lever
    rect(big, 2, 1, 4, 5, '#9a9484')
    px(big, 3, 8, '#2a2620')             # keyhole
    px(big, 3, 9, '#2a2620')
    return big


def _pictogram(kind):
    """12x15 restroom pictogram (man / woman)."""
    rows_m = ['....###.....', '...#####....', '...#####....', '....###.....', '............', '..#######...',
              '.#########..', '.#########..', '.#.#####.#..', '.#.#####.#..', '...#####....', '...##.##....',
              '...##.##....', '...##.##....', '...##.##....']
    rows_w = ['....###.....', '...#####....', '...#####....', '....###.....', '............', '...#####....',
              '..#######...', '..#######...', '.#.#####.#..', '.#########..', '.#########..', '..#######...',
              '....#.#.....', '....#.#.....', '....#.#.....']
    return bitmap(rows_m if kind == 'm' else rows_w)


def _restroom_door(kind, half):
    pal_ = ramp(['#3a2414', '#523420', '#6a442a', '#7f5434', '#93653f', '#a7784c'], 6)
    a = _wood_door_16(3901 if kind == 'm' else 3911, pal_, base=3.3)
    if half == 'top':
        a[15, :] = a[14, :]
        big = x2(a)
        p = plate(18, 21, bg='#f2f1ec', border='#8a8a86')
        col = '#1d4fa8' if kind == 'm' else '#c22a2a'
        stamp_mask(p, _pictogram(kind), 4, 3, col)
        over(big, p, 7, 6)
        return big
    a[0, :] = a[1, :]
    k = m_brushed(14, 3, STEEL, 3921, along='x')
    a[12:15, 1:15] = k
    a[12, 1:15] = STEEL[6]
    wear(a, 3922, amount=0.08, edge_only=False)
    grime(a, 3923, strength=0.14, color='#20160c', bottom=0.35)
    big = x2(a)
    rect(big, 2, 2, 8, 3, '#c0c2bf')
    rect(big, 2, 1, 4, 5, '#9fa19e')
    # push plate
    rect(big, 24, 2, 28, 12, '#b8bcbd')
    frame(big, 24, 2, 28, 12, '#8a8e90')
    return big


@door_tex('restroom_door_m_top')
def restroom_door_m_top():
    return _restroom_door('m', 'top')


@door_tex('restroom_door_m_bottom')
def restroom_door_m_bottom():
    return _restroom_door('m', 'bottom')


@door_tex('restroom_door_w_top')
def restroom_door_w_top():
    return _restroom_door('w', 'top')


@door_tex('restroom_door_w_bottom')
def restroom_door_w_bottom():
    return _restroom_door('w', 'bottom')


BACKDOOR = ramp(['#1a201d', '#252d29', '#313a35', '#3e4842', '#4c5750', '#5b665f'], 6)


def _back_door(half):
    n = noise(16, 16, 4001 if half == 'top' else 4011, beta=1.2, wrap=False)
    a = from_idx(3.2 + n * 0.4, BACKDOOR)
    frame(a, 0, 0, 15, 15, BACKDOOR[1])
    r = rng(4002 if half == 'top' else 4012)
    # dents and rust
    m = r.random((16, 16)) < 0.05
    tint(a, '#5c3016', 0.55, m)
    if half == 'bottom':
        rust = (np.arange(16)[:, None] > 11) & (r.random((16, 16)) < 0.35)
        tint(a, '#6a3418', 0.5, rust)
        a[0, :] = a[1, :]
        big = x2(a)
        # push bar across (seen from inside = the texture's face)
        rect(big, 3, 2, 28, 4, '#a5a9ab')
        rect(big, 3, 5, 28, 5, '#5e6264')
        rect(big, 2, 1, 4, 6, '#7c8082')
        rect(big, 27, 1, 29, 6, '#7c8082')
        return big
    a[15, :] = a[14, :]
    big = x2(a)
    # weathered sticker 「関係者以外立入禁止」 reduced to a red-bordered notice
    p = plate(16, 10, bg='#e8e4d6', border='#a3241c')
    stamp_center(p, jp_text('禁止', 8), 8, 5, '#a3241c')
    over(big, p, 8, 14)
    return big


@door_tex('back_door_top')
def back_door_top():
    return _back_door('top')


@door_tex('back_door_bottom')
def back_door_bottom():
    return _back_door('bottom')


def _fridge_door(half):
    n = noise(16, 16, 4101 if half == 'top' else 4111, beta=1.6, wrap=False)
    a = from_idx(4.6 + n * 0.25, WHITEPLASTIC)
    frame(a, 0, 0, 15, 15, WHITEPLASTIC[2])
    # thick insulated panel: an inset border
    frame(a, 2, 0 if half == 'bottom' else 2, 13, 15 if half == 'top' else 13, WHITEPLASTIC[3])
    if half == 'bottom':
        a[0, :] = a[1, :]
        grime(a, 4112, strength=0.12, color='#5a5a4a', bottom=0.4)
        big = x2(a)
        # big chrome latch handle
        rect(big, 2, 0, 7, 9, '#9ca2a6')
        frame(big, 2, 0, 7, 9, '#5c6266')
        rect(big, 3, 1, 4, 8, '#e6eaec')
        rect(big, 6, 3, 13, 5, '#c9ced1')
        rect(big, 6, 6, 13, 6, '#6e7478')
        return big
    a[15, :] = a[14, :]
    big = x2(a)
    # thermometer dial + a faded 「冷蔵室」 label
    dm = disc_mask(32, 32, 8.5, 9.5, 4.0)
    big[dm] = C('#3a3d40')
    dm2 = disc_mask(32, 32, 8.5, 9.5, 3.0)
    big[dm2] = C('#f0f0ea')
    line(big, 8, 9, 10, 7, '#c0281c')
    p = plate(20, 10, bg='#2d5aa0', border='#1d3a6a')
    stamp_center(p, jp_text('冷蔵室', 7), 10, 5, '#f2f2f2')
    over(big, p, 8, 18)
    return big


@door_tex('fridge_door_top')
def fridge_door_top():
    return _fridge_door('top')


@door_tex('fridge_door_bottom')
def fridge_door_bottom():
    return _fridge_door('bottom')


LAMINATE = ramp(['#6d604c', '#857760', '#9c8d74', '#b0a186', '#c1b296', '#cfc1a4'], 6)


def _stall_door(half, occupied):
    n = noise(16, 16, 4201 if half == 'top' else 4211, beta=1.5, wrap=False)
    a = from_idx(3.6 + n * 0.25, LAMINATE)
    frame(a, 0, 0, 15, 15, LAMINATE[1])
    if half == 'top':
        a[15, :] = a[14, :]
        grime(a, 4202, strength=0.1, color='#4a3c26')
        big = x2(a)
        # coat hook seen from outside: nothing; the indicator window at eye height
        p = plate(28, 13, bg='#d8d8d0' if not occupied else '#f2e8e4', border='#6a6a64')
        if occupied:
            rect(p, 1, 1, 26, 11, '#d0281e')
            stamp_center(p, jp_text('使用中', 9), 14, 6, '#ffffff')
        else:
            rect(p, 1, 1, 26, 11, '#2b62b8')
            stamp_center(p, jp_text('空き', 9), 14, 6, '#ffffff')
        over(big, p, 2, 10)
        return big
    a[0, :] = a[1, :]
    grime(a, 4212, strength=0.14, color='#4a3c26', bottom=0.35)
    wear(a, 4213, amount=0.06, edge_only=False)
    big = x2(a)
    # lock with a small indicator
    rect(big, 2, 1, 7, 6, '#b5b8b6')
    frame(big, 2, 1, 7, 6, '#7a7d7b')
    rect(big, 3, 2, 6, 3, '#d0281e' if occupied else '#2b62b8')
    return big


@door_tex('stall_door_top')
def stall_door_top():
    return _stall_door('top', False)


@door_tex('stall_door_bottom')
def stall_door_bottom():
    return _stall_door('bottom', False)


@door_tex('stall_door_occupied_top')
def stall_door_occupied_top():
    return _stall_door('top', True)


@door_tex('stall_door_occupied_bottom')
def stall_door_occupied_bottom():
    return _stall_door('bottom', True)


# ------------------------------------------------------------------------------------------------ auto door panel
GLYPH_JIDO = {
    '自': ['.#...', '#####', '#...#', '#####', '#...#', '#####'],
    '動': ['##.#.', '#####', '##.##', '#####', '.####', '##.##'],
    'ド': ['#.#.#', '#...#', '##...', '#.#..', '#....', '#....'],
    'ア': ['#####', '....#', '.##..', '..#..', '.#...', '#....'],
}


@texture('block/auto_door_panel', size=(16, 48))
def auto_door_panel():
    a = blank(16, 48, C('#7d969a', 58))
    n = noise(16, 48, 4301, beta=1.2, sx=1, sy=6, wrap=False)
    a[..., 3] = np.clip(56 + n * 8, 36, 84)
    # glass reflection streaks
    x, y = grid(16, 48)
    st = ((x * 1.0 + y * 0.45) % 13 < 1.3) & (x > 2) & (x < 13)
    a[st, :3] = a[st, :3] * 0.6 + 230 * 0.4
    a[st, 3] = 92
    # aluminium frame: stiles 2 px, top rail 2 px, bottom rail 4 px
    alu = ALU
    for xx, k in ((0, 2), (1, 4), (14, 3), (15, 1)):
        a[:, xx] = alu[k]
    a[0:2, :] = alu[4]
    a[1, :] = alu[3]
    a[44:48, :] = alu[3]
    a[44, :] = alu[5]
    a[47, :] = alu[1]
    # rubber seal line inside the frame
    a[2:44, 2, :3] = a[2:44, 2, :3] * 0.5
    a[2:44, 2, 3] = 140
    # the sun-logo decal at eye height (y ~ 18..28)
    logo = sun_logo(11, ss=8)
    logo[..., 3] = np.where(logo[..., 3] > 100, 245, 0)
    over(a, logo, 3, 17)
    # 「自動ドア」 sticker at hand height: white panel, red glyphs 2x2 (6 px tall pixel kana / kanji)
    sticker = blank(12, 15, '#f2f0ea')
    for i, ch in enumerate('自動'):
        stamp_mask(sticker, bitmap(GLYPH_JIDO[ch]), i * 6 + (1 if i == 0 else 0), 1, '#c8281c')
    for i, ch in enumerate('ドア'):
        stamp_mask(sticker, bitmap(GLYPH_JIDO[ch]), i * 6 + (1 if i == 0 else 0), 8, '#c8281c')
    hline(sticker, 0, 11, 14, '#c8281c')
    over(a, sticker, 2, 28)
    return a


# ------------------------------------------------------------------------------------------------ swing door
@texture('block/swing_door', size=(16, 32))
def swing_door():
    pal_ = ramp(['#24160d', '#331f12', '#432a18', '#53351f', '#644128', '#764e31'], 6)
    n = noise(16, 32, 4401, beta=1.3, sx=1, sy=4, wrap=False)
    a = from_idx(3.0 + n * 0.35, pal_)
    frame(a, 0, 0, 15, 31, pal_[1])
    # porthole at eye height
    cx, cy = 8.0, 9.5
    ring = ring_mask(16, 32, cx, cy, 3.4, 4.6)
    glass = disc_mask(16, 32, cx, cy, 3.4)
    a[ring] = C('#a9aeb1')
    dl(a, 0.08, ring & (grid(16, 32)[1] < cy))
    dl(a, -0.08, ring & (grid(16, 32)[1] > cy + 1))
    a[glass] = C('#5e7a80', 110)
    hl = glass & (np.abs(grid(16, 32)[0] - grid(16, 32)[1] + 2.5) < 0.8)
    a[hl] = C('#d6e4e6', 170)
    # push plate and kick plate (stainless, scuffed by carts)
    rect(a, 3, 16, 12, 19, '#b9bdbf')
    frame(a, 3, 16, 12, 19, '#80878a')
    k = m_brushed(14, 6, STEEL, 4402, along='x')
    a[25:31, 1:15] = k
    a[25, 1:15] = STEEL[6]
    wear(a[25:31], 4403, amount=0.2, edge_only=False)
    grime(a, 4404, strength=0.12, color='#1a120a', bottom=0.25)
    return a
