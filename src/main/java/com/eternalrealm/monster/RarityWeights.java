package com.eternalrealm.monster;

/**
 * Relative weights of the rarities for one equipment roll.
 *
 * <p><b>Luck</b> shifts the weights towards rarer results:
 * {@code weight'(r) = weight(r) × (1 + luck)^tier(r)}, where {@code tier} is 0 for COMMON up to 4 for LEGENDARY and
 * {@code luck} is clamped to {@code [0, MAX_LUCK_BONUS]}. With the base weights 60/25/10/4/1 a legendary drops 1% of the
 * time without luck, about 3.7% with luck 0.5 and about 8.1% with the maximum luck 1.0. The Forge layer derives the luck
 * bonus from LUK (e.g. {@code LUK × 0.002}).</p>
 */
public record RarityWeights(int common, int uncommon, int rare, int epic, int legendary) {
    public static final RarityWeights BASE = new RarityWeights(Rarity.COMMON.baseWeight(), Rarity.UNCOMMON.baseWeight(),
            Rarity.RARE.baseWeight(), Rarity.EPIC.baseWeight(), Rarity.LEGENDARY.baseWeight());
    public static final double MAX_LUCK_BONUS = 1.0;
    private static final int MAX_WEIGHT = 1_000_000;

    public RarityWeights {
        Check.range("common", common, 0, MAX_WEIGHT);
        Check.range("uncommon", uncommon, 0, MAX_WEIGHT);
        Check.range("rare", rare, 0, MAX_WEIGHT);
        Check.range("epic", epic, 0, MAX_WEIGHT);
        Check.range("legendary", legendary, 0, MAX_WEIGHT);
        if ((long) common + uncommon + rare + epic + legendary == 0) {
            throw new IllegalArgumentException("at least one rarity needs a positive weight");
        }
    }

    public int weight(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> common;
            case UNCOMMON -> uncommon;
            case RARE -> rare;
            case EPIC -> epic;
            case LEGENDARY -> legendary;
        };
    }

    /** Weights after the luck shift, indexed by {@link Rarity#ordinal()}. */
    public double[] shifted(double luckBonus) {
        double factor = 1.0 + clampLuck(luckBonus);
        double[] weights = new double[Rarity.values().length];
        for (Rarity rarity : Rarity.values()) {
            weights[rarity.ordinal()] = weight(rarity) * StrictMath.pow(factor, rarity.ordinal());
        }
        return weights;
    }

    /** Probability of {@code rarity} for a roll with {@code luckBonus}. */
    public double chance(Rarity rarity, double luckBonus) {
        double[] weights = shifted(luckBonus);
        double total = 0.0;
        for (double weight : weights) {
            total += weight;
        }
        return weights[rarity.ordinal()] / total;
    }

    /**
     * @throws IllegalArgumentException for NaN or negative luck (a programming error); values above
     *                                  {@link #MAX_LUCK_BONUS} are clamped (stats may legitimately exceed the cap)
     */
    public static double clampLuck(double luckBonus) {
        if (!(luckBonus >= 0.0)) {
            throw new IllegalArgumentException("luckBonus must be >= 0: " + luckBonus);
        }
        return Math.min(MAX_LUCK_BONUS, luckBonus);
    }
}
