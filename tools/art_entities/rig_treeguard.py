"""TREEGUARD boss rig: b2 古代樹の守護者 (the ancient tree's guardian), from the concept sheet.

A towering golem of stacked bark blocks: a small carved head with glowing lime eyes under a heavy brow, sunk between
two huge moss-capped shoulder blocks; a cushion of moss on its crown with great branching antlers that carry purple
and white flowers; a barrel chest bearing a large glowing knot rune; runes on the shoulders, forearms and shins; long
arms ending in four root fingers of two joints; thick pillar legs on splayed root feet. Moss lies on every upper face
and drips over the edges, and vines hang from the antlers, shoulders, chest and forearms (spring chains).

Motion: slow and creaking, the body leads and the limbs follow; fingers curl and splay. Actions: both fists raised
and hammered into the floor (slam), a wide low sweep of the right arm (sweep), runes flaring with the arms spread
(cast), a lunge that drives the right hand into the floor (charge, the thorn line), both hands sunk into the floor
to raise saplings (summon), a creaking bellow (roar), an awakening with blooming antlers (phase change), and a death
where it topples backwards like a felled tree. Timings are those of the old b2 look (BossRigData)."""
import math

import eranim
from boss_forms import glow_faces
from boss_kit import h, knot, moss, ragged, speckle
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
TIP_R = (-15.0, 22.0, -3.0)          # tip of the middle right finger (rest coordinates of right_f2b)
TIP_L = (15.0, 22.0, -3.0)
KNUCKLE_R = (-15.0, 11.0, -1.0)
MOUTH = (0.0, -21.0, -7.5)
FINGERS = ((-2.0, -2.6), (0.0, -3.0), (2.0, -2.6), (2.6, 1.6))     # x offset from the palm centre, z


def vine(name, x, y, z, segs=2, length=6, mat='leaf:vine'):
    """A hanging vine of {@code segs} segments (spring chain), top at (x, y, z)."""
    part = None
    for i in reversed(range(segs)):
        yy = y + i * length
        kids = [part] if part is not None else []
        part = P('%s%s' % (name, '' if i == 0 else str(i + 1)), (x, yy, z),
                 C(x - 0.5, yy, z - 0.5, 1, length, 1, mat if i == 0 else mat + '2'), *kids)
    return part


def hand(s):
    name = 'right' if s < 0 else 'left'
    cx = 15 * s
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    fingers = []
    for i, (dx, z) in enumerate(FINGERS):
        fx = cx + dx * (-s)                      # mirrored spread on the left hand
        tip = P('%s_f%db' % (name, i + 1), (fx, 17, z), C(fx - 1, 17, z - 1, 2, 5, 2, 'bark:fingertip', **k('ft%d' % i)),
                rot=(-22 if i < 3 else -10, 0, 0))
        fingers.append(P('%s_f%d' % (name, i + 1), (fx, 11, z), C(fx - 1, 11, z - 1, 2, 6, 2, 'bark:finger', **k('fg%d' % i)),
                         tip, rot=((-14, 0, 6 * s * (i - 1)) if i < 3 else (8, 0, 20 * s))))
    return P(name + '_hand', (cx, 7, -0.5), C(cx - 3.5, 7, -3.5, 7, 4, 6, 'bark:palm', **k('palm')), *fingers,
             rot=(6, 0, 0))


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x7 = -18.5 if s < 0 else 11.5
    x8 = -19 if s < 0 else 11
    fore = P(name + '_forearm', (15 * s, -4, 0),
             C(x8, -4, -4, 8, 11, 8, 'bark:farm', **k('farm')),
             C(x8, -4, -4, 8, 2, 8, 'leaf:band', inflate=0.35, **k('band')),
             hand(s),
             vine('vine_' + name[0], 15 * s + 4.2 * s, 0, 1.0, segs=2, length=6),
             rot=(-10, 0, 0))
    xs = -21 if s < 0 else 10
    return P(name + '_arm', (15 * s, -16, 0),
             C(xs, -22, -6, 11, 9, 12, 'bark:shoulder', **k('shoulder')),
             C(xs - 0.5, -23.5, -6.5, 12, 2, 13, 'leaf:shoulder_top', **k('stop')),
             C(x7, -13, -3.5, 7, 9, 7, 'bark:uarm', **k('uarm')),
             C(-19.5 if s < 0 else 17.5, -26, -2, 2, 3, 2, 'flower:bloom', **k('fl1')),
             C(-13.5 if s < 0 else 11.5, -25, 2, 2, 2, 2, 'flower:bloom2', **k('fl2')),
             vine('svine_' + name[0], 19.5 * s, -14, -6.2, segs=3, length=5),
             vine('svine2_' + name[0], 11.5 * s, -14, -6.2, segs=2, length=5),
             P('thorns_' + name[0], (15 * s, -23, 0),
               C(17 * s - 1, -28, -1, 2, 5, 2, 'crystal:thorn', **k('th1')),
               C(13 * s - 1, -26, 3, 2, 3, 2, 'crystal:thorn', **k('th2')), rot=(0, 0, 14 * s), opt=True),
             fore, rot=(0, 0, 7 * -s))


def antler(s):
    """Branching antler: a thick beam leaning out, an outer bend, an upper tine and a forward tine; flowers on the
    tips; a vine hangs from the bend."""
    name = 'r' if s < 0 else 'l'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    bx = 3.5 * s
    tip = P('antler_%s3' % name, (bx, -45, -1), C(bx - 1, -52, -2, 2, 7, 2, 'bark:twig', **k('at3')),
            C(bx - 1.5, -54, -2.5, 3, 2, 3, 'flower:bloom', **k('af1')), rot=(0, 0, -48 * s))
    outer = P('antler_%s2' % name, (bx, -38, -1), C(bx - 1.5, -45, -2.5, 3, 7, 3, 'bark:branch', **k('at2')),
              P('antler_%s5' % name, (bx, -44, -1), C(bx - 1, -49, -1, 2, 5, 2, 'bark:twig', **k('at5')),
                C(bx - 1, -50, -1, 2, 1, 2, 'flower:bloom2', **k('af4')), rot=(18, 0, -30 * s)),
              C(bx - 1 + (2 if s > 0 else -2), -43, -2, 2, 2, 2, 'flower:bloom2', **k('af2')),
              P('antler_%s4' % name, (bx, -42, -1), C(bx - 1, -43, -8, 2, 2, 7, 'bark:twig', **k('at4')),
                C(bx - 1.5, -44, -9.5, 3, 2, 2, 'flower:bloom', **k('af3')), rot=(-34, 0, 0)),
              vine('avine_' + name, bx, -41.5, -1, segs=2, length=5),
              tip, rot=(0, 0, 40 * s))
    return P('antler_' + name, (bx, -30, -1), C(bx - 1.5, -38, -2.5, 3, 8, 3, 'bark:antler', **k('at1')), outer,
             rot=(0, 0, 26 * s))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x9 = -10 if s < 0 else 1
    x8 = -9.5 if s < 0 else 1.5
    foot = P(name + '_foot', (5.5 * s, 20.5, -0.5),
             C(-10.5 if s < 0 else 0.5, 21, -7, 10, 3, 12, 'bark:foot', **k('foot')),
             C(-11.5 if s < 0 else 8.5, 22, -10, 3, 2, 4, 'bark:toe', **k('toe1')),
             C(-7.5 if s < 0 else 4.5, 22, -10.5, 3, 2, 4, 'bark:toe', **k('toe2')),
             C(-12.5 if s < 0 else 9.5, 22, 2, 3, 2, 4, 'bark:toe', **k('toe3')),
             C(-4.5 if s < 0 else 1.5, 22, 3.5, 3, 2, 3, 'bark:toe', **k('toe4')))
    shin = P(name + '_shin', (5.5 * s, 14, 0), C(x8, 14, -4, 8, 7, 8, 'bark:shin', **k('shin')),
             C(x8 - 0.5, 13, -4.5, 9, 3, 9, 'leaf:knee', **k('knee')), foot)
    return P(name + '_leg', (5.5 * s, 7, 0), C(x9, 6, -4.5, 9, 8, 9, 'bark:thigh', **k('thigh')), shin)


def geometry():
    crown = P('crown_moss', (0, -28, -1.5), C(-6, -31, -7.5, 12, 3, 12, 'leaf:crown'),
              C(-4, -33, -4.5, 8, 2, 7, 'leaf:crown2'),
              C(-6.5, -32.5, -7.8, 3, 2, 3, 'flower:bloom', tag='cf1'), C(3, -32, -2, 2, 1, 2, 'flower:bloom2', tag='cf2'),
              C(-1, -34.5, -3, 3, 2, 3, 'flower:bloom', share='cf1'),
              vine('hvine_r', -5.6, -28, -7.4, segs=2, length=5), vine('hvine_l', 5.6, -28, -7.4, segs=2, length=5))
    head = P('head', (0, -19, -1),
             C(-5, -28, -6.5, 10, 9, 10, 'bark:head', tag='head'),
             C(-5.5, -25.5, -7.2, 11, 2, 1, 'bark:brow'),
             C(-1, -23.6, -7.6, 2, 3, 1, 'bark:nose'),
             P('jaw', (0, -21.5, -6.5), C(-4, -21.5, -7.2, 8, 2, 1, 'bark:jaw')),
             crown, antler(-1), antler(1))
    rune = P('rune_chest', (0, -13, -6.6), C(-4.5, -17.5, -6.65, 9, 9, 0, 'rune:rune'))
    crystal = P('crystal', (-3, -16, 6.5), C(-5, -24, 5.5, 4, 8, 4, 'crystal:crystal'),
                P('crystal_b', (-1, -18, 6.5), C(-2, -23, 6, 2, 5, 2, 'crystal:crystal2', tag='cb'), rot=(20, 0, 30)),
                P('crystal_c', (-5, -18, 6.5), C(-6, -22, 6, 2, 4, 2, 'crystal:crystal2'), rot=(15, 0, -35)),
                rot=(24, 0, 10), opt=True)
    thorns = P('thorns', (0, -14, 0),
               C(-9, -23, 5, 2, 4, 2, 'crystal:thorn', tag='th'), C(7, -22, 5, 2, 4, 2, 'crystal:thorn', share='th'),
               C(2, -25, 5, 2, 4, 2, 'crystal:thorn', share='th'), opt=True)
    chest = P('chest', (0, -6, 0),
              C(-10, -20, -6.5, 20, 14, 13, 'bark:chest', tag='chest'),
              C(-10.5, -21.5, -7, 21, 2, 14, 'leaf:chest_top'),
              C(-6, -21, 4, 12, 6, 3, 'bark:back_block'),
              rune, head, arm(-1), arm(1), crystal, thorns,
              vine('cvine_1', -7.5, -20, -7.0, segs=2, length=5),
              vine('cvine_2', 6.5, -20, -7.0, segs=3, length=4),
              vine('cvine_3', -1.5, -20, 6.9, segs=2, length=6))
    torso = P('torso', (0, 2, 0), C(-6.5, -6, -5, 13, 8, 10, 'bark:waist'), C(-7, -7, -5.5, 14, 3, 11, 'leaf:waist_moss'),
              chest)
    hips = P('hips', (0, 6, 0), C(-7, 2, -5, 14, 6, 10, 'bark:pelvis'), torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

def _vines():
    out = []
    specs = [('vine_r', 2, 1.5), ('vine_l', 2, 1.55), ('svine_r', 3, 1.3), ('svine_l', 3, 1.32), ('svine2_r', 2, 1.45),
             ('svine2_l', 2, 1.5), ('cvine_1', 2, 1.4), ('cvine_2', 3, 1.35), ('cvine_3', 2, 1.3),
             ('avine_r', 2, 1.6), ('avine_l', 2, 1.65), ('hvine_r', 2, 1.55), ('hvine_l', 2, 1.6)]
    for name, segs, freq in specs:
        bones = [name] + ['%s%d' % (name, i) for i in range(2, segs + 1)]
        out.append(Chain(bones, rest='down', axes='xz', hang=0.92, freq=freq, damp=0.26, inertia=9.0, falloff=1.2,
                         limits={'x': (-85.0, 85.0), 'z': (-70.0, 70.0)}, key_rate=7))
    return out


CHAINS = _vines() + [
    Chain(['antler_r'], rest='up', axes='xz', hang=0.0, freq=2.4, damp=0.3, inertia=4.0, limits={'z': (-10.0, 10.0),
                                                                                                    'x': (-8.0, 8.0)}),
    Chain(['antler_l'], rest='up', axes='xz', hang=0.0, freq=2.5, damp=0.3, inertia=4.0, limits={'z': (-10.0, 10.0),
                                                                                                    'x': (-8.0, 8.0)}),
    Chain(['crown_moss'], rest='up', axes='xz', hang=0.0, freq=1.6, damp=0.4, inertia=3.0,
          limits={'x': (-6.0, 6.0), 'z': (-6.0, 6.0)}),
]
GAIT = Gait(2.4, stride=16.0, duty=0.66, lift=4.5, heel=6.0, toe=10.0)
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def creak(seed):
    return shake(1.2, 1.0, seed)


def curl(c, side, keys, tip=1.3):
    """Curls (negative) or splays (positive) the four root fingers of one hand through (t, angle, ease) keys."""
    for i in range(1, 5):
        k = 0.6 if i == 4 else 1.0
        c.rot('%s_f%d' % (side, i), [(t, a * k, 0, 0, e) for (t, a, e) in keys])
        c.rot('%s_f%db' % (side, i), [(t, a * k * tip, 0, 0, e) for (t, a, e) in keys])


def idle(x):
    T = 4.6
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.4)
    c.fn('base', 'pos', lambda t: (0.5 * sway(t), 0.3 * b(t) - 0.12, 0.0), 18)
    c.fn('hips', 'rot', lambda t: (0.0, 0.8 * math.sin(2 * math.pi * t / T), 1.0 * sway(t)), 18)
    c.fn('torso', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T + 1.1), -0.6 * math.sin(2 * math.pi * t / T),
                                    -0.8 * sway(t - 0.2)), 18)
    c.fn('chest', 'rot', lambda t: (-1.8 * b(t - 0.3), -0.8 * math.sin(2 * math.pi * t / T + 0.5),
                                    -0.6 * sway(t - 0.4)), 18)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.014 * b(t), 1.0 + 0.008 * b(t), 1.0 + 0.02 * b(t)), 18)
    look = [(0.0, 0.0, 's'), (0.9, 0.0, 's'), (1.7, 10.0, 's'), (2.5, 10.0, 's'), (3.3, -8.0, 's'), (4.0, -8.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (1.2 * math.sin(2 * math.pi * t / T), eranim.eased_value(look, t), 0.0), 36)
    r1, r2 = creak(1), creak(2)
    c.fn('crown_moss', 'rot', lambda t: (0.8 * r1(t), 0.0, 1.0 * r2(t)), 23)
    c.fn('right_arm', 'rot', lambda t: (1.2 * math.sin(2 * math.pi * t / T + 0.3), 0.0, 1.6 * b(t - 0.2)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.2 * math.sin(2 * math.pi * t / T + 0.3), 0.0, -1.6 * b(t - 0.25)), 12)
    flex = [(0.0, 0.0, 's'), (1.6, 0.0, 's'), (2.0, -14.0, 'O'), (2.5, 6.0, 's'), (2.9, 0.0, 's'), (T, 0.0, 's')]
    for i in range(1, 4):
        c.fn('right_f%d' % i, 'rot', lambda t, i=i: (eranim.eased_value(flex, t - 0.06 * i), 0.0, 0.0), 30)
        c.fn('left_f%d' % i, 'rot', lambda t, i=i: (eranim.eased_value(flex, t - 0.4 - 0.06 * i), 0.0, 0.0), 30)
    grind = [(0.0, 0.0, 's'), (3.4, 0.0, 's'), (3.65, -10.0, 'o'), (4.0, 0.0, 's'), (T, 0.0, 's')]
    c.fn('jaw', 'rot', lambda t: (eranim.eased_value(grind, t), 0.0, 0.0), 30)
    feet(c, x, samples=18)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.6 * w(t, 1, 0.25), -0.6 - 1.1 * w(t, 2, 0.08), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.0 * w(t, 2, 0.1), -6.0 * w(t), 4.5 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (4.0 + 1.2 * w(t, 2, 0.15), 3.0 * w(t, 1, 0.06), -2.5 * s1(t - 0.06)), 24)
    c.fn('chest', 'rot', lambda t: (3.0 + 1.2 * w(t, 2, 0.2), 3.0 * w(t, 1, 0.1), -2.5 * s1(t - 0.1)), 24)
    c.fn('head', 'rot', lambda t: (-5.0 - 1.0 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.12), 1.5 * s1(t - 0.14)), 24)
    c.fn('right_arm', 'rot', lambda t: (16.0 * w(t, 1, 0.1), 0.0, 2.0 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-16.0 * w(t, 1, 0.1), 0.0, -2.0 * w(t, 2, 0.1)), 24)
    c.fn('right_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, -w(t, 1, 0.16)), 0.0, 0.0), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, w(t, 1, 0.16)), 0.0, 0.0), 24)
    for i in range(1, 4):
        c.fn('right_f%d' % i, 'rot', lambda t, i=i: (-8.0 * w(t, 1, 0.25 + 0.03 * i), 0.0, 0.0), 24)
        c.fn('left_f%d' % i, 'rot', lambda t, i=i: (8.0 * w(t, 1, 0.25 + 0.03 * i), 0.0, 0.0), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Root slam: both arms heave up past the antlers as the chest arches back with a groan, then both fists hammer
    the floor in front of the feet (impact), fingers splayed into the earth, and it heaves itself upright."""
    A, B, Cc, I, F, R, E = 0.18, 0.88, 1.02, 1.2, 1.45, 1.95, 2.5
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0), (B, 0, 1.2, 1.6), (Cc, 0, 1.4, 1.8, 'o'), (I, 0, -5.6, -2.4, 'I'),
                   (F, 0, -6.0, -2.6, 'O'), (R, 0, -3.6, -1.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -4, 0, 0), (I, 10, 0, 0, 'I'), (F, 12, 0, 0, 'O'), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -8, 0, 0), (I, 16, 0, 0, 'I'), (F, 18, 0, 0, 'O'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -20, 0, 0), (Cc, -22, 0, 0, 'o'), (I, 24, 0, 0, 'I'),
                    (F, 27, 0, 0, 'O'), (R, 14, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -16, 0, 0, 'I'), (F, -18, 0, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -30, 0, 0, 'O'), (R, -8, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 12, 0, 4 * sz), (B, -170, 0, -14 * sz), (Cc, -175, 0, -16 * sz, 'o'),
                                (I, -60, 0, -8 * sz, 'I'), (F, -54, 0, -8 * sz, 'O'), (R, -40, 0, -4 * sz),
                                (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -36, 0, 0), (I, 4, 0, 0, 'I'), (F, 6, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (B, -30, 0, 0), (I, 10, 0, 0, 'I'), (E, 0, 0, 0)])
        curl(c, side, [(0, 0, 's'), (B, -40, 's'), (I, 34, 'I'), (F, 28, 'b'), (R, 10, 's'), (E, 0, 's')])
    feet(c, x, right_plan=[(0.2, 0.6, (6.0, 1.0), 3.0), (2.0, 2.4, (0.0, 0.0), 2.5)])
    for side, tip in (('right', TIP_R), ('left', TIP_L)):
        a = eranim.reach_y(c, x.rig, x.v, I, side + '_arm', 'x', side + '_f2b', tip, 24.2, -110.0, -10.0)
        eranim.reach_y(c, x.rig, x.v, F, side + '_arm', 'x', side + '_f2b', tip, 24.2, a - 14.0, a + 14.0)
    return c


def melee_sweep(x):
    """Branch sweep: the trunk winds to the right, then the long right arm swings low across the front from right
    to left (impact at the centre line), the fingers trailing through the air."""
    A, B, I, F, R, E = 0.15, 0.62, 0.9, 1.18, 1.6, 2.15
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0.3, -0.5, 0), (B, -1.4, -1.8, 1.0), (I, 1.2, -2.4, -1.8, 'I'),
                   (F, 1.8, -2.2, -2.2, 'O'), (R, 1.0, -1.2, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 12, 0), (I, 0, -10, 0, 'I'), (F, 0, -16, 0, 'b'), (R, 0, -8, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 4, 18, -3), (I, 8, -10, 3, 'I'), (F, 8, -22, 3, 'b'), (R, 4, -12, 1),
                    (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 0, -5, 0), (B, 6, 26, -5), (I, 10, -14, 4, 'I'), (F, 8, -30, 4, 'b'),
                    (R, 4, -16, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -26, 0), (I, 0, 10, 0, 'I'), (F, -2, 24, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 8, 0, -3), (B, -50, 70, 20), (I, -74, -6, 4, 'I'), (F, -68, -60, 0, 'O'),
                        (R, -36, -30, 0), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -26, 0, 0), (I, -4, 0, 0, 'I'), (F, -8, 0, 0), (E, 0, 0, 0)])
    curl(c, 'right', [(0, 0, 's'), (B, 20, 's'), (I, -10, 'I'), (F, -30, 'O'), (R, -10, 's'), (E, 0, 's')])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -20), (I, -16, 0, -38, 'I'), (F, 8, 0, -48, 'O'), (R, 0, 0, -20),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.68, 0.9, (-6.0, 2.0), 3.0), (1.7, 2.1, (0.0, 0.0), 2.0)],
         right_plan=[(0.15, 0.5, (3.0, 1.0), 1.5), (1.75, 2.1, (0.0, 0.0), 1.5)])
    return c


def cast(x):
    """The runes wake: the arms spread wide and lift, the chest swells with light and the jaw grinds open, then a
    forward heave releases the spores (impact)."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.1, 1.5, 2.05
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0), (B, 0, 0.8, 1.0), (I, 0, -1.4, -1.6, 'I'), (F, 0, -1.6, -1.8, 'O'),
                   (R, 0, -0.9, -1.0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -16, 0, 0), (I, 12, 0, 0, 'I'), (F, 14, 0, 0, 'b'), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -18, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -22, 0, 0), (I, -34, 0, 0, 'O'), (R, -10, 0, 0), (E, 0, 0, 0)])
    c.scl('rune_chest', [(0, 1, 1, 1), (B, 1.35, 1.35, 1.0), (I, 0.9, 0.9, 1.0, 'I'), (F, 1.1, 1.1, 1.0, 'b'),
                         (E, 1, 1, 1)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 8, 0, 0), (B, -110, 0, 50 * sz), (I, -76, 0, -2 * sz, 'I'),
                                (F, -82, 0, -2 * sz, 'b'), (R, -50, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -20, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
        curl(c, side, [(0, 0, 's'), (B, 30, 's'), (I, -16, 'I'), (F, 14, 'b'), (E, 0, 's')])
    feet(c, x, left_plan=[(0.6, 0.8, (-4.0, 0.5), 2.5), (1.6, 1.95, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'chest', (0.0, -13.0, -8.0)
    return c


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.0 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('chest', 'rot', lambda t: (-14.0 + 0.8 * tr(t), 0.0, 1.5 * tr2(t)), 28)
    c.fn('head', 'rot', lambda t: (-16.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 28)
    c.fn('jaw', 'rot', lambda t: (-34.0 + 3.0 * tr(t), 0.0, 0.0), 28)
    c.fn('crown_moss', 'rot', lambda t: (2.0 * tr(t), 0.0, 3.0 * tr2(t)), 28)
    c.fn('right_arm', 'rot', lambda t: (-26.0 + 2.0 * tr(t), 0.0, 52.0 + 3.0 * tr2(t)), 14)
    c.fn('left_arm', 'rot', lambda t: (-26.0 + 2.0 * tr2(t), 0.0, -52.0 - 3.0 * tr(t)), 14)
    c.fn('rune_chest', 'scale', lambda t: (1.12 + 0.06 * tr(t), 1.12 + 0.06 * tr(t), 1.0), 14)
    for side in ('right', 'left'):
        for i in range(1, 5):
            c.fn('%s_f%d' % (side, i), 'rot', lambda t, i=i: (26.0 + 3.0 * tr(t + 0.1 * i), 0.0, 0.0), 14)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=14)
    return c


def charge(x):
    """Thorn line: a lunge that drives the right hand fingers-first into the floor far ahead (impact), the trunk
    following it down, the left arm swinging back for balance."""
    A, B, I, F, R, E = 0.12, 0.7, 1.0, 1.2, 1.5, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0.5), (B, 0, -2.0, 2.4), (I, 0, -3.4, -7.0, 'I'), (F, 0, -3.6, -7.6, 'O'),
                   (R, 0, -2.4, -5.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 4, 12, 0), (I, 12, -6, 0, 'I'), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 10, 20, 0), (I, 30, -10, 0, 'I'), (F, 32, -12, 0, 'b'), (R, 18, -6, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -10, -18, 0), (I, -22, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 36, 0, 14), (I, -80, 0, 2, 'I'), (F, -84, 0, 2, 'b'), (R, -60, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -40, 0, 0), (I, 2, 0, 0, 'I'), (E, 0, 0, 0)])
    curl(c, 'right', [(0, 0, 's'), (B, -30, 's'), (I, 24, 'I'), (F, 20, 's'), (E, 0, 's')])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -56, 0, -16), (I, 26, 0, -12, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.66, 0.97, (-8.0, 0.5), 3.5), (1.5, 1.9, (0.0, 0.0), 2.5)],
         right_plan=[(0.12, 0.45, (3.0, 0.5), 1.5), (1.55, 1.92, (0.0, 0.0), 1.5)])
    eranim.reach_y(c, x.rig, x.v, I, 'right_arm', 'x', 'right_f2b', TIP_R, 24.4, -120.0, 0.0)
    return c


def summon(x):
    """Both hands sink into the floor at its sides as the guardian settles onto its roots (impact: the saplings
    stir), the chest rune swelling."""
    A, B, Cc, I, F, R, E = 0.15, 0.9, 1.1, 1.3, 1.5, 1.95, 2.6
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0), (B, 0, 1.0, 0.4), (Cc, 0, 1.2, 0.5, 'o'), (I, 0, -6.0, -1.0, 'I'),
                   (F, 0, -6.3, -1.1, 'O'), (R, 0, -5.4, -0.9), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -16, 0, 0), (I, 26, 0, 0, 'I'), (F, 30, 0, 0, 'O'), (R, 16, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -24, 0, 0), (I, 2, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -18, 0, 0), (I, -6, 0, 0), (E, 0, 0, 0)])
    c.scl('rune_chest', [(0, 1, 1, 1), (B, 1.2, 1.2, 1.0), (I, 1.4, 1.4, 1.0, 'O'), (R, 1.1, 1.1, 1.0), (E, 1, 1, 1)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (B, -150, 0, 30 * sz), (Cc, -154, 0, 30 * sz), (I, -36, 0, 16 * sz, 'I'),
                                (F, -34, 0, 16 * sz), (R, -26, 0, 10 * sz), (E, 0, 0, 0)])
        curl(c, side, [(0, 0, 's'), (B, -30, 's'), (I, 36, 'I'), (F, 30, 's'), (E, 0, 's')])
    feet(c, x, right_plan=[(0.95, 1.28, (5.0, 1.5), 2.0), (2.05, 2.5, (0.0, 0.0), 2.0)],
         left_plan=[(0.95, 1.25, (-3.0, 1.5), 2.0), (2.1, 2.5, (0.0, 0.0), 1.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'right_f2b', TIP_R, 24.6, -100.0, 20.0)
        eranim.reach_y(c, x.rig, x.v, t, 'left_arm', 'x', 'left_f2b', TIP_L, 24.6, -100.0, 20.0)
    return c


def phase_change(x):
    """Awakening: it crouches and folds its arms over the chest, then rears up with arms and antlers thrown wide
    (impact) as the flowers burst open and the runes blaze, trembling."""
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.55, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.25, 0, -0.5, 1.6, 'O'), (0.9, 0, -4.5, 0.6), (1.3, 0, -4.7, 0.5),
                   (1.75, 0, 1.4, -0.4, 'O'), (2.55, 0, 1.0, -0.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.25, -16, 0, 0, 'O'), (0.9, 28, 0, 0), (1.3, 32, 0, 0), (1.75, -24, 0, 0, 'O'),
                    (2.55, -20, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.25, -20, 0, 0, 'O'), (0.9, 24, 0, 0), (1.75, -30, 0, 0, 'O'), (2.55, -26, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.9, 0, 0, 0), (1.75, -40, 0, 0, 'O'), (2.55, -34, 0, 0), (E, 0, 0, 0)])
    c.scl('rune_chest', [(0, 1, 1, 1), (0.9, 0.8, 0.8, 1.0), (1.3, 0.8, 0.8, 1.0), (I, 1.6, 1.6, 1.0, 'O'),
                         (2.55, 1.35, 1.35, 1.0), (E, 1, 1, 1)])
    for part in ('antler_r4', 'antler_l4', 'antler_r3', 'antler_l3', 'crown_moss'):
        c.scl(part, [(0, 1, 1, 1), (1.3, 0.9, 0.9, 0.9), (I, 1.25, 1.25, 1.25, 'O'), (2.55, 1.15, 1.15, 1.15),
                     (E, 1, 1, 1)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.25, -30, 0, 40, 'O'), (0.9, -70, 0, -30), (1.3, -72, 0, -34),
                        (1.75, -50, 0, 90, 'O'), (2.55, -46, 0, 84), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.25, -26, 0, -40, 'O'), (0.9, -68, 0, 30), (1.3, -70, 0, 34),
                       (1.75, -50, 0, -92, 'O'), (2.55, -46, 0, -86), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 2.0 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -2.0 * tr(t + 0.3) * hold(t)), 48)
    for side in ('right', 'left'):
        curl(c, side, [(0, 0, 's'), (0.9, -30, 's'), (1.75, 40, 'O'), (2.55, 34, 's'), (E, 0, 's')])
    feet(c, x, right_plan=[(0.3, 0.85, (6.0, 1.0), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.6, 3.05, (0.0, 0.0), 1.5)],
         left_plan=[(0.25, 0.6, (-2.0, 1.0), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.6, 3.05, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.5
    c = Clip('stagger', E, impact=0.15)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, -0.8, 2.6, 'O'), (0.45, 0, -1.4, 3.2), (0.8, 0, -0.6, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.14, -20, 6, 8, 'O'), (0.45, -8, -4, 3), (0.7, 6, 2, -2), (1.0, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -20, 10, 6, 'O'), (0.45, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.14, -24, 0, 0, 'O'), (0.6, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.14, -46, 0, 36, 'O'), (0.45, -20, 0, 16), (0.85, -6, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.14, -36, 0, -46, 'O'), (0.45, -16, 0, -24), (0.85, -4, 0, -6), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.16, 0.65, 0.15)), 24)
    feet(c, x, right_plan=[(0.12, 0.42, (5.0, 1.0), 2.5), (0.82, 1.18, (0.0, 0.0), 2.0)],
         left_plan=[(0.36, 0.6, (2.5, 0.0), 1.5), (1.02, 1.32, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """It groans and sways, the arms fall open, then it topples backwards like a felled tree (impact on the floor);
    the antlers and the moss crown take the fall, one hand lifts a last time."""
    E, I = 4.0, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.rot('base', [(0, 0, 0, 0), (0.4, 5, 0, -3, 'O'), (1.0, -4, 0, 4), (I, -84, 0, 6, 'I'), (2.05, -78, 0, 6, 'b'),
                   (2.4, -82, 0, 6), (E, -82, 0, 6)])
    c.rot('chest', [(0, 0, 0, 0), (0.4, 18, 0, 0, 'O'), (1.0, -10, 0, 4), (I, -6, 0, 6, 'I'), (2.05, -2, 0, 4, 'b'),
                    (E, -2, 0, 4)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.4, 1.0, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.4, 20, 0, 0, 'O'), (1.0, -16, 0, 0), (I, -10, 20, 0, 'I'), (E, -12, 24, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.4, -30, 0, 0, 'O'), (1.0, -20, 0, 0), (2.0, -14, 0, 0), (E, -14, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.4, -30, 0, 40, 'O'), (1.0, -10, 0, 20), (I, -160, 0, 40, 'I'),
                        (2.1, -150, 0, 46, 'b'), (E, -150, 0, 46)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.4, -26, 0, -40, 'O'), (1.0, -8, 0, -24), (I, -170, 0, -36, 'I'),
                       (2.1, -160, 0, -42, 'b'), (2.9, -160, 0, -42), (3.15, -120, 0, -36, 'o'), (3.45, -160, 0, -42),
                       (E, -160, 0, -42)])
    for side in ('right', 'left'):
        curl(c, side, [(0, 0, 's'), (1.0, -20, 's'), (I, 30, 'I'), (2.9, 30, 's'), (3.3, -30, 's'), (E, -24, 's')])
        c.rot('%s_leg' % side, [(0, 0, 0, 0), (1.0, -8, 0, 0), (I, 6, 0, 0, 'I'), (E, 6, 0, 0)])
        c.rot('%s_shin' % side, [(0, 0, 0, 0), (1.0, 14, 0, 0), (I, 4, 0, 0, 'I'), (E, 4, 0, 0)])
    for t in (I, 2.05, 2.4, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE)
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

BARK = rgb('#6c4a2e')
BARK_DARK = rgb('#33200f')
BARK_LIGHT = rgb('#9a7448')
MOSS = rgb('#5f8f2a')
MOSS_LIGHT = rgb('#a6d43e')
MOSS_DARK = rgb('#33561a')
LIME = (196, 255, 60)
VIOLET = (200, 120, 255)


def block_seams(fc, seed, unit):
    """The body is built of stacked wooden blocks: staggered seams with a lit upper edge (fine pixels)."""
    y = int(h(seed, 'y0') * unit)
    row = 0
    while y < fc.fh:
        for x in range(fc.fw):
            fc.fpx(x, y, shade(fc.fget(x, y), 0.55))
            if y + 1 < fc.fh:
                fc.fpx(x, y + 1, lighten(fc.fget(x, y + 1), 0.12))
        x = int(h(seed, row) * unit * 1.5)
        while x < fc.fw:
            for yy in range(y, min(fc.fh, y + unit)):
                fc.fpx(x, yy, shade(fc.fget(x, yy), 0.55))
                if x + 1 < fc.fw:
                    fc.fpx(x + 1, yy, lighten(fc.fget(x + 1, yy), 0.1))
            x += unit + int(h(seed, row, x) * unit)
        y += unit
        row += 1


def bark_body(p, part, tag=None, index=0, unit=6, mossy=4, seed=0):
    for side in ('front', 'right', 'back', 'left'):
        fc = p.face(part, side, tag=tag, index=index)
        block_seams(fc, (part, side, seed), unit)
        if mossy:
            moss(fc, (part, side, 'moss', seed), mossy, MOSS, MOSS_LIGHT, MOSS_DARK)
            speckle(fc, (part, side, 'fleck'), 0.03, [MOSS, MOSS_LIGHT])
    top = p.face(part, 'top', tag=tag, index=index)
    speckle(top, (part, 'top'), 0.35, [MOSS, MOSS_LIGHT, MOSS_DARK])


def rune(p, part, side, cx, cy, size, color, tag=None, index=0, bold=False):
    fc = p.face(part, side, tag=tag, index=index)
    gfc = p.face(part, side, tag=tag, index=index, glow=True)
    # carved groove first (dark, one pixel wider all round), then the glowing line
    pts = knot(fc, cx, cy, size, shade(BARK_DARK, 0.8), bold=bold)
    for (x, y) in pts:
        for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
            if (x + dx, y + dy) not in pts:
                fc.fpx(x + dx, y + dy, BARK_DARK)
    knot(fc, cx, cy, size, color, gfc, lighten(color, 0.3), bold=bold)


def paint_eyes(p, color):
    f = p.face('head', 'front', tag='head')
    g = p.face('head', 'front', tag='head', glow=True)
    for (x0, x1) in ((3, 7), (12, 16)):
        for x in range(x0, x1 + 1):
            for y in (9, 10, 11):
                f.fpx(x, y, (24, 14, 6))
        for x in range(x0 + 1, x1):
            f.fpx(x, 10, color)
            g.fpx(x, 10, lighten(color, 0.4))
            g.fpx(x, 9, color)
            g.fpx(x, 11, shade(color, 0.7))
        g.fpx((x0 + x1) // 2, 10, (255, 255, 255))
        f.fpx((x0 + x1) // 2, 10, (255, 255, 240))
    for y in range(12, 18):                      # cheek cracks
        f.fpx(4, y, BARK_DARK)
        f.fpx(15, y, BARK_DARK)


def variants():
    bark = {'base': '#6c4a2e', 'style': 'bark', 'inner': '#a07a4a'}
    dark_bark = dict(bark, base='#5a3c24')
    moss_pal = {'base': '#5f8f2a', 'style': 'leaf', 'flowers': (190, 150, 240)}
    vine = {'base': '#4f7a24', 'style': 'leaf', 'ragged': False}
    pal = {
        'head': bark, 'brow': dark_bark, 'nose': dark_bark, 'jaw': dark_bark, 'chest': bark, 'back_block': dark_bark,
        'waist': dark_bark, 'pelvis': bark, 'shoulder': bark, 'uarm': dark_bark, 'farm': bark, 'palm': dark_bark,
        'finger': dict(bark, base='#5e4028'), 'fingertip': dict(bark, base='#523720'), 'thigh': bark,
        'shin': dark_bark, 'foot': dict(bark, base='#563a22'), 'toe': dict(bark, base='#4e3420'),
        'antler': dict(bark, base='#5e4228'), 'branch': dict(bark, base='#5a3e26'), 'twig': dict(bark, base='#523822'),
        'crown': moss_pal, 'crown2': dict(moss_pal, base='#6e9e30'), 'shoulder_top': moss_pal, 'chest_top': moss_pal,
        'waist_moss': dict(moss_pal, base='#557f26'), 'band': dict(moss_pal, base='#5a8a2a'),
        'knee': dict(moss_pal, base='#58862a'),
        'vine': vine, 'vine2': dict(vine, base='#456c20'),
        'bloom': {'base': '#b48ce8', 'style': 'gem'}, 'bloom2': {'base': '#f2f0f4', 'style': 'gem'},
        'rune': {'base': '#000000', 'style': 'plain'},
        'crystal': {'base': '#3a1e5a', 'style': 'crystal'}, 'crystal2': {'base': '#4a2670', 'style': 'crystal'},
        'thorn': {'base': '#2c163e', 'style': 'crystal'},
    }

    def deco(p, form=0):
        rune_col = LIME if form < 2 else mix(LIME, VIOLET, 0.75)
        for part, tag, unit in (('chest', 'chest', 7), ('head', 'head', 6), ('hips', None, 6), ('torso', None, 6),
                                ('right_arm', 'shoulder', 6), ('right_arm', 'uarm', 5), ('right_forearm', 'farm', 6),
                                ('right_leg', 'thigh', 6), ('right_shin', 'shin', 6), ('right_foot', 'foot', 5)):
            bark_body(p, part, tag=tag, unit=unit, mossy=3 if part not in ('right_foot',) else 0, seed=form)
        paint_eyes(p, rune_col)
        rune(p, 'chest', 'front', 19, 13, 15, rune_col, tag='chest', bold=True)
        rune(p, 'chest', 'back', 19, 12, 11, rune_col, tag='chest', bold=True)
        for side in ('right', 'front', 'back'):
            rune(p, 'right_arm', side, 10 if side != 'right' else 11, 8, 11, rune_col, tag='shoulder', bold=True)
        rune(p, 'right_forearm', 'right', 7, 11, 9, rune_col, tag='farm', bold=True)
        rune(p, 'right_forearm', 'front', 7, 12, 9, rune_col, tag='farm', bold=True)
        rune(p, 'right_shin', 'front', 7, 7, 9, rune_col, tag='shin', bold=True)
        rune(p, 'right_leg', 'front', 8, 8, 9, rune_col, tag='thigh', bold=True)
        # the chest rune plate: transparent but for the glow of the rune (it pulses when the guardian casts)
        rf = p.face('rune_chest', 'front')
        rg = p.face('rune_chest', 'front', glow=True)
        for y in range(rf.fh):
            for x in range(rf.fw):
                rf.fclear(x, y)
        pts = knot(rg, rf.fw // 2 - 1, rf.fh // 2 - 1, 15, lighten(rune_col, 0.5), bold=True)
        for (x, y) in pts:
            rf.fpx(x, y, lighten(rune_col, 0.35))
        bk = p.face('rune_chest', 'back')
        for y in range(bk.fh):
            for x in range(bk.fw):
                bk.fclear(x, y)
        jf = p.face('jaw', 'front')
        for x in range(0, jf.fw, 3):
            jf.fpx(x, jf.fh - 1, BARK_DARK)
        jg = p.face('jaw', 'back', glow=True)
        for x in range(jg.fw):
            for y in range(jg.fh):
                jg.fpx(x, y, shade(rune_col, 0.8))
        # moss that hangs over the edges and ragged leaf hems
        for part, tag in (('chest', None), ('right_arm', 'stop'), ('torso', None), ('right_shin', 'knee'),
                          ('crown_moss', None), ('right_forearm', 'band')):
            index = 1 if (part in ('chest', 'torso') and tag is None) else 0
            for side in ('front', 'right', 'back', 'left'):
                fc = p.face(part, side, tag=tag, index=index)
                ragged(fc, 3, (part, side, 'hem'), every=1)
        for part in ('right_arm',):
            for t in ('fl1', 'fl2'):
                for side in ('front', 'right', 'back', 'left', 'top'):
                    fc = p.face(part, side, tag=t)
                    fc.fpx(fc.fw // 2, fc.fh // 2, (250, 220, 90))
        if form >= 1:
            glow_faces(p, 'crystal', lighten(VIOLET, 0.2), every=2)
            for part in ('crystal_b',):
                glow_faces(p, part, lighten(VIOLET, 0.2), every=2)
        if form >= 2:
            glow_faces(p, 'thorns', VIOLET, index=0, every=3)
            for k in range(2):
                glow_faces(p, 'thorns_r', VIOLET, index=k, every=3)
    autumn = {'base': '#9a6a24', 'style': 'leaf', 'flowers': (230, 120, 160)}
    pal3 = dict(pal, crown=autumn, crown2=dict(autumn, base='#a8742a'), shoulder_top=autumn, chest_top=autumn,
                waist_moss=dict(autumn, base='#8a5e22'), band=dict(autumn, base='#946424'),
                knee=dict(autumn, base='#8e6222'))
    return [Variant('b2', palette=pal, paint=deco, glow=True, extra=dict(scale=2.15, hitbox=(4.1, 7.4))),
            Variant('b2_p1', show={'crystal'}, palette=pal, paint=lambda p: deco(p, 1), glow=True,
                    extra=dict(scale=2.15)),
            Variant('b2_p2', show={'crystal', 'thorns', 'thorns_r', 'thorns_l'}, palette=pal3, poses={'crystal': dict(scale=(1.4, 1.5, 1.4))},
                    paint=lambda p: deco(p, 2), glow=True, extra=dict(scale=2.15))]


TRAILS = [('right_hand', KNUCKLE_R, (-15.0, 18.0, -3.0)), ('left_hand', (15.0, 11.0, -1.0), (15.0, 18.0, -3.0))]


def rig():
    vs = variants()
    base = Rig('treeguard', 'TreeguardBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('treeguard', 'TreeguardBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.6
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
