"""御山様 OYAMASAMA - 1.2 x 4.2 blocks. The mountain itself: a giant, emaciated body with dark bark-like skin; a white
paper face cloth (顔布) with one painted red eye; dead cedar branches growing from the head like antlers, with
clumps of needles; a shimenawa rope with white shide around the neck; arms to the ankles.
Poses: 0 STAND, 1 WALK, 2 CRAWL, 3 REACH, 4 LOOM."""
import math

from common import ramp, pal, rng
from ent_model import Spec, Part, Cube
from ent_lib import hand, HELPERS
from ent_paint import Img, faces, fill_cube, material

BARK = ramp(['#0b0807', '#14100d', '#1d1713', '#271f19', '#322820', '#3e3228', '#4b3d31'], 7)
PAPER = ramp(['#6f695c', '#8c8676', '#a8a290', '#bdb7a4', '#ccc6b2', '#d8d2be'], 6)
STRAW = ramp(['#2a2213', '#43381f', '#5e4f2d', '#7a673c', '#94804c', '#a8945d'], 6)
NEEDLE = ramp(['#070d0b', '#0d1612', '#14211a', '#1c2c23', '#25382d', '#2f4538'], 6)
WOOD = ramp(['#1a140f', '#2a2018', '#3b2e23', '#4d3d2f', '#5e4c3b'], 5)
RED = pal('#4a0c0a', '#7a1512', '#a32019', '#c42c22')


class Oyamasama(Spec):
    id = 'oyamasama'
    java_name = 'OyamasamaModel'
    module = 'ent_oyamasama'
    tex_w = 128
    height = 4.2
    poses = [('STAND', 0), ('WALK', 1), ('CRAWL', 2), ('REACH', 3), ('LOOM', 4)]
    doc = """御山様 (oyamasama): the mountain's own body, 4.2 blocks tall (antlers reach ~5). The paper face cloth hangs
    from its brow with one painted red eye (faintly lit in the eyes layer); when it looms over you the cloth hangs
    away from the face and you see what is underneath. Dead cedar branches grow from the skull; a shimenawa with
    white shide is tied around the neck; the arms reach the ankles.
    Pose ids: 0 STAND, 1 WALK, 2 CRAWL, 3 REACH, 4 LOOM."""

    def build(self):
        hips = Part('hips', (0, -5, 0), cubes=[Cube((-4, -4, -2.5), (8, 5, 5), 'bark', tag='pelvis')])
        torso = Part('torso', (0, -3, 0), cubes=[Cube((-5, -18, -2.5), (10, 18, 5), 'bark', tag='chest')])
        rope = Part('rope', (0, -17.5, 0), cubes=[
            Cube((-4.5, -1.5, -4), (9, 2, 2), 'straw', tag='rope_f'), Cube((-4.5, -1.5, 2), (9, 2, 2), 'straw', tag='rope_b'),
            Cube((-5.5, -1.5, -3), (2, 2, 6), 'straw', tag='rope_r'), Cube((3.5, -1.5, -3), (2, 2, 6), 'straw', tag='rope_l'),
            Cube((-3.5, 0.5, -4.3), (2, 7, 0), 'shide', tag='shide_1'), Cube((1.5, 0.5, -4.3), (2, 7, 0), 'shide', tag='shide_2'),
            Cube((5.7, 0.5, -1), (0, 7, 2), 'shide', tag='shide_3'), Cube((-5.7, 0.5, -1), (0, 7, 2), 'shide', tag='shide_4')])
        neck = Part('neck', (0, -18, 0), cubes=[Cube((-1.5, -6, -1.5), (3, 6, 3), 'bark', tag='neck')])
        head = Part('head', (0, -6, 0), cubes=[Cube((-4, -9, -4), (8, 9, 8), 'bark', tag='head')])
        cloth = Part('cloth', (0, -9, -4.3), cubes=[Cube((-4, 0, 0), (8, 12, 0), 'paper', tag='cloth')])
        antlers = Part('antlers', (0, -9, 0.5))
        for side, pre in ((1, 'l'), (-1, 'r')):
            a1 = Part(f'antler_{pre}1', (side * 2.2, 0, 0), (-0.15, 0, side * 0.45),
                      cubes=[Cube((-0.5, -10, -0.5), (1, 10, 1), 'wood', tag=f'antler_{pre}1')])
            a2 = Part(f'antler_{pre}2', (0, -5, 0), (0.2, 0, side * 0.75), cubes=[Cube((-0.5, -5, -0.5), (1, 5, 1), 'wood', tag=f'antler_{pre}2')])
            a3 = Part(f'antler_{pre}3', (0, -10, 0), (-0.35, 0, -side * 0.35),
                      cubes=[Cube((-0.5, -7, -0.5), (1, 7, 1), 'wood', tag=f'antler_{pre}3'),
                             Cube((-1.5, -9, -1.5), (3, 3, 3), 'needle', tag=f'needles_{pre}')])
            a4 = Part(f'antler_{pre}4', (0, -4, 0), (0.1, 0, side * 0.9), cubes=[Cube((-0.5, -4, -0.5), (1, 4, 1), 'wood', tag=f'antler_{pre}4'),
                                                                               Cube((-1, -5, -1), (2, 2, 2), 'needle', tag=f'needles_{pre}b')])
            a3.add(a4)
            a1.add(a2, a3)
            antlers.add(a1)
        head.add(cloth, antlers)
        neck.add(head)
        torso.add(rope, neck)
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 6.4, -16.5, 0), cubes=[Cube((-1.5, -1, -1.5), (3, 18, 3), 'bark', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 17, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 16, 3), 'bark', tag=f'{pre}_lower')])
            h = hand(pre, side, palm=(4, 3, 2), seg=(5, 4), fingers=3, mat='bark', nail='claw', spread=1.3)
            h.pivot = (0, 16, 0)
            h.reset()
            fore.add(h)
            arm.add(fore)
            torso.add(arm)
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 2.5, 0, 0), cubes=[Cube((-2, 0, -2), (4, 14, 4), 'bark', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 14, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 13, 3), 'bark', tag=f'{pre}_shin'),
                                                        Cube((-2, 13, -5), (4, 2, 6), 'bark', tag=f'{pre}_foot')])
            leg.add(shin)
            hips.add(leg)
        hips.add(torso)
        self.root.add(hips)

    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        bark = material(BARK, 1201, base=3.6, amp=0.9, beta=0.8, grad=0.5, streak=True, jitter=0.25)
        mats = {'bark': bark, 'claw': bark, 'straw': material(STRAW, 1202, base=3.0, amp=0.8, grad=0.4),
                'shide': material(PAPER, 1203, base=4.0, amp=0.3, grad=0.4), 'paper': material(PAPER, 1204, base=4.2, amp=0.35, grad=0.5),
                'wood': material(WOOD, 1205, base=2.4, amp=0.7, grad=0.2, streak=True), 'needle': material(NEEDLE, 1206, base=2.6, amp=0.9, jitter=0.5)}
        for p in self.root.walk():
            for c in p.cubes:
                fill_cube(img, c, mats[c.mat])
        r = rng(1210)
        # bark cracks: dark vertical splits and pale lichen flecks on the big limbs
        for p in self.root.walk():
            for c in p.cubes:
                if c.mat != 'bark':
                    continue
                fs = faces(img, c)
                for n in ('front', 'back', 'left', 'right'):
                    fc = fs[n]
                    if fc.w < 2 or fc.h < 4:
                        continue
                    for _ in range(max(1, fc.w * fc.h // 30)):
                        x0, y0 = int(r.integers(0, fc.w)), int(r.integers(0, fc.h - 2))
                        for k in range(int(r.integers(2, 5))):
                            fc.set(x0, y0 + k, BARK[0])
                    for _ in range(max(1, fc.w * fc.h // 90)):
                        fc.set(int(r.integers(0, fc.w)), int(r.integers(0, fc.h)), '#4e5446')
        # ribs on the emaciated chest
        ch = faces(img, self.cube('chest'))
        for n in ('front', 'left', 'right'):
            fc = ch[n]
            for y in range(3, 12, 2):
                for x in range(fc.w):
                    if not (n == 'front' and x in (4, 5)):
                        fc.dl(x, y, -0.1)
                        fc.dl(x, y - 1, 0.05)
        ch['front'].fill(lambda x, y, ff: BARK[1] if y >= 13 and x in (2, 7) else None)
        # ---- the face under the cloth: eye pits and a mouth torn far too wide, teeth like splinters
        hf = faces(img, self.cube('head'))
        f = hf['front']
        f.rows(["........",
                "........",
                ".aa..aa.",
                ".ab..ba.",
                "........",
                ".mmmmmm.",
                "mtmttmtm",
                "mmmmmmmm",
                ".mtmmtm."], {'a': '#020101', 'b': '#100604', 'm': '#030202', 't': '#8c8270'})
        fe = faces(eyes, self.cube('head'))['front']
        fe.set(2, 3, '#3a0c08')
        fe.set(5, 3, '#3a0c08')
        # ---- face cloth: aged washi, rain stains, one painted red eye, faint brushed characters, torn hem
        cl = faces(img, self.cube('cloth'))
        ce = faces(eyes, self.cube('cloth'))
        eye_rows = ["..rrrr..",
                    ".rRppRr.",
                    "rRpkkpRr",
                    ".rRppRr.",
                    "..rrrr.."]
        for n in ('front', 'back'):
            fc = cl[n]
            for y in range(fc.h):
                for x in range(fc.w):
                    if r.random() < 0.08:
                        fc.dl(x, y, -0.06)
            if n == 'front':
                fc.rows(eye_rows, {'r': RED[1], 'R': RED[2], 'p': RED[3], 'k': '#1a0605'}, 0, 3)
                for (sx, sy) in ((1, 9), (2, 9), (1, 10), (6, 0), (6, 1), (5, 10), (6, 10)):
                    fc.set(sx, sy, '#2a2622')
                for k in range(3):                     # red ink ran down from the eye
                    fc.blend(3, 8 + k, RED[1], 0.6 - k * 0.15)
                ce['front'].rows(eye_rows, {'r': (40, 6, 4, 255), 'R': (70, 10, 6, 255), 'p': (95, 16, 10, 255), 'k': (20, 2, 2, 255)}, 0, 3)
            for x in range(fc.w):
                if r.random() < 0.45:
                    fc.clear(x, fc.h - 1)
            fc.blend(5, 6, '#7d6a4a', 0.4)
        # ---- shide: white zigzag paper
        for tag in ('shide_1', 'shide_2', 'shide_3', 'shide_4'):
            fs = faces(img, self.cube(tag))
            for n, fc in fs.items():
                if fc.w <= 0 or fc.h <= 0:
                    continue
                for y in range(fc.h):
                    off = (y // 2) % 2
                    for x in range(fc.w):
                        if (x == 0 and off == 1 and y % 2 == 0) or (x == fc.w - 1 and off == 0 and y % 2 == 1):
                            fc.clear(x, y)
                        else:
                            fc.set(x, y, PAPER[5] if y % 2 == 0 else PAPER[3])
        # rope twist
        for tag in ('rope_f', 'rope_b', 'rope_l', 'rope_r'):
            fs = faces(img, self.cube(tag))
            for n, fc in fs.items():
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x + y * 2) % 4 == 0:
                            fc.dl(x, y, -0.1)
        # claws: dark hooked tips
        for pre in ('right', 'left'):
            for i in (1, 2, 3):
                fs = faces(img, self.cube(f'{pre}_f{i}b'))
                for n in ('front', 'back', 'left', 'right'):
                    for y in (2, 3):
                        fs[n].set(0, y, '#070505')
                fs['bottom'].set(0, 0, '#030202')
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float walk = Math.min(1.0F, limbSwingAmount);
float snap = twitch(t, 127.0F, 31.0F);
float fl = 0.2F + Mth.sin(t * 0.04F) * 0.12F;
curl(this.rightFinger1, this.rightFinger1b, fl, fl * 1.4F + twitch(t, 41.0F, 6.0F) * 0.5F);
curl(this.rightFinger2, this.rightFinger2b, fl * 1.1F, fl * 1.2F);
curl(this.rightFinger3, this.rightFinger3b, fl * 0.9F, fl * 1.5F);
curl(this.leftFinger1, this.leftFinger1b, fl, fl * 1.3F);
curl(this.leftFinger2, this.leftFinger2b, fl * 1.2F, fl * 1.1F + twitch(t, 67.0F, 29.0F) * 0.5F);
curl(this.leftFinger3, this.leftFinger3b, fl * 0.8F, fl * 1.4F);
this.head.yRot = yaw * 0.5F;
if (pose == WALK) {
    // slow, heavy, lurching from side to side; the long arms swing late, dragging
    float c = limbSwing * 0.42F;
    float a = 0.35F + walk * 0.65F;
    float sc = Mth.sin(c);
    this.hips.zRot = sc * 0.07F * a;
    this.hips.y = -5.0F + Math.abs(Mth.cos(c)) * 1.2F * a;
    this.torso.xRot = 0.18F;
    this.torso.zRot = -sc * 0.09F * a;
    this.neck.xRot = 0.2F;
    this.head.xRot = -0.3F + pitch * 0.4F + Mth.sin(c * 2.0F) * 0.04F;
    this.head.zRot = 0.18F + snap * 0.25F;
    this.rightLeg.xRot = sc * 0.55F * a;
    this.leftLeg.xRot = -sc * 0.55F * a;
    this.rightShin.xRot = Math.max(0.0F, -sc) * 0.8F * a;
    this.leftShin.xRot = Math.max(0.0F, sc) * 0.8F * a;
    this.rightArm.xRot = -0.18F + Mth.sin(c - 0.9F) * 0.35F * a;
    this.leftArm.xRot = -0.18F - Mth.sin(c - 0.9F) * 0.35F * a;
    this.rightArm.zRot = 0.05F;
    this.leftArm.zRot = -0.05F;
    this.rightForearm.xRot = -0.1F;
    this.leftForearm.xRot = -0.1F;
} else if (pose == CRAWL) {
    // on all fours like something that has forgotten how to stand: limbs splayed (angles solved by IK offline),
    // head low, looking up at you
    float c = limbSwing * 0.55F;
    float a = 0.3F + walk * 0.7F;
    float sc = Mth.sin(c);
    float cc = Mth.cos(c);
    this.hips.y = 6.0F + Math.abs(cc) * 0.8F * a;
    this.hips.z = 9.0F;
    this.torso.xRot = 1.4F;
    this.torso.zRot = sc * 0.05F * a;
    this.neck.xRot = -0.85F;
    this.head.xRot = -0.55F + pitch * 0.6F;
    this.head.zRot = Mth.sin(t * 0.3F) * 0.15F + snap * 0.3F;
    this.rightArm.xRot = -0.26F + sc * 0.3F * a;
    this.rightArm.yRot = 0.47F;
    this.rightArm.zRot = 2.07F;
    this.rightForearm.xRot = -1.41F;
    this.rightForearm.zRot = -0.02F - Math.max(0.0F, cc) * 0.2F * a;
    this.leftArm.xRot = -0.26F - sc * 0.3F * a;
    this.leftArm.yRot = -0.47F;
    this.leftArm.zRot = -2.07F;
    this.leftForearm.xRot = -1.41F;
    this.leftForearm.zRot = 0.02F + Math.max(0.0F, -cc) * 0.2F * a;
    this.rightHand.xRot = 0.6F;
    this.leftHand.xRot = 0.6F;
    this.rightLeg.xRot = -0.60F - sc * 0.3F * a;
    this.rightLeg.yRot = 0.49F;
    this.rightLeg.zRot = 0.63F;
    this.rightShin.xRot = 1.72F;
    this.rightShin.zRot = 0.02F;
    this.leftLeg.xRot = -0.60F + sc * 0.3F * a;
    this.leftLeg.yRot = -0.49F;
    this.leftLeg.zRot = -0.63F;
    this.leftShin.xRot = 1.72F;
    this.leftShin.zRot = -0.02F;
} else if (pose == REACH) {
    // one arm comes forward, fingers opening; the head cocks, considering you
    float e = ease(pt / 16.0F);
    this.torso.xRot = 0.22F * e;
    this.neck.xRot = 0.15F * e;
    this.head.xRot = (-0.35F + pitch * 0.5F) * e;
    this.head.zRot = 0.35F * e + Mth.sin(t * 0.05F) * 0.05F + snap * 0.3F;
    this.rightArm.xRot = (-1.55F - this.torso.xRot) * e + Mth.sin(t * 0.07F) * 0.03F;
    this.rightArm.zRot = 0.05F;
    this.rightForearm.xRot = -0.1F * e;
    curl(this.rightFinger1, this.rightFinger1b, -0.2F * e, 0.35F);
    curl(this.rightFinger2, this.rightFinger2b, -0.1F * e, 0.3F + twitch(t, 23.0F, 0.0F) * 0.6F);
    curl(this.rightFinger3, this.rightFinger3b, -0.25F * e, 0.35F);
    this.leftArm.xRot = -this.torso.xRot + 0.05F;
    this.leftArm.zRot = -0.06F;
} else if (pose == LOOM) {
    // bends down over you, arms opening to either side; the face cloth hangs away from what is underneath
    float e = ease(pt / 30.0F);
    this.torso.xRot = 1.25F * e + Mth.sin(t * 0.06F) * 0.02F;
    this.neck.xRot = 0.55F * e;
    this.head.xRot = (-0.55F + pitch * 0.4F) * e;
    this.head.zRot = 0.25F * e + snap * 0.25F;
    this.rightArm.xRot = (-0.75F - this.torso.xRot) * e;
    this.leftArm.xRot = (-0.75F - this.torso.xRot) * e;
    this.rightArm.zRot = 0.75F * e;
    this.leftArm.zRot = -0.75F * e;
    this.rightForearm.xRot = -0.35F * e;
    this.leftForearm.xRot = -0.35F * e;
    this.rightLeg.xRot = -0.25F * e;
    this.leftLeg.xRot = -0.25F * e;
    this.rightShin.xRot = 0.35F * e;
    this.leftShin.xRot = 0.35F * e;
} else {
    // STAND: towering and almost still; a slow sway, the head tilting by degrees, a crack now and then
    this.torso.zRot = Mth.sin(t * 0.021F) * 0.03F;
    this.torso.xRot = 0.08F;
    this.neck.xRot = 0.15F;
    this.head.xRot = -0.2F + pitch * 0.4F;
    this.head.zRot = 0.22F + Mth.sin(t * 0.017F) * 0.15F + snap * 0.35F;
    this.rightArm.xRot = -0.08F + Mth.sin(t * 0.03F) * 0.02F;
    this.leftArm.xRot = -0.08F + Mth.sin(t * 0.03F + 1.4F) * 0.02F;
    this.rightArm.zRot = 0.04F;
    this.leftArm.zRot = -0.04F;
}
// the cloth and the shide hang from wherever the head and neck are, swaying
float px = this.hips.xRot + this.torso.xRot + this.neck.xRot + this.head.xRot;
float pz = this.hips.zRot + this.torso.zRot + this.neck.zRot + this.head.zRot;
this.cloth.xRot = -px + Mth.sin(t * 0.047F) * 0.07F - 0.04F;
this.cloth.zRot = -pz;
if (pose == LOOM) {
    // once it is bent over you the cloth slides off to one side: you see what is underneath
    float k = ease((pt - 22.0F) / 16.0F);
    this.cloth.zRot += 1.35F * k;
    this.cloth.xRot -= 0.5F * k;
}
this.rope.zRot = Mth.sin(t * 0.03F) * 0.03F;
this.antlers.zRot = Mth.sin(t * 0.019F) * 0.02F;
'''
    helpers = HELPERS


SPEC = Oyamasama


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc(cv):
        ground(cv, 3)
    o = spec.height * 0.62
    rows = []
    ims = [(lab, view(spec, tex, eyes, run, 0, 40, a, yaw_view=yv, scene=sc, ortho=o * 1.15, center_y=2.4)) for lab, a, yv in
           (('front', 10, 0), ('3/4', 30, 35), ('side', 50, 90), ('back', 70, 180))]
    rows.append(('STAND (0)', ims))
    ims = [(f'ls {l}', view(spec, tex, eyes, run, 1, 40, 20, yaw_view=70, limb=(l, 0.9), scene=sc, ortho=o * 1.15, center_y=2.4))
           for l in (0.0, 2.0, 4.0, 6.0)]
    rows.append(('WALK (1)', ims))
    ims = [(f'ls {l}', view(spec, tex, eyes, run, 2, 20, 20, yaw_view=90, limb=(l, 1.0), scene=sc, ortho=o * 0.8, center_y=1.2))
           for l in (0.0, 2.5)]
    ims.append(('3/4', view(spec, tex, eyes, run, 2, 20, 20, yaw_view=35, pitch_view=15, limb=(1.0, 1.0), scene=sc, ortho=o * 0.8, center_y=1.2)))
    ims.append(('front', view(spec, tex, eyes, run, 2, 20, 20, yaw_view=0, pitch_view=5, limb=(1.0, 1.0), scene=sc, ortho=o * 0.8, center_y=1.2)))
    rows.append(('CRAWL (2)', ims))
    ims = [(f'pt {p}', view(spec, tex, eyes, run, 3, p, 50 + p, yaw_view=50, scene=sc, ortho=o * 1.15, center_y=2.4)) for p in (0, 8, 30)]
    rows.append(('REACH (3)', ims))
    ims = [(f'pt {p}', view(spec, tex, eyes, run, 4, p, 80 + p, yaw_view=70, scene=sc, ortho=o * 1.15, center_y=2.4)) for p in (0, 15, 40)]
    ims.append(('front', view(spec, tex, eyes, run, 4, 40, 120, yaw_view=10, scene=sc, ortho=o * 1.15, center_y=2.4)))
    rows.append(('LOOM (4)', ims))
    ims = [(lab, view(spec, tex, eyes, run, pose, ptv, 30, size=(380, 300), scene=sc, night=True, eye_override=eo, target=tg))
           for lab, pose, ptv, eo, tg in (('standing in the tunnel, 9 blocks', 0, 30, (0.3, 1.62, 9.0), (0.0, 2.4, 0.0)),
                                          ('looming over you', 4, 40, (0.0, 1.62, 2.6), (0.0, 2.3, 0.0)),
                                          ('crawling at you', 2, 20, (0.4, 1.62, 5.0), (0.0, 1.0, 0.0)))]
    rows.append(('night (flashlight)', ims))
    return rows
