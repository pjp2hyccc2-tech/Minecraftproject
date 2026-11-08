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
        /** Bound to a player, not tradeable. */
        BINDING
    }

    /** A translation argument. */
    public sealed interface Arg permits Text, Key {
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
