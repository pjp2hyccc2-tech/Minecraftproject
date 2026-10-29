"""Ignivald (炎翼竜イグニヴァルド) - crimson flying wyvern, fire element. Reference large monster."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from hcmodel import Model, anim as A  # noqa: E402
from hcmodel.parts import spike_plane, spike_row, tail_chain, leg, mirror_tree, head_eyes  # noqa: E402

m = Model('ignivald')
m.config.update({'head_bones': [['neck1', 0.25], ['neck2', 0.3], ['head', 0.45]], 'walk_speed': 0.9, 'run_speed': 1.7, 'walk_scale': 2.2})

# ------------------------------------------------------------------ body
body = m.bone('body', (0, -13, 6), (-8, 0, 0))
torso = body.cube((-17, -15, -40), (34, 30, 50), 'ign_scale', faces={'top': 'ign_dark'}, decals=[{'type': 'belly_band'}])
body.cube((-14, 6, -38), (28, 11, 32), 'ign_belly')                 # chest / belly plates
body.cube((-8, -18, -36), (16, 4, 42), 'ign_dark')                  # back ridge
body.cube((-17, -10, -30), (6, 16, 22), 'ign_plate', faces={'bottom': 'ign_belly'})   # shoulder armor L/R
body.cube((11, -10, -30), (6, 16, 22), 'ign_plate', faces={'bottom': 'ign_belly'})
spike_row(body, -32, 4, 6, -17, 11, 9, 'ign_spike', 'horn_ivory', shrink=0.35)

# ------------------------------------------------------------------ neck & head
neck1 = body.bone('neck1', (0, -7, -37), (-48, 0, 0))
neck1.cube((-9, -9, -20), (18, 18, 23), 'ign_scale', faces={'top': 'ign_dark'})
neck1.cube((-6, 5, -18), (12, 5, 20), 'ign_belly')
spike_row(neck1, -16, 0, 3, -9, 8, 7, 'ign_spike', 'horn_ivory', shrink=0.2)
neck2 = neck1.bone('neck2', (0, -1, -18), (14, 0, 0))
neck2.cube((-8, -8, -19), (16, 16, 21), 'ign_scale', faces={'top': 'ign_dark'})
neck2.cube((-5, 4, -17), (10, 5, 18), 'ign_belly')
spike_row(neck2, -15, -2, 2, -8, 7, 6, 'ign_spike', 'horn_ivory')

head = neck2.bone('head', (0, -1, -17), (42, 0, 0))
skull = head.cube((-11, -11, -19), (22, 16, 23), 'ign_plate')
head_eyes(skull, '#ffd84a', '#ffb020', at=(0.72, 0.42), size=4)
snout = head.cube((-8, -7, -37), (16, 11, 19), 'ign_plate', decals=[{'type': 'nostrils', 'at': 0.25}])
head.cube((-7.5, 4, -36), (15, 2, 18), 'teeth', decals=[{'type': 'teeth', 'face': 'west'}, {'type': 'teeth', 'face': 'east'}])
head.cube((-11, -13, -17), (22, 4, 11), 'ign_dark')                  # brow ridge
head.cube((-4, -12, -31), (8, 3, 12), 'ign_dark')                    # nasal crest
spike_row(head, -26, -4, 3, -12, 7, 6, 'ign_spike', 'horn_ivory', shrink=0.3)
horn_l = head.bone('horn_l', (7, -10, -6), (30, 22, 0))
horn_l.cube((-2, -2, 0), (4, 4, 20), 'horn_dark', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.4, 'mat': 'horn_ivory'}])
horn_l2 = horn_l.bone('horn_tip_l', (0, 0, 19), (-18, 0, 0))
horn_l2.cube((-1.5, -1.5, 0), (3, 3, 10), 'horn_ivory')
mirror_tree(head, horn_l)
cheek_l = head.bone('cheek_l', (10, -2, -12), (0, 25, 0))
cheek_l.cube((0, -4, 0), (0, 8, 12), 'ign_spike', decals=[{'type': 'cutout', 'shape': 'spike'}])
mirror_tree(head, cheek_l)
jaw = head.bone('jaw', (0, 4, -8), (0, 0, 0))
jaw.cube((-9, 0, -29), (18, 6, 30), 'ign_plate', faces={'top': 'mouth'})
jaw.cube((-7.5, -1, -28), (15, 1, 21), 'teeth')
jaw.cube((-4, 1, -22), (8, 1, 15), 'tongue')
jaw.cube((-3, 5, -24), (6, 3, 10), 'ign_dark')   # chin

# ------------------------------------------------------------------ tail
tail = tail_chain(body, 'tail', (0, -6, 8), (10, 0, 0),
                  [(22, 20, 20, 0), (18, 16, 18, 6), (14, 12, 17, 4), (11, 9, 16, -6), (8, 7, 14, -8)],
                  'ign_scale', spikes=('ign_spike', 8, 'horn_ivory'))
club = tail[-1].bone('tail_club', (0, 0, 14), (-6, 0, 0))
club.cube((-6, -6, 0), (12, 12, 14), 'ign_dark')
for ang, dx in ((0, 0),):
    pass
club_spikes = club.bone('club_spikes', (0, 0, 6), (0, 0, 0))
spike_plane(club_spikes, 0, -5, 0, 9, 12, 'ign_spike', 'lava_glow', 0.45)
cs_l = club.bone('club_spike_l', (6, 0, 6), (0, 0, 90))
spike_plane(cs_l, 0, 0, 0, 9, 11, 'ign_spike', 'lava_glow', 0.45)
cs_r = club.bone('club_spike_r', (-6, 0, 6), (0, 0, -90))
spike_plane(cs_r, 0, 0, 0, 9, 11, 'ign_spike', 'lava_glow', 0.45)
club.cube((-3, -3, 13), (6, 6, 8), 'ign_spike', decals=[{'type': 'tip', 'axis': 'back', 'frac': 0.5, 'mat': 'lava_glow'}])

# ------------------------------------------------------------------ legs
thigh_l, shin_l, foot_l = leg(body, 1, 'leg_l', (14, 6, 2), (13, 24, 18), (9, 21, 9), (13, 5, 16), 'ign_scale',
                              thigh_rot=(-22, 0, 0), shin_rot=(56, 0, 0), foot_rot=(-34, 0, 0))
mirror_tree(body, thigh_l)

# ------------------------------------------------------------------ wings (left built along +X; rest = folded)
FOLD = {'wing': (-38, -66, -54), 'forearm': (0, 165, 0), 'finger1': (0, 172, 0), 'finger2': (0, 160, 0), 'finger3': (0, 148, 0)}
SPREAD = {'wing': (0, 8, -12), 'forearm': (0, -16, 0), 'finger1': (0, -10, 0), 'finger2': (0, -40, 0), 'finger3': (0, -72, 0)}
wing_l = body.bone('wing_l', (16, -12, -27), FOLD['wing'])
wing_l.cube((0, -3.5, -3.5), (32, 7, 7), 'ign_scale', faces={'top': 'ign_dark'})
wing_l.cube((0, 0, 0), (32, 0, 40), 'ign_membrane', decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 1.5, 'depth': 0.35, 'taper': 0.15}])
fore_l = wing_l.bone('forearm_l', (32, 0, 0), FOLD['forearm'])
fore_l.cube((0, -2.5, -2.5), (38, 5, 5), 'ign_scale', faces={'top': 'ign_dark'})
fore_l.cube((36, -3, -3), (5, 6, 6), 'claw', decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'horn_ivory'}])
mem_fore = fore_l.bone('mem_fore_l', (0, 0, 0), (0, 0, 0))
mem_fore.cube((0, 0, 0), (38, 0, 30), 'ign_membrane', decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 2, 'depth': 0.25}])
for i, ln in enumerate((44, 38, 30)):
    fb = fore_l.bone('finger%d_l' % (i + 1), (38, 0, 0), FOLD['finger%d' % (i + 1)])
    fb.cube((0, -1.5, -1.5), (ln, 3, 3), 'ign_dark')
    mb = fb.bone('mem_f%d_l' % (i + 1), (0, 0, 0), (0, 0, 0))
    mb.cube((0, 0, 0), (ln, 0, 24 if i < 2 else 30), 'ign_membrane',
            decals=[{'type': 'cutout', 'shape': 'scallop', 'count': 2 if i < 2 else 1.5, 'depth': 0.45}])
mirror_tree(body, wing_l)
MEMS = ['mem_fore', 'mem_f1', 'mem_f2', 'mem_f3']


def woff(part, side, extra=(0, 0, 0)):
    """Animation offset taking a wing bone from its folded rest pose to SPREAD (+ extra)."""
    f, sp = FOLD[part], SPREAD[part]
    g = 1 if side == 'l' else -1
    return (sp[0] - f[0] + extra[0], g * (sp[1] - f[1] + extra[1]), g * (sp[2] - f[2] + extra[2]))


def fold_membranes(anim, amount=0.88):
    """Collapse the outer membranes (folded wings). Use in every ground loop / state pose."""
    for side in ('l', 'r'):
        for mm in MEMS:
            A.hold(anim, '%s_%s' % (mm, side), 'scale', (1, 1, 1 - amount))


# ------------------------------------------------------------------ animations
T = m.anim
TAIL = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5']


def legs(anim, period, amp_thigh, amp_shin, amp_foot, phase=0.0, steps=16):
    for side, ph in (('l', 0), ('r', math.pi)):
        A.sine(anim, 'leg_%s_thigh' % side, 'rotation', (amp_thigh, 0, 0), period, ph + phase, steps=steps)
        A.sine(anim, 'leg_%s_shin' % side, 'rotation', (amp_shin, 0, 0), period, ph + phase - 1.0, offset=(amp_shin * 0.3, 0, 0), steps=steps)
        A.sine(anim, 'leg_%s_foot' % side, 'rotation', (amp_foot, 0, 0), period, ph + phase - 1.8, steps=steps)


def wings_spread(anim, keys_extra, fore_extra=None, unfold=True):
    """keys_extra: list of (t, (dx,dy,dz)) extra rotation on top of the spread pose for the left wing."""
    for side in ('l', 'r'):
        A.poses(anim, 'wing_' + side, 'rotation', [(t, woff('wing', side, e)) for t, e in keys_extra])
        fk = fore_extra or [(keys_extra[0][0], (0, 0, 0)), (keys_extra[-1][0], (0, 0, 0))]
        A.poses(anim, 'forearm_' + side, 'rotation', [(t, woff('forearm', side, e)) for t, e in fk])
        for i in range(1, 4):
            A.hold(anim, 'finger%d_%s' % (i, side), 'rotation', woff('finger%d' % i, side))
        if unfold:
            for mm in MEMS:
                A.hold(anim, '%s_%s' % (mm, side), 'scale', (1, 1, 1.88))  # cancels idle fold (0.12 -> 1.0)


def wings_spread_keys(anim, times_spread, unfold_scale=True):
    """Spread then fold back: times_spread = (t_open, t_hold_end, t_close, t_end)."""
    t0, t1, t2, t3 = times_spread
    for side in ('l', 'r'):
        A.poses(anim, 'wing_' + side, 'rotation', [(0, (0, 0, 0)), (t0, woff('wing', side, (-10, 10, -30))), (t1, woff('wing', side, (-6, 6, -24))), (t2, (0, 0, 0)), (t3, (0, 0, 0))])
        A.poses(anim, 'forearm_' + side, 'rotation', [(0, (0, 0, 0)), (t0, woff('forearm', side, (0, 10, 0))), (t1, woff('forearm', side, (0, 6, 0))), (t2, (0, 0, 0)), (t3, (0, 0, 0))])
        for i in range(1, 4):
            fo = woff('finger%d' % i, side)
            A.poses(anim, 'finger%d_%s' % (i, side), 'rotation', [(0, (0, 0, 0)), (t0, fo), (t1, fo), (t2, (0, 0, 0)), (t3, (0, 0, 0))])
        if unfold_scale:
            for mm in MEMS:
                A.poses(anim, '%s_%s' % (mm, side), 'scale', [(0, (1, 1, 1)), (t0, (1, 1, 1.88)), (t1, (1, 1, 1.88)), (t2, (1, 1, 1)), (t3, (1, 1, 1))])


# idle: breathing, tail and neck sway, wings folded
a = T('idle', 3.0, True)
A.sine(a, 'body', 'position', (0, 0.6, 0))
A.sine(a, 'body', 'rotation', (0.8, 0, 0))
A.sine(a, 'neck1', 'rotation', (2.5, 3, 0), phase=0.6)
A.sine(a, 'head', 'rotation', (-2, 4, 0), phase=1.2)
A.sine(a, 'jaw', 'rotation', (2, 0, 0), offset=(2, 0, 0), period=1.5)
A.chain_wave(a, TAIL, (0, 5, 0), phase_step=0.7, growth=1.15)
A.sine(a, 'wing_l', 'rotation', (0, 0, 2), phase=0.3)
A.sine(a, 'wing_r', 'rotation', (0, 0, -2), phase=0.3)
fold_membranes(a)

a = T('walk', 1.6, True)
legs(a, 1.6, 26, 22, 18)
A.sine(a, 'body', 'position', (0, 1.2, 0), period=0.8)
A.sine(a, 'body', 'rotation', (1.5, 0, 3), period=1.6)
A.sine(a, 'neck1', 'rotation', (-3, -3, 0), period=0.8, phase=0.8)
A.chain_wave(a, TAIL, (0, 7, 0), period=1.6, phase_step=0.6, growth=1.1)
A.sine(a, 'wing_l', 'rotation', (0, 0, 4), period=1.6)
A.sine(a, 'wing_r', 'rotation', (0, 0, -4), period=1.6)

a = T('run', 0.9, True)
legs(a, 0.9, 40, 34, 26, steps=12)
A.sine(a, 'body', 'position', (0, 2.2, 0), period=0.45)
A.hold(a, 'body', 'rotation', (6, 0, 0))
A.hold(a, 'neck1', 'rotation', (22, 0, 0))
A.hold(a, 'head', 'rotation', (-16, 0, 0))
A.chain_wave(a, TAIL, (0, 9, 0), period=0.9, phase_step=0.5)

# fly: wings spread and beating, legs tucked (idle is NOT applied while flying)
a = T('fly', 1.2, True)
for side in ('l', 'r'):
    g = 1 if side == 'l' else -1
    A.sample(a, 'wing_' + side, 'rotation', lambda t, side=side: woff('wing', side, (0, 0, -34 * math.sin(2 * math.pi * t / 1.2))), steps=12)
    A.sample(a, 'forearm_' + side, 'rotation', lambda t, side=side: woff('forearm', side, (0, 8 * math.sin(2 * math.pi * t / 1.2 - 0.6), -12 * math.sin(2 * math.pi * t / 1.2 - 0.6))), steps=12)
    for i in range(3):
        A.sample(a, 'finger%d_%s' % (i + 1, side), 'rotation', lambda t, side=side, i=i: woff('finger%d' % (i + 1), side, (0, 0, -8 * math.sin(2 * math.pi * t / 1.2 - 1.0))), steps=12)
    A.hold(a, 'leg_%s_thigh' % side, 'rotation', (50, 0, 0))
    A.hold(a, 'leg_%s_shin' % side, 'rotation', (-40, 0, 0))
    A.hold(a, 'leg_%s_foot' % side, 'rotation', (50, 0, 0))
A.sine(a, 'body', 'position', (0, -3, 0), period=1.2, phase=math.pi)
A.hold(a, 'body', 'rotation', (8, 0, 0))
A.hold(a, 'neck1', 'rotation', (30, 0, 0))
A.hold(a, 'head', 'rotation', (-24, 0, 0))
A.chain_wave(a, TAIL, (4, 3, 0), period=1.2, phase_step=0.6)

# roar: rear up, head high, jaw wide, wings flare
a = T('roar', 2.2)
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (-14, 0, 0)), (0.6, (-18, 0, 0)), (1.7, (-17, 0, 0)), (2.2, (0, 0, 0))])
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.4, (0, -3, 3)), (1.7, (0, -3, 3)), (2.2, (0, 0, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.6, (-12, 0, 0)), (0.9, (-10, -6, 0)), (1.3, (-12, 6, 0)), (1.7, (-10, 0, 0)), (2.2, (0, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (-28, 0, 0)), (1.7, (-26, 0, 0)), (2.2, (0, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.5, (8, 0, 0)), (0.65, (46, 0, 0)), (1.6, (42, 0, 0)), (2.0, (0, 0, 0))])
wings_spread_keys(a, (0.6, 1.7, 2.15, 2.2))
A.chain_wave(a, TAIL, (-4, 8, 0), period=0.55, phase_step=0.5)

# bite: draw back, lunge, snap
a = T('bite', 1.2)
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.3, (0, 0, 4)), (0.5, (0, 1, -10)), (0.8, (0, 0, -6)), (1.2, (0, 0, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.3, (-12, 0, 0)), (0.5, (34, 0, 0)), (0.8, (28, 0, 0)), (1.2, (0, 0, 0))])
A.poses(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (0.3, (-8, 0, 0)), (0.5, (12, 0, 0)), (1.2, (0, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-10, 0, 0)), (0.5, (-22, 0, 0)), (1.2, (0, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.32, (40, 0, 0)), (0.5, (2, 0, 0)), (0.6, (6, 0, 0)), (1.2, (0, 0, 0))])

# tail_spin: full spin with the tail whipping out
a = T('tail_spin', 1.6)
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.35, (0, 25, 0)), (0.5, (0, 0, 0), 'l'), (1.0, (0, -300, 0), 'l'), (1.2, (0, -360, 0)), (1.6, (0, -360, 0))])
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.35, (0, 2, 0)), (1.0, (0, 0, 0)), (1.6, (0, 0, 0))])
for i, b in enumerate(TAIL):
    A.poses(a, b, 'rotation', [(0, (0, 0, 0)), (0.35, (0, -10, 0)), (0.6, (-6, 16, 0)), (1.0, (-6, 14, 0)), (1.3, (0, -6, 0)), (1.6, (0, 0, 0))])

# charge: head low, running pose (movement is driven by code)
a = T('charge', 2.4)
legs(a, 0.6, 42, 34, 26, steps=24)
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.3, (10, 0, 0)), (2.0, (10, 0, 0)), (2.4, (0, 0, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.3, (40, 0, 0)), (2.0, (38, 0, 0)), (2.4, (0, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.3, (-30, 0, 0)), (2.0, (-28, 0, 0)), (2.4, (0, 0, 0))])
wings_spread_keys(a, (0.4, 2.0, 2.35, 2.4))

# fireball: rear head, then thrust with jaw open
def breath_pose(anim, t_wind, t_fire, t_end, neck_back=-16, neck_fwd=30):
    A.poses(anim, 'body', 'rotation', [(0, (0, 0, 0)), (t_wind, (-6, 0, 0)), (t_fire, (6, 0, 0)), (t_end, (0, 0, 0))])
    A.poses(anim, 'body', 'position', [(0, (0, 0, 0)), (t_wind, (0, 0, 4)), (t_fire, (0, 0, -4)), (t_end, (0, 0, 0))])
    A.poses(anim, 'neck1', 'rotation', [(0, (0, 0, 0)), (t_wind, (neck_back, 0, 0)), (t_fire, (neck_fwd, 0, 0)), (t_fire + 0.3, (neck_fwd - 4, 0, 0)), (t_end, (0, 0, 0))])
    A.poses(anim, 'head', 'rotation', [(0, (0, 0, 0)), (t_wind, (-14, 0, 0)), (t_fire, (-22, 0, 0)), (t_end, (0, 0, 0))])
    A.poses(anim, 'jaw', 'rotation', [(0, (0, 0, 0)), (t_wind, (10, 0, 0)), (t_fire, (44, 0, 0)), (t_fire + 0.35, (38, 0, 0)), (t_end, (0, 0, 0))])

a = T('fireball', 1.5)
breath_pose(a, 0.55, 0.75, 1.5)

a = T('fireball_triple', 2.2)
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.4, (-16, 0, 0)), (0.6, (26, 18, 0)), (0.9, (6, 0, 0)), (1.1, (26, 0, 0)), (1.4, (6, 0, 0)), (1.6, (26, -18, 0)), (2.2, (0, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.6, (-20, 0, 0)), (1.6, (-20, 0, 0)), (2.2, (0, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (40, 0, 0)), (0.85, (8, 0, 0)), (1.1, (40, 0, 0)), (1.35, (8, 0, 0)), (1.6, (40, 0, 0)), (2.0, (0, 0, 0))])
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (-6, 0, 0)), (1.8, (2, 0, 0)), (2.2, (0, 0, 0))])

a = T('breath_sweep', 2.8)
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.6, (-12, 40, 0)), (0.9, (24, 38, 0)), (2.1, (24, -38, 0)), (2.8, (0, 0, 0))])
A.poses(a, 'neck2', 'rotation', [(0, (0, 0, 0)), (0.9, (6, 14, 0)), (2.1, (6, -14, 0)), (2.8, (0, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (0.9, (-20, 0, 0)), (2.1, (-20, 0, 0)), (2.8, (0, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.6, (10, 0, 0)), (0.9, (44, 0, 0)), (2.1, (44, 0, 0)), (2.5, (0, 0, 0))])
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.9, (2, 8, 0)), (2.1, (2, -8, 0)), (2.8, (0, 0, 0))])

# takeoff / land (played on the ground -> idle fold is active, so unfold explicitly)
a = T('takeoff', 1.6)
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.4, (0, 5, 0)), (0.7, (0, -6, 0)), (1.6, (0, -10, 0))])
wings_spread(a, [(0, (0, 0, 0)), (0.4, (0, 0, 20)), (0.7, (0, 0, -40)), (1.0, (0, 0, 20)), (1.3, (0, 0, -36)), (1.6, (0, 0, 0))])
for side in ('l', 'r'):
    A.poses(a, 'leg_%s_thigh' % side, 'rotation', [(0, (0, 0, 0)), (0.4, (-20, 0, 0)), (0.8, (30, 0, 0)), (1.6, (50, 0, 0))])
    A.poses(a, 'leg_%s_shin' % side, 'rotation', [(0, (0, 0, 0)), (0.4, (30, 0, 0)), (1.6, (-40, 0, 0))])
a = T('land', 1.2)
A.poses(a, 'body', 'position', [(0, (0, -8, 0)), (0.6, (0, 3, 0)), (1.2, (0, 0, 0))])
for side in ('l', 'r'):
    A.poses(a, 'wing_' + side, 'rotation', [(0, woff('wing', side, (0, 0, -20))), (0.4, woff('wing', side, (0, 0, -40))), (0.9, woff('wing', side, (0, 0, 0))), (1.2, (0, 0, 0))])
    A.poses(a, 'forearm_' + side, 'rotation', [(0, woff('forearm', side)), (0.9, woff('forearm', side)), (1.2, (0, 0, 0))])
    for i in range(1, 4):
        fo = woff('finger%d' % i, side)
        A.poses(a, 'finger%d_%s' % (i, side), 'rotation', [(0, fo), (0.9, fo), (1.2, (0, 0, 0))])
    for mm in MEMS:
        A.poses(a, '%s_%s' % (mm, side), 'scale', [(0, (1, 1, 1.88)), (0.9, (1, 1, 1.88)), (1.2, (1, 1, 1))])

# airborne attacks (flying: idle not applied -> offsets relative to folded rest; use woff for spread)
a = T('fly_fireball', 1.4)
A.poses(a, 'body', 'rotation', [(0, (8, 0, 0)), (0.5, (-10, 0, 0)), (0.75, (20, 0, 0)), (1.4, (8, 0, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (30, 0, 0)), (0.5, (0, 0, 0)), (0.75, (46, 0, 0)), (1.4, (30, 0, 0))])
A.poses(a, 'head', 'rotation', [(0, (-24, 0, 0)), (0.75, (-30, 0, 0)), (1.4, (-24, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.55, (12, 0, 0)), (0.75, (44, 0, 0)), (1.1, (30, 0, 0)), (1.4, (0, 0, 0))])
for side in ('l', 'r'):
    A.sample(a, 'wing_' + side, 'rotation', lambda t, side=side: woff('wing', side, (0, 0, -30 * math.sin(2 * math.pi * t / 0.7))), steps=14)
    A.hold(a, 'forearm_' + side, 'rotation', woff('forearm', side))
    for i in range(1, 4):
        A.hold(a, 'finger%d_%s' % (i, side), 'rotation', woff('finger%d' % i, side))
    A.hold(a, 'leg_%s_thigh' % side, 'rotation', (50, 0, 0))
    A.hold(a, 'leg_%s_shin' % side, 'rotation', (-40, 0, 0))

a = T('dive', 2.0)
A.poses(a, 'body', 'rotation', [(0, (8, 0, 0)), (0.5, (-20, 0, 0)), (0.8, (28, 0, 0)), (1.5, (24, 0, 0)), (2.0, (8, 0, 0))])
for side in ('l', 'r'):
    A.poses(a, 'wing_' + side, 'rotation', [(0, woff('wing', side)), (0.5, woff('wing', side, (0, 0, -40))), (0.8, woff('wing', side, (10, -40, 20))), (1.5, woff('wing', side, (10, -40, 20))), (2.0, woff('wing', side))])
    A.hold(a, 'forearm_' + side, 'rotation', woff('forearm', side))
    for i in range(1, 4):
        A.hold(a, 'finger%d_%s' % (i, side), 'rotation', woff('finger%d' % i, side))
    A.poses(a, 'leg_%s_thigh' % side, 'rotation', [(0, (50, 0, 0)), (0.7, (-40, 0, 0)), (1.5, (-50, 0, 0)), (2.0, (50, 0, 0))])
    A.poses(a, 'leg_%s_foot' % side, 'rotation', [(0, (50, 0, 0)), (0.7, (-30, 0, 0)), (1.5, (-30, 0, 0)), (2.0, (50, 0, 0))])

a = T('wing_gust', 1.6)
for side in ('l', 'r'):
    A.poses(a, 'wing_' + side, 'rotation', [(0, woff('wing', side)), (0.4, woff('wing', side, (-14, -10, -50))), (0.7, woff('wing', side, (10, 10, 40))), (1.0, woff('wing', side, (-14, -10, -50))), (1.3, woff('wing', side, (10, 10, 40))), (1.6, woff('wing', side))])
    A.hold(a, 'forearm_' + side, 'rotation', woff('forearm', side))
    for i in range(1, 4):
        A.hold(a, 'finger%d_%s' % (i, side), 'rotation', woff('finger%d' % i, side))
    A.hold(a, 'leg_%s_thigh' % side, 'rotation', (50, 0, 0))
A.poses(a, 'body', 'rotation', [(0, (8, 0, 0)), (0.4, (-14, 0, 0)), (1.3, (-12, 0, 0)), (1.6, (8, 0, 0))])

# reactions
a = T('flinch', 1.2)
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.15, (-8, 0, 10)), (0.5, (-4, 0, -6)), (1.2, (0, 0, 0))])
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.15, (2, -2, 4)), (1.2, (0, 0, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.15, (-20, 20, 0)), (0.6, (10, -10, 0)), (1.2, (0, 0, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.2, (36, 0, 0)), (0.8, (10, 0, 0)), (1.2, (0, 0, 0))])

a = T('death', 2.0)
A.poses(a, 'body', 'rotation', [(0, (0, 0, 0)), (0.4, (-12, 0, -8)), (1.2, (6, 0, 76)), (1.5, (6, 0, 70)), (2.0, (6, 0, 72))])
A.poses(a, 'body', 'position', [(0, (0, 0, 0)), (0.4, (0, -3, 0)), (1.2, (4, 14, 0)), (2.0, (4, 13, 0))])
A.poses(a, 'neck1', 'rotation', [(0, (0, 0, 0)), (0.4, (-20, 0, 0)), (1.2, (36, 20, 0)), (2.0, (42, 24, 0))])
A.poses(a, 'head', 'rotation', [(0, (0, 0, 0)), (1.2, (10, 10, 0)), (2.0, (14, 12, 0))])
A.poses(a, 'jaw', 'rotation', [(0, (0, 0, 0)), (0.4, (40, 0, 0)), (1.4, (22, 0, 0)), (2.0, (18, 0, 0))])
for b in TAIL:
    A.poses(a, b, 'rotation', [(0, (0, 0, 0)), (1.2, (4, 8, 0)), (2.0, (2, 10, 0))])
fold_membranes(a)

a = T('sleep', 4.0, True)
A.hold(a, 'body', 'position', (0, 12, 0))
A.sine(a, 'body', 'rotation', (1.2, 0, 0), offset=(4, 0, 0))
A.hold(a, 'neck1', 'rotation', (56, -30, 0))
A.hold(a, 'neck2', 'rotation', (10, -20, 0))
A.hold(a, 'head', 'rotation', (-40, -10, 0))
for side in ('l', 'r'):
    A.hold(a, 'leg_%s_thigh' % side, 'rotation', (-40, 0, 0))
    A.hold(a, 'leg_%s_shin' % side, 'rotation', (40, 0, 0))
for b in TAIL:
    A.hold(a, b, 'rotation', (-2, -14, 0))
fold_membranes(a)

a = T('trapped', 1.0, True)
A.hold(a, 'body', 'position', (0, 9, 0))
A.sine(a, 'body', 'rotation', (6, 4, 8), period=0.5)
A.sine(a, 'neck1', 'rotation', (-10, 18, 0), period=0.5, phase=1.0, offset=(-8, 0, 0))
A.sine(a, 'jaw', 'rotation', (16, 0, 0), period=0.25, offset=(20, 0, 0))
for side in ('l', 'r'):
    g = 1 if side == 'l' else -1
    A.sine(a, 'wing_' + side, 'rotation', (0, 0, -g * 18), period=0.5)
A.chain_wave(a, TAIL, (8, 14, 0), period=0.5, phase_step=0.6)
fold_membranes(a)

a = T('stun', 1.6, True)
A.sine(a, 'body', 'rotation', (0, 0, 7), period=1.6)
A.hold(a, 'body', 'position', (0, 3, 0))
A.sine(a, 'neck1', 'rotation', (6, 22, 0), period=1.6, phase=0.5, offset=(30, 0, 0))
A.sine(a, 'head', 'rotation', (0, 0, 14), period=0.8, offset=(-10, 0, 0))
A.hold(a, 'jaw', 'rotation', (24, 0, 0))
fold_membranes(a)

if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft')
    img, glow = m.export(root)
    print('texture', m.tex_w, m.tex_h, 'cubes', sum(1 for _ in m.all_cubes()), 'bones', len(m.bones), 'anims', len(m.animations))
    if '--preview' in sys.argv:
        from hcmodel.render import preview
        sel = [x for x in sys.argv[1:] if not x.startswith('--')]
        preview(m, img, os.path.join(os.path.dirname(__file__), '..', 'preview', 'ignivald.png'), anims=sel or None, size=260)
