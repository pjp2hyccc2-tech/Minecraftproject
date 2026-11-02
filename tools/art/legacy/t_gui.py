"""GUI overlays, jump-scare faces, title logo, HUD icons and the red moon."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

from common import (Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, dilate, erode, rng,
                    lighten_arr, bresenham, smoothstep, quant_idx, bayer, upscale, font,
                    FONT_SERIF_JP_BLACK, FONT_SERIF_JP_BOLD, FONT_SERIF_JP_REG)


def gauss(x, y, cx, cy, sx, sy):
    return np.exp(-(((x - cx) / sx) ** 2 + ((y - cy) / sy) ** 2))


def shade_normals(h, light, amb=0.12):
    gy, gx = np.gradient(h)
    n = np.stack([-gx, -gy, np.ones_like(h)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    L = np.asarray(light, np.float64)
    L = L / np.linalg.norm(L)
    return amb + np.clip((n * L).sum(-1), 0, 1)


def draw_strand(arr, pts, col, hl=None, width=1, r=None):
    """Polyline strand on an (H,W,4) float array with optional glint pixels."""
    H, W = arr.shape[:2]
    for i in range(len(pts) - 1):
        for (px_, py_) in bresenham(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]):
            for wx in range(width):
                xx = px_ + wx
                if 0 <= xx < W and 0 <= py_ < H:
                    arr[py_, xx] = C(col)
            if hl is not None and r is not None and r.random() < 0.06 and 0 <= px_ < W and 0 <= py_ < H:
                arr[py_, px_] = C(hl)


def crack_walk(arr, x0, y0, ang, length, r, dark, light=None, branch=0.12, mask=None, depth=0):
    H, W = arr.shape[:2]
    px_, py_ = float(x0), float(y0)
    for i in range(int(length)):
        xi, yi = int(round(px_)), int(round(py_))
        if not (0 <= xi < W and 0 <= yi < H):
            break
        if mask is not None and not mask[yi, xi]:
            break
        arr[yi, xi] = C(dark)
        if light is not None and 0 <= xi + 1 < W and (mask is None or mask[yi, xi + 1]):
            if r.random() < 0.5:
                arr[yi, min(W - 1, xi + 1)] = C(light)
        ang += (r.random() - 0.5) * 0.8
        px_ += math.cos(ang)
        py_ += math.sin(ang)
        if depth < 2 and r.random() < branch:
            crack_walk(arr, px_, py_, ang + (0.9 if r.random() < 0.5 else -0.9), length * 0.45, r, dark,
                       light, branch * 0.6, mask, depth + 1)


# ---------------------------------------------------------------------------
# jump scare: the bride
# ---------------------------------------------------------------------------
SKIN_DEAD = ramp(['#070708', '#1c1c1c', '#36352f', '#55534a', '#7a776b', '#9e9b8e', '#bdbaad', '#d2cfc2',
                  '#dedbcf'], 9)


@texture('gui/jumpscare_bride', size=(256, 256), opaque=True)
def jumpscare_bride():
    N = 128
    x, y = grid(N, N)
    r = rng(2001)
    out = np.zeros((N, N, 4), np.float32)
    out[..., 3] = 255
    # ---- background: almost black, a breath of dried red
    bgv = 0.06 + fnoise(N, N, 2002, beta=2.0) * 0.02
    out[..., :3] = np.array([18, 8, 8]) * (0.6 + bgv[..., None] * 4)
    # ---- face height field
    cx, cy = 64.0, 73.0
    u = (x - cx) / (45.0 - smoothstep(80, 130, y) * 9.0)
    v = (y - cy) / 60.0
    r2 = u * u + v * v
    face = r2 <= 1.0
    h = np.sqrt(np.clip(1 - r2, 0, 1)) * 34
    for ex in (42, 86):
        h -= 11 * gauss(x, y, ex, 63, 13, 9.5)
    h += 4 * gauss(x, y, 64, 49, 32, 5)
    h += 7 * np.exp(-((x - 64) / 4.5) ** 2) * smoothstep(56, 70, y) * smoothstep(92, 82, y)
    for chx in (34, 94):
        h += 5 * gauss(x, y, chx, 80, 11, 7)
        h -= 4 * gauss(x, y, chx + (6 if chx < 64 else -6), 92, 7, 6)   # hollow cheeks
    h -= 5 * np.exp(-((y - 101) / 6) ** 2) * smoothstep(18, 30, x) * smoothstep(110, 98, x)
    h += 4 * gauss(x, y, 64, 118, 13, 6)
    h += fnoise(N, N, 2003, beta=2.2) * 0.8
    lit = shade_normals(h, (0.18, 0.80, 0.55), amb=0.10)
    lit *= 0.75 + 0.35 * smoothstep(30, 110, y)          # lamp below the chin
    mott = fnoise(N, N, 2004, beta=1.6) * 0.06
    val = np.clip(lit * 0.95 + mott, 0, 1)
    n = len(SKIN_DEAD)
    idx = np.clip(np.floor(val * n + bayer(N, N) * 0.7), 0, n - 1).astype(int)
    skin = SKIN_DEAD[idx]
    out[face] = skin[face]
    # ---- wataboshi hood (white silk) framing the face, covering the brow
    open_ = (((x - 64) / 50.0) ** 2 + ((y - 84) / 49.0) ** 2 <= 1.0) | ((np.abs(x - 64) < 50) & (y > 84))
    hood_outer = ((x - 64) / 80.0) ** 2 + ((y - 64) / 92.0) ** 2 <= 1.0
    hood = hood_outer & ~open_
    HOOD = ramp(['#141413', '#2c2b28', '#4b4944', '#6e6b64', '#938f86', '#b2aea3', '#c8c4b8'], 7)
    folds = np.sin(x * 0.12 + 0.3 * np.sin(y * 0.04)) * 0.045
    hv = 0.16 + 0.50 * smoothstep(0, 70, y) * (1 - 0.6 * smoothstep(30, 80, np.abs(x - 64))) + folds
    hv += 0.30 * gauss(x, y, 64, 37, 44, 6)               # the lit front rim of the hood
    hv += 0.18 * smoothstep(60, 110, y) * smoothstep(40, 70, np.abs(x - 64))
    hv = np.clip(hv, 0, 0.999)
    hidx = np.clip(np.floor(hv * 7 + bayer(N, N) * 0.6), 0, 6).astype(int)
    out[hood] = HOOD[hidx][hood]
    # deep shadow under the hood rim / between hood and face
    gap = open_ & ~face
    out[gap] = C('#040303')
    rim_sh = open_ & face & (y < 50) & ~(((x - 64) / 47.0) ** 2 + ((y - 90) / 46.0) ** 2 <= 1.0)
    out[rim_sh, :3] *= 0.35
    # ---- eyes: black voids, tiny pinprick pupils, black tears
    for (ex, ey, px_, sc) in ((42, 64, 44, 1.1), (86, 65, 84, 0.95)):
        dsock = np.sqrt(((x - ex) / (14.0 * sc)) ** 2 + ((y - ey) / (10.5 * sc)) ** 2)
        f = 0.3 + 0.7 * smoothstep(0.55, 1.05, dsock)
        out[face, :3] *= f[face][..., None]
        jitter = fnoise(N, N, 2010 + ex, beta=2.0) * 0.12
        eye = ((x - ex) / (10.0 * sc)) ** 2 + ((y - ey) / (6.8 * sc)) ** 2 + jitter <= 1.0
        low_lid = dilate(eye, 1) & ~eye & (y > ey + 1)
        out[low_lid] = C('#3a0c0a')
        out[dilate(low_lid, 1) & ~eye & ~low_lid & (y > ey + 2)] *= np.array([0.8, 0.6, 0.6, 1.0])
        out[eye] = C('#010101')
        out[ey - 1:ey + 1, px_:px_ + 2] = C('#ece8dc')
        out[ey - 2, px_ + 1] = C('#8a877e')
        # tear streaks of black fluid
        for k, dx in enumerate((-3, 2, 5)):
            ln = 10 + k * 7
            for t_ in range(ln):
                yy = ey + 7 + t_
                xx = ex + dx + int(round(math.sin(t_ * 0.3 + k) * 0.8))
                if 0 <= yy < N:
                    out[yy, xx] = C('#050404') if t_ < ln - 2 else C('#1a1716')
    # ---- nose: shadowless (underlit) with two dark nostrils
    out[85:87, 59:62] = C('#141313')
    out[85:87, 67:70] = C('#141313')
    # ---- the mouth, stretched far too wide
    mouth = np.zeros((N, N), bool)
    for xx in range(18, 111):
        tpos = (xx - 64) / 46.0
        center = 101 - 9 * tpos ** 4 - (6 if abs(tpos) > 0.85 else 0) * (abs(tpos) - 0.85) / 0.15
        half = max(0.6, 9.5 * (1 - tpos ** 2) ** 0.8)
        y0, y1 = int(round(center - half * 0.8)), int(round(center + half))
        mouth[max(0, y0):min(N, y1 + 1), xx] = True
    lips = dilate(mouth, 2) & ~mouth
    out[lips, :3] = out[lips, :3] * 0.35 + np.array([40, 24, 26]) * 0.65
    out[mouth] = C('#020101')
    # teeth: uneven, too many
    TEETH = pal('#5e5646', '#8a816a', '#aca288', '#c2b99e')
    cols = np.nonzero(mouth.any(0))[0]
    for side in ('top', 'bot'):
        xx = cols.min() + 4
        while xx < cols.max() - 4:
            w = int(r.integers(3, 7)) if side == 'top' else int(r.integers(3, 5))
            col_ys = np.nonzero(mouth[:, xx])[0]
            if len(col_ys) < 5:
                xx += w
                continue
            top, bot = col_ys.min(), col_ys.max()
            hgt = int(r.integers(2, 8)) if abs(xx - 64) < 30 else int(r.integers(2, 4))
            if side == 'bot':
                hgt = max(2, hgt - 2)
            if r.random() < 0.16:
                xx += w + 1
                continue
            for k in range(w - 1):
                for j in range(hgt):
                    yy = top + j if side == 'top' else bot - j
                    if mouth[yy, xx + k]:
                        shade = 3 if j < 1 else (2 if k < w - 2 else 1)
                        if (xx * 7) % 5 == 0:
                            shade = max(0, shade - 1)
                        if side == 'bot':
                            shade = max(0, shade - 1)
                        out[yy, xx + k] = TEETH[shade]
            gum_y = top if side == 'top' else bot
            out[gum_y, xx:xx + w - 1] = C('#3e0e0b')
            xx += w
    # torn mouth corners running up into the cheeks
    for (sx, sy, ang) in ((19, 95, -2.3), (110, 95, -0.85)):
        crack_walk(out, sx, sy, ang, 16, r, '#1a0605', None, branch=0.0)
        crack_walk(out, sx, sy + 1, ang + 0.2, 12, r, '#3a0d0a', None, branch=0.0)
        for t_ in range(9):
            yy = sy + 3 + t_
            out[yy, sx + (1 if sx < 64 else -1)] = C('#2e0907') if t_ < 7 else C('#4a100c')
    # ---- cracked dead skin
    fmask = face & ~mouth
    for (sx, sy, ang, ln, lt) in ((30, 50, 1.25, 30, True), (99, 48, 1.85, 26, False), (56, 44, 1.6, 14, False),
                                  (78, 80, 1.3, 14, False), (64, 117, -1.6, 9, False), (27, 74, 1.4, 20, True),
                                  (40, 108, 2.4, 14, True), (90, 108, 0.7, 14, False), (70, 42, 1.3, 18, True),
                                  (50, 78, 2.2, 12, False)):
        crack_walk(out, sx, sy, ang, ln, r, '#24211e', '#cfcbbd' if lt else None, branch=0.07, mask=fmask)
    # ---- wet black hair: clumps falling from under the hood, strands plastered over the face
    HAIR, GLINT = '#030303', '#3c3d46'
    clumps = (20, 31, 47, 58, 71, 84, 97, 108)
    for c_i, cxc in enumerate(clumps):
        col = np.nonzero(open_[:, cxc])[0]
        sy = int(col.min()) if len(col) else 40
        for j in range(3 if c_i in (0, 7) else 2):
            sx = cxc + j * 2 - 2
            length = int(r.integers(26, 70)) if 1 < c_i < 6 else int(r.integers(60, 100))
            sway = (r.random() - 0.5) * 0.5 + (0.25 if c_i < 4 else -0.25) * (1 < c_i < 6)
            amp = 2 + r.random() * 5
            ph = r.random() * 6
            pts = []
            for t_ in range(0, length, 2):
                px_ = sx + sway * t_ + amp * math.sin(t_ * 0.06 + ph) * smoothstep(0, 20, t_)
                pts.append((int(round(px_)), sy + t_))
            draw_strand(out, pts, HAIR, GLINT, width=1 + (j == 0 and c_i in (0, 7)), r=r)
    # strands stuck diagonally across the eye and cheek
    for (sx, sy, ex_, ey_, amp) in ((50, 40, 34, 96, 3.0), (54, 40, 44, 108, 2.5), (80, 40, 98, 92, 2.0)):
        pts = []
        for t_ in np.linspace(0, 1, 40):
            px_ = sx + (ex_ - sx) * t_ + amp * math.sin(t_ * 9)
            py_ = sy + (ey_ - sy) * t_
            pts.append((int(round(px_)), int(round(py_))))
        draw_strand(out, pts, HAIR, GLINT, width=1, r=r)
    # thick wet clumps at the sides of the face
    for (sx, sgn) in ((19, 1), (109, -1)):
        for j in range(3):
            pts = [(sx + sgn * j * 2 + int(round(3 * math.sin(t_ * 0.05 + j))), 48 + t_) for t_ in range(0, 82, 2)]
            draw_strand(out, pts, HAIR, GLINT, width=2, r=r)
    # ---- harsh contrast grade + vignette
    xx_, yy_ = (x - 64) / 64, (y - 64) / 64
    vig = 1 - 0.55 * smoothstep(0.55, 1.35, np.sqrt(xx_ ** 2 + yy_ ** 2))
    out[..., :3] *= vig[..., None]
    return upscale(out, 2)


# ---------------------------------------------------------------------------
# jump scare: the Ichimatsu doll
# ---------------------------------------------------------------------------
PORC = ramp(['#0b0a0b', '#2a2624', '#4f4943', '#7a7268', '#a59c8f', '#c9c0b0', '#ddd5c5', '#e8e1d2',
             '#efe9db'], 9)


@texture('gui/jumpscare_doll', size=(256, 256), opaque=True)
def jumpscare_doll():
    N = 128
    x, y = grid(N, N)
    r = rng(2101)
    out = np.zeros((N, N, 4), np.float32)
    out[..., 3] = 255
    out[..., :3] = np.array([10, 6, 6])
    cx, cy = 64.0, 74.0
    u = (x - cx) / 50.0
    v = (y - cy) / 55.0
    r2 = u * u + v * v
    face = r2 <= 1.0
    h = np.sqrt(np.clip(1 - r2, 0, 1)) * 30
    for ex in (44, 84):
        h -= 4 * gauss(x, y, ex, 70, 12, 8)
    for chx in (38, 90):
        h += 4 * gauss(x, y, chx, 90, 13, 10)
    h += 3 * np.exp(-((x - 64) / 4.0) ** 2) * smoothstep(66, 76, y) * smoothstep(90, 84, y)
    lit = shade_normals(h, (-0.35, -0.55, 0.75), amb=0.10)
    lit *= 1 - 0.35 * smoothstep(0.55, 1.0, np.sqrt(r2))
    val = np.clip(lit * 0.98, 0, 1)
    n = len(PORC)
    idx = np.clip(np.floor(val * n + bayer(N, N) * 0.6), 0, n - 1).astype(int)
    out[face] = PORC[idx][face]
    # glossy porcelain highlights
    for (hx, hy, sx, sy) in ((40, 52, 9, 4), (36, 86, 5, 3)):
        g = gauss(x, y, hx, hy, sx, sy) > 0.5
        out[g & face] = PORC[8]
    # faint painted blush
    for chx in (38, 90):
        b = gauss(x, y, chx, 92, 10, 6) * face
        out[..., :3] = out[..., :3] * (1 - 0.18 * b[..., None]) + np.array([150, 70, 66]) * 0.18 * b[..., None]
    # ---- black hime-cut bob: straight bangs, straight side locks
    HAIRP = pal('#020202', '#070707', '#0e0e10', '#1b1b20', '#2f3038', '#4a4b56')
    bangs = (y <= 47) & (np.abs(x - 64) < 70)
    sides = (np.abs(x - 64) > 44) & (y <= 128)
    hair = bangs | (sides & ~(face & (np.abs(x - 64) <= 44)))
    hair |= ~face & (y < 96)
    dr = np.sqrt(((x - 64) / 1.35) ** 2 + (y - 70) ** 2)
    sheen = np.exp(-((dr - 52) / 4.5) ** 2) * (1 - smoothstep(14, 62, np.abs(x - 54))) * (y < 44)
    sheen += 0.5 * np.exp(-((x - 10) / 3.0) ** 2) * smoothstep(50, 70, y) * (1 - smoothstep(90, 120, y))
    sheen += 0.5 * np.exp(-((x - 118) / 3.0) ** 2) * smoothstep(50, 70, y) * (1 - smoothstep(90, 120, y))
    hv = 1.0 + (x.astype(int) % 5 == 0) * 1.0 + (x.astype(int) % 5 == 2) * 0.5 + sheen * 3.6
    hv += bayer(N, N) * 0.8
    hi = np.clip(np.round(hv), 0, 5).astype(int)
    out[hair] = HAIRP[hi][hair]
    # razor-straight fringe edge
    for xx in range(N):
        out[47, xx] = HAIRP[0]
        if xx % 9 == 3:
            out[48, xx] = HAIRP[1]
    # ---- huge glossy black eyes
    for (ex, ey) in ((44, 70), (84, 70)):
        eye = ((x - ex) / 13.0) ** 2 + ((y - ey) / 8.8) ** 2 <= 1.0
        eye &= ~(((x - ex) / 15.0) ** 2 + ((y - ey + 11.0) / 8.5) ** 2 <= 1.0)  # almond: heavy top lid
        lid = dilate(eye, 1) & ~eye
        out[lid] = C('#141010')
        out[eye] = C('#020203')
        # glassy reflections
        out[ey - 4:ey - 1, ex - 7:ex - 3] = C('#d6d4d0')
        out[ey - 4, ex - 7] = C('#6e6d72')
        out[ey - 1, ex - 3] = C('#6e6d72')
        out[ey + 1, ex + 1:ex + 3] = C('#3a3a44')
        out[ey + 3, ex + 4:ex + 6] = C('#2a2a32')
        # painted upper lid, flicked out at the outer corner
        lidrow = np.nonzero(eye.any(0))[0]
        for xx in range(lidrow.min() - 1, lidrow.max() + 2):
            col = np.nonzero(eye[:, xx])[0] if 0 <= xx < N and eye[:, xx].any() else None
            top = (col.min() - 1) if col is not None else ey - 3
            out[top, xx] = C('#0e0a0a')
        outer = lidrow.max() + 2 if ex > 64 else lidrow.min() - 2
        step = 1 if ex > 64 else -1
        for k in range(3):
            out[ey - 3 - k, outer + step * k] = C('#0e0a0a')
    # thin painted brows high on the forehead (peeking under the fringe)
    for ex in (44, 84):
        out[51, ex - 6:ex + 6] = C('#5e5650')
    # ---- the thin red smile, a little too wide, a little crooked
    for xx in range(40, 90):
        tpos = (xx - 64) / 24.0
        yy = int(round(104 - 7 * tpos ** 2 - (1.5 * tpos if tpos > 0 else 0)))
        out[yy, xx] = C('#7c0f0c')
        if abs(tpos) < 0.5:
            out[yy + 1, xx] = C('#4a0806')
    out[107, 60:69] = C('#5e1410')
    # a dark tear has run from the left eye
    for t_ in range(26):
        yy = 79 + t_
        xx = 41 + int(round(math.sin(t_ * 0.25) * 0.7))
        out[yy, xx] = C('#160606') if t_ < 22 else C('#3a0e0a')
        if t_ < 8:
            out[yy, xx + 1] = C('#2a0a08')
    # ---- cracked porcelain with a chipped piece over one eye
    chip = np.zeros((N, N), bool)
    chip_poly = Image.new('L', (N, N), 0)
    ImageDraw.Draw(chip_poly).polygon([(92, 50), (104, 56), (100, 72), (93, 66), (88, 60)], fill=255)
    chip = np.asarray(chip_poly) > 0
    out[chip & face] = C('#070505')
    edge_c = dilate(chip, 1) & ~chip & face
    out[edge_c] = PORC[7]
    fmask = face & ~chip
    for (sx, sy, ang, ln, lt) in ((96, 66, 1.9, 44, True), (90, 56, -2.8, 30, False), (31, 62, 1.35, 34, True),
                                  (60, 50, 1.75, 14, False)):
        crack_walk(out, sx, sy, ang, ln, r, '#1a1615', '#f2ecdf' if lt else None, branch=0.08, mask=fmask)
    xx_, yy_ = (x - 64) / 64, (y - 64) / 64
    vig = 1 - 0.5 * smoothstep(0.6, 1.4, np.sqrt(xx_ ** 2 + yy_ ** 2))
    out[..., :3] *= vig[..., None]
    return upscale(out, 2)


# ---------------------------------------------------------------------------
# overlays
# ---------------------------------------------------------------------------
@texture('gui/vignette', size=(256, 256))
def vignette():
    N = 256
    x, y = grid(N, N)
    dx = (x - 127.5) / 127.5
    dy = (y - 127.5) / 127.5
    d = np.sqrt(dx ** 2 + dy ** 2) / math.sqrt(2)
    ang = np.arctan2(dy, dx)
    r = rng(2201)
    an = np.zeros_like(d)
    for k in range(2, 9):
        an += r.normal() / k * np.sin(k * ang + r.random() * 6.283)
    dd = d * (1 + 0.07 * an) + 0.025 * fnoise(N, N, 2202, beta=2.2)
    a = smoothstep(0.26, 0.86, dd) ** 1.15
    out = np.zeros((N, N, 4), np.float32)
    out[..., 3] = np.clip(a * 255, 0, 255)
    return out


@texture('gui/grain', size=(256, 256), tile=False)
def grain():
    N = 256
    n = fnoise(N, N, 2301, beta=0.35)
    n2 = fnoise(N, N, 2302, beta=0.5)
    g = np.clip(128 + n * 40, 28, 228)
    out = np.zeros((N, N, 4), np.float32)
    out[..., 0] = out[..., 1] = out[..., 2] = g
    out[..., 3] = 60 + 50 * np.clip(np.abs(n2) / 2.2, 0, 1)
    return out


@texture('gui/paper', size=(256, 256))
def paper():
    N = 256
    x, y = grid(N, N)
    r = rng(2401)

    def edge1d(seed, base, amp):
        e = fnoise(N, 1, seed, beta=1.6)[0]
        f = fnoise(N, 1, seed + 1, beta=0.6)[0]
        return base + e * amp + f * 0.8

    left = edge1d(2402, 9, 2.2)[y.astype(int)]
    right = (N - 1 - edge1d(2404, 9, 2.2))[y.astype(int)]
    top = edge1d(2406, 8, 2.0)[x.astype(int)]
    bot = (N - 1 - edge1d(2408, 10, 2.6))[x.astype(int)]
    sheet = (x > left) & (x < right) & (y > top) & (y < bot)
    # a torn-off corner and a bite out of the edge
    sheet &= ~((x - 246) + (y - 238) * 0.9 > -16 + fnoise(N, N, 2410, beta=1.2) * 2)
    sheet &= ~(((x - 6) / 8.0) ** 2 + ((y - 150) / 14.0) ** 2 < 1)
    dist = ndimage.distance_transform_edt(sheet)
    P = ramp(['#8b7d60', '#a3967a', '#b7ab8f', '#c6bb9f', '#d0c6ab', '#d8cfb6'], 6)
    v = 0.66 + fnoise(N, N, 2411, beta=1.9) * 0.05 + fnoise(N, N, 2412, beta=1.0) * 0.025
    v -= 0.22 * (1 - smoothstep(0, 26, dist))             # yellowed, dirtier edges
    idx = np.clip(np.floor(v * 6 + bayer(N, N) * 0.9), 0, 5).astype(int)
    out = np.zeros((N, N, 4), np.float32)
    out[...] = P[idx]
    # kozo fibres
    for k in range(260):
        px_, py_ = r.random() * N, r.random() * N
        ang = r.random() * math.pi * 2
        ln = int(r.integers(5, 26)) if k > 20 else int(r.integers(30, 70))
        col = P[5] if r.random() < 0.6 else P[2]
        for _ in range(ln):
            xi, yi = int(px_), int(py_)
            if 0 <= xi < N and 0 <= yi < N:
                out[yi, xi, :3] = out[yi, xi, :3] * 0.5 + col[:3] * 0.5
            ang += (r.random() - 0.5) * 0.5
            px_ += math.cos(ang)
            py_ += math.sin(ang)
    # tide-mark stains
    for (sx, sy, rx, ry, sd) in ((190, 60, 34, 26, 2420), (60, 205, 40, 22, 2421), (120, 128, 18, 14, 2422)):
        f = ((x - sx) / rx) ** 2 + ((y - sy) / ry) ** 2 + fnoise(N, N, sd, beta=2.0) * 0.32
        m = f < 1
        out[m, :3] *= np.array([0.97, 0.95, 0.91])
        ring = (f < 1) & (f > 0.88) & (fnoise(N, N, sd + 7, beta=1.5) > -0.6)
        out[ring, :3] *= np.array([0.90, 0.86, 0.78])
    # foxing: clusters of brown spots
    for _ in range(14):
        cx, cy = r.integers(14, N - 14), r.integers(14, N - 14)
        for _j in range(int(r.integers(2, 7))):
            fx, fy = cx + r.integers(-8, 9), cy + r.integers(-8, 9)
            rr = r.random() * 2.0 + 0.6
            m = (x - fx) ** 2 + (y - fy) ** 2 <= rr * rr
            out[m, :3] = out[m, :3] * 0.62 + np.array([110, 72, 40]) * 0.38
    # fold creases
    for yy in range(N):
        out[yy, 128, :3] *= 0.9
        out[yy, 129, :3] = np.minimum(out[yy, 129, :3] * 1.05, 228)
    for xx in range(N):
        out[118, xx, :3] *= 0.92
    # a faint brown-red fingerprint near the lower corner
    fp = ((x - 214) / 7.0) ** 2 + ((y - 214) / 9.0) ** 2 < 1
    ridges = (np.sin(np.sqrt((x - 214) ** 2 + ((y - 214) * 0.8) ** 2) * 1.9) > 0.2)
    out[fp & ridges, :3] = out[fp & ridges, :3] * 0.75 + np.array([90, 36, 26]) * 0.25
    # alpha: sheet + ragged fibrous fringe
    alpha = sheet.astype(np.float32) * 255
    fringe = dilate(sheet, 2) & ~sheet & (white(N, N, 2430) < 0.35)
    alpha[fringe] = 150
    out[fringe] = P[2]
    out[..., 3] = alpha
    return out


# ---------------------------------------------------------------------------
# title logo
# ---------------------------------------------------------------------------
@texture('gui/title_logo', size=(512, 256))
def title_logo():
    S = 2
    W, H = 512 * S, 256 * S
    r = rng(2501)
    m = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(m)
    f = font(FONT_SERIF_JP_BLACK, 196 * S)
    text = '冥婚'
    bb = d.textbbox((0, 0), text, font=f)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    tx = (W - tw) // 2 - bb[0]
    ty = 14 * S - bb[1]
    d.text((tx, ty), text, font=f, fill=255)
    mask = np.asarray(m, np.float32) / 255.0
    yy, xx = np.mgrid[0:H, 0:W]
    # brushy distress: streaks along the stroke direction + eroded ragged edges
    inside = mask > 0.5
    dist = ndimage.distance_transform_edt(inside)
    streak = fnoise(W, H, 2502, beta=1.2, sx=0.35, sy=3.0)
    streak2 = fnoise(W, H, 2503, beta=1.2, sx=3.0, sy=0.35)
    rough = fnoise(W, H, 2504, beta=1.0)
    keep = inside & (dist + rough * 1.3 * S + np.minimum(streak, streak2) * 0.8 * S > 1.0 * S)
    # dry-brush gaps scraping along the stroke edges only
    gaps = (streak > 2.05) & (dist < 4.5 * S)
    keep &= ~gaps
    # bristle fibres flicking out of the strokes
    flick = (~inside) & (ndimage.distance_transform_edt(~inside) < 2.5 * S) & (streak2 > 1.9)
    keep |= flick
    # drips from the lower edges of the strokes
    drip = np.zeros((H, W), bool)
    below = inside & ~np.roll(inside, -1, 0)
    cand = np.argwhere(below)
    cand = cand[cand[:, 0] < H - 90 * S]
    sel = cand[r.choice(len(cand), size=26, replace=False)]
    for (cy, cx) in sel:
        ln = int(r.integers(8, 52)) * S
        wd = r.random() * 2.2 * S + 1.4 * S
        for t_ in range(ln):
            w_ = wd * (1 - 0.55 * t_ / ln)
            y_ = cy + t_
            if y_ >= H:
                break
            x0, x1 = int(cx - w_), int(cx + w_) + 1
            drip[y_, max(0, x0):min(W, x1)] = True
        rr = wd * 1.25
        bl = ((xx - cx) ** 2 + (yy - (cy + ln)) ** 2) <= rr * rr
        drip |= bl
    alpha = (keep | drip).astype(np.float32)
    # colour: deep blood red, darker towards the stroke edges, wet glints inside
    full = keep | drip
    dist2 = ndimage.distance_transform_edt(full)
    tone = smoothstep(0, 5 * S, dist2) + fnoise(W, H, 2505, beta=1.6) * 0.12
    c_edge = np.array([46, 5, 5], np.float32)
    c_mid = np.array([112, 13, 10], np.float32)
    c_core = np.array([140, 20, 14], np.float32)
    t1 = np.clip(tone, 0, 1)[..., None]
    rgb = c_edge * (1 - t1) + c_mid * t1
    core = smoothstep(0.75, 1.2, tone)[..., None]
    rgb = rgb * (1 - core) + c_core * core
    glint = (fnoise(W, H, 2506, beta=1.4, sx=0.5, sy=2.0) > 2.1) & (dist2 > 5 * S)
    rgb[glint] = rgb[glint] * 0.6 + np.array([150, 40, 32]) * 0.4
    out = np.zeros((H, W, 4), np.float32)
    out[..., :3] = rgb
    out[..., 3] = alpha * 255
    # "MEIKON", widely spaced, pale grey
    lab = Image.new('L', (W, H), 0)
    ld = ImageDraw.Draw(lab)
    lf = font(FONT_SERIF_JP_REG, 25 * S)
    word = 'MEIKON'
    spacing = 22 * S
    widths = [ld.textbbox((0, 0), ch, font=lf)[2] - ld.textbbox((0, 0), ch, font=lf)[0] for ch in word]
    total = sum(widths) + spacing * (len(word) - 1)
    px_ = (W - total) // 2
    py_ = 218 * S
    for ch, w_ in zip(word, widths):
        b0 = ld.textbbox((0, 0), ch, font=lf)
        ld.text((px_ - b0[0], py_ - b0[1]), ch, font=lf, fill=255)
        px_ += w_ + spacing
    la = np.asarray(lab, np.float32) / 255.0
    la *= 0.82 + 0.18 * (fnoise(W, H, 2507, beta=1.0) > -0.8)
    lm = la > 0.02
    out[lm, :3] = np.array([178, 174, 166])
    out[lm, 3] = np.maximum(out[lm, 3], la[lm] * 235)
    # thin rules either side of the word
    start_x = (W - total) // 2
    end_x = start_x + total
    for (a_, b_) in ((start_x - 86 * S, start_x - 18 * S), (end_x + 18 * S, end_x + 86 * S)):
        out[py_ + 13 * S:py_ + 13 * S + S, a_:b_, :3] = np.array([120, 116, 110])
        out[py_ + 13 * S:py_ + 13 * S + S, a_:b_, 3] = 150
    # downsample 2x (premultiplied box filter)
    pm = out.copy()
    pm[..., :3] *= pm[..., 3:4] / 255.0
    pm = pm.reshape(256, S, 512, S, 4).mean((1, 3))
    a_ = pm[..., 3:4]
    res = np.zeros_like(pm)
    res[..., :3] = np.where(a_ > 0, pm[..., :3] / np.maximum(a_, 1e-6) * 255.0, 0)
    res[..., 3] = pm[..., 3]
    return res


# ---------------------------------------------------------------------------
# HUD bells
# ---------------------------------------------------------------------------
def _hud_bell_tex():
    from t_items import bell, outline
    P = ramp(['#4a3412', '#6f5220', '#977532', '#ba9849', '#d6b864', '#ecd896'], 6)
    t = bell(Tex(16), 7.5, 9.5, 4.8, P)
    t.set(5, 7, '#f6ebc4')
    return outline(t, 0.3)


@texture('gui/hud_bell', cutout=True)
def hud_bell():
    return _hud_bell_tex()


@texture('gui/hud_bell_empty')
def hud_bell_empty():
    t = _hud_bell_tex()
    m = t.a[..., 3] > 0
    ring = m & ~erode(m, 1)
    out = Tex(16)
    out.a[m] = C('#141414', 70)
    out.a[ring] = C('#5c5a56')
    # slit hint
    for xx in range(6, 10):
        out.a[12, xx] = C('#3e3d3a', 200)
    return out


# ---------------------------------------------------------------------------
# hands reaching in from the edges
# ---------------------------------------------------------------------------
def _hand(draw, S, wrist, ang, scale, curl, spread, seed):
    """Thin pale hand: forearm, tapered palm, four long jointed fingers, thumb."""
    r = rng(seed)
    ca, sa = math.cos(ang), math.sin(ang)
    px, py = -sa, ca
    wx, wy = wrist
    draw.line([((wx - ca * 90) * S, (wy - sa * 90) * S), (wx * S, wy * S)], fill=255,
              width=int(5.6 * scale * S))
    L, w0, w1 = 10.5 * scale, 2.9 * scale, 4.3 * scale
    kx, ky = wx + ca * L, wy + sa * L
    pal_ = [(wx + px * w0, wy + py * w0), (kx + px * w1, ky + py * w1), (kx - px * w1, ky - py * w1),
            (wx - px * w0, wy - py * w0)]
    draw.polygon([(a * S, b * S) for a, b in pal_], fill=255)
    tips = []
    sgn = 1 if curl >= 0 else -1
    for i, o in enumerate((-1.5, -0.5, 0.5, 1.5)):
        bx = kx + px * o * w1 * 0.56
        by = ky + py * o * w1 * 0.56
        a = ang + o * spread
        pts = [(bx, by)]
        ln = (0.82, 1.0, 0.96, 0.74)[i]
        for seg_len in (8.0, 6.5, 5.0):
            seg = seg_len * scale * ln * 1.3
            bx += math.cos(a) * seg
            by += math.sin(a) * seg
            pts.append((bx, by))
            a += abs(curl) * sgn * (0.6 + 0.8 * r.random())
        draw.line([(p[0] * S, p[1] * S) for p in pts], fill=255, width=int(2.3 * scale * S), joint='curve')
        for (qx, qy) in pts[1:-1]:      # knuckles slightly thicker
            rr = 1.2 * scale
            draw.ellipse([(qx - rr) * S, (qy - rr) * S, (qx + rr) * S, (qy + rr) * S], fill=255)
        tips.append((pts[-1], a))
    # thumb on the leading side
    ta = ang - sgn * 0.95
    tx0, ty0 = wx + ca * L * 0.35 - px * sgn * w0, wy + sa * L * 0.35 - py * sgn * w0
    tp = [(tx0, ty0)]
    a = ta
    for seg_len in (6.0, 5.0):
        tx0 += math.cos(a) * seg_len * scale
        ty0 += math.sin(a) * seg_len * scale
        tp.append((tx0, ty0))
        a += sgn * 0.5
    draw.line([(p[0] * S, p[1] * S) for p in tp], fill=255, width=int(2.1 * scale * S), joint='curve')
    tips.append((tp[-1], a))
    return tips


@texture('gui/hands', size=(256, 256))
def hands():
    N, S = 128, 4
    im = Image.new('L', (N * S, N * S), 0)
    d = ImageDraw.Draw(im)
    specs = [  # wrist, angle, scale, curl, spread
        ((2, 86), -0.30, 0.92, 0.18, 0.10),
        ((1, 32), 0.42, 0.85, 0.22, 0.09),
        ((126, 50), math.pi + 0.28, 0.92, -0.18, 0.10),
        ((127, 100), math.pi - 0.42, 0.85, -0.16, 0.09),
        ((38, 128), -1.78, 0.92, 0.18, 0.09),
        ((90, 129), -1.30, 0.88, -0.20, 0.10),
    ]
    tips_all = []
    for i, (wr, ang, sc, curl, spread) in enumerate(specs):
        tips_all += _hand(d, S, wr, ang, sc, curl, spread, 2600 + i)
    big = np.asarray(im, np.float32) / 255.0
    small = big.reshape(N, S, N, S).mean((1, 3))
    m = small > 0.42
    dist = ndimage.distance_transform_edt(m)
    HAND = ramp(['#4a4741', '#6e6a62', '#958f85', '#b5b0a5', '#cdc8bd', '#dad6cb'], 6)
    gy_, gx_ = np.gradient(ndimage.gaussian_filter(m.astype(float), 1.0))
    bevel = (-gx_ - gy_) * 2.2
    v = np.clip(dist / 2.4, 0, 1) * 3.2 + 1.2 + bevel + fnoise(N, N, 2610, beta=1.5) * 0.3
    idx = np.clip(np.round(v + bayer(N, N) * 0.5), 0, 5).astype(int)
    out = np.zeros((N, N, 4), np.float32)
    out[m] = HAND[idx][m]
    ring = dilate(m, 1) & ~m
    out[ring] = C('#2c2a27')
    shadow = dilate(m, 3) & ~dilate(m, 1)
    out[shadow] = C('#000000', 70)
    for ((tx, ty), a) in tips_all:
        for k in range(1, 3):
            xi = int(round(tx - math.cos(a) * k))
            yi = int(round(ty - math.sin(a) * k))
            if 0 <= xi < N and 0 <= yi < N and m[yi, xi]:
                out[yi, xi] = C('#55524c')
    return upscale(out, 2)


# ---------------------------------------------------------------------------
# environment: the red moon
# ---------------------------------------------------------------------------
@texture('environment/red_moon', size=(64, 64))
def red_moon():
    N = 64
    x, y = grid(N, N)
    cx = cy = 31.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    R = 19.5
    disc = d <= R
    MOON = ramp(['#2e0605', '#4a0b08', '#64110c', '#7e1a12', '#962419', '#a83021', '#b63c2a'], 7)
    limb = 1 - 0.55 * smoothstep(0.55, 1.0, d / R)
    maria = fnoise(N, N, 2701, beta=2.2)
    mar = (maria > 0.35) * 1.6 + (maria > 1.1) * 0.8
    v = 5.3 * limb - mar + fnoise(N, N, 2702, beta=1.0) * 0.35
    idx = np.clip(np.round(v + bayer(N, N) * 0.6), 0, 6).astype(int)
    out = np.zeros((N, N, 4), np.float32)
    out[disc] = MOON[idx][disc]
    out[disc, 3] = 255
    # craters
    for (kx, ky, kr) in ((24, 38, 2.0), (38, 22, 1.5), (34, 42, 1.2), (20, 26, 1.0)):
        cm = (x - kx) ** 2 + (y - ky) ** 2 <= kr * kr
        out[cm & disc, :3] *= 0.75
        rim = ((x - kx + 1) ** 2 + (y - ky + 1) ** 2 <= kr * kr) & ~cm & disc
        out[rim, :3] = np.minimum(out[rim, :3] * 1.15, 255)
    # glow
    g = ~disc
    ga = np.clip(1 - (d - R) / (N / 2 - R + 0.5), 0, 1) ** 2.2
    out[g, :3] = np.array([120, 22, 16])
    out[g, 3] = ga[g] * 165
    return out
