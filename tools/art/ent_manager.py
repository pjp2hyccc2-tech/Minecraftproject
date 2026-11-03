"""店長 the manager (森田 浩一, 47) - 0.6 x 1.85. Ran the 24-hour store alone at night for years and died of overwork at
his desk in 2019. He still watches the security monitors, and he still closes the store. A white shirt with sweat
stains, the tie loosened, the name badge 「店長　森田」, keys on the belt; a grey face with deep black rings under the
eyes, glasses with one lens cracked, thinning hair, slouched.
Poses: 0 SIT, 1 STAND, 2 WALK, 3 BOW, 4 LOOK, 5 CHASE, 6 REACH."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

SKIN = ['#3e4144', '#55595b', '#6d7171', '#858886', '#9b9d99', '#afb0ab', '#c0c0ba']
HAIR = ['#141313', '#1f1d1c', '#2b2927', '#3a3734', '#4d4a46', '#625f5a']
SHIRT = ['#6e6c66', '#87857e', '#a09e96', '#b7b5ad', '#cbc9c0', '#dbd9d0', '#e7e5dc']
SWEAT = ['#857a5a', '#9b8f6a', '#b0a47c']
TIE = ['#0d1220', '#141a2c', '#1c2439', '#262f48']
STRIPE = ['#4a1c22', '#5e252b']
SLACKS = ['#141518', '#1c1d21', '#25272c', '#2f3238', '#3b3e45']
SHOE = ['#08080a', '#111114', '#1b1b1f', '#27272c', '#383840']
STEEL = ['#3a3d41', '#55595e', '#737980', '#9399a0', '#b7bcc1', '#d9dde0']
BRASS = ['#4a3a1a', '#6e5626', '#937236', '#b8914a']


class Manager(Spec):
    id = 'manager'
    java_name = 'ManagerModel'
    module = 'ent_manager'
    tex_w = 64
    tex_h = 64
    height = 1.85
    poses = [('SIT', 0), ('STAND', 1), ('WALK', 2), ('BOW', 3), ('LOOK', 4), ('CHASE', 5), ('REACH', 6)]
    doc = """店長 (the manager, 森田 浩一): died of overwork at his desk in 2019; he still watches the security monitors and
    still closes the store. A white shirt with sweat stains, the tie loosened (it hangs with gravity), the name
    badge, keys on the belt; a grey face with black rings under the eyes, glasses with one lens cracked (their glare
    is a separate eyes-layer card: on while he watches the monitors and while he hunts, flashing as he scans),
    thinning hair, slouched.
    Pose ids: 0 SIT (office chair, seat 7.5 px up; the right hand on the mouse ~0.5 blocks ahead at desk height,
    the left typing in bursts, the head slowly moving between monitors), 1 STAND, 2 WALK (stiff, head jutting
    forward, the head following a moment late), 3 BOW (deep, the head hanging, the tie dangling straight down),
    4 LOOK (scans slowly left and right, the body lagging behind the head), 5 CHASE (fast stiff power walk, arms
    pumping, never running), 6 REACH (both hands slowly out to take you)."""

    def build(self):
        r = self.root
        body = Part('body', (0, 10.5, 0))
        body.cube(Cube((-3.5, -8, -2), (7, 8, 4), 'shirt', tag='torso'))
        body.cube(Cube((-3, -4.5, -2.5), (6, 3.5, 0.5), 'shirt', tag='belly', faces=('top', 'bottom', 'right', 'front', 'left')))
        tie = Part('tie', (0, -7.2, -2.15), cubes=[Cube((-0.75, 0, 0), (1.5, 6.5, 0), 'tie', tag='tie', faces=('front',))])
        keys = Part('keys', (-3.1, -0.6, -1.2))
        keys.add(Part('keys_ring', (0, 0, 0), cubes=[Cube((-0.5, 0, -0.25), (1, 1, 0.5), 'keys', tag='ring'),
                                                    Cube((-0.5, 1, -0.25), (1, 1.5, 0.5), 'keys', tag='bunch')]))
        neck = Part('neck', (0, -8, 0), cubes=[Cube((-1.25, -1, -1.25), (2.5, 1, 2.5), 'skin', tag='neck',
                                                    faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1, 0), cubes=[Cube((-3, -7, -2.75), (6, 7, 5.5), 'head', tag='head')])
        glasses = card('glasses', (0, -3.6, -2.95), (-3.25, -0.75, 0), (6.5, 1.5, 0), 'glasses')
        glare = card('glare', (0, -3.6, -3.0), (-3.25, -0.75, 0), (6.5, 1.5, 0), 'glare')
        mouth = card('mouth', (0, -1.5, -2.83), (-1.25, 0, 0), (2.5, 1, 0), 'mouth')
        head.add(glasses, glare, mouth)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.6, -7.5, 0), cubes=[Cube((-1, -0.5, -1), (2, 5, 2), 'shirt', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.5, 0), cubes=[Cube((-1, 0, -1), (2, 4.5, 2), 'shirt', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.5, 0), cubes=[Cube((-1, 0, -0.5), (2, 2.5, 1), 'skin', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            fore.add(hand)
            arm.add(fore)
            arms[pre] = arm
        body.add(tie, keys, neck, arms['right'], arms['left'])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.5, 10.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6.5, 2.5), 'slacks', tag=f'{pre}_thigh',
                                                                         faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 6.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6, 2.5), 'slacks', tag=f'{pre}_shin',
                                                               faces=('right', 'front', 'left', 'back')),
                                                          Cube((-1.25, 6, -2.75), (2.5, 1, 4), 'shoe', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 4747)
        F, EF, nz = pt.F, pt.EF, pt.nz
        for tag in ('neck', 'right_hand', 'left_hand'):
            pt.base(tag, SKIN, 3.9, amp=0.35, fine=0.3, top=0.5)
        for pre in ('right', 'left'):
            fs = F(f'{pre}_hand')
            for n in ('front', 'back'):
                f = fs[n]
                for y in range(2, f.h):
                    f.dl(1, y, -0.06)
                    f.dl(3, y, -0.05)
                for x in range(f.w):
                    f.set(x, f.h - 1, P(SKIN, 4.6))
        fb = F('right_hand')['back']                          # his wedding ring
        fb.set(1, 2, P(BRASS, 3))

        # ---------------------------------------------------------------- shirt: sweat stains, wrinkles, tucked in
        def sweat(Pp, nrm, n):
            return np.sin(Pp[..., 1] * 2.2 + Pp[..., 0] * 0.8 + pt.n3(Pp * 0.7) * 1.5) * 0.35
        for tag in ('torso', 'belly', 'right_upper', 'left_upper', 'right_fore', 'left_fore'):
            pt.base(tag, SHIRT, 4.5, amp=0.4, fine=0.35, top=0.5, fn=sweat)
        fs = F('torso')

        def stain(fc, cx, cy, rx, ry, k=1.0):
            for y in range(fc.h):
                for x in range(fc.w):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                    if d < 1.0 + nz(fc, x, y, 0.4, 0.2):
                        fc.set(x, y, P(SWEAT, 1.2 + (1 - d) * k + nz(fc, x, y, 0.3, 0.2)))
        stain(fs['left'], 2.5, 1.5, 4.0, 4.5)                 # under the arms
        stain(fs['right'], 5.0, 1.5, 4.0, 4.5)
        stain(fs['back'], 6.5, 4.0, 3.0, 4.5, 0.4)            # down the back, uneven
        stain(fs['back'], 8.5, 8.0, 2.0, 3.0, 0.3)
        stain(fs['front'], 6.5, 0.5, 4.0, 1.6, 0.4)           # the collar
        for pre, n in (('right', 'left'), ('left', 'right')):
            stain(F(f'{pre}_upper')[n], 2.0, 0.5, 3.0, 3.5, 0.8)
        f = fs['front']                                       # 14 x 16
        for (x, y) in ((5, 0), (6, 0), (7, 0), (8, 0), (6, 1), (7, 1), (6, 2), (7, 2)):
            f.set(x, y, P(SKIN, 3.0 if y > 0 else 2.2))       # the open collar
        for (x, y) in ((4, 0), (9, 0), (5, 1), (8, 1)):
            f.set(x, y, P(SHIRT, 5.6))                        # collar points
        for y in range(3, 14):                                 # button placket
            f.dl(7, y, -0.05)
            if y % 3 == 0:
                f.set(7, y, P(SHIRT, 2.2))
        # the name badge on his left chest, a little crooked
        badge = ['ooooo', 'wwwww', 'wkwkw', 'wwwww']
        bl = {'o': C('#cf6418'), 'w': C('#ecebe5'), 'k': C('#2a2522')}
        pt.pattern(f, badge, bl, ox=8, oy=4, jitter=False)
        f.set(13, 5, P(SHIRT, 3))
        f.set(9, 6, C('#2a2522'))
        f.set(10, 6, C('#ecebe5'))
        f.set(11, 6, C('#2a2522'))
        for n in ('front', 'back', 'left', 'right'):          # the belt
            fc = fs[n]
            for x in range(fc.w):
                fc.set(x, fc.h - 2, P(SHOE, 1.5))
                fc.set(x, fc.h - 1, P(SHOE, 0.8))
        f.set(6, f.h - 2, P(STEEL, 4))
        f.set(7, f.h - 2, P(STEEL, 3))
        for pre in ('right', 'left'):                         # cuffs, sleeves pushed up a little
            for n, fc in F(f'{pre}_fore').items():
                if n in ('front', 'back', 'left', 'right'):
                    for x in range(fc.w):
                        fc.set(x, fc.h - 1, P(SHIRT, 3.2))
                        fc.dl(x, 0, -0.05)
                        fc.dl(x, 3, -0.04)
        # ---------------------------------------------------------------- tie: loosened, dull stripes
        f = F('tie')['front']                                 # 3 x 13
        for y in range(f.h):
            for x in range(f.w):
                v = 1.8 + nz(f, x, y, 0.4, 0.2)
                f.set(x, y, P(TIE, v))
                if (x + y) % 4 == 0:
                    f.set(x, y, P(STRIPE, 1))
        for y in range(0, 2):                                 # the knot
            for x in range(f.w):
                f.set(x, y, P(TIE, 3))
        f.set(1, 1, P(TIE, 1))
        f.clear(0, f.h - 1)
        f.clear(2, f.h - 1)                                   # pointed end
        # ---------------------------------------------------------------- slacks, shoes
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                fs = pt.base(tag, SLACKS, 2.2, amp=0.35, fine=0.3, top=0.4)
                fc = fs['front']
                for y in range(fc.h):
                    fc.dl(fc.w // 2, y, 0.05)                 # the crease
            fk = F(f'{pre}_shin')['front']
            for x in range(fk.w):
                fk.dl(x, 0, 0.06)                             # shiny worn knees
            for n, fc in F(f'{pre}_foot').items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(SHOE, 1.6 + nz(fc, x, y, 0.5, 0.3) + (0.9 if n == 'top' else 0)))
                if n == 'top':
                    fc.set(fc.w // 2, fc.h - 2, P(SHOE, 4))   # a dull shine on the toe
        # ---------------------------------------------------------------- keys
        for tag in ('ring', 'bunch'):
            for n, fc in F(tag).items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(STEEL if (x + y) % 3 else BRASS, 2.5 + nz(fc, x, y, 0.8, 0.3)))
            fr = F(tag)['front']
            if tag == 'ring':
                fr.set(0, 0, P(STEEL, 4))
                fr.clear(1, 1)
            else:
                fr.set(1, 2, P(BRASS, 3))
        # ---------------------------------------------------------------- head: thinning hair, the tired face
        def hairfn(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 4.0 + Pp[..., 2] * 0.5) * 0.4
        fs = pt.base('head', HAIR, 2.3, amp=0.4, fine=0.3, top=0.3, fn=hairfn)
        top = fs['top']                                       # 12 x 11: scalp under a few combed-over strands
        rng = np.random.default_rng(9)
        for y in range(top.h):
            for x in range(top.w):
                edge = x <= 1 or x >= top.w - 2 or y <= 1
                if edge:
                    continue
                top.set(x, y, P(SKIN, 3.3 + nz(top, x, y, 0.3, 0.2)))
        for y in range(2, top.h - 1, 2):                      # strands combed across from his left side
            x0 = int(rng.integers(0, 3))
            for x in range(top.w - 1 - x0, int(rng.integers(2, 6)), -1):
                top.set(x, y, P(HAIR, 2.5 + nz(top, x, y, 0.4, 0.2)))
        f = fs['front']                                       # 12 x 14
        face = [
            'h33hhhhhh33h',
            'h3333hh3333h',
            'h3344444433h',
            '33444444443h',
            '3#bb4444bb#3',
            '3wEIw33wIEw3',
            '3#RR#33#RR#3',
            '3KKKK44KKKK3',
            '33333443333 3',
            '3s3344443s33',
            'ss3#mmmm#3ss',
            'sss344443sss',
            'hsss3333ssss',
            'hhssssssssh h',
        ]
        face[8] = '333334433333'
        face[13] = 'hhssssssssshh'[:12]
        leg = {'h': None, '3': (SKIN, 3.0), '4': (SKIN, 3.8), '#': (SKIN, 1.6), 'b': (HAIR, 3), 'w': C('#a8a596'),
               'E': C('#8a5a52'), 'I': C('#1a1515'), 'R': C('#0e0b10'), 'K': C('#2a2430'), 'm': C('#3a2b2b'),
               's': (SKIN, 2.2)}
        pt.pattern(f, face, leg)
        ef = EF('head')['front']
        for (x, y) in ((2, 5), (9, 5)):
            ef.set(x, y, '#2e2a26')                           # a wet glint in the bloodshot whites
        for n in ('right', 'left'):                           # ears, grey temples, the glasses' arms
            fc = fs[n]                                        # 11 x 14
            for (ex, ey, v) in ((5, 6, 2.6), (6, 6, 2.2), (5, 7, 2.8), (6, 7, 1.8), (5, 8, 2.4)):
                x = (fc.w - 1 - ex) if n == 'right' else ex
                fc.set(x, ey, P(SKIN, v))
            for xf in range(0, 6):
                x = (fc.w - 1 - xf) if n == 'right' else xf
                fc.set(x, 5, C('#141414'))
            for y in range(8, 14):
                for xf in range(0, 3):
                    x = (fc.w - 1 - xf) if n == 'right' else xf
                    fc.set(x, y, P(SKIN, 2.6 + nz(fc, x, y, 0.3, 0.2)))
        fs['bottom'].fill(P(SKIN, 2))
        # ---------------------------------------------------------------- glasses: thin frame, one lens cracked
        g = F('glasses')['front']                             # 13 x 3
        for y in range(g.h):
            for x in range(g.w):
                frame = x in (0, 5, 7, 12) or y in (0, 2)
                bridge = x == 6 and y == 0
                if 5 < x < 7 and not bridge:
                    g.clear(x, y)
                    continue
                if frame or bridge:
                    g.set(x, y, C('#121212'))
                else:
                    c = C('#9fb1bb')
                    c[3] = 90
                    g.set(x, y, c)
        for (x, y) in ((8, 1), (9, 1), (10, 1), (9, 0), (11, 1)):
            c = C('#e2e8ea')
            c[3] = 230
            g.set(x, y, c)                                    # the cracked lens (his left)
        gl = EF('glare')['front']
        for (x, y) in ((1, 1), (2, 1), (3, 1), (4, 1), (2, 0), (8, 1), (9, 1), (10, 1), (11, 1), (10, 0)):
            gl.set(x, y, '#cfe4ff')
        for (x, y) in ((1, 2), (4, 0), (8, 2), (11, 0)):
            gl.set(x, y, '#5f6f86')
        m = F('mouth')['front']                               # 5 x 2
        for x in range(m.w):
            m.set(x, 0, C('#2c1f1f'))
            if 0 < x < m.w - 1:
                m.set(x, 1, C('#0b0707'))
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.045F);
float jit = glitch * noise(t * 1.8F);
float jit2 = glitch * noise(t * 2.4F + 90.0F);
// his head follows you a moment late
float hy = follow(mem, 0, Mth.clamp(yaw, -1.2F, 1.2F), 0.07F, t);
float hp = Mth.clamp(pitch, -0.5F, 0.5F);
this.glare.visible = false;
this.mouth.visible = false;
float keySwing = 0.0F;
if (pose == SIT) {
    // at the desk: seat 7.5 px up, leaning to the monitors; the mouse hand twitches, the left types in bursts
    this.body.y = 15.4F;
    this.rightLeg.y = 15.4F;
    this.leftLeg.y = 15.4F;
    this.rightLeg.xRot = -1.42F;
    this.leftLeg.xRot = -1.42F;
    this.rightLeg.yRot = 0.1F;
    this.leftLeg.yRot = -0.12F;
    this.rightShin.xRot = 1.35F;
    this.leftShin.xRot = 1.5F;
    this.body.xRot = 0.32F + breath * 0.01F;
    float mx = noise(t * 0.4F) * 0.05F + twitch(t, 37.0F, 0.0F) * 0.04F;
    float typing = Math.max(0.0F, noise(t * 0.07F)) * Mth.sin(t * 2.3F);
    this.rightArm.xRot = $SI_RX$ + mx;
    this.rightArm.zRot = $SI_RZ$;
    this.rightArm.yRot = $SI_RY$ + mx;
    this.rightForearm.xRot = $SI_RF$;
    this.rightHand.xRot = $SI_RH$;
    this.leftArm.xRot = $SI_LX$;
    this.leftArm.zRot = $SI_LZ$;
    this.leftArm.yRot = $SI_LY$;
    this.leftForearm.xRot = $SI_LF$ + typing * 0.06F;
    this.leftHand.xRot = $SI_LH$ + typing * 0.15F;
    this.neck.xRot = 0.1F;
    this.head.xRot = -0.2F + Mth.sin(t * 0.013F) * 0.04F;
    this.head.yRot = Mth.sin(t * 0.011F) * 0.35F + noise(t * 0.03F) * 0.12F;
    this.glare.visible = true;
} else if (pose == WALK || pose == STAND || pose == LOOK) {
    // slouched, the head jutting forward; stiff steps when walking
    float a = pose == WALK ? Math.min(1.0F, limbSwingAmount * 1.4F) : 0.0F;
    float c = limbSwing * 0.7F;
    float s = Mth.sin(c);
    this.rightLeg.xRot = s * 0.42F * a;
    this.leftLeg.xRot = -s * 0.42F * a;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.2F)) * 0.25F * a;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.2F)) * 0.25F * a;
    this.body.y = 10.5F + Math.abs(Mth.cos(c)) * 0.3F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.xRot = 0.16F + breath * 0.008F;
    this.rightArm.xRot = -s * 0.14F * a + 0.05F;
    this.leftArm.xRot = s * 0.14F * a + 0.05F;
    this.rightArm.zRot = 0.07F;
    this.leftArm.zRot = -0.07F;
    this.rightForearm.xRot = -0.15F;
    this.leftForearm.xRot = -0.15F;
    this.neck.xRot = 0.35F;
    this.head.xRot = -0.25F + hp * 0.5F;
    this.head.yRot = hy;
    keySwing = s * 0.35F * a;
    if (pose == LOOK) {
        // scanning: the head sweeps slowly left, holds, sweeps right; the body lags behind; the glasses flash
        float sweep = Mth.sin(pt * 0.045F);
        float sc = Mth.clamp(sweep * 1.6F, -1.0F, 1.0F);
        this.head.yRot = sc * 1.05F;
        this.neck.yRot = sc * 0.2F;
        this.body.yRot = Mth.sin(pt * 0.045F - 0.9F) * 0.22F;
        this.head.xRot = -0.2F + Mth.sin(pt * 0.09F) * 0.05F;
        this.glare.visible = Math.abs(sc) < 0.25F;
    }
    if (pose == STAND) {
        this.head.zRot = Mth.sin(t * 0.017F) * 0.05F;
    }
} else if (pose == BOW) {
    // a deep bow held too long, the head hanging loose, hands flat on his thighs
    float down = easeOut(pt / 16.0F);
    this.body.xRot = 1.3F * down + breath * 0.01F;
    this.neck.xRot = 0.35F * down;
    this.head.xRot = 0.45F * down;
    this.head.zRot = 0.05F * down;
    this.rightArm.xRot = -1.05F * down;
    this.leftArm.xRot = -1.05F * down;
    this.rightArm.zRot = 0.04F;
    this.leftArm.zRot = -0.04F;
    this.rightForearm.xRot = -0.1F;
    this.leftForearm.xRot = -0.1F;
} else if (pose == CHASE) {
    // a fast, stiff power walk: arms pumping at right angles, long straight-legged strides; he never runs
    float a = Math.min(1.0F, limbSwingAmount * 1.2F);
    float c = limbSwing * 0.85F;
    float s = Mth.sin(c);
    this.rightLeg.xRot = s * 0.7F * a;
    this.leftLeg.xRot = -s * 0.7F * a;
    this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.4F)) * 0.2F * a;
    this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.4F)) * 0.2F * a;
    this.body.y = 10.5F + Math.abs(Mth.cos(c)) * 0.35F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.xRot = 0.12F;
    this.body.yRot = -s * 0.08F * a;
    this.rightArm.xRot = -s * 0.8F * a - 0.1F;
    this.leftArm.xRot = s * 0.8F * a - 0.1F;
    this.rightArm.zRot = 0.1F;
    this.leftArm.zRot = -0.1F;
    this.rightForearm.xRot = -1.45F;
    this.leftForearm.xRot = -1.45F;
    this.neck.xRot = 0.3F;
    this.head.xRot = -0.3F + hp * 0.6F;
    this.head.yRot = Mth.clamp(yaw, -1.0F, 1.0F) * 0.8F;
    this.glare.visible = true;
    keySwing = s * 0.8F * a;
    blend = 4.0F;
} else {
    // REACH: both hands slowly out toward you, the head tilting, "o-kyaku-sama..."
    float go = easeOut(pt / 20.0F);
    this.body.xRot = 0.22F * go;
    this.rightArm.xRot = -1.35F * go;
    this.leftArm.xRot = -1.42F * go;
    this.rightArm.zRot = 0.12F * go;
    this.leftArm.zRot = -0.12F * go;
    this.rightForearm.xRot = -0.2F * go;
    this.leftForearm.xRot = -0.15F * go;
    this.rightHand.xRot = -0.3F * go;
    this.leftHand.xRot = -0.25F * go;
    this.neck.xRot = 0.25F;
    this.head.xRot = -0.15F + hp * 0.5F;
    this.head.yRot = hy;
    this.head.zRot = 0.3F * go + Mth.sin(t * 0.05F) * 0.04F;
    this.mouth.visible = go > 0.5F;
    this.glare.visible = Mth.sin(t * 0.3F) > 0.3F;
}
this.head.zRot = this.head.zRot + jit * 0.35F;
this.head.yRot = this.head.yRot + jit2 * 0.3F;
this.keys.zRot = keySwing;
'''
    post = '''
// the loosened tie and the keys hang with gravity, swinging a little behind his movements
level(this.tie, this.body);
this.tie.xRot = this.tie.xRot - 0.12F + Mth.sin(t * 0.21F) * 0.02F;
level(this.keysRing, this.body, this.keys);
this.keysRing.zRot = this.keysRing.zRot + this.keys.zRot * 0.6F;
'''


def solve_ik():
    import numpy as np
    from ent_ik import solve, point, write_consts
    from ent_model import Part
    spec = Manager.__new__(Manager)
    spec.root = Part('root')
    spec.build()
    vary = lambda pre: [(f'{pre}_arm', 'xRot'), (f'{pre}_arm', 'zRot'), (f'{pre}_arm', 'yRot'),
                        (f'{pre}_forearm', 'xRot'), (f'{pre}_hand', 'xRot')]
    bnd = [(-3.0, 0.8), (-1.3, 1.3), (-1.2, 1.2), (-2.6, 0.1), (-1.6, 1.6)]
    out = {}
    fx = {'body': {'y': 15.4, 'xRot': 0.32}}
    # desk top 0.75 blocks (12 px) -> y 12; the mouse ~8.5 px ahead to his right, the keyboard ahead-left
    r = solve(spec, 'right_hand', (0, 0, 0), (-3.0, 11.6, -7.2), vary('right'), fixed=fx, bounds=bnd,
              init=[-0.9, 0, 0, -0.8, 0], also=[('right_hand', (0, 2.5, 0), (-3.0, 11.6, -9.6), 0.8)])
    out.update({'SI_RX': r['right_arm.xRot'], 'SI_RZ': r['right_arm.zRot'], 'SI_RY': r['right_arm.yRot'],
                'SI_RF': r['right_forearm.xRot'], 'SI_RH': r['right_hand.xRot']})
    r = solve(spec, 'left_hand', (0, 0, 0), (1.6, 11.6, -7.0), vary('left'), fixed=fx, bounds=bnd,
              init=[-0.9, 0, 0, -0.8, 0], also=[('left_hand', (0, 2.5, 0), (1.2, 11.6, -9.4), 0.8)])
    out.update({'SI_LX': r['left_arm.xRot'], 'SI_LZ': r['left_arm.zRot'], 'SI_LY': r['left_arm.yRot'],
                'SI_LF': r['left_forearm.xRot'], 'SI_LH': r['left_hand.xRot']})
    write_consts(__file__, 'Manager', out)
    print('solved', len(out))


# --- solved constants (ent_ik), do not edit by hand
Manager.consts = {
    'SI_LF': -1.5498,
    'SI_LH': 0.1025,
    'SI_LX': -0.3153,
    'SI_LY': 0.3113,
    'SI_LZ': 0.5213,
    'SI_RF': -1.6652,
    'SI_RH': 0.1725,
    'SI_RX': -0.3736,
    'SI_RY': -0.1392,
    'SI_RZ': -0.4205,
}
# --- end solved constants


SPEC = Manager


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.dining()
    dk = SC.desk()
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(1, 60, yaw_view=yv, scene=sc)))
    ims.append(('face', b.view(1, 60, yaw_view=-10, pitch_view=0, size=(190, 190), ortho=0.36, center_y=1.6, scene=sc)))
    ims.append(('3/4 face', b.view(1, 60, yaw_view=-40, pitch_view=0, size=(190, 190), ortho=0.36, center_y=1.6, scene=sc)))
    rows.append(('STAND (1): slouched, head forward', ims))
    ims = []
    for (yv, lab) in ((-90, 'left profile (office window)'), (40, '3/4'), (180, 'from behind (monitors)'), (0, 'front')):
        ims.append((lab, b.view(0, 60, yaw_view=yv, pitch_view=8, scene=dk)))
    ims.append(('profile close', b.view(0, 60, yaw_view=-90, pitch_view=0, size=(190, 190), ortho=0.45, center_y=1.35, scene=dk)))
    rows.append(('SIT (0): at the desk, the mouse hand twitching, glasses lit by the monitors', ims))
    walk = lambda tt: (tt * 0.5, 0.85)                         # noqa: E731
    ims = []
    for tt in (0, 4, 8, 12):
        ims.append((f'side t{tt}', b.view([(0, 2)], 100 + tt, yaw_view=90, limb=walk, scene=sc)))
    ims.append(('front', b.view([(0, 2)], 104, yaw_view=0, limb=walk, scene=sc)))
    ims.append(('head lag', b.view([(0, 2)], 110, yaw_view=20, limb=walk, head=lambda tt: (60.0 if tt > 104 else -40.0, 0.0), scene=sc)))
    rows.append(('WALK (2): stiff, head jutting forward; the head follows a moment late', ims))
    ims = []
    for ptv in (0, 8, 16, 40):
        ims.append((f'pt {ptv}', b.view([(0, 1), (100, 3)], 100 + ptv, yaw_view=75, scene=sc)))
    ims.append(('front pt 40', b.view([(0, 1), (100, 3)], 140, yaw_view=10, pitch_view=5, scene=sc)))
    rows.append(('BOW (3): deep, the head hanging, the tie dangling straight down', ims))
    ims = []
    for ptv in (0, 18, 35, 52, 70, 105):
        ims.append((f'pt {ptv}', b.view([(0, 1), (100, 4)], 100 + ptv, yaw_view=0, scene=sc)))
    rows.append(('LOOK (4): scanning left / right, the body lagging, the glasses flash at the centre', ims))
    run = lambda tt: (tt * 0.8, 1.0)                           # noqa: E731
    ims = []
    for tt in (0, 3, 6, 9):
        ims.append((f'CHASE side t{tt}', b.view([(0, 5)], 200 + tt, yaw_view=90, limb=run, scene=sc)))
    ims.append(('CHASE front', b.view([(0, 5)], 204, yaw_view=-15, limb=run, scene=sc)))
    for ptv in (6, 30):
        ims.append((f'REACH pt {ptv}', b.view([(0, 1), (100, 6)], 100 + ptv, yaw_view=-30, scene=sc)))
    rows.append(('CHASE (5) power walk / REACH (6)', ims))
    ims = []
    ims.append(('finder: profile through the office window', b.view(0, 60, size=(300, 240), eye=(-3.2, 1.55, -0.3),
                                                                    target=(0, 1.2, -0.3), finder=True, scene=dk, fov=45)))
    ims.append(('naked eye a=0.85: CHASE', b.view([(0, 5)], 204, size=(300, 240), eye=(0.3, 1.62, 5.0), target=(0, 1.2, 0),
                                                  night=True, alpha=0.85, limb=run, scene=sc, fov=50)))
    ims.append(('naked eye: BOW', b.view([(0, 1), (100, 3)], 140, size=(300, 240), eye=(1.5, 1.62, 4.0), target=(0, 0.9, 0),
                                         night=True, alpha=0.85, scene=sc, fov=50)))
    rows.append(('night', ims))
    return rows
