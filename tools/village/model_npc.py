#!/usr/bin/env python3
"""Humanoid village NPCs (receptionist / blacksmith / merchant / chief) and the training dummy.

Run from the repository root:  nice -n 5 python3 tools/village/model_npc.py [--preview]
Outputs:
  assets/huntcraft/hcmodels/npc_<role>.json + textures/entity/npc/npc_<role>.png
  assets/huntcraft/hcmodels/training_dummy.json + textures/entity/training_dummy.png
  tools/preview/npc*.png (with --preview)

Every humanoid shares bone names (root/hips/body/head/arm_l/arm_r/hand_l/hand_r/leg_l/leg_r) and the animation set
used by VillageNpc: idle, walk, wave, nod, bow, cheer, work (role specific).
Hair is a shell cube around the skull with per-face alpha masks (fringe, side locks, open face), faces are pixel art.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel import paint as P  # noqa: E402
from hcmodel.model import Cube  # noqa: E402
from hcmodel.paint import M  # noqa: E402

# The shared packer sizes UV rects with ceil(sum of sizes) but paints faces with per-dimension ceil; for fractional
# cube sizes that can overflow into neighbours. Allocate with per-dimension ceil (local override, toolkit untouched).
Cube.uv_w = property(lambda self: int(2 * (math.ceil(self.size[2]) + math.ceil(self.size[0]))))
Cube.uv_h = property(lambda self: int(math.ceil(self.size[2]) + math.ceil(self.size[1])))

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')

# ------------------------------------------------------------------ materials
M('n_skin', ['#8a5644', '#b07560', '#d49a80', '#ecbca0', '#f8d6be', '#fff0e2'], 'smooth', noise=0.015, edge=0.0)
M('n_skin_tan', ['#4e2816', '#743e24', '#9a5834', '#bc764a', '#d6966a', '#eab48a'], 'smooth', noise=0.02, edge=0.0)
M('n_skin_old', ['#6e4838', '#966854', '#ba8c72', '#d4aa90', '#e8c6ae', '#f6e0cc'], 'smooth', noise=0.02, edge=0.0)
M('n_hair_tsubaki', ['#0e0408', '#1e0a10', '#34101c', '#4e1828', '#6c2236', '#8e3046'], 'fur', noise=0.03, edge=0.05)
M('n_hair_koharu', ['#24120a', '#422412', '#62381c', '#844e28', '#a66a38', '#c48a50'], 'fur', noise=0.03, edge=0.05)
M('n_hair_grey', ['#121212', '#1e1c1b', '#2c2927', '#3c3835', '#4f4a46', '#655f5a'], 'fur', noise=0.03, edge=0.05)
M('n_hair_white', ['#8c8882', '#aeaaa3', '#cbc7c0', '#e0ddd7', '#f0eeea', '#ffffff'], 'fur', noise=0.03, edge=0.05)
M('n_beard_black', ['#1a1614', '#2a2420', '#3a322c', '#4c423a', '#60544a', '#76685c'], 'fur', noise=0.03, edge=0.04)
M('n_white', ['#7c766e', '#a6a097', '#cac5bc', '#e4e0d8', '#f4f1ec', '#ffffff'], 'cloth', noise=0.03, edge=0.06)
M('n_crimson', ['#2e0608', '#560c10', '#841a1c', '#b02a28', '#d24838', '#ec7458'], 'cloth', noise=0.03)
M('n_navy', ['#080c1a', '#101830', '#1a284c', '#263a6a', '#345088', '#4a6aa6'], 'cloth', noise=0.03)
M('n_charcoal', ['#0e0e10', '#1a1a1e', '#28282e', '#38383f', '#4a4a52', '#5e5e68'], 'cloth', noise=0.03)
M('n_brown', ['#1e140c', '#33231a', '#4a3526', '#634934', '#7c5e44', '#977558'], 'cloth', noise=0.03)
M('n_moegi', ['#1c2a0c', '#2e4414', '#46621e', '#60802c', '#7e9e40', '#a0bc5e'], 'cloth', noise=0.03)
M('n_orange', ['#3a1604', '#682808', '#9a3e0e', '#c85a18', '#e67e2c', '#f8a656'], 'cloth', noise=0.03)
M('n_purple', ['#140a1e', '#221232', '#341c4c', '#482a66', '#5e3a82', '#7a52a0'], 'cloth', noise=0.03)
M('n_grey', ['#26262a', '#3a3a40', '#505058', '#686870', '#82828a', '#9e9ea6'], 'cloth', noise=0.03)
M('n_leather', ['#1a100a', '#2e1c10', '#45291a', '#5e3a24', '#784c30', '#93623f'], 'skin', noise=0.03)
M('n_leather_dark', ['#0e0907', '#1a110c', '#281a12', '#38251a', '#4a3222', '#5e402c'], 'skin', noise=0.03)
M('n_straw', ['#4a3818', '#7a5e2a', '#a8843e', '#cfaa58', '#e8c878', '#f6e2a2'], 'fur', noise=0.04, edge=0.05)
M('n_rope', ['#3a2a16', '#5a4424', '#7c6034', '#9c7c48', '#b8985e', '#d2b47a'], 'cloth', noise=0.03)
M('n_wood', ['#22160c', '#3d2816', '#5c3d22', '#7c5530', '#9d6f40', '#bd8b55'], 'wood')
M('n_wood_dark', ['#140c06', '#24160c', '#382214', '#4c301c', '#624026', '#7a5232'], 'wood')
M('n_iron', ['#18191c', '#2c2e33', '#464a51', '#666b74', '#8c929c', '#b8bec8'], 'metal')
M('n_gold', ['#3a2600', '#6b4a00', '#a07400', '#d3a20f', '#f2c94a', '#fff0a8'], 'metal')
M('n_book', ['#1e0606', '#380c0e', '#561616', '#742220', '#92322c', '#ae4a3e'], 'skin', noise=0.02)
M('n_paper', ['#8a7c62', '#b4a688', '#d4c8aa', '#eae0c8', '#f6f0e0', '#fffcf4'], 'smooth', noise=0.02, edge=0.05)
M('n_indigo_pack', ['#0a1022', '#121c3a', '#1c2c58', '#2a4078', '#3c5898', '#5674b6'], 'cloth', noise=0.03,
  accent='n_white', accent_mode='spots', accent_freq=0.6, accent_thresh=0.78)
M('n_scarf', ['#0e2a1c', '#164430', '#206046', '#2e7e5e', '#429c78', '#62ba96'], 'cloth', noise=0.03,
  accent='n_white', accent_mode='spots', accent_freq=0.75, accent_thresh=0.8)
M('n_camellia', ['#3a0408', '#6a0a10', '#9e1418', '#cc2424', '#ea4a40', '#ff7a68'], 'smooth', noise=0.02, edge=0.08)
M('n_gourd', ['#3a1c04', '#663208', '#94500e', '#c06e1a', '#de9030', '#f2b452'], 'smooth', noise=0.03)
M('n_sack', ['#3a2c1a', '#5c4a30', '#7e6846', '#a0885e', '#bea67a', '#d8c298'], 'cloth', noise=0.05)
M('n_bead', ['#0e0806', '#1e120c', '#301e14', '#44301e', '#5a4028', '#725434'], 'smooth')

DARK = np.array([30, 18, 20])
WHITE = np.array([255, 255, 255])


def hexc(h):
    return np.array(P.hexrgb(h), float)


def find(m, name):
    for c in m.all_cubes():
        if c.name == name:
            return c
    raise KeyError(name)


# ------------------------------------------------------------------ texture post-processing helpers
def grid(a, rect, rows, pal, flip=False):
    """Paints pixel art rows (strings) into a face rect; '.' leaves the texel unchanged."""
    u, v, w, h = rect
    for y, row in enumerate(rows[:h]):
        if flip:
            row = row[::-1]
        for x, ch in enumerate(row[:w]):
            if ch == '.' or ch == ' ':
                continue
            c = pal[ch]
            a[v + y, u + x, :3] = c
            a[v + y, u + x, 3] = 255


def cut(a, rect, rows, flip=False):
    """Alpha mask for a face rect: 'X' keeps the texel, '.' makes it transparent."""
    u, v, w, h = rect
    for y in range(h):
        row = rows[y] if y < len(rows) else rows[-1]
        if flip:
            row = row[::-1]
        for x in range(w):
            ch = row[x] if x < len(row) else row[-1]
            if ch == '.':
                a[v + y, u + x, 3] = 0


def clear(a, rect):
    u, v, w, h = rect
    a[v:v + h, u:u + w, 3] = 0


def shell_masks(a, cube, north=None, side=None, south=None, top=None, bottom=None):
    """Hair shell alpha masks. side rows are written front-first (column 0 = face side)."""
    r = cube.face_rects()
    if north is not None:
        cut(a, r['north'], north)
    if side is not None:
        cut(a, r['east'], side)               # east face: column 0 = front
        cut(a, r['west'], side, flip=True)    # west face: column 0 = back
    if south is not None:
        cut(a, r['south'], south)
    if top is not None:
        cut(a, r['top'], top)
    if bottom is not None:
        cut(a, r['bottom'], bottom)


def ears(a, skull, skin_dark):
    """Ears painted on the skull's side faces (column counted from the front)."""
    r = skull.face_rects()
    for face, flip in (('east', False), ('west', True)):
        u, v, w, h = r[face]
        rows = ['.' * w] * 4 + ['...EE' + '.' * (w - 5), '...EE' + '.' * (w - 5), '....E' + '.' * (w - 5)]
        grid(a, (u, v, w, h), rows, {'E': skin_dark}, flip=flip)


# ------------------------------------------------------------------ skeleton + shared animations
def humanoid(name, leg_h=12, leg_w=4, torso=(8, 10, 5), arm=(3, 10, 3), head=9, scale=0.94, hunch=0):
    m = Model(name)
    m.config.update({'head_bones': [['head', 0.85]], 'walk_speed': 1.0, 'walk_scale': 1.6, 'render_scale': scale,
                     'action_speed': 1.0})
    b = {}
    tw, th, td = torso
    aw, al, ad = arm
    b['root'] = m.bone('root', (0, 24, 0))
    b['hips'] = b['root'].bone('hips', (0, -leg_h, 0))
    b['body'] = b['hips'].bone('body', (0, 0, 0), (hunch, 0, 0))
    b['head'] = b['body'].bone('head', (0, -th, 0), (-hunch * 0.8, 0, 0))
    b['arm_l'] = b['body'].bone('arm_l', (tw / 2, -th + 1, 0), (0, 0, -3))
    b['arm_r'] = b['body'].bone('arm_r', (-tw / 2, -th + 1, 0), (0, 0, 3))
    b['hand_l'] = b['arm_l'].bone('hand_l', (aw / 2, al - 1, 0))
    b['hand_r'] = b['arm_r'].bone('hand_r', (-aw / 2, al - 1, 0))
    b['leg_l'] = b['hips'].bone('leg_l', (leg_w / 2, 0, 0))
    b['leg_r'] = b['hips'].bone('leg_r', (-leg_w / 2, 0, 0))
    skull = b['head'].cube((-head / 2, -head, -head / 2), (head, head, head), 'skin', name='skull')
    return m, b, skull


def limbs(b, torso, arm, leg_h, leg_w, mats):
    """Default torso / arm / leg cubes. mats: torso, sleeve, hand, leg, foot."""
    tw, th, td = torso
    aw, al, ad = arm
    b['body'].cube((-tw / 2, -th, -td / 2), (tw, th, td), mats['torso'], name='torso')
    for side, ab, sx in (('l', b['arm_l'], 1), ('r', b['arm_r'], -1)):
        x0 = 0 if sx > 0 else -aw
        ab.cube((x0, -1, -ad / 2), (aw, al - 2, ad), mats['sleeve'], name='sleeve_' + side)
        ab.cube((x0, al - 3, -ad / 2), (aw, 2, ad), mats['hand'], name='hand_' + side, inflate=-0.05)
    for side, lb in (('l', b['leg_l']), ('r', b['leg_r'])):
        lb.cube((-leg_w / 2, 0, -leg_w / 2), (leg_w, leg_h - 2, leg_w), mats['leg'], name='leg_' + side)
        lb.cube((-leg_w / 2, leg_h - 2, -leg_w / 2), (leg_w, 2, leg_w), mats['foot'], name='foot_' + side, inflate=0.1)


def base_animations(m, extra_idle=None, extra_walk=None):
    a = m.anim('idle', 4.0, True)
    A.sine(a, 'body', 'scale', (0.012, 0.016, 0.012), offset=(1, 1, 1))
    A.sine(a, 'body', 'rotation', (1.0, 0, 0.6), phase=0.4)
    A.sine(a, 'head', 'rotation', (2.0, 4.0, 1.0), phase=1.1)
    A.sine(a, 'arm_l', 'rotation', (2.5, 0, -1.5), phase=0.3)
    A.sine(a, 'arm_r', 'rotation', (-2.5, 0, 1.5), phase=0.3)
    if extra_idle:
        extra_idle(a)
    a = m.anim('walk', 1.0, True)
    A.sine(a, 'leg_l', 'rotation', (30, 0, 0))
    A.sine(a, 'leg_r', 'rotation', (-30, 0, 0))
    A.sine(a, 'arm_l', 'rotation', (-24, 0, 0))
    A.sine(a, 'arm_r', 'rotation', (24, 0, 0))
    A.sine(a, 'body', 'rotation', (0, 4, 0))
    A.sample(a, 'hips', 'position', lambda t: (0, -0.7 * abs(math.sin(2 * math.pi * t)), 0))
    A.sine(a, 'head', 'rotation', (0, -3, 0))
    if extra_walk:
        extra_walk(a)
    # wave with the right hand
    a = m.anim('wave', 1.4)
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.25, (-160, -10, 18)), (1.15, (-160, -10, 18)), (1.4, (0, 0, 0))])
    A.sample(a, 'hand_r', 'rotation', lambda t: (0, 0, 28 * math.sin(t * 13) * min(1, t * 4, max(0, 1.4 - t) * 4)))
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-4, 0, 8)), (1.1, (-4, 0, 8)), (1.4, (0, 0, 0))])
    A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, -3)), (1.1, (0, 0, -3)), (1.4, (0, 0, 0))])
    a = m.anim('nod', 0.9)
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.2, (16, 0, 0)), (0.42, (0, 0, 0)), (0.62, (12, 0, 0)), (0.9, (0, 0, 0))])
    a = m.anim('bow', 1.7)
    A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (40, 0, 0)), (1.1, (40, 0, 0)), (1.7, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (12, 0, 0)), (1.1, (12, 0, 0)), (1.7, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.4, (-22, 0, 22)), (1.1, (-22, 0, 22)), (1.7, (0, 0, 0))])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.4, (-22, 0, -22)), (1.1, (-22, 0, -22)), (1.7, (0, 0, 0))])
    A.poses(a, 'hips', 'position', [(0, (0, 0, 0)), (0.4, (0, 0, 1.2)), (1.1, (0, 0, 1.2)), (1.7, (0, 0, 0))])
    a = m.anim('cheer', 1.1)
    A.poses(a, 'hips', 'position', [(0, (0, 0, 0)), (0.18, (0, 0.8, 0)), (0.38, (0, -2.6, 0)), (0.58, (0, 0, 0)), (1.1, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.25, (-165, 0, -22)), (0.85, (-165, 0, -22)), (1.1, (0, 0, 0))])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.25, (-165, 0, 22)), (0.85, (-165, 0, 22)), (1.1, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-12, 0, 0)), (0.85, (-12, 0, 0)), (1.1, (0, 0, 0))])
    A.poses(a, 'leg_l', 'rotation', [(0, (0, 0, 0)), (0.38, (-10, 0, -4)), (0.6, (0, 0, 0))])
    A.poses(a, 'leg_r', 'rotation', [(0, (0, 0, 0)), (0.38, (8, 0, 4)), (0.6, (0, 0, 0))])


# ------------------------------------------------------------------ 受付嬢 ツバキ (receptionist)
def receptionist():
    torso, arm, leg_h, leg_w = (8, 10, 5), (3, 10, 3), 12, 4
    m, b, skull = humanoid('npc_receptionist', leg_h, leg_w, torso, arm, 9, 0.93)
    skull.mat = 'n_skin'
    skull.faces = {'top': 'n_hair_tsubaki', 'south': 'n_hair_tsubaki'}
    limbs(b, torso, arm, leg_h, leg_w, {'torso': 'n_white', 'sleeve': 'n_white', 'hand': 'n_skin', 'leg': 'n_navy', 'foot': 'n_white'})
    head, body, hips = b['head'], b['body'], b['hips']
    head.cube((-4.5, -9, -4.5), (9, 9, 9), 'n_hair_tsubaki', inflate=0.35, name='hair')
    # ponytail with a crimson ribbon (two segments, sways)
    p1 = head.bone('pony1', (0, -6.6, 4.9), (28, 0, 0))
    p1.cube((-1.5, -0.6, -1.5), (3, 7, 3), 'n_hair_tsubaki')
    p2 = p1.bone('pony2', (0, 6.2, 0), (-18, 0, 0))
    p2.cube((-1.25, 0, -1.25), (2.5, 6, 2.5), 'n_hair_tsubaki', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.3}])
    p1.cube((-3.2, -1.6, -0.6), (2.2, 2, 1.2), 'n_crimson', name='ribbon_l')
    p1.cube((1.0, -1.6, -0.6), (2.2, 2, 1.2), 'n_crimson', name='ribbon_r')
    p1.cube((-1, -1.4, -1.8), (2, 1.6, 1.0), 'n_crimson')
    # camellia hair ornament (her left side)
    head.cube((4.3, -8.4, -2.6), (1.4, 2.4, 2.4), 'n_camellia', name='camellia')
    head.cube((4.9, -7.7, -1.9), (1.0, 1.0, 1.0), 'n_gold')
    head.cube((4.4, -6.2, -1.6), (0.8, 1.6, 1.2), 'n_moegi')                     # leaf
    # obi sash + bow, hakama skirt, sleeve cuffs, ledger
    body.cube((-4, -4.2, -2.5), (8, 2, 5), 'n_crimson', inflate=0.3, name='obi')
    body.cube((-2.6, -5.0, 2.7), (5.2, 3, 1.4), 'n_crimson', name='obi_bow')
    body.cube((-0.6, -4.4, -3.0), (1.2, 1.2, 0.4), 'n_gold', name='obi_clasp')
    hips.cube((-4.5, -2, -3), (9, 8, 6), 'n_navy', name='hakama', decals=[{'type': 'band', 'axis': 'down', 'range': (0.0, 0.1), 'mat': 'n_navy'}])
    for side, ab, x0 in (('l', b['arm_l'], 0), ('r', b['arm_r'], -3)):
        ab.cube((x0, 3.6, 1.0), (3, 4.4, 1.6), 'n_white', name='furisode_' + side)          # hanging sleeve panel
        ab.cube((x0, 6.2, -1.5), (3, 0.8, 3), 'n_crimson', inflate=0.3, name='cuff_' + side)
    b['leg_l'].cube((-2.2, 11.3, -2.6), (4.4, 0.7, 4.8), 'n_wood', name='zori_l')
    b['leg_r'].cube((-2.2, 11.3, -2.6), (4.4, 0.7, 4.8), 'n_wood', name='zori_r')
    led = b['hand_l'].bone('ledger', (0, 0, 0))
    led.cube((-0.2, -3.0, -2.6), (1.2, 4.2, 5.2), 'n_book', name='ledger')
    led.cube((-0.3, -2.8, -2.4), (1.0, 3.8, 4.8), 'n_paper', inflate=0.05, name='ledger_pages')

    def idle_extra(a):
        A.chain_wave(a, ['pony1', 'pony2'], (5, 0, 4), phase_step=0.8)

    def walk_extra(a):
        A.chain_wave(a, ['pony1', 'pony2'], (8, 0, 6), period=0.5, phase_step=0.7)

    base_animations(m, idle_extra, walk_extra)
    # work: holds the ledger up, writes / stamps with the right hand
    a = m.anim('work', 1.8)
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-62, 18, 8)), (1.5, (-62, 18, 8)), (1.8, (0, 0, 0))])
    A.poses(a, 'hand_l', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, -70)), (1.5, (0, 0, -70)), (1.8, (0, 0, 0))])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.35, (-58, -26, 0)), (0.55, (-46, -30, 0)), (0.75, (-60, -22, 0)),
                                     (0.95, (-46, -30, 0)), (1.15, (-60, -24, 0)), (1.5, (-58, -26, 0)), (1.8, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (18, 8, 0)), (1.45, (18, 8, 0)), (1.8, (0, 0, 0))])
    A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (5, 0, 0)), (1.45, (5, 0, 0)), (1.8, (0, 0, 0))])
    A.chain_wave(a, ['pony1', 'pony2'], (3, 0, 2), phase_step=0.8)

    def decorate(a, m):
        sk = find(m, 'skull')
        hair = find(m, 'hair')
        skin_d = hexc('#c8866e')
        ears(a, sk, skin_d)
        eye = hexc('#a8502a')
        grid(a, sk.face_rects()['north'], [
            '.........',
            '.........',
            '.........',
            '.LL...LL.',
            '.hi...hi.',
            '.II...II.',
            '.k.....k.',
            '...mMm...',
            '.........'], {'L': DARK, 'h': WHITE * 0.97, 'i': eye * 0.45, 'I': eye * 0.95,
                           'k': hexc('#f0a0a0'), 'm': hexc('#d48a7a'), 'M': hexc('#b0505a')})
        shell_masks(a, hair,
                    north=['XXXXXXXXX', 'XXXXXXXXX', 'XX.XXX.XX', 'X.......X', 'X.......X', 'X.......X', 'X.......X',
                           'X.......X', '.........'],
                    side=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'X..XXXXXX', 'X...XXXXX', 'X...XXXXX', 'X..XXXXXX',
                          'X..XXXXXX', '...XXXXXX'],
                    bottom=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', '.........'])
        # kimono collar (crossed V, left over right) + white under-collar on the torso front
        t = find(m, 'torso').face_rects()['north']   # 8 x 10
        grid(a, t, [
            'cW....Wc',
            '.cW..Wc.',
            '..cWWc..',
            '...cc...',
            '...c....',
            '....c...'], {'c': hexc('#b02a28'), 'W': hexc('#fbf8f2')})
        # guild crest on the obi bow
        u, v, w, h = find(m, 'obi_bow').face_rects()['south']
        a[v + 1, u + w // 2 - 1:u + w // 2 + 1, :3] = hexc('#f2c94a')
        # hakama pleats
        u, v, w, h = find(m, 'hakama').face_rects()['north']
        for x in (2, 4, 6):
            a[v + 2:v + h, u + x, :3] = a[v + 2:v + h, u + x, :3] * 0.72
        # ledger: gold corner + label
        u, v, w, h = find(m, 'ledger').face_rects()['west']
        a[v + 1:v + 3, u + 1:u + 3, :3] = hexc('#f0e4c8')
    return m, decorate


# ------------------------------------------------------------------ 鍛冶屋 ゲンテツ (blacksmith)
def blacksmith():
    torso, arm, leg_h, leg_w = (10, 10, 6), (4, 11, 4), 12, 5
    m, b, skull = humanoid('npc_blacksmith', leg_h, leg_w, torso, arm, 9, 0.97)
    skull.mat = 'n_skin_tan'
    skull.faces = {'top': 'n_hair_grey', 'south': 'n_hair_grey'}
    limbs(b, torso, arm, leg_h, leg_w, {'torso': 'n_charcoal', 'sleeve': 'n_skin_tan', 'hand': 'n_leather', 'leg': 'n_brown',
                                        'foot': 'n_leather_dark'})
    head, body = b['head'], b['body']
    head.cube((-4.5, -9, -4.5), (9, 9, 9), 'n_hair_grey', inflate=0.3, name='hair')
    head.cube((-4.5, -9, -4.5), (9, 2, 9), 'n_white', inflate=0.55, name='hachimaki')
    hk = head.bone('hachimaki_tails', (0, -8.0, 5.0), (12, 0, 0))
    hk.cube((-1.6, -0.6, -0.4), (1.2, 4.2, 0.6), 'n_white')
    hk.cube((0.4, -0.6, -0.4), (1.2, 3.4, 0.6), 'n_white')
    hk.cube((-1.0, -1.0, -0.8), (2.0, 1.6, 1.2), 'n_white')
    head.cube((-4.5, -2.0, -4.9), (9, 3, 1), 'n_beard_black', name='beard')
    head.cube((-3.5, 0.6, -4.7), (7, 2, 2), 'n_beard_black', name='beard_chin', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.35}])
    head.cube((-3.5, -3.0, -5.15), (7, 1, 0.6), 'n_beard_black', name='mustache')
    head.cube((-0.6, -4.6, -5.3), (1.2, 1.6, 0.8), 'n_skin_tan', name='nose')
    # short sleeves of the undershirt, leather apron with straps and tool pocket, belt + pouch
    for side, ab, x0 in (('l', b['arm_l'], 0), ('r', b['arm_r'], -4)):
        ab.cube((x0, -1, -2), (4, 3, 4), 'n_charcoal', inflate=0.25, name='shortsleeve_' + side)
        ab.cube((x0, 6.4, -2), (4, 2.6, 4), 'n_leather', inflate=0.2, name='bracer_' + side)
    body.cube((-4, -8.5, -3.7), (8, 13.5, 0.7), 'n_leather', name='apron')
    body.cube((-2.6, -1.8, -4.2), (5.2, 2.6, 0.6), 'n_leather_dark', name='apron_pocket')
    body.cube((-3.4, -10.2, -3.5), (1, 2, 0.5), 'n_leather_dark')
    body.cube((2.4, -10.2, -3.5), (1, 2, 0.5), 'n_leather_dark')
    body.cube((-5, -1.4, -3), (10, 1.4, 6), 'n_leather_dark', inflate=0.35, name='belt')
    body.cube((4.6, -1.8, -1.6), (1.6, 2.8, 3.2), 'n_leather', name='pouch')
    body.cube((-0.8, -1.6, -3.9), (1.6, 1.6, 0.5), 'n_gold', name='buckle')
    b['leg_l'].cube((-2.7, 10.8, -3.6), (5.4, 1.2, 1.4), 'n_iron', name='toecap_l')
    b['leg_r'].cube((-2.7, 10.8, -3.6), (5.4, 1.2, 1.4), 'n_iron', name='toecap_r')
    ham = b['hand_r'].bone('hammer', (0, 0.5, 0))
    ham.cube((-0.6, -1.0, -0.6), (1.2, 8, 1.2), 'n_wood_dark', name='hammer_handle')
    ham.cube((-2, 6.5, -3.5), (4, 4, 7), 'n_iron', name='hammer_head')
    ham.cube((-2.2, 7.1, -3.7), (4.4, 0.8, 7.4), 'n_leather_dark', name='hammer_band')

    def idle_extra(a):
        A.sine(a, 'hachimaki_tails', 'rotation', (6, 0, 4), phase=0.5)

    def walk_extra(a):
        A.sine(a, 'hachimaki_tails', 'rotation', (12, 0, 6), period=0.5)

    base_animations(m, idle_extra, walk_extra)
    # work: two heavy hammer blows
    a = m.anim('work', 1.5)
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-170, -8, 6)), (0.42, (-46, 0, 0)), (0.62, (-52, 0, 0)), (0.86, (-170, -8, 6)),
                                     (0.98, (-46, 0, 0)), (1.2, (-50, 0, 0)), (1.5, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-36, 10, -10)), (1.2, (-36, 10, -10)), (1.5, (0, 0, 0))])
    A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, 8, 0)), (0.42, (14, -4, 0)), (0.86, (-8, 8, 0)), (0.98, (14, -4, 0)),
                                    (1.2, (6, 0, 0)), (1.5, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (8, 0, 0)), (0.42, (16, 0, 0)), (1.2, (12, 0, 0)), (1.5, (0, 0, 0))])
    A.poses(a, 'hips', 'position', [(0, (0, 0, 0)), (0.42, (0, 0.6, 0)), (0.6, (0, 0, 0)), (0.98, (0, 0.6, 0)), (1.2, (0, 0, 0))])

    def decorate(a, m):
        sk = find(m, 'skull')
        hair = find(m, 'hair')
        ears(a, sk, hexc('#9a5834'))
        grid(a, sk.face_rects()['north'], [
            '.........',
            '.........',
            '.........',
            'BBB...BBB',
            '.wd...dw.',
            '.s.....s.',
            '.........',
            '.........',
            '.........'], {'B': hexc('#2a2420'), 'w': WHITE * 0.92, 'd': hexc('#2a1a12'), 's': hexc('#a0603a')})
        shell_masks(a, hair,
                    north=['XXXXXXXXX', 'XXXXXXXXX', 'X.......X', '.........'],
                    side=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'X..XXXXXX', 'X...XXXXX', 'X...XXXXX', 'XX.XXXXXX', 'XX.XXXXXX', 'XX.XXXXXX'],
                    bottom=['XXXXXXXXX', 'XXXXXXXXX', '.........'])
        # hachimaki: crest dot at the front, knot shading
        u, v, w, h = find(m, 'hachimaki').face_rects()['north']
        a[v:v + h, u + w // 2 - 1:u + w // 2 + 1, :3] = hexc('#2a4a8a')
        hk = find(m, 'hachimaki').face_rects()
        clear(a, hk['top'])
        clear(a, hk['bottom'])
        # apron: stitched edge + forge-hammer crest
        u, v, w, h = find(m, 'apron').face_rects()['north']
        for y in range(1, h - 1, 2):
            a[v + y, u + 1, :3] = hexc('#a07a50')
            a[v + y, u + w - 2, :3] = hexc('#a07a50')
        grid(a, (u, v + 3, w, 4), ['...GG...', '..GGGG..', '...GG...', '...GG...'], {'G': hexc('#d3a20f')})
        # muscles / scars on the bare arms
        for s in ('l', 'r'):
            u, v, w, h = find(m, 'sleeve_' + s).face_rects()['north']
            a[v + 4, u + 1:u + w - 1, :3] = a[v + 4, u + 1:u + w - 1, :3] * 0.86
    return m, decorate


# ------------------------------------------------------------------ よろず屋 コハル (merchant)
def merchant():
    torso, arm, leg_h, leg_w = (8, 10, 5), (3, 10, 3), 12, 4
    m, b, skull = humanoid('npc_merchant', leg_h, leg_w, torso, arm, 9, 0.92)
    skull.mat = 'n_skin'
    skull.faces = {'top': 'n_hair_koharu', 'south': 'n_hair_koharu'}
    limbs(b, torso, arm, leg_h, leg_w, {'torso': 'n_moegi', 'sleeve': 'n_moegi', 'hand': 'n_skin', 'leg': 'n_brown', 'foot': 'n_white'})
    head, body, hips = b['head'], b['body'], b['hips']
    head.cube((-4.5, -9, -4.5), (9, 9, 9), 'n_hair_koharu', inflate=0.35, name='hair')
    head.cube((-4.5, -5, -4.5), (9, 5, 9), 'n_hair_koharu', inflate=0.6, name='bob')
    head.cube((-4.5, -9, -4.5), (9, 2, 9), 'n_scarf', inflate=0.75, name='scarf')
    kn = head.bone('scarf_knot', (0, -7.4, 5.2), (10, 0, 0))
    kn.cube((-1.1, -1.1, -0.6), (2.2, 2.2, 1.4), 'n_scarf', name='knot')
    kn.cube((-2.4, 0.4, -0.2), (1.6, 3.6, 0.6), 'n_scarf', name='knot_tail_r')
    kn.cube((0.8, 0.4, -0.2), (1.6, 3.0, 0.6), 'n_scarf', name='knot_tail_l')
    # forearms bare (sleeves tied back with a tasuki cord), orange apron, travel pack
    for side, ab, x0 in (('l', b['arm_l'], 0), ('r', b['arm_r'], -3)):
        ab.cube((x0, 3.4, -1.5), (3, 4.6, 3), 'n_skin', inflate=-0.04, name='forearm_' + side)
        ab.cube((x0, 2.6, -1.5), (3, 1, 3), 'n_moegi', inflate=0.35, name='rolled_' + side)
    body.cube((-4, -3.2, -2.5), (8, 1.2, 5), 'n_orange', inflate=0.3, name='apron_band')
    hips.cube((-3.6, -2, -3.1), (7.2, 9, 0.6), 'n_orange', name='apron')
    hips.cube((-4.3, -2, -2.8), (8.6, 3, 5.6), 'n_brown', inflate=0.1, name='monpe_top')
    pk = body.bone('pack', (0, -5, 3.0))
    pk.cube((-3.6, -1, 0), (0.8, 13, 0.8), 'n_wood', name='pack_pole_r')
    pk.cube((2.8, -1, 0), (0.8, 13, 0.8), 'n_wood', name='pack_pole_l')
    pk.cube((-3.6, 6.5, 0), (7.2, 0.8, 4.2), 'n_wood', name='pack_shelf')
    pk.cube((-3.4, 0.6, 0.6), (6.8, 6, 4.6), 'n_indigo_pack', name='bundle_big')
    pk.cube((-2.6, -4.4, 0.8), (5.2, 5, 3.6), 'n_orange', name='bundle_small')
    pk.cube((-1.6, -5.6, 1.6), (3.2, 1.6, 2.0), 'n_orange', name='bundle_knot')
    pk.cube((-5.2, -7.2, 1.4), (10.4, 2.2, 2.2), 'n_straw', name='mat_roll')
    body.cube((-3.4, -10, -2.9), (1.2, 7.4, 0.5), 'n_rope', name='strap_r')
    body.cube((2.2, -10, -2.9), (1.2, 7.4, 0.5), 'n_rope', name='strap_l')
    b['leg_l'].cube((-2.2, 11.3, -2.6), (4.4, 0.7, 4.8), 'n_straw', name='waraji_l')
    b['leg_r'].cube((-2.2, 11.3, -2.6), (4.4, 0.7, 4.8), 'n_straw', name='waraji_r')
    ab = b['hand_r'].bone('abacus', (0, 0, 0))
    ab.cube((-0.6, -2.4, -3.6), (1.2, 3.4, 7.2), 'n_wood_dark', name='abacus')
    ab.cube((-0.75, -2.0, -3.2), (1.5, 2.6, 6.4), 'n_bead', name='abacus_beads')

    def idle_extra(a):
        A.sine(a, 'scarf_knot', 'rotation', (0, 0, 6), phase=0.2)

    def walk_extra(a):
        A.sine(a, 'pack', 'rotation', (3, 0, 2), period=0.5)
        A.sine(a, 'scarf_knot', 'rotation', (8, 0, 8), period=0.5)

    base_animations(m, idle_extra, walk_extra)
    # work: tallying on the abacus (held in front with both hands, beads flicked)
    a = m.anim('work', 1.8)
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-62, -30, 0)), (1.5, (-62, -30, 0)), (1.8, (0, 0, 0))])
    A.poses(a, 'hand_r', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, 80)), (1.5, (0, 0, 80)), (1.8, (0, 0, 0))])
    A.sample(a, 'arm_l', 'rotation', lambda t: (-58 + 7 * math.sin(t * 22) * min(1, t * 3, max(0, 1.8 - t) * 3), 30, 0))
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (16, -6, 6)), (1.0, (16, 6, -6)), (1.5, (14, 0, 0)), (1.8, (0, 0, 0))])
    A.poses(a, 'scarf_knot', 'rotation', [(0, (0, 0, 0)), (0.5, (0, 0, 10)), (1.0, (0, 0, -10)), (1.8, (0, 0, 0))])

    def decorate(a, m):
        sk = find(m, 'skull')
        ears(a, sk, hexc('#c8866e'))
        eye = hexc('#2f8a4c')
        grid(a, sk.face_rects()['north'], [
            '.........',
            '.........',
            '.........',
            '.LL...LL.',
            '.hi...hi.',
            '.II...II.',
            '.k.....k.',
            '...MMM...',
            '....t....'], {'L': DARK, 'h': WHITE * 0.97, 'i': eye * 0.45, 'I': eye,
                           'k': hexc('#f29a92'), 'M': hexc('#7a2a2e'), 't': hexc('#d86a6a')})
        shell_masks(a, find(m, 'hair'),
                    north=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'X.......X', 'X.......X', 'X.......X', '.........'],
                    side=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'X..XXXXXX', 'X...XXXXX', 'X...XXXXX', 'XX.XXXXXX', 'XXXXXXXXX', 'XXXXXXXXX'],
                    bottom=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', '.........'])
        bob = find(m, 'bob')
        r = bob.face_rects()
        clear(a, r['north'])
        clear(a, r['top'])
        cut(a, r['east'], ['X...XXXXX', 'X...XXXXX', 'XX.XXXXXX', 'XXXXXXXXX', 'XXXXXXXXX'])
        cut(a, r['west'], ['X...XXXXX', 'X...XXXXX', 'XX.XXXXXX', 'XXXXXXXXX', 'XXXXXXXXX'], flip=True)
        cut(a, r['bottom'], ['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', 'XX.....XX', 'X.......X', 'X.......X', 'X.......X', 'X.......X'])
        sc = find(m, 'scarf').face_rects()
        clear(a, sc['bottom'])
        cut(a, sc['north'], ['XXXXXXXXX', 'XXXXXXXXX', '.XXXXXXX.'])
        # kimono collar on the torso front (white under-collar), tasuki X on the back
        t = find(m, 'torso').face_rects()
        grid(a, t['north'], ['W......W', '.W....W.', '..W..W..', '...WW...', '...W....'], {'W': hexc('#f6f2ea')})
        grid(a, t['south'], ['R......R', '.R....R.', '..R..R..', '...RR...', '...RR...', '..R..R..', '.R....R.', 'R......R'], {'R': hexc('#c02a22')})
        # apron crest: a cream coin with a square hole (shop mark)
        u, v, w, h = find(m, 'apron').face_rects()['north']
        grid(a, (u + 1, v + 3, 6, 5), ['..WWW.', '.WWWWW', '.WW.WW', '.WWWWW', '..WWW.'], {'W': hexc('#fff0d0')})
        # furoshiki knot shading on the big bundle
        u, v, w, h = find(m, 'bundle_big').face_rects()['south']
        a[v + 1:v + 3, u + w // 2 - 1:u + w // 2 + 1, :3] = hexc('#f6f2ea')
    return m, decorate


# ------------------------------------------------------------------ 村長 ソウゲン (village chief)
def elder():
    torso, arm, leg_h, leg_w = (8, 10, 5), (3, 10, 3), 11, 4
    m, b, skull = humanoid('npc_elder', leg_h, leg_w, torso, arm, 9, 0.92, hunch=10)
    skull.mat = 'n_skin_old'
    limbs(b, torso, arm, leg_h, leg_w, {'torso': 'n_grey', 'sleeve': 'n_purple', 'hand': 'n_skin_old', 'leg': 'n_grey', 'foot': 'n_white'})
    head, body, hips = b['head'], b['body'], b['hips']
    head.cube((-4.5, -9, -4.5), (9, 9, 9), 'n_hair_white', inflate=0.35, name='hair')
    head.cube((-1.2, -10.4, 0.6), (2.4, 1.6, 2.4), 'n_hair_white', name='topknot')
    head.cube((-4.2, -5.6, -4.95), (3.2, 1.2, 0.8), 'n_hair_white', name='brow_r')
    head.cube((1.0, -5.6, -4.95), (3.2, 1.2, 0.8), 'n_hair_white', name='brow_l')
    head.cube((-0.6, -4.6, -5.1), (1.2, 1.8, 0.8), 'n_skin_old', name='nose')
    head.cube((-3.8, -2.9, -5.05), (7.6, 1.2, 0.8), 'n_hair_white', name='mustache')
    head.cube((-4.6, -2.4, -4.95), (1.2, 3.2, 0.7), 'n_hair_white', name='mustache_r')
    head.cube((3.4, -2.4, -4.95), (1.2, 3.2, 0.7), 'n_hair_white', name='mustache_l')
    bd = head.bone('beard', (0, -1.8, -4.6), (-6, 0, 0))
    bd.cube((-3.2, 0, -0.6), (6.4, 4.4, 1.4), 'n_hair_white', name='beard1')
    bd.cube((-2.4, 4.2, -0.4), (4.8, 4.6, 1.2), 'n_hair_white', name='beard2', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.3}])
    # haori coat over the kimono, crest, tie cord; hakama; staff with gourd
    body.cube((-4, -10, -2.5), (8, 12, 5), 'n_purple', inflate=0.35, name='haori')
    body.cube((-0.9, -6.4, -3.3), (1.8, 1.2, 0.6), 'n_white', name='haori_tie')
    for side, ab, x0 in (('l', b['arm_l'], 0), ('r', b['arm_r'], -3)):
        ab.cube((x0, 2.4, 1.0), (3, 6, 1.8), 'n_purple', name='sleeve_panel_' + side)
    hips.cube((-4.4, 1.0, -2.8), (8.8, 5, 5.6), 'n_grey', name='hakama')
    b['leg_l'].cube((-2.2, 10.2, -2.6), (4.4, 0.8, 4.8), 'n_wood', name='geta_l')
    b['leg_r'].cube((-2.2, 10.2, -2.6), (4.4, 0.8, 4.8), 'n_wood', name='geta_r')
    st = b['hand_r'].bone('staff', (0, 0.5, -0.4))
    st.cube((-0.6, -9, -0.6), (1.2, 18.5, 1.2), 'n_wood', name='staff_pole')
    st.cube((-1.3, -11.2, -1.3), (2.6, 2.6, 2.6), 'n_wood_dark', name='staff_knob')
    st.cube((-0.4, -9.4, -2.1), (2.6, 3.2, 2.2), 'n_gourd', name='gourd')
    st.cube((0.1, -10.2, -1.6), (1.4, 1.0, 1.2), 'n_gourd')
    st.cube((0.0, -9.6, -1.9), (1.6, 0.4, 1.8), 'n_rope')

    def idle_extra(a):
        A.sine(a, 'beard', 'rotation', (3, 0, 2), phase=0.8)

    def walk_extra(a):
        A.sine(a, 'beard', 'rotation', (5, 0, 3), period=0.5)

    base_animations(m, idle_extra, walk_extra)
    # the old man walks slowly: keep the staff hand calm during the walk
    m.animations['walk'].channels.pop('arm_r', None)
    A.sine(m.animations['walk'], 'arm_r', 'rotation', (6, 0, 0))
    # work: strokes his beard thoughtfully
    a = m.anim('work', 2.2)
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.35, (-78, -26, 26)), (1.9, (-78, -26, 26)), (2.2, (0, 0, 0))])
    A.sample(a, 'hand_l', 'rotation', lambda t: (10 * math.sin(t * 6.5) * min(1, t * 3, max(0, 2.2 - t) * 3), 0, 0))
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (-6, 10, 4)), (1.2, (-4, -6, -3)), (1.9, (-6, 8, 2)), (2.2, (0, 0, 0))])
    A.sample(a, 'beard', 'rotation', lambda t: (4 * math.sin(t * 6.5) * min(1, t * 3, max(0, 2.2 - t) * 3), 0, 0))

    def decorate(a, m):
        sk = find(m, 'skull')
        ears(a, sk, hexc('#b48468'))
        rim = hexc('#2a1e16')
        grid(a, sk.face_rects()['north'], [
            '.........',
            '..s...s..',
            '.........',
            'RRRR.RRRR',
            'R..RRR..R',
            'RddR.RddR',
            'RRRR.RRRR',
            '.........',
            '.........'], {'R': rim, 'd': hexc('#3a2a22'), 's': hexc('#c09a80')})
        # glass shine inside the lenses
        u, v, w, h = sk.face_rects()['north']
        for x in (1, 6):
            a[v + 4, u + x, :3] = np.minimum(a[v + 4, u + x, :3] * 1.18, 255)
        shell_masks(a, find(m, 'hair'),
                    north=['.........', '.........', 'X.......X', 'X.......X', 'X.......X', '.........'],
                    side=['.........', '.........', 'X..XXXXXX', 'X...XXXXX', 'X...XXXXX', 'X..XXXXXX', '...XXXXXX', '...XXXXXX', '....XXXXX'],
                    south=['.........', '.........', 'XXXXXXXXX'],
                    top=['.........'], bottom=['XXXXXXXXX', 'XXXXXXXXX', 'XXXXXXXXX', '.........'])
        # bald head: shiny highlight on the skull top
        u, v, w, h = sk.face_rects()['top']
        a[v + 3:v + 5, u + 3:u + 5, :3] = np.minimum(a[v + 3:v + 5, u + 3:u + 5, :3] * 1.12, 255)
        # haori front: kimono V (grey + white collar), family crests
        hr = find(m, 'haori').face_rects()
        grid(a, hr['north'], ['.WggggW.', '..WggW..', '..WggW..', '...WW...', '........', '........', '........', '........',
                              '........', '........', '........', '........'], {'W': hexc('#f4f2ec'), 'g': hexc('#6e6e76')})
        for (x, y) in ((1, 2), (6, 2)):
            grid(a, (hr['north'][0] + x - 1, hr['north'][1] + y, 3, 3), ['.c.', 'c.c', '.c.'], {'c': hexc('#f4f2ec')})
        grid(a, (hr['south'][0] + 3, hr['south'][1] + 2, 3, 3), ['.c.', 'c.c', '.c.'], {'c': hexc('#f4f2ec')})
        # hakama stripes
        u, v, w, h = find(m, 'hakama').face_rects()['north']
        for x in range(1, w, 3):
            a[v + 1:v + h, u + x, :3] = a[v + 1:v + h, u + x, :3] * 0.75
    return m, decorate


# ------------------------------------------------------------------ training dummy
def training_dummy():
    m = Model('training_dummy')
    m.config.update({'head_bones': [], 'walk_speed': 1.0, 'walk_scale': 1.0, 'render_scale': 1.0, 'action_speed': 1.0})
    root = m.bone('root', (0, 24, 0))
    base = root.bone('base', (0, 0, 0))
    base.cube((-7, -1.5, -1.2), (14, 1.5, 2.4), 'n_wood_dark', name='foot_x')
    base.cube((-1.2, -1.5, -7), (2.4, 1.5, 14), 'n_wood_dark', name='foot_z')
    base.cube((-2, -3.5, -2), (4, 2, 4), 'n_wood', name='socket')
    body = base.bone('body', (0, -3.2, 0))
    body.cube((-1, -26, -1), (2, 26, 2), 'n_wood', name='pole')
    body.cube((-4, -22, -3), (8, 11, 6), 'n_straw', name='torso')
    body.cube((-4.6, -12.4, -3.6), (9.2, 3.2, 7.2), 'n_straw', name='skirt', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.45}])
    body.cube((-4, -19.6, -3), (8, 1, 6), 'n_rope', inflate=0.3, name='rope1')
    body.cube((-4, -14.2, -3), (8, 1, 6), 'n_rope', inflate=0.3, name='rope2')
    body.cube((-10, -21.2, -1), (20, 2, 2), 'n_wood', name='crossbar')
    body.cube((-11.6, -22.6, -1.8), (3, 4.6, 3.6), 'n_straw', name='hand_r', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.3}])
    body.cube((8.6, -22.6, -1.8), (3, 4.6, 3.6), 'n_straw', name='hand_l', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.3}])
    body.cube((-10.4, -21.6, -1.4), (1.2, 2.8, 2.8), 'n_rope', inflate=0.15)
    body.cube((9.2, -21.6, -1.4), (1.2, 2.8, 2.8), 'n_rope', inflate=0.15)
    head = body.bone('head', (0, -22.4, 0))
    head.cube((-3.5, -7.4, -3.5), (7, 7, 7), 'n_sack', name='sack')
    head.cube((-3.5, -1.4, -3.5), (7, 1, 7), 'n_rope', inflate=0.25, name='neck_rope')
    hl = head.bone('horn_l', (2.4, -6.8, -0.6), (0, 0, -24))
    hl.cube((-0.8, -3.6, -0.8), (1.6, 4, 1.6), 'n_wood', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'n_paper'}])
    hr = head.bone('horn_r', (-2.4, -6.8, -0.6), (0, 0, 24))
    hr.cube((-0.8, -3.6, -0.8), (1.6, 4, 1.6), 'n_wood', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.4, 'mat': 'n_paper'}])
    a = m.anim('idle', 5.0, True)
    A.sine(a, 'body', 'rotation', (0.8, 0, 1.0))
    A.sine(a, 'head', 'rotation', (1.5, 3, 1.5), phase=0.9)
    a = m.anim('hit', 1.0)
    A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.07, (-15, 0, 4)), (0.24, (10, 0, -3)), (0.42, (-6, 0, 2)), (0.6, (3.5, 0, -1)),
                                    (0.8, (-1.5, 0, 0.5)), (1.0, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.1, (-12, 6, 6)), (0.3, (10, -4, -5)), (0.5, (-6, 2, 3)), (0.7, (3, 0, -1)),
                                    (1.0, (0, 0, 0))])
    A.poses(a, 'body', 'scale', [(0, (1, 1, 1)), (0.06, (1.06, 0.95, 1.06)), (0.2, (0.98, 1.02, 0.98)), (0.4, (1, 1, 1)), (1.0, (1, 1, 1))])
    A.poses(a, 'horn_l', 'rotation', [(0, (0, 0, 0)), (0.12, (0, 0, -8)), (0.35, (0, 0, 6)), (0.6, (0, 0, 0))])
    A.poses(a, 'horn_r', 'rotation', [(0, (0, 0, 0)), (0.12, (0, 0, 8)), (0.35, (0, 0, -6)), (0.6, (0, 0, 0))])

    def decorate(a, m):
        # target on the chest, stitched face on the sack
        t = find(m, 'torso').face_rects()['north']     # 8 x 11
        grid(a, (t[0], t[1] + 3, 8, 6), [
            '..RRRR..',
            '.RWWWWR.',
            'RWWRRWWR',
            'RWWRRWWR',
            '.RWWWWR.',
            '..RRRR..'], {'R': hexc('#b42a22'), 'W': hexc('#f2e8d2')})
        s = find(m, 'sack').face_rects()['north']       # 7 x 7
        grid(a, s, [
            '.......',
            '.......',
            '.x...x.',
            '..x.x..',
            '.x...x.',
            '.......',
            '.sssss.'], {'x': hexc('#2a1a10'), 's': hexc('#5a4228')})
        grid(a, s, ['.......', '.......', '.......', '.......', '.......', '.......', '.s.s.s.'], {'s': hexc('#2a1a10')})
        # patches on the sack sides
        r = find(m, 'sack').face_rects()
        u, v, w, h = r['east']
        a[v + 1:v + 3, u + 1:u + 3, :3] = hexc('#8a6a44')
        u, v, w, h = r['south']
        a[v + 3:v + 5, u + 4:u + 6, :3] = hexc('#6e5432')
    return m, decorate


BUILDERS = {'npc_receptionist': receptionist, 'npc_blacksmith': blacksmith, 'npc_merchant': merchant, 'npc_elder': elder,
            'training_dummy': training_dummy}


def build(name):
    m, decorate = BUILDERS[name]()
    m.pack()
    img, _ = P.paint_model(m)
    a = np.asarray(img).astype(float).copy()
    decorate(a, m)
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
    os.makedirs(os.path.join(ASSETS, 'hcmodels'), exist_ok=True)
    with open(os.path.join(ASSETS, 'hcmodels', name + '.json'), 'w') as f:
        json.dump(m.to_json(), f, separators=(',', ':'))
    if name.startswith('npc_'):
        out = os.path.join(ASSETS, 'textures', 'entity', 'npc', name + '.png')
    else:
        out = os.path.join(ASSETS, 'textures', 'entity', name + '.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print(name, m.tex_w, m.tex_h, len(list(m.all_cubes())), 'cubes')
    return m, img


def preview(models):
    from hcmodel.render import render, label
    os.makedirs(PREVIEW, exist_ok=True)
    size = 300
    tiles = []
    for name, (m, img) in models.items():
        idle = [(m.animations['idle'], 0.0, 1.0)]
        for yaw, pitch in ((25, 8), (0, 2), (160, 10)):
            im, _ = render(m, img, idle, size, yaw, pitch)
            tiles.append(label(im, '%s %d' % (name.replace('npc_', ''), yaw)))
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * size, rows * size), (20, 22, 26))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * size, (i // cols) * size))
    sheet.save(os.path.join(PREVIEW, 'npc.png'))
    # animation strips
    rows = []
    for name, (m, img) in models.items():
        for an in [n for n in ('walk', 'wave', 'bow', 'cheer', 'work', 'hit') if n in m.animations]:
            if name == 'training_dummy' and an != 'hit':
                continue
            if name != 'npc_receptionist' and an in ('walk', 'bow', 'cheer'):
                continue
            a = m.animations[an]
            _, fit = render(m, img, (), 170, 30, 10)
            row = []
            for k in range(6):
                tt = a.length * k / (6 if a.loop else 5)
                layers = [(m.animations['idle'], tt, 1.0), (a, tt, 1.0)]
                im, _ = render(m, img, layers, 170, 30, 10, (fit[0], fit[1] * 1.25))
                row.append(label(im, '%s %s %.2f' % (name.replace('npc_', ''), an, tt)))
            rows.append(row)
    sheet = Image.new('RGB', (6 * 170, len(rows) * 170), (20, 22, 26))
    for ri, r in enumerate(rows):
        for ci, im in enumerate(r):
            sheet.paste(im, (ci * 170, ri * 170))
    sheet.save(os.path.join(PREVIEW, 'npc_anims.png'))
    print('previews written')


def main():
    names = [a for a in sys.argv[1:] if not a.startswith('--')] or list(BUILDERS)
    models = {n: build(n) for n in names}
    if '--preview' in sys.argv:
        preview(models)


if __name__ == '__main__':
    main()
