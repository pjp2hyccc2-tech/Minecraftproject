"""小屋主 KOYANUSHI - 0.7 x 1.8 blocks. Emaciated old thing in filthy white underclothes, grey skin over ribs, bald with
a few long grey hairs, sunken eye sockets with tiny eyes, long yellow nails.
Poses: 0 SLEEP, 1 WAKE, 2 CHASE, 3 FACE_WALL, 4 STAND."""
from common import ramp, pal, rng
from ent_model import Spec, Part, Cube
from ent_lib import hand, HELPERS
from ent_paint import Img, faces, fill_cube, material

SKIN = ramp(['#24231f', '#36352f', '#4b4941', '#605d54', '#757167', '#888479', '#99958a'], 7)
RAG = ramp(['#3b372e', '#555043', '#6f6957', '#88816c', '#9e977f', '#ada68d'], 6)
NAIL = pal('#5c4f20', '#806e2c', '#a08b3a', '#bba653')
GREYHAIR = pal('#5a5852', '#77746c', '#939087')
MOUTH = pal('#080505', '#170c0a', '#291613')


class Koyanushi(Spec):
    id = 'koyanushi'
    java_name = 'KoyanushiModel'
    module = 'ent_koyanushi'
    tex_w = 64
    height = 1.8
    poses = [('SLEEP', 0), ('WAKE', 1), ('CHASE', 2), ('FACE_WALL', 3), ('STAND', 4)]
    doc = """小屋主 (koyanushi): the owner of the hut. Sleeps on its back along its facing (feet towards the facing);
    WAKE sits it up over 40 ticks in jerks with the head snapping sideways; CHASE is a hunched scrabbling run with
    the arms reaching; FACE_WALL sits it facing the wall, rocking, knocking its forehead now and then.
    Pose ids: 0 SLEEP, 1 WAKE, 2 CHASE, 3 FACE_WALL, 4 STAND."""

    def build(self):
        hips = Part('hips', (0, 12, 0), cubes=[Cube((-2.5, -2, -1.5), (5, 3, 3), 'rag', tag='loincloth')])
        torso = Part('torso', (0, -2, 0), cubes=[Cube((-2.5, -8, -1.5), (5, 8, 3), 'skin', tag='chest'),
                                                 Cube((-2.5, -8, -1.5), (5, 6, 3), 'rag', grow=0.2, tag='shirt')])
        neck = Part('neck', (0, -8, 0), cubes=[Cube((-1, -3, -1), (2, 3, 2), 'skin', tag='neck')])
        head = Part('head', (0, -3, 0), cubes=[Cube((-2.5, -5, -2.5), (5, 4, 5), 'head', tag='cranium'),
                                               Cube((-2, -1.2, -2), (4, 1, 4), 'mouth', tag='mouth')])
        jaw = Part('jaw', (0, -1, 2), cubes=[Cube((-2.5, 0, -4.5), (5, 2, 5), 'skin', tag='jaw')])
        hair_a = Part('hair_a', (-2.3, -4.6, 1.0), cubes=[Cube((-0.5, 0, 0), (1, 9, 0), 'grey', tag='hair_a')])
        hair_b = Part('hair_b', (1.9, -4.9, 2.0), cubes=[Cube((-0.5, 0, 0), (1, 12, 0), 'grey', tag='hair_b')])
        hair_c = Part('hair_c', (0.4, -5.0, 2.6), cubes=[Cube((-0.5, 0, 0), (1, 7, 0), 'grey', tag='hair_c')])
        head.add(jaw, hair_a, hair_b, hair_c)
        neck.add(head)
        arms = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 3.4, -7.5, 0), cubes=[Cube((-1, -0.5, -1), (2, 8, 2), 'skin', tag=f'{pre}_upper')])
            fore = Part(f'{pre}_forearm', (0, 7.5, 0), cubes=[Cube((-1, 0, -1), (2, 7, 2), 'skin', tag=f'{pre}_lower')])
            h = hand(pre, side, palm=(3, 2, 1), seg=(3, 3), fingers=3, mat='skin', nail='nail')
            h.pivot = (0, 7, 0)
            h.reset()
            fore.add(h)
            arm.add(fore)
            arms.append(arm)
        torso.add(neck, *arms)
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.4, 0, 0), cubes=[Cube((-1, 0, -1), (2, 6, 2), 'skin', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 6, 0), cubes=[Cube((-1, 0, -1), (2, 6, 2), 'skin', tag=f'{pre}_shin'),
                                                       Cube((-1, 5, -3), (2, 1, 2), 'skin', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        hips.add(torso, *legs)
        self.root.add(hips)

    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        skin = material(SKIN, 1101, base=4.0, amp=0.6, grad=0.7)
        rag = material(RAG, 1102, base=3.4, amp=0.7, grad=1.0)
        mats = {'skin': skin, 'rag': rag, 'head': material(SKIN, 1103, base=4.2, amp=0.5, grad=0.3),
                'nail': skin, 'grey': material(GREYHAIR, 1104, base=1.2, amp=0.6, grad=0.0, top_lift=0),
                'mouth': material(MOUTH, 1105, base=1.0, amp=0.4, grad=0.0, top_lift=0)}
        for p in self.root.walk():
            for c in p.cubes:
                fill_cube(img, c, mats[c.mat])
        r = rng(1110)
        # ---- face: bald brow, deep sockets with tiny eyes, collapsed nose, toothless mouth
        cr = faces(img, self.cube('cranium'))
        f = cr['front']
        f.fill(lambda x, y, ff: SKIN[5] if y == 0 else SKIN[4])
        for (sx, sy) in ((0, 1), (1, 1), (0, 2), (1, 2), (3, 1), (4, 1), (3, 2), (4, 2)):
            f.set(sx, sy, '#0d0c0a')
        f.set(1, 2, '#2a2620')           # tiny eyes glinting low in the sockets
        f.set(3, 2, '#2a2620')
        f.set(2, 2, SKIN[3])
        f.set(2, 3, '#1c1a16')           # nose: just a hole
        f.set(0, 3, SKIN[2])
        f.set(4, 3, SKIN[2])
        ef = faces(eyes, self.cube('cranium'))['front']
        ef.set(1, 2, '#d8d0a0')
        ef.set(3, 2, '#d8d0a0')
        jf = faces(img, self.cube('jaw'))
        jf['front'].fill(lambda x, y, ff: (MOUTH[0] if 0 < x < 4 else SKIN[2]) if y == 0 else SKIN[3])
        jf['front'].set(1, 0, '#8a7f55')     # one rotten tooth
        jf['top'].fill(lambda x, y, ff: MOUTH[1])
        cb = cr['bottom']
        cb.fill(lambda x, y, ff: MOUTH[0] if 0 < x < 4 and y > 0 else SKIN[3])
        # age spots on the bald scalp
        for n in ('top', 'back', 'left', 'right'):
            fc = cr[n]
            for _ in range(3):
                fc.set(int(r.integers(0, fc.w)), int(r.integers(0, fc.h)), SKIN[2])
        # ---- ribs and sternum; the rag shirt full of holes
        ch = faces(img, self.cube('chest'))
        for n in ('front', 'left', 'right', 'back'):
            fc = ch[n]
            for y in (1, 3, 5):
                for x in range(fc.w):
                    if not (n == 'front' and x == 2):
                        fc.dl(x, y, -0.12)
            for y in (0, 2, 4):
                for x in range(fc.w):
                    fc.dl(x, y, 0.04)
        ch['front'].fill(lambda x, y, ff: SKIN[2] if y >= 6 and x in (1, 3) else None)   # sunken belly
        sh = faces(img, self.cube('shirt'))
        for n, fc in sh.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    if r.random() < 0.22 or (n == 'front' and x == 2 and y < 3):
                        fc.clear(x, y)
                    elif r.random() < 0.18:
                        fc.blend(x, y, '#4a3a22', 0.55)        # old stains
            if n in ('front', 'back', 'left', 'right'):
                for x in range(fc.w):
                    if r.random() < 0.6:
                        fc.clear(x, fc.h - 1)                  # ragged hem
        lc = faces(img, self.cube('loincloth'))
        for n, fc in lc.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    if r.random() < 0.2:
                        fc.blend(x, y, '#4a3a22', 0.5)
        # ---- long yellow nails, knobbly knuckles, filthy feet
        for pre in ('right', 'left'):
            for i in (1, 2, 3):
                fs = faces(img, self.cube(f'{pre}_f{i}b'))
                for n in ('front', 'back', 'left', 'right'):
                    fs[n].set(0, 1, NAIL[2])
                    fs[n].set(0, 2, NAIL[3] if n == 'front' else NAIL[1])
                fs['bottom'].set(0, 0, NAIL[0])
                fa = faces(img, self.cube(f'{pre}_f{i}a'))
                fa['front'].set(0, 0, SKIN[5])
            fs = faces(img, self.cube(f'{pre}_thumb'))
            fs['front'].set(0, fs['front'].h - 1, NAIL[2])
            for tag in (f'{pre}_foot', f'{pre}_shin'):
                fs = faces(img, self.cube(tag))
                for n, fc in fs.items():
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if tag.endswith('foot') or y > fc.h - 3:
                                if r.random() < 0.6:
                                    fc.blend(x, y, '#1e1810', 0.6)
            for tag in (f'{pre}_upper', f'{pre}_thigh'):
                fs = faces(img, self.cube(tag))
                for n in ('front', 'back', 'left', 'right'):
                    fs[n].dl(0, fs[n].h - 1, 0.06)
                    fs[n].dl(1, fs[n].h - 1, 0.06)
        for tag in ('hair_a', 'hair_b', 'hair_c'):
            fs = faces(img, self.cube(tag))
            for n in ('front', 'back'):
                fc = fs[n]
                for y in range(fc.h):
                    if (y * 7 + len(tag)) % 5 == 0:
                        fc.clear(0, y)
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float walk = Math.min(1.0F, limbSwingAmount);
float snap = twitch(t, 67.0F, 9.0F) - twitch(t, 109.0F, 44.0F);
float breath = Mth.sin(t * 0.08F);
float fl = 0.3F + Mth.sin(t * 0.07F) * 0.15F;
curl(this.rightFinger1, this.rightFinger1b, fl, fl + twitch(t, 37.0F, 4.0F) * 0.7F);
curl(this.rightFinger2, this.rightFinger2b, fl * 1.2F, fl * 1.1F);
curl(this.rightFinger3, this.rightFinger3b, fl * 0.9F, fl * 1.4F);
curl(this.leftFinger1, this.leftFinger1b, fl * 1.1F, fl * 1.2F);
curl(this.leftFinger2, this.leftFinger2b, fl, fl * 1.3F + twitch(t, 53.0F, 21.0F) * 0.7F);
curl(this.leftFinger3, this.leftFinger3b, fl * 0.8F, fl);
if (pose == SLEEP || pose == WAKE) {
    // on its back along its facing, feet towards the facing; WAKE sits it up in jerks over 40 ticks
    float p = pose == WAKE ? jerky(pt / 40.0F, 5.0F) : 0.0F;
    this.hips.y = 22.0F;
    this.hips.z = -3.0F + p * 1.5F;
    this.rightLeg.xRot = -1.5708F;
    this.leftLeg.xRot = -1.5708F;
    this.rightLeg.zRot = 0.08F;
    this.leftLeg.zRot = -0.08F;
    this.torso.xRot = -1.5708F + p * 1.85F;
    this.torso.zScale = 1.0F + Math.max(0.0F, breath) * 0.08F * (1.0F - p);
    this.neck.xRot = 0.1F - p * 0.2F;
    this.head.xRot = -0.15F + p * 0.3F;
    this.head.yRot = 0.55F * (1.0F - p) + yaw * p;
    this.jaw.xRot = 0.35F + Mth.sin(t * 0.08F) * 0.1F;
    this.rightArm.zRot = 0.18F;
    this.leftArm.zRot = -0.18F;
    if (pose == WAKE) {
        // arms shove it up off the floor, the head snaps from side to side
        // hands stay on the floor: arm pitch in world terms goes from along the body (-90 deg) to planted in front
        float push = Mth.sin(Mth.clamp(pt / 40.0F, 0.0F, 1.0F) * Mth.PI);
        float world = -1.5708F + p * 0.6F;
        this.rightArm.xRot = world - this.torso.xRot;
        this.leftArm.xRot = world - this.torso.xRot;
        this.rightArm.zRot = 0.18F + push * 0.55F;
        this.leftArm.zRot = -0.18F - push * 0.55F;
        this.rightForearm.xRot = -0.25F * push;
        this.leftForearm.xRot = -0.25F * push;
        int beat = (int) (pt / 6.0F);
        float side = beat % 3 == 0 ? 0.7F : (beat % 3 == 1 ? -0.55F : 0.15F);
        this.head.zRot = side * (pt < 40.0F ? 1.0F : 0.3F);
        this.jaw.xRot = 0.2F + p * 0.5F;
        if (pt > 40.0F) {
            this.head.zRot = 0.3F + snap * 0.4F;
            this.head.xRot = 0.1F + pitch * 0.6F;
        }
    } else {
        this.rightForearm.xRot = -0.1F;
        this.leftForearm.xRot = -0.1F;
    }
} else if (pose == CHASE) {
    // hunched scrabbling run, arms reaching for you, mouth wide
    float rise = ease(pt / 6.0F);
    float c = limbSwing * 1.1F;
    float a = 0.4F + Math.min(1.0F, limbSwingAmount * 1.4F) * 0.6F;
    this.hips.y = 22.0F - 10.0F * rise;
    this.torso.xRot = 0.8F + Mth.sin(c * 2.0F) * 0.05F;
    this.neck.xRot = -0.15F;
    this.head.xRot = -0.65F + pitch * 0.6F;
    this.head.yRot = yaw * 0.6F;
    this.head.zRot = Mth.sin(t * 1.9F) * 0.2F + snap * 0.3F;
    this.jaw.xRot = 0.75F + Mth.sin(t * 2.7F) * 0.1F;
    this.rightArm.xRot = -1.55F + Mth.sin(c * 1.2F) * 0.45F * a;
    this.leftArm.xRot = -1.55F - Mth.sin(c * 1.2F + 0.5F) * 0.45F * a;
    this.rightArm.zRot = 0.2F;
    this.leftArm.zRot = -0.2F;
    this.rightForearm.xRot = -0.3F;
    this.leftForearm.xRot = -0.3F;
    curl(this.rightFinger1, this.rightFinger1b, 0.7F, 0.9F);
    curl(this.rightFinger2, this.rightFinger2b, 0.8F, 1.0F);
    curl(this.rightFinger3, this.rightFinger3b, 0.7F, 0.8F);
    curl(this.leftFinger1, this.leftFinger1b, 0.7F, 0.9F);
    curl(this.leftFinger2, this.leftFinger2b, 0.8F, 1.0F);
    curl(this.leftFinger3, this.leftFinger3b, 0.7F, 0.8F);
    this.rightLeg.xRot = Mth.sin(c) * 1.0F * a - 0.45F;
    this.leftLeg.xRot = -Mth.sin(c) * 1.0F * a - 0.45F;
    this.rightShin.xRot = Math.max(0.0F, -Mth.sin(c)) * 1.2F * a + 0.35F;
    this.leftShin.xRot = Math.max(0.0F, Mth.sin(c)) * 1.2F * a + 0.35F;
} else if (pose == FACE_WALL) {
    // sitting with the knees up, facing the wall, rocking; the forehead knocks the wall now and then
    this.hips.y = 21.5F;
    this.rightLeg.xRot = -2.1F;
    this.leftLeg.xRot = -2.1F;
    this.rightLeg.zRot = 0.15F;
    this.leftLeg.zRot = -0.15F;
    this.rightShin.xRot = 2.25F;
    this.leftShin.xRot = 2.25F;
    float rock = Mth.sin(t * 0.09F);
    this.torso.xRot = 0.35F + rock * 0.2F;
    this.neck.xRot = 0.25F;
    this.head.xRot = 0.3F + twitch(t, 89.0F, 0.0F) * 0.5F;
    this.head.zRot = 0.12F + snap * 0.2F;
    this.rightArm.xRot = -1.05F - rock * 0.1F;
    this.leftArm.xRot = -1.05F - rock * 0.1F;
    this.rightArm.zRot = -0.25F;
    this.leftArm.zRot = 0.25F;
    this.rightForearm.xRot = -0.6F;
    this.leftForearm.xRot = -0.6F;
} else {
    // STAND: hunched, ribs heaving, head cocked, swaying; a shambling walk
    float sw = Mth.sin(limbSwing * 0.55F);
    this.torso.xRot = 0.42F + breath * 0.03F;
    this.torso.xScale = 1.0F + Math.max(0.0F, breath) * 0.04F;
    this.torso.zRot = Mth.sin(t * 0.037F) * 0.06F;
    this.neck.xRot = 0.15F;
    this.head.xRot = -0.45F + pitch * 0.6F;
    this.head.yRot = yaw * 0.7F;
    this.head.zRot = 0.35F + Mth.sin(t * 0.027F) * 0.12F + snap * 0.35F;
    this.jaw.xRot = 0.15F + Math.max(0.0F, Mth.sin(t * 0.05F)) * 0.15F;
    this.rightArm.xRot = -this.torso.xRot + 0.1F + Mth.sin(limbSwing * 0.55F + 0.5F) * 0.3F * walk;
    this.leftArm.xRot = -this.torso.xRot + 0.1F - Mth.sin(limbSwing * 0.55F + 0.5F) * 0.3F * walk;
    this.rightArm.zRot = 0.12F;
    this.leftArm.zRot = -0.12F;
    this.rightForearm.xRot = -0.2F;
    this.leftForearm.xRot = -0.2F;
    this.rightLeg.xRot = sw * 0.5F * walk - 0.15F;
    this.leftLeg.xRot = -sw * 0.5F * walk - 0.15F;
    this.rightShin.xRot = Math.max(0.0F, -sw) * 0.7F * walk + 0.25F;
    this.leftShin.xRot = Math.max(0.0F, sw) * 0.7F * walk + 0.25F;
}
// the few long hairs hang down
float px = this.hips.xRot + this.torso.xRot + this.neck.xRot + this.head.xRot;
float pz = this.hips.zRot + this.torso.zRot + this.neck.zRot + this.head.zRot;
this.hairA.xRot = -px;
this.hairA.zRot = -pz;
this.hairB.xRot = -px + 0.05F;
this.hairB.zRot = -pz;
this.hairC.xRot = -px + 0.1F;
this.hairC.zRot = -pz;
'''
    helpers = HELPERS


SPEC = Koyanushi


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc(cv):
        ground(cv, 2, tex_name='block/dark_planks')
    rows = []
    ims = [(lab, view(spec, tex, eyes, run, 0, 40, a, yaw_view=yv, pitch_view=pv, scene=sc)) for lab, a, yv, pv in
           (('side', 10, 90, 10), ('3/4 above', 20, 40, 40), ('top', 30, 0, 80))]
    rows.append(('SLEEP (0)', ims))
    ims = [(f'pt {p}', view(spec, tex, eyes, run, 1, p, 100 + p, yaw_view=70, scene=sc)) for p in (0, 8, 16, 24, 32, 40, 60)]
    rows.append(('WAKE (1)', ims))
    ims = [(f'ls {l}', view(spec, tex, eyes, run, 2, 20, 30, yaw_view=90, limb=(l, 1.0), scene=sc)) for l in (0.0, 1.2, 2.4)]
    ims.append(('pt 2 (rising)', view(spec, tex, eyes, run, 2, 2, 30, yaw_view=60, scene=sc)))
    ims.append(('3/4', view(spec, tex, eyes, run, 2, 20, 30, yaw_view=30, limb=(0.6, 1.0), scene=sc)))
    ims.append(('front', view(spec, tex, eyes, run, 2, 20, 30, yaw_view=0, limb=(0.6, 1.0), scene=sc)))
    rows.append(('CHASE (2)', ims))
    ims = [(lab, view(spec, tex, eyes, run, 3, 40, a, yaw_view=yv, scene=sc)) for lab, a, yv in
           (('side', 10, 90), ('rock', 27, 90), ('back (you see)', 30, 180), ('3/4', 40, 135))]
    rows.append(('FACE_WALL (3)', ims))
    ims = [(lab, view(spec, tex, eyes, run, 4, 40, a, yaw_view=yv, scene=sc)) for lab, a, yv in
           (('front', 10, 0), ('3/4', 20, 35), ('side', 30, 90), ('twitch', 67 - 9 + 0.5, 15))]
    ims.append(('walk', view(spec, tex, eyes, run, 4, 40, 50, yaw_view=40, limb=(1.4, 0.8), scene=sc)))
    rows.append(('STAND (4)', ims))
    ims = [(lab, view(spec, tex, eyes, run, pose, ptv, 30, size=(360, 260), scene=sc, night=True, eye_override=eo,
                      target=tg)) for lab, pose, ptv, eo, tg in
           (('asleep in the hut', 0, 30, (2.0, 1.62, 2.5), (0.0, 0.2, 0.0)), ('it woke up', 1, 60, (0.4, 1.62, 3.0), (0.0, 0.8, 0.0)),
            ('chase', 2, 20, (0.3, 1.62, 3.0), (0.0, 1.0, 0.0)))]
    rows.append(('night (flashlight)', ims))
    return rows
