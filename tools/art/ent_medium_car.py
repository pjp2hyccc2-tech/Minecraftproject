"""The medium's car - hitbox 1.6 x 1.5, the model at true scale: an old dark-green kei hatchback (軽自動車), 3.25 blocks
long, 1.4 wide, 1.4 high, wet from the rain. See-through windows (cutout) with a minimal interior: seats, the
dashboard, a right-hand-drive wheel, the rear-view mirror with a small omamori swinging on its string, a dim dome
light (eyes layer). Yellow kei plates. Poses (read through `instanceof Animated`, 0 otherwise): 0 CLOSED,
1 DOOR_OPEN (the driver's door - right side - swings open on its front hinge)."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import Painter, pal_arr, P

GREEN = ['#0b1510', '#112018', '#172a20', '#1e3529', '#264233', '#30503e', '#3d604c', '#4f7360']
TRIM = ['#151617', '#1e2022', '#2a2d30', '#3a3e42']
CHROME = ['#4a4e52', '#6a7075', '#8d9398', '#b2b7bb', '#d6d9dc']
GLASS = ['#0c1116', '#141b22', '#1e2832', '#2c3a47', '#455868']
TYRE = ['#08080a', '#101012', '#18181b', '#222226']
SEAT = ['#16151a', '#1f1d24', '#29272f', '#34313b']
PLATE = ['#b8962a', '#d8b43a', '#ecc94e']
RED = ['#3a0a0c', '#5e1214', '#861c1c', '#b02a26']
AMBER = ['#5e3a08', '#8a560c', '#b87612']
LAMP = ['#6d6a5a', '#9a9680', '#c6c2a8', '#e6e2c8']


class MediumCar(Spec):
    id = 'medium_car'
    java_name = 'MediumCarModel'
    module = 'ent_medium_car'
    translucent = False
    plain_entity = True
    px = 1
    tex_w = 128
    tex_h = None
    height = 1.45
    poses = [('CLOSED', 0), ('DOOR_OPEN', 1)]
    doc = """The medium's car: an old dark-green kei hatchback, wet from the rain, at true scale (about 3.25 blocks long,
    1.4 wide and high - longer than its 1.6 x 1.6 hitbox: the front and back overhang it). Cutout windows show the
    interior: seats, dashboard, a right-hand-drive steering wheel, the rear-view mirror with a small omamori
    swinging on its string; a dim dome light (eyes layer). Yellow kei plates.
    Poses (read through `instanceof Animated`, 0 otherwise): 0 CLOSED, 1 DOOR_OPEN (the driver's door on the right
    side swings open on its front hinge over 14 ticks; closing is a quick slam)."""

    def build(self):
        r = self.root
        car = Part('car', (0, 0, 0))
        # lower body in three sections (front with the hood, the door section, the hatch end)
        car.cube(Cube((-11, 13, -26), (22, 8, 18), 'body', tag='front'))
        car.cube(Cube((-11, 13, -8), (22, 8, 17), 'body', tag='mid'))
        car.cube(Cube((-11, 12, 9), (22, 9, 17), 'body', tag='rear'))
        # the greenhouse: a lower and a shorter upper box (stepped rake of the windshield and the hatch)
        car.cube(Cube((-10, 7, -9), (20, 6, 33), 'cabin', tag='cabin_low', faces=('right', 'front', 'left', 'back')))
        car.cube(Cube((-10, 2, -5), (20, 5, 28), 'cabin', tag='cabin_high'))
        car.cube(Cube((-11, 19, -27), (22, 2, 1), 'bumper', tag='bumper_f'))
        car.cube(Cube((-11, 19, 26), (22, 2, 1), 'bumper', tag='bumper_r'))
        car.cube(Cube((-12, 9, -9), (1, 2, 2), 'trim', tag='mirror_r'))
        car.cube(Cube((11, 9, -9), (1, 2, 2), 'trim', tag='mirror_l'))
        for i, (x, z) in enumerate(((-11, -17), (8, -17), (-11, 15), (8, 15))):
            car.cube(Cube((x, 17, z - 3.5 + 0.5), (3, 7, 7), 'tyre', tag=f'wheel{i}'))
        # interior
        car.cube(Cube((-10, 11, -9), (20, 2, 4), 'dash', tag='dash'))
        car.cube(Cube((-9, 15, -1), (7, 2, 6), 'seat', tag='seat_r'))
        car.cube(Cube((-9, 7, 4), (7, 8, 2), 'seat', tag='back_r'))
        car.cube(Cube((2, 15, -1), (7, 2, 6), 'seat', tag='seat_l'))
        car.cube(Cube((2, 7, 4), (7, 8, 2), 'seat', tag='back_l'))
        car.cube(Cube((-9, 13, 10), (18, 2, 7), 'seat', tag='rear_seat'))
        car.cube(Cube((-9, 6, 16), (18, 7, 2), 'seat', tag='rear_back'))
        car.cube(Cube((-2, 3, -4), (3, 1, 1), 'trim', tag='rvmirror'))
        car.cube(Cube((-1, 3, 5), (2, 1, 2), 'dome', tag='dome', faces=('bottom', 'right', 'front', 'left', 'back')))
        wheel = Part('steering', (-5.5, 10.5, -5.5), rot=(0.55, 0, 0), cubes=[Cube((-2, -2, 0), (4, 4, 1), 'trim', tag='steer')])
        charm = Part('omamori', (-0.5, 4, -3.5), cubes=[Cube((0, 0, 0), (1, 1, 1), 'cord', tag='cord'),
                                                         Cube((-0.5, 1, 0), (2, 2, 1), 'charm', tag='charm')])
        # the driver's door (right side, -x), hinged at its front edge
        door = Part('door', (-11, 0, -8), cubes=[Cube((-1, 13, 0), (1, 8, 17), 'body', tag='door'),
                                                Cube((-1, 7, 1), (1, 6, 15), 'cabin', tag='door_win')])
        car.add(wheel, charm, door)
        r.add(car)

    # ================================================================================================ texture
    def paint(self):
        pt = Painter(self, 3131)
        F, EF, nz = pt.F, pt.EF, pt.nz
        rng = np.random.default_rng(31)

        def paintwork(Pp, nrm, n):
            # a little lighter on top (wet sheen), darker low down (road grime)
            v = -1.1 * np.clip((Pp[..., 1] - 17.0) / 4.0, 0, 1)
            if n == 'top':
                v = v + 1.0
            return v

        def droplets(f, density=0.06, col=None):
            for y in range(f.h):
                for x in range(f.w):
                    if rng.random() < density:
                        f.dl(x, y, 0.14 if col is None else 0)
                        if col is not None:
                            f.set(x, y, col)

        for tag in ('front', 'mid', 'rear', 'door'):
            fs = pt.base(tag, GREEN, 3.5, amp=0.12, fine=0.1, top=0.0, fn=paintwork)
            for n, f in fs.items():
                if n in ('top', 'front', 'back', 'right', 'left'):
                    droplets(f, 0.01)
                if n in ('right', 'left'):
                    for x in range(f.w):                       # a soft reflection band along the flank
                        f.dl(x, 2, 0.05)
                if n == 'bottom':
                    f.fill(P(TRIM, 0))
            for n in ('right', 'left', 'front', 'back'):
                f = fs[n]
                for x in range(f.w):                          # the belt line crease, rust and mud at the sill
                    f.dl(x, 0, 0.08)
                    f.set(x, f.h - 1, P(TRIM, 1))
                    if rng.random() < 0.25:
                        f.set(x, f.h - 2, P(['#3a2a1c', '#4a3524'], rng.random()))
        # wheel arches on the sides (dark) where the wheels sit
        for tag, zs in (('front', (9,)), ('rear', (6,))):
            for n in ('right', 'left'):
                f = F(tag)[n]
                for y in range(f.h - 5, f.h):
                    for dz in range(-4, 5):
                        if dz * dz + (y - f.h) ** 2 < 26:
                            for z0 in zs:
                                x = (f.w - 1 - (z0 + dz)) if n == 'right' else (z0 + dz)
                                if 0 <= x < f.w:
                                    f.set(x, y, P(TRIM, 0))
        # hood (front top), grille, headlights, the yellow kei plate
        f = F('front')['front']                               # 22 x 8
        for y in range(1, 6):
            for x in range(6, 16):
                f.set(x, y, P(TRIM, 0.5 + (0.8 if y % 2 else 0)))
        for (x0, x1) in ((1, 5), (17, 21)):
            for y in range(1, 5):
                for x in range(x0, x1):
                    f.set(x, y, P(LAMP, 2.0 + (1.2 if (x, y) in ((x0 + 1, 2),) else 0) + nz(f, x, y, 0.4, 0.2)))
            f.set(x0, 5, P(AMBER, 1.5))
            f.set(x1 - 1, 5, P(AMBER, 1.5))
        for y in range(5, 8):
            for x in range(8, 14):
                f.set(x, y, P(PLATE, 1.5 + nz(f, x, y, 0.3, 0.2)))
        for (x, y) in ((9, 6), (10, 6), (12, 6), (11, 5)):
            f.set(x, y, C('#1a1a12'))
        ft = F('front')['top']                                # 22 x 18: hood
        for y in range(ft.h):
            for x in range(ft.w):
                if rng.random() < 0.06:
                    ft.dl(x, y, 0.15)
            ft.dl(ft.w // 2, y, 0.05)
        for x in range(ft.w):                                  # wipers resting at the cowl
            if 3 <= x <= 9 or 12 <= x <= 18:
                ft.set(x, 1, P(TRIM, 0.5))
        # hatch: tail lights, plate, the rear window frame
        f = F('rear')['back']                                 # 22 x 9
        for (x0, x1) in ((0, 3), (19, 22)):
            for y in range(1, 6):
                for x in range(x0, x1):
                    f.set(x, y, P(RED, 1.6 + (1.2 if y == 2 else 0) + nz(f, x, y, 0.3, 0.2)))
            f.set(x0 if x0 == 0 else x1 - 1, 6, P(AMBER, 1.5))
        for y in range(5, 8):
            for x in range(8, 14):
                f.set(x, y, P(PLATE, 1.5 + nz(f, x, y, 0.3, 0.2)))
        for (x, y) in ((9, 6), (11, 6), (12, 6), (10, 5)):
            f.set(x, y, C('#1a1a12'))
        f.set(10, 2, P(CHROME, 3))
        f.set(11, 2, P(CHROME, 3))
        # door section, right side (behind the door: the jamb and the seat edge, seen when it opens)
        f = F('mid')['right']                                 # 17 x 8
        for y in range(f.h):
            for x in range(f.w):
                f.set(x, y, P(TRIM, 1.0 + nz(f, x, y, 0.4, 0.2)))
        for x in range(f.w):
            f.set(x, 0, P(GREEN, 3))
            f.set(x, f.h - 1, P(CHROME, 1))
        f = F('mid')['left']                                  # the passenger door seams and handle
        for y in range(f.h):
            f.dl(1, y, -0.12)
            f.dl(f.w - 2, y, -0.12)
        f.set(f.w - 5, 2, P(CHROME, 3))
        f.set(f.w - 4, 2, P(CHROME, 3))
        fd = F('door')['right']                               # 17 x 8, outer skin of the driver's door
        for y in range(fd.h):
            fd.dl(0, y, -0.12)
            fd.dl(fd.w - 1, y, -0.12)
        fd.set(fd.w - 4, 2, P(CHROME, 3))
        fd.set(fd.w - 3, 2, P(CHROME, 3))
        fdl = F('door')['left']                               # inner door panel (seen when open)
        for y in range(fdl.h):
            for x in range(fdl.w):
                fdl.set(x, y, P(SEAT, 1.5 + nz(fdl, x, y, 0.4, 0.2)))
        fdl.set(fdl.w - 4, 2, P(CHROME, 2))
        # ---------------------------------------------------------------- glass: the windows show the interior
        def glass(tag, frame=1, which=('right', 'front', 'left', 'back'), open_top=False, open_bottom=False):
            fs = F(tag)
            for n, f in fs.items():
                if n not in which:
                    continue
                for y in range(f.h):
                    for x in range(f.w):
                        edge = x < frame or x >= f.w - frame or (y < frame and not open_top) or \
                            (y >= f.h - frame and not open_bottom)
                        if edge:
                            f.set(x, y, P(TRIM, 1.2 + nz(f, x, y, 0.4, 0.2)))
                            continue
                        k = (x + y * 1.4) % 23.0
                        refl = 2.0 <= k < 3.4 or 4.0 <= k < 4.8
                        drop = rng.random() < 0.03
                        if refl or drop:
                            f.set(x, y, P(GLASS, 3.4 if refl else 4.2))
                        else:
                            f.clear(x, y)
            return fs
        glass('cabin_low', frame=1, open_top=True)
        fs = glass('cabin_high', frame=1, open_bottom=True)
        top = fs['top']                                       # the roof
        for y in range(top.h):
            for x in range(top.w):
                v = 4.3 + nz(top, x, y, 0.15, 0.1)
                if rng.random() < 0.02:
                    v += 1.2
                top.set(x, y, P(GREEN, v))
        for x in range(top.w):
            top.set(x, 0, P(TRIM, 1))
            top.set(x, top.h - 1, P(TRIM, 1))
        fs['bottom'].fill(P(SEAT, 2.5))                       # the headliner (inside)
        # B-pillars: the window panes are split on the sides
        for tag in ('cabin_low', 'cabin_high'):
            for n in ('right', 'left'):
                f = F(tag)[n]
                bx = (f.w - 1 - 14) if n == 'right' else 14
                for y in range(f.h):
                    f.set(bx, y, P(TRIM, 1))
                    f.set(bx + (1 if n == 'left' else -1), y, P(TRIM, 1))
        # the driver's side cabin glass belongs to the door: clear it on the cabin where the door window sits
        f = F('cabin_low')['right']                           # 33 wide (z from back 23 .. front -10)
        for y in range(f.h):
            for x in range(f.w):
                z = 24 - x - 0.5
                if -7 <= z <= 7:
                    f.clear(x, y)
        fw = glass('door_win', frame=1, which=('right', 'left'))
        # ---------------------------------------------------------------- trim, bumpers, mirrors, tyres
        for tag in ('bumper_f', 'bumper_r'):
            pt.base(tag, CHROME, 2.0, amp=0.5, fine=0.4, top=1.0)
        for tag in ('mirror_r', 'mirror_l', 'rvmirror', 'steer', 'cord'):
            pt.base(tag, TRIM, 1.4, amp=0.4, fine=0.3, top=0.6)
        for i in range(4):
            fs = pt.base(f'wheel{i}', TYRE, 1.4, amp=0.3, fine=0.3, top=0.3)
            for n in ('right', 'left'):
                f = fs[n]                                     # 7 x 7 hubcap
                for y in range(f.h):
                    for x in range(f.w):
                        d = math.hypot(x - 3, y - 3)
                        if d < 2.6:
                            f.set(x, y, P(CHROME, 2.2 - d * 0.4 + nz(f, x, y, 0.3, 0.2)))
                        if d < 0.8:
                            f.set(x, y, P(CHROME, 0.5))
        for tag in ('seat_r', 'back_r', 'seat_l', 'back_l', 'rear_seat', 'rear_back', 'dash'):
            pt.base(tag, SEAT, 1.8, amp=0.4, fine=0.3, top=0.8)
        for n, f in F('charm').items():                       # the omamori: red brocade with gold thread
            for y in range(f.h):
                for x in range(f.w):
                    f.set(x, y, C('#9a1c22') if (x + y) % 3 else C('#c8a040'))
        for n, f in F('dome').items():
            f.fill(P(LAMP, 1.5))
        for n, f in EF('dome').items():
            f.fill(C('#8a6a34'))
        EF('dome')['bottom'].fill(C('#e0b062'))
        for tag in ('seat_r', 'seat_l', 'back_r', 'back_l'):  # the dome light's faint warmth on the seat tops
            ef = EF(tag)['top']
            for y in range(ef.h):
                for x in range(ef.w):
                    if (x + y) % 2 == 0:
                        ef.set(x, y, C('#1e1508'))
        return pt.img, pt.eyes

    # ================================================================================================ animation
    anim = '''
// the driver's door swings open on its front hinge (with a little bounce on the check strap)
if (pose == DOOR_OPEN) {
    float o = ease(pt / 14.0F);
    this.door.yRot = -1.05F * o - 0.06F * Mth.sin(Math.min(1.0F, Math.max(0.0F, (pt - 14.0F) / 8.0F)) * 3.14159F);
} else {
    blend = 5.0F;
}
// the omamori swings on its string: a slow idle sway, excited for a while after the door moves
float kick = pose == DOOR_OPEN ? (float) Math.exp(-pt * 0.08F) : (float) Math.exp(-pt * 0.12F);
this.omamori.zRot = Mth.sin(t * 0.09F) * 0.06F + Mth.sin(pt * 0.5F) * 0.35F * kick;
this.omamori.xRot = Mth.sin(t * 0.07F + 1.0F) * 0.05F;
'''


SPEC = MediumCar


import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.lot(), 1: SC.lot()}
WALKING = ()
NAKED_ALPHA = 1.0
TURNAROUND = False


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sc = SC.lot()
    ims = []
    for (yv, lab) in ((-30, 'front 3/4 (driver side)'), (-90, 'right side (driver)'), (-150, 'rear 3/4'), (90, 'left side'), (0, 'front')):
        ims.append((lab, b.view(0, 40, yaw_view=yv, pitch_view=14, size=(300, 220), ortho=2.0, scene=sc)))
    rows.append(('CLOSED (0)', ims))
    ims = []
    for ptv in (0, 5, 10, 14, 22):
        ims.append((f'pt {ptv}', b.view([(0, 0), (100, 1)], 100 + ptv, yaw_view=-60, pitch_view=14, size=(300, 220),
                                        ortho=2.0, scene=sc)))
    ims.append(('slam pt 3', b.view([(0, 1), (100, 0)], 103, yaw_view=-60, pitch_view=14, size=(300, 220), ortho=2.0, scene=sc)))
    rows.append(('DOOR_OPEN (1): the driver\'s door swings open; closing slams', ims))
    ims = []
    ims.append(('through the windshield', b.view(0, 40, size=(300, 240), eye=(-0.4, 1.25, 3.0), target=(0, 0.75, 0), scene=sc, fov=50)))
    ims.append(('omamori close', b.view(0, 40, size=(300, 240), eye=(-0.2, 1.2, 1.9), target=(0.0, 1.0, 0.3), scene=sc, fov=40)))
    ims.append(('night: the dome light', b.view(0, 40, size=(300, 240), eye=(-2.8, 1.62, 2.6), target=(0, 0.6, 0), night=True,
                                               scene=sc, fov=50, flash=False, amb=0.22)))
    ims.append(('night: door open', b.view([(0, 0), (100, 1)], 140, size=(300, 240), eye=(-3.4, 1.62, 1.0), target=(0, 0.6, 0),
                                           night=True, scene=sc, fov=50, flash=False, amb=0.22)))
    rows.append(('details', ims))
    return rows
