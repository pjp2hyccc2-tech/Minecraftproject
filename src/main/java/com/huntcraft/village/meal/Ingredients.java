package com.huntcraft.village.meal;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ingredients of the Mofu Diner (モフ亭) and how they combine into dishes. A meal is two ingredients of different
 * categories: the category pair decides the dish (name + food skill), the ingredients add their stats, premium
 * ingredients (HR 3+) add a bonus skill, and one pair per in-game day is the cheaper, stronger "daily special".
 */
public final class Ingredients {
    /** Food skills (implemented in {@link MealSystem}). */
    public enum Skill {
        /** +1 carve on large monsters (HuntHooks extra carve provider). */
        CARVE,
        /** Survive one lethal hit with 1 HP. */
        GUTS,
        /** Movement speed +8%. */
        SWIFT,
        /** Extra stamina regeneration. */
        STAMINA,
        /** Slow natural health regeneration. */
        REGEN,
        /** Companions deal +25% damage. */
        CHEER,
        /** Attack +5% on top of the meal's attack bonus. */
        POWER;

        public String key() {
            return "meal.huntcraft.skill." + name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable(key());
        }

        public Component description() {
            return Component.translatable(key() + ".desc");
        }
    }

    public enum Category {
        GRAIN, MEAT, FISH, VEGETABLE, MUSHROOM, SWEET, SPICE;

        public Component displayName() {
            return Component.translatable("meal.huntcraft.cat." + name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * @param hp      max health bonus (half hearts)
     * @param stamina max stamina bonus
     * @param atk     attack bonus in percent
     * @param def     armor points
     * @param bonus   bonus skill added when this premium ingredient is used (or null)
     */
    public record Ingredient(String id, Category category, int price, int requiredHR, int hp, int stamina, int atk, int def,
                             @Nullable Skill bonus, int color) {
        public Component displayName() {
            return Component.translatable("meal.huntcraft.ing." + id);
        }

        public Component description() {
            return Component.translatable("meal.huntcraft.ing." + id + ".desc");
        }

        public boolean premium() {
            return bonus != null;
        }
    }

    public static final List<Ingredient> ALL = List.of(
            new Ingredient("kazami_rice", Category.GRAIN, 30, 1, 10, 25, 0, 0, null, 0xF3EEDC),
            new Ingredient("golden_millet", Category.GRAIN, 110, 3, 15, 50, 0, 1, Skill.STAMINA, 0xE8C24A),
            new Ingredient("boar_meat", Category.MEAT, 60, 1, 0, 0, 4, 0, null, 0xC0533E),
            new Ingredient("marbled_meat", Category.MEAT, 220, 4, 5, 0, 8, 0, Skill.POWER, 0xE88A84),
            new Ingredient("river_trout", Category.FISH, 50, 1, 5, 0, 0, 3, null, 0x8FB2C8),
            new Ingredient("king_tuna", Category.FISH, 200, 4, 10, 0, 2, 6, Skill.REGEN, 0x4A6FA0),
            new Ingredient("mountain_greens", Category.VEGETABLE, 40, 1, 15, 0, 0, 1, null, 0x6DB04A),
            new Ingredient("snow_radish", Category.VEGETABLE, 130, 3, 20, 25, 0, 2, Skill.GUTS, 0xF0F4F8),
            new Ingredient("forest_shiitake", Category.MUSHROOM, 50, 1, 5, 25, 0, 1, null, 0x8A5A36),
            new Ingredient("honey_comb", Category.SWEET, 70, 2, 0, 50, 0, 0, null, 0xF2B233),
            new Ingredient("fire_pepper", Category.SPICE, 80, 2, 0, 0, 3, 2, null, 0xE0362A));

    /** Dish type and food skill for every unordered category pair. */
    public record Dish(String id, Skill skill) {
        public Component displayName() {
            return Component.translatable("meal.huntcraft.dish." + id);
        }
    }

    private static final Map<Category, Map<Category, Dish>> DISHES = new EnumMap<>(Category.class);
    /** Dish ids in a fixed order (icon index). */
    public static final List<String> DISH_IDS = new ArrayList<>();

    private static void dish(Category a, Category b, String id, Skill s) {
        Dish d = new Dish(id, s);
        DISHES.computeIfAbsent(a, k -> new EnumMap<>(Category.class)).put(b, d);
        DISHES.computeIfAbsent(b, k -> new EnumMap<>(Category.class)).put(a, d);
        DISH_IDS.add(id);
    }

    static {
        dish(Category.GRAIN, Category.MEAT, "donburi", Skill.POWER);
        dish(Category.GRAIN, Category.FISH, "sushi", Skill.REGEN);
        dish(Category.GRAIN, Category.VEGETABLE, "zosui", Skill.STAMINA);
        dish(Category.GRAIN, Category.MUSHROOM, "takikomi", Skill.CARVE);
        dish(Category.GRAIN, Category.SWEET, "dango", Skill.CHEER);
        dish(Category.GRAIN, Category.SPICE, "yakionigiri", Skill.SWIFT);
        dish(Category.MEAT, Category.FISH, "hamayaki", Skill.CARVE);
        dish(Category.MEAT, Category.VEGETABLE, "nabe", Skill.GUTS);
        dish(Category.MEAT, Category.MUSHROOM, "kushiyaki", Skill.POWER);
        dish(Category.MEAT, Category.SWEET, "kakuni", Skill.GUTS);
        dish(Category.MEAT, Category.SPICE, "gekikara", Skill.POWER);
        dish(Category.FISH, Category.VEGETABLE, "tempura", Skill.STAMINA);
        dish(Category.FISH, Category.MUSHROOM, "dobinmushi", Skill.REGEN);
        dish(Category.FISH, Category.SWEET, "kanroni", Skill.CHEER);
        dish(Category.FISH, Category.SPICE, "nanban", Skill.SWIFT);
        dish(Category.VEGETABLE, Category.MUSHROOM, "shojin", Skill.REGEN);
        dish(Category.VEGETABLE, Category.SWEET, "shiraae", Skill.CHEER);
        dish(Category.VEGETABLE, Category.SPICE, "tsukemono", Skill.SWIFT);
        dish(Category.MUSHROOM, Category.SWEET, "mitarashi", Skill.STAMINA);
        dish(Category.MUSHROOM, Category.SPICE, "itame", Skill.CARVE);
        dish(Category.SWEET, Category.SPICE, "karintou", Skill.GUTS);
    }

    private Ingredients() {
    }

    @Nullable
    public static Ingredient get(String id) {
        for (Ingredient i : ALL) if (i.id().equals(id)) return i;
        return null;
    }

    public static int index(Ingredient i) {
        return ALL.indexOf(i);
    }

    @Nullable
    public static Dish dish(Ingredient a, Ingredient b) {
        if (a.category() == b.category()) return null;
        Map<Category, Dish> m = DISHES.get(a.category());
        return m == null ? null : m.get(b.category());
    }

    /** A cooked meal: two ingredients, their dish and the resulting stats / skills. */
    public record Meal(Ingredient a, Ingredient b, Dish dish, boolean special, int hp, int stamina, int atkPct, int def, List<Skill> skills,
                       int price) {
        public Component displayName() {
            return Component.translatable("meal.huntcraft.dish_name", a.displayName(), b.displayName(), dish.displayName());
        }

        public int requiredHR() {
            return Math.max(a.requiredHR(), b.requiredHR());
        }
    }

    @Nullable
    public static Meal cook(@Nullable Ingredient a, @Nullable Ingredient b, boolean special) {
        if (a == null || b == null) return null;
        Dish d = dish(a, b);
        if (d == null) return null;
        float k = special ? 1.2F : 1.0F;
        int hp = Math.round((a.hp() + b.hp()) * k);
        int st = Math.round((a.stamina() + b.stamina()) * k);
        int atk = Math.round((a.atk() + b.atk()) * k);
        int def = Math.round((a.def() + b.def()) * k);
        Set<Skill> skills = new LinkedHashSet<>();
        skills.add(d.skill());
        if (a.bonus() != null) skills.add(a.bonus());
        if (b.bonus() != null) skills.add(b.bonus());
        int price = a.price() + b.price();
        if (special) price = Math.round(price * 0.7F);
        return new Meal(a, b, d, special, Math.min(40, hp), Math.min(50, st), atk, def, new ArrayList<>(skills), price);
    }

    @Nullable
    public static Meal cook(String a, String b, boolean special) {
        return cook(get(a), get(b), special);
    }

    /** The daily special pair for an in-game day (deterministic, same on client and server). */
    public static Ingredient[] dailySpecial(long day) {
        long h = day * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        List<Ingredient> basic = new ArrayList<>();
        for (Ingredient i : ALL) if (i.requiredHR() <= 2) basic.add(i);
        int n = basic.size();
        int ia = (int) Math.floorMod(h, (long) n);
        for (int k = 1; k < n; k++) {
            int ib = (int) Math.floorMod((h >>> 16) + k, (long) n);
            if (basic.get(ib).category() != basic.get(ia).category()) return new Ingredient[]{basic.get(ia), basic.get(ib)};
        }
        return new Ingredient[]{basic.get(0), basic.get(2)};
    }

    public static boolean isSpecial(Ingredient a, Ingredient b, long day) {
        Ingredient[] s = dailySpecial(day);
        return (s[0] == a && s[1] == b) || (s[0] == b && s[1] == a);
    }
}
