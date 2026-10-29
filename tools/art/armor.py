#!/usr/bin/env python3
"""HuntCraft armor art generator.

For each of the 8 armor series builds two custom humanoid armor models (outer = helm/mail/greaves,
inner = coil) with monster-themed decorations (horns, crests, fur, crystals, spikes, wings, capes),
paints their 128x64 layer textures (+ _glow emissive maps), renders previews and exports the geometry
to src/main/java/com/huntcraft/gear/armor/client/ArmorGeometry.java.

    python3 tools/art/armor.py            # all sets
    python3 tools/art/armor.py ignivald   # preview/texture one set (Java file always contains every set)
Previews: tools/preview/armor_<set>.png
"""
import json
import math
import os
import sys

import numpy as np

if os.environ.get('PYTHONHASHSEED') != '0':  # hash() seeds the pixel noise: re-run with a fixed seed (reproducible output)
    os.environ['PYTHONHASHSEED'] = '0'
    os.execv(sys.executable, [sys.executable] + sys.argv)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from armorkit import ArmorModel, P, hexc, preview  # noqa: E402
from platepaint import S  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
ARMOR_TEX = os.path.join(ASSETS, 'textures', 'models', 'armor')
JAVA_OUT = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'huntcraft', 'gear', 'armor', 'client', 'ArmorGeometry.java')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')

# ----------------------------------------------------------------------------- styles (outline, shadow, base, light, highlight)
S('ivory', ['#5a4c3a', '#b7a37e', '#d8c7a2', '#efe3c6', '#fffaf0'], 'horn', speckle=0)
S('claw', ['#0c0a09', '#2b2622', '#3e3833', '#57504a', '#7a726b'], 'horn', speckle=0)
S('shadow', ['#050404', '#0e0b0b', '#161212', '#1f1a1a', '#2a2424'], speckle=0, bevel=False)
# hunter
S('hu_leather', ['#24140a', '#5a3720', '#7b4f2e', '#9a6a40', '#bd8c5c'], speckle=0.07)
S('hu_leather_dk', ['#150b05', '#2e1c0e', '#43301b', '#5a4128', '#735436'], speckle=0.05)
S('hu_cloth', ['#0c1722', '#1f3a55', '#2b5275', '#3d6d94', '#5a8db5'], 'cloth', speckle=0)
S('hu_pants', ['#0f1116', '#232833', '#323947', '#434c5d', '#596476'], 'cloth', speckle=0)
S('hu_iron', ['#1b1e23', '#4a525c', '#6f7884', '#98a2ad', '#d0d8e0'], 'metal', speckle=0)
S('hu_brass', ['#3a2508', '#8a5f1e', '#b5832e', '#d9aa4c', '#f5d88a'], 'metal', speckle=0)
S('hu_feather', ['#3a0a0a', '#9a2018', '#c4321f', '#e2583c', '#f8a080'], 'smooth', speckle=0, bevel=False)
# bone
S('bone', ['#3e3529', '#9a8a6e', '#c4b594', '#ddd0b2', '#f4ecd8'], 'bone', speckle=0.04)
S('bone_dk', ['#2a231a', '#5c4c3b', '#7d6a54', '#9c8770', '#b9a68e'], 'bone', speckle=0.03)
S('bo_leather', ['#120c08', '#33251a', '#4a3626', '#614834', '#7a5c44'], speckle=0.07)
S('bo_fur', ['#1a140f', '#3e3127', '#574638', '#72604f', '#8f7b67'], 'fur', speckle=0)
S('bo_cloth', ['#1e0807', '#5a1c15', '#7a2a1f', '#9a3c2b', '#b9573f'], 'cloth', speckle=0)
# jagraptor
S('ja_blue', ['#08131f', '#1c3a5c', '#25507c', '#3369a0', '#4f8cc6'], 'scales', speckle=0.02)
S('ja_stripe', ['#2a0d05', '#8a3a14', '#c05a1e', '#de7c34', '#f6a85e'], speckle=0)
S('ja_belly', ['#3d382c', '#8a8267', '#b1aa8c', '#cfc9ad', '#ece8d4'], 'plates', speckle=0)
S('ja_leather', ['#080b12', '#1a2232', '#253046', '#33405a', '#46567a'], speckle=0.06)
# ignivald
S('ig_scale', ['#2a0806', '#7a1a0c', '#a52a12', '#cc461c', '#ee7a3c'], 'scales', speckle=0.02)
S('ig_plate', ['#2b0a07', '#8f2611', '#b63a17', '#d65a26', '#f59050'], 'plates', speckle=0.02)
S('ig_dark', ['#120504', '#3a0e09', '#541710', '#702216', '#8e3220'], 'scales', speckle=0.02)
S('ig_belly', ['#3a2410', '#a87538', '#c8954e', '#e0b46c', '#f6d89a'], 'plates', speckle=0)
S('ig_membrane', ['#1c0505', '#6a1611', '#8c221a', '#ac3628', '#cc5440'], 'membrane', speckle=0, bevel=False)
S('ig_horn', ['#0e0907', '#2a1c14', '#3d2b1f', '#56402e', '#735842'], 'horn', speckle=0)
S('ig_trim', ['#100605', '#3a1a0f', '#4f2716', '#66361f', '#80472a'], 'metal', speckle=0)
S('ember', ['#5c1600', '#e04d00', '#ff7a12', '#ffab3d', '#ffe08a'], speckle=0, bevel=False, glow=True)
# frostgarm
S('fr_fur', ['#24364f', '#6a8db3', '#8fb3d6', '#b5d3ec', '#e6f4ff'], 'fur', speckle=0)
S('fr_fur_lt', ['#4d6680', '#a9c6dc', '#cde2ef', '#e5f1f8', '#ffffff'], 'fur', speckle=0)
S('fr_hide', ['#121a26', '#33415a', '#465772', '#5e728f', '#7e93ad'], speckle=0.05)
S('fr_leather', ['#0e141d', '#28354a', '#364761', '#485c7a', '#62799a'], speckle=0.05)
S('ice', ['#174a6a', '#3c93c4', '#6cc3ea', '#a6e4fa', '#ecfdff'], 'crystal', speckle=0, glow=True)
# voltrix
S('vo_scale', ['#060d16', '#173149', '#20425f', '#2c587c', '#3f76a2'], 'scales', speckle=0.02)
S('vo_steel', ['#05080c', '#18222f', '#222f40', '#2f3f55', '#425672'], 'metal', speckle=0)
S('vo_belly', ['#2c2b20', '#77745c', '#9a9779', '#b8b597', '#d8d6bd'], 'plates', speckle=0)
S('vo_glow', ['#4a3c00', '#c4a000', '#f2d100', '#fff04a', '#ffffb0'], speckle=0, bevel=False, glow=True)
S('vo_fin', ['#05101c', '#1b3a58', '#25506f', '#33698e', '#4a8ab5'], 'membrane', speckle=0, bevel=False)
# gravadon
S('gr_rock', ['#15120f', '#4a4038', '#625549', '#7d6e5f', '#9c8c7a'], 'rock', speckle=0.0)
S('gr_rock_big', ['#15120f', '#4a4038', '#625549', '#7d6e5f', '#9c8c7a'], 'rock', speckle=0.0, scale=1.6)
S('gr_hide', ['#1e1612', '#4a382c', '#5f4a3b', '#7a604d', '#967a63'], speckle=0.06)
S('gr_moss', ['#0f1a0b', '#2c4a20', '#3d632c', '#548039', '#73a04e'], 'fur', speckle=0)
S('gr_iron', ['#121010', '#3a332c', '#4f463d', '#665b50', '#837565'], 'metal', speckle=0)
S('mud', ['#140d07', '#3a2717', '#573c24', '#755434', '#946d47'], 'rock', speckle=0)
# azragos
S('az_scale', ['#06030b', '#1f1233', '#2c1a47', '#3c255f', '#523480'], 'scales', speckle=0.02)
S('az_plate', ['#05030a', '#1d1130', '#2a1944', '#3a245c', '#50327a'], 'plates', speckle=0.02)
S('az_belly', ['#200d10', '#5e2a2d', '#7d3c3e', '#9c5252', '#bd7270'], 'plates', speckle=0)
S('az_gold', ['#21150a', '#7a5414', '#a87a22', '#cfa23c', '#efcd6e'], 'metal', speckle=0)
S('az_horn', ['#08070a', '#26212a', '#38313e', '#4f4656', '#6d6377'], 'horn', speckle=0)
S('az_rune', ['#4a0c3a', '#c21f7d', '#e9409c', '#ff78c0', '#ffc2ec'], speckle=0, bevel=False, glow=True)
S('az_cloth', ['#07040b', '#1f1230', '#2c1a45', '#3c245c', '#523178'], 'cloth', speckle=0)
S('az_membrane', ['#06030a', '#26153a', '#351f4e', '#472a64', '#5e3880'], 'membrane', speckle=0, bevel=False)

C = {
    'dark': '#0d0a0a', 'iron_hi': '#e6ecf2', 'iron': '#8a939e', 'iron_dk': '#3a4048', 'brass': '#d6aa4a', 'brass_hi': '#f6dc8a',
    'brass_dk': '#7a5a1c', 'ivory': '#efe6cf', 'ivory_dk': '#a89a7c', 'ember': '#ffb347', 'ember_hi': '#fff0a0',
    'yellow': '#ffe55a', 'yellow_hi': '#ffffc0', 'ice': '#bff4ff', 'ice_dk': '#4fa9d3', 'rune': '#ff4fc0', 'rune_hi': '#ffc2ec',
    'ruby': '#ff3b2a', 'ruby_hi': '#ffc0b0', 'ruby_dk': '#8a0e0a',
}


# ----------------------------------------------------------------------------- geometry helpers
def chain(parent, name, pivot, rot, segs):
    """Tapered chain extending 'up' (-Y) from the pivot. segs: [(width, length, (bend rx, ry, rz), style, decals)].
    Each next segment starts at the previous tip and is rotated by its bend. Returns bones."""
    bones = []
    p = parent
    piv = pivot
    for k, seg in enumerate(segs):
        w, ln, bend, style = seg[:4]
        decals = seg[4] if len(seg) > 4 else None
        b = p.bone('%s%d' % (name, k) if k else name, piv, rot if k == 0 else bend)
        b.cube((-w / 2, -ln, -w / 2), (w, ln, w), style, decals=decals or [])
        bones.append(b)
        p = b
        piv = (0, -ln + 0.3, 0)
    return bones


def cut_plane(am, cube, keep):
    """Alpha cut-out for a decoration plane: keep(f, d, r, face) -> bool mask (f front, d down, r right)."""
    def op(a):
        for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
            g = P.face_grid(cube, face)
            if g is None:
                continue
            u0, v0, w, h, I, J, pts, n, f, d, r = g
            k = keep(f, d, r, face)
            a.img[v0:v0 + h, u0:u0 + w][~k] = 0
            a.glow[v0:v0 + h, u0:u0 + w][~k] = 0
    am.post.append(op)


def recolor(am, cube, fn):
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


def edge_glow(col):
    c = hexc(col)

    def fn(fl):
        a = fl.reg[..., 3] > 0
        e = np.zeros_like(a)
        e[1:, :] |= a[1:, :] & ~a[:-1, :]
        e[:-1, :] |= a[:-1, :] & ~a[1:, :]
        e[:, 1:] |= a[:, 1:] & ~a[:, :-1]
        e[:, :-1] |= a[:, :-1] & ~a[:, 1:]
        e[0, :] |= a[0, :]
        fl.reg[e, :3] = c
        fl.greg[e, :3] = c
        fl.greg[e, 3] = 255
    return fn


def tri_up(f, d, r, face):
    return np.abs(f - 0.5) * 2 <= d + 0.12


def faces_of(am, part):
    return {f: am.face(part, f) for f in ('top', 'bottom', 'west', 'north', 'east', 'south')}


SIDES = ('west', 'north', 'east', 'south')


def stud(fc, x, y, hi=None, lo=None):
    fc.px(x, y, hi or C['brass_hi'])
    fc.px(x, y + 1, lo or C['brass_dk'])


def glyph(fc, x, y, col, kind=0):
    """3x4 rune glyph with glow."""
    pts = [[(1, 0), (1, 1), (1, 2), (1, 3), (0, 1), (2, 2)], [(0, 0), (1, 1), (2, 0), (1, 2), (1, 3)],
           [(1, 0), (0, 1), (2, 1), (1, 2), (0, 3), (2, 3)]][kind % 3]
    for dx, dy in pts:
        fc.px(x + dx, y + dy, col, True)


def gem(fc, x, y, col, hi, lo=None, ring=None):
    """2x2 glowing gem with highlight and optional ring colour."""
    m = fc.mask(x, y, x + 1, y + 1)
    if ring:
        fc.ring(m, ring)
    fc.color(col, m, True)
    fc.px(x, y, hi, True)
    if lo:
        fc.px(x + 1, y + 1, lo, True)


def open_face(n):
    m = n.mask(1, 3, 6, 7)
    m[7, 0] = m[7, 7] = True
    n.clear(m)


# ============================================================================= sets
SETS = {}


def armor_set(fn):
    SETS[fn.__name__.replace('build_', '')] = fn
    return fn


# ----------------------------------------------------------------------------- Hunter (leather starter)
@armor_set
def build_hunter(o, i):
    pl = o.head.bone('plume_l', (4.6, -8.0, 1.2), (-28, 0, 22))
    f1 = pl.cube((0, -8, -1.5), (0, 8, 3), 'hu_feather')
    f2 = pl.cube((-1.5, -8, 0), (3, 8, 0), 'hu_feather')

    def shape(f, d, r, face):
        x = f if face in ('west', 'east') else r
        return np.abs(x - 0.5) * 2 <= np.minimum(1.0, d * 1.7) * 0.9 + 0.12
    cut_plane(o, f1, shape)
    cut_plane(o, f2, shape)
    recolor(o, f1, _feather_stripes)
    recolor(o, f2, _feather_stripes)
    pad = o.rarm.bone('pad_r', (-1, -2.6, 0), (0, 0, 14))
    pad.cube((-3.6, -1, -3.1), (5, 1.6, 6.2), 'hu_leather')
    pad.cube((-3.9, 0.4, -3.4), (5.2, 1.0, 6.8), 'hu_leather_dk')
    o.mirror(o.larm, pad)
    cuff = o.rleg.bone('cuff_r', (0, 0, 0))
    cuff.cube((-3.25, 5.0, -3.25), (6.5, 1.6, 6.5), 'hu_leather_dk')
    o.mirror(o.lleg, cuff)
    i.body.cube((-3.9, 8.5, -3.8), (2.8, 2.8, 1.3), 'hu_leather_dk', faces={'north': 'hu_leather'})
    i.body.cube((4.4, 8.0, -1.0), (1.0, 4.6, 2.0), 'hu_leather_dk')
    i.body.cube((4.3, 7.6, -1.2), (1.2, 0.8, 2.4), 'hu_iron')

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('top', 'west', 'east', 'south', 'north'):
            hf[k].plate('hu_leather')
        t = hf['top']
        t.plate('hu_leather_dk', t.mask(3, 0, 4, 7))
        n = hf['north']
        n.plate('hu_iron', n.rows(1, 2))
        stud(n, 1, 1, C['iron_hi'], C['iron_dk'])
        stud(n, 6, 1, C['iron_hi'], C['iron_dk'])
        open_face(n)
        for fc in (hf['west'], hf['east']):
            fc.plate('hu_iron', fc.rows(1, 2))
            fc.plate('hu_leather_dk', fc.mask(1, 3, 6, 7))
            stud(fc, 3, 4)
        s = hf['south']
        s.plate('hu_iron', s.rows(1, 2))
        s.plate('hu_leather_dk', s.rows(5, 7))
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('hu_cloth')
        n.plate('hu_leather', n.mask(0, 0, 2, 11))
        n.plate('hu_leather', n.mask(5, 0, 7, 11))
        for k in range(10):
            x = 7 - k
            if 0 <= x <= 7:
                n.px(x, 1 + k, '#24160d')
                n.px(x, 2 + k, '#43301b')
        n.plate('hu_brass', n.mask(3, 5, 4, 6))
        n.px(3, 5, C['brass_hi'])
        n.plate('hu_leather_dk', n.rows(9, 10))
        n.plate('hu_iron', n.mask(3, 9, 4, 10))
        s = bf['south']
        s.plate('hu_leather')
        for k in range(10):
            if 0 <= k <= 7:
                s.px(k, 1 + k, '#24160d')
                s.px(k, 2 + k, '#43301b')
        s.plate('hu_leather_dk', s.rows(9, 10))
        for k in ('west', 'east'):
            bf[k].plate('hu_leather')
            bf[k].plate('hu_leather_dk', bf[k].rows(9, 10))
        bf['top'].plate('hu_leather')
        bf['top'].plate('hu_cloth', bf['top'].mask(2, 1, 5, 2))
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('hu_leather')
        for k in SIDES:
            fc = af[k]
            fc.plate('hu_leather', fc.rows(0, 3))
            fc.plate('hu_cloth', fc.rows(4, 6))
            fc.plate('hu_leather', fc.rows(7, 11))
            fc.plate('hu_iron', fc.rows(7, 7))
            fc.hline(9, 0, 3, '#2e1c0e')
            fc.hline(10, 0, 3, '#5a4128')
        for k in ('north', 'south'):
            af[k].px(1, 9, C['brass'])
            af[k].px(2, 9, C['brass_dk'])
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('hu_leather', fc.rows(5, 11))
            fc.hline(8, 0, 3, '#2e1c0e')
            fc.plate('hu_leather_dk', fc.rows(10, 11))
            fc.hline(11, 0, 3, '#150b05')
        n = lf['north']
        n.px(1, 8, C['brass_hi'])
        n.px(2, 8, C['brass_dk'])
        n.px(1, 6, '#bd8c5c')
        n.px(2, 6, '#9a6a40')

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('hu_leather_dk', fc.rows(8, 9))
            fc.plate('hu_leather', fc.rows(10, 11))
        n = bf['north']
        n.plate('hu_brass', n.mask(3, 8, 4, 9))
        n.px(3, 8, C['brass_hi'])
        n.clear(n.mask(3, 10, 4, 11))
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            fc = lf[k]
            fc.plate('hu_pants')
            fc.plate('hu_leather_dk', fc.rows(2, 2))
        lf['north'].plate('hu_leather', lf['north'].rows(5, 7))
        lf['north'].px(1, 6, '#e0c9a0')
        lf['north'].px(2, 6, '#e0c9a0')

    return paint_outer, paint_inner


def _feather_stripes(fl):
    a = fl.reg[..., 3] > 0
    band = a & ((fl.J % 3) == 2)
    fl.reg[band, :3] = hexc('#f2e8d8')
    mid = a & (np.abs((fl.f if fl.face in ('west', 'east') else fl.r) - 0.5) < 0.18)
    fl.reg[mid, :3] = hexc('#5a1410')


# ----------------------------------------------------------------------------- Bone
@armor_set
def build_bone(o, i):
    chain(o.head, 'horn_r', (-4.6, -6.0, -1.0), (0, 0, -62),
          [(2.4, 3.0, None, 'bone'), (1.8, 2.6, (0, 0, 38), 'bone'), (1.2, 2.4, (0, 0, 34), 'ivory')])
    o.mirror(o.head, o.m.bones['horn_r'])
    o.head.cube((-1.5, -10.2, -5.3), (3, 1.6, 6.5), 'bone')
    o.head.cube((-4.6, -1.9, -5.6), (9.2, 1.4, 1.0), 'bone_dk')
    pa = o.rarm.bone('pauldron_r', (-1, -3, 0), (0, 0, 16))
    pa.cube((-4.2, -1, -3.6), (6, 2, 7.2), 'bone')
    chain(pa, 'pspike_r', (-2.6, -0.8, -1.2), (0, 0, -10), [(1.4, 2.0, None, 'bone'), (0.8, 1.6, (0, 0, -10), 'ivory')])
    chain(pa, 'pspike2_r', (-1.4, -0.8, 1.6), (12, 0, -6), [(1.2, 1.6, None, 'bone'), (0.7, 1.2, (0, 0, -8), 'ivory')])
    o.mirror(o.larm, pa)
    for k in range(3):
        o.body.cube((-1, 1.5 + k * 3.2, 3.0), (2, 2, 1), 'bone')
    sh = o.rleg.bone('shin_r', (0, 0, 0))
    sh.cube((-2.2, 5.6, -3.75), (4.4, 4.0, 0.8), 'bone')
    for x in (-1.8, 0.4):
        sh.cube((x, 10.6, -4.3), (1.2, 1.4, 1.5), 'ivory')
    o.mirror(o.lleg, sh)
    hp = i.body.bone('hip_r', (-4.6, 8.5, 0), (0, 0, 8))
    hp.cube((-1, 0, -2.4), (1, 4.2, 4.8), 'bone')
    i.mirror(i.body, hp)
    i.body.cube((-2.2, 8.9, -3.3), (4.4, 1.2, 0.8), 'bone')

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('top', 'west', 'east', 'north'):
            hf[k].plate('bone')
        hf['south'].plate('bo_leather')
        hf['south'].plate('bone', hf['south'].rows(0, 2))
        n = hf['north']
        n.plate('bone_dk', n.rows(2, 2))
        sock = '#140e0a'
        for x0 in (1, 5):
            n.color(sock, n.mask(x0, 3, x0 + 1, 4))
        n.px(1, 3, '#2a1d14')
        n.px(5, 3, '#2a1d14')
        n.px(3, 5, sock)
        n.px(4, 5, sock)
        for x in range(1, 7):
            n.px(x, 6, sock if x % 2 else '#e8dcc0')
        n.plate('bone_dk', n.rows(7, 7))
        for fc in (hf['west'], hf['east']):
            fc.plate('bo_leather', fc.rows(5, 7))
            fc.hline(5, 0, 7, '#2e2016')
        t = hf['top']
        for x, y in ((2, 2), (3, 3), (5, 5), (5, 1)):
            t.px(x, y, '#7d6e57')
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('bo_leather')
        for y, x0, x1 in ((2, 0, 7), (4, 0, 7), (6, 1, 6)):
            n.plate('bone', n.mask(x0, y, x1, y))
        n.plate('bone', n.mask(3, 1, 4, 8))
        n.plate('bo_fur', n.rows(0, 0))
        n.plate('bo_cloth', n.rows(9, 11))
        n.plate('bone', n.mask(3, 9, 4, 10))
        s = bf['south']
        s.plate('bo_leather')
        s.plate('bo_fur', s.rows(0, 1))
        s.plate('bo_cloth', s.rows(9, 11))
        for k in ('west', 'east'):
            bf[k].plate('bo_leather')
            bf[k].plate('bo_fur', bf[k].rows(0, 2))
            bf[k].plate('bo_cloth', bf[k].rows(9, 11))
        bf['top'].plate('bo_fur')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('bone')
        for k in SIDES:
            fc = af[k]
            fc.plate('bone', fc.rows(0, 3))
            fc.plate('bo_fur', fc.rows(4, 6))
            fc.plate('bone', fc.rows(7, 11))
            fc.hline(8, 0, 3, '#2e2016')
            fc.hline(10, 0, 3, '#2e2016')
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 3))
            fc.plate('bo_fur', fc.rows(4, 5))
            fc.plate('bo_leather', fc.rows(6, 11))
            for y in (7, 9):
                fc.hline(y, 0, 3, '#1a120c')
            fc.plate('shadow', fc.rows(11, 11))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('bo_leather', fc.rows(8, 9))
            fc.plate('bo_cloth', fc.rows(10, 11))
        bf['north'].plate('bone', bf['north'].mask(3, 8, 4, 9))
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            fc = lf[k]
            fc.plate('bo_leather')
            fc.plate('bo_fur', fc.rows(4, 5))
            fc.hline(8, 0, 3, '#1a120c')
            fc.hline(10, 0, 3, '#1a120c')
        lf['north'].plate('bone', lf['north'].rows(5, 7))

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Jagraptor
@armor_set
def build_jagraptor(o, i):
    sn = o.head.bone('snout', (0, -8.2, -4.6), (8, 0, 0))
    snout = sn.cube((-3.6, -0.8, -3.4), (7.2, 2.4, 3.6), 'ja_blue', faces={'bottom': 'ja_belly'})
    recolor(o, snout, _snout_teeth)
    sn.cube((-0.6, -1.4, -3.0), (1.2, 0.8, 3.2), 'ja_stripe')
    for z, hgt in ((-2.6, 3.2), (0.4, 4.2), (3.4, 3.2)):
        c = o.head.cube((0, -9.2 - hgt, z - 1.5), (0, hgt, 3), 'ja_stripe')
        cut_plane(o, c, tri_up)
    pad = o.rarm.bone('pad_r', (-1, -2.8, 0), (0, 0, 18))
    pad.cube((-3.8, -1, -3.3), (5, 1.8, 6.6), 'ja_blue')
    pad.cube((-3.6, 0.6, -3.0), (4.6, 1.2, 6.0), 'ja_stripe')
    fan = pad.bone('fan_r', (-2.6, -1, 0), (0, 0, -28))
    for z, hgt in ((-1.8, 2.8), (0.6, 3.6), (2.8, 2.6)):
        c = fan.cube((0, -hgt, z - 1.1), (0, hgt, 2.2), 'ja_stripe')
        cut_plane(o, c, tri_up)
    o.mirror(o.larm, pad)
    ft = o.rleg.bone('talon_r', (0, 0, 0))
    for x in (-1.9, 0.5):
        ft.cube((x, 10.4, -4.7), (1.4, 1.6, 2.1), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'ivory'}])
    ft.cube((-0.6, 9.2, 2.8), (1.2, 1.2, 1.8), 'claw', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.5, 'mat': 'ivory'}])
    o.mirror(o.lleg, ft)
    i.body.cube((-3.6, 9.0, -3.2), (7.2, 3.6, 0.7), 'ja_blue')
    i.body.cube((-3.6, 9.0, 2.5), (7.2, 3.6, 0.7), 'ja_blue')

    def stripes(fc, mask, phase=0):
        # raptor chevron bands: a 1px orange stripe with a dark underline every 4 rows, pointing down the middle
        cx = (fc.w - 1) / 2.0
        k = np.floor(fc.J - np.abs(fc.I - cx) * 0.6 + phase).astype(int) % 5
        fc.color('#d8702c', (k == 0) & mask)
        fc.color('#12243a', (k == 1) & mask)

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('top', 'west', 'east', 'south', 'north'):
            hf[k].plate('ja_blue')
        for k in ('west', 'east', 'south'):
            stripes(hf[k], hf[k].all())
        hf['top'].plate('ja_stripe', hf['top'].mask(3, 0, 4, 7))
        n = hf['north']
        open_face(n)
        n.plate('ja_belly', n.mask(0, 3, 0, 6))
        n.plate('ja_belly', n.mask(7, 3, 7, 6))
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('ja_blue')
        belly = n.mask(2, 2, 5, 11)
        n.ring(belly, '#0b1a2c')
        n.plate('ja_belly', belly)
        n.plate('ja_leather', n.rows(0, 1))
        n.plate('ivory', n.mask(3, 0, 4, 1))
        for k in ('south', 'west', 'east'):
            bf[k].plate('ja_blue')
            stripes(bf[k], bf[k].all(), 1)
        bf['top'].plate('ja_leather')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('ja_blue')
        for k in SIDES:
            fc = af[k]
            fc.plate('ja_blue', fc.rows(0, 3))
            fc.plate('ja_leather', fc.rows(4, 6))
            fc.plate('ja_blue', fc.rows(7, 11))
            stripes(fc, fc.rows(7, 11))
        af['west'].px(1, 8, C['ivory'])
        af['west'].px(2, 10, C['ivory'])
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('ja_blue', fc.rows(5, 11))
            fc.plate('ja_stripe', fc.rows(5, 5))
            fc.plate('shadow', fc.rows(11, 11))
        lf['north'].plate('ja_belly', lf['north'].rows(6, 10))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('ja_leather', fc.rows(8, 9))
            fc.plate('ja_blue', fc.rows(10, 11))
        bf['north'].plate('ivory', bf['north'].mask(3, 8, 4, 9))
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            fc = lf[k]
            fc.plate('ja_blue')
            stripes(fc, fc.rows(2, 11))
        lf['east'].plate('ja_belly', lf['east'].rows(2, 11))
        lf['north'].plate('ja_leather', lf['north'].rows(5, 7))

    return paint_outer, paint_inner


def _snout_teeth(fl):
    if fl.face != 'north':
        return
    a = fl.reg[..., 3] > 0
    bottom = a & (fl.J == fl.h - 1)
    teeth = bottom & ((fl.I % 2) == 0)
    fl.reg[teeth, :3] = hexc('#f4eedf')
    fl.reg[bottom & ~teeth, :3] = hexc('#140a08')


# ----------------------------------------------------------------------------- Ignivald (fire wyvern)
@armor_set
def build_ignivald(o, i):
    h = o.head.bone('horn_r', (-4.2, -7.6, -1.5), (28, -24, 0))
    h.cube((-1.2, -1.2, 0), (2.4, 2.4, 6), 'ig_horn')
    ht = h.bone('horntip_r', (0, 0, 5.6), (22, 0, 0))
    ht.cube((-0.8, -0.8, 0), (1.6, 1.6, 4.4), 'ivory')
    o.mirror(o.head, h)
    chain(o.head, 'crest', (0, -8.8, -4.2), (-14, 0, 0), [(1.4, 2.4, None, 'ig_horn'), (0.9, 2.0, (-12, 0, 0), 'ivory')])
    for z, hgt in ((0.4, 3.2), (3.2, 2.6)):
        cc = o.head.cube((0, -9.0 - hgt, z - 1.4), (0, hgt, 2.8), 'ig_dark')
        cut_plane(o, cc, tri_up)
    o.head.cube((-5.5, -4.2, -5.5), (1.2, 3.8, 3.2), 'ig_plate')
    o.head.cube((4.3, -4.2, -5.5), (1.2, 3.8, 3.2), 'ig_plate')
    pa = o.rarm.bone('pauldron_r', (-1, -3.2, 0), (0, 0, 18))
    pa.cube((-4.6, -1.5, -3.6), (6.2, 3, 7.2), 'ig_plate', faces={'bottom': 'ig_dark'})
    chain(pa, 'pspike_r', (-3.0, -1.2, -1.0), (0, 0, -24), [(1.6, 2.4, None, 'ig_horn'), (1.0, 2.2, (0, 0, -14), 'ivory')])
    chain(pa, 'pspike2_r', (-2.0, -1.2, 2.0), (16, 0, -12), [(1.2, 1.8, None, 'ig_horn'), (0.8, 1.6, (0, 0, -10), 'ivory')])
    o.mirror(o.larm, pa)
    w = o.body.bone('wing_r', (-1.6, 1.0, 3.2), (4, 40, -16))
    w.cube((-8.5, -0.7, -0.7), (8.5, 1.4, 1.4), 'ig_dark')
    w.cube((-9.4, -0.9, -0.9), (1.6, 1.8, 1.8), 'claw', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'ivory'}])
    mem = w.cube((-8.4, 0.6, 0), (8.4, 10.5, 0), 'ig_membrane')

    def wing_shape(f, d, r, face):
        x = r if face == 'north' else 1 - r
        return d <= np.clip(1.02 - x * 0.6, 0, 1) - 0.16 * np.abs(np.sin(x * math.pi * 2.2))
    cut_plane(o, mem, wing_shape)
    recolor(o, mem, _wing_bones)
    o.mirror(o.body, w)
    gk = o.rleg.bone('greave_r', (0, 0, 0))
    gk.cube((-2.5, 3.8, -3.7), (5, 2.6, 1.0), 'ig_plate')
    chain(gk, 'kneespike_r', (0, 4.6, -3.8), (-58, 0, 0), [(1.4, 2.0, None, 'ig_horn'), (0.8, 1.8, (-10, 0, 0), 'ivory')])
    for x in (-2.0, -0.5, 1.0):
        gk.cube((x, 10.6, -4.7), (1.0, 1.4, 1.9), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.55, 'mat': 'ivory'}])
    o.mirror(o.lleg, gk)
    tf = i.body.bone('tasset_r', (-0.2, 8.6, -2.6), (-6, 0, 6))
    tf.cube((-3.8, 0, -0.8), (3.6, 4.4, 0.8), 'ig_plate')
    i.mirror(i.body, tf)
    i.body.cube((-4.2, 8.6, 2.4), (8.4, 4.0, 0.8), 'ig_scale')

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        hf['top'].plate('ig_plate')
        for k in ('west', 'east', 'south'):
            hf[k].plate('ig_scale')
            hf[k].plate('ig_trim', hf[k].rows(0, 1))
        hf['south'].plate('ig_dark', hf['south'].mask(3, 2, 4, 7))
        n = hf['north']
        n.plate('ig_plate')
        n.plate('ig_trim', n.rows(0, 1))
        stud(n, 1, 0)
        stud(n, 6, 0)
        n.color('#160604', n.mask(1, 3, 6, 3))
        n.color('#160604', n.mask(2, 4, 5, 4))
        n.px(1, 3, C['ember'], True)
        n.px(2, 3, C['ember_hi'], True)
        n.px(5, 3, C['ember_hi'], True)
        n.px(6, 3, C['ember'], True)
        for x in (1, 6):
            n.vline(x, 5, 6, '#3a0e09')
        n.plate('ig_dark', n.mask(3, 4, 4, 7))
        n.px(3, 4, '#160604')
        n.px(4, 4, '#160604')
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('ig_scale')
        n.plate('ig_plate', n.mask(0, 1, 7, 4))
        belly = n.mask(2, 5, 5, 10)
        n.ring(belly, '#3a0e09')
        n.plate('ig_belly', belly)
        n.plate('ig_trim', n.rows(0, 0))
        gem(n, 3, 2, C['ruby'], C['ruby_hi'], C['ruby_dk'], ring=C['brass'])
        s = bf['south']
        s.plate('ig_scale')
        s.plate('ig_dark', s.mask(3, 0, 4, 11))
        for k in ('west', 'east'):
            bf[k].plate('ig_dark')
            bf[k].plate('ig_plate', bf[k].rows(0, 3))
        bf['top'].plate('ig_plate')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('ig_plate')
        for k in SIDES:
            fc = af[k]
            fc.plate('ig_plate', fc.rows(0, 3))
            fc.plate('ig_dark', fc.rows(4, 6))
            fc.plate('ig_scale', fc.rows(7, 11))
            fc.plate('ig_trim', fc.rows(7, 7))
        af['west'].px(1, 9, C['ember'], True)
        af['west'].px(2, 9, C['ember_hi'], True)
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('ig_scale', fc.rows(5, 11))
            fc.plate('ig_trim', fc.rows(5, 5))
            fc.plate('ig_dark', fc.rows(10, 11))
        lf['north'].plate('ig_plate', lf['north'].rows(6, 9))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('ig_trim', fc.rows(8, 9))
            fc.plate('ig_scale', fc.rows(10, 11))
        n = bf['north']
        gem(n, 3, 8, C['ruby'], C['ruby_hi'], C['ruby_dk'])
        stud(n, 1, 8)
        stud(n, 6, 8)
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            lf[k].plate('ig_scale')
            lf[k].plate('ig_dark', lf[k].rows(0, 1))
        lf['east'].plate('ig_belly', lf['east'].rows(2, 11))
        lf['north'].plate('ig_plate', lf['north'].rows(5, 7))

    return paint_outer, paint_inner


def _wing_bones(fl):
    if fl.face not in ('north', 'south'):
        return
    a = fl.reg[..., 3] > 0
    x = fl.r if fl.face == 'north' else 1 - fl.r
    bone1 = a & (np.abs(fl.J - (x * fl.h * 0.9)) < 0.6)
    bone2 = a & (np.abs(x - 0.45) < 0.5 / fl.w)
    fl.reg[bone1 | bone2, :3] = hexc('#3a0e09')


# ----------------------------------------------------------------------------- Frostgarm (ice fanged beast)
@armor_set
def build_frostgarm(o, i):
    ear = o.head.bone('ear_r', (-3.2, -9.0, 0.5), (0, 0, -18))
    ear.cube((-1.3, -3.4, -1.0), (2.6, 3.6, 1.8), 'fr_fur', faces={'north': 'fr_hide'})
    ear.cube((-0.8, -5.0, -0.6), (1.6, 1.8, 1.2), 'fr_fur')
    o.mirror(o.head, ear)
    o.head.cube((-4.9, -9.9, -6.3), (9.8, 2.3, 2.5), 'fr_fur')
    for x in (-3.6, 2.6):
        o.head.cube((x, -7.8, -6.1), (1.0, 2.4, 1.0), 'ivory')
    chain(o.head, 'crystal', (0, -9.6, -3.4), (-26, 0, 0), [(1.8, 2.6, None, 'ice'), (1.0, 2.2, (-8, 0, 0), 'ice')])
    chain(o.head, 'crystal_s_r', (-2.6, -9.4, -2.0), (-20, 0, -24), [(1.0, 1.8, None, 'ice')])
    o.mirror(o.head, o.m.bones['crystal_s_r'])
    o.body.cube((-6.0, -0.6, -3.9), (12, 3.0, 7.8), 'fr_fur', faces={'top': 'fr_fur_lt'})
    pa = o.rarm.bone('furpad_r', (-1, -2.8, 0), (0, 0, 12))
    pa.cube((-4.2, -1.5, -3.6), (6, 3.6, 7.2), 'fr_fur', faces={'top': 'fr_fur_lt'})
    chain(pa, 'icespike_r', (-2.8, -1.4, -0.6), (6, 0, -24), [(1.8, 2.6, None, 'ice'), (1.0, 2.0, (0, 0, -8), 'ice')])
    chain(pa, 'icespike2_r', (-1.6, -1.4, 2.0), (22, 0, -14), [(1.2, 2.2, None, 'ice')])
    o.mirror(o.larm, pa)
    bc = o.rleg.bone('furcuff_r', (0, 0, 0))
    bc.cube((-3.3, 4.4, -3.3), (6.6, 2.6, 6.6), 'fr_fur', faces={'top': 'fr_fur_lt'})
    for x in (-2.0, -0.5, 1.0):
        bc.cube((x, 10.8, -4.5), (1.0, 1.2, 1.7), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'ivory'}])
    o.mirror(o.lleg, bc)
    sk = i.body.cube((-4.7, 8.4, -3.2), (9.4, 4.6, 0.7), 'fr_fur')
    cut_plane(i, sk, lambda f, d, r, face: d <= 0.75 + 0.25 * np.abs(np.sin(r * 21.0)))
    sk2 = i.body.cube((-4.7, 8.4, 2.5), (9.4, 4.6, 0.7), 'fr_fur')
    cut_plane(i, sk2, lambda f, d, r, face: d <= 0.75 + 0.25 * np.abs(np.sin(r * 19.0)))

    def crystal_emblem(fc, x, y):
        pts = {(1, 0): C['ice'], (0, 1): C['ice_dk'], (1, 1): C['ice'], (2, 1): '#ecfdff', (1, 2): C['ice_dk'], (1, 3): '#2e7aa3'}
        for (dx, dy), c in pts.items():
            fc.px(x + dx, y + dy, c, True)

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('top', 'west', 'east', 'south', 'north'):
            hf[k].plate('fr_fur')
        n = hf['north']
        open_face(n)
        n.plate('fr_hide', n.mask(0, 3, 0, 6))
        n.plate('fr_hide', n.mask(7, 3, 7, 6))
        n.px(3, 1, C['ice'], True)
        n.px(4, 1, '#ecfdff', True)
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('fr_hide')
        n.plate('fr_fur_lt', n.mask(2, 3, 5, 10))
        n.plate('fr_fur', n.rows(0, 1))
        crystal_emblem(n, 2, 4)
        n.plate('fr_leather', n.rows(9, 10))
        n.plate('ivory', n.mask(3, 9, 4, 10))
        bf['south'].plate('fr_fur')
        for k in ('west', 'east'):
            bf[k].plate('fr_hide')
            bf[k].plate('fr_fur', bf[k].rows(0, 2))
        bf['top'].plate('fr_fur_lt')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('fr_fur_lt')
        for k in SIDES:
            fc = af[k]
            fc.plate('fr_fur', fc.rows(0, 3))
            fc.plate('fr_leather', fc.rows(4, 6))
            fc.plate('fr_fur', fc.rows(7, 11))
            fc.plate('fr_leather', fc.rows(7, 7))
        crystal_emblem(af['west'], 0, 8)
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 3))
            fc.plate('fr_fur', fc.rows(4, 6))
            fc.plate('fr_leather', fc.rows(7, 11))
            fc.plate('shadow', fc.rows(11, 11))
        lf['north'].plate('fr_hide', lf['north'].rows(7, 10))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('fr_leather', fc.rows(8, 9))
            fc.plate('fr_fur', fc.rows(10, 11))
        gem(bf['north'], 3, 8, C['ice_dk'], C['ice'], '#2e7aa3')
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            lf[k].plate('fr_hide')
            lf[k].plate('fr_fur', lf[k].rows(0, 2))
        lf['north'].plate('fr_leather', lf['north'].rows(5, 7))
        lf['north'].px(1, 6, C['ice'], True)
        lf['north'].px(2, 6, '#ecfdff', True)

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Voltrix (thunder leviathan)
@armor_set
def build_voltrix(o, i):
    fin = o.head.bone('fin_r', (-4.9, -6.8, -3.4), (14, -16, 0))
    fc_ = fin.cube((0, -2.8, 0), (0, 3.8, 9.5), 'vo_fin')
    cut_plane(o, fc_, lambda f, d, r, face: ((d >= (1 - f) * 0.85 - 0.05) & (d <= 1.0 - (1 - f) * 0.1)))
    recolor(o, fc_, edge_glow(C['yellow']))
    o.mirror(o.head, fin)
    tf = o.head.bone('topfin', (0, -9.0, -3.0), (0, 0, 0))
    tfc = tf.cube((0, -4.4, 0), (0, 4.4, 8.5), 'vo_fin')
    cut_plane(o, tfc, lambda f, d, r, face: (1 - d) <= f * 0.25 + (1 - f) * 1.0 + 0.04)
    recolor(o, tfc, edge_glow(C['yellow']))
    for x in (-3.7, 2.7):
        o.head.cube((x, -10.6, -5.2), (1.0, 2.0, 1.0), 'claw', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'vo_glow'}])
    pa = o.rarm.bone('pad_r', (-1, -2.8, 0), (0, 0, 16))
    pa.cube((-4.0, -1.2, -3.4), (5.6, 2.4, 6.8), 'vo_scale', faces={'top': 'vo_steel'})
    sf = pa.bone('shfin_r', (-3.6, -1.2, 0), (0, 0, -16))
    sfc = sf.cube((0, -4.8, -3.0), (0, 4.8, 6.4), 'vo_fin')
    cut_plane(o, sfc, lambda f, d, r, face: (1 - d) <= f * 1.0 + 0.05)
    recolor(o, sfc, edge_glow(C['yellow']))
    o.mirror(o.larm, pa)
    for k, (y, hgt) in enumerate(((1.0, 3.8), (4.6, 3.2), (8.0, 2.6))):
        dc = o.body.bone('dorsal%d' % k, (0, y, 3.0), (-50, 0, 0))
        d = dc.cube((0, -hgt, 0), (0, hgt, 3.4), 'vo_fin')
        cut_plane(o, d, lambda f, dd, r, face: (1 - dd) <= f * 1.0 + 0.06)
        recolor(o, d, edge_glow(C['yellow']))
    af_ = o.rleg.bone('ankfin_r', (-2.95, 6.6, 0), (0, 0, 0))
    afc = af_.cube((0, -1, -0.5), (0, 4.4, 5.2), 'vo_fin')
    cut_plane(o, afc, lambda f, d, r, face: (d >= (1 - f) * 0.8 - 0.05))
    recolor(o, afc, edge_glow(C['yellow']))
    af_.cube((0.55, 3.6, -3.8), (4.8, 1.8, 1.4), 'vo_scale', faces={'top': 'vo_steel'})
    o.mirror(o.lleg, af_)
    hf_ = i.body.bone('hipfin_r', (-4.6, 9.0, 0), (0, 0, 20))
    hfc = hf_.cube((0, 0, -2.0), (0, 3.8, 5.2), 'vo_fin')
    cut_plane(i, hfc, lambda f, d, r, face: (d <= 1.0 - (1 - f) * 0.7))
    recolor(i, hfc, edge_glow(C['yellow']))
    i.mirror(i.body, hf_)

    def chevrons(fc, y0, y1):
        for y in range(y0, y1 + 1, 3):
            for x in range(fc.w):
                yi = int(round(y + abs(x - (fc.w - 1) / 2.0) * 0.5))
                if y0 <= yi <= y1:
                    fc.px(x, yi, C['yellow'], True)

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('top', 'west', 'east', 'south'):
            hf[k].plate('vo_scale')
        n = hf['north']
        n.plate('vo_steel')
        n.plate('vo_scale', n.rows(0, 1))
        n.color('#03060a', n.mask(1, 3, 6, 3))
        n.px(1, 3, C['yellow'], True)
        n.px(2, 3, C['yellow_hi'], True)
        n.px(5, 3, C['yellow_hi'], True)
        n.px(6, 3, C['yellow'], True)
        for x in (2, 5):
            n.px(x, 6, '#03060a')
        n.px(3, 5, C['yellow'], True)
        n.px(4, 5, C['yellow'], True)
        for k in ('west', 'east'):
            hf[k].hline(5, 0, 7, C['yellow'], True)
        hf['top'].vline(3, 0, 7, C['yellow'], True)
        hf['top'].vline(4, 0, 7, C['yellow_hi'], True)
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('vo_scale')
        plate = n.mask(2, 1, 5, 8)
        n.ring(plate, '#03060a')
        n.plate('vo_belly', plate)
        n.vline(0, 1, 9, C['yellow'], True)
        n.vline(7, 1, 9, C['yellow'], True)
        n.plate('vo_steel', n.rows(10, 11))
        n.plate('vo_glow', n.mask(3, 10, 4, 10))
        s = bf['south']
        s.plate('vo_scale')
        chevrons(s, 1, 10)
        for k in ('west', 'east'):
            bf[k].plate('vo_steel')
        bf['top'].plate('vo_steel')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('vo_scale')
        for k in SIDES:
            fc = af[k]
            fc.plate('vo_scale', fc.rows(0, 3))
            fc.plate('vo_steel', fc.rows(4, 6))
            fc.plate('vo_scale', fc.rows(7, 11))
            fc.hline(8, 0, 3, C['yellow'], True)
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('vo_scale', fc.rows(5, 11))
            fc.plate('vo_steel', fc.rows(5, 5))
            fc.hline(7, 0, 3, C['yellow'], True)
            fc.plate('shadow', fc.rows(11, 11))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('vo_steel', fc.rows(8, 9))
            fc.plate('vo_scale', fc.rows(10, 11))
        gem(bf['north'], 3, 8, C['yellow'], C['yellow_hi'])
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            lf[k].plate('vo_scale')
        for k in ('west', 'south', 'east'):
            lf[k].vline(1, 2, 10, C['yellow'], True)
        lf['north'].plate('vo_steel', lf['north'].rows(5, 7))

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Gravadon (rock brute)
@armor_set
def build_gravadon(o, i):
    o.head.cube((-5.4, -11.2, -5.8), (10.8, 3.2, 9.6), 'gr_rock', faces={'top': 'gr_moss'})
    o.head.cube((-2.0, -12.4, -4.0), (4.0, 1.4, 5.0), 'gr_rock', faces={'top': 'gr_moss'})
    chain(o.head, 'tusk_r', (-4.0, -0.4, -4.4), (28, 0, -16), [(1.9, 3.6, None, 'ivory'), (1.3, 2.6, (-30, 0, 0), 'ivory')])
    o.mirror(o.head, o.m.bones['tusk_r'])
    pa = o.rarm.bone('boulder_r', (-1, -3.0, 0), (0, 0, 10))
    pa.cube((-5.2, -2.8, -4.0), (6.8, 5.0, 8.0), 'gr_rock_big', faces={'top': 'gr_moss'})
    pa.cube((-4.2, -4.6, -2.2), (4.2, 2.0, 4.4), 'gr_rock', faces={'top': 'gr_moss'})
    o.mirror(o.larm, pa)
    o.body.cube((-3.6, 0.8, -3.9), (7.2, 5.4, 1.1), 'gr_rock_big')
    sh = o.rleg.bone('rockshoe_r', (0, 0, 0))
    sh.cube((-3.4, 8.0, -3.9), (6.8, 4.1, 7.4), 'gr_rock_big', faces={'top': 'gr_moss', 'bottom': 'mud'})
    sh.cube((-2.6, 9.4, -5.0), (5.2, 2.6, 1.2), 'gr_rock')
    o.mirror(o.lleg, sh)
    hp = i.body.bone('rockhip_r', (-4.7, 8.4, 0), (0, 0, 6))
    hp.cube((-1.6, 0, -2.6), (1.6, 4.4, 5.2), 'gr_rock', faces={'top': 'gr_moss'})
    i.mirror(i.body, hp)
    i.body.cube((-2.6, 8.6, -3.4), (5.2, 3.2, 0.9), 'gr_rock')

    def moss_top(fc, rows=2, seed=0):
        for x in range(fc.w):
            depth = 1 + int(P.hash2(np.array([x]), np.array([seed]), 7)[0] * rows)
            fc.plate('gr_moss', fc.mask(x, 0, x, depth - 1))

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        for k in ('west', 'east', 'south', 'north'):
            hf[k].plate('gr_rock')
            moss_top(hf[k], 2, len(k))
        hf['top'].plate('gr_moss')
        n = hf['north']
        n.clear(n.mask(1, 3, 6, 4))
        n.plate('gr_hide', n.mask(1, 5, 6, 7))
        n.px(2, 6, '#0e0b09')
        n.px(5, 6, '#0e0b09')
        n.plate('gr_iron', n.rows(2, 2))
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('gr_hide')
        n.plate('gr_rock', n.rows(7, 11))
        n.plate('gr_iron', n.rows(9, 9))
        n.plate('ivory', n.mask(3, 9, 4, 9))
        s = bf['south']
        s.plate('gr_rock_big')
        moss_top(s, 3, 3)
        for k in ('west', 'east'):
            bf[k].plate('gr_hide')
            bf[k].plate('gr_rock', bf[k].rows(7, 11))
        bf['top'].plate('gr_moss')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('gr_moss')
        for k in SIDES:
            fc = af[k]
            fc.plate('gr_rock', fc.rows(0, 3))
            fc.plate('gr_hide', fc.rows(4, 6))
            fc.plate('gr_rock', fc.rows(7, 11))
            fc.plate('gr_iron', fc.rows(7, 7))
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('mud')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('gr_hide', fc.rows(5, 7))
            fc.plate('gr_rock', fc.rows(8, 11))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('gr_iron', fc.rows(8, 9))
            fc.plate('gr_rock', fc.rows(10, 11))
        bf['north'].plate('ivory', bf['north'].mask(3, 8, 4, 9))
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            lf[k].plate('gr_hide')
            lf[k].plate('gr_moss', lf[k].rows(0, 0))
        lf['north'].plate('gr_rock', lf['north'].rows(4, 7))

    return paint_outer, paint_inner


# ----------------------------------------------------------------------------- Azragos (elder dragon)
@armor_set
def build_azragos(o, i):
    h = o.head.bone('horn_r', (-3.8, -8.2, -1.2), (34, -26, 0))
    h.cube((-1.2, -1.2, 0), (2.4, 2.4, 5), 'az_horn')
    h2 = h.bone('horn2_r', (0, 0, 4.6), (28, 0, 0))
    h2.cube((-0.85, -0.85, 0), (1.7, 1.7, 4), 'az_horn')
    h3 = h2.bone('horn3_r', (0, 0, 3.6), (26, 0, 0))
    h3.cube((-0.5, -0.5, 0), (1.0, 1.0, 3), 'az_rune')
    o.mirror(o.head, h)
    chain(o.head, 'shorn_r', (-2.6, -9.0, -4.0), (-18, 0, -12), [(1.3, 2.2, None, 'az_horn'), (0.8, 1.6, (-10, 0, 0), 'az_rune')])
    o.mirror(o.head, o.m.bones['shorn_r'])
    for z, hgt in ((0.0, 3.0), (2.6, 2.4), (4.6, 1.8)):
        c = o.head.cube((0, -9.0 - hgt, z - 1.2), (0, hgt, 2.4), 'az_horn')
        cut_plane(o, c, tri_up)
    o.body.cube((-4.8, -1.2, -3.4), (9.6, 2.4, 6.8), 'az_plate', faces={'top': 'az_gold'})
    pa = o.rarm.bone('pauldron_r', (-1, -3.2, 0), (0, 0, 18))
    pa.cube((-4.6, -1.6, -3.7), (6.2, 3.2, 7.4), 'az_plate', faces={'bottom': 'az_belly'})
    pa.cube((-4.8, 1.0, -3.9), (6.4, 0.8, 7.8), 'az_gold')
    for k, (z, ln) in enumerate(((-2.2, 2.6), (0.4, 3.2), (2.8, 2.2))):
        chain(pa, 'pspike%d_r' % k, (-3.0, -1.4, z), (0, 0, -20), [(1.4, ln, None, 'az_horn'), (0.8, ln * 0.7, (0, 0, -8), 'az_rune')])
    o.mirror(o.larm, pa)
    cape = o.body.bone('cape', (0, 0.4, 3.2), (9, 0, 0))
    cc = cape.cube((-4.8, 0, 0), (9.6, 13.5, 0), 'az_membrane')
    cut_plane(o, cc, lambda f, d, r, face: d <= 0.80 + 0.2 * np.abs(np.sin(r * 17.0)) - 0.1 * (np.abs(r - 0.5) * 2))
    recolor(o, cc, _rune_cape)
    gk = o.rleg.bone('greave_r', (0, 0, 0))
    gk.cube((-2.5, 3.8, -3.7), (5, 2.6, 1.0), 'az_plate')
    chain(gk, 'kneespike_r', (0, 4.6, -3.8), (-58, 0, 0), [(1.4, 2.0, None, 'az_horn'), (0.8, 1.8, (-10, 0, 0), 'az_rune')])
    for x in (-2.0, -0.5, 1.0):
        gk.cube((x, 10.6, -4.8), (1.0, 1.4, 2.0), 'az_horn', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'ivory'}])
    o.mirror(o.lleg, gk)
    tf = i.body.bone('tasset_r', (-0.2, 8.6, -2.6), (-6, 0, 6))
    tf.cube((-3.8, 0, -0.8), (3.6, 4.6, 0.8), 'az_plate')
    i.mirror(i.body, tf)
    i.body.cube((-4.2, 8.6, 2.4), (8.4, 4.4, 0.8), 'az_plate')

    def paint_outer(am):
        hf = faces_of(am, 'head')
        hf['bottom'].clear()
        hf['top'].plate('az_plate')
        for k in ('west', 'east', 'south'):
            hf[k].plate('az_scale')
            hf[k].plate('az_gold', hf[k].rows(0, 0))
        glyph(hf['west'], 2, 3, C['rune'], 0)
        glyph(hf['east'], 3, 3, C['rune'], 0)
        glyph(hf['south'], 2, 3, C['rune'], 1)
        n = hf['north']
        n.plate('az_plate')
        n.plate('az_gold', n.rows(0, 1))
        gem(n, 3, 0, C['rune'], C['rune_hi'])
        n.color('#07040c', n.mask(1, 3, 6, 3))
        n.px(1, 3, C['rune'], True)
        n.px(2, 3, C['rune_hi'], True)
        n.px(5, 3, C['rune_hi'], True)
        n.px(6, 3, C['rune'], True)
        n.px(3, 5, C['rune'], True)
        n.px(4, 5, C['rune'], True)
        n.vline(2, 5, 6, '#07040c')
        n.vline(5, 5, 6, '#07040c')
        bf = faces_of(am, 'body')
        bf['bottom'].clear()
        n = bf['north']
        n.plate('az_scale')
        bp = n.mask(1, 1, 6, 8)
        n.ring(bp, '#a87a22')
        n.plate('az_plate', bp)
        gem(n, 3, 3, C['rune'], C['rune_hi'])
        n.vline(3, 5, 8, C['rune'], True)
        n.vline(4, 5, 8, C['rune'], True)
        n.px(2, 2, C['rune'], True)
        n.px(5, 2, C['rune'], True)
        n.plate('az_belly', n.rows(10, 11))
        s = bf['south']
        s.plate('az_scale')
        for y in range(1, 11, 3):
            s.px(3, y, C['rune'], True)
            s.px(4, y, C['rune'], True)
            s.px(3, y + 1, C['rune_hi'], True)
        for k in ('west', 'east'):
            bf[k].plate('az_plate')
            glyph(bf[k], 0, 5, C['rune'], 2)
        bf['top'].plate('az_gold')
        af = faces_of(am, 'arm')
        af['bottom'].clear()
        af['top'].plate('az_plate')
        for k in SIDES:
            fc = af[k]
            fc.plate('az_plate', fc.rows(0, 3))
            fc.plate('az_cloth', fc.rows(4, 6))
            fc.plate('az_scale', fc.rows(7, 11))
            fc.plate('az_gold', fc.rows(7, 7))
        glyph(af['west'], 0, 8, C['rune'], 1)
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].plate('shadow')
        for k in SIDES:
            fc = lf[k]
            fc.clear(fc.rows(0, 4))
            fc.plate('az_scale', fc.rows(5, 11))
            fc.plate('az_gold', fc.rows(5, 5))
            fc.plate('shadow', fc.rows(11, 11))
        glyph(lf['west'], 0, 7, C['rune'], 0)
        lf['north'].plate('az_plate', lf['north'].rows(6, 10))

    def paint_inner(am):
        bf = faces_of(am, 'body')
        bf['top'].clear()
        bf['bottom'].clear()
        for k in SIDES:
            fc = bf[k]
            fc.clear(fc.rows(0, 7))
            fc.plate('az_gold', fc.rows(8, 9))
            fc.plate('az_plate', fc.rows(10, 11))
        gem(bf['north'], 3, 8, C['rune'], C['rune_hi'])
        lf = faces_of(am, 'leg')
        lf['top'].clear()
        lf['bottom'].clear()
        for k in SIDES:
            lf[k].plate('az_scale')
        glyph(lf['west'], 0, 3, C['rune'], 2)
        lf['east'].plate('az_belly', lf['east'].rows(2, 9))
        lf['north'].plate('az_plate', lf['north'].rows(5, 7))

    return paint_outer, paint_inner


def _rune_cape(fl):
    if fl.face not in ('north', 'south'):
        return
    a = fl.reg[..., 3] > 0
    border = a & ((fl.J == 0) | (fl.J == 1))
    fl.reg[border, :3] = hexc('#cfa23c')
    fl.reg[a & (fl.J == 1), :3] = hexc('#7a5414')
    cx = (fl.w - 1) / 2.0
    sym = a & (np.abs(fl.I - cx) < 0.6) & (fl.J >= 3) & (fl.J <= 9)
    sym |= a & (np.abs(np.abs(fl.I - cx) - (fl.J - 3) * 0.5) < 0.5) & (fl.J >= 3) & (fl.J <= 7)
    rc = hexc('#ff4fc0')
    fl.reg[sym, :3] = rc
    fl.greg[sym, :3] = rc
    fl.greg[sym, 3] = 255


# ============================================================================= main
def build(sid):
    o = ArmorModel(sid + '_outer', inner=False)
    i = ArmorModel(sid + '_inner', inner=True)
    o.post, i.post = [], []
    po, pi = SETS[sid](o, i)

    def painter(fn):
        def run(am):
            fn(am)
            for op in am.post:
                op(am)
        return run

    tex_o, glow_o = o.paint(painter(po))
    tex_i, glow_i = i.paint(painter(pi))
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
    if '--no-java' not in argv:
        write_java(geo)


if __name__ == '__main__':
    main(sys.argv[1:])
