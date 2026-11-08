package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Rolls new equipment (drops, rewards, crafting). Pure functions of their arguments and the injected generator, so a
 * seeded generator always yields the same piece; the loot module decides slot, tier and luck.
 *
 * <p>Rarity: each rarity's {@link ItemRarity#dropWeight() drop weight} (60/25/10/4/1) is multiplied by
 * {@code 1 + luck × rank} (rank 0 = COMMON ... 4 = LEGENDARY) and one rarity is drawn by weight. Luck 0 keeps the base
 * odds (EPIC 4 %, LEGENDARY 1 %); luck 1 gives weights 60/50/30/16/5, i.e. EPIC 16/161 ≈ 9.9 % and LEGENDARY
 * 5/161 ≈ 3.1 %. COMMON's weight never changes, so luck only moves pieces upward, and the gain is linear rather than
 * exponential so stacked bonuses stay predictable. Luck is capped at {@link #MAX_LUCK_BONUS}.</p>
 */
public final class EquipmentGenerator {
    /** Luck beyond this is ignored (LEGENDARY then 13/283 ≈ 4.6 %): stacked bonuses cannot flood the economy. */
    public static final double MAX_LUCK_BONUS = 3.0;

    private EquipmentGenerator() {
    }

    /**
     * A new piece: not enhanced, no unique id ({@link EquipmentData#ensureUniqueId}), not bound. Draws
     * {@code 2 × affix count} values from {@code random}.
     *
     * @param weaponType required for {@link EquipmentSlot#WEAPON}, {@code null} for armor
     */
    public static EquipmentData roll(EquipmentSlot slot, WeaponType weaponType, ItemTier tier, ItemRarity rarity,
                                     RandomGenerator random) {
        return roll(new EquipmentBase(slot, weaponType, tier), rarity, random);
    }

    public static EquipmentData roll(EquipmentBase base, ItemRarity rarity, RandomGenerator random) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(random, "random");
        AffixPool pool = AffixPool.of(base.slot(), base.weaponType());
        return EquipmentData.of(base, rarity, pool.roll(base.tier(), rarity.affixCount(), random));
    }

    /** Rolls the rarity first ({@link #rollRarity}), then the piece. */
    public static EquipmentData rollWithLuck(EquipmentBase base, double luckBonus, RandomGenerator random) {
        return roll(base, rollRarity(luckBonus, random), random);
    }

    /**
     * A rarity drawn by the shifted weights (class documentation); draws exactly one {@code nextDouble}.
     *
     * @param luckBonus 0 = base odds; values above {@link #MAX_LUCK_BONUS} count as the maximum
     * @throws IllegalArgumentException if {@code luckBonus} is negative, NaN or infinite
     */
    public static ItemRarity rollRarity(double luckBonus, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        double luck = effectiveLuck(luckBonus);
        double target = random.nextDouble() * totalWeight(luck);
        ItemRarity[] rarities = ItemRarity.values();
        for (ItemRarity rarity : rarities) {
            target -= weight(rarity, luck);
            if (target < 0.0) {
                return rarity;
            }
        }
        return rarities[rarities.length - 1];
    }

    /** Probability of {@code rarity} at {@code luckBonus} (for tooltips, balance tests and the loot module). */
    public static double rarityChance(ItemRarity rarity, double luckBonus) {
        Objects.requireNonNull(rarity, "rarity");
        double luck = effectiveLuck(luckBonus);
        return weight(rarity, luck) / totalWeight(luck);
    }

    private static double effectiveLuck(double luckBonus) {
        if (!Double.isFinite(luckBonus) || luckBonus < 0.0) {
            throw new IllegalArgumentException("luckBonus must be a finite value >= 0: " + luckBonus);
        }
        return Math.min(MAX_LUCK_BONUS, luckBonus);
    }

    private static double weight(ItemRarity rarity, double luck) {
        return rarity.dropWeight() * (1.0 + luck * rarity.rank());
    }

    private static double totalWeight(double luck) {
        double total = 0.0;
        for (ItemRarity rarity : ItemRarity.values()) {
            total += weight(rarity, luck);
        }
        return total;
    }
}
