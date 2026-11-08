"""DRAGON boss rig: b6 灼炎の竜王. Four legs (three segments each), folded membrane wings with a hinged tip, two-part
neck, horned head with an opening jaw, four tail segments with a fin, spines along the back, glowing chest."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']


def leg(name, x, front, mirror):
    kw = (lambda t: dict(share=t, mirror=True)) if mirror else (lambda t: dict(tag=t))
    if front:
        z = -6
        upper = C(x - 2.5, 2, z - 3, 5, 10, 6, 'scale:leg', **kw('fu'))
        shin = C(x - 2.5, 12, z - 2, 5, 9, 5, 'scale:shin', **kw('fs'))
        foot = C(x - 3, 21, z - 5, 6, 3, 8, 'scale:foot', **kw('ff'))
        return P(name, (x, 3, z), upper, P(name + '_shin', (x, 12, z), shin, P(name + '_foot', (x, 21, z), foot)))
    z = 12
    upper = C(x - 3, 0, z - 4, 6, 12, 9, 'scale:thigh', **kw('hu'))
    shin = C(x - 2.5, 12, z - 1, 5, 9, 5, 'scale:shin2', **kw('hs'))
    foot = C(x - 3, 21, z - 3, 6, 3, 8, 'scale:foot', **kw('hf'))
    return P(name, (x, 2, z), upper, P(name + '_shin', (x, 12, z + 1), shin, P(name + '_foot', (x, 21, z + 1), foot)))


def wing(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    bx = -27 if s < 0 else 7
    tx = -45 if s < 0 else 27
    tip = P('%s_wing_tip' % name, (27 * s, -5, -4), C(tx, -6, -5, 18, 2, 2, 'scale:wingbone', **kw('tipbone')),
            C(tx, -4, -4, 18, 0, 14, 'membrane:membrane', **kw('tipmem')), rot=(0, 0, -50 * s * -1 if False else (-50 if s < 0 else 50)))
    return P('%s_wing' % name, (7 * s, -5, -4), C(bx, -6, -5, 20, 2, 2, 'scale:wingbone', **kw('bone')),
             C(bx, -4, -4, 20, 0, 18, 'membrane:membrane', **kw('mem')), tip,
             rot=(0, 30 if s < 0 else -30, 35 if s < 0 else -35))


def geometry():
    head = P('head', (0, -2, -27),
             C(-5, -7, -38, 10, 8, 11, 'scale:head'), C(-3.5, -4, -47, 7, 5, 9, 'scale:snout'),
             C(-1, -10, -36, 2, 3, 8, 'bone:crest'),
             P('horn_r', (-4, -6, -30), C(-5, -8, -30, 2, 2, 10, 'bone:horn', tag='horn'), rot=(22, -15, 0)),
             P('horn_l', (4, -6, -30), C(3, -8, -30, 2, 2, 10, 'bone:horn', share='horn', mirror=True), rot=(22, 15, 0)),
             P('jaw', (0, 1, -30), C(-3.5, 1, -46, 7, 2, 16, 'scale:jaw')),
             rot=(45, 0, 0))
    neck = P('neck1', (0, -2, -10), C(-4.5, -6.5, -19, 9, 9, 9, 'scale:neck'),
             P('neck2', (0, -2, -19), C(-4, -6, -27, 8, 8, 8, 'scale:neck2'), head, rot=(-15, 0, 0)), rot=(-30, 0, 0))
    tail = P('tail1', (0, -1, 16), C(-5, -5, 16, 10, 8, 12, 'scale:tail'), C(-1, -8, 18, 2, 3, 3, 'bone:spine', tag='tsp'),
             P('tail2', (0, -1, 28), C(-4, -4, 28, 8, 6, 12, 'scale:tail2'), C(-1, -6.5, 30, 2, 3, 3, 'bone:spine', share='tsp'),
               P('tail3', (0, -1, 40), C(-3, -3, 40, 6, 4, 12, 'scale:tail3'),
                 P('tail_tip', (0, -1, 52), C(-2, -2, 52, 4, 3, 10, 'scale:tail4'), C(-5, -1, 56, 10, 0, 8, 'membrane:fin')),
                 rot=(-4, 0, 0)), rot=(-6, 0, 0)), rot=(-8, 0, 0))
    body = P('body', (0, 4, 4), C(-8, -6, -10, 16, 13, 26, 'scale:body'),
             C(-1, -10, -8, 2, 4, 3, 'bone:spine', tag='sp'), C(-1, -10, -2, 2, 4, 3, 'bone:spine', share='sp'),
             C(-1, -10, 4, 2, 4, 3, 'bone:spine', share='sp'), C(-1, -9, 10, 2, 3, 3, 'bone:spine'),
             neck, tail, wing('r'), wing('l'),
             leg('front_right_leg', -7, True, False), leg('front_left_leg', 7, True, True),
             leg('hind_right_leg', -7, False, False), leg('hind_left_leg', 7, False, True))
    return [P('base', (0, 24, 0), body)]


def gait(t0, period, amp, n, offset=0.0):
    return [(round(t0 + i * period / 2, 4), (amp if (i % 2 == 0) else -amp) * (1 if offset == 0 else -1), 0, 0) for i in range(n * 2 + 1)]


def animations():
    A = []
    A.append(anim('idle', 3.0, loop=True,
                  body=dict(scale=[(0, 1, 1, 1), (1.5, 1.025, 1.04, 1.0), (3.0, 1, 1, 1)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (1.5, -3, 5, 0), (3.0, 0, 0, 0)]),
                  neck2=dict(rot=[(0, 0, 0, 0), (1.5, 3, 4, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.75, 2, -4, 0), (1.5, 0, -6, 0), (2.25, 2, 0, 0), (3.0, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 3, 0, 0), (1.5, 8, 0, 0), (3.0, 3, 0, 0)]),
                  tail1=dict(rot=[(0, 0, -6, 0), (1.5, 0, 6, 0), (3.0, 0, -6, 0)]),
                  tail2=dict(rot=[(0, 0, -6, 0), (0.4, 0, -8, 0), (1.9, 0, 8, 0), (3.0, 0, -6, 0)]),
                  tail3=dict(rot=[(0, 0, -4, 0), (0.8, 0, -10, 0), (2.3, 0, 10, 0), (3.0, 0, -4, 0)]),
                  tail_tip=dict(rot=[(0, 0, 0, 0), (1.2, 0, -14, 0), (2.7, 0, 14, 0), (3.0, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 3), (3.0, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, -3), (3.0, 0, 0, 0)])))
    A.append(anim('walk', 1.6, loop=True,
                  front_right_leg=dict(rot=gait(0, 1.6, 24, 1)),
                  hind_left_leg=dict(rot=gait(0, 1.6, 24, 1)),
                  front_left_leg=dict(rot=gait(0, 1.6, 24, 1, offset=1)),
                  hind_right_leg=dict(rot=gait(0, 1.6, 24, 1, offset=1)),
                  front_right_leg_shin=dict(rot=[(0, 0, 0, 0), (0.4, 0, 0, 0), (0.6, -30, 0, 0), (0.8, 0, 0, 0), (1.6, 0, 0, 0)]),
                  front_left_leg_shin=dict(rot=[(0, -30, 0, 0), (0.2, 0, 0, 0), (1.2, 0, 0, 0), (1.4, -30, 0, 0), (1.6, -30, 0, 0)]),
                  hind_right_leg_shin=dict(rot=[(0, 25, 0, 0), (0.2, 0, 0, 0), (1.2, 0, 0, 0), (1.4, 25, 0, 0), (1.6, 25, 0, 0)]),
                  hind_left_leg_shin=dict(rot=[(0, 0, 0, 0), (0.4, 0, 0, 0), (0.6, 25, 0, 0), (0.8, 0, 0, 0), (1.6, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 3), (0.8, 0, 0, -3), (1.6, 0, 0, 3)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.4, 0, 0.8, 0), (0.8, 0, 0, 0), (1.2, 0, 0.8, 0), (1.6, 0, 0, 0)]),
                  neck1=dict(rot=[(0, 3, -5, 0), (0.8, 3, 5, 0), (1.6, 3, -5, 0)]),
                  head=dict(rot=[(0, -3, 3, 0), (0.8, -3, -3, 0), (1.6, -3, 3, 0)]),
                  tail1=dict(rot=[(0, 0, 10, 0), (0.8, 0, -10, 0), (1.6, 0, 10, 0)]),
                  tail2=dict(rot=[(0, 0, 6, 0), (0.4, 0, 10, 0), (1.2, 0, -10, 0), (1.6, 0, 6, 0)]),
                  tail3=dict(rot=[(0, 0, 0, 0), (0.6, 0, 12, 0), (1.4, 0, -12, 0), (1.6, 0, 0, 0)])))
    A.append(anim('melee_slam', 1.8, impact=1.0,
                  body=dict(rot=[(0, 0, 0, 0), (0.6, -36, 0, 0), (0.75, -38, 0, 0), (1.0, 8, 0, 0), (1.3, 6, 0, 0), (1.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 3, 2), (1.0, 0, -1.5, -2), (1.3, 0, -1, -2), (1.8, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.6, -70, 0, 10), (1.0, -15, 0, 0), (1.8, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.6, -60, 0, -10), (1.0, -15, 0, 0), (1.8, 0, 0, 0)]),
                  front_right_leg_shin=dict(rot=[(0, 0, 0, 0), (0.6, 60, 0, 0), (1.0, 0, 0, 0), (1.8, 0, 0, 0)]),
                  front_left_leg_shin=dict(rot=[(0, 0, 0, 0), (0.6, 55, 0, 0), (1.0, 0, 0, 0), (1.8, 0, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.6, 35, 0, 0), (1.0, 0, 0, 0), (1.8, 0, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.6, 35, 0, 0), (1.0, 0, 0, 0), (1.8, 0, 0, 0)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (0.6, 10, 0, 0), (1.0, 25, 0, 0), (1.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -10, 0, 0), (1.0, 15, 0, 0), (1.8, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.6, 20, 0, 0), (1.0, 35, 0, 0), (1.3, 10, 0, 0), (1.8, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, -20, -10), (1.0, 0, 0, 10), (1.8, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, 20, 10), (1.0, 0, 0, -10), (1.8, 0, 0, 0)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.6, 20, 0, 0), (1.0, -10, 0, 0), (1.8, 0, 0, 0)])))
    A.append(anim('melee_sweep', 1.6, impact=0.9,
                  body=dict(rot=[(0, 0, 0, 0), (0.5, 0, -38, 0), (0.9, 0, 48, 0), (1.1, 0, 44, 0), (1.6, 0, 0, 0)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.5, 0, 35, 0), (0.9, 0, -50, 0), (1.1, 0, -45, 0), (1.6, 0, 0, 0)]),
                  tail2=dict(rot=[(0, 0, 0, 0), (0.55, 0, 25, 0), (0.95, 0, -40, 0), (1.6, 0, 0, 0)]),
                  tail3=dict(rot=[(0, 0, 0, 0), (0.6, 0, 20, 0), (1.0, 0, -35, 0), (1.6, 0, 0, 0)]),
                  tail_tip=dict(rot=[(0, 0, 0, 0), (0.65, 0, 15, 0), (1.05, 0, -30, 0), (1.6, 0, 0, 0)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (0.5, 0, 30, 0), (0.9, 0, -35, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, 0, 15, 0), (0.9, 0, -20, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.9, 0, -0.5, 0), (1.6, 0, 0, 0)])))
    A.append(anim('cast', 2.0, impact=1.0,
                  neck1=dict(rot=[(0, 0, 0, 0), (0.8, -28, 0, 0), (1.0, 18, 0, 0), (1.6, 16, 0, 0), (2.0, 0, 0, 0)]),
                  neck2=dict(rot=[(0, 0, 0, 0), (0.8, -20, 0, 0), (1.0, 12, 0, 0), (1.6, 10, 0, 0), (2.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, -12, 0, 0), (1.0, 14, 0, 0), (1.6, 12, 0, 0), (2.0, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.8, 25, 0, 0), (1.0, 42, 0, 0), (1.6, 40, 0, 0), (2.0, 0, 0, 0)]),
                  body=dict(scale=[(0, 1, 1, 1), (0.8, 1.06, 1.08, 1.02), (1.0, 0.98, 0.97, 1), (2.0, 1, 1, 1)],
                            rot=[(0, 0, 0, 0), (0.8, -8, 0, 0), (1.0, 4, 0, 0), (2.0, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.8, 0, -15, -8), (1.6, 0, -15, -8), (2.0, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.8, 0, 15, 8), (1.6, 0, 15, 8), (2.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, 0, 1.2), (1.0, 0, 0, -0.6), (2.0, 0, 0, 0)])))
    tremble = [(round(i * 0.1, 2), 0, 0, (2 if i % 2 else -2)) for i in range(13)]
    A.append(anim('roar', 1.2, loop=True,
                  neck1=dict(rot=[(0, -34, 0, 0), (0.6, -38, 0, 0), (1.2, -34, 0, 0)]),
                  neck2=dict(rot=[(0, -14, 0, 0), (0.6, -18, 0, 0), (1.2, -14, 0, 0)]),
                  head=dict(rot=[(t, -18, 0, z) for (t, _, __, z) in tremble]),
                  jaw=dict(rot=[(0, 45, 0, 0), (0.6, 50, 0, 0), (1.2, 45, 0, 0)]),
                  body=dict(rot=[(0, -10, 0, 0), (0.6, -12, 0, 0), (1.2, -10, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, -32, -28), (0.3, 0, -32, -18), (0.6, 0, -32, -28), (0.9, 0, -32, -18), (1.2, 0, -32, -28)]),
                  left_wing=dict(rot=[(0, 0, 32, 28), (0.3, 0, 32, 18), (0.6, 0, 32, 28), (0.9, 0, 32, 18), (1.2, 0, 32, 28)]),
                  right_wing_tip=dict(rot=[(0, 0, 0, -48), (1.2, 0, 0, -48)]),
                  left_wing_tip=dict(rot=[(0, 0, 0, 48), (1.2, 0, 0, 48)]),
                  base=dict(pos=[(0, 0, 1, 0), (0.6, 0, 1.4, 0), (1.2, 0, 1, 0)]),
                  tail1=dict(rot=[(0, 10, 0, 0), (0.6, 14, 0, 0), (1.2, 10, 0, 0)])))
    A.append(anim('charge', 1.4,
                  neck1=dict(rot=[(0, 0, 0, 0), (0.2, 22, 0, 0), (1.2, 22, 0, 0), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, -12, 0, 0), (1.2, -12, 0, 0), (1.4, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.2, 8, 0, 0), (1.2, 8, 0, 0), (1.4, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0)] + [(round(0.2 + i * 0.175, 3), (40 if i % 2 else -40), 0, 0) for i in range(6)] + [(1.4, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0)] + [(round(0.2 + i * 0.175, 3), (-40 if i % 2 else 40), 0, 0) for i in range(6)] + [(1.4, 0, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0)] + [(round(0.2 + i * 0.175, 3), (-35 if i % 2 else 35), 0, 0) for i in range(6)] + [(1.4, 0, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0)] + [(round(0.2 + i * 0.175, 3), (35 if i % 2 else -35), 0, 0) for i in range(6)] + [(1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, -1, -1), (0.375, 0, 0.8, -2), (0.55, 0, -0.5, -2), (0.725, 0, 0.8, -2),
                                 (0.9, 0, -0.5, -2), (1.075, 0, 0.8, -2), (1.4, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.2, 0, 10, -10), (1.2, 0, 10, -10), (1.4, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.2, 0, -10, 10), (1.2, 0, -10, 10), (1.4, 0, 0, 0)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.2, 12, 0, 0), (1.2, 12, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('summon', 2.0, impact=1.1,
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, -35, -55), (1.1, 0, -30, 15), (1.4, 0, -30, 5), (2.0, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, 35, 55), (1.1, 0, 30, -15), (1.4, 0, 30, -5), (2.0, 0, 0, 0)]),
                  right_wing_tip=dict(rot=[(0, 0, 0, 0), (0.6, 0, 0, 50), (1.1, 0, 0, 30), (2.0, 0, 0, 0)]),
                  left_wing_tip=dict(rot=[(0, 0, 0, 0), (0.6, 0, 0, -50), (1.1, 0, 0, -30), (2.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 1, 0), (0.9, 0, 3.5, 0), (1.1, 0, 2.5, 0), (1.6, 0, 0, 0), (2.0, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.9, -15, 0, 0), (1.4, -5, 0, 0), (2.0, 0, 0, 0)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (0.9, -30, 0, 0), (1.4, -25, 0, 0), (2.0, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.9, 40, 0, 0), (1.4, 35, 0, 0), (2.0, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.9, -30, 0, 0), (1.4, 0, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.9, -30, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('phase_change', 2.6,
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.8, 0, 65, 55), (1.1, 0, 65, 55), (1.4, 0, -35, -15), (2.0, 0, -30, -10), (2.6, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.8, 0, -65, -55), (1.1, 0, -65, -55), (1.4, 0, 35, 15), (2.0, 0, 30, 10), (2.6, 0, 0, 0)]),
                  right_wing_tip=dict(rot=[(0, 0, 0, 0), (0.8, 0, 0, -30), (1.4, 0, 0, 50), (2.0, 0, 0, 45), (2.6, 0, 0, 0)]),
                  left_wing_tip=dict(rot=[(0, 0, 0, 0), (0.8, 0, 0, 30), (1.4, 0, 0, -50), (2.0, 0, 0, -45), (2.6, 0, 0, 0)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (0.8, 25, 0, 0), (1.4, -40, 0, 0), (2.0, -35, 0, 0), (2.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, 20, 0, 0), (1.4, -20, 0, 0), (2.6, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.8, 0, 0, 0), (1.4, 50, 0, 0), (2.0, 45, 0, 0), (2.6, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.8, 10, 0, 0), (1.4, -14, 0, 0), (2.6, 0, 0, 0)],
                            scale=[(0, 1, 1, 1), (0.8, 0.95, 0.95, 0.98), (1.4, 1.08, 1.08, 1.03), (2.6, 1, 1, 1)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, -2, 0), (1.4, 0, 2, 0), (2.6, 0, 0, 0)])))
    A.append(anim('stagger', 1.2,
                  neck1=dict(rot=[(0, 0, 0, 0), (0.15, -28, 15, 0), (0.6, -15, 5, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.15, -20, 20, 10), (0.4, 0, -15, -8), (0.7, -8, 8, 4), (1.2, 0, 0, 0)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.15, 30, 0, 0), (1.2, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.15, -6, 0, 10), (0.6, -3, 0, 5), (1.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.15, 0, 0, 2.5), (0.6, 0, -0.5, 3), (1.2, 0, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.15, -40, 0, 0), (0.6, -10, 0, 0), (1.2, 0, 0, 0)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.2, 15, 25, 0), (0.6, 5, -15, 0), (1.2, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.15, 0, -10, 20), (1.2, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.15, 0, 10, -20), (1.2, 0, 0, 0)])))
    A.append(anim('death', 3.6,
                  body=dict(rot=[(0, 0, 0, 0), (0.6, -25, 0, 0), (1.6, 6, 0, 0), (3.6, 6, 0, 0)]),
                  neck1=dict(rot=[(0, 0, 0, 0), (0.6, -35, 0, 0), (1.6, 10, 0, 0), (2.6, 35, 0, 0), (3.6, 40, 0, 0)]),
                  neck2=dict(rot=[(0, 0, 0, 0), (0.6, -15, 0, 0), (2.6, 25, 0, 0), (3.6, 30, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, 0), (2.6, 30, 0, 15), (3.6, 35, 0, 20)]),
                  jaw=dict(rot=[(0, 0, 0, 0), (0.6, 45, 0, 0), (1.6, 30, 0, 0), (3.6, 25, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, -30, -25), (1.6, 0, -10, 10), (3.0, 0, 20, 30), (3.6, 0, 20, 35)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.6, 0, 30, 25), (1.6, 0, 10, -10), (3.0, 0, -20, -40), (3.6, 0, -20, -45)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.6, -40, 0, 0), (1.6, 30, 0, 0), (3.6, 35, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, 0), (1.6, 35, 0, 0), (3.6, 40, 0, 0)]),
                  front_right_leg_shin=dict(rot=[(0, 0, 0, 0), (1.6, -50, 0, 0), (3.6, -55, 0, 0)]),
                  front_left_leg_shin=dict(rot=[(0, 0, 0, 0), (1.6, -55, 0, 0), (3.6, -60, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (1.6, -35, 0, 0), (3.6, -40, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (1.6, -30, 0, 0), (3.6, -35, 0, 0)]),
                  hind_right_leg_shin=dict(rot=[(0, 0, 0, 0), (1.6, 45, 0, 0), (3.6, 50, 0, 0)]),
                  hind_left_leg_shin=dict(rot=[(0, 0, 0, 0), (1.6, 40, 0, 0), (3.6, 45, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 2, 1), (1.6, 0, -8, 1), (2.8, 0, -9, 1), (3.6, 0, -9.5, 1)],
                            rot=[(0, 0, 0, 0), (1.8, 0, 0, 0), (2.8, 0, 0, 72), (3.05, 0, 0, 66), (3.6, 0, 0, 70)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.6, 15, 20, 0), (1.6, -5, -10, 0), (3.6, -10, -15, 0)]),
                  tail2=dict(rot=[(0, 0, 0, 0), (1.6, 0, -15, 0), (3.6, 0, -20, 0)])))
    return A


def variants():
    def deco(p):
        head = p.face('head', 'left', index=0)
        for side in ('right', 'left'):
            s = p.face('head', side, index=0)
            gx = 3 if side == 'right' else s.w - 4
            for dx in range(2):
                s.px(gx + dx, 3, (255, 220, 60))
                p.face('head', side, index=0, glow=True).px(gx + dx, 3, (255, 255, 255))
            s.px(gx, 2, (40, 14, 10))
            s.px(gx + 1, 2, (40, 14, 10))
        sn = p.face('head', 'front', index=1)
        sn.px(1, 1, (30, 10, 8))
        sn.px(sn.w - 2, 1, (30, 10, 8))
        for side in ('right', 'left'):
            j = p.face('jaw', side)
            for x in range(0, j.w, 2):
                j.px(x, 0, (240, 230, 210))
            s2 = p.face('head', side, index=1)
            for x in range(0, s2.w, 2):
                s2.px(x, s2.h - 1, (240, 230, 210))
        top = p.face('jaw', 'top')
        for y in range(top.h):
            for x in range(1, top.w - 1):
                top.px(x, y, (150, 40, 30))
        gtop = p.face('jaw', 'top', glow=True)
        for y in range(2, top.h, 2):
            for x in range(2, top.w - 2):
                gtop.px(x, y, (255, 140, 40))
        belly = p.face('body', 'bottom')
        for y in range(belly.h):
            for x in range(2, belly.w - 2):
                belly.px(x, y, (220, 160, 80) if y % 3 else (180, 120, 60))
        bf = p.face('body', 'front')
        for y in range(bf.h):
            for x in range(3, bf.w - 3):
                bf.px(x, y, (230, 170, 80) if y % 3 else (190, 130, 60))
        gb = p.face('body', 'front', glow=True)
        for y in range(4, bf.h - 2):
            for x in range(5, bf.w - 5):
                if (x + y) % 3 == 0:
                    gb.px(x, y, (255, 120, 30))
        for side in ('right', 'left', 'top'):
            fc = p.face('body', side)
            gc = p.face('body', side, glow=True)
            for y in range(1, fc.h - 1, 4):
                for x in range(2, fc.w - 2, 6):
                    fc.px(x, y, (255, 150, 50))
                    gc.px(x, y, (255, 120, 30))
    scale = {'base': '#8a2418', 'style': 'scale'}
    pal = {'head': scale, 'snout': scale, 'jaw': {'base': '#7a2014', 'style': 'scale'}, 'neck': scale, 'neck2': scale,
           'body': scale, 'leg': scale, 'shin': {'base': '#7a2014', 'style': 'scale'}, 'shin2': {'base': '#7a2014', 'style': 'scale'},
           'thigh': scale, 'foot': {'base': '#3a1a14', 'style': 'scale'}, 'tail': scale, 'tail2': scale,
           'tail3': {'base': '#7a2014', 'style': 'scale'}, 'tail4': {'base': '#6a1c12', 'style': 'scale'},
           'wingbone': {'base': '#5a1810', 'style': 'scale'}, 'membrane': {'base': '#c84a1c', 'vein': (255, 170, 60)},
           'fin': {'base': '#d85a20', 'vein': (255, 180, 60)}, 'horn': {'base': '#2a2220', 'style': 'bone'},
           'crest': {'base': '#2a2220', 'style': 'bone'}, 'spine': {'base': '#2a2220', 'style': 'bone'}}
    return [Variant('b6', palette=pal, paint=deco, glow=True, extra=dict(scale=1.5, hitbox=(3.6, 3.4)))]


def rig():
    r = Rig('dragon', 'DragonBoss', geometry(), animations(), variants(), SLOTS)
    r.preview_ppu = 2.2
    return r
