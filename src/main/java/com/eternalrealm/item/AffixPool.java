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

/**
 * Which affix kinds can appear on a piece, and how likely each is (relative weights).
 *
 * <p>Weapons favour damage: their damage type's attack stat and percent bonus are the most likely, plus critical
 * bonuses; swords may also roll INT / magic damage for the spellblade. Armor favours defense, max health and VIT; each
 * piece adds a little identity (the head the mind, the legs agility, the feet movement speed). The attack stats STR,
 * INT and DEX have the same total weight over a full armor set, so no build gets more useful armor rolls than
 * another. Accessories roll primary stats and critical bonuses (STR, INT and DEX alike), charms protection and
 * fortune.</p>
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
    private static final AffixPool HEAD = pool(Map.of(
            AffixKind.DEFENSE, 3, AffixKind.MAX_HEALTH, 2, AffixKind.VIT, 2,
            AffixKind.STR, 1, AffixKind.INT, 2, AffixKind.DEX, 1, AffixKind.LUK, 1,
            AffixKind.CRIT_CHANCE, 1, AffixKind.HEALING_PERCENT, 1));
    private static final AffixPool CHEST = pool(Map.of(
            AffixKind.DEFENSE, 3, AffixKind.MAX_HEALTH, 3, AffixKind.VIT, 3,
            AffixKind.STR, 2, AffixKind.INT, 1, AffixKind.HEALING_PERCENT, 1));
    private static final AffixPool LEGS = pool(Map.of(
            AffixKind.DEFENSE, 3, AffixKind.MAX_HEALTH, 3, AffixKind.VIT, 2,
            AffixKind.STR, 1, AffixKind.INT, 1, AffixKind.AGI, 2, AffixKind.DEX, 2));
    private static final AffixPool FEET = pool(Map.of(
            AffixKind.DEFENSE, 2, AffixKind.MAX_HEALTH, 2, AffixKind.VIT, 1,
            AffixKind.AGI, 3, AffixKind.DEX, 1, AffixKind.LUK, 1, AffixKind.MOVE_SPEED, 3));
    /** Accessories: the primary stats (every build's attack stat equally) and the critical bonuses. */
    private static final AffixPool ACCESSORY = pool(Map.of(
            AffixKind.STR, 3, AffixKind.INT, 3, AffixKind.DEX, 3, AffixKind.AGI, 2, AffixKind.VIT, 2,
            AffixKind.LUK, 2, AffixKind.CRIT_CHANCE, 2, AffixKind.CRIT_DAMAGE, 2));
    /** Charms: protection and fortune. */
    private static final AffixPool CHARM = pool(Map.of(
            AffixKind.VIT, 3, AffixKind.MAX_HEALTH, 3, AffixKind.DEFENSE, 2, AffixKind.LUK, 3,
            AffixKind.HEALING_PERCENT, 2, AffixKind.MOVE_SPEED, 1, AffixKind.AGI, 1));

    /** Iteration in enum order keeps rolls deterministic. */
    private final Map<AffixKind, Integer> weights;
    private final int totalWeight;

    private AffixPool(Map<AffixKind, Integer> weights) {
        this.weights = weights;
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        this.totalWeight = total;
    }

    private static AffixPool pool(Map<AffixKind, Integer> weights) {
        Map<AffixKind, Integer> sorted = new EnumMap<>(AffixKind.class);
        for (Map.Entry<AffixKind, Integer> entry : weights.entrySet()) {
            sorted.put(entry.getKey(), Checks.range("weight of " + entry.getKey(), entry.getValue(), 1, 100));
        }
        if (sorted.size() < ItemRarity.MAX_AFFIXES) {
            throw new IllegalArgumentException("A pool needs at least " + ItemRarity.MAX_AFFIXES + " kinds: " + sorted);
        }
        return new AffixPool(Collections.unmodifiableMap(sorted));
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

    public boolean allows(AffixKind kind) {
        return weights.containsKey(kind);
    }

    /** Relative weight of {@code kind}; 0 if it cannot appear. */
    public int weight(AffixKind kind) {
        return weights.getOrDefault(kind, 0);
    }

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
     * allows (a named design's affix tendencies; other kinds are ignored).
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
        return "AffixPool" + weights;
    }
}
