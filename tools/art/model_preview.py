#!/usr/bin/env python3
"""Renders every prop block model (and the held item models in first / third person) to contact sheets.

usage: python3 tools/art/model_preview.py [name ...]   -> tools/art/preview/props.png, held_items.png
"""
import json
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image, ImageDraw

import gen_textures as G  # noqa: E402
import render3d as R  # noqa: E402
from common import PREVIEW, MODEL_ROOT, font, FONT_LABEL  # noqa: E402

_TEX = {}


def tex_lookup(ref):
    name = ref.split(':', 1)[1] if ':' in ref else ref
    if name not in _TEX:
        if name in G.REGISTRY:
            _TEX[name] = G.render(name).astype(np.float64)
        else:
            from PIL import Image as I
            p = os.path.join(G.TEX_ROOT, name + '.png')
            _TEX[name] = np.asarray(I.open(p).convert('RGBA'), np.float64)
    return _TEX[name]


def load_model(kind, name):
    with open(os.path.join(MODEL_ROOT, kind, name + '.json'), encoding='utf-8') as f:
        return json.load(f)


def render_block(model, yaw=-35.0, pitch=28.0, size=256, ground=True, bg=(46, 48, 54)):
    c = np.array([0.5, 0.5, 0.5])
    d = 3.0
    eye = c + d * np.array([math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)),
                            math.sin(math.radians(pitch)),
                            -math.cos(math.radians(yaw)) * math.cos(math.radians(pitch))])
    cv = R.Canvas(size, size, R.look_at(eye, c), ortho=0.95, bg=bg)
    if ground:
        g = tex_lookup('reisatsu:block/forest_floor_top')
        for gx in (-1, 0, 1):
            for gz in (-1, 0, 1):
                pts = np.array([(gx, 0, gz), (gx + 1, 0, gz), (gx + 1, 0, gz + 1), (gx, 0, gz + 1)], float)
                cv.quad(pts, R._uv_corners([0, 0, 16, 16]), g * np.array([0.55, 0.55, 0.55, 1.0]), shade=1.0)
    R.draw_block_model(cv, model, tex_lookup)
    cv.finish()
    return np.clip(cv.color, 0, 255)


def sheet(items, out, cols=6, cell=256):
    f = font(FONT_LABEL, 12)
    rows = (len(items) + cols - 1) // cols
    W, H = cols * (cell + 6) + 6, rows * (cell + 20) + 6
    img = Image.new('RGB', (W, H), (18, 16, 18))
    d = ImageDraw.Draw(img)
    for i, (label, arr) in enumerate(items):
        r, c = divmod(i, cols)
        x, y = 6 + c * (cell + 6), 6 + r * (cell + 20)
        img.paste(Image.fromarray(arr.astype(np.uint8)), (x, y))
        d.text((x, y + cell + 2), label, fill=(220, 210, 200), font=f)
    img.save(out)
    return out


PROPS = ['lost_bag', 'lost_bag_open', 'memo', 'memo_wall', 'logbook', 'notice_board', 'signpost', 'jizo', 'hokora',
         'hokora_offered', 'wara_ningyo', 'rope_anchor', 'rope_anchor_roped', 'climbing_rope', 'public_phone',
         'bus_stop', 'tunnel_lamp', 'tunnel_lamp_lit', 'guardrail', 'key_hook', 'key_hook_empty', 'car_door',
         'blue_tarp', 'fallen_leaves']


def props(names=None):
    items = []
    for n in (names or PROPS):
        m = load_model('block', n)
        items.append((n, render_block(m)))
        items.append((n + ' (back)', render_block(m, yaw=145, pitch=22)))
    return sheet(items, os.path.join(PREVIEW, 'props.png'), cols=6, cell=256)


def main():
    names = [a for a in sys.argv[1:] if not a.startswith('-')]
    print(props(names or None))
    try:
        import held_preview
        print(held_preview.main())
    except ImportError:
        pass


if __name__ == '__main__':
    main()
