package com.eternalrealm.item;

import java.util.Map;
import java.util.Objects;

/**
 * A crafting recipe (生産): consumes ingredients and gold and produces a piece of {@link #output()} whose rarity and
 * affixes are rolled when crafted ({@link Crafting#craft}).
 *
 * @param id            stable id ({@code [a-z0-9_]}, equal to the output's item path)
 * @param ingredients   namespaced item id → count, sorted by id
 * @param goldCost      gold consumed by one craft
 * @param requiredLevel character level needed to craft
 */
public record Recipe(String id, EquipmentBase output, Map<String, Integer> ingredients, long goldCost,
                     int requiredLevel) {
    public static final int MAX_INGREDIENT_COUNT = 999;
    public static final long MAX_GOLD_COST = 1_000_000_000L;

    public Recipe {
        Checks.id("id", id);
        Objects.requireNonNull(output, "output");
        ingredients = Checks.itemCounts("ingredients", ingredients, 1, MAX_INGREDIENT_COUNT);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException(id + ": a recipe needs ingredients");
        }
        Checks.range("goldCost", goldCost, 0L, MAX_GOLD_COST);
        Checks.range("requiredLevel", requiredLevel, 1, 100);
    }

    /** Ingredients and gold as one {@link Cost}. */
    public Cost cost() {
        return new Cost(goldCost, ingredients);
    }
}
