"""SKYGOD boss rig: b7 天空の機械神 (the machine god of the sky), from the concept sheet.

A tall angelic machine of white stone and gold, floating above the floor: a small helmed head with a gold crest and
fins and a glowing cyan visor, a golden halo ring behind it set with cyan gems and four spikes; a great chest of white
and gold with a cyan core in a gold cross frame; massive stepped white pauldrons rimmed in gold; segmented arms with gold
joints ending in dark mechanical claws of three fingers and a thumb; wings of five white blade-feathers on each side,
gold at the root, grooved with cyan light; a long robe of white plates with gold edges and cyan lines over legs of
white stone. Later forms add light, then overload: the core and the lines burn hotter, a second halo, wider wings.

Motion: weightless and exact; it hovers, the halo sways, the feathers breathe, the robe plates swing (spring chains).
Actions: a dive that drives both claws into the floor (slam), a sweeping laser from the core as the body turns (sweep),
missiles with the wings flared (cast, from the core), a braced beam forward with recoil (charge, the laser line), the
arms and wings raised as the halo flares (summon), a system alert with everything spread (roar), a reboot that powers
down and snaps back with the halo widening (phase change), and a death where the halo drops, the wings fall and it
crashes to its knees and forward. Timings are those of the old b7 look (BossRigData)."""
import math

import eranim
from boss_kit import diamond, gem, h, rivets, trim
from boss_motion import Ctx, build, finish_rig
from eranim import Chain, Clip, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
HOVER = 2.2
CORE = (0.0, -15.0, -8.5)
TIP_R = (-12.5, 19.0, -2.5)         # tip of the middle right finger (rest coordinates of right_f2b)
TIP_L = (12.5, 19.0, -2.5)
KNUCKLE_R = (-12.5, 9.0, -2.5)
HALO_R = 11.0
FEATHERS = ((40, 18), (20, 20), (0, 21), (-20, 19), (-40, 16))      # fan angle, length


def halo(name='halo', radius=HALO_R, z=7.5, cy=-38.0, opt=False, tagbase='hl'):
    """A ring of gold segments around (0, cy) in the plane facing forward, eight cyan gems and four spikes."""
    parts = []
    for k in range(8):
        cubes = [C(-2, cy - radius - 0.5, z - 0.5, 4, 1, 1, 'gold:%s_seg' % name, tag='%s_s' % tagbase if k == 0 else None,
                   share=None if k == 0 else '%s_s' % tagbase),
                 C(-2, cy + radius - 0.5, z - 0.5, 4, 1, 1, 'gold:%s_seg' % name, share='%s_s' % tagbase)]
        if k % 2 == 0:
            cubes += [C(-1, cy - radius - 1, z - 1, 2, 2, 2, 'gem:%s_gem' % name, tag='%s_g' % tagbase if k == 0 else None,
                        share=None if k == 0 else '%s_g' % tagbase),
                      C(-1, cy + radius - 1, z - 1, 2, 2, 2, 'gem:%s_gem' % name, share='%s_g' % tagbase)]
        if k % 4 == 0:
            cubes += [C(-0.5, cy - radius - 5, z - 0.5, 1, 4, 1, 'gold:%s_spike' % name,
                        tag='%s_p' % tagbase if k == 0 else None, share=None if k == 0 else '%s_p' % tagbase),
                      C(-0.5, cy + radius + 1, z - 0.5, 1, 4, 1, 'gold:%s_spike' % name, share='%s_p' % tagbase)]
        parts.append(P('%s_%d' % (name, k), (0, cy, z), *cubes, rot=(0, 0, 22.5 * k)))
    return P(name, (0, cy, z), *parts, rot=(-12, 0, 0), opt=opt)


def wing(s):
    name = 'r' if s < 0 else 'l'
    feathers = []
    for k, (fan, length) in enumerate(FEATHERS):
        tagk = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x0 = -4 - 2 - length if s < 0 else 4 + 2
        feathers.append(P('feather_%s%d' % (name, k + 1), (4 * s, -19, 8.5),
                          C(4 * s - 1.5, -20.5, 7, 3, 3, 3, 'gold:froot',
                            **tagk('fr%d' % k)),
                          C(x0, -21, 7.5, length, 4, 2, 'stone:blade%d' % (k % 3), **tagk('fb%d' % k)),
                          C(x0 - (2 if s < 0 else -length), -20.5, 7.5, 2, 3, 2, 'gold:ftip', **tagk('ft%d' % k)),
                          rot=(0, 18 * s * -1, fan * -s)))
    return P('wing_' + name, (4 * s, -19, 8.5), *feathers)


def hand(s):
    name = 'right' if s < 0 else 'left'
    cx = 12.5 * s
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    fingers = []
    for i, (dx, z) in enumerate(((-2.0, -2.5), (0.0, -2.5), (2.0, -2.5), (2.5, 1.5))):
        fx = cx + dx * (-s)
        tip = P('%s_f%db' % (name, i + 1), (fx, 14, z), C(fx - 1, 14, z - 1, 2, 5, 2, 'mech:claw_tip', **k('ct%d' % i)),
                rot=(-28 if i < 3 else -12, 0, 0))
        fingers.append(P('%s_f%d' % (name, i + 1), (fx, 9, z), C(fx - 1, 9, z - 1, 2, 5, 2, 'mech:claw', **k('cl%d' % i)),
                         C(fx - 1, 8.5, z - 1, 2, 1, 2, 'gold:joint', inflate=0.25, **k('cj%d' % i)), tip,
                         rot=((-18, 0, 5 * s * (i - 1)) if i < 3 else (10, 0, 18 * s))))
    return P(name + '_hand', (cx, 5, 0), C(cx - 3, 5, -3.5, 6, 4, 7, 'mech:palm', **k('palm')),
             C(cx - 3, 5, -4, 6, 2, 1, 'gold:knuckle', **k('knuckle')), *fingers)


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 12.5 * s
    xp = -19 if s < 0 else 8
    fore = P(name + '_forearm', (cx, -7, 0),
             C(cx - 3.5, -6, -4, 7, 11, 8, 'stone:farm', **k('farm')),
             C(cx - 3.5, -2, -4, 7, 1, 8, 'gold:band', inflate=0.3, **k('band')),
             C(cx - 3.5, 3, -4, 7, 1, 8, 'gold:band2', inflate=0.3, **k('band2')),
             hand(s), rot=(-10, 0, 0))
    return P(name + '_arm', (cx, -19, 0),
             C(xp, -24, -6.5, 11, 9, 13, 'stone:pauldron', **k('paul')),
             C(xp - 0.5, -25, -7, 12, 2, 14, 'gold:prim', inflate=0.2, **k('prim')),
             C(-19.5 if s < 0 else 10.5, -17, -6, 9, 3, 12, 'stone:pauldron2', **k('paul2')),
             C(cx - 3, -16, -3, 6, 9, 6, 'mech:uarm', **k('uarm')),
             C(cx - 3.5, -8, -3.5, 7, 3, 7, 'gold:elbow', **k('elbow')),
             fore, rot=((-6, 0, 10) if s < 0 else (-6, 0, -10)))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 4.5 * s
    foot = P(name + '_foot', (cx, 21, -0.5), C(cx - 3.5, 21, -5, 7, 3, 9, 'stone:foot', **k('foot')),
             C(cx - 3.5, 22, -6, 7, 2, 1, 'gold:toe', **k('toe')), rot=(18, 0, 0))
    shin = P(name + '_shin', (cx, 14, 0), C(cx - 3, 14, -3, 6, 7, 6, 'stone:shin', **k('shin')),
             C(cx - 3, 18, -3, 6, 1, 6, 'gold:anklet', inflate=0.3, **k('anklet')), foot, rot=(10, 0, 0))
    return P(name + '_leg', (cx, 6.5, 0), C(cx - 3.5, 6, -3.5, 7, 8, 7, 'stone:thigh', **k('thigh')),
             C(cx - 4, 12.5, -4.2, 8, 3, 2, 'gold:knee', **k('knee')), shin, rot=(-6, 0, 3 * -s))


def skirt():
    plates = []
    for side, s in (('r', -1), ('l', 1)):
        k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        plates.append(P('skirt_f' + side, (2.7 * s, 1.5, -4.8), C(-5.2 if s < 0 else 0.2, 1.5, -5.3, 5, 14, 1, 'stone:plate',
                                                                  **k('pf'))))
        plates.append(P('skirt_s' + side, (6.6 * s, 1.5, 0), C(-7.6 if s < 0 else 6.6, 1.5, -3.5, 1, 13, 7,
                                                               'stone:plate_side', **k('ps'))))
        plates.append(P('skirt_b' + side, (2.7 * s, 1.5, 4.8), C(-5.2 if s < 0 else 0.2, 1.5, 4.3, 5, 15, 1,
                                                                 'stone:plate_back', **k('pb'))))
    plates.append(P('skirt_c', (0, 1.5, -5.6), C(-1.5, 1.5, -6.1, 3, 16, 1, 'gold:center')))
    return plates


def geometry():
    head = P('head', (0, -24, -1),
             C(-3.5, -31, -4.5, 7, 7, 7, 'stone:head', tag='head'),
             C(-3, -29, -5.2, 6, 2, 1, 'visor:visor'),
             C(-0.5, -36, -4, 1, 5, 7, 'gold:crest'),
             C(-5, -32, -2, 1, 4, 4, 'gold:fin', tag='fin'), C(4, -32, -2, 1, 4, 4, 'gold:fin', share='fin', mirror=True),
             C(-3.5, -25, -5, 7, 1, 2, 'gold:chin'),
             C(-4, -31.5, -5, 8, 1, 8, 'gold:circlet'))
    core = P('core', (0, -15, -7), C(-3, -18, -8.2, 6, 6, 2, 'glowcore:core'))
    chest = P('chest', (0, -7, 0),
              C(-9, -23, -6, 18, 16, 12, 'stone:chest', tag='chest'),
              C(-5, -20, -6.8, 10, 10, 1, 'gold:frame'),
              C(-6, -25, -5, 12, 2, 10, 'gold:collar'),
              C(-5, -21, 6, 10, 10, 3, 'mech:backpack'),
              C(-9.5, -12, -6.5, 19, 2, 13, 'gold:rib', inflate=0.1),
              core, head, halo(), halo('halo2', radius=15.0, z=9.5, cy=-38.0, opt=True, tagbase='h2'),
              arm(-1), arm(1), wing(-1), wing(1))
    torso = P('torso', (0, 1, 0), C(-5, -7, -4, 10, 8, 8, 'mech:abdomen'),
              C(-5.5, -6, -4.5, 11, 1, 9, 'gold:rib2', tag='rib2'), C(-5.5, -2.5, -4.5, 11, 1, 9, 'gold:rib2', share='rib2'),
              chest)
    hips = P('hips', (0, 4, 0),
             C(-6, 1, -4, 12, 6, 8, 'mech:pelvis'),
             C(-6.5, 0, -4.5, 13, 2, 9, 'gold:belt'),
             C(-1.5, -0.5, -5.1, 3, 3, 1, 'gem:belt_gem'),
             *skirt(), torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['skirt_fr'], rest='down', axes='xz', hang=0.8, freq=1.6, damp=0.35, inertia=6.0,
          limits={'x': (-60.0, 10.0), 'z': (-20.0, 20.0)}, drag={'walk': (-8.0, 0.0, 0.0)}),
    Chain(['skirt_fl'], rest='down', axes='xz', hang=0.8, freq=1.65, damp=0.35, inertia=6.0,
          limits={'x': (-60.0, 10.0), 'z': (-20.0, 20.0)}, drag={'walk': (-8.0, 0.0, 0.0)}),
    Chain(['skirt_c'], rest='down', axes='xz', hang=0.8, freq=1.7, damp=0.35, inertia=6.0,
          limits={'x': (-60.0, 10.0), 'z': (-15.0, 15.0)}, drag={'walk': (-6.0, 0.0, 0.0)}),
    Chain(['skirt_sr'], rest='down', axes='xz', hang=0.8, freq=1.55, damp=0.35, inertia=6.0,
          limits={'x': (-25.0, 25.0), 'z': (-5.0, 45.0)}),
    Chain(['skirt_sl'], rest='down', axes='xz', hang=0.8, freq=1.6, damp=0.35, inertia=6.0,
          limits={'x': (-25.0, 25.0), 'z': (-45.0, 5.0)}),
    Chain(['skirt_br'], rest='down', axes='xz', hang=0.8, freq=1.5, damp=0.35, inertia=6.0,
          limits={'x': (-10.0, 60.0), 'z': (-20.0, 20.0)}, drag={'walk': (10.0, 0.0, 0.0)}),
    Chain(['skirt_bl'], rest='down', axes='xz', hang=0.8, freq=1.55, damp=0.35, inertia=6.0,
          limits={'x': (-10.0, 60.0), 'z': (-20.0, 20.0)}, drag={'walk': (10.0, 0.0, 0.0)}),
]
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def feathers(c, keys, side, spread=1.0):
    """Fans one wing: keys (t, open, ease); open 0 = rest, 1 = spread wide (and up), -1 = folded."""
    s = -1 if side == 'r' else 1
    for k, (fan, _) in enumerate(FEATHERS):
        extra = (2 - k) * 9.0 * spread
        c.rot('feather_%s%d' % (side, k + 1), [(t, 0, 14 * o * s, (extra * o + 10 * o) * -s if o > 0 else
                                                 (extra * 0.6 * o) * -s, e) for (t, o, e) in keys])


def claws(c, side, keys, tip=1.3):
    for i in range(1, 5):
        kk = 0.6 if i == 4 else 1.0
        c.rot('%s_f%d' % (side, i), [(t, a * kk, 0, 0, e) for (t, a, e) in keys])
        c.rot('%s_f%db' % (side, i), [(t, a * kk * tip, 0, 0, e) for (t, a, e) in keys])


def idle(x):
    T = 4.5
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.3 * math.sin(2 * math.pi * t / T + 0.8), 0.8 * math.sin(2 * math.pi * t / T),
                                   0.0), 18)
    c.fn('hips', 'rot', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T + 1.2), 0.8 * math.sin(2 * math.pi * t / T)),
         18)
    c.fn('chest', 'rot', lambda t: (-1.2 * b(t), 0.0, -0.6 * math.sin(2 * math.pi * t / T)), 18)
    scan = [(0.0, 0.0, 's'), (0.8, 0.0, 's'), (1.1, 12.0, 'O'), (2.0, 12.0, 's'), (2.3, -10.0, 'O'), (3.2, -10.0, 's'),
            (3.6, 0.0, 'O'), (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-2.0 * b(t), eranim.eased_value(scan, t), 0.0), 40)
    c.fn('halo', 'rot', lambda t: (2.0 * math.sin(2 * math.pi * t / T), 0.0, 12.0 * math.sin(2 * math.pi * t / T)), 18)
    c.fn('halo', 'pos', lambda t: (0.0, 0.6 * math.sin(2 * math.pi * t / T * 2), 0.0), 18)
    c.fn('halo2', 'rot', lambda t: (0.0, 0.0, -10.0 * math.sin(2 * math.pi * t / T)), 18)
    c.fn('core', 'scale', lambda t: (1.0 + 0.1 * b(2 * t), 1.0 + 0.1 * b(2 * t), 1.0), 18)
    c.fn('right_arm', 'rot', lambda t: (1.0 * math.sin(2 * math.pi * t / T), 0.0, 1.2 * b(t)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.0 * math.sin(2 * math.pi * t / T), 0.0, -1.2 * b(t)), 12)
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        for k in range(5):
            c.fn('feather_%s%d' % (side, k + 1), 'rot',
                 lambda t, k=k, s=s: (0.0, 0.0, 3.0 * math.sin(2 * math.pi * t / T - 0.25 * k) * -s), 18)
    for side in ('right', 'left'):
        c.fn('%s_leg' % side, 'rot', lambda t: (2.0 * math.sin(2 * math.pi * t / T + 0.6), 0.0, 0.0), 12)
        c.fn('%s_shin' % side, 'rot', lambda t: (2.5 * math.sin(2 * math.pi * t / T + 0.2), 0.0, 0.0), 12)
    return c


def walk(x):
    T = 2.2
    c = Clip('walk', T, loop=True)
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.5 * s1(t), 0.4 + 0.5 * math.sin(4 * math.pi * t / T), 0.0), 18)
    c.fn('hips', 'rot', lambda t: (8.0, 3.0 * s1(t), 2.0 * s1(t + 0.25)), 18)
    c.fn('chest', 'rot', lambda t: (2.0, -2.0 * s1(t), -1.0 * s1(t)), 18)
    c.fn('head', 'rot', lambda t: (-8.0, -1.0 * s1(t), 0.0), 8)
    c.fn('right_arm', 'rot', lambda t: (10.0 + 3.0 * s1(t), 0.0, 4.0), 18)
    c.fn('left_arm', 'rot', lambda t: (10.0 - 3.0 * s1(t), 0.0, -4.0), 18)
    for side in ('right', 'left'):
        c.fn('%s_leg' % side, 'rot', lambda t: (14.0 + 2.0 * s1(t), 0.0, 0.0), 12)
        c.fn('%s_shin' % side, 'rot', lambda t: (10.0, 0.0, 0.0), 4)
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        for k in range(5):
            c.fn('feather_%s%d' % (side, k + 1), 'rot',
                 lambda t, k=k, s=s: (0.0, 12.0 * -s, (4.0 * math.sin(2 * math.pi * t / T - 0.3 * k) - 4.0) * -s), 18)
    return c


def melee_slam(x):
    """Gravity press: it rises with the claws joined overhead and the wings raised, then dives and drives both claws
    into the floor before it (impact), the knees folding as it lands."""
    A, B, Cc, I, F, R, E = 0.2, 0.75, 0.92, 1.1, 1.38, 1.85, 2.4
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.4), (B, 0, 5.0, 1.6), (Cc, 0, 5.4, 1.8, 'o'), (I, 0, -4.8, -3.0, 'I'),
                   (F, 0, -5.0, -3.2, 'O'), (R, 0, -2.6, -1.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -6, 0, 0), (I, 16, 0, 0, 'I'), (F, 18, 0, 0, 'O'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -16, 0, 0), (Cc, -18, 0, 0, 'o'), (I, 24, 0, 0, 'I'), (F, 26, 0, 0, 'O'),
                    (R, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -20, 0, 0, 'I'), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 10, 0, 4 * sz), (B, -168, 0, -14 * sz), (Cc, -172, 0, -16 * sz, 'o'),
                                (I, -62, 0, -6 * sz, 'I'), (F, -58, 0, -6 * sz, 'O'), (R, -36, 0, -3 * sz),
                                (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -20, 0, 0), (I, 4, 0, 0, 'I'), (E, 0, 0, 0)])
        claws(c, side, [(0, 0, 's'), (B, 20, 's'), (I, 30, 'I'), (F, 26, 'b'), (R, 8, 's'), (E, 0, 's')])
        c.rot('%s_leg' % side, [(0, 0, 0, 0), (B, 20, 0, 0), (I, -46, 0, 0, 'I'), (F, -48, 0, 0), (R, -20, 0, 0),
                                (E, 0, 0, 0)])
        c.rot('%s_shin' % side, [(0, 0, 0, 0), (B, 30, 0, 0), (I, 70, 0, 0, 'I'), (F, 72, 0, 0), (R, 30, 0, 0),
                                 (E, 0, 0, 0)])
    feathers(c, [(0, 0, 's'), (B, 1.0, 's'), (I, -0.6, 'I'), (F, -0.5, 'O'), (R, 0.2, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (B, 1.0, 's'), (I, -0.6, 'I'), (F, -0.5, 'O'), (R, 0.2, 's'), (E, 0, 's')], 'l')
    for t in (I, F):
        for side, tip in (('right', TIP_R), ('left', TIP_L)):
            eranim.reach_y(c, x.rig, x.v, t, side + '_arm', 'x', side + '_f2b', tip, 24.2, -120.0, 0.0)
    eranim.unsink(c, x.rig, x.v, 0.9, 1.9, skip=DANGLE)
    return c


def melee_sweep(x):
    """Sweeping laser: the body turns far to the right with the right claw levelled, the core blazing, and sweeps
    the beam across the front (impact at the centre) to the left."""
    A, B, I, F, R, E = 0.2, 0.7, 1.0, 1.3, 1.7, 2.25
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.8, 0.8), (I, 0, 0.2, -1.2, 'I'), (F, 0, 0.2, -1.4, 'O'), (R, 0, 0.4, -0.6),
                   (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 30, 0), (I, 0, -6, 0, 'I'), (F, 0, -36, 0, 'O'), (R, 0, -18, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 4, 20, -4), (I, 6, -6, 2, 'I'), (F, 6, -26, 4, 'O'), (R, 2, -12, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -10, 0), (I, 0, 0, 0, 'I'), (F, 0, 6, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -86, 30, 10), (I, -90, 0, 6, 'I'), (F, -88, -30, 4, 'O'),
                        (R, -50, -16, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -6, 0, 0), (E, 0, 0, 0)])
    claws(c, 'right', [(0, 0, 's'), (B, 30, 's'), (I, 34, 's'), (R, 10, 's'), (E, 0, 's')])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -30, 0, -40), (I, -20, 0, -50), (F, -10, 0, -56), (R, 0, 0, -20),
                       (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.5, 1.5, 1.2), (I, 1.6, 1.6, 1.3, 'O'), (F, 1.5, 1.5, 1.2), (R, 1.1, 1.1, 1.0),
                   (E, 1, 1, 1)])
    feathers(c, [(0, 0, 's'), (B, 0.6, 's'), (F, 0.6, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (B, 0.6, 's'), (F, 0.6, 's'), (E, 0, 's')], 'l')
    return c


def cast(x):
    """Missiles: the wings flare up and open, the claws spread, the core swells and fires (release at impact)."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.1, 1.5, 2.05
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 1.6, 0.8), (I, 0, 1.0, 1.6, 'O'), (F, 0, 1.0, 1.4), (R, 0, 0.6, 0.6),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -12, 0, 0), (I, -4, 0, 0, 'I'), (F, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -10, 0, 0), (I, -4, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (B, -60, 0, 40 * sz), (I, -50, 0, 50 * sz, 'O'), (F, -50, 0, 48 * sz),
                                (R, -20, 0, 16 * sz), (E, 0, 0, 0)])
        claws(c, side, [(0, 0, 's'), (B, -20, 's'), (I, -30, 'O'), (R, -10, 's'), (E, 0, 's')])
    c.scl('core', [(0, 1, 1, 1), (B, 1.5, 1.5, 1.2), (I, 0.9, 0.9, 1.0, 'I'), (F, 1.2, 1.2, 1.1, 'b'), (E, 1, 1, 1)])
    feathers(c, [(0, 0, 's'), (B, 1.0, 's'), (I, 1.2, 'O'), (F, 1.1, 's'), (R, 0.4, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (B, 1.0, 's'), (I, 1.2, 'O'), (F, 1.1, 's'), (R, 0.4, 's'), (E, 0, 's')], 'l')
    c.muzzle_part, c.muzzle_point = 'chest', CORE
    return c


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(8.0, 1.0, 1), shake(9.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, 1.6 + 0.3 * math.sin(2 * math.pi * t / T), 0.4), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.6 * tr(t), 0.0, 0.8 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-14.0 + 0.8 * tr2(t), 0.0, 0.8 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-30.0 + 1.5 * tr(t), 0.0, 60.0 + 2.0 * tr2(t)), 14)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 1.5 * tr2(t), 0.0, -60.0 - 2.0 * tr(t)), 14)
    c.fn('core', 'scale', lambda t: (1.3 + 0.12 * tr(t), 1.3 + 0.12 * tr(t), 1.1), 14)
    c.fn('halo', 'scale', lambda t: (1.12 + 0.03 * tr2(t), 1.12 + 0.03 * tr2(t), 1.0), 14)
    for side in ('r', 'l'):
        s = -1 if side == 'r' else 1
        for k in range(5):
            c.fn('feather_%s%d' % (side, k + 1), 'rot',
                 lambda t, k=k, s=s: (0.0, 10.0 * s, ((2 - k) * 9.0 + 12.0 + 1.5 * tr(t + 0.05 * k)) * -s), 14)
    return c


def charge(x):
    """The laser line: it braces with the wings swept forward, the core charging, and fires straight ahead
    (impact), the recoil pushing it back."""
    A, B, I, F, R, E = 0.15, 0.7, 1.0, 1.3, 1.6, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, -0.6, -1.0), (I, 0, 0.4, 3.0, 'O'), (F, 0, 0.4, 2.6), (R, 0, 0.2, 1.2),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 14, 0, 0), (I, -10, 0, 0, 'O'), (F, -8, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 4, 0, 0, 'O'), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (B, -40, 0, -10 * sz), (I, -30, 0, 30 * sz, 'O'), (F, -28, 0, 28 * sz),
                                (R, -10, 0, 10 * sz), (E, 0, 0, 0)])
        claws(c, side, [(0, 0, 's'), (B, 30, 's'), (I, -20, 'O'), (R, 0, 's'), (E, 0, 's')])
    c.scl('core', [(0, 1, 1, 1), (B, 1.7, 1.7, 1.4), (I, 1.0, 1.0, 1.0, 'I'), (F, 1.2, 1.2, 1.1), (E, 1, 1, 1)])
    feathers(c, [(0, 0, 's'), (B, -0.8, 's'), (I, 0.8, 'O'), (F, 0.6, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (B, -0.8, 's'), (I, 0.8, 'O'), (F, 0.6, 's'), (E, 0, 's')], 'l')
    return c


def summon(x):
    """The arms and wings raised wide, the halo flaring and lifting as the drones answer (impact)."""
    A, B, I, F, R, E = 0.2, 0.9, 1.3, 1.6, 2.0, 2.6
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 2.0, 0), (I, 0, 3.2, 0, 'O'), (F, 0, 3.0, 0), (R, 0, 1.4, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -10, 0, 0), (I, -16, 0, 0, 'O'), (F, -14, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -24, 0, 0, 'O'), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (B, -120, 0, 30 * sz), (I, -150, 0, 40 * sz, 'O'), (F, -148, 0, 40 * sz),
                                (R, -60, 0, 16 * sz), (E, 0, 0, 0)])
        claws(c, side, [(0, 0, 's'), (B, -30, 's'), (I, -40, 'O'), (R, -10, 's'), (E, 0, 's')])
    c.scl('halo', [(0, 1, 1, 1), (B, 1.1, 1.1, 1.0), (I, 1.35, 1.35, 1.0, 'O'), (F, 1.3, 1.3, 1.0), (R, 1.1, 1.1, 1.0),
                   (E, 1, 1, 1)])
    c.pos('halo', [(0, 0, 0, 0), (I, 0, 4, 0, 'O'), (F, 0, 3.6, 0), (E, 0, 0, 0)])
    feathers(c, [(0, 0, 's'), (B, 1.2, 's'), (I, 1.5, 'O'), (F, 1.4, 's'), (R, 0.4, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (B, 1.2, 's'), (I, 1.5, 'O'), (F, 1.4, 's'), (R, 0.4, 's'), (E, 0, 's')], 'l')
    return c


def phase_change(x):
    """Reboot: the power fails (wings and arms droop, the head sinks, the halo tilts), then everything snaps back
    at once (impact): the wings burst open, the halo widens, the core blazes."""
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(10.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.55, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.5, 0, -1.6, 0, 's'), (1.4, 0, -1.8, 0), (1.75, 0, 3.0, 0, 'O'), (2.55, 0, 2.6, 0),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.5, 22, 0, 6, 's'), (1.4, 24, 0, 6), (1.75, -16, 0, 0, 'O'), (2.55, -12, 0, 0),
                    (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.0 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.5, 30, 0, 10), (1.4, 32, 0, 12), (1.75, -20, 0, 0, 'O'), (2.55, -16, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('halo', [(0, 0, 0, 0), (0.5, 20, 0, 18), (1.4, 22, 0, 20), (1.75, -6, 0, -40, 'O'), (2.55, 0, 0, -30),
                   (E, 0, 0, 0)])
    c.scl('halo', [(0, 1, 1, 1), (1.4, 0.9, 0.9, 1.0), (1.75, 1.4, 1.4, 1.0, 'O'), (2.55, 1.25, 1.25, 1.0),
                   (E, 1, 1, 1)])
    c.scl('core', [(0, 1, 1, 1), (0.5, 0.6, 0.6, 0.8), (1.4, 0.6, 0.6, 0.8), (1.75, 1.8, 1.8, 1.4, 'O'),
                   (2.55, 1.4, 1.4, 1.2), (E, 1, 1, 1)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (0.5, 10, 0, -4 * sz), (1.4, 12, 0, -4 * sz),
                                (1.75, -60, 0, 80 * sz, 'O'), (2.55, -56, 0, 74 * sz), (E, 0, 0, 0)])
        claws(c, side, [(0, 0, 's'), (0.5, 30, 's'), (1.4, 30, 's'), (1.75, -40, 'O'), (2.55, -30, 's'), (E, 0, 's')])
    feathers(c, [(0, 0, 's'), (0.5, -1.0, 's'), (1.4, -1.0, 's'), (1.75, 1.6, 'O'), (2.55, 1.4, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (0.5, -1.0, 's'), (1.4, -1.0, 's'), (1.75, 1.6, 'O'), (2.55, 1.4, 's'), (E, 0, 's')], 'l')
    return c


def stagger(x):
    E = 1.5
    c = Clip('stagger', E, impact=0.15)
    tr = shake(9.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, 0.8, 3.0, 'O'), (0.45, 0, 0.4, 3.4), (0.8, 0, 0.2, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.14, -18, 8, 8, 'O'), (0.45, -6, -4, 2), (0.75, 4, 2, -2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -20, 16, 10, 'O'), (0.45, -2, -8, 0), (0.75, 6, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.14, -40, 0, 40, 'O'), (0.45, -16, 0, 16), (0.85, -4, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.14, -40, 0, -46, 'O'), (0.45, -16, 0, -20), (0.85, -4, 0, -4), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.16, 0.7, 0.15)), 30)
    c.add('halo', 'rot', lambda t: (3.0 * tr(t + 0.1) * envelope(t, 0.16, 0.8, 0.15), 0.0, 0.0), 30)
    feathers(c, [(0, 0, 's'), (0.14, -0.8, 'O'), (0.6, 0.2, 's'), (E, 0, 's')], 'r')
    feathers(c, [(0, 0, 's'), (0.14, -0.8, 'O'), (0.6, 0.2, 's'), (E, 0, 's')], 'l')
    return c


def death(x):
    """Shutdown: the core dims, the halo slips and drops ringing to the floor, the wings fall, the god sinks out of
    the air onto its knees and topples forward on its claws (impact)."""
    E, I = 4.0, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(10.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.5, 0, 1.6, 1.0, 'O'), (1.2, 0, -9.0, 0.8, 'I'), (1.32, 0, -8.6, 0.8, 'O'),
                   (E, 0, -8.6, 0.8)])
    c.rot('hips', [(0, 0, 0, 0), (0.5, -8, 0, 0, 'O'), (1.2, 6, 0, 0), (1.4, 8, 0, 0), (I, 30, 0, 0, 'I'),
                   (2.0, 26, 0, 0, 'b'), (E, 28, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.5, -16, 0, 4, 'O'), (1.2, 16, 0, -4), (1.4, 18, 0, -2), (I, 44, 0, 0, 'I'),
                    (2.0, 40, 0, 0, 'b'), (E, 42, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.2, 1.1, 0.15)), 40)
    c.rot('head', [(0, 0, 0, 0), (0.5, -20, 0, 0, 'O'), (1.2, 26, 0, 0), (I, 6, -20, 0, 'I'), (E, 6, -22, 0)])
    c.scl('core', [(0, 1, 1, 1), (0.5, 1.4, 1.4, 1.2, 'O'), (1.2, 0.5, 0.5, 0.8), (E, 0.4, 0.4, 0.8)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (0.5, -30, 0, 34 * sz, 'O'), (1.2, 4, 0, 10 * sz),
                                (I, -80, 0, 20 * sz, 'I'), (2.0, -74, 0, 24 * sz, 'b'), (E, -76, 0, 22 * sz)])
        claws(c, side, [(0, 0, 's'), (1.2, 20, 's'), (I, -10, 'I'), (E, 30, 's')])
        c.rot('%s_leg' % side, [(0, 0, 0, 0), (1.2, -60, 0, 0, 'I'), (E, -60, 0, 0)])
        c.rot('%s_shin' % side, [(0, 0, 0, 0), (1.2, 96, 0, 0, 'I'), (E, 96, 0, 0)])
    feathers(c, [(0, 0, 's'), (0.5, 0.8, 'O'), (1.2, -1.0, 's'), (I, -1.4, 'I'), (E, -1.4, 's')], 'r')
    feathers(c, [(0, 0, 's'), (0.5, 0.8, 'O'), (1.2, -1.0, 's'), (I, -1.4, 'I'), (E, -1.4, 's')], 'l')
    c.rot('halo', [(0, 0, 0, 0), (0.6, 10, 0, 20), (1.1, 40, 0, 60, 'I'), (1.6, 86, 0, 80, 'I'), (1.8, 80, 0, 76, 'b'),
                   (E, 82, 0, 78)])
    c.pos('halo', [(0, 0, 0, 0), (0.6, 0, -2, 0), (1.6, 0, -40, -6, 'I'), (1.8, 0, -38, -6, 'b'), (E, 0, -39, -6)])
    for t in (1.2, 1.32, 1.4, I, 2.0, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE + ('halo',))
    eranim.rest_on_floor(c, x.rig, x.v, (1.6, 1.8, E), 'halo', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0])
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

WHITE = rgb('#eef0f3')
GOLD = rgb('#e3b44c')
DARK = rgb('#3a3f48')
CYAN = (88, 230, 255)


def marble(fc, seed):
    """White stone panels: faint seams in a staggered grid and soft veins."""
    for y in range(0, fc.fh, 6):
        for x in range(fc.fw):
            fc.fpx(x, y, shade(fc.fget(x, y), 0.86))
    for row in range(fc.fh // 6 + 1):
        x = (row % 2) * 4 + int(h(seed, row) * 3)
        while x < fc.fw:
            for y in range(row * 6, min(fc.fh, row * 6 + 6)):
                fc.fpx(x, y, shade(fc.fget(x, y), 0.88))
            x += 8 + int(h(seed, row, x) * 4)


def cyan_line(fc, gfc, x0, y0, x1, y1, col):
    from boss_kit import line
    line(fc, x0, y0, x1, y1, col, gfc, lighten(col, 0.3))


def paint_body(p, glow, form):
    for part, tag in (('chest', 'chest'), ('right_arm', 'paul'), ('right_arm', 'paul2'), ('right_forearm', 'farm'),
                      ('right_leg', 'thigh'), ('right_shin', 'shin'), ('right_foot', 'foot'), ('head', 'head'),
                      ('skirt_fr', 'pf'), ('skirt_sr', 'ps'), ('skirt_br', 'pb')):
        for side in ('front', 'right', 'back', 'left', 'top'):
            marble(p.face(part, side, tag=tag), (part, side))
    for part, tag in (('skirt_fr', 'pf'), ('skirt_br', 'pb')):
        for side in ('front', 'back'):
            f = p.face(part, side, tag=tag)
            g = p.face(part, side, tag=tag, glow=True)
            trim(f, GOLD, cols=(0, -1), rows=(-1,), shine=True)
            cyan_line(f, g, f.fw // 2, 2, f.fw // 2, f.fh - 4, glow)
    for side in ('right', 'left'):
        f = p.face('skirt_sr', side, tag='ps')
        g = p.face('skirt_sr', side, tag='ps', glow=True)
        trim(f, GOLD, cols=(0, -1), rows=(-1,), shine=True)
        cyan_line(f, g, f.fw // 2, 2, f.fw // 2, f.fh - 4, glow)
    cf = p.face('skirt_c', 'front')
    gc = p.face('skirt_c', 'front', glow=True)
    for y in range(cf.fh):
        cf.fpx(2, y, glow)
        cf.fpx(3, y, glow)
        gc.fpx(2, y, lighten(glow, 0.3))
        gc.fpx(3, y, lighten(glow, 0.3))
    ch = p.face('chest', 'front', tag='chest')
    gch = p.face('chest', 'front', tag='chest', glow=True)
    for (x0, y0, x1, y1) in ((4, 26, 4, 8), (31, 26, 31, 8), (8, 4, 14, 4), (21, 4, 27, 4)):
        cyan_line(ch, gch, x0, y0, x1, y1, glow)
    for side in ('front', 'right', 'back', 'left'):
        pa = p.face('right_arm', side, tag='paul')
        gpa = p.face('right_arm', side, tag='paul', glow=True)
        trim(pa, GOLD, rows=(-1, -2), shine=True)
        cyan_line(pa, gpa, 3, pa.fh - 5, pa.fw - 4, pa.fh - 5, glow)
        fa = p.face('right_forearm', side, tag='farm')
        gfa = p.face('right_forearm', side, tag='farm', glow=True)
        cyan_line(fa, gfa, fa.fw // 2, 9, fa.fw // 2, fa.fh - 3, glow)
        th = p.face('right_leg', side, tag='thigh')
        gth = p.face('right_leg', side, tag='thigh', glow=True)
        cyan_line(th, gth, th.fw // 2, 2, th.fw // 2, th.fh - 7, glow)
    fr = p.face('chest', 'front', index=1)
    for y in range(fr.fh):
        for x in range(fr.fw):
            if 3 <= x < fr.fw - 3 and 3 <= y < fr.fh - 3:
                fr.fpx(x, y, shade(GOLD, 0.7))
    trim(fr, lighten(GOLD, 0.3), rows=(0, -1), cols=(0, -1))
    v = p.face('head', 'front', index=1)
    gv = p.face('head', 'front', index=1, glow=True)
    for x in range(v.fw):
        for y in range(v.fh):
            v.fpx(x, y, lighten(glow, 0.2))
            gv.fpx(x, y, lighten(glow, 0.4))
    hf = p.face('head', 'front', tag='head')
    ghf = p.face('head', 'front', tag='head', glow=True)
    cyan_line(hf, ghf, hf.fw // 2, 9, hf.fw // 2, hf.fh - 2, glow)


def paint_wings(p, glow, form):
    for k in range(5):
        for side in ('front', 'back', 'top', 'bottom'):
            f = p.face('feather_r%d' % (k + 1), side, index=1)
            g = p.face('feather_r%d' % (k + 1), side, index=1, glow=True)
            marble(f, ('feather', k, side))
            if side in ('front', 'back', 'top'):
                cyan_line(f, g, 3, f.fh // 2, f.fw - 3, f.fh // 2, glow)
                if form >= 1:
                    cyan_line(f, g, 3, f.fh // 2 - 1, f.fw - 6, f.fh // 2 - 1, glow)


def paint_mech(p, glow, form):
    for part, tag in (('hips', None), ('torso', None), ('chest', None)):
        pass
    for k in range(4):
        for side in ('front', 'right', 'back', 'left'):
            j = p.face('right_f%d' % (k + 1), side, index=1, glow=True)
            for x in range(j.fw):
                j.fpx(x, 0, shade(glow, 0.6))
    for idx in (1, 2):
        for side in ('front', 'right', 'back', 'left'):
            rivets(p.face('torso', side, index=idx), DARK, 4, inset=1, rows=(0,))
    gm = p.face('hips', 'front', index=2)
    diamond(gm, gm.fw // 2, gm.fh // 2, 2, glow, p.face('hips', 'front', index=2, glow=True), lighten(glow, 0.4))
    for name, tag in (('halo', 'hl'), ('halo2', 'h2')):
        for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
            g = p.face(name + '_0', side, tag=tag + '_g', glow=True)
            f = p.face(name + '_0', side, tag=tag + '_g')
            diamond(f, f.fw // 2, f.fh // 2, 1, glow, g, lighten(glow, 0.4))


def variants():
    stone = {'base': '#eef0f3', 'style': 'stone', 'cracks': False}
    gold = {'base': '#e3b44c', 'style': 'gold'}
    mech = {'base': '#3a3f48', 'style': 'mech'}
    pal = {
        'head': stone, 'chest': stone, 'pauldron': stone, 'pauldron2': dict(stone, base='#e4e7eb'), 'farm': stone,
        'thigh': stone, 'shin': stone, 'foot': dict(stone, base='#e4e7eb'), 'plate': stone,
        'plate_side': dict(stone, base='#e8ebef'), 'plate_back': dict(stone, base='#e4e7eb'),
        'blade0': stone, 'blade1': dict(stone, base='#f4f5f7'), 'blade2': dict(stone, base='#e8ebee'),
        'crest': gold, 'fin': gold, 'chin': gold, 'circlet': gold, 'frame': gold, 'collar': gold, 'rib': gold,
        'rib2': gold, 'belt': gold, 'prim': gold, 'elbow': gold, 'band': gold, 'band2': gold, 'knuckle': gold,
        'joint': gold, 'knee': gold, 'toe': gold, 'anklet': gold, 'froot': gold, 'ftip': gold, 'center': gold,
        'halo_seg': gold, 'halo_spike': gold, 'halo2_seg': dict(gold, base='#f0c860'), 'halo2_spike': gold,
        'halo_gem': {'base': '#58e6ff', 'style': 'gem'}, 'halo2_gem': {'base': '#58e6ff', 'style': 'gem'},
        'belt_gem': {'base': '#58e6ff', 'style': 'gem'},
        'backpack': mech, 'uarm': mech, 'palm': mech, 'claw': dict(mech, base='#30343c'),
        'claw_tip': dict(mech, base='#2a2e35'), 'abdomen': mech, 'pelvis': mech,
        'visor': {'base': '#58e6ff', 'style': 'glowcore'}, 'core': {'base': '#58e6ff', 'style': 'glowcore'},
    }

    def deco(p, form=0):
        glow = CYAN if form < 2 else ((150, 240, 255) if form == 2 else (255, 120, 90))
        paint_body(p, glow, form)
        paint_wings(p, glow, form)
        paint_mech(p, glow, form)
        if form >= 1:
            core_f = p.face('core', 'front')
            gcf = p.face('core', 'front', glow=True)
            for y in range(core_f.fh):
                for x in range(core_f.fw):
                    gcf.fpx(x, y, lighten(glow, 0.2 + 0.1 * form))
        from boss_forms import glow_faces
        glow_faces(p, 'core', lighten(glow, 0.3))
    overload = dict(pal, core={'base': '#ff6a50', 'style': 'glowcore'}, visor={'base': '#ff6a50', 'style': 'glowcore'},
                    halo_gem={'base': '#ff6a50', 'style': 'gem'}, halo2_gem={'base': '#ff6a50', 'style': 'gem'},
                    belt_gem={'base': '#ff6a50', 'style': 'gem'})
    lift = {'base': dict(pos=(0, HOVER, 0))}
    wide = dict(lift, wing_r=dict(scale=(1.15, 1.15, 1.0)), wing_l=dict(scale=(1.15, 1.15, 1.0)))
    return [Variant('b7', palette=pal, poses=lift, paint=deco, glow=True, extra=dict(scale=2.9, hitbox=(5.4, 9.8))),
            Variant('b7_p1', palette=pal, poses=lift, paint=lambda p: deco(p, 1), glow=True, extra=dict(scale=2.9)),
            Variant('b7_p2', show={'halo2'}, palette=pal, poses=wide, paint=lambda p: deco(p, 2), glow=True,
                    extra=dict(scale=2.9)),
            Variant('b7_p3', show={'halo2'}, palette=overload, poses=dict(wide, halo2=dict(scale=(1.1, 1.1, 1.0))),
                    paint=lambda p: deco(p, 3), glow=True, extra=dict(scale=2.9))]


TRAILS = [('right_hand', KNUCKLE_R, (-12.5, 18.0, -2.5)), ('left_hand', (12.5, 9.0, -2.5), (12.5, 18.0, -2.5))]


def rig():
    vs = variants()
    base = Rig('skygod', 'SkygodBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('skygod', 'SkygodBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.6
    return finish_rig(r, 0.0, TRAILS)
