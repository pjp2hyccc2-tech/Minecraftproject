"""COLOSSUS boss rig: b3 砂塵の巨神 (sandstone giant with nemes headdress, false beard and glowing hieroglyphs) and
b7 天空の機械神 (mechanical god: visor, antennae, chest reactor and a rotating 8-segment ring on its back).

A ten-block giant (54 units to the top of the head at scale 1, drawn at about 3x). Fists of their own (wrists),
feet of their own (planted by IK), swaying nemes lappets, a two-segment loincloth and springy antennae; every clip is
slow and heavy: long readable wind-ups, short violent strikes, settling recoveries."""
import math

import eranim
from boss_forms import cracks, glow_bright, glow_faces, veins
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
FIST_R = (-14.0, 11.0, 0.0)        # bottom of the right fist (rest coordinates of right_hand)
FIST_L = (15.0, 11.0, 0.0)
CHEST_FRONT = (0.0, -14.0, -9.0)   # where beams and volleys leave (in front of the core)


def side_arm(s):
    name = 'right' if s < 0 else 'left'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x_out = -18 if s < 0 else 11
    hand = P('%s_hand' % name, (14.5 * s, 3, 0), C(x_out - 1, 3, -5, 9, 8, 10, 'sandstone:fist', **kw('fist')))
    fore = P('%s_forearm' % name, (14.5 * s, -7, 0),
             C(x_out - 0.5, -7, -4.5, 8, 10, 9, 'sandstone:forearm', **kw('fore')), hand, rot=(-10, 0, 0))
    cannon = P('cannon_%s' % ('r' if s < 0 else 'l'), (14 * s, -25, 0),
               C(x_out, -28, -3, 5, 4, 8, 'mech:cannon', **kw('cannon')),
               C(x_out + 1.5, -27, -7, 2, 2, 4, 'metal:barrel', **kw('barrel')), opt=True)
    return P('%s_arm' % name, (13 * s, -18, 0), C(x_out, -21, -4, 7, 14, 8, 'sandstone:arm', **kw('arm')),
             P('shoulder_r' if s < 0 else 'shoulder_l', (14 * s, -22, 0),
               C(x_out - 1, -24, -5, 9, 5, 10, 'sandstone:shoulder', **kw('shoulder')), cannon),
             fore, rot=(-4, 0, 5 * -s))


def geometry():
    ring_segments = []
    for k in range(8):
        ring_segments.append(P('ring_s%d' % k, (0, -12, 10), C(-4, -27, 9.5, 8, 2, 2, 'mech:ring', tag='rs' if k == 0 else None,
                                                                   share=None if k == 0 else 'rs'), rot=(0, 0, k * 45)))
    head = P('head', (0, -21, -2), C(-5, -30, -7, 10, 9, 9, 'sandstone:head'),
             P('nemes', (0, -30, 0), C(-7, -31, -5, 14, 12, 9, 'cloth:nemes'),
               P('lappet_r', (-5.5, -22, -5), C(-7, -22, -6, 3, 9, 2, 'cloth:lappet', tag='lap')),
               P('lappet_l', (5.5, -22, -5), C(4, -22, -6, 3, 9, 2, 'cloth:lappet', share='lap', mirror=True)), opt=True),
             P('false_beard', (0, -21, -6), C(-1, -21, -7, 2, 5, 2, 'gold:beard'), opt=True),
             P('visor', (0, -26, -7), C(-5, -27, -7.6, 10, 2, 1, 'glowcore:visor'), opt=True),
             P('antenna_r', (-4, -30, -2), C(-4.5, -37, -2.5, 1, 7, 1, 'metal:antenna', tag='ant'), rot=(-10, 0, -15), opt=True),
             P('antenna_l', (4, -30, -2), C(3.5, -37, -2.5, 1, 7, 1, 'metal:antenna', share='ant', mirror=True), rot=(-10, 0, 15),
               opt=True))
    torso = P('torso', (0, -1, 0), C(-11, -21, -7, 22, 20, 14, 'sandstone:torso'),
              head, side_arm(-1), side_arm(1),
              P('core', (0, -14, -7), C(-3, -17, -7.6, 6, 6, 1, 'glowcore:core'), opt=True),
              P('crystal', (0, -12, -7), C(-1.5, -16, -10, 3, 7, 3, 'crystal:crystal'),
                C(1.5, -13, -9, 2, 4, 2, 'crystal:crystal2', tag='cc'), C(-3.5, -12, -9, 2, 4, 2, 'crystal:crystal2', share='cc'),
                opt=True),
              P('ring', (0, -12, 10), *ring_segments, opt=True),
              P('back_vents', (0, -12, 7), C(-7, -18, 6.5, 4, 10, 2, 'metal:vent', tag='vent'),
                C(3, -18, 6.5, 4, 10, 2, 'metal:vent', share='vent', mirror=True), opt=True))
    legs = []
    for s in (-1, 1):
        name = 'right' if s < 0 else 'left'
        kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x = 5 * s
        legs.append(P('%s_leg' % name, (x, 4, 0), C(x - 4, 3, -4, 8, 10, 8, 'sandstone:thigh', **kw('thigh')),
                      P('%s_shin' % name, (x, 13, 0), C(x - 4.5, 13, -4.5, 9, 8, 9, 'sandstone:shin', **kw('shin')),
                        P('%s_foot' % name, (x, 21, 0), C(x - 5, 21, -6.5, 10, 3, 12, 'sandstone:foot', **kw('foot'))))))
    pelvis = P('pelvis', (0, 3, 0), C(-8, -1, -5, 16, 6, 10, 'sandstone:pelvis'), torso, *legs,
               P('loincloth', (0, 4, -5), C(-5, 4, -5.6, 10, 6, 1, 'cloth:loin'),
                 P('loincloth_lower', (0, 10, -5.1), C(-4.5, 10, -5.6, 9, 5, 1, 'cloth:loin2')), opt=True))
    return [P('base', (0, 24, 0), pelvis)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['lappet_r'], rest='down', axes='xz', hang=0.65, freq=1.9, damp=0.38, inertia=6.0,
          limits={'x': (-50.0, 40.0), 'z': (-30.0, 30.0)}),
    Chain(['lappet_l'], rest='down', axes='xz', hang=0.65, freq=2.05, damp=0.38, inertia=6.0,
          limits={'x': (-50.0, 40.0), 'z': (-30.0, 30.0)}),
    Chain(['loincloth', 'loincloth_lower'], rest='down', axes='xz', hang=0.8, freq=1.45, damp=0.34, inertia=7.0,
          limits={'x': (-75.0, 15.0), 'z': (-25.0, 25.0)}, drag={'walk': (-5.0, 0.0, 0.0), 'charge': (-25.0, 0.0, 0.0)}),
    Chain(['antenna_r'], rest='up', axes='xz', hang=0.0, freq=3.2, damp=0.22, inertia=10.0,
          limits={'x': (-35.0, 35.0), 'z': (-35.0, 35.0)}),
    Chain(['antenna_l'], rest='up', axes='xz', hang=0.0, freq=3.4, damp=0.22, inertia=10.0,
          limits={'x': (-35.0, 35.0), 'z': (-35.0, 35.0)}),
]
GAIT = Gait(2.2, stride=18.0, duty=0.66, lift=3.2, heel=8.0, toe=12.0)


def idle(x):
    T = 4.5
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.9)
    c.fn('base', 'pos', lambda t: (0.5 * sway(t), 0.45 * b(t) - 0.2, 0.0), 18)
    c.fn('pelvis', 'rot', lambda t: (0.0, 1.0 * math.sin(2 * math.pi * t / T + 2.0), 1.1 * sway(t)), 18)
    c.fn('torso', 'rot', lambda t: (-1.8 * b(t), -0.8 * math.sin(2 * math.pi * t / T + 2.0), -0.8 * sway(t)), 18)
    c.fn('torso', 'scale', lambda t: (1.0 + 0.02 * b(t), 1.0 + 0.012 * b(t), 1.0 + 0.03 * b(t)), 18)
    look = [(0.0, 0.0, 's'), (0.8, 0.0, 's'), (1.5, -11.0, 's'), (2.4, -11.0, 's'), (3.2, 8.0, 's'), (3.9, 8.0, 's'),
            (4.5, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.4 * b(t - 0.3) + 0.5, eranim.eased_value(look, t), 0.0), 36)
    c.fn('right_arm', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T), 0.0, 1.8 * b(t - 0.2)), 12)
    c.fn('left_arm', 'rot', lambda t: (-0.6 * math.sin(2 * math.pi * t / T), 0.0, -1.8 * b(t - 0.2)), 12)
    c.fn('right_forearm', 'rot', lambda t: (-2.0 * b(t - 0.35), 0.0, 0.0), 12)
    c.fn('left_forearm', 'rot', lambda t: (-2.0 * b(t - 0.4), 0.0, 0.0), 12)
    clench = [(0.0, 0.0, 's'), (2.2, 0.0, 's'), (2.5, -14.0, 'O'), (2.75, 4.0, 's'), (3.1, 0.0, 's'), (4.5, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(clench, t), 0.0, 0.0), 36)
    c.fn('left_hand', 'rot', lambda t: (eranim.eased_value(clench, t - 0.15), 0.0, 0.0), 36)
    c.rot('ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 0, 45, 'l')])
    c.fn('core', 'scale', lambda t: (1.0 + 0.16 * b(2 * t), 1.0 + 0.16 * b(2 * t), 1.0), 18)
    c.fn('crystal', 'scale', lambda t: (1.0 + 0.05 * b(t * 2), 1.0 + 0.05 * b(t * 2), 1.0 + 0.05 * b(t * 2)), 18)
    feet(c, x, samples=18)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.2 * w(t, 1, 0.25), -0.55 - 1.1 * w(t, 2, 0.07), 0.0), 24)
    c.fn('pelvis', 'rot', lambda t: (1.5 * w(t, 2, 0.1), -5.0 * w(t), 3.6 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (5.0 + 1.6 * w(t, 2, 0.13), 4.5 * w(t, 1, 0.05), -3.0 * s1(t - 0.05)), 24)
    c.fn('head', 'rot', lambda t: (-3.5 - 1.2 * w(t, 2, 0.18), -3.0 * w(t, 1, 0.07), 1.5 * s1(t - 0.1)), 24)
    c.fn('right_arm', 'rot', lambda t: (11.0 * w(t, 1, 0.08), 0.0, 1.5 + 1.5 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-11.0 * w(t, 1, 0.08), 0.0, -1.5 - 1.5 * w(t, 2, 0.1)), 24)
    c.fn('right_forearm', 'rot', lambda t: (-4.0 - 6.0 * max(0.0, -w(t, 1, 0.12)), 0.0, 0.0), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 6.0 * max(0.0, w(t, 1, 0.12)), 0.0, 0.0), 24)
    c.fn('right_hand', 'rot', lambda t: (5.0 * w(t, 1, 0.2), 0.0, 0.0), 24)
    c.fn('left_hand', 'rot', lambda t: (-5.0 * w(t, 1, 0.2), 0.0, 0.0), 24)
    c.rot('ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 0, 45, 'l')])
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Double-fist hammer: both fists rise above the head, the giant arches back, then smashes them into the floor."""
    A, B, Cc, I, F, R, E = 0.15, 0.95, 1.12, 1.3, 1.48, 1.95, 2.6
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -1.0, 0), (B, 0, 1.5, 1.8), (Cc, 0, 1.7, 2.0, 'o'), (I, 0, -7.0, -3.0, 'I'),
                   (F, 0, -7.6, -3.2, 'O'), (R, 0, -5.0, -2.2), (E, 0, 0, 0)])
    c.rot('pelvis', [(0, 0, 0, 0), (B, -8, 0, 0), (I, 18, 0, 0, 'I'), (F, 20, 0, 0, 'O'), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -26, 0, 0), (Cc, -28, 0, 0, 'o'), (I, 36, 0, 0, 'I'),
                    (F, 40, 0, 0, 'O'), (R, 24, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -18, 0, 0), (I, -12, 0, 0, 'I'), (F, -14, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 14, 0, 6 * sz), (B, -168, 0, -16 * sz), (Cc, -174, 0, -18 * sz, 'o'),
                                (I, -42, 0, -8 * sz, 'I'), (F, -34, 0, -8 * sz, 'O'), (R, -34, 0, -4 * sz), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -22, 0, 0), (I, 8, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (B, -24, 0, 0), (I, 12, 0, 0, 'I'), (F, 6, 0, 0, 'b'), (E, 0, 0, 0)])
    c.rot('ring', [(0, 0, 0, 0), (B, 0, 0, 60), (I, 0, 0, 200, 'I'), (E, 0, 0, 360, 'O')])
    c.scl('ring', [(0, 1, 1, 1), (B, 1.15, 1.15, 1), (I, 0.9, 0.9, 1, 'I'), (F, 1.2, 1.2, 1, 'b'), (E, 1, 1, 1)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.5, 1.5, 1.2), (I, 2.2, 2.2, 1.4, 'O'), (R, 1.2, 1.2, 1.1), (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.18, 0.55, (7.0, 0.8), 2.5), (2.02, 2.42, (0.0, 0.0), 2.0)])
    for side, part, point in (('right', 'right_hand', FIST_R), ('left', 'left_hand', FIST_L)):    # they stay where they landed
        a = eranim.reach_y(c, x.rig, x.v, I, side + '_arm', 'x', part, point, 24.0, -100.0, -5.0)
        eranim.reach_y(c, x.rig, x.v, F, side + '_arm', 'x', part, point, 24.0, a - 14.0, a + 14.0)
    return eranim.retime(c, 1.1)


def melee_sweep(x):
    """Haymaker: the torso coils to the right, the right fist swings through the front and far across."""
    A, B, I, F, R, E = 0.12, 0.8, 1.05, 1.32, 1.65, 2.3
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0.3, -0.6, 0), (B, -1.6, -1.9, 1.0), (I, 1.0, -2.3, -2.0, 'I'),
                   (F, 1.8, -2.1, -2.4, 'O'), (R, 1.0, -1.4, -1.5), (E, 0, 0, 0)])
    c.rot('pelvis', [(0, 0, 0, 0), (B, 0, 22, -3), (I, 2, -10, 3, 'I'), (F, 2, -26, 3, 'b'), (R, 1, -16, 1), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 0, -5, 0), (B, 6, 40, -6), (I, 10, -18, 5, 'I'), (F, 8, -48, 5, 'b'),
                    (R, 4, -30, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -32, 0), (I, 0, 10, 0, 'I'), (F, -2, 28, 0, 'b'), (R, 0, 16, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 6, 0, -3), (B, -70, 80, 0), (I, -84, -8, 0, 'I'), (F, -76, -66, 0, 'O'),
                        (R, -40, -30, 0), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -4, 0, 0, 'I'), (F, -8, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 10, 0, 0, 'I'), (F, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -55, 0, -24), (I, -24, 0, -50, 'I'), (F, 8, 0, -62, 'O'), (R, 0, 0, -30),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -26, 0, 0), (I, -10, 0, 0), (E, 0, 0, 0)])
    c.rot('ring', [(0, 0, 0, 0), (B, 0, 0, -40), (I, 0, 0, 60, 'I'), (E, 0, 0, 90, 'O')])
    c.scl('core', [(0, 1, 1, 1), (B, 1.3, 1.3, 1.1), (I, 1.7, 1.7, 1.2, 'O'), (E, 1, 1, 1)])
    feet(c, x, left_plan=[(0.84, 1.03, (-7.0, 2.0), 3.0), (1.68, 2.08, (0.0, 0.0), 2.0)],
         right_plan=[(0.15, 0.5, (3.0, 1.0), 1.5), (1.7, 2.1, (0.0, 0.0), 1.5)])
    return eranim.retime(c, 1.0)


def cast(x):
    A, B, I, F, R, E = 0.15, 0.85, 1.15, 1.45, 1.8, 2.4
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.5, 0), (B, 0, 1.0, 1.2), (I, 0, -1.6, -2.0, 'I'), (F, 0, -1.8, -2.2, 'O'),
                   (R, 0, -1.1, -1.3), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -18, 0, 0), (I, 16, 0, 0, 'I'), (F, 18, 0, 0, 'b'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('pelvis', [(0, 0, 0, 0), (B, -5, 0, 0), (I, 6, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -26, 0, 0), (I, -4, 0, 0, 'I'), (F, -2, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 8, 0, 0), (B, -150, 0, 30 * sz), (I, -86, 0, -6 * sz, 'I'),
                                (F, -94, 0, -6 * sz, 'b'), (R, -60, 0, -2 * sz), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -20, 0, 0), (I, -2, 0, 0, 'I'), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (B, -40, 0, 0), (I, -60, 0, 0, 'I'), (F, -54, 0, 0, 'b'), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.6, 1.6, 1.2), (I, 2.4, 2.4, 1.5, 'O'), (F, 1.3, 1.3, 1.1), (E, 1, 1, 1)])
    c.rot('ring', [(0, 0, 0, 0, 'l'), (B, 0, 0, 120, 'i'), (I, 0, 0, 260, 'O'), (E, 0, 0, 360, 's')])
    c.scl('ring', [(0, 1, 1, 1), (B, 1.2, 1.2, 1), (I, 1.3, 1.3, 1, 'O'), (E, 1, 1, 1)])
    feet(c, x, left_plan=[(0.9, 1.12, (-5.0, 0.5), 2.5), (1.8, 2.2, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'torso', CHEST_FRONT
    return eranim.retime(c, 0.8)


def roar(x):
    T = 1.4
    c = Clip('roar', T, loop=True)
    tr = shake(6.5, 1.0, 1)
    tr2 = shake(8.0, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.6 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('pelvis', 'rot', lambda t: (6.0, 0.0, 0.0), 2)
    c.fn('torso', 'rot', lambda t: (12.0 + 0.8 * tr(t), 0.0, 1.6 * tr2(t)), 28)
    c.fn('head', 'rot', lambda t: (10.0 + 1.2 * tr2(t), 0.0, 1.0 * tr(t)), 28)
    c.fn('right_arm', 'rot', lambda t: (14.0 + 1.5 * tr(t), 0.0, 16.0), 14)
    c.fn('left_arm', 'rot', lambda t: (14.0 + 1.5 * tr2(t), 0.0, -16.0), 14)
    c.fn('right_forearm', 'rot', lambda t: (-40.0, 0.0, 0.0), 2)
    c.fn('left_forearm', 'rot', lambda t: (-40.0, 0.0, 0.0), 2)
    c.fn('right_hand', 'rot', lambda t: (-20.0 + 2.0 * tr(t), 0.0, 0.0), 14)
    c.fn('left_hand', 'rot', lambda t: (-20.0 + 2.0 * tr2(t), 0.0, 0.0), 14)
    c.rot('ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 0, 90, 'l')])
    c.fn('core', 'scale', lambda t: (1.35 + 0.25 * math.sin(4 * math.pi * t / T),
                                     1.35 + 0.25 * math.sin(4 * math.pi * t / T), 1.2), 8)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=14)
    return c


def charge(x):
    """Shoulder charge: the giant lowers its shoulder, then drives forward."""
    A, B, I, F, R, E = 0.12, 0.7, 1.0, 1.2, 1.5, 2.0
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.5), (B, 0, -2.4, 2.6), (I, 0, -2.8, -9.0, 'I'), (F, 0, -3.0, -10.0, 'O'),
                   (R, 0, -2.2, -7.0), (E, 0, 0, 0)])
    c.rot('pelvis', [(0, 0, 0, 0), (B, 8, 16, 0), (I, 14, -6, 0, 'I'), (F, 14, -8, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 20, 24, 0), (I, 30, -12, 0, 'I'), (F, 32, -14, 0, 'b'), (R, 20, -8, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, -20, 0), (I, -18, 10, 0, 'I'), (F, -18, 12, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -20, 0, 30), (I, -40, 0, 18, 'I'), (F, -44, 0, 16), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -40, 0, 0), (I, -50, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, 30, 0, -20), (I, 22, 0, -24, 'I'), (F, 24, 0, -26), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -20, 0, 0), (I, -30, 0, 0), (E, 0, 0, 0)])
    c.rot('ring', [(0, 0, 0, 0), (I, 0, 0, 180, 'I'), (E, 0, 0, 225, 'O')])
    feet(c, x, left_plan=[(0.66, 0.97, (-10.0, 0.5), 4.0), (1.5, 1.9, (0.0, 0.0), 2.5)],
         right_plan=[(0.12, 0.45, (3.0, 0.5), 1.5), (1.55, 1.92, (0.0, 0.0), 1.5)])
    return c


def summon(x):
    """The right fist rises to the sky, then the giant drops to one knee and punches the floor."""
    A, B, Cc, I, F, R, E = 0.15, 0.9, 1.1, 1.3, 1.5, 1.95, 2.6
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0), (B, 0, 1.4, 0.5), (Cc, 0, 1.6, 0.6, 'o'), (I, 0, -7.0, -1.5, 'I'),
                   (F, 0, -7.2, -1.6, 'O'), (R, 0, -6.4, -1.2), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -14, 6, 0), (I, 26, -4, 0, 'I'), (F, 30, -4, 0, 'O'), (R, 16, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 6, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -176, 0, -6), (Cc, -178, 0, -6), (I, -70, 0, -4, 'I'), (F, -66, 0, -4),
                        (R, -50, 0, -2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 6, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 0, 0, 0), (I, 20, 0, 0, 'I'), (F, 14, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -30, 0, -30), (I, -20, 0, -22, 'I'), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.6, 1.6, 1.2), (I, 2.3, 2.3, 1.4, 'O'), (E, 1, 1, 1)])
    c.scl('crystal', [(0, 1, 1, 1), (B, 1.15, 1.15, 1.15), (I, 1.3, 1.3, 1.3, 'O'), (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.95, 1.28, (9.0, 0.5), 2.5), (2.05, 2.5, (0.0, 0.0), 2.5)], right_holds=[(1.28, 2.05, 46.0)],
         left_plan=[(0.95, 1.25, (-4.0, 0.5), 2.0), (2.1, 2.5, (0.0, 0.0), 1.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'right_hand', FIST_R, 24.0, -110.0, -20.0)
    return c


def phase_change(x):
    E, I = 3.2, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(7.0, 1.0, 3)
    grow = lambda t: envelope(t, 0.8, 1.3, 0.3)
    hold = lambda t: envelope(t, 1.8, 2.55, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.25, 0, -0.6, 1.8, 'O'), (0.9, 0, -6.6, 0.6), (1.3, 0, -6.8, 0.5),
                   (1.75, 0, 1.6, -0.4, 'O'), (2.55, 0, 1.1, -0.3), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.25, -18, 0, 0, 'O'), (0.9, 34, 0, 0), (1.3, 38, 0, 0), (1.75, -30, 0, 0, 'O'),
                    (2.55, -26, 0, 0), (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * (grow(t) + hold(t))), 48)
    c.rot('head', [(0, 0, 0, 0), (0.25, -22, 0, 0, 'O'), (0.9, 30, 0, 0), (1.3, 32, 0, 0), (1.75, -42, 0, 0, 'O'),
                   (2.55, -36, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.25, -30, 0, 40, 'O'), (0.9, -72, 0, -40), (1.3, -74, 0, -44),
                        (1.75, -40, 0, 96, 'O'), (2.55, -38, 0, 90), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.25, -26, 0, -40, 'O'), (0.9, -70, 0, 40), (1.3, -72, 0, 44),
                       (1.75, -40, 0, -98, 'O'), (2.55, -38, 0, -92), (E, 0, 0, 0)])
    for side in ('right', 'left'):
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (0.9, -40, 0, 0), (1.75, -12, 0, 0, 'O'), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (0.9, -30, 0, 0), (1.75, 10, 0, 0, 'O'), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 2.0 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -2.0 * tr(t + 0.3) * hold(t)), 48)
    c.scl('core', [(0, 1, 1, 1), (1.2, 0.5, 0.5, 1), (1.8, 2.6, 2.6, 1.6, 'O'), (2.6, 1.6, 1.6, 1.2), (E, 1, 1, 1)])
    c.rot('ring', [(0, 0, 0, 0, 'l'), (1.2, 0, 0, 60, 's'), (1.8, 0, 0, 400, 'O'), (E, 0, 0, 720, 's')])
    c.scl('ring', [(0, 1, 1, 1), (1.2, 0.7, 0.7, 1), (1.8, 1.4, 1.4, 1, 'O'), (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.3, 0.85, (9.0, 0.5), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.6, 3.05, (0.0, 0.0), 1.5)],
         right_holds=[(0.85, 1.32, 46.0)],
         left_plan=[(0.25, 0.6, (-2.0, 0.5), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.6, 3.05, (0.0, 0.0), 1.5)])
    eranim.unsink(c, x.rig, x.v, 0.0, c.length, skip=('antenna_r', 'antenna_l', 'ring'))
    return c


def stagger(x):
    E = 1.5
    c = Clip('stagger', E, impact=0.15)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.14, 0, -1.0, 2.8, 'O'), (0.45, 0, -1.8, 3.4), (0.8, 0, -0.8, 1.8), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.14, -22, 6, 8, 'O'), (0.45, -8, -4, 3), (0.68, 6, 2, -2), (1.0, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.14, -26, 10, 6, 'O'), (0.45, -4, -10, 0), (0.68, 10, 6, 0), (0.95, 0, -3, 0),
                   (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.14, -50, 0, 40, 'O'), (0.45, -22, 0, 18), (0.85, -6, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.14, -38, 0, -50, 'O'), (0.45, -18, 0, -26), (0.85, -4, 0, -6), (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.16, 0.65, 0.15)), 24)
    feet(c, x, right_plan=[(0.12, 0.42, (6.0, 1.0), 2.5), (0.82, 1.18, (0.0, 0.0), 2.0)],
         left_plan=[(0.36, 0.6, (3.0, 0.0), 1.5), (1.02, 1.32, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """The giant reels, sways forward, then topples backwards like a felled statue (floor impact at 1.8 s) and
    crumbles a little."""
    E = 4.0
    c = Clip('death', E, impact=1.8)
    tr = shake(6.0, 1.0, 7)
    c.rot('base', [(0, 0, 0, 0), (0.35, -6, 0, 0, 'O'), (1.0, 6, 0, 2), (1.8, -84, 0, 6, 'I'), (2.05, -78, 0, 5, 'b'),
                   (2.4, -82, 0, 5), (E, -82, 0, 5)])
    c.pos('base', [(0, 0, 0, 0), (0.35, 0, -0.5, 1.5, 'O'), (1.0, 0, -1.8, -0.5), (1.8, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.35, -24, 0, 0, 'O'), (1.0, 12, 0, -4), (1.8, -6, 0, -6, 'I'), (2.05, 4, 0, -6, 'b'),
                    (E, 4, 0, -6)])
    c.scl('torso', [(0, 1, 1, 1), (2.6, 1, 1, 1), (E, 1.06, 0.9, 1.06, 'O')])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.2 * tr(t) * envelope(t, 0.35, 1.0, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.35, -30, 0, 0, 'O'), (1.0, 16, 8, 0), (1.8, -20, 20, 0, 'I'), (2.1, 4, 28, 8, 'b'),
                   (E, 6, 30, 10)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.35, -40, 0, 52, 'O'), (1.0, -10, 0, 20), (1.8, -150, 0, 30, 'I'),
                        (2.1, -144, 0, 36, 'b'), (E, -144, 0, 36)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.35, -36, 0, -50, 'O'), (1.0, -8, 0, -18), (1.8, -140, 0, -34, 'I'),
                       (2.1, -134, 0, -38, 'b'), (2.9, -134, 0, -38), (3.1, -142, 0, -34, 'o'), (3.3, -134, 0, -38),
                       (E, -134, 0, -38)])
    for side in ('right', 'left'):
        c.rot('%s_leg' % side, [(0, 0, 0, 0), (1.0, -12, 0, 0), (1.8, 4, 0, 0, 'I'), (E, 4, 0, 0)])
        c.rot('%s_shin' % side, [(0, 0, 0, 0), (1.0, 18, 0, 0), (1.8, 4, 0, 0, 'I'), (E, 4, 0, 0)])
        c.rot('%s_foot' % side, [(0, 0, 0, 0), (1.0, -6, 0, 0), (1.8, 30, 0, 0, 'I'), (E, 30, 0, 0)])
    c.rot('ring', [(0, 0, 0, 0), (1.8, 0, 0, 160, 'I'), (E, 0, 0, 200, 'O')])
    c.scl('ring', [(0, 1, 1, 1), (2.6, 1, 1, 1), (E, 0.4, 0.4, 1)])
    c.scl('core', [(0, 1, 1, 1), (0.35, 2.2, 2.2, 1.5, 'O'), (1.8, 0.5, 0.5, 1), (E, 0.1, 0.1, 1)])
    for t in (1.8, 2.05, 2.4, E):
        eranim.ground(c, x.rig, x.v, t, skip=('antenna_r', 'antenna_l', 'ring'))
    eranim.unsink(c, x.rig, x.v, 0.0, 1.8, skip=('antenna_r', 'antenna_l', 'ring'))
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

def variants():
    sand = {'base': '#d4b67c', 'style': 'sandstone'}

    def deco_b3(p):
        f = p.face('head', 'front')
        f.rect(1, 3, 3, 4, (30, 22, 12))
        f.rect(6, 3, 8, 4, (30, 22, 12))
        g = p.face('head', 'front', glow=True)
        for x in (1, 2, 3, 6, 7, 8):
            f.px(x, 3, (120, 230, 255))
            g.px(x, 3, (255, 255, 255))
        for x in (2, 7):
            f.fpx(x * f.k + 1, 3 * f.k, (255, 255, 255))
        f.hline(3, 6, 7, (60, 44, 24))
        for part, sides in (('torso', ('front', 'back')), ('right_arm', ('front', 'right')), ('right_forearm', ('front',)),
                            ('right_leg', ('front',)), ('left_arm', ('front', 'left')), ('left_shin', ('front',))):
            for side in sides:
                fc = p.face(part, side)
                gc = p.face(part, side, glow=True)
                for y in range(3, fc.h - 2, 5):
                    for x in range(2, fc.w - 2, 4):
                        glyph = (x * 13 + y * 7) % 4
                        pts = [(0, 0), (1, 0), (0, 1), (1, 2)] if glyph == 0 else [(0, 0), (0, 1), (0, 2), (1, 1)] if glyph == 1 else \
                            [(1, 0), (0, 1), (1, 1), (2, 1)] if glyph == 2 else [(0, 0), (2, 0), (1, 1), (1, 2)]
                        for (dx, dy) in pts:
                            fc.px(x + dx, y + dy, (90, 200, 240))
                            gc.px(x + dx, y + dy, (255, 255, 255))
        for fc in p.all_faces('nemes', index=0, sides_only=True):
            for y in range(0, fc.h, 2):
                fc.hline(0, fc.w - 1, y, (40, 70, 150))
        for side in ('lappet_r', 'lappet_l'):
            for fc in p.all_faces(side, sides_only=True):
                for y in range(0, fc.h, 2):
                    fc.hline(0, fc.w - 1, y, (40, 70, 150))
        nf = p.face('nemes', 'front', index=0)
        for y in range(1, nf.h):
            for x in range(2, nf.w - 2):
                nf.clear(x, y)
    pal3 = {'head': sand, 'torso': sand, 'arm': sand, 'forearm': sand, 'fist': {'base': '#c4a46a', 'style': 'sandstone'},
            'shoulder': {'base': '#c8a868', 'style': 'sandstone'}, 'thigh': sand, 'shin': {'base': '#c4a46a', 'style': 'sandstone'},
            'foot': {'base': '#b89a60', 'style': 'sandstone'},
            'pelvis': sand, 'nemes': {'base': '#d8b040', 'style': 'gold'}, 'lappet': {'base': '#d8b040', 'style': 'gold'},
            'beard': {'base': '#3a50a0', 'style': 'gold'}, 'loin': {'base': '#e8e0c8', 'trim': (60, 90, 170)},
            'loin2': {'base': '#e0d8c0', 'trim': (60, 90, 170)}}
    mech = {'base': '#b8c0c8', 'style': 'mech', 'panel': 5}

    def deco_b7(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (60, 66, 74))
        f.hline(1, f.w - 2, f.h - 2, (40, 44, 50))
        for fc in p.all_faces('visor', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 255, 255))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 255, 255))
        for k in range(8):
            for fc in p.all_faces('ring_s%d' % k, glow=True, sides_only=True):
                for x in range(1, fc.w - 1, 2):
                    fc.px(x, fc.h // 2, (220, 250, 255))
        for part in ('torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
            for fc in p.all_faces(part, sides_only=True):
                gc = p.face(part, fc.face, glow=True)
                for x in range(2, fc.w - 2, 5):
                    for y in range(2, fc.h - 2, 6):
                        gc.px(x, y, (160, 230, 255))
                        fc.px(x, y, (120, 220, 255))
                        gc.fpx(x * fc.k, y * fc.k, (255, 255, 255))
    pal7 = {'head': mech, 'torso': mech, 'arm': mech, 'forearm': {'base': '#a8b0b8', 'style': 'mech', 'panel': 4},
            'fist': {'base': '#8a929c', 'style': 'metal'}, 'shoulder': {'base': '#d8c890', 'style': 'metal'},
            'thigh': mech, 'shin': {'base': '#9aa2ac', 'style': 'mech', 'panel': 4},
            'foot': {'base': '#7a828c', 'style': 'metal'}, 'pelvis': mech,
            'visor': {'base': '#60e0ff', 'style': 'glowcore'}, 'antenna': {'base': '#d8c890', 'style': 'metal'},
            'core': {'base': '#60e0ff', 'style': 'glowcore'}, 'ring': {'base': '#d8c890', 'style': 'metal', 'rivets': False},
            'vent': {'base': '#5a626c', 'style': 'mech', 'panel': 2}}
    pal3.update({'crystal': {'base': '#2e1a44', 'style': 'crystal'}, 'crystal2': {'base': '#3a2252', 'style': 'crystal'},
                 'ring': {'base': '#c8a868', 'style': 'sandstone'}, 'core': {'base': '#ff8a3a', 'style': 'glowcore'},
                 'cannon': mech, 'barrel': {'base': '#5a626c', 'style': 'metal'}, 'visor': '#ff8a3a',
                 'antenna': '#d8b040', 'vent': '#8a6a40'})
    pal7.update({'crystal': {'base': '#2e1a44', 'style': 'crystal'}, 'crystal2': {'base': '#3a2252', 'style': 'crystal'},
                 'cannon': {'base': '#9aa2ac', 'style': 'mech', 'panel': 3}, 'barrel': {'base': '#4a525c', 'style': 'metal'},
                 'nemes': '#d8b040', 'lappet': '#d8b040', 'beard': '#d8b040', 'loin': '#e8e0c8', 'loin2': '#e8e0c8'})
    for key in ('cannon', 'barrel', 'crystal', 'crystal2', 'core', 'visor', 'antenna', 'vent', 'ring'):
        pal3.setdefault(key, '#808080')
    for key in ('nemes', 'lappet', 'beard', 'loin', 'loin2', 'crystal', 'crystal2'):
        pal7.setdefault(key, '#808080')

    def deco_b3_form(form):
        def paint(p):
            deco_b3(p)
            glow_faces(p, 'crystal', (210, 130, 255), every=2)
            glow_faces(p, 'crystal', (210, 130, 255), index=1, every=2)
            g = p.face('head', 'front', glow=True)
            for x in (1, 2, 3, 6, 7, 8):
                p.face('head', 'front').px(x, 3, (255, 120, 40))
                g.px(x, 3, (255, 200, 120))
            cracks(p, ('torso', 'pelvis', 'right_arm', 'left_arm'), (255, 130, 40), (255, 190, 90), every=34 - 6 * form)
            if form >= 2:
                cracks(p, ('right_leg', 'left_leg', 'right_shin', 'left_shin', 'right_forearm', 'left_forearm'),
                       (255, 130, 40), (255, 190, 90), every=30)
                for k in range(8):
                    for fc in p.all_faces('ring_s%d' % k, glow=True, sides_only=True):
                        fc.px(fc.w // 2, fc.h // 2, (255, 170, 80))
        return paint

    def deco_b7_form(form):
        def paint(p):
            deco_b7(p)
            red = (255, 60, 60) if form >= 2 else (255, 170, 60)
            for part in ('torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
                for fc in p.all_faces(part, sides_only=True):
                    gc = p.face(part, fc.face, glow=True)
                    for x in range(4, fc.w - 2, 5):
                        for y in range(5, fc.h - 2, 6):
                            fc.px(x, y, red)
                            gc.px(x, y, red)
            if form >= 2:
                veins(p, ('torso', 'pelvis', 'right_forearm', 'left_forearm'), (200, 40, 50), (255, 120, 120), seed=form)
                for k in range(8):
                    for fc in p.all_faces('ring_s%d' % k, glow=True, sides_only=True):
                        for x in range(0, fc.w, 2):
                            fc.px(x, fc.h // 2, (255, 110, 110))
                for fc in p.all_faces('back_vents', glow=True, sides_only=True):
                    fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 150, 60))
            if form >= 3:
                for part in ('cannon_r', 'cannon_l'):
                    for fc in p.all_faces(part, index=1, glow=True):
                        fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 90, 90))
        return paint
    overload = dict(pal7, visor={'base': '#ff4848', 'style': 'glowcore'}, core={'base': '#ff4848', 'style': 'glowcore'})
    b7_parts = {'visor', 'antenna_r', 'antenna_l', 'core', 'ring', 'back_vents'}
    b3_parts = {'nemes', 'false_beard', 'loincloth', 'crystal'}
    return [Variant('b3', show={'nemes', 'false_beard', 'loincloth'}, palette=pal3, paint=deco_b3, glow=True,
                    extra=dict(scale=3.1, hitbox=(5.6, 10.0))),
            Variant('b7', show=b7_parts, palette=pal7, paint=deco_b7,
                    glow=True, extra=dict(scale=3.0, hitbox=(5.4, 9.8))),
            Variant('b3_p1', show=b3_parts, palette=pal3, paint=deco_b3_form(1), glow=True, extra=dict(scale=3.1)),
            Variant('b3_p2', show=b3_parts | {'ring'}, palette=pal3, poses={'ring': dict(scale=(1.5, 1.5, 1.5))},
                    paint=deco_b3_form(2), glow=True, extra=dict(scale=3.1)),
            Variant('b7_p1', show=b7_parts, palette=pal7, paint=deco_b7_form(1), glow=True, extra=dict(scale=3.0)),
            Variant('b7_p2', show=b7_parts, palette=overload, paint=deco_b7_form(2), glow=True, extra=dict(scale=3.0)),
            Variant('b7_p3', show=b7_parts | {'cannon_r', 'cannon_l'}, palette=overload,
                    poses={'core': dict(scale=(1.5, 1.5, 1.0))}, paint=deco_b7_form(3), glow=True,
                    extra=dict(scale=3.0))]


TRAILS = [('right_hand', (-13.5, 7.0, -5.0), (-13.5, 11.0, -5.0)), ('left_hand', (15.5, 7.0, -5.0), (15.5, 11.0, -5.0))]


def rig():
    vs = variants()
    base = Rig('colossus', 'ColossusBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('colossus', 'ColossusBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.4
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
