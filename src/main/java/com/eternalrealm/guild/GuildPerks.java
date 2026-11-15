package com.eternalrealm.guild;

import java.util.ArrayList;
import java.util.List;

/**
 * What a guild's level gives (docs/modules/guild_levels.md). Pure.
 *
 * <pre>
 * members             30, two more per level (68 at level 20)               GuildRules
 * blessing (加護)      +0.5 % experience per level above the first (+9.5 %)  members, from monsters, quests, bosses
 * house discount       5 % from level 5, 10 % from 10, 15 % from 15, 20 % at 20: buying a guild house, switching up
 * standing (格)        見習い 1, 熟練 5, 精鋭 10, 名門 15, 伝説 20: the colour of the guild's tag before its members' names
 * </pre>
 */
public final class GuildPerks {
    /** Experience bonus per level above the first. */
    public static final double EXPERIENCE_PER_LEVEL = 0.005;
    /** House discount per five levels. */
    public static final double DISCOUNT_PER_FIVE = 0.05;

    /** A guild's standing: its tag's colour and title, by level. */
    public enum Standing {
        FLEDGLING(1, 0xAAAAAA), SEASONED(5, 0x55FF55), ELITE(10, 0x55FFFF), RENOWNED(15, 0xFFAA00),
        LEGENDARY(20, 0xFF55FF);

        private final int level;
        private final int color;

        Standing(int level, int color) {
            this.level = level;
            this.color = color;
        }

        /** The level it starts at. */
        public int level() {
            return level;
        }

        /** RGB of the guild's tag. */
        public int color() {
            return color;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public String translationKey() {
            return "guild_standing.eternalrealm." + id();
        }
    }

    /** One perk line for the guild screen: what it is and how much at a level. */
    public record Perk(String key, String value) {
    }

    private GuildPerks() {
    }

    private static int clamp(int level) {
        return Math.max(1, Math.min(GuildRules.MAX_LEVELS, level));
    }

    /** Extra experience the members get (a fraction: 0.095 = +9.5 %). */
    public static double experienceBonus(int level) {
        return EXPERIENCE_PER_LEVEL * (clamp(level) - 1);
    }

    /** Discount on buying a guild house or switching to a dearer one (a fraction). */
    public static double houseDiscount(int level) {
        return DISCOUNT_PER_FIVE * Math.min(4, clamp(level) / 5);
    }

    /** {@code price} after the discount of {@code level} (whole gold, rounded up: never free). */
    public static long discounted(long price, int level) {
        if (price <= 0) {
            return Math.max(0L, price);
        }
        double discount = houseDiscount(level);
        return Math.max(1L, (long) Math.ceil(price * (1.0 - discount) - 1.0e-9));
    }

    public static Standing standing(int level) {
        Standing best = Standing.FLEDGLING;
        for (Standing standing : Standing.values()) {
            if (clamp(level) >= standing.level()) {
                best = standing;
            }
        }
        return best;
    }

    /** The perks at {@code level}, for the screen: members, blessing, discount, standing. */
    public static List<Perk> at(int level, GuildRules rules) {
        List<Perk> perks = new ArrayList<>();
        int l = Math.min(clamp(level), rules.maxLevel());
        perks.add(new Perk("members", Integer.toString(rules.memberCap(l))));
        perks.add(new Perk("experience", percent(experienceBonus(l))));
        perks.add(new Perk("discount", percent(houseDiscount(l))));
        perks.add(new Perk("standing", standing(l).id()));
        return perks;
    }

    /** "9.5%" style text of a fraction (no trailing zeros). */
    public static String percent(double fraction) {
        double value = Math.round(fraction * 1000.0) / 10.0;
        return (value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value)) + "%";
    }
}
