"""DRAGON boss rig: b6 灼炎の竜王. Four legs (three segments each, feet planted by IK in a lateral-sequence walk),
folded membrane wings with a hinged tip, two-part neck, horned head with an opening jaw, a four-segment tail that
whips after every move of the body, spines along the back, glowing chest. Drawn at 2.7x: about 18 blocks from snout
to tail tip and 13 from wing tip to wing tip."""
import math

import numpy as np

import eranim
import ermodel
from boss_forms import cracks, glow_bright, glow_faces, veins
from boss_motion import Ctx, build, finish_rig
from eranim import Chain, Clip, Gait, Leg, envelope, shake
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
SNOUT = (0.0, -2.0, -47.0)            # tip of the snout (rest coordinates of head)
TAIL_TIP = (0.0, -1.0, 62.0)
CLAW_FR = (-7.0, 24.0, -11.0)


def leg(name, x, front, mirror):
    kw = (lambda t: dict(share=t, mirror=True)) if mirror else (lambda t: dict(tag=t))
    if front:
        z = -6
        upper = C(x - 2.5, 2, z - 3, 5, 10, 6, 'scale:leg', **kw('fu'))
        shin = C(x - 2.5, 12, z - 2, 5, 9, 5, 'scale:shin', **kw('fs'))
        foot = C(x - 3, 21, z - 5, 6, 3, 8, 'scale:foot', **kw('ff'))
        return P(name, (x, 3, z), upper, P(name + '_shin', (x, 12, z), shin, P(name + '_foot', (x, 21, z), foot)))
    z = 12
    upper = C(x - 3, 0, z - 4, 6, 12, 9, 'scale:thigh', **kw('hu'))
    shin = C(x - 2.5, 12, z - 1, 5, 9, 5, 'scale:shin2', **kw('hs'))
    foot = C(x - 3, 21, z - 3, 6, 3, 8, 'scale:foot', **kw('hf'))
    return P(name, (x, 2, z), upper, P(name + '_shin', (x, 12, z + 1), shin, P(name + '_foot', (x, 21, z + 1), foot)))


def wing(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    bx = -27 if s < 0 else 7
    tx = -45 if s < 0 else 27
    tip = P('%s_wing_tip' % name, (27 * s, -5, -4), C(tx, -6, -5, 18, 2, 2, 'scale:wingbone', **kw('tipbone')),
            C(tx, -4, -4, 18, 0, 14, 'membrane:membrane', **kw('tipmem')), rot=(0, 0, -50 if s < 0 else 50))
    return P('%s_wing' % name, (7 * s, -5, -4), C(bx, -6, -5, 20, 2, 2, 'scale:wingbone', **kw('bone')),
             C(bx, -4, -4, 20, 0, 18, 'membrane:membrane', **kw('mem')), tip,
             rot=(0, 30 if s < 0 else -30, 35 if s < 0 else -35))


def geometry():
    head = P('head', (0, -2, -27),
             C(-5, -7, -38, 10, 8, 11, 'scale:head'), C(-3.5, -4, -47, 7, 5, 9, 'scale:snout'),
             C(-1, -10, -36, 2, 3, 8, 'bone:crest'),
             P('horn_r', (-4, -6, -30), C(-5, -8, -30, 2, 2, 10, 'bone:horn', tag='horn'), rot=(22, -15, 0)),
             P('horn_l', (4, -6, -30), C(3, -8, -30, 2, 2, 10, 'bone:horn', share='horn', mirror=True), rot=(22, 15, 0)),
             P('jaw', (0, 1, -30), C(-3.5, 1, -46, 7, 2, 16, 'scale:jaw')),
             rot=(45, 0, 0))
    neck = P('neck1', (0, -2, -10), C(-4.5, -6.5, -19, 9, 9, 9, 'scale:neck'),
             P('neck2', (0, -2, -19), C(-4, -6, -27, 8, 8, 8, 'scale:neck2'), head, rot=(-15, 0, 0)), rot=(-30, 0, 0))
    tail = P('tail1', (0, -1, 16), C(-5, -5, 16, 10, 8, 12, 'scale:tail'), C(-1, -8, 18, 2, 3, 3, 'bone:spine', tag='tsp'),
             P('tail2', (0, -1, 28), C(-4, -4, 28, 8, 6, 12, 'scale:tail2'), C(-1, -6.5, 30, 2, 3, 3, 'bone:spine', share='tsp'),
               P('tail3', (0, -1, 40), C(-3, -3, 40, 6, 4, 12, 'scale:tail3'),
                 P('tail_tip', (0, -1, 52), C(-2, -2, 52, 4, 3, 10, 'scale:tail4'), C(-5, -1, 56, 10, 0, 8, 'membrane:fin')),
                 rot=(-4, 0, 0)), rot=(-6, 0, 0)), rot=(-8, 0, 0))
    body = P('body', (0, 4, 4), C(-8, -6, -10, 16, 13, 26, 'scale:body'),
             C(-1, -10, -8, 2, 4, 3, 'bone:spine', tag='sp'), C(-1, -10, -2, 2, 4, 3, 'bone:spine', share='sp'),
             C(-1, -10, 4, 2, 4, 3, 'bone:spine', share='sp'), C(-1, -9, 10, 2, 3, 3, 'bone:spine'),
             neck, tail, wing('r'), wing('l'),
             leg('front_right_leg', -7, True, False), leg('front_left_leg', 7, True, True),
             leg('hind_right_leg', -7, False, False), leg('hind_left_leg', 7, False, True))
    return [P('base', (0, 24, 0), body)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['tail1', 'tail2', 'tail3', 'tail_tip'], rest='back', axes='xy', hang=0.1, freq=1.05, damp=0.32,
          inertia=7.0, falloff=1.12, limits={'x': (-40.0, 40.0), 'y': (-70.0, 70.0)}),
    Chain(['right_wing_tip'], rest='side', axes='z', hang=0.0, freq=2.1, damp=0.3, inertia=8.0,
          limits={'z': (-60.0, 60.0)}),
    Chain(['left_wing_tip'], rest='side', axes='z', hang=0.0, freq=2.2, damp=0.3, inertia=8.0,
          limits={'z': (-60.0, 60.0)}),
]
GAIT = Gait(1.8, stride=14.0, duty=0.7, lift=3.4, heel=8.0, toe=14.0)


class DragonCtx(Ctx):
    def __init__(self, rig, variant):
        super().__init__(rig, variant)
        self.legs = {
            'FR': Leg(rig, 'front_right_leg', 'front_right_leg_shin', 'front_right_leg_foot', knee='back'),
            'FL': Leg(rig, 'front_left_leg', 'front_left_leg_shin', 'front_left_leg_foot', knee='back'),
            'HR': Leg(rig, 'hind_right_leg', 'hind_right_leg_shin', 'hind_right_leg_foot'),
            'HL': Leg(rig, 'hind_left_leg', 'hind_left_leg_shin', 'hind_left_leg_foot'),
        }


def legs4(c, x, plans=None, holds=None, samples=None, skip=()):
    """All four legs by IK, each foot following its step plan (planted otherwise)."""
    plans = plans or {}
    holds = holds or {}
    for key, leg_def in x.legs.items():
        if key in skip:
            continue
        plan = list(plans.get(key, ()))
        eranim.plant(c, x.rig, x.v, leg_def, eranim.steps(leg_def, plan),
                     eranim.step_pitch(plan, holds=list(holds.get(key, ()))), samples=samples)


def idle(x):
    T = 3.4
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    c.fn('body', 'scale', lambda t: (1.0 + 0.03 * b(t), 1.0 + 0.045 * b(t), 1.0 + 0.008 * b(t)), 16)
    c.fn('body', 'rot', lambda t: (-1.2 * b(t), 1.0 * math.sin(2 * math.pi * t / T), 0.8 * math.sin(2 * math.pi * t / T + 1)), 16)
    c.fn('base', 'pos', lambda t: (0.0, 0.4 * b(t) - 0.2, 0.0), 16)
    c.fn('neck1', 'rot', lambda t: (-3.0 * b(t), 6.0 * math.sin(2 * math.pi * t / T + 0.4), 0.0), 16)
    c.fn('neck2', 'rot', lambda t: (3.0 * b(t - 0.3), 4.0 * math.sin(2 * math.pi * t / T + 0.1), 0.0), 16)
    look = [(0.0, 0.0, 's'), (0.6, 0.0, 's'), (1.1, -14.0, 's'), (1.8, -14.0, 's'), (2.4, 10.0, 's'), (2.9, 10.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (2.0 * math.sin(2 * math.pi * t / T + 1.2), eranim.eased_value(look, t), 0.0), 34)
    snort = [(0.0, 3.0, 's'), (1.5, 3.0, 's'), (1.7, 12.0, 'O'), (2.0, 4.0, 's'), (T, 3.0, 's')]
    c.fn('jaw', 'rot', lambda t: (eranim.eased_value(snort, t), 0.0, 0.0), 34)
    c.fn('tail1', 'rot', lambda t: (0.0, 8.0 * math.sin(2 * math.pi * t / T), 0.0), 16)
    c.fn('right_wing', 'rot', lambda t: (0.0, -2.0 * b(t), 3.0 * b(t)), 12)
    c.fn('left_wing', 'rot', lambda t: (0.0, 2.0 * b(t), -3.0 * b(t)), 12)
    legs4(c, x, samples=16)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    c.fn('base', 'pos', lambda t: (0.0, -0.3 - 0.6 * w(t, 2, 0.1), 0.0), 24)
    c.fn('body', 'rot', lambda t: (1.0 * w(t, 2, 0.15), 4.0 * w(t, 1, 0.1), 3.0 * math.sin(2 * math.pi * t / T)), 24)
    c.fn('neck1', 'rot', lambda t: (3.0 + 1.5 * w(t, 2, 0.2), -5.0 * w(t, 1, 0.15), 0.0), 24)
    c.fn('neck2', 'rot', lambda t: (-1.0 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.2), 0.0), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.5 * w(t, 2, 0.3), 3.0 * w(t, 1, 0.2), 0.0), 24)
    c.fn('tail1', 'rot', lambda t: (0.0, -8.0 * w(t, 1, 0.3), 0.0), 24)
    c.fn('right_wing', 'rot', lambda t: (0.0, -2.0 * w(t, 2, 0.1), 3.0 * w(t, 2, 0.1)), 24)
    c.fn('left_wing', 'rot', lambda t: (0.0, 2.0 * w(t, 2, 0.1), -3.0 * w(t, 2, 0.1)), 24)
    phases = {'HL': 0.0, 'FL': 0.25, 'HR': 0.5, 'FR': 0.75}
    times = [T * i / 24 for i in range(25)]
    for key, ph in phases.items():
        leg_def = x.legs[key]
        eranim.solve_leg(c, x.rig, x.v, leg_def, times, GAIT.ankle(leg_def, ph), GAIT.foot_pitch(ph))
    c.footsteps = [(round(T * ph, 4), x.legs[k].side, x.legs[k].rest_ankle[2] - GAIT.stride / 2.0)
                   for k, ph in sorted(phases.items(), key=lambda kv: kv[1])]
    return c


def rear_front_feet(c, x, up0, up1, hold, land, landing, back0, back1, tuck=0.58, curl=34.0):
    """Front feet of a rear: they leave the floor with the rising chest, hang tucked under it (claws curled), then
    stamp down at {@code land} on (forward, out) {@code landing} and step back home over [back0, back1]."""
    anim = c.anim()
    for key in ('FR', 'FL'):
        leg = x.legs[key]
        side = 1 if leg.side > 0 else -1
        rest = np.array(leg.rest_ankle, dtype=float)
        hip0 = np.array(x.rig.parts[leg.thigh].pivot, dtype=float)
        down = rest + np.array([landing[1] * side, 0.0, landing[0]])

        def tucked(t, leg=leg, hip0=hip0):
            hip = eranim.world_point(x.rig, x.v, anim, t, leg.thigh, hip0)
            return hip + np.array([0.0, (leg.l1 + leg.l2) * tuck, -2.5])

        def at(t, rest=rest, down=down, tucked=tucked):
            if t <= up0:
                return tuple(rest)
            if t <= up1:
                return tuple(eranim.lerp3(rest, tucked(t), eranim.smooth((t - up0) / (up1 - up0))))
            if t <= hold:
                return tuple(tucked(t))
            if t <= land:
                return tuple(eranim.lerp3(tucked(hold), down, ermodel.ease('I', (t - hold) / (land - hold))))
            if t <= back0:
                return tuple(down)
            if t <= back1:
                s = (t - back0) / (back1 - back0)
                p = eranim.lerp3(down, rest, eranim.smooth(s))
                return (p[0], p[1] - 3.0 * math.sin(math.pi * s), p[2])
            return tuple(rest)

        def pitch(t):
            if up0 < t <= up1:
                return curl * eranim.smooth((t - up0) / (up1 - up0))
            if up1 < t <= hold:
                return curl
            if hold < t <= land:
                return curl * (1.0 - ermodel.ease('I', (t - hold) / (land - hold)))
            if back0 < t <= back1:
                return 14.0 * math.sin(math.pi * (t - back0) / (back1 - back0))
            return 0.0

        eranim.plant(c, x.rig, x.v, leg, at, pitch, samples=int(c.length * 24))


def melee_slam(x):
    """Rears up on the hind legs with the wings thrown wide, then crashes down on the front claws."""
    A, B, Cc, I, F, R, E = 0.15, 0.95, 1.12, 1.3, 1.5, 1.95, 2.6
    c = Clip('melee_slam', E, impact=I)
    c.rot('body', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -38, 0, 0), (Cc, -41, 0, 0, 'o'), (I, 6, 0, 0, 'I'), (F, 9, 0, 0, 'O'),
                   (R, 5, 0, 0), (E, 0, 0, 0)])
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0), (B, 0, 4.0, 3.0), (Cc, 0, 4.4, 3.2, 'o'), (I, 0, -1.6, -3.0, 'I'),
                   (F, 0, -2.0, -3.2, 'O'), (R, 0, -1.2, -2.0), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 28, 0, 0, 'I'), (F, 30, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, 12, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, 30, 0, 0), (I, 42, 0, 0, 'O'), (R, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (B, 0, 40, 50), (I, 0, 0, -10, 'I'), (F, 0, 0, -14, 'b'), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (B, 0, -40, -50), (I, 0, 0, 10, 'I'), (F, 0, 0, 14, 'b'), (E, 0, 0, 0)])
    c.rot('tail1', [(0, 0, 0, 0), (B, 18, 0, 0), (I, -12, 0, 0, 'I'), (E, 0, 0, 0)])
    legs4(c, x, plans={'HR': [(1.95, 2.35, (0.0, 0.0), 2.0)], 'HL': [(2.05, 2.45, (0.0, 0.0), 2.0)]},
          skip=('FR', 'FL'))
    rear_front_feet(c, x, 0.22, 0.85, Cc, I, (-6.0, 1.0), 2.0, 2.45)
    return eranim.retime(c, 1.1)


def melee_sweep(x):
    """Tail sweep: the body coils to one side, then swings round and the tail whips through a wide arc."""
    A, B, I, F, R, E = 0.12, 0.75, 1.05, 1.3, 1.65, 2.3
    c = Clip('melee_sweep', E, impact=I)
    c.rot('body', [(0, 0, 0, 0), (A, 0, -4, 0), (B, 0, -36, 4), (I, 0, 44, -4, 'I'), (F, 0, 54, -5, 'b'), (R, 0, 30, -2),
                   (E, 0, 0, 0)])
    c.pos('base', [(0, 0, 0, 0), (B, 0, -1.4, 0.8), (I, 0, -0.6, -1.0, 'I'), (E, 0, 0, 0)])
    c.rot('tail1', [(0, 0, 0, 0), (B, 0, 36, 0), (I, 0, -52, 0, 'I'), (F, 0, -58, 0, 'b'), (R, 0, -30, 0), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (B, 0, 34, 0), (I, 0, -30, 0, 'I'), (F, 0, -36, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 0, 18, 0), (I, 0, -20, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, 18, 0, 0), (I, 30, 0, 0), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (B, 0, -10, 18), (I, 0, 10, -14, 'I'), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (B, 0, 10, -18), (I, 0, -10, 14, 'I'), (E, 0, 0, 0)])
    legs4(c, x, plans={'FR': [(0.2, 0.55, (0.0, 2.0), 3.0), (1.7, 2.1, (0.0, 0.0), 2.5)],
                       'FL': [(0.85, 1.1, (-2.0, 1.0), 3.0), (1.75, 2.15, (0.0, 0.0), 2.5)]})
    return c


def cast(x):
    """Fire breath: the neck coils back as the chest swells, then the head lunges low with the jaws wide open."""
    A, B, I, F, R, E = 0.15, 0.85, 1.15, 1.6, 1.9, 2.4
    c = Clip('cast', E, impact=I)
    c.scl('body', [(0, 1, 1, 1), (B, 1.08, 1.1, 1.03), (I, 0.97, 0.96, 1, 'I'), (F, 0.98, 0.97, 1), (E, 1, 1, 1)])
    c.rot('body', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 6, 0, 0, 'I'), (F, 5, 0, 0), (E, 0, 0, 0)])
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.6, 1.4), (I, 0, -0.8, -1.2, 'I'), (F, 0, -0.8, -1.2), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -32, 0, 0), (I, 20, 0, 0, 'I'), (F, 18, 0, 0), (R, 8, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('neck2', [(0, 0, 0, 0), (B, -22, 0, 0), (I, 12, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, 12, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, 4, 0, 0), (I, 46, 0, 0, 'O'), (F, 42, 0, 0), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (B, 0, -16, -10), (I, 0, -6, 6, 'I'), (F, 0, -6, 6), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (B, 0, 16, 10), (I, 0, 6, -6, 'I'), (F, 0, 6, -6), (E, 0, 0, 0)])
    c.rot('tail1', [(0, 0, 0, 0), (B, 10, 0, 0), (I, -8, 0, 0, 'I'), (E, 0, 0, 0)])
    legs4(c, x)
    c.muzzle_part, c.muzzle_point = 'head', SNOUT
    return eranim.retime(c, 0.8)


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(7.0, 1.0, 1), shake(8.5, 1.0, 2)
    c.fn('neck1', 'rot', lambda t: (-34.0 + 1.0 * tr(t), 0.0, 0.0), 28)
    c.fn('neck2', 'rot', lambda t: (-14.0, 0.0, 0.0), 2)
    c.fn('head', 'rot', lambda t: (-16.0 + 1.5 * tr2(t), 0.0, 2.0 * tr(t)), 28)
    c.fn('jaw', 'rot', lambda t: (46.0 + 4.0 * tr(t), 0.0, 0.0), 28)
    c.fn('body', 'rot', lambda t: (-10.0, 0.0, 1.0 * tr2(t)), 28)
    c.fn('base', 'pos', lambda t: (0.0, 1.2 + 0.3 * math.sin(2 * math.pi * t / T), 0.0), 12)
    c.fn('right_wing', 'rot', lambda t: (0.0, -30.0, -12.0 + 10.0 * math.sin(2 * math.pi * t / T * 2)), 16)
    c.fn('left_wing', 'rot', lambda t: (0.0, 30.0, 12.0 - 10.0 * math.sin(2 * math.pi * t / T * 2)), 16)
    c.fn('tail1', 'rot', lambda t: (10.0, 14.0 * math.sin(2 * math.pi * t / T), 0.0), 14)
    legs4(c, x, samples=14)
    return c


def charge(x):
    A, B, I, F, R, E = 0.12, 0.7, 1.0, 1.25, 1.55, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, -2.0, 3.0), (I, 0, -0.8, -10.0, 'I'), (F, 0, -1.0, -11.0, 'O'), (R, 0, -0.6, -7.0),
                   (E, 0, 0, 0)])
    c.rot('body', [(0, 0, 0, 0), (B, 8, 0, 0), (I, -4, 0, 0, 'I'), (F, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (B, 26, 0, 0), (I, 20, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -10, 0, 0), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, 10, 0, 0), (I, 36, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (B, 0, 16, -14), (I, 0, 30, -26, 'I'), (F, 0, 28, -24), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (B, 0, -16, 14), (I, 0, -30, 26, 'I'), (F, 0, -28, 24), (E, 0, 0, 0)])
    c.rot('tail1', [(0, 0, 0, 0), (B, 12, 0, 0), (I, 6, 0, 0), (E, 0, 0, 0)])
    legs4(c, x, plans={'FR': [(0.66, 0.98, (-8.0, 0.5), 4.0), (1.55, 1.9, (0.0, 0.0), 2.5)],
                       'FL': [(0.7, 1.0, (-8.0, 0.5), 4.0), (1.6, 1.95, (0.0, 0.0), 2.5)],
                       'HR': [(0.1, 0.4, (3.0, 0.0), 2.0), (1.5, 1.85, (0.0, 0.0), 2.0)],
                       'HL': [(0.15, 0.45, (3.0, 0.0), 2.0), (1.55, 1.9, (0.0, 0.0), 2.0)]})
    return c


def summon(x):
    """Rears up with the wings spread to their full span, then beats them down in one great gust."""
    A, B, Cc, I, F, R, E = 0.15, 0.9, 1.1, 1.3, 1.55, 1.95, 2.6
    c = Clip('summon', E, impact=I)
    c.rot('body', [(0, 0, 0, 0), (B, -22, 0, 0), (Cc, -24, 0, 0), (I, -6, 0, 0, 'I'), (F, -2, 0, 0), (E, 0, 0, 0)])
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0), (B, 0, 3.0, 1.6), (Cc, 0, 3.4, 1.8, 'o'), (I, 0, 1.0, 0, 'I'),
                   (F, 0, 0.4, 0), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (B, 0, -40, -62), (Cc, 0, -40, -66, 'o'), (I, 0, -30, 26, 'I'), (F, 0, -30, 18, 'b'),
                         (R, 0, -12, 6), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (B, 0, 40, 62), (Cc, 0, 40, 66, 'o'), (I, 0, 30, -26, 'I'), (F, 0, 30, -18, 'b'),
                        (R, 0, 12, -6), (E, 0, 0, 0)])
    c.rot('right_wing_tip', [(0, 0, 0, 0), (B, 0, 0, 40), (I, 0, 0, -20, 'I'), (E, 0, 0, 0)])
    c.rot('left_wing_tip', [(0, 0, 0, 0), (B, 0, 0, -40), (I, 0, 0, 20, 'I'), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (B, -28, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (B, 36, 0, 0), (I, 44, 0, 0), (R, 12, 0, 0), (E, 0, 0, 0)])
    legs4(c, x, plans={'FR': [(0.2, 0.9, (-1.0, 0.5), 6.0), (1.1, 1.3, (-3.0, 0.5), 0.0), (2.0, 2.4, (0.0, 0.0), 2.5)],
                       'FL': [(0.25, 0.9, (-1.0, 0.5), 6.0), (1.1, 1.3, (-3.0, 0.5), 0.0), (2.05, 2.45, (0.0, 0.0), 2.5)]})
    return c


def phase_change(x):
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(7.0, 1.0, 3)
    hold = lambda t: envelope(t, 1.8, 2.55, 0.2)
    c.rot('right_wing', [(0, 0, 0, 0), (0.3, 0, 20, 20, 'O'), (1.0, 0, 70, 60), (1.3, 0, 72, 62), (1.75, 0, -40, -62, 'O'),
                         (2.55, 0, -36, -56), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (0.3, 0, -20, -20, 'O'), (1.0, 0, -70, -60), (1.3, 0, -72, -62),
                        (1.75, 0, 40, 62, 'O'), (2.55, 0, 36, 56), (E, 0, 0, 0)])
    c.rot('right_wing_tip', [(0, 0, 0, 0), (1.0, 0, 0, -40), (1.75, 0, 0, 40, 'O'), (E, 0, 0, 0)])
    c.rot('left_wing_tip', [(0, 0, 0, 0), (1.0, 0, 0, 40), (1.75, 0, 0, -40, 'O'), (E, 0, 0, 0)])
    c.rot('neck1', [(0, 0, 0, 0), (0.3, -10, 0, 0, 'O'), (1.0, 30, 0, 0), (1.3, 32, 0, 0), (1.75, -44, 0, 0, 'O'),
                    (2.55, -40, 0, 0), (E, 0, 0, 0)])
    c.add('neck1', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 0.0), 48)
    c.rot('head', [(0, 0, 0, 0), (1.0, 24, 0, 0), (1.75, -24, 0, 0, 'O'), (2.55, -20, 0, 0), (E, 0, 0, 0)])
    c.add('head', 'rot', lambda t: (0.0, 0.0, 2.5 * tr(t + 0.2) * hold(t)), 48)
    c.rot('jaw', [(0, 0, 0, 0), (1.0, 0, 0, 0), (1.75, 52, 0, 0, 'O'), (2.55, 48, 0, 0), (E, 0, 0, 0)])
    c.rot('body', [(0, 0, 0, 0), (1.0, 10, 0, 0), (1.75, -16, 0, 0, 'O'), (2.55, -12, 0, 0), (E, 0, 0, 0)])
    c.scl('body', [(0, 1, 1, 1), (1.0, 0.95, 0.95, 0.98), (1.75, 1.08, 1.08, 1.03, 'O'), (E, 1, 1, 1)])
    c.pos('base', [(0, 0, 0, 0), (1.0, 0, -2.4, 0), (1.75, 0, 2.6, 0, 'O'), (2.55, 0, 2.0, 0), (E, 0, 0, 0)])
    legs4(c, x)
    return c


def stagger(x):
    E = 1.4
    c = Clip('stagger', E, impact=0.15)
    c.rot('neck1', [(0, 0, 0, 0), (0.14, -28, 16, 0, 'O'), (0.6, -14, 4, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -20, 22, 10, 'O'), (0.42, 0, -14, -8), (0.75, -8, 8, 4), (E, 0, 0, 0)])
    c.rot('jaw', [(0, 0, 0, 0), (0.14, 32, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('body', [(0, 0, 0, 0), (0.14, -6, 0, 10, 'O'), (0.6, -3, 0, 5), (E, 0, 0, 0)])
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, 0, 2.6, 'O'), (0.6, 0, -0.6, 3.0), (E, 0, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (0.14, 0, -10, 22, 'O'), (E, 0, 0, 0)])
    c.rot('left_wing', [(0, 0, 0, 0), (0.14, 0, 10, -22, 'O'), (E, 0, 0, 0)])
    legs4(c, x, plans={'FR': [(0.12, 0.4, (3.0, 1.0), 3.0), (0.8, 1.15, (0.0, 0.0), 2.0)]}, samples=int(E * 30))
    return c


def death(x):
    """Rears up in pain with a last roar, the legs give, and it crashes onto its side (1.8 s); the wings crumple and
    the neck sinks to the floor."""
    E = 4.0
    c = Clip('death', E, impact=1.8)
    tr = shake(6.0, 1.0, 7)
    c.rot('body', [(0, 0, 0, 0), (0.6, -26, 0, 0, 'O'), (1.3, 4, 0, 6), (1.8, 6, 0, 74, 'I'), (2.05, 6, 0, 68, 'b'),
                   (E, 6, 0, 70)])
    c.add('body', 'rot', lambda t: (1.5 * tr(t) * envelope(t, 0.4, 1.2, 0.2), 0.0, 0.0), 40)
    c.pos('base', [(0, 0, 0, 0), (0.6, 0, 2.4, 1.0, 'O'), (1.3, 0, -2.0, 0.8), (1.8, 0, -3.0, 0.8, 'I'), (E, 0, -3.0, 0.8)])
    c.rot('neck1', [(0, 0, 0, 0), (0.6, -36, 0, 0, 'O'), (1.3, 10, 0, 0), (2.2, 34, 0, 0), (E, 40, 0, 0)])
    c.rot('neck2', [(0, 0, 0, 0), (0.6, -16, 0, 0), (2.2, 24, 0, 0), (E, 30, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.6, -22, 0, 0), (2.2, 30, 0, 15), (E, 34, 0, 20)])
    c.rot('jaw', [(0, 0, 0, 0), (0.6, 48, 0, 0, 'O'), (1.4, 30, 0, 0), (E, 22, 0, 0)])
    c.rot('right_wing', [(0, 0, 0, 0), (0.6, 0, -30, -30, 'O'), (1.4, 0, -10, 10), (1.8, 0, 20, 34, 'I'), (E, 0, 20, 36)])
    c.rot('left_wing', [(0, 0, 0, 0), (0.6, 0, 30, 30, 'O'), (1.4, 0, 10, -10), (1.8, 0, -20, -44, 'I'), (E, 0, -20, -46)])
    c.rot('tail1', [(0, 0, 0, 0), (0.6, 16, 20, 0), (1.6, -6, -12, 0), (2.8, -10, -16, 0), (3.1, -10, -6, 0, 'o'),
                    (3.4, -10, -16, 0), (E, -10, -16, 0)])
    for key in ('front_right_leg', 'front_left_leg'):
        c.rot(key, [(0, 0, 0, 0), (0.6, -40, 0, 0), (1.6, 30, 0, 0), (E, 34, 0, 0)])
        c.rot(key + '_shin', [(0, 0, 0, 0), (1.6, -50, 0, 0), (E, -54, 0, 0)])
    for key in ('hind_right_leg', 'hind_left_leg'):
        c.rot(key, [(0, 0, 0, 0), (1.6, -34, 0, 0), (E, -38, 0, 0)])
        c.rot(key + '_shin', [(0, 0, 0, 0), (1.6, 44, 0, 0), (E, 48, 0, 0)])
    wings = ('right_wing', 'right_wing_tip', 'left_wing', 'left_wing_tip')
    for t in (1.8, 2.05, E):
        eranim.ground(c, x.rig, x.v, t, skip=wings)
    eranim.unsink(c, x.rig, x.v, 0.0, 1.8, skip=wings)
    return c


def animations(r):
    x = DragonCtx(r, r.variants[0])
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

def variants():
    def deco(p):
        for side in ('right', 'left'):
            s = p.face('head', side, index=0)
            gx = 3 if side == 'right' else s.w - 4
            for dx in range(2):
                s.px(gx + dx, 3, (255, 220, 60))
                p.face('head', side, index=0, glow=True).px(gx + dx, 3, (255, 255, 255))
            s.fpx(gx * s.k + 1, 3 * s.k, (40, 14, 10))          # a slit pupil in the burning eye
            s.fpx(gx * s.k + 1, 3 * s.k + 1, (40, 14, 10))
            s.px(gx, 2, (40, 14, 10))
            s.px(gx + 1, 2, (40, 14, 10))
        sn = p.face('head', 'front', index=1)
        sn.px(1, 1, (30, 10, 8))
        sn.px(sn.w - 2, 1, (30, 10, 8))
        for side in ('right', 'left'):
            j = p.face('jaw', side)
            for x in range(0, j.w, 2):
                j.px(x, 0, (240, 230, 210))
            s2 = p.face('head', side, index=1)
            for x in range(0, s2.w, 2):
                s2.px(x, s2.h - 1, (240, 230, 210))
        top = p.face('jaw', 'top')
        for y in range(top.h):
            for x in range(1, top.w - 1):
                top.px(x, y, (150, 40, 30))
        gtop = p.face('jaw', 'top', glow=True)
        for y in range(2, top.h, 2):
            for x in range(2, top.w - 2):
                gtop.px(x, y, (255, 140, 40))
        belly = p.face('body', 'bottom')
        for y in range(belly.h):
            for x in range(2, belly.w - 2):
                belly.px(x, y, (220, 160, 80) if y % 3 else (180, 120, 60))
        bf = p.face('body', 'front')
        for y in range(bf.h):
            for x in range(3, bf.w - 3):
                bf.px(x, y, (230, 170, 80) if y % 3 else (190, 130, 60))
        gb = p.face('body', 'front', glow=True)
        for y in range(4, bf.h - 2):
            for x in range(5, bf.w - 5):
                if (x + y) % 3 == 0:
                    gb.px(x, y, (255, 120, 30))
        for side in ('right', 'left', 'top'):
            fc = p.face('body', side)
            gc = p.face('body', side, glow=True)
            for y in range(1, fc.h - 1, 4):
                for x in range(2, fc.w - 2, 6):
                    fc.px(x, y, (255, 150, 50))
                    gc.px(x, y, (255, 120, 30))
    scale = {'base': '#8a2418', 'style': 'scale'}
    pal = {'head': scale, 'snout': scale, 'jaw': {'base': '#7a2014', 'style': 'scale'}, 'neck': scale, 'neck2': scale,
           'body': scale, 'leg': scale, 'shin': {'base': '#7a2014', 'style': 'scale'}, 'shin2': {'base': '#7a2014', 'style': 'scale'},
           'thigh': scale, 'foot': {'base': '#3a1a14', 'style': 'scale'}, 'tail': scale, 'tail2': scale,
           'tail3': {'base': '#7a2014', 'style': 'scale'}, 'tail4': {'base': '#6a1c12', 'style': 'scale'},
           'wingbone': {'base': '#5a1810', 'style': 'scale'}, 'membrane': {'base': '#c84a1c', 'vein': (255, 170, 60)},
           'fin': {'base': '#d85a20', 'vein': (255, 180, 60)}, 'horn': {'base': '#2a2220', 'style': 'bone'},
           'crest': {'base': '#2a2220', 'style': 'bone'}, 'spine': {'base': '#2a2220', 'style': 'bone'}}

    def deco_form(form):
        def paint(p):
            deco(p)
            for part in ('right_wing', 'right_wing_tip'):
                for fc in p.all_faces(part, index=1, glow=True):
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if (x * 3 + y * 5) % (7 - form) == 0:
                                fc.px(x, y, (255, 140, 40))
            glow_faces(p, 'horn_r', (255, 120, 40), every=3 if form < 3 else 1)
            glow_faces(p, 'head', (255, 120, 40), index=2, every=3 if form < 3 else 1)
            if form >= 2:
                veins(p, ('body', 'neck1', 'neck2', 'tail1', 'front_right_leg', 'front_left_leg', 'hind_right_leg',
                          'hind_left_leg'), (255, 120, 30), (255, 170, 60), seed=form)
            if form >= 3:
                for part in ('body', 'neck1', 'neck2', 'tail1', 'tail2', 'head'):
                    glow_bright(p, part, threshold=230)
                cracks(p, ('body', 'tail1', 'tail2', 'tail3', 'neck1'), (255, 200, 80), (255, 230, 140), every=20)
        return paint
    ember = {'base': '#2a1410', 'style': 'magma', 'hot': (255, 150, 40)}
    burning = dict(pal, membrane={'base': '#ff6a1c', 'vein': (255, 220, 90)}, fin={'base': '#ff7a24', 'vein': (255, 220, 90)})
    molten = dict(burning, head=ember, snout=ember, neck=ember, neck2=ember, body=ember, tail=ember, tail2=ember,
                  leg=ember, thigh=ember, jaw={'base': '#3a1810', 'style': 'magma', 'hot': (255, 170, 60)})
    return [Variant('b6', palette=pal, paint=deco, glow=True, extra=dict(scale=2.7, hitbox=(6.4, 6.1))),
            Variant('b6_p1', palette=burning, paint=deco_form(1), glow=True, extra=dict(scale=2.7)),
            Variant('b6_p2', palette=burning, poses={'horn_r': dict(scale=(1.2, 1.2, 1.3)),
                                                     'horn_l': dict(scale=(1.2, 1.2, 1.3))},
                    paint=deco_form(2), glow=True, extra=dict(scale=2.7)),
            Variant('b6_p3', palette=molten, poses={'horn_r': dict(scale=(1.3, 1.3, 1.5)),
                                                    'horn_l': dict(scale=(1.3, 1.3, 1.5)),
                                                    'right_wing': dict(scale=(1.15, 1.0, 1.15)),
                                                    'left_wing': dict(scale=(1.15, 1.0, 1.15))},
                    paint=deco_form(3), glow=True, extra=dict(scale=2.7))]


TRAILS = [('tail_tip', (0.0, -1.0, 54.0), TAIL_TIP), ('front_right_leg_foot', (-7.0, 22.0, -8.0), CLAW_FR)]


def rig():
    vs = variants()
    base = Rig('dragon', 'DragonBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('dragon', 'DragonBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.2
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
