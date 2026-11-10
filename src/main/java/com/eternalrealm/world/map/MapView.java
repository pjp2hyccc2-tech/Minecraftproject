package com.eternalrealm.world.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * What the map screens need from the server, sent about once a second on the {@code map} UI channel
 * ({@link #CHANNEL}): where the realm is, which areas are open (world progress) and which ones this player has seen,
 * where the party members are, the town NPCs near the player (with quest marks) and, inside a dungeon instance, the
 * instance's layout. Everything else (areas, biomes, dungeon entrances, the capital and the tower) is pure data the
 * client has itself ({@link MapMarkers}, {@code AstoriaLayout}).
 *
 * <p>The client decodes leniently ({@link #fromJson}): missing or broken fields become defaults and lists are capped,
 * so a bad document can never break the HUD.</p>
 */
public record MapView(Realm realm, Set<Integer> unlockedAreas, Set<Integer> discoveredAreas, boolean finalBossUnlocked,
                      List<Member> party, List<Npc> npcs, Optional<Dungeon> dungeon, Set<String> waystones) {
    public static final String CHANNEL = "map";
    public static final int MAX_MEMBERS = 40;
    public static final int MAX_NPCS = 64;
    public static final int MAX_ROOMS = 16;
    public static final int MAX_CORRIDORS = 32;
    public static final int MAX_TRAPS = 64;
    private static final int MAX_TEXT = 512;

    /** Before the first document arrives: the realm at (0, 0), only the first area open. */
    public static final MapView EMPTY = new MapView(Realm.DEFAULT, Set.of(1), Set.of(), false, List.of(), List.of(),
            Optional.empty(), Set.of());

    public MapView {
        Objects.requireNonNull(realm, "realm");
        unlockedAreas = Collections.unmodifiableSet(new TreeSet<>(unlockedAreas));
        discoveredAreas = Collections.unmodifiableSet(new TreeSet<>(discoveredAreas));
        party = List.copyOf(party);
        npcs = List.copyOf(npcs);
        Objects.requireNonNull(dungeon, "dungeon");
        waystones = Collections.unmodifiableSet(new TreeSet<>(waystones));
    }

    /** Without recorded waystones. */
    public MapView(Realm realm, Set<Integer> unlockedAreas, Set<Integer> discoveredAreas, boolean finalBossUnlocked,
                   List<Member> party, List<Npc> npcs, Optional<Dungeon> dungeon) {
        this(realm, unlockedAreas, discoveredAreas, finalBossUnlocked, party, npcs, dungeon, Set.of());
    }

    /** Whether this player has recorded the waystone (転移碑) {@code id}. */
    public boolean hasWaystone(String id) {
        return waystones.contains(id);
    }

    /**
     * The world the areas are in: {@code dimension} (e.g. {@code eternalrealm:astoria}), the centre of the rings and
     * whether it is the dedicated アストリア dimension (only then do the biomes and dungeon entrances of the pure layout
     * exist there).
     */
    public record Realm(String dimension, int centerX, int centerZ, boolean astoria) {
        public static final Realm DEFAULT = new Realm("eternalrealm:astoria", 0, 0, true);

        public Realm {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    /** Another member of the player's party; {@code health} is the fraction 0..1. */
    public record Member(UUID id, String name, String dimension, double x, double y, double z, float yaw, float health,
                         boolean leader) {
        public Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    /** Quest mark over an NPC: something to accept ("!") or to hand in ("?"). */
    public enum Mark {
        NONE(""), OFFER("!"), READY("?");

        private final String symbol;

        Mark(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        static Mark of(String symbol) {
            for (Mark mark : values()) {
                if (mark.symbol.equals(symbol)) {
                    return mark;
                }
            }
            return NONE;
        }
    }

    /**
     * A town NPC near the player.
     *
     * @param role the NPC role id (e.g. {@code quest_board}, {@code story})
     * @param name the NPC's name as a text component in JSON (so it is translated on the client)
     */
    public record Npc(double x, double y, double z, String role, String name, Mark mark) {
        public Npc {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(mark, "mark");
        }
    }

    /** A rectangle of the dungeon layout (block coordinates relative to the instance origin, inclusive). */
    public record Rect(int minX, int minZ, int maxX, int maxZ) {
        public Rect {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("empty rect");
            }
        }

        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0;
        }
    }

    /** A trap of the instance at a world position. */
    public record Trap(double x, double z, String kind) {
        public Trap {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * The dungeon instance the player is in.
     *
     * @param nameKey     translation key of the dungeon name
     * @param originX     world position of the layout origin
     * @param currentRoom room being fought (0-based), -1 before the start, the room count once in the boss arena
     * @param bossFight   the boss fight is on
     */
    public record Dungeon(String id, String nameKey, int originX, int originY, int originZ, Rect hall, List<Rect> rooms,
                          List<Rect> corridors, Rect arena, int currentRoom, boolean bossFight, List<Trap> traps) {
        public Dungeon {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(nameKey, "nameKey");
            Objects.requireNonNull(hall, "hall");
            rooms = List.copyOf(rooms);
            corridors = List.copyOf(corridors);
            Objects.requireNonNull(arena, "arena");
            traps = List.copyOf(traps);
        }
    }

    public boolean isUnlocked(int areaId) {
        return unlockedAreas.contains(areaId);
    }

    public boolean isDiscovered(int areaId) {
        return discoveredAreas.contains(areaId);
    }

    // ------------------------------------------------------------------ encoding

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("v", 1);
        JsonObject realmJson = new JsonObject();
        realmJson.addProperty("dim", realm.dimension());
        realmJson.addProperty("cx", realm.centerX());
        realmJson.addProperty("cz", realm.centerZ());
        realmJson.addProperty("astoria", realm.astoria());
        json.add("realm", realmJson);
        json.add("unlocked", ints(unlockedAreas));
        json.add("discovered", ints(discoveredAreas));
        if (!waystones.isEmpty()) {
            JsonArray stones = new JsonArray();
            waystones.forEach(stones::add);
            json.add("waystones", stones);
        }
        json.addProperty("final", finalBossUnlocked);
        JsonArray members = new JsonArray();
        for (Member member : party) {
            JsonObject m = new JsonObject();
            m.addProperty("id", member.id().toString());
            m.addProperty("name", member.name());
            m.addProperty("dim", member.dimension());
            m.addProperty("x", round(member.x()));
            m.addProperty("y", round(member.y()));
            m.addProperty("z", round(member.z()));
            m.addProperty("yaw", Math.round(member.yaw()));
            m.addProperty("hp", Math.round(member.health() * 100.0) / 100.0);
            if (member.leader()) {
                m.addProperty("leader", true);
            }
            members.add(m);
        }
        json.add("party", members);
        JsonArray npcArray = new JsonArray();
        for (Npc npc : npcs) {
            JsonObject n = new JsonObject();
            n.addProperty("x", round(npc.x()));
            n.addProperty("y", round(npc.y()));
            n.addProperty("z", round(npc.z()));
            n.addProperty("role", npc.role());
            n.addProperty("name", npc.name());
            if (npc.mark() != Mark.NONE) {
                n.addProperty("mark", npc.mark().symbol());
            }
            npcArray.add(n);
        }
        json.add("npcs", npcArray);
        dungeon.ifPresent(d -> json.add("dungeon", dungeonJson(d)));
        return json;
    }

    private static JsonObject dungeonJson(Dungeon d) {
        JsonObject json = new JsonObject();
        json.addProperty("id", d.id());
        json.addProperty("name", d.nameKey());
        json.addProperty("ox", d.originX());
        json.addProperty("oy", d.originY());
        json.addProperty("oz", d.originZ());
        json.add("hall", rect(d.hall()));
        JsonArray rooms = new JsonArray();
        d.rooms().forEach(room -> rooms.add(rect(room)));
        json.add("rooms", rooms);
        JsonArray corridors = new JsonArray();
        d.corridors().forEach(corridor -> corridors.add(rect(corridor)));
        json.add("corridors", corridors);
        json.add("arena", rect(d.arena()));
        json.addProperty("room", d.currentRoom());
        json.addProperty("boss", d.bossFight());
        JsonArray traps = new JsonArray();
        for (Trap trap : d.traps()) {
            JsonObject t = new JsonObject();
            t.addProperty("x", round(trap.x()));
            t.addProperty("z", round(trap.z()));
            t.addProperty("kind", trap.kind());
            traps.add(t);
        }
        json.add("traps", traps);
        return json;
    }

    private static JsonArray rect(Rect rect) {
        JsonArray array = new JsonArray();
        array.add(rect.minX());
        array.add(rect.minZ());
        array.add(rect.maxX());
        array.add(rect.maxZ());
        return array;
    }

    private static JsonArray ints(Collection<Integer> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ------------------------------------------------------------------ decoding

    /** Lenient: anything missing or malformed becomes the default; never throws for a JSON object. */
    public static MapView fromJson(JsonObject json) {
        if (json == null) {
            return EMPTY;
        }
        JsonObject realmJson = object(json, "realm");
        Realm realm = realmJson == null ? Realm.DEFAULT : new Realm(text(realmJson, "dim", Realm.DEFAULT.dimension()),
                (int) number(realmJson, "cx", 0), (int) number(realmJson, "cz", 0), bool(realmJson, "astoria", true));
        Set<Integer> unlocked = intSet(json, "unlocked");
        if (unlocked.isEmpty()) {
            unlocked = Set.of(1);
        }
        List<Member> party = new ArrayList<>();
        for (JsonObject m : objects(json, "party", MAX_MEMBERS)) {
            UUID id = uuid(text(m, "id", ""));
            if (id != null) {
                party.add(new Member(id, text(m, "name", "?"), text(m, "dim", ""), number(m, "x", 0), number(m, "y", 0),
                        number(m, "z", 0), (float) number(m, "yaw", 0), (float) clamp(number(m, "hp", 1), 0, 1),
                        bool(m, "leader", false)));
            }
        }
        List<Npc> npcs = new ArrayList<>();
        for (JsonObject n : objects(json, "npcs", MAX_NPCS)) {
            npcs.add(new Npc(number(n, "x", 0), number(n, "y", 0), number(n, "z", 0), text(n, "role", ""),
                    text(n, "name", ""), Mark.of(text(n, "mark", ""))));
        }
        Set<String> waystones = new TreeSet<>();
        if (json.has("waystones") && json.get("waystones").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("waystones")) {
                if (waystones.size() < 128 && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                        && element.getAsString().matches("[a-z0-9_]{1,32}")) {
                    waystones.add(element.getAsString());
                }
            }
        }
        return new MapView(realm, unlocked, intSet(json, "discovered"), bool(json, "final", false), party, npcs,
                dungeon(object(json, "dungeon")), waystones);
    }

    private static Optional<Dungeon> dungeon(JsonObject d) {
        if (d == null) {
            return Optional.empty();
        }
        Rect hall = rect(d.get("hall"));
        Rect arena = rect(d.get("arena"));
        if (hall == null || arena == null) {
            return Optional.empty();
        }
        List<Rect> rooms = rects(d, "rooms", MAX_ROOMS);
        List<Rect> corridors = rects(d, "corridors", MAX_CORRIDORS);
        List<Trap> traps = new ArrayList<>();
        for (JsonObject t : objects(d, "traps", MAX_TRAPS)) {
            traps.add(new Trap(number(t, "x", 0), number(t, "z", 0), text(t, "kind", "")));
        }
        return Optional.of(new Dungeon(text(d, "id", ""), text(d, "name", ""), (int) number(d, "ox", 0),
                (int) number(d, "oy", 0), (int) number(d, "oz", 0), hall, rooms, corridors, arena,
                (int) number(d, "room", -1), bool(d, "boss", false), traps));
    }

    private static List<Rect> rects(JsonObject json, String key, int max) {
        List<Rect> result = new ArrayList<>();
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (result.size() >= max) {
                    break;
                }
                Rect rect = rect(item);
                if (rect != null) {
                    result.add(rect);
                }
            }
        }
        return result;
    }

    private static Rect rect(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 4) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            values[i] = value.getAsInt();
        }
        return values[0] <= values[2] && values[1] <= values[3] ? new Rect(values[0], values[1], values[2], values[3])
                : null;
    }

    private static List<JsonObject> objects(JsonObject json, String key, int max) {
        List<JsonObject> result = new ArrayList<>();
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (result.size() >= max) {
                    break;
                }
                if (item.isJsonObject()) {
                    result.add(item.getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Set<Integer> intSet(JsonObject json, String key) {
        Set<Integer> result = new TreeSet<>();
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber() && result.size() < 64) {
                    result.add(item.getAsInt());
                }
            }
        }
        return result;
    }

    private static String text(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return fallback;
        }
        double value = primitive.getAsDouble();
        return Double.isFinite(value) ? value : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : fallback;
    }

    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
