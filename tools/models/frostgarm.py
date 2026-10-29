"""Frostgarm (氷牙獣フロストガルム) - huge sabre-toothed fanged beast, ice element.

Pale icy-blue striped fur, white bib/belly/muzzle, glowing ice-crystal clusters on the shoulders, spine and
tail tip, long curved sabre fangs, frost-rimmed claws and a bushy tail.
~3.5 blocks at the shoulder, ~7 blocks nose to rump (+ tail).
Run from tools/:  python3 models/frostgarm.py --preview [anim ...]
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel.paint import M  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402

# ------------------------------------------------------------------ materials (module-local)
import numpy as np  # noqa: E402
import hcmodel.paint as PAINT  # noqa: E402

_orig_accent = PAINT._accent_mask


def _fg_accent(mat, P, f, d, r, seed):
    """Jagged tiger stripes that fade out toward the belly (script-local accent modes)."""
    if mat.accent_mode in ('fg_tiger', 'fg_tiger_y'):
        x, y, z = P[..., 0], P[..., 1], P[..., 2]
        n1 = PAINT.vnoise(x * 0.07, y * 0.07, z * 0.07, 77)
        n2 = PAINT.vnoise(x * 0.4, y * 0.4, z * 0.4, 91)
        along = z if mat.accent_mode == 'fg_tiger' else y
        phase = along * mat.accent_freq * 2.2 + n1 * 3.2 + n2 * 1.1 + np.abs(x) * 0.06
        v = 0.5 + 0.5 * np.sin(phase)
        if mat.accent_mode == 'fg_tiger':
            th = mat.accent_thresh + np.clip((y + 22) / 13.0, 0, 1) * 0.25
        else:
            th = mat.accent_thresh + (r > 0.5) * 0.0
        return v > th
    return _orig_accent(mat, P, f, d, r, seed)


PAINT._accent_mask = _fg_accent

FUR = ['#2f4a70', '#4f78a6', '#78a6d2', '#a6cbea', '#d3e8f8', '#f5fbff']
FUR_L = ['#5c7896', '#86a7c4', '#b0cde3', '#d4e8f4', '#eef7fc', '#ffffff']
M('fg_fur', FUR, 'fur', belly='fg_fur_light', accent='fg_stripe', accent_mode='fg_tiger', accent_freq=0.24, accent_thresh=0.8, edge=0.05, base=0.64)
M('fg_fur_leg', FUR, 'fur', belly='fg_fur_light', accent='fg_stripe', accent_mode='fg_tiger_y', accent_freq=0.3, accent_thresh=0.9, edge=0.05, base=0.64)
M('fg_fur_plain', FUR, 'fur', belly='fg_fur_light', edge=0.05, base=0.64)
M('fg_fur_light', FUR_L, 'fur', edge=0.04, base=0.66)
M('fg_stripe', ['#142238', '#1f3352', '#2d476b', '#3f5e86', '#5778a0', '#7393b8'], 'fur', edge=0.0, base=0.5)
M('fg_dark', ['#121b2b', '#1f2d44', '#2f4260', '#435a7c', '#5b7598', '#7b95b5'], 'fur', edge=0.05)
M('fg_muzzle', ['#5f7a95', '#88a5be', '#afcade', '#d2e5f1', '#ecf6fb', '#ffffff'], 'fur', scale=0.7, edge=0.05, base=0.66)
M('fg_nose', ['#07090e', '#10141d', '#1b2130', '#283146', '#38445e', '#4b5a78'], 'skin')
M('fg_ear_inner', ['#3a4258', '#566079', '#76809a', '#97a1ba', '#b9c2d6', '#d9dfeb'], 'fur', edge=0.0)
M('fg_tuft', ['#0b1220', '#141f33', '#1f2e48', '#2c3f5e', '#3d5476', '#516a8f'], 'fur', edge=0.0)
M('fg_fang', ['#53606c', '#86939f', '#b5c2cc', '#d8e2ea', '#f0f6fa', '#ffffff'], 'horn', edge=0.04, base=0.62)
M('fg_claw', ['#0e131c', '#1b2433', '#2c3a4f', '#43566f', '#5f7792', '#7f99b3'], 'horn', edge=0.05)
M('fg_ice', ['#1b4d6b', '#2e7aa3', '#4fa9d3', '#86d0ef', '#c2ecfb', '#f5fdff'], 'crystal', glow='#9fe8ff', edge=0.08, base=0.58)
M('fg_ice_tip', ['#3d8fbf', '#6cc0e6', '#a3def6', '#cdf0fc', '#ebfbff', '#ffffff'], 'crystal', glow='#d2f6ff', edge=0.05, base=0.62)
M('fg_ice_core', ['#2f8fc4', '#5cc4ee', '#8fdcf8', '#bdeefd', '#e2faff', '#ffffff'], 'smooth', glow='#b8f2ff', edge=0.0, base=0.7)
M('fg_eye', ['#0a5a8a', '#1886c0', '#35b3ea', '#76d6fb', '#bdf0ff', '#f0ffff'], 'smooth', glow='#7fe6ff', edge=0.0, base=0.6)
M('fg_rim', ['#25587a', '#3a7fa8', '#5fa9d2', '#93cfee', '#c6ebfb', '#f2fcff'], 'crystal', edge=0.06, base=0.6)

m = Model('frostgarm')
m.config.update({'head_bones': [['neck', 0.35], ['head', 0.55]], 'walk_speed': 2.0, 'run_speed': 0.55, 'walk_scale': 2.6})

ABS = {}


def bone(parent, name, abs_pivot, rot=(0, 0, 0)):
    """Bone placed by absolute rest-space pivot (parent chain must be unrotated)."""
    pp = ABS[parent.name] if parent is not None else (0, 0, 0)
    rel = tuple(a - b for a, b in zip(abs_pivot, pp))
    b = parent.bone(name, rel, rot) if parent is not None else m.bone(name, rel, rot)
    ABS[name] = tuple(abs_pivot)
    return b


def cube(b, abs_min, size, mat, **kw):
    """Cube placed by absolute rest-space min corner (bone chain must be unrotated)."""
    p = ABS[b.name]
    return b.cube(tuple(a - q for a, q in zip(abs_min, p)), size, mat, **kw)


def crystal(parent, name, pivot, rot, w, h, glow=False):
    """Ice crystal pointing up (-Y) from its base at the bone pivot, with a sharpened glowing tip."""
    b = parent.bone(name, pivot, rot)
    b.cube((-w / 2, -h, -w / 2), (w, h, w), 'fg_ice', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.35, 'mat': 'fg_ice_tip'}], glow=glow)
    tw = max(1, w - 2)
    b.cube((-tw / 2, -h - tw - 1, -tw / 2), (tw, tw + 2, tw), 'fg_ice_tip', glow=True)
    return b


def tuft(parent, name, pivot, rot, h, w, mat='fg_fur_light'):
    """Fur tuft: crossed spike planes pointing along local -Y."""
    b = parent.bone(name, pivot, rot)
    b.cube((0, -h, -w / 2), (0, h, w), mat, decals=[{'type': 'cutout', 'shape': 'spike'}])
    b.cube((-w / 2, -h, 0), (w, h, 0), mat, decals=[{'type': 'cutout', 'shape': 'spike'}])
    return b


def fringe(b, abs_min, size, mat='fg_fur_light', depth=0.7):
    """Ragged fur fringe plane (absolute placement)."""
    return cube(b, abs_min, size, mat, decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': depth}])


# ------------------------------------------------------------------ torso: body (waist) -> chest / hips
body = bone(None, 'body', (0, -12, 4))
cube(body, (-12, -30, -10), (24, 21, 30), 'fg_fur', decals=[{'type': 'belly_band'}])
cube(body, (-10, -32, -8), (20, 3, 26), 'fg_fur')                                    # back line
cube(body, (-9.5, -10, -6), (19, 2, 22), 'fg_fur_light')                             # belly fur
fringe(body, (12.4, -13, -8), (0, 7, 26))                                            # flank fringes
fringe(body, (-12.4, -13, -8), (0, 7, 26))

chest = bone(body, 'chest', (0, -14, -8))
cube(chest, (-15, -32, -36), (30, 26, 30), 'fg_fur', decals=[{'type': 'belly_band'}])
cube(chest, (-16.5, -35, -32), (11, 8, 20), 'fg_fur')                                 # shoulder humps
cube(chest, (5.5, -35, -32), (11, 8, 20), 'fg_fur')
cube(chest, (-12, -24, -40), (24, 20, 6), 'fg_fur_light')                            # chest bib
fringe(chest, (-11, -5, -38.5), (22, 8, 0), depth=0.8)
cube(chest, (-13, -8, -34), (26, 3, 26), 'fg_fur_light')                             # under chest
fringe(chest, (15.4, -10, -32), (0, 7, 24))
fringe(chest, (-15.4, -10, -32), (0, 7, 24))

hips = bone(body, 'hips', (0, -16, 22))
cube(hips, (-13, -29, 16), (26, 22, 25), 'fg_fur', decals=[{'type': 'belly_band'}])
cube(hips, (-11, -31, 19), (22, 3, 19), 'fg_fur')

# ------------------------------------------------------------------ neck & head
neck = bone(chest, 'neck', (0, -23, -33), (-16, 0, 0))
neck.cube((-11, -11, -21), (22, 21, 25), 'fg_fur_plain')
neck.cube((-13.5, -13.5, -11), (27, 26, 10), 'fg_fur_light')                         # mane collar
neck.cube((-12, -14, -19), (24, 5, 16), 'fg_fur_plain')                              # nape crest
neck.cube((-9, 8, -21), (18, 5, 19), 'fg_fur_light')                                 # throat fluff
neck.cube((-8.5, 13, -19), (17, 6, 0), 'fg_fur_light', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.8}])
for i, (z, rx, h) in enumerate(((-15, -35, 7), (-7, -55, 10), (1, -72, 11))):
    tuft(neck, 'mane_tuft%d' % (i + 1), (0, -13.5, z), (rx, 0, 0), h, 11, 'fg_fur')
for i, (z, h) in enumerate(((-9, 9), (-1, 10))):
    c = tuft(neck, 'mane_side%d_l' % (i + 1), (13, -4, z), (-30, 0, 70), h, 10, 'fg_fur_light')
    mirror_tree(neck, c)

head = neck.bone('head', (0, -2, -19), (24, 0, 0))
head.cube((-11, -9, -13), (22, 15, 15), 'fg_fur_plain')                               # cranium
head.cube((-12.5, -3, -14), (25, 10, 11), 'fg_fur_plain')                             # broad cheeks
head.cube((-9, -12, -14), (18, 4, 11), 'fg_fur')                                      # brow / forehead (striped)
head.cube((-6.5, -13, -10), (13, 2, 8), 'fg_fur')
head.cube((-11, -9, -15), (7, 2, 3), 'fg_fur_plain')                                  # brow overhang over the eyes
head.cube((4, -9, -15), (7, 2, 3), 'fg_fur_plain')
head.cube((-10, -7, -14.6), (3.5, 2.5, 2), 'fg_eye', glow=True)                       # glowing eyes
head.cube((6.5, -7, -14.6), (3.5, 2.5, 2), 'fg_eye', glow=True)
head.cube((-7, -5, -25), (14, 10, 13), 'fg_muzzle')                                   # muzzle
head.cube((-5.5, -4, -28), (11, 8, 4), 'fg_muzzle', decals=[{'type': 'nostrils', 'at': 0.1}])
head.cube((-4, -7, -25), (8, 2.5, 13), 'fg_fur_plain')                                # nose bridge
head.cube((-2.5, -5.5, -29), (5, 2.5, 2), 'fg_nose')
head.cube((-7, 2, -26), (14, 5, 13), 'fg_fur_light')                                 # jowls / flews
head.cube((-5.5, 7, -27), (11, 1.5, 10), 'teeth')
# sabre fangs (outside the narrow lower jaw)
fang_l = head.bone('fang_l', (6, 7, -22), (6, 0, -3))
fang_l.cube((-1.5, -1, -1.5), (3, 10, 3), 'fg_fang')
fang_tip_l = fang_l.bone('fang_tip_l', (0, 9, 0), (12, 0, 0))
fang_tip_l.cube((-1, -0.5, -1), (2, 8, 2), 'fg_fang', decals=[{'type': 'tip', 'axis': 'down', 'frac': 0.4, 'mat': 'fg_ice_tip'}])
fang_tip_l.cube((-0.5, 7, -0.5), (1, 3, 1), 'fg_ice_tip')
mirror_tree(head, fang_l)
# triangular tufted ears (planes)
ear_l = head.bone('ear_l', (7, -9, -5), (-8, 0, 20))
ear_l.cube((-3.5, -2, -1), (7, 3, 2), 'fg_fur_plain')
ear_l.cube((-5, -13, 0), (10, 14, 0), 'fg_fur_plain', faces={'north': 'fg_fur_light', 'south': 'fg_dark'}, decals=[{'type': 'cutout', 'shape': 'spike'}])
ear_l.cube((-3.2, -10, -0.3), (6.4, 9, 0), 'fg_ear_inner', decals=[{'type': 'cutout', 'shape': 'spike'}])
ear_l.cube((-1.25, -18, 0), (2.5, 6, 0), 'fg_tuft', decals=[{'type': 'cutout', 'shape': 'spike'}])
ear_l.cube((0, -18, -1.25), (0, 6, 2.5), 'fg_tuft', decals=[{'type': 'cutout', 'shape': 'spike'}])
mirror_tree(head, ear_l)
# lynx cheek ruffs
for i, (y, z, ry, rz, h, w) in enumerate(((2, -8, -25, -105, 12, 10), (6, -5, -15, -130, 11, 9), (-2, -10, -30, -80, 9, 8), (8, -10, -5, -155, 9, 8))):
    c = tuft(head, 'ruff%d_l' % (i + 1), (12, y, z), (0, ry, rz), h, w)
    mirror_tree(head, c)
jaw = head.bone('jaw', (0, 7.5, -8), (0, 0, 0))
jaw.cube((-4.5, 0, -18), (9, 4, 18), 'fg_muzzle', faces={'top': 'mouth'})
jaw.cube((-4, -1, -17.5), (8, 1, 7), 'teeth')
jaw.cube((-2.5, 0.5, -15), (5, 1, 10), 'tongue')
jaw.cube((-6, 3, -15), (12, 3.5, 14), 'fg_fur_light')                                # chin fur
jaw.cube((-5.5, 6.5, -12), (11, 4, 0), 'fg_fur_light', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.8}])

# ------------------------------------------------------------------ ice crystals (shoulders / spine / tail tip)
csh = chest.bone('crys_sh_l', (11, -20.5, -14), (0, 0, 0))
crystal(csh, 'csh1_l', (0, 1, 0), (-8, 0, 22), 5, 16)
crystal(csh, 'csh2_l', (-3, 1, 5), (-32, 0, 8), 4, 12)
crystal(csh, 'csh3_l', (2, 1.5, -5), (24, 0, 34), 4, 9)
crystal(csh, 'csh4_l', (4, 2, 3), (-16, 0, 58), 3, 7)
crystal(csh, 'csh5_l', (-3, 2, -4), (14, 0, 4), 3, 8)
mirror_tree(chest, csh)
cbk = body.bone('crys_back', (0, -19, -6), (0, 0, 0))
crystal(cbk, 'cbk1', (0, 0, -1), (-28, 0, 0), 5, 13)
crystal(cbk, 'cbk2', (0, 0.5, 9), (-36, 0, 0), 4, 10)
crystal(cbk, 'cbk3', (0, 1, 18), (-44, 0, 0), 3, 7)
crystal(cbk, 'cbk1s_l', (3, 1, 3), (-30, 0, 30), 3, 7)
mirror_tree(cbk, cbk.children[-1])

# ------------------------------------------------------------------ fore legs
fleg_l = bone(chest, 'fleg_l', (12, -18, -22), (8, 0, 0))
ABS['fleg_l'] = None
fleg_l.cube((-6.5, -7, -7), (13, 27, 14), 'fg_fur_leg')
fleg_l.cube((-5, 14, 6.5), (10, 9, 0), 'fg_fur_light', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.6}])  # elbow feathering
farm_l = fleg_l.bone('farm_l', (0, 20, 0), (-8, 0, 0))
farm_l.cube((-5, -2, -5.5), (10, 18, 11), 'fg_fur_leg')
farm_l.cube((-5.5, 10, -6), (11, 5, 12), 'fg_rim')                                    # frost-rimmed wrist
bracer_l = farm_l.bone('bracer_l', (0, 5, 5), (0, 0, 0))
crystal(bracer_l, 'brc1_l', (1, 0, 0), (-62, 0, 10), 3, 8)
crystal(bracer_l, 'brc2_l', (-2, 3, 0), (-75, 0, -12), 3, 6)
paw_l = farm_l.bone('paw_l', (0, 17, 0), (0, 0, 0))
paw_l.cube((-6.5, 0, -9.5), (13, 5, 14), 'fg_fur_leg')
paw_l.cube((-6.5, 3, -10), (13, 2, 15), 'fg_fur_light')
for k in range(4):
    x = -6 + k * 3.2
    paw_l.cube((x, 2, -13), (2.4, 3, 4), 'fg_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'fg_ice_tip'}])
mirror_tree(chest, fleg_l)

# ------------------------------------------------------------------ hind legs
thigh_l = bone(hips, 'thigh_l', (11, -20, 29), (-20, 0, 0))
ABS['thigh_l'] = None
thigh_l.cube((-6.5, -7, -9), (13, 28, 17), 'fg_fur_leg')
shin_l = thigh_l.bone('shin_l', (0, 19, -1), (48, 0, 0))
shin_l.cube((-4.5, -2, -4.5), (9, 17, 9), 'fg_fur_leg')
shin_l.cube((-4, 4, 4.5), (8, 10, 0), 'fg_fur_light', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.6}])
meta_l = shin_l.bone('meta_l', (0, 15, 0), (-28, 0, 0))
meta_l.cube((-4, -1, -4), (8, 10, 8), 'fg_fur_leg')
hpaw_l = meta_l.bone('hpaw_l', (0, 8, 0), (0, 0, 0))
hpaw_l.cube((-5.5, 0, -9), (11, 5, 12), 'fg_fur_leg')
hpaw_l.cube((-5.5, 3, -9.5), (11, 2, 13), 'fg_fur_light')
for k in range(4):
    x = -5 + k * 2.7
    hpaw_l.cube((x, 2.5, -12), (2, 2.5, 3.5), 'fg_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'fg_ice_tip'}])
mirror_tree(hips, thigh_l)

# ------------------------------------------------------------------ bushy tail
TAIL_SEG = [(9, 9, 10, -52), (12, 12, 10, 10), (14, 14, 10, 13), (14, 14, 10, 16), (11, 11, 9, 16)]
p, piv = hips, (0, -9, 18)
TAIL = []
for i, (w, h, ln, rx) in enumerate(TAIL_SEG):
    tb = p.bone('tail%d' % (i + 1), piv, (rx, 0, 0))
    mat = 'fg_fur_light' if i == len(TAIL_SEG) - 1 else 'fg_fur_plain'
    ch = 2 if i else 1
    tb.cube((-w / 2, -h / 2 + ch, -1), (w, h - 2 * ch, ln + 2), mat)                  # chamfered (octagonal) section
    tb.cube((-w / 2 + ch, -h / 2, -1), (w - 2 * ch, h, ln + 2), mat)
    if i >= 1:
        fl = 'fg_fur_light' if i >= 3 else 'fg_fur_plain'
        tuft(tb, 'tail_top%d' % (i + 1), (0, -h / 2 + 1.5, ln * 0.35), (-64, 0, 0), 8 + i, w - 3, fl)
        tuft(tb, 'tail_bot%d' % (i + 1), (0, h / 2 - 1.5, ln * 0.35), (-116, 0, 0), 7 + i, w - 4, 'fg_fur_light')
        c = tuft(tb, 'tail_side%d_l' % (i + 1), (w / 2 - 1.5, 0, ln * 0.35), (-64, 0, 90), 7 + i, w - 4, fl)
        mirror_tree(tb, c)
    TAIL.append(tb.name)
    p, piv = tb, (0, 0, ln)
ttip = p.bone('tail_tip', (0, 0, 9), (0, 0, 0))
crystal(ttip, 'ctt1', (0, 0, 0), (-95, 0, 0), 5, 12)
crystal(ttip, 'ctt2', (0, -3, -1), (-55, 0, 0), 4, 9)
crystal(ttip, 'ctt3_l', (3, 0, -1), (-80, 0, 45), 3, 8)
mirror_tree(ttip, ttip.children[-1])
crystal(ttip, 'ctt4', (0, 3, -1), (-130, 0, 0), 3, 7)


# ------------------------------------------------------------------ helpers for checks
def lowest(bone_name):
    import numpy as np
    b = m.bones[bone_name]
    M_ = b.rest_matrix()
    ys = []
    for c in b.cubes:
        ox, oy, oz = c.origin
        dx, dy, dz = c.size
        for x in (ox, ox + dx):
            for y in (oy, oy + dy):
                for z in (oz, oz + dz):
                    ys.append((M_ @ np.array([x, y, z, 1.0]))[1])
    return max(ys)


# ------------------------------------------------------------------ animations
T = m.anim
FRONT = {'l': ('fleg_l', 'farm_l', 'paw_l'), 'r': ('fleg_r', 'farm_r', 'paw_r')}
HIND = {'l': ('thigh_l', 'shin_l', 'meta_l', 'hpaw_l'), 'r': ('thigh_r', 'shin_r', 'meta_r', 'hpaw_r')}


def _cyc(p, duty):
    """Leg cycle at phase p in [0,1): returns (swing angle -1 forward .. +1 back, lift 0..1)."""
    p %= 1.0
    if p < duty:
        return -1 + 2 * p / duty, 0.0
    q = (p - duty) / (1 - duty)
    return 1 - 2 * A.ease_in_out(q), math.sin(math.pi * q)


def gait(anim, period, phases, amp_f, amp_h, duty=0.62, lift_f=1.0, lift_h=1.0, steps=24):
    """Quadruped leg cycles. phases: dict leg -> phase offset (lf, rf, lh, rh)."""
    for side in ('l', 'r'):
        ph = phases['%sf' % side]
        up, fa, pw = FRONT[side]
        A.sample(anim, up, 'rotation', lambda t, ph=ph: (amp_f * _cyc(t / period + ph, duty)[0] - 18 * lift_f * _cyc(t / period + ph, duty)[1], 0, 0), steps)
        A.sample(anim, fa, 'rotation', lambda t, ph=ph: (48 * lift_f * _cyc(t / period + ph, duty)[1] - amp_f * 0.35 * _cyc(t / period + ph, duty)[0], 0, 0), steps)
        A.sample(anim, pw, 'rotation', lambda t, ph=ph: (-amp_f * 0.65 * _cyc(t / period + ph, duty)[0] + 40 * lift_f * _cyc(t / period + ph, duty)[1], 0, 0), steps)
        ph = phases['%sh' % side]
        th, sh, mt, hp = HIND[side]
        A.sample(anim, th, 'rotation', lambda t, ph=ph: (amp_h * _cyc(t / period + ph, duty)[0] - 16 * lift_h * _cyc(t / period + ph, duty)[1], 0, 0), steps)
        A.sample(anim, sh, 'rotation', lambda t, ph=ph: (30 * lift_h * _cyc(t / period + ph, duty)[1], 0, 0), steps)
        A.sample(anim, mt, 'rotation', lambda t, ph=ph: (-22 * lift_h * _cyc(t / period + ph, duty)[1], 0, 0), steps)
        A.sample(anim, hp, 'rotation', lambda t, ph=ph: (-amp_h * _cyc(t / period + ph, duty)[0] + 26 * lift_h * _cyc(t / period + ph, duty)[1], 0, 0), steps)


# idle: breathing, tail sway, ears twitch, head look
a = T('idle', 4.0, True)
A.sine(a, 'body', 'position', (0, 0.5, 0))
A.sine(a, 'chest', 'scale', (0.012, 0.015, 0), offset=(1, 1, 1))
A.sine(a, 'chest', 'rotation', (-0.8, 0, 0))
A.sine(a, 'neck', 'rotation', (1.8, 2.5, 0), phase=0.7)
A.sine(a, 'head', 'rotation', (-1.5, 3.5, 1.0), phase=1.4)
A.sine(a, 'jaw', 'rotation', (1.5, 0, 0), offset=(1.5, 0, 0), period=2.0)
A.chain_wave(a, TAIL, (2, 7, 0), phase_step=0.7, growth=1.12)
A.poses(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (2.6, (0, 0, 0)), (2.7, (-12, 0, 8)), (2.85, (0, 0, 0)), (4.0, (0, 0, 0))])
A.poses(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.1, (-10, 0, -8)), (1.25, (0, 0, 0)), (4.0, (0, 0, 0))])

# walk: lateral-sequence walk (LH, LF, RH, RF)
a = T('walk', 1.4, True)
gait(a, 1.4, {'lh': 0.0, 'lf': 0.25, 'rh': 0.5, 'rf': 0.75}, 20, 18, duty=0.64)
A.sine(a, 'body', 'position', (0, 0.9, 0), period=0.7, phase=0.4)
A.sine(a, 'body', 'rotation', (0, 1.5, 1.6), period=1.4)
A.sine(a, 'chest', 'rotation', (0, -2, -1.5), period=1.4, phase=0.6)
A.sine(a, 'hips', 'rotation', (0, 2, 1.5), period=1.4, phase=0.3)
A.sine(a, 'neck', 'rotation', (2.5, 0, 0), period=0.7, phase=1.2, offset=(4, 0, 0))
A.sine(a, 'head', 'rotation', (-2, 0, 0), period=0.7, phase=1.6, offset=(-4, 0, 0))
A.chain_wave(a, TAIL, (0, 6, 0), period=1.4, phase_step=0.6, growth=1.1)

# run: rotary gallop with spine flex
a = T('run', 0.8, True)
gait(a, 0.8, {'lh': 0.0, 'rh': 0.08, 'lf': 0.45, 'rf': 0.55}, 34, 34, duty=0.4, lift_f=1.1, lift_h=1.1, steps=20)
A.sine(a, 'body', 'position', (0, 2.4, 0), period=0.8, phase=0.0)
A.sine(a, 'body', 'rotation', (3.5, 0, 0), period=0.8, phase=0.3)
A.sine(a, 'chest', 'rotation', (5, 0, 0), period=0.8, phase=2.0)
A.sine(a, 'hips', 'rotation', (-6, 0, 0), period=0.8, phase=2.4)
A.hold(a, 'neck', 'rotation', (14, 0, 0))
A.sine(a, 'head', 'rotation', (4, 0, 0), period=0.8, phase=0.5, offset=(-10, 0, 0))
A.sine(a, 'ear_l', 'rotation', (0, 0, 0), offset=(-22, 0, 0))
A.sine(a, 'ear_r', 'rotation', (0, 0, 0), offset=(-22, 0, 0))
A.chain_wave(a, TAIL, (6, 2, 0), period=0.8, phase_step=0.5, growth=1.05)


CRYS = ['csh1_l', 'csh2_l', 'csh3_l', 'csh1_r', 'csh2_r', 'csh3_r', 'cbk1', 'cbk2', 'cbk3']
LEGS_F = ['fleg_l', 'fleg_r']
LEGS_H = ['thigh_l', 'thigh_r']


def P(anim, bone_, chan, keys):
    """Shorthand for explicit poses."""
    A.poses(anim, bone_, chan, keys)


def both(anim, bones, chan, keys):
    for b in bones:
        A.poses(anim, b, chan, keys)


def crystal_pulse(anim, t_peak, t_end, amount=0.14, t_start=0.0):
    for c in CRYS:
        A.poses(anim, c, 'scale', [(t_start, (1, 1, 1)), (t_peak, (1 + amount * 0.6, 1 + amount, 1 + amount * 0.6)), (t_end, (1, 1, 1))])


def ears_back(anim, keys):
    P(anim, 'ear_l', 'rotation', [(t, (v * -1.0, 0, v * 0.6)) for t, v in keys])
    P(anim, 'ear_r', 'rotation', [(t, (v * -1.0, 0, -v * 0.6)) for t, v in keys])


# roar (44 ticks): dip, rear back, head high, jaw wide, crystals flare, tail lashing
a = T('roar', 2.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (3, 0, 0)), (0.55, (-10, 0, 0)), (1.7, (-11, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, 2, 0)), (0.55, (0, -2, 2)), (1.7, (0, -2, 2)), (2.2, (0, 0, 0))])
P(a, 'chest', 'rotation', [(0, (0, 0, 0)), (0.55, (-5, 0, 0)), (1.7, (-5, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.25, (14, 0, 0)), (0.55, (-30, 0, 0)), (0.8, (-28, -7, 0)), (1.1, (-30, 7, 0)), (1.4, (-28, -5, 0)), (1.7, (-26, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.25, (8, 0, 0)), (0.55, (-22, 0, 0)), (1.7, (-20, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (6, 0, 0)), (0.6, (44, 0, 0)), (1.6, (40, 0, 0)), (2.0, (0, 0, 0))])
ears_back(a, [(0, 0), (0.4, 32), (1.7, 32), (2.2, 0)])
both(a, LEGS_F, 'rotation', [(0, (0, 0, 0)), (0.55, (-10, 0, 0)), (1.7, (-10, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.5, (30, 0, 0)), (1.7, (28, 0, 0)), (2.2, (0, 0, 0))])
A.chain_wave(a, TAIL[1:], (4, 12, 0), period=0.55, phase_step=0.6)
crystal_pulse(a, 0.7, 1.8, 0.12)

# flinch (24 ticks)
a = T('flinch', 1.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.15, (6, -6, -8)), (0.5, (-2, 3, 4)), (1.2, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.15, (1, -2, 5)), (0.6, (0, 1, 2)), (1.2, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.15, (-20, 24, 0)), (0.6, (8, -8, 0)), (1.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.2, (32, 0, 0)), (0.8, (8, 0, 0)), (1.2, (0, 0, 0))])
ears_back(a, [(0, 0), (0.15, 35), (0.9, 10), (1.2, 0)])
A.chain_wave(a, TAIL, (6, 10, 0), period=0.6, phase_step=0.5)


def claw(name, side):
    """Fore-paw swipe (28 ticks): wind-up 0.45 s, swipe 0.55-0.65 s (hit ticks 10-13)."""
    g = 1 if side == 'l' else -1
    o = 'r' if side == 'l' else 'l'
    a = T(name, 1.4)
    P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.45, (-8, -8 * g, -6 * g)), (0.62, (4, 10 * g, 4 * g)), (0.9, (2, 4 * g, 1 * g)), (1.4, (0, 0, 0))])
    P(a, 'body', 'position', [(0, (0, 0, 0)), (0.45, (0, -2, 2)), (0.62, (0, 1, -4)), (1.4, (0, 0, 0))])
    P(a, 'chest', 'rotation', [(0, (0, 0, 0)), (0.45, (-6, 0, -6 * g)), (0.62, (4, 0, 6 * g)), (1.4, (0, 0, 0))])
    P(a, 'fleg_' + side, 'rotation', [(0, (0, 0, 0)), (0.45, (-78, 0, -38 * g)), (0.58, (-50, 0, 10 * g)), (0.66, (-34, 0, 34 * g)), (0.85, (-16, 0, 12 * g)), (1.3, (0, 0, 0))])
    P(a, 'farm_' + side, 'rotation', [(0, (0, 0, 0)), (0.45, (48, 0, 0)), (0.6, (-8, 0, 0)), (0.85, (6, 0, 0)), (1.3, (0, 0, 0))])
    P(a, 'paw_' + side, 'rotation', [(0, (0, 0, 0)), (0.45, (34, 0, 0)), (0.6, (-24, 0, 0)), (0.9, (-6, 0, 0)), (1.3, (0, 0, 0))])
    P(a, 'fleg_' + o, 'rotation', [(0, (0, 0, 0)), (0.45, (-8, 0, 4 * g)), (0.62, (6, 0, -4 * g)), (1.3, (0, 0, 0))])
    P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.45, (-6, -12 * g, 0)), (0.62, (10, 14 * g, 0)), (1.4, (0, 0, 0))])
    P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.5, (24, 0, 0)), (0.7, (14, 0, 0)), (1.2, (0, 0, 0))])
    ears_back(a, [(0, 0), (0.4, 25), (1.0, 10), (1.4, 0)])
    P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.45, (10, 14 * g, 0)), (0.65, (6, -18 * g, 0)), (1.4, (0, 0, 0))])
    P(a, 'tail3', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 12 * g, 0)), (0.72, (0, -16 * g, 0)), (1.4, (0, 0, 0))])


claw('claw_l', 'l')
claw('claw_r', 'r')


def pounce_into(a, t0, t_crouch, t_launch, t_land, t_end, strength=1.0):
    """Keys for one pounce inside animation `a` (all times absolute). Returns dict bone->(chan, keys)."""
    s = strength
    K = {}

    def add(b, ch, keys):
        K.setdefault((b, ch), []).extend(keys)
    tl = t_launch
    air = (t_land - t_launch)
    add('body', 'position', [(t0, (0, 0, 0)), (t_crouch, (0, 7, 3)), (tl + 0.08, (0, -6 * s, -2)), (tl + air * 0.6, (0, -3, 0)), (t_land + 0.05, (0, 6, 0)), (t_end, (0, 0, 0))])
    add('body', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (4, 0, 0)), (tl + 0.06, (-18 * s, 0, 0)), (tl + air * 0.65, (8, 0, 0)), (t_land + 0.05, (10, 0, 0)), (t_end, (0, 0, 0))])
    add('chest', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (5, 0, 0)), (tl + 0.08, (-6, 0, 0)), (t_land + 0.05, (4, 0, 0)), (t_end, (0, 0, 0))])
    add('hips', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (-6, 0, 0)), (tl + 0.08, (8, 0, 0)), (t_land, (0, 0, 0)), (t_end, (0, 0, 0))])
    for sd in ('l', 'r'):
        add('fleg_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (14, 0, 0)), (tl + 0.1, (-78, 0, 0)), (tl + air * 0.7, (-58, 0, 0)), (t_land + 0.03, (-18, 0, 0)), (t_end, (0, 0, 0))])
        add('farm_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (-24, 0, 0)), (tl + 0.1, (8, 0, 0)), (t_land, (-6, 0, 0)), (t_end, (0, 0, 0))])
        add('paw_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (10, 0, 0)), (tl + 0.1, (-36, 0, 0)), (tl + air * 0.7, (-30, 0, 0)), (t_land, (16, 0, 0)), (t_end, (0, 0, 0))])
        add('thigh_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (-26, 0, 0)), (tl + 0.1, (58, 0, 0)), (tl + air * 0.6, (24, 0, 0)), (t_land + 0.05, (-12, 0, 0)), (t_end, (0, 0, 0))])
        add('shin_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (24, 0, 0)), (tl + 0.1, (-12, 0, 0)), (tl + air * 0.6, (16, 0, 0)), (t_land + 0.05, (14, 0, 0)), (t_end, (0, 0, 0))])
        add('meta_' + sd, 'rotation', [(t0, (0, 0, 0)), (t_crouch, (-12, 0, 0)), (tl + 0.1, (24, 0, 0)), (t_land, (-4, 0, 0)), (t_end, (0, 0, 0))])
    add('neck', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (12, 0, 0)), (tl + 0.06, (-10, 0, 0)), (tl + air * 0.7, (14, 0, 0)), (t_land + 0.05, (16, 0, 0)), (t_end, (0, 0, 0))])
    add('jaw', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (8, 0, 0)), (tl + 0.15, (36, 0, 0)), (t_land, (30, 0, 0)), (t_land + 0.15, (6, 0, 0)), (t_end, (0, 0, 0))])
    add('tail1', 'rotation', [(t0, (0, 0, 0)), (t_crouch, (6, 0, 0)), (tl + 0.1, (40, 0, 0)), (t_land, (30, 0, 0)), (t_end, (0, 0, 0))])
    add('tail2', 'rotation', [(t0, (0, 0, 0)), (tl + 0.12, (-10, 0, 0)), (t_land, (-6, 0, 0)), (t_end, (0, 0, 0))])
    return K


def apply_keys(a, K):
    for (b, ch), keys in K.items():
        keys = sorted(keys, key=lambda k: k[0])
        dedup = []
        for k in keys:
            if dedup and abs(dedup[-1][0] - k[0]) < 1e-6:
                dedup[-1] = k
            else:
                dedup.append(k)
        A.poses(a, b, ch, dedup)


# pounce (44 ticks): crouch, launch at tick 15, airborne, land ~tick 30
a = T('pounce', 2.2)
apply_keys(a, pounce_into(a, 0.0, 0.65, 0.75, 1.5, 2.2))
ears_back(a, [(0, 0), (0.5, 30), (1.6, 30), (2.2, 0)])

# double pounce (76 ticks, enraged): launches at ticks 12 and 38
a = T('double_pounce', 3.8)
K1 = pounce_into(a, 0.0, 0.5, 0.6, 1.35, 1.75, 1.0)
K2 = pounce_into(a, 1.75, 1.85, 1.92, 2.65, 3.6, 1.15)
for k, v in K2.items():
    K1.setdefault(k, []).extend(v)
apply_keys(a, K1)
ears_back(a, [(0, 0), (0.4, 32), (3.0, 32), (3.8, 0)])
P(a, 'neck', 'rotation', [(1.4, (14, 0, 0)), (1.6, (6, 18, 0)), (1.8, (8, 0, 0))])

# ice breath (64 ticks): inhale (crystals swell), straight frost breath ticks 22-46
a = T('ice_breath', 3.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.9, (-6, 0, 0)), (1.2, (4, 0, 0)), (2.3, (3, 0, 0)), (3.2, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.9, (0, -1, 3)), (1.2, (0, 1, -2)), (2.3, (0, 1, -2)), (3.2, (0, 0, 0))])
P(a, 'chest', 'scale', [(0, (1, 1, 1)), (0.9, (1.06, 1.07, 1.0)), (1.25, (0.98, 0.98, 1.0)), (2.3, (0.97, 0.97, 1.0)), (2.8, (1, 1, 1))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.9, (-28, 0, 0)), (1.12, (16, 0, 0)), (1.5, (14, 3, 0)), (1.9, (14, -3, 0)), (2.3, (13, 0, 0)), (3.0, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.9, (-14, 0, 0)), (1.12, (0, 0, 0)), (2.3, (0, 0, 0)), (3.0, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.8, (8, 0, 0)), (1.12, (46, 0, 0)), (2.3, (44, 0, 0)), (2.7, (0, 0, 0))])
both(a, LEGS_F, 'rotation', [(0, (0, 0, 0)), (0.9, (4, 0, 0)), (1.2, (-14, 0, 0)), (2.3, (-12, 0, 0)), (3.0, (0, 0, 0))])
ears_back(a, [(0, 0), (0.8, 20), (1.2, 38), (2.4, 38), (3.0, 0)])
crystal_pulse(a, 0.95, 2.4, 0.16)
A.chain_wave(a, TAIL, (2, 6, 0), period=0.8, phase_step=0.6)

# ice shard spray (36 ticks): rear, whip the head down, shards fly at tick 15
a = T('ice_shards', 1.8)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.6, (-9, 0, 0)), (0.8, (6, 0, 0)), (1.8, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.6, (0, -2, 2)), (0.8, (0, 2, -2)), (1.8, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.6, (-24, 0, 0)), (0.78, (20, 0, 0)), (1.0, (12, 0, 0)), (1.8, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (-12, 0, 0)), (0.78, (6, 0, 0)), (1.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (14, 0, 0)), (0.78, (48, 0, 0)), (1.1, (30, 0, 0)), (1.5, (0, 0, 0))])
both(a, LEGS_F, 'rotation', [(0, (0, 0, 0)), (0.6, (-22, 0, 0)), (0.8, (4, 0, 0)), (1.8, (0, 0, 0))])
both(a, ['farm_l', 'farm_r'], 'rotation', [(0, (0, 0, 0)), (0.6, (18, 0, 0)), (0.8, (-4, 0, 0)), (1.8, (0, 0, 0))])
ears_back(a, [(0, 0), (0.6, 30), (1.3, 15), (1.8, 0)])
crystal_pulse(a, 0.62, 0.85, 0.18)

# belly slide (56 ticks): drop onto the belly and toboggan forward (ticks 13-40), then get up
a = T('belly_slide', 2.8)
SL0, SL1 = 0.62, 2.02
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.4, (0, 7, 0)), (SL0, (0, 19, 0)), (SL1, (0, 19, 0)), (2.45, (0, 5, 0)), (2.8, (0, 0, 0))])
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (6, 0, 0)), (SL0, (2, 0, 0)), (1.0, (2, 0, 3)), (1.4, (2, 0, -3)), (1.8, (2, 0, 2)), (SL1, (2, 0, 0)), (2.8, (0, 0, 0))])
for sd in ('l', 'r'):
    P(a, 'fleg_' + sd, 'rotation', [(0, (0, 0, 0)), (0.4, (-30, 0, 0)), (SL0, (-82, 0, 0)), (SL1, (-82, 0, 0)), (2.45, (-20, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'farm_' + sd, 'rotation', [(0, (0, 0, 0)), (SL0, (-6, 0, 0)), (SL1, (-6, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'paw_' + sd, 'rotation', [(0, (0, 0, 0)), (SL0, (84, 0, 0)), (SL1, (84, 0, 0)), (2.5, (10, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'thigh_' + sd, 'rotation', [(0, (0, 0, 0)), (0.4, (24, 0, 0)), (SL0, (98, 0, 0)), (SL1, (98, 0, 0)), (2.45, (14, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'shin_' + sd, 'rotation', [(0, (0, 0, 0)), (SL0, (-44, 0, 0)), (SL1, (-44, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'meta_' + sd, 'rotation', [(0, (0, 0, 0)), (SL0, (26, 0, 0)), (SL1, (26, 0, 0)), (2.8, (0, 0, 0))])
    P(a, 'hpaw_' + sd, 'rotation', [(0, (0, 0, 0)), (SL0, (70, 0, 0)), (SL1, (70, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (SL0, (4, 0, 0)), (SL1, (4, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (SL0, (-8, 0, 0)), (SL1, (-8, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (SL0, (18, 0, 0)), (SL1, (14, 0, 0)), (2.5, (0, 0, 0))])
P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (SL0, (46, 0, 0)), (SL1, (44, 0, 0)), (2.8, (0, 0, 0))])
for i, b in enumerate(TAIL[1:]):
    P(a, b, 'rotation', [(0, (0, 0, 0)), (SL0, (-10, 0, 0)), (SL1, (-10, 0, 0)), (2.8, (0, 0, 0))])
ears_back(a, [(0, 0), (0.5, 40), (2.1, 40), (2.8, 0)])

# tail slam (46 ticks): hindquarters kick up, tail slams down at tick 16, ice spikes race forward
a = T('tail_slam', 2.3)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.7, (10, 0, 0)), (0.85, (-4, 0, 0)), (1.3, (-1, 0, 0)), (2.3, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.7, (0, -2, 0)), (0.85, (0, 3, 0)), (1.3, (0, 1, 0)), (2.3, (0, 0, 0))])
P(a, 'hips', 'rotation', [(0, (0, 0, 0)), (0.7, (12, 0, 0)), (0.85, (-6, 0, 0)), (1.3, (0, 0, 0)), (2.3, (0, 0, 0))])
P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.68, (80, 0, 0)), (0.8, (8, 0, 0)), (0.9, (12, 0, 0)), (1.4, (6, 0, 0)), (2.3, (0, 0, 0))])
for b in TAIL[1:]:
    P(a, b, 'rotation', [(0, (0, 0, 0)), (0.68, (14, 0, 0)), (0.8, (-6, 0, 0)), (0.95, (2, 0, 0)), (1.4, (0, 0, 0)), (2.3, (0, 0, 0))])
both(a, LEGS_H, 'rotation', [(0, (0, 0, 0)), (0.7, (16, 0, 0)), (0.85, (-8, 0, 0)), (2.3, (0, 0, 0))])
both(a, LEGS_F, 'rotation', [(0, (0, 0, 0)), (0.7, (-12, 0, 0)), (0.85, (-4, 0, 0)), (2.3, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.7, (14, 0, 0)), (0.85, (-12, 0, 0)), (1.3, (-6, 0, 0)), (2.3, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.7, (10, 0, 0)), (0.86, (30, 0, 0)), (1.4, (8, 0, 0)), (2.0, (0, 0, 0))])
crystal_pulse(a, 0.82, 1.4, 0.16, 0.6)
ears_back(a, [(0, 0), (0.6, 25), (1.5, 25), (2.3, 0)])

# ice field (60 ticks, enraged): rear up and howl, slam both forepaws at tick 22 -> freezing field
a = T('ice_field', 3.0)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.7, (-30, 0, 0)), (0.95, (-32, 0, 0)), (1.12, (8, 0, 0)), (1.6, (4, 0, 0)), (2.6, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.7, (0, -11, 17)), (0.95, (0, -12, 18)), (1.12, (0, 4, 0)), (1.6, (0, 2, 0)), (2.6, (0, 0, 0))])
both(a, LEGS_H, 'rotation', [(0, (0, 0, 0)), (0.7, (30, 0, 0)), (0.95, (32, 0, 0)), (1.12, (-8, 0, 0)), (2.6, (0, 0, 0))])
both(a, ['shin_l', 'shin_r'], 'rotation', [(0, (0, 0, 0)), (0.7, (-6, 0, 0)), (1.12, (8, 0, 0)), (2.6, (0, 0, 0))])
for sd, g in (('l', 1), ('r', -1)):
    P(a, 'fleg_' + sd, 'rotation', [(0, (0, 0, 0)), (0.7, (-40, 0, -8 * g)), (0.95, (-52, 0, -10 * g)), (1.1, (4, 0, -14 * g)), (1.6, (2, 0, -6 * g)), (2.6, (0, 0, 0))])
    P(a, 'farm_' + sd, 'rotation', [(0, (0, 0, 0)), (0.7, (30, 0, 0)), (0.95, (36, 0, 0)), (1.1, (-6, 0, 0)), (2.6, (0, 0, 0))])
    P(a, 'paw_' + sd, 'rotation', [(0, (0, 0, 0)), (0.95, (20, 0, 0)), (1.1, (0, 0, 0)), (2.6, (0, 0, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.5, (-34, 0, 0)), (0.95, (-36, 0, 0)), (1.12, (18, 0, 0)), (1.6, (12, 0, 0)), (2.6, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (-16, 0, 0)), (0.95, (-16, 0, 0)), (1.12, (0, 0, 0)), (2.6, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.45, (40, 0, 0)), (0.95, (42, 0, 0)), (1.12, (24, 0, 0)), (1.6, (10, 0, 0)), (2.2, (0, 0, 0))])
crystal_pulse(a, 1.0, 2.0, 0.2, 0.3)
ears_back(a, [(0, 0), (0.5, 35), (1.6, 35), (2.6, 0)])
P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.7, (34, 0, 0)), (0.95, (36, 0, 0)), (1.12, (24, 0, 0)), (2.6, (0, 0, 0))])

# death: stagger and collapse onto the left flank, legs limp (holds the last frame)
a = T('death', 2.0)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, 0, -6)), (1.2, (4, 0, 84)), (1.45, (4, 0, 78)), (2.0, (4, 0, 80))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -2, 0)), (1.2, (4, 19, 0)), (1.45, (4, 17, 0)), (2.0, (4, 18, 0))])
P(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.2, (12, 0, -10)), (2.0, (14, 0, -14))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (1.2, (6, 0, -8)), (2.0, (10, 0, -10))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.35, (36, 0, 0)), (1.4, (20, 0, 0)), (2.0, (16, 0, 0))])
for sd in ('l', 'r'):
    P(a, 'fleg_' + sd, 'rotation', [(0, (0, 0, 0)), (1.2, (-24, 0, 0)), (2.0, (-20, 0, 0))])
    P(a, 'farm_' + sd, 'rotation', [(0, (0, 0, 0)), (1.2, (20, 0, 0)), (2.0, (16, 0, 0))])
    P(a, 'thigh_' + sd, 'rotation', [(0, (0, 0, 0)), (1.2, (26, 0, 0)), (2.0, (22, 0, 0))])
    P(a, 'shin_' + sd, 'rotation', [(0, (0, 0, 0)), (1.2, (-20, 0, 0)), (2.0, (-18, 0, 0))])
ears_back(a, [(0, 0), (1.2, 30), (2.0, 34)])
for i, b in enumerate(TAIL):
    P(a, b, 'rotation', [(0, (0, 0, 0)), (1.2, (-4 if i else 44, 6, 0)), (2.0, (-5 if i else 46, 8, 0))])

# sleep: lying sphinx-like, head resting on the forepaws, tail curled, slow breathing
a = T('sleep', 4.0, True)
A.sine(a, 'body', 'position', (0, 0.5, 0), offset=(0, 20, 0))
A.sine(a, 'chest', 'scale', (0.02, 0.025, 0), offset=(1, 1, 1))
for sd, g in (('l', 1), ('r', -1)):
    A.hold(a, 'fleg_' + sd, 'rotation', (-80, 0, 0))
    A.hold(a, 'farm_' + sd, 'rotation', (-4, 0, 0))
    A.hold(a, 'paw_' + sd, 'rotation', (84, 0, 0))
    A.hold(a, 'thigh_' + sd, 'rotation', (-58, 0, -14 * g))
    A.hold(a, 'shin_' + sd, 'rotation', (110, 0, 0))
    A.hold(a, 'meta_' + sd, 'rotation', (-60, 0, 0))
    A.hold(a, 'hpaw_' + sd, 'rotation', (8, 0, 0))
A.hold(a, 'neck', 'rotation', (16, -10, 0))
A.hold(a, 'head', 'rotation', (0, -6, 0))
A.hold(a, 'jaw', 'rotation', (0, 0, 0))
A.hold(a, 'ear_l', 'rotation', (10, 0, 22))
A.hold(a, 'ear_r', 'rotation', (10, 0, -22))
for i, b in enumerate(TAIL):
    A.hold(a, b, 'rotation', (8 if i == 0 else 4, -22, 0))

# trapped: sunk in the trap, struggling
a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 9, 0))
A.sine(a, 'body', 'rotation', (5, 5, 7), period=0.5)
A.sine(a, 'neck', 'rotation', (-10, 22, 0), period=0.5, phase=1.0, offset=(-10, 0, 0))
A.sine(a, 'jaw', 'rotation', (14, 0, 0), period=0.25, offset=(22, 0, 0))
for sd, ph in (('l', 0), ('r', math.pi)):
    A.sine(a, 'fleg_' + sd, 'rotation', (26, 0, 0), period=0.5, phase=ph, offset=(-20, 0, 0))
    A.sine(a, 'farm_' + sd, 'rotation', (20, 0, 0), period=0.5, phase=ph + 1, offset=(16, 0, 0))
    A.sine(a, 'thigh_' + sd, 'rotation', (16, 0, 0), period=0.5, phase=ph + 0.5)
ears_back(a, [(0, 30), (1.0, 30)])
A.chain_wave(a, TAIL, (10, 16, 0), period=0.5, phase_step=0.6)

# stun: dazed and swaying, head lolling
a = T('stun', 1.6, True)
A.hold(a, 'body', 'position', (0, 6, 0))
A.sine(a, 'body', 'rotation', (0, 0, 7), period=1.6)
A.sine(a, 'neck', 'rotation', (6, 22, 0), period=1.6, phase=0.6, offset=(26, 0, 0))
A.sine(a, 'head', 'rotation', (0, 0, 14), period=0.8, offset=(-6, 0, 0))
A.hold(a, 'jaw', 'rotation', (20, 0, 0))
for sd, g in (('l', 1), ('r', -1)):
    A.hold(a, 'fleg_' + sd, 'rotation', (-8, 0, -10 * g))
    A.hold(a, 'thigh_' + sd, 'rotation', (6, 0, -8 * g))
A.hold(a, 'ear_l', 'rotation', (16, 0, 40))
A.hold(a, 'ear_r', 'rotation', (16, 0, -40))
for b in TAIL:
    A.hold(a, b, 'rotation', (-6, 4, 0))

if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft')
    img, glow = m.export(root)
    print('texture', m.tex_w, m.tex_h, 'cubes', sum(1 for _ in m.all_cubes()), 'bones', len(m.bones), 'anims', len(m.animations))
    for b in ('paw_l', 'hpaw_l', 'paw_r', 'hpaw_r'):
        print('lowest', b, round(lowest(b), 2))
    if '--preview' in sys.argv:
        from hcmodel.render import preview
        sel = [x for x in sys.argv[1:] if not x.startswith('--')]
        preview(m, img, os.path.join(os.path.dirname(__file__), '..', 'preview', 'frostgarm.png'), anims=sel or None, size=260)
