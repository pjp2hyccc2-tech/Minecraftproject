package com.eternalrealm.party;

import com.eternalrealm.core.ModInfo;

/**
 * Translation keys for the chat messages the Forge layer sends for {@link PartyEvent}s (texts in
 * docs/i18n/party.json). Failure messages come from {@link PartyOutcome#messageKey()}, party type names from
 * {@link PartyType#translationKey()}. "Party" below means every current member.
 */
public final class PartyMessages {
    private static final String PREFIX = "message." + ModInfo.MOD_ID + ".party.";

    /** {@link PartyEvent.Created}, to the leader. No arguments. */
    public static final String CREATED = PREFIX + "created";
    /** {@link PartyEvent.Invited}, to the party. {@code %s} = invitee name. */
    public static final String INVITE_SENT = PREFIX + "invite_sent";
    /** {@link PartyEvent.Invited}, to the invitee. {@code %1$s} = inviter name, {@code %2$s} = seconds to expiry. */
    public static final String INVITE_RECEIVED = PREFIX + "invite_received";
    /** Clickable chat button appended to {@link #INVITE_RECEIVED}. No arguments. */
    public static final String ACCEPT_BUTTON = PREFIX + "accept_button";
    /** Clickable chat button appended to {@link #INVITE_RECEIVED}. No arguments. */
    public static final String DECLINE_BUTTON = PREFIX + "decline_button";
    /** {@link PartyEvent.InviteExpired}, to the party. {@code %s} = invitee name. */
    public static final String INVITE_EXPIRED_SENT = PREFIX + "invite_expired_sent";
    /** {@link PartyEvent.InviteExpired}, to the invitee. {@code %s} = inviter name. */
    public static final String INVITE_EXPIRED_RECEIVED = PREFIX + "invite_expired_received";
    /** {@link PartyEvent.InviteDeclined}, to the party. {@code %s} = invitee name. */
    public static final String INVITE_DECLINED = PREFIX + "invite_declined";
    /** {@link PartyEvent.InviteCancelled}, to the invitee. {@code %s} = inviter name. */
    public static final String INVITE_CANCELLED = PREFIX + "invite_cancelled";
    /** {@link PartyEvent.Joined}, to the party (including the new member). {@code %s} = member name. */
    public static final String JOINED = PREFIX + "joined";
    /** {@link PartyEvent.Left}, to the remaining party. {@code %s} = member name. */
    public static final String LEFT = PREFIX + "left";
    /** {@link PartyEvent.Left} or {@link PartyEvent.Disbanded} by leaving, to the player who left. No arguments. */
    public static final String LEFT_SELF = PREFIX + "left_self";
    /** {@link PartyEvent.Kicked}, to the remaining party. {@code %s} = member name. */
    public static final String KICKED = PREFIX + "kicked";
    /** {@link PartyEvent.Kicked}, to the removed player. No arguments. */
    public static final String KICKED_SELF = PREFIX + "kicked_self";
    /** {@link PartyEvent.LeaderChanged}, to the party. {@code %s} = new leader name. */
    public static final String LEADER_CHANGED = PREFIX + "leader_changed";
    /** {@link PartyEvent.ConvertedToRaid}, to the party. {@code %s} = new capacity. */
    public static final String CONVERTED_TO_RAID = PREFIX + "converted_to_raid";
    /** {@link PartyEvent.Disbanded}, to every former member. No arguments. */
    public static final String DISBANDED = PREFIX + "disbanded";

    private PartyMessages() {
    }
}
