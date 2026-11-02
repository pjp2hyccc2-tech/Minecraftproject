"""覗き NOZOKI - 0.6 x 2.6 blocks. Far too tall and thin, grey-white skin, long neck, wet black hair to the waist,
huge black eyes with pin-prick pupils, a lipless mouth whose jaw unhinges, arms past the knees, three long
two-jointed fingers per hand. Poses: 0 PEEK_LEFT, 1 PEEK_RIGHT, 2 STAND, 3 CRAWL, 4 RETREAT."""
import numpy as np

from common import ramp, pal, C, rng, lighten_arr
from ent_model import Spec, Part, Cube
from ent_lib import hand, HELPERS
from ent_paint import Img, faces, fill_cube, material, hair_material, ragged_bottom

SKIN = ramp(['#2e3133', '#4a4e50', '#686c6c', '#878a88', '#a3a5a1', '#bbbcb6', '#cecec7'], 7)
HAIR = ramp(['#040405', '#09090b', '#101013', '#18181d', '#24242b', '#353641'], 6)
MOUTH = pal('#0a0505', '#1a0b0a', '#2c1210', '#3d1a17')
NAIL = pal('#5e5a47', '#7a7458', '#948c6a')


class Nozoki(Spec):
    id = 'nozoki'
    java_name = 'NozokiModel'
    module = 'ent_nozoki'
    tex_w = 64
    poses = [('PEEK_LEFT', 0), ('PEEK_RIGHT', 1), ('STAND', 2), ('CRAWL', 3), ('RETREAT', 4)]
    doc = """覗き (nozoki): 2.6 blocks of grey-white skin and bone behind a cedar trunk. Only the sideways head and
    one gripping hand show when it peeks; standing it is far too tall, arms past the knees; when provoked it
    drops to all fours, the jaw hanging open, and charges like a spider.
    Pose ids: 0 PEEK_LEFT, 1 PEEK_RIGHT, 2 STAND, 3 CRAWL, 4 RETREAT (slides back behind the trunk on the side
    given by Mob#isLeftHanded(): the server should call setLeftHanded(true) when it peeks left)."""

    def build(self):
        r = self.root
        hips = Part('hips', (0, 6, 0), cubes=[Cube((-2.5, -3, -1.5), (5, 3, 3), 'skin', tag='pelvis')])
        torso = Part('torso', (0, -3, 0), cubes=[Cube((-3, -8, -1.5), (6, 8, 3), 'skin', tag='chest')])
        neck = Part('neck', (0, -8, 0), cubes=[Cube((-1, -5, -1), (2, 5, 2), 'skin', tag='neck')])
        head = Part('head', (0, -5, 0), cubes=[Cube((-3.5, -8, -3), (7, 6, 6), 'head', tag='cranium'),
                                               Cube((-3, -2.2, -2.6), (6, 2, 5), 'mouth', tag='mouth')])
        jaw = Part('jaw', (0, -2, 2.5), cubes=[Cube((-3.5, 0, -5.5), (7, 2, 6), 'jaw', tag='jaw')])
        hair_back = Part('hair_back', (0, -7.5, 3.05), cubes=[Cube((-3.5, 0, 0), (7, 9, 1), 'hair', tag='hair_back')])
        hair_back2 = Part('hair_back2', (0, 9, 0), cubes=[Cube((-3.5, 0, 0), (7, 11, 1), 'hair', tag='hair_back2')])
        hair_back.add(hair_back2)
        hair_front = Part('hair_front', (0, -8, -3.25), cubes=[Cube((-3.5, 0, 0), (7, 10, 0), 'hairf', tag='hair_front')])
        hair_l = Part('hair_l', (3.55, -7.5, 0.0), cubes=[Cube((-0.5, 0, -0.5), (1, 11, 3), 'hair', tag='hair_l')])
        hair_r = Part('hair_r', (-3.55, -7.5, 0.0), cubes=[Cube((-0.5, 0, -0.5), (1, 11, 3), 'hair', tag='hair_r')])
        head.add(jaw, hair_back, hair_front, hair_l, hair_r)
        neck.add(head)
        arms = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 3.6, -7.5, 0), cubes=[Cube((-1, -0.5, -1), (2, 10, 2), 'skin', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 9.5, 0), cubes=[Cube((-1, 0, -1), (2, 9, 2), 'skin', tag=f'{pre}_lower')])
            h = hand(pre, side, palm=(3, 3, 1), seg=(4, 3), fingers=3, mat='skin', nail='nail')
            h.pivot = (0, 9, 0)
            h.reset()
            fore.add(h)
            arm.add(fore)
            arms.append(arm)
        torso.add(neck, *arms)
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.4, 0, 0), cubes=[Cube((-1, 0, -1), (2, 9, 2), 'skin', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 9, 0), cubes=[Cube((-1, 0, -1), (2, 9, 2), 'skin', tag=f'{pre}_shin'),
                                                       Cube((-1, 8, -3), (2, 1, 2), 'foot', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        hips.add(torso, *legs)
        r.add(hips)

    # ------------------------------------------------------------------------------------------------ texture
    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        skin = material(SKIN, 801, base=4.2, amp=0.55, grad=0.9)
        hairm = hair_material(HAIR, 802)
        for p in self.root.walk():
            for c in p.cubes:
                m = {'skin': skin, 'head': hairm, 'hair': hairm, 'hairf': hairm, 'jaw': skin, 'foot': skin,
                     'nail': skin, 'mouth': material(MOUTH, 803, base=1.2, amp=0.6, grad=0.0, top_lift=0)}[c.mat]
                fill_cube(img, c, m)
        # ---- face (cranium front, 7 x 6): hairline, huge black eyes, pin-prick pupils, lipless mouth line
        cr = faces(img, self.cube('cranium'))
        f = cr['front']
        f.fill(lambda x, y, ff: SKIN[6] if y < 4 else SKIN[5])
        for (hx, hy) in ((0, 0), (1, 0), (5, 0), (6, 0)):
            f.set(hx, hy, HAIR[1])
        for ex in (1, 2, 4, 5):
            for ey in (2, 3):
                f.set(ex, ey, '#030303')
        f.set(1, 1, SKIN[4])            # sunken brow
        f.set(2, 1, SKIN[5])
        f.set(4, 1, SKIN[5])
        f.set(5, 1, SKIN[4])
        f.set(1, 4, SKIN[3])            # bruised lower lids
        f.set(2, 4, SKIN[4])
        f.set(4, 4, SKIN[4])
        f.set(5, 4, SKIN[3])
        f.set(3, 3, SKIN[4])            # flat noseless face: two slits
        f.set(3, 4, SKIN[3])
        f.set(2, 3, '#1e1e1e')          # pupils (base: barely lighter)
        f.set(4, 3, '#1e1e1e')
        for x in range(7):              # lipless mouth line, stretched to the cheeks
            f.set(x, 5, MOUTH[1] if 0 < x < 6 else SKIN[3])
        ef = faces(eyes, self.cube('cranium'))['front']
        ef.set(2, 3, '#fff6dc')
        ef.set(4, 3, '#fff6dc')
        ef.set(1, 2, '#26262a')         # faint wet glint on the eyeballs
        ef.set(4, 2, '#26262a')
        # the rest of the cranium is hair; the top shows a parting
        top = cr['top']
        for y in range(top.h):
            top.set(3, y, HAIR[3] if y % 2 else HAIR[2])
        # ---- jaw: lower teeth row (only seen when it opens), chin, the back under the hair
        jf = faces(img, self.cube('jaw'))
        jf['front'].fill(lambda x, y, ff: (SKIN[4] if y == 0 else SKIN[3]) if 0 < x < 6 else SKIN[2])
        jf['front'].set(1, 0, '#8d8873')          # two teeth edges at the corners of the lipless mouth
        jf['front'].set(5, 0, '#8d8873')
        jf['top'].fill(lambda x, y, ff: (('#a09a84' if x % 2 else '#7f7a66') if y == ff.h - 1 and 0 < x < 6
                                         else (MOUTH[2] if 0 < x < 6 else SKIN[3])))
        jf['back'].fill(lambda x, y, ff: HAIR[1])
        jf['right'].fill(lambda x, y, ff: HAIR[1] if x < 3 else SKIN[3])
        jf['left'].fill(lambda x, y, ff: HAIR[1] if x > 2 else SKIN[3])
        # upper teeth under the cranium front edge (visible when the jaw drops)
        cb = cr['bottom']
        cb.fill(lambda x, y, ff: ('#a8a38c' if (y == ff.h - 1 and 0 < x < 6 and x % 2 == 0) else MOUTH[0]))
        # ---- hair sheets: ragged ends, the front curtain with gaps so the eyes show
        for tag, seed in (('hair_back2', 811), ('hair_l', 812), ('hair_r', 813)):
            fs = faces(img, self.cube(tag))
            for n in ('front', 'back', 'left', 'right'):
                ragged_bottom(fs[n], seed + len(n), maxcut=4)
        hf = faces(img, self.cube('hair_front'))
        cols = {0: 1, 1: 0, 2: 0, 3: 0, 4: 0, 5: 0, 6: 1}
        for n in ('front', 'back'):
            fc = hf[n]
            for x in range(fc.w):
                xx = x if n == 'front' else fc.w - 1 - x
                ln = cols[xx]
                for y in range(fc.h):
                    strand = (xx == 3 and 4 <= y <= 9) or (xx == 0 and 4 <= y <= 9) or (xx == 6 and 5 <= y <= 9)
                    if not strand and y >= ln:
                        fc.clear(x, y)
                    else:
                        fc.set(x, y, HAIR[2 if (x + y) % 3 else 4])
        # ---- body details
        ch = faces(img, self.cube('chest'))
        for n in ('front', 'back', 'left', 'right'):
            fc = ch[n]
            for y in (2, 4, 6):
                for x in range(fc.w):
                    if n in ('front', 'back') and x in (fc.w // 2 - 1, fc.w // 2):
                        continue
                    fc.dl(x, y, -0.09)
        ch['front'].set(2, 7, SKIN[2])
        ch['front'].set(3, 7, SKIN[2])
        for n in ('front', 'left', 'right', 'back'):
            fc = ch[n]
            for x in range(fc.w):
                fc.dl(x, 0, 0.05)
        pv = faces(img, self.cube('pelvis'))
        pv['front'].fill(lambda x, y, ff: SKIN[2] if y > 0 else SKIN[3])
        # elbows, knees, dirt on shins and feet, nails on the last finger segments
        for pre in ('right', 'left'):
            for tag in (f'{pre}_thigh', f'{pre}_upper'):
                fs = faces(img, self.cube(tag))
                for n in ('front', 'back', 'left', 'right'):
                    fs[n].dl(0, fs[n].h - 1, -0.06)
                    fs[n].dl(1, fs[n].h - 1, -0.06)
            fs = faces(img, self.cube(f'{pre}_shin'))
            r = rng(820 + len(pre))
            for n in ('front', 'back', 'left', 'right'):
                for y in range(4, fs[n].h):
                    for x in range(fs[n].w):
                        if r.random() < (y - 3) * 0.1:
                            fs[n].set(x, y, '#2a2620' if r.random() < 0.5 else '#3a352c')
            fill_cube(img, self.cube(f'{pre}_foot'), lambda n, x, y, ff: '#2b2722' if (x + y) % 2 else '#3a352d')
            for i in (1, 2, 3):
                fs = faces(img, self.cube(f'{pre}_f{i}b'))
                fs['front'].set(0, 2, NAIL[1])
                fs['front'].set(0, 1, NAIL[2])
                fs['bottom'].set(0, 0, NAIL[0])
                fs['back'].set(0, 2, SKIN[2])
                for n in ('left', 'right'):
                    fs[n].set(0, 2, NAIL[0])
            fs = faces(img, self.cube(f'{pre}_thumb'))
            fs['front'].set(0, fs['front'].h - 1, NAIL[1])
            fs = faces(img, self.cube(f'{pre}_palm'))
            fs['front'].fill(lambda x, y, ff: SKIN[4] if y < 2 else SKIN[3])
            fs['back'].fill(lambda x, y, ff: SKIN[3])
            for i in (1, 2, 3):
                fs = faces(img, self.cube(f'{pre}_f{i}a'))
                fs['front'].set(0, 0, SKIN[3])
                fs['front'].set(0, 2, SKIN[2])    # knuckle crease
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float walk = Math.min(1.0F, limbSwingAmount);
float sway = Mth.sin(t * 0.045F) * 0.05F;
float snap = twitch(t, 83.0F, 0.0F) * 0.3F - twitch(t, 131.0F, 40.0F) * 0.25F;
float breath = Mth.sin(t * 0.07F);

// restless fingers: slow flexing, one at a time twitching
float fl = 0.22F + Mth.sin(t * 0.06F) * 0.1F;
curl(this.rightFinger1, this.rightFinger1b, fl + twitch(t, 47.0F, 3.0F) * 0.6F, fl * 1.4F);
curl(this.rightFinger2, this.rightFinger2b, fl * 0.8F, fl * 1.2F + twitch(t, 61.0F, 20.0F) * 0.7F);
curl(this.rightFinger3, this.rightFinger3b, fl * 1.1F, fl * 1.5F);
curl(this.leftFinger1, this.leftFinger1b, fl, fl * 1.3F);
curl(this.leftFinger2, this.leftFinger2b, fl * 0.9F + twitch(t, 53.0F, 11.0F) * 0.6F, fl * 1.4F);
curl(this.leftFinger3, this.leftFinger3b, fl * 1.2F, fl * 1.1F);

if (pose == PEEK_LEFT || pose == PEEK_RIGHT || pose == RETREAT) {
    // behind a trunk: the head leans out sideways (~70 deg, ~10 px out), one hand grips the trunk edge
    float s = pose == PEEK_LEFT ? 1.0F : (pose == PEEK_RIGHT ? -1.0F : (entity.isLeftHanded() ? 1.0F : -1.0F));
    float e = pose == RETREAT ? 1.0F - ease(pt / 50.0F) : ease(pt / 26.0F);
    float g = pose == RETREAT ? 1.0F - ease((pt - 28.0F) / 24.0F) : ease((pt - 6.0F) / 14.0F);
    this.torso.zRot = 0.2F * s * e + sway * 0.3F;
    this.torso.xRot = 0.06F + breath * 0.015F;
    this.neck.zRot = 0.62F * s * e;
    this.neck.xRot = 0.12F;
    this.head.zRot = 0.5F * s * e + snap * s + Mth.sin(t * 0.037F) * 0.04F;
    this.head.yRot = yaw * 0.55F;
    this.head.xRot = pitch * 0.5F - 0.05F;
    this.jaw.xRot = 0.04F + Math.max(0.0F, Mth.sin(t * 0.021F)) * 0.08F;
    ModelPart arm = s > 0.0F ? this.leftArm : this.rightArm;
    ModelPart fore = s > 0.0F ? this.leftForearm : this.rightForearm;
    ModelPart hand = s > 0.0F ? this.leftHand : this.rightHand;
    // reach along the trunk side to its front corner (solved by IK offline: the trunk's near face is assumed
    // ~0.5 block in front of the entity) and wrap three long fingers around the edge
    arm.xRot = -1.45F * g + Mth.sin(t * 0.05F) * 0.01F;
    arm.yRot = -0.23F * s * g;
    arm.zRot = -0.35F * s * g;
    hand.yRot = -1.74F * s * g;
    hand.xRot = 0.2F * g;
    float k1 = 0.15F * g + 0.2F * (1.0F - g);
    float k2 = 1.5F * g + 0.25F * (1.0F - g);
    if (s > 0.0F) {
        curl(this.leftFinger1, this.leftFinger1b, k1 + twitch(t, 37.0F, 5.0F) * 0.4F * g, k2);
        curl(this.leftFinger2, this.leftFinger2b, k1 * 0.95F, k2 * 1.05F);
        curl(this.leftFinger3, this.leftFinger3b, k1 * 1.05F, k2 * 0.9F + twitch(t, 71.0F, 30.0F) * 0.5F * g);
    } else {
        curl(this.rightFinger1, this.rightFinger1b, k1, k2 * 0.9F + twitch(t, 71.0F, 30.0F) * 0.5F * g);
        curl(this.rightFinger2, this.rightFinger2b, k1 * 0.95F, k2 * 1.05F);
        curl(this.rightFinger3, this.rightFinger3b, k1 * 1.05F + twitch(t, 37.0F, 5.0F) * 0.4F * g, k2);
    }
} else if (pose == CRAWL) {
    // all fours, spider-like: belly low, elbows and knees high (limb angles solved by IK offline), head up,
    // jaw hanging open; diagonal limb pairs scuttle together
    float c = limbSwing * 1.25F;
    float a = 0.3F + Math.min(1.0F, limbSwingAmount * 1.6F) * 0.7F;
    float sc = Mth.sin(c);
    float cc = Mth.cos(c);
    this.hips.y = 18.0F - Math.abs(Mth.sin(c)) * 1.2F * a;
    this.hips.z = 3.0F;
    this.torso.xRot = 1.5F + Mth.sin(c * 2.0F) * 0.05F * a;
    this.torso.zRot = sc * 0.07F * a;
    this.neck.xRot = -1.15F;
    this.neck.zRot = -sc * 0.06F * a;
    this.head.xRot = -0.35F + pitch * 0.7F;
    this.head.yRot = yaw * 0.6F + Mth.sin(t * 1.3F) * 0.08F;
    this.head.zRot = Mth.sin(t * 0.8F) * 0.25F + snap;
    this.jaw.xRot = 0.85F + Mth.sin(t * 1.9F) * 0.12F;
    this.rightArm.xRot = 0.42F + sc * 0.45F * a;
    this.rightArm.yRot = 0.23F;
    this.rightArm.zRot = 2.12F + Math.max(0.0F, cc) * 0.25F * a;
    this.rightForearm.xRot = -1.96F;
    this.rightForearm.zRot = -0.27F - Math.max(0.0F, cc) * 0.3F * a;
    this.leftArm.xRot = 0.42F - sc * 0.45F * a;
    this.leftArm.yRot = -0.23F;
    this.leftArm.zRot = -2.12F - Math.max(0.0F, -cc) * 0.25F * a;
    this.leftForearm.xRot = -1.96F;
    this.leftForearm.zRot = 0.27F + Math.max(0.0F, -cc) * 0.3F * a;
    this.rightHand.xRot = 0.5F;
    this.leftHand.xRot = 0.5F;
    this.rightLeg.xRot = 0.36F - sc * 0.4F * a;
    this.rightLeg.yRot = -0.63F;
    this.rightLeg.zRot = 2.14F + Math.max(0.0F, -cc) * 0.25F * a;
    this.rightShin.xRot = -0.16F;
    this.rightShin.zRot = -1.87F - Math.max(0.0F, -cc) * 0.3F * a;
    this.leftLeg.xRot = 0.36F + sc * 0.4F * a;
    this.leftLeg.yRot = 0.63F;
    this.leftLeg.zRot = -2.14F - Math.max(0.0F, cc) * 0.25F * a;
    this.leftShin.xRot = -0.16F;
    this.leftShin.zRot = 1.87F + Math.max(0.0F, cc) * 0.3F * a;
} else {
    // STAND: towering, hunched, head tilted too far, swaying; slow stiff walk
    this.torso.xRot = 0.1F + breath * 0.02F;
    this.torso.zRot = sway;
    this.neck.xRot = 0.3F;
    this.neck.zRot = sway * 0.5F;
    this.head.xRot = -0.3F + pitch * 0.6F;
    this.head.yRot = yaw * 0.7F;
    this.head.zRot = 0.4F + Mth.sin(t * 0.029F) * 0.14F + snap;
    this.jaw.xRot = 0.06F + Math.max(0.0F, Mth.sin(t * 0.017F + 1.0F)) * 0.12F;
    float sw = Mth.sin(limbSwing * 0.45F);
    this.rightArm.zRot = 0.07F + Mth.sin(t * 0.04F) * 0.02F;
    this.leftArm.zRot = -0.07F - Mth.sin(t * 0.04F + 1.3F) * 0.02F;
    this.rightArm.xRot = Mth.sin(limbSwing * 0.45F + 0.6F) * 0.35F * walk;
    this.leftArm.xRot = -Mth.sin(limbSwing * 0.45F + 0.6F) * 0.35F * walk;
    this.rightForearm.xRot = -0.12F;
    this.leftForearm.xRot = -0.12F;
    this.rightLeg.xRot = sw * 0.55F * walk;
    this.leftLeg.xRot = -sw * 0.55F * walk;
    this.rightShin.xRot = Math.max(0.0F, -sw) * 0.8F * walk;
    this.leftShin.xRot = Math.max(0.0F, sw) * 0.8F * walk;
}

// wet hair hangs with gravity from wherever the head is
float px = this.hips.xRot + this.torso.xRot + this.neck.xRot + this.head.xRot;
float pz = this.hips.zRot + this.torso.zRot + this.neck.zRot + this.head.zRot;
this.hairBack.xRot = -px * 0.9F + 0.06F;
this.hairBack.zRot = -pz;
this.hairBack2.xRot = 0.06F + Mth.sin(t * 0.05F) * 0.03F;
this.hairL.xRot = -px * 0.8F;
this.hairL.zRot = -pz;
this.hairR.xRot = -px * 0.8F;
this.hairR.zRot = -pz;
'''
    helpers = HELPERS


SPEC = Nozoki


Nozoki.height = 2.6


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc_ground(cv):
        ground(cv, 2)

    def sc_trunk(cv):
        ground(cv, 2)
        trunk(cv, -0.5, 0.5, 4)

    rows = []
    ims = []
    for (yv, lab) in ((0, 'front'), (35, '3/4'), (90, 'side'), (180, 'back')):
        ims.append((lab, view(spec, tex, eyes, run, 2, 40, 40, yaw_view=yv, scene=sc_ground)))
    ims.append(('walk', view(spec, tex, eyes, run, 2, 40, 40, yaw_view=30, limb=(2.0, 0.8), scene=sc_ground)))
    ims.append(('twitch', view(spec, tex, eyes, run, 2, 40, 83.5, yaw_view=10, scene=sc_ground)))
    rows.append(('STAND (2)', ims))
    for pose, name, left in ((0, 'PEEK_LEFT (0)', False), (1, 'PEEK_RIGHT (1)', False)):
        ims = []
        for ptv in (0, 8, 16, 30, 70):
            ims.append((f'pt {ptv}', view(spec, tex, eyes, run, pose, ptv, 100 + ptv, yaw_view=0, scene=sc_trunk)))
        ims.append(('3/4 pt 60', view(spec, tex, eyes, run, pose, 60, 160, yaw_view=-40 if pose == 0 else 40, scene=sc_trunk)))
        ims.append(('top pt 60', view(spec, tex, eyes, run, pose, 60, 160, yaw_view=0, pitch_view=70, scene=sc_trunk)))
        rows.append((name + '  (trunk in front)', ims))
    ims = []
    for ptv in (0, 15, 30, 45, 60):
        ims.append((f'pt {ptv}', view(spec, tex, eyes, run, 4, ptv, 200 + ptv, yaw_view=0, left=True, scene=sc_trunk)))
    rows.append(('RETREAT (4) after a left peek (isLeftHanded)', ims))
    ims = []
    for k, ls in enumerate((0.0, 0.7, 1.4, 2.1)):
        ims.append((f'side ls {ls}', view(spec, tex, eyes, run, 3, 20, 30 + k, yaw_view=90, limb=(ls, 1.0), scene=sc_ground)))
    ims.append(('3/4', view(spec, tex, eyes, run, 3, 20, 30, yaw_view=30, limb=(0.5, 1.0), scene=sc_ground)))
    ims.append(('front', view(spec, tex, eyes, run, 3, 20, 30, yaw_view=0, pitch_view=4, limb=(0.5, 1.0), scene=sc_ground)))
    rows.append(('CRAWL (3)', ims))
    ims = []
    for (pose, ptv, lab) in ((0, 60, 'peek, flashlight'), (2, 40, 'stand'), (3, 20, 'crawl')):
        ims.append((lab, view(spec, tex, eyes, run, pose, ptv, 77, size=(400, 260), scene=sc_trunk if pose == 0 else sc_ground,
                              night=True, eye_override=(0.3, 1.62, 7.5), target=(0.0, 1.5, 0.0))))
    rows.append(('night, seen by the player 7 blocks away (flashlight)', ims))
    return rows
