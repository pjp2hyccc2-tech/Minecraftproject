"""BANDIT boss rig: b1 地下都市の盗賊王 (the bandit king Laszlo), from the concept sheet.

A lean human with the proportions of the sheet (head, long torso under a knee-length coat, long legs in boots): messy
silver hair with bangs, side locks and tufts, a tilted gold crown with an amethyst, red eyes and a scar, a white fur
mantle with lapels, a gold chain with a ruby pendant, a dark leather coat with belts, a buckle and pouches, gold bands
on the wrists, knees and boot cuffs, a long crimson-to-purple cape torn at the hem, and two daggers (silver blades
etched in red, gold guards and pommels with amethysts) held in a forward grip.

Motion: quick and cocky. The cape (three segments), the coat flaps, the hair tail, the pendant and the pouches swing
after every move (spring pass); feet are planted by IK. Actions: a leaping double plunge into the floor (slam), an
X-cross slash (sweep), a knife-fan throw (cast), a dash stab (charge), a whistle for the gang (summon), a taunting laugh
(roar), a stamping tantrum (phase change), and a death where he sinks to his knees and falls on his face, the crown
rolling off. Timings are those of the old b1 look (BossRigData), so the fight is unchanged."""
import math

import eranim
from boss_kit import chain_links, diamond, frect, gem, h, line, ragged, ramp, rivets, trim
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
TIP_R = (-6.5, 9.5, -11.0)          # dagger tips (rest coordinates of dagger_r / dagger_l)
TIP_L = (6.5, 9.5, -11.0)
GUARD_R = (-6.5, 9.5, -3.4)
GUARD_L = (6.5, 9.5, -3.4)
PALM_R = (-6.5, 9.5, -2.2)


def arm(s):
    """Right (s = -1) or left (s = 1) arm: sleeve, fur pad, forearm with bracer and gold band, hand, dagger."""
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x0 = -8.5 if s < 0 else 4.5           # outer-left corner of a 4 wide box
    dx = -7.0 if s < 0 else 6.0           # dagger column
    dagger = P('dagger_' + name[0], (6.5 * s, 9.5, 0),
               C(dx - 0.5, 8.5, 1.8, 2, 2, 1, 'gold:pommel', **k('pommel')),
               C(dx, 9, -2.2, 1, 1, 4, 'leather:grip', **k('grip')),
               C(dx, 7.5, -3.2, 1, 4, 1, 'gold:guard', **k('guard')),
               C(dx, 8.5, -9.2, 1, 2, 6, 'metal:blade', **k('blade')),
               C(dx, 9, -11.2, 1, 1, 2, 'metal:tip', **k('tip')), rot=(38, 0, 0))
    hand = P(name + '_hand', (6.5 * s, 8, 0), C(x0, 8, -2, 4, 3, 4, 'skin:hand', **k('hand')), dagger)
    fore = P(name + '_forearm', (6.5 * s, 2, 0),
             C(x0, 2, -2, 4, 6, 4, 'leather:sleeve2', **k('farm')),
             C(x0, 3.5, -2, 4, 3, 4, 'leather:bracer', inflate=0.3, **k('bracer')),
             C(x0, 6.8, -2, 4, 1, 4, 'gold:wrist', inflate=0.45, **k('wrist')),
             hand, rot=(-30 if s < 0 else -24, 0, 0))
    return P(name + '_arm', (6.5 * s, -4, 0),
             C(x0, -5, -2, 4, 7, 4, 'leather:sleeve', **k('uarm')),
             C(-9 if s < 0 else 4, -6.2, -2.5, 5, 3, 5, 'fur:pad', **k('pad')),
             fore, rot=((-10, 0, 7) if s < 0 else (-6, 0, -7)))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x0 = -4.2 if s < 0 else 0.2
    foot = P(name + '_foot', (2.2 * s, 20.5, 0), C(x0, 21, -3.5, 4, 3, 6, 'leather:boot2', **k('boot')),
             C(x0, 20.4, -2, 4, 1, 4, 'gold:cuff2', inflate=0.2, **k('ankle')))
    shin = P(name + '_shin', (2.2 * s, 16, 0), C(x0, 16, -2, 4, 5, 4, 'cloth:pants2', **k('shin')),
             C(x0, 16, -2, 4, 1, 4, 'gold:band', inflate=0.3, **k('kband')),
             C(x0, 18, -2, 4, 3, 4, 'leather:shaft', inflate=0.25, **k('shaft')),
             C(x0, 18, -2, 4, 1, 4, 'gold:cuff', inflate=0.45, **k('cuff')), foot)
    return P(name + '_leg', (2.2 * s, 10.5, 0), C(x0, 10, -2, 4, 6, 4, 'cloth:pants', **k('thigh')), shin)


def geometry():
    crown = P('crown', (0, -14.2, 0),
              C(-4.5, -16.2, -4.5, 9, 2, 9, 'gold:crown'),
              C(-1, -20.2, -4.8, 2, 4, 1, 'gold:spike_c'),
              C(-4.5, -19.2, -4.8, 2, 3, 1, 'gold:spike', tag='sp'),
              C(2.5, -19.2, -4.8, 2, 3, 1, 'gold:spike', share='sp'),
              C(-4.8, -19.2, -1, 1, 3, 2, 'gold:spike_s', tag='ss'),
              C(3.8, -19.2, -1, 1, 3, 2, 'gold:spike_s', share='ss'),
              C(-4.5, -19.2, 3.8, 2, 3, 1, 'gold:spike', share='sp'),
              C(2.5, -19.2, 3.8, 2, 3, 1, 'gold:spike', share='sp'),
              C(-1, -19.7, 3.8, 2, 3, 1, 'gold:spike_b'),
              C(-1.5, -16.6, -5.3, 3, 3, 1, 'gem:amethyst'), rot=(0, 0, -7))
    head = P('head', (0, -5, 0),
             C(-4, -13, -4, 8, 8, 8, 'skin:face', tag='face'),
             C(-4.5, -14.2, -4.5, 9, 3, 9, 'hair:hair_top'),
             C(-4.5, -12.6, -5, 9, 3, 1, 'hair:bangs'),
             C(-5.2, -12.5, -4.2, 1, 8, 8, 'hair:side', tag='side'),
             C(4.2, -12.5, -4.2, 1, 8, 8, 'hair:side', share='side', mirror=True),
             C(-4.5, -12.5, 3.8, 9, 8, 2, 'hair:back'),
             C(-6.4, -12.4, -2.5, 2, 2, 3, 'hair:tuft', tag='tf1'),
             C(4.4, -12.4, -2.5, 2, 2, 3, 'hair:tuft', share='tf1', mirror=True),
             C(-6.1, -9.6, 0.2, 2, 2, 3, 'hair:tuft', tag='tf2'),
             C(4.1, -9.6, 0.2, 2, 2, 3, 'hair:tuft', share='tf2', mirror=True),
             C(-3.2, -15.3, -3.6, 2, 1, 3, 'hair:tuft', tag='tf3'),
             C(1.6, -15.3, -1.2, 2, 1, 3, 'hair:tuft', share='tf3'),
             C(-0.8, -13.8, -5.7, 2, 3, 1, 'hair:lock'),
             crown,
             P('hair_tail', (0, -5, 4.8), C(-3.5, -5, 4.3, 7, 3, 1, 'hair:tail')))
    pendant = P('pendant', (0, -4.5, -2.9), C(-1, -1.6, -3.3, 2, 2, 1, 'gold:pendant'),
                C(-0.5, -1.1, -3.8, 1, 1, 1, 'gem:ruby'))
    cape = P('cape', (0, -5.5, 4.4), C(-6.5, -5.5, 4.0, 13, 8, 1, 'cloth:cape'),
             P('cape_mid', (0, 2.5, 4.5), C(-6.5, 2.5, 4.0, 13, 8, 1, 'cloth:cape2'),
               P('cape_low', (0, 10.5, 4.5), C(-7.5, 10.5, 4.0, 15, 10, 1, 'cloth:cape3'))), rot=(4, 0, 0))
    chest = P('chest', (0, 2, 0),
              C(-4.5, -5, -2.5, 9, 7, 5, 'leather:coat', tag='chest'),
              C(-7, -6.4, -3.3, 14, 4, 8, 'fur:mantle', tag='mantle'),
              C(-5.5, -7.2, 2.0, 4, 3, 3, 'fur:ftuft', tag='ft'), C(1.5, -7.2, 2.0, 4, 3, 3, 'fur:ftuft', share='ft', mirror=True),
              C(-5, -8, -3.5, 10, 3, 7, 'fur:collar', tag='collar'),
              C(-4.2, -5, -3.1, 2, 6, 1, 'fur:lapel', tag='lapel'),
              C(2.2, -5, -3.1, 2, 6, 1, 'fur:lapel', share='lapel', mirror=True),
              head, pendant, arm(-1), arm(1), cape)
    torso = P('torso', (0, 8, 0), C(-4, 2, -2.5, 8, 6, 5, 'leather:coat2', tag='waist'),
              C(-4.5, 5.5, -3, 9, 1, 6, 'leather:belt2'), chest, rot=(2, 0, 0))
    hips = P('hips', (0, 9, 0),
             C(-4.5, 8, -2.5, 9, 3, 5, 'leather:coat3'),
             C(-5, 8.5, -3, 10, 2, 6, 'leather:belt', tag='belt'),
             C(-1.5, 8.3, -3.5, 3, 2, 1, 'gold:buckle'),
             P('pouch_r', (-5.0, 9.4, -1.0), C(-6.2, 9.4, -2.5, 2, 3, 3, 'leather:pouch', tag='pouch')),
             P('pouch_l', (5.0, 9.4, -1.0), C(4.2, 9.4, -2.5, 2, 3, 3, 'leather:pouch', share='pouch', mirror=True)),
             P('coat_r', (-3, 10.5, -2.7), C(-5, 10.5, -3.2, 4, 7, 1, 'leather:flap', tag='flap')),
             P('coat_l', (3, 10.5, -2.7), C(1, 10.5, -3.2, 4, 7, 1, 'leather:flap', share='flap', mirror=True)),
             P('coat_back', (0, 10.5, 2.9), C(-5, 10.5, 2.6, 10, 8, 1, 'leather:flap_b')),
             torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['cape', 'cape_mid', 'cape_low'], rest='down', axes='xz', hang=0.9, freq=1.25, damp=0.28, inertia=10.0,
          falloff=1.15, limits={'x': (-4.0, 100.0), 'z': (-30.0, 30.0)},
          drag={'walk': (16.0, 0.0, 0.0), 'charge': (12.0, 0.0, 0.0), 'roar': (5.0, 0.0, 0.0)},
          per_clip={'death': {'gain': 0.0, 'limits': {'x': (-90.0, 100.0), 'z': (-30.0, 30.0)}}}),
    Chain(['coat_r'], rest='down', axes='xz', hang=0.85, freq=1.9, damp=0.32, inertia=7.0,
          limits={'x': (-75.0, 25.0), 'z': (-20.0, 30.0)}, drag={'walk': (-5.0, 0.0, 0.0)}),
    Chain(['coat_l'], rest='down', axes='xz', hang=0.85, freq=1.95, damp=0.32, inertia=7.0,
          limits={'x': (-75.0, 25.0), 'z': (-30.0, 20.0)}, drag={'walk': (-5.0, 0.0, 0.0)}),
    Chain(['coat_back'], rest='down', axes='xz', hang=0.85, freq=1.7, damp=0.3, inertia=8.0,
          limits={'x': (-15.0, 80.0), 'z': (-20.0, 20.0)}, drag={'walk': (8.0, 0.0, 0.0)}),
    Chain(['hair_tail'], rest='down', axes='xz', hang=0.7, freq=2.2, damp=0.35, inertia=5.0,
          limits={'x': (-20.0, 60.0)}),
    Chain(['pendant'], rest='down', axes='xz', hang=0.9, freq=2.6, damp=0.25, inertia=6.0,
          limits={'x': (-60.0, 40.0), 'z': (-40.0, 40.0)}),
    Chain(['pouch_r'], rest='down', axes='xz', hang=0.8, freq=2.8, damp=0.35, inertia=4.0,
          limits={'x': (-35.0, 35.0), 'z': (-10.0, 30.0)}),
    Chain(['pouch_l'], rest='down', axes='xz', hang=0.8, freq=2.9, damp=0.35, inertia=4.0,
          limits={'x': (-35.0, 35.0), 'z': (-30.0, 10.0)}),
]
GAIT = Gait(1.5, stride=12.0, duty=0.6, lift=3.2, heel=10.0, toe=18.0)
SKIRT = ('cape', 'cape_mid', 'cape_low', 'coat_r', 'coat_l', 'coat_back', 'hair_tail', 'pendant', 'pouch_r', 'pouch_l')


def cyc(t, T, k=1.0, ph=0.0):
    return math.cos(2.0 * math.pi * (k * t / T - ph))


def idle(x):
    T = 4.0
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.6)
    c.fn('base', 'pos', lambda t: (0.45 * sway(t), 0.3 * b(t) - 0.12, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.6 * math.sin(2 * math.pi * t / T + 1.2), 1.4 * sway(t)), 16)
    c.fn('torso', 'rot', lambda t: (-1.0 * b(t), -1.2 * math.sin(2 * math.pi * t / T + 1.2), -0.9 * sway(t)), 16)
    c.fn('chest', 'rot', lambda t: (-1.8 * b(t), 0.0, -0.6 * sway(t - 0.2)), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.016 * b(t), 1.0 + 0.01 * b(t), 1.0 + 0.026 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (0.8, 0.0, 's'), (1.3, -9.0, 's'), (2.1, -9.0, 's'), (2.6, 6.0, 's'), (3.4, 6.0, 's'),
            (T, 0.0, 's')]
    tilt = [(0.0, 0.0, 's'), (1.2, 0.0, 's'), (1.5, 5.0, 'o'), (2.2, 5.0, 's'), (2.6, 0.0, 's'), (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.2 * b(t - 0.3) + 0.4, eranim.eased_value(look, t), eranim.eased_value(tilt, t)),
         40)
    c.fn('right_arm', 'rot', lambda t: (1.2 * math.sin(2 * math.pi * t / T), 0.0, 1.4 * b(t - 0.1)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.2 * math.sin(2 * math.pi * t / T), 0.0, -1.6 * b(t - 0.15)), 12)
    c.fn('right_forearm', 'rot', lambda t: (-2.0 * b(t - 0.3), 0.0, 0.0), 12)
    c.fn('left_forearm', 'rot', lambda t: (-2.5 * b(t - 0.35), 0.0, 0.0), 12)
    flick = [(0.0, 0.0, 's'), (2.6, 0.0, 's'), (2.8, 22.0, 'O'), (3.05, -6.0, 's'), (3.3, 0.0, 's'), (T, 0.0, 's')]
    roll = [(0.0, 0.0, 's'), (0.9, 0.0, 's'), (1.15, -28.0, 'O'), (1.4, 8.0, 's'), (1.6, 0.0, 's'), (T, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(flick, t), 0.0, 0.0), 40)
    c.fn('dagger_l', 'rot', lambda t: (0.0, 0.0, eranim.eased_value(roll, t)), 40)
    feet(c, x, samples=16)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: cyc(t, T, k, ph)
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-0.8 * w(t, 1, 0.25), -0.5 - 0.7 * w(t, 2, 0.06), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.0 * w(t, 2, 0.1), -7.0 * w(t), 3.0 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (4.0 + 1.0 * w(t, 2, 0.15), 4.0 * w(t, 1, 0.04), -2.0 * s1(t - 0.05)), 24)
    c.fn('chest', 'rot', lambda t: (1.0 * w(t, 2, 0.2), 4.0 * w(t, 1, 0.08), -2.0 * s1(t - 0.1)), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.2 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.1), 1.2 * s1(t - 0.15)), 24)
    c.fn('right_arm', 'rot', lambda t: (20.0 * w(t, 1, 0.04), 0.0, 1.5 + 1.5 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-20.0 * w(t, 1, 0.04), 0.0, -1.5 - 1.5 * w(t, 2, 0.1)), 24)
    c.fn('right_forearm', 'rot', lambda t: (-6.0 - 10.0 * max(0.0, -w(t, 1, 0.1)), 0.0, 0.0), 24)
    c.fn('left_forearm', 'rot', lambda t: (-6.0 - 10.0 * max(0.0, w(t, 1, 0.1)), 0.0, 0.0), 24)
    c.fn('right_hand', 'rot', lambda t: (-6.0 * w(t, 1, 0.18), 0.0, 0.0), 24)
    c.fn('left_hand', 'rot', lambda t: (6.0 * w(t, 1, 0.18), 0.0, 0.0), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Leaping double plunge: a crouch, a hop with both daggers raised high, then both blades driven into the floor
    in front of him as he lands in a deep crouch (impact), a menacing look up, and he pulls them free."""
    A, B, Cc, I, F, R, E = 0.32, 0.62, 0.82, 1.1, 1.5, 1.85, 2.25
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -3.2, 1.0), (B, 0, 5.5, -2.0, 'o'), (Cc, 0, 6.0, -3.5, 's'),
                   (I, 0, -5.4, -5.0, 'I'), (I + 0.12, 0, -5.9, -5.2, 'O'), (F, 0, -5.6, -5.0), (R, 0, -2.0, -2.4),
                   (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (A, 10, 0, 0), (B, -6, 0, 0), (I, 16, 0, 0, 'I'), (F, 14, 0, 0), (R, 6, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 14, 0, 0), (B, -12, 0, 0), (Cc, -16, 0, 0), (I, 30, 0, 0, 'I'),
                    (I + 0.12, 33, 0, 0, 'O'), (F, 28, 0, 0), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -8, 0, 0), (I, 10, 0, 0, 'I'), (F, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (A, -10, 0, 0), (B, -6, 0, 0), (I, -30, 0, 0, 'I'), (I + 0.2, -38, 0, 6),
                   (F, -34, 0, 4), (R, -10, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, 40, 0, 10 * sz), (B, -168, 0, -6 * sz), (Cc, -172, 0, -8 * sz),
                                (I, -70, 0, 6 * sz, 'I'), (F, -66, 0, 6 * sz), (R, -110, 0, 4 * sz), (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (A, -20, 0, 0), (B, -40, 0, 0), (I, -6, 0, 0, 'I'),
                                    (F, -8, 0, 0), (R, -30, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (A, 20, 0, 0), (B, 70, 0, 0), (I, 64, 0, 0, 'I'), (F, 64, 0, 0),
                                 (R, 30, 0, 0), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.38, 1.04, (-3.0, 1.2), 6.5), (1.9, 2.2, (0.0, 0.0), 2.0)],
         left_plan=[(0.4, 1.0, (-5.0, 1.0), 6.5), (1.86, 2.16, (0.0, 0.0), 2.0)])
    for t in (I, I + 0.12, F):
        for side, tip in (('right', TIP_R), ('left', TIP_L)):
            eranim.reach_y(c, x.rig, x.v, t, side + '_arm', 'x', 'dagger_' + side[0], tip, 24.6, -120.0, -20.0)
    return c


def melee_sweep(x):
    """X-cross slash: both blades crossed high over the shoulders, then torn down and outward through each other in
    front of him (impact as they cross) with a lunge of the left foot."""
    A, B, I, F, R, E = 0.18, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 1.0), (B, 0, -1.4, 1.6), (I, 0, -2.6, -3.6, 'I'), (F, 0, -2.8, -4.2, 'O'),
                   (R, 0, -1.6, -2.4), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 16, 0, 0, 'I'), (F, 20, 0, 0, 'O'), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -8, 0, 0), (I, 8, 0, 0, 'I'), (F, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 6, 0, 0), (I, -12, 0, 0, 'I'), (F, -14, 0, 0), (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (A, -60, 0, -10 * sz), (B, -150, 0, -42 * sz),
                                (I, -78, 0, 10 * sz, 'I'), (F, -34, 0, 70 * sz, 'O'), (R, -20, 0, 40 * sz),
                                (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (B, -70, 0, 0), (I, -10, 0, 0, 'I'), (F, -4, 0, 0), (E, 0, 0, 0)])
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (B, -30, 0, 0), (I, 10, 0, 20 * sz, 'I'), (F, 6, 0, 30 * sz),
                                 (R, 0, 0, 10 * sz), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.52, 0.78, (-6.0, 1.0), 3.0), (1.4, 1.72, (0.0, 0.0), 2.0)],
         right_plan=[(0.1, 0.4, (2.5, 0.8), 1.5), (1.45, 1.75, (0.0, 0.0), 1.5)])
    return c


def cast(x):
    """Knife fan: draws three knives from the belt, cocks the arm behind his head while aiming with the left hand,
    then whips them out across his body (release at the impact frame)."""
    A, B, I, F, R, E = 0.2, 0.5, 0.8, 1.05, 1.4, 1.85
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.6), (B, 0, -1.0, 1.4), (I, 0, -1.6, -2.4, 'I'), (F, 0, -1.6, -2.8),
                   (R, 0, -0.8, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 14, 0), (I, 0, -12, 0, 'I'), (F, 0, -16, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 6, 6, 0), (B, -6, 22, 0), (I, 10, -20, 0, 'I'), (F, 12, -26, 0, 'O'),
                    (R, 4, -10, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -4, 14, -4), (I, 6, -16, 4, 'I'), (F, 6, -20, 4), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 0, -30, 0), (I, -4, 30, 0, 'I'), (F, -4, 36, 0), (R, 0, 12, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 30, 0, 12), (B, -160, 0, -10), (I, -86, 0, -38, 'I'), (F, -50, 0, -52, 'O'),
                        (R, -20, 0, -20), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (A, -40, 0, 0), (B, -90, 0, 0), (I, -6, 0, 0, 'I'), (F, -10, 0, 0),
                            (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, -40, 0, 0), (I, 30, 0, 0, 'I'), (F, 40, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, -40, 0, -6), (B, -88, 0, -16), (I, -40, 0, -4, 'I'), (F, 18, 0, -10),
                       (R, 6, 0, -4), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -6, 0, 0), (I, -40, 0, 0), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.35, 0.62, (-4.0, 0.6), 2.5), (1.35, 1.65, (0.0, 0.0), 2.0)],
         right_plan=[(0.05, 0.3, (2.0, 0.8), 1.5), (1.4, 1.7, (0.0, 0.0), 1.5)])
    c.muzzle_part, c.muzzle_point = 'right_hand', PALM_R
    return c


def roar(x):
    """Taunting laugh (held while telegraphing): arms flung wide, head thrown back, shoulders shaking."""
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(7.0, 1.0, 1), shake(8.5, 1.0, 2)
    laugh = lambda t: math.sin(2 * math.pi * t / T * 3)
    c.fn('base', 'pos', lambda t: (0.0, -0.8 + 0.35 * abs(laugh(t)), 0.6), 18)
    c.fn('torso', 'rot', lambda t: (-9.0 + 1.2 * laugh(t), 0.0, 1.0 * tr2(t)), 24)
    c.fn('chest', 'rot', lambda t: (-6.0 + 1.5 * abs(laugh(t)), 0.0, 1.0 * tr(t)), 24)
    c.fn('head', 'rot', lambda t: (-20.0 + 3.0 * abs(laugh(t)), 0.0, 4.0 + 1.5 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-24.0 + 2.0 * tr(t), 0.0, 52.0 + 3.0 * laugh(t)), 18)
    c.fn('left_arm', 'rot', lambda t: (-24.0 + 2.0 * tr2(t), 0.0, -52.0 - 3.0 * laugh(t)), 18)
    c.fn('right_forearm', 'rot', lambda t: (-16.0, 0.0, 0.0), 6)
    c.fn('left_forearm', 'rot', lambda t: (-16.0, 0.0, 0.0), 6)
    c.fn('right_hand', 'rot', lambda t: (-30.0 + 6.0 * tr(t), 0.0, 0.0), 18)
    c.fn('left_hand', 'rot', lambda t: (-30.0 + 6.0 * tr2(t), 0.0, 0.0), 18)
    feet(c, x, right_plan=[(-1.0, -1.0, (2.0, 1.2), 0.0)], left_plan=[(-1.0, -1.0, (-1.5, 1.2), 0.0)], samples=12)
    return c


def charge(x):
    """Dash stab: he coils low with the right blade drawn back at the hip, then explodes forward into a long lunge
    with the dagger thrust straight ahead (impact), and steps back."""
    A, B, I, F, R, E = 0.15, 0.55, 0.85, 1.15, 1.45, 1.8
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -1.2, 0.6), (B, 0, -3.6, 2.4), (I, 0, -3.0, -10.0, 'I'),
                   (F, 0, -3.2, -10.6, 'O'), (R, 0, -1.8, -6.0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 10, 20, 0), (I, 14, -8, 0, 'I'), (F, 14, -10, 0), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, 22, 14, 0), (I, 30, -14, 0, 'I'), (F, 32, -16, 0, 'O'), (R, 14, -6, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -24, -24, 0), (I, -32, 16, 0, 'I'), (F, -32, 18, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 56, 0, 18), (I, -96, 0, 4, 'I'), (F, -100, 0, 4, 'O'), (R, -60, 0, 4),
                        (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -70, 0, 0), (I, 6, 0, 0, 'I'), (F, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (B, 10, 0, 0), (I, -10, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -70, 0, -20), (I, 40, 0, -26, 'I'), (F, 46, 0, -28, 'O'), (R, 20, 0, -10),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -60, 0, 0), (I, -20, 0, 0), (E, 0, 0, 0)])
    for t in (I, F):
        eranim.aim(c, x.rig, x.v, t, 'right_hand', 'x', 'dagger_r', GUARD_R, TIP_R, -4.0, -120.0, 120.0, forward=True)
    feet(c, x, left_plan=[(0.6, 0.84, (-12.0, 1.0), 3.5), (1.4, 1.72, (0.0, 0.0), 2.5)],
         right_plan=[(0.1, 0.42, (3.0, 1.0), 1.5), (0.7, 0.9, (-2.0, 1.0), 1.0), (1.42, 1.74, (0.0, 0.0), 1.5)])
    return c


def summon(x):
    """Whistles for his gang (left fingers at the lips, head up), then thrusts the right dagger at the ceiling
    (impact: the gang answers) and points it ahead."""
    A, B, Cc, I, F, R, E = 0.3, 0.6, 1.0, 1.25, 1.6, 2.0, 2.45
    c = Clip('summon', E, impact=I)
    tr = shake(9.0, 1.0, 4)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.6, 0), (Cc, 0, 0.8, 0), (I, 0, -1.2, -0.6, 'I'), (F, 0, -1.0, -0.6),
                   (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (B, -10, 0, 0), (Cc, -12, 0, 0), (I, -4, 0, 0, 'I'), (F, 4, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -8, 8, 0), (Cc, -10, 8, 0), (I, -6, -10, 0, 'I'), (F, -2, -6, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (1.2 * tr(t) * envelope(t, B, Cc, 0.1), 0.0, 0.0), 30)
    c.rot('head', [(0, 0, 0, 0), (A, -18, -10, 0), (B, -26, -12, 0), (Cc, -28, -12, 0), (I, -16, 8, 0, 'I'),
                   (F, -6, 4, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, -100, 0, 18), (B, -118, 0, 26), (Cc, -116, 0, 26), (I, -60, 0, -54, 'O'),
                       (F, -40, 0, -46), (R, -14, 0, -16), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (A, -100, 0, 0), (B, -112, 0, 0), (Cc, -112, 0, 0), (I, -10, 0, 0, 'O'),
                           (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (B, -20, 0, 0), (I, 0, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -30, 0, 8), (Cc, -40, 0, 14), (I, -176, 0, 6, 'I'), (I + 0.1, -172, 0, 6, 'b'),
                        (F, -168, 0, 8), (R, -90, 0, 6), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (Cc, -40, 0, 0), (I, -4, 0, 0, 'I'), (F, -6, 0, 0), (R, -10, 0, 0),
                            (E, 0, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (Cc, 10, 0, 0), (I, -60, 0, 0, 'I'), (F, -60, 0, 0), (R, -10, 0, 0),
                         (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.95, 1.2, (2.5, 1.0), 1.5), (2.0, 2.3, (0.0, 0.0), 1.5)])
    return c


def phase_change(x):
    """Tantrum: he clutches his face, stamps twice on the floor with his fists clenched, then rears up with his
    arms flung wide and screams (impact), shaking, before settling back into his stance."""
    E, I = 3.0, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(8.0, 1.0, 3)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -1.0, 1.2), (0.65, 0, -2.4, 0.8, 'I'), (0.85, 0, -1.6, 0.6),
                   (1.05, 0, -2.6, 0.4, 'I'), (1.35, 0, -1.6, 0.4), (I, 0, 0.8, 0.0, 'O'), (2.5, 0, 0.6, 0.0),
                   (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.3, 22, 0, 6), (0.65, 30, 0, -4, 'I'), (1.05, 30, 0, 6, 'I'), (1.35, 22, 0, 0),
                    (I, -18, 0, 0, 'O'), (2.5, -16, 0, 0), (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (1.0 * tr(t) * envelope(t, I, 2.5, 0.15), 0.0, 1.6 * tr(t + 0.3)
                                     * envelope(t, I, 2.5, 0.15)), 60)
    c.rot('head', [(0, 0, 0, 0), (0.3, 24, 0, 0), (0.65, 20, -10, 0), (1.05, 20, 10, 0), (1.35, 10, 0, 0),
                   (I, -36, 0, 0, 'O'), (2.5, -32, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.3, -126, 0, 20), (0.5, -40, 0, -8), (0.65, 10, 0, -14, 'I'),
                       (1.05, 14, 0, -16), (1.35, -30, 0, -40), (I, -50, 0, -100, 'O'), (2.5, -46, 0, -94),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (0.3, -118, 0, 0), (0.5, -60, 0, 0), (0.65, -70, 0, 0), (1.35, -40, 0, 0),
                           (I, -8, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.3, 10, 0, 14), (0.65, 16, 0, 18, 'I'), (1.05, 20, 0, 16), (1.35, -30, 0, 40),
                        (I, -50, 0, 100, 'O'), (2.5, -46, 0, 94), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (0.3, -80, 0, 0), (1.05, -80, 0, 0), (1.35, -40, 0, 0),
                            (I, -8, 0, 0, 'O'), (E, 0, 0, 0)])
    for side in ('right', 'left'):
        c.rot('%s_hand' % side, [(0, 0, 0, 0), (1.35, 0, 0, 0), (I, -40, 0, 0, 'O'), (2.5, -36, 0, 0), (E, 0, 0, 0)])
        c.add('%s_arm' % side, 'rot', lambda t, s=side: (2.0 * tr(t + (0.2 if s == 'left' else 0.0))
                                                          * envelope(t, I, 2.5, 0.15), 0.0, 0.0), 60)
    feet(c, x, right_plan=[(0.45, 0.65, (-1.0, 0.5), 5.0), (2.55, 2.85, (0.0, 0.0), 1.5)],
         left_plan=[(0.85, 1.05, (-0.5, 0.8), 5.0), (1.4, 1.62, (1.5, 1.6), 1.5), (2.55, 2.85, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.4
    c = Clip('stagger', E, impact=0.1)
    tr = shake(6.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.1, 0, -0.8, 2.8, 'O'), (0.4, 0, -1.2, 3.6), (0.75, 0, -0.5, 1.8), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.1, -20, 8, 6, 'O'), (0.4, -8, -4, 2), (0.7, 6, 2, -2), (1.0, 2, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.1, -26, 14, 6, 'O'), (0.4, -4, -10, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.1, -40, 0, 46, 'O'), (0.4, -18, 0, 20), (0.8, -4, 0, 4), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.1, -30, 0, -52, 'O'), (0.4, -14, 0, -24), (0.8, -4, 0, -6), (E, 0, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.12, 0.6, 0.12)), 24)
    feet(c, x, right_plan=[(0.06, 0.34, (5.0, 1.2), 2.5), (0.8, 1.15, (0.0, 0.0), 2.0)],
         left_plan=[(0.3, 0.55, (2.5, 0.2), 1.5), (0.95, 1.25, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """He reels, sinks to his knees with the daggers hanging from limp hands, then folds forward until his face and
    arms strike the floor (impact); the crown slips off his head and comes to rest beside him."""
    E, I = 3.6, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, -0.6, 1.6, 'O'), (1.1, 0, -7.6, 1.0, 'I'), (1.2, 0, -7.2, 1.0, 'O'),
                   (E, 0, -7.2, 1.0)])
    c.rot('hips', [(0, 0, 0, 0), (0.45, -6, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 6, 0, 0), (I, 26, 0, 0, 'I'),
                   (1.95, 22, 0, 0, 'b'), (E, 24, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.45, -22, 0, 4, 'O'), (1.1, 10, 0, -4), (1.35, 14, 0, -2), (I, 52, 0, 0, 'I'),
                    (1.95, 46, 0, 0, 'b'), (E, 48, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 2.0 * tr(t) * envelope(t, 0.3, 1.0, 0.2)), 36)
    c.rot('chest', [(0, 0, 0, 0), (1.1, 6, 0, 0), (I, 16, 0, 0, 'I'), (E, 16, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.45, -26, 0, 0, 'O'), (1.1, 26, 0, 0), (1.35, 30, 0, 0), (I, -10, -36, 0, 'I'),
                   (E, -10, -38, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -30, 0, 40, 'O'), (1.1, 6, 0, 8), (1.35, 4, 0, 10),
                        (I, -120, 0, 30, 'I'), (1.95, -114, 0, 34, 'b'), (E, -116, 0, 32)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, -24, 0, -40, 'O'), (1.1, 6, 0, -10), (1.35, 4, 0, -12),
                       (I, -60, 0, -60, 'I'), (1.95, -56, 0, -56, 'b'), (2.8, -56, 0, -56), (3.0, -66, 0, -50, 'o'),
                       (3.2, -56, 0, -56), (E, -56, 0, -56)])
    c.rot('right_forearm', [(0, 0, 0, 0), (1.1, -6, 0, 0), (I, -10, 0, 0), (E, -10, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (1.1, -6, 0, 0), (I, -30, 0, 0), (E, -30, 0, 0)])
    c.rot('right_hand', [(0, 0, 0, 0), (1.1, 40, 0, 0), (I, 30, 0, 0), (E, 30, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (1.1, 40, 0, 0), (I, 50, 0, 0), (E, 50, 0, 0)])
    c.rot('crown', [(0, 0, 0, 0), (1.5, 0, 0, 0), (I, -20, 0, 30, 'I'), (2.2, -60, 20, 70, 'O'), (2.6, -86, 30, 88, 'b'),
                    (E, -86, 30, 88)])
    c.pos('crown', [(0, 0, 0, 0), (1.5, 0, 0, 0), (I, 0, 1.0, -1.5, 'I'), (2.2, -2.0, 1.0, -7.0, 'O'),
                    (2.6, -3.0, -1.6, -8.5, 'b'), (E, -3.0, -1.6, -8.5)])
    # the cape is laid by hand (no spring in this clip): it billows as he reels and kneels, then drapes over his
    # back and hips down to the floor
    c.rot('cape', [(0, 0, 0, 0), (0.45, 16, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 8, 0, 0), (I, -2, 0, 0, 'I'),
                   (2.0, 2, 0, 0, 'b'), (E, 0, 0, 0)])
    c.rot('cape_mid', [(0, 0, 0, 0), (0.45, 10, 0, 0), (1.1, 6, 0, 0), (1.35, 4, 0, 0), (I, -46, 0, 0, 'I'),
                       (2.05, -40, 0, 0, 'b'), (E, -42, 0, 0)])
    c.rot('cape_low', [(0, 0, 0, 0), (0.45, 8, 0, 0), (1.1, 4, 0, 0), (1.35, 2, 0, 0), (I, -44, 0, 6, 'I'),
                       (2.1, -36, 0, 4, 'b'), (E, -38, 0, 4)])
    # kneel: the ankles slide back with the toes on the floor while the body sinks onto the knees
    feet(c, x, right_plan=[(0.5, 1.1, (6.6, 0.4), 1.5)], left_plan=[(0.55, 1.12, (6.4, 0.4), 1.5)], y_off=0.0,
         right_holds=[(1.1, E, 64.0)], left_holds=[(1.12, E, 64.0)])
    for t in (1.1, 1.2, 1.35, I, 1.95, E):
        eranim.ground(c, x.rig, x.v, t, skip=SKIRT + ('crown', 'dagger_r', 'dagger_l'))
    eranim.rest_on_floor(c, x.rig, x.v, (2.6, E), 'crown', axis='z')
    eranim.rest_on_floor(c, x.rig, x.v, (I, 2.1, E), 'cape_low', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

HAIR = rgb('#d6d2de')
HAIR_DARK = rgb('#9a93ab')
HAIR_LIGHT = rgb('#f6f4fa')
GOLD = rgb('#e2b347')
GOLD_DARK = rgb('#9a6a1e')
AMETHYST = rgb('#a447e0')
RUBY = rgb('#d41e40')
SKIN = rgb('#e6b896')
CRIMSON = rgb('#8e1f3a')
PURPLE = rgb('#4c1747')


def locks(fc, period, depth, seed, side='bottom'):
    """Pointed hair locks along the bottom edge: transparent between the points (fine pixels)."""
    for x in range(fc.fw):
        u = (x + int(h(seed) * period)) % period
        d = abs(u - period / 2.0) / (period / 2.0)          # 0 at a point, 1 between points
        cut = int(round(d * depth + (h(seed, x) - 0.5) * 1.2))
        for k in range(max(0, cut)):
            fc.fclear(x, fc.fh - 1 - k)
        y = fc.fh - 1 - max(0, cut)
        if 0 <= y < fc.fh:
            fc.fpx(x, y, shade(fc.fget(x, y), 0.8))


def hair_strands(fc, seed):
    """Light and dark strands down a hair face."""
    for x in range(fc.fw):
        r = h(seed, x)
        if r < 0.22:
            for y in range(fc.fh):
                fc.fpx(x, y, lighten(fc.fget(x, y), 0.35))
        elif r > 0.8:
            for y in range(int(fc.fh * 0.3), fc.fh):
                fc.fpx(x, y, shade(fc.fget(x, y), 0.82))


def paint_face(p, eyes_glow=None, rage=False):
    f = p.face('head', 'front', tag='face')
    g = p.face('head', 'front', tag='face', glow=True)
    for x in range(16):                                         # hairline behind the bangs, shadow under them
        for y in range(5):
            f.fpx(x, y, mix(HAIR_DARK, HAIR, 0.4 + 0.1 * (y % 2)))
        f.fpx(x, 5, shade(SKIN, 0.78))
        f.fpx(x, 6, shade(SKIN, 0.9))
    brow = shade(HAIR_DARK, 0.75)
    for (x, y) in ((2, 5), (3, 5), (4, 6), (5, 6), (10, 6), (11, 6), (12, 5), (13, 5)):
        f.fpx(x, y, brow)
    lash = (40, 22, 32)
    white = (246, 240, 240)
    iris, iris_dark, iris_light, pupil = (206, 44, 76), (150, 22, 52), (255, 156, 180), (52, 10, 24)
    if rage:
        iris, iris_dark, iris_light = (240, 60, 90), (190, 30, 60), (255, 190, 210)
    for x in range(2, 6):
        f.fpx(x, 7, lash)
    for x in range(10, 14):
        f.fpx(x, 7, lash)
    f.fpx(1, 7, lash)
    f.fpx(14, 7, lash)
    row8 = {2: white, 3: iris_light, 4: pupil, 5: iris, 10: iris, 11: pupil, 12: iris_light, 13: white}
    row9 = {2: white, 3: iris, 4: iris_dark, 5: lash, 10: lash, 11: iris_dark, 12: iris, 13: white}
    for x, col in row8.items():
        f.fpx(x, 8, col)
    for x, col in row9.items():
        f.fpx(x, 9, col)
    for x in (3, 4, 11, 12):
        f.fpx(x, 10, shade(SKIN, 0.88))
    scar = (176, 70, 74)
    for (x, y) in ((4, 5), (4, 6), (4, 10), (4, 11), (5, 12)):
        f.fpx(x, y, mix(SKIN, scar, 0.8))
    f.fpx(7, 11, shade(SKIN, 0.86))
    f.fpx(8, 11, shade(SKIN, 0.9))
    f.fpx(7, 12, shade(SKIN, 0.8))
    for x in (2, 13):
        f.fpx(x, 12, mix(SKIN, (232, 140, 140), 0.35))
    mouth = (150, 82, 80)
    for x in (7, 8):
        f.fpx(x, 14, mouth)
    f.fpx(9, 13, mouth)
    f.fpx(6, 14, shade(SKIN, 0.82))
    for x in range(3, 13):
        f.fpx(x, 15, shade(SKIN, 0.9))
    if eyes_glow:
        for (x, y) in ((3, 8), (4, 8), (5, 8), (3, 9), (4, 9), (10, 8), (11, 8), (12, 8), (11, 9), (12, 9)):
            g.fpx(x, y, eyes_glow)
        g.fpx(3, 8, (255, 255, 255))
        g.fpx(12, 8, (255, 255, 255))


def paint_hair(p):
    for part, idx in (('head', 1), ('head', 2), ('head', 3), ('head', 5), ('head', 6), ('head', 8), ('head', 10),
                      ('head', 12)):
        for side in ('front', 'right', 'back', 'left', 'top'):
            hair_strands(p.face(part, side, index=idx), (part, idx, side))
    bangs = p.face('head', 'front', index=2)
    locks(bangs, 5, 6, 'bangs')
    locks(p.face('head', 'left', index=2), 2, 4, 'bangs_l')
    locks(p.face('head', 'right', index=2), 2, 4, 'bangs_r')
    for side in ('right', 'left', 'front', 'back'):
        locks(p.face('head', side, index=3), 4, 5, 'side' + side)
    for side in ('back', 'right', 'left'):
        locks(p.face('head', side, index=5), 4, 6, 'back' + side)
    for idx in (2, 3, 5, 12):
        bf = p.face('head', 'bottom', index=idx)
        for y in range(bf.fh):
            for x in range(bf.fw):
                bf.fclear(x, y)
    lock = p.face('head', 'front', index=12)
    locks(lock, 4, 4, 'lock')
    tail = p.face('hair_tail', 'back')
    hair_strands(tail, 'tail')
    locks(tail, 4, 5, 'tail')
    locks(p.face('hair_tail', 'front'), 4, 5, 'tail_f')


def paint_crown(p, gem_glow=None):
    for side in ('front', 'right', 'back', 'left'):
        f = p.face('crown', side, index=0)
        trim(f, lighten(GOLD, 0.2), rows=(0,))
        trim(f, GOLD_DARK, rows=(-1,), shine=False)
        for x in range(2, f.fw - 2, 6):
            gem(f, x, 1, 2, 2, AMETHYST if (x // 6) % 2 == 0 else RUBY,
                p.face('crown', side, index=0, glow=True) if gem_glow else None, gem_glow)
    for i in range(1, 9):
        for side in ('front', 'right', 'back', 'left'):
            f = p.face('crown', side, index=i)
            for y in range(f.fh):
                f.fpx(0, y, lighten(f.fget(0, y), 0.3))
            f.fpx(f.fw // 2, 0, (255, 246, 200))
    gf = p.face('crown', 'front', index=9)
    gem(gf, 0, 0, gf.fw, gf.fh, AMETHYST, p.face('crown', 'front', index=9, glow=True) if gem_glow else None,
        gem_glow)


def paint_fur(p):
    for tg, depth in (('mantle', 3), ('ft', 3), ('collar', 2), ('lapel', 3)):
        for side in ('front', 'right', 'back', 'left'):
            ragged(p.face('chest', side, tag=tg), depth, ('fur', tg, side), every=1)
    for side in ('front', 'right', 'back', 'left'):
        ragged(p.face('right_arm', side, index=1), 3, ('pad', side))


def paint_coat(p, glow=None):
    f = p.face('chest', 'front', tag='chest')
    for y in range(f.fh):                                    # open front of the coat over a dark shirt
        f.fpx(8, y, (22, 20, 26))
        f.fpx(9, y, (30, 27, 34))
    strap = rgb('#6e4428')
    line(f, 17, 0, 2, 13, strap)
    line(f, 17, 1, 2, 14, shade(strap, 0.8))
    for k in range(3, 14, 4):
        x = 17 - k * 15 // 13
        f.fpx(x, k, lighten(GOLD, 0.2))
    chain_links(f, 3, 0, 8, 7, GOLD, step=2)
    chain_links(f, 14, 0, 10, 7, GOLD, step=2)
    w = p.face('torso', 'front', tag='waist')
    for y in range(w.fh):
        w.fpx(7, y, (22, 20, 26))
        w.fpx(8, y, (30, 27, 34))
    for (x, y) in ((4, 3), (11, 3), (4, 8), (11, 8)):
        w.fpx(x, y, lighten(GOLD, 0.1))
    for side in ('front', 'back', 'right', 'left'):
        bf = p.face('torso', side, index=1)
        rivets(bf, GOLD, 4, inset=1, rows=(1,))
        hb = p.face('hips', side, index=1)
        rivets(hb, GOLD, 5, inset=1, rows=(1,))
    bk = p.face('hips', 'front', index=2)
    trim(bk, lighten(GOLD, 0.25), rows=(0,))
    frect(bk, 1, 1, 4, 2, GOLD_DARK)
    frect(bk, 2, 1, 3, 2, (60, 40, 20))
    for part in ('pouch_r',):
        pf = p.face(part, 'front')
        for x in range(pf.fw):
            pf.fpx(x, 2, shade(pf.fget(x, 2), 0.6))
        pf.fpx(pf.fw // 2, 3, GOLD)
    for part in ('coat_r', 'coat_back'):
        for side in ('front', 'back'):
            cf = p.face(part, side)
            trim(cf, shade(GOLD, 0.85), rows=(-2,), shine=True)
            for x in range(1, cf.fw - 1, 4):
                for y in range(0, cf.fh - 3, 3):
                    cf.fpx(x, y, lighten(cf.fget(x, y), 0.08))
    cr = p.face('coat_r', 'front')
    for y in range(cr.fh):
        cr.fpx(cr.fw - 1, y, shade(GOLD, 0.75))


def paint_limbs(p):
    for side in ('front', 'right', 'back', 'left'):
        b = p.face('right_forearm', side, index=1)
        for y in (1, b.fh - 2):
            for x in range(b.fw):
                b.fpx(x, y, shade(b.fget(x, y), 0.6))
        b.fpx(b.fw // 2, 1, lighten(GOLD, 0.2))
        for idx in (2,):
            rivets(p.face('right_forearm', side, index=idx), GOLD, 3, inset=0, rows=(0,))
        rivets(p.face('right_shin', side, index=1), GOLD, 3, inset=0, rows=(0,))
        rivets(p.face('right_shin', side, index=3), GOLD, 3, inset=0, rows=(0,))
    hf = p.face('right_hand', 'front')
    for x in range(1, hf.fw - 1, 2):                        # knuckles
        hf.fpx(x, hf.fh - 2, shade(hf.fget(x, hf.fh - 2), 0.82))
    bt = p.face('right_foot', 'front')
    for y in range(1, bt.fh - 1, 2):                        # laces
        bt.fpx(3, y, (150, 120, 90))
        bt.fpx(4, y + 1, (150, 120, 90))
    for side in ('front', 'right', 'back', 'left'):
        f = p.face('right_foot', side)
        trim(f, (34, 26, 22), rows=(-1,), shine=False)


def paint_cape(p, tear=1.0):
    segs = (('cape', CRIMSON, mix(CRIMSON, PURPLE, 0.25)), ('cape_mid', mix(CRIMSON, PURPLE, 0.25),
                                                            mix(CRIMSON, PURPLE, 0.6)),
            ('cape_low', mix(CRIMSON, PURPLE, 0.6), PURPLE))
    for part, top, bottom in segs:
        for side in ('back', 'right', 'left'):
            ramp(p.face(part, side), top, bottom, keep=0.6)
        ramp(p.face(part, 'front'), shade(top, 0.55), shade(bottom, 0.55), keep=0.5)
    bot = p.face('cape_low', 'bottom')
    for y in range(bot.fh):
        for x in range(bot.fw):
            bot.fclear(x, y)
    low = p.face('cape_low', 'back')
    trim(low, GOLD, rows=(-9, -8), shine=True)
    for side in ('back', 'front', 'right', 'left'):
        f = p.face('cape_low', side)
        ragged(f, int(7 * tear), ('cape', side), every=1, min_depth=1)
    for side in ('back', 'front'):
        f = p.face('cape_mid', side)
        for (cx, cy) in ((6, 9), (19, 5)):                     # a couple of holes
            if h(side, cx) < 0.9 * tear:
                for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (2, 1)):
                    f.fclear(cx + dx, cy + dy)



def paint_daggers(p, edge_glow=None):
    red = (176, 24, 44)
    for side in ('right', 'left'):
        b = p.face('dagger_r', side, tag='blade')
        gb = p.face('dagger_r', side, tag='blade', glow=True)
        for x in range(b.fw):
            b.fpx(x, 0, (244, 246, 252))
            b.fpx(x, b.fh - 1, (236, 238, 246))
            b.fpx(x, 1, (196, 200, 212))
            b.fpx(x, 2, (190, 194, 206))
            yy = 1 + ((x // 2) % 2)
            b.fpx(x, yy, red)
            if edge_glow:
                gb.fpx(x, yy, edge_glow)
                gb.fpx(x, 0, lighten(edge_glow, 0.5))
        t = p.face('dagger_r', side, tag='tip')
        gt = p.face('dagger_r', side, tag='tip', glow=True)
        for x in range(t.fw):
            t.fpx(x, 0, (244, 246, 252))
            if edge_glow:
                gt.fpx(x, 0, lighten(edge_glow, 0.5))
        t.fclear(0, 0)
        t.fclear(0, 1)
        t.fclear(1, 1)
        gd = p.face('dagger_r', side, tag='guard')
        gem(gd, 0, 3, 2, 2, AMETHYST, p.face('dagger_r', side, tag='guard', glow=True) if edge_glow else None,
            edge_glow)
    pm = p.face('dagger_r', 'back', tag='pommel')
    gem(pm, 1, 1, 2, 2, AMETHYST)


def paint_pendant(p, glow=None):
    r = p.face('pendant', 'front', index=1)
    gem(r, 0, 0, r.fw, r.fh, RUBY, p.face('pendant', 'front', index=1, glow=True) if glow else None, glow)
    pf = p.face('pendant', 'front', index=0)
    trim(pf, lighten(GOLD, 0.3), rows=(0, -1), cols=(0, -1))


def variants():
    gold = {'base': '#e2b347', 'style': 'gold'}
    leather = {'base': '#34303a', 'style': 'leather'}
    brown = {'base': '#6e4428', 'style': 'leather'}
    hair = {'base': '#d6d2de', 'style': 'hair'}
    fur = {'base': '#ece6dc', 'style': 'fur', 'dark': '#c9c1b4', 'light': '#fbf8f2'}
    pal = {
        'face': {'base': '#e6b896', 'style': 'skin'}, 'hand': {'base': '#dcaa88', 'style': 'skin'},
        'hair_top': hair, 'bangs': hair, 'side': hair, 'back': hair, 'tuft': hair, 'lock': hair,
        'tail': dict(hair, base='#ccc7d6'),
        'crown': gold, 'spike': dict(gold, base='#ecc35a'), 'spike_s': dict(gold, base='#e8bd52'), 'spike_c': dict(gold, base='#f0c860'),
        'spike_b': gold, 'amethyst': {'base': '#a447e0', 'style': 'gem'},
        'mantle': fur, 'collar': fur, 'ftuft': fur, 'lapel': dict(fur, base='#e4ddd1'), 'pad': fur,
        'coat': leather, 'coat2': leather, 'coat3': dict(leather, base='#2e2a33'), 'flap': leather,
        'flap_b': leather, 'sleeve': leather, 'sleeve2': dict(leather, base='#2f2b35'),
        'belt': brown, 'belt2': dict(brown, base='#5e3a22'), 'pouch': dict(brown, base='#7a4c2c'), 'bracer': brown,
        'grip': dict(brown, base='#4a2c1a'), 'shaft': {'base': '#3c2a22', 'style': 'leather'},
        'boot2': {'base': '#3a2820', 'style': 'leather'},
        'buckle': gold, 'band': gold, 'cuff': gold, 'cuff2': gold, 'wrist': gold, 'pendant': gold, 'pommel': gold,
        'guard': gold, 'ruby': {'base': '#d41e40', 'style': 'gem'},
        'pants': {'base': '#2c2a31', 'style': 'cloth'}, 'pants2': {'base': '#2a282f', 'style': 'cloth'},
        'cape': {'base': '#8e1f3a', 'style': 'cloth'}, 'cape2': {'base': '#7c1d3e', 'style': 'cloth'},
        'cape3': {'base': '#5e1a42', 'style': 'cloth'},
        'blade': {'base': '#cdd2dc', 'style': 'metal', 'rivets': False},
        'tip': {'base': '#cdd2dc', 'style': 'metal', 'rivets': False},
    }

    def deco(p, rage=False):
        paint_face(p, eyes_glow=(255, 120, 150) if rage else (210, 70, 100), rage=rage)
        paint_hair(p)
        paint_crown(p, gem_glow=(220, 140, 255) if rage else None)
        paint_fur(p)
        paint_coat(p)
        paint_limbs(p)
        paint_cape(p, tear=1.0 if not rage else 1.4)
        paint_daggers(p, edge_glow=(190, 90, 255) if rage else None)
        paint_pendant(p, glow=(255, 90, 120) if rage else None)
    return [Variant('b1', palette=pal, paint=deco, glow=True, extra=dict(scale=2.2, hitbox=(2.7, 5.4))),
            Variant('b1_p1', palette=pal, paint=lambda p: deco(p, rage=True), glow=True, extra=dict(scale=2.2))]


TRAILS = [('dagger_r', GUARD_R, TIP_R), ('dagger_l', GUARD_L, TIP_L)]


def rig():
    vs = variants()
    base = Rig('bandit', 'BanditBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('bandit', 'BanditBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 4.0
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
