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
        # proportions: 29 px tall, legs 13.5 (hip joint at y 10.5), knee-length A-line skirt, slender arms
        body = Part('body', (0, 10.5, 0))
        body.cube(Cube((-3.25, -7.5, -1.75), (6.5, 7.5, 3.5), 'torso', tag='torso',
                       faces=('top', 'right', 'front', 'left', 'back')))
        body.cube(Cube((-1.5, -2.5, 1.85), (3, 1.5, 0.5), 'bow', tag='bow'))
        skirt = Part('skirt', (0, -0.5, 0), cubes=[Cube((-4, 0, -2.25), (8, 7, 4.5), 'skirt', tag='skirt')])
        neck = Part('neck', (0, -7.5, 0), cubes=[Cube((-1, -1.5, -1), (2, 1.5, 2), 'skin', tag='neck',
                                                      faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1.5, 0), cubes=[Cube((-3, -6.5, -2.75), (6, 6.5, 5.5), 'head', tag='head')])
        bun = Part('bun', (0, -4.0, 2.75), cubes=[Cube((-1.75, -1.5, 0), (3.5, 3, 2), 'bun', tag='bun',
                                                       faces=('top', 'bottom', 'right', 'left', 'back'))])
        lock_r = Part('lock_r', (-2.6, -3.6, -2.45), cubes=[Cube((-0.25, 0, -0.25), (0.5, 4.5, 0.5), 'hair', tag='lock_r')])
        lock_l = Part('lock_l', (2.6, -3.6, -2.45), cubes=[Cube((-0.25, 0, -0.25), (0.5, 3.5, 0.5), 'hair', tag='lock_l')])
        # the split mouth: a dark gap (stretched by yScale) behind a jaw card (lower lip + chin) that slides down
        mouth = card('mouth', (0, -1.5, -2.83), (-2.5, 0, 0), (5, 2, 0), 'mouth')
        jaw = card('jaw', (0, -1.0, -2.9), (-2.5, 0, 0), (5, 1, 0), 'jaw')
        glare = card('glare', (0, -4.0, -2.85), (-2.5, -0.5, 0), (5, 1.5, 0), 'glare')
        head.add(bun, lock_r, lock_l, mouth, jaw, glare)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.35, -7.0, 0), cubes=[Cube((-1, -0.5, -1), (2, 5, 2), 'arm', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.5, 0), cubes=[Cube((-0.75, 0, -0.75), (1.5, 4.5, 1.5), 'skin', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.5, 0), cubes=[Cube((-0.75, 0, -0.5), (1.5, 2.5, 1), 'hand', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            fore.add(hand)
            arm.add(fore)
            arms[pre] = (arm, fore, hand)
        # the tray rests on the flat left hand (palm up when the forearm is raised); it is levelled after the blend
        tray = Part('tray', (0.0, 1.2, 0.0), cubes=[Cube((-3.75, -0.5, -3.75), (7.5, 0.5, 7.5), 'tray', tag='tray')])
        glass = Part('glass', (-1.0, -0.5, 0.6), cubes=[Cube((-0.75, -2.5, -0.75), (1.5, 2.5, 1.5), 'glass', tag='glass')])
        tray.add(glass)
        arms['left'][2].add(tray)
        held = Part('held_glass', (0.0, 2.0, -0.5), cubes=[Cube((-0.75, -1.5, -0.75), (1.5, 2.5, 1.5), 'glass', tag='held_glass')])
        arms['right'][2].add(held)
        body.add(skirt, neck, arms['right'][0], arms['left'][0])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.3, 10.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6.5, 2.5), 'leg', tag=f'{pre}_thigh',
                                                                         faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 6.5, 0), cubes=[Cube((-1, 0, -1), (2, 6, 2), 'leg', tag=f'{pre}_shin',
                                                               faces=('right', 'front', 'left', 'back')),
                                                          Cube((-1, 6, -2.5), (2, 1, 3.5), 'foot', tag=f'{pre}_foot')])
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

        def F(tag):
            return faces(img, self.cube(tag), S)

        def EF(tag):
            return faces(eyes, self.cube(tag), S)

        def nz(f, x, y, a=0.45, b=0.3):
            pp = f.positions()[y, x][None]
            return n3(pp)[0] * a + n3b(pp)[0] * b

        def base(tag, palette, mid, amp=0.6, top=0.5, fine=0.35):
            pa = pal_arr(palette)
            fs = F(tag)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                nrm = f.normal()
                v = mid + n3(Pp) * amp + n3b(Pp) * fine - nrm[1] * top
                k = np.clip(np.round(v), 0, len(pa) - 1).astype(int)
                f.view()[:] = pa[k]
            return fs

        def pattern(f, rows, legend, ox=0, oy=0, jitter=True):
            """legend: char -> (palette, index) | colour | None. Palette entries get a little 3D noise."""
            for y, row in enumerate(rows):
                for x, ch in enumerate(row):
                    e = legend.get(ch)
                    if e is None:
                        continue
                    if isinstance(e, tuple):
                        pal_, i = e
                        v = i + (nz(f, ox + x, oy + y) if jitter else 0.0)
                        f.set(ox + x, oy + y, P(pal_, v))
                    else:
                        f.set(ox + x, oy + y, e)

        # ---------------------------------------------------------------- skin (neck, forearms, hands)
        for tag in ('neck', 'right_fore', 'left_fore', 'right_hand', 'left_hand'):
            base(tag, SKIN, 4.4, amp=0.3, fine=0.25, top=0.6)
        for n, f in F('neck').items():
            for x in range(f.w):
                f.dl(x, 0, -0.12)
                f.dl(x, 1, -0.05)
        for pre in ('right', 'left'):
            fs = F(f'{pre}_hand')                          # 3 wide x 5 long: fingers together, pale nails
            for n in ('front', 'back'):
                f = fs[n]
                for y in range(2, f.h):
                    f.dl(1, y, -0.06)
                for x in range(f.w):
                    f.dl(x, 1, -0.05)
                    f.set(x, f.h - 1, P(SKIN, 5))
            for n in ('right', 'left'):
                f = fs[n]
                for y in range(f.h):
                    f.dl(0, y, -0.04)
            fs['bottom'].fill(P(SKIN, 5))
            fs = F(f'{pre}_fore')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]
                for x in range(f.w):
                    f.dl(x, f.h - 1, -0.05)
        f = F('right_fore')['right']                       # a bruise (the accident) on the right forearm
        for (x, y, d) in ((0, 3, -0.10), (1, 3, -0.15), (1, 4, -0.13), (2, 4, -0.07), (1, 5, -0.06)):
            f.dl(x, y, d)
            px = f.get(x, y)
            f.set(x, y, np.array([px[0] * 0.9, px[1] * 0.9, px[2] * 1.06, 255], np.float32))

        # ---------------------------------------------------------------- upper arms: puffed brown sleeves
        for pre in ('right', 'left'):
            fs = base(f'{pre}_upper', DRESS, 3.2, amp=0.5, top=0.6)
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]                                  # 4 x 10
                for y in range(f.h):
                    for x in range(f.w):
                        if y >= 6:
                            f.set(x, y, P(SKIN, 4.2 + nz(f, x, y, 0.3, 0.2) - (0.6 if y == 6 else 0)))
                        elif y == 5:
                            f.set(x, y, P(WHITE, 3 if x % 3 else 2))
                        elif y == 4:
                            f.dl(x, y, -0.08)
                for x in range(f.w):
                    if x % 2 == 0:
                        f.dl(x, 1, 0.06)
                        f.dl(x, 2, -0.04)
            fs['bottom'].fill(P(SKIN, 3))

        # ---------------------------------------------------------------- torso (13 x 15 front)
        fs = base('torso', DRESS, 3.0, amp=0.5, top=0.6)
        D, A, Wt = DRESS, APRON, WHITE
        leg = {'d': (D, 2.8), 'D': (D, 1.6), 'a': (A, 4.2), 'A': (A, 2.6), 'e': (A, 5.2), 'w': (Wt, 4.4),
               'W': (Wt, 2.3), 'v': (D, 0.6), 'b': (A, 3.0), 'B': (A, 3.6), 'k': C('#ece9e0'), 'K': C('#a9a59c'),
               'n': C('#2f2926'), 'o': C('#c45a1e'), 'p': (A, 3.4)}
        front = [
            'dwwwwvvvwwwwd',
            'dwwwWvvvWwwwd',
            'ddWwadvdawWdd',
            'dddAadddaAddd',
            'dddAaaaaKKKKK',
            'dddAaaaaKknno',
            'dddAaaaaKKKKK',
            'dddAaaaaaaaAd',
            'dddAaaaaaaaAd',
            'dddAaaeaaaaAd',
            'dddAaaaaaaaAd',
            'DbbbbbbbbbbbD',
            'DBBBBBBBBBBBD',
            'ddApaaaaaapAd',
            'dDApaaaaaapAD',
        ]
        pattern(fs['front'], front, leg)
        f = fs['front']
        for (x, y, dd) in ((4, 8, -0.10), (5, 8, -0.12), (5, 9, -0.14), (4, 9, -0.06), (6, 9, -0.05)):
            f.dl(x, y, dd)                                  # a dark water stain on the bib
        back = [
            'dddddddddddd d',
            'dAddddddddAdd',
            'ddAddddddAddd',
            'dddAddddAdddd',
            'ddddAddAddddd',
            'dddddAAdddddd',
            'ddddAddAddddd',
            'dddAddddAdddd',
            'ddAddddddAddd',
            'dAddddddddAdd',
            'ddddddDdddddd',
            'DbbbbbbbbbbbD',
            'DBBBBBBBBBBBD',
            'dddddddDddddd',
            'ddddddDdddddd',
        ]
        back[0] = 'ddddddddddddd'
        pattern(fs['back'], back, leg)
        for n in ('left', 'right'):
            f = fs[n]                                      # 7 x 15
            for x in range(f.w):
                f.set(x, 11, P(A, 3.0))
                f.set(x, 12, P(A, 3.6))
            for y in range(f.h):
                f.dl(f.w // 2, y, -0.05)
            for x in range(f.w):
                f.dl(x, 0, 0.05)
        top = fs['top']                                    # 13 x 7: collar ring and apron straps over the shoulders
        for y in range(top.h):
            for x in range(top.w):
                if x in (3, 9):
                    top.set(x, y, P(A, 4.6))
                elif 4 <= x <= 8 and 1 <= y <= 5:
                    top.set(x, y, P(Wt, 3.6) if (x in (4, 8) or y in (1, 5)) else P(SKIN, 2))
        bfs = faces(img, self.cube('bow'), S)
        for n, fc in bfs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 3.9 + nz(fc, x, y, 0.6, 0.2)
                    if n == 'back' and x in (fc.w // 2 - 1, fc.w // 2):
                        v = 2.4
                    if n == 'back' and y == fc.h - 1:
                        v -= 0.8
                    fc.set(x, y, P(A, v))

        # ---------------------------------------------------------------- skirt (16 x 14 front): apron over brown
        pa = pal_arr(DRESS)
        fs = F('skirt')
        for n, fc in fs.items():
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = Pp[y, x]
                    fold = math.sin((pp[0] * 1.7 + pp[2] * 1.7) + n3(pp[None], 2.0)[0] * 1.3)
                    v = 2.9 + fold * 0.75 + n3b(pp[None])[0] * 0.3
                    if n == 'top':
                        v = 1.0
                    if n == 'bottom':
                        v = 0.4
                    if y >= fc.h - 1 and n not in ('top', 'bottom'):
                        v -= 0.9
                    fc.set(x, y, pa[int(np.clip(round(v), 0, 5))])
        apron = pal_arr(APRON)
        f = fs['front']
        Pf = f.positions()
        for y in range(0, 12):
            for x in range(2, 14):
                pp = Pf[y, x]
                fold = math.sin(pp[0] * 1.5 + n3(pp[None], 2.0)[0] * 1.1)
                v = 4.1 + fold * 0.55 + n3b(pp[None])[0] * 0.3
                if x in (2, 13):
                    v -= 1.0
                if y == 11:
                    v -= 1.3
                f.set(x, y, apron[int(np.clip(round(v), 0, 6))])
        for y in range(3, 8):                              # the order-pad pocket, a pen clip
            f.dl(4, y, -0.12)
            f.dl(9, y, -0.12)
        for x in range(4, 10):
            f.dl(x, 3, -0.14)
            f.dl(x, 7, -0.07)
        f.set(7, 2, P(STEEL, 3))
        f.set(7, 3, P(STEEL, 5))
        for n in ('front', 'right'):                       # road grime rising from the hem on her right side
            fc = fs[n]
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    g = n3(Pp[y, x][None] * 1.7 + 30.0)[0] + (y / fc.h) * 0.9 - (0.55 if n == 'front' else 0.2) \
                        - (x / fc.w) * (0.6 if n == 'front' else 0.0)
                    if g > 0.55:
                        fc.dl(x, y, -0.07 - 0.07 * min(1.0, g - 0.55))

        # ---------------------------------------------------------------- legs
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                fs = base(tag, STOCK, 2.8, amp=0.35, fine=0.25, top=0.4)
                fc = fs['front']
                for y in range(fc.h):
                    fc.dl(fc.w // 2, y, 0.035)
        fk = F('left_shin')['front']                       # torn stocking: a hole at the knee, a long run
        for (x, y) in ((1, 0), (2, 0), (1, 1), (2, 1)):
            fk.set(x, y, P(SKIN, 3))
        fk.set(2, 2, P(SKIN, 2))
        for y in range(2, fk.h):
            fk.dl(2, y, 0.08)
        fs = F('right_foot')                               # the remaining black pump
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    v = 1.7 + nz(fc, x, y, 0.5, 0.2)
                    fc.set(x, y, P(SHOE, v))
        ftop = fs['top']
        for y in range(0, 3):
            for x in range(ftop.w):
                ftop.set(x, y, P(STOCK, 2.6))
        fs['front'].set(1, 0, P(SHOE, 4))
        fs = F('left_foot')                                # stocking only, filthy sole
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(STOCK, 2.4 + nz(fc, x, y, 0.4, 0.2)))
        fs['bottom'].fill(P(SHOE, 3))
        for n in ('front', 'left', 'right', 'back'):
            fc = fs[n]
            for x in range(fc.w):
                fc.set(x, fc.h - 1, P(STOCK, 0))

        # ---------------------------------------------------------------- head (face 12 x 13)
        fs = F('head')
        hair = pal_arr(HAIR)
        for n, fc in fs.items():
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = Pp[y, x]
                    strand = math.sin(pp[0] * 4.0 + pp[2] * 1.5 + n3(pp[None], 1.5)[0] * 2.0)
                    v = 2.0 + strand * 0.75 + n3b(pp[None])[0] * 0.35 + (0.5 if n == 'top' else 0.0)
                    fc.set(x, y, hair[int(np.clip(round(v), 0, 5))])
        sk = {'h': None, '#': (SKIN, 1), '2': (SKIN, 2), '3': (SKIN, 3), '4': (SKIN, 4), '5': (SKIN, 5),
              '6': (SKIN, 6), 'A': C('#070607'), 'X': C('#000000'), 'm': C('#2a1716'), 't': C('#bfb8a8'),
              'T': C('#948d7e'), 'l': C('#4d2d2b'), 'L': C('#64403b'), 'H': (HAIR, 3), 'b': (SKIN, 2.2),
              'a': C('#2b2c30')}
        face = [
            'hhhhhhhhhhhh',
            'hhHHHHHHHHhh',
            'h5566666655h',
            'h4bb4554bb4h',
            'h3AAA44AAA3h',
            'h2AXA33AXA2h',
            'h33a3443a33h',
            '4444455444#4',
            'm#34455443#m',
            '3m33#44#33m3',
            '2mtTtTtTtTm2',
            '22lLLLLLLl22',
            '#3334444333#',
        ]
        pattern(fs['front'], face, sk, jitter=False)
        f = fs['front']
        for (x, y) in ((0, 2), (11, 2), (0, 3), (11, 3), (0, 4), (11, 4)):
            f.set(x, y, P(HAIR, 2))
        for (x, y) in ((1, 4), (10, 4), (1, 6), (10, 6), (5, 6), (6, 6)):
            f.dl(x, y, -0.07)
        ef = EF('head')['front']
        ef.set(3, 5, '#565a60')
        ef.set(8, 5, '#565a60')
        for n in ('right', 'left'):
            fc = fs[n]                                     # 11 x 13, xf = 0 at the face edge
            for y in range(fc.h):
                for x in range(fc.w):
                    xf = (fc.w - 1 - x) if n == 'right' else x
                    skin = (y >= 5 and xf <= 2) or (y >= 7 and xf <= 4) or (y >= 10 and xf <= 6) or (y == 12 and xf <= 7)
                    if skin:
                        fc.set(x, y, P(SKIN, 3.3 - 0.25 * xf - (0.6 if y == 12 else 0) + nz(fc, x, y, 0.2, 0.1)))
            for (ex, ey, v) in ((5, 5, 2.6), (6, 5, 2.2), (5, 6, 2.9), (6, 6, 1.6), (5, 7, 2.7), (6, 7, 2.1), (5, 8, 2.4)):
                x = (fc.w - 1 - ex) if n == 'right' else ex
                fc.set(x, ey, P(SKIN, v))
            for y in range(1, 5):                          # hair combed back: streaks toward the bun
                for x in range(fc.w):
                    if (x + y * 2) % 5 == 0:
                        fc.dl(x, y, 0.05)
        fb = fs['bottom']
        for y in range(fb.h):
            for x in range(fb.w):
                if y >= 4:
                    fb.set(x, y, P(SKIN, 2))
        fs = F('bun')
        for n, fc in fs.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    netl = ((x + y) % 4 == 0 or (x - y) % 4 == 0) and (x + 2 * y) % 3 != 0
                    fc.set(x, y, C('#3b3634') if netl else P(HAIR, 2.0 + nz(fc, x, y, 0.8, 0.2)))
        for tag in ('lock_r', 'lock_l'):
            for n, fc in F(tag).items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(HAIR, 2 + (1 if y % 3 == 0 else 0)))

        # ---------------------------------------------------------------- mouth: dark gap + jaw (lower lip, chin)
        f = F('mouth')['front']                            # 10 x 4, stretched downwards by yScale
        for y in range(f.h):
            for x in range(f.w):
                cx = abs((x + 0.5) / f.w - 0.5) * 2
                if cx < 0.96 - 0.12 * y:
                    edge = cx > 0.74 - 0.12 * y
                    f.set(x, y, C('#2d1212') if edge else (C('#080203') if y > 0 else C('#1c0a0a')))
        for x in range(1, f.w - 1):
            f.set(x, 0, C('#bfb8a8') if x % 2 else C('#948d7e'))
        j = F('jaw')['front']                              # 10 x 2: the lower lip and the chin, moved down
        jrows = ['mlLLLLLLlm', '3334444333']
        jl = {'m': C('#2a1716'), 'l': C('#4d2d2b'), 'L': C('#64403b'), '3': (SKIN, 3), '4': (SKIN, 4)}
        pattern(j, jrows, jl, jitter=False)
        # glare card: base fully transparent, the eyes layer lights two pinpoints when she notices you
        g = F('glare')['front']                            # 10 x 3
        eg = EF('glare')['front']
        for (x, y) in ((2, 1), (7, 1)):
            eg.set(x, y, '#ffe9e0')
        for (x, y) in ((1, 1), (3, 1), (2, 0), (2, 2), (6, 1), (8, 1), (7, 0), (7, 2)):
            eg.set(x, y, '#5a2a26')

        # ---------------------------------------------------------------- tray + glasses
        fs = F('tray')
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
                            v = 3.4 + 1.7 * math.exp(-((dx + 0.15) ** 2 + (dy + 0.18) ** 2) / 0.012) - 0.6 * rad
                            if rad > 0.43:
                                v = 5.2 - (dy + 0.5) * 2.0
                            if 0.33 < rad < 0.37:
                                v -= 0.8
                        else:
                            v = 2.0 + (0.6 if rad > 0.43 else 0.0)
                        fc.set(x, y, P(STEEL, v))
            else:
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(STEEL, 4 + (0.8 if x == fc.w // 3 else 0)))
        for tag in ('glass', 'held_glass'):
            for n, fc in F(tag).items():
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
                            c[3] = 210 if ice else (160 if water else 120)
                            if x == 0 and n == 'front':
                                c = C('#f4fbfd')
                                c[3] = 235
                        fc.set(x, y, c)
            EF(tag)['front'].set(0, 1, '#2c3438')
        return img, eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.055F);
float look = Mth.clamp(yaw, -1.1F, 1.1F);
float lookP = Mth.clamp(pitch, -0.5F, 0.6F);
// the head never straightens: a 30 degree tilt with a slow drift; every few seconds it ticks upright for an instant
float tilt = 0.52F + Mth.sin(t * 0.021F) * 0.04F - twitch(t, 97.0F, 13.0F) * 0.22F;
float jit = glitch * noise(t * 1.7F);
float jit2 = glitch * noise(t * 2.3F + 40.0F);
this.tray.visible = true;
this.glass.visible = true;
this.heldGlass.visible = false;
this.mouth.visible = false;
this.jaw.visible = false;
this.mouth.yScale = 0.01F;
this.glare.visible = false;

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
    this.skirt.xRot = -0.03F + s * 0.03F * a;
    this.rightArm.xRot = s * 0.07F * a + 0.03F;
    this.rightArm.zRot = 0.05F;
    this.rightForearm.xRot = -0.15F;
    // the tray on the flat left palm beside her head
    this.leftArm.xRot = -0.25F;
    this.leftArm.zRot = -0.8F;
    this.leftForearm.zRot = -2.3F;
    this.leftForearm.xRot = -0.1F;
    this.head.zRot = tilt * 0.8F + jit * 0.4F;
    this.head.yRot = look * 0.8F;
    this.head.xRot = lookP * 0.6F + 0.06F;
} else if (pose == SERVE) {
    // bends, takes the glass off the tray with the right hand and sets it on the table (released at pt 24)
    float bend = span(pt, 0.0F, 10.0F);
    float k1 = span(pt, 4.0F, 12.0F);
    float k2 = span(pt, 13.0F, 23.0F);
    float k3 = span(pt, 25.0F, 35.0F);
    this.body.xRot = 0.5F * bend + breath * 0.008F;
    this.skirt.xRot = -0.42F * bend;
    this.neck.xRot = 0.12F * bend;
    this.head.xRot = 0.3F * bend + lookP * 0.3F;
    this.head.zRot = tilt * 0.9F + jit * 0.4F;
    this.head.yRot = look * 0.35F;
    // tray held low at her left side, out of the way of her face
    this.leftArm.xRot = 0.787F * bend;
    this.leftArm.yRot = -0.199F * bend;
    this.leftArm.zRot = -0.289F * bend;
    this.leftForearm.xRot = -2.027F * bend - 0.1F * (1.0F - bend);
    // right hand: rest -> the glass on the tray -> down to the table -> back to the side
    float rx = Mth.lerp(k1, 0.0F, -0.925F);
    float rz = Mth.lerp(k1, 0.05F, 0.157F);
    float ry = Mth.lerp(k1, 0.0F, -1.2F);
    float rf = Mth.lerp(k1, -0.15F, -1.855F);
    rx = Mth.lerp(k2, rx, -0.452F);
    rz = Mth.lerp(k2, rz, -0.274F);
    ry = Mth.lerp(k2, ry, -0.315F);
    rf = Mth.lerp(k2, rf, -1.156F);
    this.rightArm.xRot = Mth.lerp(k3, rx, -0.2F);
    this.rightArm.zRot = Mth.lerp(k3, rz, 0.06F);
    this.rightArm.yRot = Mth.lerp(k3, ry, 0.0F);
    this.rightForearm.xRot = Mth.lerp(k3, rf, -0.3F);
    this.heldGlass.visible = pt >= 12.0F && pt < 24.0F;
    this.glass.visible = pt < 12.0F;
} else if (pose == BOW) {
    // a 90 degree bow, held; the tray stays level by her left knee; loose strands fall forward;
    // after 34 ticks the head slowly lifts to stare at the door while the body stays folded
    float down = easeOut(pt / 14.0F);
    float lift = span(pt, 34.0F, 72.0F);
    this.body.xRot = 1.5F * down + Mth.sin(pt * 0.05F) * 0.01F;
    this.skirt.xRot = -1.3F * down;
    this.neck.xRot = 0.1F * down - 0.8F * lift;
    this.head.xRot = 0.12F * down - 0.75F * lift;
    this.head.zRot = 0.06F * down + tilt * 0.7F * lift + jit * 0.3F;
    this.head.yRot = look * 0.3F * lift;
    this.rightArm.xRot = 0.348F * down;
    this.rightArm.zRot = -0.448F * down + 0.05F;
    this.rightForearm.xRot = -1.586F * down;
    this.leftArm.xRot = 0.047F * down;
    this.leftArm.zRot = 0.381F * down;
    this.leftForearm.xRot = -1.866F * down;
} else if (pose == STAND) {
    // waiting: the tray flat before her in both hands, head tilted 30 degrees, tracking you without straightening
    this.body.xRot = 0.02F + breath * 0.008F;
    this.head.zRot = tilt + jit * 0.5F;
    this.head.yRot = look * 0.95F;
    this.head.xRot = lookP * 0.7F + 0.05F;
    this.neck.zRot = tilt * 0.12F;
    this.rightArm.xRot = 0.103F;
    this.rightArm.zRot = -0.183F;
    this.rightArm.yRot = -0.181F;
    this.rightForearm.xRot = -1.445F;
    this.leftArm.xRot = 0.103F;
    this.leftArm.zRot = 0.183F;
    this.leftArm.yRot = 0.181F;
    this.leftForearm.xRot = -1.445F;
    this.tray.x = -2.4F;
} else if (pose == NOTICE) {
    // the head rolls over sideways to 90 degrees in three jerks, the polite mouth splits open, the hollows light up
    float roll = jerky(pt / 14.0F, 3.0F);
    float open = span(pt, 5.0F, 12.0F);
    this.head.zRot = tilt + (1.6F - tilt) * roll + jit * 0.6F;
    this.head.yRot = look;
    this.head.xRot = lookP * 0.5F;
    this.neck.zRot = 0.25F * roll;
    float o = open * (1.0F + noise(t * 0.9F) * 0.08F);
    this.mouth.visible = open > 0.01F;
    this.jaw.visible = open > 0.01F;
    this.mouth.yScale = Math.max(0.01F, o);
    this.jaw.y = this.jaw.y + 2.0F * o;
    this.glare.visible = pt > 3.0F;
    this.body.xRot = 0.04F;
    this.rightArm.xRot = 0.103F - 0.4F * open;
    this.rightArm.zRot = -0.183F;
    this.rightArm.yRot = -0.181F;
    this.rightForearm.xRot = -1.445F + 1.2F * open;
    this.leftArm.xRot = 0.103F;
    this.leftArm.zRot = 0.183F;
    this.leftArm.yRot = 0.181F;
    this.leftForearm.xRot = -1.445F;
    this.tray.x = -2.4F;
    blend = 2.0F;
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
    float o = 0.9F + Mth.sin(t * 0.4F) * 0.06F;
    this.mouth.visible = true;
    this.jaw.visible = true;
    this.mouth.yScale = o;
    this.jaw.y = this.jaw.y + 2.0F * o;
    this.glare.visible = true;
    blend = 4.0F;
}
this.neck.zRot = this.neck.zRot + jit2 * 0.25F;
this.rightArm.zRot = this.rightArm.zRot + jit * 0.2F;
'''
    post = '''
// after the blend: the tray stays level on her palm, the glasses stand upright, loose strands hang down
level(this.tray, this.body, this.leftArm, this.leftForearm, this.leftHand);
level(this.heldGlass, this.body, this.rightArm, this.rightForearm, this.rightHand);
level(this.lockR, this.body, this.neck, this.head);
level(this.lockL, this.body, this.neck, this.head);
this.lockR.xRot = this.lockR.xRot + Mth.sin(t * 0.07F) * 0.05F;
this.lockL.xRot = this.lockL.xRot + Mth.sin(t * 0.07F + 1.1F) * 0.05F;
'''


SPEC = Waitress


import ent_scene as SC  # noqa: E402
POSE_SCENES = {1: SC.dining(table=True)}
WALKING = (0, 5)
NAKED_ALPHA = 0.3


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
                                                 target=(0, 0.9, 0), night=True, alpha=0.3, eyes_gain=0.6, scene=sc, fov=50)))
    rows.append(('in the restaurant at night: naked eye / through the finder', ims))
    return rows
