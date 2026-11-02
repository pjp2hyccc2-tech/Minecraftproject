"""Contact sheets and tiling previews (not shipped)."""
import os

import numpy as np
from PIL import Image, ImageDraw

from common import PREVIEW, FONT_LABEL, FONT_SERIF_JP_BOLD, font, upscale


def checker(w, h, s=8, c1=(34, 30, 34), c2=(46, 42, 46)):
    y, x = np.mgrid[0:h, 0:w]
    m = ((x // s + y // s) % 2).astype(bool)
    out = np.zeros((h, w, 3), np.float32)
    out[m] = c2
    out[~m] = c1
    return out


def over(bg, rgba):
    a = rgba[..., 3:4] / 255.0
    return bg * (1 - a) + rgba[..., :3] * a


def cell_image(arr, cell, night=False):
    """Fit arr (H,W,4 uint8/float) into a square cell using an integer nearest upscale."""
    h, w = arr.shape[:2]
    if w > cell or h > cell:
        s = max((w + cell - 1) // cell, (h + cell - 1) // cell)
        im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
        arr = np.asarray(im.resize((w // s, h // s), Image.BOX), np.float32)
        h, w = arr.shape[:2]
    k = max(1, min(cell // w, cell // h))
    big = upscale(arr.astype(np.float32), k)
    if night:
        big = big.copy()
        big[..., :3] *= np.array([0.62, 0.55, 0.50])
    bg = checker(big.shape[1], big.shape[0], max(4, k * 2))
    return over(bg, big)


def contact_sheet(items, out_path, cell=128, cols=8, night=False, title=None):
    """items: list of (label, array). Writes a labelled sheet."""
    f = font(FONT_LABEL, 11)
    pad, lab = 6, 14
    rows = (len(items) + cols - 1) // cols
    top = 20 if title else 0
    W = cols * (cell + pad) + pad
    H = top + rows * (cell + lab + pad) + pad
    sheet = Image.new('RGB', (W, H), (18, 16, 18))
    d = ImageDraw.Draw(sheet)
    if title:
        d.text((pad, 1), title, fill=(220, 210, 200), font=font(FONT_SERIF_JP_BOLD, 13))
    for i, (label, arr) in enumerate(items):
        r, c = divmod(i, cols)
        x = pad + c * (cell + pad)
        y = top + pad + r * (cell + lab + pad)
        img = cell_image(np.asarray(arr), cell, night)
        im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
        ox = x + (cell - im.width) // 2
        oy = y + (cell - im.height) // 2
        sheet.paste(im, (ox, oy))
        d.text((x, y + cell + 1), label[:20], fill=(200, 190, 180), font=f)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return out_path


def tile_preview(items, out_path, reps=3, k=6, cols=4, night=False):
    """3x3 tiled previews to check seams/repetition."""
    f = font(FONT_LABEL, 11)
    tiles = []
    for label, arr in items:
        arr = np.asarray(arr, np.float32)
        t = np.tile(arr, (reps, reps, 1))
        t = upscale(t, k)
        if night:
            t = t.copy()
            t[..., :3] *= np.array([0.62, 0.55, 0.50])
        bg = checker(t.shape[1], t.shape[0], 8)
        tiles.append((label, over(bg, t)))
    cw = max(t.shape[1] for _, t in tiles)
    ch = max(t.shape[0] for _, t in tiles)
    pad, lab = 8, 14
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * (cw + pad) + pad, rows * (ch + lab + pad) + pad), (18, 16, 18))
    d = ImageDraw.Draw(sheet)
    for i, (label, t) in enumerate(tiles):
        r, c = divmod(i, cols)
        x = pad + c * (cw + pad)
        y = pad + r * (ch + lab + pad)
        sheet.paste(Image.fromarray(np.clip(t, 0, 255).astype(np.uint8), 'RGB'), (x, y))
        d.text((x, y + ch + 1), label, fill=(200, 190, 180), font=f)
    sheet.save(out_path)
    return out_path


def strip_frames(arr, fw=16, fh=16):
    n = arr.shape[0] // fh
    return [arr[i * fh:(i + 1) * fh] for i in range(n)]
