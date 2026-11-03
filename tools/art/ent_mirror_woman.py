"""鏡の女 the woman in the mirror (白石 玲子, 31) - 0.6 x 1.8. A regular who collapsed in the women's restroom in 2015 and
was found in the morning with the tap still running. She is on the other side of the mirror: a white blouse and a
dark skirt, soaked through; long wet black hair hanging over her face with one eye showing; grey skin, blue lips;
water running off her fingers. Barefoot. Poses: 0 BOWED, 1 STARE, 2 PRESS, 3 SCREAM, 4 STAND."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

SKIN = ['#3b4247', '#525a5f', '#6b7376', '#848b8c', '#9da2a1', '#b3b7b4', '#c6c9c5']
HAIR = ['#030304', '#07080a', '#0d0e11', '#14161a', '#1d2025', '#2a2e35', '#3d434c']
BLOUSE = ['#5c6366', '#747c7f', '#8d9496', '#a5abab', '#bcc0be', '#d0d3cf', '#e0e2dd']
SKIRT = ['#08090d', '#0e1016', '#14171f', '#1b1f2a', '#242a37', '#2f3646']
LIPS = ['#1f2533', '#2c3448', '#3b465e', '#4e5a74']


class MirrorWoman(Spec):
    id = 'mirror_woman'
    java_name = 'MirrorWomanModel'
    module = 'ent_mirror_woman'
    tex_w = 64
    tex_h = 64
    height = 1.8
    poses = [('BOWED', 0), ('STARE', 1), ('PRESS', 2), ('SCREAM', 3), ('STAND', 4)]
    doc = """鏡の女 (the woman in the mirror, 白石 玲子): she lives on the other side of the women's restroom mirror. A white
    blouse and a dark skirt soaked through, long wet black hair over her face with one eye showing, grey skin, blue
    lips, barefoot; water runs off her fingers and the ends of her hair.
    Pose ids: 0 BOWED (bent over the dead sink, her face in her hands, sobbing in hitches), 1 STARE (upright,
    still, the head following you a moment late), 2 PRESS (leans in, both palms flat on the glass about 7 px in front
    of her - the mirror pane should be ~0.45 blocks ahead of her position - fingers spread; the palms slowly drag
    down and her face nears the glass), 3 SCREAM (head thrown back, the mouth stretched far too wide, rigid
    clawed hands, shaking; the hair falls away from her face), 4 STAND."""

    def build(self):
        r = self.root
        body = Part('body', (0, 10.5, 0))
        body.cube(Cube((-3.25, -7.5, -1.75), (6.5, 7.5, 3.5), 'blouse', tag='torso',
                       faces=('top', 'right', 'front', 'left', 'back')))
        skirt = Part('skirt', (0, -0.5, 0), cubes=[Cube((-3.75, 0, -2.0), (7.5, 7.5, 4), 'skirt', tag='skirt')])
        neck = Part('neck', (0, -7.5, 0), cubes=[Cube((-1, -1.5, -1), (2, 1.5, 2), 'skin', tag='neck',
                                                      faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1.5, 0), cubes=[Cube((-3, -6.5, -2.75), (6, 6.5, 5.5), 'head', tag='head')])
        hf = Part('hair_front', (0, -6.4, -2.9), cubes=[Cube((-3.25, 0, 0), (6.5, 6.5, 0), 'hairf', tag='hair_front',
                                                             faces=('front',))])
        hf2 = Part('hair_front2', (0, 6.5, 0), cubes=[Cube((-3.0, 0, 0), (6, 5, 0), 'hairf', tag='hair_front2',
                                                           faces=('front',))])
        hf.add(hf2)
        hb = Part('hair_back', (0, -5.5, 2.85), cubes=[Cube((-3.25, 0, 0), (6.5, 6, 0.5), 'hair', tag='hair_back',
                                                            faces=('back', 'right', 'left', 'front'))])
        hb2 = Part('hair_back2', (0, 6, 0), cubes=[Cube((-3.0, 0, 0), (6, 6.5, 0.5), 'hair', tag='hair_back2',
                                                        faces=('back', 'right', 'left', 'front', 'bottom'))])
        hb.add(hb2)
        hr = Part('hair_r', (-3.15, -5.0, -0.5), cubes=[Cube((-0.25, 0, -1.25), (0.5, 9, 2.5), 'hair', tag='hair_r',
                                                             faces=('right', 'left', 'front', 'back', 'bottom'))])
        hl = Part('hair_l', (3.15, -5.0, -0.5), cubes=[Cube((-0.25, 0, -1.25), (0.5, 9.5, 2.5), 'hair', tag='hair_l',
                                                            faces=('right', 'left', 'front', 'back', 'bottom'))])
        mouth = card('mouth', (0, -1.5, -2.83), (-1.5, 0, 0), (3, 2, 0), 'mouth')
        jaw = card('jaw', (0, -1.0, -2.88), (-2.0, 0, 0), (4, 1, 0), 'jaw')
        drip_hair = Part('drip_hair', (0.6, 5.0, 0.0))
        drip_hair.add(Part('drip_hair_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), 'drop', tag='drip_hair')]))
        hf2.add(drip_hair)
        head.add(hf, hb, hr, hl, mouth, jaw)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.35, -7.0, 0), cubes=[Cube((-0.75, -0.5, -0.75), (1.5, 5, 1.5), 'blouse', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 4.5, 0), cubes=[Cube((-0.75, 0, -0.75), (1.5, 4.5, 1.5), 'blouse', tag=f'{pre}_fore',
                                                                     faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'{pre}_hand', (0, 4.5, 0), cubes=[Cube((-1.0, 0, -0.25), (2, 2, 0.5), 'skin', tag=f'{pre}_palm',
                                                                  faces=('right', 'front', 'left', 'back', 'bottom'))])
            for i, fx in enumerate((-0.75, -0.25, 0.25, 0.75)):
                ln = (2.0, 2.5, 2.5, 2.0)[i]
                hand.add(Part(f'{pre}_f{i + 1}', (fx, 2.0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, ln, 0.5), 'skin', tag=f'{pre}_f{i + 1}',
                                                                        faces=('right', 'front', 'left', 'back', 'bottom'))]))
            hand.add(Part(f'{pre}_thumb', (-side * 1.0, 0.6, 0), rot=(0, 0, side * 0.5),
                          cubes=[Cube((-0.25, 0, -0.25), (0.5, 1.5, 0.5), 'skin', tag=f'{pre}_thumb')]))
            drip = Part(f'drip_{pre[0]}', (0, 2.5, 0))
            drip.add(Part(f'drip_{pre[0]}_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), 'drop', tag=f'drip_{pre[0]}')]))
            hand.children[2].add(drip)
            fore.add(hand)
            arm.add(fore)
            arms[pre] = arm
        body.add(skirt, neck, arms['right'], arms['left'])
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.3, 10.5, 0), cubes=[Cube((-1.25, 0, -1.25), (2.5, 6.5, 2.5), 'skin', tag=f'{pre}_thigh',
                                                                         faces=('right', 'front', 'left', 'back', 'bottom'))])
            shin = Part(f'{pre}_shin', (0, 6.5, 0), cubes=[Cube((-1, 0, -1), (2, 6, 2), 'skin', tag=f'{pre}_shin',
                                                               faces=('right', 'front', 'left', 'back')),
                                                          Cube((-1, 6, -2.5), (2, 1, 3.5), 'skin', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        r.add(body, *legs)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 6262)
        F, EF, nz = pt.F, pt.EF, pt.nz

        def wet_skin(Pp, nrm, n):
            streak = np.sin(Pp[..., 0] * 4.0 + Pp[..., 2] * 3.0 + pt.n3(Pp * 0.8) * 2.0)
            return np.where(streak > 0.85, 0.8, 0.0)
        skin_tags = ['neck', 'right_palm', 'left_palm', 'right_thigh', 'left_thigh', 'right_shin', 'left_shin',
                     'right_foot', 'left_foot', 'right_thumb', 'left_thumb'] + \
                    [f'{s}_f{i}' for s in ('right', 'left') for i in (1, 2, 3, 4)]
        for tag in skin_tags:
            pt.base(tag, SKIN, 4.0, amp=0.35, fine=0.3, top=0.5, fn=wet_skin)
        for s in ('right', 'left'):                           # nails bluish at the finger ends; the palm lighter
            for i in (1, 2, 3, 4):
                for n, f in F(f'{s}_f{i}').items():
                    if n in ('front', 'back', 'left', 'right'):
                        f.set(0, f.h - 1, P(LIPS, 3))
                        f.dl(0, f.h // 2, -0.06)
            f = F(f'{s}_palm')['front']
            for y in range(f.h):
                for x in range(f.w):
                    f.dl(x, y, 0.04)
            f.dl(1, 2, -0.08)
            f.dl(2, 1, -0.06)
        for pre in ('right', 'left'):                         # dirty soles, toes
            fs = F(f'{pre}_foot')
            fs['bottom'].fill(P(SKIN, 1))
            f = fs['front']
            for x in range(f.w):
                f.dl(x, 1, -0.08)
        # ---------------------------------------------------------------- blouse: soaked, clinging, grey with skin under
        def soaked(Pp, nrm, n):
            cling = pt.n3c(Pp * np.array([1.6, 0.5, 1.6]) + 4.0)        # wet folds run downwards
            return np.where(cling > 0.25, -0.9 * np.clip(cling - 0.25, 0, 0.6), 0.0) - 0.35 * np.clip((Pp[..., 1] - 2.0) / 9.0, 0, 1)
        for tag in ('torso', 'right_upper', 'left_upper', 'right_fore', 'left_fore'):
            pt.base(tag, BLOUSE, 4.6, amp=0.4, fine=0.35, top=0.5, fn=soaked)
        f = F('torso')['front']                               # 13 x 15: collar, buttons, the wet cling
        for y in range(1, f.h):
            if y % 3 == 1:
                f.set(6, y, P(BLOUSE, 2))
            f.dl(7, y, -0.04)
        for (x, y) in ((4, 0), (5, 0), (5, 1), (7, 0), (8, 0), (7, 1)):
            f.set(x, y, P(BLOUSE, 5.5))
        f.set(6, 0, P(SKIN, 2.5))
        for (x, y) in ((2, 3), (3, 3), (9, 3), (10, 3), (3, 4), (9, 4)):
            f.set(x, y, P(SKIN, 3.0))                         # skin through the wet cloth
        for pre in ('right', 'left'):
            for n, fc in F(f'{pre}_fore').items():
                if n in ('front', 'back', 'left', 'right'):
                    for x in range(fc.w):
                        fc.set(x, fc.h - 1, P(BLOUSE, 3.5))  # cuff
        # ---------------------------------------------------------------- skirt: dark, wet
        def skirtfn(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 1.6 + Pp[..., 2] * 1.6 + pt.n3(Pp) * 1.2) * 0.6
        fs = pt.base('skirt', SKIRT, 2.4, amp=0.4, fine=0.3, top=0.4, fn=skirtfn)
        for n in ('front', 'back', 'left', 'right'):
            f = fs[n]
            for x in range(f.w):
                f.set(x, f.h - 1, P(SKIRT, 0.5))
                if (x * 7) % 5 == 0:
                    f.dl(x, f.h - 2, 0.07)                  # water beading at the hem
        # ---------------------------------------------------------------- head: hair all round, the face mostly hidden
        def hairfn(Pp, nrm, n):
            return np.sin(Pp[..., 0] * 5.0 + Pp[..., 2] * 4.0) * 0.6
        fs = pt.base('head', HAIR, 2.0, amp=0.45, fine=0.3, top=0.6, fn=hairfn)
        f = fs['front']                                       # 12 x 13: grey face, one eye (her left), blue lips
        face = [
            'hhhhhhhhhhhh',
            'hhhhhhhhhhhh',
            'hh34444443hh',
            'h3444444443h',
            'h3##3443##3h',
            'h3WIW33WIW3h',
            'h3#R#33#R#3h',
            'h33333443333',
            'h3333444333h',
            'h333#44#333h',
            'h33mLLLLm33h',
            'hh33llll33hh',
            'hhh333333hhh',
        ]
        leg = {'h': None, '3': (SKIN, 3.0), '4': (SKIN, 3.8), '#': (SKIN, 1.5), 'W': C('#b9b8ae'), 'I': C('#141313'),
               'R': (SKIN, 2.0), 'm': (LIPS, 0.5), 'L': (LIPS, 2.0), 'l': (LIPS, 1.2)}
        pt.pattern(f, face, leg)
        f.set(8, 5, C('#b38f86'))                             # bloodshot corner of the eye that shows
        for (x0, y0) in ((1, 2), (3, 2), (4, 3)):             # wet strands plastered over her right eye
            for k in range(0, 9):
                x, y = x0 + k // 3, y0 + k
                if 0 <= x < 6 and y < 12:
                    f.set(x, y, P(HAIR, 1.5 + (k % 2)))
        ef = EF('head')['front']
        ef.set(8, 5, '#3e3a38')                               # the one eye: a wet glint
        ef.set(9, 5, '#9aa3a6')
        # ---------------------------------------------------------------- the curtain of wet hair over the face
        def strands(face_, seed, gap=None, ragged=0):
            rng = np.random.default_rng(seed)
            w, h = face_.w, face_.h
            cols = rng.normal(0, 0.6, w)
            wet = rng.random(w) < 0.25
            for x in range(w):
                ln = h - (rng.integers(0, ragged + 1) if ragged else 0)
                for y in range(h):
                    if y >= ln:
                        face_.clear(x, y)
                        continue
                    if gap is not None and gap(x, y):
                        face_.clear(x, y)
                        continue
                    v = 2.0 + cols[x] + (1.6 if wet[x] and (y + x) % 4 else 0.0) - (0.6 if (x + y) % 5 == 0 else 0)
                    face_.set(x, y, P(HAIR, v))
        # the parting: her left eye (texture x 7..9 at face rows 4..6 -> curtain rows 4..7) shows between strands
        strands(F('hair_front')['front'], 11, gap=lambda x, y: (7 <= x <= 9 and 4 <= y <= 7) or (x == 10 and 5 <= y <= 6)
                or (x in (2, 5) and y >= 9))
        strands(F('hair_front2')['front'], 12, gap=lambda x, y: x in (1, 4, 8) and y > 3, ragged=4)
        for tag in ('hair_back', 'hair_back2', 'hair_r', 'hair_l'):
            fs = pt.base(tag, HAIR, 2.0, amp=0.4, fine=0.3, top=0.3, fn=hairfn)
            if tag in ('hair_back2', 'hair_r', 'hair_l'):
                rng = np.random.default_rng(len(tag))
                for n in ('back', 'front', 'right', 'left'):
                    fc = fs[n]
                    for x in range(fc.w):
                        cut = int(rng.integers(0, 4))
                        for y in range(fc.h - cut, fc.h):
                            fc.clear(x, y)
        # ---------------------------------------------------------------- SCREAM mouth (stretched far too wide), jaw
        f = F('mouth')['front']                               # 6 x 4
        for y in range(f.h):
            for x in range(f.w):
                cx = abs((x + 0.5) / f.w - 0.5) * 2
                if cx < 1.0 - 0.12 * y:
                    f.set(x, y, C('#050506') if 0 < x < f.w - 1 or y > 0 else P(LIPS, 1))
        for x in range(f.w):
            f.set(x, 0, P(LIPS, 1.5) if x in (0, f.w - 1) else C('#0d0e12'))
        j = F('jaw')['front']                                 # 8 x 2
        pt.pattern(j, ['3mllllm3', '33333333'], {'3': (SKIN, 3.0), 'm': (LIPS, 0.5), 'l': (LIPS, 1.2)}, jitter=False)
        for tag in ('drip_hair', 'drip_l', 'drip_r'):
            for n, fc in F(tag).items():
                c = C('#a9c4cf')
                c[3] = 230
                fc.fill(c)
            for n, fc in EF(tag).items():
                fc.fill(C('#1a262c'))
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
float breath = Mth.sin(t * 0.05F);
float jit = glitch * noise(t * 2.0F);
float jit2 = glitch * noise(t * 2.6F + 70.0F);
this.mouth.visible = false;
this.jaw.visible = false;
float claw = 0.15F;
float spread = 0.06F;
if (pose == BOWED) {
    // bent over the dead sink, her face in her hands, sobbing in hitches
    float sob = Math.max(0.0F, noise(t * 0.45F)) * (0.6F + 0.4F * Math.max(0.0F, Mth.sin(t * 0.09F)));
    this.body.xRot = 0.85F + sob * 0.05F;
    this.body.y = 10.5F + sob * 0.25F;
    this.skirt.xRot = -0.7F;
    this.neck.xRot = 0.25F + sob * 0.08F;
    this.head.xRot = 0.35F + sob * 0.12F;
    this.rightArm.xRot = $BW_RX$;
    this.rightArm.zRot = $BW_RZ$ + sob * 0.06F;
    this.rightArm.yRot = $BW_RY$;
    this.rightForearm.xRot = $BW_RF$;
    this.rightHand.xRot = $BW_RH$;
    this.leftArm.xRot = $BW_LX$;
    this.leftArm.zRot = $BW_LZ$ - sob * 0.06F;
    this.leftArm.yRot = $BW_LY$;
    this.leftForearm.xRot = $BW_LF$;
    this.leftHand.xRot = $BW_LH$;
    claw = 0.35F;
} else if (pose == STARE) {
    // upright and still, the head following you a moment late; now and then a twitch
    float hy = follow(mem, 0, Mth.clamp(yaw, -1.0F, 1.0F), 0.06F, t);
    this.head.yRot = hy;
    this.head.xRot = 0.14F + Mth.clamp(pitch, -0.5F, 0.5F) * 0.6F;
    this.head.zRot = 0.06F + twitch(t, 89.0F, 31.0F) * 0.25F;
    this.neck.xRot = 0.12F;
    this.body.xRot = 0.03F + breath * 0.006F;
    this.rightArm.zRot = 0.04F;
    this.leftArm.zRot = -0.04F;
    this.rightForearm.xRot = -0.05F;
    this.leftForearm.xRot = -0.05F;
} else if (pose == PRESS) {
    // leaning in, both palms flat on the glass, fingers spread; they drag slowly down, the face comes closer
    float in = easeOut(pt / 12.0F);
    float drag = span(pt, 14.0F, 70.0F);
    this.body.xRot = (0.16F + 0.08F * drag) * in;
    this.neck.xRot = 0.1F * in;
    this.head.xRot = (0.08F + 0.12F * drag) * in;
    this.head.zRot = 0.18F * drag;
    this.rightArm.xRot = Mth.lerp(drag, $PR_RX$, $PD_RX$) * in;
    this.rightArm.zRot = Mth.lerp(drag, $PR_RZ$, $PD_RZ$) * in;
    this.rightArm.yRot = Mth.lerp(drag, $PR_RY$, $PD_RY$) * in;
    this.rightForearm.xRot = Mth.lerp(drag, $PR_RF$, $PD_RF$) * in;
    this.rightHand.xRot = Mth.lerp(drag, $PR_RH$, $PD_RH$) * in;
    this.leftArm.xRot = Mth.lerp(drag, $PR_LX$, $PD_LX$) * in;
    this.leftArm.zRot = Mth.lerp(drag, $PR_LZ$, $PD_LZ$) * in;
    this.leftArm.yRot = Mth.lerp(drag, $PR_LY$, $PD_LY$) * in;
    this.leftForearm.xRot = Mth.lerp(drag, $PR_LF$, $PD_LF$) * in;
    this.leftHand.xRot = Mth.lerp(drag, $PR_LH$, $PD_LH$) * in;
    claw = -0.05F;
    spread = 0.22F * in;
} else if (pose == SCREAM) {
    // head thrown back, the mouth stretched far too wide, arms rigid with the fingers clawed, shaking
    float go = easeOut(pt / 6.0F);
    float shake = noise(t * 3.1F) * 0.06F + glitch * noise(t * 5.0F) * 0.1F;
    this.body.xRot = -0.12F * go + shake * 0.5F;
    this.neck.xRot = -0.35F * go;
    this.head.xRot = -0.6F * go + shake;
    this.head.zRot = shake * 1.5F;
    this.rightArm.xRot = 0.15F * go;
    this.rightArm.zRot = 0.5F * go + shake;
    this.leftArm.xRot = 0.15F * go;
    this.leftArm.zRot = -0.5F * go - shake;
    this.rightForearm.xRot = -0.3F * go;
    this.leftForearm.xRot = -0.3F * go;
    this.rightHand.xRot = -0.4F * go;
    this.leftHand.xRot = -0.4F * go;
    float o = go * (1.0F + Mth.sin(t * 0.9F) * 0.06F);
    this.mouth.visible = true;
    this.jaw.visible = true;
    this.mouth.yScale = Math.max(0.02F, o * 1.9F);
    this.mouth.xScale = 1.0F + 0.35F * o;
    this.jaw.y = this.jaw.y + 3.6F * o;
    this.jaw.xScale = 1.0F + 0.2F * o;
    claw = 1.1F * go;
    spread = 0.3F * go;
    blend = 2.0F;
} else {
    // STAND: head bowed, dripping, swaying a little
    this.body.xRot = 0.06F + breath * 0.008F;
    this.body.zRot = Mth.sin(t * 0.019F) * 0.02F;
    this.neck.xRot = 0.2F;
    this.head.xRot = 0.32F;
    this.head.zRot = Mth.sin(t * 0.023F) * 0.05F;
    this.rightArm.zRot = 0.05F;
    this.leftArm.zRot = -0.05F;
}
// fingers: curl and spread per pose
this.rightF1.xRot = claw;
this.rightF2.xRot = claw * 1.1F;
this.rightF3.xRot = claw * 1.05F;
this.rightF4.xRot = claw * 0.9F;
this.leftF1.xRot = claw;
this.leftF2.xRot = claw * 1.1F;
this.leftF3.xRot = claw * 1.05F;
this.leftF4.xRot = claw * 0.9F;
this.rightF1.zRot = spread * 1.5F;
this.rightF2.zRot = spread * 0.5F;
this.rightF3.zRot = -spread * 0.5F;
this.rightF4.zRot = -spread * 1.5F;
this.leftF1.zRot = spread * 1.5F;
this.leftF2.zRot = spread * 0.5F;
this.leftF3.zRot = -spread * 0.5F;
this.leftF4.zRot = -spread * 1.5F;
this.head.zRot = this.head.zRot + jit * 0.35F;
this.head.xRot = this.head.xRot + jit2 * 0.25F;
'''
    post = '''
// wet hair hangs straight down from wherever her head is; the lower curtain and the long back hair swing late
level(this.hairFront2, this.body, this.neck, this.head, this.hairFront);
level(this.hairBack2, this.body, this.neck, this.head, this.hairBack);
level(this.hairR, this.body, this.neck, this.head);
level(this.hairL, this.body, this.neck, this.head);
float sw = Mth.sin(t * 0.06F) * 0.04F;
this.hairFront2.xRot = this.hairFront2.xRot + sw;
this.hairBack2.xRot = this.hairBack2.xRot - sw * 0.5F;
this.hairR.zRot = this.hairR.zRot + 0.06F;
this.hairL.zRot = this.hairL.zRot - 0.06F;
// the upper curtain stays on the face (and swings off it when the head goes back)
float hx = this.body.xRot + this.neck.xRot + this.head.xRot;
this.hairFront.xRot = Math.max(0.0F, -hx - 0.15F) * 0.9F;
// water runs off her fingers and the ends of her hair
level(this.dripL, this.body, this.leftArm, this.leftForearm, this.leftHand, this.leftF3);
level(this.dripR, this.body, this.rightArm, this.rightForearm, this.rightHand, this.rightF3);
level(this.dripHair, this.body, this.neck, this.head, this.hairFront, this.hairFront2);
float dl = (t + 5.0F) % 17.0F;
float dr = (t + 11.0F) % 23.0F;
float dh = (t + 2.0F) % 29.0F;
this.dripLFall.visible = dl < 9.0F;
this.dripRFall.visible = dr < 9.0F;
this.dripHairFall.visible = dh < 9.0F;
this.dripLFall.y = dl * dl * 0.18F;
this.dripRFall.y = dr * dr * 0.18F;
this.dripHairFall.y = dh * dh * 0.18F;
'''


def solve_ik():
    import numpy as np
    from ent_ik import solve, point, write_consts, mirror
    from ent_model import Part
    spec = MirrorWoman.__new__(MirrorWoman)
    spec.root = Part('root')
    spec.build()
    Pp = {p.name: p for p in spec.root.walk()}
    vary = lambda pre: [(f'{pre}_arm', 'xRot'), (f'{pre}_arm', 'zRot'), (f'{pre}_arm', 'yRot'),
                        (f'{pre}_forearm', 'xRot'), (f'{pre}_hand', 'xRot')]
    bnd = [(-3.3, 0.8), (-1.4, 1.4), (-1.3, 1.3), (-2.7, 0.1), (-1.6, 1.6)]
    out = {}

    def posed(fixed):
        for q in Pp.values():
            q.reset()
        for pn, attrs in fixed.items():
            for a, v in attrs.items():
                setattr(Pp[pn], a, v)

    def put(prefix, r):
        for k, v in r.items():
            part, attr = k.split('.')
            s = 'R' if part.startswith('right') else 'L'
            key = {'arm.xRot': 'X', 'arm.zRot': 'Z', 'arm.yRot': 'Y', 'forearm.xRot': 'F', 'hand.xRot': 'H'}[
                part.split('_', 1)[1] + '.' + attr]
            out[f'{prefix}_{s}{key}'] = v
    # BOWED: face in her hands while bent over
    fx = {'body': {'xRot': 0.85}, 'neck': {'xRot': 0.25}, 'head': {'xRot': 0.35}}
    posed(fx)
    tgt = point(Pp['head'], (-1.1, -3.0, -3.4))
    tip = point(Pp['head'], (-0.6, -5.2, -3.3))
    r = solve(spec, 'right_hand', (0, 1.0, -0.25), tgt, vary('right'), fixed=fx, bounds=bnd, init=[-1.6, 0.2, 0, -1.6, 0],
              also=[('right_f2', (0, 2.5, 0), tip, 0.4)])
    put('BW', r)
    put('BW', mirror(r))
    # PRESS: palms flat on the glass 7 px ahead, fingers up; then dragged ~2.5 px down
    for name, py in (('PR', 2.5), ('PD', 5.0)):
        fx = {'body': {'xRot': 0.16 if name == 'PR' else 0.24}}
        posed(fx)
        r = solve(spec, 'right_hand', (0, 1.0, -0.25), (-2.6, py, -7.2), vary('right'), fixed=fx, bounds=bnd,
                  init=[-1.4, 0, 0, -0.3, 1.2], also=[('right_f2', (0, 2.5, 0), (-2.6, py - 3.6, -7.3), 0.8)])
        put(name, r)
        put(name, mirror(r))
    write_consts(__file__, 'MirrorWoman', out)
    print('solved', len(out))


# --- solved constants (ent_ik), do not edit by hand
MirrorWoman.consts = {
    'BW_LF': -1.8145,
    'BW_LH': 0.0577,
    'BW_LX': -0.8405,
    'BW_LY': 0.6181,
    'BW_LZ': 0.0754,
    'BW_RF': -1.8145,
    'BW_RH': 0.0577,
    'BW_RX': -0.8405,
    'BW_RY': -0.6181,
    'BW_RZ': -0.0754,
    'PD_LF': -1.5276,
    'PD_LH': -1.2033,
    'PD_LX': -0.7238,
    'PD_LY': 0.2942,
    'PD_LZ': -0.1408,
    'PD_RF': -1.5276,
    'PD_RH': -1.2033,
    'PD_RX': -0.7238,
    'PD_RY': -0.2942,
    'PD_RZ': 0.1408,
    'PR_LF': -1.4866,
    'PR_LH': -0.8494,
    'PR_LX': -1.0311,
    'PR_LY': 0.2543,
    'PR_LZ': -0.1082,
    'PR_RF': -1.4866,
    'PR_RH': -0.8494,
    'PR_RX': -1.0311,
    'PR_RY': -0.2543,
    'PR_RZ': 0.1082,
}
# --- end solved constants


SPEC = MirrorWoman


import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.mirror(), 1: SC.mirror(), 2: SC.mirror(), 3: SC.mirror(), 4: SC.mirror()}
WALKING = ()
NAKED_ALPHA = 0.25


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.mirror()
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, b.view(4, 60, yaw_view=yv, scene=sc)))
    ims.append(('face close', b.view(1, 60, yaw_view=0, pitch_view=0, size=(190, 190), ortho=0.33, center_y=1.55, scene=sc)))
    ims.append(('3/4 close', b.view(1, 60, yaw_view=-30, pitch_view=0, size=(190, 190), ortho=0.33, center_y=1.55, scene=sc)))
    rows.append(('STAND (4) / STARE (1) close-ups: hair over the face, one eye', ims))
    ims = []
    for (yv, lab, tt) in ((0, 'front', 40), (40, '3/4', 52), (90, 'side', 40), (-30, '3/4 other', 47)):
        ims.append((lab, b.view(0, tt, yaw_view=yv, pitch_view=8, scene=sc)))
    ims.append(('through the mirror', b.view(0, 40, size=(220, 270), eye=(0.3, 1.62, 3.0), target=(0, 1.2, 0), scene=sc, fov=50)))
    rows.append(('BOWED (0): over the dead sink, face in her hands, sobbing', ims))
    ims = []
    head = lambda tt: (35.0 * math.sin(tt * 0.05), 0.0)      # noqa: E731
    for tt in (0, 10, 20, 30, 45):
        ims.append((f't {tt}', b.view([(0, 1)], 100 + tt, yaw_view=0, head=head, scene=sc)))
    rows.append(('STARE (1): the head follows you a moment late (target swinging +-35 deg)', ims))
    ims = []
    for ptv in (0, 6, 12, 30, 70):
        ims.append((f'pt {ptv}', b.view([(0, 1), (100, 2)], 100 + ptv, yaw_view=70, scene=sc)))
    ims.append(('front pt 20', b.view([(0, 1), (100, 2)], 120, yaw_view=0, pitch_view=0, scene=sc)))
    ims.append(('glass close', b.view([(0, 1), (100, 2)], 140, yaw_view=0, pitch_view=0, size=(190, 190), ortho=0.5,
                                      center_y=1.35, scene=sc)))
    rows.append(('PRESS (2): palms flat on the glass ~7 px ahead, dragging down', ims))
    ims = []
    for ptv in (0, 2, 4, 8, 20):
        ims.append((f'pt {ptv}', b.view([(0, 1), (100, 3)], 100 + ptv, yaw_view=10, scene=sc)))
    ims.append(('close', b.view([(0, 1), (100, 3)], 120, yaw_view=5, pitch_view=0, size=(190, 190), ortho=0.42,
                                center_y=1.5, scene=sc)))
    ims.append(('side', b.view([(0, 1), (100, 3)], 120, yaw_view=80, scene=sc)))
    rows.append(('SCREAM (3): head back, mouth too wide, clawed hands', ims))
    ims = []
    eye = (0.2, 1.62, 3.2)
    ims.append(('naked eye a=0.25', b.view(1, 60, size=(260, 260), eye=eye, target=(0, 1.3, 0), night=True, alpha=0.25, eyes_gain=0.5,
                                           scene=sc, fov=50, flash=False, amb=0.35)))
    ims.append(('finder: BOWED (photo)', b.view(0, 40, size=(260, 260), eye=eye, target=(0, 1.1, 0), finder=True, scene=sc, fov=50)))
    ims.append(('finder: PRESS', b.view([(0, 1), (100, 2)], 130, size=(260, 260), eye=(0.1, 1.55, 2.0), target=(0, 1.35, 0),
                                        finder=True, scene=sc, fov=55)))
    ims.append(('finder: SCREAM', b.view([(0, 1), (100, 3)], 120, size=(260, 260), eye=(0.1, 1.55, 2.0), target=(0, 1.45, 0),
                                         finder=True, scene=sc, fov=55)))
    rows.append(('through the mirror at night', ims))
    return rows
