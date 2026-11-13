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
 *
 * <p><b>Named designs</b> ({@link #designRecipes()}): every design with the {@link DesignSource#CRAFT} source is forged
 * from the materials of its own field (F1 = first common, F2 = second common, U = uncommon, E = elite, S = the field's
 * boss soul; step = the design's step in its field, 0..2):</p>
 * <pre>
 * weapon      F1 × 6 + F2 × 3 + U × 2 + E × step
 * chest       F2 × 6 + F1 × 3 + U × 2 + E × step      legs  F2 × 5 + F1 × 3 + U × 2 + E × step
 * head, feet  F2 × 4 + F1 × 2 + U × 1 + E × step
 * gold        tier gold × (1.5 + 0.5 × step), × 0.8 for legs, × 0.6 for head and feet
 * boss weapon S × 12 + E × 6 + U × 4, tier gold × 6 (the soul forge)
 * level       the design's level
 * </pre>
 *
 * <p><b>Trinkets</b> ({@link #trinketRecipes()}, the catalogue of {@link Trinkets}):</p>
 * <pre>
 * grades 1-7 (tier t)    accessory W × 3 + A × 2, charm A × 3 + W × 2, enhancement stones × t,
 *                        tier 2+ plus 2 of the previous area's material of the same role;
 *                        gold: the tier's gold × 1.2 (accessory) or × 1.0 (charm)
 * grades 8-13 (k = 1..6) void essence and abyss crystal × (4 + 2k), oblivion shard × k, end soul × k,
 *                        the focus's elite material × 2k, enhancement stones × 5k, reroll crystals × k;
 *                        gold 1500k² + 2500k (charm × 0.8)
 * level                  the trinket's level
 * </pre>
 *
 * <p><b>Named trinkets</b> of the lords and dungeons ({@link #namedTrinketRecipes()}, {@link NamedTrinkets}): from the
 * materials of their field, F1 × 5 + F2 × 3 (a charm the other way round) + U × 2 + E × 2 + the field's boss soul;
 * gold the tier's × 2.5 (charm × 2.0); level the trinket's.</p>
 */
public final class Recipes {
    /** Index = tier number − 1: gold of a weapon or chest piece. */
    private static final long[] GOLD = {40, 100, 200, 350, 550, 800, 1100};

    private static final List<Recipe> CATALOG;
    private static final List<Recipe> DESIGNS;
    private static final List<Recipe> TRINKETS;
    private static final List<Recipe> NAMED_TRINKETS;
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
        List<Recipe> designs = new ArrayList<>();
        for (Design design : Armory.designs()) {
            if (!design.sources().contains(DesignSource.CRAFT)) {
                continue;
            }
            Recipe recipe = recipeFor(design);
            if (byId.put(recipe.id(), recipe) != null) {
                throw new ExceptionInInitializerError("Duplicate recipe id " + recipe.id());
            }
            designs.add(recipe);
        }
        List<Recipe> trinkets = new ArrayList<>();
        for (TrinketDesign design : Trinkets.all()) {
            Recipe recipe = trinketRecipeFor(design);
            if (byId.put(recipe.id(), recipe) != null) {
                throw new ExceptionInInitializerError("Duplicate recipe id " + recipe.id());
            }
            trinkets.add(recipe);
        }
        List<Recipe> named = new ArrayList<>();
        for (TrinketDesign design : NamedTrinkets.all()) {
            Recipe recipe = namedTrinketRecipeFor(design);
            if (byId.put(recipe.id(), recipe) != null) {
                throw new ExceptionInInitializerError("Duplicate recipe id " + recipe.id());
            }
            named.add(recipe);
        }
        CATALOG = Collections.unmodifiableList(catalog);
        DESIGNS = Collections.unmodifiableList(designs);
        TRINKETS = Collections.unmodifiableList(trinkets);
        NAMED_TRINKETS = Collections.unmodifiableList(named);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private Recipes() {
    }

    /** All recipes of plain pieces, tier by tier (weapons in {@code WeaponType} order, then head, chest, legs, feet). */
    public static List<Recipe> catalog() {
        return CATALOG;
    }

    /** The recipes of the named designs, in catalogue order (field weapons, boss weapons, armor pieces). */
    public static List<Recipe> designRecipes() {
        return DESIGNS;
    }

    /** The recipes of the catalogue trinkets ({@link Trinkets#all()} order: grade by grade). */
    public static List<Recipe> trinketRecipes() {
        return TRINKETS;
    }

    /** The trinket recipes of one grade. */
    public static List<Recipe> trinketRecipes(Trinkets.Grade grade) {
        Objects.requireNonNull(grade, "grade");
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : TRINKETS) {
            if (recipe.design().level() == grade.level()) {
                result.add(recipe);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** The recipes of the named trinkets of the lords and dungeons ({@link NamedTrinkets#all()} order). */
    public static List<Recipe> namedTrinketRecipes() {
        return NAMED_TRINKETS;
    }

    /** The named trinket recipes of a field (listed by the smithy with the field's named designs). */
    public static List<Recipe> namedTrinketRecipes(int field) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : NAMED_TRINKETS) {
            if (recipe.design().field() == field) {
                result.add(recipe);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** The design recipes of a field (its weapons, its boss weapons and its armor pieces). */
    public static List<Recipe> forField(int field) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : DESIGNS) {
            if (recipe.design().field() == field) {
                result.add(recipe);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static Optional<Recipe> forDesign(Design design) {
        Objects.requireNonNull(design, "design");
        return byId(design.id()).filter(recipe -> design.equals(recipe.design()));
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

    private static Recipe recipeFor(Design design) {
        int field = design.field();
        List<String> commons = Materials.ofField(field, Materials.Grade.COMMON);
        String first = commons.get(0);
        String second = commons.get(1);
        String uncommon = Materials.ofField(field, Materials.Grade.UNCOMMON).get(0);
        String elite = Materials.ofField(field, Materials.Grade.ELITE).get(0);
        ItemTier tier = design.base().tier();
        Map<String, Integer> ingredients = new TreeMap<>();
        long gold;
        if (design instanceof WeaponDesign weapon && weapon.isBossWeapon()) {
            ingredients.put(Materials.ofField(field, Materials.Grade.BOSS).get(0), 12);
            ingredients.put(elite, 6);
            ingredients.put(uncommon, 4);
            gold = tierGold(tier) * 6;
        } else {
            int step = stepOf(design);
            int a;
            int b;
            int u;
            int percent;
            switch (design.slot()) {
                case WEAPON -> {
                    a = 6;
                    b = 3;
                    u = 2;
                    percent = 100;
                }
                case CHEST -> {
                    a = 3;
                    b = 6;
                    u = 2;
                    percent = 100;
                }
                case LEGS -> {
                    a = 3;
                    b = 5;
                    u = 2;
                    percent = 80;
                }
                default -> {
                    a = 2;
                    b = 4;
                    u = 1;
                    percent = 60;
                }
            }
            ingredients.put(first, a);
            ingredients.merge(second, b, Integer::sum);
            ingredients.merge(uncommon, u, Integer::sum);
            if (step > 0) {
                ingredients.merge(elite, step, Integer::sum);
            }
            gold = Math.round(tierGold(tier) * (1.5 + 0.5 * step) * percent / 100.0);
        }
        return new Recipe(design.id(), design.base(), ingredients, gold, design.level(), design);
    }

    private static Recipe trinketRecipeFor(TrinketDesign design) {
        Trinkets.Grade grade = Trinkets.gradeOf(design).orElseThrow();
        Trinkets.Focus focus = Trinkets.focusOf(design).orElseThrow();
        boolean charm = design.slot() == EquipmentSlot.CHARM;
        Map<String, Integer> ingredients = new TreeMap<>();
        long gold;
        if (grade.ordinal() < ItemTier.COUNT) {
            ItemTier tier = grade.tier();
            ingredients.put(charm ? Materials.armorMaterial(tier) : Materials.weaponMaterial(tier), 3);
            ingredients.merge(charm ? Materials.weaponMaterial(tier) : Materials.armorMaterial(tier), 2, Integer::sum);
            ingredients.put(Materials.ENHANCEMENT_STONE, tier.number());
            Optional<ItemTier> previous = ItemTier.byNumber(tier.number() - 1);
            previous.ifPresent(old -> ingredients.merge(
                    charm ? Materials.armorMaterial(old) : Materials.weaponMaterial(old), 2, Integer::sum));
            gold = Math.round(tierGold(tier) * (charm ? 1.0 : 1.2));
        } else {
            int k = grade.ordinal() - ItemTier.COUNT + 1;
            ingredients.put(Materials.VOID_ESSENCE, 4 + 2 * k);
            ingredients.put(Materials.ABYSS_CRYSTAL, 4 + 2 * k);
            ingredients.put(Materials.ofField(7, Materials.Grade.ELITE).get(0), k);
            ingredients.put(Materials.ofField(7, Materials.Grade.BOSS).get(0), k);
            ingredients.merge(focus.eliteMaterial(), 2 * k, Integer::sum);
            ingredients.put(Materials.ENHANCEMENT_STONE, 5 * k);
            ingredients.put(Materials.REROLL_CRYSTAL, k);
            long base = 1500L * k * k + 2500L * k;
            gold = charm ? Math.round(base * 0.8) : base;
        }
        return new Recipe(design.id(), design.base(), ingredients, gold, design.level(), design);
    }

    /**
     * A named trinket from the materials of its field (F1, F2 the commons, U the uncommon, E the elite, S the boss's
     * soul): accessory F1 × 5 + F2 × 3, charm F2 × 5 + F1 × 3, both + U × 2 + E × 2 + S × 1; gold the tier's × 2.5
     * (charm × 2.0).
     */
    private static Recipe namedTrinketRecipeFor(TrinketDesign design) {
        int field = design.field();
        List<String> commons = Materials.ofField(field, Materials.Grade.COMMON);
        boolean charm = design.slot() == EquipmentSlot.CHARM;
        Map<String, Integer> ingredients = new TreeMap<>();
        ingredients.put(commons.get(charm ? 1 : 0), 5);
        ingredients.merge(commons.get(charm ? 0 : 1), 3, Integer::sum);
        ingredients.merge(Materials.ofField(field, Materials.Grade.UNCOMMON).get(0), 2, Integer::sum);
        ingredients.merge(Materials.ofField(field, Materials.Grade.ELITE).get(0), 2, Integer::sum);
        ingredients.merge(Materials.ofField(field, Materials.Grade.BOSS).get(0), 1, Integer::sum);
        long gold = Math.round(tierGold(design.base().tier()) * (charm ? 2.0 : 2.5));
        return new Recipe(design.id(), design.base(), ingredients, gold, design.level(), design);
    }

    /** The design's step in its field (0..2), from its level. */
    private static int stepOf(Design design) {
        for (int step = 2; step >= 0; step--) {
            if (Armory.levelOf(design.field(), step) <= design.level()) {
                return step;
            }
        }
        return 0;
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
