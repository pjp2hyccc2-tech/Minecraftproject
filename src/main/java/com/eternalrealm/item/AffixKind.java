package com.eternalrealm.item;

import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.core.ModInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The bonuses a random affix can grant. {@link #id()} is the stable persistence key: never rename it.
 *
 * <p>Primary stat kinds add to the character's {@code StatBlock}, stat percent kinds ({@link #isStatPercent()}) raise a
 * primary stat by a percentage ({@code StatPercents}); the others map onto {@link CombatModifiers}. Values
 * live on a fixed grid so they survive JSON round trips exactly: flat kinds are whole numbers, percent kinds are
 * fractions in steps of 0.001 (0.1 %). The roll range grows linearly from tier 1 to tier 7 (rounded to the grid), so
 * a better tier always rolls at least as high as the tier below. Which kinds can appear on which piece is decided by
 * {@link AffixPool}.</p>
 */
public enum AffixKind {
    STR("str", StatType.STR, 1, 1, 3, 10, 16, 999),
    VIT("vit", StatType.VIT, 1, 1, 3, 10, 16, 999),
    AGI("agi", StatType.AGI, 1, 1, 3, 10, 16, 999),
    INT("int", StatType.INT, 1, 1, 3, 10, 16, 999),
    DEX("dex", StatType.DEX, 1, 1, 3, 10, 16, 999),
    LUK("luk", StatType.LUK, 1, 1, 3, 10, 16, 999),
    /** Flat defense (before the reduction formula {@code defense / (defense + 100)}). */
    DEFENSE("defense", null, 1, 1, 3, 10, 18, 9_999),
    /** Flat max health. */
    MAX_HEALTH("max_health", null, 1, 1, 2, 6, 12, 9_999),
    /** Critical chance in absolute points (0.05 = +5 %). */
    CRIT_CHANCE("crit_chance", null, 1000, 10, 20, 40, 70, 1_000),
    /** Added to the critical damage multiplier (0.2 = ×1.5 becomes ×1.7). */
    CRIT_DAMAGE("crit_damage", null, 1000, 50, 100, 150, 300, 5_000),
    PHYSICAL_PERCENT("physical_percent", null, 1000, 20, 40, 80, 140, 5_000),
    RANGED_PERCENT("ranged_percent", null, 1000, 20, 40, 80, 140, 5_000),
    MAGIC_PERCENT("magic_percent", null, 1000, 20, 40, 80, 140, 5_000),
    HEALING_PERCENT("healing_percent", null, 1000, 20, 40, 80, 140, 5_000),
    MOVE_SPEED("move_speed", null, 1000, 20, 30, 50, 80, 500),
    /*
     * Percentage bonuses of a primary stat (0.08 = STR +8 %), applied after every flat source (see StatPercents).
     * Unlike the other affixes they also grow with enhancement (EquipmentStats#percentFactor).
     */
    STR_PERCENT("str_percent", null, StatType.STR, 1000, 20, 40, 80, 120, 1_000),
    VIT_PERCENT("vit_percent", null, StatType.VIT, 1000, 20, 40, 80, 120, 1_000),
    AGI_PERCENT("agi_percent", null, StatType.AGI, 1000, 20, 40, 80, 120, 1_000),
    INT_PERCENT("int_percent", null, StatType.INT, 1000, 20, 40, 80, 120, 1_000),
    DEX_PERCENT("dex_percent", null, StatType.DEX, 1000, 20, 40, 80, 120, 1_000),
    LUK_PERCENT("luk_percent", null, StatType.LUK, 1000, 20, 40, 80, 120, 1_000);

    /**
     * Stored values may exceed the current maximum of their tier by this factor. Anything higher is forged or corrupt;
     * the margin lets a later balance pass lower the ranges without invalidating items that already exist.
     */
    public static final double STORED_VALUE_TOLERANCE = 1.5;

    private final String id;
    private final StatType stat;
    private final StatType percentStat;
    private final int scale;
    private final int[] minUnits = new int[ItemTier.COUNT];
    private final int[] maxUnits = new int[ItemTier.COUNT];
    private final int capUnits;

    /**
     * @param scale    grid units per 1.0 (1 = whole numbers, 1000 = steps of 0.001)
     * @param firstMin lowest roll of tier 1, in grid units (likewise for the other bounds)
     * @param capUnits absolute sanity limit of a stored value, in grid units
     */
    AffixKind(String id, StatType stat, int scale, int firstMin, int firstMax, int lastMin, int lastMax, int capUnits) {
        this(id, stat, null, scale, firstMin, firstMax, lastMin, lastMax, capUnits);
    }

    /** @param percentStat the primary stat a percent kind raises ({@code stat} is then {@code null}) */
    AffixKind(String id, StatType stat, StatType percentStat, int scale, int firstMin, int firstMax, int lastMin,
              int lastMax, int capUnits) {
        this.id = id;
        this.stat = stat;
        this.percentStat = percentStat;
        this.scale = scale;
        this.capUnits = capUnits;
        for (int i = 0; i < ItemTier.COUNT; i++) {
            double t = i / (double) (ItemTier.COUNT - 1);
            minUnits[i] = (int) Math.round(firstMin + (lastMin - firstMin) * t);
            maxUnits[i] = (int) Math.round(firstMax + (lastMax - firstMax) * t);
        }
    }

    public String id() {
        return id;
    }

    /** Tooltip line of an affix of this kind; its argument is {@link #format(double)}. */
    public String translationKey() {
        return "affix." + ModInfo.MOD_ID + "." + id;
    }

    /** The primary stat this kind raises, or empty for a secondary (combat modifier) kind. */
    public Optional<StatType> stat() {
        return Optional.ofNullable(stat);
    }

    public boolean isPrimaryStat() {
        return stat != null;
    }

    /** The primary stat a stat percent kind raises by a percentage, or empty. */
    public Optional<StatType> percentStat() {
        return Optional.ofNullable(percentStat);
    }

    /** Whether this kind raises a primary stat by a percentage (STR +8 %) rather than a flat amount. */
    public boolean isStatPercent() {
        return percentStat != null;
    }

    /** The stat percent kind of a primary stat. */
    public static AffixKind percentOf(StatType stat) {
        return switch (Objects.requireNonNull(stat, "stat")) {
            case STR -> STR_PERCENT;
            case VIT -> VIT_PERCENT;
            case AGI -> AGI_PERCENT;
            case INT -> INT_PERCENT;
            case DEX -> DEX_PERCENT;
            case LUK -> LUK_PERCENT;
        };
    }

    /** Whether values are fractions shown as percentages. */
    public boolean isPercent() {
        return scale != 1;
    }

    /** Smallest difference between two values (1 or 0.001). */
    public double step() {
        return 1.0 / scale;
    }

    public double min(ItemTier tier) {
        return minUnits[index(tier)] / (double) scale;
    }

    public double max(ItemTier tier) {
        return maxUnits[index(tier)] / (double) scale;
    }

    /** Absolute sanity limit of a value of this kind on any tier. */
    public double cap() {
        return capUnits / (double) scale;
    }

    /** Largest value accepted for an item of {@code tier} (see {@link #STORED_VALUE_TOLERANCE}). */
    public double maxStored(ItemTier tier) {
        int units = (int) Math.floor(maxUnits[index(tier)] * STORED_VALUE_TOLERANCE + 1.0e-9);
        return Math.min(capUnits, units) / (double) scale;
    }

    /** A uniformly random value of the tier's range; draws exactly one {@code nextInt}. */
    public double roll(ItemTier tier, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        int i = index(tier);
        int units = minUnits[i] + random.nextInt(maxUnits[i] - minUnits[i] + 1);
        return units / (double) scale;
    }

    /** Whether {@code value} lies on this kind's grid (whole numbers or steps of 0.001). */
    public boolean isOnGrid(double value) {
        if (!Double.isFinite(value)) {
            return false;
        }
        double units = value * scale;
        return Math.abs(units - Math.rint(units)) < 1.0e-6;
    }

    /** Snaps a value to the grid (nearest step). */
    public double snap(double value) {
        return Math.rint(value * scale) / scale;
    }

    /**
     * Where {@code value} lies in the tier's roll range: 0 = the minimum, 1 = a perfect roll (clamped). Lets a reroll UI
     * show how good an affix is.
     */
    public double quality(ItemTier tier, double value) {
        double min = min(tier);
        double max = max(tier);
        if (max <= min) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    /** The tooltip argument: {@code "12"} for flat kinds, {@code "7.5"} (percent, without the sign) for percent kinds. */
    public String format(double value) {
        return EquipmentTooltip.number(isPercent() ? value * 100.0 : value);
    }

    /**
     * Adds a secondary kind's value to {@code builder}; primary stat and stat percent kinds leave it unchanged. A
     * switch expression, so a new kind does not compile until it is mapped.
     */
    CombatModifiers.Builder addTo(CombatModifiers.Builder builder, double value) {
        return switch (this) {
            case STR, VIT, AGI, INT, DEX, LUK -> builder;
            case STR_PERCENT, VIT_PERCENT, AGI_PERCENT, INT_PERCENT, DEX_PERCENT, LUK_PERCENT -> builder;
            case DEFENSE -> builder.defense(value);
            case MAX_HEALTH -> builder.healthFlat(value);
            case CRIT_CHANCE -> builder.crit(value);
            case CRIT_DAMAGE -> builder.critDamage(value);
            case PHYSICAL_PERCENT -> builder.physical(value);
            case RANGED_PERCENT -> builder.ranged(value);
            case MAGIC_PERCENT -> builder.magic(value);
            case HEALING_PERCENT -> builder.healing(value);
            case MOVE_SPEED -> builder.moveSpeed(value);
        };
    }

    public static Optional<AffixKind> byId(String id) {
        for (AffixKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private static int index(ItemTier tier) {
        return Objects.requireNonNull(tier, "tier").number() - 1;
    }
}
