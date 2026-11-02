"""Quick zoom viewer: python3 zoom.py out.png name1 name2 ... (names relative to textures/)."""
import sys, os
sys.dont_write_bytecode = True
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import TEX_ROOT
import preview
out = sys.argv[1]
cell = int(os.environ.get('CELL', '256'))
cols = int(os.environ.get('COLS', '4'))
night = bool(os.environ.get('NIGHT'))
items = []
for n in sys.argv[2:]:
    a = np.asarray(Image.open(os.path.join(TEX_ROOT, n + '.png')).convert('RGBA'), np.float32)
    if a.shape[0] > a.shape[1] * 2 and a.shape[1] == 16 and os.environ.get('FRAMES'):
        for i in range(a.shape[0] // 16):
            items.append((f'{n.split("/")[-1]}{i}', a[i*16:(i+1)*16]))
    else:
        items.append((n.split('/')[-1], a))
preview.contact_sheet(items, out, cell=cell, cols=cols, night=night)
