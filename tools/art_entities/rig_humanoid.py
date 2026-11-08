"""HUMANOID enemy rig: bandit, ghoul, mummy, cultist, prisoner, jailer, ice_knight, frost_mage, fire_imp,
dark_knight, void_mage, shade (+ crossbowman, dryad for the d1/d2 templates).
Player-sized (32 units), elbows and knees, optional hoods/helmets/robes/capes/weapons toggled per variant."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import human_face, lighten, mix, rgb, shade

SLOTS = ['idle', 'walk', 'attack1', 'attack2', 'cast', 'hurt', 'spawn', 'death']


def geometry():
    head = P('head', (0, 0, 0),
             C(-4, -8, -4, 8, 8, 8, 'skin'),
             P('hood', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'cloth:hood', inflate=0.5),
               C(-2, -7, 4.5, 4, 5, 2, 'cloth:hood'), opt=True),
             P('helmet', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'metal:helm', inflate=0.6),
               P('helm_crest', (0, -8.6, 0), C(-0.5, -12, -4, 1, 4, 9, 'cloth:plume'), opt=True), opt=True),
             P('horn_r', (-3, -8, -1), C(-4, -13, -2, 2, 5, 2, 'bone:horn'), rot=(0, 0, -22), opt=True),
             P('horn_l', (3, -8, -1), C(2, -13, -2, 2, 5, 2, 'bone:horn', mirror=True), rot=(0, 0, 22), opt=True),
             P('leaf_crown', (0, -8, 0), C(-5, -10, -5, 10, 4, 10, 'leaf:leaves'), opt=True))
    right_forearm = P('right_forearm', (-6, 6, 0),
                      C(-8, 6, -2, 4, 6, 4, 'cloth:sleeve2', tag='forearm'),
                      P('dagger', (-6, 11, 0), C(-6.5, 10.5, -2, 1, 1, 3, 'leather:grip', tag='dgrip'),
                        C(-6.5, 10, -7, 1, 2, 5, 'metal:blade', tag='dblade'), rot=(30, 0, 0), opt=True),
                      P('blade', (-6, 11, 0), C(-6.5, 10.5, -2, 1, 1, 3, 'leather:grip', tag='bgrip'),
                        C(-6.5, 9, -3, 1, 4, 1, 'metal:guard'), C(-6.5, 10, -13, 1, 2, 10, 'metal:blade'), rot=(30, 0, 0),
                        opt=True),
                      P('greatblade', (-6, 11, 0), C(-6.5, 10.5, -2, 1, 1, 4, 'leather:grip'),
                        C(-7, 8.5, -3, 2, 5, 1, 'metal:guard'), C(-6.5, 9.5, -17, 1, 3, 14, 'metal:blade'), rot=(30, 0, 0),
                        opt=True),
                      P('club', (-6, 11, 0), C(-6.5, 10.5, -8, 1, 1, 10, 'wood:haft'),
                        C(-7.5, 9.5, -12, 3, 3, 4, 'metal:club'), rot=(30, 0, 0), opt=True),
                      P('staff', (-6, 11, 0), C(-6.5, -5, -0.5, 1, 19, 1, 'wood:staff'),
                        C(-7.5, -9, -1.5, 3, 4, 3, 'gem:orb'), opt=True),
                      P('crossbow', (-6, 11, 0), C(-7, 9, -1.5, 2, 10, 2, 'wood:stock'),
                        C(-11, 17, -2, 10, 1, 1, 'wood:limb'), C(-6.5, 12, -2.5, 1, 9, 1, 'metal:bolt'), opt=True),
                      P('claws_r', (-6, 11, 0), C(-8, 11, -3, 4, 3, 1, 'bone:claw', tag='claw'), opt=True),
                      P('shackle_r', (-6, 9, 0), C(-8, 8, -2, 4, 2, 4, 'metal:iron', inflate=0.5, tag='shackle'),
                        C(-6.5, 10, 1.5, 1, 4, 1, 'chain:chain', tag='chainlink'), opt=True))
    left_forearm = P('left_forearm', (6, 6, 0),
                     C(4, 6, -2, 4, 6, 4, 'cloth:sleeve2', mirror=True, share='forearm'),
                     P('claws_l', (6, 11, 0), C(4, 11, -3, 4, 3, 1, 'bone:claw', mirror=True, share='claw'), opt=True),
                     P('shackle_l', (6, 9, 0), C(4, 8, -2, 4, 2, 4, 'metal:iron', inflate=0.5, mirror=True, share='shackle'),
                       C(5.5, 10, 1.5, 1, 4, 1, 'chain:chain', share='chainlink'), opt=True),
                     P('shield_l', (8, 9, 0), C(8.5, 4, -4, 1, 9, 8, 'metal:shield'), opt=True),
                     P('hand_flame', (6, 12, -1), C(4.5, 11.5, -3.5, 3, 3, 3, 'flame:fire'), opt=True),
                     P('mummy_strips', (6, 10, 0), C(8.2, 9, -1, 0, 5, 2, 'bandage:wrap'), opt=True))
    right_arm = P('right_arm', (-5, 2, 0), C(-8, 0, -2, 4, 6, 4, 'cloth:sleeve', tag='upperarm'),
                  P('pauldron_r', (-6, 0, 0), C(-9, -1, -3, 5, 3, 6, 'metal:armor', tag='pauldron'), opt=True),
                  right_forearm)
    left_arm = P('left_arm', (5, 2, 0), C(4, 0, -2, 4, 6, 4, 'cloth:sleeve', mirror=True, share='upperarm'),
                 P('pauldron_l', (6, 0, 0), C(4, -1, -3, 5, 3, 6, 'metal:armor', mirror=True, share='pauldron'), opt=True),
                 left_forearm)
    chest = P('chest', (0, 7, 0), C(-4, 0, -2, 8, 7, 4, 'cloth:tunic'),
              head, right_arm, left_arm,
              P('cape', (0, 0, 2.5), C(-4, 0, 2.5, 8, 15, 1, 'cloth:cape'), rot=(6, 0, 0), opt=True),
              P('chest_strips', (0, 3, -2), C(-3, 2, -2.3, 2, 7, 0, 'bandage:wrap'), C(1.5, 4, -2.3, 2, 6, 0, 'bandage:wrap'),
                opt=True))
    right_leg = P('right_leg', (-2, 12, 0), C(-4, 12, -2, 4, 6, 4, 'cloth:pants', tag='thigh'),
                  P('right_shin', (-2, 18, 0), C(-4, 18, -2, 4, 6, 4, 'leather:boots', tag='shin')))
    left_leg = P('left_leg', (2, 12, 0), C(0, 12, -2, 4, 6, 4, 'cloth:pants', mirror=True, share='thigh'),
                 P('left_shin', (2, 18, 0), C(0, 18, -2, 4, 6, 4, 'leather:boots', mirror=True, share='shin')))
    hips = P('hips', (0, 12, 0), C(-4, 7, -2, 8, 5, 4, 'cloth:belly'),
             chest, right_leg, left_leg,
             P('robe', (0, 12, 0), C(-4, 12, -2, 8, 10, 4, 'cloth:robe', inflate=0.6), opt=True),
             P('tail', (0, 11, 2), C(-0.5, 10.5, 2, 1, 1, 6, 'skin:tail'),
               P('tail_tip', (0, 11, 8), C(-1.5, 10.5, 8, 3, 1, 2, 'skin:tail')), rot=(-35, 0, 0), opt=True))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------- animations

def animations():
    A = []
    A.append(anim('idle', 3.0, loop=True,
                  chest=dict(rot=[(0, 0, 0, 0), (1.5, -2.5, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.5, 2, 0, 0), (3.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 3), (1.5, -3, 0, 5), (3.0, 0, 0, 3)]),
                  left_arm=dict(rot=[(0, 0, 0, -3), (1.5, -3, 0, -5), (3.0, 0, 0, -3)]),
                  right_forearm=dict(rot=[(0, -8, 0, 0), (1.5, -12, 0, 0), (3.0, -8, 0, 0)]),
                  left_forearm=dict(rot=[(0, -8, 0, 0), (1.5, -12, 0, 0), (3.0, -8, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (1.5, 4, 0, 0), (3.0, 0, 0, 0)]),
                  robe=dict(rot=[(0, 0, 0, 0), (1.5, 2, 0, 1), (3.0, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, -12, 0), (1.5, 6, 12, 0), (3.0, 0, -12, 0)]),
                  tail_tip=dict(rot=[(0, 0, -10, 0), (1.5, 15, 10, 0), (3.0, 0, -10, 0)]),
                  hand_flame=dict(scale=[(0, 1, 1, 1), (0.75, 1.25, 1.35, 1.25), (1.5, 0.9, 1.0, 0.9), (2.25, 1.2, 1.3, 1.2), (3.0, 1, 1, 1)]),
                  hood=dict(rot=[(0, 0, 0, 0), (1.5, -1, 0, 0), (3.0, 0, 0, 0)])))
    A.append(anim('walk', 1.0, loop=True,
                  right_leg=dict(rot=[(0, 28, 0, 0), (0.5, -28, 0, 0), (1.0, 28, 0, 0)]),
                  left_leg=dict(rot=[(0, -28, 0, 0), (0.5, 28, 0, 0), (1.0, -28, 0, 0)]),
                  right_shin=dict(rot=[(0, 18, 0, 0), (0.25, 50, 0, 0), (0.5, 4, 0, 0), (0.75, 8, 0, 0), (1.0, 18, 0, 0)]),
                  left_shin=dict(rot=[(0, 4, 0, 0), (0.25, 8, 0, 0), (0.5, 18, 0, 0), (0.75, 50, 0, 0), (1.0, 4, 0, 0)]),
                  right_arm=dict(rot=[(0, -24, 0, 0), (0.5, 24, 0, 0), (1.0, -24, 0, 0)]),
                  left_arm=dict(rot=[(0, 24, 0, 0), (0.5, -24, 0, 0), (1.0, 24, 0, 0)]),
                  right_forearm=dict(rot=[(0, -18, 0, 0), (0.5, -6, 0, 0), (1.0, -18, 0, 0)]),
                  left_forearm=dict(rot=[(0, -6, 0, 0), (0.5, -18, 0, 0), (1.0, -6, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.7, 0), (0.5, 0, 0, 0), (0.75, 0, 0.7, 0), (1.0, 0, 0, 0)]),
                  hips=dict(rot=[(0, 0, 5, 0), (0.5, 0, -5, 0), (1.0, 0, 5, 0)]),
                  chest=dict(rot=[(0, 4, -6, 0), (0.5, 4, 6, 0), (1.0, 4, -6, 0)]),
                  head=dict(rot=[(0, -3, 3, 0), (0.25, -1, 0, 0), (0.5, -3, -3, 0), (0.75, -1, 0, 0), (1.0, -3, 3, 0)]),
                  cape=dict(rot=[(0, 14, 0, 0), (0.5, 20, 0, 0), (1.0, 14, 0, 0)]),
                  robe=dict(rot=[(0, 4, 0, 2), (0.5, 4, 0, -2), (1.0, 4, 0, 2)]),
                  tail=dict(rot=[(0, 0, 15, 0), (0.5, 0, -15, 0), (1.0, 0, 15, 0)])))
    A.append(anim('attack1', 0.8, impact=0.4,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.25, -155, 0, 18), (0.4, -45, 0, -8), (0.55, -30, 0, -5), (0.8, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.25, -35, 0, 0), (0.4, -5, 0, 0), (0.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.25, -35, 0, -10), (0.4, 20, 0, -15), (0.8, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.25, -12, 22, 0), (0.4, 16, -18, 0), (0.6, 8, -8, 0), (0.8, 0, 0, 0)]),
                  hips=dict(rot=[(0, 0, 0, 0), (0.25, 0, 8, 0), (0.4, 0, -8, 0), (0.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.25, -10, -12, 0), (0.4, 8, 10, 0), (0.8, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.25, 12, 0, 0), (0.4, -22, 0, 0), (0.8, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.4, 18, 0, 0), (0.8, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.4, 14, 0, 0), (0.8, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.5, 0.6), (0.4, 0, -0.6, -2.0), (0.8, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.4, 25, 0, 0), (0.8, 0, 0, 0)])))
    A.append(anim('attack2', 0.9, impact=0.5,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, 40, 0, 12), (0.5, -58, -8, 0), (0.65, -52, -5, 0), (0.9, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.3, -75, 0, 0), (0.5, 26, 0, 0), (0.65, 20, 0, 0), (0.9, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -25, 0, -12), (0.5, 35, 0, -10), (0.9, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, -4, 28, 0), (0.5, 10, -24, 0), (0.7, 6, -10, 0), (0.9, 0, 0, 0)]),
                  hips=dict(rot=[(0, 0, 0, 0), (0.3, 0, 10, 0), (0.5, 0, -10, 0), (0.9, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, 0, -20, 0), (0.5, 4, 18, 0), (0.9, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.3, 10, 0, 0), (0.5, -30, 0, 0), (0.9, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.3, 10, 0, 0), (0.5, 22, 0, 0), (0.9, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.5, 18, 0, 0), (0.9, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.5, 20, 0, 0), (0.9, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.3, 0, 0, 1.0), (0.5, 0, -1.2, -3.0), (0.9, 0, 0, 0)])))
    A.append(anim('cast', 1.2, impact=0.7,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.4, -125, 0, 22), (0.55, -130, 0, 18), (0.7, -82, 0, 6), (0.95, -70, 0, 4), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.4, -125, 0, -22), (0.55, -130, 0, -18), (0.7, -82, 0, -6), (0.95, -70, 0, -4), (1.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.4, -25, 0, 0), (0.7, -5, 0, 0), (1.2, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.4, -25, 0, 0), (0.7, -5, 0, 0), (1.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.4, -14, 0, 0), (0.55, -16, 0, 0), (0.7, 12, 0, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.4, -18, 0, 0), (0.7, 6, 0, 0), (1.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.45, 0, 0.8, 0.3), (0.7, 0, 0, -1.0), (1.2, 0, 0, 0)]),
                  robe=dict(rot=[(0, 0, 0, 0), (0.45, -8, 0, 0), (0.7, 12, 0, 0), (1.2, 0, 0, 0)]),
                  cape=dict(rot=[(0, 0, 0, 0), (0.45, 10, 0, 0), (0.7, 30, 0, 0), (1.2, 0, 0, 0)]),
                  hand_flame=dict(scale=[(0, 1, 1, 1), (0.5, 1.8, 1.8, 1.8), (0.7, 0.4, 0.4, 0.4), (0.9, 1, 1, 1), (1.2, 1, 1, 1)])))
    A.append(anim('shoot', 1.0, impact=0.6,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -88, -12, 0), (0.6, -88, -12, 0), (0.68, -100, -12, 0), (0.85, -90, -12, 0), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -88, 35, 0), (0.6, -88, 35, 0), (0.68, -98, 35, 0), (1.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.3, -20, 0, 0), (1.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, 0, 12, 0), (0.6, 0, 12, 0), (0.68, -6, 12, 0), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, 4, -14, 6), (0.6, 4, -14, 6), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.6, 0, 0, 0), (0.68, 0, 0, 1.2), (1.0, 0, 0, 0)])))
    A.append(anim('hurt', 0.4,
                  chest=dict(rot=[(0, 0, 0, 0), (0.08, -18, 0, 0), (0.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.08, -16, 0, 0), (0.4, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.08, -10, 0, 25), (0.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.08, -10, 0, -25), (0.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.08, 0, 0, 1.0), (0.4, 0, 0, 0)])))
    A.append(anim('spawn', 1.2,
                  base=dict(pos=[(0, 0, -22, 0), (0.55, 0, -3, 0), (0.75, 0, 1.2, 0), (1.0, 0, 0, 0), (1.2, 0, 0, 0)],
                            rot=[(0, 30, 0, 0), (0.75, -6, 0, 0), (1.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -165, 0, 10), (0.6, -110, 0, 15), (0.9, -20, 0, 0), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, -165, 0, -10), (0.6, -120, 0, -15), (0.95, -25, 0, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, -35, 0, 0), (0.75, 10, 0, 0), (1.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 15, 0, 0), (0.8, -8, 0, 0), (1.2, 0, 0, 0)])))
    A.append(anim('death', 1.5,
                  chest=dict(rot=[(0, 0, 0, 0), (0.25, -22, 0, 0), (0.6, 10, 0, 0), (1.5, 18, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.25, -28, 0, 0), (0.6, 5, 0, 0), (1.3, 30, 0, 10), (1.5, 25, 0, 12)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.25, -30, 0, 40), (0.9, -40, 0, 20), (1.3, -170, 0, 15), (1.5, -165, 0, 18)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.25, -30, 0, -40), (0.9, -30, 0, -20), (1.3, -150, 0, -25), (1.5, -150, 0, -28)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.6, -45, 0, 0), (1.0, -50, 0, 0), (1.3, 0, 0, 0), (1.5, 0, 0, 3)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.6, -35, 0, 0), (1.0, -45, 0, 0), (1.3, 0, 0, 0), (1.5, 0, 0, -3)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.6, 90, 0, 0), (1.0, 95, 0, 0), (1.3, 10, 0, 0), (1.5, 8, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.6, 80, 0, 0), (1.0, 90, 0, 0), (1.3, 10, 0, 0), (1.5, 8, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0, 1.0), (0.6, 0, -5.5, 0.5), (1.0, 0, -6.5, 0), (1.3, 0, -1, -2), (1.5, 0, -1.5, -2)],
                            rot=[(0, 0, 0, 0), (0.9, 8, 0, 0), (1.25, 88, 0, 0), (1.38, 80, 0, 0), (1.5, 86, 0, 0)])))
    return A


# --------------------------------------------------------------------------------------------- variants

def base_palette(**over):
    pal = {
        'skin': '#c99a78', 'hand': '#c99a78', 'tunic': '#6b4a35', 'belly': '#5d4030', 'sleeve': '#6b4a35',
        'sleeve2': '#c99a78', 'pants': '#4a3b30', 'boots': '#3b2a20', 'hood': '#4f3a2c', 'helm': '#9aa2ad',
        'plume': '#b03030', 'horn': '#d8cfb8', 'leaves': '#4f8a3a', 'armor': '#8a929c', 'cape': '#7a2222',
        'robe': '#3d3550', 'tail': '#a83a2a', 'wrap': '#d9ceb0', 'grip': '#4a3020', 'blade': '#c8d0d8',
        'guard': '#8a7040', 'haft': '#6b4c2a', 'club': '#5b5b62', 'staff': '#5a3f28', 'orb': '#c03a3a',
        'stock': '#6b4c2a', 'limb': '#4f3a22', 'bolt': '#9aa0a8', 'claw': '#e0d8c0', 'iron': '#5d6168',
        'chain': '#7a7f88', 'shield': '#7a8aa0', 'fire': '#ff9933', 'belt': '#3a2618', 'buckle': '#d0b050',
    }
    pal.update(over)
    return pal


def belt(p, part='hips', y=0, col=(58, 38, 24), buckle=(208, 176, 80)):
    for fc in p.all_faces(part, sides_only=True):
        fc.hline(0, fc.w - 1, y, col)
        fc.hline(0, fc.w - 1, y + 1, shade(col, 0.8))
    f = p.face(part, 'front')
    f.rect(f.w // 2 - 1, y, f.w // 2, y + 1, buckle)


def hands(p, col, rows=2):
    for part in ('right_forearm',):
        for fc in p.all_faces(part, sides_only=True):
            for y in range(fc.h - rows, fc.h):
                for x in range(fc.w):
                    fc.px(x, y, shade(col, 1.0 - 0.08 * (y - (fc.h - rows))))
        fc = p.face(part, 'bottom')
        fc.rect(0, 0, fc.w - 1, fc.h - 1, shade(col, 0.85))


def boots(p, col, rows=3):
    for fc in p.all_faces('right_shin', sides_only=True):
        for y in range(fc.h - rows, fc.h):
            fc.hline(0, fc.w - 1, y, shade(col, 1.0 - 0.07 * (y - fc.h + rows)))
        fc.hline(0, fc.w - 1, fc.h - rows, lighten(col, 0.2))


def hood_opening(p, depth=(1, 1, 1, 2), dark=None):
    """Cut the hood's front face open so the head (and its face) shows through; dark rim inside."""
    fc = p.face('hood', 'front')
    left, right, top, bottom = depth
    for y in range(top, fc.h - bottom + 1):
        for x in range(left, fc.w - right):
            fc.clear(x, y)
    rim = shade(p.variant.palette_rgb('hood'), 0.55) if dark is None else dark
    for y in range(top, fc.h - bottom + 1):
        fc.px(left - 1, y, rim)
        fc.px(fc.w - right, y, rim)
    fc.hline(left - 1, fc.w - right, top - 1, rim)


def shadow_face(p, eye, glow=True, y=4):
    fc = p.face('head', 'front')
    for yy in range(fc.h):
        for x in range(fc.w):
            fc.px(x, yy, (22, 18, 26) if yy < fc.h - 2 else (40, 34, 44))
    fc.px(2, y, eye)
    fc.px(5, y, eye)
    if glow:
        g = p.face('head', 'front', glow=True)
        g.px(2, y, eye)
        g.px(5, y, eye)


def staff_orb_glow(p, col):
    for fc in p.all_faces('staff', index=1, glow=True):
        for y in range(fc.h):
            for x in range(fc.w):
                fc.px(x, y, shade(col, 0.75 + 0.25 * ((x + y) % 2)))


def emblem(p, part, col, kind='diamond'):
    fc = p.face(part, 'front')
    cx, cy = fc.w // 2, fc.h // 2
    if kind == 'diamond':
        for (dx, dy) in ((0, -2), (-1, -1), (0, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
            fc.px(cx - 1 + dx, cy + dy, col)
    elif kind == 'cross':
        fc.rect(cx - 1, cy - 2, cx, cy + 2, col)
        fc.rect(cx - 3, cy - 1, cx + 2, cy, col)
    elif kind == 'eye':
        fc.hline(cx - 3, cx + 2, cy, col)
        fc.hline(cx - 2, cx + 1, cy - 1, col)
        fc.hline(cx - 2, cx + 1, cy + 1, col)
        fc.px(cx - 1, cy, (20, 10, 10))
        fc.px(cx, cy, (20, 10, 10))


def variants():
    V = []

    def deco_bandit(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#c99a78'), brows=(60, 40, 30))
        f.rect(1, 5, 6, 7, (120, 40, 40))  # bandana over mouth
        f.hline(1, 6, 5, (150, 55, 50))
        hood_opening(p, depth=(1, 1, 1, 1))
        belt(p)
        hands(p, rgb('#5a4030'))
        boots(p, rgb('#3b2a20'))
        for fc in p.all_faces('chest', sides_only=True):
            fc.vline(fc.w // 2 if fc.face in ('front', 'back') else 1, 0, fc.h - 1, (70, 50, 35))
    V.append(Variant('bandit', show={'hood', 'dagger'}, palette=base_palette(hood='#5a3d2a', tunic='#7d5a3c',
                     sleeve='#7d5a3c', sleeve2='#6f4f35'), paint=deco_bandit, extra=dict(scale=1.0)))

    def deco_ghoul(p):
        f = p.face('head', 'front')
        sk = rgb('#7f8f6a')
        f.rect(1, 2, 6, 4, shade(sk, 0.7))
        f.px(2, 3, (170, 255, 120))
        f.px(5, 3, (170, 255, 120))
        f.rect(2, 5, 5, 6, (40, 20, 20))
        for x in (2, 4):
            f.px(x, 5, (230, 225, 200))
        f.px(3, 6, (230, 225, 200))
        f.px(5, 6, (230, 225, 200))
        g = p.face('head', 'front', glow=True)
        g.px(2, 3, (150, 255, 100))
        g.px(5, 3, (150, 255, 100))
        for fc in p.all_faces('chest', sides_only=True):
            for y in range(1, fc.h, 2):
                fc.hline(1, fc.w - 2, y, shade(sk, 0.75))
        belt(p, col=(60, 50, 40), buckle=(80, 70, 60))
        hands(p, sk)
        boots(p, rgb('#4d5340'), rows=2)
    sk = '#7f8f6a'
    V.append(Variant('ghoul', show={'claws_r', 'claws_l'}, palette=base_palette(
        skin=sk, tunic={'base': sk, 'style': 'skin'}, belly='#4b4035', sleeve={'base': sk, 'style': 'skin'},
        sleeve2={'base': sk, 'style': 'skin'}, pants='#4b4035', boots={'base': sk, 'style': 'skin'}, claw='#d8d0a8'),
        poses={'chest': dict(rot=(18, 0, 0)), 'head': dict(rot=(-14, 0, 0), pos=(0, 0, -0.5)),
               'right_arm': dict(rot=(-25, 0, 6)), 'left_arm': dict(rot=(-25, 0, -6))},
        paint=deco_ghoul, glow=True, extra=dict(scale=1.0)))

    def deco_mummy(p):
        f = p.face('head', 'front')
        f.hline(1, 6, 3, (40, 30, 20))
        f.px(2, 3, (255, 210, 80))
        f.px(5, 3, (255, 210, 80))
        g = p.face('head', 'front', glow=True)
        g.px(2, 3, (255, 200, 60))
        g.px(5, 3, (255, 200, 60))
        hands(p, rgb('#cdbf9c'))
    wrap = {'base': '#d4c7a2', 'style': 'bandage'}
    V.append(Variant('mummy', show={'mummy_strips', 'chest_strips'}, palette=base_palette(
        skin=wrap, tunic=wrap, belly=wrap, sleeve=wrap, sleeve2=wrap, pants=wrap, boots=wrap),
        poses={'right_arm': dict(rot=(-75, 0, 4)), 'left_arm': dict(rot=(-70, 0, -4)), 'head': dict(rot=(6, 0, 4))},
        paint=deco_mummy, glow=True, extra=dict(scale=1.05)))

    def deco_cultist(p):
        shadow_face(p, (255, 60, 40))
        hood_opening(p, depth=(1, 1, 1, 1))
        emblem(p, 'chest', (200, 40, 40), 'eye')
        belt(p, col=(80, 20, 20), buckle=(200, 160, 60))
        hands(p, rgb('#b08a70'))
        staff_orb_glow(p, (255, 70, 50))
    V.append(Variant('cultist', show={'hood', 'robe', 'staff'}, palette=base_palette(
        hood={'base': '#5a1f24', 'trim': '#c9a24a'}, tunic='#5a1f24', belly='#4a181c', sleeve='#5a1f24',
        sleeve2='#5a1f24', robe={'base': '#4a181c', 'trim': '#c9a24a'}, pants='#3a1416', boots='#2a1a14',
        orb='#e04030'), paint=deco_cultist, glow=True, extra=dict(scale=1.0)))

    def deco_prisoner(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#b89070'), brows=(70, 55, 45), beard=(90, 75, 60))
        for fc in p.all_faces('chest', sides_only=True) + p.all_faces('hips', sides_only=True):
            for y in range(0, fc.h, 3):
                fc.hline(0, fc.w - 1, y, (120, 110, 90))
        hands(p, rgb('#b89070'))
        boots(p, rgb('#b89070'), rows=2)
    V.append(Variant('prisoner', show={'shackle_r', 'shackle_l'}, palette=base_palette(
        skin='#b89070', tunic='#a39a80', belly='#a39a80', sleeve='#a39a80', sleeve2='#b89070', pants='#8f876e',
        boots={'base': '#b89070', 'style': 'skin'}), poses={'head': dict(rot=(10, 0, 0)), 'chest': dict(rot=(6, 0, 0))},
        paint=deco_prisoner, extra=dict(scale=0.95)))

    def deco_jailer(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#a87a60'), brows=(50, 35, 25), beard=(60, 40, 30))
        hf = p.face('helmet', 'front')
        for y in range(3, hf.h):
            for x in range(hf.w):
                hf.clear(x, y)
        hf.hline(0, hf.w - 1, 2, (70, 72, 78))
        belt(p, col=(40, 30, 25), buckle=(150, 150, 150))
        f2 = p.face('hips', 'front')
        f2.px(1, 2, (200, 180, 90))
        f2.px(1, 3, (200, 180, 90))
        emblem(p, 'chest', (60, 60, 66), 'cross')
        hands(p, rgb('#3a2c22'))
        boots(p, rgb('#2c221c'), rows=3)
    V.append(Variant('jailer', show={'helmet', 'club', 'pauldron_r', 'pauldron_l'}, palette=base_palette(
        skin='#a87a60', tunic='#4a4440', belly='#3c3632', sleeve='#4a4440', sleeve2='#4a4440', pants='#3a342f',
        helm='#6c7078', armor='#6c7078'), paint=deco_jailer, extra=dict(scale=1.15)))

    def deco_ice_knight(p):
        hf = p.face('helmet', 'front')
        hf.hline(1, hf.w - 2, 3, (20, 30, 50))
        hf.vline(hf.w // 2 - 1, 3, 6, (20, 30, 50))
        hf.vline(hf.w // 2, 3, 6, (20, 30, 50))
        g = p.face('helmet', 'front', glow=True)
        g.px(2, 3, (120, 220, 255))
        g.px(5, 3, (120, 220, 255))
        emblem(p, 'chest', (180, 230, 255), 'diamond')
        sf = p.face('shield_l', 'left')
        emblem_on(sf, (200, 240, 255))
        belt(p, col=(50, 70, 100), buckle=(200, 230, 255))
        hands(p, rgb('#9fb4c8'))
        boots(p, rgb('#8aa0b8'), rows=6)
    ice_metal = {'base': '#a9c4dc', 'style': 'metal'}
    V.append(Variant('ice_knight', show={'helmet', 'helm_crest', 'blade', 'shield_l', 'pauldron_r', 'pauldron_l', 'cape'},
                     palette=base_palette(helm='#b8d0e6', armor='#b8d0e6', tunic=ice_metal, belly=ice_metal,
                                          sleeve=ice_metal, sleeve2=ice_metal, pants='#5d7896', boots=ice_metal,
                                          plume='#e8f4ff', cape='#3c5c8c', shield='#9cc0e0', blade='#dff4ff',
                                          guard='#8ab0d0'),
                     paint=deco_ice_knight, glow=True, extra=dict(scale=1.1)))

    def deco_frost_mage(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#d8c8c0'), eye_col=(60, 180, 255), brows=(200, 210, 220), beard=(225, 230, 235),
                   glow_fc=p.face('head', 'front', glow=True), glow_eyes=(80, 200, 255))
        hood_opening(p, depth=(1, 1, 1, 1))
        emblem(p, 'chest', (170, 230, 255), 'diamond')
        hands(p, rgb('#d8c8c0'))
        staff_orb_glow(p, (120, 220, 255))
    V.append(Variant('frost_mage', show={'hood', 'robe', 'staff'}, palette=base_palette(
        skin='#d8c8c0', hood={'base': '#3f6a9a', 'trim': '#e0f0ff'}, tunic='#3f6a9a', belly='#335a85',
        sleeve='#3f6a9a', sleeve2='#3f6a9a', robe={'base': '#335a85', 'trim': '#e0f0ff'}, pants='#2c4a6e',
        boots='#2a3346', staff='#d0e4f0', orb={'base': '#9fe6ff', 'style': 'crystal'}),
        paint=deco_frost_mage, glow=True, extra=dict(scale=1.0)))

    def deco_fire_imp(p):
        f = p.face('head', 'front')
        f.rect(1, 3, 2, 3, (255, 230, 60))
        f.rect(5, 3, 6, 3, (255, 230, 60))
        f.px(2, 3, (30, 10, 0))
        f.px(5, 3, (30, 10, 0))
        f.hline(2, 5, 5, (60, 10, 10))
        f.px(2, 6, (240, 230, 210))
        f.px(5, 6, (240, 230, 210))
        g = p.face('head', 'front', glow=True)
        g.rect(1, 3, 2, 3, (255, 210, 40))
        g.rect(5, 3, 6, 3, (255, 210, 40))
        for fc in p.all_faces('hand_flame', glow=True):
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.px(x, y, (255, 150, 40))
        belt(p, col=(50, 20, 10), buckle=(255, 170, 40))
    red = '#b8402c'
    V.append(Variant('fire_imp', show={'horn_r', 'horn_l', 'tail', 'claws_r', 'claws_l', 'hand_flame'},
                     palette=base_palette(skin=red, tunic={'base': red, 'style': 'skin'}, belly='#3a2018',
                                          sleeve={'base': red, 'style': 'skin'}, sleeve2={'base': red, 'style': 'skin'},
                                          pants='#3a2018', boots={'base': '#8a2c20', 'style': 'skin'},
                                          horn='#2a1a14', tail=red, claw='#2a1a14'),
                     poses={'head': dict(scale=(1.2, 1.2, 1.2), pos=(0, 0.5, 0))},
                     paint=deco_fire_imp, glow=True, extra=dict(scale=0.72)))

    def deco_dark_knight(p):
        hf = p.face('helmet', 'front')
        hf.hline(1, hf.w - 2, 3, (10, 8, 12))
        hf.hline(2, hf.w - 3, 4, (10, 8, 12))
        hf.vline(hf.w // 2 - 1, 5, 7, (10, 8, 12))
        hf.vline(hf.w // 2, 5, 7, (10, 8, 12))
        g = p.face('helmet', 'front', glow=True)
        g.px(2, 3, (255, 40, 30))
        g.px(5, 3, (255, 40, 30))
        g.px(3, 3, (180, 20, 10))
        g.px(4, 3, (180, 20, 10))
        emblem(p, 'chest', (150, 30, 40), 'cross')
        belt(p, col=(30, 26, 34), buckle=(150, 30, 40))
        hands(p, rgb('#2c2833'))
    dm = {'base': '#3a3644', 'style': 'metal'}
    V.append(Variant('dark_knight', show={'helmet', 'horn_r', 'horn_l', 'greatblade', 'pauldron_r', 'pauldron_l', 'cape'},
                     palette=base_palette(helm='#3a3644', armor='#433e50', tunic=dm, belly=dm, sleeve=dm,
                                          sleeve2=dm, pants='#24212b', boots=dm, horn='#1a1820', cape='#5a1020',
                                          blade='#5a5468', guard='#8a2030'),
                     paint=deco_dark_knight, glow=True, extra=dict(scale=1.2)))

    def deco_void_mage(p):
        shadow_face(p, (210, 120, 255))
        hood_opening(p, depth=(1, 1, 1, 1))
        emblem(p, 'chest', (180, 90, 255), 'eye')
        hands(p, rgb('#5a4a6a'))
        staff_orb_glow(p, (190, 100, 255))
        for fc in p.all_faces('robe', glow=True, sides_only=True):
            for x in range(0, fc.w, 3):
                fc.px(x, fc.h - 3, (120, 60, 200))
    V.append(Variant('void_mage', show={'hood', 'robe', 'staff'}, palette=base_palette(
        hood={'base': '#2b1f3d', 'trim': '#9a6ad0'}, tunic={'base': '#2b1f3d', 'style': 'cloth'}, belly='#24192f',
        sleeve='#2b1f3d', sleeve2='#2b1f3d', robe={'base': '#24192f', 'trim': '#9a6ad0'}, pants='#1c1426',
        boots='#16101e', staff='#1e1a24', orb={'base': '#8a3ad0', 'style': 'void'}),
        paint=deco_void_mage, glow=True, extra=dict(scale=1.0)))

    def deco_shade(p):
        shadow_face(p, (200, 230, 255))
        hood_opening(p, depth=(1, 1, 1, 1), dark=(10, 10, 16))
    ghost = {'base': '#3a3f52', 'style': 'ghost'}
    V.append(Variant('shade', show={'hood', 'robe', 'claws_r', 'claws_l'}, hide={'right_leg', 'left_leg'},
                     palette=base_palette(hood={'base': '#2a2d3c', 'style': 'ghost', 'ragged': False}, tunic=ghost, belly=ghost,
                                          sleeve=ghost, sleeve2=ghost, robe={'base': '#2f3344', 'style': 'ghost'},
                                          claw='#cfd8ff'),
                     poses={'base': dict(pos=(0, 2.0, 0)), 'robe': dict(scale=(1.0, 1.15, 1.0))},
                     paint=deco_shade, glow=True, extra=dict(scale=1.05, translucent=True)))

    def deco_crossbowman(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#c49478'), brows=(80, 60, 40))
        hood_opening(p, depth=(1, 1, 1, 1))
        belt(p)
        hands(p, rgb('#4a3426'))
        boots(p, rgb('#3b2a20'))
        for fc in p.all_faces('chest', sides_only=True):
            if fc.face == 'front':
                for k in range(fc.h):
                    fc.px(k, k, (60, 40, 25))
    V.append(Variant('crossbowman', show={'hood', 'crossbow'}, overrides={'cast': 'shoot'},
                     palette=base_palette(hood='#3c4a2c', tunic='#4f5a38', sleeve='#4f5a38', sleeve2='#4f5a38'),
                     paint=deco_crossbowman, extra=dict(scale=1.0)))

    def deco_dryad(p):
        f = p.face('head', 'front')
        human_face(f, rgb('#9cba7a'), eye_col=(40, 160, 60), brows=(60, 100, 40), mouth=True,
                   glow_fc=p.face('head', 'front', glow=True), glow_eyes=(120, 255, 120))
        staff_orb_glow(p, (255, 180, 220))
        hands(p, rgb('#9cba7a'))
    bark = {'base': '#7a5a3a', 'style': 'bark'}
    V.append(Variant('dryad', show={'leaf_crown', 'robe', 'staff'}, palette=base_palette(
        skin='#9cba7a', tunic=bark, belly=bark, sleeve=bark, sleeve2={'base': '#9cba7a', 'style': 'skin'},
        pants=bark, boots=bark, robe={'base': '#4f8a3a', 'style': 'leaf', 'flowers': (240, 160, 200)},
        leaves={'base': '#4f8a3a', 'flowers': (240, 160, 200)}, staff='#7a5a3a', orb={'base': '#f0a0c8', 'style': 'gem'}),
        paint=deco_dryad, glow=True, extra=dict(scale=1.0)))
    return V


def emblem_on(fc, col):
    cx, cy = fc.w // 2, fc.h // 2
    fc.vline(cx, 1, fc.h - 2, col)
    fc.hline(1, fc.w - 2, cy - 1, col)


def rig():
    return Rig('humanoid', 'HumanoidEnemy', geometry(), animations(), variants(), SLOTS, head='head')
