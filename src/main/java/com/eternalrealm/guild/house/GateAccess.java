package com.eternalrealm.guild.house;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/** What a guild gate does with a player who walks into it ({@link GuildHouseService#gate}). */
public enum GateAccess {
    /** A member of a guild that owns this gate's design: into their own guild's house. */
    ENTER,
    /** Not in a guild: refused. */
    NOT_IN_GUILD,
    /** The player's guild has no house yet: refused (the gate tells them where to buy one). */
    NO_HOUSE,
    /** The player's guild owns another design: refused (the gate names it). */
    OTHER_DESIGN;

    /** Translation key of the refusal (unused for {@link #ENTER}). */
    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".guildhouse.gate." + name().toLowerCase(Locale.ROOT);
    }
}
