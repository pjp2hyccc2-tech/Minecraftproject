package com.eternalrealm.ranking;

/**
 * Stored GUILD ranking line. Guild scores are stored as they are (not derived on restore): they are sums over the
 * guild's members, and the membership belongs to the guild module, not to the ranking.
 *
 * @param score           {@code > 0} (unranked guilds are not stored)
 * @param updatedAtMillis when the guild reached this score (the tie-break)
 */
public record GuildStanding(String guildId, String name, long score, long updatedAtMillis) {
    public GuildStanding {
        Check.guildId(guildId);
        Check.displayName("name", name);
        Check.range("score", score, 1L, Long.MAX_VALUE);
        Check.nonNegative("updatedAtMillis", updatedAtMillis);
    }
}
