package com.eternalrealm.entity;

import java.util.Locale;

/** The five boss rigs (models). Several bosses share a rig with different parts and textures. */
public enum BossRig {
    /** Large armoured humanoid with crown, cape and greatsword: b1, b8, final. */
    KING,
    /** Tree body, branch arms, root legs: b2. */
    TREANT,
    /** Giant: sandstone (b3) or mechanical with a rotating ring (b7). */
    COLOSSUS,
    /** Tall humanoid: floating gown and crystal crown (b5) or hooded executioner with axe (b4). */
    QUEEN,
    /** Four legs, wings, long neck and tail: b6. */
    DRAGON;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
