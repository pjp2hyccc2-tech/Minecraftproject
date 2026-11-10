package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 生産: validates and performs a craft. The server looks the recipe up by id ({@link Recipes#byId}; never trust a recipe
 * sent by the client), calls {@link #check}, removes exactly {@link Check#ingredientsToRemove()} and
 * {@link Check#goldToRemove()}, then calls {@link #craft} and gives the result (after
 * {@link EquipmentData#ensureUniqueId}) to the player, all in one tick.
 *
 * <p>Crafted pieces roll their rarity with {@link #CRAFT_LUCK_BONUS} on top of the crafter's own luck: crafting is a
 * reliable way to get the wanted piece, with slightly better odds than a drop of the same tier.</p>
 */
public final class Crafting {
    /** Added to the luck bonus of the rarity roll: COMMON 52 %, UNCOMMON 27 %, RARE 13 %, EPIC 6.1 %, LEGENDARY 1.7 %. */
    public static final double CRAFT_LUCK_BONUS = 0.25;
    /** Message after crafting; its argument is the crafted piece's name ({@link EquipmentTooltip#name}). */
    public static final String DONE_KEY = "message." + ModInfo.MOD_ID + ".crafting.done";

    public enum Result {
        OK,
        LEVEL_TOO_LOW,
        MISSING_INGREDIENTS,
        NOT_ENOUGH_GOLD;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".crafting." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param ingredientsToRemove exactly what to take from the inventory; empty unless {@link Result#OK}
     * @param goldToRemove        gold to take; 0 unless {@link Result#OK}
     * @param missing             ingredients the inventory lacks (item id → missing count), for the UI
     */
    public record Check(Result result, Map<String, Integer> ingredientsToRemove, long goldToRemove,
                        Map<String, Integer> missing) {
        public Check {
            Objects.requireNonNull(result, "result");
            ingredientsToRemove = Checks.itemCounts("ingredientsToRemove", ingredientsToRemove, 1, Integer.MAX_VALUE);
            missing = Checks.itemCounts("missing", missing, 1, Integer.MAX_VALUE);
            if (result != Result.OK && (!ingredientsToRemove.isEmpty() || goldToRemove != 0)) {
                throw new IllegalArgumentException("Only an OK check removes anything");
            }
            Checks.range("goldToRemove", goldToRemove, 0L, Recipe.MAX_GOLD_COST);
        }

        public boolean isOk() {
            return result == Result.OK;
        }
    }

    private Crafting() {
    }

    /**
     * Whether {@code recipe} can be crafted now. Checked in the order level, ingredients, gold; the first failure is
     * reported (with the missing ingredients, if any, for the UI).
     *
     * @param inventoryCounts item id → count the player has (ids not in the map count 0)
     */
    public static Check check(Recipe recipe, Map<String, Integer> inventoryCounts, long gold, int level) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(inventoryCounts, "inventoryCounts");
        Cost.checkGold(gold);
        Map<String, Integer> missing = recipe.cost().missing(inventoryCounts);
        if (level < recipe.requiredLevel()) {
            return new Check(Result.LEVEL_TOO_LOW, Map.of(), 0L, missing);
        }
        if (!missing.isEmpty()) {
            return new Check(Result.MISSING_INGREDIENTS, Map.of(), 0L, missing);
        }
        if (gold < recipe.goldCost()) {
            return new Check(Result.NOT_ENOUGH_GOLD, Map.of(), 0L, Map.of());
        }
        return new Check(Result.OK, recipe.ingredients(), recipe.goldCost(), Map.of());
    }

    /** The crafted piece with only the craft bonus as luck. */
    public static EquipmentData craft(Recipe recipe, RandomGenerator random) {
        return craft(recipe, 0.0, random);
    }

    /**
     * The crafted piece: rarity rolled with {@code luckBonus + CRAFT_LUCK_BONUS} ({@link EquipmentGenerator#rollRarity}),
     * then the affixes. Not enhanced, not bound, no unique id yet.
     */
    public static EquipmentData craft(Recipe recipe, double luckBonus, RandomGenerator random) {
        Objects.requireNonNull(recipe, "recipe");
        if (!Double.isFinite(luckBonus) || luckBonus < 0.0) {
            throw new IllegalArgumentException("luckBonus must be a finite value >= 0: " + luckBonus);
        }
        if (recipe.design() != null) {
            return EquipmentGenerator.rollWithLuck(recipe.design(), luckBonus + CRAFT_LUCK_BONUS, random);
        }
        return EquipmentGenerator.rollWithLuck(recipe.output(), luckBonus + CRAFT_LUCK_BONUS, random);
    }
}
