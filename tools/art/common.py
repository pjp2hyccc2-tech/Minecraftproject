"""Shared pixel-art helpers for the Satsujinki texture generators (numpy RGBA arrays, PIL for text)."""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'satsujinki', 'textures')

FONT_SERIF = '/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc'
FONT_SANS_BLACK = '/usr/share/fonts/opentype/noto/NotoSansCJK-Black.ttc'
FONT_DEJAVU_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'


def font(path, size, index=0):
    try:
        return ImageFont.truetype(path, size, index=index)
    except Exception:
        return ImageFont.truetype(FONT_DEJAVU_BOLD, size)


def jp_font(size, black=False):
    # index 0 in the CJK collections is JP for the Noto *CJK* ttc files
    return font(FONT_SANS_BLACK if black else FONT_SERIF, size, 0)


def canvas(w, h, c=(0, 0, 0, 0)):
    a = np.zeros((h, w, 4), dtype=np.float32)
    a[..., 0], a[..., 1], a[..., 2], a[..., 3] = c[0], c[1], c[2], c[3] if len(c) > 3 else 255
    return a


def save(arr, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = arr if isinstance(arr, Image.Image) else Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    img.save(path)
    return path


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def vnoise(w, h, scale, seed):
    r = np.random.RandomState(seed)
    gw, gh = int(w / scale) + 3, int(h / scale) + 3
    g = r.rand(gh, gw)
    ys = np.arange(h) / scale
    xs = np.arange(w) / scale
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def fbm(w, h, seed, base=4.0, octaves=3):
    out = np.zeros((h, w))
    amp, tot, s = 1.0, 0.0, base
    for o in range(octaves):
        out += vnoise(w, h, max(0.6, s), seed + o * 101) * amp
        tot += amp
        amp *= 0.5
        s /= 2.0
    return out / tot


def fill(a, x, y, w, h, c):
    a[y:y + h, x:x + w, 0] = c[0]
    a[y:y + h, x:x + w, 1] = c[1]
    a[y:y + h, x:x + w, 2] = c[2]
    a[y:y + h, x:x + w, 3] = c[3] if len(c) > 3 else 255


def px(a, x, y, c):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        a[y, x, 0], a[y, x, 1], a[y, x, 2] = c[0], c[1], c[2]
        a[y, x, 3] = c[3] if len(c) > 3 else 255


def shade(c, k):
    return (max(0, min(255, c[0] * k)), max(0, min(255, c[1] * k)), max(0, min(255, c[2] * k)), c[3] if len(c) > 3 else 255)


def mix(c1, c2, t):
    return tuple(c1[i] + (c2[i] - c1[i]) * t for i in range(3)) + (c1[3] if len(c1) > 3 else 255,)


def textured(a, x, y, w, h, c, seed, amount=0.18, scale=2.0):
    """Fill a rect with a colour modulated by noise (pixel-art material look)."""
    n = fbm(w, h, seed, base=scale, octaves=2)
    for j in range(h):
        for i in range(w):
            k = 1.0 + (n[j, i] - 0.5) * 2 * amount
            px(a, x + i, y + j, shade(c, k))


def blend_px(a, x, y, c, alpha):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        for i in range(3):
            a[y, x, i] = a[y, x, i] * (1 - alpha) + c[i] * alpha
        a[y, x, 3] = max(a[y, x, 3], 255 * alpha if len(c) < 4 else c[3] * alpha)


def blood_color(r, fresh=True):
    if fresh:
        base = [(140, 8, 10), (165, 12, 14), (120, 4, 8), (185, 22, 20), (100, 2, 6)]
    else:
        base = [(70, 10, 8), (58, 6, 6), (84, 14, 10), (48, 5, 5)]
    c = base[r.randrange(len(base))]
    return (c[0], c[1], c[2], 255)


def splat(a, cx, cy, radius, r, fresh=True, mask=None, drips=0, binary=True):
    """Irregular blood splat with satellites and optional drips (downwards)."""
    h, w = a.shape[:2]
    col = blood_color(r, fresh)
    for j in range(int(cy - radius - 2), int(cy + radius + 3)):
        for i in range(int(cx - radius - 2), int(cx + radius + 3)):
            if not (0 <= i < w and 0 <= j < h):
                continue
            if mask is not None and not mask[j, i]:
                continue
            d = math.hypot(i - cx, j - cy)
            wob = radius * (0.75 + 0.35 * math.sin(math.atan2(j - cy, i - cx) * 5 + r.random() * 0.6))
            if d <= wob:
                k = 0.85 + 0.3 * r.random()
                if d < wob * 0.45 and fresh:
                    k += 0.15
                px(a, i, j, shade(col, k))
    for s in range(int(radius * 1.5) + 1):
        ang = r.random() * math.tau
        dist = radius * (1.1 + r.random() * 1.2)
        sx, sy = int(cx + math.cos(ang) * dist), int(cy + math.sin(ang) * dist)
        if 0 <= sx < w and 0 <= sy < h and (mask is None or mask[sy, sx]):
            px(a, sx, sy, shade(col, 0.9 + 0.2 * r.random()))
    for d in range(drips):
        dx = int(cx + (r.random() - 0.5) * radius * 1.4)
        length = int(radius * (1.0 + r.random() * 2.5))
        for t in range(length):
            yy = int(cy + t)
            if 0 <= dx < w and 0 <= yy < h and (mask is None or mask[yy, dx]):
                px(a, dx, yy, shade(col, 0.95 - t * 0.02))
        yy = int(cy + length)
        if 0 <= dx < w and 0 <= yy < h and (mask is None or mask[yy, dx]):
            px(a, dx, yy, shade(col, 1.15))


def to_image(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')


def from_image(img):
    return np.array(img.convert('RGBA')).astype(np.float32)


def box_uv(u, v, w, h, d):
    """Minecraft ModelPart cube UV rects: dict face -> (x, y, w, h).
    For entity models (rendered flipped) 'down' is the visual TOP; for block entities it is the bottom."""
    return {
        'down': (u + d, v, w, d),
        'up': (u + d + w, v, w, d),
        'west': (u, v + d, d, h),
        'north': (u + d, v + d, w, h),
        'east': (u + d + w, v + d, d, h),
        'south': (u + d + w + d, v + d, w, h),
    }


def rough_text(text, size, fnt, fill_rgb, w, h, seed=1, stroke=0, stroke_rgb=(0, 0, 0)):
    """Render text into an RGBA image (w x h), centred."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bbox = d.textbbox((0, 0), text, font=fnt, stroke_width=stroke)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text(((w - tw) / 2 - bbox[0], (h - th) / 2 - bbox[1]), text, font=fnt, fill=fill_rgb + (255,),
           stroke_width=stroke, stroke_fill=stroke_rgb + (255,))
    return img
