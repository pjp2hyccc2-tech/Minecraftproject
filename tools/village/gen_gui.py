#!/usr/bin/env python3
"""Village GUI atlas: textures/gui/village/widgets.png (512x512).

Run from the repository root:  python3 tools/village/gen_gui.py   (after model_mofu.py, which writes the mofu faces)
Layout (must match VillageScreen):
  9-slices  panel (0,0,32,32,b8)  paper (32,0,32,32,b6)  inset (64,0,16,16,b4)  slot (80,0,18,18)
            button (100,0|20|40,48,20,b4)  tab (150,0|24,48,24,b6)  row sel/hover (200,0|16,64,16,b3)  title (200,32,64,20,b6)
  icons     16x16 at v=64 (index 0..31) and v=80 (32..63)
  dishes    32x32 at v=96 / v=128 (16 per row)
  faces     24x24 at v=160 (6 fur patterns)
Edges of 9-slices are constant along their stretch axis and centres are flat, so stretching never smears texture.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft', 'textures', 'gui', 'village', 'widgets.png')
FACES = os.path.join(HERE, 'out', 'mofu_faces.png')
A = np.zeros((512, 512, 4), np.uint8)


def C(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def put(x, y, c):
    A[y, x] = c


def rect(x0, y0, x1, y1, c):
    """inclusive rect"""
    A[y0:y1 + 1, x0:x1 + 1] = c


# ====================================================================== 9-slices
def frame(x, y, w, h, layers, centre):
    """layers: list of colours from the outside in (1px each); centre colour fills the rest."""
    rect(x, y, x + w - 1, y + h - 1, centre)
    for i, c in enumerate(layers):
        rect(x + i, y + i, x + w - 1 - i, y + i, c)
        rect(x + i, y + h - 1 - i, x + w - 1 - i, y + h - 1 - i, c)
        rect(x + i, y + i, x + i, y + h - 1 - i, c)
        rect(x + w - 1 - i, y + i, x + w - 1 - i, y + h - 1 - i, c)


def panel():
    # black lacquer + red-brown wood frame with a gold inlay line and gold corner fittings
    frame(0, 0, 32, 32, [C('#0e0805'), C('#5c2e1c'), C('#7e4228'), C('#62341f'), C('#d8ac52'), C('#3a2214'), C('#2c1d13'), C('#2a1c12')], C('#271a11', 246))
    gold, gold2, dk = C('#f0cc72'), C('#a87a2a'), C('#0e0805')
    for cx, cy, sx, sy in ((0, 0, 1, 1), (31, 0, -1, 1), (0, 31, 1, -1), (31, 31, -1, -1)):
        for i in range(1, 6):
            put(cx + sx * i, cy + sy * 1, gold if i < 5 else gold2)
            put(cx + sx * 1, cy + sy * i, gold if i < 5 else gold2)
        put(cx + sx * 2, cy + sy * 2, gold)
        put(cx + sx * 3, cy + sy * 2, gold2)
        put(cx + sx * 2, cy + sy * 3, gold2)
        put(cx, cy, dk)


def paper():
    frame(32, 0, 32, 32, [C('#5e4630'), C('#c9b48e'), C('#e2d3b2'), C('#ecdfc2'), C('#f0e5ca'), C('#f2e8cf')], C('#f3e9d1'))
    # slightly darker aged corners
    for cx, cy, sx, sy in ((32, 0, 1, 1), (63, 0, -1, 1), (32, 31, 1, -1), (63, 31, -1, -1)):
        put(cx + sx * 1, cy + sy * 1, C('#a8936c'))
        put(cx + sx * 2, cy + sy * 1, C('#d4c29e'))
        put(cx + sx * 1, cy + sy * 2, C('#d4c29e'))


def inset():
    x, y = 64, 0
    rect(x, y, x + 15, y + 15, C('#1d140e'))
    rect(x, y, x + 15, y, C('#0a0604'))
    rect(x, y, x, y + 15, C('#0a0604'))
    rect(x + 1, y + 1, x + 14, y + 1, C('#140d09'))
    rect(x + 1, y + 1, x + 1, y + 14, C('#140d09'))
    rect(x, y + 15, x + 15, y + 15, C('#5a4230'))
    rect(x + 15, y, x + 15, y + 15, C('#5a4230'))
    rect(x + 1, y + 14, x + 14, y + 14, C('#3a2a1e'))
    rect(x + 14, y + 1, x + 14, y + 14, C('#3a2a1e'))


def slot():
    x, y = 80, 0
    rect(x, y, x + 17, y + 17, C('#3b2c20'))
    rect(x, y, x + 16, y, C('#120c08'))
    rect(x, y, x, y + 16, C('#120c08'))
    rect(x + 1, y + 17, x + 17, y + 17, C('#8a6a4a'))
    rect(x + 17, y + 1, x + 17, y + 17, C('#8a6a4a'))
    rect(x + 1, y + 1, x + 16, y + 16, C('#2a1f17'))


def button(y, face, light, dark, outline='#0e0805'):
    x = 100
    rect(x, y, x + 47, y + 19, C(face))
    rect(x, y, x + 47, y, C(outline))
    rect(x, y + 19, x + 47, y + 19, C(outline))
    rect(x, y, x, y + 19, C(outline))
    rect(x + 47, y, x + 47, y + 19, C(outline))
    rect(x + 1, y + 1, x + 46, y + 1, C(light))
    rect(x + 1, y + 1, x + 1, y + 18, C(light))
    rect(x + 1, y + 18, x + 46, y + 18, C(dark))
    rect(x + 46, y + 1, x + 46, y + 18, C(dark))
    rect(x + 2, y + 2, x + 45, y + 2, C(light, 110))


def tab(y, face, top, line):
    x = 150
    rect(x, y, x + 47, y + 23, C(face))
    rect(x, y, x + 47, y, C('#0e0805'))
    rect(x, y, x, y + 23, C('#0e0805'))
    rect(x + 47, y, x + 47, y + 23, C('#0e0805'))
    rect(x + 1, y + 1, x + 46, y + 2, C(top))
    rect(x + 1, y + 23, x + 46, y + 23, C(line))
    rect(x + 1, y + 3, x + 1, y + 22, C(top, 90))


def rows():
    x = 200
    # selected: gold frame + warm translucent fill
    rect(x, 0, x + 63, 15, C('#f0c860', 70))
    frame(x, 0, 64, 16, [C('#f6d27a', 230), C('#9a7428', 160)], C('#f0c860', 70))
    # hover
    frame(x, 16, 64, 16, [C('#e8d4a8', 120), C('#ffffff', 30)], C('#ffffff', 34))
    # title plate (black lacquer, gold double border, red inner line)
    frame(x, 32, 64, 20, [C('#0e0805'), C('#d8ac52'), C('#1a0e08'), C('#a8322a'), C('#1a0e08'), C('#1a0e08')], C('#1c100a', 245))


# ====================================================================== sprite helpers
class S:
    """Small RGBA sprite drawn with masks and auto-shading."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.uint8)

    def mask(self):
        return Image.new('1', (self.w, self.h), 0)

    def shade(self, m, ramp, light=True, rim=True):
        """Fill mask m (PIL '1') with ramp [darkest..lightest] (>=4 colours): bevel highlight top-left, shadow bottom-right."""
        mk = np.array(m, bool)
        h, w = mk.shape
        r = [C(c) if isinstance(c, str) else c for c in ramp]
        n = len(r)
        base = r[n // 2]
        for y in range(h):
            for x in range(w):
                if not mk[y, x]:
                    continue
                c = base

                def out(dx, dy):
                    xx, yy = x + dx, y + dy
                    return not (0 <= xx < w and 0 <= yy < h and mk[yy, xx])
                if light and (out(-1, 0) or out(0, -1)):
                    c = r[min(n - 1, n // 2 + 1)]
                if light and out(-1, -1) and not out(1, 1):
                    c = r[n - 1]
                if rim and (out(1, 0) or out(0, 1)):
                    c = r[max(1, n // 2 - 1)]
                self.a[y, x] = c

    def fill(self, m, color):
        mk = np.array(m, bool)
        self.a[mk] = C(color) if isinstance(color, str) else color

    def px(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = C(color) if isinstance(color, str) else color

    def outline(self, color='#140c08'):
        al = self.a[..., 3] > 0
        o = np.zeros_like(al)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            o |= np.roll(np.roll(al, dy, 0), dx, 1)
        o &= ~al
        o[0, :] &= al[0, :]
        self.a[o] = C(color)

    def blit_to(self, x, y):
        sub = A[y:y + self.h, x:x + self.w]
        m = self.a[..., 3] > 0
        sub[m] = self.a[m]


def ellipse(s, box):
    m = s.mask()
    ImageDraw.Draw(m).ellipse(box, fill=1)
    return m


def poly(s, pts):
    m = s.mask()
    ImageDraw.Draw(m).polygon(pts, fill=1)
    return m


def rmask(s, x0, y0, x1, y1):
    m = s.mask()
    ImageDraw.Draw(m).rectangle((x0, y0, x1, y1), fill=1)
    return m


def lmask(s, pts, width=1):
    m = s.mask()
    ImageDraw.Draw(m).line(pts, fill=1, width=width)
    return m


GOLD = ['#5a3a08', '#9a6a10', '#d6a020', '#f2c94a', '#fff0a8']
SILVER = ['#3a3e44', '#6a727c', '#a8b0ba', '#d6dce4', '#ffffff']
RED = ['#3a0808', '#7a1414', '#c02a22', '#e8564a', '#ffa090']
BROWN = ['#2a1808', '#5a3618', '#8a5a2a', '#b88048', '#e0aa70']
WOOD = ['#2a1a0e', '#4a2e18', '#6e4624', '#9a6a3a', '#c89a5e']
STEEL = ['#20242a', '#4a525c', '#8a949e', '#c4ccd4', '#f4f8fc']
GREEN = ['#0e2a0e', '#1e5a1e', '#3a9a2e', '#6ad04a', '#b0f08a']
BLUE = ['#081838', '#14306a', '#2a5ab0', '#5a94e8', '#a8d4ff']
CYAN = ['#0c3040', '#1a6a8a', '#3aaad0', '#8ae0f8', '#e0fbff']
PURPLE = ['#1e0830', '#40145e', '#7a2ab0', '#b060e8', '#e0a8ff']
YELLOW = ['#4a3a00', '#9a7a00', '#e0b810', '#ffe050', '#fff8b0']
CREAM = ['#5e5040', '#a8987a', '#e0d4b8', '#f4ecd8', '#ffffff']
PINK = ['#5a1a2a', '#a03a52', '#e07090', '#f4a4bc', '#ffe0ea']


# ====================================================================== 16x16 icons
def icon_zenny(s):
    s.shade(ellipse(s, (1, 1, 14, 14)), GOLD)
    s.fill(rmask(s, 6, 6, 9, 9), '#3a2404')
    s.fill(rmask(s, 7, 7, 8, 8), '#00000000')
    s.px(4, 3, '#fffbe0')
    s.px(3, 4, '#fffbe0')
    s.outline('#3a2404')


def icon_hr(s):
    s.shade(poly(s, [(2, 2), (13, 2), (13, 8), (7.5, 14), (2, 8)]), RED)
    s.fill(rmask(s, 5, 5, 6, 10), '#ffe9a0')
    s.fill(rmask(s, 9, 5, 10, 10), '#ffe9a0')
    s.fill(rmask(s, 6, 7, 9, 7), '#ffe9a0')
    s.fill(lmask(s, [(2, 2), (13, 2)]), '#f2c94a')
    s.outline()


def icon_crown(s, ramp):
    s.shade(poly(s, [(1, 5), (4, 9), (7.5, 3), (11, 9), (14, 5), (13, 12), (2, 12)]), ramp)
    s.fill(rmask(s, 2, 11, 13, 13), ramp[1])
    for x in (4, 7, 11):
        s.px(x, 12, '#e8404a' if x == 7 else '#4aa8e8')
    s.outline()


def star_pts(cx, cy, r1, r2, n=5):
    pts = []
    for i in range(n * 2):
        r = r1 if i % 2 == 0 else r2
        a = -math.pi / 2 + i * math.pi / n
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def icon_star(s, filled=True):
    m = poly(s, star_pts(7.5, 8, 7, 3))
    if filled:
        s.shade(m, GOLD)
    else:
        s.fill(m, '#5a4c3e')
        inner = poly(s, star_pts(7.5, 8, 5, 2))
        s.fill(inner, '#2a221a')
    s.outline()


def icon_heart(s):
    m = poly(s, [(1, 5), (4, 2), (7.5, 5), (11, 2), (14, 5), (14, 7), (7.5, 14), (1, 7)])
    s.shade(m, RED)
    s.px(4, 4, '#ffd0c8')
    s.px(3, 5, '#ffd0c8')
    s.outline('#2a0606')


def icon_stamina(s):
    s.shade(poly(s, [(9, 1), (3, 9), (7, 9), (5, 15), (12, 6), (8, 6), (11, 1)]), YELLOW)
    s.outline('#3a2a00')


def icon_sword(s):
    s.shade(poly(s, [(13, 1), (14, 2), (6, 10), (5, 9)]), STEEL)
    s.fill(lmask(s, [(3, 8), (7, 12)], 1), '#d6a020')
    s.fill(lmask(s, [(2, 13), (5, 10)], 2), '#6a3a1a')
    s.px(1, 14, '#d6a020')
    s.outline()


def icon_shield(s):
    s.shade(poly(s, [(2, 2), (13, 2), (13, 8), (7.5, 14), (2, 8)]), STEEL)
    s.shade(poly(s, [(5, 4), (10, 4), (10, 8), (7.5, 11), (5, 8)]), BLUE)
    s.outline()


def icon_meat(s):
    s.shade(ellipse(s, (2, 2, 11, 11)), BROWN)
    s.fill(ellipse(s, (4, 4, 8, 8)), '#c87848')
    s.shade(poly(s, [(9, 9), (13, 13), (12, 14), (8, 10)]), CREAM)
    s.shade(ellipse(s, (11, 11, 15, 15)), CREAM)
    s.outline()


def icon_clock(s):
    s.shade(ellipse(s, (1, 1, 14, 14)), CREAM)
    s.fill(ellipse(s, (3, 3, 12, 12)), '#fbf6ea')
    s.fill(lmask(s, [(7, 7), (7, 4)]), '#3a2a1a')
    s.fill(lmask(s, [(7, 7), (10, 9)]), '#c02a22')
    s.outline()


def icon_mofu(s):
    s.shade(poly(s, [(2, 1), (5, 5), (10, 5), (13, 1), (14, 7), (13, 12), (8, 14), (2, 12), (1, 7)]), ['#5a4a3a', '#a08a70', '#e0cdb0', '#f4e8d4', '#ffffff'])
    s.px(5, 8, '#1a1210')
    s.px(10, 8, '#1a1210')
    s.px(7, 10, '#e07a8a')
    s.px(8, 10, '#e07a8a')
    s.px(3, 3, '#e8a0aa')
    s.px(12, 3, '#e8a0aa')
    s.outline('#2a1c12')


def icon_check(s):
    s.shade(lmask(s, [(2, 8), (6, 12), (13, 3)], 3), GREEN)
    s.outline('#0a1a0a')


def icon_cross(s):
    s.shade(lmask(s, [(3, 3), (12, 12)], 3), RED)
    s.shade(lmask(s, [(12, 3), (3, 12)], 3), RED)
    s.outline('#2a0606')


def icon_hammer(s):
    s.shade(rmask(s, 2, 2, 12, 6), STEEL)
    s.shade(poly(s, [(7, 6), (9, 6), (9, 14), (7, 14)]), WOOD)
    s.outline()


def icon_fire(s):
    s.shade(poly(s, [(8, 1), (12, 6), (13, 10), (11, 14), (5, 14), (3, 10), (4, 6), (6, 8)]), ['#5a1000', '#b02a00', '#f06010', '#ffa030', '#ffe090'])
    s.fill(poly(s, [(8, 7), (10, 10), (9, 13), (7, 13), (6, 11)]), '#ffe070')
    s.outline('#3a0a00')


def icon_water(s):
    s.shade(poly(s, [(8, 1), (12, 7), (13, 10), (11, 13), (8, 14), (5, 13), (3, 10), (4, 7)]), BLUE)
    s.px(5, 9, '#d8ecff')
    s.px(5, 10, '#d8ecff')
    s.outline('#081838')


def icon_thunder(s):
    s.shade(poly(s, [(10, 1), (4, 8), (8, 8), (5, 15), (12, 6), (8, 6), (12, 1)]), YELLOW)
    s.outline('#3a2a00')


def icon_ice(s):
    m = s.mask()
    d = ImageDraw.Draw(m)
    d.line([(8, 1), (8, 14)], fill=1)
    d.line([(2, 4), (13, 11)], fill=1)
    d.line([(2, 11), (13, 4)], fill=1)
    for (x, y) in ((8, 1), (8, 14), (2, 4), (13, 11), (2, 11), (13, 4)):
        d.point((x, y), fill=1)
    s.shade(m, CYAN, light=False, rim=False)
    s.fill(rmask(s, 7, 6, 8, 9), '#ffffff')
    s.outline('#0c2a3a')


def icon_dragon(s):
    s.shade(ellipse(s, (1, 4, 14, 11)), PURPLE)
    s.fill(poly(s, [(8, 4), (9, 7), (8, 11), (7, 7)]), '#1a0420')
    s.px(5, 6, '#ffd0ff')
    s.outline('#140420')


def icon_fighter(s):
    s.shade(poly(s, [(12, 1), (14, 2), (6, 11), (4, 10)]), STEEL)
    s.shade(ellipse(s, (1, 9, 7, 15)), ['#5a4a3a', '#a08a70', '#e0cdb0', '#f4e8d4', '#ffffff'])
    s.px(3, 10, '#e8a0aa')
    s.outline()


def icon_healer(s):
    s.shade(poly(s, [(2, 12), (4, 9), (8, 6), (12, 2), (14, 4), (11, 9), (6, 13), (3, 14)]), GOLD)
    s.shade(ellipse(s, (10, 1, 15, 6)), ['#3a2404', '#9a6a10', '#d6a020', '#f2c94a', '#fff0a8'])
    s.fill(poly(s, [(3, 3), (5, 2), (6, 5), (4, 6)]), '#6ad04a')
    s.outline()


def icon_bomber(s):
    s.shade(ellipse(s, (2, 4, 13, 15)), ['#0a0a0c', '#1e1e24', '#3a3a44', '#62626e', '#9a9aaa'])
    s.fill(lmask(s, [(9, 4), (11, 1)]), '#8a6a3a')
    s.px(12, 1, '#ffd040')
    s.px(12, 0, '#ff8020')
    s.px(5, 7, '#c8c8d8')
    s.outline()


def icon_book(s):
    s.shade(rmask(s, 2, 2, 13, 13), ['#2a0a08', '#5a1a14', '#8a2a20', '#b84a3a', '#e07a60'])
    s.fill(rmask(s, 3, 12, 13, 13), '#f3e9d1')
    s.fill(rmask(s, 6, 5, 10, 6), '#f2c94a')
    s.outline()


def icon_map(s):
    s.shade(poly(s, [(1, 3), (5, 1), (10, 3), (14, 1), (14, 12), (10, 14), (5, 12), (1, 14)]), CREAM)
    s.fill(lmask(s, [(5, 1), (5, 12)]), '#c8b48e')
    s.fill(lmask(s, [(10, 3), (10, 14)]), '#c8b48e')
    s.fill(lmask(s, [(7, 6), (9, 8)]), '#c02a22')
    s.fill(lmask(s, [(9, 6), (7, 8)]), '#c02a22')
    s.outline()


def icon_lock(s):
    s.fill(lmask(s, [(4, 7), (4, 4), (6, 2), (9, 2), (11, 4), (11, 7)], 2), '#8a949e')
    s.shade(rmask(s, 2, 7, 13, 14), GOLD)
    s.fill(rmask(s, 7, 9, 8, 12), '#3a2404')
    s.outline()


def icon_paw(s):
    s.shade(ellipse(s, (4, 7, 12, 14)), PINK)
    for b in ((1, 4, 4, 8), (5, 1, 8, 5), (8, 1, 11, 5), (12, 4, 15, 8)):
        s.shade(ellipse(s, b), PINK)
    s.outline('#3a1018')


def icon_arrow(s, right):
    pts = [(4, 2), (12, 8), (4, 14)] if right else [(12, 2), (4, 8), (12, 14)]
    s.shade(poly(s, pts), CREAM)
    s.outline()


def icon_plus(s):
    s.shade(rmask(s, 6, 2, 9, 13), GREEN)
    s.shade(rmask(s, 2, 6, 13, 9), GREEN)
    s.outline('#0a1a0a')


def icon_minus(s):
    s.shade(rmask(s, 2, 6, 13, 9), RED)
    s.outline('#2a0606')


def icon_helm(s):
    s.shade(poly(s, [(2, 9), (3, 4), (6, 2), (10, 2), (13, 4), (14, 9), (14, 12), (2, 12)]), STEEL)
    s.fill(rmask(s, 2, 10, 14, 11), '#d6a020')
    s.fill(poly(s, [(3, 4), (4, 0), (6, 3)]), '#a8b0ba')
    s.fill(poly(s, [(10, 3), (12, 0), (13, 4)]), '#a8b0ba')
    s.outline()


def icon_vest(s):
    s.shade(poly(s, [(3, 2), (6, 2), (8, 4), (10, 2), (13, 2), (14, 6), (13, 14), (3, 14), (2, 6)]), STEEL)
    s.fill(rmask(s, 3, 10, 13, 11), '#6a3a1a')
    s.fill(rmask(s, 7, 6, 8, 8), '#d6a020')
    s.outline()


def icon_claw(s):
    for x0 in (3, 7, 11):
        s.shade(poly(s, [(x0, 14), (x0 + 1, 14), (x0 + 3, 3), (x0 + 2, 1)]), STEEL)
    s.shade(rmask(s, 2, 12, 14, 14), WOOD)
    s.outline()


def icon_scroll(s):
    s.shade(rmask(s, 3, 3, 12, 12), CREAM)
    s.shade(rmask(s, 1, 2, 3, 13), WOOD)
    s.shade(rmask(s, 12, 2, 14, 13), WOOD)
    for y in (5, 7, 9):
        s.fill(lmask(s, [(5, y), (10, y)]), '#7a6a52')
    s.outline()


def icon_gift(s):
    s.shade(rmask(s, 2, 5, 13, 14), RED)
    s.fill(rmask(s, 7, 5, 8, 14), '#f2c94a')
    s.fill(rmask(s, 2, 8, 13, 9), '#f2c94a')
    s.fill(poly(s, [(7, 5), (3, 1), (5, 5)]), '#f2c94a')
    s.fill(poly(s, [(8, 5), (12, 1), (10, 5)]), '#f2c94a')
    s.outline()


def icon_exclaim(s):
    s.shade(ellipse(s, (1, 1, 14, 14)), GOLD)
    s.fill(rmask(s, 7, 3, 8, 9), '#3a2404')
    s.fill(rmask(s, 7, 11, 8, 12), '#3a2404')
    s.outline('#3a2404')


def icon_sparkle(s):
    s.shade(poly(s, [(8, 0), (10, 6), (15, 8), (10, 10), (8, 15), (6, 10), (1, 8), (6, 6)]), YELLOW)
    s.px(8, 7, '#ffffff')
    s.outline('#5a4000')


def icon_bag(s):
    s.shade(poly(s, [(4, 5), (12, 5), (14, 14), (2, 14)]), BROWN)
    s.fill(lmask(s, [(5, 5), (6, 2), (10, 2), (11, 5)], 1), '#5a3618')
    s.fill(rmask(s, 6, 8, 10, 9), '#f2c94a')
    s.outline()


def icon_sort(s):
    s.shade(poly(s, [(4, 1), (7, 5), (1, 5)]), CREAM)
    s.fill(rmask(s, 3, 5, 5, 14), '#e0d4b8')
    s.shade(poly(s, [(11, 14), (14, 10), (8, 10)]), CREAM)
    s.fill(rmask(s, 10, 1, 12, 10), '#e0d4b8')
    s.outline()


def icon_deposit(s):
    s.shade(rmask(s, 1, 8, 14, 14), WOOD)
    s.fill(rmask(s, 1, 8, 14, 9), '#d6a020')
    s.shade(poly(s, [(5, 1), (10, 1), (10, 4), (13, 4), (7.5, 9), (2, 4), (5, 4)]), GREEN)
    s.outline()


def icon_skull(s):
    s.shade(poly(s, [(2, 6), (4, 2), (11, 2), (13, 6), (13, 10), (10, 13), (5, 13), (2, 10)]), CREAM)
    s.fill(rmask(s, 4, 6, 6, 8), '#2a1c12')
    s.fill(rmask(s, 9, 6, 11, 8), '#2a1c12')
    s.fill(poly(s, [(1, 4), (0, 0), (4, 2)]), '#c8b48e')
    s.fill(poly(s, [(14, 4), (15, 0), (11, 2)]), '#c8b48e')
    s.outline()


def icon_flag(s):
    s.fill(rmask(s, 2, 1, 3, 15), '#6a3a1a')
    s.shade(poly(s, [(4, 2), (14, 4), (4, 9)]), RED)
    s.outline()


def icon_pot(s):
    s.shade(poly(s, [(2, 6), (13, 6), (12, 13), (3, 13)]), ['#1a1a1e', '#33333a', '#55555e', '#7a7a86', '#a8a8b4'])
    s.fill(rmask(s, 1, 5, 14, 6), '#7a7a86')
    s.fill(lmask(s, [(5, 3), (6, 1)]), '#d0d0d8')
    s.fill(lmask(s, [(9, 3), (10, 1)]), '#d0d0d8')
    s.outline()


def icon_tag(s):
    s.shade(poly(s, [(1, 7), (7, 1), (14, 1), (14, 8), (8, 14)]), ['#5a1000', '#a02a10', '#e05a20', '#f59a48', '#ffd090'])
    s.fill(rmask(s, 10, 4, 11, 5), '#3a0a00')
    s.outline()


def icon_new(s):
    s.shade(rmask(s, 0, 4, 15, 11), RED)
    for x in (2, 4):
        s.fill(rmask(s, x, 6, x, 9), '#ffffff')
    s.px(3, 7, '#ffffff')
    s.fill(rmask(s, 6, 6, 7, 9), '#ffffff')
    s.px(7, 7, '#c02a22')
    s.fill(rmask(s, 9, 6, 9, 9), '#ffffff')
    s.fill(rmask(s, 13, 6, 13, 9), '#ffffff')
    s.px(11, 8, '#ffffff')
    s.px(10, 9, '#ffffff')
    s.px(12, 9, '#ffffff')
    s.outline()


def icon_coins(s):
    for y in (11, 8, 5):
        s.shade(ellipse(s, (2, y, 13, y + 4)), GOLD)
    s.outline('#3a2404')


# ---- ingredients (indices 48..58)
def ing_rice(s):
    s.shade(poly(s, [(1, 8), (14, 8), (12, 14), (3, 14)]), ['#0a1a3a', '#1a3a7a', '#2a5ab0', '#5a8ae0', '#9ac0ff'])
    s.shade(ellipse(s, (2, 3, 13, 10)), CREAM)
    for (x, y) in ((5, 5), (8, 4), (10, 6), (6, 7)):
        s.px(x, y, '#ffffff')
    s.outline()


def ing_millet(s):
    s.fill(lmask(s, [(7, 15), (8, 4)], 1), '#6a8a2a')
    for i, (x, y) in enumerate(((5, 3), (9, 3), (4, 6), (10, 6), (5, 9), (9, 9), (7, 1))):
        s.shade(ellipse(s, (x, y, x + 2, y + 3)), GOLD)
    s.outline()


def ing_boar(s):
    s.shade(poly(s, [(1, 6), (5, 2), (13, 3), (15, 8), (11, 13), (3, 12)]), ['#3a0a08', '#7a1a14', '#b8322a', '#e0605a', '#ffa098'])
    s.fill(lmask(s, [(4, 9), (12, 5)]), '#f4d8c8')
    s.outline()


def ing_marbled(s):
    s.shade(poly(s, [(1, 6), (5, 2), (13, 3), (15, 8), (11, 13), (3, 12)]), ['#4a1418', '#8a3038', '#d06a70', '#f0a0a4', '#ffd8da'])
    for (x, y) in ((4, 6), (6, 9), (9, 5), (11, 8), (7, 4), (12, 10)):
        s.px(x, y, '#fff4f0')
        s.px(x + 1, y, '#fff4f0')
    s.outline()


def ing_trout(s):
    s.shade(ellipse(s, (1, 5, 12, 11)), ['#1a2a3a', '#3a5a6a', '#6a90a0', '#a8c8d4', '#e8f4f8'])
    s.shade(poly(s, [(11, 8), (15, 4), (15, 12)]), ['#1a2a3a', '#3a5a6a', '#6a90a0', '#a8c8d4', '#e8f4f8'])
    s.fill(lmask(s, [(3, 8), (10, 8)]), '#e08a7a')
    s.px(3, 7, '#101010')
    s.outline()


def ing_tuna(s):
    s.shade(poly(s, [(2, 3), (14, 3), (12, 13), (4, 13)]), ['#3a0610', '#7a1020', '#c02a3a', '#e85a68', '#ff98a0'])
    s.fill(lmask(s, [(4, 6), (12, 6)]), '#f8c8cc')
    s.fill(lmask(s, [(5, 10), (11, 10)]), '#f8c8cc')
    s.outline()


def ing_greens(s):
    for pts in ([(7, 15), (2, 6), (5, 2), (8, 8)], [(8, 15), (13, 5), (10, 1), (7, 8)], [(8, 15), (6, 4), (9, 0), (10, 6)]):
        s.shade(poly(s, pts), GREEN)
    s.fill(lmask(s, [(7, 15), (8, 9)]), '#2a6a1a')
    s.outline()


def ing_radish(s):
    s.shade(poly(s, [(5, 5), (11, 5), (10, 13), (8, 15), (6, 13)]), CREAM)
    for pts in ([(6, 5), (3, 0), (7, 3)], [(8, 5), (8, 0), (9, 4)], [(10, 5), (13, 0), (9, 3)]):
        s.shade(poly(s, pts), GREEN)
    s.outline()


def ing_shiitake(s):
    s.shade(ellipse(s, (1, 2, 14, 9)), ['#2a1408', '#5a2e14', '#8a4a24', '#b06a3a', '#d89060'])
    s.shade(rmask(s, 6, 8, 9, 14), CREAM)
    for (x, y) in ((4, 4), (9, 3), (11, 6)):
        s.px(x, y, '#e8d0b0')
    s.outline()


def ing_honey(s):
    for (x, y) in ((2, 2), (8, 2), (5, 7), (11, 7), (2, 11), (8, 11)):
        s.shade(poly(s, [(x + 1, y), (x + 4, y), (x + 5, y + 2), (x + 4, y + 4), (x + 1, y + 4), (x, y + 2)]), GOLD)
    s.outline('#5a3a00')


def ing_pepper(s):
    s.shade(poly(s, [(4, 4), (8, 3), (13, 9), (14, 14), (9, 11), (3, 6)]), RED)
    s.fill(lmask(s, [(4, 4), (3, 1), (6, 1)], 1), '#3a8a2a')
    s.outline('#2a0606')


ICONS = [icon_zenny, icon_hr, lambda s: icon_crown(s, GOLD), lambda s: icon_crown(s, SILVER), icon_star, lambda s: icon_star(s, False),
         icon_heart, icon_stamina, icon_sword, icon_shield, icon_meat, icon_clock, icon_mofu, icon_check, icon_cross, icon_hammer,
         icon_fire, icon_water, icon_thunder, icon_ice, icon_dragon, icon_fighter, icon_healer, icon_bomber,
         icon_book, icon_map, icon_lock, icon_paw, lambda s: icon_arrow(s, False), lambda s: icon_arrow(s, True), icon_plus, icon_minus,
         icon_helm, icon_vest, icon_claw, icon_scroll, icon_gift, icon_exclaim, icon_sparkle, icon_bag,
         icon_sort, icon_deposit, icon_skull, icon_flag, icon_pot, icon_tag, icon_new, icon_coins,
         ing_rice, ing_millet, ing_boar, ing_marbled, ing_trout, ing_tuna, ing_greens, ing_radish, ing_shiitake, ing_honey, ing_pepper]


# ====================================================================== 32x32 dishes
BOWL_BLUE = ['#081a3a', '#12306a', '#2050a0', '#4a7ad0', '#90b8f0']
BOWL_RED = ['#2a0606', '#5a0e0c', '#8a1a16', '#b8322a', '#e06050']
BOWL_CLAY = ['#2a1808', '#4e2e14', '#7a4a22', '#a06a38', '#c89058']
LACQUER = ['#0e0606', '#2a0e0c', '#4a1a14', '#6a2a1e', '#8a3a2a']
PLATE = ['#5a5a60', '#9a9aa4', '#d0d0d8', '#ecedf2', '#ffffff']
BOARD = WOOD


def bowl(s, ramp, rim='#f4ecd8'):
    s.shade(poly(s, [(3, 15), (28, 15), (25, 25), (20, 28), (11, 28), (6, 25)]), ramp)
    s.fill(rmask(s, 12, 28, 19, 29), ramp[1])
    s.fill(lmask(s, [(3, 15), (28, 15)]), rim)
    s.fill(lmask(s, [(8, 21), (23, 21)]), ramp[3])


def plate(s, y0=18):
    s.shade(ellipse(s, (1, y0, 30, y0 + 11)), PLATE)
    s.fill(ellipse(s, (5, y0 + 2, 26, y0 + 8)), '#e4e5ea')


def heap(s, box, ramp, dots=(), dotc='#ffffff'):
    s.shade(ellipse(s, box), ramp)
    for (x, y) in dots:
        s.px(x, y, dotc)


def steam(s, xs=(10, 16, 22)):
    for x in xs:
        for i, y in enumerate((6, 4, 2)):
            s.px(x + (1 if i % 2 else 0), y, (255, 255, 255, 150))


def dish_donburi(s):
    bowl(s, BOWL_BLUE)
    heap(s, (5, 9, 26, 19), CREAM, ((9, 12), (14, 11), (20, 13)))
    for (x0, y0) in ((7, 10), (13, 9), (19, 10)):
        s.shade(poly(s, [(x0, y0 + 3), (x0 + 4, y0), (x0 + 7, y0 + 2), (x0 + 3, y0 + 5)]), BROWN)
    s.fill(rmask(s, 15, 8, 16, 9), '#6ad04a')
    steam(s)


def dish_sushi(s):
    s.shade(poly(s, [(1, 18), (30, 18), (30, 24), (1, 24)]), BOARD)
    s.fill(rmask(s, 3, 24, 5, 27), '#4a2e18')
    s.fill(rmask(s, 26, 24, 28, 27), '#4a2e18')
    for i, (fx, fish) in enumerate(((3, ['#5a1000', '#b0400a', '#f07a30', '#ffa860', '#ffd0a0']), (12, RED), (21, ['#5a1000', '#b0400a', '#f07a30', '#ffa860', '#ffd0a0']))):
        heap(s, (fx, 12, fx + 8, 18), CREAM)
        s.shade(poly(s, [(fx - 1, 13), (fx + 9, 11), (fx + 9, 14), (fx - 1, 15)]), fish)
        s.fill(lmask(s, [(fx + 1, 13), (fx + 7, 12)]), (255, 255, 255, 140))


def dish_zosui(s):
    bowl(s, BOWL_CLAY)
    heap(s, (5, 10, 26, 19), ['#8a7a5a', '#c8b88e', '#ece0c0', '#f8f0dc', '#ffffff'], ((8, 13), (12, 15), (18, 12), (22, 15)), '#6ad04a')
    s.fill(rmask(s, 14, 13, 16, 14), '#f2c94a')
    steam(s)


def dish_takikomi(s):
    bowl(s, BOWL_RED)
    heap(s, (5, 9, 26, 19), ['#5a4020', '#a07a4a', '#d0aa70', '#e8c890', '#f8e4b8'], ((9, 12), (15, 11), (21, 13)), '#ffffff')
    for (x, y) in ((8, 12), (17, 13), (13, 15)):
        s.shade(ellipse(s, (x, y, x + 3, y + 2)), ['#2a1408', '#5a2e14', '#8a4a24', '#b06a3a', '#d89060'])
    steam(s)


def dango_stick(s, colors, glaze=False):
    s.fill(lmask(s, [(4, 29), (27, 3)], 1), '#c8a060')
    for i, col in enumerate(colors):
        cx, cy = 8 + i * 7, 24 - i * 7
        s.shade(ellipse(s, (cx - 4, cy - 4, cx + 4, cy + 4)), col)
        if glaze:
            s.fill(ellipse(s, (cx - 4, cy - 4, cx + 3, cy + 1)), (196, 120, 40, 200))
            s.px(cx - 2, cy - 3, '#fff0c0')


def dish_dango(s):
    dango_stick(s, [['#2a5a1a', '#4a8a2a', '#7ac04a', '#a8e078', '#d8ffb0'], CREAM, PINK])


def dish_yakionigiri(s):
    s.shade(poly(s, [(15, 4), (27, 25), (4, 25)]), ['#4a2a10', '#8a5424', '#c08040', '#e0a860', '#f8d090'])
    s.fill(poly(s, [(9, 19), (21, 19), (23, 25), (7, 25)]), '#141a12')
    s.fill(lmask(s, [(10, 20), (20, 20)]), '#2a3a24')
    s.px(13, 10, '#fff0c0')
    s.px(14, 9, '#fff0c0')


def dish_hamayaki(s):
    s.shade(rmask(s, 2, 16, 29, 27), ['#1a1a1e', '#2e2e34', '#44444c', '#5e5e68', '#808090'])
    for x in range(4, 29, 3):
        s.fill(lmask(s, [(x, 17), (x, 26)]), '#222228')
    s.shade(ellipse(s, (3, 11, 18, 18)), ['#1a2a3a', '#3a5a6a', '#6a90a0', '#a8c8d4', '#e8f4f8'])
    s.shade(poly(s, [(17, 14), (21, 10), (21, 19)]), ['#1a2a3a', '#3a5a6a', '#6a90a0', '#a8c8d4', '#e8f4f8'])
    s.fill(lmask(s, [(6, 14), (14, 14)]), '#3a2a1a')
    s.fill(lmask(s, [(20, 5), (28, 22)], 1), '#c8a060')
    for (x, y) in ((21, 8), (23, 12), (25, 16)):
        s.shade(ellipse(s, (x, y, x + 4, y + 3)), BROWN)
    s.fill(rmask(s, 10, 27, 20, 29), '#e05a20')


def dish_nabe(s):
    s.shade(poly(s, [(2, 13), (29, 13), (27, 26), (4, 26)]), ['#141418', '#2a2a30', '#44444e', '#62626e', '#8a8a98'])
    s.fill(rmask(s, 0, 15, 2, 17), '#44444e')
    s.fill(rmask(s, 29, 15, 31, 17), '#44444e')
    s.shade(ellipse(s, (3, 9, 28, 17)), ['#5a2a08', '#9a4a14', '#c86a24', '#e08a40', '#f0b070'])
    for (x, y, c) in ((7, 12, '#6ad04a'), (12, 11, '#f4ecd8'), (17, 12, '#e8564a'), (22, 11, '#8a4a24'), (14, 13, '#f2c94a')):
        s.fill(ellipse(s, (x, y, x + 3, y + 2)), c)
    steam(s, (8, 15, 22))


def dish_kushiyaki(s):
    for k, y in enumerate((9, 18)):
        s.fill(lmask(s, [(2, y + 4), (29, y)], 1), '#c8a060')
        for i in range(4):
            x = 6 + i * 6
            s.shade(rmask(s, x, y - 1 - i // 2, x + 4, y + 4 - i // 2), BROWN if (i + k) % 2 == 0 else ['#2a3a10', '#4a6a1a', '#6a9a2a', '#9ac85a', '#c8f08a'])


def dish_kakuni(s):
    bowl(s, LACQUER, '#d6a020')
    for (x, y) in ((7, 9), (14, 8), (19, 11), (10, 13)):
        s.shade(rmask(s, x, y, x + 5, y + 5), ['#2a1004', '#5a2a0c', '#8a4a1a', '#b86a30', '#e09a58'])
        s.fill(rmask(s, x, y, x + 5, y + 1), '#f4d8b8')
    s.fill(rmask(s, 17, 9, 18, 10), '#6ad04a')


def dish_gekikara(s):
    plate(s, 17)
    heap(s, (4, 11, 27, 22), ['#3a0400', '#7a0c04', '#c0200c', '#f0441c', '#ff8a50'], ((8, 14), (13, 13), (19, 15), (23, 17)), '#3a0400')
    s.shade(poly(s, [(20, 6), (24, 5), (28, 10), (27, 13), (22, 9)]), RED)
    s.fill(lmask(s, [(20, 6), (18, 4)]), '#3a8a2a')
    steam(s, (8, 13))


def dish_tempura(s):
    plate(s, 18)
    s.fill(poly(s, [(6, 19), (24, 17), (26, 25), (8, 26)]), '#f8f4e8')
    for (x0, y0) in ((5, 11), (13, 9), (20, 12)):
        s.shade(poly(s, [(x0, y0 + 9), (x0 + 3, y0), (x0 + 7, y0 + 1), (x0 + 6, y0 + 10)]), ['#5a3000', '#a06010', '#e0a030', '#f8d070', '#fff0b0'])
        s.px(x0 + 4, y0 - 1, '#e85a30')
        s.px(x0 + 5, y0 - 1, '#e85a30')


def dish_dobinmushi(s):
    s.shade(ellipse(s, (4, 10, 23, 27)), ['#2a1808', '#4e2e14', '#7a4a22', '#a06a38', '#c89058'])
    s.shade(ellipse(s, (9, 6, 18, 12)), ['#2a1808', '#4e2e14', '#7a4a22', '#a06a38', '#c89058'])
    s.fill(rmask(s, 13, 3, 14, 6), '#4e2e14')
    s.fill(lmask(s, [(22, 16), (27, 12)], 2), '#7a4a22')
    s.fill(lmask(s, [(6, 11), (13, 2), (20, 11)], 1), '#c8a060')
    s.shade(poly(s, [(22, 21), (30, 21), (29, 28), (23, 28)]), PLATE)
    s.fill(rmask(s, 23, 21, 29, 22), '#e0a040')
    steam(s, (24,))


def dish_kanroni(s):
    plate(s, 18)
    s.shade(ellipse(s, (4, 13, 24, 22)), ['#3a1a00', '#7a3a04', '#b8640c', '#e0902a', '#f8c060'])
    s.shade(poly(s, [(23, 17), (29, 12), (29, 22)]), ['#3a1a00', '#7a3a04', '#b8640c', '#e0902a', '#f8c060'])
    s.px(7, 16, '#101010')
    s.fill(lmask(s, [(10, 15), (20, 15)]), (255, 240, 200, 160))
    s.fill(rmask(s, 13, 23, 16, 24), '#6ad04a')


def dish_nanban(s):
    plate(s, 18)
    for (x, y) in ((4, 12), (14, 13)):
        s.shade(ellipse(s, (x, y, x + 12, y + 8)), ['#5a3000', '#a06010', '#d89030', '#f0c060', '#fff0a8'])
    s.fill(lmask(s, [(6, 15), (24, 17)], 1), '#f0a020')
    for (x, y) in ((8, 13), (18, 14), (12, 18), (22, 19)):
        s.fill(lmask(s, [(x, y), (x + 3, y - 1)]), '#f4f0e8')
    s.fill(rmask(s, 20, 11, 22, 12), '#e04020')


def dish_shojin(s):
    s.shade(rmask(s, 1, 10, 30, 28), LACQUER)
    s.fill(rmask(s, 3, 12, 28, 26), '#3a140e')
    for (x, y, ramp) in ((4, 13, BOWL_RED), (17, 13, BOWL_BLUE), (4, 20, BOWL_CLAY), (17, 20, PLATE)):
        s.shade(ellipse(s, (x, y, x + 10, y + 6)), ramp)
    s.fill(rmask(s, 7, 14, 10, 15), '#6ad04a')
    s.fill(rmask(s, 20, 14, 23, 15), '#f4ecd8')
    s.fill(rmask(s, 7, 21, 10, 22), '#f2c94a')
    s.fill(rmask(s, 20, 21, 23, 22), '#e8564a')


def dish_shiraae(s):
    bowl(s, ['#4a4440', '#8a827a', '#c8c0b4', '#e6e0d6', '#fcfaf6'], '#6a5a4a')
    heap(s, (6, 10, 25, 19), CREAM, ((10, 12), (16, 13), (20, 15), (13, 16)), '#4a9a2e')
    s.fill(rmask(s, 17, 11, 18, 12), '#e85a30')


def dish_tsukemono(s):
    plate(s, 17)
    for (x, y, ramp) in ((5, 14, YELLOW), (13, 13, PURPLE), (20, 14, GREEN)):
        for k in range(3):
            s.shade(rmask(s, x + k * 2, y + k, x + k * 2 + 4, y + k + 4), ramp)


def dish_mitarashi(s):
    dango_stick(s, [CREAM, CREAM, CREAM], glaze=True)


def dish_itame(s):
    s.shade(ellipse(s, (2, 14, 29, 28)), ['#141418', '#2a2a30', '#44444e', '#62626e', '#8a8a98'])
    s.fill(lmask(s, [(28, 18), (31, 14)], 2), '#4a2e18')
    heap(s, (5, 13, 26, 22), ['#3a2a10', '#6a4a20', '#9a6a30', '#c89048', '#f0c070'])
    for (x, y, c) in ((8, 15, '#4aa82a'), (13, 14, '#f07a20'), (18, 16, '#4aa82a'), (22, 15, '#f4ecd8'), (11, 18, '#e8564a'), (17, 19, '#6ad04a')):
        s.fill(rmask(s, x, y, x + 2, y + 1), c)
    steam(s, (10, 18))


def dish_karintou(s):
    s.shade(poly(s, [(2, 20), (16, 14), (30, 20), (16, 28)]), CREAM)
    for (x0, y0, x1, y1) in ((7, 18, 15, 13), (12, 19, 22, 15), (16, 21, 25, 18), (9, 22, 18, 20), (14, 16, 20, 11)):
        s.shade(lmask(s, [(x0, y0), (x1, y1)], 3), ['#1a0c04', '#3a1c08', '#6a3610', '#9a5a22', '#c8884a'])


DISHES = [dish_donburi, dish_sushi, dish_zosui, dish_takikomi, dish_dango, dish_yakionigiri, dish_hamayaki, dish_nabe, dish_kushiyaki,
          dish_kakuni, dish_gekikara, dish_tempura, dish_dobinmushi, dish_kanroni, dish_nanban, dish_shojin, dish_shiraae, dish_tsukemono,
          dish_mitarashi, dish_itame, dish_karintou]


def main():
    panel()
    paper()
    inset()
    slot()
    button(0, '#7a5232', '#b0844e', '#4a2e18')
    button(20, '#9a6c3a', '#e0b060', '#5a3a1a', '#1a0e06')
    button(40, '#4a4238', '#625a50', '#2e2822')
    tab(0, '#6e4628', '#e0b060', '#6e4628')
    tab(24, '#3a2618', '#5a3e28', '#c9a050')
    rows()
    for i, fn in enumerate(ICONS):
        s = S(16, 16)
        fn(s)
        s.blit_to((i % 32) * 16, 64 + (i // 32) * 16)
    for i, fn in enumerate(DISHES):
        s = S(32, 32)
        fn(s)
        s.outline('#1a0e08')
        s.blit_to((i % 16) * 32, 96 + (i // 16) * 32)
    if os.path.exists(FACES):
        faces = np.array(Image.open(FACES).convert('RGBA'))
        A[160:184, 0:faces.shape[1]] = faces
    else:
        print('warning: run model_mofu.py first for the mofu faces')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(A, 'RGBA').save(OUT)
    # preview: 2x of the used region on a mid-grey background
    prev = Image.new('RGBA', (512, 192), (90, 90, 96, 255))
    prev.alpha_composite(Image.fromarray(A[:192], 'RGBA'))
    prev.resize((1024, 384), Image.NEAREST).save(os.path.join(ROOT, 'tools', 'preview', 'gui_atlas.png'))
    print('wrote', OUT, len(ICONS), 'icons', len(DISHES), 'dishes')


if __name__ == '__main__':
    main()
