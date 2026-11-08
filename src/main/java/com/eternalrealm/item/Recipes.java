package com.eternalrealm.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The crafting catalog: one recipe for every weapon type and armor slot of every tier (7 × 10 = 70).
 *
 * <pre>
 * ingredients (area materials of the tier: A = armor material, W = weapon material, see {@link Materials})
 *   weapon      W × 4 + A × 2        chest  A × 5 + W × 2        legs  A × 4 + W × 2
 *   head        A × 3 + W × 1        feet   A × 3 + W × 1
 *   tier 2+     plus 2 of the previous area's material of the same role (old areas stay worth farming)
 * gold          weapon and chest 100 %, legs 80 %, head and feet 60 % of the tier's gold (40, 100, 200, 350, 550, 800, 1100)
 * level         the tier's required level
 * </pre>
 *
 * The gold alone is worth more than the expected NPC sell value of the crafted piece, even at the maximum luck, so
 * crafting to sell can never create gold.
 */
public final class Recipes {
    /** Index = tier number − 1: gold of a weapon or chest piece. */
    private static final long[] GOLD = {40, 100, 200, 350, 550, 800, 1100};

    private static final List<Recipe> CATALOG;
    private static final Map<String, Recipe> BY_ID;

    static {
        List<Recipe> catalog = new ArrayList<>();
        Map<String, Recipe> byId = new LinkedHashMap<>();
        for (EquipmentBase base : EquipmentBase.all()) {
            Recipe recipe = recipeFor(base);
            if (byId.put(recipe.id(), recipe) != null) {
                throw new ExceptionInInitializerError("Duplicate recipe id " + recipe.id());
            }
            catalog.add(recipe);
        }
        CATALOG = Collections.unmodifiableList(catalog);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private Recipes() {
    }

    /** All recipes, tier by tier (weapons in {@code WeaponType} order, then head, chest, legs, feet). */
    public static List<Recipe> catalog() {
        return CATALOG;
    }

    public static Optional<Recipe> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<Recipe> forOutput(EquipmentBase output) {
        Objects.requireNonNull(output, "output");
        return byId(output.itemPath());
    }

    public static List<Recipe> forTier(ItemTier tier) {
        Objects.requireNonNull(tier, "tier");
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : CATALOG) {
            if (recipe.output().tier() == tier) {
                result.add(recipe);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Gold of a weapon or chest recipe of {@code tier}; other armor pays a share of it. */
    public static long tierGold(ItemTier tier) {
        return GOLD[Objects.requireNonNull(tier, "tier").number() - 1];
    }

    private static Recipe recipeFor(EquipmentBase base) {
        ItemTier tier = base.tier();
        Map<String, Integer> ingredients = new TreeMap<>();
        String armor = Materials.armorMaterial(tier);
        String weapon = Materials.weaponMaterial(tier);
        int goldPercent;
        boolean weaponRole = base.isWeapon();
        switch (base.slot()) {
            case WEAPON -> {
                ingredients.put(weapon, 4);
                ingredients.put(armor, 2);
                goldPercent = 100;
            }
            case CHEST -> {
                ingredients.put(armor, 5);
                ingredients.put(weapon, 2);
                goldPercent = 100;
            }
            case LEGS -> {
                ingredients.put(armor, 4);
                ingredients.put(weapon, 2);
                goldPercent = 80;
            }
            case HEAD, FEET -> {
                ingredients.put(armor, 3);
                ingredients.put(weapon, 1);
                goldPercent = 60;
            }
            default -> throw new IllegalStateException("Unknown slot " + base.slot());
        }
        Optional<ItemTier> previous = ItemTier.byNumber(tier.number() - 1);
        if (previous.isPresent()) {
            String old = weaponRole ? Materials.weaponMaterial(previous.get()) : Materials.armorMaterial(previous.get());
            ingredients.merge(old, 2, Integer::sum);
        }
        long gold = tierGold(tier) * goldPercent / 100;
        return new Recipe(base.itemPath(), base, ingredients, gold, tier.requiredLevel());
    }
}
