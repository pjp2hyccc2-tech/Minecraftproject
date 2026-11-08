package com.eternalrealm.guild;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Result code of a {@link GuildService} operation. Message arguments ({@code %s}) are listed per constant.
 */
public enum GuildOutcome {
    OK,
    /** Name breaks the rules of {@link GuildNames}. No arguments. */
    INVALID_NAME,
    /** Tag is not 2-5 letters/digits. No arguments. */
    INVALID_TAG,
    /** Another guild has the same name (ignoring case and full-width/half-width). {@code %s} = name. */
    NAME_TAKEN,
    /** Another guild has the same tag. {@code %s} = tag. */
    TAG_TAKEN,
    /** The acting player already belongs to a guild (create, accept). No arguments. */
    ALREADY_IN_GUILD,
    /** The acting player is not in a guild. No arguments. */
    NOT_IN_GUILD,
    /** The acting player's rank does not allow this. No arguments. */
    NO_PERMISSION,
    /** The target is the acting player. No arguments. */
    SELF,
    /** The invited player already belongs to a guild. {@code %s} = target name. */
    TARGET_IN_GUILD,
    /** The target is not a member of the acting player's guild. {@code %s} = target name. */
    NOT_A_MEMBER,
    /** The guild already has a pending invite for the player. {@code %s} = target name. */
    ALREADY_INVITED,
    /** No pending invite from that guild. No arguments. */
    NO_INVITE,
    /** The guild has reached its member cap. {@code %s} = member cap. */
    GUILD_FULL,
    /** The master must hand the guild over or disband it before leaving. No arguments. */
    MASTER_MUST_TRANSFER,
    /** Promote: the target already is an officer (use the master transfer to make them master). {@code %s} = target name. */
    ALREADY_OFFICER,
    /** Demote: the target already has the lowest rank. {@code %s} = target name. */
    ALREADY_MEMBER;

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key of the failure message; empty for {@link #OK} (successes are reported through events). */
    public Optional<String> messageKey() {
        if (this == OK) {
            return Optional.empty();
        }
        return Optional.of("message." + ModInfo.MOD_ID + ".guild.error." + name().toLowerCase(Locale.ROOT));
    }
}
