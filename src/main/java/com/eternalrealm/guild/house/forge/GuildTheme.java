package com.eternalrealm.guild.house.forge;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The theme of a guild gate's portal block ({@code eternalrealm:guild_portal[theme=...]}): one per design of the
 * catalogue, same ids (a GameTest keeps them in step with {@code data/eternalrealm/guildhouse/designs.json} and
 * {@code tools/capital/blocks.py}). The theme picks the portal's colours and which design the gate leads to. Never
 * rename a value: placed blocks store it.
 */
public enum GuildTheme implements StringRepresentable {
    NORTHERN_LONGHOUSE, MUSHROOM_GROVE, ANCIENT_RUINS, PIRATE_SHIP, FLOWER_GLASSHOUSE, DWARVEN_FORGE, DESERT_PALACE,
    OBSERVATORY, JAPANESE_MANSION, GREAT_TREE, SAGE_LIBRARY, JUNGLE_TEMPLE, GOTHIC_CATHEDRAL, CORAL_HALL,
    VOLCANIC_FORTRESS, ICE_CASTLE, DRAGON_LAIR, ARCANE_TOWER, SKY_TEMPLE, MARBLE_PALACE;

    private final String id = name().toLowerCase(Locale.ROOT);

    /** The design id. */
    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static Optional<GuildTheme> byId(String id) {
        for (GuildTheme theme : values()) {
            if (theme.id.equals(id)) {
                return Optional.of(theme);
            }
        }
        return Optional.empty();
    }
}
