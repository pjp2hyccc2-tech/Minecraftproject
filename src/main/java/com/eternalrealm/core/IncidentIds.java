package com.eternalrealm.core;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generates short identifiers that tie a player-facing error message to the matching server log entry.
 * Players can report the ID to an administrator without seeing internal details.
 */
public final class IncidentIds {
    private static final SecureRandom RANDOM = new SecureRandom();

    private IncidentIds() {
    }

    /** @return an 8 character upper-case hexadecimal identifier, e.g. {@code 3FA2C91B} */
    public static String next() {
        return String.format(Locale.ROOT, "%08X", RANDOM.nextInt());
    }
}
