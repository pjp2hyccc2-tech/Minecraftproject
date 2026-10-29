"""Yukimammo (ユキマンモ) - shaggy snow mammoth with great curling tusks and a mop of hair over its eyes. Big herbivore."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, stamp_eyes, accent_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
from hcmodel.paint import vnoise  # noqa: E402

M('ym_fur', ['#5a5450', '#7e7874', '#a6a09a', '#cac4bc', '#e6e0d8', '#fbf8f2'], 'fur', contrast=0.38, noise=0.03, edge=0.05,
  belly='ym_fur_low', base=0.6)
M('ym_fur_low', ['#4a4038', '#685c52', '#887c70', '#a89c8e', '#c6bcae', '#e0d8ca'], 'fur', contrast=0.4, noise=0.03, edge=0.04)
M('ym_leg', ['#5a5450', '#7e7874', '#a6a09a', '#cac4bc', '#e6e0d8', '#fbf8f2'], 'fur', contrast=0.38, noise=0.03, edge=0.05, base=0.58)
M('ym_skin', ['#1e1814', '#30271f', '#463a2e', '#5e4f40', '#786654', '#947f6a'], 'skin', contrast=0.45, noise=0.03, edge=0.06)
M('ym_tusk', ['#6e624e', '#9a8c72', '#c0b296', '#ddd0b4', '#f0e8d2', '#fffcf2'], 'horn', edge=0.03, base=0.6)
M('ym_snow', ['#9aa8b8', '#b8c6d4', '#d2dee8', '#e6eef4', '#f4f8fb', '#ffffff'], 'smooth', noise=0.04, edge=0.0, base=0.72)

m = Model('yukimammo')
m.config.update({'head_bones': [['head', 0.7]], 'walk_speed': 4.6, 'run_speed': 0.65, 'walk_scale': 3.2})

# ------------------------------------------------------------------ body
body = m.bone('body', (0, 4, 2), (0, 0, 0))
torso = body.cube((-8, -8, -11), (16, 16, 22), 'ym_fur')
hump = body.cube((-7, -11, -11), (14, 4, 12), 'ym_fur')
skirt = body.cube((-8.5, 4, -11.5), (17, 7, 23), 'ym_fur_low', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.45}])
tail = body.bone('tail', (0, -4, 11), (20, 0, 0))
tail.cube((-1, 0, -1), (2, 6, 2), 'ym_skin')
tail.cube((-1.5, 5, -1.5), (3, 3, 3), 'ym_fur_low')


def snowcap(P, L, N, f, d, r, face):
    if face != 'top' and not (face in ('west', 'east', 'north', 'south') and True):
        return None
    n = vnoise(P[..., 0] * 0.25, P[..., 1] * 0.25, P[..., 2] * 0.25, 9)
    if face == 'top':
        return n > 0.32
    return (d < 0.18 + (n - 0.5) * 0.3)


accent_pass(m, [torso, hump], 'ym_snow', snowcap)

# ------------------------------------------------------------------ head
head = body.bone('head', (0, -5, -11), (8, 0, 0))
skull = head.cube((-6, -7, -9), (12, 12, 10), 'ym_fur')
dome = head.cube((-5, -11, -7), (10, 4, 8), 'ym_fur')
face = head.cube((-4, -1, -9.5), (8, 6, 1), 'ym_skin')
fringe = head.cube((-6.5, -8, -10), (13, 5, 3), 'ym_fur', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.45}])
accent_pass(m, [dome, skull], 'ym_snow', lambda P, L, N, f, d, r, fc: (vnoise(P[..., 0] * 0.3, P[..., 1] * 0.3, P[..., 2] * 0.3, 4) > 0.4) if fc == 'top' else None)
# bright little eyes peeking under the fringe
stamp(m, face, 'north', 0, 0, ['.WK..KW.', '..K..K..'], {'K': '#08080c', 'W': '#e6eef8'})
ear = head.bone('ear_l', (6, -3, -3), (0, -20, 0))
ear.cube((0, -2, -1), (2, 4, 2), 'ym_fur')
mirror_tree(head, ear)
jaw = head.bone('jaw', (0, 4.5, -7), (0, 0, 0))
jaw.cube((-2.5, -0.5, -2), (5, 2, 4), 'ym_skin', faces={'top': 'mouth'})
# trunk (hangs down, tip curls forward)
TRUNK = []
p = head
piv = (0, 3, -9)
for i, (w, ln, rx) in enumerate(((4, 4, 6), (3, 4, 8), (3, 3, 10), (2, 3, -30))):
    tb = p.bone('trunk%d' % (i + 1), piv, (rx, 0, 0))
    tb.cube((-w / 2, -0.5, -w / 2), (w, ln + 1, w), 'ym_skin', inflate=-0.1 if i else 0)
    TRUNK.append(tb.name)
    p = tb
    piv = (0, ln, 0)
stamp(m, p.cubes[0], 'north', 0, 3, ['KK'], {'K': '#0c0c10'})
# tusks: down-forward, sweeping up and inward
tk = head.bone('tusk_l', (3.2, 3.5, -8), (58, -16, 0))
tk.cube((-1.5, -1.5, -6), (3, 3, 7), 'ym_tusk', inflate=-0.2)
tk2 = tk.bone('tusk_l_mid', (0, 0, -5.5), (-46, 6, 0))
tk2.cube((-1, -1, -6.5), (2, 2, 7), 'ym_tusk')
tk3 = tk2.bone('tusk_l_tip', (0, 0, -6), (-48, 24, 0))
tk3.cube((-1, -1, -5.5), (2, 2, 6), 'ym_tusk', inflate=-0.25, decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.4, 'mat': 'horn_ivory'}])
mirror_tree(head, tk)

# ------------------------------------------------------------------ legs (shaggy pillars)
for key, x, z in (('fl', 5, -6.5), ('bl', 5, 7.5)):
    up = body.bone('leg_%s' % key, (x, 8, z), (0, 0, 0))
    up.cube((-3, -2, -3), (6, 8, 6), 'ym_leg')
    up.cube((-3.5, 3, -3.5), (7, 3, 7), 'ym_fur_low', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.5}])
    lo = up.bone('leg_%s_lo' % key, (0, 6, 0), (0, 0, 0))
    lo.cube((-2.5, -0.5, -2.5), (5, 5, 5), 'ym_leg')
    lo.cube((-3, 4, -3), (6, 2, 6), 'ym_skin')
    mirror_tree(body, up, '_' + key, '_' + key[0] + 'r')
LEGNAMES = {'fl': ('leg_fl', 'leg_fl_lo'), 'fr': ('leg_fr', 'leg_fr_lo'), 'bl': ('leg_bl', 'leg_bl_lo'), 'br': ('leg_br', 'leg_br_lo')}

fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim
WALK = {'fl': 0.0, 'br': math.pi * 0.5, 'fr': math.pi, 'bl': math.pi * 1.5}
TROT = {'fl': 0.0, 'br': 0.0, 'fr': math.pi, 'bl': math.pi}


def gait(a, period, amp, knee, phases, steps=16, t0=0.0, t1=None):
    for k, (u, lo) in LEGNAMES.items():
        ph = phases[k]
        A.sample(a, u, 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph), 0, 0), steps, t0, t1)
        A.sample(a, lo, 'rotation', lambda t, ph=ph, k=k: ((-knee if k[0] == 'f' else knee) * max(0.0, cyc(t, period, ph + (1.6 if k[0] == 'f' else -1.6))), 0, 0), steps, t0, t1)


def ears(a, pts):
    keys(a, 'ear_l', 'rotation', pts)
    keys(a, 'ear_r', 'rotation', [(t, (x, -y, -z)) for t, (x, y, z) in pts])


a = T('idle', 3.0, True)
A.sine(a, 'body', 'position', (0, 0.35, 0))
A.sine(a, 'head', 'rotation', (2, 3, 0), period=1.5, phase=0.5)
A.chain_wave(a, TRUNK, (5, 6, 0), phase_step=0.7, growth=1.2)
A.sine(a, 'ear_l', 'rotation', (0, -10, 0), period=1.5)
A.sine(a, 'ear_r', 'rotation', (0, 10, 0), period=1.5, phase=0.7)
A.sine(a, 'tail', 'rotation', (0, 0, 14), period=1.0)

a = T('walk', 1.6, True)
gait(a, 1.6, 20, 30, WALK)
A.sample(a, 'body', 'position', lambda t: (0, -0.6 * abs(cyc(t, 1.6)), 0), 16)
A.sine(a, 'body', 'rotation', (0.8, 1.5, 2.0), period=1.6)
A.sample(a, 'head', 'rotation', lambda t: (2 * cyc(t, 0.8, 0.8), 0, 0), 16)
A.chain_wave(a, TRUNK, (8, 10, 0), period=1.6, phase_step=0.6)

a = T('run', 0.9, True)
gait(a, 0.9, 32, 44, TROT, steps=14)
A.sample(a, 'body', 'position', lambda t: (0, -1.4 * abs(cyc(t, 0.9)), 0), 14)
A.sine(a, 'body', 'rotation', (2, 0, 2), period=0.9)
A.hold(a, 'head', 'rotation', (6, 0, 0))
A.chain_wave(a, TRUNK, (-10, 6, 0), period=0.45, phase_step=0.5)
ears(a, [(0, (0, -30, 0)), (0.9, (0, -30, 0))])

# tusk_charge (60 ticks): trumpet, then charge tusks-first, skid
a = T('tusk_charge', 3.0)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (-26, 0, 0)), (0.75, (-24, 0, 0)), (0.9, (22, 0, 0)), (2.3, (20, 0, 0)), (2.5, (4, 0, 0)), (3.0, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (-10, 0, 0)), (0.75, (-8, 0, 0)), (0.9, (5, 0, 0)), (2.3, (5, 0, 0)), (2.45, (-6, 0, 0)), (3.0, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.35, (0, -1.5, 2)), (0.75, (0, -1.5, 2)), (0.9, (0, 0, 0)), (3.0, (0, 0, 0))])
for i, tb in enumerate(TRUNK):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.35, (-40 - 6 * i, 0, 0)), (0.75, (-40 - 6 * i, 0, 0)), (0.9, (-12, 0, 0)), (2.3, (-10, 0, 0)), (3.0, (0, 0, 0))])
for k, (u, lo) in LEGNAMES.items():
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.35, (-24 if k[0] == 'f' else 10, 0, 0)), (0.75, (-24 if k[0] == 'f' else 10, 0, 0)), (0.9, (0, 0, 0))])
gait(a, 0.5, 36, 46, TROT, steps=24, t0=0.9, t1=2.3)
for k, (u, lo) in LEGNAMES.items():
    keys(a, u, 'rotation', [(2.45, (-24 if k[0] == 'f' else -10, 0, 0)), (2.7, (-14 if k[0] == 'f' else -4, 0, 0)), (3.0, (0, 0, 0))])
    keys(a, lo, 'rotation', [(2.45, (0, 0, 0)), (3.0, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.35, (0, 40, 0)), (0.75, (0, 40, 0)), (0.9, (0, -30, 0)), (2.3, (0, -30, 0)), (3.0, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.35, (24, 0, 0)), (0.75, (20, 0, 0)), (0.9, (0, 0, 0)), (3.0, (0, 0, 0))])

# tusk_sweep (32 ticks): wind up right, sweep left at ticks 12-18
a = T('tusk_sweep', 1.6)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (10, -40, -10)), (0.62, (14, 0, 0)), (0.9, (10, 42, 12)), (1.2, (6, 20, 4)), (1.6, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.5, (0, -8, 2)), (0.9, (0, 10, -2)), (1.6, (0, 0, 0))])
A.chain_wave(a, TRUNK, (0, 14, 0), period=0.8, phase_step=0.5)
ears(a, [(0, (0, 0, 0)), (0.5, (0, 30, 0)), (0.9, (0, 30, 0)), (1.6, (0, 0, 0))])

# stomp (44 ticks): rear up on the hind legs, slam down at tick 22
a = T('stomp', 2.2)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.85, (-24, 0, 0)), (1.0, (-26, 0, 0)), (1.1, (4, 0, 0)), (1.3, (1, 0, 0)), (2.2, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.85, (0, -3, 4)), (1.1, (0, 1, 0)), (1.35, (0, 0, 0)), (2.2, (0, 0, 0))])
for k in ('fl', 'fr'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.85, (-40, 0, 0)), (1.0, (-46, 0, 0)), (1.1, (8, 0, 0)), (1.35, (0, 0, 0)), (2.2, (0, 0, 0))])
    keys(a, LEGNAMES[k][1], 'rotation', [(0, (0, 0, 0)), (0.85, (40, 0, 0)), (1.1, (0, 0, 0)), (2.2, (0, 0, 0))])
for k in ('bl', 'br'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.85, (24, 0, 0)), (1.1, (0, 0, 0)), (2.2, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.85, (-20, 0, 0)), (1.1, (16, 0, 0)), (1.5, (6, 0, 0)), (2.2, (0, 0, 0))])
for i, tb in enumerate(TRUNK):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.85, (-30 - 8 * i, 0, 0)), (1.1, (10, 0, 0)), (2.2, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (22, 0, 0)), (1.1, (10, 0, 0)), (2.2, (0, 0, 0))])

# graze (80 ticks): trunk gathers grass from the ground and brings it to the mouth, twice
a = T('graze', 4.0)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (14, 0, 0)), (3.5, (14, 0, 0)), (4.0, (0, 0, 0))])
for i, tb in enumerate(TRUNK):
    down = 14 + 6 * i
    curl = (-30, -50, -60, -70)[i]
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.6, (down, 0, 0)), (1.1, (down + 6, 0, 0)), (1.6, (curl, 0, 0)), (2.0, (curl, 0, 0)),
                             (2.5, (down, 0, 0)), (3.0, (down + 6, 0, 0)), (3.5, (curl * 0.6, 0, 0)), (4.0, (0, 0, 0))])
A.sample(a, 'jaw', 'rotation', lambda t: (14 * max(0.0, math.sin(t * 12)) if (1.6 < t < 2.4 or 3.4 < t < 3.9) else 0, 0, 0), 60)
ears(a, [(0, (0, 0, 0)), (1.0, (0, -14, 0)), (2.0, (0, 6, 0)), (3.0, (0, -14, 0)), (4.0, (0, 0, 0))])

# trumpet (36 ticks): head up, trunk raised, ears spread
a = T('trumpet', 1.8)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (-28, 0, 0)), (1.4, (-26, 0, 0)), (1.8, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (-6, 0, 0)), (1.4, (-6, 0, 0)), (1.8, (0, 0, 0))])
for i, tb in enumerate(TRUNK):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.4, (-60 - 8 * i, 0, 0)), (0.8, (-66 - 8 * i, 0, 0)), (1.4, (-60 - 8 * i, 0, 0)), (1.8, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.4, (0, 50, 0)), (1.4, (0, 50, 0)), (1.8, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.4, (26, 0, 0)), (1.4, (22, 0, 0)), (1.8, (0, 0, 0))])

# shake: shakes the snow off its coat
a = T('shake', 1.5)
A.sample(a, 'body', 'rotation', lambda t: (0, 0, 8 * math.sin(t * 22) * math.sin(math.pi * t / 1.5)), 40)
A.sample(a, 'head', 'rotation', lambda t: (0, 14 * math.sin(t * 22 + 1.0) * math.sin(math.pi * t / 1.5), 0), 40)
A.sample(a, 'ear_l', 'rotation', lambda t: (0, -30 * abs(math.sin(t * 22 + 1.6)) * math.sin(math.pi * t / 1.5), 0), 40)
A.sample(a, 'ear_r', 'rotation', lambda t: (0, 30 * abs(math.sin(t * 22 + 1.6)) * math.sin(math.pi * t / 1.5), 0), 40)
A.chain_wave(a, TRUNK, (0, 16, 0), period=0.3, phase_step=0.5)

a = T('flinch', 0.8)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.12, (-5, 0, 5)), (0.4, (-1, 0, -2)), (0.8, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.12, (-18, 14, 0)), (0.45, (4, -4, 0)), (0.8, (0, 0, 0))])
for i, tb in enumerate(TRUNK):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.12, (-30, 10, 0)), (0.8, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.12, (0, 40, 0)), (0.8, (0, 0, 0))])

a = T('death', 1.8)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (-6, 0, -5)), (1.1, (0, 0, 84)), (1.3, (0, 0, 79)), (1.8, (0, 0, 80))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.35, (0, -1, 0)), (1.1, (2, 9, 0)), (1.8, (2, 9, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (-22, 0, 0)), (1.2, (14, 0, 0)), (1.8, (16, 0, 0))])
for i, tb in enumerate(TRUNK):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.4, (-40, 0, 0)), (1.2, (10, 0, 0)), (1.8, (14, 6, 0))])
for k, (u, lo) in LEGNAMES.items():
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (1.1, (-12 if k[0] == 'f' else 12, 0, 0)), (1.8, (-16 if k[0] == 'f' else 16, 0, 0))])

a = T('sleep', 4.0, True)
A.hold(a, 'body', 'position', (0, 9, 0))
A.sine(a, 'body', 'position', (0, 0.4, 0), offset=(0, 9, 0))
A.hold(a, 'head', 'rotation', (18, 0, 0))
for k, (u, lo) in LEGNAMES.items():
    A.hold(a, u, 'rotation', (-75 if k[0] == 'f' else 75, 0, 0))
    A.hold(a, lo, 'rotation', (80 if k[0] == 'f' else -80, 0, 0))
for i, tb in enumerate(TRUNK):
    A.hold(a, tb, 'rotation', (-20 - 10 * i, 0, 0))
ears(a, [(0, (0, -10, 0)), (4.0, (0, -10, 0))])

a = T('stun', 1.6, True)
A.sine(a, 'body', 'rotation', (0, 0, 5), period=1.6)
A.hold(a, 'body', 'position', (0, 1, 0))
A.sample(a, 'head', 'rotation', lambda t: (12 + 5 * cyc(t, 1.6), 12 * cyc(t, 1.6, 1.57), 10 * cyc(t, 0.8)), 16)
A.chain_wave(a, TRUNK, (6, 14, 0), period=1.6, phase_step=0.6)
ears(a, [(0, (0, -20, 0)), (1.6, (0, -20, 0))])

if __name__ == '__main__':
    run(m)
