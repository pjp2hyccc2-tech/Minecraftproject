#!/usr/bin/env python3
"""Dev viewer for vanilla-style door textures: top over bottom, 8x, on a checker (and the item sprites)."""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw
import gen_textures as G  # noqa
import preview
from common import upscale, font, FONT_LABEL

out = sys.argv[1]
doors = sys.argv[2:]
k = 8
cells = []
for d in doors:
    top = G.render('block/' + d + '_top').astype(np.float32)
    bot = G.render('block/' + d + '_bottom').astype(np.float32)
    arr = np.concatenate([top, bot], 0)
    big = upscale(arr, k)
    img = preview.over(preview.checker(big.shape[1], big.shape[0], 8), big)
    if 'item/' + d in G.REGISTRY:
        it = upscale(G.render('item/' + d).astype(np.float32), 4)
        img2 = preview.over(preview.checker(it.shape[1], it.shape[0], 8), it)
    else:
        img2 = np.zeros((64, 64, 3))
    cells.append((d, img, img2))
W = len(cells) * (16 * k + 80) + 10
sheet = Image.new('RGB', (W, 32 * k + 30), (18, 16, 18))
dr = ImageDraw.Draw(sheet)
x = 6
for d, img, img2 in cells:
    sheet.paste(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), (x, 6))
    sheet.paste(Image.fromarray(np.clip(img2, 0, 255).astype(np.uint8)), (x + 16 * k + 6, 6))
    dr.text((x, 32 * k + 10), d, fill=(220, 210, 200), font=font(FONT_LABEL, 11))
    x += 16 * k + 80
sheet.save(out)
print(out)
