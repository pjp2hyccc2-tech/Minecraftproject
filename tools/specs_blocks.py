"""Block definitions: id, names, kind, properties, textures (painted procedurally) and model geometry."""
from texlib import *

BLOCKS = []

PROPS = {
    'wood': dict(map='WOOD', strength=(2.0, 3.0), sound='WOOD'),
    'darkwood': dict(map='COLOR_BROWN', strength=(2.0, 3.0), sound='WOOD'),
    'stone': dict(map='STONE', strength=(1.5, 6.0), sound='STONE'),
    'concrete': dict(map='COLOR_LIGHT_GRAY', strength=(1.8, 6.0), sound='STONE'),
    'metal': dict(map='METAL', strength=(3.0, 6.0), sound='METAL'),
    'cloth': dict(map='WOOL', strength=(0.5, 0.5), sound='WOOL'),
    'glass': dict(map='NONE', strength=(0.3, 0.3), sound='GLASS'),
    'paper': dict(map='SNOW', strength=(0.4, 0.4), sound='BAMBOO_WOOD'),
    'plant': dict(map='PLANT', strength=(0.0, 0.0), sound='CROP'),
    'mud': dict(map='DIRT', strength=(0.5, 0.5), sound='MUD'),
    'gravel': dict(map='STONE', strength=(0.6, 0.6), sound='GRAVEL'),
    'brick': dict(map='COLOR_RED', strength=(2.0, 6.0), sound='STONE'),
    'tile': dict(map='SAND', strength=(1.5, 6.0), sound='DEEPSLATE_TILES'),
    'lantern': dict(map='COLOR_RED', strength=(0.5, 0.5), sound='WOOL'),
    'plastic': dict(map='COLOR_YELLOW', strength=(0.8, 1.0), sound='BAMBOO_WOOD'),
}


def B(id, ja, en, kind, props='stone', tex=None, **kw):
    d = dict(id=id, ja=ja, en=en, kind=kind, props=dict(PROPS[props]), tex=tex or {}, **kw)
    BLOCKS.append(d)
    return d


# =============================================================== textures

def t_paddy_mud():
    t = Tex(color='#4a3a2a', seed=11)
    t.noise(0.18)
    t.speckle(10, ['#5b4a36', '#3b2d20', '#6a5b47'])
    for _ in range(4):  # wet sheen
        x, y = t.r.randrange(16), t.r.randrange(16)
        t.set(x, y, '#7d7466')
        t.set(x + 1, y, '#6e6556')
    return t


def t_kawara(base='#5d646e', seed=3):
    t = Tex(color=base, seed=seed)
    b = hexc(base)
    for y in range(16):
        row, yy = divmod(y, 4)
        for x in range(16):
            col = (x + (row % 2) * 2) % 4
            f = [1.12, 1.02, 0.88, 0.80][col]
            if yy == 0:
                f *= 0.62
            elif yy == 1:
                f *= 1.08
            t.set(x, y, mul(b, f * (1 + (t.r.random() - 0.5) * 0.05)))
    return t


def t_kawara_green():
    return t_kawara('#2f7d5c', 5)


def t_shikkui():
    t = Tex(color='#eeeae0', seed=4)
    t.noise(0.025)
    t.speckle(6, ['#e2ddd0', '#f6f3ea'])
    return t


def t_hashira_side():
    return planks('#5b3b24', seed=5, vertical=True, board=16, grain=0.16, seam=1.0)


def t_hashira_top():
    t = Tex(color='#6e4a2e', seed=6)
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            t.set(x, y, mul(hexc('#6e4a2e'), 0.85 + 0.15 * math.sin(r * 1.6)))
    t.frame(0, 0, 16, 16, '#4a2f1b')
    return t


def t_yakisugi():
    t = planks('#2f2925', seed=7, vertical=True, board=4, grain=0.25, seam=0.55)
    t.speckle(18, ['#1c1816', '#3d3631', '#463d36'])
    return t


def t_hinoki():
    return planks('#d8b98b', seed=8, board=4, grain=0.08, seam=0.8)


def t_tatami_top():
    t = Tex(color='#bcb874', seed=9)
    for y in range(16):
        for x in range(16):
            f = 1.05 if y % 2 == 0 else 0.93
            t.set(x, y, mul(hexc('#bcb874'), f * (1 + (t.r.random() - 0.5) * 0.06)))
    t.rect(0, 0, 2, 16, '#2a3550')
    t.rect(14, 0, 16, 16, '#2a3550')
    t.vline(1, 0, 16, '#3a4870')
    t.vline(14, 0, 16, '#3a4870')
    t.hline(0, 2, 14, '#9a9658')
    return t


def t_tatami_side():
    t = Tex(color='#8f8557', seed=10)
    t.noise(0.06)
    t.rect(0, 0, 16, 2, '#bcb874')
    return t


def t_shoji():
    t = Tex(color='#f5f2e8', seed=12)
    t.noise(0.02)
    wood = '#b08458'
    for i in (0, 5, 10, 15):
        t.vline(i, 0, 16, wood)
    for j in (0, 4, 8, 12, 15):
        t.hline(j, 0, 16, wood)
    t.rect(0, 13, 16, 16, '#9b7048')
    return t


def t_wood_edge():
    return solid('#a37a4e', 0.06, 13)


def t_fusuma():
    t = Tex(color='#e9dcc0', seed=14)
    t.noise(0.03)
    for _ in range(5):  # gold clouds
        x, y = t.r.randrange(2, 12), t.r.randrange(2, 12)
        for k in range(4):
            t.set(x + k, y, '#d8bf7a')
            t.set(x + k + 1, y + 1, '#e2cd8e')
    t.frame(0, 0, 16, 16, '#3b2a1e')
    t.rect(11, 7, 13, 10, '#3b2a1e')
    t.set(11, 8, '#b08f4a')
    return t


def t_noren(base, mark='#f4f1e6'):
    t = Tex(color=base, seed=15)
    t.noise(0.05)
    ellipse(t, 4, 3, 11, 10, mark)
    ellipse(t, 6, 5, 9, 8, base)
    t.vline(5, 11, 16, mul(hexc(base), 0.6))
    t.vline(10, 11, 16, mul(hexc(base), 0.6))
    t.hline(0, 0, 16, mul(hexc(base), 0.7))
    return t


def t_chochin():
    t = Tex(color='#c8352a', seed=16)
    for y in range(16):
        for x in range(16):
            f = 0.82 if y % 3 == 0 else 1.0
            f *= 1 - abs(x - 7.5) / 30
            t.set(x, y, mul(hexc('#d23b2c'), f))
    t.rect(0, 0, 16, 2, '#1d1d1d')
    t.rect(0, 14, 16, 16, '#1d1d1d')
    return t


def t_lantern_glow():
    t = Tex(color='#fff1b0', seed=17)
    t.noise(0.04)
    return t


def t_stone(base='#9a978f', seed=18):
    t = Tex(color=base, seed=seed)
    t.noise(0.08)
    t.speckle(12, [mul(hexc(base), 0.85), mul(hexc(base), 1.12)])
    return t


def t_bamboo_fence():
    t = Tex(color=(0, 0, 0, 0), seed=19)
    for x in range(0, 16, 2):
        c = hexc('#b8b36a') if (x // 2) % 2 == 0 else hexc('#a6a35c')
        for y in range(16):
            t.set(x, y, mul(c, 1.05))
            t.set(x + 1, y, mul(c, 0.82))
        node = (x * 7) % 5 + 4
        t.set(x, node, mul(c, 0.7))
        t.set(x + 1, node, mul(c, 0.6))
    for y in (4, 11):
        t.rect(0, y, 16, y + 1, '#2a2622')
    return t


def t_zabuton():
    t = Tex(color='#5b3d7a', seed=20)
    t.noise(0.04)
    t.frame(1, 1, 15, 15, '#4a2f66')
    t.set(7, 7, '#e8d7a0')
    t.set(8, 8, '#e8d7a0')
    t.set(8, 7, '#d8c78a')
    t.set(7, 8, '#d8c78a')
    return t


def t_dark_wood():
    return planks('#6a4a30', seed=21, board=4, grain=0.07, seam=0.8)


def t_futon():
    t = Tex(color='#f3f1ea', seed=22)
    t.noise(0.02)
    for y in range(2, 16, 5):
        for x in range(1, 16, 5):
            t.set(x, y, '#9cc1e0')
            t.set(x + 1, y + 1, '#9cc1e0')
    return t


def t_kakebuton():
    t = Tex(color='#9cc1e0', seed=23)
    t.noise(0.03)
    for y in range(1, 16, 4):
        for x in range(1 + (y // 4) % 2 * 2, 16, 4):
            t.set(x, y, '#f4f1e6')
            t.set(x + 1, y, '#e86a7a')
            t.set(x, y + 1, '#e86a7a')
    return t


def t_pillow():
    t = Tex(color='#e7dcc2', seed=24)
    t.noise(0.05)
    return t


def t_kakejiku():
    t = Tex(color='#6b4a2b', seed=25)
    t.rect(2, 1, 14, 15, '#efe6cf')
    t.noise(0.02, 2, 1, 14, 15)
    poly(t, [(2, 13), (6, 7), (9, 11), (11, 8), (14, 13)], '#4a4a4a')
    poly(t, [(4, 13), (6, 9), (8, 13)], '#2e2e2e')
    ellipse(t, 9, 3, 12, 6, '#c8352a')
    t.rect(0, 0, 16, 1, '#2b1c10')
    t.rect(0, 15, 16, 16, '#2b1c10')
    return t


def t_fan_front():
    t = Tex(color=(0, 0, 0, 0), seed=26)
    ellipse(t, 0, 0, 15, 15, '#d9e8f0')
    ellipse(t, 1, 1, 14, 14, (40, 60, 80, 60))
    for a in range(3):
        ang = a * 2.094
        for r in range(1, 6):
            x = 7.5 + math.cos(ang) * r
            y = 7.5 + math.sin(ang) * r
            t.set(int(x), int(y), '#7fb0d0')
            t.set(int(x + math.cos(ang + 1.57)), int(y + math.sin(ang + 1.57)), '#8fc0e0')
    ellipse(t, 6, 6, 9, 9, '#3a6f9a')
    for i in range(0, 16, 3):
        t.vline(i, 1, 15, (210, 225, 235, 140))
    return round_mask(t)


def t_plastic(c='#d9e8f0', seed=27):
    return solid(c, 0.03, seed)


def t_furin():
    t = Tex(color=(0, 0, 0, 0), seed=28)
    t.vline(7, 0, 3, '#555555')
    ellipse(t, 3, 2, 12, 10, (200, 230, 250, 170))
    t.rect(3, 7, 13, 10, (200, 230, 250, 170))
    t.set(6, 6, '#e0403a')
    t.set(7, 6, '#e0403a')
    t.set(8, 7, '#e0403a')
    t.set(9, 5, '#3a8ad0')
    t.vline(7, 10, 12, '#555555')
    t.rect(6, 12, 9, 16, '#f0d070')
    t.set(7, 14, '#c8352a')
    return t


def t_tansu_front():
    t = planks('#5a3a24', seed=29, board=16, grain=0.08, seam=1.0)
    for y in (0, 5, 10, 15):
        t.hline(y, 0, 16, '#3a2414')
    t.vline(0, 0, 16, '#3a2414')
    t.vline(15, 0, 16, '#3a2414')
    t.vline(8, 0, 5, '#3a2414')
    for (x, y) in ((4, 2), (11, 2), (7, 7), (7, 12)):
        t.rect(x, y, x + 2, y + 1, '#1a1a1a')
        t.set(x, y + 1, '#2a2a2a')
    return t


def t_post_red(front=False):
    t = Tex(color='#c8102e', seed=30)
    for y in range(16):
        for x in range(16):
            t.set(x, y, mul(hexc('#c8102e'), 0.85 + 0.25 * math.sin((x + 2) / 16 * math.pi)))
    t.rect(0, 13, 16, 16, '#222222')
    if front:
        t.rect(4, 3, 12, 4, '#202020')  # mail slot
        t.hline(6, 6, 11, '#ffffff')
        t.hline(8, 6, 11, '#ffffff')
        t.vline(8, 8, 12, '#ffffff')
    return t


def t_vending_front():
    t = Tex(color='#f2f2f2', seed=31)
    t.rect(1, 1, 15, 7, '#2c3440')
    cols = ['#d8392b', '#2f6fb3', '#f0c419', '#3aa655', '#8e44ad', '#e67e22', '#ecf0f1']
    for i in range(6):
        t.rect(2 + i * 2, 2, 3 + i * 2, 5, t.r.choice(cols))
        t.set(2 + i * 2, 5, '#9aff9a')
    for i in range(6):
        t.set(2 + i * 2, 6, '#ff5040')
    t.rect(11, 8, 14, 11, '#9aa3ad')
    t.set(12, 9, '#202020')
    t.rect(2, 12, 14, 15, '#1d1f22')
    t.frame(0, 0, 16, 16, '#c8ccd0')
    return t


def t_vending_top_front():
    t = Tex(color='#f2f2f2', seed=32)
    t.rect(1, 1, 15, 15, '#d8e9f5')
    cols = ['#d8392b', '#2f6fb3', '#f0c419', '#3aa655', '#8e44ad', '#e67e22', '#795548', '#ecf0f1']
    for row in range(3):
        y = 2 + row * 5
        for i in range(6):
            c = t.r.choice(cols)
            t.rect(2 + i * 2, y, 3 + i * 2, y + 3, c)
            t.set(2 + i * 2, y, mul(hexc(c), 1.3))
        t.hline(y + 3, 2, 14, '#ff6040')
    t.frame(0, 0, 16, 16, '#c8ccd0')
    return t


def t_vending_side():
    t = Tex(color='#e8e8e8', seed=33)
    t.noise(0.02)
    t.rect(0, 0, 16, 3, '#d8392b')
    return t


def t_concrete(base='#a3a39e', seed=34):
    t = Tex(color=base, seed=seed)
    t.noise(0.05)
    t.speckle(8, [mul(hexc(base), 0.9), mul(hexc(base), 1.08)])
    return t


def t_asphalt():
    t = Tex(color='#3d3f42', seed=35)
    t.noise(0.12)
    t.speckle(16, ['#55585c', '#2d2f31', '#4a4c4f'])
    return t


def t_road_edge():
    t = t_asphalt()
    t.rect(12, 0, 15, 16, '#e4e4dc')
    t.noise(0.05, 12, 0, 15, 16)
    return t


def t_road_center():
    t = t_asphalt()
    t.rect(7, 0, 9, 10, '#e4e4dc')
    t.noise(0.05, 7, 0, 9, 10)
    return t


def t_road_center_solid():
    t = t_asphalt()
    t.rect(6, 0, 8, 16, '#e8c018')
    t.rect(9, 0, 11, 16, '#e8c018')
    return t


def t_crosswalk():
    t = t_asphalt()
    t.rect(0, 0, 16, 8, '#e4e4dc')
    t.noise(0.06, 0, 0, 16, 8)
    return t


def t_sidewalk():
    t = Tex(color='#8d8b86', seed=36)
    for y in range(16):
        for x in range(16):
            bx, by = x // 8, y // 4
            red = (bx + by) % 3 == 0
            c = '#b5604c' if red else '#a6a49e'
            if x % 8 == 7 or y % 4 == 3:
                c = '#7d7b76'
            t.set(x, y, mul(hexc(c), 1 + (t.r.random() - 0.5) * 0.08))
    return t


def t_tenji_line():
    t = Tex(color='#e8c018', seed=37)
    t.noise(0.04)
    for x in (2, 6, 10, 14):
        t.rect(x - 1, 1, x + 1, 15, '#f6d84a')
        t.vline(x + 1, 1, 15, '#b8960e')
    return t


def t_tenji_dots():
    t = Tex(color='#e8c018', seed=38)
    t.noise(0.04)
    for y in (2, 6, 10, 14):
        for x in (2, 6, 10, 14):
            t.set(x - 1, y - 1, '#fbe27a')
            t.set(x, y - 1, '#f6d84a')
            t.set(x - 1, y, '#f6d84a')
            t.set(x, y, '#b8960e')
    return t


def t_guardrail():
    t = Tex(color='#eceeef', seed=39)
    for y in range(16):
        f = [0.82, 0.95, 1.05, 1.0, 0.9, 0.8, 0.95, 1.05, 1.0, 0.9, 0.82, 0.95, 1.05, 1.0, 0.9, 0.8][y]
        for x in range(16):
            t.set(x, y, mul(hexc('#eceeef'), f))
    return t


def t_block_wall():
    t = bricks('#a9a8a2', '#8c8b85', seed=40, bw=8, bh=4, var=0.05)
    t.noise(0.04)
    return t


def t_ishigaki():
    return stones('#8f8a7e', '#5e5a52', seed=41, n=8)


def t_ballast():
    return gravel(['#8d8a84', '#77746f', '#a29f98', '#6a6762'], seed=42)


def t_rail_side():
    t = Tex(color='#5e5853', seed=43)
    t.noise(0.08)
    t.hline(0, 0, 16, '#b9b6b0')
    return t


def t_sleeper():
    t = Tex(color='#a9a69e', seed=44)
    t.noise(0.06)
    return t


def t_platform():
    t = tiles('#b9b8b2', '#a09f99', size=8, seed=45, var=0.04)
    return t


def t_platform_edge():
    t = t_platform()
    t.rect(0, 0, 16, 4, '#c9c8c2')
    t.hline(1, 0, 16, '#f2f2f2')
    t.rect(0, 4, 16, 9, '#e8c018')
    for x in range(1, 16, 3):
        for y in (5, 7):
            t.set(x, y, '#fbe27a')
            t.set(x + 1, y, '#b8960e')
    return t


def t_bench_blue():
    t = Tex(color='#2f6fb3', seed=46)
    t.noise(0.04)
    return t


def t_metal(base='#9aa0a6', seed=47):
    t = Tex(color=base, seed=seed)
    for y in range(16):
        for x in range(16):
            t.set(x, y, mul(hexc(base), 0.95 + 0.1 * ((x + y) % 7) / 7))
    t.noise(0.03)
    return t


def t_kenbaiki_front():
    t = Tex(color='#d8dce0', seed=48)
    t.rect(1, 1, 15, 9, '#1f3550')
    t.rect(2, 2, 14, 8, '#7fc7ff')
    for i, c in enumerate(['#ffffff', '#ffffff', '#ffe08a', '#ffffff']):
        t.rect(3 + i * 3, 3, 5 + i * 3, 5, c)
        t.rect(3 + i * 3, 6, 5 + i * 3, 7, '#2a7fd0')
    t.rect(3, 10, 6, 11, '#2a2a2a')
    t.rect(9, 10, 13, 12, '#4a4f55')
    t.rect(4, 13, 12, 15, '#2a2a2a')
    t.frame(0, 0, 16, 16, '#9aa3ad')
    return t


def t_kenbaiki_top():
    t = Tex(color='#f4f4f0', seed=49)
    t.rect(0, 0, 16, 3, '#1c7a3e')
    t.hline(9, 1, 15, '#2fa05a')
    t.hline(10, 1, 15, '#2fa05a')
    for x in (2, 6, 10, 13):
        t.rect(x, 8, x + 2, 12, '#ffffff')
        t.frame(x, 8, x + 2, 12, '#1c7a3e')
    for x in range(2, 14, 3):
        t.set(x, 13, '#c0392b')
        t.set(x + 1, 13, '#c0392b')
    return t


def t_kaisatsu_top():
    t = Tex(color='#2a64b0', seed=50)
    t.noise(0.04)
    t.rect(4, 2, 12, 6, '#9fd3ff')
    t.rect(6, 10, 10, 13, '#202428')
    return t


def t_kaisatsu_side():
    t = Tex(color='#c9ccd0', seed=51)
    t.noise(0.03)
    t.rect(0, 0, 16, 3, '#2a64b0')
    t.rect(10, 5, 14, 7, '#7cfc9a')
    return t


def t_board(field='#ffffff', band=None, band_y=12, seed=52):
    t = Tex(color=field, seed=seed)
    t.noise(0.01)
    if band:
        t.rect(0, band_y, 16, 16, band)
    t.hline(0, 0, 16, '#c8c8c8')
    return t


def t_board_back():
    return t_metal('#8a9096', 53)


def t_tile(base, grout, size=4, seed=54):
    return tiles(base, grout, size=size, seed=seed)


def t_renga():
    t = Tex(color='#cfc6b6', seed=55)
    b = hexc('#8e3b2a')
    for row in range(4):
        y0 = row * 4
        if row % 2 == 0:  # stretchers
            for k in range(3):
                x0 = k * 8 - 4
                f = 1 + (t.r.random() - 0.5) * 0.24
                t.rect(max(0, x0), y0, min(16, x0 + 7), y0 + 3, mul(b, f))
        else:  # headers
            for k in range(4):
                x0 = k * 4
                f = 1 + (t.r.random() - 0.5) * 0.24
                t.rect(x0, y0, x0 + 3, y0 + 3, mul(b, f))
    t.noise(0.07)
    return t


def t_clapboard(base, seed):
    t = Tex(color=base, seed=seed)
    b = hexc(base)
    for y in range(16):
        k = y % 4
        f = [0.72, 1.06, 1.0, 0.94][k]
        for x in range(16):
            t.set(x, y, mul(b, f * (1 + (t.r.random() - 0.5) * 0.03)))
    return t


def t_weathercock():
    t = Tex(color=(0, 0, 0, 0), seed=56)
    gold, dark = '#d8a630', '#8c6a1c'
    t.vline(7, 6, 16, dark)
    t.hline(12, 3, 12, dark)
    for (x, y) in ((3, 11), (3, 13), (11, 11), (11, 13)):
        t.set(x, y, dark)
    # rooster
    poly(t, [(4, 4), (9, 3), (11, 1), (12, 3), (11, 5), (9, 7), (5, 7), (3, 5)], gold)
    t.set(11, 1, '#c8352a')
    t.set(12, 2, '#c8352a')
    poly(t, [(2, 2), (4, 4), (3, 6), (1, 5)], gold)
    t.set(10, 3, dark)
    t.set(6, 7, dark)
    t.set(8, 7, dark)
    return t


def t_sash_window():
    t = Tex(color=(170, 205, 225, 120), seed=57)
    for x in (0, 7, 8, 15):
        t.vline(x, 0, 16, '#f4f4f0')
    for y in (0, 7, 8, 15):
        t.hline(y, 0, 16, '#f4f4f0')
    for k in range(4):
        t.set(2 + k, 5 - k, (230, 245, 255, 160))
        t.set(10 + k, 13 - k, (230, 245, 255, 160))
    return t


def t_shu(seed=58):
    t = Tex(color='#c02c1e', seed=seed)
    for y in range(16):
        for x in range(16):
            t.set(x, y, mul(hexc('#c43020'), 0.9 + 0.18 * math.sin(x / 15 * math.pi)))
    t.noise(0.02)
    return t


def t_china_gold():
    t = Tex(color='#d8a630', seed=59)
    t.noise(0.05)
    for i in range(0, 16, 4):
        t.frame(i, i % 8, i + 4, i % 8 + 4, '#9c2a1c')
    t.hline(0, 0, 16, '#9c2a1c')
    t.hline(15, 0, 16, '#9c2a1c')
    return t


def t_red_steel():
    t = Tex(color='#c62a22', seed=60)
    t.noise(0.04)
    t.hline(15, 0, 16, '#9a1e18')
    t.vline(15, 0, 16, '#a8221c')
    return t


def t_white_steel():
    t = Tex(color='#f0f0ec', seed=61)
    t.noise(0.02)
    t.hline(15, 0, 16, '#d0d0cc')
    return t


def t_window_lit(cool=False, seed=62):
    lit = ['#ffe3a0', '#fff1c8', '#ffd27a', '#3a3f48'] if not cool else ['#e8f4ff', '#d4eaff', '#f6fbff', '#3a4250']
    return window_grid('#3b3f45', '#20252c', lit=lit, seed=seed, cols=2, rows=2)


def t_window_dark():
    return window_grid('#4a5058', '#26364a', seed=63, cols=2, rows=2)


def t_office_wall():
    t = Tex(color='#b9b7af', seed=64)
    t.noise(0.03)
    t.hline(15, 0, 16, '#9a988f')
    t.vline(15, 0, 16, '#a3a198')
    return t


def t_lamp_head():
    t = Tex(color='#4a4f55', seed=65)
    t.noise(0.04)
    return t


def t_gas_glass():
    t = Tex(color=(255, 214, 130, 220), seed=66)
    for x in (0, 15):
        t.vline(x, 0, 16, '#1e1e1e')
    t.hline(0, 0, 16, '#1e1e1e')
    t.hline(15, 0, 16, '#1e1e1e')
    ellipse(t, 5, 6, 10, 12, '#fff6d8')
    return t


def t_lock_fence():
    t = Tex(color=(0, 0, 0, 0), seed=67)
    for x in (0, 4, 8, 12):
        t.vline(x, 0, 16, '#8f969c')
    t.hline(1, 0, 16, '#b8bec4')
    t.hline(14, 0, 16, '#b8bec4')
    cols = ['#d8392b', '#f0c419', '#e88aa8', '#2f6fb3', '#c0c0c0', '#d8a630', '#3aa655']
    for _ in range(9):
        x, y = t.r.randrange(1, 14), t.r.randrange(3, 12)
        c = t.r.choice(cols)
        t.rect(x, y + 1, x + 2, y + 3, c)
        t.set(x, y, '#9aa0a6')
        t.set(x + 1, y, '#9aa0a6')
    return t


def t_railing():
    t = Tex(color=(0, 0, 0, 0), seed=68)
    t.hline(0, 0, 16, '#d9dcdf')
    t.hline(1, 0, 16, '#b4b8bc')
    t.hline(9, 0, 16, '#b4b8bc')
    for x in (1, 5, 9, 13):
        t.vline(x, 1, 16, '#9aa0a6')
    return t


def t_sudare():
    t = Tex(color=(0, 0, 0, 0), seed=69)
    for y in range(0, 16):
        if y % 2 == 0:
            c = mul(hexc('#c9b07a'), 1 + (t.r.random() - 0.5) * 0.1)
            t.rect(0, y, 16, y + 1, c)
    for x in (3, 12):
        t.vline(x, 0, 16, '#6a4a2a')
    return t


def t_showcase_front():
    t = Tex(color='#cfd5da', seed=70)
    t.rect(1, 1, 15, 13, (220, 238, 248, 255))
    cakes = ['#f6e7c8', '#f4c2c2', '#6b3a2a', '#fff3d6', '#e8d39a']
    for i in range(3):
        x = 2 + i * 4
        c = t.r.choice(cakes)
        t.rect(x, 4, x + 3, 7, c)
        t.set(x + 1, 3, '#d8392b')
        c = t.r.choice(cakes)
        t.rect(x, 9, x + 3, 12, c)
        t.set(x + 2, 8, '#3aa655')
    t.hline(7, 1, 15, '#b8c0c8')
    t.rect(0, 13, 16, 16, '#5a4636')
    return t


def t_shelf_front(items, seed):
    t = planks('#8a6440', seed=seed, board=16, grain=0.06, seam=1.0)
    t.rect(1, 1, 15, 15, '#3a2a1c')
    for row in range(3):
        y = 2 + row * 5
        t.hline(y + 3, 1, 15, '#8a6440')
        x = 2
        while x < 14:
            c = t.r.choice(items)
            w = t.r.choice([2, 3])
            t.rect(x, y, min(14, x + w), y + 3, c)
            t.set(x, y, mul(hexc(c), 1.2))
            x += w + 1
    return t


def t_bamboo_weave():
    t = Tex(color='#c8a868', seed=71)
    for y in range(16):
        for x in range(16):
            f = 1.08 if (x + y) % 4 < 2 else 0.92
            t.set(x, y, mul(hexc('#c8a868'), f))
    t.hline(0, 0, 16, '#8c6a3a')
    t.hline(5, 0, 16, '#8c6a3a')
    t.hline(10, 0, 16, '#8c6a3a')
    return t


def t_jizo_front():
    t = t_stone('#8f8c84', 72)
    poly(t, [(2, 0), (14, 0), (11, 7), (5, 7)], '#c8352a')
    t.hline(0, 2, 14, '#9c2a1c')
    return t


def t_crate(veg=True):
    t = Tex(color='#e8b818', seed=73)
    t.noise(0.04)
    t.frame(0, 0, 16, 16, '#c89a10')
    if veg:
        t.rect(1, 1, 15, 15, '#7a5a10')
        for _ in range(9):
            x, y = t.r.randrange(1, 13), t.r.randrange(1, 13)
            k = t.r.randrange(3)
            if k == 0:
                ellipse(t, x, y, x + 2, y + 2, '#d8392b')
                t.set(x + 1, y, '#3aa655')
            elif k == 1:
                t.rect(x, y, x + 4, y + 1, '#3f8a2e')
                t.set(x, y, '#5aa840')
            else:
                t.rect(x, y, x + 3, y + 2, '#5b2d6e')
                t.set(x + 3, y, '#3aa655')
    else:
        for y in range(2, 14, 3):
            t.hline(y, 2, 14, '#c89a10')
    return t


def t_crate_side():
    t = Tex(color='#e8b818', seed=74)
    t.noise(0.04)
    t.rect(5, 3, 11, 5, '#a07a08')
    for x in range(1, 16, 3):
        t.vline(x, 7, 15, '#c89a10')
    return t


def t_phone():
    t = Tex(color='#1c1c1e', seed=75)
    t.noise(0.08)
    ellipse(t, 4, 4, 11, 11, '#e8e8e0')
    ellipse(t, 6, 6, 9, 9, '#1c1c1e')
    return t


def t_elevator_button():
    t = t_metal('#b8bec4', 76)
    t.rect(4, 2, 12, 14, '#2a2f35')
    poly(t, [(8, 3), (11, 7), (5, 7)], '#ffd27a')
    poly(t, [(5, 9), (11, 9), (8, 13)], '#e8e8e8')
    return t


def t_rice(age):
    t = Tex(color=(0, 0, 0, 0), seed=80 + age)
    h = [10, 14, 15, 15][age]
    greens = ['#6fae3a', '#5c9a2e', '#7fbe46']
    for k in range(7):
        x = 1 + k * 2 + t.r.randrange(2)
        top = 16 - h + t.r.randrange(3)
        c = hexc('#c9a838') if age == 3 else hexc(t.r.choice(greens))
        lean = t.r.choice([-1, 0, 1])
        for y in range(top, 16):
            xx = x + (lean if y < top + 3 else 0)
            t.set(xx, y, mul(c, 0.8 + 0.3 * (16 - y) / 16))
        if age >= 2:
            ear = '#e0c050' if age == 3 else '#a8c060'
            for j in range(3):
                t.set(x + lean + (1 if age == 3 else 0), top + j, ear)
    return t


def t_crop(kind, stage):
    t = Tex(color=(0, 0, 0, 0), seed=90 + stage + hash(kind) % 50)
    leaf = ['#4f8a2e', '#5f9a3a', '#3f7a26']
    # support stick for tomato/cucumber
    if kind in ('tomato', 'cucumber') and stage >= 1:
        t.vline(3, 2, 16, '#a88a5a')
        t.vline(12, 2, 16, '#a88a5a')
    hgt = [5, 9, 13, 14][stage]
    for k in range(10):
        x = t.r.randrange(2, 14)
        y = t.r.randrange(16 - hgt, 16)
        c = t.r.choice(leaf)
        t.set(x, y, c)
        t.set(x + 1, y, mul(hexc(c), 0.85))
        t.set(x, y + 1, mul(hexc(c), 0.75))
    t.vline(7, 16 - hgt, 16, '#3f6a20')
    t.vline(8, 16 - hgt + 2, 16, '#3f6a20')
    if stage == 3:
        fruit = {'tomato': '#d8392b', 'cucumber': '#3f8a2e', 'eggplant': '#4b2a5e'}[kind]
        for (x, y) in ((4, 6), (10, 8), (6, 11), (11, 12)):
            if kind == 'cucumber':
                t.rect(x, y, x + 1, y + 4, fruit)
                t.set(x, y, '#f0d050')
            elif kind == 'eggplant':
                t.rect(x, y, x + 2, y + 3, fruit)
                t.set(x, y, '#3a6a20')
            else:
                t.rect(x, y, x + 2, y + 2, fruit)
                t.set(x, y, '#ff7060')
    return t


# =============================================================== block list
# kinds: cube, cube_bt(top,side,bottom), facing_cube(front,side,top), pillar(side,end), stairs/slab(of), pane(pane,edge),
#        shaped(elements, facing?), cross, crop, rice, seat, futon, action, panel
G = 'cutout'

B('paddy_mud', '田んぼの泥', 'Paddy Mud', 'cube', 'mud', {'all': t_paddy_mud})
B('rice_plant', '稲', 'Rice Plant', 'rice', 'plant', {f'rice_{i}': (lambda i=i: t_rice(i)) for i in range(4)}, item=False)
for crop in ('tomato', 'cucumber', 'eggplant'):
    B(f'{crop}_crop', {'tomato': 'トマトの苗', 'cucumber': 'きゅうりの苗', 'eggplant': 'なすの苗'}[crop],
      f'{crop.title()} Plant', 'crop', 'plant', {f'{crop}_{i}': (lambda i=i, c=crop: t_crop(c, i)) for i in range(4)}, item=False, seed=f'{crop}_seeds')

B('kawara', 'いぶし瓦', 'Kawara Roof Tiles', 'cube', 'tile', {'all': t_kawara})
B('kawara_stairs', 'いぶし瓦の階段', 'Kawara Roof Stairs', 'stairs', 'tile', of='kawara')
B('kawara_slab', 'いぶし瓦のハーフ', 'Kawara Roof Slab', 'slab', 'tile', of='kawara')
B('green_kawara', '緑の瓦', 'Green Glazed Tiles', 'cube', 'tile', {'all': t_kawara_green})
B('green_kawara_stairs', '緑の瓦の階段', 'Green Glazed Stairs', 'stairs', 'tile', of='green_kawara')
B('green_kawara_slab', '緑の瓦のハーフ', 'Green Glazed Slab', 'slab', 'tile', of='green_kawara')
B('shikkui', '漆喰の壁', 'Plaster Wall', 'cube', 'concrete', {'all': t_shikkui})
B('hashira', '柱', 'Wooden Post', 'pillar', 'darkwood', {'side': t_hashira_side, 'end': t_hashira_top})
B('yakisugi', '焼杉板', 'Charred Cedar Siding', 'cube', 'darkwood', {'all': t_yakisugi})
B('hinoki', '檜の床板', 'Hinoki Flooring', 'cube', 'wood', {'all': t_hinoki})
B('hinoki_slab', '檜のハーフ', 'Hinoki Slab', 'slab', 'wood', of='hinoki')
B('hinoki_stairs', '檜の階段', 'Hinoki Stairs', 'stairs', 'wood', of='hinoki')
B('tatami', '畳', 'Tatami', 'facing_cube', 'cloth', {'top': t_tatami_top, 'side': t_tatami_side}, front='side', bottom='side')
B('shoji', '障子', 'Shoji Screen', 'pane', 'paper', {'pane': t_shoji, 'edge': t_wood_edge}, render=G)
B('fusuma', '襖', 'Fusuma', 'pane', 'paper', {'pane': t_fusuma, 'edge': t_wood_edge}, render=G)
B('bamboo_fence', '竹垣', 'Bamboo Fence', 'pane', 'wood', {'pane': t_bamboo_fence, 'edge': lambda: solid('#a6a35c', 0.05, 3)}, render=G)
B('noren_indigo', '藍の暖簾', 'Indigo Noren', 'shaped', 'cloth', {'cloth': lambda: t_noren('#22355e')},
  elements=[[0, 0, 7.5, 16, 16, 8.5, 'cloth']], nocollide=True, render=G)
B('noren_red', '赤い暖簾', 'Red Noren', 'shaped', 'cloth', {'cloth': lambda: t_noren('#a8312a')},
  elements=[[0, 0, 7.5, 16, 16, 8.5, 'cloth']], nocollide=True, render=G)
B('sudare', 'すだれ', 'Sudare Blind', 'shaped', 'wood', {'s': t_sudare}, elements=[[0, 0, 15, 16, 16, 15.5, 's']], nocollide=True, render=G)
B('chochin', '提灯', 'Paper Lantern', 'shaped', 'lantern', {'side': t_chochin, 'cap': lambda: solid('#1d1d1d', 0.05, 2)},
  elements=[[4, 2, 4, 12, 13, 12, {'n': 'side', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'cap', 'd': 'cap'}],
            [5, 13, 5, 11, 14, 11, 'cap'], [5, 1, 5, 11, 2, 11, 'cap'], [7.5, 14, 7.5, 8.5, 16, 8.5, 'cap']],
  light=13, nocollide=True, facing=False)
B('stone_lantern', '石灯籠', 'Stone Lantern', 'shaped', 'stone', {'s': t_stone, 'glow': t_lantern_glow},
  elements=[[3, 0, 3, 13, 2, 13, 's'], [6, 2, 6, 10, 8, 10, 's'], [4, 8, 4, 12, 9, 12, 's'],
            [5, 9, 5, 11, 12, 11, {'n': 'glow', 's': 'glow', 'e': 'glow', 'w': 'glow', 'u': 's', 'd': 's'}],
            [2, 12, 2, 14, 14, 14, 's'], [5, 14, 5, 11, 15, 11, 's'], [7, 15, 7, 9, 16, 9, 's']], light=7, facing=False)
B('zabuton', '座布団', 'Zabuton Cushion', 'seat', 'cloth', {'c': t_zabuton}, elements=[[1, 0, 1, 15, 3, 15, 'c']], seat=0.22)
B('chabudai', 'ちゃぶ台', 'Chabudai Table', 'shaped', 'wood', {'top': t_dark_wood},
  elements=[[0, 7, 0, 16, 9, 16, 'top'], [2, 0, 2, 4, 7, 4, 'top'], [12, 0, 2, 14, 7, 4, 'top'], [2, 0, 12, 4, 7, 14, 'top'], [12, 0, 12, 14, 7, 14, 'top']], facing=False)
B('futon', '布団', 'Futon', 'futon', 'cloth', {'f': t_futon, 'p': t_pillow},
  elements=[[0, 0, 0, 16, 3, 16, 'f'], [3, 3, 1, 13, 5, 6, 'p']])
B('kakebuton', '掛け布団', 'Kakebuton Quilt', 'futon', 'cloth', {'f': t_kakebuton}, elements=[[0, 0, 0, 16, 4, 16, 'f']])
B('kakejiku', '掛け軸', 'Hanging Scroll', 'shaped', 'paper', {'k': t_kakejiku}, elements=[[2, 0, 15, 14, 16, 16, 'k']], nocollide=True)
B('senpuki', '扇風機', 'Electric Fan', 'shaped', 'plastic', {'front': t_fan_front, 'body': t_plastic},
  elements=[[4, 0, 5, 12, 1, 11, 'body'], [7, 1, 7, 9, 8, 9, 'body'], [3, 7, 6, 13, 15, 8, {'n': 'front', 's': 'front', 'e': 'body', 'w': 'body', 'u': 'body', 'd': 'body'}],
            [6, 10, 8, 10, 12, 10, 'body']], render=G)
B('furin', '風鈴', 'Wind Chime', 'cross', 'glass', {'cross': t_furin}, render='translucent')
B('tansu', '箪笥', 'Tansu Chest', 'facing_cube', 'darkwood', {'front': t_tansu_front, 'side': t_dark_wood}, top='side', bottom='side')
B('phone', '黒電話', 'Black Telephone', 'action', 'metal', {'p': t_phone},
  elements=[[4, 0, 5, 12, 3, 11, 'p'], [3, 3, 7, 13, 5, 9, 'p']], action='talk:dad')
B('mailbox', '郵便ポスト', 'Post Box', 'shaped', 'metal', {'front': lambda: t_post_red(True), 'side': t_post_red},
  elements=[[3, 0, 3, 13, 15, 13, {'n': 'front', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'side', 'd': 'side'}], [2, 15, 2, 14, 16, 14, 'side']])
B('vending_machine', '自動販売機', 'Vending Machine', 'action', 'metal', {'front': t_vending_front, 'side': t_vending_side},
  elements=[[0, 0, 1, 16, 16, 16, {'n': 'front', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'side', 'd': 'side'}]], action='shop:vending')
B('vending_machine_top', '自動販売機（上）', 'Vending Machine Top', 'facing_cube', 'metal', {'front': t_vending_top_front, 'side': t_vending_side}, light=8, top='side', bottom='side')
B('utility_pole', '電柱', 'Utility Pole', 'shaped', 'concrete', {'c': t_concrete}, elements=[[5, 0, 5, 11, 16, 11, 'c']], facing=False)
B('utility_pole_top', '電柱（腕金）', 'Utility Pole Crossarm', 'shaped', 'concrete', {'c': t_concrete, 'm': lambda: t_metal('#6a6f74', 3), 'w': lambda: solid('#e8e8e0', 0.03, 4)},
  elements=[[5, 0, 5, 11, 16, 11, 'c'], [0, 11, 7, 16, 13, 9, 'm'], [1, 13, 7.5, 2, 15, 8.5, 'w'], [14, 13, 7.5, 15, 15, 8.5, 'w'], [7.5, 13, 7.5, 8.5, 15, 8.5, 'w']])
B('sign_pole', '標識の柱', 'Sign Pole', 'shaped', 'metal', {'m': lambda: t_metal('#8a8f94', 5)}, elements=[[7, 0, 7, 9, 16, 9, 'm']], facing=False)
B('lamp_pole', '街灯の柱', 'Lamp Post', 'shaped', 'metal', {'m': lambda: t_metal('#3e4247', 6)}, elements=[[6.5, 0, 6.5, 9.5, 16, 9.5, 'm']], facing=False)
B('street_lamp', '街灯', 'Street Lamp', 'shaped', 'metal', {'m': t_lamp_head, 'g': t_lantern_glow},
  elements=[[6.5, 0, 6.5, 9.5, 8, 9.5, 'm'], [7, 8, 0, 9, 9, 9, 'm'], [5, 7, -2, 11, 9, 4, {'n': 'm', 's': 'm', 'e': 'm', 'w': 'm', 'u': 'm', 'd': 'g'}]], light=15)
B('gas_lamp', 'ガス燈', 'Gas Lamp', 'shaped', 'metal', {'g': t_gas_glass, 'm': lambda: solid('#1e1e1e', 0.05, 7)},
  elements=[[6.5, 0, 6.5, 9.5, 3, 9.5, 'm'], [5, 3, 5, 11, 11, 11, 'g'], [4, 11, 4, 12, 12, 12, 'm'], [6, 12, 6, 10, 14, 10, 'm'], [7.5, 14, 7.5, 8.5, 16, 8.5, 'm']],
  light=14, facing=False, render='translucent')
B('asphalt', 'アスファルト', 'Asphalt', 'cube', 'concrete', {'all': t_asphalt})
B('asphalt_slab', 'アスファルト（ハーフ）', 'Asphalt Slab', 'slab', 'concrete', of='asphalt')
B('road_line', '道路の白線', 'Road Edge Line', 'facing_cube', 'concrete', {'top': t_road_edge, 'side': t_asphalt}, front='side', bottom='side')
B('road_center', '道路のセンターライン', 'Road Centre Line', 'facing_cube', 'concrete', {'top': t_road_center, 'side': t_asphalt}, front='side', bottom='side')
B('road_center_yellow', '黄色いセンターライン', 'Yellow Centre Line', 'facing_cube', 'concrete', {'top': t_road_center_solid, 'side': t_asphalt}, front='side', bottom='side')
B('crosswalk', '横断歩道', 'Crosswalk', 'facing_cube', 'concrete', {'top': t_crosswalk, 'side': t_asphalt}, front='side', bottom='side')
B('sidewalk', '歩道のタイル', 'Sidewalk Pavers', 'cube', 'concrete', {'all': t_sidewalk})
B('tenji_line', '点字ブロック（誘導）', 'Tactile Paving (Guide)', 'facing_cube', 'concrete', {'top': t_tenji_line, 'side': lambda: solid('#c9a414', 0.04, 9)}, front='side', bottom='side')
B('tenji_dots', '点字ブロック（警告）', 'Tactile Paving (Warning)', 'cube_bt', 'concrete', {'top': t_tenji_dots, 'side': lambda: solid('#c9a414', 0.04, 9)})
B('guardrail', 'ガードレール', 'Guardrail', 'shaped', 'metal', {'r': t_guardrail, 'p': lambda: t_metal('#c8ccd0', 8)},
  elements=[[0, 8, 6, 16, 14, 7.5, 'r'], [7, 0, 7.5, 9, 13, 9.5, 'p']])
B('block_wall', 'ブロック塀', 'Concrete Block Wall', 'cube', 'concrete', {'all': t_block_wall})
B('ishigaki', '石垣', 'Stone Wall', 'cube', 'stone', {'all': t_ishigaki})
B('ballast', 'バラスト', 'Track Ballast', 'cube', 'gravel', {'all': t_ballast})
B('ballast_slab', 'バラスト（ハーフ）', 'Ballast Slab', 'slab', 'gravel', of='ballast')
B('track', '線路', 'Railway Track', 'shaped', 'gravel', {'b': t_ballast, 's': t_sleeper, 'r': t_rail_side},
  elements=[[0, 0, 0, 16, 8, 16, 'b'], [-3, 8, 1, 19, 10, 5, 's'], [-3, 8, 9, 19, 10, 13, 's'], [0.5, 10, 0, 1.5, 13, 16, 'r'], [14.5, 10, 0, 15.5, 13, 16, 'r']],
  shape=[0, 0, 0, 16, 10, 16])
B('track_low', '線路（低）', 'Railway Track (low)', 'shaped', 'gravel', {'s': t_sleeper, 'r': t_rail_side},
  elements=[[-3, 0, 1, 19, 2, 5, 's'], [-3, 0, 9, 19, 2, 13, 's'], [0.5, 2, 0, 1.5, 5, 16, 'r'], [14.5, 2, 0, 15.5, 5, 16, 'r']],
  shape=[0, 0, 0, 16, 2, 16])
B('platform', 'ホームの床', 'Platform Floor', 'cube', 'concrete', {'all': t_platform})
B('platform_edge', 'ホームの端', 'Platform Edge', 'facing_cube', 'concrete', {'top': t_platform_edge, 'side': lambda: t_concrete('#b0afa9', 10)}, front='side', bottom='side')
B('station_bench', '駅のベンチ', 'Station Bench', 'seat', 'metal', {'b': t_bench_blue, 'm': lambda: t_metal('#8a9096', 11)},
  elements=[[0, 6, 2, 16, 8, 13, 'b'], [0, 8, 12, 16, 15, 14, 'b'], [1, 0, 6, 3, 6, 9, 'm'], [13, 0, 6, 15, 6, 9, 'm']], seat=0.45)
B('park_bench', '木のベンチ', 'Wooden Bench', 'seat', 'wood', {'w': t_hinoki, 'm': lambda: t_metal('#3e4247', 12)},
  elements=[[0, 6, 3, 16, 7.5, 13, 'w'], [0, 9, 12.5, 16, 14, 14, 'w'], [1, 0, 4, 2.5, 6, 13, 'm'], [13.5, 0, 4, 15, 6, 13, 'm']], seat=0.42)
B('kenbaiki', '券売機', 'Ticket Machine', 'action', 'metal', {'front': t_kenbaiki_front, 'side': lambda: t_metal('#c8ccd0', 13)},
  elements=[[0, 0, 2, 16, 16, 16, {'n': 'front', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'side', 'd': 'side'}]], action='ticket', light=6)
B('kenbaiki_top', '運賃表', 'Fare Chart', 'facing_cube', 'metal', {'front': t_kenbaiki_top, 'side': lambda: t_metal('#c8ccd0', 13)}, top='side', bottom='side', light=6)
B('kaisatsu', '自動改札機', 'Ticket Gate', 'shaped', 'metal', {'top': t_kaisatsu_top, 'side': t_kaisatsu_side},
  elements=[[4, 0, 0, 12, 14, 16, {'n': 'side', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'top', 'd': 'side'}]])
B('elevator_button', 'エレベーターのボタン', 'Elevator Button', 'action', 'metal', {'b': t_elevator_button},
  elements=[[3, 2, 15, 13, 14, 16, 'b']], action='warp', light=4)
B('tile_cream', 'クリーム色のタイル', 'Cream Tiles', 'cube', 'tile', {'all': lambda: t_tile('#e8dcbc', '#cfc3a3', 4, 14)})
B('tile_brown', '茶色のタイル', 'Brown Scratch Tiles', 'cube', 'tile', {'all': lambda: t_tile('#8b5e3c', '#6b4429', 4, 15)})
B('tile_white', '白いタイル', 'White Tiles', 'cube', 'tile', {'all': lambda: t_tile('#f2f1ec', '#d6d5cf', 8, 16)})
B('renga', '赤煉瓦', 'Red Brick', 'cube', 'brick', {'all': t_renga})
B('clapboard_green', '萌黄色の下見板', 'Pale Green Clapboard', 'cube', 'wood', {'all': lambda: t_clapboard('#b9d6a6', 17)})
B('clapboard_white', '白い下見板', 'White Clapboard', 'cube', 'wood', {'all': lambda: t_clapboard('#ecebe4', 18)})
B('weathercock', '風見鶏', 'Weathercock', 'cross', 'metal', {'cross': t_weathercock})
B('sash_window', '洋館の窓', 'Sash Window', 'pane', 'glass', {'pane': t_sash_window, 'edge': lambda: solid('#f4f4f0', 0.02, 5)}, render='translucent')
B('shu_pillar', '朱塗りの柱', 'Vermilion Pillar', 'pillar', 'wood', {'side': t_shu, 'end': lambda: solid('#9a2418', 0.05, 6)})
B('shu_block', '朱塗りの木材', 'Vermilion Wood', 'cube', 'wood', {'all': lambda: t_shu(19)})
B('china_gold', '金の装飾', 'Gold Ornament', 'cube', 'metal', {'all': t_china_gold})
B('red_steel', '赤い鉄骨', 'Red Steel', 'cube', 'metal', {'all': t_red_steel})
B('red_steel_lit', '光る赤い鉄骨', 'Lit Red Steel', 'cube', 'metal', {'all': lambda: solid('#ff4a3a', 0.05, 61)}, light=10)
B('white_steel_lit', 'ライトアップされた白い鉄骨', 'Lit White Steel', 'cube', 'metal', {'all': lambda: solid('#eaf6ff', 0.03, 62)}, light=9)
B('white_steel', '白い鉄骨', 'White Steel', 'cube', 'metal', {'all': t_white_steel})
B('window_lit', '明かりのついた窓', 'Lit Window', 'cube', 'glass', {'all': t_window_lit}, light=11)
B('window_lit_cool', '蛍光灯の窓', 'Lit Office Window', 'cube', 'glass', {'all': lambda: t_window_lit(True, 77)}, light=11)
B('window_dark', 'ビルの窓', 'Office Window', 'cube', 'glass', {'all': t_window_dark})
B('office_wall', 'ビルの外壁', 'Building Panel', 'cube', 'concrete', {'all': t_office_wall})
B('love_lock_fence', '愛の鍵のフェンス', 'Love Lock Fence', 'pane', 'metal', {'pane': t_lock_fence, 'edge': lambda: t_metal('#8f969c', 14)}, render=G)
B('railing', '手すり', 'Railing', 'pane', 'metal', {'pane': t_railing, 'edge': lambda: t_metal('#b4b8bc', 15)}, render=G)
B('cake_showcase', 'ケーキのショーケース', 'Cake Showcase', 'facing_cube', 'glass', {'front': t_showcase_front, 'side': lambda: t_metal('#cfd5da', 16)}, top='side', bottom='side', light=5)
B('bread_shelf', 'パンの棚', 'Bread Shelf', 'facing_cube', 'wood', {'front': lambda: t_shelf_front(['#d8a050', '#c08038', '#e8c070', '#b06a2a'], 20), 'side': t_dark_wood}, top='side', bottom='side')
B('souvenir_shelf', 'おみやげの棚', 'Souvenir Shelf', 'facing_cube', 'wood', {'front': lambda: t_shelf_front(['#d8392b', '#2f6fb3', '#f0c419', '#3aa655', '#f4f1e6', '#8e44ad'], 21), 'side': t_dark_wood}, top='side', bottom='side')
B('steamer', 'せいろ', 'Bamboo Steamer', 'shaped', 'wood', {'w': t_bamboo_weave}, elements=[[2, 0, 2, 14, 13, 14, 'w'], [3, 13, 3, 13, 14, 13, 'w']], facing=False)
B('jizo', 'お地蔵さま', 'Jizo Statue', 'shaped', 'stone', {'s': lambda: t_stone('#8f8c84', 22), 'f': t_jizo_front},
  elements=[[3, 0, 3, 13, 3, 13, 's'], [5, 3, 5, 11, 11, 11, {'n': 'f', 's': 's', 'e': 's', 'w': 's', 'u': 's', 'd': 's'}], [6, 11, 6, 10, 15, 10, 's']])
B('crate_vegetables', '野菜のコンテナ', 'Vegetable Crate', 'cube_bt', 'plastic', {'top': lambda: t_crate(True), 'side': t_crate_side})
B('crate_empty', 'からのコンテナ', 'Empty Crate', 'cube_bt', 'plastic', {'top': lambda: t_crate(False), 'side': t_crate_side})

def t_clear_glass():
    t = Tex(color=(200, 228, 240, 34), seed=91)
    for k in range(5):
        t.set(3 + k, 9 - k, (255, 255, 255, 70))
        t.set(9 + k, 14 - k, (255, 255, 255, 50))
    return t


def t_fan_blade():
    t = Tex(color='#e9eef0', seed=92)
    t.noise(0.03)
    return t


def t_lcd():
    t = Tex(color='#0b0d10', seed=93)
    t.frame(0, 0, 16, 16, '#3a3f45')
    for x in range(2, 14):
        if x % 3 != 1:
            t.set(x, 4, '#ff9a2a')
            t.set(x, 5, '#ff9a2a' if x % 2 else '#0b0d10')
    for x in range(2, 9):
        t.set(x, 8, '#ffffff')
        t.set(x, 9, '#ffffff' if x % 3 else '#0b0d10')
    t.hline(12, 2, 14, '#2fa05a')
    for x in (3, 7, 11):
        t.set(x, 11, '#ffffff')
        t.set(x, 12, '#ffffff')
    return t


def t_bento(kind):
    t = Tex(color='#6b4423', seed=94)
    t.frame(0, 0, 16, 16, '#4a2c14')
    t.rect(1, 1, 15, 15, '#f4f1e6')
    if kind == 'ayu':
        t.rect(2, 2, 14, 7, '#e8d8a8')
        poly(t, [(3, 9), (12, 9), (14, 11), (3, 13)], '#a8a090')
    elif kind == 'tako':
        ellipse(t, 2, 2, 13, 13, '#c88a4a')
        t.speckle(14, ['#b03a2a', '#d84a3a', '#3aa655'], 3, 3, 13, 13)
    else:
        t.rect(2, 2, 14, 7, '#f4f1e6')
        t.rect(2, 8, 14, 14, '#7a3a24')
        t.speckle(10, ['#a85a3a', '#f0c060', '#3aa655'], 2, 8, 14, 14)
    return t


# signboards with text drawn by the block entity renderer.
# panel(front, back, text centre y, text height) in the NORTH orientation.
B('station_sign', '駅名標', 'Station Name Board', 'panel', 'metal', {'f': lambda: t_board('#ffffff', '#2fa05a', 12), 'b': t_board_back},
  elements=[[0, 2, 7, 16, 14, 9, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(7, 9, 0.56, 0.62), style='station')
B('shop_sign', '看板', 'Shop Sign', 'panel', 'wood', {'f': lambda: planks('#5a3a24', 23, board=16, grain=0.1, seam=1.0), 'b': t_dark_wood},
  elements=[[0, 3, 14, 16, 13, 16, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(14, 16, 0.5, 0.56), style='shop', nocollide=True)
B('info_board', '案内板', 'Information Board', 'panel', 'metal', {'f': lambda: t_board('#1f3550', None), 'b': t_board_back},
  elements=[[0, 1, 7, 16, 15, 9, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(7, 9, 0.5, 0.8), style='info')
B('gate_plaque', '扁額', 'Gate Plaque', 'panel', 'wood', {'f': lambda: solid('#1a1a1a', 0.05, 24), 'b': lambda: t_shu(25)},
  elements=[[0, 1, 13, 16, 15, 16, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(13, 16, 0.5, 0.8), style='gate', nocollide=True)
B('neon_sign', 'ネオン看板', 'Neon Sign', 'panel', 'glass', {'f': lambda: solid('#14161c', 0.05, 26), 'b': t_board_back},
  elements=[[0, 2, 14, 16, 14, 16, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(14, 16, 0.5, 0.7), style='neon', light=9, nocollide=True)
B('bus_stop_sign', 'バス停の標識', 'Bus Stop Sign', 'panel', 'metal',
  {'f': lambda: round_mask(Tex(color='#ffffff', seed=27)) if False else t_board('#ffffff', '#2f6fb3', 13), 'b': t_board_back},
  elements=[[2, 1, 7.5, 14, 15, 8.5, {'n': 'f', 's': 'f', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(7.5, 8.5, 0.56, 0.6), style='white')

B('clear_glass', '透明ガラス', 'Clear Glass', 'cube', 'glass', {'all': t_clear_glass}, render='translucent')
B('ceiling_fan', '天井の扇風機', 'Ceiling Fan', 'shaped', 'metal', {'m': lambda: t_metal('#9aa3ab', 31), 'b': t_fan_blade},
  elements=[[6, 10, 6, 10, 16, 10, 'm'], [1, 10, 7, 15, 11, 9, 'b'], [7, 10, 1, 9, 11, 15, 'b'], [7, 11, 7, 9, 13, 9, 'm']], facing=False, nocollide=True)
B('train_lcd', '車内の案内表示', 'Passenger Display', 'facing_cube', 'metal', {'front': t_lcd, 'side': lambda: solid('#2a2e33', 0.03, 32)}, top='side', bottom='side', light=6)


# =============================================================== decorative detail blocks (round 2)
from PIL import ImageFont as _IF
_FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'


def _text(t, xy, s, size, fill):
    d = t.draw()
    f = _IF.truetype(_FONT, size)
    d.text(xy, s, font=f, fill=hexc(fill) if isinstance(fill, str) else fill)
    t.px = t.im.load()


def t_mirror():
    t = Tex(color=(0, 0, 0, 0), seed=101)
    ellipse(t, 0, 0, 15, 15, '#ff7a1a')
    ellipse(t, 2, 2, 13, 13, '#9fb4c4')
    for y in range(16):
        for x in range(16):
            c = t.get(x, y)
            if c[:3] == hexc('#9fb4c4')[:3]:
                k = 1.25 - 0.45 * ((x - 4) ** 2 + (y - 4) ** 2) ** 0.5 / 12
                t.set(x, y, mul(hexc('#a8c4d8'), k))
    t.set(4, 4, '#ffffff')
    t.set(5, 4, '#ffffff')
    t.set(4, 5, '#ffffff')
    return t


def t_pole_orange():
    t = Tex(color='#ff7a1a', seed=102)
    for y in range(0, 16, 8):
        t.rect(0, y, 16, y + 1, '#d85a10')
    return t


def t_signal_front(lit='green'):
    t = Tex(color='#3a3f45', seed=103)
    t.frame(0, 0, 16, 16, '#2a2e33')
    cols = {'green': '#3aff9a', 'yellow': '#ffd23a', 'red': '#ff3a3a'}
    dim = {'green': '#1f4a33', 'yellow': '#5a4a1f', 'red': '#5a1f1f'}
    for i, k in enumerate(('green', 'yellow', 'red')):
        cx = 3 + i * 5
        ellipse(t, cx - 2, 5, cx + 2, 10, cols[k] if k == lit else dim[k])
    return t


def t_stop_sign():
    t = Tex(32, 32, (0, 0, 0, 0), seed=104)
    poly(t, [(0, 2), (31, 2), (15.5, 30)], '#ffffff')
    poly(t, [(3, 4), (28, 4), (15.5, 26)], '#d8202a')
    _text(t, (5, 3), '止まれ', 8, '#ffffff')
    return t


def t_crossbuck(state=0):
    t = Tex(32, 32, (0, 0, 0, 0), seed=105)
    d = t.draw()
    for a, b in (((2, 2), (29, 18)), ((29, 2), (2, 18))):
        d.line([a, b], fill=hexc('#f2c91a'), width=5)
        d.line([a, b], fill=hexc('#1a1a1a'), width=1)
    t.px = t.im.load()
    on = hexc('#ff3020')
    off = hexc('#5a1a14')
    ellipse(t, 3, 20, 12, 29, '#1a1a1a')
    ellipse(t, 19, 20, 28, 29, '#1a1a1a')
    ellipse(t, 5, 22, 10, 27, on if state == 1 else off)
    ellipse(t, 21, 22, 26, 27, on if state == 2 else off)
    t.vline(15, 18, 32, '#2a2a2a')
    t.vline(16, 18, 32, '#2a2a2a')
    return t


def t_stripes_yb(vertical=True):
    t = Tex(color='#f2c91a', seed=106)
    for y in range(16):
        for x in range(16):
            if ((x + y) // 4) % 2 == 0:
                t.set(x, y, '#1a1a1a')
    return t


def t_clock():
    t = Tex(color=(0, 0, 0, 0), seed=107)
    ellipse(t, 0, 0, 15, 15, '#1a1a1a')
    ellipse(t, 1, 1, 14, 14, '#fafaf6')
    for a in range(12):
        import math as _m
        x = 7.5 + 5.6 * _m.cos(a * _m.pi / 6)
        y = 7.5 + 5.6 * _m.sin(a * _m.pi / 6)
        t.set(int(round(x)), int(round(y)), '#1a1a1a')
    d = t.draw()
    d.line([(7.5, 7.5), (4.5, 4.8)], fill=hexc('#1a1a1a'), width=1)     # 10 o'clock hand
    d.line([(7.5, 7.5), (11.5, 4.5)], fill=hexc('#1a1a1a'), width=1)    # 2 (10:10)
    t.px = t.im.load()
    t.set(7, 7, '#c0392b')
    return t


def t_barber():
    t = Tex(color='#ffffff', seed=108)
    for y in range(16):
        for x in range(16):
            k = (x + y) % 12
            if k < 4:
                t.set(x, y, '#d8202a')
            elif 6 <= k < 10:
                t.set(x, y, '#1f4aa8')
    return t


def t_kamado_front():
    t = Tex(color='#e8e2d4', seed=109)
    t.noise(0.04)
    for cx in (4, 11):
        ellipse(t, cx - 3, 6, cx + 2, 13, '#1c1612')
        t.set(cx - 1, 11, '#ff7a2a')
        t.set(cx, 12, '#ffb04a')
    t.rect(0, 14, 16, 16, '#8a8478')
    return t


def t_pot():
    t = Tex(color='#1f1f22', seed=110)
    t.noise(0.08)
    return t


def t_tsukubai():
    t = stones('#8a877e', '#6a675e', seed=111, n=4)
    ellipse(t, 3, 3, 12, 12, '#2a5a78')
    t.set(6, 6, '#8ac4e8')
    return t


def t_bamboo():
    t = Tex(color='#a8b06a', seed=112)
    for y in range(16):
        for x in range(16):
            t.set(x, y, mul(hexc('#a8b06a'), 0.9 + 0.2 * ((x % 4) == 1)))
    t.hline(5, 0, 16, '#7a8044')
    t.hline(12, 0, 16, '#7a8044')
    return t


def t_geta():
    t = planks('#c8a070', seed=113, board=16, grain=0.08, seam=1.0)
    t.rect(6, 2, 10, 4, '#c0392b')
    t.rect(4, 4, 6, 7, '#c0392b')
    t.rect(10, 4, 12, 7, '#c0392b')
    return t


def t_towel():
    t = Tex(color='#f4f4f0', seed=114)
    t.rect(0, 3, 16, 5, '#5a8ac8')
    t.rect(0, 11, 16, 12, '#5a8ac8')
    t.noise(0.03)
    return t


def t_onigawara():
    t = Tex(color='#5d646e', seed=115)
    t.noise(0.05)
    ellipse(t, 2, 2, 13, 13, '#4a5059')
    ellipse(t, 4, 5, 6, 7, '#2a2e33')
    ellipse(t, 9, 5, 11, 7, '#2a2e33')
    t.rect(5, 9, 11, 11, '#2a2e33')
    t.set(3, 2, '#7b838d')
    t.set(12, 2, '#7b838d')
    return t


def t_bin():
    t = Tex(color='#2f7a4a', seed=116)
    t.noise(0.04)
    t.rect(0, 5, 16, 9, '#f4f4f0')
    _text(t, (1, 4), 'ごみ', 4, '#2a2a2a')
    t.rect(0, 0, 16, 2, '#245f39')
    return t


def t_bicycle():
    t = Tex(color=(0, 0, 0, 0), seed=117)
    d = t.draw()
    d.ellipse([0, 7, 6, 13], outline=hexc('#2a2a2a'))
    d.ellipse([9, 7, 15, 13], outline=hexc('#2a2a2a'))
    d.line([(3, 10), (7, 6), (12, 10)], fill=hexc('#c0392b'))
    d.line([(7, 6), (8, 10), (3, 10)], fill=hexc('#c0392b'))
    d.line([(6, 5), (8, 5)], fill=hexc('#1a1a1a'))
    d.line([(11, 4), (12, 10)], fill=hexc('#9aa0a6'))
    d.line([(10, 4), (13, 4)], fill=hexc('#1a1a1a'))
    d.rectangle([10, 5, 14, 7], outline=hexc('#9aa0a6'))
    t.px = t.im.load()
    return t


def t_asagao():
    t = Tex(color=(0, 0, 0, 0), seed=118)
    for x in (4, 11):
        t.vline(x, 0, 16, '#b89a6a')
    for y in range(0, 16, 2):
        t.set(5 + (y % 4), y, '#3f8a2e')
        t.set(10 - (y % 4), y + 1, '#4f9a3a')
    for (x, y, c) in ((5, 3, '#4a5ad8'), (10, 6, '#c04ac8'), (6, 9, '#4a8ad8'), (11, 11, '#4a5ad8'), (8, 1, '#c04ac8')):
        t.rect(x - 1, y - 1, x + 2, y + 2, c)
        t.set(x, y, '#ffffff')
    return t


def t_pot_clay():
    t = Tex(color='#a8603a', seed=119)
    t.noise(0.06)
    t.rect(0, 0, 16, 2, '#8a4a2a')
    return t


def t_coil():
    t = Tex(color=(0, 0, 0, 0), seed=120)
    import math as _m
    for i in range(140):
        a = i * 0.21
        r = 1 + a * 0.55
        x, y = 7.5 + r * _m.cos(a), 7.5 + r * _m.sin(a)
        if r < 7.5:
            t.set(int(x), int(y), '#3f6a2a')
    t.set(7, 7, '#ff6a2a')
    return t


def t_rail_signal(state='green'):
    t = Tex(color='#1f2226', seed=121)
    cols = {'green': '#3aff9a', 'yellow': '#ffd23a', 'red': '#ff3a3a'}
    for i, k in enumerate(('red', 'yellow', 'green')):
        cy = 3 + i * 5
        ellipse(t, 5, cy - 2, 10, cy + 2, cols[k] if k == state else '#3a3a3a')
    return t


def t_poster(kind):
    t = Tex(32, 32, '#f4f1e6', seed=122)
    if kind == 'kobe':
        for y in range(32):
            t.rect(1, y, 31, y + 1, mix(hexc('#1f3a6a'), hexc('#e8805a'), y / 22) if y < 22 else hexc('#2a5a8a'))
        t.rect(1, 22, 31, 31, '#2a5a8a')
        for y in range(6, 22):
            w = 1 + abs(y - 12) // 3
            t.rect(16 - w, y, 16 + w, y + 1, '#d8202a')
        ellipse(t, 22, 4, 27, 9, '#fff0b0')
        _text(t, (3, 23), '神戸へ', 6, '#ffffff')
    else:
        for y in range(32):
            t.rect(1, y, 31, y + 1, mix(hexc('#7ac4f0'), hexc('#e8f4ff'), y / 16) if y < 16 else hexc('#2e86b8'))
        poly(t, [(1, 18), (12, 12), (20, 17), (31, 13), (31, 20), (1, 20)], '#3f8a4a')
        _text(t, (3, 22), '瀬戸内', 6, '#ffffff')
    t.frame(0, 0, 32, 32, '#2a2a2a')
    return t


B('pole_orange', 'オレンジの支柱', 'Orange Pole', 'shaped', 'metal', {'m': t_pole_orange}, elements=[[7, 0, 7, 9, 16, 9, 'm']], facing=False)
B('curve_mirror', 'カーブミラー', 'Traffic Mirror', 'shaped', 'metal', {'f': t_mirror, 'b': lambda: t_metal('#8a9096', 41), 'p': t_pole_orange},
  elements=[[1, 2, 6, 15, 16, 7, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}], [7, 0, 7, 9, 4, 9, 'p']], render='cutout')
B('traffic_light', '信号機', 'Traffic Light', 'shaped', 'metal', {'f': t_signal_front, 'b': lambda: t_metal('#3a3f45', 42), 'v': lambda: solid('#2a2e33', 0.03, 43)},
  elements=[[0, 4, 6, 16, 12, 10, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}], [0, 11, 4, 16, 12, 6, 'v'], [7, 0, 8, 9, 4, 10, 'b']], light=7)
B('crossing_pole', '踏切の柱', 'Crossing Pole', 'shaped', 'metal', {'m': t_stripes_yb}, elements=[[6.5, 0, 6.5, 9.5, 16, 9.5, 'm']], facing=False)
B('crossing_signal', '踏切警報機', 'Crossing Signal', 'crossing', 'metal', {f'x{i}': (lambda i=i: t_crossbuck(i)) for i in range(3)} | {'m': t_stripes_yb})
B('crossing_bar', '踏切の遮断かん', 'Crossing Bar', 'shaped', 'metal', {'m': t_stripes_yb}, elements=[[0, 6, 7, 16, 8, 9, 'm']], nocollide=True)
B('bollard', '係船柱', 'Mooring Bollard', 'shaped', 'metal', {'m': lambda: t_metal('#2a2c30', 45)},
  elements=[[3, 0, 3, 13, 2, 13, 'm'], [5, 2, 5, 11, 9, 11, 'm'], [4, 9, 4, 12, 11, 12, 'm']], facing=False)
B('life_ring', '救命浮輪', 'Life Ring', 'shaped', 'wood', {'f': lambda: _lifering(), 'b': lambda: solid('#f4f4f0', 0.03, 46)},
  elements=[[1, 1, 15, 15, 15, 16, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], nocollide=True)
B('station_clock', '駅の時計', 'Station Clock', 'shaped', 'metal', {'f': t_clock, 'r': lambda: t_metal('#3a3f45', 47)},
  elements=[[2, 1, 7, 14, 13, 9, {'n': 'f!', 's': 'f!', 'e': 'r', 'w': 'r', 'u': 'r', 'd': 'r'}], [7.5, 13, 7.5, 8.5, 16, 8.5, 'r']], nocollide=True, render='cutout')
B('barber_pole', 'サインポール', 'Barber Pole', 'shaped', 'glass', {'m': t_barber, 'c': lambda: solid('#e8f0f8', 0.02, 50)},
  elements=[[5, 0, 5, 11, 14, 11, 'm'], [4, 14, 4, 12, 16, 12, 'c']], facing=False, light=6)
B('kamado', 'かまど', 'Kamado Stove', 'shaped', 'stone', {'f': t_kamado_front, 's': lambda: solid('#e8e2d4', 0.04, 51), 'p': t_pot, 'l': t_dark_wood},
  elements=[[0, 0, 0, 16, 10, 16, {'n': 'f!', 's': 's', 'e': 's', 'w': 's', 'u': 's', 'd': 's'}], [2, 10, 4, 7, 13, 9, 'p'], [9, 10, 4, 14, 13, 9, 'p'],
            [2, 13, 4, 7, 14, 9, 'l'], [9, 13, 4, 14, 14, 9, 'l']], light=4)
B('tsukubai', 'つくばい', 'Tsukubai Basin', 'shaped', 'stone', {'t': t_tsukubai, 's': lambda: t_stone('#8a877e', 52), 'b': t_bamboo},
  elements=[[2, 0, 2, 14, 7, 14, {'n': 's', 's': 's', 'e': 's', 'w': 's', 'u': 't', 'd': 's'}], [7, 8, 8, 9, 10, 16, 'b']])
B('geta', '下駄', 'Geta Sandals', 'shaped', 'wood', {'g': t_geta}, elements=[[3, 0, 3, 7, 2, 13, 'g'], [9, 0, 3, 13, 2, 13, 'g']], nocollide=True)
B('monohoshi', '物干し竿', 'Laundry Pole', 'shaped', 'wood', {'b': t_bamboo, 't': t_towel},
  elements=[[0, 13, 7, 16, 15, 9, 'b'], [3, 5, 8, 10, 13, 8.5, 't']], nocollide=True)
B('onigawara', '鬼瓦', 'Onigawara Ridge Tile', 'shaped', 'tile', {'f': t_onigawara, 's': lambda: t_kawara()},
  elements=[[1, 0, 6, 15, 14, 10, {'n': 'f!', 's': 'f!', 'e': 's', 'w': 's', 'u': 's', 'd': 's'}]])
B('trash_bin', 'ごみ箱', 'Litter Bin', 'shaped', 'metal', {'f': t_bin, 'l': lambda: solid('#245f39', 0.03, 53)},
  elements=[[4, 0, 4, 12, 11, 12, 'f'], [3, 11, 3, 13, 12, 13, 'l']])
B('bicycle', '自転車', 'Bicycle', 'shaped', 'metal', {'b': t_bicycle},
  elements=[[0, 0, 7.5, 16, 16, 8.5, {'n': 'b!', 's': 'b!'}]], nocollide=True, render='cutout')
B('asagao', '朝顔の鉢', 'Morning Glory Pot', 'shaped', 'plant', {'v': t_asagao, 'p': t_pot_clay},
  elements=[[4, 0, 4, 12, 5, 12, 'p'], [1, 5, 8, 15, 16, 8, {'n': 'v!', 's': 'v!'}, {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}],
            [8, 5, 1, 8, 16, 15, {'e': 'v!', 'w': 'v!'}, {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}]],
  facing=False, render='cutout', shape=[4, 0, 4, 12, 12, 12])
B('katori_senko', '蚊取り線香', 'Mosquito Coil', 'shaped', 'plant', {'c': t_coil, 'm': lambda: solid('#3a6a8a', 0.04, 54)},
  elements=[[3, 0, 3, 13, 1, 13, 'm'], [3, 1, 3, 13, 1.5, 13, {'u': 'c!'}]], nocollide=True, facing=False, render='cutout')
B('rail_signal', '鉄道信号機', 'Railway Signal', 'shaped', 'metal', {'f': lambda: t_rail_signal('green'), 'b': lambda: solid('#1f2226', 0.03, 55)},
  elements=[[5, 0, 6, 11, 16, 10, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], light=6)
B('poster_kobe', '旅のポスター（神戸）', 'Travel Poster (Kobe)', 'shaped', 'paper', {'f': lambda: t_poster('kobe'), 'b': lambda: solid('#f4f1e6', 0.02, 56)},
  elements=[[1, 0, 15, 15, 16, 16, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], nocollide=True)
B('poster_sea', '旅のポスター（瀬戸内）', 'Travel Poster (Seto)', 'shaped', 'paper', {'f': lambda: t_poster('sea'), 'b': lambda: solid('#f4f1e6', 0.02, 57)},
  elements=[[1, 0, 15, 15, 16, 16, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], nocollide=True)


def _lifering():
    t = Tex(color='#f4f4f0', seed=58)
    ellipse(t, 1, 1, 14, 14, '#ff6a1a')
    ellipse(t, 5, 5, 10, 10, '#f4f4f0')
    for (x, y) in ((7, 1), (7, 13), (1, 7), (13, 7)):
        t.rect(x, y, x + 2, y + 2, '#f4f4f0')
    return t
