"""COLOSSUS boss rig: b3 砂塵の巨神 (sandstone giant with nemes headdress, false beard and glowing hieroglyphs) and
b7 天空の機械神 (mechanical god: visor, antennae, chest reactor and a rotating 8-segment ring on its back)."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']


def side_arm(s):
    name = 'right' if s < 0 else 'left'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x_out = -18 if s < 0 else 11
    fore = P('%s_forearm' % name, (14.5 * s, -7, 0),
             C(x_out - 0.5, -7, -4.5, 8, 12, 9, 'sandstone:forearm', **kw('fore')),
             C(x_out - 1, 3, -5, 9, 8, 10, 'sandstone:fist', **kw('fist')))
    return P('%s_arm' % name, (13 * s, -18, 0), C(x_out, -21, -4, 7, 14, 8, 'sandstone:arm', **kw('arm')),
             P('shoulder_%s' % s and ('shoulder_r' if s < 0 else 'shoulder_l'), (14 * s, -22, 0),
               C(x_out - 1, -24, -5, 9, 5, 10, 'sandstone:shoulder', **kw('shoulder'))),
             fore)


def geometry():
    ring_segments = []
    for k in range(8):
        ring_segments.append(P('ring_s%d' % k, (0, -12, 10), C(-4, -27, 9.5, 8, 2, 2, 'mech:ring', tag='rs' if k == 0 else None,
                                                                   share=None if k == 0 else 'rs'), rot=(0, 0, k * 45)))
    head = P('head', (0, -21, -2), C(-5, -30, -7, 10, 9, 9, 'sandstone:head'),
             P('nemes', (0, -30, 0), C(-7, -31, -5, 14, 12, 9, 'cloth:nemes'), C(-7, -22, -6, 3, 9, 2, 'cloth:lappet', tag='lap'),
               C(4, -22, -6, 3, 9, 2, 'cloth:lappet', share='lap', mirror=True), opt=True),
             P('false_beard', (0, -21, -6), C(-1, -21, -7, 2, 5, 2, 'gold:beard'), opt=True),
             P('visor', (0, -26, -7), C(-5, -27, -7.6, 10, 2, 1, 'glowcore:visor'), opt=True),
             P('antenna_r', (-4, -30, -2), C(-4.5, -37, -2.5, 1, 7, 1, 'metal:antenna', tag='ant'), rot=(-10, 0, -15), opt=True),
             P('antenna_l', (4, -30, -2), C(3.5, -37, -2.5, 1, 7, 1, 'metal:antenna', share='ant', mirror=True), rot=(-10, 0, 15),
               opt=True))
    torso = P('torso', (0, -1, 0), C(-11, -21, -7, 22, 20, 14, 'sandstone:torso'),
              head, side_arm(-1), side_arm(1),
              P('core', (0, -14, -7), C(-3, -17, -7.6, 6, 6, 1, 'glowcore:core'), opt=True),
              P('ring', (0, -12, 10), *ring_segments, opt=True),
              P('back_vents', (0, -12, 7), C(-7, -18, 6.5, 4, 10, 2, 'metal:vent', tag='vent'),
                C(3, -18, 6.5, 4, 10, 2, 'metal:vent', share='vent', mirror=True), opt=True))
    legs = []
    for s in (-1, 1):
        name = 'right' if s < 0 else 'left'
        kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x = 5 * s
        legs.append(P('%s_leg' % name, (x, 4, 0), C(x - 4, 3, -4, 8, 10, 8, 'sandstone:thigh', **kw('thigh')),
                      P('%s_shin' % name, (x, 13, 0), C(x - 4.5, 13, -4.5, 9, 11, 9, 'sandstone:shin', **kw('shin')))))
    pelvis = P('pelvis', (0, 3, 0), C(-8, -1, -5, 16, 6, 10, 'sandstone:pelvis'), torso, *legs,
               P('loincloth', (0, 4, -5), C(-5, 4, -5.6, 10, 9, 1, 'cloth:loin'), opt=True))
    return [P('base', (0, 24, 0), pelvis)]


def animations():
    A = []
    A.append(anim('idle', 4.0, loop=True,
                  torso=dict(rot=[(0, 0, 0, 0), (2.0, -2, 0, 0), (4.0, 0, 0, 0)],
                             scale=[(0, 1, 1, 1), (2.0, 1.02, 1.01, 1.02), (4.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.0, 2, 8, 0), (2.0, 0, 0, 0), (3.0, 2, -8, 0), (4.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 4), (2.0, -3, 0, 6), (4.0, 0, 0, 4)]),
                  left_arm=dict(rot=[(0, 0, 0, -4), (2.0, -3, 0, -6), (4.0, 0, 0, -4)]),
                  right_forearm=dict(rot=[(0, -8, 0, 0), (2.0, -12, 0, 0), (4.0, -8, 0, 0)]),
                  left_forearm=dict(rot=[(0, -8, 0, 0), (2.0, -12, 0, 0), (4.0, -8, 0, 0)]),
                  ring=dict(rot=[(0, 0, 0, 0), (4.0, 0, 0, 45)], lin=True),
                  core=dict(scale=[(0, 1, 1, 1), (1.0, 1.15, 1.15, 1), (2.0, 1, 1, 1), (3.0, 1.15, 1.15, 1), (4.0, 1, 1, 1)]),
                  antenna_r=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, -6), (4.0, 0, 0, 0)]),
                  antenna_l=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, 6), (4.0, 0, 0, 0)]),
                  loincloth=dict(rot=[(0, 0, 0, 0), (2.0, -4, 0, 0), (4.0, 0, 0, 0)])))
    A.append(anim('walk', 2.0, loop=True,
                  right_leg=dict(rot=[(0, 20, 0, 0), (1.0, -20, 0, 0), (2.0, 20, 0, 0)]),
                  left_leg=dict(rot=[(0, -20, 0, 0), (1.0, 20, 0, 0), (2.0, -20, 0, 0)]),
                  right_shin=dict(rot=[(0, 8, 0, 0), (0.5, 30, 0, 0), (1.0, 0, 0, 0), (2.0, 8, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.0, 8, 0, 0), (1.5, 30, 0, 0), (2.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -14, 0, 0), (1.0, 14, 0, 0), (2.0, -14, 0, 0)]),
                  left_arm=dict(rot=[(0, 14, 0, 0), (1.0, -14, 0, 0), (2.0, 14, 0, 0)]),
                  torso=dict(rot=[(0, 3, -5, 4), (1.0, 3, 5, -4), (2.0, 3, -5, 4)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 1.2, 0), (1.0, 0, 0, 0), (1.5, 0, 1.2, 0), (2.0, 0, 0, 0)]),
                  loincloth=dict(rot=[(0, -8, 0, 0), (1.0, 8, 0, 0), (2.0, -8, 0, 0)])))
    A.append(anim('melee_slam', 2.0, impact=1.1,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.7, -170, 0, 15), (0.85, -172, 0, 15), (1.1, -45, 0, 5), (1.4, -42, 0, 5), (2.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.7, -170, 0, -15), (0.85, -172, 0, -15), (1.1, -45, 0, -5), (1.4, -42, 0, -5), (2.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.7, -35, 0, 0), (1.1, 0, 0, 0), (2.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.7, -35, 0, 0), (1.1, 0, 0, 0), (2.0, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.7, -18, 0, 0), (1.1, 32, 0, 0), (1.4, 28, 0, 0), (2.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.7, -15, 0, 0), (1.1, 12, 0, 0), (2.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.7, 0, 1.5, 0.5), (1.1, 0, -3, -1.5), (1.4, 0, -2.6, -1.5), (2.0, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (1.1, -30, 0, 0), (1.4, -28, 0, 0), (2.0, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (1.1, 40, 0, 0), (1.4, 38, 0, 0), (2.0, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (1.1, -20, 0, 0), (2.0, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.1, 35, 0, 0), (2.0, 0, 0, 0)])))
    A.append(anim('melee_sweep', 1.8, impact=0.9,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -55, 0, 85), (0.9, -85, 0, -30), (1.15, -80, 0, -35), (1.8, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, 0), (0.9, -5, 0, 0), (1.8, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.6, 0, 40, 0), (0.9, 6, -45, 0), (1.15, 6, -42, 0), (1.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, 15, 0, -25), (0.9, -25, 0, -20), (1.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 0, -25, 0), (0.9, 0, 22, 0), (1.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 0, 0.6), (0.9, 0, -0.6, -1.6), (1.8, 0, 0, 0)]),
                  pelvis=dict(rot=[(0, 0, 0, 0), (0.6, 0, 12, 0), (0.9, 0, -12, 0), (1.8, 0, 0, 0)])))
    A.append(anim('cast', 2.2, impact=1.2,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.8, -160, 0, 40), (1.2, -165, 0, 45), (1.7, -160, 0, 42), (2.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.8, -160, 0, -40), (1.2, -165, 0, -45), (1.7, -160, 0, -42), (2.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.8, -20, 0, 0), (2.2, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.8, -20, 0, 0), (2.2, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.8, -18, 0, 0), (1.2, -20, 0, 0), (1.7, -18, 0, 0), (2.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, -30, 0, 0), (1.7, -30, 0, 0), (2.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, 1.5, 0), (1.7, 0, 1.5, 0), (2.2, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.8, 1.6, 1.6, 1.3), (1.2, 2.0, 2.0, 1.5), (1.7, 1.6, 1.6, 1.3), (2.2, 1, 1, 1)]),
                  ring=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 360)], scale=[(0, 1, 1, 1), (1.2, 1.15, 1.15, 1), (2.2, 1, 1, 1)],
                            rot_interp='linear')))
    tremble = [(round(i * 0.1, 2), 0, 0, (1.5 if i % 2 else -1.5)) for i in range(13)]
    A.append(anim('roar', 1.2, loop=True,
                  torso=dict(rot=[(t, -16, 0, z) for (t, _, __, z) in tremble]),
                  head=dict(rot=[(0, -30, 0, 0), (0.6, -34, 0, 0), (1.2, -30, 0, 0)]),
                  right_arm=dict(rot=[(0, -30, 0, 55), (0.6, -36, 0, 60), (1.2, -30, 0, 55)]),
                  left_arm=dict(rot=[(0, -30, 0, -55), (0.6, -36, 0, -60), (1.2, -30, 0, -55)]),
                  right_forearm=dict(rot=[(0, -40, 0, 0), (1.2, -40, 0, 0)]),
                  left_forearm=dict(rot=[(0, -40, 0, 0), (1.2, -40, 0, 0)]),
                  base=dict(pos=[(0, 0, 0.5, 0), (0.6, 0, 1.0, 0), (1.2, 0, 0.5, 0)]),
                  ring=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 90)], lin=True),
                  core=dict(scale=[(0, 1.3, 1.3, 1.2), (0.6, 1.6, 1.6, 1.3), (1.2, 1.3, 1.3, 1.2)])))
    A.append(anim('charge', 1.6,
                  torso=dict(rot=[(0, 0, 0, 0), (0.3, 30, 0, 0), (1.4, 30, 0, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -20, 0, 0), (1.4, -20, 0, 0), (1.6, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -20, 0, 35), (1.4, -20, 0, 35), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -60, 0, -10), (1.4, -60, 0, -10), (1.6, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.3, -35, 0, 0), (0.7, 35, 0, 0), (1.1, -35, 0, 0), (1.5, 20, 0, 0), (1.6, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.3, 35, 0, 0), (0.7, -35, 0, 0), (1.1, 35, 0, 0), (1.5, -20, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.3, 0, -1, -1), (0.5, 0, 0.8, -2), (0.9, 0, -1, -2), (1.3, 0, 0.8, -2), (1.6, 0, 0, 0)])))
    A.append(anim('summon', 2.2, impact=1.2,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.8, -175, 0, 10), (1.2, -175, 0, 10), (1.7, -175, 0, 10), (2.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.8, -20, 0, -30), (2.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.8, -10, 0, 0), (2.2, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.8, -12, 0, 6), (1.7, -12, 0, 6), (2.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, -35, 10, 0), (1.7, -35, 10, 0), (2.2, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.9, -40, 0, 0), (1.2, 0, 0, 0), (2.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.9, 0, 1.5, 0), (1.2, 0, -1.5, 0), (1.5, 0, 0, 0), (2.2, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (1.2, 2, 2, 1.5), (2.2, 1, 1, 1)])))
    A.append(anim('phase_change', 2.8,
                  torso=dict(rot=[(0, 0, 0, 0), (0.9, 30, 0, 0), (1.6, -20, 0, 0), (2.2, -16, 0, 0), (2.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.9, 30, 0, 0), (1.6, -35, 0, 0), (2.8, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.9, -20, 0, 15), (1.6, -50, 0, 85), (2.2, -45, 0, 80), (2.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.9, -20, 0, -15), (1.6, -50, 0, -85), (2.2, -45, 0, -80), (2.8, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.9, -70, 0, 0), (1.6, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.9, 85, 0, 0), (1.6, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.9, 70, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.9, 0, -7, 0), (1.6, 0, 1.5, 0), (2.8, 0, 0, 0)]),
                  ring=dict(rot=[(0, 0, 0, 0), (2.8, 0, 0, 720)], scale=[(0, 1, 1, 1), (0.9, 0.7, 0.7, 1), (1.6, 1.3, 1.3, 1), (2.8, 1, 1, 1)],
                            rot_interp='linear'),
                  core=dict(scale=[(0, 1, 1, 1), (0.9, 0.5, 0.5, 1), (1.6, 2.4, 2.4, 1.6), (2.8, 1, 1, 1)])))
    A.append(anim('stagger', 1.4,
                  torso=dict(rot=[(0, 0, 0, 0), (0.2, -26, 0, 8), (0.8, -14, 0, 4), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, -30, 10, 0), (1.4, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.2, -60, 0, 45), (0.8, -30, 0, 20), (1.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.2, -45, 0, -55), (0.8, -20, 0, -25), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, -0.5, 3), (0.8, 0, -1.5, 3.5), (1.4, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.2, 25, 0, 0), (1.4, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.2, 30, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('death', 4.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.6, -25, 0, 0), (1.6, 10, 0, 6), (2.4, 40, 0, 6), (4.0, 35, 0, 8)],
                             scale=[(0, 1, 1, 1), (3.2, 1, 1, 1), (4.0, 1.08, 0.8, 1.08)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -35, 0, 0), (1.6, 10, 0, 0), (2.4, 40, 0, 10), (4.0, 35, 0, 15)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -60, 0, 60), (1.6, -20, 0, 20), (3.0, -150, 0, 25), (4.0, -155, 0, 25)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -60, 0, -60), (1.6, -20, 0, -20), (3.0, -140, 0, -25), (4.0, -145, 0, -25)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (1.6, -80, 0, 0), (2.4, -80, 0, 0), (3.0, 0, 0, 0), (4.0, 0, 0, 3)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (1.6, -75, 0, 0), (2.4, -75, 0, 0), (3.0, 0, 0, 0), (4.0, 0, 0, -3)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (1.6, 92, 0, 0), (2.4, 92, 0, 0), (3.0, 8, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.6, 90, 0, 0), (2.4, 90, 0, 0), (3.0, 8, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 0, 1.5), (1.6, 0, -9, 1.5), (2.4, 0, -9, 1.5), (3.0, 0, -2, -1), (4.0, 0, -3, -1)],
                            rot=[(0, 0, 0, 0), (2.4, 0, 0, 0), (3.0, 85, 0, 0), (3.2, 79, 0, 0), (4.0, 86, 0, 0)]),
                  ring=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, 200), (4.0, 0, 0, 240)], scale=[(0, 1, 1, 1), (3.0, 1, 1, 1), (4.0, 0.3, 0.3, 1)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.6, 2.2, 2.2, 1.5), (2.4, 0.4, 0.4, 1), (4.0, 0.1, 0.1, 1)])))
    return A


def variants():
    sand = {'base': '#d4b67c', 'style': 'sandstone'}

    def deco_b3(p):
        f = p.face('head', 'front')
        f.rect(1, 3, 3, 4, (30, 22, 12))
        f.rect(6, 3, 8, 4, (30, 22, 12))
        g = p.face('head', 'front', glow=True)
        for x in (1, 2, 3, 6, 7, 8):
            f.px(x, 3, (120, 230, 255))
            g.px(x, 3, (255, 255, 255))
        f.hline(3, 6, 7, (60, 44, 24))
        for part, sides in (('torso', ('front', 'back')), ('right_arm', ('front', 'right')), ('right_forearm', ('front',)),
                            ('right_leg', ('front',))):
            for side in sides:
                fc = p.face(part, side)
                gc = p.face(part, side, glow=True)
                for y in range(3, fc.h - 2, 5):
                    for x in range(2, fc.w - 2, 4):
                        glyph = (x * 13 + y * 7) % 4
                        pts = [(0, 0), (1, 0), (0, 1), (1, 2)] if glyph == 0 else [(0, 0), (0, 1), (0, 2), (1, 1)] if glyph == 1 else \
                            [(1, 0), (0, 1), (1, 1), (2, 1)] if glyph == 2 else [(0, 0), (2, 0), (1, 1), (1, 2)]
                        for (dx, dy) in pts:
                            fc.px(x + dx, y + dy, (90, 200, 240))
                            gc.px(x + dx, y + dy, (255, 255, 255))
        for fc in p.all_faces('nemes', index=0, sides_only=True) + p.all_faces('nemes', index=1, sides_only=True):
            for y in range(0, fc.h, 2):
                fc.hline(0, fc.w - 1, y, (40, 70, 150))
        nf = p.face('nemes', 'front', index=0)
        for y in range(1, nf.h):
            for x in range(2, nf.w - 2):
                nf.clear(x, y)
    pal3 = {'head': sand, 'torso': sand, 'arm': sand, 'forearm': sand, 'fist': {'base': '#c4a46a', 'style': 'sandstone'},
            'shoulder': {'base': '#c8a868', 'style': 'sandstone'}, 'thigh': sand, 'shin': {'base': '#c4a46a', 'style': 'sandstone'},
            'pelvis': sand, 'nemes': {'base': '#d8b040', 'style': 'gold'}, 'lappet': {'base': '#d8b040', 'style': 'gold'},
            'beard': {'base': '#3a50a0', 'style': 'gold'}, 'loin': {'base': '#e8e0c8', 'trim': (60, 90, 170)}}
    mech = {'base': '#b8c0c8', 'style': 'mech', 'panel': 5}

    def deco_b7(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (60, 66, 74))
        f.hline(1, f.w - 2, f.h - 2, (40, 44, 50))
        for fc in p.all_faces('visor', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 255, 255))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 255, 255))
        for k in range(8):
            for fc in p.all_faces('ring_s%d' % k, glow=True, sides_only=True):
                for x in range(1, fc.w - 1, 2):
                    fc.px(x, fc.h // 2, (220, 250, 255))
        for part in ('torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
            for fc in p.all_faces(part, sides_only=True):
                gc = p.face(part, fc.face, glow=True)
                for x in range(2, fc.w - 2, 5):
                    for y in range(2, fc.h - 2, 6):
                        gc.px(x, y, (160, 230, 255))
                        fc.px(x, y, (120, 220, 255))
    pal7 = {'head': mech, 'torso': mech, 'arm': mech, 'forearm': {'base': '#a8b0b8', 'style': 'mech', 'panel': 4},
            'fist': {'base': '#8a929c', 'style': 'metal'}, 'shoulder': {'base': '#d8c890', 'style': 'metal'},
            'thigh': mech, 'shin': {'base': '#9aa2ac', 'style': 'mech', 'panel': 4}, 'pelvis': mech,
            'visor': {'base': '#60e0ff', 'style': 'glowcore'}, 'antenna': {'base': '#d8c890', 'style': 'metal'},
            'core': {'base': '#60e0ff', 'style': 'glowcore'}, 'ring': {'base': '#d8c890', 'style': 'metal', 'rivets': False},
            'vent': {'base': '#5a626c', 'style': 'mech', 'panel': 2}}
    return [Variant('b3', show={'nemes', 'false_beard', 'loincloth'}, palette=pal3, paint=deco_b3, glow=True,
                    extra=dict(scale=1.4, hitbox=(2.6, 4.8))),
            Variant('b7', show={'visor', 'antenna_r', 'antenna_l', 'core', 'ring', 'back_vents'}, palette=pal7, paint=deco_b7,
                    glow=True, extra=dict(scale=1.3, hitbox=(2.4, 4.4)))]


def rig():
    r = Rig('colossus', 'ColossusBoss', geometry(), animations(), variants(), SLOTS)
    r.preview_ppu = 2.4
    return r
