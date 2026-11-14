package com.eternalrealm.world.map;

import com.eternalrealm.core.ModInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The map of the capital (王都の地図; owner, 2026-11-14: 「王国に入るとまた王国専用のマップを開けるようにして、どこに何が
 * あるかみやすくして」): a top-down painting of the city as it is built and the places on it. Both come from the
 * capital's blueprint ({@code tools/capital/map_texture.py} writes the picture and {@value #RESOURCE}); this class
 * reads the places and says how each is shown: its group in the list, its icon, its colour, its name and what one
 * does there. Pure.
 */
public final class CapitalMap {
    /** The places and districts, under {@code assets/eternalrealm/}. */
    public static final String RESOURCE = "capital_map.json";
    /** The painting, under {@code assets/eternalrealm/}. */
    public static final String TEXTURE = "textures/gui/capital_map.png";
    /** The places' icons, one row of {@link #ICON_SIZE}-pixel icons in {@link #icons()} order. */
    public static final String ICON_ATLAS = "textures/gui/map/capital_icons.png";
    public static final int ICON_SIZE = 12;
    /** The icon of a story character met in the capital (the atlas's last). */
    public static final String STORY = "story";

    /** The groups of the list beside the map, in its order. */
    public enum Group {
        SERVICES, GUILDS, SIGHTS, CASTLE;

        public String translationKey() {
            return "map." + ModInfo.MOD_ID + ".capital.group." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a place is: its group, its colour on the map and how early its name is placed (lower first). */
    public enum Kind {
        QUEST("quest", Group.SERVICES, 0xFFC040, 0),
        SHOP("shop", Group.SERVICES, 0xE85A5A, 0),
        SMITH("smith", Group.SERVICES, 0xB8C2D2, 0),
        GATE("gate", Group.SERVICES, 0x4FD0C0, 0),
        INN("inn", Group.SERVICES, 0xFF8AA0, 1),
        JOB("job", Group.SERVICES, 0xA88CF0, 1),
        MARKET("market", Group.SERVICES, 0xE8C66A, 1),
        MAIL("mail", Group.SERVICES, 0xF0E6D0, 2),
        STABLE("stable", Group.SERVICES, 0xC8955A, 2),
        GUILD("guild", Group.GUILDS, 0x5AB0E8, 2),
        ARRIVAL("arrival", Group.SIGHTS, 0x7AD0FF, 1),
        REVIVAL("revival", Group.SIGHTS, 0xFFF0A0, 1),
        ARENA("arena", Group.SIGHTS, 0xE07848, 2),
        FAME("fame", Group.SIGHTS, 0xFFD447, 3),
        CASTLE("castle", Group.CASTLE, 0xD8B8FF, 3);

        private final String id;
        private final Group group;
        private final int color;
        private final int rank;

        Kind(String id, Group group, int color, int rank) {
            this.id = id;
            this.group = group;
            this.color = color;
            this.rank = rank;
        }

        public String id() {
            return id;
        }

        public Group group() {
            return group;
        }

        /** {@code 0xRRGGBB}. */
        public int color() {
            return color;
        }

        /** Name plates of a lower rank are placed first (see {@link LabelLayout}). */
        public int rank() {
            return rank;
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A place on the map: its id (names its translation keys and icon), what it is, the town NPC who works there
     * ({@code ""} if none; the minimap's role id) and where it is (world blocks).
     */
    public record Place(String id, Kind kind, String npc, double x, double z) {
        public String nameKey() {
            return "map." + ModInfo.MOD_ID + ".capital.place." + id;
        }

        public String descKey() {
            return nameKey() + ".desc";
        }

        /** Index of its icon in the atlas. */
        public int icon() {
            return CapitalMap.icon(id);
        }

        public boolean hasNpc() {
            return !npc.isEmpty();
        }
    }

    /** A district's name and where it is written. */
    public record District(String id, double x, double z) {
        public String nameKey() {
            return "map." + ModInfo.MOD_ID + ".capital.district." + id;
        }
    }

    /** The painting's bounds in world blocks and what is on it. */
    public record Data(int x0, int z0, int width, int height, List<Place> places, List<District> districts) {
        public Data {
            places = List.copyOf(places);
            districts = List.copyOf(districts);
        }

        public double centerX() {
            return x0 + width / 2.0;
        }

        public double centerZ() {
            return z0 + height / 2.0;
        }

        public boolean contains(double x, double z) {
            return x >= x0 && x <= x0 + width && z >= z0 && z <= z0 + height;
        }

        public Optional<Place> place(String id) {
            return places.stream().filter(place -> place.id().equals(id)).findFirst();
        }
    }

    /** The icons in atlas order: one per place, then {@link #STORY}. */
    private static final List<String> ICONS = List.of("arrival", "goddess", "quest_board", "item_shop", "blacksmith",
            "job_master", "auction", "mailbox", "guild_registrar", "guild_estates", "gatekeeper", "arena_master",
            "hall_of_fame", "ranking_board", "inn", "stable", "castle_gate", "great_hall", "throne", "library",
            "observatory", "chapel", STORY);
    /** Most places the list and the map take. */
    public static final int MAX_PLACES = 64;

    private CapitalMap() {
    }

    public static List<String> icons() {
        return ICONS;
    }

    /** Index of a place's icon in the atlas; places without their own get the story character's speech bubble. */
    public static int icon(String placeId) {
        int index = ICONS.indexOf(placeId);
        return index < 0 ? ICONS.size() - 1 : index;
    }

    /**
     * Reads {@value #RESOURCE}: {@code {x0, z0, width, height, places: [{id, kind, npc?, x, z}], districts: [{id, x,
     * z}]}}. Places of an unknown kind and broken entries are left out.
     *
     * @throws IllegalArgumentException if the bounds are missing or empty
     */
    public static Data parse(JsonObject json) {
        int x0 = integer(json, "x0");
        int z0 = integer(json, "z0");
        int width = integer(json, "width");
        int height = integer(json, "height");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("the capital map has no area");
        }
        List<Place> places = new ArrayList<>();
        for (JsonObject entry : objects(json, "places")) {
            String id = string(entry, "id");
            Optional<Kind> kind = Kind.byId(string(entry, "kind"));
            if (id.isEmpty() || kind.isEmpty() || !entry.has("x") || !entry.has("z") || places.size() >= MAX_PLACES) {
                continue;
            }
            try {
                places.add(new Place(id, kind.get(), string(entry, "npc"), entry.get("x").getAsDouble(),
                        entry.get("z").getAsDouble()));
            } catch (RuntimeException e) {
                // a broken entry is left out
            }
        }
        List<District> districts = new ArrayList<>();
        for (JsonObject entry : objects(json, "districts")) {
            String id = string(entry, "id");
            if (id.isEmpty() || !entry.has("x") || !entry.has("z")) {
                continue;
            }
            try {
                districts.add(new District(id, entry.get("x").getAsDouble(), entry.get("z").getAsDouble()));
            } catch (RuntimeException e) {
                // a broken entry is left out
            }
        }
        return new Data(x0, z0, width, height, places, districts);
    }

    /** The list beside the map: by group, then the kinds' order, then as the blueprint lists them. */
    public static List<Place> legend(Data data) {
        List<Place> sorted = new ArrayList<>(data.places());
        List<Place> original = data.places();
        sorted.sort(Comparator.<Place>comparingInt(place -> place.kind().group().ordinal())
                .thenComparingInt(place -> place.kind().ordinal())
                .thenComparingInt(original::indexOf));
        return sorted;
    }

    /**
     * The compass point from one point to another (north is up: −z), as a translation key
     * {@code map.eternalrealm.capital.dir.<n|ne|e|se|s|sw|w|nw>}, or {@code ...dir.here} closer than {@code near}.
     */
    public static String direction(double fromX, double fromZ, double toX, double toZ, double near) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        String prefix = "map." + ModInfo.MOD_ID + ".capital.dir.";
        if (Math.hypot(dx, dz) < near) {
            return prefix + "here";
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
        int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return prefix + List.of("n", "ne", "e", "se", "s", "sw", "w", "nw").get(sector);
    }

    // ------------------------------------------------------------------ json

    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("the capital map has no " + key);
        }
        return json.get(key).getAsInt();
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    out.add(element.getAsJsonObject());
                }
            }
        }
        return out;
    }
}
