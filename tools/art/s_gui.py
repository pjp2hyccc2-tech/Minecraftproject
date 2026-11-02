"""GUI textures: title logo, faces overlay, the phone (frame, wallpapers, app icons).

Layout facts from client/gui/PhoneScreen: frame 160x320 drawn over the screen at x 8..151, y 24..295; the home
screen darkens the wallpaper (x0.75), puts the clock on a dark band at y 26..74 and the app icons on a dark panel
at y 160..262 -> the free band of the wallpaper is y ~80..160, which is where the figure appears."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

from common import Tex, texture, ramp, pal, C, fnoise, vnoise, white, grid, rng, font, smoothstep, \
    FONT_SERIF_JP_BLACK, FONT_SERIF_JP_BOLD, FONT_SANS_JP_BOLD, FONT_SANS_JP_BLACK

SS = 2  # supersampling for painted images


def to_tex(arr):
    t = Tex(arr.shape[1], arr.shape[0])
    t.a[:] = np.asarray(arr, np.float32)
    return t


def down(arr, k):
    """Box-downsample an (H, W, 4) float array by k with premultiplied alpha."""
    h, w = arr.shape[:2]
    a = arr[..., 3:4] / 255.0
    pm = np.concatenate([arr[..., :3] * a, a * 255.0], -1)
    pm = pm[:h // k * k, :w // k * k].reshape(h // k, k, w // k, k, 4).mean((1, 3))
    al = pm[..., 3:4] / 255.0
    rgb = np.where(al > 1e-4, pm[..., :3] / np.maximum(al, 1e-4), 0)
    return np.concatenate([rgb, pm[..., 3:4]], -1)


def blur(a, s):
    return ndimage.gaussian_filter(a, s, mode='nearest')


# ================================================================================================ title logo
def brush_glyphs(text, size, font_spec, seed):
    """Brush-calligraphy mask of `text`: wobbly outlines, dry-brush streaks along the strokes, ink pooling, splatter."""
    W, H = int(size * len(text) * 1.05 + size * 0.4), int(size * 1.4)
    im = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(im)
    f = font(font_spec, size)
    x = int(size * 0.2)
    for ch in text:
        bb = d.textbbox((0, 0), ch, font=f)
        d.text((x - bb[0], int(size * 0.18) - bb[1]), ch, fill=255, font=f)
        x += int(size * 1.02)
    m = np.asarray(im, np.float32) / 255.0
    r = rng(seed)
    # wobble: displace the glyphs with smooth noise (hand tremor)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dx = blur(r.normal(0, 1, (H, W)), size * 0.05) * size * 0.9
    dy = blur(r.normal(0, 1, (H, W)), size * 0.05) * size * 0.9
    m = ndimage.map_coordinates(m, [yy + dy, xx + dx], order=1, mode='constant')
    # thicken the starts of strokes a little (ink pooling) via a blurred dilation blend
    pool = blur(m, size * 0.02)
    m = np.clip(m * 0.85 + pool * 0.4, 0, 1)
    # stroke direction from the structure tensor -> dry brush streaks run along the stroke
    gy, gx = np.gradient(blur(m, size * 0.03))
    Jxx, Jyy, Jxy = blur(gx * gx, size * 0.05), blur(gy * gy, size * 0.05), blur(gx * gy, size * 0.05)
    ang = 0.5 * np.arctan2(2 * Jxy, Jxx - Jyy)          # gradient orientation (across the stroke)
    across = np.cos(ang) * xx + np.sin(ang) * yy          # coordinate across the stroke
    streak = np.sin(across * (2 * math.pi / (size * 0.035)) + blur(r.normal(0, 1, (H, W)), 3) * 6)
    streak2 = np.sin(across * (2 * math.pi / (size * 0.013)) + blur(r.normal(0, 1, (H, W)), 2) * 4)
    edge_dist = ndimage.distance_transform_edt(m > 0.5)
    dryness = blur(r.random((H, W)), size * 0.18)
    dryness = (dryness - dryness.min()) / (np.ptp(dryness) + 1e-6)
    # dry patches where the brush ran out of ink (more likely towards stroke edges), streaked along the stroke
    dry_zone = np.clip((dryness - 0.5) * 3.0, 0, 1) * (0.55 + 0.45 * np.exp(-edge_dist / (size * 0.05)))
    dry = ((streak * 0.65 + streak2 * 0.35) > (1.08 - dry_zone * 1.05)) & (dry_zone > 0.05)
    ink = (m > 0.5) & ~dry
    # ragged edges
    rough = blur(r.normal(0, 1, (H, W)), 1.2)
    ink &= (m + rough * 0.18) > 0.5
    # splatter and a drip
    ys, xs = np.nonzero(m > 0.5)
    for _ in range(12):
        k = r.integers(0, len(xs))
        ang_ = r.uniform(0, 2 * math.pi)
        dist = r.uniform(size * 0.05, size * 0.22)
        ox, oy = xs[k] + math.cos(ang_) * dist, ys[k] + math.sin(ang_) * dist
        rad = r.uniform(size * 0.006, size * 0.02)
        ink |= (xx - ox) ** 2 + (yy - oy) ** 2 < rad ** 2
    return ink.astype(np.float32), m


@texture('gui/title_logo', size=(512, 256))
def title_logo():
    S = SS
    W, H = 512 * S, 256 * S
    out = np.zeros((H, W, 4), np.float32)
    ink, m = brush_glyphs('遭難', 150 * S, FONT_SERIF_JP_BLACK, 2401)
    ih, iw = ink.shape
    ox, oy = (W - iw) // 2, int(6 * S)
    canvas = np.zeros((H, W), np.float32)
    canvas[oy:oy + ih, ox:ox + iw] = ink[:min(ih, H - oy), :min(iw, W - ox)]
    # pale, dirty ink (reads on the dark title screen), a dark halo so it reads on anything
    halo = np.clip(blur(canvas, 5 * S) * 2.2, 0, 1) * 0.75
    n = blur(rng(2402).normal(0, 1, (H, W)), 2) * 18
    col = np.zeros((H, W, 3), np.float32)
    col[..., 0] = 206 + n
    col[..., 1] = 201 + n * 0.95
    col[..., 2] = 190 + n * 0.9
    # a red stain bleeding through the lower strokes
    yy, xx = np.mgrid[0:H, 0:W]
    stain = np.clip((yy - H * 0.35) / (H * 0.3), 0, 1) * blur(rng(2403).random((H, W)), 9 * S)
    stain = np.clip((stain - 0.45) * 3, 0, 1)
    col = col * (1 - stain[..., None] * 0.55) + np.array([120, 18, 14]) * stain[..., None] * 0.55
    out[..., :3] = col * canvas[..., None]
    out[..., 3] = np.maximum(canvas, halo) * 255
    out[..., :3] = np.where(canvas[..., None] > 0, out[..., :3] / np.maximum(canvas[..., None], 1e-6), np.array([8, 6, 6]))
    # "SOUNAN" in spaced capitals with thin rules: ― S O U N A N ―
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    fnt = font(FONT_SERIF_JP_BOLD, 26 * S)
    label = '  '.join('SOUNAN')
    tw = d.textlength(label, font=fnt)
    ty = int(208 * S)
    d.text(((W - tw) / 2, ty), label, fill=255, font=fnt)
    lw = int(70 * S)
    d.line([((W - tw) / 2 - lw - 16 * S, ty + 17 * S), ((W - tw) / 2 - 16 * S, ty + 17 * S)], fill=200, width=S * 2)
    d.line([((W + tw) / 2 + 16 * S, ty + 17 * S), ((W + tw) / 2 + lw + 16 * S, ty + 17 * S)], fill=200, width=S * 2)
    lab = np.asarray(img, np.float32) / 255.0
    labc = np.array([150, 32, 26], np.float32)
    a0 = out[..., 3:4] / 255.0
    la = lab[..., None]
    out[..., :3] = out[..., :3] * (1 - la) + labc * la
    out[..., 3] = np.maximum(out[..., 3], lab * 235 + blur(lab, 3 * S) * 120)
    return to_tex(down(out, S))


# ================================================================================================ faces overlay
def soft_face(h, w, seed, scream=False):
    """A pale, blurred face height-shaded from the front: what the eye invents in the dark."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
    r = rng(seed)
    head = np.clip(1 - (u / 0.78) ** 2 - (v / 0.98) ** 2, 0, 1) ** 0.6
    def g(cx, cy, sx, sy):
        return np.exp(-(((u - cx) / sx) ** 2 + ((v - cy) / sy) ** 2))
    ex = r.uniform(0.24, 0.34)
    eyes = g(-ex, -0.12, 0.17, 0.12) + g(ex, -0.12, 0.17, 0.12)
    mouth = g(0, 0.48, 0.28 if not scream else 0.2, 0.08 if not scream else 0.24)
    nose = g(0, 0.16, 0.06, 0.12)
    face = head * (1 - np.clip(eyes * 1.4, 0, 1)) * (1 - np.clip(mouth * 1.3, 0, 1)) + nose * 0.1 * head
    face += g(0, -0.55, 0.5, 0.3) * 0.12 * head
    return np.clip(face, 0, 1)


@texture('gui/faces_overlay', size=(256, 256))
def faces_overlay():
    S = SS
    W = H = 256 * S
    a = np.zeros((H, W), np.float32)
    r = rng(2501)
    spots = [(0.10, 0.18, 46), (0.88, 0.12, 38), (0.92, 0.62, 52), (0.07, 0.72, 58), (0.30, 0.94, 34), (0.70, 0.93, 42),
             (0.50, 0.05, 30), (0.03, 0.42, 30), (0.97, 0.36, 28)]
    for i, (fx, fy, sz) in enumerate(spots):
        s = int(sz * S)
        f = soft_face(int(s * 1.25), s, 2510 + i, scream=(i % 3 == 0))
        f = blur(f, 0.9 * S)
        cx, cy = int(fx * W), int(fy * H)
        y0, x0 = cy - f.shape[0] // 2, cx - f.shape[1] // 2
        ys, xs = slice(max(0, y0), min(H, y0 + f.shape[0])), slice(max(0, x0), min(W, x0 + f.shape[1]))
        sub = f[ys.start - y0:ys.stop - y0, xs.start - x0:xs.stop - x0]
        a[ys, xs] = np.maximum(a[ys, xs], sub * r.uniform(0.5, 0.9))
    yy, xx = np.mgrid[0:H, 0:W]
    rad = np.hypot((xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2))
    a *= smoothstep(0.45, 0.95, rad)              # only towards the edges
    a *= 0.75 + 0.25 * blur(r.random((H, W)), 6 * S) * 2
    out = np.zeros((H, W, 4), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = 150, 156, 162
    out[..., 3] = np.clip(a, 0, 1) * 70
    return to_tex(down(out, S))


# ================================================================================================ phone frame
@texture('gui/phone/frame', size=(160, 320))
def phone_frame():
    S = 4
    W, H = 160 * S, 320 * S
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

    def rrect(x0, y0, x1, y1, rad):
        cx = np.clip(xx, x0 + rad, x1 - rad)
        cy = np.clip(yy, y0 + rad, y1 - rad)
        return np.hypot(xx - cx, yy - cy) <= rad
    body = rrect(0.5 * S, 0.5 * S, 159.5 * S, 319.5 * S, 20 * S)
    bezel = rrect(3 * S, 3 * S, 157 * S, 317 * S, 17 * S)
    screen = (xx >= 8 * S) & (xx < 152 * S) & (yy >= 24 * S) & (yy < 296 * S)
    out = np.zeros((H, W, 4), np.float32)
    # aluminium rim: lit along the top-left, dark bottom-right
    shade = 40 + 26 * np.clip(1 - (xx + yy) / (W + H), 0, 1)
    rim = body & ~bezel
    out[rim, :3] = np.stack([shade, shade + 2, shade + 6], -1)[rim]
    edge = body & ~rrect(1.5 * S, 1.5 * S, 158.5 * S, 318.5 * S, 19 * S)
    out[edge, :3] = 82
    # black glass bezel with a faint reflection gradient
    g = 8 + 10 * np.clip(1 - yy / H, 0, 1) + 6 * np.exp(-((xx - yy * 0.4 - 30 * S) / (18 * S)) ** 2)
    out[bezel, :3] = np.stack([g, g + 1, g + 3], -1)[bezel]
    out[body, 3] = 255
    # earpiece slot + front camera, side buttons
    ear = rrect(64 * S, 10 * S, 96 * S, 13 * S, 1.5 * S)
    out[ear, :3] = [26, 27, 30]
    cam = np.hypot(xx - 110 * S, yy - 11.5 * S) < 3.2 * S
    out[cam, :3] = [14, 18, 28]
    lens = np.hypot(xx - 109 * S, yy - 10.6 * S) < 1.0 * S
    out[lens, :3] = [60, 80, 110]
    # scratches and a crack in the glass at the lower-left corner of the bezel (it fell down the slope)
    r = rng(2601)
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    for _ in range(14):
        x0, y0 = r.uniform(4, 156) * S, r.uniform(4, 316) * S
        a = r.uniform(0, math.pi)
        ln = r.uniform(3, 14) * S
        d.line([(x0, y0), (x0 + math.cos(a) * ln, y0 + math.sin(a) * ln)], fill=int(r.uniform(30, 70)), width=1)
    cx, cy = 10 * S, 306 * S
    for k in range(7):
        a = -math.pi / 2 + k * 0.32 + r.normal(0, 0.1)
        pts = [(cx, cy)]
        x, y = cx, cy
        for s in range(5):
            x += math.cos(a + r.normal(0, 0.3)) * r.uniform(3, 8) * S
            y += math.sin(a + r.normal(0, 0.3)) * r.uniform(3, 8) * S
            pts.append((x, y))
        d.line(pts, fill=140, width=S)
    sc = np.asarray(img, np.float32) / 255.0
    sc[~bezel] = 0
    out[..., :3] += sc[..., None] * np.array([120, 125, 130])
    # mud on the rim
    mud = (blur(r.random((H, W)), 6 * S) > 0.53) & rim & (yy > 200 * S)
    out[mud, :3] = out[mud, :3] * 0.4 + np.array([60, 44, 30]) * 0.6
    out[screen] = 0
    return to_tex(down(out, S))


# ================================================================================================ wallpapers
def ridge(w, seed, base, amp, octaves=4, rough=0.42):
    r = rng(seed)
    x = np.arange(w, dtype=np.float32)
    y = np.zeros(w, np.float32)
    a, f = amp, 1.0 / w * 2
    for o in range(octaves):
        ph = r.uniform(0, 100)
        y += a * (np.abs(np.sin(x * f * math.pi + ph) * 0.5 + np.sin(x * f * 2.3 + ph * 1.7) * 0.5))
        a *= rough
        f *= 2.1
    return base - y


def summit(seed=2701, figure=0):
    """Noon summit photo, portrait 144x272 (painted at 4x): sky, layered ridges with autumn colours, the summit sign."""
    S = 4
    W, H = 144 * S, 272 * S
    r = rng(seed)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    img = np.zeros((H, W, 3), np.float32)
    # sky: deep blue zenith to hazy horizon, sun glare upper right, a few cumulus
    t = yy / (H * 0.5)
    sky = np.stack([40 + 120 * t, 96 + 110 * t, 190 + 50 * t], -1)
    glare = np.exp(-(((xx - W * 0.86) / (W * 0.35)) ** 2 + ((yy - H * 0.02) / (H * 0.2)) ** 2))
    sky += glare[..., None] * np.array([70, 60, 40])
    cl = blur(r.random((H // 4, W // 4)), 3.5)
    cl = ndimage.zoom(cl, 4, order=1)[:H, :W]
    cl = (cl - 0.52) * 9
    cl *= np.clip(1 - np.abs(yy - H * 0.25) / (H * 0.17), 0, 1)
    cl = np.clip(cl, 0, 1)
    sky = sky * (1 - cl[..., None]) + np.array([238, 240, 244]) * cl[..., None]
    img[:] = sky
    # ridges far -> near (aerial perspective)
    layers = [(0.43, 18, (150, 168, 196), 0.0), (0.49, 26, (118, 140, 168), 0.0), (0.55, 34, (86, 104, 120), 0.25),
              (0.62, 44, (64, 76, 70), 0.6), (0.71, 56, (44, 52, 40), 1.0)]
    masks = []
    for i, (by, amp, col, autumn) in enumerate(layers):
        top = ridge(W, seed + 10 + i, by * H, amp * S)
        m = yy > top[None, :]
        n = blur(r.normal(0, 1, (H, W)), 2 + i)
        c = np.array(col, np.float32)[None, None, :] + n[..., None] * (4 + i * 3)
        if autumn:
            aut = blur(r.random((H // 8, W // 8)), 1.6)
            aut = ndimage.zoom(aut, 8, order=1)[:H, :W]
            aut = aut + blur(r.normal(0, 1, (H, W)), 1.5) * 0.02 > 0.5
            pal_ = np.array([[150, 70, 34], [170, 120, 40], [120, 44, 30], [60, 80, 40]], np.float32)
            pk = blur(r.random((H // 8, W // 8)), 1.2)
            pick = (ndimage.zoom((pk - pk.min()) / (np.ptp(pk) + 1e-6), 8, order=1)[:H, :W] * 3.99).astype(int)
            ac = pal_[pick] * (0.6 + 0.4 * (1 - i / 5))
            c = np.where(aut[..., None], c * (1 - autumn * 0.55) + ac * autumn * 0.55, c)
        # haze at the foot of each ridge
        haze = np.clip((yy - top[None, :]) / (H * 0.08), 0, 1)
        c = c * (0.85 + 0.15 * haze[..., None]) + np.array([200, 210, 225]) * (1 - haze[..., None]) * 0.12 * (5 - i) / 5
        img = np.where(m[..., None], c, img)
        masks.append((m, top))
    # cedar treeline on the 4th ridge (where something will stand)
    m4, top4 = masks[3]
    for k in range(26):
        tx = r.uniform(0, W * 0.3) if k % 2 else r.uniform(W * 0.72, W)
        ty = np.interp(tx, np.arange(W), top4) + r.uniform(-2, 6) * S
        th = r.uniform(5, 10) * S
        tri = (yy > ty - th) & (yy < ty + 2 * S) & (np.abs(xx - tx) < (yy - (ty - th)) * 0.28)
        img[tri] = np.array([28, 40, 32]) + r.normal(0, 4)
    # foreground: summit rocks, bamboo grass, the wooden summit sign
    fg_top = ridge(W, seed + 30, H * 0.80, 18 * S, octaves=4)
    fg = yy > fg_top[None, :]
    rock = blur(r.random((H // 4, W // 4)), 2.5)
    rock = ndimage.zoom(rock, 4, order=1)[:H, :W]
    depth = np.clip((yy - fg_top[None, :]) / (H * 0.2), 0, 1)
    rk = np.array([112, 108, 100]) * (0.8 + 0.3 * depth[..., None]) + blur(r.normal(0, 1, (H, W)), 1.5)[..., None] * 14
    gr = np.array([88, 112, 50]) * (0.75 + 0.35 * depth[..., None]) + blur(r.normal(0, 1, (H, W)), 1.0)[..., None] * 16
    fgc = np.where((rock > 0.47)[..., None], rk, gr)
    img = np.where(fg[..., None], fgc, img)
    # bamboo grass blades catching the noon light
    pim0 = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d0 = ImageDraw.Draw(pim0)
    for k in range(220):
        bx = r.uniform(0, W)
        by = np.interp(bx, np.arange(W), fg_top) + r.uniform(0, H * 0.2)
        if rock[min(H - 1, int(by)), min(W - 1, int(bx))] > 0.47:
            continue
        ln = r.uniform(4, 10) * S
        a_ = -math.pi / 2 + r.normal(0, 0.5)
        c_ = (int(120 + r.uniform(0, 50)), int(150 + r.uniform(0, 40)), int(60 + r.uniform(0, 30)))
        d0.line([(bx, by), (bx + math.cos(a_) * ln, by + math.sin(a_) * ln)], fill=c_, width=S)
    img = np.asarray(pim0, np.float32).copy()
    sx0, sx1 = int(W * 0.20), int(W * 0.27)
    post = (xx > sx0) & (xx < sx1) & (yy > H * 0.62) & (yy < H * 0.92)
    img[post] = np.array([118, 92, 62]) + blur(r.normal(0, 1, (H, W)), 1)[post][:, None] * 10
    board = (xx > W * 0.10) & (xx < W * 0.37) & (yy > H * 0.645) & (yy < H * 0.69)
    img[board] = np.array([150, 124, 88])
    pim = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(pim)
    f = font(FONT_SERIF_JP_BOLD, int(7.5 * S))
    d.text((W * 0.112, H * 0.649), '帰らず山', fill=(40, 30, 22), font=f)
    f2 = font(FONT_SERIF_JP_BOLD, int(5 * S))
    d.text((W * 0.30, H * 0.655), '山頂', fill=(40, 30, 22), font=f2)
    img = np.asarray(pim, np.float32).copy()
    # the figure
    if figure:
        img = draw_figure(img, figure, S, r)
    # phone-photo look: slight vignette, warm white balance, sharpening halo, sensor noise
    rad = np.hypot((xx - W / 2) / W, (yy - H / 2) / H)
    img *= (1 - rad[..., None] ** 2 * 0.5)
    img = img * np.array([1.03, 1.0, 0.97])
    out = np.zeros((H, W, 4), np.float32)
    out[..., :3] = np.clip(img, 0, 255)
    out[..., 3] = 255
    res = down(out, S)
    res[..., :3] += rng(seed + 99).normal(0, 2.2, res[..., :3].shape)
    return to_tex(np.clip(res, 0, 255))


def draw_figure(img, stage, S, r):
    H, W = img.shape[:2]
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    if stage == 1:
        # a pale figure among the distant cedars on the 4th ridge, small
        cx, base, hgt = W * 0.64, H * 0.545, 16 * S
    else:
        # much closer: standing just behind the summit sign, the face turned to the camera
        cx, base, hgt = W * 0.62, H * 0.86, 120 * S
    head_r = hgt * 0.085
    hy = base - hgt + head_r
    # body: a narrow pale column with sloping shoulders, slightly translucent
    body = (yy > hy + head_r * 0.6) & (yy < base) & (np.abs(xx - cx) < (head_r * 1.15 + (yy - hy) * 0.09))
    head = (xx - cx) ** 2 / (head_r * 0.82) ** 2 + (yy - hy) ** 2 / head_r ** 2 < 1
    hair = ((xx - cx) ** 2 / (head_r * 1.0) ** 2 + (yy - hy + head_r * 0.25) ** 2 / (head_r * 1.15) ** 2 < 1) & ~(
        ((xx - cx) ** 2 / (head_r * 0.6) ** 2 + (yy - hy - head_r * 0.15) ** 2 / (head_r * 0.85) ** 2) < 1)
    hair |= (np.abs(xx - cx) < head_r * 1.05) & (yy > hy) & (yy < hy + head_r * 3.2) & (np.abs(xx - cx) > head_r * 0.6)
    a = 0.9 if stage == 1 else 0.93
    pale = np.array([222, 222, 216])
    # soft side shading (noon sun from the upper right) and fold lines down the white robe
    sh = np.clip(0.72 + 0.28 * (xx - cx) / (head_r * 2.2), 0.55, 1.0)
    folds = 1.0 - 0.08 * (np.sin((xx - cx) / (head_r * 0.35)) > 0.6)
    bodyc = pale[None, None, :] * 0.86 * sh[..., None] * folds[..., None]
    img = img.copy()
    img[body] = img[body] * (1 - a) + bodyc[body] * a
    headc = pale[None, None, :] * np.clip(0.8 + 0.2 * (xx - cx) / head_r, 0.6, 1.05)[..., None]
    img[head] = img[head] * (1 - a) + headc[head] * a
    img[hair] = img[hair] * 0.15 + np.array([14, 14, 16]) * 0.85
    if stage == 2:
        # face: dark hollow eyes, a mouth line, looking straight into the lens
        for ex in (-0.32, 0.32):
            eye = (xx - (cx + ex * head_r)) ** 2 / (head_r * 0.17) ** 2 + (yy - (hy - head_r * 0.05)) ** 2 / (head_r * 0.13) ** 2 < 1
            img[eye] = np.array([10, 8, 8])
        mouth = (np.abs(xx - cx) < head_r * 0.35) & (np.abs(yy - (hy + head_r * 0.48)) < head_r * 0.05)
        img[mouth] = np.array([60, 34, 34])
        # the bare feet do not quite touch the ground
        img = img.copy()
    return img


@texture('gui/phone/wallpaper_0', size=(144, 272), opaque=True)
def wallpaper_0():
    return summit(figure=0)


@texture('gui/phone/wallpaper_1', size=(144, 272), opaque=True)
def wallpaper_1():
    return summit(figure=1)


@texture('gui/phone/wallpaper_2', size=(144, 272), opaque=True)
def wallpaper_2():
    return summit(figure=2)


# ================================================================================================ app icons
def icon_bg(S, top, bottom):
    W = 32 * S
    yy, xx = np.mgrid[0:W, 0:W].astype(np.float32)
    rad = 7 * S
    cx = np.clip(xx, rad, W - 1 - rad)
    cy = np.clip(yy, rad, W - 1 - rad)
    m = np.hypot(xx - cx, yy - cy) <= rad
    t = yy / W
    col = np.array(top, np.float32) * (1 - t[..., None]) + np.array(bottom, np.float32) * t[..., None]
    out = np.zeros((W, W, 4), np.float32)
    out[..., :3] = col
    out[..., 3] = m * 255.0
    return out, m


@texture('gui/phone/icons', size=(192, 32))
def phone_icons():
    S = 4
    W = 32 * S
    sheet = np.zeros((W, W * 6, 4), np.float32)
    specs = [('map', (84, 190, 140), (32, 120, 92)), ('camera', (110, 114, 122), (52, 54, 60)), ('light', (54, 62, 90), (18, 20, 34)),
             ('msg', (110, 220, 110), (40, 160, 60)), ('call', (110, 220, 120), (36, 150, 70)), ('memo', (250, 224, 110), (226, 180, 50))]
    for i, (kind, top, bot) in enumerate(specs):
        out, m = icon_bg(S, top, bot)
        im = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')
        d = ImageDraw.Draw(im)
        wht = (250, 250, 250, 255)
        if kind == 'map':
            d.polygon([(6 * S, 9 * S), (13 * S, 6 * S), (19 * S, 9 * S), (26 * S, 6 * S), (26 * S, 23 * S), (19 * S, 26 * S),
                       (13 * S, 23 * S), (6 * S, 26 * S)], fill=(236, 240, 232, 255))
            d.line([(13 * S, 6 * S), (13 * S, 23 * S)], fill=(180, 190, 180, 255), width=S)
            d.line([(19 * S, 9 * S), (19 * S, 26 * S)], fill=(180, 190, 180, 255), width=S)
            d.line([(8 * S, 20 * S), (12 * S, 15 * S), (17 * S, 17 * S), (23 * S, 11 * S)], fill=(220, 70, 50, 255), width=int(1.5 * S))
            d.ellipse([(20 * S, 7 * S), (26 * S, 13 * S)], fill=(230, 60, 50, 255))
            d.ellipse([(22 * S, 9 * S), (24 * S, 11 * S)], fill=wht)
        elif kind == 'camera':
            d.rounded_rectangle([(5 * S, 10 * S), (27 * S, 25 * S)], radius=3 * S, fill=(236, 236, 238, 255))
            d.rectangle([(11 * S, 7 * S), (18 * S, 10 * S)], fill=(236, 236, 238, 255))
            d.ellipse([(10 * S, 11 * S), (22 * S, 23 * S)], fill=(40, 42, 48, 255))
            d.ellipse([(12.5 * S, 13.5 * S), (19.5 * S, 20.5 * S)], fill=(70, 90, 120, 255))
            d.ellipse([(14 * S, 14.5 * S), (16 * S, 16.5 * S)], fill=(200, 220, 255, 255))
            d.ellipse([(23 * S, 12 * S), (25 * S, 14 * S)], fill=(230, 60, 50, 255))
        elif kind == 'light':
            d.polygon([(16 * S, 15 * S), (6 * S, 5 * S), (26 * S, 5 * S)], fill=(255, 236, 150, 120))
            d.rounded_rectangle([(12 * S, 14 * S), (20 * S, 28 * S)], radius=2 * S, fill=(230, 232, 236, 255))
            d.rectangle([(11 * S, 11 * S), (21 * S, 15 * S)], fill=(250, 250, 252, 255))
            d.ellipse([(14.5 * S, 19 * S), (17.5 * S, 22 * S)], fill=(60, 64, 80, 255))
        elif kind == 'msg':
            d.ellipse([(5 * S, 7 * S), (27 * S, 24 * S)], fill=wht)
            d.polygon([(9 * S, 20 * S), (7 * S, 27 * S), (14 * S, 22 * S)], fill=wht)
        elif kind == 'call':
            hs = [(9, 6), (13, 6), (15, 11), (12.5, 13.5), (18.5, 19.5), (21, 17), (26, 19), (26, 23), (22, 26), (17, 25),
                  (11, 21), (7, 15), (6, 10)]
            d.polygon([(x * S, y * S) for x, y in hs], fill=wht)
        elif kind == 'memo':
            for k, yv in enumerate((11, 16, 21, 26)):
                d.line([(7 * S, yv * S), ((25 - (8 if k == 3 else 0)) * S, yv * S)], fill=(150, 120, 40, 255), width=int(1.2 * S))
            d.rectangle([(0, 0), (W, 6 * S)], fill=(230, 150, 40, 255))
        arr = np.asarray(im, np.float32).copy()
        arr[..., 3] = np.minimum(arr[..., 3], m * 255.0)
        # glossy top highlight, faint grime
        yy = np.arange(W)[:, None]
        arr[..., :3] += (np.clip(1 - yy / (W * 0.45), 0, 1) * 18)[..., None]
        sheet[:, i * W:(i + 1) * W] = arr
    return to_tex(np.clip(down(sheet, S), 0, 255))
