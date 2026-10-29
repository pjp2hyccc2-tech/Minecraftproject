"""Voltrix (雷鱗竜ヴォルトリクス) - long serpentine leviathan, thunder element.

Dark navy scales with yellow lightning-stripe accents, pale belly plates, four short sprawling legs, dorsal
fins, swept horns + crest, whisker barbels and two rows of glowing electric organs along the back
(they pulse brighter while enraged - handled by the renderer). ~3 blocks tall (head raised), ~10 long.
Run from tools/:  python3 models/voltrix.py --preview [anim ...]
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel.paint import M  # noqa: E402
import hcmodel.paint as PAINT  # noqa: E402
from hcmodel.parts import mirror_tree  # noqa: E402

# ------------------------------------------------------------------ materials (module-local)
_orig_accent = PAINT._accent_mask


def _vx_accent(mat, P, f, d, r, seed):
    """Zig-zag lightning stripes running down the flanks (fade out toward the belly)."""
    if mat.accent_mode == 'vx_zigzag':
        x, y, z = P[..., 0], P[..., 1], P[..., 2]
        tri = np.abs(((y + 40) / 5.0) % 2.0 - 1.0) * 2 - 1          # triangle wave along height
        n = PAINT.vnoise(x * 0.2, y * 0.2, z * 0.2, 33)
        u = (z + tri * 3.2 + n * 2.0) % mat.accent_freq
        band = np.abs(u - mat.accent_freq / 2) < mat.accent_thresh
        top = y < 7.0                                                # keep the belly plain
        return band & top
    return _orig_accent(mat, P, f, d, r, seed)


PAINT._accent_mask = _vx_accent

M('vx_scale', ['#0b1726', '#132842', '#1c3a5e', '#285081', '#3a6aa3', '#5687c2'], 'scales', belly='vx_belly',
  accent='vx_stripe', accent_mode='vx_zigzag', accent_freq=15.0, accent_thresh=1.3, edge=0.07, base=0.55)
M('vx_scale_plain', ['#0b1726', '#132842', '#1c3a5e', '#285081', '#3a6aa3', '#5687c2'], 'scales', belly='vx_belly', edge=0.07, base=0.55)
M('vx_dark', ['#060c16', '#0b1626', '#122339', '#1b3250', '#26456a', '#355a86'], 'plates', belly='vx_belly', edge=0.08, base=0.5)
M('vx_stripe', ['#3a2c06', '#6e560c', '#a88814', '#dcb420', '#f6d84a', '#fff196'], 'scales', edge=0.0, base=0.62)
M('vx_belly', ['#3b3a2c', '#5e5c45', '#86836a', '#aba98e', '#cfcdb4', '#ebe9d6'], 'plates', scale=0.7, edge=0.06, base=0.58)
M('vx_fin', ['#050b14', '#0b1828', '#14263d', '#1f3755', '#2c4b70', '#3d628f'], 'membrane', edge=0.0, base=0.55)
M('vx_horn', ['#090c12', '#141a24', '#222b39', '#343f51', '#4b586d', '#67768d'], 'horn', edge=0.05)
M('vx_tip', ['#5c4a00', '#a88a00', '#e6c200', '#ffe23a', '#fff27a', '#ffffc0'], 'smooth', glow='#fff07a', edge=0.0, base=0.62)
M('vx_organ', ['#5c4a00', '#9a7c00', '#d8b400', '#ffdf2e', '#fff27a', '#ffffc8'], 'veins', glow='#fff36a', edge=0.0, base=0.66)
M('vx_eye', ['#6a5200', '#b08c00', '#ecc400', '#ffe23a', '#fff59a', '#ffffe0'], 'smooth', glow='#ffee66', edge=0.0, base=0.66)
M('vx_claw', ['#0b0d12', '#171b23', '#262c38', '#3a4252', '#535d70', '#727d92'], 'horn', edge=0.05)

m = Model('voltrix')
m.config.update({'head_bones': [['neck1', 0.25], ['neck2', 0.3], ['head', 0.45]], 'walk_speed': 1.6, 'run_speed': 0.75, 'walk_scale': 2.6})


def organ_row(b, zs, x, y, w=3, h=3, ln=4):
    """Pair of glowing organ nodules (left/right) at each z (bone-local)."""
    for z in zs:
        b.cube((x - w / 2, y, z), (w, h, ln), 'vx_organ', glow=True)
        b.cube((-x - w / 2, y, z), (w, h, ln), 'vx_organ', glow=True)


def fin(b, z0, ln, y, h, tip='vx_stripe'):
    """Dorsal fin spike plane along the midline, base at bone-local y, spanning z0..z0+ln."""
    return b.cube((0, y - h, z0), (0, h + 1, ln), 'vx_fin', decals=[{'type': 'cutout', 'shape': 'spike'}, {'type': 'tip', 'axis': 'up', 'frac': 0.3, 'mat': tip}])


def seg(b, z0, ln, w, h, cy=0.0, mat='vx_scale', ch=3.0):
    """Chamfered (octagonal) body section centred on x=0, y=cy, spanning z0..z0+ln."""
    b.cube((-w / 2, cy - h / 2 + ch, z0), (w, h - 2 * ch, ln), mat)
    b.cube((-w / 2 + ch, cy - h / 2, z0), (w - 2 * ch, h, ln), mat)


def electrode(parent, name, pivot, rot, h, w=3):
    b = parent.bone(name, pivot, rot)
    b.cube((-w / 2, -h, -w / 2), (w, h, w), 'vx_horn', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.3, 'mat': 'vx_stripe'}])
    b.cube((-(w - 1) / 2, -h - 3, -(w - 1) / 2), (w - 1, 4, w - 1), 'vx_tip', glow=True)
    return b


def frill(parent, name, pivot, rot, h, w):
    b = parent.bone(name, pivot, rot)
    b.cube((0, -h, -w / 2), (0, h, w), 'vx_fin', decals=[{'type': 'cutout', 'shape': 'spike'}, {'type': 'tip', 'axis': 'up', 'frac': 0.3, 'mat': 'vx_tip'}])
    return b


# ------------------------------------------------------------------ body chain (root = chest segment)
body = m.bone('body', (0, 2, -10))
seg(body, -12, 23, 20, 18)
body.cube((-7, -10.5, -10), (14, 2, 20), 'vx_dark')
fin(body, -9, 9, -10, 8)
fin(body, -1, 10, -10, 10)
organ_row(body, (-9, -2, 5), 7.8, -9.6, 3.6, 3.6, 4.5)

body2 = body.bone('body2', (0, 0, 10))
seg(body2, -1, 22, 21, 19)
body2.cube((-7.5, -11, 0), (15, 2, 21), 'vx_dark')
organ_row(body2, (0.5, 7.5, 14.5), 8.3, -10.0, 3.8, 3.8, 5)
back_el = body2.bone('back_electrodes', (0, -11, 0), (0, 0, 0))
electrode(back_el, 'elec1', (0, 0, 4), (-28, 0, 0), 10)
electrode(back_el, 'elec2', (0, 0, 12), (-32, 0, 0), 13)
electrode(back_el, 'elec3', (0, 0, 19), (-36, 0, 0), 10)

body3 = body2.bone('body3', (0, 0.5, 21))
seg(body3, -1, 21, 19, 18)
body3.cube((-6.5, -10.5, 0), (13, 2, 19), 'vx_dark')
fin(body3, 0, 10, -10, 10)
fin(body3, 9, 10, -10, 8)
organ_row(body3, (1.5, 8.5, 14.5), 7.6, -9.6, 3.6, 3.6, 4.5)

TAIL_SEG = [(15, 14, 14, -4), (12, 11.5, 13, -3), (9.5, 9, 12, 0), (7, 7, 11, 3), (5, 5, 10, 5)]
p, piv = body3, (0, 0.5, 19)
TAIL = []
for i, (w, h, ln, rx) in enumerate(TAIL_SEG):
    tb = p.bone('tail%d' % (i + 1), piv, (rx, 0, 0))
    seg(tb, -1, ln + 2, w, h, 0, 'vx_scale' if i < 3 else 'vx_scale_plain', ch=max(1.0, 2.5 - i * 0.4))
    if i < 4:
        fin(tb, 1, ln - 2, -h / 2, max(4, 9 - i * 1.6))
    if i < 3:
        organ_row(tb, (2, 8) if i < 2 else (3,), w / 2 - 1.5, -h / 2 - 0.5, 2.8, 2.8, 3.5)
    TAIL.append(tb.name)
    p, piv = tb, (0, 0, ln)
tfin = p.bone('tail_fin', (0, 0, 10), (0, 0, 0))
tf_up = tfin.bone('tail_fin_up', (0, -1, -2), (-62, 0, 0))
tf_up.cube((0, -17, -5.5), (0, 17, 11), 'vx_fin', decals=[{'type': 'cutout', 'shape': 'spike'}, {'type': 'tip', 'axis': 'up', 'frac': 0.35, 'mat': 'vx_tip'}])
tf_dn = tfin.bone('tail_fin_dn', (0, 1, -2), (-118, 0, 0))
tf_dn.cube((0, -14, -4.5), (0, 14, 9), 'vx_fin', decals=[{'type': 'cutout', 'shape': 'spike'}, {'type': 'tip', 'axis': 'up', 'frac': 0.35, 'mat': 'vx_tip'}])
tfin.cube((-1.5, -1.5, -1), (3, 3, 6), 'vx_horn')
tfin.cube((-1, -1, 4), (2, 2, 5), 'vx_tip', glow=True)

# ------------------------------------------------------------------ neck & head
neck1 = body.bone('neck1', (0, -4, -10), (-36, 0, 0))
seg(neck1, -17, 19, 15, 15, 0, 'vx_scale', 2.5)
neck1.cube((-5, -8.5, -15), (10, 2, 15), 'vx_dark')
fin(neck1, -14, 10, -8, 7)
organ_row(neck1, (-13, -6), 5.8, -8, 2.8, 2.8, 3.5)
neck2 = neck1.bone('neck2', (0, 0, -15), (-14, 0, 0))
seg(neck2, -16, 18, 13, 13, 0, 'vx_scale', 2.0)
neck2.cube((-4, -7.5, -14), (8, 2, 13), 'vx_dark')
fin(neck2, -13, 10, -7, 6)

head = neck2.bone('head', (0, 0, -15), (50, 0, 0))
seg(head, -14, 16, 18, 12, -0.5, 'vx_scale_plain', 2.5)                               # skull
head.cube((2.5, -8.5, -16), (7.5, 3, 8), 'vx_dark')                                  # brow ridges
head.cube((-10, -8.5, -16), (7.5, 3, 8), 'vx_dark')
head.cube((-4, -8, -9), (8, 2, 9), 'vx_dark')                                        # forehead plate
head.cube((-6, -4.5, -24), (12, 8.5, 11), 'vx_scale_plain')                          # snout (tapering)
head.cube((-5, -4, -29), (10, 7.5, 6), 'vx_scale_plain')
head.cube((-4, -3.5, -32.5), (8, 6.5, 4), 'vx_scale_plain', decals=[{'type': 'nostrils', 'at': 0.2}])
head.cube((-2.5, -5.5, -31), (5, 1.5, 17), 'vx_dark')                                # nasal ridge
head.cube((-6.5, 0.5, -23), (13, 3.5, 9), 'vx_dark')                                 # lip scales
head.cube((-4.8, 4, -32), (9.6, 1.5, 18), 'teeth', decals=[{'type': 'teeth', 'face': 'west'}, {'type': 'teeth', 'face': 'east'}])
head.cube((-9.6, -5.2, -14.6), (3, 2.5, 4), 'vx_eye', glow=True)                      # eyes
head.cube((6.6, -5.2, -14.6), (3, 2.5, 4), 'vx_eye', glow=True)
crest = head.bone('crest', (0, -8, -8), (-22, 0, 0))
crest.cube((0, -15, -3), (0, 15, 18), 'vx_fin', decals=[{'type': 'cutout', 'shape': 'spike'}, {'type': 'tip', 'axis': 'up', 'frac': 0.35, 'mat': 'vx_tip'}])
crest.cube((-1, -3, 0), (2, 3, 13), 'vx_horn')
horn_l = head.bone('horn_l', (6, -6.5, -3), (26, 22, 0))
horn_l.cube((-2, -2, -1), (4, 4, 13), 'vx_horn')
horn_tip_l = horn_l.bone('horn_tip_l', (0, 0, 11), (-24, 0, 0))
horn_tip_l.cube((-1.5, -1.5, 0), (3, 3, 10), 'vx_horn', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.4, 'mat': 'vx_stripe'}])
horn_tip_l.cube((-1, -1, 9), (2, 2, 4), 'vx_tip', glow=True)
mirror_tree(head, horn_l)
for i, (y, z, ry, rx, h, w) in enumerate(((1, -5, 65, -95, 13, 9), (-3, -3, 55, -70, 10, 8), (4.5, -2, 72, -120, 10, 7))):
    fr = frill(head, 'frill%d_l' % (i + 1), (8.5, y, z), (rx, ry, 0), h, w)
    mirror_tree(head, fr)
whisk_l = head.bone('whisker_l', (4, 2.5, -27), (-28, 34, 0))
whisk_l.cube((-0.75, -0.75, 0), (1.5, 1.5, 10), 'vx_horn')
wk2_l = whisk_l.bone('whisker2_l', (0, 0, 9.5), (-16, 14, 0))
wk2_l.cube((-0.75, -0.75, 0), (1.5, 1.5, 10), 'vx_stripe')
wk2_l.cube((-0.75, -0.75, 9), (1.5, 1.5, 3), 'vx_tip', glow=True)
mirror_tree(head, whisk_l)
jaw = head.bone('jaw', (0, 4.5, -12), (0, 0, 0))
jaw.cube((-5, 0, -19.5), (10, 3.5, 20), 'vx_scale_plain', faces={'top': 'mouth'})
jaw.cube((-4.5, -1.2, -19), (9, 1.2, 16), 'teeth', decals=[{'type': 'teeth', 'face': 'west', 'edge': 'top'}, {'type': 'teeth', 'face': 'east', 'edge': 'top'}])
jaw.cube((-2, 0.4, -17), (4, 1, 15), 'tongue')
jaw.cube((-6, 2.5, -14), (12, 2.5, 14), 'vx_belly')                                  # throat plates


# ------------------------------------------------------------------ legs (short, sprawling)
def leg(parent, name, pivot, upper, lower, paw, splay=58, toes=3):
    u = parent.bone(name + '_up', pivot, (0, 0, -splay))
    uw, uh, ud = upper
    u.cube((-uw / 2, -2, -ud / 2), (uw, uh, ud), 'vx_scale_plain')
    lw, lh, ld = lower
    lo = u.bone(name + '_mid', (0, uh - 3, 0), (0, 0, splay))
    lo.cube((-lw / 2, -1.5, -ld / 2), (lw, lh, ld), 'vx_scale_plain')
    lo.cube((-lw / 2 - 0.5, -2, -ld / 2 - 0.5), (lw + 1, 3, ld + 1), 'vx_dark')        # elbow plate
    pw, ph, pd = paw
    pa = lo.bone(name + '_paw', (0, lh - 1.5, 0), (0, 0, 0))
    pa.cube((-pw / 2, 0, -pd + 2), (pw, ph, pd), 'vx_scale_plain')
    tw = 1.6
    for k in range(toes):
        x = -pw / 2 + 0.6 + k * (pw - 1.2 - tw) / max(1, toes - 1)
        pa.cube((x, 0.5, -pd - 1), (tw, 2, 3.5), 'vx_claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'vx_stripe'}])
    return u, lo, pa


fl = leg(body, 'fleg_l', (8, 5, -2), (6, 12, 7), (5, 11, 6), (7, 2.5, 8))
mirror_tree(body, fl[0])
hl = leg(body3, 'hleg_l', (7.5, 4.5, 9), (7, 12, 8), (5.5, 11, 6), (7.5, 2.5, 9))
mirror_tree(body3, hl[0])


def lowest(bone_name):
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
BODY = ['body2', 'body3'] + TAIL
NECK = ['neck1', 'neck2', 'head']


def P(anim, b, chan, keys):
    A.poses(anim, b, chan, keys)



LEGS = {'fl': ('fleg_l_up', 'fleg_l_mid', 'fleg_l_paw', 1), 'fr': ('fleg_r_up', 'fleg_r_mid', 'fleg_r_paw', -1),
        'hl': ('hleg_l_up', 'hleg_l_mid', 'hleg_l_paw', 1), 'hr': ('hleg_r_up', 'hleg_r_mid', 'hleg_r_paw', -1)}
FRILLS = ['frill1_l', 'frill2_l', 'frill3_l', 'frill1_r', 'frill2_r', 'frill3_r']
WHISK = ['whisker_l', 'whisker2_l', 'whisker_r', 'whisker2_r']
CHAIN = ['body2', 'body3'] + TAIL


def _cyc(p, duty):
    p %= 1.0
    if p < duty:
        return -1 + 2 * p / duty, 0.0
    q = (p - duty) / (1 - duty)
    return 1 - 2 * A.ease_in_out(q), math.sin(math.pi * q)


def lizard_gait(anim, period, amp, lift, duty=0.6, steps=20, phases=None):
    phases = phases or {'fl': 0.0, 'hr': 0.0, 'fr': 0.5, 'hl': 0.5}
    for k, (up, lo, paw, g) in LEGS.items():
        ph = phases[k]
        A.sample(anim, up, 'rotation', lambda t, ph=ph, g=g: (amp * _cyc(t / period + ph, duty)[0], 0, -g * lift * _cyc(t / period + ph, duty)[1]), steps)
        A.sample(anim, lo, 'rotation', lambda t, ph=ph, g=g: (-amp * 0.5 * _cyc(t / period + ph, duty)[0], 0, g * lift * 0.6 * _cyc(t / period + ph, duty)[1]), steps)
        A.sample(anim, paw, 'rotation', lambda t, ph=ph: (-amp * 0.5 * _cyc(t / period + ph, duty)[0] + 25 * _cyc(t / period + ph, duty)[1], 0, 0), steps)


def undulate(anim, period, amp_body, amp_tail, neck_comp=0.8, phase=0.0, steps=None):
    """Lateral travelling wave along the body (yaw), head kept steady."""
    A.sine(anim, 'body', 'rotation', (0, amp_body * 0.5, 0), period, phase, steps=steps)
    A.chain_wave(anim, CHAIN, (0, amp_tail, 0), period, phase_step=0.75, growth=1.08, phase=phase - 0.6, steps=steps)
    A.sine(anim, 'neck1', 'rotation', (0, -amp_body * 0.5 * neck_comp, 0), period, phase, steps=steps)
    A.sine(anim, 'head', 'rotation', (0, -amp_body * 0.3 * neck_comp, 0), period, phase + 0.5, steps=steps)


def flare(anim, keys, frills=True):
    """keys: list of (t, amount 0..1) - frills spread / crest raised."""
    if frills:
        for f in FRILLS:
            g = 1 if f.endswith('_l') else -1
            P(anim, f, 'rotation', [(t, (-14 * v, 18 * v * g, 0)) for t, v in keys])
            P(anim, f, 'scale', [(t, (1, 1 + 0.35 * v, 1 + 0.2 * v)) for t, v in keys])
    P(anim, 'crest', 'rotation', [(t, (-14 * v, 0, 0)) for t, v in keys])
    P(anim, 'crest', 'scale', [(t, (1, 1 + 0.25 * v, 1 + 0.1 * v)) for t, v in keys])


# idle: breathing, gentle S-curve sway, whiskers and frills drifting
a = T('idle', 4.0, True)
A.sine(a, 'body', 'position', (0, 0.5, 0))
A.sine(a, 'body2', 'scale', (0.02, 0.025, 0), offset=(1, 1, 1))
for b, off in (('body2', 4), ('body3', 4), ('tail1', -5), ('tail2', -7), ('tail3', -4), ('tail4', 3), ('tail5', 6)):
    A.sine(a, b, 'rotation', (0, 3, 0), phase=CHAIN.index(b) * -0.7, offset=(0, off, 0))
A.sine(a, 'neck1', 'rotation', (2, 4, 0), phase=0.4)
A.sine(a, 'neck2', 'rotation', (-1.5, 3, 0), phase=1.0)
A.sine(a, 'head', 'rotation', (-2, 4, 1.5), phase=1.6)
A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0), period=2.0)
for w in WHISK:
    A.sine(a, w, 'rotation', (6, 5, 0), period=2.0, phase=0.5 if w.endswith('_l') else 1.4)
for f in FRILLS:
    A.sine(a, f, 'rotation', (4, 0, 0), period=2.0, phase=FRILLS.index(f) * 0.5)
A.sine(a, 'crest', 'rotation', (-3, 0, 0), period=2.0)
A.sine(a, 'tail_fin', 'rotation', (0, 6, 0), period=2.0, phase=1.2)

# walk: lateral undulation + sprawling diagonal gait
a = T('walk', 1.6, True)
lizard_gait(a, 1.6, 26, 18)
undulate(a, 1.6, 8, 7)
A.sine(a, 'body', 'position', (0, 0.6, 0), period=0.8, phase=0.3)
A.sine(a, 'neck1', 'rotation', (2, 0, 0), period=0.8, phase=1.0)

# run: fast wiggle, head held low and forward
a = T('run', 0.9, True)
lizard_gait(a, 0.9, 34, 24, duty=0.5, steps=16)
undulate(a, 0.9, 14, 11, steps=16)
A.sine(a, 'body', 'position', (0, 1.2, 0), period=0.45, phase=0.3)
A.hold(a, 'neck1', 'rotation', (18, 0, 0))
A.hold(a, 'neck2', 'rotation', (6, 0, 0))
A.hold(a, 'head', 'rotation', (-16, 0, 0))
for w in WHISK:
    A.hold(a, w, 'rotation', (16, 0, 0))

# roar (44 ticks): rear the front up like a cobra, jaw wide, frills and crest flare
a = T('roar', 2.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (4, 0, 0)), (0.6, (-18, 0, 0)), (1.7, (-17, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, 1, 0)), (0.6, (0, -3, 2)), (1.7, (0, -3, 2)), (2.2, (0, 0, 0))])
P(a, 'body2', 'rotation', [(0, (0, 0, 0)), (0.6, (10, 0, 0)), (1.7, (10, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'body3', 'rotation', [(0, (0, 0, 0)), (0.6, (6, 0, 0)), (1.7, (6, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.6, (-12, 0, 0)), (0.9, (-10, -8, 0)), (1.2, (-12, 8, 0)), (1.5, (-10, -4, 0)), (1.7, (-10, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (0.6, (-8, 0, 0)), (1.7, (-8, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (8, 0, 0)), (0.6, (-24, 0, 0)), (1.7, (-22, 0, 0)), (2.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.35, (8, 0, 0)), (0.62, (46, 0, 0)), (1.6, (42, 0, 0)), (2.0, (0, 0, 0))])
for k in ('fl', 'fr'):
    up, lo, paw, g = LEGS[k]
    P(a, up, 'rotation', [(0, (0, 0, 0)), (0.6, (-30, 0, -10 * g)), (1.7, (-30, 0, -10 * g)), (2.2, (0, 0, 0))])
    P(a, lo, 'rotation', [(0, (0, 0, 0)), (0.6, (20, 0, 0)), (1.7, (20, 0, 0)), (2.2, (0, 0, 0))])
flare(a, [(0, 0), (0.6, 1), (1.7, 1), (2.2, 0)])
A.chain_wave(a, TAIL, (4, 12, 0), period=0.6, phase_step=0.6)
for w in WHISK:
    P(a, w, 'rotation', [(0, (0, 0, 0)), (0.6, (-20, 10, 0)), (1.7, (-18, 10, 0)), (2.2, (0, 0, 0))])

# flinch (24 ticks)
a = T('flinch', 1.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.15, (-6, 8, -6)), (0.55, (2, -4, 3)), (1.2, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.15, (0, -1, 4)), (1.2, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.15, (-22, 22, 0)), (0.6, (8, -10, 0)), (1.2, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.15, (-10, 12, 8)), (0.6, (4, -4, 0)), (1.2, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.2, (34, 0, 0)), (0.8, (8, 0, 0)), (1.2, (0, 0, 0))])
A.chain_wave(a, CHAIN, (0, 10, 0), period=0.6, phase_step=0.6)
flare(a, [(0, 0), (0.2, 0.8), (1.2, 0)])

# bite (30 ticks): draw the neck back, lunge and snap at tick 12
a = T('bite', 1.5)
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.42, (0, -1, 5)), (0.62, (0, 1, -8)), (0.9, (0, 0, -5)), (1.5, (0, 0, 0))])
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.42, (-6, 0, 0)), (0.62, (5, 0, 0)), (1.5, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.42, (-16, 0, 0)), (0.6, (34, 0, 0)), (0.9, (26, 0, 0)), (1.5, (0, 0, 0))])
P(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (0.42, (-10, 0, 0)), (0.6, (12, 0, 0)), (1.5, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.42, (14, 0, 0)), (0.6, (-30, 0, 0)), (0.9, (-22, 0, 0)), (1.5, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.42, (44, 0, 0)), (0.58, (40, 0, 0)), (0.64, (0, 0, 0)), (0.75, (6, 0, 0)), (1.5, (0, 0, 0))])
flare(a, [(0, 0), (0.42, 0.8), (0.9, 0.4), (1.5, 0)])
for w in WHISK:
    P(a, w, 'rotation', [(0, (0, 0, 0)), (0.62, (24, 0, 0)), (1.0, (-8, 0, 0)), (1.5, (0, 0, 0))])

# tail whip (36 ticks): coil the tail to the right, whip it around the back to the left (ticks 13-20)
a = T('tail_whip', 1.8)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.6, (0, 12, 0)), (0.82, (0, -8, 0)), (1.05, (0, -14, 0)), (1.8, (0, 0, 0))])
for i, b in enumerate(CHAIN):
    wind = -9 - i * 2.0
    whip = 12 + i * 2.5
    P(a, b, 'rotation', [(0, (0, 0, 0)), (0.6, (0, wind, 0)), (0.7 + i * 0.03, (0, wind * 0.3, 0)), (0.9 + i * 0.03, (0, whip, 0)), (1.1 + i * 0.03, (0, whip * 0.8, 0)), (1.8, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.6, (0, -22, 0)), (0.9, (0, 14, 0)), (1.8, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (0, -14, 0)), (0.9, (0, 10, 0)), (1.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (18, 0, 0)), (1.0, (10, 0, 0)), (1.6, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.6, (0, 1, 0)), (0.9, (0, -1, 0)), (1.8, (0, 0, 0))])

# coil spin (44 ticks): curl into a coil, spin a full turn (ticks 15-27), uncoil
a = T('coil_spin', 2.2)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.7, (0, 20, 0)), (0.75, (0, 20, 0), 'l'), (1.35, (0, 20 - 380, 0), 'l'), (1.6, (0, -360, 0)), (2.2, (0, -360, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.7, (0, 2, 0)), (1.35, (0, 1, 0)), (2.2, (0, 0, 0))])
for i, b in enumerate(CHAIN):
    c = 14 + i * 3
    P(a, b, 'rotation', [(0, (0, 0, 0)), (0.7, (0, c, 0)), (1.35, (0, c * 1.1, 0)), (1.7, (0, c * 0.4, 0)), (2.2, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.7, (16, -20, 0)), (1.35, (16, -24, 0)), (2.2, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.7, (-10, -10, 0)), (1.35, (-10, -10, 0)), (2.2, (0, 0, 0))])
for k in LEGS:
    up, lo, paw, g = LEGS[k]
    P(a, up, 'rotation', [(0, (0, 0, 0)), (0.7, (0, 0, -16 * g)), (1.35, (0, 0, -16 * g)), (2.0, (0, 0, 0))])
flare(a, [(0, 0), (0.7, 0.7), (1.4, 0.7), (2.2, 0)])

# serpentine dash (56 ticks): pull back, then a wriggling charge (ticks 13-42)
a = T('dash', 2.8)
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.55, (0, 1, 6)), (0.7, (0, 0, -3)), (2.1, (0, 0, -3)), (2.8, (0, 0, 0))])
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.55, (-6, 0, 0)), (0.7, (4, 0, 0)), (2.1, (4, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.55, (-10, 0, 0)), (0.7, (28, 0, 0)), (2.1, (28, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (0.7, (8, 0, 0)), (2.1, (8, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.55, (6, 0, 0)), (0.7, (-26, 0, 0)), (2.1, (-26, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (30, 0, 0)), (2.0, (24, 0, 0)), (2.5, (0, 0, 0))])
for i, b in enumerate(CHAIN):
    A.sample(a, b, 'rotation', lambda t, i=i: (0, (14 + i) * math.sin(2 * math.pi * t / 0.4 - i * 0.8) * (A.ease_in_out((t - 0.6) / 0.15) if t < 0.75 else (1.0 if t < 2.0 else max(0.0, 1 - (t - 2.0) / 0.5))), 0) if t > 0.6 else (0, 0, 0), steps=56)
for k, (up, lo, paw, g) in LEGS.items():
    ph = 0.0 if k in ('fl', 'hr') else 0.5
    A.sample(a, up, 'rotation', lambda t, ph=ph, g=g: ((34 * _cyc(t / 0.4 + ph, 0.5)[0], 0, -g * 22 * _cyc(t / 0.4 + ph, 0.5)[1]) if 0.7 < t < 2.1 else (0, 0, 0)), steps=56)
flare(a, [(0, 0), (0.6, 0.6), (2.1, 0.6), (2.8, 0)])
for w in WHISK:
    P(a, w, 'rotation', [(0, (0, 0, 0)), (0.7, (22, 0, 0)), (2.1, (22, 0, 0)), (2.8, (0, 0, 0))])

# lightning (56 ticks): rear up and call the storm (tick 20), bolts strike around the target afterwards
a = T('lightning', 2.8)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.8, (-22, 0, 0)), (1.0, (-24, 0, 0)), (1.15, (-10, 0, 0)), (2.3, (-8, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.8, (0, -4, 3)), (1.15, (0, -2, 1)), (2.3, (0, -1, 1)), (2.8, (0, 0, 0))])
P(a, 'body2', 'rotation', [(0, (0, 0, 0)), (0.8, (12, 0, 0)), (1.15, (6, 0, 0)), (2.3, (5, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'body3', 'rotation', [(0, (0, 0, 0)), (0.8, (8, 0, 0)), (2.3, (4, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.8, (-16, 0, 0)), (1.0, (-18, 0, 0)), (1.15, (12, 0, 0)), (2.3, (8, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.8, (-34, 0, 0)), (1.0, (-36, 0, 0)), (1.15, (-6, 0, 0)), (2.3, (-6, 0, 0)), (2.8, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.8, (30, 0, 0)), (1.15, (48, 0, 0)), (2.0, (40, 0, 0)), (2.5, (0, 0, 0))])
A.sample(a, 'body2', 'position', lambda t: (0.6 * math.sin(t * 60) if 0.5 < t < 2.2 else 0, 0, 0), steps=56)
A.sample(a, 'body3', 'position', lambda t: (-0.6 * math.sin(t * 60 + 1) if 0.5 < t < 2.2 else 0, 0, 0), steps=56)
for k in ('fl', 'fr'):
    up, lo, paw, g = LEGS[k]
    P(a, up, 'rotation', [(0, (0, 0, 0)), (0.8, (-34, 0, -10 * g)), (1.15, (-6, 0, 0)), (2.8, (0, 0, 0))])
flare(a, [(0, 0), (0.8, 1), (2.3, 1), (2.8, 0)])
P(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.8, (14, 0, 0)), (2.3, (10, 0, 0)), (2.8, (0, 0, 0))])
A.chain_wave(a, TAIL[1:], (6, 6, 0), period=0.5, phase_step=0.6)

# thunder orbs (46 ticks): three homing orbs spat at ticks 14, 22, 30
a = T('thunder_orbs', 2.3)
keys_n, keys_h, keys_j = [(0, (0, 0, 0))], [(0, (0, 0, 0))], [(0, (0, 0, 0))]
for k, ts in enumerate((0.7, 1.1, 1.5)):
    side = (0, 10, -10)[k]
    keys_n += [(ts - 0.18, (-14, side, 0)), (ts, (16, side * 0.6, 0)), (ts + 0.12, (8, side * 0.5, 0))]
    keys_h += [(ts - 0.18, (8, 0, 0)), (ts, (-16, 0, 0))]
    keys_j += [(ts - 0.15, (16, 0, 0)), (ts, (46, 0, 0)), (ts + 0.15, (12, 0, 0))]
keys_n.append((2.3, (0, 0, 0)))
keys_h.append((2.3, (0, 0, 0)))
keys_j.append((2.3, (0, 0, 0)))
P(a, 'neck1', 'rotation', keys_n)
P(a, 'head', 'rotation', keys_h)
P(a, 'jaw', 'rotation', keys_j)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.5, (-8, 0, 0)), (1.7, (-6, 0, 0)), (2.3, (0, 0, 0))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.5, (0, -1, 1)), (1.7, (0, -1, 1)), (2.3, (0, 0, 0))])
flare(a, [(0, 0), (0.5, 0.9), (1.7, 0.9), (2.3, 0)])

# electric discharge (60 ticks, enraged): coil, tremble while charging, burst at tick 32
a = T('discharge', 3.0)
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.5, (0, 3, 0)), (1.5, (0, 3, 0)), (1.62, (0, -4, 0)), (2.0, (0, -2, 0)), (3.0, (0, 0, 0))])
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.5, (6, 0, 0)), (1.5, (6, 0, 0)), (1.62, (-16, 0, 0)), (2.2, (-8, 0, 0)), (3.0, (0, 0, 0))])
A.sample(a, 'body', 'scale', lambda t: (1.0 + (0.06 if 1.6 < t < 1.75 else 0.0), 1.0 + (0.08 if 1.6 < t < 1.75 else 0.0), 1.0), steps=60)
for i, b in enumerate(CHAIN):
    c = 12 + i * 2
    P(a, b, 'rotation', [(0, (0, 0, 0)), (0.5, (0, c, 0)), (1.5, (0, c, 0)), (1.65, (4, -c * 0.3, 0)), (2.2, (2, 0, 0)), (3.0, (0, 0, 0))])
A.sample(a, 'body2', 'position', lambda t: (0.8 * math.sin(t * 70) if 0.5 < t < 1.6 else 0, 0.5 * math.sin(t * 55) if 0.5 < t < 1.6 else 0, 0), steps=60)
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.5, (30, 0, 0)), (1.5, (30, 0, 0)), (1.65, (-22, 0, 0)), (2.3, (-10, 0, 0)), (3.0, (0, 0, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.5, (-6, 0, 0)), (1.5, (-6, 0, 0)), (1.65, (-30, 0, 0)), (2.3, (-16, 0, 0)), (3.0, (0, 0, 0))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.5, (10, 0, 0)), (1.5, (14, 0, 0)), (1.65, (50, 0, 0)), (2.3, (30, 0, 0)), (2.8, (0, 0, 0))])
for k in LEGS:
    up, lo, paw, g = LEGS[k]
    P(a, up, 'rotation', [(0, (0, 0, 0)), (0.5, (0, 0, -14 * g)), (1.5, (0, 0, -14 * g)), (1.65, (0, 0, 8 * g)), (2.4, (0, 0, 0))])
flare(a, [(0, 0), (0.5, 0.5), (1.5, 0.9), (1.65, 1.3), (2.4, 0.6), (3.0, 0)])
for w in WHISK:
    P(a, w, 'rotation', [(0, (0, 0, 0)), (1.5, (10, 0, 0)), (1.65, (-30, 0, 0)), (2.4, (-10, 0, 0)), (3.0, (0, 0, 0))])

# death: legs buckle, rolls onto its side, neck sprawls on the ground (holds the last frame)
a = T('death', 2.0)
P(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, 0, -4)), (1.1, (4, 0, 70)), (1.4, (4, 0, 64)), (2.0, (4, 0, 66))])
P(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -2, 0)), (1.1, (3, 9, 0)), (1.4, (3, 8, 0)), (2.0, (3, 8.5, 0))])
for i, b in enumerate(CHAIN):
    P(a, b, 'rotation', [(0, (0, 0, 0)), (1.1, (3, -2 - i, -10)), (2.0, (2, -2 - i, -12))])
P(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.1, (20, 4, -14)), (2.0, (22, 4, -16))])
P(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (1.1, (14, 6, 0)), (2.0, (16, 6, 0))])
P(a, 'head', 'rotation', [(0, (0, 0, 0)), (1.1, (-10, 0, -10)), (2.0, (-8, 0, -12))])
P(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.35, (40, 0, 0)), (1.3, (22, 0, 0)), (2.0, (18, 0, 0))])
for k in LEGS:
    up, lo, paw, g = LEGS[k]
    P(a, up, 'rotation', [(0, (0, 0, 0)), (1.1, (10, 0, 20 * g)), (2.0, (12, 0, 22 * g))])
flare(a, [(0, 0), (0.3, 0.6), (2.0, 0)])

# sleep: coiled up on the ground, head resting on the coils
a = T('sleep', 4.0, True)
A.sine(a, 'body', 'position', (0, 0.4, 0), offset=(0, 8, 0))
A.sine(a, 'body2', 'scale', (0.025, 0.03, 0), offset=(1, 1, 1))
for i, b in enumerate(CHAIN):
    A.hold(a, b, 'rotation', (0, 22 + i * 4, 0))
A.hold(a, 'body', 'rotation', (0, -20, 0))
A.hold(a, 'neck1', 'rotation', (30, 30, 0))
A.hold(a, 'neck2', 'rotation', (12, 24, 0))
A.hold(a, 'head', 'rotation', (-16, 10, 0))
for k in LEGS:
    up, lo, paw, g = LEGS[k]
    A.hold(a, up, 'rotation', (30, 0, -28 * g))
    A.hold(a, lo, 'rotation', (-10, 0, 20 * g))
for f in FRILLS:
    A.hold(a, f, 'rotation', (10, 0, 0))

# trapped: thrashing in place
a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 7, 0))
A.sine(a, 'body', 'rotation', (4, 6, 6), period=0.5)
A.chain_wave(a, CHAIN, (0, 12, 4), period=0.5, phase_step=0.7)
A.sine(a, 'neck1', 'rotation', (-12, 24, 0), period=0.5, phase=1.0, offset=(-12, 0, 0))
A.sine(a, 'head', 'rotation', (10, 0, 10), period=0.25)
A.sine(a, 'jaw', 'rotation', (16, 0, 0), period=0.25, offset=(24, 0, 0))
for k, (up, lo, paw, g) in LEGS.items():
    A.sine(a, up, 'rotation', (28, 0, 0), period=0.4, phase=(0 if k in ('fl', 'hr') else math.pi))
flare(a, [(0, 0.8), (1.0, 0.8)])

# stun: dazed, head lolling in circles
a = T('stun', 1.6, True)
A.hold(a, 'body', 'position', (0, 4, 0))
A.sine(a, 'body', 'rotation', (0, 0, 5), period=1.6)
A.sine(a, 'neck1', 'rotation', (6, 18, 0), period=1.6, phase=0.6, offset=(24, 0, 0))
A.sine(a, 'neck2', 'rotation', (0, 10, 0), period=1.6, phase=1.4, offset=(8, 0, 0))
A.sine(a, 'head', 'rotation', (0, 0, 14), period=0.8, offset=(-8, 0, 0))
A.hold(a, 'jaw', 'rotation', (22, 0, 0))
for i, b in enumerate(CHAIN):
    A.sine(a, b, 'rotation', (0, 3, 0), period=1.6, phase=-i * 0.5)
for w in WHISK:
    A.hold(a, w, 'rotation', (24, 0, 0))

if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft')
    img, glow = m.export(root)
    print('texture', m.tex_w, m.tex_h, 'cubes', sum(1 for _ in m.all_cubes()), 'bones', len(m.bones), 'anims', len(m.animations))
    for b in ('fleg_l_paw', 'hleg_l_paw'):
        print('lowest', b, round(lowest(b), 2))
    if '--preview' in sys.argv:
        from hcmodel.render import preview
        sel = [x for x in sys.argv[1:] if not x.startswith('--')]
        preview(m, img, os.path.join(os.path.dirname(__file__), '..', 'preview', 'voltrix.png'), anims=sel or None, size=260)
