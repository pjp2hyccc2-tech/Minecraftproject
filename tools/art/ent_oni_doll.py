"""鬼（人形）the oni doll - 0.45 x 0.6. The cheap white plush rabbit from the western room's shelf after what you did
to it: soaked grey from the bath, button eyes (the left one torn out and hanging by its thread), drooping ears, the
belly cut open and sewn shut with crude red stitches, the stab wound on its chest bulging with rice, red thread
wound round and round its body, and the kitchen knife (longer than its arm) bound into its right paw with the same
thread. Poses: 0 FLOAT, 1 SIT, 2 CRAWL, 3 WALK, 4 STAND, 5 TURN, 6 PEEK, 7 LUNGE, 8 LIMP, 9 BURN.

The whole animation runs on the doll's own clock (its pose time accumulated across poses): the game freezes it by
holding the pose time and renders stop-motion by stepping it (or by glitch >= 0.5, which drops frames in the model).
4 texels per model pixel (UV layout 32 x 32 -> 128 x 128): the doll is a third of a person's size, so it gets the
same texel count per body part as REISATSU's spirits."""
import math

import numpy as np

from common import C
from ent_model import Spec, Part, Cube
from ent_lib import card, Painter, pal_arr, P

# palettes (dark -> light)
FUR = ['#1a1c1b', '#242725', '#2f3230', '#3b3e3b', '#484b47', '#565954', '#656862', '#757770', '#86887f', '#97988f',
       '#a8a99f', '#b8b8ad', '#c7c7bb']
EAR_IN = ['#3a2c2b', '#4f3c3a', '#65504c', '#7a615c', '#8d726b', '#9e827a']
NOSE = ['#2a1416', '#43201f', '#5e2c2b', '#7a3b38']
RED = ['#2e0707', '#4a0b0b', '#6a1110', '#8c1915', '#ab241c', '#c8392a', '#de5640']
RICE = ['#5f594b', '#7f7867', '#a39b86', '#c3bba5', '#dbd4c0', '#ece7d8', '#f7f4ea']
STUFF = ['#4d4a43', '#6d6960', '#8f8a7f', '#b0aa9e', '#cbc5b9']
BUTTON = ['#030304', '#09090b', '#121215', '#1d1d22', '#2c2c33', '#45464f', '#6d6f79']
STEEL = ['#2c3034', '#40454a', '#565c62', '#6f767c', '#899096', '#a5abb0', '#c3c8cc', '#e1e4e6']
HANDLE = ['#070707', '#0f0e0e', '#191716', '#242120', '#332f2c']
CHAR = ['#050404', '#0d0a08', '#17110d', '#231912', '#2f2118']
STAIN = C('#5b4a2e')
MILDEW = C('#26301f')


class OniDoll(Spec):
    id = 'oni_doll'
    java_name = 'OniDollModel'
    module = 'ent_oni_doll'
    px = 4
    tex_w = 32
    tex_h = 32
    height = 0.6
    translucent = False
    poses = [('FLOAT', 0), ('SIT', 1), ('CRAWL', 2), ('WALK', 3), ('STAND', 4), ('TURN', 5), ('PEEK', 6), ('LUNGE', 7),
             ('LIMP', 8), ('BURN', 9)]
    doc = """鬼（人形）(the oni doll): the cheap white plush rabbit after the ritual - soaked grey, one button eye torn out and
    hanging by its thread, drooping ears, the belly sewn shut with crude red stitches, the stab wound on its chest
    bulging with rice, red thread wound round and round its body, the kitchen knife bound into its right paw.
    The whole animation runs on the doll's own clock = its pose time accumulated across poses (a pose time stepping
    back within a pose passes no time; limb swing, ageInTicks and head pitch are ignored, except that the water drips
    fall in real time): HOLD THE POSE TIME and the doll is perfectly still (seen directly), STEP IT and it moves in
    stop-motion (seen by a camera); glitch 0..0.5 snaps joints the wrong way for a few ticks, glitch 0.5..1 also
    drops frames (at 1 the pose only changes in whole 3-tick frames). Floppy parts (ears, the hanging eye, the loose
    thread, limp limbs) are damped springs on the same clock, so they too freeze and step with it. The head yaw
    (netHeadYaw, clamped +-80 degrees) is followed on the same clock: SIT, WALK, CRAWL and PEEK turn the head part
    of the way, STAND turns the whole doll (its head lies on its side); TURN, LUNGE, LIMP, BURN, FLOAT ignore it.
    Pose ids: 0 FLOAT (face down in the water, limp, bobbing, every ~23 s an arm jerks; the knife is hidden; put the
    entity at the water surface), 1 SIT (slumped against a wall behind it, head lolling, nodding off and snapping
    awake every 13 s; the knife lying on the floor in its paw),
    2 CRAWL (dragging itself on its arms, legs and ears trailing; one pull every 8 ticks, ~0.28 blocks each, so
    move it at ~0.035 blocks/tick; the knife strikes the floor at clock 16n + 9), 3 WALK (stiff marionette steps
    with a step every 11 ticks covering ~0.2 blocks (~0.018 blocks/tick), swaying as if on strings, the knife dragging
    behind and clanking down at clock 22n + 18.5), 4 STAND (upright, perfectly still, head lying on its shoulder
    at 90 degrees), 5 TURN (the head turns 180 degrees round to the viewer in three jerks at pose ticks 6, 13 and 20 -
    leave the body facing away from the viewer), 6 PEEK (leans its head and left ear out past a door frame on its
    left: the frame edge belongs ~0.17 blocks to its left; the head emerges in three little starts over 30 ticks),
    7 LUNGE (crouch 0-2, the knife raised over 0-4, springs from tick 2: ~0.56 blocks forward and ~0.2 up by tick 6 on its own (carry the
    entity on to the camera), the knife raised and the mouth seam torn open, the knife comes down at ticks 6-8:
    cut to the jump scare at ~6-7), 8 LIMP (shrieks and spasms 0-3, the knife flies out of its
    paw and vanishes at tick 3.5 - drop the knife item there - falls on its back by tick 9 with a plush bounce, rice pours from the
    wound), 9 BURN (lying on its back: thrashing, then curling up and shrinking over ~200 ticks while burnt patches
    glowing on the eyes layer spread over it).
    The eyes layer carries the button-eye glints, the knife edge, the drips and the embers of BURN."""

    # ------------------------------------------------------------------------------------------------ geometry
    def build(self):
        r = self.root
        body = Part('body', (0, 21.0, 0))
        torso = Part('torso', (0, 0, 0))
        torso.cube(Cube((-1.875, -3.5, -1.375), (3.75, 3.5, 2.75), tag='torso'))
        torso.cube(Cube((-2.125, -2.0, -1.625), (4.25, 2.25, 2.75), tag='belly'))
        torso.cube(Cube((-0.75, -1.5, 1.375), (1.5, 1.25, 0.75), tag='tail'))
        torso.cube(Cube((0.25, -3.25, -1.875), (1.5, 1.25, 0.5), tag='bulge'))
        sides = ('right', 'front', 'left', 'back')
        ring_a = Part('ring_a', (0, -2.85, 0), rot=(0.1, 0, 0.32),
                      cubes=[Cube((-2.125, -0.125, -1.625), (4.25, 0.25, 3.25), tag='ring_a', faces=sides)])
        thread_end = Part('thread_end', (1.75, -2.35, 1.45),
                          cubes=[Cube((-0.125, 0, 0), (0.25, 2.25, 0), tag='thread_end', faces=('front',))])
        drip_a = Part('drip_a', (-0.4, 0.3, -1.0))
        drip_a.add(Part('drip_a_fall', (0, 0, 0), cubes=[Cube((-0.125, 0, -0.125), (0.25, 0.5, 0.25), tag='drop')]))
        rice_a = Part('rice_a', (1.1, -2.05, -1.8))
        rice_a.add(Part('rice_a_fall', (0, 0, 0), cubes=[Cube((-0.125, 0, -0.125), (0.25, 0.25, 0.5), tag='grain')]))
        rice_b = Part('rice_b', (0.6, -2.05, -1.8))
        rice_b.add(Part('rice_b_fall', (0, 0, 0), cubes=[Cube((-0.125, 0, -0.125), (0.25, 0.25, 0.5), tag='grain_b',
                                                              uv_of='grain')]))

        # burnt patches (BURN only): cards just off the surfaces, base = char, eyes = glowing edges
        def ember(name, pivot, rot, uv=None):
            return Part(name, pivot, rot, cubes=[Cube((-1.0, -1.0, 0), (2.0, 2.0, 0), tag=name, faces=('front',),
                                                      uv_of=uv)])
        em_a = ember('ember_a', (-0.6, -0.9, -1.68), (0, 0, 0))
        em_b = ember('ember_b', (0.5, -2.0, 1.42), (0, math.pi, 0))
        torso.add(ring_a, thread_end, drip_a, rice_a, rice_b, em_a, em_b)

        head = Part('head', (0, -3.5, 0))
        head.cube(Cube((-2.5, -4.25, -2.125), (5.0, 4.25, 4.0), tag='head'))
        head.cube(Cube((-1.25, -1.75, -2.875), (2.5, 1.0, 0.75), tag='muzzle'))
        head.cube(Cube((-1.875, -2.875, -2.375), (1.25, 1.25, 0.25), tag='eye_r'))
        jaw = Part('jaw', (0, -0.75, -2.125), cubes=[Cube((-1.0, 0, -0.75), (2.0, 0.5, 0.75), tag='jaw')])
        maw = card('maw', (0, -0.75, -2.575), (-1.0, 0, 0), (2.0, 0.5, 0), 'maw')
        eye_thread = Part('eye_thread', (1.25, -2.25, -2.175),
                          cubes=[Cube((-0.125, 0, 0), (0.25, 1.0, 0), tag='eye_thread', faces=('front',))])
        eye_thread.add(Part('eye_button', (0, 1.0, 0), cubes=[Cube((-0.625, 0, -0.125), (1.25, 1.25, 0.25),
                                                                  tag='eye_button', uv_of='eye_r')]))
        ears = []
        for side, pre in ((-1, 'r'), (1, 'l')):
            # drooping at rest (down the sides of the head): flop() then never has to turn an ear through 180 degrees
            ear = Part(f'ear_{pre}', (side * 1.3, -4.25, 0.25), rot=(0.2, 0, -side * 0.75),
                       cubes=[Cube((-0.875, 0, -0.25), (1.75, 2.25, 0.5), tag=f'ear_{pre}')])
            tip = Part(f'ear_{pre}_tip', (0, 2.25, 0), rot=(0, 0, -side * 0.25),
                       cubes=[Cube((-0.875, 0, -0.25), (1.75, 2.5, 0.5), tag=f'ear_{pre}_tip')])
            ear.add(tip)
            ears.append(ear)
        drip_c = Part('drip_c', (0, 2.5, 0))
        drip_c.add(Part('drip_c_fall', (0, 0, 0), cubes=[Cube((-0.125, 0, -0.125), (0.25, 0.5, 0.25), tag='drop_c',
                                                              uv_of='drop')]))
        ears[0].children[0].add(drip_c)
        em_c = ember('ember_c', (2.55, -2.2, 0.0), (0, math.pi / 2, 0), 'ember_a')
        em_d = ember('ember_d', (-0.7, -3.2, -2.18), (0, 0, 0.4), 'ember_b')
        head.add(jaw, maw, eye_thread, *ears, em_c, em_d)

        arms = {}
        for side, pre in ((-1, 'r'), (1, 'l')):
            arm = Part(f'arm_{pre}', (side * 2.375, -3.0, 0), rot=(0, 0, -side * 0.1),
                       cubes=[Cube((-0.625, -0.25, -0.625), (1.25, 2.25, 1.25), tag=f'arm_{pre}')])
            paw = Part(f'paw_{pre}', (0, 2.0, 0), cubes=[Cube((-0.625, 0, -0.625), (1.25, 1.0, 1.25), tag=f'paw_{pre}')])
            arm.add(paw)
            arms[pre] = (arm, paw)
        knife = Part('knife', (0, 0.6, 0))
        knife.cube(Cube((-0.25, -0.5, -0.375), (0.5, 1.75, 0.75), tag='handle'))
        knife.cube(Cube((-0.125, 1.25, -0.625), (0.25, 3.75, 1.0), tag='blade'))
        arms['r'][1].add(knife)
        drip_b = Part('drip_b', (0, 1.0, 0))
        drip_b.add(Part('drip_b_fall', (0, 0, 0), cubes=[Cube((-0.125, 0, -0.125), (0.25, 0.5, 0.25), tag='drop_b',
                                                              uv_of='drop')]))
        arms['l'][1].add(drip_b)
        em_f = ember('ember_f', (0.0, 1.0, -0.68), (0, 0, 0), 'ember_a')
        arms['l'][0].add(em_f)

        legs = []
        for side, pre in ((-1, 'r'), (1, 'l')):
            leg = Part(f'leg_{pre}', (side * 1.05, 0, 0), cubes=[Cube((-0.875, -0.25, -0.875), (1.75, 2.0, 1.75),
                                                                      tag=f'leg_{pre}')])
            foot = Part(f'foot_{pre}', (0, 1.75, 0), cubes=[Cube((-0.875, 0, -1.5), (1.75, 1.25, 2.5), tag=f'foot_{pre}')])
            leg.add(foot)
            legs.append(leg)
        em_e = ember('ember_e', (0.0, 0.8, -0.93), (0, 0, 0.7), 'ember_b')
        legs[0].add(em_e)
        body.add(torso, head, arms['r'][0], arms['l'][0], *legs)
        r.add(body)

    # ------------------------------------------------------------------------------------------------ texture
    def paint(self):
        pt = Painter(self, 9031)
        F, EF = pt.F, pt.EF
        rng = np.random.default_rng(77)
        furp = pal_arr(FUR)
        red = pal_arr(RED)
        # tea-coloured stains where the bath water sat in it, in model space (rest pose): centre, radius, strength
        stains = [((-1.6, 22.8, -0.5), 1.6, 0.6), ((1.9, 15.2, 0.6), 1.3, 0.45), ((0.4, 19.6, 1.6), 1.5, 0.4),
                  ((-2.2, 18.6, -1.4), 0.9, 0.5), ((1.2, 23.6, -1.4), 1.4, 0.55), ((-0.8, 13.6, -1.6), 0.9, 0.35)]

        def edge_dist(f):
            ys, xs = np.mgrid[0:f.h, 0:f.w]
            return np.minimum(np.minimum(xs, f.w - 1 - xs), np.minimum(ys, f.h - 1 - ys))

        # wet plush mats into clumps: a jittered 3D grid of clump centres (rest pose, model pixels)
        from scipy.spatial import cKDTree
        cr = np.random.default_rng(4242)
        g = np.mgrid[-4.0:4.01:0.9, 10.0:25.01:0.9, -4.0:3.01:0.9].reshape(3, -1).T
        seeds = g + cr.uniform(-0.38, 0.38, g.shape)
        seed_tone = cr.normal(0, 0.35, len(seeds))
        tree = cKDTree(seeds)

        def clumps(Pp):
            """(light-top / dark-bottom gradient inside each clump, crevice darkness between them, per-clump tone)"""
            q = Pp.reshape(-1, 3)
            dd, ii = tree.query(q, k=2)
            rel = (q[:, 1] - seeds[ii[:, 0], 1]) / 0.6
            grad = -np.clip(rel, -1, 1)
            crev = np.clip(1.0 - (dd[:, 1] - dd[:, 0]) / 0.22, 0, 1)
            sh = Pp.shape[:-1]
            return grad.reshape(sh), crev.reshape(sh), seed_tone[ii[:, 0]].reshape(sh)

        def fur_v(f, base, wet, puff):
            """Wet plush value (palette index): soaked heavier toward the bottom, matted into clumps that catch the
            light on top and go dark underneath and between, puffy cushion edges."""
            Pp = f.positions()
            nrm = f.normal()
            y = Pp[..., 1]
            wetness = (0.3 + 0.7 * np.clip((y - 13.0) / 11.0, 0, 1) ** 1.4) * wet + 0.15 * pt.n3b(Pp * 0.6 + 5.0)
            grad, crev, tone = clumps(Pp)
            fine = pt.n3c(Pp * 3.2)
            v = base - wetness * 2.4 + grad * 0.55 - crev * 1.1 + tone + fine * 0.15 - nrm[1] * 0.45
            e = edge_dist(f)
            v = v - puff * np.select([e == 0, e == 1, e == 2], [1.05, 0.45, 0.15], 0.0)
            for (cx, cy, cz, rad, k) in ((0, 17.5, 0, 2.0, 1.1), (-2.2, 18.0, 0, 1.1, 0.8), (2.2, 18.0, 0, 1.1, 0.8),
                                         (-1.05, 21.0, 0, 1.2, 0.8), (1.05, 21.0, 0, 1.2, 0.8)):
                d = np.sqrt((Pp[..., 0] - cx) ** 2 + (Pp[..., 1] - cy) ** 2 + (Pp[..., 2] - cz) ** 2)
                v = v - k * np.clip(1.0 - d / rad, 0, 1)
            return v + rng.normal(0, 0.13, v.shape)

        def fur(tag, base=8.7, wet=1.0, puff=1.0, which=None):
            fs = F(tag)
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0 or (which and n not in which):
                    continue
                v = fur_v(f, base, wet, puff)
                col = furp[np.clip(np.round(v), 0, len(FUR) - 1).astype(int)].copy()
                Pp = f.positions()
                m = np.zeros(v.shape)
                for (c, rad, k) in stains:
                    d2 = ((Pp - np.array(c)) ** 2).sum(-1)
                    m = np.maximum(m, k * np.exp(-d2 / (rad * rad)) * (0.75 + 0.25 * pt.n3c(Pp * 2.0)))
                m = np.clip(m, 0, 0.7)[..., None]
                col[..., :3] = col[..., :3] * (1 - m) + STAIN[:3] * (0.55 + 0.45 * np.clip(v, 0, 12)[..., None] / 12.0) * m
                spk = rng.random(v.shape) < 0.006
                col[spk, :3] = MILDEW[:3] * rng.uniform(0.8, 1.3)
                f.view()[:] = col
            return fs

        def thread(fs, planes, width=0.13, pinch=0.36):
            """Red thread wound round the body: where tilted planes cut the surface, the fabric pinched beside it."""
            for n, f in fs.items():
                if f.w <= 0 or f.h <= 0 or n in ('top', 'bottom'):
                    continue
                Pp = f.positions()
                v = f.view()
                for (cy, nx, nz) in planes:
                    nn = np.array([nx, 1.0, nz])
                    nn /= np.linalg.norm(nn)
                    d = Pp[..., 0] * nn[0] + (Pp[..., 1] - cy) * nn[1] + Pp[..., 2] * nn[2]
                    near = (np.abs(d) < pinch) & (np.abs(d) >= width)
                    v[near, :3] *= 0.74
                    on = np.abs(d) < width
                    k = np.clip(np.round(3.8 + pt.n3c(Pp * 4.0)[on] * 1.2 + rng.normal(0, 0.45, on.sum())), 0, 6)
                    v[on] = red[k.astype(int)]

        # ---------------------------------------------------------------- body: torso, belly (rice-heavy), tail
        wraps = [(18.35, 0.2, 0.12), (19.55, -0.36, -0.08), (20.55, 0.14, 0.3)]
        fs = fur('torso', base=8.6)
        thread(fs, wraps)
        fb = fur('belly', base=8.2, puff=1.3)
        thread(fb, wraps)
        # the ritual seam down the belly: a dark slit, crude red cross-stitches pulled tight
        f = fb['front']                                          # 17 x 9
        for y in range(f.h):
            f.set(8, y, C('#100d0c'))
            f.dl(9, y, -0.1)
        for y in range(1, f.h, 3):
            for k in (-2, -1, 1, 2):
                f.set(8 + k, y + (1 if abs(k) == 2 else 0) * (1 if k > 0 else -1) // 1, P(RED, 4 if abs(k) == 1 else 3))
            f.set(8, y, P(RED, 5))
        f = fs['front']                                          # 15 x 14: the seam runs up to the chest wound
        for y in range(8, f.h):
            f.set(7, y, C('#100d0c'))
        for y in range(9, f.h, 3):
            f.set(6, y, P(RED, 4))
            f.set(8, y, P(RED, 4))
            f.set(7, y, P(RED, 5))
        # the stab wound around the rice bulge (bulge: x 0.25..1.75, y -3.25..-2.0 -> texels 8.5..14.5, 1..6)
        for y in range(0, 8):
            for x in range(7, 15):
                d = math.hypot((x + 0.5 - 11.5) / 3.6, (y + 0.5 - 3.5) / 3.0)
                if d < 1.0:
                    f.set(x, y, P(FUR, 2.0 + d * 3.0))
                elif d < 1.3:
                    f.dl(x, y, -0.06)
        ft = fur('tail', base=9.4, wet=0.8, puff=1.4)
        # the bulge: rice spilling out of the torn slit - loose grains, shadows between them, darker where wet
        ricep = pal_arr(RICE)
        rr = np.random.default_rng(5)
        for n, fc in F('bulge').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    g = rr.random()
                    v = (5.2 if g < 0.45 else (3.8 if g < 0.75 else 1.6)) + rr.normal(0, 0.4) + (0.4 if n == 'top' else 0)
                    fc.set(x, y, ricep[int(np.clip(round(v), 0, 6))])
        fbg = F('bulge')['front']                                # 6 x 5: torn flaps over the rice
        for (x, y) in ((0, 0), (1, 0), (2, 0), (4, 0), (5, 0), (0, 1), (5, 1), (0, 4), (1, 4), (3, 4), (4, 4), (5, 4), (5, 3)):
            fbg.set(x, y, P(FUR, 4 + ((x + y) % 2)))
        for (x, y) in ((3, 0), (1, 1), (4, 1), (2, 4)):
            fbg.set(x, y, P(FUR, 2))
        for n, fc in F('ring_a').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, red[3 + ((x // 2) % 2) + (1 if rng.random() < 0.2 else 0)])
        for n, fc in F('thread_end').items():
            for y in range(fc.h):
                fc.set(0, y, red[4 - (y % 2)])
            fc.set(0, fc.h - 1, P(RED, 6))
        for n, fc in F('drop').items():
            fc.fill(C('#8a979c'))
        for n, fc in EF('drop').items():
            fc.fill(C('#1b2124'))
        for n, fc in F('grain').items():
            fc.fill(P(RICE, 5))

        # ---------------------------------------------------------------- head
        fh = fur('head', base=9.1, puff=1.2)
        f = fh['front']                                          # 20 x 17
        # the left socket at texel (15, 8): a torn hole, dark inside, dirty stuffing round the ragged rim
        sock = ['...ss....',
                '..sDDss..',
                '.sDHHHDs.',
                'sDHHHHHDs',
                'sDHHHHHDs',
                '.sDHHHDDs',
                '.ssDDDss.',
                '...ss.s..']
        for j, row in enumerate(sock):
            for i, ch in enumerate(row):
                x, y = 11 + i, 4 + j
                if ch == 'H':
                    f.set(x, y, P(BUTTON, 0))
                elif ch == 'D':
                    f.set(x, y, P(BUTTON, 2))
                elif ch == 's':
                    f.set(x, y, P(STUFF, 2 + (x * 3 + y) % 3))
        for (x, y) in ((10, 7), (11, 4), (19, 9), (17, 3), (13, 12)):
            f.dl(x, y, -0.09)
        f.set(15, 9, P(RICE, 5))
        f.set(16, 6, P(STUFF, 1))
        # dirty water has run down from both eyes like tears
        for (x0, y0, ln) in ((14, 11, 6), (16, 11, 5), (4, 11, 4), (6, 11, 3)):
            for k in range(ln):
                f.dl(x0 + k // 4, y0 + k, -0.06 - 0.03 * (k % 2))
        for y in range(0, 10):
            f.dl(10, y, -0.045)                                  # the centre seam of the plush head
        for n in ('top', 'back'):
            fc = fh[n]
            for k in range(fc.h):
                fc.dl(fc.w // 2, k, -0.05)
        # red thread wound tight round the neck: a crease and wraps along the bottom of the head
        for n in ('front', 'left', 'right', 'back'):
            fc = fh[n]
            for x in range(fc.w):
                fc.dl(x, fc.h - 1, -0.1)
                if (x + (n == 'back')) % 4 != 1:
                    fc.set(x, fc.h - 2, P(RED, 3 + (x % 2)))
        # the muzzle: drier and paler; the stitched nose; the mouth seam continues onto the jaw
        fm = fur('muzzle', base=10.4, wet=0.5, puff=0.8)
        f = fm['front']                                          # 10 x 4: a small inverted-triangle nose, the seam below
        for (x, y, v) in ((3, 0, 2), (4, 0, 3), (5, 0, 3), (6, 0, 2), (4, 1, 2), (5, 1, 2)):
            f.set(x, y, P(NOSE, v))
        f.set(5, 2, P(NOSE, 1))
        f.set(5, 3, P(NOSE, 0))
        f.dl(4, 2, -0.05)
        fmt = fm['top']
        for (x, y) in ((4, 2), (5, 2)):
            fmt.set(x, y, P(NOSE, 2))
        fj = fur('jaw', base=10.0, wet=0.6, puff=0.6)
        f = fj['front']                                          # 8 x 2: the mouth's inverted Y, split at one corner
        pt.pattern(f, ['..n..n..', '.n....n.'], {'n': (NOSE, 1)}, jitter=False)
        f.set(7, 0, C('#0a0807'))
        f.set(6, 1, P(STUFF, 2))
        fjt = fj['top']                                          # 8 x 3: inside the mouth - rice grains for teeth
        for x in range(fjt.w):
            fjt.set(x, 0, P(RICE, 5 if x % 2 else 4))
            fjt.set(x, 1, C('#140d0c'))
            fjt.set(x, 2, C('#080505'))
        for n in ('back', 'right', 'left'):
            fj[n].fill(C('#0b0807'))
        f = F('maw')['front']                                    # 8 x 2: upper rice teeth over the dark
        for x in range(f.w):
            f.set(x, 0, P(RICE, 5 if x % 2 == 0 else 3))
            f.set(x, 1, C('#060404'))
        # the buttons (one sewn on, one hanging by its thread - one texture): black, four holes, a wet glint
        fe = F('eye_r')
        for n, fc in fe.items():
            fc.fill(P(BUTTON, 2))
        f = fe['front']                                          # 5 x 5
        pt.pattern(f, ['.ccc.', 'chRhc', 'cRRRc', 'chRhc', '.ccc.'],
                   {'c': (BUTTON, 3), 'h': (BUTTON, 0), 'R': (BUTTON, 1), '.': 'CLEAR'}, jitter=False)
        f.set(1, 0, P(BUTTON, 5))
        f.set(0, 1, P(BUTTON, 4))
        pt.pattern(fe['back'], ['.ccc.', 'ccccc', 'ccccc', 'ccccc', '.ccc.'], {'c': (BUTTON, 1), '.': 'CLEAR'}, jitter=False)
        ee = EF('eye_r')['front']                                # the glints: a dim glossy core, a bright wet highlight
        for (x, y) in ((1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3), (3, 3)):
            ee.set(x, y, C('#26272b'))
        ee.set(1, 1, C('#9a9ca2'))
        ee.set(2, 1, C('#4a4c50'))
        ee.set(1, 2, C('#3c3e42'))
        for n, fc in F('eye_thread').items():
            for y in range(fc.h):
                fc.set(0, y, C('#1a1514') if y % 2 else C('#2c2422'))
        # ears: wet grey outside, faded pink inside (front), the left tip torn through
        ear_in = pal_arr(EAR_IN)
        for tag in ('ear_r', 'ear_r_tip', 'ear_l', 'ear_l_tip'):
            fs_ = fur(tag, base=8.8, puff=0.9)
            fc = fs_['front']
            Pp = fc.positions()
            for y in range(fc.h):
                for x in range(1, fc.w - 1):
                    if 'tip' in tag and y >= fc.h - 2 and x in (1, fc.w - 2):
                        continue
                    v = 3.2 + pt.n3(Pp[y, x][None] * 2.0)[0] * 1.1 - (0.9 if 'tip' in tag and y > fc.h - 4 else 0)
                    fc.set(x, y, ear_in[int(np.clip(round(v), 0, 5))])
        for n in ('front', 'back'):
            fc = F('ear_l_tip')[n]                               # 7 x 10: a torn hole near the tip
            for (x, y) in ((3, 5), (4, 5), (3, 6), (4, 6), (4, 7)):
                fc.clear(x, y)
            for (x, y) in ((2, 5), (5, 6), (3, 4), (3, 7), (5, 7)):
                fc.set(x, y, P(STUFF, 3))
        # ---------------------------------------------------------------- limbs
        for tag in ('arm_r', 'arm_l', 'leg_r', 'leg_l'):
            fur(tag, base=8.5)
        for tag in ('paw_r', 'paw_l'):
            fs_ = fur(tag, base=7.8, wet=1.25)
            fs_['bottom'].fill(P(FUR, 3))
        for n, fc in F('paw_r').items():                         # the red thread binding the knife into the paw
            if n in ('top', 'bottom'):
                continue
            for x in range(fc.w):
                fc.set(x, 1, P(RED, 4 - (x % 2)))
                fc.set(x, 3, P(RED, 3 + (x % 2)))
        pads = pal_arr(EAR_IN)
        for tag in ('foot_r', 'foot_l'):
            fs_ = fur(tag, base=8.2, puff=1.2)
            fc = fs_['bottom']                                   # 7 x 10: the paw pads on the sole
            for y in range(fc.h):
                for x in range(fc.w):
                    big = ((x - 3.0) / 2.3) ** 2 + ((y - 3.6) / 2.5) ** 2 < 1.0
                    toes = any(((x - tx) / 1.0) ** 2 + ((y - ty) / 1.0) ** 2 < 1.0 for (tx, ty) in ((1.1, 7.5), (3.0, 8.2), (4.9, 7.5)))
                    if big or toes:
                        fc.set(x, y, pads[int(np.clip(round(2.0 + rng.normal(0, 0.5)), 0, 5))])
                    else:
                        fc.set(x, y, P(FUR, 3.0 + rng.normal(0, 0.5)))
        # ---------------------------------------------------------------- the knife
        hp = pal_arr(HANDLE)
        for n, fc in F('handle').items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, hp[int(np.clip(round(2 + rng.normal(0, 0.4) + (0.8 if x == 0 else 0)), 0, 4))])
        for n in ('right', 'left'):
            fc = F('handle')[n]                                  # 3 x 7: three rivets, the bolster
            for y in (2, 4, 6):
                fc.set(1, y, P(STEEL, 5))
            for x in range(fc.w):
                fc.set(x, 0, P(STEEL, 3))
        st = pal_arr(STEEL)
        fbl = F('blade')
        for n, fc in fbl.items():
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.set(x, y, st[int(np.clip(round(4.2 + rng.normal(0, 0.35)), 0, 7))])
        for n in ('right', 'left'):
            fc = fbl[n]                                          # 4 x 15: spine to edge, tapered tip, water spots
            for y in range(fc.h):
                for x in range(fc.w):
                    xs = x if n == 'right' else fc.w - 1 - x       # 0 at the spine, 3 at the edge
                    v = 3.4 + xs * 0.8 - y * 0.04 + rng.normal(0, 0.3)
                    if xs == 3:
                        v = 6.7
                    if y >= fc.h - 4 and xs > 3 - (fc.h - 1 - y):
                        fc.clear(x, y)
                        continue
                    fc.set(x, y, st[int(np.clip(round(v), 0, 7))])
            for (x, y) in ((1, 4), (2, 9), (1, 11)):
                fc.dl(x, y, -0.12)
        fc = fbl['front']                                        # the edge, cut away at the tip
        for y in range(fc.h - 3, fc.h):
            fc.clear(0, y)
        for y in (1, 2, 3):
            fbl['bottom'].clear(0, y)
        for n in ('right', 'left'):
            ef = EF('blade')[n]
            for y in range(0, 12):
                ef.set(3 if n == 'right' else 0, y, C('#202427') if y % 3 else C('#33383c'))
        # ---------------------------------------------------------------- embers (BURN)
        for tag, seed in (('ember_a', 1), ('ember_b', 2)):
            fc = F(tag)['front']
            ec = EF(tag)['front']
            rr = np.random.default_rng(seed)
            for y in range(fc.h):
                for x in range(fc.w):
                    d = math.hypot(x + 0.5 - 4.0, y + 0.5 - 4.0) + rr.normal(0, 0.45)
                    if d < 2.6:
                        fc.set(x, y, P(CHAR, rr.integers(0, 3)))
                        if d > 1.7 or rr.random() < 0.18:
                            ec.set(x, y, C('#ff9a3c') if rr.random() < 0.6 else C('#c2410c'))
                    elif d < 3.6:
                        fc.set(x, y, P(CHAR, 3 + rr.integers(0, 2)))
                        if rr.random() < 0.3:
                            ec.set(x, y, C('#7a2306'))
        return pt.img, pt.eyes

    # ------------------------------------------------------------------------------------------------ animation
    prologue = '''
float clock = advance(mem, pose, pt);
// glitch above ~0.4 drops frames (stop-motion: the clock holds, then jumps a whole frame); any glitch snaps joints
float hold = Mth.clamp(glitch * 1.6F - 0.6F, 0.0F, 1.0F);
float t = hold > 0.0F ? frames(clock, hold) : clock;
pt = Math.max(0.0F, pt - (clock - t));
'''

    helpers = '''
/** Stop-motion: at g = 1 time advances only in whole 3-tick frames; below that every frame holds for part of its
 *  window, then catches up fast. */
private static float frames(float x, float g) {
    float i = (float) Mth.floor(x / 3.0F);
    float f = x / 3.0F - i;
    float hold = Mth.clamp(g * (1.0F + 0.45F * hash(i + 31.0F)), 0.0F, 1.0F);
    float s = hold >= 1.0F ? 0.0F : (f < hold ? 0.0F : (f - hold) / (1.0F - hold));
    return (i + s) * 3.0F;
}

/** A joint snapping the wrong way: -1..1 during random 3-tick windows (more of them at higher g), else 0. */
private static float jolt(float t, float seed, float g) {
    float i = (float) Mth.floor(t / 3.0F);
    if (hash(i * 1.37F + seed * 11.3F) >= g * 0.4F) {
        return 0.0F;
    }
    return hash(i + seed * 7.1F) * 2.0F - 1.0F;
}

/** A flick every `period` ticks: up in two ticks, back down over four (t must be >= 0). */
private static float flick(float t, float period, float phase) {
    float m = (t + phase) % period;
    if (m < 2.0F) {
        return m * 0.5F;
    }
    return m < 6.0F ? 1.0F - (m - 2.0F) / 4.0F : 0.0F;
}

/** One marionette step (s = 0..1): yanked up fast, hangs in the air, drops like a dead weight. */
private static float lift(float s) {
    if (s < 0.16F) {
        return easeOut(s / 0.16F);
    }
    if (s < 0.5F) {
        return 1.0F - (s - 0.16F) * 0.3F;
    }
    if (s < 0.64F) {
        return 0.898F * (1.0F - easeIn((s - 0.5F) / 0.14F));
    }
    return 0.0F;
}

/** Ringing after an impact x ticks ago (0 before): a decaying wobble starting at +1. */
private static float wobble(float x, float freq, float damp) {
    if (x <= 0.0F) {
        return 0.0F;
    }
    return (float) Math.exp(-damp * x) * Mth.cos(freq * x);
}

/** The crawling arm's sweep (u = 0..1 of its stroke): 0..0.5 pulls back along the floor, 0.5..1 swings forward. */
private static float sweep(float u) {
    if (u < 0.5F) {
        return Mth.lerp(ease(u / 0.5F), 2.75F, 0.55F);
    }
    return Mth.lerp(ease((u - 0.5F) / 0.5F), 0.55F, 2.75F);
}

/** The crawling arm's height (u = 0..1): down on the floor while pulling, lifted while swinging forward. */
private static float reach(float u) {
    if (u < 0.5F) {
        return -0.3F - 0.1F * Mth.sin(u * 6.2831855F);
    }
    return -0.6F + 1.35F * Mth.sin((u - 0.5F) * 6.2831855F);
}
'''

    anim = '''
// ---------------------------------------------------------------- defaults: upright, still
float earFlop = 0.78F;
float earGx = 0.0F;
float earGy = 1.0F;
float earGz = 0.0F;
float earSpread = 0.0F;
float armFlop = 0.0F;
float armW = 1.6F;
float armZ = 0.9F;
float legFlop = 0.0F;
float legW = 1.6F;
float legZ = 0.9F;
float legGz = 0.0F;
float jawOpen = 0.0F;
float knifeAim = 0.0F;
float kgx = 0.0F;
float kgy = 1.0F;
float kgz = 0.0F;
float drips = 1.0F;
float grains = 0.0F;
float burn = -1.0F;
this.knife.visible = true;
float j1 = jolt(t, 1.0F, glitch);
float j2 = jolt(t, 2.0F, glitch);
float j3 = jolt(t, 3.0F, glitch);
float j4 = jolt(t, 4.0F, glitch);
// the head yaw the game gives it (toward you, after you looked away and back): followed on the doll's own clock
float look = chase(mem, 42, Mth.clamp(Mth.wrapDegrees(netHeadYaw), -80.0F, 80.0F) * 0.017453292F, 0.7F, 0.8F, t);

if (pose == FLOAT) {
    // face down in the bath water, limp; the rice-heavy body hangs below the surface, ears spread on the water
    float bob = Mth.sin(t * 0.07F);
    float bob2 = Mth.sin(t * 0.043F + 1.3F);
    this.body.xRot = 1.5F + bob2 * 0.035F;
    this.body.y = 25.0F + bob * 0.3F;
    this.body.z = 2.4F;
    this.body.zRot = bob2 * 0.07F;
    this.body.yRot = Mth.sin(t * 0.0087F) * 0.35F;
    this.head.xRot = 0.22F;
    this.head.zRot = 0.12F;
    this.armR.zRot = 1.2F;
    this.armR.xRot = 0.25F + bob * 0.05F;
    this.armL.zRot = -1.25F;
    this.armL.xRot = 0.3F - bob * 0.05F;
    armFlop = 0.3F;
    armW = 0.25F;
    armZ = 0.6F;
    this.legR.zRot = 0.3F;
    this.legL.zRot = -0.25F;
    legFlop = 0.3F;
    legW = 0.25F;
    legZ = 0.6F;
    earFlop = 0.7F;
    earGy = 0.35F;
    earGz = -0.75F;
    earSpread = 0.85F;
    // something moves in the water: every ~23 s its left arm jerks and the head rolls a little
    float tw = flick(t, 460.0F, 230.0F);
    this.armL.zRot = this.armL.zRot - 0.6F * tw;
    this.pawL.xRot = -0.9F * tw;
    this.head.zRot = this.head.zRot + 0.18F * tw;
    this.knife.visible = false;
    drips = 0.0F;
    blend = 10.0F;
} else if (pose == SIT) {
    // slumped against a wall behind it, legs out, the head lolling slowly; the knife lies on the floor in its paw
    float loll = Mth.sin(t * 0.021F) * 0.13F + Mth.sin(t * 0.0077F + 2.0F) * 0.08F;
    this.body.y = 23.1F;
    this.body.xRot = -0.34F;
    this.torso.yScale = 0.95F;
    this.torso.xScale = 1.04F;
    this.head.y = -3.3F;
    this.legR.xRot = -1.23F;
    this.legR.yRot = 0.24F;
    this.legL.xRot = -1.23F;
    this.legL.yRot = -0.2F;
    this.footR.xRot = 0.25F;
    this.footL.xRot = 0.3F;
    // nodding off: the head sinks further and further for four seconds, then snaps back up
    float nod = (t + 37.0F) % 260.0F;
    float droop = nod < 200.0F ? span(nod, 120.0F, 198.0F) : 1.0F - span(nod, 200.0F, 202.5F);
    this.head.zRot = 0.6F + loll + 0.5F * droop + j1 * 0.4F;
    this.head.xRot = 0.48F + Mth.sin(t * 0.017F + 1.0F) * 0.05F + 0.22F * droop;
    this.head.x = 0.35F;
    this.head.yRot = look * 0.6F;
    this.armR.zRot = 0.55F;
    this.armR.xRot = -0.35F;
    this.armL.zRot = -0.45F;
    this.armL.xRot = -0.2F;
    armFlop = 0.35F;
    this.pawR.xRot = -0.4F;
    this.knife.xRot = -1.1F;
    this.knife.zRot = 0.35F;
    earFlop = 0.85F;
} else if (pose == CRAWL) {
    // dragging itself fast on its arms: the paws claw forward over the sides and haul it along the floor, the knife
    // stabbing into the boards; the body lurches with every pull, rolling; legs and ears trail behind
    float cyc = t / 16.0F;
    float u = cyc - (float) Mth.floor(cyc);
    float uR = u + 0.5F - (float) Mth.floor(u + 0.5F);
    float half = u * 2.0F - (float) Mth.floor(u * 2.0F);
    float surge = ease(ease(half)) - half;
    float pull = Mth.sin(half * 3.1415927F);
    this.body.xRot = 1.4F - 0.07F * pull;
    this.body.y = 22.36F;
    this.body.z = -surge * 4.4F;
    this.body.zRot = Mth.sin(u * 6.2831855F) * 0.2F + j4 * 0.15F;
    this.body.yRot = Mth.sin(u * 6.2831855F + 1.0F) * 0.12F;
    this.torso.yScale = 1.0F + 0.06F * pull;
    this.armL.xRot = reach(u);
    this.armL.zRot = -sweep(u);
    this.armR.xRot = reach(uR);
    this.armR.zRot = sweep(uR);
    // paws flat and clawing on the boards while pulling, curled under while swinging forward
    this.pawL.xRot = Mth.lerp(span(u, 0.42F, 0.62F) - span(u, 0.9F, 1.0F), -0.5F, 0.3F);
    this.pawR.xRot = Mth.lerp(span(uR, 0.42F, 0.62F) - span(uR, 0.9F, 1.0F), -0.5F, 0.3F);
    // the knife: raised point-down while the arm swings forward, stabbed into the boards at the plant, then
    // scraped along the floor, flat and pointing ahead, while the doll hauls itself up to it
    float stabbing = uR < 0.5F ? 1.0F - span(uR, 0.04F, 0.3F) : span(uR, 0.62F, 0.95F);
    knifeAim = 1.0F;
    kgx = -0.3F;
    kgy = Mth.lerp(stabbing, 0.06F, 0.6F);
    kgz = Mth.lerp(stabbing, -1.0F, -0.8F);
    // the head dragged low, chin on the boards, face forward and tipped over on one side
    this.head.y = -3.16F;
    this.head.z = -1.9F;
    this.head.xRot = -1.42F + 0.08F * pull + j1 * 0.25F;
    this.head.zRot = 0.45F + j2 * 0.5F;
    this.head.yRot = -Mth.sin(u * 6.2831855F) * 0.15F + look * 0.5F;
    this.legR.zRot = 0.22F;
    this.legL.zRot = -0.2F;
    legFlop = 0.7F;
    legW = 0.5F;
    legZ = 0.35F;
    legGz = 1.0F;
    earFlop = 0.88F;
    earGy = 1.0F;
    earGz = 0.75F;
    earSpread = 0.45F;
    jawOpen = 0.18F + 0.22F * Math.max(0.0F, Mth.sin(t * 0.8F));
    grains = 1.0F;
    blend = 4.0F;
} else if (pose == WALK) {
    // stiff marionette steps: hoisted by invisible strings, one leg yanked up at a time, dropped like a dead weight;
    // the opposite arm jerks up on its string; it sways and the head lolls late; the knife drags behind
    float cyc = t / 22.0F;
    float u = cyc - (float) Mth.floor(cyc);
    float right = u < 0.5F ? 1.0F : 0.0F;
    float s = u < 0.5F ? u * 2.0F : u * 2.0F - 1.0F;
    // every step a little different; now and then a string tugs the head up and hoists it higher
    float step = (float) Mth.floor(t / 11.0F);
    float tug = hash(step * 3.7F + 1.0F) < 0.34F ? 1.0F : 0.0F;
    float la = lift(s);
    float lf = la * (0.85F + 0.3F * hash(step * 1.3F + 7.0F)) * (1.0F + 0.35F * tug);
    float side = right * 2.0F - 1.0F;
    float land = wobble((s - 0.64F) * 11.0F, 1.3F, 0.55F);
    this.body.y = 21.0F - lf * 1.1F + land * 0.25F;
    this.body.zRot = side * (0.08F + 0.14F * lf) + j4 * 0.15F;
    this.body.xRot = 0.1F + 0.05F * lf;
    this.body.yRot = side * 0.1F * lf;
    this.torso.yScale = 1.0F - 0.1F * Math.max(0.0F, land);
    this.torso.xScale = 1.0F + 0.06F * Math.max(0.0F, land);
    this.legR.xRot = right > 0.5F ? -1.3F * lf : 0.16F;
    this.legL.xRot = right > 0.5F ? 0.16F : -1.3F * lf;
    this.legR.zRot = right > 0.5F ? 0.12F * lf : 0.0F;
    this.legL.zRot = right > 0.5F ? 0.0F : -0.12F * lf;
    this.footR.xRot = right > 0.5F ? 1.0F * lf : 0.0F;
    this.footL.xRot = right > 0.5F ? 0.0F : 1.0F * lf;
    // the strings on the wrists: the arm opposite the lifted leg is jerked up and dropped with it
    this.armL.zRot = -0.12F - (right > 0.5F ? 1.5F * la : 0.0F);
    this.armL.xRot = right > 0.5F ? -0.3F * la : 0.05F;
    this.armR.zRot = 0.1F + (right > 0.5F ? 0.0F : 1.25F * la);
    this.armR.xRot = 0.3F - (right > 0.5F ? 0.0F : 0.5F * la);
    this.knife.xRot = 0.8F;
    // the blade trails behind on the floor, except while its arm is jerked up on the string
    knifeAim = 1.0F - (right > 0.5F ? 0.0F : la);
    kgx = -0.12F;
    kgy = 0.82F;
    kgz = 0.56F;
    armFlop = 0.3F;
    armW = 0.7F;
    armZ = 0.35F;
    this.head.zRot = chase(mem, 40, -this.body.zRot * 1.4F + 0.3F, 0.45F, 0.22F, t) + j1 * 0.5F;
    this.head.xRot = 0.42F + chase(mem, 41, lf * 0.35F - tug * lf * 0.75F, 0.55F, 0.25F, t);
    this.head.yRot = look * 0.5F;
    earFlop = 0.82F;
    grains = 1.0F;
    blend = 5.0F;
} else if (pose == TURN) {
    // standing still with its back to you; the head turns round to face you in three overshooting jerks
    float turn = (spring(pt - 6.0F, 0.95F, 0.42F) + spring(pt - 13.0F, 0.95F, 0.42F) + spring(pt - 20.0F, 0.95F, 0.42F)) / 3.0F;
    this.body.xRot = 0.04F;
    this.head.yRot = 3.1415927F * turn;
    this.head.zRot = 0.32F * span(pt, 22.0F, 40.0F) + j1 * 0.35F;
    this.head.xRot = 0.1F;
    this.armR.xRot = -0.18F;
    this.armR.zRot = 0.06F;
    this.knife.xRot = -0.95F;
    armFlop = 0.2F;
    earFlop = 0.78F;
    blend = 3.0F;
} else if (pose == PEEK) {
    // behind the door frame, leaning only its head and its left ear out past the edge, in three little starts
    float out = jerky(pt / 30.0F, 3.0F);
    this.body.zRot = 0.52F * out;
    this.body.x = 0.4F * out;
    this.head.zRot = 0.78F * out + j1 * 0.25F;
    this.head.x = 0.4F * out;
    this.head.xRot = 0.1F;
    this.head.yRot = look * 0.4F;
    this.armR.xRot = -0.18F;
    this.knife.xRot = -0.95F;
    armFlop = 0.5F;
    earFlop = 0.88F;
    earGx = 0.9F * flick(t, 130.0F, 50.0F);
    blend = 4.0F;
} else if (pose == LUNGE) {
    // the jump scare: a crouch, then it springs at your face, knife raised over its head, the mouth seam tearing
    // open; the knife comes down at ticks 6-8 (the game carries the entity the rest of the way to the camera)
    float crouch = span(pt, 0.0F, 2.0F) * (1.0F - span(pt, 2.0F, 3.5F));
    float leap = easeOut(Mth.clamp((pt - 2.0F) / 4.0F, 0.0F, 1.0F));
    float arc = Mth.sin(Mth.clamp((pt - 2.0F) / 8.0F, 0.0F, 1.0F) * 3.1415927F);
    float raise = span(pt, 0.0F, 4.0F);
    float stab = span(pt, 6.0F, 8.0F);
    this.body.y = 21.0F + crouch * 1.4F - arc * 3.2F;
    this.body.z = -leap * 9.0F;
    this.body.xRot = -0.3F * crouch + 0.5F * leap - 0.25F * stab;
    this.torso.yScale = 1.0F - 0.16F * crouch + 0.12F * leap;
    this.torso.xScale = 1.0F + 0.08F * crouch - 0.04F * leap;
    this.legR.xRot = 0.8F * leap;
    this.legL.xRot = 0.7F * leap;
    this.legR.zRot = 0.35F * leap;
    this.legL.zRot = -0.35F * leap;
    this.footR.xRot = 0.6F * leap;
    this.footL.xRot = 0.6F * leap;
    this.armR.xRot = Mth.lerp(stab, -2.95F * raise, -0.85F);
    this.armR.zRot = 0.25F - 0.1F * stab;
    this.knife.xRot = -0.35F * raise;
    this.armL.xRot = -1.75F * leap;
    this.armL.zRot = -0.45F * leap;
    this.pawL.xRot = -0.5F * leap;
    this.head.xRot = -0.35F * leap + 0.15F * crouch;
    jawOpen = span(pt, 2.5F, 5.0F);
    earFlop = 0.6F;
    earGy = 0.7F;
    earGz = 1.0F * leap;
    earSpread = 0.35F;
    blend = 3.0F;
} else if (pose == LIMP) {
    // the salt water: a shrieking spasm, the knife flung out of its paw, then it falls on its back with a soft
    // bounce and lies limp, rice pouring from the wound
    float spasm = span(pt, 0.0F, 1.5F) * (1.0F - span(pt, 3.0F, 6.0F));
    float fall = easeIn(Mth.clamp((pt - 3.0F) / 6.0F, 0.0F, 1.0F));
    float bounce = wobble(pt - 9.0F, 1.3F, 0.45F);
    float shake = noise(t * 2.7F) * spasm;
    this.body.xRot = -0.45F * spasm - 1.48F * fall + 0.12F * bounce * fall;
    this.body.y = 21.0F + 1.62F * fall - 0.35F * bounce * fall;
    this.body.zRot = shake * 0.12F;
    this.head.xRot = -0.65F * spasm + 0.25F * fall;
    this.head.zRot = 0.75F * fall + shake * 0.3F;
    this.armR.zRot = 0.1F + 1.4F * spasm + 0.6F * fall;
    this.armL.zRot = -0.1F - 1.4F * spasm - 0.7F * fall;
    this.armR.xRot = -0.3F * spasm - 0.2F * fall;
    this.armL.xRot = -0.3F * spasm;
    this.legR.xRot = -0.35F * spasm - 0.25F * fall;
    this.legL.xRot = -0.35F * spasm - 0.15F * fall;
    this.legR.zRot = 0.35F * fall;
    this.legL.zRot = -0.3F * fall;
    this.knife.visible = pt < 3.5F;
    this.knife.xRot = -1.8F * span(pt, 0.0F, 3.5F);
    armFlop = 0.7F * fall;
    armW = 0.6F;
    armZ = 0.3F;
    legFlop = 0.75F * fall;
    legW = 0.6F;
    legZ = 0.3F;
    jawOpen = spasm + 0.3F * fall;
    earFlop = 0.6F + 0.3F * fall;
    grains = pt < 40.0F ? 1.0F : 0.0F;
    blend = 4.0F;
} else if (pose == BURN) {
    // on its back in the fire: thrashing, then curling up as it burns and shrinks, twitching less and less
    float thrash = 1.0F - span(pt, 12.0F, 100.0F);
    float curl = span(pt, 10.0F, 130.0F);
    float shrink = span(pt, 20.0F, 220.0F);
    float tw = twitch(t, 13.0F, 0.0F) + twitch(t, 17.0F, 5.0F) * 0.7F + twitch(t, 37.0F, 11.0F) * 0.5F;
    float n1 = noise(t * 0.32F) * thrash;
    float n2 = noise(t * 0.41F + 30.0F) * thrash;
    this.body.xRot = -1.45F + 0.25F * curl + n1 * 0.12F;
    this.body.y = 22.62F + 0.25F * shrink;
    this.body.zRot = n2 * 0.15F + tw * 0.05F;
    this.torso.xScale = 1.0F - 0.18F * shrink;
    this.torso.yScale = 1.0F - 0.22F * shrink;
    this.torso.zScale = 1.0F - 0.15F * shrink;
    this.head.xRot = 0.85F * curl + n2 * 0.3F + tw * 0.1F;
    this.head.zRot = 0.3F * (1.0F - curl) + n1 * 0.4F;
    this.armR.xRot = -1.35F * curl + n1 * 0.6F;
    this.armR.zRot = 0.9F - 0.6F * curl + n2 * 0.5F + tw * 0.3F;
    this.armL.xRot = -1.25F * curl + n2 * 0.6F;
    this.armL.zRot = -0.9F + 0.55F * curl - n1 * 0.5F - tw * 0.25F;
    this.pawR.xRot = -1.0F * curl;
    this.pawL.xRot = -1.0F * curl;
    this.legR.xRot = -0.3F - 1.25F * curl + n2 * 0.4F + tw * 0.2F;
    this.legL.xRot = -0.3F - 1.2F * curl + n1 * 0.4F;
    this.legR.zRot = 0.3F * (1.0F - curl);
    this.legL.zRot = -0.3F * (1.0F - curl);
    this.footR.xRot = 0.8F * curl;
    this.footL.xRot = 0.8F * curl;
    // the ears curl like burning paper
    this.earRTip.zRot = 0.25F + 1.9F * curl;
    this.earLTip.zRot = -0.25F - 1.9F * curl;
    earFlop = 0.6F * (1.0F - curl);
    jawOpen = (1.0F - span(pt, 30.0F, 70.0F)) * (0.75F + 0.25F * Mth.sin(t * 0.9F)) + 0.25F * curl;
    this.knife.visible = false;
    drips = 0.0F;
    burn = pt;
    blend = 3.0F;
} else {
    // STAND: upright and perfectly still, the head turned over on its neck to lie at 90 degrees
    this.body.xRot = 0.04F;
    this.head.zRot = 1.57F + j1 * 0.35F;
    this.head.x = -1.83F;
    this.head.y = -5.7F;
    this.head.xRot = 0.06F;
    this.armR.xRot = -0.12F;
    this.armR.zRot = 0.08F;
    this.knife.xRot = -1.06F;
    this.armL.zRot = -0.12F;
    // its head lies on its side, so it does not turn the head to you: the whole doll has turned a little
    this.body.yRot = look * 0.6F;
    armFlop = 0.15F;
    earFlop = 0.92F;
    // snaps upright out of a crawl or a walk (the game stops it so when you look), unwinds slowly out of TURN
    blend = prev == TURN ? 10.0F : 3.0F;
}
// glitch: joints snap the wrong way for a few ticks
this.armR.zRot = this.armR.zRot + j2 * 0.7F;
this.armL.zRot = this.armL.zRot - j3 * 0.7F;
this.head.xRot = this.head.xRot + j4 * 0.35F;
this.jaw.xRot = jawOpen * 1.25F;
this.maw.visible = jawOpen > 0.02F;
this.emberA.visible = burn > 6.0F;
this.emberB.visible = burn > 16.0F;
this.emberC.visible = burn > 28.0F;
this.emberD.visible = burn > 44.0F;
this.emberE.visible = burn > 60.0F;
this.emberF.visible = burn > 80.0F;
'''

    post = '''
// plush physics after the blend: ears, the hanging eye, the loose thread end and limp limbs swing on springs
flop(mem, 0, this.earR, earFlop, earGx - earSpread, earGy, earGz, 0.42F, 0.24F, t, this.root, this.body, this.head);
flop(mem, 3, this.earRTip, Math.min(1.0F, earFlop * 1.12F), earGx - earSpread * 1.2F, earGy, earGz, 0.58F, 0.2F, t, this.root, this.body, this.head, this.earR);
flop(mem, 6, this.earL, earFlop, earGx + earSpread, earGy, earGz, 0.42F, 0.24F, t, this.root, this.body, this.head);
flop(mem, 9, this.earLTip, Math.min(1.0F, earFlop * 1.12F), earGx + earSpread * 1.2F, earGy, earGz, 0.58F, 0.2F, t, this.root, this.body, this.head, this.earL);
flop(mem, 12, this.eyeThread, 0.97F, 0.0F, 1.0F, 0.0F, 0.55F, 0.16F, t, this.root, this.body, this.head);
flop(mem, 15, this.threadEnd, 0.94F, 0.0F, 1.0F, 0.0F, 0.5F, 0.14F, t, this.root, this.body, this.torso);
flop(mem, 18, this.armR, armFlop, 0.0F, 1.0F, 0.0F, armW, armZ, t, this.root, this.body);
flop(mem, 21, this.armL, armFlop, 0.0F, 1.0F, 0.0F, armW, armZ, t, this.root, this.body);
flop(mem, 24, this.legR, legFlop, 0.0F, 0.45F, legGz, legW, legZ, t, this.root, this.body);
flop(mem, 27, this.legL, legFlop, 0.0F, 0.45F, legGz, legW, legZ, t, this.root, this.body);
flop(mem, 33, this.knife, knifeAim, kgx, kgy, kgz, 1.3F, 0.8F, t, this.root, this.body, this.armR, this.pawR);
// water drips (real time: water keeps falling even while the doll holds still) and rice grains from the wound
level(this.dripA, this.root, this.body, this.torso);
level(this.dripB, this.root, this.body, this.armL, this.pawL);
level(this.dripC, this.root, this.body, this.head, this.earR, this.earRTip);
level(this.riceA, this.root, this.body, this.torso);
level(this.riceB, this.root, this.body, this.torso);
float da = (ageInTicks + 3.0F) % 19.0F;
float db = (ageInTicks + 11.0F) % 27.0F;
float dc = (ageInTicks + 7.0F) % 23.0F;
float ra = (t + 2.0F) % 13.0F;
float rb = (t + 8.0F) % 17.0F;
this.dripAFall.visible = drips > 0.5F && da < 9.0F;
this.dripBFall.visible = drips > 0.5F && db < 9.0F;
this.dripCFall.visible = drips > 0.5F && dc < 9.0F;
this.dripAFall.y = da * da * 0.1F;
this.dripBFall.y = db * db * 0.1F;
this.dripCFall.y = dc * dc * 0.1F;
this.riceAFall.visible = grains > 0.5F && ra < 7.0F;
this.riceBFall.visible = grains > 0.5F && rb < 7.0F;
this.riceAFall.y = ra * ra * 0.16F;
this.riceBFall.y = rb * rb * 0.16F;
this.riceAFall.zRot = ra * 1.3F;
this.riceBFall.xRot = rb * 1.1F;
'''


SPEC = OniDoll

import ent_scene as SC  # noqa: E402
POSE_SCENES = {0: SC.bathtub(), 1: SC.wall_behind(), 2: SC.flooring(), 3: SC.flooring(), 4: SC.tatami(), 5: SC.tatami(),
               6: SC.door_frame(), 7: SC.closet(), 8: SC.flooring(), 9: SC.drained_tub()}
POSE_TIMES = {0: 140.0, 1: 140.0, 2: 136.0, 3: 136.0, 4: 140.0, 5: 60.0, 6: 60.0, 7: 7.0, 8: 60.0, 9: 90.0}
VIEW_ORTHO = 0.46
VIEW_CENTER = 0.3
TURN_SIZE = (150, 170)
NIGHT_EYE = ((0.7, 1.05, 2.3), (0.0, 0.28, 0.0))
IR_EYE = ((1.3, 1.85, 1.9), (0.0, 0.2, 0.0))


# ================================================================================================ previews
def previews(b, ER, rows_sel=None):
    import ent_scene as SC
    rows = []
    sel = (lambda k: rows_sel is None or k in rows_sel)
    if sel('situ'):
        ims = []
        ims.append(('CAM1 浴室: in the bath', b.view([(0, 0)], 420, size=(250, 200), eye=(1.2, 1.55, 1.6), target=(0.0, 0.0, 0.1),
                                                   look='ir', scene=SC.bathtub(), fov=62, label='CAM1  浴室',
                                                   stamp='2026/11/03  03:17:42')))
        for k, tt in enumerate((331, 334, 337)):
            ims.append((f'CAM3 廊下: WALK, frame {k + 1}', b.view([(0, 3)], tt, size=(200, 200), eye=(0.9, 1.7, 2.2),
                                                              target=(0.0, 0.25, -0.2 * k), look='ir', scene=SC.flooring(),
                                                              fov=60, glitch=lambda t_: 1.0, pos=(0.0, 0.0, -0.17 * k),
                                                              label='CAM3  廊下', stamp=f'2026/11/03  03:52:{11 + k:02d}')))
        ims.append(('through the fusuma slit', b.view(4, 140, size=(250, 200), eye=(0.15, 0.62, 2.4), target=(0.0, 0.32, 0.0),
                                                     look='night', scene=SC.tatami(), fov=50, tv=(-1.8, 0.5, 0.6),
                                                     pose_fn=None)))
        ims.append(('CAM2 和室: waiting, facing the wall', b.view(1, 140, size=(250, 200), eye=(-1.0, 1.7, -1.6), target=(0.0, 0.25, 0.0),
                                                                look='ir', scene=SC.wall_behind(dist=-0.45), fov=60,
                                                                entity_yaw=180.0, label='CAM2  和室', stamp='2026/11/03  04:21:05')))
        ims.append(('CAM4 玄関: coming for the lens', b.view([(0, 2)], 326, size=(250, 200), eye=(0.0, 0.5, 1.4), target=(0.0, 0.2, 0.0),
                                                           look='ir', scene=SC.flooring(), fov=64, label='CAM4  玄関',
                                                           stamp='2026/11/03  04:58:40')))
        rows.append(('鬼 in situ: on the camera feeds (IR, stop-motion), and through the fusuma gap by the TV light', ims))
    if sel('hero'):
        ims = []
        ims.append(('STAND 3/4', b.view(4, 140, yaw_view=-30, pitch_view=8, size=(230, 260), ortho=0.36, center_y=0.3, scene=SC.tatami())))
        ims.append(('STAND front', b.view(4, 140, yaw_view=0, pitch_view=4, size=(230, 260), ortho=0.36, center_y=0.3, scene=SC.tatami())))
        ims.append(('face (TURN done, from behind)', b.view([(0, 4), (100, 5)], 160, yaw_view=180, pitch_view=4, size=(230, 260),
                                                          ortho=0.17, center_y=0.5, scene=SC.tatami())))
        ims.append(('face 3/4', b.view([(0, 4), (100, 5)], 160, yaw_view=215, pitch_view=8, size=(230, 260), ortho=0.17,
                                       center_y=0.5, scene=SC.tatami())))
        ims.append(('back', b.view(4, 140, yaw_view=160, pitch_view=10, size=(230, 260), ortho=0.36, center_y=0.3, scene=SC.tatami())))
        ims.append(('in the TV light', b.view(4, 140, size=(260, 260), eye=(0.5, 0.9, 1.6), target=(0.0, 0.3, 0.0), look='night',
                                              scene=SC.tatami(), fov=55)))
        ims.append(('IR feed', b.view(4, 140, size=(260, 260), eye=(0.9, 1.5, 1.4), target=(0.0, 0.25, 0.0), look='ir',
                                      scene=SC.tatami(), fov=60, label='CAM2  和室')))
        rows.append(('鬼（人形）: STAND (4) - upright, perfectly still, the head turned over on its neck at 90 degrees', ims))
    if sel('float'):
        ims = []
        for tt in (0, 20, 40, 60, 80):
            ims.append((f't {tt}', b.view([(0, 0)], 400 + tt, yaw_view=-30, pitch_view=55, size=(190, 190), ortho=0.45, center_y=0.0,
                                         scene=SC.bathtub())))
        ims.append(('over the rim', b.view([(0, 0)], 420, size=(230, 190), eye=(0.9, 0.75, 1.3), target=(0.0, 0.0, 0.0),
                                           scene=SC.bathtub(), fov=55)))
        ims.append(('IR: CAM1 浴室', b.view([(0, 0)], 420, size=(230, 190), eye=(1.1, 1.6, 1.4), target=(0.0, 0.0, 0.0),
                                            look='ir', scene=SC.bathtub(), fov=60)))
        rows.append(('FLOAT (0): face down in the bath, limp, bobbing (knife hidden; entity at the water surface)', ims))
    if sel('crawl'):
        ims = []
        for k in range(8):
            ims.append((f'side t {k * 2}', b.view([(0, 2)], 320 + k * 2, yaw_view=-90, pitch_view=12, size=(170, 150), ortho=0.42,
                                                 center_y=0.18, scene=SC.flooring())))
        ims.append(('front', b.view([(0, 2)], 323, yaw_view=-10, pitch_view=14, size=(170, 150), ortho=0.42, center_y=0.18,
                                    scene=SC.flooring())))
        rows.append(('CRAWL (2): one 16-tick cycle (two pulls), every 2 ticks', ims))
    if sel('walk'):
        ims = []
        for k in range(8):
            ims.append((f'side t {k * 3}', b.view([(0, 3)], 330 + k * 3, yaw_view=-90, pitch_view=8, size=(150, 170), ortho=0.42,
                                                 center_y=0.3, scene=SC.flooring())))
        ims.append(('front t 3', b.view([(0, 3)], 333, yaw_view=-15, pitch_view=8, size=(150, 170), ortho=0.42, center_y=0.3,
                                        scene=SC.flooring())))
        rows.append(('WALK (3): one 22-tick cycle (two marionette steps), every 3 ticks', ims))
    if sel('stopmo'):
        ims = []
        for k in range(10):
            ims.append((f'tick {k}', b.view([(0, 3)], 330 + k, yaw_view=-60, pitch_view=10, size=(130, 160), ortho=0.42, center_y=0.3,
                                            glitch=lambda tt: 1.0, scene=SC.flooring())))
        rows.append(('stop-motion (glitch 1.0, WALK): the pose changes only in whole 3-tick frames', ims))
    if sel('events'):
        ims = []
        for tt in (226, 231, 233, 238):
            ims.append((f'FLOAT t {tt}', b.view([(0, 0)], float(tt), yaw_view=-30, pitch_view=55, size=(160, 150), ortho=0.45,
                                                center_y=0.0, scene=SC.bathtub())))
        for tt in (100, 160, 163, 166):
            ims.append((f'SIT t {tt}', b.view([(0, 1)], float(tt), yaw_view=-15, pitch_view=8, size=(150, 150), ortho=0.36,
                                              center_y=0.25, scene=SC.wall_behind())))
        rows.append(('rare events on the running clock: FLOAT - an arm jerks in the water (t 230); SIT - nodding off, '
                     'snapping awake (t 163)', ims))
        ims = []
        for k in range(10):
            tt = 22.0 * 2 + k * 3.0
            ims.append((f'WALK t {int(tt)}', b.view([(0, 3)], tt, yaw_view=-90, pitch_view=6, size=(130, 160), ortho=0.42,
                                                   center_y=0.32, scene=SC.flooring())))
        rows.append(('WALK over four steps: every step a little different, a string tugging the head up now and then', ims))
    if sel('turn'):
        ims = []
        for ptv in (0, 4, 7, 10, 14, 17, 21, 26, 45):
            ims.append((f'pt {ptv}', b.view([(0, 4), (100, 5)], 100 + ptv, yaw_view=180, pitch_view=8, size=(140, 170), ortho=0.36,
                                            center_y=0.32, scene=SC.tatami())))
        rows.append(('TURN (5): from STAND, the head turns round to you in three jerks (seen from behind the body)', ims))
    if sel('peek'):
        ims = []
        for ptv in (0, 8, 14, 20, 30, 60):
            ims.append((f'pt {ptv}', b.view([(0, 4), (100, 6)], 100 + ptv, yaw_view=8, pitch_view=6, size=(170, 170), ortho=0.42,
                                            center_y=0.3, scene=SC.door_frame())))
        ims.append(('night', b.view([(0, 4), (100, 6)], 160, size=(200, 170), eye=(0.5, 1.0, 1.9), target=(0.1, 0.3, 0.0), look='night',
                                    scene=SC.door_frame(), fov=55, tv=(2.4, 0.6, 1.6))))
        ims.append(('IR', b.view([(0, 4), (100, 6)], 160, size=(200, 170), eye=(0.6, 1.6, 1.8), target=(0.1, 0.3, 0.0), look='ir',
                                 scene=SC.door_frame(), fov=60, label='CAM3  廊下')))
        rows.append(('PEEK (6): only the head and the left ear lean out past the door frame', ims))
    if sel('lunge'):
        ims = []
        for ptv in (0, 1, 2, 3, 4, 5, 6, 7, 8):
            zz = -0.75 * max(0.0, min(1.0, (ptv - 2) / 6.0))
            ims.append((f'pt {ptv}', b.view([(0, 4), (100, 7)], 100 + ptv, size=(150, 150), eye=(0.12, 0.55, -1.45),
                                            target=(0.0, 0.4, -0.5), scene=SC.closet(), fov=75, entity_yaw=180.0,
                                            pos=(0.0, 0.0, zz))))
        rows.append(('LUNGE (7): from the closet floor, the game carrying it 0.75 blocks closer from tick 2: crouch 0-2, spring, knife down at 6-8', ims))
        ims = []
        for ptv in (0, 2, 4, 6, 8, 10):
            ims.append((f'side pt {ptv}', b.view([(0, 4), (100, 7)], 100 + ptv, yaw_view=-90, pitch_view=4, size=(170, 170), ortho=0.55,
                                                 center_y=0.45, pos=(0.0, 0.0, 0.0), scene=SC.closet())))
        rows.append(('LUNGE (7) from the side', ims))
    if sel('limp'):
        ims = []
        for ptv in (0, 1, 2, 4, 6, 8, 10, 13, 40):
            ims.append((f'pt {ptv}', b.view([(0, 4), (100, 8)], 100 + ptv, yaw_view=-70, pitch_view=14, size=(150, 150), ortho=0.42,
                                            center_y=0.25, scene=SC.flooring())))
        rows.append(('LIMP (8): spasm, the knife flung out at 3, falls on its back by 9, a soft bounce, rice pouring', ims))
    if sel('burn'):
        ims = []
        for ptv in (0, 10, 25, 45, 70, 110, 160, 230):
            ims.append((f'pt {ptv}', b.view([(0, 8), (100, 9)], 100 + ptv, yaw_view=-30, pitch_view=50, size=(160, 160), ortho=0.42,
                                            center_y=0.1, scene=SC.drained_tub())))
        ims.append(('night pt 60', b.view([(0, 8), (100, 9)], 160, size=(200, 160), eye=(0.7, 1.2, 1.1), target=(0.0, 0.05, 0.0),
                                          look='night', scene=SC.drained_tub(), fov=55)))
        rows.append(('BURN (9): thrashing, curling up and shrinking as the burnt patches spread', ims))
    return rows
