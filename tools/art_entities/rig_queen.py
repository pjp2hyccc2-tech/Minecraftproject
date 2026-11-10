"""QUEEN boss rig (tall humanoid): b5 氷獄の女王 (floating gown with a trailing train, crystal crown, long hair in
two segments, ice scepter, ice collar, orbiting shards, ice wings in later forms) and b4 凍獄の処刑人 (hooded
executioner with a floppy hood tip, legs with feet planted by IK, a two-segment leather apron, pauldrons and a
two-handed axe). Hands of their own carry the scepter and the axe. Two animation sets on one skeleton: the queen
glides, twirls and casts; the executioner stomps, chops and drags his chains."""
import math

import eranim
from boss_forms import cracks, glow_faces, veins
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from ermodel import C, P, Rig, Variant
from erpaint import human_face, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
GEM = (-8.0, -18.0, 0.0)            # scepter gem (rest coordinates of part scepter)
STAFF_FOOT = (-8.0, 10.5, 0.0)
AXE_EDGE = (-8.0, 7.0, -22.5)        # cutting edge of the axe head (part axe)
AXE_GRIP = (-8.0, 5.0, -6.0)
PALM_L = (8.0, 6.5, -1.0)


def geometry():
    head = P('head', (0, -13, -0.5), C(-4, -21, -4.5, 8, 8, 8, 'skin:face'),
             P('crown', (0, -21, -0.5), C(-4, -24, -3.5, 1, 3, 1, 'crystal:crown', tag='c1'), C(-2.5, -26, -3.5, 1, 5, 1, 'crystal:crown', tag='c2'),
               C(-0.5, -29, -3.8, 1, 8, 1, 'crystal:crown_c'), C(1.5, -26, -3.5, 1, 5, 1, 'crystal:crown', share='c2'),
               C(3, -24, -3.5, 1, 3, 1, 'crystal:crown', share='c1'), opt=True),
             P('hair', (0, -21, 0), C(-4.5, -21.5, -1, 9, 8, 5, 'hair:hair'),
               P('hair_lower', (0, -13.5, 1.5), C(-4.5, -13.5, -1, 9, 8, 5, 'hair:hair2')), rot=(6, 0, 0), opt=True),
             P('hood', (0, -17, -0.5), C(-4, -21, -4.5, 8, 8, 8, 'cloth:hood', inflate=0.6),
               P('hood_tip', (0, -21, 0), C(-2, -25, -2, 4, 4, 4, 'cloth:hood_tip')), opt=True),
             P('ice_horns', (0, -21, -0.5), C(-5, -27, -1, 1, 6, 1, 'crystal:ice_horn', tag='ih'),
               C(4, -27, -1, 1, 6, 1, 'crystal:ice_horn', share='ih', mirror=True),
               C(-6, -25, -1, 1, 3, 1, 'crystal:ice_horn2', tag='ih2'),
               C(5, -25, -1, 1, 3, 1, 'crystal:ice_horn2', share='ih2', mirror=True), opt=True))
    scepter = P('scepter', (-8, 5, 0), C(-8.5, -15, -0.5, 1, 26, 1, 'crystal:staff'), C(-9.5, -21, -1.5, 3, 6, 3, 'crystal:gem'),
                opt=True)
    axe = P('axe', (-8, 5, 0), C(-8.5, 4.5, -18, 1, 1, 22, 'wood:haft'), C(-9, 2, -22, 2, 11, 6, 'metal:axe'),
            C(-8.5, 1, -23, 1, 2, 1, 'metal:axe_tip'), rot=(40, 0, 0), opt=True)
    right_hand = P('right_hand', (-8, 3, 0), C(-9.5, 3, -1.5, 3, 3, 3, 'skin:hand', tag='hand'), scepter, axe)
    left_hand = P('left_hand', (8, 3, 0), C(6.5, 3, -1.5, 3, 3, 3, 'skin:hand', share='hand', mirror=True))
    right_arm = P('right_arm', (-7.5, -11, 0), C(-10, -13, -2, 4, 10, 4, 'cloth:sleeve', tag='ua'),
                  P('pauldron_r', (-8, -13, 0), C(-11, -14.5, -3, 6, 4, 6, 'metal:pauldron', tag='paul'), opt=True),
                  P('right_forearm', (-8, -3, 0), C(-10, -3, -2, 4, 6, 4, 'skin:arm', tag='fa'),
                    P('sleeve_r', (-8, 1, 0), C(-11, 0, -3, 6, 4, 6, 'cloth:cuff', tag='cuff'), opt=True), right_hand,
                    rot=(-12, 0, 0)), rot=(-6, 0, 5))
    left_arm = P('left_arm', (7.5, -11, 0), C(6, -13, -2, 4, 10, 4, 'cloth:sleeve', share='ua', mirror=True),
                 P('pauldron_l', (8, -13, 0), C(5, -14.5, -3, 6, 4, 6, 'metal:pauldron', share='paul', mirror=True), opt=True),
                 P('left_forearm', (8, -3, 0), C(6, -3, -2, 4, 6, 4, 'skin:arm', share='fa', mirror=True),
                   P('sleeve_l', (8, 1, 0), C(5, 0, -3, 6, 4, 6, 'cloth:cuff', share='cuff', mirror=True), opt=True), left_hand,
                   rot=(-16, 0, 0)), rot=(-4, 0, -5))
    shards = P('shard_ring', (0, -6, 0), C(-14, -9, -1, 2, 5, 2, 'crystal:shard', tag='sh'), C(12, -11, -1, 2, 5, 2, 'crystal:shard', share='sh'),
               C(-1, -10, 12, 2, 5, 2, 'crystal:shard', share='sh'), opt=True)
    wings = P('ice_wings', (0, -9, 3.5),
              P('wing_r', (-2, -10, 3.5), C(-15, -24, 3.5, 13, 20, 1, 'crystal:wing', tag='wg'), rot=(0, 25, -12)),
              P('wing_l', (2, -10, 3.5), C(2, -24, 3.5, 13, 20, 1, 'crystal:wing', share='wg', mirror=True),
                rot=(0, -25, 12)), opt=True)
    torso = P('torso', (0, 1, 0), C(-6, -13, -3.5, 12, 14, 7, 'cloth:bodice'),
              head, right_arm, left_arm, shards, wings,
              P('collar', (0, -13, 2), C(-6, -20, 1.5, 12, 7, 1, 'crystal:collar'), rot=(-15, 0, 0), opt=True))
    legs = []
    for s in (-1, 1):
        name = 'right' if s < 0 else 'left'
        kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x = 2.5 * s
        legs.append(P('%s_leg' % name, (x, 6, 0), C(x - 2.5, 5, -2.5, 5, 9, 5, 'cloth:pants', **kw('thigh')),
                      P('%s_shin' % name, (x, 14, 0), C(x - 2.5, 14, -2.5, 5, 7, 5, 'leather:boots', **kw('shin')),
                        P('%s_foot' % name, (x, 21, 0), C(x - 2.5, 21, -3.5, 5, 3, 6, 'leather:boot_foot', **kw('bfoot')))),
                      opt=True))
    hips = P('hips', (0, 4, 0), C(-5, 1, -3, 10, 5, 6, 'cloth:waist'), torso, *legs,
             P('gown', (0, 5, 0), C(-6, 5, -4, 12, 8, 8, 'cloth:gown'),
               P('gown_lower', (0, 13, 0), C(-8, 13, -6, 16, 9, 12, 'cloth:gown2'),
                 P('train', (0, 14, 6), C(-6, 14, 6, 12, 9, 1, 'cloth:train'), rot=(14, 0, 0))), opt=True),
             P('apron', (0, 5, -3), C(-4.5, 5, -3.6, 9, 7, 1, 'leather:apron'),
               P('apron_lower', (0, 12, -3.1), C(-4, 12, -3.6, 8, 6, 1, 'leather:apron2')), opt=True))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS_Q = [
    Chain(['hair', 'hair_lower'], rest='down', axes='xz', hang=0.8, freq=1.35, damp=0.3, inertia=8.0, falloff=1.15,
          limits={'x': (-10.0, 80.0), 'z': (-35.0, 35.0)},
          drag={'q_walk': (16.0, 0.0, 0.0), 'q_charge': (30.0, 0.0, 0.0), 'q_roar': (22.0, 0.0, 0.0)}),
    Chain(['gown_lower'], rest='down', axes='xz', hang=0.55, freq=1.5, damp=0.4, inertia=5.0,
          limits={'x': (-25.0, 30.0), 'z': (-18.0, 18.0)}, drag={'q_walk': (10.0, 0.0, 0.0), 'q_charge': (20.0, 0.0, 0.0)}),
    Chain(['train'], rest='down', axes='xz', hang=0.85, freq=1.2, damp=0.3, inertia=8.0,
          limits={'x': (-5.0, 80.0), 'z': (-30.0, 30.0)}, drag={'q_walk': (22.0, 0.0, 0.0), 'q_charge': (35.0, 0.0, 0.0)}),
]
CHAINS_X = [
    Chain(['apron', 'apron_lower'], rest='down', axes='xz', hang=0.82, freq=1.6, damp=0.34, inertia=7.0,
          limits={'x': (-70.0, 15.0), 'z': (-25.0, 25.0)}, drag={'x_walk': (-6.0, 0.0, 0.0)}),
    Chain(['hood_tip'], rest='up', axes='xz', hang=0.0, freq=2.2, damp=0.25, inertia=12.0,
          limits={'x': (-40.0, 40.0), 'z': (-40.0, 40.0)}),
]
GAIT_X = Gait(1.6, stride=13.0, duty=0.62, lift=3.2, heel=10.0, toe=16.0)
HOVER = 1.4


# --------------------------------------------------------------------------------------------- the queen

def q_idle(x):
    T = 3.2
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.0, HOVER + 0.7 * math.sin(2 * math.pi * t / T), 0.0), 16)
    c.fn('base', 'rot', lambda t: (0.0, 4.0 * math.sin(2 * math.pi * t / T * 0.5 * 2), 0.0), 16)
    c.fn('torso', 'rot', lambda t: (-1.5 * b(t), 0.0, 1.0 * math.sin(2 * math.pi * t / T + 1.0)), 16)
    c.fn('head', 'rot', lambda t: (2.0 * math.sin(2 * math.pi * t / T + 0.5), 0.0, -3.0 * math.sin(2 * math.pi * t / T)), 16)
    c.fn('right_arm', 'rot', lambda t: (-4.0 - 2.0 * b(t), 0.0, 3.0 + 1.5 * b(t)), 12)
    c.fn('right_forearm', 'rot', lambda t: (-10.0 - 3.0 * b(t - 0.2), 0.0, 0.0), 12)
    c.fn('right_hand', 'rot', lambda t: (12.0 + 3.0 * math.sin(2 * math.pi * t / T), 0.0, 0.0), 12)
    c.fn('left_arm', 'rot', lambda t: (-10.0 - 3.0 * b(t - 0.3), 0.0, -12.0 - 3.0 * b(t)), 12)
    c.fn('left_forearm', 'rot', lambda t: (-38.0 - 6.0 * b(t - 0.4), 0.0, 0.0), 12)
    c.fn('left_hand', 'rot', lambda t: (-20.0 + 8.0 * math.sin(2 * math.pi * t / T + 1.6), 0.0, 0.0), 12)
    c.fn('gown_lower', 'rot', lambda t: (1.5 * math.sin(2 * math.pi * t / T + 0.6), 0.0, 2.0 * math.sin(2 * math.pi * t / T)), 16)
    c.rot('shard_ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 120, 0, 'l')])
    c.fn('shard_ring', 'pos', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T * 2), 0.0), 16)
    c.fn('ice_wings', 'rot', lambda t: (0.0, 0.0, 0.0), 2)
    c.fn('wing_r', 'rot', lambda t: (0.0, -6.0 * b(t), 0.0), 12)
    c.fn('wing_l', 'rot', lambda t: (0.0, 6.0 * b(t), 0.0), 12)
    return c


def q_walk(x):
    T = 1.8
    c = Clip('q_walk', T, loop=True)
    c.fn('base', 'pos', lambda t: (0.0, HOVER + 0.5 * math.sin(2 * math.pi * t / T), 0.0), 12)
    c.fn('torso', 'rot', lambda t: (9.0 + 1.0 * math.sin(2 * math.pi * t / T), 2.0 * math.sin(2 * math.pi * t / T), 0.0), 12)
    c.fn('head', 'rot', lambda t: (-6.0, 0.0, 0.0), 2)
    c.fn('right_arm', 'rot', lambda t: (10.0, 0.0, 10.0), 2)
    c.fn('left_arm', 'rot', lambda t: (14.0, 0.0, -14.0), 2)
    c.fn('gown', 'rot', lambda t: (-5.0, 0.0, 2.0 * math.sin(2 * math.pi * t / T)), 12)
    c.rot('shard_ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 120, 0, 'l')])
    c.footsteps = []
    return c


def q_slam(x):
    """Rises high on the frost, both hands on the scepter, then drives its foot into the ice below."""
    A, B, I, F, R, E = 0.15, 0.75, 1.0, 1.2, 1.55, 2.1
    c = Clip('q_slam', E, impact=I)
    c.pos('base', [(0, 0, HOVER, 0), (A, 0, HOVER - 0.8, 0), (B, 0, HOVER + 4.5, 1.2), (I, 0, HOVER - 1.4, -1.6, 'I'),
                   (F, 0, HOVER - 1.8, -1.8, 'O'), (R, 0, HOVER - 0.6, -0.8), (E, 0, HOVER, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -18, 0, 0), (I, 24, 0, 0, 'I'), (F, 28, 0, 0, 'O'), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 0), (B, -166, 0, -4), (I, -42, 0, -4, 'I'), (F, -36, 0, -4, 'O'),
                        (R, -30, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 8, 0, 0), (B, -160, 0, 16), (I, -40, 0, 18, 'I'), (F, -36, 0, 18, 'O'),
                       (R, -26, 0, 8), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 70, 0, 0), (I, 70, 0, 0), (E, 0, 0, 0)])
    c.rot('gown_lower', [(0, 0, 0, 0), (B, -14, 0, 0), (I, 18, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('shard_ring', [(0, 0, 0, 0, 'l'), (B, 0, 120, 0, 'i'), (I, 0, 300, 0, 'O'), (E, 0, 360, 0, 's')])
    c.scl('shard_ring', [(0, 1, 1, 1), (B, 0.7, 1, 0.7), (I, 1.5, 1, 1.5, 'O'), (E, 1, 1, 1)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'scepter', STAFF_FOOT, 24.0, -90.0, 0.0)
    return c


def q_sweep(x):
    """A whirling sweep of the scepter: she spins on the frost, the gown flaring out."""
    A, B, I, F, R, E = 0.12, 0.65, 0.9, 1.15, 1.45, 1.9
    c = Clip('q_sweep', E, impact=I)
    c.pos('base', [(0, 0, HOVER, 0), (B, 0, HOVER + 1.0, 0.6), (I, 0, HOVER - 0.4, -1.0, 'I'), (E, 0, HOVER, 0)])
    c.rot('base', [(0, 0, 0, 0), (A, 0, -8, 0), (B, 0, 50, 0), (I, 0, -30, 0, 'I'), (F, 0, -70, 0, 'O'), (R, 0, -40, 0),
                   (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 6, 20, -6), (I, 8, -10, 6, 'I'), (F, 6, -20, 4, 'b'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 0, -40, 0), (I, 0, 20, 0, 'I'), (F, 0, 40, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -70, 70, 10), (I, -84, -10, 0, 'I'), (F, -78, -60, 0, 'O'), (R, -40, -30, 0),
                        (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 80, 0, 0), (I, 88, 0, 0, 'I'), (F, 80, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -50), (I, -20, 0, -70, 'I'), (F, -10, 0, -60), (E, 0, 0, 0)])
    c.rot('gown_lower', [(0, 0, 0, 0), (B, 0, 0, 12), (I, 0, 0, -16, 'I'), (F, 0, 0, -22, 'b'), (E, 0, 0, 0)])
    c.scl('gown_lower', [(0, 1, 1, 1), (I, 1.18, 0.92, 1.18, 'O'), (F, 1.22, 0.9, 1.22), (E, 1, 1, 1)])
    c.rot('shard_ring', [(0, 0, 0, 0, 'l'), (E, 0, 360, 0, 's')])
    for t, elevation in ((B, 4.0), (I, -2.0), (F, 0.0)):
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'scepter', STAFF_FOOT, GEM, elevation, -40.0, 160.0)
    return c


def q_cast(x):
    """Gathers the cold at her heart, then levels the scepter and releases it (projectiles leave from the gem)."""
    A, B, I, F, R, E = 0.12, 0.7, 0.95, 1.25, 1.55, 2.0
    c = Clip('q_cast', E, impact=I)
    c.pos('base', [(0, 0, HOVER, 0), (B, 0, HOVER + 1.5, 0.8), (I, 0, HOVER + 0.4, -1.2, 'I'), (F, 0, HOVER + 0.2, -1.4, 'O'),
                   (E, 0, HOVER, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -14, -12, 0), (I, 12, 10, 0, 'I'), (F, 14, 12, 0, 'b'), (R, 6, 4, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -18, 8, 0), (I, 0, -8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -40, 0, -28), (I, -92, 0, 4, 'I'), (F, -98, 0, 4, 'b'), (R, -70, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -60, 0, 0), (I, 8, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 30, 0, 0), (I, 90, 0, 0, 'I'), (F, 88, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -50, 0, 26), (I, -60, 0, -30, 'I'), (F, -56, 0, -34), (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -40, 0, 0), (I, -70, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('shard_ring', [(0, 0, 0, 0, 'l'), (B, 0, 200, 0, 'i'), (I, 0, 480, 0, 'O'), (E, 0, 600, 0, 's')])
    c.scl('shard_ring', [(0, 1, 1, 1), (B, 0.6, 1, 0.6), (I, 1.4, 1, 1.4, 'O'), (E, 1, 1, 1)])
    c.rot('wing_r', [(0, 0, 0, 0), (B, 0, 14, 0), (I, 0, -10, 0, 'I'), (E, 0, 0, 0)])
    c.rot('wing_l', [(0, 0, 0, 0), (B, 0, -14, 0), (I, 0, 10, 0, 'I'), (E, 0, 0, 0)])
    for t, elevation in ((I, 6.0), (F, 10.0)):            # the gem is levelled at the target on the release
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'scepter', STAFF_FOOT, GEM, elevation, -180.0, 180.0,
                   forward=True)
    c.muzzle_part, c.muzzle_point = 'scepter', GEM
    return eranim.retime(c, 0.8)


def q_roar(x):
    T = 1.2
    c = Clip('q_roar', T, loop=True)
    tr, tr2 = shake(7.0, 1.0, 1), shake(8.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, HOVER + 2.6 + 0.4 * math.sin(2 * math.pi * t / T), 0.0), 12)
    c.fn('torso', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.4 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-22.0 + 1.2 * tr2(t), 0.0, 0.0), 24)
    c.fn('right_arm', 'rot', lambda t: (-35.0 + 1.5 * tr(t), 0.0, 70.0 + 2.0 * tr2(t)), 12)
    c.fn('left_arm', 'rot', lambda t: (-35.0 + 1.5 * tr2(t), 0.0, -70.0 - 2.0 * tr(t)), 12)
    c.fn('left_hand', 'rot', lambda t: (-40.0, 0.0, 0.0), 2)
    c.fn('gown_lower', 'scale', lambda t: (1.1 + 0.04 * math.sin(4 * math.pi * t / T), 1.0,
                                           1.1 + 0.04 * math.sin(4 * math.pi * t / T)), 12)
    c.rot('shard_ring', [(0.0, 0, 0, 0, 'l'), (T, 0, 240, 0, 'l')])
    c.fn('wing_r', 'rot', lambda t: (0.0, 18.0 + 6.0 * math.sin(2 * math.pi * t / T), 0.0), 12)
    c.fn('wing_l', 'rot', lambda t: (0.0, -18.0 - 6.0 * math.sin(2 * math.pi * t / T), 0.0), 12)
    return c


def q_charge(x):
    A, B, I, F, R, E = 0.1, 0.55, 0.8, 1.0, 1.25, 1.6
    c = Clip('q_charge', E, impact=I)
    c.pos('base', [(0, 0, HOVER, 0), (B, 0, HOVER + 1.2, 2.0), (I, 0, HOVER + 0.4, -7.0, 'I'), (F, 0, HOVER + 0.2, -8.0, 'O'),
                   (R, 0, HOVER + 0.4, -5.0), (E, 0, HOVER, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -10, 16, 0), (I, 26, -6, 0, 'I'), (F, 28, -8, 0, 'b'), (R, 14, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 0, -14, 0), (I, -20, 6, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 30, 0, 14), (I, -84, 0, 4, 'I'), (F, -88, 0, 4, 'b'), (R, -60, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 60, 0, 0), (I, 92, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -20), (I, 30, 0, -24, 'I'), (E, 0, 0, 0)])
    c.rot('gown_lower', [(0, 0, 0, 0), (B, -10, 0, 0), (I, -28, 0, 0, 'I'), (R, -12, 0, 0), (E, 0, 0, 0)])
    for t, elevation in ((B, 4.0), (I, 0.0), (F, -2.0)):     # couched like a lance
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'scepter', STAFF_FOOT, GEM, elevation, -180.0, 180.0,
                   forward=True)
    return c


def q_summon(x):
    A, B, Cc, I, F, R, E = 0.15, 0.8, 0.95, 1.1, 1.3, 1.7, 2.2
    c = Clip('q_summon', E, impact=I)
    c.pos('base', [(0, 0, HOVER, 0), (B, 0, HOVER + 3.4, 0.4), (Cc, 0, HOVER + 3.8, 0.4, 'o'), (I, 0, HOVER - 0.6, -0.4, 'I'),
                   (F, 0, HOVER - 0.8, -0.4, 'O'), (R, 0, HOVER, 0), (E, 0, HOVER, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 16, 0, 0, 'I'), (F, 18, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 14, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -172, 0, 6), (Cc, -176, 0, 6), (I, -40, 0, 34, 'I'), (F, -36, 0, 38, 'O'),
                        (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -150, 0, -30), (Cc, -154, 0, -30), (I, -40, 0, -34, 'I'), (F, -36, 0, -38, 'O'),
                       (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 60, 0, 0), (I, 40, 0, 0), (E, 0, 0, 0)])
    c.pos('shard_ring', [(0, 0, 0, 0), (Cc, 0, 7, 0), (I, 0, -4, 0, 'I'), (E, 0, 0, 0)])
    c.scl('shard_ring', [(0, 1, 1, 1), (Cc, 0.6, 1, 0.6), (I, 1.8, 1, 1.8, 'O'), (E, 1, 1, 1)])
    c.rot('wing_r', [(0, 0, 0, 0), (Cc, 0, 30, 0), (I, 0, -20, 0, 'I'), (E, 0, 0, 0)])
    c.rot('wing_l', [(0, 0, 0, 0), (Cc, 0, -30, 0), (I, 0, 20, 0, 'I'), (E, 0, 0, 0)])
    return c


def q_phase(x):
    E, I = 3.0, 1.8
    c = Clip('q_phase', E, impact=I)
    tr = shake(8.0, 1.0, 3)
    hold = lambda t: envelope(t, 1.8, 2.45, 0.2)
    c.pos('base', [(0, 0, HOVER, 0), (0.25, 0, HOVER - 0.4, 1.2, 'O'), (1.3, 0, HOVER + 4.5, 0), (1.8, 0, HOVER + 3.6, 0, 'O'),
                   (2.45, 0, HOVER + 3.2, 0), (E, 0, HOVER, 0)])
    c.rot('base', [(0, 0, 0, 0), (0.3, 0, 0, 0), (1.3, 0, 300, 0, 'i'), (1.8, 0, 360, 0, 'O'), (E, 0, 360, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.25, -14, 0, 0, 'O'), (1.3, 26, 0, 0), (1.8, -24, 0, 0, 'O'), (2.45, -20, 0, 0),
                    (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * hold(t)), 36)
    c.rot('head', [(0, 0, 0, 0), (1.3, 24, 0, 0), (1.8, -34, 0, 0, 'O'), (2.45, -28, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (1.3, -60, 0, -30), (1.8, -40, 0, 96, 'O'), (2.45, -38, 0, 90), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (1.3, -60, 0, 30), (1.8, -40, 0, -96, 'O'), (2.45, -38, 0, -90), (E, 0, 0, 0)])
    c.scl('gown_lower', [(0, 1, 1, 1), (1.3, 0.85, 1.05, 0.85), (1.8, 1.4, 0.9, 1.4, 'O'), (2.45, 1.3, 0.92, 1.3),
                         (E, 1, 1, 1)])
    c.scl('shard_ring', [(0, 1, 1, 1), (1.3, 0.5, 1, 0.5), (1.8, 1.9, 1, 1.9, 'O'), (E, 1, 1, 1)])
    c.rot('shard_ring', [(0, 0, 0, 0, 'l'), (1.8, 0, 540, 0, 'O'), (E, 0, 720, 0, 's')])
    c.rot('wing_r', [(0, 0, 0, 0), (1.3, 0, -30, 0), (1.8, 0, 40, 0, 'O'), (2.45, 0, 34, 0), (E, 0, 0, 0)])
    c.rot('wing_l', [(0, 0, 0, 0), (1.3, 0, 30, 0), (1.8, 0, -40, 0, 'O'), (2.45, 0, -34, 0), (E, 0, 0, 0)])
    return c


def q_stagger(x):
    E = 1.3
    c = Clip('q_stagger', E, impact=0.1)
    c.pos('base', [(0, 0, HOVER, 0), (0.12, 0, HOVER - 1.2, 2.6, 'O'), (0.5, 0, HOVER - 0.8, 3.0), (E, 0, HOVER, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.12, -22, 0, 7, 'O'), (0.5, -10, 0, 3), (0.8, 4, 0, -1), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.12, -28, 10, 0, 'O'), (0.5, -6, -6, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.12, -50, 0, 44, 'O'), (0.6, -16, 0, 14), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.12, -40, 0, -50, 'O'), (0.6, -12, 0, -16), (E, 0, 0, 0)])
    c.rot('gown_lower', [(0, 0, 0, 0), (0.12, 16, 0, 0, 'O'), (0.6, 4, 0, 0), (E, 0, 0, 0)])
    return c


def q_death(x):
    """She shudders, rises one last time with her arms open to the cold, then sinks; the gown collapses on the floor
    at 1.8 s and she kneels inside it, head bowed."""
    E = 3.6
    c = Clip('q_death', E, impact=1.8)
    tr = shake(7.0, 1.0, 7)
    c.pos('base', [(0, 0, HOVER, 0), (0.3, 0, HOVER - 0.6, 1.4, 'O'), (1.2, 0, HOVER + 3.0, 0.4), (1.8, 0, -5.0, -0.5, 'I'),
                   (2.0, 0, -4.6, -0.5, 'b'), (E, 0, -4.8, -0.5)])
    c.scl('gown_lower', [(0, 1, 1, 1), (1.2, 0.9, 1.05, 0.9), (1.8, 1.45, 0.5, 1.45, 'I'), (2.0, 1.4, 0.55, 1.4, 'b'),
                         (E, 1.42, 0.52, 1.42)])
    c.scl('gown', [(0, 1, 1, 1), (1.6, 1, 1, 1), (1.8, 1.15, 0.6, 1.15, 'I'), (E, 1.15, 0.6, 1.15)])
    c.rot('torso', [(0, 0, 0, 0), (0.3, -24, 0, 0, 'O'), (1.2, -30, 0, 0), (1.8, 26, 0, 0, 'I'), (2.1, 22, 0, 4, 'b'),
                    (E, 24, 0, 4)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.3, 1.2, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.3, -34, 0, 0, 'O'), (1.2, -40, 0, 0), (1.8, 30, 0, 0, 'I'), (2.2, 36, 6, 8),
                   (E, 36, 6, 8)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.3, -40, 0, 50, 'O'), (1.2, -150, 0, 40), (1.8, -20, 0, 26, 'I'),
                        (2.2, -10, 0, 20), (E, -10, 0, 20)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.3, -36, 0, -50, 'O'), (1.2, -150, 0, -40), (1.8, -20, 0, -26, 'I'),
                       (2.2, -10, 0, -20), (E, -10, 0, -20)])
    c.pos('shard_ring', [(0, 0, 0, 0), (1.2, 0, 4, 0), (1.8, 0, 2, 0), (2.4, 0, -9, 0, 'i'), (2.6, 0, -8.5, 0, 'b'),
                         (E, 0, -9, 0)])
    c.scl('shard_ring', [(0, 1, 1, 1), (1.2, 1.8, 1, 1.8), (2.4, 2.4, 1, 2.4), (E, 2.4, 1, 2.4)])
    c.rot('wing_r', [(0, 0, 0, 0), (1.2, 0, 30, 0), (1.8, 0, -20, -20, 'I'), (E, 0, -20, -24)])
    c.rot('wing_l', [(0, 0, 0, 0), (1.2, 0, -30, 0), (1.8, 0, 20, 20, 'I'), (E, 0, 20, 24)])
    for t in (1.8, 2.0, E):          # the collapsed gown lies on the floor
        eranim.ground(c, x.rig, x.v, t, skip=('shard_ring', 'wing_r', 'wing_l', 'train'))
    return c


# --------------------------------------------------------------------------------------- the executioner

def x_idle(x):
    T = 3.6
    c = Clip('x_idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.5)
    c.fn('base', 'pos', lambda t: (0.5 * sway(t), 0.45 * b(t) - 0.6, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.0 * math.sin(2 * math.pi * t / T + 1.2), 1.2 * sway(t)), 16)
    c.fn('torso', 'rot', lambda t: (5.0 - 3.0 * b(t), -0.8 * math.sin(2 * math.pi * t / T + 1.2), -1.0 * sway(t)), 16)
    c.fn('torso', 'scale', lambda t: (1.0 + 0.03 * b(t), 1.0 + 0.02 * b(t), 1.0 + 0.04 * b(t)), 16)
    c.fn('head', 'rot', lambda t: (6.0 - 2.0 * b(t - 0.3), 4.0 * math.sin(2 * math.pi * t / T * 0.5 * 2 + 0.4), 3.0), 16)
    c.fn('right_arm', 'rot', lambda t: (-5.0 - 2.0 * b(t), 0.0, 5.0 + 2.0 * b(t - 0.2)), 12)
    c.fn('right_forearm', 'rot', lambda t: (-15.0 - 3.0 * b(t - 0.3), 0.0, 0.0), 12)
    c.fn('left_arm', 'rot', lambda t: (-2.0 * b(t), 0.0, -6.0 - 2.0 * b(t - 0.2)), 12)
    c.fn('left_forearm', 'rot', lambda t: (-10.0 - 4.0 * b(t - 0.3), 0.0, 0.0), 12)
    twitch = [(0.0, 0.0, 's'), (2.0, 0.0, 's'), (2.2, 10.0, 'O'), (2.5, -3.0, 's'), (2.8, 0.0, 's'), (T, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(twitch, t), 0.0, 0.0), 30)
    feet(c, x, right_plan=[(-1.0, -1.0, (1.5, 1.0), 0.0)], left_plan=[(-1.0, -1.0, (-1.5, 1.0), 0.0)], samples=16)
    return c


def x_walk(x):
    T = GAIT_X.length
    c = Clip('x_walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-0.7 * w(t, 1, 0.25), -0.5 - 0.8 * w(t, 2, 0.07), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.2 * w(t, 2, 0.1), -7.0 * w(t), 4.0 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (8.0 + 1.2 * w(t, 2, 0.12), 6.0 * w(t, 1, 0.04), -2.5 * s1(t - 0.04)), 24)
    c.fn('head', 'rot', lambda t: (-2.0 - 1.0 * w(t, 2, 0.16), -4.0 * w(t, 1, 0.06), 1.5 * s1(t - 0.08)), 24)
    c.fn('right_arm', 'rot', lambda t: (6.0 * w(t, 1, 0.07), 0.0, 3.0), 24)
    c.fn('right_forearm', 'rot', lambda t: (-4.0 - 3.0 * w(t, 1, 0.5), 0.0, 0.0), 24)
    c.fn('left_arm', 'rot', lambda t: (-18.0 * w(t, 1, 0.07), 0.0, -4.0), 24)
    c.fn('left_forearm', 'rot', lambda t: (-8.0 - 10.0 * max(0.0, -w(t, 1, 0.5)), 0.0, 0.0), 24)
    gait(c, x, GAIT_X)
    return c


def x_slam(x):
    """The execution: the axe rises over the hood with both hands, hangs a heartbeat, then falls into the floor."""
    A, B, Cc, I, F, R, E = 0.14, 0.75, 0.92, 1.1, 1.28, 1.65, 2.2
    c = Clip('x_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, -0.3), (B, 0, 1.0, 1.4), (Cc, 0, 1.2, 1.6, 'o'), (I, 0, -3.0, -2.2, 'I'),
                   (F, 0, -3.4, -2.4, 'O'), (R, 0, -2.8, -1.8), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -6, 4, 0), (I, 10, -2, 0, 'I'), (F, 12, -2, 0, 'O'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 5, 0, 0), (B, -24, -6, 0), (Cc, -27, -6, 0, 'o'), (I, 26, 3, 0, 'I'),
                    (F, 30, 3, 0, 'O'), (R, 20, 1, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 12, 0, 0), (B, -160, 0, 6), (Cc, -168, 0, 8, 'o'), (I, -40, 0, 2, 'I'),
                        (F, -34, 0, 2, 'O'), (R, -36, 0, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 16, 0, 0, 'I'), (F, 18, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -30, 0, 0), (Cc, -36, 0, 0, 'o'), (I, 6, 0, 0, 'I'), (F, 2, 0, 0, 'b'),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 10, 0, 0), (B, -150, 0, 22), (Cc, -158, 0, 24, 'o'), (I, -42, 0, 16, 'I'),
                       (F, -36, 0, 16, 'O'), (R, -38, 0, 10), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -20, 0, 0), (I, 6, 0, 0, 'I'), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.16, 0.5, (5.0, 0.6), 2.5), (1.72, 2.08, (0.0, 0.0), 2.0)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'right_arm', 'x', 'axe', AXE_EDGE, 23.6, -80.0, -10.0)
    return c


def x_sweep(x):
    """Beheading sweep: the axe swings flat through the front."""
    A, B, I, F, R, E = 0.1, 0.72, 1.0, 1.25, 1.55, 2.0
    c = Clip('x_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0.3, -0.5, 0), (B, -1.2, -1.5, 0.8), (I, 0.7, -1.9, -1.6, 'I'),
                   (F, 1.4, -1.7, -2.0, 'O'), (R, 0.8, -1.1, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 20, -3), (I, 2, -8, 3, 'I'), (F, 2, -22, 3, 'b'), (R, 1, -14, 1), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 0, -5, 0), (B, 4, 40, -5), (I, 8, -16, 3, 'I'), (F, 6, -44, 4, 'b'),
                    (R, 3, -28, 2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 2, -34, 0), (I, 0, 8, 0, 'I'), (F, -2, 28, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 6, 0, -3), (B, -72, 78, 2), (I, -80, -6, 2, 'I'), (F, -74, -62, 0, 'O'),
                        (R, -40, -30, 0), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 12, 0, 0, 'I'), (F, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -70, 50, 10), (I, -82, -10, 20, 'I'), (F, -76, -54, 20, 'O'), (R, -40, -24, 8),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.78, 0.97, (-5.0, 1.5), 2.5), (1.55, 1.9, (0.0, 0.0), 2.0)],
         right_plan=[(0.12, 0.45, (2.0, 1.0), 1.5), (1.6, 1.92, (0.0, 0.0), 1.5)])
    for t, elevation in ((B, 6.0), (I, -6.0), (F, -4.0)):
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'axe', AXE_GRIP, AXE_EDGE, elevation, -80.0, 140.0)
    return c


def x_cast(x):
    """Hurls a shackle of ice with the left hand while the axe hangs back."""
    A, B, I, F, R, E = 0.12, 0.7, 0.95, 1.2, 1.5, 2.0
    c = Clip('x_cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0.5, -0.4, 1.0), (I, -0.4, -1.4, -1.6, 'I'), (F, -0.5, -1.6, -1.8, 'O'), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -10, -30, 0), (I, 12, 22, 0, 'I'), (F, 14, 26, 0, 'b'), (R, 6, 10, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -6, 20, 0), (I, 4, -14, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -150, 0, -30), (I, -84, 0, 4, 'I'), (F, -90, 0, 2, 'b'), (R, -60, 0, 0),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -50, 0, 0), (I, 2, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -40, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 20, 0, 14), (I, 10, 0, 8), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.72, 0.93, (-5.0, 0.5), 2.5), (1.5, 1.86, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'left_hand', PALM_L
    return c


def x_roar(x):
    T = 1.2
    c = Clip('x_roar', T, loop=True)
    tr, tr2 = shake(7.5, 1.0, 1), shake(9.0, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.4 + 0.25 * math.sin(2 * math.pi * t / T), 0.6), 12)
    c.fn('torso', 'rot', lambda t: (12.0 + 0.8 * tr(t), 0.0, 1.6 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (8.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-110.0 + 3.0 * tr(t), 0.0, 20.0), 12)
    c.fn('right_forearm', 'rot', lambda t: (-50.0, 0.0, 0.0), 2)
    c.fn('right_hand', 'rot', lambda t: (-40.0 + 3.0 * tr2(t), 0.0, 0.0), 12)
    c.fn('left_arm', 'rot', lambda t: (-50.0 + 3.0 * math.sin(2 * math.pi * t / T), 0.0, -24.0), 12)
    c.fn('left_forearm', 'rot', lambda t: (-40.0, 0.0, 0.0), 2)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 0.5), 0.0)], samples=12)
    return c


def x_charge(x):
    """Chain pull: lunges and whips the left arm forward along the line, then yanks back."""
    A, B, I, F, R, E = 0.12, 0.6, 0.85, 1.1, 1.4, 1.8
    c = Clip('x_charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, -1.8, 2.0), (I, 0, -2.4, -6.0, 'I'), (F, 0, -2.0, -3.0, 'O'), (R, 0, -1.2, -1.6),
                   (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 8, -26, 0), (I, 22, 18, 0, 'I'), (F, -6, 10, 0, 'O'), (R, 0, 4, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -6, 22, 0), (I, -12, -12, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, 30, 0, -20), (I, -90, 0, 2, 'I'), (F, -60, 0, -10, 'O'), (R, -30, 0, -6),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -60, 0, 0), (I, 0, 0, 0, 'I'), (F, -50, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -60, 0, 20), (I, 20, 0, 10, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.56, 0.83, (-8.0, 0.5), 3.0), (1.35, 1.72, (0.0, 0.0), 2.5)])
    return c


def x_summon(x):
    """Pounds the haft of the axe on the floor three times: the jailers answer the last blow."""
    E, I = 2.2, 1.1
    c = Clip('x_summon', E, impact=I)
    beats = [(0.0, 0.0), (0.3, 1.0), (0.45, 0.0), (0.7, 1.0), (0.85, 0.0), (1.0, 1.4), (1.1, 0.0), (1.5, 0.0), (E, 0.0)]
    lift = lambda t: eranim.eased_value([(tb, v, 'O' if v == 0.0 else 's') for tb, v in beats], t)
    c.fn('right_arm', 'rot', lambda t: (-60.0 * lift(t) - 20.0 * envelope(t, 0.2, 1.4, 0.2), 0.0, -10.0 * envelope(t, 0.2, 1.4, 0.2)),
         44)
    c.fn('right_hand', 'rot', lambda t: (-50.0 * envelope(t, 0.2, 1.4, 0.2), 0.0, 0.0), 22)
    c.fn('torso', 'rot', lambda t: (6.0 * envelope(t, 0.2, 1.4, 0.2) + 6.0 * (1.0 - lift(t)) * envelope(t, 0.2, 1.4, 0.2),
                                    0.0, 0.0), 44)
    c.fn('head', 'rot', lambda t: (-14.0 * envelope(t, 0.95, 1.5, 0.15), 0.0, 0.0), 22)
    c.fn('base', 'pos', lambda t: (0.0, -1.2 * envelope(t, 0.2, 1.4, 0.2) - 0.6 * (1.0 - lift(t)) * envelope(t, 0.2, 1.4, 0.2),
                                   0.0), 44)
    c.rot('left_arm', [(0, 0, 0, 0), (0.6, -30, 0, -30), (I, -60, 0, -50, 'I'), (1.5, -50, 0, -40), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(-1.0, -1.0, (0.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-2.0, 1.0), 0.0)])
    return c


def x_phase(x):
    E, I = 3.0, 1.8
    c = Clip('x_phase', E, impact=I)
    tr = shake(8.0, 1.0, 3)
    grow = lambda t: envelope(t, 0.8, 1.3, 0.3)
    hold = lambda t: envelope(t, 1.8, 2.45, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.25, 0, -0.5, 1.6, 'O'), (0.9, 0, -5.6, 0.6), (1.3, 0, -5.8, 0.5),
                   (1.75, 0, 1.2, -0.4, 'O'), (2.45, 0, 0.8, -0.3), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.25, -16, 0, 0, 'O'), (0.9, 32, 0, 0), (1.3, 36, 0, 0), (1.75, -28, 0, 0, 'O'),
                    (2.45, -24, 0, 0), (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.2 * tr(t) * (grow(t) + hold(t))), 48)
    c.rot('head', [(0, 0, 0, 0), (0.9, 28, 0, 0), (1.75, -40, 0, 0, 'O'), (2.45, -34, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.9, -70, 0, -36), (1.75, -40, 0, 96, 'O'), (2.45, -38, 0, 90), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.9, -68, 0, 36), (1.75, -40, 0, -96, 'O'), (2.45, -38, 0, -90), (E, 0, 0, 0)])
    c.add('right_arm', 'rot', lambda t: (1.5 * tr(t) * hold(t), 0.0, 2.0 * tr(t + 0.1) * hold(t)), 48)
    c.add('left_arm', 'rot', lambda t: (1.5 * tr(t + 0.2) * hold(t), 0.0, -2.0 * tr(t + 0.3) * hold(t)), 48)
    c.scl('torso', [(0, 1, 1, 1), (1.3, 0.96, 0.96, 0.96), (1.8, 1.1, 1.06, 1.1, 'O'), (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.3, 0.85, (7.0, 0.5), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.5, 2.9, (0.0, 0.0), 1.5)],
         right_holds=[(0.85, 1.32, 46.0)],
         left_plan=[(0.25, 0.6, (-2.0, 0.5), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.5, 2.9, (0.0, 0.0), 1.5)])
    return c


def x_stagger(x):
    E = 1.3
    c = Clip('x_stagger', E, impact=0.1)
    c.pos('base', [(0, 0, 0, 0), (0.12, 0, -0.8, 2.8, 'O'), (0.42, 0, -1.4, 3.4), (0.75, 0, -0.6, 1.8), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.12, -24, 6, 8, 'O'), (0.42, -8, -4, 3), (0.65, 6, 2, -2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.12, -28, 10, 6, 'O'), (0.42, -4, -8, 0), (0.65, 10, 6, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.12, -56, 0, 40, 'O'), (0.5, -20, 0, 14), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.12, -40, 0, -54, 'O'), (0.5, -16, 0, -20), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.1, 0.36, (5.0, 1.0), 2.5), (0.72, 1.05, (0.0, 0.0), 2.0)],
         left_plan=[(0.3, 0.52, (2.5, 0.0), 1.5), (0.9, 1.2, (0.0, 0.0), 1.5)])
    return c


def x_death(x):
    """The executioner drops the axe, falls to his knees and pitches forward onto the floor at 1.8 s."""
    E = 3.6
    c = Clip('x_death', E, impact=1.8)
    tr = shake(7.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -0.4, 1.8, 'O'), (0.9, 0, -2.0, -0.8), (1.3, 0, -8.0, -1.2, 'i'),
                   (1.8, 0, -8.0, -2.6, 'I'), (2.0, 0, -8.0, -2.6, 'b'), (E, 0, -8.0, -2.6)])
    c.rot('hips', [(0, 0, 0, 0), (0.3, -8, 0, 0, 'O'), (0.9, 6, 0, 0), (1.3, 10, 0, 3), (1.8, 74, 0, 6, 'I'),
                   (2.0, 70, 0, 6, 'b'), (E, 72, 0, 6)])
    c.rot('torso', [(0, 0, 0, 0), (0.3, -26, 0, 0, 'O'), (0.9, 12, 0, -4), (1.3, 10, 0, -6), (1.8, 12, 0, -4, 'I'),
                    (2.0, 8, 0, -4, 'b'), (E, 8, 0, -4)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.3, 0.95, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.3, -34, 0, 0, 'O'), (0.9, 14, 8, 0), (1.8, 2, 26, 8, 'I'), (2.1, -6, 30, 10, 'b'),
                   (E, -6, 30, 10)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.3, -40, 0, 54, 'O'), (0.9, -6, 0, 18), (1.8, -88, 0, 34, 'I'),
                        (2.1, -84, 0, 38, 'b'), (E, -84, 0, 38)])
    c.rot('right_hand', [(0, 0, 0, 0), (0.5, 10, 0, 0), (0.85, 60, 0, 0), (E, 60, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (0.7, 0, 0, 0), (1.0, 40, 0, 26), (1.8, 44, 0, 30), (E, 44, 0, 30)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.3, -32, 0, -50, 'O'), (0.9, -4, 0, -14), (1.8, -96, 0, -30, 'I'),
                       (2.1, -92, 0, -34, 'b'), (E, -92, 0, -34)])
    kneel = [(0.85, 1.3, (7.0, 0.8), 1.5), (1.4, 1.8, (12.0, 1.0), 0.0)]
    for _ in range(2):
        feet(c, x, right_plan=kneel, left_plan=[(0.95, 1.3, (5.0, 0.8), 1.5), (1.4, 1.8, (10.0, 1.0), 0.0)],
             right_holds=[(1.3, 1.45, 50.0), (1.45, E, 72.0)], left_holds=[(1.3, 1.45, 46.0), (1.45, E, 70.0)])
        for t in (1.3, 1.8, 2.0, E):
            eranim.ground(c, x.rig, x.v, t, skip=('axe', 'hood_tip'))
    return c


def axe_off_floor(c, x):
    """The axe head rests on the floor (never in it) when it is swung down, dragged or dropped."""
    n = int(round(c.length / 0.05))
    eranim.rest_on_floor(c, x.rig, x.v, [c.length * i / n for i in range(n + 1)], 'axe', clearance=0.3)
    return c


def animations(r):
    vq = [v for v in r.variants if v.id == 'b5'][0]
    vx = [v for v in r.variants if v.id == 'b4'][0]
    xq = Ctx(r, vq)
    xx = Ctx(r, vx, LEGS)
    out = [build(fn(xq), xq, CHAINS_Q) for fn in (q_idle, q_walk, q_slam, q_sweep, q_cast, q_roar, q_charge, q_summon,
                                                    q_phase, q_stagger, q_death)]
    out += [build(axe_off_floor(fn(xx), xx), xx, CHAINS_X) for fn in (x_idle, x_walk, x_slam, x_sweep, x_cast, x_roar,
                                                                         x_charge, x_summon, x_phase, x_stagger, x_death)]
    return out


# --------------------------------------------------------------------------------------------- looks

def variants():
    def deco_b5(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#e8e4f0'), eye_col=(60, 180, 255), brows=(200, 220, 240), mouth=True,
                   glow_fc=p.face('head', 'front', glow=True), glow_eyes=(150, 230, 255))
        g = p.face('head', 'front', glow=True)
        ey = f.h // 2
        for xx in (f.w // 2 - 2, f.w // 2 + 1):
            g.fpx(xx * f.k + 1, ey * f.k, (255, 255, 255))
            f.fpx(xx * f.k + 1, ey * f.k, (255, 255, 255))
        f.px(3, 6, (120, 60, 140))
        f.px(4, 6, (120, 60, 140))
        for side in ('right', 'left', 'back', 'top'):
            fc = p.face('head', side)
            for y in range(fc.h if side != 'right' and side != 'left' else 5):
                for x in range(fc.w):
                    fc.px(x, y, shade(rgb('#e8f4ff'), 0.92 + 0.12 * ((x * 3 + y) % 3 == 0)))
        f.hline(0, f.w - 1, 0, rgb('#e8f4ff'))
        for part in ('crown', 'scepter', 'shard_ring', 'collar'):
            for i in range(len(p.rig.parts[part].cubes)):
                for fc in p.all_faces(part, index=i, glow=True):
                    for y in range(fc.fh):
                        for x in range(fc.fw):
                            if (x + y) % 3 == 0:
                                fc.fpx(x, y, (120, 200, 255))
        for part in ('gown_lower', 'train'):
            for fc in p.all_faces(part, sides_only=True):
                for x in range(fc.w):
                    if x % 3 != 1:
                        fc.px(x, fc.h - 1, (230, 250, 255))
                    fc.px(x, fc.h - 2, (200, 235, 255))
        for fc in p.all_faces('torso', sides_only=True):
            if fc.face == 'front':
                for k in range(fc.h // 2):
                    fc.px(fc.w // 2 - 1 - k // 2, k, (220, 240, 255))
                    fc.px(fc.w // 2 + k // 2, k, (220, 240, 255))
    gown = {'base': '#5a8cc8', 'trim': (230, 248, 255)}
    pal5 = {'face': '#e8e4f0', 'crown': {'base': '#bfeaff', 'style': 'crystal'}, 'crown_c': {'base': '#d8f6ff', 'style': 'crystal'},
            'hair': {'base': '#e0f0ff', 'style': 'hair'}, 'hair2': {'base': '#d4e8fa', 'style': 'hair'},
            'sleeve': {'base': '#4a7cbc'}, 'arm': '#e8e4f0', 'hand': '#e8e4f0',
            'cuff': {'base': '#e0f4ff', 'style': 'ice'}, 'staff': {'base': '#d0f0ff', 'style': 'crystal'},
            'gem': {'base': '#80d8ff', 'style': 'crystal'}, 'shard': {'base': '#c8f0ff', 'style': 'crystal'},
            'bodice': {'base': '#3c6cb0', 'trim': (230, 248, 255)}, 'collar': {'base': '#d8f6ff', 'style': 'crystal'},
            'waist': '#3c6cb0', 'gown': gown, 'gown2': dict(gown, base='#4a7cc0'), 'train': dict(gown, base='#44a0d8')}

    def deco_b4(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (40, 34, 30))
        hf = p.face('hood', 'front')
        for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
            hf.px(x, y, (12, 8, 6))
        g = p.face('hood', 'front', glow=True)
        for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
            g.px(x, y, (160, 230, 255))
        for xx in (2, 5):
            g.fpx(xx * hf.k + (1 if xx == 2 else 0), 3 * hf.k, (255, 255, 255))
        hf.hline(2, 5, 6, (60, 50, 44))
        for part in ('apron', 'apron_lower'):
            for fc in p.all_faces(part, sides_only=True):
                if fc.face == 'front':
                    for y in range(2, fc.h - 1, 3):
                        fc.px(2 + (y % 4), y, (90, 30, 26))
                        fc.px(5 - (y % 3), y + 1, (100, 34, 28))
        for fc in p.all_faces('torso', sides_only=True):
            for x in range(fc.w):
                y = (x if fc.face in ('front', 'back') else x + 3) % fc.h
                fc.px(x, y, (130, 136, 146))
                fc.px(x, (y + 1) % fc.h, (90, 96, 106))
        for fc in p.all_faces('axe', index=1, glow=True, sides_only=True):
            if fc.face in ('right', 'left'):
                fc.vline(0, 0, fc.h - 1, (170, 230, 255))
        for fc in p.all_faces('axe', index=1, sides_only=True):
            if fc.face in ('right', 'left'):
                for fy in range(fc.fh):
                    fc.fpx(0, fy, (230, 240, 250))
    pal4 = {'face': '#4a3c34', 'hood': {'base': '#2c2a34', 'style': 'cloth'}, 'hood_tip': '#2c2a34', 'sleeve': '#3a3434',
            'arm': {'base': '#8a7060', 'style': 'skin'}, 'hand': {'base': '#7a6050', 'style': 'skin'},
            'pauldron': {'base': '#5a5e68', 'style': 'metal'},
            'haft': {'base': '#4a3422', 'style': 'wood'}, 'axe': {'base': '#9aa8b8', 'style': 'metal'},
            'axe_tip': {'base': '#9aa8b8', 'style': 'metal'}, 'bodice': {'base': '#4a4040', 'style': 'leather'},
            'waist': {'base': '#3a3030', 'style': 'leather'}, 'pants': '#2e2a2a', 'boots': {'base': '#2a2220', 'style': 'leather'},
            'boot_foot': {'base': '#221c1a', 'style': 'leather'},
            'apron': {'base': '#5a3a26', 'style': 'leather'}, 'apron2': {'base': '#523422', 'style': 'leather'}}
    ice = {'wing': {'base': '#c8f0ff', 'style': 'crystal'}, 'ice_horn': {'base': '#bfeaff', 'style': 'crystal'},
           'ice_horn2': {'base': '#a8dcf8', 'style': 'crystal'}}
    pal5.update(ice)
    pal4.update(ice)
    for key in ('crown', 'crown_c', 'hair', 'hair2', 'cuff', 'staff', 'gem', 'shard', 'collar', 'gown', 'gown2', 'train'):
        pal4.setdefault(key, '#808080')
    for key in ('hood', 'hood_tip', 'pauldron', 'haft', 'axe', 'axe_tip', 'pants', 'boots', 'boot_foot', 'apron', 'apron2'):
        pal5.setdefault(key, '#808080')

    def deco_b5_form(form):
        def paint(p):
            deco_b5(p)
            if form >= 2:
                glow_faces(p, 'wing_r', (170, 230, 255), every=3)
                for fc in p.all_faces('wing_r', sides_only=True):
                    if fc.face in ('front', 'back'):
                        for y in range(0, fc.h, 3):
                            fc.hline(0, fc.w - 1 - (y // 3) % 4, y, (235, 250, 255))
            veins(p, ('torso', 'gown', 'gown_lower'), (200, 240, 255), (190, 240, 255), seed=form)
            if form >= 3:
                cracks(p, ('gown_lower', 'gown', 'right_arm', 'left_arm', 'train'), (235, 250, 255), (210, 245, 255),
                       every=24)
        return paint

    def deco_b4_form(form):
        def paint(p):
            deco_b4(p)
            glow_faces(p, 'ice_horns', (170, 230, 255), every=2)
            glow_faces(p, 'ice_horns', (170, 230, 255), index=2, every=2)
            g = p.face('hood', 'front', glow=True)
            for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
                g.px(x, y, (220, 250, 255))
            for fc in p.all_faces('axe', index=1, glow=True, sides_only=True):
                for y in range(0, fc.h, 2):
                    fc.px(min(fc.w - 1, 1 + y % 3), y, (170, 230, 255))
            veins(p, ('pauldron_r', 'pauldron_l', 'torso'), (150, 210, 240), (170, 230, 255), seed=form)
            if form >= 2:
                cracks(p, ('apron', 'apron_lower', 'right_leg', 'left_leg', 'right_arm', 'left_arm'), (170, 225, 255),
                       (190, 240, 255), every=26)
        return paint
    b5_parts = {'crown', 'hair', 'sleeve_r', 'sleeve_l', 'scepter', 'shard_ring', 'collar', 'gown'}
    b4_parts = {'hood', 'pauldron_r', 'pauldron_l', 'axe', 'right_leg', 'left_leg', 'apron'}
    frost = dict(pal5, gown={'base': '#9cc8f0', 'trim': (240, 252, 255)}, gown2={'base': '#a8d4f8', 'trim': (240, 252, 255)},
                 train={'base': '#b4dcfa', 'trim': (240, 252, 255)},
                 bodice={'base': '#86b8e8', 'trim': (240, 252, 255)}, waist='#86b8e8')
    big_crown = {'crown': dict(scale=(1.25, 1.45, 1.25)), 'shard_ring': dict(scale=(1.35, 1.35, 1.35))}
    return [Variant('b5', show=b5_parts, palette=pal5, paint=deco_b5, glow=True, extra=dict(scale=2.4, hitbox=(2.6, 6.8))),
            Variant('b4', show=b4_parts, palette=pal4, paint=deco_b4, glow=True, extra=dict(scale=2.7, hitbox=(3.0, 7.6))),
            Variant('b5_p1', show=b5_parts, palette=pal5, poses=big_crown, paint=deco_b5_form(1), glow=True,
                    extra=dict(scale=2.4)),
            Variant('b5_p2', show=b5_parts | {'ice_wings'}, palette=pal5, poses=big_crown, paint=deco_b5_form(2),
                    glow=True, extra=dict(scale=2.4)),
            Variant('b5_p3', show=b5_parts | {'ice_wings'}, palette=frost,
                    poses=dict(big_crown, ice_wings=dict(scale=(1.3, 1.3, 1.0)), crown=dict(scale=(1.35, 1.7, 1.35))),
                    paint=deco_b5_form(3), glow=True, extra=dict(scale=2.4)),
            Variant('b4_p1', show=b4_parts | {'ice_horns'}, palette=pal4, paint=deco_b4_form(1), glow=True,
                    extra=dict(scale=2.7)),
            Variant('b4_p2', show=b4_parts | {'ice_horns'}, palette=pal4, poses={'ice_horns': dict(scale=(1.2, 1.4, 1.2))},
                    paint=deco_b4_form(2), glow=True, extra=dict(scale=2.7))]


SETS = {
    'b5': dict(idle='idle', walk='q_walk', melee_slam='q_slam', melee_sweep='q_sweep', cast='q_cast', roar='q_roar',
               charge='q_charge', summon='q_summon', phase_change='q_phase', stagger='q_stagger', death='q_death'),
    'b4': dict(idle='x_idle', walk='x_walk', melee_slam='x_slam', melee_sweep='x_sweep', cast='x_cast', roar='x_roar',
               charge='x_charge', summon='x_summon', phase_change='x_phase', stagger='x_stagger', death='x_death'),
}
for _form in ('b5_p1', 'b5_p2', 'b5_p3'):
    SETS[_form] = SETS['b5']
for _form in ('b4_p1', 'b4_p2'):
    SETS[_form] = SETS['b4']

TRAILS_Q = [('scepter', (-8.0, -8.0, 0.0), GEM)]
TRAILS_X = [('axe', AXE_GRIP, AXE_EDGE)]


def rig():
    vs = variants()
    base = Rig('queen', 'QueenBoss', geometry(), [], vs, SLOTS, anim_sets=SETS, texel=2)
    r = Rig('queen', 'QueenBoss', geometry(), animations(base), vs, SLOTS, anim_sets=SETS, texel=2)
    r.preview_ppu = 3.0
    finish_rig(r, GAIT_X.walk_speed(), TRAILS_X)
    from boss_motion import local
    r.trails_for = lambda v: [(b, local(r, b, p0), local(r, b, p1)) for b, p0, p1 in
                              (TRAILS_X if v.id.startswith('b4') else TRAILS_Q)]
    return r
