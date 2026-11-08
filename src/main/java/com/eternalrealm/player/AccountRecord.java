package com.eternalrealm.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Permanent (not season-scoped) account data, keyed by player UUID. Survives season resets.
 *
 * @param lastKnownName informational only; identity is always the UUID
 */
public record AccountRecord(UUID playerId, String lastKnownName, Instant firstLoginAt, Instant lastLoginAt,
                            long loginCount) {
    public static final int MAX_NAME_LENGTH = 64;

    public AccountRecord {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(firstLoginAt, "firstLoginAt");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
        if (lastKnownName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("lastKnownName too long: " + lastKnownName.length());
        }
        if (loginCount < 0) {
            throw new IllegalArgumentException("loginCount must be >= 0: " + loginCount);
        }
    }

    public static AccountRecord firstLogin(UUID playerId, String name, Instant now) {
        return new AccountRecord(playerId, sanitizeName(name), now, now, 1);
    }

    public AccountRecord withLogin(String name, Instant now) {
        return new AccountRecord(playerId, sanitizeName(name), firstLoginAt, now, loginCount + 1);
    }

    static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
