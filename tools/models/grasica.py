"""Grasica (グラシカ) - gentle spotted deer with branching antlers. Small herbivore."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, stamp_eyes, accent_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
from hcmodel.paint import hash2  # noqa: E402

M('gr_fur', ['#2e1b0e', '#4f3018', '#744824', '#9a6234', '#bc8048', '#d9a062'], 'fur', contrast=0.45, noise=0.03, edge=0.06,
  belly='gr_belly')
M('gr_leg', ['#2e1b0e', '#4f3018', '#744824', '#9a6234', '#bc8048', '#d9a062'], 'fur', contrast=0.4, noise=0.03, edge=0.06)
M('gr_dark', ['#1c1009', '#2e1c10', '#442a18', '#5c3a22', '#764c2e', '#90603c'], 'fur', contrast=0.4, noise=0.03, edge=0.04)
M('gr_belly', ['#8c7458', '#b09474', '#ceb492', '#e6d0b0', '#f4e6cc', '#fffaee'], 'fur', contrast=0.3, noise=0.03, edge=0.03, base=0.66)
M('gr_spot', ['#a89880', '#c8b8a0', '#e0d2bc', '#f0e6d4', '#faf4e8', '#ffffff'], 'smooth', noise=0.02, edge=0.0, base=0.7)
M('gr_antler', ['#3e3226', '#5e4c3a', '#806a52', '#a08a6e', '#c2ac8e', '#e2d0b4'], 'horn', edge=0.03, base=0.6)
M('gr_hoof', ['#0e0b09', '#1c1714', '#2c2520', '#3e352e', '#524840', '#6a5e54'], 'horn', edge=0.0)
M('gr_nose', ['#0a0808', '#141010', '#201a1a', '#2e2626', '#3e3434', '#504444'], 'smooth', edge=0.0)

m = Model('grasica')
m.config.update({'head_bones': [['neck', 0.35], ['head', 0.55]], 'walk_speed': 3.0, 'run_speed': 0.6, 'walk_scale': 2.8})

# ------------------------------------------------------------------ body
body = m.bone('body', (0, 9, 1), (-2, 0, 0))
torso = body.cube((-3.5, -3.5, -8), (7, 7, 14), 'gr_fur', faces={'top': 'gr_dark'}, decals=[{'type': 'belly_band'}])
body.cube((-3, -2.5, -9), (6, 6, 2), 'gr_fur', decals=[{'type': 'belly_band'}])          # chest
tail = body.bone('tail', (0, -2.5, 6), (-35, 0, 0))
tail_c = tail.cube((-1.5, -1, 0), (3, 2, 3), 'gr_fur', faces={'bottom': 'gr_spot', 'south': 'gr_spot'})

# ------------------------------------------------------------------ neck + head
neck = body.bone('neck', (0, -1.5, -7.5), (-52, 0, 0))
neck_c = neck.cube((-1.5, -2, -8), (3, 4, 9), 'gr_fur', faces={'top': 'gr_dark'})
head = neck.bone('head', (0, -0.5, -7), (62, 0, 0))
skull = head.cube((-2.5, -3, -4), (5, 5, 5), 'gr_fur', faces={'top': 'gr_dark'})
muzzle = head.cube((-1.5, -1.5, -7.5), (3, 3, 4), 'gr_fur')
head.cube((-1, -1.25, -8), (2, 1, 1), 'gr_nose', inflate=-0.05)
jaw = head.bone('jaw', (0, 1.5, -3), (0, 0, 0))
jaw.cube((-1.5, -0.5, -4.5), (3, 1, 5), 'gr_belly', inflate=-0.05)
# big soft eyes with a highlight, lashes on top
stamp_eyes(m, skull, ['L.', 'KW', 'KK'], {'K': '#120c08', 'W': '#ffffff', 'L': '#1c120a'}, front=1, top=1)
stamp(m, muzzle, 'east', 0, 2, ['BBBB'], {'B': '#e8dcc8'})
stamp(m, muzzle, 'west', 0, 2, ['BBBB'], {'B': '#e8dcc8'}, mirror=True)
stamp(m, muzzle, 'north', 0, 2, ['BBB'], {'B': '#e8dcc8'})
ear = head.bone('ear_l', (2, -2.5, -0.5), (10, -25, 55))
ear.cube((0, -1, -0.5), (4, 2, 1), 'gr_fur', faces={'north': 'gr_belly'})
mirror_tree(head, ear)
# antlers: beam sweeping up/back with forward tines
ant = head.bone('antler_l', (1.3, -3, -1.5), (-18, 0, 18))
ant.cube((-0.5, -5, -0.5), (1, 5, 1), 'gr_antler')
t1 = ant.bone('antler_l_t1', (0, -2.5, 0), (-55, 0, 0))
t1.cube((-0.5, -3, -0.5), (1, 3, 1), 'gr_antler', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'horn_ivory'}])
up2 = ant.bone('antler_l_beam', (0, -4.5, 0), (25, 0, 10))
up2.cube((-0.5, -4, -0.5), (1, 4, 1), 'gr_antler', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'horn_ivory'}])
t2 = up2.bone('antler_l_t2', (0, -2, 0), (-50, 0, -20))
t2.cube((-0.5, -3, -0.5), (1, 3, 1), 'gr_antler', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'horn_ivory'}])
t3 = up2.bone('antler_l_t3', (0, -3.5, 0), (30, 0, 25))
t3.cube((-0.5, -2, -0.5), (1, 2, 1), 'gr_antler', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'horn_ivory'}])
mirror_tree(head, ant)

# ------------------------------------------------------------------ legs (slender)
for key, x, z, thick in (('fl', 2.2, -6.0, False), ('bl', 2.2, 4.5, True)):
    up = body.bone('leg_%s' % key, (x, 2.5, z), (0, 0, 0))
    if thick:
        up.cube((-1.5, -1.5, -2), (3, 7, 4), 'gr_leg')
    else:
        up.cube((-1, -1, -1), (2, 6, 2), 'gr_leg')
    lo = up.bone('leg_%s_lo' % key, (0, 5, 0 if not thick else 0.5), (0, 0, 0))
    lo.cube((-1, -0.5, -1), (2, 6, 2), 'gr_leg', inflate=-0.25)
    lo.cube((-1, 5, -1), (2, 1, 2), 'gr_hoof', inflate=-0.1)
    mirror_tree(body, up, '_' + key, '_' + key[0] + 'r')
LEGNAMES = {'fl': ('leg_fl', 'leg_fl_lo'), 'fr': ('leg_fr', 'leg_fr_lo'), 'bl': ('leg_bl', 'leg_bl_lo'), 'br': ('leg_br', 'leg_br_lo')}


# white spots in rows along the back and flanks
def spots(P, L, N, f, d, r, face):
    if face == 'bottom' or N[1] > 0.4:
        return None
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    row = np.floor((y + 40) / 2.2)
    zz = z + (row % 2) * 1.6
    dot = ((np.floor(zz) % 3) == 0) & ((np.floor(y + 40) % 2.2) < 1.0)
    rnd = hash2(np.floor(zz / 3).astype(np.int64), row.astype(np.int64), 5) > 0.3
    upper = (d < 0.58) if face in ('west', 'east', 'north', 'south') else (np.abs(x) > 1.2)
    return dot & rnd & upper


accent_pass(m, [torso], 'gr_spot', spots)
fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim
WALK = {'fl': 0.0, 'br': math.pi * 0.5, 'fr': math.pi, 'bl': math.pi * 1.5}
BOUND = {'fl': 0.0, 'fr': 0.35, 'bl': math.pi, 'br': math.pi + 0.35}


def gait(a, period, amp, knee, phases, steps=16):
    for k, (u, lo) in LEGNAMES.items():
        ph = phases[k]
        A.sample(a, u, 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph), 0, 0), steps)
        A.sample(a, lo, 'rotation', lambda t, ph=ph, k=k: ((-knee if k[0] == 'f' else knee) * max(0.0, cyc(t, period, ph + (1.6 if k[0] == 'f' else -1.6))), 0, 0), steps)


def ears(a, l_keys, r_keys):
    keys(a, 'ear_l', 'rotation', l_keys)
    keys(a, 'ear_r', 'rotation', [(t, (x, -y, -z)) for t, (x, y, z) in r_keys])


a = T('idle', 3.0, True)
A.sine(a, 'body', 'position', (0, 0.25, 0))
A.sine(a, 'neck', 'rotation', (1.5, 2, 0), phase=0.6)
A.sine(a, 'head', 'rotation', (-2, 3, 2), period=1.5, phase=1.1)
ears(a, [(0, (0, 0, 0)), (1.8, (0, 0, 0)), (1.9, (0, 0, -25)), (2.05, (0, 0, 0)), (3.0, (0, 0, 0))],
     [(0, (0, 0, 0)), (0.7, (0, 0, 0)), (0.8, (0, 0, -25)), (0.95, (0, 0, 0)), (3.0, (0, 0, 0))])
keys(a, 'tail', 'rotation', [(0, (0, 0, 0)), (2.3, (0, 0, 0)), (2.4, (-25, 0, 0)), (2.5, (0, 0, 0)), (2.6, (-25, 0, 0)), (2.7, (0, 0, 0)), (3.0, (0, 0, 0))])
A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0), period=0.75)

a = T('walk', 1.1, True)
gait(a, 1.1, 24, 36, WALK)
A.sample(a, 'body', 'position', lambda t: (0, -0.4 * abs(cyc(t, 1.1)), 0), 16)
A.sine(a, 'body', 'rotation', (0.8, 1.5, 1.5), period=1.1)
A.sample(a, 'neck', 'rotation', lambda t: (3 * cyc(t, 0.55, 0.9), 0, 0), 16)
A.sample(a, 'head', 'rotation', lambda t: (-3 * cyc(t, 0.55, 0.9), 0, 0), 16)

a = T('run', 0.55, True)
gait(a, 0.55, 46, 60, BOUND, steps=12)
A.sample(a, 'body', 'position', lambda t: (0, -2.0 * max(0.0, cyc(t, 0.55, 0.7)), 0), 12)
A.sample(a, 'body', 'rotation', lambda t: (6 * cyc(t, 0.55, 1.4), 0, 0), 12)
A.hold(a, 'neck', 'rotation', (18, 0, 0))
A.hold(a, 'head', 'rotation', (-14, 0, 0))
ears(a, [(0, (-20, 0, -10)), (0.55, (-20, 0, -10))], [(0, (-20, 0, -10)), (0.55, (-20, 0, -10))])
A.hold(a, 'tail', 'rotation', (-30, 0, 0))

# graze (70 ticks): neck down, nibble and chew, look up once
a = T('graze', 3.5)
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.5, (72, 0, 0)), (1.9, (72, 6, 0)), (2.2, (30, 0, 0)), (2.6, (30, 0, 0)), (2.9, (72, -6, 0)), (3.2, (60, 0, 0)), (3.5, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (-6, 0, 0)), (2.2, (-24, 0, 0)), (2.6, (-24, 0, 0)), (2.9, (-6, 0, 0)), (3.5, (0, 0, 0))])
A.sample(a, 'jaw', 'rotation', lambda t: (14 * max(0.0, math.sin(t * 16)) if (0.6 < t < 1.9 or 2.2 < t < 2.6) else 0, 0, 0), 60)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.5, (4, 0, 0)), (3.2, (4, 0, 0)), (3.5, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.5, (0, 0, 10)), (2.2, (0, -20, -10)), (2.6, (0, -20, -10)), (3.5, (0, 0, 0))],
     [(0, (0, 0, 0)), (0.5, (0, 0, 10)), (2.2, (0, -20, -10)), (2.6, (0, -20, -10)), (3.5, (0, 0, 0))])
A.sine(a, 'tail', 'rotation', (0, 18, 0), period=0.5)

# alert (40 ticks): head high, ears forward, look around
a = T('alert', 2.0)
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.25, (-16, 0, 0)), (1.7, (-16, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (6, 30, 0)), (0.9, (6, 30, 0)), (1.1, (6, -30, 0)), (1.6, (6, -30, 0)), (2.0, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.25, (-30, -30, -25)), (1.7, (-30, -30, -25)), (2.0, (0, 0, 0))],
     [(0, (0, 0, 0)), (0.25, (-30, -30, -25)), (1.7, (-30, -30, -25)), (2.0, (0, 0, 0))])
keys(a, 'tail', 'rotation', [(0, (0, 0, 0)), (0.25, (-40, 0, 0)), (1.7, (-40, 0, 0)), (2.0, (0, 0, 0))])

# scratch (30 ticks): shake the head, ears flapping
a = T('scratch', 1.5)
A.sample(a, 'head', 'rotation', lambda t: (0, 0, 22 * math.sin(t * 24) * math.sin(math.pi * min(1.0, t / 1.2))), 40)
A.sample(a, 'ear_l', 'rotation', lambda t: (0, 0, 30 * math.sin(t * 24 + 1.5) * math.sin(math.pi * min(1.0, t / 1.2))), 40)
A.sample(a, 'ear_r', 'rotation', lambda t: (0, 0, 30 * math.sin(t * 24 + 1.5) * math.sin(math.pi * min(1.0, t / 1.2))), 40)
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.2, (10, 0, 0)), (1.3, (10, 0, 0)), (1.5, (0, 0, 0))])

a = T('flinch', 0.6)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-8, 0, 8)), (0.35, (-2, 0, -3)), (0.6, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -1.5, 1.5)), (0.6, (0, 0, 0))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.1, (-20, 18, 0)), (0.4, (6, -4, 0)), (0.6, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.1, (-30, 0, -30)), (0.6, (0, 0, 0))], [(0, (0, 0, 0)), (0.1, (-30, 0, -30)), (0.6, (0, 0, 0))])

a = T('death', 1.4)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (-10, 0, -6)), (0.75, (0, 0, 86)), (0.95, (0, 0, 80)), (1.4, (0, 0, 82))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, -1.5, 0)), (0.75, (2, 9, 0)), (1.4, (2, 9, 0))])
keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.8, (40, 0, 0)), (1.4, (46, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.8, (-10, 0, 0)), (1.4, (-14, 0, 0))])
for k, (u, lo) in LEGNAMES.items():
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.75, (-16 if k[0] == 'f' else 18, 0, 0)), (1.4, (-22 if k[0] == 'f' else 24, 0, 0))])
ears(a, [(0, (0, 0, 0)), (1.0, (0, 0, 30)), (1.4, (0, 0, 30))], [(0, (0, 0, 0)), (1.0, (0, 0, 30)), (1.4, (0, 0, 30))])

a = T('sleep', 3.4, True)
A.hold(a, 'body', 'position', (0, 9.5, 0))
A.sine(a, 'body', 'rotation', (0.8, 0, 0), offset=(2, 0, 0))
A.hold(a, 'neck', 'rotation', (36, -60, 0))
A.hold(a, 'head', 'rotation', (-30, -40, -10))
for k, (u, lo) in LEGNAMES.items():
    A.hold(a, u, 'rotation', (-85 if k[0] == 'f' else 80, 0, 0))
    A.hold(a, lo, 'rotation', (150 if k[0] == 'f' else -150, 0, 0))
ears(a, [(0, (0, 0, 30)), (3.4, (0, 0, 30))], [(0, (0, 0, 30)), (3.4, (0, 0, 30))])

a = T('stun', 1.2, True)
A.sine(a, 'body', 'rotation', (0, 0, 5), period=1.2)
A.hold(a, 'body', 'position', (0, 1, 0))
A.sample(a, 'neck', 'rotation', lambda t: (16 + 6 * cyc(t, 1.2), 14 * cyc(t, 1.2, 1.57), 0), 16)
A.sample(a, 'head', 'rotation', lambda t: (0, 0, 14 * cyc(t, 0.6)), 16)
ears(a, [(0, (0, 0, 35)), (1.2, (0, 0, 35))], [(0, (0, 0, 35)), (1.2, (0, 0, 35))])
for k, (u, lo) in LEGNAMES.items():
    A.sine(a, u, 'rotation', (4, 0, 3), period=1.2, phase=0.5 if k[1] == 'l' else 2.0)

# trapped (small monsters cannot be trapped by the core; kept so every required state pose exists): pit struggle
a = T('trapped', 0.7, True)
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'body', 'rotation', (3, 0, 5), period=0.35)
A.sample(a, 'neck', 'rotation', lambda t: (-16 + 10 * cyc(t, 0.35), 16 * cyc(t, 0.7), 0), 12)
A.sine(a, 'jaw', 'rotation', (8, 0, 0), period=0.35, offset=(8, 0, 0))
for k, (u, lo) in LEGNAMES.items():
    ph = {'fl': 0.0, 'fr': math.pi, 'bl': math.pi / 2, 'br': 3 * math.pi / 2}[k]
    A.sample(a, u, 'rotation', lambda t, ph=ph: (32 * cyc(t, 0.35, ph), 0, 0), 12)
A.sine(a, 'ear_l', 'rotation', (0, 0, 20), period=0.35)
A.sine(a, 'ear_r', 'rotation', (0, 0, -20), period=0.35)
A.sine(a, 'tail', 'rotation', (0, 30, 0), period=0.2)

if __name__ == '__main__':
    run(m)
