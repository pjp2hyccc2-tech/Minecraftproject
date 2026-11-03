#!/usr/bin/env python3
"""Dev viewer: python3 atlas_view.py out.png SCALE name [name ...] -> the textures side by side at an integer scale
(rendered fresh from the generators; first frame of animations unless FRAMES=1)."""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw
import gen_textures as G  # noqa
import preview
from common import upscale, font, FONT_LABEL

out, k = sys.argv[1], int(sys.argv[2])
arrs = []
for n in sys.argv[3:]:
    a = G.render(n).astype(np.float32)
    spec = G.REGISTRY[n]
    if spec['frames'] > 1 and not os.environ.get('FRAMES'):
        a = a[:a.shape[0] // spec['frames']]
    big = upscale(a, k)
    arrs.append((n, preview.over(preview.checker(big.shape[1], big.shape[0], k), big)))
W = sum(a.shape[1] + 10 for _, a in arrs) + 10
H = max(a.shape[0] for _, a in arrs) + 30
img = Image.new('RGB', (W, H), (18, 16, 18))
d = ImageDraw.Draw(img)
x = 10
for n, a in arrs:
    img.paste(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), (x, 10))
    d.text((x, a.shape[0] + 14), n.split('/')[-1], fill=(220, 210, 200), font=font(FONT_LABEL, 11))
    x += a.shape[1] + 10
img.save(out)
print(out, img.size)
