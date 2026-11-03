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
    # laptop_frame: the camera app's transparent viewport, the status line (REC / CAM name / time drawn by code),
    # the four camera tab buttons (highlight the active one by code) and the taskbar clock
    'laptop_viewport': (52, 58, 459, 243),
    'laptop_status': (52, 245, 459, 255),
    'laptop_tabs': [(52, 45, 109, 56), (112, 45, 169, 56), (172, 45, 229, 56), (232, 45, 289, 56)],
    'laptop_clock': (452, 263, 494, 274),
    # phone_frame: the screen (transparent) inside the bezel
    'phone_screen': (136, 82, 375, 216),
    # cctv: free corners for code text: bottom-left CAM name, bottom-right timestamp
    'cctv_cam_label': (22, 252, 220, 270),
    'cctv_timestamp': (300, 252, 490, 270),
    # fusuma_slit: the gap of light
    'fusuma_gap': (243, 0, 263, 287),
    # name_field: where the typed name goes
    'name_text': (78, 12, 244, 36),
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
    """Camera-app overlay: corner brackets, ● REC, the battery, IR / resolution tags, a faint centre cross and edge
    darkening; transparent in the middle. The CAM name (bottom-left) and timestamp (bottom-right) are drawn by code."""
    W, H = 512, 288
    a = blank(W, H)
    x, y = grid(W, H)
    # edge vignette (darker corners like a cheap wide lens)
    d = np.hypot((x - W / 2) / (W / 2), (y - H / 2) / (H / 2))
    a[..., 3] = np.clip((d - 0.82) * 260, 0, 120)
    _corner_brackets(a)
    # REC
    a[disc_mask(W, H, 30, 30, 5.5)] = C('#ff2a1a')
    rec = _pil_text('REC', 15, FONT_MONO_B, fill=(245, 245, 245, 255), spacing=1)
    paste(a, rec, 40, 23)
    # battery (3/4)
    frame(a, 444, 23, 470, 36, C('#f0f0f0'))
    frame(a, 445, 24, 469, 35, C('#f0f0f0'))
    rect(a, 471, 27, 473, 32, C('#f0f0f0'))
    for k in range(3):
        rect(a, 448 + k * 7, 27, 452 + k * 7, 32, C('#f0f0f0'))
    tag = _pil_text('IR  1080p', 11, FONT_MONO_B, fill=(235, 235, 235, 230), spacing=0)
    paste(a, tag, 438 - tag.shape[1], 25)
    # centre cross
    for k in range(-7, 8):
        if abs(k) > 2:
            px(a, W // 2 + k, H // 2, C('#f0f0f0', 150))
            px(a, W // 2, H // 2 + k, C('#f0f0f0', 150))
    # safe-area tick marks at the edge centres
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
    """The laptop seen up close: the dark plastic bezel with its webcam, an old OS desktop (teal), the taskbar with a
    start button and the clock area, and the 定点カメラ viewer window: menu bar, CAM 1-4 tabs, the transparent
    viewport and a status line. Drawn stretched over the screen; RECTS gives the regions for code."""
    W, H = 512, 288
    a = blank(W, H, C('#0e0e10'))
    x, y = grid(W, H)
    # bezel: dark plastic with a soft sheen, a webcam dot, a few fingerprints
    n = noise(W, H, 16201, beta=1.8, wrap=False)
    a[..., :3] += n[..., None] * 3 + 10
    a[disc_mask(W, H, W / 2, 6.5, 2.2)] = C('#1e2228')
    px(a, W // 2, 6, C('#3a4a5a'))
    # screen: the desktop wallpaper
    sx0, sy0, sx1, sy1 = 14, 12, 497, 275
    rect(a, sx0, sy0, sx1, sy1, C('#245a5c'))
    wall = noise(sx1 - sx0 + 1, sy1 - sy0 + 1, 16202, beta=2.0, wrap=False)
    a[sy0:sy1 + 1, sx0:sx1 + 1, :3] += wall[..., None] * 6
    # two desktop icons on the left (a folder, the recycle bin)
    rect(a, 20, 20, 33, 30, C('#e8c860'))
    rect(a, 20, 18, 26, 20, C('#e8c860'))
    rect(a, 21, 40, 31, 52, C('#c8ccd0'))
    hline(a, 20, 32, 40, C('#e8ecee'))
    # taskbar
    rect(a, sx0, 262, sx1, sy1, C('#c0c0bc'))
    hline(a, sx0, sx1, 262, C('#f4f4f0'))
    rect(a, 17, 264, 58, 273, C('#c0c0bc'))
    frame(a, 17, 264, 58, 273, C('#40403c'))
    hline(a, 17, 58, 264, C('#f4f4f0'))
    vline(a, 17, 264, 273, C('#f4f4f0'))
    st = _pil_text('スタート', 9, FONT_SANS_JP_BOLD, fill=(10, 10, 10, 255))
    paste(a, st, 22, 265)
    rect(a, 62, 264, 170, 273, C('#a8a8a4'))
    tb = _pil_text('定点カメラ', 9, FONT_SANS_JP_REG, fill=(10, 10, 10, 255))
    paste(a, tb, 66, 265)
    c0 = RECTS['laptop_clock']
    rect(a, c0[0], c0[1], c0[2], c0[3], C('#b0b0ac'))
    hline(a, c0[0], c0[2], c0[1], C('#80807c'))
    # the viewer window
    wx0, wy0, wx1, wy1 = 44, 20, 467, 258
    _window_chrome(a, wx0, wy0, wx1, wy1, '定点カメラ - CamView 2.1')
    menu = _pil_text('ファイル(F)  表示(V)  カメラ(C)  録画(R)  ヘルプ(H)', 9, FONT_SANS_JP_REG, fill=(10, 10, 10, 255))
    paste(a, menu, wx0 + 6, wy0 + 15)
    for k, (t0, t1, t2, t3) in enumerate(RECTS['laptop_tabs']):
        rect(a, t0, t1, t2, t3, C('#c8c8c4'))
        hline(a, t0, t2, t1, C('#f4f4f0'))
        vline(a, t0, t1, t3, C('#f4f4f0'))
        vline(a, t2, t1, t3, C('#40403c'))
        lab = _pil_text(f'CAM {k + 1}', 9, FONT_MONO_B, fill=(10, 10, 10, 255))
        paste(a, lab, (t0 + t2) // 2 - lab.shape[1] // 2, t1 + 2)
    v0 = RECTS['laptop_viewport']
    frame(a, v0[0] - 1, v0[1] - 1, v0[2] + 1, v0[3] + 1, C('#40403c'))
    a[v0[1]:v0[3] + 1, v0[0]:v0[2] + 1] = 0
    s0 = RECTS['laptop_status']
    rect(a, s0[0], s0[1], s0[2], s0[3], C('#b8b8b4'))
    hline(a, s0[0], s0[2], s0[1], C('#80807c'))
    a[disc_mask(W, H, s0[0] + 6, s0[1] + 5.5, 3.0)] = C('#c81a10')
    # screen glass: a faint reflection streak over the bezel edge, the screen's dark edge
    frame(a, sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, C('#050506'))
    return a


@gui('phone_frame', (512, 288))
def phone_frame():
    """Your phone held sideways in the dark: everything around it near black, the bezel and its camera notch, two
    fingers at the edges, the camera app's soft buttons; the screen itself transparent (RECTS['phone_screen'])."""
    W, H = 512, 288
    a = blank(W, H, C('#050506', 246))
    x, y = grid(W, H)
    d = np.hypot((x - W / 2) / (W * 0.42), (y - H / 2) / (H * 0.42))
    a[..., 3] = np.clip(200 + (d - 0.6) * 160, 200, 250)
    # the phone body (rounded rectangle)
    bx0, by0, bx1, by1 = 118, 70, 393, 228
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle([bx0, by0, bx1, by1], radius=18, fill=(22, 22, 26, 255), outline=(60, 62, 68, 255), width=2)
    s = RECTS['phone_screen']
    dr.rounded_rectangle([s[0] - 2, s[1] - 2, s[2] + 2, s[3] + 2], radius=8, fill=(4, 4, 6, 255))
    # fingers: left hand's thumb and fingers wrapping the left end, the right thumb at the right end (dim skin)
    dr.rounded_rectangle([96, 120, 132, 150], radius=12, fill=(58, 44, 38, 255))
    dr.rounded_rectangle([92, 160, 128, 190], radius=12, fill=(52, 40, 34, 255))
    dr.rounded_rectangle([380, 150, 420, 178], radius=12, fill=(60, 46, 40, 255))
    body = np.asarray(img, np.float32)
    a = over(a, body)
    a[disc_mask(W, H, bx0 + 9, (by0 + by1) / 2, 2.5)] = C('#1e2a3a')
    # the screen hole
    a[s[1]:s[3] + 1, s[0]:s[2] + 1] = 0
    rnd = Image.new('L', (W, H), 0)
    ImageDraw.Draw(rnd).rounded_rectangle([s[0], s[1], s[2], s[3]], radius=7, fill=255)
    hole = np.asarray(rnd) > 0
    corner = (x >= s[0]) & (x <= s[2]) & (y >= s[1]) & (y <= s[3]) & ~hole
    a[corner] = C('#040406')
    # soft UI on the screen: REC, the camera selector chips (code highlights the active one), the shutter
    a[disc_mask(W, H, s[0] + 9, s[1] + 9, 3.5)] = C('#ff2a1a', 230)
    for k in range(4):
        cx = s[0] + 70 + k * 30
        img2 = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(img2).rounded_rectangle([cx - 11, s[3] - 18, cx + 11, s[3] - 6], radius=6,
                                               fill=(0, 0, 0, 110), outline=(230, 230, 230, 160))
        a = over(a, np.asarray(img2, np.float32))
        lab = _pil_text(str(k + 1), 9, FONT_MONO_B, fill=(240, 240, 240, 230))
        paste(a, lab, cx - lab.shape[1] // 2, s[3] - 16)
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
    """The forum thread printed on a cheap inkjet: board header, the thread title 「ひとりかくれんぼのやり方」, the
    things to prepare (pencil ticks beside them), the numbered procedure, the rules, the URL and page footer, a
    coffee ring and handwritten notes in red pen."""
    W, H = 256, 320
    r = rng(16601)
    x, y = grid(W, H)
    n = noise(W, H, 16602, beta=1.8, wrap=False)
    a = blank(W, H, C('#efede6'))
    a[..., :3] += n[..., None] * 3
    INK = (34, 34, 36, 255)
    GREY = (110, 110, 112, 255)
    # header bar
    rect(a, 0, 0, W - 1, 13, C('#d8d8d4'))
    hdr = _pil_text('■オカルト板 ＞ 怖い話・都市伝説スレ', 8, FONT_SANS_JP_REG, fill=GREY)
    paste(a, hdr, 5, 3)
    title = _pil_text('ひとりかくれんぼのやり方', 14, FONT_SANS_JP_BOLD, fill=INK)
    paste(a, title, 8, 17)
    meta = _pil_text('1 名前：本当にあった怖い名無し 2006/02/28(火) 03:12:44', 7, FONT_SANS_JP_REG, fill=GREY)
    paste(a, meta, 8, 35)
    hline(a, 6, W - 7, 44, C('#b8b8b4'))
    LH = 10
    yy = 47
    head = _pil_text('【用意するもの】', 9, FONT_SANS_JP_BOLD, fill=INK)
    paste(a, head, 8, yy)
    yy += 11
    col_x = [16, 132]
    for k, it in enumerate(PRINT_ITEMS):
        cx = col_x[k % 2]
        cy = yy + (k // 2) * LH
        t = _pil_text('・' + it, 9, FONT_SANS_JP_REG, fill=INK)
        paste(a, t, cx, cy)
        for (dx, dy) in ((-8, 4), (-7, 5), (-6, 6), (-5, 5), (-4, 4), (-3, 3), (-2, 2)):      # pencil tick
            px(a, cx + dx, cy + dy, C('#5a5a5e', 200))
    yy += 4 * LH + 2
    head = _pil_text('【やり方】', 9, FONT_SANS_JP_BOLD, fill=INK)
    paste(a, head, 8, yy)
    yy += 11
    step_y = {}
    for k, st in enumerate(PRINT_STEPS):
        num = _pil_text(f'{k + 1}.', 9, FONT_SANS_JP_REG, fill=INK)
        paste(a, num, 10, yy)
        t = _pil_text(st, 9, FONT_SANS_JP_REG, fill=INK)
        paste(a, t, 24, yy)
        step_y[k] = (yy, t.shape[1])
        yy += LH
    yy += 2
    head = _pil_text('【注意】', 9, FONT_SANS_JP_BOLD, fill=(150, 20, 20, 255))
    paste(a, head, 8, yy)
    yy += 11
    for ru in PRINT_RULES:
        t = _pil_text(ru, 9, FONT_SANS_JP_REG, fill=INK)
        paste(a, t, 14, yy)
        yy += LH
    foot = _pil_text('http://occult-bbs.jp/test/read.cgi/occult/1141063964/  1/1', 6, FONT_SANS_JP_REG, fill=GREY)
    paste(a, foot, 8, H - 9)
    # handwritten notes in red pen: a reminder beside the list, step 10 circled
    note = _pil_text('カメラ4台！', 10, FONT_SANS_JP_MED, fill=(190, 30, 24, 230))
    note = np.asarray(Image.fromarray(note.astype(np.uint8)).rotate(8, expand=True, resample=Image.BICUBIC),
                      np.float32)
    paste(a, note, 180, 44)
    circ = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(circ)
    ty, tw = step_y[9]
    dr.ellipse([18, ty - 2, 24 + tw + 6, ty + 11], outline=(190, 30, 24, 200), width=1)
    a = over(a, np.asarray(circ, np.float32))
    # a coffee ring and a fold line, the printer's banding
    ring = np.abs(np.hypot(x - 214, y - 262) - 17) < 1.4
    tint(a, '#8a6a40', 0.22, ring)
    hline(a, 0, W - 1, H // 2, C('#d8d6ce'))
    band = (y % 37) == 0
    dl(a, -0.02, band)
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


@gui('icons', (128, 32))
def icons():
    """16x16 cells. Row 0: cup empty, cup full (salt water), mouth full, mouth empty, breath, breath held, REC on,
    REC off. Row 1: CAM 1, CAM 2, CAM 3, CAM 4, battery, phone vibrating, eye (peek), tick (step done)."""
    a = blank(128, 32)
    _icon_cup(a, 0, 0, False)
    _icon_cup(a, 16, 0, True)
    _icon_mouth(a, 32, 0, True)
    _icon_mouth(a, 48, 0, False)
    _icon_breath(a, 64, 0, False)
    _icon_breath(a, 80, 0, True)
    a[disc_mask(128, 32, 104, 8, 4.5)] = C('#ff2a1a')
    a[disc_mask(128, 32, 104, 8, 2.0)] = C('#ff8a7a')
    a[disc_mask(128, 32, 120, 8, 4.5)] = C('#4a1410')
    for k in range(4):
        _icon_cam(a, k * 16, 16, k + 1)
    frame(a, 66, 21, 77, 27, '#f0f0f0')
    rect(a, 78, 23, 78, 25, '#f0f0f0')
    rect(a, 68, 23, 73, 25, '#f0f0f0')
    rect(a, 84, 18, 91, 29, '#1a1a1e')
    frame(a, 84, 18, 91, 29, '#e8e8e8')
    for (dx, dy) in ((81, 21), (81, 25), (94, 21), (94, 25), (80, 23), (95, 23)):
        px(a, dx, dy, '#e8e8e8')
    img = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([98, 20, 109, 27], outline=(232, 232, 232, 255), width=1)
    a = over(a, np.asarray(img, np.float32))
    a[disc_mask(128, 32, 104, 23.5, 2.2)] = C('#e8e8e8')
    px(a, 104, 23, '#1a1a1a')
    for (dx, dy) in ((114, 24), (115, 25), (116, 26), (117, 25), (118, 24), (119, 23), (120, 22), (121, 21)):
        px(a, dx, dy, '#d83a2a')
        px(a, dx, dy + 1, '#a82a1a')
    return a


@gui('name_field', (256, 48))
def name_field():
    """The naming field: a sewn-on cloth name tag (名札) - off-white cotton with a red running stitch round it, the
    label 「なまえ」 at the left; the typed name goes in RECTS['name_text']."""
    W, H = 256, 48
    x, y = grid(W, H)
    a = blank(W, H)
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([4, 4, W - 5, H - 5], radius=6, fill=(236, 232, 222, 255))
    a = over(a, np.asarray(img, np.float32))
    weave = ((x + y) % 2 == 0) & (a[..., 3] > 0)
    dl(a, -0.015, weave)
    n = noise(W, H, 16801, beta=1.8, wrap=False)
    a[..., :3] += n[..., None] * 4
    # running stitch in red thread just inside the edge
    for xx in range(10, W - 10, 6):
        hline(a, xx, xx + 2, 8, '#c41a1a')
        hline(a, xx, xx + 2, H - 9, '#c41a1a')
    for yy in range(10, H - 10, 6):
        vline(a, 8, yy, yy + 2, '#c41a1a')
        vline(a, W - 9, yy, yy + 2, '#c41a1a')
    lab = _pil_text('なまえ', 11, FONT_SANS_JP_BOLD, fill=(180, 26, 26, 255))
    paste(a, lab, 16, 17)
    vline(a, 70, 12, H - 13, '#d8b8b0')
    hline(a, 78, 244, 37, '#c8c0b0')
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
