package com.eternalrealm.combat;

/**
 * How far a player's hit pushes its target. Every action has its own knockback instead of vanilla's (D-032), but magic
 * and bow attacks push nothing (D-RANGEDKB, 2026-11-11: the owner found that enemies could not close in on mages and
 * archers): every hit of a staff or a bow, of the mage's, priest's and ranger's skills, and every projectile, blast
 * and ground area of magic or arrows. Melee keeps its knockback, the spellblade's sword blows too (magic damage at
 * a sword's length).
 */
public final class Knockback {
    private Knockback() {
    }

    /** Damage types of magic (staves, spells, the priest's light) and of bows. */
    public static boolean isMagicOrBow(DamageType type) {
        return type == DamageType.MAGIC || type == DamageType.HOLY || type == DamageType.RANGED;
    }

    /**
     * The knockback a hit really has.
     *
     * @param knockback the action's own value
     * @param type      the hit's damage type (the job skill's, else the weapon's)
     * @param swordBlow a melee blow of a sword skill that deals magic damage (the spellblade's): it keeps its push
     */
    public static double of(double knockback, DamageType type, boolean swordBlow) {
        return isMagicOrBow(type) && !swordBlow ? 0.0 : knockback;
    }
}
