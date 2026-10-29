#!/usr/bin/env python3
"""HuntCraft armor art generator.

For each of the 8 armor series builds two custom humanoid armor models (outer = helm/mail/greaves,
inner = coil) with monster-themed decorations (horns, crests, fur, crystals, spikes, wings, capes),
paints their 128x64 layer textures (+ _glow emissive maps), renders previews and exports the geometry
to src/main/java/com/huntcraft/gear/armor/client/ArmorGeometry.java.

    python3 tools/art/armor.py            # all sets
    python3 tools/art/armor.py ignivald   # one set (still rewrites the Java file with every set)
Previews: tools/preview/armor_<set>.png
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from armorkit import ArmorModel, FaceCanvas, P, hexc, preview, TW, TH  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
ARMOR_TEX = os.path.join(ASSETS, 'textures', 'models', 'armor')
JAVA_OUT = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'huntcraft', 'gear', 'armor', 'client', 'ArmorGeometry.java')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')

M = P.M
# ----------------------------------------------------------------------------- armor materials
M('hu_leather', ['#2b1b10', '#4b301c', '#6e4a2b', '#8f6339', '#b07d4a', '#cf9b63'], 'skin', noise=0.05, edge=0.06)
M('hu_leather_dk', ['#160d07', '#28180d', '#3c2516', '#533420', '#6b452b', '#865a38'], 'skin', edge=0.05)
M('hu_cloth', ['#0f1f2c', '#18334a', '#244b69', '#336588', '#4a82a8', '#6ea3c6'], 'cloth', edge=0.04)
M('hu_pants', ['#15171d', '#232730', '#333945', '#464e5e', '#5d6679', '#7a8498'], 'cloth', edge=0.04)
M('hu_iron', ['#25282d', '#3d434b', '#5b636d', '#7e8792', '#a6afba', '#d2d9e0'], 'metal', edge=0.0)
M('hu_feather', ['#4a0d0d', '#7a1714', '#a8261c', '#cf432f', '#e87355', '#f7ae8c'], 'membrane', edge=0.0)
M('bo_leather', ['#1a120c', '#2e2016', '#463222', '#5f4530', '#7a5a40', '#957254'], 'skin', edge=0.05)
M('bo_fur', ['#241c16', '#3b2f25', '#564537', '#725d4b', '#8e7861', '#ab957d'], 'fur', edge=0.0)
M('bo_cloth', ['#2a0c0a', '#4a1612', '#6b231b', '#8c3426', '#ab4a36', '#c66a50'], 'cloth', edge=0.04)
M('ja_leather', ['#0b0f17', '#151c29', '#202a3d', '#2d3a52', '#3d4c69', '#516382'], 'skin', edge=0.05)
M('ig_trim', ['#140806', '#261009', '#3a1a0f', '#4f2716', '#66361f', '#80472a'], 'metal', edge=0.0)
M('fr_leather', ['#141b26', '#212c3c', '#304056', '#425671', '#58708d', '#7590ad'], 'skin', edge=0.05)
M('vo_steel', ['#0a0f16', '#141c28', '#202c3d', '#2e3e54', '#41556f', '#5a7090'], 'metal', edge=0.0)
M('gr_iron', ['#1a1714', '#2c2722', '#433b33', '#5c5146', '#776a5c', '#948573'], 'metal', edge=0.0)
M('az_gold', ['#2a1a05', '#4d330c', '#7a5414', '#a87a22', '#cfa23c', '#efcd6e'], 'metal', edge=0.0)
M('az_cloth', ['#0c0712', '#1a0f26', '#2a183b', '#3b2352', '#4f306b', '#664187'], 'cloth', edge=0.04)
M('hc_shadow', ['#050404', '#0b0909', '#121010', '#1a1717', '#231f1f', '#2d2828'], 'smooth', edge=0.0)

C = {
    'dark': '#0d0a0a', 'iron_hi': '#e6ecf2', 'iron': '#8a939e', 'iron_dk': '#3a4048', 'brass': '#d6aa4a', 'brass_dk': '#7a5a1c',
    'ivory': '#efe6cf', 'ivory_dk': '#a89a7c', 'ember': '#ffb347', 'ember_hi': '#fff0a0', 'yellow': '#ffe55a',
    'ice': '#bff4ff', 'ice_dk': '#4fa9d3', 'rune': '#ff5fc8', 'rune_hi': '#ffc2ec', 'ruby': '#ff3b2a', 'ruby_hi': '#ffb0a0',
}


def cut_plane(am, cube, keep):
    """Alpha cut-out for a decoration plane/cube: keep(f, d, r, face) -> bool mask (f front, d down, r right)."""
    def op(a):
        for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
            g = P.face_grid(cube, face)
            if g is None:
                continue
            u0, v0, w, h, I, J, pts, n, f, d, r = g
            k = keep(f, d, r, face)
            reg = a.img[v0:v0 + h, u0:u0 + w]
            greg = a.glow[v0:v0 + h, u0:u0 + w]
            reg[~k] = 0
            greg[~k] = 0
    am.post.append(op)


def recolor(am, cube, fn):
    """Post-paint per-face recolor hook: fn(FaceLike) with .reg/.greg/f/d/r/I/J."""
    class FL:
        pass

    def op(a):
        for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
            g = P.face_grid(cube, face)
            if g is None:
                continue
            o = FL()
            o.u0, o.v0, o.w, o.h, o.I, o.J, _, _, o.f, o.d, o.r = g
            o.face = face
            o.reg = a.img[o.v0:o.v0 + o.h, o.u0:o.u0 + o.w]
            o.greg = a.glow[o.v0:o.v0 + o.h, o.u0:o.u0 + o.w]
            fn(o)
    am.post.append(op)


# shapes for cut_plane
def tri_up(f, d, r, face):           # triangle, apex at top centre (side faces of YZ planes)
    return np.abs(f - 0.5) * 2 <= d + 0.1


def fin_back(f, d, r, face):         # tapers toward the back: tall at front, point at back
    return d >= (1 - f) * 0.0 + f * 0.85 - 0.02


def fin_back_top(f, d, r, face):     # tall at the back (dorsal crest that rises toward the back)
    return (1 - d) <= (1 - f) * 0.15 + f * 1.0 + 0.05


def tri_down(f, d, r, face):         # XY plane: wide at top, point at bottom (cape / membrane)
    return np.abs(r - 0.5) * 2 <= (1 - d) * 1.05 + 0.08


def tattered(f, d, r, face):         # XY plane with ragged bottom edge
    lim = 0.78 + 0.22 * np.abs(np.sin(r * 17.0)) - 0.1 * (np.abs(r - 0.5) * 2)
    return d <= lim


def membrane_wing(f, d, r, face):    # XY plane wing: hangs from top edge, narrowing outward, scalloped
    x = r  # 0 = entity left .. 1 = right (for a plane extending to -X from its pivot, outer end is r=1)
    lim = 1.0 - x * 0.55 - 0.18 * np.abs(np.sin(x * math.pi * 2.0))
    return d <= lim


# ----------------------------------------------------------------------------- generic base painters
def helm(am, mat, top=None, back=None, side=None, brow=None, visor='open', visor_col=None, glow_eyes=None, trim=None):
    top = top or mat
    back = back or mat
    side = side or mat
    hf = {f: am.face('head', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    hf['bottom'].clear()
    hf['top'].fill(top, seed=1)
    hf['south'].fill(back, seed=2)
    hf['west'].fill(side, seed=3)
    hf['east'].fill(side, seed=3)
    n = hf['north']
    n.fill(mat, seed=4)
    if brow:
        n.fill(brow, n.rows(0, 2), seed=5)
        for fc in (hf['west'], hf['east'], hf['south']):
            fc.fill(brow, fc.rows(0, 1), seed=6)
    if trim:
        n.hline(2, 0, 7, trim)
    vc = visor_col or C['dark']
    if visor == 'open':
        m = n.mask(1, 3, 6, 7)
        m[7, 0] = m[7, 7] = True
        n.clear(m)
        n.darken(n.mask(0, 3, 0, 6) | n.mask(7, 3, 7, 6), 0.8)
    elif visor == 'half':
        n.clear(n.mask(1, 3, 6, 4))
        n.darken(n.mask(1, 5, 6, 7), 0.85)
        for x in (2, 5):
            n.px(x, 6, vc)
    elif visor == 'slit':
        n.hline(3, 1, 6, vc)
        n.hline(4, 2, 5, vc)
        if glow_eyes:
            n.px(2, 3, glow_eyes, True)
            n.px(5, 3, glow_eyes, True)
        for x in (2, 3, 4, 5):
            if x % 2 == 0:
                n.px(x, 6, vc)
    elif visor == 'mask':
        for (x0, x1) in ((1, 2), (5, 6)):
            n.color(vc, n.mask(x0, 3, x1, 4))
        if glow_eyes:
            n.px(2, 4, glow_eyes, True)
            n.px(5, 4, glow_eyes, True)
        n.px(3, 5, vc)
        n.px(4, 5, vc)
        for x in range(1, 7):
            n.px(x, 7 if x % 2 else 6, vc)
    return hf


def chest(am, mat, side=None, back=None, top=None, arm=None, arm_upper=None, bracer=None):
    side = side or mat
    back = back or mat
    top = top or mat
    bf = {f: am.face('body', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    bf['bottom'].clear()
    bf['top'].fill(top, seed=11)
    bf['north'].fill(mat, seed=12)
    bf['south'].fill(back, seed=13)
    bf['west'].fill(side, seed=14)
    bf['east'].fill(side, seed=14)
    af = {f: am.face('arm', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    af['bottom'].clear()
    arm = arm or mat
    af['top'].fill(arm, seed=15)
    for k, fc in af.items():
        if k in ('top', 'bottom'):
            continue
        fc.fill(arm, fc.rows(0, 3), seed=16)
        fc.fill(arm_upper or side, fc.rows(4, 6), seed=17)
        fc.fill(bracer or arm, fc.rows(7, 11), seed=18)
        fc.darken(fc.rows(3, 3), 0.65)
        fc.darken(fc.rows(6, 6), 0.75)
    return bf, af


def boots(am, mat, cuff=None, sole='hc_shadow', top_row=5, front=None):
    lf = {f: am.face('leg', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    lf['top'].clear()
    lf['bottom'].fill(sole, seed=21)
    for k in ('west', 'north', 'east', 'south'):
        fc = lf[k]
        fc.clear(fc.rows(0, top_row - 1))
        fc.fill(mat, fc.rows(top_row, 11), seed=22)
        if front and k == 'north':
            fc.fill(front, fc.rows(top_row + 1, 10), seed=23)
        if cuff:
            fc.fill(cuff, fc.rows(top_row, top_row + 1), seed=24)
        fc.fill(sole, fc.rows(11, 11), seed=25)
        fc.darken(fc.rows(10, 10), 0.8)
    return lf


def coil(am, mat, belt, legs, knee=None, tasset=None, belt_rows=(8, 9)):
    bf = {f: am.face('body', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    bf['top'].clear()
    bf['bottom'].clear()
    b0, b1 = belt_rows
    for k in ('west', 'north', 'east', 'south'):
        fc = bf[k]
        fc.clear(fc.rows(0, b0 - 1))
        fc.fill(belt, fc.rows(b0, b1), seed=31)
        fc.fill(tasset or mat, fc.rows(b1 + 1, 11), seed=32)
        fc.darken(fc.rows(b1, b1), 0.75)
    lf = {f: am.face('leg', f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}
    lf['top'].clear()
    lf['bottom'].clear()
    for k in ('west', 'north', 'east', 'south'):
        fc = lf[k]
        fc.fill(legs, seed=33)
        fc.fill(tasset or mat, fc.rows(0, 1), seed=34)
        if knee and k == 'north':
            fc.fill(knee, fc.rows(5, 7), seed=35)
            fc.darken(fc.rows(7, 7), 0.75)
    return bf, lf


def rivets(fc, pts, col=None, shadow=True):
    for x, y in pts:
        fc.px(x, y, col or C['iron_hi'])
        if shadow:
            fc.px(x, y + 1, C['iron_dk'])


# ============================================================================= sets
SETS = {}


def armor_set(fn):
    SETS[fn.__name__.replace('build_', '')] = fn
    return fn


# ----------------------------------------------------------------------------- Hunter (leather starter)
@armor_set
def build_hunter(o, i):
    # cap plume (left side)
    pl = o.head.bone('plume_l', (4.4, -7.5, 1.5), (-25, 0, 18))
    feather = pl.cube((0, -7, -1.5), (0, 7, 3), 'hu_feather')
    cut_plane(o, feather, lambda f, d, r, face: np.abs(f - 0.5) * 2 <= np.minimum(1.0, d * 1.6) * 0.95 + 0.05)
    o.head.cube((-1, -10, -4.2), (2, 1.2, 8.6), 'hu_leather_dk')        # cap seam ridge
    # shoulder pads
    pad = o.rarm.bone('pad_r', (-1, -2.6, 0), (0, 0, 14))
    pad.cube((-3.6, -1, -3.1), (5, 1.6, 6.2), 'hu_leather')
    pad.cube((-3.8, 0.4, -3.3), (5, 0.8, 6.6), 'hu_leather_dk')
    o.mirror(o.larm, pad)
    # boot cuffs
    cuff = o.rleg.bone('cuff_r', (0, 0, 0))
    cuff.cube((-3.25, 5.0, -3.25), (6.5, 1.6, 6.5), 'hu_leather_dk')
    o.mirror(o.lleg, cuff)
    # coil: pouch + knife
    i.body.cube((-3.8, 8.6, -3.7), (2.6, 2.6, 1.2), 'hu_leather_dk')
    i.body.cube((4.4, 8.0, -1.0), (1.0, 4.5, 2.0), 'hu_leather_dk')

    def paint_outer(am):
        hf = helm(am, 'hu_leather', top='hu_leather', brow='hu_leather_dk', visor='open')
        n = hf['north']
        n.fill('hu_iron', n.rows(1, 2), seed=7)
        n.hline(1, 0, 7, C['iron_hi'])
        rivets(n, [(1, 2), (6, 2)], C['brass'], shadow=False)
        for fc in (hf['west'], hf['east']):
            fc.fill('hu_leather_dk', fc.mask(1, 3, 6, 7), seed=8)
            fc.border(C['dark'], 'lrb', 3, 7)
            fc.px(3, 5, C['brass'])
        t = hf['top']
        for y in range(8):
            t.px(3 if y % 2 else 4, y, '#3a2414')
        bf, af = chest(am, 'hu_leather', side='hu_leather', back='hu_leather', arm='hu_leather', arm_upper='hu_cloth', bracer='hu_leather')
        n = bf['north']
        n.fill('hu_cloth', n.mask(2, 0, 5, 3), seed=9)
        n.fill('hu_cloth', n.mask(3, 4, 4, 4), seed=9)
        for k in range(9):                                   # diagonal bandolier
            x = 7 - k if k < 8 else 0
            n.px(x, 1 + k, '#24160d')
            n.px(x, 2 + k, '#3c2516')
        n.color(C['brass'], n.mask(3, 4, 4, 5))
        n.px(3, 4, '#f3d488')
        n.fill('hu_leather_dk', n.rows(9, 10), seed=10)
        n.color(C['iron'], n.mask(3, 9, 4, 10))
        n.px(3, 9, C['iron_hi'])
        s = bf['south']
        for k in range(10):
            s.px(min(7, k), 1 + k, '#24160d')
        s.fill('hu_leather_dk', s.rows(9, 10), seed=10)
        for fc in (bf['west'], bf['east']):
            fc.fill('hu_leather_dk', fc.rows(9, 10), seed=10)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.fill('hu_iron', fc.rows(7, 7), seed=3)
            fc.hline(7, 0, 3, C['iron'])
            for y in (9, 11):
                fc.px(1, y, '#24160d')
                fc.px(2, y, '#24160d')
        lf = boots(am, 'hu_leather', cuff='hu_leather_dk', top_row=5)
        n = lf['north']
        for y in range(7, 11):
            n.px(1 if y % 2 else 2, y, '#e0c9a0')
        lf['west'].color(C['brass'], lf['west'].mask(1, 8, 2, 8))

    def paint_inner(am):
        bf, lf = coil(am, 'hu_leather', 'hu_leather_dk', 'hu_pants', knee='hu_leather', tasset='hu_leather')
        n = bf['north']
        n.color(C['brass'], n.mask(3, 8, 4, 9))
        n.px(3, 8, '#f3d488')
        for x in range(8):
            if x % 3 == 1:
                n.px(x, 11, '#3a2414')
        n.clear(n.mask(3, 10, 4, 11))
        for k in ('west', 'north', 'east', 'south'):
            fc = lf[k]
            fc.darken(fc.mask(0, 11, 3, 11), 0.8)
        lf['north'].px(1, 6, '#e0c9a0')
        lf['north'].px(2, 6, '#e0c9a0')

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Bone
@armor_set
def build_bone(o, i):
    # curved horns
    h1 = o.head.bone('horn_r', (-4.8, -6.5, -1.5), (0, -10, 28))
    h1.cube((-3, -1.1, -1.1), (3, 2.2, 2.2), 'bone')
    h2 = h1.bone('horn2_r', (-2.8, 0, 0), (0, 0, 42))
    h2.cube((-3, -0.8, -0.8), (3, 1.6, 1.6), 'bone')
    h3 = h2.bone('horn3_r', (-2.8, 0, 0), (0, 0, 40))
    h3.cube((-2, -0.5, -0.5), (2, 1, 1), 'horn_ivory')
    o.mirror(o.head, h1)
    o.head.cube((-1.5, -10.2, -5.2), (3, 1.5, 6), 'bone')          # brow crest
    o.head.cube((-4.5, -2.0, -5.6), (9, 1.4, 1.0), 'bone')        # jaw guard
    # pauldrons
    pa = o.rarm.bone('pauldron_r', (-1, -3, 0), (0, 0, 16))
    pa.cube((-4.2, -1, -3.6), (6, 2, 7.2), 'bone')
    sp = pa.bone('pspike_r', (-2.5, -1, -1), (0, 0, -12))
    sp1 = sp.cube((0, -3.5, -1.5), (0, 3.5, 3), 'bone')
    cut_plane(o, sp1, tri_up)
    sp2 = pa.bone('pspike2_r', (-2.0, -1, 2), (0, 0, -8))
    s2 = sp2.cube((0, -2.5, -1.2), (0, 2.5, 2.4), 'bone')
    cut_plane(o, s2, tri_up)
    o.mirror(o.larm, pa)
    for k in range(3):                                            # vertebrae on the back
        o.body.cube((-1, 1.5 + k * 3.2, 3.0), (2, 2, 1), 'bone')
    # boots: shin plates + toe claws
    sh = o.rleg.bone('shin_r', (0, 0, 0))
    sh.cube((-2.2, 5.2, -3.7), (4.4, 4.5, 0.8), 'bone')
    for x in (-1.7, 0.3):
        sh.cube((x, 10.6, -4.2), (1.2, 1.4, 1.4), 'horn_ivory')
    o.mirror(o.lleg, sh)
    # coil: hip plates + loin plate
    hp = i.body.bone('hip_r', (-4.6, 8.5, 0), (0, 0, 8))
    hp.cube((-1, 0, -2.4), (1, 4.2, 4.8), 'bone')
    i.mirror(i.body, hp)
    i.body.cube((-2.2, 9.0, -3.3), (4.4, 1.2, 0.8), 'bone')

    def paint_outer(am):
        hf = helm(am, 'bone', top='bone', back='bo_leather', side='bone', visor='mask', visor_col='#1a120c')
        n = hf['north']
        n.darken(n.rows(0, 0), 0.8)
        for fc in (hf['west'], hf['east']):
            fc.fill('bo_leather', fc.mask(0, 5, 7, 7), seed=4)
            fc.hline(5, 0, 7, '#2e2016')
        t = hf['top']
        t.px(2, 2, '#7d6e57')
        t.px(3, 3, '#7d6e57')
        t.px(5, 5, '#7d6e57')
        hf['south'].fill('bone', hf['south'].rows(0, 2), seed=9)
        bf, af = chest(am, 'bo_leather', side='bo_leather', back='bo_leather', top='bo_fur', arm='bone', arm_upper='bo_fur', bracer='bone')
        n = bf['north']
        for y in (2, 4, 6):                                      # ribs
            n.fill('bone', n.mask(0, y, 7, y), seed=y)
            n.px(0, y + 1, '#2e2016')
            n.px(7, y + 1, '#2e2016')
        n.fill('bone', n.mask(3, 1, 4, 8), seed=3)                # sternum
        n.fill('bo_fur', n.rows(0, 0), seed=2)
        n.fill('bo_cloth', n.rows(9, 11), seed=6)
        n.hline(9, 0, 7, '#2a0c0a')
        s = bf['south']
        s.fill('bo_fur', s.rows(0, 1), seed=2)
        s.fill('bo_cloth', s.rows(9, 11), seed=6)
        for fc in (bf['west'], bf['east']):
            fc.fill('bo_fur', fc.rows(0, 2), seed=2)
            fc.fill('bo_cloth', fc.rows(9, 11), seed=6)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.hline(8, 0, 3, '#2e2016')
            fc.hline(10, 0, 3, '#2e2016')
        lf = boots(am, 'bo_leather', cuff='bo_fur', top_row=4)
        for k in ('west', 'east', 'south'):
            fc = lf[k]
            for y in (7, 9):
                fc.hline(y, 0, 3, '#1a120c')
        lf['north'].fill('bone', lf['north'].mask(0, 6, 3, 9), seed=5)

    def paint_inner(am):
        bf, lf = coil(am, 'bo_leather', 'bo_leather', 'bo_leather', knee='bone', tasset='bo_cloth')
        n = bf['north']
        n.color(C['ivory'], n.mask(3, 8, 4, 9))
        n.px(4, 9, C['ivory_dk'])
        for k in ('west', 'north', 'east', 'south'):
            fc = lf[k]
            fc.fill('bo_fur', fc.rows(4, 5), seed=4)
            fc.hline(8, 0, 3, '#1a120c')
            fc.hline(10, 0, 3, '#1a120c')

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Jagraptor
@armor_set
def build_jagraptor(o, i):
    sn = o.head.bone('snout', (0, -8.2, -4.5), (8, 0, 0))
    snout = sn.cube((-3.5, -0.8, -3.2), (7, 2.4, 3.4), 'raptor_blue', decals=[{'type': 'teeth', 'face': 'north', 'edge': 'bottom'}])
    for z, hgt in ((-2.5, 3.0), (0.5, 4.0), (3.5, 3.0)):
        c = o.head.cube((0, -9.2 - hgt, z - 1.5), (0, hgt, 3), 'raptor_stripe')
        cut_plane(o, c, tri_up)
    pad = o.rarm.bone('pad_r', (-1, -2.8, 0), (0, 0, 18))
    pad.cube((-3.8, -1, -3.3), (5, 1.8, 6.6), 'raptor_blue')
    pad.cube((-3.6, 0.6, -3.0), (4.6, 1.2, 6.0), 'raptor_stripe')
    fan = pad.bone('fan_r', (-2.6, -1, 0), (0, 0, -28))
    for z, hgt in ((-1.6, 2.6), (0.6, 3.4), (2.6, 2.4)):
        c = fan.cube((0, -hgt, z - 1.1), (0, hgt, 2.2), 'raptor_stripe')
        cut_plane(o, c, tri_up)
    o.mirror(o.larm, pad)
    ft = o.rleg.bone('talon_r', (0, 0, 0))
    for x in (-1.9, 0.4):
        ft.cube((x, 10.4, -4.6), (1.4, 1.6, 2.0), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'horn_ivory'}])
    ft.cube((-0.6, 9.4, 2.8), (1.2, 1.2, 1.6), 'claw', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.5, 'mat': 'horn_ivory'}])
    o.mirror(o.lleg, ft)
    i.body.cube((-3.6, 9.0, -3.2), (7.2, 3.6, 0.7), 'raptor_blue')
    i.body.cube((-3.6, 9.0, 2.5), (7.2, 3.6, 0.7), 'raptor_blue')

    def stripes(fc, rows, phase=0, step=3, col='raptor_stripe'):
        m = ((fc.I + fc.J + phase) % (step * 2)) < 1
        m &= fc.rows(*rows)
        fc.fill(col, m, seed=40)

    def paint_outer(am):
        hf = helm(am, 'raptor_blue', top='raptor_blue', brow='raptor_blue', visor='open')
        for fc in (hf['west'], hf['east'], hf['south']):
            stripes(fc, (0, 7), 0, 3)
        hf['top'].fill('raptor_stripe', hf['top'].mask(3, 0, 4, 7), seed=2)
        n = hf['north']
        n.fill('raptor_belly', n.mask(0, 3, 0, 6), seed=3)
        n.fill('raptor_belly', n.mask(7, 3, 7, 6), seed=3)
        bf, af = chest(am, 'raptor_blue', side='raptor_blue', back='raptor_blue', arm='raptor_blue', arm_upper='ja_leather', bracer='raptor_blue')
        n = bf['north']
        n.fill('raptor_belly', n.mask(2, 2, 5, 11), seed=4)
        for y in (4, 6, 8, 10):
            n.hline(y, 2, 5, '#8a8267')
        n.fill('ja_leather', n.rows(0, 0), seed=5)
        stripes(bf['south'], (0, 11), 1, 3)
        stripes(bf['west'], (0, 11), 0, 3)
        stripes(bf['east'], (0, 11), 0, 3)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            stripes(fc, (7, 11), 0, 2)
            if k == 'west':
                fc.px(1, 8, C['ivory'])
                fc.px(2, 10, C['ivory'])
        lf = boots(am, 'raptor_blue', cuff='raptor_stripe', top_row=5, front='raptor_belly')
        for k in ('west', 'east', 'south'):
            stripes(lf[k], (6, 10), 0, 3)

    def paint_inner(am):
        bf, lf = coil(am, 'raptor_blue', 'ja_leather', 'raptor_blue', knee='ja_leather', tasset='raptor_blue')
        n = bf['north']
        n.color(C['ivory'], n.mask(3, 8, 4, 9))
        n.px(4, 9, C['ivory_dk'])
        for k in ('west', 'north', 'east', 'south'):
            stripes(lf[k], (2, 11), 0, 3)
        lf['east'].fill('raptor_belly', lf['east'].rows(2, 11), seed=2)

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Ignivald (fire wyvern)
@armor_set
def build_ignivald(o, i):
    h = o.head.bone('horn_r', (-4.2, -7.6, -1.5), (28, -24, 0))
    h.cube((-1.1, -1.1, 0), (2.2, 2.2, 6), 'horn_dark')
    ht = h.bone('horntip_r', (0, 0, 5.6), (24, 0, 0))
    ht.cube((-0.75, -0.75, 0), (1.5, 1.5, 4), 'horn_ivory')
    o.mirror(o.head, h)
    cr = o.head.bone('crest', (0, -9.0, -3.5), (-18, 0, 0))
    c = cr.cube((0, -4.5, -2.0), (0, 4.5, 5), 'ign_spike', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'horn_ivory'}])
    cut_plane(o, c, tri_up)
    for z, hgt in ((1.0, 3.0), (3.6, 2.4)):
        cc = o.head.cube((0, -9.0 - hgt, z - 1.3), (0, hgt, 2.6), 'ign_spike')
        cut_plane(o, cc, tri_up)
    o.head.cube((-5.4, -4.0, -5.4), (1.0, 3.6, 3.0), 'ign_plate')     # cheek guards
    o.head.cube((4.4, -4.0, -5.4), (1.0, 3.6, 3.0), 'ign_plate')
    # pauldrons with spikes
    pa = o.rarm.bone('pauldron_r', (-1, -3.2, 0), (0, 0, 18))
    pa.cube((-4.6, -1.5, -3.6), (6.2, 3, 7.2), 'ign_plate', faces={'bottom': 'ign_belly'})
    s1 = pa.bone('pspike_r', (-3.2, -1.5, -1.2), (0, 0, -20))
    c1 = s1.cube((0, -4.5, -1.6), (0, 4.5, 3.2), 'ign_spike', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.45, 'mat': 'horn_ivory'}])
    cut_plane(o, c1, tri_up)
    s2 = pa.bone('pspike2_r', (-2.4, -1.5, 2.0), (0, 0, -10))
    c2 = s2.cube((0, -3.0, -1.2), (0, 3.0, 2.4), 'ign_spike', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.45, 'mat': 'horn_ivory'}])
    cut_plane(o, c2, tri_up)
    o.mirror(o.larm, pa)
    # wing cape on the back
    w = o.body.bone('wing_r', (-1.6, 1.0, 3.2), (6, 38, -14))
    w.cube((-8, -0.6, -0.6), (8, 1.2, 1.2), 'ign_dark')
    wc = w.cube((-7, -0.6, -0.6), (1.4, 1.4, 1.4), 'claw')
    mem = w.cube((-8, 0.4, 0), (8, 10, 0), 'ign_membrane')
    cut_plane(o, mem, membrane_wing)
    o.mirror(o.body, w)
    # greaves: knee guard + toe claws
    gk = o.rleg.bone('greave_r', (0, 0, 0))
    gk.cube((-2.5, 3.8, -3.7), (5, 2.6, 1.0), 'ign_plate')
    kc = gk.bone('kneespike_r', (0, 4.2, -3.6), (-35, 0, 0))
    kcs = kc.cube((0, -3.0, -1.2), (0, 3.0, 2.4), 'ign_spike', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'horn_ivory'}])
    cut_plane(o, kcs, tri_up)
    for x in (-2.0, -0.5, 1.0):
        gk.cube((x, 10.6, -4.6), (1.0, 1.4, 1.8), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.55, 'mat': 'horn_ivory'}])
    o.mirror(o.lleg, gk)
    # coil: front tassets
    tf = i.body.bone('tasset_r', (-0.2, 8.6, -2.6), (-6, 0, 6))
    tf.cube((-3.8, 0, -0.8), (3.6, 4.4, 0.8), 'ign_plate')
    i.mirror(i.body, tf)
    i.body.cube((-4.2, 8.6, 2.4), (8.4, 4.0, 0.8), 'ign_scale')

    def paint_outer(am):
        hf = helm(am, 'ign_scale', top='ign_plate', back='ign_scale', side='ign_scale', brow='ig_trim', visor='slit',
                  visor_col='#120504', glow_eyes=C['ember'], trim=C['brass_dk'])
        n = hf['north']
        n.fill('ign_plate', n.mask(0, 4, 7, 7), seed=4)
        n.color('#2a0806', n.mask(1, 3, 6, 3))
        n.px(2, 3, C['ember'], True)
        n.px(5, 3, C['ember'], True)
        for x in (2, 5):
            n.px(x, 6, '#2a0806')
        n.vline(3, 5, 7, '#5a140b')
        bf, af = chest(am, 'ign_scale', side='ign_dark', back='ign_scale', top='ign_plate', arm='ign_plate', arm_upper='ign_dark', bracer='ign_scale')
        n = bf['north']
        n.fill('ign_belly', n.mask(2, 1, 5, 10), seed=5)
        for y in (3, 5, 7, 9):
            n.hline(y, 2, 5, '#7a4d24')
        n.fill('ig_trim', n.rows(0, 0), seed=6)
        n.color(C['ruby'], n.mask(3, 2, 4, 3), True)
        n.px(3, 2, C['ruby_hi'], True)
        n.px(1, 1, C['brass'])
        n.px(6, 1, C['brass'])
        s = bf['south']
        s.fill('ign_dark', s.mask(3, 0, 4, 11), seed=7)
        for fc in (bf['west'], bf['east']):
            fc.fill('ign_scale', fc.rows(8, 11), seed=8)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.fill('ig_trim', fc.rows(7, 7), seed=9)
            if k == 'west':
                fc.px(1, 9, C['ember'], True)
        lf = boots(am, 'ign_scale', cuff='ig_trim', top_row=5, front='ign_plate')
        for k in ('west', 'east'):
            lf[k].fill('ign_dark', lf[k].rows(9, 10), seed=3)

    def paint_inner(am):
        bf, lf = coil(am, 'ign_scale', 'ig_trim', 'ign_scale', knee='ign_plate', tasset='ign_scale')
        n = bf['north']
        n.color(C['ruby'], n.mask(3, 8, 4, 9), True)
        n.px(3, 8, C['ruby_hi'], True)
        n.px(1, 8, C['brass'])
        n.px(6, 8, C['brass'])
        lf['east'].fill('ign_belly', lf['east'].rows(2, 11), seed=2)
        lf['north'].fill('ign_dark', lf['north'].rows(0, 1), seed=3)

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Frostgarm (ice fanged beast)
@armor_set
def build_frostgarm(o, i):
    ear = o.head.bone('ear_r', (-3.2, -9.0, 0.5), (0, 0, -18))
    ear.cube((-1.2, -3.2, -1.0), (2.4, 3.4, 1.8), 'frost_fur', faces={'north': 'frost_hide'})
    o.mirror(o.head, ear)
    o.head.cube((-4.8, -9.8, -6.2), (9.6, 2.2, 2.4), 'frost_fur')     # upper-jaw brim
    for x in (-3.4, 2.4):
        o.head.cube((x, -7.8, -6.0), (1.0, 2.2, 1.0), 'horn_ivory')    # fangs
    cr = o.head.bone('crystal', (0, -9.8, -3.0), (-28, 0, 0))
    cr.cube((-0.8, -4.2, -0.8), (1.6, 4.2, 1.6), 'ice_crystal', glow=True)
    o.body.cube((-6.0, -0.6, -3.8), (12, 3.0, 7.6), 'frost_fur')     # fur mantle
    pa = o.rarm.bone('furpad_r', (-1, -2.8, 0), (0, 0, 12))
    fur = pa.cube((-4.2, -1.5, -3.6), (6, 3.6, 7.2), 'frost_fur')
    cr1 = pa.bone('icespike_r', (-3.0, -1.5, -0.5), (8, 0, 24))
    cr1.cube((-0.8, -4.0, -0.8), (1.6, 4.0, 1.6), 'ice_crystal', glow=True)
    cr2 = pa.bone('icespike2_r', (-1.6, -1.5, 2.0), (22, 0, 14))
    cr2.cube((-0.6, -2.6, -0.6), (1.2, 2.6, 1.2), 'ice_crystal', glow=True)
    o.mirror(o.larm, pa)
    bc = o.rleg.bone('furcuff_r', (0, 0, 0))
    bc.cube((-3.3, 4.4, -3.3), (6.6, 2.6, 6.6), 'frost_fur')
    for x in (-2.0, -0.5, 1.0):
        bc.cube((x, 10.8, -4.4), (1.0, 1.2, 1.6), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'horn_ivory'}])
    o.mirror(o.lleg, bc)
    sk = i.body.cube((-4.7, 8.4, -3.2), (9.4, 4.6, 0.7), 'frost_fur')
    cut_plane(i, sk, lambda f, d, r, face: d <= 0.75 + 0.25 * np.abs(np.sin(r * 21.0)))
    sk2 = i.body.cube((-4.7, 8.4, 2.5), (9.4, 4.6, 0.7), 'frost_fur')
    cut_plane(i, sk2, lambda f, d, r, face: d <= 0.75 + 0.25 * np.abs(np.sin(r * 19.0)))

    def paint_outer(am):
        hf = helm(am, 'frost_fur', top='frost_fur', back='frost_fur', side='frost_fur', brow='frost_fur', visor='open')
        n = hf['north']
        n.fill('frost_hide', n.mask(0, 3, 0, 6), seed=3)
        n.fill('frost_hide', n.mask(7, 3, 7, 6), seed=3)
        n.px(3, 1, C['ice'], True)
        n.px(4, 1, C['ice'], True)
        bf, af = chest(am, 'frost_hide', side='frost_hide', back='frost_fur', top='frost_fur', arm='frost_fur', arm_upper='fr_leather', bracer='frost_fur')
        n = bf['north']
        n.fill('frost_fur_light', n.mask(2, 3, 5, 11), seed=4)
        n.fill('frost_fur', n.rows(0, 1), seed=5)
        n.color(C['ice_dk'], n.mask(3, 4, 4, 6), True)
        n.color(C['ice'], n.mask(3, 4, 3, 5), True)
        n.fill('fr_leather', n.rows(9, 10), seed=6)
        n.color(C['ivory'], n.mask(3, 9, 4, 10))
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.fill('fr_leather', fc.rows(7, 7), seed=7)
            if k == 'west':
                fc.px(1, 9, C['ice'], True)
                fc.px(2, 10, C['ice_dk'], True)
        lf = boots(am, 'fr_leather', cuff='frost_fur', top_row=4, front='frost_hide')
        for k in ('west', 'north', 'east', 'south'):
            lf[k].fill('frost_fur', lf[k].rows(4, 6), seed=3)

    def paint_inner(am):
        bf, lf = coil(am, 'frost_fur', 'fr_leather', 'frost_hide', knee='fr_leather', tasset='frost_fur')
        n = bf['north']
        n.color(C['ice_dk'], n.mask(3, 8, 4, 9), True)
        n.px(3, 8, C['ice'], True)
        for k in ('west', 'north', 'east', 'south'):
            lf[k].fill('frost_fur', lf[k].rows(0, 2), seed=3)
        lf['north'].px(1, 6, C['ice'], True)
        lf['north'].px(2, 6, C['ice'], True)

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Voltrix (thunder leviathan)
@armor_set
def build_voltrix(o, i):
    fin = o.head.bone('fin_r', (-4.9, -6.5, -3.0), (14, -14, 0))
    fc_ = fin.cube((0, -2.6, 0), (0, 3.6, 9), 'volt_scale')
    cut_plane(o, fc_, lambda f, d, r, face: (d >= f * 0.9 - 0.05) & (d <= 1.0 - f * 0.2))
    recolor(o, fc_, lambda fl: _edge_glow(fl, '#ffe55a'))
    o.mirror(o.head, fin)
    tf = o.head.bone('topfin', (0, -9.0, -2.5), (0, 0, 0))
    tfc = tf.cube((0, -4.0, 0), (0, 4.0, 8), 'volt_scale')
    cut_plane(o, tfc, lambda f, d, r, face: (1 - d) <= (1 - f) * 0.35 + f * 1.0 + 0.04)
    recolor(o, tfc, lambda fl: _edge_glow(fl, '#ffe55a'))
    for x in (-3.6, 2.6):
        o.head.cube((x, -10.6, -5.2), (1.0, 2.0, 1.0), 'claw')
    pa = o.rarm.bone('pad_r', (-1, -2.8, 0), (0, 0, 16))
    pa.cube((-4.0, -1.2, -3.4), (5.6, 2.4, 6.8), 'volt_scale', faces={'top': 'volt_stripe'})
    sf = pa.bone('shfin_r', (-3.6, -1.2, 0), (0, 0, -16))
    sfc = sf.cube((0, -4.5, -3.0), (0, 4.5, 6), 'volt_scale')
    cut_plane(o, sfc, lambda f, d, r, face: (1 - d) <= (1 - f) * 1.0 + 0.05)
    recolor(o, sfc, lambda fl: _edge_glow(fl, '#ffe55a'))
    o.mirror(o.larm, pa)
    for k, (y, hgt) in enumerate(((1.0, 3.6), (4.6, 3.0), (8.0, 2.4))):
        dc = o.body.bone('dorsal%d' % k, (0, y, 3.0), (-50, 0, 0))
        d = dc.cube((0, -hgt, 0), (0, hgt, 3.2), 'volt_scale')
        cut_plane(o, d, lambda f, dd, r, face: (1 - dd) <= (1 - f) * 1.0 + 0.06)
        recolor(o, d, lambda fl: _edge_glow(fl, '#ffe55a'))
    af = o.rleg.bone('ankfin_r', (-2.9, 7.0, 0), (0, 0, 0))
    afc = af.cube((0, -1, 0), (0, 4, 5), 'volt_scale')
    cut_plane(o, afc, lambda f, d, r, face: (d >= f * 0.8 - 0.05))
    recolor(o, afc, lambda fl: _edge_glow(fl, '#ffe55a'))
    o.rleg.cube((-2.4, 10.2, -4.2), (4.8, 1.8, 1.4), 'volt_scale', faces={'top': 'volt_stripe'})
    o.mirror(o.lleg, af)
    o.lleg.cube((-2.4, 10.2, -4.2), (4.8, 1.8, 1.4), 'volt_scale', faces={'top': 'volt_stripe'})
    hf_ = i.body.bone('hipfin_r', (-4.6, 9.0, 0), (0, 0, 20))
    hfc = hf_.cube((0, 0, -2.0), (0, 3.6, 5), 'volt_scale')
    cut_plane(i, hfc, lambda f, d, r, face: (d <= 1.0 - f * 0.7))
    recolor(i, hfc, lambda fl: _edge_glow(fl, '#ffe55a'))
    i.mirror(i.body, hf_)

    def chevrons(fc, rows, col='#ffe55a'):
        y0, y1 = rows
        for y in range(y0, y1 + 1, 3):
            for x in range(fc.w):
                yy = y + abs(x - (fc.w - 1) / 2.0) * 0.5
                yi = int(round(yy))
                if y0 <= yi <= y1:
                    fc.px(x, yi, col, True)

    def paint_outer(am):
        hf = helm(am, 'volt_scale', top='volt_scale', back='volt_scale', side='volt_scale', brow='vo_steel', visor='slit',
                  visor_col='#05080c', glow_eyes=C['yellow'])
        n = hf['north']
        n.hline(3, 1, 6, '#05080c')
        n.px(1, 3, C['yellow'], True)
        n.px(2, 3, C['yellow'], True)
        n.px(5, 3, C['yellow'], True)
        n.px(6, 3, C['yellow'], True)
        n.fill('volt_belly', n.mask(2, 5, 5, 7), seed=3)
        for fc in (hf['west'], hf['east']):
            fc.hline(5, 0, 7, C['yellow'], True)
        hf['top'].vline(3, 0, 7, C['yellow'], True)
        bf, af = chest(am, 'volt_scale', side='vo_steel', back='volt_scale', top='volt_scale', arm='volt_scale', arm_upper='vo_steel', bracer='volt_scale')
        n = bf['north']
        n.fill('volt_belly', n.mask(2, 1, 5, 9), seed=4)
        for y in (3, 5, 7):
            n.hline(y, 2, 5, '#86836a')
        chevrons(bf['south'], (1, 10))
        n.vline(1, 1, 9, C['yellow'], True)
        n.vline(6, 1, 9, C['yellow'], True)
        n.fill('vo_steel', n.rows(10, 11), seed=5)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.hline(8, 0, 3, C['yellow'], True)
        lf = boots(am, 'volt_scale', cuff='vo_steel', top_row=5, front='volt_belly')
        for k in ('west', 'east', 'south'):
            lf[k].hline(7, 0, 3, C['yellow'], True)

    def paint_inner(am):
        bf, lf = coil(am, 'volt_scale', 'vo_steel', 'volt_scale', knee='vo_steel', tasset='volt_scale')
        n = bf['north']
        n.color(C['yellow'], n.mask(3, 8, 4, 9), True)
        for k in ('west', 'south', 'east'):
            lf[k].vline(1, 2, 10, C['yellow'], True)
        lf['north'].fill('volt_belly', lf['north'].rows(2, 4), seed=2)

    return paint_outer, paint_inner


def _edge_glow(fl, col):
    """Bright glowing rim on the outline of an (already cut) decoration face."""
    a = fl.reg[..., 3] > 0
    e = np.zeros_like(a)
    e[1:, :] |= a[1:, :] & ~a[:-1, :]
    e[:-1, :] |= a[:-1, :] & ~a[1:, :]
    e[:, 1:] |= a[:, 1:] & ~a[:, :-1]
    e[:, :-1] |= a[:, :-1] & ~a[:, 1:]
    e[0, :] |= a[0, :]
    c = hexc(col)
    fl.reg[e, :3] = c
    fl.greg[e, :3] = c
    fl.greg[e, 3] = 255


# ----------------------------------------------------------------------------- Gravadon (rock brute)
@armor_set
def build_gravadon(o, i):
    o.head.cube((-5.4, -11.2, -5.8), (10.8, 3.2, 9.6), 'grav_rock', faces={'top': 'grav_moss'})
    o.head.cube((-2.0, -12.4, -4.0), (4.0, 1.4, 5.0), 'grav_rock', faces={'top': 'grav_moss'})
    t = o.head.bone('tusk_r', (-4.0, -0.4, -4.4), (28, 0, -16))
    t.cube((-0.9, -3.8, -0.9), (1.8, 3.8, 1.8), 'horn_ivory')
    tt = t.bone('tusktip_r', (0, -3.6, 0), (-30, 0, 0))
    tt.cube((-0.6, -2.4, -0.6), (1.2, 2.4, 1.2), 'horn_ivory')
    o.mirror(o.head, t)
    pa = o.rarm.bone('boulder_r', (-1, -3.0, 0), (0, 0, 10))
    pa.cube((-5.2, -2.8, -4.0), (6.8, 5.0, 8.0), 'grav_rock', faces={'top': 'grav_moss'})
    pa.cube((-4.2, -4.6, -2.2), (4.2, 2.0, 4.4), 'grav_rock', faces={'top': 'grav_moss'})
    o.mirror(o.larm, pa)
    o.body.cube((-3.6, 0.8, -3.9), (7.2, 5.4, 1.1), 'grav_rock')
    sh = o.rleg.bone('rockshoe_r', (0, 0, 0))
    sh.cube((-3.4, 8.0, -3.9), (6.8, 4.1, 7.4), 'grav_rock', faces={'top': 'grav_moss', 'bottom': 'mud'})
    sh.cube((-2.6, 9.4, -5.0), (5.2, 2.6, 1.2), 'grav_rock')
    o.mirror(o.lleg, sh)
    hp = i.body.bone('rockhip_r', (-4.7, 8.4, 0), (0, 0, 6))
    hp.cube((-1.6, 0, -2.6), (1.6, 4.4, 5.2), 'grav_rock', faces={'top': 'grav_moss'})
    i.mirror(i.body, hp)
    i.body.cube((-2.6, 8.6, -3.4), (5.2, 3.2, 0.9), 'grav_rock')

    def moss(fc, mask, seed=0):
        n = P.vnoise(fc.I * 0.6, fc.J * 0.6, fc.I * 0 + seed, seed + 3)
        fc.fill('grav_moss', mask & (n > 0.55), seed=seed)

    def paint_outer(am):
        hf = helm(am, 'grav_rock', top='grav_rock', back='grav_rock', side='grav_rock', brow='grav_rock', visor='half', visor_col='#0e0b09')
        n = hf['north']
        n.fill('grav_hide', n.mask(1, 5, 6, 7), seed=3)
        for x in (2, 4):
            n.px(x, 6, '#0e0b09')
        for fc in hf.values():
            if fc.face != 'bottom':
                moss(fc, fc.rows(0, 2), 5)
        bf, af = chest(am, 'grav_hide', side='grav_hide', back='grav_rock', top='grav_rock', arm='grav_rock', arm_upper='grav_hide', bracer='grav_rock')
        n = bf['north']
        n.fill('grav_rock', n.mask(0, 7, 7, 11), seed=4)
        n.fill('gr_iron', n.rows(9, 9), seed=5)
        moss(bf['south'], bf['south'].rows(0, 4), 7)
        moss(bf['top'], bf['top'].all(), 8)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            moss(fc, fc.rows(8, 11), 9)
            fc.fill('gr_iron', fc.rows(7, 7), seed=3)
        lf = boots(am, 'grav_rock', cuff='grav_hide', top_row=5)
        for k in ('west', 'north', 'east', 'south'):
            moss(lf[k], lf[k].rows(5, 7), 4)

    def paint_inner(am):
        bf, lf = coil(am, 'grav_rock', 'gr_iron', 'grav_hide', knee='grav_rock', tasset='grav_rock')
        n = bf['north']
        n.color(C['ivory'], n.mask(3, 8, 4, 9))
        for k in ('west', 'north', 'east', 'south'):
            moss(lf[k], lf[k].rows(0, 3), 6)

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Azragos (elder dragon)
@armor_set
def build_azragos(o, i):
    h = o.head.bone('horn_r', (-3.8, -8.2, -1.2), (34, -26, 0))
    h.cube((-1.1, -1.1, 0), (2.2, 2.2, 5), 'azr_horn')
    h2 = h.bone('horn2_r', (0, 0, 4.6), (28, 0, 0))
    h2.cube((-0.8, -0.8, 0), (1.6, 1.6, 4), 'azr_horn')
    h3 = h2.bone('horn3_r', (0, 0, 3.6), (26, 0, 0))
    h3.cube((-0.5, -0.5, 0), (1.0, 1.0, 3), 'azr_rune', glow=True)
    o.mirror(o.head, h)
    sh = o.head.bone('shorn_r', (-2.6, -9.0, -4.0), (-18, 0, -12))
    sh.cube((-0.6, -2.8, -0.6), (1.2, 2.8, 1.2), 'azr_horn')
    o.mirror(o.head, sh)
    for z, hgt in ((0.0, 3.0), (2.6, 2.4), (4.6, 1.8)):
        c = o.head.cube((0, -9.0 - hgt, z - 1.2), (0, hgt, 2.4), 'azr_horn')
        cut_plane(o, c, tri_up)
    o.body.cube((-4.8, -1.2, -3.4), (9.6, 2.4, 6.8), 'azr_plate')          # high collar
    pa = o.rarm.bone('pauldron_r', (-1, -3.2, 0), (0, 0, 18))
    pa.cube((-4.6, -1.6, -3.7), (6.2, 3.2, 7.4), 'azr_plate', faces={'bottom': 'azr_belly'})
    for k, (z, hgt) in enumerate(((-2.0, 3.6), (0.4, 4.4), (2.8, 3.0))):
        sp = pa.bone('pspike%d_r' % k, (-3.0, -1.6, z), (0, 0, -18))
        c = sp.cube((0, -hgt, -1.1), (0, hgt, 2.2), 'azr_horn', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.35, 'mat': 'azr_rune'}])
        cut_plane(o, c, tri_up)
    o.mirror(o.larm, pa)
    cape = o.body.bone('cape', (0, 0.4, 3.2), (9, 0, 0))
    cc = cape.cube((-4.8, 0, 0), (9.6, 13.5, 0), 'azr_membrane')
    cut_plane(o, cc, tattered)
    recolor(o, cc, _rune_cape)
    gk = o.rleg.bone('greave_r', (0, 0, 0))
    gk.cube((-2.5, 3.8, -3.7), (5, 2.6, 1.0), 'azr_plate')
    ks = gk.bone('kneespike_r', (0, 4.2, -3.6), (-32, 0, 0))
    ksc = ks.cube((0, -3.2, -1.2), (0, 3.2, 2.4), 'azr_horn', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'azr_rune'}])
    cut_plane(o, ksc, tri_up)
    for x in (-2.0, -0.5, 1.0):
        gk.cube((x, 10.6, -4.7), (1.0, 1.4, 1.9), 'azr_horn', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'horn_ivory'}])
    o.mirror(o.lleg, gk)
    tf = i.body.bone('tasset_r', (-0.2, 8.6, -2.6), (-6, 0, 6))
    tf.cube((-3.8, 0, -0.8), (3.6, 4.6, 0.8), 'azr_plate')
    i.mirror(i.body, tf)
    i.body.cube((-4.2, 8.6, 2.4), (8.4, 4.4, 0.8), 'azr_plate')

    def runes(fc, mask, seed=0):
        v = np.abs(P.fbm(fc.P[..., 0] * 0.35, fc.P[..., 1] * 0.35, fc.P[..., 2] * 0.35, seed) - 0.5)
        sel = mask & (v < 0.035)
        fc.color(C['rune'], sel, True)

    def paint_outer(am):
        hf = helm(am, 'azr_scale', top='azr_plate', back='azr_scale', side='azr_scale', brow='az_gold', visor='slit',
                  visor_col='#07040c', glow_eyes=C['rune'])
        n = hf['north']
        n.fill('azr_plate', n.mask(0, 4, 7, 7), seed=3)
        n.color('#07040c', n.mask(1, 3, 6, 3))
        n.px(2, 3, C['rune'], True)
        n.px(5, 3, C['rune'], True)
        n.px(3, 5, C['rune'], True)
        n.px(4, 5, C['rune'], True)
        n.vline(3, 6, 7, '#140b1f')
        n.vline(4, 6, 7, '#140b1f')
        for fc in hf.values():
            if fc.face != 'bottom':
                runes(fc, fc.all(), 2)
        bf, af = chest(am, 'azr_scale', side='azr_plate', back='azr_scale', top='azr_plate', arm='azr_plate', arm_upper='az_cloth', bracer='azr_scale')
        n = bf['north']
        n.fill('azr_plate', n.mask(1, 1, 6, 8), seed=4)
        n.fill('az_gold', n.rows(0, 0), seed=5)
        n.color(C['rune'], n.mask(3, 3, 4, 4), True)
        n.px(3, 3, C['rune_hi'], True)
        n.vline(3, 5, 8, C['rune'], True)
        n.vline(4, 5, 8, C['rune'], True)
        n.px(2, 2, C['rune'], True)
        n.px(5, 2, C['rune'], True)
        n.fill('azr_belly', n.rows(9, 11), seed=6)
        n.hline(9, 0, 7, C['brass'])
        for fc in bf.values():
            if fc.face not in ('bottom',):
                runes(fc, fc.all(), 4)
        for k, fc in af.items():
            if k in ('top', 'bottom'):
                continue
            fc.fill('az_gold', fc.rows(7, 7), seed=3)
            runes(fc, fc.rows(8, 11), 5)
        lf = boots(am, 'azr_scale', cuff='az_gold', top_row=5, front='azr_plate')
        for k in ('west', 'north', 'east', 'south'):
            runes(lf[k], lf[k].rows(6, 10), 6)

    def paint_inner(am):
        bf, lf = coil(am, 'azr_scale', 'az_gold', 'azr_scale', knee='azr_plate', tasset='azr_plate')
        n = bf['north']
        n.color(C['rune'], n.mask(3, 8, 4, 9), True)
        n.px(3, 8, C['rune_hi'], True)
        for k in ('west', 'north', 'east', 'south'):
            runes(lf[k], lf[k].all(), 7)
        lf['east'].fill('azr_belly', lf['east'].rows(2, 9), seed=2)

    return paint_outer, paint_inner


def _rune_cape(fl):
    if fl.face not in ('north', 'south'):
        return
    a = fl.reg[..., 3] > 0
    border = a & ((fl.J == 0) | (fl.J == 1))
    c = hexc('#cfa23c')
    fl.reg[border, :3] = c
    cx = (fl.w - 1) / 2.0
    sym = a & (np.abs(fl.I - cx) < 0.6) & (fl.J >= 3) & (fl.J <= 9)
    sym |= a & (np.abs(np.abs(fl.I - cx) - (fl.J - 3) * 0.5) < 0.5) & (fl.J >= 3) & (fl.J <= 7)
    rc = hexc('#ff5fc8')
    fl.reg[sym, :3] = rc
    fl.greg[sym, :3] = rc
    fl.greg[sym, 3] = 255


# ============================================================================= main
def build(sid):
    o = ArmorModel(sid + '_outer', inner=False)
    i = ArmorModel(sid + '_inner', inner=True)
    o.post, i.post = [], []
    po, pi = SETS[sid](o, i)

    def outer_painter(am):
        po(am)
        for op in am.post:
            op(am)

    def inner_painter(am):
        pi(am)
        for op in am.post:
            op(am)

    tex_o, glow_o = o.paint(outer_painter)
    tex_i, glow_i = i.paint(inner_painter)
    return o, i, (tex_o, glow_o), (tex_i, glow_i)


def java_escape(s):
    return s.replace('\\', '\\\\').replace('"', '\\"')


def write_java(geo):
    lines = ['package com.huntcraft.gear.armor.client;', '',
             'import java.util.HashMap;', 'import java.util.Map;', '',
             '/** GENERATED by tools/art/armor.py - do not edit. Armor model geometry (hcmodel-style JSON) per set and layer. */',
             'public final class ArmorGeometry {',
             '    private static final Map<String, String> JSON = new HashMap<>();', '',
             '    private ArmorGeometry() {', '    }', '',
             '    public static String get(String key) {', '        return JSON.get(key);', '    }', '',
             '    static {']
    for key in sorted(geo):
        s = json.dumps(geo[key], separators=(',', ':'))
        # keep string constants short (javac limit 65535 bytes per constant)
        chunks = [s[k:k + 8000] for k in range(0, len(s), 8000)]
        expr = ' + '.join('"%s"' % java_escape(c) for c in chunks)
        lines.append('        JSON.put("%s", %s);' % (key, expr))
    lines += ['    }', '}', '']
    with open(JAVA_OUT, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


def round_json(o):
    if isinstance(o, float):
        return round(o, 4)
    if isinstance(o, list):
        return [round_json(v) for v in o]
    if isinstance(o, dict):
        return {k: round_json(v) for k, v in o.items()}
    return o


def main(argv):
    want = [a for a in argv if not a.startswith('-')]
    os.makedirs(ARMOR_TEX, exist_ok=True)
    geo = {}
    for sid in SETS:
        o, i, (to, go), (ti, gi) = build(sid)
        geo[sid + '_outer'] = round_json(o.json())
        geo[sid + '_inner'] = round_json(i.json())
        if want and sid not in want:
            continue
        to.save(os.path.join(ARMOR_TEX, sid + '_layer_1.png'))
        ti.save(os.path.join(ARMOR_TEX, sid + '_layer_2.png'))
        for layer, g in (('1', go), ('2', gi)):
            p = os.path.join(ARMOR_TEX, '%s_layer_%s_glow.png' % (sid, layer))
            if g is not None:
                g.save(p)
            elif os.path.exists(p):
                os.remove(p)
        if '--no-preview' not in argv:
            preview([(o, to), (i, ti)], os.path.join(PREVIEW, 'armor_%s.png' % sid))
        print('armor', sid, 'ok', 'glow' if (go is not None or gi is not None) else '')
    write_java(geo)


if __name__ == '__main__':
    main(sys.argv[1:])
