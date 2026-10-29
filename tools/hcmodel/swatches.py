"""Material swatch sheet: paints one face per material at HD and writes tools/preview/swatches.png.

    python3 tools/hcmodel/swatches.py [material ...] [--detail 1.0] [--px 24]
"""
import os
import sys

TOOLS = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, TOOLS)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from hcmodel import paint as P  # noqa: E402
from hcmodel.model import Model  # noqa: E402

DEFAULT = ['ign_scale', 'ign_plate', 'ign_belly', 'frost_fur', 'boar_fur', 'ign_membrane', 'horn_dark', 'horn_ivory',
           'bone', 'grav_rock', 'lava_rock', 'ice_crystal', 'grav_hide', 'volt_organ', 'metal', 'gold', 'cloth_red', 'wood',
           'teeth', 'mouth']


def swatch(name, px=24, detail=1.0, K=4):
    m = Model('sw_' + name)
    b = m.bone('b', (0, 24 - px, 0))
    b.cube((-px / 2, 0, -px / 2), (px, px, px), name)
    m.hd_detail = detail
    m.pack()
    img, glow = P.paint_model(m, texel=K)
    c = list(m.all_cubes())[0]
    u, v, w, h = c.face_rects()['north']
    face = img.crop((u * K, v * K, (u + w) * K, (v + h) * K))
    if glow is not None:
        g = glow.crop((u * K, v * K, (u + w) * K, (v + h) * K))
        fa = np.asarray(face).astype(int)
        ga = np.asarray(g).astype(int)
        lit = ga[..., 3] > 0
        fa[lit, :3] = np.clip(fa[lit, :3] + ga[lit, :3] // 2, 0, 255)
        face = Image.fromarray(fa.astype(np.uint8), 'RGBA')
    return face


def main():
    args = sys.argv[1:]
    names = [a for a in args if not a.startswith('--') and not a.replace('.', '').isdigit()]
    detail = float(args[args.index('--detail') + 1]) if '--detail' in args else 1.0
    px = int(args[args.index('--px') + 1]) if '--px' in args else 24
    names = names or DEFAULT
    zoom = 3
    cell = px * 4 * zoom
    cols = 5
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * (cell + 8), rows * (cell + 20)), (30, 32, 36))
    d = ImageDraw.Draw(sheet)
    for k, n in enumerate(names):
        if n not in P.MATERIALS:
            print('unknown material', n)
            continue
        face = swatch(n, px, detail)
        big = face.resize((cell, cell), Image.NEAREST)
        x, y = (k % cols) * (cell + 8), (k // cols) * (cell + 20)
        sheet.paste(big, (x + 4, y + 16), big)
        d.text((x + 4, y + 2), '%s (%s)' % (n, P.MATERIALS[n].pattern), fill=(230, 230, 230))
    out = os.path.join(TOOLS, 'preview', 'swatches.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print('wrote', out)


if __name__ == '__main__':
    main()
