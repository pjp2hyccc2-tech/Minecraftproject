"""Gansekigani (ガンセキガニ) - crab wearing a mossy boulder as its shell. Small monster.

* left claw = big crusher claw (fiddler-crab wave), right claw smaller
* walk/run turn the body 90 degrees so the crab scuttles sideways (eyes keep looking ahead)
* 'shell' = withdrawn defensive state pose (body tucked under the boulder)
* the boulder bone is hidden when the 'shell' part breaks (PartDef hideBones "boulder")
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, accent_pass, dim_under_glow  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402
from hcmodel.paint import vnoise  # noqa: E402

# ------------------------------------------------------------------ materials
M('gk_shell', ['#2c0d08', '#511a0e', '#7c2c16', '#a6431f', '#cc6230', '#ea8a4e'], 'skin', contrast=0.55, noise=0.04, edge=0.09,
  belly='gk_belly', accent='gk_spot', accent_mode='spots', accent_freq=0.55, accent_thresh=0.76)
M('gk_limb', ['#2c0d08', '#511a0e', '#7c2c16', '#a6431f', '#cc6230', '#ea8a4e'], 'skin', contrast=0.5, noise=0.04, edge=0.08)
M('gk_spot', ['#5c2410', '#88401e', '#b0602e', '#d08448', '#e8a868', '#f8cc92'], 'skin', contrast=0.4, edge=0.0)
M('gk_belly', ['#6a5040', '#8e705a', '#b09276', '#ceb294', '#e6d0b4', '#f8ead6'], 'plates', scale=0.7, contrast=0.4, edge=0.05, base=0.6)
M('gk_rock', ['#1c1a18', '#34302b', '#4e4842', '#6a635b', '#878076', '#a8a094'], 'rock', scale=1.3, contrast=0.85, noise=0.05, edge=0.07)
M('gk_moss', ['#1a2a0e', '#2a4216', '#3e5c1e', '#55782a', '#6f9436', '#8cb046'], 'fur', contrast=0.7, noise=0.05, edge=0.0)
M('gk_tip', ['#120c0a', '#221814', '#34261e', '#4a382c', '#62503e', '#7c6852'], 'horn', edge=0.0)
M('gk_crystal', ['#5a3400', '#8c5600', '#c07c08', '#e8a420', '#ffc848', '#ffe890'], 'crystal', glow='#ffb43a', edge=0.0)
M('gk_eye', ['#060608', '#0e0e12', '#18181e', '#24242c', '#32323c', '#42424e'], 'smooth', edge=0.0)

m = Model('gansekigani')
m.config.update({'head_bones': [['eyes', 0.7]], 'walk_speed': 4.5, 'run_speed': 0.65, 'walk_scale': 4.0})

# ------------------------------------------------------------------ body (wide, flat carapace)
body = m.bone('body', (0, 15, 0), (0, 0, 0))
carapace = body.cube((-7, -2.5, -6), (14, 5, 11), 'gk_shell', decals=[{'type': 'belly_band'}])
dome = body.cube((-5.5, -3.5, -5), (11, 1, 9), 'gk_shell')
maw = body.bone('maw', (0, 0, -6), (0, 0, 0))
mouth = maw.cube((-2.5, 0, -1), (5, 2, 1), 'gk_belly', decals=[{'type': 'mouth', 'at': 0.5, 'teeth': False}])
# little spines along the front corners of the carapace
for side in (1, -1):
    for i, (x, z) in enumerate(((6.0, -6.5), (7.0, -4.5))):
        body.cube((side * x - 0.5 - (0 if side > 0 else 0), -1.5, z), (1, 1, 1), 'gk_tip')

# ------------------------------------------------------------------ boulder (removable)
boulder = body.bone('boulder', (0, -3, 1.5), (-4, 0, 0))
rock_main = boulder.cube((-6.5, -8, -4.5), (13, 8, 11), 'gk_rock')
rock_top = boulder.cube((-4.5, -11, -3), (8, 3, 8), 'gk_rock')
rock_cap = boulder.cube((-2.5, -12, -1), (4, 1, 4), 'gk_rock')
rock_l = boulder.cube((5.5, -6, -1), (3, 5, 6), 'gk_rock')
rock_r = boulder.cube((-8.5, -7, -3), (3, 5, 5), 'gk_rock')
rock_b = boulder.cube((-3.5, -6, 6), (7, 5, 2), 'gk_rock')
ROCKS = [rock_main, rock_top, rock_cap, rock_l, rock_r, rock_b]
for i, (x, y, z, h, rx, rz) in enumerate(((1.5, -10.5, 0.5, 5, -12, 18), (-3.5, -9.5, 3.5, 4, 14, -24), (6.5, -5.5, 2.5, 3, 8, 50))):
    cr = boulder.bone('crystal%d' % i, (x, y, z), (rx, 0, rz))
    cr.cube((-1, -h, -1), (2, h, 2), 'gk_crystal', inflate=-0.15)


def moss(P, L, N, f, d, r, face):
    if face == 'bottom':
        return None
    n = vnoise(P[..., 0] * 0.28, P[..., 1] * 0.28, P[..., 2] * 0.28, 6)
    if face == 'top':
        return n > 0.42
    return (n > 0.58) & (d < 0.45 + 0.3 * (n - 0.58))


accent_pass(m, ROCKS, 'gk_moss', moss)
dim_under_glow(m, 0.5)

# ------------------------------------------------------------------ eye stalks (in front of the boulder)
eyes = body.bone('eyes', (0, -2.5, -5), (0, 0, 0))
stalk = eyes.bone('stalk_l', (2, 0, 0), (18, 0, 14))
stalk.cube((-0.5, -4, -0.5), (1, 4, 1), 'gk_limb')
ball = stalk.cube((-1, -6, -1), (2, 2, 2), 'gk_eye')
for fc, row in (('north', ['W.']), ('east', ['W.']), ('west', ['.W']), ('top', ['..'])):
    stamp(m, ball, fc, 0, 0, row, {'W': '#ffffff'})
mirror_tree(eyes, stalk)

# ------------------------------------------------------------------ claws
def claw(side, big):
    s = 'l' if side > 0 else 'r'
    arm = body.bone('arm_%s' % s, (side * 5.0, 0.5, -5), (-26, -side * 30, side * -8))
    arm.cube((-1, -1, -4), (2, 2, 5), 'gk_limb')
    wrist = arm.bone('claw_%s' % s, (0, 0, -3.5), (14, side * (58 if big else 52), 0))
    if big:
        palm = wrist.cube((-3, -3, -7), (6, 6, 7), 'gk_shell')
        wrist.cube((-2.5, 1, -11), (5, 2, 4), 'gk_shell', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.4, 'mat': 'gk_tip'}])
        finger = wrist.bone('pincer_%s' % s, (0, -2, -7), (0, 0, 0))
        finger.cube((-2.5, -0.5, -4.5), (5, 2, 5), 'gk_shell', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.4, 'mat': 'gk_tip'}])
        for kz in (-5.5, -3):
            wrist.cube((-0.5, -4, kz), (1, 1, 1), 'gk_tip')                 # knobs on the crusher
    else:
        palm = wrist.cube((-2, -2.5, -5), (4, 4, 5), 'gk_shell')
        wrist.cube((-1.5, 0.5, -8), (3, 1, 3), 'gk_shell', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.4, 'mat': 'gk_tip'}])
        finger = wrist.bone('pincer_%s' % s, (0, -1.5, -5), (0, 0, 0))
        finger.cube((-1.5, -0.5, -3.5), (3, 2, 4), 'gk_shell', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.4, 'mat': 'gk_tip'}])
    return arm, palm


claw(1, True)
claw(-1, False)

# ------------------------------------------------------------------ walking legs (3 pairs, knees high like a spider)
LEGS = (0, 1, 2)
for i, (z, yaw) in enumerate(((-3.0, 22), (0.5, 0), (3.5, -24))):
    up = body.bone('leg%d_l' % i, (6, 0.5, z), (0, yaw, -38))
    up.cube((0, -1, -1), (6, 2, 2), 'gk_limb')
    lo = up.bone("leg%d_l_shin" % i, (5.5, 0, 0), (0, 0, 24))
    lo.cube((-1, -1, -1), (2, 8, 2), 'gk_limb', inflate=-0.1, decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.3, 'mat': 'gk_tip'}])
    mirror_tree(body, up)

fit_to_ground(m, body)
m.state_poses = ('shell',)

# ------------------------------------------------------------------ animations
T = m.anim
SIDES = (('l', 1), ('r', -1))


def mir(pts, g):
    """Mirror a left-side rotation track for the right side (Y and Z negate)."""
    return [(t, (x, g * y, g * z)) for t, (x, y, z) in pts]


def claws(a, pts_l, pts_r=None):
    keys(a, 'arm_l', 'rotation', pts_l)
    keys(a, 'arm_r', 'rotation', mir(pts_r or pts_l, -1))


def wrists(a, pts_l, pts_r=None):
    keys(a, 'claw_l', 'rotation', pts_l)
    keys(a, 'claw_r', 'rotation', mir(pts_r or pts_l, -1))


def pincers(a, pts):
    keys(a, 'pincer_l', 'rotation', pts)
    keys(a, 'pincer_r', 'rotation', pts)


def legs_pose(a, pts_up, pts_lo=None):
    """Same pose track for every leg (left values, mirrored for the right side)."""
    for i in LEGS:
        for s, g in SIDES:
            keys(a, 'leg%d_%s' % (i, s), 'rotation', mir(pts_up, g))
            if pts_lo:
                keys(a, 'leg%d_%s_shin' % (i, s), 'rotation', mir(pts_lo, g))


def scuttle(a, period, lift, reach, steps=12):
    """Sideways tripod gait: legs 0L/1R/2L move together, the others in antiphase."""
    for i in LEGS:
        for s, g in SIDES:
            ph = 0.0 if (i % 2 == 0) == (s == 'l') else math.pi
            A.sample(a, 'leg%d_%s' % (i, s), 'rotation',
                     lambda t, ph=ph, g=g: (0, g * 6 * cyc(t, period, ph), g * -lift * max(0.0, cyc(t, period, ph))), steps)
            A.sample(a, 'leg%d_%s_shin' % (i, s), 'rotation',
                     lambda t, ph=ph, g=g: (0, 0, g * -reach * cyc(t, period, ph + math.pi / 2)), steps)


# idle: breathing, mouthparts fluttering, eye stalks twitching, pincers clicking now and then
a = T('idle', 2.4, True)
A.sine(a, 'body', 'position', (0, 0.25, 0))
A.sine(a, 'maw', 'rotation', (8, 0, 0), period=0.3, offset=(4, 0, 0))
keys(a, 'stalk_l', 'rotation', [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.7, (-10, 0, 8)), (1.5, (-10, 0, 8)), (1.6, (0, 0, 0)), (2.4, (0, 0, 0))])
keys(a, 'stalk_r', 'rotation', [(0, (4, 0, -6)), (1.0, (4, 0, -6)), (1.1, (-6, 0, 0)), (2.0, (-6, 0, 0)), (2.1, (4, 0, -6)), (2.4, (4, 0, -6))])
A.sample(a, 'pincer_l', 'rotation', lambda t: (-14 * max(0.0, math.sin(t * 2 * math.pi / 1.2 * 2)) ** 3, 0, 0), 24)
A.sample(a, 'pincer_r', 'rotation', lambda t: (-12 * max(0.0, math.sin(t * 2 * math.pi / 1.2 * 2 + 2.2)) ** 3, 0, 0), 24)
A.sine(a, 'arm_l', 'rotation', (2, 0, 3), period=2.4)
A.sine(a, 'arm_r', 'rotation', (2, 0, -3), period=2.4, phase=0.9)

# walk / run: the body turns 90 degrees and scuttles sideways
for name, period, lift, reach, bob in (('walk', 0.5, 22, 18, 0.5), ('run', 0.32, 28, 24, 0.8)):
    a = T(name, period, True)
    A.hold(a, 'body', 'rotation', (0, 90, 0))
    A.sample(a, 'body', 'position', lambda t, period=period, bob=bob: (0, -bob * abs(cyc(t, period)), 0), 12)
    scuttle(a, period, lift, reach)
    A.sine(a, 'arm_l', 'rotation', (4, 0, 5), period=period)
    A.sine(a, 'arm_r', 'rotation', (4, 0, -5), period=period, phase=math.pi)
    A.sine(a, 'stalk_l', 'rotation', (0, 0, 8), period=period)
    A.sine(a, 'stalk_r', 'rotation', (0, 0, 8), period=period)
    A.sine(a, 'boulder', 'rotation', (0, 0, 1.5), period=period)

# pinch (20 ticks, hit at tick 8): the crusher claw opens wide, then lunges out and snaps shut
a = T('pinch', 1.0)
keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-26, -22, -10)), (0.4, (18, 26, 4)), (0.55, (14, 22, 2)), (1.0, (0, 0, 0))])
keys(a, 'claw_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, -30, 0)), (0.4, (6, -58, 0)), (0.6, (4, -50, 0)), (1.0, (0, 0, 0))])
keys(a, 'pincer_l', 'rotation', [(0, (0, 0, 0)), (0.28, (-50, 0, 0)), (0.4, (4, 0, 0)), (0.5, (-6, 0, 0)), (0.6, (2, 0, 0)), (1.0, (0, 0, 0))])
keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, 10, 0)), (0.6, (-8, 10, 0)), (1.0, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-6, 8, -3)), (0.4, (5, -10, 2)), (0.6, (3, -6, 1)), (1.0, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -0.8, 1)), (0.4, (0, 0, -1.5)), (1.0, (0, 0, 0))])
keys(a, 'eyes', 'rotation', [(0, (0, 0, 0)), (0.3, (-12, 0, 0)), (0.42, (10, 0, 0)), (1.0, (0, 0, 0))])

# slam (36 ticks, hit at tick 17): rears up with both claws high, smashes them into the ground
a = T('slam', 1.8)
claws(a, [(0, (0, 0, 0)), (0.65, (-64, 12, -26)), (0.76, (-70, 12, -30)), (0.86, (30, -6, 12)), (1.15, (24, -4, 10)), (1.8, (0, 0, 0))])
wrists(a, [(0, (0, 0, 0)), (0.65, (-20, -40, 0)), (0.86, (24, -50, 0)), (1.2, (18, -40, 0)), (1.8, (0, 0, 0))])
pincers(a, [(0, (0, 0, 0)), (0.6, (-45, 0, 0)), (0.84, (0, 0, 0)), (1.8, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.65, (-16, 0, 0)), (0.76, (-18, 0, 0)), (0.88, (9, 0, 0)), (1.2, (3, 0, 0)), (1.8, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.65, (0, -2.5, 1.2)), (0.88, (0, 0.6, -1.2)), (1.8, (0, 0, 0))])
legs_pose(a, [(0, (0, 0, 0)), (0.65, (0, 0, 10)), (0.88, (0, 0, -12)), (1.1, (0, 0, -6)), (1.8, (0, 0, 0))])
keys(a, 'eyes', 'rotation', [(0, (0, 0, 0)), (0.65, (-20, 0, 0)), (0.9, (22, 0, 0)), (1.8, (0, 0, 0))])
keys(a, 'boulder', 'rotation', [(0, (0, 0, 0)), (0.88, (0, 0, 0)), (0.95, (3, 0, 2)), (1.1, (-1, 0, -1)), (1.3, (0, 0, 0))])

# emerge (12 ticks): bursts out of the boulder, claws flung wide in a threat display
a = T('emerge', 0.6)
keys(a, 'body', 'position', [(0, (0, 2.5, 0)), (0.2, (0, -1.5, 0)), (0.6, (0, 0, 0))])
claws(a, [(0, (10, 40, 30)), (0.2, (-40, -30, -30)), (0.42, (-34, -24, -24)), (0.6, (0, 0, 0))])
wrists(a, [(0, (0, 20, 0)), (0.2, (-10, -40, 0)), (0.6, (0, 0, 0))])
pincers(a, [(0, (0, 0, 0)), (0.2, (-45, 0, 0)), (0.32, (0, 0, 0)), (0.44, (-40, 0, 0)), (0.6, (0, 0, 0))])
keys(a, 'stalk_l', 'rotation', [(0, (-80, 0, 0)), (0.18, (8, 0, 0)), (0.6, (0, 0, 0))])
keys(a, 'stalk_r', 'rotation', [(0, (-80, 0, 0)), (0.18, (8, 0, 0)), (0.6, (0, 0, 0))])
legs_pose(a, [(0, (0, 0, -40)), (0.2, (0, 0, 6)), (0.6, (0, 0, 0))], [(0, (0, 0, 60)), (0.2, (0, 0, -6)), (0.6, (0, 0, 0))])

# wave (30 ticks): fiddler-crab courtship wave with the crusher claw
a = T('wave', 1.5)
keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-50, -30, -40)), (0.55, (-14, -10, -10)), (0.8, (-50, -30, -40)), (1.05, (-14, -10, -10)), (1.5, (0, 0, 0))])
keys(a, 'claw_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, -40, 0)), (1.05, (-20, -40, 0)), (1.5, (0, 0, 0))])
keys(a, 'pincer_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (0.55, (0, 0, 0)), (0.8, (-30, 0, 0)), (1.05, (0, 0, 0)), (1.5, (0, 0, 0))])
keys(a, 'stalk_l', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, 16)), (1.2, (0, 0, 16)), (1.5, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, -5)), (1.2, (0, 0, -5)), (1.5, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -0.6, 0)), (1.2, (0, -0.6, 0)), (1.5, (0, 0, 0))])

# bubble (40 ticks): froths bubbles from the mouth, eyes wiggling happily
a = T('bubble', 2.0)
A.sample(a, 'maw', 'rotation', lambda t: (10 + 14 * math.sin(t * 2 * math.pi * 6) if 0.2 < t < 1.8 else 0, 0, 0), 40)
A.sample(a, 'body', 'scale', lambda t: (1 + 0.03 * max(0.0, math.sin(t * 9)), 1 + 0.04 * max(0.0, math.sin(t * 9)), 1), 30)
A.sample(a, 'stalk_l', 'rotation', lambda t: (10 * math.sin(t * 7), 0, 14 * math.sin(t * 5)), 30)
A.sample(a, 'stalk_r', 'rotation', lambda t: (10 * math.sin(t * 7 + 1), 0, -14 * math.sin(t * 5 + 1)), 30)
claws(a, [(0, (0, 0, 0)), (0.4, (6, 14, 10)), (1.6, (6, 14, 10)), (2.0, (0, 0, 0))])

# flinch: recoils, claws thrown up, eyes snap back
a = T('flinch', 0.7)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-10, 8, 8)), (0.35, (2, -3, -3)), (0.7, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -1.5, 1.5)), (0.7, (0, 0, 0))])
claws(a, [(0, (0, 0, 0)), (0.1, (-36, -20, -30)), (0.45, (-8, -6, -8)), (0.7, (0, 0, 0))])
pincers(a, [(0, (0, 0, 0)), (0.1, (-40, 0, 0)), (0.4, (0, 0, 0))])
keys(a, 'stalk_l', 'rotation', [(0, (0, 0, 0)), (0.08, (-50, 0, 20)), (0.5, (-6, 0, 0)), (0.7, (0, 0, 0))])
keys(a, 'stalk_r', 'rotation', [(0, (0, 0, 0)), (0.08, (-50, 0, -20)), (0.5, (-6, 0, 0)), (0.7, (0, 0, 0))])

# shell (state pose): body tucked under the boulder, legs folded, claws shielding the face, eyes hidden
a = T('shell', 2.0, True)
A.hold(a, 'body', 'position', (0, 2.0, 0.5))
claws(a, [(0, (10, 10, 26)), (2.0, (10, 10, 26))])
wrists(a, [(0, (0, 14, 0)), (2.0, (0, 14, 0))])
keys(a, 'stalk_l', 'rotation', [(0, (-96, 0, -10)), (2.0, (-96, 0, -10))])
keys(a, 'stalk_r', 'rotation', [(0, (-96, 0, 10)), (2.0, (-96, 0, 10))])
legs_pose(a, [(0, (0, 0, -22)), (2.0, (0, 0, -22))], [(0, (0, 0, 126)), (2.0, (0, 0, 126))])
A.sine(a, 'boulder', 'rotation', (0.6, 0, 0.6), period=2.0)
A.sine(a, 'maw', 'rotation', (4, 0, 0), period=0.5, offset=(2, 0, 0))

# death: legs give way and splay flat, the crab sags onto one side, eyes droop
a = T('death', 1.5)
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -1, 0)), (0.9, (0, 1.4, 0)), (1.5, (0, 1.4, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, 0, 6)), (0.9, (3, 0, -6)), (1.1, (2, 0, -4)), (1.5, (2, 0, -5))])
claws(a, [(0, (0, 0, 0)), (0.3, (-30, -20, -30)), (0.9, (-2, -14, 30)), (1.5, (0, -16, 32))])
wrists(a, [(0, (0, 0, 0)), (0.9, (-4, -30, 0)), (1.5, (-6, -32, 0))])
pincers(a, [(0, (0, 0, 0)), (0.3, (-40, 0, 0)), (1.5, (-20, 0, 0))])
keys(a, 'stalk_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (0.9, (40, 0, 50)), (1.5, (44, 0, 56))])
keys(a, 'stalk_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (0.9, (40, 0, -40)), (1.5, (44, 0, -46))])
legs_pose(a, [(0, (0, 0, 0)), (0.9, (0, 0, 26)), (1.5, (0, 0, 28))], [(0, (0, 0, 0)), (0.6, (0, 0, -50)), (1.0, (0, 0, -98)), (1.5, (0, 0, -102))])
keys(a, 'maw', 'rotation', [(0, (0, 0, 0)), (0.4, (30, 0, 0)), (1.5, (24, 0, 0))])

# sleep: half withdrawn, eye stalks folded flat, slow bubbly breathing
a = T('sleep', 3.0, True)
A.hold(a, 'body', 'position', (0, 1.6, 0))
claws(a, [(0, (16, 8, 22)), (3.0, (16, 8, 22))])
keys(a, 'stalk_l', 'rotation', [(0, (60, 0, 50)), (3.0, (60, 0, 50))])
keys(a, 'stalk_r', 'rotation', [(0, (60, 0, -50)), (3.0, (60, 0, -50))])
A.sine(a, 'body', 'scale', (0.02, 0.03, 0.0), period=1.5, offset=(1, 1, 1))
legs_pose(a, [(0, (0, 0, 4)), (3.0, (0, 0, 4))], [(0, (0, 0, -40)), (3.0, (0, 0, -40))])
A.sine(a, 'maw', 'rotation', (6, 0, 0), period=1.5, offset=(6, 0, 0))

# stun: dizzy wobble, eyes circling
a = T('stun', 1.0, True)
A.sine(a, 'body', 'rotation', (0, 0, 6), period=1.0)
A.sample(a, 'stalk_l', 'rotation', lambda t: (20 * math.sin(t * 2 * math.pi), 0, 20 * math.cos(t * 2 * math.pi)), 16)
A.sample(a, 'stalk_r', 'rotation', lambda t: (20 * math.sin(t * 2 * math.pi + 3), 0, -20 * math.cos(t * 2 * math.pi + 3)), 16)
claws(a, [(0, (18, 10, 26)), (0.5, (22, 14, 30)), (1.0, (18, 10, 26))])
pincers(a, [(0, (-24, 0, 0)), (1.0, (-24, 0, 0))])
A.sine(a, 'maw', 'rotation', (10, 0, 0), period=0.25, offset=(10, 0, 0))

# trapped (not used by small monsters, kept for completeness): flailing struggle
a = T('trapped', 1.0, True)
scuttle(a, 0.25, 30, 22, steps=8)
A.sample(a, 'arm_l', 'rotation', lambda t: (-30 + 20 * cyc(t, 0.5), -10, -20 * cyc(t, 0.5)), 10)
A.sample(a, 'arm_r', 'rotation', lambda t: (-30 + 20 * cyc(t, 0.5, 1.5), 10, -20 * cyc(t, 0.5, 1.5)), 10)
A.sine(a, 'body', 'rotation', (0, 0, 5), period=0.5)
pincers(a, [(0, (-30, 0, 0)), (0.25, (0, 0, 0)), (0.5, (-30, 0, 0)), (0.75, (0, 0, 0)), (1.0, (-30, 0, 0))])

if __name__ == '__main__':
    run(m)
