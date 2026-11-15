package com.eternalrealm.entity;

import java.util.Locale;

/**
 * The boss rigs (models). Every dungeon boss has a rig of its own, made from its concept sheet; the five first rigs
 * carry the field bosses (calamities and wanderers), which differ by parts, proportions and colours.
 */
public enum BossRig {
    /** Large armoured humanoid with crown, cape and greatsword: field bosses (the old look of b1, b8 and final). */
    KING,
    /** Tree body, branch arms, root legs: field bosses. */
    TREANT,
    /** Giant: sandstone or mechanical with a rotating ring: field bosses. */
    COLOSSUS,
    /** Tall humanoid: floating gown and crystal crown, or hooded executioner with axe: field bosses. */
    QUEEN,
    /** Four legs, wings, long neck and tail: field bosses. */
    DRAGON,
    /** b1 the bandit king: silver hair, crown, fur mantle, long coat and cape, two daggers. */
    BANDIT,
    /** b2 the ancient tree's guardian: bark blocks, moss, antlers in flower, glowing knot runes, root fingers. */
    TREEGUARD,
    /** b3 the sand titan: sandstone bricks, stone locks, gold and turquoise, glowing glyphs, a great maul. */
    SANDTITAN,
    /** b4 the executioner: frosted iron, horned barred helm, chains, torn crimson cape, a gigantic ice axe. */
    EXECUTIONER,
    /** b5 the ice queen: floating bell gown in white and navy, crystal crown, long hair, ice mantle, snowflake staff. */
    ICEQUEEN,
    /** b6 the dragon king: basalt scales with lava seams, cream horns and plates, burning wings, a flame-tipped tail. */
    FIREDRAGON,
    /** b7 the machine god of the sky: white stone and gold, a floating halo, blade-feather wings, a cyan core. */
    SKYGOD,
    /** b8 the gatekeeper of the end: obsidian and stone-brick blocks, a crown of gold prongs, an amethyst greatsword. */
    GATEKEEPER,
    /** final, the king of the end: a crown of spires, stepped pauldrons, a cone of robe strips, the orb and the sword. */
    ENDKING;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
