package com.eternalrealm.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The people of the capital who are not service NPCs: one townsperson per {@code crowd.<area>.<n>} spot of the capital
 * build (strolling near it), one guard per {@code guard.<post>.<n>} spot (standing watch) and the residents of a few
 * named spots (the king at {@code castle.king}). Decides deterministically who stands where (from the spot name, suited
 * to the area: sailors at the harbour, clergy in the cathedral...), their names and what they say when a player talks
 * to them. Pure: names and lines are translation keys ({@code docs/i18n/npc_bodies.json}; residents are named by their
 * story speaker key).
 */
public final class Townsfolk {
    public static final String CROWD_PREFIX = "crowd.";
    public static final String GUARD_PREFIX = "guard.";
    /** The castle build's own gate-guard spots ({@code castle.gate_guard_w/e}) also get a guard. */
    public static final String CASTLE_GATE_GUARD_PREFIX = "castle.gate_guard_";
    /** How far (blocks) a townsperson strolls from its spot. */
    public static final int STROLL_RADIUS = 6;

    public enum Kind {
        /** Strolls within {@link #STROLL_RADIUS} of its spot, pauses, looks at players. */
        CROWD,
        /** Stands watch at its spot facing the spot's yaw. */
        GUARD,
        /** A named person who stays at the spot (see {@link #residentSpots()}). */
        RESIDENT
    }

    /** A parsed spot name: kind, area (crowd) or post (guard), and the number ({@code -1} if not a number). */
    public record Spot(Kind kind, String area, int number) {
    }

    private static final List<String> COMMONERS = List.of("housewife", "merchant", "apprentice", "elder", "housewife2",
            "merchant2", "child", "apprentice2", "elder2", "bard", "child2", "farmer2");
    private static final Map<String, List<String>> AREAS = new LinkedHashMap<>();
    private static final Map<String, String> GROUPS = new LinkedHashMap<>();
    private static final Map<String, Integer> LINES = new LinkedHashMap<>();
    /** Named spots of the capital build with the person who lives there. */
    private static final Map<String, String> RESIDENTS = Map.of("castle.king", "king");

    static {
        AREAS.put("plaza", List.of("merchant", "housewife", "noble_gent", "child", "bard", "elder", "merchant2",
                "noble_lady", "apprentice", "housewife2", "adventurer", "child2", "elder2", "bard2", "noble_gent2",
                "noble_lady2"));
        AREAS.put("market", List.of("merchant", "housewife", "farmer", "merchant2", "apprentice2", "housewife2",
                "farmer2", "elder2", "merchant", "child", "sailor2", "apprentice"));
        AREAS.put("street", List.of("housewife", "apprentice", "merchant", "elder", "housewife2", "child2",
                "noble_gent2", "apprentice2", "merchant2", "bard", "child", "elder2"));
        AREAS.put("harbour", List.of("sailor", "sailor2", "merchant", "sailor", "apprentice", "sailor2", "merchant2",
                "child"));
        AREAS.put("guild", List.of("adventurer", "adventurer2", "bard", "adventurer", "merchant", "adventurer2",
                "apprentice"));
        AREAS.put("tavern", List.of("sailor", "adventurer", "bard", "farmer", "adventurer2", "bard2", "merchant",
                "sailor2"));
        AREAS.put("inn", List.of("merchant", "adventurer", "maid2", "bard2", "adventurer2", "elder", "merchant2"));
        AREAS.put("cathedral", List.of("priest", "nun", "elder", "noble_lady", "nun", "housewife", "elder2",
                "noble_gent"));
        AREAS.put("residential", List.of("housewife", "child", "elder", "apprentice", "housewife2", "child2",
                "elder2", "maid2", "apprentice2"));
        AREAS.put("farm", List.of("farmer", "farmer2", "farmer", "child", "farmer2", "elder"));
        AREAS.put("castle", List.of("maid", "noble_lady", "maid2", "noble_gent", "noble_lady2", "priest", "maid",
                "noble_gent2"));

        GROUPS.put("merchant", "merchant");
        GROUPS.put("housewife", "commoner");
        GROUPS.put("farmer", "farmer");
        GROUPS.put("sailor", "sailor");
        GROUPS.put("noble_lady", "noble");
        GROUPS.put("noble_gent", "noble");
        GROUPS.put("elder", "elder");
        GROUPS.put("apprentice", "young");
        GROUPS.put("maid", "maid");
        GROUPS.put("bard", "bard");
        GROUPS.put("child", "child");
        GROUPS.put("adventurer", "adventurer");
        GROUPS.put("guard", "guard");
        GROUPS.put("knight", "guard");
        GROUPS.put("archer", "guard");
        GROUPS.put("priest", "clergy");
        GROUPS.put("nun", "clergy");
        GROUPS.put("citizen", "commoner");
        GROUPS.put("king", "king");

        LINES.put("commoner", 5);
        LINES.put("merchant", 4);
        LINES.put("farmer", 4);
        LINES.put("sailor", 4);
        LINES.put("noble", 4);
        LINES.put("elder", 4);
        LINES.put("young", 4);
        LINES.put("maid", 4);
        LINES.put("bard", 4);
        LINES.put("child", 4);
        LINES.put("adventurer", 4);
        LINES.put("guard", 5);
        LINES.put("clergy", 4);
        LINES.put("king", 4);
    }

    private Townsfolk() {
    }

    /**
     * {@code crowd.<area>.<n>}, {@code guard.<post>.<n>}, a castle gate-guard spot or a resident's spot; empty for any
     * other name.
     */
    public static Optional<Spot> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        if (RESIDENTS.containsKey(name)) {
            return Optional.of(new Spot(Kind.RESIDENT, name.substring(0, name.indexOf('.')), -1));
        }
        Kind kind;
        String rest;
        if (name.startsWith(CROWD_PREFIX)) {
            kind = Kind.CROWD;
            rest = name.substring(CROWD_PREFIX.length());
        } else if (name.startsWith(GUARD_PREFIX)) {
            kind = Kind.GUARD;
            rest = name.substring(GUARD_PREFIX.length());
        } else if (name.startsWith(CASTLE_GATE_GUARD_PREFIX)) {
            return Optional.of(new Spot(Kind.GUARD, "castle_gate", -1));
        } else {
            return Optional.empty();
        }
        int dot = rest.lastIndexOf('.');
        String area = dot < 0 ? rest : rest.substring(0, dot);
        if (area.isEmpty()) {
            return Optional.empty();
        }
        int number = -1;
        if (dot >= 0) {
            try {
                number = Integer.parseInt(rest.substring(dot + 1));
            } catch (NumberFormatException notANumber) {
                number = -1;
            }
        }
        return Optional.of(new Spot(kind, area, number));
    }

    /** Whether a capital spot name gets a townsperson or a guard. */
    public static boolean isSpot(String name) {
        return parse(name).isPresent();
    }

    /** The townspeople who may stand in an area ({@code market}, {@code harbour}...; unknown areas: commoners). */
    public static List<String> pool(String area) {
        String key = area == null ? "" : area.toLowerCase(Locale.ROOT);
        List<String> pool = AREAS.get(key);
        if (pool != null) {
            return pool;
        }
        // compound area names ("market_north", "harbour_east") use the pool of the area they start with
        for (Map.Entry<String, List<String>> entry : AREAS.entrySet()) {
            if (key.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return COMMONERS;
    }

    /** Named spots that have a resident ({@code castle.king}). */
    public static Set<String> residentSpots() {
        return RESIDENTS.keySet();
    }

    /** Area names with their own pool. */
    public static Set<String> areas() {
        return AREAS.keySet();
    }

    /**
     * The appearance at a spot (deterministic): consecutive numbers of one area walk through its pool, starting at an
     * offset that depends on the area, so neighbours differ and every area looks different. Guards: archers on walls and
     * towers, knights inside the castle, the royal gate guard with the halberd everywhere else.
     */
    public static String appearanceFor(String spotName) {
        Optional<Spot> parsed = parse(spotName);
        if (parsed.isEmpty()) {
            return NpcBodies.DEFAULT;
        }
        Spot spot = parsed.get();
        if (spot.kind() == Kind.RESIDENT) {
            return NpcBodies.orDefault(RESIDENTS.get(spotName));
        }
        if (spot.kind() == Kind.GUARD) {
            return guardFor(spot.area());
        }
        List<String> pool = pool(spot.area());
        int offset = Integer.remainderUnsigned(hash(spot.area()), pool.size());
        int index = spot.number() >= 0 ? Math.floorMod(spot.number() + offset, pool.size())
                : Integer.remainderUnsigned(hash(spotName), pool.size());
        return NpcBodies.orDefault(pool.get(index));
    }

    /** The guard standing at a post. */
    public static String guardFor(String post) {
        String p = post == null ? "" : post.toLowerCase(Locale.ROOT);
        if (p.contains("gate")) {
            return "guard";
        }
        if (p.contains("wall") || p.contains("tower") || p.contains("rampart") || p.contains("battlement")) {
            return "archer";
        }
        if (p.contains("castle") || p.contains("keep") || p.contains("throne") || p.contains("hall")
                || p.contains("palace") || p.contains("court")) {
            return "knight";
        }
        return "guard";
    }

    /** The type of an appearance: colour variants ({@code merchant2}) belong to their type ({@code merchant}). */
    public static String type(String appearance) {
        String id = appearance == null ? NpcBodies.DEFAULT : appearance;
        int end = id.length();
        while (end > 1 && Character.isDigit(id.charAt(end - 1))) {
            end--;
        }
        return id.substring(0, end);
    }

    /** Every townsfolk type that has a name, a portrait and greetings. */
    public static Set<String> types() {
        return GROUPS.keySet();
    }

    /**
     * Name of a townsperson; also the speaker key of their greeting, whose last segment (the type) names the portrait
     * {@code textures/gui/portrait/<type>.png}.
     */
    public static String nameKey(String appearance) {
        String type = type(appearance);
        if (RESIDENTS.containsValue(type)) {
            return "story.eternalrealm.speaker." + type; // residents speak as their story selves (name and portrait)
        }
        return "npc.eternalrealm.folk." + (GROUPS.containsKey(type) ? type : "citizen");
    }

    /** What kind of things a type says. */
    public static String group(String appearance) {
        return GROUPS.getOrDefault(type(appearance), "commoner");
    }

    /** How many greetings the appearance's group has. */
    public static int greetingCount(String appearance) {
        return LINES.get(group(appearance));
    }

    /** Greeting {@code roll} (any int; wrapped into range) of an appearance. */
    public static String greetingKey(String appearance, int roll) {
        String group = group(appearance);
        return "npc.eternalrealm.folk.say." + group + "." + (Math.floorMod(roll, LINES.get(group)) + 1);
    }

    /** Every translation key of {@code npc_bodies.json} used here (names and greetings; not the residents' names). */
    public static Set<String> translationKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String type : GROUPS.keySet()) {
            if (!RESIDENTS.containsValue(type)) {
                keys.add("npc.eternalrealm.folk." + type);
            }
        }
        for (Map.Entry<String, Integer> group : LINES.entrySet()) {
            for (int n = 1; n <= group.getValue(); n++) {
                keys.add("npc.eternalrealm.folk.say." + group.getKey() + "." + n);
            }
        }
        return keys;
    }

    /** Every appearance a townsperson or guard can have. */
    public static List<String> appearances() {
        Set<String> all = new LinkedHashSet<>(COMMONERS);
        for (List<String> pool : AREAS.values()) {
            all.addAll(pool);
        }
        all.addAll(List.of("guard", "knight", "archer"));
        all.addAll(RESIDENTS.values());
        return new ArrayList<>(all);
    }

    /** FNV-1a (32 bit): stable across Java versions and platforms. */
    static int hash(String text) {
        int h = 0x811C9DC5;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }
}
