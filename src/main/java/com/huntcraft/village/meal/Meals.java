package com.huntcraft.village.meal;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Dishes served at the Mofu Diner (モフ亭). */
public final class Meals {
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

        public Component displayName() {
            return Component.translatable("meal.huntcraft.skill." + name().toLowerCase());
        }

        public Component description() {
            return Component.translatable("meal.huntcraft.skill." + name().toLowerCase() + ".desc");
        }
    }

    /**
     * @param hp      max health bonus (half hearts)
     * @param stamina max stamina bonus
     * @param atkPct  attack bonus in percent
     * @param def     armor points
     * @param icon    index in the kitchen icon strip
     */
    public record Meal(String id, int price, int requiredHR, int hp, int stamina, int atkPct, int def, List<Skill> skills, int icon) {
        public Component displayName() {
            return Component.translatable("meal.huntcraft." + id);
        }

        public Component description() {
            return Component.translatable("meal.huntcraft." + id + ".desc");
        }
    }

    public static final List<Meal> ALL = List.of(
            new Meal("onigiri", 120, 1, 10, 25, 0, 0, List.of(Skill.SWIFT), 0),
            new Meal("yakitori", 200, 1, 10, 0, 5, 0, List.of(Skill.CARVE), 1),
            new Meal("dango", 150, 1, 0, 50, 0, 0, List.of(Skill.CHEER), 2),
            new Meal("tempura", 240, 1, 0, 50, 5, 0, List.of(Skill.STAMINA), 3),
            new Meal("sushi", 300, 2, 15, 0, 0, 5, List.of(Skill.REGEN), 4),
            new Meal("nabe", 280, 2, 20, 25, 0, 3, List.of(Skill.GUTS), 5),
            new Meal("donburi", 360, 3, 20, 0, 10, 0, List.of(Skill.POWER), 6),
            new Meal("chanko", 520, 4, 20, 50, 5, 5, List.of(Skill.CARVE, Skill.GUTS), 7));

    private Meals() {
    }

    @Nullable
    public static Meal get(String id) {
        for (Meal m : ALL) if (m.id().equals(id)) return m;
        return null;
    }
}
