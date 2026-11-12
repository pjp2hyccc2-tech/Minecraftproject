"""Hero rig: the four legendary travellers of the single-player worlds (docs/modules/travellers.md).

  traveller_rosalind  流離の魔女 Rosalind: the witch who once defended the kingdom alone. A wide-brimmed hat that bends
                      at the tip, long wine-violet hair, a midnight dress with a side slit and bell sleeves, the
                      armillary staff (a star-core held by three turning rings) and a grimoire that floats beside her.
  traveller_siegwald  竜退けの騎士 Siegwald: the knight who drove a dragon off with an iron sword. Battle-worn plate,
                      a royal-blue tabard torn by claw marks, a fur-collared crimson cape, a dragon-scale pauldron with
                      ember fissures and the nameless iron sword whose fuller still glows from the dragon's fire.
  traveller_kaylen    踏破の魔剣士 Kaylen: the only one who cleared every EX dungeon. Ash-white hair with a long low
                      tail, mismatched eyes, a high-collared deep-teal coat with uneven tails, seven conquest seals at the
                      hip, a rune longsword drawn from a scabbard, and phantom blades fanned behind him in a fight.
  traveller_mariel    救国のシスター Mariel: the sister whose holy magic ended the plague. A white and gold habit and
                      veil, honey hair, a turning halo, wide sleeves, a chained prayer book and the saint's staff whose
                      teardrop crystal cured the kingdom.

Player-sized humanoid (32 units) like the NPC rig, painted at two texels per unit (sharper faces and finer
detail than the townsfolk). Face planes 'eyelids' (blink) and 'mouth' (talk) as on the NPC rig. Animation slots:
idle, walk, run (following at speed), ready (fighting stance, a loop), talk, attack, attack_b (the two ordinary
attacks, alternating), skill_a, skill_b, ultimate (each hero's own moves), down (fallen, a loop) and join (the
gesture when they join the party or win a fight). The renderer hides or shows a few parts at run time
(the phantom blades and the drawn or sheathed sword), see HeroModel.
"""
import math

from ermodel import C, P, Rig, anim

SLOTS = ['idle', 'walk', 'run', 'ready', 'talk', 'attack', 'attack_b', 'skill_a', 'skill_b', 'ultimate', 'down',
         'join']
# The face planes rest this far inside the head; animations move them out by LID_OUT (posVec z, negative = front).
LID_OUT = -0.78
TEXEL = 2


# ------------------------------------------------------------------------------------------------- geometry

def head_parts():
    return P('head', (0, 0, 0),
             C(-4, -8, -4, 8, 8, 8, 'skin:skin'),
             P('eyelids', (0, -4, -4), C(-4, -8, -3.3, 8, 8, 0, 'plain:fx')),
             P('mouth', (0, -2, -4), C(-4, -4, -3.3, 8, 4, 0, 'plain:fx')),
             P('hair_cap', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'hair:hair', inflate=0.45), opt=True),
             P('hair_top', (0, -8, 0), C(-4.5, -9.5, -4, 9, 2, 9, 'hair:hair'), opt=True),
             P('bangs', (0, -8, -4), C(-4.5, -8.6, -4.85, 9, 6, 1, 'hair:bangs'), opt=True),
             P('lock_r', (-4.2, -6, -2.5), C(-5.1, -6.5, -3.6, 1, 8, 2, 'hair:lock'),
               P('lock_r2', (-4.6, 1.5, -2.6), C(-5.1, 1.5, -3.5, 1, 6, 2, 'hair:lock')), rot=(-4, 0, 3), opt=True),
             P('lock_l', (4.2, -6, -2.5), C(4.1, -6.5, -3.6, 1, 8, 2, 'hair:lock'),
               P('lock_l2', (4.6, 1.5, -2.6), C(4.1, 1.5, -3.5, 1, 6, 2, 'hair:lock')), rot=(-4, 0, -3), opt=True),
             P('earring_r', (-4.3, -2.5, -0.5), C(-4.8, -2.5, -1, 1, 3, 1, 'gem:earring'), opt=True),
             P('earring_l', (4.3, -2.5, -0.5), C(3.8, -2.5, -1, 1, 3, 1, 'gem:earring'), opt=True),
             # the witch's hat: a broad brim and a cone that bends back at three joints
             P('hat_brim', (0, -8.7, 0), C(-8, -9.2, -8, 16, 1, 16, 'cloth:hat_brim'),
               P('hat_cone', (0, -9.2, 0), C(-4, -13.2, -4, 8, 4, 8, 'cloth:hat'),
                 P('hat_cone2', (0, -13.2, 0.4), C(-3, -17.2, -2.6, 6, 4, 6, 'cloth:hat'),
                   P('hat_tip', (0, -17.2, 0.6), C(-2, -20.2, -1.4, 4, 3, 4, 'cloth:hat'),
                     P('hat_tip2', (0, -20.2, 0.8), C(-1, -23.2, -0.2, 2, 3, 2, 'cloth:hat'), rot=(-28, 0, 8)),
                     rot=(-16, 0, 4)), rot=(-8, 0, 0)), rot=(-4, 0, 0)),
               P('hat_charm', (-7, -8.7, -3), C(-7.5, -8.7, -3.5, 1, 3, 1, 'gold:charm'),
                 C(-8, -5.7, -4, 2, 2, 1, 'gem:charm_star')),
               rot=(-5, 0, -6), opt=True),
             # the sister's veil: a hood shell, the cloth falling behind, the halo
             P('hood', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'cloth:hood', inflate=0.85), opt=True),
             P('veil', (0, -7, 4.7), C(-4.8, -7, 4.5, 10, 9, 1, 'cloth:veil'),
               P('veil2', (0, 2, 4.9), C(-5.2, 2, 4.7, 10, 7, 1, 'cloth:veil')), rot=(-8, 0, 0), opt=True),
             P('halo', (0, -9, 6.6), C(-5, -14, 6.6, 10, 10, 0, 'gold:halo'), opt=True))


def right_hand_props():
    """Children of the right forearm, held at the right hand (about (-6, 11, 0); slim arms (-5.5, 11, 0))."""
    return [
        # Rosalind's armillary staff: the pole, a crook of two prongs, the star core and three rings around it
        P('staff_witch', (-5.5, 11, 0), C(-6, -15, -0.5, 1, 38, 1, 'wood:staff'),
          C(-6.5, 21, -1, 2, 2, 2, 'gold:staff_foot'), C(-6.5, -16, -1, 2, 2, 2, 'gold:staff_collar'),
          C(-8.5, -19, -0.5, 1, 3, 1, 'wood:staff'), C(-3.5, -19, -0.5, 1, 3, 1, 'wood:staff'),
          C(-7.5, -20, -0.5, 1, 1, 1, 'wood:staff'), C(-4.5, -20, -0.5, 1, 1, 1, 'wood:staff'),
          P('staff_core', (-5.5, -20.5, 0), C(-6.5, -21.5, -1, 2, 2, 2, 'gem:core'),
            P('ring_a', (-5.5, -20.5, 0), C(-8.5, -23.5, 0, 6, 6, 0, 'gold:ring')),
            P('ring_b', (-5.5, -20.5, 0), C(-8, -23, 0, 5, 5, 0, 'gold:ring'), rot=(0, 90, 0)),
            P('ring_c', (-5.5, -20.5, 0), C(-9, -24, 0, 7, 7, 0, 'gold:ring'), rot=(90, 0, 0))),
          opt=True),
        # Siegwald's nameless iron sword, pointing forward from the fist
        P('sword_iron', (-6, 11, 0), C(-6.5, 10.5, -2, 1, 1, 5, 'leather:grip'), C(-7, 10, 3, 2, 2, 1, 'metal:pommel'),
          C(-8.5, 9.5, -3, 5, 2, 1, 'metal:guard'),
          C(-6.5, 9.5, -22, 1, 2, 19, 'metal:blade'), opt=True),
        # Kaylen's rune longsword
        P('sword_rune', (-6, 11, 0), C(-6.5, 10.5, -2, 1, 1, 4, 'leather:grip_rune'),
          C(-7, 10, 2, 2, 2, 1, 'gem:pommel_gem'), C(-8.5, 9.5, -3, 5, 2, 1, 'gold:guard_rune'),
          C(-6.5, 9.5, -20, 1, 2, 17, 'metal:rune_blade'), opt=True),
        # Mariel's staff: the pole, the sun ring, the teardrop crystal hanging in it
        P('staff_holy', (-5.5, 11, 0), C(-6, -12, -0.5, 1, 35, 1, 'gold:holy_pole'),
          C(-6.5, 22, -1, 2, 1, 2, 'gold:holy_pole'), C(-6.5, -13, -1, 2, 2, 2, 'gold:holy_pole'),
          C(-8.5, -20, 0, 6, 7, 0, 'gold:sun_ring'),
          P('staff_tear', (-5.5, -17.5, 0), C(-6.5, -18, -1, 2, 3, 2, 'gem:tear')),
          P('staff_ribbon', (-5.5, -13, 0.6), C(-6, -13, 0.6, 1, 7, 0, 'cloth:ribbon')), opt=True),
    ]


def geometry():
    head = head_parts()
    right_forearm = P('right_forearm', (-6, 6, 0),
                      P('r_fore', (-6, 6, 0), C(-8, 6, -2, 4, 6, 4, 'cloth:sleeve2')),
                      P('r_fore_slim', (-5.5, 6, 0), C(-7, 6, -2, 3, 6, 4, 'cloth:sleeve2'), opt=True),
                      P('gauntlet_r', (-6, 9, 0), C(-8.5, 8, -2.5, 5, 4, 5, 'metal:gauntlet'), opt=True),
                      P('cuff_r', (-5.5, 9, 0), C(-8, 8.5, -2.5, 5, 4, 5, 'cloth:cuff'), opt=True),
                      P('bracer_r', (-6, 9, 0), C(-8, 7.5, -2, 4, 3, 4, 'leather:bracer', inflate=0.4), opt=True),
                      *right_hand_props())
    left_forearm = P('left_forearm', (6, 6, 0),
                     P('l_fore', (6, 6, 0), C(4, 6, -2, 4, 6, 4, 'cloth:sleeve2')),
                     P('l_fore_slim', (5.5, 6, 0), C(4, 6, -2, 3, 6, 4, 'cloth:sleeve2'), opt=True),
                     P('gauntlet_l', (6, 9, 0), C(3.5, 8, -2.5, 5, 4, 5, 'metal:gauntlet'), opt=True),
                     P('cuff_l', (5.5, 9, 0), C(3, 8.5, -2.5, 5, 4, 5, 'cloth:cuff'), opt=True),
                     P('bracer_l', (6, 9, 0), C(4, 7.5, -2, 4, 3, 4, 'leather:bracer', inflate=0.4), opt=True))
    right_arm = P('right_arm', (-5, 2, 0),
                  P('r_upper', (-5, 2, 0), C(-8, 0, -2, 4, 6, 4, 'cloth:sleeve')),
                  P('r_upper_slim', (-5, 2, 0), C(-7, 0, -2, 3, 6, 4, 'cloth:sleeve'), opt=True),
                  P('pauldron_r', (-6, 0, 0), C(-9.5, -1.5, -3, 6, 3, 6, 'metal:pauldron'),
                    C(-10, 1, -3.5, 4, 2, 7, 'metal:pauldron'), opt=True),
                  right_forearm)
    left_arm = P('left_arm', (5, 2, 0),
                 P('l_upper', (5, 2, 0), C(4, 0, -2, 4, 6, 4, 'cloth:sleeve')),
                 P('l_upper_slim', (5, 2, 0), C(4, 0, -2, 3, 6, 4, 'cloth:sleeve'), opt=True),
                 # the dragon-scale pauldron: scales with ember fissures and a horn
                 P('pauldron_dragon', (6, 0, 0), C(3.5, -1.5, -3.5, 7, 3, 7, 'scale:dragon'),
                   C(4.5, 1.5, -3, 6, 2, 6, 'scale:dragon'), C(7, -4.5, -1, 1, 3, 2, 'bone:horn'), opt=True),
                 P('pauldron_s', (6, 0, 0), C(3.5, -1.5, -3, 6, 2, 6, 'metal:pauldron_s'), opt=True),
                 left_forearm)
    phantoms = [P('phantom_%d' % (i + 1), (0, 3, 4.5), C(-0.5, -9, 4.5, 1, 12, 1, 'plain:phantom'),
                  rot=(22, 0, a)) for i, a in enumerate((-80, -56, -32, 32, 56, 80))]
    chest = P('chest', (0, 7, 0),
              P('torso', (0, 7, 0), C(-4, 0, -2, 8, 7, 4, 'cloth:tunic')),
              P('torso_broad', (0, 7, 0), C(-5, 0, -2.5, 10, 7, 5, 'cloth:tunic'), opt=True),
              P('bust', (0, 1.5, -2), C(-3.5, 1.5, -3, 7, 3, 1, 'cloth:bust'), rot=(-12, 0, 0), opt=True),
              P('cuirass', (0, 7, 0), C(-5, 0, -2.5, 10, 7, 5, 'metal:cuirass', inflate=0.5), opt=True),
              head, right_arm, left_arm,
              P('collar_high', (0, 0, 0), C(-4.5, -2.5, -3, 9, 3, 7, 'cloth:collar'), opt=True),
              P('mantle_fur', (0, 0, 0), C(-6.5, -1.5, -3.5, 13, 3, 7, 'fur:mantle'), opt=True),
              P('shawl', (0, 0, 0), C(-5, -0.5, -3, 10, 3, 6, 'cloth:shawl'), opt=True),
              P('hair_long', (0, -1, 4.2), C(-4.5, -2, 3.4, 9, 9, 2, 'hair:hair_long'),
                P('hair_long2', (0, 7, 4.6), C(-4, 7, 3.8, 8, 7, 2, 'hair:hair_long')), rot=(-10, 0, 0), opt=True),
              P('tail', (0, -1.5, 4.3), C(-1, -1.5, 3.8, 2, 3, 2, 'hair:tail'),
                P('tail2', (0, 1.5, 4.5), C(-0.5, 1.5, 4, 1, 12, 1, 'hair:tail')), rot=(-6, 0, 0), opt=True),
              P('cape', (0, 0, 3), C(-6, 0, 2.8, 12, 11, 1, 'cloth:cape'),
                P('cape2', (0, 11, 3), C(-6, 11, 2.8, 12, 10, 1, 'cloth:cape')), rot=(5, 0, 0), opt=True),
              P('grimoire', (9.5, 3.5, -3), C(7.5, 3.5, -6, 4, 1, 6, 'leather:tome'),
                P('grimoire_page', (9.5, 3.4, -3), C(9.5, 3.4, -5.5, 2, 0, 5, 'plain:page')), rot=(-35, 0, 12),
                opt=True),
              P('phantoms', (0, 3, 4.5), *phantoms, opt=True))
    right_leg = P('right_leg', (-2, 12, 0), C(-4, 12, -2, 4, 6, 4, 'cloth:pants'),
                  P('right_shin', (-2, 18, 0), C(-4, 18, -2, 4, 6, 4, 'leather:boots'),
                    P('greave_r', (-2, 18, 0), C(-4, 18, -2, 4, 6, 4, 'metal:greave', inflate=0.35), opt=True),
                    P('knee_r', (-2, 18, -2), C(-4.5, 17, -2.8, 5, 2, 1, 'metal:knee'), opt=True)))
    left_leg = P('left_leg', (2, 12, 0), C(0, 12, -2, 4, 6, 4, 'cloth:pants'),
                 P('left_shin', (2, 18, 0), C(0, 18, -2, 4, 6, 4, 'leather:boots'),
                   P('greave_l', (2, 18, 0), C(0, 18, -2, 4, 6, 4, 'metal:greave', inflate=0.35), opt=True),
                   P('knee_l', (2, 18, -2), C(-0.5, 17, -2.8, 5, 2, 1, 'metal:knee'), opt=True)))
    hips = P('hips', (0, 12, 0), C(-4, 7, -2, 8, 5, 4, 'cloth:waist'),
             chest, right_leg, left_leg,
             P('dress', (0, 9, 0), C(-4.5, 9, -2.5, 9, 6, 5, 'cloth:dress'),
               P('dress2', (0, 15, 0), C(-5.5, 15, -3.5, 11, 9, 7, 'cloth:dress2')), opt=True),
             P('robe', (0, 9, 0), C(-4.5, 9, -2.5, 9, 7, 5, 'cloth:robe'),
               P('robe2', (0, 16, 0), C(-5, 16, -3, 10, 8, 6, 'cloth:robe2')), opt=True),
             P('faulds', (0, 9, 0), C(-5, 9, -3, 10, 4, 6, 'metal:faulds'), opt=True),
             P('tabard_f', (0, 10, -2.6), C(-3, 10, -3.3, 6, 10, 1, 'cloth:tabard'), opt=True),
             P('tabard_b', (0, 10, 2.6), C(-3.5, 10, 2.3, 7, 11, 1, 'cloth:tabard'), rot=(4, 0, 0), opt=True),
             P('coat_tail_r', (-2.2, 10, 2.2), C(-4.5, 10, 2, 4, 13, 1, 'cloth:coat_tail'), rot=(5, 0, 2), opt=True),
             P('coat_tail_l', (2.2, 10, 2.2), C(0.5, 10, 2, 4, 10, 1, 'cloth:coat_tail'), rot=(5, 0, -2), opt=True),
             P('coat_flap_r', (-2.5, 10, -2.4), C(-4.6, 10, -3, 3, 9, 1, 'cloth:coat_tail'), rot=(-3, 0, 3),
               opt=True),
             P('coat_flap_l', (2.5, 10, -2.4), C(1.6, 10, -3, 3, 7, 1, 'cloth:coat_tail'), rot=(-3, 0, -3),
               opt=True),
             # Kaylen's scabbard on the left hip and the hilt that shows while the sword is sheathed
             P('scabbard', (4.6, 9.5, -1), C(4.1, 9, -1.5, 1, 2, 16, 'leather:scabbard'),
               P('sheathed', (4.6, 9.5, -1), C(4.1, 9.5, -5, 1, 1, 4, 'leather:grip_rune'),
                 C(3.6, 9, -1.5, 2, 2, 1, 'gold:guard_rune'), C(4.1, 9, -6, 1, 2, 1, 'gem:pommel_gem')),
               rot=(-24, -8, 0), opt=True),
             P('seals', (-4.4, 9.5, 0), C(-4.9, 10, -2, 1, 2, 1, 'gem:seal_a'), C(-4.9, 10.5, -0.5, 1, 2, 1, 'gem:seal_b'),
               C(-4.9, 10, 1, 1, 2, 1, 'gem:seal_c'), C(-4.9, 12.5, -1.2, 1, 2, 1, 'gem:seal_d'),
               C(-4.9, 12.5, 0.4, 1, 2, 1, 'gem:seal_e'), opt=True),
             P('holybook', (-4.8, 9.5, 0.5), C(-6.2, 10, -1, 1, 4, 3, 'leather:holybook'),
               C(-5.3, 9, 0, 1, 1, 1, 'chain:chain'), rot=(0, 0, 8), opt=True))
    return [P('base', (0, 24, 0), hips)]


# ----------------------------------------------------------------------------------------------- animation kit

def blink(times, length, dur=0.13):
    """Eyelid plane keys (linear): closed for `dur` seconds at every time in `times`."""
    keys = [(0.0, 0, 0, 0)]
    for t in times:
        keys += [(t, 0, 0, 0), (t + 0.03, 0, 0, LID_OUT), (t + dur, 0, 0, LID_OUT), (t + dur + 0.03, 0, 0, 0)]
    keys.append((length, 0, 0, 0))
    return dict(pos=keys, lin=True)


def shut(t0, t1, length):
    """Eyes closed from t0 to t1 (prayers, a wink held, a finishing pose); t0 = 0 and t1 = length keep them shut."""
    keys = [(0.0, 0, 0, LID_OUT if t0 <= 0 else 0)]
    if t0 > 0:
        keys += [(t0, 0, 0, 0), (t0 + 0.04, 0, 0, LID_OUT)]
    if t1 < length - 0.05:
        keys += [(t1, 0, 0, LID_OUT), (t1 + 0.04, 0, 0, 0), (length, 0, 0, 0)]
    else:
        keys.append((length, 0, 0, LID_OUT))
    return dict(pos=keys, lin=True)


def flaps(start, end, length, period=0.17, rest_every=6, rest=0.3):
    """Mouth plane keys (linear): open/close flaps between start and end, with a short pause every few syllables."""
    keys = [(0.0, 0, 0, 0)]
    t = start
    n = 0
    while t + period <= end:
        keys += [(round(t, 3), 0, 0, 0), (round(t + 0.025, 3), 0, 0, LID_OUT),
                 (round(t + period * 0.55, 3), 0, 0, LID_OUT), (round(t + period * 0.55 + 0.025, 3), 0, 0, 0)]
        n += 1
        t += period
        if n % rest_every == 0:
            t += rest
    keys.append((length, 0, 0, 0))
    return dict(pos=keys, lin=True)


def shout(t0, t1, length):
    """Mouth open (a battle cry, the name of a move) from t0 to t1."""
    return dict(pos=[(0.0, 0, 0, 0), (t0, 0, 0, 0), (t0 + 0.03, 0, 0, LID_OUT), (t1, 0, 0, LID_OUT),
                     (t1 + 0.03, 0, 0, 0), (length, 0, 0, 0)], lin=True)


def wave(length, amp, period, phase=0.0, axis=0, offset=0.0, steps=None):
    """Sampled sine keyframes on one axis (rot/pos values), looping cleanly when length is a multiple of period."""
    n = steps or max(4, int(round(length / period * 4)))
    keys = []
    for i in range(n + 1):
        t = length * i / n
        v = offset + amp * math.sin(2 * math.pi * (t / period) + phase)
        vec = [0.0, 0.0, 0.0]
        vec[axis] = round(v, 3)
        keys.append((round(t, 4), vec[0], vec[1], vec[2]))
    return keys


def merge(*channels):
    """Sum sampled channels with identical times."""
    out = []
    for parts in zip(*channels):
        t = parts[0][0]
        out.append((t, sum(p[1] for p in parts), sum(p[2] for p in parts), sum(p[3] for p in parts)))
    return out


def spin(length, axis, turns=1.0, steps=8, t0=0.0, t1=None, offset=0.0):
    """Linear turn on one axis from t0 to t1 (whole length by default)."""
    t1 = length if t1 is None else t1
    keys = []
    if t0 > 0:
        keys.append((0.0, 0.0, 0.0, 0.0))
    for i in range(steps + 1):
        t = t0 + (t1 - t0) * i / steps
        vec = [0.0, 0.0, 0.0]
        vec[axis] = offset + 360.0 * turns * i / steps
        keys.append((round(t, 4), vec[0], vec[1], vec[2]))
    if t1 < length:
        keys.append((length, keys[-1][1], keys[-1][2], keys[-1][3]))
    return keys


def keys(*ks):
    """Shorthand: keys((t, x, y, z[, ease]), ...) -> list (eases default to Catmull-Rom)."""
    return list(ks)


def hold(pose_keys, length):
    """Holds the last key until `length` (the pose stays at the end of a one-shot)."""
    last = pose_keys[-1]
    if last[0] < length:
        pose_keys = pose_keys + [(length,) + tuple(last[1:4])]
    return pose_keys


def posed(name, length, frames, loop=False, **extra):
    """Pose-to-pose authoring: frames = [(t, pose, ease)], a pose maps bone -> (rx, ry, rz) or
    dict(rot=(...), pos=(...)); bones a pose leaves out are at rest there. `extra` adds hand-made channels
    (blink, mouth, spins) as in anim()."""
    bones = {}
    for t, pose, _e in frames:
        for bone, spec in pose.items():
            spec = spec if isinstance(spec, dict) else dict(rot=spec)
            for target in spec:
                bones.setdefault(bone, set()).add(target)
    spec = {}
    for bone, targets in bones.items():
        chans = {}
        for target in sorted(targets):
            ks = []
            for t, pose, e in frames:
                s = pose.get(bone)
                s = s if isinstance(s, dict) else (dict(rot=s) if s is not None else {})
                v = s.get(target, (1.0, 1.0, 1.0) if target == 'scale' else (0.0, 0.0, 0.0))
                ks.append((t, v[0], v[1], v[2], e))
            chans[target] = ks
        spec[bone] = chans
    for bone, ch in extra.items():
        if bone in spec:
            for target in ('rot', 'pos', 'scale'):
                if target in ch:
                    if target in spec[bone]:
                        raise ValueError('%s: %s %s set twice' % (name, bone, target))
                    spec[bone][target] = ch[target]
            if ch.get('lin'):
                for target in ('rot', 'pos', 'scale'):
                    if target in ch:
                        spec[bone][target + '_interp'] = 'linear'
        else:
            spec[bone] = ch
    return anim(name, length, loop=loop, **spec)


def add(*poses):
    """Sum of poses (bone -> rot tuples or dicts)."""
    out = {}
    for pose in poses:
        for bone, s in pose.items():
            s = s if isinstance(s, dict) else dict(rot=s)
            o = out.setdefault(bone, {})
            for target, v in s.items():
                base = o.get(target, (0.0, 0.0, 0.0))
                o[target] = tuple(base[i] + v[i] for i in range(3))
    return out


# ----------------------------------------------------------------------------------------------------- the rig

PREFIX = {'traveller_rosalind': 'ros', 'traveller_siegwald': 'sieg', 'traveller_kaylen': 'kay',
          'traveller_mariel': 'mar'}
# animation name -> seconds at which its blows land (HeroBodies.hits for the server)
HITS = {}


def rig():
    import hero_anims
    import hero_cast
    anims, hits = hero_anims.animations()
    HITS.clear()
    HITS.update(hits)
    looks = hero_cast.looks()
    sets = {v.id: {s: PREFIX[v.id] + '_' + s for s in SLOTS} for v in looks}
    r = Rig('hero', 'Hero', geometry(), anims, looks, SLOTS, anim_sets=sets, texel=TEXEL)
    return r
