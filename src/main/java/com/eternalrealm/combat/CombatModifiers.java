package com.eternalrealm.combat;

/**
 * Secondary combat bonuses that do not come from the six primary stats: job passives, equipment and similar sources.
 * Values of several sources are combined with {@link #plus(CombatModifiers)} (sums, except the dodge cost factor, which
 * multiplies). {@link CombatStats#derive(com.eternalrealm.character.StatBlock, int, CombatTuning, CombatModifiers)}
 * clamps the combined result, so no combination can produce invalid combat stats.
 *
 * @param physicalBonus    +fraction of physical damage (0.1 = +10 %)
 * @param rangedBonus      +fraction of ranged damage
 * @param magicBonus       +fraction of magic/holy damage
 * @param healingBonus     +fraction of healing done
 * @param maxHealthFlat    added to the max health bonus
 * @param maxHealthPercent +fraction of the total max health (vanilla 20 included)
 * @param defenseFlat      added to the defense value before the damage reduction formula
 * @param critChance       added to the critical chance (absolute, 0.05 = +5 percentage points)
 * @param critDamage       added to the critical damage multiplier
 * @param moveSpeed        +fraction of movement speed
 * @param guardBonus       added to the weapon's guard reduction
 * @param dodgeCostFactor  multiplies the stamina cost of the dodge (1 = unchanged)
 * @param intToPhysical    fraction of INT that also counts as STR for physical damage (hybrid jobs)
 * @param staminaBonus     added to the stamina pool
 * @param attackSpeed      +fraction of attack speed for light and heavy attacks (equipment), works like extra AGI in
 *                         {@link CombatStats#derive}; 0..{@link #MAX_ATTACK_SPEED}
 */
public record CombatModifiers(double physicalBonus, double rangedBonus, double magicBonus, double healingBonus,
                              double maxHealthFlat, double maxHealthPercent, double defenseFlat, double critChance,
                              double critDamage, double moveSpeed, double guardBonus, double dodgeCostFactor,
                              double intToPhysical, double staminaBonus, double attackSpeed) {
    public static final double MAX_ATTACK_SPEED = 0.5;
    public static final CombatModifiers NONE = new CombatModifiers(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, 0, 0, 0);

    /** Without an attack speed bonus (the shape of every source before equipment had one). */
    public CombatModifiers(double physicalBonus, double rangedBonus, double magicBonus, double healingBonus,
                           double maxHealthFlat, double maxHealthPercent, double defenseFlat, double critChance,
                           double critDamage, double moveSpeed, double guardBonus, double dodgeCostFactor,
                           double intToPhysical, double staminaBonus) {
        this(physicalBonus, rangedBonus, magicBonus, healingBonus, maxHealthFlat, maxHealthPercent, defenseFlat,
                critChance, critDamage, moveSpeed, guardBonus, dodgeCostFactor, intToPhysical, staminaBonus, 0.0);
    }

    public CombatModifiers {
        Check.range("physicalBonus", physicalBonus, -0.9, 100.0);
        Check.range("rangedBonus", rangedBonus, -0.9, 100.0);
        Check.range("magicBonus", magicBonus, -0.9, 100.0);
        Check.range("healingBonus", healingBonus, -0.9, 100.0);
        Check.range("maxHealthFlat", maxHealthFlat, -1000.0, 100_000.0);
        Check.range("maxHealthPercent", maxHealthPercent, -0.9, 100.0);
        Check.range("defenseFlat", defenseFlat, -10_000.0, 1_000_000.0);
        Check.range("critChance", critChance, -1.0, 1.0);
        Check.range("critDamage", critDamage, -1.0, 10.0);
        Check.range("moveSpeed", moveSpeed, -0.9, 10.0);
        Check.range("guardBonus", guardBonus, -1.0, 1.0);
        Check.range("dodgeCostFactor", dodgeCostFactor, 0.01, 10.0);
        Check.range("intToPhysical", intToPhysical, 0.0, 10.0);
        Check.range("staminaBonus", staminaBonus, -1000.0, 100_000.0);
        Check.range("attackSpeed", attackSpeed, 0.0, MAX_ATTACK_SPEED);
    }

    /** Combination of two sources; ranges are re-checked, so absurd sums fail loudly instead of wrapping. */
    public CombatModifiers plus(CombatModifiers other) {
        return new CombatModifiers(
                clamp(physicalBonus + other.physicalBonus, -0.9, 100.0),
                clamp(rangedBonus + other.rangedBonus, -0.9, 100.0),
                clamp(magicBonus + other.magicBonus, -0.9, 100.0),
                clamp(healingBonus + other.healingBonus, -0.9, 100.0),
                clamp(maxHealthFlat + other.maxHealthFlat, -1000.0, 100_000.0),
                clamp(maxHealthPercent + other.maxHealthPercent, -0.9, 100.0),
                clamp(defenseFlat + other.defenseFlat, -10_000.0, 1_000_000.0),
                clamp(critChance + other.critChance, -1.0, 1.0),
                clamp(critDamage + other.critDamage, -1.0, 10.0),
                clamp(moveSpeed + other.moveSpeed, -0.9, 10.0),
                clamp(guardBonus + other.guardBonus, -1.0, 1.0),
                clamp(dodgeCostFactor * other.dodgeCostFactor, 0.01, 10.0),
                clamp(intToPhysical + other.intToPhysical, 0.0, 10.0),
                clamp(staminaBonus + other.staminaBonus, -1000.0, 100_000.0),
                clamp(attackSpeed + other.attackSpeed, 0.0, MAX_ATTACK_SPEED));
    }

    /**
     * The bonuses {@code times} over (a passive skill of level {@code times}): every sum is multiplied, the dodge cost
     * factor is raised to the power. Clamped like {@link #plus}.
     */
    public CombatModifiers scaled(double times) {
        Check.range("times", times, 0.0, 100.0);
        return new CombatModifiers(
                clamp(physicalBonus * times, -0.9, 100.0),
                clamp(rangedBonus * times, -0.9, 100.0),
                clamp(magicBonus * times, -0.9, 100.0),
                clamp(healingBonus * times, -0.9, 100.0),
                clamp(maxHealthFlat * times, -1000.0, 100_000.0),
                clamp(maxHealthPercent * times, -0.9, 100.0),
                clamp(defenseFlat * times, -10_000.0, 1_000_000.0),
                clamp(critChance * times, -1.0, 1.0),
                clamp(critDamage * times, -1.0, 10.0),
                clamp(moveSpeed * times, -0.9, 10.0),
                clamp(guardBonus * times, -1.0, 1.0),
                clamp(Math.pow(dodgeCostFactor, times), 0.01, 10.0),
                clamp(intToPhysical * times, 0.0, 10.0),
                clamp(staminaBonus * times, -1000.0, 100_000.0),
                clamp(attackSpeed * times, 0.0, MAX_ATTACK_SPEED));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Builder for readable definitions ({@code CombatModifiers.builder().magic(0.2).build()}). */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double physical;
        private double ranged;
        private double magic;
        private double healing;
        private double healthFlat;
        private double healthPercent;
        private double defense;
        private double crit;
        private double critDamage;
        private double moveSpeed;
        private double guard;
        private double dodgeCost = 1.0;
        private double intToPhysical;
        private double stamina;
        private double attackSpeed;

        private Builder() {
        }

        public Builder physical(double value) {
            physical += value;
            return this;
        }

        public Builder ranged(double value) {
            ranged += value;
            return this;
        }

        public Builder magic(double value) {
            magic += value;
            return this;
        }

        public Builder healing(double value) {
            healing += value;
            return this;
        }

        public Builder healthFlat(double value) {
            healthFlat += value;
            return this;
        }

        public Builder healthPercent(double value) {
            healthPercent += value;
            return this;
        }

        public Builder defense(double value) {
            defense += value;
            return this;
        }

        public Builder crit(double value) {
            crit += value;
            return this;
        }

        public Builder critDamage(double value) {
            critDamage += value;
            return this;
        }

        public Builder moveSpeed(double value) {
            moveSpeed += value;
            return this;
        }

        public Builder guard(double value) {
            guard += value;
            return this;
        }

        public Builder dodgeCost(double factor) {
            dodgeCost *= factor;
            return this;
        }

        public Builder intToPhysical(double value) {
            intToPhysical += value;
            return this;
        }

        public Builder stamina(double value) {
            stamina += value;
            return this;
        }

        public Builder attackSpeed(double value) {
            attackSpeed += value;
            return this;
        }

        public CombatModifiers build() {
            return new CombatModifiers(physical, ranged, magic, healing, healthFlat, healthPercent, defense, crit,
                    critDamage, moveSpeed, guard, dodgeCost, intToPhysical, stamina, attackSpeed);
        }
    }
}
