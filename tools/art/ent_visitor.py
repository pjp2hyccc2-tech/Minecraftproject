"""来店者 the visitor (三浦 健司, 24) - 0.6 x 1.9. The rider who died at the curve in 2009 on his way to meet his
girlfriend at 2 a.m.: black leather jacket scraped raw down the right side, a pearl-white full-face helmet with a
smoked visor cracked from an impact (one shard missing: one eye behind it, a faint glint), the right arm broken the
wrong way at the elbow, jeans black with water, dripping. No skin shows anywhere but that eye.
Poses: 0 WALK, 1 SIT, 2 STAND, 3 PRESS, 4 NOTICE, 5 LUNGE."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

LEATHER = ['#08080a', '#0f0f12', '#16161a', '#1e1e23', '#28282e', '#34343b', '#45454d']
SUEDE = ['#2d2420', '#40332b', '#554539', '#6b5a4b', '#80705f']        # leather scraped raw
DENIM = ['#0b0e15', '#10141e', '#161c29', '#1d2535', '#262f43', '#323d55', '#424f6a']
SHELL = ['#4f4d49', '#6c6964', '#8a867f', '#a5a096', '#bcb7ac', '#cfcabe', '#e0dcd1']
VISOR = ['#07080a', '#0d1013', '#14181d', '#1c2228', '#27303a', '#3a4754', '#566676']
SKIN = ['#3d3533', '#5a4c48', '#7a6964', '#9c8a83', '#b7a59c']
BOOT = ['#0a0908', '#131110', '#1c1918', '#272321', '#35302d']
STEEL = ['#3a3d41', '#55595e', '#737980', '#9399a0', '#b7bcc1', '#d9dde0']


def crack_web(f, impact, seed=1, rows=(0, 99), n=7, rings=(2.0, 4.0), light='#b9c4cb', dark='#6e7a83'):
    """Spider-web cracks in glass: radial lines from the impact with small kinks, plus broken rings."""
    rng = np.random.default_rng(seed)
    ix, iy = impact
    y0, y1 = rows

    def put(x, y, c):
        if 0 <= x < f.w and y0 <= y <= min(y1, f.h - 1):
            f.set(x, y, c)
    angs = np.sort(rng.uniform(0, 2 * math.pi, n))
    for a in angs:
        x, y = float(ix), float(iy)
        ln = rng.uniform(4.0, 12.0)
        steps = int(ln * 2)
        for s in range(steps):
            a += rng.normal(0, 0.12)
            x += math.cos(a) * 0.5
            y += math.sin(a) * 0.5
            put(int(round(x)), int(round(y)), C(light) if s < steps * 0.6 else C(dark))
    for rr in rings:
        for k in range(int(rr * 9)):
            a = k / (rr * 9) * 2 * math.pi
            if rng.random() < 0.35:
                continue
            put(int(round(ix + math.cos(a) * rr * 1.2)), int(round(iy + math.sin(a) * rr * 0.8)), C(dark))
    put(ix, iy, C('#e8eef1'))


class Visitor(Spec):
    id = 'visitor'
    java_name = 'VisitorModel'
    module = 'ent_visitor'
    tex_w = 64
    tex_h = 64
    height = 1.9
    poses = [('WALK', 0), ('SIT', 1), ('STAND', 2), ('PRESS', 3), ('NOTICE', 4), ('LUNGE', 5)]
    doc = """来店者 (the visitor, 三浦 健司): the rider who died at the curve in front in 2009. Every night at two the door
    opens for him; he sits at table 7, presses the call button and waits. Black leather jacket scraped raw down the
    right side, a white full-face helmet with a smoked visor cracked from the impact (one shard gone: one eye, a
    faint glint), the right arm broken the wrong way, jeans black with water, dripping.
    Pose ids: 0 WALK (slow limp on the stiff right leg, the broken arm swinging dead), 1 SIT (seated on the booth
    bench: hips 7 px above the feet, the left hand flat on the table, the broken arm lying palm-up beside it, helmet
    bowed), 2 STAND (waiting, helmet down), 3 PRESS (seated: the left hand reaches the call button on the table,
    presses at pose tick 14, back by 26), 4 NOTICE (the helmet snaps toward you with an overshoot, the visor cracks
    wider; stays seated if the previous pose was SIT/PRESS), 5 LUNGE (lurching run, the good arm grabbing, the
    broken one flailing). The visor fogs with his breath; drops fall from both gloves and the chin bar."""

    def build(self):
        r = self.root
        body = Part('body', (0, 10, 0))
        body.cube(Cube((-3.75, -8, -2), (7.5, 8.5, 4), 'jacket', tag='torso'))
        neck = Part('neck', (0, -8, 0), cubes=[Cube((-1.25, -0.5, -1.25), (2.5, 0.5, 2.5), 'neck', tag='neck',
                                                    faces=('right', 'front', 'left', 'back'))])
        # a full-face helmet from three boxes: the shell, a crown step (rounds the top) and the chin bar
        helmet = Part('helmet', (0, -0.5, 0), cubes=[
            Cube((-3.25, -6.5, -3.5), (6.5, 6, 7), 'helmet', tag='helmet'),
            Cube((-2.75, -7, -3.0), (5.5, 0.5, 6), 'helmet', tag='crown', faces=('top', 'right', 'front', 'left', 'back')),
            Cube((-3.0, -1.5, -4.0), (6, 1.5, 3), 'helmet', tag='chin', faces=('bottom', 'right', 'front', 'left'))])
        cracks = card('cracks', (0, -5.0, -3.58), (-3.25, 0, 0), (6.5, 3, 0), 'cracks')
        fog = card('fog', (0, -2.0, -3.54), (-3.0, -2.5, 0), (6, 2.5, 0), 'fog')
        drip_h = Part('drip_h', (0.5, 0.0, -3.4))
        drip_h.add(Part('drip_h_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), 'drop', tag='drip_h')]))
        helmet.add(cracks, fog, drip_h)
        neck.add(helmet)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 5.1, -7.5, 0), cubes=[Cube((-1.25, -0.5, -1.25), (2.5, 5, 2.5), 'jacket', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 4.5, 2.5), 'jacket', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.5, 0), cubes=[Cube((-1, 0, -0.75), (2, 2.5, 1.5), 'glove', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            drip = Part(f'drip_{pre[0]}', (0, 2.5, 0))
            drip.add(Part(f'drip_{pre[0]}_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), 'drop', tag=f'drip_{pre[0]}')]))
            hand.add(drip)
            fore.add(hand)
            arm.add(fore)
            arms[pre] = arm
        body.add(neck, arms['right'], arms['left'])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.5, 10, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6.5, 2.5), 'jeans', tag=f'{pre}_thigh',
                                                                       faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 6.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6.5, 2.5), 'jeans', tag=f'{pre}_shin',
                                                               faces=('right', 'front', 'left', 'back')),
                                                          Cube((-1.25, 6.5, -3.0), (2.5, 1, 4), 'boot', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 7711)
        F, EF, nz = pt.F, pt.EF, pt.nz

        def wet(Pp, nrm, n):
            # water darkens from the hems up and gathers at the bottom; streaks run down
            streak = np.sin(Pp[..., 0] * 3.1 + Pp[..., 2] * 2.3) * 0.25
            return -0.5 * np.clip((Pp[..., 1] - 4.0) / 14.0, 0, 1) + streak * 0.4

        # ---------------------------------------------------------------- jacket (torso + sleeves)
        for tag in ('torso', 'right_upper', 'left_upper', 'right_fore', 'left_fore'):
            pt.base(tag, LEATHER, 3.0, amp=0.55, fine=0.45, top=0.9, fn=wet)
        # creases at the elbows and the shoulders, wet highlights
        for pre in ('right', 'left'):
            for tag in (f'{pre}_upper', f'{pre}_fore'):
                fs = F(tag)
                for n in ('front', 'back', 'left', 'right'):
                    f = fs[n]
                    for x in range(f.w):
                        if (x + (1 if tag.endswith('fore') else 0)) % 2 == 0:
                            f.dl(x, f.h - 2 if tag.endswith('upper') else 1, 0.06)
                    for y in range(f.h):
                        if (y * 3 + x) % 7 == 0:
                            f.dl(1, y, 0.05)
            fs = F(f'{pre}_fore')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]
                for x in range(f.w):
                    f.set(x, f.h - 1, P(LEATHER, 1))          # cuff
                    f.set(x, f.h - 2, P(LEATHER, 4))
        fs = F('torso')
        f = fs['front']                                       # 15 x 17: asymmetric zip, collar, pockets
        for y in range(f.h):
            zx = 9 if y < 6 else 9 - (y - 6) // 4             # the zip runs diagonally to the centre
            f.set(zx, y, P(STEEL, 3 if y % 2 else 4))
            f.dl(zx - 1, y, 0.06)
        for x in range(f.w):                                  # stand collar
            f.set(x, 0, P(LEATHER, 4))
            f.set(x, 1, P(LEATHER, 2))
        f.set(7, 0, P(LEATHER, 0))
        f.set(7, 1, P(LEATHER, 0))
        for (x, y) in ((2, 10), (3, 10), (4, 10), (5, 10), (10, 10), (11, 10), (12, 10)):     # pocket zips
            f.set(x, y, P(STEEL, 2))
        for x in range(f.w):                                  # waist band
            f.set(x, f.h - 2, P(LEATHER, 1))
            f.set(x, f.h - 1, P(LEATHER, 2))
        for y in range(f.h):                                  # wet shine on the chest
            if 3 <= y <= 7:
                f.dl(11, y, 0.08)
                f.dl(3, y + 1, 0.05)
        # the right side scraped raw by the asphalt: front-left of the texture, the right side, the right sleeve
        def scrape(fc, seed, amount, xs=None):
            """Abrasion streaks running along the body (he slid down the asphalt on his right side)."""
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = fc.positions()[y, x][None]
                    g = pt.n3c(pp * np.array([[1.6, 0.35, 1.6]]) + seed)[0] + pt.n3b(pp * np.array([[3.0, 0.6, 3.0]]) + seed)[0] * 0.6
                    lim = 0.2 - amount
                    if xs is not None:
                        lim += xs(x, y)
                    if g > lim:
                        k = 2.0 + (g - lim) * 1.8 + nz(fc, x, y, 0.3, 0.3)
                        fc.set(x, y, P(SUEDE, k))
                        if (x * 7 + y) % 5 == 0:
                            fc.set(x, y, P(SUEDE, k + 1.2))     # fine scratches
                    elif g > lim - 0.1:
                        fc.set(x, y, P(LEATHER, 6))           # the torn lip of the leather
        scrape(fs['right'], 11.0, 0.35)
        scrape(fs['front'], 12.0, 0.0, xs=lambda x, y: x * 0.25)
        scrape(fs['back'], 13.0, 0.0, xs=lambda x, y: (14 - x) * 0.25)
        for tag in ('right_upper', 'right_fore'):
            for n, amt in (('right', 0.45), ('back', 0.15), ('front', 0.05), ('left', -0.3)):
                scrape(F(tag)[n], 20.0 + len(tag) + len(n), amt)
        fb = fs['back']                                       # a faded logo patch across the shoulders
        for x in range(4, 11):
            fb.dl(x, 3, 0.05)
        fs['bottom'].fill(P(LEATHER, 0))
        # ---------------------------------------------------------------- gloves
        for pre in ('right', 'left'):
            fs = pt.base(f'{pre}_hand', LEATHER, 2.4, amp=0.4, top=0.7)
            for n in ('front', 'back'):
                f = fs[n]                                     # 4 x 5: knuckle guard and finger seams
                for x in range(f.w):
                    f.set(x, 1, P(LEATHER, 5) if n == 'back' else P(LEATHER, 3))
                for y in range(2, f.h):
                    f.dl(1, y, -0.06)
                    f.dl(3, y, -0.06)
            fs['bottom'].fill(P(LEATHER, 3))
        for n in ('back', 'right'):                           # the right glove torn open at the knuckles
            f = F('right_hand')[n]
            f.set(1, 2, P(SKIN, 2))
            f.set(2, 2, P(SKIN, 1))
        # ---------------------------------------------------------------- jeans (wet) and boots
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                fs = pt.base(tag, DENIM, 2.4, amp=0.5, fine=0.4, top=0.6, fn=wet)
                for n in ('front', 'back', 'left', 'right'):
                    f = fs[n]
                    for y in range(f.h):
                        f.dl(f.w - 1 if n == 'left' else 0, y, -0.05)      # seams
        f = F('right_shin')['front']                          # the right knee torn and scraped
        for (x, y, v) in ((1, 0, 3.0), (2, 0, 2.6), (3, 0, 2.0), (1, 1, 2.4), (2, 1, 2.0), (2, 2, 1.6), (3, 1, 1.4)):
            f.set(x, y, P(SUEDE, v))
        for pre in ('right', 'left'):
            fs = F(f'{pre}_shin')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]                                     # boot shafts over the lower shin
                for y in range(f.h - 5, f.h):
                    for x in range(f.w):
                        f.set(x, y, P(BOOT, 2.2 + nz(f, x, y, 0.5, 0.3)))
                for x in range(f.w):
                    f.set(x, f.h - 6, P(DENIM, 1))
            fs = F(f'{pre}_foot')
            for n, fc in fs.items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(BOOT, 2.0 + nz(fc, x, y, 0.5, 0.3) + (0.8 if n == 'top' else 0)))
            fs['bottom'].fill(P(BOOT, 0))
            for n in ('front', 'left', 'right', 'back'):
                fc = fs[n]
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(BOOT, 0))
        # ---------------------------------------------------------------- neck: black balaclava / collar
        pt.base('neck', LEATHER, 1.2, amp=0.3, top=0.2)

        # ---------------------------------------------------------------- helmet: dirty pearl shell, big smoked visor
        def shell_fn(Pp, nrm, n):
            return -0.35 * np.clip((Pp[..., 1] + 1.0) / 4.0, 0, 1)

        def edges(fc, amt=0.08, w=1):
            for y in range(fc.h):
                for x in range(fc.w):
                    d = min(x, fc.w - 1 - x)
                    if d < w:
                        fc.dl(x, y, -amt * (w - d) / w)

        for tag in ('helmet', 'crown', 'chin'):
            fs = pt.base(tag, SHELL, 4.2, amp=0.5, fine=0.35, top=0.6, fn=shell_fn)
            for n in ('front', 'back', 'left', 'right'):
                edges(fs[n], 0.07, 2)
        fs = F('helmet')
        f = fs['front']                                       # 13 x 12: brow of the shell, the visor, its seal
        Vv = {'s': (SHELL, 4.2), 'S': (SHELL, 5.2), 'e': (SHELL, 2.6), 'k': (VISOR, 0.3), 'v': (VISOR, 1.6),
              'V': (VISOR, 2.6), 'r': (VISOR, 4.2), 'R': (VISOR, 5.4), 'n': C('#1a1a1c'), 'g': (SHELL, 3.0),
              'w': (SKIN, 3.2), 'W': (SKIN, 2.0), 'E': C('#d4cbbf'), 'I': C('#3a281f'), 'X': C('#030202'),
              'D': C('#8e4a45'), 'C': C('#a7b2ba'), 'c': C('#6f7d88')}
        front = [
            'ssssSnSnSssss',
            'sssssssssssss',
            'eekkkkkkkkkee',
            'kvvvvVVrRvvvk',
            'kvvvVVrRvvvvk',
            'kvvVVrRvvvvvk',
            'kvVVrRvvvvvvk',
            'kvvrRvvvvvvvk',
            'kvvvvvvvvvvvk',
            'kkvvvvvvvvvkk',
            'eekkkkkkkkkee',
            'ggsgsssssgsgg',
        ]
        pt.pattern(f, front, Vv)
        crack_web(f, (9, 4), seed=5, rows=(3, 8), n=7, rings=(2.2, 4.0))
        # the missing shard over his right eye: pale skin, the eye looking out
        for (x, y) in ((2, 4), (3, 4), (4, 4), (2, 5), (3, 5), (4, 5), (5, 5), (2, 6), (3, 6), (4, 6)):
            f.set(x, y, P(SKIN, 2.6))
        f.set(2, 4, P(SKIN, 1.6))
        f.set(3, 4, P(SKIN, 1.4))
        f.set(4, 4, P(SKIN, 1.8))
        f.set(2, 5, C('#bfb5a8'))
        f.set(3, 5, C('#2a1c15'))
        f.set(4, 5, C('#cfc5b8'))
        f.set(5, 5, P(SKIN, 2.2))
        f.set(3, 6, P(SKIN, 3.2))
        for (x, y) in ((1, 4), (1, 5), (1, 6), (5, 4), (5, 6), (2, 7), (3, 7), (4, 7)):
            f.set(x, y, C('#c4ced5'))                         # the broken edge of the shard
        for n in ('right', 'left'):                          # the visor wraps round the sides; the pivot plate
            sdf = fs[n]                                      # 14 x 12, xf = 0 at the front
            for y in range(2, 10):
                for x in range(sdf.w):
                    xf = (sdf.w - 1 - x) if n == 'right' else x
                    if xf <= 2:
                        sdf.set(x, y, P(VISOR, 1.6 + (1.5 if (y + xf) % 5 == 0 else 0)) if 2 < y < 9 else P(VISOR, 0.3))
            for (px, py) in ((3, 4), (4, 4), (3, 5), (4, 5), (3, 6), (4, 6)):
                x = (sdf.w - 1 - px) if n == 'right' else px
                sdf.set(x, py, P(SHELL, 2.0) if (px, py) != (3, 5) else P(STEEL, 3))
        sd = fs['right']                                     # the slide: the right side ground grey, black streaks
        for tagn in (('helmet', 'right'), ('crown', 'right'), ('chin', 'right'), ('helmet', 'top')):
            fc = F(tagn[0])[tagn[1]]
            for y in range(fc.h):
                for x in range(fc.w):
                    pp = fc.positions()[y, x][None]
                    g = pt.n3c(pp * np.array([[0.6, 2.4, 0.6]]) + 3.0)[0] + 0.4 * pt.n3b(pp * 2.5)[0]
                    if tagn[1] == 'top':
                        g -= 0.5 + (pp[0, 0] + 3.0) * 0.25
                    if g > 0.15:
                        fc.set(x, y, P(SHELL, 2.4 + g * 0.8 + pt.nz(fc, x, y, 0.4, 0.3)))
                    if g > 0.55 and (x + 2 * y) % 3 == 0:
                        fc.set(x, y, C('#2c2a28'))
        fb = fs['back']
        for y in range(fb.h):
            fb.set(fb.w // 2, y, P(SHELL, 1.9))
        for x in range(fb.w):
            fb.set(x, fb.h - 1, C('#0c0c0d'))
        ftop = F('crown')['top']
        for y in range(ftop.h):
            ftop.set(ftop.w // 2, y, P(SHELL, 1.9))
        fs['bottom'].fill(C('#0a0a0b'))
        fch = F('chin')
        f = fch['front']                                     # 12 x 3: vents in the chin bar
        for (x, y) in ((4, 1), (7, 1)):
            f.set(x, y, P(SHELL, 1.2))
        for x in range(f.w):
            f.set(x, f.h - 1, C('#121213'))
        fch['bottom'].fill(C('#0c0c0d'))
        # eyes layer: the glint on the eye behind the missing shard, cold light caught along two cracks
        ef = EF('helmet')['front']
        ef.set(3, 5, '#c9c1b5')
        ef.set(2, 5, '#2a2624')
        ef.set(9, 4, '#20282d')
        ef.set(11, 3, '#161c20')

        # ---------------------------------------------------------------- NOTICE: the visor splits wider
        f = F('cracks')['front']                              # 13 x 6 over visor rows 3..8
        ef = EF('cracks')['front']
        crack_web(f, (9, 1), seed=9, rows=(0, 5), n=10, rings=(1.6, 3.2, 5.0), light='#dfe6ea')
        hole = [(0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (5, 0), (0, 1), (1, 1), (2, 1), (3, 1), (4, 1), (5, 1),
                (6, 1), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (5, 2), (6, 2), (1, 3), (2, 3), (3, 3), (4, 3),
                (5, 3), (2, 4), (3, 4)]
        for (x, y) in hole:
            f.set(x, y, P(SKIN, 2.8 - 0.4 * ((x + y) % 2)))
        for (x, y) in ((1, 0), (2, 0), (3, 0), (4, 0), (5, 0)):
            f.set(x, y, P(SKIN, 1.5))                          # the brow pulled up
        eye = {(1, 1): C('#a46a60'), (2, 1): C('#d8cfc2'), (3, 1): C('#e0d8cc'), (4, 1): C('#2b1d16'),
               (5, 1): C('#d8cfc2'), (6, 1): C('#9b6a60'), (2, 2): C('#cfc5b8'), (3, 2): C('#c4b6a8'),
               (4, 2): C('#3b2a22'), (5, 2): C('#b9aa9c'), (3, 3): P(SKIN, 1.6), (4, 3): P(SKIN, 1.6)}
        for (x, y), c in eye.items():
            f.set(x, y, c)
        for (x, y) in ((7, 0), (7, 1), (7, 2), (6, 3), (5, 4), (4, 4), (1, 4), (0, 3)):
            f.set(x, y, C('#d6dde1'))                          # shard edges
        ef.set(4, 1, '#ffffff')
        ef.set(4, 2, '#3c2c28')
        ef.set(9, 1, '#2a3236')
        ef.set(11, 3, '#1c2226')
        # ---------------------------------------------------------------- breath fog on the inside of the visor
        f = F('fog')['front']                                 # 12 x 5, stretched up from the chin
        for y in range(f.h):
            for x in range(f.w):
                d = abs((x + 0.5) / f.w - 0.5) * 2
                a = max(0.0, 1.0 - d * 1.1) * (0.4 + 0.6 * (y / (f.h - 1)))
                c = C('#c7d0d6')
                c[3] = int(40 + 110 * a)
                if a > 0.05:
                    f.set(x, y, c)
        # ---------------------------------------------------------------- drops
        for tag in ('drip_h', 'drip_l', 'drip_r'):
            for n, fc in F(tag).items():
                c = C('#b9d2dc')
                c[3] = 220
                fc.fill(c)
            for n, fc in EF(tag).items():
                fc.fill(C('#1e2a30'))
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.09F);
float jit = glitch * noise(t * 1.9F);
float jit2 = glitch * noise(t * 2.7F + 50.0F);
this.cracks.visible = false;
boolean seated = pose == SIT || pose == PRESS || (pose == NOTICE && (prev == SIT || prev == PRESS));
// the broken right arm: forearm bent the wrong way at the elbow and twisted, the glove limp
this.rightForearm.xRot = 0.62F;
this.rightForearm.yRot = 1.3F;
this.rightHand.xRot = 0.45F;
this.rightHand.zRot = 0.2F;
this.leftHand.xRot = -0.1F;

if (seated) {
    // on the booth bench: hips 7 px above the feet, thighs forward, feet on the floor
    this.body.y = 15.75F;
    this.rightLeg.y = 15.75F;
    this.leftLeg.y = 15.75F;
    this.rightLeg.xRot = -1.45F;
    this.leftLeg.xRot = -1.45F;
    this.rightLeg.yRot = 0.08F;
    this.leftLeg.yRot = -0.08F;
    this.rightShin.xRot = 1.42F;
    this.leftShin.xRot = 1.42F;
    this.body.xRot = 0.28F + breath * 0.012F;
    // the left hand flat on the table; the broken arm lying on it palm-up, twisted
    this.leftArm.xRot = -0.543F;
    this.leftArm.zRot = 0.718F;
    this.leftArm.yRot = 0.195F;
    this.leftForearm.xRot = -1.371F;
    this.leftHand.xRot = 0.13F;
    this.rightArm.xRot = -1.905F;
    this.rightArm.zRot = -0.983F;
    this.rightArm.yRot = -0.189F;
    this.rightForearm.xRot = -1.537F;
    this.rightForearm.yRot = 3.0F;
    this.rightHand.xRot = 1.451F;
    this.rightHand.zRot = 0.0F;
    this.neck.xRot = 0.2F;
    this.helmet.xRot = 0.26F + breath * 0.02F;
    this.helmet.zRot = 0.06F;
    if (pose == PRESS) {
        // lift the hand, reach the call button, press (tick 14), hold, back flat by 26
        float up = span(pt, 0.0F, 7.0F) * (1.0F - span(pt, 18.0F, 26.0F));
        float press = span(pt, 9.0F, 13.0F) * (1.0F - span(pt, 15.0F, 18.0F));
        this.leftArm.xRot = Mth.lerp(up, -0.543F, -1.11F) + press * 0.06F;
        this.leftArm.zRot = Mth.lerp(up, 0.718F, -0.665F);
        this.leftArm.yRot = Mth.lerp(up, 0.195F, 0.145F);
        this.leftForearm.xRot = Mth.lerp(up, -1.371F, -1.644F) + press * 0.12F;
        this.leftHand.xRot = Mth.lerp(up, 0.13F, 1.519F) + press * 0.25F;
        this.helmet.yRot = 0.25F * up;
    }
    if (pose == NOTICE) {
        // the helmet snaps round at you with an overshoot; the hands slam flat
        float sn = spring(pt, 0.9F, 0.35F);
        this.helmet.yRot = Mth.clamp(yaw, -1.9F, 1.9F) * sn;
        this.helmet.xRot = Mth.lerp(Math.min(1.0F, sn), 0.26F, Mth.clamp(pitch, -0.6F, 0.6F) - 0.05F);
        this.neck.xRot = 0.2F * (1.0F - Math.min(1.0F, sn));
        this.body.xRot = 0.28F - 0.2F * Math.min(1.0F, sn);
        this.cracks.visible = pt > 2.0F;
        blend = 1.5F;
    }
} else if (pose == WALK) {
    // slow limp: the right leg stays stiff and swings short; the body drops onto it and lurches
    float a = Math.min(1.0F, limbSwingAmount * 1.6F);
    float c = limbSwing * 0.62F;
    float s = Mth.sin(c);
    float onBad = Math.max(0.0F, -Mth.cos(c));
    this.rightLeg.xRot = s * 0.26F * a;
    this.rightShin.xRot = 0.05F;
    this.leftLeg.xRot = -s * 0.5F * a;
    this.leftShin.xRot = Math.max(0.0F, Mth.sin(c + 0.7F)) * 0.8F * a;
    this.body.y = 10.0F + onBad * 0.7F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.zRot = (onBad - 0.4F) * 0.09F * a;
    this.body.xRot = 0.16F + onBad * 0.05F * a;
    this.body.yRot = s * 0.06F * a;
    // the dead arm swings late like a pendulum and flops at the broken elbow
    this.rightArm.xRot = Mth.sin(c - 1.3F) * 0.32F * a + 0.05F;
    this.rightArm.zRot = 0.06F + onBad * 0.08F;
    this.rightForearm.xRot = 0.62F + Mth.sin(c - 2.1F) * 0.28F * a;
    this.rightHand.xRot = 0.45F + Mth.sin(c - 2.6F) * 0.3F * a;
    this.leftArm.xRot = -s * 0.22F * a;
    this.leftArm.zRot = -0.06F;
    this.leftForearm.xRot = -0.25F;
    this.neck.xRot = 0.15F;
    this.helmet.xRot = 0.22F + Mth.sin(c * 2.0F - 0.6F) * 0.04F * a;
    this.helmet.zRot = -(onBad - 0.4F) * 0.07F * a;
    this.helmet.yRot = Mth.clamp(yaw, -0.6F, 0.6F) * 0.3F;
} else if (pose == STAND) {
    // waiting by the door: helmet down, swaying slightly, perfectly patient
    this.body.xRot = 0.12F + breath * 0.01F;
    this.body.zRot = Mth.sin(t * 0.023F) * 0.025F;
    this.neck.xRot = 0.15F;
    this.helmet.xRot = 0.24F + breath * 0.02F;
    this.helmet.zRot = 0.08F + Mth.sin(t * 0.017F) * 0.03F;
    this.rightArm.xRot = 0.05F + Mth.sin(t * 0.023F - 1.0F) * 0.03F;
    this.rightArm.zRot = 0.07F;
    this.leftArm.xRot = -0.05F;
    this.leftArm.zRot = -0.07F;
    this.leftForearm.xRot = -0.2F;
} else if (pose == NOTICE) {
    // standing: the helmet snaps toward you with an overshoot and the visor cracks wider
    float sn = spring(pt, 0.9F, 0.35F);
    this.helmet.yRot = Mth.clamp(yaw, -1.9F, 1.9F) * sn;
    this.helmet.xRot = Mth.lerp(Math.min(1.0F, sn), 0.24F, Mth.clamp(pitch, -0.6F, 0.6F));
    this.neck.xRot = 0.15F * (1.0F - Math.min(1.0F, sn));
    this.body.xRot = 0.05F;
    this.rightArm.zRot = 0.1F;
    this.leftArm.xRot = -0.35F * Math.min(1.0F, sn);
    this.leftArm.zRot = -0.25F;
    this.leftForearm.xRot = -0.6F;
    this.leftHand.xRot = -0.6F;
    this.cracks.visible = pt > 2.0F;
    blend = 1.5F;
} else {
    // LUNGE: a lurching run straight at you; the good arm grabs, the broken one flails behind
    float a = Math.min(1.0F, limbSwingAmount * 1.2F);
    float c = limbSwing * 0.9F;
    float s = Mth.sin(c);
    this.body.xRot = 0.48F + Mth.sin(c * 2.0F) * 0.04F;
    this.body.y = 10.6F + Math.abs(Mth.cos(c)) * 0.6F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.rightLeg.xRot = s * 0.55F * a - 0.25F;
    this.rightShin.xRot = 0.15F;
    this.leftLeg.xRot = -s * 0.85F * a - 0.25F;
    this.leftShin.xRot = Math.max(0.0F, Mth.sin(c + 0.8F)) * 1.1F * a + 0.1F;
    this.leftArm.xRot = -1.75F + s * 0.12F * a + jit * 0.3F;
    this.leftArm.zRot = -0.15F;
    this.leftForearm.xRot = -0.25F;
    this.leftHand.xRot = -0.5F;
    this.rightArm.xRot = 0.6F + Mth.sin(c * 1.5F + 1.0F) * 0.7F * a;
    this.rightArm.zRot = 0.3F + Mth.sin(c * 1.1F) * 0.2F * a;
    this.rightForearm.xRot = 0.62F + Mth.sin(c * 1.5F) * 0.6F * a;
    this.neck.xRot = -0.25F;
    this.helmet.xRot = -0.25F + Mth.clamp(pitch, -0.5F, 0.5F) * 0.5F;
    this.helmet.yRot = Mth.clamp(yaw, -0.8F, 0.8F) * 0.6F;
    this.cracks.visible = true;
    blend = 4.0F;
}
this.helmet.zRot = this.helmet.zRot + jit * 0.35F;
this.helmet.xRot = this.helmet.xRot + jit2 * 0.2F;
this.rightForearm.xRot = this.rightForearm.xRot + jit2 * 0.6F;
'''
    post = '''
// drops: the falling parts are levelled so they fall straight down from the gloves and the chin bar
level(this.dripL, this.body, this.leftArm, this.leftForearm, this.leftHand);
level(this.dripR, this.body, this.rightArm, this.rightForearm, this.rightHand);
level(this.dripH, this.body, this.neck, this.helmet);
float dl = (t + 7.0F) % 23.0F;
float dr = (t + 15.0F) % 31.0F;
float dh = (t + 3.0F) % 41.0F;
this.dripLFall.visible = dl < 9.0F;
this.dripRFall.visible = dr < 9.0F;
this.dripHFall.visible = dh < 9.0F;
this.dripLFall.y = dl * dl * 0.16F;
this.dripRFall.y = dr * dr * 0.16F;
this.dripHFall.y = dh * dh * 0.16F;
// the visor fogs with each breath, from the chin up, and clears
float fb = (t + 20.0F) % 70.0F;
float fogk = fb < 30.0F ? span(fb, 0.0F, 8.0F) * (1.0F - span(fb, 14.0F, 30.0F)) : 0.0F;
this.fog.visible = fogk > 0.02F && !this.cracks.visible;
this.fog.yScale = Math.max(0.02F, fogk);
'''


SPEC = Visitor


import ent_scene as SC  # noqa: E402
POSE_SCENES = {1: SC.booth_table(), 3: SC.booth_table()}
WALKING = (0, 5)
NAKED_ALPHA = 0.0


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.dining()
    walk = lambda tt: (tt * 0.42, 0.8)                          # noqa: E731
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(2, 60, yaw_view=yv, scene=sc)))
    ims.append(('close', b.view(2, 60, yaw_view=-15, pitch_view=0, size=(190, 190), ortho=0.38, center_y=1.55, scene=sc)))
    ims.append(('fogged visor', b.view(2, 50, yaw_view=-15, pitch_view=0, size=(190, 190), ortho=0.38, center_y=1.55,
                                       scene=sc)))
    rows.append(('STAND (2): waiting, helmet down; the visor fogs with each breath', ims))
    ims = []
    for tt in (0, 5, 10, 15, 20):
        ims.append((f'side t{tt}', b.view([(0, 0)], 100 + tt, yaw_view=90, limb=walk, scene=sc)))
    ims.append(('front', b.view([(0, 0)], 105, yaw_view=0, limb=walk, scene=sc)))
    ims.append(('3/4', b.view([(0, 0)], 112, yaw_view=-40, limb=walk, scene=sc)))
    rows.append(('WALK (0): limping on the stiff right leg, the broken arm swinging dead', ims))
    bt = SC.booth_table()
    ims = []
    for (yv, lab) in ((90, 'side (window)'), (40, '3/4'), (0, 'front'), (-60, 'other side')):
        ims.append((lab, b.view(1, 60, yaw_view=yv, pitch_view=10, scene=bt)))
    ims.append(('profile close', b.view(1, 60, yaw_view=90, pitch_view=2, size=(190, 190), ortho=0.5, center_y=1.2, scene=bt)))
    rows.append(('SIT (1): at table 7, hand flat, the broken arm palm-up, helmet bowed', ims))
    ims = []
    for ptv in (0, 5, 10, 14, 20, 26):
        ims.append((f'pt {ptv}', b.view([(0, 1), (100, 3)], 100 + ptv, yaw_view=60, pitch_view=12, scene=bt)))
    rows.append(('PRESS (3): seated, reaches the call button (pressed at pt 14)', ims))
    ims = []
    head = lambda tt: (-70.0, 5.0)                              # noqa: E731
    for ptv in (0, 2, 4, 7, 12):
        ims.append((f'seated pt {ptv}', b.view([(0, 1), (100, 4)], 100 + ptv, yaw_view=-70, pitch_view=8, head=head, scene=bt)))
    ims.append(('standing pt 10', b.view([(0, 2), (100, 4)], 110, yaw_view=-30, head=lambda tt: (-30.0, 0.0), scene=sc)))
    ims.append(('close', b.view([(0, 2), (100, 4)], 110, yaw_view=-30, pitch_view=0, head=lambda tt: (-30.0, 0.0),
                                size=(190, 190), ortho=0.38, center_y=1.55, scene=sc)))
    rows.append(('NOTICE (4): the helmet snaps round with an overshoot, the visor splits wider', ims))
    run = lambda tt: (tt * 0.8, 1.0)                            # noqa: E731
    ims = []
    for tt in (0, 3, 6, 9):
        ims.append((f'side t{tt}', b.view([(0, 5)], 200 + tt, yaw_view=90, limb=run, scene=sc)))
    ims.append(('front', b.view([(0, 5)], 204, yaw_view=0, limb=run, scene=sc)))
    ims.append(('3/4 glitch', b.view([(0, 5)], 207, yaw_view=-35, limb=run, glitch=lambda tt: 0.8, scene=sc)))
    ims.append(('SIT>LUNGE 2', b.view([(0, 1), (100, 5)], 102, yaw_view=70, limb=run, scene=bt)))
    rows.append(('LUNGE (5): lurching run, the good arm grabbing, the broken one flailing', ims))
    ims = []
    ims.append(('finder: profile through the window', b.view(1, 60, size=(300, 240), eye=(-3.5, 1.3, 0.4),
                                                            target=(0, 1.0, 0.2), finder=True, scene=bt, fov=45)))
    ims.append(('finder: walking in', b.view([(0, 0)], 112, size=(300, 240), eye=(0.5, 1.62, 5.0), target=(0, 1.1, 0),
                                             finder=True, limb=walk, scene=sc, fov=50)))
    ims.append(('finder: NOTICE', b.view([(0, 2), (100, 4)], 110, size=(300, 240), eye=(0.3, 1.62, 2.6),
                                         target=(0, 1.45, 0), finder=True, scene=sc, fov=50)))
    rows.append(('through the camera finder (he is invisible to the naked eye)', ims))
    return rows
