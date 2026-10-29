#!/usr/bin/env python3
"""Mofu (お供モフ) cat-folk companion: fur model (6 fur patterns, NPC outfits) + gear model (8 material tiers).

Run from the repository root:  python3 tools/village/model_mofu.py [--preview]
Outputs:
  assets/huntcraft/hcmodels/mofu.json, mofu_gear.json
  assets/huntcraft/textures/entity/mofu/mofu_<pattern>.png, gear_<tier>.png
  tools/preview/mofu*.png
Both models share the skeleton (same bone names / pivots) so the gear layer follows every animation.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel import paint as P  # noqa: E402
from hcmodel.paint import M  # noqa: E402
from hcmodel.model import Cube  # noqa: E402

# Allocate UV rects with per-dimension ceil, matching how faces are painted (fractional sizes overflowed before).
Cube.uv_w = property(lambda self: int(2 * (math.ceil(self.size[2]) + math.ceil(self.size[0]))))
Cube.uv_h = property(lambda self: int(math.ceil(self.size[2]) + math.ceil(self.size[1])))

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
PREVIEW = os.path.join(ROOT, 'tools', 'preview')
PATTERNS = ['white', 'ginger', 'grey', 'black', 'tabby', 'calico']
TIERS = ['bamboo', 'bone', 'azurite', 'ignivald', 'frostgarm', 'voltrix', 'gravadon', 'azragos']

# ------------------------------------------------------------------ materials
M('mofu_belly', ['#8f8579', '#b8ad9f', '#d9d0c4', '#ece6dd', '#f8f4ee', '#ffffff'], 'fur', noise=0.04)
M('mofu_pink', ['#7a3a44', '#a4505e', '#c86c7a', '#e08a96', '#f2aab4', '#ffd0d6'], 'smooth', edge=0.0)
M('mofu_inner_ear', ['#6a3a40', '#8e4c56', '#b4646e', '#d0808a', '#e6a0a8', '#f6c4ca'], 'smooth', edge=0.04)
M('m_cloth_red', ['#3a0a0c', '#6a1416', '#9a2222', '#c63a34', '#e25c4a', '#f48a6e'], 'cloth')
M('m_cloth_white', ['#7a746c', '#a8a198', '#cfc9c0', '#e8e4dd', '#f6f3ee', '#ffffff'], 'cloth')
M('m_cloth_indigo', ['#0c1024', '#16203e', '#22325e', '#34497e', '#4a63a0', '#6a84c0'], 'cloth')
M('m_straw', ['#4a3818', '#7a5e2a', '#a8843e', '#cfaa58', '#e8c878', '#f6e2a2'], 'wood', edge=0.06)
M('m_wood', ['#2a1a0e', '#4a2e18', '#6e4624', '#8e6032', '#ae7c44', '#c89a5e'], 'wood')
M('m_metal', ['#262a30', '#41474f', '#636b75', '#89929c', '#b4bcc4', '#dfe5ea'], 'metal')
M('m_beard', ['#8a8478', '#b0a99c', '#d2ccc0', '#e8e4dc', '#f6f4ef', '#ffffff'], 'fur', noise=0.03)

# fur patterns: (base material, eye colour, eye highlight, paws white)
FUR = {
    'white': (['#857a70', '#ada296', '#cfc6bb', '#e6dfd6', '#f6f2ec', '#ffffff'], None, '#5aa8e8', True),
    'ginger': (['#4a2410', '#7a3d1a', '#a85a28', '#d07d3c', '#eba35c', '#fbc98a'], ('stripes_y', ['#2a1208', '#4a200e', '#6e3216', '#924620', '#b45e2e', '#d07d3c'], 0.6, 0.66), '#5ec46a', True),
    'grey': (['#2c3036', '#464c55', '#646c78', '#86909c', '#a8b2be', '#cdd6e0'], ('stripes_y', ['#1a1d22', '#262a31', '#353a43', '#454b56', '#565d69', '#68707e'], 0.65, 0.66), '#6cc8f0', True),
    'black': (['#0a0a0c', '#151519', '#22222a', '#32323b', '#45454f', '#5a5a66'], None, '#f2b636', False),
    'tabby': (['#3a2a1c', '#5c4430', '#806048', '#a27e62', '#c29f82', '#ddc0a4'], ('stripes_y', ['#1c140e', '#2e2218', '#463424', '#5e4632', '#765a42', '#8e6e54'], 0.7, 0.6), '#7ac850', True),
    'calico': (['#857a70', '#ada296', '#cfc6bb', '#e6dfd6', '#f6f2ec', '#ffffff'], ('spots', ['#1a1210', '#3a2418', '#7a3d1a', '#c06428', '#e08a40', '#f2b066'], 0.32, 0.6), '#d8a040', True),
}


def set_fur(pattern):
    base, accent, _, _ = FUR[pattern]
    if accent:
        mode, ramp, freq, th = accent
        M('mofu_fur_accent', ramp, 'fur')
        M('mofu_fur', base, 'fur', belly='mofu_belly', accent='mofu_fur_accent', accent_mode=mode, accent_freq=freq, accent_thresh=th, noise=0.05)
    else:
        M('mofu_fur', base, 'fur', belly='mofu_belly', noise=0.05)
    M('mofu_fur_plain', base, 'fur', noise=0.05)


# gear tiers: main, trim, metal (edge/blade), strap
GEAR = {
    'bamboo': (['#2c3a14', '#4a5e22', '#6e8434', '#94a84a', '#b8c868', '#dce694'], 'wood',
               ['#3a2a14', '#5a4220', '#7e5e30', '#a07c44', '#c09a5c', '#dcb87a'], ['#2e3a1a', '#4a5a2a', '#6a7e3e', '#8ea456', '#b2c676', '#d4e29e'], 'm_leather'),
    'bone': (['#4a4033', '#7d6e57', '#a8987c', '#cbbd9f', '#e6dcc4', '#f7f1e2'], 'bone',
             ['#2a1c12', '#46301e', '#64462c', '#84603c', '#a47c50', '#c09a6a'], ['#5a4c3a', '#8a7759', '#b7a37e', '#d8c7a2', '#efe3c6', '#fffaf0'], 'm_leather'),
    'azurite': (['#0c1a36', '#163060', '#22488c', '#3866b4', '#5a8ad6', '#8ab4f0'], 'metal',
                ['#2a2410', '#4e4218', '#7a6624', '#a68c36', '#ccb04e', '#ecd476'], ['#262a30', '#41474f', '#636b75', '#89929c', '#b4bcc4', '#e6ecf2'], 'm_leather'),
    'ignivald': (['#2a0806', '#561008', '#8a1d0c', '#bd3312', '#e05a20', '#f59a48'], 'scales',
                 ['#3a2600', '#6b4a00', '#a07400', '#d3a20f', '#f2c94a', '#fff0a8'], ['#140d0a', '#2a1c14', '#46301f', '#6b4a2e', '#946a42', '#c0925d'], 'm_leather'),
    'frostgarm': (['#2c3f5c', '#4a6a8f', '#7499bd', '#a3c4e0', '#d0e6f5', '#f4fbff'], 'fur',
                  ['#1b4d6b', '#2e7aa3', '#4fa9d3', '#86d0ef', '#c2ecfb', '#f5fdff'], ['#1d2533', '#334054', '#4d5d76', '#6c7f99', '#8fa3bb', '#b5c6d9'], 'm_leather'),
    'voltrix': (['#0d1c2a', '#173149', '#24496b', '#336590', '#4a86b5', '#6fadd8'], 'scales',
                ['#2c2508', '#5c4c0e', '#9a8016', '#d6b324', '#f2d74a', '#fff08a'], ['#3b3a2c', '#5e5c45', '#86836a', '#aba98e', '#cfcdb4', '#ebe9d6'], 'm_leather'),
    'gravadon': (['#1e1a17', '#36302a', '#524840', '#71655a', '#938677', '#b5a898'], 'rock',
                 ['#14200f', '#22361a', '#355228', '#4d703a', '#6b8f50', '#8fb06c'], ['#2a1f19', '#45342a', '#634c3d', '#836654', '#a3826d', '#c3a08a'], 'm_leather'),
    'azragos': (['#0a0612', '#170d26', '#25153d', '#382057', '#4e2f73', '#6a4593'], 'plates',
                ['#4a0c3a', '#86125e', '#c21f7d', '#e9409c', '#ff78c0', '#ffb3df'], ['#0c0a0e', '#1c181f', '#302a34', '#4a414f', '#6a5f70', '#8f8396'], 'm_leather'),
}
M('m_leather', ['#1f140c', '#3a2616', '#573a22', '#76512f', '#966b40', '#b58955'], 'skin')


def set_gear(tier):
    main, pat, trim, metal, strap = GEAR[tier]
    M('gear_main', main, pat, scale=0.6 if pat in ('scales', 'plates') else 0.8)
    M('gear_trim', trim, 'metal' if tier not in ('gravadon',) else 'fur')
    M('gear_metal', metal, 'metal' if tier != 'bamboo' else 'wood')
    M('gear_strap', P.MATERIALS[strap].ramp if False else ['#1f140c', '#3a2616', '#573a22', '#76512f', '#966b40', '#b58955'], 'skin')


# ------------------------------------------------------------------ skeleton
def skeleton(m):
    """Bones shared by the fur and gear models. Returns dict name -> bone."""
    b = {}
    b['root'] = m.bone('root', (0, 24, 0))
    b['body'] = b['root'].bone('body', (0, -6, 0))
    b['head'] = b['body'].bone('head', (0, -6, 0))
    b['ear_l'] = b['head'].bone('ear_l', (3.0, -8.2, 0.4), (0, 0, 14))
    b['ear_r'] = b['head'].bone('ear_r', (-3.0, -8.2, 0.4), (0, 0, -14))
    b['arm_l'] = b['body'].bone('arm_l', (3.1, -5.2, 0), (0, 0, -8))
    b['arm_r'] = b['body'].bone('arm_r', (-3.1, -5.2, 0), (0, 0, 8))
    b['hand_r'] = b['arm_r'].bone('hand_r', (-1, 5, 0))
    b['hand_l'] = b['arm_l'].bone('hand_l', (1, 5, 0))
    b['leg_l'] = b['body'].bone('leg_l', (1.6, 0, 0.2))
    b['leg_r'] = b['body'].bone('leg_r', (-1.6, 0, 0.2))
    b['tail1'] = b['body'].bone('tail1', (0, -1.5, 2.4), (-35, 0, 0))
    b['tail2'] = b['tail1'].bone('tail2', (0, 0, 3.5), (25, 0, 0))
    b['tail3'] = b['tail2'].bone('tail3', (0, 0, 3.2), (20, 0, 0))
    return b


def fur_model():
    m = Model('mofu')
    m.config.update({'head_bones': [['head', 0.8]], 'walk_speed': 1.6, 'run_speed': 1.5, 'walk_scale': 2.4, 'render_scale': 0.66,
                     'action_speed': 1.0})
    b = skeleton(m)
    body, head = b['body'], b['head']
    # torso (pear shaped: wider hips, fluffy chest)
    body.cube((-3, -6, -2.5), (6, 6, 5), 'mofu_fur')
    body.cube((-2.5, -5.4, -3.0), (5, 5, 1), 'mofu_belly')                 # white chest / belly
    body.cube((-3.5, -7.2, -3.0), (7, 2, 6), 'mofu_fur_plain', name='ruff')  # neck ruff
    # head: big round skull, cheek fluff, muzzle, cowlick
    skull = head.cube((-5, -8, -4), (10, 8, 8), 'mofu_fur', name='skull')
    head.cube((-4, -8.8, -3), (8, 0.8, 6), 'mofu_fur_plain', name='crown')          # rounded top
    head.cube((-1, -9.6, -2.2), (2, 0.8, 2), 'mofu_fur_plain', name='cowlick')
    for side, x in (('l', 4.4), ('r', -6.0)):
        head.cube((x, -3.8, -3.2), (1.6, 3.2, 4.4), 'mofu_fur_plain', name='cheek_' + side,
                  decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.25}])
    head.cube((-2, -3.2, -4.6), (4, 2.4, 1), 'mofu_belly', name='muzzle')
    # ears: stepped triangles (pink inside painted on the front faces)
    for side, eb in (('l', b['ear_l']), ('r', b['ear_r'])):
        eb.cube((-2, -2, -0.8), (4, 2, 1.6), 'mofu_fur', name='ear_base_' + side)
        eb.cube((-1.5, -3.5, -0.7), (3, 1.5, 1.4), 'mofu_fur', name='ear_mid_' + side)
        eb.cube((-0.75, -4.5, -0.6), (1.5, 1, 1.2), 'mofu_fur_plain', name='ear_tip_' + side)
    # arms + paws
    for side, ab, sx in (('l', b['arm_l'], 1), ('r', b['arm_r'], -1)):
        x0 = 0 if sx > 0 else -2
        ab.cube((x0, -0.6, -1), (2, 5, 2), 'mofu_fur')
        ab.cube((x0 - 0.25, 3.6, -1.25), (2.5, 2, 2.5), 'mofu_belly', name='paw_' + side)
    # legs + feet
    for side, lb in (('l', b['leg_l']), ('r', b['leg_r'])):
        lb.cube((-1.5, 0, -1.5), (3, 4, 3), 'mofu_fur')
        lb.cube((-1.75, 4, -2.6), (3.5, 2, 4), 'mofu_belly', name='foot_' + side)
    # tail: three fluffy segments
    b['tail1'].cube((-1, -1, 0), (2, 2, 4), 'mofu_fur')
    b['tail2'].cube((-1.2, -1.2, 0), (2.4, 2.4, 3.6), 'mofu_fur')
    b['tail3'].cube((-1.5, -1.5, 0), (3, 3, 3.6), 'mofu_fur', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.45, 'mat': 'mofu_belly'}])
    # ---- outfits (toggle bones, hidden unless an entity shows them)
    scarf = body.bone('scarf', (0, -6.6, 0))
    scarf.cube((-3.7, -0.8, -3.2), (7.4, 1.6, 6.4), 'm_cloth_red')
    scarf.cube((1.2, 0.6, -3.4), (1.6, 2.6, 0.4), 'm_cloth_red')        # knot tails
    scarf.cube((-0.2, 0.6, -3.4), (1.2, 2.0, 0.4), 'm_cloth_red')
    scarf.cube((-2.6, 0.2, -3.9), (1.4, 1.4, 1.2), 'gold')               # companion bell
    hat = head.bone('hat_chef', (0, -8, 0))
    hat.cube((-4.4, -2.2, -3.6), (8.8, 2.2, 7.2), 'm_cloth_white')          # band
    hat.cube((-5.2, -6.2, -4.4), (10.4, 4.2, 8.8), 'm_cloth_white')        # puffy top
    hat.cube((-4.4, -7.2, -3.6), (8.8, 1.0, 7.2), 'm_cloth_white')
    hat.cube((-4.5, -1.2, -3.7), (9, 0.6, 0.2), 'm_cloth_red')             # ribbon
    apron = body.bone('apron', (0, -6, 0))
    apron.cube((-3.2, 1.5, -3.15), (6.4, 6.5, 0.4), 'm_cloth_white')
    apron.cube((-3.3, 1.2, -3.0), (6.6, 0.8, 6.0), 'm_cloth_indigo')     # waist band
    ladle = b['hand_r'].bone('ladle', (0, 0, 0))
    ladle.cube((-0.4, -0.5, -7), (0.8, 0.8, 7), 'm_wood')
    ladle.cube((-1.2, -1.5, -8.6), (2.4, 2, 2), 'm_wood')
    kasa = head.bone('kasa', (0, -8.2, 0))
    kasa.cube((-7.5, -1.2, -7.5), (15, 1.2, 15), 'm_straw')
    kasa.cube((-5, -2.4, -5), (10, 1.2, 10), 'm_straw')
    kasa.cube((-2.5, -3.6, -2.5), (5, 1.2, 5), 'm_straw')
    kasa.cube((-1, -4.4, -1), (2, 0.8, 2), 'm_straw')
    beard = head.bone('beard', (0, -1.8, -4.6))
    beard.cube((-2.4, 0, -0.2), (4.8, 3.6, 0.6), 'm_beard', decals=[{'type': 'cutout', 'shape': 'ragged', 'depth': 0.3}])
    beard.cube((-4.8, -0.6, -0.4), (2.6, 0.8, 0.4), 'm_beard')          # mustache
    beard.cube((2.2, -0.6, -0.4), (2.6, 0.8, 0.4), 'm_beard')
    staff = b['hand_r'].bone('staff', (0, 0, 0))
    staff.cube((-0.5, -9, -0.5), (1, 15, 1), 'm_wood')
    staff.cube((-1.2, -11, -1.2), (2.4, 2.4, 2.4), 'm_wood')
    haori = body.bone('haori', (0, -6, 0))
    haori.cube((-3.4, -0.3, -2.9), (6.8, 6.8, 5.8), 'm_cloth_indigo')
    haori.cube((-3.6, -0.6, -3.0), (2.2, 1, 6.0), 'm_cloth_white')
    haori.cube((1.4, -0.6, -3.0), (2.2, 1, 6.0), 'm_cloth_white')
    add_animations(m, b)
    return m, skull


def gear_model():
    g = Model('mofu_gear')
    g.config.update({'head_bones': [['head', 0.8]], 'walk_speed': 1.6, 'run_speed': 1.5, 'walk_scale': 2.4, 'render_scale': 0.66})
    b = skeleton(g)
    # helm: cat-ear helmet shell over the top of the head, brim and crest
    helm = b['head'].bone('helm', (0, -8, 0))
    helm.cube((-5.5, -0.8, -4.5), (11, 4.2, 9), 'gear_main')
    helm.cube((-5.8, 3.0, -4.8), (11.6, 1.0, 1.4), 'gear_trim')          # brim
    helm.cube((-1, -2.2, -4.9), (2, 2.4, 2.4), 'gear_trim')              # crest
    helm.cube((-5.8, 0.4, -2.0), (1, 4.2, 5.4), 'gear_main')             # cheek guards
    helm.cube((4.8, 0.4, -2.0), (1, 4.2, 5.4), 'gear_main')
    # armor: chest plate, belt, shoulder pads (on the arm bones so they follow the arms)
    armor = b['body'].bone('armor', (0, -6, 0))
    armor.cube((-3.5, 0.4, -3.1), (7, 4.4, 6.2), 'gear_main')
    armor.cube((-3.6, 4.6, -3.2), (7.2, 1.0, 6.4), 'gear_strap')
    armor.cube((-1.2, 1.2, -3.5), (2.4, 2.4, 0.6), 'gear_trim')          # chest emblem
    armor.cube((-3.7, 5.4, -3.1), (2.4, 2.2, 0.5), 'gear_main')          # tassets
    armor.cube((1.3, 5.4, -3.1), (2.4, 2.2, 0.5), 'gear_main')
    pad_l = b['arm_l'].bone('armor_pad_l', (0, 0, 0))
    pad_l.cube((-0.4, -1.4, -1.5), (3, 2.2, 3), 'gear_main')
    pad_r = b['arm_r'].bone('armor_pad_r', (0, 0, 0))
    pad_r.cube((-2.6, -1.4, -1.5), (3, 2.2, 3), 'gear_main')
    # weapons in the right paw
    blade = b['hand_r'].bone('w_blade', (0, 0.4, 0), (70, 0, 0))
    blade.cube((-0.5, -0.5, -2), (1, 1, 3), 'gear_strap')                # grip
    blade.cube((-1.4, -1.2, -2.6), (2.8, 2.4, 0.6), 'gear_trim')         # guard
    blade.cube((-0.4, -0.9, -9.6), (0.8, 1.8, 7), 'gear_metal', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.2, 'mat': 'gear_trim'}])
    horn = b['hand_r'].bone('w_horn', (0, 0.5, -0.5), (0, 0, 0))
    horn.cube((-0.8, -0.8, -1.5), (1.6, 1.6, 3), 'gear_main')            # mouthpiece end
    horn.cube((-1.1, -2.6, -2.0), (2.2, 2.2, 2.4), 'gear_main')
    horn.cube((-1.5, -4.8, -1.6), (3.0, 2.6, 3.0), 'gear_main')
    horn.cube((-2.0, -6.2, -1.2), (4.0, 1.8, 4.0), 'gear_trim')          # bell
    horn.cube((-1.3, -1.3, -0.4), (2.6, 0.6, 0.6), 'gear_strap')         # cord
    bomb = b['hand_r'].bone('w_bomb', (0, 1.0, -0.5))
    bomb.cube((-1.6, -1.2, -1.6), (3.2, 3.2, 3.2), 'gear_main')
    bomb.cube((-1.7, -0.2, -1.7), (3.4, 0.6, 3.4), 'gear_trim')          # hoop
    bomb.cube((-0.3, -2.4, -0.3), (0.6, 1.2, 0.6), 'gear_strap')         # fuse
    pack = b['body'].bone('pack', (0, -6, 2.5))
    pack.cube((-2.6, 0.2, 0), (5.2, 4.6, 2.6), 'gear_strap')
    pack.cube((-2.2, -1.4, 0.4), (2, 2, 2), 'gear_main')                 # bombs peeking out
    pack.cube((0.2, -1.2, 0.4), (2, 2, 2), 'gear_main')
    pack.cube((-3.2, 0.6, -5.5), (0.6, 4, 5.6), 'gear_strap')            # straps
    pack.cube((2.6, 0.6, -5.5), (0.6, 4, 5.6), 'gear_strap')
    add_animations(g, b)
    return g


# ------------------------------------------------------------------ animations
def add_animations(m, b):
    # idle: breathing, head bob, ear twitches, tail sway
    a = m.anim('idle', 3.0, True)
    A.sine(a, 'body', 'position', (0, 0.25, 0))
    A.sine(a, 'body', 'scale', (0.025, -0.02, 0.025), offset=(1, 1, 1))
    A.sine(a, 'head', 'rotation', (2.5, 0, 1.5), phase=0.6)
    A.poses(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (1.1, (0, 0, 0)), (1.2, (-8, 0, 14)), (1.35, (0, 0, 0)), (3.0, (0, 0, 0))])
    A.poses(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (2.2, (0, 0, 0)), (2.3, (-8, 0, -14)), (2.45, (0, 0, 0)), (3.0, (0, 0, 0))])
    A.chain_wave(a, ['tail1', 'tail2', 'tail3'], (6, 16, 0), phase_step=0.7)
    A.sine(a, 'arm_l', 'rotation', (2, 0, -2), phase=0.3)
    A.sine(a, 'arm_r', 'rotation', (2, 0, 2), phase=0.3)
    # walk: waddling steps, arm swing, body sway
    a = m.anim('walk', 0.7, True)
    A.sine(a, 'leg_l', 'rotation', (36, 0, 0))
    A.sine(a, 'leg_r', 'rotation', (-36, 0, 0))
    A.sine(a, 'arm_l', 'rotation', (-40, 0, 0))
    A.sine(a, 'arm_r', 'rotation', (40, 0, 0))
    A.sine(a, 'body', 'rotation', (0, 6, 5))
    A.sample(a, 'body', 'position', lambda tt: (0, -0.9 * abs(math.sin(2 * math.pi * tt / 0.7)), 0))
    A.sine(a, 'head', 'rotation', (0, -5, -4))
    A.chain_wave(a, ['tail1', 'tail2', 'tail3'], (0, 22, 0), phase_step=0.6)
    # run: bounding gait, ears back
    a = m.anim('run', 0.45, True)
    A.sine(a, 'leg_l', 'rotation', (60, 0, 0))
    A.sine(a, 'leg_r', 'rotation', (-60, 0, 0))
    A.sine(a, 'arm_l', 'rotation', (-55, 0, -10))
    A.sine(a, 'arm_r', 'rotation', (55, 0, 10))
    A.hold(a, 'body', 'rotation', (12, 0, 0))
    A.sample(a, 'body', 'position', lambda tt: (0, -1.6 * abs(math.sin(2 * math.pi * tt / 0.45)), 0))
    A.hold(a, 'ear_l', 'rotation', (-30, 0, 10))
    A.hold(a, 'ear_r', 'rotation', (-30, 0, -10))
    A.chain_wave(a, ['tail1', 'tail2', 'tail3'], (14, 10, 0), phase_step=0.5)
    # attack: overhead slash with the right paw
    a = m.anim('attack', 0.45)
    A.strike(a, 'arm_r', 'rotation', (0, 0, 0), (-150, 10, 20), (-20, -10, 10), 0.14, 0.22, 0.4)
    A.strike(a, 'body', 'rotation', (0, 0, 0), (-8, 15, 0), (14, -20, 0), 0.14, 0.22, 0.4)
    A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.14, (0, -1.5, 1)), (0.22, (0, 0, -2)), (0.45, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.14, (-40, 0, -20)), (0.3, (20, 0, -10)), (0.45, (0, 0, 0))])
    # attack2: spinning side slash
    a = m.anim('attack2', 0.5)
    A.poses(a, 'root', 'rotation', [(0, (0, 0, 0)), (0.12, (0, 30, 0)), (0.3, (0, -330, 0)), (0.5, (0, -360, 0))])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.12, (-20, 0, 80)), (0.3, (-10, 0, 95)), (0.5, (0, 0, 0))])
    A.poses(a, 'root', 'position', [(0, (0, 0, 0)), (0.2, (0, -2, 0)), (0.36, (0, 0, 0)), (0.5, (0, 0, 0))])
    # throw: wind up and lob (bomber)
    a = m.anim('throw', 0.6)
    A.strike(a, 'arm_r', 'rotation', (0, 0, 0), (-200, 0, 10), (-60, 0, 0), 0.25, 0.34, 0.55)
    A.strike(a, 'body', 'rotation', (0, 0, 0), (-12, 10, 0), (16, -10, 0), 0.25, 0.34, 0.55)
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.25, (-60, 0, -30)), (0.4, (10, 0, -10)), (0.6, (0, 0, 0))])
    # horn: raise the horn to the mouth, sway with the tune
    a = m.anim('horn', 1.6)
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.2, (-95, 30, 25)), (1.4, (-95, 30, 25)), (1.6, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.2, (-80, -30, -25)), (1.4, (-80, -30, -25)), (1.6, (0, 0, 0))])
    A.sample(a, 'body', 'rotation', lambda tt: (-6 * min(1, tt * 5, (1.6 - tt) * 5), 0, 6 * math.sin(tt * 7)))
    A.sample(a, 'head', 'rotation', lambda tt: (-14 * min(1, tt * 5, (1.6 - tt) * 5), 0, -4 * math.sin(tt * 7)))
    A.poses(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, 0, 20)), (1.3, (-10, 0, 20)), (1.6, (0, 0, 0))])
    A.poses(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, 0, -20)), (1.3, (-10, 0, -20)), (1.6, (0, 0, 0))])
    # cheer: jump with both paws up
    a = m.anim('cheer', 0.9)
    A.poses(a, 'root', 'position', [(0, (0, 0, 0)), (0.15, (0, 1, 0)), (0.35, (0, -5, 0)), (0.55, (0, 0, 0)), (0.9, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.2, (-160, 0, -30)), (0.7, (-160, 0, -30)), (0.9, (0, 0, 0))])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.2, (-160, 0, 30)), (0.7, (-160, 0, 30)), (0.9, (0, 0, 0))])
    A.poses(a, 'tail1', 'rotation', [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.9, (0, 0, 0))])
    # dance: blade dance spin (fighter special)
    a = m.anim('dance', 1.0)
    A.poses(a, 'root', 'rotation', [(0, (0, 0, 0), 'l'), (1.0, (0, -720, 0), 'l')])
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.1, (-30, 0, 90)), (0.9, (-30, 0, 90)), (1.0, (0, 0, 0))])
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.1, (-30, 0, -90)), (0.9, (-30, 0, -90)), (1.0, (0, 0, 0))])
    A.sample(a, 'root', 'position', lambda tt: (0, -1.5 * abs(math.sin(tt * math.pi * 4)), 0))
    # NPC actions
    a = m.anim('wave', 1.2)
    A.poses(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.2, (-150, 0, -20)), (1.0, (-150, 0, -20)), (1.2, (0, 0, 0))])
    A.sample(a, 'arm_l', 'rotation', lambda tt: (0, 0, 25 * math.sin(tt * 14) * min(1, tt * 5, (1.2 - tt) * 5)))
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (0, 0, -10)), (1.0, (0, 0, -10)), (1.2, (0, 0, 0))])
    a = m.anim('nod', 0.8)
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.2, (18, 0, 0)), (0.4, (0, 0, 0)), (0.6, (14, 0, 0)), (0.8, (0, 0, 0))])
    A.poses(a, 'ear_l', 'rotation', [(0, (0, 0, 0)), (0.2, (12, 0, 0)), (0.8, (0, 0, 0))])
    A.poses(a, 'ear_r', 'rotation', [(0, (0, 0, 0)), (0.2, (12, 0, 0)), (0.8, (0, 0, 0))])
    a = m.anim('stir', 1.6)
    A.sample(a, 'arm_r', 'rotation', lambda tt: (-60 + 12 * math.sin(tt * 8), 18 * math.cos(tt * 8), 10))
    A.sample(a, 'body', 'rotation', lambda tt: (10, 4 * math.sin(tt * 8), 0))
    A.sample(a, 'head', 'rotation', lambda tt: (14, 0, 3 * math.sin(tt * 4)))
    a = m.anim('tap', 1.0)
    A.poses(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.25, (-25, 0, 0)), (0.4, (6, 0, 0)), (0.6, (-25, 0, 0)), (0.75, (6, 0, 0)), (1.0, (0, 0, 0))])
    A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.4, (8, 0, 4)), (1.0, (0, 0, 0))])


# ------------------------------------------------------------------ face painting
def paint_face(img, skull, muzzle, pattern):
    """Big round eyes, pink nose, mouth and whiskers on the head's north faces."""
    a = np.asarray(img).copy()
    _, _, eye, _ = FUR[pattern]
    eyec = P.hexrgb(eye)
    u, v, w, h = skull.face_rects()['north']      # 10 x 8
    dark = np.array([28, 20, 18])
    white = np.array([255, 255, 255])

    def px(x, y, c):
        a[v + y, u + x, :3] = c
        a[v + y, u + x, 3] = 255

    for ex in (1, 6):                               # eyes: 3x3, dark top with a highlight, bright iris below
        for dx in range(3):
            px(ex + dx, 2, dark)
        px(ex, 3, white * 0.96)
        px(ex + 1, 3, dark)
        px(ex + 2, 3, eyec * 0.35 + dark * 0.65)
        px(ex, 4, eyec * 0.85)
        px(ex + 1, 4, np.minimum(eyec * 1.15, 255))
        px(ex + 2, 4, eyec * 0.85)
    # brows / fur above eyes stays; blush under eyes
    blush = np.array([236, 150, 160])
    for x in (0, 9):
        px(x, 5, blush * 0.85 + a[v + 5, u + x, :3] * 0.15)
    mu, mv, mw, mh = muzzle.face_rects()['north']   # 4 x 3 (rounded up)
    # nose (pink) top centre of the muzzle, mouth "w" below
    a[mv, mu + 1:mu + 3, :3] = P.hexrgb('#e07a8a')
    a[mv, mu + 1:mu + 3, 3] = 255
    a[mv + 1, mu + 1:mu + 3, :3] = dark
    a[mv + 2, mu, :3] = dark
    a[mv + 2, mu + 3, :3] = dark
    # whiskers on the cheeks' north faces
    for cname in ('cheek_l', 'cheek_r'):
        pass
    return Image.fromarray(a, 'RGBA')


def find(m, name):
    for c in m.all_cubes():
        if c.name == name:
            return c
    raise KeyError(name)


def whiskers(img, m):
    a = np.asarray(img).copy()
    for cname in ('cheek_l', 'cheek_r'):
        c = find(m, cname)
        u, v, w, h = c.face_rects()['north']
        a[v + 1, u:u + w, :3] = (60, 50, 46)
    return Image.fromarray(a, 'RGBA')


def inner_ears(img, m):
    a = np.asarray(img).copy()
    pink = P.hexrgb('#e8a0aa')
    deep = P.hexrgb('#c87884')
    for side in ('l', 'r'):
        u, v, w, h = find(m, 'ear_base_' + side).face_rects()['north']   # 4 x 2
        a[v:v + h, u + 1:u + w - 1, :3] = [pink, deep][0]
        a[v + h - 1, u + 1:u + w - 1, :3] = deep
        u, v, w, h = find(m, 'ear_mid_' + side).face_rects()['north']    # 3 x 2
        a[v + h - 1, u + 1:u + w - 1, :3] = pink
    return Image.fromarray(a, 'RGBA')


def face_icon(img, skull, pattern):
    """24x24 face icon for GUIs (front of the head + ears silhouette), returns PIL image."""
    u, v, w, h = skull.face_rects()['north']
    face = img.crop((u, v, u + w, v + h)).resize((20, 16), Image.NEAREST)
    out = Image.new('RGBA', (24, 24), (0, 0, 0, 0))
    base = P.hexrgb(FUR[pattern][0][3])
    dark = P.hexrgb(FUR[pattern][0][1])
    from PIL import ImageDraw
    d = ImageDraw.Draw(out)
    for ex in (3, 15):   # ears
        d.polygon([(ex, 8), (ex + 3, 1), (ex + 6, 8)], fill=tuple(int(x) for x in base) + (255,), outline=tuple(int(x) for x in dark) + (255,))
        d.polygon([(ex + 2, 7), (ex + 3, 4), (ex + 4, 7)], fill=(224, 140, 150, 255))
    out.paste(face, (2, 6), face)
    d.rectangle([1, 5, 22, 22], outline=tuple(int(x) for x in dark) + (255,))
    return out


def main():
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity', 'mofu'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'hcmodels'), exist_ok=True)
    import json
    m, skull = fur_model()
    set_fur('white')
    m.pack()
    with open(os.path.join(ASSETS, 'hcmodels', 'mofu.json'), 'w') as f:
        json.dump(m.to_json(), f, separators=(',', ':'))
    muzzle = find(m, 'muzzle')
    faces = {}
    texs = {}
    for pat in PATTERNS:
        set_fur(pat)
        img, _ = P.paint_model(m)
        img = paint_face(img, skull, muzzle, pat)
        img = inner_ears(img, m)
        img.save(os.path.join(ASSETS, 'textures', 'entity', 'mofu', 'mofu_%s.png' % pat))
        faces[pat] = face_icon(img, skull, pat)
        texs[pat] = img
    os.makedirs(os.path.join(ROOT, 'tools', 'village', 'out'), exist_ok=True)
    strip = Image.new('RGBA', (24 * 6, 24), (0, 0, 0, 0))
    for i, pat in enumerate(PATTERNS):
        strip.paste(faces[pat], (i * 24, 0))
    strip.save(os.path.join(ROOT, 'tools', 'village', 'out', 'mofu_faces.png'))
    g = gear_model()
    set_gear('bamboo')
    g.pack()
    with open(os.path.join(ASSETS, 'hcmodels', 'mofu_gear.json'), 'w') as f:
        json.dump(g.to_json(), f, separators=(',', ':'))
    gtex = {}
    for tier in TIERS:
        set_gear(tier)
        gimg, _ = P.paint_model(g)
        gimg.save(os.path.join(ASSETS, 'textures', 'entity', 'mofu', 'gear_%s.png' % tier))
        gtex[tier] = gimg
    print('mofu', m.tex_w, m.tex_h, 'gear', g.tex_w, g.tex_h)
    if '--preview' in sys.argv:
        preview(m, g, texs, gtex)


def merged(m, g, tex, gtex, shown, gear_shown):
    """Single Model + texture combining fur and gear (for previews only)."""
    from hcmodel.model import Model as Mo
    W = max(m.tex_w, g.tex_w)
    H = m.tex_h + g.tex_h
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    canvas.paste(tex, (0, 0))
    canvas.paste(gtex, (0, m.tex_h))
    mm = Mo('preview', W, H)
    mm.animations = m.animations
    toggles = {'hat_chef', 'apron', 'ladle', 'kasa', 'beard', 'staff', 'haori', 'scarf'}
    gtoggles = {'w_blade', 'w_horn', 'w_bomb', 'pack', 'helm', 'armor', 'armor_pad_l', 'armor_pad_r'}

    def copy(src, dst_parent, model, dy, hidden):
        nb = (dst_parent.bone if dst_parent is not None else mm.bone)(src.name + ('' if dy == 0 else '#g'), src.pivot, src.rot)
        if src.name not in hidden:
            for c in src.cubes:
                cc = nb.cube(c.origin, c.size, c.mat, inflate=c.inflate + (0.02 if dy else 0), mirror=c.mirror)
                cc.uv = (c.uv[0], c.uv[1] + dy)
        for ch in src.children:
            copy(ch, nb, model, dy, hidden)
        return nb

    hid = {t for t in toggles if t not in shown}
    ghid = {t for t in gtoggles if t not in gear_shown}
    if 'armor' in gear_shown:
        ghid -= {'armor_pad_l', 'armor_pad_r'}
    for r in m.roots:
        copy(r, None, m, 0, hid)
    for r in g.roots:
        copy(r, None, g, m.tex_h, ghid | {'root#g'})
    # animations address bone names; duplicate channels for the '#g' copies
    from hcmodel.model import Animation
    anims = {}
    for n, a in m.animations.items():
        na = Animation(n, a.length, a.loop)
        for bn, ch in a.channels.items():
            na.channels[bn] = ch
            na.channels[bn + '#g'] = ch
        anims[n] = na
    mm.animations = anims
    return mm, canvas


def preview(m, g, texs, gtex):
    from hcmodel.render import render, label
    os.makedirs(PREVIEW, exist_ok=True)
    size = 300
    tiles = []
    configs = [('white', set(), set(), 'ignivald'), ('ginger', {'scarf'}, {'w_blade', 'helm', 'armor'}, 'bone'),
               ('grey', {'scarf'}, {'w_horn', 'armor'}, 'frostgarm'), ('black', {'scarf'}, {'w_bomb', 'pack', 'helm'}, 'voltrix'),
               ('tabby', {'scarf'}, {'w_blade', 'helm', 'armor'}, 'azragos'), ('calico', {'scarf'}, {'w_horn', 'helm', 'armor'}, 'bamboo')]
    for pat, shown, gshown, tier in configs:
        mm, canvas = merged(m, g, texs[pat], gtex[tier], shown, gshown)
        idle = [(mm.animations['idle'], 0.0, 1.0)]
        im, _ = render(mm, canvas, idle, size, 30, 12)
        tiles.append(label(im, '%s %s' % (pat, tier)))
    # NPC outfits
    for pat, shown in (('white', {'hat_chef', 'apron', 'ladle'}), ('grey', {'kasa', 'beard', 'staff', 'haori'})):
        mm, canvas = merged(m, g, texs[pat], gtex['bone'], shown, set())
        im, _ = render(mm, canvas, [(mm.animations['idle'], 0.0, 1.0)], size, 30, 12)
        tiles.append(label(im, 'npc ' + pat))
    # face close-up front + side
    mm, canvas = merged(m, g, texs['ginger'], gtex['bone'], {'scarf'}, set())
    im, _ = render(mm, canvas, [(mm.animations['idle'], 0.0, 1.0)], size, 0, 4)
    tiles.append(label(im, 'front'))
    im, _ = render(mm, canvas, [(mm.animations['idle'], 0.0, 1.0)], size, 90, 4)
    tiles.append(label(im, 'side'))
    cols = 5
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * size, rows * size), (20, 22, 26))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * size, (i // cols) * size))
    sheet.save(os.path.join(PREVIEW, 'mofu.png'))
    # animation strip
    mm, canvas = merged(m, g, texs['tabby'], gtex['ignivald'], {'scarf'}, {'w_blade', 'helm', 'armor'})
    rows = []
    for an in ('walk', 'run', 'attack', 'attack2', 'throw', 'horn', 'cheer', 'wave', 'stir'):
        a = mm.animations[an]
        row = []
        _, fit = render(mm, canvas, (), 200, 35, 15)
        c, s = fit
        fit = (c, s * 1.35)
        for k in range(6):
            tt = a.length * k / (6 if a.loop else 5)
            layers = [(mm.animations['idle'], tt, 1.0), (a, tt, 1.0)]
            im, _ = render(mm, canvas, layers, 200, 35, 15, fit)
            row.append(label(im, '%s %.2f' % (an, tt)))
        rows.append(row)
    sheet = Image.new('RGB', (6 * 200, len(rows) * 200), (20, 22, 26))
    for ri, r in enumerate(rows):
        for ci, im in enumerate(r):
            sheet.paste(im, (ci * 200, ri * 200))
    sheet.save(os.path.join(PREVIEW, 'mofu_anims.png'))
    print('previews written')


if __name__ == '__main__':
    main()
