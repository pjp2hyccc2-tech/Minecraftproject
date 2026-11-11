package com.eternalrealm.estate;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Names an estate and the design it is built from: {@code kind} ({@value #ROOM} for a player's inn room,
 * {@value #GUILD} for a guild house, other kinds are allowed), {@code owner} (a player's or a guild's id) and
 * {@code design}, the blueprint {@code data/eternalrealm/estates/<kind>/<design>/}.
 *
 * <p>There is at most one estate per kind and owner: two keys name the same estate when {@link #sameEstate} (the
 * design is what it looks like, which a redesign changes). The service looks estates up by {@link #id()} and ignores
 * the design of keys passed to it, except where a design is asked for ({@code claim}).</p>
 */
public record EstateKey(String kind, UUID owner, String design) {
    public static final String ROOM = "room";
    public static final String GUILD = "guild";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    public EstateKey {
        if (!isName(kind)) {
            throw new IllegalArgumentException("bad estate kind '" + kind + "'");
        }
        Objects.requireNonNull(owner, "owner");
        if (!isName(design)) {
            throw new IllegalArgumentException("bad estate design '" + design + "'");
        }
    }

    /** Whether {@code value} may be a kind or a design ({@code [a-z0-9_]{1,32}}). */
    public static boolean isName(String value) {
        return value != null && NAME.matcher(value).matches();
    }

    /** {@code "<kind>/<owner>"}: the estate whatever its design. */
    public String id() {
        return id(kind, owner);
    }

    public static String id(String kind, UUID owner) {
        return kind + "/" + owner;
    }

    public boolean sameEstate(EstateKey other) {
        return other != null && kind.equals(other.kind) && owner.equals(other.owner);
    }

    public EstateKey withDesign(String newDesign) {
        return new EstateKey(kind, owner, newDesign);
    }

    /** Resource folder of the design's blueprint, relative to the mod's data namespace. */
    public String blueprintFolder() {
        return blueprintFolder(kind, design);
    }

    public static String blueprintFolder(String kind, String design) {
        return "estates/" + kind + "/" + design;
    }
}
