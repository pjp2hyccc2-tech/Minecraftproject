package com.eternalrealm.guild;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Guild name and tag rules.
 *
 * <p><b>Name:</b> 2 to 16 characters (Unicode code points, so a kanji counts as one) of letters of any script
 * (including hiragana, katakana, kanji and the prolonged sound mark ー), digits, space, {@code _} and {@code -}; no
 * leading or trailing space and no double space (two names that look the same must not both exist). Input is
 * NFC-normalized first, which is invisible to players. Uniqueness uses {@link #nameKey(String)}: NFKC plus lower case,
 * so full-width/half-width variants and different letter cases count as the same name ("ＡＢＣ" = "abc" = "ABC",
 * "ｶﾞ" = "ガ").</p>
 *
 * <p><b>Tag:</b> 2 to 5 characters {@code [A-Z0-9]}, stored upper case; input is accepted in any letter case.</p>
 *
 * <p>Known limitation: look-alike letters of different scripts (Latin "A" and Cyrillic "А") are different names.</p>
 */
public final class GuildNames {
    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 16;
    public static final int MIN_TAG_LENGTH = 2;
    public static final int MAX_TAG_LENGTH = 5;

    private static final Pattern TAG = Pattern.compile("[A-Z0-9]{2,5}");
    private static final Pattern TAG_INPUT = Pattern.compile("[A-Za-z0-9]{2,5}");

    private GuildNames() {
    }

    /** Canonical (NFC) form of a name typed by a player; validate the result with {@link #isValidName(String)}. */
    public static String normalizeName(String raw) {
        return Normalizer.normalize(Objects.requireNonNull(raw, "raw"), Normalizer.Form.NFC);
    }

    /** Whether {@code name} is a valid stored name (already NFC-normalized). */
    public static boolean isValidName(String name) {
        if (name == null || !Normalizer.isNormalized(name, Normalizer.Form.NFC)) {
            return false;
        }
        int length = name.codePointCount(0, name.length());
        if (length < MIN_NAME_LENGTH || length > MAX_NAME_LENGTH) {
            return false;
        }
        if (name.startsWith(" ") || name.endsWith(" ") || name.contains("  ")) {
            return false;
        }
        return name.codePoints().allMatch(GuildNames::isNameCharacter);
    }

    private static boolean isNameCharacter(int codePoint) {
        return Character.isLetter(codePoint) || Character.isDigit(codePoint)
                || codePoint == ' ' || codePoint == '_' || codePoint == '-';
    }

    /** Key deciding whether two names are "the same" (NFKC, lower case). */
    public static String nameKey(String name) {
        String folded = Normalizer.normalize(Objects.requireNonNull(name, "name"), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return Normalizer.normalize(folded, Normalizer.Form.NFKC);
    }

    /** The stored (upper case) form of a tag typed by a player, or empty if it is not 2-5 ASCII letters/digits. */
    public static Optional<String> normalizeTag(String raw) {
        if (raw == null || !TAG_INPUT.matcher(raw).matches()) {
            return Optional.empty();
        }
        return Optional.of(raw.toUpperCase(Locale.ROOT));
    }

    /** Whether {@code tag} is a valid stored tag ({@code [A-Z0-9]{2,5}}). */
    public static boolean isValidTag(String tag) {
        return tag != null && TAG.matcher(tag).matches();
    }
}
