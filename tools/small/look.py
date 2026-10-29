"""Quick look at a model from arbitrary angles (iteration helper, no export).

usage (from tools/):  python3 small/look.py <model_id> [yaw,pitch ...] [--anim name t ...] [--size N] [--out file]
  * views default to: top, front, right side, front-right above
  * --anim name t   pose (idle layered under actions); repeat for several poses (one row each)
  * negative pitch looks from above
writes tools/preview/look.png
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from smallkit import paint, render_glow, label, PREVIEW_DIR  # noqa: E402
from PIL import Image  # noqa: E402

STATE = ('idle', 'walk', 'run', 'fly', 'death', 'sleep', 'stun', 'trapped')


def load(model_id):
    path = os.path.join(HERE, '..', 'models', model_id + '.py')
    spec = importlib.util.spec_from_file_location('mdl_' + model_id, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.m


def main(argv):
    mid = argv[0]
    views, poses, size, out = [], [], 300, os.path.join(PREVIEW_DIR, 'look.png')
    i = 1
    while i < len(argv):
        a = argv[i]
        if a == '--anim':
            poses.append((argv[i + 1], float(argv[i + 2])))
            i += 3
            continue
        if a == '--size':
            size = int(argv[i + 1])
            i += 2
            continue
        if a == '--out':
            out = argv[i + 1]
            i += 2
            continue
        y, p = a.split(',')
        views.append((float(y), float(p)))
        i += 1
    if not views:
        views = [(0, -89), (0, -6), (90, -4), (35, -20)]
    m = load(mid)
    if any(c.uv is None for c in m.all_cubes()):
        m.pack()
    tex, glow = paint(m)
    rows = []
    for an, t in (poses or [(None, 0.0)]):
        layers = []
        if an is not None:
            a = m.animations[an]
            if an not in STATE and an not in getattr(m, 'state_poses', ()) and 'idle' in m.animations:
                layers.append((m.animations['idle'], t, 1.0))
            layers.append((a, t, 1.0))
        elif 'idle' in m.animations:
            layers.append((m.animations['idle'], 0.0, 1.0))
        row = []
        for y, p in views:
            im, _ = render_glow(m, tex, glow, layers, size, y, p)
            row.append(label(im, '%s %g,%g' % (an or 'rest', y, p) + ('' if an is None else ' t=%.2f' % t)))
        rows.append(row)
    sheet = Image.new('RGB', (len(views) * size, len(rows) * size), (20, 22, 26))
    for r, row in enumerate(rows):
        for c, im in enumerate(row):
            sheet.paste(im, (c * size, r * size))
    sheet.save(out)
    print('wrote', out)


if __name__ == '__main__':
    main(sys.argv[1:])
