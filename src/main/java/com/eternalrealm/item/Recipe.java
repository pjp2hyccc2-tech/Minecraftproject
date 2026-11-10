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
 * @param design        the named design the recipe forges ({@link Recipes#designRecipes()}; the id is the design's), or
 *                      {@code null} for a plain piece of the output base
 */
public record Recipe(String id, EquipmentBase output, Map<String, Integer> ingredients, long goldCost,
                     int requiredLevel, Design design) {
    public static final int MAX_INGREDIENT_COUNT = 999;
    public static final long MAX_GOLD_COST = 1_000_000_000L;

    /** A recipe of a plain piece. */
    public Recipe(String id, EquipmentBase output, Map<String, Integer> ingredients, long goldCost, int requiredLevel) {
        this(id, output, ingredients, goldCost, requiredLevel, null);
    }

    public Recipe {
        Checks.id("id", id);
        Objects.requireNonNull(output, "output");
        ingredients = Checks.itemCounts("ingredients", ingredients, 1, MAX_INGREDIENT_COUNT);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException(id + ": a recipe needs ingredients");
        }
        Checks.range("goldCost", goldCost, 0L, MAX_GOLD_COST);
        Checks.range("requiredLevel", requiredLevel, 1, 100);
        if (design != null && (!design.id().equals(id) || !design.base().equals(output)
                || requiredLevel < design.level())) {
            throw new IllegalArgumentException(id + ": a design recipe has the design's id, base and level");
        }
    }

    /** Ingredients and gold as one {@link Cost}. */
    public Cost cost() {
        return new Cost(goldCost, ingredients);
    }
}
