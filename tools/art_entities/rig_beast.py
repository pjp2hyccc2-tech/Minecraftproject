"""BEAST enemy rig (quadruped): wolf, snow_wolf, cave_rat, frost_hound, abyss_hound, salamander, scorpion.
Wolf-sized base; the scorpion adds pincers, four extra legs and a curled stinger tail (with its own attacks)."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'attack1', 'attack2', 'cast', 'hurt', 'spawn', 'death']


def leg(name, x, z, front, tag=None, share=None, mirror=False):
    upper = C(x - 1, 15, z - 1, 2, 5, 2, 'fur:leg', tag=tag and tag + 'u', share=share and share + 'u', mirror=mirror)
    lower = C(x - 1, 20, z - 1, 2, 4, 2, 'fur:paw', tag=tag and tag + 'l', share=share and share + 'l', mirror=mirror)
    return P(name, (x, 16, z), upper, P(name.replace('_leg', '_paw'), (x, 20, z), lower))


def geometry():
    head = P('head', (0, 13, -4),
             C(-3, 9.5, -9, 6, 6, 5, 'fur:head'),
             P('snout', (0, 13, -9), C(-1.5, 12.5, -12, 3, 3, 3, 'fur:snout'), opt=True),
             P('jaw', (0, 15, -8), C(-1.5, 15, -12, 3, 1, 4, 'fur:jaw')),
             P('ear_r', (-2, 9.5, -6), C(-3, 7.5, -6.5, 2, 2, 1, 'fur:ear', tag='ear'), rot=(0, 0, -10), opt=True),
             P('ear_l', (2, 9.5, -6), C(1, 7.5, -6.5, 2, 2, 1, 'fur:ear', share='ear', mirror=True), rot=(0, 0, 10), opt=True),
             P('horn_r', (-2.5, 10, -6), C(-3, 6, -6, 1, 4, 1, 'bone:horn', tag='horn'), rot=(-25, 0, -15), opt=True),
             P('horn_l', (2.5, 10, -6), C(2, 6, -6, 1, 4, 1, 'bone:horn', share='horn', mirror=True), rot=(-25, 0, 15), opt=True),
             P('lizard_snout', (0, 13, -9), C(-2.5, 12, -13, 5, 2, 4, 'scale:snout'), opt=True))
    tail = P('tail', (0, 12.5, 7), C(-1, 11.5, 7, 2, 2, 6, 'fur:tail'),
             P('tail2', (0, 12.5, 13), C(-1, 11.5, 13, 2, 2, 5, 'fur:tail2'), rot=(-12, 0, 0)), rot=(-40, 0, 0), opt=True)
    sting = P('sting1', (0, 11, 6), C(-1, 10, 6, 2, 2, 4, 'chitin:tail', tag='seg'),
              P('sting2', (0, 11, 10), C(-1, 10, 10, 2, 2, 4, 'chitin:tail', share='seg'),
                P('sting3', (0, 11, 14), C(-1, 10, 14, 2, 2, 4, 'chitin:tail', share='seg'),
                  P('stinger', (0, 11, 18), C(-1.5, 9.5, 18, 3, 3, 3, 'chitin:sting'), C(-0.5, 10.5, 21, 1, 1, 2, 'bone:barb'),
                    rot=(45, 0, 0)), rot=(45, 0, 0)), rot=(45, 0, 0)), rot=(50, 0, 0), opt=True)
    pincer_r = P('pincer_r', (-3, 14, -4), C(-4, 13.5, -9, 2, 2, 5, 'chitin:arm', tag='parm'),
                 P('claw_r', (-3, 14.5, -9), C(-4.5, 13, -13, 3, 3, 4, 'chitin:claw', tag='pclaw')), rot=(0, 25, 0), opt=True)
    pincer_l = P('pincer_l', (3, 14, -4), C(2, 13.5, -9, 2, 2, 5, 'chitin:arm', share='parm', mirror=True),
                 P('claw_l', (3, 14.5, -9), C(1.5, 13, -13, 3, 3, 4, 'chitin:claw', share='pclaw', mirror=True)),
                 rot=(0, -25, 0), opt=True)
    mids = []
    for side, sx in (('r', -1), ('l', 1)):
        for i, z in enumerate((0.5, 3.5)):
            x0 = -9 if sx < 0 else 3
            mids.append(P('mid_leg_%s%d' % (side, i + 1), (3 * sx, 15, z),
                          C(x0, 14.5, z - 0.5, 6, 1, 1, 'chitin:leg', tag=None if (side, i) != ('r', 0) else 'midleg',
                            share=None if (side, i) == ('r', 0) else 'midleg', mirror=sx > 0),
                          rot=(0, (-12 if i == 0 else 12) * sx, -35 * sx * -1), opt=True))
    torso = P('torso', (0, 14, 1),
              C(-3, 11, -4, 6, 6, 11, 'fur:body'),
              head,
              P('mane', (0, 14, -3), C(-3.5, 10, -5, 7, 7, 5, 'fur:mane'), opt=True),
              P('spines', (0, 11, 1), C(-0.5, 7, -3, 1, 4, 2, 'crystal:spine'), C(-0.5, 8, 0.5, 1, 3, 2, 'crystal:spine'),
                C(-0.5, 9, 4, 1, 2, 2, 'crystal:spine'), opt=True),
              P('crest', (0, 11, 1), C(-0.5, 9, -3, 1, 2, 10, 'scale:crest'), opt=True),
              P('carapace', (0, 11, 1), C(-3.5, 10.5, -4.5, 7, 2, 12, 'chitin:shell'), opt=True),
              leg('front_right_leg', -2, -2.5, True, tag='fl'),
              leg('front_left_leg', 2, -2.5, True, share='fl', mirror=True),
              leg('hind_right_leg', -2, 5, False, tag='hl'),
              leg('hind_left_leg', 2, 5, False, share='hl', mirror=True),
              tail, sting, pincer_r, pincer_l, *mids)
    return [P('base', (0, 24, 0), torso)]


def animations():
    A = []
    A.append(anim('idle', 2.4, loop=True,
                  torso=dict(scale=[(0, 1, 1, 1), (1.2, 1.02, 1.04, 1.0), (2.4, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 3, 8, 0), (1.2, 0, 0, 0), (1.8, -2, -8, 0), (2.4, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 4, 0, 0), (0.3, 9, 0, 0), (0.6, 4, 0, 0), (0.9, 9, 0, 0), (1.2, 4, 0, 0),
                                (1.5, 9, 0, 0), (1.8, 4, 0, 0), (2.1, 9, 0, 0), (2.4, 4, 0, 0)]),
                  tail=dict(rot=[(0, 0, -14, 0), (0.6, 0, 14, 0), (1.2, 0, -14, 0), (1.8, 0, 14, 0), (2.4, 0, -14, 0)]),
                  tail2=dict(rot=[(0, 0, -8, 0), (0.6, 0, 10, 0), (1.2, 0, -8, 0), (1.8, 0, 10, 0), (2.4, 0, -8, 0)]),
                  ear_r=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.1, 0, 0, -20), (1.25, 0, 0, 0), (2.4, 0, 0, 0)]),
                  ear_l=dict(rot=[(0, 0, 0, 0), (1.6, 0, 0, 0), (1.7, 0, 0, 20), (1.85, 0, 0, 0), (2.4, 0, 0, 0)]),
                  sting1=dict(rot=[(0, 0, 0, 0), (1.2, -6, 4, 0), (2.4, 0, 0, 0)]),
                  stinger=dict(rot=[(0, 0, 0, 0), (1.2, 8, -6, 0), (2.4, 0, 0, 0)]),
                  pincer_r=dict(rot=[(0, 0, 0, 0), (1.2, 0, 6, 0), (2.4, 0, 0, 0)]),
                  pincer_l=dict(rot=[(0, 0, 0, 0), (1.2, 0, -6, 0), (2.4, 0, 0, 0)])))
    A.append(anim('walk', 0.8, loop=True,
                  front_right_leg=dict(rot=[(0, 32, 0, 0), (0.4, -32, 0, 0), (0.8, 32, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 32, 0, 0), (0.4, -32, 0, 0), (0.8, 32, 0, 0)]),
                  front_left_leg=dict(rot=[(0, -32, 0, 0), (0.4, 32, 0, 0), (0.8, -32, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, -32, 0, 0), (0.4, 32, 0, 0), (0.8, -32, 0, 0)]),
                  front_right_paw=dict(rot=[(0, 30, 0, 0), (0.2, 40, 0, 0), (0.4, 0, 0, 0), (0.8, 30, 0, 0)]),
                  hind_left_paw=dict(rot=[(0, 30, 0, 0), (0.2, 40, 0, 0), (0.4, 0, 0, 0), (0.8, 30, 0, 0)]),
                  front_left_paw=dict(rot=[(0, 0, 0, 0), (0.4, 30, 0, 0), (0.6, 40, 0, 0), (0.8, 0, 0, 0)]),
                  hind_right_paw=dict(rot=[(0, 0, 0, 0), (0.4, 30, 0, 0), (0.6, 40, 0, 0), (0.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, 0.6, 0), (0.4, 0, 0, 0), (0.6, 0, 0.6, 0), (0.8, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 2), (0.4, 0, 0, -2), (0.8, 0, 0, 2)]),
                  head=dict(rot=[(0, 3, 0, 0), (0.2, -2, 0, 0), (0.4, 3, 0, 0), (0.6, -2, 0, 0), (0.8, 3, 0, 0)]),
                  tail=dict(rot=[(0, 8, 12, 0), (0.4, 8, -12, 0), (0.8, 8, 12, 0)]),
                  mid_leg_r1=dict(rot=[(0, 0, 18, 0), (0.4, 0, -18, 0), (0.8, 0, 18, 0)]),
                  mid_leg_r2=dict(rot=[(0, 0, -18, 0), (0.4, 0, 18, 0), (0.8, 0, -18, 0)]),
                  mid_leg_l1=dict(rot=[(0, 0, -18, 0), (0.4, 0, 18, 0), (0.8, 0, -18, 0)]),
                  mid_leg_l2=dict(rot=[(0, 0, 18, 0), (0.4, 0, -18, 0), (0.8, 0, 18, 0)]),
                  sting1=dict(rot=[(0, 0, 6, 0), (0.4, 0, -6, 0), (0.8, 0, 6, 0)])))
    A.append(anim('attack1', 0.7, impact=0.35,
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, -0.8, 1.2), (0.35, 0, 0.6, -3.2), (0.7, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.2, 10, 0, 0), (0.35, -10, 0, 0), (0.7, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, 18, 0, 0), (0.35, -12, 0, 0), (0.5, -4, 0, 0), (0.7, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.25, 45, 0, 0), (0.35, 0, 0, 0), (0.45, 12, 0, 0), (0.7, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.2, 20, 0, 0), (0.35, -45, 0, 0), (0.7, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.2, 20, 0, 0), (0.35, -40, 0, 0), (0.7, 0, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.2, -25, 0, 0), (0.35, 35, 0, 0), (0.7, 0, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.2, -25, 0, 0), (0.35, 35, 0, 0), (0.7, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, 0, 0), (0.35, 25, 0, 0), (0.7, 0, 0, 0)])))
    A.append(anim('attack2', 0.9, impact=0.5,
                  base=dict(pos=[(0, 0, 0, 0), (0.3, 0, 2.5, 0.5), (0.5, 0, -0.5, -2.5), (0.9, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.3, -38, 0, 0), (0.5, 12, 0, 0), (0.9, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -15, 0, 0), (0.5, 22, 0, 0), (0.9, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.3, 30, 0, 0), (0.5, 40, 0, 0), (0.6, 5, 0, 0), (0.9, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.3, -95, 0, 10), (0.5, -15, 0, -5), (0.9, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.3, -70, 0, -10), (0.5, -10, 0, 0), (0.9, 0, 0, 0)]),
                  front_right_paw=dict(rot=[(0, 0, 0, 0), (0.3, 40, 0, 0), (0.5, -10, 0, 0), (0.9, 0, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.3, 30, 0, 0), (0.5, 10, 0, 0), (0.9, 0, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.3, 30, 0, 0), (0.5, 10, 0, 0), (0.9, 0, 0, 0)])))
    A.append(anim('cast', 1.4, impact=0.8,
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -50, 0, 0), (1.0, -55, 0, 0), (1.4, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.5, 35, 0, 0), (1.0, 35, 0, 0), (1.4, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.5, -14, 0, 0), (1.0, -14, 0, 0), (1.4, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, 0, 0), (0.5, -20, 0, 0), (1.4, 0, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.5, 15, 0, 0), (1.4, 0, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.5, 15, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('hurt', 0.35,
                  torso=dict(rot=[(0, 0, 0, 0), (0.07, -8, 0, 10), (0.35, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.07, -18, 0, 0), (0.35, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.07, 0, 0, 1.0), (0.35, 0, 0, 0)])))
    A.append(anim('spawn', 1.0,
                  base=dict(pos=[(0, 0, -14, 0), (0.5, 0, -2, -1), (0.75, 0, 0.8, -0.5), (1.0, 0, 0, 0)]),
                  torso=dict(rot=[(0, -35, 0, 0), (0.6, -10, 0, 0), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, -20, 0, 0), (0.7, 10, 0, 0), (1.0, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, -60, 0, 0), (0.6, -20, 0, 0), (1.0, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, -40, 0, 0), (0.6, -30, 0, 0), (1.0, 0, 0, 0)])))
    A.append(anim('death', 1.4,
                  base=dict(rot=[(0, 0, 0, 0), (0.5, 0, 0, 12), (0.9, 0, 0, 88), (1.05, 0, 0, 80), (1.4, 0, 0, 86)],
                            pos=[(0, 0, 0, 0), (0.5, 0, -1, 0), (0.9, 0, -1.5, 0), (1.4, 0, -2, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -25, 0, 0), (0.9, 15, 0, 0), (1.4, 22, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.3, 35, 0, 0), (1.4, 18, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.5, -30, 0, 0), (1.4, 25, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.5, -20, 0, 0), (1.4, 35, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.5, 20, 0, 0), (1.4, -20, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.5, 25, 0, 0), (1.4, -10, 0, 0)]),
                  tail=dict(rot=[(0, 0, 0, 0), (1.4, -25, 0, 0)]),
                  sting1=dict(rot=[(0, 0, 0, 0), (1.4, -45, 0, 0)]),
                  sting2=dict(rot=[(0, 0, 0, 0), (1.4, -30, 0, 0)])))
    # scorpion-specific attacks
    A.append(anim('scorpion_attack1', 0.7, impact=0.4,
                  pincer_r=dict(rot=[(0, 0, 0, 0), (0.25, -10, 40, 0), (0.4, -5, -15, 0), (0.7, 0, 0, 0)]),
                  pincer_l=dict(rot=[(0, 0, 0, 0), (0.25, -10, -40, 0), (0.4, -5, 15, 0), (0.7, 0, 0, 0)]),
                  claw_r=dict(rot=[(0, 0, 0, 0), (0.25, 0, 0, -30), (0.4, 0, 0, 10), (0.7, 0, 0, 0)]),
                  claw_l=dict(rot=[(0, 0, 0, 0), (0.25, 0, 0, 30), (0.4, 0, 0, -10), (0.7, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0, 0.8), (0.4, 0, 0, -2.0), (0.7, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.25, -6, 0, 0), (0.4, 6, 0, 0), (0.7, 0, 0, 0)])))
    A.append(anim('scorpion_attack2', 1.0, impact=0.6,
                  sting1=dict(rot=[(0, 0, 0, 0), (0.4, -25, 0, 0), (0.6, 35, 0, 0), (0.75, 30, 0, 0), (1.0, 0, 0, 0)]),
                  sting2=dict(rot=[(0, 0, 0, 0), (0.4, -15, 0, 0), (0.6, 20, 0, 0), (1.0, 0, 0, 0)]),
                  sting3=dict(rot=[(0, 0, 0, 0), (0.4, -10, 0, 0), (0.6, 25, 0, 0), (1.0, 0, 0, 0)]),
                  stinger=dict(rot=[(0, 0, 0, 0), (0.4, -20, 0, 0), (0.6, 30, 0, 0), (1.0, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.4, -8, 0, 0), (0.6, 8, 0, 0), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.4, 0, 0, 0.8), (0.6, 0, 0, -1.2), (1.0, 0, 0, 0)])))
    return A


def pal(**over):
    p = {'leg': '#7a6a58', 'paw': '#6a5a4a', 'head': '#7a6a58', 'snout': '#8a7a68', 'jaw': '#6a5a4a',
         'ear': '#5a4a3a', 'horn': '#2a2228', 'body': '#7a6a58', 'mane': '#8c7b66', 'tail': '#7a6a58',
         'tail2': '#8c7b66', 'spine': '#a0e0ff', 'crest': '#d06020', 'shell': '#3a2a1e', 'tail_s': '#3a2a1e',
         'sting': '#2a1a10', 'barb': '#e0d0a0', 'arm': '#4a3424', 'claw': '#4a3424', 'leg_s': '#3a2a1e'}
    p.update(over)
    return p


def face(p, eye=(30, 20, 10), glow=None, nose=(30, 22, 20), fangs=True):
    f = p.face('head', 'front')
    f.px(1, 2, eye)
    f.px(4, 2, eye)
    f.px(1, 1, shade(f.get(1, 1), 0.7))
    f.px(4, 1, shade(f.get(4, 1), 0.7))
    if glow is not None:
        g = p.face('head', 'front', glow=True)
        g.px(1, 2, glow)
        g.px(4, 2, glow)
    for side in ('right', 'left'):
        s = p.face('head', side)
        s.px(1, 2, eye)
    if p.visible(p.rig.parts['snout']):
        sn = p.face('snout', 'front')
        sn.rect(1, 0, 1, 0, nose)
        sn.px(0, 0, nose)
        sn.px(2, 0, nose)
        if fangs:
            sn.px(0, 2, (235, 230, 215))
            sn.px(2, 2, (235, 230, 215))
        top = p.face('snout', 'top')
        top.px(1, 0, nose)


def belly(p, col):
    b = p.face('torso', 'bottom')
    b.rect(1, 1, b.w - 2, b.h - 2, col)


def variants():
    V = []

    def deco_wolf(p):
        face(p)
        belly(p, rgb('#b0a090'))
    V.append(Variant('wolf', show={'snout', 'ear_r', 'ear_l', 'mane', 'tail'}, palette=pal(), paint=deco_wolf,
                     extra=dict(scale=1.0)))

    def deco_snow(p):
        face(p, eye=(40, 60, 90))
        belly(p, rgb('#ffffff'))
    V.append(Variant('snow_wolf', show={'snout', 'ear_r', 'ear_l', 'mane', 'tail'},
                     palette=pal(leg='#d8dde4', paw='#c0c8d0', head='#e4e8ee', snout='#eef2f6', jaw='#c8d0d8',
                                 ear='#a8b4c0', body='#e4e8ee', mane='#f4f8fc', tail='#e4e8ee', tail2='#f4f8fc'),
                     paint=deco_snow, extra=dict(scale=1.05)))

    def deco_rat(p):
        face(p, eye=(200, 30, 30), nose=(220, 140, 150), fangs=False)
        sn = p.face('snout', 'front')
        sn.px(0, 2, (250, 250, 240))
        sn.px(1, 2, (250, 250, 240))
        for side in ('front', 'back'):
            e = p.face('ear_r', side)
            e.px(0, 1, (210, 140, 150))
            e.px(1, 1, (210, 140, 150))
    V.append(Variant('cave_rat', show={'snout', 'ear_r', 'ear_l', 'tail'},
                     palette=pal(leg='#e0b0b0', paw='#e0b0b0', head='#6a6058', snout='#7a7068', jaw='#5a5048',
                                 ear='#b08888', body='#6a6058', tail={'base': '#d8a8a8', 'style': 'skin'},
                                 tail2={'base': '#d8a8a8', 'style': 'skin'}),
                     poses={'ear_r': dict(scale=(1.6, 1.6, 1.0)), 'ear_l': dict(scale=(1.6, 1.6, 1.0)),
                            'tail': dict(scale=(0.5, 0.5, 1.5), rot=(25, 0, 0)), 'tail2': dict(scale=(0.6, 0.6, 1.4)),
                            'front_right_leg': dict(scale=(1, 0.7, 1)), 'front_left_leg': dict(scale=(1, 0.7, 1)),
                            'hind_right_leg': dict(scale=(1, 0.7, 1)), 'hind_left_leg': dict(scale=(1, 0.7, 1)),
                            'base': dict(pos=(0, -2.4, 0)), 'snout': dict(scale=(0.8, 0.8, 1.2))},
                     paint=deco_rat, extra=dict(scale=0.6)))

    def deco_frost(p):
        face(p, eye=(120, 220, 255), glow=(120, 220, 255))
        belly(p, rgb('#d0ecff'))
        for fc in p.all_faces('spines', glow=True):
            for y in range(fc.h):
                for x in range(fc.w):
                    if (x + y) % 2 == 0:
                        fc.px(x, y, (90, 180, 230))
    V.append(Variant('frost_hound', show={'snout', 'ear_r', 'ear_l', 'mane', 'tail', 'spines'},
                     palette=pal(leg='#8aa8c0', paw='#7090aa', head='#9cb8d0', snout='#b0cce0', jaw='#7090aa',
                                 ear='#6080a0', body='#9cb8d0', mane={'base': '#d8f0ff', 'style': 'ice'},
                                 tail='#9cb8d0', tail2={'base': '#d8f0ff', 'style': 'ice'},
                                 spine={'base': '#bfeaff', 'style': 'crystal'}),
                     paint=deco_frost, glow=True, extra=dict(scale=1.15)))

    def deco_abyss(p):
        face(p, eye=(255, 80, 40), glow=(255, 60, 30))
        for fc in p.all_faces('torso', glow=True, sides_only=True):
            for x in range(1, fc.w - 1, 3):
                fc.px(x, 1, (140, 40, 200))
    V.append(Variant('abyss_hound', show={'snout', 'ear_r', 'ear_l', 'mane', 'tail', 'spines', 'horn_r', 'horn_l'},
                     palette=pal(leg='#2a2230', paw='#1e1824', head='#2e2636', snout='#3a3044', jaw='#1e1824',
                                 ear='#1e1824', body='#2e2636', mane={'base': '#3e2a4a', 'style': 'fur'},
                                 tail='#2e2636', tail2='#3e2a4a', spine={'base': '#6a2a8a', 'style': 'crystal'},
                                 horn='#c8b8d8'),
                     paint=deco_abyss, glow=True, extra=dict(scale=1.25)))

    def deco_sal(p):
        f = p.face('head', 'front')
        f.px(0, 1, (255, 220, 60))
        f.px(5, 1, (255, 220, 60))
        g = p.face('head', 'front', glow=True)
        g.px(0, 1, (255, 200, 40))
        g.px(5, 1, (255, 200, 40))
        s = p.face('lizard_snout', 'front')
        s.hline(0, s.w - 1, 1, (60, 20, 10))
        for fc in p.all_faces('torso', sides_only=True):
            for x in range(0, fc.w, 3):
                fc.px(x, 1, (255, 200, 60))
        for fc in p.all_faces('crest', glow=True):
            for y in range(fc.h):
                for x in range(fc.w):
                    if (x + y) % 3 == 0:
                        fc.px(x, y, (255, 120, 30))
    red = {'base': '#b8401c', 'style': 'scale'}
    V.append(Variant('salamander', show={'lizard_snout', 'tail', 'crest'},
                     palette=pal(leg=red, paw=red, head=red, jaw={'base': '#e09040', 'style': 'scale'}, body=red,
                                 tail=red, tail2={'base': '#d05020', 'style': 'scale'},
                                 crest={'base': '#ffb040', 'style': 'flame'}),
                     poses={'front_right_leg': dict(rot=(0, 0, 55)), 'front_left_leg': dict(rot=(0, 0, -55)),
                            'hind_right_leg': dict(rot=(0, 0, 55)), 'hind_left_leg': dict(rot=(0, 0, -55)),
                            'front_right_paw': dict(rot=(0, 0, -55)), 'front_left_paw': dict(rot=(0, 0, 55)),
                            'hind_right_paw': dict(rot=(0, 0, -55)), 'hind_left_paw': dict(rot=(0, 0, 55)),
                            'base': dict(pos=(0, -2.2, 0)), 'torso': dict(scale=(1.15, 0.85, 1.25)),
                            'head': dict(scale=(1.0, 0.75, 1.1)), 'tail': dict(scale=(1.6, 1.4, 1.4), rot=(30, 0, 0)),
                            'tail2': dict(scale=(1.2, 1.1, 1.5))},
                     paint=deco_sal, glow=True, extra=dict(scale=1.1)))

    def deco_scorpion(p):
        f = p.face('head', 'front')
        for x in (1, 2, 3, 4):
            f.px(x, 1, (230, 40, 30))
        g = p.face('head', 'front', glow=True)
        for x in (1, 2, 3, 4):
            g.px(x, 1, (200, 40, 20))
        f.hline(1, 4, 4, (20, 10, 5))
        for fc in p.all_faces('claw_r', sides_only=True):
            fc.vline(fc.w // 2, fc.h // 2, fc.h - 1, (20, 12, 6))
    chit = {'base': '#3a2a1e', 'style': 'chitin'}
    V.append(Variant('scorpion', show={'carapace', 'sting1', 'pincer_r', 'pincer_l', 'mid_leg_r1', 'mid_leg_r2',
                                       'mid_leg_l1', 'mid_leg_l2'},
                     palette=pal(leg=chit, paw=chit, head=chit, jaw=chit, body=chit, shell={'base': '#4a3624', 'style': 'chitin'},
                                 tail=chit, arm=chit, claw={'base': '#4a3020', 'style': 'chitin'}, leg_s=chit),
                     poses={'front_right_leg': dict(rot=(0, 0, 40)), 'front_left_leg': dict(rot=(0, 0, -40)),
                            'hind_right_leg': dict(rot=(0, 0, 40)), 'hind_left_leg': dict(rot=(0, 0, -40)),
                            'front_right_paw': dict(rot=(0, 0, -40)), 'front_left_paw': dict(rot=(0, 0, 40)),
                            'hind_right_paw': dict(rot=(0, 0, -40)), 'hind_left_paw': dict(rot=(0, 0, 40)),
                            'base': dict(pos=(0, -1.6, 0)), 'torso': dict(scale=(1.25, 0.7, 1.0)),
                            'head': dict(scale=(1.1, 0.6, 0.7), pos=(0, -0.5, 0.5))},
                     overrides={'attack1': 'scorpion_attack1', 'attack2': 'scorpion_attack2'},
                     paint=deco_scorpion, glow=True, extra=dict(scale=1.2)))
    import bestiary
    V.extend(bestiary.variants_for('beast'))
    return V


def rig():
    import bestiary
    r = Rig('beast', 'BeastEnemy', geometry(), animations(), variants(), SLOTS)
    bestiary.snap_to_ground(r)
    return r
