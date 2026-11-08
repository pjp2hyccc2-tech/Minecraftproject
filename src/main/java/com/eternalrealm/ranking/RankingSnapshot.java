package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, persistable state of a {@link RankingBoard} ({@code seasons/<seasonId>/rankings.json}). Created on the
 * server thread by {@link RankingBoard#snapshot()} and handed to the I/O thread; turned back into a board with
 * {@link RankingBoard#rebuild(RankingSnapshot, RankingRules)}.
 *
 * @param frozen whether the season's rankings are final (no more updates; stored scores are restored as they are)
 */
public record RankingSnapshot(SeasonId seasonId, boolean frozen, List<PlayerStanding> players,
                              List<GuildStanding> guilds, TimeAttackSnapshot timeAttack) {
    public RankingSnapshot {
        Objects.requireNonNull(seasonId, "seasonId");
        players = List.copyOf(players);
        guilds = List.copyOf(guilds);
        Objects.requireNonNull(timeAttack, "timeAttack");
        Set<UUID> playerIds = new HashSet<>();
        for (PlayerStanding player : players) {
            if (!playerIds.add(player.playerId())) {
                throw new IllegalArgumentException("Duplicate player " + player.playerId());
            }
        }
        Set<String> guildIds = new HashSet<>();
        for (GuildStanding guild : guilds) {
            if (!guildIds.add(guild.guildId())) {
                throw new IllegalArgumentException("Duplicate guild " + guild.guildId());
            }
        }
    }

    /** A season without any ranking data yet. */
    public static RankingSnapshot empty(SeasonId seasonId) {
        return new RankingSnapshot(seasonId, false, List.of(), List.of(), TimeAttackSnapshot.EMPTY);
    }
}
