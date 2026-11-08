package com.eternalrealm.monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Rolls drop tables. Deterministic for a given random generator, so tests and replays can seed it.
 *
 * <p>Every item entry is rolled independently ({@code chance}), then the equipment roll; luck only shifts the rarity of
 * equipment ({@link RarityWeights}), it does not change the drop chances.</p>
 */
public final class LootRoller {
    private LootRoller() {
    }

    /**
     * @param luckBonus {@code >= 0}; values above {@link RarityWeights#MAX_LUCK_BONUS} are clamped
     * @return the drops in table order (items first, then equipment); empty if nothing dropped
     */
    public static List<LootDrop> roll(DropTable table, double luckBonus, RandomGenerator random) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(random, "random");
        double luck = RarityWeights.clampLuck(luckBonus);
        List<LootDrop> drops = new ArrayList<>();
        for (DropEntry entry : table.entries()) {
            if (random.nextDouble() < entry.chance()) {
                int count = entry.minCount() == entry.maxCount()
                        ? entry.minCount()
                        : entry.minCount() + random.nextInt(entry.maxCount() - entry.minCount() + 1);
                drops.add(new LootDrop.ItemDrop(entry.itemId(), count));
            }
        }
        EquipmentRoll equipment = table.equipment();
        if (equipment != null && random.nextDouble() < equipment.chance()) {
            drops.add(new LootDrop.EquipmentDrop(equipment.tier(), rollRarity(equipment.weights(), luck, random)));
        }
        return List.copyOf(drops);
    }

    /** One weighted rarity draw (also usable by crafting or chests). */
    public static Rarity rollRarity(RarityWeights weights, double luckBonus, RandomGenerator random) {
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(random, "random");
        double[] shifted = weights.shifted(luckBonus);
        double total = 0.0;
        for (double weight : shifted) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        Rarity last = null;
        for (Rarity rarity : Rarity.values()) {
            double weight = shifted[rarity.ordinal()];
            if (weight <= 0.0) {
                continue;
            }
            last = rarity;
            if (roll < weight) {
                return rarity;
            }
            roll -= weight;
        }
        return last; // only reachable through floating point rounding at the very end of the range
    }
}
