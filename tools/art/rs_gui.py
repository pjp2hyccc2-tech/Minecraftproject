"""GUI textures of 霊撮 ―REISATSU― (textures/gui/): title logo, the camera finder + 霊感計, instant photo frame,
the spirits' true names in red brush ink, failed-photo blur and 心霊写真 overlays, the 霊撮帳 album, HUD icons,
note paper, locker slits, the HIDAMARI sun. (gui/jumpscare_* and gui/medium_portrait belong to the spirits worker;
vignette and grain are the MEIKON/SOUNAN images.)

Layout facts the game relies on (also in the art report):
  finder 512x288   : 4:3 capture frame lines x 96..415, y 24..263; centre focus circle centred (256, 144), r 17,
                     inside the clean region x 232..280, y 120..168 (tint that region to turn it red)
  finder_meter     : needle pivot (64, 58), scale from -60 deg (left, 0) to +60 deg (right, max), 0 deg = straight up
  photo_frame      : photo window x 8..119, y 8..91 (112x84) is fully transparent; name margin y 96..152
  icons 128x32     : 16x16 cells, row 0: film, お札, eye closed, eye half, eye open, eye alert, breath, breath (gasp)
                     row 1: no film, burnt お札, camera, flashlight, key, note, star, empty star
"""
import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from common import (texture, C, ramp, pal, fnoise, vnoise, rng, grid, font, smoothstep, FONT_SERIF_JP_BLACK,
                    FONT_SERIF_JP_BOLD, FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK, FONT_SANS_JP_MED, FONT_SANS_BOLD,
                    FONT_SANS)
from rs_lib import (blank, over, rect, px, hline, vline, line, frame, dl, tint, disc_mask, ring_mask, text_mask,
                    stamp_mask, stamp_center, sun_logo, sun_small, bitmap, downsample, noise)

SS = 2


def gui(name, size, opaque=False):
    return texture('gui/' + name, size=size, opaque=opaque)


def blur(a, s):
    return ndimage.gaussian_filter(a, s, mode='nearest')


def mask_to_rgba(mask, color, alpha=1.0):
    h, w = mask.shape
    a = blank(w, h, color)
    a[..., 3] = np.clip(mask, 0, 1) * 255 * alpha
    return a


# ================================================================================================ brush ink
def brush_glyphs(text, size, font_spec, seed, wobble=0.9, dry=1.0, splatter=12, spacing=1.02):
    """Brush-calligraphy mask of `text`: wobbly outlines, dry-brush streaks along the strokes, ink pooling, splatter."""
    W, H = int(size * len(text) * spacing + size * 0.5), int(size * 1.45)
    im = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(im)
    f = font(font_spec, size)
    x = int(size * 0.22)
    for ch in text:
        if ch == ' ' or ch == '　':
            x += int(size * 0.45)
            continue
        bb = d.textbbox((0, 0), ch, font=f)
        d.text((x - bb[0], int(size * 0.2) - bb[1]), ch, fill=255, font=f)
        x += int((bb[2] - bb[0]) + size * (spacing - 0.92))
    m = np.asarray(im, np.float32) / 255.0
    r = rng(seed)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dx = blur(r.normal(0, 1, (H, W)), size * 0.05) * size * wobble
    dy = blur(r.normal(0, 1, (H, W)), size * 0.05) * size * wobble
    m = ndimage.map_coordinates(m, [yy + dy, xx + dx], order=1, mode='constant')
    pool = blur(m, size * 0.02)
    m = np.clip(m * 0.85 + pool * 0.4, 0, 1)
    gy, gx = np.gradient(blur(m, size * 0.03))
    Jxx, Jyy, Jxy = blur(gx * gx, size * 0.05), blur(gy * gy, size * 0.05), blur(gx * gy, size * 0.05)
    ang = 0.5 * np.arctan2(2 * Jxy, Jxx - Jyy)
    across = np.cos(ang) * xx + np.sin(ang) * yy
    streak = np.sin(across * (2 * math.pi / (size * 0.035)) + blur(r.normal(0, 1, (H, W)), 3) * 6)
    streak2 = np.sin(across * (2 * math.pi / (size * 0.013)) + blur(r.normal(0, 1, (H, W)), 2) * 4)
    edge_dist = ndimage.distance_transform_edt(m > 0.5)
    dryness = blur(r.random((H, W)), size * 0.18)
    dryness = (dryness - dryness.min()) / (np.ptp(dryness) + 1e-6)
    dry_zone = np.clip((dryness - 0.5) * 3.0 * dry, 0, 1) * (0.55 + 0.45 * np.exp(-edge_dist / (size * 0.05)))
    dryv = ((streak * 0.65 + streak2 * 0.35) > (1.08 - dry_zone * 1.05)) & (dry_zone > 0.05)
    ink = (m > 0.5) & ~dryv
    rough = blur(r.normal(0, 1, (H, W)), 1.2)
    ink &= (m + rough * 0.18) > 0.5
    ys, xs = np.nonzero(m > 0.5)
    for _ in range(splatter):
        if len(xs) == 0:
            break
        k = r.integers(0, len(xs))
        a_ = r.uniform(0, 2 * math.pi)
        dist = r.uniform(size * 0.05, size * 0.22)
        ox, oy = xs[k] + math.cos(a_) * dist, ys[k] + math.sin(a_) * dist
        rad = r.uniform(size * 0.006, size * 0.02)
        ink |= (xx - ox) ** 2 + (yy - oy) ** 2 < rad ** 2
    ys, xs = np.nonzero(ink)
    if len(xs):
        ink = ink[max(0, ys.min() - 2):ys.max() + 3, max(0, xs.min() - 2):xs.max() + 3]
    return ink.astype(np.float32)


# ================================================================================================ title logo
@gui('title_logo', (512, 256))
def title_logo():
    S = SS
    W, H = 512 * S, 256 * S
    out = np.zeros((H, W, 4), np.float32)
    ink = brush_glyphs('霊撮', 168 * S, FONT_SERIF_JP_BLACK, 15401)
    ih, iw = ink.shape
    ox, oy = int(40 * S), int(10 * S)
    canvas = np.zeros((H, W), np.float32)
    hh, ww = min(ih, H - oy), min(iw, W - ox)
    canvas[oy:oy + hh, ox:ox + ww] = ink[:hh, :ww]
    halo = np.clip(blur(canvas, 5 * S) * 2.2, 0, 1) * 0.72
    n = blur(rng(15402).normal(0, 1, (H, W)), 2) * 16
    col = np.zeros((H, W, 3), np.float32)
    col[..., 0] = 214 + n
    col[..., 1] = 208 + n * 0.95
    col[..., 2] = 196 + n * 0.9
    yy, xx = np.mgrid[0:H, 0:W]
    stain = blur(rng(15403).random((H, W)), 9 * S) * np.clip((yy - H * 0.3) / (H * 0.4), 0, 1)
    stain = np.clip((stain - 0.42) * 3, 0, 1)
    col = col * (1 - stain[..., None] * 0.5) + np.array([128, 20, 16]) * stain[..., None] * 0.5
    out[..., :3] = np.where(canvas[..., None] > 0, col, np.array([6, 5, 5]))
    out[..., 3] = np.maximum(canvas, halo) * 255
    # the medium's red seal (落款): 「八雲」 carved white in a red square, slightly rotated, uneven ink
    sz = int(62 * S)
    seal = Image.new('L', (sz, sz), 0)
    sd = ImageDraw.Draw(seal)
    sd.rectangle([2 * S, 2 * S, sz - 3 * S, sz - 3 * S], fill=255)
    sf = font(FONT_SERIF_JP_BOLD, int(25 * S))
    for i, ch in enumerate('八雲'):
        bb = sd.textbbox((0, 0), ch, font=sf)
        sd.text(((sz - (bb[2] - bb[0])) / 2 - bb[0], 6 * S + i * 25 * S - bb[1]), ch, fill=0, font=sf)
    sm = np.asarray(seal.rotate(-4, resample=Image.BILINEAR), np.float32) / 255.0
    worn = blur(rng(15404).random(sm.shape), 2.5 * S) > 0.47
    sm = sm * worn
    sx, sy = int(398 * S), int(118 * S)
    seal_col = np.array([196, 34, 26], np.float32)
    a = sm[..., None]
    reg = out[sy:sy + sz, sx:sx + sz]
    ra = reg[..., 3:4] / 255.0
    reg[..., :3] = reg[..., :3] * (1 - a) + seal_col * a
    reg[..., 3:4] = np.maximum(ra, a) * 255
    # REISATSU in spaced capitals between thin rules
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    fnt = font(FONT_SERIF_JP_BOLD, 24 * S)
    label = '  '.join('REISATSU')
    tw = d.textlength(label, font=fnt)
    ty = int(212 * S)
    d.text(((W - tw) / 2, ty), label, fill=255, font=fnt)
    lw = int(64 * S)
    d.line([((W - tw) / 2 - lw - 14 * S, ty + 16 * S), ((W - tw) / 2 - 14 * S, ty + 16 * S)], fill=200, width=S * 2)
    d.line([((W + tw) / 2 + 14 * S, ty + 16 * S), ((W + tw) / 2 + lw + 14 * S, ty + 16 * S)], fill=200, width=S * 2)
    lab = np.asarray(img, np.float32) / 255.0
    labc = np.array([200, 190, 176], np.float32)
    la = lab[..., None]
    out[..., :3] = out[..., :3] * (1 - la) + labc * la
    out[..., 3] = np.maximum(out[..., 3], lab * 235 + blur(lab, 3 * S) * 110)
    return downsample(out, S)


# ================================================================================================ finder
@gui('finder', (512, 288))
def finder():
    W, H = 512, 288
    S = 2
    w, h = W * S, H * S
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32) / S
    # rounded window, the darkness outside, a soft inner falloff
    x0, y0, x1, y1, rad = 14, 10, W - 14, H - 10, 26
    dx = np.maximum(np.maximum(x0 + rad - xx, xx - (x1 - rad)), 0)
    dy = np.maximum(np.maximum(y0 + rad - yy, yy - (y1 - rad)), 0)
    d = np.hypot(dx, dy) - rad                       # >0 outside the rounded rect
    inside_edge = np.minimum(np.minimum(xx - x0, x1 - xx), np.minimum(yy - y0, y1 - yy))
    alpha = np.where(d > 0, 1.0, np.clip(1 - inside_edge / 34.0, 0, 1) ** 2.2 * 0.78)
    out = np.zeros((h, w, 4), np.float32)
    out[..., :3] = 4
    out[..., 3] = alpha * 255
    # eyepiece rim: a faint lighter ring just inside the window edge
    rim = (d > -2.2) & (d < -0.8)
    out[rim, :3] = 40
    out[rim, 3] = 210
    img = Image.new('L', (w, h), 0)
    dr = ImageDraw.Draw(img)
    lw = S

    def L(pts):
        dr.line([(p[0] * S, p[1] * S) for p in pts], fill=255, width=lw)
    # bright-line frame: corner brackets of the 4:3 capture area
    fx0, fy0, fx1, fy1 = 96, 24, 415, 263
    arm = 22
    for (cx, cy, sx, sy) in ((fx0, fy0, 1, 1), (fx1, fy0, -1, 1), (fx0, fy1, 1, -1), (fx1, fy1, -1, -1)):
        L([(cx, cy + sy * arm), (cx, cy), (cx + sx * arm, cy)])
    # parallax correction marks (short ticks inside the top corners)
    for (cx, cy, sx) in ((fx0 + 10, fy0 + 12, 1), (fx1 - 10, fy0 + 12, -1)):
        L([(cx, cy), (cx + sx * 9, cy)])
        L([(cx, cy), (cx, cy + 9)])
    # thin dotted 4:3 outline
    for t in range(fx0 + arm + 6, fx1 - arm - 4, 8):
        L([(t, fy0), (t + 2, fy0)])
        L([(t, fy1), (t + 2, fy1)])
    for t in range(fy0 + arm + 6, fy1 - arm - 4, 8):
        L([(fx0, t), (fx0, t + 2)])
        L([(fx1, t), (fx1, t + 2)])
    # centre focus circle + tiny cross (alone inside x 232..280, y 120..168)
    cx, cy, cr = 256, 144, 17
    dr.ellipse([(cx - cr) * S, (cy - cr) * S, (cx + cr) * S, (cy + cr) * S], outline=255, width=lw)
    L([(cx - 4, cy), (cx - 1, cy)])
    L([(cx + 1, cy), (cx + 4, cy)])
    L([(cx, cy - 4), (cx, cy - 1)])
    L([(cx, cy + 1), (cx, cy + 4)])
    # the frame counter window at the bottom (film count drawn by code next to it) and the meter shelf
    L([(fx0 + 4, fy1 + 10), (fx0 + 40, fy1 + 10)])
    lines = np.asarray(img, np.float32) / 255.0
    lines = np.maximum(lines, blur(lines, 1.2 * S) * 0.6)
    lc = np.array([236, 240, 228], np.float32)
    la = lines[..., None]
    out[..., :3] = out[..., :3] * (1 - la) + lc * la
    out[..., 3] = np.maximum(out[..., 3], lines * 225)
    return downsample(out, S)


@gui('finder_meter', (128, 64))
def finder_meter():
    S = 4
    W, H = 128 * S, 64 * S
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    px_, py_ = 64 * S, 58 * S
    R_ = 54 * S
    # housing: dark bezel, a cream face (semicircle), glass glare
    d.rounded_rectangle([2 * S, 2 * S, W - 3 * S, H - 3 * S], radius=10 * S, fill=(24, 22, 20, 235),
                        outline=(80, 74, 66, 255), width=2 * S)
    d.pieslice([px_ - R_, py_ - R_, px_ + R_, py_ + R_], 180, 360, fill=(226, 216, 190, 255))
    # scale arc from -60 to +60 degrees: ticks, a red zone at the top end
    for i in range(0, 21):
        t = -60 + i * 6
        a = math.radians(t - 90)
        r0 = R_ * (0.80 if i % 5 == 0 else 0.86)
        r1 = R_ * 0.93
        col = (170, 20, 16, 255) if t > 30 else (40, 30, 24, 255)
        d.line([(px_ + math.cos(a) * r0, py_ + math.sin(a) * r0), (px_ + math.cos(a) * r1, py_ + math.sin(a) * r1)],
               fill=col, width=(2 if i % 5 == 0 else 1) * S)
    d.arc([px_ - R_ * 0.93, py_ - R_ * 0.93, px_ + R_ * 0.93, py_ + R_ * 0.93], 210, 330, fill=(40, 30, 24, 255), width=S)
    d.arc([px_ - R_ * 0.96, py_ - R_ * 0.96, px_ + R_ * 0.96, py_ + R_ * 0.96], 300, 330, fill=(190, 24, 18, 255),
          width=3 * S)
    f = font(FONT_SERIF_JP_BOLD, 11 * S)
    tw = d.textlength('霊感', font=f)
    d.text((px_ - tw / 2, py_ - R_ * 0.55), '霊感', fill=(60, 40, 30, 255), font=f)
    fs = font(FONT_SANS_BOLD, 7 * S)
    for i, lab in ((0, '0'), (10, '5'), (20, '10')):
        t = -60 + i * 6
        a = math.radians(t - 90)
        r0 = R_ * 0.68
        tw = d.textlength(lab, font=fs)
        d.text((px_ + math.cos(a) * r0 - tw / 2, py_ + math.sin(a) * r0 - 4 * S), lab, fill=(60, 40, 30, 255), font=fs)
    # pivot cap
    d.ellipse([px_ - 5 * S, py_ - 5 * S, px_ + 5 * S, py_ + 5 * S], fill=(40, 36, 32, 255))
    d.ellipse([px_ - 2 * S, py_ - 2 * S, px_ + 2 * S, py_ + 2 * S], fill=(150, 140, 120, 255))
    a = np.asarray(im, np.float32)
    # age: yellowed face, a water stain, glass glare
    yy, xx = np.mgrid[0:H, 0:W]
    face = (np.hypot(xx - px_, yy - py_) < R_) & (yy < py_)
    n = blur(rng(15501).random((H, W)), 6 * S)
    st = face & (n > 0.53)
    a[st, :3] = a[st, :3] * 0.82 + np.array([150, 120, 70]) * 0.18
    glare = face & (np.abs((xx - px_ * 0.6) + (yy - py_ * 0.4) * 1.4) < 4 * S)
    a[glare, :3] = a[glare, :3] * 0.7 + 255 * 0.3
    return downsample(a, S)


# ================================================================================================ instant photo
@gui('photo_frame', (128, 160))
def photo_frame():
    W, H = 128, 160
    r = rng(15601)
    a = blank(W, H, '#f1eee4')
    n = noise(W, H, 15602, beta=1.6, wrap=False)
    a[..., :3] += n[..., None] * 3
    fine = noise(W, H, 15603, beta=0.1, wrap=False)
    a[..., :3] += fine[..., None] * 1.5
    # the slightly thicker bottom (the chemistry pod), edge shading, a fingerprint smudge
    a[150:, :, :3] *= 0.985
    frame(a, 0, 0, W - 1, H - 1, '#d8d4c8')
    hline(a, 1, W - 2, H - 2, '#e2ded2')
    # window: transparent, with a thin inset shadow on the frame side
    a[8:92, 8:120, 3] = 0
    hline(a, 7, 120, 7, '#cdc8ba')
    vline(a, 7, 7, 92, '#cdc8ba')
    hline(a, 7, 120, 92, '#e8e4d8')
    vline(a, 120, 7, 92, '#e8e4d8')
    x, y = grid(W, H)
    fp = (np.hypot((x - 104) / 6, (y - 132) / 8) < 1) & (((x + y * 0.3) % 2.2) < 1.0)
    a[fp, :3] *= 0.975
    return a


SPIRIT_NAMES = {'visitor': '三浦 健司', 'waitress': '小野寺 千夏', 'chef': '権藤 剛', 'child': '春日 ゆうか',
                'mirror_woman': '白石 玲子', 'manager': '森田 浩一'}


def _red_name(text, seed):
    """The true name writing itself in red ink: a hand-brushed Mincho, slightly uneven, bleeding into the margin."""
    S = 4
    W, H = 128 * S, 32 * S
    r = rng(seed)
    size = int(25 * S)
    f = font(FONT_SERIF_JP_BOLD, size)
    glyphs = []
    for ch in text:
        if ch == ' ':
            glyphs.append(None)
            continue
        im = Image.new('L', (size * 2, size * 2), 0)
        d = ImageDraw.Draw(im)
        bb = d.textbbox((0, 0), ch, font=f)
        d.text((size // 2 - bb[0], size // 2 - bb[1]), ch, fill=255, font=f)
        im = im.rotate(r.uniform(-4, 4), resample=Image.BILINEAR, center=(size, size))
        g = np.asarray(im, np.float32) / 255.0
        ys, xs = np.nonzero(g > 0.3)
        g = g[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        glyphs.append(g)
    gap = int(size * 0.08)
    space = int(size * 0.42)
    tw = sum((g.shape[1] if g is not None else space) for g in glyphs) + gap * (len(glyphs) - 1)
    k = min(1.0, (W - 10 * S) / tw)
    canvas = np.zeros((H, W), np.float32)
    x = (W - int(tw * k)) // 2
    for g in glyphs:
        if g is None:
            x += int(space * k)
            continue
        if k < 1.0:
            g = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize(
                (max(1, int(g.shape[1] * k)), max(1, int(g.shape[0] * k))), Image.LANCZOS), np.float32) / 255.0
        gh, gw = g.shape
        oy = (H - gh) // 2 + int(r.uniform(-1.5, 1.5) * S)
        oy = max(0, min(H - gh, oy))
        canvas[oy:oy + gh, x:x + gw] = np.maximum(canvas[oy:oy + gh, x:x + gw], g[:, :min(gw, W - x)])
        x += gw + int(gap * k)
    # brush: tiny tremor, pooled ink at stroke ends (thicker), a few dry hairs, ragged edge
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dx = blur(r.normal(0, 1, (H, W)), size * 0.04) * size * 0.06
    dy = blur(r.normal(0, 1, (H, W)), size * 0.04) * size * 0.06
    m = ndimage.map_coordinates(canvas, [yy + dy, xx + dx], order=1, mode='constant')
    pool = blur(m, S * 1.2)
    m = np.clip(m * 0.8 + pool * 0.45, 0, 1)
    rough = blur(r.normal(0, 1, (H, W)), S * 0.6)
    ink = np.clip((m - 0.45 + rough * 0.06) * 6, 0, 1)
    hair = (blur(r.normal(0, 1, (H, W)), (S * 0.3, S * 3)) > 0.9) & (m > 0.5)
    ink[hair] *= 0.35
    out = np.zeros((H, W, 4), np.float32)
    n = blur(r.normal(0, 1, (H, W)), S) * 12
    dense = np.clip(blur(ink, S * 0.8) * 1.4, 0, 1)
    out[..., 0] = 176 - dense * 40 + n
    out[..., 1] = 22 + n * 0.15
    out[..., 2] = 16 + n * 0.15
    bleed = np.clip(blur(ink, 1.6 * S) * 1.2, 0, 1) * 0.28
    out[..., 3] = np.maximum(ink, bleed) * 255
    return downsample(out, S)


for _sid, _name in SPIRIT_NAMES.items():
    gui(f'photo_name_{_sid}', (128, 32))(lambda _n=_name, _s=15700 + len(_sid) * 13: _red_name(_n, _s))


# ================================================================================================ failed photo / ghosts
def _face_field(W, H, cx, cy, s, seed, scream=False):
    """Soft pale face: brow / eyes (dark hollows) / mouth as a 0..1 field (light) and a 0..1 hollow field (dark)."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

    def g(x, y, sx, sy):
        return np.exp(-(((xx - x) / sx) ** 2 + ((yy - y) / sy) ** 2))
    head = g(cx, cy, s * 0.55, s * 0.72)
    eyes = g(cx - s * 0.2, cy - s * 0.1, s * 0.09, s * 0.07) + g(cx + s * 0.2, cy - s * 0.1, s * 0.09, s * 0.07)
    mouth = g(cx, cy + s * 0.32, s * 0.12, s * (0.16 if scream else 0.05))
    return head, np.clip(eyes + mouth, 0, 1)


@gui('photo_blur', (256, 192))
def photo_blur():
    W, H = 256, 192
    r = rng(15801)
    head, hollow = _face_field(W, H, 120, 92, 120, 15801, scream=True)
    # motion smear: drag the face sideways with decreasing strength
    acc_l = np.zeros((H, W))
    acc_d = np.zeros((H, W))
    for k in range(18):
        sh = int(k * 4.5)
        wgt = math.exp(-k / 6.0)
        acc_l += np.roll(head, sh, axis=1) * wgt
        acc_d += np.roll(hollow, sh, axis=1) * wgt
    acc_l /= acc_l.max()
    acc_d /= acc_d.max()
    n = blur(r.normal(0, 1, (H, W)), 1.5)
    out = blank(W, H)
    light = np.clip(acc_l * 0.85 + n * 0.05, 0, 1)
    dark = np.clip(acc_d * 1.2, 0, 1)
    col = np.zeros((H, W, 3), np.float32)
    col[:] = np.array([226, 224, 216])
    col = col * (1 - dark[..., None]) + np.array([12, 10, 10]) * dark[..., None]
    out[..., :3] = col
    out[..., 3] = np.clip(np.maximum(light * 0.75, dark * 0.9), 0, 1) * 255
    # streaks across the whole frame (the film dragged / the spirit's movement)
    st = blur(r.random((H, 1)).repeat(W, 1), (0.5, 30)) > 0.53
    out[st, 3] = np.maximum(out[st, 3], 40)
    return out


def _ghost(kind):
    W, H = 256, 192
    r = rng(15900 + kind)
    out = blank(W, H, '#e8e8e2')
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    if kind == 1:      # a pale face in the upper right corner, half out of frame
        head, hollow = _face_field(W, H, 222, 40, 70, 15901)
        a = head * 0.32 - hollow * 0.0
        out[..., 3] = np.clip(a, 0, 1) * 255
        dk = hollow > 0.3
        out[dk, :3] = 20
        out[dk, 3] = np.clip(hollow[dk] * 120, 0, 120)
    elif kind == 2:    # a hand reaching in from the bottom edge
        m = np.zeros((H, W), np.float32)
        palm = np.exp(-(((xx - 70) / 22) ** 2 + ((yy - 185) / 26) ** 2))
        m += palm
        for i, (fx, ln) in enumerate(((50, 55), (62, 70), (75, 72), (88, 62), (98, 40))):
            ang = (i - 2) * 0.12
            for t in np.linspace(0, 1, 40):
                x = fx + math.sin(ang) * ln * t
                y = 170 - ln * t
                m += np.exp(-(((xx - x) / 4.2) ** 2 + ((yy - y) / 4.2) ** 2)) * 0.6
        m = np.clip(m, 0, 1)
        out[..., 3] = blur(m, 1.5) * 95
    else:              # many faint faces in the dark glass behind the subject
        acc = np.zeros((H, W), np.float32)
        hol = np.zeros((H, W), np.float32)
        for (cx, cy, s) in ((40, 70, 46), (90, 54, 38), (190, 120, 52), (150, 40, 30), (230, 80, 34)):
            h_, o_ = _face_field(W, H, cx, cy, s, 15903)
            acc += h_
            hol += o_
        out[..., 3] = np.clip(acc * 0.18, 0, 1) * 255
        dk = hol > 0.35
        out[dk, :3] = 10
        out[dk, 3] = np.clip(hol[dk] * 70, 0, 80)
    n = blur(r.normal(0, 1, (H, W)), 1.0)
    out[..., 3] = np.clip(out[..., 3] * (1 + n * 0.25), 0, 255)
    return out


for _k in (1, 2, 3):
    gui(f'photo_ghost_{_k}', (256, 192))(lambda _k=_k: _ghost(_k))


# ================================================================================================ album (霊撮帳)
@gui('album', (512, 320))
def album():
    W, H = 512, 320
    r = rng(16001)
    a = blank(W, H)
    # cover (dark brown cloth) visible around the pages
    cov = blank(W, H, '#2a1810')
    n = noise(W, H, 16002, beta=0.8, wrap=False)
    cov[..., :3] += n[..., None] * 6
    x, y = grid(W, H)
    cl = (x + y) % 2 == 0
    cov[cl, :3] *= 0.92
    rr = 14
    inside = np.ones((H, W), bool)
    for (cx, cy) in ((rr, rr), (W - 1 - rr, rr), (rr, H - 1 - rr), (W - 1 - rr, H - 1 - rr)):
        corner = ((x < rr) | (x > W - 1 - rr)) & ((y < rr) | (y > H - 1 - rr))
        inside &= ~(corner & (np.hypot(x - cx, y - cy) > rr))
    a[inside] = cov[inside]
    # pages
    pg = blank(W, H, '#efe8d4')
    pn = noise(W, H, 16003, beta=1.4, wrap=False)
    pg[..., :3] += pn[..., None] * 4
    pages = (x >= 14) & (x < W - 14) & (y >= 12) & (y < H - 12)
    a[pages] = pg[pages]
    # stacked page edges at the bottom, gutter shadow at the spine
    for k in range(3):
        hline(a, 14 + k, W - 15 - k, H - 12 + k, '#d8d0b8' if k % 2 == 0 else '#c4bca4')
    sp = W // 2
    for dx_ in range(-12, 13):
        t = 1 - abs(dx_) / 12.0
        col = pages & (x == sp + dx_)
        a[col, :3] *= (1 - 0.28 * t ** 2)
    vline(a, sp, 12, H - 13, '#8a7a5a')
    # stitching
    for yy_ in range(20, H - 20, 10):
        vline(a, sp, yy_, yy_ + 4, '#5a2a1a')
    # faint ruled lines (top half of the right page) and photo corner mounts on the left page
    for yy_ in range(64, H - 30, 14):
        hline(a, sp + 24, W - 30, yy_, '#d8cfb6')
    # title 霊撮帳 in brush ink at the top of the left page, a small red seal
    ink = brush_glyphs('霊撮帳', 96, FONT_SERIF_JP_BLACK, 16004, wobble=0.3, dry=0.5, splatter=4)
    ih, iw = ink.shape
    k = min(1.0, 104 / iw)
    im = Image.fromarray((ink * 255).astype(np.uint8)).resize((int(iw * k), int(ih * k)), Image.LANCZOS)
    ink = np.asarray(im, np.float32) / 255.0
    over(a, mask_to_rgba(ink, '#1c1612', 0.92), 30, 16)
    rect(a, 30 + ink.shape[1] + 6, 24, 30 + ink.shape[1] + 17, 35, '#b8241a')
    hline(a, 28, 30 + ink.shape[1] + 20, 16 + ink.shape[0] + 4, '#8a7a5a')
    # age: stains, foxing
    for (cx, cy, rad) in ((420, 250, 22), (90, 270, 14)):
        ring = ring_mask(W, H, cx, cy, rad - 1.2, rad + 0.4) & pages
        a[ring, :3] = a[ring, :3] * 0.9 + np.array([150, 110, 60]) * 0.1
    fox = pages & (r.random((H, W)) < 0.002)
    a[fox, :3] *= 0.85
    return a


# ================================================================================================ HUD icons
def _icon_film(a, x0, y0, empty=False):
    rect(a, x0 + 2, y0 + 3, x0 + 13, y0 + 12, '#141418')
    rect(a, x0 + 3, y0 + 4, x0 + 12, y0 + 6, '#f2f0e8' if not empty else '#3a3a3e')
    rect(a, x0 + 3, y0 + 8, x0 + 12, y0 + 9, '#c8281c' if not empty else '#4a2220')
    frame(a, x0 + 2, y0 + 3, x0 + 13, y0 + 12, '#5a5a62')


def _icon_ofuda(a, x0, y0, burnt=False):
    body = '#efe6cc' if not burnt else '#4a4238'
    rect(a, x0 + 5, y0 + 1, x0 + 10, y0 + 14, body)
    frame(a, x0 + 5, y0 + 1, x0 + 10, y0 + 14, '#8a6a3a' if not burnt else '#1a1612')
    rect(a, x0 + 6, y0 + 2, x0 + 9, y0 + 4, '#c8281c' if not burnt else '#5a2a1a')
    for yy in (6, 8, 10, 12):
        hline(a, x0 + 7, x0 + 8, y0 + yy, '#141010')
    if burnt:
        for (dx_, dy_) in ((5, 13), (6, 14), (9, 1), (10, 2)):
            a[y0 + dy_, x0 + dx_, 3] = 0
        px(a, x0 + 8, y0 + 13, '#ff7a2a')


def _icon_eye(a, x0, y0, state):
    col = '#e8e4d8' if state != 3 else '#ff3a2a'
    dark = '#1a1a1a'
    if state == 0:      # closed
        hline(a, x0 + 3, x0 + 12, y0 + 8, col)
        for x in (4, 7, 10):
            px(a, x0 + x + 1, y0 + 9, col)
        return
    open_h = {1: 1, 2: 3, 3: 3}[state]
    for x in range(3, 13):
        t = (x - 7.5) / 5.0
        hgt = int(round(open_h * (1 - t * t))) + 1
        px(a, x0 + x, y0 + 8 - hgt, col)
        px(a, x0 + x, y0 + 8 + hgt - (1 if state == 1 else 0), col)
    if state >= 2:
        rect(a, x0 + 7, y0 + 6, x0 + 8, y0 + 9, col)
        px(a, x0 + 7, y0 + 7, dark)
        px(a, x0 + 8, y0 + 8, dark)
    if state == 3:
        for (dx_, dy_) in ((7, 1), (8, 1), (2, 3), (13, 3)):
            px(a, x0 + dx_, y0 + dy_, col)


def _icon_breath(a, x0, y0, gasp=False):
    col = '#cfe2ea' if not gasp else '#f2b0a0'
    for k, (yy, ln) in enumerate(((5, 7), (8, 9), (11, 6))):
        hline(a, x0 + 2 + k, x0 + 2 + k + ln, y0 + yy, col)
        px(a, x0 + 3 + k + ln, y0 + yy - 1, col)


def _icon_star(a, x0, y0, filled):
    rows = ['.......#........', '......###.......', '......###.......', '.##############.', '..############..',
            '....########....', '....###..###....', '...##......##...']
    col = '#f0c030' if filled else '#5a5040'
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '#':
                if filled or j in (3, 7) or i in (6, 8):
                    px(a, x0 + i, y0 + 4 + j, col)


@gui('icons', (128, 32))
def icons():
    a = blank(128, 32)
    _icon_film(a, 0, 0)
    _icon_ofuda(a, 16, 0)
    for k in range(4):
        _icon_eye(a, 32 + k * 16, 0, k)
    _icon_breath(a, 96, 0)
    _icon_breath(a, 112, 0, gasp=True)
    _icon_film(a, 0, 16, empty=True)
    _icon_ofuda(a, 16, 16, burnt=True)
    # camera, flashlight, key, note, stars
    rect(a, 34, 21, 45, 28, '#5a3420')
    rect(a, 34, 21, 45, 21, '#d8dcde')
    a[disc_mask(128, 32, 40, 25, 2.6)] = C('#d8dcde')
    a[disc_mask(128, 32, 40, 25, 1.6)] = C('#141c24')
    rect(a, 35, 19, 37, 20, '#f4f4ec')
    line(a, 50, 29, 58, 21, '#3a3a40')
    line(a, 51, 29, 59, 21, '#26262a')
    rect(a, 58, 19, 60, 21, '#c8ccd0')
    px(a, 60, 18, '#fff2b0')
    a[disc_mask(128, 32, 68.5, 21.5, 2.5)] = C('#c8a040')
    a[disc_mask(128, 32, 68.5, 21.5, 1.0)] = C('#000000', 0)
    line(a, 70, 23, 76, 29, '#c8a040')
    px(a, 74, 29, '#c8a040')
    px(a, 75, 28, '#c8a040')
    rect(a, 82, 18, 92, 30, '#efeadc')
    for yy in (21, 23, 25, 27):
        hline(a, 84, 90, yy, '#7a7468')
    _icon_star(a, 96, 16, True)
    _icon_star(a, 112, 16, False)
    return a


# ================================================================================================ paper
@gui('paper', (256, 256))
def paper():
    W = H = 256
    r = rng(16101)
    a = blank(W, H, '#ece6d4')
    n = noise(W, H, 16102, beta=1.5, wrap=False)
    fine = noise(W, H, 16103, beta=0.2, wrap=False)
    a[..., :3] += n[..., None] * 5 + fine[..., None] * 2
    x, y = grid(W, H)
    # sheet edges (slightly shadowed), margin line, ruled lines, a horizontal fold, a coffee ring, punched holes
    edge = np.minimum(np.minimum(x, W - 1 - x), np.minimum(y, H - 1 - y))
    a[..., :3] *= (1 - 0.12 * np.exp(-edge / 6.0))[..., None]
    for yy in range(40, H - 16, 13):
        hline(a, 16, W - 17, yy, '#c9cfd6')
    vline(a, 34, 8, H - 9, '#d8a8a0')
    fold = np.abs(y - H * 0.5) < 1.2
    a[fold, :3] *= 0.94
    a[(np.abs(y - H * 0.5 + 2) < 1.0), :3] *= 1.02
    ring = ring_mask(W, H, 196, 196, 21, 23.5)
    a[ring, :3] = a[ring, :3] * 0.88 + np.array([120, 80, 40]) * 0.12
    for hy in (52, 128, 204):
        a[disc_mask(W, H, 14, hy, 4.2)] = C('#000000', 0)
    a[..., :3] = np.clip(a[..., :3], 0, 255)
    return a


# ================================================================================================ locker slits
@gui('locker_slits', (256, 144))
def locker_slits():
    W, H = 256, 144
    S = 2
    w, h = W * S, H * S
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32) / S
    n = noise(w, h, 16201, beta=1.4, wrap=False)
    out = np.zeros((h, w, 4), np.float32)
    out[..., :3] = (np.array([30, 33, 35]) + n[..., None] * 3).astype(np.float32)
    out[..., 3] = 255
    for i, cy in enumerate((44, 60, 76, 92)):
        cx0, cx1 = 58, 198
        half = 4.2
        # stadium-shaped slit, pressed steel lip (lit from above), the outside seen through it
        d = np.where(xx < cx0, np.hypot(xx - cx0, yy - cy), np.where(xx > cx1, np.hypot(xx - cx1, yy - cy),
                                                                      np.abs(yy - cy)))
        lip = (d < half + 2.4) & (d >= half)
        out[lip & (yy < cy), :3] = 74
        out[lip & (yy >= cy), :3] = 18
        hole = d < half
        out[hole, 3] = 0
    # the steel is not quite black: a little light leaks around the slits
    glow = blur((out[..., 3] == 0).astype(np.float32), 6 * S)
    out[..., :3] += glow[..., None] * 40
    return downsample(out, S)


# ================================================================================================ the HIDAMARI sun
@gui('logo_sun', (64, 64))
def logo_sun():
    return sun_logo(64, ss=8)


# ================================================================================================ copied overlays
def _load(name):
    import os
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', name)
    return np.asarray(Image.open(p).convert('RGBA'), np.float32)


@gui('vignette', (256, 256))
def vignette():
    return _load('gui_vignette.png')     # MEIKON / SOUNAN overlay


@gui('grain', (256, 256))
def grain():
    return _load('gui_grain.png')        # MEIKON / SOUNAN overlay
