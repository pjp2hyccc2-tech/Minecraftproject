"""KING boss rig: b1 地下都市の盗賊王, b8 終焉の門番, final 終焉の王 (+ form looks).

A giant armoured humanoid (49 units to the top of the head at scale 1) with crown, a three-segment cape, a front
tasset in two segments, hands that hold the greatsword (wrist motion), and feet of their own for planted steps. Extra
parts for the forms: floating crown, void core, blade halo, bigger spikes. Every clip is authored with eased keys,
legs are solved by IK so planted feet stay on the floor, and cape, tasset and beard get a spring pass for overlapping
motion. The final boss collapses with its own, longer death (two falls, in step with DefeatSequence.FINAL)."""
import math

import eranim
from boss_forms import cracks, glow_faces, veins
from eranim import Chain, Clip, Gait, Leg, breath, envelope, shake, smooth
from ermodel import C, P, Rig, Variant
from erpaint import human_face, lighten, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
SWORD_TIP = (-10.5, 4.5, -27.5)        # rest coordinates of the blade tip (part greatsword)
SWORD_MID = (-10.5, 4.5, -12.0)
PALM_L = (10.5, 5.5, -3.5)             # where the left hand releases spells


def geometry():
    crown_cubes = [C(-5, -27, -6, 10, 2, 10, 'gold:crown'), C(-4.5, -30, -6.5, 2, 3, 1, 'gold:crown_spike', tag='cs1'),
                   C(-1, -31, -6.5, 2, 4, 1, 'gold:crown_spike'), C(2.5, -30, -6.5, 2, 3, 1, 'gold:crown_spike', share='cs1'),
                   C(-5.5, -29, -1, 1, 2, 2, 'gold:crown_spike', tag='cs2'), C(4.5, -29, -1, 1, 2, 2, 'gold:crown_spike', share='cs2')]
    head = P('head', (0, -16, -1), C(-4.5, -25, -5.5, 9, 9, 9, 'skin:face'),
             P('crown', (0, -25, -1), *crown_cubes, opt=True),
             P('helm', (0, -20, -1), C(-4.5, -25, -5.5, 9, 9, 9, 'metal:helm', inflate=0.6),
               C(-0.5, -28, -5, 1, 3, 9, 'metal:helm_crest'), opt=True),
             P('horn_r', (-4.5, -23, -1), C(-8.5, -24, -2, 4, 2, 2, 'bone:horn', tag='horn'),
               P('horn_r_tip', (-8.5, -23, -1), C(-10.5, -30, -2, 2, 7, 2, 'bone:horn_tip', tag='horntip'), rot=(0, 0, 12)),
               rot=(0, 10, -15), opt=True),
             P('horn_l', (4.5, -23, -1), C(4.5, -24, -2, 4, 2, 2, 'bone:horn', share='horn', mirror=True),
               P('horn_l_tip', (8.5, -23, -1), C(8.5, -30, -2, 2, 7, 2, 'bone:horn_tip', share='horntip', mirror=True), rot=(0, 0, -12)),
               rot=(0, -10, 15), opt=True),
             P('beard', (0, -17, -5), C(-3.5, -18, -6.5, 7, 6, 2, 'hair:beard'), rot=(10, 0, 0), opt=True),
             P('floating_crown', (0, -34, -1), C(-4, -35, -5, 8, 2, 8, 'gold:fcrown'),
               C(-3.5, -38, -5.5, 1, 3, 1, 'gold:fcrown_spike', tag='fs'), C(-0.5, -39, -5.5, 1, 4, 1, 'gold:fcrown_spike'),
               C(2.5, -38, -5.5, 1, 3, 1, 'gold:fcrown_spike', share='fs'), C(-0.5, -38, 2.5, 1, 3, 1, 'gold:fcrown_spike', share='fs'),
               opt=True))
    sword = P('greatsword', (-10.5, 5, 0), C(-11, 4.5, -2, 1, 1, 6, 'leather:grip'), C(-11.5, 1.5, -4, 2, 6, 1, 'gold:guard'),
              C(-11, 3, -28, 1, 3, 24, 'metal:blade'), C(-11.5, 4, 4, 2, 2, 2, 'gold:pommel'), rot=(45, 0, 0))
    right_hand = P('right_hand', (-10.5, 3, 0), C(-13, 3, -3, 5, 4, 6, 'metal:fist', tag='fist'), sword, rot=(8, 0, 0))
    left_hand = P('left_hand', (10.5, 3, 0), C(8, 3, -3, 5, 4, 6, 'metal:fist', share='fist', mirror=True), rot=(4, 0, 0))
    right_arm = P('right_arm', (-10, -14, 0), C(-13, -15, -3, 5, 11, 6, 'cloth:sleeve', tag='uarm'),
                  P('pauldron_r', (-10, -15, 0), C(-14, -18, -4.5, 8, 5, 9, 'metal:pauldron', tag='paul'),
                    P('spikes_r', (-11, -18, 0), C(-12, -24, -1.5, 2, 6, 2, 'metal:spike', tag='sp1'),
                      C(-8.5, -22, 1.5, 2, 4, 2, 'metal:spike', tag='sp2'), C(-13, -21, 2, 2, 3, 2, 'metal:spike', tag='sp3'),
                      rot=(0, 0, -18), opt=True)),
                  P('right_forearm', (-10.5, -4, 0), C(-13.5, -4, -3.5, 6, 7, 7, 'metal:gauntlet', tag='gaunt'), right_hand,
                    rot=(-20, 0, 0)),
                  rot=(-10, 0, 7))
    left_arm = P('left_arm', (10, -14, 0), C(8, -15, -3, 5, 11, 6, 'cloth:sleeve', share='uarm', mirror=True),
                 P('pauldron_l', (10, -15, 0), C(6, -18, -4.5, 8, 5, 9, 'metal:pauldron', share='paul', mirror=True),
                   P('spikes_l', (11, -18, 0), C(10, -24, -1.5, 2, 6, 2, 'metal:spike', share='sp1', mirror=True),
                     C(6.5, -22, 1.5, 2, 4, 2, 'metal:spike', share='sp2', mirror=True),
                     C(11, -21, 2, 2, 3, 2, 'metal:spike', share='sp3', mirror=True), rot=(0, 0, 18), opt=True)),
                 P('left_forearm', (10.5, -4, 0), C(7.5, -4, -3.5, 6, 7, 7, 'metal:gauntlet', share='gaunt', mirror=True),
                   left_hand, rot=(-12, 0, 0)),
                 rot=(-4, 0, -7))
    halo_blades = []
    for k in range(8):
        halo_blades.append(P('halo_%d' % k, (0, -22, 8), C(-1, -33, 7.5, 2, 7, 1, 'metal:halo', tag='hb' if k == 0 else None,
                                                             share=None if k == 0 else 'hb'), rot=(0, 0, k * 45)))
    cape = P('cape', (0, -15, 5.5), C(-7, -15, 5.5, 14, 10, 1, 'cloth:cape'),
             P('cape_mid', (0, -5, 5.5), C(-7, -5, 5.5, 14, 10, 1, 'cloth:cape2'),
               P('cape_lower', (0, 5, 5.5), C(-7, 5, 5.5, 14, 11, 1, 'cloth:cape3'))), rot=(6, 0, 0))
    chest = P('chest', (0, 0, 0), C(-8, -16, -5, 16, 16, 10, 'metal:cuirass'),
              head, right_arm, left_arm, cape,
              P('core', (0, -10, -5), C(-2.5, -12, -5.7, 5, 5, 1, 'glowcore:core'), opt=True),
              P('halo', (0, -22, 8), *halo_blades, opt=True))
    legs = []
    for s in (-1, 1):
        name = 'right' if s < 0 else 'left'
        kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x = 3.5 * s
        legs.append(P('%s_leg' % name, (x, 5, 0), C(x - 3, 4, -3, 6, 9, 6, 'metal:greave', **kw('thigh')),
                      P('%s_shin' % name, (x, 13, 0), C(x - 3, 13, -3, 6, 8, 6, 'metal:greave2', **kw('shin')),
                        P('%s_foot' % name, (x, 21, 0), C(x - 3.5, 21, -5, 7, 3, 8, 'metal:sabaton', **kw('boot'))))))
    hips = P('hips', (0, 3, 0), C(-7, 0, -4, 14, 6, 8, 'metal:faulds'),
             chest, *legs,
             P('tasset', (0, 5, -4), C(-5, 5, -4.6, 10, 6, 1, 'cloth:tasset'),
               P('tasset_lower', (0, 11, -4.1), C(-4.5, 11, -4.6, 9, 5, 1, 'cloth:tasset2'))))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['cape', 'cape_mid', 'cape_lower'], rest='down', axes='xz', hang=0.9, freq=1.15, damp=0.3, inertia=9.0,
          falloff=1.12, limits={'x': (-6.0, 95.0), 'z': (-35.0, 35.0)},
          drag={'walk': (14.0, 0.0, 0.0), 'charge': (10.0, 0.0, 0.0), 'roar': (6.0, 0.0, 0.0)}),
    Chain(['tasset', 'tasset_lower'], rest='down', axes='xz', hang=0.8, freq=1.7, damp=0.34, inertia=7.0,
          limits={'x': (-70.0, 18.0), 'z': (-25.0, 25.0)}, drag={'walk': (-6.0, 0.0, 0.0)}),
    Chain(['beard'], rest='down', axes='xz', hang=0.55, freq=2.4, damp=0.4, inertia=4.0,
          limits={'x': (-40.0, 30.0)}),
]


class Ctx:
    def __init__(self, rig, variant):
        self.rig, self.v = rig, variant
        self.R = Leg(rig, 'right_leg', 'right_shin', 'right_foot')
        self.L = Leg(rig, 'left_leg', 'left_shin', 'left_foot')


def feet(c, x, right_plan=(), left_plan=(), right_holds=(), left_holds=(), y_off=0.0, samples=None):
    """Both legs by IK: each foot follows its step plan (planted otherwise)."""
    for leg, plan, holds in ((x.R, right_plan, right_holds), (x.L, left_plan, left_holds)):
        eranim.plant(c, x.rig, x.v, leg, eranim.steps(leg, list(plan), y_off=y_off),
                     eranim.step_pitch(list(plan), holds=list(holds)), samples=samples)


def idle(x):
    c = Clip('idle', 4.0, loop=True)
    T = 4.0
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)          # breath 0..1 (inhale at 2.0)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.6)
    c.fn('base', 'pos', lambda t: (0.55 * sway(t), 0.35 * b(t) - 0.15, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T + 1.4), 1.3 * sway(t)), 16)
    c.fn('chest', 'rot', lambda t: (-1.6 * b(t), -1.0 * math.sin(2 * math.pi * t / T + 1.4), -0.9 * sway(t)), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.016 * b(t), 1.0 + 0.012 * b(t), 1.0 + 0.026 * b(t)), 16)
    head_yaw = [(0.0, 0.0, 's'), (0.7, 0.0, 's'), (1.3, 8.0, 's'), (2.2, 8.0, 's'), (2.8, -6.0, 's'), (3.5, -6.0, 's'),
                (4.0, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.2 * b(t - 0.25) + 0.6, eranim.eased_value(head_yaw, t), 0.0), 32)
    c.fn('right_arm', 'rot', lambda t: (0.8 * math.sin(2 * math.pi * t / T), 0.0, 1.6 * b(t - 0.15)), 12)
    c.fn('left_arm', 'rot', lambda t: (-0.8 * math.sin(2 * math.pi * t / T), 0.0, -1.8 * b(t - 0.15)), 12)
    c.fn('right_forearm', 'rot', lambda t: (-1.5 * b(t - 0.3), 0.0, 0.0), 12)
    c.fn('left_forearm', 'rot', lambda t: (-2.5 * b(t - 0.3), 0.0, 0.0), 12)
    regrip = [(0.0, 0.0, 's'), (2.4, 0.0, 's'), (2.65, 7.0, 'o'), (2.9, -2.0, 's'), (3.2, 0.0, 's'), (4.0, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(regrip, t), 0.0, 0.0), 32)
    c.fn('cape', 'rot', lambda t: (3.0 + 2.0 * math.sin(2 * math.pi * t / T * 2 + 0.3), 0.0,
                                   1.5 * math.sin(2 * math.pi * t / T + 1.0)), 16)
    c.fn('floating_crown', 'pos', lambda t: (0.0, 1.1 * math.sin(2 * math.pi * t / T * 2), 0.0), 16)
    c.rot('floating_crown', [(0.0, 0, 0, 0, 'l'), (4.0, 0, 180, 0, 'l')])
    c.rot('halo', [(0.0, 0, 0, 0, 'l'), (4.0, 0, 0, 45, 'l')])
    c.fn('core', 'scale', lambda t: (1.0 + 0.18 * b(2 * t), 1.0 + 0.18 * b(2 * t), 1.0), 16)
    feet(c, x, samples=16)
    return c


GAIT = Gait(1.5, stride=16.0, duty=0.62, lift=3.6, heel=10.0, toe=16.0)


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    c.fn('base', 'pos', lambda t: (-0.8 * w(t, 1, 0.25), -0.35 - 0.75 * w(t, 2, 0.06), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.2 * w(t, 2, 0.1), -6.0 * w(t), 3.5 * math.sin(2 * math.pi * t / T)), 24)
    c.fn('chest', 'rot', lambda t: (4.5 + 1.2 * w(t, 2, 0.12), 5.5 * w(t, 1, 0.04), -2.0 * math.sin(2 * math.pi * t / T)), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.0 * w(t, 2, 0.16), -3.5 * w(t, 1, 0.06), 1.2 * math.sin(2 * math.pi * t / T)), 24)
    c.fn('right_arm', 'rot', lambda t: (7.0 * w(t, 1, 0.07), 0.0, 1.5 + 1.0 * w(t, 2, 0.1)), 24)
    c.fn('right_forearm', 'rot', lambda t: (-3.0 - 3.0 * w(t, 1, 0.55), 0.0, 0.0), 24)
    c.fn('right_hand', 'rot', lambda t: (3.0 * w(t, 1, 0.15), 0.0, 0.0), 24)
    c.fn('left_arm', 'rot', lambda t: (-17.0 * w(t, 1, 0.07), 0.0, -2.0 - 1.5 * w(t, 2, 0.1)), 24)
    c.fn('left_forearm', 'rot', lambda t: (-8.0 - 9.0 * max(0.0, -w(t, 1, 0.55)), 0.0, 0.0), 24)
    c.fn('left_hand', 'rot', lambda t: (-6.0 * w(t, 1, 0.2), 0.0, 0.0), 24)
    c.fn('floating_crown', 'pos', lambda t: (0.0, 0.8 + 0.6 * w(t, 2, 0.3), 0.0), 12)
    c.rot('halo', [(0.0, 0, 0, 0, 'l'), (T, 0, 0, 22.5, 'l')])
    for leg, phase in ((x.R, 0.0), (x.L, 0.5)):
        eranim.solve_leg(c, x.rig, x.v, leg, [T * i / 24 for i in range(25)], GAIT.ankle(leg, phase),
                         GAIT.foot_pitch(phase))
    c.footsteps = [(round(t, 4), leg.side, -GAIT.stride / 2.0) for leg, t in
                   ((x.R, 0.0), (x.L, T * 0.5))]
    return c


def melee_slam(x):
    A, B, Cc, I, F, R, E = 0.14, 0.8, 0.98, 1.15, 1.32, 1.72, 2.3
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.9, -0.4), (B, 0, 1.3, 1.6), (Cc, 0, 1.5, 1.9, 'o'), (I, 0, -3.4, -2.4, 'I'),
                   (F, 0, -3.8, -2.6, 'O'), (R, 0, -3.1, -2.1), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -7, 4, 0), (I, 11, -2, 0, 'I'), (F, 13, -2, 0, 'O'), (R, 9, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 5, 0, 0), (B, -24, -6, 0), (Cc, -27, -6, 0, 'o'), (I, 24, 3, 0, 'I'),
                    (F, 27, 3, 0, 'O'), (R, 18, 1, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -16, 4, 0), (Cc, -18, 4, 0, 'o'), (I, -14, 0, 0, 'I'),
                   (F, -16, 0, 0), (R, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 14, 0, 0), (B, -158, 0, 10), (Cc, -166, 0, 12, 'o'), (I, -34, 0, 3, 'I'),
                        (F, -28, 0, 3, 'O'), (R, -32, 0, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -12, 0, 0), (Cc, -16, 0, 0), (I, 16, 0, 0, 'I'), (F, 20, 0, 0, 'O'),
                            (R, 14, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -38, 0, 0), (Cc, -44, 0, 0, 'o'), (I, 12, 0, 0, 'I'), (F, 4, 0, 0, 'b'),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 10, 0, 0), (B, -150, 0, 24), (Cc, -158, 0, 26, 'o'), (I, -40, 0, 18, 'I'),
                       (F, -34, 0, 18, 'O'), (R, -36, 0, 12), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -22, 0, 0), (I, 6, 0, 0, 'I'), (F, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.35, 1.35, 1.1), (I, 1.7, 1.7, 1.2, 'O'), (R, 1.1, 1.1, 1), (E, 1, 1, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (B, 0, 3.0, 0), (I, 0, -1.2, 0, 'I'), (F, 0, -0.4, 0, 'b'), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.16, 0.5, (6.0, 0.6), 2.5), (1.78, 2.12, (0.0, 0.0), 2.0)])
    # the blade bites into the floor in front at the impact frame
    eranim.reach_y(c, x.rig, x.v, I, 'right_arm', 'x', 'greatsword', SWORD_TIP, 23.2, -70.0, -10.0)
    eranim.reach_y(c, x.rig, x.v, F, 'right_arm', 'x', 'greatsword', SWORD_TIP, 23.4, -70.0, -10.0)
    # then it rests on the floor (not in it) while he heaves it back up
    eranim.rest_on_floor(c, x.rig, x.v, [F + 0.06 * i for i in range(1, 9)], 'greatsword', clearance=0.3)
    return eranim.retime(c, 1.1)


def melee_sweep(x):
    """Forehand sweep: the torso coils to the right with the blade levelled behind the right hip, then unwinds through
    the front (impact) and follows through far to the left."""
    A, B, I, F, R, E = 0.1, 0.7, 0.95, 1.2, 1.5, 2.0
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0.3, -0.6, 0), (B, -1.4, -1.8, 0.9), (I, 0.8, -2.2, -1.9, 'I'),
                   (F, 1.6, -2.0, -2.3, 'O'), (R, 1.0, -1.3, -1.5), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 20, -3), (I, 2, -8, 3, 'I'), (F, 2, -22, 3, 'b'), (R, 1, -14, 1), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 0, -5, 0), (B, 4, 38, -5), (I, 8, -14, 3, 'I'), (F, 6, -40, 4, 'b'),
                    (R, 3, -26, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 2, -34, 0), (I, 0, 8, 0, 'I'), (F, -2, 28, 0, 'b'), (R, 0, 16, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 6, 0, -3), (B, -72, 78, 2), (I, -80, -6, 2, 'I'), (F, -74, -62, 0, 'O'),
                        (R, -40, -30, 0), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 14, 0, 0, 'I'), (F, 10, 0, 0), (R, 4, 0, 0),
                            (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 50, 0, 0), (I, 40, 0, 0, 'I'), (F, 40, 0, 0, 'O'), (R, 18, 0, 0),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -58, 0, -22), (I, -24, 0, -50, 'I'), (F, 6, 0, -62, 'O'), (R, 0, 0, -30),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -12, 0, 0), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.3, 1.3, 1.1), (I, 1.6, 1.6, 1.2, 'O'), (E, 1, 1, 1)])
    feet(c, x, left_plan=[(0.74, 0.94, (-6.0, 2.0), 3.0), (1.5, 1.88, (0.0, 0.0), 2.0)],
         right_plan=[(0.12, 0.45, (2.5, 1.0), 1.5), (1.55, 1.9, (0.0, 0.0), 1.5)])
    for t, elevation in ((B, 4.0), (I, -6.0), (F, -4.0)):       # the blade sweeps level, slightly down at the hit
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'greatsword', SWORD_MID, SWORD_TIP, elevation, -40.0, 120.0)
    return eranim.retime(c, 0.8)


def cast(x):
    A, B, I, F, R, E = 0.12, 0.72, 0.95, 1.25, 1.5, 2.0
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.4, 0), (B, 0.6, 0.9, 1.1), (I, -0.4, -1.5, -1.9, 'I'),
                   (F, -0.5, -1.7, -2.1, 'O'), (R, -0.3, -1.1, -1.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -15, -22, 0), (I, 14, 18, 0, 'I'), (F, 16, 22, 0, 'b'), (R, 8, 10, 0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -4, -8, 0), (I, 5, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -22, 14, 0), (I, 6, -12, 0, 'I'), (F, 4, -14, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 8, 0, 0), (B, -165, 0, -20), (I, -88, 0, 6, 'I'), (F, -96, 0, 4, 'b'),
                       (R, -70, 0, 2), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 2, 0, 0, 'I'), (F, -2, 0, 0), (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -40, 0, 0), (I, -72, 0, 0, 'I'), (F, -64, 0, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 24, 0, 12), (I, 10, 0, 6, 'I'), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -14, 0, 0), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.6, 1.6, 1.2), (I, 2.3, 2.3, 1.4, 'O'), (F, 1.3, 1.3, 1.1), (E, 1, 1, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (B, 0, 3.0, 0), (I, 0, 0.5, 0, 'I'), (E, 0, 0, 0)])
    c.rot('halo', [(0, 0, 0, 0, 'l'), (E, 0, 0, 180, 'O')])
    feet(c, x, left_plan=[(0.7, 0.92, (-5.0, 0.5), 2.5), (1.5, 1.86, (0.0, 0.0), 2.0)])
    eranim.rest_on_floor(c, x.rig, x.v, [0.9 + 0.08 * i for i in range(10)], 'greatsword', clearance=0.3)
    c.muzzle_part, c.muzzle_point = 'left_hand', PALM_L
    return eranim.retime(c, 0.8)


def roar(x):
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr = shake(7.5, 1.0, 1)
    tr2 = shake(9.0, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.5 + 0.25 * math.sin(2 * math.pi * t / T), 0.7), 12)
    c.fn('hips', 'rot', lambda t: (5.0, 6.0, 0.0), 2)
    c.fn('chest', 'rot', lambda t: (11.0 + 0.8 * tr(t), -4.0, 1.8 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (9.0 + 1.2 * tr2(t), 4.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (20.0 + 1.5 * tr(t), 0.0, 12.0), 12)
    c.fn('right_forearm', 'rot', lambda t: (-30.0, 0.0, 0.0), 2)
    c.fn('right_hand', 'rot', lambda t: (-18.0 + 2.0 * tr2(t), 0.0, 0.0), 12)
    c.fn('left_arm', 'rot', lambda t: (-42.0 + 3.0 * math.sin(2 * math.pi * t / T), 0.0, -18.0), 12)
    c.fn('left_forearm', 'rot', lambda t: (-36.0, 0.0, 0.0), 2)
    c.fn('left_hand', 'rot', lambda t: (-22.0 + 4.0 * tr(t), 0.0, 0.0), 12)
    c.fn('core', 'scale', lambda t: (1.35 + 0.25 * math.sin(4 * math.pi * t / T), 1.35 + 0.25 * math.sin(4 * math.pi * t / T),
                                     1.1), 8)
    c.fn('floating_crown', 'pos', lambda t: (0.0, 2.2 + 0.4 * math.sin(2 * math.pi * t / T), 0.0), 8)
    c.rot('halo', [(0.0, 0, 0, 0, 'l'), (T, 0, 0, 45, 'l')])
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 0.5), 0.0)], samples=12)
    return c


def charge(x):
    A, B, I, F, R, E = 0.12, 0.6, 0.85, 1.05, 1.35, 1.8
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0.5), (B, 0, -2.3, 2.4), (I, 0, -2.7, -7.6, 'I'), (F, 0, -2.9, -8.6, 'O'),
                   (R, 0, -2.2, -6.8), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 6, 18, 0), (I, 12, -8, 0, 'I'), (F, 12, -10, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 12, 26, 0), (I, 26, -15, 0, 'I'), (F, 29, -18, 0, 'b'), (R, 18, -10, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -8, -24, 0), (I, -14, 12, 0, 'I'), (F, -12, 14, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 36, 0, 16), (I, -80, 0, 4, 'I'), (F, -86, 0, 3, 'b'), (R, -68, 0, 3),
                        (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -46, 0, 0), (I, 2, 0, 0, 'I'), (F, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -60, 0, 0), (I, -48, 0, 0, 'I'), (F, -44, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -70, 0, -16), (I, 22, 0, -10, 'I'), (F, 26, 0, -12), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -34, 0, 0), (I, -8, 0, 0), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.56, 0.82, (-9.0, 0.5), 3.5), (1.32, 1.72, (0.0, 0.0), 2.5)],
         right_plan=[(0.1, 0.4, (3.0, 0.5), 1.5), (1.4, 1.75, (0.0, 0.0), 1.5)])
    for t, elevation in ((B, 2.0), (I, -4.0), (F, -6.0), (R, -10.0)):     # the point stays level on the thrust
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'greatsword', SWORD_MID, SWORD_TIP, elevation, -120.0, 90.0)
    return c


def summon(x):
    A, B, Cc, I, F, R, E = 0.15, 0.85, 1.05, 1.25, 1.45, 1.85, 2.45
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0), (B, 0, 1.7, 0.5), (Cc, 0, 1.9, 0.6, 'o'), (I, 0, -6.0, -1.0, 'I'),
                   (F, 0, -6.2, -1.1, 'O'), (R, 0, -5.6, -0.8), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -12, 0, 0), (Cc, -14, 0, 0), (I, 20, 0, 0, 'I'), (F, 24, 0, 0, 'O'),
                    (R, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (I, 8, 0, 0, 'I'), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -24, 0, 0), (I, 18, 0, 0, 'I'), (F, 26, 0, 0), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -168, 0, -6), (Cc, -172, 0, -6), (I, -46, 0, -7, 'I'), (F, -42, 0, -7),
                        (R, -40, 0, -5), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -8, 0, 0), (I, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -100, 0, 0), (Cc, -104, 0, 0), (I, -52, 0, 0, 'I'), (F, -50, 0, 0),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -164, 0, 20), (Cc, -168, 0, 20), (I, -44, 0, 18, 'I'), (F, -40, 0, 18),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 2, 0, 0), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.5, 1.5, 1.2), (I, 2.2, 2.2, 1.4, 'O'), (R, 1.2, 1.2, 1.1), (E, 1, 1, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (B, 0, 4.0, 0), (I, 0, -1.0, 0, 'I'), (E, 0, 0, 0)])
    knee = [(0.95, 1.25, (8.0, 0.5), 2.0), (1.95, 2.35, (0.0, 0.0), 2.5)]
    feet(c, x, right_plan=knee, right_holds=[(1.25, 1.95, 48.0)],
         left_plan=[(0.95, 1.22, (-3.5, 0.5), 2.0), (2.0, 2.35, (0.0, 0.0), 1.5)])
    # raised to the sky point up, then flipped and driven into the floor point down
    for t, elevation, lo, hi in ((B, 82.0, -20.0, 140.0), (Cc, 86.0, -20.0, 140.0), (R, -80.0, -20.0, 160.0)):
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'greatsword', SWORD_MID, SWORD_TIP, elevation, lo, hi)
    for t in (I, F):
        for _ in range(3):     # the blade stands upright with its point in the floor
            eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'greatsword', SWORD_MID, SWORD_TIP, -88.0, -20.0, 160.0)
            eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'greatsword', SWORD_TIP, 25.0, -100.0, 10.0)
    return c


def phase_change(x):
    E = 3.0
    I = 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(8.0, 1.0, 3)
    grow = lambda t: envelope(t, 0.8, 1.3, 0.3)
    hold = lambda t: envelope(t, 1.8, 2.45, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.25, 0, -0.6, 1.8, 'O'), (0.9, 0, -6.4, 0.6), (1.3, 0, -6.6, 0.5),
                   (1.75, 0, 1.4, -0.4, 'O'), (2.45, 0, 1.0, -0.3), (E, 0, 0, 0)])
    keys_chest = [(0, 0, 0, 0), (0.25, -16, 0, 0, 'O'), (0.9, 34, 0, 0), (1.3, 38, 0, 0), (1.75, -30, 0, 0, 'O'),
                  (2.45, -26, 0, 0), (E, 0, 0, 0)]
    c.rot('chest', keys_chest)
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 2.2 * tr(t) * (grow(t) + hold(t))), 48)
    c.rot('head', [(0, 0, 0, 0), (0.25, -22, 0, 0, 'O'), (0.9, 30, 0, 0), (1.3, 32, 0, 0), (1.75, -42, 0, 0, 'O'),
                   (2.45, -36, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.25, -30, 0, 42, 'O'), (0.9, -72, -10, -38), (1.3, -74, -10, -42),
                        (1.75, -40, 0, 98, 'O'), (2.45, -38, 0, 92), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.25, -26, 0, -40, 'O'), (0.9, -70, 10, 40), (1.3, -72, 10, 44),
                       (1.75, -40, 0, -100, 'O'), (2.45, -38, 0, -94), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (0.9, -40, 0, 0), (1.75, -10, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (0.9, -42, 0, 0), (1.75, -12, 0, 0, 'O'), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 2.0 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -2.0 * tr(t + 0.3) * hold(t)), 48)
    c.scl('core', [(0, 1, 1, 1), (1.2, 0.45, 0.45, 1), (1.8, 2.5, 2.5, 1.6, 'O'), (2.5, 1.6, 1.6, 1.2), (E, 1, 1, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (1.2, 0, -2.0, 0), (1.8, 0, 8.0, 0, 'O'), (2.5, 0, 6.0, 0), (E, 0, 0, 0)])
    c.rot('floating_crown', [(0, 0, 0, 0), (1.8, 0, 360, 0, 'O'), (E, 0, 360, 0)])
    c.rot('halo', [(0, 0, 0, 0, 'l'), (1.2, 0, 0, 30, 's'), (1.8, 0, 0, 200, 'O'), (E, 0, 0, 270, 's')])
    feet(c, x, right_plan=[(0.3, 0.85, (8.5, 0.5), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.5, 2.9, (0.0, 0.0), 1.5)],
         right_holds=[(0.85, 1.32, 46.0)],
         left_plan=[(0.25, 0.6, (-2.0, 0.5), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.5, 2.9, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.4
    c = Clip('stagger', E, impact=0.1)
    tr = shake(6.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.12, 0, -0.9, 3.3, 'O'), (0.42, 0, -1.6, 3.9), (0.75, 0, -0.8, 2.0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.12, -25, 6, 8, 'O'), (0.42, -10, -4, 3), (0.62, 6, 2, -2), (0.9, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.12, -30, 12, 6, 'O'), (0.42, -6, -10, 0), (0.62, 10, 6, 0), (0.85, 0, -4, 0),
                   (1.05, 2, 3, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.12, -58, 0, 44, 'O'), (0.42, -26, 0, 20), (0.8, -8, 0, 6), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.12, -42, 0, -56, 'O'), (0.42, -20, 0, -30), (0.8, -6, 0, -8), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (0.12, 24, 0, 0, 'O'), (0.5, 4, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.15, 0.6, 0.15)), 24)
    feet(c, x, right_plan=[(0.1, 0.36, (6.0, 1.0), 2.5), (0.75, 1.08, (0.0, 0.0), 2.0)],
         left_plan=[(0.32, 0.55, (3.0, 0.0), 1.5), (0.95, 1.25, (0.0, 0.0), 1.5)])
    return c


def death(x, final=False):
    """Regular collapse: recoil, stagger, knees, topple face down at 1.8 s (DefeatSequence SHOCKWAVE beat)."""
    E = 3.6
    c = Clip('death', E, impact=1.8)
    tr = shake(7.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -0.4, 2.0, 'O'), (0.9, 0, -2.4, -1.0), (1.3, 0, -9.0, -1.4, 'i'),
                   (1.8, 0, -9.0, -3.0, 'I'), (2.0, 0, -9.0, -3.0, 'b'), (E, 0, -9.0, -3.0)])
    c.rot('hips', [(0, 0, 0, 0), (0.3, -8, 0, 0, 'O'), (0.9, 6, 0, 0), (1.3, 10, 0, 3), (1.8, 74, 0, 6, 'I'),
                   (2.0, 70, 0, 6, 'b'), (2.4, 72, 0, 6), (E, 72, 0, 6)])
    c.rot('chest', [(0, 0, 0, 0), (0.3, -28, 0, 0, 'O'), (0.9, 12, 0, -4), (1.3, 10, 0, -6), (1.8, 14, 0, -4, 'I'),
                    (2.0, 8, 0, -4, 'b'), (E, 8, 0, -4)])
    c.rot('head', [(0, 0, 0, 0), (0.3, -38, 0, 0, 'O'), (0.9, 14, 8, 0), (1.3, 6, 4, 0), (1.8, 2, 26, 8, 'I'),
                   (2.1, -6, 30, 10, 'b'), (E, -6, 30, 10)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.3, -42, 0, 56, 'O'), (0.9, -6, 0, 18), (1.3, 8, 0, 12),
                        (1.8, -88, 0, 34, 'I'), (2.1, -84, 0, 38, 'b'), (E, -84, 0, 38)])
    c.rot('right_forearm', [(0, 0, 0, 0), (0.9, 16, 0, 0), (1.8, 10, 0, 0), (E, 10, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (0.5, 10, 0, 0), (0.85, 64, 0, 0), (1.8, 50, 0, 0), (E, 50, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (0.7, 0, 0, 0), (1.0, 38, 0, 24), (1.8, 40, 0, 30), (E, 40, 0, 30)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.3, -32, 0, -52, 'O'), (0.9, -4, 0, -14), (1.3, 6, 0, -10),
                       (1.8, -96, 0, -30, 'I'), (2.1, -92, 0, -34, 'b'), (2.8, -92, 0, -34), (3.0, -100, 0, -30, 'o'),
                       (3.2, -92, 0, -34), (E, -92, 0, -34)])
    c.rot('left_forearm', [(0, 0, 0, 0), (0.9, 10, 0, 0), (1.8, -20, 0, 0), (E, -20, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.3, 0.95, 0.2)), 36)
    c.scl('core', [(0, 1, 1, 1), (0.3, 2.0, 2.0, 1.4, 'O'), (1.8, 0.6, 0.6, 1), (E, 0.15, 0.15, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (0.3, 0, 3, 0, 'O'), (1.3, 0, 1, -2), (2.2, 0, -16, -14, 'i'),
                             (2.45, 0, -14.5, -15, 'b'), (E, 0, -15, -15)])
    c.rot('floating_crown', [(0, 0, 0, 0), (2.2, 70, 40, 30, 'i'), (2.45, 80, 40, 36), (E, 82, 40, 36)])
    kneel = [(0.85, 1.3, (8.0, 0.8), 1.5), (1.4, 1.8, (14.0, 1.0), 0.0)]
    for _ in range(2):           # the floor contact and the legs depend on each other: settle them together
        feet(c, x, right_plan=kneel, left_plan=[(0.95, 1.3, (6.0, 0.8), 1.5), (1.4, 1.8, (12.0, 1.0), 0.0)],
             right_holds=[(1.3, 1.45, 50.0), (1.45, E, 72.0)], left_holds=[(1.3, 1.45, 46.0), (1.45, E, 70.0)])
        for t in (1.3, 1.8, 2.0, 2.4, E):
            eranim.ground(c, x.rig, x.v, t, skip=('floating_crown', 'greatsword'))
    # the sword never cuts through the floor as he staggers, and drops flat on it beside him
    eranim.rest_on_floor(c, x.rig, x.v, [0.5 + 0.05 * i for i in range(int(round((E - 0.5) / 0.05)) + 1)], 'greatsword')
    return c


def death_final(x):
    """The King of the End: kneels at 1.6 s (first shockwave), holds himself up reaching for the sky, and only falls at
    4.5 s (second shockwave); then lies still while the light takes him (DefeatSequence.FINAL)."""
    E = 5.6
    c = Clip('death_final', E, impact=1.6)
    tr = shake(6.5, 1.0, 9)
    c.pos('base', [(0, 0, 0, 0), (0.35, 0, -0.5, 2.4, 'O'), (1.1, 0, -2.0, -0.6), (1.6, 0, -9.0, -0.8, 'I'),
                   (1.75, 0, -8.6, -0.8, 'b'), (3.6, 0, -8.4, -0.8), (4.1, 0, -8.0, -1.2), (4.5, 0, -9.0, -3.0, 'I'),
                   (4.7, 0, -9.0, -3.0, 'b'), (E, 0, -9.0, -3.0)])
    c.rot('hips', [(0, 0, 0, 0), (0.35, -10, 0, 0, 'O'), (1.1, 6, 0, 0), (1.6, 10, 0, 2, 'I'), (3.6, 4, 0, 2),
                   (4.1, -6, 0, 0), (4.5, 74, 0, 4, 'I'), (4.7, 70, 0, 4, 'b'), (E, 72, 0, 4)])
    c.rot('chest', [(0, 0, 0, 0), (0.35, -30, 0, 0, 'O'), (1.1, 14, 0, 0), (1.6, 22, 0, 0, 'I'), (2.2, 10, 0, 0),
                    (3.0, -24, 6, 0), (3.6, -30, 6, 0), (4.1, -10, 0, 0), (4.5, 12, 0, -4, 'I'), (4.7, 6, 0, -4, 'b'),
                    (E, 6, 0, -4)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 2.4 * tr(t) * envelope(t, 1.8, 3.8, 0.4)), 64)
    c.rot('head', [(0, 0, 0, 0), (0.35, -40, 0, 0, 'O'), (1.1, 18, 0, 0), (1.6, 30, 0, 0, 'I'), (2.2, 10, 0, 0),
                   (3.0, -40, 0, 0), (3.6, -46, 0, 0), (4.1, -10, 0, 0), (4.5, 4, 26, 8, 'I'), (4.8, -4, 30, 10, 'b'),
                   (E, -4, 30, 10)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.35, -44, 0, 58, 'O'), (1.1, -10, 0, 20), (1.6, -50, 0, 6, 'I'),
                        (2.2, -46, 0, 8), (3.6, -44, 0, 8), (4.5, -88, 0, 34, 'I'), (4.8, -84, 0, 38, 'b'),
                        (E, -84, 0, 38)])
    c.rot('right_hand', [(0, 0, 0, 0), (1.1, -40, 0, 0), (1.6, -60, 0, 0, 'I'), (3.6, -60, 0, 0), (4.3, 40, 0, 0),
                         (E, 40, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.35, -34, 0, -54, 'O'), (1.1, -8, 0, -14), (1.6, -6, 0, -10, 'I'),
                       (2.4, -40, 0, -10), (3.0, -160, 0, -12), (3.6, -168, 0, -14), (4.1, -60, 0, -20),
                       (4.5, -96, 0, -30, 'I'), (4.8, -92, 0, -34, 'b'), (E, -92, 0, -34)])
    c.rot('left_forearm', [(0, 0, 0, 0), (2.4, -20, 0, 0), (3.0, -8, 0, 0), (3.6, -4, 0, 0), (4.5, -20, 0, 0),
                           (E, -20, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (3.0, -40, 0, 0), (3.6, -50, 0, 0), (4.5, 0, 0, 0), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (0.35, 2.2, 2.2, 1.5, 'O'), (1.6, 1.4, 1.4, 1.2), (3.6, 1.8, 1.8, 1.3),
                   (4.5, 0.4, 0.4, 1, 'I'), (E, 0.1, 0.1, 1)])
    c.pos('floating_crown', [(0, 0, 0, 0), (0.35, 0, 4, 0, 'O'), (1.6, 0, 2, 0), (3.6, 0, 6, 0), (4.4, 0, -15, -12, 'i'),
                             (4.65, 0, -13.5, -13, 'b'), (E, 0, -14, -13)])
    c.rot('floating_crown', [(0, 0, 0, 0), (3.6, 0, 240, 0), (4.4, 70, 280, 30, 'i'), (E, 82, 290, 36)])
    c.rot('halo', [(0, 0, 0, 0, 'l'), (1.6, 0, 0, 90, 'O'), (3.6, 0, 0, 160), (4.5, 0, 0, 200, 'O'), (E, 0, 0, 205)])
    for _ in range(2):
        feet(c, x, right_plan=[(1.05, 1.5, (8.0, 0.8), 1.5), (4.1, 4.5, (14.0, 1.0), 0.0)],
             left_plan=[(1.15, 1.55, (-3.0, 1.0), 1.5), (4.15, 4.5, (12.0, 1.0), 0.0)],
             right_holds=[(1.5, 4.1, 46.0), (4.1, E, 72.0)], left_holds=[(4.15, E, 70.0)])
        for t in (4.5, 4.7, E):
            eranim.ground(c, x.rig, x.v, t, skip=('floating_crown', 'greatsword', 'halo'))
    eranim.rest_on_floor(c, x.rig, x.v, [4.2 + 0.1 * i for i in range(int(round((E - 4.2) / 0.1)) + 1)], 'greatsword')
    return c


def animations(rig_obj):
    x = Ctx(rig_obj, rig_obj.variants[0])
    xf = Ctx(rig_obj, [v for v in rig_obj.variants if v.id == 'final'][0])
    built = []
    for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon, phase_change, stagger):
        clip = fn(x)
        an = clip.build(rig_obj, x.v, CHAINS)
        for extra in ('footsteps',):
            if hasattr(clip, extra):
                setattr(an, extra, getattr(clip, extra))
        if hasattr(clip, 'muzzle_part'):
            an.muzzle = muzzle(rig_obj, x.v, an, clip.muzzle_part, clip.muzzle_point)
        built.append(an)
    built.append(death(x).build(rig_obj, x.v, CHAINS))
    built.append(death_final(xf).build(rig_obj, xf.v, CHAINS))
    return built


def muzzle(rig_obj, v, an, part, point):
    """{left, up, forward} in blocks at scale 1 of a point at the clip's impact frame."""
    p = eranim.world_point(rig_obj, v, an, an.impact, part, point)
    return (p[0] / 16.0, (24.0 - p[1]) / 16.0, -p[2] / 16.0)


# --------------------------------------------------------------------------------------------- looks

def pal(**over):
    p = {'face': '#c49070', 'crown': '#d8b040', 'crown_spike': '#e8c050', 'helm': '#7a7e88', 'helm_crest': '#5a5e68',
         'horn': '#d8cfb8', 'horn_tip': '#b8af98', 'beard': '#5a3a24', 'fcrown': '#e0c050', 'fcrown_spike': '#f0d060',
         'grip': '#4a3020', 'guard': '#c8a040', 'blade': '#c8d0d8', 'pommel': '#c8a040', 'sleeve': '#7a2a24',
         'pauldron': '#8a8e98', 'spike': '#5a5e68', 'gauntlet': '#8a8e98', 'fist': '#7a7e88', 'cuirass': '#8a8e98',
         'cape': '#8a1e1e', 'cape2': '#8a1e1e', 'cape3': '#8a1e1e', 'core': '#c060ff', 'halo': '#2a2030',
         'greave': '#7a7e88', 'greave2': '#7a7e88', 'sabaton': '#5a5e68', 'faulds': '#6a6e78', 'tasset': '#7a2a24',
         'tasset2': '#7a2a24'}
    p.update(over)
    return p


def cuirass_detail(p, trim, emblem=None, glow=None):
    f = p.face('chest', 'front')
    f.vline(f.w // 2, 1, f.h - 2, shade(f.get(f.w // 2, 3), 0.7))
    for y in range(2, f.h - 2, 4):
        f.hline(2, f.w - 3, y, lighten(f.get(3, y), 0.15))
    f.hline(0, f.w - 1, 0, trim)
    f.hline(0, f.w - 1, f.h - 1, trim)
    for fy in range(f.fh):                       # fine engraving along the plates
        if fy % (4 * f.k) == f.k:
            for fx in range(2 * f.k, f.fw - 2 * f.k, 3):
                f.fpx(fx, fy, lighten(f.fget(fx, fy), 0.3))
    if emblem:
        cx, cy = f.w // 2, 5
        for (dx, dy) in emblem:
            f.px(cx + dx, cy + dy, trim)
            if glow is not None:
                p.face('chest', 'front', glow=True).px(cx + dx, cy + dy, glow)


CROWN_SHAPE = [(-2, 0), (-1, 0), (0, 0), (1, 0), (-2, -1), (0, -1), (-2, -2), (0, -2), (-1, 1), (0, 1), (-1, 2)]


def tatter(p, part, depth=3, every=2):
    for side in ('front', 'back'):
        fc = p.face(part, side)
        for x in range(fc.w):
            d = (x * 7 % 5) % depth if x % every == 0 else 0
            for k in range(d):
                fc.clear(x, fc.h - 1 - k)
            if d and fc.k > 1:                      # frayed threads below the torn edge
                fc.fclear(x * fc.k + 1, fc.fh - 1 - d * fc.k)


def eye_glint(fc, gfc, points, core, halo=None):
    """Glowing eyes with a white-hot pixel in the middle of each unit (sharp at the boss's size)."""
    for (x, y) in points:
        gfc.px(x, y, halo or core)
        gfc.fpx(x * fc.k + fc.k // 2, y * fc.k, (255, 255, 255))
        fc.fpx(x * fc.k + fc.k // 2, y * fc.k, (255, 255, 255))


def blade_edge(p, glow=None):
    """A bright cutting edge along the blade (both sides) and a darker fuller in the middle."""
    for fc in p.all_faces('greatsword', index=2, sides_only=True):
        if fc.face in ('right', 'left'):
            for fx in range(fc.fw):
                fc.fpx(fx, 0, lighten(fc.fget(fx, 0), 0.5))
                fc.fpx(fx, fc.fh // 2, shade(fc.fget(fx, fc.fh // 2), 0.75))
                if glow is not None:
                    p.face('greatsword', fc.face, index=2, glow=True).fpx(fx, 0, glow)


def variants():
    V = []

    def deco_b1(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#c49070'), eye_col=(60, 30, 20), brows=(50, 30, 20), mouth=False)
        f.px(2, 3, (120, 40, 30))
        f.hline(1, 3, 2, (60, 30, 20))
        cuirass_detail(p, (200, 160, 60), emblem=CROWN_SHAPE)
        g = p.face('head', 'front', glow=True)
        eye_glint(f, g, [(2, 4), (6, 4)], (255, 240, 200))
        for fc in p.all_faces('crown', index=0, sides_only=True):
            for x in range(1, fc.w - 1, 3):
                fc.px(x, 0, (200, 40, 40))
                p.face('crown', fc.face, index=0, glow=True).px(x, 0, (255, 230, 200))
        tatter(p, 'cape_lower')
        blade_edge(p)
        for fc in p.all_faces('chest', sides_only=True):
            if fc.face != 'front':
                for y in range(1, fc.h, 5):
                    fc.hline(0, fc.w - 1, y, (90, 60, 40))
    leather = {'base': '#6a4a30', 'style': 'leather'}
    pal_b1 = pal(
        cuirass={'base': '#7a5a3a', 'style': 'leather'}, pauldron={'base': '#8a8e98', 'style': 'metal'},
        gauntlet=leather, fist={'base': '#5a3e28', 'style': 'leather'}, greave=leather,
        greave2={'base': '#5a3e28', 'style': 'leather'}, faulds=leather,
        sabaton={'base': '#4a3020', 'style': 'leather'}, sleeve='#7a2a24', cape={'base': '#8a1e1e', 'trim': (200, 160, 60)},
        cape2={'base': '#7e1c1c'}, cape3={'base': '#741a1a', 'trim': (200, 160, 60)},
        tasset2={'base': '#6e2620', 'trim': (200, 160, 60)},
        blade={'base': '#a8b0b8', 'style': 'metal'}, core={'base': '#3a1030', 'style': 'crystal'})
    V.append(Variant('b1', show={'crown', 'beard'}, palette=pal_b1, paint=deco_b1, glow=True,
                     extra=dict(scale=1.9, hitbox=(2.7, 5.4))))

    def deco_b1_rage(p):
        deco_b1(p)
        glow_faces(p, 'core', (255, 70, 140))
        g = p.face('head', 'front', glow=True)
        eye_glint(p.face('head', 'front'), g, [(2, 4), (6, 4)], (255, 80, 60))
        veins(p, ('chest', 'right_arm', 'left_arm', 'right_leg', 'left_leg'), (210, 40, 90), (255, 90, 150))
        cracks(p, ('chest', 'hips'), (240, 70, 120), (255, 120, 170), every=36)
        for fc in p.all_faces('crown', index=0, glow=True, sides_only=True):
            for x in range(0, fc.w, 2):
                fc.px(x, 0, (255, 120, 80))
        tatter(p, 'cape_mid', depth=2, every=3)
        tatter(p, 'cape_lower', depth=5)
    V.append(Variant('b1_p1', show={'crown', 'beard', 'core', 'spikes_r', 'spikes_l'},
                     palette=dict(pal_b1, spike={'base': '#2a1020', 'style': 'crystal'}),
                     poses={'spikes_r': dict(scale=(0.8, 0.9, 0.8)), 'spikes_l': dict(scale=(0.8, 0.9, 0.8))},
                     paint=deco_b1_rage, glow=True, extra=dict(scale=1.9)))

    def deco_b8(p):
        hf = p.face('helm', 'front')
        hf.hline(1, hf.w - 2, 4, (10, 12, 14))
        hf.hline(2, hf.w - 3, 5, (10, 12, 14))
        hf.vline(hf.w // 2, 6, hf.h - 2, (10, 12, 14))
        g = p.face('helm', 'front', glow=True)
        eye_glint(hf, g, [(2, 4), (3, 4), (5, 4), (6, 4)], (220, 255, 250))
        cuirass_detail(p, (90, 220, 210), emblem=[(0, -2), (0, -1), (0, 0), (0, 1), (0, 2), (-1, 0), (1, 0), (-2, -1), (2, -1)],
                       glow=(200, 255, 250))
        blade_edge(p, glow=(160, 255, 245))
        for fc in p.all_faces('greatsword', index=2, glow=True, sides_only=True):
            if fc.face in ('right', 'left'):
                for x in range(1, fc.w - 1, 3):
                    fc.px(x, fc.h // 2, (200, 255, 250))
        tatter(p, 'cape_lower', depth=4)
        tatter(p, 'cape_mid', depth=2, every=3)
    dark = {'base': '#3c4048', 'style': 'metal'}
    pal_b8 = pal(
        helm={'base': '#454a54', 'style': 'metal'}, helm_crest='#2a2e34', horn='#2a2e34', horn_tip='#1a1e24',
        cuirass=dark, pauldron={'base': '#454a54', 'style': 'metal'}, gauntlet=dark, fist={'base': '#30343c', 'style': 'metal'},
        greave=dark, greave2=dark,
        sabaton={'base': '#2a2e34', 'style': 'metal'}, faulds=dark, sleeve='#22262c', spike='#1a1e24',
        cape={'base': '#1e3a3a', 'style': 'cloth'}, cape2={'base': '#1a3434', 'style': 'cloth'},
        cape3={'base': '#162e2e', 'style': 'cloth'}, tasset='#1e3a3a', tasset2='#1a3232',
        blade={'base': '#5a6470', 'style': 'metal'}, guard='#2a2e34', pommel='#2a2e34',
        core={'base': '#5ae8d8', 'style': 'glowcore'}, halo={'base': '#1e2a2a', 'style': 'metal'})
    V.append(Variant('b8', show={'helm', 'horn_r', 'horn_l', 'spikes_r', 'spikes_l'}, palette=pal_b8,
                     paint=deco_b8, glow=True, extra=dict(scale=2.9, hitbox=(3.8, 8.0))))

    def deco_b8_form(form):
        def deco(p):
            deco_b8(p)
            glow_faces(p, 'core', (220, 255, 250))
            veins(p, ('chest', 'right_arm', 'left_arm'), (50, 150, 140), (200, 255, 250), seed=form)
            if form >= 2:
                for k in range(8):
                    for fc in p.all_faces('halo_%d' % k, glow=True, sides_only=True):
                        fc.vline(fc.w // 2, 0, fc.h - 1, (150, 255, 240))
                veins(p, ('right_forearm', 'left_forearm', 'right_leg', 'left_leg'), (50, 150, 140), (200, 255, 250),
                      seed=form + 3)
            if form >= 3:
                cracks(p, ('chest', 'hips', 'right_shin', 'left_shin'), (120, 255, 235), (230, 255, 252), every=30)
                tatter(p, 'cape', depth=3, every=2)
                tatter(p, 'cape_lower', depth=5)
        return deco
    b8_parts = {'helm', 'horn_r', 'horn_l', 'spikes_r', 'spikes_l', 'core'}
    V.append(Variant('b8_p1', show=b8_parts, palette=pal_b8, paint=deco_b8_form(1), glow=True, extra=dict(scale=2.9)))
    V.append(Variant('b8_p2', show=b8_parts | {'halo'}, palette=pal_b8, paint=deco_b8_form(2), glow=True,
                     extra=dict(scale=2.9)))
    V.append(Variant('b8_p3', show=b8_parts | {'halo'}, palette=pal_b8,
                     poses={'spikes_r': dict(scale=(1.4, 1.6, 1.4)), 'spikes_l': dict(scale=(1.4, 1.6, 1.4))},
                     paint=deco_b8_form(3), glow=True, extra=dict(scale=2.9)))

    def make_final(phase):
        def deco(p):
            f = p.face('head', 'front')
            f.rect(0, 0, f.w - 1, f.h - 1, (60, 50, 70))
            for y in range(f.h):
                f.px(0, y, (40, 32, 48))
                f.px(f.w - 1, y, (40, 32, 48))
            f.hline(1, 3, 4, (10, 6, 14))
            f.hline(5, 7, 4, (10, 6, 14))
            g = p.face('head', 'front', glow=True)
            eye_glint(f, g, [(1, 4), (2, 4), (3, 4), (5, 4), (6, 4), (7, 4)],
                      (255, 255, 255) if phase < 2 else (255, 200, 255))
            f.hline(2, 6, 7, (20, 12, 26))
            cuirass_detail(p, (200, 160, 70), emblem=CROWN_SHAPE, glow=(255, 240, 255))
            blade_edge(p, glow=(220, 160, 255) if phase >= 1 else None)
            if phase >= 2:
                for part in ('chest', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_forearm', 'left_forearm'):
                    for fc in p.all_faces(part, sides_only=True):
                        gfc = p.face(part, fc.face, glow=True)
                        x = (len(part) * 3) % max(1, fc.w)
                        for y in range(fc.h):
                            if 0 <= x < fc.w:
                                fc.px(x, y, (200, 120, 255))
                                gfc.px(x, y, (230, 180, 255))
                            x += (1 if (y * 5 + len(part)) % 3 == 0 else (-1 if (y * 7) % 4 == 0 else 0))
            if phase >= 3:
                for fc in p.all_faces('halo_0', glow=True, sides_only=True):
                    fc.vline(fc.w // 2, 0, fc.h - 1, (220, 160, 255))
            tatter(p, 'cape_lower', depth=3 + phase)
            if phase >= 2:
                tatter(p, 'cape_mid', depth=2 + phase, every=2)
        return deco
    void = {'base': '#2a2232', 'style': 'metal'}
    final_pal = pal(face='#3c3248', crown='#c8a040', fcrown='#d8b048', cuirass=void,
                    pauldron={'base': '#342a3e', 'style': 'metal'}, gauntlet=void, fist={'base': '#221a2a', 'style': 'metal'},
                    greave=void, greave2=void,
                    sabaton={'base': '#1e1826', 'style': 'metal'}, faulds=void, sleeve='#3a1a4a', spike='#c8a040',
                    cape={'base': '#4a1060', 'trim': (200, 160, 70)}, cape2={'base': '#420e56'},
                    cape3={'base': '#3a0c4c', 'trim': (200, 160, 70)}, tasset='#4a1060',
                    tasset2={'base': '#400e54', 'trim': (200, 160, 70)}, blade={'base': '#3a3048', 'style': 'metal'},
                    guard='#c8a040', pommel='#c8a040', core={'base': '#d070ff', 'style': 'glowcore'},
                    halo={'base': '#1a1420', 'style': 'metal'})
    death_set = {'death': 'death_final'}
    V.append(Variant('final', show={'crown', 'spikes_r', 'spikes_l'}, palette=final_pal, paint=make_final(0), glow=True,
                     overrides=death_set, extra=dict(scale=3.2, hitbox=(4.2, 8.9))))
    V.append(Variant('final_p1', show={'floating_crown', 'spikes_r', 'spikes_l'}, palette=final_pal, paint=make_final(1),
                     glow=True, overrides=death_set, extra=dict(scale=3.2)))
    V.append(Variant('final_p2', show={'floating_crown', 'spikes_r', 'spikes_l', 'core'}, palette=final_pal,
                     poses={'spikes_r': dict(scale=(1.4, 1.5, 1.4)), 'spikes_l': dict(scale=(1.4, 1.5, 1.4))},
                     paint=make_final(2), glow=True, overrides=death_set, extra=dict(scale=3.2)))
    V.append(Variant('final_p3', show={'floating_crown', 'spikes_r', 'spikes_l', 'core', 'halo'}, hide={'cape_lower'},
                     palette=final_pal, poses={'spikes_r': dict(scale=(1.5, 1.7, 1.5)), 'spikes_l': dict(scale=(1.5, 1.7, 1.5))},
                     paint=make_final(3), glow=True, overrides=death_set, extra=dict(scale=3.2)))
    return V


TRAILS = [('greatsword', SWORD_MID, SWORD_TIP)]


def rig():
    vs = variants()
    r = Rig('king', 'KingBoss', geometry(), [], vs, SLOTS, texel=2)
    anims = animations(r)
    r2 = Rig('king', 'KingBoss', geometry(), anims, vs, SLOTS, texel=2)
    r2.preview_ppu = 3.0
    r2.walk_speed = GAIT.walk_speed()
    r2.trails = TRAILS
    r2.trails_for = lambda v: [(b, _local(r2, b, base), _local(r2, b, tip)) for b, base, tip in TRAILS]
    return r2


def _local(r, bone, point):
    pv = r.parts[bone].pivot
    return tuple(round(point[i] - pv[i], 3) for i in range(3))
