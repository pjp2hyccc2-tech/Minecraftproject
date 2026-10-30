"""Top-down previews of the built world (for development)."""
import os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
import numpy as np
from PIL import Image, ImageDraw
import wb

COL = [('water', (52, 110, 190)), ('rice_plant', (110, 170, 60)), ('paddy', (90, 70, 50)), ('grass_block', (98, 150, 70)), ('leaves', (50, 110, 40)),
       ('asphalt', (60, 62, 66)), ('road_', (220, 220, 210)), ('crosswalk', (200, 200, 200)), ('sidewalk', (170, 150, 140)), ('kawara', (85, 92, 104)),
       ('green_kawara', (50, 130, 95)), ('track', (120, 110, 100)), ('ballast', (140, 136, 130)), ('platform', (190, 190, 182)), ('shikkui', (236, 232, 222)),
       ('renga', (150, 60, 45)), ('bricks', (160, 80, 60)), ('red_steel', (220, 40, 30)), ('white', (240, 240, 236)), ('sand', (220, 210, 160)),
       ('gravel', (150, 145, 140)), ('stone', (128, 128, 128)), ('farmland', (110, 80, 50)), ('crop', (120, 180, 70)), ('window', (90, 110, 140)),
       ('office', (180, 178, 170)), ('concrete', (170, 170, 170)), ('tile', (210, 200, 170)), ('hinoki', (215, 185, 140)), ('dirt', (130, 100, 70)),
       ('log', (100, 80, 50)), ('bamboo', (130, 170, 60)), ('planks', (160, 130, 90)), ('glass', (160, 200, 220)), ('deepslate', (60, 60, 70)),
       ('shu', (200, 50, 30)), ('terracotta', (180, 120, 100)), ('mud', (100, 80, 60))]

def color(state):
    state = state.replace('waterlogged=false', '').replace('waterlogged=true', '')
    for k, c in COL:
        if k in state:
            return c
    return (200, 120, 200)

def render(w, x1, z1, x2, z2, scale=1, path='/tmp/prev/map.png'):
    W, H = (x2 - x1) // scale, (z2 - z1) // scale
    img = np.zeros((H, W, 3), dtype=np.uint8)
    cache = {}
    for j in range(H):
        for i in range(W):
            x, z = x1 + i * scale, z1 + j * scale
            a = w.chunks.get((x >> 4, z >> 4))
            if a is None:
                img[j, i] = (30, 30, 30)
                continue
            col = a[z & 15, x & 15]
            nz = np.flatnonzero(col)
            if not len(nz):
                continue
            top = nz[-1]
            st = w.pal[col[top]]
            c = cache.get(st)
            if c is None:
                c = cache[st] = color(st)
            shade = 0.75 + 0.25 * min(1.0, max(0.0, (top + wb.YMIN - 50) / 60))
            img[j, i] = [int(v * shade) for v in c]
    Image.fromarray(img).save(path)
