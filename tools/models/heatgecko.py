"""Heatgecko (ヒートゲッコー) - lava lizard with glowing magma spots and big gecko eyes. Small monster."""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, accent_pass, dim_under_glow  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
from hcmodel.paint import vnoise  # noqa: E402

M('hg_scale', ['#2a0806', '#4a100a', '#701c0e', '#962a14', '#b83c1c', '#d45428'], 'scales', scale=0.8, contrast=0.45, noise=0.035,
  edge=0.07, belly='hg_belly')
M('hg_limb', ['#2a0806', '#4a100a', '#701c0e', '#962a14', '#b83c1c', '#d45428'], 'scales', scale=0.7, contrast=0.4, noise=0.035, edge=0.07)
M('hg_crust', ['#0e0908', '#1a1210', '#281c18', '#382822', '#4a362e', '#5e463a'], 'rock', scale=1.7, contrast=0.7, noise=0.03,
  edge=0.0)
M('hg_belly', ['#5a3414', '#84501e', '#ac7030', '#cc9248', '#e6b466', '#fad690'], 'skin', contrast=0.3, noise=0.03, edge=0.04, base=0.6)
M('hg_dark', ['#0c0404', '#1a0806', '#2a0e0a', '#3c160e', '#501e14', '#66281a'], 'rock', scale=0.6, contrast=0.6, edge=0.0)
M('hg_lava', ['#7a1e00', '#b83600', '#ec5a04', '#ff8418', '#ffb040', '#ffe08a'], 'smooth', glow='#ff9a2a', noise=0.05, edge=0.0, base=0.62)
M('hg_eye', ['#5a3000', '#946000', '#cc9200', '#f2c000', '#ffe040', '#fff8a0'], 'smooth', glow='#ffd23a', edge=0.0, base=0.6)
M('hg_pad', ['#3a1a0a', '#5a2c12', '#7c401c', '#9c5628', '#bc6e36', '#d88a48'], 'skin', contrast=0.3, edge=0.0)

m = Model('heatgecko')
m.config.update({'head_bones': [['head', 0.8]], 'walk_speed': 4.0, 'run_speed': 0.6, 'walk_scale': 3.0})

# ------------------------------------------------------------------ body (low, sprawling)
body = m.bone('body', (0, 19, 0), (0, 0, 0))
torso = body.cube((-3.5, -2.5, -6), (7, 5, 12), 'hg_scale', faces={'top': 'hg_crust'}, decals=[{'type': 'belly_band'}])
for i, z in enumerate((-5, -2, 1, 4)):
    body.cube((-0.5, -3.5, z), (1, 1, 1), 'hg_dark')                                   # little obsidian bumps

# ------------------------------------------------------------------ head (wide, flat, big eyes)
head = body.bone('head', (0, -0.5, -6), (0, 0, 0))
skull = head.cube((-3.5, -3, -5), (7, 4, 5), 'hg_scale', faces={'top': 'hg_crust'})
snout = head.cube((-3, -2, -8), (6, 3, 3), 'hg_scale', faces={'top': 'hg_crust'})
eye = head.cube((-4.5, -4, -4), (2, 2, 2), 'hg_eye')
head.cube((2.5, -4, -4), (2, 2, 2), 'hg_eye', mirror_of=eye)
jaw = head.bone('jaw', (0, 1, -1), (0, 0, 0))
jaw.cube((-3, 0, -7), (6, 1, 7), 'hg_scale', faces={'top': 'mouth'})
tongue = jaw.bone('tongue', (0, -0.3, -2), (0, 0, 0))
tongue.cube((-0.5, -0.5, -3), (1, 1, 3), 'tongue', inflate=-0.1)
# slit pupils on the bulging eyes (front-facing and side faces) + a happy mouth line
for fc in ('north', 'east', 'west'):
    stamp(m, eye, fc, 0, 0, ['.K', '.K'] if fc != 'west' else ['K.', 'K.'], {'K': '#1a0a00'})

# ------------------------------------------------------------------ sprawling legs
LEGS = {}
for key, z in (('fl', -4), ('bl', 4)):
    up = body.bone('leg_%s' % key, (3, 0.5, z), (0, 0, 0))
    up.cube((0, -1, -1), (3, 2, 2), 'hg_limb')
    lo = up.bone('leg_%s_lo' % key, (2.5, 0, 0), (0, 0, 0))
    lo.cube((-1, -0.5, -1), (2, 4, 2), 'hg_limb')
    lo.cube((-1.5, 3.5, -2), (3, 1, 3), 'hg_pad')
    mirror_tree(body, up, '_' + key, '_' + key[0] + 'r')
LEGNAMES = {'fl': ('leg_fl', 'leg_fl_lo'), 'fr': ('leg_fr', 'leg_fr_lo'), 'bl': ('leg_bl', 'leg_bl_lo'), 'br': ('leg_br', 'leg_br_lo')}

# ------------------------------------------------------------------ tail (glowing tip)
TAIL = []
parent, piv = body, (0, -0.5, 5.5)
tail_cubes = []
for i, (w, h, ln, rx) in enumerate(((5, 4, 5, 4), (4, 3, 5, -2), (3, 2, 5, -2), (2, 2, 4, 0))):
    b = parent.bone('tail%d' % (i + 1), piv, (rx, 0, 0))
    tail_cubes.append(b.cube((-w / 2, -h / 2, 0), (w, h, ln + 1), 'hg_scale', faces={'top': 'hg_crust'}))
    TAIL.append(b.name)
    parent, piv = b, (0, 0, ln)
parent.cube((-1, -1, 3), (2, 2, 2), 'hg_lava')


# glowing magma spots on the back, head and tail
def magma(P, L, N, f, d, r, face):
    if face == 'bottom' or N[1] > 0.5:
        return None
    if face == 'top':
        return None
    n = vnoise(P[..., 0] * 0.7, P[..., 1] * 0.7, P[..., 2] * 0.7, 21)
    return (n > 0.68) & (d < 0.66)


# thin glowing fissures across the dark crust on top
def fissures(P, L, N, f, d, r, face):
    if face != 'top':
        return None
    n = vnoise(P[..., 0] * 0.32, P[..., 1] * 0.32, P[..., 2] * 0.32, 33)
    return np.abs(n - 0.5) < 0.045


accent_pass(m, [torso, skull] + tail_cubes, 'hg_lava', magma)
accent_pass(m, [torso, skull, snout] + tail_cubes, 'hg_lava', fissures, seed=11)
dim_under_glow(m, 0.4)
fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim
DIAG = {'fl': 0.0, 'br': 0.0, 'fr': math.pi, 'bl': math.pi}


def lizard_gait(a, period, amp, sway, steps=16):
    for k, (u, lo) in LEGNAMES.items():
        ph = DIAG[k]
        g = 1 if k[1] == 'l' else -1
        # legs swing forward/back around Y (sprawled) and lift (Z) during the swing
        A.sample(a, u, 'rotation', lambda t, ph=ph, g=g: (0, g * amp * cyc(t, period, ph), g * -18 * max(0.0, cyc(t, period, ph + math.pi / 2))), steps)
        A.sample(a, lo, 'rotation', lambda t, ph=ph, g=g: (0, 0, g * 14 * max(0.0, cyc(t, period, ph + math.pi / 2))), steps)
    A.sample(a, 'body', 'rotation', lambda t: (0, sway * cyc(t, period, 0.0), 0), steps)
    A.sample(a, 'head', 'rotation', lambda t: (0, -sway * 1.2 * cyc(t, period, 0.0), 0), steps)
    A.chain_wave(a, TAIL, (0, sway * 1.6, 0), period=period, phase_step=0.7, phase=math.pi, steps=steps)


a = T('idle', 2.0, True)
A.sine(a, 'body', 'position', (0, 0.2, 0))
A.sine(a, 'head', 'rotation', (-3, 4, 4), period=2.0, phase=0.5)
A.sine(a, 'jaw', 'scale', (0.0, 0.25, 0.0), period=0.5, offset=(1, 1, 1))                # throat pulse
A.chain_wave(a, TAIL, (0, 8, 0), phase_step=0.6, growth=1.15)

a = T('walk', 0.8, True)
lizard_gait(a, 0.8, 30, 8)

a = T('run', 0.45, True)
lizard_gait(a, 0.45, 40, 12, steps=12)
A.hold(a, 'head', 'position', (0, -0.5, 0))

# fire_spit (24 ticks): rear the head, spit at tick 10
a = T('fire_spit', 1.2)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.42, (-26, 0, 0)), (0.52, (10, 0, 0)), (0.7, (6, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.4, (14, 0, 0)), (0.5, (40, 0, 0)), (0.75, (30, 0, 0)), (1.0, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.42, (-10, 0, 0)), (0.52, (4, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.42, (0, -1, 1)), (0.52, (0, 0, -1)), (1.2, (0, 0, 0))])
A.chain_wave(a, TAIL, (-4, 6, 0), period=0.6, phase_step=0.5)

# lava_spit (32 ticks): puff up the throat and body, lob at tick 14
a = T('lava_spit', 1.6)
keys(a, 'body', 'scale', [(0, (1, 1, 1)), (0.6, (1.12, 1.15, 1.04)), (0.7, (0.96, 0.95, 1.0)), (1.0, (1, 1, 1))])
keys(a, 'jaw', 'scale', [(0, (1, 1, 1)), (0.6, (1.2, 2.2, 1.0)), (0.7, (1, 1, 1))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (-34, 0, 0)), (0.72, (-14, 0, 0)), (1.0, (-10, 0, 0)), (1.6, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (6, 0, 0)), (0.72, (48, 0, 0)), (1.05, (30, 0, 0)), (1.35, (0, 0, 0))])
for k, (u, lo) in LEGNAMES.items():
    g = 1 if k[1] == 'l' else -1
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.6, (0, 0, g * -14 if k[0] == 'f' else 0)), (0.8, (0, 0, 0)), (1.6, (0, 0, 0))])
A.chain_wave(a, TAIL, (-6, 4, 0), period=0.8, phase_step=0.5)

# tail_flick (22 ticks): pivot, whip the hot tail around behind at ticks 8-13
a = T('tail_flick', 1.1)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (0, 22, 0)), (0.5, (0, -10, 0)), (0.7, (0, -28, 0)), (1.1, (0, 0, 0))])
for i, tb in enumerate(TAIL):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (0.35, (0, 26 + 4 * i, 0)), (0.5, (0, -10, 0)), (0.65, (0, -34 - 6 * i, 0)), (0.8, (0, -20, 0)), (1.1, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (0, -20, 0)), (0.7, (0, 24, 0)), (1.1, (0, 0, 0))])

# lick (26 ticks): the gecko licks its own eye
a = T('lick', 1.3)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, -10, -14)), (1.0, (-8, -10, -14)), (1.3, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (22, 0, 0)), (1.0, (22, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'tongue', 'rotation', [(0, (0, 0, 0)), (0.35, (-70, 0, 0)), (0.5, (-80, 40, 0)), (0.65, (-70, 50, 0)), (0.8, (-80, 40, 0)), (1.0, (0, 0, 0))])
keys(a, 'tongue', 'scale', [(0, (1, 1, 1)), (0.35, (1, 1, 2.6)), (0.85, (1, 1, 2.6)), (1.0, (1, 1, 1))])
keys(a, 'tongue', 'position', [(0, (0, 0, 0)), (0.35, (0, -1, -2.5)), (0.9, (0, -1, -2.5)), (1.0, (0, 0, 0))])

# bask (60 ticks): flatten onto the warm rock, legs splayed, chin up
a = T('bask', 3.0)
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.5, (0, 1.6, 0)), (2.5, (0, 1.6, 0)), (3.0, (0, 0, 0))])
keys(a, 'body', 'scale', [(0, (1, 1, 1)), (0.5, (1.08, 0.9, 1)), (2.5, (1.08, 0.9, 1)), (3.0, (1, 1, 1))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (-16, 0, 0)), (2.5, (-16, 0, 0)), (3.0, (0, 0, 0))])
for k, (u, lo) in LEGNAMES.items():
    g = 1 if k[1] == 'l' else -1
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.5, (0, g * (20 if k[0] == 'f' else -20), g * 14)), (2.5, (0, g * (20 if k[0] == 'f' else -20), g * 14)), (3.0, (0, 0, 0))])
A.chain_wave(a, TAIL, (0, 4, 0), period=1.5, phase_step=0.6)

a = T('flinch', 0.6)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-10, 14, 6)), (0.35, (2, -4, -2)), (0.6, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -1.5, 1.5)), (0.6, (0, 0, 0))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.1, (30, 0, 0)), (0.45, (4, 0, 0)), (0.6, (0, 0, 0))])
A.chain_wave(a, TAIL, (6, 10, 0), period=0.3, phase_step=0.5)

# death: flips over, legs in the air
a = T('death', 1.4)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.2, (-14, 0, -20)), (0.7, (0, 0, 180)), (0.85, (0, 0, 172)), (1.0, (0, 0, 180)), (1.4, (0, 0, 180))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.2, (0, -3, 0)), (0.7, (0, -0.5, 0)), (1.4, (0, -0.5, 0))])
for k, (u, lo) in LEGNAMES.items():
    g = 1 if k[1] == 'l' else -1
    keys(a, u, 'rotation', [(0, (0, 0, 0)), (0.7, (0, 0, g * 20)), (1.4, (0, g * 10, g * 26))])
    keys(a, lo, 'rotation', [(0, (0, 0, 0)), (1.0, (0, 0, g * 30)), (1.4, (0, 0, g * 30))])
keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (34, 0, 0)), (1.4, (24, 0, 0))])
keys(a, 'tongue', 'position', [(0, (0, 0, 0)), (0.8, (0, 0, -2)), (1.4, (0, 0, -2))])
for tb in TAIL:
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (1.0, (0, 10, 0)), (1.4, (0, 12, 0))])

a = T('sleep', 3.0, True)
A.hold(a, 'body', 'position', (0, 1.8, 0))
A.sine(a, 'body', 'scale', (0.03, 0.04, 0), period=1.5, offset=(1, 1, 1))
A.hold(a, 'head', 'rotation', (10, -26, 0))
for i, tb in enumerate(TAIL):
    A.hold(a, tb, 'rotation', (0, -30, 0))
for k, (u, lo) in LEGNAMES.items():
    g = 1 if k[1] == 'l' else -1
    A.hold(a, u, 'rotation', (0, g * (30 if k[0] == 'f' else -30), g * -10))

a = T('stun', 1.0, True)
A.sine(a, 'body', 'rotation', (0, 0, 8), period=1.0)
A.sample(a, 'head', 'rotation', lambda t: (8 * cyc(t, 1.0), 14 * cyc(t, 1.0, 1.57), 14 * cyc(t, 0.5)), 16)
A.hold(a, 'jaw', 'rotation', (20, 0, 0))
A.hold(a, 'tongue', 'position', (0, 0.5, -1.5))
A.chain_wave(a, TAIL, (0, 10, 0), period=1.0, phase_step=0.6)

# trapped (small monsters cannot be trapped by the core; kept so every required state pose exists): frantic scrabbling
a = T('trapped', 0.6, True)
A.hold(a, 'body', 'position', (0, 1.5, 0))
A.sine(a, 'body', 'rotation', (0, 10, 4), period=0.3)
A.sample(a, 'head', 'rotation', lambda t: (-12 + 8 * cyc(t, 0.3), -12 * cyc(t, 0.3), 0), 10)
A.sine(a, 'jaw', 'rotation', (10, 0, 0), period=0.15, offset=(14, 0, 0))
for k, (u, lo) in LEGNAMES.items():
    g = 1 if k[1] == 'l' else -1
    A.sample(a, u, 'rotation', lambda t, g=g, ph=DIAG[k]: (0, g * 40 * cyc(t, 0.3, ph), g * -16 * max(0.0, cyc(t, 0.3, ph + math.pi / 2))), 10)
A.chain_wave(a, TAIL, (0, 24, 0), period=0.3, phase_step=0.6)

if __name__ == '__main__':
    run(m)
