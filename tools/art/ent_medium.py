"""霊媒師 八雲トキ the medium - 0.6 x 1.6. A living old woman (not a spirit, rendered opaque): grey hair in a bun with a
hairpin, a dark purple kimono under a black haori with white family crests, wide sleeves, white tabi and zori, prayer
beads (数珠) with a purple tassel. Kind, stern, tired. Poses: 0 IDLE, 1 TALK, 2 PRAY, 3 WALK, 4 KNEEL, 5 BOW."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

SKIN = ['#5e4a40', '#7a6253', '#93786a', '#ab917f', '#c2a895', '#d4bcaa', '#e2ccbb']
HAIR = ['#5d5b59', '#77746f', '#918d88', '#aaa6a0', '#c2beb8', '#d8d5cf', '#e8e5df']
KIMONO = ['#170f1e', '#21162b', '#2c1d38', '#382546', '#452e56', '#533866', '#634377']
HAORI = ['#08070a', '#0e0d11', '#151318', '#1d1a22', '#26222c', '#312c38']
OBI = ['#4a3e28', '#5e4f32', '#76643f', '#8f7a4d', '#a7905c']
WHITE = ['#8f8d87', '#aaa8a1', '#c4c2bb', '#dad8d1', '#ebe9e2']
WOOD = ['#1d120c', '#2a1a10', '#3b2516', '#4e321e', '#644128']
TASSEL = ['#2c1236', '#3e1a4c', '#542464', '#6b307c']


class Medium(Spec):
    id = 'medium'
    java_name = 'MediumModel'
    module = 'ent_medium'
    translucent = False
    tex_w = 64
    tex_h = 64
    height = 1.6
    poses = [('IDLE', 0), ('TALK', 1), ('PRAY', 2), ('WALK', 3), ('KNEEL', 4), ('BOW', 5)]
    doc = """霊媒師 八雲トキ (the medium): a living old woman, rendered opaque. Grey hair in a bun, a dark purple kimono under
    a black haori with white crests, white tabi, zori, prayer beads with a purple tassel.
    Pose ids: 0 IDLE (hands together before her, the beads hanging; she follows you with her head and blinks),
    1 TALK (the right hand gestures, a nod now and then, the mouth moving), 2 PRAY (palms pressed together before
    her face with the beads looped over them, head bowed, rocking slightly with the chant), 3 WALK (small kimono
    steps, a little hunched), 4 KNEEL (seiza: sitting on her heels, hands on her thighs), 5 BOW (a polite bow with the
    hands on the thighs; from KNEEL a seated bow with the hands on the floor)."""

    def build(self):
        r = self.root
        body = Part('body', (0, 13.5, 0))
        body.cube(Cube((-3, -7, -1.75), (6, 7, 3.5), 'haori', tag='torso'))
        hskirt = Part('haori_skirt', (0, 0, 0), cubes=[Cube((-3.25, 0, -2.0), (6.5, 3.5, 4), 'haori', tag='hskirt',
                                                             faces=('right', 'front', 'left', 'back', 'bottom'))])
        # the kimono's lower half as one narrow tube to the ankles (standing / walking); legs show only in seiza
        kimono = Part('kimono', (0, 0, 0), cubes=[Cube((-2.75, 0, -1.75), (5.5, 9.5, 3.5), 'kimono', tag='kimono',
                                                       faces=('right', 'front', 'left', 'back', 'bottom'))])
        neck = Part('neck', (0, -7, 0), cubes=[Cube((-1, -1, -1), (2, 1, 2), 'skin', tag='neck',
                                                    faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1, 0), cubes=[Cube((-2.75, -6, -2.5), (5.5, 6, 5), 'head', tag='head')])
        bun = Part('bun', (0, -4.5, 2.5), cubes=[Cube((-1.25, -1.25, 0), (2.5, 2.5, 2), 'hair', tag='bun',
                                                       faces=('top', 'bottom', 'right', 'left', 'back'))])
        pin = Part('pin', (0, 0, 1.0), rot=(0, 0, 0.5), cubes=[Cube((-2.25, -0.25, -0.25), (4.5, 0.5, 0.5), 'pin', tag='pin')])
        bun.add(pin)
        lids = card('lids', (0, -3.5, -2.58), (-2.25, -0.5, 0), (4.5, 1, 0), 'lids')
        mouth = card('mouth', (0, -1.75, -2.58), (-1.0, 0, 0), (2, 1, 0), 'mouth')
        head.add(bun, lids, mouth)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 3.85, -6.5, 0), cubes=[Cube((-0.75, -0.5, -0.75), (1.5, 4.5, 1.5), 'haori', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.0, 0), cubes=[Cube((-0.75, 0, -0.75), (1.5, 4, 1.5), 'haori', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.0, 0), cubes=[Cube((-0.75, 0, -0.5), (1.5, 2, 1), 'skin', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            sleeve = Part(f'{pre}_sleeve', (side * 0.5, 0.0, 0.0), cubes=[Cube((-0.5, 0, -2.25), (1, 5.5, 4.5), 'haori', tag=f'{pre}_sleeve')])
            fore.add(hand, sleeve)
            arm.add(fore)
            arms[pre] = (arm, hand)
        beads = Part('beads', (0.0, 1.6, 0.0), cubes=[Cube((-1.0, 0, -0.75), (2, 0.5, 1.5), 'beads', tag='beads')])
        tassel = Part('tassel', (0.5, 0.25, -0.75), cubes=[Cube((-0.25, 0, -0.25), (0.5, 2.5, 0.5), 'tassel', tag='tassel')])
        beads.add(tassel)
        arms['left'][1].add(beads)
        body.add(hskirt, kimono, neck, arms['right'][0], arms['left'][0])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.25, 13.5, 0), cubes=[Cube((-1.25, 0, -1.5), (2.5, 5, 3), 'kimono', tag=f'{pre}_thigh',
                                                                          faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 5, 0), cubes=[Cube((-1.25, 0, -1.5), (2.5, 4.5, 3), 'kimono', tag=f'{pre}_shin',
                                                             faces=('right', 'front', 'left', 'back'))])
            foot = Part(f'{pre}_foot', (0, 4.5, 0), cubes=[Cube((-1.0, 0, -2.25), (2, 1, 3.5), 'tabi', tag=f'{pre}_foot')])
            shin.add(foot)
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 8181)
        F, EF, nz = pt.F, pt.EF, pt.nz
        for tag in ('neck', 'right_hand', 'left_hand'):
            pt.base(tag, SKIN, 4.3, amp=0.3, fine=0.3, top=0.5)
        for pre in ('right', 'left'):
            fs = F(f'{pre}_hand')
            for n in ('front', 'back'):
                f = fs[n]
                for x in range(f.w):
                    f.dl(x, 1, -0.06)                         # knuckle wrinkles
                    f.set(x, f.h - 1, P(SKIN, 4.8))
                f.dl(1, 2, -0.05)
            fs['back'].set(2, 1, P(SKIN, 2.5))                # an age spot
        # ---------------------------------------------------------------- haori: black silk, crests, open front
        def silk(Pp, nrm, n):
            return np.sin(Pp[..., 1] * 1.3 + Pp[..., 0] * 0.6 + pt.n3(Pp * 0.6) * 1.4) * 0.45
        for tag in ('torso', 'hskirt', 'right_upper', 'left_upper', 'right_fore', 'left_fore', 'right_sleeve', 'left_sleeve'):
            pt.base(tag, HAORI, 2.4, amp=0.35, fine=0.3, top=0.6, fn=silk)

        def crest(f, cx, cy):
            for (dx, dy) in ((0, -1), (-1, 0), (0, 0), (1, 0), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)):
                f.set(cx + dx, cy + dy, P(WHITE, 4) if (dx, dy) != (0, 0) else P(HAORI, 1))
            for (dx, dy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                f.set(cx + dx, cy + dy, P(WHITE, 2.5))
        fs = F('torso')
        f = fs['front']                                       # 12 x 14: the haori open, kimono + white collar + obi
        for y in range(f.h):
            for x in range(4, 8):
                f.set(x, y, P(KIMONO, 3.2 + nz(f, x, y, 0.4, 0.2)))
            f.set(3, y, P(HAORI, 4))                          # the haori's front edges
            f.set(8, y, P(HAORI, 4))
        for (x, y) in ((4, 0), (7, 0), (4, 1), (7, 1), (5, 1), (6, 1), (5, 2), (6, 2)):
            f.set(x, y, P(WHITE, 4))                          # the juban collar crossing left over right
        f.set(5, 0, P(SKIN, 3.5))
        f.set(6, 0, P(SKIN, 3.5))
        f.set(4, 2, P(KIMONO, 4.5))
        f.set(7, 2, P(KIMONO, 4.5))
        for y in range(9, 13):                                 # the obi
            for x in range(4, 8):
                f.set(x, y, P(OBI, 2.5 + nz(f, x, y, 0.6, 0.3) + (0.8 if y == 10 else 0)))
        f.set(5, 11, P(OBI, 0.5))
        f.set(6, 11, P(OBI, 0.5))
        for y in range(5, 9):                                 # the haori ties (羽織紐) hanging at the chest
            f.set(4 if y % 2 else 7, y, P(WHITE, 3.5))
        f.set(5, 7, P(WHITE, 4.2))
        f.set(6, 7, P(WHITE, 4.2))
        crest(f, 1, 3)
        crest(f, 10, 3)
        crest(fs['back'], 6, 2)
        for pre in ('right', 'left'):
            crest(F(f'{pre}_sleeve')[('right' if pre == 'right' else 'left')], 3, 2)
        f = F('hskirt')['front']                              # 13 x 7
        for y in range(f.h):
            for x in range(5, 8):
                f.set(x, y, P(KIMONO, 3.2 + nz(f, x, y, 0.4, 0.2)))
            f.set(4, y, P(HAORI, 4))
            f.set(8, y, P(HAORI, 4))
        # ---------------------------------------------------------------- kimono legs: purple, the front overlap seam
        def kim(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 2.0 + pt.n3(Pp * 0.7) * 1.2) * 0.4
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_shin'):
                fs = pt.base(tag, KIMONO, 3.2, amp=0.35, fine=0.3, top=0.5, fn=kim)
                f = fs['front']
                for y in range(f.h):
                    for x in range(f.w):
                        if (x * 3 + y * 5) % 11 == 0:
                            f.dl(x, y, 0.07)                  # a faint woven pattern
            fs = F(f'{pre}_shin')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]
                for x in range(f.w):
                    f.set(x, f.h - 1, P(KIMONO, 1.5))         # the hem
        fs = pt.base('kimono', KIMONO, 3.2, amp=0.35, fine=0.3, top=0.5, fn=kim)
        f = fs['front']                                       # 11 x 19: the left panel crosses over the right
        for y in range(f.h):
            sx = 6 + (y * 2) // f.h
            f.set(sx, y, P(KIMONO, 1.0))
            f.dl(sx + 1, y, 0.05)
            for x in range(f.w):
                if (x * 3 + y * 5) % 11 == 0:
                    f.dl(x, y, 0.07)
        for n in ('front', 'back', 'left', 'right'):
            fc = fs[n]
            for x in range(fc.w):
                fc.set(x, fc.h - 1, P(KIMONO, 1.4))           # the hem
                fc.dl(x, fc.h - 2, 0.04)
        for y in range(F('right_thigh')['front'].h):
            F('right_thigh')['front'].set(4, y, P(KIMONO, 1.0))         # the overlap seam (left over right)
        for y in range(F('right_shin')['front'].h):
            F('right_shin')['front'].set(4, y, P(KIMONO, 1.0))
        for pre in ('right', 'left'):                          # tabi + zori
            fs = F(f'{pre}_foot')
            for n, fc in fs.items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(WHITE, 3.0 + nz(fc, x, y, 0.4, 0.2) + (0.6 if n == 'top' else 0)))
            fs['bottom'].fill(P(WOOD, 1))
            for n in ('front', 'back', 'left', 'right'):
                fc = fs[n]
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(WOOD, 1.5))         # the zori sole
            ft = fs['top']
            for y in range(ft.h - 3, ft.h):
                ft.set(ft.w // 2, y, P(TASSEL, 2))            # the thong (鼻緒)
            ft.set(ft.w // 2 - 1, ft.h - 3, P(TASSEL, 2))
            ft.set(ft.w // 2 + 1, ft.h - 3, P(TASSEL, 2))
        # ---------------------------------------------------------------- head: grey hair, the old face
        def hairfn(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 3.0 + Pp[..., 1] * 1.5) * 0.5
        fs = pt.base('head', HAIR, 3.4, amp=0.4, fine=0.3, top=0.5, fn=hairfn)
        top = fs['top']
        for y in range(top.h):
            top.set(top.w // 2, y, P(SKIN, 4))               # the centre parting
        f = fs['front']                                       # 11 x 12
        face = [
            'hhhhh5hhhhh',
            'hh5555555hh',
            'h555w5w555h',
            'h4ggg5ggg4h',
            'h3LeeL4LeeL',
            'h4K4454K4h4',
            '34444554443',
            '3#4#n5n#4#3',
            '3#44mlm44#3',
            '33#44444#33',
            'h334444433h',
            'hh33333333h',
        ]
        face[4] = 'hLeeL4LeeLh'
        face[5] = 'hK44454 44Kh'[:11]
        face[5] = 'hK4445444Kh'
        leg = {'h': None, '3': (SKIN, 3.0), '4': (SKIN, 4.0), '5': (SKIN, 4.8), '#': (SKIN, 2.2), 'w': (SKIN, 3.4),
               'g': (HAIR, 2.0), 'L': (SKIN, 2.4), 'e': C('#241a16'), 'm': (SKIN, 2.0), 'l': C('#7d5049'),
               'K': (SKIN, 2.8), 'n': (SKIN, 2.0)}
        pt.pattern(f, face, leg)
        for x in (2, 3, 7, 8):                                 # forehead lines
            f.dl(x, 2, -0.05)
        f.set(9, 6, P(SKIN, 1.6))                             # a mole by her nose
        ef = EF('head')['front']
        ef.set(3, 4, '#2a2420')                               # a little life in her eyes
        ef.set(8, 4, '#2a2420')
        for n in ('right', 'left'):
            fc = fs[n]                                        # 10 x 12, xf = 0 at the face
            for y in range(4, fc.h):
                for x in range(fc.w):
                    xf = (fc.w - 1 - x) if n == 'right' else x
                    if (xf <= 2 and y >= 4) or (xf <= 4 and y >= 8):
                        fc.set(x, y, P(SKIN, 3.4 - 0.3 * xf + nz(fc, x, y, 0.2, 0.1)))
            for (ex, ey, v) in ((4, 5, 2.8), (5, 5, 2.4), (4, 6, 3.0), (5, 6, 2.0), (4, 7, 3.2)):
                x = (fc.w - 1 - ex) if n == 'right' else ex
                fc.set(x, ey, P(SKIN, v))
        fs['bottom'].fill(P(SKIN, 2.5))
        for n, fc in F('bun').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(HAIR, 3.0 + math.sin((x + y) * 1.3) * 0.7 + nz(fc, x, y, 0.3, 0.2)))
        for n, fc in F('pin').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(WOOD, 3.5 + (1 if x % 3 == 0 else 0)))
            if n in ('front', 'back'):
                fc.set(0, 0, C('#8c2d3a'))                    # a red bead at the end of the hairpin
        # ---------------------------------------------------------------- blink lids, talking mouth
        lf = F('lids')['front']                               # 9 x 2 over face cols 1..9, rows 4..5
        for x in range(lf.w):
            if x != 4:
                lf.set(x, 0, P(SKIN, 3.0 if x in (0, 3, 5, 8) else 3.4))
                lf.set(x, 1, P(SKIN, 2.4))
        mf = F('mouth')['front']                              # 4 x 2
        for x in range(mf.w):
            mf.set(x, 0, C('#3a2420'))
            mf.set(x, 1, C('#7a4f4a') if x in (1, 2) else C('#4a2c28'))
        # ---------------------------------------------------------------- beads, tassel
        for n, fc in F('beads').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    bead = (x + y) % 2 == 0
                    fc.set(x, y, P(WOOD, 3.6 if bead else 1.2))
        for n, fc in F('tassel').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(TASSEL, 2.5 - (1 if y == 0 else 0) + (0.6 if y % 2 else 0)))
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.05F);
float look = Mth.clamp(yaw, -0.9F, 0.9F);
float lookP = Mth.clamp(pitch, -0.4F, 0.4F);
// she blinks
float bl = (t + 13.0F) % 83.0F;
this.lids.visible = bl < 3.0F;
this.mouth.visible = false;
this.body.xRot = 0.1F + breath * 0.008F;
this.kimono.xRot = -0.08F;
this.neck.xRot = 0.12F;
this.head.yRot = look * 0.8F;
this.head.xRot = lookP * 0.6F - 0.05F;
boolean kneeling = pose == KNEEL || (pose == BOW && prev == KNEEL);
// standing she is one narrow kimono tube with her tabi shuffling under the hem; kneeling, the folded legs show
this.kimono.visible = !kneeling;
this.rightLeg.skipDraw = !kneeling;
this.leftLeg.skipDraw = !kneeling;
this.rightShin.skipDraw = !kneeling;
this.leftShin.skipDraw = !kneeling;
if (kneeling) {
    // seiza: sitting on her heels, the shins flat on the floor behind
    this.body.y = 19.5F;
    this.rightLeg.y = 19.5F;
    this.leftLeg.y = 19.5F;
    this.rightLeg.xRot = -1.5F;
    this.leftLeg.xRot = -1.5F;
    // the shins fold right back under the thighs (their pivot drops below the thigh)
    this.rightShin.xRot = 3.0F;
    this.leftShin.xRot = 3.0F;
    this.rightShin.z = 3.0F;
    this.leftShin.z = 3.0F;
    this.body.xRot = 0.04F + breath * 0.008F;
    this.haoriSkirt.xRot = -0.2F;
}
if (pose == IDLE || pose == WALK || pose == KNEEL) {
    // hands together before her, the beads hanging from them
    this.rightArm.xRot = $HT_RX$;
    this.rightArm.zRot = $HT_RZ$;
    this.rightArm.yRot = $HT_RY$;
    this.rightForearm.xRot = $HT_RF$;
    this.rightHand.xRot = $HT_RH$;
    this.leftArm.xRot = $HT_LX$;
    this.leftArm.zRot = $HT_LZ$;
    this.leftArm.yRot = $HT_LY$;
    this.leftForearm.xRot = $HT_LF$;
    this.leftHand.xRot = $HT_LH$;
    if (pose == KNEEL) {
        this.rightArm.xRot = $KN_RX$;
        this.rightArm.zRot = $KN_RZ$;
        this.rightArm.yRot = $KN_RY$;
        this.rightForearm.xRot = $KN_RF$;
        this.rightHand.xRot = $KN_RH$;
        this.leftArm.xRot = $KN_LX$;
        this.leftArm.zRot = $KN_LZ$;
        this.leftArm.yRot = $KN_LY$;
        this.leftForearm.xRot = $KN_LF$;
        this.leftHand.xRot = $KN_LH$;
    }
    if (pose == WALK) {
        float a = Math.min(1.0F, limbSwingAmount * 1.6F);
        float c = limbSwing * 1.1F;
        float s = Mth.sin(c);
        this.rightLeg.xRot = s * 0.22F * a;
        this.leftLeg.xRot = -s * 0.22F * a;
        this.rightShin.xRot = Math.max(0.0F, Mth.sin(c - 1.0F)) * 0.25F * a;
        this.leftShin.xRot = Math.max(0.0F, -Mth.sin(c - 1.0F)) * 0.25F * a;
        this.body.y = 13.5F + Math.abs(Mth.cos(c)) * 0.2F * a;
        this.rightLeg.y = this.body.y;
        this.leftLeg.y = this.body.y;
        this.body.xRot = 0.16F;
        this.body.zRot = s * 0.02F * a;
        this.kimono.xRot = -0.16F + Math.abs(s) * 0.04F * a;
    }
} else if (pose == TALK) {
    // the left hand keeps the beads at her waist; the right hand speaks; a nod now and then
    float g = Mth.sin(t * 0.11F);
    float g2 = Mth.sin(t * 0.07F + 1.0F);
    this.leftArm.xRot = $HT_LX$;
    this.leftArm.zRot = $HT_LZ$;
    this.leftArm.yRot = $HT_LY$;
    this.leftForearm.xRot = $HT_LF$;
    this.leftHand.xRot = $HT_LH$;
    this.rightArm.xRot = -0.55F + g * 0.12F;
    this.rightArm.zRot = 0.18F + g2 * 0.08F;
    this.rightArm.yRot = 0.2F;
    this.rightForearm.xRot = -1.1F + g2 * 0.2F;
    this.rightHand.xRot = -0.5F + g * 0.25F;
    this.head.xRot = this.head.xRot + Math.max(0.0F, Mth.sin(t * 0.09F)) * 0.08F;
    this.mouth.visible = Mth.sin(t * 1.3F) * Mth.sin(t * 0.37F) > 0.15F;
} else if (pose == PRAY) {
    // palms pressed together before her face, the beads over them; rocking with the chant
    float rock = Mth.sin(t * 0.21F);
    this.body.xRot = 0.12F + rock * 0.03F;
    this.neck.xRot = 0.2F;
    this.head.xRot = 0.32F + rock * 0.04F;
    this.head.yRot = 0.0F;
    this.rightArm.xRot = $PR_RX$;
    this.rightArm.zRot = $PR_RZ$;
    this.rightArm.yRot = $PR_RY$;
    this.rightForearm.xRot = $PR_RF$;
    this.rightHand.xRot = $PR_RH$;
    this.leftArm.xRot = $PR_LX$;
    this.leftArm.zRot = $PR_LZ$;
    this.leftArm.yRot = $PR_LY$;
    this.leftForearm.xRot = $PR_LF$;
    this.leftHand.xRot = $PR_LH$;
    this.mouth.visible = Mth.sin(t * 0.9F) > -0.2F;
    this.lids.visible = true;
} else {
    // BOW: standing, a polite bow with the hands on her thighs; kneeling, a seated bow with the hands on the floor
    float down = easeOut(pt / 14.0F) * (1.0F - span(pt, 50.0F, 64.0F));
    if (kneeling) {
        this.body.xRot = 0.04F + 0.95F * down;
        this.head.xRot = 0.2F * down;
        this.rightArm.xRot = Mth.lerp(down, $KN_RX$, -1.0F);
        this.leftArm.xRot = Mth.lerp(down, $KN_LX$, -1.0F);
        this.rightArm.zRot = 0.1F;
        this.leftArm.zRot = -0.1F;
        this.rightForearm.xRot = Mth.lerp(down, $KN_RF$, -0.3F);
        this.leftForearm.xRot = Mth.lerp(down, $KN_LF$, -0.3F);
        this.rightHand.xRot = -1.0F * down;
        this.leftHand.xRot = -1.0F * down;
    } else {
        this.body.xRot = 0.1F + 0.6F * down;
        this.head.xRot = 0.15F * down;
        this.rightArm.xRot = -0.55F * down;
        this.leftArm.xRot = -0.55F * down;
        this.rightArm.zRot = -0.12F * down;
        this.leftArm.zRot = 0.12F * down;
        this.rightForearm.xRot = -0.25F * down;
        this.leftForearm.xRot = -0.25F * down;
        this.haoriSkirt.xRot = -0.45F * down;
    }
    this.lids.visible = true;
}
'''
    post = '''
// the wide sleeves and the tassel hang with gravity
level(this.rightSleeve, this.body, this.rightArm, this.rightForearm);
level(this.leftSleeve, this.body, this.leftArm, this.leftForearm);
this.rightSleeve.xRot = this.rightSleeve.xRot + Mth.sin(t * 0.05F) * 0.03F;
this.leftSleeve.xRot = this.leftSleeve.xRot + Mth.sin(t * 0.05F + 1.0F) * 0.03F;
level(this.tassel, this.body, this.leftArm, this.leftForearm, this.leftHand, this.beads);
this.tassel.zRot = this.tassel.zRot + Mth.sin(t * 0.08F) * 0.06F;
'''


def solve_ik():
    import numpy as np
    from ent_ik import solve, point, write_consts, mirror
    from ent_model import Part
    spec = Medium.__new__(Medium)
    spec.root = Part('root')
    spec.build()
    vary = lambda pre: [(f'{pre}_arm', 'xRot'), (f'{pre}_arm', 'zRot'), (f'{pre}_arm', 'yRot'),
                        (f'{pre}_forearm', 'xRot'), (f'{pre}_hand', 'xRot')]
    bnd = [(-3.0, 0.8), (-1.3, 1.3), (-1.3, 1.3), (-2.6, 0.1), (-1.6, 1.6)]
    out = {}

    def put(prefix, r):
        for k, v in r.items():
            part, attr = k.split('.')
            s = 'R' if part.startswith('right') else 'L'
            key = {'arm.xRot': 'X', 'arm.zRot': 'Z', 'arm.yRot': 'Y', 'forearm.xRot': 'F', 'hand.xRot': 'H'}[
                part.split('_', 1)[1] + '.' + attr]
            out[f'{prefix}_{s}{key}'] = v
    # hands together before her waist (right over left)
    fx = {'body': {'xRot': 0.1}}
    r = solve(spec, 'right_hand', (0, 1.0, 0), (-0.2, 13.0, -3.6), vary('right'), fixed=fx, bounds=bnd, init=[-0.4, 0, 0, -1.0, 0])
    put('HT', r)
    r = solve(spec, 'left_hand', (0, 1.0, 0), (0.6, 13.4, -3.3), vary('left'), fixed=fx, bounds=bnd, init=[-0.4, 0, 0, -1.0, 0])
    put('HT', r)
    # PRAY: palms together before her face (gassho)
    fx = {'body': {'xRot': 0.12}, 'neck': {'xRot': 0.2}, 'head': {'xRot': 0.32}}
    r = solve(spec, 'right_hand', (0, 1.0, -0.5), (-0.4, 7.4, -4.6), vary('right'), fixed=fx, bounds=bnd,
              init=[-1.6, 0, 0, -1.5, 0], also=[('right_hand', (0, 2.0, -0.5), (-0.4, 5.6, -5.0), 0.6)])
    put('PR', r)
    put('PR', mirror(r))
    # KNEEL: hands resting on her thighs
    fx = {'body': {'y': 19.5, 'xRot': 0.04}}
    r = solve(spec, 'right_hand', (0, 1.0, 0), (-1.4, 17.6, -4.2), vary('right'), fixed=fx, bounds=bnd,
              init=[-0.6, 0, 0, -0.9, 0], also=[('right_hand', (0, 2.0, 0), (-1.4, 17.8, -6.0), 0.5)])
    put('KN', r)
    put('KN', mirror(r))
    write_consts(__file__, 'Medium', out)
    print('solved', len(out))


# --- solved constants (ent_ik), do not edit by hand
Medium.consts = {
    'HT_LF': -1.0465,
    'HT_LH': -0.3151,
    'HT_LX': 0.1660,
    'HT_LY': 0.2119,
    'HT_LZ': 0.3889,
    'HT_RF': -1.0605,
    'HT_RH': -0.3148,
    'HT_RX': 0.1258,
    'HT_RY': -0.2645,
    'HT_RZ': -0.4311,
    'KN_LF': -1.4162,
    'KN_LH': -0.2180,
    'KN_LX': 0.1150,
    'KN_LY': -0.0177,
    'KN_LZ': 0.5169,
    'KN_RF': -1.4162,
    'KN_RH': -0.2180,
    'KN_RX': 0.1150,
    'KN_RY': 0.0177,
    'KN_RZ': -0.5169,
    'PR_LF': -1.5799,
    'PR_LH': -0.8065,
    'PR_LX': -0.6025,
    'PR_LY': 0.7324,
    'PR_LZ': 0.1029,
    'PR_RF': -1.5799,
    'PR_RH': -0.8065,
    'PR_RX': -0.6025,
    'PR_RY': -0.7324,
    'PR_RZ': -0.1029,
}
# --- end solved constants


SPEC = Medium


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.lot()
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(0, 60, yaw_view=yv, scene=sc)))
    ims.append(('face', b.view(0, 60, yaw_view=-10, pitch_view=0, size=(190, 190), ortho=0.33, center_y=1.35, scene=sc)))
    ims.append(('blink', b.view(0, 70 + 83 - 13 + 1 - 60 + 60, yaw_view=-10, pitch_view=0, size=(190, 190), ortho=0.33,
                                center_y=1.35, scene=sc)))
    rows.append(('IDLE (0): hands together, the beads hanging; she blinks', ims))
    ims = []
    for tt in (0, 10, 20, 30):
        ims.append((f't {tt}', b.view(1, 40 + tt, yaw_view=15, scene=sc)))
    ims.append(('side', b.view(1, 47, yaw_view=70, scene=sc)))
    rows.append(('TALK (1): gestures, nods, the mouth moving', ims))
    ims = []
    for (yv, lab) in ((0, 'front'), (40, '3/4'), (90, 'side')):
        ims.append((lab, b.view(2, 40, yaw_view=yv, scene=sc)))
    rows.append(('PRAY (2): palms together before her face, beads looped over them, rocking', ims))
    walk = lambda tt: (tt * 0.5, 0.8)                          # noqa: E731
    ims = []
    for tt in (0, 3, 6, 9):
        ims.append((f'side t{tt}', b.view([(0, 3)], 100 + tt, yaw_view=90, limb=walk, scene=sc)))
    ims.append(('front', b.view([(0, 3)], 104, yaw_view=15, limb=walk, scene=sc)))
    rows.append(('WALK (3): small kimono steps', ims))
    ims = []
    for (yv, lab) in ((0, 'front'), (60, '3/4'), (90, 'side')):
        ims.append((f'KNEEL {lab}', b.view(4, 40, yaw_view=yv, scene=sc)))
    for ptv in (0, 8, 20):
        ims.append((f'BOW pt {ptv}', b.view([(0, 0), (100, 5)], 100 + ptv, yaw_view=70, scene=sc)))
    ims.append(('seated bow', b.view([(0, 4), (100, 5)], 120, yaw_view=70, scene=sc)))
    rows.append(('KNEEL (4) seiza / BOW (5) standing and seated', ims))
    ims = []
    ims.append(('at night by her car', b.view(1, 40, size=(300, 260), eye=(0.5, 1.62, 3.0), target=(0, 1.1, 0), night=True,
                                              scene=sc, fov=50, amb=0.25)))
    rows.append(('night', ims))
    return rows
