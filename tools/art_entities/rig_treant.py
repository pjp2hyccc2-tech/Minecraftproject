"""TREANT boss rig: b2 古代樹の守護者. Gnarled trunk with a carved glowing face, a great leaf canopy with
swaying side clumps, branch arms ending in twig claws, and root legs with splayed root toes."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']


def arm(side):
    s = -1 if side == 'r' else 1
    x0 = -13 if s < 0 else 8
    claws = P('%s_claws' % ('right' if s < 0 else 'left'), (-10.5 * -s * -1 if False else 10.5 * s, 7, 0),
              C(x0, 7, -3, 1, 7, 1, 'bark:twig', tag='tw1' if s < 0 else None, share=None if s < 0 else 'tw1'),
              C(x0 + 2, 7, -1, 1, 8, 1, 'bark:twig', tag='tw2' if s < 0 else None, share=None if s < 0 else 'tw2'),
              C(x0 + 4, 7, -3, 1, 6, 1, 'bark:twig', tag='tw3' if s < 0 else None, share=None if s < 0 else 'tw3'))
    fore = P('%s_forearm' % ('right' if s < 0 else 'left'), (10.5 * s, -5, 0),
             C(x0, -5, -3, 5, 12, 5, 'bark:arm2', tag='fa' if s < 0 else None, share=None if s < 0 else 'fa', mirror=s > 0),
             claws)
    return P('%s_arm' % ('right' if s < 0 else 'left'), (9 * s, -17, 0),
             C(x0, -19, -3, 5, 14, 6, 'bark:arm', tag='ua' if s < 0 else None, share=None if s < 0 else 'ua', mirror=s > 0),
             C(x0 - 1, -20, -4, 7, 4, 8, 'leaf:tuft', tag='tuft' if s < 0 else None, share=None if s < 0 else 'tuft', mirror=s > 0),
             fore, rot=(0, 0, 12 * s * -1))


def root_leg(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    x = 4 * s
    tag = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    foot = P('%s_foot' % name, (x, 14, 0), C(x - 4, 14, -6, 8, 10, 10, 'bark:foot', **tag('foot')),
             C(x - 5 if s < 0 else x + 2, 21, -9, 3, 3, 4, 'bark:toe', **tag('toe1')),
             C(x - 1, 21, -9.5, 3, 3, 4, 'bark:toe', **tag('toe2')),
             C(x - 6 if s < 0 else x + 4, 21, 1, 2, 3, 3, 'bark:toe', **tag('toe3')))
    return P('%s_root' % name, (x, 6, 0), C(x - 3, 5, -3.5, 6, 9, 7, 'bark:root', **tag('root')), foot)


def geometry():
    canopy = P('canopy', (0, -26, 0), C(-12, -38, -11, 24, 12, 22, 'leaf:canopy'), C(-8, -44, -7, 16, 6, 14, 'leaf:canopy_top'),
               P('canopy_r', (-12, -32, 0), C(-18, -37, -6, 7, 9, 11, 'leaf:clump', tag='clump')),
               P('canopy_l', (12, -32, 0), C(11, -37, -6, 7, 9, 11, 'leaf:clump', share='clump', mirror=True)),
               P('canopy_back', (0, -30, 11), C(-8, -36, 10, 16, 10, 6, 'leaf:clump2')))
    head = P('head', (0, -20, 0), C(-6, -26, -6, 12, 6, 12, 'bark:crown'), canopy,
             P('antler_r', (-5, -26, 0), C(-6, -36, -1, 2, 10, 2, 'bark:branch', tag='ant'), rot=(0, 0, -25)),
             P('antler_l', (5, -26, 0), C(4, -36, -1, 2, 10, 2, 'bark:branch', share='ant', mirror=True), rot=(0, 0, 25)))
    upper = P('trunk_upper', (0, -4, 0), C(-8, -20, -7, 16, 16, 14, 'bark:trunk2'), head, arm('r'), arm('l'),
              P('moss_beard', (0, -6, -7), C(-5, -6, -7.6, 10, 8, 0, 'leaf:moss')),
              P('mushrooms', (7, -10, 0), C(7.5, -12, -3, 3, 2, 3, 'skin:cap', tag='cap'), C(7.5, -7, 1, 2, 2, 2, 'skin:cap2'),
                C(-10.5, -14, -1, 3, 2, 3, 'skin:cap', share='cap')))
    trunk = P('trunk', (0, 6, 0), C(-7, -4, -6, 14, 10, 12, 'bark:trunk'), upper, root_leg('r'), root_leg('l'))
    return [P('base', (0, 24, 0), trunk)]


def animations():
    A = []
    A.append(anim('idle', 4.0, loop=True,
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (2.0, -2, 0, 1), (4.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.0, 2, 5, 0), (2.0, 0, 0, 0), (3.0, 2, -5, 0), (4.0, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 2), (1.0, 2, 0, 0), (2.0, 0, 0, -2), (3.0, -2, 0, 0), (4.0, 0, 0, 2)]),
                  canopy_r=dict(rot=[(0, 0, 0, 4), (2.0, 0, 0, -4), (4.0, 0, 0, 4)]),
                  canopy_l=dict(rot=[(0, 0, 0, -3), (2.0, 0, 0, 5), (4.0, 0, 0, -3)]),
                  canopy_back=dict(rot=[(0, 3, 0, 0), (2.0, -3, 0, 0), (4.0, 3, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (2.0, -4, 0, 3), (4.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (2.0, -4, 0, -3), (4.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, -10, 0, 0), (2.0, -16, 0, 0), (4.0, -10, 0, 0)]),
                  left_forearm=dict(rot=[(0, -10, 0, 0), (2.0, -16, 0, 0), (4.0, -10, 0, 0)]),
                  right_claws=dict(rot=[(0, 0, 0, 0), (1.0, -10, 0, 0), (2.0, 0, 0, 0), (3.0, -10, 0, 0), (4.0, 0, 0, 0)]),
                  left_claws=dict(rot=[(0, -10, 0, 0), (1.0, 0, 0, 0), (2.0, -10, 0, 0), (3.0, 0, 0, 0), (4.0, -10, 0, 0)]),
                  moss_beard=dict(rot=[(0, 0, 0, 0), (2.0, 6, 0, 0), (4.0, 0, 0, 0)])))
    A.append(anim('walk', 1.8, loop=True,
                  right_root=dict(rot=[(0, 20, 0, 0), (0.9, -20, 0, 0), (1.8, 20, 0, 0)]),
                  left_root=dict(rot=[(0, -20, 0, 0), (0.9, 20, 0, 0), (1.8, -20, 0, 0)]),
                  right_foot=dict(rot=[(0, 8, 0, 0), (0.45, 25, 0, 0), (0.9, 0, 0, 0), (1.8, 8, 0, 0)]),
                  left_foot=dict(rot=[(0, 0, 0, 0), (0.9, 8, 0, 0), (1.35, 25, 0, 0), (1.8, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -14, 0, 0), (0.9, 14, 0, 0), (1.8, -14, 0, 0)]),
                  left_arm=dict(rot=[(0, 14, 0, 0), (0.9, -14, 0, 0), (1.8, 14, 0, 0)]),
                  trunk_upper=dict(rot=[(0, 3, 0, 4), (0.9, 3, 0, -4), (1.8, 3, 0, 4)]),
                  canopy=dict(rot=[(0, 0, 0, -3), (0.9, 0, 0, 3), (1.8, 0, 0, -3)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.45, 0, 1.0, 0), (0.9, 0, 0, 0), (1.35, 0, 1.0, 0), (1.8, 0, 0, 0)])))
    A.append(anim('melee_slam', 1.8, impact=1.0,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -170, 0, 10), (0.75, -172, 0, 10), (1.0, -40, 0, 0), (1.3, -38, 0, 0), (1.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -170, 0, -10), (0.75, -172, 0, -10), (1.0, -40, 0, 0), (1.3, -38, 0, 0), (1.8, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -40, 0, 0), (1.0, 5, 0, 0), (1.8, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -40, 0, 0), (1.0, 5, 0, 0), (1.8, 0, 0, 0)]),
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, 0), (1.0, 30, 0, 0), (1.3, 26, 0, 0), (1.8, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.6, -10, 0, 0), (1.05, 18, 0, 0), (1.25, 8, 0, 0), (1.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 1, 0), (1.0, 0, -2.5, -1), (1.3, 0, -2, -1), (1.8, 0, 0, 0)])))
    A.append(anim('melee_sweep', 1.6, impact=0.8,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -50, 0, 85), (0.8, -75, 0, -35), (1.0, -70, 0, -40), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -15, 0, 0), (0.8, -5, 0, 0), (1.6, 0, 0, 0)]),
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.5, 0, 40, 0), (0.8, 5, -45, 0), (1.0, 5, -40, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, 10, 0, -25), (0.8, -20, 0, -20), (1.6, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.5, 0, -10, -6), (0.85, 0, 12, 8), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, 0, -1), (1.6, 0, 0, 0)])))
    A.append(anim('cast', 2.0, impact=1.1,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.7, -150, 0, 35), (1.1, -150, 0, 40), (1.5, -150, 0, 38), (2.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.7, -150, 0, -35), (1.1, -150, 0, -40), (1.5, -150, 0, -38), (2.0, 0, 0, 0)]),
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.7, -14, 0, 0), (1.1, -10, 0, 2), (1.2, -10, 0, -2), (1.3, -10, 0, 2), (1.5, -10, 0, 0), (2.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.7, -20, 0, 0), (1.5, -20, 0, 0), (2.0, 0, 0, 0)]),
                  canopy=dict(scale=[(0, 1, 1, 1), (1.1, 1.12, 1.12, 1.12), (1.5, 1.08, 1.08, 1.08), (2.0, 1, 1, 1)],
                              rot=[(0, 0, 0, 0), (1.1, 0, 0, 4), (1.2, 0, 0, -4), (1.3, 0, 0, 4), (1.5, 0, 0, 0), (2.0, 0, 0, 0)]),
                  right_claws=dict(rot=[(0, 0, 0, 0), (0.7, -40, 0, 0), (2.0, 0, 0, 0)]),
                  left_claws=dict(rot=[(0, 0, 0, 0), (0.7, -40, 0, 0), (2.0, 0, 0, 0)])))
    shake = [(round(i * 0.1, 2), 0, 0, (5 if i % 2 else -5)) for i in range(13)]
    A.append(anim('roar', 1.2, loop=True,
                  trunk_upper=dict(rot=[(0, -16, 0, 0), (0.6, -20, 0, 0), (1.2, -16, 0, 0)]),
                  head=dict(rot=[(0, -18, 0, 0), (0.6, -24, 0, 0), (1.2, -18, 0, 0)]),
                  canopy=dict(rot=shake),
                  canopy_r=dict(rot=[(t, 0, 0, -z) for (t, _, __, z) in shake]),
                  canopy_l=dict(rot=[(t, 0, 0, z) for (t, _, __, z) in shake]),
                  right_arm=dict(rot=[(0, -30, 0, 60), (0.6, -35, 0, 66), (1.2, -30, 0, 60)]),
                  left_arm=dict(rot=[(0, -30, 0, -60), (0.6, -35, 0, -66), (1.2, -30, 0, -60)]),
                  right_claws=dict(rot=[(0, -35, 0, 0), (0.6, -45, 0, 0), (1.2, -35, 0, 0)]),
                  left_claws=dict(rot=[(0, -35, 0, 0), (0.6, -45, 0, 0), (1.2, -35, 0, 0)])))
    A.append(anim('charge', 1.4,
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.25, 30, 0, 0), (1.2, 30, 0, 0), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.25, -15, 0, 0), (1.2, -15, 0, 0), (1.4, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.25, 35, 0, 20), (1.2, 35, 0, 20), (1.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.25, 35, 0, -20), (1.2, 35, 0, -20), (1.4, 0, 0, 0)]),
                  right_root=dict(rot=[(0, 0, 0, 0), (0.25, -35, 0, 0), (0.6, 35, 0, 0), (0.95, -35, 0, 0), (1.3, 20, 0, 0), (1.4, 0, 0, 0)]),
                  left_root=dict(rot=[(0, 0, 0, 0), (0.25, 35, 0, 0), (0.6, -35, 0, 0), (0.95, 35, 0, 0), (1.3, -20, 0, 0), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, -1, -1), (0.45, 0, 0.6, -2), (0.8, 0, -1, -2), (1.1, 0, 0.6, -2), (1.4, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.25, -18, 0, 0), (1.2, -18, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('summon', 2.0, impact=1.2,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.7, -165, 0, 15), (1.2, -50, 0, 0), (1.6, -50, 0, 0), (2.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.7, -165, 0, -15), (1.2, -50, 0, 0), (1.6, -50, 0, 0), (2.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.2, 40, 0, 0), (1.6, 40, 0, 0), (2.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (1.2, 40, 0, 0), (1.6, 40, 0, 0), (2.0, 0, 0, 0)]),
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.7, -15, 0, 0), (1.2, 40, 0, 0), (1.6, 38, 0, 0), (2.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.7, 0, 1, 0), (1.2, 0, -4, 0), (1.6, 0, -4, 0), (2.0, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (1.25, 15, 0, 0), (1.6, 10, 0, 0), (2.0, 0, 0, 0)])))
    A.append(anim('phase_change', 2.6,
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.8, 25, 0, 0), (1.5, -20, 0, 0), (2.0, -16, 0, 0), (2.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, 25, 0, 0), (1.5, -30, 0, 0), (2.6, 0, 0, 0)]),
                  canopy=dict(scale=[(0, 1, 1, 1), (0.8, 0.85, 0.85, 0.85), (1.5, 1.3, 1.3, 1.3), (2.0, 1.2, 1.2, 1.2), (2.6, 1, 1, 1)]),
                  canopy_r=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 25), (2.6, 0, 0, 0)]),
                  canopy_l=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, -25), (2.6, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.8, 10, 0, 10), (1.5, -60, 0, 80), (2.0, -55, 0, 75), (2.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.8, 10, 0, -10), (1.5, -60, 0, -80), (2.0, -55, 0, -75), (2.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, -3, 0), (1.5, 0, 1.5, 0), (2.6, 0, 0, 0)])))
    A.append(anim('stagger', 1.3,
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.15, -24, 0, 8), (0.7, -14, 0, 4), (1.3, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.15, -20, 10, 0), (1.3, 0, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.2, -15, 0, 10), (0.5, 8, 0, -6), (0.8, -4, 0, 3), (1.3, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.15, -60, 0, 40), (0.7, -30, 0, 20), (1.3, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.15, -40, 0, -50), (0.7, -20, 0, -25), (1.3, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.15, 0, -0.5, 3), (0.7, 0, -1, 3.5), (1.3, 0, 0, 0)]),
                  right_root=dict(rot=[(0, 0, 0, 0), (0.15, 25, 0, 0), (1.3, 0, 0, 0)])))
    A.append(anim('death', 3.6,
                  trunk_upper=dict(rot=[(0, 0, 0, 0), (0.5, -18, 0, 0), (1.2, 5, 0, 6), (3.6, 10, 0, 6)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -25, 0, 0), (1.5, 10, 0, 0), (3.6, 15, 0, 5)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 0), (2.8, 10, 0, 0), (3.1, -12, 0, 0), (3.6, -6, 0, 0)],
                              scale=[(0, 1, 1, 1), (2.8, 1, 1, 1), (3.6, 1.08, 0.85, 1.08)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, 50), (1.5, -20, 0, 30), (2.8, -150, 0, 30), (3.6, -150, 0, 35)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, -50), (1.5, -20, 0, -30), (2.8, -140, 0, -30), (3.6, -140, 0, -35)]),
                  right_root=dict(rot=[(0, 0, 0, 0), (1.5, -40, 0, 0), (2.8, 0, 0, 0), (3.6, 0, 0, 0)]),
                  left_root=dict(rot=[(0, 0, 0, 0), (1.5, -30, 0, 0), (2.8, 0, 0, 0), (3.6, 0, 0, 0)]),
                  right_foot=dict(rot=[(0, 0, 0, 0), (1.5, 45, 0, 0), (2.8, 0, 0, 0)]),
                  left_foot=dict(rot=[(0, 0, 0, 0), (1.5, 40, 0, 0), (2.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (1.5, 0, -4, 0), (2.8, 0, -2, -2), (3.6, 0, -3, -2)],
                            rot=[(0, 0, 0, 0), (1.5, 6, 0, 0), (2.8, 86, 0, 0), (3.05, 80, 0, 0), (3.6, 87, 0, 0)])))
    return A


def variants():
    def deco(p):
        f = p.face('trunk_upper', 'front')
        g = p.face('trunk_upper', 'front', glow=True)
        socket = (22, 14, 8)
        for (x0, x1) in ((2, 5), (10, 13)):
            f.rect(x0, 3, x1, 6, socket)
            f.hline(x0 - 1, x1 + 1, 2, (58, 40, 26))
            for x in range(x0 + 1, x1):
                f.px(x, 4, (255, 214, 90))
                f.px(x, 5, (255, 170, 40))
                g.px(x, 4, (255, 255, 255))
                g.px(x, 5, (255, 220, 160))
        mouth = [(4, 10), (5, 11), (6, 10), (7, 11), (8, 10), (9, 11), (10, 10), (11, 11)]
        for (x, y) in mouth:
            f.px(x, y, socket)
            f.px(x, y + 1, socket)
            f.px(x, y + 2, (40, 20, 10))
            g.px(x, y + 1, (255, 150, 40))
        f.vline(7, 7, 9, (40, 26, 16))
        f.vline(8, 7, 9, (40, 26, 16))
        for fc in p.all_faces('mushrooms', index=0, glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (180, 255, 220))
        for fc in p.all_faces('mushrooms', index=1, glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (180, 255, 220))
        for part in ('canopy', 'canopy_r', 'canopy_l'):
            for fc in p.all_faces(part, glow=True, sides_only=True):
                for k in range(0, fc.w, 7):
                    fc.px(k, (k * 3) % max(1, fc.h), (255, 230, 255))
    bark = {'base': '#5e4430', 'style': 'bark', 'moss': (86, 130, 52), 'inner': '#b08a5a'}
    leaf = {'base': '#3f7a34', 'flowers': (235, 130, 170)}
    pal = {'trunk': bark, 'trunk2': bark, 'crown': bark, 'root': bark, 'foot': bark, 'toe': bark,
           'arm': bark, 'arm2': bark, 'twig': {'base': '#4a3424', 'style': 'bark'}, 'branch': {'base': '#4a3424', 'style': 'bark'},
           'canopy': leaf, 'canopy_top': dict(leaf, base='#4a8a3c'), 'clump': dict(leaf, base='#38703a'),
           'clump2': dict(leaf, base='#346a34'), 'tuft': dict(leaf, base='#4a8a3c'), 'moss': {'base': '#4f7a30', 'style': 'leaf'},
           'cap': {'base': '#d8f0e0', 'style': 'gem'}, 'cap2': {'base': '#c0e8d8', 'style': 'gem'}}
    return [Variant('b2', palette=pal, paint=deco, glow=True, extra=dict(scale=1.3, hitbox=(2.2, 4.0)))]


def rig():
    r = Rig('treant', 'TreantBoss', geometry(), animations(), variants(), SLOTS)
    r.preview_ppu = 2.6
    return r
