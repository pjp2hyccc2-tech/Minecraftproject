"""Decorative blocks added for finer detail: street furniture, shrine items, traditional house parts, station fittings,
harbour details and household summer items."""
from texlib import *
from specs_blocks import B, BLOCKS, t_concrete, t_metal, t_stone, t_dark_wood, t_hinoki, solid as _solid  # noqa: F401


def t_black():
    return solid('#16181b', 0.04, 101)


def t_signal_front():
    t = Tex(color='#5d646c', seed=102)
    t.noise(0.04)
    for i, (c, lit) in enumerate((('#2bd96b', True), ('#e8b020', False), ('#e03a2a', False))):
        x0 = 1 + i * 5
        ellipse(t, x0, 4, x0 + 4, 8, c if lit else mul(hexc(c), 0.35))
        if lit:
            t.set(x0 + 1, 5, '#c8ffd8')
    return t


def t_ped_front():
    t = Tex(color='#5d646c', seed=103)
    t.rect(3, 1, 13, 7, '#3a1414')
    t.rect(3, 9, 13, 15, '#0e2a18')
    for (x, y) in ((8, 2), (7, 3), (8, 3), (9, 3), (8, 4), (7, 5), (9, 5)):
        t.set(x, y, '#5a2a2a')
    for (x, y) in ((8, 10), (7, 11), (8, 11), (9, 12), (8, 12), (7, 13), (9, 14), (6, 14)):
        t.set(x, y, '#3cf08a')
    return t


def t_mirror():
    t = Tex(color=(0, 0, 0, 0), seed=104)
    ellipse(t, 0, 0, 15, 15, '#f07a1a')
    ellipse(t, 2, 2, 13, 13, '#c9d6de')
    for k in range(4):
        t.set(5 + k, 9 - k, '#ffffff')
    return t


def t_stop():
    t = Tex(color=(0, 0, 0, 0), seed=105)
    poly(t, [(0, 0), (15, 0), (8, 15), (7, 15)], '#ffffff')
    poly(t, [(1, 1), (14, 1), (8, 13), (7, 13)], '#d0201a')
    return t


def t_namako():
    t = Tex(color='#f2efe6', seed=106)
    for y in range(16):
        for x in range(16):
            u, v = (x + y) % 8, (x - y) % 8
            if u not in (0, 7) and v not in (0, 7):
                t.set(x, y, mul(hexc('#3c4148'), 0.95 + 0.1 * t.r.random()))
    return t


def t_amado():
    return planks('#4a3828', seed=107, vertical=True, board=4, grain=0.18, seam=0.6)


def t_koshi():
    t = Tex(color=(0, 0, 0, 0), seed=108)
    for x in range(0, 16, 3):
        t.vline(x, 0, 16, '#6a4a2c')
        t.vline(x + 1, 0, 16, '#5a3c22')
    t.hline(0, 0, 16, '#5a3c22')
    t.hline(15, 0, 16, '#5a3c22')
    return t


def t_rope():
    t = Tex(color='#cbb57a', seed=109)
    for y in range(16):
        for x in range(16):
            t.set(x, y, mul(hexc('#cbb57a'), 0.82 + 0.25 * (((x + y * 2) % 6) < 3)))
    return t


def t_shide():
    t = Tex(color=(0, 0, 0, 0), seed=110)
    pts = [(2, 0), (14, 0), (14, 4), (4, 4), (4, 8), (12, 8), (12, 12), (6, 12), (6, 16), (2, 16), (2, 10), (9, 10), (9, 6), (2, 6)]
    poly(t, pts, '#fbfbf7')
    return t


def t_saisen_top():
    t = planks('#6a4a2c', seed=111, board=2, grain=0.1, seam=0.4)
    return t


def t_saisen_front():
    t = planks('#6a4a2c', seed=112, board=16, grain=0.1, seam=1.0)
    t.frame(0, 0, 16, 16, '#3a2414')
    t.rect(4, 6, 12, 10, '#e8d8b0')
    return t


def t_ema():
    t = Tex(color=(0, 0, 0, 0), seed=113)
    t.rect(0, 1, 16, 2, '#5a3a20')
    t.rect(0, 8, 16, 9, '#5a3a20')
    for row in (2, 9):
        for x in range(0, 16, 4):
            poly(t, [(x, row + 1), (x + 1, row), (x + 2, row), (x + 3, row + 1), (x + 3, row + 5), (x, row + 5)], '#e4c48c')
            t.set(x + 1, row + 3, '#c0392b')
    t.vline(0, 0, 16, '#5a3a20')
    t.vline(15, 0, 16, '#5a3a20')
    return t


def t_phone_green():
    t = Tex(color='#2e8b4e', seed=114)
    t.noise(0.04)
    t.rect(3, 3, 13, 7, '#d8e8d8')
    t.rect(5, 9, 11, 13, '#1d5a32')
    return t


def t_vending_red_front():
    t = Tex(color='#d7261e', seed=115)
    t.rect(1, 1, 15, 7, '#2c3440')
    for i in range(6):
        t.rect(2 + i * 2, 2, 3 + i * 2, 5, t.r.choice(['#ffffff', '#f0c419', '#2f6fb3', '#3aa655', '#d8392b']))
        t.set(2 + i * 2, 6, '#9aff9a')
    t.rect(11, 8, 14, 11, '#9aa3ad')
    t.rect(2, 12, 14, 15, '#1d1f22')
    t.frame(0, 0, 16, 16, '#a01a14')
    return t


def t_vending_red_top():
    t = Tex(color='#d7261e', seed=116)
    t.rect(1, 1, 15, 15, '#f2e6e4')
    for row in range(3):
        y = 2 + row * 5
        for i in range(6):
            c = t.r.choice(['#d8392b', '#2f6fb3', '#f0c419', '#3aa655', '#ffffff', '#795548'])
            t.rect(2 + i * 2, y, 3 + i * 2, y + 3, c)
        t.hline(y + 3, 2, 14, '#ff6040')
    t.frame(0, 0, 16, 16, '#a01a14')
    return t


def t_vending_red_side():
    t = Tex(color='#d7261e', seed=117)
    t.noise(0.03)
    t.rect(0, 0, 16, 3, '#f2f2f2')
    return t


def t_clock():
    t = Tex(color=(0, 0, 0, 0), seed=118)
    ellipse(t, 0, 0, 15, 15, '#2a2e33')
    ellipse(t, 1, 1, 14, 14, '#f8f8f4')
    for a in range(12):
        import math as _m
        x = int(round(7.5 + 6 * _m.cos(a * _m.pi / 6)))
        y = int(round(7.5 + 6 * _m.sin(a * _m.pi / 6)))
        t.set(x, y, '#2a2e33')
    t.vline(7, 3, 8, '#111111')
    for k in range(4):
        t.set(8 + k, 7 - k // 2, '#111111')
    t.set(10, 9, '#c0392b')
    return t


def t_led_board():
    t = Tex(color='#0a0b0d', seed=119)
    t.frame(0, 0, 16, 16, '#3a3f45')
    return t


def t_kiosk_front():
    t = Tex(color='#e8e4da', seed=120)
    t.rect(0, 0, 16, 3, '#2f6fb3')
    for y in (4, 8, 12):
        t.hline(y + 3, 1, 15, '#9aa3ad')
        x = 1
        while x < 15:
            c = t.r.choice(['#d8392b', '#f0c419', '#3aa655', '#2f6fb3', '#f4f1e6', '#8e44ad', '#e67e22'])
            w = t.r.choice([1, 2])
            t.rect(x, y, min(15, x + w), y + 3, c)
            x += w + 1
    return t


def t_palm():
    t = Tex(color=(0, 0, 0, 0), seed=121)
    for side in (-1, 1):
        for k in range(8):
            x = 8 + side * k
            y = 3 + (k * k) // 6
            for j in range(-1, 2):
                if 0 <= y + j < 16:
                    t.set(x, y + j + (1 if j else 0), '#3f8a2e' if j else '#2f6a22')
            t.set(x, y + 3, '#4f9a36')
    for k in range(12):
        t.set(8, 3 + k, '#2f6a22')
        t.set(7 + (k % 3) - 1, 4 + k, '#4f9a36')
    return t


def t_bollard():
    t = Tex(color='#2b2d30', seed=122)
    t.noise(0.08)
    return t


def t_tetrapod():
    return t_concrete('#b9b6ae', 123)


def t_red_light():
    return solid('#ff2a1a', 0.04, 124)


def t_iron_fence():
    t = Tex(color=(0, 0, 0, 0), seed=125)
    for x in (1, 5, 9, 13):
        t.vline(x, 2, 16, '#1d1f22')
        t.set(x, 1, '#1d1f22')
        t.set(x - 1, 2, '#1d1f22')
        t.set(x + 1, 2, '#1d1f22')
    t.hline(4, 0, 16, '#1d1f22')
    t.hline(13, 0, 16, '#1d1f22')
    for x in (3, 7, 11, 15):
        ellipse(t, x - 1, 7, x + 1, 10, '#1d1f22')
        t.set(x, 8, (0, 0, 0, 0))
    return t


def t_planter_side():
    t = Tex(color='#a4542e', seed=126)
    t.noise(0.06)
    t.hline(0, 0, 16, '#c8744a')
    return t


def t_flowers():
    t = Tex(color=(0, 0, 0, 0), seed=127)
    for k in range(14):
        x = t.r.randrange(1, 15)
        y = t.r.randrange(2, 12)
        t.vline(x, y + 1, 16, '#3f8a2e')
        c = t.r.choice(['#e8403a', '#f0c419', '#e88aa8', '#ffffff', '#8e44ad'])
        t.set(x, y, c)
        t.set(x + 1, y, c)
        t.set(x, y - 1, c)
    return t


def t_coil():
    t = Tex(color=(0, 0, 0, 0), seed=128)
    import math as _m
    for i in range(160):
        a = i * 0.25
        r = 1 + a * 0.55
        x = int(round(7.5 + r * _m.cos(a)))
        y = int(round(7.5 + r * _m.sin(a)))
        if 0 <= x < 16 and 0 <= y < 16:
            t.set(x, y, '#2f6a3a')
    t.set(7, 7, '#e8403a')
    return t


def t_tv_front():
    t = planks('#6a4a2c', seed=129, board=16, grain=0.08, seam=1.0)
    t.rect(1, 1, 12, 12, '#2a2e33')
    t.rect(2, 2, 11, 11, '#5f7a8a')
    t.set(4, 4, '#a0c0d0')
    t.set(5, 4, '#a0c0d0')
    t.rect(13, 3, 15, 5, '#c0c0c0')
    t.rect(13, 7, 15, 9, '#c0c0c0')
    return t


def t_white_plastic():
    return solid('#f2f2ee', 0.02, 130)


def t_uchiwa():
    t = Tex(color=(0, 0, 0, 0), seed=131)
    ellipse(t, 1, 0, 14, 11, '#f4f1e6')
    ellipse(t, 3, 2, 12, 9, '#6aa8e0')
    t.set(6, 5, '#e8403a')
    t.set(7, 5, '#e8403a')
    t.set(9, 6, '#ffffff')
    t.vline(7, 11, 16, '#c8a060')
    t.vline(8, 11, 16, '#a88040')
    return t


def t_suika_top():
    t = Tex(color=(0, 0, 0, 0), seed=132)
    ellipse(t, 0, 0, 15, 15, '#f4f4f0')
    for k in range(3):
        poly(t, [(2 + k * 4, 12), (6 + k * 4, 12), (4 + k * 4, 4)], '#e8403a')
        t.hline(12, 2 + k * 4, 6 + k * 4, '#2e7d32')
        t.set(4 + k * 4, 9, '#1a1a1a')
    return t


def t_reed():
    t = Tex(color=(0, 0, 0, 0), seed=133)
    for x in range(16):
        c = mul(hexc('#c8b07a'), 0.85 + 0.3 * t.r.random())
        if x % 5 != 4:
            t.vline(x, 0, 16, c)
    for y in (3, 12):
        t.hline(y, 0, 16, '#6a4a2a')
    return t


def t_onigawara():
    t = Tex(color='#4a5058', seed=134)
    t.noise(0.08)
    ellipse(t, 4, 3, 11, 10, '#5a616a')
    ellipse(t, 6, 5, 9, 8, '#3a3f46')
    t.hline(13, 0, 16, '#3a3f46')
    return t


def t_manhole():
    t = Tex(color='#7d7f82', seed=135)
    t.noise(0.08)
    ellipse(t, 1, 1, 14, 14, '#4a4c50')
    ellipse(t, 2, 2, 13, 13, '#5c5f63')
    for k in range(2, 14, 2):
        t.hline(k, 4, 12, '#45484c')
    ellipse(t, 5, 5, 10, 10, '#6a7a8a')
    t.set(7, 7, '#d8a630')
    return t


def t_grate():
    t = Tex(color='#5a5d61', seed=136)
    for x in range(0, 16, 2):
        t.vline(x, 1, 15, '#2a2c2f')
    t.frame(0, 0, 16, 16, '#7a7d81')
    return t


def t_post_yellow():
    t = Tex(color='#e8c018', seed=137)
    for y in range(0, 16, 4):
        t.rect(0, y, 16, y + 2, '#1d1d1d')
    return t


G = 'cutout'
B('power_line', '電線', 'Power Lines', 'shaped', 'metal', {'w': t_black},
  elements=[[7.5, 13, 0, 8.5, 14, 16, 'w'], [2.5, 12, 0, 3.5, 13, 16, 'w'], [12.5, 12, 0, 13.5, 13, 16, 'w']], nocollide=True)
B('ped_signal', '歩行者用信号', 'Pedestrian Signal', 'shaped', 'metal', {'f': t_ped_front, 'b': lambda: t_metal('#5d646c', 12)},
  elements=[[4, 1, 6, 12, 15, 10, {'n': 'f!', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], light=5)
B('stop_sign', '止まれの標識', 'Stop Sign', 'panel', 'metal', {'f': t_stop, 'b': lambda: t_metal('#9aa0a6', 14)},
  elements=[[0, 0, 7.5, 16, 16, 8.5, {'n': 'f', 's': 'b', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}]], panel=(7.5, 8.5, 0.72, 0.28), style='stop', render=G)
B('namako_wall', 'なまこ壁', 'Namako Wall', 'cube', 'concrete', {'all': t_namako})
B('amado', '雨戸', 'Amado Shutter', 'cube', 'darkwood', {'all': t_amado})
B('koshi', '格子', 'Wooden Lattice', 'pane', 'wood', {'pane': t_koshi, 'edge': lambda: solid('#5a3c22', 0.05, 15)}, render=G)
B('shimenawa', 'しめ縄', 'Shimenawa Rope', 'shaped', 'cloth', {'r': t_rope, 's': t_shide},
  elements=[[0, 10, 6, 16, 14, 10, 'r'], [3, 3, 7.5, 6, 10, 8.5, 's!'], [10, 3, 7.5, 13, 10, 8.5, 's!']], nocollide=True, render=G)
B('saisen_bako', '賽銭箱', 'Offertory Box', 'shaped', 'wood', {'t': t_saisen_top, 'f': t_saisen_front},
  elements=[[1, 0, 3, 15, 10, 13, {'n': 'f', 's': 'f', 'e': 'f', 'w': 'f', 'u': 't', 'd': 'f'}]])
B('komainu', '狛犬', 'Komainu Statue', 'shaped', 'stone', {'s': lambda: t_stone('#8f8c84', 16)},
  elements=[[2, 0, 2, 14, 4, 14, 's'], [4, 4, 5, 12, 10, 13, 's'], [5, 10, 3, 11, 16, 9, 's'], [4, 14, 4, 6, 16, 6, 's'], [10, 14, 4, 12, 16, 6, 's'], [6, 4, 2, 10, 9, 4, 's']])
B('ema_rack', '絵馬かけ', 'Ema Rack', 'shaped', 'wood', {'e': t_ema, 'w': t_dark_wood},
  elements=[[0, 0, 7, 1, 16, 9, 'w'], [15, 0, 7, 16, 16, 9, 'w'], [1, 1, 7.5, 15, 15, 8.5, {'n': 'e!', 's': 'e!', 'e': 'w', 'w': 'w', 'u': 'w', 'd': 'w'}]], render=G)
B('public_phone', '公衆電話', 'Public Phone', 'shaped', 'metal', {'f': t_phone_green, 'p': lambda: solid('#2e8b4e', 0.04, 17)},
  elements=[[5, 0, 5, 11, 8, 11, 'p'], [3, 8, 4, 13, 15, 12, {'n': 'f!', 's': 'p', 'e': 'p', 'w': 'p', 'u': 'p', 'd': 'p'}]])
B('vending_machine_red', '赤い自動販売機', 'Red Vending Machine', 'action', 'metal', {'front': t_vending_red_front, 'side': t_vending_red_side},
  elements=[[0, 0, 1, 16, 16, 16, {'n': 'front', 's': 'side', 'e': 'side', 'w': 'side', 'u': 'side', 'd': 'side'}]], action='shop:vending')
B('vending_machine_red_top', '赤い自動販売機（上）', 'Red Vending Machine Top', 'facing_cube', 'metal', {'front': t_vending_red_top, 'side': t_vending_red_side},
  light=8, top='side', bottom='side')
B('departure_board', '発車標', 'Departure Board', 'panel', 'metal', {'f': t_led_board, 'b': lambda: t_metal('#3a3f45', 19)},
  elements=[[0, 3, 7, 16, 13, 9, {'n': 'f', 's': 'f', 'e': 'b', 'w': 'b', 'u': 'b', 'd': 'b'}], [7.5, 13, 7.5, 8.5, 16, 8.5, 'b']], panel=(7, 9, 0.5, 0.55),
  style='led', nocollide=True, light=4)
B('kiosk_shelf', '売店の棚', 'Kiosk Shelf', 'facing_cube', 'wood', {'front': t_kiosk_front, 'side': lambda: solid('#2f6fb3', 0.03, 20)}, top='side', bottom='side')
B('palm_fronds', 'ヤシの葉', 'Palm Fronds', 'cross', 'plant', {'cross': t_palm})
B('tetrapod', '消波ブロック', 'Tetrapod', 'shaped', 'concrete', {'c': t_tetrapod},
  elements=[[5, 5, 5, 11, 11, 11, 'c'], [0, 0, 6, 6, 6, 10, 'c'], [10, 0, 6, 16, 6, 10, 'c'], [6, 0, 10, 10, 6, 16, 'c'], [6, 11, 6, 10, 16, 10, 'c']], facing=False)
B('aviation_light', '航空障害灯', 'Aviation Light', 'shaped', 'metal', {'r': t_red_light, 'm': lambda: t_metal('#3a3f45', 21)},
  elements=[[6, 0, 6, 10, 2, 10, 'm'], [6.5, 2, 6.5, 9.5, 5, 9.5, 'r']], light=7, facing=False)
B('iron_fence', '洋館の鉄柵', 'Ornate Iron Fence', 'pane', 'metal', {'pane': t_iron_fence, 'edge': t_black}, render=G)
B('flower_planter', '花のプランター', 'Flower Planter', 'shaped', 'brick', {'s': t_planter_side, 'f': t_flowers, 'd': lambda: solid('#4a3424', 0.08, 22)},
  elements=[[1, 0, 1, 15, 6, 15, {'n': 's', 's': 's', 'e': 's', 'w': 's', 'u': 'd', 'd': 's'}],
            [2, 6, 8, 14, 15, 8, {'n': 'f!', 's': 'f!'}], [8, 6, 2, 8, 15, 14, {'e': 'f!', 'w': 'f!'}]], facing=False, render=G)
B('retro_tv', 'ブラウン管テレビ', 'Retro TV', 'shaped', 'wood', {'f': t_tv_front, 'w': t_dark_wood},
  elements=[[2, 3, 3, 14, 15, 13, {'n': 'f!', 's': 'w', 'e': 'w', 'w': 'w', 'u': 'w', 'd': 'w'}], [3, 0, 4, 4, 3, 5, 'w'], [12, 0, 4, 13, 3, 5, 'w'],
            [3, 0, 11, 4, 3, 12, 'w'], [12, 0, 11, 13, 3, 12, 'w']])
B('rice_cooker', '炊飯器', 'Rice Cooker', 'shaped', 'plastic', {'p': t_white_plastic},
  elements=[[4, 0, 4, 12, 6, 12, 'p'], [4.5, 6, 4.5, 11.5, 7.5, 11.5, 'p'], [7, 7.5, 6, 9, 8, 10, 'p']])
B('uchiwa', 'うちわ', 'Uchiwa Fan', 'shaped', 'paper', {'u': t_uchiwa}, elements=[[2, 1, 15, 14, 15, 16, 'u!']], nocollide=True, render=G)
B('suika_plate', 'スイカの皿', 'Watermelon Plate', 'shaped', 'cloth', {'t': t_suika_top, 'p': t_white_plastic},
  elements=[[2, 0, 2, 14, 1, 14, {'u': 't!', 'd': 'p', 'n': 'p', 's': 'p', 'e': 'p', 'w': 'p'}]], nocollide=True, render=G)
B('yoshizu', 'よしず', 'Yoshizu Reed Screen', 'shaped', 'wood', {'r': t_reed}, elements=[[0, 0, 12, 16, 16, 13, 'r!']], nocollide=True, render=G)
B('manhole', 'マンホール', 'Manhole Cover', 'cube_bt', 'metal', {'top': t_manhole, 'side': lambda: t_concrete('#7d7f82', 24)})
B('gutter_grate', '側溝のふた', 'Gutter Grate', 'cube_bt', 'metal', {'top': t_grate, 'side': lambda: t_concrete('#8d8f92', 25)})
B('car_stop_post', '車止めポール', 'Bollard Post', 'shaped', 'metal', {'p': t_post_yellow}, elements=[[6, 0, 6, 10, 12, 10, 'p']], facing=False)
