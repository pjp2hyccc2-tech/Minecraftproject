package com.eternalrealm.guild;

import com.eternalrealm.core.ModInfo;

/**
 * Translation keys for the chat messages the Forge layer sends for {@link GuildEvent}s (texts in
 * docs/i18n/party.json). Failure messages come from {@link GuildOutcome#messageKey()}, rank names from
 * {@link GuildRank#translationKey()}. "Guild" below means every current member who is online.
 */
public final class GuildMessages {
    private static final String PREFIX = "message." + ModInfo.MOD_ID + ".guild.";

    /** {@link GuildEvent.Created}, to the founder. {@code %1$s} = name, {@code %2$s} = tag. */
    public static final String CREATED = PREFIX + "created";
    /** {@link GuildEvent.Invited}, to the guild. {@code %s} = invitee name. */
    public static final String INVITE_SENT = PREFIX + "invite_sent";
    /**
     * {@link GuildEvent.Invited}, to the invitee. {@code %1$s} = inviter name, {@code %2$s} = guild name,
     * {@code %3$s} = seconds to expiry.
     */
    public static final String INVITE_RECEIVED = PREFIX + "invite_received";
    /** Clickable chat button appended to {@link #INVITE_RECEIVED}. No arguments. */
    public static final String ACCEPT_BUTTON = PREFIX + "accept_button";
    /** Clickable chat button appended to {@link #INVITE_RECEIVED}. No arguments. */
    public static final String DECLINE_BUTTON = PREFIX + "decline_button";
    /** {@link GuildEvent.InviteExpired}, to the guild. {@code %s} = invitee name. */
    public static final String INVITE_EXPIRED_SENT = PREFIX + "invite_expired_sent";
    /** {@link GuildEvent.InviteExpired}, to the invitee. {@code %s} = guild name. */
    public static final String INVITE_EXPIRED_RECEIVED = PREFIX + "invite_expired_received";
    /** {@link GuildEvent.InviteDeclined}, to the guild. {@code %s} = invitee name. */
    public static final String INVITE_DECLINED = PREFIX + "invite_declined";
    /** {@link GuildEvent.InviteCancelled}, to the invitee. {@code %s} = guild name. */
    public static final String INVITE_CANCELLED = PREFIX + "invite_cancelled";
    /** {@link GuildEvent.Joined}, to the guild (including the new member). {@code %s} = member name. */
    public static final String JOINED = PREFIX + "joined";
    /** {@link GuildEvent.Left}, to the guild. {@code %s} = member name. */
    public static final String LEFT = PREFIX + "left";
    /** {@link GuildEvent.Left}, to the player who left. {@code %s} = guild name. */
    public static final String LEFT_SELF = PREFIX + "left_self";
    /** {@link GuildEvent.Kicked}, to the guild. {@code %s} = member name. */
    public static final String KICKED = PREFIX + "kicked";
    /** {@link GuildEvent.Kicked}, to the removed player. {@code %s} = guild name. */
    public static final String KICKED_SELF = PREFIX + "kicked_self";
    /** {@link GuildEvent.RankChanged}, to the guild. {@code %1$s} = member name, {@code %2$s} = new rank name. */
    public static final String RANK_CHANGED = PREFIX + "rank_changed";
    /** {@link GuildEvent.MasterTransferred}, to the guild. {@code %s} = new master name. */
    public static final String MASTER_TRANSFERRED = PREFIX + "master_transferred";
    /** {@link GuildEvent.LevelUp}, to the guild. {@code %1$s} = new level, {@code %2$s} = new member cap. */
    public static final String LEVEL_UP = PREFIX + "level_up";
    /** {@link GuildEvent.Disbanded}, to every former member. {@code %s} = guild name. */
    public static final String DISBANDED = PREFIX + "disbanded";

    private GuildMessages() {
    }
}
