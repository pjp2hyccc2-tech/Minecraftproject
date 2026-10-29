"""Gravadon (岩槌竜グラヴァドン) - rock-hammer brute wyvern, water/earth. Large monster (module large.b).

Bipedal T-rex-like brute: massive rock hammer crest on the skull, boar-like lower tusks, tiny arms,
pillar legs, mossy boulder plates on the back and a heavy rock club on the tail. Teal earth-gem
nodules glow in the crest, back rocks and club. Throat sac inflates before water jets.
Run from tools/:  python3 models/gravadon.py --preview [anim ...]
"""
import math
import os
import sys

if os.environ.get('PYTHONHASHSEED') != '0':  # deterministic texture noise (cube seeds use hash())
    os.environ['PYTHONHASHSEED'] = '0'
    os.execv(sys.executable, [sys.executable] + sys.argv)

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel.paint import M  # noqa: E402
from hcmodel.parts import mirror_tree, head_eyes, tail_chain  # noqa: E402

# ------------------------------------------------------------------ materials (module-local)
M('grv_skin', ['#211a14', '#3a2f25', '#584839', '#77634f', '#978068', '#b59e84'], 'rock', scale=1.0, contrast=0.75, noise=0.04, edge=0.07,
  belly='grv_belly', accent='grv_skin_dark', accent_mode='spots', accent_freq=0.16, accent_thresh=0.66)
M('grv_skin_dark', ['#140f0c', '#271e17', '#3d3025', '#544233', '#6c5643', '#876e57'], 'rock', scale=1.0, contrast=0.75, noise=0.04, edge=0.07)
M('grv_belly', ['#33281e', '#524131', '#725c46', '#92785c', '#b09576', '#cbb091'], 'plates', scale=0.7)
M('grv_rock', ['#1e1a17', '#36302a', '#524840', '#71655a', '#938677', '#b5a898'], 'rock')
M('grv_crest', ['#131417', '#25272b', '#3a3d42', '#53575d', '#70757b', '#92979c'], 'rock', scale=1.5)
M('grv_moss', ['#14200f', '#22361a', '#355228', '#4d703a', '#6b8f50', '#8fb06c'], 'fur', edge=0.0)
M('grv_gem', ['#06302e', '#0b5550', '#138078', '#27aba0', '#5fd9cc', '#b5fff5'], 'crystal', glow='#62f0dc')
M('grv_throat', ['#2e2a27', '#4a443f', '#686059', '#877e75', '#a69c92', '#c4bab0'], 'skin', belly=None)
M('grv_claw', ['#0e0c0a', '#1d1915', '#322b25', '#4c433a', '#6b6054', '#8e8274'], 'horn')

m = Model('gravadon')
m.config.update({'head_bones': [['neck', 0.4], ['head', 0.6]], 'walk_speed': 2.2, 'run_speed': 0.5, 'walk_scale': 2.8})
AIR_ANIMS = ()

# ------------------------------------------------------------------ body
body = m.bone('body', (0, -19, 6), (-4, 0, 0))
body.cube((-18, -14, -22), (36, 28, 34), 'grv_skin', faces={'top': 'grv_skin_dark'}, decals=[{'type': 'belly_band'}])
body.cube((-16, -11, -31), (32, 25, 10), 'grv_skin', faces={'top': 'grv_skin_dark'}, decals=[{'type': 'belly_band'}])   # chest
body.cube((-15, -12, 11), (30, 23, 11), 'grv_skin', faces={'top': 'grv_skin_dark'}, decals=[{'type': 'belly_band'}])    # hips
body.cube((-15, 10, -28), (30, 6, 34), 'grv_belly')                     # belly plates
body.cube((-19.5, -6, -17), (39, 16, 24), 'grv_skin', decals=[{'type': 'belly_band'}])   # flank bulge (rounds the box)
body.cube((-13, -16, -30), (26, 4, 30), 'grv_skin_dark')                # dorsal ridge
sh_l = body.bone('shoulder_l', (17, -8, -20), (0, 0, -18))
sh_l.cube((-3, -6, -9), (8, 15, 18), 'grv_rock', faces={'top': 'grv_moss'})
mirror_tree(body, sh_l)
# small fixed rock stubs on the spine (remain after the back rocks break)
for z in (-26, -15, -4, 7):
    body.cube((-4, -17, z), (8, 4, 8), 'grv_rock', faces={'top': 'grv_moss'})


def rock(parent, name, pos, rot, size, gem=None, drape=False):
    """Boulder plate with a mossy top; optional glowing gem nodule and hanging moss."""
    b = parent.bone(name, pos, rot)
    w, h, d = size
    b.cube((-w / 2, -h, -d / 2), size, 'grv_rock', faces={'top': 'grv_moss'})
    b.cube((-w / 2 + 1, -h - 1.5, -d / 2 + 1), (w - 2, 2, d - 2), 'grv_moss')
    if gem:
        gx, gy, gz, gs = gem
        b.cube((gx, gy, gz), (gs, gs, gs), 'grv_gem')
    if drape:
        b.cube((-w / 2, -h + 1, -d / 2 - 0.5), (w, h * 0.8, 0), 'grv_moss', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.7}])
    return b


back = body.bone('back_rocks', (0, -14, 0))
rock(back, 'brock_l1', (8, 2, -20), (-6, 0, 18), (13, 9, 12), gem=(1, -6, -6.5, 3), drape=True)
rock(back, 'brock_l2', (9, 2, -7), (-3, 0, 20), (12, 9, 12), drape=True)
rock(back, 'brock_l3', (8, 2, 5), (5, 0, 20), (11, 8, 11), gem=(-2, -5, 5, 3))
rock(back, 'brock_l4', (6, 3, 16), (10, 0, 16), (9, 6, 9))
rock(back, 'brock_c1', (0, 1, -14), (-8, 0, 0), (9, 11, 10))
rock(back, 'brock_c2', (0, 1, 0), (0, 0, 0), (8, 10, 9), gem=(-1.5, -8, -5, 3))
rock(back, 'brock_c3', (0, 2, 12), (8, 0, 0), (7, 8, 8))
for bn in ('brock_l1', 'brock_l2', 'brock_l3', 'brock_l4'):
    mirror_tree(back, m.bones[bn])

# ------------------------------------------------------------------ neck & head
neck = body.bone('neck', (0, -2, -29), (-8, 0, 0))
neck.cube((-12, -11, -14), (24, 22, 17), 'grv_skin', faces={'top': 'grv_skin_dark'})
neck.cube((-6, -14, -12), (12, 4, 12), 'grv_rock', faces={'top': 'grv_moss'})
throat = neck.bone('throat', (0, 8, -7))
throat.cube((-8, -3, -6), (16, 7, 12), 'grv_throat')

head = neck.bone('head', (0, -1, -12), (12, 0, 0))
skull = head.cube((-11, -10, -20), (22, 16, 21), 'grv_skin', faces={'top': 'grv_skin_dark'})
head_eyes(skull, '#ffc04a', '#ffa020', at=(0.6, 0.5), size=4)
head.cube((-9, -5, -31), (18, 11, 12), 'grv_skin', faces={'top': 'grv_skin_dark'}, decals=[{'type': 'nostrils', 'at': 0.35}])
head.cube((-7.5, 6, -30), (15, 1.5, 10), 'teeth', decals=[{'type': 'teeth', 'face': 'west'}, {'type': 'teeth', 'face': 'east'}, {'type': 'teeth', 'face': 'north'}])
head.cube((-12, -11, -10), (24, 4, 8), 'grv_rock')                    # rear brow ridge
head.cube((10, -2, -16), (2, 5, 7), 'grv_rock')                       # cheek warts
head.cube((10.5, 2, -9), (2, 4, 5), 'grv_rock')
head.cube((-12, -2, -16), (2, 5, 7), 'grv_rock')
head.cube((-12.5, 2, -9), (2, 4, 5), 'grv_rock')
head.cube((-4, -7, -33), (8, 4, 5), 'grv_rock')                       # nasal boss
# hammer crest (mallet laid across the front of the skull): core stays, ends + striking face break off
crest = head.bone('crest', (0, -10, -14))
crest.cube((-13, -13, -14), (26, 15, 24), 'grv_crest')
crest.cube((-10, -15, -10), (20, 3, 16), 'grv_moss')
crest.cube((-3, -16, -4), (6, 3, 6), 'grv_gem')
crest_l = crest.bone('crest_l', (13, -6, -2))
crest_l.cube((0, -9, -13), (9, 18, 26), 'grv_crest')
crest_l.cube((9, -6.5, -10), (4, 13, 20), 'grv_crest')
crest_l.cube((2, -11, -9), (6, 3, 17), 'grv_moss')
crest_l.cube((10, -2, -3), (3, 4, 5), 'grv_gem')
crest_l.cube((3, 8.5, -11), (5, 3, 6), 'grv_crest')
mirror_tree(crest, crest_l)
crest_f = crest.bone('crest_front', (0, -1, -14))
crest_f.cube((-12, -10, -6), (24, 18, 6), 'grv_crest')
crest_f.cube((-9, -7, -8), (18, 12, 2), 'grv_crest')
crest_f.cube((-7, -12, -4), (14, 3, 5), 'grv_moss')

jaw = head.bone('jaw', (0, 6, -8))
jaw.cube((-10, 0, -24), (20, 7, 25), 'grv_skin', faces={'top': 'mouth'})
jaw.cube((-8.5, -1, -23), (17, 1, 18), 'teeth')
jaw.cube((-4, 0.5, -18), (8, 1, 12), 'tongue')
jaw.cube((-7, 6, -23), (14, 4, 14), 'grv_rock')                      # chin plate
tusk_l = jaw.bone('tusk_l', (8.5, 1, -21), (-22, 0, 14))
tusk_l.cube((-1.5, -10, -1.5), (3, 11, 3), 'horn_ivory', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.3, 'mat': 'bone'}])
tusk_l2 = tusk_l.bone('tusk_tip_l', (0, -9, 0), (-25, 0, 0))
tusk_l2.cube((-1, -6, -1), (2, 6, 2), 'horn_ivory')
mirror_tree(jaw, tusk_l)

# ------------------------------------------------------------------ tiny arms
arm_l = body.bone('arm_l', (12, 8, -28), (24, 0, -16))
arm_l.cube((-3, -2, -3), (6, 11, 6), 'grv_skin')
fore_l = arm_l.bone('forearm_l', (0, 9, 0), (-78, 0, 0))
fore_l.cube((-2.5, -1, -2.5), (5, 9, 5), 'grv_skin')
spur_l = fore_l.bone('arm_spur_l', (0, 1, 2))
spur_l.cube((-2, -1, 0), (4, 6, 4), 'grv_rock')
hand_l = fore_l.bone('hand_l', (0, 8, 0), (25, 0, 0))
hand_l.cube((-2.5, -1, -2), (5, 3, 4), 'grv_skin')
claws_l = hand_l.bone('arm_claws_l', (0, 1, 0))
claws_l.cube((-2.5, 1, -2), (2, 4, 2), 'grv_claw', decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.5, 'mat': 'horn_ivory'}])
claws_l.cube((0.5, 1, -2), (2, 4, 2), 'grv_claw', decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.5, 'mat': 'horn_ivory'}])
mirror_tree(body, arm_l)

# ------------------------------------------------------------------ legs (pillar-like, slightly digitigrade)
thigh_l = body.bone('thigh_l', (17, 0, 5), (-14, 0, 0))
thigh_l.cube((-9, -7, -12), (18, 29, 22), 'grv_skin', faces={'top': 'grv_skin_dark'})
thigh_l.cube((8, -3, -8), (3, 14, 14), 'grv_rock', faces={'top': 'grv_moss'})        # hip plate
shin_l = thigh_l.bone('shin_l', (0, 19, -1), (36, 0, 0))
shin_l.cube((-6.5, -2, -6.5), (13, 23, 13), 'grv_skin', decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.3, 'mat': 'mud'}])
shin_l.cube((-4, 3, -8), (8, 10, 2), 'grv_rock')                                    # shin plate
foot_l = shin_l.bone('foot_l', (0, 20, 1), (-22, 0, 0))
foot_l.cube((-9, -2, -13), (18, 7, 19), 'grv_skin', decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.45, 'mat': 'mud'}])
for k, x in enumerate((-8, -1.75, 4.5)):
    toe = foot_l.bone('toe%d_l' % k, (x + 1.75, 1, -13), (12, (k - 1) * -10, 0))
    toe.cube((-1.75, -1, -5), (3.5, 4, 5), 'grv_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.3, 'mat': 'bone'}])
foot_l.cube((-2, 1, 4), (4, 3, 4), 'grv_claw')
mirror_tree(body, thigh_l)

# ------------------------------------------------------------------ tail + club
TAIL = tail_chain(body, 'tail', (0, -4, 21), (8, 0, 0),
                  [(26, 22, 11, -2), (22, 19, 10, -5), (18, 16, 9, -5), (15, 13, 8, -4)], 'grv_skin')
for i, tb in enumerate(TAIL):
    w = (26, 22, 18, 15)[i]
    hh = (22, 19, 16, 13)[i]
    tb.cube((-w / 2 + 3, -hh / 2 - 3, 2), (w - 6, 4, 8 - i), 'grv_rock', faces={'top': 'grv_moss'})
club = TAIL[-1].bone('tail_club', (0, 0, 8), (4, 0, 0))
club.cube((-10, -9, 0), (20, 18, 19), 'grv_crest')                    # core boulder
club.cube((-8, -12, 4), (16, 4, 12), 'grv_moss')
club.cube((-6, -7, 18), (12, 13, 4), 'grv_crest')                     # rear cap
club.cube((-3, -11.5, 9), (5, 3, 4), 'grv_gem')
for side in (1, -1):
    kb = club.bone('club_knob_' + ('l' if side > 0 else 'r'), (side * 9, 0, 9), (0, side * 12, side * 10))
    kb.cube((-7, -7, -8), (14, 14, 16), 'grv_crest')                  # lateral mace knob
    kb.cube((-5 + side * 4, -9, -5), (10, 3, 10), 'grv_moss')
    kb.cube((side * 6 - 1.5, -2, -2), (3, 4, 4), 'grv_gem')
    sp = kb.bone('club_spike_' + ('l' if side > 0 else 'r'), (side * 7, 0, 0), (0, 0, side * -90))
    sp.cube((-2.5, -6, -2.5), (5, 6, 5), 'grv_claw', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.45, 'mat': 'bone'}])
TAILN = ['tail1', 'tail2', 'tail3', 'tail4']

# ------------------------------------------------------------------ animation helpers
T = m.anim
TAIL = TAILN


def P(anim, bone, chan, *keys):
    """Explicit keyframes (t, (x, y, z)) - catmull-rom smoothed."""
    A.poses(anim, bone, chan, list(keys))


def lerp_keys(keys, u):
    """Cosine interpolation through (u, values) keys, u in [0, 1]."""
    for i in range(len(keys) - 1):
        u0, v0 = keys[i]
        u1, v1 = keys[i + 1]
        if u0 <= u <= u1:
            f = (u - u0) / (u1 - u0) if u1 > u0 else 0.0
            f = 0.5 - 0.5 * math.cos(math.pi * f)
            return tuple(a + (b - a) * f for a, b in zip(v0, v1))
    return keys[-1][1]


# one leg's (thigh, shin, foot) x-rotation through a stride; u = 0 heel strike, 0.5 push-off
WALK_KEYS = [(0.0, (-20, 0, 20)), (0.25, (-6, 4, 2)), (0.5, (16, 10, -12)), (0.66, (4, 34, -26)), (0.85, (-16, 14, -2)), (1.0, (-20, 0, 20))]
RUN_KEYS = [(0.0, (-28, 4, 24)), (0.22, (-6, 8, -2)), (0.45, (24, 16, -18)), (0.62, (8, 50, -34)), (0.84, (-24, 22, -4)), (1.0, (-28, 4, 24))]


def gait(anim, period, keys, t0=0.0, t1=None, steps_per_cycle=16, amp=1.0):
    t1 = anim.length if t1 is None else t1
    steps = max(8, int(round((t1 - t0) / period * steps_per_cycle)))
    for side, ph in (('l', 0.0), ('r', 0.5)):
        for j, bn in enumerate(('thigh', 'shin', 'foot')):
            A.sample(anim, '%s_%s' % (bn, side), 'rotation',
                     lambda t, ph=ph, j=j: (amp * lerp_keys(keys, ((t - t0) / period + ph) % 1.0)[j], 0, 0),
                     steps=steps, t0=t0, t1=t1)


def legs_pose(anim, keys_l, keys_r=None):
    """keys: list of (t, (thigh, shin, foot)) x-rotations."""
    for side, ks in (('l', keys_l), ('r', keys_r or keys_l)):
        for j, bn in enumerate(('thigh', 'shin', 'foot')):
            P(anim, '%s_%s' % (bn, side), 'rotation', *[(t, (v[j], 0, 0)) for t, v in ks])


def arms(anim, keys):
    """Tiny arms: keys (t, (shoulder_x, shoulder_z_out, forearm_x))."""
    for side, g in (('l', 1), ('r', -1)):
        P(anim, 'arm_' + side, 'rotation', *[(t, (v[0], 0, -g * v[1])) for t, v in keys])
        P(anim, 'forearm_' + side, 'rotation', *[(t, (v[2], 0, 0)) for t, v in keys])


def tail_keys(anim, keys, growth=1.0):
    """Same (t, (x, y, z)) offsets on every tail bone, scaled by growth^i (whips toward the tip)."""
    for i, b in enumerate(TAIL):
        g = growth ** i
        P(anim, b, 'rotation', *[(t, tuple(c * g for c in v)) for t, v in keys])


# ------------------------------------------------------------------ loops
a = T('idle', 3.2, True)
A.sine(a, 'body', 'position', (0, 0.7, 0))
A.sine(a, 'body', 'rotation', (0.8, 0, 0), phase=0.4)
A.sine(a, 'neck', 'rotation', (2.0, 2.5, 0), phase=0.9)
A.sine(a, 'head', 'rotation', (-1.5, 3.0, 1.0), phase=1.5)
A.sine(a, 'jaw', 'rotation', (2.0, 0, 0), offset=(2.5, 0, 0), period=1.6)
A.sine(a, 'throat', 'scale', (0.04, 0.08, 0.03), offset=(1.0, 1.0, 1.0), period=1.6, phase=0.5)
A.chain_wave(a, TAIL, (0.6, 4, 0), phase_step=0.7, growth=1.12)
A.sine(a, 'arm_l', 'rotation', (4, 0, 2), phase=0.2)
A.sine(a, 'arm_r', 'rotation', (4, 0, -2), phase=0.9)

a = T('walk', 2.0, True)
gait(a, 2.0, WALK_KEYS)
A.sine(a, 'body', 'position', (0, -1.3, 0), period=1.0, phase=-math.pi / 2)
A.sine(a, 'body', 'rotation', (0, 2.0, 2.6), period=2.0)
A.sine(a, 'neck', 'rotation', (1.5, -2.0, -1.5), period=1.0, phase=0.6)
A.sine(a, 'head', 'rotation', (-1.0, -1.0, -1.2), period=2.0, phase=0.3)
A.chain_wave(a, TAIL, (0, -6, 0), period=2.0, phase_step=0.55, growth=1.12, phase=0.5)
A.sine(a, 'arm_l', 'rotation', (10, 0, 0), period=2.0, phase=math.pi)
A.sine(a, 'arm_r', 'rotation', (10, 0, 0), period=2.0)

a = T('run', 1.1, True)
gait(a, 1.1, RUN_KEYS, steps_per_cycle=14)
A.sine(a, 'body', 'position', (0, -2.6, 0), period=0.55, phase=-math.pi / 2, offset=(0, 1.0, 0))
A.sine(a, 'body', 'rotation', (2.0, 3.0, 3.5), period=1.1, offset=(6, 0, 0))
A.sine(a, 'neck', 'rotation', (3.0, -2.0, 0), period=0.55, phase=0.8, offset=(8, 0, 0))
A.hold(a, 'head', 'rotation', (-10, 0, 0))
A.hold(a, 'jaw', 'rotation', (10, 0, 0))
A.chain_wave(a, TAIL, (3, -8, 0), period=1.1, phase_step=0.5, growth=1.1, phase=0.5)
A.hold(a, 'arm_l', 'rotation', (-30, 0, 0))
A.hold(a, 'arm_r', 'rotation', (-30, 0, 0))

# ------------------------------------------------------------------ roar (44 ticks; enrage plays 40)
a = T('roar', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.3, (6, 0, 0)), (0.55, (-14, 0, 0)), (0.75, (-16, 0, 0)), (1.75, (-14, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.3, (0, 3, 0)), (0.6, (0, -3, 2)), (1.75, (0, -3, 2)), (2.2, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.6, (-16, 0, 0)), (0.9, (-14, -10, 0)), (1.2, (-16, 10, 0)), (1.5, (-14, -6, 0)), (1.8, (-10, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.3, (10, 0, 0)), (0.6, (-24, 0, 0)), (0.9, (-22, 0, 8)), (1.2, (-24, 0, -8)), (1.75, (-20, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.4, (6, 0, 0)), (0.62, (48, 0, 0)), (1.65, (44, 0, 0)), (2.0, (2, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'throat', 'scale', (0, (1, 1, 1)), (0.5, (1.3, 1.6, 1.2)), (0.7, (1.1, 1.2, 1.1)), (1.7, (1.1, 1.2, 1.1)), (2.2, (1, 1, 1)))
legs_pose(a, [(0, (0, 0, 0)), (0.25, (-30, 40, -10)), (0.45, (-8, 6, 2)), (0.6, (-6, 2, 4)), (1.8, (-6, 2, 4)), (2.2, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.3, (4, 6, -4)), (0.6, (10, 6, -8)), (1.8, (10, 6, -8)), (2.2, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.6, (-40, 25, 30)), (1.7, (-36, 22, 26)), (2.2, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.4, (-4, 0, 0)), (0.7, (8, 0, 0)), (1.0, (8, 6, 0)), (1.3, (8, -6, 0)), (1.75, (6, 0, 0)), (2.2, (0, 0, 0))], growth=1.1)

# ------------------------------------------------------------------ reactions / states
a = T('flinch', 1.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.12, (-8, -10, -6)), (0.45, (-3, 4, 3)), (1.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.12, (-2, -1, 5)), (0.5, (0, 1, 2)), (1.2, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.12, (-22, -18, 0)), (0.5, (8, 8, 0)), (1.2, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.12, (-14, -10, 12)), (0.6, (6, 4, -4)), (1.2, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.15, (38, 0, 0)), (0.7, (10, 0, 0)), (1.2, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.2, (14, 20, -10)), (0.6, (6, 6, -4)), (1.2, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.25, (-8, 4, 4)), (1.2, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.15, (6, 10, 0)), (0.5, (-2, -6, 0)), (1.2, (0, 0, 0))], growth=1.1)
arms(a, [(0, (0, 0, 0)), (0.15, (-30, 20, 20)), (0.7, (0, 0, 0)), (1.2, (0, 0, 0))])

a = T('death', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.35, (-12, 0, -6)), (0.8, (-4, 0, 26)), (1.25, (2, 0, 72)), (1.45, (2, 0, 66)), (1.7, (2, 0, 69)), (2.2, (2, 0, 68)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.35, (0, -3, 2)), (0.8, (2, 5, 0)), (1.25, (5, 15, 0)), (1.45, (5, 13, 0)), (2.2, (5, 14, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.35, (-24, 0, 0)), (1.25, (14, 14, -8)), (1.6, (18, 18, -12)), (2.2, (18, 18, -12)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.35, (-20, 0, 0)), (1.25, (8, 6, -10)), (2.2, (10, 8, -12)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.35, (44, 0, 0)), (1.3, (30, 0, 0)), (2.2, (22, 0, 0)))
P(a, 'throat', 'scale', (0, (1, 1, 1)), (0.35, (1.2, 1.4, 1.1)), (1.3, (1, 1, 1)), (2.2, (1, 1, 1)))
legs_pose(a, [(0, (0, 0, 0)), (0.35, (8, 10, -6)), (1.25, (-56, 30, 10)), (2.2, (-60, 32, 12))],
          [(0, (0, 0, 0)), (0.35, (-10, 6, 4)), (1.25, (-40, 46, -6)), (2.2, (-44, 50, -6))])
arms(a, [(0, (0, 0, 0)), (0.35, (-40, 30, 20)), (1.3, (10, 10, 0)), (2.2, (14, 14, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.35, (8, 0, 0)), (1.25, (6, 6, 0)), (1.6, (5, 8, 0)), (2.2, (5, 8, 0))])

a = T('sleep', 4.0, True)
A.hold(a, 'body', 'position', (0, 17, 0))
A.sine(a, 'body', 'rotation', (1.2, 0, 0), offset=(2, 0, -3))
A.hold(a, 'neck', 'rotation', (26, -18, 0))
A.hold(a, 'head', 'rotation', (6, -14, -8))
A.sine(a, 'jaw', 'rotation', (1.5, 0, 0), offset=(1.5, 0, 0))
A.sine(a, 'throat', 'scale', (0.06, 0.14, 0.04), offset=(1.0, 1.0, 1.0))
legs_pose(a, [(0, (-52, 92, -40)), (4.0, (-52, 92, -40))])
arms(a, [(0, (40, 10, -20)), (4.0, (40, 10, -20))])
for i, b in enumerate(TAIL):
    A.hold(a, b, 'rotation', (-3 if i == 0 else 0, -12 - i * 2, 0))

a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 10, 0))
A.sine(a, 'body', 'rotation', (6, 5, 7), period=0.5, offset=(-4, 0, 0))
A.sine(a, 'neck', 'rotation', (-10, 18, 0), period=0.5, phase=1.0, offset=(-10, 0, 0))
A.sine(a, 'head', 'rotation', (-8, 0, 10), period=0.5, phase=1.6, offset=(-8, 0, 0))
A.sine(a, 'jaw', 'rotation', (18, 0, 0), period=0.25, offset=(22, 0, 0))
legs_pose(a, [(0, (-30, 50, -10)), (0.25, (-10, 20, 0)), (0.5, (-30, 50, -10)), (0.75, (-10, 20, 0)), (1.0, (-30, 50, -10))],
          [(0, (-10, 20, 0)), (0.25, (-30, 50, -10)), (0.5, (-10, 20, 0)), (0.75, (-30, 50, -10)), (1.0, (-10, 20, 0))])
A.chain_wave(a, TAIL, (8, 16, 0), period=0.5, phase_step=0.6)
arms(a, [(0, (-30, 10, 10)), (0.25, (10, 30, 40)), (0.5, (-30, 10, 10)), (0.75, (10, 30, 40)), (1.0, (-30, 10, 10))])

a = T('stun', 1.6, True)
A.sine(a, 'body', 'rotation', (0, 3, 6), period=1.6, offset=(3, 0, 0))
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'neck', 'rotation', (6, 20, 0), period=1.6, phase=0.5, offset=(18, 0, 0))
A.sine(a, 'head', 'rotation', (8, 0, 14), period=0.8, offset=(6, 0, 0))
A.hold(a, 'jaw', 'rotation', (26, 0, 0))
legs_pose(a, [(0, (-8, 16, -6)), (1.6, (-8, 16, -6))])
A.chain_wave(a, TAIL, (-3, 5, 0), period=1.6, phase_step=0.5, growth=1.1)
arms(a, [(0, (20, 0, 10)), (1.6, (20, 0, 10))])

# ------------------------------------------------------------------ attacks (keys aligned to the Java timelines: t = tick / 20)
# bite: 28 ticks, hit @12
a = T('bite', 1.4)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.4, (-5, 0, 0)), (0.6, (8, 0, 0)), (0.9, (6, 0, 0)), (1.4, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.4, (0, -1, 4)), (0.6, (0, 2, -8)), (0.9, (0, 1, -6)), (1.4, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.4, (-16, 0, 0)), (0.6, (22, 0, 0)), (0.75, (20, -8, 0)), (0.9, (20, 8, 0)), (1.4, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.4, (-14, 0, 0)), (0.6, (4, 0, 0)), (0.75, (4, 0, 10)), (0.9, (4, 0, -10)), (1.4, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.42, (46, 0, 0)), (0.58, (0, 0, 0)), (0.66, (6, 0, 0)), (1.0, (2, 0, 0)), (1.4, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.4, (-4, 4, 0)), (0.6, (-18, 10, 8)), (1.0, (-14, 8, 6)), (1.4, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.6, (12, 4, -14)), (1.0, (10, 4, -12)), (1.4, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.4, (-3, 0, 0)), (0.6, (6, 0, 0)), (1.4, (0, 0, 0))])

# headbutt_charge: 56 ticks; wind-up 0-14 (paw + snort), charge 14-44, skid 44-56
a = T('headbutt_charge', 2.8)
gait(a, 0.5, RUN_KEYS, t0=0.75, t1=2.2, steps_per_cycle=10, amp=1.05)
for side in ('l', 'r'):
    for bn in ('thigh', 'shin', 'foot'):
        pass
# wind-up paw (right leg scrapes back twice) and skid (legs braced forward)
for bn, j in (('thigh', 0), ('shin', 1), ('foot', 2)):
    w_r = [(0.0, (0, 0, 0)), (0.2, (-24, 30, -6)), (0.35, (22, 8, -14)), (0.5, (-24, 30, -6)), (0.65, (20, 6, -12))]
    w_l = [(0.0, (0, 0, 0)), (0.35, (-6, 10, 0)), (0.65, (-8, 12, 0))]
    sk_l = [(2.32, (-34, 10, 28)), (2.55, (-30, 8, 24)), (2.8, (0, 0, 0))]
    sk_r = [(2.32, (-20, 16, 12)), (2.55, (-16, 12, 10)), (2.8, (0, 0, 0))]
    P(a, '%s_r' % bn, 'rotation', *[(t, (v[j], 0, 0)) for t, v in w_r + sk_r])
    P(a, '%s_l' % bn, 'rotation', *[(t, (v[j], 0, 0)) for t, v in w_l + sk_l])
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (6, 0, 0)), (0.8, (12, 0, 0)), (2.2, (12, 0, 0)), (2.4, (-6, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.6, (0, 2, 2)), (0.8, (0, 1, 0)), (2.2, (0, 1, 0)), (2.4, (0, 1, 3)), (2.8, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.3, (-10, 0, 0)), (0.7, (30, 0, 0)), (2.2, (28, 0, 0)), (2.45, (-6, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.3, (-6, 0, 0)), (0.7, (16, 0, 0)), (2.2, (16, 0, 0)), (2.45, (-8, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.3, (24, 0, 0)), (0.6, (2, 0, 0)), (2.25, (0, 0, 0)), (2.45, (26, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'throat', 'scale', (0, (1, 1, 1)), (0.3, (1.15, 1.3, 1.1)), (0.6, (1, 1, 1)), (2.8, (1, 1, 1)))
tail_keys(a, [(0, (0, 0, 0)), (0.7, (-1, 0, 0)), (1.2, (0, 6, 0)), (1.7, (0, -6, 0)), (2.2, (0, 4, 0)), (2.45, (2, 0, 0)), (2.8, (0, 0, 0))], growth=1.1)
arms(a, [(0, (0, 0, 0)), (0.7, (-30, 6, 10)), (2.2, (-30, 6, 10)), (2.45, (-50, 30, 30)), (2.8, (0, 0, 0))])

# head_slam: 50 ticks, impact @24 (tremor + rockfall)
a = T('head_slam', 2.5)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (-14, 0, 0)), (0.95, (-22, 0, 0)), (1.2, (13, 0, 0)), (1.6, (11, 0, 0)), (2.0, (3, 0, 0)), (2.5, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.2, (0, 2, 0)), (0.95, (0, -6, 4)), (1.2, (0, 4, -6)), (1.6, (0, 4, -5)), (2.5, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.5, (-18, 0, 0)), (0.95, (-26, 0, 0)), (1.2, (26, 0, 0)), (1.4, (28, 0, 0)), (1.6, (25, 0, 0)), (2.0, (8, 0, 0)), (2.5, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.5, (-14, 0, 0)), (0.95, (-22, 0, 0)), (1.2, (20, 0, 0)), (1.35, (22, 0, 4)), (1.5, (22, 0, -4)), (1.6, (20, 0, 0)), (2.0, (5, 0, 0)), (2.5, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.9, (30, 0, 0)), (1.15, (0, 0, 0)), (1.7, (0, 0, 0)), (2.0, (20, 0, 0)), (2.5, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.5, (-6, 8, -2)), (0.95, (8, 4, -10)), (1.2, (-26, 22, 6)), (1.6, (-24, 20, 6)), (2.5, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.5, (4, 8, -6)), (0.95, (12, 6, -14)), (1.2, (6, 14, -16)), (1.6, (6, 12, -14)), (2.5, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.95, (-50, 30, 40)), (1.2, (20, 10, 0)), (1.6, (20, 10, 0)), (2.5, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.5, (3, 0, 0)), (0.95, (5, 0, 0)), (1.2, (-3, 0, 0)), (1.35, (-1, 0, 0)), (1.6, (-2, 0, 0)), (2.5, (0, 0, 0))], growth=1.1)

# tail_club: 44 ticks; code turns the body 180 deg to the right over ticks 14-26 (see PREVIEW_ROOT_YAW), hits behind
a = T('tail_club', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.62, (2, -32, -4)), (0.72, (2, -30, -4)), (0.9, (0, -6, 3)), (1.3, (0, 10, 2)), (1.6, (0, -4, 0)), (2.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.62, (0, 4, 0)), (1.0, (0, 1, 0)), (1.5, (0, 2, 0)), (2.2, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.62, (6, 24, 0)), (1.0, (0, 14, 0)), (1.45, (0, -12, 0)), (2.2, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.62, (0, 14, 0)), (1.0, (0, 10, 0)), (1.5, (0, -8, 0)), (2.2, (0, 0, 0)))
# the tail is cocked up in the wind-up, then swings LOW so the club passes at hunter height (it flew ~1.5 blocks
# over the hunters' heads before while the code still hit them)
tail_keys(a, [(0, (0, 0, 0)), (0.62, (4, -14, 0)), (0.75, (2, -16, 0)), (1.0, (-7, -14, 0)), (1.22, (-9, -4, 0)), (1.42, (-8, 20, 0)), (1.65, (-4, 6, 0)), (1.9, (0, -4, 0)), (2.2, (0, 0, 0))], growth=1.12)
P(a, 'tail_club', 'rotation', (0, (0, 0, 0)), (0.75, (0, -12, 0)), (1.2, (0, -10, 0)), (1.45, (0, 22, 0)), (1.8, (0, -6, 0)), (2.2, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.62, (-12, 24, -6)), (0.85, (6, 10, -10)), (1.0, (-20, 40, 0)), (1.15, (-6, 10, 0)), (1.35, (4, 24, -8)), (1.7, (-4, 8, -2)), (2.2, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.62, (10, 16, -14)), (0.85, (-18, 38, -4)), (1.0, (6, 8, -8)), (1.2, (-14, 36, -2)), (1.4, (6, 8, -8)), (1.7, (4, 6, -6)), (2.2, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.62, (-20, 20, 10)), (1.3, (-30, 30, 20)), (2.2, (0, 0, 0))])


def _club_turn(t):
    # code: +15 deg/tick over ticks 14..26 (180 deg), eased
    u = min(1.0, max(0.0, (t - 0.7) / 0.6))
    return 180.0 * (0.5 - 0.5 * math.cos(math.pi * u))


PREVIEW_ROOT_YAW = {'tail_club': _club_turn}

# body_slam: 52 ticks; crouch 0-14, jump @14, airborne 15-25, impact @27, recover 36-52
a = T('body_slam', 2.6)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (8, 0, 0)), (0.75, (-10, 0, 0)), (1.1, (-4, 0, 0)), (1.35, (6, 0, 0)), (1.8, (5, 0, 0)), (2.6, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.6, (0, 9, 0)), (0.75, (0, -4, 0)), (1.1, (0, -2, 0)), (1.35, (0, 14, 0)), (1.45, (0, 15, 0)), (1.85, (0, 14, 0)), (2.3, (0, 4, 0)), (2.6, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.6, (-34, 56, -22)), (0.75, (16, -6, 10)), (0.95, (-40, 62, -20)), (1.25, (-30, 40, -10)), (1.35, (-62, 92, -30)), (1.85, (-60, 90, -30)), (2.3, (-20, 30, -10)), (2.6, (0, 0, 0))])
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.6, (12, 0, 0)), (0.75, (-16, 0, 0)), (1.2, (-12, 0, 0)), (1.35, (4, 0, 0)), (1.5, (2, 0, 0)), (1.85, (0, 0, 0)), (2.6, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.6, (4, 0, 0)), (0.75, (-14, 0, 0)), (1.35, (-6, 0, 0)), (1.85, (-4, 0, 0)), (2.6, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.75, (30, 0, 0)), (1.3, (8, 0, 0)), (1.45, (34, 0, 0)), (1.9, (10, 0, 0)), (2.6, (0, 0, 0)))
arms(a, [(0, (0, 0, 0)), (0.6, (20, 10, 0)), (0.8, (-50, 40, 30)), (1.3, (-40, 40, 20)), (1.4, (10, 20, 0)), (2.6, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.6, (-6, 0, 0)), (0.8, (10, 0, 0)), (1.25, (12, 0, 0)), (1.4, (-8, 0, 0)), (1.6, (-4, 0, 0)), (2.6, (0, 0, 0))], growth=1.1)

# mud_jet: 48 ticks; throat swells 0-16, jets @18 (fan) and @28 (aimed)
a = T('mud_jet', 2.4)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.8, (-8, 0, 0)), (0.92, (6, 0, 0)), (1.2, (-4, 0, 0)), (1.42, (6, 0, 0)), (1.8, (2, 0, 0)), (2.4, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.8, (0, -1, 4)), (0.92, (0, 1, -3)), (1.2, (0, 0, 1)), (1.42, (0, 1, -3)), (2.4, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.8, (-20, 0, 0)), (0.92, (12, 0, 0)), (1.2, (-10, 0, 0)), (1.42, (14, 0, 0)), (1.8, (6, 0, 0)), (2.4, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.8, (-12, 0, 0)), (0.92, (-6, 0, 0)), (1.2, (-8, 0, 0)), (1.42, (-4, 0, 0)), (2.4, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.8, (4, 0, 0)), (0.9, (40, 0, 0)), (1.1, (30, 0, 0)), (1.25, (10, 0, 0)), (1.4, (42, 0, 0)), (1.75, (34, 0, 0)), (2.1, (0, 0, 0)), (2.4, (0, 0, 0)))
P(a, 'throat', 'scale', (0, (1, 1, 1)), (0.75, (1.45, 1.9, 1.35)), (0.92, (1.1, 1.2, 1.05)), (1.25, (1.35, 1.7, 1.25)), (1.45, (1.0, 1.0, 1.0)), (2.4, (1, 1, 1)))
legs_pose(a, [(0, (0, 0, 0)), (0.8, (-6, 8, -2)), (0.92, (-14, 8, 6)), (2.4, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.8, (8, 4, -8)), (0.92, (12, 4, -12)), (2.4, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.8, (-6, 0, 0)), (0.95, (6, 0, 0)), (1.45, (6, 0, 0)), (2.4, (0, 0, 0))])

# rock_throw: 56 ticks; head digs 10-24, flings @28
a = T('rock_throw', 2.8)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (14, 0, 0)), (1.2, (16, 0, 0)), (1.42, (-14, 0, 0)), (1.8, (-8, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.5, (0, 4, -2)), (1.2, (0, 5, -2)), (1.42, (0, -4, 3)), (1.8, (0, -2, 2)), (2.8, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.5, (30, 0, 0)), (0.7, (35, -8, 0)), (0.9, (32, 8, 0)), (1.05, (36, -6, 0)), (1.2, (34, 0, 0)), (1.42, (-30, 0, 0)), (1.8, (-18, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.5, (20, 0, 0)), (0.7, (25, 0, 0)), (0.9, (18, 0, 0)), (1.05, (25, 0, 0)), (1.2, (22, 0, 0)), (1.42, (-34, 0, 0)), (1.8, (-16, 0, 0)), (2.8, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.6, (24, 0, 0)), (1.2, (20, 0, 0)), (1.42, (40, 0, 0)), (1.9, (10, 0, 0)), (2.8, (0, 0, 0)))
legs_pose(a, [(0, (0, 0, 0)), (0.5, (-20, 24, 2)), (1.2, (-20, 24, 2)), (1.42, (2, 2, -4)), (2.8, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.5, (14, 10, -16)), (1.2, (14, 10, -16)), (1.42, (6, 6, -8)), (2.8, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.5, (10, 10, 0)), (1.42, (-50, 30, 30)), (2.0, (-20, 10, 10)), (2.8, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.5, (-3, 0, 0)), (0.8, (-3, 5, 0)), (1.0, (-3, -5, 0)), (1.2, (-3, 0, 0)), (1.42, (6, 0, 0)), (1.8, (3, 0, 0)), (2.8, (0, 0, 0))])

# double_slam (enraged): 80 ticks, impacts @22 and @50 (geysers)
a = T('double_slam', 4.0)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.85, (-20, -10, 0)), (1.1, (13, 8, 0)), (1.45, (10, 6, 0)), (2.25, (-26, 10, 0)), (2.5, (15, -6, 0)), (3.0, (13, -4, 0)), (3.5, (5, 0, 0)), (4.0, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.85, (0, -5, 3)), (1.1, (0, 4, -6)), (1.45, (0, 3, -4)), (2.25, (0, -8, 5)), (2.5, (0, 5, -7)), (3.0, (0, 4, -6)), (4.0, (0, 0, 0)))
P(a, 'neck', 'rotation', (0, (0, 0, 0)), (0.85, (-24, -10, 0)), (1.1, (26, 6, 0)), (1.45, (23, 4, 0)), (2.25, (-30, 8, 0)), (2.5, (27, -6, 0)), (3.0, (25, -4, 0)), (3.5, (10, 0, 0)), (4.0, (0, 0, 0)))
P(a, 'head', 'rotation', (0, (0, 0, 0)), (0.85, (-20, 0, 0)), (1.1, (20, 0, 0)), (1.45, (18, 0, 0)), (2.25, (-26, 0, 0)), (2.5, (22, 0, 0)), (2.7, (22, 0, 5)), (2.85, (22, 0, -5)), (3.0, (21, 0, 0)), (3.5, (6, 0, 0)), (4.0, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.8, (34, 0, 0)), (1.05, (0, 0, 0)), (1.6, (6, 0, 0)), (2.1, (46, 0, 0)), (2.45, (0, 0, 0)), (3.2, (0, 0, 0)), (3.6, (24, 0, 0)), (4.0, (0, 0, 0)))
P(a, 'throat', 'scale', (0, (1, 1, 1)), (1.9, (1, 1, 1)), (2.15, (1.3, 1.6, 1.2)), (2.5, (1, 1, 1)), (4.0, (1, 1, 1)))
legs_pose(a, [(0, (0, 0, 0)), (0.85, (6, 4, -8)), (1.1, (-26, 22, 6)), (1.45, (-22, 20, 4)), (2.25, (10, 4, -12)), (2.5, (-30, 26, 6)), (3.0, (-28, 24, 6)), (4.0, (0, 0, 0))],
          [(0, (0, 0, 0)), (0.85, (12, 6, -14)), (1.1, (6, 14, -16)), (1.45, (4, 10, -12)), (2.25, (14, 6, -16)), (2.5, (8, 16, -18)), (3.0, (6, 14, -16)), (4.0, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.85, (-50, 30, 40)), (1.1, (20, 10, 0)), (2.25, (-60, 40, 50)), (2.5, (20, 10, 0)), (3.2, (10, 10, 0)), (4.0, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.85, (4, 4, 0)), (1.1, (-3, -4, 0)), (1.5, (-2, 0, 0)), (2.25, (6, -4, 0)), (2.5, (-3, 4, 0)), (3.0, (-2, 0, 0)), (4.0, (0, 0, 0))], growth=1.1)

def check_anims(model):
    """Minecraft divides by the gap between keyframes: duplicate timestamps in a channel would produce NaN."""
    for an, anim in model.animations.items():
        for bone, chans in anim.channels.items():
            assert bone in model.bones, '%s: unknown bone %s' % (an, bone)
            for ch, keys in chans.items():
                for k0, k1 in zip(keys, keys[1:]):
                    assert k1[0] - k0[0] > 1e-4, '%s %s %s: duplicate key time %.4f' % (an, bone, ch, k1[0])


if __name__ == '__main__':
    check_anims(m)
    root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft')
    img, glow = m.export(root)
    print('texture', m.tex_w, m.tex_h, 'cubes', sum(1 for _ in m.all_cubes()), 'bones', len(m.bones), 'anims', len(m.animations))
    if '--preview' in sys.argv:
        from hcmodel.render import preview
        sel = [x for x in sys.argv[1:] if not x.startswith('--')]
        preview(m, img, os.path.join(os.path.dirname(__file__), '..', 'preview', 'gravadon.png'), anims=sel or None, size=260)
