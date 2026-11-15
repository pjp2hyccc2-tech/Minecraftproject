"""SANDTITAN boss rig: b3 砂塵の巨神 (the god of the sand storm), from the concept sheet.

A giant of sandstone bricks: a block head with deep sockets and amber eyes under a gold diadem with a turquoise gem
and a gold crest, long locks of white stone hanging down the sides and back; huge shoulder blocks, forearms and knee
blocks carved with glowing square-spiral glyphs; a gold pectoral and a gold belt set with turquoise; a dark red
loincloth with gold trim, front and back, and red strips hanging from the shoulders, all torn at the hem; block fists
and broad feet with glowing seams. In its right hand an enormous maul: a sandstone head bound in gold with a glyph on
every face, a long haft with gold rings and a gold pommel. Later forms crack with amber light and carry a ring of
floating sandstone blocks.

Motion: ponderous; every strike lands with the whole weight, the stone locks and the cloth swing after it. Actions:
an overhead maul smash (slam), a level sweep of the maul (sweep), the maul planted while the left hand calls the sand
(cast), a lowered-shoulder charge (charge), the maul driven head-down into the floor (summon), a bellow (roar), the
titan's roar with the maul raised (phase change) and a death where it falls to its knees and crashes forward, the maul
dropping. Timings are those of the old b3 look (BossRigData)."""
import math

import eranim
from boss_kit import crackle, frect, gem, h, ragged, square_spiral, trim
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
HEAD_TOP = (-17.0, -38.0, 0.0)       # top centre of the maul head (rest coordinates of part maul)
HEAD_LOW = (-17.0, -22.0, 0.0)       # bottom centre of the maul head
HEAD_MID = (-17.0, -30.0, 0.0)
POMMEL = (-17.0, 17.0, 0.0)
PALM_L = (17.0, 10.0, -4.0)


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 17 * s
    kids = []
    if s < 0:
        maul = P('maul', (cx, 9.5, 0),
                 C(cx - 1.5, -22, -1.5, 3, 39, 3, 'wood:haft'),
                 C(cx - 2, 13, -2, 4, 2, 4, 'gold:ring', tag='ring'),
                 C(cx - 2, -2, -2, 4, 2, 4, 'gold:ring', share='ring'),
                 C(cx - 2, -18, -2, 4, 2, 4, 'gold:ring', share='ring'),
                 C(cx - 2, 17, -2, 4, 3, 4, 'gold:pommel'),
                 C(cx - 8, -38, -8, 16, 16, 16, 'sandstone:maul', tag='maulhead'),
                 C(cx - 8, -36, -8, 16, 2, 16, 'gold:mband', inflate=0.4, tag='mband'),
                 C(cx - 8, -26, -8, 16, 2, 16, 'gold:mband', inflate=0.4, share='mband'),
                 C(cx - 3, -40, -3, 6, 2, 6, 'gold:mcap'),
                 rot=(0, 0, -33))
        kids.append(maul)
    fist = P(name + '_hand', (cx, 6, 0),
             C(cx - 5, 6, -5, 10, 7, 10, 'sandstone:fist', **k('fist')),
             C(cx - 5, 11, -5.6, 10, 2, 1, 'sandstone:knuckles', **k('knk')), *kids)
    fore = P(name + '_forearm', (cx, -6, 0),
             C(cx - 5, -6, -5, 10, 12, 10, 'sandstone:farm', **k('farm')),
             C(cx - 5, 3, -5, 10, 2, 10, 'gold:bracer', inflate=0.35, **k('bracer')),
             C(cx - 5, -6, -5, 10, 1, 10, 'gold:bracer2', inflate=0.35, **k('bracer2')),
             fist, rot=(-8, 0, 0))
    strips = []
    for i, dz in enumerate((-2.5, 2.5)):
        sx = cx + 3 * s if i == 0 else cx + 6 * s
        strips.append(P('strip_%s%d' % (name[0], i + 1), (sx, -16, 7.2),
                        C(sx - 1.5, -16, 6.8, 3, 9, 1, 'cloth:strip', **k('st%d' % i)),
                        P('strip_%s%db' % (name[0], i + 1), (sx, -7, 7.2),
                          C(sx - 1.5, -7, 6.8, 3, 9, 1, 'cloth:strip2', **k('sb%d' % i)))))
    return P(name + '_arm', (cx, -20, 0),
             C(cx - 6, -28, -7, 12, 11, 14, 'sandstone:shoulder', **k('shoulder')),
             C(cx - 6.5, -28.5, -7.5, 13, 2, 15, 'gold:paul_rim', **k('rim')),
             C(cx - 4.5, -18, -4.5, 9, 12, 9, 'sandstone:uarm', **k('uarm')),
             fore, *strips, rot=(0, 0, 9 * -s))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 6.5 * s
    foot = P(name + '_foot', (cx, 19.5, 0),
             C(cx - 5.5, 20, -7, 11, 4, 13, 'sandstone:foot', **k('foot')),
             C(cx - 5.5, 22, -8, 11, 2, 1, 'sandstone:toes', **k('toes')))
    shin = P(name + '_shin', (cx, 12, 0), C(cx - 4.5, 12, -4.5, 9, 8, 9, 'sandstone:shin', **k('shin')),
             C(cx - 5.5, 8, -6, 11, 7, 11, 'sandstone:knee', **k('knee')),
             C(cx - 5, 17, -5, 10, 2, 10, 'gold:anklet', inflate=0.3, **k('anklet')), foot)
    return P(name + '_leg', (cx, 4, 0), C(cx - 5, 3, -5, 10, 9, 10, 'sandstone:thigh', **k('thigh')), shin)


def hair():
    """Locks of white stone: long ones over the front of the shoulders, short ones resting on the shoulders, and a
    fall of them down the back (spring chains)."""
    locks = []
    specs = [('lock_r1', -6.6, -6.6, 16, (-12, 0, 6)), ('lock_r2', -7.2, -1.5, 9, (0, 0, 12)),
             ('lock_r3', -6.4, 3.0, 11, (14, 0, 8)),
             ('lock_l1', 6.6, -6.6, 16, (-12, 0, -6)), ('lock_l2', 7.2, -1.5, 9, (0, 0, -12)),
             ('lock_l3', 6.4, 3.0, 11, (14, 0, -8)),
             ('lock_b1', -3.0, 4.6, 16, (18, 0, 4)), ('lock_b2', 0.0, 4.8, 19, (16, 0, 0)),
             ('lock_b3', 3.0, 4.6, 16, (18, 0, -4))]
    for name, x, z, length, rot in specs:
        a = length // 2
        locks.append(P(name, (x, -33, z), C(x - 1, -33, z - 1, 2, a, 2, 'stone:lock'),
                       P(name + 'b', (x, -33 + a, z), C(x - 1, -33 + a, z - 1, 2, length - a, 2, 'stone:lock2')),
                       rot=rot))
    return locks


def geometry():
    head = P('head', (0, -24, -1),
             C(-5, -34, -6, 10, 10, 10, 'sandstone:head', tag='head'),
             C(-5.5, -32, -6.5, 11, 2, 11, 'gold:diadem'),
             C(-1, -32.8, -7.3, 2, 3, 1, 'gem:turq_head'),
             C(-1, -40, -3, 2, 6, 6, 'gold:crest'),
             C(-4, -35, -5, 8, 1, 8, 'sandstone:cap'),
             C(-6.5, -31, -3, 2, 4, 3, 'gold:ear', tag='ear'), C(4.5, -31, -3, 2, 4, 3, 'gold:ear', share='ear', mirror=True),
             P('jaw', (0, -26, -5.5), C(-3.5, -26, -6.5, 7, 2, 1, 'sandstone:jaw')),
             *hair())
    pectoral = C(-8, -24.5, -7.6, 16, 6, 1, 'gold:pectoral', tag='pect')
    rubble = P('rubble', (0, -6, 0),
               C(-26, -10, -3, 5, 5, 5, 'sandstone:rubble', tag='rb'), C(21, -14, 2, 5, 5, 5, 'sandstone:rubble', share='rb'),
               C(-4, -12, 17, 4, 4, 4, 'sandstone:rubble2', tag='rb2'), C(2, -18, -19, 4, 4, 4, 'sandstone:rubble2', share='rb2'),
               C(-20, -20, 13, 3, 3, 3, 'sandstone:rubble3', tag='rb3'), C(17, -4, -14, 3, 3, 3, 'sandstone:rubble3', share='rb3'),
               opt=True)
    chest = P('chest', (0, -9, 0),
              C(-11, -24, -7, 22, 15, 14, 'sandstone:chest', tag='chest'),
              pectoral,
              C(-1.5, -23.2, -8.4, 3, 3, 1, 'gem:turq'),
              head, arm(-1), arm(1))
    torso = P('torso', (0, -1, 0), C(-8, -9, -5.5, 16, 8, 11, 'sandstone:waist'), chest, rubble)
    loin_f = P('loin_f', (0, 1, -6), C(-5, 1, -6.6, 10, 8, 1, 'cloth:loin'),
               P('loin_f2', (0, 9, -6), C(-5, 9, -6.6, 10, 8, 1, 'cloth:loin2')))
    loin_b = P('loin_b', (0, 1, 6), C(-5, 1, 5.6, 10, 9, 1, 'cloth:loin'),
               P('loin_b2', (0, 10, 6), C(-5, 10, 5.6, 10, 8, 1, 'cloth:loin2')))
    hips = P('hips', (0, 3, 0),
             C(-9, -1, -6, 18, 6, 12, 'sandstone:pelvis'),
             C(-9.5, -2.5, -6.5, 19, 3, 13, 'gold:belt'),
             C(-2.5, -3, -7.4, 5, 4, 1, 'gold:buckle'),
             C(-1, -2, -8.0, 2, 2, 1, 'gem:turq_belt'),
             loin_f, loin_b, torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

def _chains():
    out = [Chain(['loin_f', 'loin_f2'], rest='down', axes='xz', hang=0.85, freq=1.4, damp=0.3, inertia=8.0,
                 limits={'x': (-80.0, 20.0), 'z': (-25.0, 25.0)}, drag={'walk': (-6.0, 0.0, 0.0)}),
           Chain(['loin_b', 'loin_b2'], rest='down', axes='xz', hang=0.85, freq=1.35, damp=0.3, inertia=8.0,
                 limits={'x': (-15.0, 80.0), 'z': (-25.0, 25.0)}, drag={'walk': (8.0, 0.0, 0.0)},
                 per_clip={'death': {'limits': {'x': (-60.0, 80.0), 'z': (-25.0, 25.0)}}})]
    for s in ('r', 'l'):
        for i in (1, 2):
            out.append(Chain(['strip_%s%d' % (s, i), 'strip_%s%db' % (s, i)], rest='down', axes='xz', hang=0.9,
                             freq=1.5 + 0.07 * i, damp=0.28, inertia=9.0, limits={'x': (-10.0, 95.0), 'z': (-40.0, 40.0)},
                             drag={'walk': (10.0, 0.0, 0.0), 'charge': (14.0, 0.0, 0.0)}))
    for name in ('lock_r1', 'lock_r2', 'lock_r3', 'lock_l1', 'lock_l2', 'lock_l3', 'lock_b1', 'lock_b2', 'lock_b3'):
        lim = {'x': (-50.0, 30.0), 'z': (-30.0, 30.0)} if name[5] != 'b' else {'x': (-10.0, 70.0), 'z': (-30.0, 30.0)}
        out.append(Chain([name, name + 'b'], rest='down', axes='xz', hang=0.8, freq=1.7 + 0.04 * int(name[-1]),
                         damp=0.38, inertia=5.0, limits=lim, key_rate=8))
    return out


CHAINS = _chains()
GAIT = Gait(2.2, stride=16.0, duty=0.66, lift=4.5, heel=6.0, toe=10.0)
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def idle(x):
    T = 4.5
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.5)
    c.fn('base', 'pos', lambda t: (0.5 * sway(t), 0.35 * b(t) - 0.15, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.0 * math.sin(2 * math.pi * t / T + 1.2), 1.0 * sway(t)), 16)
    c.fn('torso', 'rot', lambda t: (-1.2 * b(t), -0.8 * math.sin(2 * math.pi * t / T + 1.2), -0.8 * sway(t)), 16)
    c.fn('chest', 'rot', lambda t: (-1.6 * b(t - 0.3), 0.0, -0.6 * sway(t - 0.2)), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.012 * b(t), 1.0 + 0.008 * b(t), 1.0 + 0.018 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (0.9, 0.0, 's'), (1.6, 8.0, 's'), (2.4, 8.0, 's'), (3.1, -7.0, 's'), (3.9, -7.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.0 * b(t - 0.4) + 0.4, eranim.eased_value(look, t), 0.0), 36)
    c.fn('right_arm', 'rot', lambda t: (0.8 * math.sin(2 * math.pi * t / T), 0.0, 1.0 * b(t - 0.15)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.0 * math.sin(2 * math.pi * t / T), 0.0, -1.4 * b(t - 0.15)), 12)
    regrip = [(0.0, 0.0, 's'), (2.6, 0.0, 's'), (2.9, 5.0, 'o'), (3.2, -2.0, 's'), (3.5, 0.0, 's'), (T, 0.0, 's')]
    c.fn('maul', 'rot', lambda t: (eranim.eased_value(regrip, t), 0.0, 0.0), 30)
    c.fn('rubble', 'pos', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T * 2), 0.0), 18)
    c.fn('rubble', 'rot', lambda t: (0.0, 12.0 * math.sin(2 * math.pi * t / T), 0.0), 18)
    feet(c, x, samples=16)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.8 * w(t, 1, 0.25), -0.7 - 1.2 * w(t, 2, 0.08), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.0 * w(t, 2, 0.1), -6.0 * w(t), 5.0 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (4.0 + 1.2 * w(t, 2, 0.15), 3.5 * w(t, 1, 0.06), -2.5 * s1(t - 0.06)), 24)
    c.fn('chest', 'rot', lambda t: (2.0 + 1.0 * w(t, 2, 0.2), 3.0 * w(t, 1, 0.1), -2.5 * s1(t - 0.1)), 24)
    c.fn('head', 'rot', lambda t: (-4.0 - 1.0 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.12), 1.5 * s1(t - 0.14)), 24)
    c.fn('right_arm', 'rot', lambda t: (8.0 * w(t, 1, 0.1), 0.0, 1.0 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-16.0 * w(t, 1, 0.1), 0.0, -2.0 * w(t, 2, 0.1)), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, w(t, 1, 0.16)), 0.0, 0.0), 24)
    c.fn('maul', 'rot', lambda t: (-3.0 * w(t, 1, 0.2), 0.0, 1.5 * w(t, 2, 0.2)), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Overhead smash: the maul swings up and back over the head as the titan rears, hangs at the top, then comes
    down with the whole body behind it and the head hits the floor in front (impact); it heaves it back up."""
    A, B, Cc, I, F, R, E = 0.2, 0.78, 0.92, 1.1, 1.38, 1.85, 2.4
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -1.0, 0.5), (B, 0, 1.0, 1.8), (Cc, 0, 1.2, 1.9, 'o'), (I, 0, -5.4, -3.0, 'I'),
                   (F, 0, -5.8, -3.2, 'O'), (R, 0, -3.4, -1.8), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -4, 10, 0), (I, 10, -6, 0, 'I'), (F, 12, -6, 0, 'O'), (R, 6, -2, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -10, 12, 0), (I, 16, -8, 0, 'I'), (F, 18, -8, 0, 'O'),
                    (R, 8, -2, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -16, 10, 0), (Cc, -18, 10, 0, 'o'), (I, 22, -8, 0, 'I'), (F, 24, -8, 0, 'O'),
                    (R, 12, -2, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -12, -8, 0), (I, -16, 6, 0, 'I'), (F, -18, 6, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -12, 0, 0), (I, -26, 0, 0, 'O'), (R, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 16, 0, 4), (B, -165, 0, -14), (Cc, -170, 0, -16, 'o'), (I, -62, 0, -18, 'I'),
                        (F, -56, 0, -18, 'O'), (R, -40, 0, -10), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (A, 10, 0, -6), (B, 40, 0, -12), (Cc, 44, 0, -12, 'o'), (I, -50, 0, -12, 'I'),
                   (F, -54, 0, -12, 'O'), (R, -30, 0, -6), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -120, 0, 20), (I, -40, 0, -20, 'I'), (F, -34, 0, -24), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -40, 0, 0), (I, -20, 0, 0), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.25, 0.7, (-5.0, 1.0), 3.5), (1.95, 2.3, (0.0, 0.0), 3.0)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'maul', 'x', 'maul', HEAD_TOP, 24.4, -120.0, 20.0)
    return c


def melee_sweep(x):
    """Level sweep: the maul is drawn far back on the right, then swung flat across the front at waist height
    (impact at the centre) and carried round to the left, the titan pivoting on its feet."""
    A, B, I, F, R, E = 0.2, 0.7, 1.0, 1.28, 1.7, 2.25
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.4), (B, -1.4, -2.0, 1.2), (I, 1.0, -2.6, -1.8, 'I'),
                   (F, 1.8, -2.4, -2.2, 'O'), (R, 1.0, -1.4, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 18, 0), (I, 0, -10, 0, 'I'), (F, 0, -22, 0, 'b'), (R, 0, -10, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 4, 22, -4), (I, 8, -12, 3, 'I'), (F, 8, -28, 4, 'b'), (R, 4, -14, 2),
                    (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 6, 24, -4), (I, 8, -16, 4, 'I'), (F, 6, -30, 4, 'b'), (R, 2, -14, 2),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -34, 0), (I, 0, 14, 0, 'I'), (F, -2, 34, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -70, 80, 30), (I, -86, -4, 10, 'I'), (F, -80, -70, 6, 'O'),
                        (R, -40, -40, 4), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (B, 70, 0, -10), (I, 80, 0, -20, 'I'), (F, 76, 0, -24, 'O'), (R, 40, 0, -10),
                   (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -60, 0, -30), (I, -30, 0, -50, 'I'), (F, 6, 0, -56, 'O'), (R, 0, 0, -24),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.72, 0.98, (-6.0, 2.0), 3.5), (1.75, 2.15, (0.0, 0.0), 2.5)],
         right_plan=[(0.15, 0.55, (3.0, 1.0), 2.0), (1.8, 2.15, (0.0, 0.0), 2.0)])
    return c


def cast(x):
    """The maul is planted head-down, the left hand raised with the glyphs blazing, then thrust forward to hurl the
    sand (impact)."""
    A, B, I, F, R, E = 0.2, 0.55, 0.8, 1.1, 1.5, 2.05
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0), (B, 0, 0.6, 1.0), (I, 0, -1.6, -1.6, 'I'), (F, 0, -1.8, -1.8, 'O'),
                   (R, 0, -1.0, -1.0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -8, -10, 0), (I, 12, 8, 0, 'I'), (F, 14, 10, 0, 'b'), (R, 6, 4, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -8, -8, 0), (I, 10, 10, 0, 'I'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -6, -8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, -18, 0, 0), (I, -28, 0, 0, 'O'), (R, -8, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -20, 0, -4), (B, -36, 0, -6), (I, -30, 0, -6), (R, -20, 0, -4),
                        (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (A, 30, 0, -12), (B, 170, 0, -12), (F, 170, 0, -12), (R, 90, 0, -6), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, -30, 0, -6), (B, -168, 0, -20), (I, -86, 0, 4, 'I'), (F, -90, 0, 4, 'b'),
                       (R, -50, 0, 0), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -20, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -20, 0, 0), (I, -70, 0, 0, 'I'), (F, -74, 0, 0), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.6, 0.8, (-4.0, 0.5), 2.5), (1.6, 1.95, (0.0, 0.0), 2.0)])
    for t in (B, I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'maul', 'x', 'maul', HEAD_TOP, 23.9, 120.0, 220.0)
    c.muzzle_part, c.muzzle_point = 'left_hand', PALM_L
    return c


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(5.0, 1.0, 1), shake(6.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.2 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.2 * tr2(t)), 28)
    c.fn('head', 'rot', lambda t: (-18.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 28)
    c.fn('jaw', 'rot', lambda t: (-30.0 + 3.0 * tr(t), 0.0, 0.0), 28)
    c.fn('right_arm', 'rot', lambda t: (-20.0 + 2.0 * tr(t), 0.0, 26.0 + 2.0 * tr2(t)), 14)
    c.fn('maul', 'rot', lambda t: (-10.0, 0.0, -14.0 + 2.0 * tr(t)), 8)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 2.0 * tr2(t), 0.0, -48.0 - 3.0 * tr(t)), 14)
    c.fn('left_forearm', 'rot', lambda t: (-40.0 + 3.0 * tr(t), 0.0, 0.0), 14)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=14)
    return c


def charge(x):
    """Lowered-shoulder charge: the titan crouches behind its left shoulder with the maul drawn back, then lunges
    forward with the full mass (impact), the cloth streaming behind."""
    A, B, I, F, R, E = 0.15, 0.68, 1.0, 1.25, 1.55, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.6), (B, 0, -3.0, 2.8), (I, 0, -3.0, -8.0, 'I'), (F, 0, -3.2, -8.6, 'O'),
                   (R, 0, -2.0, -5.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 8, -16, 0), (I, 14, -20, 0, 'I'), (F, 14, -20, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 18, -14, 0), (I, 26, -18, 0, 'I'), (F, 28, -18, 0, 'O'), (R, 12, -8, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -18, 24, 0), (I, -24, 30, 0, 'I'), (F, -24, 30, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -10), (I, -70, 0, -4, 'I'), (F, -72, 0, -4, 'b'), (R, -40, 0, -2),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -70, 0, 0), (I, -80, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 40, 0, 14), (I, 50, 0, 16, 'I'), (F, 50, 0, 16), (R, 20, 0, 6), (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (B, 40, 0, -6), (I, 50, 0, -6), (R, 20, 0, -2), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.66, 0.97, (-10.0, 0.5), 4.0), (1.5, 1.9, (0.0, 0.0), 3.0)],
         right_plan=[(0.12, 0.45, (3.0, 0.5), 2.0), (1.55, 1.92, (0.0, 0.0), 2.0)])
    return c


def summon(x):
    """The maul is raised and driven head-down into the floor before the titan (impact), its left fist raised to
    the servants rising from the sand."""
    A, B, Cc, I, F, R, E = 0.2, 0.85, 1.05, 1.3, 1.55, 2.0, 2.6
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0), (B, 0, 1.2, 0.6), (Cc, 0, 1.4, 0.6, 'o'), (I, 0, -3.6, -1.6, 'I'),
                   (F, 0, -3.8, -1.7, 'O'), (R, 0, -2.6, -1.2), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -16, 0, 0), (I, 20, 0, 0, 'I'), (F, 22, 0, 0, 'O'), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -22, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -30, 0, -6), (B, -150, 0, -20), (Cc, -154, 0, -20, 'o'), (I, -58, 0, -16, 'I'),
                        (F, -54, 0, -16, 'O'), (R, -40, 0, -10), (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (A, 60, 0, -12), (B, 150, 0, -12), (I, 150, 0, -14, 'I'), (F, 150, 0, -14),
                   (R, 80, 0, -6), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -14), (I, -166, 0, -12, 'I'), (F, -164, 0, -12), (R, -80, 0, -6),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (I, -10, 0, 0), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.95, 1.28, (5.0, 1.5), 2.0), (2.05, 2.5, (0.0, 0.0), 2.0)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'maul', HEAD_TOP, 25.0, -120.0, 20.0)
    return c


def phase_change(x):
    """The titan's roar: it crouches with the maul across its body, then rears up with the maul raised high in the
    right hand and the left fist clenched (impact), trembling, the glyphs blazing."""
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(5.5, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.55, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -0.6, 1.4, 'O'), (0.95, 0, -4.4, 0.6), (1.35, 0, -4.6, 0.5),
                   (1.78, 0, 1.4, -0.4, 'O'), (2.55, 0, 1.0, -0.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.3, -14, 0, 0, 'O'), (0.95, 26, 0, 0), (1.35, 30, 0, 0), (1.78, -22, 0, 0, 'O'),
                    (2.55, -18, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.3, -18, 0, 0, 'O'), (0.95, 20, 0, 0), (1.78, -32, 0, 0, 'O'), (2.55, -28, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.95, 0, 0, 0), (1.78, -38, 0, 0, 'O'), (2.55, -32, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.3, -20, 0, 10, 'O'), (0.95, -60, 0, -30), (1.35, -62, 0, -32),
                        (1.78, -170, 0, 10, 'O'), (2.55, -166, 0, 8), (E, 0, 0, 0)])
    c.rot('maul', [(0, 0, 0, 0), (0.95, 60, 0, -40), (1.35, 62, 0, -42), (1.78, 0, 0, -6, 'O'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.3, -26, 0, -30, 'O'), (0.95, -60, 0, 20), (1.35, -62, 0, 24),
                       (1.78, -60, 0, -70, 'O'), (2.55, -56, 0, -66), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (0.95, -60, 0, 0), (1.78, -80, 0, 0, 'O'), (2.55, -76, 0, 0), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 1.5 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -1.5 * tr(t + 0.3) * hold(t)), 48)
    c.scl('rubble', [(0, 1, 1, 1), (0.95, 0.6, 0.6, 0.6), (1.78, 1.3, 1.3, 1.3, 'O'), (2.55, 1.15, 1.15, 1.15),
                     (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.3, 0.85, (6.0, 1.0), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.6, 3.05, (0.0, 0.0), 1.5)],
         left_plan=[(0.25, 0.6, (-2.0, 1.0), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.6, 3.05, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.5
    c = Clip('stagger', E, impact=0.15)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, -0.8, 2.6, 'O'), (0.45, 0, -1.4, 3.2), (0.8, 0, -0.6, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.14, -18, 6, 8, 'O'), (0.45, -8, -4, 3), (0.7, 6, 2, -2), (1.0, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -20, 10, 6, 'O'), (0.45, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.14, -22, 0, 0, 'O'), (0.6, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.14, -30, 0, 30, 'O'), (0.45, -14, 0, 14), (0.85, -4, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.14, -36, 0, -46, 'O'), (0.45, -16, 0, -24), (0.85, -4, 0, -6), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.16, 0.65, 0.15)), 24)
    feet(c, x, right_plan=[(0.12, 0.42, (5.0, 1.0), 2.5), (0.82, 1.18, (0.0, 0.0), 2.0)],
         left_plan=[(0.36, 0.6, (2.5, 0.0), 1.5), (1.02, 1.32, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """Cracks run through it; it drops the maul, sinks to its knees and crashes forward on its face (impact on the
    floor), the stone locks spilling over its head."""
    E, I = 4.0, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(4.5, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, -0.6, 1.2, 'O'), (1.1, 0, -9.0, 0.8, 'I'), (1.2, 0, -8.6, 0.8, 'O'),
                   (E, 0, -8.6, 0.8)])
    c.rot('hips', [(0, 0, 0, 0), (0.45, -4, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 8, 0, 0), (I, 30, 0, 0, 'I'),
                   (2.0, 26, 0, 0, 'b'), (E, 28, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.45, -16, 0, 4, 'O'), (1.1, 10, 0, -4), (1.35, 14, 0, -2), (I, 44, 0, 0, 'I'),
                    (2.0, 40, 0, 0, 'b'), (E, 42, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * envelope(t, 0.3, 1.0, 0.2)), 36)
    c.rot('chest', [(0, 0, 0, 0), (1.1, 6, 0, 0), (I, 18, 0, 0, 'I'), (E, 18, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.45, -24, 0, 0, 'O'), (1.1, 20, 0, 0), (I, -8, -30, 0, 'I'), (E, -8, -32, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.45, -30, 0, 0, 'O'), (1.1, -16, 0, 0), (E, -14, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -24, 0, 30, 'O'), (1.1, 6, 0, 10), (I, -110, 0, 30, 'I'),
                        (2.0, -104, 0, 34, 'b'), (E, -106, 0, 32)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, -20, 0, -36, 'O'), (1.1, 6, 0, -12), (I, -70, 0, -50, 'I'),
                       (2.0, -64, 0, -46, 'b'), (E, -66, 0, -48)])
    # the maul falls from the opening hand and comes to rest on the floor beside him
    c.rot('maul', [(0, 0, 0, 0), (0.45, 6, 0, -10), (0.9, -20, 0, 40, 'I'), (1.15, -24, 0, 84, 'I'),
                   (1.3, -24, 0, 78, 'b'), (E, -24, 0, 80)])
    c.pos('maul', [(0, 0, 0, 0), (0.45, 0, 0, 0), (1.15, 4, -2, 0, 'I'), (E, 4, -2, 0)])
    feet(c, x, right_plan=[(0.5, 1.1, (7.0, 0.4), 2.0)], left_plan=[(0.55, 1.12, (6.8, 0.4), 2.0)],
         right_holds=[(1.1, E, 64.0)], left_holds=[(1.12, E, 64.0)])
    for t in (1.1, 1.2, 1.35, I, 2.0, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE + ('maul',))
    eranim.rest_on_floor(c, x.rig, x.v, (1.15, 1.3, E), 'maul', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

SAND = rgb('#e4cf9e')
MORTAR = rgb('#a5835a')
SAND_LIGHT = rgb('#f6e8c4')
GOLD = rgb('#e2aa3a')
TURQ = rgb('#36c9bd')
AMBER = (255, 178, 60)
RED = rgb('#982a20')
STONE_WHITE = rgb('#efeae0')


def brickwork(fc, seed, rows=4, every=8):
    """Sandstone courses (fine pixels): mortar lines, staggered joints, sun-lit upper edges."""
    y = int(h(seed, 'y') * rows)
    course = 0
    while y < fc.fh:
        for x in range(fc.fw):
            fc.fpx(x, y, shade(fc.fget(x, y), 0.68))
            if y + 1 < fc.fh:
                fc.fpx(x, y + 1, lighten(fc.fget(x, y + 1), 0.1))
        x = (course % 2) * (every // 2) - int(h(seed, course) * 2)
        while x < fc.fw:
            for yy in range(y, min(fc.fh, y + rows)):
                if 0 <= x < fc.fw:
                    fc.fpx(x, yy, shade(fc.fget(x, yy), 0.7))
            x += every + int(h(seed, course, x) * 3) - 1
        y += rows
        course += 1


def glyph(p, part, side, cx, cy, size, color, tag=None, index=0, plate=True, bold=True):
    """A carved square-spiral glyph centred on (cx, cy) (fine pixels; {@code size} cells of 2 x 2 pixels when bold):
    a darker inset plate with a glowing rim, the spiral glowing."""
    fc = p.face(part, side, tag=tag, index=index)
    gfc = p.face(part, side, tag=tag, index=index, glow=True)
    sc = 2 if bold else 1
    span = size * sc
    x0, y0 = cx - span // 2, cy - span // 2
    if plate:
        frect(fc, x0 - 3, y0 - 3, x0 + span + 2, y0 + span + 2, shade(SAND, 0.8))
        for i in range(-3, span + 3):
            for (px, py) in ((x0 + i, y0 - 3), (x0 + i, y0 + span + 2), (x0 - 3, y0 + i), (x0 + span + 2, y0 + i)):
                fc.fpx(px, py, color)
                gfc.fpx(px, py, shade(color, 0.85))
    square_spiral(fc, x0, y0, size, color, gfc, lighten(color, 0.35), dark=shade(SAND, 0.45), scale=sc)


def sandstone(p, part, tag=None, index=0, rows=4, every=8, seed=0):
    for side in ('front', 'right', 'back', 'left', 'top'):
        brickwork(p.face(part, side, tag=tag, index=index), (part, tag, index, side, seed), rows, every)


def paint_head(p, eye):
    f = p.face('head', 'front', tag='head')
    g = p.face('head', 'front', tag='head', glow=True)
    sock = (60, 42, 26)
    for (x0, x1) in ((3, 8), (12, 17)):
        frect(f, x0, 9, x1, 13, sock)
        frect(f, x0 + 1, 10, x1 - 1, 12, (34, 22, 14))
        for x in range(x0 + 1, x1):
            f.fpx(x, 11, eye)
            g.fpx(x, 11, lighten(eye, 0.4))
            g.fpx(x, 10, shade(eye, 0.8))
        f.fpx((x0 + x1) // 2, 11, (255, 250, 220))
        g.fpx((x0 + x1) // 2, 11, (255, 255, 255))
    frect(f, 9, 10, 10, 15, shade(SAND, 0.7))              # nose ridge
    for x in range(5, 15):
        f.fpx(x, 17, shade(SAND, 0.6))
    d = p.face('head', 'front', index=1)
    trim(d, lighten(GOLD, 0.3), rows=(0,))
    trim(d, shade(GOLD, 0.7), rows=(-1,), shine=False)
    for side in ('front', 'right', 'back', 'left'):
        dd = p.face('head', side, index=1)
        for x in range(1, dd.fw - 1, 4):
            dd.fpx(x, 2, shade(GOLD, 0.6))
    gem(p.face('head', 'front', index=2), 0, 0, 4, 6, TURQ)
    cr = p.face('head', 'left', index=3)
    square_spiral(cr, 3, 2, 7, shade(GOLD, 0.55))
    cr2 = p.face('head', 'right', index=3)
    square_spiral(cr2, 3, 2, 7, shade(GOLD, 0.55))
    for name in ('lock_r1', 'lock_r2', 'lock_r3', 'lock_l1', 'lock_l2', 'lock_l3', 'lock_b1', 'lock_b2', 'lock_b3'):
        for part in (name, name + 'b'):
            for side in ('front', 'right', 'back', 'left'):
                lf = p.face(part, side)
                for y in range(1, lf.fh, 5):
                    for x in range(lf.fw):
                        lf.fpx(x, y, shade(lf.fget(x, y), 0.82))
        tip = p.face(name + 'b', 'bottom')
        for y in range(tip.fh):
            for x in range(tip.fw):
                tip.fpx(x, y, shade(STONE_WHITE, 0.75))


def paint_cloth(p, tear=1.0):
    for part in ('loin_f', 'loin_f2', 'loin_b', 'loin_b2'):
        for side in ('front', 'back'):
            f = p.face(part, side)
            if part.endswith('2'):
                trim(f, GOLD, rows=(-6, -5), shine=True)
                for x in range(2, f.fw - 2, 4):
                    f.fpx(x, f.fh - 8, lighten(GOLD, 0.2))
                    f.fpx(x + 1, f.fh - 9, shade(GOLD, 0.8))
            else:
                trim(f, GOLD, rows=(0, 1), shine=True)
                for x in range(3, f.fw - 3, 5):                 # gold diamonds down the cloth
                    for (dx, dy) in ((0, 4), (1, 5), (-1, 5), (0, 6)):
                        f.fpx(x + dx, dy, GOLD)
        for side in ('front', 'back', 'right', 'left'):
            if part.endswith('2'):
                ragged(p.face(part, side), int(6 * tear), (part, side), min_depth=1)
    for s in ('r',):
        for i in (1, 2):
            for side in ('front', 'back', 'right', 'left'):
                ragged(p.face('strip_%s%db' % (s, i), side), int(6 * tear), ('strip', i, side), min_depth=1)
            trim(p.face('strip_%s%d' % (s, i), 'back'), GOLD, rows=(0,), shine=True)


def paint_gold(p):
    pf = p.face('chest', 'front', tag='pect')
    trim(pf, lighten(GOLD, 0.3), rows=(0,))
    trim(pf, shade(GOLD, 0.65), rows=(-1,), shine=False)
    for x in range(2, pf.fw - 2, 4):
        gem(pf, x, 4, 2, 2, TURQ if (x // 4) % 2 else rgb('#c8382a'))
    gem(p.face('chest', 'front', index=2), 0, 0, 6, 6, TURQ)
    bf = p.face('hips', 'front', index=1)
    for x in range(3, bf.fw - 3, 6):
        gem(bf, x, 2, 2, 2, TURQ)
    for side in ('right', 'back', 'left'):
        b2 = p.face('hips', side, index=1)
        for x in range(3, b2.fw - 3, 6):
            gem(b2, x, 2, 2, 2, TURQ)
    bk = p.face('hips', 'front', index=2)
    trim(bk, lighten(GOLD, 0.3), rows=(0, -1), cols=(0, -1))
    gem(p.face('hips', 'front', index=3), 0, 0, 4, 4, TURQ)


def paint_maul(p, glow):
    for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
        f = p.face('maul', side, tag='maulhead')
        brickwork(f, ('maul', side), rows=5, every=9)
        glyph(p, 'maul', side, f.fw // 2, f.fh // 2 + (1 if side not in ('top', 'bottom') else 0), 9, glow,
              tag='maulhead')
    for side in ('front', 'right', 'back', 'left'):
        hf = p.face('maul', side, index=0)
        for y in range(0, hf.fh, 6):
            hf.fpx(0, y, shade(hf.fget(0, y), 0.7))


def variants():
    sand = {'base': '#e4cf9e', 'style': 'sandstone', 'cracks': False}
    gold = {'base': '#e2aa3a', 'style': 'gold'}
    cloth = {'base': '#982a20', 'style': 'cloth'}
    pal = {
        'head': sand, 'cap': dict(sand, base='#d8c290'), 'jaw': dict(sand, base='#d6c08e'), 'chest': sand,
        'waist': dict(sand, base='#dcc694'), 'pelvis': sand, 'shoulder': sand, 'uarm': dict(sand, base='#dcc694'),
        'farm': sand, 'fist': sand, 'knuckles': dict(sand, base='#d2bc8a'), 'thigh': sand,
        'shin': dict(sand, base='#dcc694'), 'knee': sand, 'foot': dict(sand, base='#d8c290'),
        'toes': dict(sand, base='#ccb684'), 'maul': dict(sand, base='#e0ca98'),
        'rubble': sand, 'rubble2': dict(sand, base='#d8c290'), 'rubble3': dict(sand, base='#ccb684'),
        'diadem': gold, 'crest': gold, 'ear': gold, 'pectoral': gold, 'belt': gold, 'buckle': gold, 'bracer': gold,
        'bracer2': gold, 'paul_rim': gold, 'anklet': gold, 'ring': gold, 'pommel': gold, 'mband': gold,
        'mcap': gold,
        'turq_head': {'base': '#36c9bd', 'style': 'gem'}, 'turq': {'base': '#36c9bd', 'style': 'gem'},
        'turq_belt': {'base': '#36c9bd', 'style': 'gem'},
        'haft': {'base': '#7a5232', 'style': 'wood'},
        'lock': {'base': '#efeae0', 'style': 'stone', 'cracks': False},
        'lock2': {'base': '#e6e0d4', 'style': 'stone', 'cracks': False},
        'loin': cloth, 'loin2': dict(cloth, base='#8a2620'), 'strip': dict(cloth, base='#8e281e'),
        'strip2': dict(cloth, base='#7e221a'),
    }

    def deco(p, form=0):
        glow = AMBER if form < 2 else (255, 140, 40)
        for part, tag in (('chest', 'chest'), ('torso', None), ('hips', None), ('right_arm', 'shoulder'),
                          ('right_arm', 'uarm'), ('right_forearm', 'farm'), ('right_hand', 'fist'),
                          ('right_leg', 'thigh'), ('right_shin', 'shin'), ('right_shin', 'knee'),
                          ('right_foot', 'foot'), ('head', 'head')):
            sandstone(p, part, tag=tag, seed=form)
        paint_head(p, glow)
        glyph(p, 'right_arm', 'right', 14, 11, 7, glow, tag='shoulder')
        glyph(p, 'right_arm', 'front', 12, 11, 7, glow, tag='shoulder')
        glyph(p, 'right_arm', 'back', 12, 11, 7, glow, tag='shoulder')
        glyph(p, 'right_forearm', 'right', 10, 10, 5, glow, tag='farm')
        glyph(p, 'right_forearm', 'front', 10, 10, 5, glow, tag='farm')
        glyph(p, 'right_shin', 'front', 11, 7, 5, glow, tag='knee')
        glyph(p, 'right_shin', 'right', 11, 7, 5, glow, tag='knee')
        glyph(p, 'chest', 'back', 22, 15, 9, glow, tag='chest')
        glyph(p, 'chest', 'front', 8, 21, 5, glow, tag='chest', plate=False, bold=False)
        glyph(p, 'chest', 'front', 36, 21, 5, glow, tag='chest', plate=False, bold=False)
        ft = p.face('right_foot', 'front', tag='foot')
        gft = p.face('right_foot', 'front', tag='foot', glow=True)
        for x in range(3, ft.fw - 3, 5):                     # glowing seams over the toes
            for y in range(1, ft.fh - 1):
                ft.fpx(x, y, glow)
                gft.fpx(x, y, lighten(glow, 0.3))
        paint_gold(p)
        paint_cloth(p, tear=1.0 + 0.3 * form)
        paint_maul(p, glow)
        if form >= 1:
            for part, tag in (('chest', 'chest'), ('right_arm', 'shoulder'), ('right_leg', 'thigh'),
                              ('torso', None)):
                for side in ('front', 'right', 'back', 'left'):
                    fc = p.face(part, side, tag=tag)
                    crackle(fc, (part, side, form), 2 + form, 9 + 3 * form, glow,
                            p.face(part, side, tag=tag, glow=True), lighten(glow, 0.3))
        if form >= 2:
            for k in range(6):
                for side in ('front', 'right', 'back', 'left', 'top'):
                    fc = p.face('rubble', side, index=k)
                    crackle(fc, ('rubble', k, side), 1, 6, glow, p.face('rubble', side, index=k, glow=True), glow)
    return [Variant('b3', palette=pal, paint=deco, glow=True, extra=dict(scale=2.6, hitbox=(5.6, 10.0))),
            Variant('b3_p1', palette=pal, paint=lambda p: deco(p, 1), glow=True, extra=dict(scale=2.6)),
            Variant('b3_p2', show={'rubble'}, palette=pal, paint=lambda p: deco(p, 2), glow=True,
                    extra=dict(scale=2.6))]


TRAILS = [('maul', HEAD_LOW, HEAD_TOP)]


def rig():
    vs = variants()
    base = Rig('sandtitan', 'SandtitanBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('sandtitan', 'SandtitanBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.4
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
