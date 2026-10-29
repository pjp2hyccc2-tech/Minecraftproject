"""Buzzling (バズリング) - giant hovering wasp-beetle with a poison stinger. Small flying monster.

Rest pose = wings folded on the back (grounded); the 'fly' loop spreads them and beats them very fast."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, accent_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402

M('bz_chitin', ['#0a0c12', '#141824', '#1e2638', '#2c3850', '#3e4e6c', '#566a8c'], 'plates', scale=0.8, contrast=0.6, noise=0.03,
  edge=0.1)
M('bz_yellow', ['#3a2a04', '#6a4c08', '#9c7410', '#cc9e1c', '#ecc232', '#ffe068'], 'plates', scale=0.7, contrast=0.5, noise=0.03,
  edge=0.08)
M('bz_black', ['#050505', '#0c0c0e', '#151518', '#1f1f24', '#2a2a30', '#36363e'], 'plates', scale=0.7, contrast=0.5, edge=0.0)
M('bz_wing', ['#3c5466', '#5a7a90', '#7ea2ba', '#a2c4d8', '#c8e2f0', '#eef8ff'], 'membrane', contrast=0.6, noise=0.03, edge=0.0,
  base=0.55)
M('bz_eye', ['#3a0606', '#6e0e0a', '#a01c10', '#d0381c', '#f06a34', '#ff9a5a'], 'crystal', glow='#ff5a28', edge=0.0)
M('bz_sting', ['#1a0e04', '#2e1a08', '#4a2c10', '#6c4418', '#946022', '#be8030'], 'horn', edge=0.0)
M('bz_leg', ['#0a0c12', '#141824', '#1e2638', '#2c3850', '#3e4e6c', '#566a8c'], 'smooth', noise=0.03, edge=0.0)

m = Model('buzzling')
m.config.update({'head_bones': [['head', 0.6]], 'walk_speed': 4.0, 'run_speed': 0.6, 'walk_scale': 3.0})

# ------------------------------------------------------------------ thorax (root)
body = m.bone('body', (0, 15, 0), (0, 0, 0))
thorax = body.cube((-3, -3, -3), (6, 6, 7), 'bz_chitin')
body.cube((-2, -4, -2), (4, 1, 5), 'bz_yellow')                                         # yellow collar plate

# ------------------------------------------------------------------ head
head = body.bone('head', (0, -0.5, -3), (8, 0, 0))
skull = head.cube((-2.5, -2.5, -4), (5, 5, 4), 'bz_chitin')
eye = head.cube((-3.5, -2.5, -4), (2, 3, 3), 'bz_eye')
head.cube((1.5, -2.5, -4), (2, 3, 3), 'bz_eye', mirror_of=eye)
head.cube((-1.5, 0.5, -4.5), (3, 2, 1), 'bz_yellow')                                    # face plate
mand = head.bone('mandible_l', (1, 2, -4), (0, 20, 0))
mand.cube((-0.5, -0.5, -3), (1, 1, 3), 'bz_sting')
mirror_tree(head, mand)
ant = head.bone('antenna_l', (1, -2.5, -3.5), (-40, -20, 20))
ant.cube((-0.5, -5, -0.5), (1, 5, 1), 'bz_leg', inflate=-0.2)
ant2 = ant.bone('antenna_l_tip', (0, -4.5, 0), (-50, 0, 0))
ant2.cube((-0.5, -3, -0.5), (1, 3, 1), 'bz_leg', inflate=-0.25)
mirror_tree(head, ant)

# ------------------------------------------------------------------ abdomen + stinger
abd = body.bone('abdomen', (0, 0.5, 4), (-22, 0, 0))
abd.cube((-1, -1, -1), (2, 2, 3), 'bz_black')                                           # waist
belly = abd.cube((-3.5, -3.5, 1.5), (7, 7, 9), 'bz_yellow')
abd2 = abd.bone('abdomen_tip', (0, 0, 10), (-12, 0, 0))
tipc = abd2.cube((-2.5, -2.5, 0), (5, 5, 3), 'bz_yellow')
sting = abd2.bone('stinger', (0, 0, 2.5), (0, 0, 0))
sting.cube((-1, -1, 0), (2, 2, 2), 'bz_black')
sting.cube((-0.5, -0.5, 1.5), (1, 1, 3), 'bz_sting', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.5, 'mat': 'claw'}])


def bands(P, L, N, f, d, r, face):
    z = L[..., 2]
    return (np.floor(z) % 3) == 1


accent_pass(m, [belly], 'bz_black', bands)
accent_pass(m, [tipc], 'bz_black', lambda P, L, N, f, d, r, face: (np.floor(L[..., 2]) % 3) == 2)

# ------------------------------------------------------------------ wings (rest = folded back over the abdomen)
FOLD = {'fw': (0, -75, -8), 'bw': (0, -68, -4)}
wf = body.bone('wing_fl', (2.5, -3, -1.5), FOLD['fw'])
wf.cube((0, 0, -2), (12, 0, 5), 'bz_wing', decals=[{'type': 'cutout', 'shape': 'round'}])
wb = body.bone('wing_bl', (2.5, -3, 1), FOLD['bw'])
wb.cube((0, 0, -1.5), (9, 0, 4), 'bz_wing', decals=[{'type': 'cutout', 'shape': 'round'}])
mirror_tree(body, wf, '_fl', '_fr')
mirror_tree(body, wb, '_bl', '_br')

# ------------------------------------------------------------------ legs (3 pairs, dangling)
LEGS = []
for i, (z, rx) in enumerate(((-2, 10), (0.5, 25), (3, 40))):
    up = body.bone('leg%d_l' % i, (3, 2, z), (rx, 0, -55))
    up.cube((-0.5, -0.5, -0.5), (1, 4, 1), 'bz_leg')
    lo = up.bone('leg%d_l_shin' % i, (0, 3.5, 0), (0, 0, 70))
    lo.cube((-0.5, -0.5, -0.5), (1, 4, 1), 'bz_leg', inflate=-0.15)
    mirror_tree(body, up)
    LEGS.append(i)

fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim
WINGS = [('wing_fl', 'fw'), ('wing_bl', 'bw'), ('wing_fr', 'fw'), ('wing_br', 'bw')]


def spread(side_name, kind, flap_deg, t, period, phase=0.0, extra=(0, 0, 0)):
    """Offset from the folded rest pose to a spread, beating wing."""
    g = 1 if side_name.endswith('l') else -1
    fy = -FOLD[kind][1] - (12 if kind == 'fw' else 30)
    flap = flap_deg * math.sin(2 * math.pi * t / period + phase)
    return (extra[0], g * (fy + extra[1]), g * (FOLD[kind][2] * -1 - 10 + flap + extra[2]))


def wings_beat(a, length, period=0.1, flap=38, steps=None):
    steps = steps or int(round(length / period * 4))
    for name, kind in WINGS:
        ph = 0.0 if kind == 'fw' else 1.2
        A.sample(a, name, 'rotation', lambda t, name=name, kind=kind, ph=ph: spread(name, kind, flap, t, period, ph), steps)


def legs_pose(a, rx, rz_lo, sway=0.0, period=1.0):
    for i in LEGS:
        for side, g in (('l', 1), ('r', -1)):
            A.sample(a, 'leg%d_%s' % (i, side), 'rotation', lambda t, i=i, g=g: (rx + sway * cyc(t, period, i), 0, 0), 8)
            A.hold(a, 'leg%d_%s_shin' % (i, side), 'rotation', (0, 0, g * rz_lo))


# fly: fast wing beats, hover bob, dangling legs
a = T('fly', 1.2, True)
wings_beat(a, 1.2)
A.sine(a, 'body', 'position', (0, 1.2, 0), period=1.2)
A.sine(a, 'body', 'rotation', (3, 0, 2), period=1.2, phase=0.8)
A.sine(a, 'abdomen', 'rotation', (6, 3, 0), period=0.6)
A.sine(a, 'abdomen_tip', 'rotation', (5, 0, 0), period=0.6, phase=0.8)
legs_pose(a, 22, 10, sway=6, period=1.2)
A.sine(a, 'antenna_l', 'rotation', (8, 0, 0), period=0.4)
A.sine(a, 'antenna_r', 'rotation', (8, 0, 0), period=0.4, phase=1.0)
A.sine(a, 'mandible_l', 'rotation', (0, 10, 0), period=0.6)
A.sine(a, 'mandible_r', 'rotation', (0, -10, 0), period=0.6)

# idle (grounded): wings folded, twitching antennae, pulsing abdomen
a = T('idle', 2.0, True)
A.sine(a, 'abdomen', 'rotation', (3, 0, 0), period=1.0)
A.sine(a, 'abdomen', 'scale', (0.04, 0.04, 0.0), period=1.0, offset=(1, 1, 1))
A.sine(a, 'antenna_l', 'rotation', (10, 6, 0), period=1.0)
A.sine(a, 'antenna_r', 'rotation', (10, -6, 0), period=1.0, phase=1.4)
A.sine(a, 'mandible_l', 'rotation', (0, 8, 0), period=0.5)
A.sine(a, 'mandible_r', 'rotation', (0, -8, 0), period=0.5)
keys(a, 'wing_fl', 'rotation', [(0, (0, 0, 0)), (1.4, (0, 0, 0)), (1.45, (0, 0, -10)), (1.5, (0, 0, 0)), (1.55, (0, 0, -10)), (1.6, (0, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'wing_fr', 'rotation', [(0, (0, 0, 0)), (1.4, (0, 0, 0)), (1.45, (0, 0, 10)), (1.5, (0, 0, 0)), (1.55, (0, 0, 10)), (1.6, (0, 0, 0)), (2.0, (0, 0, 0))])

# walk / run (grounded crawl): alternating tripods
for name, period, amp in (('walk', 0.6, 22), ('run', 0.4, 30)):
    a = T(name, period, True)
    for i in LEGS:
        for side, g in (('l', 1), ('r', -1)):
            ph = (0 if (i % 2 == 0) == (side == 'l') else math.pi)
            A.sample(a, 'leg%d_%s' % (i, side), 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph), 0, 0), 8)
            A.sample(a, 'leg%d_%s' % (i, side), 'position', lambda t, ph=ph: (0, -1.0 * max(0.0, cyc(t, period, ph + 1.57)), 0), 8)
    A.sine(a, 'body', 'rotation', (0, 3, 2), period=period)
    A.sine(a, 'abdomen', 'rotation', (0, 6, 0), period=period, phase=1.0)

# stinger_shot (30 ticks): curl the abdomen forward under the body, fire at tick 14
a = T('stinger_shot', 1.5)
keys(a, 'abdomen', 'rotation', [(0, (0, 0, 0)), (0.55, (-62, 0, 0)), (0.68, (-70, 0, 0)), (0.75, (-58, 0, 0)), (1.0, (-50, 0, 0)), (1.5, (0, 0, 0))])
keys(a, 'abdomen_tip', 'rotation', [(0, (0, 0, 0)), (0.55, (-30, 0, 0)), (0.7, (-36, 0, 0)), (1.0, (-20, 0, 0)), (1.5, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.55, (-14, 0, 0)), (0.7, (-6, 0, 0)), (0.75, (-16, 0, 0)), (1.5, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.55, (0, -1, 1)), (0.72, (0, -1.5, 2)), (1.5, (0, 0, 0))])
keys(a, 'stinger', 'scale', [(0, (1, 1, 1)), (0.6, (1, 1, 1.3)), (0.7, (1, 1, 0.8)), (1.0, (1, 1, 1))])
for i in LEGS:
    keys(a, 'leg%d_l' % i, 'rotation', [(0, (0, 0, 0)), (0.6, (-25, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'leg%d_r' % i, 'rotation', [(0, (0, 0, 0)), (0.6, (-25, 0, 0)), (1.5, (0, 0, 0))])

# dive_sting (40 ticks): rise and coil (0-0.5s), dive stinger-first (0.55-1.3s), pull up
a = T('dive_sting', 2.0)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.45, (-22, 0, 0)), (0.6, (34, 0, 0)), (1.25, (30, 0, 0)), (1.5, (-10, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'abdomen', 'rotation', [(0, (0, 0, 0)), (0.45, (-40, 0, 0)), (0.6, (-84, 0, 0)), (1.25, (-80, 0, 0)), (1.5, (-20, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'abdomen_tip', 'rotation', [(0, (0, 0, 0)), (0.6, (-34, 0, 0)), (1.25, (-30, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.45, (16, 0, 0)), (0.6, (-24, 0, 0)), (1.3, (-20, 0, 0)), (2.0, (0, 0, 0))])
for i in LEGS:
    for side in ('l', 'r'):
        keys(a, 'leg%d_%s' % (i, side), 'rotation', [(0, (0, 0, 0)), (0.5, (-40, 0, 0)), (0.65, (30, 0, 0)), (1.3, (30, 0, 0)), (2.0, (0, 0, 0))])
for name, kind in WINGS:
    g = 1 if name.endswith('l') else -1
    keys(a, name, 'rotation', [(0, (0, 0, 0)), (0.6, (0, g * -20, g * -20)), (1.25, (0, g * -20, g * -20)), (1.5, (0, 0, 0)), (2.0, (0, 0, 0))])

a = T('flinch', 0.6)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.08, (-16, 10, 18)), (0.3, (6, -4, -8)), (0.6, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.08, (0, -2, 2)), (0.6, (0, 0, 0))])
keys(a, 'abdomen', 'rotation', [(0, (0, 0, 0)), (0.1, (20, 0, 0)), (0.4, (-10, 0, 0)), (0.6, (0, 0, 0))])

# death: flips onto its back, legs curl up
a = T('death', 1.5)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.2, (-20, 0, 30)), (0.7, (0, 0, 180)), (0.85, (0, 0, 170)), (1.0, (0, 0, 180)), (1.5, (0, 0, 180))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.2, (0, -2, 0)), (0.7, (0, 1.5, 0)), (1.5, (0, 1.5, 0))])
for i in LEGS:
    for side, g in (('l', 1), ('r', -1)):
        keys(a, 'leg%d_%s' % (i, side), 'rotation', [(0, (0, 0, 0)), (0.7, (-10, 0, g * 40)), (1.0, (-10, 0, g * 50)), (1.5, (-10, 0, g * 48))])
        keys(a, 'leg%d_%s_shin' % (i, side), 'rotation', [(0, (0, 0, 0)), (1.0, (0, 0, g * 60)), (1.5, (0, 0, g * 60))])
keys(a, 'abdomen', 'rotation', [(0, (0, 0, 0)), (0.7, (20, 0, 0)), (1.5, (16, 0, 0))])
keys(a, 'antenna_l', 'rotation', [(0, (0, 0, 0)), (1.0, (40, 0, 0)), (1.5, (40, 0, 0))])
keys(a, 'antenna_r', 'rotation', [(0, (0, 0, 0)), (1.0, (40, 0, 0)), (1.5, (40, 0, 0))])

# sleep: resting on the ground, legs tucked
a = T('sleep', 3.0, True)
A.hold(a, 'body', 'position', (0, 4.5, 0))
A.sine(a, 'abdomen', 'rotation', (2, 0, 0), period=1.5, offset=(14, 0, 0))
for i in LEGS:
    for side, g in (('l', 1), ('r', -1)):
        A.hold(a, 'leg%d_%s' % (i, side), 'rotation', (0, 0, g * -25))
        A.hold(a, 'leg%d_%s_shin' % (i, side), 'rotation', (0, 0, g * 20))
A.hold(a, 'antenna_l', 'rotation', (50, 0, 0))
A.hold(a, 'antenna_r', 'rotation', (50, 0, 0))
A.hold(a, 'head', 'rotation', (16, 0, 0))

# stun: grounded, twitching legs and wings
a = T('stun', 0.8, True)
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'body', 'rotation', (0, 0, 10), period=0.8)
for i in LEGS:
    for side, g in (('l', 1), ('r', -1)):
        A.sine(a, 'leg%d_%s' % (i, side), 'rotation', (30, 0, 0), period=0.2, phase=i + (0 if side == 'l' else 1.5))
for name, kind in WINGS:
    g = 1 if name.endswith('l') else -1
    A.sine(a, name, 'rotation', (0, 0, g * 14), period=0.15)
A.sine(a, 'antenna_l', 'rotation', (20, 0, 0), period=0.4)
A.sine(a, 'antenna_r', 'rotation', (20, 0, 0), period=0.4, phase=1.5)

# trapped (small monsters cannot be trapped by the core; kept so every required state pose exists): pinned, buzzing
a = T('trapped', 0.6, True)
A.hold(a, 'body', 'position', (0, 2, 0))
A.sine(a, 'body', 'rotation', (3, 0, 6), period=0.3)
wings_beat(a, 0.6, period=0.1, flap=30)
for i in LEGS:
    for side, g in (('l', 1), ('r', -1)):
        A.sample(a, 'leg%d_%s' % (i, side), 'rotation', lambda t, i=i, g=g: (20 * cyc(t, 0.3, i * 2.0), 0, g * 10 * cyc(t, 0.3, i * 2.0 + 1)), 10)
A.sine(a, 'antenna_l', 'rotation', (20, 0, 0), period=0.3)
A.sine(a, 'antenna_r', 'rotation', (20, 0, 0), period=0.3, phase=1.0)

if __name__ == '__main__':
    run(m)
