"""写るもの UTSURUMONO - 0.6 x 1.8 blocks. A translucent pale hiker - the one who wrote the notes years ago: faded rain
jacket, hood up, face lost in shadow, an old pack; one arm pointing the way out. Seen only through the phone
camera. Poses: 0 POINT, 1 STAND, 2 LOOK."""
from common import ramp, pal, rng
from ent_model import Spec, Part, Cube
from ent_lib import HELPERS
from ent_paint import Img, faces, fill_cube, material

JACKET = ramp(['#262c33', '#36404a', '#49545f', '#5d6a76', '#73808c', '#88949f', '#9aa5ae'], 7)
TROUSER = ramp(['#1a1c1f', '#25282c', '#30343a', '#3d4248'], 4)
PACK = ramp(['#2a1a16', '#3c2620', '#4f342b', '#614237'], 4)
BOOT = ramp(['#16120e', '#221b15', '#30261d'], 3)
ALPHA = 165


class Utsurumono(Spec):
    id = 'utsurumono'
    java_name = 'UtsurumonoModel'
    module = 'ent_utsurumono'
    translucent = True
    tex_w = 64
    height = 1.8
    poses = [('POINT', 0), ('STAND', 1), ('LOOK', 2)]
    doc = """写るもの (utsurumono): a lost hiker who never got out, faded and translucent (the model uses
    RenderType.entityTranslucent; the texture alpha is ~65%). Only the phone camera shows it. POINT raises one arm
    along its facing (the way out) with the head hanging; LOOK raises the face-in-shadow towards the viewer.
    Pose ids: 0 POINT, 1 STAND, 2 LOOK. The eyes layer holds two barely visible points in the hood's shadow."""

    def build(self):
        hips = Part('hips', (0, 12, 0), cubes=[Cube((-3.5, -1.5, -2), (7, 2, 4), 'trouser', tag='seat')])
        torso = Part('torso', (0, -1, 0), cubes=[Cube((-3.5, -10, -2), (7, 10, 4), 'jacket', grow=0.25, tag='jacket'),
                                                 Cube((-3, -9.5, 2.3), (6, 8, 3), 'pack', tag='pack')])
        head = Part('head', (0, -10, 0), cubes=[Cube((-3, -6, -3), (6, 6, 6), 'shadow', tag='head'),
                                                Cube((-3.5, -6.6, -3.2), (7, 7, 7), 'jacket', grow=0.3, tag='hood')])
        torso.add(head)
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.6, -9, 0), cubes=[Cube((-1.5, -1, -1.5), (3, 7, 3), 'jacket', grow=0.2, tag=f'{pre}_sleeve')])
            fore = Part(f'{pre}_forearm', (0, 6, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 6, 3), 'jacket', grow=0.1, tag=f'{pre}_cuff'),
                                                          Cube((-1, 6, -1), (2, 2, 2), 'hand', tag=f'{pre}_hand')])
            if pre == 'right':
                fore.cubes.append(Cube((-0.5, 8, -0.5), (1, 2, 1), 'hand', tag='finger'))
            arm.add(fore)
            torso.add(arm)
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.8, 0, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 6, 3), 'trouser', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 6, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 6, 3), 'trouser', tag=f'{pre}_shin'),
                                                       Cube((-1.5, 4, -2.5), (3, 2, 1), 'boot', tag=f'{pre}_toe')])
            leg.add(shin)
            hips.add(leg)
        hips.add(torso)
        self.root.add(hips)

    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        mats = {'jacket': material(JACKET, 1301, base=3.8, amp=0.6, grad=0.9, alpha=ALPHA),
                'trouser': material(TROUSER, 1302, base=2.0, amp=0.5, alpha=ALPHA),
                'pack': material(PACK, 1303, base=2.0, alpha=ALPHA), 'boot': material(BOOT, 1304, base=1.2, alpha=ALPHA),
                'hand': material(pal('#5e5c58', '#77746e', '#8c8983'), 1305, base=1.4, alpha=ALPHA),
                'shadow': material(pal('#050607', '#0a0c0e', '#101316'), 1306, base=1.0, amp=0.4, alpha=215)}
        for p in self.root.walk():
            for c in p.cubes:
                fill_cube(img, c, mats[c.mat])
        hd = faces(img, self.cube('hood'))
        fr = hd['front']
        for y in range(fr.h):
            for x in range(fr.w):
                if 0 < x < fr.w - 1 and y > 0:
                    fr.clear(x, y)
        bot = hd['bottom']
        for y in range(bot.h):
            for x in range(bot.w):
                if 0 < x < bot.w - 1 and y > 0:
                    bot.clear(x, y)
        ef = faces(eyes, self.cube('head'))['front']
        ef.set(2, 3, (34, 40, 44, 255))
        ef.set(4, 3, (34, 40, 44, 255))
        # the jacket's old reflective strip, the pack's frayed straps, dirt and water stains
        r = rng(1310)
        jk = faces(img, self.cube('jacket'))
        for n in ('front', 'back', 'left', 'right'):
            fc = jk[n]
            for x in range(fc.w):
                fc.set(x, 7, (150, 156, 158, ALPHA))
            for _ in range(4):
                fc.blend(int(r.integers(0, fc.w)), int(r.integers(5, fc.h)), '#2a2420', 0.5)
        fr = jk['front']
        for y in range(10):
            fr.set(3, y, (40, 48, 56, ALPHA))
        pk = faces(img, self.cube('pack'))
        pk['back'].fill(lambda x, y, ff: (PACK[3][0], PACK[3][1], PACK[3][2], ALPHA) if y in (1, 5) else None)
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float sway = Mth.sin(t * 0.03F) * 0.025F;
this.torso.zRot = sway;
this.rightArm.zRot = 0.05F;
this.leftArm.zRot = -0.05F;
if (pose == POINT) {
    // one arm raised along its facing, pointing the way out; the head hangs
    float e = ease(pt / 30.0F);
    this.rightArm.xRot = -1.5F * e + Mth.sin(t * 0.04F) * 0.015F;
    this.rightArm.yRot = 0.05F * e;
    this.head.xRot = 0.55F * e;
    this.head.zRot = 0.1F;
    this.torso.xRot = 0.04F;
} else if (pose == LOOK) {
    // the shadow inside the hood turns up towards you
    float e = ease(pt / 40.0F);
    this.head.yRot = yaw * e;
    this.head.xRot = (-0.1F + pitch) * e + 0.35F * (1.0F - e);
    this.head.zRot = 0.12F * e + Mth.sin(t * 0.02F) * 0.04F;
} else {
    // STAND: still, head bowed, barely swaying
    this.head.xRot = 0.35F + Mth.sin(t * 0.025F) * 0.03F;
    this.head.zRot = Mth.sin(t * 0.017F) * 0.05F;
    this.rightArm.xRot = Mth.sin(t * 0.03F) * 0.02F;
    this.leftArm.xRot = -Mth.sin(t * 0.03F) * 0.02F;
}
'''
    helpers = HELPERS


SPEC = Utsurumono


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc(cv):
        ground(cv, 2)
    rows = []
    ims = [(lab, view(spec, tex, eyes, run, 0, p, 40, yaw_view=yv, scene=sc)) for lab, p, yv in
           (('pt 0', 0, 30), ('pt 15', 15, 30), ('front', 40, 0), ('side', 40, 90), ('back', 40, 180))]
    rows.append(('POINT (0)', ims))
    ims = [(lab, view(spec, tex, eyes, run, 1, 40, a, yaw_view=yv, scene=sc)) for lab, a, yv in (('front', 10, 0), ('3/4', 30, 35))]
    rows.append(('STAND (1)', ims))
    ims = [(lab, view(spec, tex, eyes, run, 2, p, 40, yaw_view=yv, head_pitch=-15, scene=sc)) for lab, p, yv in
           (('pt 0', 0, 10), ('pt 20', 20, 10), ('pt 60', 60, 10))]
    rows.append(('LOOK (2)', ims))
    ims = [(lab, view(spec, tex, eyes, run, pose, 40, 30, size=(360, 280), scene=sc, night=True, eye_override=(0.4, 1.62, 4.5),
                      target=(0.0, 1.1, 0.0))) for lab, pose in (('point', 0), ('look', 2))]
    rows.append(('night', ims))
    return rows
