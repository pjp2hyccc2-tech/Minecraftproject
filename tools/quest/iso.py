"""Tiny isometric renderer for checking quest-map landmarks (terrain columns + voxels) in a box."""
import numpy as np
from PIL import Image, ImageDraw

from qmap import color_of, FLUID_WATER, FLUID_LAVA, FLUID_FROZEN, COLORS


def render_box(m, u0, v0, u1, v1, ymin=None, ymax=None, s=6, path=None, rot=0):
    """Isometric view of the box (u0..u1, v0..v1) seen from the south-east (rot=0) or other quadrants."""
    blocks = {}
    for v in range(v0, v1 + 1):
        for u in range(u0, u1 + 1):
            h = int(m.hi[v, u])
            col = color_of(m.spal.name(m.top[v, u]))
            sub = color_of(m.spal.name(m.sub[v, u]))
            lo = h - 4 if ymin is None else max(ymin, h - 12)
            for y in range(lo, h + 1):
                if m.clo[v, u] > 0 and m.clo[v, u] <= y <= m.chi[v, u]:
                    continue
                blocks[(u, y, v)] = col if y == h else sub
            if m.fluid[v, u]:
                fc = COLORS['lava'] if m.fluid[v, u] == FLUID_LAVA else COLORS['ice'] if m.fluid[v, u] == FLUID_FROZEN else COLORS['water']
                for y in range(h + 1, int(m.level[v, u]) + 1):
                    blocks[(u, y, v)] = fc
    for (u, y, v), i in m.vox.items():
        if u0 <= u <= u1 and v0 <= v <= v1:
            n = m.vpal.name(i)
            if n == 'minecraft:air':
                blocks.pop((u, y, v), None)
            else:
                blocks[(u, y, v)] = color_of(n)
    if ymin is not None:
        blocks = {k: c for k, c in blocks.items() if k[1] >= ymin}
    if ymax is not None:
        blocks = {k: c for k, c in blocks.items() if k[1] <= ymax}
    if not blocks:
        return None
    # rotation: view from SE by default (u right-down, v left-down)
    def tr(u, v):
        cu, cv = u - u0, v - v0
        W, H = u1 - u0, v1 - v0
        for _ in range(rot % 4):
            cu, cv = W - cv, cu
            W, H = H, W
        return cu, cv
    ys = [k[1] for k in blocks]
    y_lo, y_hi = min(ys), max(ys)
    n = max(u1 - u0, v1 - v0) + 1
    width = int((2 * n + 4) * s)
    height = int((n + (y_hi - y_lo) * 1.2 + 6) * s)
    img = Image.new('RGB', (width, height), (30, 32, 40))
    d = ImageDraw.Draw(img)
    items = []
    for (u, y, v), c in blocks.items():
        a, b = tr(u, v)
        items.append((a + b, y, a, b, c))
    items.sort(key=lambda t: (t[0], t[1]))
    ox = width // 2
    oy = int((y_hi - y_lo) * 1.2 * s + 2 * s)
    for (_, y, a, b, c) in items:
        x = ox + (a - b) * s
        yy = oy + (a + b) * s * 0.5 - (y - y_lo) * s * 1.2
        top = [(x, yy - s * 0.5), (x + s, yy), (x, yy + s * 0.5), (x - s, yy)]
        left = [(x - s, yy), (x, yy + s * 0.5), (x, yy + s * 1.7), (x - s, yy + s * 1.2)]
        right = [(x + s, yy), (x, yy + s * 0.5), (x, yy + s * 1.7), (x + s, yy + s * 1.2)]
        cc = np.array(c)
        d.polygon(left, fill=tuple((cc * 0.72).astype(int)))
        d.polygon(right, fill=tuple((cc * 0.86).astype(int)))
        d.polygon(top, fill=tuple(np.minimum(255, cc * 1.05).astype(int)))
    if path:
        img.save(path)
    return img
