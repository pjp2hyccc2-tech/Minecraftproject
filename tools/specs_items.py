"""Item sprites (16x16 pixel art, drawn procedurally)."""
from texlib import *


def sprite(seed=1):
    return Tex(color=(0, 0, 0, 0), seed=seed)


def fin(t, outline_col='#2a1e14'):
    outline(t, outline_col)
    return t


def s_journal():
    t = sprite(1)
    t.rect(3, 2, 13, 15, '#8e2f22')
    t.rect(4, 2, 13, 14, '#a8392a')
    t.rect(12, 3, 13, 14, '#f4ecd8')
    t.rect(6, 5, 11, 8, '#f4ecd8')
    t.hline(6, 7, 10, '#5a3a2a')
    t.vline(4, 2, 15, '#6e2418')
    t.rect(9, 0, 10, 4, '#d8a630')
    return fin(t)


def s_ticket():
    t = sprite(2)
    t.rect(1, 5, 15, 11, '#f2e4b0')
    t.rect(1, 5, 15, 6, '#3aa655')
    t.hline(8, 3, 12, '#3a3a3a')
    t.hline(9, 3, 9, '#7a7a7a')
    t.set(13, 8, '#c0392b')
    t.set(12, 9, '#c0392b')
    return fin(t, '#6a5a3a')


def s_seedling():
    t = sprite(3)
    for x, h in ((5, 9), (7, 11), (9, 10), (11, 8)):
        t.vline(x, 14 - h, 14, '#5c9a2e')
        t.set(x + 1, 14 - h + 1, '#7fbe46')
    t.rect(4, 13, 13, 16, '#6a5038')
    return fin(t, '#2a3a14')


def s_rice():
    t = sprite(4)
    ellipse(t, 2, 6, 13, 14, '#f4f1e6')
    poly(t, [(2, 9), (13, 9), (12, 15), (3, 15)], '#8a6a3a')
    t.rect(3, 10, 13, 11, '#b08a50')
    for (x, y) in ((5, 7), (8, 6), (10, 8), (6, 9)):
        t.set(x, y, '#ffffff')
    return fin(t)


def s_seeds(c):
    t = sprite(5)
    t.rect(4, 3, 12, 14, '#e8dcc0')
    t.rect(4, 3, 12, 6, c)
    for (x, y) in ((6, 9), (8, 11), (10, 9), (7, 12), (9, 8)):
        t.set(x, y, '#8a6a3a')
    return fin(t)


def s_round(c, hl, leaf='#3aa655', size=10, seed=6):
    t = sprite(seed)
    o = (16 - size) // 2
    ellipse(t, o, o + 1, o + size - 1, o + size, c)
    shade_sphere(t, 8, 9, size / 2)
    t.set(o + 2, o + 3, hl)
    t.set(o + 3, o + 3, hl)
    t.rect(7, o - 1, 9, o + 2, leaf)
    t.set(6, o + 1, leaf)
    t.set(9, o + 1, leaf)
    return fin(t)


def s_cucumber():
    t = sprite(7)
    for i in range(11):
        x, y = 3 + i, 12 - i // 1
        t.rect(x, y - 1, x + 2, y + 1, '#3f8a2e')
    for i in range(0, 11, 3):
        t.set(4 + i, 11 - i, '#7fbe46')
    t.set(13, 2, '#f0d050')
    t.set(14, 2, '#f0d050')
    return fin(t)


def s_eggplant():
    t = sprite(8)
    ellipse(t, 4, 5, 12, 15, '#4b2a5e')
    shade_sphere(t, 8, 10, 5)
    t.rect(6, 3, 11, 6, '#3a6a20')
    t.rect(8, 1, 9, 3, '#3a6a20')
    t.set(6, 8, '#8a6aa0')
    return fin(t)


def s_onigiri():
    t = sprite(9)
    poly(t, [(8, 1), (15, 13), (1, 13)], '#f6f4ee')
    t.rect(1, 13, 15, 15, '#f6f4ee')
    t.rect(4, 9, 12, 15, '#1e2a22')
    t.set(7, 4, '#e8e4d8')
    t.set(9, 6, '#e8e4d8')
    return fin(t)


def s_glass(liquid, top=None, seed=10):
    t = sprite(seed)
    t.rect(4, 3, 12, 15, (220, 235, 245, 200))
    t.rect(5, 6, 11, 14, liquid)
    if top:
        t.rect(5, 4, 11, 6, top)
    t.vline(5, 6, 14, mul(hexc(liquid) if isinstance(liquid, str) else liquid, 1.2))
    return fin(t, '#4a5a66')


def s_suika():
    t = sprite(11)
    poly(t, [(1, 5), (15, 5), (8, 15)], '#e8403a')
    t.hline(5, 1, 15, '#f4f1e6')
    t.hline(4, 1, 15, '#2e7d32')
    t.hline(3, 2, 14, '#1b5e20')
    for (x, y) in ((5, 7), (9, 7), (7, 10), (11, 8), (8, 12)):
        t.set(x, y, '#1a1a1a')
    return fin(t)


def s_kakigori():
    t = sprite(12)
    poly(t, [(3, 9), (13, 9), (11, 15), (5, 15)], (220, 235, 245, 220))
    ellipse(t, 3, 2, 12, 10, '#ffffff')
    ellipse(t, 4, 2, 11, 7, '#e8403a')
    t.set(6, 4, '#ff8a80')
    return fin(t, '#4a5a66')


def s_ramune():
    t = sprite(13)
    t.rect(6, 1, 10, 3, '#3a7ad0')
    t.rect(6, 3, 10, 6, (170, 220, 240, 220))
    ellipse(t, 6, 4, 9, 7, (220, 240, 250, 255))
    t.rect(5, 6, 11, 15, (150, 210, 235, 220))
    t.rect(5, 9, 11, 12, '#3a7ad0')
    t.set(7, 10, '#ffffff')
    return fin(t, '#2a4a66')


def s_ice_candy():
    t = sprite(14)
    t.rect(5, 2, 11, 11, '#5ab0e8')
    t.rect(5, 2, 11, 4, '#8ad0f8')
    t.rect(7, 11, 9, 15, '#d8b880')
    return fin(t, '#1a4a66')


def s_dagashi():
    t = sprite(15)
    t.rect(2, 6, 14, 12, '#f0c419')
    t.rect(1, 7, 2, 11, '#e67e22')
    t.rect(14, 7, 15, 11, '#e67e22')
    t.rect(5, 7, 11, 11, '#d8392b')
    t.set(7, 9, '#ffffff')
    return fin(t)


def s_can(c, band='#ffffff', seed=16):
    t = sprite(seed)
    t.rect(5, 2, 11, 15, c)
    t.rect(5, 2, 11, 3, '#c0c4c8')
    t.rect(5, 7, 11, 10, band)
    t.vline(6, 3, 14, mul(hexc(c), 1.25))
    return fin(t, '#2a2e33')


def s_ayu():
    t = sprite(17)
    t.vline(2, 1, 16, '#c8a878')
    poly(t, [(3, 3), (13, 6), (14, 9), (3, 11)], '#8a8a80')
    t.rect(3, 6, 13, 8, '#d8c8a0')
    t.set(12, 7, '#1a1a1a')
    poly(t, [(3, 11), (6, 14), (3, 15)], '#6a6a60')
    t.speckle(5, ['#5a4a3a', '#3a2a1a'], 4, 4, 12, 9)
    return fin(t)


def s_cream_soda():
    t = sprite(18)
    t.rect(4, 5, 12, 15, (60, 200, 90, 230))
    ellipse(t, 4, 2, 11, 7, '#fffaf0')
    t.set(10, 2, '#d8392b')
    t.set(11, 1, '#3a7a2a')
    t.vline(12, 0, 6, '#e8e8e8')
    return fin(t, '#2a4a33')


def s_cup(liquid, saucer='#f4f4f0', seed=19):
    t = sprite(seed)
    t.rect(3, 6, 11, 13, '#f4f4f0')
    t.rect(4, 6, 10, 8, liquid)
    t.rect(11, 8, 13, 11, '#f4f4f0')
    t.rect(12, 9, 13, 10, (0, 0, 0, 0))
    t.rect(1, 13, 15, 14, saucer)
    t.set(6, 3, (220, 220, 220, 160))
    t.set(7, 2, (220, 220, 220, 160))
    t.set(8, 4, (220, 220, 220, 160))
    return fin(t, '#4a3a2a')


def s_akashiyaki():
    t = sprite(20)
    t.rect(1, 9, 15, 14, '#8a5a2a')
    for x in (2, 6, 10):
        ellipse(t, x, 5, x + 4, 10, '#f2d27a')
        t.set(x + 1, 6, '#fff0b0')
    return fin(t)


def s_butaman():
    t = sprite(21)
    ellipse(t, 2, 4, 14, 14, '#f8f4ea')
    shade_sphere(t, 8, 9, 6)
    for i in range(4):
        t.set(8 + (i - 2), 5 + abs(i - 2) // 2, '#e0d8c4')
    t.rect(2, 13, 14, 15, '#e8d8b0')
    return fin(t, '#6a5a44')


def s_xlb():
    t = sprite(22)
    t.rect(1, 11, 15, 15, '#c8a868')
    ellipse(t, 3, 4, 13, 13, '#f6f0e2')
    t.set(8, 4, '#e0d4bc')
    t.set(7, 5, '#e0d4bc')
    t.set(9, 5, '#e0d4bc')
    shade_sphere(t, 8, 9, 5)
    return fin(t, '#6a5a44')


def s_goma():
    t = sprite(23)
    for (x, y) in ((2, 6), (8, 5), (5, 10)):
        ellipse(t, x, y, x + 6, y + 6, '#d8a050')
        t.speckle(6, ['#f4ecd8', '#3a2a1a'], x + 1, y + 1, x + 5, y + 5)
    return fin(t)


def s_sobameshi():
    t = sprite(24)
    ellipse(t, 1, 5, 15, 15, '#e8e4dc')
    ellipse(t, 2, 6, 14, 13, '#6a3a1a')
    t.speckle(18, ['#a86a3a', '#f4f1e6', '#3aa655', '#d8392b', '#8a4a2a'], 3, 7, 13, 12)
    return fin(t)


def s_kushi():
    t = sprite(25)
    for i in range(14):
        t.set(1 + i, 14 - i, '#d8b880')
    for k in range(3):
        x, y = 3 + k * 4, 9 - k * 4
        t.rect(x, y, x + 4, y + 4, '#8a3a2a')
        t.set(x + 1, y + 1, '#c86a4a')
        t.set(x + 3, y + 2, '#5a2018')
    return fin(t)


def s_baum():
    t = sprite(26)
    ellipse(t, 1, 1, 15, 15, '#e8c878')
    for r in (6, 4, 2):
        ellipse(t, 8 - r, 8 - r, 7 + r, 7 + r, '#d8a850' if r % 4 == 2 else '#f0d890')
    ellipse(t, 6, 6, 9, 9, (0, 0, 0, 0))
    t.frame(0, 0, 16, 16, (0, 0, 0, 0))
    return fin(t)


def s_pudding():
    t = sprite(27)
    poly(t, [(4, 5), (12, 5), (14, 14), (2, 14)], '#f6d870')
    t.rect(4, 4, 12, 7, '#7a3a14')
    t.rect(1, 14, 15, 15, '#f4f4f0')
    t.set(5, 9, '#fff0a8')
    return fin(t)


def s_cheesecake():
    t = sprite(28)
    poly(t, [(1, 7), (15, 4), (15, 12), (1, 14)], '#f6e7b8')
    poly(t, [(1, 7), (15, 4), (15, 6), (1, 9)], '#c88a3a')
    t.rect(1, 13, 15, 15, '#b07a3a')
    return fin(t)


def s_croissant():
    t = sprite(29)
    poly(t, [(1, 11), (4, 5), (8, 3), (12, 5), (15, 11), (11, 9), (8, 8), (5, 9)], '#d8963a')
    for x in (5, 8, 11):
        t.vline(x, 4, 9, '#a86a20')
    t.set(7, 4, '#f0c070')
    return fin(t)


def s_curry_pan():
    t = sprite(30)
    ellipse(t, 1, 4, 15, 13, '#b8742a')
    t.speckle(16, ['#e8b060', '#8a5018', '#d89040'], 2, 5, 14, 12)
    return fin(t)


def s_crepe():
    t = sprite(31)
    poly(t, [(3, 4), (13, 4), (8, 15)], '#e8c070')
    ellipse(t, 3, 1, 13, 7, '#fffaf0')
    t.set(5, 2, '#d8392b')
    t.set(9, 2, '#f0c419')
    t.set(7, 3, '#d8392b')
    t.rect(4, 6, 12, 8, '#f4ecd8')
    return fin(t)


def s_senbei():
    t = sprite(32)
    t.rect(2, 3, 14, 13, '#c8904a')
    t.frame(2, 3, 14, 13, '#a87030')
    t.rect(5, 6, 11, 10, '#a87030')
    t.set(7, 7, '#e0b070')
    t.set(8, 8, '#e0b070')
    return fin(t)


def s_tin():
    t = sprite(33)
    t.rect(1, 4, 15, 13, '#2f4f8f')
    t.rect(1, 4, 15, 6, '#d8a630')
    t.rect(4, 7, 12, 11, '#f4ecd8')
    t.set(6, 9, '#c0392b')
    t.set(9, 8, '#c0392b')
    return fin(t)


def s_kugini():
    t = sprite(34)
    t.rect(3, 4, 13, 15, (230, 235, 240, 200))
    t.rect(3, 3, 13, 5, '#d8392b')
    t.rect(4, 6, 12, 14, '#4a2a14')
    t.speckle(10, ['#6a4a24', '#2a1a0a', '#8a6a3a'], 4, 6, 12, 14)
    return fin(t, '#3a2a1a')


def s_amazake():
    t = sprite(35)
    t.rect(5, 1, 11, 3, '#c0392b')
    t.rect(4, 3, 12, 15, '#f4f1e6')
    t.rect(4, 6, 12, 11, '#2a4a7a')
    t.vline(8, 7, 10, '#ffffff')
    return fin(t, '#3a3a3a')


def s_tansan():
    t = sprite(36)
    ellipse(t, 2, 2, 14, 14, '#f0dcae')
    ellipse(t, 4, 4, 12, 12, '#e8cf98')
    for a in range(8):
        x = 8 + int(math.cos(a * 0.785) * 5)
        y = 8 + int(math.sin(a * 0.785) * 5)
        t.set(x, y, '#d8b878')
    return fin(t)


def s_pearl():
    t = sprite(37)
    t.vline(8, 1, 8, '#c8102e')
    t.set(7, 1, '#c8102e')
    t.set(9, 1, '#c8102e')
    ellipse(t, 4, 7, 12, 15, '#f4f2f0')
    shade_sphere(t, 8, 11, 4)
    t.set(6, 9, '#ffffff')
    t.set(10, 12, '#e8d8f0')
    return fin(t, '#6a6070')


def s_tower():
    t = sprite(38)
    for y in range(1, 15):
        w = 1 + int(abs(y - 6) * 0.45) + (2 if y < 4 else 0)
        t.rect(8 - w, y, 8 + w, y + 1, '#c62a22')
        if y % 3 == 0:
            t.set(8 - w, y, '#ff6a5a')
    t.rect(5, 2, 11, 4, '#f4f4f0')
    t.rect(3, 14, 13, 16, '#6a6a6a')
    return fin(t, '#5a1410')


def s_bento(kind):
    t = sprite(60)
    t.rect(1, 3, 15, 14, '#5a3418')
    t.rect(2, 4, 14, 13, '#f4f1e6')
    if kind == 'ayu':
        t.rect(3, 5, 13, 8, '#e0cc98')
        poly(t, [(3, 9), (11, 9), (13, 11), (3, 12)], '#9a9488')
        t.set(11, 10, '#1a1a1a')
    elif kind == 'tako':
        t.rect(3, 5, 13, 12, '#c8904a')
        t.speckle(12, ['#b03a2a', '#d84a3a', '#3aa655'], 3, 5, 13, 12)
    else:
        t.rect(3, 5, 13, 7, '#f4f1e6')
        t.rect(3, 8, 13, 12, '#7a3a24')
        t.speckle(8, ['#a85a3a', '#f0c060', '#3aa655'], 3, 8, 13, 12)
    t.rect(1, 2, 15, 3, '#c0392b')
    t.vline(8, 2, 14, '#c0392b')
    return fin(t)


ITEM_SPRITES = {
    'ekiben_okawa': lambda: s_bento('ayu'), 'ekiben_shiomi': lambda: s_bento('tako'), 'ekiben_kobe': lambda: s_bento('beef'),
    'journal': s_journal, 'ticket': s_ticket, 'rice_seedling': s_seedling, 'rice': s_rice,
    'tomato_seeds': lambda: s_seeds('#d8392b'), 'cucumber_seeds': lambda: s_seeds('#3f8a2e'), 'eggplant_seeds': lambda: s_seeds('#5b2d6e'),
    'tomato': lambda: s_round('#d8392b', '#ff8a7a'), 'cucumber': s_cucumber, 'eggplant': s_eggplant,
    'onigiri': s_onigiri, 'mugicha': lambda: s_glass('#b8742a', None, 40), 'suika': s_suika, 'kakigori': s_kakigori,
    'ramune': s_ramune, 'ice_candy': s_ice_candy, 'dagashi': s_dagashi, 'cider': lambda: s_can('#2f6fb3', '#f4f4f0', 41),
    'ayu_shioyaki': s_ayu, 'cream_soda': s_cream_soda, 'coffee': lambda: s_cup('#3a2010'), 'kocha': lambda: s_cup('#a8401a', '#f4f4f0', 42),
    'akashiyaki': s_akashiyaki, 'butaman': s_butaman, 'xiaolongbao': s_xlb, 'goma_dango': s_goma, 'sobameshi': s_sobameshi,
    'wagyu_kushi': s_kushi, 'baumkuchen': s_baum, 'kobe_pudding': s_pudding, 'cheesecake': s_cheesecake, 'croissant': s_croissant,
    'curry_pan': s_curry_pan, 'crepe': s_crepe, 'kawara_senbei': s_senbei, 'kobe_cookies': s_tin, 'ikanago_kugini': s_kugini,
    'amazake': s_amazake, 'arima_senbei': s_tansan, 'pearl_charm': s_pearl, 'port_tower_figure': s_tower,
}
