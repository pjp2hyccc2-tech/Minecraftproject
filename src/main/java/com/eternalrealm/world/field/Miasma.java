package com.eternalrealm.world.field;

import com.eternalrealm.core.ModInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 瘴気 (miasma), one gauge per field: every monster slain in a field stirs it up (elites and lords much more) and it
 * also seeps in slowly by itself. When it overflows the field's calamity (災厄) stirs: first an omen (兆し) — the sky
 * darkens and everyone is warned — then it manifests at its lair. Subdue it and the field is purified (浄化) for a
 * while: no miasma, more experience. A calamity left alone (nobody fighting it) or not subdued in time departs, and
 * the field keeps part of its miasma.
 *
 * <p>Pure state machine; the server ticks it, reports kills and the calamity's fate, and acts on the returned events.
 * Ticks are server ticks.</p>
 */
public final class Miasma {
    public static final int MAX = 1000;
    public static final int NORMAL_KILL = 8;
    public static final int ELITE_KILL = 25;
    public static final int LORD_KILL = 150;
    /** One point seeps in every this many ticks without anyone fighting. */
    public static final int SEEP_TICKS = 10 * 20;
    public static final int OMEN_TICKS = 60 * 20;
    /** A manifested calamity departs when nobody has fought it for this long... */
    public static final int IDLE_DEPART_TICKS = 5 * 60 * 20;
    /** ...or when it has not been subdued after this long. */
    public static final int MANIFEST_LIMIT_TICKS = 20 * 60 * 20;
    public static final int PURIFIED_TICKS = 10 * 60 * 20;
    /** Miasma that stays in the field after a calamity departed. */
    public static final int AFTER_DEPARTURE = 400;
    /** Experience from monsters of a purified field. */
    public static final double PURIFIED_EXP_MULTIPLIER = 1.25;

    public enum Phase {
        CALM,
        OMEN,
        MANIFEST,
        PURIFIED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "threat." + ModInfo.MOD_ID + ".phase." + id();
        }
    }

    /** What the server has to do. */
    public record Event(Kind kind, int field) {
        public enum Kind {
            /** The gauge overflowed: darken the sky, warn everyone, scar the lair. */
            OMEN,
            /** The omen is over: spawn the calamity at its lair. */
            MANIFEST,
            /** Nobody fought it, or it was not subdued in time: remove it. */
            DEPART,
            /** It was subdued: the field is purified. */
            PURIFIED,
            /** The purification wore off. */
            CALM
        }
    }

    private final int[] gauge = new int[FieldLayout.FIELD_COUNT + 1];
    private final Phase[] phase = new Phase[FieldLayout.FIELD_COUNT + 1];
    private final long[] since = new long[FieldLayout.FIELD_COUNT + 1];
    private final long[] lastEngaged = new long[FieldLayout.FIELD_COUNT + 1];
    private final long[] lastSeep = new long[FieldLayout.FIELD_COUNT + 1];

    public Miasma() {
        for (int field = 0; field <= FieldLayout.FIELD_COUNT; field++) {
            phase[field] = Phase.CALM;
        }
    }

    private static boolean valid(int field) {
        return field >= 1 && field <= FieldLayout.FIELD_COUNT;
    }

    public Phase phase(int field) {
        return valid(field) ? phase[field] : Phase.CALM;
    }

    public int gauge(int field) {
        return valid(field) ? gauge[field] : 0;
    }

    /** Ticks left in the current phase (omen, manifest limit, purification); 0 when calm. */
    public long remaining(int field, long now) {
        if (!valid(field)) {
            return 0L;
        }
        return switch (phase[field]) {
            case OMEN -> Math.max(0L, since[field] + OMEN_TICKS - now);
            case MANIFEST -> Math.max(0L, since[field] + MANIFEST_LIMIT_TICKS - now);
            case PURIFIED -> Math.max(0L, since[field] + PURIFIED_TICKS - now);
            case CALM -> 0L;
        };
    }

    public double expMultiplier(int field) {
        return phase(field) == Phase.PURIFIED ? PURIFIED_EXP_MULTIPLIER : 1.0;
    }

    /** A monster of {@code points} miasma was slain in {@code field}. */
    public List<Event> addKill(int field, int points, long now) {
        if (!valid(field) || phase[field] != Phase.CALM || points <= 0) {
            return List.of();
        }
        return raise(field, points, now);
    }

    private List<Event> raise(int field, int points, long now) {
        gauge[field] = Math.min(MAX, gauge[field] + points);
        if (gauge[field] >= MAX) {
            phase[field] = Phase.OMEN;
            since[field] = now;
            return List.of(new Event(Event.Kind.OMEN, field));
        }
        return List.of();
    }

    /** Someone fought the manifested calamity. */
    public void engaged(int field, long now) {
        if (valid(field) && phase[field] == Phase.MANIFEST) {
            lastEngaged[field] = now;
        }
    }

    /** The calamity of {@code field} was subdued. */
    public List<Event> subdued(int field, long now) {
        if (!valid(field) || phase[field] != Phase.MANIFEST) {
            return List.of();
        }
        phase[field] = Phase.PURIFIED;
        since[field] = now;
        gauge[field] = 0;
        return List.of(new Event(Event.Kind.PURIFIED, field));
    }

    /** The calamity could not appear or vanished on its own: the field calms down with part of its miasma. */
    public List<Event> departed(int field, long now) {
        if (!valid(field) || phase[field] != Phase.MANIFEST && phase[field] != Phase.OMEN) {
            return List.of();
        }
        phase[field] = Phase.CALM;
        since[field] = now;
        gauge[field] = Math.min(gauge[field], AFTER_DEPARTURE);
        return List.of(new Event(Event.Kind.DEPART, field));
    }

    /** Once per tick (or less often: everything is computed from {@code now}). */
    public List<Event> tick(long now) {
        List<Event> events = new ArrayList<>();
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            switch (phase[field]) {
                case CALM -> {
                    if (lastSeep[field] == 0L) {
                        lastSeep[field] = now;
                    }
                    long steps = (now - lastSeep[field]) / SEEP_TICKS;
                    if (steps > 0) {
                        lastSeep[field] += steps * SEEP_TICKS;
                        events.addAll(raise(field, (int) Math.min(MAX, steps), now));
                    }
                }
                case OMEN -> {
                    if (now - since[field] >= OMEN_TICKS) {
                        phase[field] = Phase.MANIFEST;
                        since[field] = now;
                        lastEngaged[field] = now;
                        events.add(new Event(Event.Kind.MANIFEST, field));
                    }
                }
                case MANIFEST -> {
                    if (now - lastEngaged[field] >= IDLE_DEPART_TICKS || now - since[field] >= MANIFEST_LIMIT_TICKS) {
                        events.addAll(departed(field, now));
                    }
                }
                case PURIFIED -> {
                    if (now - since[field] >= PURIFIED_TICKS) {
                        phase[field] = Phase.CALM;
                        since[field] = now;
                        lastSeep[field] = now;
                        events.add(new Event(Event.Kind.CALM, field));
                    }
                }
            }
        }
        return events;
    }

    // ------------------------------------------------------------------ persistence

    /** The gauges (phases are not kept: after a restart every field is calm, an interrupted calamity left 80%). */
    public String toJson() {
        JsonArray array = new JsonArray();
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            int value = phase[field] == Phase.CALM ? gauge[field]
                    : phase[field] == Phase.PURIFIED ? 0 : MAX * 8 / 10;
            array.add(value);
        }
        JsonObject json = new JsonObject();
        json.add("gauges", array);
        return json.toString();
    }

    public static Miasma fromJson(String text) {
        Miasma miasma = new Miasma();
        if (text == null || text.isEmpty()) {
            return miasma;
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (element.isJsonObject() && element.getAsJsonObject().has("gauges")) {
                JsonArray array = element.getAsJsonObject().getAsJsonArray("gauges");
                for (int i = 0; i < array.size() && i < FieldLayout.FIELD_COUNT; i++) {
                    miasma.gauge[i + 1] = Math.max(0, Math.min(MAX - 1, array.get(i).getAsInt()));
                }
            }
        } catch (RuntimeException e) {
            // broken data: start calm
        }
        return miasma;
    }
}
