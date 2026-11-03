"""定点カメラ the fixed camera - 0.25 x 0.3. A small black action camera clamped on a mini tripod: the lens barrel off
centre on the front, the red REC LED on top (blinking, eyes layer), the rear screen faintly lit with the live view
(eyes layer). Poses (via `instanceof Animated`, else IDLE): 0 IDLE, 1 FALLEN (knocked over: tips onto its side
with a bounce, the camera twisting in its clamp). 4 texels per model pixel (UV 32 x 32 -> 128 x 128)."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

PLASTIC = ['#060607', '#0b0b0d', '#111114', '#18181c', '#212126', '#2c2c33', '#3b3b44', '#4f4f5a']
METAL = ['#1c1d20', '#2a2c30', '#3b3e43', '#50545a', '#686d74', '#858a91', '#a6abb1']
GLASS = ['#03040a', '#070a16', '#0d1428', '#18244a', '#2a3d70', '#4c64a0', '#8aa2d4']
RED = ['#2a0606', '#4a0a0a', '#701010', '#a01818', '#d22828', '#ff4a3a']
RUBBER = ['#090909', '#121212', '#1b1b1b']


class Camera(Spec):
    id = 'camera'
    java_name = 'CameraModel'
    module = 'ent_camera'
    px = 4
    tex_w = 32
    tex_h = 32
    height = 0.3
    translucent = False
    plain_entity = True
    poses = [('IDLE', 0), ('FALLEN', 1)]
    doc = """定点カメラ (the fixed camera): a small black action camera clamped on a mini tripod, the lens barrel off centre on
    the front (the camera looks along the entity's facing), the red REC LED on top and the rear screen showing the
    live view (both on the eyes layer: the LED blinks in real time, ~1.4 s on / 0.6 s off).
    Pose ids (read through instanceof Animated; anything else draws IDLE): 0 IDLE, 1 FALLEN (knocked over by
    something: tips over to its right in ~5 ticks with a bounce at ticks 5-9, the camera twisting in its clamp;
    it ends on its side, rolled 83 degrees: roll the feed's view by the same angle, e.g. ViewportEvent.ComputeCameraAngles
    setRoll, ramping with the fall over pose ticks 0-5)."""

    def build(self):
        r = self.root
        rig = Part('rig', (-1.75, 24.0, 0))             # tips over around the floor point beside its right foot
        tripod = Part('tripod', (1.75, -2.3, 0), cubes=[Cube((-0.5, -0.5, -0.5), (1.0, 0.75, 1.0), tag='hub')])
        for i in range(3):
            a = i * 2.0 * math.pi / 3.0
            leg = Part(f'leg{i}', (0, 0, 0), rot=(0.42, a, 0), cubes=[Cube((-0.25, 0, -0.25), (0.5, 2.25, 0.5), tag=f'leg{i}',
                                                                          uv_of=None if i == 0 else 'leg0')])
            leg.add(Part(f'foot{i}', (0, 2.25, 0), cubes=[Cube((-0.375, -0.25, -0.375), (0.75, 0.5, 0.75), tag=f'foot{i}',
                                                                uv_of=None if i == 0 else 'foot0')]))
            tripod.add(leg)
        column = Part('column', (0, -0.5, 0), cubes=[Cube((-0.25, -0.5, -0.25), (0.5, 0.5, 0.5), tag='column'),
                                                      Cube((-0.375, -1.0, -0.375), (0.75, 0.5, 0.75), tag='ball')])
        cam = Part('cam', (0, -1.0, 0))
        cam.cube(Cube((-1.5, -2.25, -0.875), (3.0, 2.0, 1.75), tag='body'))
        cam.cube(Cube((-0.5, -0.25, -0.5), (1.0, 0.25, 1.0), tag='clamp'))
        cam.cube(Cube((-1.25, -1.75, -1.375), (1.0, 1.0, 0.5), tag='lens'))
        cam.cube(Cube((0.75, -2.5, -0.5), (0.5, 0.25, 0.5), tag='button'))
        cam.add(Part('led', (-0.75, -2.25, -0.5), cubes=[Cube((-0.25, -0.25, -0.25), (0.5, 0.25, 0.5), tag='led')]))
        cam.add(card('screen', (0, -1.25, 0.9), (-1.25, -0.75, 0), (2.5, 1.5, 0), 'screen', faces=('front',),
                     rot=(0, math.pi, 0)))
        column.add(cam)
        tripod.add(column)
        rig.add(tripod)
        r.add(rig)

    def paint(self):
        pt = Painter(self, 3131)
        F, EF = pt.F, pt.EF
        rng = np.random.default_rng(31)

        def edge_dist(f):
            ys, xs = np.mgrid[0:f.h, 0:f.w]
            return np.minimum(np.minimum(xs, f.w - 1 - xs), np.minimum(ys, f.h - 1 - ys))

        def plastic(tag, base=2.6, pal=PLASTIC):
            pa = pal_arr(pal)
            for n, f in F(tag).items():
                if f.w <= 0 or f.h <= 0:
                    continue
                Pp = f.positions()
                v = base + pt.n3c(Pp * 2.0) * 0.3 - f.normal()[1] * 0.8 + 0.9 * (edge_dist(f) == 0) + rng.normal(0, 0.15, (f.h, f.w))
                f.view()[:] = pa[np.clip(np.round(v), 0, len(pal) - 1).astype(int)]

        plastic('body', 2.4)
        plastic('clamp', 3.0, METAL)
        plastic('hub', 2.6)
        plastic('column', 3.0, METAL)
        plastic('ball', 3.2, METAL)
        plastic('leg0', 2.2)
        for n, f in F('foot0').items():
            f.fill(P(RUBBER, 1))
        plastic('button', 4.0, METAL)
        fb = F('body')
        f = fb['front']                                          # 12 x 8: a small mono status screen, vents
        for x in range(6, 11):
            for y in range(1, 4):
                f.set(x, y, P(GLASS, 1))
        f.set(7, 2, P(RED, 3))
        for x in range(6, 11):
            f.set(x, 6, P(PLASTIC, 1) if x % 2 else P(PLASTIC, 4))
        for n in ('left', 'right'):                              # side: the battery door seam, a tiny logo plate
            fc = fb[n]
            for y in range(1, fc.h - 1):
                fc.dl(1, y, -0.08)
            fc.set(3, 3, P(METAL, 4))
            fc.set(4, 3, P(METAL, 4))
        fl = F('lens')
        for n, fc in fl.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, P(METAL, 2 + (1 if (x + y) % 3 == 0 else 0)))
        f = fl['front']                                          # 4 x 4: the lens - a glass eye with a curved glint
        pt.pattern(f, ['rggr', 'gGgg', 'ggkg', 'rggr'], {'r': (METAL, 4), 'g': (GLASS, 2), 'G': (GLASS, 5), 'k': (GLASS, 0)},
                   jitter=False)
        el = EF('lens')['front']
        el.set(1, 1, C('#141c30'))
        el.set(2, 1, C('#0a0f1c'))
        for n, fc in F('led').items():
            fc.fill(P(RED, 2))
        fe = EF('led')
        for n, fc in fe.items():
            fc.fill(C('#ff2a1e'))
        fe['top'].set(0, 0, C('#ff8a70'))
        fs = F('screen')['front']                                # 10 x 6: the rear screen, the live view on it
        ef = EF('screen')['front']
        for y in range(fs.h):
            for x in range(fs.w):
                fs.set(x, y, C('#05080a'))
                if 0 < x < fs.w - 1 and 0 < y < fs.h - 1:
                    v = 0.25 + 0.2 * math.sin(x * 0.9 + y * 0.4) + rng.normal(0, 0.06)
                    if y >= 3 and 3 <= x <= 5:
                        v += 0.25                                # a pale shape in the live view
                    c = np.array([18, 42, 36]) * v * 1.4
                    ef.set(x, y, np.array([c[0], c[1], c[2], 255.0]))
        ef.set(1, 1, C('#5a0c0c'))                               # the REC dot on the screen
        return pt.img, pt.eyes

    anim = '''
// IDLE: still; the REC LED blinks in real time
this.led.visible = (ageInTicks % 40.0F) < 28.0F;
if (pose == FALLEN) {
    // knocked over: tips to its right with gravity, hits the floor at tick 5, bounces, the camera twisting in its clamp
    float fall = easeIn(Mth.clamp(pt / 5.0F, 0.0F, 1.0F));
    float bounce = pt > 5.0F ? (float) Math.exp(-0.5F * (pt - 5.0F)) * Mth.sin((pt - 5.0F) * 1.5F) : 0.0F;
    this.rig.zRot = -1.45F * fall + 0.14F * bounce;
    this.cam.yRot = 0.35F * span(pt, 4.0F, 6.0F) + 0.1F * bounce;
    this.cam.zRot = -0.25F * span(pt, 4.5F, 7.0F);
    this.leg1.xRot = this.leg1.xRot + 0.15F * fall;
    blend = 0.0F;
}
'''


SPEC = Camera

import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.shelf(), 1: SC.shelf()}
POSE_TIMES = {0: 40.0, 1: 40.0}
VIEW_ORTHO = 0.26
VIEW_CENTER = 0.17
TURN_SIZE = (150, 150)
NIGHT_EYE = ((0.45, 0.4, 0.8), (0.0, 0.12, 0.0))
IR_EYE = ((0.7, 0.7, 0.9), (0.0, 0.12, 0.0))


def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    ims = []
    for (yv, pv, lab) in ((0, 8, 'front (the lens)'), (-35, 14, '3/4'), (-90, 6, 'side'), (160, 12, 'back: the screen'),
                          (-30, 55, 'from above')):
        ims.append((lab, b.view(0, 40, yaw_view=yv, pitch_view=pv, size=(170, 170), scene=SC.shelf(wall=yv < 100))))
    ims.append(('LED off phase', b.view(0, 40, yaw_view=-35, pitch_view=14, size=(170, 170), scene=SC.shelf(),
                                        pose_fn=lambda P: setattr(P['led'], 'visible', False))))
    ims.append(('dark room', b.view(0, 40, size=(190, 170), eye=(0.5, 0.35, 0.9), target=(0.0, 0.12, 0.0), look='night',
                                    scene=SC.shelf(), fov=50, tv=(2.0, 0.4, 1.5))))
    ims.append(('dark room, from behind', b.view(0, 40, size=(190, 170), eye=(-0.4, 0.4, -0.8), target=(0.0, 0.12, 0.0),
                                                 look='night', scene=SC.shelf(wall=False), fov=50, tv=(2.0, 0.4, 1.5))))
    rows.append(('定点カメラ: IDLE (0) - the REC LED and the rear screen on the eyes layer', ims))
    ims = []
    for ptv in (0, 2, 4, 5, 6, 8, 12, 30):
        ims.append((f'pt {ptv}', b.view([(0, 0), (100, 1)], 100 + ptv, yaw_view=-10, pitch_view=12, size=(140, 140), ortho=0.34,
                                        center_y=0.14, scene=SC.shelf())))
    rows.append(('FALLEN (1): knocked over to its right, a bounce, the camera twisted in its clamp', ims))
    return rows
