"""後ろ USHIRO - 0.6 x 2.2 blocks. Woman-like, bent forward over you: long black hair hanging forward, chalk-white
face, a too-wide grin, eyes far too close together, grey burial kimono (経帷子) closed right-over-left like the
dead, bare grey feet. Poses: 0 LEAN, 1 GRAB."""
import math

from common import ramp, pal, rng
from ent_model import Spec, Part, Cube
from ent_lib import hand, HELPERS
from ent_paint import Img, faces, fill_cube, material, hair_material, ragged_bottom

SKIN = ramp(['#6d6b66', '#8f8d87', '#acaaa4', '#c6c4bd', '#dad8d0', '#e9e7df'], 6)
HAIR = ramp(['#040405', '#09090b', '#101013', '#18181d', '#24242b', '#353641'], 6)
KIMONO = ramp(['#2e2e2d', '#464644', '#5f5e5b', '#787773', '#918f8a', '#a6a59f', '#b6b5ae'], 7)
MOUTH = pal('#0a0505', '#1c0b0a', '#311311', '#46201c')
TEETH = pal('#7d7868', '#a29c88', '#c2bda8')


class Ushiro(Spec):
    id = 'ushiro'
    java_name = 'UshiroModel'
    module = 'ent_ushiro'
    tex_w = 64
    height = 2.2
    poses = [('LEAN', 0), ('GRAB', 1)]
    doc = """後ろ (ushiro): walks right behind you, bent forward so its face hangs at your head height, hair hanging,
    arms dangling to the knees, whispering. Turn around and it is face to face with you; GRAB raises the arms and
    the too-wide grin opens. Pose ids: 0 LEAN, 1 GRAB."""

    def build(self):
        hips = Part('hips', (0, 8, 0), cubes=[Cube((-3.5, -1, -2.5), (7, 2, 5), 'kimono', tag='waist')])
        skirt = Part('skirt', (0, 0, 0), cubes=[Cube((-3.5, 0, -2.5), (7, 15, 5), 'kimono', tag='skirt')])
        torso = Part('torso', (0, 0, 0), cubes=[Cube((-3.5, -10, -2), (7, 10, 4), 'kimono', tag='chest')])
        neck = Part('neck', (0, -10, 0), cubes=[Cube((-1, -2, -1), (2, 2, 2), 'skin', tag='neck')])
        head = Part('head', (0, -2, 0), cubes=[Cube((-3.5, -7, -3), (7, 5, 6), 'head', tag='cranium'),
                                               Cube((-3, -2.2, -2.6), (6, 2, 5), 'mouth', tag='mouth')])
        jaw = Part('jaw', (0, -2, 2.5), cubes=[Cube((-3.5, 0, -5.5), (7, 2, 6), 'jaw', tag='jaw')])
        hair_back = Part('hair_back', (0, -6.5, 3.05), cubes=[Cube((-3.5, 0, 0), (7, 10, 1), 'hair', tag='hair_back')])
        hair_back2 = Part('hair_back2', (0, 10, 0), cubes=[Cube((-3.5, 0, 0), (7, 10, 1), 'hair', tag='hair_back2')])
        hair_back.add(hair_back2)
        hair_front = Part('hair_front', (0, -7, -3.3), cubes=[Cube((-3.5, 0, 0), (7, 15, 0), 'hairf', tag='hair_front')])
        hair_l = Part('hair_l', (3.55, -6.5, 0.0), cubes=[Cube((-0.5, 0, -2.5), (1, 13, 5), 'hair', tag='hair_l')])
        hair_r = Part('hair_r', (-3.55, -6.5, 0.0), cubes=[Cube((-0.5, 0, -2.5), (1, 13, 5), 'hair', tag='hair_r')])
        head.add(jaw, hair_back, hair_front, hair_l, hair_r)
        neck.add(head)
        arms = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.5, -9.5, 0), cubes=[Cube((-1, -0.5, -1), (2, 9, 2), 'skin', tag=f'{pre}_upper'),
                                                                Cube((-2, -0.5, -2), (3, 8, 4), 'kimono', tag=f'{pre}_sleeve')])
            fore = Part(f'{pre}_forearm', (0, 8.5, 0), cubes=[Cube((-1, 0, -1), (2, 8, 2), 'skin', tag=f'{pre}_lower')])
            h = hand(pre, side, palm=(3, 2, 1), seg=(3, 2), fingers=3, mat='skin', nail='nail')
            h.pivot = (0, 8, 0)
            h.reset()
            fore.add(h)
            arm.add(fore)
            arms.append(arm)
        torso.add(neck, *arms)
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.5, 0, 0), cubes=[Cube((-1, 0, -1), (2, 8, 2), 'skin', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 8, 0), cubes=[Cube((-1, 0, -1), (2, 8, 2), 'skin', tag=f'{pre}_shin'),
                                                       Cube((-1, 7, -3), (2, 1, 2), 'skin', tag=f'{pre}_foot')])
            leg.add(shin)
            legs.append(leg)
        hips.add(skirt, torso, *legs)
        self.root.add(hips)

    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        skin = material(SKIN, 1001, base=3.6, amp=0.45, grad=0.6)
        kim = material(KIMONO, 1002, base=4.0, amp=0.55, grad=0.8, streak=True)
        hairm = hair_material(HAIR, 1003)
        mats = {'skin': skin, 'kimono': kim, 'head': hairm, 'hair': hairm, 'hairf': hairm, 'jaw': skin, 'nail': skin,
                'mouth': material(MOUTH, 1004, base=1.2, amp=0.5, grad=0.0, top_lift=0)}
        for p in self.root.walk():
            for c in p.cubes:
                fill_cube(img, c, mats[c.mat])
        # ---- face: chalk white, eyes nearly touching, the grin running ear to ear
        cr = faces(img, self.cube('cranium'))
        f = cr['front']
        f.fill(lambda x, y, ff: SKIN[5] if y < 3 else SKIN[4])
        for x in (0, 1, 5, 6):
            f.set(x, 0, HAIR[1])
        f.set(0, 1, HAIR[2])
        f.set(6, 1, HAIR[2])
        f.set(2, 1, SKIN[3])        # brows pinched towards the middle
        f.set(4, 1, SKIN[3])
        f.set(2, 2, '#050404')      # eyes, one pixel apart
        f.set(4, 2, '#050404')
        f.set(3, 2, SKIN[2])        # the bridge between them in shadow
        f.set(2, 3, '#5a1a18')      # red rims below
        f.set(4, 3, '#5a1a18')
        f.set(0, 3, MOUTH[1])       # grin corners turned up to the ears
        f.set(6, 3, MOUTH[1])
        for x in range(7):
            f.set(x, 4, TEETH[1 + (x % 2)] if 0 < x < 6 else MOUTH[1])
        ef = faces(eyes, self.cube('cranium'))['front']
        ef.set(2, 2, '#e8e2d0')
        ef.set(4, 2, '#e8e2d0')
        jf = faces(img, self.cube('jaw'))
        jf['front'].fill(lambda x, y, ff: (MOUTH[0] if 0 < x < 6 else SKIN[3]) if y == 0 else SKIN[4])
        jf['top'].fill(lambda x, y, ff: TEETH[x % 2 + 1] if y == ff.h - 1 and 0 < x < 6 else MOUTH[2])
        jf['back'].fill(lambda x, y, ff: HAIR[1])
        cb = cr['bottom']
        cb.fill(lambda x, y, ff: TEETH[2] if (y == ff.h - 1 and 0 < x < 6) else MOUTH[0])
        top = cr['top']
        for y in range(top.h):
            top.set(3, y, HAIR[3])
        # ---- hair: ragged sheets; the front curtain parts over the face
        for tag, seed in (('hair_back2', 1011), ('hair_l', 1012), ('hair_r', 1013)):
            fs = faces(img, self.cube(tag))
            for n in ('front', 'back', 'left', 'right'):
                ragged_bottom(fs[n], seed + len(n), maxcut=4)
        hf = faces(img, self.cube('hair_front'))
        lens = {0: 15, 1: 14, 2: 1, 3: 0, 4: 1, 5: 13, 6: 15}
        r = rng(1014)
        for n in ('front', 'back'):
            fc = hf[n]
            for x in range(fc.w):
                xx = x if n == 'front' else fc.w - 1 - x
                ln = lens[xx] - (int(r.integers(0, 3)) if lens[xx] > 3 else 0)
                for y in range(fc.h):
                    over_face = xx in (1, 5) and 1 <= y <= 4          # keep the eyes and grin visible
                    if y >= ln or over_face:
                        fc.clear(x, y)
                    else:
                        fc.set(x, y, HAIR[2 if (x + y) % 3 else 4])
        # ---- kimono: collar crossed right over left (the way the dead are dressed), faint sutra, mud at the hem
        ch = faces(img, self.cube('chest'))
        fr = ch['front']
        for y in range(6):
            fr.set(3 - y // 2, y, KIMONO[6])      # the outer panel edge running down to the wearer's left
            fr.set(4 + y // 3, y, KIMONO[1])
        fr.set(3, 0, SKIN[4])
        fr.set(4, 0, SKIN[4])
        for x in range(7):                         # narrow sash
            fr.set(x, 7, KIMONO[2])
            for n in ('back', 'left', 'right'):
                if x < ch[n].w:
                    ch[n].set(x, 7, KIMONO[2])
        sk = faces(img, self.cube('skirt'))
        for n in ('front', 'back', 'left', 'right'):
            fc = sk[n]
            for y in range(fc.h - 4, fc.h):
                for x in range(fc.w):
                    if r.random() < (y - fc.h + 5) * 0.2:
                        fc.blend(x, y, '#2e261c', 0.6)
            for x in range(fc.w):
                if r.random() < 0.5:
                    fc.clear(x, fc.h - 1)                  # torn hem
        sk['front'].fill(lambda x, y, ff: KIMONO[1] if x == 4 else None)   # panel overlap line
        for tag in ('chest', 'skirt'):
            fs = faces(img, self.cube(tag))
            for n in ('back', 'left', 'right'):
                fc = fs[n]
                for x in range(1, fc.w - 1, 2):
                    for y in range(1, fc.h - 2):
                        if r.random() < 0.18:
                            fc.dl(x, y, -0.07)              # sutra strokes, barely there
        for pre in ('right', 'left'):
            sl = faces(img, self.cube(f'{pre}_sleeve'))
            for n in ('front', 'back', 'left', 'right'):
                for x in range(sl[n].w):
                    sl[n].dl(x, sl[n].h - 1, -0.08)
            ft = faces(img, self.cube(f'{pre}_foot'))
            for n, fc in ft.items():
                fc.fill(lambda x, y, ff: SKIN[2] if (x + y) % 2 else SKIN[1])
            for i in (1, 2, 3):
                fs = faces(img, self.cube(f'{pre}_f{i}b'))
                fs['front'].set(0, 1, '#7a7458')
                fs['bottom'].set(0, 0, '#5e5a47')
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float walk = Math.min(1.0F, limbSwingAmount);
float snap = twitch(t, 71.0F, 17.0F) - twitch(t, 113.0F, 50.0F);
float breath = Mth.sin(t * 0.06F);
float sw = Mth.sin(limbSwing * 0.6662F);
// footsteps that match yours
this.rightLeg.xRot = sw * 0.5F * walk;
this.leftLeg.xRot = -sw * 0.5F * walk;
this.rightShin.xRot = Math.max(0.0F, -sw) * 0.6F * walk;
this.leftShin.xRot = Math.max(0.0F, sw) * 0.6F * walk;
this.skirt.xRot = Math.abs(sw) * 0.04F * walk;
float fl = 0.25F + Mth.sin(t * 0.05F) * 0.15F;
curl(this.rightFinger1, this.rightFinger1b, fl, fl * 1.5F + twitch(t, 43.0F, 2.0F) * 0.6F);
curl(this.rightFinger2, this.rightFinger2b, fl * 1.2F, fl * 1.2F);
curl(this.rightFinger3, this.rightFinger3b, fl * 0.8F, fl * 1.6F);
curl(this.leftFinger1, this.leftFinger1b, fl * 1.1F, fl * 1.3F);
curl(this.leftFinger2, this.leftFinger2b, fl, fl * 1.5F + twitch(t, 59.0F, 31.0F) * 0.6F);
curl(this.leftFinger3, this.leftFinger3b, fl * 0.9F, fl * 1.2F);
if (pose == GRAB) {
    // arms up, the grin tears open, the head jerks
    float e = ease(pt / 5.0F);
    this.torso.xRot = 0.55F + breath * 0.02F;
    this.neck.xRot = 0.1F;
    this.head.xRot = -0.55F + pitch * 0.5F + Mth.sin(t * 2.9F) * 0.08F;
    this.head.zRot = Mth.sin(t * 2.3F) * 0.3F * e + 0.2F;
    this.head.yRot = yaw * 0.6F;
    this.jaw.xRot = 1.05F * e + Mth.sin(t * 3.7F) * 0.06F;
    this.rightArm.xRot = -2.15F * e;
    this.rightArm.zRot = -0.55F * e;
    this.leftArm.xRot = -2.05F * e;
    this.leftArm.zRot = 0.55F * e;
    this.rightForearm.xRot = -0.35F * e;
    this.leftForearm.xRot = -0.35F * e;
    curl(this.rightFinger1, this.rightFinger1b, -0.3F * e, 0.6F);
    curl(this.rightFinger2, this.rightFinger2b, -0.2F * e, 0.7F);
    curl(this.rightFinger3, this.rightFinger3b, -0.35F * e, 0.6F);
    curl(this.leftFinger1, this.leftFinger1b, -0.3F * e, 0.6F);
    curl(this.leftFinger2, this.leftFinger2b, -0.2F * e, 0.7F);
    curl(this.leftFinger3, this.leftFinger3b, -0.35F * e, 0.6F);
} else {
    // LEAN: bent at the hips, its face hanging at your head height, arms dangling, whispering
    float e = ease(pt / 20.0F);
    this.torso.xRot = 1.02F * e + breath * 0.025F;
    this.torso.zRot = Mth.sin(t * 0.023F) * 0.05F;
    this.neck.xRot = 0.2F * e;
    this.head.xRot = -1.12F * e + pitch * 0.4F;
    this.head.zRot = (0.28F + Mth.sin(t * 0.031F) * 0.1F) * e + snap * 0.45F;
    this.head.yRot = yaw * 0.5F;
    this.jaw.xRot = 0.03F + Math.max(0.0F, Mth.sin(t * 0.33F)) * 0.07F * Math.max(0.0F, Mth.sin(t * 0.05F));
    float hang = -this.torso.xRot;
    this.rightArm.xRot = hang + Mth.sin(t * 0.047F) * 0.05F + sw * 0.08F * walk;
    this.leftArm.xRot = hang + Mth.sin(t * 0.047F + 1.1F) * 0.05F - sw * 0.08F * walk;
    this.rightArm.zRot = 0.06F;
    this.leftArm.zRot = -0.06F;
}
// hair hangs with gravity
float px = this.hips.xRot + this.torso.xRot + this.neck.xRot + this.head.xRot;
float pz = this.hips.zRot + this.torso.zRot + this.neck.zRot + this.head.zRot;
this.hairBack.xRot = -px * 0.9F + 0.05F;
this.hairBack.zRot = -pz;
this.hairBack2.xRot = 0.05F;
this.hairFront.xRot = -px * 0.85F;
this.hairFront.zRot = -pz * 0.9F;
this.hairL.xRot = -px * 0.85F;
this.hairL.zRot = -pz;
this.hairR.xRot = -px * 0.85F;
this.hairR.zRot = -pz;
'''
    helpers = HELPERS


SPEC = Ushiro


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc(cv):
        ground(cv, 2)
    rows = []
    ims = [(lab, view(spec, tex, eyes, run, 0, p, a, yaw_view=yv, scene=sc)) for lab, p, a, yv in
           (('pt 0', 0, 10, 20), ('pt 10', 10, 20, 20), ('front', 40, 40, 0), ('3/4', 40, 50, 35), ('side', 40, 60, 90),
            ('back', 40, 70, 180))]
    ims.append(('walk', view(spec, tex, eyes, run, 0, 40, 80, yaw_view=60, limb=(1.0, 0.8), scene=sc)))
    rows.append(('LEAN (0)', ims))
    ims = [(f'pt {p}', view(spec, tex, eyes, run, 1, p, 30 + p, yaw_view=10, scene=sc)) for p in (0, 2, 5, 12)]
    ims.append(('side', view(spec, tex, eyes, run, 1, 12, 40, yaw_view=90, scene=sc)))
    rows.append(('GRAB (1)', ims))
    # what you see when you turn around: player eye at 1.62, the ushiro 0.9 blocks behind you, leaning over
    ims = [(lab, view(spec, tex, eyes, run, pose, 40, a, size=(360, 300), scene=sc, night=True, eye_override=(0.0, 1.62, 1.35),
                      target=(0.0, 1.6, 0.0))) for lab, pose, a in (('turn around: LEAN', 0, 44), ('GRAB', 1, 45))]
    ims.append(('from the side', view(spec, tex, eyes, run, 0, 40, 44, size=(360, 300), scene=sc, night=True,
                                      eye_override=(3.0, 1.62, 1.0), target=(0.0, 1.3, 0.3))))
    rows.append(('night: turning around (it walks 0.9 blocks behind you)', ims))
    return rows
