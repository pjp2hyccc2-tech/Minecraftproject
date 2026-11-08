"""QUEEN boss rig (tall humanoid): b5 氷獄の女王 (floating gown, crystal crown, long hair, ice scepter, ice collar,
orbiting shards) and b4 凍獄の処刑人 (hooded executioner: legs, leather apron, pauldrons, two-handed axe).
Two animation sets on one skeleton: the queen glides and casts, the executioner stomps and chops."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import human_face, lighten, rgb, shade

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']


def geometry():
    head = P('head', (0, -13, -0.5), C(-4, -21, -4.5, 8, 8, 8, 'skin:face'),
             P('crown', (0, -21, -0.5), C(-4, -24, -3.5, 1, 3, 1, 'crystal:crown', tag='c1'), C(-2.5, -26, -3.5, 1, 5, 1, 'crystal:crown', tag='c2'),
               C(-0.5, -29, -3.8, 1, 8, 1, 'crystal:crown_c'), C(1.5, -26, -3.5, 1, 5, 1, 'crystal:crown', share='c2'),
               C(3, -24, -3.5, 1, 3, 1, 'crystal:crown', share='c1'), opt=True),
             P('hair', (0, -21, 0), C(-4.5, -21.5, -1, 9, 16, 5, 'hair:hair'), rot=(6, 0, 0), opt=True),
             P('hood', (0, -17, -0.5), C(-4, -21, -4.5, 8, 8, 8, 'cloth:hood', inflate=0.6),
               C(-2, -25, -2, 4, 4, 4, 'cloth:hood_tip'), opt=True))
    right_arm = P('right_arm', (-7.5, -11, 0), C(-10, -13, -2, 4, 10, 4, 'cloth:sleeve', tag='ua'),
                  P('pauldron_r', (-8, -13, 0), C(-11, -14.5, -3, 6, 4, 6, 'metal:pauldron', tag='paul'), opt=True),
                  P('right_forearm', (-8, -3, 0), C(-10, -3, -2, 4, 9, 4, 'skin:arm', tag='fa'),
                    P('sleeve_r', (-8, 1, 0), C(-11, 1, -3, 6, 6, 6, 'cloth:cuff', tag='cuff'), opt=True),
                    P('scepter', (-8, 5, 0), C(-8.5, -15, -0.5, 1, 26, 1, 'crystal:staff'), C(-9.5, -21, -1.5, 3, 6, 3, 'crystal:gem'),
                      opt=True),
                    P('axe', (-8, 5, 0), C(-8.5, 4.5, -18, 1, 1, 22, 'wood:haft'), C(-9, 2, -22, 2, 11, 6, 'metal:axe'),
                      C(-8.5, 1, -23, 1, 2, 1, 'metal:axe_tip'), rot=(40, 0, 0), opt=True)))
    left_arm = P('left_arm', (7.5, -11, 0), C(6, -13, -2, 4, 10, 4, 'cloth:sleeve', share='ua', mirror=True),
                 P('pauldron_l', (8, -13, 0), C(5, -14.5, -3, 6, 4, 6, 'metal:pauldron', share='paul', mirror=True), opt=True),
                 P('left_forearm', (8, -3, 0), C(6, -3, -2, 4, 9, 4, 'skin:arm', share='fa', mirror=True),
                   P('sleeve_l', (8, 1, 0), C(5, 1, -3, 6, 6, 6, 'cloth:cuff', share='cuff', mirror=True), opt=True)))
    shards = P('shard_ring', (0, -6, 0), C(-14, -9, -1, 2, 5, 2, 'crystal:shard', tag='sh'), C(12, -11, -1, 2, 5, 2, 'crystal:shard', share='sh'),
               C(-1, -10, 12, 2, 5, 2, 'crystal:shard', share='sh'), opt=True)
    torso = P('torso', (0, 1, 0), C(-6, -13, -3.5, 12, 14, 7, 'cloth:bodice'),
              head, right_arm, left_arm, shards,
              P('collar', (0, -13, 2), C(-6, -20, 1.5, 12, 7, 1, 'crystal:collar'), rot=(-15, 0, 0), opt=True))
    legs = []
    for s in (-1, 1):
        name = 'right' if s < 0 else 'left'
        kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
        x = 2.5 * s
        legs.append(P('%s_leg' % name, (x, 6, 0), C(x - 2.5, 5, -2.5, 5, 9, 5, 'cloth:pants', **kw('thigh')),
                      P('%s_shin' % name, (x, 14, 0), C(x - 2.5, 14, -2.5, 5, 10, 5, 'leather:boots', **kw('shin'))), opt=True))
    hips = P('hips', (0, 4, 0), C(-5, 1, -3, 10, 5, 6, 'cloth:waist'), torso, *legs,
             P('gown', (0, 5, 0), C(-6, 5, -4, 12, 8, 8, 'cloth:gown'),
               P('gown_lower', (0, 13, 0), C(-8, 13, -6, 16, 9, 12, 'cloth:gown2')), opt=True),
             P('apron', (0, 5, -3), C(-4.5, 5, -3.6, 9, 13, 1, 'leather:apron'), opt=True))
    return [P('base', (0, 24, 0), hips)]


def queen_anims():
    A = []
    A.append(anim('idle', 3.0, loop=True,
                  base=dict(pos=[(0, 0, 0.8, 0), (1.5, 0, 1.8, 0), (3.0, 0, 0.8, 0)]),
                  gown=dict(rot=[(0, 0, 0, 1), (1.5, 1.5, 0, -1), (3.0, 0, 0, 1)]),
                  gown_lower=dict(rot=[(0, 2, 0, -1.5), (1.5, -1, 0, 1.5), (3.0, 2, 0, -1.5)]),
                  torso=dict(rot=[(0, 0, 0, 0), (1.5, -2, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, -2), (1.5, 2, 0, 2), (3.0, 0, 0, -2)]),
                  right_arm=dict(rot=[(0, -10, 0, 6), (1.5, -14, 0, 8), (3.0, -10, 0, 6)]),
                  right_forearm=dict(rot=[(0, -20, 0, 0), (1.5, -26, 0, 0), (3.0, -20, 0, 0)]),
                  left_arm=dict(rot=[(0, -6, 0, -10), (1.5, -10, 0, -14), (3.0, -6, 0, -10)]),
                  left_forearm=dict(rot=[(0, -30, 0, 0), (1.5, -40, 0, 0), (3.0, -30, 0, 0)]),
                  hair=dict(rot=[(0, 0, 0, 0), (1.5, 4, 0, 0), (3.0, 0, 0, 0)]),
                  shard_ring=dict(rot=[(0, 0, 0, 0), (3.0, 0, 120, 0)], pos=[(0, 0, 0, 0), (1.5, 0, 1.2, 0), (3.0, 0, 0, 0)],
                                  rot_interp='linear')))
    A.append(anim('q_walk', 1.6, loop=True,
                  torso=dict(rot=[(0, 8, 0, 0), (0.8, 10, 0, 0), (1.6, 8, 0, 0)]),
                  gown_lower=dict(rot=[(0, -12, 0, 2), (0.8, -14, 0, -2), (1.6, -12, 0, 2)]),
                  gown=dict(rot=[(0, -4, 0, 0), (1.6, -4, 0, 0)]),
                  hair=dict(rot=[(0, 12, 0, 0), (0.8, 16, 0, 0), (1.6, 12, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.8, 0, 0.5, 0), (1.6, 0, 0, 0)])))
    A.append(anim('q_slam', 1.4, impact=0.8,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -165, 0, 10), (0.8, -40, 0, 0), (1.05, -38, 0, 0), (1.4, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 0), (0.8, 10, 0, 0), (1.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, -50), (0.8, -10, 0, -35), (1.4, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.5, -15, 0, 0), (0.8, 22, 0, 0), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -15, 0, 0), (0.8, 12, 0, 0), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 2.5, 0.5), (0.8, 0, -1.0, -1.5), (1.05, 0, -0.8, -1.5), (1.4, 0, 0, 0)]),
                  gown_lower=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 0), (0.8, 15, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('q_sweep', 1.3, impact=0.65,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.4, -80, 55, 10), (0.65, -80, -65, 0), (0.85, -75, -70, 0), (1.3, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.4, 0, 32, 0), (0.65, 4, -38, 0), (0.85, 4, -34, 0), (1.3, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.4, -20, 0, -40), (0.65, -30, 0, -60), (1.3, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.4, 0, -20, 0), (0.65, 0, 20, 0), (1.3, 0, 0, 0)]),
                  gown_lower=dict(rot=[(0, 0, 0, 0), (0.4, 0, 0, -8), (0.65, 0, 0, 10), (1.3, 0, 0, 0)]),
                  base=dict(rot=[(0, 0, 0, 0), (0.4, 0, 15, 0), (0.65, 0, -15, 0), (1.3, 0, 0, 0)])))
    A.append(anim('q_cast', 1.6, impact=0.9,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.55, -125, 0, 32), (0.9, -85, 0, 8), (1.2, -80, 0, 6), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.55, -125, 0, -32), (0.9, -85, 0, -8), (1.2, -80, 0, -6), (1.6, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.55, -14, 0, 0), (0.9, 10, 0, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.55, -22, 0, 0), (0.9, 5, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.55, 0, 2, 0), (0.9, 0, 1, -1), (1.6, 0, 0, 0)]),
                  shard_ring=dict(rot=[(0, 0, 0, 0), (1.6, 0, 480, 0)], scale=[(0, 1, 1, 1), (0.55, 0.6, 1, 0.6), (0.9, 1.3, 1, 1.3), (1.6, 1, 1, 1)],
                                  rot_interp='linear'),
                  hair=dict(rot=[(0, 0, 0, 0), (0.9, 25, 0, 0), (1.6, 0, 0, 0)])))
    tremble = [(round(i * 0.1, 2), 0, 0, (1.5 if i % 2 else -1.5)) for i in range(13)]
    A.append(anim('q_roar', 1.2, loop=True,
                  right_arm=dict(rot=[(0, -35, 0, 70), (0.6, -40, 0, 76), (1.2, -35, 0, 70)]),
                  left_arm=dict(rot=[(0, -35, 0, -70), (0.6, -40, 0, -76), (1.2, -35, 0, -70)]),
                  torso=dict(rot=[(t, -12, 0, z) for (t, _, __, z) in tremble]),
                  head=dict(rot=[(0, -22, 0, 0), (0.6, -26, 0, 0), (1.2, -22, 0, 0)]),
                  gown_lower=dict(scale=[(0, 1.08, 1, 1.08), (0.6, 1.14, 1, 1.14), (1.2, 1.08, 1, 1.08)]),
                  base=dict(pos=[(0, 0, 2, 0), (0.6, 0, 2.6, 0), (1.2, 0, 2, 0)]),
                  hair=dict(rot=[(0, 20, 0, 0), (0.6, 28, 0, 0), (1.2, 20, 0, 0)]),
                  shard_ring=dict(rot=[(0, 0, 0, 0), (1.2, 0, 240, 0)], lin=True)))
    A.append(anim('q_charge', 1.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.2, 28, 0, 0), (0.8, 28, 0, 0), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, -20, 0, 0), (0.8, -20, 0, 0), (1.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.2, 45, 0, 20), (0.8, 45, 0, 20), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.2, 45, 0, -20), (0.8, 45, 0, -20), (1.0, 0, 0, 0)]),
                  gown_lower=dict(rot=[(0, 0, 0, 0), (0.2, -30, 0, 0), (0.8, -30, 0, 0), (1.0, 0, 0, 0)]),
                  hair=dict(rot=[(0, 0, 0, 0), (0.2, 50, 0, 0), (0.8, 55, 0, 0), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, 1.5, -1), (0.8, 0, 1.5, -3), (1.0, 0, 0, 0)])))
    A.append(anim('q_summon', 1.8, impact=1.0,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -175, 0, 5), (1.0, -175, 0, 5), (1.3, -40, 0, 20), (1.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -150, 0, -30), (1.0, -150, 0, -30), (1.3, -40, 0, -20), (1.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, 0), (1.3, 15, 0, 0), (1.8, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.6, -10, 0, 0), (1.3, 15, 0, 0), (1.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 3, 0), (1.0, 0, 3.4, 0), (1.3, 0, 0, 0), (1.8, 0, 0, 0)]),
                  shard_ring=dict(pos=[(0, 0, 0, 0), (1.0, 0, 6, 0), (1.3, 0, -4, 0), (1.8, 0, 0, 0)])))
    A.append(anim('q_phase', 2.2,
                  base=dict(rot=[(0, 0, 0, 0), (1.4, 0, 360, 0), (2.2, 0, 360, 0)], pos=[(0, 0, 0, 0), (0.7, 0, 3, 0), (1.4, 0, 3.5, 0), (2.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.4, -10, 0, 85), (1.4, -10, 0, 85), (2.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.4, -10, 0, -85), (1.4, -10, 0, -85), (2.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.7, -25, 0, 0), (1.4, -25, 0, 0), (2.2, 0, 0, 0)]),
                  gown_lower=dict(scale=[(0, 1, 1, 1), (0.7, 1.3, 0.9, 1.3), (1.4, 1.3, 0.9, 1.3), (2.2, 1, 1, 1)]),
                  hair=dict(rot=[(0, 0, 0, 0), (0.7, 40, 0, 0), (1.4, 40, 0, 0), (2.2, 0, 0, 0)]),
                  shard_ring=dict(scale=[(0, 1, 1, 1), (1.0, 1.6, 1, 1.6), (2.2, 1, 1, 1)])))
    A.append(anim('q_stagger', 1.0,
                  base=dict(pos=[(0, 0, 0, 0), (0.15, 0, -1.5, 2), (0.6, 0, -1, 2.5), (1.0, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.15, -22, 0, 6), (0.6, -12, 0, 3), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.15, -28, 8, 0), (1.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.15, -50, 0, 45), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.15, -40, 0, -50), (1.0, 0, 0, 0)]),
                  gown_lower=dict(rot=[(0, 0, 0, 0), (0.15, 15, 0, 0), (1.0, 0, 0, 0)])))
    A.append(anim('q_death', 3.0,
                  base=dict(pos=[(0, 0, 0, 0), (1.2, 0, 4.5, 0), (2.1, 0, 4.5, 0), (2.6, 0, -4, 0), (3.0, 0, -6, 0)],
                            scale=[(0, 1, 1, 1), (2.1, 1, 1, 1), (2.6, 1.15, 0.6, 1.15), (3.0, 1.3, 0.25, 1.3)]),
                  torso=dict(rot=[(0, 0, 0, 0), (1.2, -25, 0, 0), (1.5, -25, 0, 2), (1.6, -25, 0, -2), (1.7, -25, 0, 2), (2.1, -25, 0, 0), (3.0, 20, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.2, -35, 0, 0), (2.1, -35, 0, 0), (3.0, 30, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.2, -150, 0, 40), (2.1, -150, 0, 40), (3.0, -20, 0, 30)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.2, -150, 0, -40), (2.1, -150, 0, -40), (3.0, -20, 0, -30)]),
                  shard_ring=dict(pos=[(0, 0, 0, 0), (1.2, 0, 4, 0), (2.1, 0, 4, 0), (3.0, 0, -10, 0)],
                                  scale=[(0, 1, 1, 1), (2.1, 1.8, 1, 1.8), (3.0, 2.4, 1, 2.4)]),
                  hair=dict(rot=[(0, 0, 0, 0), (1.2, 30, 0, 0), (3.0, 10, 0, 0)])))
    return A


def exec_anims():
    A = []
    A.append(anim('x_idle', 3.5, loop=True,
                  torso=dict(rot=[(0, 4, 0, 0), (1.75, 1, 0, 0), (3.5, 4, 0, 0)], scale=[(0, 1, 1, 1), (1.75, 1.03, 1.02, 1.04), (3.5, 1, 1, 1)]),
                  head=dict(rot=[(0, 6, 0, 0), (1.75, 3, 6, 0), (3.5, 6, 0, 0)]),
                  right_arm=dict(rot=[(0, -5, 0, 5), (1.75, -8, 0, 7), (3.5, -5, 0, 5)]),
                  right_forearm=dict(rot=[(0, -15, 0, 0), (1.75, -20, 0, 0), (3.5, -15, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, -6), (1.75, -4, 0, -8), (3.5, 0, 0, -6)]),
                  left_forearm=dict(rot=[(0, -10, 0, 0), (1.75, -14, 0, 0), (3.5, -10, 0, 0)]),
                  apron=dict(rot=[(0, 0, 0, 0), (1.75, -3, 0, 0), (3.5, 0, 0, 0)])))
    A.append(anim('x_walk', 1.4, loop=True,
                  right_leg=dict(rot=[(0, 26, 0, 0), (0.7, -26, 0, 0), (1.4, 26, 0, 0)]),
                  left_leg=dict(rot=[(0, -26, 0, 0), (0.7, 26, 0, 0), (1.4, -26, 0, 0)]),
                  right_shin=dict(rot=[(0, 12, 0, 0), (0.35, 42, 0, 0), (0.7, 2, 0, 0), (1.4, 12, 0, 0)]),
                  left_shin=dict(rot=[(0, 2, 0, 0), (0.7, 12, 0, 0), (1.05, 42, 0, 0), (1.4, 2, 0, 0)]),
                  left_arm=dict(rot=[(0, 18, 0, 0), (0.7, -18, 0, 0), (1.4, 18, 0, 0)]),
                  right_arm=dict(rot=[(0, -8, 0, 0), (0.7, 8, 0, 0), (1.4, -8, 0, 0)]),
                  torso=dict(rot=[(0, 5, -5, 3), (0.7, 5, 5, -3), (1.4, 5, -5, 3)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.35, 0, 0.8, 0), (0.7, 0, 0, 0), (1.05, 0, 0.8, 0), (1.4, 0, 0, 0)]),
                  apron=dict(rot=[(0, -8, 0, 0), (0.7, 8, 0, 0), (1.4, -8, 0, 0)])))
    A.append(anim('x_slam', 1.6, impact=0.9,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.55, -172, 0, 6), (0.9, -40, 0, 0), (1.2, -36, 0, 0), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.55, -20, 0, 0), (0.9, 12, 0, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.55, -165, 0, -25), (0.9, -45, 0, -15), (1.2, -42, 0, -15), (1.6, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.55, -35, 0, 0), (0.9, -5, 0, 0), (1.6, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.55, -16, 8, 0), (0.9, 28, 0, 0), (1.2, 24, 0, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.55, -10, 0, 0), (0.9, 15, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.55, 0, 1, 0.5), (0.9, 0, -2.5, -1.5), (1.2, 0, -2.2, -1.5), (1.6, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.9, -35, 0, 0), (1.2, -32, 0, 0), (1.6, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.9, 48, 0, 0), (1.2, 45, 0, 0), (1.6, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.9, 22, 0, 0), (1.6, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.9, 30, 0, 0), (1.6, 0, 0, 0)])))
    A.append(anim('x_sweep', 1.5, impact=0.75,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.45, -60, 50, 85), (0.75, -85, -55, -10), (0.95, -80, -60, -12), (1.5, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.45, -80, 40, 40), (0.75, -85, -60, 10), (0.95, -80, -60, 10), (1.5, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.45, 0, 45, 0), (0.75, 6, -50, 0), (0.95, 6, -46, 0), (1.5, 0, 0, 0)]),
                  hips=dict(rot=[(0, 0, 0, 0), (0.45, 0, 12, 0), (0.75, 0, -15, 0), (1.5, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.45, 0, -30, 0), (0.75, 0, 25, 0), (1.5, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.45, 0, -0.5, 0.6), (0.75, 0, -1, -1.5), (1.5, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.75, -25, 0, 0), (1.5, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.75, 25, 0, 0), (1.5, 0, 0, 0)])))
    A.append(anim('x_cast', 1.6, impact=0.9,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -150, 0, 10), (0.9, -95, 0, 5), (1.2, -92, 0, 5), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, 15, 0, 0), (0.9, -40, 0, 0), (1.2, -40, 0, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -150, 0, -15), (0.9, -100, 0, -5), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, 0), (0.9, 12, 0, 0), (1.05, 0, 0, 0), (1.2, 12, 0, 0), (1.6, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.6, -12, 0, 0), (0.9, 8, 0, 0), (1.6, 0, 0, 0)])))
    tremble = [(round(i * 0.1, 2), 0, 0, (1.5 if i % 2 else -1.5)) for i in range(11)]
    A.append(anim('x_roar', 1.0, loop=True,
                  torso=dict(rot=[(t, -14, 0, z) for (t, _, __, z) in tremble]),
                  head=dict(rot=[(0, -28, 0, 0), (0.5, -32, 0, 0), (1.0, -28, 0, 0)]),
                  right_arm=dict(rot=[(0, -40, 0, 50), (0.5, -45, 0, 56), (1.0, -40, 0, 50)]),
                  left_arm=dict(rot=[(0, -30, 0, -55), (0.5, -35, 0, -60), (1.0, -30, 0, -55)]),
                  right_forearm=dict(rot=[(0, -40, 0, 0), (1.0, -40, 0, 0)]),
                  base=dict(pos=[(0, 0, 0.4, 0), (0.5, 0, 0.8, 0), (1.0, 0, 0.4, 0)])))
    A.append(anim('x_charge', 1.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.2, 32, 0, 0), (0.8, 32, 0, 0), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.2, -25, 0, 0), (0.8, -25, 0, 0), (1.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.2, 45, 0, 15), (0.8, 45, 0, 15), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.2, -55, 0, -10), (0.8, -55, 0, -10), (1.0, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.2, -45, 0, 0), (0.45, 45, 0, 0), (0.7, -45, 0, 0), (0.95, 30, 0, 0), (1.0, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.2, 45, 0, 0), (0.45, -45, 0, 0), (0.7, 45, 0, 0), (0.95, -30, 0, 0), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, -1, -1), (0.35, 0, 0.6, -2), (0.6, 0, -1, -2), (0.85, 0, 0.6, -2), (1.0, 0, 0, 0)])))
    A.append(anim('x_summon', 1.6, impact=0.9,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.55, -120, 0, 5), (0.9, -30, 0, 0), (1.2, -30, 0, 0), (1.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.55, 10, 0, 0), (0.9, 45, 0, 0), (1.2, 45, 0, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.55, -100, 0, -10), (0.9, -35, 0, -5), (1.6, 0, 0, 0)]),
                  torso=dict(rot=[(0, 0, 0, 0), (0.55, -10, 0, 0), (0.9, 25, 0, 0), (1.2, 22, 0, 0), (1.6, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.55, -45, 0, 0), (0.9, -10, 0, 0), (1.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.55, 0, 0.8, 0), (0.9, 0, -2, 0), (1.2, 0, -2, 0), (1.6, 0, 0, 0)])))
    A.append(anim('x_phase', 2.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.6, 20, 0, 0), (1.1, -22, 0, 0), (1.5, -18, 0, 0), (2.0, 0, 0, 0)],
                             scale=[(0, 1, 1, 1), (1.1, 1.1, 1.05, 1.1), (2.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 25, 0, 0), (1.1, -32, 0, 0), (2.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, 20), (1.1, -60, 0, 75), (1.5, -55, 0, 70), (2.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -20, 0, -20), (1.1, -60, 0, -75), (1.5, -55, 0, -70), (2.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.1, -60, 0, 0), (2.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (1.1, -70, 0, 0), (2.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, -1.5, 0), (1.1, 0, 0.8, 0), (2.0, 0, 0, 0)])))
    A.append(anim('x_stagger', 1.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.15, -26, 0, 6), (0.55, -14, 0, 3), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.15, -30, 8, 0), (1.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.15, -60, 0, 40), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.15, -45, 0, -55), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.15, 0, -0.6, 2.5), (0.55, 0, -1, 3), (1.0, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.15, 28, 0, 0), (1.0, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.15, 25, 0, 0), (1.0, 0, 0, 0)])))
    A.append(anim('x_death', 3.0,
                  torso=dict(rot=[(0, 0, 0, 0), (0.5, -20, 0, 0), (1.3, 8, 0, 0), (2.0, 38, 0, 0), (3.0, 30, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -30, 0, 0), (2.0, 40, 0, 10), (3.0, 30, 0, 15)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, 35), (1.3, 10, 0, 25), (2.6, -150, 0, 20), (3.0, -158, 0, 20)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.3, 35, 0, 0), (3.0, 10, 0, 0)]),
                  axe=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.3, 45, 0, 20), (3.0, 45, 0, 20)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -30, 0, -40), (1.3, 5, 0, -15), (2.6, -140, 0, -25), (3.0, -148, 0, -25)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (1.3, -82, 0, 0), (2.0, -82, 0, 0), (2.6, 0, 0, 0), (3.0, 0, 0, 3)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (1.3, -78, 0, 0), (2.0, -78, 0, 0), (2.6, 0, 0, 0), (3.0, 0, 0, -3)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (1.3, 92, 0, 0), (2.0, 92, 0, 0), (2.6, 8, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (1.3, 90, 0, 0), (2.0, 90, 0, 0), (2.6, 8, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 0, 1.5), (1.3, 0, -9, 1.5), (2.0, 0, -9, 1.5), (2.6, 0, -2, -1), (3.0, 0, -2.5, -1)],
                            rot=[(0, 0, 0, 0), (2.0, 0, 0, 0), (2.6, 84, 0, 0), (2.75, 78, 0, 0), (3.0, 86, 0, 0)])))
    return A


def variants():
    def deco_b5(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#e8e4f0'), eye_col=(60, 180, 255), brows=(200, 220, 240), mouth=True,
                   glow_fc=p.face('head', 'front', glow=True), glow_eyes=(150, 230, 255))
        f.px(3, 6, (120, 60, 140))
        f.px(4, 6, (120, 60, 140))
        for side in ('right', 'left', 'back', 'top'):
            fc = p.face('head', side)
            for y in range(fc.h if side != 'right' and side != 'left' else 5):
                for x in range(fc.w):
                    fc.px(x, y, shade(rgb('#e8f4ff'), 0.92 + 0.12 * ((x * 3 + y) % 3 == 0)))
        f.hline(0, f.w - 1, 0, rgb('#e8f4ff'))
        for part in ('crown', 'scepter', 'shard_ring', 'collar'):
            for i in range(len(p.rig.parts[part].cubes)):
                for fc in p.all_faces(part, index=i, glow=True):
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if (x + y) % 2 == 0:
                                fc.px(x, y, (120, 200, 255))
        for fc in p.all_faces('gown_lower', sides_only=True):
            for x in range(fc.w):
                if x % 3 != 1:
                    fc.px(x, fc.h - 1, (230, 250, 255))
                fc.px(x, fc.h - 2, (200, 235, 255))
        for fc in p.all_faces('torso', sides_only=True):
            if fc.face == 'front':
                for k in range(fc.h // 2):
                    fc.px(fc.w // 2 - 1 - k // 2, k, (220, 240, 255))
                    fc.px(fc.w // 2 + k // 2, k, (220, 240, 255))
    gown = {'base': '#5a8cc8', 'trim': (230, 248, 255)}
    pal5 = {'face': '#e8e4f0', 'crown': {'base': '#bfeaff', 'style': 'crystal'}, 'crown_c': {'base': '#d8f6ff', 'style': 'crystal'},
            'hair': {'base': '#e0f0ff', 'style': 'hair'}, 'sleeve': {'base': '#4a7cbc'}, 'arm': '#e8e4f0',
            'cuff': {'base': '#e0f4ff', 'style': 'ice'}, 'staff': {'base': '#d0f0ff', 'style': 'crystal'},
            'gem': {'base': '#80d8ff', 'style': 'crystal'}, 'shard': {'base': '#c8f0ff', 'style': 'crystal'},
            'bodice': {'base': '#3c6cb0', 'trim': (230, 248, 255)}, 'collar': {'base': '#d8f6ff', 'style': 'crystal'},
            'waist': '#3c6cb0', 'gown': gown, 'gown2': dict(gown, base='#4a7cc0')}

    def deco_b4(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (40, 34, 30))
        hf = p.face('hood', 'front')
        for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
            hf.px(x, y, (12, 8, 6))
        g = p.face('hood', 'front', glow=True)
        for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
            g.px(x, y, (160, 230, 255))
        hf.hline(2, 5, 6, (60, 50, 44))
        for fc in p.all_faces('apron', sides_only=True):
            if fc.face == 'front':
                for y in range(2, fc.h - 1, 3):
                    fc.px(2 + (y % 4), y, (90, 30, 26))
                    fc.px(5 - (y % 3), y + 1, (100, 34, 28))
        for fc in p.all_faces('torso', sides_only=True):
            for x in range(fc.w):
                y = (x if fc.face in ('front', 'back') else x + 3) % fc.h
                fc.px(x, y, (130, 136, 146))
                fc.px(x, (y + 1) % fc.h, (90, 96, 106))
        for fc in p.all_faces('axe', index=1, glow=True, sides_only=True):
            if fc.face in ('right', 'left'):
                fc.vline(0, 0, fc.h - 1, (170, 230, 255))
    pal4 = {'face': '#4a3c34', 'hood': {'base': '#2c2a34', 'style': 'cloth'}, 'hood_tip': '#2c2a34', 'sleeve': '#3a3434',
            'arm': {'base': '#8a7060', 'style': 'skin'}, 'pauldron': {'base': '#5a5e68', 'style': 'metal'},
            'haft': {'base': '#4a3422', 'style': 'wood'}, 'axe': {'base': '#9aa8b8', 'style': 'metal'},
            'axe_tip': {'base': '#9aa8b8', 'style': 'metal'}, 'bodice': {'base': '#4a4040', 'style': 'leather'},
            'waist': {'base': '#3a3030', 'style': 'leather'}, 'pants': '#2e2a2a', 'boots': {'base': '#2a2220', 'style': 'leather'},
            'apron': {'base': '#5a3a26', 'style': 'leather'}}
    return [Variant('b5', show={'crown', 'hair', 'sleeve_r', 'sleeve_l', 'scepter', 'shard_ring', 'collar', 'gown'},
                    palette=pal5, paint=deco_b5, glow=True, extra=dict(scale=1.2, hitbox=(1.3, 3.4))),
            Variant('b4', show={'hood', 'pauldron_r', 'pauldron_l', 'axe', 'right_leg', 'left_leg', 'apron'},
                    palette=pal4, paint=deco_b4, glow=True, extra=dict(scale=1.35, hitbox=(1.5, 3.8)))]


SETS = {
    'b5': dict(idle='idle', walk='q_walk', melee_slam='q_slam', melee_sweep='q_sweep', cast='q_cast', roar='q_roar',
               charge='q_charge', summon='q_summon', phase_change='q_phase', stagger='q_stagger', death='q_death'),
    'b4': dict(idle='x_idle', walk='x_walk', melee_slam='x_slam', melee_sweep='x_sweep', cast='x_cast', roar='x_roar',
               charge='x_charge', summon='x_summon', phase_change='x_phase', stagger='x_stagger', death='x_death'),
}


def rig():
    r = Rig('queen', 'QueenBoss', geometry(), queen_anims() + exec_anims(), variants(), SLOTS, anim_sets=SETS)
    r.preview_ppu = 3.0
    return r
