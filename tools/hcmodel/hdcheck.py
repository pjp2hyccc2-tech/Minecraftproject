"""HD texture check: paints a model script's model and renders close-ups.

    python3 tools/hcmodel/hdcheck.py tools/models/ignivald.py [--texel 4] [--export] [--views ...]

Writes tools/preview/<name>_hd.png (full + zoomed views, emissive layer added) and prints texture size,
glow ratio and timing.  With --export the textures (+ _glow, _n, _s) are written to the assets folder as the
model script's own export would do (the JSON is not touched).
"""
import importlib.util
import os
import sys
import time

TOOLS = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
if TOOLS not in sys.path:
    sys.path.insert(0, TOOLS)

import numpy as np  # noqa: E402

from hcmodel import paint as painter  # noqa: E402
from hcmodel.render import closeup  # noqa: E402


def load_model(path):
    """Imports the model script as a module without running its __main__ block; returns its Model `m`."""
    name = os.path.splitext(os.path.basename(path))[0]
    sys.path.insert(0, os.path.dirname(os.path.abspath(path)))
    sys.path.insert(0, os.path.join(TOOLS, 'small'))
    spec = importlib.util.spec_from_file_location('hcmodel_script_' + name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.m, mod


def glow_ratio(img, glow):
    if glow is None:
        return 0.0
    g = np.asarray(glow.convert('RGBA'))
    b = np.asarray(img.convert('RGBA'))
    lit = ((g[..., 3] > 0) & (g[..., :3].sum(-1) > 0)).sum()
    return lit / max(1, (b[..., 3] > 0).sum())


def main():
    args = sys.argv[1:]
    path = [a for a in args if a.endswith('.py')][0]
    if '--texel' in args:
        painter.TEXEL = int(args[args.index('--texel') + 1])
    m, mod = load_model(path)
    if any(c.uv is None for c in m.all_cubes()):
        m.pack()
    t0 = time.time()
    if hasattr(m, 'post_passes') or hasattr(m, 'final_passes') or hasattr(mod, 'run'):
        import smallkit
        img, glow = smallkit.paint(m)   # smallkit scripts: post passes (stamps / accents)
    else:
        img, glow = painter.paint_model(m)
    dt = time.time() - t0
    print('%s: %dx%d (texel %d) painted in %.1fs, glow ratio %.3f' % (m.name, img.size[0], img.size[1], painter.TEXEL, dt, glow_ratio(img, glow)))
    out = os.path.join(TOOLS, 'preview', m.name + '_hd.png')
    closeup(m, img, out, size=int(args[args.index('--size') + 1]) if '--size' in args else 640, glow=glow)
    print('wrote', out)
    if '--export' in args:
        root = os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'huntcraft', 'textures', 'entity')
        img.save(os.path.join(root, m.name + '.png'))
        gp = os.path.join(root, m.name + '_glow.png')
        if glow is not None:
            glow.save(gp)
        elif os.path.exists(gp):
            os.remove(gp)
        painter.save_pbr(m, os.path.join(root, m.name))


if __name__ == '__main__':
    main()
