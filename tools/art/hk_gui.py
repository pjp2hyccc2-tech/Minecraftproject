"""GUI textures of HITORI (DESIGN §7 GUI; gui/jumpscare_* belong to the models worker).

Overlays are 512 x 288 (16:9) and meant to be drawn stretched over the whole screen; transparent where the world
(or the camera feed) shows through. Rectangles the game needs (in texture pixels) are listed in RECTS below and in
the art report.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

from common import (C, ramp, rng, grid, texture, font, fnoise, FONT_SANS_JP_BOLD, FONT_SANS_JP_MED, FONT_SANS_JP_REG,
                    FONT_SANS_JP_BLACK, FONT_SERIF_JP_BOLD, FONT_SERIF_JP_REG, FONT_LABEL, FONT_SANS, FONT_SANS_BOLD,
                    HERE)
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, disc_mask, ring_mask,
                    text_mask, stamp_mask, stamp_center, downsample, FONT_IPAG)

FONT_MONO = '/usr/share/texmf/fonts/opentype/public/lm/lmmono10-regular.otf'
FONT_MONO_B = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf'
FONT_SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'

RECTS = {
    # laptop_frame (drawn stretched over the whole screen by CameraApp / EndingScreen): the transparent viewport is
    # the camera app's feed rect (w/22, h/14) - (w - w/22, h - h/9) -> texture px 23..488 x 21..255
    'laptop_viewport': (23, 21, 488, 255),
    # phone_frame (drawn over the phone body = the feed rect + 14 / 10 screen px): its screen hole
    'phone_screen': (18, 13, 493, 274),
    # name_field (192 x 36, drawn at 192 x 36): the EditBox (light text, no border) sits at 16..176 x 10..28
    'name_text': (16, 10, 176, 28),
    # fusuma_slit: the gap of light
    'fusuma_gap': (243, 0, 263, 287),
}


def gui(name, size):
    return texture('gui/' + name, size=size)


def _pil_text(text, size, font_spec, fill=(255, 255, 255, 255), spacing=0):
    f = font(font_spec, size)
    W = int(size * (len(text) + 2) * 1.2 + spacing * len(text)) + 8
    H = int(size * 2.2)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x = 4
    for ch in text:
        d.text((x, size // 3), ch, fill=fill, font=f)
        x += int(d.textlength(ch, font=f)) + spacing
    a = np.asarray(im, np.float32)
    ys, xs = np.nonzero(a[..., 3] > 0)
    if len(xs) == 0:
        return np.zeros((1, 1, 4), np.float32)
    return a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def paste(a, b, x, y, alpha=1.0):
    return over(a, b, int(x), int(y), alpha)


# ================================================================================================ title logo
def _thread_shade(mask, seed, base=('#3e0405', '#8e0c0e', '#c81e1c', '#f05a4c'), twist=0.95, gloss=True):
    """Colour a thread-shaped mask: rounded (bright core, dark edges), plied (diagonal twist stripes), wet gloss."""
    h, w = mask.shape
    d = ndimage.distance_transform_edt(mask)
    loc_max = ndimage.maximum_filter(d, size=9)
    t = np.clip(d / np.maximum(loc_max, 1e-3), 0, 1)
    y, x = np.mgrid[0:h, 0:w]
    tw = np.sin((x + y) * twist) * 0.5 + 0.5
    v = np.clip(t * 0.85 + (tw - 0.5) * 0.35 * t, 0, 1)
    cols = np.array([C(c)[:3] for c in base])
    idx = v * (len(cols) - 1)
    i0 = np.floor(idx).astype(int)
    i1 = np.clip(i0 + 1, 0, len(cols) - 1)
    f = (idx - i0)[..., None]
    rgb = cols[i0] * (1 - f) + cols[i1] * f
    out = np.zeros((h, w, 4), np.float32)
    out[..., :3] = rgb
    out[..., 3] = mask * 255.0
    if gloss:
        r = rng(seed)
        n = fnoise(256, 256, seed, beta=1.4)
        n = np.tile(n, (h // 256 + 1, w // 256 + 1))[:h, :w]
        g = mask & (t > 0.72) & (n > 1.1) & (tw > 0.6)
        out[g, :3] = out[g, :3] * 0.3 + np.array([255, 200, 190]) * 0.7
    return out


def _char_skeleton(ch, size, seed, font_spec=FONT_SANS_JP_MED):
    """Skeleton of one character, rendered, warped by smooth noise (a shaky hand), then thinned to a centre line."""
    from skimage.morphology import skeletonize
    r = rng(seed)
    f = font(font_spec, size)
    S = int(size * 1.6)
    im = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(im)
    bb = d.textbbox((0, 0), ch, font=f)
    d.text(((S - (bb[2] - bb[0])) / 2 - bb[0], (S - (bb[3] - bb[1])) / 2 - bb[1]), ch, fill=255, font=f)
    im = im.rotate(r.uniform(-7, 7), resample=Image.BICUBIC, center=(S / 2, S / 2))
    m = np.asarray(im, np.float32) / 255.0
    # warp: smooth displacement field
    n1 = fnoise(64, 64, seed + 1, beta=2.4)
    n2 = fnoise(64, 64, seed + 2, beta=2.4)
    zx = ndimage.zoom(n1, S / 64, order=1)[:S, :S]
    zy = ndimage.zoom(n2, S / 64, order=1)[:S, :S]
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    amp = size * 0.045
    m = ndimage.map_coordinates(m, [yy + zy * amp, xx + zx * amp], order=1)
    sk = skeletonize(m > 0.5)
    return sk


def _drip(mask_rgba, x0, y0, length, width, seed):
    """A wet drip running down from (x0, y0): tapering, a bulb at the end, a highlight line."""
    r = rng(seed)
    h, w = mask_rgba.shape[:2]
    m = np.zeros((h, w), bool)
    yy, xx = np.mgrid[0:h, 0:w]
    xs = x0
    for k in range(int(length)):
        f = k / max(length, 1)
        ww = width * (1 - 0.45 * f)
        xs += r.normal(0, 0.15)
        y = int(y0 + k)
        if 0 <= y < h:
            m[y, max(0, int(xs - ww / 2)):min(w, int(xs + ww / 2) + 1)] = True
    br = width * 0.85
    m |= (xx - xs) ** 2 + (yy - (y0 + length)) ** 2 <= br * br
    return m


def title_logo_art(W=512, H=256, SS=2):
    """「ひとりかくれんぼ」 written in one wet red thread: each character's centre line (shaky, uneven, a little
    rotated) re-drawn as a plied thread, the characters joined by the sagging thread, drips running down, a loose end
    trailing off to a sewing needle; "― HITORI KAKURENBO ―" typed below."""
    from skimage.morphology import dilation, disk
    text = 'ひとりかくれんぼ'
    WW, HH = W * SS, H * SS
    r = rng(16001)
    sk_all = np.zeros((HH, WW), bool)
    size = 124
    step = 116
    x = (WW - step * len(text)) // 2 - 10
    ends = []
    for i, ch in enumerate(text):
        s = int(size * r.uniform(0.9, 1.08))
        sk = _char_skeleton(ch, s, 16010 + i * 7)
        hh, ww = sk.shape
        cy = int(176 + r.uniform(-16, 16) + (8 if ch in 'りれ' else 0))
        y0 = cy - hh // 2
        x0 = x + step // 2 - ww // 2
        ys, xs = np.nonzero(sk)
        ys2, xs2 = ys + y0, xs + x0
        ok = (ys2 >= 0) & (ys2 < HH) & (xs2 >= 0) & (xs2 < WW)
        sk_all[ys2[ok], xs2[ok]] = True
        if len(xs):
            li = np.argmin(xs2)
            ri = np.argmax(xs2)
            ends.append(((xs2[li], ys2[li]), (xs2[ri], ys2[ri])))
        x += step
    # the thread joining one character to the next (thin, sagging)
    join = np.zeros((HH, WW), bool)
    for i in range(len(ends) - 1):
        (_, (ax, ay)), ((bx, by), _) = ends[i], ends[i + 1]
        n = int(abs(bx - ax) + 4)
        for k in range(n + 1):
            t = k / max(n, 1)
            xx_ = ax + (bx - ax) * t
            yy_ = ay + (by - ay) * t + math.sin(t * math.pi) * 12
            xi, yi = int(xx_), int(yy_)
            if 0 <= yi < HH and 0 <= xi < WW:
                join[yi, xi] = True
    # the loose end: from the last character, a long curve sagging down and back under the word to a needle
    (lx, ly) = ends[-1][1]
    tail = np.zeros((HH, WW), bool)
    pts = []
    for k in range(400):
        t = k / 399
        xx_ = lx + 26 * math.sin(t * math.pi) - t * 150 + math.sin(t * 9) * 6
        yy_ = ly + t * 150 + math.sin(t * 4.0) * 10
        pts.append((xx_, yy_))
        if 0 <= int(yy_) < HH and 0 <= int(xx_) < WW:
            tail[int(yy_), int(xx_)] = True
    thread = dilation(sk_all, disk(6))
    thin = dilation(join | tail, disk(2))
    col = _thread_shade(thread, 16050)
    thin_col = _thread_shade(thin & ~thread, 16051, base=('#5a0607', '#9a1212', '#c82a24', '#e85a4a'), twist=1.3,
                             gloss=False)
    out = np.zeros((HH, WW, 4), np.float32)
    out = over(out, thin_col)
    # drips from the lower edges of the strokes
    below = thread & ~np.roll(thread, -1, 0)
    cand = np.argwhere(below)
    cand = cand[(cand[:, 0] > 80) & (cand[:, 0] < HH - 170)]
    drips = np.zeros((HH, WW), bool)
    order = r.permutation(len(cand))
    used = []
    for idx in order:
        yy_, xx_ = cand[idx]
        if any(abs(xx_ - ux) < 26 for ux in used):
            continue
        used.append(xx_)
        L = r.uniform(12, 60) if r.random() < 0.7 else r.uniform(60, 120)
        drips |= _drip(out, xx_, yy_ - 2, L, r.uniform(6, 9), 16100 + idx)
        if len(used) > 26:
            break
    drip_col = _thread_shade(drips & ~thread, 16052, base=('#2e0203', '#6a0608', '#a01010', '#d8403a'), twist=0.0)
    out = over(out, drip_col)
    out = over(out, col)
    # splatter dots
    for _ in range(60):
        sx, sy = int(r.integers(30, WW - 30)), int(r.integers(70, 330))
        if thread[max(0, sy - 30):sy + 30, max(0, sx - 30):sx + 30].any():
            rr = r.uniform(1.5, 4.5)
            m = disc_mask(WW, HH, sx, sy, rr)
            out[m] = C('#8a0a0c')
    # the needle at the end of the thread
    nx, ny = pts[-1]
    ang = math.atan2(pts[-1][1] - pts[-12][1], pts[-1][0] - pts[-12][0])
    img = Image.fromarray(np.zeros((HH, WW, 4), np.uint8), 'RGBA')
    dd = ImageDraw.Draw(img)
    L = 70
    ex, ey = nx + math.cos(ang) * L, ny + math.sin(ang) * L
    dd.line([(nx - math.cos(ang) * 6, ny - math.sin(ang) * 6), (ex, ey)], fill=(200, 205, 210, 255), width=5)
    dd.line([(nx, ny), (ex - math.cos(ang) * 4, ey - math.sin(ang) * 4)], fill=(240, 244, 246, 255), width=2)
    dd.ellipse([nx - 4, ny - 4, nx + 4, ny + 4], outline=(160, 165, 170, 255), width=2)
    out = over(out, np.asarray(img, np.float32))
    # a soft dark shadow under everything so the thread reads on a dark title screen
    alpha = out[..., 3] / 255.0
    sh = ndimage.gaussian_filter(np.roll(np.roll(alpha, 5, 0), 3, 1), 4.0)
    shadow = np.zeros_like(out)
    shadow[..., :3] = (12, 0, 0)
    shadow[..., 3] = np.clip(sh * 1.4, 0, 1) * 210
    out = over(shadow, out)
    out = downsample(out, SS)
    # subtitle, typed, with thin rules either side
    sub = _pil_text('HITORI KAKURENBO', 21, FONT_MONO, fill=(214, 204, 190, 255), spacing=4)
    sh = sub.copy()
    sh[..., :3] = (40, 4, 4)
    sh[..., 3] *= 0.8
    sx = (W - sub.shape[1]) // 2
    sy = 216
    paste(out, sh, sx + 2, sy + 2)
    paste(out, sub, sx, sy)
    for (x0, x1) in ((sx - 44, sx - 12), (sx + sub.shape[1] + 12, sx + sub.shape[1] + 44)):
        hline(out, x0, x1, sy + 9, C('#d6ccbe'))
        hline(out, x0 + 2, x1 + 2, sy + 11, C('#280404', 200))
    return out


@gui('title_logo', (512, 256))
def title_logo():
    return title_logo_art()


# ================================================================================================ camera-app overlays
def _corner_brackets(a, inset=14, L=26, t=2, col='#f0f0f0', alpha=0.95):
    H, W = a.shape[:2]
    for (cx, cy, sx, sy) in ((inset, inset, 1, 1), (W - 1 - inset, inset, -1, 1), (inset, H - 1 - inset, 1, -1),
                             (W - 1 - inset, H - 1 - inset, -1, -1)):
        x0, x1 = sorted((cx, cx + sx * L))
        y0, y1 = sorted((cy, cy + sy * (t - 1)))
        rect(a, x0, y0, x1, y1, C(col, 255 * alpha))
        x0, x1 = sorted((cx, cx + sx * (t - 1)))
        y0, y1 = sorted((cy, cy + sy * L))
        rect(a, x0, y0, x1, y1, C(col, 255 * alpha))


@gui('cctv', (512, 288))
def cctv():
    """Camera-app overlay drawn stretched over the feed: corner brackets, a faint centre cross, safe-area ticks and a
    cheap lens's darker corners; transparent in the middle. REC, the CAM name, IR, the battery, the timestamp and the
    camera tabs are drawn by the game's CameraApp, so none of them is baked in here."""
    W, H = 512, 288
    a = blank(W, H)
    x, y = grid(W, H)
    d = np.hypot((x - W / 2) / (W / 2), (y - H / 2) / (H / 2))
    a[..., 3] = np.clip((d - 0.82) * 260, 0, 120)
    _corner_brackets(a, inset=5, L=22)                    # at the outer corners: the game's texts sit inside them
    for k in range(-7, 8):
        if abs(k) > 2:
            px(a, W // 2 + k, H // 2, C('#f0f0f0', 150))
            px(a, W // 2, H // 2 + k, C('#f0f0f0', 150))
    for (x0, y0, x1, y1) in ((W // 2 - 6, 16, W // 2 + 6, 17), (W // 2 - 6, H - 18, W // 2 + 6, H - 17),
                             (16, H // 2 - 6, 17, H // 2 + 6), (W - 18, H // 2 - 6, W - 17, H // 2 + 6)):
        rect(a, x0, y0, x1, y1, C('#f0f0f0', 160))
    return a


def _window_chrome(a, x0, y0, x1, y1, title):
    """An old-OS window: raised grey frame, a blue gradient title bar with the title and three buttons."""
    rect(a, x0, y0, x1, y1, C('#c0c0bc'))
    hline(a, x0, x1, y0, C('#f4f4f0'))
    vline(a, x0, y0, y1, C('#f4f4f0'))
    hline(a, x0, x1, y1, C('#40403c'))
    vline(a, x1, y0, y1, C('#40403c'))
    tb0, tb1 = y0 + 2, y0 + 13
    for xx in range(x0 + 2, x1 - 1):
        f = (xx - x0) / max(1, x1 - x0)
        c = C('#0a2a6a') * (1 - f) + C('#5a86c8') * f
        a[tb0:tb1 + 1, xx] = c
    t = _pil_text(title, 10, FONT_SANS_JP_BOLD, fill=(250, 250, 250, 255))
    paste(a, t, x0 + 6, tb0 + 1)
    for k, sym in enumerate(('_', '□', 'x')):
        bx = x1 - 13 - (2 - k) * 13
        rect(a, bx, tb0 + 1, bx + 10, tb1 - 1, C('#c0c0bc'))
        hline(a, bx, bx + 10, tb0 + 1, C('#f4f4f0'))
        vline(a, bx + 10, tb0 + 1, tb1 - 1, C('#40403c'))
        hline(a, bx, bx + 10, tb1 - 1, C('#40403c'))
        s = _pil_text(sym, 8, FONT_SANS_BOLD, fill=(20, 20, 20, 255))
        paste(a, s, bx + 5 - s.shape[1] // 2, tb0 + 2)


@gui('laptop_frame', (512, 288))
def laptop_frame():
    """The laptop seen up close, drawn stretched over the whole screen: the dark plastic bezel with its webcam, an old
    OS with the 定点カメラ viewer maximized - title bar above the feed, the taskbar below it (start button, the viewer's
    task button, the tray) - and the transparent viewport exactly where the game draws the feed
    (RECTS['laptop_viewport'])."""
    W, H = 512, 288
    a = blank(W, H, C('#0e0e10'))
    n = noise(W, H, 16201, beta=1.8, wrap=False)
    a[..., :3] += n[..., None] * 3 + 10
    # bezel sheen toward the top, the webcam and its dead LED, a few smudges
    x, y = grid(W, H)
    a[..., :3] += np.clip(1 - y / 40, 0, 1)[..., None] * 6
    a[disc_mask(W, H, W / 2, 4.5, 2.2)] = C('#1e2228')
    px(a, W // 2, 4, C('#3a4a5a'))
    px(a, W // 2 + 5, 4, C('#2a1a1a'))
    # the screen
    sx0, sy0, sx1, sy1 = 12, 7, 499, 279
    rect(a, sx0, sy0, sx1, sy1, C('#245a5c'))
    v0 = RECTS['laptop_viewport']
    # the maximized window: frame, title bar above the feed
    wx0, wy0, wx1, wy1 = v0[0] - 2, 8, v0[2] + 2, v0[3] + 1
    rect(a, wx0, wy0, wx1, wy1, C('#c0c0bc'))
    hline(a, wx0, wx1, wy0, C('#f4f4f0'))
    vline(a, wx0, wy0, wy1, C('#f4f4f0'))
    vline(a, wx1, wy0, wy1, C('#40403c'))
    tb0, tb1 = wy0 + 1, v0[1] - 2
    for xx in range(wx0 + 1, wx1):
        f = (xx - wx0) / max(1, wx1 - wx0)
        a[tb0:tb1 + 1, xx] = C('#0a2a6a') * (1 - f) + C('#5a86c8') * f
    t = _pil_text('定点カメラ  -  CamView 2.1', 9, FONT_SANS_JP_BOLD, fill=(250, 250, 250, 255))
    paste(a, t, wx0 + 16, tb0 + 1)
    rect(a, wx0 + 4, tb0 + 2, wx0 + 11, tb1 - 2, C('#e8e4d8'))
    a[disc_mask(W, H, wx0 + 7.5, (tb0 + tb1) / 2 + 0.5, 1.6)] = C('#c81a10')
    for k, sym in enumerate(('_', '□', 'x')):
        bx = wx1 - 12 - (2 - k) * 11
        rect(a, bx, tb0 + 1, bx + 9, tb1 - 1, C('#c0c0bc'))
        hline(a, bx, bx + 9, tb0 + 1, C('#f4f4f0'))
        vline(a, bx + 9, tb0 + 1, tb1 - 1, C('#40403c'))
        hline(a, bx, bx + 9, tb1 - 1, C('#40403c'))
        sy = _pil_text(sym, 7, FONT_SANS_BOLD, fill=(20, 20, 20, 255))
        paste(a, sy, bx + 5 - sy.shape[1] // 2, tb0 + 2)
    # the viewport: a sunken black edge, then transparent
    frame(a, v0[0] - 1, v0[1] - 1, v0[2] + 1, v0[3] + 1, C('#40403c'))
    a[v0[1]:v0[3] + 1, v0[0]:v0[2] + 1] = 0
    # the taskbar under the window
    ty0, ty1 = v0[3] + 2, sy1
    rect(a, sx0, ty0, sx1, ty1, C('#c0c0bc'))
    hline(a, sx0, sx1, ty0, C('#f4f4f0'))
    rect(a, 15, ty0 + 2, 62, ty1 - 2, C('#c0c0bc'))
    frame(a, 15, ty0 + 2, 62, ty1 - 2, C('#40403c'))
    hline(a, 15, 62, ty0 + 2, C('#f4f4f0'))
    vline(a, 15, ty0 + 2, ty1 - 2, C('#f4f4f0'))
    a[disc_mask(W, H, 21.5, (ty0 + ty1) / 2 + 0.5, 3.0)] = C('#3a7a3a')
    st = _pil_text('スタート', 8, FONT_SANS_JP_BOLD, fill=(10, 10, 10, 255))
    paste(a, st, 26, ty0 + 3)
    rect(a, 66, ty0 + 2, 178, ty1 - 2, C('#a8a8a4'))
    frame(a, 66, ty0 + 2, 178, ty1 - 2, C('#f4f4f0'))
    hline(a, 66, 178, ty0 + 2, C('#40403c'))
    vline(a, 66, ty0 + 2, ty1 - 2, C('#40403c'))
    tb = _pil_text('定点カメラ - CamView', 8, FONT_SANS_JP_REG, fill=(10, 10, 10, 255))
    paste(a, tb, 71, ty0 + 3)
    rect(a, 438, ty0 + 2, 496, ty1 - 2, C('#b0b0ac'))
    hline(a, 438, 496, ty0 + 2, C('#80807c'))
    vline(a, 438, ty0 + 2, ty1 - 2, C('#80807c'))
    for k, c in enumerate(('#3a6ac8', '#e8e4d8', '#3aa04a')):
        rect(a, 442 + k * 8, ty0 + 5, 446 + k * 8, ty1 - 5, C(c))
    frame(a, sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, C('#050506'))
    return a


@gui('phone_frame', (512, 288))
def phone_frame():
    """Your phone held sideways, drawn by CameraApp over the phone body (the feed rect plus a 14 / 10 px margin): the
    black glossy bezel with rounded corners, the front camera dot and earpiece on the left end, a cracked corner of the
    glass; the screen (RECTS['phone_screen']) is transparent."""
    W, H = 512, 288
    img = Image.new('RGBA', (W, H), (5, 5, 6, 244))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=26, fill=(18, 18, 22, 255), outline=(58, 60, 66, 255), width=2)
    d.rounded_rectangle([4, 4, W - 5, H - 5], radius=22, outline=(30, 30, 36, 255), width=2)
    s0 = RECTS['phone_screen']
    d.rounded_rectangle([s0[0] - 2, s0[1] - 2, s0[2] + 2, s0[3] + 2], radius=10, fill=(4, 4, 6, 255))
    a = np.asarray(img, np.float32).copy()
    # bezel sheen on the top edge, the front camera and the earpiece slot on the left end
    for k in range(2, 9):
        hline(a, 30, W - 31, k, C('#2a2a32'), 0.25)
    a[disc_mask(W, H, 9.5, H / 2, 3.0)] = C('#0a0c12')
    a[disc_mask(W, H, 9.0, H / 2 - 0.8, 1.0)] = C('#2a3a5a')
    rect(a, 8, H // 2 + 14, 10, H // 2 + 40, C('#0a0a0c'))
    # the screen hole with rounded corners
    hole = Image.new('L', (W, H), 0)
    ImageDraw.Draw(hole).rounded_rectangle([s0[0], s0[1], s0[2], s0[3]], radius=9, fill=255)
    hm = np.asarray(hole) > 0
    a[hm] = 0
    # a cracked corner of the glass (bottom right): thin bright lines over the feed's corner, translucent
    cr = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dc = ImageDraw.Draw(cr)
    ox, oy = s0[2] + 1, H // 2 + 34          # from the right edge, away from the game's texts in the corners
    for (dx, dy) in ((-30, -8), (-18, 14), (-36, 6), (-10, -22)):
        dc.line([(ox, oy), (ox + dx, oy + dy)], fill=(220, 226, 230, 110), width=1)
    dc.line([(ox - 30, oy - 8), (ox - 44, oy - 4)], fill=(220, 226, 230, 80), width=1)
    a = over(a, np.asarray(cr, np.float32))
    return a


@gui('fusuma_slit', (512, 288))
def fusuma_slit():
    """Peeking through the cracked-open fusuma: the inside of the closet door (dark lining paper, a newspaper patch
    barely visible) fills the view, the panel's lacquered edge, and a narrow vertical gap of light (transparent)."""
    W, H = 512, 288
    r = rng(16401)
    x, y = grid(W, H)
    n = noise(W, H, 16402, beta=1.6, wrap=False)
    fib = noise(W, H, 16403, beta=0.3, wrap=False)
    a = blank(W, H, C('#0c0908', 252))
    a[..., :3] += (n[..., None] * 4 + fib[..., None] * 2 + 6)
    g0, g1 = RECTS['fusuma_gap'][0], RECTS['fusuma_gap'][2]
    # the gap's edges waver a little (paper edge, the frame not quite straight)
    lo = g0 + (noise(1, H, 16404, beta=1.8, wrap=False)[:, 0] * 1.4).astype(int)
    hi = g1 + (noise(1, H, 16405, beta=1.8, wrap=False)[:, 0] * 1.4).astype(int)
    for yy in range(H):
        a[yy, lo[yy]:hi[yy] + 1] = 0
        # light catching the edges: a thin warm-grey rim on the paper edge, the lacquered frame on the right
        for k, al in ((1, 120), (2, 60), (3, 25)):
            xx = lo[yy] - k
            if xx >= 0:
                a[yy, xx, :3] = a[yy, xx, :3] * (1 - al / 255) + np.array([150, 140, 120]) * al / 255
        for k in range(1, 7):
            xx = hi[yy] + k
            if xx < W:
                a[yy, xx, :3] = (20, 14, 12) if k > 1 else (70, 56, 46)
    # dark falloff toward the screen edges
    v = np.clip(np.hypot((x - (g0 + g1) / 2) / (W * 0.55), (y - H / 2) / (H * 0.8)), 0, 1)
    a[..., :3] *= (1 - v[..., None] * 0.6)
    return a


@gui('closet_dark', (512, 288))
def closet_dark():
    """Inside the 押し入れ: a dark vignette, the futon of the upper shelf bulging down over its board edge at the top
    of the view (dim floral cover), the corner of a cardboard box low on the left; the middle stays clear."""
    W, H = 512, 288
    x, y = grid(W, H)
    a = blank(W, H, C('#030303', 0))
    d = np.hypot((x - W / 2) / (W * 0.62), (y - H * 0.55) / (H * 0.62))
    a[..., 3] = np.clip((d - 0.55) * 420, 0, 245)
    # the futon edge: a soft bulging band hanging from the top
    xs = np.arange(W)
    edge = 46 + 10 * np.sin(xs / W * 2 * math.pi * 1.5 + 0.6) + noise(W, 1, 16501, beta=2.0, wrap=False)[0] * 5
    fut = y < edge[None, :]
    n = noise(W, H, 16502, beta=1.4, wrap=False)
    col = np.zeros((H, W, 3)) + np.array([28, 26, 34]) + n[..., None] * 4
    flower = (((x // 7) + (y // 7)) % 5 == 0) & ((x % 7 > 2) & (y % 7 > 2))
    col[flower] += (14, 8, 10)
    shade = np.clip((edge[None, :] - y) / 22, 0, 1)
    col *= (0.55 + 0.45 * (1 - shade[..., None]))
    a[fut, :3] = col[fut]
    a[fut, 3] = 248
    # its lower rim catching a little light
    rim = fut & (y > edge[None, :] - 3)
    a[rim, :3] = (46, 42, 52)
    # the board edge (中段) behind the futon, at the very top
    a[0:6, :, :3] = (22, 16, 12)
    a[0:6, :, 3] = 252
    # the box corner, bottom left
    box = (x < 92 - (H - y) * 0.15) & (y > 222)
    a[box, :3] = (36, 28, 20)
    a[box, 3] = 240
    a[box & (y < 225), :3] = (54, 44, 32)
    return a


# ================================================================================================ the printout
PRINT_STEPS = [
    'ぬいぐるみの綿を全部抜き、米と自分の爪を詰める',
    '赤い糸で縫い合わせ、余った糸を巻きつける',
    '人形に名前をつける（自分の名前はダメ）',
    '午前3時、「最初の鬼は○○だから」と3回言う',
    '風呂場に行き、人形を水を張った浴槽に入れる',
    '家中の明かりを消し、テレビだけつける',
    '目を閉じて10数え、刃物を持って風呂場へ',
    '「○○みつけた」と言って人形を刺す',
    '「次は○○が鬼だから」と言い、隠れる',
    '隠れる時は塩水を口に含んでおく',
    '終わる時は塩水を人形にかけ「私の勝ち」3回',
    '人形は必ず燃やして処分すること',
]
PRINT_RULES = ['一、必ず一人で行うこと', '一、隠れている間は声を出さない', '一、始めたら家から出ない',
               '一、始めたら必ず終わらせる（2時間以内）', '一、終わったら人形を燃やす']
PRINT_ITEMS = ['手足のあるぬいぐるみ', '米', '自分の爪', '赤い糸・針', '刃物', 'コップ一杯の塩水', '水を張った風呂']


@gui('printout', (256, 320))
def printout():
    """The forum thread printed on a cheap inkjet, as paper: PrintoutScreen writes the text (title, post line, the
    steps with their check boxes, the rules, the page number) over it, so the texture carries only the paper - the
    grey header band of the page (the site name is drawn on it at 12, 5), a rule under the post line, printer banding,
    a fold, a staple, a coffee ring and a smudge of red pen in the margin."""
    W, H = 256, 320
    x, y = grid(W, H)
    n = noise(W, H, 16602, beta=1.8, wrap=False)
    fib = noise(W, H, 16603, beta=0.2, wrap=False)
    a = blank(W, H, C('#ede9df'))
    a[..., :3] += n[..., None] * 3 + fib[..., None] * 1.5
    # header band of the printed page and a faint favicon square at its right end
    rect(a, 0, 0, W - 1, 15, C('#d8d2c4'))
    hline(a, 0, W - 1, 15, C('#c4bcac'))
    rect(a, W - 20, 4, W - 13, 11, C('#a8a090'))
    rect(a, W - 18, 6, W - 15, 9, C('#d8d2c4'))
    # the rule under the post line, a thin grey line
    hline(a, 8, W - 9, 45, C('#c8c2b4'))
    # printer banding: every 37 rows a pale streak, and a faint toner-starved band
    dl(a, -0.018, (y % 37) == 0)
    tint(a, '#f6f4ee', 0.25, (y >= 200) & (y <= 203))
    # fold across the middle (light / shade pair)
    hline(a, 0, W - 1, H // 2, C('#d6d2c6'))
    hline(a, 0, W - 1, H // 2 + 1, C('#f4f2ea'))
    # staple at the top left
    hline(a, 3, 9, 3, C('#9aa0a4'))
    hline(a, 3, 9, 4, C('#5a5e62'))
    # coffee ring (low right) and a smear of red pen in the right margin
    ring = np.abs(np.hypot(x - 214, y - 262) - 17) < 1.4
    tint(a, '#8a6a40', 0.2, ring)
    tint(a, '#8a6a40', 0.06, np.hypot(x - 214, y - 262) < 17)
    for k in range(6):
        px(a, 248 - (k % 2), 70 + k * 2, C('#b8302a'), 0.5)
    # the paper's edge
    vline(a, W - 1, 0, H - 1, C('#c8c2b4'))
    hline(a, 0, W - 1, H - 1, C('#c8c2b4'))
    return a


# ================================================================================================ icons, name field
def _icon_cup(a, x0, y0, full):
    for yy in range(3, 14):
        w = 4 + (yy - 3) * 0.15
        cx = x0 + 8
        px(a, int(cx - w), y0 + yy, '#d8e4e8')
        px(a, int(cx + w), y0 + yy, '#d8e4e8')
    hline(a, x0 + 3, x0 + 13, y0 + 2, '#f0f6f8')
    hline(a, x0 + 4, x0 + 12, y0 + 14, '#d8e4e8')
    if full:
        for yy in range(6, 14):
            w = 3 + (yy - 3) * 0.15
            hline(a, int(x0 + 8 - w) + 1, int(x0 + 8 + w) - 1, y0 + yy, '#c8d8dc')
        hline(a, x0 + 5, x0 + 11, y0 + 6, '#ffffff')
        px(a, x0 + 7, y0 + 10, '#ffffff')
        px(a, x0 + 9, y0 + 12, '#ffffff')


def _icon_mouth(a, x0, y0, full):
    """Lips: full = pressed shut, cheeks puffed (salt water held); empty = open."""
    if full:
        for (dx, dy) in ((3, 7), (2, 8), (2, 9), (3, 10), (13, 7), (14, 8), (14, 9), (13, 10)):
            px(a, x0 + dx, y0 + dy, '#d8b8a8')
        hline(a, x0 + 5, x0 + 11, y0 + 8, '#b84a4a')
        hline(a, x0 + 5, x0 + 11, y0 + 9, '#8a2a2a')
        hline(a, x0 + 6, x0 + 10, y0 + 10, '#b84a4a')
        px(a, x0 + 12, y0 + 5, '#a8d8f0')
    else:
        hline(a, x0 + 5, x0 + 11, y0 + 6, '#b84a4a')
        rect(a, x0 + 5, y0 + 7, x0 + 11, y0 + 9, '#1a0808')
        hline(a, x0 + 5, x0 + 11, y0 + 10, '#b84a4a')
        px(a, x0 + 4, y0 + 8, '#b84a4a')
        px(a, x0 + 12, y0 + 8, '#b84a4a')


def _icon_breath(a, x0, y0, held):
    for k, yy in enumerate((5, 8, 11)):
        L = 9 - k * 2
        hline(a, x0 + 3, x0 + 3 + L, y0 + yy, '#e0e8ec')
        px(a, x0 + 4 + L, y0 + yy - 1, '#e0e8ec')
    if held:
        line(a, x0 + 2, y0 + 2, x0 + 13, y0 + 13, '#ff4a3a')
        line(a, x0 + 3, y0 + 2, x0 + 14, y0 + 13, '#c82a1a')


def _icon_cam(a, x0, y0, n):
    rect(a, x0 + 1, y0 + 3, x0 + 14, y0 + 12, '#1a1a1e')
    frame(a, x0 + 1, y0 + 3, x0 + 14, y0 + 12, '#e8e8e8')
    m = np.zeros((5, 3))
    from hk_lib import text35
    g = text35(str(n))
    stamp_mask(a, g, x0 + 9, y0 + 5, '#f0f0f0')
    rect(a, x0 + 3, y0 + 6, x0 + 6, y0 + 9, '#5a8ab8')
    px(a, x0 + 12, y0 + 4, '#ff2a1a')


def _icon_box(a, x0, y0, ticked):
    """A pencil-drawn check box (the printout's), ticked in red pen."""
    frame(a, x0 + 3, y0 + 4, x0 + 12, y0 + 13, '#d8d4cc')
    if ticked:
        for (dx, dy) in ((4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4), (13, 3)):
            px(a, x0 + dx, y0 + dy, '#e83a2a')
            px(a, x0 + dx, y0 + dy + 1, '#a82a1a')


@gui('icons', (128, 32))
def icons():
    """16x16 cells, indexed as the game's HorrorOverlay uses them: 0 cup (salt water), 1 breath, 2 REC, 3 cup empty,
    4 breath held, 5 mouth full (cheeks puffed), 6 mouth empty, 7 check (ticked box), 8 box (empty), 9-12 CAM 1-4,
    13 battery, 14 phone vibrating, 15 eye (peek)."""
    a = blank(128, 32)
    _icon_cup(a, 0, 0, True)
    _icon_breath(a, 16, 0, False)
    a[disc_mask(128, 32, 40, 8, 4.5)] = C('#ff2a1a')
    a[disc_mask(128, 32, 40, 8, 2.0)] = C('#ff8a7a')
    _icon_cup(a, 48, 0, False)
    _icon_breath(a, 64, 0, True)
    _icon_mouth(a, 80, 0, True)
    _icon_mouth(a, 96, 0, False)
    _icon_box(a, 112, 0, True)
    _icon_box(a, 0, 16, False)
    for k in range(4):
        _icon_cam(a, 16 + k * 16, 16, k + 1)
    frame(a, 82, 21, 93, 27, '#f0f0f0')
    rect(a, 94, 23, 94, 25, '#f0f0f0')
    rect(a, 84, 23, 89, 25, '#f0f0f0')
    rect(a, 100, 18, 107, 29, '#1a1a1e')
    frame(a, 100, 18, 107, 29, '#e8e8e8')
    for (dx, dy) in ((97, 21), (97, 25), (110, 21), (110, 25), (96, 23), (111, 23)):
        px(a, dx, dy, '#e8e8e8')
    img = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([114, 20, 125, 27], outline=(232, 232, 232, 255), width=1)
    a = over(a, np.asarray(img, np.float32))
    a[disc_mask(128, 32, 120, 23.5, 2.2)] = C('#e8e8e8')
    px(a, 120, 23, '#1a1a1a')
    return a


@gui('name_field', (192, 36))
def name_field():
    """The naming field (drawn at 192 x 36; the game's EditBox writes light text, no border, at 16..176 x 10..28): a
    dark cloth name tag with a red running stitch around it, sewn on - the name reads light on it."""
    W, H = 192, 36
    x, y = grid(W, H)
    a = blank(W, H)
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([1, 1, W - 2, H - 2], radius=5, fill=(40, 22, 24, 255))
    a = over(a, np.asarray(img, np.float32))
    inside = a[..., 3] > 0
    weave = ((x + y) % 2 == 0) & inside
    dl(a, 0.015, weave)
    n = noise(W, H, 16801, beta=1.8, wrap=False)
    a[..., :3] += n[..., None] * 3
    for xx in range(7, W - 8, 6):
        hline(a, xx, xx + 2, 4, '#c41a1a')
        hline(a, xx, xx + 2, H - 5, '#c41a1a')
    for yy in range(7, H - 8, 6):
        vline(a, 4, yy, yy + 2, '#c41a1a')
        vline(a, W - 5, yy, yy + 2, '#c41a1a')
    r0 = RECTS['name_text']
    rect(a, r0[0], r0[3] + 1, r0[2], r0[3] + 1, C('#5a3a3a'))
    # a loose end of the red thread at the corner
    for k in range(5):
        px(a, W - 6 + (k % 2), H - 5 + k // 2, '#c41a1a')
    return a


# ================================================================================================ copies
def _load(name):
    p = os.path.join(HERE, 'data', name)
    return np.asarray(Image.open(p).convert('RGBA'), np.float32)


@gui('vignette', (256, 256))
def vignette():
    return _load('gui_vignette.png')


@gui('grain', (256, 256))
def grain():
    return _load('gui_grain.png')
