import sys, os
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image
from common import TEX_ROOT, upscale
import preview
def load(n): return np.asarray(Image.open(f'{TEX_ROOT}/{n}.png').convert('RGBA'), np.float32)
items = []
for d in sys.argv[2:]:
    for fl in ['block/tatami_top', 'block/old_planks', 'block/engawa']:
        base = load(fl).copy(); dec = load(d)
        a = dec[..., 3:4] / 255
        base[..., :3] = base[..., :3] * (1 - a) + dec[..., :3] * a
        items.append((d.split('/')[-1] + '@' + fl.split('/')[-1][:6], base))
preview.contact_sheet(items, sys.argv[1], cell=192, cols=6)
