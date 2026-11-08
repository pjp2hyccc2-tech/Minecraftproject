package com.eternalrealm.story;

import java.util.Optional;

/**
 * The seven areas of Astoria with the ids and slugs of the world module (area ids are the {@code areaId} of the
 * dungeon definitions). {@link #unlockedBy()} is the boss whose first server-wide kill opens the area; the story text
 * of {@link StoryTrigger.AreaUnlocked} assumes this order.
 */
public enum StoryArea {
    ARCELIA(1, "arcelia", null),
    ELDER_FOREST(2, "elder_forest", "b1"),
    SANDRIA_DESERT(3, "sandria_desert", "b2"),
    NORDIA_SNOWFIELD(4, "nordia_snowfield", "b3"),
    DRAGONIA_VOLCANO(5, "dragonia_volcano", "b5"),
    CELESTIA(6, "celestia", "b6"),
    TOWER_OF_THE_END(7, "tower_of_the_end", "b7");

    private final int id;
    private final String slug;
    private final String unlockedBy;

    StoryArea(int id, String slug, String unlockedBy) {
        this.id = id;
        this.slug = slug;
        this.unlockedBy = unlockedBy;
    }

    public int id() {
        return id;
    }

    /** Same as the world module's slug (its names are {@code area.eternalrealm.<slug>}). */
    public String slug() {
        return slug;
    }

    /** The boss that opens this area; empty for the starting area. */
    public Optional<String> unlockedBy() {
        return Optional.ofNullable(unlockedBy);
    }

    public static Optional<StoryArea> byId(int id) {
        for (StoryArea area : values()) {
            if (area.id == id) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    public static Optional<StoryArea> bySlug(String slug) {
        for (StoryArea area : values()) {
            if (area.slug.equals(slug)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }
}
