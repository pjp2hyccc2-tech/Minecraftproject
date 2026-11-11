package com.eternalrealm.item;

import java.util.List;
import java.util.Objects;

/**
 * One line of an equipment tooltip, independent of Minecraft's text classes. The client renders it as
 * {@code Component.translatable(translationKey, args...)} in {@link #color()}, where a {@link Text} argument is passed
 * as its string and a {@link Key} argument as {@code Component.translatable(key)}.
 *
 * @param kind  what the line shows, so the client may restyle or reorder kinds
 * @param color suggested RGB colour ({@code 0xRRGGBB})
 */
public record TooltipLine(Kind kind, String translationKey, List<Arg> args, int color) {
    public static final int COLOR_TEXT = 0xAAAAAA;
    public static final int COLOR_STAT = 0xFFFFFF;
    public static final int COLOR_AFFIX = 0x55FFFF;
    public static final int COLOR_ENHANCEMENT = 0xFFFF55;
    public static final int COLOR_WARNING = 0xFF5555;
    public static final int COLOR_DESIGN = 0xFFD27A;
    public static final int COLOR_TRAIT = 0x9BE28A;
    public static final int COLOR_PERK = 0xE59BFF;
    public static final int COLOR_ART = 0xFF9F5A;
    public static final int COLOR_SET = 0x7FC8FF;
    public static final int COLOR_LORE = 0x8A8A9A;
    /** The gold of a relic (金枠): its name, its frame and its tooltip header. */
    public static final int COLOR_RELIC = 0xFFD84A;

    public enum Kind {
        /** The display name (the item's name, not a tooltip body line). */
        NAME,
        /** Rarity and weapon type / slot. */
        RARITY,
        /** Attack, defense or max health of the piece itself. */
        BASE_STAT,
        AFFIX,
        ENHANCEMENT,
        /** Required level; {@link #COLOR_WARNING} when the viewer does not meet it. */
        REQUIREMENT,
        /** The jobs that can use a weapon; {@link #COLOR_WARNING} when the viewer's job cannot. */
        USABLE_BY,
        /** Bound to a player, not tradeable. */
        BINDING,
        /** Where a named design comes from ("ヌシ「…」の得物"). */
        DESIGN,
        /** A named weapon's handling, element and fixed bonuses. */
        TRAIT,
        /** A special effect resolved when hits land (lifesteal, evasion, ...). */
        PERK,
        /** The weapon art of a boss weapon. */
        ART,
        /** A named armor set and its two- and four-piece bonuses. */
        SET,
        /** The flavour text of a named design. */
        LORE
    }

    /** A translation argument. */
    public sealed interface Arg permits Text, Key, Join, Nested {
    }

    /** A nested translation with its own arguments ("会心率 +3%" inside "2点: %s"). */
    public record Nested(String translationKey, List<Arg> args) implements Arg {
        public Nested {
            Objects.requireNonNull(translationKey, "translationKey");
            args = List.copyOf(args);
        }

        public static Nested of(String translationKey, Arg... args) {
            return new Nested(translationKey, List.of(args));
        }
    }

    /** Literal text (already formatted numbers). */
    public record Text(String text) implements Arg {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A nested translation (tier prefix, item noun, rarity name, ...). */
    public record Key(String translationKey) implements Arg {
        public Key {
            Objects.requireNonNull(translationKey, "translationKey");
        }
    }

    /** Several arguments joined with a translated separator ("ナイト・魔剣士", "Knight, Spellblade"). */
    public record Join(List<Arg> parts, String separatorKey) implements Arg {
        public Join {
            parts = List.copyOf(parts);
            Objects.requireNonNull(separatorKey, "separatorKey");
        }
    }

    public TooltipLine {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(translationKey, "translationKey");
        args = List.copyOf(args);
        Checks.range("color", color, 0, 0xFFFFFF);
    }

    public static TooltipLine of(Kind kind, String translationKey, int color, Arg... args) {
        return new TooltipLine(kind, translationKey, List.of(args), color);
    }
}
