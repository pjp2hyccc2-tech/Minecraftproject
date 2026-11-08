package com.eternalrealm.story;

import java.util.regex.Pattern;

/**
 * Argument validation shared by the story types. The story content is code, so a failure here is a programming error
 * and must fail loudly at startup / in tests. Ids that arrive in triggers are checked too, so that a malformed id can
 * never be turned into a different dialogue id (e.g. one containing a dot).
 */
final class Check {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern DOTTED = Pattern.compile("[a-z0-9_]{1,64}(\\.[a-z0-9_]{1,64}){0,15}");

    private Check() {
    }

    /** One id segment, e.g. "b1", "quest_board", "m1_arrival". */
    static String id(String name, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + ID.pattern() + ": " + value);
        }
        return value;
    }

    /** Dot-separated id segments, e.g. a dialogue id "boss.b1.intro" or a key "story.eternalrealm.speaker.lilia". */
    static String dotted(String name, String value) {
        if (value == null || !DOTTED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be dot-separated [a-z0-9_] segments: " + value);
        }
        return value;
    }

    static int range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }
}
