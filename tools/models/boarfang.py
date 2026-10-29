"""Boarfang (ボアファング) - stubborn tusked boar with a bristly mane. Small monster."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, stamp_eyes, accent_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402

M('bf_fur', ['#24170e', '#3d2818', '#5a3b23', '#7a5232', '#9a6c44', '#b98a5c'], 'fur', contrast=0.55, noise=0.035, edge=0.06,
  belly='bf_belly')
M('bf_leg', ['#24170e', '#3d2818', '#5a3b23', '#7a5232', '#9a6c44', '#b98a5c'], 'fur', contrast=0.5, noise=0.035, edge=0.06)
M('bf_belly', ['#4a3424', '#6b4e38', '#8c6a50', '#ab8a6c', '#c8a98a', '#e0c6a8'], 'fur', contrast=0.6, noise=0.04, edge=0.04, base=0.6)
M('bf_mane', ['#0c0806', '#1a120c', '#2a1d14', '#3c2b1e', '#503a2a', '#664b37'], 'fur', contrast=0.9, noise=0.05, edge=0.0)
M('bf_snout', ['#3a2424', '#5a3838', '#7a5050', '#996a68', '#b88884', '#d4a8a2'], 'skin', contrast=0.5, noise=0.03, edge=0.06)
M('bf_hoof', ['#0e0b09', '#1c1714', '#2c2520', '#3e352e', '#524840', '#6a5e54'], 'horn', edge=0.0)
M('bf_tusk', ['#6a5c48', '#958368', '#bba98a', '#d8caa8', '#eee4c8', '#fffaec'], 'horn', edge=0.04, base=0.6)

m = Model('boarfang')
m.config.update({'head_bones': [['head', 0.8]], 'walk_speed': 3.0, 'run_speed': 0.6, 'walk_scale': 2.8})

# ------------------------------------------------------------------ body
body = m.bone('body', (0, 11, 1), (0, 0, 0))
front = body.cube((-5, -6, -9), (10, 11, 9), 'bf_fur', faces={'top': 'bf_mane'}, decals=[{'type': 'belly_band'}])   # heavy shoulders
rear = body.cube((-4.5, -4.5, -1), (9, 9, 9), 'bf_fur', decals=[{'type': 'belly_band'}])
body.cube((-3.5, -8, -8), (7, 2, 7), 'bf_fur', faces={'top': 'bf_mane'})                    # hump
for i, (z, h) in enumerate(((-8, 3), (-5.5, 4), (-3, 3), (-0.5, 3), (2, 2), (4.5, 2))):
    body.cube((-1, -6 - (2 if i < 4 else 0) - h + (0 if i < 4 else 1.5), z), (2, h, 2), 'bf_mane')   # bristly mane

# ------------------------------------------------------------------ head
head = body.bone('head', (0, -1, -9), (18, 0, 0))
skull = head.cube((-4, -4.5, -7), (8, 8, 7), 'bf_fur', faces={'top': 'bf_mane'})
snout = head.cube((-3, -1.5, -11), (6, 5, 5), 'bf_fur')
nose = head.cube((-2, -0.5, -12), (4, 3, 1), 'bf_snout')
head.cube((-4.5, 1, -6), (9, 3, 5), 'bf_fur')                                             # cheeks / jowls
jaw = head.bone('jaw', (0, 3.5, -4), (0, 0, 0))
jaw.cube((-2.5, 0, -7), (5, 2, 7), 'bf_fur', faces={'top': 'mouth'})
stamp_eyes(m, skull, ['KR', 'KK'], {'K': '#120a06', 'R': '#d8a040'}, front=1, top=2)
stamp(m, nose, 'north', 0, 1, ['K..K'], {'K': '#1a0e0e'})
# tusks: base pointing forward/out, tip curling up
tusk = head.bone('tusk_l', (2.6, 2.5, -9.5), (-38, -30, 0))
tusk.cube((-1, -1, -4), (2, 2, 5), 'bf_tusk')
tip = tusk.bone('tusk_tip_l', (0, 0, -3.5), (-55, 10, 0))
tip.cube((-1, -1, -4), (2, 2, 5), 'bf_tusk', inflate=-0.2, decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.3, 'mat': 'horn_ivory'}])
tip2 = tip.bone('tusk_end_l', (0, 0, -4), (-45, 0, 0))
tip2.cube((-1, -1, -3), (2, 2, 3), 'horn_ivory', inflate=-0.45)
mirror_tree(head, tusk)
ear = head.bone('ear_l', (3, -4, -2.5), (-15, 0, 40))
ear.cube((-1, -3, -0.5), (2, 3, 1), 'bf_fur', faces={'north': 'bf_snout'})
ear.cube((-0.5, -4, -0.5), (1, 1, 1), 'bf_mane')
mirror_tree(head, ear)

# ------------------------------------------------------------------ legs
LEGS = {}
for key, x, z in (('fl', 3.4, -5.5), ('bl', 3.4, 5.5)):
    up = body.bone('leg_%s' % key, (x, 3.5, z), (0, 0, 0))
    up.cube((-1.5, -1, -1.5), (3, 5, 3), 'bf_leg')
    lo = up.bone('leg_%s_lo' % key, (0, 4, 0), (0, 0, 0))
    lo.cube((-1, -0.5, -1), (2, 4, 2), 'bf_leg')
    lo.cube((-1.5, 3, -1.5), (3, 1, 3), 'bf_hoof')
    mirror_tree(body, up, '_' + key, '_' + key[0] + 'r')
LEGNAMES = {'fl': ('leg_fl', 'leg_fl_lo'), 'fr': ('leg_fr', 'leg_fr_lo'), 'bl': ('leg_bl', 'leg_bl_lo'), 'br': ('leg_br', 'leg_br_lo')}

# ------------------------------------------------------------------ tail
tail = body.bone('tail', (0, -3, 8), (35, 0, 0))
tail.cube((-0.5, -0.5, 0), (1, 1, 4), 'bf_fur')
tuft = tail.bone('tail_tuft', (0, 0, 3.5), (20, 0, 0))
tuft.cube((-1, -1, 0), (2, 2, 3), 'bf_mane')

fit_to_ground(m, body)


# ------------------------------------------------------------------ animations
def gait(a, period, amp, knee, phases, steps=16):
    for k, (u, lo) in LEGNAMES.items():
        ph = phases[k]
        A.sample(a, u, 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph), 0, 0), steps)
        A.sample(a, lo, 'rotation', lambda t, ph=ph, k=k: (-knee * max(0.0, cyc(t, period, ph + 1.6)) if k[0] == 'f' else knee * max(0.0, cyc(t, period, ph - 1.6)), 0, 0), steps)


WALK = {'fl': 0.0, 'br': math.pi * 0.5, 'fr': math.pi, 'bl': math.pi * 1.5}
GALLOP = {'fl': 0.0, 'fr': 0.5, 'bl': math.pi, 'br': math.pi + 0.5}
T = m.anim

a = T('idle', 2.0, True)
A.sine(a, 'body', 'position', (0, 0.3, 0))
A.sine(a, 'body', 'rotation', (0.6, 0, 0), phase=0.5)
A.sine(a, 'head', 'rotation', (2.5, 3, 0), period=1.0, phase=0.3)
A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0), period=0.5)
keys(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (1.2, (0, 0, 0)), (1.3, (0, 0, 18)), (1.45, (0, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (0.4, (0, 0, 0)), (0.5, (0, 0, -18)), (0.65, (0, 0, 0)), (2.0, (0, 0, 0))])
A.sine(a, 'tail', 'rotation', (0, 25, 0), period=0.5)

a = T('walk', 0.9, True)
gait(a, 0.9, 26, 34, WALK)
A.sample(a, 'body', 'position', lambda t: (0, -0.5 * abs(cyc(t, 0.9)), 0), 16)
A.sine(a, 'body', 'rotation', (1.0, 2, 2), period=0.9)
A.sample(a, 'head', 'rotation', lambda t: (3 * cyc(t, 0.45, 0.8), 0, 0), 16)
A.sine(a, 'tail', 'rotation', (0, 20, 0), period=0.45)

a = T('run', 0.5, True)
gait(a, 0.5, 42, 50, GALLOP, steps=12)
A.sample(a, 'body', 'position', lambda t: (0, -1.4 * max(0.0, cyc(t, 0.5, 0.6)), 0), 12)
A.sample(a, 'body', 'rotation', lambda t: (4 * cyc(t, 0.5, 1.2), 0, 0), 12)
A.hold(a, 'head', 'rotation', (8, 0, 0))
A.hold(a, 'tail', 'rotation', (-30, 0, 0))
A.hold(a, 'ear_l', 'rotation', (-30, 0, 0))
A.hold(a, 'ear_r', 'rotation', (-30, 0, 0))

# charge (56 ticks): paw the ground, gallop with the head low, skid to a stop
a = T('charge', 2.8)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (20, 0, 0)), (0.8, (22, 0, 0)), (0.85, (26, 0, 0)), (2.1, (24, 0, 0)), (2.4, (6, 0, 0)), (2.8, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (6, 0, 0)), (0.8, (6, 0, 0)), (0.85, (8, 0, 0)), (2.1, (8, 0, 0)), (2.25, (-10, 0, 0)), (2.5, (-6, 0, 0)), (2.8, (0, 0, 0))])
A.sample(a, 'body', 'position', lambda t: (0, (0.8 if t < 0.8 else 0) + (-1.4 * max(0.0, cyc(t, 0.4, 0.6)) if 0.85 < t < 2.1 else 0), 0), 56)
# paw: right front leg scrapes backwards
A.sample(a, 'leg_fr', 'rotation', lambda t: ((-28 + 50 * max(0.0, cyc(t, 0.27))) if t < 0.8 else 0, 0, 0), 30, 0.0, 0.8)
A.sample(a, 'leg_fr_lo', 'rotation', lambda t: ((-30 * max(0.0, cyc(t, 0.27, -1.5))) if t < 0.8 else 0, 0, 0), 30, 0.0, 0.8)
for k, (u, lo) in LEGNAMES.items():
    ph = GALLOP[k]
    if k != 'fr':
        keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.8, (0, 0, 0))])
        keys(a, lo, 'rotation', [(0, (0, 0, 0)), (0.8, (0, 0, 0))])
    A.sample(a, u, 'rotation', lambda t, ph=ph: (46 * cyc(t, 0.4, ph), 0, 0), 30, 0.85, 2.1)
    A.sample(a, lo, 'rotation', lambda t, ph=ph, k=k: ((-52 if k[0] == 'f' else 52) * max(0.0, cyc(t, 0.4, ph + (1.6 if k[0] == 'f' else -1.6))), 0, 0), 30, 0.85, 2.1)
    keys(a, u, 'rotation', [(2.25, (-30 if k[0] == 'f' else -20, 0, 0)), (2.55, (-24 if k[0] == 'f' else -14, 0, 0)), (2.8, (0, 0, 0))])
    keys(a, lo, 'rotation', [(2.25, (10, 0, 0)), (2.8, (0, 0, 0))])
keys(a, 'tail', 'rotation', [(0, (0, 0, 0)), (0.3, (-40, 0, 0)), (2.3, (-40, 0, 0)), (2.8, (0, 0, 0))])
keys(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (2.3, (-30, 0, 0)), (2.8, (0, 0, 0))])
keys(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (2.3, (-30, 0, 0)), (2.8, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.1, (18, 0, 0)), (0.3, (6, 0, 0)), (2.8, (0, 0, 0))])

# tusk_toss (24 ticks): head down, then a violent upward swing at tick 10
a = T('tusk_toss', 1.2)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (30, 8, 0)), (0.5, (-40, -6, 0)), (0.65, (-34, -4, 0)), (1.2, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (8, 0, 0)), (0.5, (-12, 0, 0)), (0.7, (-8, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.35, (0, 1, 1)), (0.5, (0, -1.5, -2)), (1.2, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.45, (22, 0, 0)), (0.8, (6, 0, 0)), (1.2, (0, 0, 0))])
for k in ('fl', 'fr'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.35, (12, 0, 0)), (0.5, (-22, 0, 0)), (0.8, (-8, 0, 0)), (1.2, (0, 0, 0))])
for k in ('bl', 'br'):
    keys(a, LEGNAMES[k][0], 'rotation', [(0, (0, 0, 0)), (0.35, (-10, 0, 0)), (0.5, (14, 0, 0)), (1.2, (0, 0, 0))])

# root (idle action): snuffle in the dirt
a = T('root', 2.5)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (40, 0, 0)), (2.1, (40, 0, 0)), (2.5, (0, 0, 0))])
A.sample(a, 'head', 'position', lambda t: (0, 0, (-1.2 * max(0.0, math.sin(t * 9))) if 0.5 < t < 2.0 else 0), 40)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (8, 0, 0)), (2.1, (8, 0, 0)), (2.5, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.4, (0, 1, 0)), (2.1, (0, 1, 0)), (2.5, (0, 0, 0))])
A.sample(a, 'jaw', 'rotation', lambda t: (10 * max(0.0, math.sin(t * 14)) if 0.5 < t < 2.0 else 0, 0, 0), 40)
A.sine(a, 'tail', 'rotation', (0, 30, 0), period=0.4)

# shake: shake off dust like a dog
a = T('shake', 1.2)
A.sample(a, 'body', 'rotation', lambda t: (0, 0, 14 * math.sin(t * 26) * math.sin(math.pi * t / 1.2)), 36)
A.sample(a, 'head', 'rotation', lambda t: (0, 18 * math.sin(t * 26 + 1.0) * math.sin(math.pi * t / 1.2), 0), 36)
A.sample(a, 'ear_l', 'rotation', lambda t: (0, 0, 25 * math.sin(t * 26 + 1.8) * math.sin(math.pi * t / 1.2)), 36)
A.sample(a, 'ear_r', 'rotation', lambda t: (0, 0, 25 * math.sin(t * 26 + 1.8) * math.sin(math.pi * t / 1.2)), 36)

a = T('flinch', 0.7)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-6, 0, 8)), (0.35, (-2, 0, -4)), (0.7, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -1, 2)), (0.7, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.1, (-22, 14, 0)), (0.4, (6, -6, 0)), (0.7, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.12, (26, 0, 0)), (0.5, (4, 0, 0)), (0.7, (0, 0, 0))])

a = T('death', 1.5)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (-8, 0, -6)), (0.8, (0, 0, 88)), (1.0, (0, 0, 82)), (1.5, (0, 0, 84))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, -1, 0)), (0.8, (2, 3.5, 0)), (1.5, (2, 3.5, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.9, (10, 0, 0)), (1.5, (14, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (24, 0, 0)), (1.5, (16, 0, 0))])
for k, (u, lo) in LEGNAMES.items():
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.8, (-14 if k[0] == 'f' else 14, 0, 0)), (1.5, (-20 if k[0] == 'f' else 20, 0, 0))])
keys(a, 'tail', 'rotation', [(0, (0, 0, 0)), (1.0, (-30, 0, 0)), (1.5, (-30, 0, 0))])

a = T('sleep', 3.0, True)
A.hold(a, 'body', 'position', (0, 6.5, 0))
A.sine(a, 'body', 'rotation', (0, 0, 1.0), offset=(0, 0, 4))
A.hold(a, 'head', 'rotation', (22, 0, 0))
A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0))
for k, (u, lo) in LEGNAMES.items():
    A.hold(a, u, 'rotation', (-80 if k[0] == 'f' else 80, 0, 0))
    A.hold(a, lo, 'rotation', (60 if k[0] == 'f' else -70, 0, 0))
A.hold(a, 'ear_l', 'rotation', (0, 0, 30))
A.hold(a, 'ear_r', 'rotation', (0, 0, -30))
A.hold(a, 'tail', 'rotation', (-30, 0, 0))

a = T('stun', 1.2, True)
A.sine(a, 'body', 'rotation', (0, 0, 6), period=1.2)
A.hold(a, 'body', 'position', (0, 1, 0))
A.sample(a, 'head', 'rotation', lambda t: (14 + 6 * cyc(t, 1.2), 12 * cyc(t, 1.2, 1.57), 14 * cyc(t, 0.6)), 16)
A.hold(a, 'jaw', 'rotation', (20, 0, 0))
A.sine(a, 'ear_l', 'rotation', (0, 0, 15), period=0.6)
A.sine(a, 'ear_r', 'rotation', (0, 0, -15), period=0.6)

if __name__ == '__main__':
    run(m)
