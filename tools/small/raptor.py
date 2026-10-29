"""Shared raptor skeleton + animations for Jagraptor (blue/orange, fin crest) and Blizraptor (white/ice, frost crest).

Faces -Z, +Y down, ground y = 24. Left = +X.
"""
import math

import numpy as np

from smallkit import Model, A, M, fit_to_ground, keys, cyc, stamp, stamp_eyes, accent_pass
from hcmodel.parts import mirror_tree
from hcmodel.paint import vnoise

# ------------------------------------------------------------------ materials (own registry entries)
M('jag_skin', ['#10233a', '#1a3d62', '#25578a', '#3374ae', '#4b93cc', '#74b6e2'], 'scales', scale=1.0, contrast=0.5,
  noise=0.035, edge=0.07, belly='jag_belly')
M('jag_limb', ['#10233a', '#1a3d62', '#25578a', '#3374ae', '#4b93cc', '#74b6e2'], 'scales', scale=0.8, contrast=0.45,
  noise=0.035, edge=0.07)
M('jag_dark', ['#07101c', '#0c1a2b', '#132840', '#1c3858', '#284b72', '#36608d'], 'scales', contrast=0.5, noise=0.04, edge=0.06)
M('jag_belly', ['#7a6e52', '#a09272', '#c2b592', '#dccfae', '#eee4c8', '#fbf6e6'], 'skin', contrast=0.3, noise=0.03, edge=0.04, base=0.62)
M('jag_stripe', ['#3a1206', '#6a240c', '#a03c14', '#d05c20', '#ef8434', '#ffb064'], 'scales', contrast=0.45, noise=0.03, edge=0.0)
M('jag_crest', ['#3a1006', '#6e230c', '#a83c14', '#dc5e20', '#f78a3a', '#ffb870'], 'smooth', contrast=0.6, noise=0.04, edge=0.06)
M('jag_crest_tip', ['#0b1a2c', '#13304e', '#1d4874', '#2a6399', '#3f81b8', '#62a3d4'], 'smooth')
M('bliz_skin', ['#56657a', '#7a8da3', '#a2b6ca', '#c8d8e6', '#e6f0f8', '#ffffff'], 'scales', scale=1.0, contrast=0.4,
  noise=0.03, edge=0.07, belly='bliz_belly', base=0.6)
M('bliz_limb', ['#56657a', '#7a8da3', '#a2b6ca', '#c8d8e6', '#e6f0f8', '#ffffff'], 'scales', scale=0.8, contrast=0.35,
  noise=0.03, edge=0.07, base=0.6)
M('bliz_dark', ['#2c3a4c', '#405268', '#5a6f88', '#7a90a8', '#9cb0c6', '#bfd0e2'], 'scales', contrast=0.5, noise=0.04, edge=0.06)
M('bliz_belly', ['#7a8693', '#9ca8b4', '#bcc6d0', '#d8e0e8', '#eef2f6', '#ffffff'], 'skin', contrast=0.3, noise=0.03, edge=0.04, base=0.62)
M('bliz_stripe', ['#0d2d48', '#174a72', '#226a9c', '#3a8fc4', '#62b5e2', '#9ad8f7'], 'scales', contrast=0.45, noise=0.03, edge=0.0)
M('frost_spike', ['#1b4d6b', '#2e7aa3', '#4fa9d3', '#86d0ef', '#c2ecfb', '#f5fdff'], 'crystal', glow='#bff3ff', edge=0.0)
M('raptor_claw', ['#1a1410', '#2e251d', '#4a3d31', '#6d5d4c', '#968470', '#c2b29c'], 'horn', edge=0.0)


def build(name, variant):
    jag = variant == 'jag'
    skin = 'jag_skin' if jag else 'bliz_skin'
    limb = 'jag_limb' if jag else 'bliz_limb'
    dark = 'jag_dark' if jag else 'bliz_dark'
    stripe = 'jag_stripe' if jag else 'bliz_stripe'

    m = Model(name)
    m.config.update({'head_bones': [['neck', 0.35], ['head', 0.55]], 'walk_speed': 3.4, 'run_speed': 0.55, 'walk_scale': 2.6})

    # ------------------------------------------------------------------ body
    body = m.bone('body', (0, 11.5, 1), (-5, 0, 0))
    torso = body.cube((-3.5, -3.5, -8), (7, 7, 12), skin, faces={'top': dark}, decals=[{'type': 'belly_band'}])
    body.cube((-1, -4.5, -7), (2, 1, 10), dark)                                    # spine ridge
    for z, hgt in ((-6, 2), (-3, 2), (0, 2), (2.5, 1)):
        body.cube((-0.5, -4.5 - hgt, z), (1, hgt, 2), dark)                         # little back spines

    # ------------------------------------------------------------------ neck + head
    neck = body.bone('neck', (0, -1.5, -7), (-44, 0, 0))
    neck_c = neck.cube((-2, -2, -7), (4, 4, 8), skin, faces={'top': dark})
    head = neck.bone('head', (0, -0.5, -6), (49, 0, 0))
    skull = head.cube((-2.5, -3, -4), (5, 5, 5), skin, faces={'top': dark})
    snout = head.cube((-2, -2, -9), (4, 3, 5), skin, faces={'top': dark})
    head.cube((-1.5, 0.75, -8.5), (3, 1, 4), 'teeth', inflate=-0.25)               # upper teeth
    head.cube((-3, -3.5, -3.5), (6, 1, 3), limb, faces={'top': dark})               # brow ridge
    jaw = head.bone('jaw', (0, 1, -3), (0, 0, 0))
    jaw.cube((-2, 0, -6), (4, 2, 7), skin, faces={'top': 'mouth'})
    jaw.cube((-1.5, -0.75, -5.5), (3, 1, 4), 'teeth', inflate=-0.25)
    jaw.cube((-1, -0.25, -4.5), (2, 1, 4), 'tongue', inflate=-0.3)

    # eyes / teeth pixel art
    if jag:
        pal = {'B': '#173252', 'Y': '#ffd23f', 'O': '#f08a1c', 'K': '#140c08', 'W': '#fff6c8'}
    else:
        pal = {'B': '#3c4c62', 'Y': ('#8ff0ff', '#6fe6ff'), 'O': ('#2f9fd8', '#3fb6e8'), 'K': '#0a1a2a', 'W': ('#ffffff', '#e8ffff')}
    stamp_eyes(m, skull, ['BBB.', 'YKW.', 'OKY.'], pal, front=0, top=1)
    tooth = {'T': '#efe8d4'}
    stamp(m, snout, 'east', 0, 2, ['T.T.T'], tooth)
    stamp(m, snout, 'west', 0, 2, ['T.T.T'], tooth, mirror=True)
    stamp(m, snout, 'north', 0, 0, ['K..K'], {'K': '#0c1420' if jag else '#3a4a5a'})

    # crest
    crest = head.bone('crest', (0, -4, -1), (0, 0, 0))
    if jag:
        # stepped fin of solid slabs, sweeping back
        for i, (z, h, tilt) in enumerate(((-3.0, 2, -10), (-1.5, 3, -20), (0.0, 4, -30), (1.5, 5, -40), (3.0, 4, -52))):
            fin = crest.bone('fin%d' % i, (0, 0.5, z), (tilt, 0, 0))
            fin.cube((-0.5, -h, -1), (1, h, 2), 'jag_crest', decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.3, 'mat': 'jag_crest_tip'}])
        cf = head.bone('frill_l', (2.5, -0.5, -0.5), (0, 35, -15))
        cf.cube((0, -2, 0), (0, 4, 4), 'jag_crest', decals=[{'type': 'cutout', 'shape': 'spike'}])
        mirror_tree(head, cf)
    else:
        for i, (x, z, h, tilt, roll) in enumerate(((0, -2.5, 3, -18, 0), (0, 0, 6, -32, 0), (0, 2.5, 5, -56, 0),
                                                   (1.3, 0.5, 4, -42, 26), (-1.3, 0.5, 4, -42, -26))):
            sp = crest.bone('spike%d' % i, (x, 0.5, z), (tilt, 0, roll))
            sp.cube((-1, -h, -1), (2, h, 2), 'frost_spike', inflate=-0.25,
                    decals=[{'type': 'tip', 'axis': 'up', 'frac': 0.5, 'mat': 'ice_crystal'}])
        cf = head.bone('frill_l', (2.5, -0.5, -0.5), (0, 35, -15))
        cf.cube((0, -2, 0), (0, 4, 4), 'raptor_ice', decals=[{'type': 'cutout', 'shape': 'spike'}])
        mirror_tree(head, cf)

    # ------------------------------------------------------------------ arms
    arm = body.bone('arm_l', (3.2, 2.5, -6), (40, 0, -10))
    arm.cube((-1, -0.5, -1), (2, 3, 2), limb)
    fore = arm.bone('forearm_l', (0, 2.2, 0), (-75, 0, 0))
    fore.cube((-1, -0.5, -1), (2, 3, 2), limb, inflate=-0.25)
    fore.cube((-1, 2, -1.25), (2, 1, 2), 'raptor_claw', inflate=-0.2)
    mirror_tree(body, arm)

    # ------------------------------------------------------------------ legs (digitigrade)
    th = body.bone('leg_l_thigh', (3.5, 1, 1), (-30, 0, 0))
    th_c = th.cube((-1.5, -1.5, -2), (3, 7, 4), limb, faces={'top': dark})
    sh = th.bone('leg_l_shin', (0, 5, 0.5), (72, 0, 0))
    sh.cube((-1, -0.5, -1), (2, 6, 2), limb)
    ft = sh.bone('leg_l_foot', (0, 5.2, 0), (-42, 0, 0))
    ft.cube((-1, -0.5, -1), (2, 4, 2), limb, inflate=-0.1)
    ft.cube((-1.5, 3, -3.5), (3, 1, 4), limb)
    for x in (-1.5, -0.25, 1.0):
        ft.cube((x, 3, -4.5), (1, 1, 1), 'raptor_claw', inflate=-0.05)
    ft.cube((-1.6, 1.5, -2.6), (1, 2, 1), 'raptor_claw', inflate=-0.1)            # sickle claw
    mirror_tree(body, th)

    # ------------------------------------------------------------------ tail
    tail_cubes = []
    parent = body
    piv = (0, -1, 3.5)
    for i, (w, h, ln, rx) in enumerate([(5, 5, 6, 12), (4, 4, 6, -4), (3, 3, 6, -5), (2, 2, 5, -4)]):
        b = parent.bone('tail%d' % (i + 1), piv, (rx, 0, 0))
        tail_cubes.append(b.cube((-w / 2, -h / 2, 0), (w, h, ln + 1), skin, faces={'top': dark}))
        parent = b
        piv = (0, 0, ln)
    tip = parent.bone('tail_tip', (0, 0, 5), (0, 0, 0))
    if jag:
        tip.cube((0, -2, 0), (0, 4, 4), 'jag_crest', decals=[{'type': 'cutout', 'shape': 'spike_back'}])
        tip.cube((-1, -1, -1), (2, 2, 3), 'jag_stripe', inflate=-0.2)
    else:
        tip.cube((-1, -1, -0.5), (2, 2, 4), 'frost_spike', inflate=-0.25, decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.5, 'mat': 'ice_crystal'}])

    # stripes on the back and flanks (not the belly), following each part's long axis
    def stripes(freq, thresh, phase=0.0, axis=2):
        def fn(P, L, N, f, d, r, face):
            if face == 'bottom' or N[1] > 0.5:
                return None
            s = L[..., axis] * freq + phase + (vnoise(P[..., 0] * 0.3, P[..., 1] * 0.3, P[..., 2] * 0.3, 11) - 0.5) * 1.6
            band = np.sin(s) > thresh
            upper = d < 0.62 if face in ('west', 'east', 'north', 'south') else np.ones_like(d, dtype=bool)
            return band & upper
        return fn
    accent_pass(m, [torso], stripe, stripes(0.95, 0.45))
    accent_pass(m, [neck_c], stripe, stripes(1.1, 0.55, 1.0))
    accent_pass(m, tail_cubes, stripe, stripes(1.0, 0.35, 0.5))
    accent_pass(m, [th_c], stripe, stripes(1.3, 0.5, 0.3, axis=1))

    fit_to_ground(m, body)
    animate(m, jag)
    return m


TAIL = ['tail1', 'tail2', 'tail3', 'tail4']
LEGS = {'l': ('leg_l_thigh', 'leg_l_shin', 'leg_l_foot'), 'r': ('leg_r_thigh', 'leg_r_shin', 'leg_r_foot')}


def legs_walk(a, period, thigh, knee, foot, steps=16):
    for side, ph in (('l', 0.0), ('r', math.pi)):
        t, s, f = LEGS[side]
        A.sample(a, t, 'rotation', lambda x, ph=ph: (thigh * cyc(x, period, ph), 0, 0), steps)
        A.sample(a, s, 'rotation', lambda x, ph=ph: (knee * max(0.0, cyc(x, period, ph - 1.9)) - knee * 0.15, 0, 0), steps)
        A.sample(a, f, 'rotation', lambda x, ph=ph: (foot * cyc(x, period, ph - 1.2), 0, 0), steps)


def legs_hold(a, thigh, shin, foot):
    for side in ('l', 'r'):
        t, s, f = LEGS[side]
        A.hold(a, t, 'rotation', (thigh, 0, 0))
        A.hold(a, s, 'rotation', (shin, 0, 0))
        A.hold(a, f, 'rotation', (foot, 0, 0))


def legs_keys(a, pts_thigh, pts_shin, pts_foot):
    for side in ('l', 'r'):
        t, s, f = LEGS[side]
        keys(a, t, 'rotation', pts_thigh)
        keys(a, s, 'rotation', pts_shin)
        keys(a, f, 'rotation', pts_foot)


def animate(m, jag):
    T = m.anim
    # idle: breathing, tail sway, head bob, crest twitch
    a = T('idle', 2.4, True)
    A.sine(a, 'body', 'position', (0, 0.35, 0))
    A.sine(a, 'body', 'rotation', (0.8, 0, 0), phase=0.4)
    A.sine(a, 'neck', 'rotation', (2.0, 3, 0), phase=0.8)
    A.sine(a, 'head', 'rotation', (-2.5, 4, 2), period=1.2, phase=1.3)
    A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0), period=1.2)
    A.chain_wave(a, TAIL, (1.5, 6, 0), phase_step=0.6, growth=1.15)
    A.sine(a, 'arm_l', 'rotation', (4, 0, 0), phase=0.2)
    A.sine(a, 'arm_r', 'rotation', (4, 0, 0), phase=0.5)
    A.sine(a, 'crest', 'rotation', (3, 0, 0), period=0.8)

    # walk: bouncy bird-like gait
    a = T('walk', 1.0, True)
    legs_walk(a, 1.0, 30, 38, 22)
    A.sample(a, 'body', 'position', lambda t: (0, -0.8 * abs(cyc(t, 1.0)), 0), 16)
    A.sine(a, 'body', 'rotation', (1.5, 3, 2.5), period=1.0)
    A.sample(a, 'neck', 'rotation', lambda t: (4 * cyc(t, 0.5, 1.0), 0, 0), 16)
    A.sample(a, 'head', 'rotation', lambda t: (-4 * cyc(t, 0.5, 1.0), 0, 0), 16)
    A.chain_wave(a, TAIL, (2, 9, 0), period=1.0, phase_step=0.6, growth=1.1)
    A.sine(a, 'arm_l', 'rotation', (12, 0, 0), period=1.0, phase=math.pi)
    A.sine(a, 'arm_r', 'rotation', (12, 0, 0), period=1.0)

    # run: stretched forward, long strides
    a = T('run', 0.6, True)
    legs_walk(a, 0.6, 48, 60, 30, steps=14)
    A.sample(a, 'body', 'position', lambda t: (0, -1.6 * abs(cyc(t, 0.6)), 0), 14)
    A.sample(a, 'body', 'rotation', lambda t: (8 + 3 * cyc(t, 0.3, 0.6), 0, 0), 14)
    A.hold(a, 'neck', 'rotation', (24, 0, 0))
    A.sample(a, 'head', 'rotation', lambda t: (-20 - 3 * cyc(t, 0.3, 0.9), 0, 0), 14)
    A.chain_wave(a, TAIL, (-3, 5, 0), period=0.6, phase_step=0.5)
    A.hold(a, 'arm_l', 'rotation', (-30, 0, 0))
    A.hold(a, 'arm_r', 'rotation', (-30, 0, 0))
    A.hold(a, 'crest', 'rotation', (-12, 0, 0))

    # bite: rear back, snap forward
    a = T('bite', 0.9)
    keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, 0.5, 2)), (0.42, (0, 0.5, -3.5)), (0.6, (0, 0, -2.5)), (0.9, (0, 0, 0))])
    keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-6, 0, 0)), (0.42, (8, 0, 0)), (0.9, (0, 0, 0))])
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-16, 0, 0)), (0.42, (30, 0, 0)), (0.6, (24, 0, 0)), (0.9, (0, 0, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-14, 0, 0)), (0.42, (-14, 0, 0)), (0.9, (0, 0, 0))])
    keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.3, (42, 0, 0)), (0.4, (40, 0, 0)), (0.45, (0, 0, 0)), (0.55, (8, 0, 0)), (0.9, (0, 0, 0))])
    keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (0.3, (-14, 0, 0)), (0.5, (6, 0, 0)), (0.9, (0, 0, 0))])
    A.chain_wave(a, TAIL, (-4, 6, 0), period=0.45, phase_step=0.5)
    legs_keys(a, [(0, (0, 0, 0)), (0.3, (-8, 0, 0)), (0.42, (14, 0, 0)), (0.9, (0, 0, 0))],
              [(0, (0, 0, 0)), (0.3, (12, 0, 0)), (0.42, (-6, 0, 0)), (0.9, (0, 0, 0))],
              [(0, (0, 0, 0)), (0.3, (-6, 0, 0)), (0.42, (-8, 0, 0)), (0.9, (0, 0, 0))])

    # leap_kick: crouch, spring, double kick in the air, land
    a = T('leap_kick', 1.4)
    keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.35, (0, 3.5, 1)), (0.45, (0, -2, -2)), (0.6, (0, -5, -3)), (0.85, (0, -2, -2)),
                                 (0.95, (0, 2.5, 0)), (1.15, (0, 0.5, 0)), (1.4, (0, 0, 0))])
    keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (10, 0, 0)), (0.5, (-24, 0, 0)), (0.8, (-28, 0, 0)), (0.95, (6, 0, 0)), (1.4, (0, 0, 0))])
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.35, (14, 0, 0)), (0.6, (26, 0, 0)), (0.95, (10, 0, 0)), (1.4, (0, 0, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (-10, 0, 0)), (0.6, (-6, 0, 0)), (1.4, (0, 0, 0))])
    keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.45, (30, 0, 0)), (0.8, (24, 0, 0)), (1.0, (0, 0, 0))])
    legs_keys(a, [(0, (0, 0, 0)), (0.35, (-26, 0, 0)), (0.5, (-62, 0, 0)), (0.62, (-34, 0, 0)), (0.72, (-70, 0, 0)), (0.9, (-20, 0, 0)), (1.0, (-18, 0, 0)), (1.4, (0, 0, 0))],
              [(0, (0, 0, 0)), (0.35, (34, 0, 0)), (0.5, (-30, 0, 0)), (0.62, (10, 0, 0)), (0.72, (-34, 0, 0)), (0.9, (20, 0, 0)), (1.0, (30, 0, 0)), (1.4, (0, 0, 0))],
              [(0, (0, 0, 0)), (0.35, (-14, 0, 0)), (0.5, (30, 0, 0)), (0.72, (34, 0, 0)), (0.95, (-10, 0, 0)), (1.4, (0, 0, 0))])
    for i, b in enumerate(TAIL):
        keys(a, b, 'rotation', [(0, (0, 0, 0)), (0.35, (-6, 0, 0)), (0.55, (14, 0, 0)), (0.85, (12, 0, 0)), (1.0, (-6, 0, 0)), (1.4, (0, 0, 0))])
    keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.5, (-50, 0, -30)), (0.9, (-40, 0, -20)), (1.4, (0, 0, 0))])
    keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.5, (-50, 0, 30)), (0.9, (-40, 0, 20)), (1.4, (0, 0, 0))])

    # pack_call: rear up, head to the sky, two yips with the crest flared
    a = T('pack_call', 1.5)
    keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, 0, 0)), (1.2, (-9, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, -1, 1)), (1.2, (0, -1, 1)), (1.5, (0, 0, 0))])
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-14, 0, 0)), (0.45, (-18, 0, 0)), (0.65, (-12, 0, 0)), (0.9, (-18, 0, 0)), (1.2, (-12, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (1.2, (-30, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.4, (4, 0, 0)), (0.47, (40, 0, 0)), (0.62, (4, 0, 0)), (0.85, (4, 0, 0)), (0.92, (42, 0, 0)), (1.1, (4, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (0.4, (14, 0, 0)), (1.2, (14, 0, 0)), (1.5, (0, 0, 0))])
    keys(a, 'crest', 'scale', [(0, (1, 1, 1)), (0.4, (1, 1.25, 1.15)), (1.2, (1, 1.25, 1.15)), (1.5, (1, 1, 1))])
    keys(a, 'frill_l', 'rotation', [(0, (0, 0, 0)), (0.4, (0, 30, 0)), (1.2, (0, 30, 0)), (1.5, (0, 0, 0))])
    keys(a, 'frill_r', 'rotation', [(0, (0, 0, 0)), (0.4, (0, -30, 0)), (1.2, (0, -30, 0)), (1.5, (0, 0, 0))])
    A.chain_wave(a, TAIL, (3, 14, 0), period=0.5, phase_step=0.5)
    keys(a, 'arm_l', 'rotation', [(0, (0, 0, 0)), (0.4, (-30, 0, -20)), (1.2, (-30, 0, -20)), (1.5, (0, 0, 0))])
    keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.4, (-30, 0, 20)), (1.2, (-30, 0, 20)), (1.5, (0, 0, 0))])

    if not jag:
        # ice_spit: rear the head back, puff, spit
        a = T('ice_spit', 1.3)
        keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.45, (-8, 0, 0)), (0.58, (6, 0, 0)), (1.3, (0, 0, 0))])
        keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.45, (0, 0, 2)), (0.58, (0, 0, -1.5)), (1.3, (0, 0, 0))])
        keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.45, (-22, 0, 0)), (0.58, (22, 0, 0)), (0.8, (18, 0, 0)), (1.3, (0, 0, 0))])
        keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.45, (-16, 0, 0)), (0.58, (-8, 0, 0)), (1.3, (0, 0, 0))])
        keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.4, (12, 0, 0)), (0.55, (44, 0, 0)), (0.8, (30, 0, 0)), (1.1, (0, 0, 0))])
        keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (0.45, (10, 0, 0)), (0.6, (-10, 0, 0)), (1.3, (0, 0, 0))])
        A.chain_wave(a, TAIL, (-3, 5, 0), period=0.65, phase_step=0.5)

    # look: glance left and right (idle action)
    a = T('look', 2.0)
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.3, (-14, 0, 0)), (1.6, (-14, 0, 0)), (2.0, (0, 0, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (-6, 40, 10)), (0.9, (-6, 40, 10)), (1.1, (-6, -40, -10)), (1.6, (-6, -40, -10)), (2.0, (0, 0, 0))])
    keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (0.3, (10, 0, 0)), (1.6, (10, 0, 0)), (2.0, (0, 0, 0))])

    # preen: nibble at the flank
    a = T('preen', 1.8)
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.35, (16, -48, 0)), (1.4, (16, -48, 0)), (1.8, (0, 0, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.35, (24, -42, -16)), (1.4, (24, -42, -16)), (1.8, (0, 0, 0))])
    A.sample(a, 'jaw', 'rotation', lambda t: (12 * max(0.0, math.sin(t * 22)) if 0.4 < t < 1.4 else 0, 0, 0), 36)
    keys(a, 'arm_r', 'rotation', [(0, (0, 0, 0)), (0.35, (-30, 0, 0)), (1.4, (-30, 0, 0)), (1.8, (0, 0, 0))])
    A.chain_wave(a, TAIL, (0, 10, 0), period=0.9, phase_step=0.6)

    # flinch
    a = T('flinch', 0.7)
    keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.1, (-12, 0, 6)), (0.35, (-4, 0, -3)), (0.7, (0, 0, 0))])
    keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.1, (0, -0.5, 2.5)), (0.7, (0, 0, 0))])
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.1, (-18, 16, 0)), (0.4, (6, -6, 0)), (0.7, (0, 0, 0))])
    keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.12, (36, 0, 0)), (0.5, (8, 0, 0)), (0.7, (0, 0, 0))])
    keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (0.12, (-18, 0, 0)), (0.7, (0, 0, 0))])

    # death: stagger and topple onto the side
    a = T('death', 1.5)
    keys(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.25, (-14, 0, -8)), (0.8, (2, 0, 82)), (1.0, (2, 0, 76)), (1.5, (2, 0, 78))])
    keys(a, 'body', 'position', [(0, (0, 0, 0)), (0.25, (0, -1, 1)), (0.8, (3, 7, 0)), (1.0, (3, 6.5, 0)), (1.5, (3, 6.5, 0))])
    keys(a, 'neck', 'rotation', [(0, (0, 0, 0)), (0.25, (-24, 0, 0)), (0.8, (30, 10, 0)), (1.5, (36, 14, 0))])
    keys(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.8, (10, 0, 0)), (1.5, (6, 4, 0))])
    keys(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.25, (38, 0, 0)), (0.9, (24, 0, 0)), (1.5, (22, 0, 0))])
    keys(a, 'crest', 'rotation', [(0, (0, 0, 0)), (1.0, (10, 0, 0)), (1.5, (10, 0, 0))])
    legs_keys(a, [(0, (0, 0, 0)), (0.3, (-10, 0, 0)), (0.9, (-30, 0, 0)), (1.5, (-34, 0, 0))],
              [(0, (0, 0, 0)), (0.3, (20, 0, 0)), (0.9, (10, 0, 0)), (1.5, (12, 0, 0))],
              [(0, (0, 0, 0)), (0.9, (20, 0, 0)), (1.5, (24, 0, 0))])
    for b in TAIL:
        keys(a, b, 'rotation', [(0, (0, 0, 0)), (0.8, (-2, 6, 0)), (1.5, (-4, 8, 0))])

    # sleep: crouched, legs folded, head resting on the ground
    a = T('sleep', 3.2, True)
    A.hold(a, 'body', 'position', (0, 6.5, 0))
    A.sine(a, 'body', 'rotation', (1.0, 0, 0), offset=(4, 0, 0))
    A.hold(a, 'neck', 'rotation', (40, -18, 0))
    A.hold(a, 'head', 'rotation', (-30, -14, -8))
    A.hold(a, 'jaw', 'rotation', (0, 0, 0))
    legs_hold(a, -60, 110, -50)
    for i, b in enumerate(TAIL):
        A.hold(a, b, 'rotation', (6 if i == 0 else -2, -16, 0))
    A.hold(a, 'arm_l', 'rotation', (-20, 0, 0))
    A.hold(a, 'arm_r', 'rotation', (-20, 0, 0))
    A.hold(a, 'crest', 'rotation', (16, 0, 0))

    # stun: wobbly, dizzy head circles
    a = T('stun', 1.2, True)
    A.sine(a, 'body', 'rotation', (0, 0, 6), period=1.2)
    A.hold(a, 'body', 'position', (0, 1.5, 0))
    A.sample(a, 'neck', 'rotation', lambda t: (14 + 8 * cyc(t, 1.2), 14 * cyc(t, 1.2, 1.57), 0), 16)
    A.sample(a, 'head', 'rotation', lambda t: (0, 0, 16 * cyc(t, 0.6)), 16)
    A.hold(a, 'jaw', 'rotation', (22, 0, 0))
    legs_hold(a, -8, 14, -4)
    A.chain_wave(a, TAIL, (2, 6, 0), period=1.2, phase_step=0.5)
    A.hold(a, 'crest', 'rotation', (12, 0, 0))

    # trapped (small monsters cannot be trapped by the core; kept so every required state pose exists): thrashing
    a = T('trapped', 0.8, True)
    A.hold(a, 'body', 'position', (0, 3, 0))
    A.sine(a, 'body', 'rotation', (4, 0, 6), period=0.4)
    A.sample(a, 'neck', 'rotation', lambda t: (-20 + 12 * cyc(t, 0.4), 18 * cyc(t, 0.8), 0), 12)
    A.sine(a, 'jaw', 'rotation', (12, 0, 0), period=0.2, offset=(16, 0, 0))
    legs_walk(a, 0.4, 30, 50, 20, steps=12)
    A.sine(a, 'arm_l', 'rotation', (30, 0, 0), period=0.3, offset=(-30, 0, 0))
    A.sine(a, 'arm_r', 'rotation', (30, 0, 0), period=0.3, phase=1.5, offset=(-30, 0, 0))
    A.chain_wave(a, TAIL, (6, 18, 0), period=0.4, phase_step=0.5)
