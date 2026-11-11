package com.eternalrealm.estate;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Showcase estates (見学用の屋敷, docs/modules/testkit.md): for testing, every design of a kind can stand once more as a
 * showcase that no player or guild owns. Its owner is a fixed id derived from the kind and the design (a name-based
 * UUID of {@code eternalrealm:showcase/<kind>/<design>}), so the same showcase is found again after a restart and can
 * never be mistaken for a player's or a guild's estate: a player's id is random or derived from a player name, a
 * guild's id is random. Who may tour showcases is the platform layer's business. Pure.
 */
public final class Showcase {
    private static final String PREFIX = "eternalrealm:showcase/";

    private Showcase() {
    }

    /** The owner id of the showcase of {@code design} of {@code kind}. */
    public static UUID owner(String kind, String design) {
        if (!EstateKey.isName(kind) || !EstateKey.isName(design)) {
            throw new IllegalArgumentException("bad showcase " + kind + "/" + design);
        }
        return UUID.nameUUIDFromBytes((PREFIX + kind + "/" + design).getBytes(StandardCharsets.UTF_8));
    }

    /** The key of the showcase of {@code design} of {@code kind}. */
    public static EstateKey key(String kind, String design) {
        return new EstateKey(kind, owner(kind, design), design);
    }

    /**
     * Whether {@code key} names a showcase (its owner is the showcase owner of its kind and design). Cheap for the
     * random ids of players and guilds (only name-based ids are hashed).
     */
    public static boolean is(EstateKey key) {
        return key != null && key.owner().version() == 3 && key.owner().equals(owner(key.kind(), key.design()));
    }

    /** Which of {@code designs} (of {@code kind}) {@code owner} is the showcase of, if any. */
    public static Optional<String> designOf(String kind, UUID owner, Collection<String> designs) {
        if (owner == null || !EstateKey.isName(kind)) {
            return Optional.empty();
        }
        for (String design : designs) {
            if (EstateKey.isName(design) && owner.equals(owner(kind, design))) {
                return Optional.of(design);
            }
        }
        return Optional.empty();
    }
}
