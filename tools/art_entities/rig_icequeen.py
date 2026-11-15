"""ICEQUEEN boss rig: b5 氷獄の女王 (the queen of the ice prison), from the concept sheet.

A tall queen who floats a hand's breadth above the floor: a pale face with large glowing blue eyes, long silver-white
hair (bangs, locks down the front, a fall to the hem behind), a crown of ice crystals with a great diamond; a high
collar of fur and ice, puffed shoulders, a bodice of white and navy with a diamond brooch and a line of gems; long
white sleeves flaring at the cuffs; a bell-shaped gown of three tiers in white and navy with a glowing navy front
panel of ice and gems, ice shards round its hem, and a mantle of pale ice behind. In her right hand a crystal staff
crowned with a six-armed snowflake around a diamond. Later forms grow the crown, add wings of ice and deepen to a
colder blue.

Motion: graceful and floating; the gown tiers, the hair, the mantle and the cuffs trail every move (spring chains).
Actions: the staff raised and struck into the floor (slam), a sweep of the snowflake through the air (sweep), the
staff levelled at the target (cast, from the snowflake's gem), a gliding thrust (charge), both arms lifted with the
staff as the ice rises (summon), arms spread in wrath (roar), a twirl that flares the gown before the staff is
raised (phase change), and a death where she sinks into her gown and falls to the side. Timings are those of the
old b5 look (BossRigData)."""
import math

import eranim
from boss_kit import diamond, frost, gem, h, ragged, ramp, trim
from boss_motion import Ctx, build, finish_rig
from eranim import Chain, Clip, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
FLAKE = (-6.5, -27.0, -0.5)          # the snowflake's diamond (rest coordinates of part staff)
FLAKE_TOP = (-6.5, -33.0, 0.0)
STAFF_FOOT = (-6.5, 22.0, 0.0)
STAFF_MID = (-6.5, -14.0, 0.0)
HOVER = 1.1                          # how far she floats above the floor at rest (model units)


def snowflake():
    """Six crystal arms round a diamond, in the plane facing forward, each with a pair of side twigs."""
    arms = []
    cx, cy = -6.5, -27.0
    for k in range(6):
        tag = 'fa' if k == 0 else None
        share = None if k == 0 else 'fa'
        tw = 'ft' if k == 0 else None
        tws = None if k == 0 else 'ft'
        arms.append(P('flake_%d' % k, (cx, cy, 0), C(cx - 0.5, cy - 6, -0.5, 1, 5, 1, 'crystal:flake', tag=tag, share=share),
                      C(cx - 1.5, cy - 4, -0.5, 3, 1, 1, 'crystal:twig', tag=tw, share=tws),
                      rot=(0, 0, 60 * k)))
    return arms


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 6.5 * s
    kids = []
    if s < 0:
        staff = P('staff', (cx, 1.5, 0),
                  C(cx - 1, -22, -1, 2, 44, 2, 'crystal:shaft'),
                  C(cx - 1.5, -0.5, -1.5, 3, 3, 3, 'gold:grip'),
                  C(cx - 1.5, -23, -1.5, 3, 2, 3, 'gold:staff_collar'),
                  C(cx - 1, 22, -1, 2, 2, 2, 'crystal:foot'),
                  C(cx - 1.5, -28.5, -1.5, 3, 3, 2, 'gem:flake_gem'),
                  *snowflake(), rot=(4, 0, -4))
        kids.append(staff)
    hand = P(name + '_hand', (cx, 0, 0), C(cx - 1.5, 0, -1.5, 3, 3, 3, 'skin:glove', **k('glove')), *kids)
    cuff = P('cuff_' + name[0], (cx, -1.5, 0), C(cx - 3, -1.5, -3, 6, 3, 6, 'cloth:cuff', **k('cuff')),
             C(cx - 3, -1.5, -3, 6, 1, 6, 'ice:cuff_ice', inflate=0.25, **k('cice')))
    fore = P(name + '_forearm', (cx, -6, 0), C(cx - 2, -6, -2, 4, 6, 4, 'cloth:sleeve2', **k('farm')), cuff, hand,
             rot=(-12, 0, 0))
    return P(name + '_arm', (cx, -12, 0),
             C(cx - 2.5, -14.5, -3, 5, 4, 6, 'cloth:puff', **k('puff')),
             C(cx - 2.5, -14.5, -3, 5, 1, 6, 'gold:puff_trim', inflate=0.3, **k('ptrim')),
             C(cx - 2, -12, -2, 4, 6, 4, 'cloth:sleeve', **k('uarm')), fore,
             rot=((-14, 0, 8) if s < 0 else (-6, 0, -8)))


def geometry():
    crown = P('crown', (0, -23.5, -0.5),
              C(-4.5, -25.5, -4.5, 9, 2, 9, 'crystal:band'),
              C(-1, -33.5, -5.2, 2, 8, 2, 'crystal:spire'),
              C(-1, -28.5, -5.6, 2, 3, 1, 'gem:crown_gem'),
              C(-3.5, -30, -5, 1, 5, 1, 'crystal:spike', tag='s1'), C(2.5, -30, -5, 1, 5, 1, 'crystal:spike', share='s1'),
              C(-5, -28.5, -4.6, 1, 3, 1, 'crystal:spike2', tag='s2'), C(4, -28.5, -4.6, 1, 3, 1, 'crystal:spike2', share='s2'),
              C(-5, -29, -1, 1, 4, 1, 'crystal:spike4', tag='s4'), C(4, -29, -1, 1, 4, 1, 'crystal:spike4', share='s4'),
              C(-3, -29.5, 3.6, 1, 4, 1, 'crystal:spike4', share='s4'), C(2, -29.5, 3.6, 1, 4, 1, 'crystal:spike4', share='s4'),
              C(-0.5, -31, 3.6, 1, 6, 1, 'crystal:spike3'))
    hair_back = P('hair_back', (0, -20, 4.6), C(-5, -20, 4.1, 10, 10, 2, 'hair:back'),
                  P('hair_back2', (0, -10, 4.6), C(-5, -10, 4.1, 10, 10, 2, 'hair:back2'),
                    P('hair_back3', (0, 0, 4.6), C(-4.5, 0, 4.1, 9, 12, 2, 'hair:back3'))))
    lock_r = P('lock_r', (-4.6, -17, -3.2), C(-5.6, -17, -4.2, 2, 6, 2, 'hair:lock'),
               P('lock_r2', (-4.6, -11, -3.2), C(-5.6, -11, -4.2, 2, 6, 2, 'hair:lock2')))
    lock_l = P('lock_l', (4.6, -17, -3.2), C(3.6, -17, -4.2, 2, 6, 2, 'hair:lock', mirror=True),
               P('lock_l2', (4.6, -11, -3.2), C(3.6, -11, -4.2, 2, 6, 2, 'hair:lock2', mirror=True)))
    head = P('head', (0, -14, -0.5),
             C(-4.5, -22.5, -5, 9, 9, 9, 'skin:face', tag='face'),
             C(-5, -23.5, -5.5, 10, 3, 10, 'hair:top'),
             C(-5, -22, -5.6, 10, 3, 1, 'hair:bangs'),
             C(-5.6, -22, -5, 1, 7, 9, 'hair:side', tag='side'), C(4.6, -22, -5, 1, 7, 9, 'hair:side', share='side', mirror=True),
             crown, lock_r, lock_l, hair_back)
    wings = P('ice_wings', (0, -9, 4.5),
              P('wing_r', (-2, -9, 4.5), C(-14, -16, 4.5, 12, 4, 1, 'ice:wing1', tag='w1'),
                C(-16, -12, 4.5, 13, 3, 1, 'ice:wing2', tag='w2'), C(-15, -9, 4.5, 11, 3, 1, 'ice:wing3', tag='w3'),
                C(-12, -6, 4.5, 8, 2, 1, 'ice:wing4', tag='w4'), rot=(0, 18, 14)),
              P('wing_l', (2, -9, 4.5), C(2, -16, 4.5, 12, 4, 1, 'ice:wing1', share='w1', mirror=True),
                C(3, -12, 4.5, 13, 3, 1, 'ice:wing2', share='w2', mirror=True),
                C(4, -9, 4.5, 11, 3, 1, 'ice:wing3', share='w3', mirror=True),
                C(4, -6, 4.5, 8, 2, 1, 'ice:wing4', share='w4', mirror=True), rot=(0, -18, -14)),
              opt=True)
    mantle = P('mantle', (0, -13, 4.2), C(-6.5, -13, 3.8, 13, 10, 1, 'ice:mantle'),
               P('mantle2', (0, -3, 4.2), C(-8, -3, 3.8, 16, 12, 1, 'ice:mantle2'),
                 P('mantle3', (0, 9, 4.2), C(-9.5, 9, 3.8, 19, 14, 1, 'ice:mantle3'))), rot=(6, 0, 0))
    chest = P('chest', (0, -2, 0),
              C(-5, -14, -3.5, 10, 12, 7, 'cloth:bodice', tag='bodice'),
              C(-5.5, -16.5, -4, 11, 3, 8, 'fur:collar'),
              C(-6.5, -14.8, -3.8, 13, 2, 7, 'fur:stole'),
              C(-1.5, -11.5, -4.2, 3, 3, 1, 'gem:brooch'),
              C(-1, -7.5, -4.0, 2, 2, 1, 'gem:gem2', tag='g2'), C(-1, -4.8, -4.0, 2, 2, 1, 'gem:gem2', share='g2'),
              head, arm(-1), arm(1), wings, mantle)
    shards = [C(-12, 19, -4, 2, 5, 3, 'ice:shard', tag='sh1'), C(10, 19, -4, 2, 5, 3, 'ice:shard', share='sh1'),
              C(-12, 18, 3, 2, 6, 3, 'ice:shard2', tag='sh2'), C(10, 18, 3, 2, 6, 3, 'ice:shard2', share='sh2'),
              C(-6, 20, -9, 3, 4, 2, 'ice:shard3', tag='sh3'), C(3, 20, -9, 3, 4, 2, 'ice:shard3', share='sh3'),
              C(-5, 20, 7.8, 3, 4, 2, 'ice:shard3', share='sh3'), C(2, 20, 7.8, 3, 4, 2, 'ice:shard3', share='sh3')]
    skirt3 = P('skirt3', (0, 15.5, 0), C(-11, 15.5, -8, 22, 8, 16, 'cloth:skirt3', tag='sk3'),
               C(-4, 15.5, -8.6, 8, 8, 1, 'cloth:panel3', tag='pn3'), *shards)
    skirt2 = P('skirt2', (0, 7.5, 0), C(-9, 7.5, -6.5, 18, 8, 13, 'cloth:skirt2', tag='sk2'),
               C(-3.5, 7.5, -7.1, 7, 8, 1, 'cloth:panel2', tag='pn2'), skirt3)
    skirt = P('skirt', (0, -1, 0), C(-7, -1, -5, 14, 9, 10, 'cloth:skirt1', tag='sk1'),
              C(-3, -1, -5.6, 6, 9, 1, 'cloth:panel1', tag='pn1'),
              C(-7.5, -1.5, -5.5, 15, 1, 11, 'gold:sash'), skirt2)
    hips = P('hips', (0, -1, 0), C(-5, -3, -3.5, 10, 3, 7, 'cloth:waist'), chest, skirt)
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['skirt', 'skirt2', 'skirt3'], rest='down', axes='xz', hang=0.75, freq=1.3, damp=0.45, inertia=5.0,
          falloff=1.1, limits={'x': (-7.0, 7.0), 'z': (-6.0, 6.0)},
          drag={'walk': (5.0, 0.0, 0.0), 'charge': (7.0, 0.0, 0.0)}, floor=False),
    Chain(['hair_back', 'hair_back2', 'hair_back3'], rest='down', axes='xz', hang=0.85, freq=1.3, damp=0.3,
          inertia=8.0, falloff=1.15, limits={'x': (-6.0, 80.0), 'z': (-30.0, 30.0)},
          drag={'walk': (10.0, 0.0, 0.0), 'charge': (14.0, 0.0, 0.0)},
          per_clip={'death': {'limits': {'x': (-60.0, 80.0), 'z': (-40.0, 40.0)}}}),
    Chain(['mantle', 'mantle2', 'mantle3'], rest='down', axes='xz', hang=0.88, freq=1.15, damp=0.3, inertia=9.0,
          falloff=1.15, limits={'x': (-4.0, 95.0), 'z': (-30.0, 30.0)},
          drag={'walk': (14.0, 0.0, 0.0), 'charge': (16.0, 0.0, 0.0), 'roar': (6.0, 0.0, 0.0)}),
    Chain(['lock_r', 'lock_r2'], rest='down', axes='xz', hang=0.8, freq=1.8, damp=0.35, inertia=5.0,
          limits={'x': (-40.0, 25.0), 'z': (-25.0, 25.0)}),
    Chain(['lock_l', 'lock_l2'], rest='down', axes='xz', hang=0.8, freq=1.85, damp=0.35, inertia=5.0,
          limits={'x': (-40.0, 25.0), 'z': (-25.0, 25.0)}),
    Chain(['cuff_r'], rest='down', axes='xz', hang=0.6, freq=2.4, damp=0.4, inertia=3.0,
          limits={'x': (-25.0, 25.0), 'z': (-25.0, 25.0)}),
    Chain(['cuff_l'], rest='down', axes='xz', hang=0.6, freq=2.5, damp=0.4, inertia=3.0,
          limits={'x': (-25.0, 25.0), 'z': (-25.0, 25.0)}),
]
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def hover(t, T, amp=0.6):
    return amp * math.sin(2 * math.pi * t / T)


def idle(x):
    T = 3.2
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.3 * math.sin(2 * math.pi * t / T + 0.8), hover(t, T), 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 2.0 * math.sin(2 * math.pi * t / T + 1.2), 1.0 * math.sin(2 * math.pi * t / T)),
         16)
    c.fn('chest', 'rot', lambda t: (-1.6 * b(t), -1.5 * math.sin(2 * math.pi * t / T + 1.2), 0.0), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.014 * b(t), 1.0 + 0.01 * b(t), 1.0 + 0.02 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (0.6, 0.0, 's'), (1.1, -7.0, 's'), (1.8, -7.0, 's'), (2.3, 5.0, 's'), (2.8, 5.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.0 * b(t - 0.2), eranim.eased_value(look, t), 2.0 * math.sin(2 * math.pi * t / T)),
         32)
    c.fn('right_arm', 'rot', lambda t: (0.8 * math.sin(2 * math.pi * t / T), 0.0, 0.8 * b(t)), 12)
    c.fn('left_arm', 'rot', lambda t: (-2.0 * math.sin(2 * math.pi * t / T + 0.5), 0.0, -2.0 * b(t - 0.2)), 12)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 * b(t - 0.3), 0.0, 0.0), 12)
    c.fn('staff', 'rot', lambda t: (0.0, 6.0 * math.sin(2 * math.pi * t / T), 0.0), 16)
    for k in range(6):
        c.fn('flake_%d' % k, 'scale', lambda t, k=k: (1.0 + 0.06 * math.sin(2 * math.pi * (t / T) * 2 + k),
                                                      1.0 + 0.06 * math.sin(2 * math.pi * (t / T) * 2 + k), 1.0), 12)
    return c


def walk(x):
    T = 1.8
    c = Clip('walk', T, loop=True)
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.6 * s1(t), 0.5 + 0.4 * math.sin(4 * math.pi * t / T), 0.0), 18)
    c.fn('hips', 'rot', lambda t: (6.0, 4.0 * s1(t), 2.0 * s1(t + 0.2)), 18)
    c.fn('chest', 'rot', lambda t: (2.0, -3.0 * s1(t), -1.0 * s1(t)), 18)
    c.fn('head', 'rot', lambda t: (-6.0, -1.5 * s1(t), 1.0 * s1(t - 0.1)), 18)
    c.fn('right_arm', 'rot', lambda t: (-4.0 + 3.0 * s1(t), 0.0, 2.0), 18)
    c.fn('left_arm', 'rot', lambda t: (8.0 - 6.0 * s1(t), 0.0, -10.0), 18)
    c.fn('left_forearm', 'rot', lambda t: (-10.0, 0.0, 0.0), 6)
    return c


def melee_slam(x):
    """The staff raised high in both hands and driven foot-first into the floor before her (impact), ice bursting."""
    A, B, Cc, I, F, R, E = 0.15, 0.65, 0.82, 1.0, 1.25, 1.65, 2.1
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.4, 0.4), (B, 0, 2.4, 1.0), (Cc, 0, 2.6, 1.1, 'o'), (I, 0, -0.8, -2.0, 'I'),
                   (F, 0, -1.0, -2.2, 'O'), (R, 0, -0.4, -1.2), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -14, 0, 0), (Cc, -16, 0, 0, 'o'), (I, 18, 0, 0, 'I'), (F, 20, 0, 0, 'O'),
                    (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 4), (B, -150, 0, -20), (Cc, -156, 0, -22, 'o'), (I, -60, 0, -8, 'I'),
                        (F, -56, 0, -8, 'O'), (R, -30, 0, -4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -150, 0, 26), (Cc, -156, 0, 26, 'o'), (I, -64, 0, 30, 'I'), (F, -60, 0, 30),
                       (R, -30, 0, 10), (E, 0, 0, 0)])
    c.rot('staff', [(0, 0, 0, 0), (B, -10, 0, 0), (I, -26, 0, 0, 'I'), (F, -26, 0, 0), (E, 0, 0, 0)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'staff', 'x', 'staff', STAFF_FOOT, 24.5, -80.0, 30.0)
    return c


def melee_sweep(x):
    """The blizzard: the staff swept flat across the front, the snowflake leading (impact at the centre)."""
    A, B, I, F, R, E = 0.15, 0.6, 0.9, 1.15, 1.5, 1.9
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, -0.8, 0.4, 0.8), (I, 0.8, 0.0, -1.6, 'I'), (F, 1.2, 0.0, -1.8, 'O'),
                   (R, 0.6, 0.2, -0.8), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 26, 0), (I, 0, -14, 0, 'I'), (F, 0, -30, 0, 'b'), (R, 0, -14, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 2, 22, -4), (I, 6, -12, 4, 'I'), (F, 4, -24, 4, 'b'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -30, 0), (I, 0, 12, 0, 'I'), (F, 0, 26, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -70, 70, 40), (I, -90, -4, 10, 'I'), (F, -84, -66, 6, 'O'),
                        (R, -44, -36, 4), (E, 0, 0, 0)])
    c.rot('staff', [(0, 0, 0, 0), (B, -70, 0, 20), (I, -86, 0, 10, 'I'), (F, -80, 0, 0, 'O'), (R, -40, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -30), (I, -20, 0, -60, 'I'), (F, -10, 0, -70, 'O'), (R, 0, 0, -30),
                       (E, 0, 0, 0)])
    return c


def cast(x):
    """The staff levelled at her foe, the snowflake flaring, the left hand drawn back, then a thrust (release)."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 1.0, 1.0), (I, 0, 0.4, -1.4, 'I'), (F, 0, 0.4, -1.6, 'O'), (R, 0, 0.4, -0.8),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -10, 18, 0), (I, 8, -10, 0, 'I'), (F, 10, -12, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -6, -14, 0), (I, 0, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 4), (B, -40, 0, 20), (I, -92, 0, 4, 'I'), (F, -96, 0, 4, 'b'),
                        (R, -50, 0, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, 4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('staff', [(0, 0, 0, 0), (B, 20, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -90, 0, -30), (I, 20, 0, -20, 'I'), (F, 24, 0, -22), (E, 0, 0, 0)])
    for k in range(6):
        c.scl('flake_%d' % k, [(0, 1, 1, 1), (B, 1.35, 1.35, 1.0), (I, 1.0, 1.0, 1.0, 'I'), (F, 1.12, 1.12, 1.0, 'b'),
                               (E, 1, 1, 1)])
    for t in (I, F):
        eranim.aim(c, x.rig, x.v, t, 'staff', 'x', 'staff', STAFF_MID, FLAKE_TOP, 0.0, -150.0, 60.0, forward=True)
    c.muzzle_part, c.muzzle_point = 'staff', FLAKE
    return c


def roar(x):
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, 1.4 + 0.4 * math.sin(2 * math.pi * t / T), 0.6), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.0 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-16.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-40.0 + 2.0 * tr(t), 0.0, 40.0 + 2.0 * tr2(t)), 14)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 2.0 * tr2(t), 0.0, -60.0 - 3.0 * tr(t)), 14)
    c.fn('left_forearm', 'rot', lambda t: (-20.0, 0.0, 0.0), 6)
    for k in range(6):
        c.fn('flake_%d' % k, 'scale', lambda t, k=k: (1.15 + 0.08 * tr(t + 0.05 * k), 1.15 + 0.08 * tr(t + 0.05 * k),
                                                      1.0), 14)
    return c


def charge(x):
    """A gliding thrust: she sweeps low and fast across the floor with the staff levelled like a lance (impact)."""
    A, B, I, F, R, E = 0.15, 0.5, 0.8, 1.0, 1.25, 1.6
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, 0.4, 0.6), (B, 0, 0.0, 2.4), (I, 0, -0.2, -10.0, 'I'), (F, 0, -0.2, -10.6, 'O'),
                   (R, 0, 0.2, -6.0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 8, 14, 0), (I, 20, -8, 0, 'I'), (F, 20, -8, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 10, 10, 0), (I, 14, -8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, -10, 0), (I, -24, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 30, 0, 14), (I, -90, 0, 4, 'I'), (F, -94, 0, 4, 'O'), (R, -50, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -50, 0, -20), (I, 30, 0, -40, 'I'), (F, 34, 0, -42), (E, 0, 0, 0)])
    for t in (I, F):
        eranim.aim(c, x.rig, x.v, t, 'staff', 'x', 'staff', STAFF_MID, FLAKE_TOP, -4.0, -150.0, 60.0, forward=True)
    return c


def summon(x):
    """Both arms lifted with the staff above her head as the ice rises around her (impact)."""
    A, B, I, F, R, E = 0.2, 0.75, 1.1, 1.4, 1.75, 2.2
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, -0.4, 0.4), (I, 0, 3.0, 0.0, 'O'), (F, 0, 2.8, 0.0), (R, 0, 1.2, 0.0),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 10, 0, 0), (I, -14, 0, 0, 'O'), (F, -12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 12, 0, 0), (I, -22, 0, 0, 'O'), (F, -20, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -20, 0, 10), (I, -170, 0, -6, 'O'), (F, -168, 0, -6), (R, -80, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -20, 0, -10), (I, -150, 0, -30, 'O'), (F, -148, 0, -30), (R, -70, 0, -10),
                       (E, 0, 0, 0)])
    for part in ('skirt3',):
        c.scl(part, [(0, 1, 1, 1), (B, 0.96, 1.0, 0.96), (I, 1.12, 1.0, 1.12, 'O'), (R, 1.04, 1.0, 1.04), (E, 1, 1, 1)])
    return c


def phase_change(x):
    """A twirl that flares the gown and the hair, then the staff thrust skywards (impact) as the cold bursts."""
    E, I = 3.0, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.5, 0.2)
    c.rot('base', [(0, 0, 0, 0), (0.3, 0, -20, 0, 'O'), (1.4, 0, 360, 0, 's'), (E, 0, 360, 0)])
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, 0.6, 0), (1.0, 0, 2.6, 0), (I, 0, 3.4, 0, 'O'), (2.5, 0, 3.0, 0),
                   (E, 0, 0, 0)])
    c.scl('skirt3', [(0, 1, 1, 1), (0.6, 1.2, 0.95, 1.2), (1.2, 1.3, 0.92, 1.3), (I, 1.12, 1.0, 1.12, 'O'),
                     (E, 1, 1, 1)])
    c.scl('skirt2', [(0, 1, 1, 1), (0.6, 1.12, 1.0, 1.12), (1.2, 1.16, 1.0, 1.16), (I, 1.06, 1.0, 1.06, 'O'),
                     (E, 1, 1, 1)])
    c.rot('chest', [(0, 0, 0, 0), (0.6, 6, 0, 0), (1.4, 10, 0, 0), (I, -18, 0, 0, 'O'), (2.5, -16, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.6, 10, 0, 0), (1.4, 16, 0, 0), (I, -26, 0, 0, 'O'), (2.5, -22, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.6, -30, 0, 60), (1.4, -40, 0, 60), (I, -172, 0, -4, 'O'), (2.5, -170, 0, -4),
                        (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.6, -30, 0, -60), (1.4, -40, 0, -60), (I, -40, 0, -80, 'O'), (2.5, -40, 0, -76),
                       (E, 0, 0, 0)])
    return c


def stagger(x):
    E = 1.3
    c = Clip('stagger', E, impact=0.1)
    tr = shake(6.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.1, 0, 0.6, 3.0, 'O'), (0.4, 0, 0.2, 3.6), (0.75, 0, 0.2, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.1, -20, 8, 8, 'O'), (0.4, -8, -4, 3), (0.7, 6, 2, -2), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.1, -24, 14, 8, 'O'), (0.4, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.1, -30, 0, 30, 'O'), (0.4, -14, 0, 14), (0.8, -4, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.1, -40, 0, -50, 'O'), (0.4, -16, 0, -24), (0.8, -4, 0, -6), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.12, 0.6, 0.12)), 24)
    return c


def death(x):
    """The cold leaves her: she sinks into her gown, the staff slipping from her hand, and folds sideways onto the
    floor (impact) as the gown spreads."""
    E, I = 3.6, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, 0.8, 1.0, 'O'), (1.2, 0, -5.0, 0.6, 's'), (I, 0, -6.6, 0.4, 'I'),
                   (E, 0, -6.6, 0.4)])
    c.scl('skirt3', [(0, 1, 1, 1), (1.2, 1.18, 0.7, 1.18), (I, 1.3, 0.5, 1.3, 'I'), (2.1, 1.34, 0.46, 1.34, 'b'),
                     (E, 1.34, 0.46, 1.34)])
    c.scl('skirt2', [(0, 1, 1, 1), (1.2, 1.08, 0.7, 1.08), (I, 1.15, 0.55, 1.15, 'I'), (E, 1.15, 0.55, 1.15)])
    c.scl('skirt', [(0, 1, 1, 1), (1.2, 1.02, 0.8, 1.02), (I, 1.04, 0.7, 1.04, 'I'), (E, 1.04, 0.7, 1.04)])
    c.rot('hips', [(0, 0, 0, 0), (0.45, -6, 0, 0, 'O'), (1.2, 6, 0, 4), (I, 10, 0, 36, 'I'), (2.0, 8, 0, 32, 'b'),
                   (E, 8, 0, 34)])
    c.rot('chest', [(0, 0, 0, 0), (0.45, -14, 0, 4, 'O'), (1.2, 16, 0, 6), (I, 20, 0, 30, 'I'), (2.0, 18, 0, 26, 'b'),
                    (E, 18, 0, 28)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.3, 1.0, 0.2)), 36)
    c.rot('head', [(0, 0, 0, 0), (0.45, -20, 0, 0, 'O'), (1.2, 24, 0, 0), (I, 10, 0, 20, 'I'), (E, 10, 0, 22)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -20, 0, 30, 'O'), (1.2, 4, 0, 14), (I, -20, 0, 70, 'I'),
                        (2.0, -18, 0, 64, 'b'), (E, -18, 0, 66)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, -30, 0, -30, 'O'), (1.2, -10, 0, -10), (I, -60, 0, -20, 'I'),
                       (2.6, -60, 0, -20), (2.85, -76, 0, -16, 'o'), (3.1, -60, 0, -20), (E, -60, 0, -20)])
    c.rot('staff', [(0, 0, 0, 0), (0.45, 0, 0, 10), (1.2, 10, 0, 30, 'I'), (1.5, 14, 0, 80, 'I'), (1.7, 14, 0, 74, 'b'),
                    (E, 14, 0, 76)])
    c.rot('crown', [(0, 0, 0, 0), (1.6, 0, 0, 0), (I, 0, 0, 14, 'I'), (E, 0, 0, 14)])
    for t in (1.5, 1.7, E):
        eranim.rest_on_floor(c, x.rig, x.v, (t,), 'staff', axis='z')
    eranim.unsink(c, x.rig, x.v, 1.0, E, skip=DANGLE + ('staff',))
    return c


def animations(r):
    x = Ctx(r, r.variants[0])
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

SKIN = rgb('#f4e6e6')
HAIR = rgb('#e6e8f2')
NAVY = rgb('#2a3c8a')
ICE = rgb('#9fd6ff')
GLOW = (120, 210, 255)
WHITE = rgb('#f2f4fa')
GOLDC = rgb('#d8c48a')


def paint_face(p, eye):
    f = p.face('head', 'front', tag='face')
    g = p.face('head', 'front', tag='face', glow=True)
    for x in range(f.fw):
        for y in range(5):
            f.fpx(x, y, mix(HAIR, shade(HAIR, 0.8), 0.3 + 0.1 * (y % 2)))
        f.fpx(x, 5, shade(SKIN, 0.85))
    lash = (40, 40, 70)
    for (x0, x1) in ((2, 6), (11, 15)):
        for x in range(x0, x1 + 1):
            f.fpx(x, 7, lash)
        for x in range(x0, x1 + 1):
            for y in (8, 9, 10):
                f.fpx(x, y, mix(eye, (20, 40, 110), (y - 8) / 3.0))
                g.fpx(x, y, mix(lighten(eye, 0.3), eye, (y - 8) / 3.0))
        f.fpx(x0 + 1, 8, (255, 255, 255))
        g.fpx(x0 + 1, 8, (255, 255, 255))
        f.fpx(x1 - 1, 10, lighten(eye, 0.5))
        f.fpx((x0 + x1) // 2, 9, (14, 22, 60))
    for x in (2, 15):
        f.fpx(x, 12, mix(SKIN, (240, 170, 190), 0.4))
    f.fpx(8, 13, shade(SKIN, 0.86))
    f.fpx(9, 13, shade(SKIN, 0.86))
    f.fpx(8, 15, (200, 140, 160))
    f.fpx(9, 15, (200, 140, 160))


def locks(fc, period, depth, seed):
    for x in range(fc.fw):
        u = (x + int(h(seed) * period)) % period
        d = abs(u - period / 2.0) / (period / 2.0)
        cut = int(round(d * depth))
        for k in range(cut):
            fc.fclear(x, fc.fh - 1 - k)


def paint_hair(p):
    for part, idx in (('head', 1), ('head', 2), ('head', 3)):
        for side in ('front', 'right', 'back', 'left', 'top'):
            f = p.face(part, side, index=idx)
            for x in range(f.fw):
                if h(part, idx, side, x) < 0.25:
                    for y in range(f.fh):
                        f.fpx(x, y, lighten(f.fget(x, y), 0.3))
    locks(p.face('head', 'front', index=2), 4, 4, 'bangs')
    for idx in (2, 3):
        bf = p.face('head', 'bottom', index=idx)
        for y in range(bf.fh):
            for x in range(bf.fw):
                bf.fclear(x, y)
    for side in ('front', 'right', 'left', 'back'):
        locks(p.face('head', side, index=3), 3, 4, 'side' + side)
    for part in ('hair_back', 'hair_back2', 'hair_back3', 'lock_r', 'lock_r2'):
        for side in ('front', 'right', 'back', 'left'):
            f = p.face(part, side)
            for x in range(f.fw):
                if h(part, side, x) < 0.3:
                    for y in range(f.fh):
                        f.fpx(x, y, lighten(f.fget(x, y), 0.35))
    for side in ('front', 'right', 'back', 'left'):
        locks(p.face('hair_back3', side), 4, 7, 'hb' + side)
        locks(p.face('lock_r2', side), 4, 3, 'lk' + side)


def paint_gown(p, glow, form):
    for part, tag, ramp_to in (('skirt', 'sk1', 0.0), ('skirt2', 'sk2', 0.15), ('skirt3', 'sk3', 0.3)):
        for side in ('front', 'right', 'back', 'left'):
            f = p.face(part, side, tag=tag)
            for x in range(f.fw):                       # navy and white stripes, a navy hem
                stripe = (x // 4) % 3 == 1
                for y in range(f.fh):
                    if stripe:
                        f.fpx(x, y, mix(f.fget(x, y), NAVY, 0.75))
            for y in range(f.fh - 3, f.fh):
                for x in range(f.fw):
                    f.fpx(x, y, mix(NAVY, ICE, 0.3 if y == f.fh - 3 else 0.0))
            trim(f, GOLDC, rows=(f.fh - 4,), shine=True)
    for part, tag in (('skirt', 'pn1'), ('skirt2', 'pn2'), ('skirt3', 'pn3')):
        f = p.face(part, 'front', tag=tag)
        g = p.face(part, 'front', tag=tag, glow=True)
        for y in range(f.fh):
            for x in range(f.fw):
                d = abs(x - (f.fw - 1) / 2.0) / (f.fw / 2.0)
                f.fpx(x, y, mix(lighten(ICE, 0.2), NAVY, min(1.0, d * 1.2)))
                if d < 0.45 and (x + y) % 3 == 0:
                    g.fpx(x, y, shade(glow, 0.8))
        diamond(f, f.fw // 2, f.fh // 2, 2, ICE, g, glow)
    for part in ('skirt3',):
        for i in range(8):
            for side in ('front', 'right', 'back', 'left', 'top'):
                f = p.face(part, side, index=2 + i)
                g = p.face(part, side, index=2 + i, glow=True)
                for y in range(f.fh):
                    for x in range(f.fw):
                        if (x + y) % 2 == 0:
                            g.fpx(x, y, shade(glow, 0.7 + 0.1 * form))
    bd = p.face('chest', 'front', tag='bodice')
    for y in range(bd.fh):
        for x in range(bd.fw):
            if abs(x - (bd.fw - 1) / 2.0) < 4:
                bd.fpx(x, y, mix(bd.fget(x, y), NAVY, 0.8))
    trim(bd, GOLDC, cols=(5, -6), shine=True)
    for idx, size in ((3, 3), (4, 2)):
        f = p.face('chest', 'front', index=idx)
        g = p.face('chest', 'front', index=idx, glow=True)
        diamond(f, f.fw // 2, f.fh // 2, size, ICE, g, glow)


def paint_ice(p, glow, form):
    for part in ('mantle', 'mantle2', 'mantle3'):
        for side in ('front', 'right', 'back', 'left'):
            f = p.face(part, side)
            g = p.face(part, side, glow=True)
            for y in range(f.fh):
                for x in range(f.fw):
                    if (x * 3 + y * 2) % 11 == 0 or (x * 5 - y) % 13 == 0:
                        f.fpx(x, y, lighten(f.fget(x, y), 0.4))
                        g.fpx(x, y, shade(glow, 0.6))
            for x in range(f.fw):
                g.fpx(x, f.fh - 1, glow)
        ragged(p.face(part, 'back'), 4 if part == 'mantle3' else 1, (part, 'b'))
        ragged(p.face(part, 'front'), 4 if part == 'mantle3' else 1, (part, 'f'))
    for part in ('crown',):
        for i in range(12):
            for side in ('front', 'right', 'back', 'left'):
                g = p.face(part, side, index=i, glow=True)
                for y in range(g.fh):
                    g.fpx(0, y, glow)
                    if i in (1, 11):
                        g.fpx(g.fw - 1, y, lighten(glow, 0.3))
        gf = p.face('crown', 'front', index=2)
        diamond(gf, gf.fw // 2, gf.fh // 2, 2, ICE, p.face('crown', 'front', index=2, glow=True), lighten(glow, 0.4))
    for k in range(6):
        for idx in (0, 1):
            for side in ('front', 'right', 'back', 'left'):
                g = p.face('flake_%d' % k, side, index=idx, glow=True)
                for y in range(g.fh):
                    for x in range(g.fw):
                        g.fpx(x, y, glow if (x + y) % 2 == 0 else shade(glow, 0.6))
    gf = p.face('staff', 'front', index=4)
    diamond(gf, gf.fw // 2, gf.fh // 2, 2, ICE, p.face('staff', 'front', index=4, glow=True), (220, 245, 255))
    for side in ('front', 'right', 'back', 'left'):
        f = p.face('staff', side, index=0)
        g = p.face('staff', side, index=0, glow=True)
        for y in range(0, f.fh, 3):
            g.fpx(0, y, shade(glow, 0.7))
    if form >= 2:
        for tag in ('w1', 'w2', 'w3', 'w4'):
            for side in ('front', 'back', 'right', 'left'):
                g = p.face('wing_r', side, tag=tag, glow=True)
                f = p.face('wing_r', side, tag=tag)
                for y in range(f.fh):
                    for x in range(f.fw):
                        if (x + 2 * y) % 5 == 0:
                            g.fpx(x, y, glow)
                for x in range(f.fw):
                    if h(tag, side, x) < 0.5:
                        f.fclear(x, f.fh - 1)


def variants():
    cloth = {'base': '#f0f2f8', 'style': 'cloth'}
    navy = {'base': '#2a3c8a', 'style': 'cloth'}
    crystal = {'base': '#8fd0ff', 'style': 'crystal'}
    ice = {'base': '#b6e2ff', 'style': 'ice'}
    hair = {'base': '#e6e8f2', 'style': 'hair'}
    pal = {
        'face': {'base': '#f4e6e6', 'style': 'skin'}, 'glove': {'base': '#f2f4fa', 'style': 'cloth'},
        'top': hair, 'bangs': hair, 'side': hair, 'back': hair, 'back2': dict(hair, base='#dfe2ee'),
        'back3': dict(hair, base='#d8dcea'), 'lock': hair, 'lock2': dict(hair, base='#dfe2ee'),
        'band': crystal, 'spire': dict(crystal, base='#a8dcff'), 'spike': crystal, 'spike2': crystal,
        'spike3': crystal, 'spike4': crystal, 'crown_gem': {'base': '#5ab4ff', 'style': 'gem'},
        'collar': {'base': '#f4f6fb', 'style': 'fur', 'dark': '#cfd8e8', 'light': '#ffffff'},
        'stole': {'base': '#eef2fa', 'style': 'fur', 'dark': '#cfd8e8', 'light': '#ffffff'},
        'bodice': cloth, 'waist': navy, 'brooch': {'base': '#5ab4ff', 'style': 'gem'},
        'gem2': {'base': '#5ab4ff', 'style': 'gem'}, 'puff': cloth, 'puff_trim': {'base': '#d8c48a', 'style': 'gold'},
        'sleeve': cloth, 'sleeve2': cloth, 'cuff': dict(cloth, base='#e8ecf6'), 'cuff_ice': ice,
        'skirt1': cloth, 'skirt2': cloth, 'skirt3': dict(cloth, base='#eceff8'), 'panel1': navy, 'panel2': navy,
        'panel3': navy, 'sash': {'base': '#d8c48a', 'style': 'gold'},
        'shard': ice, 'shard2': dict(ice, base='#a6daff'), 'shard3': dict(ice, base='#c4e8ff'),
        'mantle': dict(ice, base='#cfeaff'), 'mantle2': dict(ice, base='#c4e4ff'), 'mantle3': dict(ice, base='#b8deff'),
        'shaft': crystal, 'grip': {'base': '#d8c48a', 'style': 'gold'}, 'foot': crystal,
        'flake_gem': {'base': '#5ab4ff', 'style': 'gem'}, 'flake': crystal, 'twig': crystal,
        'wing1': ice, 'wing2': dict(ice, base='#a8dcff'), 'wing3': dict(ice, base='#9ad4ff'), 'wing4': ice,
    }
    pal['staff_collar'] = {'base': '#d8c48a', 'style': 'gold'}

    def deco(p, form=0):
        eye = (90, 180, 255) if form < 3 else (150, 220, 255)
        glow = GLOW if form < 3 else (160, 230, 255)
        paint_face(p, eye)
        paint_hair(p)
        paint_gown(p, glow, form)
        paint_ice(p, glow, form)
    frost_pal = dict(pal, skirt1=dict(pal['skirt1'], base='#dce8fa'), skirt2=dict(pal['skirt2'], base='#d4e2f8'),
                     skirt3=dict(pal['skirt3'], base='#ccdcf6'), panel1=dict(pal['panel1'], base='#1c2a6e'),
                     panel2=dict(pal['panel2'], base='#1c2a6e'), panel3=dict(pal['panel3'], base='#1c2a6e'))
    lift = {'base': dict(pos=(0, HOVER, 0))}
    big_crown = dict(lift, crown=dict(scale=(1.2, 1.25, 1.2)))
    return [Variant('b5', palette=pal, poses=lift, paint=deco, glow=True, extra=dict(scale=2.3, hitbox=(2.6, 6.8))),
            Variant('b5_p1', palette=pal, poses=big_crown, paint=lambda p: deco(p, 1), glow=True, extra=dict(scale=2.3)),
            Variant('b5_p2', show={'ice_wings'}, palette=pal, poses=big_crown, paint=lambda p: deco(p, 2), glow=True,
                    extra=dict(scale=2.3)),
            Variant('b5_p3', show={'ice_wings'}, palette=frost_pal,
                    poses=dict(big_crown, ice_wings=dict(scale=(1.3, 1.25, 1.0))), paint=lambda p: deco(p, 3),
                    glow=True, extra=dict(scale=2.3))]


TRAILS = [('staff', STAFF_MID, FLAKE_TOP)]


def rig():
    vs = variants()
    base = Rig('icequeen', 'IcequeenBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('icequeen', 'IcequeenBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 3.2
    return finish_rig(r, 0.0, TRAILS)
