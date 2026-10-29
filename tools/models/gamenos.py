"""Gamenos (ガメノス) - huge placid tortoise whose plated shell is overgrown with moss and flowers. Big herbivore."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, stamp_eyes, accent_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
from hcmodel.paint import vnoise  # noqa: E402

M('gm_shell', ['#1a2016', '#2b3524', '#3f4c32', '#566442', '#6f7e54', '#8c9a6c'], 'plates', scale=1.5, contrast=0.9, noise=0.04,
  edge=0.12)
M('gm_rim', ['#16180e', '#262816', '#3a3b22', '#504f2e', '#68663c', '#827e4e'], 'plates', scale=1.1, contrast=0.8, edge=0.1)
M('gm_moss', ['#14240c', '#213a12', '#30541a', '#427224', '#58902e', '#74ae40'], 'fur', contrast=0.8, noise=0.05, edge=0.0)
M('gm_skin', ['#2a291c', '#44422e', '#615e42', '#7f7b58', '#9d9872', '#bcb690'], 'scales', scale=1.3, contrast=0.55, noise=0.04,
  edge=0.07, belly='gm_skin_b')
M('gm_leg', ['#2a291c', '#44422e', '#615e42', '#7f7b58', '#9d9872', '#bcb690'], 'scales', scale=1.3, contrast=0.55, noise=0.04, edge=0.07)
M('gm_skin_b', ['#4a4630', '#6a6444', '#8a845c', '#a8a276', '#c4be92', '#ddd8b0'], 'scales', scale=1.0, contrast=0.4, edge=0.05)
M('gm_plastron', ['#4a3e22', '#6c5c32', '#907c46', '#b09c5e', '#ccbc7c', '#e4d6a0'], 'plates', scale=1.2, contrast=0.6, edge=0.08)
M('gm_beak', ['#14110c', '#241f17', '#3a3226', '#524838', '#6c604c', '#887a64'], 'horn', edge=0.04)
M('gm_nail', ['#3a3226', '#5a4e3c', '#7c6e56', '#9c8e74', '#bcae92', '#d8ccb2'], 'horn', edge=0.0)
M('gm_grass', ['#1c3a10', '#2c5a18', '#3c7a20', '#52982c', '#6cb63a', '#8ed250'], 'smooth', noise=0.06, edge=0.0)
M('gm_flower_r', ['#5a0c18', '#8a1424', '#b82034', '#e03448', '#f45e6c', '#ff94a0'], 'smooth', edge=0.0, base=0.6)
M('gm_flower_y', ['#6a4a00', '#9a7000', '#c89a10', '#ecc028', '#fcdc54', '#fff094'], 'smooth', edge=0.0, base=0.6)
M('gm_flower_w', ['#8a8a96', '#aaaab6', '#c8c8d2', '#e0e0e8', '#f2f2f6', '#ffffff'], 'smooth', edge=0.0, base=0.6)
M('gm_club', ['#1e1a12', '#322c20', '#4a4230', '#645a44', '#80745a', '#9e9076'], 'rock', scale=0.8, contrast=0.8, edge=0.06)

m = Model('gamenos')
m.config.update({'head_bones': [['neck', 0.4], ['head', 0.5]], 'walk_speed': 5.0, 'run_speed': 0.7, 'walk_scale': 3.5})

# ------------------------------------------------------------------ shell (stepped dome)
body = m.bone('body', (0, 11, 1), (0, 0, 0))
rim = body.cube((-11, -3, -14), (22, 5, 28), 'gm_rim')
mid = body.cube((-10, -8, -13), (20, 6, 26), 'gm_shell')
top = body.cube((-8, -12, -11), (16, 4, 22), 'gm_shell')
crown = body.cube((-5, -14, -7), (10, 2, 14), 'gm_shell')
plastron = body.cube((-9, 1.5, -12), (18, 2, 24), 'gm_plastron')
shell_cubes = [rim, mid, top, crown]


# moss patches on the upper shell
def moss(P, L, N, f, d, r, face):
    if face == 'bottom':
        return None
    n = vnoise(P[..., 0] * 0.22, P[..., 1] * 0.22, P[..., 2] * 0.22, 3)
    if face == 'top':
        return n > 0.36
    return (n > 0.5) & (d < 0.55)


accent_pass(m, shell_cubes, 'gm_moss', moss)

# little garden on the shell: grass tufts (crossed planes) and flowers
garden = body.bone('garden', (0, -14, 0), (0, 0, 0))
for i, (x, z, h, rot) in enumerate(((-3, -4, 4, 0), (3, 2, 3, 30), (-1, 5, 5, 60), (5, -6, 3, 15), (-6, 1, 3, 45), (1, -1, 4, 80))):
    tuft = garden.bone('grass%d' % i, (x, (2 if abs(x) > 4 or abs(z) > 6 else 0), z), (0, rot, 0))
    tuft.cube((-2, -h, 0), (4, h, 0), 'gm_grass', decals=[{'type': 'cutout', 'shape': 'spike'}])
    tuft.cube((0, -h, -2), (0, h, 4), 'gm_grass', decals=[{'type': 'cutout', 'shape': 'spike'}])
for i, (x, z, mat) in enumerate(((2, -3, 'gm_flower_r'), (-4, 3, 'gm_flower_y'), (4, 5, 'gm_flower_w'), (-2, -7, 'gm_flower_y'), (6, -1, 'gm_flower_r'))):
    yb = 2 if (abs(x) > 4 or abs(z) > 6) else 0
    stem = garden.bone('flower%d' % i, (x, yb, z), (0, 20 * i, 0))
    stem.cube((0, -3, -0.5), (0, 3, 1), 'gm_grass')
    stem.cube((-1, -4, -1), (2, 1, 2), mat)

# ------------------------------------------------------------------ neck + head
neck = body.bone('neck', (0, -1, -13), (-12, 0, 0))
neck.cube((-3.5, -3.5, -7), (7, 7, 8), 'gm_skin')
head = neck.bone('head', (0, -0.5, -6), (14, 0, 0))
skull = head.cube((-4, -4, -7), (8, 7, 8), 'gm_skin')
beak = head.cube((-3, -0.5, -8.5), (6, 3, 2), 'gm_beak')
head.cube((-4.5, -4.5, -6), (9, 2, 5), 'gm_skin', inflate=-0.2)                       # heavy brow
jaw = head.bone('jaw', (0, 2.5, -3), (0, 0, 0))
jaw.cube((-3.5, 0, -5.5), (7, 2, 6), 'gm_skin', faces={'top': 'mouth'})
jaw.cube((-2.5, 0.5, -6), (5, 2, 1), 'gm_beak', inflate=-0.1)
# sleepy, gentle eyes (heavy lid)
stamp_eyes(m, skull, ['LLL', 'KWK', '.K.'], {'L': '#3a3828', 'K': '#100c08', 'W': '#e8dca0'}, front=1, top=2)
stamp(m, beak, 'top', 1, 0, ['K..K'], {'K': '#0a0806'})

# ------------------------------------------------------------------ legs (stumpy pillars)
for key, x, z in (('fl', 8, -8), ('bl', 8, 8)):
    up = body.bone('leg_%s' % key, (x, 2, z), (0, 0, 0))
    up.cube((-3, -2, -3), (6, 7, 6), 'gm_leg')
    ft = up.bone('leg_%s_foot' % key, (0, 4.5, 0), (0, 0, 0))
    ft.cube((-3.5, 0, -3.5), (7, 3, 7), 'gm_leg')
    for nx in (-2.5, -0.5, 1.5):
        ft.cube((nx, 1.5, -4), (1, 2, 1), 'gm_nail')
    mirror_tree(body, up, '_' + key, '_' + key[0] + 'r')
LEGNAMES = {'fl': ('leg_fl', 'leg_fl_foot'), 'fr': ('leg_fr', 'leg_fr_foot'), 'bl': ('leg_bl', 'leg_bl_foot'), 'br': ('leg_br', 'leg_br_foot')}

# ------------------------------------------------------------------ club tail
t1 = body.bone('tail1', (0, 0, 13), (10, 0, 0))
t1.cube((-2.5, -2, 0), (5, 4, 6), 'gm_skin')
t2 = t1.bone('tail2', (0, 0, 5), (-6, 0, 0))
t2.cube((-2, -1.5, 0), (4, 3, 5), 'gm_skin')
club = t2.bone('tail_club', (0, 0, 4.5), (-4, 0, 0))
club.cube((-3, -2.5, 0), (6, 5, 5), 'gm_club')
for sx, sy in ((-3.5, 0), (2.5, 0), (-0.5, -3.5)):
    club.cube((sx, sy - 0.5, 1.5), (1, 1, 2), 'gm_nail')
TAIL = ['tail1', 'tail2', 'tail_club']

fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim
DIAG = {'fl': 0.0, 'br': 0.0, 'fr': math.pi, 'bl': math.pi}


def gait(a, period, amp, lift, steps=16):
    for k, (u, ft) in LEGNAMES.items():
        ph = DIAG[k]
        A.sample(a, u, 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph), 0, 0), steps)
        A.sample(a, u, 'position', lambda t, ph=ph: (0, -lift * max(0.0, cyc(t, period, ph - math.pi / 2)), 0), steps)
        A.sample(a, ft, 'rotation', lambda t, ph=ph: (-amp * 0.6 * cyc(t, period, ph), 0, 0), steps)


a = T('idle', 4.0, True)
A.sine(a, 'body', 'position', (0, 0.3, 0))
A.sine(a, 'neck', 'rotation', (2, 4, 0), phase=0.4)
A.sine(a, 'head', 'rotation', (-2, 5, 2), period=2.0, phase=1.0)
A.sine(a, 'jaw', 'rotation', (1.5, 0, 0), offset=(1.5, 0, 0), period=1.0)
A.chain_wave(a, TAIL, (0, 5, 0), phase_step=0.6)
A.sine(a, 'garden', 'rotation', (0, 0, 1.0), period=2.0)

a = T('walk', 1.6, True)
gait(a, 1.6, 18, 1.5)
A.sine(a, 'body', 'rotation', (0, 1.5, 2.5), period=1.6)
A.sample(a, 'body', 'position', lambda t: (0, -0.4 * abs(cyc(t, 1.6)), 0), 16)
A.sample(a, 'neck', 'rotation', lambda t: (2 * cyc(t, 0.8), 3 * cyc(t, 1.6, 1.0), 0), 16)
A.chain_wave(a, TAIL, (0, 8, 0), period=1.6, phase_step=0.6)

a = T('run', 1.0, True)
gait(a, 1.0, 26, 2.5, steps=14)
A.sine(a, 'body', 'rotation', (0, 2, 4), period=1.0)
A.sample(a, 'body', 'position', lambda t: (0, -0.8 * abs(cyc(t, 1.0)), 0), 14)
A.hold(a, 'neck', 'rotation', (10, 0, 0))
A.chain_wave(a, TAIL, (0, 10, 0), period=1.0, phase_step=0.5)

# tail_swing (36 ticks): wind up to one side, sweep behind at ticks 12-21
a = T('tail_swing', 1.8)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 14, 2)), (0.75, (0, -18, -3)), (1.05, (0, -22, -3)), (1.8, (0, 0, 0))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.5, (6, 55, 0)), (0.75, (2, -20, 0)), (1.05, (2, -60, 0)), (1.4, (0, -20, 0)), (1.8, (0, 0, 0))])
keys(a, 'tail2', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 30, 0)), (0.75, (0, -10, 0)), (1.05, (0, -35, 0)), (1.4, (0, -10, 0)), (1.8, (0, 0, 0))])
keys(a, 'tail_club', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 20, 0)), (0.85, (0, -25, 0)), (1.1, (0, -30, 0)), (1.8, (0, 0, 0))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.5, (10, -10, 0)), (1.05, (6, 14, 0)), (1.8, (0, 0, 0))])
for k, (u, ft) in LEGNAMES.items():
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.5, (0, 0, 6 if k[1] == 'l' else -6)), (1.05, (0, 0, -6 if k[1] == 'l' else 6)), (1.8, (0, 0, 0))])

# stomp (40 ticks): rear the front up, slam down at tick 21
a = T('stomp', 2.0)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.8, (-20, 0, 0)), (0.95, (-22, 0, 0)), (1.05, (4, 0, 0)), (1.25, (1, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.8, (0, -2, 3)), (1.05, (0, 0.8, 0)), (1.3, (0, 0, 0)), (2.0, (0, 0, 0))])
for k in ('fl', 'fr'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.8, (-30, 0, 0)), (0.95, (-36, 0, 0)), (1.05, (10, 0, 0)), (1.3, (0, 0, 0)), (2.0, (0, 0, 0))])
for k in ('bl', 'br'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.8, (22, 0, 0)), (1.05, (-2, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.8, (-14, 0, 0)), (1.05, (16, 0, 0)), (1.4, (8, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.7, (20, 0, 0)), (1.0, (26, 0, 0)), (1.3, (0, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.8, (-14, 0, 0)), (1.05, (10, 0, 0)), (2.0, (0, 0, 0))])

# graze (90 ticks): stretch down, bite and chew
a = T('graze', 4.5)
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.7, (40, 0, 0)), (2.0, (40, 8, 0)), (2.4, (22, 0, 0)), (3.4, (22, 0, 0)), (3.8, (40, -8, 0)), (4.1, (30, 0, 0)), (4.5, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.7, (14, 0, 0)), (2.0, (14, 0, 0)), (2.4, (-6, 0, 0)), (3.4, (-6, 0, 0)), (3.8, (14, 0, 0)), (4.5, (0, 0, 0))])
A.sample(a, 'jaw', 'rotation', lambda t: (16 * max(0.0, math.sin(t * 9)) if (0.8 < t < 2.0 or 2.4 < t < 3.4) else 0, 0, 0), 80)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.7, (3, 0, 0)), (4.1, (3, 0, 0)), (4.5, (0, 0, 0))])
A.chain_wave(a, TAIL, (0, 6, 0), period=1.5, phase_step=0.5)

# yawn (50 ticks): slow, enormous yawn
a = T('yawn', 2.5)
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.6, (-18, 0, 0)), (1.8, (-18, 0, 0)), (2.5, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (-20, 0, 6)), (1.8, (-16, 0, -6)), (2.5, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.5, (8, 0, 0)), (1.0, (46, 0, 0)), (1.6, (44, 0, 0)), (2.0, (0, 0, 0)), (2.5, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (1.0, (0, -0.8, 0)), (2.5, (0, 0, 0))])

# flinch: duck the head into the shell
a = T('flinch', 0.9)
keys(a, 'neck', 'position', [(0, (0, 0, 0)), (0.12, (0, 1, 5)), (0.6, (0, 1, 4)), (0.9, (0, 0, 0))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.12, (-10, 12, 0)), (0.9, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.12, (-3, 0, 4)), (0.5, (0, 0, -2)), (0.9, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.15, (20, 0, 0)), (0.6, (4, 0, 0)), (0.9, (0, 0, 0))])

a = T('death', 1.8)
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -0.8, 0)), (0.9, (0, 7, 0)), (1.1, (0, 6.5, 0)), (1.8, (0, 6.6, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-4, 0, 3)), (0.9, (3, 0, -6)), (1.8, (2, 0, -5))])
for k, (u, ft) in LEGNAMES.items():
    s = 1 if k[1] == 'l' else -1
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.9, (-40 if k[0] == 'f' else 40, 0, -50 * s)), (1.8, (-44 if k[0] == 'f' else 44, 0, -54 * s))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.0, (40, 18, 0)), (1.8, (44, 20, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (1.0, (-30, 0, 10)), (1.8, (-34, 0, 12))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (26, 0, 0)), (1.8, (14, 0, 0))])
for b in TAIL:
    keys(a, b, 'rotation', [(0, (0, 0, 0)), (1.0, (14, 10, 0)), (1.8, (14, 12, 0))])

a = T('sleep', 4.0, True)
A.hold(a, 'body', 'position', (0, 7, 0))
A.sine(a, 'body', 'position', (0, 0.3, 0), offset=(0, 7, 0))
for k, (u, ft) in LEGNAMES.items():
    s = 1 if k[1] == 'l' else -1
    A.hold(a, u, 'rotation', (-50 if k[0] == 'f' else 50, 0, -40 * s))
A.hold(a, 'neck', 'position', (0, 1, 3))
A.hold(a, 'neck', 'rotation', (30, 0, 0))
A.hold(a, 'head', 'rotation', (-24, 0, 0))
for b in TAIL:
    A.hold(a, b, 'rotation', (10, 14, 0))

a = T('stun', 1.6, True)
A.sine(a, 'body', 'rotation', (0, 0, 4), period=1.6)
A.sample(a, 'neck', 'rotation', lambda t: (16 + 6 * cyc(t, 1.6), 16 * cyc(t, 1.6, 1.57), 0), 16)
A.sample(a, 'head', 'rotation', lambda t: (0, 0, 14 * cyc(t, 0.8)), 16)
A.hold(a, 'jaw', 'rotation', (18, 0, 0))
A.chain_wave(a, TAIL, (0, 8, 0), period=1.6, phase_step=0.6)

# trapped (small monsters cannot be trapped by the core; kept so every required state pose exists): pit struggle
a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'body', 'rotation', (3, 0, 5), period=0.5)
A.sample(a, 'neck', 'rotation', lambda t: (-12 + 8 * cyc(t, 0.5), 14 * cyc(t, 1.0), 0), 12)
A.sine(a, 'jaw', 'rotation', (10, 0, 0), period=0.25, offset=(12, 0, 0))
for k, (u, lo) in LEGNAMES.items():
    ph = {'fl': 0.0, 'fr': math.pi, 'bl': math.pi / 2, 'br': 3 * math.pi / 2}[k]
    A.sample(a, u, 'rotation', lambda t, ph=ph: (26 * cyc(t, 0.5, ph), 0, 0), 12)
A.chain_wave(a, TAIL, (0, 16, 0), period=0.5, phase_step=0.5)

if __name__ == '__main__':
    run(m)
