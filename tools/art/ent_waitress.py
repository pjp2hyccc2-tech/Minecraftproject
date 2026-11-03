"""給仕 the waitress (小野寺 千夏, 19) - 0.6 x 1.8. The night-shift waitress of HIDAMARI: brown dress, orange bib apron,
white round collar, name badge 「小野寺」, hair in a net, skin grey-white, a fixed polite smile far too wide, the eyes
black hollows, one shoe missing (left foot in a torn stocking), a round steel tray with a glass of ice water.
Poses: 0 WALK, 1 SERVE, 2 BOW, 3 STAND, 4 NOTICE, 5 CHASE."""
import math

import numpy as np

from common import C, rng
from ent_model import Spec, Part, Cube
from ent_lib import card
from ent_paint import Img, faces, Face, Noise3

SKIN = ['#4f5359', '#6c7074', '#8b8e8f', '#a6a8a6', '#bdbdb8', '#cfcec8', '#dedcd5']
HAIR = ['#09080a', '#121012', '#1b1717', '#251f1d', '#322a26', '#43382f']
DRESS = ['#1c110c', '#281810', '#352016', '#43291c', '#523323', '#613d2a']
APRON = ['#5e2608', '#7f340c', '#a04411', '#be5516', '#d6681d', '#e57f2c', '#ef9a48']
WHITE = ['#7d7f80', '#9d9e9c', '#b9b9b4', '#cfcec8', '#e0dfd9', '#eeede8']
STOCK = ['#4a3f3a', '#5d4f48', '#6f5f57', '#817067', '#938176']
SHOE = ['#08080a', '#121215', '#1d1d22', '#2b2b31', '#3d3d45']
STEEL = ['#3a3d41', '#55595e', '#737980', '#9399a0', '#b7bcc1', '#d9dde0', '#f2f4f5']


def P(hexes, i):
    return C(hexes[int(max(0, min(len(hexes) - 1, i)))])


def pal_arr(hexes):
    return np.array([C(h) for h in hexes], np.float32)


class Waitress(Spec):
    id = 'waitress'
    java_name = 'WaitressModel'
    module = 'ent_waitress'
    tex_w = 64
    tex_h = 64
    height = 1.8
    poses = [('WALK', 0), ('SERVE', 1), ('BOW', 2), ('STAND', 3), ('NOTICE', 4), ('CHASE', 5)]
    doc = """給仕 (the waitress, 小野寺 千夏): the night-shift waitress of HIDAMARI still serving water to customers nobody
    else can see. Brown dress, orange bib apron, white collar, name badge, hair in a net; grey-white skin, a polite
    smile far too wide and black hollow eyes; one shoe missing; a round steel tray with a glass of ice water.
    Pose ids: 0 WALK (tray at the shoulder, gliding without any bob, head tilted), 1 SERVE (bends, takes the glass
    from the tray and sets it down: the glass leaves her hand at pose tick 24), 2 BOW (a 90 degree bow, the tray
    kept level, loose strands fall forward; after 34 ticks the head slowly lifts while the body stays folded),
    3 STAND (tray before her, head tilted 30 degrees, tracking you without straightening), 4 NOTICE (the head rolls
    sideways to 90 degrees in jerks, the mouth opens), 5 CHASE (fast stiff walk, arms out, tray gone).
    The tray, its glass and the hand-held glass are parts of this model (hidden when not in use)."""

    def build(self):
        r = self.root
        body = Part('body', (0, 12, 0))
        body.cube(Cube((-3.5, -8.5, -1.75), (7, 8.5, 3.5), 'torso', tag='torso',
                       faces=('top', 'right', 'front', 'left', 'back')))
        body.cube(Cube((-1.5, -3.5, 1.85), (3, 1.5, 0.5), 'bow', tag='bow'))
        skirt = Part('skirt', (0, -0.5, 0), cubes=[Cube((-4, 0, -2.25), (8, 7, 4.5), 'skirt', tag='skirt')])
        neck = Part('neck', (0, -8.5, 0), cubes=[Cube((-1, -1.5, -1), (2, 1.5, 2), 'skin', tag='neck',
                                                      faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1.5, 0), cubes=[Cube((-3, -7, -3), (6, 7, 6), 'head', tag='head')])
        bun = Part('bun', (0, -4.5, 3), cubes=[Cube((-1.75, -1.5, 0), (3.5, 3, 2), 'bun', tag='bun',
                                                     faces=('top', 'bottom', 'right', 'left', 'back'))])
        lock_r = Part('lock_r', (-2.75, -4, -2.6), cubes=[Cube((-0.25, 0, -0.25), (0.5, 4.5, 0.5), 'hair', tag='lock_r')])
        lock_l = Part('lock_l', (2.75, -4, -2.6), cubes=[Cube((-0.25, 0, -0.25), (0.5, 4, 0.5), 'hair', tag='lock_l')])
        mouth = card('mouth', (0, -2.0, -3.1), (-2.5, 0, 0), (5, 3, 0), 'mouth')
        head.add(bun, lock_r, lock_l, mouth)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.6, -8, 0), cubes=[Cube((-1, -0.5, -1), (2, 5, 2), 'arm', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.5, 0), cubes=[Cube((-0.9, 0, -0.9), (2, 4.5, 2), 'skin', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.5, 0), cubes=[Cube((-0.9, 0, -0.65), (2, 2.5, 1.5), 'hand', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            fore.add(hand)
            arm.add(fore)
            arms[pre] = (arm, fore, hand)
        # the tray rests on the flat left hand (palm up when the forearm is raised)
        tray = Part('tray', (0.1, 2.3, 0.1), cubes=[Cube((-3.75, -0.5, -3.75), (7.5, 0.5, 7.5), 'tray', tag='tray')])
        glass = Part('glass', (-1.0, -0.5, 0.5), cubes=[Cube((-0.75, -2.5, -0.75), (1.5, 2.5, 1.5), 'glass', tag='glass')])
        tray.add(glass)
        arms['left'][2].add(tray)
        held = Part('held_glass', (0.1, 2.0, -0.6), cubes=[Cube((-0.75, -1.5, -0.75), (1.5, 2.5, 1.5), 'glass', tag='held_glass')])
        arms['right'][2].add(held)
        body.add(skirt, neck, arms['right'][0], arms['left'][0])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.4, 12, 0), cubes=[Cube((-1.2, 0, -1.2), (2.5, 6, 2.5), 'leg', tag=f'{pre}_thigh',
                                                                       faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0.05, 6, 0), cubes=[Cube((-1, 0, -1), (2, 5, 2.5), 'leg', tag=f'{pre}_shin',
                                                                faces=('right', 'front', 'left', 'back')),
                                                           Cube((-1, 5, -2.5), (2, 1, 3.5), 'foot', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        S = self.px
        W, H = self.tex_w * S, self.tex_h * S
        img, eyes = Img(W, H), Img(W, H)
        n3 = Noise3(4411, 3.0)
        n3b = Noise3(4412, 1.2)
        rr = rng(4410)

        def F(tag):
            return faces(img, self.cube(tag), S)

        def EF(tag):
            return faces(eyes, self.cube(tag), S)

        def base(tag, palette, mid, amp=0.6, grad=0.0, top=0.5, seed_scale=None, fine=0.35, wet=None):
            """Shaded base over every face of a cube from a palette, 3D noise + light from above + AO below."""
            pa = pal_arr(palette)
            fs = F(tag)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                nrm = f.normal()
                v = mid + n3(Pp) * amp + n3b(Pp) * fine
                v = v + nrm[1] * (-top)                         # up-facing lighter
                v = v - grad * np.clip((Pp[..., 1] - 6.0) / 18.0, 0, 1)
                if wet is not None:
                    v = v - wet(Pp)
                k = np.clip(np.round(v), 0, len(pa) - 1).astype(int)
                f.view()[:] = pa[k]
            return fs

        # ---------------------------------------------------------------- skin (arms, neck, hands)
        def ao_y(Pp, y0, y1, amt):
            return amt * np.clip((Pp[..., 1] - y0) / (y1 - y0), 0, 1)

        for tag in ('neck', 'right_fore', 'left_fore', 'right_hand', 'left_hand'):
            base(tag, SKIN, 4.3, amp=0.35, fine=0.3, top=0.6)
        # neck: shadow under the chin
        for n, f in F('neck').items():
            for x in range(f.w):
                f.dl(x, 0, -0.10)
                f.dl(x, 1, -0.05)
        # hands: knuckles, finger gaps, pale nails at the tips
        for pre in ('right', 'left'):
            fs = F(f'{pre}_hand')
            for n in ('front', 'back'):
                f = fs[n]
                for x in (1, 3):
                    for y in range(2, f.h):
                        f.dl(x, y, -0.07)
                for x in range(f.w):
                    f.dl(x, 1, -0.04)
                    f.set(x, f.h - 1, P(SKIN, 5) if x % 2 == 0 else P(SKIN, 4))
            fb = fs['bottom']
            for x in range(fb.w):
                for y in range(fb.h):
                    fb.set(x, y, P(SKIN, 5) if x % 2 == 0 else P(SKIN, 4))
            for n in ('right', 'left'):
                f = fs[n]
                for y in range(f.h):
                    f.dl(0, y, -0.05)
        # forearms: wrist line, a bruise on the right forearm (the accident)
        for pre in ('right', 'left'):
            fs = F(f'{pre}_fore')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]
                for x in range(f.w):
                    f.dl(x, f.h - 1, -0.05)
        f = F('right_fore')['right']
        for (x, y, d) in ((1, 3, -0.12), (2, 3, -0.15), (2, 4, -0.12), (1, 4, -0.08), (3, 4, -0.06)):
            f.dl(x, y, d)
            px = f.get(x, y)
            f.set(x, y, np.array([px[0] * 0.92, px[1] * 0.9, px[2] * 1.05, 255], np.float32))

        # ---------------------------------------------------------------- upper arms: short puffed brown sleeves
        for pre in ('right', 'left'):
            fs = base(f'{pre}_upper', DRESS, 3.2, amp=0.5, top=0.6)
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]
                for y in range(f.h):
                    for x in range(f.w):
                        if y >= 5:                      # skin below the sleeve
                            Pp = f.positions()[y, x]
                            v = 4.2 + n3(Pp[None])[0] * 0.35
                            f.set(x, y, P(SKIN, v))
                        elif y == 4:                    # white cuff band
                            f.set(x, y, P(WHITE, 3 if x % 3 else 2))
                        elif y == 3:
                            f.dl(x, y, -0.06)
                for x in range(f.w):                    # gathered puff at the top
                    if x % 2 == 0:
                        f.dl(x, 1, 0.05)
                        f.dl(x, 2, -0.05)
            fb = fs['bottom']
            fb.fill(P(SKIN, 3))

        # ---------------------------------------------------------------- torso: dress, bib apron, collar, badge
        fs = base('torso', DRESS, 3.0, amp=0.5, top=0.6)
        apron = pal_arr(APRON)
        f = fs['front']                                    # 14 x 17
        Pf = f.positions()
        for y in range(f.h):
            for x in range(f.w):
                bib = 3 <= x <= 10 and y >= 4
                strap = (x in (3, 4) or x in (9, 10)) and y < 4
                if bib or strap:
                    v = 4.0 + n3(Pf[y, x][None])[0] * 0.5 + n3b(Pf[y, x][None])[0] * 0.3
                    if x in (3, 10):
                        v -= 0.8                           # bib edge shade
                    if y >= 14:
                        v -= 0.4
                    f.set(x, y, apron[int(np.clip(round(v), 0, 6))])
        # waist band of the apron (the bib ends at the waist ties)
        for x in range(f.w):
            f.set(x, 15, P(APRON, 2.6 if 1 <= x <= 12 else 1.5))
            f.set(x, 16, P(APRON, 3.2) if 1 <= x <= 12 else P(DRESS, 2))
        # round white collar flaps around the neck opening
        collar = ['..wwwwww..wwwwww..',
                  ]
        for x in range(f.w):
            f.set(x, 0, P(WHITE, 4))
        for (x, y) in ((1, 1), (2, 1), (3, 1), (4, 1), (5, 1), (8, 1), (9, 1), (10, 1), (11, 1), (12, 1),
                       (2, 2), (3, 2), (4, 2), (9, 2), (10, 2), (11, 2), (3, 3), (10, 3)):
            f.set(x, y, P(WHITE, 4 if y < 2 else 3))
        for (x, y) in ((5, 2), (8, 2), (4, 3), (9, 3), (1, 2), (12, 2)):
            f.set(x, y, P(WHITE, 1))
        f.set(6, 1, P(DRESS, 1))                           # V of the dress under the collar
        f.set(7, 1, P(DRESS, 1))
        f.set(6, 2, P(DRESS, 2))
        f.set(7, 2, P(DRESS, 2))
        # name badge 「小野寺」 on her left chest (front texture right side)
        bx0, by0, bw, bh = 7, 5, 5, 3
        for y in range(by0, by0 + bh):
            for x in range(bx0, bx0 + bw):
                edge = y in (by0, by0 + bh - 1) or x in (bx0, bx0 + bw - 1)
                f.set(x, y, P(WHITE, 2) if edge else P(WHITE, 5))
        for x in (bx0 + 1, bx0 + 2, bx0 + 3):
            f.set(x, by0 + 1, '#3a3330' if x != bx0 + 2 else '#5a4e48')
        f.set(bx0 + 4, by0 + 1, '#c45a1e')                 # the little sun logo dot
        # stains: a dark smear on the apron bib (road water), grime at the right side
        for (x, y, d) in ((4, 9, -0.10), (5, 9, -0.12), (5, 10, -0.14), (6, 10, -0.08), (4, 10, -0.06), (5, 11, -0.07)):
            f.dl(x, y, d)
        # back: apron straps cross, ties at the waist
        fb = fs['back']
        for y in range(fb.h):
            for x in range(fb.w):
                d1 = abs((x - 2) - y * 0.62)
                d2 = abs((fb.w - 3 - x) - y * 0.62)
                if (d1 < 0.8 or d2 < 0.8) and y < 14:
                    fb.set(x, y, P(APRON, 3.6 + n3(fb.positions()[y, x][None])[0] * 0.5))
        for x in range(fb.w):
            fb.set(x, 15, P(APRON, 2.5))
            fb.set(x, 16, P(APRON, 3.2))
        for y in range(fb.h):                              # zip line
            fb.dl(fb.w // 2, y, -0.06)
        # sides: the apron band continues
        for n in ('left', 'right'):
            fsd = fs[n]
            for x in range(fsd.w):
                fsd.set(x, 15, P(APRON, 2.5))
                fsd.set(x, 16, P(APRON, 3.0))
                fsd.dl(x, 0, 0.04)
            for y in range(fsd.h):                         # side seam
                fsd.dl(fsd.w // 2, y, -0.05)
        ft = fs['top']
        for y in range(ft.h):
            for x in range(ft.w):
                if x in (3, 4, 9, 10):
                    ft.set(x, y, P(APRON, 4))
                elif 5 <= x <= 8 and 2 <= y <= 4:
                    ft.set(x, y, P(WHITE, 3))
        # the apron bow at the back of the waist
        bfs = faces(img, self.cube('bow'), S)
        for n, fc in bfs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 3.8 + n3(fc.positions()[y, x][None])[0] * 0.6
                    if n == 'back' and (x == fc.w // 2 or x == fc.w // 2 - 1):
                        v = 2.4                            # knot
                    fc.set(x, y, P(APRON, v))

        # ---------------------------------------------------------------- skirt
        pa = pal_arr(DRESS)
        fs = F('skirt')
        for n, fc in fs.items():
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = Pp[y, x]
                    fold = math.sin((pp[0] * 1.9 + pp[2] * 1.9) + n3(pp[None], 2.0)[0] * 1.2)
                    v = 2.8 + fold * 0.7 + n3b(pp[None])[0] * 0.3
                    if n == 'top':
                        v = 1.2
                    if n == 'bottom':
                        v = 0.6
                    if y == fc.h - 1 and n not in ('top', 'bottom'):
                        v -= 0.8                           # hem
                    fc.set(x, y, pa[int(np.clip(round(v), 0, 5))])
        f = fs['front']                                    # 16 x 14: the apron skirt
        Pf = f.positions()
        for y in range(0, 12):
            for x in range(2, 14):
                pp = Pf[y, x]
                fold = math.sin(pp[0] * 1.6 + n3(pp[None], 2.0)[0])
                v = 4.0 + fold * 0.6 + n3b(pp[None])[0] * 0.3
                if x in (2, 13):
                    v -= 0.9
                if y == 11:
                    v -= 1.2                               # apron hem stitch
                f.set(x, y, apron[int(np.clip(round(v), 0, 6))])
        for y in range(3, 7):                              # pocket
            f.dl(4, y, -0.10)
            f.dl(8, y, -0.10)
        for x in range(4, 9):
            f.dl(x, 3, -0.12)
            f.dl(x, 6, -0.06)
        f.set(6, 2, P(STEEL, 3))                           # order pad pen clip in the pocket
        f.set(6, 3, P(STEEL, 4))
        # road grime: dark wet smears rising from the hem on her right side (texture left)
        for n in ('front', 'right'):
            fc = fs[n]
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    g = n3(Pp[y, x][None] * 1.7 + 30.0)[0] + (y / fc.h) * 0.9 - (0.55 if n == 'front' else 0.2) \
                        - (x / fc.w) * (0.6 if n == 'front' else 0.0)
                    if g > 0.55:
                        fc.dl(x, y, -0.07 - 0.06 * min(1.0, g - 0.55))

        # ---------------------------------------------------------------- legs: stockings, one shoe, a torn stocking
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                fs = base(tag, STOCK, 2.8, amp=0.35, fine=0.25, top=0.4)
                for n in ('front', 'back', 'left', 'right'):
                    fc = fs[n]
                    for y in range(fc.h):          # sheen down the middle of the shin
                        if n == 'front':
                            fc.dl(fc.w // 2, y, 0.03)
            # knee
            fk = F(f'{pre}_thigh')['front']
            for x in range(fk.w):
                fk.dl(x, fk.h - 1, 0.03)
        # left: the stocking has runs and a hole at the knee
        fk = F('left_shin')['front']
        for (x, y) in ((1, 0), (2, 0), (1, 1), (2, 1), (3, 1)):
            fk.set(x, y, P(SKIN, 3))
        fk.set(2, 2, P(SKIN, 2))
        for y in range(2, fk.h):
            fk.dl(3, y, 0.07)
        ft = F('left_thigh')['front']
        for y in range(4, ft.h):
            ft.dl(1, y, 0.07)
        # feet: right has its black pump, left is only the dirty stocking
        fs = F('right_foot')
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 1.6 + n3(fc.positions()[y, x][None])[0] * 0.5
                    if n == 'top':
                        v = 2.5 if not (y >= fc.h - 3) else 1.6
                    fc.set(x, y, P(SHOE, v))
        ftop = fs['top']                                   # the instep in stocking above the pump opening
        for y in range(0, 3):
            for x in range(ftop.w):
                ftop.set(x, y, P(STOCK, 2.5))
        fs = F('left_foot')
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 2.4 + n3(fc.positions()[y, x][None])[0] * 0.4
                    fc.set(x, y, P(STOCK, v))
            if n in ('bottom',):
                fc.fill(P(SHOE, 3))
        for n in ('front', 'left', 'right', 'back'):
            fc = fs[n]
            for x in range(fc.w):
                fc.set(x, fc.h - 1, P(STOCK, 0))           # filthy sole edge
        fs['front'].set(1, 0, P(STOCK, 1))                 # toes

        # ---------------------------------------------------------------- head
        fs = F('head')
        hair = pal_arr(HAIR)
        for n, fc in fs.items():
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = Pp[y, x]
                    strand = math.sin(pp[0] * 5.0 + n3(pp[None], 1.5)[0] * 2.0)
                    v = 2.0 + strand * 0.7 + n3b(pp[None])[0] * 0.4
                    if n == 'top':
                        v += 0.6
                    fc.set(x, y, hair[int(np.clip(round(v), 0, 5))])
        # the face (front, 12 x 14)
        f = fs['front']
        sk = pal_arr(SKIN)
        face = [
            'hhhhhhhhhhhh',
            'hhhhhhhhhhhh',
            'hhhhHhhhhhhh',
            'hh5666666hhh',
            'h545566655hh',
            'h4AAA44AAA4h',
            'h3AXA33AXA3h',
            '4433344433#4',
            '#44445544444',
            '33#3444443#3',
            '2mttttttttm2',
            '22lLLLLLLl22',
            '#3334444333#',
            '##33333333##',
        ]
        leg = {'h': None, 'H': None, '#': P(SKIN, 1), '2': P(SKIN, 2), '3': P(SKIN, 3), '4': P(SKIN, 4),
               '5': P(SKIN, 5), '6': P(SKIN, 6), 'A': C('#050405'), 'X': C('#000000'),
               'm': C('#2a1716'), 't': C('#b9b2a2'), 'l': C('#4a2c2a'), 'L': C('#5f3a37')}
        for y, row in enumerate(face):
            for x, ch in enumerate(row):
                c = leg.get(ch)
                if c is not None:
                    f.set(x, y, c)
        # hairline: soft side part and the fringe pulled back into the net
        for (x, y) in ((3, 2), (4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2)):
            f.set(x, y, P(HAIR, 3 if x in (4, 5) else 2))
        for (x, y) in ((1, 3), (2, 3), (9, 3), (10, 3), (11, 3), (0, 4), (11, 4), (0, 5), (11, 5), (0, 6), (11, 6)):
            f.set(x, y, P(HAIR, 2))
        # eye hollows: deeper rims, sunken sockets
        for (x, y) in ((2, 4), (3, 4), (4, 4), (7, 4), (8, 4), (9, 4)):
            f.set(x, y, P(SKIN, 3))
        for (x, y) in ((1, 5), (5, 5), (6, 5), (10, 5), (1, 6), (5, 6), (6, 6), (10, 6), (2, 7), (3, 7), (4, 7), (7, 7), (8, 7), (9, 7)):
            f.dl(x, y, -0.06)
        # teeth gaps in the too-wide smile, the corners pulled up into the cheeks
        for x in (3, 5, 7, 9):
            f.set(x, 10, C('#9d9686'))
        for (x, y) in ((0, 9), (11, 9), (0, 10), (11, 10)):
            f.set(x, y, P(SKIN, 2))
        f.set(1, 9, C('#3a2321'))
        f.set(10, 9, C('#3a2321'))
        f.set(0, 8, P(SKIN, 3))
        f.set(11, 8, P(SKIN, 3))
        # cheek dimples of the fixed smile
        f.dl(1, 8, -0.08)
        f.dl(10, 8, -0.08)
        # eyes layer: tiny dim pinpoints deep in the hollows
        ef = EF('head')['front']
        ef.set(3, 6, '#5e6268')
        ef.set(8, 6, '#5e6268')
        # sides: hair pulled back over the ears, the ear, temple skin
        for n in ('right', 'left'):
            fc = fs[n]                                     # 12 x 14, x = back->front on 'right', front->back on 'left'
            for y in range(fc.h):
                for x in range(fc.w):
                    xf = (fc.w - 1 - x) if n == 'right' else x      # 0 = front edge
                    skin = (y >= 6 and xf <= 3) or (y >= 9 and xf <= 6) or (y >= 11 and xf <= 8)
                    if skin:
                        v = 3.6 - (0.8 if y >= 12 else 0) - (0.4 if xf >= 5 else 0)
                        fc.set(x, y, P(SKIN, v))
            for (ex, ey) in ((5, 6), (6, 6), (5, 7), (6, 7), (5, 8), (6, 8)):     # the ear
                x = (fc.w - 1 - ex) if n == 'right' else ex
                fc.set(x, ey, P(SKIN, 3 if (ex, ey) != (6, 7) else 2))
        fb = fs['bottom']
        for y in range(fb.h):
            for x in range(fb.w):
                if y >= 4:
                    fb.set(x, y, P(SKIN, 2))
        # ---------------------------------------------------------------- bun in a net
        fs = F('bun')
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 2.2 + n3(fc.positions()[y, x][None], 1.0)[0] * 0.8
                    netl = (x + y) % 3 == 0 or (x - y) % 3 == 0
                    fc.set(x, y, P(HAIR, v + (1.5 if netl else 0)) if not netl else C('#3f3a3a'))
        for tag in ('lock_r', 'lock_l'):
            fs = F(tag)
            for n, fc in fs.items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(HAIR, 2 + (1 if y % 3 == 0 else 0)))

        # ---------------------------------------------------------------- mouth card (opened in NOTICE / CHASE)
        f = F('mouth')['front']                            # 10 x 6
        for y in range(f.h):
            for x in range(f.w):
                cx = (x + 0.5) / f.w - 0.5
                inside = abs(cx) < 0.5 - 0.06 * y
                if inside:
                    v = '#060203' if y >= 1 else '#24100f'
                    f.set(x, y, v)
        for x in range(1, f.w - 1):                        # upper teeth row hanging at the top
            f.set(x, 0, '#b9b2a2' if x % 2 else '#9d9686')
        for x in range(2, f.w - 2):                        # lower lip at the bottom of the stretched mouth
            f.set(x, f.h - 1, '#4a2c2a')
        f.set(1, f.h - 2, '#4a2c2a')
        f.set(f.w - 2, f.h - 2, '#4a2c2a')

        # ---------------------------------------------------------------- tray + glasses
        fs = F('tray')                                     # 15 x 15 disc, rim 1 texel
        for n, fc in fs.items():
            if n in ('top', 'bottom'):
                for y in range(fc.h):
                    for x in range(fc.w):
                        dx, dy = (x + 0.5) / fc.w - 0.5, (y + 0.5) / fc.h - 0.5
                        rad = math.hypot(dx, dy)
                        if rad > 0.5:
                            fc.clear(x, y)
                            continue
                        if n == 'top':
                            v = 3.5 + 1.6 * math.exp(-((dx + 0.15) ** 2 + (dy + 0.18) ** 2) / 0.012) - 0.6 * rad
                            if rad > 0.43:
                                v = 5.2 - (dy + 0.5) * 2.0
                            if 0.33 < rad < 0.37:
                                v -= 0.8                   # pressed ring
                        else:
                            v = 2.0 + (0.6 if rad > 0.43 else 0.0)
                        fc.set(x, y, P(STEEL, v))
            else:
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(STEEL, 4 + (0.8 if x == fc.w // 3 else 0)))
        for tag in ('glass', 'held_glass'):
            fs = F(tag)
            for n, fc in fs.items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        if n == 'top':
                            c = C('#cfe6ee')
                            c[3] = 150
                        elif n == 'bottom':
                            c = C('#9fb6be')
                            c[3] = 200
                        else:
                            water = y >= 1
                            ice = water and y <= 2 and (x + y) % 2 == 0
                            c = C('#e8f5f8') if ice else (C('#a9cdd8') if water else C('#dfeef2'))
                            c[3] = 205 if ice else (150 if water else 110)
                            if x == 0 and n == 'front':
                                c = C('#f4fbfd')
                                c[3] = 230         # bright edge
                        fc.set(x, y, c)
            ef = EF(tag)['front']
            ef.set(0, 1, '#2c3438')                        # faint glint in the ice
        return img, eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.055F);
float look = Mth.clamp(yaw, -1.1F, 1.1F);
float lookP = Mth.clamp(pitch, -0.5F, 0.6F);
// the head never straightens: a 30 degree tilt, with a slow drift and rare ticks the wrong way
float tilt = 0.5F + Mth.sin(t * 0.021F) * 0.04F - twitch(t, 97.0F, 13.0F) * 0.18F;
float jit = glitch * noise(t * 1.7F);
float jit2 = glitch * noise(t * 2.3F + 40.0F);
// smooth, unnatural constants
this.tray.visible = true;
this.glass.visible = true;
this.heldGlass.visible = false;
this.mouth.visible = false;
this.mouth.yScale = 0.01F;
this.lockR.zRot = 0.0F;
this.lockL.zRot = 0.0F;
float hipX = 0.0F;
float hipZ = 0.0F;

if (pose == WALK) {
    float a = Math.min(1.0F, limbSwingAmount * 1.5F);
    float c = limbSwing * 1.05F;
    float s = Mth.sin(c);
    // short quick regular steps; the body glides: no bob, no sway at all
    this.rightLeg.xRot = s * 0.42F * a;
    this.leftLeg.xRot = -s * 0.42F * a;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.2F)) * 0.55F * a;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.2F)) * 0.55F * a;
    this.body.xRot = 0.03F;
    this.skirt.xRot = -0.03F + s * 0.025F * a;
    // the free arm hangs stiff, a tiny pendulum
    this.rightArm.xRot = s * 0.07F * a + 0.02F;
    this.rightArm.zRot = 0.05F;
    this.rightForearm.xRot = -0.12F;
    // tray carried at the shoulder on the flat left hand, perfectly level
    this.leftArm.xRot = -0.3F;
    this.leftArm.yRot = 0.0F;
    this.leftArm.zRot = -0.42F;
    this.leftForearm.xRot = -2.25F;
    this.leftForearm.zRot = 0.4F;
    this.leftHand.xRot = 0.0F;
    this.head.zRot = tilt * 0.8F + jit * 0.4F;
    this.head.yRot = look * 0.8F;
    this.head.xRot = lookP * 0.6F + 0.08F;
} else if (pose == SERVE) {
    // bends at the hips, takes the glass off the tray with the right hand and sets it on the table
    float bend = span(pt, 0.0F, 12.0F);
    float reach = span(pt, 6.0F, 14.0F);
    float lower = span(pt, 14.0F, 24.0F);
    float back = span(pt, 26.0F, 36.0F);
    this.body.xRot = 0.62F * bend + breath * 0.01F;
    this.skirt.xRot = -0.5F * bend;
    this.neck.xRot = 0.1F * bend;
    this.head.xRot = 0.35F * bend + lookP * 0.3F;
    this.head.zRot = tilt * 0.9F + jit * 0.4F;
    this.head.yRot = look * 0.4F;
    // tray: held out to the side on the left hand, kept level against the bend
    this.leftArm.xRot = -0.25F - 0.62F * bend;
    this.leftArm.zRot = -0.45F;
    this.leftForearm.xRot = -2.1F + 0.62F * bend;
    this.leftForearm.zRot = 0.42F;
    this.leftHand.xRot = 0.0F;
    // right hand: to the tray glass, then down to the table in front of her
    this.rightArm.xRot = -1.05F * reach + (-0.55F) * lower * (1.0F - back) + 0.3F * back;
    this.rightArm.zRot = -0.5F * reach * (1.0F - lower) + 0.06F;
    this.rightArm.yRot = -0.35F * reach * (1.0F - lower);
    this.rightForearm.xRot = -1.25F * reach * (1.0F - lower) - 0.5F * lower * (1.0F - back);
    this.rightHand.xRot = 0.35F * lower;
    boolean inHand = pt >= 12.0F && pt < 24.0F;
    this.heldGlass.visible = inHand;
    this.glass.visible = pt < 12.0F;
    this.rightLeg.xRot = 0.0F;
    this.leftLeg.xRot = 0.0F;
} else if (pose == BOW) {
    // a 90 degree bow, held: the tray stays level in the hanging left hand; loose strands fall forward;
    // near the end the head slowly lifts to look at the door while the body stays folded
    float down = easeOut(pt / 14.0F);
    float lift = span(pt, 34.0F, 70.0F);
    this.body.xRot = 1.57F * down + Mth.sin(pt * 0.05F) * 0.01F;
    this.skirt.xRot = -1.35F * down;
    this.neck.xRot = 0.1F * down - 0.85F * lift;
    this.head.xRot = 0.15F * down - 0.75F * lift;
    this.head.zRot = 0.08F * down + tilt * 0.6F * lift + jit * 0.3F;
    this.head.yRot = look * 0.3F * lift;
    this.rightArm.xRot = -1.45F * down;
    this.rightArm.zRot = 0.1F;
    this.rightForearm.xRot = -0.1F;
    this.leftArm.xRot = -1.57F * down - 0.2F;
    this.leftArm.zRot = -0.25F;
    this.leftForearm.xRot = -1.25F;
    this.leftForearm.zRot = 0.0F;
    this.leftHand.xRot = 0.0F;
} else if (pose == STAND) {
    // waiting: the tray held flat before her in both hands, head tilted 30 degrees, tracking you without
    // straightening; motionless but for a shallow breath
    this.body.xRot = 0.02F + breath * 0.008F;
    this.head.zRot = tilt + jit * 0.5F;
    this.head.yRot = look * 0.95F;
    this.head.xRot = lookP * 0.7F + 0.05F;
    this.neck.zRot = tilt * 0.15F;
    this.rightArm.xRot = -0.55F;
    this.rightArm.zRot = 0.18F;
    this.rightArm.yRot = 0.15F;
    this.rightForearm.xRot = -0.8F;
    this.leftArm.xRot = -0.55F;
    this.leftArm.zRot = -0.18F;
    this.leftArm.yRot = -0.15F;
    this.leftForearm.xRot = -0.8F;
    this.leftHand.xRot = 0.0F;
} else if (pose == NOTICE) {
    // the head rolls over sideways to 90 degrees in three jerks, the polite mouth splits open
    float roll = jerky(pt / 14.0F, 3.0F);
    float open = span(pt, 5.0F, 12.0F);
    this.head.zRot = tilt + (1.6F - tilt) * roll + jit * 0.6F;
    this.head.yRot = look;
    this.head.xRot = lookP * 0.5F;
    this.neck.zRot = 0.25F * roll;
    this.mouth.visible = open > 0.01F;
    this.mouth.yScale = Math.max(0.01F, open * (0.9F + noise(t * 0.9F) * 0.08F));
    this.body.xRot = 0.04F;
    this.rightArm.xRot = -0.35F * open;
    this.rightArm.zRot = 0.12F;
    this.leftArm.xRot = -0.55F;
    this.leftArm.zRot = -0.18F;
    this.leftForearm.xRot = -0.8F;
} else {
    // CHASE: fast and stiff, knees barely bending, arms straight out, the head lolled over sideways, mouth open
    float a = Math.min(1.0F, limbSwingAmount * 1.3F);
    float c = limbSwing * 1.35F;
    float s = Mth.sin(c);
    this.tray.visible = false;
    this.glass.visible = false;
    this.rightLeg.xRot = s * 0.75F * a;
    this.leftLeg.xRot = -s * 0.75F * a;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.4F)) * 0.25F * a;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.4F)) * 0.25F * a;
    this.body.xRot = 0.16F;
    this.skirt.xRot = -0.12F + s * 0.05F * a;
    this.rightArm.xRot = -1.45F + s * 0.05F * a + jit2 * 0.3F;
    this.leftArm.xRot = -1.45F - s * 0.05F * a - jit2 * 0.3F;
    this.rightArm.zRot = 0.08F;
    this.leftArm.zRot = -0.08F;
    this.rightForearm.xRot = -0.08F;
    this.leftForearm.xRot = -0.08F;
    this.head.zRot = 1.35F + Mth.sin(t * 0.13F) * 0.06F + jit;
    this.head.yRot = look * 0.6F;
    this.head.xRot = lookP * 0.4F - 0.08F;
    this.mouth.visible = true;
    this.mouth.yScale = 0.85F + Mth.sin(t * 0.4F) * 0.05F;
    blend = 4.0F;
}
// breath in the chest, glitch jolts at the joints
this.body.y = 12.0F;
this.neck.zRot = this.neck.zRot + jit2 * 0.25F;
this.rightArm.zRot = this.rightArm.zRot + jit * 0.2F;
if (pose == NOTICE) {
    blend = 2.0F;
}
'''


Waitress.post = '''
// after the blend: the tray stays level on her palm, the glasses stand upright, loose strands hang down
level(this.tray, this.body, this.leftArm, this.leftForearm, this.leftHand);
this.tray.zRot = this.tray.zRot + (pose == NOTICE ? 0.3F * span(pt, 5.0F, 12.0F) : 0.0F);
level(this.heldGlass, this.body, this.rightArm, this.rightForearm, this.rightHand);
level(this.lockR, this.body, this.neck, this.head);
level(this.lockL, this.body, this.neck, this.head);
this.lockR.xRot = this.lockR.xRot + Mth.sin(t * 0.07F) * 0.05F;
this.lockL.xRot = this.lockL.xRot + Mth.sin(t * 0.07F + 1.1F) * 0.05F;
'''


SPEC = Waitress


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    walk = lambda tt: (tt * 0.55, 0.85)                         # noqa: E731
    sc = SC.dining()
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(3, 60, yaw_view=yv, scene=sc)))
    ims.append(('head track 50', b.view(3, 60, yaw_view=20, head=lambda tt: (50.0, 0.0), scene=sc)))
    ims.append(('close', b.view(3, 60, yaw_view=12, pitch_view=4, size=(190, 190), ortho=0.42, center_y=1.5, scene=sc)))
    rows.append(('STAND (3): tray before her, head tilted 30, tracking you without straightening', ims))
    ims = []
    for k, tt in enumerate((0, 6, 12, 18, 24)):
        ims.append((f'side ls {tt}', b.view([(0, 0)], 100 + tt, yaw_view=90, limb=walk, scene=sc)))
    ims.append(('front', b.view([(0, 0)], 112, yaw_view=0, limb=walk, scene=sc)))
    ims.append(('3/4', b.view([(0, 0)], 112, yaw_view=-40, limb=walk, scene=sc)))
    rows.append(('WALK (0): gliding, tray at the shoulder, head tilted', ims))
    ims = []
    for ptv in (0, 6, 12, 18, 24, 32):
        ims.append((f'pt {ptv}', b.view([(0, 3), (100, 1)], 100 + ptv, yaw_view=70, scene=SC.dining(table=True))))
    ims.append(('front pt 20', b.view([(0, 3), (100, 1)], 120, yaw_view=10, scene=SC.dining(table=True))))
    rows.append(('SERVE (1): bend, take the glass, set it down (glass leaves the hand at pt 24)', ims))
    ims = []
    for ptv in (0, 5, 10, 20, 40, 60):
        ims.append((f'pt {ptv}', b.view([(0, 3), (100, 2)], 100 + ptv, yaw_view=75, scene=sc)))
    ims.append(('front pt 70', b.view([(0, 3), (100, 2)], 170, yaw_view=8, pitch_view=4, scene=sc)))
    rows.append(('BOW (2): 90 degrees, tray level, strands fall; the head lifts after 34 ticks', ims))
    ims = []
    for ptv in (0, 3, 6, 9, 12, 20):
        ims.append((f'pt {ptv}', b.view([(0, 3), (100, 4)], 100 + ptv, yaw_view=5, scene=sc)))
    ims.append(('close pt 20', b.view([(0, 3), (100, 4)], 120, yaw_view=5, pitch_view=2, size=(190, 190), ortho=0.42,
                                      center_y=1.45, scene=sc)))
    rows.append(('NOTICE (4): head rolls to 90 in jerks, mouth splits open', ims))
    run = lambda tt: (tt * 0.9, 1.0)                            # noqa: E731
    ims = []
    for k, tt in enumerate((0, 3, 6, 9)):
        ims.append((f'side t{tt}', b.view([(0, 5)], 200 + tt, yaw_view=90, limb=run, scene=sc)))
    ims.append(('front', b.view([(0, 5)], 204, yaw_view=0, limb=run, scene=sc)))
    ims.append(('3/4 glitch', b.view([(0, 5)], 207, yaw_view=-35, limb=run, glitch=lambda tt: 0.8, scene=sc)))
    rows.append(('CHASE (5): fast stiff walk, arms out, tray gone', ims))
    ims = []
    for ptv in (0, 2, 4, 6, 8):
        ims.append((f'STAND>WALK {ptv}', b.view([(0, 3), (100, 0)], 100 + ptv, yaw_view=40, limb=walk, scene=sc)))
    ims.append(('BOW>STAND 4', b.view([(0, 2), (100, 3)], 104, yaw_view=60, scene=sc)))
    rows.append(('blends between poses (8 ticks, eased)', ims))
    ims = []
    eye = (0.4, 1.62, 6.0)
    ims.append(('naked eye a=0.3, flashlight', b.view(3, 60, size=(300, 260), eye=eye, target=(0, 1.2, 0), night=True,
                                                      alpha=0.3, scene=sc, fov=50)))
    ims.append(('finder (a=1)', b.view(3, 60, size=(300, 260), eye=eye, target=(0, 1.2, 0), finder=True, scene=sc, fov=50)))
    ims.append(('finder: NOTICE', b.view([(0, 3), (100, 4)], 120, size=(300, 260), eye=(0.2, 1.62, 3.0),
                                         target=(0, 1.4, 0), finder=True, scene=sc, fov=50)))
    ims.append(('night: BOW at the door', b.view([(0, 3), (100, 2)], 150, size=(300, 260), eye=(2.4, 1.62, 4.0),
                                                 target=(0, 0.9, 0), night=True, alpha=0.3, scene=sc, fov=50)))
    rows.append(('in the restaurant at night: naked eye / through the finder', ims))
    return rows
