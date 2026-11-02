#!/usr/bin/env python3
"""Dev viewer: python3 tools/art/look.py out.png block/cedar_log block/forest_floor_top ...

For each texture: an 8x single view and a 3x3 tiled view (4x), optionally night-tinted (NIGHT=1).
Textures are rendered fresh from the generators (no need to run gen_textures first)."""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image, ImageDraw

import gen_textures as G  # noqa: E402  (registers everything)
from common import upscale, font, FONT_LABEL  # noqa: E402
import preview  # noqa: E402


def main():
    out = sys.argv[1]
    names = sys.argv[2:]
    night = bool(os.environ.get('NIGHT'))
    reps = int(os.environ.get('REPS', '3'))
    tiles = []
    for n in names:
        if '*' in n:
            import fnmatch
            tiles += [m for m in sorted(G.REGISTRY) if fnmatch.fnmatch(m, n)]
        else:
            tiles.append(n)
    cells = []
    for n in tiles:
        arr = G.render(n).astype(np.float32)
        h, w = arr.shape[:2]
        k1 = max(1, 128 // max(w, h))
        single = preview.cell_image(arr, max(w, h) * k1, night)
        tk = max(1, 192 // (max(w, h) * reps)) if max(w, h) <= 64 else 1
        tiled = np.tile(arr, (reps, reps, 1))
        tiled = upscale(tiled, tk)
        if night:
            tiled[..., :3] *= np.array([0.62, 0.55, 0.50])
        tiled = preview.over(preview.checker(tiled.shape[1], tiled.shape[0], 8), tiled)
        cells.append((n, single, tiled))
    pad = 8
    W = sum(max(s.shape[1], t.shape[1]) + pad for _, s, t in cells[:6]) + pad
    rows = [cells[i:i + 6] for i in range(0, len(cells), 6)]
    H = 0
    for r in rows:
        H += max(s.shape[0] + t.shape[0] + 30 for _, s, t in r) + pad
    sheet = Image.new('RGB', (max(W, 200), H + pad), (18, 16, 18))
    d = ImageDraw.Draw(sheet)
    f = font(FONT_LABEL, 11)
    y = pad
    for r in rows:
        x = pad
        rh = 0
        for n, s, t in r:
            sheet.paste(Image.fromarray(np.clip(s, 0, 255).astype(np.uint8)), (x, y))
            sheet.paste(Image.fromarray(np.clip(t, 0, 255).astype(np.uint8)), (x, y + s.shape[0] + 14))
            d.text((x, y + s.shape[0] + 1), n.split('/')[-1][:26], fill=(220, 210, 200), font=f)
            x += max(s.shape[1], t.shape[1]) + pad
            rh = max(rh, s.shape[0] + t.shape[0] + 30)
        y += rh + pad
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
