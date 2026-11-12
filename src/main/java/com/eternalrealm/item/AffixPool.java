package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

import static java.util.Map.entry;

/**
 * Which affix kinds can appear on a piece, and how likely each is (relative weights).
 *
 * <p>Weapons favour damage: their damage type's attack stat and percent bonus are the most likely, plus critical
 * bonuses; swords may also roll INT / magic damage for the spellblade. Armor favours defense, max health and VIT; each
 * piece adds a little identity (the head the mind, the legs agility, the feet movement speed). The attack stats STR,
 * INT and DEX have the same total weight over a full armor set, so no build gets more useful armor rolls than
 * another. Armor pieces also roll the primary stats as percentages ({@link AffixKind#isStatPercent()}). Accessories roll
 * percent primary stats and critical bonuses (STR, INT and DEX alike), charms protection and fortune.</p>
 */
public final class AffixPool {
    private static final AffixPool MELEE = pool(Map.of(
            AffixKind.STR, 3, AffixKind.AGI, 2, AffixKind.LUK, 2,
            AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2, AffixKind.PHYSICAL_PERCENT, 3));
    private static final AffixPool SWORD = pool(Map.of(
            AffixKind.STR, 3, AffixKind.AGI, 2, AffixKind.LUK, 2, AffixKind.INT, 1,
            AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2, AffixKind.PHYSICAL_PERCENT, 3,
            AffixKind.MAGIC_PERCENT, 1));
    private static final AffixPool STAFF = pool(Map.of(
            AffixKind.INT, 3, AffixKind.AGI, 1, AffixKind.VIT, 1, AffixKind.LUK, 1,
            AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2, AffixKind.MAGIC_PERCENT, 3,
            AffixKind.HEALING_PERCENT, 2));
    private static final AffixPool BOW = pool(Map.of(
            AffixKind.DEX, 3, AffixKind.AGI, 2, AffixKind.LUK, 2,
            AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2, AffixKind.RANGED_PERCENT, 3));
    /*
     * Armor also rolls the stat percent kinds (STR +8 %), weight 1 each: every piece STR %, INT % and DEX % alike (so
     * no build is favoured), plus the piece's own identity (the head VIT and LUK, the chest VIT, the legs VIT and AGI,
     * the feet AGI).
     */
    private static final AffixPool HEAD = pool(Map.ofEntries(
            entry(AffixKind.DEFENSE, 3), entry(AffixKind.MAX_HEALTH, 2), entry(AffixKind.VIT, 2),
            entry(AffixKind.STR, 1), entry(AffixKind.INT, 2), entry(AffixKind.DEX, 1), entry(AffixKind.LUK, 1),
            entry(AffixKind.CRIT_CHANCE, 1), entry(AffixKind.HEALING_PERCENT, 1),
            entry(AffixKind.STR_PERCENT, 1), entry(AffixKind.INT_PERCENT, 1), entry(AffixKind.DEX_PERCENT, 1),
            entry(AffixKind.VIT_PERCENT, 1), entry(AffixKind.LUK_PERCENT, 1)), Set.of());
    private static final AffixPool CHEST = pool(Map.ofEntries(
            entry(AffixKind.DEFENSE, 3), entry(AffixKind.MAX_HEALTH, 3), entry(AffixKind.VIT, 3),
            entry(AffixKind.STR, 2), entry(AffixKind.INT, 1), entry(AffixKind.HEALING_PERCENT, 1),
            entry(AffixKind.STR_PERCENT, 1), entry(AffixKind.INT_PERCENT, 1), entry(AffixKind.DEX_PERCENT, 1),
            entry(AffixKind.VIT_PERCENT, 1)), Set.of());
    private static final AffixPool LEGS = pool(Map.ofEntries(
            entry(AffixKind.DEFENSE, 3), entry(AffixKind.MAX_HEALTH, 3), entry(AffixKind.VIT, 2),
            entry(AffixKind.STR, 1), entry(AffixKind.INT, 1), entry(AffixKind.AGI, 2), entry(AffixKind.DEX, 2),
            entry(AffixKind.STR_PERCENT, 1), entry(AffixKind.INT_PERCENT, 1), entry(AffixKind.DEX_PERCENT, 1),
            entry(AffixKind.VIT_PERCENT, 1), entry(AffixKind.AGI_PERCENT, 1)), Set.of());
    private static final AffixPool FEET = pool(Map.ofEntries(
            entry(AffixKind.DEFENSE, 2), entry(AffixKind.MAX_HEALTH, 2), entry(AffixKind.VIT, 1),
            entry(AffixKind.AGI, 3), entry(AffixKind.DEX, 1), entry(AffixKind.LUK, 1), entry(AffixKind.MOVE_SPEED, 3),
            entry(AffixKind.STR_PERCENT, 1), entry(AffixKind.INT_PERCENT, 1), entry(AffixKind.DEX_PERCENT, 1),
            entry(AffixKind.AGI_PERCENT, 1)), Set.of());
    /**
     * Accessories: the primary stats as percentages (every build's attack stat equally) and the critical bonuses. The
     * flat primary stats of older accessories stay valid but never roll again.
     */
    private static final AffixPool ACCESSORY = pool(Map.of(
            AffixKind.STR_PERCENT, 3, AffixKind.INT_PERCENT, 3, AffixKind.DEX_PERCENT, 3, AffixKind.AGI_PERCENT, 2,
            AffixKind.VIT_PERCENT, 2, AffixKind.LUK_PERCENT, 2, AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2),
            EnumSet.of(AffixKind.STR, AffixKind.INT, AffixKind.DEX, AffixKind.AGI, AffixKind.VIT, AffixKind.LUK));
    /** Charms: protection and fortune, and a little of every attack stat as a percentage. */
    private static final AffixPool CHARM = pool(Map.of(
            AffixKind.VIT_PERCENT, 3, AffixKind.LUK_PERCENT, 3, AffixKind.MAX_HEALTH, 3, AffixKind.DEFENSE, 2,
            AffixKind.HEALING_PERCENT, 2, AffixKind.MOVE_SPEED, 1, AffixKind.AGI_PERCENT, 1,
            AffixKind.STR_PERCENT, 1, AffixKind.INT_PERCENT, 1, AffixKind.DEX_PERCENT, 1),
            EnumSet.of(AffixKind.VIT, AffixKind.LUK, AffixKind.AGI));

    /** The kinds that roll; iteration in enum order keeps rolls deterministic. */
    private final Map<AffixKind, Integer> weights;
    /** Kinds that no longer roll here but stay valid on pieces made before (never removed: see {@link AffixKind}). */
    private final Set<AffixKind> legacy;
    private final int totalWeight;

    private AffixPool(Map<AffixKind, Integer> weights, Set<AffixKind> legacy) {
        this.weights = weights;
        this.legacy = legacy;
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        this.totalWeight = total;
    }

    private static AffixPool pool(Map<AffixKind, Integer> weights) {
        return pool(weights, Set.of());
    }

    private static AffixPool pool(Map<AffixKind, Integer> weights, Set<AffixKind> legacy) {
        Map<AffixKind, Integer> sorted = new EnumMap<>(AffixKind.class);
        for (Map.Entry<AffixKind, Integer> entry : weights.entrySet()) {
            sorted.put(entry.getKey(), Checks.range("weight of " + entry.getKey(), entry.getValue(), 1, 100));
        }
        if (sorted.size() < ItemRarity.MAX_AFFIXES) {
            throw new IllegalArgumentException("A pool needs at least " + ItemRarity.MAX_AFFIXES + " kinds: " + sorted);
        }
        for (AffixKind kind : legacy) {
            if (sorted.containsKey(kind)) {
                throw new IllegalArgumentException(kind + " cannot both roll and be legacy");
            }
        }
        Set<AffixKind> old = legacy.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(legacy));
        return new AffixPool(Collections.unmodifiableMap(sorted), old);
    }

    /**
     * The pool of a piece.
     *
     * @param weaponType required for {@link EquipmentSlot#WEAPON}, must be {@code null} for armor
     */
    public static AffixPool of(EquipmentSlot slot, WeaponType weaponType) {
        Objects.requireNonNull(slot, "slot");
        if (slot == EquipmentSlot.WEAPON) {
            Objects.requireNonNull(weaponType, "weaponType of a weapon");
            return switch (weaponType) {
                case ONE_HANDED_SWORD, TWO_HANDED_SWORD -> SWORD;
                case DUAL_BLADES, SPEAR -> MELEE;
                case STAFF -> STAFF;
                case BOW -> BOW;
            };
        }
        if (weaponType != null) {
            throw new IllegalArgumentException("Armor has no weapon type: " + slot + " / " + weaponType);
        }
        return switch (slot) {
            case HEAD -> HEAD;
            case CHEST -> CHEST;
            case LEGS -> LEGS;
            case FEET -> FEET;
            case ACCESSORY -> ACCESSORY;
            case CHARM -> CHARM;
            case WEAPON -> throw new IllegalStateException("unreachable");
        };
    }

    /** Whether a piece may carry {@code kind}: it rolls here, or it did before ({@link #isLegacy}). */
    public boolean allows(AffixKind kind) {
        return weights.containsKey(kind) || legacy.contains(kind);
    }

    /** Whether {@code kind} can be rolled (drops, crafting, rerolls). */
    public boolean rolls(AffixKind kind) {
        return weights.containsKey(kind);
    }

    /** Whether {@code kind} is valid on older pieces but never rolls any more. */
    public boolean isLegacy(AffixKind kind) {
        return legacy.contains(kind);
    }

    /** Relative weight of {@code kind}; 0 if it cannot roll. */
    public int weight(AffixKind kind) {
        return weights.getOrDefault(kind, 0);
    }

    /** The kinds that roll. */
    public Set<AffixKind> kinds() {
        return Collections.unmodifiableSet(EnumSet.copyOf(weights.keySet()));
    }

    public int totalWeight() {
        return totalWeight;
    }

    /** Probability that one draw from the full pool is {@code kind}. */
    public double chance(AffixKind kind) {
        return weight(kind) / (double) totalWeight;
    }

    /**
     * {@code count} affixes of distinct kinds, drawn by weight without replacement, each with a uniformly random value
     * of the tier's range. Draws alternate between a kind ({@code nextInt}) and its value ({@code nextInt}), so the
     * result is deterministic for a seeded generator.
     */
    public List<Affix> roll(ItemTier tier, int count, RandomGenerator random) {
        return roll(tier, count, random, Map.of());
    }

    /**
     * Like {@link #roll(ItemTier, int, RandomGenerator)}, with {@code bias} added to the weights of the kinds this pool
     * rolls (a named design's affix tendencies; other kinds, legacy ones included, are ignored).
     */
    public List<Affix> roll(ItemTier tier, int count, RandomGenerator random, Map<AffixKind, Integer> bias) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(bias, "bias");
        Checks.range("count", count, 0, weights.size());
        Map<AffixKind, Integer> remaining = new EnumMap<>(weights);
        for (Map.Entry<AffixKind, Integer> entry : bias.entrySet()) {
            if (remaining.containsKey(entry.getKey()) && entry.getValue() > 0) {
                remaining.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        int remainingWeight = 0;
        for (int weight : remaining.values()) {
            remainingWeight += weight;
        }
        List<Affix> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int target = random.nextInt(remainingWeight);
            AffixKind picked = null;
            for (Map.Entry<AffixKind, Integer> entry : remaining.entrySet()) {
                target -= entry.getValue();
                if (target < 0) {
                    picked = entry.getKey();
                    break;
                }
            }
            if (picked == null) {
                throw new IllegalStateException("weighted draw out of range");
            }
            remainingWeight -= remaining.remove(picked);
            result.add(new Affix(picked, picked.roll(tier, random)));
        }
        return result;
    }

    @Override
    public String toString() {
        return "AffixPool" + weights + (legacy.isEmpty() ? "" : " legacy " + legacy);
    }
}
