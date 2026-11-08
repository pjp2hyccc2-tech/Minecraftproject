package com.eternalrealm.world.gen;

/**
 * Named positions in the capital アルセリア王都 where the integrator places NPCs, boards and the spawn. Offsets are
 * relative to the plaza centre (0, 0); every spot stands on the plaza floor level ({@code dy} = 1 = feet just above the
 * paving). {@code yaw} is the Minecraft facing for an NPC there (0 = south, 90 = west, 180 = north, 270 = east).
 */
public enum HubSpot {
    /** In front of the quest board, north-east corner of the plaza. */
    QUEST_BOARD(10, -9, 45.0F),
    /** Behind the counter of the item shop (south-west building, door to the north). */
    ITEM_SHOP(-29, 12, 180.0F),
    /** At the anvil of the blacksmith (south-east building, open front to the north). */
    BLACKSMITH(28, 11, 180.0F),
    /** At the lectern of the job hall (west building, door to the south). */
    JOB_MASTER(-30, -16, 0.0F),
    /** Behind the auction counter (east building, door to the south). */
    AUCTION_HOUSE(30, -16, 0.0F),
    /** Beside the post box, south-east corner of the plaza. */
    MAILBOX(10, 9, 135.0F),
    /** At the desk of the guild hall (north-west building). */
    GUILD_REGISTRAR(-27, -32, 0.0F),
    /** Inside the south gate (main entrance of the city). */
    GATEKEEPER(4, 35, 90.0F),
    /** At the gate of the training arena (south-east quarter). */
    ARENA_MASTER(24, 18, 90.0F),
    /** In front of the ranking notice board, north-west corner of the plaza. */
    RANKING_BOARD(-10, -9, 315.0F),
    /** Inside the hall of fame (north-east building). */
    HALL_OF_FAME(27, -32, 0.0F),
    /** Where players arrive: south of the fountain, looking at the castle. */
    SPAWN(0, 7, 180.0F),
    /** In front of the goddess statue, south-west corner of the plaza. */
    RESPAWN(-9, 9, 225.0F);

    private final int dx;
    private final int dz;
    private final float yaw;

    HubSpot(int dx, int dz, float yaw) {
        this.dx = dx;
        this.dz = dz;
        this.yaw = yaw;
    }

    public int dx() {
        return dx;
    }

    /** Feet height above the plaza floor. */
    public int dy() {
        return 1;
    }

    public int dz() {
        return dz;
    }

    public float yaw() {
        return yaw;
    }

    /** Translation key of the spot's label (e.g. for an NPC name tag or a map marker). */
    public String translationKey() {
        return "hub.eternalrealm." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
