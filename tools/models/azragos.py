"""Azragos (冥炎龍アズラゴス) - elder dragon of the netherflame, dragon element. Final boss (module large.b).

Regal six-limbed dragon: four legs plus great wings, a crown of swept-back horns, violet-black scales with
glowing magenta rune veins (emissive), a long tail ending in a crossed sword blade. A hidden emissive
"nova core" in front of the chest grows during the SUPERNOVA charge.
Run from tools/:  python3 models/azragos.py --preview [anim ...]
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
from hcmodel.parts import mirror_tree, head_eyes, tail_chain, spike_plane  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import hcmodel.paint as PAINT  # noqa: E402

# ------------------------------------------------------------------ emissive fixes (script-local patches of the painter)
# (the same fixes are in core paint.py since origin/huntcraft c855aa5; these patches are harmless on top of it)
# 1) paint.py bug: a glowing ACCENT material on a non-glowing base copies the accent's glow onto the WHOLE face
#    ("gl = agl if gl is None"), so every rune-veined face became fully emissive (84 % of the body glowed pink in game:
#    the _glow layer is drawn additively and full-bright). Returning an empty glow array instead of None makes the
#    accent mask apply.
_orig_shade = PAINT._shade


def _shade_masked(*args, **kwargs):
    rgb, gl = _orig_shade(*args, **kwargs)
    if gl is None:
        gl = np.zeros(rgb.shape[:2] + (4,))
    return rgb, gl


PAINT._shade = _shade_masked
# 2) never glow where the base texture is cut out (the glow layer would light up the holes of membranes / blades)
_orig_paint_model = PAINT.paint_model


def _paint_model_masked(model, seed=1):
    img, glow = _orig_paint_model(model, seed)
    if glow is not None:
        g = np.array(glow)
        g[np.asarray(img)[..., 3] < 26] = 0
        glow = Image.fromarray(g, 'RGBA')
    return img, glow


PAINT.paint_model = _paint_model_masked

# ------------------------------------------------------------------ materials (module-local)
M('azg_scale', ['#0b0714', '#190f2a', '#281842', '#3b235c', '#523279', '#6e4898'], 'scales', scale=1.5, belly='azg_belly',
  accent='azg_rune', accent_mode='veins3d', accent_freq=0.16, accent_thresh=0.975)
M('azg_plate', ['#08050d', '#150c21', '#241537', '#36204f', '#4c2e6c', '#67428e'], 'plates', belly='azg_belly',
  accent='azg_rune', accent_mode='veins3d', accent_freq=0.13, accent_thresh=0.98)
M('azg_belly', ['#1c0d17', '#311726', '#4a2337', '#663249', '#84455d', '#a25e75'], 'plates', scale=0.7)
M('azg_rune', ['#4a0c3a', '#86125e', '#c21f7d', '#e9409c', '#ff78c0', '#ffb3df'], 'smooth', glow='#ff5fbf')
M('azg_wing', ['#0b0510', '#1b0d24', '#2e163b', '#432155', '#5c2f72', '#7a4593'], 'membrane',
  accent='azg_rune', accent_mode='veins3d', accent_freq=0.1, accent_thresh=0.982)
M('azg_horn', ['#0c0a0e', '#1c181f', '#302a34', '#4a414f', '#6a5f70', '#8f8396'], 'horn')
M('azg_claw', ['#0a080c', '#18141b', '#2a242e', '#3f3744', '#594f5f', '#7a6f80'], 'horn')

m = Model('azragos')
m.config.update({'head_bones': [['neck1', 0.2], ['neck2', 0.25], ['neck3', 0.25], ['head', 0.3]], 'walk_speed': 2.0, 'run_speed': 0.55, 'walk_scale': 2.6})
AIR_ANIMS = ('fly_orb', 'meteor_rain', 'fly_roar', 'fly_flinch')
PREVIEW_FLY_SWITCH = {'takeoff': (0.6, True), 'land': (1.0, False), 'dive': (1.65, False)}

# ------------------------------------------------------------------ body
body = m.bone('body', (0, -20, 4), (-6, 0, 0))
body.cube((-15, -13, -24), (30, 26, 42), 'azg_scale', faces={'top': 'azg_plate'}, decals=[{'type': 'belly_band'}])
body.cube((-14, -11, -32), (28, 24, 9), 'azg_scale', faces={'top': 'azg_plate'}, decals=[{'type': 'belly_band'}])    # chest
body.cube((-13, -11, 17), (26, 21, 9), 'azg_scale', faces={'top': 'azg_plate'}, decals=[{'type': 'belly_band'}])     # hips
body.cube((-12, 10, -29), (24, 5, 38), 'azg_belly')                                                                  # belly plates
body.cube((-16.5, -6, -16), (33, 15, 24), 'azg_scale', decals=[{'type': 'belly_band'}])                               # rib bulge
body.cube((-6, -16, -29), (12, 4, 46), 'azg_plate')                                                                  # dorsal ridge
body.cube((-1, -16.5, -27), (2, 1, 42), 'azg_rune')                                                                  # glowing spine line
for i, z in enumerate((-25, -16, -7, 2, 11)):
    spike_plane(body, 0, -16, z, (9, 10, 10, 9, 7)[i], 8, 'azg_horn', 'azg_rune', 0.3)

# ------------------------------------------------------------------ neck & head (S-curve: rising, then arching forward)
neck1 = body.bone('neck1', (0, -6, -30), (-24, 0, 0))
neck1.cube((-8, -8, -13), (16, 16, 15), 'azg_scale', faces={'top': 'azg_plate'})
neck1.cube((-5, 6, -12), (10, 3, 13), 'azg_belly')
spike_plane(neck1, 0, -8, -6, 7, 7, 'azg_horn', 'azg_rune', 0.3)
neck2 = neck1.bone('neck2', (0, -1, -11), (-4, 0, 0))
neck2.cube((-7, -7, -13), (14, 14, 14), 'azg_scale', faces={'top': 'azg_plate'})
neck2.cube((-4.5, 5, -12), (9, 3, 12), 'azg_belly')
spike_plane(neck2, 0, -7, -6, 6, 6, 'azg_horn', 'azg_rune', 0.3)
neck3 = neck2.bone('neck3', (0, -1, -11), (18, 0, 0))
neck3.cube((-6, -6, -12), (12, 12, 13), 'azg_scale', faces={'top': 'azg_plate'})
neck3.cube((-4, 4, -11), (8, 3, 11), 'azg_belly')
spike_plane(neck3, 0, -6, -5, 5, 6, 'azg_horn', 'azg_rune', 0.3)

head = neck3.bone('head', (0, -1, -10), (16, 0, 0))
skull = head.cube((-8, -8, -15), (16, 12, 17), 'azg_plate')
head_eyes(skull, '#ffd6f4', '#ff5fd0', at=(0.8, 0.42), size=3)
head.cube((-6, -5, -27), (12, 8, 13), 'azg_scale', faces={'top': 'azg_plate'}, decals=[{'type': 'nostrils', 'at': 0.3}])
head.cube((-5.5, 3, -26), (11, 1.5, 11), 'teeth', decals=[{'type': 'teeth', 'face': 'west'}, {'type': 'teeth', 'face': 'east'}])
head.cube((-9, -10, -14), (18, 3, 9), 'azg_plate')                              # brow ridge
head.cube((-3, -7, -28), (6, 3, 8), 'azg_plate')                                # nasal ridge
head.cube((-1, -8, -26), (2, 1, 20), 'azg_rune')                                # glowing midline rune
head.cube((8.3, -3, -13), (0.5, 1, 9), 'azg_rune')                              # cheek runes
head.cube((-8.8, -3, -13), (0.5, 1, 9), 'azg_rune')
spike_plane(head, 0, -10, -2, 6, 8, 'azg_horn', 'azg_rune', 0.3)
# crown of swept-back horns
horn_l = head.bone('horn_l', (6, -8, -6), (18, 16, 0))
horn_l.cube((-2.5, -2.5, 0), (5, 5, 17), 'azg_horn')
horn_tl = horn_l.bone('horn_tip_l', (0, 0, 16), (14, 4, 0))
horn_tl.cube((-1.5, -1.5, 0), (3, 3, 14), 'azg_horn', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.3, 'mat': 'azg_rune'}])
horn2_l = head.bone('horn2_l', (8, -4, -3), (8, 42, 0))
horn2_l.cube((-1.5, -1.5, 0), (3, 3, 11), 'azg_horn')
horn2_tl = horn2_l.bone('horn2_tip_l', (0, 0, 10), (14, -6, 0))
horn2_tl.cube((-1, -1, 0), (2, 2, 9), 'azg_horn', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.35, 'mat': 'azg_rune'}])
horn3_l = head.bone('horn3_l', (8, 1, -6), (-6, 28, 0))
horn3_l.cube((-1, -1, 0), (2, 2, 10), 'azg_horn', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.4, 'mat': 'azg_rune'}])
for b in (horn_l, horn2_l, horn3_l):
    mirror_tree(head, b)
jaw = head.bone('jaw', (0, 4, -6))
jaw.cube((-7, 0, -21), (14, 4, 22), 'azg_scale', faces={'top': 'mouth'})
jaw.cube((-5.5, -1, -20), (11, 1, 16), 'teeth')
jaw.cube((-3, 0.5, -17), (6, 1, 11), 'tongue')
jaw.cube((-2, 3, -19), (4, 3, 8), 'azg_plate')                                  # chin

# ------------------------------------------------------------------ forelegs
arm_l = body.bone('arm_l', (12, 0, -22), (10, 0, -4))
arm_l.cube((-5.5, -4, -6.5), (11, 20, 13), 'azg_scale')
paul_l = arm_l.bone('pauldron_l', (5.5, -2, 0), (0, 0, -10))
paul_l.cube((-1, -6, -8), (4, 13, 16), 'azg_plate', decals=[{'type': 'band', 'axis': 'down', 'range': (0.7, 0.78), 'mat': 'azg_rune'}])
elb_l = arm_l.bone('elbow_spike_l', (0, 14, 5.5), (-60, 0, 0))
elb_l.cube((-1.5, -1.5, 0), (3, 3, 9), 'azg_horn', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.35, 'mat': 'azg_rune'}])
fore_l = arm_l.bone('forearm_l', (0, 15, 0), (-22, 0, 4))
fore_l.cube((-4.5, -1, -4.5), (9, 18, 9), 'azg_scale')
fplate_l = fore_l.bone('fore_plate_l', (0, 2, -4.5))
fplate_l.cube((-3.5, -1, -2), (7, 12, 2), 'azg_plate', decals=[{'type': 'band', 'axis': 'down', 'range': (0.45, 0.55), 'mat': 'azg_rune'}])
paw_l = fore_l.bone('forepaw_l', (0, 16, 0), (12, 0, 0))
paw_l.cube((-5.5, -1, -10), (11, 5, 12), 'azg_scale')
for k, x in enumerate((-5, -1, 3)):
    c = paw_l.bone('fclaw%d_l' % k, (x + 1, 1, -10), (14, (k - 1) * -12, 0))
    c.cube((-1, -1, -5), (2, 3, 5), 'azg_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.3, 'mat': 'bone_dark'}])
mirror_tree(body, arm_l)

# ------------------------------------------------------------------ hind legs (digitigrade)
thigh_l = body.bone('thigh_l', (12, 2, 14), (-18, 0, 0))
thigh_l.cube((-6.5, -5, -9.5), (13, 22, 18), 'azg_scale')
thigh_l.cube((5.5, -3, -6), (2, 12, 12), 'azg_plate', decals=[{'type': 'band', 'axis': 'down', 'range': (0.5, 0.58), 'mat': 'azg_rune'}])
shin_l = thigh_l.bone('shin_l', (0, 15, 2), (52, 0, 0))
shin_l.cube((-4.5, -1, -4.5), (9, 16, 9), 'azg_scale')
hfoot_l = shin_l.bone('hfoot_l', (0, 14, 0), (-34, 0, 0))
hfoot_l.cube((-4, -1, -3.5), (8, 11, 7), 'azg_scale')
hpaw_l = hfoot_l.bone('hpaw_l', (0, 9, 0), (0, 0, 0))
hpaw_l.cube((-5.5, -1, -10), (11, 4, 12), 'azg_scale')
for k, x in enumerate((-5, -1, 3)):
    c = hpaw_l.bone('hclaw%d_l' % k, (x + 1, 0.5, -10), (14, (k - 1) * -12, 0))
    c.cube((-1, -1, -5), (2, 3, 5), 'azg_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.3, 'mat': 'bone_dark'}])
mirror_tree(body, thigh_l)

# ------------------------------------------------------------------ tail + blade
TAIL = tail_chain(body, 'tail', (0, -4, 25), (8, 0, 0),
                  [(17, 15, 11, -3), (14, 12, 10, -4), (12, 10, 9, -4), (10, 8, 9, -2), (8, 7, 8, 0), (6, 5, 7, 2)], 'azg_scale')
for i, tb in enumerate(TAIL):
    h = (15, 12, 10, 8, 7, 5)[i]
    spike_plane(tb, 0, -h / 2, (11, 10, 9, 9, 8, 7)[i] * 0.5, max(3, 7 - i), 6, 'azg_horn', 'azg_rune', 0.3)
blade = TAIL[-1].bone('tail_blade', (0, 0, 7))
blade.cube((-1.5, -1.5, 0), (3, 3, 6), 'azg_horn')
bh = blade.bone('blade_h', (0, 0, 2))
bh.cube((-8, 0, 0), (16, 0, 22), 'azg_horn', decals=[{'type': 'cutout', 'shape': 'spike_back'}, {'type': 'tip', 'axis': 'back', 'frac': 0.25, 'mat': 'azg_rune'}])
bv = blade.bone('blade_v', (0, 0, 2), (0, 0, 90))
bv.cube((-6, 0, 0), (12, 0, 20), 'azg_horn', decals=[{'type': 'cutout', 'shape': 'spike_back'}, {'type': 'tip', 'axis': 'back', 'frac': 0.25, 'mat': 'azg_rune'}])
TAILN = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6']

# ------------------------------------------------------------------ wings (left built along +X; rest = folded)
FOLD = {'wing': (-40, -62, -58), 'forearm': (0, 162, 0), 'finger1': (0, 170, 0), 'finger2': (0, 158, 0), 'finger3': (0, 146, 0)}
SPREAD = {'wing': (0, 6, -14), 'forearm': (0, -14, 0), 'finger1': (0, -8, 0), 'finger2': (0, -38, 0), 'finger3': (0, -70, 0)}
wing_l = body.bone('wing_l', (13, -11, -18), FOLD['wing'])
wing_l.cube((0, -3.5, -3.5), (30, 7, 7), 'azg_scale', faces={'top': 'azg_plate'})
wing_l.cube((2, -0.5, -4.2), (26, 1, 1), 'azg_rune')                  # glowing leading edge
wing_l.cube((0, 0, 0), (30, 0, 38), 'azg_wing', decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 1.5, 'depth': 0.3, 'taper': 0.15}])
fore_wl = wing_l.bone('wforearm_l', (30, 0, 0), FOLD['forearm'])
fore_wl.cube((0, -2.5, -2.5), (34, 5, 5), 'azg_scale', faces={'top': 'azg_plate'})
fore_wl.cube((1, -0.5, -3.2), (31, 1, 1), 'azg_rune')                 # glowing leading edge
fore_wl.cube((32, -3, -3), (5, 6, 6), 'azg_horn', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'azg_rune'}])
mem_fore = fore_wl.bone('mem_fore_l', (0, 0, 0))
mem_fore.cube((0, 0, 0), (34, 0, 30), 'azg_wing', decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 2, 'depth': 0.22}])
for i, ln in enumerate((46, 40, 32)):
    fb = fore_wl.bone('finger%d_l' % (i + 1), (34, 0, 0), FOLD['finger%d' % (i + 1)])
    fb.cube((0, -1.5, -1.5), (ln, 3, 3), 'azg_plate')
    mb = fb.bone('mem_f%d_l' % (i + 1), (0, 0, 0))
    mb.cube((0, 0, 0), (ln, 0, 24 if i < 2 else 30), 'azg_wing',
            decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 2 if i < 2 else 1.5, 'depth': 0.42}])
mirror_tree(body, wing_l)
MEMS = ['mem_fore', 'mem_f1', 'mem_f2', 'mem_f3']

# ------------------------------------------------------------------ nova core (hidden; grows during supernova)
core = body.bone('nova_core', (0, -2, -40))
core.cube((-4, -4, -4), (8, 8, 8), 'azg_rune')
core.cube((-10, -1, -1), (20, 2, 2), 'azg_rune')
core.cube((-1, -10, -1), (2, 20, 2), 'azg_rune')
core.cube((-1, -1, -10), (2, 2, 20), 'azg_rune')
HIDE = (0.01, 0.01, 0.01)


def woff(part, side, extra=(0, 0, 0)):
    """Animation offset taking a wing bone from its folded rest pose to SPREAD (+ extra)."""
    f, sp = FOLD[part], SPREAD[part]
    g = 1 if side == 'l' else -1
    return (sp[0] - f[0] + extra[0], g * (sp[1] - f[1] + extra[1]), g * (sp[2] - f[2] + extra[2]))


def wname(part, side):
    return {'wing': 'wing_', 'forearm': 'wforearm_'}.get(part, part + '_') + side


def fold_membranes(anim, amount=0.88):
    """Collapse the membranes (folded wings). Use in every ground loop / state pose."""
    for side in ('l', 'r'):
        for mm in MEMS:
            A.hold(anim, '%s_%s' % (mm, side), 'scale', (1, 1, 1 - amount))


def hide_core(anim):
    A.hold(anim, 'nova_core', 'scale', HIDE)


T = m.anim
NECK = ['neck1', 'neck2', 'neck3']
LEGS = {'fore': ('arm', 'forearm', 'forepaw'), 'hind': ('thigh', 'shin', 'hfoot')}


def P(anim, bone, chan, *keys):
    A.poses(anim, bone, chan, list(keys))


def lerp_keys(keys, u):
    for i in range(len(keys) - 1):
        u0, v0 = keys[i]
        u1, v1 = keys[i + 1]
        if u0 <= u <= u1:
            f = (u - u0) / (u1 - u0) if u1 > u0 else 0.0
            f = 0.5 - 0.5 * math.cos(math.pi * f)
            return tuple(a + (b - a) * f for a, b in zip(v0, v1))
    return keys[-1][1]


def keyed(keys, t):
    """Cosine interpolation through (t, values) keys at time t."""
    if t <= keys[0][0]:
        return keys[0][1]
    return lerp_keys([(k[0], k[1]) for k in keys], min(t, keys[-1][0]))


# stride keys (upper, lower, foot) x-rotations; u = 0 touch-down, ~0.55 lift-off
FORE_WALK = [(0.0, (-18, 0, 14)), (0.3, (-4, 2, 2)), (0.55, (16, 6, -12)), (0.72, (6, 40, 24)), (0.88, (-14, 14, 8)), (1.0, (-18, 0, 14))]
HIND_WALK = [(0.0, (-16, -4, 14)), (0.3, (-4, 2, 2)), (0.55, (16, 4, -14)), (0.72, (2, 30, -24)), (0.88, (-14, 12, -2)), (1.0, (-16, -4, 14))]
FORE_RUN = [(0.0, (-28, 0, 20)), (0.25, (-6, 4, 4)), (0.48, (24, 8, -16)), (0.66, (10, 56, 30)), (0.86, (-24, 20, 10)), (1.0, (-28, 0, 20))]
HIND_RUN = [(0.0, (-26, -6, 22)), (0.25, (-6, 4, 2)), (0.48, (26, 6, -20)), (0.66, (4, 44, -32)), (0.86, (-22, 16, -4)), (1.0, (-26, -6, 22))]


def gait(anim, period, phases, fore, hind, t0=0.0, t1=None, spc=16, amp=1.0):
    """phases: dict leg -> phase offset, legs 'fl','fr','hl','hr'."""
    t1 = anim.length if t1 is None else t1
    steps = max(8, int(round((t1 - t0) / period * spc)))
    for leg, ph in phases.items():
        kind = 'fore' if leg[0] == 'f' else 'hind'
        keys = fore if kind == 'fore' else hind
        side = leg[1]
        for j, bn in enumerate(LEGS[kind]):
            A.sample(anim, '%s_%s' % (bn, side), 'rotation',
                     lambda t, ph=ph, j=j, keys=keys: (amp * lerp_keys(keys, ((t - t0) / period + ph) % 1.0)[j], 0, 0),
                     steps=steps, t0=t0, t1=t1)


def leg_keys(anim, kind, side, keys):
    """keys: (t, (upper, lower, foot)) x-rotations for one leg."""
    for j, bn in enumerate(LEGS[kind]):
        P(anim, '%s_%s' % (bn, side), 'rotation', *[(t, (v[j], 0, 0)) for t, v in keys])


def tail_keys(anim, keys, growth=1.0):
    for i, b in enumerate(TAILN):
        g = growth ** i
        P(anim, b, 'rotation', *[(t, tuple(c * g for c in v)) for t, v in keys])


def neck_keys(anim, keys, w=(0.3, 0.25, 0.2, 0.25)):
    """Distributes (t, (x, y, z)) total neck+head rotation over neck1..3 + head."""
    for b, k in zip(NECK + ['head'], w):
        P(anim, b, 'rotation', *[(t, tuple(c * k for c in v)) for t, v in keys])


def wings(anim, keys, fore=None, fingers=True, membranes=None):
    """keys: (t, extra) - extra rotation on top of SPREAD (None = folded rest) for both wings (left values, mirrored).
    membranes: (t, z-scale offset) keys; ground anims use 1.88 to cancel the idle fold."""
    for side in ('l', 'r'):
        P(anim, 'wing_' + side, 'rotation', *[(t, (0, 0, 0) if e is None else woff('wing', side, e)) for t, e in keys])
        fk = fore if fore is not None else [(t, None if e is None else (0, 0, 0)) for t, e in keys]
        P(anim, 'wforearm_' + side, 'rotation', *[(t, (0, 0, 0) if e is None else woff('forearm', side, e)) for t, e in fk])
        if fingers:
            for i in range(1, 4):
                P(anim, 'finger%d_%s' % (i, side), 'rotation', *[(t, (0, 0, 0) if e is None else woff('finger%d' % i, side, (0, 0, e[2] * 0.3))) for t, e in keys])
        if membranes:
            for mm in MEMS:
                P(anim, '%s_%s' % (mm, side), 'scale', *[(t, (1, 1, s)) for t, s in membranes])


# staggered wing transitions: (start, end) fractions of a fold<->spread transition per bone group
ORDER_OPEN = {'wing': (0.0, 0.65), 'forearm': (0.12, 0.8), 'finger': (0.3, 1.0), 'mem': (0.45, 1.0)}
ORDER_CLOSE = {'mem': (0.0, 0.4), 'finger': (0.0, 0.55), 'forearm': (0.2, 0.85), 'wing': (0.35, 1.0)}
WING_GROUPS = ['wing', 'forearm', 'finger1', 'finger2', 'finger3', 'mem']


def wing_track(anim, keys, ground=True, fore_extra=None):
    """keys: (t, state) with state None (folded rest) or an extra rotation on top of SPREAD (left values).
    Fold<->spread transitions are staggered so the wing folds like a real limb (no 'picture frame')."""
    for side in ('l', 'r'):
        def val(bone, st):
            if bone == 'mem':
                return (1, 1, 1) if st is None or not ground else (1, 1, 1.88)
            if st is None:
                return (0, 0, 0)
            if bone == 'wing':
                return woff('wing', side, st)
            if bone == 'forearm':
                return woff('forearm', side, fore_extra or (0, 0, 0))
            return woff(bone, side, (0, 0, st[2] * 0.3))
        tracks = {b: [] for b in WING_GROUPS}
        prev = None
        for t, st in keys:
            if prev is not None and (prev[1] is None) != (st is None):
                t0, s0 = prev
                order = ORDER_OPEN if s0 is None else ORDER_CLOSE
                for bone, tr in tracks.items():
                    a, b = order['finger' if bone.startswith('finger') else bone]
                    ta, tb = t0 + (t - t0) * a, t0 + (t - t0) * b
                    if ta - t0 > 0.02:
                        tr.append((ta, val(bone, s0)))
                    if t - tb > 0.02:
                        tr.append((tb, val(bone, st)))
                    tr.append((t, val(bone, st)))
            else:
                for bone, tr in tracks.items():
                    tr.append((t, val(bone, st)))
            prev = (t, st)
        for bone, tr in tracks.items():
            if bone == 'mem':
                for mm in MEMS:
                    P(anim, '%s_%s' % (mm, side), 'scale', *tr)
            else:
                P(anim, wname(bone, side), 'rotation', *tr)


def wing_delta(anim, keys, fore=None):
    """Air actions: rotation deltas on top of the fly loop (no spread offsets). keys: (t, (x, y, z)) left values."""
    for side in ('l', 'r'):
        g = 1 if side == 'l' else -1
        P(anim, 'wing_' + side, 'rotation', *[(t, (v[0], g * v[1], g * v[2])) for t, v in keys])
        if fore:
            P(anim, 'wforearm_' + side, 'rotation', *[(t, (v[0], g * v[1], g * v[2])) for t, v in fore])


def core_keys(anim, keys):
    """nova core scale (ground: idle holds 0.01, so +0.99 is added here)."""
    P(anim, 'nova_core', 'scale', *[(t, (s + 0.99, s + 0.99, s + 0.99)) for t, s in keys])


# ------------------------------------------------------------------ loops
a = T('idle', 3.6, True)
A.sine(a, 'body', 'position', (0, 0.6, 0), offset=(0, 1.3, 0))   # +1.3 px: the paws hovered ~0.1 block above the ground
A.sine(a, 'body', 'rotation', (0.7, 0, 0), phase=0.4)
A.sine(a, 'neck1', 'rotation', (2.0, 2.0, 0), phase=0.8)
A.sine(a, 'neck3', 'rotation', (-1.5, 2.0, 0), phase=1.3)
A.sine(a, 'head', 'rotation', (-1.5, 3.0, 1.5), phase=1.7)
A.sine(a, 'jaw', 'rotation', (1.5, 0, 0), offset=(1.5, 0, 0), period=1.8)
A.chain_wave(a, TAILN, (0.5, 4, 0), phase_step=0.6, growth=1.1)
A.sine(a, 'wing_l', 'rotation', (0, 0, 2), phase=0.3)
A.sine(a, 'wing_r', 'rotation', (0, 0, -2), phase=0.3)
fold_membranes(a)
hide_core(a)

a = T('walk', 2.2, True)
gait(a, 2.2, {'hl': 0.0, 'fl': 0.25, 'hr': 0.5, 'fr': 0.75}, FORE_WALK, HIND_WALK)
A.sine(a, 'body', 'position', (0, -0.9, 0), period=1.1, phase=-math.pi / 2)
A.sine(a, 'body', 'rotation', (0.8, 1.5, 1.8), period=2.2)
A.sine(a, 'neck1', 'rotation', (1.5, -2.0, 0), period=1.1, phase=0.6)
A.sine(a, 'head', 'rotation', (-1.0, -1.5, -1.0), period=2.2, phase=0.4)
A.chain_wave(a, TAILN, (0, -5, 0), period=2.2, phase_step=0.5, growth=1.1, phase=0.5)
A.sine(a, 'wing_l', 'rotation', (0, 0, 3), period=1.1)
A.sine(a, 'wing_r', 'rotation', (0, 0, -3), period=1.1)

a = T('run', 1.0, True)
gait(a, 1.0, {'hl': 0.0, 'hr': 0.08, 'fl': 0.5, 'fr': 0.58}, FORE_RUN, HIND_RUN, spc=14)
A.sine(a, 'body', 'position', (0, -2.2, 0), period=0.5, phase=-math.pi / 2, offset=(0, 0.6, 0))
A.sine(a, 'body', 'rotation', (4.5, 0, 0), period=1.0, phase=0.6, offset=(3, 0, 0))
A.sine(a, 'neck1', 'rotation', (-4, 0, 0), period=1.0, phase=0.9, offset=(10, 0, 0))
A.hold(a, 'neck3', 'rotation', (-6, 0, 0))
A.hold(a, 'head', 'rotation', (-8, 0, 0))
A.hold(a, 'jaw', 'rotation', (8, 0, 0))
A.chain_wave(a, TAILN, (3, -6, 0), period=1.0, phase_step=0.45, growth=1.08, phase=0.5)
A.sine(a, 'wing_l', 'rotation', (0, 0, 6), period=0.5)
A.sine(a, 'wing_r', 'rotation', (0, 0, -6), period=0.5)

# fly: idle is NOT applied while flying -> membranes are at rest scale (open), core must be hidden here
FP = 1.4
a = T('fly', FP, True)
for side in ('l', 'r'):
    A.sample(a, 'wing_' + side, 'rotation', lambda t, side=side: woff('wing', side, (0, 0, -32 * math.sin(2 * math.pi * t / FP))), steps=14)
    A.sample(a, 'wforearm_' + side, 'rotation', lambda t, side=side: woff('forearm', side, (0, 8 * math.sin(2 * math.pi * t / FP - 0.7), -14 * math.sin(2 * math.pi * t / FP - 0.7))), steps=14)
    for i in range(3):
        A.sample(a, 'finger%d_%s' % (i + 1, side), 'rotation', lambda t, side=side, i=i: woff('finger%d' % (i + 1), side, (0, 0, -9 * math.sin(2 * math.pi * t / FP - 1.1))), steps=14)
    A.hold(a, 'arm_' + side, 'rotation', (56, 0, 0))
    A.hold(a, 'forearm_' + side, 'rotation', (-96, 0, 0))
    A.hold(a, 'forepaw_' + side, 'rotation', (60, 0, 0))
    A.hold(a, 'thigh_' + side, 'rotation', (70, 0, 0))
    A.hold(a, 'shin_' + side, 'rotation', (-44, 0, 0))
    A.hold(a, 'hfoot_' + side, 'rotation', (56, 0, 0))
A.sine(a, 'body', 'position', (0, -3, 0), period=FP, phase=math.pi)
A.hold(a, 'body', 'rotation', (6, 0, 0))
A.sine(a, 'neck1', 'rotation', (2, 0, 0), period=FP, phase=0.5, offset=(14, 0, 0))
A.hold(a, 'neck3', 'rotation', (-8, 0, 0))
A.hold(a, 'head', 'rotation', (-10, 0, 0))
A.chain_wave(a, TAILN, (3, 4, 0), period=FP, phase_step=0.6, phase=0.4)
hide_core(a)

# ------------------------------------------------------------------ roar (first sight 44 ticks / enrage 40): rears up, wings flare
a = T('roar', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.35, (5, 0, 0)), (0.65, (-20, 0, 0)), (1.75, (-18, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.35, (0, 2, 0)), (0.65, (0, 1, 4)), (1.75, (0, 1, 4)), (2.2, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.35, (28, 0, 0)), (0.65, (-30, 0, 0)), (0.95, (-26, -12, 0)), (1.3, (-28, 12, 0)), (1.75, (-22, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.45, (6, 0, 0)), (0.68, (46, 0, 0)), (1.7, (42, 0, 0)), (2.05, (0, 0, 0)))
wing_track(a, [(0, None), (0.55, (-14, 10, -34)), (1.7, (-10, 6, -28)), (2.1, None), (2.2, None)])
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.35, (6, 6, 0)), (0.7, (-34, -24, 20)), (1.7, (-30, -20, 18)), (2.2, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.7, (-12, 14, 18)), (1.7, (-12, 14, 18)), (2.2, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.4, (-3, 0, 0)), (0.7, (6, 0, 0)), (1.0, (6, 8, 0)), (1.4, (6, -8, 0)), (1.75, (4, 0, 0)), (2.2, (0, 0, 0))], growth=1.1)

# air roar (played instead of roar while flying): deltas on the fly loop
a = T('fly_roar', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (-18, 0, 0)), (1.7, (-16, 0, 0)), (2.2, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.35, (20, 0, 0)), (0.65, (-30, 0, 0)), (1.0, (-26, -10, 0)), (1.35, (-28, 10, 0)), (1.75, (-20, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.45, (6, 0, 0)), (0.68, (46, 0, 0)), (1.7, (42, 0, 0)), (2.05, (0, 0, 0)))
wing_delta(a, [(0, (0, 0, 0)), (0.6, (-6, 8, -16)), (1.7, (-6, 8, -16)), (2.2, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.7, (6, 6, 0)), (1.4, (6, -6, 0)), (2.2, (0, 0, 0))])

# ------------------------------------------------------------------ reactions
a = T('flinch', 1.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.12, (-8, 8, -6)), (0.45, (-2, -3, 3)), (1.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.12, (1, -1, 4)), (0.5, (0, 1, 1)), (1.2, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.12, (-34, 26, 0)), (0.5, (12, -10, 0)), (1.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.15, (40, 0, 0)), (0.7, (8, 0, 0)), (1.2, (0, 0, 0)))
for side, g in (('l', 1), ('r', -1)):
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.12, (-10, g * 14, g * -16)), (0.5, (3, g * -4, g * 5)), (1.2, (0, 0, 0)))
tail_keys(a, [(0, (0, 0, 0)), (0.15, (6, -10, 0)), (0.5, (-2, 6, 0)), (1.2, (0, 0, 0))], growth=1.1)
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.2, (12, 16, -8)), (0.6, (4, 4, -2)), (1.2, (0, 0, 0))])

a = T('fly_flinch', 1.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.15, (-14, 6, 18)), (0.5, (6, -2, -6)), (1.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.15, (0, 6, 2)), (0.5, (0, 2, 0)), (1.2, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.15, (-30, 20, 0)), (0.5, (10, -8, 0)), (1.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.15, (38, 0, 0)), (0.7, (6, 0, 0)), (1.2, (0, 0, 0)))
wing_delta(a, [(0, (0, 0, 0)), (0.15, (16, -20, 30)), (0.5, (-6, 6, -10)), (1.2, (0, 0, 0))])

a = T('death', 2.4)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.4, (-14, 0, -6)), (0.9, (-4, 0, 26)), (1.4, (4, 0, 70)), (1.6, (4, 0, 64)), (1.85, (4, 0, 67)), (2.4, (4, 0, 66)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.4, (0, -4, 2)), (0.9, (2, 6, 0)), (1.4, (5, 15, 0)), (1.6, (5, 13, 0)), (2.4, (5, 14, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.4, (-40, 0, 0)), (0.9, (-10, 10, 0)), (1.4, (34, 24, -10)), (1.8, (40, 30, -12)), (2.4, (40, 30, -12))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.4, (46, 0, 0)), (1.4, (26, 0, 0)), (2.4, (18, 0, 0)))
for side, g in (('l', 1), ('r', -1)):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.4, (-30, -20, 10)), (1.4, (-40 + g * 10, 30, 10)), (2.4, (-44 + g * 10, 34, 12))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.4, (-8, 10, -4)), (1.4, (-30 + g * 8, 30, -10)), (2.4, (-34 + g * 8, 34, -10))])
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.4, (12, g * 20, g * 14)), (1.4, (8, g * 6, g * -10)), (2.4, (6, g * 6, g * -12)))
tail_keys(a, [(0, (0, 0, 0)), (0.4, (6, 0, 0)), (1.4, (5, 8, 0)), (1.8, (5, 10, 0)), (2.4, (5, 10, 0))])
fold_membranes(a)
hide_core(a)

a = T('sleep', 4.0, True)
A.hold(a, 'body', 'position', (0, 15, 0))
A.sine(a, 'body', 'rotation', (1.0, 0, 0), offset=(4, 0, -2))
A.hold(a, 'neck1', 'rotation', (30, -26, 0))
A.hold(a, 'neck2', 'rotation', (14, -26, 0))
A.hold(a, 'neck3', 'rotation', (4, -24, 0))
A.hold(a, 'head', 'rotation', (-6, -20, -6))
A.sine(a, 'jaw', 'rotation', (1.0, 0, 0), offset=(1.0, 0, 0))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (-56, 92, 24)), (4.0, (-56, 92, 24))])
    leg_keys(a, 'hind', side, [(0, (-52, 72, -10)), (4.0, (-52, 72, -10))])
for i, b in enumerate(TAILN):
    A.hold(a, b, 'rotation', (-2 if i == 0 else 0, -14 - i, 0))
fold_membranes(a)
hide_core(a)

a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 9, 0))
A.sine(a, 'body', 'rotation', (5, 5, 7), period=0.5, offset=(-4, 0, 0))
A.sine(a, 'neck1', 'rotation', (-10, 16, 0), period=0.5, phase=1.0, offset=(-12, 0, 0))
A.sine(a, 'neck3', 'rotation', (-6, 10, 0), period=0.5, phase=1.4)
A.sine(a, 'head', 'rotation', (-8, 0, 10), period=0.5, phase=1.8, offset=(-6, 0, 0))
A.sine(a, 'jaw', 'rotation', (16, 0, 0), period=0.25, offset=(22, 0, 0))
for side, g in (('l', 1), ('r', -1)):
    A.sine(a, 'wing_' + side, 'rotation', (0, g * 10, -g * 22), period=0.5, phase=0.4)
    leg_keys(a, 'fore', side, [(0, (-30, 40, 10)), (0.25 if g > 0 else 0.5, (-10, 10, 0)), (0.5 if g > 0 else 0.75, (-30, 40, 10)), (1.0, (-30, 40, 10))])
A.chain_wave(a, TAILN, (8, 16, 0), period=0.5, phase_step=0.6)
fold_membranes(a)
hide_core(a)

a = T('stun', 1.6, True)
A.sine(a, 'body', 'rotation', (0, 3, 6), period=1.6, offset=(3, 0, 0))
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'neck1', 'rotation', (6, 18, 0), period=1.6, phase=0.5, offset=(22, 0, 0))
A.sine(a, 'neck3', 'rotation', (4, 10, 0), period=1.6, phase=1.0, offset=(10, 0, 0))
A.sine(a, 'head', 'rotation', (8, 0, 16), period=0.8, offset=(4, 0, 0))
A.hold(a, 'jaw', 'rotation', (24, 0, 0))
for side, g in (('l', 1), ('r', -1)):
    A.sine(a, 'wing_' + side, 'rotation', (0, 0, -g * 6), period=1.6, offset=(6, g * 8, g * 10))
    leg_keys(a, 'fore', side, [(0, (-6, 14, -4)), (1.6, (-6, 14, -4))])
A.chain_wave(a, TAILN, (-2, 5, 0), period=1.6, phase_step=0.5, growth=1.1)
fold_membranes(a)
hide_core(a)

# ------------------------------------------------------------------ ground attacks (t = tick / 20)
# bite: 26 ticks, hit @12
a = T('bite', 1.3)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.4, (-4, 0, 0)), (0.6, (6, 0, 0)), (0.85, (4, 0, 0)), (1.3, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.4, (0, -1, 3)), (0.6, (0, 1, -7)), (0.85, (0, 1, -5)), (1.3, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.4, (-36, 0, 0)), (0.6, (40, 0, 0)), (0.72, (36, -10, 0)), (0.86, (36, 10, 0)), (1.3, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.42, (44, 0, 0)), (0.58, (0, 0, 0)), (0.66, (6, 0, 0)), (1.0, (2, 0, 0)), (1.3, (0, 0, 0)))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.6, (-12, 6, 6)), (1.0, (-8, 4, 4)), (1.3, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.4, (-2, 0, 0)), (0.6, (4, 0, 0)), (1.3, (0, 0, 0))])

# claw_swipe: 44 ticks; right claw sweeps @12-16, left claw @24-28
a = T('claw_swipe', 2.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (-12, 12, 0)), (0.8, (2, -14, 0)), (1.1, (-12, -12, 0)), (1.4, (2, 14, 0)), (1.8, (-2, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.5, (0, 0, 3)), (0.8, (0, 1, -4)), (1.1, (0, 0, 2)), (1.4, (0, 1, -4)), (2.2, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.5, (-10, -8, 0)), (0.8, (10, 12, 0)), (1.1, (-10, 8, 0)), (1.4, (10, -12, 0)), (2.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.6, (20, 0, 0)), (0.8, (6, 0, 0)), (1.2, (20, 0, 0)), (1.4, (6, 0, 0)), (2.2, (0, 0, 0)))
# right foreleg (arm_r): raised forward and out to the right, then rakes across to the left (y: + = toward the right)
P(a, 'arm_r', 'rotation', (0, (0, 0, 0)), (0.5, (-84, 42, 0)), (0.62, (-76, 24, 0)), (0.8, (-44, -40, 0)), (1.0, (-14, -12, 0)), (1.2, (0, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'forearm_r', 'rotation', (0, (0, 0, 0)), (0.5, (-24, 0, 0)), (0.8, (-12, 0, 0)), (1.2, (0, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'forepaw_r', 'rotation', (0, (0, 0, 0)), (0.5, (-34, 0, 0)), (0.8, (24, 0, 0)), (1.2, (0, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'arm_l', 'rotation', (0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.1, (-84, -42, 0)), (1.22, (-76, -24, 0)), (1.4, (-44, 40, 0)), (1.6, (-14, 12, 0)), (1.9, (0, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'forearm_l', 'rotation', (0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.1, (-24, 0, 0)), (1.4, (-12, 0, 0)), (1.9, (0, 0, 0)), (2.2, (0, 0, 0)))
P(a, 'forepaw_l', 'rotation', (0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.1, (-34, 0, 0)), (1.4, (24, 0, 0)), (1.9, (0, 0, 0)), (2.2, (0, 0, 0)))
for side in ('l', 'r'):
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.5, (-10, 12, 10)), (1.5, (-10, 12, 10)), (2.2, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.5, (2, -6, 0)), (0.8, (2, 6, 0)), (1.1, (2, 6, 0)), (1.4, (2, -6, 0)), (2.2, (0, 0, 0))])

# tail_sweep: 40 ticks; the body pivots on the hind legs and the bladed tail sweeps LOW from the right, behind, to the
# left @14-24 (body yaw and tail bend reinforce each other; the neck counter-turns so the head keeps facing forward)
a = T('tail_sweep', 2.0)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (2, -24, -4)), (0.7, (2, -26, -4)), (0.95, (0, 4, 0)), (1.2, (-2, 34, 5)), (1.45, (0, 30, 3)), (2.0, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.6, (0, 2, -1)), (1.2, (0, 1, 1)), (2.0, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.6, (8, 30, 0)), (0.95, (6, 0, 0)), (1.2, (4, -28, 0)), (1.5, (0, -22, 0)), (2.0, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.6, (-3, -12, 0)), (0.72, (-3, -13, 0)), (0.95, (-4, 1, 0)), (1.2, (-4, 14, 0)), (1.45, (-3, 15, 0)), (1.75, (-1, 4, 0)), (2.0, (0, 0, 0))], growth=1.06)
P(a, 'tail_blade', 'rotation', (0, (0, 0, 0)), (0.7, (0, -18, 0)), (1.2, (0, 18, 0)), (1.45, (0, 26, 0)), (1.8, (0, -4, 0)), (2.0, (0, 0, 0)))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.6, (18, 0, 0)), (1.2, (10, 0, 0)), (1.7, (0, 0, 0)))
for side, g in (('l', 1), ('r', -1)):
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.6, (8 * g, 10, -6)), (1.2, (-8 * g, 10, -2)), (2.0, (0, 0, 0))])
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.6, (-6 * g, 6, 2)), (1.2, (6 * g, 6, -4)), (2.0, (0, 0, 0))])

# breath_beam: 70 ticks; charge 0-21, beam sweeps left->right @22-52
a = T('breath_beam', 3.5)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.9, (-8, 0, 0)), (1.1, (4, -6, 0)), (2.6, (4, 6, 0)), (3.0, (0, 0, 0)), (3.5, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.9, (0, -1, 4)), (1.1, (0, 1, -3)), (2.6, (0, 1, -3)), (3.5, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.9, (-34, 0, 0)), (1.1, (10, -52, 0)), (1.85, (12, 0, 0)), (2.6, (10, 52, 0)), (3.1, (0, 10, 0)), (3.5, (0, 0, 0))], w=(0.38, 0.28, 0.2, 0.14))
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.5, (14, 0, 0)), (0.95, (24, 0, 0)), (1.1, (44, 0, 0)), (2.6, (42, 0, 0)), (3.0, (4, 0, 0)), (3.5, (0, 0, 0)))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.9, (-14, 10, 4)), (1.1, (-18, 6, 10)), (2.6, (-18, 6, 10)), (3.5, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (1.1, (10, 4, -8)), (2.6, (10, 4, -8)), (3.5, (0, 0, 0))])
for side, g in (('l', 1), ('r', -1)):
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.9, (-10, g * 12, g * -18)), (1.1, (6, g * -8, g * 10)), (2.6, (6, g * -8, g * 10)), (3.2, (0, 0, 0)), (3.5, (0, 0, 0)))
tail_keys(a, [(0, (0, 0, 0)), (1.1, (-2, 8, 0)), (1.85, (-2, 0, 0)), (2.6, (-2, -8, 0)), (3.5, (0, 0, 0))])

# orb_volley: 50 ticks; volleys @18 and @30
a = T('orb_volley', 2.5)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.8, (-10, 0, 0)), (0.95, (6, 0, 0)), (1.3, (-6, 0, 0)), (1.55, (6, 0, 0)), (2.0, (2, 0, 0)), (2.5, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.8, (0, -2, 4)), (0.95, (0, 1, -4)), (1.3, (0, -1, 2)), (1.55, (0, 1, -4)), (2.5, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.8, (-40, 0, 0)), (0.95, (30, 0, 0)), (1.3, (-24, 0, 0)), (1.55, (30, 0, 0)), (2.0, (8, 0, 0)), (2.5, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.7, (14, 0, 0)), (0.92, (46, 0, 0)), (1.15, (16, 0, 0)), (1.52, (46, 0, 0)), (1.9, (10, 0, 0)), (2.5, (0, 0, 0)))
for side, g in (('l', 1), ('r', -1)):
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.8, (-8, g * 10, g * -16)), (0.95, (4, g * -4, g * 6)), (1.6, (2, 0, 0)), (2.5, (0, 0, 0)))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.8, (-10, 6, 4)), (0.95, (-16, 4, 10)), (1.6, (-16, 4, 10)), (2.5, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.8, (-3, 0, 0)), (0.95, (4, 0, 0)), (2.5, (0, 0, 0))])

# wing_gust (ground): 40 ticks; forward wing beats @16 and @26
a = T('wing_gust', 2.0)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (-16, 0, 0)), (0.8, (-6, 0, 0)), (1.1, (-14, 0, 0)), (1.3, (-4, 0, 0)), (2.0, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.6, (0, 0, 4)), (0.8, (0, 1, 0)), (1.1, (0, 0, 3)), (1.3, (0, 1, 0)), (2.0, (0, 0, 0)))
wing_track(a, [(0, None), (0.5, (-14, 26, -46)), (0.8, (12, -30, 36)), (1.05, (-14, 26, -46)), (1.3, (12, -30, 36)), (1.6, (0, 0, 0)), (2.0, None)])
neck_keys(a, [(0, (0, 0, 0)), (0.6, (-16, 0, 0)), (0.8, (12, 0, 0)), (1.1, (-10, 0, 0)), (1.3, (12, 0, 0)), (2.0, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.6, (26, 0, 0)), (1.4, (20, 0, 0)), (2.0, (0, 0, 0)))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.6, (-26, -14, 14)), (1.3, (-20, -10, 10)), (2.0, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.6, (-10, 12, 14)), (1.3, (-10, 12, 12)), (2.0, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.6, (6, 0, 0)), (1.3, (6, 0, 0)), (2.0, (0, 0, 0))])

# supernova (enraged): 90 ticks; rears up 0-10, charges 10-51 (core grows), detonates @52, exhausted after
a = T('supernova', 4.5)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (-26, 0, 0)), (2.5, (-28, 0, 0)), (2.62, (10, 0, 0)), (2.9, (6, 0, 0)), (3.8, (4, 0, 0)), (4.5, (0, 0, 0)))
NOVA_POS = [(0, (0, 0, 0)), (0.5, (0, 2, 5)), (2.5, (0, 1.5, 5)), (2.62, (0, 4, -4)), (2.9, (0, 6, -2)), (3.8, (0, 5, 0)), (4.5, (0, 0, 0))]
A.sample(a, 'body', 'position', lambda t: tuple(v + (0.35 * math.sin(t * 61.0) if (i == 0 and 0.6 < t < 2.45) else 0)
                                                 for i, v in enumerate(keyed(NOVA_POS, t))), steps=90)
neck_keys(a, [(0, (0, 0, 0)), (0.5, (-46, 0, 0)), (2.5, (-50, 0, 0)), (2.62, (40, 0, 0)), (2.9, (30, 0, 0)), (3.8, (26, 0, 0)), (4.5, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.5, (44, 0, 0)), (1.0, (20, 0, 0)), (2.45, (36, 0, 0)), (2.62, (48, 0, 0)), (3.0, (20, 0, 0)), (3.8, (16, 0, 0)), (4.5, (0, 0, 0)))
wing_track(a, [(0, None), (0.5, (-14, 10, -40)), (2.5, (-16, 12, -44)), (2.62, (14, -8, 30)), (3.0, (16, -10, 34)), (3.9, (20, -10, 40)), (4.35, None), (4.5, None)])
for side in ('l', 'r'):
    A.sample(a, 'mem_f1_' + side, 'rotation', lambda t: (0, 0, 2.5 * math.sin(t * 47.0)), steps=36, t0=0.6, t1=2.45)
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.5, (-50, -30, 26)), (2.5, (-54, -34, 28)), (2.62, (-10, 10, 4)), (3.0, (-14, 16, 2)), (3.8, (-14, 16, 2)), (4.5, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.5, (-14, 22, 18)), (2.5, (-16, 24, 20)), (2.62, (-6, 18, -18)), (3.8, (-6, 18, -14)), (4.5, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.5, (6, 0, 0)), (2.5, (8, 0, 0)), (2.62, (-6, 0, 0)), (3.2, (-3, 0, 0)), (4.5, (0, 0, 0))], growth=1.1)
core_keys(a, [(0, 0.01), (0.45, 0.01), (0.7, 0.5), (1.1, 0.85), (1.4, 1.05), (1.6, 0.95), (1.9, 1.35), (2.1, 1.25), (2.47, 1.8), (2.6, 3.0), (2.75, 0.01), (4.5, 0.01)])

# ------------------------------------------------------------------ flight transitions
# takeoff: ground until tick 12 (idle base, wings folded -> spread here), then the fly loop takes over
a = T('takeoff', 1.6)
P(a, 'body', 'position', (0, (0, 0, 0)), (0.4, (0, 6, 0)), (0.58, (0, -4, 0)), (0.62, (0, -2, 0)), (1.6, (0, 0, 0)))
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.4, (6, 0, 0)), (0.58, (-10, 0, 0)), (0.62, (-14, 0, 0)), (1.2, (-10, 0, 0)), (1.6, (0, 0, 0)))
# wings: spread from the fold while grounded (until 0.59), then small deltas on top of the fly loop
for side in ('l', 'r'):
    g = 1 if side == 'l' else -1
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.3, woff('wing', side, (-6, 6, -40))), (0.5, woff('wing', side, (6, -6, 30))), (0.59, woff('wing', side)),
      (0.61, (0, 0, 0)), (0.8, (0, 0, g * -12)), (1.0, (0, 0, g * 10)), (1.6, (0, 0, 0)))
    P(a, 'wforearm_' + side, 'rotation', (0, (0, 0, 0)), (0.3, woff('forearm', side)), (0.59, woff('forearm', side)), (0.61, (0, 0, 0)), (1.6, (0, 0, 0)))
    for i in range(1, 4):
        P(a, 'finger%d_%s' % (i, side), 'rotation', (0, (0, 0, 0)), (0.3, woff('finger%d' % i, side)), (0.59, woff('finger%d' % i, side)), (0.61, (0, 0, 0)), (1.6, (0, 0, 0)))
    for mm in MEMS:
        P(a, '%s_%s' % (mm, side), 'scale', (0, (1, 1, 1)), (0.3, (1, 1, 1.88)), (0.59, (1, 1, 1.88)), (0.61, (1, 1, 1)), (1.6, (1, 1, 1)))
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.4, (-20, 40, 10)), (0.58, (20, -10, -10)), (0.62, (-30, 50, -30)), (1.6, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.4, (-24, 36, -10)), (0.58, (20, -14, 10)), (0.62, (-40, 30, -30)), (1.6, (0, 0, 0))])
neck_keys(a, [(0, (0, 0, 0)), (0.4, (16, 0, 0)), (0.6, (-16, 0, 0)), (1.6, (0, 0, 0))])

# land: flying until tick 20 (fly base: deltas only), then the idle base (spread -> fold)
a = T('land', 1.2)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (-14, 0, 0)), (0.98, (-10, 0, 0)), (1.02, (-4, 0, 0)), (1.2, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (0.98, (0, -2, 0)), (1.05, (0, 5, 0)), (1.2, (0, 0, 0)))
for side in ('l', 'r'):
    g = 1 if side == 'l' else -1
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.4, (0, 0, g * -24)), (0.98, (0, 0, g * -10)), (1.0, woff('wing', side, (0, 0, -10))), (1.2, (0, 0, 0)))
    P(a, 'wforearm_' + side, 'rotation', (0, (0, 0, 0)), (0.98, (0, 0, 0)), (1.0, woff('forearm', side)), (1.2, (0, 0, 0)))
    for i in range(1, 4):
        P(a, 'finger%d_%s' % (i, side), 'rotation', (0, (0, 0, 0)), (0.98, (0, 0, 0)), (1.0, woff('finger%d' % i, side)), (1.2, (0, 0, 0)))
    for mm in MEMS:
        P(a, '%s_%s' % (mm, side), 'scale', (0, (1, 1, 1)), (0.98, (1, 1, 1)), (1.0, (1, 1, 1.88)), (1.2, (1, 1, 1)))
    # legs come down out of the flight tuck (fly holds arm 42/forearm -70/paw 40, thigh 52/shin -30/hfoot 40)
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.6, (-54, 86, -56)), (0.98, (-58, 94, -60)), (1.0, (-4, 8, 2)), (1.2, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (0.6, (-68, 44, -52)), (0.98, (-72, 48, -56)), (1.0, (-6, 10, -2)), (1.2, (0, 0, 0))])
neck_keys(a, [(0, (0, 0, 0)), (0.5, (-10, 0, 0)), (1.05, (10, 0, 0)), (1.2, (0, 0, 0))])

# ------------------------------------------------------------------ air attacks (deltas on top of the fly loop)
a = T('fly_orb', 1.8)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.7, (-12, 0, 0)), (0.85, (10, 0, 0)), (1.3, (2, 0, 0)), (1.8, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.7, (-40, 0, 0)), (0.85, (26, 0, 0)), (1.3, (6, 0, 0)), (1.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.6, (14, 0, 0)), (0.82, (46, 0, 0)), (1.2, (20, 0, 0)), (1.8, (0, 0, 0)))
wing_delta(a, [(0, (0, 0, 0)), (0.7, (0, 10, -14)), (0.85, (0, -6, 10)), (1.8, (0, 0, 0))])

a = T('dive', 2.0)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.5, (-22, 0, 0)), (0.8, (24, 0, 0)), (1.62, (20, 0, 0)), (1.7, (6, 0, 0)), (2.0, (0, 0, 0)))
P(a, 'body', 'position', (0, (0, 0, 0)), (1.62, (0, 0, 0)), (1.72, (0, 7, 0)), (2.0, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.5, (-16, 0, 0)), (0.8, (-24, 0, 0)), (1.62, (-20, 0, 0)), (1.75, (14, 0, 0)), (2.0, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.5, (30, 0, 0)), (1.6, (36, 0, 0)), (1.8, (10, 0, 0)), (2.0, (0, 0, 0)))
for side in ('l', 'r'):
    g = 1 if side == 'l' else -1
    # air: swept back over the fly pose; from tick 33 (1.65 s) the base is the folded idle pose
    P(a, 'wing_' + side, 'rotation', (0, (0, 0, 0)), (0.5, (0, g * 4, g * -30)), (0.8, (10, g * 40, g * 24)), (1.62, (10, g * 40, g * 24)),
      (1.66, woff('wing', side, (10, 30, 10))), (2.0, (0, 0, 0)))
    P(a, 'wforearm_' + side, 'rotation', (0, (0, 0, 0)), (1.62, (0, 0, 0)), (1.66, woff('forearm', side)), (2.0, (0, 0, 0)))
    for i in range(1, 4):
        P(a, 'finger%d_%s' % (i, side), 'rotation', (0, (0, 0, 0)), (1.62, (0, 0, 0)), (1.66, woff('finger%d' % i, side)), (2.0, (0, 0, 0)))
    for mm in MEMS:
        P(a, '%s_%s' % (mm, side), 'scale', (0, (1, 1, 1)), (1.62, (1, 1, 1)), (1.66, (1, 1, 1.88)), (2.0, (1, 1, 1)))
    # forelegs reach forward to strike, then plant (fly tuck is undone after landing)
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.5, (-20, 30, 0)), (0.8, (-110, 80, -30)), (1.62, (-110, 80, -30)), (1.66, (-14, 14, 6)), (2.0, (0, 0, 0))])
    leg_keys(a, 'hind', side, [(0, (0, 0, 0)), (1.62, (0, 0, 0)), (1.66, (-14, 20, -6)), (2.0, (0, 0, 0))])

a = T('meteor_rain', 3.5)
P(a, 'body', 'rotation', (0, (0, 0, 0)), (0.6, (-20, 0, 0)), (2.7, (-18, 0, 0)), (3.5, (0, 0, 0)))
neck_keys(a, [(0, (0, 0, 0)), (0.35, (16, 0, 0)), (0.65, (-46, 0, 0)), (1.0, (-40, 0, 0)), (2.7, (-36, 0, 0)), (3.5, (0, 0, 0))])
P(a, 'jaw', 'rotation', (0, (0, 0, 0)), (0.45, (6, 0, 0)), (0.68, (48, 0, 0)), (1.3, (30, 0, 0)), (2.7, (26, 0, 0)), (3.2, (0, 0, 0)))
wing_delta(a, [(0, (0, 0, 0)), (0.6, (-10, 10, -24)), (1.0, (-8, 8, -18)), (2.7, (-8, 8, -18)), (3.5, (0, 0, 0))])
for side in ('l', 'r'):
    leg_keys(a, 'fore', side, [(0, (0, 0, 0)), (0.6, (-50, 50, -20)), (2.7, (-46, 46, -18)), (3.5, (0, 0, 0))])
tail_keys(a, [(0, (0, 0, 0)), (0.6, (-6, 0, 0)), (1.6, (-6, 8, 0)), (2.4, (-6, -8, 0)), (3.5, (0, 0, 0))])

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
        preview(m, img, os.path.join(os.path.dirname(__file__), '..', 'preview', 'azragos.png'), anims=sel or None, size=260)
