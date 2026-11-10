"""TREANT boss rig: b2 古代樹の守護者. A gnarled trunk with a carved glowing face and a hinged bark jaw, a great leaf
canopy whose clumps lag and rustle, branch arms ending in twig claws with vines hanging from them, moss strands from
the canopy, and root legs of three segments (root, stump, toes) planted by IK. Slow, creaking motion: the trunk
sways before the limbs follow, the canopy and the vines keep moving after every stop."""
import math

import eranim
from boss_forms import cracks, glow_faces, veins
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_root', 'right_shin', 'right_foot'), ('left_root', 'left_shin', 'left_foot'))
CLAW_R = (-11.0, 15.0, -2.0)       # tips of the right claws (rest coordinates of right_claws)
CLAW_L = (11.0, 15.0, -2.0)
MOUTH = (0.0, -9.0, -10.0)


def arm(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    x0 = -13 if s < 0 else 8
    t = (lambda tag: dict(tag=tag)) if s < 0 else (lambda tag: dict(share=tag))
    claws = P('%s_claws' % name, (10.5 * s, 7, 0),
              C(x0, 7, -3, 1, 7, 1, 'bark:twig', **t('tw1')), C(x0 + 2, 7, -1, 1, 8, 1, 'bark:twig', **t('tw2')),
              C(x0 + 4, 7, -3, 1, 6, 1, 'bark:twig', **t('tw3')))
    vx = x0 + 0.5 if s < 0 else x0 + 3.5
    vine = P('vine_%s' % side, (vx + 0.5, 2, 2.5), C(vx, 2, 2, 1, 7, 1, 'leaf:vine', **t('vine')),
             P('vine_%s2' % side, (vx + 0.5, 9, 2.5), C(vx, 9, 2, 1, 7, 1, 'leaf:vine2', **t('vine2'))))
    fore = P('%s_forearm' % name, (10.5 * s, -5, 0),
             C(x0, -5, -3, 5, 12, 5, 'bark:arm2', **(dict(tag='fa') if s < 0 else dict(share='fa', mirror=True))),
             claws, vine, rot=(-8, 0, 0))
    tx = x0 - 2 if s < 0 else x0 + 5
    thorns = P('thorns_%s' % side, (tx, -14, 0),
               C(tx, -17, -1, 2, 2, 2, 'crystal:thorn', **t('th1')), C(tx, -12, 1, 2, 2, 2, 'crystal:thorn', **t('th2')),
               C(tx, -8, -2, 2, 2, 2, 'crystal:thorn', **t('th3')), opt=True)
    return P('%s_arm' % name, (9 * s, -17, 0),
             C(x0, -19, -3, 5, 14, 6, 'bark:arm', **(dict(tag='ua') if s < 0 else dict(share='ua', mirror=True))),
             C(x0 - 1, -20, -4, 7, 4, 8, 'leaf:tuft', **(dict(tag='tuft') if s < 0 else dict(share='tuft', mirror=True))),
             fore, thorns, rot=(-3, 0, 12 * -s))


def root_leg(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    x = 4 * s
    tag = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    foot = P('%s_foot' % name, (x, 21, 0), C(x - 4.5, 21, -6, 9, 3, 11, 'bark:foot', **tag('foot')),
             C(x - 5 if s < 0 else x + 2, 21, -9, 3, 3, 4, 'bark:toe', **tag('toe1')),
             C(x - 1, 21, -9.5, 3, 3, 4, 'bark:toe', **tag('toe2')),
             C(x - 6 if s < 0 else x + 4, 21, 3, 2, 3, 3, 'bark:toe', **tag('toe3')))
    shin = P('%s_shin' % name, (x, 14, 0), C(x - 3.5, 14, -4, 7, 7, 8, 'bark:shin', **tag('shin')), foot)
    return P('%s_root' % name, (x, 6, 0), C(x - 3, 5, -3.5, 6, 9, 7, 'bark:root', **tag('root')), shin)


def geometry():
    canopy = P('canopy', (0, -26, 0), C(-12, -38, -11, 24, 12, 22, 'leaf:canopy'), C(-8, -44, -7, 16, 6, 14, 'leaf:canopy_top'),
               P('canopy_r', (-12, -32, 0), C(-18, -37, -6, 7, 9, 11, 'leaf:clump', tag='clump')),
               P('canopy_l', (12, -32, 0), C(11, -37, -6, 7, 9, 11, 'leaf:clump', share='clump', mirror=True)),
               P('canopy_back', (0, -30, 11), C(-8, -36, 10, 16, 10, 6, 'leaf:clump2')),
               P('moss_r', (-10, -27, -10), C(-10.5, -27, -10.5, 1, 8, 1, 'leaf:vine', tag='moss1'),
                 P('moss_r2', (-10, -19, -10), C(-10.5, -19, -10.5, 1, 6, 1, 'leaf:vine2', tag='moss2'))),
               P('moss_l', (9, -27, -10), C(8.5, -27, -10.5, 1, 8, 1, 'leaf:vine', share='moss1'),
                 P('moss_l2', (9, -19, -10), C(8.5, -19, -10.5, 1, 6, 1, 'leaf:vine2', share='moss2'))))
    head = P('head', (0, -20, 0), C(-6, -26, -6, 12, 6, 12, 'bark:crown'), canopy,
             P('antler_r', (-5, -26, 0), C(-6, -36, -1, 2, 10, 2, 'bark:branch', tag='ant'), rot=(0, 0, -25)),
             P('antler_l', (5, -26, 0), C(4, -36, -1, 2, 10, 2, 'bark:branch', share='ant', mirror=True), rot=(0, 0, 25)))
    crystal = P('crystal', (4.5, -7, -7), C(3, -12, -10, 3, 7, 3, 'crystal:crystal'),
                P('crystal_b', (6.5, -8, -7), C(5.5, -11, -9.5, 2, 4, 2, 'crystal:crystal2', tag='cs'), rot=(0, 0, -30)),
                P('crystal_c', (2.5, -6, -7), C(1.5, -9, -9, 2, 4, 2, 'crystal:crystal2', share='cs'), rot=(0, 0, 35)),
                rot=(-15, 0, 0), opt=True)
    upper = P('trunk_upper', (0, -4, 0), C(-8, -20, -7, 16, 16, 14, 'bark:trunk2'), head, arm('r'), arm('l'), crystal,
              P('jaw', (0, -9, -7.5), C(-4, -9, -8, 8, 3, 1, 'bark:jaw')),
              P('moss_beard', (0, -6, -7), C(-5, -6, -7.6, 10, 8, 0, 'leaf:moss')),
              P('mushrooms', (7, -10, 0), C(7.5, -12, -3, 3, 2, 3, 'skin:cap', tag='cap'), C(7.5, -7, 1, 2, 2, 2, 'skin:cap2'),
                C(-10.5, -14, -1, 3, 2, 3, 'skin:cap', share='cap')))
    trunk = P('trunk', (0, 6, 0), C(-7, -4, -6, 14, 10, 12, 'bark:trunk'), upper, root_leg('r'), root_leg('l'))
    return [P('base', (0, 24, 0), trunk)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['canopy'], rest='up', axes='xz', hang=0.0, freq=1.05, damp=0.42, inertia=5.0,
          limits={'x': (-14.0, 14.0), 'z': (-14.0, 14.0)}),
    Chain(['canopy_r'], rest='up', axes='xz', hang=0.0, freq=1.7, damp=0.3, inertia=6.0, limits={'z': (-25.0, 25.0)}),
    Chain(['canopy_l'], rest='up', axes='xz', hang=0.0, freq=1.8, damp=0.3, inertia=6.0, limits={'z': (-25.0, 25.0)}),
    Chain(['canopy_back'], rest='up', axes='x', hang=0.0, freq=1.5, damp=0.32, inertia=6.0, limits={'x': (-20.0, 20.0)}),
    Chain(['vine_r', 'vine_r2'], rest='down', axes='xz', hang=0.92, freq=1.5, damp=0.28, inertia=8.0, falloff=1.2),
    Chain(['vine_l', 'vine_l2'], rest='down', axes='xz', hang=0.92, freq=1.55, damp=0.28, inertia=8.0, falloff=1.2),
    Chain(['moss_r', 'moss_r2'], rest='down', axes='xz', hang=0.9, freq=1.4, damp=0.3, inertia=8.0, falloff=1.2),
    Chain(['moss_l', 'moss_l2'], rest='down', axes='xz', hang=0.9, freq=1.45, damp=0.3, inertia=8.0, falloff=1.2),
    Chain(['moss_beard'], rest='down', axes='xz', hang=0.75, freq=1.9, damp=0.35, inertia=6.0,
          limits={'x': (-45.0, 10.0)}),
    Chain(['antler_r'], rest='up', axes='xz', hang=0.0, freq=2.6, damp=0.3, inertia=5.0, limits={'z': (-15.0, 15.0)}),
    Chain(['antler_l'], rest='up', axes='xz', hang=0.0, freq=2.7, damp=0.3, inertia=5.0, limits={'z': (-15.0, 15.0)}),
]
GAIT = Gait(2.4, stride=14.0, duty=0.68, lift=3.6, heel=6.0, toe=10.0)


def rustle(seed):
    return shake(1.3, 1.0, seed)


def idle(x):
    T = 4.6
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.4)
    c.fn('base', 'pos', lambda t: (0.6 * sway(t), 0.35 * b(t) - 0.15, 0.0), 18)
    c.fn('trunk', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T + 1.1), 0.8 * math.sin(2 * math.pi * t / T),
                                    1.2 * sway(t)), 18)
    c.fn('trunk_upper', 'rot', lambda t: (-1.8 * b(t - 0.3), -1.0 * math.sin(2 * math.pi * t / T + 0.5),
                                          -1.0 * sway(t - 0.3)), 18)
    c.fn('trunk_upper', 'scale', lambda t: (1.0 + 0.018 * b(t), 1.0 + 0.01 * b(t), 1.0 + 0.022 * b(t)), 18)
    look = [(0.0, 0.0, 's'), (0.9, 0.0, 's'), (1.6, 9.0, 's'), (2.5, 9.0, 's'), (3.3, -7.0, 's'), (4.0, -7.0, 's'),
            (4.6, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (1.0 * math.sin(2 * math.pi * t / T), eranim.eased_value(look, t), 0.0), 36)
    r1, r2, r3 = rustle(1), rustle(2), rustle(3)
    c.fn('canopy', 'rot', lambda t: (1.2 * r1(t), 0.0, 1.5 * r2(t)), 23)
    c.fn('canopy_r', 'rot', lambda t: (0.0, 0.0, 3.0 * r3(t)), 23)
    c.fn('canopy_l', 'rot', lambda t: (0.0, 0.0, -3.0 * r1(t + 0.7)), 23)
    c.fn('right_arm', 'rot', lambda t: (1.0 * math.sin(2 * math.pi * t / T + 0.3), 0.0, 2.0 * b(t - 0.2)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.0 * math.sin(2 * math.pi * t / T + 0.3), 0.0, -2.0 * b(t - 0.25)), 12)
    flex = [(0.0, 0.0, 's'), (1.8, 0.0, 's'), (2.2, -18.0, 'O'), (2.6, 6.0, 's'), (3.0, 0.0, 's'), (T, 0.0, 's')]
    c.fn('right_claws', 'rot', lambda t: (eranim.eased_value(flex, t), 0.0, 0.0), 30)
    c.fn('left_claws', 'rot', lambda t: (eranim.eased_value(flex, t - 0.3), 0.0, 0.0), 30)
    creak = [(0.0, 0.0, 's'), (3.2, 0.0, 's'), (3.45, -8.0, 'o'), (3.8, 0.0, 's'), (T, 0.0, 's')]
    c.fn('jaw', 'rot', lambda t: (eranim.eased_value(creak, t), 0.0, 0.0), 30)
    feet(c, x, samples=18)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.3 * w(t, 1, 0.25), -0.5 - 1.0 * w(t, 2, 0.08), 0.0), 24)
    c.fn('trunk', 'rot', lambda t: (1.2 * w(t, 2, 0.1), -5.0 * w(t), 4.5 * s1(t)), 24)
    c.fn('trunk_upper', 'rot', lambda t: (6.0 + 1.5 * w(t, 2, 0.15), 4.0 * w(t, 1, 0.08), -3.0 * s1(t - 0.08)), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.0 * w(t, 2, 0.2), -2.5 * w(t, 1, 0.1), 1.5 * s1(t - 0.12)), 24)
    c.fn('right_arm', 'rot', lambda t: (14.0 * w(t, 1, 0.1), 0.0, 2.0 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-14.0 * w(t, 1, 0.1), 0.0, -2.0 * w(t, 2, 0.1)), 24)
    c.fn('right_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, -w(t, 1, 0.15)), 0.0, 0.0), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, w(t, 1, 0.15)), 0.0, 0.0), 24)
    c.fn('right_claws', 'rot', lambda t: (-6.0 * w(t, 1, 0.25), 0.0, 0.0), 24)
    c.fn('left_claws', 'rot', lambda t: (6.0 * w(t, 1, 0.25), 0.0, 0.0), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Both branch arms rise high above the canopy, the trunk bends back creaking, then the claws hammer the floor."""
    A, B, Cc, I, F, R, E = 0.15, 0.95, 1.12, 1.3, 1.5, 1.95, 2.6
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0), (B, 0, 1.2, 1.5), (Cc, 0, 1.4, 1.7, 'o'), (I, 0, -5.4, -2.6, 'I'),
                   (F, 0, -5.8, -2.8, 'O'), (R, 0, -3.6, -1.8), (E, 0, 0, 0)])
    c.rot('trunk', [(0, 0, 0, 0), (B, -6, 0, 0), (I, 14, 0, 0, 'I'), (F, 16, 0, 0, 'O'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -24, 0, 0), (Cc, -26, 0, 0, 'o'), (I, 30, 0, 0, 'I'),
                          (F, 34, 0, 0, 'O'), (R, 20, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -8, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -26, 0, 0, 'O'), (R, -8, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 12, 0, 4 * sz), (B, -168, 0, -12 * sz), (Cc, -174, 0, -14 * sz, 'o'),
                                (I, -46, 0, -6 * sz, 'I'), (F, -38, 0, -6 * sz, 'O'), (R, -36, 0, -3 * sz), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -30, 0, 0), (I, 8, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_claws' % side, [(0, 0, 0, 0), (B, -35, 0, 0), (I, 15, 0, 0, 'I'), (F, 8, 0, 0, 'b'), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.18, 0.55, (6.0, 0.8), 2.5), (2.02, 2.42, (0.0, 0.0), 2.0)])
    for side, part, point in (('right', 'right_claws', CLAW_R), ('left', 'left_claws', CLAW_L)):    # they stay where they landed
        a = eranim.reach_y(c, x.rig, x.v, I, side + '_arm', 'x', part, point, 24.0, -100.0, -5.0)
        eranim.reach_y(c, x.rig, x.v, F, side + '_arm', 'x', part, point, 24.0, a - 14.0, a + 14.0)
    return eranim.retime(c, 1.2)


def melee_sweep(x):
    A, B, I, F, R, E = 0.12, 0.8, 1.05, 1.32, 1.65, 2.3
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0.3, -0.5, 0), (B, -1.5, -1.7, 0.9), (I, 1.0, -2.1, -1.8, 'I'),
                   (F, 1.7, -1.9, -2.2, 'O'), (R, 1.0, -1.2, -1.4), (E, 0, 0, 0)])
    c.rot('trunk', [(0, 0, 0, 0), (B, 0, 18, -3), (I, 2, -8, 3, 'I'), (F, 2, -22, 3, 'b'), (R, 1, -12, 1), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (A, 0, -5, 0), (B, 6, 38, -6), (I, 10, -18, 5, 'I'), (F, 8, -44, 5, 'b'),
                          (R, 4, -28, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -30, 0), (I, 0, 10, 0, 'I'), (F, -2, 26, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 6, 0, -3), (B, -70, 78, 0), (I, -86, -8, 0, 'I'), (F, -78, -64, 0, 'O'),
                        (R, -40, -30, 0), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -2, 0, 0, 'I'), (F, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_claws', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 10, 0, 0, 'I'), (F, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -50, 0, -22), (I, -22, 0, -46, 'I'), (F, 8, 0, -58, 'O'), (R, 0, 0, -26),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.84, 1.03, (-6.0, 2.0), 3.0), (1.68, 2.08, (0.0, 0.0), 2.0)],
         right_plan=[(0.15, 0.5, (3.0, 1.0), 1.5), (1.7, 2.1, (0.0, 0.0), 1.5)])
    return eranim.retime(c, 0.9)


def cast(x):
    """Arms spread wide while the canopy swells and the jaw opens on the glow inside, then a forward release."""
    A, B, I, F, R, E = 0.15, 0.85, 1.15, 1.45, 1.8, 2.4
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0), (B, 0, 0.9, 1.1), (I, 0, -1.4, -1.8, 'I'), (F, 0, -1.6, -2.0, 'O'),
                   (R, 0, -1.0, -1.2), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (B, -18, 0, 0), (I, 14, 0, 0, 'I'), (F, 16, 0, 0, 'b'), (R, 6, 0, 0),
                          (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -20, 0, 0), (I, -2, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -24, 0, 0), (I, -34, 0, 0, 'O'), (R, -10, 0, 0), (E, 0, 0, 0)])
    c.scl('canopy', [(0, 1, 1, 1), (B, 1.14, 1.14, 1.14), (I, 0.96, 0.96, 0.96, 'I'), (F, 1.04, 1.04, 1.04, 'b'),
                     (E, 1, 1, 1)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 8, 0, 0), (B, -130, 0, 40 * sz), (I, -80, 0, -4 * sz, 'I'),
                                (F, -88, 0, -4 * sz, 'b'), (R, -56, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -24, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
        c.rot('%s_claws' % side, [(0, 0, 0, 0), (B, -40, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.9, 1.12, (-4.0, 0.5), 2.5), (1.8, 2.2, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'trunk_upper', MOUTH
    return eranim.retime(c, 0.8)


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.2 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('trunk_upper', 'rot', lambda t: (-14.0 + 0.8 * tr(t), 0.0, 1.5 * tr2(t)), 28)
    c.fn('head', 'rot', lambda t: (-16.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 28)
    c.fn('jaw', 'rot', lambda t: (-32.0 + 3.0 * tr(t), 0.0, 0.0), 28)
    c.fn('canopy', 'rot', lambda t: (2.0 * tr(t), 0.0, 3.5 * tr2(t)), 28)
    c.fn('canopy_r', 'rot', lambda t: (0.0, 0.0, 5.0 * tr2(t)), 28)
    c.fn('canopy_l', 'rot', lambda t: (0.0, 0.0, -5.0 * tr(t)), 28)
    c.fn('right_arm', 'rot', lambda t: (-30.0 + 2.0 * tr(t), 0.0, 58.0 + 3.0 * tr2(t)), 14)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 2.0 * tr2(t), 0.0, -58.0 - 3.0 * tr(t)), 14)
    c.fn('right_claws', 'rot', lambda t: (-38.0 + 4.0 * tr(t), 0.0, 0.0), 14)
    c.fn('left_claws', 'rot', lambda t: (-38.0 + 4.0 * tr2(t), 0.0, 0.0), 14)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=14)
    return c


def charge(x):
    """Thorn thrust: the right claw is driven forward and down into the floor, the trunk lunging after it."""
    A, B, I, F, R, E = 0.12, 0.7, 1.0, 1.2, 1.5, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0.5), (B, 0, -2.0, 2.4), (I, 0, -2.6, -7.0, 'I'), (F, 0, -2.8, -7.6, 'O'),
                   (R, 0, -2.0, -5.2), (E, 0, 0, 0)])
    c.rot('trunk', [(0, 0, 0, 0), (B, 6, 14, 0), (I, 12, -6, 0, 'I'), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (B, 10, 24, 0), (I, 28, -10, 0, 'I'), (F, 30, -12, 0, 'b'), (R, 18, -6, 0),
                          (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -10, -18, 0), (I, -18, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 36, 0, 14), (I, -96, 0, 2, 'I'), (F, -100, 0, 2, 'b'), (R, -70, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -40, 0, 0), (I, 2, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_claws', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 20, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -60, 0, -16), (I, 24, 0, -12, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.66, 0.97, (-8.0, 0.5), 3.5), (1.5, 1.9, (0.0, 0.0), 2.5)],
         right_plan=[(0.12, 0.45, (3.0, 0.5), 1.5), (1.55, 1.92, (0.0, 0.0), 1.5)])
    return c


def summon(x):
    """Both claws plunge into the floor while the guardian sinks onto its roots; saplings answer."""
    A, B, Cc, I, F, R, E = 0.15, 0.9, 1.1, 1.3, 1.5, 1.95, 2.6
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0), (B, 0, 1.2, 0.5), (Cc, 0, 1.4, 0.6, 'o'), (I, 0, -5.5, -1.2, 'I'),
                   (F, 0, -5.8, -1.3, 'O'), (R, 0, -5.0, -1.0), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (B, -16, 0, 0), (I, 30, 0, 0, 'I'), (F, 34, 0, 0, 'O'), (R, 18, 0, 0),
                          (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -26, 0, 0), (I, 4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -20, 0, 0), (I, -6, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (B, -160, 0, 12 * sz), (Cc, -164, 0, 12 * sz), (I, -60, 0, -4 * sz, 'I'),
                                (F, -56, 0, -4 * sz), (R, -44, 0, -2 * sz), (E, 0, 0, 0)])
        c.rot('%s_claws' % side, [(0, 0, 0, 0), (B, -30, 0, 0), (I, 25, 0, 0, 'I'), (E, 0, 0, 0)])
    c.scl('canopy', [(0, 1, 1, 1), (B, 1.08, 1.08, 1.08), (I, 0.95, 0.95, 0.95, 'I'), (F, 1.03, 1.03, 1.03, 'b'),
                     (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.95, 1.28, (5.0, 1.5), 2.0), (2.05, 2.5, (0.0, 0.0), 2.0)],
         left_plan=[(0.95, 1.25, (-3.0, 1.5), 2.0), (2.1, 2.5, (0.0, 0.0), 1.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'right_claws', CLAW_R, 25.0, -110.0, -10.0)
        eranim.reach_y(c, x.rig, x.v, t, 'left_arm', 'x', 'left_claws', CLAW_L, 25.0, -110.0, -10.0)
    return c


def phase_change(x):
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    grow = lambda t: envelope(t, 0.8, 1.3, 0.3)
    hold = lambda t: envelope(t, 1.8, 2.55, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.25, 0, -0.5, 1.6, 'O'), (0.9, 0, -4.5, 0.6), (1.3, 0, -4.7, 0.5),
                   (1.75, 0, 1.4, -0.4, 'O'), (2.55, 0, 1.0, -0.3), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (0.25, -16, 0, 0, 'O'), (0.9, 30, 0, 0), (1.3, 34, 0, 0), (1.75, -26, 0, 0, 'O'),
                          (2.55, -22, 0, 0), (E, 0, 0, 0)])
    c.add('trunk_upper', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * (grow(t) + hold(t))), 48)
    c.rot('head', [(0, 0, 0, 0), (0.25, -20, 0, 0, 'O'), (0.9, 26, 0, 0), (1.75, -34, 0, 0, 'O'), (2.55, -30, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.9, 0, 0, 0), (1.75, -40, 0, 0, 'O'), (2.55, -34, 0, 0), (E, 0, 0, 0)])
    c.scl('canopy', [(0, 1, 1, 1), (0.9, 0.82, 0.82, 0.82), (1.3, 0.8, 0.8, 0.8), (1.8, 1.32, 1.32, 1.32, 'O'),
                     (2.55, 1.2, 1.2, 1.2), (E, 1, 1, 1)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.25, -30, 0, 40, 'O'), (0.9, -70, 0, -36), (1.3, -72, 0, -40),
                        (1.75, -50, 0, 92, 'O'), (2.55, -46, 0, 86), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.25, -26, 0, -40, 'O'), (0.9, -68, 0, 36), (1.3, -70, 0, 40),
                       (1.75, -50, 0, -94, 'O'), (2.55, -46, 0, -88), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 2.0 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -2.0 * tr(t + 0.3) * hold(t)), 48)
    for side in ('right', 'left'):
        c.rot('%s_claws' % side, [(0, 0, 0, 0), (0.9, 20, 0, 0), (1.75, -45, 0, 0, 'O'), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.3, 0.85, (6.0, 1.0), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.6, 3.05, (0.0, 0.0), 1.5)],
         left_plan=[(0.25, 0.6, (-2.0, 1.0), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.6, 3.05, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.5
    c = Clip('stagger', E, impact=0.15)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, -0.8, 2.6, 'O'), (0.45, 0, -1.4, 3.2), (0.8, 0, -0.6, 1.6), (E, 0, 0, 0)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (0.14, -22, 6, 8, 'O'), (0.45, -8, -4, 3), (0.7, 6, 2, -2), (1.0, 2, 0, 0),
                          (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -22, 10, 6, 'O'), (0.45, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.14, -24, 0, 0, 'O'), (0.6, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.14, -50, 0, 40, 'O'), (0.45, -22, 0, 18), (0.85, -6, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.14, -38, 0, -50, 'O'), (0.45, -18, 0, -26), (0.85, -4, 0, -6), (E, 0, 0, 0)])
    c.add('trunk_upper', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.16, 0.65, 0.15)), 24)
    feet(c, x, right_plan=[(0.12, 0.42, (5.0, 1.0), 2.5), (0.82, 1.18, (0.0, 0.0), 2.0)],
         left_plan=[(0.36, 0.6, (2.5, 0.0), 1.5), (1.02, 1.32, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """The guardian groans, sways and topples sideways like a felled tree (floor impact at 1.8 s); the canopy
    crushes against the floor."""
    E = 4.0
    c = Clip('death', E, impact=1.8)
    tr = shake(5.0, 1.0, 7)
    c.rot('base', [(0, 0, 0, 0), (0.4, 0, 0, -5, 'O'), (1.0, 4, 0, 8), (1.8, 6, 0, 84, 'I'), (2.05, 6, 0, 78, 'b'),
                   (2.4, 6, 0, 82), (E, 6, 0, 82)])
    c.rot('trunk_upper', [(0, 0, 0, 0), (0.4, -20, 0, 0, 'O'), (1.0, 10, 0, -4), (1.8, 4, 0, -8, 'I'),
                          (2.05, 0, 0, -4, 'b'), (E, 0, 0, -4)])
    c.add('trunk_upper', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.4, 1.0, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.4, -24, 0, 0, 'O'), (1.0, 10, 0, 0), (1.8, 0, 0, -10, 'I'), (E, 4, 0, -12)])
    c.rot('jaw', [(0, 0, 0, 0), (0.4, -36, 0, 0, 'O'), (1.0, -20, 0, 0), (2.0, -14, 0, 0), (E, -14, 0, 0)])
    c.scl('canopy', [(0, 1, 1, 1), (1.8, 1, 1, 1), (2.0, 1.12, 0.8, 1.12, 'O'), (E, 1.1, 0.84, 1.1)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.4, -40, 0, 50, 'O'), (1.0, -14, 0, 24), (1.8, -150, 0, 30, 'I'),
                        (2.1, -144, 0, 36, 'b'), (E, -144, 0, 36)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.4, -36, 0, -50, 'O'), (1.0, -12, 0, -20), (1.8, -10, 0, -60, 'I'),
                       (2.1, -12, 0, -54, 'b'), (2.9, -12, 0, -54), (3.1, -20, 0, -50, 'o'), (3.3, -12, 0, -54),
                       (E, -12, 0, -54)])
    for side in ('right', 'left'):
        c.rot('%s_root' % side, [(0, 0, 0, 0), (1.0, -10, 0, 0), (1.8, 2, 0, 0, 'I'), (E, 2, 0, 0)])
        c.rot('%s_shin' % side, [(0, 0, 0, 0), (1.0, 16, 0, 0), (1.8, 4, 0, 0, 'I'), (E, 4, 0, 0)])
    for t in (1.8, 2.05, 2.4, E):
        eranim.ground(c, x.rig, x.v, t, skip=('moss_r', 'moss_r2', 'moss_l', 'moss_l2', 'vine_r', 'vine_r2', 'vine_l',
                                               'vine_l2'))
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

def variants():
    def deco(p):
        f = p.face('trunk_upper', 'front')
        g = p.face('trunk_upper', 'front', glow=True)
        socket = (22, 14, 8)
        for (x0, x1) in ((2, 5), (10, 13)):
            f.rect(x0, 3, x1, 6, socket)
            f.hline(x0 - 1, x1 + 1, 2, (58, 40, 26))
            for x in range(x0 + 1, x1):
                f.px(x, 4, (255, 214, 90))
                f.px(x, 5, (255, 170, 40))
                g.px(x, 4, (255, 255, 255))
                g.px(x, 5, (255, 220, 160))
            g.fpx((x0 + 1) * f.k + 1, 4 * f.k, (255, 255, 255))
        mouth = [(4, 10), (5, 11), (6, 10), (7, 11), (8, 10), (9, 11), (10, 10), (11, 11)]
        for (x, y) in mouth:
            f.px(x, y, socket)
            f.px(x, y + 1, socket)
            f.px(x, y + 2, (40, 20, 10))
            g.px(x, y + 1, (255, 150, 40))
        for y in range(12, 15):
            for xx in range(4, 12):
                g.px(xx, y, (255, 120, 30) if (xx + y) % 2 else (255, 170, 60))   # the glow behind the jaw
        f.vline(7, 7, 9, (40, 26, 16))
        f.vline(8, 7, 9, (40, 26, 16))
        jf = p.face('jaw', 'front')
        for x in range(0, jf.w, 2):
            jf.px(x, jf.h - 1, (40, 26, 16))
        for fc in p.all_faces('mushrooms', index=0, glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (180, 255, 220))
        for fc in p.all_faces('mushrooms', index=1, glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (180, 255, 220))
        for part in ('canopy', 'canopy_r', 'canopy_l'):
            for fc in p.all_faces(part, glow=True, sides_only=True):
                for k in range(0, fc.fw, 7):
                    fc.fpx(k, (k * 3) % max(1, fc.fh), (255, 230, 255))
    bark = {'base': '#5e4430', 'style': 'bark', 'moss': (86, 130, 52), 'inner': '#b08a5a'}
    leaf = {'base': '#3f7a34', 'flowers': (235, 130, 170)}
    vine = {'base': '#3a6a2c', 'style': 'leaf', 'ragged': False}
    pal = {'trunk': bark, 'trunk2': bark, 'crown': bark, 'root': bark, 'shin': bark, 'foot': bark, 'toe': bark,
           'jaw': {'base': '#4e3826', 'style': 'bark'},
           'arm': bark, 'arm2': bark, 'twig': {'base': '#4a3424', 'style': 'bark'}, 'branch': {'base': '#4a3424', 'style': 'bark'},
           'canopy': leaf, 'canopy_top': dict(leaf, base='#4a8a3c'), 'clump': dict(leaf, base='#38703a'),
           'clump2': dict(leaf, base='#346a34'), 'tuft': dict(leaf, base='#4a8a3c'), 'moss': {'base': '#4f7a30', 'style': 'leaf'},
           'vine': vine, 'vine2': dict(vine, base='#346228'),
           'cap': {'base': '#d8f0e0', 'style': 'gem'}, 'cap2': {'base': '#c0e8d8', 'style': 'gem'}}
    pal.update({'crystal': {'base': '#2e1a44', 'style': 'crystal'}, 'crystal2': {'base': '#3a2252', 'style': 'crystal'},
                'thorn': {'base': '#24142e', 'style': 'crystal'}})

    def deco_form(form):
        def paint(p):
            deco(p)
            glow_faces(p, 'crystal', (200, 120, 255), every=2)
            for part in ('crystal_b', 'crystal_c'):
                glow_faces(p, part, (200, 120, 255), every=2)
            veins(p, ('trunk_upper', 'trunk'), (120, 60, 170), (210, 150, 255), seed=form)
            f = p.face('trunk_upper', 'front', glow=True)
            for (x0, x1) in ((2, 5), (10, 13)):
                for x in range(x0 + 1, x1):
                    f.px(x, 4, (230, 170, 255))
                    f.px(x, 5, (190, 110, 255))
            if form >= 2:
                veins(p, ('right_arm', 'left_arm', 'right_root', 'left_root'), (120, 60, 170), (210, 150, 255),
                      seed=form + 2)
                cracks(p, ('trunk',), (150, 80, 210), (220, 170, 255), every=30)
                for k in range(3):
                    glow_faces(p, 'thorns_r', (180, 100, 255), index=k, every=3)
        return paint
    autumn = {'base': '#8a5a26', 'flowers': (160, 60, 120)}
    pal3 = dict(pal, canopy=autumn, canopy_top=dict(autumn, base='#9a6a2c'), clump=dict(autumn, base='#7a4e22'),
                clump2=dict(autumn, base='#6e4620'), tuft=dict(autumn, base='#9a6a2c'),
                vine=dict(vine, base='#6a5a2a'), vine2=dict(vine, base='#5e5026'))
    return [Variant('b2', palette=pal, paint=deco, glow=True, extra=dict(scale=2.4, hitbox=(4.1, 7.4))),
            Variant('b2_p1', show={'crystal'}, palette=pal, paint=deco_form(1), glow=True, extra=dict(scale=2.4)),
            Variant('b2_p2', show={'crystal', 'thorns_r', 'thorns_l'}, palette=pal3,
                    poses={'crystal': dict(scale=(1.4, 1.5, 1.4))}, paint=deco_form(2), glow=True,
                    extra=dict(scale=2.4))]


TRAILS = [('right_claws', (-10.5, 9.0, -1.0), (-10.5, 15.0, -1.0)), ('left_claws', (10.5, 9.0, -1.0), (10.5, 15.0, -1.0))]


def rig():
    vs = variants()
    base = Rig('treant', 'TreantBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('treant', 'TreantBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.6
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
