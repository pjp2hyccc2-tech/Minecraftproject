"""NPC rig: the town and story people of Astoria (22 named characters, 8 dungeon wardens and a plain citizen).

Player-sized humanoid (32 units) with elbows and knees like the HUMANOID enemy rig, plus a large set of optional parts
so silhouettes differ, not only colours: arm builds (wide/slim) and torsos (standard/broad/belly), hair shells and
pieces (ponytail, buns, braids, long hair), beards and moustaches, headwear (helmet/hood shell, band, top hat, cap brim,
turban, beret, tiara, crown, crest, wings, lamp, halo), glasses and goggles, collars, scarves, fur mantles, capes, coat
tails, aprons, skirts and robes, satchels and pouches, and held props (hammers, ledger and quill, coin pouch, dish and
rag, ladle and bowl, papers and stamp, gavel, trumpet, practice sword, drinking horn, polearms, staffs, floating book,
armillary sphere, a pigeon on the shoulder, lute, broom, basket, candle, scepter, longbow, kite shield, quiver,
backpack, sack, straw hat, mitre, wizard hat, pipe, wreath...).

Two face planes animate the face: 'eyelids' (blink) and 'mouth' (talk) rest just inside the head and step in front
of the face for a few frames. Slots: idle (always), walk (townsfolk strolling; follows the movement), work
(per-character loop, faded out while a player is near, while walking or talking) and talk (played once when a player
talks to the NPC). The appearances are defined in npc_cast*.py.
"""
import math

from ermodel import C, P, Rig, anim

SLOTS = ['idle', 'walk', 'work', 'talk']

# The face planes rest this far inside the head; animations move them out by LID_OUT (posVec z, negative = front).
LID_OUT = -0.78


# ------------------------------------------------------------------------------------------------- geometry

def head_parts():
    return P('head', (0, 0, 0),
             C(-4, -8, -4, 8, 8, 8, 'skin:skin'),
             P('eyelids', (0, -4, -4), C(-4, -8, -3.3, 8, 8, 0, 'plain:fx')),
             P('mouth', (0, -2, -4), C(-4, -4, -3.3, 8, 4, 0, 'plain:fx')),
             # hair and headwear shells (painted with transparency like a skin's hat layer)
             P('hair_cap', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'hair:hair', inflate=0.5), opt=True),
             P('band', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'cloth:band', inflate=0.75), opt=True),
             P('shell', (0, 0, 0), C(-4, -8, -4, 8, 8, 8, 'cloth:shell', inflate=1.0), opt=True),
             # hair pieces
             P('ponytail', (0, -6, 4.4), C(-1.5, -7.5, 4.3, 3, 3, 2, 'hair:hair'),
               P('ponytail_tail', (0, -6, 5.8), C(-1.5, -6.5, 5.3, 3, 9, 2, 'hair:hair'), rot=(22, 0, 0)), opt=True),
             P('bun_top', (0, -8, 0), C(-2, -11, -1.5, 4, 3, 4, 'hair:hair'), opt=True),
             P('bun_back', (0, -5, 4.5), C(-2, -7, 4.3, 4, 4, 3, 'hair:hair'), opt=True),
             P('ear_r', (-4, -4, 0), C(-8, -5, -0.5, 4, 2, 1, 'skin:ear'), rot=(0, 0, 22), opt=True),
             P('ear_l', (4, -4, 0), C(4, -5, -0.5, 4, 2, 1, 'skin:ear'), rot=(0, 0, -22), opt=True),
             # facial hair and eyewear (in front of the face; the beard leaves a mouth gap)
             P('beard', (0, -2, -4), C(-4, -3, -4.9, 8, 6, 1, 'hair:beard'), opt=True),
             P('beard_big', (0, -2, -4), C(-4.5, -3, -5.6, 9, 8, 2, 'hair:beard'), opt=True),
             P('beard_braids', (0, 3, -4.5), C(-3, 3, -5.2, 2, 5, 1, 'hair:beard'), C(1, 3, -5.2, 2, 5, 1, 'hair:beard'),
               opt=True),
             P('mustache', (0, -2, -4), C(-5, -3, -4.3, 10, 2, 0, 'hair:beard'), opt=True),
             P('glasses', (0, -4, -4), C(-4, -6, -4.25, 8, 4, 0, 'plain:glass'), opt=True),
             P('goggles', (0, -7, -4), C(-3.5, -8, -4.9, 3, 2, 1, 'metal:goggle'), C(0.5, -8, -4.9, 3, 2, 1, 'metal:goggle'),
               opt=True),
             P('candy', (1.5, -1.5, -4), C(1, -2, -6, 1, 1, 2, 'plain:stick'), C(0.5, -2.5, -7.5, 2, 2, 2, 'gem:candy'),
               opt=True),
             # headwear pieces
             P('tiara', (0, -8, -2), C(-3, -10.5, -2.5, 6, 3, 0, 'gold:tiara'), opt=True),
             P('crown_small', (0, -8.5, 0), C(-2.5, -11.5, -2.5, 5, 3, 5, 'gold:crown'), rot=(0, 18, 14), opt=True),
             P('top_hat', (0, -8.5, 0), C(-5.5, -9.5, -5.5, 11, 1, 11, 'cloth:hat'), C(-3.5, -15.5, -3.5, 7, 6, 7, 'cloth:hat2'),
               rot=(0, 0, 8), opt=True),
             P('brim', (0, -6.5, -4.5), C(-4.5, -6.5, -8.5, 9, 1, 4, 'cloth:hat'), rot=(-8, 0, 0), opt=True),
             P('turban', (0, -9, 0), C(-5, -12, -5, 10, 3, 10, 'cloth:turban'), C(-1, -10.5, -5.6, 2, 2, 1, 'gem:jewel'),
               opt=True),
             P('beret', (0, -9, 0), C(-5, -10.5, -4.5, 10, 2, 9, 'cloth:hat'), C(3.5, -16, 0.5, 1, 6, 1, 'feather:quill'),
               rot=(0, 0, -10), opt=True),
             P('helm_crest', (0, -9, 0), C(-0.5, -13, -4, 1, 4, 9, 'cloth:plume'), opt=True),
             P('helm_wing_r', (-5, -6, 1), C(-6, -12, 0, 1, 6, 4, 'feather:wing'), rot=(-20, 0, -18), opt=True),
             P('helm_wing_l', (5, -6, 1), C(5, -12, 0, 1, 6, 4, 'feather:wing'), rot=(-20, 0, 18), opt=True),
             P('helm_lamp', (0, -8, -5), C(-1.5, -9, -5.6, 3, 2, 1, 'metal:lamp'), opt=True),
             P('headband_tails', (0, -5, 4.8), C(-1.5, -5, 4.8, 1, 6, 1, 'cloth:band'), C(0.5, -5, 4.8, 1, 5, 1, 'cloth:band'),
               rot=(14, 0, 0), opt=True),
             P('halo', (0, -6, 6.2), C(-6, -12, 6.2, 12, 12, 0, 'gold:halo'), opt=True),
             P('straw_hat', (0, -8.5, 0), C(-7, -8.8, -7, 14, 1, 14, 'cloth:straw'), C(-4, -11.8, -4, 8, 3, 8, 'cloth:straw2'),
               rot=(-5, 0, 0), opt=True),
             P('mitre', (0, -8.5, 0), C(-3, -15.5, -2.5, 6, 7, 5, 'cloth:mitre'), opt=True),
             P('wizard_hat', (0, -8.5, 0), C(-6, -9.5, -6, 12, 1, 12, 'cloth:hat'), C(-3.5, -13.5, -3.5, 7, 4, 7, 'cloth:hat2'),
               P('wizard_tip', (0, -13.5, 0), C(-2, -17.5, -2, 4, 4, 4, 'cloth:hat2'), C(-1, -20.5, -1, 2, 3, 2, 'cloth:hat2'),
                 rot=(-18, 0, 12)), opt=True),
             P('pipe', (1.5, -1.5, -4), C(1, -2, -7, 1, 1, 3, 'wood:pipe'), C(0.5, -4, -8.5, 2, 3, 2, 'wood:pipe_bowl'),
               opt=True),
             P('wreath', (0, -8.5, 0), C(-4.5, -9.2, -4.5, 9, 1, 9, 'leaf:wreath'), opt=True),
             P('veil_back', (0, -6, 4.6), C(-4.5, -6, 4.4, 9, 13, 1, 'cloth:veil'), rot=(-6, 0, 0), opt=True))


def right_hand_props():
    """Children of the right forearm, held at the right hand (about (-6, 11, 0))."""
    return [
        P('hammer', (-6, 11, 0), C(-6.5, 10.5, -6, 1, 1, 7, 'wood:haft'), C(-8, 8.5, -8.5, 4, 3, 3, 'metal:tool'),
          rot=(18, 0, 0), opt=True),
        P('hammer_big', (-6, 11, 0), C(-6.5, 8, -0.5, 1, 11, 1, 'wood:haft'), C(-8.5, 19, -2.5, 5, 5, 5, 'metal:tool'),
          opt=True),
        P('quill', (-6, 11, -1), C(-6.5, 7, -1.5, 1, 5, 1, 'feather:quill'), rot=(-35, 0, 0), opt=True),
        P('coin', (-6, 11.5, -1), C(-6.5, 11.5, -1.5, 1, 1, 1, 'gold:coin'), opt=True),
        P('rag', (-6, 11, 0), C(-7.5, 11, -2, 3, 2, 3, 'cloth:rag'), opt=True),
        P('ladle', (-6, 11, 0), C(-6.5, 10.5, -7, 1, 1, 7, 'wood:ladle'), C(-7.5, 10, -9, 3, 2, 2, 'metal:tool'),
          rot=(25, 0, 0), opt=True),
        P('stamp', (-6, 11, 0), C(-6.5, 8.5, -0.5, 1, 3, 1, 'wood:handle'), C(-7.5, 11.5, -1.5, 3, 2, 3, 'metal:tool'),
          opt=True),
        P('gavel', (-6, 11, 0), C(-6.5, 10.5, -6, 1, 1, 6, 'wood:handle'), C(-7.5, 9.5, -8, 3, 3, 2, 'wood:gavel'),
          rot=(25, 0, 0), opt=True),
        P('trumpet', (-6, 11, 0), C(-6.5, 10.5, -5, 1, 1, 5, 'gold:brass'), C(-7.5, 9.5, -8, 3, 3, 3, 'gold:brass'),
          rot=(20, 0, 0), opt=True),
        P('practice_sword', (-6, 11, 0), C(-6.5, 10.5, -1.5, 1, 1, 3, 'leather:grip'), C(-7.5, 10, -2.5, 3, 2, 1, 'wood:guard'),
          C(-6.5, 10, -13.5, 1, 2, 11, 'wood:blade'), rot=(28, 0, 0), opt=True),
        P('drink_horn', (-6, 11, 0), C(-7, 9, -3, 2, 3, 2, 'bone:horn'), C(-7, 7, -4.5, 2, 2, 2, 'bone:horn'),
          C(-6.5, 6, -5.5, 1, 2, 1, 'bone:horn'), opt=True),
        P('letter', (-6, 11, 0), C(-7.5, 10.5, -3.5, 3, 2, 1, 'plain:paper'), opt=True),
        P('halberd', (-6, 11, 0), C(-6.5, -13, -0.5, 1, 37, 1, 'wood:pole'), C(-10.5, -11, -0.5, 4, 5, 1, 'metal:blade'),
          C(-6.5, -16, -0.5, 1, 3, 1, 'metal:blade'), opt=True),
        P('spear', (-6, 11, 0), C(-6.5, -11, -0.5, 1, 35, 1, 'wood:pole'), C(-7, -15, -1, 2, 4, 2, 'metal:blade'),
          opt=True),
        P('glaive', (-6, 11, 0), C(-6.5, -9, -0.5, 1, 33, 1, 'wood:pole'), C(-7, -16, -2.5, 1, 7, 4, 'metal:blade'),
          opt=True),
        P('lantern_staff', (-6, 11, 0), C(-6.5, -10, -0.5, 1, 34, 1, 'wood:pole'), C(-10.5, -10, -0.5, 4, 1, 1, 'metal:iron'),
          P('lantern', (-10, -9, 0), C(-11.5, -8.5, -1.5, 3, 4, 3, 'metal:lantern'), C(-10.5, -9.5, -0.5, 1, 1, 1, 'metal:iron')),
          opt=True),
        P('staff_nature', (-6, 11, 0), C(-6.5, -6, -0.5, 1, 30, 1, 'bark:staff'), C(-8, -8, -1, 2, 2, 2, 'bark:staff'),
          C(-5.5, -7, -0.5, 2, 1, 1, 'leaf:leafy'),
          P('staff_bud', (-7, -9.5, 0), C(-8.5, -11, -1.5, 3, 3, 3, 'gem:bud')), opt=True),
        P('shackle_r', (-6, 9, 0), C(-8.5, 8.5, -2.5, 5, 2, 5, 'metal:iron'), C(-6.5, 10.5, 1.5, 1, 4, 1, 'chain:chain'),
          opt=True),
        # held upright when the forearm points forward: these props extend towards -z from the fist
        P('potion_hand', (-6, 11, -2), C(-7, 10, -5, 2, 2, 3, 'gem:potion_hand'), C(-6.5, 10.5, -6, 1, 1, 1, 'wood:cork'),
          opt=True),
        P('candle', (-6, 11, -2), C(-7, 10, -3, 2, 2, 1, 'gold:holder'), C(-6.5, 10.5, -6, 1, 1, 3, 'plain:wax'),
          C(-6.5, 10.5, -7, 1, 1, 1, 'flame:candle_flame'), opt=True),
        P('scepter', (-6, 11, -1), C(-6.5, 10.5, -10, 1, 1, 9, 'gold:scepter'),
          C(-7.5, 9.5, -13, 3, 3, 3, 'gem:scepter_gem'), opt=True),
        # resting on the ground
        P('cane', (-6, 11, 0), C(-6.5, 11, -1.5, 1, 13, 1, 'wood:cane'), C(-8, 10, -1.5, 3, 1, 1, 'wood:cane'), opt=True),
        P('broom', (-6, 11, 0), C(-6.5, -3, -0.5, 1, 21, 1, 'wood:pole'), C(-8, 18, -2, 4, 6, 3, 'cloth:bristle'), opt=True),
        P('pitchfork', (-6, 11, 0), C(-6.5, -10, -0.5, 1, 34, 1, 'wood:pole'), C(-8.5, -11, -0.5, 5, 1, 1, 'metal:iron'),
          C(-8.5, -15, -0.5, 1, 4, 1, 'metal:iron'), C(-6.5, -15, -0.5, 1, 4, 1, 'metal:iron'),
          C(-4.5, -15, -0.5, 1, 4, 1, 'metal:iron'), opt=True),
    ]


def left_hand_props():
    """Children of the left forearm, held at the left hand (about (6, 11, 0))."""
    return [
        P('ledger', (6, 11, -1), C(2.5, 11, -6, 6, 1, 7, 'leather:book'), rot=(62, 0, 0), opt=True),
        P('coin_pouch', (6, 11, 0), C(4.5, 11, -1.5, 3, 3, 3, 'leather:pouch'), opt=True),
        P('plate', (6, 11, -1), C(2.5, 11, -6, 6, 1, 6, 'plain:plate'), rot=(62, 0, 0), opt=True),
        P('bowl', (6, 11, -1), C(3, 11, -5.5, 5, 2, 5, 'wood:bowl'), rot=(62, 0, 0), opt=True),
        P('papers', (6, 11, -1), C(2.5, 11, -6.5, 6, 2, 6, 'plain:paper'), rot=(62, 0, 0), opt=True),
        P('tome', (6, 11, -1), C(1.5, 11, -7, 8, 2, 7, 'leather:book'),
          P('tome_page', (5.5, 10.9, -3.5), C(5.5, 10.9, -6.5, 3, 0, 6, 'plain:page')), rot=(62, 0, 0), opt=True),
        P('gauntlet_l', (6, 9, 0), C(3.5, 7, -2.5, 5, 5, 5, 'scale:gauntlet'), opt=True),
        P('basket', (6, 11, 0), C(3.5, 12, -2.5, 5, 4, 5, 'wood:basket'), C(5.5, 9, -0.5, 1, 3, 1, 'wood:basket_handle'),
          opt=True),
        P('fan', (6, 11, -2), C(3.5, 9.5, -7, 5, 0, 5, 'cloth:fan'), opt=True),
        P('longbow', (6, 11, 0), C(6, 0, -3, 0, 22, 5, 'wood:longbow'), opt=True),
        P('shield_arm', (6, 9, -3), C(2, 3, -4.5, 8, 11, 1, 'wood:kite'), opt=True),
    ]


def geometry():
    head = head_parts()
    right_forearm = P('right_forearm', (-6, 6, 0),
                      P('r_fore', (-6, 6, 0), C(-8, 6, -2, 4, 6, 4, 'cloth:sleeve2')),
                      P('r_fore_slim', (-6, 6, 0), C(-7, 6, -2, 3, 6, 4, 'cloth:sleeve2'), opt=True),
                      P('bracer_r', (-6, 9, 0), C(-8, 7.5, -2, 4, 3, 4, 'leather:bracer', inflate=0.45), opt=True),
                      *right_hand_props())
    left_forearm = P('left_forearm', (6, 6, 0),
                     P('l_fore', (6, 6, 0), C(4, 6, -2, 4, 6, 4, 'cloth:sleeve2')),
                     P('l_fore_slim', (6, 6, 0), C(4, 6, -2, 3, 6, 4, 'cloth:sleeve2'), opt=True),
                     P('bracer_l', (6, 9, 0), C(4, 7.5, -2, 4, 3, 4, 'leather:bracer', inflate=0.45), opt=True),
                     *left_hand_props())
    right_arm = P('right_arm', (-5, 2, 0),
                  P('r_upper', (-5, 2, 0), C(-8, 0, -2, 4, 6, 4, 'cloth:sleeve')),
                  P('r_upper_slim', (-5, 2, 0), C(-7, 0, -2, 3, 6, 4, 'cloth:sleeve'), opt=True),
                  P('pauldron_r', (-6, 0, 0), C(-9, -1, -3, 5, 3, 6, 'metal:armor'), opt=True),
                  right_forearm)
    left_arm = P('left_arm', (5, 2, 0),
                 P('l_upper', (5, 2, 0), C(4, 0, -2, 4, 6, 4, 'cloth:sleeve')),
                 P('l_upper_slim', (5, 2, 0), C(4, 0, -2, 3, 6, 4, 'cloth:sleeve'), opt=True),
                 P('pauldron_l', (6, 0, 0), C(4, -1, -3, 5, 3, 6, 'metal:armor'), opt=True),
                 left_forearm)
    chest = P('chest', (0, 7, 0),
              P('torso', (0, 7, 0), C(-4, 0, -2, 8, 7, 4, 'cloth:tunic')),
              P('torso_broad', (0, 7, 0), C(-5, 0, -2.5, 10, 7, 5, 'cloth:tunic'), opt=True),
              P('belly', (0, 6, -2), C(-4.5, 3, -3.5, 9, 6, 2, 'cloth:tunic'), opt=True),
              head, right_arm, left_arm,
              P('collar', (0, 0, 0), C(-5, -2, -3.5, 10, 3, 8, 'cloth:collar'), opt=True),
              P('scarf', (0, 0, 0), C(-4.5, -1, -2.6, 9, 2, 5, 'cloth:scarf'),
                P('scarf_tail', (2.5, 1, -2.8), C(1.5, 1, -3.4, 2, 6, 1, 'cloth:scarf'), rot=(-6, 0, 8)), opt=True),
              P('mantle', (0, 0, 0), C(-8.5, -1, -3, 17, 3, 6, 'fur:mantle'), opt=True),
              P('braid_r', (-3.5, -2, -2.5), C(-4.5, -2, -3.4, 2, 11, 2, 'hair:hair'), rot=(-4, 0, 4), opt=True),
              P('braid_l', (3.5, -2, -2.5), C(2.5, -2, -3.4, 2, 11, 2, 'hair:hair'), rot=(-4, 0, -4), opt=True),
              P('hair_back', (0, -1, 4.2), C(-4.5, -1, 3.6, 9, 11, 1, 'hair:hair'), rot=(-12, 0, 0), opt=True),
              P('cape', (0, 0, 2.2), C(-6, 0, 2.2, 12, 20, 1, 'cloth:cape'), rot=(5, 0, 0), opt=True),
              P('apron_bib', (0, 1, -2), C(-3, 1, -2.7, 6, 6, 1, 'cloth:apron'), opt=True),
              P('potions', (0, 1, -2), C(-3, 0.5, -3.2, 1, 2, 1, 'gem:potion_r'), C(-1, 1.5, -3.2, 1, 2, 1, 'gem:potion_b'),
                C(1, 2.5, -3.2, 1, 2, 1, 'gem:potion_g'), opt=True),
              P('pigeon', (6, -1, 0), C(4.5, -3.5, -1.5, 3, 3, 4, 'feather:pigeon'), C(5, -2.5, 2.5, 2, 1, 3, 'feather:pigeon_tail'),
                P('pigeon_head', (6, -3.5, -1.5), C(5, -5.5, -2.5, 2, 2, 2, 'feather:pigeon_head'),
                  C(5.5, -4.5, -3.5, 1, 1, 1, 'plain:beak')),
                P('pigeon_wing_r', (4.5, -3, 0), C(4, -3, -1.5, 1, 2, 4, 'feather:pigeon_wing')),
                P('pigeon_wing_l', (7.5, -3, 0), C(7, -3, -1.5, 1, 2, 4, 'feather:pigeon_wing')), opt=True),
              P('carpet', (0, 2, 3.5), C(-6.5, 0.5, 2.2, 13, 3, 3, 'cloth:carpet'), opt=True),
              P('shield_back', (0, 3.5, 2.6), C(-4.5, -1, 2.6, 9, 9, 1, 'wood:shield'), rot=(4, 0, 0), opt=True),
              P('bow_back', (0, 3, 2.6), C(-0.5, -6, 2.6, 1, 18, 1, 'wood:bow'), rot=(0, 0, 32), opt=True),
              P('book_float', (0, 3, -7.5), C(-3.5, 2.5, -10, 7, 1, 5, 'leather:book'),
                P('book_page', (0, 2.4, -7.5), C(0, 2.4, -9.7, 3, 0, 4, 'plain:page')), rot=(-38, 0, 0), opt=True),
              P('armillary', (0, 4, -6.5), C(-1, 3, -7.5, 2, 2, 2, 'gem:orb'),
                P('ring_a', (0, 4, -6.5), C(-3, 1, -6.5, 6, 6, 0, 'gold:ring')),
                P('ring_b', (0, 4, -6.5), C(-3, 1, -6.5, 6, 6, 0, 'gold:ring'), rot=(0, 90, 0)), opt=True),
              P('lute', (-1, 5, -3), C(-4, 3, -5.5, 5, 5, 2, 'wood:lute'), C(1, 4.5, -5, 8, 1, 1, 'wood:lute_neck'),
                C(9, 4, -5.2, 2, 2, 1, 'wood:lute_head'), rot=(0, 0, -38), opt=True),
              P('quiver', (2, 3, 2.5), C(0.5, -2, 2.2, 3, 9, 3, 'leather:quiver'), C(1, -4, 2.7, 2, 2, 2, 'feather:fletch'),
                rot=(0, 0, -24), opt=True),
              P('backpack', (0, 3, 2), C(-3.5, 0, 2.1, 7, 7, 3, 'leather:pack'), C(-4.5, -1.5, 2.6, 9, 2, 2, 'cloth:bedroll'),
                opt=True),
              P('sack', (5, -1, 1), C(3, -5, -2, 6, 5, 6, 'cloth:sack'), rot=(0, 0, 12), opt=True))
    right_leg = P('right_leg', (-2, 12, 0), C(-4, 12, -2, 4, 6, 4, 'cloth:pants'),
                  P('right_shin', (-2, 18, 0), C(-4, 18, -2, 4, 6, 4, 'leather:boots'),
                    P('fur_cuff_r', (-2, 18, 0), C(-4, 17.5, -2, 4, 2, 4, 'fur:cuff', inflate=0.5), opt=True),
                    P('shackle_ankle', (-2, 22, 0), C(-4.5, 21, -2.5, 5, 1, 5, 'metal:iron'),
                      C(-2.5, 23, -6.5, 1, 1, 4, 'chain:chain'), opt=True)))
    left_leg = P('left_leg', (2, 12, 0), C(0, 12, -2, 4, 6, 4, 'cloth:pants'),
                 P('left_shin', (2, 18, 0), C(0, 18, -2, 4, 6, 4, 'leather:boots'),
                   P('fur_cuff_l', (2, 18, 0), C(0, 17.5, -2, 4, 2, 4, 'fur:cuff', inflate=0.5), opt=True)))
    hips = P('hips', (0, 12, 0), C(-4, 7, -2, 8, 5, 4, 'cloth:belly'),
             chest, right_leg, left_leg,
             P('skirt_short', (0, 9, 0), C(-4.5, 9, -2.5, 9, 7, 5, 'cloth:skirt'), opt=True),
             P('skirt_long', (0, 9, 0), C(-4.5, 9, -2.5, 9, 7, 5, 'cloth:skirt'), C(-5.5, 16, -3.5, 11, 8, 7, 'cloth:skirt2'),
               opt=True),
             P('robe_long', (0, 9, 0), C(-4.5, 9, -2.5, 9, 15, 5, 'cloth:robe'), opt=True),
             P('coat_tails', (0, 11, 2), C(-4, 11, 2.1, 8, 9, 1, 'cloth:coat'), rot=(6, 0, 0), opt=True),
             P('apron', (0, 9, -2), C(-4, 9, -2.7, 8, 10, 1, 'cloth:apron'), opt=True),
             P('sword_hip', (4.5, 9, -1), C(4.2, 5, -1.5, 1, 3, 1, 'leather:grip'), C(3.7, 8, -2, 2, 1, 2, 'gold:guard'),
               C(4.2, 9, -2, 1, 10, 2, 'leather:scabbard'), rot=(22, 0, -6), opt=True),
             P('pouch', (0, 9, 0), C(2, 8.5, -2.9, 2, 2, 1, 'leather:pouch'), C(-4.6, 8.5, -1, 1, 2, 2, 'leather:pouch'), opt=True),
             P('satchel', (-5, 9, 0), C(-6.5, 7.5, -2.5, 2, 5, 5, 'leather:satchel'), opt=True),
             P('keyring', (3.5, 10, -2), C(3, 9.5, -2.9, 2, 3, 1, 'metal:keys'), opt=True),
             P('greatsword', (0, 12, -5.5), C(-0.5, 8, -6, 1, 4, 1, 'leather:grip'), C(-3, 12, -6.5, 6, 1, 2, 'metal:guard'),
               C(-1, 13, -6, 2, 11, 1, 'metal:blade'), opt=True))
    return [P('base', (0, 24, 0), hips)]


# ----------------------------------------------------------------------------------------------- animations

def blink(times, length, dur=0.13):
    """Eyelid plane keys (linear): closed for `dur` seconds at every time in `times`."""
    keys = [(0.0, 0, 0, 0)]
    for t in times:
        keys += [(t, 0, 0, 0), (t + 0.03, 0, 0, LID_OUT), (t + dur, 0, 0, LID_OUT), (t + dur + 0.03, 0, 0, 0)]
    keys.append((length, 0, 0, 0))
    return dict(pos=keys, lin=True)


def flaps(start, end, length, period=0.17, rest_every=6, rest=0.3):
    """Mouth plane keys (linear): open/close flaps between start and end, with a short pause every few syllables."""
    keys = [(0.0, 0, 0, 0)]
    t = start
    n = 0
    while t + period <= end:
        keys += [(round(t, 3), 0, 0, 0), (round(t + 0.025, 3), 0, 0, LID_OUT), (round(t + period * 0.55, 3), 0, 0, LID_OUT),
                 (round(t + period * 0.55 + 0.025, 3), 0, 0, 0)]
        n += 1
        t += period
        if n % rest_every == 0:
            t += rest
    keys.append((length, 0, 0, 0))
    return dict(pos=keys, lin=True)


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


def spin(length, axis, turns=1.0, steps=8):
    keys = []
    for i in range(steps + 1):
        t = length * i / steps
        vec = [0.0, 0.0, 0.0]
        vec[axis] = 360.0 * turns * i / steps
        keys.append((round(t, 4), vec[0], vec[1], vec[2]))
    return keys


def idle_anims():
    L = 6.0
    A = []
    sway = dict(
        chest=dict(rot=merge(wave(L, -1.3, 3.0, axis=0, offset=-0.6), wave(L, 0.7, 6.0, phase=math.pi, axis=2))),
        hips=dict(rot=wave(L, 1.2, 6.0, axis=2)),
        right_leg=dict(rot=wave(L, -1.2, 6.0, axis=2)),
        left_leg=dict(rot=wave(L, -1.2, 6.0, axis=2)),
        head=dict(rot=[(0, 0, 0, 0), (1.6, 1.5, -3, 0), (3.2, -0.5, 1, 0.8), (4.6, 0.8, 3, 0), (6.0, 0, 0, 0)]),
        right_arm=dict(rot=wave(L, 1.6, 3.0, axis=2, offset=1.0)),
        left_arm=dict(rot=wave(L, -1.6, 3.0, axis=2, offset=-1.0)),
        right_forearm=dict(rot=wave(L, -2.0, 3.0, phase=0.6, axis=0)),
        left_forearm=dict(rot=wave(L, -2.0, 3.0, phase=0.9, axis=0)),
        eyelids=blink((1.9, 4.6, 4.85), L),
        cape=dict(rot=wave(L, 2.0, 3.0, phase=0.8, axis=0, offset=1.0)),
        coat_tails=dict(rot=wave(L, 2.0, 3.0, phase=0.8, axis=0, offset=1.0)),
        skirt_long=dict(rot=wave(L, 0.8, 6.0, phase=1.0, axis=2)),
        robe_long=dict(rot=wave(L, 0.8, 6.0, phase=1.0, axis=2)),
        skirt_short=dict(rot=wave(L, 1.0, 6.0, phase=1.0, axis=2)),
        hair_back=dict(rot=wave(L, 1.5, 3.0, phase=1.2, axis=0)),
        ponytail_tail=dict(rot=merge(wave(L, 4.0, 3.0, phase=0.5, axis=2), wave(L, 3.0, 3.0, phase=1.4, axis=0))),
        braid_r=dict(rot=wave(L, 1.5, 3.0, phase=0.4, axis=2)),
        braid_l=dict(rot=wave(L, -1.5, 3.0, phase=0.4, axis=2)),
        headband_tails=dict(rot=merge(wave(L, 6.0, 3.0, phase=0.2, axis=2), wave(L, 4.0, 1.5, axis=0))),
        scarf_tail=dict(rot=wave(L, 4.0, 3.0, phase=0.7, axis=2)),
        lantern=dict(rot=wave(L, 4.0, 3.0, axis=2)),
        pigeon_head=dict(rot=[(0, 0, 0, 0), (1.0, 0, 25, 0), (1.4, 0, 25, 0), (2.2, 10, -15, 0), (3.8, 0, -20, 0),
                              (4.6, -5, 10, 0), (6.0, 0, 0, 0)]),
        halo=dict(rot=spin(L, 2, 0.5), lin=True),
        staff_bud=dict(scale=[(0, 1, 1, 1), (1.5, 1.12, 1.12, 1.12), (3.0, 1, 1, 1), (4.5, 1.12, 1.12, 1.12), (6.0, 1, 1, 1)]),
    )
    A.append(anim('idle', L, loop=True, **sway))
    # soldiers and wardens: the same idle with a steadier stance
    still = {}
    for bone, spec in sway.items():
        s = dict(spec)
        if bone in ('chest', 'hips', 'right_leg', 'left_leg', 'right_arm', 'left_arm', 'head'):
            s['rot'] = [(k[0], k[1] * 0.5, k[2] * 0.5, k[3] * 0.5) for k in spec['rot']]
        still[bone] = s
    A.append(anim('idle_still', L, loop=True, **still))
    # Selene hovers above the ground
    hover = dict(sway)
    hover['base'] = dict(pos=wave(L, 0.5, 3.0, axis=1, offset=2.0))
    hover['robe_long'] = dict(rot=merge(wave(L, 2.0, 3.0, phase=1.0, axis=0, offset=1.0), wave(L, 1.0, 6.0, axis=2)))
    A.append(anim('idle_hover', L, loop=True, **hover))
    return A


def walk_anims():
    """Strolling townsfolk (vanilla animateWalk drives the time from the distance walked; 1 s = two steps)."""
    sway = [(0, 3, 0, 2.5), (0.5, 3, 0, -2.5), (1.0, 3, 0, 2.5)]
    return [anim('walk', 1.0, loop=True,
                 right_leg=dict(rot=[(0, 24, 0, 0), (0.5, -24, 0, 0), (1.0, 24, 0, 0)]),
                 left_leg=dict(rot=[(0, -24, 0, 0), (0.5, 24, 0, 0), (1.0, -24, 0, 0)]),
                 right_shin=dict(rot=[(0, 10, 0, 0), (0.25, 38, 0, 0), (0.5, 4, 0, 0), (0.75, 6, 0, 0), (1.0, 10, 0, 0)]),
                 left_shin=dict(rot=[(0, 4, 0, 0), (0.25, 6, 0, 0), (0.5, 10, 0, 0), (0.75, 38, 0, 0), (1.0, 4, 0, 0)]),
                 right_arm=dict(rot=[(0, -16, 0, 0), (0.5, 16, 0, 0), (1.0, -16, 0, 0)]),
                 left_arm=dict(rot=[(0, 16, 0, 0), (0.5, -16, 0, 0), (1.0, 16, 0, 0)]),
                 right_forearm=dict(rot=[(0, -10, 0, 0), (0.5, -3, 0, 0), (1.0, -10, 0, 0)]),
                 left_forearm=dict(rot=[(0, -3, 0, 0), (0.5, -10, 0, 0), (1.0, -3, 0, 0)]),
                 base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.55, 0), (0.5, 0, 0, 0), (0.75, 0, 0.55, 0), (1.0, 0, 0, 0)]),
                 hips=dict(rot=[(0, 0, 4, 0), (0.5, 0, -4, 0), (1.0, 0, 4, 0)]),
                 chest=dict(rot=[(0, 2, -5, 0), (0.5, 2, 5, 0), (1.0, 2, -5, 0)]),
                 head=dict(rot=[(0, -1, 4, 0), (0.25, 0, 0, 0), (0.5, -1, -4, 0), (0.75, 0, 0, 0), (1.0, -1, 4, 0)]),
                 cape=dict(rot=[(0, 9, 0, 0), (0.5, 13, 0, 0), (1.0, 9, 0, 0)]),
                 coat_tails=dict(rot=[(0, 8, 0, 0), (0.5, 12, 0, 0), (1.0, 8, 0, 0)]),
                 veil_back=dict(rot=[(0, 6, 0, 0), (0.5, 9, 0, 0), (1.0, 6, 0, 0)]),
                 hair_back=dict(rot=[(0, 4, 0, 0), (0.5, 7, 0, 0), (1.0, 4, 0, 0)]),
                 skirt_long=dict(rot=sway), robe_long=dict(rot=sway), skirt_short=dict(rot=sway),
                 ponytail_tail=dict(rot=[(0, 6, 0, 8), (0.5, 10, 0, -8), (1.0, 6, 0, 8)]),
                 braid_r=dict(rot=[(0, -3, 0, 3), (0.5, -5, 0, -3), (1.0, -3, 0, 3)]),
                 braid_l=dict(rot=[(0, -3, 0, 3), (0.5, -5, 0, -3), (1.0, -3, 0, 3)]),
                 scarf_tail=dict(rot=[(0, -10, 0, 6), (0.5, -14, 0, -6), (1.0, -10, 0, 6)]),
                 lantern=dict(rot=[(0, 0, 0, 8), (0.5, 0, 0, -8), (1.0, 0, 0, 8)]))]


def work_anims():
    A = []
    # default: glances around now and then
    A.append(anim('work', 8.0, loop=True,
                  head=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.8, -2, 28, 0), (3.2, -2, 28, 0), (3.9, 0, 0, 0),
                                 (5.4, 0, 0, 0), (6.0, 4, -24, 2), (7.2, 4, -24, 2), (8.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.8, 0, 6, 0), (3.2, 0, 6, 0), (3.9, 0, 0, 0), (6.0, 0, -5, 0),
                                  (7.2, 0, -5, 0), (8.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (4.2, 0, 0, 0), (4.6, -20, 0, 0), (5.2, -20, 0, 0), (5.6, 0, 0, 0),
                                          (8.0, 0, 0, 0)])))
    # watch: head-only scan (hands stay on the weapon)
    A.append(anim('work_watch', 8.0, loop=True,
                  head=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (2.6, 2, 32, 0), (4.0, 2, 32, 0), (5.0, 0, -26, 0),
                                 (6.6, 0, -26, 0), (8.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (2.6, 0, 5, 0), (4.0, 0, 5, 0), (5.0, 0, -4, 0), (6.6, 0, -4, 0),
                                  (8.0, 0, 0, 0)])))
    # guard: scan and shift the polearm
    A.append(anim('work_guard', 8.0, loop=True,
                  head=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (2.6, 2, 30, 0), (4.0, 2, 30, 0), (5.0, 0, -26, 0),
                                 (6.6, 0, -26, 0), (8.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -8, 0, 4), (1.0, -3, 0, 2), (6.8, -3, 0, 2), (7.3, -9, 0, 5),
                                      (8.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -6, 0, 0), (1.0, -2, 0, 0), (7.3, -6, 0, 0), (8.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (2.6, 0, 0, -2), (5.0, 0, 0, 2), (8.0, 0, 0, 0)])))
    # blacksmith: three strikes and a look at the work
    strikes = []
    arm = [(0, 0, 0, 0)]
    fore = [(0, 0, 0, 0)]
    chest = [(0, 0, 0, 0)]
    for k in range(3):
        t0 = 0.15 + k * 0.75
        arm += [(t0 + 0.3, -95, 0, 6), (t0 + 0.5, 12, 0, 0), (t0 + 0.62, 4, 0, 0)]
        fore += [(t0 + 0.3, -30, 0, 0), (t0 + 0.5, 6, 0, 0), (t0 + 0.62, 0, 0, 0)]
        chest += [(t0 + 0.3, -6, -4, 0), (t0 + 0.5, 7, 3, 0), (t0 + 0.62, 4, 0, 0)]
        strikes.append(t0 + 0.5)
    arm += [(3.0, 0, 0, 0), (4.0, 0, 0, 0)]
    fore += [(3.0, -10, 0, 0), (3.6, -14, 0, 0), (4.0, 0, 0, 0)]
    chest += [(3.0, 6, 0, 0), (4.0, 0, 0, 0)]
    A.append(anim('work_hammer', 4.0, loop=True,
                  right_arm=dict(rot=arm), right_forearm=dict(rot=fore), chest=dict(rot=chest),
                  head=dict(rot=[(0, 14, 0, 0), (2.4, 14, 0, 0), (3.0, 20, -10, 6), (3.6, 20, -10, 6), (4.0, 14, 0, 0)]),
                  left_arm=dict(rot=[(0, -10, 0, -6), (4.0, -10, 0, -6)])))
    # the old dragonsmith lifts the giant hammer she leans on and lets it fall
    A.append(anim('work_bighammer', 5.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.4, 0, 0, 0), (2.0, -60, 0, 8), (2.35, -62, 0, 8), (2.55, 6, 0, 0),
                                      (2.75, 0, 0, 0), (5.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.4, 0, 0, 0), (2.0, -50, 0, 0), (2.35, -55, 0, 0), (2.55, 0, 0, 0),
                                          (5.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.4, 0, 0, 0), (2.0, -10, -6, 0), (2.4, -12, -6, 0), (2.6, 6, 2, 0),
                                  (3.2, 0, 0, 0), (5.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (2.0, -8, 0, 0), (2.6, 8, 0, 0), (3.4, 4, 12, 4), (4.4, 4, 12, 4), (5.0, 0, 0, 0)])))
    # receptionist: writes in the ledger, looks up, writes again
    wr = [(0, 0, 0, 0)]
    for k in range(10):
        t = 0.1 + k * 0.2
        wr += [(round(t, 2), -4, 3 if k % 2 else -3, 2), (round(t + 0.1, 2), 2, -2 if k % 2 else 2, -1)]
    wr += [(2.4, 0, 0, 0), (3.6, 0, 0, 0)]
    A.append(anim('work_write', 3.6, loop=True,
                  right_forearm=dict(rot=wr),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.0, 0, -6, 0), (2.0, 0, 6, 0), (2.4, 0, 0, 0), (3.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 16, 0, 0), (2.3, 16, -4, 0), (2.7, -4, 6, 2), (3.3, -4, 6, 2), (3.6, 16, 0, 0)]),
                  ponytail_tail=dict(rot=[(0, 0, 0, 0), (2.5, 0, 0, 0), (2.8, -8, 0, 8), (3.2, 4, 0, -5), (3.6, 0, 0, 0)])))
    # shopkeeper: takes coins out of the pouch, inspects one, drops it back
    A.append(anim('work_coins', 2.8, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -8, 0, -10), (0.9, -40, 0, -6), (1.6, -42, 0, -6), (2.1, -8, 0, -10),
                                      (2.8, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -10, 0, 0), (0.9, -55, 0, 0), (1.6, -58, 0, 0), (2.1, -10, 0, 0),
                                          (2.8, 0, 0, 0)]),
                  coin=dict(pos=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.15, 0, 3, 0), (1.3, 0, 0, 0), (2.8, 0, 0, 0)],
                            rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.3, 360, 0, 0), (2.8, 360, 0, 0)], rot_interp='linear'),
                  head=dict(rot=[(0, 8, 0, 0), (0.9, -6, 6, 0), (1.6, -6, 6, 0), (2.1, 10, 0, 0), (2.8, 8, 0, 0)])))
    # dishwasher: scrubs the plate in circles, glances around nervously
    circ = [(0, 0, 0, 0)]
    for k in range(8):
        t = 0.15 + k * 0.3
        circ += [(round(t, 2), -6, 10, 0), (round(t + 0.075, 2), 0, 0, 6), (round(t + 0.15, 2), 6, -10, 0),
                 (round(t + 0.225, 2), 0, 0, -6)]
    circ += [(2.7, 0, 0, 0), (4.0, 0, 0, 0)]
    A.append(anim('work_dish', 4.0, loop=True,
                  right_forearm=dict(rot=circ),
                  head=dict(rot=[(0, 18, 0, 0), (2.6, 18, 0, 0), (2.9, 0, 35, 0), (3.2, 0, -35, 0), (3.6, 10, 0, 0),
                                 (4.0, 18, 0, 0)]),
                  chest=dict(rot=[(0, 4, 0, 0), (2.6, 4, 0, 0), (3.0, 0, 6, 0), (3.3, 0, -6, 0), (4.0, 4, 0, 0)])))
    # scholar: the floating book bobs and a page turns over
    A.append(anim('work_read', 4.0, loop=True,
                  book_float=dict(pos=wave(4.0, 0.6, 4.0, axis=1), rot=wave(4.0, 3.0, 4.0, phase=1.0, axis=2)),
                  book_page=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, 0), (2.5, 0, 0, -180), (3.85, 0, 0, -180),
                                      (4.0, 0, 0, -360)], lin=True),
                  head=dict(rot=[(0, 14, 0, 0), (1.8, 16, -5, 3), (2.4, 14, 4, 0), (4.0, 14, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.7, 0, 0, 0), (2.1, -45, 0, -10), (2.6, -40, 0, -18), (3.0, 0, 0, 0),
                                      (4.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.7, 0, 0, 0), (2.1, -40, 0, 0), (2.6, -35, 0, 0), (3.0, 0, 0, 0),
                                          (4.0, 0, 0, 0)])))
    # courier: the pigeon flutters and pecks, the boy reads a letter
    flap = [(0, 0, 0, 0), (0.9, 0, 0, 0)]
    for k in range(5):
        t = 1.0 + k * 0.16
        flap += [(round(t, 2), 0, 0, 70), (round(t + 0.08, 2), 0, 0, 5)]
    flap += [(1.9, 0, 0, 0), (4.0, 0, 0, 0)]
    A.append(anim('work_pigeon', 4.0, loop=True,
                  pigeon_wing_r=dict(rot=[(k[0], k[1], k[2], -k[3]) for k in flap]),
                  pigeon_wing_l=dict(rot=flap),
                  pigeon=dict(pos=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.2, 0, 1.2, 0), (1.7, 0, 0.6, 0), (1.95, 0, 0, 0),
                                   (4.0, 0, 0, 0)],
                              rot=[(0, 0, 0, 0), (2.6, 0, 0, 0), (2.8, 20, 0, 0), (2.95, 0, 0, 0), (3.1, 20, 0, 0),
                                   (3.25, 0, 0, 0), (4.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.9, -4, 30, 6), (2.0, -4, 30, 6), (2.5, 10, -6, 0), (3.6, 10, -6, 0),
                                 (4.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.5, -45, 0, -8), (3.6, -45, 0, -8), (4.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.5, -45, 0, 0), (3.6, -45, 0, 0), (4.0, 0, 0, 0)])))
    # registrar: three stamps on the papers, then straightens the glasses
    st_arm = [(0, 0, 0, 0)]
    st_fore = [(0, 0, 0, 0)]
    for k in range(3):
        t = 0.2 + k * 0.55
        st_arm += [(round(t + 0.2, 2), -20, 0, 0), (round(t + 0.32, 2), 4, 0, 0), (round(t + 0.45, 2), -4, 0, 0)]
        st_fore += [(round(t + 0.2, 2), -25, 0, 0), (round(t + 0.32, 2), 6, 0, 0), (round(t + 0.45, 2), -2, 0, 0)]
    st_arm += [(2.2, 0, 0, 0), (2.6, -70, 0, -18), (3.1, -72, 0, -18), (3.5, 0, 0, 0), (4.0, 0, 0, 0)]
    st_fore += [(2.2, 0, 0, 0), (2.6, -75, 0, 0), (3.1, -78, 0, 0), (3.5, 0, 0, 0), (4.0, 0, 0, 0)]
    A.append(anim('work_stamp', 4.0, loop=True, right_arm=dict(rot=st_arm), right_forearm=dict(rot=st_fore),
                  head=dict(rot=[(0, 12, 0, 0), (2.2, 12, 0, 0), (2.7, 0, 0, 0), (3.2, 0, 0, 0), (3.6, 12, 0, 0),
                                 (4.0, 12, 0, 0)])))
    # guildmaster: stirs his famous stew and tastes it, very pleased
    stir = [(0, 0, 0, 0)]
    for k in range(5):
        t = 0.1 + k * 0.4
        stir += [(round(t, 2), 0, 12, 4), (round(t + 0.1, 2), 6, 0, 0), (round(t + 0.2, 2), 0, -12, -4),
                 (round(t + 0.3, 2), -6, 0, 0)]
    stir += [(2.2, 0, 0, 0), (2.7, -55, 0, -14), (3.6, -58, 0, -14), (4.1, 0, 0, 0), (5.0, 0, 0, 0)]
    A.append(anim('work_ladle', 5.0, loop=True,
                  right_forearm=dict(rot=stir),
                  right_arm=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.7, -25, 0, -10), (3.6, -25, 0, -10), (4.1, 0, 0, 0),
                                      (5.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 10, 0, 0), (2.2, 10, 0, 0), (2.8, -6, 0, 0), (3.2, -6, 0, 0), (3.5, 4, 0, 0),
                                 (3.8, -4, 0, 0), (4.4, 10, 0, 0), (5.0, 10, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (3.0, -4, 0, 0), (3.6, -2, 0, 0), (5.0, 0, 0, 0)])))
    # chieftain: a long swig from the drinking horn
    A.append(anim('work_drink', 5.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.6, -60, 0, -18), (2.8, -62, 0, -20), (3.4, 0, 0, 0),
                                      (5.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.6, -45, 0, 0), (2.8, -60, 0, 0), (3.4, 0, 0, 0),
                                          (5.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (1.9, -18, 0, 0), (2.8, -26, 0, 0), (3.3, 6, 0, 0),
                                 (3.7, 0, 0, 0), (5.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.9, -6, 0, 0), (2.8, -9, 0, 0), (3.4, 3, 0, 0), (4.0, 0, 0, 0),
                                  (5.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (3.4, 0, 0, 0), (3.8, -20, 0, 10), (4.4, 0, 0, 0), (5.0, 0, 0, 0)])))
    # tower watcher: raises the lantern and looks up at the tower
    A.append(anim('work_lantern', 7.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (2.4, -16, 0, 6), (4.6, -16, 0, 6), (5.4, 0, 0, 0),
                                      (7.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, 0), (2.8, -26, 10, 0), (4.6, -26, 10, 0), (5.4, 0, 0, 0),
                                 (7.0, 0, 0, 0)]),
                  lantern=dict(rot=[(0, 0, 0, 0), (2.4, 0, 0, 10), (3.0, 0, 0, -8), (3.6, 0, 0, 5), (4.2, 0, 0, -2),
                                    (7.0, 0, 0, 0)])))
    # auctioneer: taps the gavel into her palm
    A.append(anim('work_gavel', 3.0, loop=True,
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.3, -35, 0, 0), (0.45, 4, 0, 0), (0.7, -30, 0, 0), (0.85, 4, 0, 0),
                                          (1.2, 0, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.45, 4, 0, -5), (0.85, 4, 0, 5), (1.4, -4, 10, 4), (2.6, -4, 10, 4),
                                 (3.0, 0, 0, 0)])))
    # arena master: raises the speaking trumpet and calls out, other arm up
    A.append(anim('work_trumpet', 6.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (2.1, -75, 0, -12), (3.8, -78, 0, -12), (4.4, 0, 0, 0),
                                      (6.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.5, 0, 0, 0), (2.1, -50, 0, 0), (3.8, -50, 0, 0), (4.4, 0, 0, 0),
                                          (6.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.6, 0, 0, 0), (2.2, -160, 0, 18), (2.6, -150, 0, 26), (3.0, -160, 0, 18),
                                     (3.4, -150, 0, 26), (3.8, -160, 0, 18), (4.4, 0, 0, 0), (6.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (2.1, -8, 0, 0), (3.8, -8, 0, 0), (4.4, 0, 0, 0), (6.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (2.1, -8, 0, 0), (3.8, -8, 0, 0), (4.4, 0, 0, 0), (6.0, 0, 0, 0)])))
    # drill instructor: an overhead practice strike
    A.append(anim('work_kata', 3.6, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, 0, 0, 0), (1.1, -150, 0, -6), (1.35, -35, 0, 0), (1.6, -30, 0, 0),
                                      (2.4, 0, 0, 0), (3.6, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.1, -25, 0, 0), (1.35, -5, 0, 0), (2.4, 0, 0, 0), (3.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.1, -40, 0, -10), (1.35, 10, 0, -12), (2.4, 0, 0, 0), (3.6, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.1, -10, 14, 0), (1.35, 10, -10, 0), (1.6, 8, -8, 0), (2.4, 0, 0, 0),
                                  (3.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.1, -6, -10, 0), (1.35, 6, 8, 0), (2.4, 0, 0, 0), (3.6, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (1.1, 0, 0, 0.5), (1.35, 0, -0.6, -1.2), (2.4, 0, 0, 0), (3.6, 0, 0, 0)])))
    # caravan master: tosses a coin and catches it
    A.append(anim('work_coinflip', 3.0, loop=True,
                  coin=dict(pos=[(0, 0, 0, 0), (0.5, 0, 0, 0), (0.85, 0, 9, 0), (1.2, 0, 0, 0), (3.0, 0, 0, 0)],
                            rot=[(0, 0, 0, 0), (0.5, 0, 0, 0), (1.2, 1080, 0, 0), (3.0, 1080, 0, 0)], rot_interp='linear'),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.4, 0, 0, 0), (0.5, -12, 0, 0), (0.6, 0, 0, 0), (1.15, 0, 0, 0),
                                          (1.25, 6, 0, 0), (1.4, 0, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, 0, 0, 0), (0.85, -22, 0, 0), (1.2, 4, 0, 0), (1.6, 0, 0, 0),
                                 (3.0, 0, 0, 0)])))
    # archivist: turns a page of the heavy tome, then tilts her head (this page again?)
    A.append(anim('work_book', 5.0, loop=True,
                  tome_page=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.5, 0, 0, 180), (4.85, 0, 0, 180), (5.0, 0, 0, 360)],
                                 lin=True),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, -14), (1.0, -32, 0, -16), (1.5, -28, 0, -6), (2.0, 0, 0, 0),
                                      (5.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -40, 0, 0), (1.5, -40, 0, 0), (2.0, 0, 0, 0), (5.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 16, 0, 0), (2.2, 16, 0, 0), (2.7, 10, -6, 12), (3.9, 10, -6, 12), (4.4, 16, 0, 0),
                                 (5.0, 16, 0, 0)])))
    # princess in disguise: pushes her glasses up and looks around - did anyone notice?
    A.append(anim('work_glasses', 6.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.4, -42, 0, 6), (2.0, -42, 0, 6), (2.4, 0, 0, 0),
                                      (6.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.4, -62, 0, 0), (2.0, -64, 0, 0), (2.4, 0, 0, 0),
                                          (6.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.4, -4, 0, 0), (2.0, -4, 0, 0), (3.0, 0, 30, 0), (3.6, 0, 30, 0),
                                 (4.0, 0, -28, 0), (4.6, 0, -28, 0), (5.2, 2, 0, 0), (6.0, 0, 0, 0)])))
    # freed swordsman: rolls the stiff shoulder and studies the broken shackle
    A.append(anim('work_shoulder', 7.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.8, -20, 0, 18), (1.4, 10, 0, 10), (2.0, -20, 0, 18), (2.6, 0, 0, 0),
                                      (3.6, 0, 0, 0), (4.2, -35, -10, -6), (5.6, -35, -10, -6), (6.2, 0, 0, 0), (7.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (3.6, 0, 0, 0), (4.2, -70, 0, 0), (5.6, -70, 0, 0), (6.2, 0, 0, 0),
                                          (7.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -55, 0, 30), (2.2, -55, 0, 30), (2.7, 0, 0, 0), (7.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -50, 0, 0), (2.2, -50, 0, 0), (2.7, 0, 0, 0), (7.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, 4, -12, 0), (2.2, 4, -12, 0), (2.8, 0, 0, 0), (4.2, 18, -14, 0),
                                 (5.6, 18, -14, 0), (6.2, 0, 0, 0), (7.0, 0, 0, 0)])))
    # forest sage: the bud on her staff swells; she lifts the staff a little and hums
    A.append(anim('work_staff', 6.0, loop=True,
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.2, -10, 0, 3), (3.4, -10, 0, 3), (4.4, 0, 0, 0), (6.0, 0, 0, 0)]),
                  staff_bud=dict(scale=[(0, 1, 1, 1), (1.6, 1.35, 1.35, 1.35), (2.4, 1.2, 1.2, 1.2), (3.2, 1.35, 1.35, 1.35),
                                        (4.6, 1, 1, 1), (6.0, 1, 1, 1)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.4, -50, -10, 10), (3.2, -50, 10, 16), (4.2, 0, 0, 0), (6.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (1.4, -30, 0, 0), (3.2, -26, 0, 0), (4.2, 0, 0, 0), (6.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.2, -12, -14, 0), (3.4, -12, -14, 0), (4.4, 0, 0, 0), (6.0, 0, 0, 0)])))
    # oracle: the armillary sphere turns; her head tilts in small mechanical steps
    A.append(anim('work_orb', 4.0, loop=True,
                  ring_a=dict(rot=spin(4.0, 1, 1.0), lin=True),
                  ring_b=dict(rot=spin(4.0, 0, -1.0), lin=True),
                  armillary=dict(pos=wave(4.0, 0.4, 2.0, axis=1)),
                  head=dict(rot=[(0, 0, 0, 0), (0.9, 0, 0, 0), (1.0, 0, 0, 8), (1.9, 0, 0, 8), (2.0, 0, 0, 14),
                                 (2.9, 0, 0, 14), (3.0, 0, 0, 0), (4.0, 0, 0, 0)], lin=True)))
    # shopkeeper: shakes a potion, holds it up to the light, presents it
    A.append(anim('work_potion', 4.0, loop=True,
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.3, 0, 0, 12), (0.45, 0, 0, -12), (0.6, 0, 0, 12), (0.75, 0, 0, -12),
                                          (0.9, 0, 0, 0), (1.6, -18, 0, 0), (2.6, -20, 0, 0), (3.2, 0, 0, 0), (4.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.0, 0, 0, 0), (1.6, -26, 0, 4), (2.6, -28, 0, 4), (3.2, 0, 0, 0),
                                      (4.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 6, 0, 0), (0.9, 8, -6, 0), (1.6, -10, -8, 4), (2.6, -10, -8, 4), (3.2, 4, 6, 0),
                                 (4.0, 6, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.6, -3, -4, 0), (2.6, -3, -4, 0), (3.2, 2, 4, 0), (4.0, 0, 0, 0)])))
    # prayer: hands folded, the head bows, rises, bows again
    A.append(anim('work_pray', 6.0, loop=True,
                  head=dict(rot=[(0, 16, 0, 0), (2.4, 18, 0, 0), (3.2, -6, 0, 0), (4.4, -8, 0, 0), (5.2, 16, 0, 0),
                                 (6.0, 16, 0, 0)]),
                  chest=dict(rot=[(0, 5, 0, 0), (2.4, 6, 0, 0), (3.2, 0, 0, 0), (4.4, 0, 0, 0), (5.2, 5, 0, 0), (6.0, 5, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (3.0, -4, 0, 0), (6.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (3.0, -4, 0, 0), (6.0, 0, 0, 0)])))
    # maid: sweeps with the broom, looks at the floor
    sw_arm = [(0, 0, 0, 0)]
    sw_fore = [(0, 0, 0, 0)]
    for k in range(4):
        t = 0.1 + k * 0.5
        sw_arm += [(round(t + 0.25, 2), -14, 0, 14), (round(t + 0.5, 2), -6, 0, -8)]
        sw_fore += [(round(t + 0.25, 2), -10, 0, 0), (round(t + 0.5, 2), -2, 0, 0)]
    sw_arm += [(2.6, 0, 0, 0), (3.6, 0, 0, 0)]
    sw_fore += [(2.6, 0, 0, 0), (3.6, 0, 0, 0)]
    A.append(anim('work_sweep', 3.6, loop=True, right_arm=dict(rot=sw_arm), right_forearm=dict(rot=sw_fore),
                  left_arm=dict(rot=[(k[0], k[1] * 0.6, 0, -k[3] * 0.4) for k in sw_arm]),
                  chest=dict(rot=[(0, 6, 0, 0), (0.6, 8, 6, 0), (1.1, 8, -6, 0), (1.6, 8, 6, 0), (2.1, 8, -6, 0), (2.6, 4, 0, 0),
                                  (3.6, 6, 0, 0)]),
                  head=dict(rot=[(0, 18, 0, 0), (2.6, 18, 0, 0), (3.0, 0, 20, 0), (3.4, 10, 0, 0), (3.6, 18, 0, 0)])))
    # bard: strums the lute and taps a foot
    st = [(0, 0, 0, 0)]
    for k in range(8):
        t = k * 0.2
        st += [(round(t + 0.08, 2), -14, 0, 0), (round(t + 0.16, 2), 4, 0, 0)]
    st.append((1.6, 0, 0, 0))
    A.append(anim('work_lute', 1.6, loop=True, right_forearm=dict(rot=st),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.4, 0, 0, 6), (0.8, 0, 0, 0), (1.2, 0, 0, 6), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 4, 0, -6), (0.4, -2, 0, -2), (0.8, 4, 0, -6), (1.2, -2, 0, -2), (1.6, 4, 0, -6)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.2, -8, 0, 0), (0.4, 0, 0, 0), (0.6, -8, 0, 0), (0.8, 0, 0, 0),
                                       (1.0, -8, 0, 0), (1.2, 0, 0, 0), (1.4, -8, 0, 0), (1.6, 0, 0, 0)])))
    # noble lady: fans herself, glances about
    fn = [(0, 0, 0, 0)]
    for k in range(6):
        t = 0.2 + k * 0.24
        fn += [(round(t, 2), 0, 0, 18), (round(t + 0.12, 2), 0, 0, -6)]
    fn += [(1.8, 0, 0, 0), (4.0, 0, 0, 0)]
    A.append(anim('work_fan', 4.0, loop=True, left_forearm=dict(rot=fn),
                  head=dict(rot=[(0, 0, 0, 0), (1.9, 0, 0, 0), (2.4, -4, -24, 4), (3.2, -4, -24, 4), (3.7, 0, 0, 0),
                                 (4.0, 0, 0, 0)])))
    # pipe smoker: a long draw on the pipe, the chest rises, a slow look around
    A.append(anim('work_pipe', 7.0, loop=True,
                  head=dict(rot=[(0, 0, 0, 0), (1.0, -8, 0, 0), (2.2, -10, 0, 0), (2.8, 2, 0, 0), (4.0, 0, 24, 0),
                                 (5.2, 0, 24, 0), (6.2, 0, 0, 0), (7.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.0, -3, 0, 0), (2.2, -4, 0, 0), (2.8, 1, 0, 0), (7.0, 0, 0, 0)]),
                  pipe=dict(rot=[(0, 0, 0, 0), (1.0, -6, 0, 0), (2.2, -8, 0, 0), (2.8, 0, 0, 0), (7.0, 0, 0, 0)])))
    return A


def talk_anims():
    A = []
    L = 3.0
    A.append(anim('talk', L,
                  mouth=flaps(0.2, 2.5, L),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, 7, 0, 0), (0.6, -2, 0, 0), (1.4, 3, 0, -4), (2.2, -1, 0, 3), (2.7, 4, 0, 0),
                                 (3.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -32, 0, 10), (1.4, -36, 6, 14), (2.2, -30, 0, 8), (2.7, 0, 0, 0),
                                      (3.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -42, 0, 0), (0.9, -30, 0, 0), (1.4, -48, 0, 0), (2.2, -34, 0, 0),
                                          (2.7, 0, 0, 0), (3.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.5, 3, 4, 0), (1.5, 2, -2, 0), (2.7, 0, 0, 0), (3.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.0, -8, 0, -6), (2.0, -12, 0, -8), (2.7, 0, 0, 0), (3.0, 0, 0, 0)])))
    A.append(anim('talk_bow', 3.4,
                  mouth=flaps(1.0, 3.0, 3.4),
                  chest=dict(rot=[(0, 0, 0, 0), (0.35, 26, 0, 0), (0.8, 26, 0, 0), (1.1, 0, 0, 0), (2.2, 2, -3, 0), (3.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.35, 12, 0, 0), (0.8, 12, 0, 0), (1.1, -2, 0, 0), (1.8, 0, 0, 6), (2.6, 3, 0, -4),
                                 (3.4, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.6, -40, 0, -14), (2.6, -44, 0, -16), (3.1, 0, 0, 0),
                                      (3.4, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.6, -40, 0, 0), (2.6, -46, 0, 0), (3.1, 0, 0, 0),
                                          (3.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.6, -36, 0, 14), (2.6, -40, 0, 16), (3.1, 0, 0, 0),
                                     (3.4, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.6, -36, 0, 0), (2.6, -42, 0, 0), (3.1, 0, 0, 0),
                                         (3.4, 0, 0, 0)])))
    A.append(anim('talk_hearty', 3.2,
                  mouth=flaps(0.15, 2.8, 3.2, period=0.14, rest_every=8),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -16, 0, 0)] + [(round(0.4 + k * 0.12, 2), -16 + (4 if k % 2 else 0), 0, 0)
                                                                   for k in range(8)] + [(1.6, 2, 0, 0), (2.4, 4, 0, 0),
                                                                                         (3.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, -6, 0, 0)] + [(round(0.4 + k * 0.12, 2), -6 + (3 if k % 2 else 0), 0, 0)
                                                                   for k in range(8)] + [(1.6, 2, 0, 0), (3.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.4, -20, 0, 40), (1.4, -24, 0, 44), (2.0, -70, 0, 0), (2.6, -64, 0, 0),
                                      (3.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.4, -30, 0, 0), (1.4, -34, 0, 0), (2.0, -10, 0, 0), (2.6, -12, 0, 0),
                                          (3.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.4, -14, 0, -30), (1.4, -14, 0, -34), (2.2, 0, 0, 0), (3.2, 0, 0, 0)])))
    A.append(anim('talk_gruff', 3.0,
                  mouth=flaps(0.4, 2.2, 3.0, period=0.2, rest_every=4, rest=0.45),
                  head=dict(rot=[(0, 0, 0, 0), (0.4, 10, 0, 0), (0.8, 2, 0, 0), (1.6, 3, 0, 5), (2.4, 8, 0, 0), (3.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.4, 3, 0, 0), (2.4, 2, 0, 0), (3.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (1.0, -12, 0, 4), (2.2, -12, 0, 4), (2.8, 0, 0, 0), (3.0, 0, 0, 0)])))
    A.append(anim('talk_flourish', 3.2,
                  mouth=flaps(0.2, 2.9, 3.2, period=0.15, rest_every=7),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -40, 0, 50), (1.0, -95, 0, 30), (1.5, -60, 0, -20), (2.3, -50, 0, -14),
                                      (2.9, 0, 0, 0), (3.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -20, 0, 0), (1.0, -30, 0, 0), (1.5, -40, 0, 0), (2.3, -45, 0, 0),
                                          (2.9, 0, 0, 0), (3.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.6, -30, 0, 10), (2.3, -30, 0, 10), (2.9, 0, 0, 0), (3.2, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -60, 0, 0), (2.3, -60, 0, 0), (2.9, 0, 0, 0), (3.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.6, 10, 8, 0), (1.2, 4, -6, 0), (2.4, 2, 0, 0), (3.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 8, 0, -6), (1.2, -4, 0, 6), (2.4, 2, 0, 0), (3.2, 0, 0, 0)])))
    A.append(anim('talk_point', 3.0,
                  mouth=flaps(0.3, 2.6, 3.0, period=0.16),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.4, -82, 0, -6), (1.4, -84, 0, -6), (1.7, -30, 0, 8), (2.0, -38, 0, 10),
                                      (2.6, 0, 0, 0), (3.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.4, -6, 0, 0), (1.4, -6, 0, 0), (1.7, -70, 0, 0), (2.0, -76, 0, 0),
                                          (2.6, 0, 0, 0), (3.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.4, 4, 8, 0), (1.4, 4, 8, 0), (1.8, 0, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.4, 4, -4, 0), (1.5, 10, 0, 0), (1.8, 0, 0, 0), (2.4, 6, 0, 0), (3.0, 0, 0, 0)])))
    A.append(anim('talk_nervous', 3.2,
                  mouth=flaps(0.2, 2.9, 3.2, period=0.12, rest_every=9),
                  head=dict(rot=[(0, 0, 0, 0), (0.25, 0, 30, 0), (0.5, 0, -30, 0), (0.75, 0, 0, 0), (1.6, 6, 0, -5), (2.2, 0, 0, 5),
                                 (3.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.8, -150, 0, 30), (1.5, -150, 0, 30), (2.0, 0, 0, 0), (3.2, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.8, -60, 0, 0), (1.0, -50, 0, 0), (1.2, -60, 0, 0), (1.5, -55, 0, 0),
                                         (2.0, 0, 0, 0), (3.2, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (2.0, 0, 0, 0), (2.3, -20, 0, 28), (2.8, -20, 0, 28), (3.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (2.3, -4, 0, 0), (2.8, -4, 0, 0), (3.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (2.3, 0, 0.6, 0), (2.8, 0, 0.6, 0), (3.2, 0, 0, 0)])))
    A.append(anim('talk_kid', 3.0,
                  mouth=flaps(0.3, 2.6, 3.0, period=0.14),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -165, 0, 18), (0.5, -160, 0, 34), (0.7, -165, 0, 14), (0.9, -160, 0, 34),
                                      (1.1, -165, 0, 14), (1.5, 0, 0, 0), (3.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.2, 0, 1.2, 0), (0.4, 0, 0, 0), (0.6, 0, 1.2, 0), (0.8, 0, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.4, -6, 0, 6), (1.2, 0, 0, -6), (2.0, 6, 0, 0), (3.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (1.6, 4, 0, 0), (2.4, -2, 0, 0), (3.0, 0, 0, 0)])))
    A.append(anim('talk_scholar', 3.2,
                  mouth=flaps(0.3, 2.8, 3.2, period=0.18),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -62, 0, -10), (1.8, -64, 0, -10), (2.4, -40, 0, 6), (2.9, 0, 0, 0),
                                      (3.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.5, -70, 0, 0), (1.0, -76, 0, 0), (1.4, -70, 0, 0), (1.8, -76, 0, 0),
                                          (2.4, -40, 0, 0), (2.9, 0, 0, 0), (3.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -4, 0, 8), (1.6, -2, 0, 8), (2.2, 5, 0, -4), (3.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.6, 2, -4, 0), (2.4, 2, 2, 0), (3.2, 0, 0, 0)])))
    A.append(anim('talk_mech', 3.4,
                  mouth=flaps(0.4, 3.0, 3.4, period=0.2, rest_every=5),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, 0, 0, 0), (0.35, 18, 0, 0), (0.9, 18, 0, 0), (0.95, 0, 0, 0), (3.4, 0, 0, 0)],
                             lin=True),
                  head=dict(rot=[(0, 0, 0, 0), (0.35, 10, 0, 0), (0.9, 10, 0, 0), (0.95, 0, 0, 0), (1.6, 0, 0, 0), (1.65, 0, 0, -12),
                                 (2.4, 0, 0, -12), (2.45, 0, 0, 0), (3.4, 0, 0, 0)], lin=True)))
    # guards: a salute with the free hand, then a few curt words
    A.append(anim('talk_salute', 3.0,
                  mouth=flaps(1.0, 2.4, 3.0, period=0.19, rest_every=4),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -150, 0, -30), (0.9, -150, 0, -30), (1.3, 0, 0, 0), (3.0, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.3, -60, 0, 0), (0.9, -60, 0, 0), (1.3, 0, 0, 0), (3.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, -3, 0, 0), (0.9, -3, 0, 0), (1.3, 0, 0, 0), (3.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -4, 0, 0), (0.9, -4, 0, 0), (1.3, 6, 0, 0), (2.2, 3, 0, 2), (3.0, 0, 0, 0)])))
    # clergy: raises a hand in blessing
    A.append(anim('talk_bless', 3.4,
                  mouth=flaps(0.4, 2.9, 3.4, period=0.2, rest_every=6),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.6, -70, 0, 8), (2.6, -72, 0, 8), (3.2, 0, 0, 0), (3.4, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.6, -40, 0, 0), (1.4, -44, 0, 6), (2.0, -40, 0, -6), (2.6, -42, 0, 0),
                                          (3.2, 0, 0, 0), (3.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 8, 0, 0), (1.6, 4, 0, 4), (2.6, 6, 0, 0), (3.4, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.6, 3, 0, 0), (2.6, 3, 0, 0), (3.4, 0, 0, 0)])))
    return A


def animations():
    return idle_anims() + walk_anims() + work_anims() + talk_anims()


def rig():
    import npc_cast
    r = Rig('npc', 'Npc', geometry(), animations(), npc_cast.appearances(), SLOTS, head='head')
    r.preview_ppu = 4.0
    return r
