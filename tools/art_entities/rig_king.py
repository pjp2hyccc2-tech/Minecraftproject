"""KING boss rig: b1 地下都市の盗賊王, b8 終焉の門番, final 終焉の王 (+ phase looks final_p1..final_p3).
Large armoured humanoid (44 units tall at scale 1) with crown, cape (two segments) and a greatsword; extra parts
for the final boss: floating crown (p1+), void core and bigger spikes (p2+), blade halo and burnt cape (p3)."""
from boss_forms import cracks, glow_faces, veins
from ermodel import C, P, Rig, Variant, anim
from erpaint import human_face, lighten, mix, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']


def geometry():
    crown_cubes = [C(-5, -27, -6, 10, 2, 10, 'gold:crown'), C(-4.5, -30, -6.5, 2, 3, 1, 'gold:crown_spike', tag='cs1'),
                   C(-1, -31, -6.5, 2, 4, 1, 'gold:crown_spike'), C(2.5, -30, -6.5, 2, 3, 1, 'gold:crown_spike', share='cs1'),
                   C(-5.5, -29, -1, 1, 2, 2, 'gold:crown_spike', tag='cs2'), C(4.5, -29, -1, 1, 2, 2, 'gold:crown_spike', share='cs2')]
    head = P('head', (0, -16, -1), C(-4.5, -25, -5.5, 9, 9, 9, 'skin:face'),
             P('crown', (0, -25, -1), *crown_cubes, opt=True),
             P('helm', (0, -20, -1), C(-4.5, -25, -5.5, 9, 9, 9, 'metal:helm', inflate=0.6),
               C(-0.5, -28, -5, 1, 3, 9, 'metal:helm_crest'), opt=True),
             P('horn_r', (-4.5, -23, -1), C(-8.5, -24, -2, 4, 2, 2, 'bone:horn', tag='horn'),
               P('horn_r_tip', (-8.5, -23, -1), C(-10.5, -30, -2, 2, 7, 2, 'bone:horn_tip', tag='horntip'), rot=(0, 0, 12)),
               rot=(0, 10, -15), opt=True),
             P('horn_l', (4.5, -23, -1), C(4.5, -24, -2, 4, 2, 2, 'bone:horn', share='horn', mirror=True),
               P('horn_l_tip', (8.5, -23, -1), C(8.5, -30, -2, 2, 7, 2, 'bone:horn_tip', share='horntip', mirror=True), rot=(0, 0, -12)),
               rot=(0, -10, 15), opt=True),
             P('beard', (0, -17, -5), C(-3.5, -18, -6.5, 7, 6, 2, 'hair:beard'), rot=(10, 0, 0), opt=True),
             P('floating_crown', (0, -34, -1), C(-4, -35, -5, 8, 2, 8, 'gold:fcrown'),
               C(-3.5, -38, -5.5, 1, 3, 1, 'gold:fcrown_spike', tag='fs'), C(-0.5, -39, -5.5, 1, 4, 1, 'gold:fcrown_spike'),
               C(2.5, -38, -5.5, 1, 3, 1, 'gold:fcrown_spike', share='fs'), C(-0.5, -38, 2.5, 1, 3, 1, 'gold:fcrown_spike', share='fs'),
               opt=True))
    sword = P('greatsword', (-10.5, 4, 0), C(-11, 3.5, -2, 1, 1, 6, 'leather:grip'), C(-11.5, 0.5, -4, 2, 6, 1, 'gold:guard'),
              C(-11, 2, -28, 1, 3, 24, 'metal:blade'), C(-11.5, 3, 4, 2, 2, 2, 'gold:pommel'), rot=(45, 0, 0))
    right_arm = P('right_arm', (-10, -14, 0), C(-13, -15, -3, 5, 11, 6, 'cloth:sleeve', tag='uarm'),
                  P('pauldron_r', (-10, -15, 0), C(-14, -18, -4.5, 8, 5, 9, 'metal:pauldron', tag='paul'),
                    P('spikes_r', (-11, -18, 0), C(-12, -24, -1.5, 2, 6, 2, 'metal:spike', tag='sp1'),
                      C(-8.5, -22, 1.5, 2, 4, 2, 'metal:spike', tag='sp2'), C(-13, -21, 2, 2, 3, 2, 'metal:spike', tag='sp3'),
                      rot=(0, 0, -18), opt=True)),
                  P('right_forearm', (-10.5, -4, 0), C(-13.5, -4, -3.5, 6, 10, 7, 'metal:gauntlet', tag='gaunt'), sword))
    left_arm = P('left_arm', (10, -14, 0), C(8, -15, -3, 5, 11, 6, 'cloth:sleeve', share='uarm', mirror=True),
                 P('pauldron_l', (10, -15, 0), C(6, -18, -4.5, 8, 5, 9, 'metal:pauldron', share='paul', mirror=True),
                   P('spikes_l', (11, -18, 0), C(10, -24, -1.5, 2, 6, 2, 'metal:spike', share='sp1', mirror=True),
                     C(6.5, -22, 1.5, 2, 4, 2, 'metal:spike', share='sp2', mirror=True),
                     C(11, -21, 2, 2, 3, 2, 'metal:spike', share='sp3', mirror=True), rot=(0, 0, 18), opt=True)),
                 P('left_forearm', (10.5, -4, 0), C(7.5, -4, -3.5, 6, 10, 7, 'metal:gauntlet', share='gaunt', mirror=True)))
    halo_blades = []
    for k in range(8):
        halo_blades.append(P('halo_%d' % k, (0, -22, 8), C(-1, -33, 7.5, 2, 7, 1, 'metal:halo', tag='hb' if k == 0 else None,
                                                             share=None if k == 0 else 'hb'), rot=(0, 0, k * 45)))
    chest = P('chest', (0, 0, 0), C(-8, -16, -5, 16, 16, 10, 'metal:cuirass'),
              head, right_arm, left_arm,
              P('cape', (0, -15, 5.5), C(-7, -15, 5.5, 14, 14, 1, 'cloth:cape'),
                P('cape_lower', (0, -1, 6), C(-7, -1, 5.5, 14, 13, 1, 'cloth:cape2')), rot=(5, 0, 0)),
              P('core', (0, -10, -5), C(-2.5, -12, -5.7, 5, 5, 1, 'glowcore:core'), opt=True),
              P('halo', (0, -22, 8), *halo_blades, opt=True))
    right_leg = P('right_leg', (-3.5, 5, 0), C(-6.5, 4, -3, 6, 9, 6, 'metal:greave', tag='thigh'),
                  P('right_shin', (-3.5, 13, 0), C(-6.5, 13, -3, 6, 8, 6, 'metal:greave2', tag='shin'),
                    C(-7, 21, -4, 7, 3, 7, 'metal:sabaton', tag='boot')))
    left_leg = P('left_leg', (3.5, 5, 0), C(0.5, 4, -3, 6, 9, 6, 'metal:greave', share='thigh', mirror=True),
                 P('left_shin', (3.5, 13, 0), C(0.5, 13, -3, 6, 8, 6, 'metal:greave2', share='shin', mirror=True),
                   C(0, 21, -4, 7, 3, 7, 'metal:sabaton', share='boot', mirror=True)))
    hips = P('hips', (0, 3, 0), C(-7, 0, -4, 14, 6, 8, 'metal:faulds'),
             chest, right_leg, left_leg,
             P('tasset', (0, 5, -4), C(-5, 5, -4.6, 10, 7, 1, 'cloth:tasset')))
    return [P('base', (0, 24, 0), hips)]


def animations():
    A = []
    A.append(anim('idle', 4.0, loop=True,
                  chest=dict(rot=[(0, 0, 0, 0), (2.0, -2.5, 0, 0), (4.0, 0, 0, 0)],
                             scale=[(0, 1, 1, 1), (2.0, 1.02, 1.01, 1.03), (4.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.0, 2, 6, 0), (2.0, 1, 0, 0), (3.0, 2, -6, 0), (4.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -8, 0, 4), (2.0, -11, 0, 6), (4.0, -8, 0, 4)]),
                  right_forearm=dict(rot=[(0, -18, 0, 0), (2.0, -22, 0, 0), (4.0, -18, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, -5), (2.0, -3, 0, -7), (4.0, 0, 0, -5)]),
                  left_forearm=dict(rot=[(0, -10, 0, 0), (2.0, -14, 0, 0), (4.0, -10, 0, 0)]),
                  cape=dict(rot=[(0, 3, 0, 0), (1.3, 7, 0, 1), (2.6, 4, 0, -1), (4.0, 3, 0, 0)]),
                  cape_lower=dict(rot=[(0, 2, 0, 0), (1.6, 8, 0, 2), (3.0, 3, 0, -2), (4.0, 2, 0, 0)]),
                  tasset=dict(rot=[(0, 0, 0, 0), (2.0, -3, 0, 0), (4.0, 0, 0, 0)]),
                  floating_crown=dict(pos=[(0, 0, 0, 0), (1.0, 0, 1.2, 0), (2.0, 0, 0, 0), (3.0, 0, 1.2, 0), (4.0, 0, 0, 0)],
                                      rot=[(0, 0, 0, 0), (4.0, 0, 180, 0)], rot_interp='linear'),
                  halo=dict(rot=[(0, 0, 0, 0), (4.0, 0, 0, 45)], lin=True),
                  core=dict(scale=[(0, 1, 1, 1), (1.0, 1.2, 1.2, 1), (2.0, 1, 1, 1), (3.0, 1.2, 1.2, 1), (4.0, 1, 1, 1)])))
    A.append(anim('walk', 1.4, loop=True,
                  right_leg=dict(rot=[(0, 22, 0, 0), (0.7, -22, 0, 0), (1.4, 22, 0, 0)]),
                  left_leg=dict(rot=[(0, -22, 0, 0), (0.7, 22, 0, 0), (1.4, -22, 0, 0)]),
                  right_shin=dict(rot=[(0, 12, 0, 0), (0.35, 38, 0, 0), (0.7, 2, 0, 0), (1.4, 12, 0, 0)]),
                  left_shin=dict(rot=[(0, 2, 0, 0), (0.7, 12, 0, 0), (1.05, 38, 0, 0), (1.4, 2, 0, 0)]),
                  left_arm=dict(rot=[(0, 16, 0, 0), (0.7, -16, 0, 0), (1.4, 16, 0, 0)]),
                  right_arm=dict(rot=[(0, -6, 0, 0), (0.7, 6, 0, 0), (1.4, -6, 0, 0)]),
                  chest=dict(rot=[(0, 3, -5, 3), (0.7, 3, 5, -3), (1.4, 3, -5, 3)]),
                  hips=dict(rot=[(0, 0, 5, 0), (0.7, 0, -5, 0), (1.4, 0, 5, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.35, 0, 0.9, 0), (0.7, 0, 0, 0), (1.05, 0, 0.9, 0), (1.4, 0, 0, 0)]),
                  cape=dict(rot=[(0, 14, 0, 0), (0.7, 20, 0, 0), (1.4, 14, 0, 0)]),
                  cape_lower=dict(rot=[(0, 8, 0, 0), (0.7, 14, 0, 0), (1.4, 8, 0, 0)]),
                  tasset=dict(rot=[(0, -6, 0, 0), (0.7, 6, 0, 0), (1.4, -6, 0, 0)])))
    A.append(anim('melee_slam', 1.6, impact=0.85,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -172, 0, 8), (0.62, -175, 0, 8), (0.85, -38, 0, 0), (1.15, -34, 0, 0), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -18, 0, 0), (0.85, 12, 0, 0), (1.15, 10, 0, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -160, 0, -28), (0.85, -42, 0, -14), (1.15, -40, 0, -14), (1.6, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, 0), (0.85, -10, 0, 0), (1.6, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.5, -16, 10, 0), (0.85, 26, -4, 0), (1.15, 22, 0, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -12, 0, 0), (0.85, 12, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 1.2, 0.5), (0.85, 0, -2.5, -1.5), (1.15, 0, -2.2, -1.5), (1.6, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.5, 8, 0, 0), (0.85, -32, 0, 0), (1.15, -30, 0, 0), (1.6, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.85, 45, 0, 0), (1.15, 42, 0, 0), (1.6, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.85, 22, 0, 0), (1.6, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.85, 32, 0, 0), (1.6, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.5, 25, 0, 0), (0.85, 8, 0, 0), (1.6, 0, 0, 0)]),
                  cape_lower=dict(rot=[(0, 0, 0, 0), (0.6, 20, 0, 0), (1.0, 5, 0, 0), (1.6, 0, 0, 0)])))
    A.append(anim('melee_sweep', 1.4, impact=0.7,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.42, -55, 45, 80), (0.7, -80, -50, -10), (0.9, -75, -55, -15), (1.4, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.42, -25, 0, 0), (0.7, -5, 0, 0), (1.4, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.42, 0, 42, 0), (0.7, 6, -48, 0), (0.9, 6, -44, 0), (1.4, 0, 0, 0)]),
                  hips=dict(rot=[(0, 0, 0, 0), (0.42, 0, 12, 0), (0.7, 0, -14, 0), (1.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.42, -40, 0, -35), (0.7, 20, 0, -40), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.42, 0, -30, 0), (0.7, 0, 25, 0), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.42, 0, -0.8, 0.8), (0.7, 0, -1.4, -1.6), (1.4, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.42, 15, 0, 0), (0.7, -25, 0, 0), (1.4, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.7, 25, 0, 0), (1.4, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.7, 15, 0, 0), (1.4, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.7, 18, 0, -10), (1.4, 0, 0, 0)])))
    A.append(anim('cast', 1.6, impact=0.9,
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -160, 0, -18), (0.9, -95, 0, -5), (1.2, -90, 0, -5), (1.6, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, 0), (0.9, 0, 0, 0), (1.6, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, 15), (1.2, -30, 0, 15), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, 30, 0, 0), (1.2, 30, 0, 0), (1.6, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.6, -14, -15, 0), (0.9, 8, 10, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -22, 0, 0), (0.9, 5, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 0.8, 0), (0.9, 0, 0, -0.8), (1.6, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.6, 1.8, 1.8, 1.4), (0.9, 0.7, 0.7, 1), (1.6, 1, 1, 1)]),
                  floating_crown=dict(pos=[(0, 0, 0, 0), (0.6, 0, 3, 0), (1.6, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.6, 18, 0, 0), (0.9, 30, 0, 0), (1.6, 0, 0, 0)])))
    tremble = [(round(i * 0.1, 2), 0, 0, (1.5 if i % 2 else -1.5)) for i in range(11)]
    A.append(anim('roar', 1.0, loop=True,
                  chest=dict(rot=[(t, -14 + z * 0.4, 0, z) for (t, _, __, z) in tremble]),
                  head=dict(rot=[(0, -28, 0, 0), (0.5, -32, 0, 0), (1.0, -28, 0, 0)]),
                  right_arm=dict(rot=[(0, -25, 0, 58), (0.5, -30, 0, 62), (1.0, -25, 0, 58)]),
                  left_arm=dict(rot=[(0, -25, 0, -58), (0.5, -30, 0, -62), (1.0, -25, 0, -58)]),
                  right_forearm=dict(rot=[(0, -30, 0, 0), (1.0, -30, 0, 0)]),
                  left_forearm=dict(rot=[(0, -35, 0, 0), (1.0, -35, 0, 0)]),
                  cape=dict(rot=[(0, 25, 0, 0), (0.25, 31, 0, 2), (0.5, 26, 0, -2), (0.75, 32, 0, 1), (1.0, 25, 0, 0)]),
                  cape_lower=dict(rot=[(0, 15, 0, 0), (0.3, 22, 0, 0), (0.6, 14, 0, 0), (1.0, 15, 0, 0)]),
                  base=dict(pos=[(0, 0, 0.4, 0), (0.5, 0, 0.8, 0), (1.0, 0, 0.4, 0)]),
                  right_leg=dict(rot=[(0, -10, 0, 8), (1.0, -10, 0, 8)]),
                  left_leg=dict(rot=[(0, 10, 0, -8), (1.0, 10, 0, -8)]),
                  floating_crown=dict(pos=[(0, 0, 2, 0), (0.5, 0, 3, 0), (1.0, 0, 2, 0)])))
    A.append(anim('charge', 1.2,
                  chest=dict(rot=[(0, 0, 0, 0), (0.2, 28, 0, 0), (1.0, 28, 0, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, -22, 0, 0), (1.0, -22, 0, 0), (1.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.2, 40, 0, 15), (1.0, 40, 0, 15), (1.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.2, -20, 0, 0), (1.0, -20, 0, 0), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.2, -45, 0, -10), (1.0, -45, 0, -10), (1.2, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.2, -40, 0, 0), (0.5, 40, 0, 0), (0.8, -40, 0, 0), (1.1, 30, 0, 0), (1.2, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.2, 40, 0, 0), (0.5, -40, 0, 0), (0.8, 40, 0, 0), (1.1, -30, 0, 0), (1.2, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.35, 50, 0, 0), (0.65, 10, 0, 0), (0.95, 50, 0, 0), (1.2, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.2, 10, 0, 0), (0.5, 50, 0, 0), (0.8, 10, 0, 0), (1.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, -1, -1), (0.35, 0, 0.5, -2), (0.5, 0, -1, -2), (0.65, 0, 0.5, -2),
                                 (0.8, 0, -1, -2), (1.0, 0, 0, -1), (1.2, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.2, 45, 0, 0), (1.0, 50, 0, 0), (1.2, 0, 0, 0)]),
                  cape_lower=dict(rot=[(0, 0, 0, 0), (0.3, 25, 0, 0), (1.0, 30, 0, 0), (1.2, 0, 0, 0)])))
    A.append(anim('summon', 1.8, impact=1.0,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -150, 0, 5), (0.75, -152, 0, 5), (1.0, -40, 0, 0), (1.4, -40, 0, 0), (1.8, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, 15, 0, 0), (1.0, 72, 0, 0), (1.4, 72, 0, 0), (1.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -150, 0, -8), (1.0, -45, 0, -5), (1.4, -45, 0, -5), (1.8, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.6, 10, 0, 0), (1.0, 65, 0, 0), (1.4, 65, 0, 0), (1.8, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.6, -12, 0, 0), (1.0, 30, 0, 0), (1.4, 28, 0, 0), (1.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -25, 0, 0), (1.0, 0, 0, 0), (1.4, -15, 0, 0), (1.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 1, 0), (1.0, 0, -4, -1), (1.4, 0, -4, -1), (1.8, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (1.0, -50, 0, 0), (1.4, -50, 0, 0), (1.8, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (1.0, 60, 0, 0), (1.4, 60, 0, 0), (1.8, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (1.0, 10, 0, 0), (1.4, 10, 0, 0), (1.8, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.0, 55, 0, 0), (1.4, 55, 0, 0), (1.8, 0, 0, 0)])))
    A.append(anim('phase_change', 2.4,
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.5, -80, 0, 0), (0.9, -80, 0, 0), (1.4, 0, 0, 0), (2.4, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.5, 92, 0, 0), (0.9, 92, 0, 0), (1.4, 0, 0, 0), (2.4, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 0), (0.9, -10, 0, 0), (1.4, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.5, 75, 0, 0), (0.9, 75, 0, 0), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, -7, 0), (0.9, 0, -7, 0), (1.4, 0, 1.5, 0), (1.9, 0, 1.0, 0), (2.4, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.5, 25, 0, 0), (0.9, 30, 0, 0), (1.4, -18, 0, 0), (1.9, -14, 0, 0), (2.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, 30, 0, 0), (0.9, 35, 0, 0), (1.4, -32, 0, 0), (1.9, -25, 0, 0), (2.4, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 10), (0.9, -10, 0, 10), (1.4, -40, 0, 85), (1.9, -40, 0, 80), (2.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, -10), (0.9, -10, 0, -10), (1.4, -40, 0, -85), (1.9, -40, 0, -80), (2.4, 0, 0, 0)]),
                  floating_crown=dict(pos=[(0, 0, 0, 0), (0.9, 0, -2, 0), (1.4, 0, 6, 0), (2.4, 0, 0, 0)],
                                      rot=[(0, 0, 0, 0), (1.4, 0, 360, 0), (2.4, 0, 360, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.9, 5, 0, 0), (1.4, 45, 0, 0), (2.4, 0, 0, 0)]),
                  cape_lower=dict(rot=[(0, 0, 0, 0), (1.4, 30, 0, 0), (2.4, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.9, 0.5, 0.5, 1), (1.4, 2.2, 2.2, 1.5), (2.4, 1, 1, 1)])))
    A.append(anim('stagger', 1.2,
                  chest=dict(rot=[(0, 0, 0, 0), (0.15, -26, 0, 6), (0.6, -16, 0, 3), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.15, -32, 8, 0), (0.6, -15, 0, 0), (1.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.15, -65, 0, 42), (0.6, -30, 0, 20), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.15, -45, 0, -55), (0.6, -20, 0, -25), (1.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.15, 0, -0.6, 3.0), (0.6, 0, -1.0, 3.6), (1.2, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.15, 28, 0, 0), (0.6, 20, 0, 0), (1.2, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.15, -18, 0, 0), (0.6, -12, 0, 0), (1.2, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.15, 25, 0, 0), (1.2, 0, 0, 0)]),
                  greatsword=dict(rot=[(0, 0, 0, 0), (0.15, 25, 0, 0), (1.2, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.15, -10, 0, 0), (0.6, 15, 0, 0), (1.2, 0, 0, 0)])))
    A.append(anim('death', 3.2,
                  chest=dict(rot=[(0, 0, 0, 0), (0.5, -22, 0, 0), (1.4, 8, 0, 0), (2.1, 36, 0, 0), (3.2, 30, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -32, 0, 0), (1.4, 10, 0, 0), (2.1, 42, 0, 10), (3.2, 30, 0, 18)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, 35), (1.4, -12, 0, 30), (2.1, 0, 0, 12), (2.8, -150, 0, 20), (3.2, -160, 0, 20)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.4, 35, 0, 0), (3.2, 10, 0, 0)]),
                  greatsword=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.4, 50, 0, 25), (3.2, 50, 0, 25)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -30, 0, -40), (1.4, -10, 0, -20), (2.1, 5, 0, -8), (2.8, -140, 0, -25), (3.2, -150, 0, -25)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.6, 10, 0, 0), (1.4, -85, 0, 0), (2.2, -85, 0, 0), (2.8, 0, 0, 0), (3.2, 0, 0, 4)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.6, -10, 0, 0), (1.4, -80, 0, 0), (2.2, -80, 0, 0), (2.8, 0, 0, 0), (3.2, 0, 0, -4)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (1.4, 95, 0, 0), (2.2, 95, 0, 0), (2.8, 10, 0, 0), (3.2, 8, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.4, 92, 0, 0), (2.2, 92, 0, 0), (2.8, 10, 0, 0), (3.2, 8, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 0, 1.5), (1.4, 0, -8.5, 1.5), (2.2, 0, -8.5, 1.5), (2.8, 0, -2, -1), (3.2, 0, -2.5, -1)],
                            rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.8, 84, 0, 0), (2.95, 78, 0, 0), (3.2, 86, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.5, 20, 0, 0), (2.2, 5, 0, 0), (2.8, -40, 0, 0), (3.2, -30, 0, 0)]),
                  cape_lower=dict(rot=[(0, 0, 0, 0), (2.8, -20, 0, 0), (3.2, -10, 0, 0)]),
                  floating_crown=dict(pos=[(0, 0, 0, 0), (1.4, 0, 2, 0), (2.4, 0, -12, -6), (3.2, 0, -14, -8)],
                                      rot=[(0, 0, 0, 0), (2.4, 30, 0, 50), (3.2, 40, 0, 70)]),
                  core=dict(scale=[(0, 1, 1, 1), (1.0, 2.0, 2.0, 1.5), (2.2, 0.3, 0.3, 1), (3.2, 0.1, 0.1, 1)])))
    return A


def pal(**over):
    p = {'face': '#c49070', 'crown': '#d8b040', 'crown_spike': '#e8c050', 'helm': '#7a7e88', 'helm_crest': '#5a5e68',
         'horn': '#d8cfb8', 'horn_tip': '#b8af98', 'beard': '#5a3a24', 'fcrown': '#e0c050', 'fcrown_spike': '#f0d060',
         'grip': '#4a3020', 'guard': '#c8a040', 'blade': '#c8d0d8', 'pommel': '#c8a040', 'sleeve': '#7a2a24',
         'pauldron': '#8a8e98', 'spike': '#5a5e68', 'gauntlet': '#8a8e98', 'cuirass': '#8a8e98', 'cape': '#8a1e1e',
         'cape2': '#8a1e1e', 'core': '#c060ff', 'halo': '#2a2030', 'greave': '#7a7e88', 'greave2': '#7a7e88',
         'sabaton': '#5a5e68', 'faulds': '#6a6e78', 'tasset': '#7a2a24'}
    p.update(over)
    return p


def cuirass_detail(p, trim, emblem=None, glow=None):
    f = p.face('chest', 'front')
    f.vline(f.w // 2, 1, f.h - 2, shade(f.get(f.w // 2, 3), 0.7))
    for y in range(2, f.h - 2, 4):
        f.hline(2, f.w - 3, y, lighten(f.get(3, y), 0.15))
    f.hline(0, f.w - 1, 0, trim)
    f.hline(0, f.w - 1, f.h - 1, trim)
    if emblem:
        cx, cy = f.w // 2, 5
        for (dx, dy) in emblem:
            f.px(cx + dx, cy + dy, trim)
            if glow is not None:
                p.face('chest', 'front', glow=True).px(cx + dx, cy + dy, glow)


CROWN_SHAPE = [(-2, 0), (-1, 0), (0, 0), (1, 0), (-2, -1), (0, -1), (-2, -2), (0, -2), (-1, 1), (0, 1), (-1, 2)]


def tatter(p, part, depth=3, every=2):
    for side in ('front', 'back'):
        fc = p.face(part, side)
        for x in range(fc.w):
            d = (x * 7 % 5) % depth if x % every == 0 else 0
            for k in range(d):
                fc.clear(x, fc.h - 1 - k)


def variants():
    V = []

    def deco_b1(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#c49070'), eye_col=(60, 30, 20), brows=(50, 30, 20), mouth=False)
        f.px(2, 3, (120, 40, 30))
        f.hline(1, 3, 2, (60, 30, 20))
        cuirass_detail(p, (200, 160, 60), emblem=CROWN_SHAPE)
        g = p.face('head', 'front', glow=True)
        g.px(2, 4, (255, 240, 200))
        g.px(6, 4, (255, 240, 200))
        for fc in p.all_faces('crown', index=0, sides_only=True):
            for x in range(1, fc.w - 1, 3):
                fc.px(x, 0, (200, 40, 40))
                p.face('crown', fc.face, index=0, glow=True).px(x, 0, (255, 230, 200))
        tatter(p, 'cape_lower')
        for fc in p.all_faces('chest', sides_only=True):
            if fc.face != 'front':
                for y in range(1, fc.h, 5):
                    fc.hline(0, fc.w - 1, y, (90, 60, 40))
    leather = {'base': '#6a4a30', 'style': 'leather'}
    pal_b1 = pal(
        cuirass={'base': '#7a5a3a', 'style': 'leather'}, pauldron={'base': '#8a8e98', 'style': 'metal'},
        gauntlet=leather, greave=leather, greave2={'base': '#5a3e28', 'style': 'leather'}, faulds=leather,
        sabaton={'base': '#4a3020', 'style': 'leather'}, sleeve='#7a2a24', cape={'base': '#8a1e1e', 'trim': (200, 160, 60)},
        cape2={'base': '#7a1a1a', 'trim': (200, 160, 60)}, blade={'base': '#a8b0b8', 'style': 'metal'},
        core={'base': '#3a1030', 'style': 'crystal'})
    V.append(Variant('b1', show={'crown', 'beard'}, palette=pal_b1, paint=deco_b1, glow=True,
                     extra=dict(scale=1.0, hitbox=(1.4, 2.8))))

    def deco_b1_rage(p):
        # second form: the black crystal on his chest flares, greed burns in his eyes, the cape is in tatters
        deco_b1(p)
        glow_faces(p, 'core', (255, 70, 140))
        g = p.face('head', 'front', glow=True)
        g.px(2, 4, (255, 80, 60))
        g.px(6, 4, (255, 80, 60))
        veins(p, ('chest', 'right_arm', 'left_arm', 'right_leg', 'left_leg'), (210, 40, 90), (255, 90, 150))
        cracks(p, ('chest', 'hips'), (240, 70, 120), (255, 120, 170), every=36)
        for fc in p.all_faces('crown', index=0, glow=True, sides_only=True):
            for x in range(0, fc.w, 2):
                fc.px(x, 0, (255, 120, 80))
        tatter(p, 'cape', depth=3, every=2)
        tatter(p, 'cape_lower', depth=5)
    V.append(Variant('b1_p1', show={'crown', 'beard', 'core', 'spikes_r', 'spikes_l'},
                     palette=dict(pal_b1, spike={'base': '#2a1020', 'style': 'crystal'}),
                     poses={'spikes_r': dict(scale=(0.8, 0.9, 0.8)), 'spikes_l': dict(scale=(0.8, 0.9, 0.8))},
                     paint=deco_b1_rage, glow=True, extra=dict(scale=1.0)))

    def deco_b8(p):
        hf = p.face('helm', 'front')
        hf.hline(1, hf.w - 2, 4, (10, 12, 14))
        hf.hline(2, hf.w - 3, 5, (10, 12, 14))
        hf.vline(hf.w // 2, 6, hf.h - 2, (10, 12, 14))
        g = p.face('helm', 'front', glow=True)
        for x in (2, 3, 5, 6):
            g.px(x, 4, (220, 255, 250))
        cuirass_detail(p, (90, 220, 210), emblem=[(0, -2), (0, -1), (0, 0), (0, 1), (0, 2), (-1, 0), (1, 0), (-2, -1), (2, -1)],
                       glow=(200, 255, 250))
        for fc in p.all_faces('greatsword', index=2, glow=True, sides_only=True):
            if fc.face in ('right', 'left'):
                for x in range(1, fc.w - 1, 3):
                    fc.px(x, fc.h // 2, (200, 255, 250))
        tatter(p, 'cape_lower', depth=4)
        tatter(p, 'cape', depth=2, every=3)
    dark = {'base': '#3c4048', 'style': 'metal'}
    pal_b8 = pal(
        helm={'base': '#454a54', 'style': 'metal'}, helm_crest='#2a2e34', horn='#2a2e34', horn_tip='#1a1e24',
        cuirass=dark, pauldron={'base': '#454a54', 'style': 'metal'}, gauntlet=dark, greave=dark, greave2=dark,
        sabaton={'base': '#2a2e34', 'style': 'metal'}, faulds=dark, sleeve='#22262c', spike='#1a1e24',
        cape={'base': '#1e3a3a', 'style': 'cloth'}, cape2={'base': '#162e2e', 'style': 'cloth'}, tasset='#1e3a3a',
        blade={'base': '#5a6470', 'style': 'metal'}, guard='#2a2e34', pommel='#2a2e34',
        core={'base': '#5ae8d8', 'style': 'glowcore'}, halo={'base': '#1e2a2a', 'style': 'metal'})
    V.append(Variant('b8', show={'helm', 'horn_r', 'horn_l', 'spikes_r', 'spikes_l'}, palette=pal_b8,
                     paint=deco_b8, glow=True, extra=dict(scale=1.45, hitbox=(1.9, 4.0))))

    def deco_b8_form(form):
        # the gatekeeper's oath burns brighter with every form: a void heart, a halo of blades, cracks of light
        def deco(p):
            deco_b8(p)
            glow_faces(p, 'core', (220, 255, 250))
            veins(p, ('chest', 'right_arm', 'left_arm'), (50, 150, 140), (200, 255, 250), seed=form)
            if form >= 2:
                for k in range(8):
                    for fc in p.all_faces('halo_%d' % k, glow=True, sides_only=True):
                        fc.vline(fc.w // 2, 0, fc.h - 1, (150, 255, 240))
                veins(p, ('right_forearm', 'left_forearm', 'right_leg', 'left_leg'), (50, 150, 140), (200, 255, 250),
                      seed=form + 3)
            if form >= 3:
                cracks(p, ('chest', 'hips', 'right_shin', 'left_shin'), (120, 255, 235), (230, 255, 252), every=30)
                tatter(p, 'cape', depth=3, every=2)
                tatter(p, 'cape_lower', depth=5)
        return deco
    b8_parts = {'helm', 'horn_r', 'horn_l', 'spikes_r', 'spikes_l', 'core'}
    V.append(Variant('b8_p1', show=b8_parts, palette=pal_b8, paint=deco_b8_form(1), glow=True, extra=dict(scale=1.45)))
    V.append(Variant('b8_p2', show=b8_parts | {'halo'}, palette=pal_b8, paint=deco_b8_form(2), glow=True,
                     extra=dict(scale=1.45)))
    V.append(Variant('b8_p3', show=b8_parts | {'halo'}, palette=pal_b8,
                     poses={'spikes_r': dict(scale=(1.4, 1.6, 1.4)), 'spikes_l': dict(scale=(1.4, 1.6, 1.4))},
                     paint=deco_b8_form(3), glow=True, extra=dict(scale=1.45)))

    def make_final(phase):
        def deco(p):
            f = p.face('head', 'front')
            f.rect(0, 0, f.w - 1, f.h - 1, (60, 50, 70))
            for y in range(f.h):
                f.px(0, y, (40, 32, 48))
                f.px(f.w - 1, y, (40, 32, 48))
            f.hline(1, 3, 4, (10, 6, 14))
            f.hline(5, 7, 4, (10, 6, 14))
            g = p.face('head', 'front', glow=True)
            for x in (1, 2, 3, 5, 6, 7):
                g.px(x, 4, (255, 255, 255) if phase < 2 else (255, 200, 255))
            f.hline(2, 6, 7, (20, 12, 26))
            cuirass_detail(p, (200, 160, 70), emblem=CROWN_SHAPE, glow=(255, 240, 255))
            if phase >= 2:
                for part in ('chest', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_forearm', 'left_forearm'):
                    for fc in p.all_faces(part, sides_only=True):
                        gfc = p.face(part, fc.face, glow=True)
                        x = (len(part) * 3) % max(1, fc.w)
                        for y in range(fc.h):
                            if 0 <= x < fc.w:
                                fc.px(x, y, (200, 120, 255))
                                gfc.px(x, y, (230, 180, 255))
                            x += (1 if (y * 5 + len(part)) % 3 == 0 else (-1 if (y * 7) % 4 == 0 else 0))
            if phase >= 3:
                for fc in p.all_faces('halo_0', glow=True, sides_only=True):
                    fc.vline(fc.w // 2, 0, fc.h - 1, (220, 160, 255))
            tatter(p, 'cape_lower', depth=3 + phase)
            if phase >= 2:
                tatter(p, 'cape', depth=2 + phase, every=2)
        return deco
    void = {'base': '#2a2232', 'style': 'metal'}
    final_pal = pal(face='#3c3248', crown='#c8a040', fcrown='#d8b048', cuirass=void,
                    pauldron={'base': '#342a3e', 'style': 'metal'}, gauntlet=void, greave=void, greave2=void,
                    sabaton={'base': '#1e1826', 'style': 'metal'}, faulds=void, sleeve='#3a1a4a', spike='#c8a040',
                    cape={'base': '#4a1060', 'trim': (200, 160, 70)}, cape2={'base': '#3a0c4c', 'trim': (200, 160, 70)},
                    tasset='#4a1060', blade={'base': '#3a3048', 'style': 'metal'}, guard='#c8a040', pommel='#c8a040',
                    core={'base': '#d070ff', 'style': 'glowcore'}, halo={'base': '#1a1420', 'style': 'metal'})
    V.append(Variant('final', show={'crown', 'spikes_r', 'spikes_l'}, palette=final_pal, paint=make_final(0), glow=True,
                     extra=dict(scale=1.7, hitbox=(2.2, 4.7))))
    V.append(Variant('final_p1', show={'floating_crown', 'spikes_r', 'spikes_l'}, palette=final_pal, paint=make_final(1),
                     glow=True, extra=dict(scale=1.7)))
    V.append(Variant('final_p2', show={'floating_crown', 'spikes_r', 'spikes_l', 'core'}, palette=final_pal,
                     poses={'spikes_r': dict(scale=(1.4, 1.5, 1.4)), 'spikes_l': dict(scale=(1.4, 1.5, 1.4))},
                     paint=make_final(2), glow=True, extra=dict(scale=1.7)))
    V.append(Variant('final_p3', show={'floating_crown', 'spikes_r', 'spikes_l', 'core', 'halo'}, hide={'cape_lower'},
                     palette=final_pal, poses={'spikes_r': dict(scale=(1.5, 1.7, 1.5)), 'spikes_l': dict(scale=(1.5, 1.7, 1.5))},
                     paint=make_final(3), glow=True, extra=dict(scale=1.7)))
    return V


def rig():
    r = Rig('king', 'KingBoss', geometry(), animations(), variants(), SLOTS)
    r.preview_ppu = 3.0
    return r
