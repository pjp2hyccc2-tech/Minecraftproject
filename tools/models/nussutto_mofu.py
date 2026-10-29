"""Nussutto Mofu (ぬすっとモフ) - mischievous cat-folk thief. Small monster.

Classic Japanese "dorobou" look: a green karakusa (arabesque) cloth tied over the head (ears poking through, knot under
the chin) and a karakusa furoshiki bundle on its back.

* 'loot' bone (direct child of 'body'): MofuRenderer draws the stolen item there, on top of the head; MofuModel raises
  both arms to hold it while the thief carries loot.
* hidden props revealed by animations: 'lids' (closed eyes: sleep / death / stun blink), 'bubble' (snot bubble while
  asleep), 'coin' (inside the right paw, counted in 'count').
* fur variants: nussutto_mofu.png (grey tabby), _ginger.png, _black.png.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import Model, A, M, run, fit_to_ground, keys, cyc, stamp, accent_pass, dim_under_glow, pixel_pass  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402

# ------------------------------------------------------------------ materials
M('nm_fur', ['#202127', '#34363e', '#4c4f58', '#686c76', '#878c97', '#a9aeb8'], 'fur', contrast=0.5, noise=0.035, edge=0.06,
  belly='nm_white', accent='nm_stripe', accent_mode='stripes_y', accent_freq=0.75, accent_thresh=0.76)
M('nm_stripe', ['#0f1013', '#18191d', '#24252b', '#313339', '#3f4148', '#4f525a'], 'fur', contrast=0.45, edge=0.0)
M('nm_fur_g', ['#3a1a08', '#62300f', '#8c4a1a', '#b56628', '#d8863c', '#f0aa5c'], 'fur', contrast=0.5, noise=0.035, edge=0.06,
  belly='nm_white', accent='nm_stripe_g', accent_mode='stripes_y', accent_freq=0.75, accent_thresh=0.76)
M('nm_stripe_g', ['#2a1004', '#421c08', '#5e2c0e', '#7a3c16', '#96501e', '#b06628'], 'fur', contrast=0.45, edge=0.0)
M('nm_fur_b', ['#08080a', '#101013', '#18181d', '#222228', '#2e2e36', '#3c3c46'], 'fur', contrast=0.5, noise=0.035, edge=0.05,
  belly='nm_white')
M('nm_white', ['#6e6862', '#9c958c', '#c4bdb3', '#e0d9cf', '#f2ede6', '#ffffff'], 'fur', contrast=0.4, noise=0.03, edge=0.04, base=0.62)
M('nm_cloth', ['#0b2410', '#123a19', '#1a5224', '#246c30', '#30863c', '#44a04e'], 'cloth', contrast=0.5, noise=0.03, edge=0.08)
M('nm_pink', ['#5e2a32', '#8c3f4b', '#b65a67', '#d97d89', '#efa3ad', '#ffccd2'], 'smooth', edge=0.0, base=0.6)
M('nm_coin', ['#4a3000', '#7a5200', '#b07c00', '#e0aa10', '#ffd040', '#fff09a'], 'metal', edge=0.0)
M('nm_bubble', ['#5a8aa8', '#7aaccb', '#9ccbe4', '#c0e2f2', '#e0f2fa', '#ffffff'], 'smooth', edge=0.0, base=0.62)

FUR, WHITE, CLOTH = 'nm_fur', 'nm_white', 'nm_cloth'

m = Model('nussutto_mofu')
m.config.update({'head_bones': [['head', 0.8]], 'walk_speed': 3.6, 'run_speed': 0.55, 'walk_scale': 2.6})

# ------------------------------------------------------------------ body
body = m.bone('body', (0, 18, 0), (0, 0, 0))
torso = body.cube((-2.5, -2, -2), (5, 4, 4), FUR)
chest_knot = body.cube((-1, -2, -2.6), (2, 2, 1), CLOTH)                         # furoshiki corners tied on the chest
strap_l = body.cube((1, -2.5, -2.3), (1, 1, 5), CLOTH)                           # cloth over the shoulders
body.cube((-2, -2.5, -2.3), (1, 1, 5), CLOTH, mirror_of=strap_l)

# ------------------------------------------------------------------ head (big, chibi)
head = body.bone('head', (0, -2, -0.5), (0, 0, 0))
skull = head.cube((-4, -7, -3), (8, 7, 6), FUR)
muzzle = head.cube((-2, -2.5, -3.8), (4, 2, 1), WHITE)
# karakusa hood: over the top and back of the head, tied down the cheeks and under the chin
hood = head.cube((-4.5, -8, -3.5), (9, 3, 7), CLOTH)
tie_l = head.cube((4, -5, -3.4), (1, 5, 1), CLOTH)
head.cube((-5, -5, -3.4), (1, 5, 1), CLOTH, mirror_of=tie_l)
chin = head.cube((-4, -0.6, -3.4), (8, 1, 1), CLOTH, inflate=-0.05)
back = head.cube((-4, -5, 2.5), (8, 3, 1), CLOTH)
knot = head.bone('knot', (0, 0.2, -3.4), (0, 0, 0))
knot_c = knot.cube((-1, -0.8, -1.2), (2, 2, 2), CLOTH, inflate=-0.1)
flap = knot.bone('knot_l', (1, 0.5, -0.6), (0, -20, 30))
flap.cube((0, -0.5, -0.5), (3, 1, 1), CLOTH)
mirror_tree(knot, flap)

# ears poke through the hood
ear = head.bone('ear_l', (2.4, -7.8, -0.5), (0, 0, 10))
ear.cube((-2, -1, -0.5), (4, 1, 1), FUR)                                         # stepped triangle leaning outward
ear.cube((-1, -2, -0.5), (3, 1, 1), FUR)
ear.cube((0.5, -3, -0.5), (1, 1, 1), FUR)
ear.cube((-1, -1, -0.56), (2, 1, 0), 'nm_pink')                                   # pink inner ear (planes)
ear.cube((0, -2, -0.56), (1, 1, 0), 'nm_pink')
mirror_tree(head, ear)

# closed eyelids (hidden inside the head; slid forward in sleep / death)
lids = head.bone('lids', (0, -4, -2.2), (0, 0, 0))
lid_l = lids.cube((1, -1, -0.5), (2, 3, 1), FUR)
lids.cube((-3, -1, -0.5), (2, 3, 1), FUR, mirror_of=lid_l)
# snot bubble (hidden inside the head)
bubble = head.bone('bubble', (0.5, -2, -1.5), (0, 0, 0))
bubble.cube((-1, -1, -1), (2, 2, 2), 'nm_bubble', inflate=-0.2)

# face pixel art: shifty eyes (both pupils glancing to one side), pink nose, "w" mouth
EYE = {'Y': ('#c8d438', '#8c9a22'), 'G': ('#eef49a', '#b4c050'), 'K': '#0c0a08', 'L': '#14151a'}
stamp(m, skull, 'north', 0, 2, ['.LL..LL.', '.GK..GK.', '.YK..YK.'], EYE)
stamp(m, muzzle, 'north', 0, 0, ['.PP.', '.KK.'], {'P': '#e07f8e', 'K': '#8a7a74'})
stamp(m, skull, 'north', 0, 4, ['W......W', 'WW....WW', 'WW....WW'], {'W': '#e0d9cf'})        # white cheeks
stamp(m, lid_l, 'north', 0, 1, ['KK'], {'K': '#0e0e10'})

# ------------------------------------------------------------------ arms
arm = body.bone('arm_l', (3.5, -1.5, 0), (0, 0, -8))
arm_c = arm.cube((-1, -0.5, -1), (2, 6, 2), FUR, decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.3, 'mat': WHITE}])
mirror_tree(body, arm)
coin = m.bones['arm_r'].bone('coin', (0, 4.5, 0), (0, 0, 0))
coin.cube((-1, -1, -0.5), (2, 2, 1), 'nm_coin', inflate=-0.3)

# ------------------------------------------------------------------ legs
leg = body.bone('leg_l', (1.3, 2, 0), (0, 0, 0))
leg.cube((-1, -0.5, -1), (2, 4, 2), FUR)
leg.cube((-1, 2.5, -2), (2, 1, 3), WHITE, inflate=0.05)
mirror_tree(body, leg)

# ------------------------------------------------------------------ tail (curls up behind the bundle)
TAIL = []
parent, piv = body, (0, 1.5, 1.8)
for i, (ln, rx) in enumerate(((4, 40), (3, 24), (3, 26), (3, 30))):
    b = parent.bone('tail%d' % (i + 1), piv, (rx, -38 if i == 0 else 0, 0))
    mat = WHITE if i == 3 else FUR
    b.cube((-1, -1, 0), (2, 2, ln + 0.0), mat, inflate=-0.05 * i)
    TAIL.append(b.name)
    parent, piv = b, (0, 0, ln - 0.3)

# ------------------------------------------------------------------ karakusa bundle on the back
bundle = body.bone('bundle', (0, -0.5, 2), (6, 0, 0))
sack = bundle.cube((-3, -3, 0), (6, 5, 4), CLOTH)
bundle.cube((-2.5, -4, 0.5), (5, 1, 3), CLOTH)
bknot = bundle.bone('bundle_knot', (0, -4, 2), (0, 0, 0))
bflap = bknot.bone('bundle_knot_l', (0.5, 0, 0), (0, 0, 28))
bflap.cube((-0.5, -2.5, -0.5), (1, 3, 1), CLOTH)
mirror_tree(bknot, bflap)
bknot.cube((-1, -1, -1), (2, 1, 2), CLOTH)

# loot anchor (renderer): on top of the head
body.bone('loot', (0, -12.5, -0.5), (0, 0, 0))

CLOTH_CUBES = [c for c in m.all_cubes() if c.mat == CLOTH and c.mirror_of is None]


# ------------------------------------------------------------------ karakusa motif (post pass)
MOTIF = ['.WWW..',
         'W...W.',
         'W.W.W.',
         'W..W..',
         '.W....',
         '......']
CURL = ['.WW...',          # small curl for thin faces
        'W.W...',
        '.W....']


def karakusa(model, cubes):
    """White arabesque spirals on the green cloth, one spiral centred on every face."""
    def fn(img, glow):
        for cube in cubes:
            for face, (u0, v0, w, h) in cube.face_rects().items():
                if w < 2 or h < 2:
                    continue
                big = w >= 5 and h >= 5
                tile = MOTIF if big else CURL
                th = len(tile)
                ox = (2 - (w - 1) // 2) % 6 if big else (1 - (w - 1) // 2 % 3) % 6
                oy = (2 - (h - 1) // 2) % 6 if big else ((1 - (h - 1) // 2) % th)
                for j in range(h):
                    for i in range(w):
                        if not big and w < 3 and h < 3:
                            continue
                        if tile[(j + oy) % th][(i + ox) % 6] != 'W':
                            continue
                        px = img[v0 + j, u0 + i]
                        if px[3] == 0:
                            continue
                        lum = (int(px[0]) * 3 + int(px[1]) * 6 + int(px[2])) / 10.0
                        k = min(1.0, 0.74 + lum / 170.0)
                        px[:3] = [int(232 * k), int(242 * k), int(220 * k)]
    pixel_pass(model, fn)


karakusa(m, CLOTH_CUBES)


# white chest / belly patch
def bib(P, L, N, f, d, r, face):
    if face != 'north':
        return None
    return (np.abs(r - 0.5) < 0.32) & (d > 0.25)


accent_pass(m, [torso], WHITE, bib)

dim_under_glow(m, 0.45)
fit_to_ground(m, body)

# ------------------------------------------------------------------ animations
T = m.anim


def arms(a, pts_l, pts_r=None):
    """Arm rotation tracks; pts_r defaults to the mirror of pts_l (Y/Z negated)."""
    keys(a, 'arm_l', 'rotation', pts_l)
    keys(a, 'arm_r', 'rotation', pts_r if pts_r is not None else [(t, (x, -y, -z)) for t, (x, y, z) in pts_l])


def ears(a, pts_l):
    keys(a, 'ear_l', 'rotation', pts_l)
    keys(a, 'ear_r', 'rotation', [(t, (x, -y, -z)) for t, (x, y, z) in pts_l])


def tail_wave(a, amp=(4, 10, 0), period=None, phase_step=0.7, growth=1.1):
    A.chain_wave(a, TAIL, amp, period=period, phase_step=phase_step, growth=growth)


def steps(a, period, amp, lift, phase=0.0, n=16):
    for leg, ph in (('leg_l', 0.0), ('leg_r', math.pi)):
        A.sample(a, leg, 'rotation', lambda t, ph=ph: (amp * cyc(t, period, ph + phase), 0, 0), n)
        A.sample(a, leg, 'position', lambda t, ph=ph: (0, -lift * max(0.0, cyc(t, period, ph + phase - math.pi / 2)), 0), n)


# idle: breathing, ear flicks, sly glances, tail curling
a = T('idle', 2.4, True)
A.sine(a, 'body', 'position', (0, 0.25, 0), period=1.2)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.65, (0, 10, 3)), (1.2, (0, 10, 3)), (1.35, (0, -6, -2)), (2.0, (0, -6, -2)), (2.4, (0, 0, 0))])
keys(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (1.5, (0, 0, 0)), (1.58, (0, 0, 22)), (1.7, (0, 0, 0)), (2.4, (0, 0, 0))])
keys(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, 0)), (0.38, (0, 0, -22)), (0.5, (0, 0, 0)), (2.4, (0, 0, 0))])
A.sine(a, 'arm_l', 'rotation', (3, 0, 2), period=2.4)
A.sine(a, 'arm_r', 'rotation', (3, 0, -2), period=2.4, phase=1.0)
A.sine(a, 'knot_l', 'rotation', (0, 6, 0), period=1.2)
A.sine(a, 'knot_r', 'rotation', (0, -6, 0), period=1.2, phase=0.6)
tail_wave(a, (5, 9, 0), phase_step=0.6)

# walk: exaggerated cartoon tiptoe, paws held up in front
a = T('walk', 0.8, True)
steps(a, 0.8, 30, 1.6)
A.sample(a, 'body', 'position', lambda t: (0, -0.8 * abs(cyc(t, 0.8)), 0), 16)
A.sample(a, 'body', 'rotation', lambda t: (8, 4 * cyc(t, 0.8), 3 * cyc(t, 0.8)), 16)
A.sample(a, 'head', 'rotation', lambda t: (-6, -5 * cyc(t, 0.8), -3 * cyc(t, 0.8)), 16)
arms(a, [(0, (-58, 0, -6)), (0.2, (-64, 0, -8)), (0.4, (-58, 0, -6)), (0.6, (-52, 0, -4)), (0.8, (-58, 0, -6))],
     [(0, (-58, 0, 6)), (0.2, (-52, 0, 4)), (0.4, (-58, 0, 6)), (0.6, (-64, 0, 8)), (0.8, (-58, 0, 6))])
tail_wave(a, (4, 12, 0), period=0.8, phase_step=0.6)

# run: quick scamper, leaning forward, arms pumping, tail streaming back
a = T('run', 0.45, True)
steps(a, 0.45, 55, 2.2, n=12)
A.sample(a, 'body', 'position', lambda t: (0, -1.1 * abs(cyc(t, 0.45)), 0), 12)
A.sample(a, 'body', 'rotation', lambda t: (16, 6 * cyc(t, 0.45), 0), 12)
A.hold(a, 'head', 'rotation', (-12, 0, 0))
A.sample(a, 'arm_l', 'rotation', lambda t: (-55 * cyc(t, 0.45), 0, -10), 12)
A.sample(a, 'arm_r', 'rotation', lambda t: (55 * cyc(t, 0.45), 0, 10), 12)
ears(a, [(0, (-25, 0, 12)), (0.45, (-25, 0, 12))])
A.sample(a, 'tail1', 'rotation', lambda t: (-38, 10 * cyc(t, 0.45), 0), 12)
A.chain_wave(a, TAIL[1:], (6, 8, 0), period=0.45, phase_step=0.7)
A.sample(a, 'bundle', 'rotation', lambda t: (3 * cyc(t, 0.225), 0, 2 * cyc(t, 0.45)), 12)

# steal (24 ticks, lunge 6-11, grab at tick 11): sly crouch, dash, snatch, yank it back
a = T('steal', 1.2)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.28, (18, -10, 0)), (0.5, (26, 6, 0)), (0.6, (20, 8, 0)), (0.8, (-8, -6, 0)), (1.2, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.28, (0, 1.5, 1)), (0.5, (0, 0.5, -1.5)), (0.8, (0, -0.5, 0.5)), (1.2, (0, 0, 0))])
keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.28, (40, 0, 10)), (0.5, (-100, 0, -10)), (0.58, (-96, 0, -14)), (0.8, (-40, 0, -30)), (1.2, (0, 0, 0))])
keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.28, (-30, 0, -10)), (0.5, (30, 0, -10)), (0.8, (-20, 0, -10)), (1.2, (0, 0, 0))])
keys(a, 'leg_l', 'rotation', [(0, (0, 0, 0)), (0.28, (-20, 0, 0)), (0.5, (-40, 0, 0)), (0.8, (-10, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'leg_r', 'rotation', [(0, (0, 0, 0)), (0.28, (20, 0, 0)), (0.5, (35, 0, 0)), (0.8, (10, 0, 0)), (1.2, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.28, (-10, 12, 6)), (0.5, (-16, 0, 0)), (0.8, (4, -18, -6)), (1.2, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.28, (-20, 0, 20)), (0.6, (-20, 0, 20)), (0.9, (0, 0, 0))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.28, (20, 0, 0)), (0.5, (-30, 0, 0)), (1.2, (0, 0, 0))])

# swipe (16 ticks, hit at tick 7): rears a paw up and rakes it down across
a = T('swipe', 0.8)
keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.25, (-150, 0, -30)), (0.32, (-120, 0, -10)), (0.4, (-30, 0, 30)), (0.5, (-20, 0, 34)), (0.8, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (-6, -18, 0)), (0.38, (12, 20, 0)), (0.5, (10, 16, 0)), (0.8, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, -0.5, 0.5)), (0.38, (0, 0.5, -1.2)), (0.8, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.25, (-8, 10, 0)), (0.38, (6, -12, 0)), (0.8, (0, 0, 0))])
keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.25, (20, 0, 10)), (0.4, (-20, 0, 10)), (0.8, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.2, (-25, 0, 25)), (0.6, (-25, 0, 25)), (0.8, (0, 0, 0))])

# cheer: hops with both arms thrown up
a = T('cheer', 0.8)
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.12, (0, 1, 0)), (0.32, (0, -4, 0)), (0.52, (0, 0.6, 0)), (0.64, (0, -0.8, 0)), (0.8, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.15, (-165, 0, 18)), (0.4, (-172, 0, 24)), (0.6, (-160, 0, 16)), (0.8, (0, 0, 0))])
keys(a, 'leg_l', 'rotation', [(0, (0, 0, 0)), (0.32, (-25, 0, -8)), (0.52, (0, 0, 0))])
keys(a, 'leg_r', 'rotation', [(0, (0, 0, 0)), (0.32, (20, 0, 8)), (0.52, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-16, 0, 6)), (0.6, (-8, 0, -4)), (0.8, (0, 0, 0))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.3, (25, 0, 0)), (0.8, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.3, (0, 0, -12)), (0.8, (0, 0, 0))])

# look (40 ticks): paw shading the eyes, peering left and right
a = T('look', 2.0)
keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-150, 0, 34)), (1.7, (-150, 0, 34)), (2.0, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-6, 0, 0)), (0.5, (-6, 36, 6)), (0.9, (-6, 36, 6)), (1.15, (-6, -36, -6)), (1.55, (-6, -36, -6)), (1.8, (0, 0, 0))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (8, 0, 0)), (0.5, (8, 10, 0)), (0.9, (8, 10, 0)), (1.15, (8, -10, 0)), (1.55, (8, -10, 0)), (1.8, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, 0.8, 0)), (1.7, (0, 0.8, 0)), (2.0, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.5, (-15, 0, -8)), (1.55, (-15, 0, -8)), (2.0, (0, 0, 0))])
tail_wave(a, (3, 14, 0), period=1.0, phase_step=0.7)

# count (40 ticks): flips a gold coin out of its paw, counting with a smug nod
a = T('count', 2.0)
arms(a, [(0, (0, 0, 0)), (0.25, (-62, 0, 16)), (1.75, (-62, 0, 16)), (2.0, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.25, (18, 0, 0)), (0.55, (24, 0, 6)), (0.85, (18, 0, 0)), (1.15, (24, 0, -6)), (1.45, (18, 0, 0)), (1.75, (18, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'coin', 'position', [(0, (0, 0, 0)), (0.25, (0, 0, 0)), (0.35, (0, 2.4, -1)), (0.5, (0, 0.5, -5)), (0.65, (0, 2.4, -1)), (0.8, (0, 0.5, -5)), (0.95, (0, 2.4, -1)),
                             (1.1, (0, 0.5, -5)), (1.25, (0, 2.4, -1)), (1.6, (0, 2.4, -1)), (1.75, (0, 0, 0)), (2.0, (0, 0, 0))])
keys(a, 'coin', 'rotation', [(0, (0, 0, 0)), (0.35, (0, 0, 0)), (0.5, (180, 0, 0)), (0.65, (360, 0, 0)), (0.8, (540, 0, 0)), (0.95, (720, 0, 0)), (1.1, (900, 0, 0)),
                             (1.25, (1080, 0, 0)), (2.0, (1080, 0, 0))], interp='l')
tail_wave(a, (4, 16, 0), period=0.6, phase_step=0.7)
ears(a, [(0, (0, 0, 0)), (0.3, (0, 0, 10)), (1.7, (0, 0, 10)), (2.0, (0, 0, 0))])

# flinch: jolts back, arms flung out, ears flat
a = T('flinch', 0.7)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-16, 8, 6)), (0.35, (4, -3, -2)), (0.7, (0, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -1, 1.5)), (0.7, (0, 0, 0))])
arms(a, [(0, (0, 0, 0)), (0.1, (-60, 0, 40)), (0.45, (-10, 0, 8)), (0.7, (0, 0, 0))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.1, (-20, 10, 0)), (0.4, (6, -4, 0)), (0.7, (0, 0, 0))])
ears(a, [(0, (0, 0, 0)), (0.1, (-30, 0, 40)), (0.55, (-10, 0, 10)), (0.7, (0, 0, 0))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.1, (30, 0, 0)), (0.7, (0, 0, 0))])
keys(a, 'lids', 'position', [(0, (0, 0, 0)), (0.06, (0, 0, -1.0)), (0.3, (0, 0, -1.0)), (0.36, (0, 0, 0))])

# burrow (special state, 30 ticks then the thief is gone): frantic digging, then a head-first dive
a = T('burrow', 1.5)
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, 0.6, -0.5)), (0.5, (0, 1.5, -0.5)), (0.85, (0, 5, -0.5)), (0.98, (0, 1, -1)), (1.12, (0, 7, -1)), (1.45, (0, 24, -1)), (1.5, (0, 24, -1))])
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (26, 0, 0)), (0.85, (34, 0, 0)), (1.1, (150, 0, 0)), (1.5, (170, 0, 0))])
A.sample(a, 'arm_l', 'rotation', lambda t: ((-60 - 45 * cyc(t, 0.14)) if 0.2 < t < 0.95 else (-40 if t <= 0.2 else -170), 0, -10), 40)
A.sample(a, 'arm_r', 'rotation', lambda t: ((-60 + 45 * cyc(t, 0.14)) if 0.2 < t < 0.95 else (-40 if t <= 0.2 else -170), 0, 10), 40)
A.sample(a, 'leg_l', 'rotation', lambda t: (-30 * cyc(t, 0.16) if t > 1.0 else -26 * min(1.0, t / 0.25), 0, -6 * min(1.0, t / 0.25)), 30)
A.sample(a, 'leg_r', 'rotation', lambda t: (30 * cyc(t, 0.16) if t > 1.0 else -26 * min(1.0, t / 0.25), 0, 6 * min(1.0, t / 0.25)), 30)
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.25, (20, 0, 0)), (1.5, (20, 0, 0))])
A.sample(a, 'tail1', 'rotation', lambda t: (10, 25 * cyc(t, 0.3), 0), 20)
ears(a, [(0, (0, 0, 0)), (0.25, (-30, 0, 30)), (1.5, (-30, 0, 30))])

# death: knocked out flat on its back (on the bundle), limbs in the air, eyes shut
a = T('death', 1.4)
keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (8, 0, 10)), (0.45, (-6, 0, -8)), (0.95, (-90, 0, 0)), (1.08, (-84, 0, 0)), (1.2, (-90, 0, 0)), (1.4, (-90, 0, 0))])
keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.45, (0, -0.5, 0)), (0.95, (0, -1.2, 2.5)), (1.4, (0, -1.2, 2.5))])
keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.25, (10, 0, 14)), (0.45, (-10, 0, -12)), (0.95, (-22, 0, 8)), (1.4, (-24, 0, 10))])
arms(a, [(0, (0, 0, 0)), (0.45, (-40, 0, 30)), (0.95, (-150, 0, 50)), (1.4, (-160, 0, 56))])
keys(a, 'leg_l', 'rotation', [(0, (0, 0, 0)), (0.95, (-60, 0, -14)), (1.4, (-70, 0, -18))])
keys(a, 'leg_r', 'rotation', [(0, (0, 0, 0)), (0.95, (-40, 0, 14)), (1.4, (-48, 0, 18))])
keys(a, 'lids', 'position', [(0, (0, 0, 0)), (0.4, (0, 0, 0)), (0.45, (0, 0, -1.0)), (1.4, (0, 0, -1.0))])
ears(a, [(0, (0, 0, 0)), (0.95, (-20, 0, 40)), (1.4, (-20, 0, 44))])
keys(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (1.0, (-40, -46, 0)), (1.4, (-40, -48, 0))])
for i, tb in enumerate(TAIL[1:]):
    keys(a, tb, 'rotation', [(0, (0, 0, 0)), (1.0, (-22, 10, 0)), (1.4, (-24, 12, 0))])

# sleep: sits, nodding off, snot bubble swelling with every breath
a = T('sleep', 3.0, True)
A.hold(a, 'body', 'position', (0, 2.4, 0.5))
A.hold(a, 'body', 'rotation', (-8, 0, 0))
keys(a, 'leg_l', 'rotation', [(0, (-82, 6, 0)), (3.0, (-82, 6, 0))])
keys(a, 'leg_r', 'rotation', [(0, (-82, -6, 0)), (3.0, (-82, -6, 0))])
A.sample(a, 'head', 'rotation', lambda t: (22 + 6 * cyc(t, 1.5), 0, 8), 16)
arms(a, [(0, (-58, 0, 14)), (3.0, (-58, 0, 14))])
A.hold(a, 'lids', 'position', (0, 0, -1.0))
A.hold(a, 'bubble', 'position', (0.6, 0.4, -2.8))
A.sample(a, 'bubble', 'scale', lambda t: (0.75 + 0.6 * max(0.0, cyc(t, 1.5)),) * 3, 16)
ears(a, [(0, (0, 0, 24)), (3.0, (0, 0, 24))])
A.hold(a, 'tail1', 'rotation', (-30, -40, 0))
A.chain_wave(a, TAIL[1:], (0, 4, 0), period=3.0, phase_step=0.5)

# stun: dizzy sway, head lolling in circles
a = T('stun', 1.0, True)
A.sine(a, 'body', 'rotation', (0, 0, 8), period=1.0)
A.sample(a, 'head', 'rotation', lambda t: (10 * math.sin(t * 2 * math.pi), 0, 14 * math.cos(t * 2 * math.pi)), 16)
arms(a, [(0, (8, 0, 18)), (0.5, (14, 0, 24)), (1.0, (8, 0, 18))])
ears(a, [(0, (0, 0, 30)), (1.0, (0, 0, 30))])
A.hold(a, 'body', 'position', (0, 0.6, 0))
tail_wave(a, (2, 6, 0), period=1.0)

# trapped (not used by small monsters, kept for completeness): flailing
a = T('trapped', 0.6, True)
A.sample(a, 'arm_l', 'rotation', lambda t: (-120 + 40 * cyc(t, 0.3), 0, 20), 10)
A.sample(a, 'arm_r', 'rotation', lambda t: (-120 + 40 * cyc(t, 0.3, 1.6), 0, -20), 10)
steps(a, 0.3, 30, 0.0, n=10)
A.sine(a, 'body', 'rotation', (0, 10, 4), period=0.6)

if __name__ == '__main__':
    run(m, variants={'ginger': {'nm_fur': 'nm_fur_g'}, 'black': {'nm_fur': 'nm_fur_b'}})
