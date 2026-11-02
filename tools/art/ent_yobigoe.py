"""呼び声 YOBIGOE - 0.6 x 1.9 blocks. Looks like a rescuer: faded orange rain jacket with retroreflective tape, hood,
headlamp, dark trousers, boots, a small pack. Under the hood the face is smooth skin with no features.
Poses: 0 CALL, 1 IDLE, 2 TURN, 3 RUSH."""
import math

import numpy as np

from common import ramp, pal, C, rng
from ent_model import Spec, Part, Cube
from ent_lib import HELPERS
from ent_paint import Img, faces, fill_cube, material

JACKET = ramp(['#2c1007', '#4a1b0c', '#6b2912', '#8a3a1c', '#a24d29', '#b5623a', '#c27a52'], 7)
TROUSER = ramp(['#0c0d10', '#14161a', '#1c1f25', '#262a31', '#30353e'], 5)
BOOT = ramp(['#120c08', '#1e150e', '#2b1f15', '#3a2b1e'], 4)
GLOVE = ramp(['#0e0e10', '#18181b', '#232327', '#2e2e33'], 4)
FACE = ramp(['#4c3d36', '#66534a', '#80695d', '#977f70', '#a99283', '#b5a090'], 6)
TAPE = pal('#7b7e80', '#9a9d9e', '#b4b6b5')
PACK = ramp(['#120b0b', '#1e1213', '#2a1a1b', '#382425'], 4)


class Yobigoe(Spec):
    id = 'yobigoe'
    java_name = 'YobigoeModel'
    module = 'ent_yobigoe'
    tex_w = 128
    height = 1.9
    poses = [('CALL', 0), ('IDLE', 1), ('TURN', 2), ('RUSH', 3)]
    doc = """呼び声 (yobigoe): a rescuer in a faded orange rain jacket and hood, headlamp burning, waving slowly 30-50
    blocks away. Under the hood there is no face, only smooth skin. TURN: the body turns away while the head stays
    on you (180 degree neck twist). RUSH: the lamp goes out (lamp glow and beam hidden) and it sprints.
    Pose ids: 0 CALL, 1 IDLE, 2 TURN, 3 RUSH. The headlamp glow and its soft beam cone are drawn by the eyes layer;
    the retroreflective tape on the jacket is in the eyes layer too (dim), so it flares in the flashlight."""

    def build(self):
        hips = Part('hips', (0, 10, 0), cubes=[Cube((-3.5, -1.5, -2), (7, 2, 4), 'trouser', tag='seat')])
        torso = Part('torso', (0, -1, 0), cubes=[Cube((-3.5, -10, -2), (7, 10, 4), 'jacket', grow=0.25, tag='jacket'),
                                                 Cube((-3, -9, 2.3), (6, 7, 2), 'pack', tag='pack')])
        head = Part('head', (0, -10, 0), cubes=[Cube((-3, -6, -3), (6, 6, 6), 'face', tag='head'),
                                                Cube((-3.5, -6.6, -3.2), (7, 7, 7), 'hood', grow=0.25, tag='hood')])
        lamp = Part('lamp', (0, -4.8, -3.75), cubes=[Cube((-1, -0.5, -1.2), (2, 1, 1), 'lamp', tag='lamp')])
        lamp_glow = Part('lamp_glow', (0, -4.8, -4.0), cubes=[Cube((-1, -0.5, -1.0), (2, 1, 0), 'glow', tag='lamp_glow'),
                                                            Cube((-4, -4, -1.3), (8, 8, 0), 'glow', tag='lamp_halo')])
        beam = Part('beam', (0, -4.8, -4.6), cubes=[Cube((0, -6, -20), (0, 12, 20), 'beam', tag='beam_v'),
                                                    Cube((-6, 0, -20), (12, 0, 20), 'beam', tag='beam_h')])
        head.add(lamp, lamp_glow, beam)
        arms = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            arm = Part(f'{pre}_arm', (side * 4.6, -9.0, 0), cubes=[Cube((-1.5, -1, -1.5), (3, 8, 3), 'jacket', grow=0.2, tag=f'{pre}_sleeve')])
            fore = Part(f'{pre}_forearm', (0, 7, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 7, 3), 'jacket', grow=0.1, tag=f'{pre}_cuff'),
                                                          Cube((-1.5, 7, -1.5), (3, 3, 3), 'glove', tag=f'{pre}_glove')])
            arm.add(fore)
            arms.append(arm)
        torso.add(head, *arms)
        legs = []
        for side, pre in ((-1, 'right'), (1, 'left')):
            leg = Part(f'{pre}_leg', (side * 1.8, 0, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 7, 3), 'trouser', tag=f'{pre}_thigh')])
            shin = Part(f'{pre}_shin', (0, 7, 0), cubes=[Cube((-1.5, 0, -1.5), (3, 7, 3), 'trouser', tag=f'{pre}_shin'),
                                                       Cube((-1.5, 5, -2.5), (3, 2, 1), 'boot', tag=f'{pre}_toe')])
            leg.add(shin)
            legs.append(leg)
        hips.add(torso, *legs)
        self.root.add(hips)

    def paint(self):
        W, H = self.tex_w, self.tex_h
        img, eyes = Img(W, H), Img(W, H)
        mats = {'jacket': material(JACKET, 901, base=3.6, amp=0.6, grad=1.0), 'trouser': material(TROUSER, 902, base=2.4, amp=0.5),
                'pack': material(PACK, 903, base=1.8), 'face': material(FACE, 904, base=3.4, amp=0.25, grad=0.5, top_lift=0.2),
                'hood': material(JACKET, 905, base=3.4, amp=0.6, grad=0.6), 'lamp': material(GLOVE, 906, base=1.5),
                'glove': material(GLOVE, 907, base=1.8, amp=0.5), 'boot': material(BOOT, 908, base=1.6)}
        for p in self.root.walk():
            for c in p.cubes:
                if c.mat in mats:
                    fill_cube(img, c, mats[c.mat])
        # ---- the face: smooth skin, no features, just the faintest swelling where they should be
        hf = faces(img, self.cube('head'))
        f = hf['front']
        f.fill(lambda x, y, ff: FACE[4] if 1 <= y <= 4 and 1 <= x <= 4 else FACE[3])
        f.set(2, 2, FACE[3])        # where eyes would be: barely a shade
        f.set(3, 2, FACE[3])
        f.set(2, 3, FACE[5])        # a ridge where a nose would be
        f.set(3, 3, FACE[5])
        for x in range(6):
            f.set(x, 5, FACE[2])
            f.set(x, 0, FACE[2])
        for n in ('left', 'right', 'back', 'top'):
            hf[n].fill(lambda x, y, ff: FACE[1])
        # ---- hood: open at the front (rim only), headlamp strap all around
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
        for n in ('front', 'left', 'right', 'back'):
            fc = hd[n]
            for x in range(fc.w):
                if fc.w > 0 and 1 < fc.h:
                    fc.set(x, 2, GLOVE[1] if (x % 3) else GLOVE[2])
        for n in ('left', 'right', 'back'):
            fc = hd[n]
            for x in range(fc.w):
                fc.dl(x, fc.h - 1, -0.08)
        # inside of the hood seen around the face
        for n in ('left', 'right'):
            fc = hd[n]
            fc.set(0 if n == 'right' else fc.w - 1, 4, JACKET[1])
        # ---- headlamp: housing + lens; glow + beam only in the eyes layer
        lp = faces(img, self.cube('lamp'))
        lp['front'].fill(lambda x, y, ff: '#b9b4a0')
        lp['top'].fill(lambda x, y, ff: GLOVE[2])
        lg = faces(img, self.cube('lamp_glow'))
        for n in ('front', 'back'):
            lg[n].fill(lambda x, y, ff: (0, 0, 0, 0))
        el = faces(eyes, self.cube('lamp_glow'))
        el['front'].fill(lambda x, y, ff: '#fffbe8')
        el['back'].fill(lambda x, y, ff: '#fffbe8')
        # halo billboard around the lamp: what you see when it looks at you from 40 blocks away
        hb = faces(img, self.cube('lamp_halo'))
        he = faces(eyes, self.cube('lamp_halo'))
        for n in ('front', 'back'):
            hb[n].fill(lambda x, y, ff: (0, 0, 0, 0))

            def halo(x, y, ff):
                d = math.hypot(x - 3.5, y - 3.5) / 4.2
                if d >= 1.0:
                    return None
                v = int(round(120 * (1 - d) ** 2.2))
                return (v, int(v * 0.96), int(v * 0.84), max(v, 1)) if v >= 4 else None
            he[n].fill(halo)
        for tag in ('beam_v', 'beam_h'):
            fb = faces(img, self.cube(tag))
            for n, fc in fb.items():
                if fc.w > 0 and fc.h > 0:
                    fc.fill(lambda x, y, ff: (0, 0, 0, 0))
            fe = faces(eyes, self.cube(tag))
            for n, fc in fe.items():
                if fc.w <= 0 or fc.h <= 0:
                    continue
                for y in range(fc.h):
                    for x in range(fc.w):
                        if tag == 'beam_v':
                            s = x / (fc.w - 1) if n == 'right' else 1 - x / (fc.w - 1)     # 0 at the lamp
                            u = (y - (fc.h - 1) / 2) / ((fc.h - 1) / 2)
                        else:
                            s = y / (fc.h - 1)
                            u = (x - (fc.w - 1) / 2) / ((fc.w - 1) / 2)
                        hw = 0.12 + 0.88 * s
                        if abs(u) > hw:
                            continue
                        k = (1 - s) ** 2.0 * (1 - abs(u) / hw) ** 1.4
                        v = int(round(72 * k))
                        if v < 6:
                            continue
                        fc.set(x, y, (v, int(v * 0.95), int(v * 0.8), max(v, 1)))
        # ---- jacket details: reflective tape bands (eyes layer: dim, flares in the flashlight), zip, pockets
        jk = faces(img, self.cube('jacket'))
        je = faces(eyes, self.cube('jacket'))
        for n in ('front', 'back', 'left', 'right'):
            fc, fe = jk[n], je[n]
            for x in range(fc.w):
                for y in (6,):
                    fc.set(x, y, TAPE[1] if (x * 3 + y) % 5 else TAPE[0])
                    fe.set(x, y, (46, 46, 44, 255) if (x * 3 + y) % 5 else (30, 30, 28, 255))
        fr = jk['front']
        for y in range(10):
            fr.set(3, y, JACKET[1])
        fr.set(1, 3, JACKET[2])
        fr.set(5, 3, JACKET[2])
        for y in range(4, 6):
            fr.set(1, y, JACKET[2])
            fr.set(5, y, JACKET[2])
        # dirt and wet streaks low on the jacket
        r = rng(911)
        for n in ('front', 'back', 'left', 'right'):
            fc = jk[n]
            for y in range(7, 10):
                for x in range(fc.w):
                    if r.random() < 0.25:
                        fc.blend(x, y, '#3a2a1c', 0.5)
        for pre in ('right', 'left'):
            sl = faces(img, self.cube(f'{pre}_sleeve'))
            se = faces(eyes, self.cube(f'{pre}_sleeve'))
            for n in ('front', 'back', 'left', 'right'):
                for x in range(sl[n].w):
                    sl[n].set(x, 4, TAPE[1])
                    se[n].set(x, 4, (40, 40, 38, 255))
            sh = faces(img, self.cube(f'{pre}_shin'))
            for n in ('front', 'back', 'left', 'right'):
                for y in range(4, 7):
                    for x in range(sh[n].w):
                        sh[n].set(x, y, BOOT[1 + ((x + y) % 2)] if y > 4 else BOOT[3])
            fill_cube(img, self.cube(f'{pre}_toe'), lambda n, x, y, ff: BOOT[1])
            # mud on the trousers
            th = faces(img, self.cube(f'{pre}_thigh'))
            for n in ('front', 'left', 'right'):
                th[n].blend(1, 5, '#3a2c20', 0.5)
        pk = faces(img, self.cube('pack'))
        pk['back'].fill(lambda x, y, ff: PACK[2] if y not in (2,) else TAPE[0])
        faces(eyes, self.cube('pack'))['back'].fill(lambda x, y, ff: (30, 30, 28, 255) if y == 2 else None)
        return img, eyes

    anim = '''
float partial = ageInTicks - (float) ((int) ageInTicks);
int pose = entity.pose();
float pt = entity.poseTime(partial);
float t = ageInTicks;
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
float walk = Math.min(1.0F, limbSwingAmount);
float snap = twitch(t, 97.0F, 13.0F);
float sw = Mth.sin(limbSwing * 0.6F);
this.beam.xScale = 3.0F;
this.beam.yScale = 3.0F;
this.beam.zScale = 3.0F;
// the lamp gutters now and then
boolean flicker = twitch(t, 151.0F, 7.0F) > 0.34F && twitch(t, 151.0F, 7.0F) < 0.67F;
this.lampGlow.visible = !flicker;
this.beam.visible = !flicker;
this.head.yRot = yaw * 0.8F;
this.head.xRot = pitch * 0.8F;
this.rightLeg.xRot = sw * 0.6F * walk;
this.leftLeg.xRot = -sw * 0.6F * walk;
this.rightShin.xRot = Math.max(0.0F, -sw) * 0.7F * walk;
this.leftShin.xRot = Math.max(0.0F, sw) * 0.7F * walk;
this.rightArm.zRot = 0.05F;
this.leftArm.zRot = -0.05F;
if (pose == CALL) {
    // one arm waving overhead, slow and far too regular; everything else dead still
    float wave = Mth.sin(t * 0.105F);
    this.rightArm.zRot = 2.5F + wave * 0.3F;
    this.rightArm.xRot = -0.12F;
    this.rightForearm.zRot = -0.35F + wave * 0.25F;
    this.leftArm.xRot = sw * 0.3F * walk;
    this.torso.zRot = -0.05F;
    this.head.zRot = 0.1F + snap * 0.15F;
} else if (pose == TURN) {
    // the body turns away, the head stays on you: a 180 degree twist at the neck
    float k = ease(pt / 18.0F);
    this.hips.yRot = Mth.PI * k;
    this.head.yRot = -Mth.PI * k + yaw * 0.8F;
    this.head.zRot = 0.25F * k + snap * 0.2F;
    this.torso.xRot = 0.08F * k;
    this.rightArm.xRot = Mth.sin(t * 0.03F) * 0.03F;
    this.leftArm.xRot = -Mth.sin(t * 0.03F) * 0.03F;
} else if (pose == RUSH) {
    // lamp out; sprinting bent forward, arms flailing out of rhythm, head lolling
    this.lampGlow.visible = false;
    this.beam.visible = false;
    float c = limbSwing * 0.9F;
    float a = 0.4F + Math.min(1.0F, limbSwingAmount * 1.5F) * 0.6F;
    this.torso.xRot = 0.5F;
    this.head.xRot = -0.55F + pitch * 0.5F;
    this.head.zRot = Mth.sin(t * 1.7F) * 0.35F;
    this.rightArm.xRot = -0.4F + Mth.sin(c * 1.3F) * 1.3F * a;
    this.leftArm.xRot = -0.4F - Mth.sin(c * 1.1F + 0.7F) * 1.3F * a;
    this.rightArm.zRot = 0.35F;
    this.leftArm.zRot = -0.35F;
    this.rightForearm.xRot = -0.8F;
    this.leftForearm.xRot = -0.8F;
    this.rightLeg.xRot = Mth.sin(c) * 1.1F * a - 0.3F;
    this.leftLeg.xRot = -Mth.sin(c) * 1.1F * a - 0.3F;
    this.rightShin.xRot = Math.max(0.0F, -Mth.sin(c)) * 1.4F * a + 0.2F;
    this.leftShin.xRot = Math.max(0.0F, Mth.sin(c)) * 1.4F * a + 0.2F;
} else {
    // IDLE: standing perfectly still, head cocked as if listening; a twitch now and then
    this.head.zRot = 0.32F + Mth.sin(t * 0.021F) * 0.06F + snap * 0.3F;
    this.head.xRot = 0.15F + pitch * 0.5F;
    this.rightArm.xRot = sw * 0.25F * walk;
    this.leftArm.xRot = -sw * 0.25F * walk;
}
'''
    helpers = HELPERS


SPEC = Yobigoe


def previews(spec, tex, eyes, run, view, ground, trunk):
    def sc(cv):
        ground(cv, 2)
    rows = []
    ims = [(f'call t{a}', view(spec, tex, eyes, run, 0, 40, a, yaw_view=10, scene=sc)) for a in (0, 8, 16, 24, 40)]
    ims.append(('side', view(spec, tex, eyes, run, 0, 40, 10, yaw_view=90, scene=sc)))
    ims.append(('back', view(spec, tex, eyes, run, 0, 40, 10, yaw_view=180, scene=sc)))
    rows.append(('CALL (0)', ims))
    ims = [(lab, view(spec, tex, eyes, run, 1, 40, a, yaw_view=yv, scene=sc)) for lab, a, yv in
           (('front', 20, 0), ('3/4', 50, 40), ('twitch', 97 - 13 + 0.5, 0), ('walk', 20, 30))]
    ims[-1] = ('walk', view(spec, tex, eyes, run, 1, 40, 20, yaw_view=30, limb=(1.5, 0.8), scene=sc))
    rows.append(('IDLE (1)', ims))
    ims = [(f'pt {p}', view(spec, tex, eyes, run, 2, p, 60 + p, yaw_view=0, scene=sc)) for p in (0, 6, 12, 18, 40)]
    ims.append(('3/4 pt 40', view(spec, tex, eyes, run, 2, 40, 100, yaw_view=45, scene=sc)))
    rows.append(('TURN (2)', ims))
    ims = [(f'ls {l}', view(spec, tex, eyes, run, 3, 10, 30, yaw_view=90, limb=(l, 1.0), scene=sc)) for l in (0.0, 1.0, 2.0, 3.0)]
    ims.append(('3/4', view(spec, tex, eyes, run, 3, 10, 30, yaw_view=35, limb=(1.0, 1.0), scene=sc)))
    rows.append(('RUSH (3)  (lamp out)', ims))
    ims = []
    for (pose, lab, eo) in ((0, 'calling, 14 blocks', (0.5, 1.62, 14.0)), (1, 'idle, 5 blocks', (0.3, 1.62, 5.0)),
                            (2, 'turned, 4 blocks', (0.3, 1.62, 4.0))):
        ims.append((lab, view(spec, tex, eyes, run, pose, 40, 30, size=(400, 260), scene=sc, night=True,
                              eye_override=eo, target=(0.0, 1.3, 0.0))))
    rows.append(('night, seen by the player', ims))
    return rows
