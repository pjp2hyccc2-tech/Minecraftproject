package com.eternalrealm.guild.house;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/** Result code of a {@link GuildHouseService} operation. */
public enum GuildHouseOutcome {
    OK,
    /** The acting player is not in a guild. */
    NOT_IN_GUILD,
    /** The acting player's rank does not allow this (buying and switching are the master's). */
    NO_PERMISSION,
    /** No such design in the catalogue. */
    UNKNOWN_DESIGN,
    /** Buy: the guild already has a house (switch the design instead). */
    ALREADY_OWNED,
    /** Switch, give up: the guild has no house. */
    NO_HOUSE,
    /** Switch: that is the current design. */
    SAME_DESIGN,
    /** The house fund and the master's wallet together do not cover the price. */
    NOT_ENOUGH_GOLD,
    /** A donation of nothing, more than the wallet holds or beyond the fund's limit. */
    INVALID_AMOUNT,
    /** An empty note or message where text is needed. */
    EMPTY_TEXT,
    /** The author already has {@link GuildHouseRules#NOTES_PER_MEMBER} notes on the board. */
    TOO_MANY_NOTES,
    /** No such note. */
    NOTE_NOT_FOUND,
    /** The guild houses are not running (no estates dimension or the data did not load). */
    UNAVAILABLE,
    /** The house is still being built. */
    BUILDING;

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key of the failure message; empty for {@link #OK}. */
    public Optional<String> messageKey() {
        return this == OK ? Optional.empty()
                : Optional.of("message." + ModInfo.MOD_ID + ".guildhouse.error." + name().toLowerCase(Locale.ROOT));
    }
}
