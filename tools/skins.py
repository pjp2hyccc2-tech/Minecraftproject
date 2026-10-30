"""Procedural 64x64 player-format skins for the NPCs."""
import random
from PIL import Image
from texlib import hexc, mul

# (part, x, y, w, h, d) classic layout; faces: top, bottom, right, front, left, back
PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}


def faces(part):
    x, y, w, h, d = PARTS[part]
    return {
        'top': (x + d, y, w, d), 'bottom': (x + d + w, y, w, d),
        'right': (x, y + d, d, h), 'front': (x + d, y + d, w, h),
        'left': (x + d + w, y + d, d, h), 'back': (x + d + w + d, y + d, w, h),
    }


class Skin:
    def __init__(self, seed):
        self.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.im.load()
        self.r = random.Random(seed)

    def paint(self, part, fn):
        for face, (fx, fy, fw, fh) in faces(part).items():
            for v in range(fh):
                for u in range(fw):
                    c = fn(face, u, v, fw, fh)
                    if c is None:
                        continue
                    if isinstance(c, str):
                        c = hexc(c)
                    if c[3] > 0 and c[3] == 255:
                        c = mul(c, 1 + (self.r.random() - 0.5) * 0.06)
                    self.px[fx + u, fy + v] = c


def make_skin(p, seed=1):
    """p: dict(skin, hair, hair_style, eyes, top, top_style, accent, bottom, bottom_style, shoes, hat, hat_color, glasses, apron, beard)"""
    s = Skin(seed)
    skin = hexc(p.get('skin', '#f0c8a0'))
    hair = hexc(p.get('hair', '#2a1e18'))
    style = p.get('hair_style', 'short')
    top = hexc(p.get('top', '#4a7ab0'))
    top_style = p.get('top_style', 'tshirt')
    accent = hexc(p.get('accent', '#ffffff'))
    bottom = hexc(p.get('bottom', '#3a4a6a'))
    bstyle = p.get('bottom_style', 'pants')
    shoes = hexc(p.get('shoes', '#3a3030'))
    eye = hexc(p.get('eyes', '#2a2420'))
    old = p.get('old', False)

    # ---- head
    def head(face, u, v, w, h):
        if face == 'top':
            return hair if style != 'bald' else skin
        if face == 'bottom':
            return skin
        if face == 'back':
            lim = {'short': 6, 'long': 8, 'bob': 7, 'bun': 6, 'bald': 5, 'pony': 8, 'buzz': 5}.get(style, 6)
            return hair if v < lim else skin
        if face in ('right', 'left'):
            lim = {'short': 3, 'long': 8, 'bob': 6, 'bun': 4, 'bald': 3, 'pony': 4, 'buzz': 2}.get(style, 3)
            if style == 'bald' and v < 2:
                return skin
            front_edge = u >= 6 if face == 'right' else u <= 1
            if style in ('long', 'bob') and front_edge and v >= 3:
                return skin
            return hair if v < lim else skin
        # front
        fringe = {'short': 2, 'long': 2, 'bob': 2, 'bun': 1, 'bald': 0, 'pony': 2, 'buzz': 1}.get(style, 2)
        if v < fringe and not (style == 'short' and v == 1 and u in (3, 4)):
            return hair
        if style in ('long', 'bob') and (u == 0 or u == 7) and v < (7 if style == 'long' else 5):
            return hair
        if v == 3 and u in (1, 2, 5, 6) and p.get('brows', True):
            return mul(hair, 0.9) if u in (1, 6) else mul(hair, 1.0)
        if v == 4 and u in (2, 5):
            return eye
        if v == 4 and u in (1, 6):
            return '#ffffff' if not old else mul(skin, 0.9)
        if v == 6 and u in (3, 4):
            return '#b0605a' if not p.get('beard') else hexc(p['beard'])
        if p.get('beard') and v >= 6 and u in (1, 2, 5, 6):
            return hexc(p['beard'])
        if v == 5 and u in (1, 6):
            return mul(skin, 0.94) if not p.get('blush') else hexc('#f0a0a0')
        if old and v == 5 and u in (2, 5):
            return mul(skin, 0.88)
        return skin

    s.paint('head', head)

    # ---- hat layer: hats, buns, glasses
    hat = p.get('hat')
    hatc = hexc(p.get('hat_color', '#2a3a5a'))

    def hatlayer(face, u, v, w, h):
        if p.get('glasses') and face == 'front' and v == 4 and u in (0, 1, 2, 3, 4, 5, 6, 7):
            if u in (1, 2, 5, 6):
                return None
            return hexc(p['glasses'])
        if p.get('glasses') and face == 'front' and v in (3, 5) and u in (1, 2, 5, 6):
            return hexc(p['glasses'])
        if hat == 'cap':
            if face == 'top' or (face != 'bottom' and v < 2):
                return hatc
            if face != 'bottom' and v == 2:
                return hexc('#d8a630') if face != 'back' else hatc
            if face == 'front' and v == 3:
                return hexc('#151515')
        if hat == 'straw':
            if face == 'top' or (face != 'bottom' and v < 2):
                return hexc('#e0c47a')
            if face != 'bottom' and v == 2:
                return hexc('#8a3a2a')
        if hat == 'chef':
            if face == 'top' or (face != 'bottom' and v < 3):
                return hexc('#f8f8f6')
        if hat == 'beret':
            if face == 'top' or (face != 'bottom' and v < 1):
                return hatc
        if hat == 'scarf':
            if face == 'top' or (face != 'bottom' and v < 2):
                return hatc
            if face == 'back' and v < 4:
                return hatc
        if style == 'bun' and face == 'back' and 2 <= u <= 5 and v <= 3:
            return mul(hair, 0.85)
        if style == 'bun' and face == 'top' and 2 <= u <= 5 and v >= 5:
            return mul(hair, 0.9)
        if style == 'pony' and face == 'back' and 3 <= u <= 4:
            return hair
        return None

    s.paint('hat', hatlayer)

    # ---- body
    ts = top_style

    def body(face, u, v, w, h):
        if face == 'top':
            return top if ts not in ('tank',) else top
        if face == 'bottom':
            return bottom
        if v >= 10:
            if bstyle in ('skirt', 'dress'):
                return bottom if ts != 'dress' else top
            return bottom if ts != 'dress' else top
        if ts == 'uniform':
            if face == 'front' and u in (3, 4) and v < 6:
                return accent if u == 3 or v > 1 else hexc('#c0392b')
            if face == 'front' and v in (3, 6, 9) and u == 2:
                return hexc('#d8a630')
        if ts == 'shirt' and face == 'front' and u in (3, 4) and v == 0:
            return mul(top, 0.8)
        if ts == 'kimono' and face == 'front':
            if (u == 3 and v < 5) or (u == 4 and v < 4):
                return accent
            if v in (6, 7):
                return hexc(p.get('obi', '#c0392b'))
        if ts == 'vest' and face == 'front' and u in (3, 4):
            return accent
        if ts == 'hoodie' and face == 'front' and 2 <= u <= 5 and 6 <= v <= 8:
            return mul(top, 0.85)
        if p.get('pattern') == 'stripe' and v % 3 == 0:
            return mul(top, 0.8)
        if p.get('pattern') == 'flower' and (u * 3 + v * 5) % 7 == 0:
            return accent
        return top

    s.paint('body', body)

    def jacket(face, u, v, w, h):
        ap = p.get('apron')
        if ap and face == 'front' and v >= 2:
            if v == 2 and u in (0, 7):
                return None
            return hexc(ap)
        if ap and face in ('right', 'left') and v == 6:
            return hexc(ap)
        if ap and face == 'back' and v == 6 and u in (3, 4):
            return hexc(ap)
        if p.get('coat') and face != 'bottom' and face != 'top':
            if face == 'front' and u in (3, 4) and v < 10:
                return None
            return hexc(p['coat'])
        return None

    s.paint('jacket', jacket)

    # ---- arms
    sleeve = {'tshirt': 4, 'shirt': 11, 'uniform': 11, 'kimono': 9, 'vest': 4, 'hoodie': 11, 'blouse': 6, 'tank': 0, 'dress': 3}.get(ts, 4)

    def arm(face, u, v, w, h):
        if face == 'top':
            return top if sleeve > 0 else skin
        if face == 'bottom':
            return skin
        if v < sleeve:
            if ts == 'vest' and v < 4:
                return hexc(p.get('shirt', '#f4f4f0'))
            return top
        if ts == 'uniform' and v == sleeve:
            return accent
        return skin

    s.paint('rarm', arm)
    s.paint('larm', arm)
    if p.get('coat'):
        def coat_arm(face, u, v, w, h):
            if face in ('top',) or v < 11:
                return hexc(p['coat']) if face != 'bottom' else None
            return None
        s.paint('rsleeve', coat_arm)
        s.paint('lsleeve', coat_arm)

    # ---- legs
    blen = {'pants': 10, 'shorts': 4, 'skirt': 5, 'long_skirt': 9, 'dress': 5, 'monpe': 10}.get(bstyle, 10)

    def leg(face, u, v, w, h):
        if face == 'top':
            return bottom
        if face == 'bottom' or v >= 10:
            return shoes
        if ts == 'dress' and v < blen:
            return top
        if v < blen:
            if bstyle == 'monpe' and v > 7:
                return mul(bottom, 0.85)
            return bottom
        return hexc(p.get('socks', '#f4f4f0')) if v >= 9 and p.get('socks') else skin

    s.paint('rleg', leg)
    s.paint('lleg', leg)
    return s.im


SKINS = {
    'grandma': dict(skin='#ecc49c', hair='#c9c6c0', hair_style='bun', top='#7a6a8a', top_style='shirt', pattern='flower', accent='#e8d8f0',
                    bottom='#4a4560', bottom_style='monpe', shoes='#3a3a3a', apron='#e8e2d0', old=True),
    'mom': dict(skin='#f2cca6', hair='#3a2418', hair_style='pony', top='#e8a0a0', top_style='blouse', bottom='#3a4a6a', bottom_style='long_skirt',
                apron='#f4f1e6', shoes='#e8e4dc'),
    'farmer_old': dict(skin='#d0a070', hair='#9a968e', hair_style='bald', top='#e8e4d8', top_style='shirt', bottom='#5a5a4a', shoes='#2a3a2a',
                       hat='straw', old=True, beard='#a8a49c'),
    'market_man': dict(skin='#dcaa80', hair='#2a2420', hair_style='buzz', top='#2f6a3a', top_style='vest', shirt='#f4f4f0', accent='#f4f4f0',
                       bottom='#3a3a3a', hat='cap', hat_color='#2f6a3a'),
    'yorozuya_lady': dict(skin='#eec6a0', hair='#5a3a2a', hair_style='short', top='#d8a050', top_style='shirt', pattern='flower', accent='#f4ecd8',
                          bottom='#5a4a6a', bottom_style='monpe', apron='#3a6aa8', hat='scarf', hat_color='#e8e0d0', old=True),
    'kid_boy': dict(skin='#e8b88c', hair='#1e1612', hair_style='buzz', top='#4aa0e0', top_style='tshirt', bottom='#2a3a5a', bottom_style='shorts',
                    shoes='#e0e0e0', socks='#ffffff', hat='cap', hat_color='#d8392b'),
    'kid_girl': dict(skin='#f0c8a0', hair='#2a1a14', hair_style='bob', top='#ffd0e0', top_style='dress', bottom='#ffd0e0', bottom_style='dress',
                     shoes='#d8392b', blush=True),
    'old_man': dict(skin='#dcae86', hair='#e0dcd6', hair_style='bald', top='#8a9aa8', top_style='shirt', bottom='#4a4a50', old=True, glasses='#3a3a3a'),
    'station_staff': dict(skin='#eec6a0', hair='#1e1612', hair_style='short', top='#1f2f55', top_style='uniform', accent='#f4f4f0',
                          bottom='#1f2f55', shoes='#151515', hat='cap', hat_color='#1f2f55'),
    'chef': dict(skin='#e0b088', hair='#2a2420', hair_style='buzz', top='#f4f4f0', top_style='shirt', bottom='#3a3a3a', apron='#2a3a5a',
                 hat='scarf', hat_color='#2a3a5a'),
    'master': dict(skin='#e6be98', hair='#4a4440', hair_style='short', top='#f4f4f0', top_style='vest', shirt='#f4f4f0', accent='#2a2a2a',
                   coat=None, bottom='#2a2a2a', apron='#4a2a1a', beard='#4a4440', glasses='#6a4a2a'),
    'townsman': dict(skin='#e8bc94', hair='#1e1612', hair_style='short', top='#8aa8c8', top_style='shirt', bottom='#4a4a4a'),
    'townswoman': dict(skin='#f2cca6', hair='#4a2a1a', hair_style='long', top='#f4e8c8', top_style='blouse', bottom='#7a8a5a', bottom_style='long_skirt'),
    'fisher': dict(skin='#c89068', hair='#2a2420', hair_style='buzz', top='#5a7a8a', top_style='vest', shirt='#e8e4d8', accent='#c8a050',
                   bottom='#3a4a3a', hat='beret', hat_color='#6a7a5a', shoes='#1a2a1a'),
    'obaachan': dict(skin='#e8c09a', hair='#d8d4ce', hair_style='bun', top='#6a8aa8', top_style='shirt', bottom='#3a3a4a', bottom_style='long_skirt', old=True),
    'okami': dict(skin='#f0c8a0', hair='#2a1a14', hair_style='bun', top='#3a5a8a', top_style='kimono', accent='#f4f1e6', obi='#d8a630',
                  bottom='#3a5a8a', apron='#f4f1e6'),
    'info_lady': dict(skin='#f2cca6', hair='#3a2418', hair_style='bob', top='#2f6fb3', top_style='uniform', accent='#f4f4f0',
                      bottom='#2a3a5a', bottom_style='skirt', shoes='#1a1a1a', hat='beret', hat_color='#2f6fb3'),
    'cafe_staff': dict(skin='#f0c8a0', hair='#5a3a28', hair_style='pony', top='#f4f4f0', top_style='shirt', bottom='#2a2a2a', apron='#2a2a2a'),
    'shop_staff': dict(skin='#e8bc94', hair='#2a1e18', hair_style='short', top='#c8d8e8', top_style='shirt', bottom='#3a3a3a', apron='#c0392b'),
    'musician': dict(skin='#e0b088', hair='#6a4a2a', hair_style='long', top='#2a2a2a', top_style='tshirt', bottom='#3a4a6a', shoes='#f4f4f0', hat='beret', hat_color='#8a3a2a'),
    'tourist_a': dict(skin='#f0c8a0', hair='#2a1a14', hair_style='short', top='#f0c419', top_style='tshirt', bottom='#d8d0b8', bottom_style='shorts', shoes='#f4f4f0', hat='cap', hat_color='#2f6fb3'),
    'tourist_b': dict(skin='#f4d4b4', hair='#c8a060', hair_style='long', top='#ffffff', top_style='blouse', pattern='flower', accent='#e88aa8', bottom='#6a8ab8', bottom_style='skirt', shoes='#d8a080'),
    'tourist_c': dict(skin='#a87050', hair='#1a1210', hair_style='buzz', top='#3aa655', top_style='tshirt', bottom='#2a2a3a', shoes='#d8392b'),
    'tourist_d': dict(skin='#f0c8a0', hair='#3a2418', hair_style='bob', top='#a8d8f0', top_style='tshirt', bottom='#f4f4f0', bottom_style='skirt', shoes='#e8e4dc', glasses='#2a2a2a'),
    'crepe_girl': dict(skin='#f2cca6', hair='#6a3a20', hair_style='pony', top='#ffb0c8', top_style='tshirt', bottom='#3a4a6a', apron='#ffffff', hat='cap', hat_color='#ff80a0'),
    'tower_staff': dict(skin='#eec6a0', hair='#2a1e18', hair_style='bob', top='#c62a22', top_style='uniform', accent='#f4f4f0', bottom='#2a2a2a', bottom_style='skirt', shoes='#1a1a1a'),
    'guide_old': dict(skin='#e0b490', hair='#d0ccc4', hair_style='short', top='#e8d860', top_style='vest', shirt='#f4f4f0', accent='#f4f4f0', bottom='#5a5a60', old=True, hat='cap', hat_color='#e8d860'),
    'museum_staff': dict(skin='#eec6a0', hair='#2a1e18', hair_style='short', top='#1f3a6a', top_style='uniform', accent='#f4f4f0', bottom='#1f3a6a', hat='cap', hat_color='#1f3a6a'),
    'chinese_vendor': dict(skin='#f0c8a0', hair='#1a1210', hair_style='bun', top='#c0392b', top_style='kimono', accent='#d8a630', obi='#d8a630', bottom='#2a2a2a'),
    'dimsum_cook': dict(skin='#e0b088', hair='#1a1210', hair_style='buzz', top='#f4f4f0', top_style='kimono', accent='#c0392b', obi='#c0392b', bottom='#2a2a2a', hat='chef'),
    'patissier': dict(skin='#f2cca6', hair='#5a3a28', hair_style='short', top='#f8f8f6', top_style='uniform', accent='#c8b8a0', bottom='#3a3a3a', hat='chef'),
    'baker': dict(skin='#e8bc94', hair='#3a2418', hair_style='short', top='#f4ecd8', top_style='shirt', bottom='#6a5a4a', apron='#a87a4a', hat='scarf', hat_color='#a87a4a'),
    'shopkeeper_old': dict(skin='#e0b490', hair='#bcb8b0', hair_style='short', top='#3a4a5a', top_style='kimono', accent='#f4f1e6', obi='#2a2a2a', bottom='#3a4a5a', old=True, glasses='#6a4a2a'),
    'salaryman': dict(skin='#e8bc94', hair='#1e1612', hair_style='short', top='#2a2e38', top_style='uniform', accent='#f4f4f0', bottom='#2a2e38', shoes='#151515'),
    'artist': dict(skin='#e0b088', hair='#8a7a6a', hair_style='short', top='#c8a878', top_style='vest', shirt='#e8e4d8', accent='#6a4a2a', bottom='#4a4a3a', hat='beret', hat_color='#2a2a2a', beard='#8a7a6a', old=True),
    'photographer': dict(skin='#dcaa80', hair='#2a2420', hair_style='short', top='#6a6a50', top_style='vest', shirt='#2a2a2a', accent='#4a4a3a', bottom='#2a2a2a', hat='cap', hat_color='#2a2a2a'),
    'couple_m': dict(skin='#eec6a0', hair='#3a2418', hair_style='short', top='#f4f4f0', top_style='shirt', bottom='#2a3a5a'),
    'couple_f': dict(skin='#f4d4b4', hair='#5a3a28', hair_style='long', top='#f0b0c0', top_style='dress', bottom='#f0b0c0', bottom_style='dress', shoes='#f4f4f0'),
    'student': dict(skin='#f0c8a0', hair='#1e1612', hair_style='short', top='#f4f4f0', top_style='shirt', bottom='#2a2e3a', shoes='#3a2a1a'),
}
