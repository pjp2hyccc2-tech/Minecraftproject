package com.eternalrealm.world.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * The rules of the waystones (転移碑) of the field map ({@link FieldLayout#waystones()}): a player records a waystone by
 * walking up to it (or touching it) and can then travel from any waystone to any recorded one whose field is open.
 * Server-authoritative: the server checks every travel request with {@link #check}.
 */
public final class Waystones {
    /** A player within this distance (blocks, horizontal) of a waystone's point may use it. */
    public static final double USE_RADIUS = 7.0;
    /** Walking this close to a waystone records it. */
    public static final double DISCOVER_RADIUS = 12.0;
    /** A waystone block belongs to the nearest waystone point within this distance. */
    public static final double IDENTIFY_RADIUS = 16.0;
    /** Seconds between two travels of one player. */
    public static final int COOLDOWN_SECONDS = 8;
    /** Player-data extension that holds the recorded waystones. */
    public static final String EXTENSION = "waystones";
    /** S2C UI channel: open the travel menu ({@code {"at", "cooldown", "recorded"}}). */
    public static final String UI_CHANNEL = "waystone";
    /** S2C UI channel: the player just arrived (screen flash). */
    public static final String ARRIVE_CHANNEL = "waystone_arrive";
    /** C2S UI action: travel to {@code {"to": id}}. */
    public static final String TRAVEL_ACTION = "waystone.travel";

    /** Outcome of a travel request. */
    public enum Result {
        OK, UNKNOWN, SAME, NOT_AT_WAYSTONE, NOT_DISCOVERED, SEALED, COOLDOWN, IN_COMBAT;

        /** {@code waystone.eternalrealm.result.<name>} */
        public String translationKey() {
            return "waystone.eternalrealm.result." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Waystones() {
    }

    public static List<FieldLayout.Waystone> all() {
        return FieldLayout.waystones();
    }

    public static Optional<FieldLayout.Waystone> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (FieldLayout.Waystone waystone : all()) {
            if (waystone.id().equals(id)) {
                return Optional.of(waystone);
            }
        }
        return Optional.empty();
    }

    /** The waystone whose point is nearest to (x, z), if it is within {@code radius} blocks. */
    public static Optional<FieldLayout.Waystone> nearest(double x, double z, double radius) {
        FieldLayout.Waystone best = null;
        double bestDistance = radius * radius;
        for (FieldLayout.Waystone waystone : all()) {
            double dx = waystone.x() + 0.5 - x;
            double dz = waystone.z() + 0.5 - z;
            double distance = dx * dx + dz * dz;
            if (distance <= bestDistance) {
                best = waystone;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** {@code waystone.eternalrealm.<id>}: the waystone's name. */
    public static String translationKey(String id) {
        return "waystone.eternalrealm." + id;
    }

    /** Milliseconds a player must still wait after travelling at {@code lastTravel} (0 = may travel). */
    public static long cooldownLeft(long now, long lastTravel) {
        if (lastTravel <= 0L) {
            return 0L;
        }
        return Math.max(0L, lastTravel + COOLDOWN_SECONDS * 1000L - now);
    }

    /**
     * Whether a player standing at {@code at} (the waystone they are using, empty if none is near) may travel to
     * {@code toId}.
     *
     * @param discovered the waystones the player has recorded
     * @param unlocked   which fields (area ids) are open on this server
     */
    public static Result check(Optional<FieldLayout.Waystone> at, String toId, Set<String> discovered,
                               IntPredicate unlocked, long now, long lastTravel, boolean inCombat) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(discovered, "discovered");
        Objects.requireNonNull(unlocked, "unlocked");
        Optional<FieldLayout.Waystone> to = byId(toId);
        if (to.isEmpty()) {
            return Result.UNKNOWN;
        }
        if (at.isEmpty()) {
            return Result.NOT_AT_WAYSTONE;
        }
        if (at.get().id().equals(toId)) {
            return Result.SAME;
        }
        if (!discovered.contains(toId)) {
            return Result.NOT_DISCOVERED;
        }
        if (!unlocked.test(to.get().field())) {
            return Result.SEALED;
        }
        if (inCombat) {
            return Result.IN_COMBAT;
        }
        if (cooldownLeft(now, lastTravel) > 0L) {
            return Result.COOLDOWN;
        }
        return Result.OK;
    }

    // ------------------------------------------------------------------ the recorded set as JSON

    /** {@code {"ids": [...]}} with the ids sorted. */
    public static String toJson(Collection<String> ids) {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (String id : new TreeSet<>(ids)) {
            array.add(id);
        }
        json.add("ids", array);
        return json.toString();
    }

    /** Lenient: unknown ids and malformed documents are dropped (an empty set at worst). */
    public static Set<String> fromJson(String text) {
        TreeSet<String> ids = new TreeSet<>();
        if (text == null || text.isBlank()) {
            return Collections.unmodifiableSet(ids);
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("ids")
                    && parsed.getAsJsonObject().get("ids").isJsonArray()) {
                for (JsonElement element : parsed.getAsJsonObject().getAsJsonArray("ids")) {
                    if (element.isJsonPrimitive() && byId(element.getAsString()).isPresent()) {
                        ids.add(element.getAsString());
                    }
                }
            }
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            ids.clear();
        }
        return Collections.unmodifiableSet(ids);
    }
}
