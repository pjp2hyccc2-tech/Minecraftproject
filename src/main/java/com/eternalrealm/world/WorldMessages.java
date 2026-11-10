package com.eternalrealm.world;

import java.util.List;

/**
 * Translation keys of the player-visible world texts the Forge layer shows. Area names come from
 * {@link AreaDefinition#translationKey()}; boss names from the boss module.
 */
public final class WorldMessages {
    /** Broadcast after a first boss defeat opened an area. Arg: area name. */
    public static final String AREA_UNLOCKED = "message.eternalrealm.world.area_unlocked";
    /** Broadcast on the first defeat of a boss on this server. Arg: boss name. */
    public static final String BOSS_FIRST_DEFEAT = "message.eternalrealm.world.boss_first_defeat";
    /** Broadcast when {@code b1}..{@code b8} have all fallen. No args. */
    public static final String FINAL_BOSS_UNLOCKED = "message.eternalrealm.world.final_boss_unlocked";
    /** To a player entering an area for the first time. Arg: area name. */
    public static final String AREA_DISCOVERED = "message.eternalrealm.world.area_discovered";
    /** To a player trying to enter a locked area. Arg: area name. */
    public static final String AREA_LOCKED = "message.eternalrealm.world.area_locked";
    /** Subtitle of the area title. Args: minimum and maximum recommended level. */
    public static final String RECOMMENDED_LEVEL = "hud.eternalrealm.area.recommended_level";
    /** Shown when the mist at the edge of the field map turns a player back. */
    public static final String WORLD_EDGE = "message.eternalrealm.world.world_edge";
    /** For the open-ended high fields: "推奨レベル 40〜". */
    public static final String RECOMMENDED_LEVEL_FROM = "hud.eternalrealm.area.recommended_level_from";

    public static final List<String> ALL = List.of(AREA_UNLOCKED, BOSS_FIRST_DEFEAT, FINAL_BOSS_UNLOCKED,
            AREA_DISCOVERED, AREA_LOCKED, RECOMMENDED_LEVEL, RECOMMENDED_LEVEL_FROM, WORLD_EDGE);

    private WorldMessages() {
    }
}
