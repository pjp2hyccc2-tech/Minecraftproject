"""女 the woman - 0.6 x 1.8. The apparition only the cameras see: very tall and thin, long wet black hair hanging over
her face (parted over one eye), a long white nightdress, old and stained, grey bare feet with black soles, thin grey
arms long enough that the hands hang at her knees, and too-long fingers (two joints each, 3.5 px). She stands in
a corner facing the wall... or the lens, a little closer every time you look. Poses: 0 STAND, 1 FACE, 2 CLOSE, 3 GONE.

Translucent-friendly (entityTranslucent): every box drops the faces that would show through the others; the hair
curtains are single cards. Like the doll she runs on her own clock (pose time accumulated across poses): hold the
pose time and she is utterly still; glitch makes her joints snap and (>= 0.5) drops frames."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

SKIN = ['#2d3236', '#3c4246', '#4d5358', '#5f666a', '#72787b', '#858a8c', '#979b9c', '#a8abab']
HAIR = ['#020203', '#050607', '#0a0b0d', '#101215', '#171a1e', '#20242a', '#2d323a', '#3f4550']
DRESS = ['#4f4b43', '#66625a', '#7e7a71', '#959187', '#aaa69b', '#bdb9ad', '#ccc8bc', '#d8d5ca']
STAIN = ['#3a2a1c', '#4f3a26', '#665034', '#7c6644']
NAIL = ['#16141a', '#232029', '#322d38']
LIPS = ['#272c38', '#353b49', '#454c5c']
SOLE = ['#0b0a0a', '#151313', '#211e1c']


class Woman(Spec):
    id = 'woman'
    java_name = 'WomanModel'
    module = 'ent_woman'
    px = 2
    tex_w = 64
    tex_h = 64
    height = 1.8
    translucent = True
    poses = [('STAND', 0), ('FACE', 1), ('CLOSE', 2), ('GONE', 3)]
    doc = """女 (the woman): the apparition only the cameras see - very tall and thin, long wet black hair over her face
    (parted over her left eye), a long stained white nightdress, grey bare feet, thin grey arms whose hands hang at
    her knees, too-long two-jointed fingers. Translucent-friendly (entityTranslucent, no inner faces).
    The animation runs on her own clock = pose time accumulated across poses (hold it and she is utterly still;
    the drips from her hair and fingers fall in real time); glitch 0..0.5 snaps her joints, 0.5..1 also drops frames.
    Pose ids: 0 STAND (in a corner facing the wall, head bowed almost to it, arms hanging, perfectly still but for
    a rare twitch), 1 FACE (turned to the lens: chin down, head tilted, looking up through the parted hair - the
    eye glints on the eyes layer), 2 CLOSE (leans toward the lens from the hips, neck stretched long, her face and
    her right hand with its fingers spread aimed at it: set the entity pitch (headPitch, + = down) to the angle from
    her eyes down to the lens and the head yaw (clamped +-69 deg) toward it. Her visible eye then sits ~0.41 / 0.59 /
    0.71 / 0.78 blocks in front of her feet at 1.49 / 1.34 / 1.15 / 0.93 blocks height for pitch 0 / 20 / 40 / 60,
    and the fingertips ~1.1-1.25 blocks out along the same line - put the lens just beyond them, or under them for
    the hand to cover it), 3 GONE (her joints snap and she folds into the floor over 4 ticks, then nothing is drawn)."""

    # ------------------------------------------------------------------------------------------------ geometry
    def build(self):
        r = self.root
        body = Part('body', (0, 11.0, 0))
        body.cube(Cube((-3.0, -7.5, -1.5), (6.0, 7.5, 3.0), tag='torso', faces=('top', 'right', 'front', 'left', 'back')))
        skirt = Part('skirt', (0, -0.25, 0),
                     cubes=[Cube((-3.5, 0, -1.75), (7.0, 9.25, 3.5), tag='skirt', faces=('right', 'front', 'left', 'back', 'bottom'))])
        hem = Part('hem', (0, 9.25, -1.8), cubes=[Cube((-3.5, 0, 0), (7.0, 1.0, 0), tag='hem', faces=('front',))])
        skirt.add(hem)
        neck = Part('neck', (0, -7.5, 0), cubes=[Cube((-0.75, -1.5, -0.75), (1.5, 1.5, 1.5), tag='neck',
                                                      faces=('right', 'front', 'left', 'back'))])
        head = Part('head', (0, -1.5, 0), cubes=[Cube((-2.75, -6.0, -2.5), (5.5, 6.0, 5.0), tag='head')])
        hf = Part('hair_front', (0, -6.1, -2.62), cubes=[Cube((-3.0, 0, 0), (6.0, 6.5, 0), tag='hair_front', faces=('front',))])
        hf2 = Part('hair_front2', (0, 6.5, 0), cubes=[Cube((-2.75, 0, 0), (5.5, 7.5, 0), tag='hair_front2', faces=('front',))])
        hf.add(hf2)
        hb = Part('hair_back', (0, -5.6, 2.55), cubes=[Cube((-3.0, 0, 0), (6.0, 7.0, 0.5), tag='hair_back',
                                                            faces=('back', 'right', 'left', 'front'))])
        hb2 = Part('hair_back2', (0, 7.0, 0), cubes=[Cube((-2.75, 0, 0), (5.5, 9.0, 0.5), tag='hair_back2',
                                                          faces=('back', 'right', 'left', 'front', 'bottom'))])
        hb.add(hb2)
        hr = Part('hair_r', (-2.9, -5.2, -0.4), cubes=[Cube((-0.25, 0, -1.25), (0.5, 10.5, 2.5), tag='hair_r',
                                                            faces=('right', 'left', 'front', 'back', 'bottom'))])
        hl = Part('hair_l', (2.9, -5.2, -0.4), cubes=[Cube((-0.25, 0, -1.25), (0.5, 11.0, 2.5), tag='hair_l',
                                                           faces=('right', 'left', 'front', 'back', 'bottom'))])
        drip_h = Part('drip_h', (-1.0, 7.5, 0.0))
        drip_h.add(Part('drip_h_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), tag='drop')]))
        hf2.add(drip_h)
        head.add(hf, hb, hr, hl)
        neck.add(head)
        arms = {}
        for side, pre in ((-1, 'r'), (1, 'l')):
            arm = Part(f'arm_{pre}', (side * 3.75, -7.0, 0), cubes=[Cube((-0.75, -0.5, -0.75), (1.5, 6.0, 1.5), tag=f'arm_{pre}')])
            fore = Part(f'fore_{pre}', (0, 5.5, 0), cubes=[Cube((-0.75, 0, -0.75), (1.5, 5.5, 1.5), tag=f'fore_{pre}',
                                                                faces=('right', 'front', 'left', 'back', 'bottom'))])
            hand = Part(f'hand_{pre}', (0, 5.5, 0), cubes=[Cube((-0.75, 0, -0.5), (1.5, 1.5, 1.0), tag=f'hand_{pre}',
                                                                faces=('right', 'front', 'left', 'back', 'bottom'))])
            for i, fx in enumerate((-0.6, -0.2, 0.2, 0.6)):
                fx = fx * -side
                f1 = Part(f'f{i + 1}{pre}', (fx, 1.4, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 1.75, 0.5),
                                                                      tag=f'f{i + 1}{pre}', uv_of=None if (i, pre) == (0, 'r') else 'f1r',
                                                                      faces=('right', 'front', 'left', 'back'))])
                f2 = Part(f'f{i + 1}{pre}_tip', (0, 1.75, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 1.75, 0.5),
                                                                          tag=f'f{i + 1}{pre}_tip', uv_of=None if (i, pre) == (0, 'r') else 'f1r_tip',
                                                                          faces=('right', 'front', 'left', 'back', 'bottom'))])
                f1.add(f2)
                hand.add(f1)
            hand.add(Part(f'thumb_{pre}', (side * -0.8, 0.4, -0.2), rot=(0, 0, side * -0.45),
                          cubes=[Cube((-0.25, 0, -0.25), (0.5, 1.5, 0.5), tag=f'thumb_{pre}', uv_of=None if pre == 'r' else 'thumb_r',
                                      faces=('right', 'front', 'left', 'back', 'bottom'))]))
            drip = Part(f'drip_{pre}', (0, 1.75, 0))
            drip.add(Part(f'drip_{pre}_fall', (0, 0, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 0.5, 0.5), tag=f'drop_{pre}',
                                                                     uv_of='drop')]))
            hand.children[2].children[0].add(drip)
            fore.add(hand)
            arm.add(fore)
            arms[pre] = arm
        body.add(skirt, neck, arms['r'], arms['l'])
        legs = []
        for side, pre in ((-1, 'r'), (1, 'l')):
            leg = Part(f'leg_{pre}', (side * 1.2, 16.0, 0), cubes=[Cube((-0.875, 0, -0.875), (1.75, 7.0, 1.75), tag=f'leg_{pre}',
                                                                        faces=('right', 'front', 'left', 'back'))])
            leg.add(Part(f'foot_{pre}', (0, 7.0, 0), cubes=[Cube((-0.875, 0, -2.25), (1.75, 1.0, 3.0), tag=f'foot_{pre}')]))
            legs.append(leg)
        r.add(body, *legs)

    # ------------------------------------------------------------------------------------------------ texture
    def paint(self):
        pt = Painter(self, 6161)
        F, EF = pt.F, pt.EF
        rng = np.random.default_rng(61)

        def edge_dist(f):
            ys, xs = np.mgrid[0:f.h, 0:f.w]
            return np.minimum(np.minimum(xs, f.w - 1 - xs), np.minimum(ys, f.h - 1 - ys))

        def skin(tag, base=4.6):
            pa = pal_arr(SKIN)
            fs = F(tag)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                v = base + pt.n3(Pp * 1.2) * 0.45 + pt.n3c(Pp) * 0.25 - f.normal()[1] * 0.4
                vein = 1.0 - np.abs(pt.n3b(Pp * np.array([2.5, 0.6, 2.5])))
                v = np.where(vein > 0.93, v - 0.9, v)                   # bluish veins under the grey skin
                v = v - 0.35 * (edge_dist(f) == 0)
                f.view()[:] = pa[np.clip(np.round(v + rng.normal(0, 0.15, v.shape)), 0, len(SKIN) - 1).astype(int)]
            return fs

        def hair(tag, base=2.2, ragged=0, seed=1, gap=None):
            pa = pal_arr(HAIR)
            fs = F(tag)
            rr = np.random.default_rng(seed)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                strand = np.sin(Pp[..., 0] * 5.5 + Pp[..., 2] * 4.0 + pt.n3(Pp * 0.7) * 2.0)
                v = base + strand * 0.7 + pt.n3c(Pp) * 0.35
                sheen = (strand > 0.75) & (rr.random(v.shape) < 0.5)       # wet highlights running down
                v = np.where(sheen, v + 2.4, v)
                f.view()[:] = pa[np.clip(np.round(v), 0, len(HAIR) - 1).astype(int)]
                if ragged and n not in ('top',):
                    cut = rr.integers(0, ragged + 1, f.w)
                    for x in range(f.w):
                        for y in range(f.h - cut[x], f.h):
                            f.clear(x, y)
                if gap is not None and n == 'front':
                    for y in range(f.h):
                        for x in range(f.w):
                            if gap(x, y):
                                f.clear(x, y)
            return fs

        # ---------------------------------------------------------------- the nightdress
        dp = pal_arr(DRESS)
        sp = pal_arr(STAIN)
        for tag in ('torso', 'skirt', 'hem'):
            for n, f in F(tag).items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                fold = np.sin(Pp[..., 0] * 1.9 + Pp[..., 2] * 1.9 + pt.n3(Pp * 0.6) * 1.6)
                y = Pp[..., 1]
                v = 5.3 + fold * 0.8 + pt.n3c(Pp) * 0.3 - f.normal()[1] * 0.3 - 1.5 * np.clip((y - 15.0) / 6.0, 0, 1)
                cling = pt.n3b(Pp * np.array([1.4, 0.4, 1.4]) + 3.0)          # wet cloth clinging in streaks
                v = np.where(cling > 0.35, v - 1.0, v)
                col = dp[np.clip(np.round(v + rng.normal(0, 0.18, v.shape)), 0, len(DRESS) - 1).astype(int)].copy()
                # old stains: water-yellowed blotches, a large dark one down the front, mud rising from the hem
                st = pt.n3b(Pp * 0.5 + 17.0)
                m = np.clip((st - 0.3) * 1.5, 0, 0.5)
                blot = np.exp(-(((Pp[..., 0] - 1.2) / 1.5) ** 2 + ((y - 8.0) / 2.2) ** 2))
                runs = np.exp(-(((Pp[..., 0] - 1.2 + 0.9 * np.round(np.sin(Pp[..., 0] * 3.0))) / 0.35) ** 2)) * \
                    np.clip((y - 8.0) / 2.0, 0, 1) * np.clip((15.5 - y) / 4.0, 0, 1)
                front = (blot + 0.7 * runs) * (f.normal()[2] < -0.5)
                m2 = np.clip(front * (0.6 + 0.4 * pt.n3c(Pp * 2.0)) * 0.75, 0, 0.62)
                mud = np.clip((y - 17.5) / 2.5, 0, 1) * (0.6 + 0.4 * pt.n3(Pp * 2.0))
                for mm, c in ((m, sp[3]), (m2, sp[0]), (np.clip(mud, 0, 0.8), sp[1])):
                    col[..., :3] = col[..., :3] * (1 - mm[..., None]) + c[:3] * mm[..., None]
                f.view()[:] = col
        f = F('torso')['front']                                   # 12 x 15: a gathered neckline, the collarbones
        for x in range(2, 10):
            f.set(x, 0, P(SKIN, 4.5))
            if 3 <= x <= 8:
                f.set(x, 1, P(SKIN, 4.0))
        for x in range(1, 11):
            f.set(x, 2, P(DRESS, 3 + (x % 2)))
        for n in ('front', 'back', 'left', 'right'):
            fc = F('hem')['front'] if n == 'front' else None
        fc = F('hem')['front']                                    # 14 x 2: a torn, ragged hem
        for x in range(fc.w):
            if rng.random() < 0.45:
                fc.clear(x, 1)
            if rng.random() < 0.15:
                fc.clear(x, 0)
        for n in ('front', 'back', 'left', 'right'):              # sleeves: short, the arm bare from the elbow
            fc = F('arm_r')[n]
        # ---------------------------------------------------------------- skin: neck, arms, hands, fingers, legs, feet
        for tag in ('neck', 'fore_r', 'fore_l', 'hand_r', 'hand_l', 'f1r', 'f1r_tip', 'thumb_r', 'leg_r', 'leg_l',
                    'foot_r', 'foot_l'):
            skin(tag)
        for tag in ('arm_r', 'arm_l'):                            # short sleeves over the upper arms
            fs = skin(tag)
            for n, fc in fs.items():
                if n in ('top', 'bottom'):
                    continue
                for y in range(0, 7):
                    for x in range(fc.w):
                        fc.set(x, y, P(DRESS, 4.6 + rng.normal(0, 0.4) - (1.2 if y == 6 else 0)))
            fs['top'].fill(P(DRESS, 5))
        for n, fc in F('f1r_tip').items():                        # blackened nails at the fingertips
            if n in ('front', 'back', 'left', 'right'):
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(NAIL, 1))
                    fc.set(x, fc.h - 2, P(NAIL, 2))
            if n == 'bottom':
                fc.fill(P(NAIL, 0))
        for n, fc in F('thumb_r').items():
            if n in ('front', 'back', 'left', 'right'):
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(NAIL, 1))
        for n, fc in F('f1r').items():                            # knuckles
            if n in ('front', 'back'):
                for x in range(fc.w):
                    fc.dl(x, fc.h - 1, -0.08)
        for tag in ('foot_r', 'foot_l'):
            fs = F(tag)
            fs['bottom'].fill(P(SOLE, 1))
            for n in ('front', 'left', 'right', 'back'):
                fc = fs[n]
                for x in range(fc.w):
                    fc.set(x, fc.h - 1, P(SOLE, 2))
            fc = fs['front']                                      # toes
            for x in range(fc.w):
                if x % 1 == 0:
                    fc.dl(x, 0, -0.06 if x % 2 else 0.03)
            fc = fs['top']
            for x in range(fc.w):
                for y in range(fc.h - 2, fc.h):
                    if x % 2 == 0:
                        fc.dl(x, y, -0.08)
        for tag in ('leg_r', 'leg_l'):                            # dirt rising up the shins
            for n, fc in F(tag).items():
                for y in range(fc.h - 4, fc.h):
                    for x in range(fc.w):
                        fc.dl(x, y, -0.03 * (y - fc.h + 5))
        # ---------------------------------------------------------------- the head: hair all round, a grey face
        hair('head', base=2.0)
        f = F('head')['front']                                    # 11 x 12: the face under the curtain
        face = [
            'hhhhhhhhhhh',
            'hhh44444hhh',
            'h444444444h',
            'h433344#33h',
            'h3##334###3',
            'h3#e#3#WIWw',
            'h33#334RRR3',
            'h3333443#33',
            'h3333343333',
            'h33mLLLLm33',
            'hh33llll33h',
            'hhh333333hh',
        ]
        leg = {'h': None, '3': (SKIN, 3.2), '4': (SKIN, 4.0), '#': (SKIN, 1.2), 'e': (SKIN, 1.0), 'W': C('#b3a49b'),
               'w': C('#8f7570'), 'I': C('#9aa1a3'), 'R': C('#6e3c3a'), 'm': (LIPS, 0), 'L': (LIPS, 2), 'l': (LIPS, 1)}
        pt.pattern(f, face, leg, jitter=False)
        f.set(8, 5, C('#050506'))                                 # a pinprick pupil in a pale, clouded iris
        ef = EF('head')['front']
        ef.set(8, 5, C('#7d878b'))
        ef.set(7, 5, C('#3c4245'))
        ef.set(9, 5, C('#30353a'))
        # the curtain of wet hair: parted over her left eye (texture x 7..9 of the face, rows 4..6)
        hair('hair_front', base=2.0, seed=11, gap=lambda x, y: (7 <= x <= 10 and 4 <= y <= 6) or (8 <= x <= 9 and y == 7)
             or (x in (2, 5) and y >= 10))
        hair('hair_front2', base=2.0, seed=12, ragged=5, gap=lambda x, y: (x in (1, 4, 8) and y > 4) or (x == 10 and y > 8))
        hair('hair_back', base=2.0, seed=13)
        hair('hair_back2', base=2.0, seed=14, ragged=5)
        hair('hair_r', base=2.0, seed=15, ragged=4)
        hair('hair_l', base=2.0, seed=16, ragged=4)
        for n, fc in F('drop').items():
            c = C('#9fb4bc')
            c[3] = 225
            fc.fill(c)
        for n, fc in EF('drop').items():
            fc.fill(C('#182227'))
        return pt.img, pt.eyes

    # ------------------------------------------------------------------------------------------------ animation
    prologue = '''
float clock = advance(mem, pose, pt);
float hold = Mth.clamp(glitch * 1.6F - 0.6F, 0.0F, 1.0F);
float t = hold > 0.0F ? frames(clock, hold) : clock;
pt = Math.max(0.0F, pt - (clock - t));
float yaw = Mth.clamp(netHeadYaw * 0.017453292F, -1.2F, 1.2F);
float pitch = Mth.clamp(headPitch * 0.017453292F, -0.6F, 1.2F);
'''

    helpers = '''
/** Stop-motion: at g = 1 time advances only in whole 3-tick frames; below that each frame holds, then catches up. */
private static float frames(float x, float g) {
    float i = (float) Mth.floor(x / 3.0F);
    float f = x / 3.0F - i;
    float hold = Mth.clamp(g * (1.0F + 0.45F * hash(i + 31.0F)), 0.0F, 1.0F);
    float s = hold >= 1.0F ? 0.0F : (f < hold ? 0.0F : (f - hold) / (1.0F - hold));
    return (i + s) * 3.0F;
}

/** A flick every `period` ticks: up in two ticks, back down over four (t must be >= 0). */
private static float flick(float t, float period, float phase) {
    float m = (t + phase) % period;
    if (m < 2.0F) {
        return m * 0.5F;
    }
    return m < 6.0F ? 1.0F - (m - 2.0F) / 4.0F : 0.0F;
}

/** A joint snapping the wrong way: -1..1 during random 3-tick windows (more of them at higher g), else 0. */
private static float jolt(float t, float seed, float g) {
    float i = (float) Mth.floor(t / 3.0F);
    if (hash(i * 1.37F + seed * 11.3F) >= g * 0.4F) {
        return 0.0F;
    }
    return hash(i + seed * 7.1F) * 2.0F - 1.0F;
}
'''

    anim = '''
float j1 = jolt(t, 1.0F, glitch);
float j2 = jolt(t, 2.0F, glitch);
float j3 = jolt(t, 3.0F, glitch);
float curl = 0.22F;
float spread = 0.05F;
float curlR = 0.0F;
float spreadR = 0.0F;
float hairFlop = 0.9F;
float sideFlop = 0.9F;
float frontFlop = 0.9F;
float skirtFlop = 1.0F;
float armFlop = 0.0F;
float drips = 1.0F;
if (pose == FACE) {
    // turned to the lens: chin down, the head tipped over, looking up at you through the parted hair
    this.body.xRot = 0.03F;
    this.body.zRot = Mth.sin(t * 0.017F) * 0.012F;
    this.neck.xRot = 0.12F;
    this.head.xRot = 0.3F + flick(t, 151.0F, 40.0F) * 0.12F;
    this.head.zRot = 0.26F + j1 * 0.4F;
    this.head.yRot = yaw * 0.5F;
    this.armR.xRot = -0.06F;
    this.armL.xRot = -0.04F;
    this.armR.zRot = 0.05F;
    this.armL.zRot = -0.06F;
    this.foreR.xRot = -0.1F;
    this.foreL.xRot = -0.08F;
    curl = 0.12F + flick(t, 97.0F, 13.0F) * 0.5F;
    spread = 0.14F;
} else if (pose == CLOSE) {
    // leaning toward the lens from the hips, the neck stretched long, her face and one reaching hand aimed down the
    // entity pitch at it (pitch + = the lens is below her eyes), the fingers spread to cover it
    float in = easeOut(pt / 10.0F);
    float bend = Mth.clamp(0.55F + pitch * 0.75F, 0.3F, 1.45F) * in;
    this.body.xRot = bend;
    this.neck.xRot = -0.35F * in;
    this.neck.yScale = 1.0F + 0.7F * in;
    this.head.xRot = (pitch - bend / Math.max(0.05F, in) + 0.35F) * in + j3 * 0.25F;
    this.head.zRot = 0.36F * in + j1 * 0.4F;
    this.head.yRot = yaw * 0.6F;
    this.armR.xRot = (-1.6F + pitch) * in - bend + Mth.sin(t * 0.05F) * 0.02F;
    this.armR.zRot = 0.1F * in;
    this.armR.yRot = (0.18F + yaw * 0.3F) * in;
    this.foreR.xRot = -0.12F * in;
    this.handR.xRot = -0.3F * in;
    this.armL.zRot = -0.08F;
    armFlop = 0.85F;
    curlR = -0.12F;
    spreadR = 0.32F;
    curl = 0.35F;
    hairFlop = 0.6F;
    sideFlop = 0.25F;
    frontFlop = 0.2F;
} else if (pose == GONE) {
    // the feed tears: her joints snap every which way and she folds into the floor; then nothing
    float k = span(pt, 0.0F, 4.0F);
    float shake = noise(t * 4.0F) * (1.0F - k);
    this.root.visible = pt < 4.0F;
    this.body.y = 11.0F + 9.0F * k * k;
    this.body.yScale = 1.0F - 0.75F * k;
    this.body.zRot = shake * 0.5F;
    this.head.zRot = shake * 1.4F + 1.2F * k;
    this.armR.zRot = shake * 1.8F;
    this.armL.zRot = -shake * 1.6F;
    this.legR.yScale = 1.0F - 0.9F * k;
    this.legL.yScale = 1.0F - 0.9F * k;
    this.legR.y = 16.0F + 7.0F * k;
    this.legL.y = 16.0F + 7.0F * k;
    curl = 1.4F * (1.0F - k);
    drips = 0.0F;
} else {
    // STAND: in the corner facing the wall, head bowed almost to it, arms hanging; nothing moves but a rare twitch
    this.body.xRot = 0.02F;
    this.neck.xRot = 0.22F;
    this.head.xRot = 0.28F;
    this.head.zRot = 0.07F + flick(t, 131.0F, 17.0F) * 0.3F + j1 * 0.4F;
    this.armR.zRot = 0.035F;
    this.armL.zRot = -0.035F;
    this.foreR.xRot = -0.04F;
    this.foreL.xRot = -0.04F;
    curl = 0.3F + flick(t, 83.0F, 29.0F) * 0.45F;
}
this.armR.zRot = this.armR.zRot + j2 * 0.5F;
this.armL.zRot = this.armL.zRot - j3 * 0.5F;
// the long fingers: two joints each, curling and spreading
float cr = curl + curlR;
float sr = spread + spreadR;
this.f1r.xRot = -cr * 0.8F;
this.f2r.xRot = -cr * 0.9F;
this.f3r.xRot = -cr * 0.85F;
this.f4r.xRot = -cr * 0.7F;
this.f1rTip.xRot = -cr * 1.1F;
this.f2rTip.xRot = -cr * 1.2F;
this.f3rTip.xRot = -cr * 1.15F;
this.f4rTip.xRot = -cr * 1.0F;
this.f1r.zRot = sr * 1.5F;
this.f2r.zRot = sr * 0.5F;
this.f3r.zRot = -sr * 0.5F;
this.f4r.zRot = -sr * 1.5F;
this.f1l.xRot = -curl * 0.8F;
this.f2l.xRot = -curl * 0.9F;
this.f3l.xRot = -curl * 0.85F;
this.f4l.xRot = -curl * 0.7F;
this.f1lTip.xRot = -curl * 1.1F;
this.f2lTip.xRot = -curl * 1.2F;
this.f3lTip.xRot = -curl * 1.15F;
this.f4lTip.xRot = -curl * 1.0F;
this.f1l.zRot = -spread * 1.5F;
this.f2l.zRot = -spread * 0.5F;
this.f3l.zRot = spread * 0.5F;
this.f4l.zRot = spread * 1.5F;
'''

    post = '''
// the skirt and the hair hang by their own weight; the lower curtain and the long back hair swing late
flop(mem, 0, this.skirt, skirtFlop, 0.0F, 1.0F, 0.0F, 0.9F, 0.7F, t, this.root, this.body);
flop(mem, 3, this.hairBack, hairFlop * 0.8F, 0.0F, 1.0F, 0.0F, 0.5F, 0.3F, t, this.root, this.body, this.neck, this.head);
flop(mem, 6, this.hairBack2, hairFlop, 0.0F, 1.0F, 0.0F, 0.45F, 0.25F, t, this.root, this.body, this.neck, this.head, this.hairBack);
flop(mem, 9, this.hairFront2, frontFlop, 0.0F, 1.0F, 0.0F, 0.45F, 0.25F, t, this.root, this.body, this.neck, this.head, this.hairFront);
flop(mem, 12, this.hairR, sideFlop, 0.0F, 1.0F, 0.0F, 0.5F, 0.3F, t, this.root, this.body, this.neck, this.head);
flop(mem, 15, this.hairL, sideFlop, 0.0F, 1.0F, 0.0F, 0.5F, 0.3F, t, this.root, this.body, this.neck, this.head);
flop(mem, 18, this.armL, armFlop, 0.0F, 1.0F, 0.0F, 0.6F, 0.4F, t, this.root, this.body);
// the upper curtain stays on the face, falling away only when her head tips back
float hx = this.body.xRot + this.neck.xRot + this.head.xRot;
this.hairFront.xRot = Math.max(0.0F, -hx - 0.15F) * 0.9F;
level(this.dripH, this.root, this.body, this.neck, this.head, this.hairFront, this.hairFront2);
level(this.dripR, this.root, this.body, this.armR, this.foreR, this.handR, this.f3r, this.f3rTip);
level(this.dripL, this.root, this.body, this.armL, this.foreL, this.handL, this.f3l, this.f3lTip);
float da = (ageInTicks + 5.0F) % 31.0F;
float db = (ageInTicks + 13.0F) % 23.0F;
float dc = (ageInTicks + 2.0F) % 37.0F;
this.dripHFall.visible = drips > 0.5F && da < 10.0F;
this.dripRFall.visible = drips > 0.5F && db < 10.0F;
this.dripLFall.visible = drips > 0.5F && dc < 10.0F;
this.dripHFall.y = da * da * 0.2F;
this.dripRFall.y = db * db * 0.2F;
this.dripLFall.y = dc * dc * 0.2F;
'''


SPEC = Woman

import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.tatami(), 1: SC.tatami(), 2: SC.tatami(), 3: SC.tatami()}
POSE_TIMES = {0: 140.0, 1: 140.0, 2: 140.0, 3: 2.0}
VIEW_ORTHO = 1.12
VIEW_CENTER = 0.95
TURN_SIZE = (150, 230)
NIGHT_EYE = ((0.9, 1.5, 3.4), (0.0, 1.0, 0.0))
IR_EYE = ((1.6, 2.2, 3.0), (0.0, 0.95, 0.0))


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sel = (lambda k: rows_sel is None or k in rows_sel)
    if sel('hero'):
        ims = []
        ims.append(('FACE front', b.view(1, 140, yaw_view=0, pitch_view=4, size=(220, 300), ortho=1.1, center_y=0.95, scene=SC.tatami())))
        ims.append(('FACE 3/4', b.view(1, 140, yaw_view=-35, pitch_view=6, size=(220, 300), ortho=1.1, center_y=0.95, scene=SC.tatami())))
        ims.append(('face close', b.view(1, 140, yaw_view=-8, pitch_view=0, size=(220, 300), ortho=0.36, center_y=1.5, scene=SC.tatami())))
        ims.append(('STAND (from the room)', b.view(0, 140, yaw_view=200, pitch_view=8, size=(220, 300), ortho=1.1, center_y=0.95,
                                                    scene=SC.corner())))
        ims.append(('hands', b.view(1, 140, yaw_view=-20, pitch_view=0, size=(220, 300), ortho=0.42, center_y=0.42, scene=SC.tatami())))
        rows.append(('女: FACE (1) / STAND (0) lit', ims))
    if sel('feeds'):
        ims = []
        ims.append(('STAND: CAM2 corner', b.view(0, 140, size=(260, 220), eye=(-1.3, 2.1, -2.6), target=(0.0, 1.0, 0.0), look='ir',
                                                  scene=SC.corner(), fov=62, label='CAM2  和室')))
        ims.append(('FACE: the next visit', b.view(1, 140, size=(260, 220), eye=(0.9, 2.0, 2.2), target=(0.0, 1.1, 0.0), look='ir',
                                                   scene=SC.tatami(), fov=62, label='CAM2  和室', stamp='2026/11/03  03:44:12')))
        ims.append(('CLOSE: at the lens (pitch 20)', b.view([(0, 1), (100, 2)], 160, size=(260, 220), eye=(0.05, 0.98, -1.5),
                                                            target=(-0.12, 1.25, -0.6), look='ir', scene=SC.tatami(), fov=70,
                                                            entity_yaw=180.0, head=lambda tt: (0.0, 20.0), label='CAM2  和室',
                                                            stamp='2026/11/03  04:02:57')))
        ims.append(('CLOSE: low camera (pitch 40)', b.view([(0, 1), (100, 2)], 160, size=(260, 220), eye=(0.05, 0.5, -1.45),
                                                           target=(-0.12, 1.0, -0.7), look='ir', scene=SC.tatami(), fov=70,
                                                           entity_yaw=180.0, head=lambda tt: (0.0, 40.0), label='CAM4  玄関',
                                                           stamp='2026/11/03  04:05:31')))
        ims.append(('naked eye (she is not there)', b.view(1, 140, size=(220, 220), eye=(0.9, 1.6, 3.0), target=(0.0, 1.0, 0.0),
                                                           look='night', scene=SC.tatami(), fov=55, alpha=0.0)))
        rows.append(('女 on the feeds: every visit a little closer', ims))
    if sel('close'):
        ims = []
        for ptv in (0, 3, 6, 10, 20):
            ims.append((f'side pt {ptv}', b.view([(0, 1), (100, 2)], 100 + ptv, yaw_view=-90, pitch_view=4, size=(170, 230), ortho=1.1,
                                                 center_y=0.95, scene=SC.tatami())))
        ims.append(('front pt 20', b.view([(0, 1), (100, 2)], 120, yaw_view=-10, pitch_view=4, size=(170, 230), ortho=1.1, center_y=0.95,
                                          scene=SC.tatami())))
        ims.append(('pitch 40 side', b.view([(0, 1), (100, 2)], 120, yaw_view=-90, pitch_view=4, size=(170, 230), ortho=1.1,
                                            center_y=0.95, scene=SC.tatami(), head=lambda tt: (0.0, 40.0))))
        rows.append(('CLOSE (2): leaning in from the hips, the neck stretching, a hand reaching for the lens', ims))
    if sel('gone'):
        ims = []
        for ptv in (0, 1, 2, 3, 4):
            ims.append((f'pt {ptv}', b.view([(0, 1), (100, 3)], 100 + ptv, yaw_view=-20, pitch_view=4, size=(150, 230), ortho=1.1,
                                            center_y=0.95, scene=SC.tatami(), glitch=lambda tt: 0.6)))
        rows.append(('GONE (3): snapping and folding into the floor in 4 ticks (glitch 0.6), then nothing', ims))
    return rows
