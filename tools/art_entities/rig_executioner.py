"""EXECUTIONER boss rig: b4 凍獄の処刑人 (the executioner of the frozen prison), from the concept sheet.

A huge knight in dark iron rimed with frost: a great helm with a barred visor and glowing blue eyes, curved bone horns
with icicles; square pauldrons layered with snow, icicles hanging from their edges; heavy chains across the chest and
round the waist, the loose end swinging; a torn crimson cape and a tattered red tabard; armoured gauntlets, faulds,
greaves and sabatons. In the right hand a gigantic axe on a long haft: a crescent blade of black iron crusted with ice
along the edge, cracked with blue light, a spike behind and a chain hanging from the head. Later forms grow horns of
ice, glowing cracks and a crown of icicles.

Motion: heavy and deliberate; the axe drags its weight. Actions: an overhead two-handed chop into the floor (slam,
the frost nova), the execution: a great horizontal swing (sweep), a lash of the chain from the left hand (cast), a
lunge that drives the axe ahead (charge, the chain pull), the haft stamped on the floor with the left hand raised
(summon), a bellow with the axe held out (roar), the axe raised in both hands as the ice bursts (phase change), and a
death where he sinks to his knees on the axe and falls. Timings are those of the old b4 look (BossRigData)."""
import math

import eranim
from boss_kit import chain_links, crackle, frost, gem, h, ragged, ramp, rivets, trim
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
EDGE_TOP = (-9.0, 22.0, 15.0)        # the cutting edge of the axe (rest coordinates of part axe)
EDGE_LOW = (-9.0, 35.0, 15.0)
EDGE_MID = (-9.0, 28.5, 15.5)
AXE_NECK = (-9.0, 28.0, 2.0)
PALM_L = (9.0, 7.0, -2.6)


def icicles(x0, x1, y, z0, z1, mat, step=2, seed=0):
    """Icicles along the lower edge of a plate: thin boxes of varied length."""
    out = []
    xs = [x0 + i * step for i in range(int((x1 - x0) / step) + 1)]
    for i, x in enumerate(xs):
        length = 1 + int(h(seed, i) * 3)
        z = z0 if i % 2 == 0 else z1
        out.append(C(x, y, z, 1, length, 1, mat))
    return out


def horn(s):
    """A bone horn: a thick base going out from the helm, a middle rising outward, a tip curling up and in; the
    later forms crown the tips with spikes of ice."""
    name = 'r' if s < 0 else 'l'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    ice = P('ice_horn_' + name, (9.5 * s, -30, 0), C(9.5 * s - 1, -37, -1, 2, 7, 2, 'ice:ice_horn', **k('ih')),
            rot=(0, 0, 10 * s), opt=True)
    tip = P('horn_%s3' % name, (9.5 * s, -26, 0), C(9.5 * s - 0.5, -30, -0.5, 1, 4, 1, 'bone:horn_tip', **k('h3')),
            C(9.5 * s - 0.5, -31, -0.5, 1, 1, 1, 'ice:horn_ice', **k('h3i')), ice, rot=(0, 0, -42 * s))
    mid = P('horn_%s2' % name, (9.5 * s, -21, 0), C(9.5 * s - 1, -26, -1, 2, 5, 2, 'bone:horn_mid', **k('h2')), tip,
            rot=(0, 0, 22 * s))
    return P('horn_' + name, (4.5 * s, -19.5, 0), C(4.5 * s - (5 if s < 0 else 0), -21, -1.5, 5, 3, 3, 'bone:horn', **k('h1')),
             mid)


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 9.0 * s
    kids = []
    if s < 0:
        blade = [C(-10, 23, 2, 2, 11, 12, 'metal:axe_blade', tag='blade'),
                 C(-10, 21, 9, 2, 2, 5, 'metal:axe_horn', tag='ahorn'),
                 C(-10, 34, 9, 2, 2, 5, 'metal:axe_horn', share='ahorn'),
                 C(-10.5, 21.5, 14, 3, 14, 2, 'ice:edge', tag='edge'),
                 C(-10.5, 24, -1.5, 3, 8, 3, 'metal:socket'),
                 C(-9.5, 27, -6.5, 1, 2, 5, 'metal:spike'),
                 C(-10.5, 22, 2, 3, 1, 8, 'ice:blade_snow')]
        axe = P('axe', (cx, 7, 0),
                C(-10, -4, -1, 2, 38, 2, 'wood:haft'),
                C(-10.5, -6, -1.5, 3, 2, 3, 'metal:butt'),
                C(-10.5, 1, -1.5, 3, 1, 3, 'leather:wrap', tag='wrap'),
                C(-10.5, 13, -1.5, 3, 1, 3, 'leather:wrap', share='wrap'),
                *blade,
                P('axe_chain', (-9, 25, -1.2), C(-9.5, 25, -1.7, 1, 5, 1, 'chain:link', tag='ach'),
                  P('axe_chain2', (-9, 30, -1.2), C(-9.5, 30, -1.7, 1, 5, 1, 'chain:link', share='ach'))),
                rot=(-86, 0, 0))
        kids.append(axe)
    fist = P(name + '_hand', (cx, 5, 0), C(cx - 2.5, 5, -2.5, 5, 4, 5, 'metal:fist', **k('fist')), *kids)
    fore = P(name + '_forearm', (cx, -2, 0),
             C(cx - 3, -2, -3, 6, 7, 6, 'metal:gauntlet', **k('gaunt')),
             C(cx - 3, -2, -3, 6, 1, 6, 'ice:cuff_snow', inflate=0.3, **k('gsnow')),
             fist, rot=(-10 if s < 0 else -6, 0, 0))
    pz = [C(cx - 4.5 + (0 if s < 0 else 0), -14, -5, 9, 6, 10, 'metal:pauldron', **k('paul')),
          C(cx - 4, -9, -4.5, 8, 3, 9, 'metal:pauldron2', **k('paul2')),
          C(cx - 5, -15, -5.5, 10, 1, 11, 'ice:snow', **k('psnow'))]
    ics = icicles(cx - 4 if s < 0 else cx - 4, cx + 3.5, -6, -4.6, 3.6, 'ice:icicle', step=2, seed=name)
    return P(name + '_arm', (cx, -10, 0), *pz, *ics,
             C(cx - 2.5, -9, -2.5, 5, 7, 5, 'metal:arm', **k('uarm')),
             fore, rot=((-12, 0, 6) if s < 0 else (-4, 0, -6)))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 3.4 * s
    foot = P(name + '_foot', (cx, 20.5, 0), C(cx - 3.5, 21, -4.5, 7, 3, 8, 'metal:sabaton', **k('sab')),
             C(cx - 3.5, 20.5, -3, 7, 1, 6, 'ice:boot_snow', inflate=0.2, **k('bsnow')))
    shin = P(name + '_shin', (cx, 15, 0), C(cx - 3, 15, -3, 6, 6, 6, 'metal:greave', **k('greave')),
             C(cx - 3.5, 13.5, -3.8, 7, 3, 2, 'metal:knee', **k('knee')),
             C(cx - 3.5, 13.2, -3.9, 7, 1, 3, 'ice:knee_snow', **k('ksnow')), foot)
    return P(name + '_leg', (cx, 9.5, 0), C(cx - 3, 9, -3, 6, 6, 6, 'metal:cuisse', **k('cuisse')), shin)


def geometry():
    head = P('head', (0, -13, 0),
             C(-4.5, -23, -4.5, 9, 10, 9, 'metal:helm', tag='helm'),
             C(-4, -20, -5.2, 8, 6, 1, 'metal:visor', tag='visor'),
             C(-0.5, -24.5, -4.8, 1, 2, 9, 'metal:crest'),
             C(-5, -24, -5, 10, 1, 10, 'ice:helm_snow'),
             *icicles(-4, 4, -13, -5.4, -5.4, 'ice:helm_icicle', step=2, seed='helm'),
             horn(-1), horn(1),
             P('icicle_crown', (0, -24, 0),
               C(-4, -29, -4, 1, 5, 1, 'ice:crown_ice', tag='ci1'), C(-1.5, -31, -4.6, 2, 7, 1, 'ice:crown_ice2'),
               C(3, -29, -4, 1, 5, 1, 'ice:crown_ice', share='ci1'), C(-4, -28, 3, 1, 4, 1, 'ice:crown_ice3', tag='ci3'),
               C(3, -28, 3, 1, 4, 1, 'ice:crown_ice3', share='ci3'), opt=True))
    chest_chain = P('chest_chain', (0, -6, -4.6),
                    *[C(-11 + 2 * i, -6.5 + (0.0 if i % 2 == 0 else 0.25), -5.4, 2, 1 if i % 2 else 2, 1,
                        'chain:clink' if i % 2 else 'chain:clink2') for i in range(11)], rot=(0, 0, -38))
    cape = P('cape', (0, -12, 4.8), C(-7, -12, 4.6, 14, 9, 1, 'cloth:cape'),
             P('cape_mid', (0, -3, 4.8), C(-7.5, -3, 4.6, 15, 9, 1, 'cloth:cape2'),
               P('cape_low', (0, 6, 4.8), C(-8, 6, 4.6, 16, 11, 1, 'cloth:cape3'))), rot=(4, 0, 0))
    chest = P('chest', (0, 0, 0),
              C(-7, -12, -4.5, 14, 12, 9, 'metal:cuirass', tag='cuirass'),
              C(-4.5, -14, -3.5, 9, 2, 7, 'metal:gorget'),
              C(-7.5, -13, -5, 15, 1, 10, 'ice:chest_snow'),
              chest_chain, head, arm(-1), arm(1), cape)
    torso = P('torso', (0, 6, 0), C(-5.5, 0, -3.5, 11, 6, 7, 'metal:waist'), chest)
    tabard = P('tabard', (0, 8, -4.4), C(-3, 8, -4.9, 6, 8, 1, 'cloth:tabard'),
               P('tabard2', (0, 16, -4.4), C(-3, 16, -4.9, 6, 6, 1, 'cloth:tabard2')))
    waist_chain = P('waist_chain', (4.5, 8, -4.3), C(4, 8, -4.8, 1, 4, 1, 'chain:link', tag='wch'),
                    P('waist_chain2', (4.5, 12, -4.3), C(4, 12, -4.8, 1, 4, 1, 'chain:link', share='wch'),
                      P('waist_chain3', (4.5, 16, -4.3), C(4, 16, -4.8, 1, 4, 1, 'chain:link', share='wch'))))
    hips = P('hips', (0, 9, 0),
             C(-6, 6, -3.5, 12, 4, 7, 'metal:faulds'),
             C(-6.5, 5.5, -4, 13, 2, 8, 'leather:belt', tag='belt'),
             C(-1.5, 5.2, -4.6, 3, 3, 1, 'metal:buckle'),
             C(-6.6, 6, -4.4, 13, 1, 1, 'chain:belt_chain'),
             C(-6.5, 9.5, -4.2, 4, 4, 1, 'metal:tasset', tag='tas'), C(2.5, 9.5, -4.2, 4, 4, 1, 'metal:tasset', share='tas'),
             C(-7, 9.5, -2.5, 1, 4, 5, 'metal:tasset_side', tag='tass'), C(6, 9.5, -2.5, 1, 4, 5, 'metal:tasset_side', share='tass'),
             tabard, waist_chain, torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['cape', 'cape_mid', 'cape_low'], rest='down', axes='xz', hang=0.9, freq=1.1, damp=0.3, inertia=10.0,
          falloff=1.15, limits={'x': (-4.0, 100.0), 'z': (-30.0, 30.0)},
          drag={'walk': (14.0, 0.0, 0.0), 'charge': (12.0, 0.0, 0.0), 'roar': (5.0, 0.0, 0.0)},
          per_clip={'death': {'gain': 0.0, 'limits': {'x': (-90.0, 100.0), 'z': (-30.0, 30.0)}}}),
    Chain(['tabard', 'tabard2'], rest='down', axes='xz', hang=0.85, freq=1.6, damp=0.3, inertia=8.0,
          limits={'x': (-80.0, 20.0), 'z': (-25.0, 25.0)}, drag={'walk': (-6.0, 0.0, 0.0)}),
    Chain(['waist_chain', 'waist_chain2', 'waist_chain3'], rest='down', axes='xz', hang=0.95, freq=1.9, damp=0.22,
          inertia=10.0, limits={'x': (-90.0, 60.0), 'z': (-60.0, 60.0)}),
    Chain(['axe_chain', 'axe_chain2'], rest='down', axes='xz', hang=0.95, freq=2.0, damp=0.22, inertia=8.0,
          limits={'x': (-120.0, 120.0), 'z': (-90.0, 90.0)}),
]
GAIT = Gait(1.6, stride=12.0, duty=0.62, lift=3.4, heel=8.0, toe=14.0)
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def idle(x):
    T = 3.6
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.6)
    c.fn('base', 'pos', lambda t: (0.45 * sway(t), 0.35 * b(t) - 0.15, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T + 1.4), 1.2 * sway(t)), 16)
    c.fn('chest', 'rot', lambda t: (-1.8 * b(t), -1.0 * math.sin(2 * math.pi * t / T + 1.4), -0.8 * sway(t)), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.016 * b(t), 1.0 + 0.012 * b(t), 1.0 + 0.024 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (0.7, 0.0, 's'), (1.2, 8.0, 's'), (2.0, 8.0, 's'), (2.5, -6.0, 's'), (3.1, -6.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.2 * b(t - 0.25) + 0.6, eranim.eased_value(look, t), 0.0), 32)
    c.fn('right_arm', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T), 0.0, 1.0 * b(t - 0.15)), 12)
    c.fn('left_arm', 'rot', lambda t: (-0.8 * math.sin(2 * math.pi * t / T), 0.0, -1.8 * b(t - 0.15)), 12)
    regrip = [(0.0, 0.0, 's'), (2.2, 0.0, 's'), (2.45, -5.0, 'o'), (2.7, 2.0, 's'), (3.0, 0.0, 's'), (T, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(regrip, t), 0.0, 0.0), 32)
    flex = [(0.0, 0.0, 's'), (0.8, 0.0, 's'), (1.0, -12.0, 'O'), (1.3, 0.0, 's'), (T, 0.0, 's')]
    c.fn('left_hand', 'rot', lambda t: (eranim.eased_value(flex, t), 0.0, 0.0), 32)
    feet(c, x, samples=16)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.2 * w(t, 1, 0.25), -0.6 - 0.9 * w(t, 2, 0.08), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.0 * w(t, 2, 0.1), -6.0 * w(t), 4.0 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (4.0 + 1.0 * w(t, 2, 0.15), 4.0 * w(t, 1, 0.06), -2.0 * s1(t - 0.06)), 24)
    c.fn('chest', 'rot', lambda t: (1.0 * w(t, 2, 0.2), 3.0 * w(t, 1, 0.1), -2.0 * s1(t - 0.1)), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.0 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.12), 1.2 * s1(t - 0.14)), 24)
    c.fn('right_arm', 'rot', lambda t: (5.0 * w(t, 1, 0.1), 0.0, 1.0 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-16.0 * w(t, 1, 0.1), 0.0, -2.0 * w(t, 2, 0.1)), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, w(t, 1, 0.16)), 0.0, 0.0), 24)
    c.fn('axe', 'rot', lambda t: (-3.0 * w(t, 1, 0.2) - 2.0, 0.0, 1.0 * w(t, 2, 0.2)), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """Overhead chop (the frost nova): both hands on the haft, the axe swung up over the horns and behind, a pause,
    then the blade driven down into the floor ahead (impact) and wrenched free."""
    A, B, Cc, I, F, R, E = 0.18, 0.72, 0.88, 1.1, 1.38, 1.8, 2.2
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.6), (B, 0, 0.8, 1.6), (Cc, 0, 1.0, 1.7, 'o'), (I, 0, -4.2, -2.6, 'I'),
                   (F, 0, -4.6, -2.8, 'O'), (R, 0, -2.6, -1.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -4, 0, 0), (I, 10, 0, 0, 'I'), (F, 12, 0, 0, 'O'), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -20, 0, 0), (Cc, -22, 0, 0, 'o'), (I, 26, 0, 0, 'I'),
                    (F, 28, 0, 0, 'O'), (R, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 0, 0), (I, -18, 0, 0, 'I'), (F, -20, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 12, 0, 4), (B, -165, 0, -16), (Cc, -170, 0, -18, 'o'), (I, -70, 0, -10, 'I'),
                        (F, -66, 0, -10, 'O'), (R, -44, 0, -6), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -26, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (A, 20, 0, 0), (B, 150, 0, 6), (Cc, 156, 0, 6, 'o'), (I, -20, 0, 0, 'I'),
                  (F, -24, 0, 0, 'O'), (R, -10, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 10, 0, -4), (B, -165, 0, 22), (Cc, -170, 0, 24, 'o'), (I, -72, 0, 22, 'I'),
                       (F, -68, 0, 22, 'O'), (R, -40, 0, 10), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -6, 0, 0, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.25, 0.65, (-5.0, 1.0), 3.0), (1.85, 2.15, (0.0, 0.0), 2.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'axe', 'x', 'axe', EDGE_MID, 24.6, -90.0, 60.0)
    return c


def melee_sweep(x):
    """The execution: the axe hauled back far to the right, then a great flat swing across the front at chest height
    (impact as it passes the centre), carried round to the left with the whole body."""
    A, B, I, F, R, E = 0.2, 0.7, 1.0, 1.28, 1.6, 2.0
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.4), (B, -1.2, -1.8, 1.2), (I, 1.0, -2.2, -1.8, 'I'),
                   (F, 1.6, -2.0, -2.2, 'O'), (R, 0.8, -1.2, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 22, 0), (I, 0, -12, 0, 'I'), (F, 0, -26, 0, 'b'), (R, 0, -12, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 4, 30, -5), (I, 8, -16, 4, 'I'), (F, 6, -34, 5, 'b'), (R, 2, -16, 2),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -36, 0), (I, 0, 16, 0, 'I'), (F, -2, 36, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -60, 70, 40), (I, -82, -6, 8, 'I'), (F, -76, -70, 4, 'O'),
                        (R, -40, -40, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (B, 40, 0, -10), (I, 50, 0, -20, 'I'), (F, 44, 0, -24, 'O'), (R, 20, 0, -10),
                  (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -70, 0, 30), (I, -80, 0, -10, 'I'), (F, -60, 0, -50, 'O'), (R, -20, 0, -20),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.72, 0.98, (-6.0, 2.0), 3.0), (1.55, 1.9, (0.0, 0.0), 2.5)],
         right_plan=[(0.15, 0.55, (3.0, 1.0), 2.0), (1.6, 1.92, (0.0, 0.0), 2.0)])
    return c


def cast(x):
    """A lash of the waist chain: the left hand gathers it and whips it overhead and forward (impact), the axe held
    back in the right."""
    A, B, I, F, R, E = 0.2, 0.62, 0.95, 1.2, 1.55, 2.0
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.4), (B, 0, -1.0, 1.2), (I, 0, -1.8, -1.8, 'I'), (F, 0, -1.9, -2.0),
                   (R, 0, -1.0, -1.0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -8, -24, 0), (I, 12, 18, 0, 'I'), (F, 14, 22, 0, 'b'), (R, 6, 8, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -6, 18, 0), (I, -4, -14, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 20, 0, -10), (B, -150, 0, -30), (I, -80, 0, 6, 'I'), (F, -60, 0, 10, 'O'),
                       (R, -30, 0, 4), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -60, 0, 0), (I, -6, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 20, 0, 10), (I, 26, 0, 12), (R, 10, 0, 4), (E, 0, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (B, 14, 0, 0), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('waist_chain', [(0, 0, 0, 0), (B, -60, 0, -30), (I, -110, 0, 10, 'I'), (F, -60, 0, 6), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.7, 0.92, (-4.0, 0.5), 2.5), (1.55, 1.85, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'left_hand', PALM_L
    return c


def roar(x):
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.0 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.2 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-18.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-30.0 + 2.0 * tr(t), 0.0, 30.0 + 2.0 * tr2(t)), 14)
    c.fn('axe', 'rot', lambda t: (20.0, 0.0, -10.0 + 2.0 * tr(t)), 8)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 2.0 * tr2(t), 0.0, -50.0 - 3.0 * tr(t)), 14)
    c.fn('left_hand', 'rot', lambda t: (-20.0 + 4.0 * tr(t), 0.0, 0.0), 14)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=12)
    return c


def charge(x):
    """The chain pull: he crouches with the axe drawn back, then lunges and drives it ahead at the end of his reach
    (impact), dragging himself after it."""
    A, B, I, F, R, E = 0.15, 0.55, 0.85, 1.12, 1.42, 1.8
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.6), (B, 0, -3.0, 2.6), (I, 0, -2.8, -9.0, 'I'), (F, 0, -3.0, -9.6, 'O'),
                   (R, 0, -1.8, -6.0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 8, 18, 0), (I, 12, -10, 0, 'I'), (F, 12, -12, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 18, 18, 0), (I, 24, -14, 0, 'I'), (F, 26, -16, 0, 'O'), (R, 12, -6, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -20, -20, 0), (I, -26, 14, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 50, 0, 14), (I, -90, 0, 4, 'I'), (F, -94, 0, 4, 'O'), (R, -56, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (B, 30, 0, 0), (I, 56, 0, 0, 'I'), (F, 58, 0, 0), (R, 30, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -60, 0, -20), (I, 40, 0, -24, 'I'), (F, 46, 0, -26, 'O'), (R, 20, 0, -10),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.6, 0.84, (-11.0, 1.0), 3.5), (1.4, 1.72, (0.0, 0.0), 2.5)],
         right_plan=[(0.1, 0.42, (3.0, 1.0), 1.5), (0.7, 0.9, (-2.0, 1.0), 1.0), (1.42, 1.74, (0.0, 0.0), 1.5)])
    return c


def summon(x):
    """The haft stamped on the floor, the left hand raised to the cells as the jailers rise (impact)."""
    A, B, Cc, I, F, R, E = 0.2, 0.7, 0.9, 1.1, 1.4, 1.8, 2.2
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 1.0, 0.4), (Cc, 0, 1.2, 0.5, 'o'), (I, 0, -2.4, -0.8, 'I'), (F, 0, -2.6, -0.9),
                   (R, 0, -1.4, -0.4), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -14, 0, 0), (I, 10, 0, 0, 'I'), (F, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -20, 0, 0), (I, -14, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -20, 0, 4), (B, -60, 0, 6), (Cc, -64, 0, 6), (I, -30, 0, 4, 'I'),
                        (F, -28, 0, 4), (R, -14, 0, 2), (E, 0, 0, 0)])
    c.rot('axe', [(0, 0, 0, 0), (A, 30, 0, 0), (B, 60, 0, 0), (I, 56, 0, 0, 'I'), (F, 56, 0, 0), (R, 30, 0, 0),
                  (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -40, 0, -10), (I, -170, 0, -10, 'I'), (F, -168, 0, -10), (R, -80, 0, -6),
                       (E, 0, 0, 0)])
    c.rot('left_hand', [(0, 0, 0, 0), (I, -20, 0, 0), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.8, 1.06, (3.0, 1.0), 2.0), (1.85, 2.15, (0.0, 0.0), 2.0)])
    eranim.rest_on_floor(c, x.rig, x.v, (I, F), 'axe', axis='x', clearance=-0.6)
    return c


def phase_change(x):
    """Cold fury: the axe taken in both hands and raised high over the horns as the ice bursts around him (impact),
    trembling, then lowered."""
    E, I = 3.0, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.5, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -0.6, 1.2, 'O'), (0.95, 0, -4.0, 0.6), (1.35, 0, -4.2, 0.5),
                   (1.78, 0, 1.2, -0.4, 'O'), (2.5, 0, 0.8, -0.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.3, -14, 0, 0, 'O'), (0.95, 26, 0, 0), (1.35, 30, 0, 0), (1.78, -22, 0, 0, 'O'),
                    (2.5, -18, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.3, -16, 0, 0, 'O'), (0.95, 20, 0, 0), (1.78, -30, 0, 0, 'O'), (2.5, -26, 0, 0),
                   (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (0.3, -20, 0, 14 * sz, 'O'), (0.95, -50, 0, -10 * sz),
                                (1.35, -52, 0, -12 * sz), (1.78, -172, 0, -8 * sz, 'O'), (2.5, -168, 0, -6 * sz),
                                (E, 0, 0, 0)])
        c.add('%s_arm' % side, 'rot', lambda t, sz=sz: (1.5 * tr(t + 0.1 * sz) * hold(t), 0.0, 0.0), 48)
    c.rot('axe', [(0, 0, 0, 0), (0.95, 40, 0, 0), (1.78, 120, 0, 0, 'O'), (2.5, 116, 0, 0), (E, 0, 0, 0)])
    feet(c, x, right_plan=[(0.3, 0.85, (6.0, 1.0), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.55, 2.85, (0.0, 0.0), 1.5)],
         left_plan=[(0.25, 0.6, (-2.0, 1.0), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.55, 2.85, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.3
    c = Clip('stagger', E, impact=0.1)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.1, 0, -0.8, 2.6, 'O'), (0.4, 0, -1.2, 3.2), (0.75, 0, -0.5, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.1, -18, 6, 8, 'O'), (0.4, -8, -4, 3), (0.7, 6, 2, -2), (0.95, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.1, -22, 12, 6, 'O'), (0.4, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.1, -20, 0, 30, 'O'), (0.4, -10, 0, 12), (0.8, -2, 0, 2), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.1, -36, 0, -46, 'O'), (0.4, -16, 0, -24), (0.8, -4, 0, -6), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.12, 0.6, 0.12)), 24)
    feet(c, x, right_plan=[(0.06, 0.34, (5.0, 1.0), 2.5), (0.75, 1.05, (0.0, 0.0), 2.0)],
         left_plan=[(0.3, 0.52, (2.5, 0.0), 1.5), (0.9, 1.18, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """The cold leaves him: he plants the axe, sinks to his knees leaning on the haft, then the haft slides away and
    he falls forward on his face (impact)."""
    E, I = 3.6, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, -0.6, 1.2, 'O'), (1.1, 0, -7.4, 0.6, 'I'), (1.2, 0, -7.0, 0.6, 'O'),
                   (E, 0, -7.0, 0.6)])
    c.rot('hips', [(0, 0, 0, 0), (0.45, -4, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 8, 0, 0), (I, 28, 0, 0, 'I'),
                   (1.95, 24, 0, 0, 'b'), (E, 26, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.45, -14, 0, 4, 'O'), (1.1, 8, 0, -4), (1.35, 12, 0, -2), (I, 46, 0, 0, 'I'),
                    (1.95, 42, 0, 0, 'b'), (E, 44, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * envelope(t, 0.3, 1.0, 0.2)), 36)
    c.rot('chest', [(0, 0, 0, 0), (1.1, 6, 0, 0), (I, 16, 0, 0, 'I'), (E, 16, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.45, -22, 0, 0, 'O'), (1.1, 22, 0, 0), (I, -6, -30, 0, 'I'), (E, -6, -32, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -30, 0, 10, 'O'), (1.1, -40, 0, 10), (1.35, -44, 0, 12),
                        (I, -110, 0, 30, 'I'), (1.95, -104, 0, 34, 'b'), (E, -106, 0, 32)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, -20, 0, -36, 'O'), (1.1, 6, 0, -12), (I, -70, 0, -50, 'I'),
                       (1.95, -64, 0, -46, 'b'), (E, -66, 0, -48)])
    c.rot('axe', [(0, 0, 0, 0), (0.45, 30, 0, 0), (1.1, 50, 0, 0), (1.35, 52, 0, 0), (1.6, 20, 0, 60, 'I'),
                  (I, 10, 0, 88, 'I'), (1.95, 12, 0, 84, 'b'), (E, 12, 0, 86)])
    c.rot('cape', [(0, 0, 0, 0), (0.45, 14, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 6, 0, 0), (I, -2, 0, 0, 'I'),
                   (2.0, 2, 0, 0, 'b'), (E, 0, 0, 0)])
    c.rot('cape_mid', [(0, 0, 0, 0), (0.45, 10, 0, 0), (1.1, 6, 0, 0), (I, -44, 0, 0, 'I'), (2.05, -38, 0, 0, 'b'),
                       (E, -40, 0, 0)])
    c.rot('cape_low', [(0, 0, 0, 0), (0.45, 8, 0, 0), (1.1, 4, 0, 0), (I, -42, 0, 6, 'I'), (2.1, -34, 0, 4, 'b'),
                       (E, -36, 0, 4)])
    feet(c, x, right_plan=[(0.5, 1.1, (6.4, 0.4), 1.5)], left_plan=[(0.55, 1.12, (6.2, 0.4), 1.5)],
         right_holds=[(1.1, E, 64.0)], left_holds=[(1.12, E, 64.0)])
    for t in (1.1, 1.2, 1.35, I, 1.95, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE + ('axe',))
    eranim.rest_on_floor(c, x.rig, x.v, (I, 1.95, E), 'axe', axis='x')
    eranim.rest_on_floor(c, x.rig, x.v, (I, 2.1, E), 'cape_low', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

IRON = rgb('#4b4f57')
IRON_DARK = rgb('#25282e')
SNOW = rgb('#eef6fb')
ICE = rgb('#9fd8f6')
ICE_GLOW = (110, 200, 255)
CRIMSON = rgb('#7c1d22')


def paint_helm(p, eye):
    v = p.face('head', 'front', tag='visor')
    g = p.face('head', 'front', tag='visor', glow=True)
    for y in range(v.fh):
        for x in range(v.fw):
            if x % 3 == 1 and y >= 5:
                v.fpx(x, y, IRON_DARK)                       # the bars of the visor
    for x in range(1, v.fw - 1):
        v.fpx(x, 3, (14, 16, 20))
        v.fpx(x, 4, (14, 16, 20))
    for (x0, x1) in ((3, 6), (10, 13)):
        for x in range(x0, x1 + 1):
            v.fpx(x, 3, eye)
            g.fpx(x, 3, lighten(eye, 0.4))
            g.fpx(x, 4, shade(eye, 0.8))
        g.fpx((x0 + x1) // 2, 3, (255, 255, 255))
    trim(v, lighten(IRON, 0.3), rows=(0,), cols=(0, -1))
    for side in ('front', 'right', 'back', 'left'):
        hf = p.face('head', side, tag='helm')
        rivets(hf, IRON, 4, inset=1, rows=(1, hf.fh - 2))
        frost(hf, ('helm', side), 3, SNOW, ICE)


def paint_armour(p):
    for part, tag in (('chest', 'cuirass'), ('right_arm', 'paul'), ('right_arm', 'paul2'), ('right_forearm', 'gaunt'),
                      ('right_leg', 'cuisse'), ('right_shin', 'greave'), ('right_foot', 'sab'), ('hips', None),
                      ('torso', None), ('right_shin', 'knee')):
        for side in ('front', 'right', 'back', 'left'):
            fc = p.face(part, side, tag=tag)
            frost(fc, (part, tag, side), 2, SNOW, ICE)
            for x in range(fc.fw):
                if h(part, side, x) < 0.12:
                    for y in range(fc.fh):
                        if h(part, side, x, y) < 0.08:
                            fc.fpx(x, y, lighten(fc.fget(x, y), 0.35))
    cf = p.face('chest', 'front', tag='cuirass')
    for y in range(4, cf.fh - 2):
        cf.fpx(cf.fw // 2, y, shade(IRON, 0.6))
        cf.fpx(cf.fw // 2 + 1, y, lighten(IRON, 0.2))
    for y in range(6, cf.fh - 2, 5):
        for x in range(3, cf.fw - 3):
            cf.fpx(x, y, shade(cf.fget(x, y), 0.7))
    for side in ('front', 'back', 'right', 'left'):
        bf = p.face('hips', side, tag='belt')
        rivets(bf, rgb('#8a8e96'), 4, inset=1, rows=(1,))


def paint_cloth(p, tear=1.0):
    for part, top, bottom in (('cape', CRIMSON, shade(CRIMSON, 0.85)), ('cape_mid', shade(CRIMSON, 0.85),
                                                                         shade(CRIMSON, 0.72)),
                              ('cape_low', shade(CRIMSON, 0.72), shade(CRIMSON, 0.6))):
        for side in ('back', 'right', 'left'):
            ramp(p.face(part, side), top, bottom, keep=0.6)
        ramp(p.face(part, 'front'), shade(top, 0.5), shade(bottom, 0.5), keep=0.5)
        frost(p.face(part, 'back'), (part, 'frost'), 1, SNOW, ICE)
    for side in ('back', 'front', 'right', 'left'):
        ragged(p.face('cape_low', side), int(8 * tear), ('cape', side), min_depth=1)
        ragged(p.face('cape_mid', side), int(3 * tear), ('capem', side))
        ragged(p.face('tabard2', side), int(6 * tear), ('tab', side), min_depth=1)
    bot = p.face('cape_low', 'bottom')
    for y in range(bot.fh):
        for x in range(bot.fw):
            bot.fclear(x, y)
    for side in ('back', 'front'):
        f = p.face('cape_mid', side)
        for (cx, cy) in ((7, 6), (22, 11)):
            for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2)):
                f.fclear(cx + dx, cy + dy)


def paint_axe(p, crack):
    for side in ('right', 'left'):
        b = p.face('axe', side, tag='blade')
        gb = p.face('axe', side, tag='blade', glow=True)
        for y in range(b.fh):
            for x in range(b.fw):
                d = x / max(1, b.fw - 1)
                b.fpx(x, y, mix(shade(IRON, 0.7), IRON_DARK, 0.5 * d) if (x + y) % 7 else shade(IRON, 0.6))
        crackle(b, ('axe', side), 4, 10, crack, gb, lighten(crack, 0.3), branch=0.6)
        for y in range(b.fh):
            b.fpx(0, y, lighten(ICE, 0.2))
            b.fpx(1, y, ICE)
        e = p.face('axe', side, tag='edge')
        for y in range(e.fh):
            for x in range(e.fw):
                e.fpx(x, y, mix(ICE, SNOW, 0.5 if (x + y) % 3 == 0 else 0.0))
    for side in ('front', 'back', 'right', 'left'):
        hf = p.face('axe', side, index=0)
        for y in range(0, hf.fh, 9):
            for x in range(hf.fw):
                hf.fpx(x, y, shade(hf.fget(x, y), 0.6))


def variants():
    iron = {'base': '#4b4f57', 'style': 'metal'}
    dark = {'base': '#3a3d44', 'style': 'metal'}
    ice = {'base': '#a6dcf8', 'style': 'ice'}
    snow = {'base': '#eef6fb', 'style': 'ice'}
    pal = {
        'helm': iron, 'visor': dark, 'crest': dark, 'cuirass': iron, 'gorget': dark, 'pauldron': iron,
        'pauldron2': dark, 'arm': dark, 'gauntlet': iron, 'fist': dark, 'faulds': dark, 'waist': dark, 'cuisse': iron,
        'greave': iron, 'knee': iron, 'sabaton': dark, 'tasset': iron, 'tasset_side': dark, 'buckle': {'base': '#8a8e96', 'style': 'metal'},
        'belt': {'base': '#3a2a22', 'style': 'leather'}, 'wrap': {'base': '#3a2a22', 'style': 'leather'},
        'horn': {'base': '#e6dccb', 'style': 'bone'}, 'horn_mid': {'base': '#ece4d4', 'style': 'bone'},
        'horn_tip': {'base': '#f2ece0', 'style': 'bone'},
        'snow': snow, 'helm_snow': snow, 'chest_snow': snow, 'cuff_snow': snow, 'boot_snow': snow, 'knee_snow': snow,
        'blade_snow': snow,
        'icicle': ice, 'helm_icicle': ice, 'axe_icicle': ice, 'edge': ice, 'horn_ice': ice,
        'ice_horn': {'base': '#bfe8ff', 'style': 'ice'}, 'crown_ice': ice, 'crown_ice2': {'base': '#cdeeff', 'style': 'ice'},
        'crown_ice3': ice,
        'link': {'base': '#7c818a', 'style': 'chain'}, 'clink': {'base': '#7c818a', 'style': 'metal', 'rivets': False},
        'clink2': {'base': '#6a6f78', 'style': 'metal', 'rivets': False},
        'belt_chain': {'base': '#7c818a', 'style': 'chain'},
        'cape': {'base': '#7c1d22', 'style': 'cloth'}, 'cape2': {'base': '#6e1a1e', 'style': 'cloth'},
        'cape3': {'base': '#5e161a', 'style': 'cloth'}, 'tabard': {'base': '#7a1e22', 'style': 'cloth'},
        'tabard2': {'base': '#6a1a1e', 'style': 'cloth'},
        'haft': {'base': '#3c3026', 'style': 'wood'}, 'butt': dark, 'axe_blade': {'base': '#3e424a', 'style': 'metal', 'rivets': False},
        'axe_horn': {'base': '#3e424a', 'style': 'metal', 'rivets': False}, 'socket': dark,
        'spike': {'base': '#5a5e66', 'style': 'metal', 'rivets': False},
    }

    def deco(p, form=0):
        eye = ICE_GLOW if form < 2 else (170, 230, 255)
        paint_helm(p, eye)
        paint_armour(p)
        paint_cloth(p, tear=1.0 + 0.25 * form)
        paint_axe(p, ICE_GLOW)
        if form >= 1:
            for part, tag in (('chest', 'cuirass'), ('right_arm', 'paul'), ('right_leg', 'cuisse'),
                              ('right_forearm', 'gaunt')):
                for side in ('front', 'right', 'back', 'left'):
                    crackle(p.face(part, side, tag=tag), (part, side, form), form, 6 + 2 * form, ICE_GLOW,
                            p.face(part, side, tag=tag, glow=True), lighten(ICE_GLOW, 0.3))
            for s in ('r',):
                for side in ('front', 'right', 'back', 'left', 'top'):
                    fc = p.face('ice_horn_' + s, side)
                    gfc = p.face('ice_horn_' + s, side, glow=True)
                    for y in range(0, fc.fh, 2):
                        gfc.fpx(fc.fw // 2, y, ICE_GLOW)
        if form >= 2:
            for i in range(5):
                for side in ('front', 'right', 'back', 'left'):
                    fc = p.face('icicle_crown', side, index=i)
                    gfc = p.face('icicle_crown', side, index=i, glow=True)
                    for y in range(fc.fh):
                        gfc.fpx(0, y, ICE_GLOW)
            for i in range(11):
                gfc = p.face('chest_chain', 'front', index=i, glow=True)
                for y in range(gfc.fh):
                    for x in range(gfc.fw):
                        if (x + y) % 2 == 0:
                            gfc.fpx(x, y, shade(ICE_GLOW, 0.7))
    return [Variant('b4', palette=pal, paint=deco, glow=True, extra=dict(scale=2.6, hitbox=(3.0, 7.6))),
            Variant('b4_p1', show={'ice_horn_r', 'ice_horn_l'}, palette=pal, paint=lambda p: deco(p, 1), glow=True,
                    extra=dict(scale=2.6)),
            Variant('b4_p2', show={'ice_horn_r', 'ice_horn_l', 'icicle_crown'}, palette=pal,
                    poses={'ice_horn_r': dict(scale=(1.2, 1.3, 1.2)), 'ice_horn_l': dict(scale=(1.2, 1.3, 1.2))},
                    paint=lambda p: deco(p, 2), glow=True, extra=dict(scale=2.6))]


TRAILS = [('axe', EDGE_TOP, EDGE_LOW)]


def rig():
    vs = variants()
    base = Rig('executioner', 'ExecutionerBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('executioner', 'ExecutionerBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 3.0
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
