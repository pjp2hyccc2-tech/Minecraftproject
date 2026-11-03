"""かくれんぼの子 the child (春日 ゆうか, 5) - 0.45 x 1.1. Went missing during hide-and-seek in the restaurant in 2001
and was never found; she still plays. A yellow kindergarten hat with its elastic, a pale blue smock with a tulip
name tag 「ゆうか」, white socks, red indoor shoes, a black bob whose fringe covers her eyes; too still.
Poses: 0 RUN, 1 COUNT, 2 HIDE, 3 PEEK, 4 FOUND, 5 STAND, 6 GIGGLE."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

SKIN = ['#4c4f55', '#6a6d71', '#8a8c8c', '#a7a8a5', '#bfbfba', '#d2d1cb', '#e1e0d9']
HAIR = ['#060607', '#0c0c0e', '#131316', '#1b1b20', '#26262c', '#33333b']
HAT = ['#7a5a0c', '#9c7610', '#bf9414', '#dcb31f', '#efcb38', '#f8df66']
SMOCK = ['#4d6478', '#627c92', '#7895ac', '#8eacc2', '#a5c2d6', '#bbd4e4', '#d1e3ee']
WHITE = ['#8b8c8a', '#a8a9a5', '#c2c2bd', '#d6d5cf', '#e6e5df']
RED = ['#4e0d0f', '#6e1416', '#901d1e', '#b02a28', '#c83e36']
TULIP = ['#b85a78', '#d47a96', '#eda3b8']


class Child(Spec):
    id = 'child'
    java_name = 'ChildModel'
    module = 'ent_child'
    tex_w = 64
    tex_h = 64
    height = 1.15
    poses = [('RUN', 0), ('COUNT', 1), ('HIDE', 2), ('PEEK', 3), ('FOUND', 4), ('STAND', 5), ('GIGGLE', 6)]
    doc = """かくれんぼの子 (the child, 春日 ゆうか): five years old, lost while playing hide-and-seek here in 2001. Yellow
    kindergarten hat, pale blue smock with her name tag, red shoes, a black bob over her eyes.
    Pose ids: 0 RUN (skipping), 1 COUNT (face to the pillar ~5 px in front, forearms crossed over her eyes against
    it, counting bobs every 16 ticks), 2 HIDE (squatting small, hands over the eyes), 3 PEEK (from the squat the
    hands slide down over 16 ticks, the head comes up and turns to you, an eye shows between the bangs),
    4 FOUND (springs up, points straight at you, mouth wide - the snap is the scare), 5 STAND (hands behind her
    back, head tilted; every few seconds the tilt flips to the other side), 6 GIGGLE (hand over her mouth,
    shoulders shaking)."""

    def build(self):
        r = self.root
        body = Part('body', (0, 17, 0))
        body.cube(Cube((-2.5, -5, -1.5), (5, 5, 3), 'smock', tag='torso', faces=('top', 'right', 'front', 'left', 'back')))
        smock = Part('smock', (0, 0, 0), cubes=[Cube((-3, 0, -1.75), (6, 2, 3.5), 'smock', tag='hem')])
        neck = Part('neck', (0, -5, 0), cubes=[Cube((-0.75, -0.5, -0.75), (1.5, 0.5, 1.5), 'skin', tag='neck',
                                                    faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -0.5, 0), cubes=[Cube((-2.75, -5, -2.5), (5.5, 5, 5), 'hair', tag='head',
                                                       faces=('bottom', 'right', 'front', 'left', 'back'))])
        bob_r = Part('bob_r', (0, 0, 0), cubes=[Cube((-3.25, -3.5, -2.25), (0.5, 4, 5), 'hair', tag='bob_r',
                                                      faces=('bottom', 'right', 'front', 'back'))])
        bob_l = Part('bob_l', (0, 0, 0), cubes=[Cube((2.75, -3.5, -2.25), (0.5, 4, 5), 'hair', tag='bob_l',
                                                      faces=('bottom', 'left', 'front', 'back'))])
        bob_b = Part('bob_b', (0, 0, 0), cubes=[Cube((-2.75, -3.5, 2.6), (5.5, 4, 0.5), 'hair', tag='bob_b',
                                                      faces=('bottom', 'back', 'right', 'left'))])
        # the round kindergarten hat: a dome (crown + a smaller cap on top) and a short soft brim all round
        crown = Part('hat', (0, -4.5, 0), cubes=[
            Cube((-3, -2, -2.75), (6, 2, 5.5), 'hat', tag='crown', faces=('right', 'front', 'left', 'back', 'top')),
            Cube((-2.25, -2.5, -2.0), (4.5, 0.5, 4), 'hat', tag='dome', faces=('right', 'front', 'left', 'back', 'top'))])
        brims = [
            Part('brim_f', (0, 0, -2.75), cubes=[Cube((-3.5, -0.25, -1.0), (7, 0.5, 1.0), 'hat', tag='brim_f',
                                                       faces=('top', 'bottom', 'front', 'right', 'left'))]),
            Part('brim_b', (0, 0, 2.75), cubes=[Cube((-3.5, -0.25, 0), (7, 0.5, 1.0), 'hat', tag='brim_b',
                                                      faces=('top', 'bottom', 'back', 'right', 'left'))]),
            Part('brim_r', (-3, 0, 0), cubes=[Cube((-0.5, -0.25, -2.75), (0.5, 0.5, 5.5), 'hat', tag='brim_r',
                                                    faces=('top', 'bottom', 'right'))]),
            Part('brim_l', (3, 0, 0), cubes=[Cube((0, -0.25, -2.75), (0.5, 0.5, 5.5), 'hat', tag='brim_l',
                                                   faces=('top', 'bottom', 'left'))]),
        ]
        crown.add(*brims)
        mouth = card('mouth', (0, -1.5, -2.58), (-1.25, 0, 0), (2.5, 1.5, 0), 'mouth')
        jaw = card('jaw', (0, -1.0, -2.64), (-1.5, 0, 0), (3, 1, 0), 'jaw')
        glint = card('glint', (-1.25, -2.75, -2.58), (-0.75, 0, 0), (1.5, 1, 0), 'glint')
        head.add(bob_r, bob_l, bob_b, crown, mouth, jaw, glint)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 3.35, -4.5, 0), cubes=[Cube((-0.75, -0.5, -0.75), (1.5, 3, 1.5), 'smock', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 2.5, 0), cubes=[Cube((-0.75, 0, -0.75), (1.5, 2.5, 1.5), 'skin', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 2.5, 0), cubes=[Cube((-0.75, 0, -0.5), (1.5, 1.5, 1), 'skin', tag=f'{pre}_hand',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            fore.add(hand)
            arm.add(fore)
            arms[pre] = arm
        body.add(smock, neck, arms['right'], arms['left'])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.1, 17, 0), cubes=[Cube((-1, 0, -1), (2, 3, 2), 'skin', tag=f'{pre}_thigh',
                                                                       faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 3, 0), cubes=[Cube((-1, 0, -1), (2, 3, 2), 'skin', tag=f'{pre}_shin',
                                                             faces=('right', 'front', 'left', 'back')),
                                                        Cube((-1, 3, -1.75), (2, 1, 3), 'shoe', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 5151)
        F, EF, nz = pt.F, pt.EF, pt.nz
        # skin: pale, a little grey (dead)
        for tag in ('neck', 'right_fore', 'left_fore', 'right_hand', 'left_hand', 'right_thigh', 'left_thigh',
                    'right_shin', 'left_shin'):
            pt.base(tag, SKIN, 4.4, amp=0.3, fine=0.25, top=0.5)
        for pre in ('right', 'left'):
            fs = F(f'{pre}_hand')
            for n in ('front', 'back'):
                f = fs[n]
                for x in range(f.w):
                    f.set(x, f.h - 1, P(SKIN, 5))
                f.dl(1, 1, -0.06)
            fs = F(f'{pre}_shin')
            for n in ('front', 'back', 'left', 'right'):
                f = fs[n]                                     # white socks with a frilled top, dust at the knees
                for y in range(f.h - 2, f.h):
                    for x in range(f.w):
                        f.set(x, y, P(WHITE, 3.2 + nz(f, x, y, 0.4, 0.3)))
                for x in range(f.w):
                    f.set(x, f.h - 3, P(WHITE, 4) if x % 2 else P(WHITE, 2))
                if n == 'front':
                    f.dl(1, 0, -0.08)
                    f.dl(2, 0, -0.08)
                    f.dl(1, 1, -0.05)
            for n, fc in F(f'{pre}_foot').items():             # red indoor shoes with a white band
                for y in range(fc.h):
                    for x in range(fc.w):
                        fc.set(x, y, P(RED, 2.4 + nz(fc, x, y, 0.4, 0.3) + (0.6 if n == 'top' else 0)))
                if n in ('front', 'left', 'right', 'back'):
                    for x in range(fc.w):
                        fc.set(x, fc.h - 1, P(WHITE, 2))
            ft = F(f'{pre}_foot')['top']
            for y in range(0, 2):
                for x in range(ft.w):
                    ft.set(x, y, P(WHITE, 3))
        # ---------------------------------------------------------------- smock
        def dusty(Pp, nrm, n):
            return -0.6 * np.clip((Pp[..., 1] - 15.0) / 4.0, 0, 1) + pt.n3c(Pp * 2.0)[..., ] * 0.25
        for tag in ('torso', 'hem', 'right_upper', 'left_upper'):
            pt.base(tag, SMOCK, 4.0, amp=0.45, fine=0.35, top=0.6, fn=dusty)
        fs = F('torso')
        f = fs['front']                                       # 10 x 10: round neckline, the tulip tag on her left
        for x in range(3, 7):
            f.set(x, 0, P(SKIN, 3.5))
        f.set(2, 0, P(SMOCK, 2))
        f.set(7, 0, P(SMOCK, 2))
        f.set(3, 1, P(SMOCK, 2))
        f.set(6, 1, P(SMOCK, 2))
        tulip = ['.p.p.', 'ppppp', 'pwwwp', 'pwRwp', '.ppp.']
        for y, row in enumerate(tulip):
            for x, ch in enumerate(row):
                if ch == 'p':
                    f.set(5 + x, 2 + y, P(TULIP, 1 + ((x + y) % 2)))
                elif ch == 'w':
                    f.set(5 + x, 2 + y, C('#f2efe8'))
                elif ch == 'R':
                    f.set(5 + x, 2 + y, C('#c0392b'))
        f.set(6, 4, C('#d24a3a'))                             # 「ゆうか」 in red marker, three tiny strokes
        f.set(8, 4, C('#d24a3a'))
        for x in range(1, 4):                                 # a pocket with a hanky corner
            f.set(x, 7, P(SMOCK, 2.5))
        f.set(1, 8, P(SMOCK, 2.5))
        f.set(3, 8, P(SMOCK, 2.5))
        f.set(2, 6, C('#e8c8d8'))
        fb = fs['back']
        for y in range(1, 4):                                 # the button at the back of the neck
            fb.dl(fb.w // 2, y, -0.06)
        fb.set(fb.w // 2, 1, C('#f0eee8'))
        fh = F('hem')
        for n in ('front', 'back', 'left', 'right'):
            f = fh[n]
            for x in range(f.w):
                f.set(x, f.h - 1, P(SMOCK, 2.2 + nz(f, x, f.h - 1, 0.4, 0.2)))
                if x % 2 == 0:
                    f.dl(x, 0, -0.05)
        for pre in ('right', 'left'):                         # puffed sleeves with elastic at the elbow
            for n, f in F(f'{pre}_upper').items():
                if n in ('front', 'back', 'left', 'right'):
                    for x in range(f.w):
                        f.set(x, f.h - 1, P(SMOCK, 2.4))
                        f.dl(x, 0, 0.05)
        # ---------------------------------------------------------------- hair: the bob and the fringe over the eyes
        def hairfn(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 3.3 + Pp[..., 2] * 2.1) * 0.5
        for tag in ('head', 'bob_r', 'bob_l', 'bob_b'):
            fs = pt.base(tag, HAIR, 2.0, amp=0.5, fine=0.3, top=0.6, fn=hairfn)
            for n in ('right', 'left', 'back', 'front'):
                f = fs[n]
                if tag != 'head' and n in ('right', 'left', 'back', 'front'):
                    for x in range(f.w):                       # blunt cut ends, a little ragged
                        if (x * 5) % 7 == 0:
                            f.clear(x, f.h - 1)
        f = F('head')['front']                                # 11 x 10
        face = [
            'hhhhhhhhhhh',
            'hhhhhhhhhhh',
            'hhhhhhhhhhh',
            'hhhhhhhhhhh',
            'hhhhhghhhhh',
            'hhgh4hg4hhh',
            'h444454444h',
            'h3444m44443',
            'h34444444 3',
            'hh3333333hh',
        ]
        face[8] = 'h344444443h'
        leg = {'h': None, 'g': (HAIR, 3.5), '3': (SKIN, 3.2), '4': (SKIN, 4.4), '5': (SKIN, 5.2), 'm': C('#5c3a3c')}
        pt.pattern(f, face, leg)
        for x in range(1, 10):                                # the straight fringe ends unevenly over the eyes
            if x % 3 == 1:
                f.set(x, 5, P(HAIR, 2.5))
        f.set(3, 5, C('#0e0c0e'))                             # a dark eye in the gap of the fringe
        f.dl(4, 7, -0.08)                                     # cheek shadow under the nose
        ef = EF('head')['front']
        ef.set(3, 5, '#1a2024')                               # the wet glint (nearly nothing)
        for n in ('right', 'left'):                           # the hat elastic under the chin
            fc = F('head')[n]
            for y in range(4, fc.h):
                xx = (fc.w // 2) if True else 0
                fc.set(xx, y, C('#2b2b2b'))
        fb = F('head')['bottom']
        for x in range(fb.w):
            fb.set(x, fb.h // 3, C('#2b2b2b'))
        # ---------------------------------------------------------------- the yellow hat
        for tag in ('crown', 'dome', 'brim_f', 'brim_b', 'brim_r', 'brim_l'):
            fs = pt.base(tag, HAT, 3.6, amp=0.4, fine=0.3, top=0.8)
            for n, f in fs.items():
                if n == 'bottom':
                    f.fill(P(HAT, 1.6))
        fc = F('crown')
        for n in ('front', 'back', 'left', 'right'):
            f = fc[n]
            for x in range(f.w):
                f.set(x, f.h - 1, P(HAT, 2.2))               # the band
                f.dl(x, 0, 0.04)
        ft = F('dome')['top']
        for (x, y) in ((4, 3), (4, 4)):
            ft.set(x, y, P(HAT, 4.8))                         # the button on top
        for (x, y) in ((1, 1), (2, 1), (6, 5), (7, 6)):
            ft.dl(x, y, -0.1)                                 # grime of twenty years
        for n in ('front', 'back', 'left', 'right'):
            f = F('dome')[n]
            for x in range(f.w):
                f.dl(x, 0, 0.05)
        # ---------------------------------------------------------------- mouth (FOUND), jaw, the eye between the bangs
        f = F('mouth')['front']                               # 5 x 3
        for y in range(f.h):
            for x in range(f.w):
                cx = abs((x + 0.5) / f.w - 0.5) * 2
                if cx < 1.0 - 0.25 * y:
                    f.set(x, y, C('#0a0405') if y > 0 or 0 < x < 4 else C('#3a1c1e'))
        j = F('jaw')['front']                                 # 6 x 2
        pt.pattern(j, ['3mmmm3', '334433'], {'3': (SKIN, 3.2), '4': (SKIN, 4.2), 'm': C('#6a4446')}, jitter=False)
        g = F('glint')['front']                               # 3 x 2: hair parts, an eye looks out
        pt.pattern(g, ['gEg', 'g4g'], {'g': (HAIR, 1.5), 'E': C('#0b0a0b'), '4': (SKIN, 3.6)}, jitter=False)
        g.set(1, 0, C('#1c1714'))
        EF('glint')['front'].set(1, 0, '#c8d6dc')
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.07F);
float jit = glitch * noise(t * 2.1F);
float jit2 = glitch * noise(t * 2.9F + 20.0F);
float look = Mth.clamp(yaw, -1.2F, 1.2F);
float lookP = Mth.clamp(pitch, -0.6F, 0.6F);
this.mouth.visible = false;
this.jaw.visible = false;
this.glint.visible = false;
// the brim droops a little all round
this.brimF.xRot = 0.38F;
this.brimB.xRot = -0.35F;
this.brimR.zRot = 0.38F;
this.brimL.zRot = -0.38F;
boolean squat = pose == HIDE || pose == PEEK;
if (squat) {
    // crouched small on her heels, hunched over her knees
    this.body.y = 20.6F;
    this.rightLeg.y = 20.6F;
    this.leftLeg.y = 20.6F;
    this.rightLeg.xRot = -2.05F;
    this.leftLeg.xRot = -2.05F;
    this.rightLeg.zRot = 0.16F;
    this.leftLeg.zRot = -0.16F;
    this.rightShin.xRot = 2.25F;
    this.leftShin.xRot = 2.25F;
    this.body.xRot = 0.55F + breath * 0.01F;
    this.smock.xRot = -0.35F;
    this.neck.xRot = 0.1F;
    this.head.xRot = 0.12F;
    this.rightArm.xRot = $HD_RX$;
    this.rightArm.zRot = $HD_RZ$;
    this.rightArm.yRot = $HD_RY$;
    this.rightForearm.xRot = $HD_RF$;
    this.rightHand.xRot = $HD_RH$;
    this.leftArm.xRot = $HD_LX$;
    this.leftArm.zRot = $HD_LZ$;
    this.leftArm.yRot = $HD_LY$;
    this.leftForearm.xRot = $HD_LF$;
    this.leftHand.xRot = $HD_LH$;
    if (pose == PEEK) {
        // the hands slide down from the eyes, slowly; then the head comes up and turns to you
        float slide = span(pt, 0.0F, 16.0F);
        float up = span(pt, 10.0F, 22.0F);
        this.rightArm.xRot = Mth.lerp(slide, $HD_RX$, $PK_RX$);
        this.rightArm.zRot = Mth.lerp(slide, $HD_RZ$, $PK_RZ$);
        this.rightArm.yRot = Mth.lerp(slide, $HD_RY$, $PK_RY$);
        this.rightForearm.xRot = Mth.lerp(slide, $HD_RF$, $PK_RF$);
        this.rightHand.xRot = Mth.lerp(slide, $HD_RH$, $PK_RH$);
        this.leftArm.xRot = Mth.lerp(slide, $HD_LX$, $PK_LX$);
        this.leftArm.zRot = Mth.lerp(slide, $HD_LZ$, $PK_LZ$);
        this.leftArm.yRot = Mth.lerp(slide, $HD_LY$, $PK_LY$);
        this.leftForearm.xRot = Mth.lerp(slide, $HD_LF$, $PK_LF$);
        this.leftHand.xRot = Mth.lerp(slide, $HD_LH$, $PK_LH$);
        this.neck.xRot = 0.1F - 0.35F * up;
        this.head.xRot = 0.12F - 0.4F * up + lookP * 0.5F * up;
        this.head.yRot = look * up;
        this.head.zRot = 0.18F * up;
        this.glint.visible = pt > 14.0F;
    }
} else if (pose == RUN) {
    // skipping: step-hop, knees high, arms swinging wide, the head bobbing loosely
    float a = Math.min(1.0F, limbSwingAmount * 1.5F);
    float c = limbSwing * 0.95F;
    float s = Mth.sin(c);
    float hop = Math.abs(Mth.sin(c));
    this.body.y = 17.0F - hop * 1.2F * a;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.rightLeg.xRot = -Math.max(0.0F, s) * 1.25F * a + Math.max(0.0F, -s) * 0.35F * a;
    this.leftLeg.xRot = -Math.max(0.0F, -s) * 1.25F * a + Math.max(0.0F, s) * 0.35F * a;
    this.rightShin.xRot = Math.max(0.0F, s) * 1.4F * a;
    this.leftShin.xRot = Math.max(0.0F, -s) * 1.4F * a;
    this.rightArm.xRot = s * 0.95F * a;
    this.leftArm.xRot = -s * 0.95F * a;
    this.rightArm.zRot = 0.25F;
    this.leftArm.zRot = -0.25F;
    this.rightForearm.xRot = -0.5F;
    this.leftForearm.xRot = -0.5F;
    this.body.xRot = 0.1F;
    this.head.xRot = -0.05F + Mth.sin(c * 2.0F) * 0.08F * a;
    this.head.zRot = s * 0.15F * a;
    this.smock.xRot = -0.1F - hop * 0.15F * a;
} else if (pose == COUNT) {
    // face to the pillar, forearms crossed over her eyes against it; a little bob with every number
    float num = (pt % 16.0F) / 16.0F;
    float bob = Mth.sin(num * 3.14159F) * (pt < 150.0F ? 1.0F : 0.4F);
    this.body.xRot = 0.24F + bob * 0.03F;
    this.neck.xRot = 0.25F;
    this.head.xRot = 0.28F + bob * 0.06F;
    this.head.zRot = Mth.sin(pt * 0.013F) * 0.06F;
    this.rightArm.xRot = $CT_RX$;
    this.rightArm.zRot = $CT_RZ$;
    this.rightArm.yRot = $CT_RY$;
    this.rightForearm.xRot = $CT_RF$;
    this.rightHand.xRot = $CT_RH$;
    this.leftArm.xRot = $CT_LX$;
    this.leftArm.zRot = $CT_LZ$;
    this.leftArm.yRot = $CT_LY$;
    this.leftForearm.xRot = $CT_LF$;
    this.leftHand.xRot = $CT_LH$;
    this.rightLeg.zRot = 0.03F;
    this.leftLeg.zRot = -0.03F;
} else if (pose == FOUND) {
    // springs up and points straight at you, mouth wide: "mi-tsu-ke-ta"
    float pop = spring(pt, 1.1F, 0.4F);
    float aim = Math.min(1.0F, pt / 3.0F);
    this.body.xRot = -0.08F;
    this.body.y = 17.0F - 0.6F * Math.max(0.0F, 1.0F - pt / 4.0F);
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.rightArm.xRot = (-1.57F + lookP) * aim;
    this.rightArm.yRot = look * 0.8F * aim;
    this.rightArm.zRot = 0.05F;
    this.rightForearm.xRot = 0.0F;
    this.rightHand.xRot = 0.0F;
    this.leftArm.xRot = 0.2F;
    this.leftArm.zRot = -0.3F * pop;
    this.head.yRot = look;
    this.head.xRot = lookP * 0.8F - 0.1F;
    this.head.zRot = 0.3F * pop + Mth.sin(t * 0.6F) * 0.04F;
    float o = Math.min(1.0F, pt / 4.0F) * (0.95F + Mth.sin(t * 0.5F) * 0.05F);
    this.mouth.visible = true;
    this.jaw.visible = true;
    this.mouth.yScale = Math.max(0.02F, o);
    this.jaw.y = this.jaw.y + 1.5F * o;
    this.glint.visible = true;
    blend = 2.0F;
} else if (pose == STAND) {
    // hands clasped behind her back, head tilted; the tilt flips to the other side now and then, too fast
    float flip = Mth.sin(t * 0.021F) > 0.0F ? 1.0F : -1.0F;
    this.head.zRot = 0.42F * flip;
    this.head.yRot = look * 0.6F;
    this.head.xRot = lookP * 0.4F + 0.08F;
    this.body.zRot = -0.03F * flip;
    this.rightArm.xRot = $ST_RX$;
    this.rightArm.zRot = $ST_RZ$;
    this.rightArm.yRot = $ST_RY$;
    this.rightForearm.xRot = $ST_RF$;
    this.leftArm.xRot = $ST_LX$;
    this.leftArm.zRot = $ST_LZ$;
    this.leftArm.yRot = $ST_LY$;
    this.leftForearm.xRot = $ST_LF$;
} else {
    // GIGGLE: a hand over her mouth, the shoulders shaking in little bursts
    float burst = Math.max(0.0F, Mth.sin(t * 0.17F));
    float shake = Mth.sin(t * 2.6F) * burst;
    this.body.y = 17.0F + shake * 0.25F;
    this.rightLeg.y = this.body.y;
    this.leftLeg.y = this.body.y;
    this.body.xRot = 0.16F + burst * 0.06F;
    this.body.zRot = shake * 0.03F;
    this.head.xRot = 0.2F + shake * 0.06F;
    this.head.zRot = 0.2F + shake * 0.05F;
    this.head.yRot = look * 0.4F;
    this.rightArm.xRot = $GG_RX$;
    this.rightArm.zRot = $GG_RZ$;
    this.rightArm.yRot = $GG_RY$;
    this.rightForearm.xRot = $GG_RF$;
    this.rightHand.xRot = $GG_RH$;
    this.leftArm.xRot = 0.05F + shake * 0.05F;
    this.leftArm.zRot = $GG_LZ$;
    this.leftForearm.xRot = $GG_LF$;
}
this.head.zRot = this.head.zRot + jit * 0.4F;
this.head.yRot = this.head.yRot + jit2 * 0.25F;
'''


def solve_ik():
    """Solves the hand placements (python3 -c 'import ent_child; ent_child.solve_ik()')."""
    import numpy as np
    from ent_ik import solve, point, write_consts, mirror
    Child.consts = {k: 0.0 for k in Child.consts} or {}
    spec = Child.__new__(Child)
    from ent_model import Part, pack
    spec.root = Part('root')
    spec.build()
    P = {p.name: p for p in spec.root.walk()}
    vary = lambda pre: [(f'{pre}_arm', 'xRot'), (f'{pre}_arm', 'zRot'), (f'{pre}_arm', 'yRot'),
                        (f'{pre}_forearm', 'xRot'), (f'{pre}_hand', 'xRot')]
    bnd = [(-3.3, 1.2), (-1.6, 1.6), (-1.4, 1.4), (-2.7, 0.2), (-1.6, 1.6)]
    out = {'GG_LZ': -0.12, 'GG_LF': -0.4}

    def posed(fixed):
        for q in P.values():
            q.reset()
        for pn, attrs in fixed.items():
            for a, v in attrs.items():
                setattr(P[pn], a, v)

    def put(prefix, r):
        for k, v in r.items():
            part, attr = k.split('.')
            s = 'R' if part.startswith('right') else 'L'
            key = {'arm.xRot': 'X', 'arm.zRot': 'Z', 'arm.yRot': 'Y', 'forearm.xRot': 'F', 'hand.xRot': 'H'}[
                part.split('_', 1)[1] + '.' + attr]
            out[f'{prefix}_{s}{key}'] = v
    # HIDE: squatting, palms over the eyes (solve the right, mirror the left)
    fx = {'body': {'y': 20.6, 'xRot': 0.55}, 'neck': {'xRot': 0.1}, 'head': {'xRot': 0.12}}
    posed(fx)
    eye = point(P['head'], (-1.2, -2.6, -2.5))
    tgt = point(P['head'], (-1.2, -2.6, -3.3))
    tip = point(P['head'], (-0.7, -3.6, -3.1))
    r = solve(spec, 'right_hand', (0, 0.75, -0.5), tgt, vary('right'), fixed=fx, bounds=bnd, init=[-2.0, 0, 0, -1.5, 0],
              also=[('right_hand', (0, 1.5, -0.5), tip, 0.6)])
    put('HD', r)
    put('HD', mirror(r))
    # PEEK end: the hands slid down onto her knees
    posed({'body': {'y': 20.6, 'xRot': 0.55}, 'right_leg': {'y': 20.6, 'xRot': -2.05, 'zRot': 0.16},
           'left_leg': {'y': 20.6, 'xRot': -2.05, 'zRot': -0.16}, 'right_shin': {'xRot': 2.25}, 'left_shin': {'xRot': 2.25}})
    knee = point(P['right_leg'], (0, 3.0, -1.0))
    r = solve(spec, 'right_hand', (0, 0.75, 0.5), knee + np.array([0, -0.3, -0.2]), vary('right'), fixed=fx, bounds=bnd,
              init=[-1.0, 0, 0, -1.0, 0])
    put('PK', r)
    put('PK', mirror(r))
    # COUNT: leaning on the pillar, forearms crossed over her eyes
    fx = {'body': {'xRot': 0.4}, 'neck': {'xRot': 0.2}, 'head': {'xRot': 0.25}}
    posed(fx)
    eyeR = point(P['head'], (-1.2, -2.6, -2.7))
    eyeL = point(P['head'], (1.2, -2.6, -2.7))
    r = solve(spec, 'right_hand', (0, 0.75, 0), eyeL + np.array([0.2, -0.2, -0.9]), vary('right'), fixed=fx, bounds=bnd,
              init=[-2.0, 0, 0, -1.5, 0], also=[('right_forearm', (0, 0.5, 0), eyeR + np.array([-1.0, 0.5, -0.9]), 0.5)])
    put('CT', r)
    r = solve(spec, 'left_hand', (0, 0.75, 0), eyeR + np.array([-0.2, 0.4, -1.4]), vary('left'), fixed=fx, bounds=bnd,
              init=[-2.0, 0, 0, -1.5, 0], also=[('left_forearm', (0, 0.5, 0), eyeL + np.array([1.0, 1.0, -1.4]), 0.5)])
    put('CT', r)
    # GIGGLE: the right palm over her mouth
    fx = {'body': {'xRot': 0.16}, 'head': {'xRot': 0.2, 'zRot': 0.2}}
    posed(fx)
    m = point(P['head'], (0, -1.3, -3.2))
    r = solve(spec, 'right_hand', (0, 0.75, -0.5), m, vary('right'), fixed=fx, bounds=bnd, init=[-2.0, 0, 0, -1.5, 0],
              also=[('right_hand', (0, 1.5, -0.5), point(P['head'], (0.8, -1.9, -3.1)), 0.4)])
    put('GG', r)
    # STAND: hands clasped behind her back
    posed({})
    bnd2 = [(0.3, 1.1), (-0.9, 0.1), (-0.6, 0.6), (-1.6, 0.0), (-1.6, 1.6)]
    r = solve(spec, 'right_hand', (0, 0.75, 0), (-0.6, 16.6, 2.5), vary('right'), bounds=bnd2, init=[0.6, -0.4, 0.0, -0.6, 0],
              also=[('right_forearm', (0, 0, 0), (-3.0, 14.6, 1.9), 0.4)])
    put('ST', {k: v for k, v in r.items() if not k.endswith('hand.xRot')})
    put('ST', mirror({k: v for k, v in r.items() if not k.endswith('hand.xRot')}))
    write_consts(__file__, 'Child', out)
    print('solved', len(out), 'constants')


# --- solved constants (ent_ik), do not edit by hand
Child.consts = {
    'CT_LF': -0.3328,
    'CT_LH': -0.1315,
    'CT_LX': -2.0731,
    'CT_LY': -0.2643,
    'CT_LZ': -1.3742,
    'CT_RF': -0.3811,
    'CT_RH': -0.1458,
    'CT_RX': -2.1170,
    'CT_RY': 0.3440,
    'CT_RZ': 1.3459,
    'GG_LF': -0.4000,
    'GG_LZ': -0.1200,
    'GG_RF': -0.5208,
    'GG_RH': -0.4527,
    'GG_RX': -1.9862,
    'GG_RY': -0.1876,
    'GG_RZ': 0.9755,
    'HD_LF': -0.2818,
    'HD_LH': -0.5935,
    'HD_LX': -2.0066,
    'HD_LY': 0.2398,
    'HD_LZ': -0.3915,
    'HD_RF': -0.2818,
    'HD_RH': -0.5935,
    'HD_RX': -2.0066,
    'HD_RY': -0.2398,
    'HD_RZ': 0.3915,
    'PK_LF': -2.1517,
    'PK_LH': -0.5552,
    'PK_LX': 0.1471,
    'PK_LY': 1.2994,
    'PK_LZ': -0.0052,
    'PK_RF': -2.1517,
    'PK_RH': -0.5552,
    'PK_RX': 0.1471,
    'PK_RY': -1.2994,
    'PK_RZ': 0.0052,
    'ST_LF': -0.6357,
    'ST_LX': 0.9263,
    'ST_LY': 0.5589,
    'ST_LZ': 0.9000,
    'ST_RF': -0.6357,
    'ST_RX': 0.9263,
    'ST_RY': -0.5589,
    'ST_RZ': -0.9000,
}
# --- end solved constants


SPEC = Child


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.dining()
    pil = SC.pillar_near()
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(5, 60, yaw_view=yv, scene=sc, dist=4.0)))
    ims.append(('tilt flips', b.view(5, 150, yaw_view=10, scene=sc, dist=4.0)))
    ims.append(('close', b.view(5, 60, yaw_view=-10, pitch_view=0, size=(190, 190), ortho=0.33, center_y=0.85, scene=sc)))
    rows.append(('STAND (5): hands behind, head tilted (flips side now and then)', ims))
    run = lambda tt: (tt * 0.6, 0.9)                            # noqa: E731
    ims = []
    for tt in (0, 2, 4, 6, 8):
        ims.append((f'side t{tt}', b.view([(0, 0)], 100 + tt, yaw_view=90, limb=run, scene=sc)))
    ims.append(('3/4', b.view([(0, 0)], 103, yaw_view=-40, limb=run, scene=sc)))
    rows.append(('RUN (0): skipping', ims))
    ims = []
    for (yv, lab, tt) in ((180, 'from behind', 40), (130, '3/4 behind', 48), (90, 'side', 40), (40, 'pillar side', 40)):
        ims.append((lab, b.view(1, tt, yaw_view=yv, pitch_view=10, scene=pil)))
    rows.append(('COUNT (1): face to the pillar, forearms over her eyes', ims))
    ims = []
    for (yv, lab) in ((0, 'front'), (40, '3/4'), (90, 'side')):
        ims.append((f'HIDE {lab}', b.view(2, 40, yaw_view=yv, pitch_view=15, scene=sc)))
    for ptv in (0, 6, 12, 18, 30):
        ims.append((f'PEEK pt {ptv}', b.view([(0, 2), (100, 3)], 100 + ptv, yaw_view=20, pitch_view=12,
                                             head=lambda tt: (25.0, -10.0), scene=sc)))
    rows.append(('HIDE (2) -> PEEK (3): the hands slide down, the head comes up, an eye between the bangs', ims))
    ims = []
    for ptv in (0, 1, 2, 4, 10):
        ims.append((f'pt {ptv}', b.view([(0, 2), (100, 4)], 100 + ptv, yaw_view=15, head=lambda tt: (15.0, -15.0), scene=sc)))
    ims.append(('close', b.view([(0, 2), (100, 4)], 110, yaw_view=10, pitch_view=0, head=lambda tt: (10.0, -12.0),
                                size=(190, 190), ortho=0.38, center_y=0.8, scene=sc)))
    rows.append(('FOUND (4): springs up, points at you, mouth wide (from HIDE: blend 2 ticks)', ims))
    ims = []
    for tt in (0, 3, 6, 9):
        ims.append((f't {tt}', b.view(6, 40 + tt, yaw_view=10, scene=sc)))
    ims.append(('side', b.view(6, 40, yaw_view=80, scene=sc)))
    rows.append(('GIGGLE (6): hand over her mouth, shoulders shaking', ims))
    ims = []
    ims.append(('finder: hiding under a table', b.view(2, 40, size=(300, 240), eye=(0.6, 1.62, 3.4), target=(0, 0.45, 0),
                                                       finder=True, scene=SC.dining(table=True), fov=50)))
    ims.append(('finder: PEEK', b.view([(0, 2), (100, 3)], 130, size=(300, 240), eye=(0.6, 1.62, 3.4),
                                       target=(0, 0.5, 0), finder=True, head=lambda tt: (10.0, -25.0), scene=sc, fov=50)))
    ims.append(('finder: FOUND', b.view([(0, 2), (100, 4)], 110, size=(300, 240), eye=(0.3, 1.62, 2.2),
                                        target=(0, 0.75, 0), finder=True, head=lambda tt: (8.0, -30.0), scene=sc, fov=55)))
    rows.append(('through the finder (she is invisible to the naked eye)', ims))
    return rows
